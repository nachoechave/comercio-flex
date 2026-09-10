package com.comercioflex.identity.infrastructure;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

import com.comercioflex.identity.application.PublicIdentityRepository;
import com.comercioflex.identity.application.PublicIdentityService;
import com.comercioflex.notification.application.TransactionalEmail;
import com.comercioflex.notification.application.TransactionalEmailSender;
import com.comercioflex.notification.infrastructure.EmailProperties;
import com.comercioflex.tenant.application.TenantResolver;
import com.comercioflex.tenant.application.TenantDomainResolver;
import com.comercioflex.tenant.application.TenantContext;

/** Identity-specific delivery: plaintext secrets only live in memory and the email. */
@Service
public class IdentityRecoveryDelivery {

	private static final Logger LOG = LoggerFactory.getLogger(IdentityRecoveryDelivery.class);
	private final TaskExecutor executor;
	private final PublicIdentityRepository repository;
	private final TransactionTemplate transactions;
	private final TransactionalEmailSender sender;
	private final EmailProperties emailProperties;
	private final TenantResolver tenants;
	private final TenantDomainResolver domains;
	private final TenantContext context;
	private final URI publicBase;
	private final SecureRandom random = new SecureRandom();

	public IdentityRecoveryDelivery(@Qualifier("identityRecoveryExecutor") TaskExecutor executor,
			PublicIdentityRepository repository, @Qualifier("controlTransactionTemplate") TransactionTemplate transactions,
			TransactionalEmailSender sender, EmailProperties emailProperties, TenantResolver tenants,
			TenantDomainResolver domains, TenantContext context,
			@Value("${app.identity.public-base-uri:http://localhost:4200}") URI publicBase) {
		this.executor = executor;
		this.repository = repository;
		this.transactions = transactions;
		this.sender = sender;
		this.emailProperties = emailProperties;
		this.tenants = tenants;
		this.domains = domains;
		this.context = context;
		this.publicBase = publicBase;
		boolean local = "http".equals(publicBase.getScheme()) && "localhost".equals(publicBase.getHost());
		if ((!"https".equals(publicBase.getScheme()) && !local) || publicBase.getHost() == null
				|| publicBase.getRawUserInfo() != null || publicBase.getRawQuery() != null
				|| publicBase.getRawFragment() != null || !(publicBase.getPath().isEmpty() || publicBase.getPath().equals("/"))) {
			throw new IllegalArgumentException("IDENTITY_PUBLIC_BASE_URI must be a trusted HTTPS origin (localhost allowed for development).");
		}
	}

	public void request(String tenantSlug, String email) {
		// Queue both existing and unknown identities. Neither DB lookup nor SMTP latency reaches the response.
		try { executor.execute(() -> deliver(tenantSlug, email)); }
		catch (RejectedExecutionException exception) { LOG.warn("identity_recovery_queue_full"); }
	}

	private void deliver(String slug, String email) {
		try {
			if (!emailProperties.isEnabled()) return;
			var tenant = tenants.resolveActive(slug);
			PublicIdentityService.requireRadio(tenant);
			try (var ignored = context.open(tenant.databaseKey())) {
				var user = transactions.execute(status -> repository.activeUser(email));
				if (user == null || user.isEmpty()) return;
				byte[] bytes = new byte[32];
				random.nextBytes(bytes);
				String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
				transactions.executeWithoutResult(status -> repository.storeReset(user.get(), tenant.id(),
					PublicIdentityService.hash(token), Instant.now().plus(Duration.ofMinutes(30))));
				String origin = publicBase.toString().replaceAll("/$", "");
				String path = "/tiendas/" + tenant.slug() + "/nueva-contrasena";
				var hostname = domains.verifiedPrimaryHostname(tenant.id());
				if (hostname.isPresent()) {
					origin = "https://" + hostname.get();
					path = "/nueva-contrasena";
				}
				String url = origin + path + "#token=" + token;
				String title = "Restablecer contraseña — " + tenant.displayName();
				String text = tenant.displayName() + "\n\nRestablecé tu contraseña de Comercio Flex: " + url
					+ "\nEl enlace vence en 30 minutos y se puede usar una sola vez. La contraseña es compartida entre tus sitios."
					+ "\nSi no lo solicitaste, ignorá este mensaje.";
				sender.send(new TransactionalEmail(email, title,
					"<h1>" + HtmlUtils.htmlEscape(tenant.displayName()) + "</h1><p>Restablecé tu contraseña de Comercio Flex.</p>"
					+ "<p><a href=\"" + HtmlUtils.htmlEscape(url) + "\">Crear una nueva contraseña</a></p>"
					+ "<p>El enlace vence en 30 minutos y es de un solo uso. El cambio afecta tu identidad global.</p>"
					+ "<p>Si no lo solicitaste, ignorá este mensaje.</p>", text));
			}
		}
		catch (RuntimeException exception) {
			// Do not log exception messages, recipient, message bodies or reset links.
			LOG.warn("identity_recovery_delivery_failed");
		}
	}
}
