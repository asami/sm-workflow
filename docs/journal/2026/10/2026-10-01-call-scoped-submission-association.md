# Call-scoped submission association recovery

Date: 2026-10-01
Status: managed recovery and focused validation passed; production installation/CLI open

Previous turn made progress: managed selection reader and 77 passing tests in
eleven suites (attempt 493). Continue the same Phase toward real CLI assembly.

Freeze edits to ManagedHostRecordRead, the selection reader, the Step-close
Operation, GoalPhaseSubmissionAssociation and the managed suspension spec.
Extract the existing call-scoped managed read seam so both readers use the same
DataStoreSpace bind/load/restore path without acquiring private infrastructure.
Require canonical logical store names. Step-close supports a call-scoped host
InvocationPort while retaining existing injected-port compatibility; never
capture a Factory context for future requests.

Retain immutable submission identity plus the exact canonical Start WAIT wire
as host association provenance. Reuse WorkflowWaitJsonV1 for Handle decoding;
do not introduce a second Handle schema. The retained WAIT is not mutable
progression authority: the existing Step-close gate loads current authoritative
instance state and validates the recovered Handle and lifecycle before issuing
work. Prefix opaque application JSON for lossless SQL storage; wrong lookup
identity, absent records, malformed envelopes and context-free calls fail closed.
The reader has no write/install/upsert or public Operation.

The executable example uses a test-only installer to Create the association
after actual Start and deliberately stops before review issuance. Shutdown and
reopen discard the original Start wire/installer state. A new host with only the
main association reader must issue review on the exact recorded Start instance;
missing associations must leave it unchanged. Existing selection-reader tests
continue to validate escaped lease denial and reopen recovery.

This is not production trusted installation, explicit collection grants,
authenticated provenance, atomic Start/association installation, exact input
scope, command/closure authorization or default CLI activation. Those remain
open; no CNCF public API, alternate Workflow engine or private pool is added.
Validate actual packageBin plus the existing eleven-suite accumulator.

Attempt 494 (SMWF-CALL-SCOPED-ASSOCIATION-DIRECT-20261001-494) passed actual
Compile / packageBin and all 78 tests in eleven suites. SBT/wrapper exited 0,
lock released; no aborted/failed/canceled/ignored/pending cases. Complete summary
read at
`/tmp/skill.cncf.d/cncf-sbt-d5f336e89482ae3d185b12141613c0286cb563a6ca7decbbefe43e74ba8c9443-51de14a76c9976a156e39fe988b8d95b/74185-20261001T022535Z.summary.json`.
The new SQLite example proves recorded association recovery after shutdown/reopen
and normal registered Step-close issuance on the exact Start instance with no
conversation association Map. Foreign lookup/prefix and missing/context-free
association calls fail closed. Selection reader reopen and escaped-lease denial
remain green after read-seam extraction. git diff --check passed.
These are bounded integration facts, not a production installation/grant/CLI
acceptance receipt. All outstanding obligations above remain open.
