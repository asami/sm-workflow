# CNCF Workflow Protocol Application Specialization

Status: normative application boundary

## Position

sm-workflow は CNCF Generic Workflow JSON Protocol の application consumer である。generic Start/Handle/Continuation/WorkOrder/Decision/Wait/Terminal/Result/Evidence/Presentation/ExecutionRequirement envelope を再定義しない。

sm-workflow が所有するのは software-development application schema と policy である。

## Application schemas

- GoalPhaseStartInput / GoalPhaseResult
- SplitPhaseStartInput / SplitPhaseResult
- RepositorySyncStartInput / RepositorySyncResult
- software-development WorkOrder input/result schemas
- application Decision payloads
- application terminal payloads

Phase / Step / Slice、repository、review、validation、commit 等は application payload 内に置く。

## GoalPhase start

人間が `sm-goal-phase` entry point を明示選択した後、skill は Phase 情報を収集し、
manifest binding で解決した registered `StartGoalPhase` Operation に
schema-validated `GoalPhaseStartInput` と host/client が作成した
`WorkflowInvocationSelection` を渡す。Operation はauthorityを検証し、profile inputをCNCF
generic StartRequest の `input.payload` へ写像する。skill prose、conversation history、
recommendation を canonical input または invocation authority にしない。

StartResult は CNCF generic WorkflowHandle + Continuation をそのまま使用する。

## Transport-neutral application Operation interface

Skill-facing contract は component に登録された typed CML Operation である。MCP と CNCF
launcher/CLI は同じ Operation を搬送する transport adapter であり、どちらも独自の Workflow
semantics、Operation 選択、または第二の Start/Continuation protocol を持たない。通常の
interactive deployment は MCP を使用でき、one-shot deployment は launcher/CLI を使用できる。
public skill bundle は特定の transport の argv grammar を contract に含めない。

各 adapter は registered CML Operation の exact identifier と schema-validated request を
受理し、CNCF generic response を返す。profile start Operation は人間選択と manifest binding
から決まり、後続のcompletion Operationはcurrent Continuationが指定する。Skillはこの二つを
独自に推測せず、next state、任意 command argv、または直接 state mutationを指定しない。
Phase 1ではlauncher JSON I/Oをfixtureで検証し、Phase 2ではMCPとlauncherの同値性を検証する。

## Continuation

sm-workflow は generic Continuation.kind を制御に使用し、presentation text を parse しない。
WORK_ORDER の application payload を typed software-development task として worker に渡し、
typed Result/Evidence を current Continuation が指定する registered completion Operation へ返す。
completion Operation は generic CNCF Result/Decision submission を呼び、runtime 内部の
progression evaluator が次の Continuation まで進める。Skill-facing generic
`advanceWorkflow` Operation は別途設けない。

## Console

CNCF generic Presentation を基礎に、Phase / Step / review / validation 等の application context を人間向けに表示する。JSON が正本であり、console text は projection である。

## Reasoning mapping

Workflow WorkOrder が要求する abstract reasoning level (`ROUTINE | STANDARD | DEEP | CRITICAL`) を、sm-workflow skill/host の versioned mapping policy で concrete worker profile に写像する。

mapping は設定/policyであり GoalPhaseWorkflow の StateMachine semantics ではない。actual selected profile/model/effort は可能な範囲で execution evidence として返す。

## Phase 1 executable specification

Phase 1 は少なくとも各 reference workflow について以下を JSON fixture で証明する。

```text
application StartInput
  -> human-selected registered profile Start Operation
  -> CNCF StartRequest
  -> StartResult + Continuation
  -> application WorkResult / Decision
  -> Continuation-selected registered completion Operation
  -> generic Result / Decision submission
  -> next Continuation
  -> ...
  -> Terminal + application Result
```

Codex console projection も fixture response から生成できることを確認する。


## Work classification and execution placement policy

sm-* Skills classify software-development work during planning. sm-workflow owns the policy that converts that semantic classification into application requirements carried through the CNCF generic ExecutionRequirement envelope. The execution harness owns concrete provider selection.

Responsibility boundary: Skill -> WorkClassification (what kind of work); sm-workflow -> placement / independence / reasoning requirement (how it must execute); Execution Harness -> concrete provider (who executes it).

