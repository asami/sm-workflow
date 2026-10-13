# Phase 6 Security Review Profile

Status: PLANNED projection into Phase 6
Added: 2026-10-14
Producer dependencies: CNCF Phase 107, Cozy Phase 83, textus-cbd-support Phase 16
Canonical architecture: goldenport-cncf/docs/design/textus-security-architecture.md

## Purpose

Integrate safe-by-construction application-security evidence into the existing
Phase 6 Step / Phase / Final review route. This is not a second security
workflow or an additional mandatory review cycle.

## Review placement

- **Step end**: run focused deterministic checks relevant to changed public
  surface, DataStore use, rendering/input binding or explicit escape hatches.
- **Phase review**: consume CBD Review for cross-cutting authorization,
  exposure, escape-hatch justification and security architecture fit when the
  Effective Acceptance Boundary includes those concerns.
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

Phase 6 can identify the security evidence required for a changed Slice, request
the appropriate CAR lint/CBD Review provider evidence, preserve accepted current
evidence across later unchanged steps, and surface a blocking security finding
through the existing Admission Gap/repair route.
