# Managed review SPI preflight

Date: 2026-10-01
State: IMPLEMENT; sm-workflow Phase 2

Continue the explicitly authorized parent-direct limited recovery. Own
GoalPhaseManagedRepairPublication, GoalPhaseManagedServiceAssembly,
GoalPhaseRepairCompletionOperation, GoalPhaseManagedSuspensionOperationSpec and
this journal. Preserve all other changes; no producer/ABI/CML/public Operation,
pool, schema, default factory, commit or acceptance change.

Move the exact existing generated ReviewStep SPI check into a pure shared
admission function. Use it before any Service storage binding and again at
re-review publication. Reject a foreign capability/action/operation/input/result,
missing completion/evidence metadata, or a requirement omitting full review.
The ordinary four-Operation Service must not advertise unusable follow-up ports.
Add negative assertions next to the existing create_Core Service composition
setup and retain the adjacent Given/When/Then normal dispatch scenarios.
Validate the same six-suite accumulator as attempt 462. Existing success is not
production CLI, public recovery, durable authority, or Phase acceptance evidence.

## Observed validation

Attempt `SMWF-MANAGED-REVIEW-SPI-PREFLIGHT-DIRECT-20261001-463` compiled the
three changed main sources and the changed spec, then passed 52 tests in six
suites. SBT and wrapper exited 0; lock released. The structured summary was read:
`/tmp/skill.cncf.d/cncf-sbt-ce01f3a90d7d3ff17285b98354db59083f2dfafd3171ffcb2793b7b77ad838b4-748b2487cbf75a70aaf6ca83300ff806/65583-20260930T235708Z.summary.json`.
The adjacent `.log` retains full diagnostics. `git diff --check` also passed.

The existing Start/reopen and automatic repair/re-review Given/When/Then
scenarios exercise additional create_Core preflight assertions: ten foreign or
missing SPI shapes fail both the review-only and four-Operation Service binding,
and requirements without full-review policy/capability fail repair binding.
These failures occur before the Start storage binder in main composition.
The shared pure check is also called before re-review publication, preventing
composition and recovery from maintaining different SPI admission algorithms.

Normal automatic follow-up remains green. Public interrupted-follow-up recovery,
production default factory/configuration, durable host authority, authorized
managed Step closure, launcher/server parity, independent acceptance reviews and
full Phase closure remain open. No commit, publication, release or Phase
acceptance is claimed.
