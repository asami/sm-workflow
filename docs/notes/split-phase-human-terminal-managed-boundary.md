# SplitPhase Human answer to managed Terminal

Status: revised design with an unverified consumer implementation candidate.
Observed: 2026-10-04 22:26:33 UTC / 2026-10-05 Asia/Tokyo.
The initial stronger producer prerequisite was rejected by the actual independent
design review. Its unchanged FINDINGS report is retained at
`.codex-workflow/phases/PHASE-2/human-terminal-boundary-design-review-1-result.json`.
This revision distinguishes the existing contract from that optional proposal;
it is not runtime acceptance, a new repair frame or permission to edit another
checkout. The initial proposal remains chronological evidence in the journal.

## Existing behavior that must remain

`submitSplitHumanDirection` independently admits the Human result, retains the
complete canonical answer and typed closing result, and appends HumanAnswered
in CNCF's managed Decision completion. All three answers currently use that
same answer-recording boundary. Identical retained submissions are observations,
not renewed consent or another closing program. The completed correction917
proves the two interrupted Defer cases only; it does not implement a Terminal.

The original Workflow design gives Defer a BLOCKED outcome and Cancel a
CANCELLED outcome. These are distinct application outcomes, not a new AI task,
successful document application or an indefinitely actionable WAIT. Resolution
references still require Workflow-owned resolution-data validation. An answer
reference is not permission to select a processor, mutate documents or change
the original scope.

## Source evidence and the changed implementation order

- Before the terminal candidate, `SplitPhaseManagedAdvanceServiceOperation`
  routed HumanAnswered through canonical retained-answer observation and then
  reported unavailable resolution/terminal handlers. The new candidate routes
  recorded Defer/Cancel and their matching Completed successor separately;
  it has not been compiled, executed or independently reviewed.
- `SplitPhaseNativeHumanAnswer.scala:28-37` produces Active/HumanAnswered with
  the adjacent full native history. Changing this to immediate Completed would
  change the existing answer-recording contract, not implement its successor.
- CNCF `WorkflowMechanicalTransition.scala:52-67` admits only Active,
  unsuspended predecessors and Active successors. Its current public-package
  `WorkflowMechanicalTransitionSpec` explicitly includes a Completed successor
  among the refused variants. This is an intentional contract, not a missing
  conditional that the consumer may silently relax.
- `WorkflowManagedMechanicalTransition.executeC` already supplies the desired
  owning Subsystem/shared resource, captured declarative Creates, selected
  events, managed UnitOfWork and verified native receipt/readback. That route
  currently cannot represent the terminal successor above.
- `WorkflowComponentStorage.bindInstancesC` exposes canonical single-instance
  append. `WorkflowInstanceDataStorePersistence.append` returns authoritative
  native-row readback. It does not co-commit a separate terminal receipt and
  selected event batch. Neither separate artifact is an existing requirement
  for deterministic Defer/Cancel termination with no new effects or events.
- `WorkflowManagedDecisionCompletion` is a genuine issued-Decision completion,
  not an ordinary successor operation for an already answered, unsuspended
  instance. Replaying it or fabricating another Decision to obtain a closing
  transaction would add an interaction with no semantic/Human work.
- `WorkflowProtocolV1.projectTerminalC` requires the matching durable Completed
  instance. It projects a continuation; it does not persist that instance or
  prove a terminal payload was committed.

The source-supported minimum uses the existing owned canonical instance append
and projects a typed result deterministically from the already committed Human
answer plus its exact Completed successor. No new producer capability is required
for this minimum. Preserve the active-only mechanical contract and actual managed
Human answer. Do not use private SQL, a fake AI/Human result, a Terminal over
Active or a completion response presented as Terminal.

## Conditional stronger producer capability, not a current prerequisite

Only if a concrete nonderivable terminal fact or selected atomic effect is
required should a new producer capability be admitted. That stronger API would
express ordinary deterministic termination separately
from issued-result completion and ordinary active mechanical progress. Its
validated construction selects one owned Active/unsuspended predecessor, one
Completed/no-progression/no-suspension append, an explicit terminal reference,
the actual Operation and a complete typed output. No application writer claim,
management hash, exclusion lock or competing-writer retry is introduced.

