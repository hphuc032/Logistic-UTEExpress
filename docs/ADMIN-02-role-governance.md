# ADMIN-02 role governance

ADMIN can grant or revoke USER, ADMIN, MANAGER and SHIPPER on an active account from `/admin/accounts/{id}`. New registrations still receive USER by default. VENDOR is granted by shop approval and is not manually managed here. MANAGER has read-only access to account roles and `/manager/shippers`.

Every role mutation requires the account's current version and CSRF token. The actor comes from the authenticated principal, not an HTTP field. A successful mutation increments token_version and account version, then writes an audit row in the same transaction. Stale versions, duplicate grants, missing roles and inactive targets fail closed. The ADMIN role row is locked before counting active admins or locking an admin account, so concurrent revokes/locks cannot remove the last active admin. SHIP-01 can use `RoleGovernanceService.activeShippers()` for assignment choices; this excludes locked and unverified accounts.

This task does not change SecurityConfig, the USER registration default, or the shop approval VENDOR role workflow.
