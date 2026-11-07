# Primary publication after host capacity correction

Failure identity: `P611-B2-HOST-PUBLICATION-01`.

## R1 — Closed failure classification

This recovery applies to one retained primary `beforeChecks` publication with
no dependency identity, status `failed`, actual nonzero `Exited`, no pending
preparation, and complete untruncated UTF-8 stdout and stderr. The configured
host must select exactly one fixed `publish-local` program for that workspace,
matching the retained process identity. Its output must contain `No space left
on device` and explicit `(cozyBuildCar)` or `(cozyBuildSar)` packaging failure
summaries. The tiny qualification fixture uses that same packaging summary.

Compile or generation source failures, Test failures, generic host errors,
UNKNOWN, timeout, cancellation, nonstart, incomplete output, truncation and
changed source/program/policy are outside this classification. Existing source
repair and UNKNOWN recovery contracts keep their original schemas and choices.
No blanket host retry, disk cleanup or capacity repair is implemented here.

A terminated failure can have partially published artifacts. Recovery retains
the entire failed request and process result and never asserts that effects
were absent or rolled back. The Human authorizes one **whole-primary** successor
with those possible partial effects understood.

## R2 — Exact same-Session target

The existing public operation
`describeGoalPhasePrerequisitePublicationRecovery --sessionId <session>` returns
`sm-workflow.goal-phase-prerequisite-publication-host-recovery.v1` at an eligible
unstarted ordinary Validation frontier. The target captures the complete native
controller, Plan, base, completed prefix, candidate and its record revision,
generation and repair accounting, original accepted semantic result, whole
failed publication, ordered targets, complete publication/process policies,
genuinely published dependency prefix, prior host direction references and
original source/UNKNOWN/repair lineage.

The descriptor only reads facts. Every dependency in the prefix must have an
actual ready `published` result, still be its exact latest publication, and
still have the captured source. Existing UNKNOWN artifact acceptance is not a
successful publication receipt and is not silently skipped. Earlier UNKNOWN
history remains intact. Native history reconstruction verifies adjacent native
records, definition, causation and full historical target semantics without
observing present host capacity or current source. Live admission and execution
perform current source, policy, prefix and freshness checks again.

Public field conversion accepts only known aliases of fields in the closed
typed target. Opaque controller/process-policy strings and parameter maps are
not recursively rewritten. Missing, crossed, unknown and ambiguous data refuse.

## R3 — Independent Human and correction evidence

Use `recoverGoalPhasePrerequisitePublication --input` or `--inputResource` with
the exact described target and one of these choices:

- `AuthorizePrimaryPublicationAfterHostCorrectionOnce` requires `hostCorrection`.
- `Defer` may omit the correction and writes no direction or publication fact.

`hostCorrection` is the generated `GoalPhasePublicationHostCorrection` value:

| Field | Meaning |
| --- | --- |
| `failedRequestIdentity` | Exact retained failed publication request |
| `programIdentity` | Exact retained fixed publication program |
| `observationReference` | Separately attributable correction observation identity/revision |
| `observedAt` | ISO Instant of the supplied observation |
| `summary` | Concrete observation supplied by its independent owner |

The existing `goal-phase-prerequisite-publication-recovery` evidence purpose
binds the complete target, choice, rationale, participant, Human reference and
all correction fields. Evidence preparation requires the actual participant
and both Human and correction references in the supplied execution evidence.
Those references may identify the same document containing both the
observation and decision; no distinct-document requirement is imposed.
The correction observation must be no later than that independent Human
evidence's occurrence. Rationale and observation summary preserve valid UTF-8
multiline prose without an arbitrary size bound; blank, NUL and unencodable
text refuse. The old process capture supplies elapsed time, not an invented
wall-clock timestamp.
The configured independent evidence provider must authenticate and admit the
exact binding. Passive preparation and JSON references alone authenticate
nobody and do not prove that host capacity has recovered. The evidence owner
attests to the observation and Human answer; restored capacity alone grants no
execution authority. Claimed workers, forged/crossed observations, malformed
fields, unknown fields and explicit null corrections refuse. Legacy target
branches reject supplied host corrections and retain their existing behavior
when the optional field is absent.

## R4 — One direction, one primary successor

Recovery appends only the native
`ordered-primary-publication-host-recovery:` mechanical direction fact. It
preserves the controller, candidate, source, Plan, base, completed prefix,
generation and all repair counters. It starts no process.

