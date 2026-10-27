# Native pending-merge validation

## Phase 6.9 target allocation — 2026-10-27

The adopted [runtime-owned synchronization contract](repository-sync-runtime-orchestration.md)
is delivered by [Phase 6.9](../phase/phase-6.9.md), for both clean and dirty entry.
It replaces blanket sync-triggered product tests/reviews with impact selection
and applicable result reuse, and handles large diffs through evidence access
rather than size-only Human gates. Existing implementation/evidence statements
below retain their historical scope until qualified public adoption.


Date: 2026-10-06. Parent-owned connected implementation; acceptance remains open.

Current connected CML selection is `repository-sync-native-merge-validation-v7`;
stored earlier selections are not rebound. Fresh1001 passed322/322 tests in25
completed suites, zero failures/aborts/skips, SBT/wrapper0 and released shared
lock. Parent verified actual current typed result and consumed permission.
All six added public scenarios passed.1000 first failed main compilation on
nested automatic codecs; explicit bottom-up codecs corrected that cause. Strict
generated state projection and new Wait.expectedRevision assertions were also
aligned before1001. Full655 through1001 retains the failure and all earlier
history. No independent integrated verdict or practical acceptance follows.

[Actual1001 log](/private/tmp/skill.cncf.d/a-f35e9cbd-50c9-477d-b8c2-7dbb391b4e4d/76622-20261006T032548Z.log).

The owning `advanceRepositorySync` consumes the actual uncommitted
`PendingMergePrepared` record. An explicitly installed host validation policy
selects either no product tests (a reason is required) or an ordered finite set
of product checks. Absence of this policy does not mean tests may be skipped.
Each product check identifies a runtime-installed zero-argument capability;
neither the AI reply nor a public command chooses executable, argv or target.
SBT product checks must use the shared serial wrapper and its proper execution
authority; this batch does not install that SBT bridge or prove product SBT.

Workflow first checks the selected root, branch, HEAD, tracking tip, MERGE_HEAD,
ordinary staged-only status and actual index tree. These are commit/validation
preconditions, not unchanged-content or management-integrity certificates.
Structural validation uses managed Git cached-diff checks against both selected
parents, rejecting newly introduced findings common to both comparisons.
Complete bounded stdout/stderr and actual process termination establish results;
an AI result or prebuilt digest receipt cannot do so.

A native conditional transition saves each check intent before invocation and
each actual result afterward. Retained recorded checks are not rerun. A retained
intent lacking a recorded result is indeterminate and returns nonterminal WAIT,
without automatic retry or invented success. Interruption after recorded checks
can continue with only the remaining checks. Failed validation remains
nonterminal, with actual results retained, and does not permit review/commit/push.
Only all successful selected checks permit the native `MergeValidated` append.
External commands and storage are not one atomic transaction.

Executable scenarios extend the actual public Start/advance SQLite/real-Git
host: successful clean and text-conflict pending merges; failed configured check;
interruptions before invocation, after invocation before recording, and after a
recorded result; physical host reopen, no replay, selection/precondition refusal.
An explicit fixture no-product-test policy establishes structural validation
only. A runtime-fixed Git fixture check establishes managed check execution,
not a product test or production SBT installation. Separate-JVM Workflow-store
reopen, normal installation, paired independent semantic review, repair/Human
answers, final commit/push/convergence and all three practical routes remain open.
Original nine, complete history and repair 3/3 are unchanged.
