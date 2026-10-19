package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationResult
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceFilePath, WorkspaceId}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record

/** Owning-call scoped review acquisition and the existing CAM acceptance gate.
  * @since   Oct. 17, 2026
  * @version Oct. 17, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseScopedQualityReview {
  val metadataKey = "sm-workflow.scoped-quality.model-metadata"
  val subjectsKey = "sm-workflow.scoped-quality.model-subjects"
  private val _semantics = Vector("semantic-consistency", "implementation-quality", "excessive-checks", "integration")

  // This seam is package-only fixture evidence, never a product-selectable provider.
  trait Port {
    def readTextC(logicalFilename: String): Consequence[Option[String]]
    def reviewC(request: Record): Consequence[Record]
  }
  private final case class ModelScope(filename: String, subjects: Vector[String])

  def admitC(core: ActionCall.Core, input: GoalPhaseNativeWork.Request,
    submitted: ContinuationResult[GoalPhaseNativeWork.Result]): Consequence[Unit] =
    _outcome_c(submitted).flatMap {
      case "repair" | "decision-needed" => Consequence.unit
      case _ => for {
        _ <- _check(core != null && core.executionContext != null && core.component != null,
          "requires its actual owning call")
        subsystem <- core.component.flatMap(_.subsystem).map(Consequence.success)
          .getOrElse(Consequence.serviceUnavailable("Scoped quality review owning subsystem is unavailable"))
        port = new Port {
          def readTextC(filename: String): Consequence[Option[String]] = for {
            workspace <- WorkspaceId.parseC("sm-workflow-repository")
            path <- WorkspaceFilePath.parseC(filename)
            result <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), path)
          } yield result.text
          def reviewC(record: Record): Consequence[Record] = {
            val request = Request.of(component = "CbdSupport", service = "CbdReviewAdmin",
              operation = "reviewScopedQuality", properties = record.fields.map(field =>
                Property(field.key, field.value.single, None)).toList)
            subsystem.executeOperationResponseInChildContext(request, core.executionContext).flatMap {
              case OperationResponse.RecordResponse(actual) => Consequence.success(actual)
              case _ => _invalid("requires an ordinary Operation Record response")
            }
          }
        }
        _ <- admitC(input, submitted, core.executionContext.resolvedParameters, port)
      } yield ()
    }

  def admitC(input: GoalPhaseNativeWork.Request,
    submitted: ContinuationResult[GoalPhaseNativeWork.Result], parameters: ResolvedParameters,
    port: Port): Consequence[Unit] = _outcome_c(submitted).flatMap {
    case "repair" | "decision-needed" => Consequence.unit
    case _ => for {
      _ <- _check(input != null && input.review != null && input.review.exists(_ != null) && port != null,
        "requires the assigned review context")
      context = input.review.get
      _ <- _check(context.changedPaths != null && context.changedPaths.nonEmpty &&
        context.changedPaths.size <= 64 && context.changedPaths.distinct.size == context.changedPaths.size &&
        context.changedPaths.forall(_filename) && context.diff != null, "assigned paths or diff are invalid")
      _ <- _sequence(context.changedPaths.map(path => WorkspaceFilePath.parseC(path)))
      _ <- _check(input.generation >= 0 && input.generation < Long.MaxValue &&
        submitted.resultReference != null && _id(submitted.resultReference.identity) &&
        _id(input.instanceIdentity) && _id(input.sourceRevision), "review target or generation is invalid")
      model <- _configuration_c(parameters)
      _ <- if (context.changedPaths.exists(_.endsWith(".cml")) && model.isEmpty)
        _configuration_invalid("changed CML requires explicit model metadata and subjects") else Consequence.unit
      selected = GoalPhaseScopedQualityAdmission.Selection(submitted.resultReference.identity,
        input.generation + 1, input.instanceIdentity, input.sourceRevision, context.changedPaths,
        model.toVector.flatMap(_.subjects), model.fold(_semantics)(_ => Vector("cml-contract") ++ _semantics))
      requirements <- _requirements_c(input)
      sources <- _sequence(context.changedPaths.zipWithIndex.map { (path, ordinal) =>
        port.readTextC(path).flatMap { text =>
          _check(text != null && text.forall(_ != null), "source text is invalid").map { _ =>
            text.map(value => Record.dataAuto("identity" -> s"source-${ordinal + 1}",
              "kind" -> "source", "path" -> path, "content" -> value))
          }
        }
      })
      metadata <- model match {
        case None => Consequence.success(Vector.empty[Record])
        case Some(scope) => port.readTextC(scope.filename).flatMap {
          case Some(text) if text != null => Consequence.success(Vector(Record.dataAuto(
            "identity" -> "metadata-1", "kind" -> "cml-metadata", "content" -> text)))
          case _ => _invalid("selected model metadata is missing")
        }
      }
      materials = sources.flatten ++ Vector(Record.dataAuto("identity" -> "diff-1", "kind" -> "diff",
        "content" -> context.diff)) ++ metadata
      contents = materials.map(_.getString("content").get)
      _ <- _check(materials.size <= 64 && requirements.size <= 64 && contents.forall(_bytes(_) <= 262144) &&
        (contents ++ requirements).map(value => _bytes(value).toLong).sum <= 1048576L,
        "selected materials or requirements exceed review bounds")
      request = Record.dataAuto("reviewId" -> selected.reviewId, "revision" -> selected.revision,
        "subjectId" -> selected.subjectId, "subjectRevision" -> selected.subjectRevision,
        "checks" -> selected.checks, "paths" -> selected.paths, "modelSubjects" -> selected.modelSubjects,
        "requirements" -> requirements, "materials" -> materials)
      actual <- port.reviewC(request)
      decoded <- GoalPhaseScopedQualityAdmission.decodeResultC(actual)
      evaluation <- GoalPhaseScopedQualityAdmission.evaluateC(selected, Some(decoded))
      _ <- GoalPhaseScopedQualityAdmission.requireAdmittedC(evaluation, "accept")
    } yield ()
  }

  private def _outcome_c(submitted: ContinuationResult[GoalPhaseNativeWork.Result]): Consequence[String] =
    if (submitted == null || submitted.result == null || submitted.result.value == null ||
        !Set("accept", "repair", "decision-needed").contains(submitted.result.value.outcome))
      _invalid("submitted review outcome is invalid")
    else Consequence.success(submitted.result.value.outcome)

  private def _configuration_c(parameters: ResolvedParameters): Consequence[Option[ModelScope]] =
    if (parameters == null) _configuration_invalid("current resolved parameters are missing")
    else (parameters.get(metadataKey).map(_.value), parameters.get(subjectsKey).map(_.value)) match {
      case (None, None) => Consequence.success(None)
      case (Some(ConfigurationValue.StringValue(filename)), Some(ConfigurationValue.ListValue(values)))
          if _filename(filename) && WorkspaceFilePath.parseC(filename).toOption.nonEmpty &&
            values != null && values.nonEmpty && values.size <= 64 =>
        val subjects = values.collect { case ConfigurationValue.StringValue(value) if _label(value) => value }.toVector
        if (subjects.size == values.size && subjects.distinct.size == subjects.size)
          Consequence.success(Some(ModelScope(filename, subjects)))
        else _configuration_invalid("model subjects require distinct exact termId strings")
      case _ => _configuration_invalid("model metadata and subjects require both strict configured values")
    }

  private def _requirements_c(input: GoalPhaseNativeWork.Request): Consequence[Vector[String]] = for {
    _ <- _check(input.purpose != null, "assigned purpose is missing")
    _ <- GoalPhaseAcceptanceBoundary.savedPlanC(input.selectedPlan)
    instructions <- GoalPhaseDocumentSource.instructionsC(input.documents, input.stepIdentity)
    effective = input.selectedPlan.effectiveAcceptance.toVector
    requirements = Vector(input.purpose) ++ Option(instructions).filter(_.nonEmpty).toVector ++
      effective.flatMap(_.usage) ++ effective.flatMap(value =>
        GoalPhaseAcceptanceBoundary.applicable(value, input.kind).map(_.description))
  } yield requirements

  private def _sequence[A](items: Vector[Consequence[A]]): Consequence[Vector[A]] =
    items.foldLeft(Consequence.success(Vector.empty[A])) { (prior, next) =>
      for { values <- prior; value <- next } yield values :+ value
    }
  private def _id(value: String): Boolean = value != null && !value.isBlank && value.length <= 128 && !value.exists(_.isControl)
  private def _label(value: String): Boolean = value != null && !value.isBlank && value.trim == value &&
    value.length <= 1024 && !value.exists(_.isControl)
  private def _filename(value: String): Boolean = _label(value) && !value.startsWith("/") &&
    !value.contains('\\') && !value.matches("(?s)[A-Za-z][A-Za-z0-9+.-]*:.*")
  private def _bytes(value: String): Int = value.getBytes(UTF_8).length
  private def _check(valid: Boolean, detail: String): Consequence[Unit] =
    if (valid) Consequence.unit else _invalid(detail)
  private def _invalid[A](detail: String): Consequence[A] = Consequence.stateConflict("Scoped quality review: " + detail)
  private def _configuration_invalid[A](detail: String): Consequence[A] =
    Consequence.configurationInvalid("Scoped quality review: " + detail)
}
