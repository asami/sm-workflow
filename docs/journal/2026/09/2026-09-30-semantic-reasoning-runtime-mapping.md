# Semantic Reasoning Classes and Runtime Mapping

Date: 2026-09-30
Status: decision
Phase: [Phase 5](../../../phase/phase-5.md)

## Decision

sm-workflow separates semantic reasoning requirements from provider-specific model and effort controls.

Workflow reasoning is classified by work purpose: Planning, Analysis, Design, Coding, Review, and Judgment. Each purpose is subdivided only where sm-workflow itself can meaningfully choose a different reasoning demand. The semantic combination is treated as one ReasoningClass, such as CodingDeep or ReviewCritical, rather than as a provider-style model/level pair.

At runtime CNCF resolves ReasoningClass through configuration through CNCF standard Component configuration binding to a concrete execution profile. Multiple semantic classes may intentionally resolve to the same concrete profile.

## Motivation

Provider controls change with model generations. Keeping medium/high/xhigh or concrete model names in Workflow semantics would make definitions unstable and migrations error-prone. A richer semantic vocabulary can remain stable while runtime mappings are centrally updated.

## Current deployment assumption

Coding currently uses GPT-6 Sol medium, high, and xhigh. Review currently uses GPT-6 Sol high and xhigh. These counts do not constrain semantic granularity. Planning, Analysis, Design, and Judgment mappings will be established operationally.

## Phase placement

Existing Phase 3 (Resolved Failure Model) and Phase 4 (Service Bus Development Events) already occupy the next planned sequence. This work is therefore Phase 5 and does not enlarge any preceding Phase's closure scope.
