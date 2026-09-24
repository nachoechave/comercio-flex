package com.comercioflex.config;

import java.util.Locale;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class PlatformSeoController {

	private static final Set<String> PLATFORM_HOSTS = Set.of(
		"comercioflex.com.ar",
		"www.comercioflex.com.ar");

	private static final String ROBOTS = """
		User-agent: OAI-SearchBot
		Allow: /
		Disallow: /admin/
		Disallow: /superadmin/
		Disallow: /api/
		Disallow: /actuator/
		Disallow: /checkout
		Disallow: /carrito
		Disallow: /mi-cuenta
		Disallow: /mis-pedidos
		Disallow: /pedidos/
		Disallow: /payment-return/

		User-agent: *
		Allow: /
		Disallow: /admin/
		Disallow: /superadmin/
		Disallow: /api/
		Disallow: /actuator/
		Disallow: /checkout
		Disallow: /carrito
		Disallow: /mi-cuenta
		Disallow: /mis-pedidos
		Disallow: /pedidos/
		Disallow: /payment-return/

		Sitemap: https://comercioflex.com.ar/sitemap.xml
		""";

	private static final String SITEMAP = """
		<?xml version="1.0" encoding="UTF-8"?>
		<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
		  <url>
		    <loc>https://comercioflex.com.ar/</loc>
		  </url>
		</urlset>
		""";

	@GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> robots(HttpServletRequest request) {
		if (!isPlatformHost(request)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok()
			.contentType(MediaType.TEXT_PLAIN)
			.body(ROBOTS);
	}

	@GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
	public ResponseEntity<String> sitemap(HttpServletRequest request) {
		if (!isPlatformHost(request)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_XML)
			.body(SITEMAP);
	}

	static boolean isPlatformHost(HttpServletRequest request) {
		String host = request.getServerName();
		if (host == null || host.isBlank()) {
			return false;
		}
		String normalized = host.trim().toLowerCase(Locale.ROOT);
		if (normalized.endsWith(".")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return PLATFORM_HOSTS.contains(normalized);
	}
}
