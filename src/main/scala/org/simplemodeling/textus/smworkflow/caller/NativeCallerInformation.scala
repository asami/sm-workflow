package org.simplemodeling.textus.smworkflow.caller

import io.circe.Json
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.simplemodeling.textus.smworkflow.preparation.NativePreparationInput
import org.simplemodeling.textus.smworkflow.value as V

/** Passive caller observations reuse the original issued continuation. They do
  * not complete semantic work, authenticate Human consent or execute a command.
  * @since   Oct. 23, 2026
  * @version Oct. 23, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object NativeCallerInformation {
  private val _kinds = Set("execution-terminal", "human-message", "optional-usage")
  private val _outcomes = Set("available", "partial", "unavailable")

  def prepareC(input: V.WorkflowCallerInformationPrepareInput): Consequence[V.WorkflowCallerInformationRequest] =
    if (input == null) _invalid("requires its typed input") else for {
      _ <- NativeWorkflowCallerExecution.validateIssuedC(input.issuedWorkContext)
      _ <- if (_kinds(input.kind) && _exact(input.callerIdentity) && _valid_source(input.source)) Consequence.unit
        else _invalid("requires a supported information kind, caller identity and exact source locator")
    } yield V.WorkflowCallerInformationRequest(input.issuedWorkContext, input.kind, input.source, input.callerIdentity)

  def recordC(input: V.WorkflowCallerInformationRecordInput,
    readText: String => Consequence[String]): Consequence[V.WorkflowCallerInformationResult] =
    if (input == null || readText == null) _invalid("requires the original issued context and current resource reader")
    else for {
      requestrecord <- NativePreparationInput.readC(input.request, readText)
      request <- summon[ValueReader[V.WorkflowCallerInformationRequest]].readC(requestrecord)
      checked <- prepareC(V.WorkflowCallerInformationPrepareInput(request.issuedWorkContext, request.kind,
        request.source, request.callerIdentity))
      _ <- _same_issued_c(input.issuedWorkContext, checked.issuedWorkContext)
      observationrecord <- NativePreparationInput.readC(input.observation, readText)
      observation <- summon[ValueReader[V.WorkflowCallerInformationObservation]].readC(observationrecord)
      _ <- if (observation.request != null) Consequence.unit else _invalid("requires the retained information request")
      _ <- _same_issued_c(checked.issuedWorkContext, observation.request.issuedWorkContext)
      _ <- if (observation.request.kind == checked.kind && observation.request.source == checked.source &&
          observation.request.callerIdentity == checked.callerIdentity && _outcomes(observation.outcome) &&
          _exact(observation.detail) && scala.util.Try(Instant.parse(observation.observedAt)).isSuccess) Consequence.unit
        else _invalid("returned observation crosses the selected information request or lacks its actual outcome/time")
      terminal <- observation.outcome match {
        case "unavailable" => if (observation.rawObservation.isEmpty) Consequence.success(Option.empty[String])
          else _invalid("unavailable must not claim source bytes")
        case "available" | "partial" => observation.rawObservation.filter(s => s != null && s.trim.nonEmpty && !s.contains('\u0000')).map { raw =>
          _source_item_c(checked, raw).flatMap { item =>
            val terminal = checked.kind == "execution-terminal" &&
              Set("completed", "failed").contains(item.hcursor.get[String]("status").toOption.getOrElse("")) &&
              item.hcursor.get[Int]("exitCode").isRight &&
              !item.hcursor.get[Boolean]("truncated").toOption.contains(true) &&
              !item.hcursor.downField("output").get[Boolean]("truncated").toOption.contains(true)
            val supplied = checked.kind match {
              case "execution-terminal" => terminal
              case "human-message" => item.hcursor.get[Vector[Json]]("content").toOption.exists(_.nonEmpty)
              case "optional-usage" => item.hcursor.downField("usage").focus.exists(!_.isNull)
            }
            if (observation.outcome == "available" && !supplied)
              _invalid("available requires the requested actual information; use partial or unavailable without inventing it")
            else Consequence.success(Option.when(terminal && observation.outcome == "available")(item.noSpaces))
          }
        }.getOrElse(_invalid("partial/available requires retained original source bytes"))
      }
      reference = V.WorkflowArtifactReference("codex:" + checked.source.threadId + ":" +
        checked.source.turnId + ":" + checked.source.itemId, "1")
    } yield V.WorkflowCallerInformationResult(checked, observation.outcome, reference, observation.observedAt,
      observation.rawObservation, terminal, observation.detail)

  private def _same_issued_c(current: V.WorkflowIssuedWorkContext, retained: V.WorkflowIssuedWorkContext): Consequence[Unit] = for {
    _ <- NativeWorkflowCallerExecution.validateIssuedC(current)
    left <- current.toRecord().toJsonStringC
    right <- retained.toRecord().toJsonStringC
    _ <- if (io.circe.parser.parse(left).toOption == io.circe.parser.parse(right).toOption) Consequence.unit
      else _invalid("returned observation belongs to a different issued Session, continuation, revision or scope")
  } yield ()

  /** This is attributable public source, not an authenticated permission token.
    * The caller retains the actual tool exchange; runtime never fetches it again.
    */
  private def _source_item_c(request: V.WorkflowCallerInformationRequest, raw: String): Consequence[Json] = for {
    page <- io.circe.parser.parse(raw).fold(e => _invalid(e.message), Consequence.success)
    _ <- if (page.hcursor.downField("thread").get[String]("id").toOption.contains(request.source.threadId)) Consequence.unit
      else _invalid("public source names a different caller thread")
    turns <- page.hcursor.get[Vector[Json]]("turns").fold(e => _invalid(e.message), Consequence.success)
    selected = turns.filter(_.hcursor.get[String]("id").toOption.contains(request.source.turnId))
    _ <- if (selected.size == 1) Consequence.unit else _invalid("exact public source turn is missing or duplicated")
    items <- selected.head.hcursor.get[Vector[Json]]("items").fold(e => _invalid(e.message), Consequence.success)
    selecteditems = items.filter(_.hcursor.get[String]("id").toOption.contains(request.source.itemId))
    _ <- if (selecteditems.size == 1) Consequence.unit else _invalid("exact public source item is missing or duplicated")
    item = selecteditems.head
    expected = if (request.kind == "human-message") "userMessage" else if (request.kind == "execution-terminal") "commandExecution" else "agentMessage"
    _ <- if (item.hcursor.get[String]("type").toOption.contains(expected)) Consequence.unit
      else _invalid("public source item has a different information kind")
  } yield item

  private def _valid_source(value: V.WorkflowCodexSourceLocator): Boolean = value != null &&
    Vector(value.threadId, value.turnId, value.itemId).forall(s => _exact(s) && !s.contains(':'))
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.contains('\u0000')
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Caller information " + reason)
}
