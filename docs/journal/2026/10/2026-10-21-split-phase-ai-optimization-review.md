# SplitPhase AI Optimization Review for Phase 6.5 Candidate

Date: 2026-10-21
Status: Design review / Phase 6.5 candidate input
Scope: sm-split-phase / SplitPhaseWorkflow

## Purpose

Review the current SplitPhase specification and implementation from the
viewpoint of AI optimization, alongside the RepositorySync Phase 6.5 review.

SplitPhase differs from RepositorySync: semantic judgment is more intrinsic to
planning, but the existing architecture already confines AI to unresolved
semantic gaps and keeps partition calculation deterministic. The optimization
target is therefore not primarily removal of review; it is better Semantic Gap
Routing, reduced repeated context loading, and use of cheaper reasoning where
the bounded semantic task permits it.

## Overall assessment

SplitPhase is already strongly AI-optimized.

The normative design makes AI-cost reduction a first-class objective. Workflow
owns history/evidence collection, known-work calibration, contiguous candidate
enumeration, partition optimization, numbering, ownership/validation policy,
document projection/application and static validation.

AI is intentionally restricted to information that deterministic computation
cannot supply, such as novel-work estimates and unresolved semantic boundaries.
The implementation reflects this model. In particular,
`SplitPhaseExplicitPartitionComputation.enumerateC` returns a typed
`SemanticRequired` only when unresolved duration items or unresolved permitted
cut edges remain. When all inputs are known, enumeration and ranking proceed
without semantic AI.

The important existing pattern is:

```text
deterministic planning
  -> explicit unresolved semantic set
  -> semantic AI only for that set
  -> deterministic admission
  -> deterministic enumeration
  -> deterministic optimization
  -> deterministic proposal validation
```

AI does not select the final partition.

## Existing strengths

### Deterministic partition ownership

The current computation already owns:

- known versus unresolved duration representation;
- permitted cut/cross/either boundary representation;
- bounded candidate enumeration;
- eight-hour ceiling rejection;
- six-hour-target scoring;
- short-child penalty;
- deterministic lexicographic tie breaking.

This is the desired architecture. Do not move these calculations back into AI
prompts in the name of simplifying the workflow.

### Unknown-only semantic work

The specification explicitly sends only unresolved novel work to semantic
estimation and prevents AI from overwriting known/calibrated estimates.

Likewise, unresolved semantic boundaries are represented explicitly instead of
asking AI to rediscover the entire partition problem.

Repeated retrieval of the same semantic answer is also intentionally avoided.
A result belongs to its frozen unresolved set; deterministic admission and
optimization follow it. A genuinely new gap may create a new semantic request,
but rejection is not a prompt-retry loop.

These properties are directly aligned with the Phase 6.5 direction.

## Phase 6.5 generalization: Semantic Gap Detection

For SplitPhase, "Bad Smell Detection" is not the best primary term. The more
general mechanism is Semantic Gap Detection.

The existing `SemanticRequired` outcome is a concrete example:

```text
deterministic computation
       |
       +-- complete semantic inputs
       |      -> continue with no AI
       |
       +-- SemanticRequired
              -> issue bounded semantic work
```

This suggests a broader Phase 6.5 principle:

> Do not invoke AI because the workflow reached an AI-labelled stage. Invoke AI
> only when deterministic computation produces a concrete semantic gap that the
> workflow cannot close mechanically.

Bad Smell is one form of semantic gap used for development/review triage.
RepositorySync has merge-risk/semantic gaps. SplitPhase has planning semantic
gaps.

A possible common vocabulary is:

```text
GoalPhase       -> implementation/review Bad-Smell Gap
RepositorySync  -> merge semantic/risk gap
SplitPhase      -> planning semantic gap
```

The profile-specific typed gap remains authoritative; a generic abstraction
must not erase useful domain structure.

## Optimization opportunity: bundle gaps at one semantic frontier

Current SplitPhase has several semantic concepts/routes, including partition
semantics, scope semantics and reasoning semantics. Typed separation is useful,
but AI execution should be checked for repeated loading of substantially the
same planning context.

When multiple unresolved facts are known at the same semantic frontier, consider
whether one bounded request can carry the known gap bundle, for example:

```text
SemanticGapBundle
  unresolvedEstimates
  unresolvedBoundaries
  unresolvedReasoning
```

This must not become a return to "AI, split this Phase." Runtime still identifies
the gaps and deterministic code still calculates the partition. The purpose is
only to avoid several independent AI contexts rereading the same planning
evidence when the unresolved facts can safely be answered together.

Do not bundle gaps that have different authority, lifecycle or Human-decision
boundaries merely to reduce call count.

## Lightweight-model opportunity

