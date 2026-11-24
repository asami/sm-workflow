# Phase 25.2: Workflow Completion Metrics

Status: planned; execution not started
Planned: 2026-11-24 (Asia/Tokyo)
Scheduled: immediately after [Phase 25.1](phase-25.1.md), followed by [Phase 7.2](phase-7.2.md)
Depends on: released [Phase 25](phase-25.md) Task API and existing execution ledger.
Reuse available Phase 25.1 route attribution and Phase 28.1 repair evidence; do not alter their acceptance criteria.
Checklist: [Phase 25.2 acceptance](phase-25.2-checklist.md)

## Goal and scope

At ordinary sm-workflow completion, show how often AI was actually called and
which deterministic tools actually fixed the candidate. Use existing execution,
repair, and result evidence. Deliver a useful per-Workflow Session view now;
keep durable cross-run usage attribution, token/cost accounting, and Phase-total
reporting in [Phase 13](phase-13.md) and [Phase 20](phase-20.md).

## P252-01 — AI invocation counts

- Count one confirmed execution per invocation ID, including retries or fallback
  calls that actually ran. Do not count dispatch selection, an unused fallback,
  a rejected request before execution, or repeated delivery of the same result.
  Show indeterminate execution separately rather than guessing. Keep outcome
  separate from whether the invocation ran.
- Show a joint breakdown by execution engine, configured entitlement route,
  thinking mode, and reasoning level. Distinguish Codex, OpenAI API monthly
  route, OpenAI API daily route, and other engines by name when known. Preserve
  mode and level as separate dimensions, and show unknown or not applicable
  explicitly. Show the executed engine and effective settings when observed;
  never substitute planned values for missing execution facts.
- A configured monthly/daily route is not proof of actual billing or free usage.
  Label the route as configured, and show unknown if it is not known. Do not
  query billing services, infer credit consumption, or change routing.

## P252-02 — Successful deterministic repairs

- Count one per distinct repair application that actually changed the candidate,
  passed revalidation, and was retained. Attribute it to Scalafix, Metals, or
  other in-program corrections; retain the tool name for other corrections when
  available (e.g. Scalafmt or built-in fixes). Count applications, not changes
  per file, rule, or diagnostic. Count each application only once.
- Do not count check-only lint, proposed edits, no-ops, rejected patches or
  rolled-back repairs as successful. Keep zero distinct from unavailable.
- Reuse available repair evidence, including the Metals and Scala correction
  routes delivered by Phase 28.1. Count only the actual tool used: an
  applicable Scalafmt fix is not a Scalafix fix. If a route is disabled,
  unsupported, or lacks evidence, report that limitation honestly.

## P252-03 — Completion output and verification

- Emit two compact Markdown tables directly in the initiating Codex chat as
  part of the ordinary Workflow completion reply. A CLI-only result, log,
  attachment, or instruction to open a separate report does not satisfy this.
  Derive both tables from the same typed completion metrics; do not ask AI to
  reconstruct the numbers. Example layout (illustrative counts only):

  | Engine / configured route | Thinking mode | Reasoning level | Calls |
  | --- | --- | --- | ---: |
  | Codex | Review | High | 2 |
  | OpenAI API / daily | Repair | Medium | 1 |

  | Successful repair tool | Applications |
  | --- | ---: |
  | Scalafix | 0 |
  | Metals | 1 |
  | Other / built-in correction | 1 |

- Keep the same counts available as structured completion data. Aggregate the current Session and linked
  child executions only where evidence permits; deduplicate by stable invocation
  or repair ID across resumes. Mark incomplete coverage, including older or
  opaque external executions; never read prompts, source, or secrets to estimate it.
- Metrics are observational. Missing evidence must not block closure or alter
  validation, Admission, repair limits, or synchronization. No extra AI calls.
- Verify rendering in the initiating Codex chat through the normal packaged
  completion path with representative events,
  replay, partial evidence, zero counts, rollback, and later provider extension.
