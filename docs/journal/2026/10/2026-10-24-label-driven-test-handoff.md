# Restore label-driven test selection

Date: 2026-10-24 (Asia/Tokyo)
Status: focused implementation delivered; isolated installed qualification accepted
Recipients: sm-workflow開発 and textus-control-center

## Decision and concrete case

The user confirmed that tests intended for execution must use metadata labels.
DoNotDiscover is reserved for intentional exclusion, not FULL/HEAVY classification
or a substitute for runtime/CAR preparation. The concrete trigger was a request
to add NativeGoalDependencyValidationCliSpec to build.sbt's class-name Set so an
annotated suite could be explicitly selected. That one-line change follows the
old workaround but does not satisfy the adopted label-driven architecture.

Read the [specification](../../../spec/label-driven-test-selection.md) and
[LTEST-1–4 plan/checklist](../../../notes/label-driven-test-migration-plan.md).
Canonical CNCF metadata owns purpose/feature/requirements. sm-workflow discovers,
selects, prepares and executes; no second membership registry is required.

## Producer instructions

Start with current suite/default-runner inventory and one actual CLI test route.
Make explicit selection execute without a per-class build.sbt registration.
Connect required artifact/runtime preparation through existing metadata and typed
facilities. Before removing runnable DoNotDiscover annotations, align default
SBT test and FULL selection so previously hidden HEAVY/external-service tests do
not suddenly execute. Do not simply delete annotations or include every suite.

FULL excludes HEAVY and external servers, including Docker MySQL/PostgreSQL
lifecycle dependencies. SQLite and small test-owned same-JVM loopback servers
remain eligible. Multiple purposes are supported by the ABI, but contradictory
FULL/HEAVY or external requirements must not silently bypass this boundary.
Artifact dependency alone does not establish HEAVY; classify actual behavior/cost.

Migrate remaining runnable suites, retire manual Set/TestDefinition enrollment
and update current authoring instructions. Preserve intentional disabled/helper
exclusions with their reasons and retain historical validation descriptions.
Use focused positive/negative selection tests and one actual isolated connected
specification; do not run external services merely to prove their exclusion.
Identify any actual CNCF adapter gap through the existing scope mechanism rather
than inventing new execution capabilities or bypasses.

## Consumer and preservation

Preserve current dependency-validation implementation, any proposed/applied one-line
enrollment, accepted source scope and current Session/Plan/history/counts/results.
Do not revert active work wholesale or use this planning document as authority
to modify unadmitted producer paths. Coordinate ownership with the producer;
the final delivered route removes the need for future manual class enrollment.

Do not restart, replay accepted Submit, rewrite historical evidence or rerun
successful checks solely to adopt the selection change. Previously passed tests
retain their actual coverage; they do not retroactively prove a larger FULL set.
Keep live execution on its retained selection and adopt through supported public
continuation. Share actual delivery/adoption evidence between the two chats as
explicitly authorized by the user.

The original planning entry above changed documentation only. The subsequent
explicit implementation request is adopted below; its history is retained.


## Delivered producer route and consumer adoption

The producer now uses canonical annotations on runnable suites and the existing
CNCF discovery/composition/binding APIs. The standard SBT `testOnly` entry derives
operations and runtime preparation from those declarations; `runtimeTestOnly`
is an alias. The manual class-name enrollment Set is removed. Default `test`
and native FULL share the exclusion policy. Intentional fixture-only exclusions
retain their reasons; empty selection and contradictory FULL metadata refuse.

Author runnable tests with a canonical `ValidationSpecification` declaration.
Use existing purpose/feature/execution-requirement vocabulary and classify actual
behavior. Do not edit a second suite list. Local SQLite or same-JVM loopback use
alone does not imply an external service. Repeated native/SBT processes and
separately started or required services remain outside routine FULL. Keep
specification/operation trace references in comments when cumulative human test
names would exceed the canonical operation bound; do not weaken that bound.

Select the actual normal SBT executable for the representative dependency CLI
fixture with `smworkflow.spec.sbt` or `SM_WORKFLOW_SPEC_SBT`. Metadata-selected
runtime preparation uses the existing build tasks. Actual workflow validation
continues through `sbt_*` and the UoW-owned adapter; this migration does not move
live Phase checks to the caller AI. Runtime/config/artifact selection still uses
the existing host contracts.

The connected correction also reconstructs the same fixed primary-plus-captured-
dependency check set at Validation and Review. Primary terminal-LF completion
cannot borrow Compile/Test from a dependency, and captured dependencies retain
ordinary independent Review because original completion evidence has no original
dependency-candidate snapshot. This adds no public completion bypass.

Adopt the source/build change at the next supported consumer boundary. Keep the
existing Session, accepted source scope, Plan/base/generation, repair counters,
issued work and actual execution history. Do not re-Start or replay accepted
Submit to obtain the new selection behavior. Existing commands finish under
their retained selection. The qualified runtime installation is task-private;
normal live-host promotion is a separate owner action, not implied by publication.

The Control Center annotations are source work only until its owner compiles,
discovers and runs them. Producer qualification does not accept those consumer
results, the pending caller-input transport work, or all other dirty sources.
No CNCF source change was required by this label migration.

Acceptance status is maintained in the [LTEST checklist](../../../notes/label-driven-test-migration-plan.md).
Detailed actual logs, failed attempts, independent reviews and installation
qualification are retained outside Git at:
`macmini:~/backup/journal/2026/10/sm-workflow/label-tests-20261024/a-4769766c-28bb-4b7e-b86b-24e93bef8359/`.
Retention: 2026-11-23; active reference/adoption hold has no expiry, next review
2026-10-31. No full-suite or all-HEAVY execution is claimed by this acceptance.
