# FR-10 follow-up — exact develop source 4d57f30

This is an additive follow-up to PR #77. All original files at publication
`2b7d4eb780f4d48f5684a1a999a9d92c4e82f203` remain immutable, including the original
artifact manifest, report, screenshots and historical 54 PASS / 12 FAIL result.
The current disposition is recorded in `report-addendum.md` after execution, not
by rewriting that historical result.

## Runner v2

The Python runner now requires `--expected-source <full-40-character-SHA>` and
`--primary-root <repository-to-preserve>`. `--root` must be a separate clean
checkout at the exact expected source. Worktree index paths use `git rev-parse
--git-path index`, so the primary repository can itself be a linked worktree.
The output directory must not exist. Old execution directories are never reused.
Migration versions must equal the checked-out source inventory, rather than a
hard-coded count. Runtime ENV inherits only OS necessities and supplies fresh
local test secrets. Removal requires the exact owned container ID and task label;
Java exit, primary HEAD/index/status and source cleanliness are recorded.

`runner/catalog-browser.cjs` is byte-identical to the original final browser
runner. All 22 groups x 3 viewports, including 12 populated-empty-state assertions,
are retained. No production fix or test assertion is loosened by this packet.

Example (replace tool/output paths; no npm install is performed):

```powershell
python <PACKET>/runner/run_catalog_acceptance_v2.py --root <CLEAN-DETACHED-CHECKOUT> --expected-source 4d57f30d7309a6d297b945b48e2d8b5a97a07cb7 --primary-root <PRIMARY-REPO> --output <NEW-OUTPUT-DIRECTORY> --java <JDK21>/bin/java.exe --node <NODE>/node.exe --playwright <EXISTING-PLAYWRIGHT-MODULE> --chrome <EXISTING-CHROME>/chrome.exe --browser-runner <PACKET>/runner/catalog-browser.cjs
```

Docker Desktop must be running. PostgreSQL is disposable and bound to loopback.
Mail delivery is out of Catalog scope; only the isolated process mail health probe
is disabled. CSRF, route authorization and database constraints are unchanged.
The real existing demo Catalog seeder and disabled synthetic owners are used.
No Render database or PR #69/#71 execution is involved.

Automated browser execution is not human acceptance. The versioned human
checklist remains unsigned until HP/TD actually perform and record their review.
