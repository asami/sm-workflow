# Release repair candidate recovery

The existing `describeGoalPhaseWholePhaseCandidateRecovery` and
`recoverGoalPhaseWholePhaseCandidate` operations cover a limited release repair
frontier: every Step is closed, an ordinary release repair was accepted, and the
original inspection has a complete contiguous source-history and staging prefix,
but no candidate, observation intent, or commit-hygiene plan/result was retained.

The optional `releasePreparation` target field captures the exact resolved
source-history selection, actual history intent/result pairs, and commit hygiene
mode. History pairs precede the exact staging pairs. Identities, consecutive
native revisions, selected ownership, completed Steps, full Plan, original base,
tip, and repair counters remain bound. Targets without this field keep their
existing wire representation and history-free whole-Phase interpretation.
Unknown fields, incomplete or crossed prefixes, later effects, and noncanonical
targets refuse recovery.

For release recovery, physical pending paths must be owned by the unchanged Plan
and covered by an actually selected owner. A directory selector covers its
slash-bounded descendants; a selected Phase repair inventory entry covers only
its exact literal path. Pending rename endpoints are checked separately because
the typed pending-path observation disables rename detection. Repository snapshots
also require both rename endpoints to remain owned. An unrelated unstaged catalog
may remain in the worktree; staging that catalog refuses description. Legacy
whole-Phase management selection retains its existing exact-path checks.

Description uses ordinary typed Git observations and checks the pending index
against the captured parent. Only an index with no findings can offer the
remaining observation. It does not correct files or stage paths. As with existing
whole-Phase description, `IndexTree`/`write-tree` can write Git objects or index
cache. The exact current repository/index, Phase validation and process policy,
diff budget, source-history selection, and hygiene mode must still match.

An independent fresh Human answer must attest from actual terminal invocation
evidence that the prior invocation ended. The input Boolean, prior Submit, and
passive preparation alone do not establish that evidence. Recovery appends only
the existing `AuthorizeNewObservation` direction; `Defer` grants no effect.
The historical outcome stays `UNKNOWN` with `termination-unconfirmed`.

A separate ordinary Advance persists a fresh observation intent before effects,
rechecks the exact current repository/index and policies, performs the missing
read-only `CheckPendingIndex`, and records a clean, correction-free hygiene record
under the new direction's `commit-hygiene-plan`. A second clean check precedes
candidate capture and the existing Phase checks and independent review. It never
repeats source-history application, staging, or accepted Submit, and never applies
hygiene corrections. If a check fails or requires changes, the fresh intent stays
unresolved with no automatic retry.

`GoalPhaseWholePhaseCandidateRecoverySpec` covers canonical legacy and release
targets and crossed/incomplete history, stages, rules, identities and revisions.
`NativeGoalReleaseRepairCandidateRecoveryCliSpec` provides separately selected
OLD setup and NEW resume stages on one isolated test-owned repository. The parent
runs `smworkflow.spec.releaseCandidateStage=setup` before publication and `resume`
afterward, with explicit `smworkflow.spec.installedCommand`,
`smworkflow.spec.releaseCandidateOldInstallations`,
`smworkflow.spec.releaseCandidateNewInstallations`,
`smworkflow.spec.releaseCandidateLocator`, and normal `smworkflow.spec.sbt` paths.
The locator is created once by setup and consumed as input by resume. The fixture
uses actual public Start/Submit/Advance, typed Compile/Test and Git, independent
test-owned review/authority/Human preparation, and read-only retained evidence.
It proves the operational boundary; its test-owned answers are not genuine Human
consent. Execution results must be collected from those runs before acceptance.

The explicit fixture option `smworkflow.spec.releaseCandidateDirectoryOwners=true`
selects a new `p69-release-directory-` repository with exactly `src/test/scala`
and `build.sbt` as selectors. Two physical Scala files and harmless build comments
change in the Step and release repair. The tracked catalog remains changed only
in the worktree. This variant uses the dedicated NEW `target/p69rcr/installations`
and creates `target/p69rcr/directory-old-new-locator.json` once. It includes a
fixture-owned staged-catalog refusal before Human authorization, then restores
only that catalog index entry and checks exact retained facts before continuing.
It does not reopen or rerun the completed exact-file fixture. Without the option,
the original exact-file setup, resume and verify semantics remain unchanged.

The fixture-only `smworkflow.spec.releaseCandidateStage=verify` finishes retained
assertions if a test assertion failed after NEW already reached ReviewPhase. It
requires the original locator and an absent final result file, reopens with log
index 2000, reads the actual saved direction and `new-authorize.json`, and obtains
the pending ReviewPhase through ordinary Advance. Native instance, effect counts
and check logs must remain exact across that readback. It retains the accepted
Step review and requires no prior Phase review. It prepares no new Human answer
and repeats no recovery, candidate preparation or checks; OLD setup and NEW logs
remain retained. This stage is test evidence completion only.
