# Phase 9: Acceptance-Bounded Convergence and Provider Routing

Status: planned
Planned: 2026-10-08
Depends on: Phase 6, Phase 8
Related: CAR lint, deterministic validation, Provider Routing, textus-experiment

## Goal

Extend Provider Routing from provider-declared DECLINED escalation into validation-observed model switching.

sm-workflow MUST execute a bounded, cost-aware correction loop. Lightweight compile/targeted-test/diagnostic/repair-history evidence is used first to observe convergence. CAR lint is a heavier architectural/Textus-conformance gate and MUST NOT run on every inner-loop repair merely to check progress. Re-route when lightweight evidence already shows non-convergence, or when a later CAR lint result shows that the candidate is architecturally unsuitable.

The purpose is to expand the practical range of inexpensive/local models without requiring them to have perfect pretrained knowledge of Textus, OFP, or project architecture.

Local LLM deployment and capability checks are developer-owned manual reference
work, following the [trial notes](phase-8-local-llm-reference.md). They are not
Phase 9 entry or completion gates. Deliver and verify routing with available
admitted providers/profiles, including hosted providers; no local adapter,
installation or local-model performance result is required.

Phase 9 also turns the minimum Acceptance Boundary contract established in Phase 6 into the governing convergence boundary. Validation or Review work outside the Effective Acceptance Boundary is not evidence that the provider is failing to converge. sm-workflow must distinguish genuine inability to satisfy required quality from provider-generated assurance work that should not have been undertaken.

## Use-case-first planning and review (2026-10-11)

Acceptance Boundary applies before implementation: planning starts from user-visible Use Cases and end-to-end scenarios, not from a bottom-up inventory of components to perfect. Plan bounded vertical Use Case Slices with observable outcomes, required quality attributes, explicit non-goals and early integration evidence. Components and infrastructure are supporting tasks, not independently expanding completion goals.

Planning Review must check that each proposed Slice reaches an actual user-facing boundary, that component assumptions are validated through early integration, and that acceptance criteria are sufficient but do not introduce speculative assurance. Reject plans that defer all integration until component completion or turn hypothetical abnormal behavior into primary work.

Implementation/Admission Review must verify the integrated Slice against the same Effective Acceptance Boundary. A locally passing component test does not substitute for a connected user scenario. Conversely, an out-of-boundary component guarantee or test does not block closure. Findings are classified as required current-Slice defects, explicit future work, or rejected assurance; AI findings alone cannot expand scope.

Phase 6 remains responsible for minimal boundary propagation; Phase 9 owns the fuller planning/review/convergence interpretation. Reuse existing Goal/Phase planning, WorkOrder, Admission and Review paths. Do not introduce a second planning engine, exhaustive traceability database or additional mandatory review cycles.

## 2026-10-11 clarification: external planning and contract-first slices

Development planning and its approval occur separately before sm-workflow execution. sm-workflow consumes the adopted plan and Project Acceptance Boundary; it does not own a new mandatory plan-authoring or planning-review workflow. Existing PLAN_DESIGNING/PLAN_ADMITTING states are limited to operational decomposition/admission of an already adopted plan, not authorization to invent new user requirements or quality guarantees.

A top-down development unit may originate from a Use Case **or** an externally observable contract such as an API/Operation, CLI/MCP command, UI action, event/message or other published interface. A full end-to-end Use Case is not mandatory for a small API change. Each unit must have observable acceptance criteria and connected evidence through the necessary implementation layers. Internal components are supporting tasks, not the default completion unit.

Planning review happens in the separate planning process and checks external-contract coverage, integration assumptions, dependencies, slice size and Effective Acceptance Boundary alignment. Execution-time Review checks delivered behavior against the adopted plan and boundary; a discovered planning gap becomes a proposal for external plan revision rather than automatic scope growth. Reuse existing Workflow operations and avoid new mandatory planning gates.

## Two escalation sources

Phase 9 distinguishes:

1. **Voluntary escalation** — the active provider returns DECLINED.
2. **Observed escalation** — the provider continues attempting the work, but sm-workflow observes from deterministic validation history that the attempt is not converging and selects another admitted provider.

BLOCKED remains distinct: it represents missing authority/input/design/context where model switching is not the appropriate response.

## Feedback loop

Phase 9 uses two validation tiers.

~~~text
Semantic WorkOrder
  -> Provider A
  -> implementation / repair
  -> lightweight convergence loop
       compile / targeted test / typed diagnostics / repair history
       +-> stuck/diverging -> early Provider Routing
       +-> converging -> bounded repair with Provider A
       +-> candidate formed
             -> CAR lint when required by policy/gate
                  clean -> normal Review / Admission
                  repairable -> bounded feedback to Provider A
                  deep/repeated -> Provider Routing -> Provider B
