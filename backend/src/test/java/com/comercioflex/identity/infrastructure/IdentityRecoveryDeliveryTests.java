package com.comercioflex.identity.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.comercioflex.identity.application.PublicIdentityRepository;
import com.comercioflex.notification.application.TransactionalEmail;
import com.comercioflex.notification.application.TransactionalEmailSender;
import com.comercioflex.notification.infrastructure.EmailProperties;
import com.comercioflex.tenant.application.*;
import com.comercioflex.tenant.domain.TenantType;

class IdentityRecoveryDeliveryTests {
    private final PublicIdentityRepository repository = mock(PublicIdentityRepository.class);
    private final TransactionalEmailSender sender = mock(TransactionalEmailSender.class);
    private final TenantResolver tenants = mock(TenantResolver.class);
    private final TenantDomainResolver domains = mock(TenantDomainResolver.class);
    private final TenantContext context = new TenantContext();
    private final EmailProperties properties = new EmailProperties();
    private final TransactionTemplate transactions = new TransactionTemplate(mock(PlatformTransactionManager.class));
    @BeforeEach void prepare() {
        properties.setEnabled(true);
        when(tenants.resolveActive("radio")).thenReturn(new ResolvedTenant(1L, "radio", "Radio <script>", "db-radio", TenantType.RADIO));
        when(repository.activeUser("user@example.com")).thenReturn(Optional.of(2L));
        when(domains.verifiedPrimaryHostname(1L)).thenReturn(Optional.empty());
    }
    private IdentityRecoveryDelivery delivery(TaskExecutor executor, String origin) {
        return new IdentityRecoveryDelivery(executor, repository, transactions, sender, properties, tenants, domains, context, URI.create(origin));
    }
    @Test void usesOnlyVerifiedCustomDomainAndEscapesBranding() {
        when(domains.verifiedPrimaryHostname(1L)).thenReturn(Optional.of("radio.example"));
        doAnswer(invocation -> {
            assertThat(context.currentDatabaseKey()).contains("db-radio"); return null;
        }).when(sender).send(any());
        delivery(Runnable::run, "https://platform.example").request("radio", "user@example.com");
        var message = ArgumentCaptor.forClass(TransactionalEmail.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().htmlBody()).contains("Radio &lt;script&gt;", "https://radio.example/nueva-contrasena#token=").doesNotContain("<script>");
        assertThat(context.currentDatabaseKey()).isEmpty();
    }
    @Test void rejectsUntrustedOriginConfiguration() {
        for (String origin : new String[] { "http://external.example", "https://user:secret@example.com", "https://example.com/path", "https://example.com?redirect=evil", "https://example.com#fragment" }) {
            assertThatThrownBy(() -> delivery(Runnable::run, origin)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatCode(() -> delivery(Runnable::run, "http://localhost:4200")).doesNotThrowAnyException();
    }
    @Test void lookupOccursOnlyAfterTheQueuedTaskRuns() {
        TaskExecutor executor = mock(TaskExecutor.class);
        delivery(executor, "https://platform.example").request("radio", "user@example.com");
        verify(executor).execute(any(Runnable.class));
        verifyNoInteractions(repository, sender);
    }
    @Test void smtpFailureDoesNotEscapeAndAlwaysClosesTenantContext() {
        doThrow(new IllegalStateException("sensitive SMTP details")).when(sender).send(any());
        assertThatCode(() -> delivery(Runnable::run, "https://platform.example").request("radio", "user@example.com")).doesNotThrowAnyException();
        assertThat(context.currentDatabaseKey()).isEmpty();
    }
    @Test void disabledEmailAndUnknownUsersDoNotSendMail() {
        properties.setEnabled(false);
        delivery(Runnable::run, "https://platform.example").request("radio", "user@example.com");
        verifyNoInteractions(repository, sender);
        properties.setEnabled(true);
        when(repository.activeUser("unknown@example.com")).thenReturn(Optional.empty());
        delivery(Runnable::run, "https://platform.example").request("radio", "unknown@example.com");
        verifyNoInteractions(sender);
        verify(repository, never()).storeReset(anyLong(), anyLong(), any(), any());
        assertThat(context.currentDatabaseKey()).isEmpty();
    }
    @Test void queueRejectionDoesNotChangePublicResponse() {
        TaskExecutor executor = task -> { throw new RejectedExecutionException(); };
        assertThatCode(() -> delivery(executor, "https://platform.example").request("radio", "user@example.com")).doesNotThrowAnyException();
        verifyNoInteractions(repository, sender);
    }
}
