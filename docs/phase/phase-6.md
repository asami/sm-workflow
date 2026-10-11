# Phase 6: Abstract Thinking Modes, Skill Integration and Execution Requirements

Status: planned
Planned: 2026-10-03
Depends on: Phase 5 including Phase 5.1, CNCF Phase 80 minimum execution-requirement extension

Consume the Phase 5.1 session-resolved Operation contract: the
client supplies session context and actual required parameters/results, not
copies of Workflow's internal revision/snapshot/issued envelope. This runtime
boundary does not predefine thinking modes or task-specific skill semantics;
those and actual CLI/skill wiring remain Phase 6 work. Integration must not
reintroduce unnecessary copy/unchanged-content validation.

The owning user's 2026-10-11 normal-use limit also carries into integration:
duplicate/competing answers are caller responsibility; intermediate exclusion,
unique winners, complete-history retention, identical replay and intermediate
recovery are not promised. Use normal Operation calls and a new execution from
the beginning after abandonment. Do not rebuild removed checks in a skill or
client, and do not automatically add new guarantees or review findings to scope.

## Execution plan — 2026-10-07

Reuse the accepted Phase 5 runtime and Phase 2 workflow. The first skill-connected
driver is a bounded GoalPhase development task with implementation, independent
review and actual completion. Preserve the requirements below; this is work
ordering, not a new compatibility layer or a reduction to skill text alone.

The 2026-10-07 user clarification places command/CLI wiring here together with
skill integration. Phase 2 functionally tests public Operations by direct calls;
it does not launch a command process per test. Phase 6 connects the command
invocation/result boundary to those same Operation implementations, then verifies
actual skill use. Do not duplicate Workflow logic in the command or skill layer.

### A. Deliver one complete mode/skill/client/result contract

1. Fix a finite table for the selected thinking modes: task purpose, supplied
   context, expected result and the workflow operation consuming that result.
   Include the required abstract analysis/design distinction and implementation/
   review uses. Choose concrete public names/payloads here, together with existing
   supported callers and saved-request handling; avoid a vocabulary-only phase.
2. Implement the selected skills, ordinary client invocation/completion and
   corresponding runtime bindings together. First prove issued request -> actual
   skill execution -> typed result -> normal workflow continuation. Then complete
   the driver through independent review and its real outcome. Design connected
   executable specs at the same time; do not leave client installation until last.
3. Reuse CNCF ExecutionRequirement/Evidence for placement, independence and
   provider matching. Connect one supported unavailable-provider fallback and
   the already-required non-identical participant case, using Human where
   applicable. Test the generic contract without implementing every remote worker,
   local-model or OpenClaw integration. No new provider framework is a prerequisite.

Observable result: the chosen development workflow can be invoked through its
real skill/client and completed. Mode/result mismatches and prohibited reviewer
identity reuse fail at the existing typed boundary. This internal milestone
does not bypass the remaining Phase 6 acceptance before gradual daily use.

### B. Reuse that interface for validation planning, FIX and improvement

Implement AI-authored metadata/Slice planning, TEST_FIX, REVIEW_FIX, semantic
change classification, Issue dispositions and convergence hints on the same
request/result route. Connect the existing human-selected warning/improvement
path to an actual proposal and adopted revision. Developer-requested replanning
uses that path; it is not another engine or another mandatory acceptance gate.

Implement the repair-count reset contract in this batch, including the runtime
decision/guard and Step-close transitions as well as their skill/client
connection. Explicit user continuation approval resets both active counts;
accepted Step completion with its commit makes the next Step start at zero.
Retain history and convergence trends, and remove per-finding reapproval caused
solely by the previous window's exhausted counts. P6-TS05 tracks this required
delivery. It is not deferred to Phase 9 or satisfied by skill wording alone.

Use one shared driver with representative test-failure, review-finding,
validation-design-gap and human-selected improvement branches. Exercise each
branch's distinct semantics, reusing the Phase 5 runtime tests for policy
permutations. Do not repeat every threshold/Issue-state case through live AI.
Keep an actual connected invocation/result example; scripted results alone do
not establish skill integration, and AI assertions never replace runtime evidence.

