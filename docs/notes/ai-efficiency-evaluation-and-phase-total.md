# AI efficiency evaluation and Phase-total accounting

Date: 2026-11-13
Status: adopted planning allocation; no implementation or measured savings claimed

Optimize total work required to deliver an accepted result at the agreed quality.
Selecting a cheaper model is useful only when added repair, review, coordination
or elapsed time does not erase the benefit. Avoid unnecessary AI work first.

| Concern | Plan owner | Scope |
| --- | --- | --- |
| Phase-total consumption | [Phase 20](../phase/phase-20.md) | Aggregate attributable invocations across ordinary resumptions; completion summary first |
| Quality/time/consumption comparison | [Phase 9](../phase/phase-9.md#efficiency-evaluation-and-profile-feedback--2026-11-13) | Refine existing evaluation using comparable work and acceptance scope |
| Implementation/review/repair/coordination breakdown | [Phase 13](../phase/phase-13.md) | Refine existing attribution using available execution metadata |
| Observation-to-profile feedback | Phase 9, consuming existing Phase 6.10 profile configuration | Developer-led configuration changes for future assignments |

Compare like work classes, complexity and acceptance boundaries. Retain final
quality disposition, first-pass acceptance, repairs and observed reopened issues;
do not count a forced closure as a successful accepted result. Show completion
time with its measurement boundary and model-specific usage with coverage.
Missing data is not zero. Keep failed and abandoned effort visible rather than
report only the successful final attempt. No new monitoring campaign is required.

Work category and parent/child role are separate axes. A child may review and a
parent may implement; derive category from existing assignment/execution data,
not agent position or a new AI classifier. Mixed/unclassified work remains
visible and is counted once. Reuse existing inclusion and cumulative-value rules.

Raw tokens across models are not equivalent monetary cost or subscription quota.
Cached/reasoning breakdowns are not added twice. Source-line ratios may help
inspection but must not reward code expansion or penalize deletion/refactoring.
Small ordinary-work observations guide decisions without proving causality.

For profile feedback, retain baseline and changed configuration, reason, relevant
observations and limitations. A developer selects a bounded revision for future
assignments and compares subsequent similar work. Preserve the existing quality
bar and independent review where required. Do not alter active assignments,
automatically optimize profiles or call AI merely to authorize evaluation.

Items 2–4 refine existing plans; only Phase-total aggregation is a new follow-up.
No active Phase acceptance, roadmap priority, runtime configuration or workflow
state changes through this documentation update.
