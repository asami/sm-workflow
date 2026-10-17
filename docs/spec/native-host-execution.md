# Native host execution handoff

The existing host uses public `host.NativeWorkflowHostExecution` to connect an
actual generated `WorkflowIssuedWorkContext` from a Request to CNCF's finite
`WorkflowExecutionHarness`. This library bridge does not install a Codex/provider
driver, invoke tools, dispatch an application command or advance a Workflow.
Actual installed host/tool/provider availability, all three skill families and
genuine separate chat/session observations remain Phase 6 acceptance obligations.

## R1 — First issued input and retained scope

`prepareC(issued, mapping, origin)` accepts the actual public Request context,
the canonical mapping snapshot resolved for that first issue and the genuine
origin executor descriptor. The current protocol and four finite thinking modes
analysis, design, implementation and review are accepted. Native handle, run,
continuation and expected revision are required. CNCF converts the unchanged
issued executionRequirement and consumes its disposition; pending work is never
passed back to first-issue `mapping.resolveC`. Unmapped invocation, Human, Wait
or Terminal is not a successful mapped execution.

The Prepared value retains the original public context and host Session. The
public scope, required result type and completion route stay with the issued
request. Concrete profiles and observed attempts belong to the host's existing
logical/task resource binding, outside the Goal business Plan and Workflow
storage. The host retains this Session at first issue for redisplay and restart.
A missing original session or mapping snapshot is a gap; a newer configuration
cannot reconstruct a pending selection.

## R2 — Operational sequence and honest outcomes

1. Obtain the actual issued scope and original canonical configuration through
   the existing host entry. Prepare and retain the host Session once.
2. Consume `session.nextSelection`. Perform only actually available and admitted
   INLINE work or the configured independently scoped executor through existing
   host tools. INLINE needs no mandatory child. Provider/model names alone grant
   no capability, availability or permission.
3. `Prepared.recordC(selection,outcome)` delegates to CNCF using an actual
   observed outcome. Record honest detail and only observed resources before
   selecting any configured alternate. UNAVAILABLE/ProviderUnavailable and
   DECLINED/CapabilityDeclined permit the one explicitly configured alternate
   only after the first DELEGATED attempt. BLOCKED/AuthorityBlocked or
   ResourceBlocked and FAILED/SemanticFailure or UncertainExecution stop.
   Host transport/callback uncertainty is FAILED/UncertainExecution. Exhaustion
   and INLINE unavailability stop. No retry or placement escalation exists.
4. Only final operational COMPLETED with an actual matching executor can supply
   completion evidence to the ordinary semantic Submit. Submit still converts
   the typed semantic payload and owns the saved requirement, evidence, CAS and
   independent review admission. Its actual accepted/message/outcome controls
   whether the host separately invokes explicit Advance. Rejection or uncertainty
   cannot trigger success-path Advance.

Missing metrics remain unknown: elapsedMillis, inputTokens, outputTokens,
monetaryCost with paired currency, and machineIdentity are optional observations,
not estimates, inferred zero/cost, benchmarks or permission limits. CNCF enforces
nonnegative known numbers and concrete profile/context relations. UNAVAILABLE
has no executor; COMPLETED requires the actual executor. Other noncompleted
outcomes may omit it, but any supplied descriptor must match the selected work.
COMPLETED says work returned, not that its semantic result was accepted.

## R3 — Completion evidence and independent admission

`completionEvidenceC(references)` requires genuinely supplied artifact references
and final COMPLETED. It projects existing `WorkflowExecutionEvidence` with the
actual executor context as workerIdentity, actual model as modelIdentity and
existing `WorkflowExecutionObservation`. It creates no business result, Human
answer, reviewer evidence, authentication, consent or effect authority. Required
review must still be independently prepared and admitted through its existing
host review path, even after execution completion.

The assigned semantic worker returns only its requested meaning and normal typed
result. Provider DECLINED, BLOCKED or uncertainty returns control to the host
without a fabricated completed or decision-needed payload, Submit or eager
Advance. A valid semantic decision-needed payload retains its ordinary result
contract and genuine independent Human channel.

## R4 — Restoration contract

