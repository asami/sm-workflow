# Phase 12: Common Evidence and automatic Action recording

status=closed
Current status: closed
Owner: sm-workflow
Update rule: close only after accepted Step commits, the sole Phase full review, required full validation and successful local release.
planned_at=2026-10-20
owner=sm-workflow
development_chat=sm-workflow開発
execution_priority=before-phase-6.4-E2-integration
depends_on=accepted-phase-11-common-journal
checklist=[Phase 12 checklist](phase-12-checklist.md)
spec=[Common Evidence](../spec/common-evidence.md)

## Goal and scope

Provide a common Evidence owner, like the common Journal delivered in Phase 11.
One actual completed test Action's text log is automatically saved during normal
Advance progression, Journal records its Evidence reference, and a fresh process
can read the Entry and payload. No AI recording-only task, physical-path API or
second Evidence/Journal service is required.

This is a short producer Phase before Phase 6.4's remaining E2 adoption, not a
successor requiring 7–11.1 completion. It owns the minimum reusable runtime and
real storage route; 6.4 owns integration and later operational extensions. The
existing 6.4 Goal, Session, base, accepted E1, counters and all evidence remain.
Phase creation starts no Goal and changes no native question/Plan or running work.

## Main route first

Formal delivery Step: **P12-DELIVERY**. The V1–V3 rows below are its connected
acceptance slices, retaining every existing outcome. The independently accepted
Step is committed before the separate Phase-wide review and final full validation;
this grouping adds no repeated review or test gates.

| Step | Work | Exit evidence |
| --- | --- | --- |
| V1: automatic one-log route | Inspect accepted Journal/storage/Action-result contracts; define minimum typed Evidence record and storage-observation producer; connect one real completed test Action in Advance to managed local archive, common Journal and public ID read. | Actual log saved outside Git, actual Entry/Evidence references returned without AI transcription, and fresh-process Journal -> Evidence payload retrieval. API stubs or allocation alone do not qualify. |
| V2: bounded recording outcomes | Reuse stable source/output identity and actual saved observations; handle repeated Advance, known failed test logs, storage failure, Journal-only recovery and uncertainty without replaying the original Action. | Minimal focused tests plus the real path prove distinct outcomes, source preservation and no duplicate allocation/Action execution. |
| V3: delivery and trial handoff | Reconcile affected skill/result projections, perform required validation/review, publish/deploy through normal authorized delivery and hand the actual interface and setup to 6.4. | The 6.4 host can use the accepted one-log path; exact delivery/results/limitations are recorded. Its E2 acceptance still belongs to 6.4. |

V1 includes actual disk storage and readback, not a model-only first stage.
Freeze only this route's necessary contract. Existing File intents and an explicit
host-prepared archive parent are sufficient if confirmed; general directory or
compression requirements remain separately scoped. Do not wait for all producers.
Keep the initial selected producer explicit rather than silently recording every
Action or recursively recording Journal/read calls.

## Ownership with Phase 6.4

| Capability | Delivery owner |
| --- | --- |
| Common Evidence identity/metadata/store, managed single-text archival/read | Phase 12 |
| Advance producer integration and common Journal storage observation | Phase 12 |
| Minimum stable-source reuse and truthful recording outcomes | Phase 12 |
| Installed trial adoption and Phase 6.4 E2 real-development acceptance | Phase 6.4, reusing Phase 12's evidence at its actual scope |
| E3 operational failure scenarios and E4 batch/compression/retention/hold/due behavior | Phase 6.4, extending the same common service |
| Physical I/O capabilities that are actually missing | CNCF owner, using a concrete scoped request; no absolute-display-path prerequisite |

Common runtime edits remain coordinated through sm-workflow開発; the 6.4 owner
supplies operational requirements and acceptance and agrees exact file ownership
before extending that code. No second producer Phase or reopened Phase 12 acceptance
is required merely for later 6.4 features. No private implementation in the trial
host or simultaneous overlapping source edits.

## Completion and validation

Complete [P12-01..10](phase-12-checklist.md) with actual evidence. Select focused
tests for the changed producer/storage/Journal connection, reuse applicable
existing tests/reviews, and perform the repository's required integrated Phase
validation/review/local closure. No per-helper reviews or repeated full suites.
SBT uses the registered runner and sbt-native locks; no machine-wide wrapper lock.

Publish/deploy the accepted result through the owning development workflow and
record the actual host/configuration and artifact identity. The plan itself is
not proof of installed capability or permission to answer the pending 6.4 Human.
Do not copy live Workflow rows, replay old Submit, rerun successful tests just for
recording, reset counters or claim Phase 12 results as Phase 6.4 acceptance.
The receiving owner validates only changed/missing consumer connections.

Initial delivery excludes full media coverage, broad search/management, batch,
compression, automatic deletion, generic interrupted-workflow recovery and Phase
11.1's Journal management. See the [two-chat handoff](../journal/2026/10/2026-10-20-phase-12-common-evidence-handoff.md).

## Local completion

The accepted P12-DELIVERY Step and its original three commits remain intact.
The sole Phase full review is closed by the independent focused repair chain;
the final local release includes the bounded HTTP 409, shared-resume and GWT
corrections. Required Core, CNCF and sm-workflow repository-full tests and the
declared workstation full driver passed. Applicable installed runtime evidence
is reused at its original scope. See the producer delivery journal for the
completion boundary and retained limits.

Original Goal, bases, PLAN epoch and histories are preserved: Step repairs 0,
Phase repairs 5. Retained nonblocking Hygiene findings remain in their canonical
journal. No new Start, Submit, live Phase 6.4 action or remote push is performed.
The Phase index is preserved with concurrent planning; this document and its
checklist are the canonical completed Phase status.
