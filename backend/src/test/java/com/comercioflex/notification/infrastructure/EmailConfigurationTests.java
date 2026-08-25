package com.comercioflex.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import com.comercioflex.notification.application.TransactionalEmail;
import com.comercioflex.notification.application.TransactionalEmailSender;

class EmailConfigurationTests {

	@Test
	void disabledEmailUsesNoOpSenderWithoutSmtpConfigurationOrConnection() {
		EmailProperties properties = new EmailProperties();
		TransactionalEmailSender sender = new EmailConfiguration().transactionalEmailSender(properties);

		assertThatCode(() -> sender.send(new TransactionalEmail(
			"ana@example.com", "Asunto", "<p>Hola</p>", "Hola")))
			.doesNotThrowAnyException();
	}

	@Test
	void smtpSenderHasStrictConfiguredTimeouts() {
		EmailProperties properties = new EmailProperties();
		properties.setEnabled(true);
		properties.setHost("smtp.example.test");
		properties.setSmtpConnectTimeoutMs(1_111);
		properties.setSmtpReadTimeoutMs(2_222);
		properties.setSmtpWriteTimeoutMs(3_333);

		TransactionalEmailSender sender =
			new EmailConfiguration().transactionalEmailSender(properties);
		JavaMailSenderImpl mailSender = (JavaMailSenderImpl)
			ReflectionTestUtils.getField(sender, "mailSender");

		assertThat(mailSender).isNotNull();
		assertThat(mailSender.getJavaMailProperties())
			.containsEntry("mail.smtp.connectiontimeout", "1111")
			.containsEntry("mail.smtp.timeout", "2222")
			.containsEntry("mail.smtp.writetimeout", "3333");
	}
}
