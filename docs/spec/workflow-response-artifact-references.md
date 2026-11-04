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
