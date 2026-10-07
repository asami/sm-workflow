package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.workflow.ContinuationIdentity
import org.goldenport.record.Record
import GoalPhaseHumanDecisionContract.Question

/**
 * Internal Workflow-owned question writer; no human answer or public entry.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object GoalPhaseManagedHumanQuestionRegistration {
  def registerForCallC(reviewIdentity: ContinuationIdentity, question: Question, core: ActionCall.Core,
    workflow: GoalPhaseManagedStartAssembly.Declaration): Consequence[Question] =
    if (question == null || core == null || core.executionContext == null || core.component == null ||
        !core.component.exists(_.subsystem.nonEmpty)) Consequence.stateConflict("Human question requires its owning call")
    else {
      given ExecutionContext = core.executionContext
      for {
        _ <- question.validateC
        declared <- GoalPhaseHumanQuestionStoragePolicy.decodeC(core.component.get.subsystem.get.configuration, workflow)
        write <- ComponentRecordStorage.bindForCallC(core, declared.storeName, declared.collection,
          DataStore.StringEntryId(question.request.continuationId.value))
        actual <- GoalPhaseManagedClosureDecisionRegistration.withCurrentIntentC(reviewIdentity, question.intent, core, workflow) {
          GoalPhaseHumanQuestionRecord.retainC(question, new GoalPhaseClosureDecisionRetention.Port {
            def loadC() = write.loadC()
            def createC(record: Record) = write.createC(record)
          })
        }
      } yield actual
    }
}
