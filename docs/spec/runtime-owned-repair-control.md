# Runtime-owned repair control

Date: 2026-10-25 (Asia/Tokyo)
Status: adopted target contract; runtime implementation and live adoption pending
Design: [repair ownership](../design/runtime-owned-repair-control.md)
Delivery: [bounded correction](../phase/runtime-repair-control-correction.md)

## Ownership and ordinary continuation

sm-workflow owns continuation, convergence assessment, counting and stop/decision
transitions. AI diagnoses failures, proposes repairs and returns semantic results
through issued work. Skills/callers transport those results and actual Human
requests; they must not implement a competing repair controller in prose, scripts,
proposal files or handoff rules.

A terminated, attributable validation failure in an authorized repair scope enters
ordinary repair while the runtime policy allows it. It does not require approval
for each failed check or each corrected fixture. Unqualified artifacts must not
be adopted, but that restriction does not prohibit repairing them. A failed test
alone, several files, an executor replacement or a corrected preparation does not
establish divergence or new authority. Preserve collection of live work and
reconciliation of unknown effects; this contract never authorizes blind replay.

For the first whole-Phase check failure, a completed Plan under the adopted
`sm-workflow.repair-trend.v1` policy enters the same Plan's ordinary Release repair
when the actual check terminated with a known failed exit and allowance remains.
Every accepted Step, original base and Plan revision remains unchanged. This
does not append a corrective Step, revise the Plan, retry the original check or
reserve an additional window. One admission reserves one ordinary batch; reads,
repeated Advance and retained reconstruction reuse the same durable work and
count. A coarse failed check supplies no complete comparable finding catalog.

Historical `failed` validation outcomes still reconstruct their original
Decision. A separate pure native receipt, under
`sm-workflow.initial-phase-check-repair.v1`, appends the guarded
`initial-phase-check-repair-admitted` Decision-to-Repair transition. It binds the
current Decision, original candidate/check references, adopted policy, limits,
pre-counts and exact repair directive. Its source revision and normal native CAS
permit only that adjacent admission, including when a separately retained policy
adoption precedes it. Existing validation receipts are never rewritten.

This first repair precedes independent Phase review. Its public check-failure
context retains the original actual `failed` diagnostics and the exact optional
`initialPhaseRepair` admission reference; its review context is absent. Legacy
contexts retain absent defaults. A supplied reference alone grants no authority:
the runtime proves the native prefixes and exact expected issued Request. No
prior PhaseReview or review acceptance is manufactured. A fresh candidate,
selected whole-Phase checks, independent PhaseReview and normal closure remain
mandatory after repair. A newer Phase validation projection must retain its own
identity and diagnostics when an older Step has evidence or formal assessment.
The initial admission remains native provenance across completed repairs and
fresh checks until the first actual PhaseReview. A later ordinary failure in
that chain carries its own actual `repair-required` or `repair-limit-reached`
payload and the same original admission reference. Normal counting, limits and
unknown-outcome exclusions still apply. Once an actual PhaseReview exists, the
existing review-owned repair context is authoritative.

No-policy, exhausted-budget, timeout, unknown, incomplete, output-truncated,
stopped, uncertain or admission-failed results do not select this admission.
Policy adoption keeps its existing public eligibility: this correction does not
open policy adoption on a legacy no-policy failed Phase Decision.
Pending repair, document decisions, accepted Phase review, recording, closure
intents and Release commits keep their existing owning routes. The existing
active Release repair route after an actual integrated review is unchanged.

Use existing authorization for the same purpose, target, operations and effects.
An actual new scope/effect, explicit human restriction, unresolved semantic
decision, runtime convergence stop or exhausted repair window retains its genuine
decision route. An AI-authored "one run only/no repair after failure" clause is
not an independently requested human restriction. Broad approval of a proposal
must not be used to manufacture that restriction again after the user has directed
ordinary bounded continuation. Retain the historical proposal and decision.

## Convergence and limits

Use the existing repair state and actual validation/finding history. At repair
completion, assess a bounded recent trend of attributable unresolved findings,
failure recurrence, regression and oscillation. Single temporary expansion and
single failure do not establish divergence. Continued improvement permits the
next required repair below the bound; sustained divergence stops automatic repair
through the runtime's typed decision route before reaching ten. Sustained absence
of progress is evaluated by the same named/versioned runtime policy. Exact window
and thresholds belong to that policy/configuration, never a caller prompt.

AI PROGRESSING/STALLED/REGRESSING reports are evidence, not transitions. Explicit
AI BLOCKED is returned through the existing decision-needed result and interpreted
by the runtime. Insufficient observations remain unknown; do not create an approval
or extra AI call merely to obtain a convergence label. Reuse existing evidence,
without new resource scans, digest gates or mandatory per-fix reviews.

The normal automatic repair window has a hard ceiling of **10 batches**. Reserve
one cycle when dispatching a repair batch; diagnostics, reads, validation commands,
review calls and result collection do not each consume another cycle. Hygiene and
minor fixes still consume the automatic count; retain the existing ordinary-count
exemptions. Successful batch ten may finish normally; batch eleven is not issued
automatically. Worker/provider replacement does not reset or enlarge the window.
An explicitly human-selected smaller allowance remains visible; callers must not
invent a smaller allowance such as 3. An explicit new continuation window retains
its existing Human semantics and all cumulative history. Policy adoption itself
does not open a new window or refund either count.

