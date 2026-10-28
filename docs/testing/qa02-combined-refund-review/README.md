# QA-02 combined refund verification — Draft review packet

Publication is authorized by TD for QD/HP technical review only. QA-02 remains IN PROGRESS.
HP Final Acceptance has not been supplied. No approval or merge is requested by this packet.

## Read first

- [Historical verification report](QA-02-final-acceptance-draft.md)
- [Sanitized combined evidence](QA-02-combined-evidence.json)
- [QD migration/seed handoff](QD-shared-seed-handoff.md)
- [Operation-specific SELECT assertion](refund-operation-invariants.proposal.sql)
- [Tested adapter reference](combined-refund-adapter.reference.py.txt)
- [Frozen tested-source inventory](source-freeze.json)
- [Publication provenance/checksums](publication-manifest.json)

Returned-COD completion retains Order REFUNDED and COD Payment PAID. Cancelled-paid
completion retains Order CANCELLED and Payment PAID. Both record immutable SUCCEEDED
refund evidence. The SELECT proposal is the exact executed operation-specific query body;
it is not a migration or an automatic change to QD's runner.

The reference adapter depends on PR #69's existing implementation. This packet does not
copy that runner, install another seed implementation, or change QD's branch. It is a
review reference, not a standalone installer. QD should adopt only reviewed fixture/assertion
changes and retain its normal Git HEAD/clean-checkout guard. The export identity override
was only for the commit-free disposable experiment.

## Tested source versus publication source

Tested combined source is Git tree bd0644ba373c5273a09e8af4898300dc4bdd8680, with frozen
manifest SHA-256 7a21a0278f3be9a214423c59a463f3e3fa66f131946c0bbec0728feda45d7662.
It combined exact PR #68/#69/#70 sources plus the local adapter; it was never a merged commit.
Fresh local results were 690 unit +1388 PostgreSQL =2078 PASS,33 Node PASS, both refund
operations PASS and checkpoint-backed rerun PASS, with zero final failures/errors/skips/timeouts.
Historical draft errors, the original PR69 assertion FAIL, final corrected results and raw
evidence checksums remain distinct in the report/JSON.

This review branch starts from develop fa48cb69cef92276f0dddf0d2bd396eee098f2f3 and changes
only this documentation directory. It omits the production/test/migration/runner changes
from the three unmerged PRs. Therefore its HEAD/tree differs from the tested combined tree.
It publishes sanitized descriptions, provenance and a nonexecuting reference proposal.
Exact publication HEAD and CI run are recorded in the Draft PR. Existing2078 local results
are not exact-head CI evidence for this documentation/proposal publication.

Raw logs/XML, SMTP messages, private HTTP bodies, passwords, cookies and tokens remain local.
Original documents are preserved; the manifest records their original hashes and the hashes
of sanitized published files. Profile email/avatar/name metadata is removed from copied
review records; public owner handles remain only for review provenance. Git normalization
is disabled in this directory to preserve exact artifact checksums.

## Requested review and remaining gates

QD (datcaq): review V20261028081110 in PR #70, conditional evidence CHECKs, nullable
ReturnRequest semantics, retained unique/composite-FK/append-only constraints, historical
returned-COD preservation and adoption of the operation-specific PR69 correction.

HP (hphuc032): review combined evidence and initial ADMIN/synthetic PAID fixture/bootstrap
policy, checkpoint-backed rerun scope and remaining acceptance gates. No VNPAY callback or
real transfer verification is claimed; VNPAY Sandbox remains PAY-03 DEFERRED.

Owner adoption, scoped reviews, authorized integration, fresh latest-develop PostgreSQL/Node
regression and HP final acceptance remain required. The original blanket all-refunds-REFUNDED
assertion is still an upstream integration blocker; the locally passing proposal does not close it.
