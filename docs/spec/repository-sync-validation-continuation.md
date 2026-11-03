# RepositorySync same-Session validation continuation

Date: 2026-10-26. Contract: RS-VC1.

`describeRepositorySyncValidationContinuation(sessionId)` describes an active,
unsuspended `IntegrationValidationChecking` intent with no result under an equal,
local-ahead or fast-forward integration. The target includes the exact native
revision and causation, original Start participant/repository/root/branch/remote/
endpoint/FULL_STATE context, selected check policy and check identity/kind/ordinal,
candidate and unresolved-intent references and revisions, contiguous successful
receipts, actual HEAD/tree/tracking/index and every dirty path with index,
worktree and rename codes. It also names the separate ordinary Advance route.

The target and answer have strict canonical codecs. Missing, null, extra or
malformed meaning-bearing fields refuse. Public Record property naming is
normalized before the exact target codec. A new description is required when
the native frontier, index, status or selected path scope changes.

## Direction and independent evidence

The choices are:

- `AuthorizeNewValidation`: the complete original candidate is still clean and
  unchanged. Reuse its validated contiguous successful prefix and execute the
  first missing check and subsequent checks under a fresh native source.
- `AdoptCurrentFullState`: HEAD/branch/tracking remain the same, but listed
  contents are dirty under the original FULL_STATE scope. The explicit direction
  covers all described staged and unstaged files. Old successful checks remain
  historical; the fresh checkpoint tree runs all required checks again.
- `Defer`: append no direction and perform no mutation or check.

`adoptRepositorySyncValidationContinuation(input)` requires the complete target,
choice, rationale, existing direction artifact reference, matching execution
worker, `priorExecutionEnded` and `acceptOriginalUncertainty`. A non-Defer choice
requires both booleans to be true and independent host evidence under the exact
`repository-sync-validation-continuation` purpose. Evidence binds the component
instance, participant, reference, full target, choice and rationale. Authentication,
missing receipts, elapsed time and current file contents never prove termination
or consent. The trusted host must externally verify that the actual invocation
is terminal or was never started before provisioning. Live or unknown lifecycle
does not qualify. Accepting uncertainty leaves the original effects UNKNOWN;
the old intent is never relabeled as not-started or successful.

`NativeWorkflowPreparationService.prepareEvidence`, family `RepositorySync`, kind
`validation-continuation`, passively encodes the supplied description and typed
answer with participant, reference, execution, issuer and occurrence time. It
cannot observe termination, manufacture consent, issue/provision evidence, adopt
direction or execute the workflow. The independent host owns provisioning.

Adoption re-observes the public target after evidence admission and appends one
ordinary managed mechanical transition to `ValidationContinuationAdmitted`.
Its unique key is `validation-continuation-<source-current-revision>-adopted`.
Adoption is storage only. A duplicate or stale target refuses without another
direction or effect. Separate ordinary `advanceRepositorySync` does unfinished
work and still requires current effect grants and selected policy.

## Candidate and namespace lifecycle

The optional continuation direction is propagated through native progress v3.
Absent direction is omitted, preserving the historical payload wire shape.
The latest actual adoption history entry selects its retained mechanical receipt;
its causation, adjacent revision and original candidate/intent/prefix are checked.
Arbitrary highest-row selection cannot choose a candidate.

The scoped store uses `validation-continuation-<adoption source revision>` for
all new references, causation and candidate keys. Preservation, integration,
validation and convergence use that same store and shared managed adapter.
Old fetch, checkpoint, accepted integration, checks and convergence are never
consumed as the new candidate's effects. Terminal replay selects the scoped
convergence receipt. No auxiliary store, lock, outbox or retry registry is added.

For an unchanged candidate, ordinary Advance records a fresh `Integrated`
source and derives a new validation State/source revision seeded only with the
verified successful prefix. It creates no fake new result rows for old checks.
The missing and later checks use existing typed Git and SBT UoW operations.
A resultless new intent still requires fresh public adoption and independent
terminal evidence, including when its source is a continuation namespace.

For adopted changed FULL_STATE, ordinary Advance stages current contents using
typed `StageFullState`, captures `IndexTree`, creates and installs the normal
checkpoint, integrates against the original fetched input, then validates and
converges through normal typed capabilities. It does not fetch again before
integration, repeat the old checkpoint, unstage/reset/delete/stash files or
discard another owner's changes. Mixed staging is admitted only by this explicit
direction; the original preservation gate remains in force elsewhere.

