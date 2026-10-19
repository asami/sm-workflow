# SmWorkflow user guide

This development component provides the GoalPhase, SplitPhase and RepositorySync
Operation implementations through the normal component factory. Executable
Specifications call Operations directly, including normal managed effects.
Phase 5 is formally released; Phase 5.1 verifies these normal paths. Command/CLI
wiring and real skill integration belong to Phase 6. Duplicate answers and
competing submissions are the caller's responsibility. Intermediate recovery,
identical replay responses and complete runtime-history preservation are not
promised. Abandon unsuccessful work and start a NEW execution from the beginning;
this does not clean up or restore uncertain effects of the abandoned execution.

## Prepare the owning runtime

The owning Textus Subsystem initializes its shared storage and process facilities
before constructing the component. Supply trusted host configuration, the normal
storage plan, purpose-specific record destinations, repository/check policies
and bounded process grants. Component construction does not create a second
database pool, seed Workflow instances or grant execution permission.

Use the official development CAR/runtime artifacts. In this workstation's
development workflow, SBT runs through the registered command runner and shared
serial wrapper. The developer preparation task is not an Operation or an AI
semantic skill's responsibility. Runtime preparation and package generation do
not prove that a Workflow completed or that human/review evidence exists.

See [the reference manual](index.md) and the repository's
`docs/spec/normal-three-workflow-host.md` for the seventeen provided Operations,
their named business types and host configuration. A declared Required SPI
does not become an additional provided Operation merely because Help describes
it.

## First successful Phase 2 example

In a source checkout, use the existing
[normal host configuration fixture](../../../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowNormalHostFixture.scala)
and [direct Operation specification](../../../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowDirectOperationSpec.scala).
These are repository-only source links, not promised packaged manual routes or
a production input issuer. Select the existing example
`E12 session result consumption: continue actual scope through enriched and any assigned reasoning to its complete outcome`
through the registered serial test runner. Do not run SBT directly or substitute
its separate-process observation entry as a public client.

The example supplies owned input records through declared managed storage,
initializes shared storage and the normal three-service bindings, then:

1. Calls `startSplitPhase` with named selected business input; Start creates the
   execution and its owning context, and returns sessionId.
2. Calls `advanceSplitPhase` with that sessionId; Workflow returns the actual
   handoff-scope business request.
3. Returns only the assigned `Enriched` scope result through
   `submitHandoffScopeResult`; the reduced input omits the saved result destination
   because Workflow resolves that assignment internally.
4. Lets Workflow issue any genuinely unknown reasoning task, returns only that
   assigned result, and advances to the complete read-only Preview.
5. Receives the normal terminal Preview without repeating completion.

The successful result is the selected Preview with its source/child projection,
not merely the Start or completion acknowledgement; the example does not write
Phase documents. The same specification's normal APPLY example separately
declares the managed document capability and checks actual changed files and
reopening. Scope/reasoning Human examples label their supplied evidence as test
data. A selected successful example does not override the failed integrated
regression or prove every interruption or a production Human channel.

For a refusal, first inspect the actual error against the trusted host definition,
collection declarations, ingress caller and issued result references. Fix the
specified input/configuration mismatch; do not infer permission from JSON or
retry a possibly completed effect. Preserve indeterminate outcomes for supported
observation rather than treating them as successful checks.

## Call an Operation directly

Use the configured Subsystem's Operation dispatcher and an ingress-resolved
caller. Do not substitute a private Workflow runtime or call an admission helper
as the external interface. This is the direct call exercised by the specs:

```scala
import org.goldenport.protocol.{Property, Request}

val response = owner.executeOperationResponseInContext(
  Request.of(
    component = component.name,
    service = "SplitPhaseService",
    operation = "advanceSplitPhase",
    properties = List(Property("sessionId", started.sessionId, None))
  ),
  actor
)
```

`owner`, `component` and `actor` are the actual configured Subsystem, component
and resolved caller. `started` is the declared Start result obtained through its
generated ValueReader from RecordResponse. Start supplies its declared business
Value or Record as the named `input` Property and may supply a proposed sessionId.
Advance always uses the actual returned sessionId, never a chat/ambiguity selector
or a Command envelope.
An unbound `ExecutionContext.create()` is not authentication or
an effect grant. Direct dispatch uses normal ActionCall, commit and after-commit
response processing without launching a CLI merely to invoke the Operation.

