# Native host Split Start preparation

The trusted owning host selects the actual external Split Start allocation
reference, its current allocation row, the selected current Source row and the
component instance from normal component selection/assembly. Initial Start
consent precedes creation of a Workflow execution. Building it requires no
invented Workflow instance, handle, session or internal Workflow-store lookup.
The host separately obtains genuinely attributable consent from the responsible
operator and supplies its evidence reference, operator participant, trusted
issuer and historical occurrence. A saved row or issuer flag authenticates none
of that provenance.

Initial Start consent is distinct from the answer to an issued Scope/Reasoning
Human, independent review, Step/Phase closing permission and RepositoryEffect.
The fifteen ordinary Operations, skills and business parameters are unchanged.
Real task work continues through `command`; the one ordinary FQN client Start
smoke establishes connectivity only.

## Public passive preparation

`org.simplemodeling.textus.smworkflow.host.NativeHostStartPreparation.splitC`
accepts these explicit arguments and returns `Consequence[Prepared]`, where
`Prepared` contains `recordIdentity: String` and `record: Record`:

| Argument | Required selected input |
| --- | --- |
| `componentInstance: String` | Actual current owning component's canonical instance key |
| `allocationReference: WorkflowArtifactReference` | Actual external allocation address with `identity` and `revision` |
| `allocationRow: Record` | String `splitPhaseTypedStartPayload` in current `sm-workflow.split-phase-typed-start-allocation.v3` format |
| `sourceRow: Record` | String `splitPhasePlanningInputPayload` in current `sm-workflow.split-phase-planning-input.v1` Source format |
| `reference: WorkflowArtifactReference` | Explicit consent evidence identity and revision |
| `participant: String` | Actual responsible operator metadata |
| `issuer: String` | Explicit trusted host issuer metadata |
| `occurredAt: Instant` | Explicit historical consent occurrence |

The existing allocation decoder consumes the supplied external address and
current business input, allocation participant, Source reference, optional
proposal reference and planning-context reference. The existing Source decoder
consumes exactly that selected Source address. Preparation does not load a
proposal body, resolve a configuration or manufacture a retained Start record.
Current source/input/proposal relationships and native participant admission
remain at the actual Start boundary. Selected references are no body certificate.

The public producer and native Start port share this exact current projection:

```json
{"componentInstance":"<actual owner>","startAttestation":{"participantIdentity":"<allocation participant>","input":{"sourcePhaseIdentity":"<business phase>","invocationMode":"<business mode>","proposalReference":"<selected reference or null>"},"source":{"reference":{"identity":"<selected source>","revision":"<selected revision>"},"phase":"<source phase>","authority":{"identity":"<authority>","revision":"<authority revision>"}},"proposal":{"identity":"<selected proposal>","revision":"<selected revision>"}}}
```

Absent input proposal reference and absent proposal target are JSON null. There
is no Start allocation reference or Workflow instance in this consent binding;
the allocation reference separately determines the existing lookup key through
`ThreeWorkflowHostEvidencePorts.key(identity, revision)`. No session,
configuration, planning context, command, grant or proposal body is copied.

The existing v7 Evidence codec selects that binding with purpose
`split-phase-start`. It preserves explicit evidence reference/revision,
issuer, operator participant and occurrence. The returned record contains only
`nativeHostEvidencePayload`; its ID uses the existing purpose/lookup identity.
Required supplied identities and metadata must be nonblank exact strings with
no control characters, supplied rows and time must be nonnull, and consumed
payload fields must be Strings. Unused row/evidence annotations are inert.
No new equality between metadata operator and selected allocation participant
is imposed: current SplitStart observation preserves metadata while native
principal admission separately checks the actual allocation participant.

Preparation reads no DataStore, writes no file or row, selects no clock,
authenticates no operator, grants no permission and invokes no prepare/admit,
Start or Advance operation.

## Passive pre-Start planning template

