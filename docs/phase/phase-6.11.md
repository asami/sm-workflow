# Phase 6.11: Runtime-Owned Directory-List RepositorySync

Date: 2026-10-31
Status: planned; no implementation or acceptance claimed.
Owner: sm-workflow development; adoption by sm-workflow-dev after Phase 6.10.
Sequence: Phase 6.9 -> Phase 6.10 -> Phase 6.11 -> Phase 7.
Specification: [directory-list synchronization](../spec/repository-sync-directory-list.md).
Checklist: [P611 acceptance](phase-6.11-checklist.md).
Handoff: [English development handoff](../journal/2026/10/2026-10-31-phase-6.11-repository-sync-directory-list-handoff.md).

## Purpose and preservation

Reduce AI cost by accepting existing checkout directories and sync intent once,
executing normal synchronization inside sm-workflow and returning the aggregate
result. Configured equal/push/fast-forward and deterministically accepted automatic
merge cases need zero intermediate AI decisions. Preserve low-level operations
for explicitly requested skill adjustments. No worktree creation/management is
part of this Phase; Phase 7 retains that development responsibility.

Keep ongoing Phase 6.10 source work, Session/Plan, accepted results, history,
repair counts and verification intact. This Phase follows 6.10; it adds no new
6.10 completion gate. Reuse qualified 6.9 routes and evidence, the existing typed
Git/SBT runtime, focused/estimate machinery, common recovery and presentation.
Do not rebuild those services or require all predecessor tests to run again.

## Main route first

| Unit | Work | Connected acceptance |
| --- | --- | --- |
| B1 | Inventory existing single-repository/lower-level public APIs and configuration; add directory-list intake and normal identity/target resolution | Mixed equal/local-ahead/remote-ahead repositories finish from one installed entry with no AI setup loop or product tests |
| B2 | Compose automatic merge and impact-selected checks before commit/push | Program merge passes required focused/FULL checks; document merge uses lightweight checks; successful deterministic paths need no AI |
| B3 | Make purpose-based commits before synchronization the default dirty route; connect conflict/semantic handback | Reuse reasons/acceptance, commit coherent groups in dependency order, then sync; ask only unresolved questions and preserve unrelated edits |
| B4 | Retain per-repository results and aggregate continuation; preserve lower-level direct entry | Partial failure/recollection does not replay completed effects; explicit push/fast-forward still use the shared runtime |
| B5 | Simplify skill/installed help, qualify one real batch and publish the accepted implementation normally | Directory-list caller performs transport/issued semantic work/result display only; durable English completion and Phase 7 handoff |

First prove B1 end to end, then extend the same route. Minimal result retention
needed by B1 must already exist there; B4 broadens failure/recovery qualification,
not deferring basic correctness. Use declared ordering only where applicable;
otherwise input order is sufficient. Do not require generic dependency inference,
parallel execution or a scheduler for the initial batch.

## Mandatory reported-incident coverage — 2026-10-31

These are planned corrections, not completed fixes. See the
[target/validation resolution contract](../spec/repository-sync-directory-list.md#reported-entry-gaps-target-and-validation-resolution--2026-10-31).

- B1: reproduce App/Core/TFAF-style remote-tip exit 2 using a work branch without
  upstream and an authorized/configured integration target. Trace the actual
  failure; resolve the remote/ref before observation and continue normal integration
  to that target without upstream mutation, raw caller Git, dirty-work loss or force
  push. Include a bounded CNCF typed-operation correction only if needed.
- B2: reproduce CNCF-style fetched program changes with the normal project validation
  configuration already available. Resolve/reuse that ordinary validation route
  without AI setup or redundant sync-specific registration; run the required checks
  and push only after actual acceptance.
- B4/B5: when target or validation settings are genuinely missing, retain the same
  Session/candidate/results, expose a precise setup request and resume through a
  public configuration-adoption route. Do not hide this gap behind the skill's
  correct prohibition on direct Git, reset counters or replay successful work.

Prioritize target resolution in B1 and normal validation binding in B2; these are
part of the main route, not optional late hardening. Exercise no-upstream explicit
origin/main and non-main targets, contradictory/missing targets, absent remote refs
and authentication failures, normal configured validation, truly missing settings
and same-Session resume. Existing 6.10 work and reported live Sessions remain untouched.

## Verification and completion

Use controlled local repositories and remotes through the installed public route.
Cover a mixed clean batch, non-main upstream, automatic code merge with successful
validation, document-only merge, failed merge validation without push, dirty known
purpose, unresolved/conflicting purpose, partial success and result recollection.
Prove no additional semantic calls for deterministic success using existing issued
work/effect records, not a model benchmark. Demonstrate original lower-level APIs
and single-repository callers still work under the same authority and recovery.

Use changed-behavior focused validation and applicable retained predecessor evidence.
Required heavy integration stays explicitly classified; do not add blanket FULL,
HEAVY or independent reviews at each repository boundary. Perform the appropriate
integrated review once, then normal runtime-owned commit/publication/activation
where needed. A planned test is not qualification and publication is not test PASS.

Complete P611 only with actual evidence and a durable English Phase record containing
source/installed revisions, tested relations, validation selection/reuse reasons,
semantic-call counts, aggregate/per-repository results and remaining limitations.
Do not alter current live Sessions or sync repositories merely by adopting this plan.
