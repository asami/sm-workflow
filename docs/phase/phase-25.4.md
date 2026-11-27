# Phase 25.4: Strategic Judgement, Task Design and Context Assessment

Status: planned; execution not started
Planned: 2026-11-25 (Asia/Tokyo)
Scheduled: immediately after [Phase 25.3](phase-25.3.md), followed by [Phase 7.2](phase-7.2.md).
Depends on: existing Phase 9 routing, [Phase 25.2](phase-25.2.md) metrics and Phase 25.3 dual allowance
Checklist: [Phase 25.4 acceptance](phase-25.4-checklist.md)

## Goal

Improve task classification independently of model selection. Distinguish
reasoning difficulty from change size and context requirements so bounded API
tasks can use suitable high-capability models without unnecessary agent
execution. Supply provider-neutral capability requirements to Phase 27.

## Delivery

### P254-01 — Task taxonomy

Extend existing typed task and judgment contracts rather than replace them.
Classify purpose (implementation, repair, review, judgment, planning),
reasoning difficulty, change scope, context breadth, acquisition method,
continuity, quality requirements, verifiability and failure impact.
Distinguish deterministic repair, routine compiler repair, local semantic
repair, difficult type or logic repair, and exploration-dependent repair.
Distinguish lint, smell detection, local semantic review, specification and
invariant review, and architectural review. Include classification, strategy
selection, root-cause analysis, convergence and Take Over judgments.
Keep unknown dimensions explicit; file count alone cannot determine difficulty.

### P254-02 — Context assessment

Represent context sufficiency independently of reasoning difficulty:
self-contained, statically resolvable, dynamically resolvable, exploration
required, session dependent, or unknown. Identify required evidence, missing
context, provenance, exact candidate identity and bounded input requirements.
Reuse deterministic context extraction where possible. Allow typed requests
for additional information with bounded iterations; use existing Take Over
when exploration or session continuity is required. Do not expose additional
source or secrets beyond existing authorization and sharing policy.

### P254-03 — Capability requirements and classification policy

Derive provider-neutral requirements for reasoning, context, tools, output
contract and validation. Apply the strategic judgement and Task Specification
routing policy in P254-06.
Mechanically decidable facts and hard constraints remain programmatic. Record classification evidence and uncertainty.
Routing itself remains deterministic. Separate task taxonomy from the AI
capability model and allowance identity. Candidate routes include deterministic
tools, local LLMs, eligible API models and agent CLIs. Routine bounded tasks
may fit Terra/Luna-class models; difficult bounded type, invariant, security
or convergence judgments may fit Sol-class models when verified eligible.
These are hypotheses, not fixed model assignments or quality guarantees.
Reuse Phase 25.3 admission; preserve existing defaults unless an explicit
qualified policy changes them. Phase 27 owns generalized cost-aware routing.

### P254-04 — Failure-aware reclassification

Distinguish insufficient reasoning, missing context, exploration needs,
output truncation, excessive task size, ambiguous specification, nonconvergence,
quota refusal and uncertain execution. Choose bounded remedies: additional
context, different capable model, task split, clarification, or Take Over.
Do not escalate reasoning merely because context is missing. Preserve repair
limits, candidate validation, rollback and authorization; uncertain execution
must follow existing recovery semantics.

### P254-05 — Evidence and qualification

Extend available Phase 25.2 observations with task classification, context
assessment, selected capability and route, failure reason, validation outcome,
retries, latency and reported token usage. Preserve missingness and avoid
additional calls solely for metrics. Compare representative bounded tasks
using identical context and validation across eligible model classes where
authorized. Distinguish measured quality and cost from estimates; do not
assume stronger models always improve outcomes. Verify deterministic rules,
unknown classifications, bounded context requests, failure-specific routing,
resume and ordinary review/repair/judgment paths using fixtures and scoped
qualification. Record independent review before closure.

#### Work counts in the command result