Existing coverage is P6-TS01..06. AI does not choose official tests per execution,
reset counters, infer approval from a warning or change the adopted plan by itself.
No additional instrumentation is required just to improve an explanatory hint.

### C. Integrated acceptance, usable installation and gradual use

Validate the selected modes, ordinary client, independent execution, bounded
fallback, non-identical participant and managed-suite branches together. Perform
the required independent review at this connected boundary; after fixes, rerun
affected interface/behavior checks and required regressions rather than another
complete provider-by-mode matrix. Keep compatibility checks for actual callers.

Complete the existing acceptance below, document installation/configuration and
limitations, and start gradual real use. Do not wait for Phase 7 overlay or an
empty Phase 8 backlog. P2-C02 initial skill creation/connection is delivered here;
broader distribution/provider coverage remains separately owned. Record actual
use-blocking discoveries for early repair and other needs in Phase 8, preserving
their evidence and the accepted contract rather than reopening completed phases.

## Skill integration and start of use (2026-10-06)

Follow the revised [incremental-use plan](README.md). Phase 5 supplies a runtime
foundation validated through CLI/control operations; it does not create or
connect skills. This phase designs abstract thinking modes and the skill-facing
contract, creates/adapts the selected skills and connects them to that foundation
as one coherent delivery. After connected acceptance, start gradual skill-based
real use and continue it through Phase 7. Phase 2's
extended backlog now belongs to [Phase 8](phase-8.md), not an implicit
prerequisite that must be completed before this phase can start.

Keep the planned Phase 6 acceptance scope bounded. Implement newly discovered
missing functionality early when it prevents the supported real-use scenario;
include a nonblocking addition here only when it belongs to this phase and fits
the bounded delivery. Otherwise record it in Phase 8 with the observed use case,
impact, current state and existing evidence. Do not continually expand this
phase to cover every provider, participant or hypothetical fallback. Preserve
Phase 5's removal of unnecessary hash, tamper, unchanged-content and local TTL
checks across all newly supported paths.

Implement connected behavior and its Executable Specifications as a coherent
batch. Use compilation and focused tests as needed during implementation, then
validate and independently review the integrated behavior. A small internal
helper or declaration does not require a separate acceptance/review cycle.
After review fixes, rerun affected specifications and required regressions;
reuse relevant actual results instead of repeating unrelated checks. Completion
requires this phase's selected behavior and required validation/review, not an
empty Phase 8 backlog. Continue real use into [Phase 7](phase-7.md).

## Goal

Deliver the minimum system usable through skills: define abstract thinking modes and the skill-facing request/result contract, implement the selected skills and their runtime connection, and generalize the execution-routing model proven by Phase 5 without making sm-workflow own provider-specific or harness-specific semantics.

Phase 5 proves the minimum runtime foundation. Phase 6 completes the skill-connected development slice and establishes the contract used for further sm-* skills. Existing skill files are reference material to reconcile with this contract, not proof that the interface or integration is already complete. Do not build a provisional skill set in Phase 5 and require compatibility with its accidental interface.

## Abstract thinking modes and the skill interface

Abstract thinking modes affect the work requested from a skill, not just the
provider/model/effort chosen to execute it. Define the supported modes from the
selected development scenario: what question is asked, what context and
constraints are supplied, the expected abstraction level and result structure,
and how Workflow uses the returned result. In particular, distinguish a request
to identify problem structure/constraints from one asking for concrete design or
implementation where those tasks are needed. Settle exact names and typed
payloads with the interface design; do not assume an unimplemented mode exists.

Design the request, semantic result, normal client invocation/completion route
and applicable error behavior together. Keep thinking-mode meaning distinct
from reasoning intensity, INLINE/DELEGATED placement and concrete model effort.
The skill performs the issued semantic task; Workflow owns progression and the
harness owns provider execution. Do not duplicate their responsibilities in the
skill. A returned analysis does not itself authorize a mutation.

Implement the contract, selected skills, runtime connection and connected
Executable Specifications in one delivery. Reconcile existing reference skills
and affected callers together rather than inventing a provisional compatibility
layer. Account for actual supported saved requests if the contract changes;
do not silently reinterpret their meaning. Use the accepted Phase 6 interface
as the baseline for subsequent skill expansion and explicit compatibility tests.

