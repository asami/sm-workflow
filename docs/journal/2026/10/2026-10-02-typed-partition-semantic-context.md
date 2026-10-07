# Typed partition semantic context record

Date: 2026-10-02 (Asia/Tokyo)
Status: bounded implementation and focused accumulator passed; not Step/Phase acceptance.

## Dependency and goal

Producer completion is now claim-free (750: 68/68), its local SNAPSHOT has been
refreshed (751), and the sm-workflow consumer passed 381/381 (752). Continue the
original Phase 2 toward real typed partition issue/completion. CML registration
alone does not provide restart recovery. The next necessary boundary is a
complete, hash-free semantic context record, not a callback using old semantic
Inputs/Workflow or an in-memory map of Prepared objects.

Use the single-user typed-control policy and retained parent-direct limited
recovery. No agents, no new repository/Phase, no shared-skill changes or commit.
Preserve unrelated work and original authority/review/release requirements.

## Frozen paths

All paths are under the existing sm-workflow root:

- `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseExplicitPartitionContext.scala`
- `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseExplicitPartitionContextJsonV1.scala`
- `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePartitionContextReadPolicy.scala`
- `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePartitionContextDataStorePort.scala`
- `src/main/scala/org/simplemodeling/textus/smworkflow/admission/ManagedHostRecordRead.scala`
- `src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseExplicitPartitionContextSpec.scala`
- `src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseManagedPartitionContextReadSpec.scala`
- this journal and the Phase 2 progress projection.

## Contract

Store an explicit context reference; the host-owned Start reference,
WorkflowHandle, selected instance revision, complete configuration,
Continuation identity, completion Operation, execution requirement and
presentation; and complete purpose-typed proposal/source/inventory,
boundary/validation/handoff, partition estimate/policy and planning records.
These are raw data, not decoded admission, capabilities, executable selections,
human consent or an issued WorkOrder.

At the natural preparation boundary, require independently selected complete
current facts and the canonical instance/configuration. Reconstruct through
P/B/E/W constructors once and require an actual PartitionSemanticRequired
outcome. The derived request carries only unknown items/edges. No stored
Prepared/Admitted or result selector; no legacy hash-backed reader/fallback.
Later canonical issuance must separately prove its state/action/required ABI,
host grants and atomic publication. A context row alone never does so.

Use one separately declared context collection with a minimal independent host
read policy. Binding rejects another logical store or Workflow instance/
Continuation collection aliases. Every actual read re-admits the current host
declaration and uses the existing current Operation/DataStoreSpace/common
physical provider. No pool, lease, context cache, writer, registration Operation
or public callback is added in this boundary.

Closed versioned JSON preserves raw complete data, purpose-specific revisions
and explicit optional fields. Reject unsupported schema, nested unknown/missing
members, crossed reference, malformed types and oversize UTF-8 storage data.
JSON decoding does not construct Prepared; semantic source/closure/range rules
remain at their owning constructors instead of being duplicated in the codec.

## Executable proof and limits

Prove complete round-trip and reconstruction, unchanged-reference fact drift,
unknown-only request, already-known inputs not becoming AI work, foreign owner/
instance/configuration and malformed wire refusal. Use real managed Service/CLI
call and SQLite pool shutdown/reopen for raw record recovery and grant/declaration
denial; private fixture installation is not production registration or consent.
No claim that this connects public typed partition completion yet.

Run the existing focused accumulator plus the two new suites through the same
serialized wrapper after exact edits are complete. At most three converging
same-boundary pre-review mechanical repairs. Independent acceptance, full
validation/review, production issue/completion/CLI/MCP and Phase release stay
open.

## Pre-review repair and conformance refinement

Invocation 753 reached actual terminal exit 1 with `lock=released` before tests:
ambiguous common/instance ContinuationIdentity imports and two anonymous Set
encoder definitions sharing a generated name. Repair 1/3 aliases the instance
identity and gives the two purpose-specific encoders distinct names.
Evidence: `/tmp/skill.cncf.d/a-ccf7fd8a-e70f-454d-8f9b-fd4ce08fb60b/41597-20261001T235528Z.summary.json`.

The same frozen host-binding obligation is made explicit before rerun: pass the
privately admitted current SplitPhase runtime Catalog into preparation and
require its exact definition, rather than accepting another Workflow of the
same Component. The spec supplies the actual admitted catalog and rejects a
real GoalPhase definition. This neither activates an Operation nor creates a
new route/authority. Full facts are compared once; owning P/B/E/W constructors
then receive the already selected records. Raw decoding's canonical projection
no longer decodes that successfully decoded structure a second time.

