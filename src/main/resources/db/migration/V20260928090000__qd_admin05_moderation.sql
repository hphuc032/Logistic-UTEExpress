-- Forward-only migration. Vendor HIDDEN is distinct from Ops MODERATED.
ALTER TABLE uteexpress.products DROP CONSTRAINT ck_products_status;
ALTER TABLE uteexpress.products ADD CONSTRAINT ck_products_status CHECK (status IN ('ACTIVE', 'HIDDEN', 'MODERATED'));
ALTER TABLE uteexpress.products ADD COLUMN moderation_reason VARCHAR(1000);
ALTER TABLE uteexpress.products ADD CONSTRAINT ck_products_moderation_reason CHECK (
    status <> 'MODERATED' OR (moderation_reason IS NOT NULL AND btrim(moderation_reason) <> '')
);
ALTER TABLE uteexpress.shops DROP CONSTRAINT ck_shops_status;
ALTER TABLE uteexpress.shops ADD CONSTRAINT ck_shops_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'));
ALTER TABLE uteexpress.shops ADD COLUMN moderation_reason VARCHAR(1000);
ALTER TABLE uteexpress.shops ADD CONSTRAINT ck_shops_moderation_reason CHECK (
    status <> 'SUSPENDED' OR (moderation_reason IS NOT NULL AND btrim(moderation_reason) <> '')
);
