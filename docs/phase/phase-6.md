# Phase 6: Abstract Thinking Modes, Skill Integration and Execution Requirements

Status: planned
Planned: 2026-10-03
Depends on: Phase 5 / Phase 5.1, Phase 5.2 complete migration and Phase 5.3 common Workflow loop / minimal data access, CNCF Phase 80 minimum execution-requirement extension

Consume the [Phase 5.3](phase-5.3.md) common Workflow loop, business-typed
Operations and one-session-per-execution contract, retaining
[Phase 5.2](phase-5.2.md)'s removal of redundant control inputs: the
client supplies session context and actual required parameters/results, not
copies of Workflow's internal revision/snapshot/issued envelope. This runtime
boundary does not predefine thinking modes or task-specific skill semantics;
those and actual CLI/skill wiring remain Phase 6 work. Integration must not
reintroduce unnecessary copy/unchanged-content validation.

Skills are new consumers, not legacy clients to preserve. Use only the Phase 5.3
business-typed minimal interface; do not teach universal JSON-string business
parameters or old full commands/envelopes, and do not add compatibility
wrappers, optional legacy modes or fallback routes in skills/CLI clients.
Phases 5.2 and 5.3 own removal of those interfaces before this connected implementation.
Start declares business inputs, result submission uses the requested result type,
and Human answers use their answer type. JSON is a transport representation;
Start returns one session ID per execution; Advance and replies use that ID
without additional execution, context or request selectors.

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

### Session identity lifecycle — 2026-10-12

Consume Phase 5.2's minimal-input handoff and Phase 5.3's business types and
one-session-per-execution contract. This supersedes the earlier multiple-target
selection plan. The caller supplies an identity on every command/REST
invocation, directly or through a client that explicitly attaches invocation
context. An Operation without new business parameters still receives that
identity. A process-local implicit session alone does not establish this client
contract.

Before implementing batch A, fix the following finite responsibility table for
the supported client. Document concrete fields, retention location and examples
alongside that client; do not leave these duties assigned to an unspecified host.

| Concern | Required contract |
| --- | --- |
| Existing identity | A chat ID or another host ID may be proposed at Start when obtainable and usable for this one execution. Do not assume Codex exposes it or reuse it across several executions. |
| No usable identity | Omit the proposed ID; sm-workflow Start allocates and returns the session ID as implemented in Phase 5.3. In both cases the client retains the canonical ID returned by Start. |
| Retention | Name the caller-side storage location and owner. The client retrieves the same ID for subsequent independent invocations; the skill must not reconstruct it from prose or Workflow state. Persist only the needed routing references, not state snapshots or copied work envelopes. |
| Propagation | Define the command argument and/or REST request field/context mapping for each supported transport. Start, advance, result submission and Human answers carry the same session identity to the existing runtime binding. Host-supplied context removes duplicate business arguments, not the identity transfer itself. |
| Several executions | Each execution has its own session ID, including executions started from the same chat. Retain each returned ID with the corresponding development task. No candidate search or extra selection/instance/context/request identifier is exposed to the caller. |
| Meaning | The ID locates workflow context; it does not grant execution permission. Existing caller authority and effect scope remain with their current owners. No TTL, hash, unchanged-state proof or new comparison ledger is introduced. |

Connected acceptance, included in batch A and its integrated review:

- [ ] P6-ID01: Demonstrate proposing an available chat/host ID and omitting it
  when unavailable. In both cases retain the ID returned by Start at the
  documented client location; no Codex-only assumption.
- [ ] P6-ID02: Use the actual client across independent command processes or
  REST requests for Start -> issued work -> result/Human answer as applicable
  -> advance -> intended outcome. Read the retained ID and propagate it each
  time; test-fixture injection of a prebuilt ExecutionContext is insufficient.
- [ ] P6-ID03: Two different chats conduct independent developments through
  the ordinary client with separate returned session IDs; their interleaved
  calls reach only their own execution. A chat starting another execution retains
  a distinct ID. No extra selector, candidate list or internal-state copy is
  requested; missing/invalid IDs produce ordinary input errors.

Use the selected practical driver and affected examples, not a new exhaustive
transport, concurrency or recovery matrix. Normal continuation between calls
does not add recovery of interrupted effects. These are Phase 6 integration
conditions, not new release gates for Phase 5.2 or business-type work for 5.3.

