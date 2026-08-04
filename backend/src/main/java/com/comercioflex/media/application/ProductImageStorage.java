package com.comercioflex.media.application;

public interface ProductImageStorage {
	void store(String key, byte[] bytes, String contentType);
	StorageObject load(String key, String contentType);
	void delete(String key);
}
