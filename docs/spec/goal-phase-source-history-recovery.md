# Source-history recovery through the ordinary Human Decision

## Same-authority comment recovery — adopted 2026-10-26

Status: target contract and delivery plan; implementation/installed qualification
remain pending. This section supersedes the legacy unconditional Human escalation
for missing source-history results below. It does not authorize a caller to bypass
a pending runtime question. [Delivery and adoption](../phase/source-history-recovery-result-collection-correction.md)
include the existing Phase 6.6 D3 frontier.

SHR-01. A local source-history comment update already authorized for the same
Component, purpose, acceptance and operations is a repeatable normalization, not
an arbitrary external effect. A missing completion receipt alone MUST NOT require
`AuthorizeNewAttempt`. sm-workflow owns classification and continuation. AI and the
skill do not infer success/non-execution, grant permission or run a parallel repair
loop. Generic unknown Git, publication and other effects retain their own contracts.

SHR-02. Normalize only the admitted comment area under the selected provider/rule
and effective date already associated with the operation. Reapplying that same
normalization must have no additional change: N(N(text)) = N(text). Preserve source
code, unrelated comments and user edits. Reconcile only the affected file(s) through
existing managed File/Resource operations, not process-based reads or repository
scans. Do not introduce hashes, ownership locks, per-read checks or a new store.

SHR-03. At ordinary continuation, read the affected file and apply these decisions:

- A retained attributable terminal result exists: consume/reconcile it without
  another write or execution.
- Current comment satisfies the intended normalization: append a current
  observation and continue without writing. This establishes the current
  postcondition, not who wrote it or whether the old interrupted attempt succeeded.
- Current comment can be normalized unambiguously within the same authorized
  boundary: perform the bounded normalization and record the actual result through
  existing UoW/File and history owners; continue ordinary candidate processing.
- Malformed/ambiguous comment, unavailable file, changed rule/authority or actual
  conflicting content: return the concrete diagnostic. A known failure before any
  write is not UNKNOWN. Ordinary in-scope correction remains ordinary work; ask
  Human only when a genuine intent/authority decision is required. Do not delete
  ambiguous comments by guessing which one is authoritative.

The runtime must ensure the old execution has ended before another write; reuse
its execution lifecycle, not a new polling or locking mechanism. Use the existing
managed replacement facility where applicable so partial writes cannot be reported
as success. Persistent I/O failures return their actual outcome; no busy retry or
silent infinite recovery loop. Another interruption retains its observations and
resumes from the current postcondition through the same owner.

SHR-04. Retain the original UNKNOWN intent and successful prefix unchanged. Append
reconciliation/current normalization outcomes with references to that intent.
A postcondition observed now does not rewrite the old attempt as NotExecuted or
Success. Mechanical continuation consumes no semantic repair cycle and refunds
none. Actual semantic RepairStep assignments remain counted by the existing
controller (convergence and ten-cycle ceiling), not by a new comment-repair budget.
Ordinary candidate capture and applicable validation follow changed content;
old PASS results do not certify new source bytes.

SHR-05. Provide a public same-Session adoption/continuation for legacy pending
source-history Human questions, including Human226 in Phase 6.6. Use retained
existing authorization and actual user policy direction, record why the pending
question is superseded by reconciliation, and preserve the original question
unanswered in historical evidence. Do not fabricate an AuthorizeNewAttempt answer,
edit storage, drop an obligation, restart or replay accepted Submit. A previously
explicit Defer/Cancel or revoked authority is not undone by automatic recovery.
No additional per-file approval is required just to adopt an already authorized
policy. Until this public route is delivered, report the specific runtime gap and
respect the actual installed decision; do not ask again for direction already held.

SHR-06. Recovery success and cause resolution remain separate. Record the original
failure, reconciliation branch, actual result and unresolved cause through existing
Journal/Evidence/Finding owners without extra AI calls. A duplicate-comment input
corrected for this attempt does not prove the comment producer defect is fixed.
See [recovery observations](recovery-observations-and-cause-resolution.md).

## Legacy explicit-Human route and retained evidence

