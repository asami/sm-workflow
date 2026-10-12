package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{HistoryEntry, HistorySequence, Lifecycle, CausationReference}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseManagedHumanCompletion as H,
  SplitPhaseHumanDirectionAnswerContract as A, SplitPhaseExplicitPartitionSemantic as S}

/** Deterministic termination from canonical retained Human facts. No additional
  * result row, event, command, consent or resource owner is introduced.
  *
  * @since Oct.  5, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object SplitPhaseHumanTerminal {
  enum Outcome { case Blocked, Cancelled }
  def advanceC(command: SplitPhaseAdvanceTarget, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core,
    observation: () => Consequence[Unit]): Consequence[V.SplitPhaseAdvanceResult] = for {
    observed <- H.observeAnswerForAdvanceC(command, declaration, catalog, core)
    outcome <- observed.completion.result.result.value match {
      case A.Answer.Defer => Consequence.success(Outcome.Blocked)
      case A.Answer.Cancel => Consequence.success(Outcome.Cancelled)
      case _: A.Answer.ProvideResolution => Consequence.serviceUnavailable(
        "SplitPhase advance verified the retained Human answer; resolution handler remains unavailable")
    }
    answer = observed.completion.result
    next <- observed.answered.appendC(observed.answered.revision, HistoryEntry(
      HistorySequence(observed.answered.history.size.toLong + 1L), observed.answered.revision.next,
      Lifecycle.Completed, None, observed.answered.correlation,
      CausationReference("split-human-terminal:" + answer.continuationId.value),
      observed.answered.derivedCompositeOccurrence, observed.answered.committedPredecessor, None))
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition),
      declaration.instances, declaration.continuations)
    instances <- storage.bindInstancesC()(using core.executionContext)
    current <- instances.appendC(next.identity, observed.answered.revision, next.history.last)(using core.executionContext)
    _ <- observation()
  } yield NativeWorkflowOperationOutput.splitStopped(current.identity.value, _outcome(outcome), answer.resultReference)

  private def _outcome(outcome: Outcome): String = outcome match {
    case Outcome.Blocked => "BLOCKED"
    case Outcome.Cancelled => "CANCELLED"
  }

}
