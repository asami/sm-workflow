# Selected SplitPhase document effects

Status: current typed File consumer contract; parent qualification and independent
review remain required. Earlier generic-process results are historical evidence
for their earlier source and do not qualify this replacement.

`SplitPhaseSelectedDocumentEffect.prepareC` requires the actual native APPLY
source and one selected repository. Its preparation retains the observed native
source, snapshot, deterministic projection and typed business request. PREVIEW
and bootstrap paths that use no File effect need no document workspace mapping.
The existing native `entry.ApplyingDocuments` intent remains required before
application. No durable history, receipt schema or native edge changes here.

Every actual observe/apply call reads the current owning Subsystem's exact string
configuration keys `sm-workflow.split-phase.documents.repository` and
`sm-workflow.split-phase.documents.workspace`. The selected repository must equal
the configured repository; the workspace is parsed as a logical `WorkspaceId`.
Absent, null, mistyped, empty, whitespace-altered, crossed or unknown associations
fail. There is no request, environment, JVM-property, default-root or derived-path
fallback. This is a trusted business association; CNCF independently enforces
the current actual Component's workspace grant, root and resource byte cap.
See [normal typed File workflow](normal-typed-file-workflow.md) for host configuration.

`WorkflowSelectedDocumentFiles` derives every selected path from the saved
snapshot through `SplitPhaseSelectedDocuments.projectC`. It reads actual complete
text through `WorkflowFileExecution` and the protected CNCF File DSL in the
owning call's current UoW. The application checks the source's typed boundary
and authority, unique unfinished Scope ownership and selected child contract.
Completed rows, including identities `ID` and `old---completed`, unrelated
headings/history, Unicode and final newline are preserved. Newly completed work,
extra unfinished work, changed source authority and independently occupied
children remain conflicts rather than overwritable data.

Observation reads all selected paths without writes, writable locks, temporary
files or another JVM. All Ready is Ready; all Applied is Applied; mixed
Applied/Ready is Partial. An applied final source with a missing or nonapplied
child is Conflict. Application accepts only all Ready: every already-applied,
partial or conflicting state refuses application without resuming missing files.
A later observation does not authorize replay. Existing saved-intent and
uncertainty handling remains with the owning native workflow.

Application computes the complete intended contents before the first write.
Children are installed in reverse selected order with `CreateNew`; source
managed sections are installed last with `UpdateExisting(exact observed text)`.
Only actual Created/Updated outcomes establish success. Missing, Changed and
AlreadyExists stop at the actual path; errors after a write attempt become
transaction-indeterminate. Completed writes remain; there is no retry, rollback,
upsert, whole-file seal or old 2 MiB application limit. CNCF owns finite complete
UTF-8 acquisition, runtime limits and atomic per-file installation. This is no
multi-file transaction or arbitrary-editor compare-and-swap guarantee.

`SplitPhaseSelectedDocumentEffectSpec` exercises actual normal workspace-only V3
assembly and owning SmWorkflow UoWs, selected state classification, preservation,
no replay, trusted mapping/current grant composition and B's current root/cap.
Two public `ThreeWorkflowDirectOperationSpec` typed File APPLY scenarios retain
real Start/Advance, managed SQLite history and actual source/child assertions.
Producer filesystem/security algorithms are qualified separately and reused.
The original File product main and executable launch route are retired; six
NativeHost main/launch retirements and installed whole-workflow acceptance,
normal typed SBT, independent Step/Phase review and release remain required.
