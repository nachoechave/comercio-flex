package com.comercioflex.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Entrega la SPA para las rutas navegables cuando frontend y backend comparten origen.
 * Los recursos estáticos y la API continúan siendo resueltos por sus handlers propios.
 */
@Controller
public class SpaForwardController {

	@GetMapping({
		"/", "/admin", "/admin/**", "/superadmin", "/superadmin/**",
		"/tiendas/{slug}", "/tiendas/{slug}/**",
		"/stores/{slug}/payment-return/{returnToken}"
	})
	public String forwardToAngular() {
		return "forward:/index.html";
	}
}
