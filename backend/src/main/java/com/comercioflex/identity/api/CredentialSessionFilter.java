package com.comercioflex.identity.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.comercioflex.identity.application.PlatformPrincipal;

/** Stops a revoked session being resurrected by an in-flight request after a password reset. */
public class CredentialSessionFilter extends OncePerRequestFilter {

	private final JdbcTemplate jdbc;

	public CredentialSessionFilter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof PlatformPrincipal principal) {
			boolean valid = jdbc.query("SELECT password_hash FROM platform_users WHERE id = ?",
				(rs, n) -> MessageDigest.isEqual(rs.getString(1).getBytes(StandardCharsets.UTF_8),
					principal.getPassword().getBytes(StandardCharsets.UTF_8)), principal.id())
				.stream().findFirst().orElse(false);
			if (!valid) {
				SecurityContextHolder.clearContext();
				var session = request.getSession(false);
				if (session != null) session.invalidate();
			}
		}
		chain.doFilter(request, response);
	}
}
