package com.comercioflex.catalog.application;

public class InvalidCategoryNameException extends RuntimeException {

	public InvalidCategoryNameException(String message) {
		super(message);
	}
}