### Common client loop and chat display — 2026-10-12

Use the same loop for GoalPhase, SplitPhase and RepositorySync: Start and retain
its session ID -> Advance -> process the returned work Request or obtain the
Human answer -> call its named Submit Operation -> display the returned chat
message -> Advance again after acceptance. No extra human instruction is needed
for an accepted normal submission. Wait follows its stated condition, Terminal
ends the loop, and errors are displayed/handled without success-path Advance.

Every submit result is designed by Phase 5.3 for chat display. Display its actual
message with ordinary formatting and use its typed acceptance outcome for
control. Do not dump internal JSON, infer acceptance from prose, reconstruct the
outcome via extra status/history queries or add a model call for acknowledgement.
Next-work selection belongs to Advance, not the display code or the worker skill.
The host/client owns this loop; semantic skills process the assigned request and
return the required result without recreating a workflow engine.

The accepted priority is AI cost and structural simplicity over fewer transport
calls. Display -> Advance after acceptance is deterministic client control, not
an additional AI planning/decision step. Do not add combined Start/Submit-and-
Advance variants or a batching mechanism to this delivery merely to reduce calls.

Include this behavior in the existing connected driver and integrated review:
verify that work/Human submission messages are displayed before the next Advance,
and that failure/Wait/Terminal do not cause unconditional next-loop calls.
Runtime response semantics and removal of unnecessary information reads are
Phase 5.3 obligations; Phase 6 connects their actual skill/client/chat use.

### Command execution ownership — 2026-10-12

sm-workflow owns the whole command-execution activity, not merely recording a
command that an AI worker runs. This applies to SBT, Git, test tools, generators,
formatters, history maintenance, application/acceptance commands, packaging and
authorized artifact placement/publication. Reuse existing tools and CNCF execution
facilities; do not implement another compiler, Git engine or generic shell framework.

The runtime selects the adopted operation, resolves the working directory, argv,
environment and relevant inputs, starts the process, handles waiting/readiness,
collects exit status and bounded diagnostics, records the actual result, performs
owned cleanup and applies the defined next transition. Long-lived processes use
the same ownership through shutdown; never stop an unrelated user-owned process.
Failures remain actual execution outcomes. Do not blindly retry effects whose
completion is unknown, claim a command succeeded from an AI summary, or add
intermediate recovery guarantees withdrawn from the normal-use contract.

AI owns semantic implementation/editing, diagnosis, review, conflict resolution
and proposals for new validation/build behavior. A compile failure is collected
by runtime and supplied to AI for diagnosis/fix; runtime runs the subsequent
validation. A Git conflict yields semantic resolution work; runtime performs
subsequent Git operations. An unfamiliar required tool/command follows the normal
configuration/development decision path, not a fallback to AI shell execution.
Do not send command work orders to AI runners, require AI log transcription, or
ask AI to choose the next command after every process. AI file editing remains
semantic work; its editor transport does not transfer build/Git execution ownership.

The client's CLI/REST calls to sm-workflow are the entry transport, not recursive
command work. Runtime consumes existing permission/effect scope and yields only
where an actual decision is required; owning execution grants no new push,
publication, deployment or dependency-edit authority. Product SBT/cache exclusion
remains with SBT/Ivy/Coursier as already planned; do not add a machine-wide runtime
mutex. Current repository development must still obey its separate SBT runner
and serialization instructions while implementing this product.

### Phase documents and live plan synchronization — 2026-10-12

Phase documents/checklists are the external planning and progress interface.
sm-workflow encodes their supported fields into typed internal plan data and
executes from that data plus its owned execution state. The encoding is a working
representation, not an independently authored plan, copied-source certificate or
tamper-detection ledger. Phase documents own plan meaning; actual execution state
and results remain runtime facts. Stable Phase/work-item IDs connect the two.

Implement this ordinary live-use sequence in the same GoalPhase driver:

1. At Start, read the selected Phase document set and encode items, dependencies,
   acceptance conditions and supported configuration. Parse declared fields
   deterministically; unresolved free-text meaning goes to bounded AI planning
   and, where required, developer adoption. Do not ask AI to reinterpret the
   entire plan on every iteration or treat arbitrary prose as executable commands.
2. After receiving work results and before selecting/dispatching the next work,
   refresh from the latest relevant documents. A next Advance after Wait also
   refreshes before selection. Retain execution facts and map updates by item ID;
   ordinary additions, ordering changes and clarified text do not themselves
   require stopping or renewed approval within the existing scope.
