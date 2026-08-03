package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class PaymentWebhookMetricsTests {

	@Test
	void recordsOnlyBoundedNonSensitiveOutcomeTags() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		PaymentWebhookMetrics metrics = new PaymentWebhookMetrics(registry);

		metrics.received(true);
		metrics.received(false);
		metrics.processed();
		metrics.failed(false, true);
		metrics.failed(true, true);
		metrics.failed(true, false);
		metrics.manuallyScheduled();

		assertThat(registry.find("comercio.flex.payment.webhooks").counters())
			.hasSize(7)
			.allSatisfy(counter -> {
				assertThat(counter.count()).isEqualTo(1);
				assertThat(counter.getId().getTags()).hasSize(1);
				assertThat(counter.getId().getTag("outcome")).isIn(Set.of(
					"received", "duplicate", "processed", "retried",
					"dead_exhausted", "dead_terminal", "manual_retry"));
			});
	}
}
