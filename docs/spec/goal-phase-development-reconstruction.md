# GoalPhase current development reconstruction

`GoalPhaseService.reconstructGoalPhaseDevelopment` selects current development in
an existing native Session. The current Phase/checklist documents and source
determine remaining development; native history retains accepted results and
supports their reuse. Reconstruction does not start another Goal or Session.

## Business input

`GoalPhaseDevelopmentReconstructionInput` contains exactly these required fields:

| Field | Meaning |
| --- | --- |
| `sessionId` | The existing native GoalPhase Session. |
| `phasePath` | Literal workspace-relative current Phase file. |
| `checklistPath` | A different literal workspace-relative checklist file. |
| `proposal` | Complete raw `GoalPhasePlanRevision.Proposal` semantic projection. |
| `rationale` | Why this current selection is needed. |

Supply exactly one `input` or `inputResource`. The latter uses an absolute local
`file:` URI without authority, query or fragment, through the existing CNCF
Resource owner, configured roots, finite acquisition limits and UTF-8 decoding.
Both transports use the same business decoder. Caller-selected stages, historical
frontier IDs, effect receipts, `planSelection`, effective acceptance and claimed
Human approval are not input authority.

The native owner reads both actual nonempty documents through typed File UoW.
When the Phase has a selected `sm-workflow-plan` block, its owning decoder must
produce exactly the proposed raw Plan. A plain prose Phase is supported: actual
paths/text are retained as business evidence and the caller supplies the semantic
projection. This does not claim that runtime parsed prose or established success.

The existing checklist parser extracts actual identities and acceptance text.
Every proposed adopted checklist condition must match an actual identity/text.
Checked marks remain document data; they establish no native completion, passed
check, accepted review or commit. The existing Project owner captures effective
acceptance. A same-Plan projection may keep the existing revision.

## Native selection and history

The existing history-only walker locates the actual current controller. A pending
actual WorkOrder, Human question or recording request is recollected first. Its
result must be collected before another selection can replace remaining work.
Reconstruction never supersedes a potentially active worker.

The bounded technical normalization reassessment below is the sole exception
for an unanswered reconstruction question. It uses native DecisionSupersession;
the issued question remains unanswered with its original body and history.

An unissued selection preserves Session, participant, Component, definition,
repository/root/branch, original base, actual completed Step identity/order and
accepted commits. Remaining paths must fit admitted host candidate coverage;
checks must fit the declared selected policies. Original required acceptance,
validation, independent review and closure obligations remain owed. Compatible
unaccepted remaining purpose/document changes may be selected within that
boundary. Actual protected meaning changes receive concrete Human reasons.

An insertion or reorder at the current boundary may be adopted when no repair
scope is reserved. A pending or stopped repair, a charged repair, or retained
repair observations protect their original Step or Release boundary. Assessment
marks a move of that boundary incompatible and does not offer adoption; the
controller enforces the same rule on application. Plan revisions within the
same protected boundary retain its original convergence scope and accounting.
A pending or stopped repair reservation cannot be amended, even when its current
boundary remains the same; assessment withholds adoption for that revision.

One managed mechanical transition uses
`session:ordered-development-reconstruction:<actualFromRevision>@1` with
causation `ordered-development-reconstructed`. Its record contains current
document selection/text, raw proposal/captured Plan, current Git/index
observation, relevant retained native references, concrete assessment and any
actual preceding selection/Human-answer references. It contains no second
Cursor, dependency-chain cache, copied native history, source tree, log archive,
new digest or content seal.

Traversal authenticates the managed record and its adjacent actual predecessor.
It first walks the genuine history prefix ending at that predecessor, retaining
any Paused/UNKNOWN state, then applies the stored selection. Reconstruction from
history does not consult later current files or project defaults. All older
records, failures and accepted Directions retain their original meaning.

The reconstruction codec omits only its own optional members. Nested business
values retain their owning canonical codecs, including a Git dirty entry's
nullable `renamedFrom` member. The existing Human-context boundary restores that
member when public Record presentation omits it; stored reconstruction decoding
remains exact. Malformed historical records are not silently rewritten or
converted into accepted transitions.

## Current capture and applicable evidence

When an existing candidate is affected, reconstruction prepares ordinary
`InspectCandidate`, or whole-Phase `PhaseInspection` only after every real Step
has closed. Only current candidate/check/review/closure projections that became
obsolete are cleared. Original results remain attached to their original subject.
The generation advances once; an initial-source special generation moves to the
ordinary later-generation capture rather than weakening its historical guards.