### Skill-owned WorkClassification

For implementation planning, a Skill may report application-semantic indicators such as complexity (TRIVIAL | SIMPLE | STANDARD | COMPLEX), responsibility (PROGRAMMING | ENGINEERING), contextFootprint (SMALL | MEDIUM | LARGE), and the work type / semantic reasoning class already defined by the reasoning model.

These are classifications of work, not provider selections. TRIVIAL does not mean parent task, and PROGRAMMING does not mean Luna. A one- or two-line change is common evidence for TRIVIAL, but line count is not normative: a one-line authorization or domain-semantic change can be non-trivial.

The initial TRIVIAL criterion is a very small local change requiring no unresolved design judgment whose result is locally and easily verifiable. The Skill owns this semantic assessment because it has the Phase/task/project context needed to interpret the change.

### sm-workflow-owned execution disposition

sm-workflow resolves WorkClassification plus Workflow policy into orthogonal execution requirements. Placement and independence must not be collapsed into one flag.

- placement: INLINE | DELEGATED
- independence: OPTIONAL | REQUIRED
- semantic ReasoningClass / abstract reasoning level as already defined

INLINE means the current execution participant may perform the semantic work without creating another worker solely for routing. It does not name a ChatGPT/Codex parent task. DELEGATED means execution should use another provider/context. REQUIRED independence additionally means the delegated execution must not reuse the producer's execution context as the reviewer/decision context.

Initial policy:

| Classification / work | Placement | Independence | Reasoning |
| --- | --- | --- | --- |
| TRIVIAL implementation | INLINE | OPTIONAL | recorded semantic requirement; provider-profile mismatch does not by itself force delegation |
| bounded implementation whose current executor satisfies the resolved profile | INLINE | OPTIONAL | normal Coding requirement |
| implementation requiring a different profile | DELEGATED | OPTIONAL | normal Coding requirement |
| context-heavy implementation where parent context should be protected | DELEGATED | OPTIONAL | normal Coding requirement |
| independent review | DELEGATED | REQUIRED | Review requirement |

TRIVIAL implementation is therefore a fast path over normal concrete model/mode/level routing. It does not bypass validation, review, Admission, evidence, authorization, or any Workflow acceptance requirement.

### Provider selection

The execution harness resolves the requirements against current execution capability and configured provider mapping. Concrete model/provider identity remains runtime policy/evidence and must not become Workflow transition semantics.

For example, with a Sol/high parent: TRIVIAL implementation is INLINE on the current parent even if normal Coding mapping would prefer another profile; PROGRAMMING mapped to Luna/high is delegated to Luna/high when not on the trivial fast path; bounded Sol/high implementation may be INLINE when the current parent satisfies it; independent Sol/high review is delegated to a separate Sol/high worker despite matching model/effort.

This keeps three questions separate: the Skill classifies the work, sm-workflow decides logical execution requirements, and the harness chooses the concrete execution provider.


## Local-first judgment and semantic execution policy

Judgment and ordinary semantic implementation are eligible for local execution when their resolved ExecutionRequirement can be satisfied by an admitted local provider. Local execution is a Provider Selection policy, not Workflow semantics and not a weakening of validation, review, Admission, independence, or evidence requirements.

### Local-first Judgment

Bounded AI Judgment SHOULD be designed as a narrow typed decision whenever practical. A representative case is classification of deterministic compiler/test evidence before TEST_FIX execution:

~~~text
deterministic failure evidence
  -> deterministic pattern/rule classification where sufficient
  -> local AI Judgment for unresolved semantic classification
  -> abstract reasoning requirement
  -> TEST_FIX WorkOrder
  -> provider selection
~~~

The Judgment result should be structured and bounded, conceptually including failure category, required abstract ReasoningLevel, confidence, and rationale. Judgment does not perform the Fix and does not select a named model/provider. Low confidence, an unsupported category, or a requirement beyond local capability may resolve to a stronger provider through normal provider-selection policy.

