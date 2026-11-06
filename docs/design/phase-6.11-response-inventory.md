# Phase 6.11 response and acquisition inventory

Date: 2026-11-05
Scope: P611-B1 source inspection. This is an implementation inventory, not an
installed qualification or an acceptance record. No workflow storage was read.

## Producers and consumers

All source paths below are relative to
`src/main/scala/org/simplemodeling/textus/smworkflow/`.

| Family | Response producer and existing owner | Consumers requiring the same exact revision |
| --- | --- | --- |
| GoalPhase | `admission/NativeWorkflowOperationOutput.scala`: `goalWork`, `goalValidation`, completion projections; `GoalPhaseNativeRun` and `GoalPhaseNativeWorkContinuation` own native retained work | `NativeWorkflowSessionInput`, `NativeWorkflowResultInput`, GoalPhase recovery/adoption operations, `preparation/NativeExternalEvidenceInput.scala` |
| GoalTask | `admission/GoalTaskService.scala`: `BoundedTaskService`; `GoalTaskNativeRun` owns the run | Work-result submission, malformed-issued-work recovery, diagnostic resolution, task/fix delivery preparation |
| DelegateTask | `admission/GoalTaskService.scala`: `DelegateTaskService` and the same `BoundedTaskService`; `DelegateTaskNativeRun` owns the run | Work-result submission, `DelegateTaskDeliveryService`, diagnostic resolution and delivery/adoption preparation |
| StandaloneFix | `admission/StandaloneFixService.scala`; `StandaloneFixNativeRun` owns the run | Work-result submission, validation recovery, diagnostic resolution, task/fix adoption preparation |
| RepositorySync | `admission/NativeWorkflowOperationOutput.scala`: conflict, purpose, review, repair, waiting and completion projections; `RepositorySyncManagedFetch` and `RepositorySyncNativeProgressStore` own native progress | `NativeWorkflowSessionInput`, `NativeWorkflowResultInput`, push/purpose/validation/repair continuation description and adoption, repository effect and evidence preparation |
| SplitPhase | `admission/NativeWorkflowOperationOutput.scala`: partition, scope, reasoning and Human-direction projections; `SplitPhaseManagedServiceAssembly` binds native owners | Partition/scope/reasoning/Human submissions, split-start and evidence preparation |
| Shared preparation | `admission/NativeWorkflowPreparationServiceAssembly.scala` and `preparation/*` return prepared records | The relevant Start, authority/evidence registration, delivery/adoption and caller execution operations |
| Shared Evidence | `admission/CommonEvidenceService.scala`, `CommonEvidence` and `CommonEvidenceLifecycle` own archive retention | Public `readEvidence`, `readEvidenceRange`, `describeEvidence`, batch archive and retention operations |

The affected public generated contracts are declared by
`src/main/cozy/sm-workflow.cml`; service binding and publication are selected by
`ThreeWorkflowLauncherAssembly.scala`. Changing a renderer alone cannot remove
embedded bodies from those service contracts or their preparation/adoption inputs.

## Existing duplication and minimum reference boundary

`NativeWorkflowOperationOutput.goalWork` projects the complete selected Plan,
original base, completed Steps and candidate into issued work.
`RepositorySyncPurposePreparationContinuation.Target` carries serialized
`progressSnapshot` and `actualSnapshot`; its admission compares those exact
snapshots. `RepositorySyncRepairScopeCorrection.Target` can carry a complete
purpose repair snapshot. These consumers must be changed together with their
producers; deleting the bodies at presentation time would remove required input.

Evidence already has public identity/revision reads through its existing owner.
Native Workflow snapshots and mechanical/issued receipts have their own retention
owners. A future compact reference must select the actual retained owner, Session,
material and revision after confirmed retention. Native progress must not be
copied into a second Evidence archive merely to obtain an address. An allocated
identity or caller response file does not establish confirmed retention.

`admission/RepositorySyncDirectoryList.scala` now retains per-checkout Session
relations and aggregate results as typed native mechanical records under the
existing RepositorySync Workflow storage owner. `RepositoryDirectoryBatchResult`
contains `RepositoryDirectoryResultReference` and concise checkout summaries.
`readRepositoryDirectoryResult` reads the selected revision through that owner
and checks the original participant. No snapshot, execution capture or semantic
body is copied into Evidence. Broader snapshot/receipt reference consumers,
availability-state recovery and legacy full-body compatibility remain B4 work.

## Current target and acquisition boundaries

`StandardWorkflowRuntime._repository_target_c` reads the already admitted typed
process plan. An ordinary multi-repository Start selects an exact configured
identity; a single legacy binding preserves its existing business identity.
Recollection resolves the original native Start root to its current admitted
workspace. It requires explicit source branch, remote, remote branch and
endpoint. `RepositorySyncDirectoryList.resolveC` resolves the
configured directory list against the same admitted plan before remote
observation, ignoring validation workspaces without Git destination bindings.
It deduplicates normalized checkout paths, keeps distinct worktrees, refuses
missing/ambiguous bindings and retains the explicit source and destination.
`StandardRepositorySyncIntent` verifies optional caller target
fields against that binding rather than guessing `origin/main`.

