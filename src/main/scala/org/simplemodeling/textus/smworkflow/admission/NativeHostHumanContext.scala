package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.record.io.RecordDecoder
import org.simplemodeling.textus.smworkflow.value as V

/** Selected Human context from the actual owning Decision.
  * @since   Oct. 14, 2026
  * @version Oct. 14, 2026
  */
private[smworkflow] object NativeHostHumanContext {
  def goalC(core: ActionCall.Core, question: WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing]):
    Consequence[V.WorkflowHostHumanContext] = for {
    source <- ThreeWorkflowHostEvidencePorts.goalHumanSourceC(question)
    selected <- NativeHostHumanScope.questionBindingC(question, source)
    context <- _context_c(core, selected, NativeHostEvidence.Purpose.GoalHuman,
      Set(GoalPhaseOrderedHumanDecision.inputCodec.typeIdentity), GoalPhaseOrderedHumanDecision.answerCodec.typeIdentity)
  } yield context

  def repositoryC(core: ActionCall.Core, question: WorkflowInteraction[RepositorySyncHumanConflict.Input, Nothing]):
    Consequence[V.WorkflowHostHumanContext] = for {
    source <- ThreeWorkflowHostEvidencePorts.repositoryHumanSourceC(question)
    selected <- NativeHostHumanScope.questionBindingC(question, source)
    context <- _context_c(core, selected, NativeHostEvidence.Purpose.RepositoryHuman,
      Set(RepositorySyncHumanConflict.inputCodec.typeIdentity), RepositorySyncHumanConflict.answerCodec.typeIdentity)
  } yield context

  def splitC(core: ActionCall.Core, question: WorkflowInteraction[?, Nothing]): Consequence[V.WorkflowHostHumanContext] = for {
    selected <- NativeHostSplitAnswerScope.questionBindingC(question)
    context <- _context_c(core, selected, NativeHostEvidence.Purpose.SplitAnswer,
      Set(SplitPhaseNativeHumanDirection.inputCodec.typeIdentity, SplitPhaseReasoningHumanDirection.inputCodec.typeIdentity),
      SplitPhaseHumanDirectionAnswerContract.resultType)
  } yield context

  private def _context_c(core: ActionCall.Core, question: Json, purpose: NativeHostEvidence.Purpose,
    inputs: Set[String], resultType: String): Consequence[V.WorkflowHostHumanContext] = for {
    owner <- Option(core).flatMap(_.component).flatMap(c => Option(c.core)).flatMap(c => Option(c.instanceId))
      .map(_.canonicalKey).filter(_exact).map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Issued Human has no actual owning component"))
    _ <- if (question.hcursor.get[String]("inputType").toOption.exists(inputs) &&
        question.hcursor.get[String]("resultType").toOption.contains(resultType)) Consequence.unit
      else Consequence.stateConflict("Issued Human has no matching native input/answer codecs")
    selected <- (if (purpose == NativeHostEvidence.Purpose.SplitAnswer) NativeHostSplitAnswerScope.selectQuestion(question)
      else NativeHostHumanScope.selectQuestion(question, purpose)).fold(Consequence.stateConflict(_), Consequence.success)
    record <- new RecordDecoder().json(selected.noSpaces)
  } yield V.WorkflowHostHumanContext(purpose.identity, owner, record)

  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
