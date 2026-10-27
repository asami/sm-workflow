# Explicit bounded Plan revision

## Remaining-purpose semantic continuation (R17–R19, E4)

R17. `captureC` retains its strict current-Step and whole-Phase corrective-Step
meaning. Fresh no-document semantic question preparation uses
`captureSemanticProposalC`. A changed purpose of an existing future unaccepted
Step structurally selects the separate `remaining-purpose` branch. Ordinary
capture errors are never swallowed to select it. This branch requires the actual
Implementation/Repair decision-needed result, a Decision cursor equal to the
accepted-prefix size and below Step count, no selected documents or document
conflict, no pending/exhausted repair or convergence stop, closure/unknown effect,
accepted Phase review or generation overflow. Document-backed future-purpose
proposals refuse through the existing strict route; their Bound is never dropped.

The complete Plan requires a fresh revision and exact identity, Phase,
repository, Step count/order/IDs, accepted Step definitions, paths, dependencies,
checks, action policies, work classifications, Phase check/review policies,
repair limit, raw acceptance and saved effective acceptance, including condition
order and sources. Only purposes at the current cursor and later may change;
at least one later purpose must actually differ. The new capture copies the
controller's already saved effective acceptance after checking raw acceptance
equality. It does not recapture current project defaults. Ordinary remaining-Plan
eligibility and this stricter pure delta guard both apply on saved adoption.

R18. `Captured.revisionScope: Option[String] = None` is a closed optional public
field. Its only present value is `remaining-purpose`. Historical absence emits
exactly the old JSON and retains its old meaning. Present null, unknown values,
selected documents combined with this scope, contradictory raw Proposal/captured
Plan, a scoped capture without a future purpose change, and an erased scope on a
future-purpose question refuse. A retained question uses its stored scope and
capture, never fresh semantic capture or later host defaults.
`adoptedC` sends historical absence to strict `adoptRevisionC`; only the explicit
new scope uses the strict purpose-only guard and `adoptRemainingRevisionC`.

R19. Ordinary Advance can issue one actual Human from the retained accepted
semantic result before any question exists, without replaying its accepted Submit.
The real question context and notice display `remaining-purpose` and every changed
unaccepted Step identity. Choices remain exactly `AdoptPlanRevision`,
`RejectPlanRevision`, `Defer`, `Cancel`. The existing independent Human owner binds
the entire capture, including scope and target; erasing/changing scope or proposal
cannot reuse its evidence. Genuine independent adoption resumes the same current
Step at generation + 1, retaining Session, original base, accepted commits/reviews,
issues, history and all repair counts/policy/observations. New work still needs its
own checks, independent review and real scoped commit. Rejection, Defer, Cancel
and the separate remaining-Plan Operations retain their owning transitions.

E4 (`CBD12-PLAN-HUMAN-409`) covers an accepted first Step, a second-Step accepted
decision-needed proposal changing second/third purposes, and a runtime closed
before Human issuance. The ordinary source/live route reopens and issues the
scoped question once, retains it on readback, independently adopts it and reopens
at the same second Step with generation + 1 and an exact prefix/base/counts.
The separate Operation example describes and adopts a remaining Plan at that new
issued frontier. Pure cases retain old capture refusal, finite codecs and crossed
scope/evidence/accepted-prefix/delta/exhaustion/overflow refusal. The dedicated
installed CLI example is selected with the existing
`smworkflow.spec.installedCommand`, `smworkflow.spec.installations` and
`smworkflow.spec.sbt` flags; it requires an installed command and has no source
launcher fallback. It performs actual fixture work, successful checks, independent
fixture review, source-authority admission and a real Step commit before issuing
the next Step. All semantic and Human fixture inputs are specification-owned.
These authored examples establish no execution result, live consumer adoption or
genuine Human direction; parent validation and independent review remain required.

