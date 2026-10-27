# Phase repair inventory continuation

Failure identity: `P99-INVENTORY-01`. This is an operational same-Session repair
contract. It does not start another Goal, Phase, Session, Step or repair batch.

## Rules

R1. An ordered Plan has an optional trailing `phaseRepairInventory` vector. An
absent old JSON field means the empty vector; explicit null and malformed values
refuse. The encoder omits an empty extension. Entries are at most 256 unique,
literal relative paths without traversal, controls or wildcard syntax.
`phaseOwnedPaths` preserves the distinct union of original Step ownership followed
by the admitted inventory. Accepted Step definitions and their evidence remain
immutable. Starting with an unadmitted inventory, ordinary Plan adoption and
refresh cannot introduce or change it.

R2. Pure inventory adoption requires all Steps accepted, an active phase-wide
Repair or its Decision, a real directive and a positive already reserved repair
count at most the effective limit. Equality with that limit retains the last
already reserved batch; no additional batch is opened. Pending repair, an
unreserved next batch, policy/trend stop, document
Decision, reviewed candidate and closure frontiers refuse. Only a fresh Plan
revision and a nonempty prefix-preserving append of uncovered paths may change.
Identity, repository, Phase, Steps, raw/effective acceptance, checks and repair
policy remain exact. Adoption returns the same Repair at generation + 1, retaining
base, accepted commits, current/cumulative counters, control and directive. Old
candidate/check/review/intent frontiers are invalidated, with no batch reservation
or refund.

R3. The native walker alone constructs the continuation source from an actual
issued phase-wide RepairStep and accepted `decision-needed` result. It proves the
contiguous native request, suspension and completion; exact Session, Plan, base,
accepted prefix, generation, repair progress and original checked-failure context
are required. Dependency/diagnostic work, unknown effects, stale records and
foreign sources refuse. A typed `planRevision` that contains the exact inventory
append is captured with original saved acceptance and admitted by one adjacent
guarded mechanical receipt before another Human request. Every added path must
be an exact current finite host `candidatePaths` entry. Registration does not
grant Git, SBT, File or other capabilities.

R4. Existing paused Sessions use `describeGoalPhaseRemainingPlan` and
`adoptGoalPhaseRemainingPlan` with the distinct closed target marker
`sm-workflow.phase-repair-inventory.v2`; unqualified v1 targets and receipts
refuse. Selection is explicit, never a fallback after another continuation proof
fails. A target binds immutable semantic work/result references and revisions,
Plan/base/accepted prefix/progress/generation, actual question identity and
pending revision, optional actual Defer choice/result reference, current frontier
and proposed capture. Description reads
retained history only. Unanswered adoption uses native DecisionSupersession and
retains the unanswered question. Accepted-Defer adoption appends one mechanical
successor after that actual answer; it never rewrites, skips or treats Defer as
permission. Cancel and unrelated Human decisions refuse. Reopening authenticates
the same contiguous pre/post-Defer history before issuing work. Unanswered
inventory supersession uses `ordered-phase-repair-inventory-human:` plus the
actual directive reference identity; ordinary Human resumption keeps its own
causation and owner.

Full request/result, pending-history and answer payloads remain private native
facts. Before capture and replay, the sealed Source proves their complete actual
contiguous lineage and exact typed values. The public target carries bounded
native references instead of copied logs or records. Reconstructed Saved values
retain the freshly proven actual request privately for the original checked
failure context. Supplied targets and directions keep their exact codecs and
comparisons; decoding a target alone grants no native provenance.

R5. Public recovery requires independently attributable direction through the
existing remaining-plan evidence purpose/provider. The complete target, proposal,
participant, Component, rationale and reference must match. Extra fields,
explicit null, mismatched evidence, cross-source targets and duplicate/stale
adoption refuse. No new filename consent question is issued. Public calls append
direction only; later ordinary Advance issues the same phase Repair. An unknown
receipt/effect does not authorize retry. Existing ordinary, Review, current-Step,
failed-Phase and timeout targets retain their separate meanings/codecs.

R6. Actual repair candidate selection, staged/changed-path admission, native
validation and metadata context, retained candidate replay, recording/recovery
and release commit use `phasePathsOwned`; `phaseOwnedPaths` is the declared
selection/metadata vector. Original Step roots retain their existing directory
coverage. Every appended inventory entry owns exactly that file identity, with
no descendant expansion. Actual dirty descendants of a registered directory
entry refuse before source history or staging. Completed Step evidence continues
to use its original Step contract. Only selected registered paths are staged by the
typed Git/UoW path. The new candidate needs fresh normal checks, independent
Phase review and actual release closure; no old PASS is reused for the new
source. The native receipt schema is `sm-workflow.phase-repair-inventory.v2`, its
mechanical causation is `ordered-phase-repair-inventory`, and the finite CML edge
is Decision -> Repair on `phase-repair-inventory-continued`.

## Executable examples

E1. `GoalPhaseRepairInventoryContinuationSpec` authors pure eligibility/delta
coverage: immutable prefix/base/counters/directive, one generation, no Step,
saved acceptance, append prefix and refusal of changed contracts or ordinary
Plan routes.

E2. The same local specification covers absent legacy extension, explicit empty,
null/malformed/wildcard/traversal rejection and extended wire round trip. Its
`bounded native references` case proves exact v2 codec and Core Record round
trips, a fixture target below 64 KiB, absence of retired copied payload fields,
and refusal of crossed revisions, old v1, extra fields and explicit null.

E3. `NativeGoalPhaseRepairInventoryCliSpec` authors an isolated real Git/SBT
scenario using the normal known-failed-Phase fixture and optional installed CLI
selection. An actual typed proposal continues without Human, fixes the product
test outside original Step ownership, performs a fresh successful Test and
independent fixture Phase review, and closes with a real selected-path release
commit while preserving accepted history and counts.

E4. The native specification separately authors actual unanswered, Defer and
Cancel frontiers; public describe/preparation/adoption, missing/crossed/extra
field evidence refusal, stale/duplicate adoption and reopen continuity. Actual
public targets stay below 64 KiB in this fixture and bind the real native pending
revision, question/work/result identities and Defer choice/reference. Fixture
evidence is specification-owned and asserts no production Human provenance.

Execution, generation, focused checks, independent review and installed delivery
belong to the parent workflow. Authored examples alone are not passing evidence.
