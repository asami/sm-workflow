package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.value as V

/** Semantic intent for Phase planning and bounded Tasks. Execution policy
  * owns model, effort, worker identity and permission.
  * @since   Oct.  8, 2026
  *  version Oct. 11, 2026
  *  version Oct. 16, 2026
  * @version Nov. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseWorkClassification {
  enum Kind { case PLANNING, ANALYSIS, DESIGN, CODING, REVIEW, JUDGMENT }
  enum Complexity { case TRIVIAL, SIMPLE, STANDARD, COMPLEX }
  enum Domain { case PROGRAMMING, ENGINEERING }
  enum Footprint { case SMALL, MEDIUM, LARGE }

  final case class Classification(kind: Kind, complexity: Complexity, domain: Domain, footprint: Footprint, nature: Option[String] = None) {
    def validateC: Consequence[Classification] =
      if (kind == null || complexity == null || domain == null || footprint == null || nature == null ||
          nature.exists(n => !Set("mechanical", "parent-inline", "deterministic", "hardest")(n)) ||
          (nature.contains("mechanical") && kind != Kind.CODING))
        Consequence.stateConflict("WorkClassification is incomplete")
      else Consequence.success(this)

    def requirementC(base: ExecutionRequirement): Consequence[ExecutionRequirement] =
      if (base == null) Consequence.stateConflict("WorkClassification has no logical execution requirement")
      else for {
        _ <- validateC
        admitted <- base.validateC
        _ <- if ((kind == Kind.REVIEW) == admitted.reviewRequired) Consequence.unit else
          Consequence.stateConflict("WorkClassification kind contradicts the assigned review boundary")
        derived <- semanticRequirementC(admitted)
      } yield derived

    /** Split semantic kind is independent of its inherited review obligation. */
    private[smworkflow] def semanticRequirementC(base: ExecutionRequirement): Consequence[ExecutionRequirement] =
      if (base == null) Consequence.stateConflict("WorkClassification has no logical execution requirement")
      else for {
        _ <- validateC
        admitted <- base.validateC
        level = if (kind == Kind.REVIEW) {
          if (complexity == Complexity.COMPLEX || footprint == Footprint.LARGE) ReasoningLevel.Critical
          else ReasoningLevel.Standard
        } else complexity match {
          case Complexity.TRIVIAL | Complexity.SIMPLE => ReasoningLevel.Routine
          case Complexity.STANDARD => ReasoningLevel.Standard
          case Complexity.COMPLEX => ReasoningLevel.Deep
        }
        workname = kind.toString.toLowerCase.capitalize
        domainname = if (kind == Kind.CODING) domain.toString.toLowerCase.capitalize else ""
        intensity = level.value.toLowerCase.capitalize
      } yield admitted.copy(reasoning = level,
        reasoningClass = Some(ReasoningClassReference(workname + domainname +
          (if (nature.contains("mechanical")) "Mechanical" else intensity))))
  }

  /** Each assignment describes the three semantic frontiers independently. */
  final case class Assignment(implementation: Classification, repair: Classification, review: Classification) {
    def validateC: Consequence[Assignment] =
      if (implementation == null || repair == null || review == null ||
          implementation.kind != Kind.CODING || repair.kind != Kind.CODING || review.kind != Kind.REVIEW)
        Consequence.stateConflict("GoalPhase classification must bind implementation, repair and independent review")
      else for { _ <- implementation.validateC; _ <- repair.validateC; _ <- review.validateC } yield this

    def validateTaskC(taskKind: String): Consequence[Assignment] = {
      val expected = if (taskKind == "documentation") Kind.DESIGN else Kind.CODING
      if (!Set("code", "documentation")(taskKind) || implementation == null || repair == null || review == null ||
          implementation.kind != expected || repair.kind != expected || review.kind != Kind.REVIEW)
        Consequence.stateConflict("Task classification must bind its implementation, repair and independent review")
      else for {
        _ <- implementation.validateC
        _ <- repair.validateC
        _ <- review.validateC
        _ <- if (Vector(implementation, repair, review).exists(_.nature.contains("deterministic")))
          Consequence.stateConflict("Deterministic work cannot replace a Task semantic assignment") else Consequence.unit
      } yield this
    }
  }

  def taskInputC(value: Option[V.WorkflowTaskWorkClassification]): Consequence[Option[Assignment]] = value match {
    case None => Consequence.success(None)
    case Some(work) if work != null => for {
      implementation <- _task_classification_c(work.implementation)
      repair <- _task_classification_c(work.repair)
      review <- _task_classification_c(work.review)
    } yield Some(Assignment(implementation, repair, review))
    case _ => Consequence.stateConflict("Task workClassification is incomplete")
  }

  def taskValue(value: Assignment): V.WorkflowTaskWorkClassification =
    V.WorkflowTaskWorkClassification(_task_value(value.implementation), _task_value(value.repair), _task_value(value.review))

  private def _task_classification_c(value: V.WorkflowSemanticWorkClassification): Consequence[Classification] = {
    def _member_c_[A](field: String, value: String, values: Array[A]): Consequence[A] =
      values.find(_.toString == value).map(Consequence.success).getOrElse(
        Consequence.stateConflict("Unsupported WorkClassification " + field))
    if (value == null) Consequence.stateConflict("Task workClassification is incomplete")
    else for {
      kind <- _member_c_("kind", value.kind, Kind.values)
      complexity <- _member_c_("complexity", value.complexity, Complexity.values)
      domain <- _member_c_("domain", value.domain, Domain.values)
      footprint <- _member_c_("footprint", value.footprint, Footprint.values)
      result <- Classification(kind, complexity, domain, footprint, value.nature).validateC
    } yield result
  }

  private def _task_value(value: Classification): V.WorkflowSemanticWorkClassification =
    V.WorkflowSemanticWorkClassification(value.kind.toString, value.complexity.toString, value.domain.toString,
      value.footprint.toString, value.nature)

  /** Reconstruct only retained semantic intent. Invocation-time mapping owns
    * concrete placement; a phase-wide repair has no selected Step to invent.
    */
  def selectedClassification(kind: String, stepIdentity: Option[String],
    selectedPlan: GoalPhaseOrderedProgression.Plan): Option[Classification] =
    if (kind == "ReviewPhase") selectedPlan.phaseReviewClassification
      else if (kind == "RepairStep" && stepIdentity.isEmpty) {
        selectedPlan.steps.map(_.workClassification.map(_.repair)).distinct match {
          case Vector(Some(value)) => Some(value)
          case _ => None
        }
      } else stepIdentity.flatMap(identity => selectedPlan.steps.find(_.identity == identity))
        .flatMap(_.workClassification).map { work => kind match {
          case "ImplementStep" => work.implementation
          case "RepairStep" => work.repair
          case _ => work.review
        }}

  def requirementC(kind: String, stepIdentity: Option[String],
    selectedPlan: GoalPhaseOrderedProgression.Plan, base: ExecutionRequirement): Consequence[ExecutionRequirement] =
    selectedClassification(kind, stepIdentity, selectedPlan) match {
      case Some(value) => value.requirementC(base)
      case None => Consequence.success(base)
    }

  given Encoder[Classification] = Encoder.instance(value => Json.obj(
    "kind" -> Json.fromString(value.kind.toString), "complexity" -> Json.fromString(value.complexity.toString),
    "domain" -> Json.fromString(value.domain.toString), "footprint" -> Json.fromString(value.footprint.toString)).mapObject(fields =>
      value.nature.fold(fields)(n => fields.add("nature", Json.fromString(n)))))
  given Decoder[Classification] = Decoder.instance { cursor =>
    def enumeration[A](key: String, values: Array[A]): Decoder.Result[A] =
      cursor.get[String](key).flatMap(value => values.find(_.toString == value).toRight(
        io.circe.DecodingFailure("Unsupported WorkClassification " + key, cursor.history)))
    for {
      kind <- enumeration("kind", Kind.values)
      complexity <- enumeration("complexity", Complexity.values)
      domain <- enumeration("domain", Domain.values)
      footprint <- enumeration("footprint", Footprint.values)
      nature <- cursor.get[Option[String]]("nature")
      value = Classification(kind, complexity, domain, footprint, nature)
      _ <- Either.cond(value.validateC.isSuccess, (), io.circe.DecodingFailure("Invalid work nature", cursor.history))
    } yield value
  }
  given Encoder[Assignment] = deriveEncoder
  given Decoder[Assignment] = deriveDecoder
}