## Boundary

The intended three-layer boundary is:

```text
Skill / application planning
  -> application-specific WorkAssessment / WorkClassification

Workflow application policy
  -> generic logical ExecutionRequirement

Execution Harness
  -> concrete Provider Selection
  -> ExecutionEvidence
```

Input assessment remains application-specific where its meaning is domain-specific. Generic execution requirements and evidence belong to CNCF.

## Scope

The skill integration below is core Phase 6 work, not a Phase 8 distribution
follow-up. Keep the first supported workflow and modes finite; broader provider
coverage must not turn this phase into an unbounded framework project.

Define the selected abstract thinking modes and their skill request/result
semantics; create/adapt and connect the skills needed for the first practical
workflow, with ordinary invocation/completion and documented setup. The
following execution-requirement work supports that delivery:

1. Reconcile Phase 5 WorkClassification with the generic CNCF ExecutionRequirement contract.
2. Stabilize placement semantics such as INLINE / DELEGATED without referring to ChatGPT/Codex parent/child task topology.
3. Stabilize independence semantics and execution-context identity sufficient to prove producer/reviewer separation.
4. Define Requirement -> ExecutionEvidence conformance and Admission behavior.
5. Generalize context-isolation / context-footprint routing beyond the Phase 5 heuristic; evaluate explicit context-budget evidence only if operationally justified.
6. Define provider capability matching and configuration-driven Provider Selection without making provider identity Workflow transition semantics.
7. Define bounded fallback/escalation behavior for unavailable or insufficient providers.
8. Generalize the contract to Human, remote worker, OpenClaw-like worker, local model, and other execution participants where the same semantics apply.
9. Preserve direct control-plane invocation: Workflow commands themselves do not require a child AI task.
10. Record enough execution evidence to evaluate routing quality, independence, admission rate, retries, latency, usage and cost. Preserve dimensions needed for project/provider/machine profiling: semantic work type/classification, reasoning requirement, provider/profile, execution context/machine identity where available, COMPLETED/DECLINED/BLOCKED outcome and typed reason, validation outcome, review outcome/disagreement, escalation path, and elapsed/resource evidence. Do not collapse these observations into one opaque heaviness score.
11. Consume CNCF Phase 101 logical Component resource APIs for runtime state/work resources where execution participants require project-local storage; provider/harness code MUST NOT depend on a hard-coded `.textus/sm-workflow` layout.
12. Keep configuration on CNCF's existing hierarchical Component configuration mechanism; Phase 6 MUST NOT create a second configuration lookup/merge model.
13. Define the minimum Project Acceptance Boundary as project configuration on that same hierarchical configuration mechanism. Combine it with the current Goal/Phase acceptance conditions into an Effective Acceptance Boundary and carry that boundary through implementation, TEST_FIX/REVIEW_FIX, Admission and Review.
14. The Effective Acceptance Boundary limits required quality assurance as well as defining required quality. A provider/reviewer MUST NOT promote an out-of-boundary robustness idea, exceptional-path guarantee or new test into a blocking completion condition merely because it appears safer.
15. Phase 6 implements only boundary definition, configuration, projection and enforcement at the existing request/result/Admission/Review interfaces. Rich convergence interpretation, assurance-overhead classification and boundary-aware provider re-routing belong to Phase 9; do not add a second quality-policy engine here.

## Usage Contract input boundary (2026-10-11)

The development plan is authored and reviewed separately before sm-workflow execution. Its top-down entry is a **Usage Contract**: how a human or software consumer uses the system and the observable behavior/effect expected. It may describe a Use Case, API/Operation, CLI/MCP command, UI action, or event/message; it is more than an interface signature.

Phase 6 consumes the adopted Usage Contract and Project Acceptance Boundary, projecting applicable acceptance conditions into existing WorkOrder, implementation/FIX, Admission and Review. A Development Slice realizes a bounded portion through the necessary implementation layers. Do not create another planning engine, contract registry or configuration mechanism. Providers cannot silently change the adopted Usage Contract or Boundary.

## Managed-suite design, FIX and improvement skills (2026-10-06)

