# SplitPhase managed proposal evidence reads

Same active sm-workflow Phase 2, IMPLEMENT. Previous turn progressed through
actual source/proposal record reads and database reopen, with invocation 697
passing 230 tests. Retain original Phase scope and user-authorized parent-direct
limited recovery; no delegate. Parent model and effort not directly observable,
standard interface mode. Serial SBT, platform review, exact scope, original
base/authority, independent reviews and final acceptance remain required.

## Frozen implementation scope

Own SplitPhaseProposalAssessment (call-scoped Port view),
ProfileStartAdmission (shared current-call selection view),
SplitPhaseProposalAssessmentServiceOperation (current call forwarding),
SplitPhaseSemanticWorkflow (assessment-only ephemeral read-port view),
ManagedHostRecordRead (closed proposal-purpose read), new
SplitPhaseHostProposalReadPolicy, SplitPhaseProposalRecordJsonV1,
SplitPhaseProposalDataStorePort and their specifications, and a bounded
scenario in SplitPhaseManagedStartAssemblySpec. The existing target programs
have been read completely. Preserve unrelated dirty changes. No Preview/Apply
or semantic-completion execution change, source writer, initial selection
issuer, standard launcher activation, Git, publishing, installation or commit.

The four required read purposes are inventory, draft, validation and source
boundary. Settings are exact resolved host configuration under
`sm-workflow.split-phase.proposal-records.`: `store-name`,
`inventory-collection`, `draft-collection`, `validation-collection`,
`boundary-collection`. Same store as Workflow, selection and entry records;
all nine collections must be distinct (the four purposes, selection, entry
source/proposal, instance and continuation). No physical storage grant is
implied. Recheck the owning current call's host policy at each managed read.

Each storage projection carries exact `schemaVersion`, explicit `reference`
and typed `payload`. Inventory/boundary reference the source snapshot identity;
draft/validation reference the proposal identity. Do not derive identities or
hashes. Bound payload size, require exact case-class fields recursively,
integer/boolean/enum types, explicit optional fields and collection shape.
Existing deterministic validators retain semantic admission responsibility.
Decoding does not assert Ready, rewrite durations or fill missing evidence.

Stored validation is a separate five-field baseline projection:
calibrationRequests, semanticHandoffs, validationOwnership, partitionPolicy,
semanticEdges. It deliberately cannot deserialize the private Admitted
semanticInputs object. Reconstruct only Evidence(..., semanticInputs=None).
AI enrichment remains the typed Continuation completion/admission overlay,
not a trusted flag in arbitrary JSON. Maps retain their domain keys; sets
retain domain semantics. Exact recursive shape checks are not management hash
or byte-identity control. Existing fixture algorithms are not certified removed.

Assessment's incoming core must reach selection, entry and proposal ports,
including the semantic-assessment branch. The temporary semantic read-port
view shares the exact existing runtime, persistence, issued work, intents and
policies, never a second engine/store. Context-free access still refuses.
Pure fixture ports retain their behavior. Completion's current-call adaptation
and all-profile canonical assembly remain subsequent work, not acceptance here.

Executable authoring gate: AnyWordSpec/Matchers/GivenWhenThen, clauses next to
setup/action/expectations. Prove independent exact purpose/disjoint policy,
all four codec roundtrips, nested extras/missing fields/type/enum refusal and
rejection of forged semanticInputs; current core views; real host fixture
readback of all four purposes plus missing and expired call refusal, without
creating a human selection or Workflow instance. No new digest calculation in
the added record-reader scenarios. Existing fixture validators remain in the
regression accumulator. Run the exact SplitPhase + GoalPhase managed command
after fresh permission allocation, no edits during SBT. At most three bounded
pre-review mechanical repairs in this unit; no independent review or full
Phase completion claim from focused tests. Stop on scope/authority expansion.

## Actual verification

Invocation 698 compiled the main changes and dependent sources, prepared the
canonical runtime, packaged the actual JAR, and passed all 240 tests in 34
suites. Zero failed, aborted, canceled, ignored or pending cases. Both exits
zero and lock released. Full summary and the new codec/policy/current-call/
real-datastore scenario logs were read at
`/tmp/skill.cncf.d/a-a0898896-33ab-4848-bf7a-d0360862ffbd/84527-20261001T182019Z.summary.json`.
No repair was needed; no sources changed while SBT was live. Whitespace checks
passed and the full consumed-ID ledger retains 698. No live session remains.

New evidence proves all four projection roundtrips, nested field/type/enum
refusals, explicit reference correlation and forged semanticInputs rejection.
Nine-collection separation and exact read purposes are tested. Canonical host
dispatch really reads eight projections over two calls, observes absent rows,
and refuses every expired call. The fixture's baseline is deliberately
structurally typed but semantically insufficient: empty calibration cannot
cover owned work and is refused before calibration/digest computation. Neither
record decoding nor host read policy falsely turns it into Ready or consent.

Both ordinary and semantic assessment branches forward each incoming capsule
to selection and entry, and stop before effects when consent/source/core is
missing. Four-purpose proposal call views are separately exercised. These
tests do not prove a positive managed proposal assessment reaching semantic
work; no such broad result is inferred from the negative branch checks.
Existing semantic fixture regressions still pass but remain memory/file
fixtures, not normal-user managed runtime acceptance.

Remaining full Phase work includes positive Start with independently admitted
human-selection ingress and hash-free production identity/idempotency;
canonical all-operation Split assembly; Preview/Apply and semantic-completion
current-call host ports; controlled external commands and retained receipts;
all-profile launcher/MCP, management/resident projections and full positive
restart/transport/concurrency behavior. Original-base admission, independent
Step/full Phase reviews, acceptance commits, final full validation and release
remain unclaimed. No commit, push, publication, user installation or new pool
was performed. The next safe step is sharing the canonical Split persistence
binding across Start and assessment and consuming these managed readers in
one host-only service assembly, without prematurely publishing the fixture's
hash-derived progression or making an initial-human authority choice.
