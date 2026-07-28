package com.comercioflex.catalog.application;

public class CategoryNotFoundException extends RuntimeException {

	public CategoryNotFoundException() {
		super("Category not found.");
	}
}
