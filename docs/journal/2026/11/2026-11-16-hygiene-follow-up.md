# Documentation hygiene follow-up

## HYG-PLANNING-HOST-TERMINOLOGY-01 — OPEN, nonblocking (P3)

Discovery: 2026-11-16, independent planning-document commit review.
Owner: sm-workflow documentation maintenance.

The Phase index, Phase 8/15 planning additions and the November 12 initialization
journal use `host` for runtime installation, composition or policy. The adopted
`docs/spec/runtime-caller-terminology.md` reserves that word for network
destinations and assigns `Runtime`, `RuntimeRoot` and `RuntimeComposition` to
the other meanings. Neighboring historical prose retains the same terminology.

This is naming/presentation maintenance. The intended owners and planned status
are explicit, and these changes introduce no API or configuration field. It
does not block the planning commit or establish implementation/readiness.

Follow up by normalizing active explanatory prose with the existing
purpose-specific terms, preserving historical quotations and traceability links.
Keep this maintenance separate from host initialization implementation,
workspace provisioning, Session continuation and their actual acceptance.

Source: independent reviewer `/root/pending_planning_commit_review01`; review
disposition PASS, no Current Boundary Blockers. No terminology repair is
performed or accepted by this record.
