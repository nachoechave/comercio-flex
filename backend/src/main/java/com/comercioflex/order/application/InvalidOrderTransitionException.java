package com.comercioflex.order.application;

public class InvalidOrderTransitionException extends RuntimeException {

	public InvalidOrderTransitionException(String message) {
		super(message);
	}
}
