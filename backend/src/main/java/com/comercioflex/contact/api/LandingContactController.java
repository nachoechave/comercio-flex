package com.comercioflex.contact.api;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import com.comercioflex.notification.application.TransactionalEmail;
import com.comercioflex.notification.application.TransactionalEmailSender;
import com.comercioflex.notification.infrastructure.EmailProperties;

@RestController
@RequestMapping("/api/v1/public/contact")
public class LandingContactController {
	private static final Duration MIN_FORM_AGE = Duration.ofSeconds(2);
	private static final Duration MAX_FORM_AGE = Duration.ofHours(24);
	private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(10);
	private static final int RATE_LIMIT_MAX_REQUESTS = 5;

	private final TransactionalEmailSender emailSender;
	private final EmailProperties emailProperties;
	private final String recipientEmail;
	private final Map<String, Deque<Long>> requestTimesByIp = new ConcurrentHashMap<>();

	public LandingContactController(
			TransactionalEmailSender emailSender,
			EmailProperties emailProperties,
			@Value("${app.contact.recipient-email:nacho9847@gmail.com}") String recipientEmail) {
		this.emailSender = emailSender;
		this.emailProperties = emailProperties;
		this.recipientEmail = recipientEmail;
	}

	@PostMapping
	public ResponseEntity<Void> submit(
			@Valid @RequestBody LandingContactRequest request,
			HttpServletRequest httpRequest) {
		if (request.website() != null && !request.website().isBlank()) {
			return ResponseEntity.accepted().build();
		}

		long now = System.currentTimeMillis();
		long formAgeMillis = now - request.startedAt();
		if (formAgeMillis < MIN_FORM_AGE.toMillis() || formAgeMillis > MAX_FORM_AGE.toMillis()) {
			return ResponseEntity.badRequest().build();
		}

		if (!allowRequest(clientIp(httpRequest), now)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}

		if (!emailProperties.isEnabled()) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}

		emailSender.send(buildEmail(request));
		return ResponseEntity.accepted().build();
	}

	ResponseEntity<Void> submit(LandingContactRequest request) {
		return submit(request, null);
	}

	private boolean allowRequest(String clientIp, long now) {
		Deque<Long> timestamps = requestTimesByIp.computeIfAbsent(clientIp, ignored -> new ArrayDeque<>());
		synchronized (timestamps) {
			long cutoff = now - RATE_LIMIT_WINDOW.toMillis();
			while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
				timestamps.removeFirst();
			}
			if (timestamps.size() >= RATE_LIMIT_MAX_REQUESTS) {
				return false;
			}
			timestamps.addLast(now);
			return true;
		}
	}

	private String clientIp(HttpServletRequest request) {
		if (request == null) {
			return "local-test";
		}
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			int comma = forwardedFor.indexOf(',');
			String firstIp = comma >= 0 ? forwardedFor.substring(0, comma) : forwardedFor;
			if (!firstIp.isBlank()) {
				return firstIp.trim();
			}
		}
		String remoteAddress = request.getRemoteAddr();
		return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
	}

	private TransactionalEmail buildEmail(LandingContactRequest request) {
		String name = request.name().trim();
		String business = request.business().trim();
		String email = request.email().trim();
		String whatsapp = request.whatsapp().trim();
		String message = request.message().trim();
		String safeSubjectBusiness = business.replace('\r', ' ').replace('\n', ' ');

		String textBody = "Nueva consulta desde comercioflex.com.ar\n\n"
			+ "Nombre: " + name + "\n"
			+ "Negocio: " + business + "\n"
			+ "Email: " + email + "\n"
			+ "WhatsApp: " + whatsapp + "\n\n"
			+ "Mensaje:\n" + message;

		String htmlBody = """
			<div style="font-family:Arial,sans-serif;color:#172033;line-height:1.55">
			  <h2 style="margin:0 0 16px;color:#0f172a">Nueva consulta de Comercio Flex</h2>
			  <table style="border-collapse:collapse;width:100%%;max-width:620px">
			    <tr><td style="padding:6px 0;font-weight:700">Nombre</td><td style="padding:6px 0">%s</td></tr>
			    <tr><td style="padding:6px 0;font-weight:700">Negocio</td><td style="padding:6px 0">%s</td></tr>
			    <tr><td style="padding:6px 0;font-weight:700">Email</td><td style="padding:6px 0">%s</td></tr>
			    <tr><td style="padding:6px 0;font-weight:700">WhatsApp</td><td style="padding:6px 0">%s</td></tr>
			  </table>
			  <h3 style="margin:20px 0 8px">Mensaje</h3>
			  <p style="white-space:pre-line;margin:0">%s</p>
			</div>
			""".formatted(
				HtmlUtils.htmlEscape(name),
				HtmlUtils.htmlEscape(business),
				HtmlUtils.htmlEscape(email),
				HtmlUtils.htmlEscape(whatsapp),
				HtmlUtils.htmlEscape(message));

		return new TransactionalEmail(
			recipientEmail,
			"Nueva consulta Comercio Flex · " + safeSubjectBusiness,
			htmlBody,
			textBody);
	}

	public record LandingContactRequest(
		@NotBlank @Size(max = 80) String name,
		@NotBlank @Size(max = 120) String business,
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Size(min = 6, max = 30) String whatsapp,
		@NotBlank @Size(max = 2000) String message,
		@Size(max = 120) String website,
		@Positive long startedAt) {
	}
}