3. Keep an in-flight work assignment attributable to the conditions under which
   it was issued. If its item is removed, its acceptance changes, or another
   actual contradiction leaves applicability/continuation undecidable, retain
   the result and ask the specific semantic/Human decision through the existing
   route before accepting affected work or dispatching a conflicting successor.
   Do not silently cancel a process, discard its result, or claim the revised
   requirement is already satisfied. Missing/duplicate IDs and invalid dependency
   references are ordinary input errors, not a demand for integrity proof.
4. Project actual progress into the latest document's designated status/checklist
   fields and result references. Read that target at write time and update only
   owned fields, preserving current human/AI prose, ordering and plan edits.
   A stale internal serialization must never replace the whole document. An
   unresolved overlapping edit produces a specific conflict, not silent loss;
   no general concurrent-document editor or recovery framework is required.

A document checkbox alone cannot establish an unexecuted test, review or release.
Keep previously recorded/imported completion attributable to its actual source;
do not erase it or turn it into newly executed evidence. Changed plan text does
not reset Goal/base/epoch, review history, fix counts or recorded results. Reuse
applicable results when their real inputs and acceptance still apply; a changed
requirement affects its relevant acceptance, not an automatic full-suite rerun.
If applicability needs semantic interpretation, request that bounded judgment.

Use deterministic updates for structured progress and AI for narrative journal
entries, rationale and substantive replanning. Metrics alone never rewrite the
plan. Live synchronization neither auto-expands permission nor introduces hashes,
file snapshots, TTL gates, unchanged-content proofs or repository-wide monitors.
Refresh the relevant plan at normal boundaries, not before every helper operation.
This is synchronization during ordinary work, not restoration of abandoned runs.

### A. Deliver one complete mode/skill/client/result contract

1. Fix the session identity lifecycle above together with a finite table for
   the selected thinking modes: task purpose, supplied
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