Separate ordinary `advanceGoalPhase --sessionId <same-session>` derives the
permit from the admitted direction. Immediately before execution, it rechecks
the exact source, candidate, targets, program, policies and latest publication
attempt. Completed dependencies use only collection/latest/source checks and
are never sent to publication execution. Only the primary uses the existing
`LocalPublication.ConfirmedFailureSuccessor` primitive with a deterministic
request identity derived from the saved native direction.

Repeated Advance collects that exact successor. It cannot publish it twice.
A direction awaiting its first execution cannot authorize another successor;
ordinary Advance is next. If its actual successor fails, that new failed result
remains failed. A separately described eligible new failure requires a new
independent Human direction and correction observation. There is no automatic
retry. After publication succeeds, ordinary selected validation, independent
review and local closure keep their existing owners and retained receipts.

## R5 — Qualification and consumer ownership

`GoalPhasePrerequisitePublicationHostRecoverySpec` E1–E6 use explicitly owned
pure process fixtures. They prove classification boundaries, closed targets,
exact evidence/correction binding, legacy Action compatibility, unchanged
controller/accounting and finite successor collection. They do not claim real
native execution or real consumer consent.

The distinct explicit-only
`NativeGoalPrerequisitePublicationHostRecoveryCliSpec` E7 lives in the existing
CLI specification file. Select `smworkflow.spec.installedCommand` and an
absolute `smworkflow.spec.hostRecoveryHandoff`, then select
`smworkflow.spec.hostRecoveryPhase=prepare` before normal installation and
`resume` after it. Prepare creates tiny primary/dependency projects, genuine
typed SBT publication results, a real controlled packaging failure and the old
CLI's public 409. It retains only public caller/host/Session/source/artifact
metadata and exact public outputs. No consumer database is copied or seeded.

Resume reopens that same isolated host and Session, corrects capacity through
an external fixture marker without editing tracked source, provisions separate
specification-owned Human/correction evidence, and uses public recovery and
ordinary Advance. Real per-project task counters prove one primary successor
and no successful dependency replay. A real focused test verifies the primary
API against the dependency. Qualification ends at normal `ReviewStep` handback;
ordinary independent review and downstream Step commit/closure remain with
their existing workflow owners. The producer's own local acceptance commit is
a separate required gate. Original public failure/refusal bytes, source
candidate, base, prefix and repair counts are retained. Existing CLI examples
remain separately registered and are not run by selecting this suite.

`GoalPhasePrerequisitePublicationHostRecoveryColdDecodeSpec` is a separate
explicit-only fresh-JVM regression. Select the retained public description's
file URI through `smworkflow.spec.hostRecoveryDescriptionResource` and the same
`smworkflow.spec.hostRecoveryHandoff`. Its first host target decode precedes any
UNKNOWN specification fixture warmup. It checks closed public roundtrip,
Session and failed-result identity, the published dependency prefix, actual
per-project counter files, the real check marker and unchanged original public
outputs. These are retained observations of the already executed installed
continuation, not manufactured process receipts or a new workflow run.

The real consumer owner must inspect and supply its own correction observation
and Human direction on its retained Session. Fixture consent is never consumer
consent. Separate source integration obligations remain with that owner; this
recovery does not edit, reset or operate any consumer.

## Published dependency adoption and v2

When a successful dependency's inputs were subsequently repaired and normally
published elsewhere, [published dependency adoption](goal-phase-published-dependency-source-adoption.md)
first records the exact source Direction/capture and a separate recollected
artifact fact. Host target `sm-workflow.goal-phase-prerequisite-publication-host-recovery.v2`
then includes nonempty `publishedDependencyAdoptions`. The original successful
`dependencyPrefix` and failed primary requests keep their actual old source
revisions. The new candidate/capture and external publication provenance are
explicit additional facts; they do not impersonate a consumer publication.

Unselected dependencies retain ordinary original-result/latest/current-source
checks. The selected dependency requires its actual source capture, post-stage
freshness, original retained prefix and independently admitted external result.
No dependency ensure/execute is called. The primary still requires its exact
original failure/latest/source, genuine host correction, independent concrete
Human direction and one ordinary successor. V1 encoding and all UNKNOWN
contracts remain unchanged. Selecting the new explicit installed suite never
reruns the earlier host-only episode.
