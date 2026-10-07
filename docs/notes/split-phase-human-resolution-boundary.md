# SplitPhase Human resolution consumption

Status: revised exploratory design after independent FINDINGS; not runtime acceptance.
Observed: 2026-10-05 Asia/Tokyo.

Independent design report: `human-resolution-boundary-design-review-1-result.json`
in the PHASE-2 control directory. Its raw FINDINGS remain unchanged. The
consumer contract is promoted separately to `docs/spec/split-phase-human-resolution.md`;
it is a working specification, not implemented or executable acceptance.

## Original delivery obligation

The full PHASE-2 Goal requires `ProvideResolution` to resume Workflow-owned
validation and planning from an independently recorded Human answer. The
reference is neither an operation selector nor permission to expand authority,
apply documents, change completed history, or silently discard a candidate.
The successful Defer/Cancel implementation and validation920 do not implement
this branch. Original9/R5/A7/null/limit3/closedconsumed3 remain unchanged.

## Current source facts

`SplitPhaseManagedHumanCompletion.observeAnswerForAdvanceC` constructs an
observation only after loading the actual owning published scope checkpoint,
the genuine issued Decision, canonical completed answer and complete native
Answered history. `SplitPhaseHumanTerminal.advanceC` currently refuses
`ProvideResolution` after that observation. No reference-to-resolution-data
contract or handler exists. A passive codec cannot replace this observation.

`SplitPhaseScopeProjectionSnapshot.Record` retains the complete first planning
root and effective selection. For a PartitionCompletion root, those effective
facts already include the earlier admitted partition semantic result. Comparing
new data only with the initial pre-enrichment context would lose known facts.

The published Human frontier has two forms:

- `HumanDirectionRequired`: the scope participant returned DecisionNeeded and
  no new scope annotations. The first frontier still contains every original
  Built, Decision and unresolved semantic candidate.
- `Applied`: every candidate and its effective transfer scopes are retained;
  each result is Built, Decision or KnownUnrepresentable. The latter means
  scopes are known, but one or both ends have multiple owners for that cut.

The existing planning kernel advances only an ALL-Built projection. It retains
the enumeration and uses deterministic admission, duration ranking and objective
selection. Supplying `None` for a blocked candidate is not a resolution:
`SplitPhaseExplicitCandidateAdmissibility` treats missing non-short-child proof
as EvidenceRequired, not as a justified exclusion.

## Established constraints on the resolution boundary

1. Public advance owns dispatch. Skills return the issued semantic result;
   they do not choose entry assessment, validation, projection or apply.
2. Load the explicitly referenced data through the owning Component's canonical
   DataStoreSpace and declared purpose/collection, using the actual ActionCall.
   No filesystem input, private SQLite/provider, second pool, newest-reference
   lookup, synthetic consent or decoder-created native authority is admitted.
3. Admit resolution meaning at one natural boundary receiving the existing
   canonical Human observation and the independently selected data. Subsequent
   pure processors consume its constructed value, without duplicating checks,
   content hashes, copy seals or competing-writer guards.
4. Retain source, original authority, completed history, ordered unfinished
   work, closure criteria/prerequisites, known durations, known partition edges,
   calibration/policy and the complete candidate frontier. Never select a
   cheaper frontier by dropping a held candidate or resetting the enumeration.
5. A bare Human answer reference cannot rewrite scope meaning or turn an
   unresolved structural problem into a claimed Built result. A deliberate
   complete Human interpretation may supersede prior AI scope annotations at
   a distinct admission boundary bounded by the original request ranges. Keep
   that prior publication and judgment as history; prior AI meaning is not
   immutable source authority. Changes to
   repository placement, occupied identities or closed validation policy need
   their actual authorized source data; a resolution assertion is insufficient.
6. Derived child identities, document references, ownership chains, candidate
   admission and ranking remain Workflow work. Resolution data supplies meaning,
   not document text, gate flags, preselected winners or processor names.
7. A rejected or incomplete resolution has no native append, continuation
   publication, document change, command, allocator replay or automatic retry.
   It is not Defer/Cancel, successful application, or another identical AI task.
