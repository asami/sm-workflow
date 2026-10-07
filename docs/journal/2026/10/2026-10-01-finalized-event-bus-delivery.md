# Finalized native event projections and actual EventBus delivery

Status: internal managed UoW to actual bus integration verified; public runtime
activation and full Phase 2 acceptance remain open. Previous goal turn progressed
with managed UoW opt-in. This turn replaces the original unissued-input delivery
in the real SQLite closing scenario with verified finalized event projections.

## Implemented

The common DataStoreSpace host entry now has a finalized projection companion.
It preserves deterministic host route selection and its captured EventStore
policy. Pure transient input takes the selected nonpersistent records with zero
sequence. For durable input, classification maps each original occurrence to a
newly assigned, retained-existing or retained-batch fact. Every durable delivery
record must match one exact event collection/identity/byte row in the complete
verified native readback. It is decoded from canonical verified bytes, including
its hidden delivery key, never regenerated from incoming payload. Retained
incoming names or unsupported payloads cannot replace retained canonical facts.
Original input order and occurrence multiplicity are preserved. Missing or
divergent projection is transaction-indeterminate, not a delivery fallback.

These finalized records remain data, not a UoW delivery permit. The prepared
Workflow participant stores them only after native verification, projects the
exact Workflow result and hands them to delivery only after callbacks and owned
resource completion. Its memory discard drops both incoming and finalized
batches; no native write, claim release or replay is performed.

DefaultEventBus has an internal committed-record dispatch entry that never
invokes EventEngine.emit or EventStore.append. It matches subscription name,
kind and persistentOnly against the finalized policy-selected record and uses
the actual ExecutionContext for authorized handler dispatch. A distinct
FinalizedRecordDomainEvent exposes the exact record to subscriptions; it does
not claim to reconstruct any arbitrary original DomainEvent subclass. The
ordinary publish/emit APIs and their authorization remain unchanged.

## Executable evidence and corrections

The real typed Completion/closing/local-intent SQLite scenario now uses actual
DefaultEventBus subscribers in all five event modes. Durable delivery sequences
are positive and their encoded bodies equal stored event_json; transient
delivery is nonpersistent with sequence zero. Retained input deliberately has a
different name and unsupported body, but subscribers receive the stored body.
The memory EventStore stays empty: managed bus delivery performs no second append.

An actual subscriber failure occurs after native joint completion; both selected
event facts and semantic/actual closing results remain stored. Managed UoW keeps
Committed disposition, stops the batch at the first handler failure and refuses
a second commit/delivery. Separate actual-bus tests verify selected persistence
subscription filters, no append and rejection of unfinalized sequence shapes.

395 compiled source and passed 429 tests/40 suites, but the appended selector
EventBusBaselineSpec was nonexistent and therefore provided no bus regression
coverage. The selector was corrected to the actual EventBusSpec in 396.
396 ran 440 tests/41 suites with one failure: the new subscriber-failure SQLite
fixture lacked canonical event schema before native absence observation. The
existing provisioning helper was added; native/failure contracts were not
relaxed. Final 397 compiled that fixture correction and passed **440 executed
tests in 41 suites**, zero failed, aborted, canceled or ignored; two preexisting
RuntimeContext placeholder examples remain pending. SBT/wrapper exits 0/0 and
lock=released. Logs and summaries were inspected. Shared serialization and
normal caches were used. No repository-full validation is claimed.

Final evidence: `/tmp/skill.cncf.d/cncf-sbt-96d2b8cc4d97076ac70725105a54b50c68e1076a300c3dc8cf5b8c9fec189b06-36870963af2ba2b57b9d506cd3d0f57a/99244-20260930T185739Z.log`
and adjacent `99244-20260930T185739Z.summary.json`.

Final SHA-256:

- DataStoreSpace.scala: `f61612a7c9c18c99310298eb3ff5804e1c4409a6cf6111cbd8d3b65912c05d5e`.
- ManagedEventDuplicateClassifier.scala: `ee0f96d67822407b9de9bfae10b5c430267fafa94de2b6643bbb93b9565a4712`.
- EventBus.scala: `453c3f71ae961bc17d8907c20bd20bfdca873b4d4851caf9f6041cc6cddb6cbd`.
- FinalizedRecordDomainEvent.scala: `c4924a80d98587db18d23991562399e7ec6bff45992222bc9bdff340be4561d6`.
- WorkflowPreparedDataStoreTransition.scala: `05ae4aff7d7b9e46ace54a37e4358d2c69486533802ff68929ceca524df81f49`.
- EventBusSpec.scala: `8f5fec425e5115c24ef4a231d239a77f7a919ea7398c776e4336b09061269a42`.
- WorkflowCompletionDataStorePersistenceSpec.scala: `edaac5544eb6be50c1f776f957188e4399fe7ca9c3eecd39f3dfd0058bbcfe6e`.

## Remaining production obligations

No public Operation/production bootstrap selects this managed route yet. Actual
Action selection/result contract/security, production grants and claim runtime
assembly remain required. The internal bus entry trusts the managed coordinator;
a finalized record itself never establishes publish/dispatch authority. Existing
subscribers expecting a specific original DomainEvent subtype are not silently
migrated; application subscription binding must explicitly accept the finalized
record contract. Full CommittedTransition-specific consumer compatibility is
not claimed.

Active SQL rollback/readback uncertainty/resource failure with zero actual bus
dispatch, independent-writer evidence, configured durable EventStore query/replay
and no transient restart replay still require integrated acceptance. An empty
memory EventStore proves no second append here, not durable query/replay
integration. Application server/CLI/MCP, management, packaging and S12/P2-U05/
P2-U15/P2-U16 remain open. No commit, release, push, publication, sandbox
relaxation or new approval occurred. Goal remains active.