An unstarted reconstructed capture has no candidate/check/closure/effect intent.
Repeating the same effective document projection retains that record/generation;
further unstaged edits before capture begins need no additional reconstruction.
This makes no unchanged-worktree-byte claim. Normal candidate capture observes
the then-current contents.

Actual saved candidate/check/review evidence is applicable only when typed
observed HEAD and index tree match the actual candidate and every covered
selected worktree entry is clean. Changed contents prepare fresh capture and
affected checks. A changed candidate running the same check is ordinary work.

Normal candidate construction owns source-history selection and normalization.
An unchanged source comment requires no File write. Current typed status proving
already indexed identical selected content records a fresh observation instead
of executing `StageSelectedAll` again; a correctly staged deletion qualifies.
Actual unstaged/untracked changes still require normal selected staging. Missing
results never become permission to replay an effect intent.

## Retained dependency source authority

Original source dependency Direction, consumption, approvals, native lineage,
source guards, checks and freshness remain required. A current primary is not
forced back to obsolete historical primary bytes. Ordinary later-generation
capture performs current source qualification with those retained obligations.

If a common selection changes the captured Plan, source Capture v9 adds optional
`planSelection`, linking the actual managed common reconstruction. Its ephemeral
native chain proof binds the actual selected Plan, Session, base, Step,
generation and chronology. Shape-valid JSON alone supplies no native authority.
Fresh capture derives this reference from the native chain; callers cannot
provide it. The original Direction is unchanged.

Capture v1-v8 omit `planSelection` and keep their existing exact Plan meaning.
Explicit null, unknown fields, crossed references and version/metadata mismatch
refuse. V9 is a common provenance variant, with no incident-specific operation or
target. Check/review/closure freshness authenticates the actual native selection.

## Genuine Human decisions and uncertainty

The existing ordered Human purpose, independent issuer/time/reference and typed
CNCF suspension/completion remain the sole Human owner. The optional
`developmentReconstruction` context is omitted on legacy questions and carried
through public Human output, canonical source projection, preparation and
independent evidence binding. Native input retains the real preceding semantic
work/result; runtime fabricates no decision-needed Submit.

The presentation names actual document paths, current/proposed Plan
identity/revision, observed source/index, changed fields/conditions with old/new
meaning and the exact remaining observation/owner action. Complete typed business
values remain in the context; presentation does not repeat complete Plans.

Only applicable choices are offered:

- `AdoptDevelopmentReconstruction` selects a resolvable semantic/document
  ambiguity within existing Component and host authority. It is unavailable for
  crossed identity, divergent accepted completion, removed required obligations,
  altered repair allowance, completed Phase or unresolved external effects.
- `KeepCurrentRequirements` retains the original requirements/provenance and
  actual current source, resolving only the document-projection choice. It rolls
  back no source and clears no uncertainty.
- `Defer` and `Cancel` preserve all records and produce no automatic work/effects.

An actual adopted answer is retained by the existing Human completion and then
appended through the same common managed reconstruction. A separate ordinary
Advance owns subsequent work. Maintenance prose is not a runtime Human answer.
Passive evidence preparation requires the actual reconstruction context for
Adopt and Keep and checks the context's applicable choices. Existing ordinary
and recovery questions retain their own direction contracts.

Unknown writer, commit, publication or push outcomes retain their original
UNKNOWN records. Existing supported observation/reconciliation remains the only
way to determine the outcome. Unresolved effects have concrete owner/action
reasons and do not offer an ineffective Adopt choice. Historical UNKNOWN already
explicitly accepted by a retained genuine continuation is not a fresh ambiguity.

Repair stage, actual failure, reservation, counters, pending allowance,
convergence control and observations remain. Exhaustion stays at its existing
Human boundary. Reconstruction grants no refund, new window, free implementation
restart or closed-Phase reopening.

A common Plan revision retains the actual predecessor's current repair-scope
identity through an ephemeral controller override. Every same-Step transition
carries it; no observation or count is rewritten. Actual normal Step closure
clears it for the next semantic scope. Legacy states without a common selection
keep their original revision-sensitive scope calculation. The override is
derived from native common-record traversal and is never caller input or another
persisted registry.

## Current-source completed-prefix selection

A formally failed source-history normalization can have a completed prefix
whose old rule/date postcondition no longer applies to legitimate current
source. This is applicability of a new subject, not evidence that the original
UNKNOWN succeeded, never started, or may be replayed. The normalization
Direction must retain its admitted `priorExecutionEnded` evidence, and its
actual adjacent reconciliation result must have `outcome=failed`. A resultless
reconciliation remains an unresolved writer and is ineligible.

