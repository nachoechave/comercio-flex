package com.comercioflex.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.email")
public class EmailProperties {
	private boolean enabled;
	private String provider = "smtp";
	private String fromName = "Comercio Flex";
	private String fromAddress = "no-reply@example.com";
	private String host = "";
	private int port = 587;
	private String username = "";
	private String password = "";
	private String publicBaseUri = "";
	private boolean starttls = true;
	private int smtpConnectTimeoutMs = 5_000;
	private int smtpReadTimeoutMs = 10_000;
	private int smtpWriteTimeoutMs = 10_000;
	private boolean outboxWorkerEnabled = true;
	private long outboxPollIntervalMs = 30_000;
	private int outboxBatchSize = 25;
	private int outboxMaxAttempts = 5;
	private long outboxInitialBackoffSeconds = 60;
	private long outboxMaxBackoffSeconds = 3_600;
	private long outboxSendingTimeoutSeconds = 300;

	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public String getProvider() { return provider; }
	public void setProvider(String provider) { this.provider = provider; }
	public String getFromName() { return fromName; }
	public void setFromName(String fromName) { this.fromName = fromName; }
	public String getFromAddress() { return fromAddress; }
	public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }
	public String getHost() { return host; }
	public void setHost(String host) { this.host = host; }
	public int getPort() { return port; }
	public void setPort(int port) { this.port = port; }
	public String getUsername() { return username; }
	public void setUsername(String username) { this.username = username; }
	public String getPassword() { return password; }
	public void setPassword(String password) { this.password = password; }
	public String getPublicBaseUri() { return publicBaseUri; }
	public void setPublicBaseUri(String publicBaseUri) { this.publicBaseUri = publicBaseUri; }
	public boolean isStarttls() { return starttls; }
	public void setStarttls(boolean starttls) { this.starttls = starttls; }
	public int getSmtpConnectTimeoutMs() { return smtpConnectTimeoutMs; }
	public void setSmtpConnectTimeoutMs(int value) { this.smtpConnectTimeoutMs = value; }
	public int getSmtpReadTimeoutMs() { return smtpReadTimeoutMs; }
	public void setSmtpReadTimeoutMs(int value) { this.smtpReadTimeoutMs = value; }
	public int getSmtpWriteTimeoutMs() { return smtpWriteTimeoutMs; }
	public void setSmtpWriteTimeoutMs(int value) { this.smtpWriteTimeoutMs = value; }
	public boolean isOutboxWorkerEnabled() { return outboxWorkerEnabled; }
	public void setOutboxWorkerEnabled(boolean value) { this.outboxWorkerEnabled = value; }
	public long getOutboxPollIntervalMs() { return outboxPollIntervalMs; }
	public void setOutboxPollIntervalMs(long value) { this.outboxPollIntervalMs = value; }
	public int getOutboxBatchSize() { return outboxBatchSize; }
	public void setOutboxBatchSize(int value) { this.outboxBatchSize = value; }
	public int getOutboxMaxAttempts() { return outboxMaxAttempts; }
	public void setOutboxMaxAttempts(int value) { this.outboxMaxAttempts = value; }
	public long getOutboxInitialBackoffSeconds() { return outboxInitialBackoffSeconds; }
	public void setOutboxInitialBackoffSeconds(long value) { this.outboxInitialBackoffSeconds = value; }
	public long getOutboxMaxBackoffSeconds() { return outboxMaxBackoffSeconds; }
	public void setOutboxMaxBackoffSeconds(long value) { this.outboxMaxBackoffSeconds = value; }
	public long getOutboxSendingTimeoutSeconds() { return outboxSendingTimeoutSeconds; }
	public void setOutboxSendingTimeoutSeconds(long value) { this.outboxSendingTimeoutSeconds = value; }
}
