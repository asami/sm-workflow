# Phase 6.10 Acceptance Checklist

Status: planned; all items pending.
Contract: [AI operating profiles](../spec/ai-operating-profiles.md)
Plan: [Phase 6.10](phase-6.10.md)

- [ ] P610-01 (M1): Inventory effective baseline; omitted/explicit standard preserve existing selection, parent configuration and acceptance rules.
- [ ] P610-02 (M1/M2): Config and sm-goal-phase logical profile parameter support standard/cost/quality; new-work precedence is explicit > config > standard.
- [ ] P610-03 (M2): Saved issued work survives resume/config changes unchanged; configured and actual selections are distinguished; no silent unavailable-model fallback.
- [ ] P610-04 (M3): Capture Phase-start/end physical source-line counts using the same scope/rule; exclude generated/vendor/output/evidence files; include additions/deletions and count final unterminated lines consistently.
- [ ] P610-05 (M3): Display both raw counts, signed net delta and already acquired attributable tokens; separate Codex/local totals and ratios; deduplicate repeated/inclusive usage.
- [ ] P610-06 (M3): Positive delta computes observed tokens/net source step; zero/negative delta or missing/incompatible endpoints gives N/A with reason; unknown usage is not zero and partial usage is explicitly labelled.
- [ ] P610-07 (M3/M4): Exactly two logical endpoint measurements; no intermediate sampling, additional AI calls, usage-fetch calls or monitoring; scope/interval/profile attribution is visible.
- [ ] P610-08 (M4): Resume/close recollection reuse retained endpoints/results; no invented start for an active Phase, repeat close/commit, new approval or metrics failure gate.
- [ ] P610-09 (M4): Installed help/skill and runtime agree on profile input and actual Phase-close output; focused verification and integrated review cover changed behavior while retaining prior valid evidence.
- [ ] P610-10 (M4): Preserve existing Session/Plan/history/repair counts/accepted work; no live profile switch or mandatory Terra run; Phase 9 comparison and Phase 13 acquisition remain separate.
