# Failed whole-Phase repair adoption

Version: Oct. 24, 2026

P116-CONT-01 supplies a normal same-Session continuation after one genuinely
terminated nonzero whole-Phase SBT check. Phase 6.5 additionally admits the
distinct confirmed `TimedOut`/typed `incomplete` source in R8. Its public Operations are
`GoalPhaseService.describeGoalPhaseFailedPhaseRepair` and
`GoalPhaseService.adoptGoalPhaseFailedPhaseRepair`.
This corrective route is optional when a product defect requires a new Step.
For a deadline extension alone, [validation recovery R10](goal-phase-validation-recovery.md)
offers `AuthorizeNewAttempt` (deadline extension), `LimitedRepair`, and `Defer`
for a confirmed timeout. Deadline extension authorizes a new attempt of the same
check on the unchanged candidate, without a corrective Step or repair-count
consumption; the normal host deadline must actually be adjusted before separate
Advance and authorization does not change configuration. LimitedRepair authorizes
bounded investigation/preparation before a captured concrete proposal and its
separately evidenced adoption here. It grants no adoption of an unknown future Plan.

## Rules

R1. Description reads retained native history only. Ordinary Failed eligibility is the unchanged
`GoalPhaseKnownFailedValidationRecovery.currentTargetC` proof: exact current
unsuspended PhaseValidation Decision, all Steps accepted, one real terminated
nonzero failed check, reconciled metadata, selection and attempt history. Unknown,
unconfirmed timeout, cancelled, Step, multiple-check, reviewed, pending-repair, closure and
document-backed repair frontiers refuse. No repository observation is performed.
Confirmed timeout uses only the explicit R8 source; failure of known-failed proof
never selects another recovery path.

R2. The target binds the complete original effective Plan, accepted Step prefix,
original recovery target including `knownFailedPhase`, repair progress,
generation, current declaration `candidatePaths`, the complete current native
Step closure policy, complete raw and effective proposal and selected corrective
checks. New target, direction and evidence
envelopes reject unknown, missing and explicit-null fields. Live targets are
reconstructed from retained history and the current declaration, including its
complete current closure policy. A retained Direction keeps both its complete
original candidatePaths vector and closure policy; R6 permits only
prefix-preserving append-only extensions of the current host paths and programs
during historical reconstruction, without changing live description or adoption
admission.

R3. `adoptFailedPhaseRepairRevisionC` is a pure eligibility transition, never
proof of Human consent or a successful check. Require unchanged Plan identity,
Phase, repository, repair limit, Phase checks, review classification and raw and
effective acceptance, a fresh Plan revision, every original Step exactly, and
exactly one nonempty new Step with every original Step identity as its exact
dependency set. The original Plan has fewer than 64 Steps. New scope can extend
beyond original Step ownership only within explicitly admitted current host
candidatePaths with an exact entry for every corrective Step ownership path and
its existing fixed native program. Mere descendant coverage by a host directory
entry refuses; staging authority is unchanged. Wildcard or whole-repository
fallback is prohibited. Current Step check policy selects and validates the
corrective checks. Before description or adoption, the current native closure
installation must validate and its fixed StepProgram must map the appended Step
identity. An unmapped identity refuses even with exact admitted files and checks.

R4. Choices are exactly `AdoptRepairPlan` and `Defer`. Both require independently
issued evidence purpose `goal-phase-failed-phase-repair-adoption`, actual issuer,
time, participant and reference, and exact `failedPhaseRepairAdoption` binding:
complete target plus answer(choice,rationale,participant,reference). Missing,
crossed, old or stale evidence refuses. Defer appends nothing. Passive preparation
uses family `GoalPhase`, kind `failed-phase-repair-adoption`, the actual generated
description and adoption input, and matching execution worker/reference. It
returns existing StartupImport rows; it neither provisions nor continues.

R5. Adoption saves one distinct guarded/CAS Direction through the fixed Advance
binding. Namespace is `<session>:ordered-failed-phase-repair-adoption:<DecisionRevision>@1`,
schema is `sm-workflow.goal-phase-failed-phase-repair-adoption.v1` for the unchanged
known-failed source or explicit `sm-workflow.goal-phase-failed-phase-repair-adoption.v2`
for R8, and CML edge
`failed-phase-repair-adopted` is Decision to Implementation. It retains the actual
target and Human reference, participant and rationale. No File/Git/SBT/Submit/
Advance effect executes. Neither public call itself constitutes a Human answer.

