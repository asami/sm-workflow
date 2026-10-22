package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.record.io.RecordDecoder
import org.simplemodeling.textus.smworkflow.value as V

/** Selected Human context from the actual owning Decision.
  * @since   Oct. 14, 2026
  * @version Oct. 23, 2026
  */
private[smworkflow] object NativeHumanDecisionContext {
  def goalC(core: ActionCall.Core, question: WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing]):
    Consequence[V.WorkflowHumanDecisionContext] = for {
    source <- ThreeWorkflowExternalEvidencePorts.goalHumanSourceC(question)
    selected <- NativeHumanDecisionScope.questionBindingC(question, source)
    context <- _context_c(core, selected, NativeExternalEvidence.Purpose.GoalHuman,
      Set(GoalPhaseOrderedHumanDecision.inputCodec.typeIdentity), GoalPhaseOrderedHumanDecision.answerCodec.typeIdentity)
  } yield context

  def repositoryC(core: ActionCall.Core, question: WorkflowInteraction[RepositorySyncHumanConflict.Input, Nothing]):
    Consequence[V.WorkflowHumanDecisionContext] = for {
    source <- ThreeWorkflowExternalEvidencePorts.repositoryHumanSourceC(question)
    selected <- NativeHumanDecisionScope.questionBindingC(question, source)
    context <- _context_c(core, selected, NativeExternalEvidence.Purpose.RepositoryHuman,
      Set(RepositorySyncHumanConflict.inputCodec.typeIdentity), RepositorySyncHumanConflict.answerCodec.typeIdentity)
  } yield context

  def splitC(core: ActionCall.Core, question: WorkflowInteraction[?, Nothing]): Consequence[V.WorkflowHumanDecisionContext] = for {
    selected <- NativeSplitAnswerScope.questionBindingC(question)
    context <- _context_c(core, selected, NativeExternalEvidence.Purpose.SplitAnswer,
      Set(SplitPhaseNativeHumanDirection.inputCodec.typeIdentity, SplitPhaseReasoningHumanDirection.inputCodec.typeIdentity),
      SplitPhaseHumanDirectionAnswerContract.resultType)
  } yield context

  private def _context_c(core: ActionCall.Core, question: Json, purpose: NativeExternalEvidence.Purpose,
    inputs: Set[String], resultType: String): Consequence[V.WorkflowHumanDecisionContext] = for {
    owner <- Option(core).flatMap(_.component).flatMap(c => Option(c.core)).flatMap(c => Option(c.instanceId))
      .map(_.canonicalKey).filter(_exact).map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Issued Human has no actual owning component"))
    _ <- if (question.hcursor.get[String]("inputType").toOption.exists(inputs) &&
        question.hcursor.get[String]("resultType").toOption.contains(resultType)) Consequence.unit
      else Consequence.stateConflict("Issued Human has no matching native input/answer codecs")
    selected <- (if (purpose == NativeExternalEvidence.Purpose.SplitAnswer) NativeSplitAnswerScope.selectQuestion(question)
      else NativeHumanDecisionScope.selectQuestion(question, purpose)).fold(Consequence.stateConflict(_), Consequence.success)
    record <- new RecordDecoder().json(selected.noSpaces)
  } yield V.WorkflowHumanDecisionContext(purpose.identity, owner, record)

  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
