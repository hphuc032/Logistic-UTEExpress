# INT-02 evidence manifest

Only sanitized references/counts/digests are published here. Raw logs and XML can contain test credentials, request cookies, synthetic proof references, system properties and restricted media metadata; keep them local/ignored and inspect before sharing. No token, credential, private proof or DB dump is committed.

## Frozen identities

- Repository: https://github.com/hphuc032/Logistic-UTEExpress
- Branch: `feature/int-02-integration-verification` (local only).
- Baseline approved develop: `5b5164dd29ba5465ffefe19d431da7b70e41357a`.
- Final executable/test commit: `9d296d7e5a3e4bc26da76c4b20c7a8d003c8698a`.
- The documentation commit follows the tested source and changes only `docs/testing/INT-02-*.md`. It is not claimed to have been separately Maven-tested; executable inputs are unchanged.
- Master Plan path/hash: `D:/JavaWeb/Project/UTEExpress_Master_Project_Plan.docx`; SHA-256 `18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`. No binary copy is committed.
- External QA-03 tooling frozen commit: `e3a153e6b7f5efcdda67682cf503992c079d742f`, PR #61 Draft/unmerged. Exact `scripts/qa` and doc subtree archived to ignored `.work/qa03-tooling/source` without merging/copying into production.
- Runner SHA-256: `5ce1f478ef4b1dcfd62d73f924482d5203231bfe5b67c11d1a7c9f8fab845d8b`.
- Pending #59 head `c958510c3b05ad761400c5941198ad9c5a91ff7b`; #60 head `1e608133708e322e5ab741cf50803381cf63bc49`. Neither tested as integrated dashboard functionality.

## Reproducible commands (PowerShell, Java 21)

```powershell
git rev-parse HEAD
git status --porcelain=v1
java -version
.\mvnw.cmd -v
node --version
docker version --format '{{.Server.Version}} {{.Server.Os}}'
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it clean verify
node --test --test-reporter=tap src/test/js/notifications.test.cjs
# From isolated QA tooling frozen at the SHA above:
python -B .work/qa03-tooling/source/scripts/qa/verify_operations.py --checkout D:\UTEExpress
git diff --check
git status --porcelain=v1
```

Runner actual child commands: absolute `mvnw.cmd -q -Ppostgres-it clean verify`, then `node --test --test-reporter=tap src/test/js/notifications.test.cjs`. The runner checks Java/Node/Docker, clean Git/source SHA before and after, required suites, exact XML case counts and skips/errors/flakes/reruns/summary timeout; a failed run removes stale PASS JSON. It is supplementary evidence collection, not acceptance by itself.

Own tool tests: `python -B -m unittest discover -s .work/qa03-tooling/source/scripts/qa/tests -v`: 15 PASS, exit 0, retained `.work/qa03-tooling/tool-tests.log`. These fixture tests are not included in application totals.

## Actual evidence inventory

All paths below are relative to `D:/UTEExpress`; none of the raw artifacts are staged.

| Artifact | Source / meaning |
|---|---|
| `.work/int02-baseline/source-head.txt` | Frozen baseline SHA |
| `.work/int02-baseline/maven.log` | Baseline full command, exit 0, BUILD SUCCESS; 652+1,141 tests; 13:01 min |
| `.work/int02-baseline/node.log` | Baseline 12 client tests, exit 0 |
| `.work/int02-baseline/baseline-evidence.json` | Sanitized per-suite counts and report hashes from this actual baseline |
| `.work/int02-baseline/reports/{surefire-reports,failsafe-reports}` | Preserved baseline raw XML before subsequent target clean |
| `.work/int02-baseline/focused-attempt1.{log,xml}` | Failed new-test BYTEA assertion; 2 failures; resolved F06 |
| `.work/int02-baseline/focused.log` | Corrected focused run; 7 architecture + 2 integrated scenarios PASS, exit 0 |
| `target/surefire-reports/TEST-*.xml` | Final clean-run unit/architecture evidence |
| `target/failsafe-reports/TEST-*.xml`, `failsafe-summary.xml` | Final PostgreSQL tests/timeout/flakes |
| `target/qa-03/operations-evidence.json` | Final runner exact tested_revision, timestamps, per-suite SHA-256 and counts |
| `target/qa-03/{maven,node}.log` | Private raw final command logs |
| `.work/int02-baseline/final-runner.log` | Runner exit and sanitized final console summary |
| `.work/int02-baseline/shipper-rate-proof.log` | Actual compiled DTO: 66.7 vs Plan 40.0, probe exit 0 |

