package com.comercioflex.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.order.domain.FulfillmentType;
import com.comercioflex.order.domain.OrderPaymentMethod;
import com.comercioflex.order.domain.OrderStatus;

class AdminOrderServiceTests {

    private static final UUID ORDER_ID =
            UUID.fromString(
                    "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    private static final UUID VARIANT_ID =
            UUID.fromString(
                    "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    private static final UUID KEY =
            UUID.fromString(
                    "11111111-1111-4111-8111-111111111111");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "22222222-2222-4222-8222-222222222222");

    private static final Instant NOW =
            Instant.parse(
                    "2026-07-30T18:00:00Z");

    private AdminOrderRepository repository;
    private OrderPaymentPolicy paymentPolicy;
    private AdminOrderService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository =
                mock(AdminOrderRepository.class);

        paymentPolicy =
                mock(OrderPaymentPolicy.class);

        TransactionTemplate transactions =
                mock(TransactionTemplate.class);

        when(transactions.execute(any()))
                .thenAnswer(
                        invocation -> {
                            TransactionCallback<Object> callback =
                                    invocation.getArgument(0);

                            return callback.doInTransaction(
                                    mock(
                                            TransactionStatus.class));
                        });

        service =
                new AdminOrderService(
                        repository,
                        transactions,
                        new OrderTransitionExecutor(
                                repository,
                                paymentPolicy,
                                Clock.fixed(
                                        NOW,
                                        ZoneOffset.UTC)));

        when(repository.findTransition(KEY))
                .thenReturn(
                        Optional.empty());

        when(repository.findDetail(ORDER_ID))
                .thenReturn(
                        Optional.of(detail()));
    }

    @Test
    void confirmsByConsumingReservationAndPhysicalStock() {
        when(repository.lockOrder(ORDER_ID))
                .thenReturn(
                        Optional.of(
                                locked(
                                        OrderStatus.PENDING_CONFIRMATION,
                                        0)));

        OrderStockLine line =
                new OrderStockLine(
                        8,
                        VARIANT_ID,
                        new BigDecimal("2.000"));

        when(repository.findStockLinesForUpdate(7))
                .thenReturn(
                        List.of(line));

        when(repository.findBalanceForUpdate(8))
                .thenReturn(
                        new BigDecimal("5.000"));

        when(repository.updateBalance(
                        8,
                        new BigDecimal("3.000")))
                .thenReturn(4L);

        when(repository.updateReservations(
                        7,
                        "ACTIVE",
                        "CONSUMED"))
                .thenReturn(1);

        service.transition(
                command(
                        OrderStatus.CONFIRMED));

        verify(repository)
                .updateReservations(
                        7,
                        "ACTIVE",
                        "CONSUMED");

        verify(repository)
                .updateOrderStatus(
                        7,
                        0,
                        OrderStatus.CONFIRMED);

        verify(repository)
                .insertInventoryMovement(
                        any(),
                        anyLong(),
                        any(),
                        any(),
                        any(),
                        anyLong(),
                        anyBoolean(),
                        any(),
                        any(),
                        any());
    }

    @Test
    void cancellationRestoresStockAndReleasesConsumedReservation() {
        when(repository.lockOrder(ORDER_ID))
                .thenReturn(
                        Optional.of(
                                locked(
                                        OrderStatus.CONFIRMED,
                                        1)));

        OrderStockLine line =
                new OrderStockLine(
                        8,
                        VARIANT_ID,
                        new BigDecimal("2.000"));

        when(repository.findStockLinesForUpdate(7))
                .thenReturn(
                        List.of(line));

        when(repository.findBalanceForUpdate(8))
                .thenReturn(
                        new BigDecimal("3.000"));

        when(repository.updateBalance(
                        8,
                        new BigDecimal("5.000")))
                .thenReturn(5L);

        when(repository.updateReservations(
                        7,
                        "CONSUMED",
                        "RELEASED"))
                .thenReturn(1);

        service.transition(
                command(
                        OrderStatus.CANCELLED));

        verify(repository)
                .updateReservations(
                        7,
                        "CONSUMED",
                        "RELEASED");

        verify(repository)
                .updateOrderStatus(
                        7,
                        1,
                        OrderStatus.CANCELLED);
    }

