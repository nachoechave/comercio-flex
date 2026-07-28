package com.comercioflex.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MembershipRoleTests {

	@Test
	void ownerCanUseEveryTenantPermission() {
		assertThat(TenantPermission.values())
			.allMatch(MembershipRole.OWNER::allows);
	}

	@Test
	void adminCanOperateTheStoreButCannotManageOwnershipOrPayments() {
		assertThat(MembershipRole.ADMIN.allows(TenantPermission.MANAGE_CATALOG)).isTrue();
		assertThat(MembershipRole.ADMIN.allows(TenantPermission.ADJUST_STOCK)).isTrue();
		assertThat(MembershipRole.ADMIN.allows(TenantPermission.MANAGE_ORDERS)).isTrue();
		assertThat(MembershipRole.ADMIN.allows(TenantPermission.MANAGE_BASIC_SETTINGS)).isTrue();
		assertThat(MembershipRole.ADMIN.allows(TenantPermission.MANAGE_MEMBERSHIPS)).isFalse();
		assertThat(MembershipRole.ADMIN.allows(TenantPermission.MANAGE_PAYMENTS)).isFalse();
	}

	@Test
	void staffHasOnlyOperationalPermissions() {
		assertThat(MembershipRole.STAFF.allows(TenantPermission.VIEW_CATALOG)).isTrue();
		assertThat(MembershipRole.STAFF.allows(TenantPermission.VIEW_INVENTORY)).isTrue();
		assertThat(MembershipRole.STAFF.allows(TenantPermission.ADJUST_STOCK)).isTrue();
		assertThat(MembershipRole.STAFF.allows(TenantPermission.MANAGE_ORDERS)).isTrue();
		assertThat(MembershipRole.STAFF.allows(TenantPermission.MANAGE_CATALOG)).isFalse();
		assertThat(MembershipRole.STAFF.allows(TenantPermission.VIEW_DASHBOARD)).isFalse();
		assertThat(MembershipRole.STAFF.allows(TenantPermission.MANAGE_BASIC_SETTINGS)).isFalse();
		assertThat(MembershipRole.STAFF.allows(TenantPermission.MANAGE_MEMBERSHIPS)).isFalse();
		assertThat(MembershipRole.STAFF.allows(TenantPermission.MANAGE_PAYMENTS)).isFalse();
	}
}
