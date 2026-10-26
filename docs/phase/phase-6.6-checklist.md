# Phase 6.6 Checklist: Incremental SBT Failure Diagnosis and Repair

Status: completed — all sixteen Plan1 conditions accepted by ReviewPhase1438;
additional P66-17/P66-18 reconciled as completed from retained execution evidence
Phase: [Phase 6.6](phase-6.6.md)

Session `f01d809d1b4bfe8a4b5c241dac301b81` completed through Advance060
on 2026-10-26. Native validation1435 passed1774 tests across182 suites;
ReviewPhase1438 accepted all sixteen conditions, with final Submit057 accepted.
The original Plan1 and D1–D4 commits, UNKNOWN/failed history and repair counts
remain retained. Synchronization is a separate repository operation.

## D1: retained failure and structured diagnostics

- [x] P66-01: One real retained SBT failure is associated with its actual check,
      candidate and available Evidence without rerunning the failed command.
- [x] P66-02: The initial view includes available assertion values, exception
      causes, relevant stack/source locations and surrounding context. Exact
      duplicate grouping retains occurrences and does not assert common causation.
- [x] P66-03: Source loss/truncation, summary omission, incomplete extraction and
      unavailable evidence are distinct. Suite/setup/compiler failures do not
      invent test outcomes; ambiguous attribution remains explicit.
- [x] P66-13: Compilation errors encountered during SBT test/focused execution
      include available project/configuration, compiler message, file/line/column,
      source context and type details. Production and test-source compilation
      are both covered; AI can request related declarations/call sites and group
      root/cascading errors into a repair unit.
- [x] P66-14: Tests prevented from starting by compilation failure are explicitly
      not executed because compilation failed, never failed/canceled/passed tests
      or a successful empty selection. Preserve actually executed results in
      aggregate builds; ambiguous execution remains unknown.

## D2: incremental information interaction

- [x] P66-04: An actual issued diagnosis requests multiple relevant details in
      one batch and receives managed Evidence/File/Resource results through the
      same Session/work continuation, without ProcessExec file access.
- [x] P66-05: Coverage and unavailable items are returned explicitly. Broader
      context and full available evidence remain accessible when needed.
- [x] P66-06: Delivered references/context are reused; a replacement worker gets
      the facts it needs. Reads add no repair count, approval/review gate or SBT replay.

## D3: repair and focused validation


- [x] P66-16: D3 reuses the qualified RRC runtime controller for convergence and
      the ten-cycle ceiling; no AI-authored one-run restriction or per-failure
      reapproval. Reuse [RRC evidence](runtime-repair-control-correction.md)
      without repeating its full acceptance; preserve the existing Session/counts.

- [x] P66-07: AI cause/evidence/scope/dependency/verification judgments produce one
      concrete Fix Manifest admitted through the existing repair assignment path.
- [x] P66-08: Actual focused behavioral tests pass after that repair and the
      workflow returns to normal admission; compile-only/empty selection is insufficient.
- [x] P66-09: Remaining findings can revise the diagnosis while retaining earlier
      evidence and consumed repair counts; no automatic worker-per-test split.
- [x] P66-15: A representative compilation failure follows diagnosis, additional
      information and repair to successful normal compilation and actual focused
      behavioral-test execution. Compile-only success is insufficient; subsequent
      test failure returns to the same repair path with prior evidence and counts
      retained. Cover both compilation scopes and mixed execution proportionately,
      reusing focused fixtures rather than duplicating the entire integration run.

## D4: integration and adoption


- [x] P66-10: One connected failure → information round trip → repair → focused
      validation path is accepted, with representative unavailable/partial cases
      covered proportionately. Existing review and full/heavy policy is retained.
- [x] P66-11: Normal generation, affected callers, helper/skill distribution and
      delivery agree on the actual public contract; source-only and isolated
      evidence are not mislabeled as live deployment or consumer acceptance.
- [x] P66-12: Adoption preserves Session/work identities, accepted work, history,
      repair counts and original success/failure/UNKNOWN results. English handoff
      records actual usable capability and remaining limits without replay/reset.

## Additional conditions reconciled against retained execution evidence

P66-17/P66-18 were later producer planning additions; they are not extra
acceptance gates in native Plan1 or additional ReviewPhase1438 judgments.
The [2026-10-26 evidence reconciliation](../journal/2026/10/2026-10-26-phase-66-p66-17-18-evidence-reconciliation.md)
confirms that both were already performed. Their unchecked state was a stale
documentation projection, not remaining implementation or successor work.
No Session operation, Plan amendment or successful execution was repeated.

- [x] P66-17: Consume [SHR-C2–5/7](source-history-recovery-result-collection-correction.md)
      for source-history interruption within existing authority, including public
      adoption of a retained pending Human question. Keep D1/D2, accepted D3 repair,
      original UNKNOWN/question, Session and repair count 1/10; no new approval
      solely for missing comment-update receipt or caller-side bypass.
      Evidence: public description017 retains Human226 and eight prefix results;
      prepared evidence018 and adopted result019 use the existing policy direction.
      Ordinary Advance020 passed Compile, lint and four focused tests at counts1/10;
      Advance022 retains D1/D2, commits D3 as `38b53fa5` and issues D4.

- [x] P66-18: Consume [SHR-C1/6](source-history-recovery-result-collection-correction.md)
      to recover attributable completed results after caller/model interruption.
      Report publication, activation, installed qualification and consumer adoption
      separately; retain/reuse successful executions without replay or periodic AI
      monitoring. Missing final chat response alone is not an incomplete execution.
      Evidence: all nine known RRC command receipts match their original terminal
      results and SBT summaries, including retained failures; original publish01
      and installed03 succeeded with exit0. RRC and SHR publication records each
      show Completed/Activated; SHR installed01 passed nine isolated cases.
      Actual consumer adoption019 and continuation020/022 establish live use,
      separately from producer qualification. See the reconciliation for locators.
