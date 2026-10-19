# Normal typed Git workflow

Date: 2026-10-15 (Asia/Tokyo)
Status: primary consumer implementation and executable specifications completed
bounded source-level parent qualification (66 passing checks in eight suites).
Independent review and Step/Phase acceptance remain pending.

## R1 — Intent and host responsibility

The primary supplies `GitOperation[A]` and the single logical Git workspace
`sm-workflow-repository`. The thin `git_*` DSL creates a typed UoW intent;
the owning current UoW interprets it with its actual Component ancestry,
current workspace binding and independent per-operation process capability.
Normal direct Git uses the shared CNCF lifecycle. The primary has no Git
ProcessExecutionRequest, argv/string command, direct driver or generic fallback.
Normal direct SBT keeps its separate typed Test intent and native tool locks.

The trusted host supplies public strict `cncf.runtime-process-execution.v3`
JSON with exactly `schema`, `programs`, `grants`, `workspaces`,
`workspaceGrants`, `sbtPrograms` and `gitPrograms`. Bootstrap, host admission
and private codecs belong to CNCF. A Git declaration contains target, operation,
capability, identity, gitExecutable, localBranch, remote, remoteBranch, endpoint,
commitIdentity, selectionId, selectedPath, commitMessage, limits and environment;
the three bounded Goal closure kinds also require `selectedPaths` as described below.
Unused optional fields are explicitly null. Selected path/message fields occur
only on compatible kinds; semantic capabilities and selection keys are unique.
The canonical repository directory exists before bootstrap.

The test host fixes `/usr/bin/git`, one local bare endpoint, a normal host
identity, finite limits and Manage workspace authority for the actual component.
It configures only needed kinds and grants each independently. Fixture-only
fixed ProcessBuilder vectors create repository inputs; no fixture process request,
injected driver/context, completed Workflow history or fake result is supplied.
CLI uses the same helper for its actually consumed read subset. Nonisolated SBT
configuration combines Git V3 workspace/grants with SBT declarations; the three
isolated SBT fixtures retain their qualified normal configuration.

## R2 — Selections, facts and policy

| Consumer | Typed facts/effects | Application responsibility |
| --- | --- | --- |
| Observation/fetch | Branch/root/remote/HEAD/tree/status/history; Fetch | Existing target comparison, unsupported-history refusal, persisted fetch intent and actual fetched tip |
| Preservation/integration/convergence | StageFullState/IndexTree/checkpoint/CAS, FastForward/PendingMerge, CommitPending/Push/Fetch | Existing stage order, current remote AuthorityPort, acknowledgment and convergence observation |
| Conflict/review | UnmergedIndex/BlobContent, PendingDiff/PendingPaths | Text/blob/path budgets, mode/stage grouping, selected scope and independent semantic review |
| Goal candidate/closure | Selected staging/commit, diff/paths/metadata | Step ownership, reviewed tree/parent linkage, saved intent and genuine authority |
| Validation | CheckPendingIndex and typed Test | Original receipt schema, selected order and parent-finding acceptance |

Business String OIDs and branches pass through CNCF public parsers before
constructing typed operations; CNCF revalidates them in the current UoW. Results
become business facts through `valueC`. CNCF owns output parsing, optional refs,
status/unmerged classification, index/blob metadata and binary diff facts.
The primary retains shallow/replacement/common-ancestor policy, full text budgets,
path ownership and workflow judgment without parsing Git stdout again.

| Semantic selector | Ordinal bounds | Typed operation | Host-fixed data |
| --- | --- | --- | --- |
| `conflict-N` | 1..128 | StageSelected | Conflict path |
| `goal-path-N` | 1..256 | StageSelectedAll | Goal candidate path |
| `goal-step-N` | 1..64 | CommitSelected, CheckSelectedIndex, SelectedIndexChanges | Exact selected Step paths; commit additionally fixes message and identity |

The unused old immediate Merge, IsAncestor and IndexEntries definitions are
retired. PendingMerge followed by CommitPending supplies the existing merge path.
Only `ConflictObservationRequired` permits subsequent actual conflict observation;
an arbitrary nonzero exit does not prove a conflict. Failed, Incomplete and
InvalidOutput outcomes cannot create confirmed stages or usable Git facts.

