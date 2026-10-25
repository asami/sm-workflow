# Phase 6.6 Checklist: Incremental SBT Failure Diagnosis and Repair

Status: planned
Phase: [Phase 6.6](phase-6.6.md)

All P66 outcomes are pending. The prior caller-size-limit removal is a completed
baseline, not new Phase 6.6 acceptance. Reuse actual earlier results within their
scope; source inspection here does not certify a new execution.

## D1: retained failure and structured diagnostics

- [ ] P66-01: One real retained SBT failure is associated with its actual check,
      candidate and available Evidence without rerunning the failed command.
- [ ] P66-02: The initial view includes available assertion values, exception
      causes, relevant stack/source locations and surrounding context. Exact
      duplicate grouping retains occurrences and does not assert common causation.
- [ ] P66-03: Source loss/truncation, summary omission, incomplete extraction and
      unavailable evidence are distinct. Suite/setup/compiler failures do not
      invent test outcomes; ambiguous attribution remains explicit.
- [ ] P66-13: Compilation errors encountered during SBT test/focused execution
      include available project/configuration, compiler message, file/line/column,
      source context and type details. Production and test-source compilation
      are both covered; AI can request related declarations/call sites and group
      root/cascading errors into a repair unit.
- [ ] P66-14: Tests prevented from starting by compilation failure are explicitly
      not executed because compilation failed, never failed/canceled/passed tests
      or a successful empty selection. Preserve actually executed results in
      aggregate builds; ambiguous execution remains unknown.

## D2: incremental information interaction

- [ ] P66-04: An actual issued diagnosis requests multiple relevant details in
      one batch and receives managed Evidence/File/Resource results through the
      same Session/work continuation, without ProcessExec file access.
- [ ] P66-05: Coverage and unavailable items are returned explicitly. Broader
      context and full available evidence remain accessible when needed.
- [ ] P66-06: Delivered references/context are reused; a replacement worker gets
      the facts it needs. Reads add no repair count, approval/review gate or SBT replay.

## D3: repair and focused validation

- [ ] P66-16: D3 reuses the qualified RRC runtime controller for convergence and
      the ten-cycle ceiling; no AI-authored one-run restriction or per-failure
      reapproval. Reuse [RRC evidence](runtime-repair-control-correction.md)
      without repeating its full acceptance; preserve the existing Session/counts.

- [ ] P66-07: AI cause/evidence/scope/dependency/verification judgments produce one
      concrete Fix Manifest admitted through the existing repair assignment path.
- [ ] P66-08: Actual focused behavioral tests pass after that repair and the
      workflow returns to normal admission; compile-only/empty selection is insufficient.
- [ ] P66-09: Remaining findings can revise the diagnosis while retaining earlier
      evidence and consumed repair counts; no automatic worker-per-test split.
- [ ] P66-15: A representative compilation failure follows diagnosis, additional
      information and repair to successful normal compilation and actual focused
      behavioral-test execution. Compile-only success is insufficient; subsequent
      test failure returns to the same repair path with prior evidence and counts
      retained. Cover both compilation scopes and mixed execution proportionately,
      reusing focused fixtures rather than duplicating the entire integration run.

## D4: integration and adoption

- [ ] P66-10: One connected failure → information round trip → repair → focused
      validation path is accepted, with representative unavailable/partial cases
      covered proportionately. Existing review and full/heavy policy is retained.
- [ ] P66-11: Normal generation, affected callers, helper/skill distribution and
      delivery agree on the actual public contract; source-only and isolated
      evidence are not mislabeled as live deployment or consumer acceptance.
- [ ] P66-12: Adoption preserves Session/work identities, accepted work, history,
      repair counts and original success/failure/UNKNOWN results. English handoff
      records actual usable capability and remaining limits without replay/reset.