8. On successful consumption, publish the resolved planning successor and its
   retained result through the existing owning managed publication boundary.
   Repeated advance and physical reopen observe that successor; they do not
   reload mutable input as a reason to repeat the semantic application.
   Actual required atomic facts/effects decide publication needs, not a blanket
   assumption that every successor needs a new producer API or side record.

## Questions to settle before selecting the data contract

These are design questions, not newly imposed requirements or upstream blockers.

- For DecisionNeeded with unknown scope meaning, can a typed complete scope
  resolution reuse the existing semantic scope validator while constructing a
  distinct Human-resolution value (never a fabricated AI completion)? Which
  exact retained request supplies permissible item ranges and evidence?
- For Applied/KnownUnrepresentable, explicit complete Human supersession of a
  prior participant's scope interpretation is the selected minimum. Original
  request bounds and source handoff bodies remain protected; old interpretation
  remains historical input. Candidate-specific scopes are not permitted. This
  requires a distinct Human value, not a forged AI result. Factorization is a
  separate undecided model change; do not infer Cartesian transfer semantics.
- Can repository/numbering Decisions be resolved within the frozen source
  authority through independently read changed host facts? Distinguish a real
  authorized correction from a client assertion that a collision disappeared.
  If original facts make a requested result impossible, expose that fact rather
  than relaxing numbering, pretending the candidate is Built, or silently
  removing it. Under unchanged facts, the selected minimum retains every such
  Decision and refuses continuation with complete held-candidate diagnostics.
  Versioned host-fact supersession remains undefined, not a missing producer API.
- If deterministic infeasibility is an allowed candidate outcome, what complete
  retained justification and ranking contract is needed? Existing `None`
  evidence is not such an outcome. Do not add exclusion merely to bypass
  ALL-Built or narrow full-frontier acceptance.
- What is the smallest coherent Component-local resolution ADT and single
  constructed admission boundary covering the supported Human semantics? Which
  existing native/state/storage types can carry it, and which genuinely missing
  producer capability, if any, is required by selected atomic facts/effects?

## Source-supported implementation path selected after review

Use a passive Human scope-resolution record with its exact data reference,
original context/first/published-checkpoint references, issued Decision identity,
complete global scopes, rationale and evidence. The canonical Human answer
reference is retained separately from the referenced resolution data. One
admission boundary consumes the actual native-bound Human observation and the
independently loaded record. Share only pure range/coverage validation with the
scope participant; do not fabricate its result reference or completion.

Apply interpretation to the current complete published frontier, not to a fresh
first-root replay. Existing Built documents stay published even when their
first-root origin was Semantic. Only formerly unresolved candidates create new
documents. Every original candidate, allocation and effective partition fact
remains. Require ALL-Built before existing admission/objective continuation;
remaining structural Decisions or unrepresentable scopes refuse before effects.

Retain the resolved calculation, prior publication and canonical Human answer
with one Active unsuspended mechanical successor and only genuinely new Creates.
The generic owning `WorkflowManagedMechanicalTransition` supports this atomic
shape; scope-specific capture/codec cannot be reused unchanged. Retained receipt
routing must precede fresh input loading. The minimum can preserve HumanAnswered
progression; it must not claim a generated HumanResolved or re-question edge.
An invalid resolution cannot silently become a different answer to the same
Decision. This implementation path needs new consumer code and executable proof,
not a new upstream producer requirement.

## Required executable behavior

The eventual executable spec must exercise both Mechanical and
PartitionCompletion original roots. Required behaviors include complete valid
resolution and deterministic downstream continuation; unknown-vs-known semantic
data separation; preservation of existing Built/Decision candidates and their
allocations; known estimate/edge/authority/closure protection; full coverage and
ordering; invalid/foreign/missing reference refusal before effects; actual
current-call policy and store ownership; one committed native successor;
retained same-result Service/CLI observation; interruption/readback and reopen
without semantic replay; and refusal of unsupported collision/repository or
transfer transformations. A partial scope-only implementation does not close
the Human resolution branch or the full Phase.

No runtime source, CML, persisted schema or existing spec is changed by this
note. New tests must use a fresh registered runner and shared serialized SBT;
independent runtime QA follows actual success. Design review supplies design
evidence only. Default production ingress, all later handlers, transports,
storage/recovery, managed commands, generic distributed sm-* skills, measured
cost/round trips, full specs/help/docs and release conditions remain required.
