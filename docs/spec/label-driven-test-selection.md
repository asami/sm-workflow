# Label-driven test discovery, selection and preparation

Date: 2026-10-24 (Asia/Tokyo)
Status: implemented and accepted within the focused LTEST boundary
Plan: [LTEST delivery](../notes/label-driven-test-migration-plan.md)

## Canonical classification

All tests intended for supported execution use CNCF Validation/Test Metadata:
purpose, feature, expected duration and execution requirements. Reuse CNCF
discovery/composition/binding and existing sm-workflow selection; do not create a
second tag vocabulary or manually maintained suite membership registry.

Discovery, selection and preparation are distinct:

    discover operations -> resolve metadata -> select requested purpose/features
      -> bind declared execution requirements -> prepare -> execute -> retain results

Discoverable does not mean selected by FULL. Explicitly selectable does not mean
authorized for arbitrary effects. Class/spec defaults and operation-level metadata
retain the producer's merge semantics. Preserve actual logical operation identity
and result attribution while changing selection plumbing.

## DoNotDiscover and manual registration

Do not use `@DoNotDiscover` to classify CLI, slow, artifact-dependent, optional or
explicit-only runnable specifications. Remove it from those specifications as
their metadata selection/preparation route becomes operational. Eliminate the
`build.sbt` class-name Set and per-suite TestDefinition rewriting used to re-enroll
such suites for testOnly. Adding an ordinary labelled test must require no second
class-name registration.

Retain `@DoNotDiscover` only for intentional exclusion from normal independent
execution, with an explicit reason: for example a fixture-only helper suite or an
intentionally disabled test. Inventory the actual rationale; do not convert every
annotation mechanically. An intentionally disabled test must not be silently
re-enabled by a wildcard or routine metadata query. Runnable explicit-only tests
use selection policy/labels rather than hiding from discovery.

Explicit per-run operation/class selection remains useful for diagnosis and
focused validation. It is not a persistent second catalog. A selected runnable
operation must resolve and execute through the supported route, including its
preparation, without a build.sbt enrollment edit. Missing selection must remain
visible; an empty run is not successful execution of the requested test.

Historical documentation about individually enrolled DoNotDiscover suites remains
historical evidence, not the target authoring contract. A one-line enrollment can
be retained as existing work while migration is incomplete, but is not migration
acceptance and does not become the permanent solution.

## ADMISSION operating target

ADMISSION targets **at most one minute of aggregate test-body execution time per
selected run**, excluding compilation, dependency resolution, generation,
discovery, runtime startup, fixture setup/teardown and result retention. Sum
the selected bodies even when execution is parallel; this is not a one-minute
allowance per test or suite. It is an engineering guideline, not a fixed timeout,
test assertion, automatic cancellation or automatic reclassification rule.
An over-target successful run remains successful; reconsider selection/cost with
its actual coverage retained. Independently configured execution deadlines keep
their existing role and are not set to 60 seconds by this policy.

Use `testOnly --purpose ADMISSION` through the existing execution owner; inspect
with `labelledTestSelection --purpose ADMISSION`. The initial local declarations
cover GoalPhase ordered progression, RepositorySync input selection and SplitPhase
candidate allocation. `AdmissionTestDeclaration` adds ADMISSION while retaining
FOCUSED/FULL. Existing HEAVY declarations and default FULL selection are unchanged.
Add or narrow membership at the executable specification, without a second suite
registry. Operation overrides retain CNCF's replacement semantics: explicitly
retain any intended FOCUSED/FULL memberships when overriding purposes.

Record body time separately from preparation/cleanup when the execution evidence
supports that separation. Suite/run wall-clock duration is not net body time;
report net time as unavailable when it cannot be separated. A conservative
upper bound below one minute may establish the target, with that basis explicit.
Do not invent `expectedDuration` values: a declaration default is per operation,
not the aggregate selection budget. ADMISSION is quick acceptance coverage, not
a replacement for affected FOCUSED tests or required FULL/HEAVY acceptance.

## FULL boundary

