# Goal Phase validation metadata selection

`GoalPhaseValidationMetadata` is the internal bounded discovery, query and exact-binding
consumer of the accepted CNCF V1 APIs. Its typed `Request` supplies a discovery scope,
selection query, explicit supplemental logical references and a host-selected logical
`WorkspaceId`, followed by optional explicit typed-data invocations and global
`executionParameters` (both default empty). Each invocation contains an exact logical
`reference` and `parameters: Map[String, Json]`. It is not a command list or an
operation/configuration parser.

`ValidationDiscoveryV1` owns actual local ScalaTest registration enumeration, annotation
interpretation, normalized metadata, query validation and exact-reference resolution.
`ValidationMetadataV1` owns declaration validation and composition, including execution
requirements and the normalized input schema. `ValidationInputsV1` owns input names,
kinds, required values, defaults and exact value validation. `ValidationOperationBindingV1`
binds that actual resolved schema and owns creation of each typed `SbtExec` intent.
The caller supplies and retains ownership of the actual ClassLoader. This helper does
not create or change a loader, inspect annotations itself, traverse files or parse source.
The native caller's bounded loader lifecycle is described below.

The consumer retains the actual returned inventory and requires its scope identity,
revision and every artifact field to match the request. The producer's documented sort
by component/artifact identity is accepted. This relationship is ordinary input context,
not an unchanged-content certificate or a separate authoritative registry.

Queries use OR within purposes, OR within features and AND between those dimensions.
An empty dimension adds no filter. Ordered candidates are query results, then legacy
supplemental references with empty per-invocation parameters, then explicit invocations.
For every candidate the consumer supplies `executionParameters ++ invocation.parameters`
to the producer's exact binder against the same inventory and workspace. Explicit
per-invocation values win for the same key. Unknown globals are passed through and
rejected by the producer; the consumer never filters them by schema or coerces values.
Only after binding does the consumer deduplicate by exact logical reference and equal
normalized typed parameter maps, preserving the first occurrence. Implicit defaults and
equal explicit defaults therefore select once; different values of one logical operation
remain separate runnable variants. Supplemental and explicit-invocation vectors and each
parameter map are independently bounded to 16, as is the final distinct selection.
Maps, keys, invocation/reference members and JVM Json members must be structurally
present; ordinary JSON null remains supplied data for producer rejection. Missing,
invalid and ambiguous references keep their distinct
producer diagnostics. No guessed identities, label normalization or broadened query is
used. A valid unannotated or out-of-query supplemental operation retains its actual
normalized metadata; no tags or purposes are invented.

`Selection` retains the inventory, original query, original supplemental vector and
unchanged producer `BoundOperation` values. `requireRunnableC` permits a required batch
only when inventory completeness is `COMPLETE` and the selected count is 1 through 16.
An empty complete selection is represented explicitly but fails that eligibility check.
`PARTIAL` and `UNKNOWN` fail even with nonempty selections. Failures report actual scope
identity, revision, completeness, count and retained diagnostics. Incomplete or empty
selection never proves absence or runtime success. The explicit no-tests policy elsewhere
is unchanged.

Metadata and `ExecutionRequirement` are declarations. Logical workspace selection and
batch eligibility grant no execution, effect, consent or profile authority. This helper
has no interpreter, process request, runtime result, receipt, facts, store or counters.
Effects belong to the current UoW route. `GoalPhaseNativeMetadata` connects the native
consumer to that existing route as described below. Discovery and retained context
alone do not establish connected runtime acceptance, TS01 completion or Phase closure,
and introduce no review gate.

The executable specification discovers the compiled, narrow `metafixture` artifact with
the fixture class's code source and actual loader. Its three real registrations contain
two declared scenarios and one scenario without an operation anchor. Discovery evidence
comes from the producer API. Explicitly synthetic negative completeness and reference
variants test refusal semantics only. Assertions cover declarations and typed intent;
they do not claim execution of those selected intents. Parent-selected generation,
compilation and focused runtime validation remain the parent's responsibility.

## Native host configuration and selection

The fixed native validation `Check` may declare an optional `Profile` only for
`TestOnly`. The Profile contains an exact discovery scope, producer query and explicit
supplemental references. It is nested structured host configuration resolved through
`ThreeWorkflowHostParameters`, `ComponentInitializationParameters` and the actual
`ExecutionContext`. Runtime-selected `hostIdentity` and `operation` fields are rejected
in caller policy configuration. No command, executable, argument vector, permission,
system-property or environment parser is added. An absent historical kind retains
ordinary Test meaning only.

### Explicit Slice validation-plan source

A Profile may instead append an explicit `source`. In that case its `query` must
be exactly `{"purposes":[],"features":[]}` and `supplemental` must be `[]`;
the selected file is the sole Slice query and supplemental-selection owner.
The discovery `scope` remains the original host-owned scope. Embedded Profiles
omit `source` and keep their existing behavior. Explicit null and unknown fields
are refused; no selected source falls back to embedded data.

The exact Source object is:

```json
{
  "path": "docs/slices/S1-validation.json",
  "identity": "slice-validation-S1",
  "sliceRef": "S1",
  "acceptanceConditions": ["smoke-condition", "focused-condition"]
}
```

The host issues the actual path, identity, Slice reference and required condition
IDs in the existing ContextBundle. A path is a literal CNCF WorkspaceFilePath,
with no URI, and must appear exactly in both the selected Step's `ownedPaths`
and the declaration's `candidatePaths`. A Step source's `sliceRef` equals that
Step's identity. A Phase source uses `phase:` followed by the actual Phase and
may use the union of Plan Step owned paths, while still requiring an exact
candidate path. Issue-time checks are pure: the first implementation assignment
can author a file that does not yet exist. Only actually selected host checks
contribute instructions; an unselected source grants no work scope.

