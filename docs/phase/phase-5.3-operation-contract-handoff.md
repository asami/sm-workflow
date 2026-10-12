# Phase 5.3 Operation contract handoff to Phase 6

Created: 2026-10-12. Completed: 2026-10-13.
This is the delivered interface and verified handoff for the formal LOCAL
Phase 5.3 release, not authorization to start Phase 6. Canonical acceptance
remains in [Phase 5.3](phase-5.3.md) and its
[closure](phase-5.3-closure.md), effective with the matching verified release commit.

## Interface to consume

The finite fifteen-operation input/response table is
[Common Workflow Operation loop](../spec/common-workflow-operation-loop.md).
CML, generated Provided/Operation metadata and the normal factory bind the same
named business Values. Current definitions are GoalPhase v6, SplitPhase v14,
RepositorySync v12 (`*-business-operation-*`). Do not use the historical
Phase 5.2 universal `arg1` examples or its ambiguity selectors.

- Start takes named `input` and optional proposed `sessionId`; returns its
  actual start acknowledgement and canonical `sessionId`. Without a proposal
  the owning call assigns a new ID. A chat can start distinct executions.
- Advance requires that execution's `sessionId`. Read `kind`: Request, Human,
  Wait or Terminal. It owns next-work selection and managed progression.
- Request/Human supplies `submitOperation`, `submitSessionId` and `resultType`,
  plus the declared work/question view. Perform only that semantic work or
  obtain the actual Human answer, and return its typed result/answer.
- Submit consumes named `result` or `answer` and returns `sessionId`, `accepted`,
  `outcome` and useful `message` after its actual owning completion commit.
  Display the message; on acceptance call Advance explicitly. Do not call a
  model to reconstruct the acknowledgement or load additional internal records.
- Wait reports the actual condition; it is not an unconditional polling loop.
  Terminal reports the actual outcome. Errors/rejections are not successful
  submissions and do not authorize success-path Advance or uncertain effect replay.

GoalPhase can request internal RepositorySync work. Preserve the outer Goal
session for subsequent Goal Advance, but submit that child work to the returned
`submitSessionId` and operation. Do not derive either route from message text.

Six submit paths require a genuine `resultReference`; three Split semantic
paths use the destination already assigned by the issued work. Optional
`completionFacts` and `executionEvidence` keep their actual distinct purpose.
Do not echo run/revision/snapshot/native work envelopes or query an internal
record to discover the return route. Actual Human consent and command/file
authority remain at their existing host boundaries; AI output is not consent.

## Existing transport and direct Operation use

Named Properties accept the generated business Value, its Record or the
existing JSON representation of that declared value. JSON is a transport
representation, not a universal string business endpoint. For example:

```scala
val start = Request.of(
  component = SmWorkflowComponent.componentId.name,
  service = "GoalPhaseService",
  operation = "startGoalPhase",
  properties = List(Property("input",
    GoalPhaseStartInput("phase-6", "sm-workflow"), None)))

// Consume GoalPhaseStartResult from the returned Record, then retain its ID.
val advance = Request.of(
  component = SmWorkflowComponent.componentId.name,
  service = "GoalPhaseService",
  operation = "advanceGoalPhase",
  properties = List(Property("sessionId", started.sessionId, None)))
```

These are request-construction examples for an already configured/authenticated
host; they do not install authority. Business results use the existing
`OperationResponse.RecordResponse` and generated `ValueReader` at the direct
consumer. Actual CLI/skill/chat formatting and retention of `started` belong
to Phase 6. There is no business DTO in primitive-only Scalar and no native
String response for the caller to decode.

## Acceptance evidence

Phase53 focused06 actually compiled and executed all planned28 suites:
144 tests passed,0 failed,0 aborted; SBT/wrapper0 and shared lock released.
This includes the three generated ABI suites at their real package names,
normal all-three semantic/Human/completion paths, independent session
interleaving and new-execution restart. Earlier failed attempts remain recorded.
Current CML generation produced142 Scala sources before these tests.

The planned `runtimeScripted/scripted runtime/operation-sbt` also passed its
three actual managed-SBT scenarios, with SBT/wrapper0 and lock released:
Repository successful check/convergence, Repository failed product check/Wait,
and Goal Step/Phase checks followed by actual RepositorySync convergence.
The connected implementation Step's independent review and focused re-review
are recorded. Five review findings were resolved by the focused re-review;
the remaining three documentation statements were closed by a separately
recorded, limited parent M0 amendment. This is not a rewritten reviewer PASS.
The final affected six suites passed42 tests, with SBT/wrapper0 and lock released;
the earlier focused/scripted evidence is retained only for its applicable scope.
The accepted implementation Step is4401aa131c2ad41e9f0a608352079c11e3c08e91.
Exactly one epoch1 Phase full review reported five bounded findings; all were
closed through the recorded focused repairs. The latest independent focused03
PASS accepts the final twelve local test/probe corrections without weakening
assertions or adding a production contract. Cumulative Phase repair3 and all
original histories remain; no reset or new repair frame.

Actual final repository-full02 passed95 suites/616 tests, zero failures or
aborted/canceled/ignored/pending tests, SBT/wrapper0 and shared lock released.
Actual current CAR input reports56OK269WARN0FAIL with static24OK11WARN0FAIL;
existing nonblocking Hygiene remains separate. Full01 and focused14 retain
their actual failures. Formal LOCAL release uses closure binding
smwf-phase53-closure@1 and commits the canonical checklist/closure/findings.
No CLI/skill/chat-client, Web/OpenAPI/MCP or packaged-publication success is claimed.
No upstream capability was added.

## Phase 6 boundary

Implement and adjust the real skills/client loop through use of this interface.
Skill names remain `sm-*` (including sm-goal-phase, sm-split-phase and
sm-repository-sync); legacy `cncf-*` operation is not replaced by this handoff.
The Workflow requests AI work and the current Codex returns the requested
result; do not add a direct Workflow-to-AI execution service as a prerequisite.

No new same-execution duplicate-answer exclusion, unique winner, identical
replay response, complete runtime-history retention or intermediate recovery
guarantee is supplied. Keep actual file scope/non-overwrite protection and use
a NEW execution after failure. Phase 6 owns CLI/skill/host wiring and normal
use adjustment; it does not retroactively change earlier formal releases.
