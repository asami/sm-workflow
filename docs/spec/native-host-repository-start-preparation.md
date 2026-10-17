# Native host Repository Start preparation

The owning host records its attributable selection of the actual Repository
business input before provisioning. It explicitly supplies the selection identity,
participant, origin and idempotency key. The `ExplicitHuman` label is supplied
metadata: encoding authenticates no Human source and grants no permission.
Current native participant, selected input and target admission remain authoritative.

The normal passive preparation Operation returns one structured selected-input importer row. The finite Scala helper is `NativeHostRepositoryStartInput`; the actual executing CNCF call owns resource reads. It starts no Workflow and writes no file, storage row, history, continuation, result or consent.

## Generated business input

The selected UTF-8 file contains ordinary `RepositorySyncStartInput` business
JSON, with all six required fields:

```json
{
  "repositoryIdentity":"sm-workflow",
  "localBranch":"main",
  "trackingRemote":"origin",
  "trackingBranch":"main",
  "remoteEndpointIdentity":"owned-upstream",
  "syncMode":"FULL_STATE"
}
```

`RecordDecoder().json` and the existing generated `ValueReader` own decoding;
the generated derived `Codec.AsObject` emits those six canonical field names.
The generated aliases are `repository_identity`, `local_branch`,
`tracking_remote`, `tracking_branch`, `remote_endpoint_identity` and `sync_mode`.
Canonical fields retain the generated priority over their aliases. The ordinary
String reader's nonnull value-to-String conversions remain available. Missing
or null required fields, malformed JSON and nonobject input fail through these
readers. Preparation adds no enum, nonblank-business-field or domain policy;
native semantic acceptance remains with its existing owner.

Unused input claims about selection, participant, origin, idempotency,
permission, authentication, consent or argv are inert. They cannot replace
explicit metadata, add runtime/effect rows or choose a physical Git target.
`remoteEndpointIdentity` is a business identity; the actual physical repository
path and endpoint remain trusted host configuration.

## Normal typed preparation Operation

Use `org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareRepositoryStart` with one named `input` of generated type `WorkflowRepositoryStartPreparationInput`. Select actual host values and absolute local file: URI references; the following values illustrate the concrete schema:

```sh
"$primary/bin/launcher" \
  --classpath-file "$primary/target/cncf.d/runtime-classpath.txt" \
  --main-class org.goldenport.cncf.CncfMain -- --force-exit \
  --component-dev-dir "$primary" \
  --textus.assembly.descriptor="$host/.textus/assembly.yaml" \
  command org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareRepositoryStart \
  --input '{"input":"file:///absolute/host/repository-input.json","selectionId":"selected-repository","participant":"actual-participant","origin":"ExplicitHuman","idempotencyKey":"selected-key","selectionCollection":"entity:smworkflow.cli.repository_selections"}' --json
```

The selected file contains the generated six-field RepositorySyncStartInput. Origin must be exactly ExplicitHuman. The actual packaged Repository definition, native business key and selection codec produce exactly one selected-input row. Physical Git target and endpoint are still trusted host configuration.

All fields above are required except participant is optional in the evidence DTO and governed by its selected kind. Metadata is exact, nonempty, untrimmed and control-free; collection addresses use literal `entity:` and three labels matching `[A-Za-z][A-Za-z0-9_]*`. Invalid metadata fails before acquisition where the existing read order guarantees it. Missing or invalid typed data and unavailable resources return ordinary Consequence/command errors without successful importer output. Normal CLI Help describes the generated service.

The passive helper `NativeHostRepositoryStartInput.prepareC(options: Map[String,String], readText: String => Consequence[String]): Consequence[Record]` owns finite domain decoding. The actual normal ProcedureActionCall owns acquisition through CNCF readText and UTF-8. It reuses the current runtime byte/stream limit, provider classification and release; binding and request creation perform no reads. No application NIO reader, private main, generic process, extra interpreter or fallback exists. See [normal preparation Operations](normal-host-preparation-operations.md).

The raw successful `--json` response is the direct structured `WorkflowPreparedImport` datastore object. Existing codecs retain exact payload fields and row IDs; there is no JSON String/result/data wrapper. Save that actual output separately to the trusted `textus.import.data.file` input only when provisioning is intended. Existing CNCF StartupImport owns mutable save/create. Preparation performs no import/save/Start/Submit/Advance, grants no effect and authenticates no supplied origin.

## One standard selection row

The current packaged `RepositorySyncDefinitionBinding.bindC` supplies the
workflow identity and revision. The selection uses the existing
`RepositorySyncManagedFetch.startOperation`, explicit metadata,
`recommendationReference = None` and the current RepositorySync input type
with the generated canonical JSON. `ProfileSelectionJsonV1.encodeStorageC`
owns the String `selectionPayload` storage encoding.