The selected file has this exact envelope and typed payload shape:

```json
{
  "schemaVersion": "sm-workflow.slice-validation-plan.v1",
  "payload": {
    "identity": "slice-validation-S1",
    "revision": "revision-1",
    "sliceRef": "S1",
    "acceptanceConditions": [
      {"identity": "smoke-condition", "description": "The smoke scenario is selected"},
      {"identity": "focused-condition", "description": "The exact focused scenario is included"}
    ],
    "metadataSelection": {"purposes": ["SMOKE"], "features": []},
    "additionalOperations": [
      {
        "reference": {
          "component": "component:sm-workflow-fixture",
          "specification": "metadata-fixture",
          "operation": "focused-b"
        },
        "parameters": {}
      }
    ],
    "executionParameters": {},
    "acceptanceCoverageRefs": [
      {"condition": "smoke-condition", "query": true, "operations": []},
      {
        "condition": "focused-condition",
        "query": false,
        "operations": [{"component": "component:sm-workflow-fixture", "specification": "metadata-fixture", "operation": "focused-b"}]
      }
    ]
  }
}
```

Every introduced object is closed and non-null. Arrays and maps are required
even when empty. Stable identities, revisions, Slice and condition references
are nonblank ordinary strings bounded to 4096 UTF-8 bytes. Vectors are bounded
to 16. Conditions are nonempty and unique with meaningful descriptions; the
payload's condition-ID set must equal the Source's required set. There is exactly
one coverage entry per condition. Each entry declares `query=true` or at least
one exact operation reference. Query coverage needs an actual queried operation;
every explicit coverage reference must occur in the actual selected union from
the complete discovered inventory. Declared supplemental references alone cannot
establish successful coverage or exact binding. Producer query and reference
codecs remain the existing metadata codecs; no annotation parser is introduced.

Per-invocation `parameters` and `executionParameters` are required finite objects,
each bounded to 16 entries. Their structural admission does not interpret input names,
kinds or defaults. The adopted full Plan supplies both to the existing producer binder.
Wrong JSON kinds, unknown keys, explicit null and absent required values fail through
`ValidationInputsV1` before executable intent is returned. No value is silently omitted
or converted to an ordinary command/argv/configuration representation. Existing no-input
operations retain empty maps and unchanged binding behavior.

For example, the actual isolated `typedmetafixture` declares `count` (Integer, default
1), `enabled` (BooleanValue, default true), and `label` (Text, default `default case`).
With query `{"purposes":[],"features":[]}`, these additional operations and globals
select the same logical scenario twice, with counts 3 and 7, both disabled and carrying
the shared label. An explicit count 3 would instead deduplicate with the query candidate.

```json
{
  "additionalOperations": [{
    "reference": {
      "component": "component:sm-workflow-typed-fixture",
      "specification": "typed-metadata-fixture",
      "operation": "typed-case"
    },
    "parameters": {"count": 7}
  }],
  "executionParameters": {"count": 3, "enabled": false, "label": "shared case"}
}
```

Retained metadata adds optional `inputSchema`, emitted when nonempty and interpreted as
empty when absent for the existing no-input wire contract. Its single representation is
an array of closed parameter objects `{name, kind, required, defaultValue?}`. Kinds are
exactly `Text`, `BooleanValue`, and `Integer`; defaults are matching JSON string, boolean,
or integral Long number primitives. Boolean and integer defaults are never stringified.
Absent `defaultValue` means None; explicit null for either default or schema fails.
Unknown fields, mismatched default kinds and noncanonical incoming schema order fail.
`ValidationInputsV1.validateSchemaC` and `ValidationDiscoveryV1.selectC` own schema and
canonical metadata validation; this consumer never silently sorts incoming metadata.

The new isolated ordinary annotated ScalaTest fixture consumes standard `test.configMap`
through the producer bridge in `withFixture`. Focused specifications cover actual
discovery, binding and retained transport. These are consumer wiring contracts; actual
process input variants, genuine AI adoption, integrated acceptance and the remaining
Phase obligations require separate evidence. No local publication, full validation,
Step acceptance or Phase acceptance is claimed here.

Native Validation checks all selected source scope/path bindings before effects.
After the ordinary check prefix succeeds and actual candidate preconditions
hold, the owning ActionCall reads each selected file through
`WorkflowFileExecution.readTextC`, `FileTarget` and the logical
`sm-workflow-repository` workspace. It decodes and admits the exact plan, then
uses the existing CNCF discovery, union selection and binding route. A failed or
unknown prefix does not read the file or discover metadata. Missing files,
invalid plans, mismatched identity/Slice/condition IDs and absent or extra captured
host groups are refusals with no default, alias or fallback.

The ordinary retained Batch appends the typed `plan` alongside its original
configured Profile, complete inventory and exact references. Embedded Batches
omit `plan`; source-bound Batches require it, and embedded Batches forbid it.
Public Record normalization retains the enriched typed context. Retained binding
reconstructs selection and verifies coverage using that inventory without file
reads, loader/discovery calls or check reexecution. No raw file snapshot or
integrity authority is added. Actual execution still produces the existing typed
check results and receipts; independent Review and Admission keep their owners.

