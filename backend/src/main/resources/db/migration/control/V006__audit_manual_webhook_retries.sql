CREATE TABLE payment_webhook_retry_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    webhook_event_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    actor_user_public_id BINARY(16) NOT NULL,
    action_name VARCHAR(40) NOT NULL,
	previous_attempt_count INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_payment_webhook_retry_audit PRIMARY KEY (id),
    CONSTRAINT uk_payment_webhook_retry_audit_public_id UNIQUE (public_id),
    CONSTRAINT fk_payment_webhook_retry_audit_event FOREIGN KEY (webhook_event_id)
        REFERENCES payment_webhook_events (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_webhook_retry_audit_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_webhook_retry_audit_actor FOREIGN KEY (actor_user_id)
        REFERENCES platform_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_payment_webhook_retry_audit_action
        CHECK (action_name = 'MANUAL_RETRY_SCHEDULED'),
    CONSTRAINT ck_payment_webhook_retry_audit_attempts
        CHECK (previous_attempt_count >= 0)
);

CREATE INDEX ix_payment_webhook_retry_audit_event
    ON payment_webhook_retry_audit (webhook_event_id, created_at);

CREATE INDEX ix_payment_webhook_retry_audit_tenant
    ON payment_webhook_retry_audit (tenant_id, created_at);

CREATE INDEX ix_payment_webhook_events_tenant_status
    ON payment_webhook_events (route_id, status, updated_at, id);