Such stronger publication would reuse CNCF's same Subsystem/DataStoreSpace/physical provider and
managed UnitOfWork machinery. Native history, retained terminal output and any
actually selected local effects/events commit or roll back together. EventStore
selects persistence from event kinds; the caller does not supply persistence
flags and the Subsystem declaration does not persist all events. The existing
mechanical receipt codec must not start accepting terminal records under its
unchanged active-only schema.

A retained query returns the originally committed typed output with its actual
current native record. It executes no transition, allocator, consent verifier,
processor, command or event delivery. An uncertain immediate acknowledgement
does not authorize replay; a later read can establish committed facts. A
contradictory or one-sided result is an explicit failure, not automatic repair.
This is a storage/crash-consistency boundary, not application pseudo-exclusion.

`DEV-SMWF-MANAGED-TERMINAL-PUBLICATION-001` records this conditional option,
not a blocking upstream dependency. No producer API/schema change is selected
for the minimum path; no second provider or unchecked generic "allow Completed"
flag is introduced.

## Required consumer behavior

1. Public `advance` loads current state and authenticates the original relay
   through the existing current-call/original-Start relationship.
2. At HumanAnswered, one natural admission boundary joins the actual canonical
   answer, issued Decision, complete native prefix and whole original frontier.
   Produce an internal typed observation; do not decode the JSON acknowledgement
   as execution authority or repeatedly rebuild checks downstream.
3. Workflow dispatches the closed answer alternatives. Defer constructs BLOCKED
   and Cancel constructs CANCELLED under the declared CML transition. Both use
   the existing canonical owned instance append with authoritative readback,
   with no AI request, separate result row or event batch. Neither applies
   planning documents or executes an external command.
4. ProvideResolution selects the explicitly referenced resolution data, checks
   its original context/frontier and admissible semantic effect, then resumes
   the Workflow-selected validation/planning path. Missing resolution is not a
   Terminal, a Defer fallback or authority to run a skill-selected handler.
5. Completed state admits its exact successor relation to the full retained
   Human answer before the older planning route, derives the same closed result
   and fixed presentation, and returns native `WorkflowContinuation.Terminal` via
   `WorkflowProtocolV1.projectTerminalC` and the canonical Terminal codec.
   Repeated advance and reopen return the same outcome without another effect.

CML must declare the answer-successor events/outcomes and generated contracts
must match the actual installed Operation. The consumer must not introduce a
private terminal state machine detached from those declarations. Existing
HumanAnswered evidence remains a prefix of the terminal instance history.

## Verification frontier

The minimum path must keep active mechanical progress refusing Completed and
verify one genuine canonical successor append. Its result must be derivable
without changing clocks, mutable policy or fresh external facts. Pre-write
failure must not append; uncertain acknowledgement must refuse immediate success
or retry and be recoverable by later authoritative observation. Malformed
Completed history, wrong causation, absent/conflicting answer, foreign original
context or incompatible definition must fail explicitly without automatic repair.
Use real canonical storage, not a private substitute accepted as production evidence.

Only an independently admitted stronger publication feature would require new
producer specs for atomic native/result/effect/event publication and rollback.
A CML transition event name does not itself select an EventRecord batch.

Consumer executable specs must cover both Mechanical and PartitionCompletion
original roots, Defer and Cancel, exact distinction between answer and terminal,
no new consent/allocation/AI work, retained submission and advance after terminal,
Service/CLI observation, physical reopen and original-relay refusal. Resolution
has its own complete admission/resumption cases; a Defer/Cancel-only success
cannot close Human handling, production ingress or the whole Phase.

SBT remains a fresh typed request to the registered command runner through the
shared serial wrapper, followed by a fresh read-only independent review. Actual
producer changes, if later required, need their admitted source/validation/
dependency-refresh boundary before consumer evidence can use them. No test is
run merely to give a documentary clarification a green result, and917 is not
replayed or represented as validation of the new terminal candidate.

## Retained full scope

This removes an unsupported implementation dependency, not any Goal requirement. Production
Start/default verifier, all later reasoning/document/apply branches, one-shot,
server/MCP/separate-process modes, resident references, managed external
commands, generic distributed sm-* skills, measured AI cost/round trips, full
executable specifications, help/docs and independent release gates remain.
Original9/R5/A7, unknown historical counter null, explicit limit3/closed3 and
all history remain unchanged. Consumed executions remain575-through917.
