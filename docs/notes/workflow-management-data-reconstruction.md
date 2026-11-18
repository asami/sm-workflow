# Reconstruct execution management data from current source

Date: 2026-11-18 (Asia/Tokyo)
Status: User-directed normal runtime contract; implementation and verification are established separately by actual results.

## Purpose and rationale

The Human reaffirmed on 2026-11-18 that this is normal sm-workflow behavior,
not an incident-specific restoration exception. Existing execution owners must
reconstruct derived management data when the current source and retained facts
make continuation possible, then use the ordinary remaining execution stages.
Recording this requirement does not assert that every case is implemented or
verified, and does not authorize caller-side private-state editing.

When continuation cannot proceed, sm-workflow should reconstruct the execution
management data from the current source and retained business facts, then resume
through its ordinary execution path from a confirmed successful stage. The user
has stated this approach before and reports that it has not been followed
successfully. This note makes the intended approach explicit for subsequent
implementation and review.

Failure patterns are numerous. Adding a precise recovery branch for each pattern
creates continuing development and verification work. Putting those branches in
shared code does not make the approach general. The intended simplification is
to make execution management data reconstructible and use a small set of normal
restart stages, rather than reproduce every intermediate state exactly.

## Preserve facts; reconstruct derived execution data

Reconstruction keeps the same Session, objective, accepted requirements, owned
scope, acceptance conditions, history, counters and limits. It retains actual
semantic results, failures and confirmed execution outcomes. It does not rewrite
an accepted answer, delete a failed attempt, fabricate successful evidence or
replace the Session with a new one.

The current source is the basis for the work to execute. Reconstructible data
includes the next execution stage, candidate selection and required validation
targets. Historical source references and results remain historical facts; an old
execution base does not automatically become an equality gate against current
source. A later main commit or unrelated document addition alone does not make
the original purpose invalid.

Reconstruction returns to a logical execution stage. It does not roll back source
files or Git history. It does not reset repair counters, replenish a budget or
add a repair frame. Subsequent actual work follows the existing counting rules.

## Normal continuation and restart

Use the following decision sequence within the existing execution owner:

1. Read the current source, retained business input and actual execution results.
2. Identify a confirmed successful stage from which the required remaining work
   can run, and reconstruct its derived execution management data.
3. If reconstruction succeeds without contradiction, use the ordinary execution
   path for the remaining stages.
4. If the required data cannot be reconstructed, return an error explaining what
   is missing. If reconstruction exposes a contradiction, return an error
   explaining the conflicting facts.

For example, if implementation remains in the current source but continuation
after validation has failed, validate the current source again when necessary
and proceed through normal review and closure. Repeating validation can be
simpler than building elaborate proof that an old result is reusable. Reuse
applicable confirmed results when straightforward; neither mandatory reruns nor
perfect evidence reuse is the objective.

Confirmed commits and other completed external effects are consumed as actual
results. Restarting an execution stage does not authorize blindly repeating
commit, push or publication. A genuinely live invocation is collected through
its existing execution route. If the available facts cannot establish how to
continue, report that inability rather than infer either success or nonexecution.

## Error and waiting semantics

The user's explicit boundary is: inability to reconstruct is an error;
contradiction during reconstruction is an error. Do not silently alter retained
facts to make reconstruction succeed, or add an incident-specific reconciliation
branch for each contradiction.

Wait describes an actual ongoing operation or an explicitly required external
response. An unusable intermediate management state must not remain in Wait
indefinitely merely because it cannot be resumed exactly. It takes the normal
reconstruction route or returns an explanatory error.

## Keep the mechanism small

Assess necessary input, target, scope and execution permissions at their actual
consuming boundaries. Reconstruction errors are not a reason to introduce old
copy equality, unchanged-source proofs, management hashes, verification markers,
comparison ledgers or repeated validation of already established facts. In
particular, do not define every difference from the old management representation
as a contradiction: legitimate current-source changes are why reconstruction is
needed.

Review the implementation against these questions:

- Does a previously unusable intermediate state reach normal execution or a
  concrete error, rather than an unproductive Wait?
- Does the mechanism work through shared restart stages without another branch
  keyed to a particular incident, Session, project or historical base?
- Are Session, purpose, acceptance conditions, history, counters and limits
  preserved while derived execution data is rebuilt?
- Are completed effects respected without source rollback or invented outcomes?

These questions express the design direction. This note does not require a new
verifier, recovery service, exhaustive failure matrix or broad acceptance-test
expansion. Use proportionate behavioral evidence for the implementation actually
changed.

## Related guidance

- [Single-user Git-managed workbench control](../../ai/directive/core/workbench-control.md)
  supplies the shared minimal-checking and typed-control model.
- [Goal Phase current inventory continuation](../spec/goal-phase-current-inventory-continuation.md)
  is an affected existing contract; this note claims no completion of its repair.