### Acceptance Boundary integration — 2026-10-11

Resolve project defaults through CNCF's existing hierarchical configuration.
Combine them with the explicitly adopted Goal/Phase conditions; an explicit
Goal/Phase override takes precedence for that scope, including an explicit
exclusion of a broader project default. Conditions not overridden retain their
project meaning. Providers and reviewers cannot invent an override. Resolve
conflicting adopted conditions during planning through an explicit owner
decision, rather than silently unioning them into stronger requirements.
This quality-policy boundary does not override actual execution permissions.

Carry the resulting boundary through the selected skill request, implementation,
TEST_FIX/REVIEW_FIX and normal Review/Admission. Within the existing connected
driver, demonstrate that a required failure blocks acceptance, an excluded
assurance proposal does not block or authorize repair, and an explicit
Goal/Phase override is applied consistently at each consuming interface.
Include these outcomes in Phase 6 connected acceptance and independent review;
no separate review cycle or policy engine is required. P6-TS02/03 below in the
linked checklist track these branches. Phase 9 extends this same contract.

Connect the Phase 5 runtime to AI/skills under
[Managed Test Suites and Verification Cost Guard](../notes/managed-test-suites-and-verification-cost-guard.md)
and its [decision journal](../journal/2026/10/2026-10-06-managed-test-suites-cost-feedback.md).
Reuse Phase 5 CNCF Phase 103 discovery/query, RunTestSuite, receipts, warnings and validation
transitions; do not implement another test selector or execution ledger in a skill.

- AI proposes changes to executable tests and their CNCF annotations/Cozy-owned
  CML properties. Changes pass normal Candidate/review/admission; no suite
  metadata registry is created. Slice planning authors the management file with
  acceptance conditions, FOCUSED feature selection, supplemental operation refs
  and typed parameters. Runtime executes that admitted combination. New feature
  declarations, when needed, remain explicit Test Architecture changes. Runtime never asks AI for ad-hoc official test lists.
- Connect TEST_FIX and REVIEW_FIX as evidence-triggered FIX semantic work.
  Both receive the original requirement, current Candidate and relevant evidence;
  TEST_FIX includes purpose/metadata revision and failure diagnostics, REVIEW_FIX includes
  review findings. Both use implementation-like execution routing, not review
  routing merely because a request came from a review. Reconcile their thinking
  modes and payloads with this phase's common request/result interface.
- A fix may identify a production, test, suite-design or requirement problem;
  never define success as merely satisfying a failed assertion. Return the revised
  Candidate or explicit design gap. Runtime owns revalidation and admission:
  SMOKE -> the same Slice FOCUSED, then review/admission as required. The skill
  cannot announce validation success, broaden official scope or change state.
- A human-selected duration warning forms one bounded improvement request using
  the suite, receipts, warning and relevant context. AI may propose no change
  with a reason, revised metadata, test-code changes or both. Review/admit those
  source changes normally, including coverage deltas and CNCF interpretation. Compare subsequent measured
  results before claiming improvement; warning alone never starts this work.
- Each Fix returns semantic change depth, logical units/cross-file coupling,
  a minorBugFix classification with rationale, and convergence assessment/hints.
  Keep physical metrics separate from AI classification. History/GWT comment
  hygiene and small bug corrections are exempt only from the ordinary repair
  count under Phase 5 policy; their results still affect convergence judgment
  and every batch consumes an automatic cycle toward the absolute hard limit;
  feature work, major restructuring and mixed substantive fixes are not made
  exempt by a convenient label. PROGRESSING/STALLED/REGRESSING remain advisory;
  explicit BLOCKED stops automatic cycling through runtime decision handling.
  Explain progress, remaining issues or needed decisions using bounded existing
  evidence; do not require extra scans/tests to enrich every hint. Future feedback
  refinements are possible without expanding the current collection requirement.
- Fix skills account for every assigned issue ID with FIXED, UNRESOLVED,
  DECLINED or BLOCKED; new findings enter separately through evidence intake.
  FIXED is a claim, not validation. UNRESOLVED can explain partial progress with
  bounded existing evidence. Reviewers retain supplied identities where supported
  and return explicit coverage/resolution evidence; absence from a narrowed
  review never closes an issue. Runtime owns reconciliation and guard decisions.
  Exercise intermediate narrowing within policy while preserving affected scope
  and required final review/SMOKE/FOCUSED/ADMISSION. No fuzzy identity matching or
  extra per-issue review cycle is introduced.

