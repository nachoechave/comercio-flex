package com.comercioflex.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.comercioflex.notification.application.NotificationOutboxRepository;
import com.comercioflex.notification.application.TransactionalEmail;
import com.comercioflex.order.application.AdminOrderDetail;
import com.comercioflex.order.application.AdminOrderRepository;
import com.comercioflex.order.domain.FulfillmentType;
import com.comercioflex.order.domain.GuestOrderItem;
import com.comercioflex.order.domain.OrderPaymentMethod;
import com.comercioflex.order.domain.OrderStatus;
import com.comercioflex.payment.application.BankTransferPayment;
import com.comercioflex.payment.domain.BankTransferStatus;
import com.comercioflex.tenant.application.StoreSettingsRepository;
import com.comercioflex.tenant.domain.StoreSettings;

class OutboxCustomerNotificationServiceTests {

    private static final UUID ORDER_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static final UUID PAYMENT_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");

    private static final UUID SECOND_PAYMENT_ID =
            UUID.fromString("33333333-3333-4333-8333-333333333333");

    private static final Instant NOW =
            Instant.parse("2026-08-25T15:05:00Z");

    private final NotificationOutboxRepository outbox =
            mock(NotificationOutboxRepository.class);

    private final AdminOrderRepository orders =
            mock(AdminOrderRepository.class);

    private final StoreSettingsRepository stores =
            mock(StoreSettingsRepository.class);

    private final EmailBrandingResolver branding =
            mock(EmailBrandingResolver.class);

    private OutboxCustomerNotificationService service;

    @BeforeEach
    void setUp() {
        when(stores.findCurrent())
                .thenReturn(Optional.of(store()));

        when(branding.resolve(any()))
                .thenReturn(
                        new EmailBranding(
                                "Tienda A",
                                "#6D3CE7",
                                "#FFFFFF",
                                null));

        service = new OutboxCustomerNotificationService(
                outbox,
                orders,
                stores,
                new EmailTemplateRenderer(),
                branding);
    }

    @Test
    void confirmedOrderCreatesOneSafeIdempotentEvent() {
        when(outbox.enqueue(any(), any(), any(), any(), any()))
                .thenReturn(true)
                .thenReturn(false);

        service.orderConfirmed(order(), NOW, "Mercado Pago");
        service.orderConfirmed(order(), NOW, "Mercado Pago");

        ArgumentCaptor<TransactionalEmail> email =
                ArgumentCaptor.forClass(TransactionalEmail.class);

        verify(outbox, org.mockito.Mockito.times(2))
                .enqueue(
                        eq("ORDER_CONFIRMED:" + ORDER_ID),
                        eq("ORDER_CONFIRMED"),
                        eq(ORDER_ID),
                        eq(null),
                        email.capture());

        assertThat(email.getValue().subject())
                .isEqualTo("Tu pedido ORD-000011 fue confirmado");

        assertThat(email.getValue().htmlBody())
                .contains(
                        "Tienda A",
                        "Ana Pérez",
                        "Remera",
                        "25/08/2026, 12:05",
                        "Pago aprobado",
                        "Mercado Pago")
                .doesNotContain(
                        "lookup",
                        "token",
                        ORDER_ID.toString());
    }

