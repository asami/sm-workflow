# Call-scoped host selection reader

Date: 2026-10-01
Status: managed reader and focused validation passed; production installation/CLI open

Previous turn made progress: pure selection codec and 76 passing tests in eleven
suites (attempt 490). Continue the same Phase and complete real managed storage
integration rather than treating the codec as deployment evidence.

Further inspection found an existing usable seam in ActionCallFeaturePart:
bind_component_application_datastore_c calls Subsystem's managed binder when
the call has an owning Component/Subsystem. Normal Subsystem action execution
holds the outer managed lease, so the bound DataStoreSpace view remains live
for that call. This avoids a new CNCF public API. ComponentLogic's parent space
alone was insufficient evidence; the explicit ActionCall binder is required.

Freeze the implementation to ProfileStartAdmission, GoalPhaseStartServiceOperation,
ProfileSelectionDataStorePort and the managed suspension spec. CallScopedSelectionPort
requires the current ActionCall.Core, rejects context-free lookup, and preserves
the existing injected SelectionPort compatibility. Start delegates its host
lookup through that current capsule, not a captured Factory context. The read
adapter rejects missing/foreign Component ownership, binds the configured logical
store through the existing managed ActionCall seam, loads via DataStoreSpace,
decodes selectionPayload and verifies exact lookup identity. Prior DataStoreSpace
binding is restored on success/failure. No raw provider, pool, write/install,
upsert, approval or public Operation is introduced.

The new executable example uses a test-only trusted Create installer inside the
first managed dispatch. A separate reopened host receives only the main read
adapter, reads the existing selection and returns identical Start without an
installer or conversation-owned selection map. Missing records/context fail
closed. This does not establish a production trusted-installation boundary,
explicit application collection grants, input-to-selection scope or default
CLI activation. Those remain requirements before exposure.

Run actual packageBin and the eleven-suite accumulator. No new CNCF source edit
or public contract is required for this bounded read path.

Attempt 491 packaged main code successfully but stopped during test compilation:
Scala widened the anonymous test installer to ProcedureActionCall, hiding its
test-only installC method. Freeze the repair to a named local Installer class;
do not change main admission or datastore behavior. Add an escaped-capsule
assertion to the same example to require failure after the outer dispatch lease
ends. SBT/wrapper exited 1 with lock released; no tests executed. Complete summary
read at
`/tmp/skill.cncf.d/cncf-sbt-a4f5857b1712ac82b12eda13d2ccbfb872b1b69835117b202a678bcaaf60a723-a562cd9b5c0d61843a041d6272bf54d0/69626-20261001T021728Z.summary.json`.

Attempt 492 compiled and packaged successfully, then ran 77 tests in eleven
suites: 76 passed, the new durable selection example failed during JSON decode.
SqlDataStore's structured-value decoder interprets strings beginning with an
object delimiter as Record data. WorkflowInstanceDataStorePersistence already
avoids this by prefixing opaque JSON. Freeze the repair to the application
selection storage codec, adapter and tests: selectionPayload carries a strict
sm-workflow.profile-selection-json.v1: prefix; reject prefixless storage rather
than reconstructing lossy values. Wire JSON is unchanged. Round-trip all origins
through the storage codec and reject absent/foreign encodings. No SQL/provider
behavior, pool lifecycle or human admission rule changes.
SBT/wrapper exited 1, lock released. Complete summary read at
`/tmp/skill.cncf.d/cncf-sbt-23673ecf08004201fdfbfe733b70acf75467312b7e4fabbcb45db184fd7997d3-242ff45c755c0e43c406a6925817f22e/70392-20261001T021837Z.summary.json`.

Attempt 493 (SMWF-CALL-SCOPED-SELECTION-DIRECT-20261001-493) passed actual
Compile / packageBin and all 77 tests in eleven suites. SBT/wrapper exited 0,
lock released, no aborted/failed/canceled/ignored/pending cases. Complete
structured summary read at
`/tmp/skill.cncf.d/cncf-sbt-f50c70199820bf74038efc315cbd353e9c61ddbc118e4abcaa12067c855a7754-5fb4aebef6c61d8533649c66136f5cbf/71759-20261001T022109Z.summary.json`.
The new SQLite example now passes selection read through DataStoreSpace,
identical Start after host shutdown/reopen with only the main reader, missing
record denial and escaped ActionCall rejection after dispatch lease release.
git diff --check passed. This establishes a usable existing managed read seam,
not trusted installation, explicit collection authorization, concurrent writer
consistency, complete input binding, factory/default CLI integration or Phase
acceptance. Those obligations remain open; no new CNCF API was added.
