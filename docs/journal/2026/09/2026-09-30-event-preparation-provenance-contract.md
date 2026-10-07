# Host EventStore provenance and immutable handoff boundary

Date: 2026-09-30
Status: source gap identified and construction contract fixed; implementation pending

The previous goal turn made concrete progress: focused-341 supplied real managed
resource competition/revocation evidence (223 tests, 26 suites). Re-read the
named cncf-goal-phase SKILL.md and execution contract for this existing active
goal. Retain Phase 2 and its admitted CNCF Phase 88 dependency work, not a new
goal, split goal or successor. The user's existing simplified recovery override
remains in effect; no runner/history gates or new skill/agent edits were added.

Current source inspection finds Subsystem._event_store unconditionally uses
legacy EventStore.inMemory with no fixed persistence policy. Existing closure
specs select from separately configured memory stores. Therefore revision
capture is not proof of selection by the host's configured EventStore.
EventStore.PersistenceSelection's case-class data cannot mint that authority.

Recorded docs/notes/managed-event-preparation-handoff-contract.md to separate
trusted bootstrap/service capture, host-only ordinary-event selection, immutable
cursor/envelope/index/absence evidence and the later native publication gate.
Retained incoming payloads remain exempt from new-envelope encoding; genuinely
new durable facts freeze canonically after classification. No mutable source or
exported Record can become write-validation authority. Storage policy stays
separate from Subsystem storage declarations. Legacy construction stays usable
for legacy memory operation but rejects the new managed preparation route.

Updated Phase 2's checklist with the actual focused-339/341 progress and this
remaining configured-service gap. This turn changed documentation only, ran no
SBT, and made no new validation claim, commit, publication or Phase acceptance.
The existing active goal remains active. S12/P2-U15/P2-U16/P2-U05 remain open;
production assembly, launcher/MCP and application/dispatch are not implemented.

Next: implement explicit trusted EventStore construction/configuration capture
and host-bound preparation with the above executable requirements, then native
write-time observation validation. Do not manufacture an application kind
catalog or bypass CNCF with direct SQLite calls.
