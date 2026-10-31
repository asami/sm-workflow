# Migrate runnable tests to metadata labels

Date: 2026-10-24 (Asia/Tokyo)
Status: LTEST-1–4 accepted within the focused migration boundary
Producer: sm-workflow development; representative consumer: textus-control-center
Specification: [label-driven selection](../spec/label-driven-test-selection.md)
Handoff: [English instructions](../journal/2026/10/2026-10-24-label-driven-test-handoff.md)

Current target: [ADMISSION and standard annotations](../spec/label-driven-test-selection.md).
LTEST-1–4 below retain their historical acceptance. Their blanket removal of
runnable DoNotDiscover annotations is superseded by the follow-up below.

## Position

Deliver a bounded correction to the existing managed-test/validation-metadata
path. Do not reopen accepted Phase 6.3 or add prerequisites to ongoing Phase
development. Prioritize the concrete NativeGoalDependencyValidationCliSpec case
and its existing CLI/runtime preparation route, then migrate remaining runnable
DoNotDiscover/manual-list suites. Preserve the active dependency-validation work.
No new numbered Phase or separate test registry is introduced.

## Sequence

### LTEST-1 — Inventory and classify

Identify DoNotDiscover sites, the build.sbt enrollment list, default SBT selection
and actual runtime/CAR/service needs. Assign existing purpose/feature/requirement
metadata. Keep explicit reasons for intentional exclusions. This inventory is
migration evidence, not a new runtime authority or permanent suite catalog.

### LTEST-2 — One connected explicit selection plus FULL isolation

Connect shared metadata discovery/selection to the actual runner and prerequisite
preparation. Select NativeGoalDependencyValidationCliSpec without a manual class
enrollment and prove actual nonempty execution with isolated fixtures. Ensure
default test/FULL excludes HEAVY and external-service requirements before removing
its annotation/list entry. Use appropriate scope validation, not a broad external
service run. Inspect CNCF/sbt adapter behavior before choosing the change owner.

### LTEST-3 — Migrate remaining runnable suites

Apply the same route to remaining supported executable specifications. Remove
redundant class-name Set/TestDefinition enrollment and obsolete authoring guidance.
Retain intentional disabled/helper exclusions with reasons. Do not keep a parallel
manual list as compatibility machinery. Preserve historical evidence references.

### LTEST-4 — Deliver and adopt

Align installed/runtime query, explicit selection and default FULL behavior; provide
actual validation and English consumer instructions. Reuse current Sessions and
accepted evidence. Consumer adoption does not require regeneration of its Plan,
duplicate test registration, replayed work or new per-class approval rituals.
Actual expansion of implementation authority still uses existing scope adoption.

## Acceptance checklist — accepted

- [x] LTEST-A01: Every migrated runnable suite is discoverable through canonical
  metadata; adding another representative labelled suite needs no manual registry.
- [x] LTEST-A02: Explicit focused selection executes the requested CLI specification
  and produces nonempty actual results with runtime/CAR prerequisites prepared.
- [x] LTEST-A03: Both ordinary/default test and FULL exclude HEAVY and external
  server/Docker-dependent work; contradictory metadata is visible, not silently run.
- [x] LTEST-A04: Explicit HEAVY selection remains possible through its policy,
  distinct from FULL; selection verification does not itself launch external services.
- [x] LTEST-A05: SQLite and eligible same-JVM loopback fixtures are not incorrectly
  excluded merely because they use a database or HTTP.
- [x] LTEST-A06: Intentional disabled/helper exclusions retain their reason and do
  not become ordinary runnable tests; zero selection is not a false PASS.
- [x] LTEST-A07: Manual class-list enrollment is removed for migrated tests; required
  preparation and unavailable prerequisites retain accurate execution dispositions.
- [x] LTEST-A08: Existing source work, Session/history/counters and validation scopes
  survive adoption; no re-Start or redundant successful checks solely for migration.

Use focused behavioral verification and one actual connected CLI test as the main
proof. Do not mark this checklist complete from source edits, metadata inspection
or historical results alone. Full/heavy obligations remain governed by actual
owning requests and affected scope.


## Acceptance boundary and remaining ownership

Acceptance covers canonical discovery, supported explicit selection/preparation,
shared default/FULL exclusion and the isolated connected dependency CLI path.
The connected result path reconstructs the same exact primary/dependency checks
at Validation and Review. Captured dependencies retain ordinary independent
review; primary completion coverage cannot borrow a dependency's Compile/Test.

This is focused migration acceptance. It does not certify execution of all FULL
or HEAVY bodies, all cases of the representative CLI, consumer source adoption,
or unrelated dirty changes. Existing owning Phase obligations remain unchanged.
No live Session, accepted Submit, Goal, Plan, history or repair counter is reset.
The installed qualification uses a private installation; normal host promotion
remains the owning workflow's responsibility.

Detailed execution/review evidence is outside Git:
`macmini:~/backup/journal/2026/10/sm-workflow/label-tests-20261024/a-4769766c-28bb-4b7e-b86b-24e93bef8359/`.
Default retention: 2026-11-23. Bound original references remain on hold for active
consumer adoption, with next retention review on 2026-10-31; no originals were
removed. Nonblocking maintenance is recorded in the
[label-selection Hygiene ledger](../journal/2026/10/2026-10-24-label-selection-hygiene-follow-up.md).

## Standard-annotation follow-up — 2026-10-31

Status: planned; no new implementation/validation claimed. Owner: sm-workflow
development. This is the bounded successor to the completed ON_DEMAND delivery,
not a reopened closed Task/Phase or a prerequisite for unrelated work.

| Order | Work | Acceptance |
| --- | --- | --- |
| TA-S1 | CNCF producer classification/query and framework adapter changes | ADMISSION only; Slow/DoNotDiscover respected; no duplicate custom labels |
| TA-S2 | One real consumer main route using small fixtures | Ordinary/ADMISSION/Slow/explicit named selection, setup after selection, attributed result |
| TA-S3 | Migrate real declarations, CML/config, compiled inventory, SBT adapter and caller guidance | Removed labels have no active aliases; assertions and existing scripts retained |
| TA-S4 | Qualify and deliver coherent producer/consumer revisions | Changed-contract checks, conditional scoped review, runtime-owned commit/publication as needed, preservation evidence |

- [ ] TA-A01: ADMISSION is the sole custom classification; FULL/focused remain
      run scopes, SMOKE cases assessed individually, no HEAVY/ON_DEMAND purpose.
- [ ] TA-A02: Normal FULL and ADMISSION exclude Slow and automatic discovery of
      DoNotDiscover; class/per-test tags and combined annotations are exercised.
- [ ] TA-A03: Explicit Slow and named focused/explicit-suite selections execute
      the requested scope without a registration list or broad-query re-enrollment.
- [ ] TA-A04: Feature/duration/requirements, logical IDs and result attribution
      survive; no excluded suite setup starts external services.
- [ ] TA-A05: Only required new checks run; no self-update/catalogue replay,
      previous evidence preserved, no Session/counter reset or implicit activation.
- [ ] TA-A06: Product-packaged and deployed skill guidance agree; the actual
      supported commands and remaining limitations accompany the delivered result.

Follow [the English handoff](../journal/2026/10/2026-10-31-admission-standard-annotations-handoff.md).
Do not block this plan on a post-return correction API; a terminal predecessor
uses normal linked new Task intake. Keep the separately pending main integration
visible and preserve both branch histories and unrelated dirty/staged work.
