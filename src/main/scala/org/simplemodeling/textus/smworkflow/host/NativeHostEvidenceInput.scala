package org.simplemodeling.textus.smworkflow.host

import java.time.Instant
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseNativeWork, NativeHostEvidence,
  RepositorySyncNativeMergeReview, SplitPhaseNativeHumanDirection, SplitPhaseReasoningHumanDirection}
import org.simplemodeling.textus.smworkflow.value as V

/** Finite conversion of explicit issued output and supplied host facts to
  * standard StartupImport input. It neither writes storage nor calls Workflow.
  * @since   Oct. 14, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeHostEvidenceInput {
  private val _required = Set("family", "kind", "request", "result", "reference", "execution-evidence", "issuer", "occurred-at", "collection")

  def prepareC(supplied: Map[String, String], readText: String => Consequence[String]): Consequence[Record] = try {
    for {
      options <- NativeHostPreparationInput.optionsC(supplied, _required, readText, Set("participant"))
      _ <- if (Set("GoalPhase", "RepositorySync", "SplitPhase")(options("family")) &&
          Set("review", "human")(options("kind")) && !(options("family") == "SplitPhase" && options("kind") == "review") &&
          (if (options("kind") == "human") options.contains("participant") else !options.contains("participant"))) Consequence.unit
        else _invalid("unsupported family/kind or missing Human participant")
      occurred <- NativeHostPreparationInput.instantC(options("occurred-at"))
      _ <- NativeHostPreparationInput.collectionC(options("collection"))
      request <- NativeHostPreparationInput.readC(options("request"), readText)
      result <- NativeHostPreparationInput.readC(options("result"), readText)
      referenceRecord <- NativeHostPreparationInput.readC(options("reference"), readText)
      executionRecord <- NativeHostPreparationInput.readC(options("execution-evidence"), readText)
      reference <- summon[ValueReader[V.WorkflowArtifactReference]].readC(referenceRecord)
      execution <- summon[ValueReader[V.WorkflowExecutionEvidence]].readC(executionRecord)
      prepared <- _selected_c(options, request, result, reference, execution, occurred)
      payload <- prepared._2.getString(NativeHostEvidence.storageField).map(Consequence.success)
        .getOrElse(_invalid("prepared row has no native payload"))
      row = Record.data("id" -> prepared._1, NativeHostEvidence.storageField -> payload)
      bootstrap = Record.data("datastore" -> Vector(Record.data("collection" -> options("collection"), "records" -> Vector(row))))
    } yield bootstrap
  } catch { case NonFatal(_) => _invalid("invalid explicit argument, file or typed input") }

  private def _selected_c(options: Map[String, String], request: Record, result: Record,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence,
    occurred: Instant): Consequence[(String, Record)] = {
    val human = options("kind") == "human"
    val issuer = options("issuer")
    val participant = options.getOrElse("participant", "")
    def goalReview(context: V.WorkflowHostReviewContext): Consequence[(String, Record)] = for {
      answer <- summon[ValueReader[V.GoalPhaseWorkResult]].readC(result)
      prepared <- NativeHostReviewPreparation.goalC(context, answer, reference, execution, issuer, occurred)
    } yield (prepared.recordIdentity, prepared.record)
    def repositoryReview(context: V.WorkflowHostReviewContext): Consequence[(String, Record)] = for {
      answer <- summon[ValueReader[V.RepositorySyncNativeMergeReviewResult]].readC(result)
      prepared <- NativeHostReviewPreparation.repositoryC(context, answer, reference, execution, issuer, occurred)
    } yield (prepared.recordIdentity, prepared.record)
    def goalHuman(context: V.WorkflowHostHumanContext): Consequence[(String, Record)] = for {
      answer <- summon[ValueReader[V.GoalPhaseOrderedDecisionAnswer]].readC(result)
      prepared <- NativeHostHumanPreparation.goalC(context, answer, reference, execution, participant, issuer, occurred)
    } yield (prepared.recordIdentity, prepared.record)
    def repositoryHuman(context: V.WorkflowHostHumanContext): Consequence[(String, Record)] = for {
      answer <- summon[ValueReader[V.RepositorySyncHumanConflictAnswer]].readC(result)
      prepared <- NativeHostHumanPreparation.repositoryC(context, answer, reference, execution, participant, issuer, occurred)
    } yield (prepared.recordIdentity, prepared.record)
    options("family") match {
      case "GoalPhase" => for {
        output <- summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(request)
        _ <- _kind_c(output.kind, human)
        _ <- if ((if (human) output.human.size + output.repositoryHuman.size else output.work.toVector.flatMap(_.review).size + output.repositoryReview.size) == 1) Consequence.unit
          else _invalid("Goal assigned context is absent or ambiguous")
        prepared <- if (human) {
          val own = output.human.toVector.flatMap(_.hostContext)
          val child = output.repositoryHuman.toVector.flatMap(_.hostContext)
          _one_c(own ++ child).flatMap(context =>
            if (own.nonEmpty && output.resultType.contains("GoalPhaseOrderedDecisionAnswer")) goalHuman(context)
            else if (child.nonEmpty && output.resultType.contains("RepositorySyncHumanConflictAnswer")) repositoryHuman(context)
            else _invalid("Goal Human result type differs from its assigned context"))
        } else {
          val own = output.work.toVector.flatMap(_.review).flatMap(_.hostContext)
          val child = output.repositoryReview.toVector.flatMap(_.hostContext)
          _one_c(own ++ child).flatMap(context =>
            if (own.nonEmpty && output.work.exists(w => w.kind == "ReviewStep" || w.kind == "ReviewPhase") &&
                output.resultType.contains(GoalPhaseNativeWork.resultCodec.typeIdentity) &&
                context.completion.typeIdentity == GoalPhaseNativeWork.resultCodec.typeIdentity) goalReview(context)
            else if (child.nonEmpty && output.resultType.contains(RepositorySyncNativeMergeReview.resultCodec.typeIdentity) &&
                context.completion.typeIdentity == RepositorySyncNativeMergeReview.resultCodec.typeIdentity) repositoryReview(context)
            else _invalid("Goal review result type differs from its assigned context"))
        }
      } yield prepared
      case "RepositorySync" => for {
        output <- summon[ValueReader[V.RepositorySyncAdvanceResult]].readC(request)
        _ <- _kind_c(output.kind, human)
        prepared <- if (human && output.resultType.contains("RepositorySyncHumanConflictAnswer"))
          _one_c(output.human.toVector.flatMap(_.hostContext)).flatMap(repositoryHuman)
          else if (!human && output.resultType.contains(RepositorySyncNativeMergeReview.resultCodec.typeIdentity))
            _one_c(output.review.toVector.flatMap(_.hostContext)).flatMap(repositoryReview)
          else _invalid("Repository result type differs from its assigned context")
      } yield prepared
      case "SplitPhase" => for {
        output <- summon[ValueReader[V.SplitPhaseAdvanceResult]].readC(request)
        _ <- _kind_c(output.kind, human)
        _ <- if (human && output.resultType.contains("SplitPhaseHumanDirectionAnswer")) Consequence.unit
          else _invalid("Split result type differs from its assigned Human context")
        _ <- if (output.scopeHuman.size + output.reasoningHuman.size == 1) Consequence.unit
          else _invalid("Split assigned context is absent or ambiguous")
        context <- _one_c(output.scopeHuman.toVector.flatMap(_.hostContext) ++ output.reasoningHuman.toVector.flatMap(_.hostContext))
        _ <- if ((if (output.scopeHuman.nonEmpty) NativeHostHumanQuestionWire.inputType(context.question).contains(SplitPhaseNativeHumanDirection.inputCodec.typeIdentity)
          else NativeHostHumanQuestionWire.inputType(context.question).contains(SplitPhaseReasoningHumanDirection.inputCodec.typeIdentity))) Consequence.unit
          else _invalid("Split Human source differs from its assigned kind")
        answer <- summon[ValueReader[V.SplitPhaseHumanDirectionAnswer]].readC(result)
        prepared <- NativeHostHumanPreparation.splitC(context, answer, reference, execution, participant, issuer, occurred)
      } yield (prepared.recordIdentity, prepared.record)
      case _ => _invalid("unsupported family")
    }
  }

  private def _one_c[A](values: Vector[A]): Consequence[A] = if (values.size == 1 && values.head != null)
    Consequence.success(values.head) else _invalid("assigned context is absent or ambiguous")
  private def _kind_c(kind: String, human: Boolean): Consequence[Unit] =
    if (kind == (if (human) "Human" else "Request")) Consequence.unit else _invalid("output is not the declared assigned kind")
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Host evidence input " + reason)
}
