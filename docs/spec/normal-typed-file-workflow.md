# Normal typed File workflow

Date: 2026-10-15 (Asia/Tokyo)
Status: actual current-UoW typed File composition has bounded selected
qualification. Actual193 passed 139 checks in 21 selected suites, qualifying
the common SBT/repair/workflow transition across all 18 concrete shared-fixture
consumers plus 3 generated ABI/completion/retention guards. Independent Step
and Phase acceptance remain open.

## Owning UoW and responsibilities

`WorkflowFileExecution` borrows the actual owning `ActionCall.Core` and executes
only protected `file_read_text` and `file_write_text` intents through the normal
CNCF UoW interpreter. It accepts logical `FileTarget`, `WorkspaceFilePath`, text
and `FileWriteMode`; it returns actual `FileReadTextResult` and
`FileWriteTextResult`. It constructs no provider, driver, filesystem authority,
process request, executable, argv, JSON/stdout receipt or alternate interpreter.
Construction or preparation in A grants no authority when B executes the call.
B's actual Component, current workspace service, coarse grant, root and finite
runtime cap govern every operation.

CNCF owns complete bounded strict UTF-8 acquisition, canonical root admission,
ordinary parents/leaves, symlink refusal, grant checks and atomic per-file
installation. A missing leaf returns `None`; a present empty file is `Some("")`.
`CreateNew` returns Created or an occupied-leaf conflict. `UpdateExisting` carries
the exact expected text and returns Updated or Missing/Changed. The current
runtime resource byte cap bounds expected and replacement text as well as read
text; the caller supplies no per-call maximum or application read limit.
See the [producer File contract](../../../goldenport-cncf/docs/spec/file-workspace-dsl.md)
for those guarantees and their limits.

sm-workflow owns saved planning selection, exact repository association, semantic
path/text policy, compare/edit meaning, native intent/history and truthful
uncertainty. No typed File result alone advances or authorizes a workflow.
Each operation runs once. Reads create no writable lock, metadata file or
temporary file. There is no retry, partial apply replay, rollback, arbitrary-editor
CAS or multi-file/Git/storage transaction guarantee.

## Public trusted configuration

Normal CNCF bootstrap consumes strict V3 JSON. A document-only host can configure
this value under `textus.subsystem.process-execution.plan` with empty process,
SBT and Git definitions and grants:

```json
{
  "schema": "cncf.runtime-process-execution.v3",
  "programs": [],
  "grants": [],
  "workspaces": [{"identity": "sm-workflow-documents", "root": "/trusted/project"}],
  "workspaceGrants": [{
    "componentId": "org.simplemodeling.textus.SmWorkflow",
    "workspaceId": "sm-workflow-documents",
    "access": "manage"
  }],
  "sbtPrograms": [],
  "gitPrograms": []
}
```

Use the actual SmWorkflow component identity declared by the installed CAR for
`componentId`. The trusted owning Subsystem also stores exact string values:

```json
{
  "sm-workflow.split-phase.documents.repository": "selected-repository",
  "sm-workflow.split-phase.documents.workspace": "sm-workflow-documents",
  "textus.resource.read.max-bytes": "1048576"
}
```

The selected repository must exactly equal the configured repository. Both keys
are resolved on every actual observe/apply call through the owning Subsystem's
resolved configuration and `GoalPhaseHostRecordReadPolicy.storedValue`.
Missing/null/mistyped/empty/nonexact values fail. Workspace parsing and actual
current admission still apply. No request/environment/JVM-property fallback,
default root, repository-name-to-path conversion, second grant or per-file ACL
is introduced. PREVIEW and bootstrap without File use need no such mapping.

Conflict and repair use `sm-workflow-repository`, the same existing workspace
as [typed Git](normal-typed-git-workflow.md), with its original host binding and
Manage grant. No second root registry or Git metadata/backlink lookup is needed
for File. `RepositorySyncNativeConflictInput.Policy` restricts assigned semantic
paths, `.git` exclusion, file counts, NUL and finite text values after complete
CNCF acquisition; it does not replace producer authority or choose a read limit.

## Application results and native continuation

`WorkflowSelectedDocumentFiles` projects only saved selected paths, reads them
all, compares the source authority/boundary and unfinished ownership, preserves
completed/history rows and computes the complete write set. All Ready permits
reverse-order CreateNew children and source-last UpdateExisting. All Applied is
an Applied observation; mixed Applied/Ready is Partial; final source with missing
children is Conflict. Apply refuses every already-applied, partial or conflicting
state. Actual write conflicts stop at the selected path and an ordinary error
after attempted write becomes transaction-indeterminate. The native
ApplyingDocuments gate and existing durable schemas remain unchanged.

`RepositorySyncFileEffect` captures ordered actual present texts, observes exact
expected/replacement values and preflights every assigned file before writing.
Missing or conflicting text yields conflict. Observe reports all Applied or
otherwise Ready; apply refuses any changed replacement already applied.
Unchanged files retain ordered coverage and need no write. Remaining changed
files use exactly one UpdateExisting each and consume actual Updated.

ConflictInput and RepairContinuation consume capture into their original issued
work. ConflictMaterialization and RepairMaterialization consume observe/apply
inside existing saved materialization and stage intents, typed Git ordinals,
preconditions and uncertainty handling. Submit alone performs no File effect.
Actual results return to the same native pending-merge/check/review chain;
semantic proposal, Human/reviewer evidence and remote grants retain their roles.

## Delivery and remaining obligations

All five original File consumers now use this path. All nine original product
mains and their executable launch routes are absent from source and the current
packaged JAR, including the two File mains and six NativeHost routes. There is
no replacement main/JVM/process fallback. `WorkflowManagedProcessExecution` retains qualified
typed Git/SBT only. The normal runtime may allocate an inert standard process
driver for host lifecycle; File selects no program or process capability.

The executable specs use public strict V3 normal bootstrap and actual SmWorkflow
Component UoWs. Selected-document specs cover business composition rather than
duplicating producer filesystem algorithms. Two public APPLY and two public
Conflict/ConflictLiteral scenarios use actual managed history and files. The
latter retain saved pending merge and physical reopen while required typed SBT
is deliberately unavailable: denial/Wait supplies no test success, independent
acceptance, convergence or full conflict/repair/Goal qualification.

The shared fixture now authors clean public typed Git/SBT plans and real tracked
initial builds; the repair cases select ordinary SBT behavior from actual product
input instead of a generic JVM probe. Actual193 qualified this common-fixture
transition with 139 passing checks across all 18 concrete shared-fixture
consumers (9 direct, 8 via `NativeHostPreparationManagedFixture`, 1 via
`NativeHostPreparationOperationFixture`) plus 3 generated ABI/completion/retention
guards: 21 selected suites. The two missing-SBT File
negatives deliberately retain Git-only authority on both initial boot and reopen,
required-check denial/Wait, no SBT marker and unchanged materialized files.
Actual188B passed 206 checks in 20 selected suites, including all seven normal
SBT cases and normal/lost-acknowledgement native completion rereads; that scope
retains its historical scope alongside the current193 common-consumer result.
Neither result repairs the original malformed captured pending request. Typed
UpdateExisting retains exact expected-text refusal; the original issue and native
history remain unchanged.
Installed command and lightweight client, modes/provider/session/results,
bounded Goal completion, default-deny/explicit opt-in and minimum CBD16,
independent Step acceptance, one full Phase review and local release remain
required. Aggregate full-suite validation stays with Phase 6.3. Earlier evidence
retains its declared source and scope; no whole-Phase acceptance is implied.
