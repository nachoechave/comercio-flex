package com.comercioflex.order.application;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.order.domain.OrderStatus;

@Service
public class AdminOrderService {

	private final AdminOrderRepository repository;
	private final TransactionTemplate transactionTemplate;
	private final OrderTransitionExecutor executor;

	@Autowired
	public AdminOrderService(
			AdminOrderRepository repository,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate transactionTemplate) {
		this(repository, transactionTemplate, new OrderTransitionExecutor(repository));
	}

	AdminOrderService(
			AdminOrderRepository repository,
			TransactionTemplate transactionTemplate,
			Clock clock) {
		this(repository, transactionTemplate, new OrderTransitionExecutor(repository, clock));
	}

	AdminOrderService(
			AdminOrderRepository repository,
			TransactionTemplate transactionTemplate,
			OrderTransitionExecutor executor) {
		this.repository = repository;
		this.transactionTemplate = transactionTemplate;
		this.executor = executor;
	}

	public AdminOrderPage findPage(AdminOrderSearch rawSearch) {
		AdminOrderSearch search = validate(rawSearch);
		return transactionTemplate.execute(ignored -> {
			repository.expirePendingOrders();
			return repository.findPage(search);
		});
	}

	public AdminOrderDetail find(UUID orderId) {
		return transactionTemplate.execute(ignored -> {
			repository.expirePendingOrders();
			return repository.findDetail(orderId)
				.orElseThrow(AdminOrderNotFoundException::new);
		});
	}

	public AdminOrderDetail transition(OrderTransitionCommand rawCommand) {
		OrderTransitionCommand command = validate(rawCommand);
		OrderTransitionExecution outcome;
		try {
			outcome = transactionTemplate.execute(ignored -> executor.execute(command));
		}
		catch (DuplicateKeyException exception) {
			return transactionTemplate.execute(ignored -> replayAfterDuplicate(command, exception));
		}
		if (outcome == null || outcome.expired()) {
			throw new InvalidOrderTransitionException(
				"La reserva del pedido ya venció.");
		}
		return outcome.detail();
	}

	private AdminOrderDetail replayAfterDuplicate(
			OrderTransitionCommand command,
			DuplicateKeyException original) {
		StoredOrderTransition stored = repository.findTransition(command.idempotencyKey())
			.orElseThrow(() -> original);
		requireSameTransition(stored, command);
		return repository.findDetail(command.orderId())
			.orElseThrow(AdminOrderNotFoundException::new);
	}

	private AdminOrderSearch validate(AdminOrderSearch search) {
		if (search == null || search.page() < 0 || search.size() < 1 || search.size() > 100) {
			throw new InvalidOrderTransitionException("Paginación inválida.");
		}
		String query = normalize(search.query(), 30);
		return new AdminOrderSearch(search.page(), search.size(), query, search.status());
	}

	private OrderTransitionCommand validate(OrderTransitionCommand command) {
		if (command == null
				|| command.orderId() == null
				|| command.idempotencyKey() == null
				|| command.targetStatus() == null
				|| command.actorId() == null
				|| command.actorDisplayName() == null) {
			throw new InvalidOrderTransitionException("La transición está incompleta.");
		}
		return new OrderTransitionCommand(
			command.orderId(),
			command.idempotencyKey(),
			command.targetStatus(),
			normalize(command.note(), 500),
			command.actorId(),
			command.actorDisplayName());
	}

	static void requireSameTransition(
			StoredOrderTransition stored,
			OrderTransitionCommand requested) {
		if (!stored.orderId().equals(requested.orderId())
				|| stored.targetStatus() != requested.targetStatus()
				|| !Objects.equals(stored.note(), requested.note())) {
			throw new OrderTransitionIdempotencyConflictException();
		}
	}

	private String normalize(String value, int maxLength) {
		if (value == null || value.isBlank()) return null;
		String normalized = value.trim().replaceAll("\\s+", " ");
		if (normalized.length() > maxLength
				|| normalized.chars().anyMatch(Character::isISOControl)) {
			throw new InvalidOrderTransitionException("El texto ingresado no es válido.");
		}
		return normalized;
	}
}
