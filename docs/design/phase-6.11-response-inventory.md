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

## B2 ordinary binding implementation and remaining producer boundary — 2026-11-06

The issued B2 Request belongs to Session
`3585e04191dce0bc0f70e394b6745767`, continuation
`3585e04191dce0bc0f70e394b6745767:ordered-implementation:1785`, expected
revision `1786`, under the same Plan revision `1`. B1's accepted local commit is
`d6f76e04a339e62718ff4310de091700693fc070`. This section records authored
consumer work, not a validation receipt or B2/Phase acceptance.

`RepositorySyncProgramImpact.bindOrdinaryC` resolves already declared checks
against the ordinary process plan's SBT programs and workspace root. An existing
exact ordinal binding is retained only when it belongs to this selected checkout.
Otherwise one ordinary workspace for the required operation is necessary;
missing or ambiguous destinations remain setup gaps. This does not install a
program, change its executable/JVM/environment/limits, create a workspace grant,
or confer execution authority. The existing typed attempt owner enforces current
admission. A private Check carries an optional resolved workspace; historical
checks omit the field and retain their original ordinal meaning and encoding.
Actual result/refusal admission checks that exact selected workspace.

The existing declared impact policy still selects bounded focused checks versus
its explicitly declared broad FULL checks. Unselected missing bindings do not
prevent an otherwise covered selection. Applicable native prior-evidence rules
remain in their existing owner; these changes manufacture no reusable receipt.
The ordinary SBT program keeps its own finite limits and JVM settings. This
connection alone does not add metadata-derived estimates or certify that an
arbitrary Test task implements canonical FULL/HEAVY/Slow filtering.

A bare available Test program does not establish FULL coverage. If ordinary
impact/FULL classification cannot be established, or a selected required binding
is missing or ambiguous, native Advance returns `ValidationSetupRequired` before
product dispatch, merge commit or push. The actual pending merge remains with
its original native owner. Declared non-consumed document-only changes continue
through the existing structural checks. Actual executed failures, admission
refusals, incomplete execution and resultless intents retain their distinct
existing behavior. Same-Session supplementation/aggregate resumption remains B4.

`RepositoryDirectorySummary.diagnostic` is optional and used for this bounded
setup diagnostic. The aggregate retains the actual child's message, Session and
native revision rather than losing the missing check/root/coverage information.
Completed summaries and historical retained Result records omit the field.
No source, log, snapshot or process capture is copied into this diagnostic.
The CML declaration and explicit output-summary ABI entry describe that shape;
normal generation/compilation remain the parent workflow's responsibility.

`RepositorySyncDirectoryListMergeSpec` authors controlled real Git/typed SBT
cases for bounded source impact, declared broad build impact, an unselected
missing compile binding under FULL, non-consumed documents, missing required
binding, missing coverage, actual product failure and actual finite timeout.
It asserts pending-merge preservation and same-result collection without replay.
The fixture deliberately declares its product coverage; it supplies no genuine
live-provider or child-mode acceptance and does not qualify other projects'
FULL adapters. No test or validation command was executed by this assignment.

Two required connections remain unavailable rather than silently defaulted:

- The standard composition exposes no ordinary general project coverage/FULL
  discovery profile independent of the existing sync impact policy and the
  GoalPhase-specific metadata profile. An available Test executable alone
  cannot supply this missing semantic declaration. Consequently absent coverage
  returns setup rather than inventing a FULL selection or adding a second
  project registry.
- The inspected CNCF `SbtTarget` has workspace, optional scenario and estimate;
  `SbtExecutionResult` has target, operation, raw process and optional test
  selection. `ValidationMetadataV1.Metadata` and
  `ValidationOperationBindingV1.bindC` do not convey required/effective target
  mode or actual fixture/live attribution. The latter binds an exact TestOnly
  selector but no mode. A host profile, environment map or successful process
  cannot prove what a forked target consumed. VM-01/03/04 therefore require a
  producer-owned typed mode/default/observed-attribution contract and its normal
  build connection; this consumer does not parse arbitrary stdout or introduce
  a launcher. No CNCF or Cozy source was edited.

The semantic return must remain decision-needed while that required boundary is
unresolved. The Phase's 29 acceptance conditions, B3 dirty-purpose work, B4
recovery/reference work and B5 installed qualification remain unchanged and
pending. No original checkout, old Session, counters or accepted workflow
results were edited.

## B4 authored retained-owner connections — 2026-11-08

