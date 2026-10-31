# QA-03 current post-merge technical acceptance packet

Original Master Plan SHA25618523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a was re-read. **QA03 technical evidence is ready for TD/HP review; whole QA03 remains BLOCKED, not COMPLETED.**

## Source and synchronization

Accepted develop/#70 merge:6a26370659a2fa7b95c6a1d00d2598820de6bb34, including #68a34e8cc and #74UI02. QA03 normal merge12f3530 had no conflict; backup ref retained. Final executed source **89d676221c3e2a2303a09536adb14ec3aa65033b**, tree11929f0afac30d8ad998cb97f0287902557059d2. Its additional housekeeping ignores generated Python bytecode. Production backend, SecurityConfig, financial/refund guards and migrations are identical to accepted develop. Four remaining QA-specific code files are seed/diagnostic tooling and test-only fixtures. Historical integration commits/docs remain in the ancestry/diff; no destructive rewrite, older production replay or duplicate SQL version. **28 versioned SQL migrations**; seed history counter29 includes1SCHEMA marker plus28SQL (read-only observation on controlled DB; exact observation timestamp not retained).

## Fresh G02 execution

Full Java21/PostgreSQL17.6 clean verify: **709unit +1415PostgreSQL =2124 PASS**,0failures/errors/skips/flakes, Maven/runner exit0/no timeout. All144XML hashes, summary and Maven/Node logs rechecked. Separate complete Node40 and tooling23 PASS. The runner's notification Node12 is a subset of40 and is not added. CI package-time reruns are not counted. Commands/UTC times/durations/exits/version data in receipts; raw private logs/XML remain local, not publicly published.

Normal **run-95b528ce406c** and controlled Clock−2s **run-3138bd73d978** each used a fresh owned DB and passed, exit0/no timeout/owned container removed. Each executed two independent TD child processes, validated15Order fixtures, operation-specific refund semantics, old returned ledger, inventory/voucher release, financial readback and no rerun writes. All34table counts and aggregate row fingerprints match before/after **within each run**. Across separate runs fingerprints are not expected equal because timestamps differ. The shared module checkpoint is same-process validate-and-preserve; only TD fixture reruns are independent processes. Lost-checkpoint recovery is NOT RUN.

Controlled Clock marker is present in both TD children only, absent in the shared app; production JAR excludes fixture/Clock classes. Eligible cancelled-paid rows have valid amount,onePAID,not-expired and paid chronology. Returned-COD refunded Order remainsREFUNDED; cancelled-paid refunded Order remainsCANCELLED, PaymentPAID preserved. Historical Q70-S01 remains OPEN/Medium, disposition ACCEPTED RISK—LIMITED; original predicate is not declared resolved and arbitrary clock-skew correctness is not claimed.

Financial complete metrics in each combined run: deliveredSales720000, merchandise refunds550000, netCommission25200. CollectedPayments/netCollectedRevenue/netRevenueAfterCommission deliberately remain unavailable/null with unknownEvents1 because of the PAID NEW ONLINE negative fixture; this is expected fail-closed behavior, not silently omitted evidence or invented zero. No live gateway/payment settlement is claimed.

## G01 is a separate candidate

PR75 current packet at3ae2c82 tests executable sourcedb3c876, normal-merged accepted develop6a26370. **No PR75 production changes are included in this QA03 source.** Current candidate:709unit,188PostgreSQL/8targeted classes,Node48,tooling17 PASS; not a full candidate PostgreSQL suite.24main+12fresh supplementary real Chrome155 cases PASS at375/768/1440. Another local upload-restart check passed with stable media bytes and3width renders. Original screenshots/actor/routes/expected+actual/source/JAR/dataset/time/hash records and retained failed harness attempts are on75. Tool-driven keyboard is automated.

**G01 AUTOMATED PASS / HUMAN ACCEPTANCE PENDING.** No human clicks/signatures/physical device/IME/screen-reader or full human visual audit is fabricated. Genuine-human checklist included. HP must review candidate and applicable observation scope; OTPcopy10minutes/server5minutes remains HP-owned Low.

## Failed attempts retained

The first post-sync full attempt on12f3530 ran2124Java cases without failures but wrapper exit1 because concurrent Python tooling created untracked bytecode. Its receipt/triage/raw reports are preserved; it is not relabeled runnerPASS.89d6762 added only Python generated-cache ignores, retaining tracked/untracked-source guards, then reran the entire full runner successfully. Candidate's omitted-postgres-profile invocation and browser Bootstrap-transition/stock-exhaustion harness attempts are also preserved in75, separately from corrected runs. No production guard/assertion weakening.

## Remaining acceptance gates

See acceptance-status.json for owner/evidence/blocking/action per item. TD must review current source, refund/financial invariants and candidate compatibility. HP must explicitly accept disposable synthetic initialADMIN/PAID prerequisites and checkpoint scope, decide G01/human/candidate acceptance and PROMO03 platform-management scope. Manager commission writes remainDENYALL under accepted ADMIN06 exception. Target-host/TLS/realSMTP/offhost DB+uploads restore/production observability are separate NOTRUN release gates. No silent waiver or deferral is created. #69/#75 remain Draft, no approval/merge/QA completion by this work.


## CI scope

Exact PR75 publication3ae2c82 CI40842071379 SUCCESS; private workflow log was checked for actualNode48 (including the8assignment tests). Raw CI ZIP stays private; receipt/hash is public. Main/evidence CI for QA03 publication must be checked on its final HEAD; the inherited PR70-only PostgreSQL job is conditionally SKIPPED on69/75, not PostgreSQL evidence. Fresh full PostgreSQL results above are local source-bound verification, not a GitHub PostgreSQL run.
