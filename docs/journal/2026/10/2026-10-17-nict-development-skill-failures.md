# Actual skill failures during nict-editing-studio development

Date: 2026-10-17 (Asia/Tokyo)
Status: incident retrospective; no product or shared-skill changes in this entry
Related: [sm-workflow delivery-friction investigation](2026-10-17-phase96-delivery-friction-sm-workflow-review.md)

## Context and evidence

The owner requested a separate account of skill defects that actually occurred
in the `nict-editing-studio開発` chat. That chat was implementing dependency
CNCF Phase 96 in `cloud-native-component-framework-phase96`, supporting the
NICT server/App. These are incidents in the development execution procedure,
not findings that the NICT application or sm-workflow product failed.

This account uses the owning chat's status reports, the Phase 96 local execution
records, the shared skills' incident-specific recovery contracts, and native
Git history. It is a bounded set of confirmed examples, not an exhaustive
incident inventory or a measurement of all time spent on Phase 96. The private
execution records and raw transcripts remain local; this journal summarizes
engineering lessons rather than copying those artifacts into Git.

## Observed incidents

### 1. Obsolete inherited context overrode the selected SBT request

For P96-07B2A's `accumulator-001`, intake inspection succeeded, but the runner
introduced a prerequisite-order check from an earlier inherited summary. It
treated `representative002` as pending even though the parent had already
observed its successful result: six tests passed and the shared SBT lock was
released. The selected accumulator covered 41 suites.

The runner stopped before rendering/submitting the command or starting SBT.
The defect was allowing stale narrative context to override the currently
selected execution request. Revision 26 of the shared procedure introduced an
incident-specific recovery. The recovery contract explicitly notes that the
complete old child transcript was unavailable; attributed reports and parent
observations must not be represented as a complete execution trace.

### 2. Manual runner-identity transcription stopped eleven suites before execution

For P96-07C2B1's `representative003`, the runner supplied a short identity with
its final character omitted. Inspection rejected it with
`runner identity must select exactly one typed intake` (reported exit 2).
The parent could inspect the original intake successfully.

This was a handoff/transcription failure, not a failed product test. SBT had
not started. Revision 27 added a bounded recovery for the same eleven suites.
The fragility lay in having an AI reproduce an already-bound identifier and
making that clerical error require workflow recovery. The complete old child
transcript was unavailable for this incident too.

### 3. An earlier recovery exception was interpreted as the only allowed execution

For P96-07C3's authentication/MCP `representative002`, intake inspection was
accepted, but the runner read revision 27 as allowing only the separate
`representative003` recovery. It therefore stopped its own selected request.
The reported selection was 39 suites, not the earlier summary's count of 40.
No SBT execution or permission use was reported.

Revision 28 added another incident-specific recovery. During that repair, an
automatic approval review separately rejected a proposed shared-source patch
as an unauthorized expansion of the general validation route. That rejected
patch applied no edits; a narrower incident-specific alternative followed.
This source-edit rejection must not be confused with an SBT permission denial.

This incident demonstrates a maintainability problem: accumulating narrow
exceptions can make the normal route harder for the execution agent to identify.
The old child's full transcript was unavailable; this account preserves that
evidence limitation.

### 4. A guessed inspector path prevented the requested test from starting

For P96-08A's `representative004`, the runner invoked a nonexistent guessed
inspection script, then read the canonical transport instructions. The recorded
request was `testOnly org.goldenport.cncf.display.DisplayBusinessOperationIngressSpec`.

The selected child transcript recorded those two calls and the terminal stop;
the nested command exit code was not exposed and remains unknown. SBT was not
started. Revision 29 made reading and using the canonical inspector invocation
explicit and introduced a recovery for this incident.

This was an execution-instruction/discovery failure. The product test had not
yet had an opportunity to pass or fail.

### 5. Lost verifier-result retention blocked an already-reviewed Step commit

P96-08 had a successful two-test correction check, a successful 40-suite /
332-test focused run, and independent review PASS. Its 23-path local commit
request was prepared, but the commit runner failed to retain the verifier's
returned `bundle_locator` under the required internal storage key.

