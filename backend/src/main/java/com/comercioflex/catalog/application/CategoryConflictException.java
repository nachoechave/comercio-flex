package com.comercioflex.catalog.application;

public class CategoryConflictException extends RuntimeException {

	public CategoryConflictException() {
		super("A category with the same name or slug already exists.");
	}
}
