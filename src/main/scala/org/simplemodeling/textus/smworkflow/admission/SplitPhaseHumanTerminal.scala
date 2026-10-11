package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.SplitPhaseHumanDirectionLifecycle
import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{HistoryEntry, HistorySequence, Lifecycle, CausationReference}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceCommand
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseManagedHumanCompletion as H,
  SplitPhaseHumanDirectionAnswerContract as A, SplitPhaseExplicitPartitionSemantic as S}

/** Deterministic termination from canonical retained Human facts. No additional
  * result row, event, command, consent or resource owner is introduced.
  *
  * @since Oct.  5, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object SplitPhaseHumanTerminal {
  enum Outcome { case Blocked, Cancelled }
  final case class Result(outcome: Outcome, context: S.ContextReference,
    decision: ContinuationIdentity, answer: ContextReference)

  val schema = "sm-workflow.split-human-terminal.v1"
  val payloadCodec: PayloadCodec[Result] = new PayloadCodec[Result] {
    val typeIdentity = "SplitPhaseAdvanceResult"
    def encode(value: Result): Json = encodeC(value).toOption.getOrElse(
      throw new IllegalArgumentException("Invalid SplitPhase Human terminal result"))
    def decode(value: Json): Either[String, Result] = for {
      json <- Option(value).filter(v => _bounded(v.noSpaces)).toRight("Terminal payload is absent or oversized")
      root <- json.asObject.toRight("Terminal envelope differs")
      _ <- Either.cond(root("schemaVersion").flatMap(_.asString).contains(schema), (), "Terminal schema differs")
      body <- root("payload").flatMap(_.asObject)
        .toRight("Terminal fields differ")
      outcome <- body("outcome").flatMap(_.asString) match {
        case Some("BLOCKED") => Right(Outcome.Blocked)
        case Some("CANCELLED") => Right(Outcome.Cancelled)
        case _ => Left("Terminal outcome differs")
      }
      context <- _reference(body("context").getOrElse(Json.Null))
      decision <- body("decision").flatMap(_.asString).filter(_canonical).toRight("Terminal Decision differs")
      answer <- _reference(body("answer").getOrElse(Json.Null))
    } yield Result(outcome, S.ContextReference(context.identity, context.revision), ContinuationIdentity(decision), answer)
  }

  def encodeC(result: Result): Consequence[Json] =
    if (result == null || result.outcome == null || result.context == null || result.decision == null || result.answer == null ||
        !_canonical(result.context.identity) || !_canonical(result.context.revision) || !_canonical(result.decision.value) ||
        !_canonical(result.answer.identity) || !_canonical(result.answer.revision)) _invalid("payload facts differ")
    else {
      val json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> Json.obj(
        "outcome" -> Json.fromString(_outcome(result.outcome)),
        "context" -> _reference_json(result.context.identity, result.context.revision),
        "decision" -> Json.fromString(result.decision.value),
        "answer" -> _reference_json(result.answer.identity, result.answer.revision)))
      if (_bounded(json.noSpaces)) Consequence.success(json) else _invalid("payload exceeds its UTF-8 bound")
    }

  def advanceC(command: SplitPhaseAdvanceCommand, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core,
    observation: () => Consequence[Unit]): Consequence[String] = for {
    observed <- H.observeAnswerForAdvanceC(command, declaration, catalog, core)
    outcome <- observed.completion.result.result.value match {
      case A.Answer.Defer => Consequence.success(Outcome.Blocked)
      case A.Answer.Cancel => Consequence.success(Outcome.Cancelled)
      case _: A.Answer.ProvideResolution => Consequence.serviceUnavailable(
        "SplitPhase advance verified the retained Human answer; resolution handler remains unavailable")
    }
    edge = outcome match {
      case Outcome.Blocked => ("human-deferred", "Deferred")
      case Outcome.Cancelled => ("human-cancelled", "Cancelled")
    }
    _ <- if (SplitPhaseHumanDirectionLifecycle.permits("Answered", edge._1, edge._2)) Consequence.unit
      else _invalid("successor is not declared by the generated CML")
    answer = observed.completion.result
    decision = observed.plan.question.current.asInstanceOf[WorkflowContinuation.Decision[SplitPhaseNativeHumanDirection.Input]]
    input = decision.request.input.get.value
    result = Result(outcome, input.context, answer.continuationId, answer.resultReference)
    _ <- encodeC(result).map(_ => ())
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
    handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current)
    terminal <- WorkflowProtocolV1.projectTerminalC(handle, current, TypedValue(payloadCodec.typeIdentity, result),
      MinimalPresentation("SplitPhase " + _outcome(outcome), "The recorded Human direction ended this split run.",
        reason = Some(_outcome(outcome))))
    wire <- WorkflowTerminalJsonV1.encodeC(terminal, payloadCodec)
  } yield wire

  private def _outcome(outcome: Outcome): String = outcome match {
    case Outcome.Blocked => "BLOCKED"
    case Outcome.Cancelled => "CANCELLED"
  }
  private def _reference_json(identity: String, revision: String): Json =
    Json.obj("identity" -> Json.fromString(identity), "revision" -> Json.fromString(revision))
  private def _reference(value: Json): Either[String, ContextReference] = for {
    fields <- value.asObject.toRight("Terminal reference fields differ")
    identity <- fields("identity").flatMap(_.asString).filter(_canonical).toRight("Terminal reference identity differs")
    revision <- fields("revision").flatMap(_.asString).filter(_canonical).toRight("Terminal reference revision differs")
  } yield ContextReference(identity, revision)
  private def _canonical(value: String): Boolean = value != null && value.nonEmpty && value.length <= 1024 &&
    value.trim == value && !value.exists(_.isControl) && UTF_8.newEncoder().canEncode(value)
  private def _bounded(value: String): Boolean = UTF_8.newEncoder().canEncode(value) && value.getBytes(UTF_8).length <= 16384
  private def _invalid[T](reason: String): Consequence[T] = Consequence.stateConflict("SplitPhase Human terminal " + reason)
  private def _uncertain[T](reason: String): Consequence[T] = DataStoreConditionalTransitionFailure.transactionIndeterminate(
    "SplitPhase Human terminal " + reason)
}
