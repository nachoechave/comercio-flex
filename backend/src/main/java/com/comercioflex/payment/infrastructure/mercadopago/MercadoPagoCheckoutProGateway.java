package com.comercioflex.payment.infrastructure.mercadopago;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mercadopago.client.merchantorder.MerchantOrderClient;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferencePaymentMethodsRequest;
import com.mercadopago.client.preference.PreferencePaymentTypeRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.merchantorder.MerchantOrder;
import com.mercadopago.resources.merchantorder.MerchantOrderPayment;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import com.mercadopago.net.MPElementsResourcesPage;
import com.mercadopago.net.MPResultsResourcesPage;
import com.mercadopago.net.MPSearchRequest;

import com.comercioflex.payment.application.CheckoutPaymentException;
import com.comercioflex.payment.application.CheckoutPreferenceCommand;
import com.comercioflex.payment.application.CheckoutProGateway;
import com.comercioflex.payment.application.CheckoutProProperties;
import com.comercioflex.payment.application.CreatedCheckoutPreference;
import com.comercioflex.payment.application.PaymentCredential;
import com.comercioflex.payment.application.ProviderCheckoutState;
import com.comercioflex.payment.application.VerifiedProviderPayment;
import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.payment.domain.PaymentResultStatus;

public final class MercadoPagoCheckoutProGateway implements CheckoutProGateway {
	private static final Pattern PROVIDER_ERROR_CODE = Pattern.compile(
		"\\\"error\\\"\\s*:\\s*\\\"([A-Za-z0-9_.-]{1,64})\\\"");

	private final PreferenceClient preferences;
	private final PaymentClient payments;
	private final MerchantOrderClient merchantOrders;
	private final CheckoutProProperties properties;

	public MercadoPagoCheckoutProGateway(CheckoutProProperties properties) {
		this(new PreferenceClient(), new PaymentClient(), new MerchantOrderClient(), properties);
	}

	MercadoPagoCheckoutProGateway(
			PreferenceClient preferences,
			PaymentClient payments,
			MerchantOrderClient merchantOrders,
			CheckoutProProperties properties) {
		this.preferences = preferences;
		this.payments = payments;
		this.merchantOrders = merchantOrders;
		this.properties = properties;
	}

	@Override
	public CreatedCheckoutPreference createPreference(
			PaymentCredential credential, CheckoutPreferenceCommand command) {
		PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
			.success(command.returnUri().toString())
			.pending(command.returnUri().toString())
			.failure(command.returnUri().toString())
			.build();
		PreferenceItemRequest item = PreferenceItemRequest.builder()
			.id(command.paymentAttemptId().toString())
			.title(command.title())
			.currencyId(command.currencyCode())
			.quantity(1)
			.unitPrice(command.amount())
			.build();
		PreferencePaymentMethodsRequest paymentMethods = PreferencePaymentMethodsRequest.builder()
			.excludedPaymentTypes(List.of(
				PreferencePaymentTypeRequest.builder().id("ticket").build()))
			.build();
		PreferenceRequest request = PreferenceRequest.builder()
			.items(List.of(item))
			.externalReference(command.externalReference())
			.metadata(Map.of("payment_attempt_id", command.paymentAttemptId().toString()))
			.backUrls(backUrls)
			.autoReturn("approved")
			.notificationUrl(command.notificationUri().toString())
			.paymentMethods(paymentMethods)
			.binaryMode(false)
			.expires(true)
			.expirationDateTo(OffsetDateTime.ofInstant(command.expiresAt(), ZoneOffset.UTC))
			.build();
		try {
			Preference preference = preferences.create(request, options(credential));
			String checkoutUrl = credential.environment()
				== com.comercioflex.payment.domain.PaymentEnvironment.TEST
				? preference.getSandboxInitPoint() : preference.getInitPoint();
			if (blank(preference.getId()) || blank(checkoutUrl)
					|| preference.getCollectorId() == null) {
				throw invalidProviderResponse();
			}
			return new CreatedCheckoutPreference(
				preference.getId(), URI.create(checkoutUrl),
				preference.getCollectorId().toString());
		}
		catch (MPApiException | MPException exception) {
			throw gatewayFailure("PREFERENCE_CREATION_FAILED", exception);
		}
	}

