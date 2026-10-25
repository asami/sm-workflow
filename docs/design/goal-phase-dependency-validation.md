# GoalPhase dependency validation design

## Planned authorization correction — 2026-10-25

Implement [PA-01..07](../spec/goal-phase-dependency-scope.md#purpose-based-authorization--adopted-2026-10-25)
in existing dependency scope, Human decision, continuation, candidate and validation
owners. Separate retained authorization from planned/actual file inventory; add
ordinary typed scope-update admission before escalating to Human. The AI supplies
files and purpose rationale, not an approval-required verdict. Preserve historical
codecs/receipts and append public adoption/current inventory to existing history.
The source architecture below describes the file-bound baseline, not completion
of this correction. Do not introduce a parallel workflow or consent store.


One optional selection in GoalPhaseWorkResult names a host binding and the exact
saved dependency approvals. GoalPhaseNativeWorkContinuation reconstructs approval
context from actual completed Human work, including multiple same-Step decisions.
The native owner admits selection before the existing semantic completion. It
retains a captured Admission, not a later lookup of mutable host defaults.

GoalPhaseDependencyValidation owns finite business values, strict codecs, exact
scope union and result projections. GoalPhaseNativeDependencyValidation owns typed
Git capture/freshness and logical execution selection. It uses the current UoW;
there is no separate resolver, process executor, Session or dependency workflow.

The primary candidate payload retains optional dependency capture. Dependency
staging shares the existing candidate intent/result owner. The existing validation
Store owns the combined ordered check chain; dependency checks carry an explicit
subject and retain their target/operation. The controller still receives its
original primary check identities and a truthful aggregate outcome. Public views
separate primary receipts from dependency proof. Native recovery targets include
the selected dependency subject when applicable.

Review context carries the same capture and exact selected results; completion
and closure verify the actual dependency state. Evidence and issue attribution
use the dependency repository for dependency checks. Existing Plan/controller
identity, original base, accepted prefix, generations and counters are preserved.

GoalPhaseNativeWorkContinuation distinguishes current review acceptance from
historical repair rationale with its existing `_is_review` predicate. Both
`_issue_assigned_c` and `ClosingProgram.selectC` check inherited dependency
captures only for ReviewStep/ReviewPhase. RepairStep keeps its original review
context and prior reports without comparing an admitted correction to the old
capture's staged tree or diff. Its completion retains ordinary primary ownership
and immutable dependency admission. The next native candidate owner performs
the existing permitted staging and fresh observation; the validation owner checks
that new capture before and after each selected check. Review acceptance and
Step/Phase closure continue to reject dependency drift. No staging workaround,
replacement capture or alternate execution owner is introduced.

Optional fields are absent on old wires. New records validate the complete
extension; omission cannot erase a captured requirement. Generated public Scala
belongs to the CML generator and must not be hand edited. Source specifications
cannot establish generated/installed ABI compatibility or live Session acceptance.

## Handoff boundaries

Dependency staging uses the candidate Store's existing native intent/result
transitions. Completed capture is reconciled against those immutable stage pairs
and observation records; an already retained intent is never dispatched again.
Candidate recovery now carries the exact transitive dependency Admission and
contiguous dependency facts described below. Unknown effects remain retained;
the existing explicit recovery directions control any new observation.

The existing public dependencyValidation field carries references. This prefix
extension changes only package-private Admission arity, with a genuine optional
internal default; public generated constructor arities stay unchanged. Generated
code still belongs to its generator. Source edits and authored fixtures alone
establish neither successful compilation nor compatibility of an installed CAR.

## Candidate recovery provenance (CC51-S1 additional scope)

The candidate's optional `dependencyOrigin` names the original stage source, actual
observation source and actual observation-result record revision. It references
native receipts; it does not rewrite a capture's `sourceRevision`. Legacy normal
captures without this field retain their original same-source interpretation.
When referenced across a direction, legacy completed receipts remain byte-for-byte
unchanged and the new candidate explicitly records their actual origin.

The existing candidate recovery target includes a strictly contiguous
`dependencyPrefix` of exact native receipt identities/revisions/payloads. It can end
before staging, after a recorded stage, at an UNKNOWN stage intent, after all stages,
at an UNKNOWN observation intent or at a completed observation result. Only fully
recorded staging can authorize a new observation. Other prefixes offer existing
same-Step `ReviseWork` or `Defer`. The pure direction path issues no Git effects.

A new observation uses the existing candidate Store with a distinct source namespace
and wrapper observation intent. Completed stage effects are referenced, not rerun.
Its dependency observation has a separate intent/result pair; later primary failure
retains that completed pair. Observation continuation binds both actual repository
snapshots, the immutable original target, all prior prefixes, diff budget and fresh
independent Human answer. Each effect owner rechecks actual freshness and its
existing grants. The native walker retains approvals, bases, counters, failed and
unknown receipts and uses the existing controller generation event for revision.

Source compatibility remains an ordinary caller migration: generated CML values get
no hand-authored managed-code defaults. Generic public-result adapters preserve
`dependencyValidation` through `publicSelection`; intentional legacy fixtures use
`None`. Source specifications require owning native qualification and independent
review before any installed or Phase acceptance claim.

## Single-dependency accepted-prefix lineage

The optional package-private `Admission.acceptedPrefix` selects a versioned
incremental baseline while retaining the immutable original dependency commit base.
`GoalPhaseDependencyValidation` owns its closed v1 business codec and pure capture
interpretation. Public reference-only Selection and host Binding remain unchanged;
the caller cannot supply the enriched Admission through semantic completion.

`GoalPhaseNativeWorkContinuation` extends its existing Cursor and Prepared values
with an ephemeral DependencyChain. Only its native accepted-closure branch can
construct entries, after ordinary reconstruction has proved actual candidate
provenance, selected successful validation, independent review acceptance and
observed Step closure. Each entry retains the exact original capture and actual
closure revision. There is no new persistent registry, certificate store, resolver
or dependency execution controller. Every public operation reconstructs the same
contiguous native history again. A decoded prefix alone cannot construct a chain.

At initial current-Step semantic admission, the native owner compares the unchanged
same-Step approval union and selected binding, resolves the immediate accepted
predecessor and appends only its exact anchor to the newly saved Admission. Same-Step
repair and retained completion reconstruction require equality with that Admission.
Old first-Step binding, approvals, capture, native history and check/review payloads
retain their original bytes and meaning. Host defaults can select a new current
binding before adoption; they cannot replace an already adopted same-Step binding.

The chain resolver rejects Component/requester, repository/workspace, root/branch,
original-base, Session/Plan and primary-base crossings. It also requires the exact
immediate anchor and accumulated actual changes, so a missing or skipped predecessor
cannot stand in for a native accepted closure. The 64-Step and 128-path bounds
remain finite. Histories of unrelated dependencies retain their existing live
checks; this extension adds no generalized multi-repository workflow.

`GoalPhaseNativeDependencyValidation` checks the cumulative original-base inventory
against inherited actual paths plus current admitted paths. Its existing candidate
owner stages only current paths with new current intent/result pairs. Typed
PendingPaths/PendingDiff use the retained predecessor tree object as their argument;
the CNCF Git adapter already supports that typed object and disables rename folding,
so both deletion and addition endpoints remain subject to current ownership.
Capture.candidate stays the full live HEAD/index snapshot; changedPaths/diff become
the complete predecessor-to-current delta only when the explicit v1 prefix exists.
Legacy captures retain original-commit-to-index semantics. Before/after observations
bind each capture to a stable observed repository/index without claiming filesystem
atomicity or introducing a global lock or path hash freeze.

The candidate Store passes the reconstructed chain through normal and recovered
captures. Recovery snapshot shape checks accept cumulative pending state and retain
the current inventory as new edit scope. They are pure structural checks, never
lineage authorization. The native snapshot/effect owner must resolve the anchor
again; completed stages and all unknown prior observations remain attributable in
their original namespaces and CandidateOrigin fields.

The validation Store uses the same whole-chain validator in its before/after check
preconditions even when the current primary-only capture contains no dependency.
Actual process receipts are persisted before a failing postcondition is returned.
Review issue/completion, Step closure callback and Phase review/closing use this
same validator. It proves every accepted ancestor and every admitted incremental
edge, then compares live state to the latest qualified capture per dependency.
Only a valid successor changes which full tree must be live. A stale historical
tip without a successor still refuses. Independent review sees unchanged ancestors
and the current incremental payload, including overlap needing fresh integration
assessment; previous PASS never validates later bytes.

Adoption uses existing public Submit/Advance and the actual retained current
request. The owner separately authorizes its host binding, positional stage
mapping, check identities/ordinals and capabilities, then delivers and qualifies
the new CAR/runtime through the ordinary route. No manual Session edit, restarted
Session, new public Operation, generated DTO arity change, base replacement,
counter refund or dependency publication is part of this source extension.
New runtime reads old records without rewriting them. New extended records require
this supporting runtime; older closed decoders are expected to reject them.
