# SplitPhase scope projection after semantic completion

The next original PHASE-2 runtime requirement is mechanical application of an
admitted scope result to the complete retained candidate frontier. This record
captures the current source boundary and a proposed implementation contract.
It is not implemented, independently reviewed, or admitted as a repair or test
request. The current three technical findings remain unresolved.

The essential constraint is that Workflow must consume an actual retained scope
completion, preserve every competing candidate, and absorb the mechanical work
without another AI invocation. Neither a semantic acknowledgement nor a decoded
planning snapshot authorizes application or establishes Terminal.

## Confirmed source boundary

`SplitPhaseManagedScopeSemantic.Port.advanceForCallC` already obtains a privately
constructed native Source, the exact issued Plan, and actual retained completion.
Its unsuspended-completion branch still returns serviceUnavailable for the
whole-frontier projection or genuine Human handler. `_observe_completion_c`
checks the admitted result, closing type, adjacent completed revision and full
native history prefix. This is the relevant connection point; a client must not
choose a processing stage or supply a new planning snapshot.

`SplitPhaseScopeSemantic.admitC` permits Enriched results whose ranges are exact
ordered nonempty subsets of the requested producing/receiving ranges. It does
not require that each range has one owner in every enumerated candidate. Thus
Enriched is semantic admission, not a proof that every candidate can now be
represented. DecisionNeeded contains no partial scopes and requires a genuine
human channel rather than deterministic invention of a choice.

`SplitPhaseExplicitCandidateProjection.projectOneC` currently derives transfer
ranges from the complete original template children. It reports SemanticRequired
when either range spans multiple candidate owners, retains repository/numbering
Decisions, and produces Built documents only after shape/boundary validation.
It has no entry that consumes an admitted scope result. Merely rerunning this
unchanged processor would use the original broad ranges and report the same
unknown work, wasting AI cost rather than applying the known result.

`SplitPhasePlanningCheckpointPreparation.Record` is a first-checkpoint contract.
Origin has only Mechanical and PartitionCompletion. `inspectC` ties the original
context owner's instance revision to its predecessor and checks the corresponding
one-append/two-append committed revision. It cannot represent a later scope
issue, completion and projection without changing that meaning.

`SplitPhasePlanningCheckpointPublication.prepareC` creates the context index,
mapping and Built document rows; its context index key is fixed by the original
instance and context. `SplitPhasePlanningCheckpointDataStorePort` reads that
index as the immutable first mapping. Reusing its Create set for the next stage
would collide with existing rows; replacing the mapping would destroy the
original source used by native scope recovery. Neither route is admitted.

The current CNCF `WorkflowManagedMechanicalTransition` already accepts a typed
payload and a Create-only local UnitOfWork program, commits through the owning
Subsystem's verified multi-guard space, and returns success only with Verified
retained data. Its `loadRetainedC` observes a receipt plus current native instance.
This supports a separate application stage without a new database pool or a
direct SQLite implementation. Source inspection is not runtime proof of that
new application composition; new executable specs remain necessary.

## Proposed ownership and record contract

Advance remains the only public routing entry for this continuation. The client
keeps returning the issued scope result through its assigned completion operation.
No caller-selected route, host allocation, policy, command or application flag
is added. Any new externally required action must have its CML request/result/
operation contract before exposure; a handwritten endpoint is not a substitute.

The proposed later record is separate from the first planning checkpoint. Its
typed data must retain these relationships:

| Relationship | Required retained data |
| --- | --- |
| Original planning source | Exact instance/context and first checkpoint reference, complete original frontier and original Start lineage. |
| Semantic source | Exact scope continuation, host-assigned result reference, admitted typed result/evidence and native completed revision. |
| Mechanical source | Exact projection reference, before/after native revisions and owning advance operation, with a canonical mechanical receipt. |
| Effective frontier | Ordered complete candidate keys, unchanged cuts/durations/allocations, preserved original documents/Decisions and explicit effective transfer scopes. |
| Continuation | Actual next requirement or planning data, never a fabricated WAIT, human answer, document write or Terminal. |

These names describe a proposed application contract, not a newly installed
codec/schema or new authority source. Select explicit references/revisions from
Workflow-owned purposes. Do not derive management identities from hashes, modify
the first context index, or use a decoded record as an executable capability.

The owning managed layer must construct a private completed-scope source only
after checking the original authenticated participant, independently read
Start/context/effective facts/Built rows, exact native issue and retained result,
and the complete committed history prefix. A raw result or semantic Admitted
value is insufficient by itself. The mechanical kernel consumes that selected
source; it does not retrieve authority from JSON.

