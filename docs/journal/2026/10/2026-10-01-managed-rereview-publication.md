# Managed re-review publication

Date: 2026-10-01
Phase: sm-workflow Phase 2
State: IMPLEMENT

Retain explicit user parent-direct limited recovery. Own
GoalPhaseManagedRepairPublication.scala, GoalPhaseManagedSuspensionOperationSpec.scala
and this journal; preserve every other delta. No producer, pool, schema/ABI/CML,
public Operation or legacy compatibility change, commit or acceptance claim.

Bind retained repair completion on the existing authorized Component storage.
Advance from an opaque retained repair and verified history, then publish one
deterministic full-review successor. First publication reads authoritative bytes,
keeps old evidence and atomically retains suspension, Continuation and issued
input. Retry/reopen reads the exact issued candidate without calling SnapshotPort.
Verify complete projected current record and issued headers, context, policy,
original submission/Step and retained evidence before returning it. Refuse foreign
revision/policy or missing predecessor before reading bytes or mutating storage.

Extend adjacent Given/When/Then behavior: first publication advances revision 5
to 6 with changed-byte candidate and expired evidence; snapshot failure before
publication leaves revision 5 unchanged; retry/reopen with unavailable bytes
returns identical work and no extra history. Validate the same six-suite serial
command as 457. Public advance/default factory wiring, concurrency/failure/process
coverage, independent reviews/full validation and commits remain unfinished.

After publication validation 458 passed, extend this same owned executable
scenario to return fresh full-review evidence for the issued repaired candidate
through normal Subsystem submitStepReview, retain its exact result at revision 7
and reject replay. When the fixture includes repair ports, its review clock now
uses their explicit host clock rather than the original first-review clock.
No production timing policy or Step commit authority changes. Revalidate the
same six suites on these final bytes; 458 is preliminary evidence only.

## Final focused evidence

Added internal advanceReReviewC using the same captured Component storage:
retained repair completion, immutable original review authority and completion
prefix; declared full-review SPI/policy; first authoritative byte read; atomic
managed suspension and issued input. Retry/reopen uses retained input and verifies
the complete projected successor and exact WorkOrder. No old review evidence
is erased or interpreted as acceptance. No callback or process-local Intent is
required. Different current advancement is refused.

Final invocation `SMWF-MANAGED-REREVIEW-DISPATCH-DIRECT-20261001-459` passed
all 51 tests in six suites, zero failures/aborts. SBT and wrapper exit 0,
lock released; persistent summary was read after terminal completion.
The six-host scenario covers review completion, repair publication/completion,
re-review first-read failure, atomic issue, retry and reopen with unavailable
bytes, and fresh full-review completion/replay refusal at revision 7 through
normal Subsystem dispatch. The Step stays open with no Git commit authority.
All legacy repair/review regression suites passed too.

- Log: `/tmp/skill.cncf.d/cncf-sbt-d27b23453d206e5839ab2ee2b4ad53da415aa5113553578081b1fa6c30118215-3c57dd156bbb3025f7e50269e151bed3/55522-20260930T234010Z.log`
- Summary: `/tmp/skill.cncf.d/cncf-sbt-d27b23453d206e5839ab2ee2b4ad53da415aa5113553578081b1fa6c30118215-3c57dd156bbb3025f7e50269e151bed3/55522-20260930T234010Z.summary.json`

Owned source identities captured during and after final invocation matched:

| Source | SHA-256 |
| --- | --- |
| GoalPhaseManagedRepairPublication.scala | `eb1b1068e744405c71cbeaeac90a5302b7e37e679e3afd3a8d285b8dffa4e8e9` |
| GoalPhaseManagedSuspensionOperationSpec.scala | `b129e30a133e71f788cf0e5bc8f237ca71181c88fa942cdd724b71fbe3e64fb4` |

The CNCF SNAPSHOT remains
`2c4a0f75d1e8ce4241deaa14d13ff5b1bdd03983546a0eb83878ac158332e2b1`.
No producer republish or Cozy/CNCF source edit was performed. `git diff --check`
passed. No live SBT remains. Existing generic WorkOrder warning is retained;
no warning-free or full-suite assurance is claimed.

This is internal managed publication/completion evidence, not public advance
Operation, CLI/server parity, concurrent publication/failure-injection proof,
default factory configuration or durable Step closure/commit integration.
Next integrate these managed transitions with complete service composition and
the workflow-owned advance operation; the skill must not select branches.
Independent acceptance reviews, full Phase validation and release commits remain
required. Keep the full Phase 2 goal active.
