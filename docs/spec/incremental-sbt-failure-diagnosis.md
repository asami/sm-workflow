# Incremental SBT failure diagnosis

Date: 2026-10-23
Status: accepted D1–D4 normally delivered and installed-qualified; integrated Phase acceptance pending
Delivery: [Phase 6.6](../phase/phase-6.6.md); prior caller-size-limit removal is complete

## Purpose and ownership

Establish the main route: actual SBT failure → structured diagnostics → requested
additional information → AI repair proposal → existing repair assignment → focused
validation. Reduce log-search and repeated-context cost while retaining access to
the complete available evidence. This route applies when failure diagnosis is
needed; it adds no AI gate to passing checks or ordinary accepted Steps.

CNCF owns typed SBT execution, process lifecycle and managed resource access.
sm-workflow owns diagnostic extraction, evidence references, information delivery
and work progression. The existing caller/semantic provider owns cause inference
and repair planning. A repair worker implements the admitted concrete proposal.
Reuse Common Evidence, Common Journal and existing Workflow/WorkOrder contracts;
do not create another executor, evidence store, repair engine or approval layer.

## Evidence and deterministic extraction

Retain the actual available stdout/stderr and original result through
[Common Evidence](common-evidence.md). Diagnostic views reference the execution,
selected check, candidate/source context and source material. Do not copy the
entire log or Workflow history into every response. Extraction/archival failure
must remain separate from the original SBT outcome; it cannot turn a failed test
into a transport failure or justify rerunning that test merely to recreate logs.

Prefer existing structured test/compiler reports when attributable to this run;
otherwise parse the retained logs. Record the extraction source. Do not merge a
report and its console repetition as two failures. Preserve unavailable counts
as unknown, rather than zero, and retain unmatched material for investigation.

The initial view contains:

- Actual termination/exit facts, check identity, selection and reported counts.
- Test failures, suite aborts, compiler errors, setup/launch/build failures and
  cancellations as distinct observations; no test failure is invented for a
  build that never reached tests.
- Suite/test identity when known, assertion expected/actual values when present,
  exception cause chain, relevant stack frames and source locations.
- Diagnostic occurrence references, surrounding execution context and access to
  the available full log. Identical rendered diagnostics may be grouped, retaining
  occurrence counts, locations and per-occurrence context.
- Extraction coverage, omitted content, available detail and unavailable or
  truncated source information. A display omission differs from source loss.

Grouping identical text is not proof of a common cause. Different failures may
share a cause; identical exceptions may not. AI decides causal grouping after
reading relevant evidence and code. The existing standalone SBT summarizer is a
reference for useful fields and parsing examples, not a complete diagnostic ABI.
Its first-200-entry/2000-character truncation must not be inherited silently.

Compilation failure during SBT test execution is a first-class diagnosis input,
including both production and test-source compilation. Retain available compiler
messages, file/line/column, project/configuration, source context and type details.
AI may request related declarations/call sites and distinguish causal errors from
cascading messages before proposing the repair.

If compilation prevents selected tests from starting, report compilation failure
and those tests as not executed because compilation failed. Do not fabricate test
failure/cancellation counts, success or successful empty selection. Keep actual
results for any other tests already executed in an aggregate invocation. An exit
code alone cannot establish whether a test ran; unknown execution remains unknown.
After repair, require normal compilation and actual selected focused behavioral
execution. Compilation success alone does not close the repair. Subsequent test
failures continue through the same path while retaining the compiler failure,
repair assignments/counts and previous results.

## Incremental semantic interaction

Use the existing issued-work continuation and caller result path. The following
are proposed semantic result variants, not claims of installed API availability:

| Result | Meaning and next action |
| --- | --- |
| NeedInformation | A batched list of requested diagnostic details, log regions or source sections, with the reason each helps distinguish hypotheses |
| RepairProposal | Cause hypotheses, supporting evidence, affected scope, repair units, dependencies, proposed edits and focused validation selection |
| UnableToDiagnose | Missing source, external prerequisite, unresolved design/scope question or remaining uncertainty; retain the reason without inventing a repair |

D2 source contract: `GoalPhaseService.resolveGoalPhaseDiagnosticInformation`
accepts the exact current `issuedWorkContext`, a request identity and a batch of
typed selections with reasons. It is a side read of the outstanding `RepairStep`
with retained check failure, not an accepted semantic completion or another work
assignment. Its selections are `failure-context`, `cause-chain`, `suite-context`,
`execution-context`, `full-output` and `source-file`. Unsupported selectors return
an explicit unavailable item. Suite attribution without a retained structured
interval expands to execution context and stays partial. File reads use the
managed repository workspace within selected ownership; their candidate
applicability is unverified, with the actual observation time retained.

The immutable delivered batch uses Common Evidence and its Evidence Journal
producer. Repeating the same request identity returns that observed batch;
changing its selections or candidate refuses reuse. A fresh request identity
observes current files again. `knownEvidence` suppresses a repeated batch's text
only when the caller already has that exact Evidence reference. Empty selections
restore a page of delivered references, including storage availability and the
normal Evidence read route for full prior facts. `historyAfter` pages the remaining
rounds; twenty items per request/page is not an interaction-round limit. No-new-
information and unavailable items remain stored facts. Journal unavailability
is reported independently of delivery and original SBT outcome. This is the
source contract; installed CLI and connected consumer acceptance remain D4 work.

An information request reuses existing Session/work/continuation/execution
references and selects source material through managed resource access. Supply
semantic units such as one failure with context, its cause chain, suite setup,
execution interval or source symbol; arbitrary fixed-size text chunks are not
the normal interface. When broader context is necessary, allow expansion to the
suite, shared fixtures, execution-wide context and full available output.