Compile diagnostics are an important initial driver because they are deterministic evidence and many cases can be cheaply classified before AI is used. Error count alone MUST NOT determine reasoning depth: many diagnostics may share one root cause, while one diagnostic may expose a deep type/API/design problem.

### Local-first implementation target

The provider policy SHOULD treat local execution as a first-class candidate not only for ROUTINE work but also for STANDARD implementation and bounded TEST_FIX when measured capability supports it. DEEP/CRITICAL work, independent review, low-confidence Judgment, or failed local attempts may resolve/escalate to stronger providers according to policy.

This is a target policy rather than an assumption that every STANDARD task is locally solvable. Routing SHOULD become evidence-driven using execution outcomes such as compile/test/review success, retries, elapsed time, cost, and escalation. sm-workflow/corpus/experiment integration may later compare local models and cloud providers against the same reproducible work context.

A desired operating shape is:

~~~text
deterministic rule/operation where sufficient
  -> local Judgment where semantic classification is needed
  -> local ROUTINE/STANDARD implementation or bounded FIX where capable
  -> deterministic validation
  -> independent review / stronger reasoning / escalation where required
~~~

The local-first policy MUST remain replaceable/versioned configuration. No named local model, cloud model, or machine capability becomes part of Workflow transition semantics.


## Provider Routing basic design

Provider Routing follows three primary principles: **local-first execution**, **graceful decline/escalation**, and **evidence-driven policy improvement**. These principles apply to semantic Implementation and Review and, where suitable, bounded Judgment.

### Local-first

When an admitted local provider satisfies the resolved ExecutionRequirement, it SHOULD be considered before a more expensive/stronger provider. This applies to Implementation and Review, including STANDARD work where measured capability supports it. Local-first is an execution policy, not a claim that local models can complete all such work.

Review remains subject to its independence requirement. A local implementation provider and an independent local review execution MUST use execution contexts satisfying the resolved independence constraint. Local-first never weakens independent review semantics.

A deployment MAY use staged review: an inexpensive independent local review as the first review stage, followed by stronger review when policy, uncertainty, work class, sampling/calibration, or findings require it. A local PASS is not automatically equivalent to final Admission unless the admitted routing/review policy says it is sufficient.

### Decline Protocol and graceful escalation

Semantic providers MUST have a normal typed way to decline work they do not judge themselves capable of completing/reviewing reliably. Providers MUST NOT be forced to manufacture a result merely because a WorkOrder was assigned.

Conceptually, semantic execution has at least these outcomes:

~~~text
COMPLETED
DECLINED
BLOCKED
~~~

- COMPLETED: semantic work produced the requested typed result; normal validation/review/admission continues.
- DECLINED: the provider judges the work outside its reliable capability/context. This is a routing outcome, not a semantic failure. sm-workflow/provider routing SHOULD retry the same semantic work with the next admitted stronger/capable provider according to policy.
- BLOCKED: merely choosing a stronger provider is not expected to resolve the problem because an upstream requirement, design, missing input, authority, or human decision is required. This enters typed decision/error handling rather than blind escalation.

Implementation and Review both use this principle. Review may additionally express uncertainty/finding information in its application result, but provider incapability is represented as DECLINED rather than a fabricated PASS/FINDINGS judgment.

Escalation MUST be bounded and policy-driven. It is a provider-routing chain, not an unbounded retry loop. The same ExecutionRequirement, work identity, Candidate/evidence context, decline reason, and provider attempt history SHOULD remain attributable across escalation.

### Evidence-driven routing improvement

Every provider attempt SHOULD record sufficient ExecutionEvidence to evaluate routing quality. Useful dimensions include:

- semantic work type and WorkClassification;
- abstract ReasoningLevel / ExecutionRequirement;
- relevant failure/finding characteristics;
- selected provider/profile and independence disposition;
- COMPLETED / DECLINED / BLOCKED outcome and reason;
- deterministic compile/test/validation result;
- review result and later stronger-review disagreement when sampled/staged;
- retry/Fix/escalation path;
- elapsed time and available cost/resource evidence.

Routing policy SHOULD improve from observed evidence rather than static intuition alone. Initial policy can be simple and deterministic. Aggregated evidence produces routing KPIs such as local completion/success rate, decline rate, escalation rate, validation failure after local completion, and local-review false-negative/disagreement rate against stronger review.