The remaining sections describe the previous implementation and its historical
qualification. Their unconditional UNKNOWN-to-Human policy is superseded by
SHR-01..06 for this bounded comment operation once delivered/adopted. They remain
relevant to actual installed legacy behavior and provenance, not a reason to add
a caller-side approval gate to the new contract.


## Normal public continuation

Call the existing `GoalPhaseService.advanceGoalPhase(sessionId)` for the existing
Session. If native candidate history contains the exact unresolved
source-history intent after zero or more contiguous successful normalization
pairs, the next ordinary Advance issues a durable `Human`
Decision. Its `resultType` is `GoalPhaseOrderedDecisionAnswer`, its
`submitOperation` is `submitGoalPhaseOrderedDecision`, and its offered choices
are exactly `AuthorizeNewAttempt` and `Defer`. The question displays the full
optional `sourceHistoryRecovery` target and warns that the old normalization
effect remains UNKNOWN and another attempt may repeat it.

Repeated Advance returns the same saved question. It does not repeat the source
effect or create another inspection, intent or Decision. The saved question
contains the actual preceding accepted implementation or repair result, original
Plan/base, and repair counters. Its source is the original unresolved intent record,
after the retained inspection, rather than the earlier implementation successor.

Submit the supplied typed answer, actual answer reference and independent Human
execution evidence through ordinary `submitGoalPhaseOrderedDecision`. The
existing independent GoalHuman host evidence purpose and v7 codec bind the full
captured recovery target as part of the ordinary question. Missing, malformed,
crossed or changed target evidence, a stale reference, and a legacy attestation
without that target cannot authorize this answer. `AuthorizeNewAttempt` is not
offered or admitted on ordinary non-recovery Human questions. The normal
`prepareEvidence` GoalPhase kind `human` remains passive preparation; the host
owns independent Human provenance and normal import selection.

The normal host procedure is:

1. Preserve the actual issued Advance response as the request file. The actual
   external Human channel supplies the literal selected answer and rationale,
   and the actual answer artifact reference. The trusted host supplies the
   actual participant, worker, issuer, `occurredAt` and recorded execution facts.
2. Call existing `NativeHostPreparationService.prepareEvidence` with family
   `GoalPhase`, kind `human`, the whole opaque request, result, reference and
   `executionEvidence` files, and those actual host fields. The normal program
   normalizes the selected question and generates the scoped v7 binding and
   import rows.
3. The host provisions and selects the returned rows through the existing
   independent normal StartupImport. Forward the typed answer, reference and
   `executionEvidence` to exactly the issued `submitOperation` and
   `submitSessionId`, then explicitly call ordinary `advanceGoalPhase`.

