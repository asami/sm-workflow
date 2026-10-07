# SplitPhase passive planning snapshot contract

Date: 2026-10-02
Scope: original sm-workflow Phase 2; admitted CNCF Phase 88 unchanged
State: parent-only limited-recovery design; not ordinary accepted PLAN

## Continuity and source findings

The previous turn made progress: test configuration now invokes official runtime
preparation through the Test/compile dependency. Actual full-test 804 passed
924/924 examples in 123 suites, exits 0/0, lock=released. Its complete summary
was read, not inferred from a settings file. The original active Goal observation
still has createdAt 1790766223 and the same Phase-completion objective. There is
no replacement Goal, Phase, base, accepted epoch or review ledger.

Retain the user's parent-only limited recovery and no-agents instruction, all
ordinary authority/base/Goal-binding/PLAN-owner evidence gaps, and original
Step/review/release obligations. The current parent profile is not directly
observable. This turn reads sources and records a design; it changes no
production/spec source, starts no SBT/agent and performs no Git mutation.

This is the next prerequisite of the mechanical checkpoint consumer design,
not a substitute end state. Newly read source establishes:

- `W.Trace` retains E/Q/A/O results, while their constructed Candidate, Built,
  Assessment, ShortReview and Objective outcomes cannot be decoded as executable
  objects. They need passive value projections, not reflective constructors.
- `Q.Requirements` contains the entire mixed frontier. The old enrichment
  publication returns zero candidate documents for that outcome. Built members
  must survive alongside pending members, and their local semantic handoffs are
  distinct from the generated cross-child handoff document.
- `A.Assessment.shortReview` contains all attempted rebalances/merges and its
  rejection reason. Keeping only duration leaders would lose required packing
  evidence and rejected candidates.
- O scores are BigInt. The current executable overflow example expects
  `BigInt(440) * BigInt(Long.MaxValue)`. The snapshot must not narrow to Long,
  floating point or a JavaScript-interpreted numeric literal.
- O's human tie retains positional leader/authority correspondence. A tied
  frontier with different handoff authority is not an automatically chosen plan.
- W currently supplies Some(boundary) for every Q.Projected candidate. Therefore
  its HostEvidenceRequired branch is not reachable through the current planning
  kernel. Represent it as passive data without inventing a constructed W outcome
  or claiming an executable positive route that does not exist.
- Native WorkflowMechanicalTransitionJsonV1 has its own 1 MiB bound and guards
  payload-codec exceptions. A 1 MiB standalone payload is not necessarily a
  1 MiB complete native receipt. Later execution must pre-encode the complete
  receipt/effects before any write; this slice does not establish native capacity.

Aliases in this document: W = SplitPhaseExplicitPlanningAdvance; E = explicit
partition computation; Q = candidate projection; A = candidate admissibility;
O = objective selection; P = proposal precheck; B = boundary validation;
C = explicit partition context; R = new passive planning snapshot.

## Frozen passive model and projection

Create `private[smworkflow] object SplitPhasePlanningSnapshot`. All Record/view
types below are immutable raw data with ordinary case-class constructors. They
are not grants, WorkOrders, Decisions, retained receipts or selected current
state. Only `Projected` has a private owning constructor.

```scala
Record(selection: C.Facts, partition: PartitionView,
       projection: Option[ProjectionView], admission: Option[AdmissionView],
       objective: Option[ObjectiveView])
CandidateKey(cutAfterIndices: Vector[Int])
CandidateView(key: CandidateKey, children: Vector[E.ChildRange])
Projected private (record: Record, disposition: Disposition)
projectC(outcome: W.Outcome): Consequence[Projected]
dispositionC(record: Record): Consequence[Disposition]
```

`projectC` copies the original private outcome's already computed selection and
trace. Selection is the **effective** P/B/E/W data at that computation, including
an allocated planning row or post-semantic-resolution references. It is not
necessarily the original registered context's facts. Original context/Start,
owner and native prefix belong to the later managed wrapper and must be stored
and independently verified there. Do not recover a current C.PreparedPlanning
by pretending that a receipt's old instance is today's canonical instance.

