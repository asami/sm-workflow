# Call-scoped closure authority lookup

Continue the same active Phase 2 under the user-authorized parent-direct limited
recovery. Previous turn was a status restatement, not implementation progress.
The existing post-resolution Factory hook remains selected; no new CNCF API.

Bounded implementation owns GoalPhaseClosureAuthority.scala,
GoalPhaseStepCloseServiceOperation.scala and
GoalPhaseClosureAuthorityExecutionSpec.scala. Preserve unrelated changes.
Introduce call-scoped authorization and Decision ports, rejecting context-free
access. Bind ephemeral proxies to the current ActionCall.Core in both Step-close
authority branches. Keep existing injected ports compatible and keep exact
candidate/instance/authority double-read admission unchanged. Neither the proxy
nor a returned Decision grants authority or installs a record.

Executable scenario: Given call-scoped host ports, When Service/CLI Step-close
finds no authorization, Then both lookups receive the current core and history
and commits stay unchanged. Context-free lookup fails. Given separately retained
exact authority, When a new call closes the Step, Then it uses a fresh core and
commits once. Existing authority rejection/race tests remain the accumulator.

Validate actual packageBin and the previous eleven suites plus the closure
authority execution suite. Production durable authorization/Decision adapters,
trusted installation, collection grants, managed review-completion auto-close,
external commands and default CLI activation remain open. No release or commit.

Attempt 495 (SMWF-CALL-SCOPED-CLOSURE-DIRECT-20261001-495) successfully
completed Compile / packageBin and all 86 tests in twelve suites, with zero
failed/aborted/canceled/ignored/pending cases. SBT and wrapper exited 0; the
shared lock was released. Complete structured summary read at
`/tmp/skill.cncf.d/cncf-sbt-0e78c5a0b4bae591823cdb9c3657bd2d5bdcf5f203a7715b095eacb4738dadb6-0646bfe8f3a1e0da2c478383c29fd5c7/78101-20261001T023251Z.summary.json`.
git diff --check passed. No live SBT session remains.

The new Service/CLI behavior proves call-scoped proxy propagation on the
pre-reviewed path and context-free denial, plus separate-authority exact-once
closure using a test commit port. The automatic authority branch also binds
the current core, but has no new dedicated call-scoped behavioral scenario in
this receipt. This is not evidence of durable closure authority, production
Git execution, or production default CLI assembly. Managed review completion
still needs its own current-call integration rather than a captured factory
context. Keep the full Phase goal active.
