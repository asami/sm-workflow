# Fixed SBT validation runtime adapter

Status: connected RepositorySync success/failure and GoalPhase whole-Phase SBT
validation passed1101 (3/3), including fresh-runtime no-replay observations.
1099's scripted child-build failure is retained; required recovery and integrated
acceptance remain open.
This document describes the product adapter and its test harness, not Phase2
acceptance, a skill client, or authority to operate on a user's live repository.

## Host selection and Operation execution

`SbtValidationProgram.definitionC` constructs a trusted host program for one
`sm-workflow-repository-validation-N` capability (ordinal1..16). The host fixes
the absolute repository, Java executable, SBT launcher JAR, runtime classpath,
ordered SBT command vector, environment and CNCF process limits. Each SBT command
is one argv element; no shell interprets it. The allowed request arguments are
exactly the empty vector. The normal runtime process-plan schema installs this
definition and grants the selected capability to the component.

RepositorySync and GoalPhase already select that capability from their saved
host validation policy. Public `advance` and semantic completion inputs cannot
select a launcher, command or directory. A semantic result reports only its
assigned work. The owning Workflow persists intent before invoking the existing
CNCF ProcessExec and persists its actual bounded receipt afterward. No new
pool, SQL writer, Workflow engine, permission or automatic retry is introduced.

The adapter is a Java entry point in the component runtime. It launches the fixed
SBT JAR with the **actual OS working directory** set to the selected repository,
not a changed Java `user.dir` over a different native cwd. SBT and its forked
product tests therefore have the same selected base. It inherits the CNCF-selected
environment/stdio and returns the actual launcher exit status. CNCF owns the
outer process timeout, output capture/budgets, cancellation and descendant-tree
termination. A launch error is nonzero; it is never a fabricated successful test.

The declaration is invocation control, not an OS sandbox for arbitrary build
code. A host must trust/review the selected build and task effects before granting
them; a fixed `test` command does not make all code in `build.sbt` harmless.
No credentials, live remote write or general arbitrary-command permission is
granted by this adapter. The product does not depend on a cncf-* skill or the
development SBT wrapper. Shared-machine scheduling remains a host responsibility.

## Direct-Operation executable specification

`ThreeWorkflowSbtOperationSpec` uses the production three-service composition,
normal process plan, SQLite/DataStoreSpace and public RepositorySync/GoalPhase Operations.
It prepares only test-owned input/evidence/build files and local bare repositories.
It does not inject a process driver, seed Workflow history or claim test-owned
evidence is a real Human/reviewer issuer.

The selected owned build's `Test/test` compiles and executes `ProductCheck` against
an actual source file. A correct file passes; an incorrect file raises a real
assertion and makes SBT fail. An external marker records the executing SBT PID.
The Operation must save that output and exit, converge only after success, and
retain a failed check as Wait without replay. Fresh-runtime observation must keep
the same native history, result and single process entry.

The one-Step GoalPhase example starts from an actual clean committed build. The
specification changes only the assigned `value.txt` input and returns its assigned
semantic result. Workflow runs the actual Step SBT check, supplies its receipt to
review, commits the separately authorized reviewed Step, and runs the actual
whole-Phase SBT check. Separately evidenced Phase closure invokes the installed
RepositorySync Operations and their third real SBT check before convergence.
Fresh runtimes retain exact pending work and the whole-Phase terminal; three
distinct SBT PID entries remain three, not four. Review/effect evidence is test-owned,
not production Human issuance. These scenarios do not prove every failure or
uncertain-effect resolution, separate-JVM SBT recovery or integrated Phase acceptance.

The suite is `DoNotDiscover`; ordinary `test` discovery must not launch nested
SBT. Its selected runner verifies actual process ancestry of the owning top-level
invocation. The test-only Scala2.12 `runtimeScripted` project runs it under one
`runtimeScripted/scripted runtime/operation-sbt` invocation. The scripted child
owns the Java Operation caller and its managed SBT descendants sequentially.
The root component remains Scala3 and is neither republished nor made an sbt
plugin. `scriptedDependencies` compiles the actual root test/runtime inputs and
exports selected classpath/launcher/owner data; it does not invoke `publishLocal`
or create an unchanged-content certificate.

Development runs use the registered command runner and shared serial wrapper.
The user's AGENTS contract permits children managed by one top-level scripted
invocation; the wrapper holds its lock until that invocation terminates. There
is no wrapper recursion, independent concurrent top-level SBT, lock bypass or
manual-parent test route. This use follows the existing
[sbt scripted framework](https://www.scala-sbt.org/1.x/docs/Testing-sbt-plugins.html).
Actual1100 used a fresh registered cncf_command_runner, gpt-6-luna/medium,
/root/sbt_receipt_6a13501d95074e62b2d5. Parent admitted the current typed request/
result and consumed permission; SBT0/wrapper0/lock released,2/2 tests in one
completed suite, no aborts/skips/failures. Original log:
/private/tmp/skill.cncf.d/a-72ee374b-915b-4cbe-b1f0-22a4b70c2adf/12163-20261007T025010Z.log.
The failing **product** check is an expected successful spec scenario; it is not
rewritten into a successful product-test receipt. Both actual receipts and
one-entry markers survive fresh-runtime reopening. That earlier run did not prove
GoalPhase SBT completion, separate-JVM SBT recovery or formal independent acceptance.
Actual1101 adds the whole-Phase example:3/3 in one completed suite, no failures/
aborts/skips, SBT0/wrapper0/lock released and consumed permission/current typed
result admitted. Registered cncf_command_runner, gpt-6-luna/medium,
/root/sbt_receipt_5e275c88b8174e7d825b. Original log:
/private/tmp/skill.cncf.d/a-a173ae89-c771-453c-816a-cc5a5e2fb100/18718-20261007T030217Z.log.
The relevant Step, Phase and repository receipts and actual committed/converged
postimage now pass through public Operations. Formal integrated acceptance remains open.
See the [Phase2 checklist](../phase/phase-2-executable-specification-checklist.md)
for actual validation and still-open acceptance work.
