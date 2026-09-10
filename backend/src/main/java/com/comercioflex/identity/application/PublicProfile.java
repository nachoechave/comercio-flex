package com.comercioflex.identity.application;

/** Global identity data only; never contains roles, internal IDs or credentials. */
public record PublicProfile(String firstName, String lastName, String email, String phone) {
}