The host uses CNCF `encodeC`/`decodeC` for its versioned
`cncf.workflow-execution-harness.v1` artifact, then
`restoreC(originalIssued, decodedSession)`. The bridge checks the consumed typed
work reference and execution requirement for correlation/contradictions,
including contradictory declaration/wire handle fields. It does not compare a
full public/private WorkOrder body or create an immutability certificate.
Unrelated diagnostics grant no authority. Restoration never consults a later
mapping or reads runtime storage. Retained sessions keep their original concrete
selection while new issued continuations use their own first-issue snapshot.

## R5 — Existing ownership and capability limits

The host owns tools, retention, observations and explicit caller control.
CNCF owns generic profile/attempt/resource/serialization contracts. The bridge
only projects the actual public issue and completion evidence. Goal policy,
semantic results, mechanical commands, native revision/CAS, review, Human,
commit/repository effects and completion remain with their existing owners.
Missing host/provider binding does not authorize a replacement engine, new
request, generic Process fallback, new service/main or fabricated availability.
Command remains the primary operational route; the client retains its adopted
lightweight connection scope. Split review/full-suite ownership remains unchanged.

## R6 — Three generated public values and two normal Operations

The existing `NativeHostPreparationService` adds two passive COMMAND Operations,
`prepareExecution` and `recordExecution`. Each has exactly one named `input` of
the corresponding generated value type and returns `WorkflowHostExecutionState`
as a direct structured JSON object. The six importer Operations retain their
existing `WorkflowPreparedImport` output and behavior. These conversions perform
no provider IO, import/save, Workflow Start, Submit or Advance.

| Value | Fields in declared order |
| --- | --- |
| `WorkflowHostExecutionPrepareInput` (COMMAND) | required `issuedWorkContext: WorkflowIssuedWorkContext`, `mapping: string`, `origin: string` |
| `WorkflowHostExecutionRecordInput` (COMMAND) | required `issuedWorkContext: WorkflowIssuedWorkContext`, `session: string`, `observation: string`; optional `reference: string` |
| `WorkflowHostExecutionState` | required `session: string`; optional `nextSelection: string`, `completionEvidence: WorkflowExecutionEvidence` |

The issued DTO is inline structured data from the actual Request. Input file
Strings are exact absolute local `file:` URIs: mapping is the original canonical
`cncf.workflow-execution-profile-mapping.v1` snapshot; origin is the existing
`sm-workflow.execution-origin.v1` wrapper. Preparation consumes its `current`
descriptor without re-resolving the wrapper's preference for already issued work.
Record's session URI selects the explicit previously retained artifact;
observation selects one flat CNCF attempt JSON containing its supplied ordinal
and concrete profile. A supplied selection must match the retained Session's
current selection; the helper never substitutes `Session.nextSelection` for it.

## R7 — Current reader, wire aliases and opaque retention

`NativeHostExecutionInput` borrows only the normal executing call's
`String => Consequence[String]` reader. All required and optional URI metadata
validates before acquisition. Prepare reads mapping then origin. Record reads
Session then observation and restores the Session against the actual issued DTO;
it reads one reference only for COMPLETED. Existing CNCF current-resource roots,
provider classification, UTF-8, byte/stream caps and release remain the owners.
Configure narrow `textus.resource.url.file.roots` and
`textus.resource.read.max-bytes` before boot; URI metadata gives no read grant.
There is no application NIO reader, arbitrary public Map, storage writer or
provider engine.

The consumed requirement aliases are `reviewRequired`/`review_required`,
`reasoningClass`/`reasoning_class`, and nested
`originContextIdentity`/`origin_context_identity`. Equal values agree;
contradictions reject before the strict issued decoder. Existing handle aliases
retain the same consumed-field rule. Unknown/missing/type errors remain errors;
generic context and concrete profile controls are never recursively renamed.

The response's `session` is opaque canonical
`cncf.workflow-execution-harness.v1` JSON **text**, and `nextSelection`, when
present, is opaque canonical `encodeSelectionC` JSON **text**. Retain these strings
unchanged outside business Workflow storage. Normal CLI recursive display naming
must not transform their contents. Only `completionEvidence` is ordinary generated
structured data. First-issue preparation requires the original snapshot; restore
requires the explicit retained Session and never reads a later mapping. Missing
original artifacts do not authorize rediscovery, re-preparation or migration.

