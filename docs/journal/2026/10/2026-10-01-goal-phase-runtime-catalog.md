# GoalPhase runtime catalog admission

Date: 2026-10-01
State: IMPLEMENT; sm-workflow Phase 2

Parent-direct limited recovery. Own new GoalPhaseRuntimeCatalog main source and
spec, GoalPhaseManagedComponentFactory, ManagedSuspensionOperationSpec and this
journal. Preserve other changes. No public Operation/ABI/CML/configuration key,
producer, pool, default launcher, commit or acceptance mutation.

Inspection found only test-classpath JSON ABI resources in the current target;
compiled main descriptors exist but default Factory still exposes generated
services rather than the managed binding. Do not copy a fixture into production
or synthesize durable human authority to close that gap.

Introduce a pure opaque runtime catalog admitted from exact generated JSON text
by existing CNCF receivers. Compare the received Workflow, states, actions,
source lines and Required SPI with the compiled generated descriptor. Retain
raw JSON and its fingerprint unchanged; no normalization, CML reparse or DB
access. Make the managed Factory require that admitted catalog and reject a
declaration/producer differing from it before binding storage. Add
Given/When/Then pure catalog scenarios and retain main Factory restart tests.
Validate the previous seven-suite accumulator plus the new catalog suite.
Shipping official generator artifacts, configuration resolution and durable
host authority remain incomplete and are not replaced by this catalog.

## Observed executable evidence

Attempt `SMWF-GOAL-RUNTIME-CATALOG-DIRECT-20261001-467` compiled the two
changed main sources and two specs, then passed 58 tests in eight suites with
zero failed/aborted. SBT and wrapper exited 0, lock released. The structured
summary was read at
`/tmp/skill.cncf.d/cncf-sbt-110f996abf278be33a4b343fb75d1d8d219bfa6f732d9aa2c7e31bdd549475ca-1db7f9833b57ea414060919ee757649f/74435-20261001T001001Z.summary.json`;
the adjacent `.log` retains full output. `git diff --check` passed.

Four new Given/When/Then scenarios prove exact raw text/fingerprint retention,
receiver-valid foreign-state rejection against compiled Workflow metadata,
missing/malformed input rejection without fixture fallback, and distinct exact
fingerprints for whitespace-different bytes. Existing main Factory scenarios
add null-catalog and mismatching-fingerprint construction rejection, then pass
Start, review, repair, interrupted follow-up recovery and reopen on a matching
catalog. DefinitionBinding is not compared by object identity: its validated
public version/source/ABI/fingerprint fields must match the admitted catalog.

Catalog admission currently compares the receiver's Workflow projection:
ordered state names, actions, Required SPI and their represented source lines,
root/definition/version. It does not claim comparison of information discarded
by that receiver (such as state source lines) or full compiled producer policy
metadata. Producer sidecar admission and Workflow/sidecar correlation use the
existing CNCF receiver. Official generated-resource packaging and fuller
compiled producer-policy correlation remain necessary before production
configuration enables this path; test resources were not copied to main.

No pool is acquired by catalog parsing, and no config key or fallback human
authority was introduced. Default launcher/configuration resolution, durable
host selection/association/closure authority, typed public recovery, external
command execution and all Phase acceptance/release gates remain open. No
publish, commit, full review, release or Phase acceptance was performed.
