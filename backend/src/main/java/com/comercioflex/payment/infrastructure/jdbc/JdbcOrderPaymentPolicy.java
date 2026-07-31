package com.comercioflex.payment.infrastructure.jdbc;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.comercioflex.order.application.OrderPaymentPolicy;

@Component
public class JdbcOrderPaymentPolicy implements OrderPaymentPolicy {

	private final JdbcTemplate jdbcTemplate;

	public JdbcOrderPaymentPolicy(
			@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public boolean blocksManualConfirmation(long orderInternalId) {
		return count("""
			SELECT COUNT(*) FROM payment_intents
			WHERE order_id = ?
				AND status IN ('CREATED', 'PENDING', 'REQUIRES_REVIEW')
			""", orderInternalId) > 0;
	}

	@Override
	public boolean hasAppliedPayment(long orderInternalId) {
		return count("""
			SELECT COUNT(*)
			FROM payment_transactions transaction_record
			JOIN payment_intents intent
				ON intent.id = transaction_record.payment_intent_id
			WHERE intent.order_id = ?
				AND transaction_record.applied_at IS NOT NULL
			""", orderInternalId) > 0;
	}

	private int count(String sql, long orderInternalId) {
		Integer value = jdbcTemplate.queryForObject(sql, Integer.class, orderInternalId);
		return value == null ? 0 : value;
	}
}