The allocation's existing `planningContext` selects a passive template in the
declared partition-context collection. The current
`sm-workflow.split-phase-partition-context.v1` payload has this consumed
projection, using the existing storage field, prefix and UTF-8 byte limit:

```text
reference: {identity, revision}
owner: {requirement, presentation}
facts: {proposal, boundary, partition, planning}
```

`TemplateOwner` carries only the typed execution requirement and minimal
presentation; `Template` carries that owner, selected reference and complete
typed facts. Template decoding checks the current schema, selected reference
and consumed types. Inert root/owner annotations, including malformed unused
execution metadata, do not gate template use. It does not decode a full retained
owner first, derive missing owner values or perform runtime semantic admission.
There is no compatibility fallback, new format, schema version or public
Operation.

Only after actual native Start does the owning boundary construct the retained
full context's handle, current instance revision, actual configuration, retained
Start reference, continuation identity and completion operation. It carries
only requirement, presentation and facts from the template. The normal full
retained context decoder still requires its complete execution owner, and
subsequent preparation retains current-instance/current-facts admission.
Template data supplies no native history, consent, principal admission, effect
authority or issued work. Separate current Start consent and native participant
checks remain required; current numbering policy still owns occupied-phase and
collision decisions.

## Normal typed preparation Operation

Use `org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareSplitStart` with one named `input` of generated type `WorkflowSplitStartPreparationInput`. Select actual host values and absolute local file: URI references; the following values illustrate the concrete schema:

```sh
"$primary/bin/launcher" \
  --classpath-file "$primary/target/cncf.d/runtime-classpath.txt" \
  --main-class org.goldenport.cncf.CncfMain -- --force-exit \
  --component-dev-dir "$primary" \
  --textus.assembly.descriptor="$host/.textus/assembly.yaml" \
  command org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareSplitStart \
  --input '{"componentInstance":"actual-component-instance","allocationReference":"file:///absolute/host/allocation-reference.json","allocation":"file:///absolute/host/allocation.json","source":"file:///absolute/host/source.json","reference":"file:///absolute/host/consent-reference.json","participant":"actual-operator","issuer":"configured-host","occurredAt":"2026-10-14T00:00:00Z","collection":"entity:smworkflow.cli.host_evidence"}' --json
```

Read the four selected UTF-8 records in order: allocation reference, current allocation, current Source and consent reference. Reference files contain WorkflowArtifactReference JSON. Call the unchanged NativeHostStartPreparation.splitC; PREVIEW/APPLY, optional proposal, historical time, exact owner/scope and native row identity remain unchanged. Generated reference String conversions, including Boolean-to-String, remain available.

All fields above are required except participant is optional in the evidence DTO and governed by its selected kind. Metadata is exact, nonempty, untrimmed and control-free; collection addresses use literal `entity:` and three labels matching `[A-Za-z][A-Za-z0-9_]*`. Invalid metadata fails before acquisition where the existing read order guarantees it. Missing or invalid typed data and unavailable resources return ordinary Consequence/command errors without successful importer output. Normal CLI Help describes the generated service.

The passive helper `NativeHostSplitStartInput.prepareC(options: Map[String,String], readText: String => Consequence[String]): Consequence[Record]` owns finite domain decoding. The actual normal ProcedureActionCall owns acquisition through CNCF readText and UTF-8. It reuses the current runtime byte/stream limit, provider classification and release; binding and request creation perform no reads. No application NIO reader, private main, generic process, extra interpreter or fallback exists. See [normal preparation Operations](normal-host-preparation-operations.md).

The raw successful `--json` response is the direct structured `WorkflowPreparedImport` datastore object. Existing codecs retain exact payload fields and row IDs; there is no JSON String/result/data wrapper. Save that actual output separately to the trusted `textus.import.data.file` input only when provisioning is intended. Existing CNCF StartupImport owns mutable save/create. Preparation performs no import/save/Start/Submit/Advance, grants no effect and authenticates no supplied origin.