Updated 2026-10-27: the separate
[current primary-Component inventory adoption](goal-phase-remaining-plan-adoption.md#current-primary-component-inventory-continuation-r13r16-e3)
uses its own pure current-Step literal append guard and typed `Target.currentInventory`
within the existing remaining-Plan operations. Ordinary `adoptRevisionC` and
`adoptRemainingRevisionC` remain strict about ownership. The inventory branch
changes only the fresh revision and current uncompleted Step's explicit appended
paths; every purpose, check, condition/source, closure policy and other Step remains
exact. Each added path requires explicit same-Component host registration and an
independent Human binding of the exact current target. Retained adoption resumes
ordinary Implementation at generation + 1 with the original base, accepted prefix,
partialCompletion chains and all repair counts unchanged. The original unanswered
Human is atomically superseded without an answer; actual effects belong to a
separate ordinary Advance. This contract neither introduces a Proposal schema nor
permits current host defaults to reinterpret old captured inputs. See R13–R16 and
E3 for public codec, authority and installed old-to-new qualification obligations.

Updated 2026-10-23: the separate
[remaining Plan adoption](goal-phase-remaining-plan-adoption.md#narrow-review-decision-continuation-r8r12)
contract now includes a narrow saved ReviewStep `decision-needed` continuation.
This requires one actual OPEN review finding, one fresh required Step condition,
an otherwise exact Plan and independently admitted Human direction. Ordinary
reviewer Plan proposals and ordinary repair acceptance remain unchanged. The
new revision explicitly retains every old raw/effective condition and source;
it governs new and future unaccepted work without reassessing historical work.
Retained native supersession selects RepairStep and consumes one ordinary
repair, preserving Session/base/prefix/history and the unanswered old question.
This source update still requires parent-owned focused validation, independent
review and isolated installed qualification before delivery.

`GoalPhasePlanRevision` owns the pure proposal/capture/adoption boundary for
PHASE-6.2 TS03. H2/H3 connect it to ordinary semantic Submit, actual saved Human
questions, independent evidence admission, atomic Human completion and the sole
native same-Session history walker. Specifications use explicit fixture-owned
answers; they establish neither genuine Human adoption nor Step acceptance.
PHASE-6.3 additionally permits one explicit corrective Step after an integrated
whole-Phase finding, through the same saved question and actual Human adoption.

The connected route is a raw complete Plan proposal, followed by the
actual question owner's captured Human question, explicit host-admitted adoption
of the selected answer, and new implementation, checks and independent review in
the same Session. The native owner admits both the actual question and
its actual answer separately. It must retain prior native history and all failures.
The proposal and capture grant no consent, commands, executable selection,
workspace access, permission, native result, review or commit authority.

`Proposal(identity, revision, reason, plan)` carries a complete raw Plan without
`effectiveAcceptance`. Identity and revision are nonempty, control-free and at
most 128 UTF-8 bytes; reason is nonempty, permits multiple lines, excludes NUL and
is at most 16384 UTF-8 bytes. Typed codecs refuse unknown top-level keys and
absent, null or malformed required fields. Plan decoding uses the owning Plan
codec and complete Plan validation.

`captureC(controller, proposal, project)` uses the actual acceptance-boundary
owner to capture project defaults and explicit adopted whole-condition overrides.
It returns `Captured(proposal, plan, documents = None)` only after validating that saved Plan and
checking controller eligibility through a discarded pure adoption. The internal
`proposal-eligibility` reference is only a dry run; it is never execution or Human
evidence. Capture leaves the original Decision unchanged.

`Captured.documents` optionally retains the selected business
`Bound(source, bindings, items)`. Absence is omitted from JSON. This owner's
finite nested codecs require exact Source, Binding, Item and Bound fields and
refuse present null, unknown or absent required fields, malformed membership
and invalid Bound data. Existing `GoalPhaseDocumentSource.instructionsC`
validates the Bound; binding Step order must exactly match the captured Plan.
This adds no document parser or integrity certificate.

For an existing selected document source, fresh question preparation calls
`GoalPhaseDocumentSource.loadForCallC(previous.source, core)` once through its
actual owning ActionCall and existing File UoW to acquire the two files.
`captureDocumentsC(controller, proposal, project, previous, imported)` requires
the imported raw Plan to equal the complete explicit proposal, performs ordinary
capture, then attaches `Bound(previous.source, imported.bindings, imported.items)`.
Prior and proposed bindings must be equal and cover their respective Plans in
exact Step order. Source paths, item identities, membership and every checked
flag remain equal. Only item acceptance text belonging to the current unclosed
Step may change; all other items remain equal by identity, including accepted
Steps and unselected future Steps. Existing bounded controller eligibility,
ownership, dependencies, classifications and accepted-prefix guards still apply.

Captured input is the question owner's selected business input, not a consent or
anti-tamper certificate. Its saved Plan, with effective acceptance removed, must
equal its explicit raw proposal Plan. This equality checks the relationship
between two business objects. Readback and adoption consume the saved capture
without consulting later project configuration or reconstructing prior defaults
from a replaced effective boundary. There are no management hashes, aliases,
payload copies as integrity proof or fallback selection.
Saved document validation pairs the capture with the actual previous optional
Bound, requires matching presence and repeats the same pure compatibility
checks. It performs no reads, writes or reconstruction of prior configuration.

`adoptedC(controller, captured, evidence)` validates the saved capture. Historical
scope absence passes the actual host-admitted answer reference to
`State.adoptRevisionC`; only explicit `remaining-purpose` dispatches through the
stricter R17–R19 guard and `State.adoptRemainingRevisionC`. Historical adoption
ordinarily requires a current unclosed Step at a semantic Decision sourced from
Implementation, Repair or Review, with no document conflict, pending exhausted
repair or closure intent, and a generation below the maximum. Ordinary unpaused
work, uncertain native effects and Closed are ineligible. The sole additional
whole-Phase eligibility branch is specified below.
An exhausted repair window cannot be renewed through adoption.

The complete proposed Plan must have a new revision. Plan identity, Phase,
repository, repair limit, Phase checks, Phase review classification, Step count
and every other Step remain equal. Current Step identity, owned paths,
dependencies and work classification remain equal. Only its purpose and selected
literal check identities may change. Fresh question capture requires nonempty
current-Step check identities that all match declared `validationPolicy` checks.
Executable, workspace and permission checks remain with their existing owners.

Before any Step is accepted, explicit owner adoption may revise the effective
acceptance boundary. After a Step is accepted, effective Usage Contract usage
and every effective `all`/`step` condition must remain exactly equal, compared by
condition identity. Accepted Steps cannot be retrospectively redefined.
Phase-only conditions remain available for explicit adoption at these frontiers,
where no accepted Phase review exists.

Success resumes the same Step at Implementation with the proposed captured Plan
and generation incremented once. Cursor, original base, completed commit/review
history, repository receipt and both consumed repair counts are retained. The
candidate, check receipt, review evidence, closure intent, decision, repair
directive, decision source, document-decision flag and pending repair are cleared.
There is no counter reset or refund. Repeating adoption at the resulting
Implementation frontier fails. New semantic completion and candidate inspection
must precede Validate selecting the revised checks; no old receipt becomes fresh
acceptance. Prior history remains external retained native history.

Ordinary `DecisionResumptionAdmitted` retains its existing behavior and Plan;
Resume cannot impersonate Plan adoption. Existing live `refreshedC` behavior is
also unchanged. This boundary adds no generic Event, reset, native action or
commit. The parent owns focused validation and integrated review/acceptance.

## Corrective Step after an integrated Phase finding

An actual independent `ReviewPhase` may report `decision-needed`, a genuine
nonempty integration finding and one complete raw `planRevision` proposal.
The proposal is business decision content in the actual independent result.
Its exact owning Proposal JSON, including reason, revision and proposed Step
meaning, participates in the review evidence verdict; a different proposal
cannot reuse that attestation. Present null or malformed data is rejected.
An absent historical proposal remains absent. No entire request, state, provider
snapshot or hash certificate is added. A `dependencyProposal` and `planRevision`
cannot accompany the same result. Existing acceptance observations, issue
coverage, finding recording and independence requirements still apply.

The ordinary review outcome becomes `ReviewOutcome.Human` and then Decision;
neither the proposal nor its independent admission adopts a Plan. The native
owner retains the complete actual-shaped Phase report, including its proposal,
original candidate, checks, generation and actual result reference, in prior
business context. Retention and reconstruction keep their existing provenance
and bounds. The actual Human question owner captures the proposal against the
selected host acceptance before offering adoption.

The extra `State.adoptRevisionC` branch requires Decision sourced from
PhaseReview, no document conflict, pending repair or closure intent, a cursor
equal to both the old Step count and completed Step count, a generation below
`Long.MaxValue`, and fewer than 64 old Steps. The proposed complete valid Plan
must use a fresh revision and retain exactly the same identity, Phase,
repository, repair limit, Phase checks, Phase review classification, raw
acceptance declaration and captured effective acceptance. Every old Step remains
the exact prefix. Exactly one appended Step has a distinct identity, every old
Step identity as its exact dependency set, nonempty checks and nonempty owned
paths each equal to or nested under an original owned-path scope. No completed
Step, accepted requirement, repository or permission can change.

This additional branch requires an explicit Plan with `Source.documents = None`.
Document-backed Phase corrective-Step amendment is unsupported and returns the
ordinary precise limitation without rereading, removing or rewriting selected
document bindings or falling back to an explicit Plan. Existing document-backed
current-Step revision remains available under its unchanged rules.

Fresh capture checks the appended Step's selected checks against the already
declared host validation policy. The host must also have predeclared the needed
closure ordinal, owned paths and checks; Plan adoption extends no host authority.
Saved questions retain their exact proposal, captured acceptance, actual result
and history rather than consulting later defaults. Genuine host-admitted
`AdoptPlanRevision` uses the existing `human-plan-revision-adopted`
Decision-to-Implementation transition. Reject retains the old PhaseReview
decision source and resumes only that existing review through its ordinary
resumption transition. Defer and Cancel keep their current paused semantics.
An invocation of the enclosing Phase is not the actual Human answer.

Adoption retains the original Session/base, every completed Step identity,
commit and review reference, issue history and both consumed repair counts.
It increments generation once, keeps the cursor at the old Step count and
starts only the appended Step at Implementation, clearing the ordinary
candidate/check/review/intent/decision/pending fields. It does not refund the
predecessor's current repair counters. The normal corrective Step closure
performs its existing counter reset. Fresh semantic completion, inspection,
selected checks, independent Step review and a real new Step commit must all
precede the next whole-Phase review. Accepted commits are never reopened.
No automatic refresh, new Stage, WorkKind, CML transition or repair engine is
introduced by this branch.

Review instructions keep `ReviewStep` unable to propose or adopt revisions and
keep `ReviewPhase` read-only. Affected re-review consumes actual prior reports
and findings, the current combined diff/checks and adopted requirements. It
reuses unrelated still-applicable judgment and assesses remaining integration
findings and regressions. It introduces no per-edit reviewer, approval bypass or
automatic rerun of all Step semantics.

## Ordinary semantic proposal and actual saved question

`GoalPhaseNativeWork.Result` carries trailing optional `planRevision: Proposal`.
Ordinary `ImplementStep` or `RepairStep` `decision-needed` results may
include it. Completed results refuse it. `ReviewStep`, review `accept` and review
`repair` refuse it. Only `ReviewPhase` `decision-needed` with a genuine nonempty
finding may additionally carry a corrective-Step proposal. Null or malformed
proposal presence remains invalid for every kind.
Existing acceptance, issue report and Fix assessment requirements still apply.
Proposal presence supplies no correction or `FIXED` disposition. Owning codecs
omit absence and reject present null or malformed values.

Public `GoalPhaseWorkResult.planRevision` is an optional structured Record of
the exact owning Proposal JSON. `GoalPhaseHumanQuestion.planRevision` similarly
projects the exact saved Captured JSON, including internal work classification.
Input uses `Record.toJsonStringC`, Circe parsing and recursive CNCF
`RuntimeContext.PropertyNameContext.default.normalizeInputName` on every object
key before the unchanged strict owning codec. Any normalized-name collision,
including equal aliases, fails. Scalars, numbers and arrays, including empty
arrays, stay literal. There is no String, Base64, command or alternate schema.

Fresh Human preparation captures the raw proposal once with current project
acceptance and checks eligibility. The resulting actual question stores its
Captured business input and offers exactly `AdoptPlanRevision`,
`RejectPlanRevision`, `Defer`, `Cancel`. Ordinary and exhausted-window questions
retain their exact existing choice vectors. Selected documents are saved only
through the bounded current-Step capture above. An ineligible proposal cannot
produce a question or a consent claim.
The additional PhaseReview corrective-Step question requires absent selected
documents; it never changes the existing document-backed current-Step route.

Advance, retained readback and Human completion load the actual issued question
before preparing their state. Existing questions validate and consume their
saved Captured input and its raw-proposal relationship to the actual requesting
semantic result. They do not recapture later project defaults or reselect later
host check policy as a precondition. Saved capture establishes the selected
business target, without being an anti-tamper certificate or consent evidence.
Retained question preparation and completion validate saved document presence
and compatibility against their actual predecessor without rereading either
file. They retain the existing requesting result, semantic source, counts and
independent evidence checks.

## Independent answer and following explicit Advance

The selected Human evidence scope contains optional `planRevision` only when
the actual question has a captured revision. Its owning Captured codec validates
the full selected business target. Changed proposed checks, acceptance,
requirements, proposal identity or revision cannot reuse unrelated independently
selected answer evidence. Public Human question normalization uses the same
recursive CNCF normalizer for this field, and malformed or conflicting presence
stays invalid rather than disappearing. Unrelated finite scope and diagnostics
retain their existing behavior. Passive preparation recognizes global declared
choices; native admission decides which choice the actual question offered.

Independent host evidence, participant admission and ordinary atomic managed
completion remain mandatory. `AdoptPlanRevision` passes only the saved capture
and actual answer result reference to `adoptedC`, with the CML transition
`human-plan-revision-adopted` to Implementation. `RejectPlanRevision` invokes
ordinary `DecisionResumptionAdmitted`, retains the old Plan/source stage and
records explicit rationale in `DecisionContext`. It uses the existing
`human-semantic-resumed-*` transition. Defer and Cancel preserve paused history.
Neither ResumeAssignedWork nor ContinueRepairs is offered for revision questions;
an exhausted pending repair window cannot be bypassed by adoption.

Human Submit only commits its answer and returns ordinary acceptance/message.
It executes no tests, dispatches no successor, changes no host profile and grants
no source, Git, SBT, review or closure authority. The sole contiguous native
walker follows explicit adoption or rejection. Adoption clears cursor candidate
and validation while preserving original Session/base, completed Steps, issues,
history and consumed repair counts. Only `AdoptPlanRevision` selects that saved
Input's captured documents as the cursor documents. The walker retains actual
document progress and its completed-Step projection, and retains any existing
document policy after validating it against the adopted Plan and fixed candidate
paths. Rejection, Defer and Cancel retain the old documents, policy and progress.
There is no file restoration or implicit adoption. Ordinary document refresh and
progress retain their owners at the following fresh frontier.
The next explicit outer Advance issues the
new ImplementStep through the existing execution profile and Required SPI.
New semantic completion, fresh candidate inspection, selected checks, independent
review and actual Step commit remain mandatory gates.

## Specification evidence and remaining delivery

`GoalPhasePlanRevisionSpec` covers public naming/collision refusal, semantic
result restrictions and saved-question codecs in addition to H1 pure guards.
Its Phase 6.3 pure cases close two Steps, retain the complete actual-shaped
integrated decision and proposal, capture an explicit fixture answer, adopt
exactly one bounded corrective Step, and pass through the normal controller
gates to PhaseReview again. They cover protected prefix/acceptance/policy/scope
refusals, rejection, document-backed limits, the Step bound and generation
overflow. `NativeHostReviewScopeSpec` covers exact typed proposal retention,
changed reason/revision/Step evidence refusal and malformed presence while
preserving historical absence.
Its H3 cases cover strict optional and nested saved document codecs, eligible
current acceptance revision with a completed prefix and actual progress
projection, and refusal of changed source, membership, checked flags, unselected
acceptance, imported Plan or mismatched saved presence.
`NativeHostHumanScopeSpec` and `NativeHostHumanPreparationSpec` exercise typed
revision questions, real CNCF public formatting and fixture-owned answers.
They show that changes to checks/acceptance or proposal identity/revision cannot
reuse an old independent attestation, while saved capture survives later defaults.

`NativeGoalPlanRevisionCliSpec` is `@DoNotDiscover` and uses the existing fresh
two-Step Phase 1 fixture with normal typed SBT and isolated local/bare repositories.
Every semantic submission and Human answer is explicitly SPECIFICATION-OWNED.
It saves actual request/answer/reference/execution files under the existing
allowed root, invokes normal `NativeHostPreparationService.prepareEvidence`,
and selects only that actual successful prepared import. It seeds no native
workflow database row. The positive route adopts through ordinary Human Submit,
then explicit Advance issues revised implementation; a fixture-owned value.txt
correction and normal semantic Submit precede one actual typed successful Test
and pending independent ReviewStep. Repeated questions and reviews do not rerun
SBT. Separate cases cover rejection, Defer, Cancel, nonoffered ResumeAssignedWork,
missing independent evidence and unknown configured check refusal.

The document-bound variant uses the same normal SBT continuation fixture, with
`value.txt`, `docs/phase.md` and `docs/checklist.md` as fixed candidates. Both
document paths belong to both Steps; value.txt belongs only to the first. Before
the initial fixture commit, setup writes a real sm-workflow-plan envelope and
unchecked explicit checklist acceptance. The ordinary source declaration and
input preparation select these documents; no native state is seeded. Its added
case authors a raw rev2/current-purpose Plan and ITEM-1 acceptance, submits the
proposal normally and asserts the saved typed Bound. Temporary changes to both
current files leave repeated pending questions and Human completion consuming
the exact saved capture. The fixture restores the proposed business documents
before the following fresh frontier, with no unchanged-input seal claim. The
issued WorkContext's existing `ContextBundle.summary` contains the adopted
checklist acceptance. A fixture-owned value correction and ordinary completed
Submit then precede one actual successful typed Test and pending independent
ReviewStep, with no replay on repeated Advance. The case uses the existing real
prepareEvidence/import route and explicitly specification-owned answer.

These source specifications do not claim a run result. The parent selects and
owns generation, Compile/Testcompile, the frozen focused suites, integrated
review and acceptance. H3 actual AI-authored tests/annotations/Slice-management
document authoring and genuine Human-selected adoption remain incomplete. H2/H3
fixture wiring closes neither TS01/TS03 nor the protected FIX Step; RECORD,
DECISION and integrated Step acceptance remain required. Full-suite
ownership remains Phase 6.3 and CBD ownership remains Phase 10.
This corrective-Step implementation and targeted specifications do not establish
actual installed integration, a real cross-Step finding, genuine Human
adoption, completed corrective work, affected Phase re-review or final Phase
closure. Those installed acceptance obligations and the parent's comprehensive
development Phase review and aggregate validation remain owed in the same
active Phase 6.3 Goal.
