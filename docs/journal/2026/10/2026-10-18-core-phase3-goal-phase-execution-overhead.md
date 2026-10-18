# Core Phase 3: execution overhead in the current cncf-goal-phase workflow

Date: 2026-10-18 (Asia/Tokyo)
Status: observed workflow problems and improvement criteria; no implementation change

## Purpose and evidence limits

The owner asked whether sm-workflow improves the excessive development time
observed while implementing goldenport-core Phase 3. This machine does not have
the latest sm-workflow. Therefore this entry records the problems in the current
cncf-goal-phase execution and the behavior to check against the latest product;
it does not claim that sm-workflow has, or has not, already solved them.

The case is Record construction/normalization and ordinary codec migration in
`simplemodeling-lib` (GitHub repository `asami/goldenport-core`), developed in the
`SimpleModeling開発` chat. Evidence consists of its reported execution sequence,
local SBT output, native Git history and Phase 3 acceptance documents. This is
a bounded observation, not a complete time study. Reported agent actions are
distinguished from command timings. Private transcripts and execution artifacts
are not copied into this journal.

The concern is time to deliver an integrated usable change. Neither the number
of tests nor the number of completed procedural states alone measures progress.

## Observed problems

### 1. Repeated short validations incur much larger shared-queue waits

Five inspected SBT invocations reported the following final waiting counters:

| Invocation | Reported wait | SBT task time reported after startup |
| --- | ---: | ---: |
| representative-001 | 21 minutes | 27 seconds |
| representative-002 | 6 minutes | 25 seconds |
| accumulator-001 | 14 minutes | 2 seconds |
| accumulator-002 | 33 minutes | 8 seconds |
| RCA-03 representative-001 | 26 minutes 30 seconds | 33 seconds |

The wait counters sum to **100 minutes 30 seconds**. They are sampled counters,
not exact end-to-end durations; SBT task time excludes some startup and orchestration
cost. These observations must not be described as 100 minutes of test execution.
Other development work was sharing the machine's serialized SBT route.

The global lock is an existing execution policy, not a Core defect. However,
splitting a connected change into repeated invocations repeatedly exposes it to
that queue. Making individual tests faster does not address the dominant cost
in these examples. Runtime ownership alone also does not eliminate resource
contention.

### 2. Incidental representation assertions create avoidable repair cycles

One RCA-03 run executed 60 tests: 58 passed and two failed. The reported failures
compared HOCON object field order and the whitespace of an empty JSON array
(`[]` versus a formatted empty array). The owning chat identified these as
assertions exceeding the intended semantic contract. A later YAML empty-object
line-format assertion also required correction after semantic comparison passed.

The problem is not that failing tests should be ignored. Specifications should
assert the required observable contract. Where formatting or order is not a
requirement, incidental renderer output should not create a product blocker.
Known assertion defects should be corrected together before the next selected
run, rather than discovered and repaired one trivial assertion at a time.

### 3. Small repairs receive disproportionate orchestration

The owning chat reported delegating a one-line YAML assertion removal to a
dedicated fix worker and preparing another test execution handoff. Worker
selection, context preparation, handoff, command admission, result collection
and repeated status reporting then surround a very small edit.

This is evidence of procedural amplification, not proof that every such action
was required by one particular line of the skill. Independent review of a
substantive change remains valuable. A trivial repair does not automatically
benefit from a separate implementation agent, and deterministic command work
does not require AI reasoning at each handoff.

### 4. Selected-run administration adds work without increasing coverage

The chat also reported a misspelled suite name in a selected 13-suite invocation,
followed by a supplemental three-test run. The omitted coverage still needed
execution, but the extra invocation was caused by command selection/transcription,
not by another product defect. Repeated manual reconstruction of selected
commands makes such clerical failures more likely.

### 5. A useful review can coexist with an inefficient delivery process

Independent review found a real HOCON large-integer round-trip defect. Its repair
passed 104 tests across four suites and a focused independent re-review. Earlier
RCA-01/02 validation recorded 39 representative tests and 123 related tests;
RCA-03 selected validation recorded 176 tests across 16 suites. These overlapping
counts must not be summed into a unique coverage total.

