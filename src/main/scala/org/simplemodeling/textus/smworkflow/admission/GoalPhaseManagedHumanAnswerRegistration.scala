package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.workflow.ContinuationIdentity
import org.goldenport.record.Record

/**
 * Internal answer retention only: no history advance or command authorization.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object GoalPhaseManagedHumanAnswerRegistration {
  import GoalPhaseHumanDecisionContract.*
  import GoalPhaseHumanInteractionVerification.*
  def registerForCallC(reviewIdentity: ContinuationIdentity, questionIdentity: ContinuationIdentity, answer: Answer,
    interactionReference: String, asOf: Instant, core: ActionCall.Core, workflow: GoalPhaseManagedStartAssembly.Declaration,
    verifier: Verifier): Consequence[Receipt] =
    if (questionIdentity == null || !canonical(questionIdentity.value, 256) || asOf == null || core == null ||
        core.executionContext == null || core.component == null || !core.component.exists(_.subsystem.nonEmpty))
      Consequence.stateConflict("Human answer requires its owning managed call and explicit question")
    else {
      given ExecutionContext = core.executionContext
      val host = core.component.get.subsystem.get.configuration
      val key = DataStore.StringEntryId(questionIdentity.value)
      for {
        questions <- GoalPhaseHumanQuestionStoragePolicy.decodeC(host, workflow)
        answers <- GoalPhaseHumanAnswerStoragePolicy.decodeC(host, workflow)
        // Capture the independent answer Create grant before consulting records
        // or the verifier. A question Create grant is never reused here.
        write <- ComponentRecordStorage.bindForCallC(core, answers.storeName, answers.collection, key)
        row <- ManagedHostRecordRead.loadHumanQuestionC(core, questions.storeName, questions.collection,
          questionIdentity.value, workflow).flatMap {
          case Some(value) if value != null => Consequence.success(value)
          case _ => Consequence.stateConflict("No retained answerable question")
        }
        question <- row.getString(GoalPhaseHumanQuestionRecord.storageField).map(GoalPhaseHumanQuestionRecord.decodeStorageC)
          .getOrElse(Consequence.stateConflict("Retained question payload is absent"))
        _ <- if (question.request.continuationId == questionIdentity) Consequence.unit
          else Consequence.stateConflict("Retained question identity differs from its storage key")
        _ <- question.admitAnswerC(answer)
        actor = core.executionContext.security.principal.id.value
        _ <- GoalPhaseParticipantAdmission.admitC(actor, core.executionContext)
        expected = Binding(core.component.get.core.instanceId, question, answer, actor)
        result <- GoalPhaseManagedClosureDecisionRegistration.withCurrentIntentC(reviewIdentity, question.intent, core, workflow) {
          GoalPhaseHumanAnswerRecord.retainC(expected, interactionReference, new GoalPhaseClosureDecisionRetention.Port {
            def loadC() = write.loadC()
            def createC(record: Record) = write.createC(record)
          }) {
            admitForCallC(expected, interactionReference, asOf, core, verifier).flatMap(_.receiptForCallC(core))
          }
        }
      } yield result
    }
}
