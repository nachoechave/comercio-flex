package com.comercioflex.contact.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.comercioflex.contact.api.LandingContactController.LandingContactRequest;
import com.comercioflex.notification.application.TransactionalEmail;
import com.comercioflex.notification.application.TransactionalEmailSender;
import com.comercioflex.notification.infrastructure.EmailProperties;

class LandingContactControllerTests {

	@Test
	void sendsValidContactToConfiguredRecipient() {
		TransactionalEmailSender sender = mock(TransactionalEmailSender.class);
		EmailProperties properties = new EmailProperties();
		properties.setEnabled(true);
		LandingContactController controller =
			new LandingContactController(sender, properties, "nacho9847@gmail.com");

		var response = controller.submit(validRequest(System.currentTimeMillis() - 3_000));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		ArgumentCaptor<TransactionalEmail> captor = ArgumentCaptor.forClass(TransactionalEmail.class);
		verify(sender).send(captor.capture());
		assertThat(captor.getValue().recipient()).isEqualTo("nacho9847@gmail.com");
		assertThat(captor.getValue().subject()).contains("Comercio de prueba");
		assertThat(captor.getValue().textBody()).contains("cliente@example.com", "2215555555");
	}

	@Test
	void rejectsFormsSubmittedTooFast() {
		TransactionalEmailSender sender = mock(TransactionalEmailSender.class);
		EmailProperties properties = new EmailProperties();
		properties.setEnabled(true);
		LandingContactController controller =
			new LandingContactController(sender, properties, "nacho9847@gmail.com");

		var response = controller.submit(validRequest(System.currentTimeMillis()));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		verifyNoInteractions(sender);
	}

	@Test
	void silentlyAcceptsHoneypotSubmissionsWithoutSendingEmail() {
		TransactionalEmailSender sender = mock(TransactionalEmailSender.class);
		EmailProperties properties = new EmailProperties();
		properties.setEnabled(true);
		LandingContactController controller =
			new LandingContactController(sender, properties, "nacho9847@gmail.com");
		LandingContactRequest request = new LandingContactRequest(
			"Bot",
			"Spam",
			"bot@example.com",
			"2215555555",
			"Spam",
			"https://spam.example",
			System.currentTimeMillis() - 3_000);

		var response = controller.submit(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		verifyNoInteractions(sender);
	}

	@Test
	void reportsServiceUnavailableWhenTransactionalEmailIsDisabled() {
		TransactionalEmailSender sender = mock(TransactionalEmailSender.class);
		EmailProperties properties = new EmailProperties();
		properties.setEnabled(false);
		LandingContactController controller =
			new LandingContactController(sender, properties, "nacho9847@gmail.com");

		var response = controller.submit(validRequest(System.currentTimeMillis() - 3_000));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		verify(sender, org.mockito.Mockito.never()).send(any());
	}

	private LandingContactRequest validRequest(long startedAt) {
		return new LandingContactRequest(
			"Ignacio",
			"Comercio de prueba",
			"cliente@example.com",
			"2215555555",
			"Quiero conocer Comercio Flex.",
			"",
			startedAt);
	}
}