Authoring or changing the selected plan is ordinary owned Candidate work.
Implementation and repair must preserve the issued requirements and host scope;
independent review assesses the plan together with source and real check outcomes.
Semantic Submit does not accept the author's own plan, execute checks, admit a
Candidate or close the Step. These pure fixture contracts establish no actual
File UoW/Submit/AI authoring route or primary Step/Phase acceptance. The separately
owned normal CLI route and remaining Phase requirements still need their actual
evidence.

At a fresh native Validation boundary, the component owns a bounded `URLClassLoader`
over exactly the declared artifact roots, with the CNCF discovery ABI loader from
`classOf[ValidationDiscoveryV1.Inventory].getClassLoader` as its required non-null
parent. This shares ScalaTest framework type identity with the producer-owned
discovery API, including when the component loader resolves ScalaTest locally.
It delegates discovery and required-batch eligibility to the accepted producer APIs,
then closes that loader. It changes neither TCCL nor global classpath. Complete
ScalaTest 3.2.19 is an explicit consumer compile dependency because production discovery
loads actual Suite styles and assertion dependencies. Read-only status and review
projection never discover again.

Each selected metadata host check expands into exact TestOnly checks under its fixed
workspace ordinal. Length-prefixed host/component/specification/operation segments
produce stable logical identities, bounded to 4096 UTF-8 bytes. A reference occurring
once keeps that existing identity exactly. If one logical reference has multiple
distinct typed variants, all its occurrences append `:invocation:<ordinal>`, with a
1-based ordinal counting only occurrences of that reference in batch order. Repeated
references are admitted only when full Plan and inventory re-selection produces the
distinct variants. `operationC` pairs rebound operations and expanded checks by position,
requires exact check equality and confirms the operation reference, retaining the
correct parameter map for each variant. Expanded
checks carry the original host identity and exact operation reference. Their ordinals
may repeat within that one group only; another group cannot reuse the ordinal. The
whole selected batch remains bounded to 1 through 16 checks. Controller observations
map these checks back to stable distinct literal host IDs, preserving selected-plan
equality and existing repair counters and grants.

Retained binding uses only the full saved Plan and actual inventory, preserving schema,
defaults, globals and explicit values without rediscovery, file replay or hashes, even
when the retained artifact-root context is unavailable. The existing Review binding
captures the complete Batch, so it includes those values without a new review mechanism.
The current UoW host continues to own executable selection, logical workspace mapping,
capabilities, Manage grants, scenario allowlists and execution limits. Typed declarations
and successful pure selection confer no runtime permission.

The actual full producer inventory is retained in `Batch`, together with the exact
Profile, fixed check identity and ordinal, and selected reference order. Inventory
scope, roots, revisions, completeness, diagnostics, all discovered operations,
provenance, selectors, normalized metadata and origins remain ordinary saved input.
The inventory is never relabeled as a complete selected subset. This context supplies
no transit-integrity certificate, artifact recopy, acceptance or execution authority.

Consumer-owned Circe codecs preserve the finite typed V1 shapes and closed enum wire
values. Artifact paths are absolute and normalized. Expected duration is persisted as
an exact positive integral `expectedDurationNanos` and decoded in nanoseconds without
Double conversion. Execution requirements retain capabilities, risk, reasoning,
reviewRequired, disposition and reasoningClass. Explicit nulls, wrong types, unknown
enums and malformed references fail. Optional absence remains supported only where
the domain allows it; an absent historical metadata collection means empty business
context under the original schema versions.

## Retained selection and the owning UoW

When a requested group contains metadata, Native Validation first executes its
maximal leading ordinary check prefix through the existing owning UoW. A leading
Test can therefore compile newly authored tests, annotation anchors and generated
CML declaration providers before discovery loads that same artifact. Each ordinary
check retains its original identity, ordinal and kind, saved intent, typed result
and acknowledgement. A failed check or an intent without a result stops before
discovery; no later inventory or successful receipt is invented.

After the ordinary prefix succeeds, Native Validation saves its actual prepared
Payload under the mechanical `selection` reference. It uses the current native
storage and the same Validation stage. The Payload retains the original Validation
frontier source revision, full expanded selected checks, full metadata Batches and
the actual prefix receipts. For prefix length `p`, ordinary intent/result revisions
are `frontier + 2*i + 1` / `frontier + 2*i + 2`, and selection is at
`frontier + 2*p + 1`. An empty prefix keeps the original selection-at-`frontier + 1`
behavior. Ordinary validation without metadata adds no selection event.

Resume loads and validates this exact saved selection against the current literal host
policy, group order, kinds, ordinals and Profiles. It reselects and exactly rebinds the
saved full inventory through the producer APIs, checking reference order and expanded
identities without a loader, rediscovery or a requirement that artifact files remain
unchanged. Before consuming a saved selection it reconciles every actual ordinary
prefix intent/result pair: exact source, Step, candidate, ordinary selected checks,
receipt identities and kinds, contiguous native record identity, progression and
revision, and genuinely successful prefix receipts. The selection must retain those
same receipts. Resume skips their indices, so the build never runs again. Suffix
intent/result revisions remain `frontier + 2 + 2*i` / `frontier + 3 + 2*i`.

Missing or mismatched selection fails except while the exact ordinary prefix is
still being performed. Selection-only retained input and a completed check prefix
can be displayed safely. An existing intent without a result remains pending and
non-replayable, including before metadata selection. This adds no retry or fallback
execution path.

