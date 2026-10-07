# Hash-free typed Start preparation and partition binding

Date: 2026-10-02 (Asia/Tokyo)
Status: implemented; selected runtime/package/focused validation passed, not Step/Phase acceptance.

## Design and authority

Continue original Phase 2 after accumulator 768 passed runtime/package and
401/401 examples in 59 suites. All original authority bases, independent
acceptance reviews, full validation and release remain open. Retained
parent-direct limited recovery/no agents applies; no permission or lock bypass.

The old explicit Start record contains digest-bearing EntryAssessment data.
Use a distinct closed typed Start record/schema containing explicit reference,
instance identity, compiled Workflow identity/revision, configuration, exact
Start command, complete host selection, typed source and optional typed proposal.
No legacy decoder or assessment fallback. Reuse only the hash-free command and
selection data codecs and current command-admission gate. Exact selected input
JSON must be present in the host selection and match the Start input; selection
origin alone cannot bind an arbitrary caller input. Full current source,
proposal and selection are independently read at one construction boundary.
The compiled catalog determines Workflow binding; no computed identities.

Pure Prepared constructs the canonical initial instance but issues no Start.
Binding to a privately prepared partition requires exact Start reference,
instance identity/compiled definition/configuration/correlation, initial
history causation and complete selected source (and proposal when selected at
Start). A later proposal is allowed when Start selected none; this is not
proposal validation authority. The context Prepared carries its already
validated current instance so binding does not reload or revalidate that same
canonical record. Private constructors establish the new cross-record relation.

Managed loading uses current Operation, the Subsystem provider and exact
typed-start, selection and nine planning purpose declarations. All fourteen
collections, including context/instance/continuation, must be distinct in one
Workflow store. Source/proposal current reads are fixed typed methods, not a
caller-selected processor. No retained call, provider, pool, lease, writer,
claim, contention retry, newest lookup or digest.

Host-recorded human selection is data, not independently verified genuine
human ingress. These constructors/readers do not create or activate an
instance, register a Start, issue WorkOrder, attest consent or accept results.
Real native ingress and canonical issue/completion must be connected and
tested separately; private fixtures cannot close those obligations.

## Frozen ownership

Relative paths under this repository:

- `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseTypedStartPreparation.scala` (new pure types/construction/binding)
- `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseTypedStartJsonV1.scala` (new closed data codec)
- `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseTypedStartReadPolicy.scala` (new independent host purpose)
- `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseTypedStartDataStorePort.scala` (new current reads/binding)
- `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseExplicitPartitionContext.scala` (Prepared retains its validated current instance)
- `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePlanningInputDataStorePort.scala` (two fixed source/proposal readers)
- `src/main/scala/org/simplemodeling/textus/smworkflow/admission/ManagedHostRecordRead.scala` (one exact typed-start read)
- `src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseTypedStartPreparationSpec.scala` (new pure specs/shared private fixture)
- `src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseManagedTypedStartReadSpec.scala` (new actual host specs)
- this journal and `docs/phase/phase-2.md` (evidence projection)

No legacy Start rewrite, CML/ABI/public API change, upstream source edits,
shared skills, configuration installation, Git writes, commit or publication.

## Specification and validation

Prove exact complete preparation/initial identity, optional proposal,
same-reference current-data/selection/input drift refusal, nonhuman selection,
wrong source/authority and Start/context/instance history bindings. Closed
raw codec must reject unknown/missing nested fields, crossed versions, legacy
prefix, oversized UTF-8 and malformed data without constructing Prepared.
Real Service/CLI probes read independent current rows and canonical context
through one provider, reopen physical storage, refuse retained undeclared and
expired calls and preserve history/prior DataStoreSpace binding. Private
installers create test initial+history and data only, not real human Start.

Run the unchanged original runtime/package/focused accumulator; both new
SplitPhase suites join the existing wildcard. Fresh typed permissions/scoped
native review, shared machine SBT serialization, no edits while live, at most
three converging pre-review source repairs for this new boundary. Earlier
repair histories remain separate and no acceptance gate is narrowed.

## Invocation 769 and frozen M1 repair

Actual parent-direct invocation 769 compiled the changed main/test sources,
prepared runtime and package successfully. The unchanged accumulator ran
407 examples in 61 suites: 406 succeeded, one failed, none aborted/canceled/
pending. Both managed typed Start examples passed, including actual Service,
CLI, SQLite reopen, current-source/proposal/selection drift and expired calls.
SBT/wrapper exited 1 with the matching terminal marker and `lock=released`.
Evidence:
`/tmp/skill.cncf.d/a-3d63bb9e-6ba5-436e-ac78-9a416347bd42/15650-20261002T024841Z.summary.json`
and the matching `.log`.

The sole failure is the new pure binding example at test line 62. It fabricated
an instance by changing history causation without changing current causation;
the canonical instance constructor correctly rejected it with
`CWF77-WORKFLOW-PERSISTENCE-011: current-facts-must-match-final-history-entry`.
This does not exercise the intended cross-Start relation. Freeze M1 repair 1/3
to the new pure spec only: construct a separate valid initial instance with
foreign causation and append its matching history through the existing typed
constructor, then verify the new binding rejects that valid foreign history.
No production guard change, test weakening, upstream edit or legacy fallback.
Rerun the unchanged original accumulator with a fresh permission/attempt.

## Successful selected validation 770

Actual invocation `SMWF-TYPED-START-PARTITION-BINDING-REPAIR1-DIRECT-20261002-770`
completed the unchanged runtime preparation, packaging and focused accumulator:
**407/407 examples in 61 suites**, zero failed/canceled/ignored/pending/aborted.
The repaired pure fixture now reaches the intended relation and is refused;
all four pure typed Start and both actual managed read examples pass. SBT and
wrapper exited 0 with the matching terminal marker and `lock=released`.
Evidence:
`/tmp/skill.cncf.d/a-6d54b320-fb86-4857-96d2-53bda229eba9/16680-20261002T025016Z.summary.json`
and matching `.log`. Repair count is 1/3 for this frozen unit; invocation 769
and its actual failure remain recorded, not replaced by the successful result.

Execution route is retained parent-direct limited recovery, no spawned agents;
actual parent model/effort is not observable. Fresh typed permission, native
scoped review, standard shared JVM/development caches and machine-wide lock
were used without bypass. No Cozy/upstream source edits, CML/ABI/public API
changes, Git writes, publication, shared-skill install or cleanup occurred.
Only build/runtime/package and private test storage outputs were produced.
Documentation projection after terminal completion does not change source
validation scope. This is not an independent acceptance review.

Original Goal/Step/Phase remains open. Next production work is actual hash-free
Start construction/registration from genuine current human ingress and the
canonical typed WorkOrder issue/completion connection. Current raw selection
fixtures do not establish genuine consent, and a canonical fixture instance
does not prove public Start execution. Full validation, original independent
reviews, release and skill/CLI acceptance obligations remain required.
