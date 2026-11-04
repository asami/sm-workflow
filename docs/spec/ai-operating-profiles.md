# AI Operating Profiles and Lightweight Phase Metrics

Status: accepted target specification; M1/M2 selection route authored in Phase 6.10, runtime qualification pending.
Date: 2026-10-27
Plan: [Phase 6.10](../phase/phase-6.10.md)

## Profiles and selection

Provide named operating profiles `standard`, `cost`, `quality`, and the explicit
experimental opt-in `local-experiment`. The latter is not a Phase 6.10 completion
requirement; see the experimental selection note below.
`standard` defaults to the versioned classification-based assignment below for
newly issued work after adoption. Retain the original effective baseline as
historical evidence; omission selects standard, not a fabricated historical model.
Source configuration alone does not prove the actual model.

| Profile | Intended worker selection |
| --- | --- |
| standard | Versioned classification-based defaults; retained issued work keeps its captured selection |
| cost | Planned Low/Medium refinement below; keep current mappings until qualification/adoption and retain Sol High/XHigh for difficult work |
| quality | Candidate stronger Sol effort for difficult implementation and integrated review |

Keep the parent configuration, acceptance conditions, test scope, review triggers
and repair limit unchanged across profiles. Profiles change worker assignments,
not permission or quality gates. Concrete candidates must be supported by the
execution environment; do not silently substitute an unavailable model. Register
and qualify the selection mechanism without making live Terra execution a Phase
completion prerequisite. Deliver the new standard revision through normal adoption for unissued work.
The 2026-11-01 decision supersedes the earlier deferred-November rollout; this
planning update itself does not switch live configuration.

Add a logical `profile` parameter to sm-goal-phase and its public runtime input.
For new work, precedence is explicit invocation parameter, configured profile,
then `standard`. The generated Start and Advance Operations expose this optional logical input.
The runtime resolves the registered operating-profile name and work requirements.
The skill-side execution adapter resolves Codex-specific model/effort assignments
under the captured policy; it does not invent policy or new permissions. Resolve and capture the effective selection
at a new Task/Step boundary. A resume-time selection can affect only subsequent
unissued work; issued/running work retains its saved selection. Historical work
without a profile retains its original execution facts, not a fabricated profile.

Record profile identity/version and configured/observed model and effort using
existing Journal/Evidence and execution records. Unknown actual values remain
unknown. Preserve mixed-profile attribution when a Phase uses more than one
selection. Do not add an AI call to choose, inspect or report a profile.

## Authored M1/M2 selection route (retained baseline, before M2A)

The following describes retained M1/M2 behavior, not the final M2A target.

The optional `profile` parameter is generated on `startGoalPhase` and
`advanceGoalPhase`, preserving the existing two-argument `GoalPhaseStartInput`
and exact business admission. The normal Component key is
`sm-workflow.goal-phase.profile`. All three names are registered. Standard uses
the existing `workflow.execution.profiles` mapping and
`sm-workflow.execution.origin`; opt-in cost/quality use typed
`sm-workflow.goal-phase.profiles.cost` / `.quality` with the same existing CNCF
mapping schema. Caller environments explicitly bind semantic reasoning classes to supported
concrete profiles. There is no new built-in model table or live configuration
change. Missing opt-in mapping, class or origin is an explicit unavailable
failure, never standard fallback. Provider availability is not fetched.

At a new semantic Task/Step issue, explicit current invocation wins, then saved
explicit Start intent, then current configured profile, then standard. A Start
choice originating only from config does not freeze later config for unissued
work. A resume parameter affects newly issued work in that call. Retained pending
work bypasses all selection; absence in historical records stays absent.

The ordinary issued-work record captures identity/revision/source, semantic
reasoning class, mapping identity/revision, configured concrete descriptor and
selected concrete descriptor. Model and effort controls are configured values;
observed model/effort is not inferred. The public Work view and existing
extensible issued-context Record project this same retained capture. Existing
WorkflowIssuedWorkContext constructors and generic protocol fields are unchanged.
The existing caller harness reconstructs that exact retained mapping with its
normal mapping constructor; it does not reroute from later caller configuration.
Cost/quality have no alternate substitution. Standard preserves its previously
configured finite fallback with explicit failure observation. Result admission
checks observed profile identity and any supplied model against captured allowed
selection; missing observation is not a successful mapped execution.

