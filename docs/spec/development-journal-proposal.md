# Normalized development journal: proposed public contract

Status: design proposal, 2026-10-20. No journal Operation, migration, revised
Phase Plan or Human answer is installed or adopted by this document.

The human direction transferred from the Phase 6.4 development chat is to make
sm-workflow own journal persistence, retrieval and updates, and to normalize
journal information for reuse. The subsequent direct human instructions make
Operation-owned common recording the default, add skill-specific entries only
optionally, and centralize journal management in Operations to reduce AI cost.
Runtime collects the facts it already owns and manages their shared records.
AI supplies only additional semantic context when useful and consumes actual
results. The previously accepted E1 direct-document-edit design is
historical accepted work; a prospective change needs its normal adoption.

## Operation-owned common recording and management

An ordinary business Operation records its own meaningful outcome and the
shared journal references within its managed execution. It returns the actual
business result together with the journal recording outcome and Entry references.
There is no mandatory second AI call to create an entry, choose a filename,
update an index, render Markdown or maintain retention metadata.

The common record uses actual admitted scope, operation identity, observed
outcome, occurrence/recording time and existing result/Evidence references.
Recorded context comes from the current call; do not ask AI to reconstruct facts
already owned by the runtime. A state transition is recorded only if it actually
occurred. Do not journal every low-level helper, read or status poll by default,
and do not recursively journal journal-management Operations.

| Responsibility | Owner and normal route |
| --- | --- |
| Shared factual journal | Relevant business Operation records its actual observations through the common current-UoW journal writer. |
| Skill-specific explanation | Optional typed supplement supplied with that Operation where its contract permits; retained with skill identity and source, linked to the common Entry. |
| Identity, scope links, indexes and history | Runtime owns allocation, durable persistence and revision/conflict handling. A skill supplies no guessed runtime identity or copied Workflow envelope. |
| Search and presentation | Normal journal Operation resolves a bounded selection and returns structured entries, relevant relationships and a derived human-readable view together. |
| Corrections, explicit holds and selected lifecycle actions | Typed journal-management Operation processes a bounded batch and reports actual per-entry outcomes. AI does not edit store files or maintain a second index. |

The optional supplement may add a rationale, design observation or skill-specific
context. It cannot replace the runtime outcome, attribution, consent or Evidence
facts. Omitting it still produces the complete common entry required by the
business Operation. A retained skill-specific entry uses the same managed journal
store and links to the shared Entry; no skill-owned persistence engine is added.

Use existing declared UoW/datastore facilities and their actual guarantees. Being
in one Operation does not make filesystem effects and journal writes one atomic
transaction. After a successful business effect and failed/uncertain journal
write, retain the actual business result and expose that separate journal state.
A later selected journal-only management action must not replay the business
effect. Recording grants must be declared before the Operation is enabled; a
journal feature cannot silently grant writes to an otherwise read-only caller.

The intended AI-cost reduction comes from eliminating routine journal decisions,
separate record/index/render round trips and AI polling for journal maintenance.
Return the common result and bounded journal view in the same response. Runtime
programs handle any required internal sequence; do not issue per-entry AI work.
These are design goals. No reduced token count, latency or invocation count has
been measured yet.

### Common meaning and information richness

Classify journal content by its meaning, not by which skill produced it. Review
findings, rationale, design choices, issues and handoffs may all use shared typed
records even when AI supplies the semantic judgment. Normal admitted semantic
results and genuine Human answers already contain common journal input. Preserve
their actual source and meaning; do not require a second journal-writing AI task
or treat every AI-authored field as a skill-specific supplement.

The [skill usage inventory](development-journal-skill-usage-inventory.md) maps
current recording duties and read-only uses to candidate common responsibilities.
In particular, accepted finding transfer is a concrete common Operation candidate;
its actual Step/Phase eligibility and independent review requirements remain.
Do not change an already issued recording request or Session definition in place.

Record the relevant information owned by the Operation: requested versus actual
selected targets, actual scope, meaningful configuration/policy choices, branch
reasons, per-item/check results, observed timings and errors/uncertainty, and actual
result/artifact/Evidence relationships. Retain available typed detail rather than
reducing it to an AI-written status sentence. Do not invent unavailable fields.
Return a short human-facing view with bounded structured detail and referenced
evidence accessible from the same entry. Full raw logs and payloads retain their
existing evidence storage policy; richness does not require copying them into Git.

Only content that the common semantic model cannot express needs an optional
skill-specific typed extension. The extension uses the same identity, provenance,
storage and management facilities. It is not a skill-owned journal backend.

## Canonical data and ownership

Use a separately declared journal purpose in the existing configured Component
datastore. A structured Journal Entry is the single mutable business authority
for newly adopted managed entries. Markdown is a derived view, not a second
editable store. This does not silently migrate or demote existing Git documents.

