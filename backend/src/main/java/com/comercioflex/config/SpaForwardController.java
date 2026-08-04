package com.comercioflex.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Entrega la SPA para las rutas navegables cuando frontend y backend comparten origen.
 * Los recursos estáticos y la API continúan siendo resueltos por sus handlers propios.
 */
@Controller
public class SpaForwardController {

	@GetMapping({"/", "/admin", "/admin/**", "/tiendas/{slug}", "/tiendas/{slug}/**"})
	public String forwardToAngular() {
		return "forward:/index.html";
	}
}
