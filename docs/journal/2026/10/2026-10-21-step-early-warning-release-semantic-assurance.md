# Phase 6.5 Candidate: Step Early Warning and Release Semantic Assurance

Date: 2026-10-21
Status: Design candidate / review input for prospective Phase 6.5
Related:
- 2026-10-20-step-review-reduction-workflow-optimization-plan.md
- 2026-10-21-phase-6.5-bad-smell-triage-and-finding-management.md
- 2026-10-21-repository-sync-ai-optimization-review.md
- 2026-10-21-split-phase-ai-optimization-review.md

## Decision direction

Separate the quality purpose of Step and Release.

- **STEP is cheap early warning / fast feedback.**
- **RELEASE is strong semantic assurance and the last-resort AI quality gate.**

Do not require every Step to establish complete semantic correctness. Requiring
that would recreate mandatory per-Step AI review under another name and restore
the cost and review-induced scope expansion that Phase 6.5 is intended to reduce.

A Step needs enough evidence to answer:

> Is there a concrete reason not to continue building on this Step?

Release answers the stronger question:

> Does the completed Plan, as an integrated result, satisfy its intended Usage
> Contracts and acceptance boundary and remain suitable for release?

## Target workflow

```text
PLAN
  |
  +-- STEP
  |     implementation
  |       -> full compile + focused test
  |       -> Scala lint
  |       -> CAR lint
  |       -> deterministic spec/Given-When-Then smell detection
  |       -> FIX + affected re-validation for deterministic failures
  |       -> optional AI Semantic Inspection only when semantic uncertainty
  |          remains worth resolving now
  |       -> Detailed Review/FIX convergence only when escalation is justified
  |       -> Step commit
  |
  +-- STEP ...
  |
  +-- RELEASE
        -> integrated/full validation
        -> Scala lint / CAR lint
        -> cbd-support Review
        -> comprehensive AI Review
        -> semantic Usage Contract <-> Given/When/Then <-> implementation check
        -> architecture / complexity / over-engineering assessment
        -> Detailed Review/FIX + affected re-validation/re-review as needed
        -> finalize pending nonblocking findings
        -> Final
        -> release commit
```

AI Semantic Inspection is not a normal mandatory Step gate.

## Given/When/Then at Step

Removing mandatory Step AI review creates a real risk: compile and focused tests
can prove that the implementation satisfies the executable tests that exist, but
cannot by themselves prove that those tests express the intended Given/When/Then.

Do not solve that problem by running AI on every Step merely to inspect
Given/When/Then semantics.

Instead, detect structural/specification smells deterministically where possible.

Representative early-warning signals include:

- public/Usage Contract behavior changes but no relevant executable scenario
  changes;
- a new Operation has no corresponding scenario;
- a new input variant has no corresponding Given coverage;
- a new result/failure variant has no corresponding Then coverage;
- Guard/Capability behavior changes without an applicable refusal/permission
  scenario;
- state/PowerType alternatives change without corresponding scenario coverage;
- the Step's changed contract and selected focused tests have no apparent
  metadata relationship;
- substantial Given/When/Then growth occurs without a corresponding contract or
  implementation change;
- CML/Operation/Test Metadata ABI relationships become incomplete or inconsistent.

The Step detector asks whether the specification shape is suspicious. It does
**not** claim to prove that the prose/semantic meaning of every Given/When/Then is
correct.

Where practical, CAR lint should own contract/spec/Test-Metadata structural
smells; Scala lint owns source-level implementation smells. Both contribute
structured evidence to any later semantic inspection.

## Optional AI Semantic Inspection

If deterministic evidence is clean and no other risk signal exists, proceed
without AI.

If evidence leaves a semantic uncertainty that is useful to resolve before
continuing, issue a bounded Semantic Inspection. Its task is narrow:

1. assess the specific specification-alignment or Bad-Smell uncertainty;
2. decide whether Detailed Review is justified;
3. do not enumerate unrelated improvements or broaden the Step.

This can be a candidate for Luna and later Local LLM evaluation. CLEAN permits
normal continuation. SUSPICIOUS or DECLINE escalates. An obvious deterministic
failure goes directly to FIX and does not need AI triage.

The intended progression is:

