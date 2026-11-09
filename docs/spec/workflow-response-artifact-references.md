# Workflow response references to retained artifacts

Date: 2026-11-04
Status: planned; implementation and installed qualification pending.
Delivery: [Phase 6.11](../phase/phase-6.11.md), B1/B4/B5.
Acceptance: P611-22–29 in the [Phase checklist](../phase/phase-6.11-checklist.md).

## Purpose and existing owners

Ordinary sm-workflow responses return concise business outcomes and references
to material retained by its existing owner. Apply this common contract across
GoalPhase, GoalTask, RepositorySync, SplitPhase, DelegateTask and StandaloneFix,
including preparation, issued-work, recovery and completion views. Inventory the
actual Operations and generated contracts; this plan asserts no new installed API.

The reported RepositorySync response was 37,533,326 bytes. Two identical encoded
progress snapshots contributed approximately 35.34 MB; those snapshots themselves
included file versions, plans, validation captures and a previous recovery target.
Increasing the acquisition budget does not remove that repeated transfer.

Reuse [Common Evidence](common-evidence.md) and its configured archive and
Component datastore for material actually stored there. Evidence ID/revision and
the existing owning host/component context are the public address; an absolute
archive filename is unnecessary. Workflow progress snapshots and typed native
receipts retain their own managed persistence and references. Do not copy them
into Evidence merely to manufacture an address or introduce a parallel store.

Confirm actual retention before returning a usable reference. Allocation alone,
a temporary caller response file or a guessed destination is not stored content.
If material is not yet retained, use the declared owner's normal retention route
or report its precise pending/failed/indeterminate/unavailable state. Preserve
the original producing result and never rerun its business effect to rebuild data.

## Response and consumption contract

Keep operation outcome, message, current control kind, choices, counters and
necessary semantic summaries inline. Carry large source/plan content, snapshots,
logs, result documents and prior targets as typed references with identity,
revision, material kind and the owner/context needed for resolution. Use actual
existing reference types where applicable. Size/media/availability metadata may
report observed facts but is not an authority or content-equality certificate.

The same saved material uses the same reference wherever needed. Do not inline
the same snapshot twice, recursively serialize previous full targets, or return
logs as Base64 inside ordinary control responses. Explicit bounded detail reads
may return the selected content; ordinary responses and default reads remain
reference/summary views. Label abbreviated previews and retain accessible full
material. No arbitrary response-length gate replaces this structural change.

Consumers resolve references through the owning public typed read/lookup route
and admitted CNCF I/O. Evidence uses its existing public service; expose any
missing snapshot/receipt lookup through its managed owner, never caller access
to private Workflow storage. Resolve only the material required by the issued
semantic task. The caller must not reconstruct a complete historical envelope
merely to dispatch work, prepare evidence or submit a decision.

Preparation and adoption/submission accept the retained target/result reference
and resolve its exact revision in the owning runtime. Preserve owner, Session,
purpose, candidate and current-frontier checks and actual independent Human,
review and execution evidence. A reference proves neither permission nor success.
Do not substitute latest content for a requested revision, broaden grants, add
digest/size admission certificates or bypass existing concurrency checks.

## CLI standard output and diagnostics

Apply the reference-based default to the installed CLI's ordinary human-readable
output and `--json` responses. Emit concise actual outcomes, control/decision
summaries and the same retained identity/revision references as the service
contract. Do not expand referenced material while rendering the CLI response.
Large sources, logs, snapshots, prior targets and Base64 bodies remain with their
existing owner. This must work before stdout is returned to an AI command tool;
caller-authored redirection or post-output truncation is not the product solution.

Keep structured business stdout separate from compact progress/diagnostic events.
Do not forward raw child stdout/stderr or large payloads onto stderr as a way of
avoiding the stdout contract. Preserve complete admitted execution captures and
their actual termination/result through the existing runtime/Evidence owners.
Failures still expose a useful reason, actual exit/availability state and retained
references when present; missing Evidence is reported without invented IDs.

Full selected content is returned only through an explicitly requested authorized
detail retrieval, under the artifact acquisition budget. Version changed CLI
schemas and qualify compatibility for existing consumers. Preserve exit semantics
and normal workflow control. Reference display/retrieval adds no AI call, replay
of business effects or duplicate archival.

Measure actual emitted stdout and stderr on success, failure and resumed work,
including `--json` and the reported 37.5 MB target. Verify the control output stays
compact as retained source/log/history content grows, the full material remains
retrievable in a fresh process, and explicit detail retrieval returns only the
selected material. Report transferred bytes; do not infer a fixed AI-token saving
without actual model-input observations. P611-27–29 qualify this CLI boundary.

## Availability, compatibility and acquisition

Missing, expired, inaccessible, wrong-owner/revision and indeterminate material
return concrete outcomes without fabricated content, fallback to latest, new
execution or accepted-result replay. Use existing retention/hold facilities for
material required by active work and its outstanding consumers; preserve archive
and cleanup ownership. Reference transfer grants no cross-host access.

