# Goal Phase Repair Continuation

- Status: Normative behavioral specification
- Scope: ordered native GoalPhase selected Step repair windows
- Since: 2026-10-18
- Authority: [Phase 6.2](../phase/phase-6.2.md) and the
  [managed verification cost guard](../notes/managed-test-suites-and-verification-cost-guard.md)

## Contract

The existing ordered GoalPhase controller, native history, public Operations and
Human completion path own repair continuation. A repair window belongs to the
active selected Step and uses that Plan's existing explicit `repairLimit`.
This specification does not change the default allowance, classify minor fixes,
add public counter fields or introduce a second repair engine.

### R1 — Count actual assigned batches in the active window

`State.repairs` counts automatic repair batches reserved in the current Step
window. `State.ordinaryRepairs` counts its substantive batches. All currently
unclassified repairs are substantive: assigning one batch increments both
counters exactly once. Semantic completion, inspection and selected checks do
not themselves reserve another batch. State and both counts MUST reconstruct
from the same contiguous actual native history; no separate counter store,
history truncation, content snapshot or digest is a reset mechanism.

### R2 — Reset only on accepted Step commit or verified continuation

A `StepCommitObserved` event resets both active counters to zero only after the
controller has admitted the matching candidate, successful selected checks,
independent review, saved closure intent and actual commit. The actual commit
must match that intent, parent and candidate tree. The next Step (or existing
whole-Phase frontier after the final Step) starts at zero. All `ClosedStep`
references and the native history persist.

A candidate completion, semantic repair completion, checkpoint/WIP commit,
unverified or crossed commit, failed close, unknown closure result, ordinary
`ResumeAssignedWork`, document refresh, `Defer` or `Cancel` MUST NOT reset or
refund the active counters. Whole-Phase closure retains its existing contract.

### R3 — Retain typed exhausted repair eligibility

Only a genuine known repairable selected Step check failure or independently
admitted `ReviewOutcome.Repair` can retain pending repair eligibility at
`Decision`. The selected allowance must be positive and already exhausted,
the generation must allow fresh work, and the actual assigned repair reason and
findings must be retained. The controller MUST use this typed pending directive
and its source frontier; reason text is never eligibility evidence.

The validation owner records `repair-required` for a genuine terminated known
Step failure below the selected limit. At an exhausted positive limit it records
`repair-limit-reached`, retaining the actual failed candidate, selected check
identities/types, receipts, exit facts and diagnostics. The existing
`checks-failed` transition moves that result to `Decision`. Direct
`RepairableChecksObserved` exhaustion also retains its directive at `Decision`.
Independent review exhaustion retains that actual review evidence and directive.

Historical `failed` outcomes remain noncontinuable Decisions. A new outcome
string alone cannot upgrade them. Ordinary `NativeFailure`, indeterminate,
malformed, missing or contradictory check facts, Phase-wide check/review,
zero allowance, document conflicts, generation overflow and unrelated Human
decisions MUST NOT create repair continuation authority.

