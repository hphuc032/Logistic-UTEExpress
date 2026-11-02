# Scoped acceptance matrix

| Criterion | Result | Evidence |
|---|---|---|
| Exact #70 migration/old ledger/FK/immutability | PASS scoped independent | 690unit +205PG, exact688884c; independent manifest |
| Existing migration integrity | PASS | All27 old Git objects unchanged; only newV20261028081110 |
| Refund key/ADMIN/concurrency/rollback | PASS scoped independent | 15blocking observations,8PG classes |
| Operation-specific combined states | PASS observed | returned -> REFUNDED, cancelled-paid -> CANCELLED;3refunds |
| Original returned ledger preservation | PASS observed | Before/after row digest equal; upgrade independently tested |
| Combined rerun | PASS observed / OPEN Medium reliability finding | Full34-table fingerprint equality, separate TD invocations; first attempt FAIL retained |
| Full integrated candidate regression | PASS source1d8f923 | 2078Java/PostgreSQL,33Node,17tooling; source-bound manifests |
| Exact-head CI after packet publication | TRACKED ON GITHUB | Do not substitute local PostgreSQL for GitHub CI or relabel previous SHA |
| HP bootstrap/checkpoint disposition | PENDING | Q69-D01/D02 |
| Browser/manual375/768/1440 | NOT RUN/BLOCKED | Q03-G01; no screenshots |
| Post-merge latest-develop verification | NOT RUN | Dependencies unmerged; no fabricated post-merge run |
| WholeQA02/QA03 acceptance | IN PROGRESS/BLOCKED | TD review + HP final decisions required |
