package com.comercioflex.identity.application;

import com.comercioflex.identity.domain.MembershipRole;

public record ActiveMembership(
	String storeSlug,
	String storeName,
	MembershipRole role) {
}
