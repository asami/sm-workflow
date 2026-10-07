# Explicit managed Completion UnitOfWork opt-in

Status: internal active-UoW/native integration verified; no production Operation
or default engine activation. Previous goal turn progressed with joint closing
result retention. This turn adds a real UnitOfWork subtype and affected-consumer
validation, rather than declaring isolated participant evidence sufficient.

## Implementation boundary

`ManagedCompletionUnitOfWork` is private CNCF opt-in, holding one prepared
participant on its original thread. Its commit override never invokes the
ordinary EventEngine or a second storage transaction. It consumes its attempt
before preparation/native execution; repeated commit/abort, context cloning and
foreign-thread commit are refused. An already-attempted participant is uncertain,
not evidence of a fresh aborted attempt.

Existing ordinary UnitOfWork commit/abort algorithms remain unchanged. Internal
hooks admit only an otherwise empty UoW, complete its operational-resource
registry and retain commit/abort results for RuntimeContext consumers. Dirty
entities, pending events and legacy success/abort callbacks are not presumed
enlisted: they reject before native execution. The interpreter has a default
allowing admission hook; this subtype overrides it to reject every unenlisted
operation before target/provider lookup. No execution through another ordinary
interpreter can silently add a separate local/external write.

`UnitOfWorkTermination.Indeterminate` distinguishes uncertain native publication
from proven no publication. Native Verified maps to Committed, proven Rejected
to Aborted, missing/uncertain outcome to Indeterminate. Resources receive that
disposition. The participant runs frozen callbacks and cleanup, then UoW-owned
resource completion, and only then original-order delivery. Resource/callback
failure suppresses delivery without changing committed storage disposition.
Memory discard never releases claims or compensates storage. Native control
exceptions retain uncertainty, finish cleanup/discard, preserve interruption and
rethrow without retry. Pre-attempt cancellation aggregates cleanup diagnostics;
abort retains its outcome for RuntimeContext rather than silently reporting
success after resource failure.

## Executable evidence

The actual typed SQLite Completion/local-intent example across all five ordinary
event modes now commits through this UnitOfWork, not manual participant prepare/
commit/finish. Joint semantic/actual closing results and local post-images remain
exact. Delivery still uses a trusted test port, not the production EventEngine.

Seven dedicated UoW examples cover once-only native commit, callbacks/resources
before delivery, distinct rejected/uncertain termination, committed cleanup
failure with no delivery, rejection of legacy callbacks, interpreter denial
before/after commit, native interruption cleanup/discard and retained abort
cleanup failure. Existing ContinuationRuntime tests include persistent recovery
and claim-release failures. Inspection confirms persistent resume releases on
commit failure only for explicit Aborted; RuntimeContext marks supplemental
commit only for explicit Committed. Neither interprets Indeterminate as abort.
These are existing-consumer regressions, not admission of the new production
managed continuation route.

391 compiled with two source errors (generic Failure pattern and invalid
Consequence accessor); corrected without changing authority or contract.
392 passed 427 executed tests/40 suites; 393 added native interruption and passed
428; final 394 compiled cancellation/abort-result corrections and passed
**429 executed tests in 40 suites**, zero failed, aborted, canceled or ignored.
Two existing RuntimeContext placeholder examples remain pending; they are not
passing coverage. All attempts reached terminal lock=released. Successful
attempts had SBT/wrapper exits 0/0. Logs and structured summaries were inspected;
normal caches and the mandatory serial wrapper were used. No full repository
validation or acceptance is claimed.

Final evidence: `/tmp/skill.cncf.d/cncf-sbt-ec102c68dc076f49f6e79555d729c2db79b7d5f8ed2346ae7fafe4c27d1cef63-0b12281a68b25a03286bfb63ef821bb0/93995-20260930T184311Z.log`
and adjacent `93995-20260930T184311Z.summary.json`.

Final SHA-256:

- `ManagedCompletionUnitOfWork.scala`: `aa84e66c2f7b53a924fb9305033ba3603c9855537236764c27704639b75063a0`.
- `ManagedCompletionParticipant.scala`: `f88e46483d82f62609d3600e8f960e8ff0c67e1616815aa382058e283e69a156`.
- `UnitOfWork.scala`: `f3a2e9d11b5f810e5719d4a5f3d29a5b4a06def3258b95fa8f71c926a80b3821`.
- `UnitOfWorkResource.scala`: `b9ac2229a21181c7116cb3541dc359f7a2c373b2b9737a37aefe3451e6ca9b3b`.
- `UnitOfWorkInterpreter.scala`: `1f71e57f6edb66268a63be59d115f5fa56ab40ff14ebc8b1e5852cbadcd25fbf`.
- `ManagedCompletionUnitOfWorkSpec.scala`: `579facb43d651dd99c8d1b71842262f672afaecdbc1cf82b1f68872c25da6282`.
- `WorkflowCompletionDataStorePersistenceSpec.scala`: `3c8077fd9ab48f54d981f3080acbdf6f51f310eb1c8c89d58be28c79a605bf78`.

## Remaining production obligations

Actual admitted Action selection/result contract/security, production grants,
claim/runtime assembly and finalized EventEngine delivery remain required.
This subtype deliberately refuses legacy postcommit completion callbacks: the
existing persistent resume route cannot simply swap its UoW factory and thereby
become atomic. It needs the native joint claim/result route, not a second
postcommit persistence step. No public factory or Operation selects this subtype
yet. Concrete SQL rollback/readback-uncertainty/cleanup/writer scenarios through
this active UoW and no transient restart/replay remain acceptance obligations.

S12/P2-U05/P2-U15/P2-U16, application assembly, CLI/MCP/server/restart/management
and distribution acceptance remain open. No commit, release, push, publication,
sandbox relaxation or renewed approval was performed. Goal stays active.
