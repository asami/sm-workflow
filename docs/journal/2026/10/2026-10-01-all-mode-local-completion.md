# Local completion across every host-selected event mode

Status: internal native/typed participant integration verified; production
closing-program and active UoW integration remain open. Previous goal turn
progressed with new-durable local composition; this turn changes source and
executable evidence, without narrowing the Phase objective.

## Implemented

`DataStoreLocalCompletionPlan` admits bounded additional revision guards and
Create-only records, including zero guards without a dummy EventStore cursor.
The same captured host policy admits exact owners/collections/entries and every
target must resolve to the same physical provider. Complete ordered readback
covers the Workflow pair, local guarded records and Creates, plus retained
observation records where applicable. Pure plans remain data, never grants.

`conditionalHostEventsWithLocalIntentsVerified` selects exactly one native route
from ordinary events and the captured service. Transient/empty and all-retained
invocations now carry canonical local intents to their already-open native
SQLite connection. Local guards and Creates are applied before native commit
alongside the Workflow pair; stale guards or failure rollback the whole write.
Retained observations remain validation-only. Transient/empty input observes
and writes no event-domain table. No dummy cursor increment, second transaction,
postcommit local write, route fallback or reclassification is introduced.

The existing typed prepared Completion participant now passes canonical local
intents to that same common host entry. Its exact result/claim/history projection
remains after complete native verification, and its original input batch stays
undelivered until callback/cleanup completion. Storage uncertainty never becomes
claim release, compensation or replay. Standard grants and public Operations
are unchanged; successful local permissions are explicit trusted test assembly.

## EventStore reservation correction

Inspection found that the preceding internal prepared-local entry could accept
an extra Create in an EventStore collection because write policy admitted that
collection for the host-generated selected event records. This would conflate
event selection with local data authority. The internal route was not publicly
activated. Both prepared and ordinary local entries now reject local guards or
Creates targeting the captured cursor, event or delivery-index collections,
even when those collections are permitted for EventStore's own generated plan.
Local inputs cannot bypass the event-kind persistence policy. No user-facing
permission, declaration expansion or sandbox change was needed for this repair.

## Executable evidence

Twenty-six configurations in three new executable scenario groups prove:

- Retained/transient/empty input × success, zero-guard Create-only, stale local
  guard, BeforePublish rollback and malformed postcommit local readback.
  One native commit/readback occurs on success; rollback leaves both local and
  Workflow state unchanged. Readback uncertainty leaves actual committed data
  intact. Retained event facts/cursor are unchanged; transient/empty has zero
  native observations and no event-domain SQL statements.
- Cursor/event/index Create injection through ordinary and prepared entry:
  all six reject before provider writes, with no injected storage record.
- Real claimed typed Completion participant across empty/transient/retained/
  new/mixed ordinary input: exact semantic result, completed claim and local
  post-images are retained together before test-port delivery. Transient event
  records remain absent from storage. Finish delivers the original batch once.

Focused-384 passed 303/303 tests, 31 suites. Focused-385
(`SMWF-CNCF-TYPED-LOCAL-PARTICIPANT-DIRECT-20261001-385`) compiled the final
source/spec and passed **304/304 tests, 31 suites**, zero failed, aborted,
canceled, ignored or pending. Both used the unchanged focused-376 suite argv,
mandatory serial wrapper and normal caches; logs and structured summaries were
read, SBT/wrapper exits 0/0, lock=released. Full repository validation is not
claimed.

384 evidence: `/tmp/skill.cncf.d/cncf-sbt-68984756bb0d2f492d48a5c6e56dacfa1eab927b56b539f78ac24f8322f8bf8f-e17d71a0dea9201ac96d17bb7c46b041/74008-20260930T174356Z.log`
and adjacent `74008-20260930T174356Z.summary.json`.

385 evidence: `/tmp/skill.cncf.d/cncf-sbt-4cfbf0a11e78a0ecda6a95e7219fc9426357e3a653f3cba31fc2152a2e33b3a9-4716d4524fc752120d5081aaf799ed1f/75286-20260930T174746Z.log`
and adjacent `75286-20260930T174746Z.summary.json`.

Final source SHA-256:

- `DataStoreLocalCompletionPlan.scala`: `eb30406dc84dde10ded2ae39fb325f10d77d256121b6fbd8d366ac4e755c5bd1`.
- `DataStoreSpace.scala`: `161d32c2990e80fc9abad041dc62570098156cf88253d36346892e8983823cb8`.
- `ManagedEventPreparation.scala`: `93cff72c1baf4fe5dea13df64cc6f106f56b2918795fc95192701de32294d8b3`.
- `SqlDataStore.scala`: `b45d6e4f3386ff080010f287167630f07ce0af528a7e0a853e14a5ed24f23e8c`.
- `WorkflowPreparedDataStoreTransition.scala`: `07efa5f6fe3328e786a6cd755d92291ceb1bc7f955ef5dbdd7fb3524d9caf523`.
- `ManagedEventObservationIsolationSpec.scala`: `97e6e2b1905b4a105465ccf7592c2dd43455815124f41a5d523b64e08b9ec0b5`.
- `WorkflowCompletionDataStorePersistenceSpec.scala`: `a18616d5ea346298383b63146ea5dae97b088e42d0c8ccc9e5af4713bd517d42`.

## Remaining production obligations

Local intent input is currently constructed by trusted test code, not derived
from actual admitted closing-program interpretation. Do not execute existing
`stageClosingProgramC` local writes separately and call that enlistment. The
next boundary must deterministically derive/freeze admitted closing intents,
reject unsupported or external effects and retain the actual Action result
before selecting this native completion. Immutable closing-plan handoff,
production declaration/configuration, independent local-writer competition,
affected continuation/resource consumers, central UoW uncertainty handling and
actual event dispatch remain open. Test-port delivery is not production bus,
finalized-event projection or restart/replay evidence.

No central UnitOfWork/resource, EventEngine, standard grant or public Operation
was switched. No commit, push, publication, release, sandbox change or additional
human approval occurred. S12/P2-U05/P2-U15/P2-U16 remain unchecked, as do runtime,
CLI/MCP, restart, management and distribution acceptance. Goal remains active.