A returned ordinary prefix non-success retains the complete maximal ordinary
selection and receipts for only actually performed checks. The owning controller receives its complete declared required check list
for the existing repair or Decision event, preserving repair history and the failed
candidate context. Nonleading, reordered, extra, empty or wrong-candidate partial
selection is refused. With valid matching typed facts and successful earlier
receipts, exited/nonzero/Failed, indeterminate/no-exit/Incomplete and
exited/zero/Unverified TestOnly results can take the normal failure disposition.
Only the existing repairable typed failure permits Repair. Incomplete and
Unverified results reach Decision without retry authority, successful SBT claims
or metadata discovery. An intent without a returned result stays pending.
Successful admission still requires exact coverage of the
controller's full current Step or Phase check list; a successful ordinary prefix
cannot enter Review. Omitted metadata checks remain unfinished. None of these
source contracts establishes actual CML consumer execution or acceptance.

For each metadata check the current owning call uses the producer's exact bound
TestOnly intent through `WorkflowManagedProcessExecution.validationOperationC` and
the existing `sbt_test_only` DSL and UnitOfWork. Returned operation and target,
including the exact scenario, must match that intent before standard receipt creation.
There is no separate interpreter, generic process request or class-only fallback.
CNCF retains ownership of runtime class allowlists, workspace Manage permission,
capabilities, executable, working directory, environment, limits, arguments and typed
outcome classification. SBT receipt admission accepts ordinary host identities or
`metadata:` identities bounded by the native runtime identity contract to 4096 UTF-8
bytes without control characters. Generic and Git receipt identity limits remain
unchanged. Both use the same complete bounded output, UTF-8 and termination checks.
The standard receipt retains raw stdout/stderr and typed SBT facts, including
UnverifiedTests, under the stable per-operation identity. sm-workflow
neither parses output nor invents successful selected-test facts.

## Public review context and remaining proof

Public validation checks carry optional original host identity and a structured exact
operation Record. Public validation and native review context carry Batch Records
whose internal codec is canonical camelCase. Normal CNCF public Record rendering
follows its property naming context, which defaults to snake_case.
The full-Record converter follows Core's structured-data contract: null object
attributes are omitted at root and nested levels, and null sequence members are
refused, including inside nested arrays. Exact non-null scalars, large JSON
integers, array positions and empty arrays or objects are preserved. Producer-validated
Batch inputs contain no null parameter values. These Records are presentation and review context, never caller
execution authority. The actual passed validation metadata follows assigned review,
issue, repair and reopen context. Independent review scope includes nonempty
`validationMetadata`; passive host preparation retains its declared outer
`validationMetadata`/`validation_metadata` aliases and rejects conflicts. The existing
Batch normalization boundary applies CNCF's default input-name normalization to
structural object keys before the same exact typed Batch decoding, then returns
canonical camelCase JSON. Two structural fields that normalize to the same key are
rejected even when their values agree; unknown normalized fields, wrong types and
unknown enums still fail.

Canonical private Plan files and retained Batch JSON keep exact parameter-name maps
at `plan.executionParameters` and `plan.additionalOperations[].parameters`. Only the
public Batch Record projection represents a nonempty map at either location as an
array of closed `{ "name": "case-label", "value": "single" }` records, sorted by
the exact name. Names are string data values, so valid names such as `case-label`,
`case--label` and `tail-` survive ordinary CNCF field naming distinctly. Empty maps
remain empty objects, preserving the existing no-input public wire. Dictionaries at
other paths keep their ordinary Record projection.

`normalizeBatches` restores these public nonempty entry arrays to sorted exact
canonical maps and preserves keys and values inside already-canonical maps. An
entry requires exactly `name` and `value`, a nonempty string name, and a present
non-null primitive value. Empty arrays, duplicate exact names, missing or extra
fields, null entries and object or array values fail without merging, filtering or
coercion. Private Plan codecs continue to require map objects and accept no entry
arrays. The actual producer still owns accepted names, kinds, declared defaults and
schema binding; unknown names, wrong kinds and null canonical values remain invalid.
No alias inference or business-name normalization supplies a replacement value.
All other configuration field naming remains unchanged. Different selected metadata
changes the selected review target without inferring any host attestation.

The focused specifications use actual compiled-fixture discovery and real retained
batches. Explicitly synthetic mismatched typed returns establish refusal semantics
only. G1 source and specifications do not prove TS01 native runtime acceptance. G2
must still use normal Start/Submit/Advance in a dedicated isolated ScalaTest project,
execute actual selected `sbt_test_only` through the owning UoW, retain its exact typed
receipt and no-replay evidence, and demonstrate the relevant actual UoW permission
refusal. No product main, scripted invocation or product global lock supplies that
proof. Aggregate validation remains PHASE-6.3 responsibility.

## G2 normal CLI verification contract

`NativeGoalMetadataCliSpec` is an explicit `@DoNotDiscover` selection through the
ordinary `runtimeTestOnly` task. Its isolated fixture uses input Phase `1`, repository
`smwf-p62-cli-metadata`, one `step-1` owning `docs/phase.md`, literal check
`metadata-tests` for both Step and Phase, and repair limit zero. This is fixture input
and does not select the product's Phase 7 or modify the retained Phase 6.2 workflow.
Existing repair, continuation, quality and source-history fixtures keep their own
setup and execution behavior.

`freshMetadata` first creates the ordinary owned repository and local bare upstream.
It writes the selected host, Plan and normal configuration before Start, then authors
and commits only the isolated ScalaTest build setup. That setup commit is not a
Workflow Step acceptance. The docs baseline remains; the specification subsequently
changes only its assigned guide. No existing product state, SQLite rows or receipts
are read, seeded or replaced.

