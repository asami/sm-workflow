# SplitPhase retained intent from managed input records and issued work

Phase 2 IMPLEMENT under retained limited parent-direct recovery. Previous turn
714 passed 257 / 36 focused tests including physical baseline reads/reopen.
Full Phase scope/base/review/acceptance/release remain unchanged and incomplete.

## Frozen implementation

Own SplitPhaseSemanticWorkflow.scala, SplitPhaseManagedSemanticStorageAssembly.scala,
SplitPhaseSemanticInputDataStorePort.scala, new SplitPhaseSemanticIntentCommandJsonV1.scala
and SplitPhaseSemanticIntentDataStorePort.scala, SplitPhaseSemanticWorkflowSpec.scala,
and this journal. Extract existing Retained preparation assembly without altering
algorithms. Add a closed data-only original assessment command projection in
the same baseline record. New read-only intent adapter loads command/baseline
under current call, reconstructs Intent against full current facts, re-admits
the original selection/Start context, and matches actual canonical issued work
before returning freshly assembled Retained. Never decode private Intent or
duplicate persisted instance/Continuation state, write/issue or cache Retained.

Completion-only record assembly derives the baseline reader from exact host
declarations and binds canonical issued/instance/completion once. Keep ordinary
fixture completion APIs and algorithms unchanged. Convert the existing real
SQLite seeded Service/CLI completion scenario to this actual record-backed
intent adapter. Private fixture input+command installation uses one managed
record create before completion; restart runs no installer or saved Retained
callback. Assert missing command/crossed Continuation/malformed provenance
refuses before completion; valid closing/readback and replay refusal survive
reopen. Existing source/selection callbacks stay fixtures, not production human
ingress; full current baseline has separate positive managed proof from 714.

This does not activate production issuer/standard launcher or claim hash-free
Start/semantic preparation, enriched durable Preview, separate-process restart
or initial human consent. Existing hash calculations remain fixture algorithms,
not a new management-hash admission route. No other repository/grant/pool/API,
Git/publish/install/shared-skill change. No agents; actual parent profile not
observable, standard mode. Preserve platform review/shared SBT lock and original
assurance. Fresh 715 runs unchanged runtime/JAR/focused accumulator; at most
three converging mechanical pre-review repairs, none initially. No live edits.

## Validation and repair evidence

715 compiled production code and built runtime/JAR, then failed test compilation:
the fixture's Retained cache is a var of immutable Map, so clear() is not its
API. No tests ran. Tool/SBT/wrapper exits were 1 and terminal lock=released.
Repair 1 changes only that cache removal to assignment of Map.empty, retaining
the same cache-empty assertion and scenario. Fresh 716 repeats the full frozen
accumulator. Two bounded repair slots remain; no semantic/source change.

716 passed 258 / 36 focused tests, runtime and JAR, exits0 lock=released.
Source inspection then identified one current-call ordering gap in the new
intent adapter: canonical issued load preceded ManagedHostRecordRead's actual
current lease/component admission. For absent issue, it could return None
without ever admitting the current call. Repair 2 freezes only reordering
current managed input-row read/admission before canonical issue lookup; neither
row nor decoded command can return Retained without the issued witness. Add
ended/foreign-call and context-free/independent-write refusals to the same seeded
completion scenario, including an absent issue identity. Fresh 717 revalidates
the unchanged accumulator. One repair slot remains; no expanded grant/API.

717 passed 258 / 36 focused tests, exits0 lock=released, with the new current
lease admission order and escaped/foreign/independent-write/inert-row assertions.
Coverage closure 3 admits only SplitPhaseSemanticInputRecordSpec.scala's existing
714 real SQLite fixture: it deliberately contains baseline rows without an
original command, so assert loadRecoveryForCallC refuses that actual stored row
on both initial/reopened hosts. Also clarify the crossed-provenance scenario's
Then wording (its retained result stays absent, not readable). No production
source, schema, grant or assertion weakening. Fresh 718 repeats the unchanged
accumulator. The three bounded adjustments for this unit are now used; retain
all earlier evidence and do not extend this unit automatically if it fails.

## Verified outcome

718 completed runtime preparation, packageBin and the unchanged focused
accumulator: 258 tests / 36 suites, all succeeded with no failures/aborts.
Tool/SBT/wrapper exits all 0, terminal lock=released. Full success summary and
the changed command/recovery scenario log were read. Whitespace check passed.
No review, acceptance, original-base substitution or commit was performed.

RecordCompletionPorts no longer accepts an application Retained callback. The
assembly derives its baseline reader from exact host declarations and uses its
same canonical storage for original issued-work matching and common completion.
The private fixture Retained Map is empty throughout the managed scenario.
The original command and complete input facts are data-only managed projections;
each recovery re-admits current selection/source and ordinary semantic Intent.
Stored preparatory rows do not issue work or grant Start/claim/write permission.

Through actual Service and CLI, cold missing-input completion refused without
altering suspension/result. Correct command/input records recovered the original
semantic intent, matched the saved WorkOrder and completed to revision 3 WAIT
with exact typed result. Crossed Continuation facts refused, retaining revision
2 and no completion. Both branches retained their respective history/results
after real pool shutdown/reopen and rejected replay or crossed completion.
Actual SQLite baseline rows without command metadata refused the combined
recovery read. Closed command schema refused extra authority/nested fields and
foreign reference. An inert preparatory row with no issued work returned no
Retained. Current-call, ended-call (including absent issue), foreign component,
null context, context-free and independent-save refusals were verified.

Evidence summary:
/tmp/skill.cncf.d/a-504ba1e9-c206-4399-b15d-5c21b788d8e3/18109-20261001T195750Z.summary.json

Evidence log:
/tmp/skill.cncf.d/a-504ba1e9-c206-4399-b15d-5c21b788d8e3/18109-20261001T195750Z.log

Limits: source/selection callbacks here are still independent test fixtures;
714 separately proves physical baseline reconstruction. Positive completion in
this scenario occurs before the first pool shutdown; successful first completion
after shutdown between publication and result submission remains a required
next scenario, not inferred from result readback/replay refusal. Separate-process
restart, real initial human ingress, hash-free production preparation/public
issuer, standard activation, enriched Preview recovery and all original Phase
assurance/release requirements remain unproven. Goal stays active; this is
implementation progress, not a repeated genuine-blocker audit or completion.
