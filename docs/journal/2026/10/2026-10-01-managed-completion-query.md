# Opaque managed completion query implementation slice

Date: 2026-10-01
Phase: sm-workflow Phase 2 / admitted CNCF Phase 88
State: IMPLEMENT

Continue the user-authorized parent-direct recovery route. This slice owns only
the existing CNCF `WorkflowManagedCompletion.scala`, the existing application
`GoalPhaseManagedSuspensionOperationSpec.scala`, and this journal. Preserve all
other work and lifecycle semantics. No new repository, pool, provider, schema,
initial data, timestamp inference, commit, release or remote publication.

Freeze the query contract: `loadRetainedC` is bound to the same host Component,
configuration, definition catalog, collections, completion Operation and codecs
as writes. Validate input before physical access, re-admit the captured grant,
borrow the existing verified DataStoreSpace and delegate the existing provider
query. Require an actual typed closing result with the binding's closing type.
Return an opaque framework-constructed retained value, not an application-made
claim that decoded JSON is committed. Do not call the event callback, execute a
closing program, claim a Continuation, append an instance or publish work.

Executable scenario: issue through the managed path; query missing and suspended
continuations; complete a repair judgment through normal Subsystem dispatch;
shutdown/reopen the same database; query its exact typed retained result and
completion revision without a fresh timestamp/admission; reject an incompatible
completion Operation/type and calls through the stopped original host. Check
unchanged history, one managed resource, no active lease and no event callbacks
caused by querying. Author behavior with Given/When/Then adjacent to actions.

Validation: focused producer compilation/regression followed sequentially by
development SNAPSHOT publishLocal and the focused consumer spec, using only the
machine-wide serial wrapper, normal caches and fresh permission metadata.
This is a read seam for the subsequent managed repair implementation, not a
complete repair loop, default factory, CLI recovery or Phase acceptance.

## Implemented, not yet compiled

Added `WorkflowManagedCompletion.loadRetainedC` and a non-publicly-constructible
typed `Retained` witness. The query re-admits the fixed host grant, uses the
existing verified managed view and completion provider query, and rejects a
missing or differently typed closing result. Its provider and lease remain
internal. The application executable scenario uses ordinary Subsystem dispatch
and three owned-host lifetimes over one SQLite file; it is authored but has not
run. Its explicit late timestamp demonstrates that fresh review admission would
reject the old evidence while retained-result recovery must not re-admit it.

## Validation prerequisite failure

Invocation `SMWF-MANAGED-COMPLETION-QUERY-PRODUCER-DIRECT-20261001-442`
selected exactly the two producer suites `WorkflowManagedIssuedWorkSpec` and
`WorkflowCompletionDataStorePersistenceSpec`. It failed before new-source
compilation or tests: the project-owned Information generator reported a stale
Cozy development runtime classpath. SBT/wrapper exited 1/1 and released the lock.

- log: `/tmp/skill.cncf.d/cncf-sbt-36b6e89a85b0973272b53d944e1d1fe52de749d8025df491a8994897b0fb0f30-8e62a42be6d1ec6e944b04a8bb9f3c02/23672-20260930T224824Z.log`
- summary: `/tmp/skill.cncf.d/cncf-sbt-36b6e89a85b0973272b53d944e1d1fe52de749d8025df491a8994897b0fb0f30-8e62a42be6d1ec6e944b04a8bb9f3c02/23672-20260930T224824Z.summary.json`

The selected dependency refresh
`SMWF-MANAGED-QUERY-COZY-CLASSPATH-DIRECT-20261001-443` ran only
`cozyExportRuntimeClasspath` in Cozy's already-admitted validation/artifact
scope. The task depends on Runtime/fullClasspath and compiled current Cozy
sources. It failed with eleven missing-member errors in
`cozy/media/CozyMediaCrossReview.scala`: `logicalIdentity`, `visualPageIdentity`,
`visualPageSetIdentity`, `catalogIdentity` and `binding` are not available on the
referenced presentation evidence types. This is outside this slice's source
ownership. SBT/wrapper exited 1/1 and released the lock; no tests ran.

- log: `/tmp/skill.cncf.d/cncf-sbt-d5b9d551b99083a1e31c3498c5d911ff2693e90e5f11bae1d8d5dbc6dc5573c4-d91bfaf2d44253025da8c1f15bc7793f/24329-20260930T224921Z.log`
- summary: `/tmp/skill.cncf.d/cncf-sbt-d5b9d551b99083a1e31c3498c5d911ff2693e90e5f11bae1d8d5dbc6dc5573c4-d91bfaf2d44253025da8c1f15bc7793f/24329-20260930T224921Z.summary.json`

Inspection confirmed CNCF generation selects its project-declared exact
`cozy --runtime 0.3.3-SNAPSHOT` coordinate. Do not substitute another generator,
disable generation, falsify classpath freshness, patch Cozy sources outside the
admitted mutation scope or publish the uncompiled CNCF changes. No producer
publishLocal or consumer test was attempted after these failures. Both summaries
were inspected; no live SBT session remains. Resume the same selected producer
test after the generator prerequisite is repaired, then refresh the SNAPSHOT
and run the consumer scenario. No platform permission rejection occurred.

## Resume and bounded fixture repair

External Cozy source edits removed the eleven missing-member references. No
Cozy source mutation was admitted or performed here. Prerequisite 444 succeeded,
but further external source edits made producer 445 reject its stale classpath.
`find src/main -newer target/cozy.d/runtime-classpath.txt` identified the changed
CrossReview source. Prerequisite 446 compiled it and exported the current
classpath; producer 447 then compiled the query and passed 38 tests in two
suites. Refresh 448 published only the development SNAPSHOT locally.