## Follow the issued result contract

Start admits the selected original input. Advance owns deterministic routing,
calculation, managed commands and saved progression. It returns the next
WorkOrder, Decision, Wait or Terminal instead of asking the caller to choose an
internal stage.

A semantic participant returns only the requested typed result to the completion
Operation identified in its issued work. With normal reduced submission, Workflow
resolves handle, run/continuation identity, revision and snapshot from the actual
issued state. Supply the actual sessionId and named typed result/answer.
The three Split semantic completions also
resolve their assigned destination; return the semantic business value without a destination
echo. Other actual artifact/result/evidence references remain required business
data. Semantic evidence is consumed once; it is not echoed in outer execution
evidence as a comparison copy. Actual independently supplied execution/Human/
reviewer evidence keeps its distinct purpose. No historical full-envelope client
contract is supported.
Never add a cursor, argv, winning candidate, storage plan or permission to an
advance command. Do not invent a human answer or accepted review.

Consume successful `OperationResponse.RecordResponse` with the generated
ValueReader for the declared response. Every Submit returns actual sessionId,
accepted, outcome and a truthful chat-oriented message, not a Workflow Wait
frame or native envelope. An acknowledgement proves neither Terminal nor
document application. The owning caller subsequently advances the Workflow;
the semantic skill does not inspect state and dispatch internal effect stages.

Phase 2 specs may supply explicitly test-owned input/review/Human evidence
through declared managed storage. Such fixtures are not a deployed Human
interaction channel or independent production reviewer. Encoding a JSON record
does not grant permission or attest consent.

## Practical routes

- SplitPhase progresses from selected Start through any required semantic scope,
  reasoning and Human results to complete Preview or authorized Apply. Preview
  is read-only. Apply uses the saved selected plan and Workflow-owned document
  capability; an occupied or conflicting document is not permission to overwrite
  it. See [scope](split-phase-scope.md) and [Human direction](split-phase-human.md).
- RepositorySync uses the selected repository and fixed upstream to observe
  equality/ahead/behind/divergence, preserve intended changes, integrate or issue
  conflict work, validate/review and converge through real non-force effects.
  The caller cannot supply arbitrary Git commands or substitute command success.
- GoalPhase executes the supplied bounded ordered Step plan, including required
  implementation, validation, review, repair, Step commit, whole-Phase checks and
  repository closure. Closing one Step does not complete the Phase. Ordered
  semantic Human Resume/Defer/Cancel is distinct from older Step-closure Human
  input and cannot refund repairs or authorize replay of an unknown effect.

The connected examples use specification-owned repositories, remotes and
documents. Actual managed Git/SBT/document effects remain necessary even though
the Operation caller is direct rather than a CLI process.

## Continue normal work or start a NEW execution

Open a fresh configured runtime and use the returned execution sessionId.
Removed ambiguity-selector, identity-only or full-envelope
interfaces are not alternatives. Do not reinstall instances
or fabricate history. Normal pending work and completed outcomes are consumed
through the public interface. Return each requested answer once; duplicate or
competing submissions are caller misuse, without an identical-response guarantee.

Intermediate Git/document application is not resumed from postimages, nor is
an uncertain product-check outcome treated as successful. Abandon unsuccessful
work and start a NEW execution from the beginning. This does not restore, clean
up or automatically retry uncertain effects of the abandoned execution. Actual
file scope, unrelated-file protection and command permission remain at their
effect owners; Human authentication is not new execution authority.

## Limits and next ownership

Phase 2 has its completed finite direct-Operation release. Phase 5.1's current
fixed normal-use/check-removal boundary has passed bounded Operation validation
and independent closure review. Its formal release without additional tests/review
is user-approved; native commit/closure outcome is recorded separately. This guide does
not promise arbitrary uncertain-effect recovery or interruption coverage.

Phase 5 owns semantic runtime mapping/reference observations. Phase 6 owns
command/CLI skill wiring, installable `sm-*` clients and actual interaction/result
relay. Phase 7 owns workspace-wide synchronization. Phase 8 owns broader
provider/event/transport/concurrency/restart and comparative AI-cost evaluation.
Moving those obligations preserves them; it neither accepts them nor makes a
separate deployed client a Phase 2 prerequisite.
