# Normal Workflow execution connection: causal gap, not provider acceptance

Current disposition — 2026-10-11: the mandatory direct-AI-executor/current-context
development premise in this historical audit is superseded by the owning user's
correction. Workflow requests work; the current Codex executes and returns its
result. Additional TextusAI development is stopped, not a required Phase 5.1
prerequisite. Original inspection and review evidence below is preserved, not
rewritten as acceptance or active implementation authority. See the
[request/return scope correction](2026-10-11-workflow-ai-request-return-scope-correction.md)
and the current [Phase 5.1 plan](../../../phase/phase-5.1.md).

Date: 2026-10-09, Asia/Tokyo.
Boundary: implementation follow-through of existing Phase5 batch A. This does
not add policy, approval, acceptance gates, a Goal or a repair frame. Original
nine/originals/all history, repair upper3/consumed3 and all frames are unchanged.
The same full Goal remains ACTIVE. Actual99/100/101 outcomes remain separate.

## Observation from the normal production route

The installed `impl.ComponentFactory` selects ThreeWorkflowHostParameters.
Its canonical initialization reads WorkflowExecutionProfileParameters and stores
the resolved mapping in GoalPhaseNativeRun.Declaration. The configured factory
binds GoalPhase, SplitPhase and RepositorySync services. It does not thereby
install or invoke an AI executor.

GoalPhaseNativeWorkContinuation.prepareAssignedC builds a semantic requirement
from the retained Plan. issueAssignedC persists the actual suspension and issued
WorkOrder; advance returns that work until completion. No call in this causal
route supplies an observed current AI executor to resolveC, invokes that
resolved executor or consumes its actual execution response. Configuration is
not evidence of those actions. Direct Operation tests that prepare their own
semantic responses/evidence are not evidence of runtime invocation either.

Navigation (not line-identity or unchanged-content gates):
- src/main/scala/org/simplemodeling/textus/smworkflow/impl/ComponentFactory.scala
- src/main/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowHostParameters.scala
- src/main/scala/org/simplemodeling/textus/smworkflow/admission/SmWorkflowConfiguredComponentFactory.scala
- src/main/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowLauncherAssembly.scala
- src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeWorkContinuation.scala

## Existing public capabilities and their limits

CNCF WorkflowExecutionProfileMapping.resolveC selects placement using an actual
WorkflowExecutorDescriptor. observeC consumes a descriptor after execution.
Neither method discovers a runtime nor invokes one. ProviderExecutionRequest
and StateMachineProgramProvider already express typed Required SPI execution
and managed UnitOfWork intent; these are the existing execution ownership
boundary, not a reason to introduce a private sm-workflow process runtime.

CNCF AiRunner supports generate, generateRecord and chat. TextusAI publishes
that public runner through its normal Component.Port. Its Codex capability
starts fixed `exec --sandbox read-only --ephemeral`; managed ProcessExec owns
the process. Actual thread.started data supplies the new execution context
after the child runs. It is not a producer of the currently attached parent AI
context, nor a callable inline-context capability. A job, Workflow, Component,
security session or configuration label must not be substituted for that fact.

GoalPhaseNativeWork.Result contains outcome, summary and findings. A successful
AiRunner.generateRecord reply shaped like that result does not establish that
the assigned ImplementOrderedStep source work was performed. A provider's
generated text is not automatically a semantic-action implementation, independent
review admission, source-edit permission or native completion.

External source navigation:
- CNCF workflow/WorkflowExecutionProfileMapping.scala
- CNCF workflow/StateMachineActionExecution.scala
- CNCF workflow/StateMachineProviderResolution.scala
- CNCF spi/ai/runner/AiRunner.scala
- TextusAI ComponentFactory.scala
- TextusAI provider/codex/CodexExecutionBinding.scala
- TextusAI provider/codex/CodexRuntimeProvider.scala
- TextusAI runtime/CodexExecutionEvents.scala
- TextusAI runtime/AiExecutionFacts.scala

