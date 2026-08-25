package com.comercioflex.notification.infrastructure;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.comercioflex.order.application.AdminOrderDetail;
import com.comercioflex.order.domain.GuestOrderItem;
import com.comercioflex.payment.application.BankTransferPayment;
import com.comercioflex.tenant.domain.StoreSettings;

@Component
class EmailTemplateRenderer {
	private static final Locale LOCALE = Locale.forLanguageTag("es-AR");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(
		"dd/MM/yyyy, HH:mm", LOCALE);

	RenderedEmail orderConfirmed(AdminOrderDetail order, StoreSettings store, Instant confirmedAt) {
		String number = orderNumber(order.number());
		Map<String, String> values = common(order, store);
		values.put("itemsHtml", itemsHtml(order, store.currencyCode()));
		values.put("itemsText", itemsText(order, store.currencyCode()));
		values.put("fulfillment", pickup(store));
		values.put("eventDate", date(confirmedAt, store.timezone()));
		return render("order-confirmed", "Tu pedido %s fue confirmado".formatted(number), values);
	}

	RenderedEmail receiptRejected(AdminOrderDetail order, BankTransferPayment payment,
			StoreSettings store) {
		String number = orderNumber(order.number());
		Map<String, String> values = common(order, store);
		values.put("rejectionReason", payment.rejectionReason());
		values.put("reservationExpiresAt", date(payment.reservationExpiresAt(), store.timezone()));
		return render("bank-transfer-receipt-rejected",
			"Necesitamos que revises el comprobante de tu pedido %s".formatted(number), values);
	}

	private Map<String, String> common(AdminOrderDetail order, StoreSettings store) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("storeName", store.storeName());
		values.put("customerName", order.customerName());
		values.put("orderNumber", orderNumber(order.number()));
		values.put("total", money(order.subtotal(), order.currencyCode()));
		return values;
	}

	private RenderedEmail render(String name, String subject, Map<String, String> values) {
		String html = resource("email-templates/" + name + ".html");
		String text = resource("email-templates/" + name + ".txt");
		for (Map.Entry<String, String> entry : values.entrySet()) {
			html = html.replace("{{" + entry.getKey() + "}}",
				"itemsHtml".equals(entry.getKey()) ? entry.getValue() : escapeHtml(entry.getValue()));
			text = text.replace("{{" + entry.getKey() + "}}", entry.getValue());
		}
		return new RenderedEmail(subject, html, text);
	}

	private String itemsHtml(AdminOrderDetail order, String currency) {
		StringBuilder result = new StringBuilder();
		for (GuestOrderItem item : order.items()) {
			result.append("<tr><td>").append(escapeHtml(item.productName()))
				.append(" × ").append(quantity(item.quantity())).append("</td><td class=\"amount\">")
				.append(escapeHtml(money(item.lineTotal(), currency))).append("</td></tr>");
		}
		return result.toString();
	}

	private String itemsText(AdminOrderDetail order, String currency) {
		return order.items().stream().map(item -> "- %s × %s — %s".formatted(
			item.productName(), quantity(item.quantity()), money(item.lineTotal(), currency)))
			.reduce((left, right) -> left + System.lineSeparator() + right).orElse("-");
	}

	private String pickup(StoreSettings store) {
		StringBuilder value = new StringBuilder("Retiro en el comercio");
		if (store.pickupAddress() != null && !store.pickupAddress().isBlank()) {
			value.append(" — ").append(store.pickupAddress());
		}
		if (store.pickupInstructions() != null && !store.pickupInstructions().isBlank()) {
			value.append(". ").append(store.pickupInstructions());
		}
		return value.toString();
	}

	private String money(BigDecimal value, String currencyCode) {
		NumberFormat format = NumberFormat.getCurrencyInstance(LOCALE);
		format.setCurrency(Currency.getInstance(currencyCode));
		return format.format(value);
	}

	private String quantity(BigDecimal value) {
		return value.setScale(3, RoundingMode.UNNECESSARY).stripTrailingZeros().toPlainString();
	}

	private String date(Instant value, String timezone) {
		ZoneId zone;
		try { zone = ZoneId.of(timezone); }
		catch (RuntimeException exception) { zone = ZoneId.of("America/Argentina/Buenos_Aires"); }
		return DATE_TIME.withZone(zone).format(value);
	}

	private String orderNumber(long number) { return "ORD-%06d".formatted(number); }

	private String resource(String path) {
		try {
			return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException exception) {
			throw new IllegalStateException("No se pudo cargar el template " + path, exception);
		}
	}

	private String escapeHtml(String value) {
		if (value == null) return "";
		return value.replace("&", "&amp;").replace("<", "&lt;")
			.replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
	}
}
