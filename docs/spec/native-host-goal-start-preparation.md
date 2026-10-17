# Native host Goal Start preparation

The owning host records its genuinely attributable selection of the actual Goal
business input and complete ordered Plan before provisioning. It supplies the
selection identity, participant, origin and idempotency key explicitly. The
`ExplicitHuman` option is supplied host data; neither that label nor successful
conversion authenticates a Human source or grants permission. Actual participant,
selection, scope and Plan admission remain with `ProfileStartAdmission`,
`NativeWorkflowStartInput`, `GoalPhaseNativeRun` and ordered progression.

The normal passive preparation Operation returns only selected importer inputs. It creates no Workflow state, history, continuation, result or consent. The finite Scala decoding helper is `NativeHostGoalStartInput`; resource reads belong to the executing normal CNCF call.

## Explicit business input and bare Plan

The two selected UTF-8 files contain ordinary business JSON and a bare
`GoalPhaseOrderedProgression.Plan`, respectively. They contain no storage prefix,
selection envelope or runtime record. The generated `GoalPhaseStartInput` reader
owns business input decoding through `RecordDecoder` and `ValueReader`; its
derived `Codec.AsObject` owns canonical selected JSON:

```json
{"phaseIdentity":"phase-6","repositoryIdentity":"sm-workflow"}
```

The current generated wire aliases `phase_identity` and `repository_identity`
are accepted and normalize to canonical field names. The existing String reader
retains its nonnull value-to-String conversions; preparation adds no stricter
business codec. Missing/null required fields and invalid JSON fail through the
canonical readers. Unused diagnostics, including claimed consent, permission,
workflow identity or execution authority, are inert.

The existing Plan companion Decoder reads the explicit selected Plan. Its
Encoder produces this bare shape, with the host supplying actual values:

```json
{
  "identity":"selected-plan",
  "revision":"1",
  "phase":"phase-6",
  "repository":"sm-workflow",
  "steps":[{
    "identity":"step-1",
    "purpose":"Implement the selected boundary",
    "ownedPaths":["src/main/Selected.scala"],
    "dependencies":[],
    "checks":["selected-focused-check"]
  }],
  "phaseChecks":["selected-phase-check"],
  "repairLimit":0
}
```

`Plan` fields are `identity`, `revision`, `phase`, `repository`, `steps`,
`phaseChecks`, `repairLimit` and optional `phaseReviewClassification`. Each
`GoalPhaseOrderedProgression.Step` has `identity`, `purpose`, `ownedPaths`,
`dependencies`, `checks` and optional `workClassification`. When supplied,
`workClassification` is `GoalPhaseWorkClassification.Assignment` with
`implementation`, `repair` and `review` Classification objects. A Classification
contains the existing enum names for `kind`, `complexity`, `domain` and
`footprint`; `phaseReviewClassification` uses the same Classification shape.
Supply the host's actual typed Plan through its existing Encoder. Extra unused
Plan diagnostics are inert; structural decoding errors fail. Conversion does
not decide semantic Plan validity, scope agreement or authorization.

## Normal typed preparation Operation

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

The passive helper `NativeHostGoalStartInput.prepareC(options: Map[String,String], readText: String => Consequence[String]): Consequence[Record]` owns finite domain decoding. The actual normal ProcedureActionCall owns acquisition through CNCF readText and UTF-8. It reuses the current runtime byte/stream limit, provider classification and release; binding and request creation perform no reads. No application NIO reader, private main, generic process, extra interpreter or fallback exists. See [normal preparation Operations](normal-host-preparation-operations.md).

The raw successful `--json` response is the direct structured `WorkflowPreparedImport` datastore object. Existing codecs retain exact payload fields and row IDs; there is no JSON String/result/data wrapper. Save that actual output separately to the trusted `textus.import.data.file` input only when provisioning is intended. Existing CNCF StartupImport owns mutable save/create. Preparation performs no import/save/Start/Submit/Advance, grants no effect and authenticates no supplied origin.

Both collections require literal `entity:` followed by exactly three dotted
labels matching `[A-Za-z][A-Za-z0-9_]*`, validated by guarded existing
`EntityCollectionId` construction. The two addresses must be distinct. Their
example names above must be selected and configured by the actual owning host;
the tool chooses no collection or directory. Canonical printed collection IDs
in storage grants/configuration retain their existing format.

