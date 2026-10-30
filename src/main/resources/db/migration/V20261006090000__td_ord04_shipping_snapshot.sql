-- ORD-04: preserve validated checkout selection without inventing historical shipping facts.
-- No defaults, backfill or provider FK: this is an immutable historical snapshot.
ALTER TABLE uteexpress.orders
    ADD COLUMN shipping_provider_id BIGINT NULL,
    ADD COLUMN shipping_service_code VARCHAR(32) NULL,
    ADD CONSTRAINT ck_orders_shipping_provider_id
        CHECK (shipping_provider_id IS NULL OR shipping_provider_id > 0),
    ADD CONSTRAINT ck_orders_shipping_service_code
        CHECK (shipping_service_code IS NULL OR shipping_service_code ~ '^[A-Z][A-Z0-9_]{0,31}$');
