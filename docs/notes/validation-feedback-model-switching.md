# Validation-Feedback Model Switching

## Purpose

sm-workflow uses deterministic validation not only as an acceptance gate but also as feedback for reasoning execution.

A provider may voluntarily DECLINE work, but self-awareness is not required for safe local-model use. sm-workflow can also infer that the current reasoning provider is not converging from compile, test, and CAR lint history and re-route the same semantic work to another admitted provider.

## Core model

~~~text
WorkOrder
  -> reason/implement
  -> deterministic validation
  -> feedback
  -> repair
  -> validation
  -> converge | re-route | blocked
~~~

Re-routing preserves semantic work identity. Provider/model identity is execution policy, not Workflow semantics.

## Why CAR lint matters

A local LLM may have training priors that do not match Textus/OFP architecture. It can still enter the practical operating zone if Textus supplies machine-readable correction.

Compiler, tests, and CAR lint form complementary feedback:

- compiler constrains language/type correctness;
- tests constrain observable behavior;
- CAR lint constrains architecture and Textus programming-model usage.

Therefore the operational target is not "make the model an OFP expert". It is "make the model capable of producing conforming Textus code inside the runtime/harness envelope".

This is analogous to human application programmers: they need not reconstruct the theory behind every runtime/DSL abstraction if the platform exposes a usable programming model and gives precise feedback when they leave it.

## Convergence and switching

The first implementation should use explicit, explainable signals: recurring material findings, lack of reduction, oscillation, or introduction of equally serious architectural violations.

A retry budget is policy. When exhausted or non-convergence is detected, Provider Routing selects another admitted provider. This can be another local model or a stronger hosted model.

Provider-declared DECLINED and Workflow-observed non-convergence are distinct causes but share the same re-routing infrastructure.

## Long-term measurement

Persist attempt histories so textus-experiment can measure:

- raw local completion;
- success after CAR lint feedback;
- success after same-model repair;
- success after local-to-local switch;
- success after stronger-model escalation;
- correction cycles and cost;
- finding classes correlated with model switching;
- final Review disagreement.

These measurements should inform later versioned routing policy through normal human/admission governance rather than runtime self-modification.