Version changed generated response/input contracts and qualify retained full-body
records through an explicit compatibility route. Existing Session identities,
stored revisions, accepted results, UNKNOWN/failure history and repair counters
remain intact. Introduce no mandatory historical rewrite, duplicate archival or
re-Start/re-Submit requirement. Test a fresh process resolving retained references.

The separate `workflow.artifact.max-bytes` default remains 67,108,864 bytes
(64 MiB), configurable through normal properties. It applies when acquiring
selected full artifacts and retained legacy full-body inputs. Compact responses
remove the need to repeatedly transfer those bodies; they do not waive CNCF
acquisition limits or other owners' independent storage/capture budgets.

## Qualification

Inventory every affected response producer/consumer across the six workflow
families and shared preparation/evidence services. Verify ordinary control
responses do not grow with duplicated log/source/history bytes; references and
their actual outcomes remain correctly attributable. Include the reported
37.5 MB case, shared nested snapshots/logs, exact revision retrieval, fresh-process
resume, owner/permission refusal, unavailable/expired content and legacy inputs.
Obtain needed full content through the public route and reach the same isolated
Session's normal validation/review/commit/completion path. Preserve successful
effects and all prior uncertainty; authored fixtures are not real Human consent
or independent product review. Record actual transfer sizes and coverage limits.

## Derived preparation imports — B5 authored contract, 2026-11-09

Pure preparation functions keep returning complete importer records without
storage effects. Their executing public importer Operations now separately
retain the **derived importer template** through the configured CommonEvidence
collection, managed File DSL/UoW and its storage observation. They neither import
the encoded host-evidence rows nor archive a second copy of raw execution logs
or native snapshots. Normal output is `WorkflowPreparationResult`: a compact
availability status/message and `retainedImport` only after confirmed storage.
Missing policy is `setup-required`; conflicting or indeterminate storage never
falls back to a complete response and never authorizes another File attempt.

Use the actual `retainedImport` as `readPreparedImport.input` on
`NativeWorkflowPreparationService` to retrieve the complete unchanged importer.
That explicit detail Operation requires its exact Component, authenticated
participant, preparation operation/purpose, source association, evidence ID and
revision. CommonEvidence owns collection and File access; possession of a
reference or a supplied participant string grants no read authority. Generic
Evidence detail reads enforce the same prepared-source principal check before
payload access. Preparation attribution is never native, Human, review,
execution or anti-tamper authority; independent source admission remains intact.

Association keys use the actual business source identity/revision, operation and
kind, selected business scope and native Session/source revision when present.
Both supported public field spellings are resolved without copying historical
bodies, and contradictory aliases refuse. Observation timestamps, issuers and
generated answer filenames do not allocate a new association. Identical bytes
reuse the confirmed reference; differing importer bytes under the same source
association return `conflict` without overwriting the original. This includes a
changed supplied provenance timestamp that changes importer bytes: callers must
retain the original provenance rather than silently rewriting it. Pre-Start
sources use a declared `host-preparation` storage association, not a fabricated
native Session, and retain their actual selected resource references.

The Workflow artifact read budget also reaches CommonEvidence File reads,
including Gzip decoding, through the delivered CNCF `FileReadPolicy`. Generic
Resource/File constructors and their limits remain unchanged. Derived importer
writes independently select `FileWritePolicy` through
`workflow.preparation.max-write-bytes` (absence default 67,108,864 bytes; normal
property precedence and CNCF's finite supported range). Selecting a read budget
neither selects write capacity nor grants FileWrite. A full derived serialized
import can be larger than its input; both actual capacities remain applicable,
and installed qualification must report the observed sizes and any refusal.

Every changed importer Operation supports explicit
`--preparationFormat legacy-full` for its historical complete output, without
retention. The name avoids CNCF's reserved `--format` rendering option. The new
optional public result fields qualify this selected legacy shape. Existing pure
APIs and old stored `passed`/`failed` CommonEvidence records remain readable.
The CommonEvidence v1 string vocabulary additionally admits `prepared` for
these versioned `sm-workflow.prepared-import.v1` associations. Older binaries
that recognize only passed/failed are **not** asserted to read new prepared
records. An importer is prepared, never a passed check. CML/ABI/help and the
in-repository caller follow this changed contract; external copies require
their own rollout and are not qualification evidence.

The installed specification measures actual text/JSON stdout/stderr, retains
full-baseline/detail captures separately, and compares them with ordinary
compact transfers. It uses the exact supplied 37,533,326 / 39,715,102 /
47,011,719 / 55,908,377-byte incident inputs passively, plus fresh isolated
Session acquisition correction. Authored cases and fixtures are not observed
qualification or Phase acceptance. No original historical Session is adopted,
continued or replayed by those passive source-input cases.
