# SplitPhase Start current-call selection access

Goal State: IMPLEMENT — sm-workflow Phase 2. Previous turn changed four carriers
and passed 179 tests / 24 suites after one bounded test-constructor repair:
progress. User-authorized parent limited recovery, no agents; parent model and
effort are not directly observable. No change to Phase scope/acceptance/bases.

## Frozen slice

Own the whole inspected SplitPhaseStartServiceOperation.scala, new
SplitPhaseStartCurrentCallSpec.scala and this journal. Existing
CallScopedSelectionPort.loadC deliberately refuses context-free lookup; Start
passed that interface directly to execution. Therefore canonical managed
selection records could not be read through this public Operation.

Inside the existing BoundCall, reject null core/context, adapt only a managed
CallScopedSelectionPort to loadForCallC(identity, core), and otherwise preserve
existing host-port behavior. Match the established GoalPhase current-call
pattern. No Factory-time capture, grants, human issuer, record writing,
public request fields, schema, storage selection or progression changes.

Three Given/When/Then scenarios execute the real bound CommandAction against
official runtime definition metadata. Exact object identity proves two calls
use distinct current capsules; missing capsules cause zero lookup; absent or
non-human selection records stop before source/persistence ports. Counting
ports cannot provide authority or effects. No positive human record is minted.

Validation: official preparation/packageBin, all SplitPhase*, GoalPhase runtime
catalog/shared-carrier and ProfileStartAdmission regression suites, serially
with fresh permission metadata. No source edits during the live command.

Still required: current-call source/proposal/semantic/application persistence
composition for all SplitPhase Operations, independently admitted initial
human selection ingress, managed durable execution/restart, Factory and
ordinary server/CLI/MCP acceptance. This is one prerequisite fix, not P2-R04
or full Phase acceptance. Existing fixture/hash contracts are not activated
as a production substitute; pending distribution digest permission is unrelated.

## Observed validation

SMWF-SPLIT-START-CURRENT-CALL-DIRECT-20261002-691 completed official preparation,
packageBin and the frozen focused accumulator. Full summary was read:
sbt_exit=0, wrapper_exit=0, lock=released; 185 succeeded tests in 26 completed
suites, with no failed/aborted/canceled/ignored/pending cases. All three new
current-call scenarios ran and passed. Existing non-managed fixture Start
execution/retry and semantic/preview/apply regressions remain compatible.

Receipt: /tmp/skill.cncf.d/a-62667075-6c7f-4bbf-9a08-f4ef0d90b806/71320-20261001T174027Z.summary.json

git diff --check passed. This proves exact current-capsule delegation and
fail-closed absence/non-human behavior in real bound Action execution; it is
not proof of an independently verified human selection, a managed DataStore
read or a production SplitPhase instance. No consent records, document writes,
Git mutations, publication, installation or Phase closure occurred.
