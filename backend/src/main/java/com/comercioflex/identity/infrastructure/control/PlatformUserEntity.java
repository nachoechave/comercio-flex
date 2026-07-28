package com.comercioflex.identity.infrastructure.control;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.comercioflex.identity.domain.UserStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "platform_users")
class PlatformUserEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "public_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
	private UUID publicId;

	@Column(name = "email_normalized", nullable = false, unique = true, length = 254)
	private String emailNormalized;

	@Column(name = "display_name", nullable = false, length = 160)
	private String displayName;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private UserStatus status;

	@Column(name = "password_changed_at", nullable = false)
	private Instant passwordChangedAt;

	protected PlatformUserEntity() {
	}

	PlatformUserEntity(
			UUID publicId,
			String emailNormalized,
			String displayName,
			String passwordHash,
			UserStatus status,
			Instant passwordChangedAt) {
		this.publicId = publicId;
		this.emailNormalized = emailNormalized;
		this.displayName = displayName;
		this.passwordHash = passwordHash;
		this.status = status;
		this.passwordChangedAt = passwordChangedAt;
	}

	Long id() {
		return id;
	}
}
