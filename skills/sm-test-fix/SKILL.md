---
name: sm-test-fix
description: Repair one authorized single-repository test or compiler failure through the native standalone FIX runtime.
---

# sm-test-fix

Use the [public runtime contract](../../docs/spec/standalone-fix-runtime.md) and
[existing-chat delivery contract](../../docs/spec/task-fix-delivery.md).
The [native host contract](../../docs/spec/native-host-execution.md) owns actual
execution and the [caller information contract](../../docs/spec/caller-information.md)
owns attributed observations. This skill is a thin caller/semantic adapter.

Adopt an explicitly authorized objective, repository, literal owned paths, selected
checks, ordered conditions and original source references as `StandaloneFixInput`. No Phase
management files are required. Freeze that input; later suggestions do not expand
scope. Existing Phase repair stays in its current Session. Preserve other edits.

The authorized caller uses `NativeWorkflowPreparationService.prepareStandaloneFixStart` to prepare
the selected import; a trusted host separately provisions actual selection and
authority. Start through `StandaloneFixService.startStandaloneFix` with that same input and selected participant,
selectionId and idempotencyKey. Preparation, authentication and a pasted reference
are not Human consent. Missing host/check/Git registration is a concrete setup gap.
Never substitute a GoalPhase, private store access or a caller-owned executor.

Use `StandaloneFixService.advanceStandaloneFix` on the retained Session. Native runtime owns candidate capture,
SBT selection/execution, evidence, diagnosis, repair limits, waiting and closure.
Do not run SBT, build a command runner, select extra tests, poll, reset counts or
replay an uncertain effect. Information requests use the assigned finite native
information operation and existing caller tools. No periodic AI status calls.

For actual issued semantic work, consume the complete work request and
`issuedWorkContext`, including adopted conditions, candidate, prior review,
original sources, failure and repair counts. Perform only the issued scope;
Review is read-only and independent of every implementation/repair worker.
Return `StandaloneFixWorkResult` with actual findings, conditions, unresolved limitations and
required diagnostic/disposition facts. The caller records genuine execution
observations through existing `prepareExecution`/`recordExecution`, then submits
only the actual result/reference/evidence through `StandaloneFixService.submitStandaloneFixWorkResult`. Submit closes that
WorkOrder; a separate native Advance owns validation and request acceptance.
A semantic return is not native acceptance.

No implicit commit. Accepted repairs return accepted-uncommitted; missing checks, failed execution and UNKNOWN remain incomplete. Compilation failure keeps affected tests not executed.

For another existing chat, obtain genuine direction for the exact destination,
objective/effects and result return unless already authorized. Retain distinct
requester and destination identities. Send through the existing authorized chat
tool; do not create a chat service or polling loop. Use public
`prepareTaskFixDelivery` for the unchanged actual destination response and its
real source artifact, and `prepareTaskFixAdoption` for the requester's explicit
adopt/defer/reject disposition. These passive preparations authenticate nothing
and mutate no Session. The requester consumes the attachment through its current
supported workflow, checks affected integration and supplies its own actual
semantic result. Return commit versus uncommitted truth and remaining gaps.
Installed and cross-chat acceptance require actual retained evidence; source
fixtures, dispatch acknowledgement and tool exit zero do not establish them.
