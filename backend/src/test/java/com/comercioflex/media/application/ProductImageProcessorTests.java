package com.comercioflex.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.comercioflex.media.config.ProductMediaProperties;

class ProductImageProcessorTests {

	private final ProductMediaProperties properties = new ProductMediaProperties();
	private final ProductImageProcessor processor = new ProductImageProcessor(properties);

	@Test
	void sanitizesPngAndCreatesBoundedRepresentations() throws Exception {
		BufferedImage source = new BufferedImage(2_000, 1_000, BufferedImage.TYPE_INT_ARGB);
		source.setRGB(0, 0, Color.RED.getRGB());
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(source, "png", bytes);

		ProcessedProductImage result = processor.process(bytes.toByteArray());

		assertThat(result.contentType()).isEqualTo("image/png");
		assertThat(result.width()).isEqualTo(1_600);
		assertThat(result.height()).isEqualTo(800);
		BufferedImage thumbnail = ImageIO.read(
			new java.io.ByteArrayInputStream(result.thumbnailBytes()));
		assertThat(thumbnail.getWidth()).isEqualTo(480);
		assertThat(thumbnail.getHeight()).isEqualTo(240);
		assertThat(result.sha256()).hasSize(64);
	}

	@Test
	void acceptsJpegAndRejectsDeclaredLookingButInvalidContent() throws Exception {
		BufferedImage source = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(source, "jpeg", bytes);
		assertThat(processor.process(bytes.toByteArray()).contentType()).isEqualTo("image/jpeg");

		assertThatThrownBy(() -> processor.process(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}))
			.isInstanceOf(InvalidProductImageException.class);
		assertThatThrownBy(() -> processor.process("<svg/>".getBytes()))
			.isInstanceOf(InvalidProductImageException.class);
	}

	@Test
	void rejectsFilesAboveConfiguredSizeBeforeDecoding() {
		properties.setMaxFileSizeBytes(8);
		assertThatThrownBy(() -> processor.process(new byte[9]))
			.isInstanceOf(InvalidProductImageException.class)
			.hasMessageContaining("5 MB");
	}

	@Test
	void rejectsImagesAbovePixelLimit() throws Exception {
		properties.setMaxPixels(99);
		BufferedImage source = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(source, "png", bytes);
		assertThatThrownBy(() -> processor.process(bytes.toByteArray()))
			.isInstanceOf(InvalidProductImageException.class)
			.hasMessageContaining("dimensiones");
	}

	@Test
	void normalizesJpegExifOrientationFromMobilePhotos() throws Exception {
		BufferedImage source = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
		ImageIO.write(source, "jpeg", jpeg);

		ProcessedProductImage result = processor.process(withExifOrientation(jpeg.toByteArray(), 6));

		assertThat(result.width()).isEqualTo(20);
		assertThat(result.height()).isEqualTo(40);
	}

	private byte[] withExifOrientation(byte[] jpeg, int orientation) throws Exception {
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		result.write(jpeg, 0, 2);
		byte[] tiff = ByteBuffer.allocate(26)
			.order(java.nio.ByteOrder.LITTLE_ENDIAN)
			.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8)
			.putShort((short) 1)
			.putShort((short) 0x0112).putShort((short) 3).putInt(1)
			.putShort((short) orientation).putShort((short) 0)
			.putInt(0)
			.array();
		int payloadLength = 6 + tiff.length;
		result.write(0xff);
		result.write(0xe1);
		result.write((payloadLength + 2) >>> 8);
		result.write((payloadLength + 2) & 0xff);
		result.write(new byte[] {'E', 'x', 'i', 'f', 0, 0});
		result.write(tiff);
		result.write(jpeg, 2, jpeg.length - 2);
		return result.toByteArray();
	}
}
