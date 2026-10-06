# Local-first Judgment and Fix Reasoning Routing

- Date: 2026-10-07
- Status: Design direction
- Scope: sm-workflow
- Related: ExecutionRequirement / Provider Selection, TEST_FIX / REVIEW_FIX, local-LLM operation, corpus / experiment

## Motivation

The current design already separates abstract reasoning requirements from concrete provider/model selection and defines TEST_FIX / REVIEW_FIX as evidence-triggered semantic work. The next useful step is to make the reasoning requirement for a Fix sensitive to the actual deterministic failure evidence rather than treating Fix as one uniform difficulty.

At the same time, local LLM operation should be considered for two broader roles:

1. bounded AI Judgment;
2. ordinary STANDARD implementation, in addition to ROUTINE/simple work.

If these roles are reliable, cloud reasoning can concentrate on DEEP/CRITICAL work, independent review, low-confidence decisions, and escalation.

## Compile/test failure as a routing driver

Compiler/test diagnostics are deterministic evidence and therefore a good first driver for dynamic Fix routing.

~~~text
compile/test failure
  -> normalize deterministic diagnostics
  -> deterministic pattern/rule classification where sufficient
  -> AI Judgment where semantic interpretation remains
  -> abstract ReasoningLevel
  -> TEST_FIX WorkOrder
  -> ExecutionRequirement
  -> Provider Selection
~~~

Examples range from missing imports/identifier mistakes and localized signature mismatches to difficult type inference, API migration, or design-boundary failures. Diagnostic count is not complexity: one root cause can produce many errors.

This pre-execution classification is distinct from the existing FixChangeAssessment. The former predicts the reasoning requirement from failure evidence before work; the latter describes the semantic depth of the revision after the Fix.

## Local AI Judgment

Judgment is particularly promising for local LLMs because it can often be constrained to a small typed input/output contract rather than open-ended implementation. A conceptual result is:

~~~text
FailureJudgment
  category
  reasoningLevel: ROUTINE | STANDARD | DEEP | CRITICAL
  confidence
  rationale
~~~

Deterministic rules should resolve obvious cases first. Local AI Judgment handles cases requiring semantic interpretation. Low confidence, unsupported categories, or a DEEP/CRITICAL requirement may be escalated through normal Provider Selection. The Judgment itself does not choose a named model.

This is consistent with JudgmentAction as judgment-specialized semantic work and with the rule that deterministic processing remains deterministic when AI is unnecessary.

## Local-first STANDARD implementation

Local execution should not be limited permanently to hygiene and trivial fixes. A useful target is that ROUTINE and a meaningful portion of STANDARD implementation / bounded TEST_FIX can execute locally when the configured local provider satisfies the resolved requirement.

Initial policy direction:

- ROUTINE: local-first.
- STANDARD: local-first candidate; escalate based on capability/evidence.
- DEEP: normally stronger provider.
- CRITICAL: strongest appropriate provider/policy.
- independent REVIEW: preserve required independence regardless of model class.
- Judgment: local-first when bounded; escalate on confidence/capability.

These are provider-policy defaults, not Workflow semantics.

## Measurement and learning

Routing quality should be measured rather than assumed. sm-workflow should preserve enough execution evidence to evaluate:

- work/failure classification;
- requested abstract reasoning level;
- selected provider/profile;
- compile/test outcome;
- review outcome;
- Fix cycles/retries;
- elapsed time and available cost evidence;
- escalation reason.

The existing corpus/experiment direction can later replay reproducible work contexts against different local/cloud reasoning engines. This allows routing to evolve from broad level-based defaults toward evidence-based routing by work characteristics.

A particularly useful question is not merely "can a local model code?" but "for which admitted work characteristics does local execution minimize total cost while still converging through deterministic validation and independent review?"

## Architectural boundary

No local model name, cloud model name, Mac hardware assumption, or provider-specific effort setting belongs in Workflow transition semantics. Workflow carries semantic work and abstract ExecutionRequirement; sm-workflow policy resolves application requirements; the execution harness/provider layer selects the concrete executor.

The desired operating model is:

~~~text
deterministic processing
  -> local semantic Judgment where needed
  -> local ROUTINE/STANDARD work where capable
  -> deterministic validation
  -> independent review / stronger reasoning / escalation when required
~~~

This keeps local-first operation an optimization and capability policy rather than a second workflow model.
