# PR #68 — historical and new full regression

These are two separate executions. No counts or exit codes are combined, and the
previously published historical manifest is unchanged.

| Field | Historical full run | New full run |
|---|---|---|
| Tested source SHA | `8bab8dd9358a9f6fef022ec2c4e489e2f0c26fb5` | `c593352492184da2a9ca46ebea4a55b802ac6416` |
| Git tree | `70e4a19de2473a747b4d0d1f554f78b6c6cada99` | `1d545a3350963edf8ca46d5cae7284ce018467cb` |
| Handoff run ID | `historical-integrated-full-8bab8dd-20261028T102849Z-manifest` | `pr68-recheck-full-20261029T034451Z` |
| Time, UTC | Manifest collection `2026-10-28T10:28:49.493245+00:00`; original process start/end not recorded in accessible artifacts | Maven start `2026-10-29T03:44:51.535402+00:00`; process end `2026-10-29T03:52:32.388756+00:00` |
| Java unit | 686, 73 suites | 697, 74 suites |
| PostgreSQL integration | 1,325, 63 suites | 1,326, 63 suites |
| Total | **2,011** | **2,023** |
| Maven command | `mvnw.cmd -q -Ppostgres-it clean verify`, reconstructed from source-bound runner | `cmd /d /c mvnw.cmd -B --no-transfer-progress -o -Ppostgres-it clean verify`, exact subprocess record |
| Maven exit / timeout | 0 / false, recorded in published historical manifest | 0 / false, actual `Popen.wait()` result plus checked raw log and XML |
| Node | Historical runner 12 PASS, exit 0; separate Node33 result is supplemental and overlapping | Existing same-source local Node33 PASS, exit 0; GitHub CI independently Node33 PASS |
| Manifest | [Original integrated manifest](../qa03-form-binding/integrated-full-run-manifest.json) | [Gate evidence](../QA-02-PR68-gate-evidence.json), [new provenance](provenance.json) |
| Raw Maven log hash | `e06e1c848797ea80523a83eba2ad90cca19c6b663bc741a9d641ed4cb4f7ef5a` | `b9a8b718e1580b121e93463973771aabd5f0eb8a145907a9bf5112c820e7b0e1` |
| Raw artifact availability now | Historical raw log/XML not located in accessible workspaces; hashes and results retained from original published manifest, not independently rehashed now | `.work/pr68-recheck-20261029/full.json`, `full-1.log`, `full-surefire-reports/`, `full-failsafe-reports/`; hashes and counts independently verified |

Run IDs above are stable labels assigned for this handoff from the original source
and timestamps; neither original runner emitted a separate run UUID. Historical
collection time is not presented as a process start or finish timestamp. Historical
raw artifacts were sought in the QA-02 workspaces; no raw rerun or substitute result
was generated. The historical command is reconstructed from the immutable runner's
`commands()` implementation, not claimed as a recovered command transcript.

## Verified delta

| Suite | Historical count | New count | Verified source change |
|---|---:|---:|---|
| `VendorProductPromotionBindingTest` | absent, 0 | 11 | New MVC regression class: normal transport headers, form-versus-header version, header-only missing version, four forged fields, three invalid versions, foreign owned lookup denial |
| `PromotionBrowserFormsIT` | 5 | 6 | Added `realPromotionDisableAcceptsHeadersAndRetainsOwnershipCsrfAndFieldValidation` |
| Every other suite | unchanged | unchanged | Counts compared across all manifest rows; no suite or test removed by the source diff |

The new real HTTP test covers foreign 404, USER 403, missing CSRF 403, header-only
version 400, extra business field 400, unchanged active state after denial, valid
deactivation preserving Product/discount, and stale replay 409. The 11 new unit
cases plus one integration case explain **the entire +12 difference**.
[Machine-readable comparison](run-comparison.json) lists all 12 testcase entries
extracted from the new raw XML and the exact changed source paths.

`git diff 8bab8dd..c593352 -- src scripts pom.xml .mvn` changes only
VendorProductPromotionController, the new MVC test, and PromotionBrowserFormsIT.
The intervening `e79b760` commit adds documentation/evidence only. Historical
application/test/POM identities are not relabelled as the new source.

## Provenance and encoding

New-source proof is the combined runner Git guard, clean before/after snapshots,
actual Git tree, actual process exit, Maven clean/compile log, 137 XML report hashes,
and **763 recorded input files matched to immutable Git blobs at c593352**.
There are 655 exact byte matches and 108 matches requiring only documented CRLF/LF
checkout conversion. XML `basedir`/`user.dir` and Maven artifact coordinates support
the record but do not independently encode a Git SHA.

The historical manifest records runner SHA `8bab8dd` and working-file SHA-256
`4005a275ed21d1d657f9f8c5cb0ad663f83be08074fe66cb10926d494a21036f`.
That matches the immutable runner after Windows CRLF checkout conversion; the Git
LF blob hash is `894884feb5cdb3cafa45088880cb0b0f7827706c7c2247ef7e140d83bb583dc8`.
This encoding difference was checked explicitly rather than treated as a source change.

Historical PASS/exit0 is therefore a verified published record with a clear raw
access limit. The new **2,023 PASS belongs exactly to c593352**, with raw artifacts
and independent Git/input verification available locally. A new full run is not
required to establish the new result's source identity.

Only sanitized counters, testcase names, commands, object IDs and checksums are
included in this packet. Raw Maven logs, XML properties, cookies, credentials and
environment dumps are not published. QA-02 remains IN PROGRESS; HP final acceptance
and merge remain PENDING.
