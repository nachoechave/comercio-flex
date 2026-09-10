package com.comercioflex.membership.infrastructure.control;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.security.access.AccessDeniedException;
import com.comercioflex.membership.application.MemberIdentityDirectory;
@Repository
public class ControlMemberIdentityDirectory implements MemberIdentityDirectory {
 private final JdbcTemplate jdbc;
 public ControlMemberIdentityDirectory(@Qualifier("controlJdbcTemplate") JdbcTemplate jdbc) { this.jdbc = jdbc; }
 public void requireActive(UUID id) {
  Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM platform_users WHERE public_id = UUID_TO_BIN(?) AND status = 'ACTIVE'", Integer.class, id.toString());
  if (count == null || count != 1) throw new AccessDeniedException("La cuenta no está disponible.");
 }
 public Map<UUID, Identity> findAll(Collection<UUID> ids) {
  Map<UUID, Identity> result = new HashMap<>();
  if (ids.isEmpty()) return result;
  String parameters = String.join(",", Collections.nCopies(ids.size(), "UUID_TO_BIN(?)"));
  jdbc.query("SELECT BIN_TO_UUID(public_id) public_id, COALESCE(first_name,display_name) first_name, COALESCE(last_name,'') last_name,email_normalized FROM platform_users WHERE public_id IN (" + parameters + ")",
   rs -> { result.put(UUID.fromString(rs.getString("public_id")), new Identity(rs.getString("first_name"),rs.getString("last_name"),rs.getString("email_normalized"))); }, ids.stream().map(UUID::toString).toArray());
  return result;
 }
}