Include a structured summary in the normal `sm-workflow` command result.
Select the work opportunities that the invocation issues, selects, attempts,
or resolves, using its actual Session and work references. Report their
counts by work purpose, execution route, and owning project. For example, an
Advance that runs validation and issues a review reports the completed
validation and the pending review. A Submit reports the disposition of the
work it receives. Apply this contract to every caller.

At GoalPhase completion, return the Phase total and relevant summary in
`completion.metrics`. Keep the complete Phase breakdown available through
the public retained completion. The same structured rows drive human-readable
output. Preserve confirmed zero, partial coverage, and unknown values for
the selected scope.

Count every work opportunity issued for the Phase or its linked workflows.
Use the declared work purpose and the retained reason when a shared
operation serves more than one purpose. The inventory includes the following
work where the selected workflows issue it.

- Requirements and context gathering, analysis, estimation, Phase or Step
  partitioning, strategy formulation, design, instruction preparation, and
  plan revision.
- Diagnosis and triage, implementation, error repair, FixCheck repair,
  review repair, and recovery or reconstruction after interruption.
- Validation, including Compile, ADMISSION, TestOnly, FULL, FixCheck,
  integration and installed-runtime qualification. Count test cases
  separately from validation work.
- Review, independent review, and re-review. Retain the actual review
  scope, including Step, Phase, merge, and delivery acceptance.
- Documentation and evidence recording, dependency development and
  delegation, result receipt and adoption, artifact publication and
  activation, repository integration and synchronization, local commit,
  and release closure.
- Human decisions, including scope approval, plan adoption, continuation,
  or deferral.

Verify coverage against the work and effect declarations of GoalPhase and
linked SplitPhase, GoalTask, DelegateTask, StandaloneFix, RepositorySync,
publication and evidence operations. Retain the original work kind and
an explicit unclassified row for an observed item with no purpose mapping.

Record an applicable built-in rule or template, attempted routes, and the
route that completed each item. Report completion by the command itself,
by external tool with the tool name, by AI with Codex, OpenAI daily
allowance, OpenAI monthly credit, or an unknown AI route, and by a human
when the operation requires a human decision. Attribute allowance use
from retained evidence.

Retain the Phase total and the same breakdown by the project that owns
each work item. Use the registered repository or Component identity for
each dependency project. Preserve the dependency edge and the local
worktree or assigned chat as attribution details. Join the parent request
and destination result through their retained identities. Receipt and
adoption are requester operations, and the dependency implementation,
validation, review, and publication belong to the dependency project.
Count a delivered result once at its actual work owner even if multiple
requests reference it. Report missing project attribution explicitly.

For each purpose, reconcile the total with completed work by route, unfinished
work, skipped work, and work with unknown disposition. Count selections,
attempts, retries, and outcomes separately from work completed. For example, a
repair completed by Codex after a Scalafix attempt is one completed repair
with one attempt on each route. A matched template followed by AI execution
retains the template match and credits completion to the AI route. Preserve
known subtotals, confirmed zero, and unknown coverage across resumed work
without duplicating a work item or an attempt.

### P254-06 — Strategic judgement and task design

Separate Strategic Judgement (strategy and risk), Task Assessment
(classification and capability requirements), Task Design (review viewpoints
and execution instructions), and Task Execution (performing the work).

Use existing diagnostics and applicability rules to select the first suitable
path before dispatch:

1. **Deterministic resolution.** Apply a supported rule or tool when the input
   establishes its applicability and the intended result is fully determined.
   Verify a repair through the usual validation path. This path uses no model.
2. **Template directive for the executor.** When the approach is already known
   and the work still requires code or semantic judgment, fill an applicable,
   versioned template with the actual diagnostics, permitted scope, required
   context and acceptance checks. Send the resulting typed Task Specification
   directly to the selected Terra or Sol executor under the execution policy
   below.
3. **AI-designed directive.** When the available templates cannot specify a
   safe approach, use strategic judgement to design the directive. Send the
   admitted directive to the executor and verify the outcome.

