# Phase 6.5 adoption and development handoff

Date: 2026-10-20
Status: adopted plan; implementation and acceptance not started
Intended development chat: sm-workflow-dev開発

## Decision

The user adopted a separate Phase 6.5 in the sm-workflow-dev development checkout
after discussing Step-review reduction and asked to create the Phase. The current
checkout is `sm-workflow-dev`, branch `development`; its absolute host path is an
environment detail, not a repository identity or a new worktree requirement.

The [plan](../../../phase/phase-6.5.md) and
[checklist](../../../phase/phase-6.5-checklist.md) establish the sequence
6.4 -> 6.5 -> 7. This adds no prerequisite to already accepted 6.3 production use.
The original proposal was the sibling sm-workflow repository's
`docs/journal/2026/10/2026-10-20-step-review-reduction-workflow-optimization-plan.md`.
That proposal nominated 6.5 provisionally. The adopted decisions below supersede
its mandatory CBD Release review and implicit reviewless-admission/FIX details;
this handoff is self-contained and requires no sibling checkout at execution.

## Adopted contract

- PLAN chooses Step kind and conditional Actions. Ordinary code Steps require
  compile and actual focused behavioral tests; semantic Step review defaults OFF.
  Applicable Scala/CAR lint augments this assurance. Q3 now delivers a minimal
  Scala lint profile, verified on Scala 3.3.8 and then ON for applicable code
  Steps/Release. It can remain OFF during Q1/Q2 preparation, but not as a substitute
  for Q3 delivery; a selected enabled check cannot disappear after failure.
- A Step kind can select semantic review. Do not remove that route or add an AI
  decision call at every Step to decide whether to use it. Review OFF is recorded
  as such, with real deterministic admission evidence, never fabricated PASS.
- Step and Release both have FIX. Batch related required repairs, revalidate the
  affected scope, reuse unaffected tests/reviews and return to the same acceptance
  boundary. Release fixes preserve closed Step commits and do not introduce a
  new Step/review/approval ceremony per finding.
- Release performs integrated business/meaning review; re-review is limited to
  unresolved findings, repair delta and impacts. Final reuses existing results.
  Findings outside the accepted requirement boundary do not become mandatory work.
- Existing repair counts, limits and history remain. The user explicitly accepted
  leaving observed convergence and the ten-cycle final backstop in Phase 9 for
  now. This is not a claim that those future controls already work everywhere.
  Provider/Action changes and intermediate repair commits do not refund budgets.
- CBD review is OFF until Phase 10. No Phase 9/10 completion dependency is added.

## Development instructions

1. Complete the current 6.4 execution under its existing admitted contract. Keep
   its E1 accepted commit, open E2–E4 work, pending Human decision, successful
   validation, Goal/Session identity, counters and all working-tree changes.
2. Start 6.5 separately after 6.4 closure. Reuse the accepted runtime and inspect
   only the gaps for Action policy and formal admission. Update connected typed
   contracts together rather than retaining a legacy mandatory-review adapter.
3. Deliver Q1 first: code change -> compile/focused/applicable lint -> formal
   reviewless admission -> native Step commit in the real workflow.
4. Deliver Q2: failed Step -> FIX -> acceptance, and Release finding -> FIX ->
   affected validation/necessary scoped review -> Final/local release. Reuse the
   existing correction machinery and standing authority for in-scope repairs.
5. Deliver Q3's minimal Scalafix syntactic-rule profile from existing conventions,
   actual Scala 3.3.8 connection and lint -> FIX -> admission route. Enable the
   adopted applicable rules; add the selected semantic-review branch and affected
   skill wiring. Preserve OFF, unmet condition, failure and unavailable outcomes.
6. Complete Q4 integrated acceptance and Phase 7 handoff. Use available 6.4
   baseline facts for review/cost comparison; no new mandatory measurement service.

Do not modify the running workflow's policy to self-accept 6.5. Exercise the new
behavior in explicitly selected validation executions; adopt it for subsequent
work only through normal accepted delivery. Preserve existing production usage.
No Phase 6.3 changes, live-state rewrites, test reruns, deployments or messages to
another chat are performed by this documentation-only creation.

## Ownership and remaining work

