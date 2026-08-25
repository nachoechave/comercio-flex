package com.comercioflex.notification.application;

public record TransactionalEmail(
	String recipient,
	String subject,
	String htmlBody,
	String textBody) {
}