Invocation 754 reached terminal exit 1 and `lock=released`: the complete nested
raw record's automatic Circe derivation exceeded ordinary Scala inline depth.
No tests ran. Repair 2/3 binds explicit semiautomatically derived codecs for the
owning raw aggregates once, with unchanged schema/fields and no compiler-flag
override or alternate decoder. Before Test compilation, the new foreign-profile
fixture is corrected to use the actual existing GoalPhaseRuntimeCatalog rather
than a guessed binding object name. Frozen paths and assertions remain intact.
Evidence: `/tmp/skill.cncf.d/a-94191d49-3108-453e-b2dc-9fd055636974/42893-20261001T235913Z.summary.json`.

Invocation 755 reached terminal exit 1 and `lock=released` before tests: the
new aggregate givens named by Scala from their identical short `Records` names
conflicted. Repair 3/3 explicitly names all aggregate encoder/decoder bindings;
no payload fields, derivation semantics, source facts or runtime gates change.
This exhausts this boundary's three pre-review mechanical repairs.
Evidence: `/tmp/skill.cncf.d/a-60ddd7d5-7c1a-4424-b790-92b56daed54c/45279-20261002T000513Z.summary.json`.

Invocation 756 compiled all five new/changed main sources successfully, then
packaging's Cozy delegate refused its stale development runtime classpath
before Test compilation. It reached terminal exit 1 and `lock=released`;
no tests ran. This is a separate build prerequisite, not another source repair.
Admit the existing Cozy dependency at `/Users/asami/src/dev2025/cozy` for the
exact inspected `cozyExportRuntimeClasspath` task only: normal compilation/cache
and `target/cozy.d/runtime-classpath.txt` / `runtime-version.txt` output, no
source/configuration edits, publication, Git writes or remote effect. Then rerun
the identical sm-workflow accumulator on the unchanged candidate. No mechanical
repair budget reset or acceptance bypass is authorized.
Evidence: `/tmp/skill.cncf.d/a-06908efb-4538-4169-a8cb-b722e234c30c/45826-20261002T000635Z.summary.json`.

## Current validation and exact resume boundary

Cozy prerequisite 757 completed with SBT/wrapper exit 0 and `lock=released`.
Evidence: `/tmp/skill.cncf.d/a-c1c068c8-fd86-4339-a15f-c75e63cdce36/46293-20261002T000735Z.summary.json`.

Unchanged-candidate rerun 758 successfully prepared the runtime and packaged
the main sources. Test compilation then failed solely at the two new direct
`owner.systemNode` observations (lines 140/141): `Subsystem.systemNode` is
`private[cncf]` and is not visible to this application-package spec. SBT and
wrapper both exited 1, `lock=released`; no selected tests ran. This is a source
visibility error, not platform rejection, missing permission or an SBT lock
problem. The last green consumer 752 does not validate these new files.
Evidence: `/tmp/skill.cncf.d/a-6cbb6c86-c67a-4916-9885-fc2a43c900eb/46664-20261002T000818Z.summary.json`.

Read-only diagnosis found the existing local test-only
`org.goldenport.cncf.testutil.NativeHumanManagedHostObservation.counts(host)`.
It returns `(managedDataStoreCount, activeLeaseCount)` from the actual host;
the exact intended observation is `counts(bootstrap) shouldBe (1, 0)`.
Replace only the two inaccessible observations with this existing helper,
without adding an upstream/public API, new access capability or weakening the
assertions. All other new source/spec paths and the original accumulator remain
unchanged. No additional code repair or SBT invocation has been performed after
the three recorded pre-review repairs.

The exhausted pre-review ledger is preserved, not reset and not conflated with
the Phase full-review convergence counter. Under the goal-phase execution
contract, technical direction for this exact fixture-only correction admits a
new separately recorded bounded pre-review batch on the same Phase/Step; it is
not a new Phase, review evidence or an acceptance waiver. No typed full-review
`repair-cycle-exhausted` record is fabricated. Retain the last failed candidate
and all attempt IDs; no commit, staging, push, skill mutation or live SBT exists.

Production typed issue/completion and independently authorized current-fact
readers, as well as all original independent Step/Phase review, full validation
and release gates, remain open.

## User-directed resumed pre-review batch

The owning conversation's actual user message on 2026-10-02 is `再開して`.
The parent admits it against the exact pending question above: resume the same
Phase 2 with the two inaccessible spec observations replaced by the existing
test-only helper, followed by the unchanged accumulator. This is fresh human
direction, not consent inferred from the intervening scheduler continuations.
No new Phase, Goal, source root, public API, product behavior or acceptance gate
is introduced. The product Goal was marked blocked only for the unanswered
pre-review repair question; its original full objective remains unchanged.

