package com.comercioflex.tenant.application;

import java.util.Set;

/** Platform namespaces cannot be claimed by tenants. */
public final class TenantPublicPaths {
 private TenantPublicPaths() {}
 public static final Set<String> RESERVED = Set.of("no-encontrado", "api", "admin", "superadmin", "tiendas", "stores", "login", "logout", "auth", "oauth", "actuator", "error", "assets", "static", "index.html", "favicon.ico", "robots.txt", "sitemap.xml", "registro", "ingresar", "olvide-contrasena", "nueva-contrasena", "mi-cuenta", "socios", "programas", "nosotros", "carrito", "checkout", "mis-pedidos", "pedidos", "productos", "payment-return");
 public static boolean validSlug(String slug) {
  return slug != null && slug.length() <= 100 && slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") && !RESERVED.contains(slug);
 }
 public static String radio(String slug) {
  if (!validSlug(slug)) throw new TenantNotFoundException();
  return "/" + slug;
 }
 public static boolean cleanPath(String path) {
  String[] parts = path.split("/", 3);
  if (parts.length < 2 || !validSlug(parts[1])) return false;
  return parts.length == 2 || Set.of("", "programas", "nosotros", "socios", "login", "ingresar", "registro", "olvide-contrasena", "nueva-contrasena", "mi-cuenta", "mi-cuenta/plan", "mi-cuenta/cuotas", "mi-cuenta/perfil", "mi-cuenta/pago-retorno").contains(parts[2]);
 }
}
