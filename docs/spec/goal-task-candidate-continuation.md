# GoalTask interrupted candidate capture

Ordinary `advanceGoalTask` continues an existing `CandidateIntent` using the
original Task input, conditions, accepted semantic result and current Git state.
It does not require another Start, repair submission or incident-specific API.
The shared bounded-work native owner also serves standalone and delegated work.

The owner observes the current selected repository and index before staging.
Already staged selected content, including a staged deletion, needs no new
staging. Only owned paths with current unstaged or untracked changes require
their existing typed staging operation. The resulting actual selected index
paths, diff and tree become the ordinary candidate.

Candidate selection retains the same Task, Session, original request, acceptance,
base, semantic references, history, generation, selected checks, repair counts
and limit. Existing applicable complete results use ordinary candidate admission;
an unvalidated repaired candidate proceeds to its previously selected validation.
Reading a retained capture intent is not proof that SBT started or completed.

An unresolved repository/base/ownership choice, pending merge or extra staged
path returns `Human` with the actual changes and original owned paths. The owner
does not unstage, reset, commit or choose another worker's changes. Human is not
required merely because candidate capture was interrupted.

Validation, commit and publication uncertainty retain their existing collection
contracts. This correction permits no replay of their intents, new repair window,
counter refund, generic concurrency guarantee or application-specific recovery.
Normal progression owns the one subsequent validation; a later Advance collects
its retained review/work boundary rather than submitting the repair again.

Executable specification: `BoundedWorkNativeOutcomeSpec`. Isolated repositories,
retained checkpoints and controlled process outputs are specification-owned;
they do not claim execution or recovery of an actual UserAccount Session.
