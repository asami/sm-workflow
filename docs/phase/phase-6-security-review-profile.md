# Phase 6 Security Review Profile

Status: adopted plan for the Phase 6 / 6.1 / 6.2 / 6.3 family; implementation acceptance pending
Added: 2026-10-14
Producer dependencies: CNCF Phase 107, CNCF Phase 110 bounded early slice, Cozy Phase 83, textus-cbd-support Phase 16 (laundering early slice included)
Canonical architecture: [Textus Security Architecture](../../../goldenport-cncf/docs/design/textus-security-architecture.md)

## Purpose

Integrate safe-by-construction application-security evidence into the existing
Phase 6 Step / Phase / Final review route. This is not a second security
workflow or an additional mandatory review cycle.

## Additional-check applicability and producer guarantees

This section governs every review placement and acceptance clause below and
their projections into Phases 6/6.1/6.2/6.3. An additional security check is
required only when all three conditions hold:

1. The security contract is explicitly adopted within the current Effective
   Acceptance Boundary, with a concrete threat/required guarantee.
2. The current change affects that contract or its application conditions.
3. Existing applicable producer, integration or review evidence does not cover
   the affected requirement.

Use the existing Slice/requirement/evidence information to select the smallest
missing check. This rule does not introduce a separate applicability ledger,
certificate, per-Step questionnaire, provider query or proof-of-no-change check.
An unaffected or out-of-scope concern needs no additional security execution.
An affected requirement already covered by applicable evidence reuses it.
Neither case is an Admission Gap, a required provider call, or an excuse to
block progression. Not applicable is not a claim of universal security PASS.

CNCF/Cozy own verification of their actual delivered guarantees. Ordinary
application use satisfying the selected contract's declared conditions MUST
reuse those guarantees and MUST NOT repeat their internal vulnerability tests
in each application, Step, Phase or Final. Use established contract/handoff
information; do not repeatedly inspect producer internals or demand fresh
producer certificates. A PLANNED document alone establishes no such guarantee.

Application-specific checks cover only changed exposure/configuration, custom
adapters, explicit escapes or composition that actually affects a guarantee's
conditions and lacks applicable evidence. A changed dependency version by itself
does not require the complete security suite: assess the relevant contract
change or known defect using the ordinary dependency handoff. A demonstrated
violation of an adopted contract still follows ordinary findings/repair; an
unmodeled concern is a scope decision, not automatic additional machinery.

Examples: an editorial document change requires no SQL/XSS/CSRF check; ordinary
typed DataStore use does not repeat CNCF's parameter-binding tests; ordinary
generated rendering does not repeat Cozy's escaping tests. Introducing raw SQL
or changing an exposed Operation may require one affected application-boundary
check when the three conditions hold. Final does not replay those checks merely
because the lifecycle label changed.

Missing evidence blocks only the specifically required, applicable acceptance
condition. An unused security provider or an unfinished unrelated producer Phase
is not a blocker. sm-workflow selects, sequences and reuses evidence; it does
not add runtime security guards, scanners or fallback checks of its own.

## Review placement

### Concrete Phase 6 correction — adopted 2026-10-15

