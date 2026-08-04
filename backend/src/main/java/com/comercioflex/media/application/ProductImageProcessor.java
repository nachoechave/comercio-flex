package com.comercioflex.media.application;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;

import com.comercioflex.media.config.ProductMediaProperties;

@Component
public class ProductImageProcessor {

	private final ProductMediaProperties properties;

	public ProductImageProcessor(ProductMediaProperties properties) {
		this.properties = properties;
	}

	public ProcessedProductImage process(byte[] source) {
		if (source == null || source.length == 0) {
			throw invalid("Seleccioná una imagen JPEG o PNG.");
		}
		if (source.length > properties.getMaxFileSizeBytes()) {
			throw invalid("La imagen no puede superar 5 MB.");
		}
		String format = detectFormat(source);
		try (ImageInputStream input = ImageIO.createImageInputStream(
				new ByteArrayInputStream(source))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) throw invalid("El archivo no contiene una imagen válida.");
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width <= 0 || height <= 0
						|| Math.multiplyExact((long) width, height) > properties.getMaxPixels()) {
					throw invalid("La imagen tiene dimensiones demasiado grandes.");
				}
				BufferedImage decoded = orient(reader.read(0), jpegOrientation(source, format));
				BufferedImage display = resize(decoded, properties.getDisplayMaxDimension(), format);
				BufferedImage thumbnail = resize(decoded, properties.getThumbnailMaxDimension(), format);
				byte[] displayBytes = encode(display, format);
				byte[] thumbnailBytes = encode(thumbnail, format);
				return new ProcessedProductImage(
					displayBytes,
					thumbnailBytes,
					"jpeg".equals(format) ? "image/jpeg" : "image/png",
					"jpeg".equals(format) ? "jpg" : "png",
					display.getWidth(),
					display.getHeight(),
					sha256(displayBytes));
			}
			finally {
				reader.dispose();
			}
		}
		catch (InvalidProductImageException exception) {
			throw exception;
		}
		catch (IOException | ArithmeticException exception) {
			throw invalid("No pudimos procesar la imagen seleccionada.");
		}
	}

	private String detectFormat(byte[] bytes) {
		if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
				&& (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
			return "jpeg";
		}
		if (bytes.length >= 8
				&& (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50
				&& bytes[2] == 0x4e && bytes[3] == 0x47
				&& bytes[4] == 0x0d && bytes[5] == 0x0a
				&& bytes[6] == 0x1a && bytes[7] == 0x0a) {
			return "png";
		}
		throw invalid("El formato permitido es JPEG o PNG.");
	}

	private BufferedImage resize(BufferedImage source, int maxDimension, String format) {
		double scale = Math.min(1d,
			Math.min((double) maxDimension / source.getWidth(),
				(double) maxDimension / source.getHeight()));
		int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
		int type = "jpeg".equals(format) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
		BufferedImage target = new BufferedImage(width, height, type);
		Graphics2D graphics = target.createGraphics();
		try {
			if ("jpeg".equals(format)) {
				graphics.setColor(Color.WHITE);
				graphics.fillRect(0, 0, width, height);
			}
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
				RenderingHints.VALUE_RENDER_QUALITY);
			graphics.drawImage(source, 0, 0, width, height, null);
		}
		finally {
			graphics.dispose();
		}
		return target;
	}

	private int jpegOrientation(byte[] source, String format) {
		if (!"jpeg".equals(format)) return 1;
		try {
			ExifIFD0Directory directory = ImageMetadataReader
				.readMetadata(new ByteArrayInputStream(source))
				.getFirstDirectoryOfType(ExifIFD0Directory.class);
			return directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)
				? directory.getInt(ExifIFD0Directory.TAG_ORIENTATION) : 1;
		}
		catch (Exception ignored) {
			return 1;
		}
	}

	private BufferedImage orient(BufferedImage source, int orientation) {
		if (orientation < 2 || orientation > 8) return source;
		int sourceWidth = source.getWidth();
		int sourceHeight = source.getHeight();
		boolean swap = orientation >= 5;
		BufferedImage target = new BufferedImage(
			swap ? sourceHeight : sourceWidth,
			swap ? sourceWidth : sourceHeight,
			source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < sourceHeight; y++) {
			for (int x = 0; x < sourceWidth; x++) {
				int dx;
				int dy;
				switch (orientation) {
					case 2 -> { dx = sourceWidth - 1 - x; dy = y; }
					case 3 -> { dx = sourceWidth - 1 - x; dy = sourceHeight - 1 - y; }
					case 4 -> { dx = x; dy = sourceHeight - 1 - y; }
					case 5 -> { dx = y; dy = x; }
					case 6 -> { dx = sourceHeight - 1 - y; dy = x; }
					case 7 -> { dx = sourceHeight - 1 - y; dy = sourceWidth - 1 - x; }
					case 8 -> { dx = y; dy = sourceWidth - 1 - x; }
					default -> { dx = x; dy = y; }
				}
				target.setRGB(dx, dy, source.getRGB(x, y));
			}
		}
		return target;
	}

	private byte[] encode(BufferedImage image, String format) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		if (!ImageIO.write(image, format, output)) {
			throw new IOException("No image writer available for " + format);
		}
		return output.toByteArray();
	}

	private String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private InvalidProductImageException invalid(String message) {
		return new InvalidProductImageException(message);
	}
}
