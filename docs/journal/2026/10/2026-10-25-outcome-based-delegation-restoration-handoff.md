# Outcome-based delegation and uninterrupted authorized restoration

Date: 2026-10-25 (Asia/Tokyo)
Recipients: textus-control-center開発; SimpleModeling開発; sm-workflow開発
Status: skill/common contract correction; no runtime implementation or live action

## Direction and observed friction

The user requests coherent outcome delegation, fewer fine-grained review/progress
round trips, and correction of the earlier RRC handoff so missing RRC does not
stop already-authorized restoration. Preserve all existing Sessions, history,
repair counts, accepted results and verification evidence.

The inspected Control Center turn contained 13 wait_threads calls, 8 cross-chat
sends and repeated interactions with the same reviewer. These are tool counts,
not measured tokens or a claim that each wait caused a separate AI invocation.
Both chats narrated the same SBT progress. Design preparation, consumer review,
further design clarification and a frozen implementation instruction became
successive handoffs. Even a version-date comment received separate closure work.
Some review found a real public optional-field decoding problem; preserve those
findings instead of discarding all review as waste.

The latest inspected qualification was 21 passed / 4 failed out of 25, with the
third suite not reached. The producer proposed seven already-admitted source
paths. Its design deferred further checks until a new RRC restoration owner
became available. That dependency was not intended: the earlier handoff allowed
an architectural migration to become a new recovery gate. The earlier 18/7 result
and all subsequent results remain separate. Use the latest actual evidence;
these snapshots are not replacement Session state.

## Corrected responsibility assignment

**SimpleModeling development** owns the existing bounded runtime-restoration
deliverable: diagnosis, admitted source correction, required validation through
existing execution facilities, coordination of applicable independent review,
and a qualified result for the consumer. Continue from current work and reuse
the existing diagnosis, public-contract questions, review and test evidence.
Do not wait for a consumer instruction freezing every edit. Resolve routine
technical choices within scope and consolidate genuinely unresolved semantic
questions. Existing worker-role restrictions remain: this outcome owner
coordinates the required roles rather than granting them new command authority.

**Control Center development** owns the consumer requirement, acceptance boundary
and supported same-Session continuation. Receive the coherent producer result;
do not re-diagnose every fixture, independently poll the same process/logs or
mediate every technical question. If the existing reviewer is reachable only
from this chat, relay a consolidated review package and consolidated findings
at the applicable boundary. Do not discard useful review or add another reviewer
solely to remove this routing limitation.

**sm-workflow development** implements RRC in parallel and provides its qualified
public adoption path. Coordinate overlapping source ownership with the existing
restoration owner; do not start a competing implementation of the same wire fix.
RRC controls the future common repair path, but its absence is not a new
prerequisite for current authorized restoration or qualification.

## Execution, review and notifications

Use the existing applicable standalone restoration route and its actual permissions
and limits until RRC is delivered. Normal Phase checks remain inside sm-workflow.
Do not build a new skill/helper controller, counter or automatic retry mechanism.
An actual existing runtime decision or unavailable current execution capability
still requires its supported handling; distinguish it from an unimplemented
future feature. Unknown/live effects are collected or reconciled, not replayed.
Passing qualification does not grant additional publication authority.

One execution owner collects terminal results. Consumers receive completion,
material scope/contract changes or a decision the owner cannot resolve. Use
existing notifications and already-available observations for user-facing updates;
do not create status-only AI turns or duplicate polling solely for narration.

Review unresolved semantic changes together at the required acceptance boundary.
Reuse compilation, focused tests and existing review. Dates/comments and routine
local changes without new semantic questions do not require separate review
round trips. Preserve independent review where required; the implementing owner
cannot self-certify it. A new review is justified by a material new question,
not merely another file, command receipt or handoff.

The result handoff includes actual scope, outcomes, applicable evidence references,
remaining failures/uncertainties and the next supported consumer action. Reuse
existing records instead of adding new approval/snapshot ledgers. Keep the same
consumer Session, original base, accepted work, active/cumulative repair counts
and all historical verification outcomes. Do not restart, refund counts, replay
accepted Submit or directly edit Session storage.

## Updated sources and qualification boundary

- [Common delegation contract](../../../spec/native-host-execution.md#outcome-based-delegation)
  and its workstation-distributed counterpart define outcome ownership.
- Component and workstation sm-goal-phase skills reference that contract and
  explicitly preserve the transitional standalone restoration route.
- [RRC specification](../../../spec/runtime-owned-repair-control.md),
  [design](../../../design/runtime-owned-repair-control.md),
  [plan](../../../phase/runtime-repair-control-correction.md) and the
  [earlier handoff](2026-10-25-runtime-owned-repair-control-handoff.md) are corrected.

This change edits instructions/documents only. It neither runs SBT nor installs a
runtime nor changes a Session. Actual restoration qualification, RRC qualification
and consumer adoption remain separate results. The installed skill is a symlink
to the workstation source, so that instruction correction is immediately visible;
component packaging still follows normal delivery.
