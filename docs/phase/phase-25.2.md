# Phase 25.2: Workflow Completion Metrics

Status: locally released on 2026-11-27 (Asia/Tokyo), commit `d4f14e79`
Planned: 2026-11-24 (Asia/Tokyo)
Scheduled: immediately after [Phase 25.1](phase-25.1.md), followed by [Phase 28.2](phase-28.2.md)
Depends on: released [Phase 25](phase-25.md) Task API and existing execution ledger.
Reuse available Phase 25.1 route attribution and Phase 28.1 repair evidence; do not alter their acceptance criteria.
Checklist: [Phase 25.2 acceptance](phase-25.2-checklist.md)

## Goal and scope

At ordinary sm-workflow completion, show how often AI was actually called and
which deterministic tools actually fixed the candidate. Use existing execution,
repair, and result evidence. Deliver a useful per-Workflow Session view now;
record available per-execution token usage without adding a new metering service.
Keep durable cross-run attribution, cost accounting, and Phase-total
reporting in [Phase 13](phase-13.md) and [Phase 20](phase-20.md).

Add one early validation delivery in P252-05 to reduce time spent on routine
Steps and repository synchronization. Start the CNCF contract and consumer
integration early in the Phase. The completion metrics can proceed
independently of the CNCF delivery.

## P252-01 — AI invocation counts and available token usage

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

- Record provider- or host-reported tokens for confirmed invocations when
  available: input, output and total as reported or derivable from an explicit
  usage contract. Preserve provenance. Do not add cached or reasoning token
  subsets again; do not subtract them or infer charges. Avoid double-counting
  cumulative events, retries or duplicate results for the same invocation.
- Show observed token subtotals by the same breakdown and for the Session, along
  with usage coverage (calls with usage / confirmed calls). When some calls
  lack usage, label the subtotal incomplete; when none report usage, show
  unknown, not zero. Keep genuinely reported zero distinct from unknown. Do
  not estimate missing tokens or make additional calls to obtain them.

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

- Emit compact Markdown tables for AI usage, retained automatic repairs, and
  operational metrics directly in the initiating Codex chat as
  part of the ordinary Workflow completion reply. A CLI-only result, log,
  attachment, or instruction to open a separate report does not satisfy this.
  Derive the tables from the same typed completion metrics; do not ask AI to
  reconstruct the numbers. Example layout (illustrative counts only):

  | Engine / configured route | Thinking mode | Reasoning level | Calls | Input tokens | Output tokens | Total tokens | Usage coverage |
  | --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
  | Codex | Review | High | 2 | Unknown | Unknown | Unknown | 0/2 |
  | OpenAI API / daily | Repair | Medium | 1 | 1,400 | 300 | 1,700 | 1/1 |

  | Successful repair tool | Applications |
  | --- | ---: |
  | Scalafix | 0 |
  | Metals | 1 |
  | Other / built-in correction | 1 |

  | Operational metric | Observed result |
  | --- | --- |
  | Resolved without AI repair | 5/8 attempted repair episodes |
  | Rework cycles | Compile 2, test 1, lint 0, review 1 |
  | Elapsed / cumulative time by stage | Elapsed 15m; AI 4m, build/test 7m, approval wait 6m |

- Keep the same counts, known token totals, coverage and missingness available
  as structured completion data. Aggregate the current Session and linked
  child executions only where evidence permits; deduplicate by stable invocation
  or repair ID across resumes. Mark incomplete coverage, including older or
  opaque external executions; never read prompts, source, or secrets to estimate it.
- Metrics are observational. Missing evidence must not block closure or alter
  validation, Admission, repair limits, or synchronization. No extra AI calls.
- Verify rendering in the initiating Codex chat through the normal packaged
  completion path with representative events,
  replay, partial evidence, zero counts, rollback, and later provider extension.

## P252-04 — Operational metrics in the initial release

- **Resolved without AI repair:** group by stable repair episode. Among episodes
  with an actual automatic correction attempt and a known disposition, count
  those resolved by a retained, successfully revalidated deterministic edit
  without an AI repair invocation for that episode. Show numerator and
  denominator, and separately show unresolved or unobservable episodes. A
  diagnostic-only check is not an attempt. Separate the count of successful
  edit applications (P252-02) from the count of resolved episodes. This is an
  observed outcome, not an estimate of avoided tokens or calls.
- **Rework cycles:** count actual repeat compile, test, lint, and review passes
  belonging to a rework cycle, by stage. Exclude the initial pass, skipped
  attempts, and replay of retained events. Use retained cycle and stage
  identities or explicitly report unavailable attribution; do not substitute
  general run counts for rework.
- **Time:** report elapsed Workflow Session time and separate observed
  cumulative durations for AI execution, build/test, and human approval wait.
  Use recorded start and end boundaries, identify incomplete intervals, and
  do not sum concurrent activity into wall-clock elapsed time. If a duration
  cannot be observed, mark it unknown rather than inferring it.

## P252-05 — Short Step validation through CNCF typed SBT

- Use `ADMISSION` for routine GoalPhase Step acceptance, together with the
  focused `TestOnly` checks required by the change. Keep `FULL` for Phase
  closure and whenever the Plan requires broader coverage. Preserve explicit
  validation already issued to existing Sessions, including Phase 25.1.
