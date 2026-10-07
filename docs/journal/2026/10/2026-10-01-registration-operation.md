# GoalPhase registration Operation

Continue active Phase 2 in task 01a0a6d7-7043-73a2-a808-1367fe5fdb1f, /root.
Previous turn was progress: managed actor/Create/readback/retry/reopen passed
145 tests in nineteen suites. Retain explicit parent-direct limited recovery
and `回避して` for skill-only execution-route stops, not platform review,
typed allocation/permission, normal caches or machine-wide SBT serialization.
No commit/publication authority or new producer root. Preserve other changes.

## Frozen implementation boundary

IMPLEMENT-REGISTRATION-OPERATION-1 owns this journal and:

- src/main/cozy/sm-workflow.cml
- src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseRegistrationServiceOperation.scala (new)
- src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseSubmissionRegistration.scala (derived receipt facts only)
- src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseManagedComponentFactory.scala (configured host service composition only)
- src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseManagedSuspensionOperationSpec.scala (new public dispatch scenario)

All under /Users/asami/src/dev2026/sm-workflow. CNCF unchanged SNAPSHOT from
528 is validation dependency only. Generated sources/resources are build output,
never hand-edited. No changes to the Workflow state graph or existing provided
operations; registration is a standalone pre-review Service command.

Declare GoalPhaseSubmissionRegistrationCommand with registrationIdentity,
submissionIdentity, idempotencyIdentity and typed original Start command.
Declare a derived receipt Value and a result with schemaVersion and receipt;
no caller-supplied WAIT, receipt, authority, storage destination or human flag.
Add GoalPhaseService.registerGoalPhaseSubmission to CML. New Value declarations
are appended, preserving existing Workflow source locations/ABI projections.
The wire schema is sm-workflow.goal-phase-registration-command.v1, exactly
schemaVersion plus those four request fields. Reject extra/missing/noncanonical
input before any ActionCall. Nested Start is decoded by its existing codec and
all its identifiers must be canonical. Output schema is
sm-workflow.goal-phase-registration-result.v1 and the receipt facts from the
actual loaded registration returned by managed registration, not prepared JSON.

Bind only through configuredHostRecordsC. During pure composition derive the
fixed Association-purpose collection from the resolved host snapshot and require
the Workflow store and disjoint infrastructure as existing readers do. Runtime
calls use their current core, call-scoped persisted selection, original Start
and framework-issued Create capability. Absence of a Create grant denies the
call, not a Factory-time installation or implicit grant. No pool or data is
created by this operation's factory binding itself.

Specs: actual public command through Subsystem dispatch, real local ingress in
the current context, explicit captured grant, closed request rejection, same
receipt on retry and after reopened host, foreign actor refusal. A test-only
selection installer may establish the existing selection prerequisite but is
not human attribution evidence. The production command writes registration;
no requestStepClose registration probe substitutes. Use AnyWordSpec/Matchers
and adjacent Given/When/Then. Package generated CML and run nineteen-suite
accumulator. Halt on generator/schema disagreement or necessary public policy
change rather than silently weaken the contract.

Parent /root, model/effort not directly observable, standard, direct disclosure
before edits; bounded-settled realization of admitted registration contract.
No full Phase PLAN completion, acceptance or release. Concurrent registration,
fault-injected observation, independent review, launcher/CLI/server activation,
controlled commands, human entry authority and skill packaging remain required.

## Compile correction

Invocation SMWF-REGISTRATION-OPERATION-DIRECT-20261001-533 generated 68 Scala
sources successfully but failed compilation at the new adapter's use of
OperationDefinition.Specification.copy: that API is an abstract holder, not a
case class. No tests ran. Both exits 1, lock released, full summary read:
/tmp/skill.cncf.d/a-e1f1ce09-405a-4a5c-821f-d7098769ca40/28716-20261001T062609Z.summary.json.
Freeze FIX-REGISTRATION-SPECIFICATION-1 to the adapter's specification assembly
and this journal. Use the public Specification constructor with the same name,
JSON request and generated response, not an assumed copy method. No CML,
semantic or admission change. Parent /root direct under retained recovery,
model/effort not directly observable, standard. Rerun the same accumulator.

## Verified result

Invocation SMWF-REGISTRATION-OPERATION-DIRECT-20261001-534 compiled/packaged
the generated CML and passed 146 tests in nineteen suites, zero failures,
aborts, canceled, ignored or pending. Both exits zero, lock released. The full
summary and public registration scenario log section were read:
/tmp/skill.cncf.d/a-58aed81d-d871-4057-8a3b-c24e631e5632/29209-20261001T062706Z.summary.json.
533 and 534 appended to the retained ledger; both sessions terminal. No edits
during SBT. git diff --check passed.

CML generation produced the three new Value sources and Service Operation.
Existing Workflow/source ABI admission remained green without hand-editing its
fixtures or changing the state graph. The configured host factory appends the
bound registration command using its resolved Association-purpose collection;
the historical explicit-port factory remains unchanged. The adapter rejects
unknown/missing/noncanonical request fields before constructing an action,
then invokes managed registration with the dispatch core and returns only the
observed record's derived receipt under the declared result schema.

The actual public Operation scenario first establishes its selection/Start
prerequisite with an explicitly test-only installer. The configured host then
performs registration through registerGoalPhaseSubmission, not the private
requestStepClose probe. Equal retry and a separately reopened configured host
return the identical receipt, history remains at Start, foreign actor and
extra/noncanonical input are rejected, controlled-test default ingress cannot
register, and stored bytes cannot authorize retry without a captured Create
grant. Fixture selection is not independently proven human attribution.

Still required: concurrent writers and fault-injected Create/readback outcomes,
independent review, real launcher/CLI/server/MCP activation and common-pool
end-to-end configuration, separate human entry contracts, controlled external
commands, distributable sm-* skills and full Phase assurance/release. No commit
or Phase completion claim.
