# SmWorkflow user guide

This development component provides the GoalPhase, SplitPhase and RepositorySync
Operation implementations through the normal component factory. Phase 2 verifies
them with direct Operation calls in Executable Specifications, including actual
managed effects and durable reopening. Command/CLI wiring used by skills and
real skill integration belong to Phase 6. The component is not Phase-accepted or
release-ready while integrated tests, review and closing remain incomplete.

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
`docs/spec/normal-three-workflow-host.md` for the fifteen provided Operations,
their complete wire codecs and host configuration. A declared Required SPI
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

1. Calls `startSplitPhase` with the selected input; Start creates its own instance.
2. Registers the specification-owned partition context and calls
   `advanceSplitPhase`; Workflow issues the genuine handoff-scope WorkOrder.
3. Returns only the assigned `Enriched` scope result through
   `submitHandoffScopeResult`; the reduced input omits the saved result destination
   because Workflow resolves that assignment internally.
4. Lets Workflow issue any genuinely unknown reasoning task, returns only that
   assigned result, and advances to the complete read-only Preview.
5. Reopens a separate runtime/JVM to observe the retained work and final outcome,
   without reinstalling instances or repeating completion.

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
import org.goldenport.protocol.{Argument, Request}

val response = owner.executeOperationResponseInContext(
  Request.of(
    component = component.name,
    service = "SplitPhaseService",
    operation = "advanceSplitPhase",
    arguments = List(Argument("arg1", advanceWire))
  ),
  actor
)
```

`owner`, `component` and `actor` are the actual configured Subsystem, component
and resolved caller. `advanceWire` is the complete typed JSON command, not a
shell command. An unbound `ExecutionContext.create()` is not authentication or
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
issued state. Supply session context and only a request selector when actual
issued generations are ambiguous. The three Split semantic completions also
resolve their assigned destination; return `result.value` without a destination
echo. Other actual artifact/result/evidence references remain required business
data. A historical full-envelope client retains its original wire contract.
Never add a cursor, argv, winning candidate, storage plan or permission to an
advance command. Do not invent a human answer or accepted review.

Decode a successful `OperationResponse.Scalar` with the declared response codec.
Some completion Operations return an Operation-specific acknowledgement rather
than a Workflow Wait frame. An acknowledgement proves neither Terminal nor
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

## Reopen without repeating effects

Open a fresh configured runtime and use the same session context, adding a target
selector only for genuine ambiguity. Historical identity-based clients still use
their saved Workflow identity and original context. Do not reinstall instances
or fabricate history. Pending assigned work,
completed results and Terminal must be observed through the public interface.
An unchanged duplicate result may return its original saved acknowledgement;
a changed or crossed result must not create another completion.

Selected Git/document interruption scenarios continue from observed postimages
without repeating their effect. An unrecorded product-check result instead
remains indeterminate Wait without automatic replay. That safe Wait is not a
successful check, rollback proof or convergence. Preserve uncertainty and use
supported observation; do not turn authentication or a Human answer into new
execution authority.

## Limits and next ownership

Integrated consumer validation and independent acceptance determine the actual
Phase 2 delivery, not this guide or isolated green tests. This guide does not
claim release acceptance, arbitrary uncertain-effect recovery or every possible
interruption permutation.

Phase 5 owns semantic runtime mapping/reference observations. Phase 6 owns
command/CLI skill wiring, installable `sm-*` clients and actual interaction/result
relay. Phase 7 owns workspace-wide synchronization. Phase 8 owns broader
provider/event/transport/concurrency/restart and comparative AI-cost evaluation.
Moving those obligations preserves them; it neither accepts them nor makes a
separate deployed client a Phase 2 prerequisite.
