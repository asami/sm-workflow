# Phase 5: Semantic Reasoning Classes and Runtime Mapping

Status: planned
Planned: 2026-09-30
Depends on: Phase 4

## Goal

Decouple sm-workflow's semantic description of AI work from provider/model-specific reasoning controls. Workflow definitions express the kind and semantic intensity of reasoning required; provider model names and controls such as medium/high/xhigh are resolved only at invocation time.

## Semantic work kinds

Initial vocabulary:

- PLANNING — decomposition, sequencing, and change-scope decisions.
- ANALYSIS — understand state, causes, dependencies, and evidence.
- DESIGN — determine solution structure, contracts, models, APIs, or state machines.
- CODING — create or modify implementation artifacts and tests.
- REVIEW — evaluate artifacts/changes against requirements, design, quality, and evidence.
- JUDGMENT — bounded admission, selection, approval, or candidate/evidence decision.

Testing is not initially separate: test creation is normally CODING, diagnostic interpretation ANALYSIS, and acceptance evaluation REVIEW.

## Semantic intensity

Each work kind is subdivided only as far as sm-workflow can meaningfully select the class from workflow context. Initial vocabulary may include ROUTINE, STANDARD, DEEP, CRITICAL, and EXHAUSTIVE, but supported intensities MAY differ by work kind.

A reasoning class is the semantic combination (for example CodingDeep, ReviewCritical, JudgmentStandard). It is one workflow-level semantic requirement, not two provider knobs.

Multiple reasoning classes MAY map to the same concrete execution profile.

## Runtime mapping

    Workflow Action
        |
        | ReasoningClass = ReviewDeep
        v
    Reasoning Profile Resolver
        |
        | ~/.cncf.d/...
        v
    Concrete Execution Profile
        +-- provider
        +-- model
        +-- provider-specific reasoning mode/effort
        +-- invocation controls
        |
        v
    Codex / other provider

The resolver belongs to the CNCF execution/runtime boundary. sm-workflow selects semantic intent; runtime configuration determines how the current environment realizes it. Exact filename/schema and deterministic precedence are fixed during implementation.

## Initial Codex mapping target

Current operating targets intentionally collapse the richer semantic vocabulary:

- Coding: GPT-6 Sol medium / high / xhigh.
- Review: GPT-6 Sol high / xhigh.

These concrete bands MUST NOT determine the number of semantic classes. Planning, Analysis, Design, and Judgment mappings are operational configuration, not Workflow semantics.

## Scope

1. Define ReasoningClass and the six initial work kinds.
2. Define meaningful semantic intensities per work kind.
3. Define how Actions request a ReasoningClass.
4. Define the CNCF-facing resolver contract and concrete execution-profile representation.
5. Define and implement user-level mapping under ~/.cncf.d/ with deterministic lookup.
6. Remove provider-specific reasoning levels from sm-workflow semantics where superseded.
7. Provide default mappings for the supported Codex environment.
8. Record both requested semantic class and resolved concrete profile in diagnostics/evidence.
9. Add Executable Specifications for mapping, many-to-one collapse, missing configuration, and mapping changes.

## Executable Specification requirements

Demonstrate that a Workflow requests semantic reasoning without provider details; distinct semantic classes may resolve to the same profile; mapping changes affect subsequent invocation without Workflow changes; coding resolves across current medium/high/xhigh bands; review resolves across high/xhigh bands; missing mappings fail explicitly; and provider-specific values do not leak back into persisted Workflow semantics.

## Non-goals

- Encoding Codex effort names as abstract Workflow levels.
- Requiring identical intensity sets for all work kinds.
- Automatic cross-provider cost optimization.
- Changing closure criteria of preceding Phases.
