package com.comercioflex.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Entrega la SPA para las rutas navegables cuando frontend y backend comparten origen.
 * Los recursos estáticos y la API continúan siendo resueltos por sus handlers propios.
 */
@Controller
public class SpaForwardController {
	private final com.comercioflex.tenant.application.TenantResolver tenants;
	public SpaForwardController(com.comercioflex.tenant.application.TenantResolver tenants) { this.tenants = tenants; }

	@GetMapping({"/{slug:[a-z0-9-]+}", "/{slug}/programas", "/{slug}/nosotros", "/{slug}/socios", "/{slug}/login", "/{slug}/ingresar", "/{slug}/registro", "/{slug}/olvide-contrasena", "/{slug}/nueva-contrasena", "/{slug}/mi-cuenta", "/{slug}/mi-cuenta/**"})
	public String radio(jakarta.servlet.http.HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		String first = path.substring(1).split("/", 2)[0];
		if (java.util.Set.of("admin", "superadmin", "login", "registro", "ingresar", "olvide-contrasena", "nueva-contrasena", "mi-cuenta", "socios", "carrito", "checkout", "mis-pedidos", "pedidos", "productos", "payment-return", "programas", "nosotros", "no-encontrado").contains(first)) return "forward:/index.html";
		if (!com.comercioflex.tenant.application.TenantPublicPaths.cleanPath(path)) throw new com.comercioflex.tenant.application.TenantNotFoundException();
		var tenant = tenants.resolveActive(path.split("/")[1]);
		if (tenant.tenantType() != com.comercioflex.tenant.domain.TenantType.RADIO) throw new com.comercioflex.tenant.application.TenantNotFoundException();
		return "forward:/index.html";
	}

	@GetMapping({
			"/programas", "/nosotros", "/login", "/no-encontrado",
			"/", "/admin", "/admin/**", "/superadmin", "/superadmin/**",
			"/stores/{slug}/payment-return/{returnToken}",
			"/payment-return/{returnToken}",
			"/registro", "/ingresar", "/olvide-contrasena", "/nueva-contrasena", "/mi-cuenta", "/mi-cuenta/**",
			"/socios", "/carrito",
			"/checkout",
			"/mis-pedidos",
			"/pedidos/**",
			"/productos/**"
	})
	public String forwardToAngular() {
		return "forward:/index.html";
	}
	@GetMapping({"/tiendas/{slug}", "/tiendas/{slug}/**"})
	public String legacy(@org.springframework.web.bind.annotation.PathVariable String slug, jakarta.servlet.http.HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		String suffix = path.substring(("/tiendas/" + slug).length());
		if (suffix.equals("/admin") || suffix.startsWith("/admin/")) return "forward:/index.html";
		com.comercioflex.tenant.application.ResolvedTenant tenant;
        try { tenant = tenants.resolveActive(slug); }
        catch (com.comercioflex.tenant.application.TenantNotFoundException ignored) { return "forward:/index.html"; }
		if (tenant.tenantType() != com.comercioflex.tenant.domain.TenantType.RADIO) return "forward:/index.html";
		if (suffix.equals("/ingresar")) suffix = "/login";
		String target = com.comercioflex.tenant.application.TenantPublicPaths.radio(slug) + suffix;
		if (!com.comercioflex.tenant.application.TenantPublicPaths.cleanPath(target)) throw new com.comercioflex.tenant.application.TenantNotFoundException();
		return "redirect:" + target + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
	}
}
