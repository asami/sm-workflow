# Managed UoW completion integration proposal

Status: isolated participant conformance in progress; no runtime activation

The previous typed host completion slice passed focused-375 (270/270, 27
suites). It proves managed storage and projection, not active UoW integration.

## Verified existing gap

In CNCF UnitOfWork.commit, the standard EventEngine commit returns Unit. The
termination variable begins Aborted and becomes Committed only after
eventengine.commit returns. An exception before that assignment cannot express
a committed-but-unverified or uncertain native outcome. TransactionContext's
commit/abort bodies are empty. UnitOfWorkTermination has only Committed,
Aborted and Disposed. ContinuationRuntimePersistence releases failed resume
claims only when lastCommitTermination is Aborted; the new managed route must
not cause an uncertain native completion to enter that branch.

UnitOfWork currently runs postcommit callbacks before resource completion.
WorkflowCompletionServiceOperation separately resumes/commits a fresh UoW and
appends instance history in afterCommitResponseC. Calling the new host storage
adapter there is not a single-domain transaction and does not satisfy P2-U05.

## Proposed internal opt-in boundary

Keep ordinary EventEngine and compatibility commit behavior unchanged. Admit a
separate private CNCF managed participant only after isolated conformance.
Its commit must report a closed authoritative result, not a Boolean or an
exception interpreted as an abort:

| Native evidence | UoW disposition | Permitted subsequent behavior |
| --- | --- | --- |
| Complete verified enlisted commit | Committed | Complete callbacks/resources; only then admit original-order delivery |
| Proven no publication/rollback | Aborted | Existing bounded abort handling; no delivery |
| Uncertain or committed-unverified | Indeterminate | Memory-only discard; no claim release, retry, compensation or delivery |

The uncertainty result must preserve structured failure evidence. A missing or
throwing commit outcome is uncertain, not a proven rejection. Participant
discard means dropping process-local prepared/delivery data only, never changing
native storage. A finalized managed UoW must not commit or dispatch a second
time. Delivery failure is not a storage rollback or permission to replay.

Verified storage is a necessary input, not itself a delivery permit. Callback
or resource-completion failure must suppress delivery, retain committed storage
and diagnostics, and preserve the native disposition. Transient records must
never enter restart/recovery streams. Postcommit callbacks cannot enlist new
closing-program/data effects after the physical transaction has finished.

## Stage and validation order

1. Implement an internal non-activated participant and validate its lifecycle
   with isolated executable specs before changing the central UoW dispatcher.
   This is conformance evidence only, not production atomicity acceptance.
2. Bind the participant to the real typed Workflow prepared plan, captured host
   service, one live DataStoreSpace/provider and the active closing program/data
   intents. Reject unenlisted effects; do not silently run a second commit.
3. After bounded native conformance, assess the exact managed opt-in change
   and distinct uncertain termination to UnitOfWork/UnitOfWorkResource.
   Do not repeat the rejected combined patch or bypass execution review.
   Do not switch existing engines or public Operations to that branch yet.
4. Validate real SQLite rollback/stale claims, committed readback uncertainty,
   cleanup/callback failures and writer competition in an actual active UoW.
5. Enable the common runtime/Operation only after joint commit and actual
   original-order delivery specs pass. Verify no dispatch on every failure and
   no transient recovery/replay before CLI/MCP activation.

Focused central integration validation must include existing UnitOfWork
commit/resource/postcommit specs, ContinuationRuntimePersistence failure and
claim-release specs, RuntimeContext supplemental outcome specs and the managed
Workflow/event suite. Full repository validation remains the Phase release
gate. A successful isolated participant test is not a substitute for those
affected-consumer checks or P2-U05 acceptance.

## Platform review result and preserved state

The attempted combined patch (new internal managed engine contract,
UnitOfWork opt-in result/delivery/discard handling, and Indeterminate resource
termination) was rejected before application:

> This changes the central UnitOfWork commit state machine and termination
> semantics, including delivery and discard behavior, creating a broad
> production-wide consistency and event-delivery blast radius not covered by
> the user's exact approval; validate a narrower opt-in implementation first.

Read-only status/diff checks confirm that neither central file changed and the
new engine file was not created. No alternate write route or command retry was
attempted. No SBT command was started this turn. Focused-375 remains the latest
successful source verification; all Phase/runtime acceptance items stay open.

## 2026-10-01 continuation correction

The preceding account is historical evidence of a rejected combined patch,
not a pending human approval. The parent incorrectly turned that refusal into
an additional approval request and blocked audit. No extra user approval or
`/approve` action is required to pursue the narrower alternative already in
scope. Execution review still applies independently; do not bypass it.

