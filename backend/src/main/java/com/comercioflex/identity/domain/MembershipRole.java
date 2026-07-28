package com.comercioflex.identity.domain;

import java.util.EnumSet;
import java.util.Set;

public enum MembershipRole {

	OWNER(EnumSet.allOf(TenantPermission.class)),
	ADMIN(EnumSet.of(
		TenantPermission.VIEW_DASHBOARD,
		TenantPermission.VIEW_CATALOG,
		TenantPermission.MANAGE_CATALOG,
		TenantPermission.VIEW_INVENTORY,
		TenantPermission.ADJUST_STOCK,
		TenantPermission.MANAGE_ORDERS,
		TenantPermission.MANAGE_BASIC_SETTINGS)),
	STAFF(EnumSet.of(
		TenantPermission.VIEW_CATALOG,
		TenantPermission.VIEW_INVENTORY,
		TenantPermission.ADJUST_STOCK,
		TenantPermission.MANAGE_ORDERS));

	private final Set<TenantPermission> permissions;

	MembershipRole(Set<TenantPermission> permissions) {
		this.permissions = Set.copyOf(permissions);
	}

	public boolean allows(TenantPermission permission) {
		return permissions.contains(permission);
	}
}