`RepositorySyncManagedGitObservation.remoteTipC` consumes CNCF's typed
`GitOperation.RemoteTip`. The inspected CNCF producer builds `ls-remote` from its
configured endpoint/remote branch. The consuming observation now recognizes an
absent ref only from an actual terminal no-match result with complete empty
stdout/stderr; other failures retain the producer's concrete safe diagnostics.
`RepositorySyncDirectoryListSpec` authors those distinctions and a mixed batch
on source branches created without upstream configuration, targeting a distinct
`release` branch. No executable qualification has run in this assignment.
Installed authentication/transport qualification remains B5 work.

`NativeWorkflowResourceInput` delegates acquisition to the executing call's reader.
`NativeWorkflowPreparationServiceAssembly` and artifact recovery readers now use
`WorkflowArtifactBudget.readTextC` with explicit UTF-8. The approved CNCF
dependency extension adds `ExecutionContext.readResource` and `readResourceText`
with immutable `ResourceReadLimits`, using the same observed provider owner.
`WorkflowArtifactBudget` binds the optional Component parameter through ordinary
configuration resolution. These are authored source changes awaiting the
runtime-owned dependency/consumer validation and publication.

CNCF's `ResourceReadLimits.createC` owns the supported finite range, currently
1 through 268,435,456 bytes. The required Workflow default of 67,108,864 bytes is
within it. The new `workflow.artifact.max-bytes` binding uses the approved
CNCF-owned ExecutionContext-aware Resource extension preserving authorization,
roots, strict decoding and observations. Generic Resource, Git capture, SBT
capture and mechanical-receipt budgets must remain independent. Application file
I/O, provider reconstruction and a post-acquisition content-size gate are excluded.

## Remaining boundary

The actual Human8 approval and native inventory continuation admit eleven
literal CNCF source/spec/document paths. That authority does not cover other
dependency files or remote publication. B1 sources and executable specifications
are authored; no compile, lint, executable validation, independent review,
installed qualification or acceptance is claimed. Original Phase 6.10 Sessions,
accepted results, history and counters were not read or modified. Phase 7
workspace management remains out of scope. B2–B5 retain their own validation,
dirty-purpose, recovery/reference and installed-output obligations.

## Consumer source reconciliation after initial source adoption

The parent supplied the accepted maintenance source
`cee1c5cff718c36cdbdab757406d8e1db7df20ba` from the separate
`sm-workflow/.codex-workflow/checkouts/p611-initial-source-20261105` checkout,
and reported its normal `publication02` installation. That runtime introduced
the v8 initial dependency-source Direction/Capture and v5 native candidate
envelope now used by this Phase's existing Session. The consumer source still
had only the preceding codecs. Publishing that source would discard the
ability to decode and traverse the just-adopted history.

The B1 reconciliation incorporates that exact maintenance commit's seven
production source changes, the five directly coupled executable-specification
files, and its owning dependency-source-revision specification. It preserves
the two overlapping B1 `WorkflowArtifactBudget.readTextC` calls in
`GoalPhaseCandidateRecovery` and `GoalPhaseDependencySourceRevision`. The
maintenance catalog/version change is excluded. Existing staged B1 bytes and
the original primary base are retained; no Git index or workflow record was
changed by this source reconciliation.

The retained v8 origin explicitly distinguishes current primary observations
from historical completed-effect provenance and an unavailable predecessor
Guard. Traversal authenticates the real ReviseWork lineage; the first new
candidate retains its separate initial-source effect relation and fresh
dependency capture. Legacy v1-v7 Guard requirements and records remain intact.
The transferred specifications cover these codecs and rejected crossed/fake
provenance, plus the normal installed continuation fixture. Their presence is
authored executable coverage, not a validation receipt for this consumer tree.
Consumer compilation, selected checks, independent review and subsequent
runtime-owned publication remain required.

## Fresh B1 implementation handoff — 2026-11-06

The fresh public Request issued `P611-B1` in Session
`3585e04191dce0bc0f70e394b6745767`, continuation
`3585e04191dce0bc0f70e394b6745767:ordered-implementation:2`, at expected
revision `3`. Its selected Plan is
`phase-6.11-directory-list-and-retained-responses` revision `1`. The inherited
changed source is the starting implementation for this new Session; historical
maintenance, publication and prior Session notes above are provenance, not
acceptance of this Session or its candidate.

The B1 handoff preserves that implementation and adds executable specifications
for both an explicit `origin/main` destination and a distinct `release/next`
destination with the same no-upstream source branch. Optional caller target
fields must agree with the configured native binding; they cannot replace it.
The artifact-budget specification now passes initialized Component overrides
through `WorkflowArtifactBudget.readTextC` into the executing Resource owner,
including an inclusive multibyte boundary and an unchanged smaller generic read
policy. These additions cover the actual public admission/acquisition seams
rather than only their configuration decoders.

No validation command, independent review, runtime invocation, Git mutation or
dependency edit was performed by this semantic implementation assignment. The
parent workflow owns actual contract compilation, Scala lint and directory-list
validation. B2–B5 continuation, broader response reference connections and
installed output qualification remain separate unfinished Steps. The old
checkout and old Session were not edited; no historical result or execution
receipt is adopted by this document.