The issued B4 assignment belongs to Session
`3585e04191dce0bc0f70e394b6745767`, continuation
`3585e04191dce0bc0f70e394b6745767:ordered-implementation:2514`, expected
revision `2515`, Plan revision `2`. The retained public Request was read without
changing it. This section records source authorship, not a validation receipt or
Step/Phase acceptance.

| Producer / consumer | Authored connection |
| --- | --- |
| GoalPhase, GoalTask, DelegateTask, StandaloneFix, RepositorySync, SplitPhase issued work | The external control adapter confirms the actual native issued record before returning `retainedWork`; work/Human bodies remain with their existing WorkOrder/Decision owner. Genuine issued context, native completion identities, outcomes and repair counters remain inline. |
| Native exact detail read | `WorkflowRetainedWorkService.readWorkflowRetainedWork` selects the exact Component instance, Workflow identity/revision, Session, continuation, expected revision and owning input codec. It loads immutable work and renders its owning business projection without advancing or replaying effects. |
| GoalPhase validation/completion, bounded Task/Fix state, Split selected plan | `retainedMaterials` identifies exact mechanical receipt field projections. Current synchronization/Evidence/application observations remain explicitly in `controlSummary`; a historical snapshot is never relabelled as a later observation. |
| Repository directory result | Aggregate revisions continue to retain original members and native work identities. Public summaries return confirmed work references and finite control summaries; collection does not reissue completed or uncertain child work. |
| Repository setup supplementation | Missing directory/target settings are retained as `ConfigurationRequired` before any child or remote effect. `refreshRepositoryDirectoryConfiguration` requires the existing aggregate Session, participant and exact outstanding revision. It resolves only the captured directory selection or reconsiders native `ValidationSetupRequired` waits after normal host configuration changes; started targets and other failures/UNKNOWN effects retain their existing contracts. |
| Purpose preparation/materialization recovery targets | Repeated progress/prepared/intent bodies become confirmed `retainedSnapshot` references to their actual native receipts. Runtime expansion precedes the unchanged complete target codec, genuine Human evidence and current-frontier admission. Legacy complete targets are still accepted. |
| Shared preparation and result transport | Preparation accepts the exact work resource. Inline/Resource recovery inputs expand selected snapshot descriptors inside the owning runtime. Semantic result Resource transport uses the complete acquired serialized input and the existing result decoder; no caller envelope reconstruction or application content ceiling is added. |
| Product caller compatibility | `native-goal-command.py read-work` performs one explicit public read. The dispatcher and submission helpers can select its original issued response while retaining the unchanged detail artifact. Evidence preparation forwards the owner resource. No deployed `development-workstation` file was edited. |

The two public read Operations use existing managed native storage and its
configured retention/receipt limits. They neither copy snapshots into Evidence
nor create a parallel archive. Reference creation requires successful native
read-back, not allocation. Missing/inaccessible/foreign/unsupported or uncertain
reads return their concrete native failure; no latest revision or successful
effect is substituted. Resource file acquisition retains the independently
configured artifact budget, roots, authorization and UTF-8 decoding. Native
datastore reads retain their own managed owner and independent receipt limits.

Passive preparation is an explicit artifact export before host import, not an
already-retained control response. Its existing importer rows and Evidence
preparation are not advertised as usable stored references. Common Evidence's
existing describe/archive metadata remains concise; `readEvidence`/range and
the new native material reads are explicit selected-content reads. Legacy
lower-level recovery description APIs retain their complete target contracts;
only the connected preparation/materialization descriptors change representation.
Their admission codecs and historical storage formats are not rewritten.

Authored executable coverage includes raw compact Goal control with a growing
multibyte/escaped retained Plan, a separate runtime reading the same exact issued
revision, crossed/missing reference refusals, selected serialized snapshot
restoration, two-repository dirty purpose reference consumption, and exact
same-Session configuration refresh without repeating completed checks. Existing
fixture consumers now follow explicit public detail reads when they need legacy
full views; raw-control specifications use a separate helper. The product Python
adapters have source specifications for explicit detail selection and refusal of
crossed identity/revision. None of these checks was executed by the semantic
implementation worker.

B5 still owns installed qualification of the approximately 37.5 MB incident,
actual stdout/stderr measurements, retained payload growth, representative
route coverage, normal publication and integrated review. The source-level
connections above are not a claim of those results. The existing installed
operational caller remains unchanged until the owning runtime publishes/adopts
the product and its packaged caller scripts.
