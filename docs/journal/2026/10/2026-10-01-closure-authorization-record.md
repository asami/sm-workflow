# Recorded closure authorization reader

Continue active Phase 2 under parent-direct limited recovery. Own the new private
GoalPhaseClosureAuthorizationRecord and additions to the closure execution spec.
Previous turn completed call-scoped proxy propagation and 86 tests. Preserve all
unrelated changes; no public API, permission policy, writer or launcher change.

Record immutable authorization facts under the host intent identity: canonical
WAIT scope (Handle, revision, snapshot), request digest, authority reference,
participant and grant/expiry timestamps. The WAIT is generated from recorded
authorization scope only, not from the current request, and is not approval.
Reuse canonical WorkflowWaitJsonV1 to avoid a second Handle/snapshot codec.
Strict schema/field/type/prefix/lookup and interval checks; preserve past expiry
and foreign scope for existing closure admission to reject. Never synthesize a
missing authorization from Intent or renew timestamps during decoding.

The read-only call-scoped DataStorePort uses ManagedHostRecordRead and the
existing managed DataStoreSpace lease. Absence returns None; malformed present
records fail rather than downgrade to absence. It has no install/upsert operation.
Executable codec and existing closure-gate scenarios test round-trip facts,
expired-record rejection, malformed envelope rejection and no closure effects.
Actual managed SQLite authorization read/reopen proof and trusted installation
are not established here. Keep those, collection grants, Decision durability,
managed completion auto-close and production CLI wiring open.

Validation: actual packageBin plus the twelve-suite accumulator from attempt495.

Attempt 496 (SMWF-CLOSURE-AUTHORIZATION-RECORD-DIRECT-20261001-496) passed
actual Compile / packageBin and all 88 tests in twelve suites. No failed,
aborted, canceled, ignored or pending cases; SBT/wrapper exited 0 and lock was
released. Full structured summary read at
`/tmp/skill.cncf.d/cncf-sbt-612a5fd9139c6a9f96da8dc2c35d102fb9a29171d8eef0b33aec57338b0b6cdc-4930a9768d05dca2d1fd8332e3e58bfd/80106-20261001T023621Z.summary.json`.
git diff --check passed. No SBT session remains. Record facts round-trip exactly;
an expired decoded authorization is rejected by the existing gate without a
commit. Malformed payloads and foreign lookup identities fail closed.

The new reader compiles but its actual SQLite/reopen behavior is not yet proven
by this receipt. Next bounded integration should install records through a
test-only trusted managed host, discard all authority Maps, reopen and read
using the main reader; test absence, malformed-present records and escaped
lease denial. Production authenticated installation/grants and CLI activation
remain required before the full Phase can close. No acceptance or release.