The initial applicability check uses existing evidence. A known case can
proceed immediately; a case outside the known patterns goes to strategic
judgement without first attempting a speculative repair. Use the same
distinction for reviews and other bounded work.

Connect [Phase 25.5's diagnostic-driven repair strategies](phase-25.5.md#p255-06--diagnostic-driven-repair-strategy-templates)
to the same applicability check and typed Task Specification. Phase 25.4
qualifies the connection with fixtures. Phase 25.5 delivers pattern matching,
template qualification, scope, versioning and invalidation after Phase 25.4.

Retain the diagnostic pattern, strategy or rule, candidate and tool versions,
and actual validation and repair outcome through existing evidence. Use
qualified strategies for later candidates whose current diagnostics, semantic
context, project policy and tool versions satisfy the template's conditions.
Bind the current candidate and let the execution policy select Terra or Sol.
Reuse the strategy and its validation conditions; a saved edit or a previous
PASS does not establish correctness on another candidate. Unknown, stale or
ambiguous matches continue through ordinary assessment. Promote a recurring
strategy to deterministic repair only after qualification at the owning rule
or tool boundary.

For cases that require AI strategic judgement or directive design, select the
design model in this order:

1. **Astra on verified free allowance** when the model is eligible and
   Phase 25.3 admission confirms that the bounded request fits the free route.
2. **Sol within the Codex subscription** when free Astra cannot admit the
   request and an eligible Sol route has verified room below the configured
   weekly utilization ceiling. Reserve the remaining headroom for other work.
3. **Last resort using a prepared directive** when neither design route is
   admissible or the design result is unusable. Select an existing versioned
   directive whose applicability conditions match the evidence. Bind the
   candidate context and scope, then delegate execution to Terra or Sol as
   required by the directive. An applicable deterministic rule can still
   resolve a known part directly. Keep any remaining problem explicit and
   use the existing Take Over or clarification path when no directive applies.

Use the latest available observation of utilization and reset time for the
actual account and shared allowance. Apply the existing route checks at
dispatch. An unknown or stale observation, a reached ceiling, or an unavailable
route leaves this subscription option inadmissible. The weekly ceiling is a
configurable policy value and must be qualified with representative use.

For an explicitly selected metered Sol route, require paid authorization
that covers this work and a verified remaining monetary budget for the
request. Keep this authorization and accounting independent of subscription
utilization. The ordinary subscription route uses the last-resort policy when
the weekly ceiling is reached. Mechanically decidable facts stay programmatic.

Use stateless requests with bounded Strategy Context: goal, current state,
constraints, evidence, summarized attempt history, available capabilities,
candidate identity and typed decision contract. sm-workflow owns persistent
state. Pass the strategy into the executor's directive as a typed Task
Specification containing review viewpoints, execution instructions, repair
strategy, task decomposition, required context, expected outputs and
validation conditions as applicable. Record applicability evidence, the origin
of the directive, selected model and entitlement where used, and uncertainty.
Avoid reapplying the same unsuccessful rule or directive to the same inputs.
Preserve reservations and apply existing recovery to uncertain dispatch or
usage before considering another invocation.

Model requirements are independent of entitlement: [Phase 23 P23-05 divergence
recovery](phase-23.md#bounded-divergence-recovery-p23-05) is an Astra-required
task class, not a routine strategic-judgement fallback. That separate
policy may use separately authorized paid Astra when free Astra cannot
admit the required context or quota. It does not change this default, allow
implicit paid overage, or permit substitution with a lower model.

Use eligible Terra-class models in the 10M daily allowance for most bounded
review, repair and code generation. Select Sol when the directive needs
reasoning or capabilities beyond the eligible Terra route. Apply the same
execution policy to ordinary templates, AI-designed directives and last-resort
directives. Choose on the content and capability needs of the work, subject to
existing route, entitlement and budget checks. Use agent CLIs for exploration
or session-dependent work when appropriate.

AI-generated plans are proposals. Programmatic controls enforce authorization,
sharing, quota, scope, mandatory tests and Admission. Plans cannot weaken
mandatory checks or grant permissions. Retain validated reusable review
viewpoints and instruction templates with provenance, applicability conditions
and versioning; revalidate before reuse. Promote generalized knowledge to
deterministic rules only after review and verification.

Compare AI-designed and template-based directives with applicable direct
review baselines. Hold execution capability constant when assessing the effect
of design and record Terra and Sol execution separately. Measure end-to-end
quality, missed findings, retries, latency and total tokens including design
calls. Verify free Astra priority, subscription Sol below the weekly ceiling,
last-resort directive selection, and separately selected metered Sol with
authorization and sufficient monetary budget. Include missing or stale usage,
a weekly reset, invalid plans, uncertain dispatch, restored free allowance and
unchanged mandatory validation.

### P254-07 — Entitlement-independent architecture and economic evidence

Treat Task Design / Task Execution separation as independent of billing
or allowance. Use fixtures and available free capacity to qualify the architecture
first. Phase 25.4 owns the weekly subscription guard and separately authorized
metered Sol option in P254-06. Preserve interfaces so Phase 27 can generalize
explicitly budget-authorized advanced design with lower-cost execution when
evidence supports its economic value.
Compare against direct advanced execution and programmatic design with
ordinary execution on comparable tasks and mandatory quality gates.
Measure total incremental cost per successfully accepted task, including
design, context preparation, execution, retries, validation, failed attempts
and amortized reusable design artifacts; report latency and quality separately.
Keep observed charges, allowance consumption, estimated prices and unknowns
distinct. Record conditions under which separation is beneficial or not.

### P254-08 — Human-selected optimization profile

Before a Project execution starts, accept an explicit Human selection of a
versioned optimization profile through the Project/Session start configuration.
Offer three named profiles (working names: Baseline, Standard, Aggressive).
Baseline uses the ordinary path without the new optional optimizations;
Standard enables qualified reuse and bounded optimizations; Aggressive may
add eligible proactive preparation within the same limits. Keep the mapping
explicit and versioned, and do not claim an optimization is enabled before
its implementation is available. Omitted selection preserves existing behavior
and is recorded as unspecified, not as measured Baseline.

Resolve the selection before dispatch and record the profile ID/version and
effective optional mechanisms with the execution identity. Inherit that
selection in child Steps and on resume; never let AI, quota changes or retries
silently change it. A different level requires a new Human-selected execution.
The same required checks, Admission, capability requirements (including any
mandatory Astra class), authorization and retry/spend ceilings apply to all
profiles. The profile is independent of model reasoning level and grants no
permission to spend. Use [Phase 25.2](phase-25.2.md) metrics for ordinary-run
comparison; [Phase 25.5](phase-25.5.md) consumes the profile for optional
template and cache behavior.

## Operational validation of Phase 25.3

Use the installed Phase 25.3 route for eligible actual work during Phase 25.4
development. Extend the two live release cases from Phase 25.3 by observing
ordinary reviews and bounded compiler repairs as they arise, with the existing
evidence, allowance, cost and Admission policies.
Record actual provider and model, allowance group, reported tokens and
verified billing separately. Preserve reasons for deterministic resolution,
ineligibility, fallback, or uncertainty. Use the observed classification and
context cases in P254-01, P254-02 and P254-04. Report cases with no live
opportunity or incomplete evidence explicitly.

Use these observations, including recurring work suitable for reuse and
directive design costs, to decide when to start [Phase 25.5](phase-25.5.md).

## Ownership and boundaries

sm-workflow owns task semantics, context assessment, Workflow and Admission.
textus-ai owns provider capability descriptors, generic execution and quota
admission. Reuse Phase 9 convergence and Take Over semantics, Phase 25.2
metrics and Phase 25.3 allowance accounting. Do not duplicate Phase 26 server
work or Phase 27 generalized routing. Planning does not start a Session.
