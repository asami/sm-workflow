# Active managed UoW failure boundaries with actual EventBus subscribers

Status: bounded executable evidence; production Operation/CLI activation remains open.

The previous goal turn inspected successful validation but changed no implementation.
This turn strengthens existing executable examples in CNCF Phase 88 rather than
switching the default runtime to a managed factory. The current common completion
Operation still commits resumed execution and appends instance history separately.
That is not the production joint-completion route required by sm-workflow Phase 2.

## Executable changes

The existing lifecycle example now uses real SQLite storage, actual prepared
closing results, a ManagedCompletionUnitOfWork, a durable/transient mixed batch,
and an actual DefaultEventBus subscriber. Callback, participant cleanup and UoW
resource-release failures are tested separately. All retain the semantic result,
actual closing result, Completed claim and selected durable event/cursor. The
transient event is absent from storage. Resource release observes Committed exactly
once; cleanup occurs once. Subscriber dispatch remains zero, including after a
second rejected commit. The in-memory EventStore remains empty, proving no second
append here, not configured durable query/replay.

The existing stale-root/projection example now also uses the active managed UoW,
nonempty durable input and a real bus subscriber. A stale root yields Rejected /
Aborted with no result or event/cursor advancement and preserves the claimed
Continuation. A test-injected trusted projection failure after actual native
commit yields Indeterminate, retains the semantic result, Completed claim and
durable event/cursor, and performs no callbacks or bus dispatch. Neither route
admits another commit. The latter is committed projection uncertainty, not an
injected provider readback failure or interrupted physical commit.

Only executable-spec source changed in the producer during this turn. No public
Operation, default engine, authorization policy, factory or request schema was
changed. Existing tests were expanded rather than counted as additional tests.

## Validation

Focused-398 compiled the updated source and passed 440 executed tests across 41
suites, zero failures/aborts/cancellations/ignored. Two existing RuntimeContext
placeholders remain pending. Shared serialized SBT used normal caches; SBT and
wrapper exited 0/0 with lock released. The persistent summary was inspected.
This is not repository-full or downstream application validation.

Log: `/tmp/skill.cncf.d/cncf-sbt-d1b63d59449eb64d4ba12407baab6cce2cb851fd5792fcfcfaab96e6dffdbf76-43b6bb96ad2c51bb5525fef3eeb5fe41/6802-20260930T191459Z.log`.
Summary: adjacent `6802-20260930T191459Z.summary.json`.
Producer spec SHA-256: `c88ad02ea002d48980c11f623c8d3cb0f82063038bf24f2008830f151bdb9ce6`.

## Remaining route to production

Actual active-UoW SQL rollback/provider-readback uncertainty and independent-writer
competition still require integrated bus-suppression evidence. Existing separate
provider specs are not promoted to that broader claim. Production Action selection,
expected closing-result contract and host grants remain required. The common
completion Operation needs a host-selected joint execution backend that owns the
canonical claim and retains both results in its native commit; it must not use the
legacy postcommit claim-completion callback or a second instance append. Skills
must never select that backend or receive claim tokens/storage authority.

Then refresh the admitted producer SNAPSHOT and verify actual application binding,
launcher/CLI start/completion/reopen, server/MCP equivalence, management and skill
packaging. All Phase 2 acceptance checkboxes remain unchanged. No acceptance commit,
release, push, publication or sandbox relaxation occurred. Goal remains active.
