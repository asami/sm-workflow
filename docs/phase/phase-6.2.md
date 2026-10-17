# Phase 6.2: FIX, Finding Recording and Dependency Decisions

status=planned
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-6.3
aggregate_validation_sequence=["PHASE-6","PHASE-6.1","PHASE-6.2","PHASE-6.3"]
depends_on=phase-6.1.md
Planned: 2026-10-13. Split from Phase 6.
Owner: sm-workflow. Predecessor: [6.1](phase-6.1.md). Successor: [6.3](phase-6.3.md).

## Goal and connected implementation order

On the accepted 6/6.1 request/result, execution/live-plan and Step-Admission
interfaces, deliver validation design, TEST_FIX/REVIEW_FIX, human-selected
improvement, counter reset, semantic maintenance, actual finding recording and
dependency decisions. Extend the same driver with representative distinct branches;
do not build another engine or repeat Phase 5's complete threshold/state matrix.

### NICT delivery-friction observations — adopted 2026-10-17

This is clarification of existing requirements from the
[NICT incident handoff](../journal/2026/10/2026-10-17-nict-skill-failures-phase-6-family-handoff.md),
not a new item ID, acceptance gate or retrospective reopening. Preserve current
issued work, in-flight results and applicable validation/review evidence. The
owning development chat adopts it at the next ordinary planning/dispatch boundary;
this document edit does not mutate live plans, requests, counters or Goal state.

Under P6-TS01..06 and the existing finding-recording requirements, distinguish
an execution-procedure defect from a product/test failure. Repairing the route
does not by itself invalidate applicable product validation or independent review.
Assess actual changed requirements/inputs/scope and retain applicable evidence;
changed product candidates still need their required selected checks/review.
Same-candidate retained results are not proof of selective check reuse after a
change. Do not add a new per-check reuse engine to satisfy this observation.

Use connected delivery units and proportionate selected checks; show which checks
and review scope a bounded repair repeats and why within the existing driver.
Record optional findings in the existing one-batch AI task; no items skips it.
Do not create per-helper/per-finding review cycles, recursively review recording,
or require progress-document edits at every internal state. Missing actual scope
authority for shared-component edits stays a genuine boundary, not a failed
product test or permission to discard previous product acceptance.

### Managed-suite design, FIX and improvement skills

Own P6-TS01..06. AI authors test/annotation/CML/Slice-validation metadata changes
through Candidate/review/admission, including feature IDs and explicit supplemental
refs/typed parameters. Runtime executes adopted SMOKE/FOCUSED/feature union and
validation, never an AI-provided improvised official test list. FIX uses original
requirements/current Candidate plus distinct test diagnostics or review findings,
implementation-like routing and explicit issue outcomes. FIXED is a claim awaiting
validation; preserve UNRESOLVED/DECLINED/BLOCKED, coverage and recurring findings.
Test/suite/requirement/design gaps follow explicit revision/adoption; do not blindly
change production to satisfy a bad assertion or broaden an accepted profile.

Carry Phase 6's Effective Acceptance Boundary consistently into FIX/Review/Admission:
required failures block, excluded assurance does not become repair authority, explicit
adopted Goal/Phase overrides precede project defaults, and conflicting adopted
conditions need their owner. Human-selected warnings/replanning produce bounded
proposals; only developer adoption changes the plan. Demonstrate actual later
measurements before claiming improvement; indicators alone do not request replanning.
Report semantic depth/coupling/minor-fix rationale/convergence hints separately
from counts; guards retain authority and BLOCKED hands off. No model optimism,
renaming or advisory classification exempts substantive work.

### Security findings in FIX and decisions — adopted 2026-10-14

Apply the [security review profile](phase-6-security-review-profile.md) within
P6-TS02/03, P6-CB01..04 and the existing finding-recording contract. Use a
representative attributable security contract violation on the same connected
driver. An in-boundary blocker follows normal FIX and later affected validation/
review; preserve its finding identity and actual unresolved/provider-gap state.
The security label does not grant additional repair authority or reset counts.

A blocking security finding must identify the adopted contract and a concrete
violation or a missing check that satisfies the profile's three applicability
conditions. Absence of an optional scan or duplicated CNCF/Cozy producer test is
not a blocker. FIX/re-review targets the affected violation only; ordinary use
of delivered producer guarantees does not require internal producer retesting.
Do not introduce compensating application guards/checks merely because a
provider is unavailable. Preserve no-additional-check and evidence-reuse paths
through ordinary FIX/decision scenarios without adding a separate safety ledger.

Record out-of-boundary hardening proposals through the existing nonblocking
recording/adoption route. A required upstream change follows the currently
admitted dependency scope and Human decision contract, not an automatic
CNCF107/Cozy83/CBD16 Phase launch. Keep deterministic lint and security semantics
with their providers; do not copy a scanner or authorization engine here.
No new repair loop, mandatory threat matrix or repeated unaffected checks is added.