No processor load/select/prepare/resolve/enumerate/project/assess/rank/calculate
call, policy lookup, allocator, callback, command, AI dispatch or write occurs
inside this copier or codec. Copying/comparing complete retained values is
permitted; replaying their computation is not. Do not construct W.Outcome or
private E/Q/A/O values from decoded records.

### Closed view families

Each family below is a sealed passive sum type. Its variants and fields are
closed, with no generic Json bag or opaque caller-supplied stage/processor name.
`key` always identifies the original ordered cut vector, not a regenerated
document ID or a management hash.

| Family/variant | Exact retained fields |
| --- | --- |
| PartitionView.Enumerated | evaluatedCount, rejectedByCeiling, candidates: Vector[CandidateView] |
| PartitionView.SemanticRequired | items: Vector[E.UnresolvedItem], afterItems: Vector[P.WorkItemIdentity] |
| PartitionView.NoAdmissible | reason: E.NoCandidateReason, evaluatedCount, rejectedByCeiling |
| ProjectionView.Projected | candidates: Vector[BuiltView] |
| ProjectionView.Requirements | candidates: Vector[CandidateProjectionView], scopes: Vector[W.ScopeWork], decisionKeys: Vector[CandidateKey] |
| CandidateProjectionView.BuiltView | key, proposal: P.ProposalRecord, ownership: B.Ownership, handoffs: B.HandoffPlan, localHandoffs: Vector[Q.LocalHandoff] |
| CandidateProjectionView.SemanticView | key, gaps: Vector[Q.ScopeGap] |
| CandidateProjectionView.DecisionView | key, reason: Q.DecisionReason, repositories: Vector[Q.RepositoryIssue], numbering: Option[NumberingView] |
| NumberingView | reason: P.DecisionReason, records: P.Records |
| AdmissionView.EvidenceRequired | assessments: Vector[AssessmentView], pendingKeys, rejectedKeys |
| AdmissionView.NoAdmissible | assessments: Vector[AssessmentView], rejectedKeys |
| AdmissionView.DurationRanked | assessments: Vector[AssessmentView], eligible: Vector[RankedView], durationLeaderKeys, rejectedKeys |
| AssessmentView | key, boundaryProof: Option[CandidateKey], shortReview: Option[ShortReviewView], status: A.Status |
| ShortReviewView | rebalanceAttempts: Vector[A.RebalanceAttempt], mergeAttempts: Vector[A.MergeAttempt], rejection: Option[A.Rejection] |
| RankedView | key, score: E.Score |
| ObjectiveView.RecordRequired | required: O.RequiredRecord, leaderKeys, costScores: Vector[CostScoreView] |
| ObjectiveView.ReasoningDemandRequired | items: Vector[O.UnresolvedItem], leaderKeys |
| ObjectiveView.Selected | key, stage: O.Stage, costScores, overheadScores: Vector[OverheadScoreView] |
| ObjectiveView.HumanDecisionRequired | leaderKeys, authorities: Vector[O.AuthorityProjection], costScores, overheadScores |
| CostScoreView | key, potentialSavingUnits: String, materialSavingUnits: String |
| OverheadScoreView | key, childCount, handoffCount, validationRunCount, totalUnits: String |

All *Keys fields are Vector[CandidateKey]; all counts/range indexes are Int,
except E.Score's existing distanceFromSixHoursMinutes is Long. No duration,
closure, source, authority, profile or allocation is recomputed by projection.

BuiltView retains all three complete allocated candidate documents, including
incoming handoff maps and validation dependencies. Their common source,
inventory and boundary are already in selection. boundaryProof is Some(key)
only when the assessment's complete admitted boundary matches the corresponding
BuiltView and common selection facts; compare data, not hashes. None preserves
the actual absence. This avoids duplicating the same complete documents at
every trace stage without dropping proof facts. It is not decoded authority.

