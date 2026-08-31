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
		PreferenceLinkDiagnostics preferenceLinkDiagnostics = reconciliationDiagnostics == null
			? null : reconciliationDiagnostics.preferenceLinkDiagnostics();
		PreferenceSearchDiagnostics preferenceSearchDiagnostics = reconciliationDiagnostics == null
			? null : reconciliationDiagnostics.preferenceSearchDiagnostics();
		return new CheckoutPaymentException(
			code, getMessage(), getCause(),
			new ReconciliationDiagnostics(
				stage, reason, providerHttpStatus, providerErrorCode, resultCount,
				providerResponseNull, merchantOrdersCollectionNull,
				merchantOrdersCount, pagingPresent, preferenceLinkDiagnostics,
				preferenceSearchDiagnostics));
	}

	public CheckoutPaymentException withPreferenceLinkDiagnostics(
			PreferenceLinkDiagnostics preferenceLinkDiagnostics) {
		ReconciliationDiagnostics current = reconciliationDiagnostics;
		return new CheckoutPaymentException(
			code, getMessage(), getCause(),
			new ReconciliationDiagnostics(
				current == null ? null : current.stage(),
				current == null ? null : current.reason(),
				current == null ? null : current.providerHttpStatus(),
				current == null ? null : current.providerErrorCode(),
				current == null ? null : current.resultCount(),
				current == null ? null : current.providerResponseNull(),
				current == null ? null : current.merchantOrdersCollectionNull(),
				current == null ? null : current.merchantOrdersCount(),
				current == null ? null : current.pagingPresent(),
				preferenceLinkDiagnostics,
				current == null ? null : current.preferenceSearchDiagnostics()));
	}

	public CheckoutPaymentException withPreferenceSearchDiagnostics(
			PreferenceSearchDiagnostics preferenceSearchDiagnostics) {
		ReconciliationDiagnostics current = reconciliationDiagnostics;
		return new CheckoutPaymentException(
			code, getMessage(), getCause(),
			new ReconciliationDiagnostics(
				current == null ? null : current.stage(),
				current == null ? null : current.reason(),
				current == null ? null : current.providerHttpStatus(),
				current == null ? null : current.providerErrorCode(),
				current == null ? null : current.resultCount(),
				current == null ? null : current.providerResponseNull(),
				current == null ? null : current.merchantOrdersCollectionNull(),
				current == null ? null : current.merchantOrdersCount(),
				current == null ? null : current.pagingPresent(),
				current == null ? null : current.preferenceLinkDiagnostics(),
				preferenceSearchDiagnostics));
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
		Boolean pagingPresent,
		PreferenceLinkDiagnostics preferenceLinkDiagnostics,
		PreferenceSearchDiagnostics preferenceSearchDiagnostics) {
	}

	public record PreferenceLinkDiagnostics(
		Boolean paymentOrderPresent,
		Boolean paymentOrderIdPresent,
		Boolean paymentOrderTypePresent,
		Boolean merchantOrderLookupAttempted,
		Boolean merchantOrderResponsePresent,
		Boolean merchantOrderPreferencePresent,
		Boolean merchantOrderPreferenceMatches,
		Integer merchantOrderHttpStatus,
		String merchantOrderProviderErrorCode) {
	}
}