Freeze a separate resumed pre-review batch, at most three byte-changing
attempts. Attempt 1 replaces only the two spec observations with
`NativeHumanManagedHostObservation.counts(bootstrap) shouldBe (1, 0)`.
This preserves both original assertions and actual host observation. The first
batch remains exhausted at 3/3; Step and full-Phase repair counters are not reset
or inferred. Scope is the existing spec, this journal and Phase 2 projection;
no main-source modification, upstream edit, shared-skill change or Git mutation.
Run the original serialized generation/packaging/focused accumulator, with
fresh typed command permissions and scoped native review. Retained user-directed
parent-only limited recovery applies; no agents, hash control or old adapter.

Resumed invocation 759 stopped before tests on a newly stale Cozy development
classpath (terminal exit 1, `lock=released`). The already admitted build-only
prerequisite 760 completed with exit 0 and `lock=released`; no source edits or
additional byte-changing repair were made for this prerequisite.
Evidence: `/tmp/skill.cncf.d/a-a2d454fc-f988-4569-b511-181643915850/68884-20261002T010835Z.summary.json`
and `/tmp/skill.cncf.d/a-15a64445-8b05-43c0-b9e4-4cd2deb2c2e9/69708-20261002T010941Z.summary.json`.

Invocation 761 compiled both new specs and ran 389 examples in 55 suites:
388 succeeded, one failed, no cancellations/pending/aborted suites, terminal
exit 1 and `lock=released`. Both actual managed context Service/CLI/reopen
examples passed, including retained declaration refusal, inactive-call denial,
one managed physical provider and no residual lease. The only failed example
never reached its foreign-owner assertion: its test fixture used
`ComponentId("test.Foreign")`, rejected by the existing ComponentId constructor
because its namespace had only one segment. This is not a runtime context
admission failure or a changed public contract.
Evidence: `/tmp/skill.cncf.d/a-1df91c14-1d80-417b-a525-f83938d96c68/70404-20261002T011048Z.summary.json`
and log lines 731-736.

The outer Phase admits resumed batch repair 2/3 as one M1 fixture correction,
under `cncf-test-fix` orchestrated repair mode and retained parent-direct
recovery. Exact additional existing owned path:
`src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseExplicitPartitionContextSpec.scala`.
Replace only that foreign Component identity with a well-formed foreign
namespace; all foreign-owner, actual foreign-definition, range/source and
known-input rejection assertions remain. This path is already in the original
frozen context boundary; no repository, production API, schema, acceptance or
semantic repair surface is added. Failure convergence is observed, not asserted:
the inaccessible observation compile failure closed and 388 actual examples
passed, leaving one fixture construction error. Preserve the first exhausted
batch and the resumed 1/3 history. Rerun the same full focused accumulator;
repository-full validation remains owed at the original release gate.

## Focused result and continuation

Resumed repair 2/3 uses the already established `org.example.Foreign` identity
from existing specs; no assertion was removed or weakened and main sources
were unchanged. Invocation 762 completed the identical generation/runtime,
`packageBin` and selected focused accumulator with **389/389** examples in
**55 suites**, zero failures, cancellations, ignored/pending or aborted suites.
SBT/wrapper both exited 0 and the authoritative marker reported `lock=released`.
Both new context suites ran: six raw-data/construction scenarios and two real
managed Service/CLI/read/pool-reopen scenarios. `git diff --check` also passed.
Evidence: `/tmp/skill.cncf.d/a-4468290b-fbdd-45fe-8f9a-2533513a0c09/72013-20261002T011337Z.summary.json`
and its complete `.log`; the original 3/3 and resumed 2/3 histories remain.

The pending technical-direction question is resolved by the actual human
`再開して` instruction, and its diagnosed failures are closed by actual focused
execution. There is no remaining human choice or live SBT in this slice.
Parent-only execution and test-fixture installation are not independent review,
production registration or human ingress evidence. No stage/commit/push,
publication, skill installation or unrelated source edits occurred.

Continue the original Phase 2 at the next necessary production boundary:
independently authorized current-fact readers and host-owned context
registration, then canonical typed WorkOrder issue/result completion using the
registered partition Operations. The stored snapshot must not be presented as
its own independently current source; do not reuse the old hash-based semantic
assembly or manufacture consent/capabilities from decoded data. This slice
provides context storage and construction, not that production connection.
Independent Step acceptance and full Phase review/full validation/release,
original authority/base admission and the complete original Phase scope remain
open. Successful focused tests do not establish Phase completion.
