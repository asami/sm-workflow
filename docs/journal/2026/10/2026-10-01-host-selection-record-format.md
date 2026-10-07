# Host selection record format and managed-access investigation

Date: 2026-10-01
Status: codec and focused validation passed; managed storage integration open

Previous turn: progress, main configured Factory composition and 71 passing
tests in ten suites (attempt 489). Continue the same active Phase.

Current inspected contracts: WorkflowComponentStorage supplies typed instance,
issued-work and completion bindings; it does not expose general host record
access. Subsystem's managed component-store resolution and lease callback are
private[cncf]. Ordinary Action execution already holds a managed lease, but
ComponentLogic constructs its runtime from the parent DataStoreSpace, and a
generic DataStoreSpace lookup alone is not evidence that a host authority
collection uses an admitted managed provider. RepositorySupport is only a
context/search convenience. CandidateAdmissionEvidencePersistence.LocalJson
is a separate immutable file store, not the requested shared physical provider.
Do not use any of these facts to advertise production host authority adapters.

The next storage integration must retain one managed physical provider, exact
owner/collection grants, call-scoped leases and explicit trusted write authority.
No new CNCF public API, host record grant or authority-install Operation is
implemented or selected by this investigation. The final admitted host storage
seam remains open and needs contract resolution before activation.

Freeze this edit to ProfileSelectionJsonV1 and its executable spec. The pure
versioned JSON codec preserves every field of the existing Selection model,
including origin, exact operation/workflow/version, participant, idempotency
key and optional recommendation. It accepts nonhuman origins as recorded facts;
it never changes them to ExplicitHuman or grants Start authority. The existing
ProfileStartAdmission gate still rejects nonhuman records. Wrong schema, field
sets, typed values, unknown origin and incomplete records fail closed.
This is persistence-format preparation, not a storage writer or public input.
There is no physical path, provider, pool or authority-install method.

Validate actual packageBin and the existing ten-suite accumulator plus the new
codec suite. Durable storage, trusted installation, input-to-selection scope,
association/authorization/decision records and CLI activation remain unfinished.

Attempt 490 (SMWF-HOST-SELECTION-CODEC-DIRECT-20261001-490) passed actual
Compile / packageBin and all 76 tests in eleven completed suites. SBT/wrapper
exits are 0, the shared lock is released, no aborted/failed/canceled/ignored/
pending cases. The complete summary was read at
`/tmp/skill.cncf.d/cncf-sbt-609de502e9b65c5a873766acc4347735ce1804e7be8fddc41713bb88dc5ed05f-dc52e7d0efccf79143dd8da99d40f3a2/67514-20261001T021235Z.summary.json`.
Five new Given/When/Then scenarios cover all five origins with and without a
recommendation, unchanged Start admission of decoded facts, absent/extra/schema
fields, malformed typed values and incomplete records. This is not durable
SQLite storage, trusted host installation, authenticated provenance or a new
human authorization Operation. Existing unchecked production items remain open.
