# Managed Test Suites allocated to Phases 5, 6 and 7

Date: 2026-10-06 (Asia/Tokyo)
Status: user-approved planning allocation; implementation and acceptance pending

## Inputs and decision

The user added the [design note](../../../notes/managed-test-suites-and-verification-cost-guard.md)
and [decision journal](2026-10-06-managed-test-suites-cost-feedback.md) to GitHub,
then requested reflection in Phases 5/6/7 after discussing their ownership.
Both source documents were imported without modification from GitHub main at
commit `55bb473e5d2cf7c0cb2e0366e4437cce8574a063`. Existing local development and
Phase changes were preserved; this was not a repository merge or branch reset.

## Allocation

- [Phase 5](../../../phase/phase-5.md) owns suite definitions/admission/resources,
  fixed project and Slice-focused profiles, deterministic execution/receipts,
  duration policy/warnings, runtime FIX revalidation and bounded improvement
  endpoints. Validate the runtime with CLI/control operations and executable
  scenarios; do not create skills or require AI invocation to close this slice.
- [Phase 6](../../../phase/phase-6.md) connects AI suite design/revision and
  planning-time focused selection, TEST_FIX/REVIEW_FIX, and one human-selected
  warning-to-improvement cycle. Runtime retains validation/transition authority.
- [Phase 7](../../../phase/phase-7.md) consumes registered per-project FULL
  definitions under its existing dependency-aware Git policy and registered
  verification profiles for artifact selection changes. Preserve the actual
  dependency selection context as well as candidate/suite revision evidence.

The [checklist](../../../phase/managed-test-suites-phase-checklist.md) maps the
source note's executable scenarios to connected acceptance and adds the existing
Phase 7 Git/overlay integration cases. No new Phase or Phase 1/2 gate is created.
Phase 5 remains without skill implementation; Phase 6 remains the start of
skill-based use, and Phase 7 extends that use across repositories/artifacts.

## Scope and cost discipline

Official focused selection is part of Slice planning; a FIX reruns SMOKE and the
same admitted profile rather than selecting broader tests after implementation.
If that profile is inadequate, revise the plan/suite explicitly. Exploratory
checks are still available but do not replace official acceptance. The validation
pipeline applies at a connected Candidate/Slice boundary, not after every helper.

ADMISSION's one-minute and FULL's ten-minute budgets are engineering targets,
not timeouts, validity expiry or correctness gates. Retain PASS with a warning,
registered long-running exceptions and explicit optional HEAVY. A warning alone
neither retries tests nor dispatches AI. Use ordinary execution evidence without
hashes, copied logs, unchanged-content proofs or a global build-tool mutex.

Phase 4 Service Bus warning delivery and later dashboards/statistical analysis
remain outside this initial allocation. No unrequested automated optimizer or
test-dependency analyzer is introduced.

## Work performed

Imported the two source documents; updated Phase 5/6/7 scope and completion,
the roadmap and overlay checklist; created the cross-phase suite checklist.
Checked document references, headings, whitespace and scope consistency. This
task did not run product tests, modify runtime/skills, resume the active Phase,
change Goal state/history, commit or publish.
