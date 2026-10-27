# Phase 6.8: Commit Recovery and Delegated Delivery

Status: in progress; C1/C2 source implementation assigned, qualification and Phase acceptance pending.
Planned: 2026-10-26 (Asia/Tokyo)
Owner: sm-workflow development
Native Start: Session `15e4adbe862418c95b8fb7c6a1374155`, Plan revision `1`,
base `e3bf444d2e7369c25ca596cba0533f8562f07fa5`; retained task artifacts
`target/sm-workflow/runtime-phase-6.8-20261027/target/sm-workflow/tasks/phase-6.8-01a1555d-b4a7-7be3-8232-5396c2f5e293/002-start/`
and `003-advance/`. Startup is not validation or acceptance evidence.
Sequence: Phase 6.7 -> Phase 6.8 -> Phase 6.9 -> Phase 7
Specification: [commit completion and recovery](../spec/commit-completion-and-recovery.md)
Delegation contract: [sm-delegate-task](../spec/delegate-task.md)
Checklist: [P68 acceptance](phase-6.8-checklist.md)
Handoff: [development adoption](../journal/2026/10/2026-10-26-phase-6.8-commit-recovery-handoff.md)

## Purpose and active-work boundary

Standardize ordinary commit preparation and recovery using the bounded repair
exposed by P67-T1. Complete the limited repair and continue Phase 6.7 under its
unchanged plan first. Neither 6.7 restoration nor its acceptance waits for 6.8.

This subsequent Phase is not a new name for the current repair. Adopt its actual
implementation, tests, review and installed qualification when delivered; do not
claim pending work as complete. Phase 17 retains Task/FIX breadth, and Phase 7
retains related projects, FIFO, subtrees and artifact/synchronization integration.

## Successor allocation — 2026-10-27

[Phase 6.9](phase-6.9.md) owns both clean RepositorySync orchestration and dirty
purpose-based commit preparation. This Phase retains its original C1–C5 and
P68-01–14 acceptance and adds the approved D1–D4 delegated-delivery slice below;
it does not acquire those
sync optimizations. Its qualified commit/recovery results feed 6.9 and Phase 7.

## Main route first

| Unit | Work | Connected outcome |
| --- | --- | --- |
| C1 | Reconcile delivered capabilities/gaps and authority semantics | Same-purpose work continues without fixed-file/document-SHA approval gates. |
| C2 | Connect existing preparation/result/partial-closure handling | Authorized commit -> interrupted delivery -> result collection or justified completion -> next Step, without duplicate effects. |
| C3 | Configurable mechanical commit hygiene | CML EOF finding -> default repair -> recheck -> one commit within Advance; `error` reports without modifying. |
| C4 | Shared Step/Stage, release and RepositorySync closure, including completion records | Runtime-owned record update/commit; scoped Terminal and caller-specific commit/merge behavior. |
| C5 | Caller alignment and installed qualification | One usable delivery, preserved Session/evidence, Phase 7 handoff. |

Prove C1/C2 first and connect C3 immediately afterward. Use deterministic
judgment whenever sufficient; delegate only unresolved semantic questions.
Do not replace Human gates with repeated AI gates. No generic formatter,
unlimited Git-repair framework or periodic monitoring is required.

### C1/C2 concrete authority carry-forward — 2026-10-27

