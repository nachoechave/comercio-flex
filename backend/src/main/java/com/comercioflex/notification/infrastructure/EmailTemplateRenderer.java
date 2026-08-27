package com.comercioflex.notification.infrastructure;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.comercioflex.catalog.domain.VariantOptionValue;
import com.comercioflex.order.application.AdminOrderDetail;
import com.comercioflex.order.domain.GuestOrderItem;
import com.comercioflex.payment.application.BankTransferPayment;
import com.comercioflex.tenant.domain.StoreSettings;

@Component
class EmailTemplateRenderer {
	private static final Locale LOCALE = Locale.forLanguageTag("es-AR");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(
		"dd/MM/yyyy, HH:mm", LOCALE);
	private static final Set<String> HTML_FRAGMENTS = Set.of("itemsHtml", "logoHtml");

	RenderedEmail orderConfirmed(AdminOrderDetail order, StoreSettings store,
			EmailBranding branding, Instant confirmedAt) {
		String number = orderNumber(order.number());
		Map<String, String> values = common(order, branding);
		values.put("itemsHtml", itemsHtml(order, store.currencyCode()));
		values.put("itemsText", itemsText(order, store.currencyCode()));
		values.put("fulfillment", pickup(store));
		values.put("eventDate", date(confirmedAt, store.timezone()));
		return render("order-confirmed", "Tu pedido %s fue confirmado".formatted(number), values);
	}

	RenderedEmail receiptRejected(AdminOrderDetail order, BankTransferPayment payment,
			StoreSettings store, EmailBranding branding) {
		String number = orderNumber(order.number());
		Map<String, String> values = common(order, branding);
		values.put("itemsHtml", itemsHtml(order, store.currencyCode()));
		values.put("itemsText", itemsText(order, store.currencyCode()));
		values.put("fulfillment", pickup(store));
		values.put("rejectionReason", payment.rejectionReason());
		values.put("reservationExpiresAt", date(payment.reservationExpiresAt(), store.timezone()));
		return render("bank-transfer-receipt-rejected",
			"Necesitamos que revises el comprobante de tu pedido %s".formatted(number), values);
	}

	private Map<String, String> common(AdminOrderDetail order, EmailBranding branding) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("storeName", branding.storeName());
		values.put("headerColor", branding.headerColor());
		values.put("headerTextColor", branding.headerTextColor());
		values.put("logoHtml", logoHtml(branding));
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
				HTML_FRAGMENTS.contains(entry.getKey()) ? entry.getValue() : escapeHtml(entry.getValue()));
			text = text.replace("{{" + entry.getKey() + "}}", entry.getValue());
		}
		return new RenderedEmail(subject, html, text);
	}

	private String itemsHtml(AdminOrderDetail order, String currency) {
		StringBuilder result = new StringBuilder();
		for (GuestOrderItem item : order.items()) {
			result.append("<tr><td style=\"padding:16px 0;border-bottom:1px solid #E5E7EB;"
				+ "vertical-align:top;color:#172033;font-family:Arial,sans-serif;font-size:15px;line-height:22px;\">")
				.append("<strong>").append(escapeHtml(item.productName())).append("</strong>")
				.append(itemOptionsHtml(item))
				.append("<br><span style=\"color:#64748B;font-size:14px;\">Cantidad: ")
				.append(quantity(item.quantity())).append("</span></td>")
				.append("<td align=\"right\" style=\"padding:16px 0 16px 12px;border-bottom:1px solid #E5E7EB;"
					+ "vertical-align:top;white-space:nowrap;color:#172033;font-family:Arial,sans-serif;"
					+ "font-size:15px;line-height:22px;font-weight:bold;\">")
				.append(escapeHtml(money(item.lineTotal(), currency))).append("</td></tr>");
		}
		return result.toString();
	}

	private String itemsText(AdminOrderDetail order, String currency) {
		return order.items().stream().map(item -> "- %s%s%n  Cantidad: %s%n  %s".formatted(
			item.productName(), itemOptionsText(item), quantity(item.quantity()),
			money(item.lineTotal(), currency)))
			.reduce((left, right) -> left + System.lineSeparator() + right).orElse("-");
	}

	private String itemOptionsHtml(GuestOrderItem item) {
		String options = itemOptionsLabel(item);
		return options.isEmpty() ? "" : "<br><span style=\"color:#64748B;font-size:13px;\">"
			+ escapeHtml(options) + "</span>";
	}

	private String itemOptionsText(GuestOrderItem item) {
		String options = itemOptionsLabel(item);
		return options.isEmpty() ? "" : " · " + options;
	}

	private String itemOptionsLabel(GuestOrderItem item) {
		Map<String, VariantOptionValue> unique = new LinkedHashMap<>();
		if (item.options() != null) {
			item.options().forEach(option -> addOption(unique, option));
		}
		addOption(unique, new VariantOptionValue("Talle", item.size()));
		addOption(unique, new VariantOptionValue("Color", item.color()));
		return unique.values().stream()
			.map(option -> option.name() + ": " + option.value())
			.collect(Collectors.joining(" · "));
	}

	private void addOption(Map<String, VariantOptionValue> unique, VariantOptionValue option) {
		if (option == null || option.name() == null || option.value() == null) return;
		String name = cleanOptionPart(option.name());
		String value = cleanOptionPart(option.value());
		if (name.isEmpty() || value.isEmpty()) return;
		String key = normalizeOptionPart(name) + '\u001f' + normalizeOptionPart(value);
		unique.putIfAbsent(key, new VariantOptionValue(name, value));
	}

	private String cleanOptionPart(String value) {
		return value.strip().replaceAll("\\s+", " ");
	}

	private String normalizeOptionPart(String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFKC)
			.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private String logoHtml(EmailBranding branding) {
		if (branding.logoUrl() == null) return "";
		return ("<div style=\"margin:0 0 14px;\"><img src=\"%s\" alt=\"Logo de %s\" width=\"180\" "
			+ "style=\"display:inline-block;width:auto;max-width:180px;height:auto;max-height:72px;"
			+ "padding:8px;background:#FFFFFF;border-radius:8px;border:0;\"></div>")
			.formatted(escapeHtml(branding.logoUrl()), escapeHtml(branding.storeName()));
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
			.replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
			.replace("\u00a0", "&nbsp;");
	}
}
