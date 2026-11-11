# Journal single-entry route and later batch expansion

Date: 2026-11-11 (Asia/Tokyo)
Status: adopted planning split; implementation and acceptance remain pending

The user agreed to split Phase 11.1 rather than bring all Journal management
forward before operational improvements. Keep the useful single-entry route
in [11.1](../../../phase/phase-11.1.md); move its batch and additional-consumer
work to the separate [11.2](../../../phase/phase-11.2.md).

## Allocation

| Earlier item | Phase 11.1 | Phase 11.2 |
| --- | --- | --- |
| P111-01/02 | Minimal Decision/Issue/Handoff types and linked records | Reuse, not another implementation |
| P111-03 | Single-entry correction/status change with expected revision, reasons and conflict handling | Preserve those rules per batch entry |
| P111-04 | Necessary bounded query and selected detail | P112-01/02/03: finite batch, individual outcomes and partial-result continuation |
| P111-05 | One real selected consumer route | P112-04: selected additional skills/consumers |
| P111-06 | Single-route connected verification, review and usable delivery | P112-05 owns affected batch/additional-route acceptance |

Keep all P111 IDs and unchecked statuses. A transferred requirement is not a
completed requirement. Do not make 11.1 wait for 11.2, full skill migration or a
general management system. Demonstrate one real record -> linked read ->
correction/status change -> fresh-process read route in 11.1, using the existing
Journal store/writer/public operations. Preserve revision/conflict safety and
required independent review; narrowing scope does not remove those obligations.

## Scheduling and ownership

The selected default priority is **7 -> 11.1 -> 8 -> 8.1 -> 9 -> 18**.
Phase 7 remains first. Phase 8 consumes the useful Journal route for operational
work; Phase 9 may use the records in Takeover context. Removing unnecessary
Human decisions remains Phase 8's responsibility, and model switching remains
Phase 9's. Journal records do not generate consent or workflow progression.

Phase 11.2 has no fixed slot in that sequence; select it from actual use after
11.1. It is not a prerequisite for 8, 9 or 18. Keep the earlier conditional 7/8
overlap only for selected work independent of unfinished 7 and 11.1 delivery,
under the already recorded half-day assessment including validation/review.
No such estimate is asserted here. Phase 18 still follows 9 and its Flutter
connection still requires qualified CNCF Phase 117 delivery.

This changes planning documents only. Accepted Phases11/12, existing source
work, Goals, Sessions, decisions, UNKNOWN/failures, repair counts and verification
remain. It starts no Phase, changes no live Plan/adoption and adds no Phase 6.11
closure condition or runtime repair. No tests, publication, commit or push are
performed by this planning task.