## R3 — Historical receipts and uncertainty

`Receipt(identity, termination, exitCode, stdout, stderr)` and validation State
keep their exact existing persisted schema. Live structural checks obtain typed
`GitIndexCheck`, while existing `receiptC` stores their actual bounded strict raw
captures. It retains complete failed/indeterminate receipts, including their
actual exit or missing completion; it never fabricates a successful process.
Malformed/incomplete capture leaves the saved intent uncertain and nonreplayable.

Before decoding a retained structural receipt, require exited termination, an
actual exit code, valid UTF-8 text without NUL, and at most 65,536 UTF-8 bytes in
each stored stream. `GitIndexCheck.parseStoredC` is the sole findings decoder.
This pure compatibility codec executes nothing and establishes no permission,
capture provenance or completion beyond the caller's prior admission.

The original `successful(receipts, merge)` policy stays intact: every structural
sample must be recognized clean exit zero or findings exit two. A single sample
retains its existing acceptance, including findings. With two merge parents,
their opaque finding sets must have empty intersection; findings inherited only
from one parent are permitted. Required product checks still follow structural
checks and require actual exit zero. No new nonmerge-clean policy or saved
policy/version field is introduced.

Durable intent, CAS, current authority and acknowledgment remain separate from
external Git completion. Unknown saved intents are not automatically replayed;
only genuine later observations can establish an already-present effect under
the existing owning workflow policy. The same-chat permission for CNCF's private
Git lowering grants no primary generic Process execution, remote expansion,
File execution or additional launch route.

## Executable examples

- **G1-Equal, G1-LocalAhead, G1-RemoteAhead**:
  `ThreeWorkflowTypedGitOperationSpec` drives actual public Start/Advance through
  fetched, integrated and structural-validation facts to the separately
  test-authorized fixed convergence, then reopens an ordinary runtime and checks
  the unchanged public result, durable history and actual HEAD/tracking/remote.
  Host selection explicitly uses `explicit-no-product-tests`, with a reason and
  zero product checks. These three cases qualify Git composition only.
- **G2**: the same public workflow lacks the current Fetch process grant and
  refuses before mutation or confirmed fetched/integrated/validation progress.
- **G3**: the existing native acknowledgment hook interrupts an actual saved
  structural Git-check intent before completion. Reopening without that hook
  and without the check grant returns Wait, preserving intent/history and no
  fabricated receipt or replay.
- `RepositorySyncManagedGitObservationSpec` covers real dirty/reopen, history,
  unrelated/common-ancestor policy, fast-forward, pending merge/commit, nonforce,
  wrong destination and missing-grant composition through normal bootstrap.
- **R1..R4** in `RepositorySyncNativeValidationApplicabilitySpec` retain the old
  schema, clean/single/inherited/shared findings policy, refuse malformed and
  overbound stored receipts, and retain admitted actual negative captures.
  CNCF's qualified decoder/security cases are reused.

## Remaining boundary

Parent qualification172C completed with SBT exit 0, wrapper exit 0 and the
serialization lock released: 61 checks in seven suites plus five representative
checks in one suite passed (66 checks in eight suites), with zero failed,
aborted, canceled, ignored or pending checks. Actual public
Equal/LocalAhead/RemoteAhead first-loop and reopen results preserve terminal,
history and reflog; missing Fetch grant and unknown check intent/reopen without
replay also passed. This bounded source-level result does not establish
product-test assurance or genuine installed operator/reviewer acceptance.
Existing full public conflict, Goal and repair expectations remain required.
The common fixture now authors clean public typed Git/SBT composition with real
tracked initial ProductCheck inputs, including repair success and negative
checks selected by actual shared-file text. Generic Java validation and the
repair JVM probe are removed. Actual193 qualified this transition with 139
passing checks across all 18 concrete shared-fixture consumers (9 direct, 8 via
`NativeHostPreparationManagedFixture`, 1 via
`NativeHostPreparationOperationFixture`) plus 3 generated ABI/completion/retention
guards: 21 selected suites. This scoped normal-route qualification is separate
from historical172C evidence and genuine installed acceptance.

The five explicit Git-only examples retain their selected empty product-test
policy and now assert no SBT PID marker across original episodes and reopens,
despite the common tracked build precondition. The two File conflict negatives
retain required validation with explicitly missing SBT permission, preserving
denial/Wait and no launch instead of weakening their policy.

