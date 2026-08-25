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
	private boolean starttls = true;

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
	public boolean isStarttls() { return starttls; }
	public void setStarttls(boolean starttls) { this.starttls = starttls; }
}
