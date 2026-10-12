# CAR lint vs AI overchecking review

Date: 2026-10-10
Status: workflow direction
Related: Phase 9, Provider Routing, nightly quality, cbd-support Review

## Decision

sm-workflow must keep routine deterministic validation affordable.

CAR lint is a deterministic architectural/Textus-conformance diagnostic and may participate in Candidate/commit/admission policy. It must not be expanded into a heavy AI semantic scan merely to detect every form of excessive defensive checking.

Semantic overchecking detection belongs to cbd-support Review.

## Execution shape

    implementation / repair
      -> cheap deterministic validation
      -> CAR lint when policy requires
      -> Candidate / commit progression
      -> semantic Review according to normal review policy

A dedicated overchecking concern may be included in semantic Review. Broader Component hygiene scans may run explicitly or as lower-frequency/nightly quality work rather than on every commit.

Review should look for model-unjustified validation, verification, integrity, recovery, synchronization, duplicate/shadow state, repeated observation, and other defensive machinery. Findings remain normal typed Review evidence and participate in existing REVIEW_FIX / re-review / Admission behavior.

## Provider routing

Overchecking review is semantic work and therefore uses provider-neutral ExecutionRequirement / Provider Routing. A local model may be used first when policy admits it. DECLINED, BLOCKED, low-confidence or otherwise unresolved work follows the existing escalation model; Workflow semantics do not name a model.

## Corpus feedback

Confirmed Review findings and their repairs should be retainable as corpus candidates. Repeated patterns may later be promoted into cheap deterministic cbd-support CAR lint rules. This is a quality-learning loop, not online autonomous rule mutation.

## Cost boundary

Do not require an AI overchecking scan merely to create each commit. Do not run broad Component scans inside the ordinary Fix inner loop. Existing verification-cost and convergence guards remain authoritative.
