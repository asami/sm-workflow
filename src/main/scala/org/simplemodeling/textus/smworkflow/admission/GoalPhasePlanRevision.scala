package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.record.Record
import GoalPhaseOrderedProgression as O
import GoalPhaseAcceptanceBoundary as A
import GoalPhaseDocumentSource as D
import GoalPhaseDocumentPlan as P

/** Pure complete-Plan proposal and the question owner's captured business input.
  * Neither value certifies consent, transport integrity or execution authority.
  * @since   Oct. 18, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhasePlanRevision {
  final case class Proposal(identity: String, revision: String, reason: String, plan: O.Plan)
  object Proposal {
    given Encoder[Proposal] = deriveEncoder
    given Decoder[Proposal] = _decoder(deriveDecoder[Proposal],
      Set("identity", "revision", "reason", "plan"), validateProposalC)
  }
  // These finite codecs belong to saved revision input, not the document parser.
  private given Encoder[D.Source] = deriveEncoder
  private given Decoder[D.Source] = _decoder(deriveDecoder[D.Source], Set("phasePath", "checklistPath"),
    value => D.validateC(value).map(_ => ()))
  private given Encoder[P.Binding] = deriveEncoder
  private given Decoder[P.Binding] = _decoder(deriveDecoder[P.Binding], Set("step", "items"), value =>
    _check(value != null && _stable_id(value.step) && value.items != null && value.items.nonEmpty &&
      value.items.forall(_stable_id), "document binding is invalid"))
  private given Encoder[P.Item] = deriveEncoder
  private given Decoder[P.Item] = _decoder(deriveDecoder[P.Item], Set("identity", "acceptance", "checked"), value =>
    _check(value != null && _stable_id(value.identity) && value.acceptance != null &&
      value.acceptance.trim.nonEmpty, "document item is invalid"))
  private given Encoder[D.Bound] = deriveEncoder
  private given Decoder[D.Bound] = _decoder(deriveDecoder[D.Bound], Set("source", "bindings", "items"),
    value => D.instructionsC(Some(value), None).map(_ => ()))

  val remainingPurposeScope = "remaining-purpose"
  val phaseRepairInventoryScope = "phase-repair-inventory"
  val phaseAcceptanceDeferralScope = "phase-acceptance-deferral"
  final case class Captured(proposal: Proposal, plan: O.Plan, documents: Option[D.Bound] = None,
    revisionScope: Option[String] = None)
  object Captured {
    given Encoder[Captured] = Encoder.instance(value => Json.obj(
      "proposal" -> value.proposal.asJson, "plan" -> value.plan.asJson).mapObject { fields =>
      val documents = value.documents.fold(fields)(documents => fields.add("documents", documents.asJson))
      value.revisionScope.fold(documents)(scope => documents.add("revisionScope", Json.fromString(scope)))
    })
    given Decoder[Captured] = _decoder(deriveDecoder[Captured], Set("proposal", "plan"),
      validateCapturedC, Set("documents", "revisionScope"))
  }

  def validateOptionC(value: Option[Proposal]): Consequence[Unit] =
    if (value == null) _check(false, "proposal Option is null")
    else value.fold(Consequence.unit)(validateProposalC)

  def validateCapturedOptionC(value: Option[Captured]): Consequence[Unit] =
    if (value == null) _check(false, "captured Option is null")
    else value.fold(Consequence.unit)(validateCapturedC)

  /** Interpret every structured public name through CNCF, then use the exact
    * owning codec. Even equal colliding aliases are ambiguous business input.
    */
  def normalizeJsonC(value: Json): Consequence[Json] =
    if (value == null) Consequence.stateConflict("GoalPhase Plan revision JSON is null")
    else value.asObject match {
      case Some(fields) =>
        val named = fields.toVector.map { (key, nested) =>
          (RuntimeContext.PropertyNameContext.default.normalizeInputName(key), nested)
        }
        if (named.map(_._1).distinct.size != named.size)
          Consequence.stateConflict("GoalPhase Plan revision public names collide")
        else named.foldLeft(Consequence.success(Vector.empty[(String, Json)])) { (prior, field) => for {
          accumulated <- prior
          nested <- normalizeJsonC(field._2)
        } yield accumulated :+ (field._1 -> nested) }.map(values => Json.obj(values*))
      case None => value.asArray match {
        case Some(values) => values.foldLeft(Consequence.success(Vector.empty[Json])) { (prior, nested) => for {
          accumulated <- prior
          normalized <- normalizeJsonC(nested)
        } yield accumulated :+ normalized }.map(Json.fromValues)
        case None => Consequence.success(value)
      }
    }

  def fromPublicProposalC(value: Option[Record]): Consequence[Option[Proposal]] =
    if (value == null || value.exists(_ == null))
      Consequence.stateConflict("GoalPhase Plan revision public Option is null")
    else value.fold(Consequence.success(Option.empty[Proposal])) { record => for {
      text <- record.toJsonStringC
      json <- io.circe.parser.parse(text).fold(error => Consequence.stateConflict(error.message), Consequence.success)
      normalized <- normalizeJsonC(json)
      proposal <- normalized.as[Proposal].fold(error => Consequence.stateConflict(error.message), Consequence.success)
      _ <- _check(proposal.plan.phaseRepairInventory.isEmpty || normalized == proposal.asJson,
        "Phase repair inventory proposal requires exactly its complete canonical non-null fields")
    } yield Some(proposal) }

  def publicProposal(value: Option[Proposal]): Option[Record] =
    value.map(proposal => GoalPhaseNativeMetadata.record(proposal.asJson))
  def publicCaptured(value: Option[Captured]): Option[Record] =
    value.map(captured => GoalPhaseNativeMetadata.record(captured.asJson))

  def validateProposalC(value: Proposal): Consequence[Unit] = for {
    _ <- _check(value != null && _identity(value.identity) && _identity(value.revision) &&
      _reason(value.reason), "proposal identity, revision or reason is invalid")
    _ <- O.validatePlanC(value.plan)
    _ <- _check(value.plan.effectiveAcceptance.isEmpty, "proposal must contain a raw complete Plan")
  } yield ()

  def validateCapturedC(value: Captured): Consequence[Unit] = for {
    _ <- _check(value != null, "captured business input is absent")
    _ <- validateProposalC(value.proposal)
    _ <- O.validatePlanC(value.plan)
    _ <- A.savedPlanC(value.plan)
    _ <- _check(value.plan.copy(effectiveAcceptance = None) == value.proposal.plan,
      "captured Plan differs from its explicit raw proposal")
    _ <- _check(value.documents != null, "captured documents Option is null")
    _ <- _check(value.revisionScope != null && value.revisionScope.forall(scope =>
      Set(remainingPurposeScope, phaseRepairInventoryScope, phaseAcceptanceDeferralScope)(scope)),
      "captured revision scope is invalid")
    _ <- _check(!value.revisionScope.exists(Set(remainingPurposeScope, phaseAcceptanceDeferralScope)) || value.documents.isEmpty,
      "remaining-purpose and Phase acceptance deferral require absent selected documents")
    _ <- value.documents.fold(Consequence.unit)(_documents_c(value.plan, _))
  } yield ()

  def captureC(controller: O.State, proposal: Proposal, project: Option[A.Project]): Consequence[Captured] = for {
    _ <- validateProposalC(proposal)
    plan <- A.captureC(project, proposal.plan)
    captured = Captured(proposal, plan)
    _ <- validateCapturedC(captured)
    _ <- _check(controller != null, "controller is absent")
    _ <- eligibleC(controller, captured)
  } yield captured

  /** Fresh semantic questions select this branch from the actual Plan delta.
    * An ordinary capture failure must never be reinterpreted as another scope.
    */
  def captureSemanticProposalC(controller: O.State, proposal: Proposal, project: Option[A.Project],
    documents: Option[D.Bound] = None): Consequence[Captured] = for {
    _ <- _check(controller != null && documents != null, "semantic capture source is absent")
    _ <- validateProposalC(proposal)
    captured <- if (controller.stage == O.Stage.Decision && controller.decisionSource.contains(O.Stage.PhaseReview) &&
        controller.cursor == controller.plan.steps.size && controller.completed.size == controller.plan.steps.size &&
        proposal.plan.steps.size == controller.plan.steps.size && proposal.plan.acceptance != controller.plan.acceptance) for {
      _ <- _check(documents.isEmpty, "Phase acceptance deferral requires absent selected documents")
      _ <- _check(controller.plan.acceptance.nonEmpty && proposal.plan.acceptance.nonEmpty,
        "Phase acceptance deferral requires raw adopted conditions")
      changed = controller.plan.acceptance.get.conditions.zip(proposal.plan.acceptance.get.conditions)
        .collect { case (prior, next) if prior != next => prior.identity }
      plan = proposal.plan.copy(effectiveAcceptance = controller.plan.effectiveAcceptance.map(effective =>
        effective.copy(conditions = effective.conditions.map(condition =>
          if (changed.contains(condition.identity)) condition.copy(required = false) else condition))))
      selected = Captured(proposal, plan, revisionScope = Some(phaseAcceptanceDeferralScope))
      _ <- adoptedC(controller, selected, "proposal-eligibility")
    } yield selected else if (proposal.plan.phaseRepairInventory != controller.plan.phaseRepairInventory) for {
      plan <- Consequence.success(proposal.plan.copy(effectiveAcceptance = controller.plan.effectiveAcceptance))
      selected = Captured(proposal, plan, documents, Some(phaseRepairInventoryScope))
      _ <- validateCapturedC(selected)
      _ <- controller.adoptPhaseRepairInventoryC(plan, "proposal-eligibility")
    } yield selected else if (_future_purpose_changed(controller.plan, controller.cursor, proposal.plan)) for {
      _ <- _check(documents.isEmpty, "remaining-purpose requires absent selected documents")
      _ <- _check(proposal.plan.acceptance == controller.plan.acceptance,
        "remaining-purpose must retain raw acceptance")
      // Preserve the already selected boundary, independent of later project defaults.
      plan = proposal.plan.copy(effectiveAcceptance = controller.plan.effectiveAcceptance)
      selected = Captured(proposal, plan, revisionScope = Some(remainingPurposeScope))
      _ <- adoptedC(controller, selected, "proposal-eligibility")
    } yield selected else captureC(controller, proposal, project)
  } yield captured

  /** Pure delta guard shared by fresh capture, saved questions and adoption. */
  def validateRemainingPurposeC(previous: O.Plan, cursor: Int, proposed: O.Plan): Consequence[Unit] = for {
    _ <- O.validatePlanC(previous)
    _ <- O.validatePlanC(proposed)
    _ <- A.savedPlanC(previous)
    _ <- A.savedPlanC(proposed)
    _ <- _check(cursor >= 0 && cursor < previous.steps.size,
      "remaining-purpose requires a current unaccepted Step")
    _ <- _check(proposed.copy(revision = previous.revision, steps = previous.steps) == previous &&
      proposed.revision != previous.revision && proposed.steps.size == previous.steps.size &&
      proposed.steps.take(cursor) == previous.steps.take(cursor) &&
      proposed.steps.drop(cursor).zip(previous.steps.drop(cursor)).forall { (next, prior) =>
        next.copy(purpose = prior.purpose) == prior
      } && _future_purpose_changed(previous, cursor, proposed),
      "remaining-purpose may change only unaccepted purposes and must change a future Step purpose")
  } yield ()

  def validateCapturedScopeC(previous: O.Plan, cursor: Int, captured: Captured): Consequence[Unit] = for {
    _ <- validateCapturedC(captured)
    _ <- O.validatePlanC(previous)
    _ <- captured.revisionScope match {
      case Some(`phaseAcceptanceDeferralScope`) => O.phaseAcceptanceDeferralConditionsC(previous, captured.plan).map(_ => ())
      case Some(`phaseRepairInventoryScope`) => O.phaseRepairInventoryPathsC(previous, captured.plan).map(_ => ())
      case Some(`remainingPurposeScope`) => validateRemainingPurposeC(previous, cursor, captured.plan)
      case None => _check(!(cursor == previous.steps.size && captured.plan.steps.size == previous.steps.size &&
          captured.plan.acceptance != previous.acceptance) &&
        previous.phaseRepairInventory == captured.plan.phaseRepairInventory &&
        !_future_purpose_changed(previous, cursor, captured.plan),
        "future purpose changes require explicit remaining-purpose scope")
      case _ => _check(false, "captured revision scope is invalid")
    }
  } yield ()

  private def _future_purpose_changed(previous: O.Plan, cursor: Int, proposed: O.Plan): Boolean =
    previous.steps.drop(cursor + 1).zip(proposed.steps.drop(cursor + 1)).exists { (prior, next) =>
      prior.purpose != next.purpose
    }

  def captureDocumentsC(controller: O.State, proposal: Proposal, project: Option[A.Project],
    previous: D.Bound, imported: P.Imported): Consequence[Captured] = for {
    _ <- _check(imported != null && proposal != null && imported.plan == proposal.plan,
      "imported document Plan differs from its explicit raw proposal")
    captured <- captureC(controller, proposal, project)
    _ <- _check(previous != null, "previous documents are absent")
    selected = captured.copy(documents = Some(D.Bound(previous.source, imported.bindings, imported.items)))
    _ <- validateDocumentsC(controller, selected, Some(previous))
  } yield selected

  /** Saved capture is paired with its actual prior business selection, without IO. */
  def validateDocumentsC(controller: O.State, captured: Captured,
    previous: Option[D.Bound]): Consequence[Unit] = for {
    _ <- validateCapturedC(captured)
    _ <- _check(previous != null, "previous documents Option is null")
    _ <- (previous, captured.documents) match {
      case (None, None) => Consequence.unit
      case (Some(prior), Some(selected)) => _compatible_documents_c(controller, captured.plan, prior, selected)
      case _ => _check(false, "captured document presence differs from its predecessor")
    }
  } yield ()

  /** Describing or retaining a proposal is not authority to adopt it. Exact
    * current inventory may reach its pending question so the separate public
    * supersession owner can authenticate a Human directive or retained boundary.
    * Ordinary AdoptPlanRevision continues to use its unchanged stricter guard.
    */
  def eligibleC(controller: O.State, captured: Captured): Consequence[Unit] = for {
    _ <- validateCapturedC(captured)
    _ <- _check(controller != null, "controller is absent")
    _ <- if (captured.revisionScope.contains(phaseAcceptanceDeferralScope))
      adoptedC(controller, captured, "proposal-eligibility").map(_ => ())
      else if (controller.cursor < controller.plan.steps.size && captured.plan.steps.size == controller.plan.steps.size &&
        captured.plan.steps.map(_.ownedPaths) != controller.plan.steps.map(_.ownedPaths))
      controller.adoptCurrentInventoryRevisionC(captured.plan, "proposal-eligibility").map(_ => ())
      else controller.adoptRevisionC(captured.plan, "proposal-eligibility").map(_ => ())
  } yield ()

  def adoptedC(controller: O.State, captured: Captured, evidence: String): Consequence[O.State] = for {
    _ <- validateCapturedC(captured)
    _ <- _check(controller != null, "controller is absent")
    _ <- validateCapturedScopeC(controller.plan, controller.cursor, captured)
    adopted <- captured.revisionScope match {
      case Some(`phaseAcceptanceDeferralScope`) => controller.adoptPhaseAcceptanceDeferralC(captured.plan, evidence)
      case Some(`phaseRepairInventoryScope`) => controller.adoptPhaseRepairInventoryC(captured.plan, evidence)
      case None => controller.adoptRevisionC(captured.plan, evidence)
      case Some(`remainingPurposeScope`) => for {
        _ <- validateRemainingPurposeC(controller.plan, controller.cursor, captured.plan)
        _ <- _check(controller.cursor == controller.completed.size &&
          controller.completed.map(_.step) == controller.plan.steps.take(controller.cursor).map(_.identity) &&
          controller.completed.forall(O.validStepAcceptance) &&
          controller.repairs < controller.effectiveRepairLimit && controller.repairControl.flatMap(_.stop).isEmpty &&
          !controller.policyRepairPending && controller.releaseCommit.isEmpty,
          "remaining-purpose requires an exact accepted prefix and unexhausted repair scope")
        next <- controller.adoptRemainingRevisionC(captured.plan, evidence)
      } yield next
      case _ => Consequence.stateConflict("GoalPhase Plan revision scope is invalid")
    }
  } yield adopted

  /** Eligibility from the actual requesting business result, never Human consent. */
  def validatePhaseAcceptanceDeferralSourceC(work: GoalPhaseNativeWork.Request,
    result: GoalPhaseNativeWork.Result, captured: Captured): Consequence[Unit] = for {
    _ <- validateCapturedC(captured)
    _ <- if (!captured.revisionScope.contains(phaseAcceptanceDeferralScope)) Consequence.unit else for {
      _ <- _check(work != null && result != null, "Phase acceptance deferral source is absent")
      _ <- O.validatePlanC(work.selectedPlan)
      _ <- _check(work != null && result != null && work.kind == "ReviewPhase" && work.stepIdentity != null &&
        work.stepIdentity.isEmpty && work.completed != null &&
        work.completed.map(_.step) == work.selectedPlan.steps.map(_.identity) && work.completed.forall(O.validStepAcceptance) &&
        work.candidate != null && work.candidate.exists(s => s != null &&
          s.head != null && s.tree != null && s.head.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})") && s.tree.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) &&
        work.checksReceipt != null && work.checksReceipt.exists(_reason) &&
        result.outcome == "decision-needed" && result.findings != null && result.findings.nonEmpty &&
        result.findings.forall(_reason) && result.planRevision.contains(captured.proposal) &&
        work.documents != null && work.documents.isEmpty,
        "Phase acceptance deferral requires its exact actual all-closed ReviewPhase decision and proposal")
      changed <- O.phaseAcceptanceDeferralConditionsC(work.selectedPlan, captured.plan)
      _ <- A.admitC(work.selectedPlan, work.kind, result)
      _ <- _check(result.acceptance.exists(report => changed.forall(id => report.conditions.exists(observation =>
        observation.identity == id && Set("unsatisfied", "not-evaluated")(observation.status)))),
        "Every deferred condition requires its actual prior unsatisfied or not-evaluated observation")
    } yield ()
  } yield ()

  private def _documents_c(plan: O.Plan, value: D.Bound): Consequence[Unit] = for {
    _ <- D.instructionsC(Some(value), None)
    _ <- _check(value.bindings.map(_.step) == plan.steps.map(_.identity),
      "document bindings differ from the exact selected Plan order")
  } yield ()

  private def _compatible_documents_c(controller: O.State, plan: O.Plan,
    previous: D.Bound, selected: D.Bound): Consequence[Unit] = for {
    _ <- _check(controller != null && controller.cursor >= 0 && controller.cursor < controller.plan.steps.size,
      "documents require a current unclosed Step")
    _ <- _documents_c(controller.plan, previous)
    _ <- _documents_c(plan, selected)
    _ <- _check(previous.source == selected.source && previous.bindings == selected.bindings,
      "document source or item membership changed")
    editable = previous.bindings(controller.cursor).items.toSet
    _ <- _check(previous.items.forall { prior =>
      selected.items.find(_.identity == prior.identity).exists { item =>
        if (editable(prior.identity)) item.checked == prior.checked else item == prior
      }
    }, "document checked flags or acceptance outside the current Step changed")
  } yield ()

  private def _decoder[T](raw: Decoder[T], keys: Set[String], validate: T => Consequence[Unit],
    optional: Set[String] = Set.empty): Decoder[T] =
    Decoder.instance { cursor =>
      for {
        _ <- Either.cond(cursor.value.asObject.exists(o => keys.subsetOf(o.keys.toSet) &&
          o.keys.toSet.subsetOf(keys ++ optional)) && _non_null(cursor.value), (),
          DecodingFailure("Plan revision requires exactly its non-null business fields", cursor.history))
        value <- raw(cursor)
        _ <- Either.cond(validate(value).isSuccess, (),
          DecodingFailure("Plan revision business input is invalid", cursor.history))
      } yield value
    }
  private def _non_null(value: Json): Boolean = !value.isNull &&
    value.asObject.forall(_.values.forall(_non_null)) && value.asArray.forall(_.forall(_non_null))
  private def _bounded(value: String, maximum: Int): Boolean = value != null && value.trim.nonEmpty &&
    UTF_8.newEncoder().canEncode(value) && value.getBytes(UTF_8).length <= maximum
  private def _identity(value: String): Boolean = _bounded(value, 128) &&
    !value.exists(c => Character.isISOControl(c))
  private def _stable_id(value: String): Boolean =
    value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
  private def _reason(value: String): Boolean = _bounded(value, 16384) && !value.contains('\u0000')
  private def _check(valid: Boolean, reason: String): Consequence[Unit] =
    if (valid) Consequence.unit else Consequence.stateConflict("GoalPhase Plan revision: " + reason)
}
