# GoalPhase Step assessment and conditional semantic escalation

date=2026-10-21
status=adopted-design-not-implemented
owner=sm-workflow
phase=[Phase 6.5](../phase/phase-6.5.md)

## Purpose

Step provides cheap early warning and fast feedback; Release provides one
integrated semantic review. End ordinary Step validation when the declared Step
conditions are met and no unresolved signal requires judgment before continuation.
Call AI only for the uncertainty that needs resolution at this boundary.
Deterministic assessment is runtime branching over existing facts
and selected policy, not an AI task or a new general policy engine.

PLAN selects the Step kind, required checks, a small explicit signal profile and
any mandatory semantic review. The runtime does not ask AI to decide whether to
ask AI. Existing authority and acceptance boundaries remain; routing to triage
does not authorize new requirements, edits or external effects.

PLAN also identifies planned later integration work and the conditions under
which a nonblocking observation can wait for Release. A Step need not establish
the complete Plan's semantic correctness, but it must satisfy its own required
checks and provide a usable basis for subsequent work. Existing evidence and
ordinary incremental compilation are sufficient within their actual scope;
neither a routine clean build nor an unconditional AI review is required.

## Input and routing

Reuse the Step purpose/acceptance boundary, candidate changes, actual compile and
focused-test results, selected Scala/CAR lint results, available boundary/change
facts, prior applicable semantic decisions and PLAN's selected Actions.
OFF, not applicable, not executed, failed, unavailable and indeterminate are not
zero findings or PASS. Preserve the evidence's actual scope and source.

Apply the following precedence:

| Condition | Next action |
| --- | --- |
| Required validation is missing, unavailable or uncertain | Use the existing validation/preparation/observation route. Do not substitute AI for an unexecuted check or blindly rerun an uncertain effect. |
| A known in-boundary check failure requires correction | Existing FIX -> affected re-validation. No triage to decide whether a confirmed failure exists. |
| Required checks pass and PLAN explicitly requires detailed semantic review | Go directly to Detailed Review; omit the intermediate triage call. |
| Required checks pass, no mandatory review, no unresolved signal requiring judgment before continuation | Formal Step admission -> native commit, with zero AI triage/review/waiver calls. Retain any eligible Release observation with its unresolved meaning. |
| Required checks pass, but selected signals leave semantic uncertainty that needs resolution before continuation | Conditional AI Bad Smell Triage with existing evidence. |

"Sufficiently CLEAN" means the declared Step conditions are satisfied and the
selected policy has no unresolved immediate semantic obligation. It is not proof that all
possible defects are absent. For an ordinary local code Step, the representative
route is compile PASS + actual focused PASS + applicable Scala/CAR lint clean +
no boundary-change signal -> admission/commit. Documentation-only checks retain
their explicitly selected scope; no fictional test success is introduced.

## Immediate resolution versus Release observation

Use the PLAN-selected Step kind, acceptance conditions, known dependencies and
actual signal facts. Do not add an AI call per Step to decide whether a call is
worthwhile, or a Human approval for each observation.

- Missing/failed required checks, explicitly mandatory Step review and uncertainty
  about a contract or guarantee required by the next work cannot be deferred.
  Use the validation/FIX/conditional judgment route appropriate to the actual gap.
- Work explicitly scheduled for a later Step and nonblocking integration/meaning
  observations may wait for Release when the Step's own acceptance and subsequent
  prerequisites remain satisfied under the selected policy.
- If the available facts do not justify deferral and continuation depends on the
  unresolved judgment, use bounded triage or the existing decision route. Do not
  silently label it nonblocking or invent a universal deferral certificate.

Reuse the existing Journal observation/decision facilities to retain source,
unresolved question, reason for deferral, relevant planned work and Release review
destination. A deferred observation is neither CLEAN, a confirmed Finding nor
closed merely because the Step committed. Changed dependencies can make it
immediate; otherwise do not repeatedly reconsider it on each unchanged Step.
Release consumes these records and their applicable later resolution. No second
queue/ledger, per-observation AI recording task or Phase 11.1 dependency is added.