Consumer 449 compiled main sources but rejected three new test accesses to
`Subsystem.systemNode`, which is intentionally private[cncf]. This is a confirmed
test-authoring error, not a missing public capability. Freeze M1 repair unit 1
in orchestrated cncf-test-fix mode: preserve all public read/reopen/result/replay
assertions and move only internal resource-count observations to the framework
package's existing `WorkflowManagedIssuedWorkSpec.scala`. That exact test file,
the existing consumer spec and this journal are the entire repair frontier.
Their complete prior source reads are retained. Add a behavior scenario with
adjacent Given/When/Then verifying read absence while suspended, unchanged root
and issued work, no callbacks, one managed resource, no retained leases, and
query refusal after shutdown. Do not expose SystemNode, use reflection, add a
test-package escape hatch or change production behavior. Remove the three
illegal consumer expressions only after their observations are assigned to
the producer scenario. Parent owns this bounded mechanical correction under
the retained user-direct recovery; no agent or commit is requested.

Re-run the same two producer suites and the failing five consumer suites in
sequence. The production artifact did not change during this fixture repair,
so retain refresh 448 rather than publishing again. Full validation and
independent review remain pending at the Phase release gates. The abandoned
Cozy-edit question is no longer required: its external prerequisite was repaired
without expanding this task's source ownership.

## Producer fixture closure and live consumer handoff

`SMWF-MANAGED-QUERY-FIXTURE-PRODUCER-DIRECT-20261001-450` compiled the
framework fixture correction and passed 39 tests in two suites, including the
new managed-resource/lease/query/shutdown scenario. The inspected summary
records zero failures, aborts, cancellations, ignored or pending tests, exits
0/0 and a released lock.

- log: `/tmp/skill.cncf.d/cncf-sbt-d6780ba5a35c6e88d593bff301593550d6e98bf4c0a4128513119bc2148d7b92-c5b3c622462878326efbe43d1bf65580/30505-20260930T225846Z.log`
- summary: `/tmp/skill.cncf.d/cncf-sbt-d6780ba5a35c6e88d593bff301593550d6e98bf4c0a4128513119bc2148d7b92-c5b3c622462878326efbe43d1bf65580/30505-20260930T225846Z.summary.json`

Producer main source SHA-256 remains
`990ab1332224af75b34c865610d03623e36b0d805586b470d6f91a0f7c771205`.
The locally refreshed CNCF JAR from 448 is
`2c4a0f75d1e8ce4241deaa14d13ff5b1bdd03983546a0eb83878ac158332e2b1`.
Consumer fixture SHA-256 after the mechanical correction is
`62f64921cba742218f03849a251cfc36135c2f31a54afa248d46992ec47d9cc9`.
No production bytes changed during this fixture correction, so no second
publishLocal was performed. Both repositories' diff checks passed.

Selected consumer invocation
`SMWF-MANAGED-QUERY-FIXTURE-CONSUMER-DIRECT-20261001-451` is submitted and
live, not complete. Its permission artifact was accepted and its serial wrapper
reported waiting for another active SBT invocation. Continue polling the same
command-tool session (84230); do not restart it, allocate another attempt,
infer terminality from silence or remove the shared lock. No consumer success
is claimed until its terminal marker and structured summary are inspected.
The bounded repair remains open pending that exact consumer result. This is a
verified process wait, not an unanswered request to expand Cozy source scope.

## Consumer terminal closure (451)

The preceding live handoff is superseded by the observed terminal result.
Invocation 451 acquired the shared lock without another submission, compiled
63 test sources and passed all 45 tests in five suites. The retained-repair
scenario passed: a valid repair judgment committed through normal dispatch
remains queryable after host reconstruction and evidence expiry, with exact
semantic result, closing result and instance revision. Fresh admission at the
late timestamp still rejects the expired evidence. Wrong completion Operation,
wrong closing type and replay reject; history and query callback count remain
unchanged. Internal resource/lease checks passed in producer 450, not through
private access from the consumer package.

- log: `/tmp/skill.cncf.d/cncf-sbt-c661a861a6b078bd27b2aeade73d437567ff0b51bca113c8a80f53a3131ebe03-7bc529d4c70c8d6202744d498cebe6a7/31611-20260930T230016Z.log`
- summary: `/tmp/skill.cncf.d/cncf-sbt-c661a861a6b078bd27b2aeade73d437567ff0b51bca113c8a80f53a3131ebe03-7bc529d4c70c8d6202744d498cebe6a7/31611-20260930T230016Z.summary.json`

The inspected summary records 45 successes, five completed suites, zero
failures/aborts/cancellations/ignored/pending, SBT/wrapper exits 0/0 and a released
lock. Test/fullClasspath names the refreshed normal local Ivy CNCF JAR. JAR,
producer main and consumer fixture hashes checked after validation equal the
recorded values above. No live SBT remains; do not resume session 84230 or repeat
451. M1 fixture repair unit 1 closes on focused evidence only.

Next implement managed repair Intent recovery from the opaque retained witness
and issue its successor through the existing atomic managed suspension route.
The current repair loop still uses its separate Intent prerequisite and legacy
publication path; this query slice does not claim to have migrated it. Full
Service/default-factory/production-authority/CLI/server/advance recovery and
Phase validation, independent review and acceptance commits remain required.
No goal completion, release or commit was performed.
