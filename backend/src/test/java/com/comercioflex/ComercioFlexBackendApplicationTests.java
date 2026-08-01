package com.comercioflex;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.payment.application.CheckoutControlRepository;
import com.comercioflex.payment.application.CheckoutPaymentException;
import com.comercioflex.payment.domain.PaymentEnvironment;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ComercioFlexBackendApplicationTests {

	@Autowired
	private CheckoutControlRepository checkoutControlRepository;

	@Autowired
	@Qualifier("controlTransactionTemplate")
	private TransactionTemplate controlTransactions;

	@Test
	void contextLoads() {
	}

	@Test
	void controlRepositoriesWorkWithoutTenantContext() {
		assertThatThrownBy(() -> controlTransactions.executeWithoutResult(status ->
			checkoutControlRepository.requireCommerciallyEnabled(
				Long.MAX_VALUE, PaymentEnvironment.TEST)))
			.isInstanceOf(CheckoutPaymentException.class);
	}

}
