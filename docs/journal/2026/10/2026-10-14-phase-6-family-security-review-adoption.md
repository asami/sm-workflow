# Phase 6 family security review adoption

Date: 2026-10-14 (Asia/Tokyo)
Status: adopted development plan; implementation/review acceptance remains open
Owner: sm-workflow

## Decision

The user requested reflection in Phases 6/6.1/6.2/6.3 and local import of CNCF
107, Cozy 83 and CBD Support 16. The GitHub security projection originally used
the pre-split "Phase 6" name. The local split and its current operational
directions are retained; the projection now names each delivery owner.

The [security review profile](../../../phase/phase-6-security-review-profile.md)
and [development governance](../../../notes/textus-security-development-governance.md)
consume upstream sm-workflow commit `2d9ad9e32096665d245d95abd059822c6a8af846`.
The profile is adapted to the split; governance retains the upstream wording.
This is a working-tree documentation integration, not a merge of the sm-workflow
branch or a checkpoint/acceptance commit of its ongoing implementation.

## Delivery ownership

- Phase 6: applicable Slice requirements and evidence/provider needs in the
  existing minimum Effective Acceptance Boundary; establish the installed
  skill/command main route first and retain lightweight client qualification.
- Phase 6.1: focused deterministic security evidence and scoped review through
  P6-SQ01's Step Evidence/Admission route.
- Phase 6.2: ordinary FIX, finding recording and dependency decisions through
  P6-TS02/03, P6-CB01..04 and the existing recording contract.
- Phase 6.3: cross-Step security interpretation, affected repair/re-review and
  Final evidence reuse through P6-SQ02..05.

The shared checklist adds acceptance detail against existing owners/IDs.
No extra review cycle, management hash or duplicate full validation is added.
PHASE-6.3 keeps aggregate validation for 6/6.1/6.2/6.3; Phase 6.4 stays separate.
Ongoing results, issued-work context, history and counts remain; only evidence
affected by new requirements needs reassessment. No implementation success,
test PASS or Phase closure follows from this planning change.

## Upstream plans available locally

The existing main branches fast-forwarded to these fetched GitHub commits;
incoming changes were documentation only and did not overlap dirty local paths.
The same histories also bring their adjacent architecture/planning documents.

| Repository | Local imported tip | Selected plan |
| --- | --- | --- |
| goldenport-cncf | `cc6070c516d194c5531924e676d38e1ad347a68d` | Phase 107 and canonical Textus security architecture |
| cozy | `684ce896d44a04dde1470863e01282156cf48cb6` | Phase 83 and Cozy security responsibility |
| textus-cbd-support | `03db030de5ea369d8dac50e3da64fa8e12ce371b` | Phase 16 and security review responsibility |

These producer plans declare PLANNED status. Import is not producer execution,
artifact publication or acceptance. Their security content supplements the
existing CNCF106/Cozy81/CBD15 staged-review route. Require concrete handoffs for
the selected Slice; an absent required capability leaves that acceptance open
while independent work continues. Product changes follow separately admitted
owner scope. No producer Phase was started and no remote push was performed.
