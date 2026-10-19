# Phase 6.3: single-repository production use and development handoff

Date: 2026-10-18
Status: adopted planning clarification; no live-state or product acceptance change

## User decision and delivery boundary

The user confirmed the production-availability breakdown and requested that
single-repository `sm-repository-sync` be explicitly usable at Phase 6.3 closure.
The [roadmap](../../../phase/README.md#production-availability-by-completed-phase--clarified-2026-10-18)
now lists each Phase 6/6.1/6.2/6.3/6.4/7/8/9 delivery and its production boundary.

Phase 6 supplies installed bounded workflow integration; 6.1 adds runtime-owned
commands/live plans/ordinary Step admission; 6.2 adds repair/recording/decisions.
Phase 6.3 accepts the integrated GoalPhase/SplitPhase/RepositorySync families and
starts gradual production use. Phase 6.4 adds evidence storage to those usable
workflows. Phase 7 adds multi-repository/worktree/artifact-overlay operation.
Phase 8 selects actual-use extensions and does not promise every optional
transport/provider. Phase 9 adds observed convergence-based provider switching.
CBD review integration remains independent Phase 10, not an initial-use wait.

The existing installed-use obligation now has the explicit completion row
P63-USE01 in the [shared checklist](../../../phase/managed-test-suites-phase-checklist.md#phase-63-production-use-acceptance--2026-10-18).
It does not introduce a second review campaign or duplicate the transferred
P6-SQ ledger. No previously unchecked item is accepted by this edit.

## Instructions for the active development chat

1. Adopt at the next ordinary planning/dispatch boundary. Preserve the active
   Goal/base/PLAN epoch, current repair/finding history, issued requests and all
   tracked/untracked source/generated files and evidence. Collect in-flight
   results under their issued contract before judging applicability. Do not
   copy historical counters into the live plan or restart development.
2. Keep accepted PLAN/COMMAND and other bounded results accepted. Continue the
   revised CBD-independent 6.1 admission route and any independently necessary
   Action/UoW repair. This clarification adds no new 6.1 producer dependency,
   does not retarget an active worker, and does not activate Phase 6.3 early.
3. At integrated planning, map existing installed RepositorySync evidence to
   [the explicit 6.3 condition](../../../phase/phase-6.3.md#single-repository-production-acceptance--2026-10-18).
   Reuse applicable results at their original scope. Source compilation, mocks,
   no-effect runs and direct Operation tests alone are not production acceptance.
   Identify only missing connection/effect/setup cases; avoid repeating every
   earlier scenario or rebuilding the RepositorySync engine.
4. Exercise the ordinary installed `sm-repository-sync` on an admitted repository
   and remote: preserve intended local state -> fetch/classify -> integrate ->
   actual semantic conflict/decision where needed -> applicable validation/review
   -> permitted non-force push -> convergence confirmation. Record one actual
   connected success and applicable preservation/conflict/failure evidence.
   A real isolated remote can supply controlled Git effects; document transport
   limits and separately establish actual host/authentication setup for advertised
   GitHub use. No fabricated review/permission or push to an unrelated remote.
5. Retain proper installed setup/invocation and known limits in normal usage
   documentation. `development-workstation` is an example of intended single-
   repository use, not a hard-coded repository or push authorization. Basic sync
   must not be moved into Phase 7 merely because that Phase also owns sync work.
6. Accept P63-USE01 only with attributable actual results. Keep aggregate full
   validation owned by PHASE-6.3 for [6, 6.1, 6.2, 6.3] and the existing independent
   Phase review/closure. Do not add another full-suite run or review cycle solely
   for this named criterion. Phase 6.3 stays open if basic installed sync is still
   unusable; unavailable optional worktree/overlay/CBD features do not block it.

## Preservation and verification boundary

All prior execution successes/failures, reviews, scope limits and source changes
remain intact. Changed acceptance is assessed only at its actual affected scope.
The current runtime must be checked by its owning development chat; this plan
does not claim that any pending fix, installed route or deployment has completed.
No source/generated file, private workflow record or Git index is changed here.
No runtime test, push, deployment, new Goal or message to another chat is issued.

Documentation validation: seven affected documents and 189 local link targets
checked; new referenced section targets exist. P63-USE01 has one unchecked
completion row. The four-member aggregate sequence/owner and five deferred CBD
rows remain unchanged. `git diff --check` passes. These are document checks,
not product validation or installed-use acceptance.

## Later clarification: three distinct use tiers

The user subsequently clarified ordinary SplitPhase and single-repository
RepositorySync use at 6.3, limited single-repository GoalPhase use at 6.3, and
full practical GoalPhase adoption with related-project management in Phase 7.
The [updated handoff](2026-10-18-three-skill-production-tiers-handoff.md) records
this allocation and P63-USE02/03 without changing P63-USE01 or earlier evidence.
