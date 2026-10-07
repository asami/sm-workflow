# Native Decision consumer: artifact and typed answer payload

Continue the complete sm-workflow Phase 2. Previous turn made progress with
the CNCF declared Decision completion Service (131/131 tests, invocation 595).
This turn refreshes that exact producer dependency and supplies the consumer's
closed typed answer payload. It does not claim native pending/resume complete.

## Dependency refresh

Invocation `SMWF-CNCF-NATIVE-DECISION-ARTIFACT-REFRESH-DIRECT-20261001-596`
ran the ordinary `publishLocal` in the admitted CNCF root. Actual build wiring
exports the runtime catalog under target and publishes the unchanged
`org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT` coordinate to local Ivy.
Real information CML generation and provenance validation ran. Both exits 0,
shared lock released. The actual resulting JAR was inspected and contains
`WorkflowManagedIssuedDecision`, `WorkflowManagedDecisionCompletion` and
`WorkflowDecisionCompletionServiceOperation` classes.

Log: `/tmp/skill.cncf.d/a-167d5f19-c120-4438-935c-407faeac9305/8757-20261001T114019Z.log`.
No source version change, remote publication, shared warehouse update, commit
or push ran. This is dependency freshness, not a release acceptance.

## Consumer implementation and executable evidence

`GoalPhaseHumanDecisionContract.answerPayloadCodec` carries the existing
bounded closed answer JSON as the native `GoalPhaseHumanDecisionAnswer` typed
result. It shares existing strict encode/decode validation. Extra actor,
issuer, receipt, question or permission fields refuse; unknown syntactically
valid choices still require exact question admission. Decoding is not a human
verification capability and supplies no effect authority.

The added executable example round-trips the actual native ContinuationResult
using the refreshed CNCF dependency. It confirms Decision-only structural
admission and rejection through the WorkOrder path, null/noncanonical payload
refusal and unknown-choice refusal. Evidence in this pure example is explicitly
a fixture, never production human verification.

Invocation `SMWF-NATIVE-DECISION-ANSWER-CODEC-DIRECT-20261001-597` compiled
the consumer and passed 38/38 tests in seven suites, zero failed/aborted/
canceled/ignored/pending. Coverage includes human question/answer contracts
and storage, public human command shapes, stop-only retention, configured
Factory and managed completion binding regressions. Both exits 0 and shared
lock released; saved summary inspected. The unchanged CML was reused by the
normal sbt-cozy timestamp mechanism, not by a manually skipped generator.
Existing unchecked generic-pattern warnings remain; no acceptance waiver.

Log: `/tmp/skill.cncf.d/a-e9cbb7b3-df14-477e-a20e-9b31825e638a/9959-20261001T114230Z.log`.
Summary: `/tmp/skill.cncf.d/a-e9cbb7b3-df14-477e-a20e-9b31825e638a/9959-20261001T114230Z.summary.json`.

Parent `/root` executes directly under user-directed limited recovery;
model/effort are not directly observable. Scoped platform escalation, fresh
permission artifacts and the canonical serial wrapper remain in force.
No agent or commit ran. No owned SBT process remains live; diff check clean.

## Next native integration boundary

Current accepted-review question/answer records are observations at the current
review revision, not native Workflow suspension. Their identities and schemas
must remain immutable. CML currently declares review/repair required operations
but no human pending/resume contract. Existing `recordGoalPhaseHumanAnswer`
accepts a host-verified answer command rather than a generic native result;
it must not be silently rebound to incompatible typed ABI input.

Define the native human required operation and completion route in CML, allocate
a separate native question identity/revision, and derive suspension from the
actual current reviewed instance. Preserve exact choices/context and tie the
host-only human receipt to that new question. Use the managed native publication
and completion capabilities; do not reuse review WorkOrder metadata or separately
append instance history. Reopening must recover the exact issued question and
committed answer without verifier/semantic replay. Neither declared choice may
authorize Git or other external commands.

Those runtime bindings, real human issuer/verifier, bounded command authority,
production CLI/server/MCP integration, remaining workflows and full Phase
review/validation/release remain open. The focused tests above do not substitute
for those requirements.