R6. Retained traversal proves the original real failure and exact contiguous
Direction successor. Both recorded and current candidatePaths vectors must be
non-null, nonempty, unique, at most 256 valid repository-relative paths and free
of wildcards or null entries. The current paths must start with the complete
recorded vector in the same order, preserving every original fixed path ordinal.
Only valid exact path entries may be appended; removal, reordering, insertion
within or before the prefix and replacement refuse. An appended path need not
be a descendant of an original path: the explicit current host, current Plan
admission and fixed program capabilities independently govern live scope.
Expanding the current host requires an exact candidatePaths entry plus its
matching fixed stage program and capability for the appended ordinal; directory
coverage alone remains insufficient.
Both recorded and current closure policies must validate,
have exactly the same identity and revision, and the current programs must start
with the complete recorded programs in the same order with the same Step identities
and ordinals. Only a suffix of future programs may be appended. Removal, reordering,
insertion within or before the recorded prefix, changed identity or revision,
changed existing mappings, duplicates and null or invalid policies refuse.
Historical target reconstruction uses the complete recorded candidatePaths and
closure policy with the actual current closure authority and current acceptance
and checks. The complete reconstructed target
must still equal the stored Direction target. Live execution continues using the
current declaration and its complete current policy. It applies the same pure transition, increments generation
once and clears active candidate/check/review/closure/decision/directive
projections. Original base, accepted Steps/commits/reviews, repository receipt,
repair counts and limits remain. Native history, completedCandidates,
priorReviews, findings and issues remain. Duplicate adoption refuses.

R7. A separate ordinary Advance issues ImplementStep for the appended corrective
Step. Existing repaired source may be selected through ordinary scope admission;
it need not be rewritten. Native source-history admission uses the actual
replayed Plan and exact current inspection cursor, so an adopted corrective Step
retains ordinary selected-source history normalization. Candidate/check, independent ReviewStep, local Step
commit, whole-Phase check, independent ReviewPhase and closure remain unchanged.
No implicit dirt adoption, custom launcher, arbitrary command fallback or new
storage registry is introduced. R8 validation recovery continues to recheck the
same retained candidate only.

R8. A separate closed v2 target has `source.kind: "terminated-timeout"`, never a
`knownFailedPhase` field. It binds actual Session, workflow identity/revision,
participant, Plan identity/revision, original base, exact candidate, original
validation source revision, current Decision revision, the unique check identity
and ordinal, final attempt ordinal, actual intent/result/final validation receipt
identities and revisions, `termination: "timed-out"` and `outcome: "incomplete"`.
The rest of the target binds the same complete original Plan, accepted prefix,
repair counters/limit, generation, current candidatePaths, fixed closure policy,
captured raw/effective proposal and selected corrective checks as R2–R7.
Complete canonical records reject unknown, null, missing and crossed fields.
The producer's typed TimedOut contract confirms termination; termination not
confirmed has its own `termination-unconfirmed` variant and refuses here.
No exit 143 conversion, stdout-based outcome inference or new producer adapter
is allowed. Partial stdout/stderr remain diagnostics in the original receipt;
the target binds that receipt instead of duplicating or completing its output.

Require the exact current unsuspended PhaseValidation Decision after all Steps
are accepted, exactly one whole-Phase check, and the exact actual candidate and
selection. Both terminal source variants share ordinary actual-attempt
reconciliation, including every contiguous predecessor, actual intent/result,
and the immediately adjacent final Decision receipt. Missing/UNKNOWN, launch
not started, duplicate/crossed attempt, cancelled, termination unconfirmed,
other incomplete, multi-check, Step, review, closure, pending repair and stale
frontiers refuse. Ordinary `repairable` classification and the R8 known-failed
target remain unchanged and exclude timeout. The distinct
`terminatedTimeoutPhase` extension in validation recovery R10 shares this actual
source proof and offers exactly `AuthorizeNewAttempt`, `LimitedRepair`, `Defer`.
AuthorizeNewAttempt preserves its same-candidate retry wire meaning under current
host permissions and configured deadline. LimitedRepair first requires exact
independent Human evidence through normal validation recovery, then returns
`accepted: true`, `limited-repair-preparation-required` without Store/history/Plan/
counter/Git/SBT mutation or retry Direction. The original Decision remains current
so this v2 source proof remains valid. This is bounded investigation/preparation,
never adoption of an unknown future Plan, retry authorization or check success.

Limited repair includes fixing known errors, identifying the test actually
executing at termination from actual start/end observations and retained output,
with diagnostic stack/process evidence when needed, and investigating/debugging
a suspected hang and fixing established causes. Timeout alone does not prove a
hang; the last printed test may already have completed. Report unknown test
identity or missing evidence honestly. Investigation may establish normal slow
execution or external wait rather than deadlock. CNCF owns execution and result
interpretation; no application regex parser or fabricated current-test identity
supplies evidence. Defer permits deeper investigation without retry.

After diagnosis/planning, call `describeGoalPhaseFailedPhaseRepair` with the
captured concrete proposal, then independently authorize
`adoptGoalPhaseFailedPhaseRepair`. Only the adopted Plan issues a normal
corrective Step on separate ordinary Advance. No Decision-to-Decision selection
event or duplicate repair executor is added.