The existing reconstruct Operation reads current prefix files through File/UoW
under the unchanged complete selected goldenport rule and workspace ownership.
It partitions the full original prefix, in order, into reusable paths satisfying
the saved rule/date and fresh paths requiring current preparation. A missing
file is known absence for ordinary candidate deletion. Read, policy or semantic
uncertainty is not classified as applicability or nonexecution. Existing
full-prefix and candidate-prefix verifiers remain strict for their old callers.

Selection requires at least one fresh prefix path, determinate actual documents
and proposal, the same selected Step/Plan/base/ownership, and no other unresolved
candidate, Git, closure, publication intent or repair reservation. Required
checks, independent review and original acceptance obligations remain. A new
candidate generation preserves completed Steps, semantic results, base, ordinary
and cumulative repairs, repair-control observations and reservations. It neither
resets a counter nor creates or refunds an allowance.

The common record uses a v2 envelope only when `prefixSelection` is present.
Its small versioned proof names the actual retained normalization Direction and
formal failed result, ordered reusable/fresh paths, old/new generations, and
optionally the exact superseded question identity/pending revision. The full
recovery target comes from the authenticated retained normalization owner; it
is not copied into the common record. Missing, null, unknown, crossed or wrong
version proof refuses. Legacy v1 records omit the field and retain their exact
bytes and original replay meaning.

An already issued question is eligible only when its genuinely saved context
is the previous common reconstruction directly after that same failed result,
with no incompatibilities or semantic questions and only that normalization
owner's technical uncertainty. Current documents must also be determinate.
Native DecisionSupersession retains the original prompt and body, records the
distinct selection and leaves `humanAnswer` absent. It fabricates no Human
answer or completion. Unrelated and meaningful Human questions stay pending.

Native traversal authenticates the selection and exact predecessor, retained
Direction/result, complete ordered partition, generations and optional question
supersession. Replay of the new selection's classification uses saved facts.
Existing ancestor normalization/recovery loaders retain their selected-policy
resolution guards; this does not make that preexisting chain independent of
configuration. Eligibility requires the same installed rule/provider.
The transient reuse context is derived from native history for exactly the
selected new generation, Step and base; it is not another persisted controller
or permission. Before fresh candidate persistence, only selected reusable
entries are reverified against their old rule/date and excluded from new
history effects. Fresh prefix paths and the old affected UNKNOWN path enter
ordinary current-date preparation with new references. No old receipt is
trimmed or relabelled.

The original recovery target retains its historical management paths, which are
authenticated against its original cursor. A new common selection can bind the
same actual documents to the current document-progress policy; those literal
paths must be owned by every selected Step and admitted by the current fixed
host. New candidate reuse does not require the historical management-path
vector to equal that current policy. Normal current document/recording ownership
preflight remains required, and legacy recovery reuse retains its exact old
management-path comparison.

An unchanged unstarted selection retains its generation only while selected
reusable postconditions still hold. A changed reusable path refuses before
fresh effects. Successful candidate capture ends the transient reuse; later
ordinary Repair cannot inherit indefinite skips. Fresh candidate staging,
dependencies, byte/index freshness, checks, independent review and closure use
their existing owners. Recollecting the pending Review repeats no success.

## A later current-candidate history obligation

Consumed source-history authority is scoped to its actual native inspection
subject: Session identity and source revision. The existing Boolean and native
records retain their meaning. Native traversal additionally derives a set of
consumed subjects from authenticated normalization/recovery targets and actual
source-history Human completions. The derived set survives current-source
selection, Step changes and repair; it is not persisted as another controller.
A matching subject remains consumed even if a later legacy Boolean is false.
A legacy true without reconstructible subject, crossed Session, null or invalid
noncanonical revision fails closed.

A different genuine current inspection can seek its own existing public
description or Human admission. This grants no retry, effect, consent, new
generation or semantic repair allowance. Current native intent, Step/Plan/base,
ownership, participant and evidence checks remain. The old consumed target
cannot be submitted again. Current normalization requires its own exact target
and newly admitted prior-execution-ended judgment/evidence; a missing old result
remains UNKNOWN.

