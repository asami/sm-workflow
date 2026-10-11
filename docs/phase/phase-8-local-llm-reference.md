# Reference: manual local LLM trial

This document summarizes the [concrete provider experiment proposed for Phase 8](https://github.com/asami/sm-workflow/blob/357dcac/docs/phase/phase-8.md)
on 2026-10-07. It is operational reference information, not a Phase 8
or Phase 9 completion condition. The developer conducts these checks separately
from phase acceptance. The authoritative Phase 8 scope and completion criteria
are in [phase-8.md](phase-8.md).

## Trial purpose

Manually try a local or self-hosted LLM with Codex where practical. OpenCodex
and OpenCode may be compared as optional, replaceable adapters. None is a
required runtime dependency or a prerequisite for starting or closing Phase 6,
8 or 9. Phase 9 routing acceptance uses available admitted providers/profiles.

Keep the Phase 6 boundary provider-neutral:

```text
WorkClassification
  -> ExecutionRequirement
  -> Provider Selection
  -> concrete adapter or harness
  -> ExecutionEvidence
```

Provider, model, machine and reasoning-option names belong to environment
resolution and execution evidence, not Workflow transition semantics.

## Suggested manual observations

- Try bounded judgment, implementation or TEST_FIX, and independent review on
  real development work with deterministic validation.
- Observe COMPLETED, DECLINED and BLOCKED results. Check whether a declined
  local attempt can hand the same work to a stronger provider, and whether a
  blocked attempt reaches a typed decision rather than an automatic loop.
- Record the project and work type, reasoning requirement, provider and machine,
  result, validation and review results, elapsed time, and available usage or
  resource evidence. Compare local completion, escalation and disagreement
  rates only when enough observations exist.
- If available, compare machines with different local capacity. Treat machine
  identity as policy and evidence, not workflow semantics.
- Evaluate OpenCodex or OpenCode only after the direct Codex path is understood;
  compare tool behavior, result quality, switching cost and operational failure.

Manual findings may inform a later selected Phase 8 scenario. They do not claim
that local LLM support has been implemented, validated or accepted.