For Q.Requirements, copy the W.ProjectionRequired aggregate scopes exactly,
including its already deduplicated order, and its decisions' ordered keys;
keep all per-candidate ScopeGap values/owners as well. Recovery need not redo
scope aggregation to discover what was requested. No Built documents are
invented for a SemanticView/DecisionView, and no Built inside a mixed frontier
is omitted. For Selected, its key identifies the retained BuiltView whose
complete boundary matches O.Selected; localHandoffs remain attached to it.

BigInt scores project using `.toString`. Their wire values must match the
canonical unsigned decimal grammar `0|[1-9][0-9]*`. Preserve arbitrary precision
within the whole UTF-8 byte bound. Reject negative values, signs, leading zeros,
fractions, exponents and numeric/Boolean/coerced replacements. No rescore or
threshold comparison is performed by decoding.

### Shape inspection is not business replay

Keep shape inspection in R, invoked by dispositionC and the storage boundary.
It checks complete/non-null structural data, closed stage composition, ordered
keys/coverage and retained-reference relationships; it does not prove current
authority or recalculate a candidate's desirability.

- Preserve exactly the enumerated order and every candidate. Keys are nonempty,
  strictly increasing, distinct, within the selected item interval. Child ranges
  match their cuts, cover contiguous selected items exactly, and retain positive
  expectedMinutes <=480. Do not re-sum the selected duration estimates.
- Enumerated counts are nonnegative, within the selected maximumCombinations,
  and evaluatedCount equals retained candidates plus rejectedByCeiling. At least
  one candidate exists. NoAuthorizedSplitBoundary has counts 0/0; the all-ceiling
  refusal has positive equal evaluated/rejected counts. Do not enumerate cuts.
- Optional stages form a prefix. Non-Enumerated partition has no later stage.
  Enumerated without projection is AllocationRequired; its selected allocations
  must be absent. A projection needs the selected complete allocation data.
- Projection's ordered keys equal the complete enumeration and its Built
  document references match the corresponding allocation. Projected contains
  only BuiltView; Requirements contains a real SemanticView/DecisionView and
  has no admission/objective. Its aggregate work matches the retained per-key
  requirements, using only passive field comparisons, not Q.projectC.
- Admission requires Projected and complete ordered assessments. boundaryProof
  may refer only to its own present BuiltView. Short-review/status consistency,
  pending/rejected subsets and eligible/leader membership are structural checks.
  All ranked keys, costs, overheads and selected keys refer to retained candidates;
  preserve each supplied order rather than sorting/re-ranking it.
- Objective requires DurationRanked. A required record, unresolved reasoning,
  authority-sensitive tie or selected key is a distinct closed disposition.
  Human leaders and authorities retain exact positional coverage. Never choose
  a tie or promote decoded Selected into independent proposal admission.
- Unknown item/edge/scope/reasoning data retain original identities and reasons.
  No unknown value is converted to an estimate or to known-work AI dispatch.

Disposition is derived solely from the closed trace composition and has exactly
the ten current W outcome names. Do not store a redundant caller-asserted
winner/needs-AI/executable/success flag. Decoding returns Record, never Projected
or a privately admitted native recovery. A coherent raw record still proves
neither completeness of an authoritative native commit nor current facts.
Do not describe local typed storage as tamper-proof against database mutation.

## Closed codec contract

Create `private[smworkflow] object SplitPhasePlanningSnapshotJsonV1`:

- schema = `sm-workflow.split-phase-planning-snapshot.v1`;
- storageField = `splitPhasePlanningSnapshotPayload`;
- maximumPayloadBytes = 1024 * 1024, measured as UTF-8 bytes;
- `encodeC(record): Consequence[String]` and `decodeC(raw): Consequence[Record]`;
- prefix schema + `:` followed by exactly `{schemaVersion, payload}`;
- `payloadCodec: WorkflowResultJsonV1.PayloadCodec[R.Record]`, typeIdentity
  exactly `SplitPhasePlanningSnapshot`, whose Json value is the same complete
  closed envelope without the text prefix, not a ContinuationResult.