For new Step/Phase plans and overnight execution, follow the
[ADMISSION/FULL/HEAVY cadence](native-host-execution.md#validation-cadence-admission-full-and-heavy).
That operational policy uses the existing selection mechanism described here.

Normal FULL selection excludes HEAVY work and external-service-dependent work.
MySQL/PostgreSQL Docker start/stop, a separately started server, a required existing
server, or an external service must not become a prerequisite of FULL. SQLite and
small loopback servers started/stopped within the test's own JVM are eligible,
subject to the ordinary FULL cost/behavior policy. Artifact/runtime preparation
alone does not automatically make a test HEAVY; classify its actual cost and effects.

Although the shared ABI permits multiple purpose memberships, an operation marked
HEAVY must not execute in ordinary FULL. Contradictory FULL/HEAVY or FULL/external
requirements are exposed as classification issues, without silently executing the
excluded work or claiming that the contradictory FULL selection was satisfied.
HEAVY runs only when explicitly selected by the owning policy/request; it remains
separate in execution evidence and is never a fallback for an empty FULL selection.

Align ordinary/default SBT test selection and sm-workflow FULL with this boundary
before removing runnable-suite exclusions. An unfiltered default `test` must not
suddenly run formerly hidden heavy or external-service fixtures. Use a shared
metadata-derived selection/adapter, not a replacement hard-coded class-name list.
For unclassified tests, classify from source/known behavior before inclusion;
do not infer FULL merely from absence of DoNotDiscover or a short class name.

## ON_DEMAND selection and low-frequency maintenance

Adopted policy: 2026-10-31. Add `ON_DEMAND` to CNCF's canonical Validation/Test
Metadata purpose vocabulary and consume it through the existing discovery,
selection and execution routes. This is the formal label for low-frequency
executable specifications; it must not be a sm-workflow-only string or a second
suite registry. The user-facing spelling `on_demand` follows the existing purpose
parser's case conventions; the canonical purpose is `ON_DEMAND`.
Implementation of the new purpose and migration remain pending until verified.

Previous use of "scripted" for these ScalaTest suites was incorrect. Reserve that
term for the actual sbt scripted mechanism. A procedure's implementation form
(ScalaTest, sbt scripted, or a standalone script) is distinct from its selection
policy; do not force non-Scala procedures into a ScalaTest catalogue.

ON_DEMAND remains discoverable and explicitly selectable. Do not use
`@DoNotDiscover` to hide these runnable suites or manually enroll class names.
Ordinary ADMISSION, FULL and HEAVY selections exclude ON_DEMAND operations, even
if inherited/default metadata would otherwise include them. Selecting HEAVY must
not implicitly opt into ON_DEMAND. Explicit ON_DEMAND selection or an explicitly
named ON_DEMAND operation in a focused selection can execute it, with the normal
execution requirements and permissions. A broad feature/FOCUSED query alone is
not explicit consent to include ON_DEMAND; record the actual selected operations.
Conflicting ordinary memberships must not silently broaden routine selection.

Apply this policy to existing low-frequency cases as well as new ones. Keep their
entry points and documented prerequisites usable for on-demand and explicitly
selected nighttime runs. Exclusion from routine completion must not turn them
into documentation-only archives. They are not mandatory for ordinary Step,
Phase, fix, commit or repository-sync completion. Active availability does not
mean continuous execution or continuous qualification against every revision.

Maintain procedures when they are exercised. Repair environment/specification
drift found during a selected run, preserve the original result and record any
repaired rerun separately. Investigate genuine product regressions as such;
do not weaken assertions to hide them. Retain previous scripts, results and
version context. Unattended runs may retain exit status, duration and evidence
references without AI; subsequent maintenance need not use an AI monitoring or
automatic repair loop. This change does not create a nighttime schedule.

Retain purpose, requirements, versions, invocation, expected result, effects and
cleanup with each procedure. Keep concise summaries and existing non-Git evidence
references. Self-update uses a dedicated environment, not live consumer Sessions
or a development runtime selection. Runtime ownership of SBT/publication remains
unchanged. Only continuing regression requirements justified by their cost belong
in HEAVY; do not move every historical scenario there. Preserve a coverage map
and reusable small fixtures/bounded contract coverage.

Inventory ordinary invocation links and remove any actual mandatory links without
inventing removals where none exist. Preserve running invocations and all Session,
history, repair-count and successful-result records. Migration acceptance uses
small selection checks: discover ON_DEMAND; exclude it from default test/FULL,
ADMISSION and HEAVY (including inherited defaults); explicitly select ON_DEMAND
and a named focused case; prepare requirements only for selected operations.
Exercise a harmless representative case to establish dispatch. Do not replay the
expensive historical catalogue or self-update merely to prove selection. Verify
producer/consumer purpose parsing and retained result attribution together.

See the [ON_DEMAND correction handoff](../journal/2026/10/2026-10-31-minimal-reusable-workflow-fixture.md#on_demand-correction--authoritative-handoff)
for the development assignment. No new registry, scheduler or execution framework
is required; the accepted incident repair remains closed.

## Runtime preparation and ownership

The SBT adapter uses `ValidationSpecification` declarations from the compiled
test artifact and CNCF discovery/selection. Ordinary `test` selects FULL;
ordinary `testOnly <suite-pattern> -- -t <name>` or `-z <substring>` selects
explicit operations. `testOnly --purpose HEAVY <suite-pattern>` requests a
canonical purpose. `runtimeTestOnly` delegates to the same standard entry.
`labelledTestSelection --purpose FULL` inspects selection without executing
selected test bodies. An explicit empty selection fails.

Exact selected names remain bound to their individual suite. Preparation is
derived after purpose and name filtering, so an excluded operation cannot add a
runtime prerequisite. The `sm-workflow.runtime` capability uses the existing
`cozyPrepareRuntime` and `Compile/packageBin` tasks. Routine FULL does not
prepare formerly hidden native CLI work. Unsupported framework options fail
instead of broadening the requested execution.

The initial dependency CLI declares FOCUSED/HEAVY and a runtime requirement.
Local specifications declare FOCUSED/FULL, NORMAL and local requirements;
external-service scenarios remain explicit HEAVY work. The migration inventory
is evidence only and is never consulted by the adapter.

For the dependency CLI fixture, select the actual normal SBT executable through
`smworkflow.spec.sbt` (or `SM_WORKFLOW_SPEC_SBT`). This is an execution-host
prerequisite, not suite registration. Its absence is a visible failure, not an
empty successful run. Installed qualification additionally supplies the actual
installed command and a private installation root; that route has no source
launcher fallback. Other prerequisites remain owned by their existing adapters.

Declare runtime/CAR and other supported prerequisites as execution requirements;
the owning runtime prepares selected prerequisites using existing typed facilities.
Reuse the existing runtime preparation tasks where applicable, but select their
use from metadata rather than a manual suite list. Keep unavailable prerequisites
distinct from test failure. No generic ProcessExec or AI-runner bypass is introduced.

CNCF owns metadata/discovery/binding vocabulary; sm-workflow owns query, execution,
results and caller projection. Identify any actual producer adapter gap and reuse
or extend that boundary with admitted scope. Do not infer new API availability or
permission for arbitrary cross-repository changes from this specification.

## Preservation and migration

First inventory current hidden/manual-list suites and their actual requirements.
Then establish label selection and default FULL exclusion in one bounded migration
unit; only afterward remove the corresponding hidden/list entries. Verify both
positive explicit selection and negative FULL isolation. Do not run HEAVY/external
services merely to prove exclusion; selection tests and harmless representative
fixtures establish that boundary.

Preserve ongoing product/test changes, existing Session/Plan/continuation, commits,
history, repair counts and all validation/review results with their original scope.
Changing labels does not retroactively turn earlier partial coverage into FULL or
invalidate unrelated passed tests. Validate the changed selection/preparation and
affected tests only. No re-Start, accepted Submit replay, private storage edits or
routine full-suite rerun solely for adoption. Current active invocations finish
under their retained selection; supported continuation adopts the new behavior.