### Workflow responsibility boundary

Own the P6-TS05 runtime/client reset contract. Explicit user continuation approval
resets both active counts once, preserving accumulated history and convergence;
subsequent repairs do not re-prompt per finding. Accepted Step completion plus its
commit starts the next Step at zero. Checkpoint/WIP/within-Step repairs preserve
the unfinished loop. Implement missing decision/guard/Step-close transitions and
test exhaustion -> approval -> multiple repairs and Step-close/next-Step through
the real client. Phase 9 reuses this accepted contract; it does not own initial reset.

### Dependency Component Boundary and Human Approval — 2026-10-12

Own P6-CB01..04/previous Scope16. Planning/implementation/review identify dependency
internal changes using adopted Component/Usage Contract boundaries, including
same-repository dependencies. Report target, modification/reason/contract and
dependent work before dispatch/edit/acceptance. Runtime retains the proposal and
yields to actual Human approval/reject/defer. Actual unapproved discovered edits
remain attributable for developer judgment; do not silently approve or revert them.
Approval scopes a linked work unit with acceptance, not arbitrary future edits.
Reuse existing explicit approval, while a new out-of-scope proposal yields again.
Normal public-contract use/inspection is permitted; an AI claim, hygiene label,
passing test or unchanged API is not consent. No filesystem monitor, scope-copy
certificate or new approval engine; document detection limits. This product
contract reuses the currently adopted integration scope below; it does not
restore the superseded CNCF/Cozy wait-only instruction or grant unrelated scope.

### Review and maintenance responsibilities — 2026-10-12

Own P6-HR02/03: one integrated AI review judges meaning-dependent GWT and other
names/comments/examples alongside correctness/test adequacy. Batch adopted
semantic corrections in ordinary REVIEW_FIX; distinguish explanation-only edits
from assertions/behavior for validation and counting. Runtime deterministic
history/tool handling already belongs to Phase 6.1 P6-HR01/04 and is reused here.
Unadopted nonblocking observations do not acquire repair authority.

### Hygiene and development-candidate recording — 2026-10-12

Runtime consumes required/nonblocking findings and issues one bounded recording
request per applicable batch with canonical identities/source/destination. The
actual AI/recording skill reads and edits the selected project journal/candidate
document, returns saved file/section refs and actual outcome; runtime consumes the
result and progresses. A read-only reviewer does not do the edits. No items skips
recording; partial/failure is truthful and does not silently turn into implementation.
Recording does not adopt work, broaden repairs/counts or recursively require another
review/recording cycle. Demonstrate actual file edits/results, accepted nonblocking
and mixed reviews plus deterministic no-items/failure cases; no fabricated receipt.

### Same-Context Review execution — adopted 2026-10-18

Add the Same-Context Review route after Phase 6.1; do not modify or reopen the
currently executing 6.1 delivery to introduce it. Consume cbd-support Phase 15's
ReviewRequest Provider. For semantic STEP/FIX/REVIEW_FIX work, cbd-support returns
the structured ReviewRequest/review policy without starting AI. sm-workflow returns
that request to the active Codex worker that already owns the implementation
conversation and consumes its typed Review result through the existing
Finding/Evidence/Admission path.

This is a context-quality requirement, not merely token optimization. The worker's
current conversation may contain goal/phase intent, design choices, rejected
alternatives, inspected source/spec relationships, test diagnostics and repair
history that cannot be reconstructed reliably from repository files alone.
sm-workflow therefore does not copy, snapshot, persist or reconstruct that
conversation. Continuation keeps the semantic task in the active worker; only the
bounded ReviewRequest and returned result cross the cbd-support boundary.

Record review execution provenance sufficient to distinguish `same-context` from
`independent-context` without introducing a context ledger. Same-context is the
default for the connected implementation/FIX route. cbd-support must not silently
spawn another AI session in orchestrated mode. An unavailable same-context
continuation is reported truthfully; it is not permission for an invisible fallback.
Independent execution is an explicit orchestration decision and is integrated at
Phase 6.3 for review purposes that genuinely require independence.

Acceptance for this slice requires one actual implementation/FIX worker to receive
a cbd-support ReviewRequest, perform the semantic review in the same worker context,
return the typed result, and drive the existing finding/evidence flow, while the
cbd-support orchestrated route performs zero AI-provider invocations. Standalone
cbd-support AI execution remains a provider-owned route and is not removed.

## Closure and successor handoff