Use the existing SplitPhaseExplicitPartitionRawCodecs givens for P/B/E/W raw
data. Define named leaf codecs before aggregates to avoid forward initialization
and excessive nested Scala inline derivation. New sealed view sums use Circe's
explicitly derived single-variant object form, e.g. `{"Enumerated":{...}}`;
retain the above variant/field names as v1 wire names. No legacy reader, aliases,
default inferred fields, generic class-name reflection or lossy field filter.

Parse one raw envelope and decode once; shape-check the passive record and
compare its complete canonical Json projection to the parsed Json to close
unknown/missing nested fields. Harmless formatting/member order is accepted;
wrong primitives, unknown variants, partial structures and conflicting references
refuse. Preserve the actual optional fields, including explicit None values.
Guard serialization errors/null data; encode must not return partially valid
or oversized data. PayloadCodec uses the same shape/envelope/size contract;
invalid encoding raises a bounded argument error consumed by the native guarded
codec, while decode returns Left. No unvalidated native write is selected here.

The later managed path must pre-encode the **entire** native receipt and all
actual Creates, checking the existing native byte bounds and 64-effect maximum
before execution. A too-large frontier is a host-capacity refusal/need, not
truncation, split transactions, an AI estimation request or silent empty data.

## Exact next implementation frontier

New production paths, all under the existing admission package:

1. src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePlanningSnapshot.scala
2. src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePlanningSnapshotJsonV1.scala

New executable specifications:

3. src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePlanningSnapshotSpec.scala
4. src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePlanningSnapshotJsonV1Spec.scala

Evidence ownership: this journal and docs/phase/phase-2.md only. Reuse the existing
explicit partition/candidate allocation fixtures without changing them. Private
test helper methods may live inside these two new specs; do not grow a fixture
framework. Every new behavior group has adjacent Given/When/Then and should
expectations. Preserve the broader dirty/untracked checkout.

Do not edit old E/Q/A/O/W processors, RawCodecs, context/Start/semantic codecs,
completion/enrichment publication, CML, DefinitionBinding, RuntimeCatalog,
Factory, provider, upstream repository, build/test settings or shared skills in
this prerequisite. Existing public/legacy contracts remain compatible.

Class C (passive persistence contract), bounded settled implementation after
this parent design. Expected slice execution 30-90 minutes, medium confidence:
two projection/codec files, two specs, existing raw codecs and fixtures; no new
provider/generator/transport. This is a slice estimate, not a new whole-Phase
packing result, profile attestation or promise of Phase completion. Freeze all
source/spec paths before edits; stop and replan for a newly necessary source,
changed schema/design, provider or public execution boundary. No additional
semantic discovery or review selection is delegated to a worker.

### Required executable behaviors

- Whole allocated all-known plan preserves all candidates, facts, selected key,
  complete documents, local actions and mechanical stage prefix without another
  planning pass; cover Duration/Cost/Overhead/StableTieBreak results.
- Unknown-only duration/edge requirements retain known neighboring work and
  exact unknown identity/reason sets, with no later stages or fake allocation.
- Missing allocation and both unavailable-partition reasons stay distinct.
- A mixed scope frontier retains Built documents and all unresolved candidates,
  exact aggregated scopes and per-candidate owner/gap records. Mixed repository
  and numbering Decisions retain actual reasons and complete numbering data.
- Admission retains rejected/eligible/leader sets, all short-child reviews and
  merge/rebalance attempts, including blocked-by-handoff facts. None of these
  become a winner by omission of rejected candidates.
- Missing objective policies, unresolved reasoning and different-authority
  human ties remain distinct, with ordered score/authority correspondence.
- Long.MaxValue input rates retain exact score strings greater than Long;
  encoding/decoding does not round, overflow, change thresholds or optimize.
- HostEvidenceRequired passive wire/shape cases are honest raw-data codec tests,
  not fabricated private W outcomes or proof of reachable positive execution.
- Complete codec roundtrips preserve every variant/optional field, mixed frontier
  and local handoff; formatting/member-order changes are accepted. Unknown/
  missing nested fields/variants, wrong primitive types, malformed decimals,
  crossed keys/coverage/stage composition/proof/document refs and oversize UTF-8
  records refuse without fallback. Null dependencies and Projected constructor
  privacy are checked without reflection bypass.