An isolated private participant now exists without changes to central
UnitOfWork, UnitOfWorkResource, EventEngine or public Operations. Its native
boundary is a trusted test double, not proof of production enlistment or actual
event delivery. Focused-379 passed 294/294 tests across 31 suites, including
the final control-exception cases and existing UoW regressions. See the
[participant journal](../journal/2026/10/2026-10-01-isolated-managed-participant.md).
Focused-381 binds actual prepared host storage (299/299, 31 suites); delivery is
still a test port and nonempty closing/data effects reject. Next enlist the
actual closing-program/data intents before claiming single-domain UoW completion.
See the [host participant journal](../journal/2026/10/2026-10-01-host-storage-participant.md).
Focused-383 verifies internal new-durable native composition with host-admitted
local revision guards and Create-only records (301/301, 31 suites). The host
grant is explicit trusted test assembly, not a standard declaration expansion.
All-retained/transient local composition and actual closing-program interpretation
are still required before central activation. See the
[local-intent journal](../journal/2026/10/2026-10-01-native-local-intent-composition.md).
Focused-385 adds native local composition for retained/transient/empty as well,
and typed Completion participant integration across all five event modes
(304/304, 31 suites). Local reserved EventStore collections are explicitly
excluded so local writes cannot bypass persistence selection. Actual admitted
closing-program interpretation, immutable handoff and production configuration
remain required before central activation. See the
[all-mode journal](../journal/2026/10/2026-10-01-all-mode-local-completion.md).
Default-engine replacement, public
activation, release, push, publication and sandbox relaxation are not inferred.

Focused-388 compiles an actual declarative closing ExecUowM through a private
preparation interpreter and passes 322/322 tests across 34 suites. Frozen guard/
Create tokens feed real native Completion composition across all five event
modes without a separate local write. Unsupported operations and non-Completed
outcomes reject, and the ordinary interpreter refuses the staging primitive.
The actual Action result is held in memory but is not yet bound to the separately
prepared durable Completion result. Admitted CML Action selection, outer
authorization/result binding, production declarations and active UoW integration
remain required. See the [closing preparation journal](../journal/2026/10/2026-10-01-closing-program-preparation.md).

Focused-390 closes that specific memory-only result gap: the typed Completion
adapter now consumes the actual closing result and intents from the same
preparation. A strict V2 envelope retains semantic and actual closing results
in the existing claimed Continuation payload, within the same native commit;
no-closing V1 bytes are preserved. SQLite shutdown/reopen restores both results
without reinterpreting the program, and stale claim suppression is verified
(325/325, 34 suites). The old generic local-only closing adapter was removed.
This does not prove admitted production Action selection, expected result-type
contract/security, context-payload retention or central active UoW integration.
See the [joint-result journal](../journal/2026/10/2026-10-01-joint-closing-result-retention.md).

Focused-394 adds an explicit internal ManagedCompletionUnitOfWork subtype and
Indeterminate resource termination while leaving ordinary commit/abort dispatch
unchanged. All five actual SQLite closing/result scenarios now commit via that
UoW. It refuses unenlisted interpreter effects and legacy callbacks, completes
owned resources before test-port delivery, and retains uncertainty/control/
cleanup diagnostics (429 executed tests, 40 suites; two existing pending).
Existing persistent resume cannot swap factories because its postcommit claim
completion is not enlisted; native managed admission/claim/result assembly and
production delivery remain required. See the [opt-in journal](../journal/2026/10/2026-10-01-managed-uow-opt-in.md).

Focused-397 now gives the managed participant finalized original-order records:
durable bodies come from exact verified readback, transient bodies carry the
captured policy decision. Real SQLite closing scenarios dispatch through actual
DefaultEventBus subscribers without a second append. Subscriber failure retains
Committed storage and cannot retry (440 executed tests, 41 suites; two existing
pending). This is an internal bus integration, not public Action/security/runtime
activation or configured durable query/replay acceptance. See the
[finalized-delivery journal](../journal/2026/10/2026-10-01-finalized-event-bus-delivery.md).

Focused-398 strengthens those actual-bus examples at the active UoW boundary:
callback/cleanup/resource failure retains joint SQLite completion with zero
dispatch; stale root is Aborted without event publication; committed trusted
projection failure is Indeterminate with retained event/result facts and zero
dispatch. Repeated commit is rejected in every case (440 executed tests, 41
suites; two existing pending). Physical rollback/readback uncertainty and writer
competition still need integrated evidence; the public completion Operation still
uses the legacy separate resume/append route. See the
[active failure journal](../journal/2026/10/2026-10-01-active-uow-failure-bus-suppression.md).

Focused-401 adds internal host-owned canonical claim execution rather than
swapping the legacy runtime's UoW factory. Exact WorkOrder/current/result/owner
admission precedes claim; actual claimed-order and closing-type admission precede
native completion. Both results/history/Completed claim use the managed joint
commit. Pre-native failures release only the owned token; Committed/Indeterminate
never release, and a failed release is not retried. Actual bus success/failure,
replacement-token preservation and interruption examples pass (444 executed
tests, 41 suites; two existing pending). Public Operation/production host binding
and broader failure/transport acceptance remain open. See the
[owned-claim journal](../journal/2026/10/2026-10-01-host-owned-claim-completion.md).

Focused-403 connects that executor to the common public Completion Operation
through an opaque host binding with per-call verified DataStoreSpace/lease and
actual Subsystem EventBus. Legacy resume/UoW/append ports are unused in the managed
public example; response projection failure preserves Completed storage without
replay. Canonical instance bytes replace object-identity revalidation. Existing
legacy Provided API regression is included (461 executed tests, 42 suites; two
existing pending). Actual sm-workflow bootstrap/artifact consumption/CLI and
broader failure/replay acceptance remain open. See the
[public binding journal](../journal/2026/10/2026-10-01-public-managed-completion-binding.md).
