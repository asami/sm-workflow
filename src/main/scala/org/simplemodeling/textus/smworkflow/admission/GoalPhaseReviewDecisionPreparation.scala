package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/**
 * One Workflow-owned deterministic follow-up shared by completion and recovery.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object GoalPhaseReviewDecisionPreparation {
  final case class Configuration(declaration: GoalPhaseManagedStartAssembly.Declaration,
    template: GoalPhaseClosureDecisionTemplate, collection: DataStore.CollectionId) {
    def validateC: Consequence[Unit] =
      if (declaration == null || declaration.configuration == null || declaration.definition == null ||
          declaration.instances == null || declaration.continuations == null || template == null || collection == null)
        Consequence.configurationInvalid("Automatic review question preparation is incomplete")
      else template.validateC
  }

  /** Resolve the missing-authority branch already selected by the owning
    * assessment. Registration owns the actual record-write boundary; there is
    * no recursive projection, second authority read or complete-intent proof.
    */
  def resolveForCallC(configuration: Configuration, intent: GoalPhaseClosureAuthority.Intent, core: ActionCall.Core
  ): Consequence[ContinuationRequest[GoalPhaseClosureAuthority.Input]] =
    if (configuration == null || core == null || core.executionContext == null ||
        intent == null)
      Consequence.stateConflict("Question preparation requires its assessed intent and current call")
    else {
      given ExecutionContext = core.executionContext
      val declaration = configuration.declaration
      for {
        _ <- configuration.validateC
        question <- configuration.template.requestC(intent)
        wire <- WorkflowDecisionJsonV1.encodeC(WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing](
          intent.handle, WorkflowContinuation.Decision(question, MinimalPresentation("Step closure authority",
            "Exact local commit authority is unavailable; Step remains open"))), GoalPhaseClosureAuthority.inputCodec)
        saved <- GoalPhaseManagedClosureDecisionRegistration.retainAssessedForCallC(intent,
          wire, core, declaration, configuration.collection)
      } yield saved
    }
}