Live/history preflight had been accepted. The runner reported
`COMMIT_RUNNER_RESULT_INCOMPLETE` and stopped before rendering or Git mutation.
The then-applicable handoff rules prohibited reconstructing the lost result or
repeating the verifier, and no applicable recovery route was available.

One runner failure persisted across three parent turns; these were not three
failed Git executions. The Goal was marked blocked while successful product
validation/review and the unexecuted request were retained. A new shared-skill
repair decision, focused procedure tests, independent procedure review and
deployment were needed before that same product commit could proceed.

**Observed resolution:** the owning chat subsequently reported 12 procedure
tests passing, independent review PASS, and one fresh runner completing the
same 23-path commit. Native Git confirms local commit
`3ee7bdc1607e977060f64f9b051789430788ad20` for P96-08. This does not imply that
CNCF main was updated, the product was pushed, or all of Phase 96 was completed.

## Observed procedural amplification

The historical shared state-machine rule required exactly one state per parent
turn. Related instructions explicitly ended a user-answer turn after recording
the decision. Actual owning-chat reports show separate turns for re-review
PASS, commit preparation, repeated diagnosis of the same retention stop,
approval recording without execution, the eventual commit, and the next plan.

The P96-08 commit-preparation report also recorded acceptance updates to five
documents. This confirms repeated management work around a single accepted
Step; it does not establish that every document was edited on every turn.

There is no basis here to describe the incident as repeated full-Phase review:
the inspected P96-08 report recorded zero Phase full reviews. Nor does this
account show that accepted product evidence was discarded: the successful
tests and review were explicitly retained through the commit recovery.

The general state-transition and pre-submission commit guidance has since been
revised locally to allow ready states to continue in the same turn and bounded
recovery of an unsubmitted commit request. The NICT chat's actual P96-08 recovery
was separately reported above. Local instruction changes alone do not prove
that all active chats have adopted them or that the SBT incidents are generally
resolved.

## Lessons for sm-workflow

- Runtime should own deterministic command selection, invocation, result
  capture and normal progression. AI should not manually reconstruct inspector
  commands, identifiers or result-storage steps merely to execute accepted work.
- Distinguish product failure, known non-start, unknown effect and actual
  platform denial. A procedure failure does not establish a failed product test.
- Preserve applicable product acceptance when repairing its execution route.
  A shared-component edit can require separate scope authority without making
  the unchanged product implementation require acceptance all over again.
- Keep one clear normal execution contract. Case-specific recovery provisions
  must not accidentally become a whitelist of executable development requests.
- Separate state responsibilities and independent review without requiring a
  chat turn per internal transition or per progress-record update.

These observations inform the existing Phase 6 integration work. They do not
authorize a new intermediate-recovery guarantee, generic retry engine, integrity
ledger, extra release gate, or removal of genuine permission boundaries.

## Source locators

Local evidence was read on 2026-10-17; these identifiers aid later inspection
without publishing private runtime artifacts:

- Owning chat: `nict-editing-studio開発`, `01a0a674-ad6f-7850-a376-a95e5fe2f488`.
- Phase 96 execution directory: `cloud-native-component-framework-phase96/.codex-workflow/phases/PHASE-96/execution/`.
- Incident records: `phase96-p96-07b2a-accumulator-order-stop-001.json`, `phase96-p96-07c2b1-representative003-runner-identity-stop-001.json`, `phase96-p96-08a-selected-intake-path-stop-001.json`, and `phase96-p96-08-step-commit-execution-001.json` (revision 12 at inspection).
- Shared contracts: `development-workstation/common/codex/skills/cncf-workflow-protocol/references/typed-command-control.md`, the four Phase96 recovery sections for revisions 26–29.
- Historical/current state policy: `cncf-goal-phase/references/state-machine.md` and `execution-contract.md` in that shared skills tree.
- Commit retention/recovery policy: `cncf-commit-execution/references/rendered-request-handoff.md` and `pre-submission-recovery.md` in that tree.

No product tests or shared-skill tests were rerun to write this retrospective.
