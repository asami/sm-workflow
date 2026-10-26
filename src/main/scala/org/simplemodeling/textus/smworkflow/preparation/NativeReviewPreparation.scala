package org.simplemodeling.textus.smworkflow.preparation

import io.circe.Json
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseAcceptanceBoundary, GoalPhaseDependencyScope, GoalPhaseFindingRecording, GoalPhaseFixAssessment, GoalPhaseFixIssues, GoalPhaseNativeMetadata, GoalPhaseNativeReviewCompletion, GoalPhaseNativeWork, GoalPhasePlanRevision, GoalPhasePurposeRepair, GoalPhaseRepairProgress, NativeExternalEvidence,
  RepositorySyncNativeMergeReview, ThreeWorkflowExternalEvidencePorts, WorkflowSbtValidation}
import io.circe.syntax.*
import org.simplemodeling.textus.smworkflow.value as V

/** Passive preparation of managed review input using the existing v7 codec.
  * The independent reviewer owns the review and provisioning of the returned row.
  * @since   Oct. 14, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeReviewPreparation {
  final case class Prepared(recordIdentity: String, record: Record)

  /** Preserve the complete opaque target before Record projection can erase
    * malformed fields or colliding public property names. */
  private[smworkflow] def sourceHistoryNormalizationTargetC(value: Json): Consequence[Unit] =
    org.simplemodeling.textus.smworkflow.admission.GoalPhaseSourceHistoryNormalization.decodePublicTarget(value)
      .fold(Consequence.stateConflict(_), _ => Consequence.unit)

  private val _handle_names = Vector("componentIdentity" -> "component_identity",
    "workflowIdentity" -> "workflow_identity", "workflowRevision" -> "workflow_revision",
    "instanceIdentity" -> "instance_identity", "protocolVersion" -> "protocol_version")
  private val _goal_names = Vector("sourceRevision" -> "source_revision", "workKind" -> "work_kind",
    "checksReceipt" -> "checks_receipt", "changedPaths" -> "changed_paths",
    "effectiveAcceptance" -> "effective_acceptance", "validationMetadata" -> "validation_metadata", "purposeRepair" -> "purpose_repair")
  private val _repository_names = Vector("validatedRevision" -> "validated_revision", "localTip" -> "local_tip",
    "remoteTip" -> "remote_tip", "indexTree" -> "index_tree", "validationMode" -> "validation_mode",
    "changedPaths" -> "changed_paths", "selectedChecks" -> "selected_checks", "judgmentMode" -> "judgment_mode")
  private val _check_names = Vector("exitCode" -> "exit_code")
  private val _selection_names = Vector("testClasses" -> "test_classes", "completedSuites" -> "completed_suites",
    "testsRun" -> "tests_run")

  def goalC(context: V.WorkflowReviewContext, result: V.GoalPhaseWorkResult,
    resultReference: V.WorkflowArtifactReference, executionEvidence: V.WorkflowExecutionEvidence,
    issuer: String, occurredAt: Instant): Consequence[Prepared] =
    if (result == null || result.outcome == null || result.findings == null ||
        result.findings.exists(_ == null) || (result.outcome == "repair" && result.summary == null))
      Consequence.stateConflict("Goal review preparation has no selected semantic result")
    else if (result.acceptance == null) Consequence.stateConflict("Goal review preparation has no report option")
    else {
      val report = GoalPhaseAcceptanceBoundary.internalReport(result.acceptance)
      for {
        _ <- report.fold(Consequence.unit)(GoalPhaseAcceptanceBoundary.validateReportC)
        assessment <- GoalPhaseFixAssessment.internalReportC(result.fixAssessment)
        issues <- GoalPhaseFixIssues.internalReportC(result.issueReport)
        revision <- GoalPhasePlanRevision.fromPublicProposalC(result.planRevision)
        observations <- GoalPhaseFindingRecording.internalObservationsC(result.reviewObservations)
        dependency <- GoalPhaseDependencyScope.fromPublicProposalC(result.dependencyProposal)
        conditions <- GoalPhaseNativeReviewCompletion.fromPublicConditionsC(result.completionConditions)
        sources <- GoalPhasePurposeRepair.internalSourcesC(result.repairFindingSources)
        prepared <- _prepare_c(context, NativeExternalEvidence.Purpose.GoalReview, GoalPhaseNativeWork.resultCodec.typeIdentity,
          resultReference, executionEvidence, issuer, occurredAt, ThreeWorkflowExternalEvidencePorts.goalReviewVerdict(
            GoalPhaseNativeWork.Result(result.outcome, result.summary, result.findings, report, assessment, issues, revision, observations, dependency, conditions, sources)))
      } yield prepared
    }

  def repositoryC(context: V.WorkflowReviewContext, result: V.RepositorySyncNativeMergeReviewResult,
    resultReference: V.WorkflowArtifactReference, executionEvidence: V.WorkflowExecutionEvidence,
    issuer: String, occurredAt: Instant): Consequence[Prepared] =
    if (result == null || result.decision == null || result.findings == null || result.findings.exists(f =>
        f == null || f.identity == null || f.affectedPaths == null || f.affectedPaths.exists(_ == null) ||
          (result.decision == "repair" && f.rationale == null)))
      Consequence.stateConflict("Repository review preparation has no selected semantic result")
    else _prepare_c(context, NativeExternalEvidence.Purpose.RepositoryReview, RepositorySyncNativeMergeReview.resultCodec.typeIdentity,
      resultReference, executionEvidence, issuer, occurredAt, ThreeWorkflowExternalEvidencePorts.repositoryReviewVerdict(
        RepositorySyncNativeMergeReview.Result(result.decision, result.rationale, result.findings.map(f =>
          RepositorySyncNativeMergeReview.Finding(f.identity, f.affectedPaths, f.rationale)))))

  private def _prepare_c(context: V.WorkflowReviewContext, purpose: NativeExternalEvidence.Purpose,
    typeIdentity: String, reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence,
    issuer: String, occurredAt: Instant, verdict: Json): Consequence[Prepared] = for {
    _ <- if (context != null && context.purpose == purpose.identity && _exact(context.componentInstance) &&
        context.target != null && context.completion != null && context.completion.handle != null &&
        context.completion.typeIdentity == typeIdentity && _exact(context.completion.runId) &&
        _exact(context.completion.continuationId) && reference != null && _exact(reference.identity) &&
        _exact(reference.revision) && execution != null && execution.workerIdentity != null &&
        execution.workerIdentity.exists(_exact) && _exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Review preparation requires its actual purpose, owner, completion, result reference, worker, issuer and time")
    targetJson <- _json_c(context.target)
    target <- _target_c(targetJson, purpose)
    handleJson <- _json_c(context.completion.handle)
    handle <- _project_c(handleJson, _handle_names)
    _ <- if (handle.asObject.exists(o => Vector("componentIdentity", "workflowIdentity", "workflowRevision",
        "instanceIdentity", "protocolVersion").forall(k => o(k).flatMap(_.asString).exists(_exact)))) Consequence.unit
      else Consequence.stateConflict("Review preparation has an incomplete actual handle")
    completion = Json.obj("handle" -> handle, "runId" -> Json.fromString(context.completion.runId),
      "continuationId" -> Json.fromString(context.completion.continuationId),
      "typeIdentity" -> Json.fromString(context.completion.typeIdentity))
    binding = ThreeWorkflowExternalEvidencePorts.reviewBinding(context.componentInstance,
      if (purpose == NativeExternalEvidence.Purpose.GoalReview) "goal-review" else "repository-review", target, completion,
      Json.obj("identity" -> Json.fromString(reference.identity), "revision" -> Json.fromString(reference.revision)),
      Json.fromString(execution.workerIdentity.get), verdict)
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, purpose.identity, identity,
      reference.identity, reference.revision, issuer, execution.workerIdentity.get, binding, occurredAt.toString)
    encoded <- NativeExternalEvidence.encodeStorageC(evidence)
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))

  private def _project_c(value: Json, names: Vector[(String, String)]): Consequence[Json] =
    value.asObject.map { fields =>
      names.foldLeft(Consequence.success(fields)) { case (prior, (declaration, wire)) => prior.flatMap { selected =>
        (selected(declaration), selected(wire)) match {
          case (Some(left), Some(right)) if left != right =>
            Consequence.stateConflict("Review preparation has contradictory names for " + declaration)
          case (_, Some(right)) => Consequence.success(selected.remove(wire).add(declaration, right))
          case _ => Consequence.success(selected)
        }
      }}.map(Json.fromJsonObject)
    }.getOrElse(Consequence.stateConflict("Review preparation requires a structured object"))

  private def _target_c(value: Json, purpose: NativeExternalEvidence.Purpose): Consequence[Json] =
    if (purpose == NativeExternalEvidence.Purpose.GoalReview) for {
      selected <- _project_c(value, _goal_names)
      contract <- selected.hcursor.downField("purposeRepair").focus match {
        case None => Consequence.success(Option.empty[Json])
        case Some(value) => for {
          normalized <- GoalPhaseNativeMetadata.normalizePublicJson(value).fold(Consequence.stateConflict(_), Consequence.success)
          parsed <- normalized.as[GoalPhasePurposeRepair.Contract].fold(error => Consequence.stateConflict(error.message), Consequence.success)
        } yield Some(parsed.asJson)
      }
      metadata <- selected.hcursor.downField("validationMetadata").focus match {
        case None => Consequence.success(Option.empty[Json])
        case Some(value) => GoalPhaseNativeMetadata.normalizeBatches(value).fold(
          Consequence.stateConflict(_), normalized => Consequence.success(Some(normalized)))
      }
      declaration <- selected.hcursor.downField("testChecks").focus match {
        case None => Consequence.success(Option.empty[Json])
        case Some(value) => _checks_c(value).map(Some(_))
      }
      wire <- selected.hcursor.downField("test_checks").focus match {
        case None => Consequence.success(Option.empty[Json])
        case Some(value) => _checks_c(value).map(Some(_))
      }
      _ <- if (declaration.nonEmpty && wire.nonEmpty && declaration != wire)
        Consequence.stateConflict("Review preparation has contradictory names for testChecks") else Consequence.unit
      progressdeclaration <- selected.hcursor.downField("repairProgress").focus match {
        case None => Consequence.success(Option.empty[Json])
        case Some(value) => _progress_c(value).map(Some(_))
      }
      progresswire <- selected.hcursor.downField("repair_progress").focus match {
        case None => Consequence.success(Option.empty[Json])
        case Some(value) => _progress_c(value).map(Some(_))
      }
      _ <- if (progressdeclaration.nonEmpty && progresswire.nonEmpty && progressdeclaration != progresswire)
        Consequence.stateConflict("Review preparation has contradictory names for repairProgress") else Consequence.unit
      issues <- selected.hcursor.downField("issues").focus match {
        case None => Consequence.success(Option.empty[Json])
        case Some(value) => _issues_c(value).map(Some(_))
      }
    } yield selected.mapObject { fields =>
      val purposefields = contract.fold(fields)(value => fields.add("purposeRepair", value))
      val canonical = metadata.fold(purposefields.remove("test_checks"))(
        value => purposefields.remove("test_checks").add("validationMetadata", value))
      val checks = declaration.orElse(wire).fold(canonical)(checks => canonical.add("testChecks", checks))
      val withissues = issues.fold(checks)(value => checks.add("issues", value))
      progressdeclaration.orElse(progresswire).fold(withissues.remove("repair_progress"))(
        value => withissues.remove("repair_progress").add("repairProgress", value))
    }
    else for {
      selected <- _project_c(value, _repository_names)
      checks <- selected.hcursor.downField("checks").focus.map(Consequence.success)
        .getOrElse(Consequence.stateConflict("Review preparation requires a checks array"))
      projected <- _checks_c(checks)
    } yield selected.mapObject(_.add("checks", projected))

  private def _progress_c(value: Json): Consequence[Json] = for {
    normalized <- _project_c(value, Vector("automaticCount" -> "automatic_count", "ordinaryCount" -> "ordinary_count",
      "runtimePolicy" -> "runtime_policy", "selectedLimit" -> "selected_limit", "effectiveLimit" -> "effective_limit"))
    snapshot <- normalized.as[GoalPhaseRepairProgress.Snapshot].fold(
      error => Consequence.stateConflict(error.message), Consequence.success)
  } yield snapshot.asJson

  private def _checks_c(value: Json): Consequence[Json] = for {
    checks <- value.asArray.map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Review preparation requires a checks array"))
    projected <- checks.foldLeft(Consequence.success(Vector.empty[Json])) { (prior, check) => for {
        values <- prior
        receipt <- _project_c(check, _check_names)
        sbt <- receipt.hcursor.downField("sbt").focus match {
          case None => Consequence.success(Option.empty[Json])
          case Some(facts) => _sbt_c(facts).map(Some(_))
        }
        selected = receipt.mapObject(fields => io.circe.JsonObject.fromIterable(fields.toVector.filter {
          case (key, _) => Set("identity", "termination", "exitCode")(key)
        }))
      } yield values :+ sbt.fold(selected)(facts => selected.mapObject(_.add("sbt", facts))) }
  } yield Json.fromValues(projected)

  // Opaque Record projection may use declaration or wire names. Normalize
  // only those names, then apply the same strict business-state decoder.
  private def _issues_c(value: Json): Consequence[Json] = for {
    fields <- value.asObject.map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Review preparation requires structured issues"))
    issues <- _issue_array_c(fields("issues"), Vector("sourceIdentity" -> "source_identity",
      "evidenceReference" -> "evidence_reference", "latestDisposition" -> "latest_disposition"))
    history <- _issue_array_c(fields("history"), Vector("issueIdentity" -> "issue_identity",
      "previousStatus" -> "previous_status", "evidenceReference" -> "evidence_reference"))
    movement <- fields("movement") match {
      case None => Consequence.success(Option.empty[Json])
      case Some(value) => _project_c(value, Vector("evidenceReference" -> "evidence_reference",
        "openIssues" -> "open_issues", "resolvedIssues" -> "resolved_issues", "newIssues" -> "new_issues",
        "reopenedIssues" -> "reopened_issues", "correspondenceKnown" -> "correspondence_known")).map(Some(_))
    }
    normalized = Json.fromJsonObject(movement.fold(fields.add("issues", issues).add("history", history))(
      m => fields.add("issues", issues).add("history", history).add("movement", m)))
    state <- normalized.as[GoalPhaseFixIssues.State].fold(error => Consequence.stateConflict(error.message), Consequence.success)
  } yield state.asJson

  private def _issue_array_c(value: Option[Json], names: Vector[(String, String)]): Consequence[Json] =
    value.flatMap(_.asArray).map { array =>
      array.foldLeft(Consequence.success(Vector.empty[Json])) { (prior, entry) => for {
        entries <- prior
        projected <- _project_c(entry, names)
      } yield entries :+ projected }.map(Json.fromValues)
    }.getOrElse(Consequence.stateConflict("Review preparation requires issue arrays"))

  private def _sbt_c(value: Json): Consequence[Json] = for {
    fields <- value.asObject.map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Review preparation requires structured sbt facts"))
    selection <- fields("selection") match {
      case Some(value) if !value.isNull => _project_c(value, _selection_names).map(Some(_))
      case value => Consequence.success(value)
    }
    normalized = Json.fromJsonObject(selection.fold(fields)(value => fields.add("selection", value)))
    facts <- normalized.as[WorkflowSbtValidation.Facts].fold(error => Consequence.stateConflict(error.message), Consequence.success)
  } yield facts.asJson

  private def _json_c(record: Record): Consequence[Json] = record.toJsonStringC.flatMap(text =>
    io.circe.parser.parse(text).fold(error => Consequence.stateConflict(error.message), Consequence.success))
  private def _exact(value: String): Boolean =
    value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
