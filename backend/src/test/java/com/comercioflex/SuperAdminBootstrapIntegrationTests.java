package com.comercioflex;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.comercioflex.identity.infrastructure.control.SuperAdminBootstrap;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
	"app.super-admin-bootstrap.enabled=true",
	"app.super-admin-bootstrap.email=Initial.Admin@Example.COM",
	"app.super-admin-bootstrap.password=a-secure-bootstrap-password",
	"app.super-admin-bootstrap.display-name=Administrador de plataforma"
})
class SuperAdminBootstrapIntegrationTests {

	@Autowired
	private SuperAdminBootstrap bootstrap;

	@Autowired
	@Qualifier("controlJdbcTemplate")
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void createsOnlyTheFirstSuperAdminAndKeepsItsCredentialsOnRerun() {
		var account = jdbcTemplate.queryForMap("""
			SELECT id, email_normalized, display_name, password_hash, status, platform_role
			FROM platform_users
			WHERE email_normalized = 'initial.admin@example.com'
			""");

		assertThat(account)
			.containsEntry("email_normalized", "initial.admin@example.com")
			.containsEntry("display_name", "Administrador de plataforma")
			.containsEntry("status", "ACTIVE")
			.containsEntry("platform_role", "SUPER_ADMIN");
		String originalHash = (String) account.get("password_hash");
		assertThat(passwordEncoder.matches("a-secure-bootstrap-password", originalHash)).isTrue();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM memberships WHERE user_id = ?",
			Integer.class,
			account.get("id"))).isZero();

		bootstrap.run();

		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM platform_users WHERE platform_role = 'SUPER_ADMIN'",
			Integer.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT password_hash FROM platform_users WHERE email_normalized = 'initial.admin@example.com'",
			String.class)).isEqualTo(originalHash);
	}
}