Preparation accepts the actual v2 description through the existing purpose and
exact target/proposal/answer binding. An old Human direction for another target
or choice supplies no authority. Defer writes nothing; adoption writes exactly
one CAS Direction and has no Git/File/SBT/Start/Submit/Advance effects. A separate
ordinary Advance reconstructs the original timeout at its historical Decision,
requires v2 and its exact adjacent successor, and uses the existing pure
failed-phase-repair-adopted transition. Generation increases exactly once;
original timeout/result/history, accepted Steps/commits/reviews, base and repair
counters remain immutable. General Decision resumption is unchanged.

## Executable examples

E1 (`GoalPhaseOrderedProgressionSpec`, `GoalPhaseFailedPhaseRepairAdoptionSpec`)
preserves the accepted prefix/base/counters and rejects changed identity,
revision, accepted Steps, acceptance, policy or dependencies. Only the new
generation requests the exact corrective Step.

E2 (`GoalPhaseFailedPhaseRepairAdoptionSpec`) rejects incomplete/extended/null
new targets, missing/invalid closure policies or unmapped corrective Step
identities, directions and evidence scope; passive preparation binds the exact
proposal, closure policy, choice, participant and reference. Pure retained-path
and retained-policy examples return both complete original vectors for unchanged
and append-only hosts, preserving historical path ordinals. They refuse crossed
order, removal, insertion, replacement, duplicates and invalid/null/empty or
overlong paths, absolute/traversal/wildcard paths, and crossed
identity/revision/mappings or invalid/null closure policies.

E3 (`NativeGoalFailedPhaseRepairAdoptionCliSpec`) uses a new isolated native
Store, fixed real Git/SBT UoW, actual failed whole-Phase Test, independent fixture
evidence, reopened traversal and ordinary continuation. It includes explicitly
selected scope outside original Step ownership and unrelated dirt. Exact-scope
proposals with unmapped corrective identities and crossed closure policies
refuse without history writes. After the real persisted Direction, a changed host
corrective ordinal refuses without history writes or rerunning the failed Test.
A changed candidate-path prefix also refuses on fresh ordinary CLI Advance before
history writes, SBT or repository mutation. Restoring the original mappings and
path prefix, appending the exact `src/main/scala/CompileConsumer.scala` entry with
its matching fixed stage program and capability, and appending a future third
StepProgram permits fresh CLI traversal, corrective Compile, review, commit and
whole-Phase Test. The stored Direction still retains its original complete
candidatePaths and closure policy. This proves historical replay and the existing
selected corrective scope under the extended host; it does not execute a new
future Step. Fixture
evidence claims no production Human provenance. Existing installed CLI switches
select the same isolated scenario; no product Phase or existing Session is used.

E4 (`GoalPhaseInterruptedValidationRepairSpec`) supplies typed specification-owned
timeout, cancelled, unknown, unconfirmed and exited-143 observations. It proves
closed classification, unchanged no-retry behavior, ordinary adjacent attempt
reconciliation, distinct canonical v2 wire, strict source/receipt/participant
bindings and passive preparation. These inputs claim no production termination
attestation. E1 also checks the existing pure corrective transition for an
incomplete timeout without widening generic Decision resumption. E2 retains
v1 schema/constructors/wire and rejects crossed v2 decoding.

E5 (`NativeGoalFailedPhaseRepairAdoptionCliSpec`, name containing `timeout recovery choices` and `terminated timeout`)
uses a NEW isolated native Session and real ordinary UoW/SBT Test. A real failed
Test emits its actual diagnostics, then its fixture-owned task waits finitely
for 180 seconds; a configured 120-second execution deadline returns actual
TimedOut/incomplete. No result is mocked and no existing incident is rerun.
Before capturing a corrective proposal, the actual timeout description offers all
three recovery choices. Normal passive preparation/import supplies independent
LimitedRepair Human evidence and public recover returns diagnosis/planning scope
and the existing proposal/adoption operation guidance. Native history/revision,
retained receipt/output, source/build bytes, Git tip/reflog and SBT count remain
unchanged. The known failed-Test diagnostics and finite fixture wait then support
a concrete corrective proposal without fabricating a hang diagnosis.
Describe/prepare/Defer/new Human/adopt preserve files, Git tip and SBT count;
adopt adds only one adjacent v2 Direction. Read-only validation recovery
description succeeds with `terminatedTimeoutPhase` and no `knownFailedPhase`;
E5 selects LimitedRepair without authorizing a same-candidate retry and continues
through separately evidenced corrective adoption. Old-choice Human scope, crossed target and duplicate adoption
refuse. Immediately after adoption,
before a new corrective assignment is issued, submitting an altered payload with
the genuine original accepted `recovery-implementation@1` reference refuses:
the historical result has no pending assignment. History/revision, source, Git
tip and SBT count remain unchanged. This does not classify a fresh business result
for current pending work as historical by its prose.
A separate Advance issues the corrective Step; its actual source change, real
Compile, independent ReviewStep, local commit and new whole-Phase Test reach
independent ReviewPhase with the original accepted prefix and timeout receipt
retained. Reopened CLI traversal uses the saved v2 Direction. The existing installed
executable switch selects this same isolated scenario; fixture evidence claims
no production Human provenance.
