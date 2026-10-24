# GoalPhase Step purpose repair

New ReviewStep work captures `purposeRepair` with identity
`sm-workflow.step-purpose-repair.v1`, the selected Step identity, original
purpose and exact ownedPaths. Capture occurs only when new work is issued.
History walking validates the saved descriptor against the saved selected Plan
and never adds one to old work. ReviewPhase retains its existing contract.

An independent reviewer may return `repair` for defects within that issued
Step purpose and scope without per-finding Human approval. `findings` remains
an ordered vector of at most 128 nonblank NUL-free UTF-8 texts of at most 16384
bytes each. Optional `repairFindingSources` has exactly one source per finding,
with contiguous 1-based ordinals in vector order. Each closed tagged source is
either `required-condition` (findingOrdinal, conditionIdentity) or
`step-purpose` (findingOrdinal, stepIdentity, rationale, affectedPaths). Extra,
irrelevant, unknown and null fields reject. Purpose paths are nonempty,
distinct, at most 256 repository-relative paths without traversal or control
characters and match existing Step ownership prefix semantics.

Required-condition sources cover every applicable required unsatisfied report
observation in report order and use the exact original detail as finding text.
All required conditions must be evaluated for typed repair. A satisfied,
excluded, unknown or not-evaluated condition cannot be a failed source. Purpose
sources cover additional reviewer judgments. Runtime binds identities and scope;
it does not classify prose or turn observations/advisories into defects.
Accept/completed still requires every required condition satisfied, empty
findings and absent sources. Sources on any non-review result reject.

Public CML represents the optional collection as
`GoalPhaseRepairFindingSources { sources: Vector[Record] }`; public
`GoalPhaseWorkResult.repairFindingSources` is an optional wrapper. Explicit
boxing/unboxing retains the exact source records. The raw public boundary
rejects null or unknown wrapper fields before generated decoding. The native
saved Result uses an optional JSON array directly. Both forms feed the same
strict Source decoder and independent attestation.

Native request v2 is the unchanged absent-descriptor projection; v3 requires
the descriptor. Native result v1 is the unchanged absent-source projection;
v2 requires a valid source array. Encode old unmarked values using their old
versions and typeIdentity strings. New fields in old versions, explicit null,
and missing new-version fields reject. New work also accepts the literal old
required-only result, with exact ordered required failure equality; untyped
results never become purpose judgments. Every contract and source field enters
independent target/result normalization and evidence matching.

Typed repair uses ordinary ReviewAdmitted Repair, RepairDirective, issue ledger,
budget, generation, checks and independent verification. A FIXED self-report
alone never resolves a review issue. No new issue or repair-budget owner exists.

## Saved legacy Human batch continuation

`describeGoalPhaseReviewPurposeRepair` reads the exact current unanswered Human
from an old issued ReviewStep with absent `purposeRepair` and an accepted untyped
decision-needed report. Newly marked ReviewSteps use the ordinary typed route.
Its target binds the original
assignment and result reference/report, unchanged Plan, Step purpose and scope,
base, accepted prefix, generation, candidate/checks, repair progress and every
saved blocking finding with its exact ordinal/text/OPEN REVIEW issue. It never
reinterprets old required observations or manufactures reviewer sources.

Passive `prepareEvidence` kind `review-purpose-repair` requires the actual
description and `GoalPhaseReviewPurposeRepairInput`, matching reference and
participant, and explicit `acknowledgeOriginalPurposeDefects=true`. A separately
provisioned genuine Human directive owns the acknowledgment that the entire
batch is original-purpose work inside scope. Engineering approval, description
and preparation confer no live direction or execution permissions.

`admitGoalPhaseReviewPurposeRepair` independently verifies that directive and
atomically records a DecisionSupersession. The old review, observations,
unanswered question and choices remain unchanged. No Plan revision, required
condition, Resume semantics, check, Submit, Advance or commit is added. The next
separate ordinary Advance reconstructs same-Step RepairStep with one ordinary
charge. Exact target/revision validation across restart prevents duplicated
charges; stale, repeated, foreign, exhausted, active-repair, conflicting or
unknown-effect inputs reject. RepairDirective.reason is exactly the original
review summary. The existing decisionContext retains the complete original
requestingResult, explicit `legacy-review-human-purpose` origin, full rationale
and actual independent directive reference. The origin identifies this business
direction without answering the old unanswered Human question; the retained
prior report and stored payload preserve the original review and provenance.

The native supersession envelope retains historical outer family typeIdentity
`GoalPhaseRemainingPlanAdoption`. This identifies the storage family only.
The shared closed sum reads once: ExistingRemainingPlan delegates to the
unchanged old payload codec and reconstruction, while LegacyPurposeRepair uses
exact inner `schemaVersion=sm-workflow.goal-phase-legacy-purpose-repair.v1`,
`origin=legacy-review-human-purpose`, and complete typed payload. Unknown,
null, mixed and extra discriminators reject without decoder probing or fallback.
Old writers and old payload projections are unchanged, including histories with
an earlier remaining-Plan adoption followed by a later purpose continuation.

Executable specifications: GoalPhasePurposeRepairSpec,
GoalPhaseLegacyPurposeRepairContinuationSpec, NativeReviewScopeSpec,
NativeReviewPreparationSpec and NativeGoalReviewRepairAdoptionCliSpec. Semantic
fixture actors provide specification-owned evidence only, never live consent.