	@Override
	public VerifiedProviderPayment findPayment(
			PaymentCredential credential, String providerPaymentId) {
		try {
			long numericId = Long.parseLong(providerPaymentId);
			MPRequestOptions options = options(credential);
			Payment payment = payments.get(numericId, options);
			if (payment == null || payment.getId() == null || payment.getOrder() == null
					|| payment.getOrder().getId() == null) {
				throw invalidProviderResponse();
			}
			MerchantOrder merchantOrder = merchantOrders.get(payment.getOrder().getId(), options);
			if (merchantOrder == null || blank(merchantOrder.getPreferenceId())) {
				throw invalidProviderResponse();
			}
			return new VerifiedProviderPayment(
				payment.getId().toString(),
				payment.getCollectorId() == null ? null : payment.getCollectorId().toString(),
				merchantOrder.getPreferenceId(), payment.getExternalReference(),
				payment.getTransactionAmount(), payment.getCurrencyId(), payment.isLiveMode(),
				mapStatus(payment.getStatus()),
				payment.getDateLastUpdated() == null ? null
					: payment.getDateLastUpdated().toInstant());
		}
		catch (NumberFormatException exception) {
			throw new CheckoutPaymentException(
				"INVALID_PROVIDER_RESOURCE", "El identificador de pago no es válido.", exception);
		}
		catch (MPApiException | MPException exception) {
			throw gatewayFailure("PAYMENT_LOOKUP_FAILED", exception);
		}
	}

	@Override
	public Optional<VerifiedProviderPayment> findPaymentForPreference(
			PaymentCredential credential, String preferenceId,
			String externalReference, BigDecimal amount,
			String currencyCode) {
		Integer resultCount = null;
		try {
			MPElementsResourcesPage<MerchantOrder> page = searchMerchantOrders(
				credential, preferenceId);
			if (page == null) {
				throw invalidProviderSearchShape(true, null);
			}
			List<MerchantOrder> merchantOrderElements = page.getElements();
			if (merchantOrderElements == null || merchantOrderElements.isEmpty()) {
				return findPaymentByExternalReference(
					credential, preferenceId, externalReference, amount, currencyCode);
			}
			resultCount = merchantOrderElements.size();
			VerifiedProviderPayment candidate = null;
			for (MerchantOrder order : merchantOrderElements) {
				if (order == null || !preferenceId.equals(order.getPreferenceId())) {
					continue;
				}
				validateMerchantOrder(order, credential, externalReference, resultCount);
				for (MerchantOrderPayment summary : order.getPayments()) {
					if (summary == null || summary.getId() == null) {
						throw diagnosticFailure(
							invalidProviderResponse(), "PAYMENT_SELECTION",
							"INVALID_PROVIDER_RESPONSE", null, resultCount);
					}
					VerifiedProviderPayment payment;
					try {
						payment = findPayment(credential, summary.getId().toString());
					}
					catch (CheckoutPaymentException exception) {
						throw enrichSelectionFailure(exception, resultCount);
					}
					if (payment.status() == PaymentResultStatus.APPROVED) {
						return Optional.of(payment);
					}
					if (candidate == null || newer(payment, candidate)) {
						candidate = payment;
					}
				}
			}
			return Optional.ofNullable(candidate);
		}
		catch (MPApiException | MPException exception) {
			throw diagnosticFailure(
				gatewayFailure("PREFERENCE_LOOKUP_FAILED", exception),
				"PROVIDER_SEARCH", "PREFERENCE_LOOKUP_FAILED",
				providerHttpStatus(exception), resultCount);
		}
	}

