package com.comercioflex.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import com.comercioflex.notification.application.TransactionalEmail;

class SmtpTransactionalEmailSenderTests {

	@Test
	void sendsUtf8MultipartEmailWithPlainTextAndHtmlAlternatives() throws Exception {
		JavaMailSender mailSender = mock(JavaMailSender.class);
		MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
		when(mailSender.createMimeMessage()).thenReturn(message);
		EmailProperties properties = new EmailProperties();
		properties.setFromAddress("pedidos@example.com");
		properties.setFromName("Comercio Ñandú");
		TransactionalEmail email = new TransactionalEmail(
			"ana@example.com",
			"Pedido confirmado — envío ágil",
			"<p>¡Gracias por tu compra! Total: $ 12.345,67</p>",
			"¡Gracias por tu compra! Total: $ 12.345,67");

		new SmtpTransactionalEmailSender(mailSender, properties).send(email);
		message.saveChanges();

		verify(mailSender).send(message);
		assertThat(message.isMimeType("multipart/*")).isTrue();
		assertThat(findPart(message, "multipart/alternative")).isNotNull();
		Part textPart = findPart(message, "text/plain");
		Part htmlPart = findPart(message, "text/html");
		assertThat(textPart).isNotNull();
		assertThat(htmlPart).isNotNull();
		assertThat(textPart.getContentType()).containsIgnoringCase("charset=UTF-8");
		assertThat(htmlPart.getContentType()).containsIgnoringCase("charset=UTF-8");
		assertThat(textPart.getContent()).isEqualTo(email.textBody());
		assertThat(htmlPart.getContent()).isEqualTo(email.htmlBody());
		assertThat(message.getSubject()).isEqualTo(email.subject());
		assertThat(message.getRecipients(jakarta.mail.Message.RecipientType.TO))
			.extracting(Object::toString)
			.containsExactly(email.recipient());
		InternetAddress from = (InternetAddress) message.getFrom()[0];
		assertThat(from.getAddress()).isEqualTo(properties.getFromAddress());
		assertThat(from.getPersonal()).isEqualTo(properties.getFromName());
	}

	private Part findPart(Part part, String mimeType) throws Exception {
		if (part.isMimeType(mimeType)) return part;
		Object content = part.getContent();
		if (!(content instanceof Multipart multipart)) return null;
		for (int index = 0; index < multipart.getCount(); index++) {
			Part match = findPart(multipart.getBodyPart(index), mimeType);
			if (match != null) return match;
		}
		return null;
	}
}
