# Long Advance: preserve execution and collect its result

Date: 2026-10-25 (Asia/Tokyo)
Recipients: sm-workflow-dev開発; sm-workflow開発
Status: instruction/common contract correction; no process or Session mutation

The user requests removal of periodic caller log checks and unchanged running
commentary during long Advance calls. Preserve the existing execution and collect
its actual result, with Session, history, repair counts and verification evidence
unchanged.

## Observed case

The consumer successfully adopted Plan 3 and submitted P65-Q5 implementation and
independent Step review in Session `e89e8ba92d074262e4c8a10ef11d1183`.
The retained caller observation reported five suites / 157 tests passing. The
subsequent `repository-full` invocation had a runtime deadline of 5,400,000 ms.
At inspection, its retained event was waiting and response.json was empty; this
did not establish a hang or a terminal result. There were 25 commentary messages
from the first full-validation-start message, many repeating unchanged waiting,
and repeated reads of the same retained log. No token consumption was measured.

The actual execution reference at that inspection was task output
`102-advance-q5-closure-and-phase-validation` under the existing Phase 6.5 task.
Use the latest actual handle/state, not a historical PID copied from this journal.
Do not launch another Advance or kill the current process to adopt these rules.

## Changed contract

Both workstation and component copies of sm-goal-phase, sm-repository-sync and
sm-split-phase now reference the common
[long-running invocation collection contract](../../../spec/native-host-execution.md#long-running-invocation-collection).
The common contract is updated in both distributions. Start/return indicators
remain paired; intermediate commentary is for material already-observed changes,
not each heartbeat or wait yield. Existing installed skill symlinks expose their
workstation sources; component packaging follows normal delivery.

Use existing completion notification or pending-call resume where available.
Where the transport requires explicit collection, use the same handle's supported
wait/resume only as needed for result delivery. Do not add periodic tail/read,
response-file size checks, status probes, process scans, monitoring agents or
status-only AI invocations. Quiet output does not require a narration turn.

Do not remove necessary result collection, abandon the live handle, or treat
waiting as completion. Keep actual continuation/output references in existing
context and process the real result through the ordinary workflow when it arrives.
Explicit user status requests or concrete new diagnostic needs may justify a
bounded read; this exception is not a recurring timer. Preserve all prior results
and uncertainty without replay, reset, restart or additional cancellation rights.

## Responsibilities and limits

sm-workflow-dev owns collection of its current consumer invocation and subsequent
normal Phase continuation. sm-workflow development uses the same principle for
its own long invocations and delegated delivery. Neither needs to send repeated
"still waiting" updates or ask the other to inspect the same log.

This wording does not implement a new event transport. If the tool requires model
resumption to collect output, use its minimum supported mechanism and report that
limitation honestly. Do not claim zero-AI waiting from a skill edit, introduce a
replacement monitoring helper, or make future transport work a prerequisite for
the existing invocation to finish. No Scala code, runtime selection, execution,
Session state, history, repair counts or validation evidence is changed here.