The isolated build is normal sbt 1.9.7 / Scala 3.3.8. It uses the existing compiled
`SelectedMetadataFixtureSpec` CodeSource root once and existing absolute normalized
entries from the primary `target/cncf.d/runtime-classpath.txt` as
`Test / unmanagedClasspath`. ScalaTest's standard Framework and one explicit
`TestDefinition` enroll the deliberate `@DoNotDiscover` suite. Its real three
registrations and assertions execute through ordinary `testOnly <class> -- -t
"<exact name>"`. No test task is replaced, and no main or substitute executable
supplies test results. The build's retained `Global / onLoad` appends its actual PID
and incoming command lines to scenario-owned observation files; these observations
are not successful test receipts.

The profile declares scope `sm-workflow-metadata-fixture` / `scope-1`, component
`component:sm-workflow-fixture`, artifact `sm-workflow-fixture-tests` with literal
fixture revisions `artifact-1` / `source-1`, and the narrow `metafixture` package.
The query is `(SMOKE OR FOCUSED) AND feature(component:sm-workflow-fixture,feature-a)`.
Actual discovery selects `smoke-a`; the explicit supplemental reference adds
`focused-b`. The unannotated third registration remains in the full inventory and
does not execute. Both selected operations retain workspace ordinal 1 under the
original `metadata-tests` identity. Their stable logical receipt identities are
derived from that identity and the exact producer references.

The positive scenario requires separate normal Start, Advance, semantic Submit and
explicit Advance processes. Start and semantic Submit must produce no SBT observation.
The validation Advance must run two actual typed `sbt_test_only` operations in its
owning UoW and return pending `ReviewStep`. At that normal auto-progression frontier,
the public `work.review` carries successful typed receipts and the full metadata
Batch; a separate `validation` view is not required on the review result. Assertions
require two successful exit-zero TestOnly receipts, each with the exact class, one
completed suite, one executed test, the correct actual captured scenario output and
no unselected registration output. Actual command observations must select the same
class/name pairs, with two distinct child SBT PIDs outside the parent and CLI processes.
Full Record roundtrips must retain all three registrations, provenance, query,
supplemental input, focused-b's exact 500ms duration, execution requirement and origins.
Two further separate Advance calls must retain the same candidate, Batch, receipts
and issued review context without new SBT PIDs or commands.

Three independent negative fixtures remove only the initial SBT capability, remove
only its workspace Manage grant, or replace only its exact suite allowlist with a
distinct valid class. Needed typed Git permissions remain available. The missing
Manage fixture retains its matching process grant, so normal Start must reject that
structurally inconsistent configuration with `Invalid runtime process declaration`.
It establishes no successful Session and issues no Submit or Advance; no SBT
observation may exist. This earlier configuration rejection is distinct from an
owning-UoW execution refusal.

The capability and wrong-suite fixtures establish normal Sessions, assigned docs
implementation and semantic Submit before their first validation Advance refuses
with `process.execution.capability-grant` and `sbt.execution.scenario-allowlist`,
respectively, before any SBT observation. Their repeated Advance must retain a
truthful fault or incomplete Wait, without successful selected receipts, ReviewStep,
completion, replay or fallback. All negative tests retain their actual separate
invocation stdout and stderr and never change configuration after refusal.

This implementation contract is not terminal execution evidence. The parent still
owns the selected `runtimeTestOnly NativeGoalMetadataCliSpec` invocation, affected
metadata-suite validation, terminal evidence and integrated independent P62-FIX
acceptance. The examples stop pending review and author no independent review PASS,
Human authority or Workflow Step commit. E's genuine review and commit evidence is
separate. G2 covers the single sm-workflow CNCF S4 consumer connection; it does not
close whole CNCF103, TS01 or Phase 6.2. Current Phase 6.2 review/release obligations
remain unchanged; aggregate full-suite ownership stays with Phase 6.3.

## I1 actual source authoring driver contract

`NativeGoalValidationAuthoringLiveSpec` is one explicitly enrolled `@DoNotDiscover`
finite-stage driver. Properties under `smworkflow.spec.p62.authoring.` select `stage`,
fresh ignored `output`, and, after preparation, the exact retained `root` and actual
`result` / `reference` or `preparation` files. An absent stage cancels; an unknown
stage fails. The existing `smworkflow.spec.sbt` selects the normal executable.
Inputs must be normalized real local regular files beneath that scenario. Saved
requests use `validation-authoring-` names; each stage retains actual native CLI
stdout/stderr, invocation arguments/PIDs, task/Session routing, and SBT PID/command
observations in fresh files. Those observations do not establish successful checks,
independent review, external provenance or effect authority.

The separate disposable Phase `1`, repository `smwf-p62-cli-authoring`, adopts the
native `native-authoring-plan@1` input with repair limit two. Step1 owns only
`src/test/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriterSpec.scala`
and `docs/validation.md`, selecting literal `build-tests`; Step2 owns only that guide,
selecting literal `metadata-tests`. Phase checks select `metadata-tests`. Dependencies,
classifications, acceptance and effective acceptance are absent in this raw Plan.
This variant has no documentSource/checklist progress or invented Human answer and
does not replace the original active Goal, H6 evidence or product Phase7.

`prepare` creates and commits only ordinary sbt1.9.7/Scala3.3.8 build inputs, the
LineWriter product library and exact requirements, then pushes setup to the fixture's
own local bare endpoint. The normal build consumes existing primary runtime classpath
entries for Compile and Test, excludes the existing compiled test fixture root, and
enrolls the fixed authored suite with ScalaTest's normal Framework/TestDefinition.
LineWriter appends one LF. The test source remains absent, no SBT runs, and normal
Start/retain/Advance issues genuine external test/annotation authoring work. The
fixture authors no tests, declaration providers, annotations or semantic outcome.

