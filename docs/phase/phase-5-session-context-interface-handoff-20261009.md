# Side-conversation handoff: session-resolved public Workflow interface

Date: 2026-10-09, Asia/Tokyo.
Status: human clarification and observed public-contract gap; NOT implemented or
tested, NOT Phase acceptance, and NOT authorization to resume execution here.

## Human clarification

The intended interface carries only a session identifier as control context,
plus parameters actually needed for the requested operation or the requested AI
result. If the host supplies the session identity implicitly, the caller does
not need to send it explicitly.

The human confirmed: "本来はセッションIdだけ持ちまわればOKなはずだよね？"
This is not merely a request to consolidate validation. Removing comparison
checks while still requiring the caller to echo a saved context envelope does
not achieve this interface.

## Responsibility boundary

- The caller supplies initial business parameters, operation-specific parameters
  and the semantic result requested by Workflow. Necessary result content is not
  eliminated by the session-only control-context rule.
- Workflow resolves the associated instance, current state, pending request,
  continuation and applicable execution context from the session and its actual
  managed state. It chooses transitions and the next semantic work.
- The skill returns the requested AI result. It does not reconstruct internal
  state, choose internal stages, dispatch competing processing paths or determine
  which mechanical transition should run next.
- Internal revisions, claims and CAS remain Workflow implementation concerns;
  callers are not required to reproduce their saved copies as completion proof.
- When multiple responses can legitimately be pending in one session, return
  and accept only the additional target identifier needed to distinguish those
  requests. Do not guess the target or require the entire issued envelope.
- A session identifier identifies context; it is not itself authorization.
  Resolve actual caller authority and scope where the operation needs them,
  without introducing a comparison ledger or repeated admission machinery.

Implicit Codex chat identity is conditional, not an established host capability.
The generic component must not require Codex-specific identifiers. Explicit
opaque session identity remains usable when host context cannot supply one.

## Observed gap in the existing public contract

The public host specification still describes fifteen Operations accepting one
JSON string in `arg1`. One JSON argument is not the same as minimal caller input.

[Current public contract](/Users/asami/src/dev2026/sm-workflow/docs/spec/normal-three-workflow-host.md:114)

Its advance contract uses Workflow `instanceIdentity` for GoalPhase and
RepositorySync, and also a typed partition context reference for SplitPhase.
Workflow instance identity has not been established as the caller's session
identity or as an implicit session-to-instance lookup.

Its completion contract explicitly requires retention of the handle,
run/continuation identity, expected revision, context snapshot, completion
Operation and assigned result reference from the issued WorkOrder or Decision.

[Advance and completion contract](/Users/asami/src/dev2026/sm-workflow/docs/spec/normal-three-workflow-host.md:140)

Earlier focused source inspection also observed `WorkflowResultJsonV1` decoding
the handle, runId, continuationId, expectedRevision and contextSnapshot from the
submitted result. No implicit Codex chat-to-Workflow/pending-request resolution
was found in that inspected public path. This is not a claim that no session
facility exists elsewhere in the ecosystem.

The public specification above was re-read for this handoff. Implementation is
being changed by the main task concurrently; inspect current consumers before
acting. File links and line numbers are navigation, not identity proofs or
restart gates.

## Required correction to carry into the main task

1. Reconcile the public Operation contract with session-resolved context, rather
   than merely deleting predicates inside the current echo-envelope contract.
2. Resolve existing Workflow context on the server side. Separate initial
   business input and actual result content from redundant control-context copies.
3. Remove unnecessary echoed context, declaration copies, selection diagnostics
   and unchanged-content certificates across producers, consumers, codecs,
   retained-format readers, documentation and executable specs. Do not replace
   them with hashes, full identity/revision comparisons or validated markers.
4. Preserve natural input conversion, real reference resolution and genuinely
   necessary transition, scope, authority and update-conflict handling. Justify
   each consuming condition by the concrete wrong effect it prevents. Persistence
   of real Workflow state is allowed; separate saved comparison artifacts are
   not required by this design.
5. Make ambiguous pending work explicit only where ambiguity actually exists.
   Completion must resolve the intended outstanding request, not simply select
   whichever request is newest when a delayed answer arrives.

Acceptance should exercise the public Operation directly with session context
and necessary parameters only; no CLI or skill transport is needed for these
functional specs. Cover semantic-result submission without echoed revision or
snapshot, actual persisted-state recovery, genuinely ambiguous pending requests
and real concurrent-update behavior. Exercise all three Workflow families.
Rejecting absent redundant context must not remain an acceptance expectation.
Passing an unchanged-envelope test does not establish this correction.

## Relation to the earlier side handoff and main-task boundaries

This supplements, rather than supersedes, the necessity-first removal guidance:

[R38 side-review handoff](/Users/asami/src/dev2026/sm-workflow/docs/phase/phase-5-r38-side-review-handoff-20261009.md)

For any main-task continuation, preserve its full Goal scope, original nine
findings, originals, all history, repair upper limit three / consumed three and
existing repair frames. This document does not reset counters, add a repair
allowance, create a command request or authorize replay of a refused request.
Preserve parent implementation, registered runner testing, read-only independent
review and the shared serial SBT wrapper. Do not declare Phase completion from
this clarification or from a partial interface success.

The human requested a handoff only in this side conversation. The only write is
this new Markdown file. No source, existing task record, Goal, counter, repair
frame, Git state or permission was changed; no subagent, test or SBT was started.
