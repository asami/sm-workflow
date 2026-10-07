# Factory-time canonical parameter admission boundary

Goal State: IMPLEMENT investigation. Previous turn made progress with compiled
policy admission and 62 passing tests (attempt 483). No SBT session remains.
This turn re-read current main code and the canonical CNCF creation/resolution
implementation before selecting production launcher wiring.

## Observed current path

SmWorkflowPrimaryFactory.create_Core calls component_core, which assembles the
generated GoalPhaseService and StepReviewService placeholders. The explicit
GoalPhaseManagedComponentFactory binds the managed four-Operation Service in
create_Core, but requires an already prepared declaration and host ports.
No main-code configured selection/association/closure-authority store exists.

CNCF Component.Factory._create_participant_c currently performs:

1. create_Component(params).
2. create_Core(params, comp).
3. Derive participant-specific instance identity and shared core.
4. Apply instance application-config overlay.
5. Select component-owned descriptor and resolve canonical initialization
   parameters through ComponentParameterBootstrap.
6. initialize_component_c with those admitted parameters.

ComponentCreate.configuration publicly returns Subsystem.configuration only.
ComponentCreate.runtimeConfiguration and ComponentParameterBootstrap are
private[cncf]. Component.initializationParameters is initially empty. Thus an
external create_Core implementation cannot currently consume the same typed
parameter/provenance boundary used by normal initialization. Reading only
Subsystem.configuration would lose canonical packaged/assembly/instance/runtime/
test-overlay resolution. Reimplementing that resolver in sm-workflow is not the
desired architecture.

This issue is separate from DB-pool initialization. The managed Subsystem pool
must still be initialized before component construction. Parameter resolution
must remain pure and borrow no pool; service storage binding remains through
WorkflowComponentStorage and the owning Subsystem.

## Proposed bounded prerequisite, not yet implemented

Expose a factory-time canonical parameter-resolution capability in CNCF,
reusing ComponentParameterBootstrap and the existing descriptor selection.
Preserve existing create_Core timing, normal participant identity behavior,
required/optional/confidential decoding, nested/path routes and resolution
precedence. Do not expose raw runtime configuration or storage resources and
do not add a second resolver in the application. Define how the resulting
immutable parameters are reused or checked during ordinary initialization,
so service construction and component initialization cannot consume different
configuration/provenance facts.

Exact prospective upstream frontier: Component.scala factory composition and
a focused external-factory executable spec, using the existing parameter
bootstrap tests as a regression accumulator. This changes a protected external
factory contract and requires explicit direction before implementation. The
CNCF repository is already an admitted prerequisite; this is not permission
for new repositories, unrelated upstream features or public publication.

Acceptance must prove factory-time read equals normal initialization for every
admitted layer, including instance overrides and path routes; malformed/missing/
confidential required values fail before service advertisement; no storage
resource is acquired by decoding; participant identity/source precedence stay
unchanged; ordinary no-declaration factories remain compatible. After the
prerequisite, sm-workflow needs its real configured host authority adapters and
default-factory connection, not a fixture-promoting substitute. Phase 2 remains
active and incomplete. No source/ABI change, test rerun, commit, push or
publication was performed in this investigation.

## Existing hook alternative, verified read-only

The next automatic turn inspected ComponentInit and Component.Factory's
existing protected initialize_component_c(component, params) hook. ComponentInit
publicly exposes the admitted initializationParameters and core, and its copy
can replace only core while preserving assembly, participant role, descriptors,
instance metadata and parameter provenance. The factory hook runs after canonical
resolution and before component.initializeC, so an external factory can assemble
the configured Service there without a new CNCF API or duplicated resolver.
Component.initializeC constructs ServiceGroup from the supplied core before
running the component-specific hook; therefore the factory hook is the viable
boundary, not a late mutation in the component's own initialization hook.

Correct the earlier claim: a new API is required for *create_Core-time typed
resolution*, not for every possible production integration. Existing factory
initialization is a viable alternative that leaves startup ordering and public
contracts unchanged. It must retain the captured ComponentCreate needed by
WorkflowComponentStorage without shared mutable factory state, replace only the
core's protocol/service definitions, preserve canonical core instanceId/jobEngine
and any other framework-derived fields, and fail before initialization on bad
configuration. No pool creation is part of this hook either.

The unresolved design choice is now precise: add the factory-time resolution
API to keep service assembly in create_Core, or use the existing post-resolution
factory initialization hook. Neither was implemented without user direction;
the pending API question is not answered by an automatic continuation. This
turn supplies new evidence and corrects the prerequisite claim rather than
asserting another implementation/test completion.

## Pending direction audit

Third consecutive goal turn with the same unresolved service-assembly boundary:
the initial API proposal, the verified existing-hook alternative, and the
current unanswered continuation. The latter contains no human selection.
Rechecked active goal identity, this journal and default factory; no selected
implementation exists and validation sessions 481-483 are terminal. The latest
turn is no implementation progress, not a live-process wait. Preserve the full
Phase objective and stop automatic continuation as blocked pending direction.
Recommended answer: use the existing post-resolution factory initialization
hook. Alternative: explicitly admit the CNCF factory-time resolution API.
Neither answer authorizes private pools, fabricated authority, a new Phase,
public publication or unrelated repository changes.

Resolved by direct user message `後者にして`: use the existing factory
initialization hook, not a new CNCF API. The same goal is active again. The
implementation and attempt-485 focused evidence are recorded in
[configured hook journal](2026-10-01-configured-factory-hook.md).
This resolution supersedes the pending direction above without changing the
Phase identity, admitted repositories or resource ownership contract.