`after-source` accepts actual external completed semantic work and its reference,
then ordinary typed Test compiles and executes the authored source. Pending
ReviewStep must retain the exact two changed paths, candidate and successful typed
receipt with one SBT PID. `after-review` consumes separate real independent review
preparation through ordinary prepareEvidence/import/issued Submit and stops at the
actual StepClosure Wait. `after-authority` consumes separate actual authority
preparation through prepareAuthority/import/Advance. Its typed local Step1 commit
must have the original base parent and reviewed candidate tree before the same
Session receives Step2. The driver creates no authority file or review PASS.

`after-selection` consumes externally completed Step2 guide work. Only then does
ordinary native validation discover the compiled authored artifact under
`repository/target/scala-3.3.8/test-classes` in the narrow `p62authored` package.
The profile is `line-writer-authored/scope-1`, component `component:line-writer`,
artifact `line-writer-tests/artifact-1/authored-step-1`. Actual discovery must return
all three `line-writer@1` operations ascii, unicode and empty with their authored
features, purposes and origins. The query `(SMOKE OR FOCUSED) AND
feature(component:line-writer,ascii)` selects ascii and the exact supplemental
reference adds unicode; empty remains unselected. Unicode retains its authored
500ms duration, NORMAL class and line-writer-tests/local/STANDARD/no-review
execution requirement. Two actual typed TestOnly checks under ordinal2 must each
report the exact suite/scenario, one completed suite and one test. There must be
three distinct total SBT PIDs including Step1 Test, with exact selected command
observations. Normal public Record metadata is decoded through `normalizeBatches`;
the driver uses neither a source/annotation parser nor SBT output to invent facts.
Repeated Advance must retain the same pending metadata ReviewStep and its full
inventory/receipts without additional SBT execution. The driver stops there before
actual independent Step2 review, Step2 commit or Phase closure.

This source contract is not execution or primary acceptance evidence. G2 retains
its separate existing-compiled-fixture proof; I1 requires actual external AI
authoring, normal compilation, independent review and committed artifact discovery
in that order. No worker validation or acceptance is claimed. Parent-selected
generation, Compile, Test compilation and prepare-stage runtime validation remain
separate; later stages require genuine external inputs. CML syntax/generation,
Slice-management full adoption, project-default override, semantic concrete review
findings, finding recording and dependency decisions remain primary obligations.

## K2 actual CML consumer driver contract

`NativeGoalCmlValidationAuthoringLiveSpec` is a separate explicitly enrolled
`@DoNotDiscover` finite driver. Properties under `smworkflow.spec.p62.cml.` select
only `prepare` or `after-source`, fresh ignored `output`, and the retained `root`
and actual external `result` / `reference` files for the latter stage. An absent
stage cancels and an unknown stage fails. The normal `smworkflow.spec.sbt`
executable, normalized real files, public initial request, saved environment and
exact typed Git/SBT host configuration follow the existing authoring boundary.
Resume accepts only log range1000 and never reconstructs Workflow state.

Preparation supplies source requirements and ordinary build inputs only, in the
separate Phase1 repository `smwf-p62-cli-cml-authoring` with
`native-cml-authoring-plan@1`. Its single Step1 owns the same test source and
`docs/validation.md` as I1, plus `docs/validation.cml`, and selects `build-tests`
then `metadata-tests`; Phase checks select `metadata-tests`, with repair limit2.
Document and acceptance variants remain absent. Start/retain/Advance must issue
actual ImplementStep with its original Git base, no authored tests or CML and
zero SBT observations. The fixture authors no CML solution, tests, runtime
annotation anchors, generated providers, semantic result, review or authority.

Actual external AI work must author those three assigned files and matching
runtime annotation anchors. The CML must generate public no-argument
`SpecificationDeclaration`, `AsciiDeclaration`, `UnicodeDeclaration` and
`EmptyDeclaration` providers in `org.simplemodeling.textus.smworkflow.p62authored`
through ordinary Cozy `modeler-scala-value`. Specification `line-writer@1` has
declaration `{}`; operation declarations preserve I1's exact ascii/unicode/empty
registrations, features, purposes, origins, duration and execution requirement.
The parent separately runs ordinary Cozy generation before `after-source`.
Only this variant adds the normal
`target/scala-3.3.8/src_managed/test/scala` unmanaged Test source directory so
the generated providers compile with AI-authored tests and anchors in the same
ordinary Test artifact. It adds no source generator, source copying or task replacement.

`after-source` requires all three authored files and four exact generated provider
paths while the original unanswered request still has zero SBT observations.
Issued Submit consumes the actual supplied completed result/reference without
running checks. Ordinary Advance must then run Test and two selected TestOnly
operations through the existing owning UoW, returning actual pending ReviewStep
with exactly three changed paths, three successful typed receipts and one full
COMPLETE three-operation Batch. Query ascii, supplemental unicode and unselected
empty retain the original profile, provenance and both metadata origins. Exact
bound suite/scenario selections, one suite/one test per TestOnly receipt, three
distinct SBT PIDs and two exact TestOnly command observations are required.
Public Record roundtrips and two further Advance reads must retain the same
candidate, inventory, receipts and pending request without new SBT execution.
The driver stops before independent review, authority, Step commit or Phase closure.

This source implementation is not execution success or integrated P62-FIX
acceptance. The parent owns ordinary generation, compilation, finite-stage runs,
actual external inputs and acceptance evidence. Existing accepted I2/I3 evidence
and authoring fixtures remain separate and unchanged. Full Slice management-file
adoption and the remaining Phase6.2 acceptance limits remain open.