At inspection, RCA-01/02 and RCA-03 had accepted Step commits (`2150f93` and
`dc14bf9`), while RCA-04/05 and Phase closure remained open. This shows substantive
progress and the value of review; it does not justify all surrounding overhead
or establish that the Phase was complete.

## Implications to check in the latest sm-workflow

1. **Deliver a connected route as a coherent batch.** Plan implementation and its
   executable specifications together. Repair known failures together where they
   belong to the same scope. Avoid a new delivery/review cycle for every helper,
   assertion or bookkeeping edit. Early focused feedback remains useful when it
   resolves a real implementation uncertainty.
2. **Own deterministic execution in runtime.** Select and execute SBT/Git/other
   commands, collect their results and advance the workflow without asking AI to
   transcribe command identities, logs or mechanical state transitions. Delegate
   semantic work when model capability, cost, context isolation or independence
   warrants it; do not require a new worker for every trivial repair.
3. **Use proportionate validation and review scope.** Explain which checks a
   repair affects and reuse applicable results. Do not restart comprehensive
   testing or review solely because a lifecycle label changed. Keep actual
   integration obligations and independent review, including checks capable of
   finding the large-integer defect in this case. Do not introduce hashes,
   copied-file comparisons, TTL gates or unchanged-content proof ledgers.
4. **Separate waiting from execution in feedback.** Existing timing evidence
   should distinguish queue wait, startup/build, test execution and AI/process
   overhead where available. Unknown intervals remain unknown. Do not add test
   runs merely to manufacture measurements. Developers can use the feedback to
   ask AI to reorganize batches or plans; automated replanning is not assumed.
5. **Demonstrate end-to-end progression.** A successful Submit should lead to
   display and the next Advance without a human prompt or model turn whose sole
   purpose is continuation. An execution-procedure failure should be reported
   separately from a product test failure.

The intended product policy delegates SBT resource exclusion to SBT and its
dependency tooling; this record does not authorize bypassing the current
machine-wide wrapper or changing active development commands. Any latest-product
assessment must distinguish the implemented product policy from the rules used
to develop that product today.

These are improvement criteria for the existing integration work, not new
mandatory per-repair gates, a new monitoring subsystem or a prescribed count of
test invocations. Use this concrete incident to assess whether the ordinary
workflow actually reduces time and AI work while preserving meaningful quality.

## Related records and local source locators

- [Phase 96 delivery friction](2026-10-17-phase96-delivery-friction-sm-workflow-review.md).
- [Actual NICT development skill failures](2026-10-17-nict-development-skill-failures.md).
- [Staged quality review responsibilities](2026-10-13-staged-quality-review-matrix.md).
- Owning chat: `SimpleModeling開発`, `01a0b935-a7ef-7fc3-8b56-cdc1c10d08c1`.
- Core checkout on this host: `/Users/asami/src/dev2025/simplemodeling-lib`;
  `docs/phase/phase-3.md` and `docs/phase/phase-3-checklist.md`.
- Local `.codex-workflow/disposable/` execution directories, in the table's order:
  `a-7e4c7cf2-8b1c-4109-9099-7f0c11aece7c`,
  `a-d8dcf749-f352-46aa-a29e-e91f41c22c70`,
  `a-3881c16e-dfa5-4201-a8d5-cb112b9a179c`,
  `a-d9988449-322e-42fc-98a8-2f17a38d21cf`,
  `a-9dc32acf-42f3-4549-9412-1e43736ea678` (`stderr.txt` and `stdout.txt`).
- Installed shared procedure:
  `/Users/asami/.agents/skills/cncf-goal-phase/SKILL.md` and its
  `references/connected-step-delivery.md`, plus `cncf-command-execution`.
  The current goal skill already calls for a connected usable route without
  artificial turn boundaries. Observed friction therefore cannot be reduced to
  a claim that its current text explicitly mandates every inefficient action.

Only this journal was added. No product/shared-skill code, Phase acceptance,
active task or execution policy was changed. No tests were rerun.
