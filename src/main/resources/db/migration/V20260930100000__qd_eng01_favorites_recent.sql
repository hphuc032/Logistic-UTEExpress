CREATE TABLE uteexpress.favorites (
    user_id BIGINT NOT NULL REFERENCES uteexpress.users (id),
    product_id BIGINT NOT NULL REFERENCES uteexpress.products (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_favorites PRIMARY KEY (user_id, product_id)
);
CREATE INDEX ix_favorites_user_created ON uteexpress.favorites (user_id, created_at DESC, product_id DESC);

CREATE TABLE uteexpress.product_views (
    user_id BIGINT NOT NULL REFERENCES uteexpress.users (id),
    product_id BIGINT NOT NULL REFERENCES uteexpress.products (id),
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_product_views PRIMARY KEY (user_id, product_id)
);
CREATE INDEX ix_product_views_user_viewed ON uteexpress.product_views (user_id, viewed_at DESC, product_id DESC);
