package com.comercioflex.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.comercioflex.catalog.domain.VariantOptionValue;
import com.comercioflex.order.application.AdminOrderDetail;
import com.comercioflex.order.domain.FulfillmentType;
import com.comercioflex.order.domain.GuestOrderItem;
import com.comercioflex.order.domain.OrderPaymentMethod;
import com.comercioflex.order.domain.OrderStatus;
import com.comercioflex.payment.application.BankTransferPayment;
import com.comercioflex.payment.domain.BankTransferStatus;
import com.comercioflex.tenant.domain.StoreSettings;

class EmailTemplateRendererTests {

    private static final UUID ORDER_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static final Instant EVENT_AT =
            Instant.parse("2026-08-27T12:52:00Z");

    private final EmailTemplateRenderer renderer =
            new EmailTemplateRenderer();

    @Test
    void confirmedOrderHasEquivalentUtf8HtmlAndTextWithArgentineFormats() {
        RenderedEmail result = renderer.orderConfirmed(
                order("Ignacio", "Canguro"),
                store(),
                new EmailBranding(
                        "Ñandú Tienda",
                        "#6D3CE7",
                        "#FFFFFF",
                        "https://cdn.example.com/logo.png"),
                EVENT_AT);

        assertThat(result.html())
                .contains(
                        "Ñandú Tienda",
                        "Ignacio",
                        "ORD-000014",
                        "Canguro",
                        "Cantidad: 1",
                        "Talle: M",
                        "Color: Azul",
                        "$&nbsp;19.999,00",
                        "60 y 123",
                        "27/08/2026, 09:52",
                        "background:#6D3CE7",
                        "color:#FFFFFF",
                        "src=\"https://cdn.example.com/logo.png\"",
                        "max-width:640px")
                .doesNotContain(
                        "lookupToken",
                        "lookup-token",
                        "localhost",
                        ORDER_ID.toString(),
                        "<style>");

        assertThat(result.text())
                .contains(
                        "Ñandú Tienda",
                        "Ignacio",
                        "ORD-000014",
                        "Canguro",
                        "Cantidad: 1",
                        "Talle: M",
                        "Color: Azul",
                        "$\u00a019.999,00",
                        "Retiro en el comercio",
                        "60 y 123",
                        "27/08/2026, 09:52")
                .doesNotContain(
                        "lookupToken",
                        "localhost");
    }

    @Test
    void confirmedOrderRendersEquivalentLegacyAndGenericOptionsOnlyOnce() {
        GuestOrderItem item = item(
                "Remera térmica",
                "M",
                "Negro",
                List.of(
                        new VariantOptionValue("Talle", "M"),
                        new VariantOptionValue("Color", "Negro"),
                        new VariantOptionValue("Material", "Algodón")));

        RenderedEmail result = renderer.orderConfirmed(
                order("Ignacio", List.of(item)),
                store(),
                new EmailBranding(
                        "Ñandú Tienda",
                        "#6D3CE7",
                        "#FFFFFF",
                        "https://cdn.example.com/logo.png"),
                EVENT_AT);

        assertThat(result.html())
                .contains(
                        "Talle: M · Color: Negro · Material: Algodón",
                        "Ñandú Tienda",
                        "background:#6D3CE7",
                        "src=\"https://cdn.example.com/logo.png\"")
                .containsOnlyOnce("Talle: M")
                .containsOnlyOnce("Color: Negro");

        assertThat(result.text())
                .contains(
                        "Talle: M · Color: Negro · Material: Algodón",
                        "Ñandú Tienda")
                .containsOnlyOnce("Talle: M")
                .containsOnlyOnce("Color: Negro");
    }

    @Test
    void confirmedOrderSafelyDeduplicatesNormalizedPairsAndPreservesDifferentOptions() {
        GuestOrderItem item = item(
                "Café edición limitada",
                "M",
                null,
                List.of(
                        new VariantOptionValue(" Talle ", " M "),
                        new VariantOptionValue("talle", "m"),
                        new VariantOptionValue("Talle", "L"),
                        new VariantOptionValue("Presentación", "Cápsulas"),
                        new VariantOptionValue("Peso", "500 g")));

        RenderedEmail result = renderer.orderConfirmed(
                order("María", List.of(item)),
                store(),
                new EmailBranding(
                        "Ñandú Tienda",
                        "#6D3CE7",
                        "#FFFFFF",
                        null),
                EVENT_AT);

        assertThat(result.html())
                .contains(
                        "Talle: M",
                        "Talle: L",
                        "Presentación: Cápsulas",
                        "Peso: 500 g")
                .containsOnlyOnce("Talle: M")
                .doesNotContain("talle: m");

        assertThat(result.text())
                .contains(
                        "Café edición limitada",
                        "Talle: M",
                        "Talle: L",
                        "Presentación: Cápsulas",
                        "Peso: 500 g")
                .containsOnlyOnce("Talle: M")
                .doesNotContain("talle: m");
    }

