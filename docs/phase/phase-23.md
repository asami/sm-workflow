# Phase 23: Strategic Phase Convergence

Status: planned; no implementation, Session or acceptance claimed.
Selected: 2026-11-19 (Asia/Tokyo)
Owner: sm-workflow development
Delivery priority: **Phase 9 -> Phase 18 -> Phase 23**.
Predecessors: Phase 9 Acceptance-Bounded Convergence and Provider Routing; Phase 18 Tool-Neutral Validation.
Checklist: [Phase 23 acceptance](phase-23-checklist.md).

## Purpose

Help a running Goal Phase converge by **strategically disposing of findings**, rather than repeatedly repairing every observed issue in the current Phase. Preserve the adopted Usage Contract and Effective Acceptance Boundary. Propose whether each finding must remain in the current Phase, is nonblocking Hygiene, is a Future Enhancement, or belongs to a separately planned Stabilization Phase. The purpose is not to conceal defects, manufacture PASS or force closure.

Astra is an optional high-capability **strategic assessor** for ambiguous multi-issue disposition. It does not implement fixes, change acceptance criteria, approve its own proposals or control Workflow state. Deterministic evidence and inexpensive classification come first. Use Astra only when its expected benefit warrants the cost.

## Scope and ownership

- **Phase 9** supplies actual convergence observations, required/optional boundary interpretation, provider-routing capability and bounded Take Over. Do not rebuild it here.
- **Phase 18** supplies tool-neutral validation evidence for SBT, project-declared checks and Flutter. Preserve its selected priority and independent acceptance.
- **Phase 23** adds cross-finding strategic assessment, explicit disposition proposals, dependency/blocker checks and handoff to existing approval/journal/plan processes.
- Existing GoalPhase Fix Assessment remains advisory; its PROGRESSING/STALLED/REGRESSING/BLOCKED values are signals, not automatic authority.
- Reuse existing Candidate–Admission, Human decisions, Journal finding identity, Hygiene and Development Candidate mechanisms. No new planning engine, approval gate or issue database.

## Disposition contract

For each identified finding, retain original ID, origin, severity, source evidence, required acceptance criterion (if any), dependencies, proposed destination, rationale, risks, confidence and review/approval status.

Destinations:
1. **CURRENT**: required to satisfy the adopted acceptance boundary or protect mandatory correctness/security. Remains blocking until genuinely resolved or an authorized plan change.
2. **HYGIENE**: nonblocking maintenance/cleanup with explicit ownership and evidence; never reclassify a real defect as cosmetic.
3. **FUTURE_ENHANCEMENT**: new or expanded functionality outside the adopted scope; propose an explicit future development candidate.
4. **STABILIZATION**: deferred robustness, extended integration/qualification or hard residual work, only when not required for current acceptance. The existing Phase 22 is a specific stabilization owner for transferred Phase 8 residuals, **not** a universal sink for all future findings.
5. **UNDECIDED**: insufficient evidence, conflicting constraints or dependency uncertainty; keep visible and require further decision.

A destination is a *proposal*, not an accepted reassignment. A transferred finding is not resolved, passed or removed from history. Record target owner/phase when selected; do not invent one.

## Minimal execution route

1. Trigger only on meaningful observed stall/regression, repeated repair, scope expansion or an explicit Human request; never invoke Astra for every Step.
2. Deterministically collect the adopted goal/acceptance boundary, typed findings, actual tests/lint/review evidence, attempts and existing planned destinations into a compact, reproducible Context Package. Packaging must not require costly AI. Optional packaging assistance is limited to Local LLM or Luna under a strict budget.
3. Apply clear deterministic rules for required blockers and known nonblocking candidates. For genuinely ambiguous **strategic** cases, request a bounded Astra assessment through the admitted AI integration; allow decline if context is incomplete.
4. Return a typed proposal: per-finding disposition, evidence, dependency impact, why current acceptance is preserved, unresolved questions, recommended next actions, and estimated value of reassignment.
5. Submit the proposal to the existing Human/Admission or external planning authority as appropriate. No automatic acceptance-boundary change or autonomous Phase file rewrite.
6. After authorized adoption, record/reconcile canonical Journal and development-candidate references, preserving identities, provenance, open/closed state and actual evidence. Continue current Phase only against its genuine remaining blockers.