Developer-requested plan reconstruction is another use of the existing
human-selected improvement path and thinking-mode/skill interface. A developer
uses the Phase 5 cost/convergence/issue indicators to decide whether to ask AI
to investigate the plan. AI receives current acceptance conditions, unresolved
issues, recent observations and known dependencies as bounded existing context,
and proposes work ordering, batch or scope-allocation changes with reasons.
The developer chooses the plan; runtime continues with consistently updated
completion conditions and validation planning, retaining history, deferred-item
identities, counters and applicable evidence. Indicators alone never request
replanning or alter scope. This usage adds no separate replanning engine or
acceptance gate and does not bypass existing guard decisions or cycle limits.
See [indicator use for replanning](../notes/managed-test-suites-and-verification-cost-guard.md#using-indicators-for-developer-requested-replanning-2026-10-07).

Connected acceptance must demonstrate the Phase 6 items in the
[managed-suite checklist](managed-test-suites-phase-checklist.md): planning-time
feature selection, both FIX paths, a validation-design gap and one actual
human-selected warning -> AI proposal -> admitted revision -> later observation
cycle. Duration warnings and provider ERROR/TIMEOUT must not turn into automatic
TEST_FIX or repair authorization. Keep ordinary execution deterministic and
bounded; no autonomous optimizer, copied full logs or management hashes.
Validate the connected convergence-assessment/guard path, including minor-count
exclusions, the absolute 10-cycle backstop including hygiene/minor-only repetition,
and AI BLOCKED. Runtime owns both counts and decisions;
AI does not reset history or turn its own optimism into continuation authority.
Also validate connected issue accounting, rejected unsolicited FIXED claims,
partial-progress reports, evidence-confirmed resolution/reopening and review
scope preservation using the Phase 5 runtime path.

## Workflow responsibility boundary

Consume the [explicit continuation and Step-completion reset contract](../notes/managed-test-suites-and-verification-cost-guard.md#explicit-continuation-approval-and-step-completion--2026-10-11).
User approval to continue stopped repairs resets both active counts once while
retaining history and convergence trends. Accepted Step completion/commit closes
that Step's loop; the next Step starts at zero. Checkpoint/WIP/within-Step repair
commits do not reset an unfinished loop. Connect this behavior through the
existing decision and Step-close routes, without per-finding reapproval.
Phase 6 owns implementation and connected acceptance of this contract. Reuse
existing runtime support where present and implement missing transitions here;
do not assume the Phase 5 foundation already supplies the reset behavior.
Phase 9 consumes the accepted contract when adding provider re-routing.

The generic execution contract MUST preserve the software-development responsibility split proven by sm-workflow: GoalPhase may close and locally commit one admitted repository/worktree, while RepositorySync may orchestrate synchronization/convergence across the Project Workspace. ExecutionRequirement generalization MUST NOT merge these application responsibilities into one generic closing operation.

Phase 7's [Development Artifact Overlay](../spec/development-artifact-overlay.md)
uses this same skill/client boundary: native Operations and execution adapters
own publication and dependency selection. Skills must not acquire an alternate
artifact lifecycle or copied JSON state machine. The overlay implementation is
Phase 7 work and does not become a prerequisite for Phase 6's first usable skills.

## Genericization rule

Do not move software-development classifications such as TRIVIAL or PROGRAMMING / ENGINEERING into CNCF merely because Phase 5 uses them. Generalize only the resolved execution concepts that are meaningful across applications.

## Executable Specification direction

Demonstrate at least:

- application-specific assessments mapping to the same generic execution requirement;
- INLINE execution by the current participant;
- DELEGATED execution by another provider;
- REQUIRED independence rejecting completion that reuses a prohibited producer execution identity;
- provider replacement without Workflow definition change;
- provider unavailability/fallback without changing semantic WorkOrder identity;
- equivalent generic behavior for an AI worker and at least one non-identical participant class;
- ExecutionEvidence sufficient for requirement conformance and later audit;
- DECLINED preserved as a normal attributable provider-routing outcome distinct from semantic failure/BLOCKED;
- evidence can be aggregated by Project x Provider/Machine to derive local decline rate, validated local completion rate, escalation rate, and review disagreement without changing Workflow semantics.

Also demonstrate the actual skill-connected route: an issued request carries
the selected thinking mode and relevant context; the skill supplies the declared
semantic result; Workflow accepts it and proceeds to the real scenario outcome.
Cover supported modes with examples of their expected abstraction/result shape,
and reject incompatible request/result combinations at the appropriate boundary.
Changing concrete provider mapping must not silently change the requested mode
or its semantic result contract. CLI-only tests and standalone skill text are
insufficient evidence of this connection.

## Practical completion and start of use

- The selected abstract thinking modes and request/result contract are implemented.
- Required skills are created/adapted, available in the intended execution
  environment and connected through the normal client to the runtime.
- The selected development workflow works from skill invocation through result
  submission and completion, with required execution placement and independent
  review; setup and limitations are documented.
- Connected Executable Specifications and required checks pass, and independent
  review has no unresolved blockers for this practical scope.
- The connected driver demonstrates Effective Acceptance Boundary enforcement:
  required failures block, excluded assurance proposals do not, and explicit
  adopted Goal/Phase overrides are consistent across request/FIX/Review/Admission.
- The managed-suite design/FIX/improvement connection above passes its assigned
  acceptance, using the Phase 5 runtime and the same public skill interface.
- P6-TS05 proves runtime and normal client behavior for exhaustion -> explicit
  continuation approval -> both counts zero -> multiple subsequent repair
  batches without per-finding reapproval, retaining history/convergence trends.
  Accepted Step completion/commit -> next Step counts zero is also verified;
  a checkpoint/WIP/within-Step repair commit leaves active counts intact.
  Reset implementation and this connected acceptance are required to close
  Phase 6, before gradual real use begins.
- Phase 5's removal of unnecessary checks remains effective on the connected
  route. Record other needs in Phase 8 without delaying use for optional breadth.

After this acceptance, begin gradual real use. Continue it during Phase 7;
address use-blocking gaps early and finish necessary carryover in Phase 8.

## Non-goals

- Moving sm-goal-phase planning semantics into CNCF.
- Treating concrete model/provider names as Workflow guards.
- Building a universal autonomous-agent framework.
- Making context-budget optimization a prerequisite for Phase 5 runtime completion or Phase 6 initial skill-based use.

## Post-Phase-6 provider candidate: OpenCode

OpenCode is a concrete provider candidate for a successor Phase after Phase 6 closes. It is intentionally NOT a Phase 6 completion requirement.

Phase 6 should establish a provider-neutral contract strong enough that a later OpenCode adapter can be added without changing Workflow definitions or semantic WorkClassification. The later adapter can be evaluated as a driver of the generic ExecutionRequirement -> Provider Selection -> ExecutionEvidence boundary.

The main expected value is a common execution gateway to cloud and local/self-hosted models, including Ollama/LM Studio-class environments, without making sm-workflow itself implement each model/provider protocol. Codex remains an independent provider; OpenCode is not intended to replace or become the semantic identity of coding work.

A successor implementation should study VirtusLab Orca's OpenCode backend as a reference for server/session lifecycle, model selection, streaming/result handling, tool execution, local/self-hosted provider use, and execution/cost evidence. Orca is reference evidence only; sm-workflow/CNCF contracts remain authoritative.

The Workflow boundary must remain logical. OpenCode/provider/model/reasoning-variant identifiers belong to environment/provider resolution and ExecutionEvidence, not Workflow guards or application state transitions.

Phase 6 therefore closes once generic provider replacement is proven; it must not remain open waiting for OpenCode/Ollama integration.

Concrete local LLM integration is a manual operational trial, recorded as
[Phase 8 reference information](phase-8-local-llm-reference.md). It is a
developer-owned reference activity, not a completion condition for Phase 6, 8
or 9. Only a need observed in real use
can bring a bounded provider-related extension into Phase 8's selected work.
