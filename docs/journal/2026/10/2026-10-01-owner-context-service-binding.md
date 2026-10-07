# Owner-context service binding

## Frozen implementation scope

Previous turn made concrete progress: CNCF's private metadata preparation
passed 75 producer examples, local dependency refresh and consumer package plus
200 examples (647). Continue the same sm-workflow Phase 2, no successor.
User-directed limited recovery (parent, no agents) overrides skill delegation
and migration execution-stop routing only; it does not waive platform review,
serial SBT, private permission artifacts, independent acceptance/release gates
or original-base admission. Parent model/effort are not directly observable.

The configured factory currently captures the caller's ExecutionContext through
configuredC/configuredHostRecordsC, even though the existing post-resolution
hook can now derive its actual Component-owned context. Replace the internal
binder with a three-argument callback receiving that real context; managed
configured assembly supplies it locally and requires no caller context.
No artificial ExecutionContext.create, admitted actor, grant, private pool,
production human verifier or command authority may be synthesized.

Owned sources: GoalPhaseConfiguredComponentFactory.scala and
GoalPhaseManagedComponentFactory.scala in admission. Owned examples:
GoalPhaseConfiguredComponentFactorySpec.scala and GoalPhaseRuntimeParametersSpec.scala.
All are inspected whole before editing. Existing explicit low-level managed
factory remains unchanged. New behavior example must use AnyWordSpec,
Given/When/Then and should matchers; callback arity updates are mechanical.
Consumers are the two managed configured entry points and their existing real
SQLite/current-call/reopen tests. No public CML/ABI contract change.

Prove per-component owner/instance/parameter visibility before services,
no authenticated actor in composition and no context leakage on factory reuse.
Validate configured/runtime-parameter/managed-suspension suites, then the
GoalPhase plus ComponentFactory accumulator with the refreshed dependency.
Preserve existing dirty work. No staging, commit, push or release.

This removes the caller-context prerequisite, not the remaining default
launcher HostPorts policy/composition work. Do not mark standard CLI, server,
independent-process recovery or Phase 2 complete from this slice.

## Bounded validation repair

648 is terminal with sbt_exit=1, wrapper_exit=1 and lock=released. Production
compiled; the new example alone referenced ComponentCreate.subsystem, which
is private[cncf]. M1 fixture correction uses the already captured first/second
owners for the same identity/job-engine assertions; it does not widen CNCF API
or weaken scope/context expectations. Parent-owned orchestrated repair,
existing owned example only, first bounded byte-changing repair attempt;
no reviewed Phase closure repair cycle or authority/base gate is entered.
Re-run the same focused argv with a fresh permission identity.
Evidence: /tmp/skill.cncf.d/a-f20f9f2c-0800-4a55-91a6-e81a7495e947/95474-20261001T143739Z.summary.json

## Implemented and verified

The internal configured callback now receives the actual Component logic
ExecutionContext alongside its captured creation and resolved parameters.
Both configuredC and configuredHostRecordsC no longer take an implicit
ExecutionContext from the caller. Each callback installs only its supplied
composition context for the existing managed storage/service binding.
Explicit application dependencies, grant checks and current-call ingress
authentication remain unchanged; the lower explicit managed factory is not
promoted to default launcher assembly.

The new executable example constructs two independently owned named components
through one reused primary Factory with no implicit caller context. It observes
each distinct actual component scope as the composition scope's parent, the
same DataStoreSpace as that component, its immutable parameter snapshot and
owner job engine. Neither composition is provider-authenticated or trusted-local
authenticated. Both expose the normally initialized bound service.
Existing required-parameter/refusal examples retain their assertions.

649 passed 33/33 examples in three suites, including the existing real SQLite,
current-call, reopen, review and native human question/answer scenarios.
650 passed Compile / packageBin and 201/201 examples in 28 GoalPhase/Factory
suites. Both have sbt_exit=0, wrapper_exit=0, lock=released, no failures/aborts,
and their complete terminal summaries were read before subsequent actions:

- /tmp/skill.cncf.d/a-6b66683b-6af5-436b-9b2a-538c1b8da0ab/95975-20261001T143834Z.summary.json
- /tmp/skill.cncf.d/a-9af92e9b-2eeb-4d5d-aa75-29f4b414d79a/96576-20261001T143937Z.summary.json

No producer source changed after the previous dependency refresh. No private
storage, fixture ingress authentication or human verifier was added to main
assembly. Full repository validation, independent acceptance review, original
Phase-base admission and commit/release gates remain pending, not waived.
The goal remains active for the complete Phase 2 scope.

Next actual integration frontier is the standard impl.ComponentFactory:
it still returns generated placeholders. Connect explicit production HostPorts
policy and admitted catalog to this now owner-contextual configured composition,
then prove standalone launcher/bootstrap and independent-process recovery.
Do not fabricate human selection/command authority or use test-context creation
to make that default integration pass.
