package com.comercioflex.notification.infrastructure;

import java.util.Properties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.comercioflex.notification.application.TransactionalEmailSender;

@Configuration
@EnableConfigurationProperties(EmailProperties.class)
class EmailConfiguration {

	@Bean
	TransactionalEmailSender transactionalEmailSender(EmailProperties properties) {
		if (!properties.isEnabled()) return email -> { };
		if (!"smtp".equalsIgnoreCase(properties.getProvider())) {
			throw new IllegalStateException("EMAIL_PROVIDER debe ser smtp.");
		}
		if (properties.getHost() == null || properties.getHost().isBlank()) {
			throw new IllegalStateException("SMTP_HOST es obligatorio cuando EMAIL_ENABLED=true.");
		}
		JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
		mailSender.setHost(properties.getHost());
		mailSender.setPort(properties.getPort());
		mailSender.setUsername(properties.getUsername());
		mailSender.setPassword(properties.getPassword());
		Properties javaMail = mailSender.getJavaMailProperties();
		javaMail.setProperty("mail.smtp.auth",
			Boolean.toString(properties.getUsername() != null && !properties.getUsername().isBlank()));
		javaMail.setProperty("mail.smtp.starttls.enable", Boolean.toString(properties.isStarttls()));
		return new SmtpTransactionalEmailSender(mailSender, properties);
	}
}
