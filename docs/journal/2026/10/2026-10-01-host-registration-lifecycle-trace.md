# Host registration and completion lifecycle trace

Continue active Phase 2, parent-direct limited recovery. The preceding turn
recorded an installation candidate. Inspection now disproves its proposed
association-at-result placement: requestStepClose resolves the association
before issuing review. Installing it at submitStepReview would make the first
review impossible without a fixture or a circular authority assumption.

Source evidence inspected:

- GoalPhaseStepCloseServiceOperation.BoundCall.execute resolves submission to
  Start, verifies active Awaiting instance, then invokes managed suspend.
- WorkflowCompletionServiceOperation checks issued work/operation and exact
  current instance before managed completion.
- WorkflowProtocolV1.admitResultC checks protocol correlation and payload
  evidence; it does not bind the caller to an authenticated actor.
- WorkflowManagedCompletion enters the owning Subsystem's verified managed
  space. WorkflowCompletionDataStorePersistence enlists prepared closing local
  effects with the native completion transaction.
- ManagedCompletionParticipant is a transaction coordinator, not a submitting
  person's or AI agent's identity. Subsystem.admitWorkflowStorageC admits the
  declared root/Continuation pair, not arbitrary association/receipt writers.

Correct the [installation candidate](../../../notes/goal-phase-host-record-installation.md)
to include pre-review registerGoalPhaseSubmission and separate authenticated
actor admission from transaction participation. No new public Operation is
exposed. No alternate result store, fixture writer, postcommit association write,
new pool or guessed human authority is introduced.

Next implementation needs an explicit submitting-actor binding, host-derived
retained Start provenance, Create-only collection grant and atomic registration
receipt. Completion retains its existing transaction and result record, but
its caller binding must be proven separately before default transport activation.

Own only the corrected note and this journal. Documentation-only validation:
git diff --check passed. No SBT was run, no previous tests are claimed as proof
of these missing contracts, and no Phase acceptance/commit/closure is asserted.