    @Test
    void confirmedOrderSupportsProductsWithoutOptionsAndMultipleVariants() {
        GuestOrderItem simple =
                item("Gift card", null, null, List.of());

        GuestOrderItem variant = item(
                "Buzo",
                "L",
                "Azul",
                List.of(
                        new VariantOptionValue("Talle", "L"),
                        new VariantOptionValue("Color", "Azul")));

        RenderedEmail result = renderer.orderConfirmed(
                order("Ana", List.of(simple, variant)),
                store(),
                new EmailBranding(
                        "Tienda Sur",
                        "#8B2F45",
                        "#FFFFFF",
                        null),
                EVENT_AT);

        assertThat(result.html())
                .contains(
                        "Gift card",
                        "Buzo",
                        "Talle: L · Color: Azul")
                .containsOnlyOnce("Talle: L")
                .containsOnlyOnce("Color: Azul")
                .doesNotContain(
                        "Gift card</strong><br><span style=\"color:#64748B;font-size:13px;\">");

        assertThat(result.text())
                .contains(
                        "- Gift card"
                                + System.lineSeparator()
                                + "  Cantidad: 1",
                        "- Buzo · Talle: L · Color: Azul")
                .containsOnlyOnce("Talle: L")
                .containsOnlyOnce("Color: Azul");
    }

    @Test
    void dynamicOrderAndStoreDataCannotInjectHtml() {
        RenderedEmail result = renderer.orderConfirmed(
                order(
                        "<img src=x onerror=alert(1)>",
                        "<script>bad()</script>"),
                store("<b>dirección</b>"),
                new EmailBranding(
                        "<svg onload=bad>",
                        "#334155",
                        "#FFFFFF",
                        null),
                EVENT_AT);

        assertThat(result.html())
                .contains(
                        "&lt;img src=x onerror=alert(1)&gt;",
                        "&lt;script&gt;bad()&lt;/script&gt;",
                        "&lt;b&gt;dirección&lt;/b&gt;",
                        "&lt;svg onload=bad&gt;")
                .doesNotContain(
                        "<img src=x",
                        "<script>bad()",
                        "<b>dirección</b>",
                        "<svg onload");
    }

    @Test
    void rejectedReceiptIncludesReasonItemsTotalExpiryAndPendingStateInBothBodies() {
        RenderedEmail result = renderer.receiptRejected(
                order("Ana", "Remera"),
                payment(),
                store(),
                new EmailBranding(
                        "Tienda Sur",
                        "#8B2F45",
                        "#FFFFFF",
                        null));

        assertThat(result.html())
                .contains(
                        "Tienda Sur",
                        "ORD-000014",
                        "Remera",
                        "$&nbsp;19.999,00",
                        "Ilegible &lt;revisar&gt;",
                        "28/08/2026, 09:56",
                        "sigue pendiente",
                        "No adjuntamos comprobantes por email")
                .doesNotContain(
                        "Ilegible <revisar>",
                        "<img",
                        "href=",
                        "lookup",
                        "private-object-key");

        assertThat(result.text())
                .contains(
                        "Tienda Sur",
                        "ORD-000014",
                        "Remera",
                        "$\u00a019.999,00",
                        "Ilegible <revisar>",
                        "28/08/2026, 09:56",
                        "sigue pendiente",
                        "No adjuntamos comprobantes por email");
    }

    @Test
    void confirmedBankTransferOrderShowsListPriceDiscountAndFinalTotal() {
        GuestOrderItem item = new GuestOrderItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Tabla de surf",
                null,
                null,
                List.of(),
                "UNIT",
                new BigDecimal("25000.00"),
                new BigDecimal("1.000"),
                new BigDecimal("25000.00"));

