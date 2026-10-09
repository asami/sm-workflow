# CAR lint feedback loop and reasoning-model switching

- Date: 2026-10-08
- Status: Design decision
- Scope: sm-workflow
- Result: Phase 9 added

## Context

Local LLM usefulness is not determined only by what the model learned before release. Even when its training priors do not fit Textus/OFP well, deterministic feedback can move some tasks into the practical operating zone.

In particular, CAR lint can tell a model that code which compiles and may even pass tests still violates the Textus programming model or architecture.

The previous Provider Routing design already allowed a provider to return DECLINED and be replaced by a stronger/capable provider. That is insufficient by itself because a weaker model may not recognize its own limit.

## Decision

Add a Workflow-controlled feedback loop:

~~~text
provider
  -> implementation
  -> compile/test/CAR lint
  -> feedback
  -> repair
  -> validation
       converged -> continue
       not converging -> change provider/model and re-execute
~~~

This creates two independent escalation triggers:

- voluntary: the reasoning provider returns DECLINED;
- observed: sm-workflow detects bounded non-convergence from deterministic evidence.

The second trigger allows local models to be used aggressively without trusting model self-assessment.

## Architectural implication

The practical objective is Textus conformance, not theoretical OFP expertise. If the Textus runtime, DSL, harness, compiler, tests, and CAR lint constrain the solution space sufficiently, a model can write good Textus code even when its pretrained programming style is more procedural.

This applies equally to human programmers: platform quality is partly demonstrated when application developers do not need to reconstruct the underlying OFP theory for routine work.

## Phase placement

Phase 8 validates concrete local provider execution and provider-declared DECLINED escalation. Phase 9 builds on that foundation and adds validation-feedback repair plus Workflow-observed model switching.

This ordering keeps provider integration separate from convergence policy.

## Experiment connection

Attempt history should be retained for replay through textus-corpus/textus-experiment. Important comparisons include raw local, local + feedback, local + repeated repair, local-to-local switching, and local-to-strong escalation.

The resulting evidence can later improve versioned routing policy, but runtime routing must not autonomously rewrite policy from recent outcomes.


## Verification-cost refinement

CAR lint is comparatively heavy, so it is not part of every inner repair iteration.

~~~text
implementation / repair
  -> lightweight convergence observation
       compile / targeted tests / diagnostics / repair history
       -> stuck or diverging: early escalation
       -> converging: continue bounded repair
       -> candidate formed
            -> CAR lint when required
                 -> clean: continue
                 -> repairable: bounded same-model feedback
                 -> deep/repeated: escalation
~~~

Observed escalation can therefore happen before CAR lint when the execution trajectory is already abnormal, or after CAR lint when architectural/Textus-conformance evidence shows that a different reasoning model is appropriate. This follows the verification-cost-guard principle: expensive validation is not a precautionary progress probe.


## 2026-10-10 observation: abandonment/protocol failure as routing evidence

A concrete local-model experiment exposed a third escalation path in addition to provider-declared DECLINED and Workflow-observed non-convergence.

Environment:

- ChatGPT/Codex desktop integration with Ollama;
- local model: gpt-oss:20b;
- task: inspect and explain the algebra-based DSL in CNCF UnitOfWork;
- the model successfully began repository exploration, but after roughly one minute ended by emitting what appeared to be the next shell/tool invocation (a `sed` command encoded as tool-call JSON) instead of continuing the agent loop and producing a result.

This should not automatically be classified as a reasoning failure. The model may have been capable of understanding the Scala/DSL material but failed to sustain the surrounding agent/tool protocol. sm-workflow therefore needs to distinguish at least:

- `DECLINED`: the model/provider explicitly judges the task outside its capability;
- `ABANDONED`: execution stops without a valid completion or explicit decline;
- `PROTOCOL_FAILURE`: tool/IoC/continuation behavior becomes invalid or cannot continue;
- `VALIDATION_FAILURE`: a nominally completed result fails deterministic validation;
- bounded `NON_CONVERGENCE`: repeated repair is not converging.

All of these are escalation evidence. A local-first route should be able to hand the same bounded task/context to a stronger provider rather than requiring a human to restart the work manually.

## Corpus and routing feedback

Escalation records are also corpus candidates. Retain enough execution evidence to compare:

~~~text
task characteristics
  x project/context
  x work kind
  x provider/model/reasoning level
  x outcome category
  x cost/latency
  x escalation target
  x eventual successful result
~~~

In particular, preserve the stronger model's successful continuation/result after escalation. A failure sample without the successful resolution is much less useful for later provider evaluation.

This evidence should feed future Model Selector / Provider Routing policy. The useful KPI is not only task success rate. It also includes:

- appropriate decline rate;
- inappropriate acceptance followed by abandonment/protocol failure;
- validation failure and non-convergence rate;
- escalation success rate;
- latency/cost before escalation;
- success by task/project/work-kind characteristics.

A weaker model that quickly and correctly declines work outside its capability may be more useful than one that attempts everything and fails late. Therefore decline quality is a first-class model-selection signal.

The operational loop becomes:

~~~text
select provider
  -> execute
       success ----------------------> accept + evidence
       declined ---------------------+
       abandoned --------------------+
       protocol failure -------------+-> escalate -> stronger provider
       validation failure -----------+                  |
       bounded non-convergence ------+                  v
                                               result + corpus evidence
                                                        |
                                                        v
                                            future routing evaluation
~~~

Routing-policy updates remain versioned and controlled. Runtime observations provide evidence; they do not cause an online self-modifying routing policy.

This makes escalation + corpus capture a core requirement for practical local-first operation rather than merely an experimental convenience.
