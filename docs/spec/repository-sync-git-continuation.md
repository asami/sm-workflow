# RepositorySync Git execution and continuation

Adopted: 2026-10-26. Normative target contract; implementation and installed
adoption are tracked in the [correction plan](../phase/repository-sync-git-correction.md).

This supersedes the portions of the managed-fetch, native-convergence and normal
typed-Git specifications that require operation-by-operation executable setup,
exact equality with a pre-fetch advertised tip, or abandoning the existing
Session solely because an effect result was interrupted. Historical tests retain
their original scope; this document does not relabel them as current acceptance.

## RS-GIT-1 — Producer and consumer responsibilities

CNCF owns typed Git intent, command construction, executable selection (explicit
override or ordinary PATH Git), normal authentication, parsing and managed process
lifecycle. sm-workflow must not require per-operation program registrations,
build a shell fallback, or duplicate these producer guarantees. Existing target,
operation and authorization boundaries remain applicable.

sm-workflow owns synchronization intent, fetched revision selection, integration,
selected validation/review, effect-result retention and continuation. Skills
perform issued semantic work and collect the actual result; they add no private
retry limit, approval gate or speculative Git command.

## RS-GIT-2 — Fetch establishes the integration input

Use the actual successfully fetched tracking tip and its actual relation to the
local repository as the input to integration. An earlier advertised tip may be
retained as an observation, but equality with it is not a success requirement.
A remote update between advertisement and fetch is ordinary repository movement,
not by itself an uncertain failure or a new authorization request. A pre-fetch
`ls-remote` is not mandatory merely to duplicate the fetch result.

Retain the fetched revision used by subsequent decisions. If later remote movement
affects push or integration, evaluate the actual relationship through normal
non-force synchronization. Do not silently substitute another repository, remote
or branch, claim convergence with a newer unseen state, or discard local changes.
A genuine conflict follows the existing semantic/Human route. No force push is
introduced by this correction.

## RS-GIT-3 — Reconcile completed effects before doing more work

Use the existing invocation, retained process result, workflow record and native
Git facts. After interruption, first collect the original execution's actual
result. While it remains live or termination is unknown, do not start a competing
execution. Do not add periodic AI polling to establish liveness.

When termination is established, reconcile according to the operation:

| Operation | Continuation |
| --- | --- |
| Commit | Reuse its actual terminal receipt or establish that the intended commit exists with the intended tree/parents and repository relationship. Record the recovered result and continue without another commit. An unrelated HEAD is not proof. |
| Push | Reuse terminal success, or observe the selected destination to establish that the intended commit was incorporated, including a later descendant where appropriate. Continue with the actual current relationship; exact tip equality is not the only evidence of incorporation. |
| Fetch | Consume an attributable completed fetch and its actual tracking result. If a new observation is needed after termination, another fetch of the same authorized target may proceed under the existing authorization. Do not report the old unknown attempt as proven success. |
| Confirmed not started / known failure | Retain the result and select the applicable ordinary continuation. No new Human approval solely for a missing receipt or changed executor; no unbounded retry loop. |
| Insufficient or contradictory evidence | Keep uncertainty explicit and return the concrete diagnostic or required semantic decision. Never infer no effect from a parser/transport error. |

Git and workflow persistence are separate effects, not one transaction. A failed
record write does not undo a successful Git command. Record recovery as a new
attributable observation linked to the original intent/result, preserving the
original failure or uncertainty. Repeated continuation consumes completed stages
without repeating mutations. Recovery success is distinct from fixing its cause;
reuse existing Journal/Evidence/Finding mechanisms without additional AI calls.

## RS-GIT-4 — Preserve the current run and authorization

Keep existing Session, accepted results, history, repair counts and validation
evidence. Adopt the correction through the owning public continuation/adoption
route, including any necessary persisted contract revision handling. Do not
re-Start, rewrite runtime storage, fake Human answers, repeat accepted Submit or
reset counters. A delivery that cannot yet adopt the active Session reports that
gap explicitly rather than asking for an unrelated new Session.

Reconciliation is mechanical recovery, not a new semantic repair cycle. Preserve
existing counts; do not reset or refund them. Existing authorization covers the
same target, purpose and operation. A real scope change or unresolved user choice
uses the formal runtime Human route. Skills must not independently override such
a request; the runtime must supersede obsolete requests through its public path.

## RS-GIT-5 — Proportionate observation and acceptance

Observe facts needed for the next decision or a real recovery boundary. Reuse
unchanged facts already guaranteed by typed construction and producer results;
do not repeat an exhaustive repository scan per minor step or create hashes,
leases, application locks or a second retry ledger. Separate observations are
not an atomic repository snapshot. Preserve native Git locks and necessary
storage semantics.

Acceptance covers normal synchronization, remote movement during fetch, and
interruptions after commit/push/fetch success but before result persistence.
Repeated continuation must not duplicate successful mutations, lose the active
result, or demand routine new approval. Persistent failure returns a real result
without a busy loop. Existing unaffected validation is reused; new behavior is
verified with controlled local repositories and then the installed public route.