    @Test
    void rejectedMercadoPagoAttemptCreatesOneSafeIdempotentEvent() {
        when(outbox.enqueue(any(), any(), any(), any(), any()))
                .thenReturn(true)
                .thenReturn(false);

        service.mercadoPagoPaymentRejected(
                order(),
                PAYMENT_ID,
                NOW);

        service.mercadoPagoPaymentRejected(
                order(),
                PAYMENT_ID,
                NOW);

        ArgumentCaptor<TransactionalEmail> email =
                ArgumentCaptor.forClass(TransactionalEmail.class);

        verify(outbox, org.mockito.Mockito.times(2))
                .enqueue(
                        eq("MERCADO_PAGO_PAYMENT_REJECTED:" + PAYMENT_ID),
                        eq("MERCADO_PAGO_PAYMENT_REJECTED"),
                        eq(ORDER_ID),
                        eq(null),
                        email.capture());

        assertThat(email.getValue().htmlBody())
                .contains(
                        "Tienda A",
                        "ORD-000011",
                        "Mercado Pago",
                        "$&nbsp;19.999,00",
                        "El pago no pudo completarse")
                .doesNotContain(
                        "lookup",
                        "access token",
                        "refresh token",
                        "tarjeta",
                        PAYMENT_ID.toString(),
                        ORDER_ID.toString());

        assertThat(email.getValue().textBody())
                .contains(
                        "Tienda A",
                        "ORD-000011",
                        "Mercado Pago",
                        "$\u00a019.999,00",
                        "EL PAGO NO PUDO COMPLETARSE")
                .doesNotContain(
                        "lookup",
                        "access token",
                        "refresh token",
                        "tarjeta",
                        PAYMENT_ID.toString(),
                        ORDER_ID.toString());
    }

    @Test
    void rejectedReceiptKeepsTheOrderPendingAndUsesThePaymentAttemptAsKey() {
        when(orders.findDetail(ORDER_ID))
                .thenReturn(Optional.of(order()));

        when(outbox.enqueue(any(), any(), any(), any(), any()))
                .thenReturn(true);

        service.bankTransferReceiptRejected(
                payment(),
                NOW);

        ArgumentCaptor<TransactionalEmail> email =
                ArgumentCaptor.forClass(TransactionalEmail.class);

        verify(outbox)
                .enqueue(
                        eq("BANK_TRANSFER_RECEIPT_REJECTED:" + PAYMENT_ID),
                        eq("BANK_TRANSFER_RECEIPT_REJECTED"),
                        eq(ORDER_ID),
                        eq(20L),
                        email.capture());

        assertThat(email.getValue().textBody())
                .contains(
                        "Tienda A",
                        "Remera",
                        "comprobante",
                        "No se distingue el importe",
                        "sigue pendiente")
                .doesNotContain(
                        "pedido fue rechazado",
                        "pedido rechazado");
    }

    @Test
    void anotherReceiptAttemptCanCreateItsOwnRejectionNotification() {
        when(orders.findDetail(ORDER_ID))
                .thenReturn(Optional.of(order()));

        when(outbox.enqueue(any(), any(), any(), any(), any()))
                .thenReturn(true);

        service.bankTransferReceiptRejected(
                payment(),
                NOW);

        service.bankTransferReceiptRejected(
                payment(SECOND_PAYMENT_ID, 21L),
                NOW);

        verify(outbox)
                .enqueue(
                        eq("BANK_TRANSFER_RECEIPT_REJECTED:" + PAYMENT_ID),
                        eq("BANK_TRANSFER_RECEIPT_REJECTED"),
                        eq(ORDER_ID),
                        eq(20L),
                        any());

        verify(outbox)
                .enqueue(
                        eq("BANK_TRANSFER_RECEIPT_REJECTED:" + SECOND_PAYMENT_ID),
                        eq("BANK_TRANSFER_RECEIPT_REJECTED"),
                        eq(ORDER_ID),
                        eq(21L),
                        any());
    }

