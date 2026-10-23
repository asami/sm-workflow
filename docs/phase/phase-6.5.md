# Phase 6.5: Conditional Actions and Workflow Quality Optimization

status=planned
planned_at=2026-10-20
depends_on=phase-6.4.md
checklist=[Phase 6.5 Checklist](phase-6.5-checklist.md)
spec=[Step assessment and conditional escalation](../spec/goal-phase-step-assessment.md)
cross_workflow_spec=[Workflow-specific semantic gaps](../spec/workflow-semantic-gap-routing.md)

Owner: sm-workflow. Predecessor: [6.4](phase-6.4.md). Successor: [7](phase-7.md).
Adoption and development instructions: [handoff journal](../journal/2026/10/2026-10-20-phase-65-workflow-optimization-handoff.md).

## Goal and preservation boundary

Reduce AI cost and review-induced scope growth by making deterministic checks
the normal Step admission route. Keep semantic judgment available as a planned
conditional Action and concentrate integrated business/meaning review at Release.
Both Step and Release must have a usable FIX and return-to-admission route.

Step is cheap early warning; Release is one integrated semantic review. A Step
satisfies its own acceptance and subsequent prerequisites without proving the
whole Plan's semantics. Release checks the integrated Plan/Usage Contract,
Given/When/Then and implementation correspondence in that single review, reusing
existing results. See the [handoff addendum](../journal/2026/10/2026-10-21-phase-65-three-workflow-optimization-handoff.md#追記-step早期警告release単一統合レビュー).

Adopted 2026-10-21: runtime deterministic assessment accepts an ordinary Step
without AI when its declared conditions are met and no unresolved semantic signal
requires escalation. AI Bad Smell Triage is a conditional second assessment,
never an all-Step gate. See the [latest handoff](../journal/2026/10/2026-10-21-phase-65-deterministic-assessment-handoff.md).

Further adopted 2026-10-21: extend this principle to RepositorySync's formal
reviewless merge admission and SplitPhase's unknown-only semantic work/context
reuse. Establish GoalPhase first, then RepositorySync, then SplitPhase, before
integrated delivery. See the [cross-workflow specification](../spec/workflow-semantic-gap-routing.md)
and [combined handoff](../journal/2026/10/2026-10-21-phase-65-three-workflow-optimization-handoff.md).
LLM evaluation is outside scope. Luna adoption is optional where an existing
supported route suffices; unsuccessful or unavailable Luna adoption does not
prevent Phase acceptance with an adequate existing profile.

Complete active Phase 6.4 with its existing workflow first. This plan does not
change its Session, Goal, accepted Steps, pending decisions, counters, validation
results or source changes. It does not reopen Phase 6.3 or alter its plan and
aggregate acceptance. Plan creation is not implementation or execution acceptance.
The workflow used to develop 6.5 keeps its admitted policy until a separately
accepted deployment can serve subsequent executions; do not rewrite a live
execution merely to demonstrate the new behavior.

Reuse the existing GoalPhase progression, validation, Admission, FIX, native
commit, journal and skill interfaces. Do not add a second workflow engine,
mandatory planning review, per-Action AI gate or compatibility implementation.

## Action selection and Step admission

PLAN selects the Step kind and applicable Actions using small explicit defaults,
kind profiles and explicit per-Step overrides. The runtime executes that selection;
it does not ask AI on every Step whether review is needed. Step and Release use
the same Action selection model with their own scopes and return points.

| Action | Ordinary code Step | Release |
| --- | --- | --- |
| Compile | Required; normal compilation of the affected build/test configurations | Reuse applicable compile evidence and required integrated validation |
| Focused test | Required; actual selected behavioral tests must execute and pass | Rerun for affected repairs; reuse other applicable results |
| Scala lint | Q3 delivers a minimal Scalafix syntactic-rule profile; adopted applicable rules are ON after Scala 3.3.8 connection acceptance | Apply the adopted profile to the release delta and reuse applicable Step results |
| CAR lint | Selected for applicable CAR scope; reuse unaffected applicable results | Selected integrated CAR conformance |
| Deterministic assessment | Runtime evaluates required results and PLAN-selected signals; admit directly, FIX, or route unresolved meaning | Reuse accepted Step outcomes and select actual remaining obligations |
| AI Bad Smell Triage / Semantic Inspection | One conditional Action when checks pass but a signal needs judgment before continuation; CLEAN returns to admission, SUSPICIOUS/DECLINE to detailed review | Not a mandatory pre-review Action |
| AI detailed semantic review | Conditional on triage escalation or explicit PLAN requirement; a planned required review bypasses triage | One integrated Plan/Usage Contract, Given/When/Then, implementation and architecture review using existing results |
| CBD review | OFF pending Phase 10 | OFF pending Phase 10 |
| FIX | Conditional on actionable failed checks or blocking review findings | Conditional on actionable failed checks or blocking review findings |
| Commit / Final | Formal Step admission then native Step commit | Final reuses accepted results, then native release closure |

Compilation contributes structural/type assurance; it does not replace focused
behavioral tests or prove every business requirement. Normal incremental
compilation is sufficient for its declared scope; no routine clean build.
For code changes, an empty selection or compile-only success cannot count as
focused-test success. Documentation-only Steps explicitly select relevant checks
without inventing Scala execution or test success.

Distinguish OFF, enabled-but-condition-not-met, and actual execution results.
An enabled Action that fails or is unavailable must not silently become OFF.
Future features may be represented as OFF without implementing their execution.
No exhaustive risk matrix is required; expand profiles from actual use.

Reviewless Step admission is a first-class completion record: selected policy,
actual check results and their applicable scope, review disposition, and commit
result. Review OFF is not a fabricated review PASS, empty review evidence or an
AI waiver. A skipped triage is not a fictional AI CLEAN, and actual triage CLEAN
is not detailed-review acceptance. Update the current required-review contract and connected callers
together. Reuse compiler, focused-test and previous review assurance; do not ask
another reviewer to reconfirm mechanically established or settled propositions.

The [assessment contract](../spec/goal-phase-step-assessment.md) defines routing
precedence: missing/uncertain required validation uses its execution/observation
route; known in-boundary failure uses FIX; required planned review goes directly
to detailed review; passed conditions without unresolved immediate obligations admit
without AI; uncertainty requiring judgment before continuation invokes triage. Lint-clean changes may
still trigger selected size, dependency/Component boundary, public-contract or
structural-complexity signals. Signals are attributable observations, not automatic
defects. Compare them with planned scope and settled decisions. No universal
size threshold, exhaustive rule matrix or AI call to select the next AI call.

PLAN-selected Step kind, acceptance and dependencies determine immediate versus
Release treatment. Failed/missing required checks, mandatory review and an
unresolved guarantee needed by subsequent work must be handled at the Step.
Planned later integration work and eligible nonblocking observations may wait
for Release, retaining source/question/reason/planned work in existing Journal
records. Deferral is not CLEAN, a confirmed Finding or closure. If continuation
depends on a judgment whose deferral cannot be established, resolve it through
the appropriate bounded route; do not add per-Step AI/Human permission rituals.

Q3 may use existing explicit contract/Operation/feature/scenario mappings for
structural Given/When/Then early warning. CAR lint owns supported structural
contract checks; workflow consumes results and small declared-data joins.
Unsupported optional detectors remain OFF, not a producer-delivery prerequisite.
Incomplete metadata is not proof of missing scenarios, and unchanged tests may
already cover changed behavior. Required enabled checks cannot be downgraded to
OFF. No prose-analysis engine or exhaustive new test-coverage map is required.

Runtime records actual assessment inputs/references, selected rule, branch reason
and omitted/executed Actions through common Journal. Retain detailed payloads via
Evidence when available; do not add a clerical AI task or separate decision ledger.

## Main route first

| Order / Step | Work | Exit evidence |
| --- | --- | --- |
| Q1: ordinary Step admission | Define minimal typed Action policy and deterministic assessment; connect checks and signals to admission/native commit, with PLAN-owned immediate versus Release treatment. | One local code Step commits with zero AI calls; eligible later observations retain their unresolved meaning while immediate obligations cannot be deferred. |
| Q2: Step and Release FIX | Reuse existing correction mechanisms; connect failed Step checks and Release findings to repair, affected validation and the original admission/closure point. | A failing Step recovers to commit, and a Release finding is repaired after closed Steps and reaches Final/local release without recreating those Steps. |
| Q3: conditional branches, minimal Scala lint and skill alignment | Connect minimal Scalafix on Scala 3.3.8, available structural scenario signals, conditional triage/direct planned review and affected skill contracts. | Lint findings use FIX; immediate semantic uncertainty selects triage; planned review bypasses triage. Partial metadata does not prove absent coverage; unsupported optional detectors stay OFF. CBD remains OFF. |
| Q3-R: RepositorySync main route, then exceptions | Connect conflict-free pending merge and actual checks/assessment to first-class merge admission, native commit and authorized convergence. Then connect known-check-failure FIX and conditional/required review. | A clean diverged merge completes with zero AI calls; a failed check repairs without a fabricated review finding; semantic uncertainty escalates and AI-authored conflict resolution receives required independent review. |
| Q3-S: SplitPhase reuse, then bounded semantic work | Preserve deterministic partitioning and establish known-input/no-AI and retained-answer reuse first. Reduce overlapping context; bundle only already-known independent gaps when justified. | Actual complete-input and saved-answer routes progress without new semantic calls; bounded unknown work preserves known inputs and returns through deterministic admission/proposal selection. |
| Q4: integrated acceptance and delivery | Validate actual installed/client progression across the three workflows; one integrated Phase/Release review consumes accepted results and deferred observations; deliver to Phase 7. | Normal/repair routes and semantic correspondence accepted; only affected re-review after FIX, no repeated review at Final, no model-comparison gate. |

Build the minimum policy needed for Q1 in Q1; broader conditional branches belong
to Q3. This ordering must not defer the actual working route until a generic
framework, complete lint catalog or future CBD integration has been constructed.
Normal admission must not wait for a cheap-model trial or universal Bad Smell
coverage. Q3-R and Q3-S extend the existing Q3-to-Q4 interval; existing Q1–Q4
identities and prior evidence remain. Do not finish a generic cross-workflow
abstraction before any workflow's main route works.

## RepositorySync and SplitPhase delivery boundaries

Q3-R reuses the current Git progression and its actual pending candidate. Formal
merge admission is distinct from ReviewAccepted and from the non-merge
IntegrationValidated shortcut. Update admission/commit/convergence consumers
together; preserve parent/tree and effect-authority checks at their existing owner.
Applicable selected validation remains required; non-Scala/non-CAR scopes declare
non-applicability. Known failed checks enter bounded FIX without requiring AI to
invent a review finding. Missing/uncertain results retain their observation route.

Use existing change/metadata/lint facts to select merge uncertainty; accepted
parent changes and old findings are not automatically new blockers. Clean merges
need no semantic call. Explicit required review bypasses triage. AI-authored
conflict resolution retains independent Detailed Review in 6.5. Repaired candidates
reuse applicable results; re-review findings, repair delta and affected meaning,
not the entire history. Do not recreate a Git engine or duplicate baseline builds.

Q3-S reuses the existing SemanticRequired, scope/reasoning contracts and native
saved results. Preserve deterministic calibration/enumeration/ranking and final
partition selection; AI supplies only the issued unknowns. Prior answers remain
reusable at their admitted scope. Normal enrichment needs no second AI review;
mechanical admission proves its declared constraints, not factual accuracy of
every estimate. Information gaps, capability limitations, Human choices and
execution failures remain distinct. Do not reinterpret DecisionNeeded as generic
provider decline. Safe same-frontier bundling is optional; later dependent gaps
must not be invented early to reduce call count.

Shorten semantic worker inputs using bounded source/results and existing context
references while retaining host/runtime controls at their owner. Common Journal
records actual routing and reuse. No separate ledger or AI clerical step is added.

## Model policy: opportunistic Luna, no LLM evaluation

LLM evaluation, model comparisons, benchmark corpora, Local LLM deployment and
provider accuracy/performance scoring are outside 6.5. Future comparison and
enhanced switching remain Phase 9 concerns; optional Local LLM reference work
does not become a Phase 9 gate either.

Adopt Luna for suitable bounded tasks only through available supported profile
configuration and ordinary workflow acceptance. If Luna is unavailable or cannot
handle the contract, retain/use an existing adequate profile and proceed. Do not
create a retry/benchmark campaign, add a routing engine or require a successful
Luna conversion to complete 6.5. Record actual selected profile/outcome when
available; unchanged-candidate retries do not erase previous results or counters.

## Minimal Scala lint — adopted 2026-10-20

Q3 owns a working minimal Scala lint integration, not just an optional hook for a
future producer. Reuse Scalafix and existing build/task conventions rather than
create a private parser, analysis engine or AI lint service. CNCF already has a
Scalafix `fixCheck` entry and a limited `DisableSyntax` configuration; their
presence is reference evidence, not proof that the required rules or Scala 3.3.8
connection work in this checkout. Select and verify the actual tool/configuration
against this project's Scala version during Q3; do not blindly copy a version or
enable SemanticDB/extra compilation merely for syntactic checks.

Separate responsibilities:

| Mechanism | Owned assurance |
| --- | --- |
| Compiler | Types, syntax and existing compiler diagnostics |
| Scala lint | Mechanically decidable violations of adopted Scala implementation conventions beyond compiler checks |
| CAR lint | Component structure and CNCF/Textus usage contracts |
| Focused tests | Changed behavioral contracts |
| AI review | Residual business/design meaning |

Select a few useful rules from the repository's existing Scala conventions and
record each rule's ID, rationale, applicable source scope and blocking/warning
disposition. Do not invent blanket `var`, `null` or `throw` bans, stylistic
preferences or speculative improvements. Distinguish handwritten product code,
generated code, tests and framework/infrastructure code; a rule's applicability
comes from the convention and role. Existing required compiler diagnostics stay
in force, but are not duplicated by a new lint rule. CNCF-specific boundary rules
stay with CAR lint; do not implement a second boundary scanner in Scala lint.

Start with syntax-based checks. Inspect the changed Scala files with their full
syntax context and attribute findings to the changed scope; do not treat raw
line matching as semantic proof. Report pre-existing unrelated findings separately
without requiring repository-wide cleanup. If a selected tool cannot distinguish
new/affected findings directly, make that limited attribution explicit rather
than invent a second analysis service or mark all old findings as blockers.
Release checks cover its cumulative delta and reuse applicable Step results.

The lint Action checks and reports without modifying sources. Automatic rewriting,
if subsequently selected, is a separate repair operation with normal validation.
Return actual rule/source location/disposition and execution outcome through the
existing validation/FIX contract. An empty effective rule set is not completed
lint delivery. Prove a selected violation is found, valid code is accepted, a
repair returns to Step admission, and failed/unavailable execution stays distinct
from OFF. Prefer a small representative acceptance over repeated full scans.

Before Q3 delivery the new Action can be explicitly OFF while Q1/Q2 establish
the main route. After acceptance, the adopted applicable profile is ON for code
Steps and Release. Additional future rules remain OFF until selected. Tool
unavailability is an unresolved Q3 delivery issue, not grounds to close 6.5 with
the entire Scala lint Action OFF. Full rule-catalog coverage, whole-repository
remediation and richer semantic analysis are outside this Phase.

### Intentional exceptions as a normal FIX outcome

A Scala lint finding has two normal repair outcomes: correct the implementation,
or retain an intentional implementation and apply a scoped suppression based on
the user's decision. The latter is accepted exception handling, not disabling
the lint Action or claiming that the original implementation was changed.

Use standard Scalafix `@SuppressWarnings(Array("scalafix:<rule-id>"))` or a
rule-specific `scalafix:ok` comment, with the reason in source. Use paired
`scalafix:off` / `scalafix:on` only where a region is needed. Target the relevant
rule and smallest practical scope; other rules and unrelated code remain checked.
The [standard suppression contract](https://scalacenter.github.io/scalafix/docs/users/suppression.html)
provides the mechanism; no private annotation or exception registry is needed.

The normal route is finding -> user decision that the implementation is
intentional -> suppression and reason -> actual lint recheck -> applicable
validation -> the same Step/Release admission. Record implementation correction
and accepted intentional exception as distinct dispositions in the existing
finding/decision/repair record. Retain the original finding, rule/source scope,
decision attribution and reason, and the actual recheck result. A successful
recheck establishes effective suppression and the remaining checks, not proof
that Scalafix judged the reason correct.

Reuse an already applicable user decision without reapproval or an extra AI
review. Do not introduce a Human question for every lint finding; ordinary
implementation fixes continue under standing authority. Only a genuinely missing
decision uses the existing decision route. Any remaining semantic judgment can
be batched into the planned Step/Release review, without reopening settled intent.

The suppression change follows normal affected validation. Recheck lint; compile
changed annotations as needed and run focused tests when behavior is affected,
while retaining still-applicable focused-test evidence for a suppression-only
change. Suppression does not waive compile/test failures or reset repair history
and counts. Q3 acceptance must prove a selected intentional finding is suppressed,
an unrelated unsuppressed violation is still reported, and the workflow returns
to admission with the exception disposition and existing user decision retained.

## FIX contract

Step: failed focused test/applicable lint or a selected review finding -> one
bounded repair batch -> affected validation -> necessary scoped re-review only
when semantic judgment remains -> Step admission -> commit.

Release: integrated checks/review find a blocking issue -> repair the release
delta -> affected focused/integration checks and necessary scoped re-review ->
Release admission -> Final -> release commit. Closed Step commits and acceptance
history remain intact. Do not manufacture an individual Step/review ceremony for
each finding, or automatically turn an in-scope Release repair into an amendment
requiring fresh user approval. The adopted Plan covers ordinary repairs within
its accepted scope and authority; actual requirement/authority expansion uses the
existing decision route. Speculative improvements remain nonblocking candidates.

Batch related findings. Preserve unaffected validation and reviews, and rerun
focused behavioral tests affected by code repairs. CAR lint is not a mandatory
probe on every inner repair iteration. A previously reviewed condition settled
by the repair and selected checks needs no extra AI review; outstanding meaning
changes require only the relevant judgment. Phase Review integrates the result;
Final consumes existing acceptance rather than repeating tests or reviews.
After repairs, reassess only changed results/signals and reuse still-applicable
triage/review decisions. Do not repeat triage on the same unchanged candidate or
reopen settled lint exceptions without new relevant evidence. An actual triage
execution failure is not a semantic DECLINE; missing validation is never resolved
by AI CLEAN. Smell observations alone grant no FIX authority.

Retain the current repair limits, counting-window rules, unresolved findings and
history. Changing Action, executor or provider, retrying preparation, and saving
an intermediate Release repair commit do not create a fresh repair budget. This
Phase does not add or relax a budget or claim an unimplemented convergence gate.
[Phase 9](phase-9.md) remains the owner of observed convergence, provider routing
and the shared absolute ten-automatic-FIX backstop. Its eventual application must
cover both Step and Release FIX. Phase 6.5 does not wait for Phase 9 or implement
its new observation/routing mechanism in advance.

## Validation and completion

Release uses one integrated review, supplied by the existing Phase Review where
applicable. Usage Contract <-> Given/When/Then <-> implementation alignment,
cross-Step integration, architecture, duplication and unnecessary complexity are
review perspectives in that one activity. They are not separate mandatory AI
gates. Use existing results and inspect residual meaning; do not re-prove every
accepted Step. Consume deferred observations and actual later resolutions, and
do not carry unresolved required acceptance beyond release. FIX re-review stays
within findings, repair delta and affected meaning; Final reuses acceptance.

The same review/result batches nonblocking dispositions and feedback. If Release
detection was sufficient, add no Step cost. If early detection was needed, record
a bounded improvement candidate for the cheapest supported mechanism; do not
automatically add a rule, per-finding AI retrospective, analysis ledger or new
6.5 obligation. CBD remains OFF even at Release. This policy does not add a
Release review to RepositorySync or SplitPhase where their own contracts have none.

Use focused behavioral checks for changed progression, admission, repair and
skill contracts. At closure, use the repository's applicable full validation
policy once with retained-result reuse; HEAVY runs only when explicitly selected.
Do not repeatedly run whole-repository tests or reviews after each local fix.

Accept this Phase only when the [checklist](phase-6.5-checklist.md) has actual
evidence for normal reviewless Step commit, selected semantic review, the adopted
Scala lint profile running on Scala 3.3.8 including user-decided scoped exceptions,
deterministic no-AI admission, lint-clean signal escalation, triage outcomes and
direct planned review, Step FIX, Release FIX after closed Steps,
RepositorySync's reviewless actual merge and failure/review branches,
SplitPhase's no-AI known-input path and retained/unknown-only semantic routes,
and integrated Final/local release. A unit-only
simulation does not replace the actual workflow/client connection. Reuse existing
runtime/producer guarantees; do not revalidate CNCF or Cozy internals.
Acceptance also covers honest deferred-observation retention/consumption,
immediate-obligation handling, limited metadata signals and one integrated review
of semantic correspondence with no separate Final review. Existing IDs/results
remain; new checks supplement rather than invalidate earlier acceptance.

Record available Step-review dispatch counts, repair/re-review activity and
meaningful Release findings against the retained 6.4 baseline. Record AI cost or
duration when actually available; otherwise state unknown. Do not add a benchmark
subsystem or speculative saving threshold. A finding efficiently resolved at
Release is not by itself a reason to restore mandatory per-Step review.
Include skipped/actual triage calls, escalation/DECLINE and dismissed signals
where observable, and distinguish important Release misses from harmless deferred
findings. Cost reduction means avoiding unnecessary calls, not merely replacing
each detailed review with a cheaper mandatory call.
These are observations of ordinary workflow acceptance, not an LLM evaluation or
model-comparison activity. Successful Luna adoption is not a completion condition.

Phase 7 consumes this accepted lower-cost workflow plus 6.4 evidence storage.
Phase 9 convergence and Phase 10 CBD integration stay separately owned. Existing
SplitPhase/RepositorySync production eligibility and GoalPhase's single-repository
use boundary remain; this Phase does not implement workspace/artifact overlays.
