package com.comercioflex.membership.application;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
public interface MemberIdentityDirectory {
 void requireActive(UUID publicId);
 Map<UUID, Identity> findAll(Collection<UUID> publicIds);
 record Identity(String firstName, String lastName, String email) {}
}