## Signals beyond lint

Zero lint smells alone do not establish that triage is unnecessary. A small
selected profile can use attributable facts such as:

- change size disproportionate to the Step's planned purpose/scope;
- changes crossing declared dependency or Component boundaries;
- changes to a public contract;
- a marked increase in structural complexity, where supported observations exist.

Record each signal's rule, actual source/scope and relation to the planned change.
A signal is a reason to assess uncertainty, not a product defect or repair order.
An expected boundary change with an applicable settled judgment need not trigger
another review just because it is large; required planned review still applies.
Thresholds/classifications belong to the selected profile and available project
facts, not unexplained universal line-count limits. Do not build whole-program
semantic analysis to supply these signals or claim an unmeasured metric is clean.
Distinguish unavailable structural observations (potential semantic uncertainty)
from missing required validation (an execution/evidence gap).

### Executable scenario / Given-When-Then structural signals

Use only available explicit mappings among Usage Contracts, Operations, declared
features, selected executable scenarios and changed artifacts. CAR lint owns
contract/specification/Test-Metadata structural checks where supported; Scala
lint owns source implementation rules. Workflow consumes existing structured
results and performs only small joins/selections over declared facts, not a
duplicate parser or semantic coverage engine.

A changed Operation with no scenario in an explicitly complete mapped inventory,
or a selected focused-test set with no declared relationship to its target,
can be an early-warning signal. A changed input/result/Guard alternative can
support such a signal only when that specific mapping and inventory exist.
Missing or partial metadata cannot prove missing behavioral coverage. Feature
tags alone are not the complete target population or a Given/Then coverage map.
Do not introduce these producer mappings as a prerequisite for all of 6.5.

Existing scenarios may already cover the change: no test-file edit does not mean
no coverage. Respect planned intermediate states and prior accepted judgments.
A signal remains an observation unless an actual adopted check establishes a
failure. Unsupported optional detectors stay OFF with their limitation recorded;
an enabled required check that fails or is unavailable cannot silently become OFF.
Do not parse prose to claim semantic correctness or require a full catalog of
new smells before delivering the main route.

## Conditional AI triage

"AI Semantic Inspection" in the early-warning proposal names this same
conditional Action; it is not another inspection/review stage. Mandatory planned
review still dispatches directly. Once a genuine immediate gap selects triage,
the CLEAN/SUSPICIOUS/DECLINE rules below apply without a new deferral loophole.

When selected, triage uses a context separate from the implementation reasoning
and an appropriate configured cheaper profile. It receives Step purpose/boundary,
the actual candidate diff or scoped source access, deterministic results including
zero lint signals, trigger reasons and applicable prior decisions. A diff summary
alone must not pretend to establish facts it cannot support. Use bounded displays
and references to detailed evidence rather than copying every log into the prompt.

Triage has a small structured result:

- CLEAN: no residual reason for detailed review in the assessed scope;
- SUSPICIOUS: attributable observations and the scope needing detailed review;
- DECLINE: unable to judge, with the actual reason and available scope.

CLEAN returns to formal admission using the existing validation and policy.
SUSPICIOUS or a genuine semantic DECLINE dispatches Detailed Review with the
original lint/structural evidence and triage reasons. No loop through another
triage call on the same unchanged candidate. Transport/tool failure is an
execution failure, not a fabricated semantic DECLINE or CLEAN.

A triage observation is not a confirmed blocking Finding. CLEAN is not an
independent detailed-review acceptance and supplies no fabricated review receipt.
Planned mandatory review cannot be bypassed by a CLEAN triage result.
Detailed Review confirms whether an actual in-boundary finding needs FIX or
whether the signal is harmless. Nonblocking suggestions do not sustain repair.

## FIX, reuse and records