The separately authorized [known failed whole-Phase validation contract](goal-phase-validation-recovery.md#r8--distinct-known-failed-whole-phase-target)
permits an independently directed fresh trial of one terminated failed Phase
check on its same retained candidate. It does not renew a Step repair window,
reopen accepted Steps or reset/refund either repair counter. The exclusions in
this Step repair specification remain unchanged.

### R4 — Ask the exact question and admit independent Human evidence

An eligible exhausted repair window offers exactly
`ContinueRepairs`, `Defer`, `Cancel`. An ordinary semantic/document decision
offers exactly `ResumeAssignedWork`, `Defer`, `Cancel`. The saved
`Decision.presentation.currentSituation` JSON captures the actual three-choice
vector. Public output MUST project exactly one of those two vectors and reject
missing, malformed, extra, duplicate or unknown choices; it MUST NOT substitute
the codec's complete choice vocabulary.

The answer codec recognizes both resumption kinds. The actual Source/controller
and issued question MUST reject `ResumeAssignedWork` at a budget question and
`ContinueRepairs` at another question, including when an independently prepared
record exists for the wrong choice. Displaying a choice never proves consent.

The existing authenticated participant admission, independent host Human
attestation, actual pending question and result target, native completion CAS,
and committed completion path remain mandatory. A semantic AI result, caller
annotation, evidence reference string or fixture response cannot replace them.
`Defer` and `Cancel` retain their existing paused behavior without new work,
automatic re-questioning, counter reset or closure.

### R5 — Renew once and reserve the first batch

An actually admitted `ContinueRepairs` answer is the durable reset event. It
submits typed `RepairContinuationAdmitted`, distinct from ordinary
`DecisionResumptionAdmitted`. Conceptually it resets both counters to zero and
then reserves the first resumed repair batch exactly once. The resulting
`Repair` frontier therefore has `repairs == 1` and `ordinaryRepairs == 1`, with
the same configured positive limit and a fresh work generation.

Continuation clears stale candidate/check/review acceptance and closure intent,
while preserving the selected Plan, original base, completed Step references,
the original assigned failure context and all prior consumed-batch history. It
grants no commands, broader scope, amendment, review acceptance or closure.
Repeated Submit/Advance and reconstruction MUST NOT reset the window or reserve
the batch again. Further batches within the renewed allowance need no per-finding
approval; another exhaustion needs another genuine pending Human decision.

### R6 — Re-enter ordinary inspection, checks and independent review

Current and reconstructed native traversal MUST consume the same retained Human
answer. An exhausted Validation decision uses its genuine last semantic result
and actual controller/validation source to issue Human work; it MUST NOT invent
a semantic result to explain a native failure.

A resumed TEST repair consumes the exact saved failed candidate/diff/check
context with outcome `repair-limit-reached`, admitted only with matching genuine
typed failure facts. A resumed REVIEW repair consumes the actual retained
independent review context and its assigned directive. Semantic repair completion
returns through fresh native inspection, selected checks and independent review.
Prior checks or reviews cannot become acceptance of the new repair candidate.

## Executable examples and evidence limits

| Example | Required observed behavior | Executable specification |
| --- | --- | --- |
| E1 | A repair is counted once; an accepted matching Step commit starts the next Step at zero and retains the closed Step. Invalid closes do not reset. | `GoalPhaseOrderedProgressionSpec` |
| E2 | TEST and REVIEW exhaustion wait; admitted continuation reserves one fresh bounded batch; another failure exhausts the same configured limit. Ordinary Resume, foreign events, zero allowance and Phase failures cannot refund. | `GoalPhaseOrderedProgressionSpec` |
| E3 | Only matching terminated typed failure facts admit `repair-limit-reached`; old `failed`, unknown, malformed, premature and zero-allowance claims do not acquire eligibility. The saved failure context survives its codec. | `GoalPhaseNativeCheckRepairSpec` |
| E4 | Public Operations issue an exhausted-review Human question, reject missing host evidence and the other resumption choice, consume separately prepared specification-owned evidence, retain history/Plan/base, and issue exactly one fresh repair before fresh checks/review. | `ThreeWorkflowGoalOperationSpec` |
| E5 | Separate ordinary CLI commands fail Test, issue one repair, fail Test again, then redisplay the exact `ContinueRepairs` question without rerunning SBT. The existing successful repair path still reaches fresh independent review. | `NativeGoalCheckRepairCliSpec` |
| E6 | Ordinary and exhausted public questions display their own exact three choices; unusable issued presentations fail without a global-choice fallback. | `GoalPhaseOrderedProgressionSpec`, `ThreeWorkflowGoalOperationSpec`, `NativeGoalCheckRepairCliSpec` |

Controller and codec examples are pure evidence, not native execution or consent
proof. The connected operation scenario's selected edits, semantic results,
review responses and independently prepared Human evidence are explicitly owned
by the specification. They exercise the production admission path without
claiming a real Human action or external provider execution. The CLI exhaustion
example stops at the actual pending question and fabricates no Human consent.
These examples neither publish a Phase result nor weaken existing native Git/SBT
Unit-of-Work ownership or participant/host responsibility.
