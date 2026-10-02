# ENG-01 — Favorites and recently viewed products

Owner: Quốc Đạt. Base: `develop` after PROD-01/PROD-02.

Authenticated USER/VENDOR can add and remove a product from favorites. A
`(user_id, product_id)` primary key and `ON CONFLICT DO NOTHING` make repeated
adds idempotent. A user can only read or change their own list; account ID comes
from the authenticated principal, never the request. Adding a favorite requires
the Catalog purchasability contract. Removing one is idempotent.

The public product detail remains available to guests. After a successful detail
GET by a buyer, the Engagement interceptor records the view and exposes the
favorite state to the template. The explicit POST route remains available for
clients. The per-user upsert refreshes the timestamp, locks the account row, and
trims the history to 30 rows. This makes simultaneous views and equal clock
ticks deterministic. Favorite and recent lists render hidden/moderated products
as unavailable, without a link or leaking their current name/price.
The history column is named `last_viewed_at` as in the database contract.
Favorite and history links and detail actions are shown only to USER/VENDOR;
ADMIN, MANAGER and SHIPPER do not see buyer actions in the shared UI.

Routes: `GET /user/favorites`, `POST /user/favorites/{productId}`,
`POST /user/favorites/{productId}/remove`, `GET /user/recently-viewed`, and
`POST /user/recently-viewed/{productId}`. POST routes require CSRF. The two
new tables are introduced by a forward migration, with foreign keys to users
and products. Catalog entities and services remain owned by HP.

Validation: `EngagementIT` covers duplicate prevention, account isolation,
unavailable products, 30-item trimming/refresh, detail recording, CSRF and
authorization, including buyer-only UI rendering. Full Maven/CI outcomes are
recorded in the PR after running.
