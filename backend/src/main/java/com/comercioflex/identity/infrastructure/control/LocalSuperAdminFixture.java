package com.comercioflex.identity.infrastructure.control;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.identity.application.EmailNormalizer;

@Component
@Profile("local")
@ConditionalOnProperty(name = "app.local-super-admin.enabled", havingValue = "true")
public class LocalSuperAdminFixture implements CommandLineRunner {

	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate transactionTemplate;
	private final PasswordEncoder passwordEncoder;
	private final EmailNormalizer emailNormalizer;
	private final String email;
	private final String password;
	private final String displayName;

	public LocalSuperAdminFixture(
			@Qualifier("controlJdbcTemplate") JdbcTemplate jdbcTemplate,
			@Qualifier("controlTransactionTemplate") TransactionTemplate transactionTemplate,
			PasswordEncoder passwordEncoder,
			EmailNormalizer emailNormalizer,
			@Value("${app.local-super-admin.email:}") String email,
			@Value("${app.local-super-admin.password:}") String password,
			@Value("${app.local-super-admin.display-name:}") String displayName) {
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
		transactionTemplate.executeWithoutResult(status -> provision());
	}

	private void provision() {
		String normalizedEmail = emailNormalizer.normalize(email);
		var existingRoles = jdbcTemplate.queryForList(
			"SELECT platform_role FROM platform_users WHERE email_normalized = ?",
			String.class,
			normalizedEmail);
		if (!existingRoles.isEmpty()) {
			if (!"SUPER_ADMIN".equals(existingRoles.getFirst())) {
				throw new IllegalStateException(
					"LOCAL_SUPER_ADMIN_EMAIL already belongs to a non-SuperAdmin user");
			}
			return;
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
	}

	private void validateConfiguration() {
		if (emailNormalizer.normalize(email).isBlank()
				|| displayName == null
				|| displayName.isBlank()) {
			throw new IllegalStateException(
				"All LOCAL_SUPER_ADMIN_* variables are required when the fixture is enabled");
		}
		if (password == null || password.length() < 12) {
			throw new IllegalStateException(
				"LOCAL_SUPER_ADMIN_PASSWORD must contain at least 12 characters");
		}
	}
}