Use constructed processor outcomes for positive projection tests, not a
hand-created W result or tests based only on source text. Codec tests deliberately
use raw Record values; their success is not execution authority or native recovery.

### Frozen proportional validation

Representative and affected accumulator, each through a fresh typed permission
and the existing serial wrapper under retained parent-only limited recovery:

```text
["--batch", "testOnly org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningSnapshotSpec org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningSnapshotJsonV1Spec"]
["--batch", "testOnly org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPlanningAdvanceSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseMechanicalCandidateAllocationSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitCandidateProjectionSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitCandidateAdmissibilitySpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitObjectiveSelectionSpec"]
```

Test/compile now supplies official runtime preparation automatically. No separate
compile, full suite, producer refresh or Cozy build refresh is selected absent
an actual dependency failure. Keep normal caches, fixed JVM, platform review,
machine-wide lock, actual process evidence/log/summary and all 473 prior IDs.
No command identity/allocation is created by this planning turn. Require tracked
diff checks plus explicit checks of owned untracked source/spec bytes.

Pre-review repair count for this new prerequisite is 0/3, not a reset of any
previous ledger. General binding remains 0/3; test-settings correction is 1/3;
producer is 3/3. An ordinary accepted implementation/review manifest still needs
the missing original authority/base/lease evidence; do not fabricate it from
current HEAD or parent commentary. Independent protected contract review and
the original Phase full-review/release remain required before acceptance.

## Next state and end-state fidelity

Next state: IMPLEMENT these exact four paths under the user's retained limited
recovery exception; no ordinary accepted PLAN transition is asserted. This
turn fixes a complete, source-grounded passive contract and validation frontier.
It is authoritative design progress, not new implementation/test evidence.

After its actual validation, freeze and implement the managed consumer: exact
application mapping before allocation, original Start/current-fact admission,
one computed frontier, all actual Built documents plus allocated planning/mapping
Creates, one native transaction/checkpoint, whole-set verified readback, and
read-only current-prefix recovery that never invokes the old reconstructive
semantic completion route. The same snapshot must be captured on original AI
completion execution. Use the captured continuation-side collection and sole
Subsystem-owned pool/grant pair; no direct Workflow SQLite or fake AI receipt.

Then install actual generated `advanceSplitPhase`, its versioned provided ABI
and default GoalPhase+SplitPhase service assembly, genuine human ingress,
unknown-only partition/scope/reasoning WorkOrders and Decisions, document
publication/application, CLI/server/MCP/separate-process behavior and generic
packaged sm-* skills with cncf-* compatibility. Managed external commands,
safe automatic merges/human-or-AI conflicts, all original Step independent
reviews/exact commits, one Phase full review/full validation/final release are
still part of the unchanged Goal. A passive codec or green internal fixture
does not close any of those product or assurance requirements.

## IMPLEMENT candidate: passive projection and closed codec

The four frozen production/spec paths are now implemented under the retained
parent-only limited recovery exception. Projection copies the existing W trace,
including complete mixed frontiers, raw short-child attempts, local handoffs and
exact BigInt decimal scores. Shape inspection and the closed V1 codec do not
invoke any planning processor, allocator, provider or native transition.

The two new specs use actual W outcomes for projection and deliberately raw data
for the currently unreachable HostEvidenceRequired wire case. Representative
and affected tests remain pending at this entry. No generated public operation,
native read-only recovery, independent acceptance review or commit is claimed.
Pre-review repair count remains 0/3. The 924/924 test-settings correction result
precedes these new source edits and is not evidence for this candidate.

### Bounded pre-review repair 1/3

805 failed in Compile before any test ran: Scala synthesized the same anonymous
given name for P.DecisionReason and Q.DecisionReason codecs. Give both existing
leaf pairs distinct explicit names; no schema, behavior or selected path changes.
SBT and wrapper exited 1 and released the shared lock. Evidence:
`/tmp/skill.cncf.d/a-7b709095-5b19-459a-9a76-26693839427d/85547-20261002T115513Z.log`
and its complete `.summary.json`. Retry the same representative command with
a fresh permission/identity and all 474 consumed IDs. Acceptance remains open.

