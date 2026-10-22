# Normal native workflow preparation Operations

Terminology revision 2026-10-23: the target service name is
`NativeWorkflowPreparationService`, with `--runtime-root` for its configuration
directory. Caller execution conversion uses Caller names; Human/review/authority
preparation uses its concrete purpose. The [rename contract](runtime-caller-terminology.md)
supersedes the old names in the current implementation examples below. These
examples are migration inputs, not compatibility promises. Regenerate and update
them with the connected implementation under the [adoption plan](../notes/runtime-caller-terminology-adoption-plan.md).

The normal configured SmWorkflow bundle binds eight passive COMMAND Operations in NativeHostPreparationService alongside the three session workflow services: six import preparations and two host-execution conversions. Each consumes one named input of a distinct generated value type. Preparation requires the normal host context; execution conversion also consumes the actual issued context and explicitly retained host artifacts. The existing semantic MCP publication set remains unchanged; these Operations issue no WorkOrder or workflow stage.

Before boot, trusted host configuration must explicitly select the actual input read-only roots with `textus.resource.url.file.roots`, a CSV of absolute paths. Without configured roots, file reads are denied; paths outside the selected roots remain denied. `textus.resource.read.max-bytes` governs the existing bounded reader. For example, select a narrow input directory and the default-sized byte cap in host configuration:

```json
{
  "textus.resource.url.file.roots": "/absolute/host/inputs",
  "textus.resource.read.max-bytes": "1048576"
}
```

The operator chooses the actual paths. Supplying URI metadata does not grant read access.

## R1 — Concrete public input and route

Every route starts with `org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.`. Each Operation accepts exactly `--input <one concrete JSON object>` through the ordinary CncfMain command launcher, as shown below. File-designated Strings are explicit absolute local file: URI references. No cwd resolution, generic argv, Map, main selector, command selector, callback or authority port is public.

| Operation | Generated input type | Selected file fields |
|---|---|---|
| `prepareGoalStart` | `WorkflowGoalStartPreparationInput` | `input`, `plan` |
| `prepareRepositoryStart` | `WorkflowRepositoryStartPreparationInput` | `input` |
| `prepareSplitStart` | `WorkflowSplitStartPreparationInput` | `allocationReference`, `allocation`, `source`, `reference` |
| `prepareRepositoryEffect` | `WorkflowRepositoryEffectPreparationInput` | `selection`, `target`, `reference` |
| `prepareAuthority` | `WorkflowAuthorityPreparationInput` | `request`, `reference` |
| `prepareEvidence` | `WorkflowEvidencePreparationInput` | `request`, `result`, `reference`, `executionEvidence` |
| `prepareExecution` | `WorkflowHostExecutionPrepareInput` | `mapping`, `origin`; actual `issuedWorkContext` is inline |
| `recordExecution` | `WorkflowHostExecutionRecordInput` | `session`, `observation`, optional `reference`; actual `issuedWorkContext` is inline |

