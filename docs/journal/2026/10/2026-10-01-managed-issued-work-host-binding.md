# Managed issued-work host binding

## Frozen dependency frontier

Continue sm-workflow Phase 2 and its admitted CNCF Phase 88 dependency.
Binding admission 423 passed 49 tests; actual issued-work recovery still lacks
a public opaque Subsystem-managed adapter. Preserve existing compound issue
semantics rather than publishing a WorkOrder separately or constructing SQL
inside the application.

Target programs in CNCF: `subsystem/Subsystem.scala` (whole source read), new
`workflow/WorkflowManagedIssuedWork.scala`, and new
`workflow/WorkflowManagedIssuedWorkSpec.scala`. Add internal pure admission of
the exact captured Component/store/instance/Continuation grant before any pool
access. The new public binding retains only immutable host inputs, not a store
or lease. Every suspend/read/identical-retry call checks admission and borrows
one verified common-pool view for its synchronous duration. Atomic suspension
uses the existing compound transition and derives its owner from the bound
Component. Do not expose preparation objects beyond a lease, raw stores,
claim tokens, SQL or permission-selecting callbacks.

Executable spec must exercise actual managed SQLite issue, immutable recovery,
host shutdown/reopen, mismatched collection/store/component rejection and
post-shutdown failure. Existing issued-work and managed-completion suites remain
regression gates. This is not production CLI assembly, migration completion,
full Phase validation or acceptance. No commit or remote publication.

## Executed focused validation (424)

`CNCF-MANAGED-ISSUED-HOST-DIRECT-20261001-424` compiled the new binding,
Subsystem admission and executable spec. Four suites completed, 29 tests
passed, none failed or aborted. SBT/wrapper exited 0/0 and released the lock;
CNCF `git diff --check` passed before and after validation.

The new host spec proves one managed provider while a host is active, zero
outstanding leases after each call, atomic issue with exact immutable recovery,
rejection of separate initial issue and stale suspension replay, failure after
shutdown, and reopening the same SQLite file through a fresh host. It also
checks captured-grant rejection for foreign collections, store and Component,
and bound-configuration rejection without publishing work. Reopen is within
one JVM, not separate-process launcher evidence.

Evidence:
- log: `/tmp/skill.cncf.d/cncf-sbt-e4c12bf007d3c1432c4308321fbf40326ae8bc70473210937965e6f3ff11efe6-c328b7993b33187a1cf10a8fb692e84e/87588-20260930T213603Z.log`
- summary: `/tmp/skill.cncf.d/cncf-sbt-e4c12bf007d3c1432c4308321fbf40326ae8bc70473210937965e6f3ff11efe6-c328b7993b33187a1cf10a8fb692e84e/87588-20260930T213603Z.summary.json`

Next: refresh the admitted CNCF SNAPSHOT after these producer changes, then
compose the real GoalPhase managed issued-work and completion ports in the
application. The synchronous suspension API deliberately exports no prepared
transition whose lease could expire; integration must preserve Operation/UoW
publication ordering rather than use it as an eager pre-commit side effect.
Existing completion binding admission is not end-to-end closure evidence.
Full Phase validation, independent acceptance and release remain pending.

## Managed suspension commit frontier

Operation integration revealed that the opaque suspension entry must use the
host DataStoreSpace policy and managed UnitOfWork, not merely call the borrowed
provider's immediate compound transition. Freeze the same new binding and spec
for this correction: prepare the existing exact issue inside its live lease,
build its existing host participant, commit one managed UnitOfWork, and return
only verified storage projection. Ordinary event records may accompany issue;
the captured EventStore policy selects persistence and delivery. No raw
prepared value escapes, no external UoW callback performs a second storage
commit, and no cursor initialization is inferred. Update the host spec to
explicitly initialize its first-install cursor. Reopen must not initialize it.

Validation `CNCF-MANAGED-ISSUED-UOW-DIRECT-20261001-425` passed the same
four suites / 29 tests, with SBT/wrapper 0/0 and lock released. The host issue
now commits through the existing policy-admitted participant and managed UoW,
returning verified storage only after its completion. No lease-bound object
escapes. The current new spec covers an empty accompanying event batch;
event-policy composition has existing lower-layer coverage and still needs
direct issue-event integration coverage before acceptance.

Evidence log:
`/tmp/skill.cncf.d/cncf-sbt-1530380c259ee7775d71b81cbf18e5ab977fd84aa03efe57afd9816736f68575-d27787e641c1796cc22fb88162b81ba8/88828-20260930T213855Z.log`
and adjacent `88828-20260930T213855Z.summary.json`.

## Producer refresh and consumer check (426–427)

Invocation `CNCF-MANAGED-ISSUED-REFRESH-DIRECT-20261001-426` ran
`--batch`, `show organization`, `show name`, `show version`, `publishLocal`.
It refreshed unchanged coordinates `org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT`
in local Ivy, with SBT/wrapper 0/0 and lock released. Project wiring adds only
the target runtime-catalog export to publishLocal; no remote publication or
release warehouse was selected.

Consumer `SMWF-MANAGED-ISSUED-CONSUMER-DIRECT-20261001-427` showed the actual
Test dependency classpath and re-ran seven GoalPhase suites. It recompiled 73
main and 62 test sources; 49 tests passed, seven suites completed, none aborted,
SBT/wrapper 0/0, lock released. Its selected producer jar was
`/Users/asami/.ivy2/local/org.goldenport/goldenport-cncf_3/0.5.3-SNAPSHOT/jars/goldenport-cncf_3.jar`,
SHA-256 `685f8ed654b3216f639f9e2a2616a9803cd1901ca665a590434e7681e81c590a`
before and after consumer validation.

Evidence logs and adjacent summaries:
- `/tmp/skill.cncf.d/cncf-sbt-b0eae197005ca54190a5d2a1cd7f1cf9a5888cf53a8769698c0c5b41a25224c6-d6932bd1579cb20fe3a7cfa5051d9506/89414-20260930T214004Z.log`
- `/tmp/skill.cncf.d/cncf-sbt-5f9b90135c7f86f3ffd1da08bbec21f56d4a27392afcb2d5cfe351b60083366f-de9f8f49bf2064ea51dccc7472be1331/90558-20260930T214050Z.log`

Next application frontier remains `GoalPhaseStepCloseServiceOperation.scala`
and `GoalPhaseReviewBoundary.scala`: pure WorkOrder projection from the planned
suspension, then one managed suspension/issue call before response publication,
without legacy stage/append/claim/independent issue. Preserve generated request
and completion contracts, current host-associated Handle and typed candidate
assessment. These application changes and their real managed-operation spec
have not yet been made. Do not infer their acceptance from these regressions.