- Add a typed `Full`/`Admission` selector to CNCF's `sbt_test` and
  `sbt_test_attempt` as overloads. Keep the existing single-argument entry
  points and their `FULL` semantics. CNCF resolves selection, binds execution
  to the Session workspace, and records the requested scope and actual
  execution through its existing typed SBT/UoW path. Reuse CNCF validation
  discovery and selection; keep exact `TestOnly` for change-specific checks.
- Select `ADMISSION` through [validation metadata](../spec/goal-phase-validation-metadata.md)
  and apply the [ordinary-test exclusions](../spec/label-driven-test-selection.md#execution-and-preservation).
  Use about one minute of test-body time as the selection target. Preserve
  the existing prerequisites. When complete discovery verifies an empty
  ADMISSION selection, start no test bodies and show `ADMISSION対象0件`.
  Successful Compile in the same validation candidate satisfies the Scala
  minimum in that case. Unavailable or unverified discovery leaves the
  required check unsatisfied. Any selected focused checks still run, and
  Phase closure continues to require FULL. No external-service test may
  start through routine `ADMISSION`.
- Qualify the published CNCF contract in the installed sm-workflow path.
  Record one ordinary Step accepted with `ADMISSION` and its applicable
  focused checks, followed by Phase closure with `FULL`. Confirm result
  attribution to the same Session and exact candidate, including resume,
  and distinguish compile/setup time from test-body time. Use comparable
  retained FULL evidence to show the executed selection and elapsed time
  where available. Preserve the rest of the Phase 8 validation residuals
  in [Phase 22](phase-22.md#selected-residual-work).

### RepositorySync merge validation

Use the same CNCF selector for actual merge candidates in the standard
[RepositorySync route](../spec/repository-sync-runtime-orchestration.md).
Apply the classification to the final candidate against the merge base and both
parents. The strongest applicable row sets product test scope.

| Merge contents | Product test scope |
| --- | --- |
| Only documentation outside the project's build and runtime inputs | None. Run the applicable structural and document checks. |
| Source changes carried through from either parent without combining or editing program logic within a source file. A merge or conflict resolution limited to ordinary comments also qualifies. | `ADMISSION` only, including its existing build prerequisites. |
| Both sides' program logic is combined within a source file, or merge resolution changes program logic. This also applies when Git reports a clean merge. | `FULL`. |

Determine the source result from the actual changes and candidate content.
Use a language-aware comparison where comment equivalence matters. Preserve
significant whitespace, string contents, directives and comments consumed by
the build, tooling or generators. Use existing impact and coverage rules for
consumed non-source inputs such as build definitions, dependency configuration,
generators and submodules, and for changes whose classification cannot be
established. A required broader scope takes precedence over `ADMISSION`.

Use `FOCUSED` (`TestOnly`) to check specific affected behavior or to reproduce
and verify a bounded repair. Run it when the existing project policy or the
actual finding identifies the behavior to check. A repair can use `FOCUSED`
first and then satisfy the merge candidate's required `ADMISSION` or `FULL`
before acceptance. A routine source merge whose selected scope is `ADMISSION`
does not add `FOCUSED` by default.

Use this policy for the applicable local-main and remote merge stages. Equal,
push-only and fast-forward keep their existing checks. Keep project lint,
conditional semantic review, applicable validation reuse and Git verification.
Record the selected scope and reason using existing candidate evidence and
preserve issued plans and accepted results. Qualify the three outcomes through
installed sm-workflow with local Git fixtures, including a clean logic merge,
a comment-only conflict, and a build-consumed document.

## Optimization comparison from ordinary executions

Reuse completion metrics for the Human-selected
[Phase 25.4 optimization profile](phase-25.4.md#p254-08--human-selected-optimization-profile)
when its metadata is available. This reporting extension does not block the
initial Phase 25.2 release on Phase 25.4. Record the requested profile, its
version and effective mechanisms with each run; keep unspecified and legacy
runs separate from Baseline.

- Compare observed tokens per distinct logical Step within comparable task
  role and difficulty cohorts (e.g. strategy judgment, compiler repair,
  review). Sum actual invocations for the Step, including retries and
  authorized fallbacks; do not count replay twice. Include confirmed zero-AI
  Steps and report cohort size and token coverage. Where usage is partial,
  report tokens per Step for the complete-coverage subset and show the
  partial subtotal separately;
  do not treat missing usage or unknown difficulty as zero or equivalent.
- Also report actual AI calls per completed task, including tasks with zero
  calls. Show incomplete/failed tasks and their observed usage separately so
  that cheaper but less successful routes do not appear equivalent. Keep
  observed Codex allowance usage, free API use and paid usage distinct where
  measurable; do not derive Codex allowance units from token counts.
- Use operational logs rather than regenerate the same program for metrics.
  Compare only when role, difficulty and required acceptance are comparable;
  identify model/route differences and sample size. No extra AI calls or
  completion gate are introduced for measurement.

## Secondary breakdowns from existing records

- When the execution record identifies the actual task role, show AI calls
  (and available token subtotals) by implementation, repair, review, and
  judgment. Keep unknown roles explicit.
- When retained routing decisions identify an actual route switch, show its
  count and recorded reason (e.g. allowance exhausted or provider unavailable).
  Count the switch decision once; the invocation count still covers only actual
  AI execution. Do not attribute unrecorded reasons or change fallback policy.
- These two breakdowns are best effort: do not add a separate instrumentation
  project or a completion gate. Prefer observed comparable runs over a
  speculative counterfactual savings estimate.