Configuration comes from the current ExecutionContext's resolved host snapshot.
The host selects the store/collection and grants. Operation input cannot select
SQL collections, filesystem roots, executable commands or alternative providers.
No application datastore, private Workflow row reader or filesystem interpreter
is introduced. Reuse the current-call Component datastore/UoW boundary; inspect
its actual create, query and conditional-update guarantees before implementation.
An available read port alone is not proof of a suitable write/history contract.

| Record | Normalized meaning |
| --- | --- |
| Journal Entry identity and revision | Ordinary business identity and revision; no hash, consent or acceptance certificate. |
| Kind | A typed record category with its own bounded content, not an arbitrary executable instruction. |
| Scope references | Existing project, Phase, Step and Run identities where genuinely known; preserve absence. A reference grants no lookup into private Workflow state. |
| Attribution and provenance | Actual recording context and source reference; separately label caller-reported authorship. Do not manufacture an authenticated actor or AI provider. |
| Times | Caller-reported occurrence time separately from runtime observation/recording time. |
| Meaning | Summary, rationale and bounded narrative, with category-specific structured fields. |
| Corrections | Prior revision, actual reason and source; retain prior content rather than rewriting its history. |
| Evidence relation | Evidence ID and optional actual producer-result reference. The EvidenceRecord owns host, destination, representation and retention. |

These are logical references and typed fields, not a requirement for a new SQL
table per concept. Do not copy entire Workflow contexts into each entry. A
cross-project or remote reference is not an execution or access grant.

The first connected kind is an Operation-recorded execution-evidence observation.
Skill-specific additions are optional. Design decisions,
issues and handoffs may use the same common record later, with separately defined
typed content and selected callers. Their entire implementation is not added
to the Phase 6.4 E2 acceptance boundary.

## Proposed minimum public surface

Names are working CML names until the prospective contract is adopted. They are
not instructions to invoke an Operation that is not installed.

| Operation | Typed input | Actual result |
| --- | --- | --- |
| Existing evidence/business Operation with common recording | Existing business input; optional typed skill-specific supplement where declared | Actual business outcome plus common journal recording outcome and actual Entry references. No mandatory follow-up journal call. |
| `JournalService.readJournalEntry` | Entry ID and optional specific revision | Found / absent / unavailable; actual structured entry and bounded derived Markdown where selected. |
| `JournalService.findJournalEntries` | Typed scope/kind/time filters and bounded page selection | Actual bounded entries and continuation, or explicit unsupported/unavailable outcome; no SQL or arbitrary path input. |
| `JournalService.manageJournalEntries` | Bounded typed batch of selected journal-only actions: add a supplement, correct an expected revision, or apply a supported explicit lifecycle action | Per-entry recorded / corrected / conflict / failed / indeterminate / unsupported outcomes, actual retained references and bounded combined view. No business-effect replay. |

The runtime uses existing identity facilities and retains the journal relationship
with the actual source result through the normal durable boundary. The skill is
not required to allocate a second ID for common recording. A lost response must
be observed through the same actual result/reference and retained Entry identity;
do not reconstruct an entry by filename or blindly create another one. A proposed
identity is not proof that a row exists. A duplicate with different meaning is a
conflict, not an upsert. Do not rerun tests, archival or uncertain writes blindly,
or add a second retry engine or deduplication by content hash. Inspect the actual
producer's durable result-to-entry relationship before claiming this recovery.

Corrections must retain history and use the actual datastore's conditional write
and transaction guarantees. A concurrent update must not silently replace
another revision. If the configured owner cannot supply that guarantee, expose
the limitation and obtain its separately owned contract; do not implement an
application lock, private SQL transaction or success-shaped placeholder.

The minimum evidence route needs real Operation-owned record/read and
fresh-process retrieval, with no required AI journal-maintenance call.
Query/correction behavior must be selected and validated at its declared delivery
boundary; merely declaring the generic surface does not make it available.
Management is a closed set of typed journal actions, not arbitrary code, SQL,
paths or a second Workflow engine. A batch has finite input/output bounds and
explicit per-entry outcomes; partial completion is not aggregate success. No
automatic deletion, scheduler or new permission is introduced. Evidence retention
remains with its existing owner; journal lifecycle policy is separately explicit.

## Evidence facts, display and failure

EvidenceRecord remains the source for its allocation/storage observations,
configured host, actual admitted location, representation and retention basis.
Journal output may project those fields from an actual lookup, with its
observation time. Current availability is an observation made at lookup time,
not a permanent flag on the journal entry. Record an actual historical storage
result by its source reference separately from a new availability observation.

Keep the following distinct: storage outcome, current availability, test result,
review/acceptance, and journal-write outcome. Missing/expired evidence neither
invalidates past accepted work nor supplies evidence for a fresh claim. A journal
claim cannot authorize Workflow advancement, a Human choice or a remote effect.

