ALTER TABLE payment_intents
    DROP CHECK ck_payment_intents_status,
    ADD CONSTRAINT ck_payment_intents_status CHECK (
        status IN (
            'CREATED',
            'PENDING',
            'APPROVED',
            'REJECTED',
            'EXPIRED',
            'REQUIRES_REVIEW'
        )
    );
