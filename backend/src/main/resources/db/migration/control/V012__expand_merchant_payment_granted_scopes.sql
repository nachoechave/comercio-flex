-- OAuth providers return variable-length granted scope metadata, which must be
-- preserved completely instead of being constrained to 255 characters.
ALTER TABLE merchant_payment_connections
    MODIFY COLUMN granted_scopes TEXT NULL;
