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
		TenantPermission.MANAGE_POS_SALES,
		TenantPermission.MANAGE_BASIC_SETTINGS,
		TenantPermission.VIEW_RADIO_MEMBERSHIPS,
		TenantPermission.MANAGE_RADIO_PLANS)),
	MANAGER(EnumSet.of(
		TenantPermission.VIEW_DASHBOARD,
		TenantPermission.VIEW_CATALOG,
		TenantPermission.VIEW_INVENTORY,
		TenantPermission.ADJUST_STOCK,
		TenantPermission.MANAGE_ORDERS,
		TenantPermission.MANAGE_POS_SALES)),
	SELLER(EnumSet.of(
		TenantPermission.VIEW_CATALOG,
		TenantPermission.VIEW_INVENTORY,
		TenantPermission.MANAGE_POS_SALES)),
	STAFF(EnumSet.of(
		TenantPermission.VIEW_CATALOG,
		TenantPermission.VIEW_INVENTORY,
		TenantPermission.ADJUST_STOCK,
		TenantPermission.MANAGE_ORDERS,
		TenantPermission.MANAGE_POS_SALES));

	private final Set<TenantPermission> permissions;

	MembershipRole(Set<TenantPermission> permissions) {
		this.permissions = Set.copyOf(permissions);
	}

	public boolean allows(TenantPermission permission) {
		return permissions.contains(permission);
	}
}