    @Test
    void eachEventSnapshotsOnlyTheBrandingOfItsCurrentTenant() {
        StoreSettings storeA =
                store("Tienda A");

        StoreSettings storeB =
                store("Tienda B");

        when(stores.findCurrent())
                .thenReturn(
                        Optional.of(storeA),
                        Optional.of(storeB));

        when(branding.resolve(storeA))
                .thenReturn(
                        new EmailBranding(
                                "Tienda A",
                                "#112233",
                                "#FFFFFF",
                                null));

        when(branding.resolve(storeB))
                .thenReturn(
                        new EmailBranding(
                                "Tienda B",
                                "#F5E942",
                                "#172033",
                                null));

        when(outbox.enqueue(any(), any(), any(), any(), any()))
                .thenReturn(true);

        service.orderConfirmed(order(), NOW);
        service.orderConfirmed(order(), NOW);

        ArgumentCaptor<TransactionalEmail> emails =
                ArgumentCaptor.forClass(TransactionalEmail.class);

        verify(outbox, org.mockito.Mockito.times(2))
                .enqueue(
                        any(),
                        eq("ORDER_CONFIRMED"),
                        eq(ORDER_ID),
                        eq(null),
                        emails.capture());

        assertThat(emails.getAllValues().get(0).htmlBody())
                .contains(
                        "Tienda A",
                        "background:#112233")
                .doesNotContain(
                        "Tienda B",
                        "#F5E942");

        assertThat(emails.getAllValues().get(1).htmlBody())
                .contains(
                        "Tienda B",
                        "background:#F5E942")
                .doesNotContain(
                        "Tienda A",
                        "#112233");
    }

    @Test
    void anOrderWithoutCustomerEmailDoesNotCreateOutboxData() {
        AdminOrderDetail baseOrder =
                order();

        AdminOrderDetail withoutEmail =
                new AdminOrderDetail(
                        baseOrder.id(),
                        baseOrder.number(),
                        baseOrder.status(),
                        baseOrder.fulfillmentType(),
                        baseOrder.paymentMethod(),
                        baseOrder.customerName(),
                        baseOrder.customerPhone(),
                        null,
                        baseOrder.notes(),
                        baseOrder.currencyCode(),
                        baseOrder.listSubtotal(),
                        baseOrder.discountPercentage(),
                        baseOrder.discountAmount(),
                        baseOrder.subtotal(),
                        baseOrder.reservationExpiresAt(),
                        baseOrder.createdAt(),
                        baseOrder.version(),
                        baseOrder.items(),
                        baseOrder.history());

        service.orderConfirmed(
                withoutEmail,
                NOW);

        verify(outbox, never())
                .enqueue(
                        any(),
                        any(),
                        any(),
                        any(),
                        any());
    }

    private AdminOrderDetail order() {
        return new AdminOrderDetail(
                ORDER_ID,
                11L,
                OrderStatus.CONFIRMED,
                FulfillmentType.PICKUP,
                OrderPaymentMethod.MERCADO_PAGO,
                "Ana Pérez",
                "1155551234",
                "ana@example.com",
                null,
                "ARS",
                new BigDecimal("19999.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("19999.00"),
                NOW.plusSeconds(3600),
                NOW.minusSeconds(300),
                2L,
                List.of(
                        new GuestOrderItem(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "Remera",
                                "M",
                                "Azul",
                                List.of(),
                                "UNIT",
                                new BigDecimal("19999.00"),
                                new BigDecimal("1.000"),
                                new BigDecimal("19999.00"))),
                List.of());
    }

    private BankTransferPayment payment() {
        return payment(
                PAYMENT_ID,
                20L);
    }

    private BankTransferPayment payment(
            UUID paymentId,
            long internalId) {

        return new BankTransferPayment(
                internalId,
                paymentId,
                10L,
                ORDER_ID,
                11L,
                "Ana Pérez",
                new BigDecimal("19999.00"),
                "ARS",
                NOW.plusSeconds(3600),
                1,
                BankTransferStatus.REJECTED,
                "private-object-key",
                "receipt.pdf",
                "application/pdf",
                100L,
                NOW.minusSeconds(60),
                NOW,
                7L,
                "No se distingue el importe",
                NOW,
                NOW,
                1L);
    }

    private StoreSettings store() {
        return store("Tienda A");
    }

    private StoreSettings store(
            String name) {

        return new StoreSettings(
                name,
                "ARS",
                "America/Argentina/Buenos_Aires",
                "1155550000",
                "store@example.com",
                "Av. Siempre Viva 123",
                "Traé tu DNI",
                true,
                BigDecimal.ZERO,
                "Banco",
                name,
                "TIENDA.A",
                null,
                null,
                null);
    }
}