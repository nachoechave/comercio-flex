package com.comercioflex.payment.application;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class PaymentWebhookMetrics {

	private final Counter received;
	private final Counter duplicate;
	private final Counter processed;
	private final Counter retried;
	private final Counter deadExhausted;
	private final Counter deadTerminal;
	private final Counter manuallyScheduled;

	public PaymentWebhookMetrics(MeterRegistry registry) {
		received = counter(registry, "received", "Notificaciones nuevas aceptadas");
		duplicate = counter(registry, "duplicate", "Notificaciones duplicadas ignoradas");
		processed = counter(registry, "processed", "Notificaciones procesadas");
		retried = counter(registry, "retried", "Reintentos automaticos programados");
		deadExhausted = counter(registry, "dead_exhausted", "Notificaciones que agotaron sus reintentos");
		deadTerminal = counter(registry, "dead_terminal", "Notificaciones con un fallo no recuperable");
		manuallyScheduled = counter(registry, "manual_retry", "Reintentos manuales programados");
	}

	private Counter counter(MeterRegistry registry, String outcome, String description) {
		return Counter.builder("comercio.flex.payment.webhooks")
			.description(description)
			.tag("outcome", outcome)
			.register(registry);
	}

	public void received(boolean inserted) {
		(inserted ? received : duplicate).increment();
	}

	public void processed() {
		processed.increment();
	}

	public void failed(boolean isDead, boolean retryable) {
		if (!isDead) {
			retried.increment();
		}
		else {
			(retryable ? deadExhausted : deadTerminal).increment();
		}
	}

	public void manuallyScheduled() {
		manuallyScheduled.increment();
	}
}