Policy evolution is Human-in-the-Loop:

~~~text
ExecutionEvidence
  -> aggregate routing KPI / corpus
  -> analysis or experiment
  -> routing-policy revision proposal
  -> human/admission process
  -> versioned routing policy
~~~

Runtime evidence MUST NOT silently self-modify routing policy. corpus/experiment may replay reproducible work contexts against alternative providers/models before a policy revision is admitted.

The long-term objective is not to maximize local execution percentage. It is to minimize total execution cost/latency while preserving convergence and required quality, using stronger providers where evidence shows they add value.


## Machine placement and repository-based development migration

Provider Routing and physical Machine Placement are related but distinct optimization layers. Provider/model routing can be relatively fine-grained. Moving development execution between physical machines has substantially higher transfer/resume cost and SHOULD therefore operate at a coarser granularity.

A repository checkpoint plus sm-workflow state can act as a development-migration boundary. sm-repository-sync may synchronize an admitted repository state through the configured remote (for example GitHub), after which another machine with a compatible development environment can resume a sufficiently large Goal/Phase/Slice. This is analogous in spirit to process migration, but the migrated unit is durable development state rather than an in-memory process image.

Conceptually:

~~~text
fine-grained semantic routing
  WorkOrder -> local/cloud model/provider on current machine

coarse-grained execution placement
  Goal / Phase / sufficiently-large Slice
    -> repository checkpoint/sync
    -> another machine
    -> restore compatible environment/context
    -> resume workflow execution
~~~

Machine Placement SHOULD consider capabilities beyond LLM reasoning: CPU/build throughput, memory, available local models, toolchain/repository compatibility, current load, expected validation cost, and environment/cache state where available. These are execution capabilities/policy inputs, not Workflow transition semantics.

Migration MUST NOT be assumed beneficial. Relevant cost includes repository synchronization, checkout/worktree preparation, dependency/cache warm-up, environment/context reconstruction, and lost locality. Initial operation SHOULD use simple coarse policy and record evidence rather than implement a sophisticated distributed scheduler.

The useful decision boundary is whether expected benefit from parallelism, available compute, lower semantic-provider cost, or reduced queue/wall-clock time justifies migration cost. Fine-grained Fix/Review operations normally remain on the current machine unless evidence later supports otherwise; multi-hour or otherwise substantial development units are stronger migration candidates.

ExecutionEvidence SHOULD make physical placement and migration attributable where practical: machine/provider identity, migration/sync occurrence and duration, build/test duration, semantic-provider duration, escalation/decline path, and end-to-end completion time. This evidence can support later placement-policy improvement without autonomous runtime self-modification.


### Provider tool-capability requirements

Reasoning capability and execution/tool capability are separate routing dimensions. A model may have sufficient semantic ability for a WorkOrder while the environment/provider through which it is invoked lacks a required tool such as Web access, repository access, filesystem/shell execution, Git, MCP, or another application capability.

ExecutionRequirement SHOULD therefore be able to express required tool capabilities independently of ReasoningLevel. Provider profiles advertise available capabilities, and routing MUST reject an incompatible provider before semantic execution when a hard requirement is known.

Conceptually:

~~~text
WorkOrder
  -> ExecutionRequirement
       reasoning requirement
       tool-capability requirements
           web?
           repository/filesystem?
           shell/build/test?
           git?
           MCP/application tools?
  -> Provider Routing
       capability match
       then cost/quality/locality policy
~~~

A missing required capability is not evidence that the model is unintelligent and SHOULD NOT normally be recorded as a reasoning decline/failure. It is a routing incompatibility. If the requirement becomes known only during execution, the attempt may yield a typed capability-blocked/reroute outcome and continue on a compatible provider.

Tool availability may depend on the integration/harness rather than the underlying model. Therefore evidence and provider identity SHOULD distinguish model identity from execution environment/integration where practical.

This is especially important for local-first operation: local providers can remain preferred for repository-local Implementation, Fix, validation interpretation, and Review work that does not require external information, while work requiring current Web information can be routed directly to a Web-capable provider instead of first failing locally.
