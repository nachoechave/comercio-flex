package com.comercioflex.payment.application;

public class CheckoutPaymentException extends RuntimeException {

	private final String code;
	private final ReconciliationDiagnostics reconciliationDiagnostics;

	public CheckoutPaymentException(String code, String message) {
		this(code, message, null, null);
	}

	public CheckoutPaymentException(String code, String message, Throwable cause) {
		this(code, message, cause, null);
	}

	private CheckoutPaymentException(
			String code, String message, Throwable cause,
			ReconciliationDiagnostics reconciliationDiagnostics) {
		super(message, cause);
		this.code = code;
		this.reconciliationDiagnostics = reconciliationDiagnostics;
	}

	public String code() {
		return code;
	}

	public ReconciliationDiagnostics reconciliationDiagnostics() {
		return reconciliationDiagnostics;
	}

	public CheckoutPaymentException withReconciliationDiagnostics(
			String stage, String reason, Integer providerHttpStatus,
			String providerErrorCode, Integer resultCount) {
		return withReconciliationDiagnostics(
			stage, reason, providerHttpStatus, providerErrorCode, resultCount,
			null, null, null, null);
	}

	public CheckoutPaymentException withReconciliationDiagnostics(
			String stage, String reason, Integer providerHttpStatus,
			String providerErrorCode, Integer resultCount,
			Boolean providerResponseNull, Boolean merchantOrdersCollectionNull,
			Integer merchantOrdersCount, Boolean pagingPresent) {
		return new CheckoutPaymentException(
			code, getMessage(), getCause(),
			new ReconciliationDiagnostics(
				stage, reason, providerHttpStatus, providerErrorCode, resultCount,
				providerResponseNull, merchantOrdersCollectionNull,
				merchantOrdersCount, pagingPresent));
	}

	public record ReconciliationDiagnostics(
		String stage,
		String reason,
		Integer providerHttpStatus,
		String providerErrorCode,
		Integer resultCount,
		Boolean providerResponseNull,
		Boolean merchantOrdersCollectionNull,
		Integer merchantOrdersCount,
		Boolean pagingPresent) {
	}
}