	@Override
	public ProviderCheckoutState inspectPreference(
			PaymentCredential credential, String preferenceId, String externalReference) {
		try {
			MPElementsResourcesPage<MerchantOrder> page = searchMerchantOrders(
				credential, preferenceId);
			if (page == null || page.getElements() == null || page.getElements().isEmpty()) {
				return ProviderCheckoutState.INCONCLUSIVE;
			}
			boolean matched = false;
			for (MerchantOrder order : page.getElements()) {
				if (order == null || !preferenceId.equals(order.getPreferenceId())) {
					continue;
				}
				matched = true;
				validateMerchantOrder(
					order, credential, externalReference, page.getElements().size());
				if (!order.getPayments().isEmpty()) {
					return ProviderCheckoutState.PAYMENT_RECORDED;
				}
			}
			return matched
				? ProviderCheckoutState.NO_PAYMENT_RECORDED
				: ProviderCheckoutState.INCONCLUSIVE;
		}
		catch (MPApiException | MPException exception) {
			throw gatewayFailure("PREFERENCE_LOOKUP_FAILED", exception);
		}
	}

	private MPElementsResourcesPage<MerchantOrder> searchMerchantOrders(
			PaymentCredential credential, String preferenceId)
			throws MPException, MPApiException {
		MPSearchRequest request = MPSearchRequest.builder()
			.limit(10)
			.offset(0)
			.filters(Map.of("preference_id", preferenceId))
			.build();
		return merchantOrders.search(request, options(credential));
	}

	private MPResultsResourcesPage<Payment> searchPayments(
			PaymentCredential credential, String externalReference)
			throws MPException, MPApiException {
		MPSearchRequest request = MPSearchRequest.builder()
			.limit(50)
			.offset(0)
			.filters(Map.of(
				"external_reference", externalReference,
				"sort", "date_created",
				"criteria", "desc"))
			.build();
		return payments.search(request, options(credential));
	}

	private void validateMerchantOrder(
			MerchantOrder order, PaymentCredential credential,
			String externalReference, int resultCount) {
		if (!externalReference.equals(order.getExternalReference())) {
			throw diagnosticFailure(
				invalidProviderResponse(), "REFERENCE_VALIDATION",
				"REFERENCE_MISMATCH", null, resultCount);
		}
		if (order.getCollector() == null || order.getCollector().getId() == null) {
			throw diagnosticFailure(
				invalidProviderResponse(), "SELLER_VALIDATION",
				"INVALID_PROVIDER_RESPONSE", null, resultCount);
		}
		if (!credential.sellerAccountId().equals(order.getCollector().getId().toString())) {
			throw diagnosticFailure(
				invalidProviderResponse(), "SELLER_VALIDATION",
				"SELLER_MISMATCH", null, resultCount);
		}
		if (order.getPayments() == null) {
			throw diagnosticFailure(
				invalidProviderResponse(), "PAYMENT_SELECTION",
				"INVALID_PROVIDER_RESPONSE", null, resultCount);
		}
	}