The parent AI must not edit the question target, manufacture execution evidence,
issuer, time or consent, or construct or adjust import rows to make approval
pass. Neither a skill nor AI supplies the channel facts. If genuine channel
facts are absent, stop with the Human question pending. This repair adds no
Human ingress API, provenance source or host dispatcher, and supplies no manual
describe/recover fallback. The existing `scripts/native-goal-command.py complete`
now owns this finite ordinary Goal Human connection when explicitly supplied
the actual answer/reference/execution submission and `--human-provenance` file.
It calls passive preparation once, selects the exact raw preparation response
through the fixed canonical runtime configuration argument
`--textus.import.data.file=<absolute path>` on the issued Human Submit only.
CNCF admits its Args snapshot under the actually selected subsystem. The caller
follows actual accepted Submit with one ordinary Advance without that argument.
It changes no persistent host
configuration, creates no import rows and authenticates no live channel.
Missing provenance, rejection, uncertainty, cancellation or changed routing
stops without Advance/retry. See the [finite host contract](native-goal-command-task.md#complete-a-supplied-ordinary-goal-human-answer)
for the exact supplied shapes and retained preparation/Submit/Advance outputs.

The isolated installed CLI fixture provisions test-owned Human metadata.
Those tests prove program transport, binding generation and admission, plus real
SBT execution; they do not prove live host Human capture, authenticity or real
production consent.

The opt-in installed example `complete the ordinary recovery Human through the deployed host program and installed CLI without manual import selection`
requires explicit `smworkflow.spec.installedCommand` and
`smworkflow.spec.hostCommandScript` pointing to the absolute installed CLI and
canonical shared Python program. A fresh fixture supplies explicit test-owned
channel records, corrects its own source and invokes that deployed program
through managed uv. The program owns preparation and fixed import selection.
Assertions retain the original UNKNOWN intent and accepted history prefix,
Plan/base/counts, unchanged configuration and exactly one real typed native SBT
check before the next independent ReviewStep. Those fixture records establish
program plumbing and admission only, with no claim of live Human consent.

Managed atomic completion persists only the Human answer and its actual
successor. Both choices have declared lifecycle self-transitions at
`InspectCandidate`; neither changes repair counters or executes File, Git, SBT
or Advance. `Defer` retains a stopped path with no fresh question or retry.
After `AuthorizeNewAttempt`, the host explicitly calls the next ordinary
`advanceGoalPhase`. Native traversal validates the contiguous original
inspection/intent and issued Human pending/completion chain, moves its cursor to
the actual Human completion successor, and runs the existing candidate and
validation progression. No diagnostic recovery operation or mechanical recovery
direction is selected as a fallback.

The original missing result stays UNKNOWN. Accepted work, Session, Goal, Plan,
base, history and counts remain retained; no reStart or reSubmit is required.
The consumed recovery allowance survives same-Step document transitions and
resets only at actual closure into the next Step. A newly unknown attempt stops
without another recovery Human. Other stops, including staging, incomplete or
unsupported history prefixes, whole-Phase inspection, changed provider rules, and unavailable Human
installation, keep their fail-closed behavior.

Human Submit reconstructs its pending question from retained contiguous history,
including prior completed-Step commits and checks, without live Git observation
or replaying prior work. The historical-target reader does not require current
revision to equal the old intent after suspension; initial issue still requires
that exact original current frontier. Ordinary Advance continues to require its
normal Git authority and actual commit verification.

An already completed recovery Human may retain the candidate paths captured by
its actual saved typed question when the current declaration extends that exact
ordered vector with distinct safe paths. The historical reader first derives
the original target and frontier from actual native receipts, then requires the
exact contiguous Human suspension and its completed, non-suspended
`InspectCandidate` successor before selecting the captured target. A saved
unanswered question or absent question continues to use the full current
declaration. Reordering, replacing or removing an original path refuses.
Reconstruction changes only the declaration's candidate paths to that captured
vector and requires complete target equality; every other current declaration,
source-history rule, Plan, participant, base, inspection, completed prefix and
native adjacency check remains exact. Malformed or crossed completed history
refuses without falling back to another target.

This historical read grants no new execution authority and chooses no Human
answer. The ordinary Human port still independently verifies the actual typed
completion, answer, exact input, result, repair progress, target and genuine
Human provenance. Initial issue, pending completion, new recovery admission and
fresh candidate effects continue to use the complete current declaration. No
stored question, payload, native result or history cell is rewritten.

## Lower-level explicit diagnostic capability

The public `GoalPhaseService` exposes
`describeGoalPhaseSourceHistoryRecovery(sessionId)` and
`recoverGoalPhaseSourceHistory(input)`. These Operations preserve accepted
implementation and the original unknown source-normalization effect. They
neither perform normalization nor infer its outcome from missing receipts or
stderr. A newly authorized attempt may repeat the old effect.

## R1 — Exact eligible retained source

Description reads the normal generated Operation's selected Session through
managed storage. Only a selected Step at `InspectCandidate` is eligible, with
this contiguous history and its corresponding normally encoded receipts:

1. The original implementation successor at source revision S.
2. Saved `ordered-candidate:inspection` at S + 1.
3. For each index i from zero through n - 1, its normally encoded
   `ordered-candidate:source-history-intent-i` at S + 2 + 2i and actual
   `ordered-candidate:source-history-result-i` at S + 3 + 2i.
4. The unresolved `ordered-candidate:source-history-intent-n` at S + 2 + 2n.

The current frontier must still be the intent's next record. There must be no
source-history result for n, candidate, staging intent or staging result for S.
The runtime derives n from the contiguous original native receipts and history;
callers cannot select a later intent. Every prefix result must be `updated` or
`unchanged`, with exact matching source, Step, path, provider, style and date
provenance. Missing or excluded prefix outcomes remain ineligible. Missing,
crossed, duplicated or reordered references, payloads or native revisions fail
closed. Index and revision arithmetic cannot wrap.
Whole-Phase inspection, validation, review, closure, another partial candidate,
and a newly failed attempt following recovery are ineligible. The original
source-history payload codec and its legacy bytes are unchanged. Missing
results remain unknown, including a known historical interpretation failure;
this contract introduces no failure observation or NotExecuted outcome.

The complete target is a public Record binding Session, Plan identity/revision,
immutable original base, controller tip, definition identity/revision,
participant, selected Step, source revision S, frontier revision, deterministic
intent identity/revision and native intent record revision, host candidate and
owned paths, management paths, exact inspection payload, intent payload, and
currently resolved source-history rule. The retained intent's provider, style,
path and date provenance must agree. Legacy receipts do not retain the full
original rule: its provider identity is checked, while the complete currently
resolved rule is bound for this recovery decision. Public property-name
normalization rejects collisions. Description is read-only and grants no
authority. Description and recovery reconstruct prior completed Steps from
canonical retained closure history, without observing live Git. Ordinary
Advance continues to verify those actual commits through its normal Git path.

For n = 0 the original explicit target encoding and its existing Human bindings
remain byte compatible: no extension fields are emitted. Later targets require
`prefixContract = sm-workflow.goal-phase-source-history-recovery-prefix.v2`,
`intentIndex` and `completedPrefix`. Each concrete `CompletedPrefix` binds its
index, exact original intent/result identities and reference revisions, both
native record revisions, and both typed source-history payloads. All fields are
required; absence never becomes an empty later prefix or promotes an old answer
to later-intent permission. Ordinary opaque host question normalization retains
this entire target in the existing Human evidence binding.

## R2 — Direction is separate from execution

The generated description result supplies `decision-required`, the target,
`AuthorizeNewAttempt` and `Defer`, and recovery operation identity. It warns
that the old effect remains unknown and the new attempt may repeat
normalization. Correcting a malformed input alone does not grant a retry.

Recovery requires the exact current target, declared choice, rationale and
actual Human artifact reference. `Defer` preserves state. `AuthorizeNewAttempt`
appends exactly one managed mechanical transition at the current frontier,
using its own `GoalPhaseSourceHistoryRecoveryDirection` codec and deterministic
reference bound to S. The new record stays at `phase.InspectCandidate` with
`ordered-source-history-recovery:S` causation. Its payload retains the complete
target, actual reference, participant and rationale. CAS owns persistence;
there is no private store or direct database mutation. Stale, cross-session,
crossed-stage, missing, malformed or repeated authorization refuses.

Neither choice calls Advance, File, Git or SBT. Recovery submits no semantic
result, grants no successful check or review, resets no repair window, and
fabricates no effect receipt. Authentication and AI prose are not independent
Human consent.

## R3 — Independent host evidence

The optional declaration installation is `sourceHistoryRecovery`, appended to
the existing declaration for source compatibility. An absent installation
denies recovery. The ordinary installed host supplies an independent evidence
port using purpose `goal-phase-source-history-recovery` and binding
`sourceHistoryRecovery`. Validation recovery evidence cannot authorize source
history recovery. Existing issuer, actual participant/reference, time,
component and exact target checks still apply.

Normal `NativeHostPreparationService.prepareEvidence` accepts GoalPhase kind
`source-history-recovery`: request is the generated
`GoalPhaseSourceHistoryRecoveryDescriptionResult`, result is the supplied
`GoalPhaseSourceHistoryRecoveryAnswer`, with actual reference, matching
execution worker, participant, issuer and time. Preparation passively returns
standard datastore import rows. It does not provision or authenticate Human
provenance. The trusted host owns that provenance and independent normal
StartupImport selection.

## R4 — Ordinary continuation and reopening

Before the existing partial-inspection Wait, the read-only walker loads this
specific retained authorization for S. It verifies the exact original
inspection, successful history prefix, unresolved intent and adjacent recovery direction, then
moves only its native cursor to the recovery successor. Controller, document
state, completed candidates, accepted implementation and repair counters stay
unchanged. The cursor records that this Step has consumed its one recovery
direction and preserves that fact across document selection, progress and every
other same-Step transition. Only an actual completed Step closure advancing to
the next Step clears it. Ordinary dispatch then creates a fresh candidate
attempt keyed by the new frontier. No additional semantic Submit is required.

Both ordinary Human completion and diagnostic direction retain the exact saved
target as an explicit candidate continuation input, separate from the pending
question. Before any fresh inspection persistence, staging or normalization
write, bounded File UoW reads interpret every completed prefix source against
its saved original rule and date. Each must still produce `unchanged`; missing,
malformed or no longer normalized prefix sources fail closed without writes.
Those paths are excluded from fresh normalization work. Their original success
receipts remain solely in the old history; no new success receipts are
synthesized. Remaining work uses the fresh source revision and native
references, and the old unresolved intent remains UNKNOWN.

Prefix reuse belongs only to that authorized fresh candidate attempt. Successful
complete candidate observation clears the saved prefix continuation as the
controller moves onward; a partial or UNKNOWN candidate retains its existing
stop. The consumed recovery allowance remains set until actual Step closure.
If an independent Review subsequently requests an ordinary same-Step repair,
that repair receives normal fresh source-history processing under the current
rule/date, including any former prefix paths it edits. No old prefix exclusion
or saved normalization date carries into that later repair candidate, and no
additional recovery permission is granted.

Normal candidate path admission, global-index ownership and native parent
ancestry checks apply to the new attempt. The existing bounded native
descendant proof may establish an independently integrated parent containing
the already normalized prefix bytes. Historical target reconstruction reads
its retained original inspection without observing its obsolete parent again;
fresh staging still observes ancestry and pins the actual descendant parent.
Its integrated delta is excluded through the existing parent-relative candidate
inspection, preserving the immutable Phase base and accepted Step tip. No
extra file exclusions or ownership policy are introduced. Reopening traverses the recovery
once and consumes the normal saved new-candidate/check history. Completed
checks and Step commits are not replayed. If the new attempt fails, ordinary
Advance stops again; description and recovery refuse another direction for
that same Step, including after a safe future-tail document update creates an
intervening selection record. This first-incident API cannot silently grant
another attempt. Prefix reuse is limited to the exact successful contiguous
source-history contract above; staging and Git-effect recovery remain outside
this boundary.

The older canonical mechanical Direction has the same historical-only
append-only declaration rule. Its actual retained receipt is loaded and decoded
before its captured target is reconstructed with complete equality. Participant,
original intent and exact contiguous successor checks remain required. This
read-only replay does not change the current-target or append functions and
does not admit an old target for a new direction or effect.

## Executable coverage and qualification

`P116-CONT-01-RETAINED` extends the existing ordinary completed-Human descendant
example and canonical mechanical-direction example with an unrelated safe
candidate-path append. Both require the identical pending ReviewStep, complete
native record and retained payloads, unchanged source bytes and modification
times, unchanged index and unchanged real check counts. The completed-Human
example also reorders original policy entries and requires a fail-closed Wait
with no new history or effects. These assertions require parent-owned focused
validation and independent review.

The normal-flow regression is the
`NativeGoalSourceHistoryRecoveryCliSpec` example
`issue one ordinary recovery Human and reach independent ReviewStep only after its independently admitted typed answer`.
It uses the existing generated normal CLI fixture and a fresh SQLite host:
first unknown intent, ordinary Advance Human, repeated identical Human,
independently prepared ordinary GoalHuman evidence, ordinary typed completion,
explicit next Advance, actual native candidate/SBT checks, and the next
independent `ReviewStep`. It checks retained Plan, base, accepted result,
history and counters. Separate examples cover Defer, missing/crossed/stale
evidence, storage-only completion with Git grants absent, and no second recovery
after a fresh failure or intervening document transition.

`NativeHostHumanPreparationSpec` covers complete target binding through public
property-name styles, malformed/colliding targets, legacy attestation rejection,
and recovery-only choice admission. `GoalPhaseSourceHistoryRecoverySpec` also
covers optional ordered Human input codec compatibility. The existing malformed
date example in `NativeGoalSourceHistoryCliSpec` now expects the same durable
recovery Human while retaining source/index/no-replay checks.

`GoalPhaseSourceHistoryRecoverySpec` covers public target roundtrip, legacy
intent preservation, malformed/crossed facts and independent evidence binding.
`NativeGoalSourceHistoryRecoveryCliSpec` retains generated-Operation diagnostic
scenarios in fresh normal SQLite hosts for duplicate-header failure, read-only
description, input correction, refused evidence, Defer, explicit direction,
separate Advance, reopening, unknown staging rejection and foreign-index
admission. A lower-level episode completes a real first Step before the
second-Step failure: public description and recovery succeed with Git
capabilities unavailable while normal Advance still requires them. Normal
Human-flow episodes independently cover the same retained completed-Step
history and preserve consumed direction across a safe future-tail document
update before another actual normalization failure. They retain the completed
commit/check receipts, accepted prefix and repair counters.
These diagnostic scenarios remain lower-level capability evidence.

Earlier 25-test PASS and installed PASS evidence qualified the explicit API
only. They do not establish acceptance of this new normal Human flow. The parent
must qualify the exact new ordinary Human example against the final generated
sources and installed artifact, with independent review and validation.

For post-publication qualification, the new CLI suite accepts
`-Dsmworkflow.spec.installedCommand=/absolute/path/to/sm-workflow`. Recovery,
preparation and Advance then use that ordinary installed executable with
`--host` selecting the same isolated test host. Without this explicit property,
the existing CncfMain fixture route is used. The suite never addresses a live
product Session. Test fixtures and supplied Human answers are test inputs;
passing them does not establish production Human consent.

The P116 bounded extension adds scanner regressions for exact subsystem JSON
interpolation and four/five terminal quote runs, typed prefix codec and crossed
binding rejection, and isolated four-success/fifth-unknown episodes. Normal
Human completion covers an independently integrated descendant, retained
original receipts/base/accepted tip/counters, remaining-source-only native
normalization, actual typed SBT checks and independent Review. Separate episodes
cover altered-prefix refusal before fresh persistence and a newly unknown
successor without another Human allowance. The installed host-complete example
uses the same later-intent shape with explicit test-owned Human records. These
new examples require parent-owned validation and independent review; historical
PASS and publication records above do not qualify this extension.

`CB-P116-SOURCE-HISTORY-CONTINUATION-001` additionally covers ordinary
Review-to-Repair after the recovered candidate completes: independent prepared
Review evidence requests repair, the assigned ordinary RepairStep removes a
former prefix source's header while retaining valid source, and fresh Advance
normalizes that path with new native intent/result receipts and a real selected
SBT check before another independent Review. Original prefix receipts, UNKNOWN
intent, Plan/base, completed Step facts and the single recovery Human remain
retained. This regression distinguishes candidate-scoped prefix reuse from the
Step-scoped consumed recovery allowance.

## Public same-authority normalization adoption contract

The source implementation adds these ordinary `GoalPhaseService` Operations:

- `describeGoalPhaseSourceHistoryNormalization(sessionId)` returns
  `GoalPhaseSourceHistoryNormalizationDescriptionResult`, with outcome
  `adoption-required`, the exact target, choices
  `AdoptSameAuthorityNormalization` and `Defer`, and
  `adoptionOperation = adoptGoalPhaseSourceHistoryNormalization`.
- `adoptGoalPhaseSourceHistoryNormalization(input)` accepts
  `GoalPhaseSourceHistoryNormalizationAdoptionInput`: `sessionId`, `target`,
  `choice`, `rationale`, actual policy-direction `reference`, and Boolean
  `priorExecutionEnded`. It returns
  `GoalPhaseSourceHistoryNormalizationAdoptionResult`. Adoption and description
  use managed native storage only; neither calls File, Git, SBT or Advance.

The canonical target contains the complete existing `recovery` target, exact
current `frontierRevision` and `frontierCause`, the named policy
`sm-workflow.source-history-normalization.v1`, and, only when present, exact
`pendingQuestion(identity,pendingRevision)` or `resumeFrom`, as applicable.
Initial targets omit `resumeFrom` and retain the original frontier/question
rules. A continuation target omits `pendingQuestion`, retains the complete
original recovery rule/date/prefix, and binds the actual current failed or
resultless reconciliation frontier. Its exact `resumeFrom` record contains
`directionIdentity`, `directionRevision`, `intentIdentity`, `intentRevision`
and, for an actual failed result only, `resultIdentity`, `resultRevision` and
`outcome = failed`. A resultless intent omits all three result/outcome fields;
it remains UNKNOWN. Successful observations cannot be resumed. These references
name the immediately preceding actual normalization attempt, including each
consecutive continuation, rather than an earlier convenient direction.
Complete canonical nested payloads
are required. Extra, absent, null, crossed and colliding public property names
refuse. Later unresolved intents retain every successful contiguous prefix
pair; the same contract supports intent8. Staging, other effect histories,
changed authority, completed questions and actual old Defer/Cancel decisions
cannot be adopted through this route.

For a pending legacy Human, the genuinely saved question supplies the complete
captured recovery target and full `historyPolicy`. Adoption and replay compare
it with the actual native source, requesting result, pending revision and current
resolved full rule. A changed captured rule refuses. An unsuspended legacy
intent retained provider identity and effective date, rather than the full old
rule. Its description states that unavailable old full-rule provenance; the
complete currently resolved rule is bound for adoption, without claiming that
the original intent independently proved that full rule.

The actual already held policy-direction artifact is the new direction's
reference. It is not an answer to the old Human and requires no newly invented
per-file Human approval. The distinct independent host evidence purpose is
`goal-phase-source-history-normalization`, binding
`sourceHistoryNormalization(target,answer(choice,rationale,participant,reference,priorExecutionEnded))`.
An effect-bearing choice requires `priorExecutionEnded=true`. The trusted host
must attest that fact from actual terminal evidence for the old invocation;
missing receipts, elapsed time, present content and caller prose prove none of
it. Old source-history-recovery evidence and generic authorization cannot
authorize the new purpose.

Normal passive `prepareEvidence` accepts GoalPhase kind
`source-history-normalization`, the actual description as request and
`GoalPhaseSourceHistoryNormalizationAnswer(choice,rationale,priorExecutionEnded)`
as result, plus the actual policy reference, matching execution worker,
participant, issuer and time. Preparation returns ordinary inert import rows.
It proves neither real lifecycle termination nor consent and does not provision,
adopt, answer the old question or Advance.

`Defer` writes nothing. Adoption appends an immutable
`GoalPhaseSourceHistoryNormalizationDirection`. With an exact unanswered
source-history question, normal decision supersession retains that question
unanswered and records a distinct `superseded-by-normalization` cause. It never
creates a Human completion or an `AuthorizeNewAttempt` answer. The original
UNKNOWN and successful prefix remain unchanged. Replay loads the exact immutable
direction and saved question, validates the complete original target and every
adjacent native predecessor, and consumes the supersession once.

A separate ordinary `advanceGoalPhase` owns reconciliation. It appends its own
managed reconciliation intent referring to the original intent and policy
direction, verifies completed prefix sources with their retained rule/date,
and reads the affected file through the existing File UoW. An already satisfied
postcondition records `observed-current-postcondition` with no File replacement.
An unambiguous normalizable comment uses managed
`UpdateExisting(expectedText)` and records the actual `updated-current` result.
Source body, unrelated comments and actual user changes remain preserved.

Missing or unreadable source, malformed/duplicate managed comments, changed
rule or conflicting replacement retain concrete diagnostics. Known read or
pure prewrite failures and the managed conflict result produce a separate
durable `failed` observation. A dispatched write without an attributable result
remains UNKNOWN. Reconciliation never fills the original missing result or
asserts who performed an already observed normalization. Available actual branch,
result, original UNKNOWN, policy reference and unassessed cause resolution are
recorded through existing CommonEvidence and Journal owners, without AI work.

An attributable retained reconciliation result is reused after interruption,
without another completed write. A new unresolved reconciliation writer stops
ordinary Advance with an explicit requirement for actual terminal lifecycle
evidence. The old prior-ended assertion does not authorize that new writer, and
the route supplies no busy retry, replacement Human or extra comment-repair
allowance. A durable failed observation also stops with its actual diagnostic.
Repeated Advance preserves that stop until continuation is explicitly adopted
through the same public describe/preparation/adopt Operations. Ordinary authorized
correction of an ambiguous source can precede that continuation.

Description reconstructs the actual immediately preceding direction and
reconciliation from retained native history. Adoption compares the complete
resume target with that current frontier and requires refreshed independent
`priorExecutionEnded=true` evidence for that exact latest invocation. The policy
direction artifact identity/revision remains the same; the refreshed host
lifecycle attestation supplies information rather than renewed Human authority.
Absent/false evidence, stale/crossed provenance, a different authority artifact,
changed full rule/scope, completed candidate/Review and genuine old Defer/Cancel
refuse. No File access occurs in description or adoption, and current bytes or
missing results never prove termination.

Adoption appends one distinct mechanical direction after that exact failed or
resultless frontier, with a reference and cause derived from its source frontier
revision. Native replay reconstructs the original question/rule and every
adjacent direction/intent/result edge before consuming the next direction.
Separate ordinary Advance then creates new reconciliation references and reads
the current affected source: an already satisfied original-date postcondition
requires no write; unnormalized unambiguous content receives the same bounded
managed update. Consecutive genuine attempts use the same relationship, each
requiring its latest actual lifecycle information. Earlier failed results,
unresolved intents and the original UNKNOWN stay unchanged; no missing result
is filled. The existing observation/CommonEvidence/Journal payloads link the
original UNKNOWN, prior direction/intent/result and new actual branch/result,
with cause resolution still unassessed.

After actual reconciliation success, ordinary candidate capture verifies both
the successful prefix and affected current postcondition before fresh effects.
Those paths are excluded only from this candidate's new-date normalization.
Remaining files receive normal processing, fresh selected validation and
independent Review. No original success receipt is synthesized for the affected
file. Successful candidate capture clears this reuse, so a later genuine
semantic RepairStep normalizes its changed files normally. The same Session,
Plan/base, accepted work, completed Steps, RRC counts and all failed/UNKNOWN
receipts remain; mechanical adoption/reconciliation reserve and refund no
semantic repair batches. Legacy explicit APIs and their historical codecs remain
readable and retain their existing choices.

`GoalPhaseSourceHistoryNormalizationSpec` covers the strict later-intent8 target,
own-purpose binding and passive preparation, real managed File idempotence,
read-only current observation, bounded update, ambiguous known failure and
changed-rule refusal. `NativeGoalSourceHistoryNormalizationCliSpec` uses the
existing isolated normal CLI fixture with a completed Step, four actual prefix
updates and an unresolved successor. It covers public adoption without an old
Human answer, separate Advance through actual selected SBT to independent
Review, unchanged original facts, Defer, denied evidence, known prewrite failure,
ordinary correction followed by same-policy continuation, interruption before
File work and after File write before result, consecutive exact-frontier
continuations, and retained-result continuation. The pure binding suite includes
failed/resultless resume targets and crossed/stale/null/extra/public-name
rejections. The interruption cases use the already supported test-owned native
acknowledgement hook and never seed native rows.

Installed qualification supplies explicit `smworkflow.spec.installedCommand`
and `smworkflow.spec.runtimeRoot`; the suite resolves and pins that immutable
publication directory for new public calls through `SM_WORKFLOW_INSTALLATIONS`.
Optional explicit `smworkflow.spec.legacyRuntimeRoot` creates the legacy pending
question with an actual old image, then switches to the pinned new image for
description, preparation, adoption and Advance. These fixtures own their host,
policy metadata and terminal assertions; they establish no live Human consent.
Generation, compilation, focused validation, independent review, publication
and installed qualification remain parent-owned requirements. The source
contract alone does not claim an installed product or live consumer adoption.
