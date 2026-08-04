package com.comercioflex.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.media")
public class ProductMediaProperties {

	private String storage = "local";
	private String localRoot = ".data/media";
	private long maxFileSizeBytes = 5_242_880;
	private long maxPixels = 10_000_000;
	private int displayMaxDimension = 1_600;
	private int thumbnailMaxDimension = 480;
	private final S3 s3 = new S3();

	public String getStorage() { return storage; }
	public void setStorage(String storage) { this.storage = storage; }
	public String getLocalRoot() { return localRoot; }
	public void setLocalRoot(String localRoot) { this.localRoot = localRoot; }
	public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
	public void setMaxFileSizeBytes(long value) { this.maxFileSizeBytes = value; }
	public long getMaxPixels() { return maxPixels; }
	public void setMaxPixels(long maxPixels) { this.maxPixels = maxPixels; }
	public int getDisplayMaxDimension() { return displayMaxDimension; }
	public void setDisplayMaxDimension(int value) { this.displayMaxDimension = value; }
	public int getThumbnailMaxDimension() { return thumbnailMaxDimension; }
	public void setThumbnailMaxDimension(int value) { this.thumbnailMaxDimension = value; }
	public S3 getS3() { return s3; }

	public static class S3 {
		private String bucket;
		private String region = "us-east-1";
		private String endpoint;
		private String accessKey;
		private String secretKey;
		private boolean pathStyle;

		public String getBucket() { return bucket; }
		public void setBucket(String bucket) { this.bucket = bucket; }
		public String getRegion() { return region; }
		public void setRegion(String region) { this.region = region; }
		public String getEndpoint() { return endpoint; }
		public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
		public String getAccessKey() { return accessKey; }
		public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
		public String getSecretKey() { return secretKey; }
		public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
		public boolean isPathStyle() { return pathStyle; }
		public void setPathStyle(boolean pathStyle) { this.pathStyle = pathStyle; }
	}
}
