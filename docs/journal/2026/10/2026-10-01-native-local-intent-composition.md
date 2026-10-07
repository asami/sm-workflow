# Native composition of local closing/data intents with new durable events

Status: internal new-durable storage composition verified; full closing-program
and active UoW integration remain open. Previous goal turn was progress
(host participant source/spec and focused-381). This turn changes native-plan
composition and executable evidence, not Phase acceptance.

## Source changes

`ManagedEventPreparation.planWithLocalIntentsC` composes canonical additional
revision guards and Create-only records with the captured service's prepared
new-event plan. It retains the Workflow pair/correlation, derives only genuinely
new event/index Creates, and preserves the real cursor guard. Closing guard
capacity reserves one of the existing eight slots for that cursor. Existing
multi-guard admission checks all guards, exact bindings, record limits and
overlapping pair/guard/Create targets. Null or over-capacity vectors reject.
No callback, Save/Delete side effect, arbitrary program or caller success flag
is admitted by this input contract.

`DataStoreSpace.conditionalPreparedEventsWithLocalIntentsVerified` reuses the
original live host-bound preparation, validates the entire resulting plan
against its captured owner/collection/entry policy, and resolves every target
to the identical host provider. It invokes the existing verified native
transaction and complete readback seam once, not a Workflow SQL connection or
second transaction. Existing prepared-event entry delegates with empty local
inputs; ordinary route selection and public Operations are unchanged.

This is the new-durable branch only. All-retained and transient/empty local
composition must be implemented separately, without dummy cursor advancement,
unnecessary event-domain observation/write, fallback or reclassification.
Invoking this prepared route with all-retained input remains a rejection.

## Executable evidence and authority

The real borrowed SQLite test provider is used. A trusted host assembly fixture
explicitly admits one exact local owner/collection/entry and one Create
collection; local input does not create the grant. Standard Subsystem grants
remain unchanged and deny these additions before native write.

Seven configurations in two new executable scenario groups prove:

- Successful pair + cursor + local guarded update + new event/index + local
  Create commit once, with eleven actual post-images including retained facts.
- Stale local revision, existing Create identity and failure at BeforePublish
  rollback the Workflow pair, selected new facts and local writes together.
  Existing duplicate Create bytes and unrelated local fields remain intact.
- Ungranted local owner and the unchanged standard host grant reject before
  any provider write, commit or readback.
- Malformed local postcommit readback is transaction-indeterminate. One actual
  commit remains retained, with exact stored local state and selected facts;
  no compensation or retry occurs. The readback mutation is test-only and does
  not alter actual storage.

Focused-382 passed 301/301 tests in 31 suites. Focused-383
(`SMWF-CNCF-NATIVE-LOCAL-READBACK-DIRECT-20261001-383`) compiled the final test
change and again passed **301/301 tests, 31 suites**, zero failed, aborted,
canceled, ignored or pending. Both executions used the mandatory serial wrapper,
normal caches and the unchanged focused-376 exact suite argv. Logs and summaries
were read; SBT/wrapper exits 0/0 and lock=released.

382 evidence: `/tmp/skill.cncf.d/cncf-sbt-589e942fa194694a55b1e1354aa95184d7b25b26abfcc9e9f46d6cdd2aac5148-999682c20695151397df12ba230aebe7/69543-20260930T173158Z.log`
and adjacent `69543-20260930T173158Z.summary.json`.

383 evidence: `/tmp/skill.cncf.d/cncf-sbt-d6a248e978a9d0284a6e9760cdb47892c036eb65fb759365ba43cd49c3543c8c-883a6426ac4db074f359ba3e5baf2f63/70273-20260930T173339Z.log`
and adjacent `70273-20260930T173339Z.summary.json`.

Final source SHA-256:

- `ManagedEventPreparation.scala`:
  `fe6d1403a89fa0ea6629acfbf83a199232fb7fb0c2407cb056548a3dae4096f7`.
- `DataStoreSpace.scala`:
  `f3917850d9017f33ae60553fb95a8a01e451270ebfd38ba07e0d9b30e3d525d5`.
- `ManagedEventObservationIsolationSpec.scala`:
  `414981eaecb3f89868550671177105362e17af8f5fe9cf93a05acdb5fb3f387b`.

## Remaining acceptance

This evidence is not actual ExecUowM closing-program interpretation, typed
Completion participant integration with local inputs, production local-grant
configuration, all-retained/transient local composition, independent local
writer competition or actual event dispatch. Those remain obligations, not
unsupported cases to waive. Ordinary closing interpreter writes before this
native route would still be separate effects and are not a solution.

Central UnitOfWork/UnitOfWorkResource and EventStorePersistenceDeclaration have
no diff; no standard grant expansion, public activation, release, publication,
push, commit or sandbox change occurred. P2-U05/P2-U15/P2-U16/S12 and the full
runtime/CLI/MCP/restart/distribution requirements remain open. Continue native
local composition for all event modes, then bind real admitted closing-program
intents and assess the bounded central UoW seam. No goal completion is claimed.
