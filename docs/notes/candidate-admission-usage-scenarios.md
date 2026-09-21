# Candidate-Admission Usage Scenarios

- Date: 2026-09-21
- Status: Important behavioral guidance; non-normative usage scenarios
- Scope: sm-workflow
- Related: [Candidate-Admission Model](candidate-admission-model.md), [Skill Adapter Design](sm-workflow-skill-adapter-design.md), [Goal Phase Workflow](sm-goal-phase-workflow-definition.md)

## Position

Candidate-Admission is not mandatory for every sm-workflow Workflow or StateMachine. These scenarios document the important behavioral image where a Skill/AI has semantic autonomy and the Workflow decides whether the resulting candidate can become an accepted state.

The generic Admission runtime is supplied by CNCF Phase 90 above the Phase 77 Continuation runtime. sm-workflow specializes it with software-development candidate/evidence/closure semantics.

## Scenario 1: Step close with no prior review

The Skill implements the Step and updates Phase/Checklist and other management files to the state it believes is complete, then submits RequestStepClose without review evidence.

```text
Skill
  implementation + management update
  -> RequestStepClose(candidate)
sm-workflow / Admission
  -> full Step review evidence missing
  -> ReviewStep semantic Action
CNCF Phase 77
  -> Suspended(Continuation)
Skill
  -> ReviewResult(ACCEPT)
Admission
  -> re-evaluate same candidate/closure intent
  -> deterministic validation
  -> CommitChanges
  -> Step Closed
```

The Skill does not submit a second close request after review.

## Scenario 2: Proactive scoped review is useful but insufficient

The Skill performs a focused review while working and submits that evidence with the close request.

```text
Candidate
  + SCOPED review ACCEPT @ R20
  -> RequestStepClose
Admission requirement
  STEP_FULL review @ R20
  -> scoped evidence retained but insufficient
  -> ReviewStep(STEP_FULL)
  -> ACCEPT
  -> admission
  -> commit
```

The proactive review is not discarded, but it does not override the Workflow's required scope.

## Scenario 3: Proactive full review avoids duplicate AI work

A full review against the exact candidate snapshot is supplied as fresh evidence. Admission reuses it, requests no duplicate Review Continuation, runs deterministic validation and proceeds to commit/Step Closed. This is an important cost property.

## Scenario 4: Repair makes prior evidence stale

A full review may find blockers. After repair creates a new candidate revision, Admission evaluates evidence coverage/freshness. A bounded delta can require focused ReReview; a broad delta can require STEP_FULL ReReview. The application closure policy owns focused/full semantics; CNCF Phase 90 supplies generic evidence/admission mechanics.

## Scenario 5: Skill believes work is complete, Workflow disagrees

The Skill submits the state it believes is ready. Admission may report missing review evidence, stale evidence, missing/failed validation, unresolved blocker lineage, an authority requirement, or successful admission. This disagreement is expected: semantic candidate construction and governed admission are deliberately independent.

## Scenario 6: Admission gap needs no AI

Not every gap becomes a semantic Continuation.

```text
Candidate
  -> Admission Evaluation
  -> validation evidence missing
  -> deterministic validation Provider
  -> evidence produced
  -> Admission Evaluation
  -> admitted
```

Only a semantic gap selects a semantic Action that may suspend through Phase 77 Continuation.

## Scenario 7: Authority gap

If closure requires authority that cannot be derived mechanically, Admission uses a Decision boundary. AI must not invent approval. After an authorized result, Admission Evaluation resumes.

## Scenario 8: Split and repository-sync candidates

For SplitPhaseWorkflow, a Skill may construct/enrich a split proposal candidate. Admission validates deterministic numbering, boundaries, collision/evidence requirements and authority before application.

For RepositorySyncWorkflow, a Skill may construct a semantic conflict-resolution candidate. Admission validates it against the frozen merge boundary, validation/review evidence and authority before deterministic merge/commit progression.

These profiles need not force CAM where their normal path is fully deterministic.

## Behavioral summary

```text
Skill/AI
  "This is the state I believe should be accepted."
       |
       v
Candidate Submission
       |
       v
Admission
  "Does this candidate satisfy the Workflow contract?"
       |
       +-- yes -> deterministic completion/commit
       |
       +-- no  -> identify the gap
                    |
                    +-- semantic -> Action -> Continuation if external
                    +-- deterministic -> Provider
                    +-- authority -> Decision
```

The important operational principle is:

> The Skill constructs what it believes is a final candidate; sm-workflow asks CNCF Admission to determine what, if anything, is still required before the Workflow can accept it.

This usage pattern is important guidance for sm-workflow design and Skill generation, but it does not require all Workflows to use Candidate-Admission.