### Bounded pre-review repair 2/3

806 compiled both production files and prepared the official runtime classpath,
then failed Test/compile before tests. Two new fixtures used the misspelled named
argument lowerUnitsPerMinute instead of lowerCostUnitsPerMinute; one assertion
read rejectedCandidates through A.Outcome instead of its actual DurationRanked
subtype. Correct only these new spec references. The production contract is
unchanged. SBT/wrapper exited 1 and released the shared lock. Complete evidence:
`/tmp/skill.cncf.d/a-cb33e686-19d2-437c-8985-90c840140337/85998-20261002T115612Z.log`
and its `.summary.json`. Retry the same representative command with a fresh
identity/permission and all 475 consumed IDs. No test success is claimed yet.

### Representative evidence and bounded coverage repair 3/3

807 compiled and ran both new specs: 15/15 passed, two suites, no abort/cancel/
ignore/pending, SBT/wrapper exit 0, lock released. Complete evidence:
`/tmp/skill.cncf.d/a-bec30123-cff1-4973-afbf-e8b533f381ee/86617-20261002T115729Z.log`
and its `.summary.json`. The parent coverage check found that the broad raw
roundtrip group did not exercise numbering/repository Decisions and every
selected stage, and the short-review group compared complete attempt data but
did not assert a real blockedByHandoff=true attempt. Supplement only the new
projection spec: every actual projected positive outcome now also roundtrips
the codec, and the short-review group uses a real admitted handoff and asserts
both blocked merge and blocked rebalance attempts. Production files, contract
and the frozen command remain unchanged. This final bounded coverage repair is
3/3; a further failure needs diagnosis/replanning rather than another blind
repair. Rerun representative before the affected accumulator. No acceptance.

## Proportional validation of the final candidate

808 reran the unchanged representative command after the coverage supplement:
**15/15 examples in two suites**, no failed/aborted/canceled/ignored/pending
examples. 809 then ran the frozen affected accumulator on the same production
and spec tree: **52/52 examples in five existing suites**, with the same zero
failure/skip counts. Both SBT and wrapper exits were 0 and the shared lock was
released. Test/compile supplied official cozyPrepareRuntime automatically; no
producer refresh, separate compile or new full-suite run was performed.

- Representative complete log and summary:
  `/tmp/skill.cncf.d/a-e4f050ee-1f14-49db-80c3-078352b583cb/87256-20261002T115910Z.log`
  and the adjacent `.summary.json`.
- Affected complete log and summary:
  `/tmp/skill.cncf.d/a-e5d7fc5f-346a-44d0-a692-ecc4bdb19cff/87634-20261002T115957Z.log`
  and the adjacent `.summary.json`.

The actual logs and complete summaries were inspected. Tracked diff whitespace
checks and explicit /dev/null comparisons for all four owned untracked Scala
files reported no whitespace problems; the latter return 1 because each new
file differs from /dev/null, not because of a diagnostic. All 478 consumed
execution identities remain in the ledger. No SBT process remains active here.
The passive slice pre-review repair count is 3/3 (two compile-reference repairs
and one executable coverage supplement), not a reset of upstream or earlier
repair limits.

This is parent-only limited-recovery candidate validation, not independent
protected contract review, an accepted Step/Phase, or a commit. The original
Goal remains active with its original bindings and end-state requirements.
Next bounded state is PLAN for the managed consumer described above: captured
continuation-side collection, independent original Start/current-fact checks,
complete immutable application mapping and Built documents, native whole-receipt
capacity preflight, one same-pool transaction/checkpoint, verified readback and
read-only current-prefix recovery. Snapshot capture on original AI completion
must use this same passive format. Public generated advanceSplitPhase and
multi-workflow default assembly still follow that integration; no such public
or durable recovery capability is claimed by these green internal tests.