```text
deterministic evidence
  -> stop if sufficient
  -> cheap AI only if semantic triage is useful
  -> strong Detailed Review only if required
```

## Release is the semantic last resort

Release intentionally retains strong AI assurance.

At Release the implementation is no longer a transient intermediate Step. The
reviewer can inspect the complete Plan result and check relationships that are
expensive or misleading to judge repeatedly during construction:

- Plan/Usage Contract satisfaction;
- semantic correspondence of Given/When/Then to intended behavior;
- implementation correspondence to those executable scenarios;
- integration across Steps;
- architectural/component boundaries;
- duplicated state/mechanisms and accidental complexity;
- over-engineering and excessive defensive machinery;
- residual semantic findings not expressible by compiler/test/lint rules.

Release review includes cbd-support review knowledge and comprehensive AI review.
Where findings are blocking/in-boundary, Detailed Review includes FIX,
affected re-validation and Re-Review until the accepted boundary converges.

Convergence does not mean no optional improvement can be imagined.

## Why semantic alignment is often better at Release

Intermediate Step state can be incomplete by design. Requiring a strong semantic
review of every intermediate state encourages local optimization and can cause
the reviewer to add tests, abstractions and defensive behavior that later Steps
would have made unnecessary.

At Release:

- all planned Steps are visible together;
- the final Usage Contract is clearer;
- executable scenarios can be judged as a coherent set;
- duplicated or contradictory behavior across Steps becomes visible;
- the reviewer can judge necessary-and-sufficient implementation rather than
  optimizing each local diff independently.

Therefore a semantic issue safely detected and corrected at Release is not by
itself evidence that every Step needed AI review.

## Feedback from Release to the Step harness

For each meaningful Release finding, ask whether earlier detection was actually
necessary.

```text
Release finding
  |
  +-- Release detection was sufficient
  |      -> no Step cost added
  |
  +-- earlier warning was needed
         |
         +-- type/model/compiler improvement
         +-- focused-test selection/coverage improvement
         +-- Scala-lint rule
         +-- CAR-lint rule
         +-- spec/Given-When-Then smell rule
         +-- bounded AI Semantic Inspection trigger
```

Prefer the cheapest deterministic mechanism that can reliably detect the class
of problem. Do not automatically respond to a Release finding by restoring a
mandatory Step AI review.

Over time, this feedback should strengthen the non-AI Step harness and reduce
the number of Steps that need semantic AI before Release.

## Relationship to Bad Smell and Semantic Gap routing

This quality split generalizes the other Phase 6.5 candidate work.

- GoalPhase Step development uses deterministic early-warning smells and
  optional semantic inspection.
- RepositorySync uses deterministic merge/lint smell evidence and only escalates
  suspicious merge candidates.
- SplitPhase computes until an explicit planning Semantic Gap remains and invokes
  AI only for that residual.

The common principle is:

> Compute and validate cheaply as far as possible. Use AI before Release only
> when a concrete semantic uncertainty is worth resolving immediately. Keep a
> strong integrated AI review at Release as the final semantic assurance layer.

## Phase 6.5 review questions

Before adoption, confirm:

1. Which deterministic metadata links Usage Contracts, Operations and focused
   executable Given/When/Then scenarios well enough for structural smell checks?
2. Which spec-alignment smells belong in CAR lint versus a small workflow-level
   detector?
3. What exact conditions justify optional Step Semantic Inspection?
4. Can clean Steps close without any semantic AI while retaining honest evidence
   of the checks actually performed?
5. Does Release review explicitly include final Given/When/Then semantic
   alignment so that Step optimization does not silently remove that assurance?
6. Can Release findings be classified back into cheaper Step harness improvements
   without creating another heavy analytics/ledger system?
7. Does the resulting workflow reduce AI calls and review-induced scope expansion
   while preserving Release quality?

## Intended principle

**STEP = cheap early warning. RELEASE = strong semantic assurance.**

Use the compiler, focused tests, Scala lint, CAR lint and specification-shape
signals to keep Steps moving cheaply. Invoke semantic AI during a Step only when
there is a concrete reason to do so. Use the Release AI review as the last resort
that verifies the integrated Plan, Usage Contract, Given/When/Then and
implementation as a coherent whole.