        AdminOrderDetail bankTransferOrder =
                new AdminOrderDetail(
                        ORDER_ID,
                        14L,
                        OrderStatus.CONFIRMED,
                        FulfillmentType.PICKUP,
                        OrderPaymentMethod.BANK_TRANSFER,
                        "Ignacio",
                        "1155551234",
                        "cliente@example.com",
                        null,
                        "ARS",
                        new BigDecimal("25000.00"),
                        new BigDecimal("20.00"),
                        new BigDecimal("5000.00"),
                        new BigDecimal("20000.00"),
                        Instant.parse("2026-08-28T12:56:00Z"),
                        EVENT_AT.minusSeconds(300),
                        2L,
                        List.of(item),
                        List.of());

        RenderedEmail result =
                renderer.orderConfirmed(
                        bankTransferOrder,
                        store(),
                        new EmailBranding(
                                "La Ola Madre",
                                "#6D3CE7",
                                "#FFFFFF",
                                null),
                        EVENT_AT,
                        "Transferencia bancaria");

        assertThat(result.html())
                .contains(
                        "La Ola Madre",
                        "Transferencia bancaria",
                        "Precio de lista",
                        "$&nbsp;25.000,00",
                        "Descuento por transferencia (20%)",
                        "-$&nbsp;5.000,00",
                        "Total",
                        "$&nbsp;20.000,00")
                .doesNotContain(
                        "{{pricingHtml}}",
                        "{{pricingText}}",
                        "{{paymentMethod}}",
                        "{{eventDate}}");

        assertThat(result.text())
                .contains(
                        "La Ola Madre",
                        "Transferencia bancaria",
                        "Precio de lista: $\u00a025.000,00",
                        "Descuento por transferencia (20%): -$\u00a05.000,00",
                        "Total: $\u00a020.000,00")
                .doesNotContain(
                        "{{pricingHtml}}",
                        "{{pricingText}}",
                        "{{paymentMethod}}",
                        "{{eventDate}}");
    }

    private AdminOrderDetail order(
            String customer,
            String product) {

        return order(
                customer,
                List.of(
                        item(
                                product,
                                "M",
                                "Azul",
                                List.of(
                                        new VariantOptionValue(
                                                "Material",
                                                "Algodón")))));
    }

    private AdminOrderDetail order(
            String customer,
            List<GuestOrderItem> items) {

        return new AdminOrderDetail(
                ORDER_ID,
                14L,
                OrderStatus.CONFIRMED,
                FulfillmentType.PICKUP,
                OrderPaymentMethod.MERCADO_PAGO,
                customer,
                "1155551234",
                "cliente@example.com",
                null,
                "ARS",
                new BigDecimal("19999.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("19999.00"),
                Instant.parse("2026-08-28T12:56:00Z"),
                EVENT_AT.minusSeconds(300),
                2L,
                items,
                List.of());
    }

    private GuestOrderItem item(
            String product,
            String size,
            String color,
            List<VariantOptionValue> options) {

        return new GuestOrderItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                product,
                size,
                color,
                options,
                "UNIT",
                new BigDecimal("19999.00"),
                new BigDecimal("1.000"),
                new BigDecimal("19999.00"));
    }

    private BankTransferPayment payment() {
        return new BankTransferPayment(
                20L,
                UUID.randomUUID(),
                10L,
                ORDER_ID,
                14L,
                "Ana",
                new BigDecimal("19999.00"),
                "ARS",
                Instant.parse("2026-08-28T12:56:00Z"),
                1,
                BankTransferStatus.REJECTED,
                "private-object-key",
                "receipt.pdf",
                "application/pdf",
                100L,
                EVENT_AT.minusSeconds(60),
                EVENT_AT,
                7L,
                "Ilegible <revisar>",
                EVENT_AT,
                EVENT_AT,
                1L);
    }

    private StoreSettings store() {
        return store("60 y 123");
    }

    private StoreSettings store(String address) {
        return new StoreSettings(
                "Tienda",
                "ARS",
                "America/Argentina/Buenos_Aires",
                null,
                null,
                address,
                "Traé tu DNI",
                true,
                BigDecimal.ZERO,
                "Banco",
                "Tienda",
                "TIENDA",
                null,
                null,
                null);
    }
}