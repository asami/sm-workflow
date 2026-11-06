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

## Paired executable evidence

`GoalPhaseDevelopmentReconstructionSpec` describes pure evidence reuse, retained
prefix/base/repair state, unstarted capture recollection, compatible projection,
concrete applicable choices, checklist semantics and already indexed deletion.
`GoalPhaseDependencySourceRevisionSpec` describes legacy/v9 wire boundaries.

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