The direction authorizes the named current path selection, **not an immutable
content seal or byte-identical Human review**. Same-path worktree contents at
normal staging time become checkpoint contents. Index/status/path/HEAD/tracking
drift since description refuses. Immediately after staging and before checkpoint
creation, the worktree must be clean with all changes staged, the pre-effect
HEAD/tracking must remain the same, and the complete staged path/rename endpoints
must equal the adopted selection. Status codes may normalize during staging;
they are not required to remain unchanged after staging. A concurrent unrelated
path included by full staging therefore refuses before commit.

The checkpoint capture program first obtains an immutable index tree, then
observes and gates the post-stage path/rename scope, fully staged clean worktree
and HEAD/tracking. It reads the index tree once more and requires equality with
that first tree. Index drift across the observation refuses before checkpoint
creation. Only the first captured tree is supplied to `CreateCheckpoint`; no
later unchecked tree is used. This finite pre/post observation gate does not
provide an atomic repository snapshot. External index activity after the gate
cannot change the immutable tree already selected for checkpoint creation.

## Bounded support and owner routes

Pending merges/unmerged entries, changed HEAD/tracking/scope, a failure with an
actual result, later review/Human/mutation, materialization/checkpoint interruption,
and completed/cancelled Sessions refuse with the existing owner route. A new
checkpoint or integration interruption is not silently replayed. This contract
does not complete the broader RS-C3 commit/push/fetch recovery work. Existing
Goal/history/counts and all old intents/results/candidates remain attributable.

## Executable specification and delivery status

`RepositorySyncValidationContinuationSpec` specifies strict target/answer and
historical payload behavior, candidate-choice applicability, prefix facts and
normal independent managed evidence provisioning. `RepositorySyncGeneratedAbiSpec`
includes both public provided Operations.

`NativeRepositoryValidationContinuationCliSpec` declares RuntimeTestDeclaration
and uses a fresh local bare remote and clone. Normal Start/Advance creates a
checkpoint and real missing-SBT-binding admission failure, leaving a resultless
intent. The fixture independently attests that its actual CLI invocation returned
and terminated, provisions evidence through normal host import, and calls public
describe/prepare/adopt/Advance. It covers unchanged-prefix reuse, changed mixed
staging with both contents preserved in a fresh checkpoint, Defer/denials/stale
direction, actual pending merge/unmerged refusals and a second resultless
continuation namespace. It also specifies post-stage path/rename drift refusal
and current same-path contents at staging time. It reads actual
managed persistence only for qualification; it edits no native row or receipt.

The pure suite also supplies finite controlled typed observations to the actual
capture helper. Stable reads return the first immutable tree in tree/observation/
tree order. A changed second tree refuses without reaching the downstream
continuation; a refused path scope stops before the confirming tree read. These
examples assert observation behavior without manufacturing process results or
checkpoint receipts. The public source and installed CLI scenarios retain the
normal Git/UoW/checkpoint qualification.

Explicit installed mode requires `smworkflow.spec.installedCommand` and
`smworkflow.spec.runtimeRoot`; the image path is pinned once and all business
calls execute that installed CLI. Source mode uses the normal source launcher.
The SBT executable is explicitly selected with the existing `smworkflow.spec.sbt`
or `SM_WORKFLOW_SPEC_SBT` fixture option. Local Push/FinalFetch grants are explicitly
fixture-owned, with no external provider, active Session or GitHub push.

Parent delivery on 2026-10-26 completed normal CML generation/compile, selected
five-suite source qualification (38 distinct examples), independent source and
fixture closure, normal `publishLocal` and pinned installed public CLI
qualification (6/6). Image `54ab474d-64c0-4584-ab2d-d62c8c444960` was normally
created, accepted and activated. Original failed attempts remain recorded in the
[delivery journal](../journal/2026/10/2026-10-26-repository-sync-validation-continuation.md).
This qualifies the bounded producer contract; real owner Session adoption and
continuation remain separate. It does not qualify broader RS-C3 recovery.

## Required missing lint continuation v2

The preceding RS-VC1 contract retains its historical meaning. The same public
`describeRepositorySyncValidationContinuation --sessionId` and
`adoptRepositorySyncValidationContinuation --input` operations also recognize
`sm-workflow.repository-sync-required-lint-continuation.v2` explicitly. An unknown
or malformed policy refuses; a failed v2 decode never falls back to v1.