At that same selected generation, candidate preparation composes the original
reconstruction's verified reusable subset with the authenticated CURRENT
recovery's successful prefix and separately qualified affected normalization.
The current source revision must be later than the original frontier. Its full
legacy target/ownership/rule/date verifier is unchanged. Duplicate claims between
old reusable and current prefix paths refuse, as does a changed old reusable
postcondition. Only the verified union is skipped. Old receipts remain intact;
normal staging, current ownership, dependency, check and independent review
requirements continue unchanged.

## Paired executable evidence

`GoalPhaseDevelopmentReconstructionSpec` describes pure evidence reuse, retained
prefix/base/repair state, unstarted capture recollection, compatible projection,
concrete applicable choices, checklist semantics and already indexed deletion.
`GoalPhaseDependencySourceRevisionSpec` describes legacy/v9 wire boundaries.

`GoalPhaseDevelopmentReconstructionSpec` additionally covers exact v1/v2 codec
boundaries, fresh technical generations, unchanged default controller behavior,
retained repair accounting and refusal of business/closure Decisions.
`GoalPhaseSourceHistoryNormalizationSpec` covers the complete ordered prefix
partition, known absence, strict old verifier, stale reusable paths and refusal
of resultless or crossed normalization facts.

`NativeGoalSourceHistoryPrefixReconstructionCliSpec` is one explicitly selected
`@DoNotDiscover` Runtime leaf. It reuses the tiny Resource normalization support
and normal host. Set `smworkflow.spec.prefixReconstructionMode` to `capture-old`
or `continue-new`, and `smworkflow.spec.prefixReconstructionEpisode` to one
explicit absolute caller-owned locator. `smworkflow.spec.installedCommand`
selects the normal installed launcher and the same host's actual assembly.
Capture runs before parent-owned publication and retains a real first-Step
commit, four Nov5 prefix successes, UNKNOWN, actual failed normalization and
technical Defer/Cancel question. Fixture-local `textus.clock.virtual-start-at`
sets Nov5 before preparation/Start, then Nov6 after legitimate source changes;
machine time is unchanged. Continue passively reopens the same physical host
with unused log indices, supersedes only that stale question, and separately
Advances through current preparation and actual checks to independent Review.
It asserts preserved original records, source bytes/mtime for reusable entries,
new current qualifications, counters and no repeated effects on recollection.
These authored scenarios require parent-owned validation, independent review,
normal LocalPublication and both installed stages before any qualification claim.

`GoalPhaseCurrentHistoryRecoverySubjectSpec`, in the existing reconstruction spec
file, separately covers same/different native subjects, legacy provenance gaps,
crossed/malformed contexts and unchanged accounting. It also applies the actual
goldenport rule to the complete multi-class installed-spec source, whose extra
class descriptions use ordinary `Created:`/`Last revised:` labels instead of a
second managed source header.

`NativeGoalCurrentSourceHistoryStopCliSpec` is one constant `@DoNotDiscover`
Runtime leaf. Its explicit `smworkflow.spec.currentCandidateStopMode` is
`capture-stop` or `continue-stop`; `smworkflow.spec.currentCandidateStopEpisode`
names one absolute existing seed/locator. It passively reopens the retained
partial tiny fixture, actual task and logs with its first Step/check/commit and
four dated prefixes already complete. It never repeats Start, setup or Submit.
Capture retains an actual current-source v2 selection, then demonstrates a
distinct current intent stopped by a second managed block comment. Ordinary
source conformance removes only that extra comment. The old installed runtime
retains Wait and refuses normalization description. Caller checkpoints retain
the actual successful selection before later calls and the stopped call/record
before later assertions; they only add observations/routing to the locator.
After parent-owned publication, continue uses that exact subject's public Human
and normalization evidence to reach a fresh native check and independent Review.
Both UNKNOWNs, formal failure, accepted selection, base, completed Step,
generation, counters and successful prefix File facts remain. Review recollection
repeats no successful effects. Execution and qualification remain parent-owned.

`NativeGoalDevelopmentReconstructionCliSpec` and
`NativeGoalDevelopmentReconstructionAdoptionCliSpec` are independently selected
`DoNotDiscover` runtime leaves for Keep and Adopt. The adoption leaf's tiny
primary/dependency fixture retains an actual accepted old source
Direction and old UNKNOWN, accepts later source, encounters the former historical
guard refusal, invokes the common route with actual current documents, admits
independent specification-owned Human evidence, then separately advances through
a real failing check, normal RepairStep, actual correction, affected checks,
independent review and local Step commit. A further same-Step source edit uses
the same common route. Public Human context/evidence tampering and pending worker
supersession are refused. Fixture authority is never consumer authority.