The typed File/read consumers have bounded selected current-UoW qualification.
All nine original product mains and launch routes are absent from source and
the current packaged JAR. Actual188B passed 206 checks in 20 selected suites,
including all seven normal SBT cases and normal/lost-acknowledgement native
completion rereads. These historical results and current193 qualification do
not complete original installed task obligations. The selected installed host's
residual generic definitions are now replaced by exactly 21 typed Git declarations
and grants for its existing target, without additional operation or remote
authority. SBT declarations remain empty and required validation remains open;
no generic fallback is available. Installed command/lightweight
client, mode/session/provider/result/security/minimum CBD16 and bounded Goal,
independent Step acceptance and commits, the sole full Phase review and local
release remain required. Phase 6.3 owns aggregate full-suite work.
The adopted planning/security scope and Phase 6.1/6.2/6.3 responsibilities remain.

Related: [managed observations](repository-sync-managed-git-observation.md),
[native installation](native-client-installation.md),
[normal typed SBT](normal-typed-sbt-validation.md),
[CNCF typed Git contract](../../../goldenport-cncf/docs/spec/typed-git-execution.md).

## P61-COMMAND-B5 — Qualified provider conflict continuation

Actual focused qualification recorded 2026-10-17. The explicitly selected
`NativeRepositoryProviderConflictCliSpec` has two independent command cases:
`prepare actual provider conflict` and `consume actual provider conflict`.
The prepare case creates one owned local bare upstream and diverged tracked
`value.txt`, prepares its public Repository input through
`prepareRepositoryStart`, selects the actual successful import and calls normal
Repository Start/Advance through the standard `CncfMain` launcher. Its pending
conflict must expose the actual initial/local/remote text, tips, blobs and
worktree markers without running SBT. Repeated Advance must retain that Request.

The requested mapping `p61-git-provider-mapping` revision 1 selects
`p61-git-provider-sol-medium` (`openai`, `gpt-6.1-sol`, medium reasoning effort)
for `CodingProgrammingStandard` and `ReviewStandard`, with no fallback. The
explicit parent is `p61-parent-sol-high` with the actual supplied origin context;
context isolation is requested and forced inline execution is disabled. Review
is classified REVIEW/STANDARD/PROGRAMMING/SMALL; repair and conflict are
CODING/STANDARD/PROGRAMMING/SMALL. This configuration authenticates no provider
completion. Normal `prepareExecution` saves the actual opaque session and first
pending selection alongside the public Start/issued/prepared DTOs. Only after
assertions does the case print `P61_GIT_PROVIDER_ROOT=<root>`.

The parent's separately selected semantic provider reads those actual inputs
and the product contract and returns only ordered conflict IDs, proposed text
and rationale in `git-provider-result.json`, with its actual child reference.
The provider neither edits the repository nor executes Git/SBT. The parent
records `git-provider-observation.json` after actual native completion, retaining
only observed resource values. These files are business inputs; JSON coherence
alone establishes no provenance or permission.

The consume case requires explicit `smworkflow.spec.gitProviderRoot`, reopens
only that existing direct fixture child and correlates the original session,
medium selection and actual child result/reference/observation. Ordinary Advance
must still show the original conflict before `recordExecution`. Recording and
the named semantic Submit must leave its text unchanged and start no SBT. A
separate owning Advance must materialize the proposal, stage only `value.txt`
through the typed Git DSL/UoW, run both structural index checks and fresh
required Test, and issue independent `ReviewStandard` merge Review. Assertions
cover both parent diffs/tips, the actual index tree, completed check codes,
product cwd/pass output and one external SBT PID. Repeated Review must not run
another Test. Every business call uses a distinct standard CLI child process.

