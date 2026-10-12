# Common Workflow Operation loop

Current contract: PHASE-5.3, P53-01–08. Implementation and validation are in
progress; this document defines the interface, not evidence of acceptance.
CML and generated Operation metadata are the field/type authority.

## Business interface

Every call uses the actual configured Subsystem, component and authenticated
caller. Business parameters are named Properties. A generated Value, its Record
or its existing transport JSON representation is read as that declared business
type at ingress. There is no positional universal-string business endpoint.

| Service / Operation | Named business parameter | Declared response |
| --- | --- | --- |
| GoalPhaseService.startGoalPhase | input: GoalPhaseStartInput; optional sessionId | GoalPhaseStartResult |
| GoalPhaseService.advanceGoalPhase | sessionId | GoalPhaseAdvanceResult |
| GoalPhaseService.submitGoalPhaseWorkResult | sessionId; result: GoalPhaseWorkResult | GoalPhaseWorkSubmissionResult |
| GoalPhaseService.submitGoalPhaseOrderedDecision | sessionId; answer: GoalPhaseOrderedDecisionAnswer | GoalPhaseOrderedDecisionSubmissionResult |
| SplitPhaseService.startSplitPhase | input: SplitPhaseStartInput; optional sessionId | SplitPhaseStartResult |
| SplitPhaseService.advanceSplitPhase | sessionId | SplitPhaseAdvanceResult |
| SplitPhaseService.submitPartitionSemanticResult | sessionId; result: SplitPhasePartitionSemanticResult | SplitPhaseSemanticSubmissionResult |
| SplitPhaseService.submitHandoffScopeResult | sessionId; result: SplitPhaseScopeSemanticResult | SplitPhaseScopeSubmissionResult |
| SplitPhaseService.submitReasoningResult | sessionId; result: SplitPhaseReasoningSemanticResult | SplitPhaseReasoningSubmissionResult |
| SplitPhaseService.submitSplitHumanDirection | sessionId; answer: SplitPhaseHumanDirectionAnswer | SplitPhaseHumanDirectionSubmissionResult |
| RepositorySyncService.startRepositorySync | input: RepositorySyncStartInput; optional sessionId | RepositorySyncStartResult |
| RepositorySyncService.advanceRepositorySync | sessionId | RepositorySyncAdvanceResult |
| RepositorySyncService.submitRepositorySyncWorkResult | sessionId; result: RepositorySyncConflictResult | RepositorySyncConflictSubmissionResult |
| RepositorySyncService.submitNativeMergeReview | sessionId; result: RepositorySyncNativeMergeReviewResult | RepositorySyncNativeMergeReviewSubmissionResult |
| RepositorySyncService.submitRepositoryConflictDecision | sessionId; answer: RepositorySyncHumanConflictAnswer | RepositorySyncHumanConflictSubmissionResult |

Submit also consumes named completionFacts and executionEvidence when supplied.
Six work/Human paths require the genuine resultReference. The three Split
semantic paths resolve their assigned result destination from issued work and
do not require a caller to repeat it. Interpretation evidence belongs in the
semantic result; independent execution/review/Human evidence retains its actual
distinct purpose. No field grants command authority or invents consent.

## Execution and control

Start admits the selected business entry and creates one execution. Without an
optional proposed sessionId it assigns an ID using the actual call's ID source.
Use the returned sessionId for every following Advance or Submit. It identifies
the execution, not the actor/chat/permission. Several executions in one chat
remain separate. Start never searches for a previous execution to reuse.

Advance owns deterministic progression and returns kind Request, Human, Wait
or Terminal, a truthful message, and the actual corresponding business view.
Request/Human identifies submitOperation, submitSessionId and resultType.
Goal repository closure may return a child's submitSessionId while preserving
the parent's outer sessionId. Return the requested value to that exact route.
Do not dispatch internal stages or infer state from the message.

Submit selects actual current issued work/decision internally, passes the typed
business result through existing managed completion and returns sessionId,
accepted, outcome and message after the owning commit. It does not call Advance,
perform another AI task or load a stored copy to prove an unchanged result.
Obtain subsequent work only with an explicit Advance.

## Structured output and actual owners

All business outputs use existing OperationResponse.RecordResponse, produced
from their declared generated Values. A direct typed consumer uses the existing
generated ValueReader on that Record. Protocol JSON/formatting remains at the
existing egress boundary; there is no native WorkOrder/Wait/Terminal String to
decode as the public business result.

Workflow uses the current public CNCF Operation/completion/storage APIs.
Native state, owning transactions, grants and actual effects stay at their
existing owners. Execution/current business/work/decision references replace
candidate discovery and ambiguity selectors. Necessary ordered Goal business
history is not a new comparison certificate; no saved-input equality, hash,
validation stamp, mutable discovery registry or new shared framework is added.

## Verification boundary

Normal direct specs cover the fifteen Operations, semantic and Human paths,
independent interleaving, complete Preview/Apply/repository outcomes, truthful
acceptance and new-execution restart. Actual file scope and occupied-file
non-overwrite remain necessary. Existing scripted checks retain real managed
SBT effects. Generation or compilation alone is not passing behavioral evidence.

Duplicate/competing answers, unique winners, identical replay responses, complete
runtime-history retention and intermediate recovery are not guarantees.
After failure use a NEW execution; do not infer that uncertain prior effects
were rolled back. Phase6 owns CLI/skill/client wiring and actual use adjustment.