Final machine evidence summary: PASS: 652 Surefire / 70 classes + 1,143 PostgreSQL / 55 classes = 1,795 Java; 12 Node PASS; Maven/Node/runner exit 0; 0 failures, 0 errors, 0 skips, 0 flaky/rerun XML markers; timeout=false.
Evidence collection UTC timestamp: 2026-10-25T16:35:48.930558+00:00.
SHA-256 at collection:

| Safe artifact reference | Digest |
|---|---|
| `target/qa-03/operations-evidence.json` | `c1cddd3025c72ac43c1e8444a965b08dacaa8a85d3f8b52590fb4cc5b640e2bb` |
| `target/failsafe-reports/failsafe-summary.xml` | `1e20bb9a980f23da019be13490bef3808af5d9e283d8be130de2b02908c81cdd` |
| `target/qa-03/maven.log` | `cabe95bac65eeeee836aec2c97f06550ccf9e9feeb26a7ce7bec0a919d35ecd8` |
| `target/qa-03/node.log` | `415a94c1df3d8401a67468d4be2ebd8c2eb14900232d98bbc790bb98df36fd5a` |
| `.work/int02-baseline/baseline-evidence.json` | `4e991922ff25810312850d5769fbff0cfbe6bc380531329e9c33c24db6daa124` |
| `.work/int02-baseline/maven.log` | `9480976e392c99089010809c316d2da5a66089dd6cbd715a89081b6507e75696` |
| `.work/int02-baseline/focused.log` | `53947ceef1af7ca8266676aa67127a69d8a96fe7a4c3bbb1a89d6d49c981483d` |
| `.work/qa03-tooling/tool-tests.log` | `47fdb4174d1ed90e1905f3b9cb5e86c44f0997cef25629fcf8dc17e9f526157a` |

No overlapping run counts are summed. Final zero flaky XML markers means no recorded flakes/reruns in this execution, not proof tests can never be flaky.

### Formula reproduction without private data

```java
import com.uteexpress.shipping.dto.ShipperDashboard;
public class ShipperRateProbe {
    public static void main(String[] args) {
        var dashboard = new ShipperDashboard(6, 0, 0, 0, 2, 1, 3, 0);
        System.out.println(dashboard.successRate()); // actual 66.7
        System.out.println(java.math.BigDecimal.valueOf(200)
            .divide(java.math.BigDecimal.valueOf(5), 1, java.math.RoundingMode.HALF_UP)); // Plan 40.0
    }
}
```

Save under ignored `.work/int02-baseline/ShipperRateProbe.java`, then run `java --class-path D:\UTEExpress\target\classes .work/int02-baseline/ShipperRateProbe.java`. The unsuccessful initial JShell classloader probe is not counted as successful formula evidence.

## Frozen executable input identities

Git object IDs at the tested commit:

- `src/main`: `692de622116f49fddd101d3fb031e00225c57a61`
- `src/test`: `40a1ce39f577fd837fcfa0a56c1861fd5299b550`
- `pom.xml`: `89ba1f2c4fdbd5aafc0e9dfe909034b52990452f`
- `.github/workflows/ci.yml`: `97dc4d2f0d66997d78aaeb967c62f1c58c4a91b8`

Remote readback near final verification still shows develop `5b5164dd29ba5465ffefe19d431da7b70e41357a` and main `4c2dc14f8f9a1df4441283b9dd870a232359d1fb`. #59/#60 remain OPEN/unmerged and #61 OPEN/Draft at the recorded heads.

## Evidence boundary / handoff

Source starts and ends clean at final runner SHA; later doc-only identity is disclosed above. Use `git diff 9d296d7..HEAD --name-only` to verify documentation-only change; run exact-source testing again after any production/test/dependency/config/CI change or pending dashboard integration. Build outputs remain ignored; no environment dumps or secrets are published. No new GitHub CI, deployment, multi-instance/socket load or manual browser acceptance was executed.