### Minimal runtime policy v1

The corrective implementation selects the named policy
`sm-workflow.repair-trend.v1`. Its bounded window is the latest four comparable
attributable failure observations for the same Step or Release scope. Each
observation retains the actual check/review reference and its finding set;
diagnostic calls and AI convergence labels are not observations of execution.
The generic sentence identifying a failed SBT check is not a complete unresolved
finding catalog. Repeated failure of that check alone cannot distinguish
improving diagnostics from a stall; this coarse case retains unknown trend and
the ordinary automatic budget. No extra AI classification call is required.
Four equal nonempty sets establish sustained recurrence; alternating distinct
sets A/B/A/B establish oscillation; three successive strict set expansions
across four observations establish sustained regression. These are runtime
decision reasons. Other changes, a single temporary expansion, missing evidence
or fewer than four comparable observations do not establish divergence.

The automatic dispatch ceiling is the smaller of the selected allowance and
ten. Retained legacy Plans with larger limits remain readable and their original
selected limit is not rewritten during decoding. New native Starts capture the
policy version; old Starts without that field retain their historical replay
semantics until a separately recorded public policy adoption. Policy adoption
and a Human-selected new repair window are distinct transitions. Adopting policy
does not reset consumed counts; a genuinely selected new window retains its
existing Human semantics and cumulative native history.

The public adoption direction distinguishes correcting a caller-selected limit
to ten from retaining an explicit smaller selected limit. Neither a description
nor an absent provenance field proves what happened in unseen caller text. A
restoration association identifies the actual producer work and its suspended
consumer as business context; it grants no consumer access, state transition,
count change or replay authority. Qualification and actual consumer adoption
remain separate evidence.

## Runtime restoration

### Transition before RRC delivery — 2026-10-25 correction

The following common-controller design is the target, not a prerequisite for
already-authorized restoration. Until delivered, one producer delivery owner
continues the bounded restoration through the existing applicable standalone
execution facilities and their actual permissions/limits. Missing RRC is tracked
in parallel and must not cause a new blanket stop, one-run approval request or
consumer-to-producer design-freezing loop. Preserve existing real stops and
unknown-effect settlement; do not implement a competing skill/controller or claim
standalone execution proves RRC support. Normal Phase validation still belongs to
the native workflow. See [outcome-based delegation](native-host-execution.md#outcome-based-delegation).

The broken-runtime restoration exception remains an execution transport exception,
not permission to create a caller-owned approval/repair loop. Assign restoration
to an executable producer work scope using the existing repair controller, checks
and execution facilities. Retain its link to the suspended consumer and its own
actual repair accounting; never debit or reset the consumer silently. If no such
route is executable, expose the specific capability gap to the already-authorized
producer work, without blocking the transitional route above. Do not claim a typed
restoration Operation exists until delivered.

Pass actual qualification failures through repair, focused validation and existing
conditional review before publication/adoption. Preserve authorized publication
scope; passing checks does not confer additional publication rights. Return to the
original consumer work only through normal supported continuation.

## Existing Sessions and observations

Adopt policy through a public runtime transition preserving Session/work identity,
accepted results, cumulative and active counts, original limits, all failures and
unknown outcomes. The current user direction authorizes correction of accidental
caller-selected limits and independent stop clauses within the existing scope;
do not request the same authorization again. A change from accidental limit 3 to
10 preserves the consumed count (for example 1 remains 1) and records the old/new
policy and attributable direction. Truly human-selected restrictions remain intact.
Do not edit stored Sessions, restart, resubmit accepted work, or rerun valid checks
just to adopt this policy. If the public transition is missing, implement that gap
in the authorized producer scope rather than inventing a consumer bypass.

The public adoption Target uses the Record projection that omits null-valued
object fields, including an absent review on a completed Step with valid formal
admission. Its decoder requires the complete projected Target after normal field
name normalization; supplied nulls, unknown fields and missing required fields
remain invalid. Retained Target, Direction, evidence binding and ClosedStep codecs
keep their existing format, including `review: null`; public projection does not
change saved history or the exact described-target equality required for adoption.

Project stop/decision provenance using existing result/context fields: actual
runtime Human request, concrete authority/scope delta, capability gap, or observed
caller-added restriction. These are semantic distinctions, not invented shipped
enum values. Where caller information is unavailable, record unknown rather than
claiming runtime detection of unseen chat text. Reuse the existing
[recovery observations](recovery-observations-and-cause-resolution.md) owner to
record available facts in Journal/Evidence/Finding without extra AI calls, a
second ledger, retroactive approval fabrication or an approval-as-detector gate.
Recovery success and cause resolution remain separate.

## Observed implementation baseline

On 2026-10-25, Plan defaults to 10 but accepts explicit limits up to 64; the
Control Center plan uses 3. Repair dispatch/count limits exist. Fix assessment
retains advisory statuses, while complete trend-based control and restoration
integration are not established. This contract supersedes the permissive limit
and caller-owned stop behavior as a development target, not a runtime PASS claim.
