# Configurable automatic commit hygiene: development handoff

Allocation adopted later on 2026-10-26: [Phase 6.8 C3/C4](../../../phase/phase-6.8.md)
owns this work after active Phase 6.7 and before Phase 7. The
[combined target contract](../../../spec/commit-completion-and-recovery.md)
also covers authority and recovery. This allocation adds no active P67 gate
or implementation/qualification claim.

## Decision and delivery status

The user wants mechanically repairable commit-precheck findings to be repaired
automatically by default, with a property that instead makes them errors without
modifying files. The motivating incident is Git's `new blank line at EOF.`
diagnostic on a CML file immediately before commit.

This is a development handoff, not an implementation or validation completion
record. Only this journal document is added by this task. Existing plans, code,
Sessions and validation results are unchanged. Implementation belongs to the
sm-workflow development owner; do not interrupt ongoing publication-route work
or restart an active Phase to adopt this handoff.

## Existing foundation

- [Normal typed Git workflow](../../../spec/normal-typed-git-workflow.md)
  already describes bounded terminal-LF correction with reuse of applicable
  validation and review results.
- `GoalPhaseNativeClosure` currently saves a nonclean selected-index precheck
  and returns without committing. A later Advance can accept an external EOF
  correction using `admitEofCorrectionC` / `admitTerminalLfChangesC`.
- CNCF's typed `GitIndexCheck` exposes EOF paths and an EOF-only classification.
- The installed `cncf-commit-execution` skill has an existing
  `scripts/repair_eof_blank_lines.py` helper, while its commit-runner contract
  forbids automatic rewriting. These are reference material for behavior and
  tests; do not introduce a shell/Python repair subprocess into sm-workflow.

The missing behavior is runtime-owned repair within ordinary commit preparation,
without a routine return to AI for editing and another Advance just to remove
terminal blank lines. Existing rules requiring an earlier failed Advance must be
updated to allow the actual current precheck to supply the same finding evidence.
Do not fabricate a prior failure or delete historical failures.

## Configuration

Proposed public property:

```properties
sm-workflow.commit.hygiene.mode = auto-fix
```

| Value | Behavior |
| --- | --- |
| `auto-fix` | Default when omitted. Apply supported mechanical corrections, recheck, then commit when clean. |
| `error` | Report the finding and stop commit preparation without hygiene edits or a commit. |

Expose this through the normal sm-workspace configuration and sm-workflow typed
parameter resolution. Reuse existing configuration precedence. An unsupported
value is a configuration error, not an implicit fallback. Record the effective
mode with the operation result. Both modes retain the integrity check; neither
means ignoring a finding.

## Main path and ownership

```text
selected commit candidate
  -> typed integrity precheck
  -> clean: ordinary commit
  -> supported finding + auto-fix:
       managed file correction -> update selected staging -> precheck -> commit
  -> error mode or unsupported finding: structured diagnostic, no commit
```

sm-workflow owns mode resolution, repair selection, application, result retention
and continuation. Skills display the outcome and return only genuinely issued
semantic work. No per-finding AI worker, independent repair script, additional
human confirmation or incident-specific exception is needed for the supported
correction within the existing authorized scope.

Use the existing managed filesystem operations and CNCF typed Git operations.
Share the policy and correction implementation across Step, release and
RepositorySync commit preparation, while retaining each workflow's existing
commit/merge semantics. First prove the selected Step path, then integrate the
other callers; do not make a new general formatter framework a prerequisite.

## First supported correction

Support removal of extra terminal LF blank lines, retaining the terminal newline
and every other byte and the file mode. Start with the existing narrow EOF-only
contract and CML incident. Do not generalize it to arbitrary line-ending changes,
trailing-space removal, indentation, formatting or semantic edits. Add other
corrections individually when their meaning and acceptance conditions are known.
Files whose terminal bytes are intentional data or test expectations are not
automatically covered merely because Git reports whitespace.

Only selected, authorized paths are eligible. Preserve unrelated staged and
unstaged changes, including partial staging: never stage an entire file in order
to capture a hygiene correction if that would include unrelated edits. Use the
existing selection semantics; when they cannot preserve that distinction, return
the specific limitation without widening scope or overwriting user changes.

Perform one correction pass followed by the ordinary integrity recheck. If
findings remain, return their actual diagnostics rather than looping or declaring
success. Re-entry uses retained execution facts and actual Git state: recover a
completed commit result, and do not replay an unknown commit effect.

## Evidence, acceptance and preservation

- Record detected findings, effective mode, affected paths, applied correction,
  recheck outcome and actual commit outcome through existing Journal/Evidence
  operations. Keep bulky raw logs outside Git. Successful normalization is an
  ordinary preparation outcome, not an AI approval gate or a new ledger.
- Preserve original validation/review targets and use the existing bounded EOF
  correction relationship to the committed candidate. Do not relabel old test
  results as executions against changed bytes.
- For this mechanically established correction alone, reuse applicable compile,
  selected-test and review evidence. Do not request another AI review or full
  test run. A semantic change continues through the normal affected checks.
- Do not charge a mechanical hygiene correction to the product FIX counter or
  change convergence/hard-limit policy. Preserve all existing repair counts.
- Keep Goal, Session, original base, accepted work, failure history, successful
  validation and pending Git work. Do not re-Start, replay an accepted Submit,
  reset counters, discard changes or rewrite historical receipts.
- Align contradictory skill/common-commit wording for runtime-owned preparation.
  The leaf commit executor need not gain arbitrary file-edit permission: the
  runtime normalizes the candidate before handing it to the existing commit path.

## Implementation order and focused acceptance

1. Add typed mode resolution, defaulting to `auto-fix`, and document the property
   in the existing configuration reference.
2. Connect the current EOF finding and bounded correction to selected Step
   preparation. Retain real precheck evidence and continue within the same
   Advance when repair and recheck succeed.
3. Verify the complete path: CML extra terminal blank line -> automatic correction
   -> clean recheck -> exactly one commit, with no AI or Human round trip.
4. Connect release and RepositorySync to the same preparation behavior; retain
   selected-path and merge-parent semantics and reuse their existing tests.
5. Align specifications, the additive development plan, skill guidance and the
   deployment/adoption handoff. Reuse existing implementation and evidence.

Focused acceptance cases:

- Property absent: the supported CML EOF finding is repaired and committed.
- Explicit `auto-fix`: same outcome; a clean candidate causes no file rewrite.
- `error`: finding returned, file/index bytes unchanged by hygiene, no commit.
- Invalid mode: ordinary configuration diagnostic.
- Only the selected supported change is staged; unrelated and partially staged
  work is preserved. Unsupported or meaning-sensitive findings are not rewritten.
- Recheck failure does not commit or repeat repairs indefinitely; retained facts
  distinguish successful correction from a remaining problem.
- Applicable tests/reviews remain reusable and counters/history remain intact.
- Completed commit result collection does not create another commit.
- Step, release and RepositorySync honor the same mode without changing their
  native commit/merge behavior.

Do not rerun full validation or invoke AI merely to certify this handoff. Product
verification belongs to implementation of the above behavior, with tests selected
for the changed boundaries.
