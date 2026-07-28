package com.comercioflex.identity.infrastructure.control;

import com.comercioflex.identity.domain.MembershipRole;
import com.comercioflex.identity.domain.MembershipStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "memberships",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_memberships_user_tenant",
		columnNames = {"user_id", "tenant_id"}))
class MembershipEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "tenant_id", nullable = false)
	private Long tenantId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MembershipRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MembershipStatus status;

	protected MembershipEntity() {
	}

	MembershipEntity(Long userId, Long tenantId, MembershipRole role, MembershipStatus status) {
		this.userId = userId;
		this.tenantId = tenantId;
		this.role = role;
		this.status = status;
	}
}
