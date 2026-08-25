package com.comercioflex.notification.application;

public interface TransactionalEmailSender {
	void send(TransactionalEmail email);
}