	private Optional<VerifiedProviderPayment> findPaymentByExternalReference(
			PaymentCredential credential, String preferenceId,
			String externalReference, BigDecimal amount,
			String currencyCode) {
		Integer resultCount = null;
		try {
			MPResultsResourcesPage<Payment> page = searchPayments(
				credential, externalReference);
			if (page == null || page.getResults() == null) {
				throw diagnosticFailure(
					invalidProviderResponse(), "PROVIDER_SEARCH",
					"INVALID_PROVIDER_RESPONSE", null, null);
			}
			List<Payment> results = page.getResults();
			resultCount = results.size();
			if (results.isEmpty()) {
				return Optional.empty();
			}

			VerifiedProviderPayment selected = null;
			CheckoutPaymentException firstRejectedCandidate = null;
			for (Payment summary : results) {
				if (summary == null || summary.getId() == null) {
					if (firstRejectedCandidate == null) {
						firstRejectedCandidate = diagnosticFailure(
							invalidProviderResponse(), "PAYMENT_SELECTION",
							"INVALID_PROVIDER_RESPONSE", null, resultCount);
					}
					continue;
				}
				VerifiedProviderPayment payment;
				try {
					payment = findPayment(credential, summary.getId().toString());
				}
				catch (CheckoutPaymentException exception) {
					if ("INVALID_PROVIDER_RESPONSE".equals(exception.code())) {
						if (firstRejectedCandidate == null) {
							firstRejectedCandidate = diagnosticFailure(
								exception, "PREFERENCE_VALIDATION",
								"PREFERENCE_NOT_VERIFIABLE", null, resultCount);
						}
						continue;
					}
					if ("UNSUPPORTED_PAYMENT_STATUS".equals(exception.code())) {
						if (firstRejectedCandidate == null) {
							firstRejectedCandidate = diagnosticFailure(
								exception, "STATUS_VALIDATION",
								"UNSUPPORTED_PAYMENT_STATUS", null, resultCount);
						}
						continue;
					}
					throw enrichSelectionFailure(exception, resultCount);
				}

				CheckoutPaymentException rejection = validateFallbackCandidate(
					credential, preferenceId, externalReference, amount,
					currencyCode, payment, resultCount);
				if (rejection != null) {
					if (firstRejectedCandidate == null) {
						firstRejectedCandidate = rejection;
					}
					continue;
				}
				if (selected == null || preferredFallbackPayment(payment, selected)) {
					selected = payment;
				}
			}
			if (selected != null) {
				return Optional.of(selected);
			}
			if (firstRejectedCandidate != null) {
				throw firstRejectedCandidate;
			}
			return Optional.empty();
		}
		catch (MPApiException | MPException exception) {
			throw diagnosticFailure(
				gatewayFailure("PAYMENT_SEARCH_FAILED", exception),
				"PROVIDER_SEARCH", "PAYMENT_SEARCH_FAILED",
				providerHttpStatus(exception), resultCount);
		}
	}

	private CheckoutPaymentException validateFallbackCandidate(
			PaymentCredential credential, String preferenceId,
			String externalReference, BigDecimal amount,
			String currencyCode, VerifiedProviderPayment payment,
			int resultCount) {
		if (!credential.sellerAccountId().equals(payment.sellerAccountId())) {
			return candidateRejection("SELLER_VALIDATION", "SELLER_MISMATCH", resultCount);
		}
		if (credential.environment() == PaymentEnvironment.PRODUCTION
				&& !payment.liveMode()) {
			return candidateRejection(
				"ENVIRONMENT_VALIDATION", "ENVIRONMENT_MISMATCH", resultCount);
		}
		if (!preferenceId.equals(payment.preferenceId())) {
			return candidateRejection(
				"PREFERENCE_VALIDATION", "PREFERENCE_NOT_VERIFIABLE", resultCount);
		}
		if (!externalReference.equals(payment.externalReference())) {
			return candidateRejection(
				"REFERENCE_VALIDATION", "REFERENCE_MISMATCH", resultCount);
		}
		if (amount == null || payment.amount() == null
				|| amount.compareTo(payment.amount()) != 0) {
			return candidateRejection("AMOUNT_VALIDATION", "AMOUNT_MISMATCH", resultCount);
		}
		if (!currencyCode.equals(payment.currencyCode())) {
			return candidateRejection(
				"CURRENCY_VALIDATION", "CURRENCY_MISMATCH", resultCount);
		}
		return null;
	}

	private CheckoutPaymentException candidateRejection(
			String stage, String reason, int resultCount) {
		return diagnosticFailure(
			new CheckoutPaymentException(
				"PAYMENT_VALIDATION_FAILED",
				"El pago verificado no coincide con el pedido."),
			stage, reason, null, resultCount);
	}

	private boolean preferredFallbackPayment(
			VerifiedProviderPayment candidate, VerifiedProviderPayment current) {
		if (candidate.status() == PaymentResultStatus.APPROVED
				&& current.status() != PaymentResultStatus.APPROVED) {
			return true;
		}
		if (candidate.status() != PaymentResultStatus.APPROVED
				&& current.status() == PaymentResultStatus.APPROVED) {
			return false;
		}
		return newer(candidate, current);
	}

