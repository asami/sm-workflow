# sm-workflow Workflow SPI Consumer Refinement

Date: 2026-09-17
Status: Design refinement

## Position

`sm-workflow` はCML/CNCF Workflow SPI / Suspended Action modelのSoftware Development specialization/reference consumerとする。

Skillから見るContinuationは「Workflowから発行されたcommand」のようにprojectionできるが、source of truthはWorkflow SPI operationとそのSuspended Continuationである。

```text
CML Workflow SPI
  -> CNCF runtime
     -> Suspended Continuation
        -> Generic Skill command projection
           -> sm-workflow Skill/Parent
```

## Development workflow

例:

```text
BuildProject      -> internal provider -> Completed
RunTests          -> internal provider -> Completed
ReviewChange      -> Workflow SPI / external -> Suspended
ReviewResult      -> resume -> transition
CommitChanges     -> internal provider -> Completed
Completed
```

`ReviewChange` 等のsemantic AI workはSoftware Development Workflow SPIとして外部仕様化できる。SkillはそのSPI invocationをcommand/WorkOrderとして受け、適切なworkerをdispatchし、typed Result/Evidenceを返す。

## Boundary

- generic SPI/Continuation/runtime: CML/CNCF
- generic Skill command/WorkOrder projection: CNCF Generic Skill Workflow Support
- software development SPI/profile/policy: sm-workflow
- concrete parent/worker model dispatch: host/Skill operational policy

build/test/git closingは通常internal providerであり、Skill commandとして外へ出さない。

## Testing

Workflow SPIへdeterministic test providerをbindingすることで、AI Skillを実際に起動せずにSoftware Development WorkflowのStateMachine/closing semanticsをExecutable Specificationとして検証できるようにする。