Own P6-TS01..06, P6-CB01..04 and P6-HR02/03 in the
[single checklist](managed-test-suites-phase-checklist.md#phase-6-family--aiskill-connection),
and the adopted actual recording conditions above. Accept representative live
skill/client FIX, recording, warning/improvement and Human-decision examples with
bounded branch specs and actual runtime outcomes. Preserve assigned finding IDs,
effect scope/history and unchanged permission boundaries. Hand 6.3 the accepted
complete contracts, installed/configured skills, saved recording refs, owner-specific
quality connection and applicable focused/review/Step evidence.
The [pre-split specification](../notes/phase-6-pre-split-plan-20261013.md) retains
full wording; [the ownership map](phase-6.md#exhaustive-unfinished-requirement-ownership)
does not duplicate any unfinished obligation in this child.

## Shared scope and adopted integration direction

sm-workflow owns this Phase under the shared bounded integration scope. Consume the accepted Phase
5.3 [business Operation contract](phase-5.3-operation-contract-handoff.md): one
session per execution, named inputs/results/answers and explicit Advance.
Keep actual effect permissions and the existing configuration/resource owners.
Do not restore full-envelope echoes, target discovery, hashes, TTL, copied-state
proofs, compatibility routes or a second workflow/configuration engine.

The earlier 2026-10-13 CNCF/Cozy wait-only instruction is historical, superseded
for selected existing main checkouts by the [Phase 6 adopted integration direction](phase-6.md#shared-scope-and-adopted-integration-direction).
The 2026-10-15 correction additionally selects the bounded CNCF110 producer and
CBD16 laundering rule slice. Consume Phase 6's accepted typed Git/SBT, resource
and process/HTTP opt-in contracts and evidence; do not rebuild P6-SBX01..05 here.
Preserve this child's existing delivery IDs and add only affected missing checks.
No whole upstream Phase activation/closure or unrelated repository work follows
from this dependency. Missing generic behavior belongs to its producer, never
a sm-workflow bypass. Reuse standing adopted authority rather than asking for it
again; new out-of-scope work still follows the existing scope-decision route.

The ordinary-use scope is unchanged: distinct executions have distinct sessions;
after abandonment start a NEW execution. No same-execution winner, identical
replay, exhaustive history or interrupted-effect recovery is promised. Actual
file scope/non-overwrite and truthful execution results remain required.

## Validation and closure obligations

Implement connected Executable Specifications with the owned behavior. Each
member retains proportional focused compile/behavior and affected-consumer
validation, independent Step acceptance review, Step commits, one comprehensive
review of its own Phase scope, bounded repair/focused re-review, recorded finding
dispositions, a closure ledger and its own local Phase-release commit.
Reuse actual applicable evidence; no acceptance/review cycle per helper or finding.

The generated top metadata is the reciprocal aggregate declaration. Phases 6,
6.1 and 6.2 omit only repository-full validation and truthfully record
`deferred-not-run`; Phase 6.3 owns `sbt --batch test` once for the entire accepted
serial sequence at its final release tree. No bootstrap exception applies:
`build.sbt` already exists. Normal development SBT still uses the registered
runner and machine-wide serial wrapper required by AGENTS.md.
This split records no test/review success or release acceptance.

## Non-goals

No Phase 7 workspace/overlay delivery, Phase 8 optional transport/provider breadth,
Phase 9 convergence/provider switching, OpenCode/local-LLM installation or new
universal agent framework. Manual local-LLM trials remain reference activity.
This planning update starts no product Goal, child execution, upstream work,
test, publication, push, deployment or commit.

## Phase Plan Gate

Phase Plan Gate: PROCEED
- target: 6h expected-duration target; 8h expected-duration ceiling
- estimate_calibration: no comparable planned/observed duration pair; no multiplier
- planning_demand: protected-decision
- recommended_parent_profile: gpt-6.1-sol / high
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: FIX/reset/recording/dependency disposition on the connected driver
- frozen_profile_transition_handoff: Phase 6.1 accepted process/plan/maintenance/Step-Evidence contract
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: expected 6h; range 5–8h; centered on target and below ceiling; low confidence
- incoming_semantic_handoffs: []
- merge_attempts_for_every_sub_4h_child: none; no child below 4h
- rebalance_attempts_for_every_sub_5h_child: none; no child below 5h
- adjacent_merge_structural_rejection_evidence: none; adjacent expected totals are 12h, above the 8h ceiling
- profile_cost_only_rejection_forbidden: true
- short_child_basis: none
- overhead_tradeoff: three added Phase review/closure/release handoffs; final-only avoids repeating repository-full tests; no quantified cost saving claimed
- agent_reasoning_mode_policy: default standard; pro only at an eligible justified agent launch
- runtime_suitability: re-evaluate in the Phase execution task
- source: applied split from Phase 6 on 2026-10-13

These are structural planning results, not verification of a future task's
runtime profile. Dependency delay is separate from the implementation estimate;
the 8h ceiling is a packing rule, not an execution timeout.
