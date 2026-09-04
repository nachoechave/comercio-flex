ALTER TABLE orders
    ADD COLUMN payment_method VARCHAR(30) NULL
        AFTER fulfillment_type,
    ADD COLUMN list_subtotal DECIMAL(15,2) NULL
        AFTER currency_code,
    ADD COLUMN discount_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00
        AFTER list_subtotal,
    ADD COLUMN discount_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00
        AFTER discount_percentage;

-- Pedidos creados antes de esta migración no tenían descuento aplicado.
-- Conservamos su subtotal original como precio de lista.
UPDATE orders
SET payment_method = 'MERCADO_PAGO',
    list_subtotal = subtotal
WHERE payment_method IS NULL
   OR list_subtotal IS NULL;

ALTER TABLE orders
    MODIFY COLUMN payment_method VARCHAR(30) NOT NULL,
    MODIFY COLUMN list_subtotal DECIMAL(15,2) NOT NULL,
    ADD CONSTRAINT ck_orders_payment_method
        CHECK (payment_method IN ('MERCADO_PAGO', 'BANK_TRANSFER')),
    ADD CONSTRAINT ck_orders_list_subtotal
        CHECK (list_subtotal > 0),
    ADD CONSTRAINT ck_orders_discount_percentage
        CHECK (
            discount_percentage >= 0.00
            AND discount_percentage <= 50.00
        ),
    ADD CONSTRAINT ck_orders_discount_amount
        CHECK (
            discount_amount >= 0.00
            AND discount_amount <= list_subtotal
        );