## L2B externally authored Slice plan driver contract

`NativeGoalSliceValidationPlanLiveSpec` is an explicitly enrolled `@DoNotDiscover`
finite driver. Properties under `smworkflow.spec.p62.sliceplan.` select `stage`,
fresh ignored `output`, and, on resume, the exact retained `root`. An absent stage
cancels; an unknown stage fails. `after-source` additionally requires actual external
`result` and `reference` files; `after-review` and `after-authority` require separate
genuine `preparation` files. Supplied files and preparation-referenced local URIs must
be normalized real regular files within the scenario root. Output is a fresh directory
under `target/sm-workflow/work` with an existing real parent. Retained public requests
use `slice-validation-plan-` names and the saved task is `slice-validation-plan-live-task`.
All native stdout/stderr, argv/PIDs, stage routing and SBT observations are retained
with fresh output names. Resume uses only finite log ranges1000/2000/3000 and the
saved ordinary host, raw Plan, input, initial request and exact typed process
declarations, including their saved SBT environment and limits.

Preparation uses separate input Phase1, repository `smwf-p62-cli-slice-plan` and
`native-slice-validation-plan@1`, with one Step1 owning exactly `docs/phase.md` and
`docs/slice-validation.json`, check `metadata-tests` and repair limit2. The ordinary
setup commits only the real sbt1.9.7/Scala3.3.8 build inputs and requirements guide
before Start. It consumes the same existing compiled `SelectedMetadataFixtureSpec`
artifact and narrow discovery scope as G2, with the normal ScalaTest Framework and
typed TestOnly suite allowlist. The selected source file remains absent. The guide
states requirements only; neither setup nor driver authors a solution plan, semantic
result, review, execution evidence or authority.

The Step Profile has empty embedded query and supplemental selection, with Source
`docs/slice-validation.json`, identity `slice-validation-step-1`, slice `step-1` and
condition IDs `smoke-condition` / `focused-condition`. Actual external authoring must
write the closed `sm-workflow.slice-validation-plan.v1` envelope, meaningful condition
descriptions and actual coverage, selecting smoke feature-a through the query and
adding focused-b explicitly. This historical L2B driver uses empty execution and
additional-operation parameters, reflecting the zero-input binder restriction at the
time; that restriction is superseded by the typed contract above. The unannotated
supplement stays unselected. Phase
validation separately declares the embedded `(SMOKE OR FOCUSED) AND
feature(component:sm-workflow-fixture,feature-a)` query plus focused-b, with no Source.

`prepare` performs normal Start/retain/Advance and saves the actual initial
ImplementStep request with exact source instructions, Plan, base and counters0/0/2,
zero SBT observations and an absent source file. `after-source` requires both owned
files and actual completed external work. Redisplay must retain the unanswered initial
request; issued Submit itself runs no checks. Ordinary Advance then acquires the
actual file through the existing File UoW, captures its admitted typed Plan, discovers
all three registrations and executes smoke-a followed by focused-b. Pending ReviewStep
must carry exactly two changed paths, the original base/Plan, a different candidate
tree, full COMPLETE inventory/provenance/metadata and two successful typed TestOnly
receipts. Public Records are normalized and decoded through the existing Batch codec;
the captured Plan must equal the admitted externally authored file. Assertions retain
the real 500ms/NORMAL focused metadata, capability/risk/reasoning requirement, exact
workspace1 suite/scenario and one-suite/one-test facts. Two distinct SBT PIDs and
exact selectors are observed. Two further Advances retain the same review, candidate,
captured Plan, inventory and receipts without replay.

`after-review` imports genuine separately supplied review preparation through normal
prepareEvidence and issued Submit, then retains the actual StepClosure Wait for an
accepted review. Other actual review outcomes retain their honest public frontier for
the parent; the driver authors no disposition. `after-authority` imports genuine
authority preparation for that saved Wait and performs ordinary Advance. The actual
typed Git Step commit must have the initial parent, independently reviewed tree and
nonblank review reference. Normal progression performs Phase ProductCheck with the
separate embedded Profile and retains pending ReviewPhase, two more successful
TestOnly receipts, full metadata without a Step source Plan and four distinct total
SBT PIDs. Another Advance must not replay checks or close the Phase.

If genuine review observations first require existing finding recording, the driver
instead preserves that actual request and its exact requirement in the stage output,
reports it to the parent, and stops with the two existing checks. It never assumes an
empty observation set or manufactures a next Step. No Phase review or closure is
submitted. Driver implementation is not actual authoring, adoption, execution success
or primary acceptance evidence. Parent-selected compile/Test compilation, finite runs,
external author/reviewer/authority work and integrated review remain separate.
Existing CML43 generation evidence is reused independently. Actual process evidence for
typed producer parameters, intentional Phase variants, full-suite6.3 and the remaining Phase6.2 obligations remain
open; this driver does not alter the active whole Goal, original Plan or repair history.


## P6-TS01 externally authored typed-input live driver contract

`NativeGoalTypedInputsLiveSpec` is a separate, explicitly enrolled `@DoNotDiscover`
suite. It retains the historical zero-input drivers and their K2/L3 evidence. Select
its positive test with `testOnly` and `-z "retain each finite typed-input frontier"`.
Properties under `smworkflow.spec.p62.typedinputs.` supply `stage`, fresh ignored
`output`, and, on resume, `root` and optional `logIndex`. The ordinary executable is
`smworkflow.spec.sbt`. No positive stage runs when `stage` is absent. The only stages
are `prepare`, `after-source`, `after-review` and `after-authority`; resume log ranges
are respectively1000–1999,2000–2999 and3000–3999. A stage never overwrites earlier
logs. Output must be fresh under `target/sm-workflow/work` with a canonical existing
parent. Saved public requests use `typed-inputs-`; the saved task is
`typed-inputs-live-task`. Native stdout/stderr, actual argv/PIDs and handoffs remain
in ignored output.

