# sm-workflow development chat operation

Date: 2026-11-18

## Adopted direction

The user selected actor-style operation for the sm-workflow development chat:
perform development on the canonical local `main` checkout and process requests
one at a time in receipt order (FIFO).

This is the operating policy for this chat. It does not claim that the planned
runtime-owned FIFO in `docs/spec/dependency-development-delivery.md` has been
implemented, configured or qualified. Do not build another controller, queue
service, replay framework or verification ledger merely to apply this policy.

## Processing rules

- Keep one active development request. Later independent requests wait in
  receipt order and do not interrupt its source edits, validation, review,
  commit or selected local publication.
- Treat a correction, authorization or additional evidence for the active
  request as its continuation, preserving its identity, Session, history,
  accepted results and counters. Do not enqueue it as a duplicate request.
- Answer status questions without changing the active request or queue order.
- If the active request needs an answer or an external result, retain that
  waiting state and order. Reordering, holding or cancelling work requires
  explicit direction; do not silently skip a waiting request.
- Use `/Users/asami/src/dev2026/sm-workflow` on `main` for new source edits.
  Retain historical worktrees, refs and proposals without treating them as
  independent active development lanes or merging them automatically.
- Implementation, test execution and independent read-only review are stages
  of the active request. Subordinate agents do not start another queued request
  or edit the shared checkout concurrently. Keep independent top-level SBT
  sequential through the user-required shared wrapper.
- Preserve unrelated local configuration and work. Main-based development does
  not itself authorize push, remote publication or another chat's Session
  operations. Retain each request's selected delivery and acceptance boundary.
- Report actual completion or an actual wait distinctly. Acknowledgement of a
  chat message is not native Workflow admission, successful validation,
  publication or consumer adoption.

## Existing request carried forward

At adoption there is one known unfinished development request, first in order:
the Cozy multiple-report selected-validation continuation correction.

The user authorized adding these five paths, followed by repair, revalidation,
independent review and local publication:

1. `src/main/scala/org/simplemodeling/textus/smworkflow/admission/TerminatedValidationContinuation.scala`
2. `src/test/scala/org/simplemodeling/textus/smworkflow/admission/TerminatedValidationContinuationSpec.scala`
3. `src/test/scala/org/simplemodeling/textus/smworkflow/admission/DelegateTaskValidationContinuationOperationSpec.scala`
4. `src/test/resources/validation-continuation/cozy-two-reports-20261118-stdout.log`
5. `src/test/resources/validation-continuation/cozy-two-reports-20261118-stderr.log`

The original proposal and scope report remain under
`.codex-workflow/checkouts/cozy-multi-report-validation-continuation-20261118/.codex-workflow/tasks/cozy-multi-report-validation-continuation-20261118/`.
At this policy's adoption, that branch and local main both point to `5c7a0b6e`;
the proposal has not been applied or tested. This is an initial observation,
not a condition requiring branches to remain identical.

The new explicit main-based direction supersedes this request's earlier
prohibition on main reflection for its continuing development. The prohibition
on push remains. Preserve the original consumer Session, InvalidReport and
full captures, generation 2, counters 1/10, conditions and unrelated candidate.
Do not restart or rewrite the earlier completed producer Task to manufacture
acceptance of this new correction. Actual Cozy correction/adoption remains
owned by the requesting chat.

Historical requests already completed are not automatically re-enqueued.
No other unfinished request is inferred solely from an old branch or worktree.
