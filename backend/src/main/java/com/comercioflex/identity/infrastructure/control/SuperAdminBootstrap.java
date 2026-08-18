package com.comercioflex.identity.infrastructure.control;

import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.identity.application.EmailNormalizer;

@Component
@ConditionalOnProperty(name = "app.super-admin-bootstrap.enabled", havingValue = "true")
public class SuperAdminBootstrap implements CommandLineRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(SuperAdminBootstrap.class);
	private static final String LOCK_NAME = "comercio-flex:super-admin-bootstrap";
	private static final int LOCK_TIMEOUT_SECONDS = 15;
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate transactionTemplate;
	private final PasswordEncoder passwordEncoder;
	private final EmailNormalizer emailNormalizer;
	private final String email;
	private final String password;
	private final String displayName;

	public SuperAdminBootstrap(
			@Qualifier("controlJdbcTemplate") JdbcTemplate jdbcTemplate,
			@Qualifier("controlTransactionTemplate") TransactionTemplate transactionTemplate,
			PasswordEncoder passwordEncoder,
			EmailNormalizer emailNormalizer,
			@Value("${app.super-admin-bootstrap.email:}") String email,
			@Value("${app.super-admin-bootstrap.password:}") String password,
			@Value("${app.super-admin-bootstrap.display-name:}") String displayName) {
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = transactionTemplate;
		this.passwordEncoder = passwordEncoder;
		this.emailNormalizer = emailNormalizer;
		this.email = email;
		this.password = password;
		this.displayName = displayName;
	}

	@Override
	public void run(String... args) {
		validateConfiguration();
		Boolean created = transactionTemplate.execute(status -> provisionWithLock());
		if (Boolean.TRUE.equals(created)) {
			LOGGER.warn(
				"Initial SUPER_ADMIN account created. Disable the bootstrap and remove its password secret before the next deploy.");
		}
	}

	private boolean provisionWithLock() {
		Integer acquired = jdbcTemplate.queryForObject(
			"SELECT GET_LOCK(?, ?)",
			Integer.class,
			LOCK_NAME,
			LOCK_TIMEOUT_SECONDS);
		if (!Integer.valueOf(1).equals(acquired)) {
			throw new IllegalStateException("Could not acquire the SUPER_ADMIN bootstrap lock");
		}

		try {
			return provision();
		}
		finally {
			releaseLock();
		}
	}

	private boolean provision() {
		String normalizedEmail = emailNormalizer.normalize(email);
		var existingRoles = jdbcTemplate.queryForList(
			"SELECT platform_role FROM platform_users WHERE email_normalized = ?",
			String.class,
			normalizedEmail);
		if (!existingRoles.isEmpty()) {
			if (!"SUPER_ADMIN".equals(existingRoles.getFirst())) {
				throw new IllegalStateException(
					"SUPER_ADMIN_BOOTSTRAP_EMAIL already belongs to a non-SuperAdmin user");
			}
			return false;
		}

		Integer superAdminCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM platform_users WHERE platform_role = 'SUPER_ADMIN'",
			Integer.class);
		if (superAdminCount != null && superAdminCount > 0) {
			throw new IllegalStateException(
				"A SUPER_ADMIN already exists; the bootstrap cannot create another one");
		}

		jdbcTemplate.update("""
			INSERT INTO platform_users (
				public_id, email_normalized, display_name, password_hash,
				status, platform_role
			)
			VALUES (UUID_TO_BIN(?), ?, ?, ?, 'ACTIVE', 'SUPER_ADMIN')
			""",
			UUID.randomUUID().toString(),
			normalizedEmail,
			displayName.strip(),
			passwordEncoder.encode(password));
		return true;
	}

	private void releaseLock() {
		try {
			jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, LOCK_NAME);
		}
		catch (DataAccessException exception) {
			LOGGER.warn("Could not explicitly release the SUPER_ADMIN bootstrap lock", exception);
		}
	}

	private void validateConfiguration() {
		String normalizedEmail = emailNormalizer.normalize(email);
		if (normalizedEmail.length() > 254 || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
			throw new IllegalStateException(
				"SUPER_ADMIN_BOOTSTRAP_EMAIL must contain a valid email address");
		}
		if (displayName == null || displayName.strip().length() < 2
				|| displayName.strip().length() > 160) {
			throw new IllegalStateException(
				"SUPER_ADMIN_BOOTSTRAP_DISPLAY_NAME must contain between 2 and 160 characters");
		}
		if (password == null || password.length() < 12 || password.length() > 200) {
			throw new IllegalStateException(
				"SUPER_ADMIN_BOOTSTRAP_PASSWORD must contain between 12 and 200 characters");
		}
	}
}