## Trusted provisioning and native use

The independently responsible host saves successful stdout to its trusted
configuration-selected `textus.import.data.file`. Existing CNCF StartupImport
owns mutable save/create provisioning on normal startup. For in-process owning
hosts, use the existing managed save/create path with its current grants.
Neither the pure API nor tool provisions a row, and the returned input is no
committed, immutable or atomic receipt. Keep consent input separate from
Workflow instances, continuations and events; never seed execution history.

The current NativeHostEvidence Reader retains trusted current issuer,
purpose/lookup/owner/selected target/nonfuture occurrence, actual principal and
managed read permissions. Existing SplitPhaseTypedStartHumanVerification owns
consent admission. Authentication, consent and effect authority remain separate.
Preparation alone leaves Start without consent. After explicit trusted
provisioning, a NEW normal Start may return its actual session ID; only a
separate Advance issues work. Normal Submit acknowledges and the caller
separately Advances. Nothing here supplies Apply, Git, check, review or closure
authority.

## Rules and executable examples

R1: Consume actual host-selected allocation reference/current allocation row,
current selected Source row and component owner. Never fabricate a Workflow
instance or look up consent scope from its control store.

R2: Reuse the exact shared current SplitStart scope and existing allocation and
Source codecs. Field selection is no body certificate or semantic Start admission.

R3: Require explicit evidence reference/operator/issuer/occurrence. These are
passive metadata; encoding proves no operator origin or consent. Preserve current
native metadata correlation semantics.

R4: Only existing trusted managed provisioning and current Reader/principal/read
permissions/consent may admit Start. Preparation grants no other purpose or
effect and performs no automatic Start or Advance.

R5: The normal typed passive Operation reads explicitly selected resource references and metadata and emits
only standard importer JSON. It supplies no identity, clock, permission or storage
default. Failure has no successful JSON.

R6: Retain command-primary/light-client scope and existing review/history/repair
boundaries. Select changed-surface security evidence and provider needs through
existing owners; required missing evidence remains open.

R7: Consume only the selected current pre-Start template's
`reference`, `owner.requirement`, `owner.presentation` and `facts`. Keep it
passive and require the actual native Start to construct every execution owner
value. Preserve full retained decoding, consent, participant, current facts and
numbering ownership; annotations supply no authority or compatibility path.

`NativeHostStartPreparationSpec`, `NativeHostSplitStartInputSpec` and
`SplitPhaseExplicitPartitionContextSpec`/`ThreeWorkflowDirectOperationSpec` bind these obligations with canonical
spec/example/rule metadata, adjacent Given/When/Then and should assertions.
Rows, clock, operator metadata and finite processes are specification-owned.
They establish no genuine independently attributable task or consent provenance.

