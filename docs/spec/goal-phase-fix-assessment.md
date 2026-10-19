# GoalPhase Fix assessment

The optional `GoalPhaseWorkResult.fixAssessment` records the AI's semantic claims
about one actual `RepairStep`, from either retained TEST failure or independent
REVIEW findings. It is not a command, acceptance route, scope grant or independent
verification. Initial implementation and independent Step/Phase review reject
nonempty or null assessment options. Existing ordinary results may omit the field;
absence conservatively counts as substantive work.

`GoalPhaseFixAssessment` has required `change` and `convergence` values. Change
contains `changeClass`, `affectedLogicalUnits`, `crossFileLogic`, `minorBugFix`,
`rationale`, `requirementChanged` and `contractChanged`. Its exact class vocabulary
is `HYGIENE`, `TRIVIAL_COMPILE_FIX`, `SIMPLE_LOGIC`, `STANDARD_LOGIC`,
`COMPLEX_LOGIC`. Affected logical units are nonempty, distinct and limited to 128,
each at most 1024 UTF8 bytes. Rationales are nonblank, NUL-free text of at most
16384 UTF8 bytes. Null reports, nested values, text and option containers reject.
Text is retained as supplied, without truncation or normalization.

Physical file count does not determine semantic depth. Several independent local
units may remain STANDARD_LOGIC across several files. Coordinated cross-file
logic requires COMPLEX_LOGIC, and COMPLEX_LOGIC requires `crossFileLogic=true`.
A SIMPLE_LOGIC label alone does not establish a minor bug correction.

Convergence contains exact `status` (`PROGRESSING`, `STALLED`, `REGRESSING`,
`BLOCKED`), `confidence` (`HIGH`, `MEDIUM`, `LOW`) and bounded rationale. The first
three statuses are advisory. They grant no commands, reset, broader scope,
increased limit, automatic replan or automatic stop. BLOCKED is admitted only
with the actual `decision-needed` outcome. A completed BLOCKED reply rejects
before candidate inspection/check/review. The existing native Decision/Human
route retains the actual report in result history and its public display.

The selected Plan constructor defaults to a repair limit of 10. Explicit zero
disables automatic repair; an explicitly selected nonnegative bounded limit is
retained, including in captured existing Plans. No report changes that selection.
The existing automatic counter reserves one at repair dispatch, including every
hygiene/minor batch. It is never refunded by assessment. The ordinary counter also
conservatively reserves one at dispatch. Only a validated matching actual repair
completion may remove that batch's ordinary reservation once:

- HYGIENE with no requirement/contract change or coordinated cross-file logic;
- an explicit `minorBugFix=true` TRIVIAL_COMPILE_FIX or SIMPLE_LOGIC correction,
  with no requirement/contract change or coordinated cross-file logic.

STANDARD_LOGIC, COMPLEX_LOGIC, new requirements/features/contracts and mixed
substantive batches count ordinarily. The native completion owner derives the
internal `ordinaryCountExempt` event flag from admitted Request/Result contents;
it is not an exposed command parameter. Wrong frontiers/keys, implementation
exemptions, repeated completion events and impossible subtraction reject.
Native reads and Advances reconstruct the same transition through retained actual
results, without a separate counter store, hashes or copied state. Exemption
does not erase history or allow an additional automatic batch. Existing explicit
ContinueRepairs and accepted Step-commit resets retain their existing semantics.

Internal result JSON keeps `sm-workflow.goal-phase-native-work-result.v1`, omits
absent `fixAssessment`, and rejects present null/malformed reports. Public DTOs
`GoalPhaseFixChangeAssessment`, `GoalPhaseFixConvergenceAssessment` and
`GoalPhaseFixAssessment` project the same business fields. Acceptance mapping
remains explicit and separate. The report contains no counters or authority.

Labels and optimistic claims are not verification of changed source. Existing
ownership/diff checks, selected validation and independent semantic review retain
their responsibilities for every changed candidate, including hygiene/minor
corrections. One integrated independent review judges meaning-dependent GWT
placement/explanations, names, comments and examples with correctness and test
adequacy. Adopted findings are edited in the ordinary repair batch, with no
separate helper/GWT review loop. Runtime source-history maintenance remains the
existing Phase 6.1 owner's deterministic work; it must not fabricate an AI repair
or assessment and does not consume an AI repair batch.

This payload does not implement metadata discovery, issue identity/coverage,
instrumentation, statistics or another convergence engine. See the semantic
classification, convergence assessment and cycle-limit sections of
[the managed-suite contract](../notes/managed-test-suites-and-verification-cost-guard.md)
and the maintenance responsibilities in [Phase 6.2](../phase/phase-6.2.md).