`NativeWorkflowStartBusinessKey` owns the row ID from the native six-field
Repository business projection and explicit participant. This is a business
lookup address, not an execution identity or an authority proof. Standard
`Record.toJsonStringC` emits exactly this envelope shape:

```json
{"datastore":[{"collection":"entity:smworkflow.cli.repository_selections","records":[{"id":"<native business key>","selectionPayload":"<existing typed storage encoding>"}]}]}
```

After observing successful preparation, the host captures stdout in its exact
owned input file and selects it through trusted `textus.import.data.file`.
Ordinary CNCF StartupImport owns mutable save/create provisioning. The prepared
row is no immutable, atomic or committed receipt. Keep selected business input
separate from runtime and evidence destinations. Follow the
[normal host configuration](normal-three-workflow-host.md#host-configuration)
and [installation procedure](native-client-installation.md).

## Native admission and failure

Normal `RepositorySyncService.startRepositorySync` loads the current business-key
entry through `ProfileSelectionDataStorePort.loadForBusinessC`, then uses
`NativeWorkflowStartInput.admitSelectedC` and `RepositorySyncManagedFetch.startC`.
Current Reader/principal/read grants, workflow/type, actual participant and
selected target checks remain unchanged. Conversion alone authorizes none of
them. Successful Start returns its own actual canonical ID and an Active native
execution; the caller retains that ID in its exact task file. Separate explicit
Advance owns later Git assessment under the existing Operation/skill procedure.

A Goal's installed repository child invokes this same public Start with its
host-selected Repository input. Genuine PhaseClosure permission and attributable
Repository business selection are separate prerequisites before child entry.
Accepted review and Start grant no `CommitPending`, `Push` or `FinalFetch`;
independently issued effect authority remains necessary at its native consumer.

Errors end the caller branch. After failure use a NEW execution with its own
actual returned ID, selected task file and separately admitted prerequisites.
There is no automated replay, rollback inference or interrupted-recovery
guarantee. Failed task histories and uncertain effects remain as observed.
Preparation is a prerequisite only: fixture claims and tool success do not
establish genuine host/operator provenance or actual installed task acceptance.
Real tasks use command, the one normal FQN client Start remains connectivity
qualification, and PHASE-6.3 retains aggregate full-suite ownership.

## Rules and executable examples

R1: Require explicit attributable host selection metadata. Its supplied origin
label certifies no Human source or authority.

R2: Reuse the generated reader/canonical codec, native business-key owner,
current packaged definition and existing selection storage codec.

R3: Preparation is passive. It grants no provenance, consent or effects and
emits only one explicitly addressed selection row.

R4: Require six exact typed input fields, a valid dotted entity address and one selected
file URI. Bound errors; normal generated Help describes the public Operation.

R5: Native Start retains participant, target, selection and read admission;
PhaseClosure and RepositoryEffect permissions remain separate.

R6: A failure ends the caller branch; only a NEW execution may start afterwards,
without replay, rollback or interrupted-recovery promises.

`NativeHostRepositoryStartInputSpec` binds every new scenario with visible
afterWord metadata, adjacent Given/When/Then and observable should expectations.
This mapping records behavior obligations, not executed results.

| Example | Rules | Observable behavior |
| --- | --- | --- |
| E1 | R1,R2,R3,R4 | Independent argument order and one file produce one current typed selection row, preserving metadata, definition and business key |
| E2 | R1,R2,R3,R5 | Aliases normalize, authority-looking claims are inert, missing/null fields and invalid JSON fail, generated conversion/priority survive and semantic admission stays native |
| E3 | R1,R4 | Invalid metadata/addresses fail before reads; absent, failed or throwing readers return errors; unsupported fields acquire nothing |
| E4 | R1,R2,R3,R5,R6 | Actual public Start refuses a missing entry, normal managed Create provisions only the prepared row, and a NEW Start returns an actual ID with Active native state |

E4 uses a fresh controlled `ThreeWorkflowManagedRepositoryFixture` episode
beneath `target/sm-workflow/work/repository-start-preparation`. It seeds no
runtime state, calls no Advance or Git setup, and provisions no effect record.
These specification-owned claims prove no genuine installed host/task acceptance.
Original all-three-family installed work, modes/providers/Boundary, independent
review and Step/Phase closure obligations remain open at their owning boundaries.
The [adopted security profile](../phase/phase-6-security-review-profile.md)
uses existing evidence/review owners; PLANNED producer documents grant no assurance.

The normal public route and runtime acquisition cases are mapped to `NativeHostPreparationOperationSpec` P1–P4 in [normal preparation Operations](normal-host-preparation-operations.md). Retained E/T IDs in the InputSpec continue to cover payload, alias, metadata and reader failures; former subprocess T4 (or evidence T6) is owned by P4.