Initial fixture commit/push only establishes the real tracked inputs. The
selected workflow host retains initial Fetch, excludes CommitPending and Push,
has no generic process program and creates no effect authority evidence or
FinalFetch grant. Parent qualified actual preparation (2 suites/2 tests), genuine
selected medium provider completion supplying a proposal only, then the normal
consumer (1 suite/1 test). Runtime materialized the proposal, performed typed Git
stage/structural checks/fresh Test and reached pending independent merge Review.
The semantic provider ran no SBT, Git or process commands. Requested configuration
and parent-observed completion are distinct evidence, without runtime/model
attestation. Both cases stop at their declared pending boundary; independent
Review acceptance remains unfinished. Local fixture initial Git effects grant no
real remote permission. No CommitPending, Push, final fetch or convergence
acceptance is claimed. COMMAND is connected and focused validated, pending its
existing protected independent Step review/local acceptance commit. This does
not close P6-EX02 or Phase 6.1 or replace historical results.

## Selected Goal closure and known-no-commit continuation

Every host declaration for `commit-selected`, `check-selected-index` and
`selected-index-changes` requires its own explicit `selectedPaths` JSON array:
1..256 unique valid Git paths fixed by the host for the exact `goal-step-N`.
All three declarations for a Step bind the same vector. There is no implicit
fallback to candidate paths or the whole index. Update obsolete declarations
before using this contract. Selected reads need their own grants and no commit
identity; commit retains its independent identity and grant. Closure also needs
fixed `blob-content` and `tree-changes` read declarations. The fixture's finite
argument count is 512 to accommodate 256 fixed paths and their fixed prefix;
this changes no other production limit.

The current native v3 closure receipt retains the original Input, ordinal and
optional commit, plus optional attempt, actual precheck and native change facts.
Absent new fields default to zero/none/empty; explicit null or malformed values
reject. Existing v3 completed receipts keep their exact reviewed-tree meaning
and grant no new effect. An old intent without a result remains unknown.

Before a new selected precheck, native branch, HEAD and HEAD tree must still
match the original parent. Current-Step changes must be staged and merged with
no selected unstaged or untracked data. Unrelated staged or unstaged work can
remain. Native closure neither stages nor resets nor unstages unrelated paths,
and compares selected content rather than requiring a globally empty index.
Commit uses ordinary CNCF typed `CommitSelected` with its host-fixed selection.

The owner saves actual `CheckSelectedIndex` facts before commit intent. A
nonclean check with unchanged selected reviewed content is a terminal
known-no-commit result for that Advance: it dispatches no commit, saves no commit
intent and changes no controller counters. After selected correction, the next
ordinary Advance may append one new attempt under the same original authority
lookup. A clean precheck without its intent, a retained intent without completion,
or any unknown effect remains Wait and grants no replay. Missing success or a
generic exit code never establishes known-no-commit. There is no same-call retry.

Only an earlier admitted actual EOF-only failure can permit a difference from
the reviewed candidate. The owner requires every native change to be `M` on
an owned path reported by that earlier check, with distinct present old/new
blob IDs and unchanged regular mode `100644` or `100755`. Complete typed UTF8
blob bytes without NUL must prove removal solely of extra terminal LF bytes:
the original ends in at least two LF bytes, and the nonempty result retains all
other bytes followed by exactly one LF. No trailing-space, CR, semantic, rename,
delete, type or mode change qualifies. Other changes require the ordinary
affected validation/review route.

After commit, native identity, exact original single parent and observed HEAD
tree must agree. No owned dirty path may remain, while unrelated work can
remain. Actual typed `TreeChanges` from reviewed candidate to committed tree
must match exactly the admitted canonical path/mode/blob vector; ordinary
closure requires that vector to be empty and the original reviewed tree.
Reconstruction re-observes immutable typed blobs and tree changes and admits
each retained record against its actual full native history prefix. Prior
failed checks are append-only; only the actual latest failed frontier can select
a fresh attempt. Later legitimate Step history can follow an already completed
receipt without making it authority for another commit.

The narrow EOF correction event preserves original validation and review result
targets and the original review reference. It introduces no semantic request,
review-reuse certificate, Human reapproval, new controller state or authority.
There is no reset-to-empty ritual. `GoalPhaseNativeClosureSpec`,
`GoalPhaseOrderedProgressionSpec` and
`GoalPhaseNativeRetentionApplicabilitySpec` author adjacent Given/When/Then
expectations for these contracts. Targeted pure specifications are not installed
acceptance or a production availability claim. Parent validation and the actual
selected staging → EOF failure → correction → one commit → next Step episode,
plus aggregate validation and Phase review, retain their existing obligations.
