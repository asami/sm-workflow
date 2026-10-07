# Public canonical EventStore host bootstrap

Status: typed construction and managed Completion regression verified;
launcher configuration, application composition and full lifecycle proof remain open.

## Implementation scope

Retained sm-workflow Phase 2 / admitted CNCF Phase 88 parent-direct recovery.
No agent was spawned, public publication/commit/push performed, or Cozy source
edited. Mandatory SBT serialization remained in force. Existing dirty work
was preserved.

CNCF adds `subsystem/SubsystemEventStoreBootstrap.scala`. Its public `createC`
requires an already validated EventPersistencePolicy, storage declaration and
canonical host inputs. It delegates to DefaultSubsystemFactory.defaultWithScope
within the existing private construction scopes. Neither a declaration by
itself nor an Operation input grants storage access. Policy is captured once;
the subsequent ordinary host retains its previous behavior. No raw datastore
view, claim token, sender policy override or SQL API is exposed.

The opaque host offers explicit `initializeCursorC` (first install, no upsert or
reset) and `shutdownC`. The latter reuses canonical owned cleanup only when
this construction created the SystemNode; an inherited runtime node retains its
original owner. Missing storage does not trigger initialization during normal
Workflow progression. EventStore's in-memory service remains the policy/live
service, while the managed Completion route writes its selected records through
the admitted DataStoreSpace; this does not implement durable ordinary EventStore
query/replay.

## Executable evidence

`SubsystemEventStoreBootstrapSpec` declares package `example.workflowhost`,
outside CNCF's private visibility, although the source is stored in the existing
subsystem test directory. It proves public construction, fixed-policy isolation,
SQLite cursor initialization, duplicate-initialization refusal after resource
reopen, and absent policy/declaration rejection. Cursor facts are compared by
revision/sequence, not reference equality of rehydrated objects.

The existing `WorkflowCompletionDataStorePersistenceSpec` now constructs all its
managed hosts through this public canonical factory rather than private
EventStore constructor fixtures. Its public Completion Operation, actual bus,
owned claims, retained semantic/closing results, failure and recovery scenarios
remain covered. It still seeds exact records in a controlled test; it is not
the real sm-workflow generated application bootstrap or a launcher-started run.

Final focused invocation `SMWF-CNCF-HOST-LIFECYCLE-DIRECT-20261001-412`:

```text
--batch
testOnly example.workflowhost.SubsystemEventStoreBootstrapSpec org.goldenport.cncf.workflow.WorkflowCompletionDataStorePersistenceSpec org.goldenport.cncf.event.EventCursorRecordV1Spec
```

45 tests executed across 3 suites, all successful, none aborted, canceled,
ignored or pending. SBT/wrapper exits 0/0, lock=released; matching summary read.
Evidence:
`/tmp/skill.cncf.d/cncf-sbt-dd4c6b8d4fba59096ade39d0820b8eb1baa1847dc57eb5ce5adb5d6aa7e7b7a0-3985e5d344533a84ca6531807fc837cf/44093-20260930T202511Z.log`
and adjacent `44093-20260930T202511Z.summary.json`.
`git diff --check` passed.

## Preserved failure and intermediate ledger

- 406: generation failed before tests because Cozy's development classpath was
  stale; exits 1/1, lock released. Its declaration-suite selector was also found
  nonexistent and replaced with actual EventCursorRecordV1Spec thereafter.
- 407: admitted `cozyExportRuntimeClasspath` prerequisite succeeded, exits 0/0,
  lock released; no source edits or remote publication.
- 408: new test suite aborted for a noncanonical ComponentId namespace; 42
  existing tests passed, but the command failed. Corrected fixture only.
- 409: 44 passed, one new assertion failed because EventCursorRecordV1 is not a
  value-equality case class. Corrected to exact persisted cursor facts.
- 410: 45/45 passed with new public construction examples.
- 411: 45/45 passed after switching managed Completion fixtures to the public
  canonical host. Source inspection then showed Subsystem shutdown only releases
  its logical binding; added host-owned node cleanup before final 412.

Each invocation had a fresh metadata identity; all terminal markers and
summaries were inspected before starting the next SBT command. Earlier failures
are not acceptance evidence, and 411 does not prove the later lifecycle edit.

## Outstanding evidence and next work

The final log contains 191 Hikari `Start completed` and 65 `Shutdown completed`
messages. This mismatch is observed lifecycle evidence, not a proven attribution
to the new managed resource or a completed cleanup proof. Canonical built-in
construction is broader than the former empty Subsystem fixture; identify the
remaining pool owners before promoting production host lifecycle. Do not hide
the observation by returning to an empty/private host fixture. The inherited
node branch also needs executable ownership proof.

This public programmatic host factory does not yet project trusted launcher
configuration or replace runtime repository-policy admission. The next runtime
connection must preserve CncfRuntime's admitted repository bootstrap and SystemNode
ownership; no application-specific parallel CLI, raw ResolvedConfiguration
authority shortcut or direct Workflow-to-SQL path is allowed.

CNCF source changed after refresh-404. Refresh its local SNAPSHOT again before
consumer validation; consumer-405 does not contain this host API. Real generated
GoalPhase Operation composition, separate-process reopen, server/MCP equivalence
and Phase release remain open. No production acceptance checkbox is promoted.
