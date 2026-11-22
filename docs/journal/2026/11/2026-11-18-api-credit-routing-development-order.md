# Development order for Context Packaging and API-credit routing

Date: 2026-11-18 (Asia/Tokyo)
Status: user-selected development priority; current allocation is Phases 24/25 (2026-11-19); implementation not started

## Initial same-day allocation (superseded below)

The user selected the following development order for this chat:

1. Finish the currently developing stabilization Phase.
2. Develop and complete the remaining [Phase 7](../../../phase/phase-7.md) work.
3. Prioritize Context Packaging and cost-aware API provider routing within
   [Phase 9](../../../phase/phase-9.md).

The design input is the GitHub
[Context Packaging and Cost-Aware Provider Routing proposal](https://github.com/asami/sm-workflow/blob/main/docs/journal/2026/2026-11-18-context-packaging-provider-routing.md).
Its objective is to use eligible OpenAI API credits for work with a bounded,
sufficient context package, retaining the normal validation and Admission route
and a provider-independent continuation when another executor is needed.

## Initial planning integration (superseded allocation)

Use Phase 9's existing API Direct / Agent Continuation and R1 -> R2 -> R3
delivery structure. Begin with deterministic context assembly and a bounded
supplied-input review connected through a real API adapter and the existing
result-acceptance route. Subsequent implementation/repair routing retains the
candidate, accepted results, validation history and remaining repair allowance.
Packaging should use existing structured workflow inputs and avoid routine AI
calls merely to construct the package.

This is the latest priority for this chat after stabilization and Phase 7.
Earlier serial and parallel-lane decisions remain historical records; they do
not require this chat to undertake unrelated planned Phases before prioritizing
this work. At implementation planning, assess the actual accepted Phase 8 and
CNCF/provider capabilities required by the selected slice. Retain any concrete
missing prerequisite explicitly; scheduling does not prove its delivery or
waive acceptance requirements.

Budget implementation must account for the proposal's requirement to avoid
unintended paid usage. The official
[API credit benefit documentation](https://developers.openai.com/api/docs/guides/chatgpt-api-credits)
states that purchased credits can be consumed after free credits run out.
Account eligibility, activation and remaining credits have not been verified by
this planning record.

## Initial Phase 22/23 allocation — 2026-11-18 (superseded numbering)

After checking the existing Phase identities, the user adopted new numbers:

1. Finish the current stabilization development.
2. Finish the remaining existing Phase 7 development.
3. Assess and deliver only concrete required producer/setup prerequisites.
4. Deliver [then-proposed API Phase 22; now Phase 24](../../../phase/phase-24.md): practical Context Packaging
   and OpenAI API-credit use for cost reduction.
5. Follow with [then-proposed API Phase 23; now Phase 25](../../../phase/phase-25.md): the generic textus-ai
   Task API and selected multi-provider expansion.

This replaces the initial allocation above to Phase 9 and the intermediate
suggestions of Phase 10/11 or 12/13. Existing Phase 8 runtime improvements,
Phase 9 convergence/routing, Phase 10 CBD, completed Phase 11 Journal, closed
Phase 12 Evidence and Phase 13 usage accounting retain their identities and
history. No existing plan is overwritten with an unrelated feature.

Phase 22's prerequisite schedule identifies PRE-1 contract inventory; PRE-2 the
required CNCF Phase 80 P80-A/B/C subset if missing; PRE-3 existing textus-ai
AiRunner/OpenAI readiness and necessary fixes; and PRE-4 actual API access and
verified no-paid-overage setup. The Phase 80 plan is still marked planned;
local textus-ai-core contains the adapter/cost foundation but installed consumer
readiness and the user's credit configuration have not been established.
Assess actual code, delivery and evidence first; consume ready capabilities and
schedule missing development with the proper producer owner before consumer use.
No full Phase 80, whole Phase 9, broad provider matrix or Phase 13/20 completion
is inserted as a prerequisite merely by association.

The second [design proposal](https://github.com/asami/sm-workflow/blob/main/docs/journal/2026/2026-11-18-textus-ai-task-api-integration.md)
clarifies the shared responsibility boundary. Phase 22 already keeps provider
transport/credentials and existing cost policy in textus-ai, uses a minimal
provider-neutral consumer boundary and retains per-call evidence. Generic task
schemas, capability/quota catalog expansion and selected multi-provider routing
follow in Phase 23. Do not implement the same OpenAI adapter in sm-workflow or
delay practical Phase 22 use until generic expansion is complete.

Phase 9 shared dispatch, typed acceptance and retained-work handoff are reused
with applicable evidence references; its ledger remains unchanged in identity
and unchecked state. A Phase 22 result is not proof of all R1–R3 convergence
conditions. Phase 22 and 23 have distinct new checklists, all unchecked.

## Current allocation and preserved schedule — 2026-11-19

The user requested preserving both sets of plans, moving the unpublished API
plans to unused numbers, reconciling references, and committing the result to
GitHub main. The latest native GitHub main already assigns Phase 22 to runtime
stabilization and Phase 23 to strategic convergence. Those two plans and
checklists are retained verbatim; the API plans had not been committed there.
The historical allocations above do not select those existing Phases.

| Unpublished local API plan | Current canonical plan | Condition IDs |
| --- | --- | --- |
| API Phase 22: Context Packaging and practical API-credit use | [Phase 24](../../../phase/phase-24.md) / [checklist](../../../phase/phase-24-checklist.md) | P22-01–11 -> P24-01–11; all remain unchecked |
| API Phase 23: textus-ai Task API and provider expansion | [Phase 25](../../../phase/phase-25.md) / [checklist](../../../phase/phase-25-checklist.md) | P23-01–08 -> P25-01–08; all remain unchecked |

The user's latest priority for sm-workflow-dev開発 is **current stabilization
-> remaining Phase 7 -> necessary bounded producer/setup prerequisites ->
Phase 24 practical API use**, followed by Phase 25 expansion. Phase 8's
Human-approved FORMAL release is a consumable predecessor; its retained native
Human state and transferred assurance in canonical Phase 22 do not reopen it or
insert the whole stabilization-successor scope ahead of API use.

PRE-1–4 are retained in Phase 24: inspect the actual dispatch/adapter contracts,
qualify only missing CNCF/textus-ai capability with their owners, and establish
usable API access and enforceable no-paid-overage configuration. Reuse delivered
capability and relevant evidence. This allocation does not require whole Phases
8.1/9/13/20/22/23/25 to finish before Phase 24; a concrete capability gap remains
an explicit scheduled prerequisite, never assumed ready from a document title.

Phase 9's existing R1–R3 requirements and earlier producer/customer rollout are
preserved. Shared API/agent dispatch and handoff evidence are cross-referenced
at their applicable scope, without duplicate implementation, duplicate live
calls or automatic Phase 9 acceptance. The new consumer lane is recorded as a
separate explicit current priority; it does not silently reassign another chat.

This journal is a design/decision record, not an execution log. Planning,
renumbering and publication do not start a Phase or accept an unchecked item.

## Preservation

This decision records future priority only. Preserve the existing Phase 7
Session, issued work, accepted results, successful checks, histories and repair
counts. It starts no Phase and neither restarts nor resubmits existing work, and
does not change another chat's assignment. Existing source changes and Git
staging remain intact. No implementation, test execution, publication, account
configuration or push is part of this record.

## Phase 25 after Phase 28 — 2026-11-22

The user selected Phase 25 as the next Phase after
[Phase 28](../../../phase/phase-28.md), ahead of Phase 18. The decision moves
normal OpenAI API use into [Phase 25](../../../phase/phase-25.md#ordinary-openai-use).
Phase 28 supplies deterministic fixes first. Phase 25 starts with independent
review and small remaining compiler-error repairs and measures routing through
the ordinary sm-workflow command. Task eligibility, verified usage bounds,
runtime-owned patch checks and existing Codex continuation stay with their
respective owners. This decision changes scheduling and acceptance scope.
