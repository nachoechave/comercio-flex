package com.comercioflex.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptLimiter {

	private static final int CLEANUP_INTERVAL = 100;

	private final LoginRateLimitProperties properties;
	private final Clock clock;
	private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();
	private final AtomicInteger operations = new AtomicInteger();

	@Autowired
	public LoginAttemptLimiter(LoginRateLimitProperties properties) {
		this(properties, Clock.systemUTC());
	}

	LoginAttemptLimiter(LoginRateLimitProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	public void checkAllowed(String remoteAddress, String normalizedEmail) {
		cleanupPeriodically();
		AttemptWindow window = attempts.get(key(remoteAddress, normalizedEmail));
		if (window != null && window.isLimited(now(), properties)) {
			throw new LoginRateLimitExceededException();
		}
	}

	public void recordFailure(String remoteAddress, String normalizedEmail) {
		String key = key(remoteAddress, normalizedEmail);
		AttemptWindow existing = attempts.get(key);
		if (existing == null && attempts.size() >= properties.maxKeys()) {
			removeExpired();
			if (attempts.size() >= properties.maxKeys()) {
				throw new LoginRateLimitExceededException();
			}
		}
		attempts.computeIfAbsent(key, ignored -> new AttemptWindow())
			.record(now(), properties);
	}

	public void reset(String remoteAddress, String normalizedEmail) {
		attempts.remove(key(remoteAddress, normalizedEmail));
	}

	private Instant now() {
		return clock.instant();
	}

	private void cleanupPeriodically() {
		if (operations.incrementAndGet() % CLEANUP_INTERVAL == 0) {
			removeExpired();
		}
	}

	private void removeExpired() {
		Instant cutoff = now().minus(properties.window());
		attempts.entrySet().removeIf(entry -> entry.getValue().isExpired(cutoff));
	}

	private String key(String remoteAddress, String normalizedEmail) {
		String rawKey = String.valueOf(remoteAddress) + '\u0000' + normalizedEmail;
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(rawKey.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 must be available", exception);
		}
	}

	private static final class AttemptWindow {

		private final ArrayDeque<Instant> failures = new ArrayDeque<>();

		synchronized void record(Instant now, LoginRateLimitProperties properties) {
			removeBefore(now.minus(properties.window()));
			failures.addLast(now);
		}

		synchronized boolean isLimited(Instant now, LoginRateLimitProperties properties) {
			removeBefore(now.minus(properties.window()));
			return failures.size() >= properties.maxAttempts();
		}

		synchronized boolean isExpired(Instant cutoff) {
			removeBefore(cutoff);
			return failures.isEmpty();
		}

		private void removeBefore(Instant cutoff) {
			while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
				failures.removeFirst();
			}
		}
	}
}
