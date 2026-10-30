# AI Operating Profiles and Lightweight Phase Metrics

Status: accepted target specification; implementation planned in Phase 6.10.
Date: 2026-10-27
Plan: [Phase 6.10](../phase/phase-6.10.md)

## Profiles and selection

Provide three named operating profiles: `standard`, `cost`, and `quality`.
`standard` preserves the effective existing role/model/effort resolution rules;
omitting a profile preserves current behavior. Do not replace the baseline with
a newly recommended model table. Inventory actual runtime and packaged settings
before implementation; source configuration alone does not prove the actual model.

| Profile | Intended worker selection |
| --- | --- |
| standard | Existing effective configuration and selection rules |
| cost | Candidate Terra for bounded implementation/repair/review and Luna for small edits/classification; retain Sol for complex work |
| quality | Candidate stronger Sol effort for difficult implementation and integrated review |

Keep the parent configuration, acceptance conditions, test scope, review triggers
and repair limit unchanged across profiles. Profiles change worker assignments,
not permission or quality gates. Concrete candidates must be supported by the
execution environment; do not silently substitute an unavailable model. Register
and qualify the selection mechanism without making live Terra execution a Phase
completion prerequisite. Keep the existing configuration during the baseline
observation period; Terra activation is a later explicit decision after the
November baseline, not an automatic consequence of model availability.

Add a logical `profile` parameter to sm-goal-phase and its public runtime input.
For new work, precedence is explicit invocation parameter, configured profile,
then `standard`. This is a planned public input, not a claim about today's CLI.
The runtime resolves the registered name; the skill does not construct raw model
overrides or decide new permissions. Resolve and capture the effective selection
at a new Task/Step boundary. A resume-time selection can affect only subsequent
unissued work; issued/running work retains its saved selection. Historical work
without a profile retains its original execution facts, not a fabricated profile.

Record profile identity/version and configured/observed model and effort using
existing Journal/Evidence and execution records. Unknown actual values remain
unknown. Preserve mixed-profile attribution when a Phase uses more than one
selection. Do not add an AI call to choose, inspect or report a profile.

## Role-based candidates and operational comparison — 2026-10-30

Keep the effective existing mapping as standard, including its existing declared
fallback behavior. Do not rewrite it to a recommended Sol High table or infer
observed execution from packaged configuration. Parent settings remain identical
across standard/cost/quality; the current proposal does not switch live profiles.

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
