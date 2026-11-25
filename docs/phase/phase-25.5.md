# Phase 25.5: Hierarchical Instruction Generation and Caching

Status: planned; execution not started
Planned: 2026-11-25 (Asia/Tokyo)
Scheduled: after [Phase 25.4](phase-25.4.md), before Phase 28.2 (proposed)
Depends on: Phase 25.4 strategic judgement and typed Task Specification
Checklist: [Phase 25.5 acceptance](phase-25.5-checklist.md)

## Goal

Amortize advanced strategic inference across repeated tasks by generating
and caching scoped instructions from versioned templates. Terra-class models
remain the main bounded execution capability. Reuse is context-aware, not
unconditional replay of a previous task prompt.

## Delivery

### P255-01 — Templates and scoped instantiation

Provide typed templates for review, repair, implementation, test, judgement
and Take Over. Generate Project Instructions from template and project
architecture, policies and quality constraints; generate Phase Instructions
from project instruction and current Phase goals, plan and risks. Bind
current candidate, source, diff and diagnostics at Task execution time.
Prefer deterministic binding; use bounded AI adaptation where needed.
Keep mandatory policy and validation authoritative outside generated text.

### P255-02 — Cache lifecycle

Key by scope identity, template version, relevant context dependencies and
parent instruction identity/version. Retain provenance, generation route,
model, applicable constraints, validation status and usage evidence.
Invalidate affected descendants on relevant template, architecture, policy,
Phase plan or strategy changes. Ordinary source changes invalidate task
bindings unless they alter a recorded higher-scope dependency. Validate
applicability before reuse; never reuse across unauthorized projects or
incompatible workspaces. Handle concurrent generation, atomic publication,
restart and stale entries. Prefer lazy generation or planned Phase-start
generation; no broad knowledge repository or embedding search required.

### P255-03 — Template-specific model suitability

Define minimum and preferred capabilities, model suitability (including
Astra, Sol and Terra where available), scope, expected reuse, quality impact,
generation budget and fallback per template. Treat initial suitability as
hypotheses and refine with comparable evidence. Architecture/security
templates may favor Astra; ordinary review/test templates may favor Sol;
routine templates may be adequately served by Terra or programmatic rules.
No fixed model assignment. Keep exact model identity, execution route and
entitlement separate.

### P255-04 — Strategic investment and allowance-aware generation

Use Phase 25.3 verified daily allowance admission and Phase 25.4 free-first
policy. Where Astra and Sol both satisfy quality requirements, prefer Astra
when free and economically suitable, and lower-cost Sol when paid and
explicitly authorized. Account for shared quota, remaining capacity,
reset/expiry, forecast demand and opportunity cost; do not assume separate
Astra/Sol balances. Consider observed Codex weekly allowance headroom and
reset time when an eligible route exists; unknown balances are not free.
Reuse valid instructions before regeneration. Prefer investment in
high-reuse Project and Phase Instructions over individual tasks when justified.
Allow bounded pre-generation for planned work using otherwise-expiring
surplus only when likely reuse and context stability justify it.
Phase 27 owns generalized cross-route scheduling and paid optimization;
Phase 25.5 preserves free-only defaults and programmatic fallback, with
explicit budget authorization required for any paid generation.

### P255-05 — Qualification and economics

Verify template binding, scope isolation, invalidation, concurrent cache
publication, resume, free exhaustion, unknown weekly balance and fallback.
Compare cached and uncached design with equivalent validation; measure
generation, adaptation, repeated input, lookup, validation, retries,
quality, latency and cost per accepted task. Record reuse count and
amortized generation cost; distinguish estimates from observations.
No claim that reuse or Astra always improves quality or cost.
Record scoped qualification and independent review before closure.

## Boundaries

sm-workflow owns templates, scope, context dependencies and instruction
lifecycle. textus-ai owns provider capabilities, eligibility and accounting.
Phase 27 consumes suitability and reuse evidence for deterministic cost-aware
routing. Generalized knowledge extraction and pattern-language composition
are future work. Planning does not start a Session.
