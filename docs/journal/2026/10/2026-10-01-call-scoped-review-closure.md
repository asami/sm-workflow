# Current-call binding for reviewed closure

Continue active Phase 2 under parent-direct limited recovery. Previous turn
proved SQLite authorization reader reopening and passed 89 tests. Own private
CallScopedOperationBinding, GoalPhaseReviewCompletionOperation, the shared
GoalPhaseApplicationFixture and one new GoalPhaseRepairLoopSpec scenario.

The canonical completion codec has no ActionCall.Core callback argument.
Avoid new CNCF public API and factory-held context: wrap the existing declared
Operation only for reviewed-authority auto-close. Retain its specification and
initial decoder. At execution, construct the codec with the current core and
delegate to the same canonical completion Operation. It still owns admission,
closing, persistence and replay denial; the adapter does not implement another
completion engine. The current core exists only for this call, including its
post-commit authority double-read/Decision lookup. Null context fails closed.
No factory-global state or Workflow permission is inferred from a review.

Executable scenario covers Service/CLI accepted review with/without separately
supplied test host authority: both lookups see the current completion context;
absence yields Decision, exact fixture authority yields one test commit, and
replay causes no further lookup/effect. Existing fixtures remain compatible.
This is not the managed SQLite closure engine: managed review assembly still
rejects independent history append, and durable receipt/Decision integration,
trusted authority installation, controlled Git and default launcher are open.
Validate actual packageBin plus the twelve-suite accumulator from attempt497.

Attempt 498 compiled/packageBin successfully but failed nine closure scenarios
(81/90 passed). SBT/wrapper exited 1 and lock released; complete summary read at
`/tmp/skill.cncf.d/cncf-sbt-068d87e745354881bf4d725b9a4513c296ebae209918b04e5b0d6f02fb5c2e3c-621ee065b4b1719e8c8767c4ed0faa19/90478-20261001T024410Z.summary.json`.
Root cause: canonical completion executes its closing selection in execute,
then performs completion/projection in afterCommitResponseC. The adapter had
delegated only execute, returning void and dropping the second stage. Retain
one delegate in each call, forward afterCommitResponseC exactly once and clear
that reference. No global capture or replacement completion engine; the same
canonical call owns both lifecycle stages. Retry the unchanged accumulator.

Attempt 499 passed actual Compile / packageBin and all 90 tests in twelve
suites, including the new current-call Service/CLI cases and all nine prior
regressions. SBT/wrapper exited 0; lock released, zero failed/aborted/canceled/
ignored/pending cases. Complete structured summary read at
`/tmp/skill.cncf.d/cncf-sbt-9ab5ca9457522445fabdfe9248c0d1580a88b14bc0ee37a940a6b72d8ea8560d-f43d97e8e13e3aadd1e6f51d1fb6fd80/91277-20261001T024553Z.summary.json`.
git diff --check passed; no SBT session remains. Accepted review alone returns
Decision with no commit; independently supplied exact fixture authority allows
one test closure, both reads have the same completion call core, and replay
does not look up authority again. Legacy static ports and retained projection
remain compatible.

This is a prerequisite current-call bridge, not production managed closure:
the new behavior uses fixture persistence/receipt/commit ports. Managed assembly
still has no authorized closure append route, durable Decision/accepted-proof
adapters or controlled Git. Real SQLite completion-to-authority integration,
trusted installation/grants and default CLI remain required. Keep Phase active;
no acceptance review, release or commit was performed.