Phase 6.5 owns conditional Action execution, reviewless admission and connected
Step/Release FIX. Phase 9 owns enhanced convergence/provider routing and shared
hard-stop application; Phase 10 owns CBD integration; Phase 7 owns related-project
management and development artifact overlays. Plans are not implementation
evidence. All P65 checklist items start unchecked.

## Scala lint refinement — adopted 2026-10-20

The user accepted a minimal Scala lint delivery inside Q3 instead of leaving
the whole feature OFF until an unspecified later producer provides it. This
updates the original availability-only policy without changing the Q1 -> Q2 ->
Q3 -> Q4 order or active 6.4 execution.

Read the [minimum delivery contract](../../../phase/phase-6.5.md#minimal-scala-lint--adopted-2026-10-20).
P65-08 now covers rule selection and Scala 3.3.8 connection; new P65-15 covers
actual detection, accepted valid code, FIX/admission and truthful result/scope
handling. Other checklist IDs and pending status are retained.

Observed reference: sibling CNCF `build.sbt` provides `fixCheck` using
`scalafixAll --check`; `.scalafix.conf` selects `DisableSyntax` with `noVars` and
`noNulls` false. This limited setup is not an accepted shared rule catalog or
validation result for sm-workflow-dev. Syntactic rules need no SemanticDB or
pre-compilation according to the [Scalafix rule documentation](https://scalacenter.github.io/scalafix/docs/rules/overview.html).
Reuse the tool approach, verify actual project compatibility, and avoid adding a
compile solely for syntax checks.

Rules must express existing implementation conventions, with small explicit
source scopes and blocking/warning dispositions. Avoid blanket syntax bans,
style cleanup, duplicate compiler/CAR checks or a new parsing engine. Check
changed files in their syntax context and attribute findings to affected code;
separate unrelated existing findings. Treat generated code, tests and runtime
infrastructure according to their roles. Keep inspection separate from source
rewriting, and reuse valid Step results at Release.

No Scala lint implementation, build configuration, active Session or previous
validation result is changed by this planning update.

## User-decided lint exceptions — adopted 2026-10-20

The user requested that Q3 also support fixing a lint finding by retaining the
intentional implementation and adding a standard suppression following the user's
decision. This is a normal FIX outcome alongside implementation correction.

Use `@SuppressWarnings` with a specific `scalafix:` rule ID or a rule-specific
`scalafix:ok` comment and a source reason; use paired off/on comments where a
region is necessary. Recheck lint and perform affected validation, then return
to the same Step/Release admission. Keep other rules enabled. A suppression-only
change can reuse still-applicable focused-test results; changed annotations and
behavior receive the relevant compile/test checks.

Record correction and accepted intentional exception separately in the existing
finding/decision/repair model. Preserve original findings, decision attribution,
rule/source scope, reasons and actual results without a parallel exception ledger.
Scalafix applies the suppression; it does not assess the reason's business validity.
Reuse existing user decisions without another approval or extra AI review; do not
require a Human decision for ordinary lint repairs. Outstanding meaning judgments
belong in the already planned review. History and repair counters are retained.

The [plan contract](../../../phase/phase-6.5.md#intentional-exceptions-as-a-normal-fix-outcome)
and new P65-16 acceptance cover the selected suppression, continued detection of
an unrelated unsuppressed violation and return to admission. Existing checklist
IDs and pending status remain. This is a plan update only; active 6.4 and existing
implementation/validation results are unchanged.

## Conditional triage refinement — 2026-10-21

The user adopted deterministic assessment with AI only when semantic uncertainty
remains. The [latest handoff](2026-10-21-phase-65-deterministic-assessment-handoff.md)
and [specification](../../../spec/goal-phase-step-assessment.md) supersede any
interpretation that a cheap AI detector is mandatory for all Steps. Passed
conditions and no unresolved signal lead directly to admission/commit. Known
failures use FIX; missing/uncertain required checks use the execution/observation
route. Semantic uncertainty selects triage; planned required detailed review
bypasses triage. Lint clean alone does not suppress boundary/change signals.

Runtime records branch reasons through common Journal without AI transcription.
Triage observations are not confirmed review Findings. Existing P65 identities,
flags, lint-exception policy, Phase 9/10 ownership and all current execution
history/results remain. P65-17..20 add routing/recording acceptance, all pending.
