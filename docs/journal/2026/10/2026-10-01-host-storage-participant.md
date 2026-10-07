# Real host storage bound to the managed participant

Status: internal source integration verified; central UoW and closing/data
enlistment remain open. Previous turn progressed with focused-379 and correction
of the mistaken approval latch. This turn changed source and executable specs.

## Implemented boundary

`WorkflowPreparedDataStoreTransition.hostParticipantC` binds the existing
effect-free Workflow pair to `ManagedCompletionParticipant`. It uses the real
DataStoreSpace host entry and its captured EventStore policy, not a native
test-double commit or direct Workflow SQLite access. Binding and every native
attempt require the prepared provider to be the identical borrowed provider
for both collections. The host, not the skill or caller, selects the event
storage route. Existing `commitHostEventsC` uses the same factored provider and
verified projection helpers; its compatibility projection is unchanged.

The native outcome comes from actual host evidence: verified readback plus
successful typed projection is Verified; actual NotMatched is proven Rejected;
ordinary failure or failed postcommit projection is Indeterminate. A generic
failure is not interpreted as rollback, even when caused by lease loss. No
failure chooses another route, retries, releases a claim or compensates storage.

The original ordinary batch is retained in process memory only. A trusted host
delivery port can receive it after frozen callbacks and cleanup succeed; the
participant clears its local batch on discard and cannot execute again. This
port remains a test observation in these specs, not actual production dispatch.
No claim is made about finalized event sequence/identity projections delivered
by a production event bus. Callback effects do not become enlisted native
closing/data effects; nonempty prepared side effects reject binding.

## Executable evidence

Five new scenario groups cover eight configurations:

- Foreign provider and unenlisted closing side effect: reject before participant
  binding, no callback/delivery/native publication.
- Escaped host lease: commit returns Indeterminate, no callback or delivery;
  a fresh scope proves the actual claim remains Claimed with no retained result.
- Real mixed retained/transient/new batch: actual native storage completes the
  typed result/history/claim and selected durable records, with no lifecycle
  actions before finish. Test-port delivery then receives the original order
  after callback and cleanup, once; the transient record is absent in storage.
- Callback and cleanup failures: actual completed storage stays verified while
  delivery is suppressed, cleanup occurs once and no compensation/replay occurs.
- Stale root versus faulty projection: actual NotMatched is Rejected with no
  completion; failed projection after actual commit is Indeterminate with the
  result still retained. Neither admits callback or delivery.

Focused-380 passed 297/297 tests in 31 suites. After adding the admission/lease
cases, focused-381 (`SMWF-CNCF-HOST-PARTICIPANT-ADMISSION-DIRECT-20261001-381`)
passed **299/299 tests in 31 suites**, zero failed, aborted, canceled, ignored or
pending. Both wrapper/SBT exits were 0/0 with lock=released. The unchanged
focused-376 exact suite vector is retained in each summary. Both logs and
summaries were inspected; repository-full validation is not asserted.

380 evidence: `/tmp/skill.cncf.d/cncf-sbt-4fe11e9f89d6c05be589e67a31c89017584ddcaa5b48e63a03c792df27f84552-dd40b22d8b0401dbe085c642ba3cf45a/65123-20260930T172121Z.log`
and adjacent `65123-20260930T172121Z.summary.json`.

381 evidence: `/tmp/skill.cncf.d/cncf-sbt-4e70f488d534087341b79ea2d224fc7af326fdbc6baafa9de3daab303ed1d288-4b4eb575bf324afa54e71dc71075bd08/65864-20260930T172308Z.log`
and adjacent `65864-20260930T172308Z.summary.json`.

Final producer hashes:

- `WorkflowPreparedDataStoreTransition.scala`:
  `06d1b8b95e876810b6d1207b7ad5d48162a1b3498d2f5bfa0e52af1a95b98427`.
- `WorkflowCompletionDataStorePersistenceSpec.scala`:
  `9946b8a6b7c6ecd6f2cc22c60e6aadc6cf489a0eee0b283226bf2ca3917a9e4b`.

Git diff checks confirm central UnitOfWork and UnitOfWorkResource remain
unchanged. No public Operation, EventEngine or runtime switches to this seam.
No commit, push, publication, release, sandbox change or extra approval was used.

## Next production obligation

The current host route explicitly permits only the effect-free pair. Extend
native host composition to admitted closing/data intents before central UoW
activation; reject effects that cannot be represented in the same physical
provider transaction. Existing `stageClosingProgramC` executes through the
active interpreter, which performs local writes, so invoking it before this
participant is not proof of atomic enlistment. A recording stub or arbitrary
postcommit callback is likewise not production effect authority.

The next native composition must preserve exact owner/collection admission,
independent revision guards, Create-only intent rules, retained-event
observation closure and no event-domain writes for transient-only input.
Closing writes must rollback together with the Workflow pair and selected
durable events, with complete authoritative readback after one physical commit.
Only then assess a bounded central managed-UoW opt-in and its uncertain
termination/claim-release consumers. Actual production dispatch and transient
no-recovery/no-replay still require executable evidence. S12/P2-U15/P2-U16/P2-U05,
runtime/CLI/MCP/restart and distribution remain open; no smaller definition of
Phase completion is adopted.
