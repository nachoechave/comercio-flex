ALTER TABLE payment_transactions
    DROP CHECK ck_payment_transactions_currency,
    ADD CONSTRAINT ck_payment_transactions_currency
        CHECK (REGEXP_LIKE(currency_code, '^[A-Z]{3}$', 'c'));

ALTER TABLE payment_intents
    DROP CHECK ck_payment_intents_currency,
    ADD CONSTRAINT ck_payment_intents_currency
        CHECK (REGEXP_LIKE(currency_code, '^[A-Z]{3}$', 'c'));
