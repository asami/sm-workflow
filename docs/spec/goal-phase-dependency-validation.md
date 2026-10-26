# GoalPhase dependency validation

An explicitly revised source uses the separate versioned
[dependency source revision](goal-phase-dependency-source-revision.md) contract.
It retains native V1 admissions and accepted-prefix history, and qualifies the
new source with fresh checks. It does not reinterpret a V1 capture at a new base.

## Authorization correction — adopted 2026-10-25

[PA-01..07](goal-phase-dependency-scope.md#purpose-based-authorization--adopted-2026-10-25)
supersedes DV-01/02/07 file-list authorization semantics below as a planned runtime
correction. Fixed binding identity/base/check operations remain stable; the current
concrete path inventory can advance through admitted same-purpose scope updates.
Do not require a new Human answer solely to extend the exact union of paths.
Capture, review and validation consume that admitted revision and actual changed
files; preserve previous receipts and validate affected changes. A repair must
still refuse genuinely out-of-boundary work and unauthorized binding/operation
replacement. Regression D below must distinguish those cases from a necessary
new same-purpose file. Existing source/test evidence is retained, not reclassified
as proof of this new behavior. See the [delivery checklist](../phase/purpose-based-authorization-correction.md).


Status: source implementation contract; compilation, native tests, generated ABI,
installed runtime qualification and the caller's real Phase acceptance are separate.

## Requirements

- DV-01: one current unclosed Step may explicitly select one fixed host binding
  through ordinary semantic Submit. A binding declares dependency Component,
  repository, logical Git workspace, absolute root, branch, exact literal paths,
  base selection and finite typed SBT checks. It supplies no effect permission.
- DV-02: the native history owner accumulates actual ApproveDependencyScope
  completions for this Step. Every selected proposal and genuine answer reference
  must match that history. The binding's paths must equal the exact union of the
  selected proposals for the same requester/dependency/Usage Contract. A second
  classifier approval may extend that union; neither approval is silently widened.
- DV-03: semantic input supplies references only, never a Git snapshot, result or
  success assertion. A selected host base is checked against actual Git objects
  and described as adoption of existing work, not a retrospective Start capture.
  The primary original base, Plan, Goal, Session and repair counters do not change.
- DV-04: candidate capture uses existing typed Git and ordinary native intent/result
  persistence. Exact permitted staging precedes IndexTree/PendingPaths/PendingDiff.
  Root, branch, HEAD, base tree, staged tree and supported worktree state must agree.
  A result-less effect is unknown and is never automatically replayed.
- DV-05: checks run through the existing typed SBT UoW. Every attempt is bound to
  repository/workspace/base/candidate/binding/check/operation, with before/after
  freshness checks. TestOnly requires verified executed suites, not merely exit 0.
  Original rejected, stopped, failed and unknown attempts remain in native history.
- DV-06: primary and dependency results are projected separately and jointly gate
  validation success. Independent review receives actual dependency diff/results;
  review completion and closure recheck dependency freshness. No dependency commit,
  publication, network update or Phase acceptance follows from a check receipt.
- DV-07: selection is immutable once adopted for the Step. Omission cannot remove
  it; later host defaults cannot replace it. A repair can capture a fresh candidate
  while preserving selection, base and all prior attempts. Recovery uses the
  existing explicit direction with the complete dependency subject; unknown effects
  are not converted to safe retries. No repair counter is reset or refunded.
  Inherited review captures and results in RepairStep are historical evidence,
  not a requirement that the repair keep the old candidate bytes or index.
  Issuing and completing RepairStep allow unstaged admitted corrections; ordinary
  candidate capture then stages, observes and validates the fresh candidate under
  the same binding, approvals, base, ownership and effect permissions. Issuing
  ReviewStep/ReviewPhase and admitting review completion still check the exact
  reviewed dependency candidate; validation and closure retain strict freshness.
- DV-08: legacy records omit dependency evidence and retain their old meaning.
  Absence is never dependency success. Null, unknown fields, duplicate references,
  crossed assignments, wrong repositories/targets/paths and missing grants refuse.

The workspace must remain under its ordinary exclusive work ownership while a
check runs. Before/after observations are not an atomic filesystem snapshot or a
hermetic assertion about every transitive build input. Host configuration and
grants are installed by their existing owners, not edited by semantic work.

## Review repair regression boundary

`NativeGoalDependencyValidationCliSpec` uses ordinary two-repository CLI fixtures:

- A: accepted review-repair, issued RepairStep, an unstaged admitted correction,
  accepted repair Submit, then native fresh staging, capture, selected checks and
  a new ReviewStep. Old review evidence, exact admission, base and repair charges
  remain attributable.
- B: a correction made after the accepted verdict but before RepairStep issuance
  is not rejected against the historical review capture.
- C: the existing review-completion and closure drift specifications still refuse
  a changed reviewed candidate without rerunning checks or committing.
- D: fresh capture after repair still refuses an unapproved path; repair Submit
  still refuses a replacement binding. Existing root, branch, HEAD, base tree,
  workspace and SBT permission refusals remain selected in the same suite.

The new positive fixtures correct the tracked executable mode of `value.txt`;
its exact expected content and the existing Compile/Test selection are unchanged.
Authored source scenarios are not executed test results or installed acceptance.

## Candidate recovery provenance (CC51-S1 additional scope)

The candidate's optional `dependencyOrigin` names the original stage source, actual
observation source and actual observation-result record revision. It references
native receipts; it does not rewrite a capture's `sourceRevision`. Legacy normal
captures without this field retain their original same-source interpretation.
When referenced across a direction, legacy completed receipts remain byte-for-byte
unchanged and the new candidate explicitly records their actual origin.

The existing candidate recovery target includes a strictly contiguous
`dependencyPrefix` of exact native receipt identities/revisions/payloads. It can end
before staging, after a recorded stage, at an UNKNOWN stage intent, after all stages,
at an UNKNOWN observation intent or at a completed observation result. Only fully
recorded staging can authorize a new observation. Other prefixes offer existing
same-Step `ReviseWork` or `Defer`. The pure direction path issues no Git effects.

A new observation uses the existing candidate Store with a distinct source namespace
and wrapper observation intent. Completed stage effects are referenced, not rerun.
Its dependency observation has a separate intent/result pair; later primary failure
retains that completed pair. Observation continuation binds both actual repository
snapshots, the immutable original target, all prior prefixes, diff budget and fresh
independent Human answer. Each effect owner rechecks actual freshness and its
existing grants. The native walker retains approvals, bases, counters, failed and
unknown receipts and uses the existing controller generation event for revision.

Source compatibility remains an ordinary caller migration: generated CML values get
no hand-authored managed-code defaults. Generic public-result adapters preserve
`dependencyValidation` through `publicSelection`; intentional legacy fixtures use
`None`. Source specifications require owning native qualification and independent
review before any installed or Phase acceptance claim.

## Accepted single-dependency prefix (DV-09..12)

These requirements extend the existing file-bound implementation baseline. They do
not implement the separate PA-01..07 authorization correction above.

- DV-09: public `Selection(binding, approvals)` and host `Binding` remain unchanged.
  The native completion owner may enrich its package-private `Admission` with
  optional `acceptedPrefix`. Its exact closed schema is
  `sm-workflow.dependency-accepted-prefix.v1`, with `sessionId`, `plan` (identity and
  revision), `step`, `closureRevision`, `candidate` (head and full index tree), and
  `paths`. The anchor names the immediate preceding genuinely accepted capture for
  the same dependency. Its paths are accumulated actual accepted changed paths,
  never a union of historical permissions. The original commit base remains fixed.
- DV-10: the existing contiguous native walker reconstructs every predecessor from
  its real semantic admission, stage/observation intent/result ancestry, successful
  selected validation, accepted independent review and observed Step closure. Only
  that walker constructs the ephemeral resolved chain. Decoding a valid anchor,
  selecting a host binding or supplying a PASS does not establish trust. Missing,
  skipped, forked, failed, unreviewed, unclosed or crossed predecessors refuse.
  Session, Plan, primary base, Component, requester, repository, logical workspace,
  root, branch and original dependency base must remain attributable. The chain is
  bounded by the existing 64-Step Plan and 128-path cumulative inventory limits.
- DV-11: current same-Step approval admission remains exact and separate. Let P1
  be accumulated actual predecessor changes, P2 current admitted paths and T1 the
  accepted predecessor tree. Existing pending state may coexist only in P1 union
  P2. Stage only P2 through new current native intents. Observe full IndexTree T2,
  but capture changedPaths/diff against T1 using typed PendingPaths/PendingDiff.
  The complete incremental delta, including additions, deletions, modes and both
  renamed endpoints, must lie in P2. Also check the complete original-base pending
  inventory against the cumulative union. Inherited-only paths cannot authorize a
  new edit. Bind the tree and diff to matching before/after observations and retain
  ordinary staged/worktree restrictions, grants and output limits.
- DV-12: every selected current check verifies the resolved chain and exact latest
  full index, incremental paths and diff before and after its observed process.
  This includes primary-only Step and Phase checks with no new dependency capture.
  Persist actual failed, stopped and uncertain receipts as before. Independent
  review receives all original historical payloads plus the current incremental
  capture and selected results; overlapping edits require current integration
  judgment. Review issue/completion, Step closing and Phase review/closing use the
  same chain validator. A proved successor replaces its ancestor's live-tree
  comparison only after all native edges have been reconstructed. Without a valid
  successor the historical tip retains ordinary full-tree freshness. Omitting a
  later dependency selection cannot hide changed dependency bytes or inherit PASS.

Legacy Admission encoding remains exactly `binding` and `approvals`; absent prefix
retains its original commit-to-index capture interpretation. Omit the extension
when absent. Explicit null, unknown members, unknown versions and incomplete or
malformed extensions refuse. Extended records require the supporting runtime; safe
downgrade to an older closed decoder is not promised. Generated public constructor
arities, CML services/Operations and ABI declarations are unchanged.

Same-Step repairs preserve the exact admission and anchor. Historical repair
rationale remains attributable while fresh current correction is captured and
qualified through the ordinary owners. Before initial adoption a pending current
Step may use a separately authorized current host binding at ordinary Submit; an
already adopted binding cannot silently change. Neither historical approvals nor
new inventory evidence manufacture current same-Step scope permission.

Existing candidate recovery carries the Admission transitively. Description and
continuation snapshots admit cumulative pending paths while retaining the current
binding as the sole new staging scope. AuthorizeNewObservation still requires
completed stages and supported staged/worktree state; ReviseWork and Defer retain
their existing eligible frontiers. The effect owner resolves the real prefix again
and checks the incremental delta before adopting a capture. Preserve every original
base, source revision, CandidateOrigin, direction, intent/result, failure, unknown
effect and repair count. No completed stage is blindly replayed.

The owning parent must separately deliver and qualify the runtime, apply its
authorized host mapping/check/grant change and use the actual retained public
Session request/result. There is no public prefix parameter, storage migration,
replacement Session, base rebasing, dependency commit or automatic publication.
The isolated authored specifications establish no installed or live Phase result
until their normal validation and independent review have actually completed.
