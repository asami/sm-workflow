# Standalone fix runtime entry

Date: 2026-10-26 (Asia/Tokyo)
Status: P67-T1 source implementation; installed behavioral acceptance remains pending.
Contract: [standalone acceptance](standalone-fix-acceptance.md).

`StandaloneFixService` owns one bounded single-repository test repair. It has its
own derived `StandaloneFixWorkflow`, canonical CNCF instance and WorkOrder
continuations. It creates no GoalPhase Plan, synthetic Step or Step commit.
`StandaloneFixNativeRun.Host` is a neutral capability view over configured storage,
repository, fixed Git path ordinals and typed SBT check bindings. The normal
multi-workflow factory projects its existing same-repository bindings into this
view; the business input and its recorded selection contain no Phase number or
Plan file. Matching host programs do not transfer another Session's authority or
accepted checks.

## Public route

1. `NativeWorkflowPreparationService.prepareStandaloneFixStart` reads a complete
   `StandaloneFixInput` from the normal Resource context and prepares the existing
   finite selection import. Its preparation DTO reuses the repository preparation
   fields: input URI, selection identity, participant, ExplicitHuman origin,
   idempotency key and selection collection. Preparation grants no authority.
2. `StandaloneFixService.startStandaloneFix` consumes that separately provisioned
   selection and the exact same input, plus selectionId, participant and
   idempotencyKey. The actual actor, managed store and canonical session routing
   remain owned by CNCF. Each requested path must be a fixed configured host path;
   each requested check must resolve exactly to the configured typed binding.
   Before creating a new native instance or running repository/check effects,
   Start requires both the actual execution profile mapping and origin. The
   adopted code/documentation kind derives CodingProgrammingStandard or
   DesignStandard; possible independent review requires ReviewStandard. Missing
   classes, invalid origins and incompatible forced-inline/isolation preferences
   are rejected before Start. Fresh issues retain the resulting disposition.
   This admission does not rebind an already retained pending WorkOrder.
3. `advanceStandaloneFix(sessionId)` owns candidate observation, selected managed
   checks, diagnosis, semantic WorkOrder issue and completion assessment. Requests
   return their actual issuedWorkContext, submit operation/session and result type.
   Candidate and validation intents are retained before their effects. A loaded
   unknown intent returns Wait and is never replay permission. Confirmed SBT
   admission rejection retains nonexecution and the unconsumed selection; a later
   normal Advance may continue corrected preparation without resetting counts.
4. An actual repair worker returns `StandaloneFixWorkResult` through
   `submitStandaloneFixWorkResult`, with its real result reference and worker
   identity. Configured execution disposition is admitted by common CNCF machinery;
   absence of the profile/origin pair creates no executor observation. Submit closes
   only that actual WorkOrder. A separate normal Advance captures the new candidate
   and validates its bytes.
5. `resolveStandaloneFixDiagnosticInformation` accepts the existing finite
   diagnostic-information DTO against the exact pending standalone WorkOrder. The
   historical GoalPhase DTO name does not create a Phase. Shared selection,
   same-call source-read caching, ownership checks, immutable Evidence delivery,
   Journal history and known-Evidence suppression are used directly. Information
   requests neither complete work nor consume a repair batch.

A code request selects Compile and an actual nonempty focused TestOnly check,
plus FixCheck when Scala lint is required. Test success is a typed managed result
with completed suites and positive executed test count. Exit zero, compilation
alone, empty selection and incomplete capture cannot establish behavioral success.
Compilation failure retains the actual diagnostic and records affected tests as
not executed, with no invented zero or PASS count.

## Assessment and completion

The shared deterministic check gate is also used by GoalPhase Step assessment.
The existing Fix classification predicate and runtime repair-trend policy retain
their meaning. One completed repair increments the existing automatic batch count;
classification may exempt the ordinary count but never renew the ten-batch ceiling.
Convergence stops and exhausted allowance return Human with retained evidence.

The adopted `reviewRequired` policy and remaining semantic questions determine
independent review. Settled local repair has no review or review-waiver AI call.
An unresolved expected value, selection/suppression change, public behavior or
combined effect must be reported as an unresolved semantic question; changed file
count is not an automatic review ceremony. A required review's exact Work/Result,
component, completion identity, result reference and actual independent worker
are admitted through the existing trusted external-evidence reader under purpose
`standalone-fix-independent-review`. The returned reviewContext is consumed by
normal `prepareEvidence` with family `StandaloneFix`, kind `review`. The repair
worker cannot be its own reviewer. Follow-up review retains prior findings and
candidate and judges the repair delta and affected meaning. Unknown or missing
provenance does not become accepted review.

Accepted standalone changes remain uncommitted, including any selected index
staging. There is no commit, push, release, Phase Review or Release Review operation
in this route. The entire adopted condition list must be satisfied. Initially
passing checks yield a distinct NoChange state without inventing a semantic repair
or condition assessment. A byte-identical candidate reuses its complete applicable
successful check batch. Changed bytes receive fresh selected checks. All old
checkpoints, failures, unknown intents, results and counts remain in canonical
history. Terminal redisplay executes no validation or repository operation.

The initial route refuses unrelated dirty paths before staging, preserving them
without effects; extended dirty/focused selection belongs to Phase 17. Genuine
scope or effect expansion returns Human rather than silently widening the host.
Existing active Phase repairs remain in their existing Session.

## Evidence boundary

`StandaloneFixWorkflowSpec` is a bounded ADMISSION suite. Its receipts and source
reader are explicitly fixtures, covering progression, compiler-prevented tests,
nonempty focused success, conditional review, reuse, limits and shared information
selection. They do not prove an installed SBT process ran or that P67-01/P67-08
have been accepted. Real failing-test/compiler repair and installed CLI/skill
adoption remain the separately owned Phase acceptance work. No checklist is
checked by this source implementation.
