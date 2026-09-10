ALTER TABLE merchant_payment_capabilities ADD membership_checkout_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 ADD CONSTRAINT ck_membership_checkout_enabled CHECK (membership_checkout_enabled IN (FALSE, TRUE));
ALTER TABLE payment_webhook_routes ADD destination_type VARCHAR(30) NOT NULL DEFAULT 'ECOMMERCE_ORDER',
 ADD CONSTRAINT ck_webhook_destination CHECK (destination_type IN ('ECOMMERCE_ORDER','RADIO_MEMBERSHIP'));
CREATE INDEX ix_membership_route_recovery ON payment_webhook_routes (destination_type, status, updated_at);
