# Current-call artifact snapshot routing

## Frozen implementation manifest

IMPLEMENT-CALL-SCOPED-SNAPSHOT-1 continues active Phase 2 in task
01a0a6d7-7043-73a2-a808-1367fe5fdb1f. Previous turn is progress: actual platform
evidence rejected the proposed SecureDirectoryStream reader; withdrawal-540
passed 147/147 tests and terminated with lock released. No live command.
Parent /root DIRECT, model/effort not directly observable, standard, retained
explicit limited-recovery override. Disclosure precedes source edits.

Allowed source root: /Users/asami/src/dev2026/sm-workflow only. Own this journal,
admission/GoalPhaseCallScopedSnapshot.scala (new), GoalPhaseStepCloseServiceOperation.scala,
GoalPhaseReviewCompletionOperation.scala, GoalPhaseRepairCompletionOperation.scala,
and test admission/GoalPhaseManagedSuspensionOperationSpec.scala. All source/test
paths are below src/main/scala/org/simplemodeling/textus/smworkflow or
src/test/scala/org/simplemodeling/textus/smworkflow respectively.

Whole-file observations: SnapshotPort currently supplies exact bytes without
ActionCall; closure/digest and repair algorithms are pure consumers. Step-close
creates BoundCall with actual core; managed accepted projection already receives
current core; managed repair completion rebuilds decoder callbacks inside its
current wrapper. Thread the snapshot through exactly these same execution edges.
Do not capture Factory context or modify the pure consumers/public SnapshotPort.

Add a package-private opt-in Port extending existing SnapshotPort. Context-free
load always refuses. forCall returns a short-lived view delegating Step + actual
owning core, rejecting absent/foreign owners first. Legacy explicit ports remain
unchanged. The seam is neither storage nor authority: its concrete implementation
must verify active lease and exact configured destination on every read. No global
context, actor assertion, provider, file path, Blob schema, pool or cache is added.
Wrap auto-close even without reviewed-authority ports when a scoped snapshot is
selected; reject missing core rather than invoking an unscoped fallback.

Executable spec authoring: modified AnyWordSpec scenarios retain adjacent
Given/When/Then/Matchers. The actual managed accepted-review scenario and four
repair/re-review reopen scenarios use a private scoped snapshot probe that first
performs canonical ManagedHostRecordRead under the current call, then returns
fixture bytes. Require positive dispatch, correct core/owner, equal reads on one
call, context-free/null/escaped-call refusal and no extra reads after replay.
The interrupted-recovery test's existing direct private publisher call retains
an explicitly context-free fixture snapshot only for that internal legacy probe;
it is not a production authority route or Blob evidence. Ordinary managed
completion callbacks use only the scoped probe.
The accepted-review scenario also reopens a fourth real host, binds the ordinary
managed requestStepClose Operation with the same scoped snapshot and explicit
fixture association/stop-only Decision, and supplies existing full-review evidence.
Require current-call artifact reads and missing-authority Decision without a
history append, commit or a new registration/selection authority claim.

Validate packageBin plus retained nineteen-suite accumulator through fresh typed
allocation/permission, first-attempt scoped escalation, shared SBT lock, normal
caches and complete terminal summary. Retain explicit `回避して` exception only
for skill-only old route stops. No commit/push/publication/dependency refresh or
upstream edits. Stop on required public/protected schema, storage grant or new
host-policy expansion. This is implementation/validation, not independent review,
PLAN/base admission, Step acceptance or Phase closure. Production artifact mapping,
bounded Blob byte acquisition and default launcher host remain open.

## Verified implementation

SMWF-CALL-SCOPED-SNAPSHOT-DIRECT-20261001-541 packaged the current component
and passed all 147 tests in nineteen suites, zero failures/aborts/canceled/
ignored/pending. SBT and wrapper exits zero, shared lock released. Complete
summary and the accepted-review plus all four repair/re-review scenario sections
were read:
/tmp/skill.cncf.d/a-58c1efd2-1a01-4713-9961-d310416039f6/44635-20261001T070202Z.summary.json.
Fresh ID appended to retained ledger; session terminal. git diff --check passed;
no source edits while running. No dependency refresh was needed.

The package-private opt-in scoped Port cannot load context-free. Each Operation
creates an ephemeral view from its actual core; legacy explicit SnapshotPort
objects remain unchanged. Review auto-close without reviewed-authority ports
also gets a current wrapper when its snapshot is scoped, rather than relying
on a missing call. The view validates owning sm-workflow component/context but
does not claim to authenticate, authorize or verify an active lease itself.
Concrete storage callbacks retain those checks.

Positive evidence uses ManagedHostRecordRead under actual SQLite managed dispatch
before fixture-byte projection. Accepted review made two reads with the same
actual call; direct/null/escaped attempts and replay made no additional successful
reads. A fourth independently reconstructed host dispatched pre-reviewed
requestStepClose; its two reads shared that new call, not the previous review
call, and missing authority yielded Decision without a commit/history append.
Normal repair completion issued fresh review via its active scoped callback.
Interrupted repair completion retained its result despite snapshot failure; the
existing explicit internal recovery fixture still recovered the gap. Both
authority-projection settings passed, and all captured completed calls refused
further storage-backed artifact projection.

The returned bytes remain test fixtures: this proves current-call routing and
storage lease checks, not production Blob payload acquisition or actual project
artifact materialization. It does not activate default launcher/MCP or finish
Phase 2. Next work can implement a bounded Blob reader on this seam, but must
freeze exact host Step/role/EntityId mapping, component-owned datastore binding,
managed-source metadata versus actual payload checks, stream limits/lifecycle,
and real provider/reopen evidence before selecting it in production.
