# Phase 6.10: AI Operating Profiles and Lightweight Phase Metrics

Status: planned; no implementation or acceptance claimed.
Date: 2026-10-27
Owner: sm-workflow development
Sequence: Phase 6.8 -> Phase 6.9 -> Phase 6.10 -> Phase 7
Specification: [profiles and endpoint metrics](../spec/ai-operating-profiles.md)
Checklist: [P610 acceptance](phase-6.10-checklist.md)
Handoff: [English development handoff](../journal/2026/10/2026-10-27-phase-6.10-ai-operating-profiles-handoff.md)

## Purpose and preservation

Keep existing behavior as `standard`, add opt-in `cost` and `quality` selection,
and obtain an initial operational impression from two source counts and already
available usage. The denominator is net physical source lines, not Workflow Step
count. Do not add intermediate sampling or AI calls for measurement or display.

This plan does not change live model configuration, active Phase acceptance,
Session/Plan, pending work, accepted results, history, repair counts or successful
verification. No restart, resubmit, counter reset or replay of successful work.
Keep Phase 6.9's clean/dirty RepositorySync scope intact. Phase 7 retains its
related-project delivery obligations; existing authorized corrections need not
wait for 6.10. Model evaluation stays in Phase 9; usage acquisition stays in 13.

## Main route first

| Unit | Work | Connected result |
| --- | --- | --- |
| M1 | Inventory effective existing selection and expose registered logical profile through config/public input/sm-goal-phase | Omitted and explicit standard preserve baseline behavior; no active configuration switch |
| M2 | Connect cost/quality definitions and Task/Step selection capture to the existing execution mechanism | Explicit parameter > config > standard for new work; issued work stays unchanged; unavailable model is reported without silent fallback |
| M3 | Capture start/end source counts and project existing attributable usage into Phase-close output | Raw counts, signed delta and separate Codex/local ratios, with coverage and N/A reasons; zero additional AI calls |
| M4 | Qualify resume, unavailable/partial data, packaged help/skill and actual close output | Preserved Session/history/results, deterministic result recollection and no new close gate; handoff to Phase 7/9/13 |

Establish standard resolution and one real close-output route before broader
profile cases. Reuse execution profiles, Journal/Evidence and existing usage
receipts. Source counting uses the ordinary managed file/snapshot facilities;
do not introduce a shell workflow, new runner or monitoring loop for metrics.
Record actual coverage: fixture arithmetic proves formulas, not live model usage.

## Validation and completion

Use focused tests for selection precedence, saved-work stability, endpoint
counting and metric rendering. Include new/deleted/renamed source files,
generated-source exclusion, unterminated lines, zero/negative delta, partial and
missing usage, duplicate parent/child receipts, resume and close recollection.
Check unavailable metrics do not change the terminal outcome or repeat effects.
Use one integrated review for the changed contracts and reuse applicable existing
validation results. Installed entry/help must expose the profile parameter and
the actual close output must label unavailable observations honestly.

No compulsory Terra benchmark, provider comparison, extra FULL solely for
statistics, new metrics-fetch call or Phase 13 completion requirement. Retain the
current baseline through the planned November observation; actual Terra adoption
is a later explicit decision. Completion qualifies the mechanism and three named
profiles, not a claim that one profile is faster, cheaper or more capable.