The six import DTOs have required String fields except participant is optional only in WorkflowEvidencePreparationInput. Human requires it and review forbids it. Metadata validation retains exact nonempty/untrimmed/control-free values, ISO Instant and strict dotted entity collection grammar. ExplicitHuman is required for selected Goal/Repository Start. Evidence permits the exact family/kind combinations and assigned context/result types; Split review is refused. The two execution DTOs carry a required generated WorkflowIssuedWorkContext and exact URI Strings, with reference optional only in recordExecution. Their [finite execution contract](native-host-execution.md#r6--three-generated-public-values-and-two-normal-operations) defines the exact field order, alias checks, outcomes and opaque Session retention.

### prepareGoalStart

Use `org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareGoalStart` with one named `input` of generated type `WorkflowGoalStartPreparationInput`. Select actual host values and absolute local file: URI references; the following values illustrate the concrete schema:

```sh
"$primary/bin/launcher" \
  --classpath-file "$primary/target/cncf.d/runtime-classpath.txt" \
  --main-class org.goldenport.cncf.CncfMain -- --force-exit \
  --component-dev-dir "$primary" \
  --textus.assembly.descriptor="$host/.textus/assembly.yaml" \
  command org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareGoalStart \
  --input '{"input":"file:///absolute/host/goal-input.json","plan":"file:///absolute/host/goal-plan.json","selectionId":"selected-goal","participant":"actual-participant","origin":"ExplicitHuman","idempotencyKey":"selected-key","selectionCollection":"entity:smworkflow.cli.goal_selection","planCollection":"entity:smworkflow.cli.goal_plans"}' --json
```

The selected files contain generated GoalPhaseStartInput business JSON and the bare existing ordered Plan. Origin must be exactly ExplicitHuman; the two collection addresses must be distinct. The current packaged Goal catalog, selected-input business key and existing selection/Plan codecs retain authority. Exactly two collections contain one selected row each.

All fields above are required except participant is optional in the evidence DTO and governed by its selected kind. Metadata is exact, nonempty, untrimmed and control-free; collection addresses use literal `entity:` and three labels matching `[A-Za-z][A-Za-z0-9_]*`. Invalid metadata fails before acquisition where the existing read order guarantees it. Missing or invalid typed data and unavailable resources return ordinary Consequence/command errors without successful importer output. Normal CLI Help describes the generated service.

The passive helper `NativeHostGoalStartInput.prepareC(options: Map[String,String], readText: String => Consequence[String]): Consequence[Record]` owns finite domain decoding. The actual normal ProcedureActionCall owns acquisition through CNCF readText and UTF-8. It reuses the current runtime byte/stream limit, provider classification and release; binding and request creation perform no reads. No application NIO reader, private main, generic process, extra interpreter or fallback exists.

The raw successful `--json` response is the direct structured `WorkflowPreparedImport` datastore object. Existing codecs retain exact payload fields and row IDs; there is no JSON String/result/data wrapper. Save that actual output separately to the trusted `textus.import.data.file` input only when provisioning is intended. Existing CNCF StartupImport owns mutable save/create. Preparation performs no import/save/Start/Submit/Advance, grants no effect and authenticates no supplied origin.

### prepareRepositoryStart

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

The passive helper `NativeHostRepositoryStartInput.prepareC(options: Map[String,String], readText: String => Consequence[String]): Consequence[Record]` owns finite domain decoding. The actual normal ProcedureActionCall owns acquisition through CNCF readText and UTF-8. It reuses the current runtime byte/stream limit, provider classification and release; binding and request creation perform no reads. No application NIO reader, private main, generic process, extra interpreter or fallback exists.

The raw successful `--json` response is the direct structured `WorkflowPreparedImport` datastore object. Existing codecs retain exact payload fields and row IDs; there is no JSON String/result/data wrapper. Save that actual output separately to the trusted `textus.import.data.file` input only when provisioning is intended. Existing CNCF StartupImport owns mutable save/create. Preparation performs no import/save/Start/Submit/Advance, grants no effect and authenticates no supplied origin.

### prepareSplitStart

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

The passive helper `NativeHostSplitStartInput.prepareC(options: Map[String,String], readText: String => Consequence[String]): Consequence[Record]` owns finite domain decoding. The actual normal ProcedureActionCall owns acquisition through CNCF readText and UTF-8. It reuses the current runtime byte/stream limit, provider classification and release; binding and request creation perform no reads. No application NIO reader, private main, generic process, extra interpreter or fallback exists.

The raw successful `--json` response is the direct structured `WorkflowPreparedImport` datastore object. Existing codecs retain exact payload fields and row IDs; there is no JSON String/result/data wrapper. Save that actual output separately to the trusted `textus.import.data.file` input only when provisioning is intended. Existing CNCF StartupImport owns mutable save/create. Preparation performs no import/save/Start/Submit/Advance, grants no effect and authenticates no supplied origin.

### prepareRepositoryEffect

Use `org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareRepositoryEffect` with one named `input` of generated type `WorkflowRepositoryEffectPreparationInput`. Select actual host values and absolute local file: URI references; the following values illustrate the concrete schema:

```sh
"$primary/bin/launcher" \
  --classpath-file "$primary/target/cncf.d/runtime-classpath.txt" \
  --main-class org.goldenport.cncf.CncfMain -- --force-exit \
  --component-dev-dir "$primary" \
  --textus.assembly.descriptor="$host/.textus/assembly.yaml" \
  command org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareRepositoryEffect \
  --input '{"componentInstance":"actual-component-instance","selection":"file:///absolute/host/selection.json","target":"file:///absolute/host/target.json","effect":"Push","reference":"file:///absolute/host/effect-reference.json","participant":"actual-operator","issuer":"configured-host","occurredAt":"2026-10-14T00:00:00Z","collection":"entity:smworkflow.cli.host_evidence"}' --json
```

Read the three selected UTF-8 records in order: selection, physical target and evidence reference. The target has the existing five consumed String fields root, localBranch, remote, remoteBranch and endpoint. Select exactly CommitPending, Push or FinalFetch. The unchanged NativeHostRepositoryEffectPreparation.repositoryC retains owner/participant/scope/identity and emits one row without executing Git. Generated reference String conversions remain available.

All fields above are required except participant is optional in the evidence DTO and governed by its selected kind. Metadata is exact, nonempty, untrimmed and control-free; collection addresses use literal `entity:` and three labels matching `[A-Za-z][A-Za-z0-9_]*`. Invalid metadata fails before acquisition where the existing read order guarantees it. Missing or invalid typed data and unavailable resources return ordinary Consequence/command errors without successful importer output. Normal CLI Help describes the generated service.

The passive helper `NativeHostRepositoryEffectInput.prepareC(options: Map[String,String], readText: String => Consequence[String]): Consequence[Record]` owns finite domain decoding. The actual normal ProcedureActionCall owns acquisition through CNCF readText and UTF-8. It reuses the current runtime byte/stream limit, provider classification and release; binding and request creation perform no reads. No application NIO reader, private main, generic process, extra interpreter or fallback exists.

The raw successful `--json` response is the direct structured `WorkflowPreparedImport` datastore object. Existing codecs retain exact payload fields and row IDs; there is no JSON String/result/data wrapper. Save that actual output separately to the trusted `textus.import.data.file` input only when provisioning is intended. Existing CNCF StartupImport owns mutable save/create. Preparation performs no import/save/Start/Submit/Advance, grants no effect and authenticates no supplied origin.

### prepareAuthority

Use `org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareAuthority` with one named `input` of generated type `WorkflowAuthorityPreparationInput`. Select actual host values and absolute local file: URI references; the following values illustrate the concrete schema:

```sh
"$primary/bin/launcher" \
  --classpath-file "$primary/target/cncf.d/runtime-classpath.txt" \
  --main-class org.goldenport.cncf.CncfMain -- --force-exit \
  --component-dev-dir "$primary" \
  --textus.assembly.descriptor="$host/.textus/assembly.yaml" \
  command org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareAuthority \
  --input '{"request":"file:///absolute/host/full-goal-wait.json","reference":"file:///absolute/host/authority-reference.json","participant":"actual-operator","issuer":"configured-host","occurredAt":"2026-10-14T00:00:00Z","collection":"entity:smworkflow.cli.host_evidence"}' --json
```

Save the full normal Goal Advance JSON from its actual closing Wait. The generated reader requires Wait and exactly one supported authorityContext. Request and reference are read in that order; unchanged NativeHostAuthorityPreparation.goalC retains actual Step/Phase scope aliases, owner, historical time and native row identity. Ordinary Waits and other assigned kinds supply no closing authority.

All fields above are required except participant is optional in the evidence DTO and governed by its selected kind. Metadata is exact, nonempty, untrimmed and control-free; collection addresses use literal `entity:` and three labels matching `[A-Za-z][A-Za-z0-9_]*`. Invalid metadata fails before acquisition where the existing read order guarantees it. Missing or invalid typed data and unavailable resources return ordinary Consequence/command errors without successful importer output. Normal CLI Help describes the generated service.

The passive helper `NativeHostAuthorityInput.prepareC(options: Map[String,String], readText: String => Consequence[String]): Consequence[Record]` owns finite domain decoding. The actual normal ProcedureActionCall owns acquisition through CNCF readText and UTF-8. It reuses the current runtime byte/stream limit, provider classification and release; binding and request creation perform no reads. No application NIO reader, private main, generic process, extra interpreter or fallback exists.

The raw successful `--json` response is the direct structured `WorkflowPreparedImport` datastore object. Existing codecs retain exact payload fields and row IDs; there is no JSON String/result/data wrapper. Save that actual output separately to the trusted `textus.import.data.file` input only when provisioning is intended. Existing CNCF StartupImport owns mutable save/create. Preparation performs no import/save/Start/Submit/Advance, grants no effect and authenticates no supplied origin.

### prepareEvidence

Use `org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareEvidence` with one named `input` of generated type `WorkflowEvidencePreparationInput`. Select actual host values and absolute local file: URI references; the following values illustrate the concrete schema:

```sh
"$primary/bin/launcher" \
  --classpath-file "$primary/target/cncf.d/runtime-classpath.txt" \
  --main-class org.goldenport.cncf.CncfMain -- --force-exit \
  --component-dev-dir "$primary" \
  --textus.assembly.descriptor="$host/.textus/assembly.yaml" \
  command org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareEvidence \
  --input '{"family":"GoalPhase","kind":"human","request":"file:///absolute/host/full-human-output.json","result":"file:///absolute/host/answer.json","reference":"file:///absolute/host/answer-reference.json","executionEvidence":"file:///absolute/host/execution.json","issuer":"configured-host","occurredAt":"2026-10-14T00:00:00Z","collection":"entity:smworkflow.cli.host_evidence","participant":"actual-submitting-participant"}' --json
```

Read request, result, artifact reference and execution evidence in that order. Families are exactly GoalPhase, RepositorySync and SplitPhase. kind is review or human; Split review is refused. participant is required for Human and forbidden for review. Use actual assigned Request/Human outputs, preserving Goal Step/Phase review, direct/forwarded Repository completion and Split Human source/type distinctions. No reviewer or operator provenance is certified.

All fields above are required except participant is optional in the evidence DTO and governed by its selected kind. Metadata is exact, nonempty, untrimmed and control-free; collection addresses use literal `entity:` and three labels matching `[A-Za-z][A-Za-z0-9_]*`. Invalid metadata fails before acquisition where the existing read order guarantees it. Missing or invalid typed data and unavailable resources return ordinary Consequence/command errors without successful importer output. Normal CLI Help describes the generated service.

The passive helper `NativeHostEvidenceInput.prepareC(options: Map[String,String], readText: String => Consequence[String]): Consequence[Record]` owns finite domain decoding. The actual normal ProcedureActionCall owns acquisition through CNCF readText and UTF-8. It reuses the current runtime byte/stream limit, provider classification and release; binding and request creation perform no reads. No application NIO reader, private main, generic process, extra interpreter or fallback exists.

The raw successful `--json` response is the direct structured `WorkflowPreparedImport` datastore object. Existing codecs retain exact payload fields and row IDs; there is no JSON String/result/data wrapper. Save that actual output separately to the trusted `textus.import.data.file` input only when provisioning is intended. Existing CNCF StartupImport owns mutable save/create. Preparation performs no import/save/Start/Submit/Advance, grants no effect and authenticates no supplied origin.

### prepareExecution and recordExecution

Both use the same normal launcher prefix and one concrete `--input` object;
see the [complete command examples](native-host-execution.md#r9--ordinary-command-and-caller-loop).
`prepareExecution` consumes issuedWorkContext, mapping and origin; the latter
two select the original canonical mapping snapshot and existing origin wrapper.
It returns an opaque canonical Session string, optional canonical nextSelection
string and no completionEvidence. It never re-resolves an issued disposition.

`recordExecution` consumes issuedWorkContext, session, observation and optional
reference. It restores the explicitly selected canonical Session against the
actual issue, then consumes the observation's supplied attempt/profile through
the generic decoder and `recordC`. It does not substitute a current selection
for a stale supplied one or read a newer mapping. COMPLETED requires one
WorkflowArtifactReference file and may return structured completionEvidence;
other outcomes forbid reference and produce no completion evidence. Only the
finite configured alternate may remain after first DELEGATED unavailability or
decline. Blocked, failed, uncertain and exhausted outcomes stop.

The direct normal output is `WorkflowHostExecutionState`, with required session
and optional nextSelection JSON **strings**, plus optional structured
completionEvidence. These are host artifacts, not datastore importer rows or
business session IDs. Retain strings unchanged so normal recursive CLI naming
does not rename canonical controls. No import, save, provider call, Submit or
Advance occurs, and the result grants no authority or semantic acceptance.

## R2 — Current call and ordinary resource ownership

The actual generated declaration supplies metadata and ValueReader input decoding through NativeWorkflowOperationInput.recordC(request, "input"). Exact owning service/operation matching precedes CommandAction Sync creation. Passive preparation bypasses workflow session wrappers. Only ProcedureActionCall.execute maps the finite typed fields to the selected helper and supplies its call-local String reader.

The reader validates the exact reference, uses ResourceReference.parseC, permits only file scheme, then invokes protected read_resource_text with UTF-8. The actual current resource context owns provider selection/classification, stream acquisition/release and the configured textus.resource.read.max-bytes/runtime stream enforcement. No app readBounded cap, custom provider, NIO acquisition, additional UoW/interpreter, root discovery or fallback is introduced. Domain decoding retains its existing order after normal call creation; invalid metadata acquires no file where that order guarantees it.

## R3 — Direct structured importer output

The six import preparations return WorkflowPreparedImport through the standard `OperationResponse.Json`, parsed from the existing prepared importer Record. The two execution conversions instead return the direct WorkflowHostExecutionState described above. The normal formatter preserves the importer object:

```json
{"datastore":[{"collection":"entity:smworkflow.cli.host_evidence","records":[{"id":"existing-native-identity","nativeHostEvidencePayload":"existing-native-storage-encoding"}]}]}
```

WorkflowPreparedImport has datastore: WorkflowPreparedImportCollection +. Each collection has collection: String and records: WorkflowPreparedImportRow +. A row has id: String and optional String selectionPayload, selectedPlanPayload, nativeHostEvidencePayload; exactly one payload is actually emitted per row. Goal emits its two existing selected rows; Repository emits one selected-input row; the other Operations emit one native evidence row. Existing storage codecs, full payloads, exact camel-case field names, collection addresses, lookup/identity and native business owners are unchanged. These storage names are consumed data and must not undergo display naming. No generated toRecord transformation is applied to nested importer rows and no String/result/data envelope wraps output.

## R4 — Passive meaning and unchanged owners

Supplied metadata authenticates no origin, certifies no reviewer/operator result and grants no effect. No import, save, Start, Submit, Advance or remote operation occurs. Trusted provisioning remains a separate existing StartupImport or managed storage action. Its mutable input is no committed receipt or immutability claim. Actual native principal/provisioning/permission grants, owner scopes, aliases, historical time, payload codecs, no replay and unknown-result handling remain authoritative.

Typed Git and typed SBT continue inside their existing normal UoW adapters and native lock owners. No generic process, private main/JAR, new engine, global product SBT wrapper or scripted fallback is introduced. Development top-level SBT remains owned by the registered serial harness under AGENTS. Shared skills and user workflow Operation procedures are unchanged.

## R5 — Callers and executable examples

Finite Python actions keep their existing CLI options, routing/aliases, locking, task no-overwrite, timeout cleanup, output retention and exactly one native invocation. The two additional execution actions use only their declared finite options and explicitly retain Session/selection/evidence as [host artifacts](native-client-installation.md#finite-host-execution-actions). They map selected files with Path.as_uri() and explicit DTO field names. They check consumed route fields without replacing native semantic decoding or synthesizing authority. Unknown result stops without retry; Submit remains separate.

| Example | Executable scenario owner |
|---|---|
| P1 | NativeHostPreparationOperationSpec: parameterized actual six bound Operations, Unicode local files/metadata, real normal formatter Goal/direct Repository/Goal-forwarded Repository Human request files, equality with native business owners, storage codec decoding and no passive save |
| P2 | Exact typed service/input/request creation without acquisition, malformed metadata order, unsupported family-kind, missing participant and crossed Human context |
| P3 | Actual missing file (P2), actual oversized file denied by ordinary runtime cap, exact local file URI policy |
| P4 | All six preparation Operations through standard CncfMain command, including Goal/direct Repository/Goal-forwarded Repository Human; exact direct --json importer objects, finite invalid metadata and normal generated service Help |
| P5 | Fresh unprovisioned normal host refuses Start; actual standard prepareGoalStart stdout is explicitly selected unchanged for real StartupImport, then a separate normal Goal Start returns started and a nonempty session |
| E1–E3,E7 / T1–T3,T5,T7,T8 | Corresponding six NativeHost*InputSpec files retain selected payload/business validation, finite Human wire aliases, metadata and explicit reader failures |
| Existing CLI leaves | NativeWorkflowCliSpec retains physical runtime close/reopen, separate ordinary StartupImport, Goal Start/Advance, task ID routing and one client connection |
| N1–N4 | NativeHostExecutionOperationSpec: four generated mode scopes, normal wire aliases, actual current-reader files, observed selected attempts, finite fallback, completion evidence and metadata/root/cap refusal |
| C1–C2 | NativeHostExecutionCliSpec: independent ordinary command Start/Advance/prepare/record, explicit Session retention across configuration change, unchanged pending redisplay and actual named semantic Submit acknowledgement |

P5 selects the actual successful command stdout's absolute path through the fixture's trusted `textus.import.data.file` configuration key. It does not parse, rewrite, copy or normalize that file for provisioning. A subsequent separate normal process reopens the host and invokes CNCF StartupImport before Goal Start. Preparation alone still saves no row; the operator's explicit configuration selection owns provisioning.

Preparation is a prerequisite. Normal typed SBT shared fixture, full conflict/repair/Goal workflow, genuine installed mode/session/provider tasks, bounded Goal task, remaining security/CBD assurance, independent review and Step/Phase closure remain mandatory. Primary aggregate full-suite validation remains PHASE-6.3.