The dedicated input is Phase1, repository `smwf-p62-cli-typed-inputs`, Plan
`native-typed-inputs@1`, one `step-1` owning exactly `docs/phase.md` and
`docs/typed-validation.json`, check `typed-input-tests`, repair limit2 and the existing
review and closure settings. Setup creates the ordinary build for the actual compiled
`TypedMetadataFixtureSpec`, enrolls its exact suite at typed TestOnly ordinal1, and
commits only the disposable local build/requirements baseline. Its upstream remains
the existing local disposable repository. Setup supplies a requirements guide, never
a source Plan solution, proposed semantic answer, result, receipt, review or authority.

The actual `TypedMetadataFixture.scope` is used for both boundaries. Step has an empty
embedded query/supplemental selection and Source `docs/typed-validation.json`, identity
`typed-validation-step-1`, slice `step-1` and conditions `default-input` / `override-input`.
Phase has no Source and an empty embedded query selecting its one default registration.
External Step authoring must use the exact closed v1 envelope and revision1, meaningful
condition descriptions, coverage and an appended rationale. The exact reference is
`component:sm-workflow-typed-fixture / typed-metadata-fixture / typed-case`. The query
selects count1. Explicit count1 equals that default and must collapse; explicit count2
with label `override case` is distinct. Globals are boolean `enabled=false` and string
`label="日本語 shared $() literal"`. `default-input` has query coverage;
`override-input` has explicit reference coverage. The guide states these requirements
without writing the Plan solution.

`prepare` performs ordinary Start/retain/Advance, saves the actual ImplementStep request,
requirements baseline and root/task/session/stage metadata, and asserts the exact
Source/Plan/base, zero repairs, absent source and no SBT PID/task/observer file.
`after-source` requires genuine external `result` and `reference` files and both actual
owned files. Unanswered Advance and semantic Submit run no checks. Normal Advance
then uses File acquisition, discovery and typed UoW/SBT execution. Pending ReviewStep
must retain the exact candidate, full admitted Plan and COMPLETE one-operation inventory
with all three input schema kinds/defaults. It carries two same-reference checks with
`invocation:1` / `invocation:2`, exact-suite/exact-test receipts (one suite and one test
each), and bound maps `(1,false,"日本語 shared $() literal")` then
`(2,false,"override case")`. Two independent child PIDs and ordinary task selectors
must match, including the fixed `-Dcncf.validation.input.*` values. Two repeated Advances
retain the same pending review, metadata, receipts, PIDs and received-input observations.

The independently asserted received values come from test-only instrumentation in the
actual fixture's existing `ValidationInputsV1.fromConfigMapC` bridge. Only the dedicated
host supplies `SMWORKFLOW_TYPED_INPUT_OBSERVATIONS`, fixed to the scenario root's
`typed-input-observations.jsonl`. The fixture derives the primary `target` from its real
code source, requires an absolute normalized regular-or-absent file beneath a canonical
direct `target/sm-workflow/work/native-cli` child, and appends one canonical primitive
JSON line per actual invocation using CREATE/APPEND. Count is a Long, enabled a Boolean
and label a String. With no observer environment it writes nothing. No input parser,
operation authority, generic argv/environment extension or product File API is added.
Observation lines are test instrumentation, never receipt or acceptance authority;
the driver does not infer received values solely from command rendering.

`after-review` requires genuine separately supplied `preparation` for ordinary
prepareEvidence and issued Submit. Full review target/metadata normalization remains
checked. A real accepted outcome advances to the unchanged StepClosure Wait/candidate;
other real outcomes remain visible without invented closure. `after-authority` requires
genuine authority `preparation` for that actual saved Wait and the independently
supplied accepted review. Ordinary Advance performs the isolated local Step commit,
whose parent is the original base and tree is the reviewed candidate. Nonempty genuine
finding recording is routed unchanged and retains two executions until handled. If
normal progression instead reaches ReviewPhase, its embedded default check runs once:
three total actual PIDs/task selectors/observer lines, with third map
`(1,true,"default case")`, one Phase receipt and full schema inventory without a Source
Plan. Repeated frontiers replay no checks. No Phase completion is manufactured.

The distinct test `refuse specification-owned wrong-kind and unknown typed inputs before
SBT` is selected separately with `testOnly -z "refuse specification-owned"` and no positive
stage property; it cancels when a positive stage is selected. Each invalid map (string
count or unknown key) gets its own fresh disposable native Session. This negative
specification may explicitly author invalid Plan/semantic result/reference inputs.
Ordinary Start/Submit/Advance must retain the actual failed typed result and selected
Session before any SBT PID, task or observer line. It supplies no genuine AI, independent
review or Human approval. These stated negative inputs never serve as positive evidence.

This implementation claims no stage execution or success. Parent-selected compilation,
focused suites, negative execution, genuine external positive work/review/authority and
integrated review remain required. Fresh packaged Skill inspection and task-private
semantic caller adoption remain with the parent. Primary Step/Phase acceptance,
fullsuite6.3, CBD10 and all other whole6.2 obligations remain open; original Goal,
Plan/epoch, K2/L3 history and repair accounting are retained. No evidence logs are
committed by this driver contract.
