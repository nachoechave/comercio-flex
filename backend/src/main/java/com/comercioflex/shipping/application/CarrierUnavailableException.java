package com.comercioflex.shipping.application;

public class CarrierUnavailableException extends RuntimeException {
  public CarrierUnavailableException(String message) {
    super(message);
  }

  public CarrierUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
