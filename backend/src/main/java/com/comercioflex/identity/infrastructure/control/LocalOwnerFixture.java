package com.comercioflex.identity.infrastructure.control;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.comercioflex.identity.application.EmailNormalizer;
import com.comercioflex.identity.domain.MembershipRole;
import com.comercioflex.identity.domain.MembershipStatus;
import com.comercioflex.identity.domain.UserStatus;
import com.comercioflex.tenant.infrastructure.control.TenantRepository;

@Component
@Profile("local")
@ConditionalOnProperty(name = "app.local-owner.enabled", havingValue = "true")
public class LocalOwnerFixture implements CommandLineRunner {

	private final PlatformUserRepository userRepository;
	private final MembershipRepository membershipRepository;
	private final TenantRepository tenantRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailNormalizer emailNormalizer;
	private final String email;
	private final String password;
	private final String displayName;
	private final String storeSlug;

	public LocalOwnerFixture(
			PlatformUserRepository userRepository,
			MembershipRepository membershipRepository,
			TenantRepository tenantRepository,
			PasswordEncoder passwordEncoder,
			EmailNormalizer emailNormalizer,
			@Value("${app.local-owner.email:}") String email,
			@Value("${app.local-owner.password:}") String password,
			@Value("${app.local-owner.display-name:}") String displayName,
			@Value("${app.local-owner.store-slug:}") String storeSlug) {
		this.userRepository = userRepository;
		this.membershipRepository = membershipRepository;
		this.tenantRepository = tenantRepository;
		this.passwordEncoder = passwordEncoder;
		this.emailNormalizer = emailNormalizer;
		this.email = email;
		this.password = password;
		this.displayName = displayName;
		this.storeSlug = storeSlug;
	}

	@Override
	@Transactional
	public void run(String... args) {
		validateConfiguration();
		String normalizedEmail = emailNormalizer.normalize(email);
		if (userRepository.existsByEmailNormalized(normalizedEmail)) {
			return;
		}

		var tenant = tenantRepository.findActiveBySlug(storeSlug)
			.orElseThrow(() -> new IllegalStateException(
				"LOCAL_OWNER_STORE_SLUG must identify an active configured store"));

		PlatformUserEntity user = userRepository.save(new PlatformUserEntity(
			UUID.randomUUID(),
			normalizedEmail,
			displayName.strip(),
			passwordEncoder.encode(password),
			UserStatus.ACTIVE,
			Instant.now()));
		membershipRepository.save(new MembershipEntity(
			user.id(),
			tenant.id(),
			MembershipRole.OWNER,
			MembershipStatus.ACTIVE));
	}

	private void validateConfiguration() {
		if (emailNormalizer.normalize(email).isBlank()
				|| displayName == null
				|| displayName.isBlank()
				|| storeSlug == null
				|| storeSlug.isBlank()) {
			throw new IllegalStateException(
				"All LOCAL_OWNER_* variables are required when local owner fixture is enabled");
		}
		if (password == null || password.length() < 12) {
			throw new IllegalStateException(
				"LOCAL_OWNER_PASSWORD must contain at least 12 characters");
		}
	}
}