Reuse the current Step/Release FIX path. After repair, rerun affected checks and
reassess only changed evidence/signals. Retain unaffected validation, triage and
review results at their actual scope. Re-review only unresolved findings, repair
delta and affected meaning; a settled condition verified by repair/checks needs
no extra semantic call. Changing the candidate does not automatically replay all
triage, lint or reviews, nor does it automatically preserve an inapplicable CLEAN.

The accepted Scala lint exception route remains: user-decided rule-scoped
suppression with source reason, actual lint recheck and applicable validation.
Do not repeatedly escalate the same already accepted intent without new relevant
evidence. Suppression does not waive another required check or review.

Runtime records the actual assessment rule/profile, input/result references,
observed signals, branch/reason and selected or omitted Actions through the common
Journal producer pattern. Evidence retains available payloads. No AI prose task,
private decision ledger or Markdown dual write is needed. Record skipped triage
as skipped by policy, not CLEAN returned by a nonexistent AI run. Distinguish
mechanical assessment, triage observations and review-confirmed Finding sources;
do not weaken existing Finding eligibility or manufacture reviewed status.

Reuse existing recording failure handling: do not rerun checks, triage or review
solely because Journal/Evidence recording failed. Required admission still uses
actual valid results. This plan does not introduce a mandatory AI Final Recording
gate, a new finding-management engine or a Phase 11.1 completion dependency.

## Release and phase boundaries

Release performs one integrated semantic review of the completed Plan within its
accepted boundary. The following are views within that review, not sequential
mandatory AI calls:

- Plan/Usage Contract satisfaction and intended Given/When/Then meaning;
- correspondence between executable scenarios and the actual implementation;
- Step integration, Component/architecture boundaries and their interactions;
- duplicated state/mechanisms, unnecessary complexity and over-engineering;
- unresolved deferred observations and relevant remaining semantic findings.

Reuse actual compile/test/lint evidence, accepted Step judgments and later
resolutions. Inspect the integrated meaning and remaining gaps rather than
re-prove compiler guarantees or re-review every accepted diff. Phase Review
supplies this integrated review where the existing lifecycle already has it;
do not add a second comprehensive Release review under another name.
Blocking in-boundary findings use existing Release FIX, affected validation and
focused re-review of findings/repair delta/affected meaning. This does not rerun
the whole integrated review after every fix. Unresolved required meaning cannot
be silently deferred beyond release. Final consumes the resulting acceptance
and records; it does not dispatch another test/review or an AI final-recording task.

Classify nonblocking observations and useful feedback in the same review/result
and existing Journal records. A finding adequately corrected at Release adds no
Step burden by default. If earlier detection was actually needed, record a
bounded improvement candidate for the cheapest applicable compiler/model, test
selection or lint mechanism, with conditional triage only where needed. Do not
automatically enable new Step rules, turn optional improvements into blockers,
or require a separate AI retrospective for every finding. No new analytics system
or finding-lifecycle implementation is required for this feedback.

CBD remains OFF until Phase 10. Existing repair counters/limits/history remain;
Phase 9 owns enhanced observed convergence and provider routing. Neither Phase
is pulled into 6.5. No blanket model pin, Local LLM benchmark project or new
all-Step AI gate is adopted by this specification.

Phase 6.5 implements Q1's deterministic no-AI main route first, Q2 repair, Q3
minimal lint and conditional escalation, then Q3-R RepositorySync and Q3-S
SplitPhase improvements before Q4 installed integrated acceptance. Their distinct
admission and semantic contracts are defined in the
[cross-workflow specification](workflow-semantic-gap-routing.md); no generic
engine replaces them. LLM evaluation is out of scope. Luna may be adopted through
existing supported configuration, but failure to adopt it does not block 6.5
acceptance using an adequate existing profile. Model comparison stays with the
future Phase 9 work, without a Local LLM evaluation prerequisite.
Current Phase 6.4/12 execution and earlier accepted evidence are not modified.
See the [adoption handoff](../journal/2026/10/2026-10-21-phase-65-deterministic-assessment-handoff.md).