The observed Process-to-filesystem route meets all three applicability conditions:
the managed effect contract is now explicitly adopted, the current implementation
violates it, and prior evidence does not cover the correction. Therefore
[P6-SBX01..05](managed-test-suites-phase-checklist.md#phase-6-managed-effects-correction--2026-10-15)
are required Phase 6 acceptance, not a speculative optional scan. Phase 6 owns
the CNCF110 early producer integration, five file-operation migration, necessary
SBT preparation separation and CBD16's bounded laundering rule connection.

Process/HTTP default-deny and explicit opt-in, CNCF Git/SBT typed DSLs, other
command-name/registered-definition grants and ordinary shell exclusion are
CNCF runtime responsibilities. Ordinary resource reads use the normal resource
DSL with runtime limits; development-file manipulation uses the protected File
Workspace DSL with logical-root/coarse authority as specified in the
[Phase 6 handoff](../journal/2026/10/2026-10-15-phase-6-file-workspace-dsl-handoff.md).
There is no application-selected safety-variant read API or per-file ACL matrix. Process
registration cannot authorize otherwise denied filesystem/network/datastore
effects. Reuse the shared External Effect vocabulary in CBD instead of building
another classifier/guard inside sm-workflow. Typed operations and registered
programs do not prove hostile-code containment or all effects of arbitrary builds.

The main route must include this correction before acceptance. Full hierarchical
SandboxContext, CAR Loading Admission breadth, server propagation and Docker
handoff remain later work. Successors consume the actual delivered guarantees
under their conditions, adding only checks for uncovered changes. No additional
per-Step security cycle, evidence certificate or blanket suite is introduced.

Detailed rationale and upstream versions:
[integration journal](../journal/2026/10/2026-10-15-phase-6-managed-effects-and-sbt-plan.md).

### Existing Step / Phase / Final route

- **Step end**: only when the three applicability conditions above hold, run
  the smallest missing deterministic check within the existing Step route.
  Step completion itself does not trigger a security scan.
- **Phase review**: consume CBD Review for cross-cutting authorization,
  exposure, escape-hatch justification and security architecture fit when the
  three applicability conditions hold for the affected composition. Reuse
  applicable evidence when it already covers those concerns.
- **Final/closure**: require only release-relevant security evidence not already
  current and reusable; do not replay unchanged checks.

Security evidence follows the same staleness, scope, admission and reuse rules
as other review evidence.

## Convergence rule

A security finding inside the Effective Acceptance Boundary can trigger normal
Repair/ReReview or provider escalation. A speculative hardening proposal outside
the boundary cannot expand the current Goal automatically.

Security review must not create management hashes, duplicate verification,
whole-tree copies, broad rollback machinery or extra review loops merely to
prove that security review happened.

## Acceptance

The Phase 6 family can select a genuinely missing security check under the
three applicability conditions, request the corresponding provider evidence,
and reuse applicable evidence and delivered producer guarantees. Unaffected and
already-covered cases proceed without additional security execution or provider
wait. A concrete in-boundary blocker uses the existing Admission Gap/repair
route. Demonstrate this bounded selection/reuse behavior within the existing
connected acceptance scenarios, not a new security test matrix or per-task ritual.

## Split ownership — adopted 2026-10-14

The upstream reference to "Phase 6" denotes the complete pre-split family.
Apply this profile through the existing connected driver, in the order below.
The [shared checklist](managed-test-suites-phase-checklist.md#security-review-projection--adopted-2026-10-14)
records the additional criteria against existing delivery owners and IDs.

| Owner | Security projection | Existing acceptance boundary |
| --- | --- | --- |
| [6](phase-6.md) | Select applicable requirements; establish the admitted installed command/skill main route including the CNCF110/CBD16 correction. | Existing mode/context/result/provider/Boundary contract plus P6-SBX01..05; no private security engine or complete scanner matrix. |
| [6.1](phase-6.1.md) | Request only missing applicable CAR lint/scoped review evidence through the real provider connection; reuse covered guarantees without additional execution. | P6-SQ01; no additional Step-review cycle. |
| [6.2](phase-6.2.md) | Route an in-boundary security finding through ordinary FIX, recording and dependency decisions; preserve identity, authority and actual unresolved results. | P6-TS02/03, P6-CB01..04 and the existing recording contract. |
| [6.3](phase-6.3.md) | Assess only uncovered affected cross-Step security meaning; perform affected repair/re-review and reuse applicable evidence and producer guarantees at Final. | P6-SQ02..05; repository-full validation remains owned here. |

Security is a concern carried by existing requirements, evidence and findings,
not a new generic lifecycle enum. For a changed public surface, declared input,
DataStore use, output rendering or explicit escape, apply the three conditions
before selecting additional evidence. These categories alone do not trigger a
check. A scan with no findings alone does not establish
absence of SQL injection, XSS or CSRF. Missing required evidence is an explicit
Admission Gap/Unknown, not PASS. An out-of-boundary concern may be recorded for
adoption without silently enlarging the current task.

## Producer handoff and bounded dependencies

- [CNCF 107](../../../goldenport-cncf/docs/phase/phase-107.md) owns safe runtime
  primitives, authorization/input boundaries and escape metadata.
- [CNCF 110 early slice](https://github.com/asami/goldenport-cncf/blob/549ca76cce3784c8c1f3f0d995b9524c42b02dea/docs/phase/phase-110.md)
  owns the minimum shared External Effect classification and typed Git/SBT,
  process/HTTP policy needed by Phase 6. Consume minimum managed-resource gaps
  in the same producer scope; leave the rest of Phase 110 open.
- [Cozy 83](../../../cozy/docs/phase/phase-83.md) consumes that ABI for safe
  generated surfaces and metadata.
- [CBD Support 16](../../../textus-cbd-support/docs/phase/phase-16.md) owns
  deterministic rules and cross-cutting security interpretation. Its
  [2026-10-15 early slice](https://github.com/asami/textus-cbd-support/blob/8c5b8c4a121b17453c6684a60ec0534e22ca172f/docs/phase/phase-16.md)
  supplies the bounded laundering rule for this correction.

The earlier plans are available in local working trees; the latest CNCF110/CBD16
early-slice plans were inspected from fetched origin/main without merging their
working trees. Their declared status is PLANNED. Fetching
or integrating the documents is not implementation, artifact availability or
producer acceptance. They extend the security content consumed by the existing
CNCF106/Cozy81/CBD15 staged-review route rather than replacing that route.
Require only a concrete handoff selected under the three applicability
conditions; reuse established handoffs without a new provider invocation. Continue
independent work when a provider capability is missing; the affected acceptance
must remain open until actual evidence is available. Do not implement substitute
security rules in sm-workflow or make whole producer roadmaps a prerequisite
for the initial Phase 6 main route.

Product changes in another repository follow the owning Phase's currently
adopted integration authority. This document import does not start or close any
producer Phase. The additional DataStore ABI release-classification work in
CBD16 is relevant only when the selected persistent change brings that contract
into the Effective Acceptance Boundary; it does not add deployment/migration
work to this family automatically.

## Application to work already in progress

At the next planning/dispatch boundary, incorporate these criteria into the
remaining owned work. Preserve issued-work context and actual returned results,
Goal/history, finding IDs, repair counts and accepted evidence. Assess whether
the changed requirements affect earlier evidence; fill the specific gap rather
than restart the Phase or assume every old result remains sufficient.

Keep the adopted command-based operational route and lightweight client
connection scope. Keep PHASE-6.3 as the sole aggregate full-suite owner for
6/6.1/6.2/6.3; Phase 6.4 remains separate evidence-storage work. This adoption
records no validation PASS, review acceptance or Phase completion.