## R8 — Record outcome and separate semantic completion

COMPLETED requires `reference` to select one existing generated
`WorkflowArtifactReference` JSON. Its generated ValueReader and the bridge create
completion evidence only after CNCF admits the supplied selected attempt and
actual matching descriptor. Noncompleted outcomes forbid a supplied reference
and emit no completionEvidence. Pending and first UNAVAILABLE/DECLINED Sessions
emit their allowed finite nextSelection; blocked, failed, uncertain, exhausted
or INLINE-unavailable Sessions emit no retry or success. Resource unknowns stay
unknown under R2; no metrics or provider availability are inferred.

Operational completion is separate from business acceptance. A caller supplies
the actual returned completionEvidence with its compatible semantic result and
genuine reference to the existing named Submit. It checks that Submit's actual
accepted/message/outcome before a separate explicit Advance. This passive API
certifies no reviewer, operator consent, authenticated provider, effect authority
or semantic success. Newly issued Repository native conflict, independent native
merge review and affected-file native merge repair use explicit classifications
and canonical mapping/origin described in
[Repository native execution](repository-sync-native-execution.md). The existing
review/repair pair remains required when the key is present; additive optional
conflict must be explicit CODING for a fresh mapped conflict. Omission preserves
unmapped conflict behavior, while explicit null or malformed input rejects.
The exact fresh conflict missing-issued-row branch binds once; pending
issues and completions retain their original requirement and host Session;
new configuration binds only a genuinely new issue. Missing mapped classes or
dispositions still reject. Fresh Split partition/scope DESIGN and reasoning
ANALYSIS now consume the explicit finite input in
[Split native execution](split-phase-native-execution.md). Their inherited
independent-review obligation stays intact; saved issues and retained Sessions
are reused through normal completion even after mapping replacement. Human
and other unbound Repository operations retain their original obligations. A
conflict decision-needed result uses its real separate Human channel, whose
response has no issued work context. This binding installs no provider.

## R9 — Ordinary command and caller loop

Select complete concrete input files yourself from the actual Request and
explicit artifacts. With the existing normal host launcher, the two commands are:

```sh
"$primary/bin/launcher" \
  --classpath-file "$primary/target/cncf.d/runtime-classpath.txt" \
  --main-class org.goldenport.cncf.CncfMain -- --force-exit \
  --component-dev-dir "$primary" \
  --textus.assembly.descriptor="$host/.textus/assembly.yaml" \
  command org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareExecution \
  --input "$(cat "$task/prepare-execution-input.json")" --json

"$primary/bin/launcher" \
  --classpath-file "$primary/target/cncf.d/runtime-classpath.txt" \
  --main-class org.goldenport.cncf.CncfMain -- --force-exit \
  --component-dev-dir "$primary" \
  --textus.assembly.descriptor="$host/.textus/assembly.yaml" \
  command org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.recordExecution \
  --input "$(cat "$task/record-execution-input.json")" --json
```

The existing finite `native-goal-command.py` offers `prepare-execution` with
`request,mapping,origin`, and `record-execution` with
`request,execution-session,observation` plus optional `reference`, in addition to
the shared primary/host/task/action/output options. It checks the retained outer
Goal ID against Request.sessionId, accepts the existing Goal-owned Repository
review/repair/conflict child routes, and forwards the whole nonempty issued DTO unchanged.
The conflict pair is exactly `submitRepositorySyncWorkResult` with
`RepositorySyncConflictResult`, with a nonblank issued child ID distinct from
the retained Goal ID. The same Request kind, outer ID and consumed alias checks
apply before dispatch; no conflict body or profile is inferred.
Consumed `issuedWorkContext`/`issued_work_context`,
`nextSelection`/`next_selection`, and
`completionEvidence`/`completion_evidence` aliases must agree. No family or target
selector is added; native mapped input still owns applicability.

The same finite actions accept retained `SplitPhase` routing for only these
exact action/operation/type pairs from the actual selected Request:

| Semantic action | Issued operation | Issued result type |
| --- | --- | --- |
| `submit-split-partition` | `submitPartitionSemanticResult` | `SplitPhasePartitionSemanticResult` |
| `submit-split-scope` | `submitHandoffScopeResult` | `SplitPhaseScopeSemanticResult` |
| `submit-split-reasoning` | `submitReasoningResult` | `SplitPhaseReasoningSemanticResult` |