Successful stdout is standard Record/JSON importer input with exactly two
datastore entries and one row in each. The selection row ID comes from
`NativeWorkflowStartBusinessKey(Goal(input), participant)` and its String field
is the existing `ProfileSelectionJsonV1.storageField` (`selectionPayload`). The
Plan row ID is the explicit selection identity and its String field is
`GoalPhaseSelectedPlanJsonV1.storageField` (`selectedPlanPayload`). Existing
storage codecs own all prefixes and envelopes. The actual packaged
`GoalPhaseRuntimeCatalog.loadC().definition` supplies workflow identity/revision;
there is no copied definition or authority override. `recommendationReference`
is absent (`None`), and the selected typed business value uses the existing
GoalPhase input type identity.

Failure exits two with a bounded tool diagnostic on stderr and no successful
JSON. Help prints usage; successful preparation prints JSON. Hosting JVM
warnings may also appear on stderr. The tool creates no directory or output
file and runs no subsequent process.

## Host provisioning and ordinary use

The host captures successful stdout into a task-owned host input file after
observing a zero exit status. It selects that exact file as
`textus.import.data.file` and configures the matching separately owned selection
and Plan collections through the current host-record destinations, storage plan
and managed read grants. Follow the
[normal host configuration](normal-three-workflow-host.md#host-configuration).
Existing CNCF StartupImport owns mutable save/create provisioning on ordinary
startup. Imported input is no committed, immutable or atomic receipt. Import
only the selected business input and Plan rows, keeping runtime and evidence
destinations separate.

The host records attributable real selection before provisioning. Encoding does
not replace that provenance or native authorization. After native admission,
the ordinary command FQN `org.simplemodeling.textus.SmWorkflow.GoalPhaseService.startGoalPhase`
returns the actual canonical task session ID. Retain and reread that ID in the
exact owned task file, then explicitly call the ordinary command Advance under
the [installation and caller routing procedure](native-client-installation.md).
Business parameters and installed skill procedures remain unchanged. Real task
execution uses command; the one normal FQN client Start smoke remains connection
qualification only.

## Rules and executable examples

R1: Prepare only explicit host-selected input and Plan. Supplied origin metadata
asserts no authenticated Human source or granted permission.

R2: Require all eight explicit input fields, exact metadata, supported origin and
distinct valid collections. Choose no identity, Plan, participant or consent.

R3: Reuse the generated Record/ValueReader and derived JSON codec plus the
existing Plan companion codec. Native owners retain semantic acceptance.

R4: Reuse the actual packaged catalog, storage codecs and business-key owner to
emit exactly two selected collection rows.

R5: Preparation persists no runtime or evidence state and performs no Start,
Advance, import or authorization.

R6: The ordinary CLI consumer provisions selected specification inputs through
the production helper. Command transport behavior establishes no genuine
installed semantic execution acceptance.

R7: Select evidence for this changed untrusted-input/host-import composition
through existing security and acceptance owners, with no new engine or stage.

`NativeHostGoalStartInputSpec` binds E1–E3 with afterWord metadata, adjacent actual
Given/When/Then and should expectations. E4 uses the existing unchanged
`NativeWorkflowCliSpec` assertion leaves with the production helper in its
selected-input provisioning path. This mapping records obligations, not results.

| Example | Rules | Observable behavior |
| --- | --- | --- |
| E1 | R1,R3,R4,R5 | Explicit current files/metadata decode as two selected rows, preserving addresses, typed input, packaged definition and every Plan field |
| E2 | R2,R3 | Generated aliases normalize; unused diagnostics are inert; canonical reader and structural Plan failures return errors; semantic admission remains native |
| E3 | R1,R2,R4,R5 | Invalid arguments/readers/origin/collections fail; sole help reads no file; no automatic selection or authentication |
| E4 | R4,R6 | Ordinary command Start/Advance, same-task ID retention, rejection/interleaving and one normal client Start consume production-prepared rows |

The [adopted security profile](../phase/phase-6-security-review-profile.md)
retains existing authority, selected input, least-authority storage and host
attribution boundaries. CNCF107/Cozy83/CBD16 remain PLANNED producer documents,
not delivered guarantees, prerequisites or security PASS. Existing Phase
6.1/6.2/6.3 evidence/review/repair owners remain authoritative.

Tool and specification success are prerequisites only. Actual installed tasks
for all three families, attributable host/operator/executor/provider work,
independent Goal execution/review, typed modes and Effective Acceptance Boundary
work, original Step validation/acceptance and complete Phase closure remain
mandatory. PHASE-6.3 retains primary aggregate full-suite ownership.

The normal public route and runtime acquisition cases are mapped to `NativeHostPreparationOperationSpec` P1–P4 in [normal preparation Operations](normal-host-preparation-operations.md). Retained E/T IDs in the InputSpec continue to cover payload, alias, metadata and reader failures; former subprocess T4 (or evidence T6) is owned by P4.