After successful archival, a failed journal write leaves archival successful and
the source result/allocated ID intact. Later journal-only recording must not
repeat the test or archive. Evidence expiration does not expire the journal.
Journal retention/migration is an explicit policy, not the evidence's 30-day TTL.

Humans still receive the exact configured host and actual absolute storage
destination required by Phase 6.4. AI can use Entry ID, Evidence ID and a public
context locator without copying the full path as an independent record. Neither
view may guess HOME, use a display-only root, access private CNCF binding fields
or call NIO/shell as a substitute. Evidence bytes stay outside Git.

Markdown is rendered from actual structured records. An explicitly selected
file export is a separate managed effect with an admitted target; the read/render
Operation grants no overwrite of an existing manually authored file. Initial
delivery may return the rendered view without adding file export.

## Existing documents and Phase 6.4 impact

Keep Git-managed source, canonical requirements, human decisions, accepted E1
documents and existing journals. Link a legacy document by repository identity,
revision and path when appropriate; do not scan/import it as if it were an
authoritative structured entry. Migration requires explicit selection, field
mapping, provenance, readback and a clear future editing policy. No automatic
bulk migration or deletion is part of the evidence main route.

| Existing requirement | Prospective change | Preserved acceptance |
| --- | --- | --- |
| E1 / P64-01..03 | Add a prospective journal-ownership addendum after adoption; retain the accepted E1 text/result as historical. | Material classification, evidence identity, exact destination, separate facts and accepted E1 commit/review/Compile. |
| E2 / P64-04..08 | The normal evidence Operation records its common journal from actual results, accepts an optional skill supplement and returns actual Entry references in the same response. | Real one-file allocate/archive/readback, real journal persistence, fresh-process lookup and no evidence payload in Git. |
| E3 / P64-09..12 | Journal persistence failure is reported by that Operation; retry only the confirmed journal action where permitted. | Source preservation, uncertainty and existing evidence observation; no test/archive replay. |
| E4 / P64-13..18 | Adapt AI instructions to use returned common entries and only selected optional supplements/managed journal actions when that Step is issued. | Batch, compression, retention/holds/due queries, truthful absence, independent Phase review, full validation and normal release. |

Do not add all generic journal categories, bulk migration or file export as E2
completion requirements. The minimal proposed E2 change keeps existing owned
paths, dependencies, check identity and all other Step records. The selected
Usage Contract still requires real AI journal recording; the actual human must
adopt the runtime-mediated recording interpretation explicitly. If that requires
changing the captured Usage Contract rather than preserving it, ordinary bounded
revision is ineligible and must stop with that precise limitation.

## Same-Session adoption boundary

The actual frontier is the saved dependency Human question at continuation
`96c294d73be3d48067f3721d8a2abf33:ordered-human:26`. It concerns only
`P64-E2-public-file-location@1`. Its four choices remain ApproveDependencyScope,
RejectDependencyScope, Defer and Cancel. No journal instruction or this proposal
answers it, changes its payload or supplies new CNCF scope consent.

After a genuine answer and the existing normal continuation, returning to E2 may
allow a new decision-needed result with a complete bounded Plan revision. Keep
the dependency question and revision question distinct; those proposals cannot
coexist in one semantic result. Capture and adoption must use the existing owner
and independent Human channel. A private candidate document or JSON is not
adoption or an issued question.

The existing guards retain every accepted Step, base, repair count and history;
preserve ownership/dependencies, all other Steps and the captured Usage Contract.
The selected document bindings and checked flags remain unchanged; only current
unclosed Step acceptance text may change. Validate the exact proposed Plan and
document pair through normal capture before claiming it is admissible.

If the concrete contract needs wider ownership, changed accepted conditions or
changed captured usage, report the unsupported boundary and design an explicit
owner-side amendment. Do not relax guards, silently replace a pending Human,
edit native storage, reset/restart the Phase or move accepted work to a new run.
This proposal adds no generic amendment runtime merely to avoid a real decision.

## Delivery allocation update — 2026-10-20

The later direct user instruction selects development before Phase 6.4's remaining
work and requires the skills to support that Phase's actual sm-workflow trial.
[Phase 11](../phase/phase-11.md) now owns the minimum shared store/writer/read/find,
accepted-finding connection and packaged/deployed skill integration. It uses
existing Operation/review results without depending on unfinished evidence archive.
[Phase 11.1](../phase/phase-11.1.md) owns Decision/Issue/Handoff and correction/batch
extensions. The [design](../design/development-journal.md) defines their common model.

This supersedes the earlier prospective allocation of the shared Journal
implementation itself to E2. Phase 6.4 retains its evidence responsibilities and
consumes the Phase 11 delivery rather than creating another journal backend.
Earlier E2 revision candidates remain historical unadopted proposals, not new
instructions to submit them. Current issued work, pending questions, accepted E1,
captured contracts, repair counts and results are preserved. Integrate at the
actual supported adoption boundary; no stored question or Session is rewritten
by this document. Planning is not implementation or runtime contract adoption.