Return a coverage/availability result for each requested item. Missing or
unsupported material does not silently become empty successful evidence. Batch
independent requests in one return. Reuse the same diagnosis context where the
caller supports it, and retain delivered references/ranges so unchanged material
need not be resent. If that context is lost or a new worker is assigned, supply
the necessary compact case history and evidence; never assume another agent has
read it. Source reads identify the observed revision or candidate applicability.

Information retrieval uses admitted File/Resource/Evidence operations, not shell
commands or ProcessExecution as file access. Within existing access/scope, it
requires no new Human permission or review. It creates no repair assignment and
increments no repair counter. Repeated requests for already supplied information
return availability/context honestly; they do not trigger another SBT execution.
Do not add an arbitrary round limit, model escalation or an AI call just to decide
which AI call comes next. Persist unresolved/no-new-information status when the
provider cannot proceed; do not manufacture progress through automatic polling.

## Repair and validation

D3 source contract adds optional `diagnosticRepair` to the existing
`GoalPhaseWorkResult` for a completed, failure-bearing `RepairStep`. It is one
strict `sm-workflow.diagnostic-repair.v1` Fix Manifest: `identity`, the actual
validation `sourceRevision` and `candidate`, `causeHypothesis`, retained-log
`evidence` locators (`check`, `stream`, `firstLine`, `lastLine`, `rationale`),
confirmed current-work `information` Evidence references, `ownedPaths`, concrete
`directions`, `dependencies`, selected `checks` and `risks`. The provider forms
the manifest before edits; the already assigned repair worker implements it
and returns it through ordinary Submit. Inline work uses the same semantic
participant. No second work assignment or approval is introduced to admit it.

Native admission compares the manifest to the captured failure, checks the log
ranges and selected ownership, verifies any information references against the
actual issued work, and requires selected behavioral verification as well as
any declared compilation check. It does not certify the causal hypothesis,
assert that a source file matches the failed candidate, or establish successful
tests. Existing candidate ownership and typed SBT admission still govern actual
edits and positive nonempty focused execution. Unsupported/missing context can
continue through NeedInformation or the existing explicit decision-needed route;
it does not justify inventing a manifest or a new approval loop.

Accepted manifests remain in native result history. Subsequent work receives
same-Step `diagnosticHistory` with the actual result reference, generation and
manifest, so later test failures can revise hypotheses while preserving prior
compiler failure and counts. Records carrying the new result/history fields use
native result v3/request v4; old forms retain their original versions. New records
must not be silently downgraded for an old reader. This source contract still
requires the planned connected/installed acceptance and qualified RRC delivery.

The diagnosis provider proposes units based on causes and edit dependencies,
not one worker per failed test. Each unit carries the concrete Fix Manifest
content: hypothesis/evidence, owned scope, implementation direction, known risks,
dependencies and selected verification. Initial delivery uses one repair unit.
Automatic multi-unit scheduling and parallel repairs are later extensions;
parallelism is allowed only when edit ownership and dependencies permit it.

Map the proposal into the existing admitted repair path. A hypothesis may be
revised, split or merged as new facts appear, retaining its history. This does
not erase consumed repair cycles or expand scope by itself. Respect the adopted
Acceptance Boundary and resolved Failure Model; an observed gap is a scoped
proposal, not authorization for speculative defensive mechanisms.

Count actual repair assignments under the existing
[repair continuation contract](goal-phase-repair-continuation.md). Preserve its
allowance, counts and history. Diagnostic requests, reads and result inspection
are not repairs. When required focused tests pass, return to the existing Step
or Release admission route; when they fail, retain new findings alongside the
prior evidence for revised diagnosis. Do not add a mandatory independent review
to every diagnostic or local fix. Existing selected review/lint obligations remain.

Run affected focused behavioral tests through the existing typed SBT route and
normal compilation. A compile-only result or empty selection is not focused-test
success. Reuse unaffected validation. Full/heavy tests retain their planned
Step/Release ownership; neither each fix nor each diagnosis request mandates a
full run. If isolation cannot reproduce an order-dependent failure, explicitly
select the necessary broader scope. Reuse the
[runtime-owned repair control](runtime-owned-repair-control.md) and its bounded
early correction for convergence stops and the ten-cycle ceiling, without a
second counter or caller-owned approval loop. Phase 9 retains broader convergence
refinement and provider routing; their completion is not required for ordinary
same-scope repair continuation.

## Transport and preservation

The helper's fixed 8 MiB response and 1 MiB stderr post-exit rejection and response
read cap have already been removed. Source and distributed helper equality was
confirmed on 2026-10-23; existing regression cases were inspected, not rerun for
this planning update. Treat removal as completed baseline work, not a Phase 6.6
task. Preserve actual exit/JSON handling and raw files, and do not reintroduce
duplicate caller ceilings.
Summary display limits are independent of result acceptance. This does not waive
CNCF capture policy, imply missing bytes exist, or require unlimited buffering.
Prefer referenced evidence and incremental managed reads; design large-output
handling without repeatedly loading or serializing the entire log.

Preserve ongoing Goal, Session, Step and continuation identities, histories,
repair counts, accepted work and original success/failure/UNKNOWN results.
Adopt at an appropriate boundary after an issued call finishes. Do not replay
accepted work or uncertain effects, reset records, reclassify historical failures
as new-route success or silently rewrite stored results. Reuse already retained
failure evidence where available. A new runtime response/view can reference old
evidence without converting historical receipts.

See the [development plan](../notes/incremental-sbt-failure-diagnosis-plan.md),
[design](../design/incremental-sbt-failure-diagnosis.md) and
[Phase 6.6 English handoff](../journal/2026/10/2026-10-23-phase-6.6-incremental-diagnosis-handoff.md).