For steps 3–5 reuse the request construction, typed-result handling and
Japanese user presentation qualified by [Phase 24 Q1](phase-24.md#q1-language-verification-within-p24-05).
Write the assessment instructions and disposition rationale in English. Carry
approval quotations and log excerpts verbatim and retain finding IDs and
disposition values. Present the recommendation and any question to the user in
Japanese through the same path.

## Safety and cost constraints

- Required checks, tests, security and Usage Contract behavior cannot be deferred solely to accelerate closure.
- Unverified, failed or UNKNOWN outcomes remain truthful; accepted prior Steps and repair counters are not rewritten or reset.
- Do not create recursive review/assurance work to validate the classifier itself.
- Do not invoke Astra just to label routine Hygiene or restate existing rules.
- Bound context bytes/tokens, invocation count, estimated spend and fallback; record provider/model and usage via existing accounting.
- Strategic assessment may recommend changing direction, Take Over or stopping, but those are proposals governed by existing authorities.

## Delivery slices

| Slice | Work | Connected acceptance |
| --- | --- | --- |
| S1 | Deterministic inventory and classification boundary | Current blockers and nonblocking findings remain attributable, and unknown cases are explicit; no extra AI required. |
| S2 | Bounded Astra strategic proposal | An ambiguous multi-finding fixture produces typed, evidence-linked disposition and dependency assessment, including an explicit refusal/unknown path. |
| S3 | Existing approval and journal/plan handoff | Adopted proposals are recorded once with origin and destination; declined proposals do not change scope or history. |
| S4 | Realistic convergence qualification | Demonstrate reduced unnecessary current-Phase work without weakening acceptance, with cost and invocation accounting. |

## Exclusions

No autonomous planning approval, broad project rescheduling, unconditional Phase 22 transfer, AI-written source patches, universal dependency inference, new Session lifecycle or revalidation of accepted historical outcomes.

## Dependencies and scheduling

Execute **after Phase 9 and Phase 18**. Do not modify the selected Phase 9 -> Phase 18 order or add a Phase 23 gate to either predecessor. Existing Phase 22 stabilization remains separately planned and is not silently superseded.

## Open design questions

- Which typed Journal/Development Candidate owners expose the minimal disposition and adoption operations?
- How to represent cross-finding dependency evidence without a second graph/database?
- Which convergence signals justify the expense of Astra versus a Human decision?
- Whether and how textus-ai exposes the Astra capability and hard no-overage budget policy.

The detailed implementation should inspect current public APIs before allocating code-level changes.

## Hygiene selection during stabilization planning — 2026-11-19

A strategic disposition to HYGIENE retains the finding as **open, nonblocking, attributable work** rather than silently fixing or deleting it. When planning a future Stabilization Phase, collect eligible accumulated Hygiene candidates from the existing canonical Journal, including oversized handwritten source-file warnings originating in Phase 9. Select a **bounded subset** based on maintainability impact, expected reduction in AI context/processing cost, dependencies, change risk, and cost/benefit. Record which findings are selected, their destination/owner and acceptance conditions; keep unselected Hygiene open for later consideration.

The Stabilization Phase must have its **own fixed Effective Acceptance Boundary**. Do not import every Hygiene item automatically, convert a warning into a mandatory source split, or let cleanup work recursively expand closure requirements. File splitting may affect Scala visibility, given/implicit resolution, initialization and contracts; require appropriate validation and normal Admission. Existing mandatory defects remain CURRENT and cannot be reclassified as optional Hygiene.

Lifecycle: discover/record during ordinary development -> assess as nonblocking Hygiene where justified -> select a bounded set when creating stabilization work -> implement and verify through ordinary controls -> update original canonical findings without duplicates. This is a planning/handoff policy, not a second backlog system or an authorization for autonomous Phase creation.