| Example | Rules | Observable contract |
| --- | --- | --- |
| E1 | R1,R2,R3 | PREVIEW/APPLY and present/absent proposal targets equal the unchanged native binding; exact lookup and metadata survive |
| E2 | R1,R2,R3,R6 | Malformed current payloads/types/addresses/null metadata fail; annotations are inert; semantic Start relationships retain their native owner |
| E3 | R3,R4,R6 | Current observer rejects foreign owner/authority/target participant/mode/proposal/lookup/issuer/purpose/future time; valid metadata operator may differ |
| E4 | R1,R4,R6 | Preparation remains physically absent; explicit managed Create allows matching current Reader access, while permission/target failures remain failures |
| E5 | R1,R2,R3,R4,R6 | Production Start refuses absent consent; separate public preparation/Create then a NEW Start returns a real session; separate Advance issues Request, retaining positive interleaved/fresh/Human/PREVIEW/APPLY/Terminal regressions |
| E6 | R7 | `SplitPhaseExplicitPartitionContextSpec` round-trips the minimal passive template and ignores inert/malformed unused execution annotations while full retained decoding refuses its incomplete owner. Existing public E5 in `ThreeWorkflowDirectOperationSpec` provisions that ownerless template, refuses missing consent, then separately prepares/Creates consent, invokes a NEW native Start with its actual first history entry and separately Advances; issued canonical instance equals the returned Start session |
| E7 | R7 | `SplitPhaseExplicitPartitionContextSpec` refuses missing/mistyped consumed requirement/presentation/facts/reference, foreign selected reference/schema and excess UTF-8 bytes, while accepting the exact current byte boundary |
| T1 | R1,R2,R3,R5 | Four saved current files produce exactly one standard importer row equal to public preparation |
| T2 | R1,R3,R5,R6 | Invalid flags/files/current schemas/source address/null required reference/time/collection fail without successful JSON |
| T3 | R2,R4,R5,R6 | Only four injected file reads occur; exact dotted address and v7 target retain explicit metadata, discard inert annotations and preserve generated reference conversion |
| T4 | R1,R3,R5,R6 | Actual finite compiled launcher emits parseable rows/exit zero; invalid time exits two/no success JSON; help succeeds, with 30-second limit and owned descendant/stream cleanup | Normal CncfMain command cases in NativeHostPreparationOperationSpec P4 preserve selected payloads and refuse invalid metadata |

## Changed Slice security evidence and remaining work

The adopted [Phase 6 security profile](../phase/phase-6-security-review-profile.md)
selects declared input/selected target, separate authentication/consent/effect
authority, least-authority DataStore provisioning and explicit file/process
escape attribution for this Slice's existing Effective Acceptance Boundary.
E1/E2/T1/T2/T3 and retained native scope/typed Start examples cover current
selection mechanics; E3/E4/E5 cover current consent and managed read/provisioning;
T3/T4 cover explicit file reads and finite process qualification. Actual host
allocation/Source provenance and genuine operator attribution remain required.
E6/E7 codec examples are data decoding evidence only. E6's public Operation
scenario uses specification-owned rows and actual native transitions; neither
part establishes installed user acceptance, independently attributable consent
or actual provider work. Those remain with the existing installed task and
provider acceptance boundaries.

Relevant deterministic CAR lint and scoped independent semantic/CBD review
evidence remain with the existing CNCF106/Cozy81/CBD15 connection, supplemented
by CNCF107/Cozy83/CBD16 security content. Phase 6.1/P6-SQ01 owns that actual Step
Evidence/Admission provider route; existing Step review and Phase 6.3/P6-SQ02..05
own scoped review, cross-Step assessment and Final reuse. Phase 6.2's ordinary
FIX/recording/dependency route owns in-boundary findings. No extra review stage,
scanner, provider substitute rule or management hash is introduced. Imported
PLANNED documents establish neither provider availability nor security PASS.
Concrete required missing evidence remains Admission Gap/Unknown while
independent work continues. These examples imply no SQL/XSS/CSRF absence claim.

RepositoryEffect preparation/provisioning is separate mandatory D2E work.
Genuine installed command task loops for all three families, typed modes,
providers, differing canonical-ID consumption, Human/Wait/Terminal/errors,
Effective Acceptance Boundary, the bounded Goal, independent review and complete
Step/Phase closure remain open. Primary aggregate full-suite validation remains
owned by PHASE-6.3. No fixture or tool example completes those obligations.

See [native applicability](native-host-split-start-applicability.md),
[normal host](normal-three-workflow-host.md),
[installation](native-client-installation.md),
[Human preparation](native-host-human-preparation.md) and
[closing authority preparation](native-host-authority-preparation.md).

The normal public route and runtime acquisition cases are mapped to `NativeHostPreparationOperationSpec` P1–P4 in [normal preparation Operations](normal-host-preparation-operations.md). Retained E/T IDs in the InputSpec continue to cover payload, alias, metadata and reader failures; former subprocess T4 (or evidence T6) is owned by P4.
