# SmWorkflow reference manual

SmWorkflow is a Textus component for software-development workflows. Workflow
owns progression and deterministic processing. An AI client performs only the
semantic task in an issued WorkOrder and returns its typed result to Workflow.
The component is in development. Its normal factory composes all three
Workflow Operation implementations; integrated Phase acceptance and release
remain incomplete. Phase 2 calls Operations directly from Executable
Specifications. Command/CLI skill wiring belongs to Phase 6.

Start with the [user guide](user-guide.md). The
[SplitPhase scope contract](split-phase-scope.md) describes the separate handoff
scope task and its result boundary. The [Human direction contract](split-phase-human.md)
explains answer verification, retained observation and the current terminal limits.
These are authored manual sources; packaged
Help navigation and runtime routes still require verification.

## Component identity

The component name is `org.simplemodeling.textus.SmWorkflow`, JVM package
`org.simplemodeling.textus.smworkflow`, artifact `textus-sm-workflow`, and current
development version `0.1.0-SNAPSHOT`. Component discovery and operation dispatch
belong to the CNCF launcher; this project does not provide a second workflow CLI.

## Runtime availability

| Workflow | Current binding | Important limit |
| --- | --- | --- |
| GoalPhaseWorkflow | Normal factory composes ordered work/result/decision and whole-Phase closure, with managed validation, review, repair and Git effects. | Selected connected scenarios pass, but full regression/review remain open. Host policies, permissions and separately supplied evidence are required; a closed Step is not Phase completion. |
| SplitPhaseWorkflow | Normal factory binds Start, planning/scope/reasoning/Human completion, selected Preview and runtime-owned Apply. | Selected direct Operation/reopen cases pass. Completed selected work is protected before first Apply and partial-child resume, verified by bounded tests/review. Document interference and uncertain effects must not be treated as successful application; whole-regression/Phase acceptance remain open. |
| RepositorySyncWorkflow | Normal factory binds selected Start through real managed Git integration/conflict/review/validation and non-force convergence. | Selected connected/recovery scenarios pass on owned repositories/remotes; arbitrary commands, caller-made consent and replay of unknown checks are not supported. |

A generated operation declaration alone does not install a handler. CLI and MCP
clients must use the operations actually published by their configured host,
not assume every operation in the CML is callable.

## Workflow and AI responsibilities

Workflow retains the original input, current instance, issued work, typed result
and execution evidence. Its owning consumers enforce actual target/type relations,
state, authenticated actor, scope and update conflicts where the action needs them,
not a blanket unchanged-input proof. It must not turn a passive
stored planning snapshot into executable authority merely by decoding it.

The design assigns calibration, candidate enumeration, packing checks, identity
handling, document projection and managed external commands to Workflow.
`advance` is intended to absorb mechanical transitions and return only the next
semantic task. Reduced AI invocations and client round trips are explicit design
goals, not a measured performance claim for this incomplete runtime.

An AI skill may add only the unknown inputs requested by its issued task. It
must not choose the next operation or state, run Git/SBT, access runtime storage,
invent authority, overwrite known estimates, or substitute a human decision.
The distributed names are `sm-goal-phase`, `sm-split-phase` and
`sm-repository-sync`; their sources do not invoke legacy `cncf-*` workflows.
Production installation and current-contract end-to-end skill acceptance remain
separate unfinished work. Existing legacy skills are not replaced.

## Operations and results

Start requires a configured host's independently admitted user selection; JSON
input does not prove consent. A WorkOrder identifies the semantic request,
execution requirement, snapshot and completion operation. The client returns
the result through that assigned completion operation. Workflow supplies internal
controls from actual issued state; the client does not reproduce them as proof.
A Decision requires the host's independent human channel.

### Session input under development

The normal factory has session routes for all three Start/advance families with
scoped direct Operation validation; full connected acceptance remains open.
An actual invocation session requires no duplicate field.
Otherwise supply `sessionIdentity` in a `sm-workflow.session-operation.v1` object.
Start also supplies `input` as the business command object or its existing encoded
string. Advance needs no instance/revision/snapshot/context echo. Several actual
targets need only `instanceIdentity`; Split needs `contextIdentity` only when
several owned partition contexts exist. Workflow never chooses the newest row.

Reduced `sm-workflow.result-submission.v1` input retains actual `result.value`
and consumed facts/evidence. Partition, handoff-scope and reasoning semantic
completion resolve the result destination from the actual saved WorkOrder;
omit `result.reference` rather than return a comparison copy. Obsolete destination
echoes on these three reduced paths are ignored. Other completion Operations still
need the actual result/artifact or independently supplied evidence reference in
`result.reference`; Workflow does not invent it. Its `sessionIdentity` is optional
when supplied by the actual host. `requestIdentity` is necessary only when several
issued generations can explain the answer, including old completed generations;
a sole pending request alone does not prove an answer is for that generation.
Supported historical identity-only/full-envelope input remains available.

Association is retained in the native initial instance row, not a separate
post-Start index. Allocation alone is not successful Start and grants no effect
authority. Legacy rows without association are not automatically rebound. Repeated
Start/legacy association behavior and full connected validation remain open.
These Operation shapes do not claim Phase6 CLI or skill integration.

