package com.comercioflex.identity.api;

import java.util.List;

import com.comercioflex.identity.application.ActiveMembership;
import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.domain.MembershipRole;
import com.comercioflex.identity.domain.PlatformRole;

public record SessionResponse(
	boolean authenticated,
	UserResponse user,
	List<MembershipResponse> memberships) {

	public static SessionResponse anonymous() {
		return new SessionResponse(false, null, List.of());
	}

	public static SessionResponse authenticated(
			PlatformPrincipal principal,
			List<ActiveMembership> memberships,
			PlatformRole platformRole) {
		return new SessionResponse(
			true,
			new UserResponse(
				principal.publicId().toString(),
				principal.email(),
				principal.displayName(),
				platformRole),
			memberships.stream()
				.map(membership -> new MembershipResponse(
					membership.storeSlug(),
					membership.storeName(),
					membership.role()))
				.toList());
	}

	public record UserResponse(
		String id,
		String email,
		String displayName,
		PlatformRole platformRole) {
	}

	public record MembershipResponse(
		String storeSlug,
		String storeName,
		MembershipRole role) {
	}
}
