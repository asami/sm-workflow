# Recorded closure Decision reader

Continue active Phase 2 under parent-direct limited recovery. Previous turn
combined managed repair/authority Service and passed 93 tests. Own new private
GoalPhaseClosureDecisionRecord and two closure execution scenarios; preserve
unrelated changes. No new public Operation or trusted installation authority.

Store the host's exact canonical Decision wire under the exact intent key using
a versioned prefixed envelope. Reuse WorkflowDecisionJsonV1 and the existing
closure Input codec; do not add Handle/request schemas or synthesize host IDs.
Read requires exact Handle, run, revision, snapshot, typed Input and stop-only
resultType. The existing closure gate still checks capability, fact references
and noncallable completion identities. Missing/malformed records fail; never
generate a Decision from current Intent when a host record is absent.
Call-scoped DataStorePort shares ManagedHostRecordRead and has no writer/upsert.

Executable cases preserve exact host metadata with no grant/history/commit,
reject invalid storage and foreign intent scopes, and deny context-free reads.
These are codec/admission facts, not managed SQLite Decision reopen proof or
authenticated installation. Those, projection recovery, authorized closure,
controlled commands, default CLI and full assurance remain open. Validate actual
packageBin plus the twelve-suite accumulator from attempt502; no acceptance.

Attempt 503 failed compile before tests: ContinuationRequest is nested in
WorkflowProtocolV1, not directly in the workflow package. Remove only the bad
explicit import; the existing wildcard already imports the canonical type.
SBT/wrapper exited 1 and lock released. Full structured summary read at
`/tmp/skill.cncf.d/cncf-sbt-6bd119d84d6fbe313b9909e2eb8f1ae7510abeb1bbc4db358d059ebf0abd0e40-f4de498e6f17e256045ecd7bfed1bc61/97863-20261001T025904Z.summary.json`.
Retry the same accumulator after the mechanical import correction.

Attempt 504 passed actual Compile / packageBin and all 95 tests in twelve
suites, no failed/aborted/canceled/ignored/pending cases. SBT/wrapper exited0;
shared lock released. Complete structured summary read at
`/tmp/skill.cncf.d/cncf-sbt-6d1acf1cbdc413b04f228c8fe1db23d092ee8b31fa5c38eb01e2ab5bdf8ea1df-4d64ed277fa7dcc3db502713706ad1d5/98283-20261001T025952Z.summary.json`.
git diff --check passed; no SBT session remains. Canonical stop-only request
round-trips exactly without authority/history/commit changes; malformed storage
and foreign Handle/revision/snapshot/input/lookup fail. The host installer owns
the supplied lookup key; encoding does not derive or authenticate that key.
The adapter compiles, but its actual managed SQLite reopen behavior and durable
trusted installation still need proof. No production Decision adapter was
activated, and no recovery Operation was added. Keep the full Phase goal active.
