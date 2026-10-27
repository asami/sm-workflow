package org.simplemodeling.textus.smworkflow.preparation

import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseNativeMetadata, StandaloneReviewFix, WorkflowRepairAssessment}
import org.simplemodeling.textus.smworkflow.admission.StandaloneFixExecution as S
import org.simplemodeling.textus.smworkflow.admission.StandaloneFixExecution.given
import org.simplemodeling.textus.smworkflow.value as V

/** Existing-chat result attachment; no dispatch, workflow storage or authority.
  * The retained public response is caller-observed evidence, not a certificate.
  * @since   Oct. 27, 2026
  * @version Oct. 27, 2026
  */
private[smworkflow] object NativeTaskFixDelivery {
  private val provenance = "caller-observed; source authentication and Human authority are not established by preparation"

  def prepareC(input: V.WorkflowTaskFixDeliveryPreparationInput,
    read: String => Consequence[String]): Consequence[V.WorkflowTaskFixDelivery] =
    if (input == null || read == null) invalid("requires selected input and its current resource reader") else for {
      _ <- ownersC(input.requestIdentity, input.requesterIdentity, input.destinationIdentity)
      _ <- if (Set("sm-goal-task", "sm-test-fix", "sm-review-fix")(input.route) &&
        NativePreparationInput.exact(input.destinationParticipant) && input.sourceReference != null &&
        NativePreparationInput.exact(input.sourceReference.identity) && NativePreparationInput.exact(input.sourceReference.revision))
        Consequence.unit else invalid("requires exact route, destination participant and actual source reference")
      raw <- NativePreparationInput.readC(input.result, read)
      response <- summon[ValueReader[V.StandaloneFixView]].readC(raw)
      state <- stateC(response.state)
      _ <- S.inputC(state.input)
      _ <- if (NativePreparationInput.exact(response.sessionId) && state.input.requestIdentity == input.requestIdentity &&
        state.participant == input.destinationParticipant && route(state) == input.route &&
        state.selected.map(_.identity) == state.input.checks)
        Consequence.unit else invalid("public result crosses the selected request, participant or workflow route")
      disposition <- dispositionC(response, state)
    } yield V.WorkflowTaskFixDelivery(input.route, input.requestIdentity, input.requesterIdentity,
      input.destinationIdentity, input.destinationParticipant, response.sessionId, input.sourceReference,
      raw, disposition, provenance)

  def adoptC(input: V.WorkflowTaskFixAdoptionPreparationInput,
    read: String => Consequence[String]): Consequence[V.WorkflowTaskFixAdoption] =
    if (input == null || read == null) invalid("requires selected adoption and its current resource reader") else for {
      _ <- ownersC(input.requestIdentity, input.requesterIdentity, input.destinationIdentity)
      raw <- NativePreparationInput.readC(input.delivery, read)
      packet <- summon[ValueReader[V.WorkflowTaskFixDelivery]].readC(raw)
      _ <- if (packet.requestIdentity == input.requestIdentity && packet.requesterIdentity == input.requesterIdentity &&
        packet.destinationIdentity == input.destinationIdentity && packet.provenance == provenance &&
        Set("adopt", "defer", "reject")(input.disposition) && NativePreparationInput.exact(input.rationale))
        Consequence.unit else invalid("adoption crosses the returned request/owners or lacks an explicit disposition and rationale")
      // Re-project the original bytes; never trust an edited disposition label.
      response <- summon[ValueReader[V.StandaloneFixView]].readC(packet.observation)
      state <- stateC(response.state)
      _ <- S.inputC(state.input)
      actual <- dispositionC(response, state)
      _ <- if (actual == packet.disposition && response.sessionId == packet.sessionId &&
        state.input.requestIdentity == packet.requestIdentity && state.participant == packet.destinationParticipant &&
        route(state) == packet.route && state.selected.map(_.identity) == state.input.checks && packet.sourceReference != null &&
        NativePreparationInput.exact(packet.sourceReference.identity) && NativePreparationInput.exact(packet.sourceReference.revision) &&
        (input.disposition != "adopt" || Set("completed-committed", "accepted-uncommitted", "no-change")(actual)))
        Consequence.unit else invalid("attachment is contradictory or incomplete; only defer/reject can record unfinished work")
    } yield V.WorkflowTaskFixAdoption(packet, input.disposition, input.rationale,
      "attachment-only; requester retains its existing Session, admission and affected acceptance")

  private def stateC(value: Option[Record]): Consequence[S.State] = for {
    record <- value.map(Consequence.success).getOrElse(invalid("requires the complete native public state"))
    text <- record.toJsonStringC
    raw <- parse(text).fold(e => invalid(e.getMessage), Consequence.success)
    normalized <- GoalPhaseNativeMetadata.normalizePublicJson(raw).fold(invalid(_), Consequence.success)
    state <- normalized.as[S.State].fold(e => invalid(e.getMessage), Consequence.success)
  } yield state

  private def route(state: S.State): String =
    if (state.input.task.nonEmpty) "sm-goal-task" else if (state.input.reviewOrigin.nonEmpty) "sm-review-fix" else "sm-test-fix"

  private def dispositionC(response: V.StandaloneFixView, state: S.State): Consequence[String] = for {
    _ <- if (Set("Completed", "Accepted", "NoChange")(state.stage)) for {
      _ <- WorkflowRepairAssessment.checksC(state.input.kind, state.input.scalaLint, state.selected, state.receipts)
      _ <- StandaloneReviewFix.verifiedC(state)
      _ <- if (state.unresolvedQuestions.isEmpty && state.findings.isEmpty &&
        (state.semanticReference.isEmpty || (state.conditionReport.map(_.identity) == state.input.conditions && state.conditionReport.forall(_.satisfied))))
        Consequence.unit else invalid("terminal observation retains unmet conditions or findings")
    } yield () else Consequence.unit
    result <- labelC(response, state)
  } yield result

  private def labelC(response: V.StandaloneFixView, state: S.State): Consequence[String] = {
    val finished = Set("Completed", "Accepted", "NoChange")(state.stage)
    if (finished && response.kind != "Terminal") invalid("finished state requires its terminal public response")
    else if (state.stage == "Completed") {
      if (state.input.task.nonEmpty && state.reviewReference.nonEmpty && state.commit.exists(c =>
        state.candidate.exists(p => p.paths.nonEmpty && p.head == c.parent && p.tree == c.tree && c.identity != c.parent)))
        Consequence.success("completed-committed")
      else invalid("Task completion lacks its reviewed actual commit")
    } else if (state.stage == "Accepted") {
      if (state.input.task.isEmpty && state.commit.isEmpty &&
        ((!state.input.reviewRequired && !StandaloneReviewFix.needsReview(state)) || state.reviewReference.nonEmpty) &&
        state.input.reviewOrigin.forall(origin => state.reviewDispositions.exists(entries =>
          entries.map(_.identity) == origin.findings.map(_.identity) && entries.forall(_.outcome != "unresolved"))))
        Consequence.success("accepted-uncommitted")
      else invalid("standalone acceptance cannot imply a Task commit")
    } else if (state.stage == "NoChange") {
      if (state.commit.isEmpty && state.candidate.exists(p => state.input.task.isEmpty || p.paths.isEmpty)) Consequence.success("no-change")
      else invalid("no-change cannot contain a changed candidate or manufactured commit")
    } else if (Set("Wait", "Human")(response.kind) || Set("Wait", "Human", "CommitUnknown", "ValidationIntent", "CommitIntent")(state.stage))
      Consequence.success("observed-wait")
    else if (state.stage == "CommitFailed") Consequence.success("closure-failed")
    else Consequence.success("observed-work")
  }

  private def ownersC(request: String, requester: String, destination: String): Consequence[Unit] =
    if (Vector(request, requester, destination).forall(NativePreparationInput.exact) && requester != destination)
      Consequence.unit else invalid("requires exact selected request and distinct existing requester/destination identities")
  private def invalid[A](message: String): Consequence[A] = Consequence.stateConflict("Task/FIX delivery " + message)
}