Retained reconstruction must preserve the original enumeration and allocation,
not enumerate cuts or allocate identities again. If an executable computation
input must be reconstructed, its constructor must require that independently
admitted native source and exact retained frontier. Do not add a general raw
snapshot-to-planner adapter. Mechanical and partition-AI origins remain distinct,
including the effective known estimates/edges produced by partition completion.

## Proposed deterministic application

Workflow applies each known scope to every relevant original transfer, retaining
the full frontier in its existing order. It does not choose an already Built
candidate merely because another candidate has a remaining representability
problem. Cuts, duration estimates, source closure, dependencies and authority
are not semantic result fields and cannot be replaced by the scope reply.

For each affected candidate, compute producing/receiving owners from its original
item ownership. One shared owner makes the original transfer local work, not
permission to delete its action or evidence obligation. Two distinct single
owners produce an inter-child transfer retaining kind/input/action/output,
invalidation reason and authority; rebuild incoming/dependency metadata through
the existing boundary rules. Existing Built document bodies and host identities
remain original; effective local-range annotations must remain separately
explicit so the semantic scope is not lost while retaining original documents.

A range spanning multiple owners remains a representability need even after
Enriched. Proposed handling is a deterministic Human requirement carrying that
candidate, exact admitted ranges and owner mapping. It must not repeat an
identical AI task for an already known scope or silently eliminate the candidate.
This proposed requirement needs an admitted typed Decision/handler before public
execution; no current implementation or Human acceptance is claimed. Existing
repository/numbering Decisions remain alongside it.

Only when every candidate is mechanically representable may the existing
candidate-admission and objective processors continue. Duration, cost isolation,
overhead and authority-aware tie-break retain their declared ordering and data
requirements. Missing host records are host needs, unresolved reasoning is its
own semantic need, and competing authority remains a genuine Human need.
Selected planning documents still require authorized application before Terminal.

## Proposed managed publication and recovery

The new stage must use the actual owning advance call and the initialized
Subsystem/DataStoreSpace provider. Publish its new projection record and newly
constructed candidate documents together with one native mechanical append in
the existing managed UnitOfWork. Preserve previously published Built documents;
do not attempt duplicate Creates or silently change existing document bodies.
Verify exact committed record/documents through canonical readback before routing.

Repeated advance first observes the retained projection and original scope
completion. It cannot reissue scope work, select a new allocation policy, append
again or treat missing/divergent committed readback as a fresh path. Before-commit
failure rolls back; uncertain commit/readback returns uncertainty without retry
or success. Event durability remains EventStore policy, not an application-side
persist-all instruction. No pool, application claim/lock or new retry is added.

Mechanical-origin revision sequence is first checkpoint2, scope issue3,
completion4, then proposed projection5 for a revision1 original instance.
Partition-completion-origin is first checkpoint3, scope issue4, completion5,
then proposed projection6. These are derived example sequences, not executed
new-stage results or a relaxation of exact current revision validation.

## Required executable evidence

The future implementation and generated/public consumers must be verified together:

1. Actual public advance after scope completion applies the known ranges without
   another semantic invocation, preserves the whole frontier, and exposes only
   the real next requirement. Both original mechanical and partition-AI roots
   must be covered, including a mixed Built/scope/repository/numbering frontier.
2. Local and cross-child transfers preserve every original action/closure/
   authority obligation. A valid but still multi-owner Enriched result remains
   an explicit requirement, not an arbitrary winner or identical AI reissue.
3. Retained replay and physical reopen observe the same projection/source,
   without allocation/enum policy reads, duplicate append or new issued work.
4. Crossed checkpoint/result/actor, same-reference fact/document drift, missing
   native completion/prefix or inconsistent later record reject without effects.
5. Pre-commit failure rolls back the new stage; committed readback uncertainty
   returns no success/retry. The original first mapping and existing Built rows
   remain intact in both cases.
6. New examples have semantic Given/When/Then and owned work output under target
   with safe cleanup or explicit retention. Use fresh admitted runner requests
   and a fresh independent read-only reviewer; old865 is not new-stage validation.

## Admission and unchanged accounting

This is source-grounded prerequisite design for original unfinished delivery,
not a post-review source edit or an exception to repair accounting. No CML,
Scala, tests, build, upstream repository, skill or configuration was changed;
no SBT, test request, child, repair slot or counter was created. The actual
implementation stays subject3 with123/123 focused tests and independent FINDINGS.
Its three blockers require the already requested actual human repair direction;
scheduler continuation does not supply it. Do not silently skip their closure
while declaring this next stage or the Phase accepted.

Original9/R5/A7, all history, limit3/all3 consumed closed cycles and full consumed
command prefix531 through865 remain unchanged. All original runtime/default,
command/transport/skill/cost, validation/review/documentation/release duties remain
open. Goal stays ACTIVE, Phase in_progress. The generic managed primitive is
available in source; no CNCF change is justified merely by this proposed stage.