The [bounded M1 baseline inventory](../notes/phase-6.10-standard-baseline.md)
distinguishes packaged gpt-6.1-sol/medium from unknown observed values. The
[M2 implementation note](../notes/phase-6.10-profile-capture.md) records source
coverage and pending runtime qualification. No checklist acceptance is implied.

## Role-based candidates and operational comparison — 2026-10-30

The original candidate table below is retained as planning history. Future cost
assignments are refined by [the November 2 cost plan](#cost-profile-refinement--2026-11-02);
the comparison metrics and preservation rules remain applicable.

Retain the original standard baseline and issued fallback behavior as historical
facts. New standard assignments follow the 2026-11-01 contract below. Never infer
observed execution from packaged configuration. Parent settings remain identical
across standard/cost/quality; this document update does not switch live profiles.

| Work role | Cost-profile comparison candidate | Boundary |
| --- | --- | --- |
| Bounded implementation, repair, and required lightweight review | GPT-6.1 Terra; High is an initial effort candidate | Clear inputs, owned scope and existing acceptance checks; preserve reviewer independence |
| Small edits, extraction, classification and bounded first-pass judgment | GPT-6 Luna; effort explicitly configured | Only already-required semantic work; no additional AI gate or call |
| Complex implementation/repair and integrated semantic review | Retain the applicable Sol assignment | Do not force a cheaper model onto unresolved cross-boundary reasoning |
| Parent planning and exceptional architectural judgment | Unchanged parent configuration | Astra remains a separate explicit escalation option, not a baseline comparison variable |
| Git/SBT execution, log extraction, recording and deterministic selection | Existing runtime operations | No AI model assignment for mechanically executable work |

These are configurable hypotheses to compare, not claims that Terra cannot code,
that Luna is limited to text processing, or that Astra guarantees correctness.
Use exact registered model identifiers and supported effort values. Role names
map through existing semantic classes/profile mappings; introduce no second
scheduler or fixed model table. Retain mapping revision and actual role/model
attribution so Terra and Luna assignments can be distinguished within cost.
Compare a changed role assignment against standard while holding parent, scope,
acceptance, tests, review triggers and repair limit fixed. Missing model support
remains explicit; it never silently becomes another profile or successful trial.

Phase 6.10 adds an observational comparison from ordinary accepted development,
not compulsory duplicate executions or a benchmark. Evaluate the following:

| Metric | Definition |
| --- | --- |
| Total consumption through outcome | All acquired attributable parent/worker/review/repair/retry/escalation usage for the selected work interval, deduplicated; retain failed and abandoned work |
| Completion time | Wall-clock start to actual accepted terminal completion, including repairs, validation and waits; show recorded execution/wait breakdown where already available |
| Eventual success rate | Accepted completed work units / all terminal work units in the declared comparable cohort, including failed, exhausted, cancelled and deferred terminal outcomes; show counts and outcome breakdown |
| First-pass success rate | Work units accepted without a repair/retry / the same terminal cohort; show separately from eventual success |

Choose and report the unit (for example semantic Task or Step) and cohort before
comparison; never pool Task, Step and Phase counts or incompatible task classes.
Pending work is displayed separately, not counted as a success or silently lost.
Unsuccessful/open work has elapsed time, not a fabricated completion time.
Show sample size, source/profile revision, observation window, scope and coverage.
Mixed-profile work remains mixed with per-attempt attribution; do not attribute
all cost or success to the final worker or assign the same outcome to two cohorts.
Do not infer model causality from differing task difficulty or waiting time.

Reuse existing records only. Missing usage is unavailable; partial usage is
partial, never a complete total. Keep Codex and local LLM usage separate, and
retain input/output/cache distinctions. These operational measures supplement,
not replace, the signed source-line delta and observed tokens/net-line ratio.
Missing measurements and unfavorable comparisons do not block Phase closure,
reset repair counts, change acceptance, or trigger extra AI calls/revalidation.

The 2026-10-30 published Standard API uncached input/output unit-price ratio
Astra:Sol:Terra:Luna is 100:20:10:1. That is not a per-task cost, speed, or included
subscription-allowance ratio. Cache mix, reasoning/output volume, retries and
speed modes affect actual consumption. Never multiply token counts by that ratio
to manufacture measured credits or account usage. Any monetary estimate must
separately identify its dated tariff, provider and token categories.
Official references: [API pricing](https://developers.openai.com/api/docs/pricing),
[Work/Codex pricing](https://learn.chatgpt.com/docs/pricing), and
[model selection](https://learn.chatgpt.com/docs/model-selection).

At Phase close, retain the compact comparison and its limitations in the normal
Git-tracked completion record: profile/mapping and source revisions, role/model
observations, cohort/counts, observed consumption coverage, elapsed/completion
figures and unresolved limitations. Disposable Evidence links alone are not a
durable summary. Do not copy bulk logs or add another close/commit loop.

## Standard assignment and dispatch follow-up — 2026-11-01

This accepted target supersedes the earlier requirement to leave standard's
mapping unchanged indefinitely and the deferred-November Terra activation rule.
Keep the M1 baseline and M2 execution facts as historical evidence. Deliver a new
logical mapping revision for unissued work; never rewrite an issued capture.
This document update itself neither changes live configuration nor starts work.

### Classification-based standard defaults

| Work | Default model | Reasoning effort |
| --- | --- | --- |
| Deterministic normalization or extraction | Runtime operation; no AI | Not applicable |
| Delegated mechanical hygiene with explicit rules and no semantic choice | gpt-6-luna | medium |
| Small ordinary implementation/fix with established cause and solution | gpt-6.1-terra | medium |
| Ordinary implementation/fix within an established design | gpt-6.1-terra | high |
| Bounded investigation/initial diagnosis; required local review or re-review | gpt-6.1-terra | high |
| Difficult implementation/fix, root-cause analysis or design; Phase/Release integrated review | gpt-6.1-sol | high |
| Hardest interacting boundaries or integrated review | gpt-6.1-sol | xhigh |
| Parent planning and eligible direct edits | Existing user-selected parent | Unchanged; intended baseline Sol High |

These are configurable standard defaults, not measured performance claims. Keep
cost and quality separately selectable. Retain their explicit mappings and the
same acceptance conditions, test scopes, review triggers and repair limit.
Concrete model IDs and supported efforts belong to the skill-side execution
adapter configuration, keyed by the operating-profile/policy revision; reuse
existing profile descriptors for reporting, not separate hardcoded dispatch tables. Do not select Astra automatically.

Preserve enough of the existing classification to distinguish mechanical hygiene
from ordinary small coding: TRIVIAL and SIMPLE currently collapse to Routine, so
mapping all Routine work to Luna is insufficient. Carry the explicit mechanical
nature/rules into resolution using the existing classification contract. File
count alone does not determine complexity. Changes to public names, serialized
fields, behavior or invariants require their actual semantic classification.

### Parent INLINE and actual start

Resolve deterministic work first. For a small, clear local edit with no required
independent context, retain parent INLINE, including the usual few-line repair.
This is not a hard line-count threshold. The runtime chooses/captures INLINE before
using the delegated worker table; a different configured worker model alone must
not force delegation. An issued DELEGATED requirement is not overridden by the
parent. Required independent review remains separate from the implementer context.

Responsibility is split at the caller boundary. sm-workflow owns work
classification, the logical operating profile, required context/disposition,
allowed fallback policy, acceptance and retained result records. It does not know
which Codex models are available, which model this parent is actually using, or
which start parameters this Codex installation supports.

The skill-side execution adapter owns the configured concrete assignment table,
availability resolution and actual Codex agent start. It consumes the issued work
and captured policy, resolves the model/effort through that versioned table, and
returns the requested/resolved/launch observations to sm-workflow through the
existing continuation/result path. Carry the policy revision before dispatch;
retain the concrete resolution with the same issued work so recollection or
resume cannot choose a new assignment from changed caller settings. Reuse the
existing capture machinery; do not introduce a second scheduler or availability
service in sm-workflow.

Connect classification -> logical profile/policy -> disposition -> skill-side
concrete model/effort resolution -> registered role or supported adapter -> actual
start -> result admission. A harness selection record alone is not successful
dispatch. Fixed sm_workflow_* roles currently configured as Sol Medium must not
silently erase the selected model/effort. Use supported role variants or a
supported launch adapter, preserving the role's execution contract. Do not borrow
an unrelated runner role to obtain its model. Git/SBT/publication stay runtime-owned.

Record requested and resolved model/effort, profile/mapping revision, disposition,
role/adapter, launch identity and fallback reason/path with existing records.
Distinguish launch arguments from provider-observed model/effort. Missing observed
values remain unknown; never infer them from a role name or configuration.

### Finite pre-start availability fallback

For the new standard revision, configure the following ordered edges explicitly:

| Unavailable assignment | Next candidate |
| --- | --- |
| Luna medium | Terra medium |
| Terra medium | Sol medium |
| Terra high | Sol high |
| Sol medium | Sol high |
| Sol high | Sol xhigh |
| Sol xhigh | No automatic alternative; return unavailable |

Here "one above" means this routing policy, not a universal model ranking.
Follow edges only when the candidate model/effort is confirmed unavailable before
work execution starts. Capture the finite chain at issue; try each candidate at
most once. Exhaustion returns explicit unavailable without another AI decision.
The skill-side adapter uses availability exposed by Codex or a typed launch
rejection proving no execution, not speculative AI probes. It applies the captured
finite policy and reports the chosen fallback and reason; sm-workflow records and
checks that result without independently discovering Codex models. Missing/malformed profile mappings, invalid permissions,
provider policy refusals and arbitrary transport errors are not model absence.

Failure, timeout, unknown execution state, or a semantic DECLINED after starting
must not trigger this availability fallback. Use existing execution recovery and
convergence policy; do not replay work under a new model. Preserve legacy issued
fallback semantics on their captured revision. Extend the current single-alternate
representation as needed for new standard work rather than losing old records.
Cost/quality keep their explicit contracts; this change does not grant them an
implicit standard fallback. No extra human confirmation is required for a captured
fallback within the same authorized work and operations.

### Qualification and adoption

Qualify the primary route with a bounded real delegated task on an available
configured model through the skill-side adapter, plus parent INLINE and actual
launch-argument/result linkage.
Cover classification, all configured fallback edges, exhaustion, unsupported
effort, retained captures and non-fallback outcomes with deterministic tests.
Fixtures prove selection, not live execution. At adoption, verify availability;
Terra being named in documentation does not establish availability in this chat.
When unavailable, prove the pre-start fallback route and report Terra unqualified;
a live Terra run or comparative benchmark is not a Phase completion prerequisite.

Preserve Session/Plan identity, accepted M1/M2 work, active M3, history, repair
counts and successful validation. Add M2A before M4 without reopening accepted
work; resume existing startup recovery independently. Apply the new revision only
to unissued work at a normal boundary through existing plan/config adoption.
Do not restart, resubmit accepted results, reset counters, or replay successful
checks solely for adoption. Retain mixed-revision attribution in Phase-close
metrics. No extra AI call is introduced for routing, availability polling,
statistics or display. Reuse one integrated review for the changed contract.

## Cost profile refinement — 2026-11-02

Status: planned opt-in mapping revision; not active configuration or accepted
runtime behavior. This refines the October 30 cost candidates for future work;
it does not alter the conservative November 1 standard mapping or quality.
Retain the original M1/M2 mappings and captures as historical execution facts.

### Classification-based cost candidates

| Work classification | Cost candidate | Effort |
| --- | --- | --- |
| Deterministic normalization, extraction, recording, Git/SBT/publication | Existing runtime operation; no AI | Not applicable |
| Eligible small clear local repair without an independent-context requirement | Existing parent INLINE | Unchanged |
| Mechanical edit with exact targets/transformation and semantics already decided | gpt-6-luna | low |
| Hygiene requiring bounded interpretation of explicit rules and affected locations | gpt-6-luna | medium |
| Small ordinary implementation/fix with established cause, solution and expected behavior | gpt-6.1-terra | low |
| Ordinary implementation/fix within established design | gpt-6.1-terra | medium |
| Bounded investigation and initial diagnosis | gpt-6.1-terra | medium |
| Required local review or changed-scope re-review | gpt-6.1-terra | medium |
| Difficult implementation/fix, root-cause analysis or design | gpt-6.1-sol | high |
| Phase/Release integrated review | gpt-6.1-sol | high |
| Hardest interacting boundaries or integrated judgment | gpt-6.1-sol | xhigh |
| Parent planning and orchestration | Existing user-selected parent | Unchanged |

Low is selected from already-resolved decisions, not line/file count. An exact
prescribed rename or semantically justified expected-value update may qualify;
determining correct names, expected behavior, public contracts or invariants
belongs to the appropriate semantic class. Mechanical work with no AI need stays
deterministic. Do not spend a new parent analysis turn or manufacture a lengthy
fix recipe solely to make work eligible for Low. Preserve the same acceptance
conditions, test scope, review triggers and independence, repair trend and limit.

Reuse the existing runtime classification/profile/disposition, skill-side
versioned mapping, capability resolution and actual launch/result path. Do not
add another scheduler, hardcoded dispatch table or AI classification gate.
Keep requested, resolved and observed profiles distinct. A default registered
Sol Medium role must not silently override an explicit Low/Medium assignment.

### Explicit cost availability alternatives

For this new cost mapping revision only, configure these pre-start alternatives:

| Unavailable cost assignment | Explicit alternate |
| --- | --- |
| Terra low | Sol low |
| Terra medium | Sol medium |
| Terra high, if explicitly configured | Sol high |

These are cost-owned edges, not implicit adoption of standard's fallback chain.
Retained cost/quality captures without alternatives keep that meaning. An
unsupported model or effort may select only a captured finite next candidate;
if none is configured/available, return explicit unavailable. Each candidate is
considered at most once. Additional edges for Luna or Sol are not implied by
this table. Never substitute another generation or lower effort silently.

The skill-side adapter uses actual caller capability evidence or a typed launch
rejection proving no execution. Do not infer availability from another machine,
probe it with an extra AI call, or ask again for already-authorized work merely
because a declared alternate is selected. Policy/permission refusal, malformed
configuration, transport uncertainty, post-start failure, timeout and semantic
DECLINED are not availability fallback. Use existing repair/convergence handling;
Low failure does not automatically create a Medium rerun or reset counts.

### Non-disruptive qualification and adoption

Planning this revision does not switch profile/config, issue work, replace a
pending assignment, revise the live Session/Plan or add a new active Phase gate.
Continue active M3 and existing M2A/M4 obligations independently. Reuse the shared
selection infrastructure; at a normal unissued development boundary, implement
mapping-only portions and focused tests where supported. If distinguishing Low
work or cost alternatives needs new runtime machinery beyond the existing route,
record a concrete linked follow-up and retain the current supported cost mapping.
Do not block current Phase 6.10 closure on that follow-up, a live Terra run or a
cost benchmark, and do not describe deferred candidates as delivered.

Only after qualification and explicit selection of cost with the new mapping
revision may it affect newly issued Task/Step work. A previously issued cost
assignment retains its captured revision, actual resolution and fallback policy
across resume. Standard remains the default when cost is not selected. Preserve
existing Goal/Session/Plan, accepted M1/M2 results, active M3, full history, repair
counts and successful evidence; no restart, accepted-result re-Submit, reset or
successful-check replay for this adoption.

Qualify classification distinctions, exact launch arguments, Terra-to-Sol effort
preservation, missing alternate/exhaustion, non-fallback failures and retained
captures with focused deterministic tests. Reuse the existing available-model
adapter proof and integrated review where applicable. Tests prove routing, not
provider availability or savings. Do not add duplicate live work for comparison.
Use the existing ordinary-work metrics: total attributable consumption including
repair/retry and failed attempts, elapsed completion time, first-pass/eventual
success, cohort counts and partial/unknown coverage. Compare like work with the
same acceptance rules; keep Codex/local and mixed-revision attribution separate.
Performance hypotheses do not change acceptance. Tuning affects future unissued
work only; dedicated comparative experiments remain Phase 9.

Handoff: [cost refinement without interrupting active work](../journal/2026/11/2026-11-02-phase-6.10-cost-profile-refinement-handoff.md).

## Local experiment selection — 2026-11-02

The source selection/configuration route registers `local-experiment` through the
existing public `profile` input and normal Component key
`sm-workflow.goal-phase.profiles.local-experiment`. This is optional and never
selected by default. The selection/capture schema revision remains unchanged;
existing standard/cost/quality selections retain their meaning.

Supply a complete existing `cncf.workflow-execution-profile-mapping.v1` mapping
and the ordinary execution origin. The intended configuration starts from cost
and replaces only its Luna assignments with local models. There is no implicit
runtime inheritance or automatic transformation of cost; preserve Terra/Sol
assignments explicitly in the supplied mapping. Do not replace an undifferentiated
Routine class wholesale if it also contains non-Luna work. Unsupported finer
classification remains a configuration limitation, not a new development gate.

The adjacent candidate at
[local model candidate](../../examples/ai-profiles/local-experimental-candidate.json)
records the desired replacements, not a loadable CNCF mapping. Missing mapping
or origin reports unavailable through the existing opt-in path; there is no
silent cost/standard/cloud fallback. Model availability and actual local execution
remain with the existing caller adapter. Registration alone does not prove those
models run on the current machine.

Only unissued work uses a newly selected profile. Preserve pending assignments,
Session/Plan/history/counters and prior evidence. No default/live config change,
new acceptance item, runtime migration or mandatory local-model test accompanies
this optional selection support. This source change is uncompiled/untested at
the user's direction and not installed or published by this side conversation.

## Two source snapshots, not intermediate work accounting

At Phase start and Phase close, count source lines in the same selected source
scope. Here **source steps means physical source lines**, not Workflow Step
objects, statements, executed instructions or accepted development task counts.
Use a simple stable rule: include blank/comment lines within selected source
files; count an unterminated final line once. No language parser or AI assessment
is needed. Record the counting rule and scope alongside both numbers.

Use the repository's existing authored-source selection when available; exclude
generated sources, build outputs, caches, vendored dependencies and evidence.
Capture the same roots/include/exclude rules for both endpoints, allowing files
to be added/deleted within that scope. Do not freeze the initial file list, which
would miss newly authored files. Count the selected snapshot, including intended
uncommitted source at start when present, rather than assuming HEAD is the working
tree. Reuse an existing coherent source snapshot/identity where available. Avoid
new locks, a second snapshot store or source hashes introduced just for metrics.

Take the end measurement from the actual final accepted Phase source state before
best-effort repository synchronization can bring in unrelated changes. Preserve
the measurement on resume/result collection; do not resample a later working
tree as if it were the original Phase endpoint. No per-Step sampling, polling or
intermediate addition/deletion aggregation. Endpoint measurement has small local
I/O cost; the zero-cost goal means no additional AI calls, token acquisition or
dedicated measurement infrastructure, not literally zero CPU/I/O.

## Phase-close projection

| Output | Definition |
| --- | --- |
| Start source steps | Source line count at the captured Phase start |
| End source steps | Source line count at the captured Phase close |
| Net source steps | End minus start, retaining its sign |
| Observed Phase tokens | Already acquired, attributable usage for this Phase, deduplicated across parent/subagents and repeated receipts |
| Observed tokens per net source step | Observed Phase tokens divided by positive net source steps |

Use the existing [usage accounting](ai-usage-accounting.md) contract for usage
semantics and deduplication; do not sum provider totals and their included child
totals twice. Include observed failed attempts and repair effort attributable to
the Phase. Keep Codex and local LLM totals/ratios separate and retain available
input/output/cached breakdowns without adding cached tokens again to an inclusive
total. Do not substitute account-wide usage deltas or a final invocation's usage
for the entire Phase. Retain the observed interval and measurement coverage.

If only part of the Phase usage is available, label the numerator and ratio
`partial observed usage`, not total cost or a complete efficiency measurement.
If no attributable usage exists, show `unavailable`, not zero. Use `N/A` for the
ratio when net steps are zero/negative, either endpoint is unavailable, or their
scopes/counting rules differ. Show available raw values and the reason. Do not
use absolute delta, clamp the denominator to one, or invent missing values.

Display the raw values beside the ratio, plus selected profile(s) and available
actual-model attribution. For example, start=10,000, end=10,200, net=+200 and
observed Codex tokens=40,000 gives 200 observed Codex tokens/net source step;
usage coverage must still be shown. This is an inexpensive exploratory signal,
not a productivity score: deletion/refactoring, comments, language and task mix
can change its meaning. Never make the ratio an acceptance, repair or model
selection gate.

## Failure, adoption and ownership

Missing metrics or an I/O failure must not block Phase completion or trigger
revalidation, another AI call, user approval or a repeat close/commit. Record
unavailability through existing runtime records and render the close summary
deterministically. Do not let metrics-record generation create a self-referential
source-count/commit loop.

For an already-running Phase without a captured start, report start/net/ratio as
unavailable unless an existing exact start snapshot supports the same count. Do
not substitute the adoption-time tree or perform historical log reconstruction.
Preserve Session, Plan, accepted results, history, repair counts and verification.

Phase 6.10 owns named selection and this small endpoint projection. Phase 13
continues to own acquiring and attributing usage; its full completion is not a
prerequisite. Phase 9 owns dedicated model experiments and enhanced routing. Phase 6.10 owns
the ordinary-work comparison projection defined above. No benchmark,
new telemetry service, retrospective audit or live configuration change is part
of adopting this plan.

## Delivered source connection (2026-11-02, qualification pending)

The forward logical policy is selected explicitly by
`sm-workflow.goal-phase.dispatch-policy=standard-2026-11-01` at an unissued
boundary. Its captured dispatch fields are distinct from legacy concrete and
single-alternate records. Concrete assignments are in the versioned product
caller mapping under `skills/sm-goal-phase/dispatch-mappings.json`; the portable
`scripts/goal-phase-dispatch.py` adapter preserves the ordinary semantic role
instructions while emitting supported model/effort launch arguments. Retained
prepare/start/return state is caller evidence, not proof from a configuration
name. See the installed sm-goal-phase skill for the exact argument/return route.
The current implementation worker was issued without that capture and is not
retrospective qualification of the new policy.

The native Phase projection now declares comparable Step cohorts and retains
unknown historical time, usage and first-pass evidence explicitly. Caller-owned
already-retained Task/Step facts can project through
`NativeCallerPhaseMetrics.compare`; it performs no acquisition or workflow effect.
The terminal denominator includes unsuccessful terminal outcomes, while pending
work remains separate. Conflicting repeated units and cross-cohort execution
attribution are excluded with limitations instead of being silently counted.

The existing `sm-workflow.completion.records` Phase recording owner can append
the compact English summary to a selected tracked Markdown destination such as
`docs/notes/phase-6.10-completion.md`. It retains the end endpoint before clerical
recording and reuses it in actual close. Required ownership/staging and the
phase-release commit owner must already be configured. Missing metrics remain
nonblocking; this feature supplies no new file or effect permission. Source tests
and fixture launches establish contracts, not installed delivery, provider
availability or completion of this development Phase. Actual qualification and
independent acceptance remain with the parent runtime.

Optional November 2 cost Low refinements and local candidate materialization
remain nonblocking future configuration work where finer classification or
availability is unsupported. No live cost/local setting is changed here.

The logical dispatch-policy setting may explicitly supply `{revision, edges}`
with the standard revision and a finite subset of its documented logical role edges. The short revision
string selects the documented default. Captures retain the actual edges; cyclic,
unknown-role and parent/runtime fallback edges are refused. The caller validates
its versioned edge data against that capture before resolving availability. No
runtime concrete-model table or provider discovery is added.

## Passive retained observations

Fresh logical-only preparation selects the dispatch policy and registers
`sm-workflow.goal-phase.origin-context` with the actual caller context identity
(trimmed, nonblank, no controls, at most 1,024 characters). It needs no invented
model/provider descriptor or duplicate runtime concrete assignment. When a
legacy concrete origin is also present its context must agree. Pending work
retains its original capture; later registration affects only fresh issuance.
The adapter accepts real `task_name` tool returns, preserves them unchanged and
refuses disagreement with a simultaneous `agent_id` alias. A verified absolute
`request_artifact` public Request file provides bounded launch context while the
adapter retains the full original Request. It is required above 131,072 serialized
characters. The file must decode to the exact original Request; no guessed path
or rewritten work request is accepted.

`sm-workflow.goal-phase.retained-observations` accepts an exact structured value
or its JSON string (at most 1 MiB). This is a passive snapshot of facts acquired
during ordinary authorized work, with provenance references, never permission
or a source of new telemetry. The closed canonical v1 form is:

```json
{
  "schemaVersion": "sm-workflow.goal-phase-retained-observations.v1",
  "phaseSession": "actual-native-session",
  "sourceReference": "caller-owned-retained-record-reference",
  "attempts": [{
    "continuationIdentity": "actual-native-session:actual-continuation",
    "stepIdentity": "actual-Step",
    "kind": "ImplementStep",
    "executionIdentity": "actual-caller-execution",
    "status": "completed",
    "startedAt": "2026-11-02T00:00:00Z",
    "endedAt": "2026-11-02T00:01:00Z",
    "sourceReference": "actual-launch-and-return-reference",
    "usage": [{
      "identity": "actual-retained-usage-record",
      "revision": 1,
      "phaseSession": "actual-native-session",
      "execution": "actual-caller-execution",
      "destination": "codex",
      "role": "worker",
      "model": null,
      "intervalStart": "2026-11-02T00:00:00Z",
      "intervalEnd": "2026-11-02T00:01:00Z",
      "inputTokens": null,
      "outputTokens": null,
      "cachedInputTokens": null,
      "reasoningOutputTokens": null,
      "inclusion": "self-only",
      "coveredExecutions": [],
      "coverage": "unavailable",
      "status": "completed"
    }]
  }],
  "closures": [{
    "stepIdentity": "actual-Step",
    "commitIdentity": "0123456789012345678901234567890123456789",
    "endedAt": "2026-11-02T00:02:00Z",
    "sourceReference": "actual-accepted-local-commit-reference"
  }],
  "completeHistorySteps": []
}
```

The example is a schema illustration, not a real execution observation. All
listed fields are required; nullable fields use explicit null, vectors may be
empty, unknown fields and duplicate JSON keys are refused. Do not populate facts from configured model
names or inferred time. Attempts allow at most 512 unique execution identities,
64 usage rows each, and unique usage identity/revision pairs. Closures and
complete-history Step IDs allow at most 64 unique Steps each; provenance and
identity strings are trimmed, nonblank, control-free and at most 4,096 characters.
Usage inclusion covers at most 512 unique execution IDs. Timestamps must be ISO
instants with nonnegative intervals; known token counts/revisions are nonnegative.
Missing tokens are null, never an aggregate fake zero.

Attempt kinds are ImplementStep, RepairStep, ReviewStep, ReviewPhase or TriageStep;
ReviewPhase has null stepIdentity. Status is completed, failed, declined,
cancelled or unknown. Usage destination is codex/local/other/unknown, inclusion
is self-only/inclusive/unknown and coverage is complete/partial/unavailable.
Every attempt must match an actual retained native continuation, Session,
Step and kind. Each closure must match the accepted Step's commit. Complete
history may only name an actual planned implemented Step, and asserts that the
caller retained its entire attempt history. The projection additionally requires
all native work to be represented and accepted before reporting positive
first-pass success. Native RepairStep or actual failed attempts always establish
non-first-pass work; caller flags never override native repair/outcome facts.
A missing or unknown attempt remains incomplete history. PhaseReview resources
may contribute to Phase totals but are not silently attributed to a Step cohort.

Normal completion recording captures the snapshot once before writing the English
record and reuses it on close/recollection. Later config changes cannot recapture
observations for a completed Session. Legacy absent completion endpoints and
missing historical facts remain unavailable. Structurally invalid configuration
is refused; valid data whose native binding differs is excluded with a limitation
instead of blocking completion or triggering repair. The durable English record
shows provenance, available time/first-pass/resource facts and missing coverage.
Cross-cohort attribution exclusions or absent referenced usage downgrade affected
totals to partial/unavailable while retaining confirmed non-overlapping totals.
No new benchmark, acquisition service, evidence-authentication shortcut or effect
permission is introduced.