~~~

The loop MUST be bounded. Expensive verification follows the existing verification-cost-guard principle and MUST NOT be run merely as a precautionary progress probe.

Before repair or re-routing, classify actionable evidence against the Effective Acceptance Boundary. Boundary-required failures may drive repair/convergence decisions. Out-of-boundary assurance work must instead be stopped, rejected, or surfaced as a nonblocking proposal; it MUST NOT justify another repair cycle or escalation to a stronger provider.

## Lightweight convergence observations

Phase 9 should support deterministic observations sufficient to identify at least:

- compile/test failure class recurring without meaningful progress;
- material finding count/severity not improving across bounded attempts;
- repair oscillation where previously resolved findings repeatedly return;
- explicit provider DECLINED;
- successful deterministic convergence.

The first implementation SHOULD prefer simple explicit rules over a learned or opaque convergence score.

A finding identity may use rule identity, source location/semantic target, typed diagnostic identity, or another normal diagnostic key. Phase 9 MUST NOT introduce custom content hashes or integrity machinery merely to detect recurrence.

## Model switching

A model switch is re-execution of the same semantic WorkOrder under a newly selected admitted provider/profile. It is not a new user task.

Preserve:

- semantic work identity and acceptance criteria;
- relevant repository/commit/worktree context;
- current candidate state when safe and meaningful;
- deterministic validation evidence;
- prior provider attempts and outcomes;
- ordinary repair count and consumed automatic Fix cycles under the existing
  Phase 5/6 policy, including the absolute hard limit of 10 automatic Fix cycles;
- feedback already supplied;
- reason for re-routing.

Provider selection remains governed by the provider-neutral ExecutionRequirement and routing policy. Workflow semantics MUST NOT encode named models such as Qwen, Ollama, Luna, or Sol.

A routing policy MAY prefer another local provider before a stronger hosted provider when measured capability and cost justify it.

## CAR lint gate and correction feedback

CAR lint is heavier than ordinary compile/targeted-test feedback. It SHOULD normally run after a plausible candidate has formed, at an architectural gate, or when policy specifically requests it. If lightweight evidence is already clearly abnormal, sm-workflow MAY escalate before paying the CAR lint cost.

CAR lint results then drive a second decision: clean -> continue; local/repairable -> bounded same-provider repair; deep/severe/repeated or poorly converging findings -> re-route.

## CAR lint as correction feedback

CAR lint is treated as a machine-readable architectural/Textus-conformance feedback source, alongside compiler and test evidence.

The desired separation is:

- compiler: language/type conformance;
- tests: behavioral conformance;
- CAR lint: architectural/Textus programming-model conformance.

CAR lint findings SHOULD carry enough typed information for a reasoning provider to understand what rule was violated, where, why it matters, and what class of correction is expected without prescribing a brittle textual patch.

## Routing policy

Initial routing policy MUST be explicit and versioned. Representative policy:

- first lightweight actionable failure -> normally return feedback to the same provider;
- lightweight stuck/diverging/oscillating behavior -> re-route early without requiring CAR lint;
- after candidate formation, run CAR lint only where required by gate/policy;
- repairable CAR lint findings -> bounded same-provider repair; repeated/deep findings -> re-route;
- architecture/deep-model findings MAY route directly to a stronger provider;
- DECLINED -> normal next-provider selection;
- BLOCKED -> typed decision/human/upstream handoff rather than blind model escalation.

Exact retry counts and severity thresholds belong to policy/configuration, not Workflow semantics.

Measured convergence is the primary basis for continuing repair, switching
providers or stopping: use failure/finding movement, supported partial progress,
recurrence and oscillation. Ordinary repair count is an input to that judgment;
crossing its soft threshold alone does not stop converging work below the hard
limit. Stalled or diverging work is handled when observed, without waiting for
ten cycles. AI self-assessment supports, but does not replace, observed evidence.

The shared limit of 10 automatic Fix cycles is an exceptional final backstop
against runaway unattended operation, not the normal stopping criterion, a
target, or permission to keep retrying until ten. It prevents an eleventh
automatic Fix in the same counting window even if convergence looks favorable; a successful tenth result
may complete normally. Use the existing
[convergence and repair policy](../notes/managed-test-suites-and-verification-cost-guard.md).

Provider-specific attempt limits operate within the existing shared repair
policy; they cannot reset or enlarge it. A switch within the same semantic
WorkOrder retains both counters and their existing counting rules, including
ordinary-count exclusions and automatic-cycle consumption for hygiene/minor
fixes. A newly selected provider receives only the remaining allowance. Reaching
the shared hard limit with required work still unresolved stops further automatic
repair with an explicit unresolved outcome; another provider selection does not
grant a new allowance. Reuse the
existing guard rather than introducing a second repair counter/ledger.