Implement the [same-purpose inventory/Plan adoption contract](../spec/commit-completion-and-recovery.md#same-purpose-inventory-and-plan-adoption--2026-10-27).
Reuse the bounded P67-T4 current-inventory adoption path, but remove its universal
fresh-Human requirement when existing authorization covers the same Component,
purpose, acceptance conditions and operations. Runtime owns the decision and
normal inventory/Plan/candidate/staging/commit-registration updates; the caller
does not bypass a formal request or fabricate consent. Publicly supersede an old
inventory-only Human wait with the retained authority and reason, without answering
it fictitiously. Genuine uncovered decisions remain Human-owned.

Prove one necessary ABI inventory append -> same Step continuation -> required
new-candidate checks -> ordinary commit, without an additional Human/AI approval
gate or FIX increment. Reuse successful results at their actual scope. Qualify
re-entry and a real boundary-expansion contrast. The currently approved response
031 adoption proceeds independently and must not wait for this permanent work.
See the [English handoff](../journal/2026/10/2026-10-27-phase-6.8-authority-carry-forward-handoff.md).

### C2/C4 completion-record closure — 2026-10-27

Implement [completion records and Terminal scope](../spec/commit-completion-and-recovery.md#completion-records-and-terminal-scope--2026-10-27).
The motivating report is Stage 12.1 returning Terminal/completed while Phase 12
remains active, followed by parent-authored completion prose and an external
cncf-commit-execution commit. Stage versus Phase scope can legitimately differ;
required completion-record closure must not escape to a separate skill afterward.

Prove accepted work -> runtime-issued narrative only if needed -> tracked record
update -> necessary runtime-owned commit -> persisted local closure -> scoped
Terminal. Reuse an already completed implementation commit or coalesce when still
possible. Add no self-referential commit-ID/record loop. Qualification includes
post-commit result loss, unfinished records and an older early-Terminal episode
completed via public closure continuation without resetting/replaying development.
Connect this C4 path before C5/D4 final qualification. Preserve existing standalone
test-fix's uncommitted contract, and record best-effort sync separately.
See the [English handoff](../journal/2026/10/2026-10-27-phase-6.8-terminal-record-closure-handoff.md).

### C1/C2 and D1/D3 malformed issued-work recovery — 2026-10-27

Add prevention and same-Session recovery for a Task/FIX WorkOrder retained without
the semantic reasoningClass or resolved execution disposition required by its
normal caller. The observed development-only Task is Session
`4698ba5ab263a9b42ec517ae479820ff`, source revision `2`, pending revision `3`,
continuation `4698ba5ab263a9b42ec517ae479820ff:standalone:3`. It is an incident
locator, not a hard-coded production exception or authorization to operate it.
This is now explicit Phase 6.8 work, with [P68-28–33](phase-6.8-checklist.md).

Before fresh issuance, derive the existing semantic class from actual work kind
and check the owning host's profile/origin mapping. Cover code, documentation,
repair and independent review through shared execution, including delegation's
reuse of it. Do not invent an executor observation or persist an unusable issue
and leave its repair to a caller. Preserve the existing Task/FIX test, review,
commit and completion contracts; this correction is not their broader Phase 17
feature expansion.

For an already retained malformed issue, plan a normal public description and
recovery route bound to the actual current owner, definition, Session, original
work and frontier. Preserve the original issue and failure history; retire it
through an admitted managed atomic transition, recover its exact canonical
checkpoint and issue corrected work only through a subsequent ordinary
Continuation. Assess the necessary CNCF common capability before implementation;
do not misuse Decision supersession, rewrite private rows or copy a modified
issuedWorkContext into Submit. An old proposal withdrawn from this chat's
operational repair scope is not an admitted implementation manifest.

Native Available/unclaimed and initial EntityRevision1 establish only the native
claim state, not absence of external execution. Select actual caller source
locators and use existing independent host evidence admission. AI-created files,
Booleans, configured issuer names or passive preparation alone prove neither
nonexecution nor Human consent. A genuine covering nonexecution observation can
support ordinary same-purpose preparation correction without another Task
approval. If that observation is unavailable, explicitly expose unresolved prior
effects and offer a distinct genuine Human choice on the precise public target
to authorize one new attempt while retaining those unresolved facts. Keep this
choice separate from approval of this plan or of the original Task. Do not label
it proven nonexecution or retry automatically. Defer remains available; known
live execution and native conflicts follow their existing observation/termination
requirements, not a bypass.

Qualify stale targets, concurrent claim/completion, delayed old results,
interrupted retirement/checkpoint/issue and duplicate result collection. Normal
valid pending work is not a profile-switch or scope-change opportunity. Align
normal generation, Help/ABI and installed caller behavior in C5/D4, and qualify
normal producer-to-consumer publication and isolated installed continuation.
Reuse successful applicable evidence and preserve Session/base, accepted results,
original authority, repair counts and unrelated work. No re-Start, substitute
Task, accepted Submit replay or successful-effect replay.

This addition is planning only. It does not reopen active 6.7 acceptance, grant a
live recovery choice, start 6.8 or direct this operational chat to implement
sm-workflow-dev features now. The owning development chat keeps the affected
Task and decides any actual recovery only after delivered qualification and a
fresh exact public description. See the
[English handoff](../journal/2026/10/2026-10-27-phase-6.8-malformed-issued-work-handoff.md).

## Delegated-delivery slice D1–D4 — 2026-10-27

Add the independent [sm-delegate-task contract](../spec/delegate-task.md).
Do not wrap sm-test-fix/goal-task or change their test scope, review, commit or
completion contracts. Reuse their shared runtime mechanisms at the service level.
The delegation Workflow owns verification scope, mandatory local commit,
required publishLocal, return and consumer adoption. Uncommitted work is not
successful delegate completion; no-change work returns an existing commit.

| Unit | Work | Connected outcome |
| --- | --- | --- |
| D1 | Define delegation request/result and CML route using shared execution and C1/C2 authority/recovery | One admitted request, separate requester/destination owners, no wrapper skill |
| D2 | Connect available existing chat -> shared implementation/FIX -> focused acceptance -> local commit -> publishLocal -> return/adoption | First usable installed artifact delivery with attributable outcomes |
| D3 | Add impact-selected FULL, conditional/integrated review and result/publication-failure continuation | No blanket FULL or duplicate work/effects; actual delivery status remains honest |
| D4 | Package thin skill, ABI/help and installed qualification; hand off to Phase 7 | Caller transports issued work; runtime owns lifecycle, no extra monitoring |

After proving C1/C2, connect C3 and the D1/D2 main route, then D3 and C4 shared
integration. Complete D4 with C5 installed qualification and one integrated review.
Reuse 6.7 execution/delivery evidence at its actual scope; do not replay its entire
acceptance. Phase 7 retains FIFO/busy-destination scheduling, modes/subtrees and
explicit whole-Phase composition; initial D2 selects one available existing chat.
Phase 6.9 optimization is not a prerequisite for basic delegated local delivery.
Keep ongoing 6.7 and its separately approved response031 adoption independent.
See the [English handoff](../journal/2026/10/2026-10-27-phase-6.8-delegate-task-handoff.md).

## Reuse map

| Existing work | Phase 6.8 responsibility |
| --- | --- |
| P67-T1 bounded partial-commit repair | Adopt selection/containment checks, chain records and qualification; generalize applicable authority, not the incident ID or 25-file scope. |
| [PA-D1–5](purpose-based-authorization-correction.md) | Close remaining same-purpose/public-adoption gaps without a second implementation. |
| [SHR-C1–7](source-history-recovery-result-collection-correction.md) | Reuse accepted normalization/result collection; qualify only changed commit integration. |
| [RRC](runtime-repair-control-correction.md) | Reuse convergence/limits; distinguish deterministic preparation from product FIX without refunding history. |
| [ROBS](../notes/recovery-observations-delivery-plan.md) | Reuse automatic recording and separate recovery from cause resolution. |
| [Git correction](repository-sync-git-correction.md) | Reuse completed-effect handling and observations, preserving merge parents. |
| [Hygiene handoff](../journal/2026/10/2026-10-26-commit-hygiene-auto-fix-handoff.md) | Allocate the property and first EOF correction to C3/C4. |
| [Runtime local publication](../spec/runtime-owned-local-publication.md) | Reuse necessity/execution/collection for delivery; no standalone AI publishLocal. |
| [Candidate recovery complete Resource input](../spec/goal-phase-candidate-recovery.md#complete-resource-input), [repair journal](../journal/2026/10/2026-10-27-candidate-recovery-resource-input.md) | The current existing-API `inputResource` transport repair proceeds independently and does not wait for Phase 6.8. C5/D4 must preserve and adopt its delivered code, public contract and actual qualification, testing only changed integration; do not duplicate its implementation or roll back to an older version. This repair is not Phase 6.8 implementation or acceptance. |

At execution planning, map original acceptance IDs to satisfied capabilities,
connection-only checks and remaining gaps. Reuse evidence at its actual scope.
Do not rerun all predecessor acceptance to populate a new checklist.

## Completion and preservation

Complete P68 through an installed main route and representative failure/re-entry
cases with real Git outcomes. Select tests for changed behavior; compile and
applicable existing tests/reviews cover settled parts. Review residual business
meaning at Step boundaries and one integrated Phase/release boundary, not each
line or normalization.

Deliver public configuration/help, shared/packaged skill alignment, actual
publication/activation when necessary and an English adoption handoff. Planning
alone does not establish production availability.

Preserve Sessions, original bases, accepted work, failure/UNKNOWN history,
repair counts and successful checks. Keep explicit exclusions, including the
current P67 real-Session decision reserved for its owner. Do not edit active 6.7
acceptance, live plan or staged recovery documents to adopt this future Phase.

### C3 implementation source — 2026-10-27

Native Session `15e4adbe862418c95b8fb7c6a1374155`, Plan 1 issued P68-C3
from `014-c1c2-closure-advance` after accepted C1/C2 commit
`e45fdd2243d46c7bf8f7d68d79e864e0520df97b`. C3 source adds typed
`sm-workflow.commit.hygiene.mode` (default `auto-fix`, diagnostic-only `error`),
a retained managed CML terminal-LF preparation plan, and focused
`CommitHygieneSpec` runtime examples. Affected partially staged CML receives the
explicit no-write limitation described in the combined specification. Step
closure, release candidate preparation and pending merge preparation use the
shared policy. Source authorship alone is not compilation, test execution,
installed qualification or Phase acceptance; checklist conditions remain open.
Broader C4 completion-record closure and caller qualification remain separate.