For Split, both Request.sessionId and submitSessionId must equal the retained
Split ID, the kind must be Request, and all consumed aliases must agree. The
whole nonempty issuedWorkContext remains opaque; no business body, classification,
profile or outcome is inferred. Cross-family pairs, Goal/Repository children
under Split, Split pairs under Goal, or crossed IDs refuse before launch/output.
The route file has only family/sessionId and accepts the three closed families
GoalPhase, SplitPhase and RepositorySync;
original Goal actions keep their Goal-only boundary. The passive calls still
invoke only fixed prepareExecution/recordExecution with the explicit artifacts.
Record selects the original retained Session even after mapping changes.

The [finite caller contract](native-goal-command-task.md) covers separate
`prepare-split-input`, `start-split`, `advance-split` and the three semantic
actions. Passive Split Start preparation follows
[native Start preparation](native-host-start-preparation.md) and the
[normal preparation contract](normal-host-preparation-operations.md), including
the current generated input's required participant among its nine fields.
It grants no storage, Start or consent. Split semantic metadata requires object
result with only optional completionFacts/executionEvidence and rejects
resultReference because native owns its assigned destination. Native Human,
effects, independent review and Preview/Apply remain separate under the
[common loop](common-workflow-operation-loop.md); no helper performs them.

Each action makes one ordinary call in a fresh selected output directory. Along
with `response.json` and `stderr.log`, it retains the actual Session string as
`execution-session.json`, optional selection text as `execution-selection.json`,
and optional structured evidence as `execution-evidence.json`. Record explicitly
selects the preceding call's `execution-session.json` through
`--execution-session`; it never scans candidates, reconstructs a missing Session,
overwrites output, chooses an attempt/provider, auto-Submits or auto-Advances.
Failure/transport uncertainty retains raw streams without promoting completion.
The opaque Session is no business session ID or accepted Submit.

For conflict completion, select that original retained Session when recording
the actual observation, even after configuration changes. The finite
`submit-repository-conflict` action forwards the supplied result/reference and
optional facts/evidence once to the issued Repository child and checks its actual
acknowledgement. Submit retains only a proposal; a separate explicit Advance
owns effects/checks or genuine Human continuation and newly issued independent
review. Prepare/record perform none of those steps.

## R12 — Standalone Repository caller and retained execution

The historical `native-goal-command.py` filename also covers standalone
Repository. The fixed ordinary CncfMain bootstrap, full component FQN,
primary/host/task/action/output pairs, owned directories and exclusive outputs
remain the same. `start-repository` requires input and permits optional proposal;
it forwards the whole strict input object text and supplied `--sessionId` proposal
to `org.simplemodeling.textus.SmWorkflow.RepositorySyncService.startRepositorySync`.
Require actual `outcome=started`, nonblank message and nonblank agreed returned
ID. Exclusively retain `{"family":"RepositorySync","sessionId":"<actual ID>"}`
even if the proposal differs or was omitted. Occupied or concurrently occupied
routing is never overwritten. The regular UTF-8/O_NOFOLLOW route reader accepts
exactly family/sessionId and only GoalPhase, SplitPhase or RepositorySync.
Original Goal and Split actions keep their exact family checks.

`advance-repository` has no additional action options and rereads the saved
Repository route for fixed `RepositorySyncService.advanceRepositorySync` with
the actual retained ID. Require the same actual response ID, nonblank message
and kind Request, Human, Wait or Terminal. All three Advance actions have the
fixed 240-second outer bound; all other actions have 60 seconds. No public family,
operation, service, target or timeout selector is introduced.

Three existing semantic actions accept only these exact Repository pairs:

| Action | Fixed Repository operation | Issued result type |
| --- | --- | --- |
| `submit-repository-review` | `submitNativeMergeReview` | `RepositorySyncNativeMergeReviewResult` |
| `submit-repository-repair` | `submitNativeMergeRepair` | `RepositorySyncNativeMergeRepairResult` |
| `submit-repository-conflict` | `submitRepositorySyncWorkResult` | `RepositorySyncConflictResult` |