    @Test
    void blocksManualConfirmationWhilePaymentIsActive() {
        when(repository.lockOrder(ORDER_ID))
                .thenReturn(
                        Optional.of(
                                locked(
                                        OrderStatus.PENDING_CONFIRMATION,
                                        0)));

        when(paymentPolicy.blocksManualConfirmation(7))
                .thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.transition(
                                        command(
                                                OrderStatus.CONFIRMED)))
                .isInstanceOf(
                        InvalidOrderTransitionException.class)
                .hasMessageContaining(
                        "pago en proceso");

        verify(
                repository,
                never())
                .updateBalance(
                        anyLong(),
                        any());
    }

    @Test
    void blocksCancellationForApprovedPayment() {
        when(repository.lockOrder(ORDER_ID))
                .thenReturn(
                        Optional.of(
                                locked(
                                        OrderStatus.CONFIRMED,
                                        1)));

        when(paymentPolicy.hasAppliedPayment(7))
                .thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.transition(
                                        command(
                                                OrderStatus.CANCELLED)))
                .isInstanceOf(
                        InvalidOrderTransitionException.class)
                .hasMessageContaining(
                        "reembolso");

        verify(
                repository,
                never())
                .updateBalance(
                        anyLong(),
                        any());
    }

    @Test
    void rejectsInvalidOrExpiredTransitionsWithoutMovingStock() {
        when(repository.lockOrder(ORDER_ID))
                .thenReturn(
                        Optional.of(
                                locked(
                                        OrderStatus.COMPLETED,
                                        3)));

        assertThatThrownBy(
                        () ->
                                service.transition(
                                        command(
                                                OrderStatus.CANCELLED)))
                .isInstanceOf(
                        InvalidOrderTransitionException.class);

        verify(
                repository,
                never())
                .updateBalance(
                        anyLong(),
                        any());
    }

    @Test
    void commitsExpirationBeforeReportingTheInvalidTransition() {
        when(repository.lockOrder(ORDER_ID))
                .thenReturn(
                        Optional.of(
                                new LockedAdminOrder(
                                        7,
                                        ORDER_ID,
                                        OrderStatus.PENDING_CONFIRMATION,
                                        NOW,
                                        0)));

        assertThatThrownBy(
                        () ->
                                service.transition(
                                        command(
                                                OrderStatus.CONFIRMED)))
                .isInstanceOf(
                        InvalidOrderTransitionException.class)
                .hasMessageContaining(
                        "venció");

        verify(repository)
                .expireOrder(7);

        verify(
                repository,
                never())
                .updateBalance(
                        anyLong(),
                        any());
    }

    @Test
    void replaysOnlyTheSameIdempotentTransition() {
        when(repository.lockOrder(ORDER_ID))
                .thenReturn(
                        Optional.of(
                                locked(
                                        OrderStatus.CONFIRMED,
                                        1)));

        when(repository.findTransition(KEY))
                .thenReturn(
                        Optional.of(
                                new StoredOrderTransition(
                                        ORDER_ID,
                                        OrderStatus.CONFIRMED,
                                        "Revisado")));

        AdminOrderDetail replay =
                service.transition(
                        command(
                                OrderStatus.CONFIRMED));

        assertThat(replay.id())
                .isEqualTo(
                        ORDER_ID);

        verify(
                repository,
                never())
                .updateOrderStatus(
                        anyLong(),
                        anyLong(),
                        any());

        assertThatThrownBy(
                        () ->
                                service.transition(
                                        command(
                                                OrderStatus.CANCELLED)))
                .isInstanceOf(
                        OrderTransitionIdempotencyConflictException.class);
    }

    private OrderTransitionCommand command(
            OrderStatus target) {

        return new OrderTransitionCommand(
                ORDER_ID,
                KEY,
                target,
                "Revisado",
                ACTOR_ID,
                "Operador");
    }

    private LockedAdminOrder locked(
            OrderStatus status,
            long version) {

        return new LockedAdminOrder(
                7,
                ORDER_ID,
                status,
                NOW.plusSeconds(1800),
                version);
    }

    private AdminOrderDetail detail() {
        return new AdminOrderDetail(
                ORDER_ID,
                7L,
                OrderStatus.CONFIRMED,
                FulfillmentType.PICKUP,
                OrderPaymentMethod.MERCADO_PAGO,
                "Cliente",
                "1100000000",
                null,
                null,
                "ARS",
                new BigDecimal("5000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("5000.00"),
                NOW.plusSeconds(1800),
                NOW,
                1L,
                List.of(),
                List.of());
    }
}