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