Connect command execution and live Phase-plan synchronization above in this same
batch: an actual build/test and local Git effect are runtime-owned, and a document
edit between work steps changes the next selection without erasing prior results.
Use P6-EX01..03 and P6-PS01..04 in the
[connected checklist](managed-test-suites-phase-checklist.md#command-execution-and-live-phase-plan-synchronization)
as cases in this driver, not separate helper-by-helper implementation/review
cycles or optional Phase 8 adapters.

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

Include the [Hygiene / development-candidate recording route](#hygiene-and-development-candidate-recording--2026-10-12)
in this same batch: review result -> AI recording request -> document edit/result
-> next workflow work. Implement runtime progression and the recording skill
together; a note telling developers to maintain journals manually is insufficient.

Apply the [review and maintenance responsibility split](#review-and-maintenance-responsibilities--2026-10-12)
in this batch as well. Source-history maintenance is deterministic Workflow work;
GWT and other meaning-dependent review/correction are AI work. Do not route all
Hygiene observations to an AI fixer merely because they share a classification.

Include [dependency Component boundary handling](#dependency-component-boundary-and-human-approval--2026-10-12)
on this same request/result/decision path. Planning, implementation and review
participants identify proposed dependency modifications; Workflow yields to the
developer before dispatching that work. Deliver both skill behavior and runtime
decision handling, not a caution in the skill text alone.

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

### Staged quality review across projects — 2026-10-13

Phase 6 is the concrete consumer/driver for the adopted
[staged review matrix](../journal/2026/10/2026-10-13-staged-quality-review-matrix.md).
Deliver the required producer/provider work in its owning project, connecting
the smallest complete Step route first rather than waiting for whole upstream
roadmaps. This does not expand Phase 5.2/5.3 or reset prior execution history.

| Owner / delivery | Required responsibility |
| --- | --- |
| sm-workflow Phase 6 | Lifecycle timing, scope/requirements, Evidence consumption/reuse, Admission, Repair/ReReview and runtime commit/closure. |
| [CNCF Phase 106](https://github.com/asami/goldenport-cncf/blob/main/docs/phase/phase-106.md) | Reuse existing Phase 90 CAM and implement only missing generic Requirement/Evidence/coverage/applicability/Admission connections. |
| [Cozy Phase 81](https://github.com/asami/cozy/blob/main/docs/phase/phase-81.md) | Existing CML/CAR metadata path supplies required model identities, Usage Contract-facing relationships and generated-artifact scope with explicit coverage limits. |
| [cbd-support Phase 15](https://github.com/asami/textus-cbd-support/blob/main/docs/phase/phase-15.md) | Common scoped lint/semantic review, typed findings and coverage/gaps, focused re-review and reusable results. |

`STEP`, `PHASE` and `FINAL` are application lifecycle meanings owned here, not
CNCF ABI values or generic Cozy-generated runtime enums. Map them to generic
requirements and provider profiles/scope. The detailed matrix in the journal
is the baseline, not a requirement to run every checker three times.

| Boundary | Question and normal action |
| --- | --- |
| Step Close | Is the delivered Slice locally sound? Evaluate changed and necessary connected scope before Admission/Step commit, using applicable existing evidence where available. |
| Phase Review | Is the integrated Phase sound? Review whole-Phase Usage Contract, integration, cross-Step assumptions, duplication and excessive checking/abstraction; Step PASS alone does not establish integration quality. |
| Phase Final | Is release justified? Confirm required coverage, actual findings/dispositions, applicable review/validation and release prerequisites; reuse comprehensive Phase evidence and examine only relevant repair deltas/gaps. |

Final is primarily deterministic closure readiness. Dispatch bounded semantic
work only for an actual unmet semantic requirement; no automatic full exploratory
review or redundant full test merely because a lifecycle label changed. Preserve
actually required final validation and independent review, and do not silently
waive scope when evidence is insufficient. Nonblocking findings follow existing
recording rules; unresolved blockers prevent closure. A newly discovered real
blocker still receives ordinary handling, even at Final.

Freshness means applicability to actual current subject, inputs and requirements.
Use ordinary references, known changes and supplied review judgments; no TTL,
management hash, full-copy comparison or unchanged-content certificate. A commit
or state transition alone does not invalidate evidence. After repair or a live
Phase-plan change, evaluate affected coverage and request only missing work;
preserve unrelated applicable results. Unknown applicability stays explicit and
uses the existing scoped decision route, not guessed PASS or a blanket rerun.

Keep cbd-support's advisory findings, structured semantic-review results, developer
authority and CNCF Admission distinct. SQR-01 defines the supported integration
with its existing provider-authority contract; do not treat an AI summary as
Assurance or give a provider direct commit authority. CAR lint commands and result
collection run under runtime ownership. Semantic reviews follow P6-DG context/
independence/cost policy, with no extra full-parent-history inheritance.

Delivery within the existing batches:

1. Batch A fixes a finite generic contract/fixture with the three owners and
   connects real CML output -> lint/scoped independent review -> Admission ->
   local Step commit. Runtime/interface missing work is implemented at its owner.
2. Batch B extends the same route to two locally accepted Steps with a cross-Step
   Usage Contract conflict, then integrated review -> bounded repair -> affected
   re-review. Include an excessive-checking/duplicate-state semantic observation
   without requiring automatic discovery of every possible design defect.
3. Batch C accepts Final reuse and real closing effects, including coverage gaps,
   unresolved blockers and changed acceptance. P6-SQ01..05 in the connected
   checklist cover these behaviors through one shared driver; no review cycle
   per helper/finding and no separate full campaign per project.

Each producer/provider closes on its own bounded checklist, validation/review and
its actual handoff in this driver, without waiting for all of Phase 6 to close.
Phase 6 completion requires those needed deliveries and the integrated outcome,
not unrelated UI/KPI, exhaustive CML support or full producer roadmaps. This avoids
circular whole-Phase dependencies. Share evidence at its actual scope without
claiming one repository's tests accept another. Phase 7 reuses this route across
the workspace; Phase 9 adds convergence/provider re-routing, not initial review
or Final evidence reuse. Creating these plans starts no other project's Goal.

### C. Integrated acceptance, usable installation and gradual use

Validate the selected modes, ordinary client, independent execution, bounded
fallback, non-identical participant and managed-suite branches together. Perform
the required independent review at this connected boundary; after fixes, rerun
affected interface/behavior checks and required regressions rather than another
complete provider-by-mode matrix. Check actual callers against the Phase 5.3
contract; do not preserve earlier input formats.

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
layer. Use the current saved-request representation retained through Phases 5.2/5.3 and do not
silently reinterpret its meaning or restore pre-migration readers. Use the
accepted Phase 6 interface as the baseline for subsequent skill expansion and
caller contract tests.

### Parent chat, delegation and review context — 2026-10-12

The ordinary operating loop is parent chat <-> sm-workflow. The parent handles
developer dialogue, requirements, design judgment and semantic work that does
not benefit from delegation. Runtime owns deterministic progression and command
execution. Delegate semantic work when its cost/capability, reasoning needs,
independence or context isolation justify a subagent; do not spawn one for every
Workflow step or for command execution. Delegated results return through the same
typed Submit -> chat display -> Advance loop, not a second orchestration loop.

| Work | Operational selection policy |
| --- | --- |
| Parent dialogue and ordinary design/development judgment | Normally use an upper-tier programming model appropriate to the current environment. This does not require the most expensive general-purpose model. |
| Implementation and repair | Prefer the least expensive suitable model/profile for the bounded task, retaining required quality and validation. Delegate when this makes sense even if the parent uses a stronger model; do not assume every repair is simple or require delegation for trivial work. |
| Review | Meet the required independence and choose capability/effort for the difficulty and impact. A stronger or more expensive model is available when needed, not mandatory for every review. Context isolation alone can justify delegation to the same model. |
| Deterministic execution | Use sm-workflow/tool adapters, with no AI command-runner or model invocation. |

The user's current example, GPT-6.1 Sol high for the parent and no Astra default
for routine programming, expresses this operational preference, not fixed model
names or permanent rankings. Resolve concrete models/effort through environment
profiles and existing configuration. Keep parent defaults separate from per-work
provider selection; no model name belongs in Workflow guards or public business
parameters. Model choice, reasoning effort, context scope and independence are
separate dimensions; stronger models do not substitute for independent review.

Review context should contain the applicable requirements/acceptance boundary,
target changes and necessary surrounding code, relevant design/usage contracts,
actual validation results and the specific findings under review. Do not inherit
the entire parent conversation or implementation deliberation by default. Supply
decision rationale when necessary to understand the adopted contract, without
treating the implementer's confidence as review evidence. Preserve required
coverage, especially final review scope; narrower context does not mean omitted
dependencies or acceptance conditions. Reviewers can request/access additional
relevant material through the existing route when needed, without automatically
importing full history or creating a new information-gathering framework.

Workflow policy supplies the semantic request and execution requirements; the
Codex/client harness maps them to inline work or actual subagent invocation and
the selected context. Use the existing configuration/ExecutionRequirement path,
not a second Codex-specific Workflow policy engine. Phase 6 delivers this initial
role-based selection and context contract; Phase 9 adds validation-observed
re-routing, not the first ability to choose a cheaper implementer or independent
review context. Cover P6-DG01..03 in the existing connected driver and integrated
review, without a provider-by-model benchmark matrix.

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
16. Implement dependency Component / Usage Contract Boundary handling throughout planning, implementation and review. A proposal to modify an external Component or library outside the adopted scope yields to Human Approval through the existing decision path; repository/worktree location alone does not define this boundary.

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

### Dependency Component Boundary and Human Approval — 2026-10-12

Adopt the [dependency-component boundary journal at commit 13ac53f](https://github.com/asami/sm-workflow/blob/13ac53f/docs/journal/2026/10/2026-10-12-dependency-component-boundary.md).
This is a sm-workflow product requirement. It is independent of CNCF Phase 105's
hygiene work and is not satisfied by the present cncf-goal-phase skill stopping.

Resolve the target Component, dependency Components/libraries, permitted Usage
Contracts and explicitly approved modification scope from the current project
and Goal/Phase context. Carry these meanings into planning, implementation/FIX
and review requests through the existing configuration/request mechanisms. A
shared repository or worktree does not combine Component ownership; a different
repository alone does not imply an unapproved change when that Component is
already explicitly included. Keep the minimal session-resolved interface: do
not ask callers to echo internal scope copies, revisions or approval certificates.

Responsibilities and progression:

- AI/skill participants use the supplied boundary and actual task/code context
  to identify dependency changes. Dependency inspection for diagnosis and normal
  use of its public Usage Contract do not by themselves require new approval.
- Planning reports a plan that requires modification inside a dependency before
  adopting that work. Implementation stops before the outside-scope edit and
  reports the required change. Review reports accidental or unjustified crossing
  that escaped the earlier stages; it does not silently approve or expand scope.
- The result identifies the affected Component/library, proposed modification,
  reason, affected Usage Contract and current work that depends on the decision.
  Runtime consumes that proposal, retains its context and yields through the
  existing Human Decision request. It must not dispatch the proposed dependency
  modification or accept an unresolved crossing as completed work while waiting.
- A developer can approve the stated scope, reject it or defer it. Approval
  normally establishes a separately scoped dependency work unit with its own
  acceptance criteria and a link back to the consumer; it does not authorize
  arbitrary additional dependency changes. Rejection/defer leaves the dependency
  modification unapproved and leads to replanning or waiting, not automatic repair.
- Existing explicit approval covering the actual proposed change is reused;
  ordinary continuation does not repeatedly ask for the same approval. A new
  proposal outside that scope yields again. AI assertions, test success, public
  API stability and a hygiene classification are not developer approval.
- Review-discovered edits are retained for developer judgment; do not erase them,
  automatically revert unrelated work or publish/accept the crossing. Retain the
  work's history and report what has already changed separately from a proposal.

This is semantic development control using AI findings and runtime decisions,
not a claim that Workflow can statically detect every possible dependency edit
or sandbox all file writes. Do not build another approval engine, filesystem
monitor, management hash, TTL or unchanged-content proof mechanism. Reuse the
normal decision/result path and record concrete detection limits.

P6-CB01..04 in the [connected checklist](managed-test-suites-phase-checklist.md)
cover the cncf-goal-phase reference case and its planning/implementation/review,
same-repository, external-library, approval and refusal branches. Exercise them
within the existing driver and integrated review; no per-stage review cycle or
new dependency-management framework is required.

### Managed-suite skill connection

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
  Keep physical metrics separate from AI classification. AI-authored GWT/comment
  hygiene and small bug corrections are exempt only from the ordinary repair
  count under Phase 5 policy; their results still affect convergence judgment
  and every AI Fix batch consumes an automatic cycle toward the absolute hard limit.
  Routine deterministic history/format maintenance is not an AI Fix batch and
  does not create a repair cycle; actual failures remain attributable outcomes.
  Feature work, major restructuring and mixed substantive fixes are not made
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

## Review and maintenance responsibilities — 2026-10-12

Review covers both functional correctness and applicable Hygiene/conformance.
Given/When/Then is one meaning-dependent review item, not a separate mandatory
review service or a new review round. Separate who performs the work from its
classification and from whether a finding is required for this delivery.

| Concern | Owner and handling |
| --- | --- |
| Source history comments, including declared version/date/header fields | sm-workflow executes the repository's deterministic maintenance rule on the owned changed files. AI does not calculate/edit/check these fields or receive omissions as repair tasks. Preserve existing origin/authorship information as the rule requires; do not invent history. |
| Mechanical formatting, whitespace, imports or other tool-fixable conventions | sm-workflow invokes the configured formatter/linter and its bounded fixes where deterministic behavior is declared. Do not ask AI to reproduce a tool's transformations. A diagnostic requiring meaning-dependent judgment goes to the AI path below. |
| Executable Spec Given/When/Then | AI reviews and, when adopted, edits the actual scenario explanation and placement against the setup, action and assertions. Empty labels or mechanically inserted prose do not establish correct GWT. |
| Names, comments, documentation and examples | AI checks meaning, clarity, terminology and agreement with actual behavior. Adopted fixes are AI editing tasks; free-form explanation is distinct from deterministic history fields. |
| Local readability and organization | AI assesses small local cleanup and duplication in the changed scope. A large file split, architectural refactor or behavior change is not automatically Hygiene and is not implicitly adopted. |
| Correctness, test adequacy and design/contract issues | AI reviews semantics and returns required findings or development candidates under the Effective Acceptance Boundary. Assertions, setup/actions and test coverage changes are not comments-only GWT hygiene. |

The table is a responsibility guide for the existing changed-scope review, not
an exhaustive new checklist for every file or a separate pass per category.
Runtime may use configured tools to collect factual diagnostics; AI performs
the judgment that cannot be determined by those rules. No new general-purpose
static-analysis framework or history-comparison ledger is required.

### Connected handling

1. AI implementation/FIX returns its actual edits and result. Its assignment
   excludes source-history field maintenance; generated skill instructions must
   not reintroduce history-date chores inherited from older development skills.
2. During mechanical progression, sm-workflow applies declared history and
   formatting maintenance before the candidate's applicable validation/review
   and commit. Reuse existing changed-path information. Do not scan the whole
   repository or silently alter an already accepted candidate after review.
3. One integrated AI review receives the relevant requirements, changed code/specs
   and actual diagnostics. It covers semantic correctness and meaning-dependent
   Hygiene, including GWT, names, explanations and local readability. It does not
   duplicate history-date or deterministic formatter inspection.
4. Runtime routes each finding by responsibility and adopted scope. Required
   or already-adopted semantic corrections become one bounded REVIEW_FIX request
   with issue IDs, targets, reasons and expected corrections. The AI edits the
   actual files and returns per-issue outcomes through the common Submit route.
   Deterministic maintenance omissions stay on the Workflow/tool path.
5. Nonblocking Hygiene and development candidates that were not adopted follow
   the recording route below and do not block completion. Validation/review of
   actual corrections follows their affected meaning; no extra cycle per GWT
   comment, name or individual finding, and no unrelated full-suite rerun merely
   for a deterministic header edit.

If maintenance fails or its rule is unavailable, report the actual tool/configuration
problem through normal error handling; do not silently delegate date/header editing
to AI. A separate explicit request to develop/fix the maintenance tool is ordinary
development work, not routine source-history upkeep. Actual execution results are
recorded normally; no unchanged-content proof or copied-file certificate is added.

Phase 6 delivers the runtime maintenance connection and the review/FIX/recording
skill responsibilities together, using Phase 5.3's common loop. P6-HR01..04 in the
[connected checklist](managed-test-suites-phase-checklist.md#review-and-maintenance-responsibilities)
cover this distinction in the existing integrated batch. This is not a new Phase
5.3 completion condition or permission to expand the active Phase 5.2.

## Hygiene and development-candidate recording — 2026-10-12

Deliver the following connected responsibility split as Phase 6 functionality:

1. **sm-workflow receives the review result**, distinguishing required repairs,
   nonblocking Hygiene and development candidates. Reuse applicable finding
   identities and classification contracts. Review acceptance can coexist with
   nonblocking findings; do not require every finding list to be empty to accept.
2. **sm-workflow requests recording work from AI** through the normal semantic
   work/result route. Supply the items, their review/source context and the
   project's selected recording destination/convention. The request is to record
   discoveries, not to fix or implement them.
3. **Codex / the assigned recording skill edits and saves the document**. It reads
   the existing project journal or candidate document, records the classification,
   description, affected target, reason and source, and returns the actual edited
   file/section references and work outcome. Use the selected project convention;
   each skill must not independently invent its own storage location or format.
4. **sm-workflow consumes the recording result and requests the next work** under
   the existing workflow decision. Required repairs remain required; where only
   nonblocking items remain, proceed toward normal completion/release after
   recording instead of starting a repair cycle for those items.

sm-workflow owns the request, result association and progression. AI owns this
free-form finding-record prose and document editing. Deterministic source-history
edits follow the separate maintenance contract above. Workflow does not need a prose editor or a second
document-storage subsystem. Keep the original review result and recording
references in the ordinary workflow records; the project document holds the
human-readable entry. A read-only reviewer can return findings without editing;
the development/recording worker performs the subsequent recording task.

Recording and adoption are distinct. Neither an entry nor its classification
adds a required repair, changes the accepted scope, increments repair counts or
consumes an automatic Fix cycle. This task records findings; it is not a Hygiene
repair batch. Later actual Hygiene/minor repairs still follow the existing count
and convergence rules. A developer can explicitly adopt a candidate into a
Phase/Step through the existing planning path. Recording is not such adoption.

Batch the items from one review into a bounded recording task. When there are no
items, skip it. A failed/partial recording returns its actual outcome and uses
ordinary task failure/decision handling; do not claim the entries were saved or
silently turn that failure into implementation work. Do not add unbounded retry,
a separate review cycle for each entry, or another review of the recording that
recursively creates more mandatory recording work.

Use Phase 5.3's business-typed minimal interface: session context plus actual recording inputs
and results. Do not require callers to echo issued envelopes, internal revisions,
saved findings or file contents as comparison certificates. Use ordinary file
changes and task outcomes, without hashes, full-copy retention, unchanged-content
checks or a new recording-integrity ledger.

Connected Executable Specifications and the Phase 6 driver cover:

- a review accepted with Hygiene/development candidates -> a recording request
  -> AI/skill writes the selected project document -> result consumed -> normal
  continuation/release eligibility, without converting those items into repairs;
- a mixed review preserves required repairs while separately recording optional
  items; recording does not enlarge repair scope or alter repair counters;
- no optional items skips recording, and failed/partial recording is reported
  accurately rather than marked complete.

Include an actual Codex/skill file-edit/result example in the connected Phase 6
acceptance, alongside deterministic specs. Standalone skill text or a fabricated
recording receipt does not demonstrate the feature. Validate and review this
with the existing integrated batch, not through another per-item review process.

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

- P6-SQ01..05 establish the real Step -> integrated Phase Review -> repair/re-review
  -> Final reuse/closure route with CNCF 106, Cozy 81 and cbd-support 15 bounded
  deliveries accepted at their owners. Initial staged review is not deferred to
  Phase 9 or satisfied only by mocked provider output or authored skill text.
- P6-EX01..03 and P6-PS01..04 in the connected checklist demonstrate runtime-owned
  command lifecycles and live Phase-document/internal-plan synchronization.
  Required SBT/Git connections use real effects; AI executes semantic work, not
  shell commands or log forwarding. Progress writeback preserves current plan
  edits and execution facts; these capabilities are not deferred to Phase 8.
- The selected abstract thinking modes and request/result contract are implemented.
- P6-DG01..03 demonstrate the parent/runtime loop, role-appropriate delegation
  and bounded but sufficient independent-review context. Concrete model profiles
  are replaceable; neither universal delegation nor the most expensive model
  is the programming default.
- Required skills are created/adapted, available in the intended execution
  environment and connected through the normal client to the runtime.
- The common client loop displays each submit's returned chat-oriented result
  before the next Advance and uses its actual typed acceptance outcome. No extra
  information fetch or AI acknowledgement step is needed for that display.
- P6-ID01..03 establish the documented identity acquisition/allocation owner,
  caller retention and propagation across independent invocations, including
  independent developments in different chats with one session ID per execution
  and no additional caller-side target selection.
- The selected development workflow works from skill invocation through result
  submission and completion, with required execution placement and independent
  review; setup and limitations are documented.
- Connected Executable Specifications and required checks pass, and independent
  review has no unresolved blockers for this practical scope.
- The connected driver demonstrates Effective Acceptance Boundary enforcement:
  required failures block, excluded assurance proposals do not, and explicit
  adopted Goal/Phase overrides are consistent across request/FIX/Review/Admission.
- Dependency boundary scenarios P6-CB01..04 pass through actual skill/client and
  runtime decision handling: proposed external Component/library edits yield to
  the developer, including same-repository dependencies; approval is scoped,
  refusal does not dispatch edits and already-approved work does not re-prompt.
  Planning, implementation and review responsibilities are all demonstrated.
- The managed-suite design/FIX/improvement connection above passes its assigned
  acceptance, using the Phase 5 runtime and the same public skill interface.
- Hygiene and development candidates pass through review intake -> AI recording
  request -> actual project-document edit/save -> result -> next work. Review
  acceptance permits nonblocking findings; recording does not adopt them as
  required repairs or add a repair cycle. The runtime and skill both implement
  their responsibilities, including accurate recording-failure outcomes.
- P6-HR01..04 demonstrate deterministic source-history maintenance with no AI
  editing/review chore, integrated semantic Hygiene review including GWT and
  other observations, bounded adopted correction and nonblocking recording.
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


## Safe-by-construction security review projection — 2026-10-14

Application-security evidence follows the existing Step / Phase / Final review
route; Phase 6 does not create a second security workflow. The detailed
placement and producer dependencies are defined in
[Phase 6 Security Review Profile](phase-6-security-review-profile.md).

CNCF Phase 107 supplies safe runtime/ABI primitives, Cozy Phase 83 supplies safe
generated surfaces, and textus-cbd-support Phase 16 supplies CAR lint/CBD Review
evidence. Step review uses focused deterministic evidence; Phase review owns
cross-cutting architecture/security assessment; Final reuses current evidence
and adds only release-relevant checks. Security findings obey the same Effective
Acceptance Boundary, staleness, repair and admission rules as other findings.
