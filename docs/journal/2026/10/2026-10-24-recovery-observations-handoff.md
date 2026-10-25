# Recovery success must not hide unresolved causes

Date: 2026-10-24 (Asia/Tokyo)
Status: specification and plan adopted; implementation/live adoption pending
Recipients: sm-workflow開発 and sm-workflow-dev開発

## Decision and incident

The user wants ordinary recovery to continue without excessive approval requests,
while preserving the defect signal that those requests previously exposed.
Recovery success is not cause resolution. Record recovery observations through
common Journal, Evidence and Finding services without additional AI calls.

In the inspected Phase 6.5 P65-Q1 incident, the Git commit selected nonexistent
`.scalafix.conf`, planned for later work, and returned Exited(1). Correcting the
physical selection still led to AuthorizeNewAttempt under the current recovery
contract. The inspected Session was e89e8ba92d074262e4c8a10ef11d1183, with 146
passing checks, accepted review and 4/10 repairs reported by the consumer. These
are historical observations; verify current public state rather than assuming
the same frontier remains active. Earlier source-history uncertainty is a
separate occurrence and must not be rewritten as this known commit failure.

The original public response locators on the observed machine are under
`/Users/asami/src/dev2026/sm-workflow-dev/target/sm-workflow/runtime-phase-6.5-20261023/target/sm-workflow/tasks/phase-6.5-01a1555d-b4a7-7be3-8232-5396c2f5e293/`,
in `021-advance-q1-closure/response.json` and
`022-describe-q1-closure-recovery/response.json`. These are evidence locators,
not portable repository identities or authority to modify Session storage.

## Producer instructions

Adopt the [specification](../../../spec/recovery-observations-and-cause-resolution.md)
and [ROBS-1–4 plan/checklist](../../../notes/recovery-observations-delivery-plan.md).
Extend the already-requested commit-selection/ordinary-recovery fix with one
automatic observation route. Fix actual target construction, including tracked
deletions, and the unnecessary Human gate for ended/no-commit/in-authority
correction. Do not silently bypass current admission before that route exists.

Reuse actual Evidence, derive Journal entries deterministically, and add an
operation-sourced Finding through the common owner. Current review-origin
Finding contracts need an explicit extension, not a fabricated review or clerical
AI assignment. A successful retry leaves the causal Finding open until correction
or a supported non-defect explanation has applicable evidence. Expected external
failures need not become product defects; uncertain attribution remains uncertain.

Use ordinary source identities for repeat-safe projection and recurrence links.
No hashes, whole-store scans, new approval artifacts or universal detector are
required. Recording failure stays separate from business completion and must not
replay effects. Deliver the selected-commit route first, then recurrence/closure
and the same-Session adoption procedure. Publish an English handoff describing
actual delivered behavior, validation and limitations for the consumer.

## Consumer adoption

Retain ongoing edits, existing Session/Plan, accepted implementation and review,
all history including failed/unknown attempts, repair counts and applicable test
results. Do not restart, resubmit accepted work, manually patch runtime storage,
rerun successful checks merely for adoption or launch a competing producer fix.
Use the producer's supported public adoption path and next required operation.
Do not manufacture Human evidence or treat this document as a response to an
existing exact-target AuthorizeNewAttempt question.

Display actual recovery and cause status separately, with real references. Use
existing Step/Release assessment for unresolved semantic questions; do not invoke
AI for event transcription, recurrence counting or every successful recovery.
No new Phase gate or reset of repair/convergence limits is introduced. Both chats
may share delivery/adoption information as explicitly authorized by the user.

## Work performed in this planning update

Documentation only. No product execution, live Session mutation, commit, push,
test rerun or claim of completed implementation. Existing concurrent edits are
preserved. The ROBS acceptance checklist remains pending.