This branch addresses an active, unsuspended native `MergeValidated` candidate
whose original structural and selected product checks actually succeeded but
omitted required Scala lint. There must be no later review, commit, convergence,
uncertain execution or ongoing effect. The original candidate may come from an
ordinary merge or an existing `repair-N` source. The current host must select the
same ordered check identities, ordinals and kinds, with exactly one fixed
`FixCheck` appended. Removing, reordering or rebinding old checks, appending new
Compile/Test checks, or changing candidate inputs refuses.

The v2 target contains the actual Start principal, repository and Component
instance, native frontier and causation, original pending-merge source and
retained validation references and revisions, candidate parents/tree/tracking,
original selected check facts, and each original intent/result reference pair
with terminal successful observations. It includes the current expanded fixed
check selection and actual index/status observations. It does not invent a v1
resultless-intent reference or duplicate old process output as new evidence.
Managed native history and original receipts authenticate these facts during
description and adoption; caller-supplied success is insufficient.

The choices are `AuthorizeMissingRequiredLint` and `Defer`. The existing required
answer fields `priorExecutionEnded` and `acceptOriginalUncertainty` must both be
**false** for v2: the old resultless-UNKNOWN assertions are not applicable to
these already successful original checks. This does not assert termination of
an unknown invocation or acceptance of its uncertainty. V1 adopting answers
continue to require **true/true**. A v2 `Defer` answer also uses false/false and
appends no native direction.

Normal passive evidence preparation uses family `RepositorySync`, kind
`validation-continuation`, and the existing dedicated
`repository-sync-validation-continuation` purpose. The description's explicit
policy selects the typed answer and binding. Component, participant, reference,
target, choice, rationale and both flags must match independent host evidence.
Preparation encodes supplied facts; the independent host owns provisioning.

Adoption re-observes the complete target and stores only a managed
`RequiredLintContinuationAdmitted` direction. A separate ordinary Advance creates
a genuine `PendingMergePrepared` source in
`required-lint-<adoption target frontier revision>`. It retains the original
candidate and repair context and seeds the original successful ordered prefix
from authenticated native receipts. It writes no replacement execution results
for those old checks. Only the appended missing lint runs through the ordinary
fixed CNCF `FixCheck` / `fixCheck` SBT UoW, under current effect permission.

Successful lint returns to independent review and normal local merge commit,
push and final fetch. Failed lint enters existing check-origin repair with the
original success prefix retained. A new timed-out or resultless lint remains
indeterminate and ordinary repeated Advance cannot replay it. A later changed
repair candidate does not inherit old success as applicable evidence. Repeated
or stale adoption either returns its exact passive receipt or refuses without
effects. Original Session, accepted work, history, repair budget and original
check results retain their meaning throughout.

For a genuinely new Scala pending-merge candidate, required-lint selection is
checked against typed actual parent/index paths and host program-impact policy
before the first SBT effect. Missing required lint therefore refuses before
Compile/Test. Retained old completed validation remains available through the
bounded v2 recovery branch after ordinary host correction.

`RepositorySyncRequiredLintContinuationSpec` declares pure controlled contract
facts and normal independent evidence ingress. The explicitly selected
`@DoNotDiscover` `NativeRepositoryRequiredLintContinuationCliSpec` declares
`RuntimeTestDeclaration` and uses tiny independent ordinary installed-host
repositories, real Git/local bare upstreams, SQLite and normal SBT. It requires
`smworkflow.spec.installedCommand`, `smworkflow.spec.sbt`, and an absolute private
`smworkflow.spec.requiredLintEpisode` file; it has no runtime-image-root selector.

Its `capture REQUIRED-LINT actual successful checks and postcheck refusal` case
belongs before new CAR publication. It retains each of four completed old-CAR
episodes incrementally, including real Compile/Test results and the old Scala
applicability refusal. Cases beginning `continue REQUIRED-LINT` belong after new
normal publication and reopen those exact Sessions. They specify real scalafix
success followed by separate fixture-owned review/convergence, a genuine
DisableSyntax lint failure, a genuine fixed-lint execution deadline timeout with
passive repeats, and a fixed-lint capability denial that retains its actual
intent without a lint process or replay of successful Compile/Test. The denied
Session receives no restored grant or retry. The cases also specify
stale/crossed evidence refusals and new-candidate preflight
before any SBT invocation. They never re-Start a retained Session or repeat an
accepted semantic result to upgrade it. All semantic, reviewer and lifecycle
inputs in these cases are specification-owned; the cases make no live provider
acceptance claim. This section defines the contract and selected scenarios,
without claiming their execution results.
