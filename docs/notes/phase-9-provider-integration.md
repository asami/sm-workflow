# Phase 9 provider integration: P9-01 source gap map

Source inventory on 2026-11-21. This is a source-level handoff, not a live
provider qualification or an assertion that Phase 80 or R1 is complete. The
consumer worktree is based on `5a2b4b8e3817d26705c90939761d23eaa2f923b4`;
canonical CNCF source inspected at `5c8ba58a4bb7d6605188b1c89b7753fca192a7ac`
and textus-ai-core at `833e8fad9646aac1e3d3e1c4674fa49d01f5daac`.
The CNCF checkout is `0.5.6-SNAPSHOT`, the coordinate declared in
`project.yaml`. `build.sbt` permits an explicitly selected source checkout;
otherwise it resolves the declared artifact. Matching SNAPSHOT coordinates do
not prove the resolved binary contains the inspected source. The actual runtime
classpath and provider configuration were not observed in this inventory.

| Boundary | Source and observed role | Gap for Phase 9 |
| --- | --- | --- |
| Generic effectful dispatcher | CNCF `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkInterpreter.scala`, `_invoke_state_machine_provider_c`, interprets a `StateMachineProgramProvider` through the UoW and admits its result; deterministic providers use `execute`. `workflow/StateMachineProviderResolution.scala` resolves registered providers. CNCF `UnitOfWorkStateMachineProviderDispatchSpec` is an executable specification with test providers, not evidence of a live AI provider. | No registered Phase 9 review provider connecting this generic dispatch to both interfaces was established by these sources. Generic dispatch itself exists; do not treat the entire dispatcher as missing. |
| API boundary | textus-ai-core `runtime/TextusAiRunner.scala` resolves generate/chat services and `runtime/AiRuntimeBinding.scala` binds provider extensions. `provider/openai/OpenAiRuntimeProvider.scala` implements `OpenAiGenerateService` using `HttpSupport.post`. These are concrete inference entry points, dependent on configuration and transport authority. | No sm-workflow textus-ai dependency or connected typed review mapping was found in its `project.yaml`, `build.sbt`, CML and main Scala search. Actual configured availability, complete input mapping, structured review result and provider evidence through normal Admission remain unverified. An inference service does not establish edit capability. |
| Agent boundary | `scripts/goal-phase-dispatch.py` prepares exact `collaboration.spawn_agent` arguments from the original Request and retained mapping; the host invokes that tool and supplies actual launch and result to `launched`/`complete`. `skills/sm-goal-phase/SKILL.md` documents this caller route. CNCF `ContinuationSpiAdapter.scala` converts an external submission against issued work, and `SkillWorkResultNormalizerV1.scala` supplies an existing typed normalizer; neither launches an agent. | Existing host transport can issue delegated semantic work. A bounded review with the same supplied diff/specification/validation package as the API route and a distinct observed context has not been demonstrated here. Requested model/profile is not an observed provider fact. |
| Completion and Admission | CNCF `ContinuationRuntimePersistence.scala` implements claim and completion, and `WorkflowCompletionServiceOperation` supplies the completion boundary. In sm-workflow, `GoalPhaseNativeWorkContinuation.scala` binds `GoalPhaseService.submitGoalPhaseWorkResult`; `NativeWorkflowResultInput.scala` reads the typed result and the existing workflow owns admission. `src/main/cozy/sm-workflow.cml` declares `GoalPhaseWorkResult`. `scripts/goal-phase-dispatch.py` projects caller evidence, while `docs/spec/native-host-execution.md` specifies normal Submit and later Advance. | A returned provider result is not accepted merely because it exists. The same typed review result entering this route from both interfaces, with genuine evidence, requires connected verification. |
| Pure selection | CNCF `workflow/WorkflowExecutionHarness.scala` selects and records finite observations. sm-workflow `caller/NativeWorkflowCallerExecution.scala` bridges issued context to it and explicitly refuses retained logical dispatch captures, directing them to the versioned caller adapter. | Neither is a provider launcher. Harness tests and recorded fixture outcomes cannot demonstrate a live provider call. |

The actionable integration gap is the missing *demonstrated connection* from a
bounded supplied-input review to an available API service and to the existing
agent host, with equivalent inputs, distinct invocation identities, typed result,
actual provider/context observations and the same existing Completion/Admission.
The inspected source does not establish that CNCF needs a new generic producer
contract: first map the selected review requirement and actual adapters onto the
existing UoW and Continuation interfaces. If that exercise exposes a concrete
unrepresentable operation or result, return its exact producer boundary and
attributable evidence for CNCF ownership. Do not infer producer closure from
fixtures or from the Phase 80 document's planned P80-A/B/C slices.

No provider, host tool, build or test was invoked for this inventory. It does not
establish deployed availability, a binary's source identity, live transport,
independence, uncertainty recovery, or implementation/repair Takeover. These
remain R1/R2 verification and delivery work; P9-01 stays unchecked pending
assessment of this handoff and its missing connection.