Explicit user approval to continue stopped repair work resets both active
counts to zero and opens one new counting window, retaining cumulative history,
issues and convergence trends. This approval covers subsequent repair batches
within the window, not a single finding; old exhausted counts cannot trigger
per-finding reapproval. Accepted Step completion with its Step commit closes
that loop and the next Step starts at zero. Checkpoint/WIP/repair commits within
an unfinished Step do not reset it. Apply the reset contract in the linked note;
provider switching alone never opens a window.

### Acceptance-bounded routing

The routing decision uses the Phase 6 Effective Acceptance Boundary:

- required quality not satisfied and improving -> bounded same-provider repair;
- required quality not satisfied and stalled/diverging -> provider re-routing;
- proposed assurance outside the boundary -> stop/reject that work rather than re-route;
- Review finding outside the boundary -> nonblocking proposal/follow-up unless a human explicitly changes the accepted boundary;
- evidence of main-route complexity caused by unnecessary assurance -> prefer removal/simplification, not a stronger provider asked to perfect the same unnecessary mechanism.

Boundary changes are explicit project/Goal/Phase policy changes. A provider, Review result or test failure cannot silently enlarge the boundary.

## Evidence

Record each attempt as part of one semantic-work execution history:

- provider/profile;
- attempt number;
- existing ordinary repair count and automatic-cycle consumption/remaining bound;
- validation findings before/after repair;
- feedback delivered;
- convergence classification;
- voluntary DECLINED vs observed re-route;
- next-provider selection reason;
- final validation/review/admission outcome;
- elapsed/resource/cost evidence where available.

This evidence is suitable for textus-corpus/textus-experiment replay and later routing-policy improvement.

## Executable specification

Demonstrate at least:

1. an admitted provider converges through compile/targeted-test feedback without running CAR lint on every repair;
2. lightweight evidence clearly diverges and triggers early re-routing before CAR lint;
3. compile/test evidence improves across retries and remains on the same provider within the shared hard bound; crossing the ordinary-count soft threshold alone does not stop converging work;
4. a candidate reaches CAR lint, receives a repairable finding, repairs it, and converges;
5. a deep or repeated CAR lint finding triggers re-routing;
6. repair oscillation triggers re-routing;
7. provider DECLINED triggers voluntary escalation through the same routing framework;
8. BLOCKED does not cause blind stronger-model retry;
9. Provider A -> Provider B preserves semantic WorkOrder identity, validation history and both existing repair counters;
10. policy can select another admitted provider/profile before a stronger profile without assuming local or hosted placement;
11. bounded attempts terminate with an explicit unresolved outcome when no admitted provider converges; include a switch just before the shared hard limit, prove that only the remaining allowance is available, and that provider-specific limits cannot extend it; a successful tenth result can complete, but an eleventh automatic Fix cannot be dispatched;
12. no named model/provider is embedded in Workflow transition semantics;
13. an out-of-boundary concurrency/recovery assurance finding does not trigger repair or stronger-model escalation;
14. the same symptom classified as a required Acceptance Boundary failure can drive bounded repair and later re-routing;
15. unnecessary assurance that increases main-route complexity is removed/simplified while the required acceptance conditions remain satisfied.

## Completion

Phase 6 owns implementation and connected acceptance of approval-based and
Step-completion resets (P6-TS05). Reuse that accepted evidence here and verify
that provider switching preserves the active window and does not break those
reset semantics; do not defer their initial delivery until this phase.
The eleventh-Fix prohibition applies within each window, not across separately
approved continuations or completed Steps.

Phase 9 is complete when a real development task can execute:

admitted Provider A -> deterministic feedback -> same-provider repair -> non-convergence detection -> admitted Provider B/profile -> deterministic validation -> normal Review/Admission,

with typed evidence showing why the model switch occurred.

Use available supported providers/profiles for this real-task connection and
deterministic executable scenarios for the bounded policy cases. Local LLM
trials remain developer-owned reference work and are not required evidence.

Completion also requires evidence that out-of-boundary assurance work is distinguished from genuine non-convergence and cannot cause an automatic repair/escalation loop.

## Non-goals

- autonomous online learning of routing policy;
- fine-tuning a local model;
- installing or accepting a local LLM/provider as a Phase completion requirement;
- requiring the local model to understand OFP theory explicitly;
- unlimited retries;
- replacing Review or Admission with lint success;
- encoding provider/model names in Workflow semantics;
- speculative rollback/integrity/contamination machinery.