SplitPhase appears especially suitable for a Luna-class first attempt.

The semantic worker is commonly asked to classify or estimate bounded unknowns,
not to execute commands or choose the complete partition. Its typed output is
then admitted and consumed by deterministic optimization.

A target routing is:

```text
deterministic computation
  -> bounded semantic gap
  -> Luna candidate
       |
       +-- valid typed result
       |      -> deterministic admission/calculation
       |
       +-- DECLINE / insufficient confidence / unsupported semantic demand
              -> Sol/stronger reasoning
```

If Luna can reliably handle these bounded tasks, evaluate Local LLMs against the
same corpus. Luna is the lower-bound control before assuming Local LLM viability.

This is likely a better lightweight-model proving case than asking a cheap model
to perform broad implementation or comprehensive code review.

## Review of AI results

Ordinary SplitPhase semantic enrichment should not automatically require a
second AI review.

The semantic result is not final authority for the partition. It is followed by
deterministic schema/authority admission, enumeration, optimization and proposal
validation. This gives the workflow a strong mechanical harness around weaker
semantic completion.

If a semantic result is inconsistent, low-confidence, declined or exposes a
genuine new semantic uncertainty, route it to stronger reasoning or Human as
appropriate. Do not add an unconditional AI-review-after-AI pattern.

The initial Phase 6.5 goal should therefore be:

```text
cheap bounded semantic completion
  -> deterministic admission/proof
  -> strong model only on unresolved/escalated cases
```

## Skill/context optimization

Like sm-repository-sync, `skills/sm-split-phase/SKILL.md` currently carries
substantial host/runtime operational guidance in addition to its semantic
instructions: command display, SBT/runtime lifecycle, termination, task routing,
journal lookup, host completion/Advance procedure and provider handoff.

Those controls may be necessary product contracts, but Phase 6.5 should examine
whether every semantic worker needs them in its reasoning context.

The ideal semantic input is closer to:

```text
issued semantic purpose
+ exact source/planning context
+ explicit unresolved gap set
+ relevant evidence and authority boundary
+ typed result schema
```

Runtime/host-owned command, routing, lifecycle and effect controls should remain
enforced by their owners rather than repeatedly consuming semantic-model context
where possible.

Do not weaken authority or safety contracts merely to shorten a prompt.

## Comparison with RepositorySync

The two profiles exercise different Phase 6.5 optimizations:

| Concern | RepositorySync | SplitPhase |
| --- | --- | --- |
| Main AI work | conflict resolution / merge review | unknown estimation / semantic classification |
| Deterministic core | Git integration/convergence | partition enumeration/optimization |
| Primary optimization | remove routine Detailed Review | optimize Semantic Gap Routing |
| Cheap-model potential | moderate | potentially high |
| Detailed Review | selected suspicious/high-risk merge | normally unnecessary for admitted enrichment |
| Strong-model escalation | suspicious merge, AI-created conflict resolution | decline/low confidence/semantic complexity |

Together they show that Phase 6.5 should not be framed only as "reduce review."
The more general objective is AI Invocation Optimization: use deterministic
computation to expose the smallest semantic residual, route that residual to the
cheapest adequate reasoning level, and escalate only when necessary.

## Implementation review points

Before changing SplitPhase behavior, inspect:

1. whether partition/scope/reasoning semantic calls currently reload overlapping
   planning context that can safely be bundled;
2. whether each semantic call is triggered by a concrete typed gap rather than
   lifecycle position alone;
3. whether retained/cached semantic results are reused at their admitted scope;
4. whether Luna can complete the current bounded partition/scope/reasoning
   contracts without changing partition authority;
5. whether DECLINE/low-confidence can be represented and escalated without
   inventing a parallel workflow;
6. whether semantic worker prompt/context can be reduced while host/runtime
   controls remain fully enforced;
7. whether any current independent review obligation is redundant after
   deterministic admission/optimization.

Do not create a second SplitPhase workflow, generic AI planner or another
snapshot/ledger system to implement these optimizations.

## Recommendation

Use SplitPhase as the Semantic Gap Routing proving case for Phase 6.5.

Preserve the existing deterministic partition engine. Treat its explicit
`SemanticRequired` pattern as a model for the wider workflow architecture:
compute as far as possible mechanically, identify exactly what remains unknown,
and ask AI only for that residual.

Evaluate Luna first on those bounded semantic gaps, then Local LLM if Luna
establishes lightweight-model viability. Reserve Sol/stronger reasoning for
declined, uncertain or genuinely complex semantic gaps.

The desired direction is:

> deterministic computation -> explicit semantic gap -> cheapest adequate
> semantic completion -> deterministic admission/proof -> escalation only when
> the gap remains unresolved.
