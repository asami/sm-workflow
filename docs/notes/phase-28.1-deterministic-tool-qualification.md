# Phase 28.1 deterministic Scala tools qualification

This record tracks the tool inputs and remaining packaged qualification for
[Phase 28.1](../phase/phase-28.1.md#p281-03--verify-and-deliver-the-packaged-workflow).

## Tool behavior observed in isolated samples

- Scalafmt 3.8.6 reformatted single-file Scala 2.12 and Scala 3 inputs under
  the corresponding configured dialects. A second pass produced identical
  output. The Scala 3 sample kept its enclosing object braces and its
  indentation-based method body.
- In Scalafmt 3.8.6, a named file in an isolated directory respected both
  `project.excludeFilters` and `project.excludePaths` with
  `--respect-project-filters`. Its stdout contained formatted source for
  included files, and excluded files returned `NoMatchingFiles`. The same
  flags with stdin still formatted an excluded file. These observations
  select the named-file preview route. The original source stayed unchanged
  in the named-file preview.
- Scalafix 0.12.1 produced the explicit `: Unit =` rewrite for
  `ProcedureSyntax` and dropped the redundant `final` modifier from a
  configured `RedundantSyntax.finalObject` sample. In both cases the
  read-only check returned the expected one-file diff. The unchanged
  control returned exit 0 with no diff.
- On the Scala 3.3.8 compiler output, Scalafix 0.12.1 sorted two named
  Scala imports with matching SemanticDB. Changing the source under that
  metadata yielded exit 16 and the stale fingerprint diagnostic. With no
  corresponding metadata it yielded exit 8. These were isolated tool
  observations of the exact, stale, and missing cases.

## Packaged qualification observed

The focused suite passed 27/27 on native validation revision `1137`. Both
Scala 2.12.18 and 3.3.8 Metals cases ran through the packaged workflow.
The fixtures extract the executable companions from the candidate CAR before
registration. The combined Scala 3 case compares the
source digests observed by successful Compile calls with the candidate after
Metals and with the final formatted candidate. The compile-clean Scala 3 case
checks structural braces and a terminal indentation region and requires the
ordinary typed FixCheck to pass with `NoStructuralIndentation` selected. The
excluded-file case also tests explicit history `none` under the team profile.
Retain the results for unavailable tools, inadmissible edits, failed
revalidation and rollback. The packaged rollback scenario arranges for
Compile to reject the candidate after the diagnostic rewrite and formatter
both take effect. It checks the rejected source and the original bytes
restored before fallback. The [Phase release record](../phase/phase-28.1.md#human-approved-formal-local-release--2026-11-24)
retains the failed candidate FixCheck and the user-approved local release disposition.

## P281-08 alignment and repair candidate

The P281-03 consumer selected `ai/directive` commit
`0ad4d6762f43af97a49c69e6686c003c50cac594`, the successor of its prior
`faa09f8e2a305a74dfebbc902b96f63f98ab8b96` pin. The shared GoalPhase skill
alignment is commit `d445078b59819f8bdbeafb69a8ae3fa93df2a5f8`; its retained
alignment record reports that its paragraph matches the bundled skill. These
changes describe native ownership of managed Scala history and consumption of
the program result. The local selection and alignment evidence are retained in
`p281-08-local-alignment/phase-consumer-selection.json` and `result.json` in
the Phase task artifacts. The focused qualification is recorded above.

The previous repair prepared fixture Git read grants, explicit source-history
selection and formatter configuration, safe rejection of inadmissible formatter
previews, and the YAML null expectation for issue-7. The retained
`plan4-repair-01/semantic-result-inventory-ready.json` records that the actual
prior TestOnly run had 19 successes and 6 failures. Later revisions retain that
result alongside the successful focused run.
