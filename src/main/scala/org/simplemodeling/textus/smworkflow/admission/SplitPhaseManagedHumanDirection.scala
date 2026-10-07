package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceCommand
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseNativeHumanDirection as H,
  SplitPhaseManagedScopeProjectionCheckpoint as D}

/** Current-call native Decision issue/recovery. Metadata only is retained;
  * authentication admits the relay, never the missing independent Human answer.
  *
  * @since Oct.  4, 2026
  * @version Oct.  4, 2026
  */
private[smworkflow] object SplitPhaseManagedHumanDirection {
  final class Port private[SplitPhaseManagedHumanDirection] (
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    published: D.Port) {
    /** Existing exact suspension is observed first; arbitrary later state or
      * an absent question after progress is not a fresh-issue opportunity.
      */
    def advanceForCallC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core):
        Consequence[Option[WorkflowInteraction[H.Input, Nothing]]] = for {
      observed <- published.loadForCallC(command, core).flatMap {
        case Some(value) => Consequence.success(value)
        case None => _invalid("actual owning scope publication is absent")
      }
      plan <- H.prepareC(observed)
      result <- plan match {
        case None => Consequence.success(None)
        case Some(actual) => for {
          storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
            SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition), declaration.instances, declaration.continuations)
          decisions <- storage.bindDecisionC(H.completionOperation, H.inputCodec)
          retained <- decisions.loadC(actual.question.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]].request.continuationId)(using core.executionContext)
          question <- retained match {
            case Some(value) if value == actual.question && SplitPhaseDefinitionBinding.sameRecord(observed.current, actual.next) =>
              Consequence.success(value)
            case Some(_) => _invalid("retained question or exact current Human suspension differs")
            case None if SplitPhaseDefinitionBinding.sameRecord(observed.current, actual.intent.current) =>
              decisions.suspendC(actual.intent, actual.question)(using core.executionContext) match {
                case Consequence.Success(committed) if SplitPhaseDefinitionBinding.sameRecord(committed.instance, actual.next) => for {
                  saved <- decisions.loadC(actual.question.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]].request.continuationId)(using core.executionContext)
                  current <- published.loadForCallC(command, core)
                  question <- (saved, current) match {
                    case (Some(value), Some(now)) if value == actual.question && now.record == observed.record &&
                        SplitPhaseDefinitionBinding.sameRecord(now.current, actual.next) => Consequence.success(value)
                    case _ => _uncertain("committed Decision readback differs; observe later without retry")
                  }
                } yield question
                case _ => _uncertain("Decision commit is not established; observe later without retry")
              }
            case None => _invalid("advanced history lacks its actual published Human question")
          }
        } yield Some(question)
      }
    } yield result
  }

  def bindC(params: ComponentCreate, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[Port] = for {
    required <- SplitPhaseDefinitionBinding.humanDirectionOperationC
    _ <- if (params != null && declaration != null && catalog != null &&
        required.inputType.map(_.value).contains(H.inputCodec.typeIdentity)) Consequence.unit
      else _invalid("host or generated Human declaration differs")
    published <- D.bindC(params.configuration, declaration, catalog)
    _ <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, declaration.configuration,
      Vector(catalog.definition), declaration.instances, declaration.continuations)
  } yield new Port(declaration, catalog, published)

  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase managed Human direction " + reason)
  private def _uncertain[A](reason: String): Consequence[A] = DataStoreConditionalTransitionFailure.transactionIndeterminate("SplitPhase managed Human direction " + reason)
}
