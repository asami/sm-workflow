# GoalPhase native human Decision CML contract

Continue the full sm-workflow Phase 2. Previous turn made progress: refreshed
CNCF artifact 596 and consumer typed answer codec 597 (38/38 focused tests).
This slice defines the CML native human boundary; it does not implement its
managed runtime activation or declare Phase acceptance.

## Definition revision and ownership

The current GoalPhase definition becomes `goal-phase-human-decision-v3` under
the same `GoalPhaseWorkflow` identity. This is a new definition revision, not
rewriting a persisted v2 instance, question or answer. Original v2 workflow and
candidate-admission ABI fixtures are retained as separate `*-v2-abi.json`
resources; current fixtures are selected from the actual new generated outputs
with every field and source correlation preserved. There is no automatic
instance migration or identity/schema alias.

Two existing application revision guards and the dependent current-definition
test inputs/expectations are aligned to v3. Existing observation Operations
`prepareGoalPhaseHumanDecision` / `recordGoalPhaseHumanAnswer` and their storage
schemas and question identities remain unchanged. The new native route must
allocate its own question identity and adjacent pending revision.

## CML state and Operation contract

A separate `human` constituent uses `GoalPhaseHumanDecisionLifecycle`:
`Idle --human-question-issued--> Awaiting --human-answer-recorded--> Answered`.
The initial composite is `step.Awaiting, human.Idle`. Composite derivation
distinguishes `Pending`, `HumanPending`, `HumanAnswered` and `Closed`. All six
reachable step/human configurations have explicit complete mappings, including
three projections to Closed. These are state projections, not permission to
close while a question is pending; the runtime authority/transition admission
remains a separate obligation.

`PresentHumanDecision` is an OPERATION, not an AI JUDGMENT. Its distinct
`human-decision-capability` requires
`HumanDecisionService.presentGoalPhaseHumanDecision`, with exact typed
`GoalPhaseClosureAuthorityInput` and `GoalPhaseHumanDecisionAnswer`. The new
Provided completion is `GoalPhaseService.submitGoalPhaseHumanDecision`, taking
the latter result type and returning `GoalPhaseHumanAnswerResult`.

The authority input matches the existing closure input shape; its candidate
correlations are not new workflow control hashes. Both new Operation input
Values carry the standard COMMAND input-kind metadata. Answer choices do not
close a Step or grant Git/effect permission. Review and repair capabilities and
the ReviewStep/AdmitStepClose judgment/admission relationship remain unchanged.

The review/repair fixture checks all five exact Provided declarations but
advertises only its four actually implemented Operations. No placeholder human
completion is installed or advertised as executable.

## Executable validation and repairs

- 598: standard generator refused incomplete Closed derivation (human role
  omitted). No compile/tests. The remaining complete mappings were added.
- 599: generator refused missing COMMAND metadata on a new input Value. Both
  relevant Values received explicit metadata. No compile/tests.
- 600: real sbt-cozy generation emitted 86 Scala sources, compiled successfully
  and passed both existing generated ABI tests. Both exits 0, lock released.
- 601: the GoalPhase accumulator ran 187 tests; 158 passed, 29 failed. One
  expected Operation vector and 28 fixture initialization cases still enforced
  the old four-declaration list. The exact declaration list was updated without
  adding an unbound human Operation to the fixture's actual Service.
- 602 (`SMWF-NATIVE-HUMAN-CML-REGRESSION-DIRECT-20261001-602`): the same
  accumulator passed 187/187 tests in 26 suites, zero failed/aborted/canceled/
  ignored/pending. Saved summary inspected, both exits 0 and lock released.
  It includes the added human required/provided type and state assertions,
  complete generated ABI fixture/source-provenance comparison, CNCF admission,
  review/repair/start/routing, configured managed host and existing human
  observation regressions. This is not full Phase validation.

Log: `/tmp/skill.cncf.d/a-75932b55-c8ca-476d-8e46-9d5ded9f2c32/15757-20261001T115311Z.log`.
Summary: `/tmp/skill.cncf.d/a-75932b55-c8ca-476d-8e46-9d5ded9f2c32/15757-20261001T115311Z.summary.json`.

Parent `/root` directly implemented under user-directed limited recovery;
model/effort are not directly observable. Fresh permission storage, scoped
escalation and the canonical shared SBT wrapper remain in force. No agent,
commit, push, publication or definition migration ran. No owned SBT is live;
diff whitespace check clean.

## Required next runtime boundary

Bind native publication from the actual accepted reviewed instance through
the managed Decision capability, preserving exact candidate/choices/context
and separate pending identity/revision. Reopening must recover that pending
question, not rebuild or overwrite the old observation question. Bind the
dedicated generated completion to exact host-verified human evidence and a
pure adjacent transition; atomically retain the answer and history through
the shared provider. Response failure and retry must not replay verification
or effects. A stale accepted-review observation is not current native authority.

Real human issuer/verifier, native application runtime execution/reopen,
independent bounded command permissions/effect recovery, production CLI/server/
MCP and remaining workflows plus full Phase review/validation/release remain open.