Standalone Request.sessionId and submitSessionId both equal the saved Repository
ID. Under GoalPhase, Request.sessionId equals the saved Goal ID while the issued
nonblank child submitSessionId must differ. Split refuses Repository pairs.
Require Request kind and agreeing consumed declaration/snake aliases. Each action
still requires exactly request/submission files. Forward supplied object result
and required object resultReference, with only optional array completionFacts
and object executionEvidence. Require the fixed target's actual acknowledgement
ID, Boolean accepted=true, outcome=accepted and nonblank message. Bodies remain
opaque; Submit supplies no effects, retry, authority or automatic Advance.

`prepare-repository-review-input` uses the same fixed review route guard. Saved
family alone selects RepositorySync for standalone or GoalPhase for a child;
kind remains review. One passive normal
`NativeHostPreparationService.prepareEvidence` call carries whole selected
existing request/result/reference/executionEvidence file URIs and explicit
issuer/occurredAt/strict collection. The caller parses no business body or
hostContext. Success requires a datastore array and retains raw streams without
changing route, configuration or input. It performs no provisioning, review,
consent, Submit or Advance. Original prepare-review-input defaults to GoalPhase;
prepare-repository-input remains passive and routing-independent.

Shared prepare-execution/record-execution admit standalone Repository only for
those three pairs and the same retained request/submit ID. They preserve Goal
own work, three distinct Goal children and three same-ID Split pairs. Forward
the whole nonempty issuedWorkContext unchanged with agreeing consumed aliases.
Prepare selects explicit mapping/origin URIs; record selects the explicit
original retained execution-session, actual observation and optional reference.
The fixed preparation service, opaque Session/selection retention and structured
evidence alias guards remain unchanged. A later mapping cannot reconstruct the
pending Session; these actions execute no provider, infer no profile and
automatically Submit or Advance no result.

Every action makes one normal call. Nonzero exit, timeout, malformed/nonobject/
duplicate/nonfinite/bounded output or uncertain acknowledgement retains raw
streams and stops without retry/progression. The current UoW owns Git/SBT/file/
HTTP effects and checks, direct normal binaries and native SBT locks; all nine
retired main entry points remain retired. There is no application Process fallback
or global product serialization. Genuine Human channels, effects, consent and
independent review keep their native owners. Command stays primary and client
qualification stays light. R1–R9 above retain their original responsibilities.

## Executable specifications

`NativeWorkflowHostExecutionSpec` E1 covers all four generated public scopes,
generic artifact restoration and actual completion projection; E2 covers
missing/mismatched native references and unmapped/unsupported input; E3 covers
noncompleted outcomes; E4 covers actual descriptor relations, references and
inert diagnostics. These are specification-owned observations.

`NativeHostExecutionOperationSpec` N1–N4 use the configured normal service and
actual current file reader for four scopes, declared/snake aliases, opaque state,
unavailable/declined alternate completion, stopped/exhausted outcomes, consumed
contradictions, reference metadata and root/byte-cap denial.
`NativeHostExecutionCliSpec` C1–C2 use independent ordinary CncfMain processes
for actual Start/task retention/Advance, prepare/record, normal semantic Submit
acknowledgement and pending redisplay/restoration despite later configuration.
`test_native_goal_command.py` E25–E29 cover finite typed forwarding, explicit
artifact retention, aliases/task correlation and native uncertainty/refusal;
E25/E26 include Goal/review/repair/conflict routes and agreeing route aliases.
E30–E33 cover one-call conflict Submit, exact child acknowledgement and refusal
of crossed routes, malformed/bounded responses, options and selected files.
E34–E40 add finite standalone Split forwarding: E34 nine-field passive
preparation, E35 canonical Start and four bounded Advance observations, E36
three exact semantic pairs with Split metadata, E37 all three mapped execution
routes/aliases with whole context and explicit original Session, E38 closed
family/issued-route refusals, E39 raw-stream retention on uncertainty and E40
exact option/file/metadata admission. The original E25/E26 Goal/review/repair/
conflict cases and earlier assertions remain required. Adjacent Given/When/Then
and executable expectations describe obligations, not validation results.
Their mocked subprocess observations establish caller specification coverage;
actual installed provider execution, task delivery and genuine two-chat work
require their own evidence.
E41–E47 add exactly seven standalone Repository caller methods while preserving
all 58 earlier methods and their assertions. Each has adjacent Given/When/Then
and mocks only caller subprocess.run. Their R12 mapping is:

| Example | Observable caller obligation |
| --- | --- |
| E41 | Canonical Start/proposals/ID aliases, occupied/raced routing and four explicit 240-second Advance kinds |
| E42 | Three same-ID semantic pairs/aliases, opaque required result/reference and absent/present optional metadata with canonical acknowledgement |
| E43 | Saved standalone/Goal-child review family and whole explicit URI metadata, without input/configuration/routing changes |
| E44 | Three execution pairs/aliases, whole context, original Session after mapping changes and opaque returned artifacts |
| E45 | Crossed family/session/child/operation/type/kind/context and contradictory aliases refuse before launch/output |
| E46 | All lifecycle/Submit/review/prepare/record actions retain streams on transport, strict/bounded response or acknowledgement uncertainty without retry/progression |
| E47 | Exact options, strict collection, explicit paths, required resultReference, metadata types and unknown fields refuse before launch/output |

These mocked tests establish finite caller behavior only when validated. They
claim no successful installed/provider/task/two-chat work or Step/Phase acceptance.

`SmWorkflowGeneratedHelpSpec` retains the eight-operation service catalog and
ordinary generated input/output descriptions. Their supplied executors and
outcomes establish no installed provider or genuine user chat acceptance.

The existing `ThreeWorkflowGoalOperationSpec` mapped loop connects E7 first
Routine INLINE, E8 retained pending selection/new configuration and nonprogressing
operational outcomes, and E9 first ReviewStep unavailable-to-alternate completion
to actual independent host review preparation and normal public Submit/explicit
Advance. Its fixtures keep semantic/Human/repair/closure and typed Git/SBT owners.
These examples qualify neither installed providers nor genuine two-chat work.

`RepositorySyncNativeExecutionSpec` E1–E4 cover explicit paired Component
classification and finite fresh binding/refusal. The E5 `mapped repository
execution` example in `ThreeWorkflowRepositoryOperationSpec` connects actual
stored review/public context, normal host preparation, retained Session
restoration after changed configuration, observed completion, separately
prepared review evidence and normal Submit/explicit Advance. E6 in
`RepositorySyncNativeMergeRepairContinuationSpec` connects the original mapped
review, actual INLINE repair, proposal-only Submit, separate application/fresh
ordinary SBT and newly mapped independent review. These remain owned local
Git/SQLite and specification executor scenarios, with no installed provider,
real chat or all-family acceptance claim.

The same adapter's E7 adds optional conflict shape and strict CODING binding,
old-pair compatibility and missing mapped conflict/class refusal. Repository E8
extends the existing public Human-preparation E6 with exactly two mapped conflict
execution variants, resolved and decision-needed, alongside all three original
directions. Each retains the first actual public conflict's canonical Session,
reopens under changed origin/profile/classification without the old coding class,
repeats pending Advance and issued-row reads, and restores the original Session
through CNCF encode/decode. Missing or crossed original-origin observations refuse
without native/file/SBT progression. Valid Submit retains only the proposal;
separate Advance owns materialization and ordinary typed UoW SBT, or the genuine
separate Human question and specification-owned evidenced answer. Each newly
issued ReviewCritical prepares its own later DELEGATED/REQUIRED Session, records
a separately scoped executor and supplies its own independent host review
evidence before normal Submit and separate convergence. These authored programs
await parent validation; they claim no installed provider, genuine two-chat work
or Step/whole-Phase acceptance.

`SplitPhaseNativeExecutionSpec` E1–E4 and the `ThreeWorkflowDirectOperationSpec`
E5 partition / E6 scope-reasoning programs are authored for parent validation.
The latter preserve all legacy directions and add one mapped variant per program,
using actual saved issues, first-public-context host Sessions and completed
specification-owned observations with genuine typed Submit. Partition reopens
under valid changed mapping/origin without its original DesignStandard class,
restores the retained original Session, and retains the compatible presentation
completion observer. Missing/crossed observations refuse before progression;
Submit and next Advance remain separate. No execution success or installed
provider/all-family/two-chat acceptance is claimed by authoring these examples.