WAIT and a completion acknowledgement are nonterminal. A semantic completion
does not by itself prove document application, Git execution or whole-workflow
success. Interpret Terminal only under the corresponding implemented operation
and its verified effects. Do not turn an unavailable downstream handler into a
successful WAIT or Terminal.

For the current SplitPhase scope route, consult the
[operation and payload contract](split-phase-scope.md); it is distinct from
partition-estimate and boundary judgments.

## Host configuration

Storage is configured and owned by the host Subsystem. Workflow borrows its
canonical DataStoreSpace and initialized provider rather than opening another
SQLite pool. Store names, collection declarations and policies do not grant
actor authority or mean that all events are durable. Event persistence follows
the EventStore's event-kind policy.

The normal three-workflow factory requires trusted host definitions, the common
Workflow storage pair and each configured purpose-specific record destination.
The parameter groups below are part of that configuration, not a complete
definition or a recipe for the old Goal-only default factory:

- `sm-workflow.goal-phase.persistence.`: `store-name`, `store-identity`,
  `migration-policy`, `retention-policy`, `lease-policy`, `instance-collection`
  and `continuation-collection`. The two collections must be distinct.
- `sm-workflow.host.definition`: the closed typed three-workflow host definition
  containing the fixed repository target, Goal input/candidate/check/review/
  closure policies and RepositorySync input/check/conflict/review policies.
- `sm-workflow.host.evidence.`: declared logical store, evidence collection and
  trusted issuer for separately supplied evidence; the record codec does not
  issue consent or review acceptance.

These groups are not a complete host configuration example. Record readers,
artifact access, authentication and collection permissions also require the
host's admitted declarations. Do not guess missing policy values or use a
configuration string to claim human or command authority.
The normal connection also requires `sm-workflow.host.definition`, host evidence
and all selected SplitPhase/RepositorySync read/allocation/checkpoint settings.
See the repository's `docs/spec/normal-three-workflow-host.md` for the closed
host-definition and fifteen provided Operation input/response contracts.

## Failure and recovery

The three public advance decoders consume their declared schema and actual
instance/context reference fields. Unused diagnostics and extra stage, cursor,
argv, receipt or permission-looking fields are ignored, never accepted as
controls. Missing/wrongly typed required fields or incompatible schemas fail
conversion. Logical references stay exact without trimming or speculative
native-key admission; the owning Operation resolves the actual saved Start,
instance, context and participant before progressing. Formatting or ignored
diagnostics do not impose a separate application command-codec byte limit;
actual transport/runtime resource policy and physical storage-key bounds are
owned by their consuming boundaries, not waived by decoding data.

Each actual input or transition boundary admits the execution definition,
caller permission, target scope, possible state and update conflict once where
needed. Missing configuration, unresolved or incompatible executable targets,
foreign actors, crossed scope, impossible transitions and CAS conflicts must
fail at that boundary. A stored reference selects data; it is neither authority
nor proof that the data has remained unchanged. Diagnostic or presentation
changes do not require whole saved-body, declaration or regenerated-output
comparisons. Checks already completed at the owning boundary are not repeated
merely under an authority or state-verification label.

For an already issued scope task, a retained advance observes the original
issued work rather than allocating another task. Retained completion must be
checked against canonical native history and its original checkpoint.
Older SplitPhase data, including `split-phase-public-advance-v5` and
`split-phase-human-terminal-v8` and `split-phase-reasoning-demand-v9`, is not silently rebound to the current
`split-phase-selected-application-v12` definition. The additive planning assembly
installs paired native reasoning issue/result/successor handlers. The normal
factory now selects the native three-workflow connection; declaration alone
still does not grant execution or prove a supported outcome.
The reasoning request carries the Workflow-assigned result identity/revision.
Normal reduced submission resolves it internally; only a historical full-envelope
client copies that reference. Neither client selects a destination or replacement
planning/demand reference. The application
request uses its closed v2 schema without a v1 fallback.

The separately assembled Human profile introduced in v7 has actual public answer completion,
not merely declarations. Fresh answers require an independent host verifier;
authentication alone is insufficient. Defer and Cancel have typed terminal
paths. ProvideResolution completion retains the independently verified answer;
subsequent paired-host advance observes the actual resolution and continues the
whole frontier through planning/reasoning to selected Preview. The acknowledgement
itself does not apply documents or complete the split. Selected APPLY has additive
public-host intent/effect/completion and reopen evidence, with runtime-owned
capability configuration. Normal factory composition includes the paired native
handlers. That does not claim arbitrary document-conflict semantic/Human recovery
or Phase acceptance; see the scope contract for supported behavior and limits. See the
[Human direction contract](split-phase-human.md) for these distinctions.

An indeterminate commit/readback is not rejection proof or success proof. Report
uncertainty and use the host's supported observation/recovery path; do not
automatically resubmit a completion or launch commands from the AI client.

## Help and transport status

The framework conventions include component Help/manual navigation,
`/openapi.json` and `/mcp`. The project's static Web form declaration is not
proof that those routes or a workflow handler work. Resolve selectors and
endpoints from the configured host. Help, manual links, OpenAPI and MCP must be
verified through their actual runtime transports before release; MCP is not
assumed to be an ordinary GET page.
