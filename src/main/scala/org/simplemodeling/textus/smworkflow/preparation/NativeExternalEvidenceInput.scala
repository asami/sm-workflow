package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseNativeWork, NativeExternalEvidence,
  RepositorySyncNativeMergeReview, SplitPhaseNativeHumanDirection, SplitPhaseReasoningHumanDirection}
import org.simplemodeling.textus.smworkflow.value as V

/** Finite conversion of explicit issued output and supplied external facts to
  * standard StartupImport input. It neither writes storage nor calls Workflow.
  * @since   Oct. 14, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeExternalEvidenceInput {
  private val _required = Set("family", "kind", "request", "result", "reference", "execution-evidence", "issuer", "occurred-at", "collection")

  def prepareC(supplied: Map[String, String], readText: String => Consequence[String]): Consequence[Record] = try {
    for {
      options <- NativePreparationInput.optionsC(supplied, _required, readText, Set("participant"))
      _ <- if (Set("GoalPhase", "RepositorySync", "SplitPhase")(options("family")) &&
          Set("review", "human", "release-repair-continuation", "review-purpose-repair", "validation-recovery", "validation-repair-adoption", "source-history-recovery", "source-history-normalization", "candidate-recovery", "candidate-observation-continuation", "whole-phase-candidate-recovery", "closure-recovery", "remaining-plan-adoption", "failed-phase-repair-adoption", "completion-policy-adoption", "repair-policy-adoption")(options("kind")) &&
          !(options("family") == "SplitPhase" && options("kind") == "review") &&
          (!Set("release-repair-continuation", "review-purpose-repair", "validation-recovery", "validation-repair-adoption", "source-history-recovery", "source-history-normalization", "candidate-recovery", "candidate-observation-continuation", "whole-phase-candidate-recovery", "closure-recovery", "remaining-plan-adoption", "failed-phase-repair-adoption", "completion-policy-adoption", "repair-policy-adoption")(options("kind")) || options("family") == "GoalPhase") &&
          (if (Set("human", "release-repair-continuation", "review-purpose-repair", "validation-recovery", "validation-repair-adoption", "source-history-recovery", "source-history-normalization", "candidate-recovery", "candidate-observation-continuation", "whole-phase-candidate-recovery", "closure-recovery", "remaining-plan-adoption", "failed-phase-repair-adoption", "completion-policy-adoption", "repair-policy-adoption")(options("kind"))) options.contains("participant")
            else !options.contains("participant"))) Consequence.unit
        else _invalid("unsupported family/kind or missing Human participant")
      occurred <- NativePreparationInput.instantC(options("occurred-at"))
      _ <- NativePreparationInput.collectionC(options("collection"))
      request <- if (options("family") == "GoalPhase" &&
          Set("candidate-recovery", "candidate-observation-continuation", "source-history-normalization")(options("kind")))
        readText(options("request")).flatMap { text => for {
          json <- io.circe.parser.parse(text).fold(error => _invalid(error.message), Consequence.success)
          target <- json.hcursor.get[io.circe.Json]("target").fold(error => _invalid(error.message), Consequence.success)
          _ <- (if (options("kind") == "candidate-recovery")
            org.simplemodeling.textus.smworkflow.admission.GoalPhaseCandidateRecovery.decodePublicTarget(target).map(_ => ())
          else if (options("kind") == "source-history-normalization") NativeReviewPreparation.sourceHistoryNormalizationTargetC(target)
            .map[Either[String, Unit]](_ => Right(())).fold(error => Left(error.toString), identity)
          else org.simplemodeling.textus.smworkflow.admission.GoalPhaseCandidateObservationContinuation.decodePublicTarget(target).map(_ => ()))
            .fold(_invalid(_), Consequence.success)
          record <- new org.goldenport.record.io.RecordDecoder().json(text)
        } yield record }
        else NativePreparationInput.readC(options("request"), readText)
      result <- if (options("family") == "GoalPhase" && options("kind") == "review")
        readText(options("result")).flatMap { text => for {
          json <- io.circe.parser.parse(text).fold(error => _invalid(error.message), Consequence.success)
          _ <- org.simplemodeling.textus.smworkflow.admission.GoalPhasePurposeRepair.validatePublicResultJsonC(json)
          _ <- org.simplemodeling.textus.smworkflow.admission.IncrementalSbtDiagnosisRepair.validatePublicResultJsonC(json)
          record <- new org.goldenport.record.io.RecordDecoder().json(text)
        } yield record }
        else NativePreparationInput.readC(options("result"), readText)
      referenceRecord <- NativePreparationInput.readC(options("reference"), readText)
      executionRecord <- NativePreparationInput.readC(options("execution-evidence"), readText)
      reference <- summon[ValueReader[V.WorkflowArtifactReference]].readC(referenceRecord)
      execution <- summon[ValueReader[V.WorkflowExecutionEvidence]].readC(executionRecord)
      prepared <- _selected_c(options, request, result, reference, execution, occurred)
      payload <- prepared._2.getString(NativeExternalEvidence.storageField).map(Consequence.success)
        .getOrElse(_invalid("prepared row has no native payload"))
      row = Record.data("id" -> prepared._1, NativeExternalEvidence.storageField -> payload)
      bootstrap = Record.data("datastore" -> Vector(Record.data("collection" -> options("collection"), "records" -> Vector(row))))
    } yield bootstrap
  } catch { case NonFatal(_) => _invalid("invalid explicit argument, file or typed input") }

  private def _selected_c(options: Map[String, String], request: Record, result: Record,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence,
    occurred: Instant): Consequence[(String, Record)] = {
    val human = options("kind") == "human"
    val issuer = options("issuer")
    val participant = options.getOrElse("participant", "")
    def goalReview(context: V.WorkflowReviewContext): Consequence[(String, Record)] = for {
      _ <- org.simplemodeling.textus.smworkflow.admission.GoalPhasePurposeRepair.validatePublicResultC(result)
      answer <- summon[ValueReader[V.GoalPhaseWorkResult]].readC(result)
      prepared <- NativeReviewPreparation.goalC(context, answer, reference, execution, issuer, occurred)
    } yield (prepared.recordIdentity, prepared.record)
    def repositoryReview(context: V.WorkflowReviewContext): Consequence[(String, Record)] = for {
      answer <- summon[ValueReader[V.RepositorySyncNativeMergeReviewResult]].readC(result)
      prepared <- NativeReviewPreparation.repositoryC(context, answer, reference, execution, issuer, occurred)
    } yield (prepared.recordIdentity, prepared.record)
    def goalHuman(context: V.WorkflowHumanDecisionContext): Consequence[(String, Record)] = for {
      answer <- summon[ValueReader[V.GoalPhaseOrderedDecisionAnswer]].readC(result)
      prepared <- NativeHumanDecisionPreparation.goalC(context, answer, reference, execution, participant, issuer, occurred)
    } yield (prepared.recordIdentity, prepared.record)
    def repositoryHuman(context: V.WorkflowHumanDecisionContext): Consequence[(String, Record)] = for {
      answer <- summon[ValueReader[V.RepositorySyncHumanConflictAnswer]].readC(result)
      prepared <- NativeHumanDecisionPreparation.repositoryC(context, answer, reference, execution, participant, issuer, occurred)
    } yield (prepared.recordIdentity, prepared.record)
    options("family") match {
      case "GoalPhase" if options("kind") == "release-repair-continuation" => for {
        description <- summon[ValueReader[V.GoalPhaseReleaseRepairContinuationDescriptionResult]].readC(request)
        input <- summon[ValueReader[V.GoalPhaseReleaseRepairContinuationRecoveryInput]].readC(result)
        prepared <- NativeReleaseRepairPreparation.prepareC(description, input, reference, execution, participant, issuer, occurred)
      } yield (prepared.recordIdentity, prepared.record)
      case "GoalPhase" if options("kind") == "review-purpose-repair" => for {
        description <- summon[ValueReader[V.GoalPhaseReviewPurposeRepairDescriptionResult]].readC(request)
        input <- summon[ValueReader[V.GoalPhaseReviewPurposeRepairInput]].readC(result)
        prepared <- NativeLegacyPurposeRepairPreparation.prepareC(description, input, reference, execution, participant, issuer, occurred)
      } yield (prepared.recordIdentity, prepared.record)
      case "GoalPhase" if options("kind") == "completion-policy-adoption" => for {
        description <- summon[ValueReader[V.GoalPhaseCompletionPolicyDescriptionResult]].readC(request)
        input <- summon[ValueReader[V.GoalPhaseCompletionPolicyAdoptionInput]].readC(result)
        prepared <- NativeCompletionPolicyPreparation.prepareC(description, input, reference, execution,
          participant, issuer, occurred)
      } yield (prepared.recordIdentity, prepared.record)
      case "GoalPhase" if options("kind") == "repair-policy-adoption" => for {
        description <- summon[ValueReader[V.GoalPhaseRepairPolicyDescriptionResult]].readC(request)
        input <- summon[ValueReader[V.GoalPhaseRepairPolicyAdoptionInput]].readC(result)
        prepared <- NativeRepairPolicyPreparation.prepareC(description, input, reference, execution,
          participant, issuer, occurred)
      } yield (prepared.recordIdentity, prepared.record)
      case "GoalPhase" if options("kind") == "failed-phase-repair-adoption" => for {
        description <- summon[ValueReader[V.GoalPhaseFailedPhaseRepairDescriptionResult]].readC(request)
        input <- summon[ValueReader[V.GoalPhaseFailedPhaseRepairAdoptionInput]].readC(result)
        prepared <- NativeFailedPhaseRepairPreparation.prepareC(description, input, reference, execution,
          participant, issuer, occurred)
      } yield (prepared.recordIdentity, prepared.record)
      case "GoalPhase" if options("kind") == "validation-repair-adoption" => for {
        description <- summon[ValueReader[V.GoalPhaseValidationRepairDescriptionResult]].readC(request)
        input <- summon[ValueReader[V.GoalPhaseValidationRepairAdoptionInput]].readC(result)
        prepared <- NativeValidationRepairPreparation.prepareC(description, input, reference, execution,
          participant, issuer, occurred)
      } yield (prepared.recordIdentity, prepared.record)
      case "GoalPhase" if options("kind") == "closure-recovery" => for {
        description <- summon[ValueReader[V.GoalPhaseClosureRecoveryDescriptionResult]].readC(request)
        answer <- summon[ValueReader[V.GoalPhaseClosureRecoveryAnswer]].readC(result)
        prepared <- NativeClosureRecoveryPreparation.prepareC(description, answer, reference, execution,
          participant, issuer, occurred)
      } yield (prepared.recordIdentity, prepared.record)
      case "GoalPhase" if options("kind") == "remaining-plan-adoption" => for {
        description <- summon[ValueReader[V.GoalPhaseRemainingPlanDescriptionResult]].readC(request)
        input <- summon[ValueReader[V.GoalPhaseRemainingPlanAdoptionInput]].readC(result)
        prepared <- NativeRemainingPlanPreparation.prepareC(description, input, reference, execution,
          participant, issuer, occurred)
      } yield (prepared.recordIdentity, prepared.record)
      case "GoalPhase" if options("kind") == "validation-recovery" => for {
        description <- summon[ValueReader[V.GoalPhaseValidationRecoveryDescriptionResult]].readC(request)
        answer <- summon[ValueReader[V.GoalPhaseValidationRecoveryAnswer]].readC(result)
        prepared <- NativeValidationRecoveryPreparation.prepareC(description, answer, reference, execution,
          participant, issuer, occurred)
      } yield (prepared.recordIdentity, prepared.record)
      case "GoalPhase" if options("kind") == "whole-phase-candidate-recovery" => for {
        description <- summon[ValueReader[V.GoalPhaseWholePhaseCandidateRecoveryDescriptionResult]].readC(request)
        answer <- summon[ValueReader[V.GoalPhaseWholePhaseCandidateRecoveryAnswer]].readC(result)
        prepared <- NativeWholePhaseCandidateRecoveryPreparation.prepareC(description, answer, reference, execution,
          participant, issuer, occurred)
      } yield (prepared.recordIdentity, prepared.record)
      case "GoalPhase" if options("kind") == "candidate-recovery" => for {
        description <- summon[ValueReader[V.GoalPhaseCandidateRecoveryDescriptionResult]].readC(request)
        answer <- summon[ValueReader[V.GoalPhaseCandidateRecoveryAnswer]].readC(result)
        prepared <- NativeCandidateRecoveryPreparation.prepareC(description, answer, reference, execution,
          participant, issuer, occurred)
      } yield (prepared.recordIdentity, prepared.record)
      case "GoalPhase" if options("kind") == "candidate-observation-continuation" => for {
        description <- summon[ValueReader[V.GoalPhaseCandidateObservationContinuationDescriptionResult]].readC(request)
        answer <- summon[ValueReader[V.GoalPhaseCandidateObservationContinuationAnswer]].readC(result)
        prepared <- NativeCandidateObservationContinuationPreparation.prepareC(description, answer, reference, execution,
          participant, issuer, occurred)
      } yield (prepared.recordIdentity, prepared.record)
      case "GoalPhase" if options("kind") == "source-history-recovery" => for {
        description <- summon[ValueReader[V.GoalPhaseSourceHistoryRecoveryDescriptionResult]].readC(request)
        answer <- summon[ValueReader[V.GoalPhaseSourceHistoryRecoveryAnswer]].readC(result)
        prepared <- NativeSourceHistoryRecoveryPreparation.prepareC(description, answer, reference, execution,
          participant, issuer, occurred)
      } yield (prepared.recordIdentity, prepared.record)
      case "GoalPhase" if options("kind") == "source-history-normalization" => for {
        description <- summon[ValueReader[V.GoalPhaseSourceHistoryNormalizationDescriptionResult]].readC(request)
        answer <- summon[ValueReader[V.GoalPhaseSourceHistoryNormalizationAnswer]].readC(result)
        prepared <- NativeSourceHistoryNormalizationPreparation.prepareC(description, answer, reference, execution,
          participant, issuer, occurred)
      } yield (prepared.recordIdentity, prepared.record)
      case "GoalPhase" => for {
        output <- summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(request)
        _ <- _kind_c(output.kind, human)
        _ <- if ((if (human) output.human.size + output.repositoryHuman.size else output.work.toVector.flatMap(_.review).size + output.repositoryReview.size) == 1) Consequence.unit
          else _invalid("Goal assigned context is absent or ambiguous")
        prepared <- if (human) {
          val own = output.human.toVector.flatMap(_.humanDecisionContext)
          val child = output.repositoryHuman.toVector.flatMap(_.humanDecisionContext)
          _one_c(own ++ child).flatMap(context =>
            if (own.nonEmpty && output.resultType.contains("GoalPhaseOrderedDecisionAnswer")) goalHuman(context)
            else if (child.nonEmpty && output.resultType.contains("RepositorySyncHumanConflictAnswer")) repositoryHuman(context)
            else _invalid("Goal Human result type differs from its assigned context"))
        } else {
          val own = output.work.toVector.flatMap(_.review).flatMap(_.reviewContext)
          val child = output.repositoryReview.toVector.flatMap(_.reviewContext)
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
          _one_c(output.human.toVector.flatMap(_.humanDecisionContext)).flatMap(repositoryHuman)
          else if (!human && output.resultType.contains(RepositorySyncNativeMergeReview.resultCodec.typeIdentity))
            _one_c(output.review.toVector.flatMap(_.reviewContext)).flatMap(repositoryReview)
          else _invalid("Repository result type differs from its assigned context")
      } yield prepared
      case "SplitPhase" => for {
        output <- summon[ValueReader[V.SplitPhaseAdvanceResult]].readC(request)
        _ <- _kind_c(output.kind, human)
        _ <- if (human && output.resultType.contains("SplitPhaseHumanDirectionAnswer")) Consequence.unit
          else _invalid("Split result type differs from its assigned Human context")
        _ <- if (output.scopeHuman.size + output.reasoningHuman.size == 1) Consequence.unit
          else _invalid("Split assigned context is absent or ambiguous")
        context <- _one_c(output.scopeHuman.toVector.flatMap(_.humanDecisionContext) ++ output.reasoningHuman.toVector.flatMap(_.humanDecisionContext))
        _ <- if ((if (output.scopeHuman.nonEmpty) NativeHumanDecisionQuestionWire.inputType(context.question).contains(SplitPhaseNativeHumanDirection.inputCodec.typeIdentity)
          else NativeHumanDecisionQuestionWire.inputType(context.question).contains(SplitPhaseReasoningHumanDirection.inputCodec.typeIdentity))) Consequence.unit
          else _invalid("Split Human source differs from its assigned kind")
        answer <- summon[ValueReader[V.SplitPhaseHumanDirectionAnswer]].readC(result)
        prepared <- NativeHumanDecisionPreparation.splitC(context, answer, reference, execution, participant, issuer, occurred)
      } yield (prepared.recordIdentity, prepared.record)
      case _ => _invalid("unsupported family")
    }
  }

  private def _one_c[A](values: Vector[A]): Consequence[A] = if (values.size == 1 && values.head != null)
    Consequence.success(values.head) else _invalid("assigned context is absent or ambiguous")
  private def _kind_c(kind: String, human: Boolean): Consequence[Unit] =
    if (kind == (if (human) "Human" else "Request")) Consequence.unit else _invalid("output is not the declared assigned kind")
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("External evidence input " + reason)
}
