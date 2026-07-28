package com.comercioflex.identity.infrastructure.control;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.comercioflex.identity.application.UserCredentials;

public interface PlatformUserRepository extends JpaRepository<PlatformUserEntity, Long> {

	@Query("""
		SELECT new com.comercioflex.identity.application.UserCredentials(
			user.id,
			user.publicId,
			user.emailNormalized,
			user.displayName,
			user.passwordHash,
			user.status
		)
		FROM PlatformUserEntity user
		WHERE user.emailNormalized = :email
		""")
	Optional<UserCredentials> findCredentialsByEmail(@Param("email") String email);

	boolean existsByEmailNormalized(String emailNormalized);
}
