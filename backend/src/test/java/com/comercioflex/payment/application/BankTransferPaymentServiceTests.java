package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.notification.application.CustomerNotificationPublisher;
import com.comercioflex.order.application.OrderTransitionExecution;
import com.comercioflex.order.application.PaidOrderConfirmer;
import com.comercioflex.order.domain.OrderPaymentMethod;
import com.comercioflex.order.domain.OrderStatus;
import com.comercioflex.payment.domain.BankTransferStatus;

class BankTransferPaymentServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-24T15:00:00Z");
	private static final UUID ORDER_ID =
		UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final UUID PAYMENT_ID =
		UUID.fromString("22222222-2222-4222-8222-222222222222");
	private static final String TOKEN = "A".repeat(43);

	private final BankTransferRepository repository =
		mock(BankTransferRepository.class);

	private final PaymentReceiptStorage storage =
		mock(PaymentReceiptStorage.class);

	private final PaidOrderConfirmer confirmer =
		mock(PaidOrderConfirmer.class);

	private final CustomerNotificationPublisher notifications =
		mock(CustomerNotificationPublisher.class);

	private final TransactionTemplate transactions =
		mock(TransactionTemplate.class);

	private BankTransferPaymentService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		when(transactions.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<Object> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		});

		service = new BankTransferPaymentService(
			repository,
			storage,
			confirmer,
			notifications,
			transactions,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void rejectsInitiationWhenBankTransferIsDisabled() {
		when(repository.findConfiguration()).thenReturn(disabled());

		assertThatThrownBy(
			() -> service.initiate("tienda-a", ORDER_ID, TOKEN)
		)
			.isInstanceOf(BankTransferPaymentException.class)
			.hasMessageContaining("no está habilitada");
	}

	@Test
	void existingTransferCanBeReadWhenNewTransfersAreDisabled() {
		BankTransferPayment awaiting =
			payment(BankTransferStatus.AWAITING_RECEIPT, 1);

		when(repository.findConfiguration()).thenReturn(disabled());

		when(repository.findByIdAndOrderToken(
			eq(PAYMENT_ID),
			eq(ORDER_ID),
			any(),
			eq(false)
		)).thenReturn(Optional.of(awaiting));

		when(repository.lockOrder(eq(ORDER_ID), any()))
			.thenReturn(Optional.of(
				new BankTransferOrder(
					10L,
					ORDER_ID,
					10L,
					OrderStatus.PENDING_CONFIRMATION,
					OrderPaymentMethod.BANK_TRANSFER,
					"Cliente",
					new BigDecimal("12500.00"),
					"ARS",
					NOW.plusSeconds(1800)
				)
			));

		when(repository.findCurrentForOrder(10L))
			.thenReturn(Optional.of(awaiting));

		assertThat(
			service.find(ORDER_ID, TOKEN, PAYMENT_ID).payment()
		).isSameAs(awaiting);

		assertThat(
			service.findCurrent(ORDER_ID, TOKEN).payment()
		).isSameAs(awaiting);
	}

	@Test
	void existingTransferAcceptsReceiptWhenNewTransfersAreDisabled() {
		BankTransferPayment awaiting =
			payment(BankTransferStatus.AWAITING_RECEIPT, 1);

		BankTransferPayment reviewing =
			payment(BankTransferStatus.UNDER_REVIEW, 1);

		byte[] pdf =
			"%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII);

		when(repository.findConfiguration()).thenReturn(disabled());

		when(repository.findByIdAndOrderToken(
			eq(PAYMENT_ID),
			eq(ORDER_ID),
			any(),
			eq(true)
		)).thenReturn(Optional.of(awaiting));

		when(repository.findById(PAYMENT_ID, false))
			.thenReturn(Optional.of(reviewing));

		BankTransferInstructions result = service.upload(
			"tienda-a",
			ORDER_ID,
			TOKEN,
			PAYMENT_ID,
			"receipt.pdf",
			"application/pdf",
			pdf
		);

		assertThat(result.payment().status())
			.isEqualTo(BankTransferStatus.UNDER_REVIEW);

		verify(storage).store(
			any(),
			eq(pdf),
			eq("application/pdf")
		);

		verify(repository).attachReceipt(
			eq(awaiting),
			any(),
			eq("receipt.pdf"),
			eq("application/pdf"),
			eq((long) pdf.length),
			eq(NOW)
		);
	}

	@Test
	void adminDownloadsTheReceiptThroughPrivateStorage() {
		BankTransferPayment reviewing =
			payment(BankTransferStatus.UNDER_REVIEW, 1);

		byte[] bytes =
			"%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII);

		PaymentReceiptObject stored =
			new PaymentReceiptObject(bytes, "application/pdf");

		when(repository.findById(PAYMENT_ID, false))
			.thenReturn(Optional.of(reviewing));

		when(storage.load("object-key", "application/pdf"))
			.thenReturn(stored);

		DownloadedPaymentReceipt downloaded =
			service.download(PAYMENT_ID);

		assertThat(downloaded.object().bytes())
			.isEqualTo(bytes);

		assertThat(downloaded.object().contentType())
			.isEqualTo("application/pdf");

		assertThat(downloaded.originalFilename())
			.isEqualTo("receipt.pdf");

		verify(storage).load(
			"object-key",
			"application/pdf"
		);
	}

	@Test
	void initiatesAndExtendsTheReservationForTwentyFourHours() {
		stubInitiation(null, 1);

		BankTransferInstructions result =
			service.initiate("tienda-a", ORDER_ID, TOKEN);

		assertThat(result.payment().id())
			.isEqualTo(PAYMENT_ID);

		assertThat(result.payment().status())
			.isEqualTo(BankTransferStatus.AWAITING_RECEIPT);

		verify(repository).extendReservation(
			10L,
			NOW.plusSeconds(24 * 60 * 60)
		);

		verify(repository).insert(
			any(UUID.class),
			eq(10L),
			eq(1)
		);
	}

	@ParameterizedTest
	@MethodSource("validReceipts")
	void acceptsValidJpegPngAndPdf(
		String contentType,
		byte[] bytes
	) {
		BankTransferPayment awaiting =
			payment(BankTransferStatus.AWAITING_RECEIPT, 1);

		BankTransferPayment reviewing =
			payment(BankTransferStatus.UNDER_REVIEW, 1);

		when(repository.findConfiguration())
			.thenReturn(enabled());

		when(repository.findByIdAndOrderToken(
			eq(PAYMENT_ID),
			eq(ORDER_ID),
			any(),
			eq(true)
		)).thenReturn(Optional.of(awaiting));

		when(repository.findById(PAYMENT_ID, false))
			.thenReturn(Optional.of(reviewing));

		BankTransferInstructions result = service.upload(
			"tienda-a",
			ORDER_ID,
			TOKEN,
			PAYMENT_ID,
			"receipt",
			contentType,
			bytes
		);

		assertThat(result.payment().status())
			.isEqualTo(BankTransferStatus.UNDER_REVIEW);

		verify(storage).store(
			any(),
			eq(bytes),
			eq(contentType)
		);

		verify(repository).attachReceipt(
			eq(awaiting),
			any(),
			any(),
			eq(contentType),
			eq((long) bytes.length),
			eq(NOW)
		);

		verify(confirmer, never())
			.confirmWithinCurrentTransaction(any(), any());
	}

	@Test
	void rejectsAFileWhoseDeclaredTypeDoesNotMatchItsSignature() {
		assertThatThrownBy(
			() -> service.upload(
				"tienda-a",
				ORDER_ID,
				TOKEN,
				PAYMENT_ID,
				"receipt.jpg",
				"image/jpeg",
				"not-a-jpeg".getBytes(StandardCharsets.UTF_8)
			)
		)
			.isInstanceOf(BankTransferPaymentException.class)
			.hasMessageContaining("JPEG, PNG o PDF");

		verify(storage, never())
			.store(any(), any(), any());
	}

	@Test
	void rejectsAReceiptLargerThanFiveMegabytes() {
		byte[] oversized =
			new byte[(int) BankTransferPaymentService.MAX_RECEIPT_SIZE + 1];

		assertThatThrownBy(
			() -> service.upload(
				"tienda-a",
				ORDER_ID,
				TOKEN,
				PAYMENT_ID,
				"large.pdf",
				"application/pdf",
				oversized
			)
		)
			.isInstanceOf(BankTransferPaymentException.class)
			.hasMessageContaining("5 MB");

		verify(storage, never())
			.store(any(), any(), any());
	}

	@Test
	void anotherTenantCannotReadAReceiptAbsentFromItsRoutedDatabase() {
		when(repository.findConfiguration())
			.thenReturn(enabled());

		when(repository.findByIdAndOrderToken(
			eq(PAYMENT_ID),
			eq(ORDER_ID),
			any(),
			eq(false)
		)).thenReturn(Optional.empty());

		assertThatThrownBy(
			() -> service.find(ORDER_ID, TOKEN, PAYMENT_ID)
		)
			.isInstanceOf(BankTransferPaymentException.class)
			.hasMessageContaining("no existe");
	}

	@Test
	void approvalConfirmsTheOrderAndMarksTheReceiptApproved() {
		BankTransferPayment reviewing =
			payment(BankTransferStatus.UNDER_REVIEW, 1);

		BankTransferPayment approved =
			payment(BankTransferStatus.APPROVED, 1);

		when(repository.findById(PAYMENT_ID, true))
			.thenReturn(Optional.of(reviewing));

		when(repository.findById(PAYMENT_ID, false))
			.thenReturn(Optional.of(approved));

		when(confirmer.confirmWithinCurrentTransaction(
			eq(ORDER_ID),
			any(),
			eq("Transferencia bancaria")
		)).thenReturn(
			OrderTransitionExecution.completed(null)
		);

		assertThat(
			service.approve(PAYMENT_ID, 7L).status()
		).isEqualTo(BankTransferStatus.APPROVED);

		verify(confirmer).confirmWithinCurrentTransaction(
			eq(ORDER_ID),
			any(),
			eq("Transferencia bancaria")
		);

		verify(repository).approve(
			reviewing,
			7L,
			NOW
		);
	}

	@Test
	void repeatedApprovalIsIdempotentAndDoesNotConsumeStockAgain() {
		BankTransferPayment approved =
			payment(BankTransferStatus.APPROVED, 1);

		when(repository.findById(PAYMENT_ID, true))
			.thenReturn(Optional.of(approved));

		assertThat(
			service.approve(PAYMENT_ID, 7L)
		).isSameAs(approved);

		verify(confirmer, never())
			.confirmWithinCurrentTransaction(
				any(),
				any(),
				any()
			);

		verify(repository, never())
			.approve(
				any(),
				anyLong(),
				any()
			);
	}

	@Test
	void rejectionDoesNotConfirmOrConsumeStock() {
		BankTransferPayment reviewing =
			payment(BankTransferStatus.UNDER_REVIEW, 1);

		BankTransferPayment rejected =
			payment(BankTransferStatus.REJECTED, 1);

		when(repository.findById(PAYMENT_ID, true))
			.thenReturn(Optional.of(reviewing));

		when(repository.findById(PAYMENT_ID, false))
			.thenReturn(Optional.of(rejected));

		assertThat(
			service.reject(
				PAYMENT_ID,
				7L,
				"No se distingue el importe"
			).status()
		).isEqualTo(BankTransferStatus.REJECTED);

		verify(repository).reject(
			reviewing,
			7L,
			"No se distingue el importe",
			NOW
		);

		verify(notifications)
			.bankTransferReceiptRejected(
				rejected,
				NOW
			);

		verify(confirmer, never())
			.confirmWithinCurrentTransaction(
				any(),
				any(),
				any()
			);
	}

	@Test
	void aRejectedReceiptAllowsANewAttempt() {
		stubInitiation(
			payment(BankTransferStatus.REJECTED, 1),
			2
		);

		BankTransferInstructions result =
			service.initiate(
				"tienda-a",
				ORDER_ID,
				TOKEN
			);

		assertThat(result.payment().attemptNumber())
			.isEqualTo(2);

		verify(repository).insert(
			any(UUID.class),
			eq(10L),
			eq(2)
		);
	}

	@Test
	void anExpiredReservationCannotBeApproved() {
		BankTransferPayment reviewing =
			payment(BankTransferStatus.UNDER_REVIEW, 1);

		when(repository.findById(PAYMENT_ID, true))
			.thenReturn(Optional.of(reviewing));

		when(confirmer.confirmWithinCurrentTransaction(
			eq(ORDER_ID),
			any(),
			eq("Transferencia bancaria")
		)).thenReturn(
			OrderTransitionExecution.expiration()
		);

		assertThatThrownBy(
			() -> service.approve(PAYMENT_ID, 7L)
		)
			.isInstanceOf(BankTransferPaymentException.class)
			.hasMessageContaining("reserva venció");

		verify(repository, never())
			.approve(
				any(),
				anyLong(),
				any()
			);
	}

	private void stubInitiation(
		BankTransferPayment current,
		int nextAttempt
	) {
		when(repository.findConfiguration())
			.thenReturn(enabled());

		when(repository.lockOrder(eq(ORDER_ID), any()))
			.thenReturn(Optional.of(
				new BankTransferOrder(
					10L,
					ORDER_ID,
					10L,
					OrderStatus.PENDING_CONFIRMATION,
					OrderPaymentMethod.BANK_TRANSFER,
					"Cliente",
					new BigDecimal("12500.00"),
					"ARS",
					NOW.plusSeconds(1800)
				)
			));

		when(repository.findCurrentForOrder(10L))
			.thenReturn(Optional.ofNullable(current));

		when(repository.hasBlockingCheckout(10L))
			.thenReturn(false);

		when(repository.nextAttemptNumber(10L))
			.thenReturn(nextAttempt);

		when(repository.findById(any(UUID.class), eq(false)))
			.thenReturn(Optional.of(
				payment(
					BankTransferStatus.AWAITING_RECEIPT,
					nextAttempt
				)
			));
	}

	private BankTransferConfiguration enabled() {
		return new BankTransferConfiguration(
			true,
			BigDecimal.ZERO,
			"Banco",
			"Titular",
			"ALIAS.TEST",
			null
		);
	}

	private BankTransferConfiguration disabled() {
		return new BankTransferConfiguration(
			false,
			BigDecimal.ZERO,
			null,
			null,
			null,
			null
		);
	}

	private BankTransferPayment payment(
		BankTransferStatus status,
		int attempt
	) {
		boolean receipt =
			status != BankTransferStatus.AWAITING_RECEIPT;

		return new BankTransferPayment(
			20L,
			PAYMENT_ID,
			10L,
			ORDER_ID,
			10L,
			"Cliente",
			new BigDecimal("12500.00"),
			"ARS",
			NOW.plusSeconds(24 * 60 * 60),
			attempt,
			status,
			receipt ? "object-key" : null,
			receipt ? "receipt.pdf" : null,
			receipt ? "application/pdf" : null,
			receipt ? 100L : null,
			receipt ? NOW : null,
			status == BankTransferStatus.APPROVED
					|| status == BankTransferStatus.REJECTED
				? NOW
				: null,
			status == BankTransferStatus.APPROVED
					|| status == BankTransferStatus.REJECTED
				? 7L
				: null,
			status == BankTransferStatus.REJECTED
				? "No válido"
				: null,
			NOW,
			NOW,
			0L
		);
	}

	static List<Object[]> validReceipts() {
		return List.of(
			new Object[] {
				"image/jpeg",
				new byte[] {
					(byte) 0xff,
					(byte) 0xd8,
					(byte) 0xff,
					0x00,
					(byte) 0xff,
					(byte) 0xd9
				}
			},
			new Object[] {
				"image/png",
				new byte[] {
					(byte) 0x89,
					0x50,
					0x4e,
					0x47,
					0x0d,
					0x0a,
					0x1a,
					0x0a
				}
			},
			new Object[] {
				"application/pdf",
				"%PDF-1.4\n%%EOF"
					.getBytes(StandardCharsets.US_ASCII)
			}
		);
	}
}