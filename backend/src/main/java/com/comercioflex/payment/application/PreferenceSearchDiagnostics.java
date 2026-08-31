package com.comercioflex.payment.application;

public record PreferenceSearchDiagnostics(
	Integer preferenceCreationCount,
	Boolean storedPreferenceFound,
	Boolean actualPaymentPreferenceFound,
	Boolean storedPreferenceMatchesActual,
	Boolean multiplePreferencesFound,
	Boolean storedPreferenceAmongSearchResults) {

	public static PreferenceSearchDiagnostics unavailable(boolean storedPreferenceMatchesActual) {
		return new PreferenceSearchDiagnostics(
			null, null, null, storedPreferenceMatchesActual, null, null);
	}
}
