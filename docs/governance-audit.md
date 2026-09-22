# Governance audit contract — ADMIN-00

Use `AuditLogService.append(AuditEntry)` from a trusted application service after authorizing a business change. There is no public HTTP command for audit writes.

The caller must:
1. Resolve actorId through the approved identity boundary. Never bind actorId from a form or parse an opaque CurrentUser subject. Use null only for a documented system action.
2. Open a write transaction and perform/validate the business action in it.
3. Append the audit record in that same transaction. Do not ignore an audit failure.
4. Supply server-defined action, targetType and reason codes (uppercase letters, digits and underscore, 1–64 characters, starting with a letter).
5. Use a positive logical targetId. The polymorphic target is not a database FK.

## Snapshots and sensitive information

beforeState/afterState are defensively copied maps. Null maps become empty. Only the following fields are accepted:

| Key | Value |
| --- | --- |
| status | Uppercase state code, letters/underscore, up to 40 characters |
| active | true / false |
| role | USER / VENDOR / MANAGER / ADMIN / SHIPPER |
| ratePercent | Nonnegative decimal <= 100, up to 4 fractional digits |
| relatedId | Positive Long value |
| version | Nonnegative Long value |

The service writes keys in sorted order as a compact summary. These are audit serialization limits, not business permission limits (for example Manager commission limits must still be enforced by the owning action service).

Do not pass secrets disguised as allowed status/action/reason codes. The caller supplies constants and approved enum values, not arbitrary request text. No generic filter can make unrestricted personal text safe to audit. Passwords/hashes, OTP, JWT, email, address and raw payload fields are not allowed keys. Any future need for new fields or free-text reasons requires an explicit safe mapping and review.

## Persistence and atomicity

`audit_logs.actor_id` is a nullable FK to AUTH-01 users. No cascade deletes are configured. Application records are immutable and the repository offers no delete operation. Direct DB administration is outside this application guarantee.

MANDATORY propagation intentionally refuses standalone calls; saveAndFlush exposes constraint errors within the business transaction. It does not use REQUIRES_NEW or after-commit logging. A rolled-back action must leave no success audit.

No audit is generated merely for viewing a dashboard. State-changing governance tasks will call this service when implemented. The system-wide audit viewer belongs to DASH-02 and is Admin-only.
