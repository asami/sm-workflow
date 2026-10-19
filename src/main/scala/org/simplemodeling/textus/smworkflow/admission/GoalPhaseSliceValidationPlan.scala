package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.validation.ValidationDiscoveryV1 as D
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceFilePath, WorkspaceId}
import GoalPhaseNativeMetadata as N
import GoalPhaseNativeValidation.Check

/** Explicit logical Slice selection, acquired only by the owning File UoW.
  * @since   Oct. 18, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseSliceValidationPlan {
  final case class Source(path: String, identity: String, sliceRef: String, acceptanceConditions: Vector[String])
  final case class Condition(identity: String, description: String)
  final case class Invocation(reference: D.OperationReference, parameters: Map[String, Json])
  final case class Coverage(condition: String, query: Boolean, operations: Vector[D.OperationReference])
  final case class Plan(identity: String, revision: String, sliceRef: String,
    acceptanceConditions: Vector[Condition], metadataSelection: D.SelectionQuery,
    additionalOperations: Vector[Invocation], executionParameters: Map[String, Json],
    acceptanceCoverageRefs: Vector[Coverage])

  val schemaVersion = "sm-workflow.slice-validation-plan.v1"
  import N.given

  private def _strict[A](decoder: Decoder[A], keys: Set[String])(valid: A => Boolean): Decoder[A] =
    Decoder.instance { cursor => cursor.value.asObject match {
      case Some(fields) if fields.keys.toSet == keys && fields.values.forall(!_.isNull) =>
        decoder.tryDecode(cursor).flatMap(value => Either.cond(valid(value), value,
          DecodingFailure("Invalid Slice validation plan value", cursor.history)))
      case _ => Left(DecodingFailure("Slice validation plan requires an exact non-null object", cursor.history))
    }}

  object Source {
    given Encoder[Source] = deriveEncoder
    given Decoder[Source] = _strict(deriveDecoder[Source],
      Set("path", "identity", "sliceRef", "acceptanceConditions"))(sourceValid)
  }
  object Condition {
    given Encoder[Condition] = deriveEncoder
    given Decoder[Condition] = _strict(deriveDecoder[Condition], Set("identity", "description"))(_condition_valid)
  }
  object Invocation {
    given Encoder[Invocation] = deriveEncoder
    given Decoder[Invocation] = _strict(deriveDecoder[Invocation], Set("reference", "parameters"))(_invocation_valid)
  }
  object Coverage {
    given Encoder[Coverage] = deriveEncoder
    given Decoder[Coverage] = _strict(deriveDecoder[Coverage], Set("condition", "query", "operations"))(_coverage_valid)
  }
  object Plan {
    given Encoder[Plan] = deriveEncoder
    given Decoder[Plan] = _strict(deriveDecoder[Plan], Set("identity", "revision", "sliceRef",
      "acceptanceConditions", "metadataSelection", "additionalOperations", "executionParameters",
      "acceptanceCoverageRefs"))(planValid)
  }

  private def _text(value: String): Boolean = N.runtimeIdentityValid(value) && value.trim.nonEmpty
  private def _bounded[A](values: Vector[A]): Boolean = values != null && values.size <= 16 && !values.contains(null)
  private def _conditions(values: Vector[String]): Boolean = _bounded(values) && values.nonEmpty &&
    values.forall(_text) && values.distinct == values
  private def _path(value: String): Boolean = value != null && value.nonEmpty && !value.exists(_.isControl) && !value.contains(":") &&
    !value.contains("\\") && WorkspaceFilePath.parseC(value).isSuccess
  def sourceValid(value: Source): Boolean = value != null && _path(value.path) && _text(value.identity) &&
    _text(value.sliceRef) && _conditions(value.acceptanceConditions)
  private def _condition_valid(value: Condition): Boolean = value != null && _text(value.identity) &&
    value.description != null && value.description.trim.nonEmpty
  private def _invocation_valid(value: Invocation): Boolean = value != null && N.referenceValid(value.reference) &&
    N.parametersValid(value.parameters)
  private def _coverage_valid(value: Coverage): Boolean = value != null && _text(value.condition) &&
    _bounded(value.operations) && value.operations.forall(N.referenceValid) && (value.query || value.operations.nonEmpty)
  def planValid(value: Plan): Boolean = value != null && _text(value.identity) && _text(value.revision) &&
    _text(value.sliceRef) && _bounded(value.acceptanceConditions) && value.acceptanceConditions.forall(_condition_valid) &&
    _conditions(value.acceptanceConditions.map(_.identity)) && N.queryValid(value.metadataSelection) &&
    _bounded(value.additionalOperations) && value.additionalOperations.forall(_invocation_valid) &&
    N.parametersValid(value.executionParameters) &&
    _bounded(value.acceptanceCoverageRefs) && value.acceptanceCoverageRefs.forall(_coverage_valid) &&
    value.acceptanceCoverageRefs.map(_.condition).distinct.size == value.acceptanceCoverageRefs.size &&
    value.acceptanceCoverageRefs.map(_.condition).toSet == value.acceptanceConditions.map(_.identity).toSet

  def decodeC(raw: String): Consequence[Plan] = {
    val decoded = for {
      json <- io.circe.parser.parse(Option(raw).getOrElse("")).left.map(_.message)
      fields <- json.asObject.toRight("Slice validation plan envelope must be an object")
      _ <- Either.cond(fields.keys.toSet == Set("schemaVersion", "payload") && fields.values.forall(!_.isNull), (),
        "Slice validation plan envelope requires exact non-null fields")
      schema <- json.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == schemaVersion, (), "Slice validation plan schema differs")
      plan <- json.hcursor.get[Plan]("payload").left.map(_.message)
    } yield plan
    decoded.fold(_invalid(_), Consequence.success)
  }

  /** Pure path/scope binding; the file need not exist when work is issued. */
  def bindingC(source: Source, scope: String, owned: Vector[String], candidates: Vector[String]): Consequence[Unit] =
    if (sourceValid(source) && source.sliceRef == scope && owned != null && candidates != null &&
        owned.contains(source.path) && candidates.contains(source.path)) Consequence.unit
    else _invalid("Slice validation source requires its exact selected scope, owned path and candidate path")

  def admitC(source: Source, plan: Plan): Consequence[Plan] =
    if (sourceValid(source) && planValid(plan) && source.identity == plan.identity &&
        source.sliceRef == plan.sliceRef && source.acceptanceConditions.toSet == plan.acceptanceConditions.map(_.identity).toSet)
      Consequence.success(plan)
    else _invalid("Slice validation plan differs from its exact source identity, slice or condition-ID set")

  /** The actual complete producer inventory and selected union decide coverage. */
  def coverageC(plan: Plan, inventory: D.Inventory, selected: Vector[D.OperationReference]): Consequence[Unit] =
    if (!planValid(plan) || inventory == null || inventory.completeness != D.Completeness.COMPLETE || selected == null)
      _invalid("Slice validation coverage requires a valid plan and actual COMPLETE selection")
    else D.selectC(inventory, plan.metadataSelection).flatMap { queried =>
      if (plan.acceptanceCoverageRefs.forall(c => (!c.query || queried.nonEmpty) && c.operations.forall(selected.contains)))
        Consequence.unit else _invalid("Slice validation condition coverage is absent from the actual query or selected union")
    }

  def bindingsC(checks: Vector[Check], scope: String, owned: Vector[String], candidates: Vector[String]): Consequence[Unit] =
    if (checks == null || checks.exists(c => c == null || c.metadata == null || c.metadata.exists(p => !N.profileValid(p))))
      _invalid("Slice validation source checks are invalid")
    else checks.flatMap(_.metadata.flatMap(_.source)).foldLeft(Consequence.unit) { (prior, source) =>
      prior.flatMap(_ => bindingC(source, scope, owned, candidates))
    }

  def instructionsC(checks: Vector[Check], scope: String, owned: Vector[String], candidates: Vector[String]): Consequence[String] =
    bindingsC(checks, scope, owned, candidates).map { _ =>
      val sources = checks.flatMap(check => check.metadata.flatMap(_.source).map(check.identity -> _))
      if (sources.isEmpty) "" else "\nSelected Slice validation-plan sources:\n" + sources.map { (host, source) =>
        "Check " + host + ": path=" + source.path + ", identity=" + source.identity + ", slice=" + source.sliceRef +
          ", required condition IDs=" + source.acceptanceConditions.mkString(", ")
      }.mkString("\n") + "\nAuthor or change only these issued contracts within the owned Candidate. " +
        "Implementation, repair and independent review must treat plan changes as Candidate work requiring independent review and admission. " +
        "Do not silently narrow required conditions, replace host scope, guess another filename or accept your own plan. " +
        "Runtime owns File acquisition, discovery, typed parameter binding, selection and check execution.\n"
    }

  def captureC(checks: Vector[Check], core: ActionCall.Core, scope: String,
    owned: Vector[String], candidates: Vector[String]): Consequence[Map[String, Plan]] = for {
    _ <- bindingsC(checks, scope, owned, candidates)
    captured <- checks.foldLeft(Consequence.success(Map.empty[String, Plan])) { (prior, check) => prior.flatMap { plans =>
      check.metadata.flatMap(_.source) match {
        case None => Consequence.success(plans)
        case Some(source) => for {
          workspace <- WorkspaceId.parseC("sm-workflow-repository")
          path <- WorkspaceFilePath.parseC(source.path)
          result <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), path)
          raw <- result.text.map(Consequence.success).getOrElse(_invalid("Selected Slice validation-plan file is missing: " + source.path))
          decoded <- decodeC(raw)
          plan <- admitC(source, decoded)
        } yield plans.updated(check.identity, plan)
      }
    }}
  } yield captured

  private def _invalid[A](message: String): Consequence[A] = Consequence.stateConflict(message)
}
