# Findings FR-10 Catalog

## FR10-CAT-01 — Medium — OPEN — contradictory empty state with records

Owner: **HP (Catalog/shared UI)**. Reviewer: **TD** theo Master Plan. Không sửa production trong acceptance task này.

Source `a5620f1`, JAR/source proof và fresh dataset của `empty-state-3`. Reproduced4 routes×3viewports=12 real browser FAIL:

| Route | Visible populated cards | Incorrect message | Evidence example |
|---|---:|---|---|
| `/` |12 products|Chưa có sản phẩm|[1440px](attempts/empty-state-3/screenshots/CAT-POPULATED-HOME-1440.png)|
| `/categories/demo-category` |12 products|Chưa có sản phẩm|[768px](attempts/empty-state-3/screenshots/CAT-POPULATED-CATEGORY-768.png)|
| `/shops` |3 approved shops|Chưa có cửa hàng|[375px](attempts/empty-state-3/screenshots/CAT-POPULATED-SHOPS-375.png)|
| `/shops/demo-shop` |7 products|Chưa có sản phẩm|[1440px](attempts/empty-state-3/screenshots/CAT-POPULATED-SHOP-1440.png)|

Repro: start fresh Catalog fixture; open each route at375/768/1440; count visible cards; observe empty-state heading above real cards. Expected: empty state only when relevant collection is empty. Actual: both branches visible, misleading browsing status. No access-control/data corruption finding inferred.

Source clues: `src/main/resources/templates/index.html` emptyState caller, `categories/detail.html:12`, `shops/list.html:11`, `shops/detail.html:17` put `th:if` and `th:replace` on the same element; fragment root in `fragments/components.html` is unconditional. Probable cause is fragment-replacement/conditional processing order, to be confirmed by owner. A narrow owner-reviewed correction could place condition on an outer block; this is a proposal, not an applied patch.

Closure: fix in separately authorized source; reproduce populated AND truly-empty variants at all3widths, preserve public visibility/media/security; human read-back after correction. No closure/risk acceptance is recorded.

## FR10-HAR-01 — Informational — harness synchronization corrected

Baseline54 preserved52PASS/2FAIL: menu closing expected `aria-expanded=false` at375/768 but gottrue. Source and UI unchanged. Recheck records `navbar-collapse collapsing` when a menu link first becomes visible; this is earlier than the end of Bootstrap opening transition. Initial driver clicked close too early.

Targeted fresh recheck waits for `#primaryNavbar.collapse.show`, then closes and waits for hidden state, without fixed sleep or weakened assertions.3/3 PASS. Original two failures and driver versions remain in `attempts/baseline-1` and `prior-runners/attempt-1`. This resolves test synchronization only; human focus/fast-touch usability is still unsigned. These are not two production findings hidden as PASS.

## Documentation limitations

- QA-02 FR readiness packet not found in inspected refs; HP explicitly chose Master Plan Catalog scope. No invented release-gate definition.
- Historical G01/FR06 metadata was read only; no raw transfer verification, FR07 execution or QA03 completion claimed.
- Agent sampled screenshot contact sheets; this is not HP manual visual acceptance or full real-device/screenreader testing.


## Provenance precision

Baseline run1 captured accepted source/tree/JAR and clean-worktree guards before execution; its driver archive was copied after that run, not a contemporaneous recorded launch hash. Recheck2 and diagnostic3 record driver/environment-runner hashes at launch. Initial build-only start timestamp/raw log was not separately captured; terminal output records Maven duration/finish/exit. These limits are not filled with inferred timestamps or retroactive hash claims.
