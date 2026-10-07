# Phase 9: Validation-Feedback Model Switching and Convergence Routing

Status: planned
Planned: 2026-10-08
Depends on: Phase 6, Phase 8
Related: CAR lint, deterministic validation, Provider Routing, textus-experiment

## Goal

Extend Provider Routing from provider-declared DECLINED escalation into validation-observed model switching.

sm-workflow MUST be able to execute a bounded correction loop in which deterministic feedback from compile, tests, and CAR lint is returned to the current reasoning provider, and then re-route the same semantic work to another admitted provider/model when the observed correction process is not converging.

The purpose is to expand the practical range of inexpensive/local models without requiring them to have perfect pretrained knowledge of Textus, OFP, or project architecture.

## Two escalation sources

Phase 9 distinguishes:

1. **Voluntary escalation** — the active provider returns DECLINED.
2. **Observed escalation** — the provider continues attempting the work, but sm-workflow observes from deterministic validation history that the attempt is not converging and selects another admitted provider.

BLOCKED remains distinct: it represents missing authority/input/design/context where model switching is not the appropriate response.

## Feedback loop

~~~text
Semantic WorkOrder
  -> Provider A
  -> candidate implementation
  -> compile / test / CAR lint
       PASS -> continue normal Review / Admission
       actionable findings
         -> feedback to Provider A
         -> repair attempt
         -> deterministic validation
              converging -> bounded retry with Provider A
              non-converging -> Provider Routing
                                  -> Provider B
                                  -> same semantic work + relevant evidence/history
                                  -> validate again
~~~

The loop MUST be bounded. It MUST NOT become an unbounded "retry until green" mechanism.

## Convergence observations

Phase 9 should support deterministic observations sufficient to identify at least:

- the same material CAR lint finding recurring after repair;
- compile/test failure class recurring without meaningful progress;
- material finding count/severity not improving across bounded attempts;
- repair oscillation where previously resolved findings repeatedly return;
- a repair introducing new architectural violations of comparable or greater severity;
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
- feedback already supplied;
- reason for re-routing.

Provider selection remains governed by the provider-neutral ExecutionRequirement and routing policy. Workflow semantics MUST NOT encode named models such as Qwen, Ollama, Luna, or Sol.

A routing policy MAY prefer another local provider before a stronger hosted provider when measured capability and cost justify it.

## CAR lint as correction feedback

CAR lint is treated as a machine-readable architectural/Textus-conformance feedback source, alongside compiler and test evidence.

The desired separation is:

- compiler: language/type conformance;
- tests: behavioral conformance;
- CAR lint: architectural/Textus programming-model conformance.

CAR lint findings SHOULD carry enough typed information for a reasoning provider to understand what rule was violated, where, why it matters, and what class of correction is expected without prescribing a brittle textual patch.

## Routing policy

Initial routing policy MUST be explicit and versioned. Representative policy:

- first actionable validation failure -> return feedback to the same provider;
- repeated equivalent material finding or bounded non-improvement -> re-route;
- architecture/deep-model findings MAY route directly to a stronger provider;
- DECLINED -> normal next-provider selection;
- BLOCKED -> typed decision/human/upstream handoff rather than blind model escalation.

Exact retry counts and severity thresholds belong to policy/configuration, not Workflow semantics.

## Evidence

Record each attempt as part of one semantic-work execution history:

- provider/profile;
- attempt number;
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

1. local provider produces a CAR lint finding, repairs it, and converges without model switching;
2. the same material lint finding recurs and triggers observed re-routing;
3. compile/test evidence improves across retries and therefore remains on the same provider within the configured bound;
4. repair oscillation triggers re-routing;
5. provider DECLINED triggers voluntary escalation through the same routing framework;
6. BLOCKED does not cause blind stronger-model retry;
7. Provider A -> Provider B preserves semantic WorkOrder identity and validation history;
8. a second local provider can be selected before a stronger hosted provider when policy admits it;
9. bounded attempts terminate with an explicit unresolved outcome when no admitted provider converges;
10. no named model/provider is embedded in Workflow transition semantics.

## Completion

Phase 9 is complete when a real development task can execute:

local provider -> deterministic feedback -> local repair -> non-convergence detection -> different provider/model -> deterministic validation -> normal Review/Admission,

with typed evidence showing why the model switch occurred.

## Non-goals

- autonomous online learning of routing policy;
- fine-tuning a local model;
- requiring the local model to understand OFP theory explicitly;
- unlimited retries;
- replacing Review or Admission with lint success;
- encoding provider/model names in Workflow semantics;
- speculative rollback/integrity/contamination machinery.
