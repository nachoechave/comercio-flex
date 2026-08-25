package com.comercioflex.notification.infrastructure;

import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import com.comercioflex.notification.application.TransactionalEmail;
import com.comercioflex.notification.application.TransactionalEmailSender;

final class SmtpTransactionalEmailSender implements TransactionalEmailSender {
	private final JavaMailSender mailSender;
	private final EmailProperties properties;

	SmtpTransactionalEmailSender(JavaMailSender mailSender, EmailProperties properties) {
		this.mailSender = mailSender;
		this.properties = properties;
	}

	@Override
	public void send(TransactionalEmail email) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(
				message, false, StandardCharsets.UTF_8.name());
			helper.setFrom(new InternetAddress(
				properties.getFromAddress(), properties.getFromName(), StandardCharsets.UTF_8.name()));
			helper.setTo(email.recipient());
			helper.setSubject(email.subject());
			helper.setText(email.textBody(), email.htmlBody());
			mailSender.send(message);
		}
		catch (MessagingException | java.io.UnsupportedEncodingException exception) {
			throw new IllegalStateException("No se pudo construir el email transaccional.", exception);
		}
	}
}