## Consequence for the next connected implementation

The missing work is an owning-runtime public producer/consumer connection, not
another local profile table, verifier or copied-context acceptance gate:

1. The execution owner exposes an actually available current AI executor and
   callable placement capability through the public CNCF execution boundary.
   Absence stays explicit; do not manufacture inline context from configuration.
2. The normal Component consumer resolves the semantic requirement before issuing
   or invoking its work and calls the admitted Required SPI action. It must not
   reinterpret arbitrary generated text as completed source work. Concrete
   implementation/review adapters must satisfy their actual action contract.
3. The real execution response supplies optional observations from the owning
   runtime, not semantic JSON or caller assertions. Existing native completion
   consumes the requested semantic result and owns transitions/state/CAS/effects.
   The session-minimal interface remains part of the adopted correction, not an
   obligation for clients to echo internal revisions or snapshots.

Implement producer and normal consumer together with connected executable
specifications. Do not accept a newly introduced helper/API alone, create a
test-only host, grant a new sandbox, add a protocol-mode switch or move required
Phase5 execution into future work. Phase6 still owns skill creation, abstract
thinking-mode/request-result design and actual skill wiring; existing Phase5
requirements remain intact rather than silently choosing one document sentence
over the other.

Actual101 established only bounded review-ingress removal (four whole suites
27/27, current-request ACCEPTED and read-only SOURCE_PASS). Actual100's controlled
provider tests established response/context production, not this connection.
No additional SBT, paid AI, process dispatch, deployment or source-edit capability
was executed in this audit. No final source acceptance or full Phase completion
is claimed.

## Independent causal audit result

Reviewer `/root/phase5_normal_execution_contract_audit`, default read-only agent,
explicit gpt-6.1-sol/high, returned FINAL AUDIT_ONLY. Actual provider runtime
metadata was not independently observable. Its three descriptive source gaps
are CB-AUDIT-EXECUTOR (actual current descriptor producer), CB-AUDIT-DISPATCH
(semantic implementation/repair and attached-context execution), and
CB-AUDIT-COMPLETION (actual result/context bridge into native completion).
These identify already required Phase5 outcomes; they are not newly admitted
repair findings, another formal fix batch or future-only Development Candidates.
HYG=[]/DEV=[] in this causal audit; whole-file naming/spec acceptance was not
performed. No edits or commands beyond read-only inspection by the reviewer.

The audit additionally established a usable existing execution seam:
ComponentFactoryStateMachinePart installs factory provider bindings, and
UnitOfWorkInterpreter evaluates UnitOfWorkOp.StateMachineProviderExecute through
the Component's selected StateMachineProgramProvider. Normal sm-workflow inherits
empty provider lists. Public SPI assembly can select TextusAI's AiRunner, but its
generation SPI is not the missing semantic source-action implementation.
This changes the next implementation action: connect an owning-runtime public
semantic-action capability to the existing provider/UoW seam and normal host,
rather than connecting generateRecord and calling its reply ImplementStep.

Specific causal locations (navigation only):
- /Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/ComponentFactoryStateMachinePart.scala:111
- /Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkInterpreter.scala:210
- /Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkInterpreter.scala:1124
- /Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/workflow/WorkflowProtocolV1.scala:329
- /Users/asami/src/dev2026/textus-ai/src/main/scala/org/simplemodeling/textus/ai/runtime/TextusAiRunner.scala:1444

Existing Phase5 statements assigning actual warning-improvement/assessment AI
generation to Phase6 concern those narrower features. They do not cancel batch
A's explicit runtime placement/independence/result requirement. No blanket
Phase6 deferral is adopted. Session identity, origin AI context and executor AI
context remain separate concepts. One normal GoalPhase implementation and its
independent review, using real public producers/consumers and managed completion,
is the first connected implementation driver; the all-three-family requirements
and later acceptance remain intact. No additional counter/frame, command
permission, helper-specific acceptance cycle or Phase closure follows.
