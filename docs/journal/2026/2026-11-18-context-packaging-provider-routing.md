# Context Packaging and Cost-Aware Provider Routing — Design Proposal

Date: 2026-11-18
Status: Proposal (journal; not an implementation plan)

## Background and objective

sm-workflow should use the monthly OpenAI API credit allowance for suitable work before consuming Codex capacity. API credits cannot be spent on API-key Codex usage. The aim is not to move every task to API, but to identify *self-contained problems* that can be solved with a bounded context package. The same packaging should improve model selection inside Codex (Terra/Sol) and local-LLM routing.

**Design principle:** Context Packaging exists to reduce AI work; packaging must not itself become an expensive AI task.

## Context Package contract

A package is a bounded, reproducible set of files and metadata that collectively contain **all information necessary to understand and solve the problem**. Neither the number of original source files nor the number of changed files is the defining criterion. Multiple source files may be packaged together.

Suggested fields:
- problem/goal and current step or fix request
- expected behavior, constraints, acceptance criteria (Given/When/Then when available)
- relevant source excerpts or files, interfaces and contracts
- compiler/test/lint findings and relevant evidence
- repository revision, file paths, content hashes and provenance
- allowed change scope and required tools
- package manifest: byte size, estimated token count, included/excluded resources, completeness status, construction method

The package should be immutable per attempt, content-addressable where practical, and referencable from Continuation rather than copying all content into workflow state. Do not include secrets or unnecessary private data.

## Deterministic-first construction

Build from existing workflow inputs, structured compiler diagnostics, file locations, changed-file lists, explicit dependency metadata, contracts, lint/test output and known repository paths. Apply bounded traversal, deduplication, stable ordering, file-size limits, token estimates and caching/incremental updates.

No AI is used in the normal packaging path. In exceptional uncertain cases, optionally consult **Local LLM or Luna only**, under a strict cost/attempt budget; either may decline. Do not escalate to expensive reasoning merely to construct the package. If completeness cannot be established cheaply, classify as uncertain and route the *problem* to a more capable agent (usually Codex).

Completeness is a conservative eligibility judgment based on available evidence, **not** a proof that every hidden dependency is absent. API models must be allowed to decline on discovering missing context.

## Routing dimensions

Keep independent:
- Change Scope: anticipated edit surface
- Context Scope and Completeness: what must be known; complete / uncertain / incomplete
- Context Size: actual bytes and estimated tokens
- Reasoning Complexity: rules first; optional low-cost classification
- Tool Requirements: patch-only, repository exploration, tests, multi-step agent actions
- Provider budget, capability and quality history

Initial experimental thresholds: <=64 KiB standard, up to 1 MiB extended **only if token window, cost, and capability checks pass**. These are configurable *policy thresholds*, not API limits. Reject or reroute uncertain, oversized or tool-incompatible packages.

Recommended routing:
1. Deterministic fixes/verification when possible.
2. For complete, bounded, tool-compatible packages, OpenAI API first while eligible credit budget remains.
3. For API decline, failed bounded retries, insufficient credit or unsuitable context, hand off to Codex Terra or Sol according to complexity and tools.
4. Local LLM/Luna may assist only where economically justified; do not assume they must be invoked for every request.

The provider must not bypass common tests, lint, Candidate–Admission or security policies. API can return a patch candidate; sm-workflow validates paths, applies in a controlled workspace, tests and admits. Commit and privileged actions remain behind existing authorization/admission boundaries.

## Continuation and handoff

Continuation references the package, repository revision, accepted decisions, attempted patches, errors, test/lint evidence, remaining issues and next action. It is a provider-independent *working contract*, not a transfer of hidden model reasoning. Prefer switching at action/step boundaries; preserve checkpoints and rollback on partial failure. Existing Take Over and retry limits apply.

## Cost and safety

Measure packaging CPU/time separately from API tokens/cost and Codex usage. Track provider/model, estimated versus actual tokens, cache hits, latency, attempts, quality/admission outcomes and declines. Enforce a hard API spend budget and avoid unintended pay-as-you-go overages; verify actual credit/billing behavior before enabling automatic fallback. Never treat a speculative Codex-credit conversion as an accounting equivalence.

## Evaluation and rollout

1. Instrument current fix classifications and collect representative tasks.
2. Implement deterministic package manifest and bounded assembly, without AI.
3. Compare identical packages across API, Terra and Sol on acceptance, convergence, time and cost.
4. Start with small, clearly self-contained compiler fixes, function changes and reviews; expand based on evidence, not file count.
5. Tune 64 KiB / 1 MiB thresholds, completeness rules and model routing with observed outcomes.

Acceptance criteria: packaging overhead is materially smaller than saved inference cost; missing context leads to safe decline/fallback; no unapproved edits; reproducible package/continuation; demonstrably improved provider selection and cost without degrading admission quality.

## Open questions

- Which existing sm-workflow metadata can establish context completeness without AI?
- Which tool actions are already reusable for API patch application?
- How to obtain reliable remaining API promotional-credit information and avoid overage?
- Where to persist package artifacts and their retention policy?
- What are the best empirical size/complexity thresholds per model?

This journal is a design proposal. Phase allocation and implementation details should be decided after checking current code and existing phase plans.