	private boolean newer(
			VerifiedProviderPayment candidate, VerifiedProviderPayment current) {
		if (candidate.providerUpdatedAt() == null) return false;
		return current.providerUpdatedAt() == null
			|| candidate.providerUpdatedAt().isAfter(current.providerUpdatedAt());
	}

	private MPRequestOptions options(PaymentCredential credential) {
		return MPRequestOptions.builder()
			.accessToken(credential.accessToken())
			.connectionTimeout(Math.toIntExact(properties.connectTimeout().toMillis()))
			.connectionRequestTimeout(Math.toIntExact(properties.connectTimeout().toMillis()))
			.socketTimeout(Math.toIntExact(properties.readTimeout().toMillis()))
			.build();
	}

	private PaymentResultStatus mapStatus(String status) {
		if (status == null) {
			throw invalidProviderResponse();
		}
		return switch (status.toLowerCase(Locale.ROOT)) {
			case "approved" -> PaymentResultStatus.APPROVED;
			case "pending", "in_process", "authorized" -> PaymentResultStatus.PENDING;
			case "rejected", "cancelled" -> PaymentResultStatus.REJECTED;
			default -> throw new CheckoutPaymentException(
				"UNSUPPORTED_PAYMENT_STATUS", "Mercado Pago devolvió un estado no soportado.");
		};
	}

	private CheckoutPaymentException invalidProviderResponse() {
		return new CheckoutPaymentException(
			"INVALID_PROVIDER_RESPONSE", "Mercado Pago devolvió una respuesta incompleta.");
	}

	private CheckoutPaymentException gatewayFailure(String code, Exception cause) {
		return new CheckoutPaymentException(
			code, "No se pudo completar la operación con Mercado Pago.", cause);
	}

	private CheckoutPaymentException enrichSelectionFailure(
			CheckoutPaymentException exception, int resultCount) {
		CheckoutPaymentException.ReconciliationDiagnostics diagnostics =
			exception.reconciliationDiagnostics();
		if (diagnostics != null) {
			return exception.withReconciliationDiagnostics(
				diagnostics.stage(), diagnostics.reason(), diagnostics.providerHttpStatus(),
				diagnostics.providerErrorCode(), resultCount);
		}
		String stage = exception.code().equals("UNSUPPORTED_PAYMENT_STATUS")
			? "STATUS_VALIDATION" : "PAYMENT_SELECTION";
		return diagnosticFailure(
			exception, stage, exception.code(), providerHttpStatus(exception), resultCount);
	}

	private CheckoutPaymentException diagnosticFailure(
			CheckoutPaymentException exception, String stage, String reason,
			Integer providerHttpStatus, Integer resultCount) {
		return exception.withReconciliationDiagnostics(
			stage, reason, providerHttpStatus, providerErrorCode(exception), resultCount);
	}

	private CheckoutPaymentException invalidProviderSearchShape(
			boolean providerResponseNull, Boolean merchantOrdersCollectionNull) {
		return invalidProviderResponse().withReconciliationDiagnostics(
			"PROVIDER_SEARCH", "INVALID_PROVIDER_RESPONSE", null, null, null,
			providerResponseNull, merchantOrdersCollectionNull, null, false);
	}

	private Integer providerHttpStatus(Exception exception) {
		Throwable current = exception;
		while (current != null) {
			if (current instanceof MPApiException apiException) {
				return apiException.getStatusCode();
			}
			current = current.getCause();
		}
		return null;
	}

	private String providerErrorCode(Exception exception) {
		Throwable current = exception;
		while (current != null) {
			if (current instanceof MPApiException apiException
					&& apiException.getApiResponse() != null
					&& apiException.getApiResponse().getContent() != null) {
				Matcher matcher = PROVIDER_ERROR_CODE.matcher(
					apiException.getApiResponse().getContent());
				return matcher.find() ? matcher.group(1) : null;
			}
			current = current.getCause();
		}
		return null;
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
