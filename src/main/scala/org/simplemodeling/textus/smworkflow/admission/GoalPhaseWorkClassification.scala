package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/** Application planning input only. Classification selects semantic intent;
  * neither a model, effort, worker identity nor execution permission is input.
  * The public skill-facing contract remains Phase 6 work.
  * @since   Oct.  8, 2026
  *  version Oct. 11, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseWorkClassification {
  enum Kind { case PLANNING, ANALYSIS, DESIGN, CODING, REVIEW, JUDGMENT }
  enum Complexity { case TRIVIAL, SIMPLE, STANDARD, COMPLEX }
  enum Domain { case PROGRAMMING, ENGINEERING }
  enum Footprint { case SMALL, MEDIUM, LARGE }

  final case class Classification(kind: Kind, complexity: Complexity, domain: Domain, footprint: Footprint) {
    def validateC: Consequence[Classification] =
      if (kind == null || complexity == null || domain == null || footprint == null)
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
        reasoningClass = Some(ReasoningClassReference(workname + domainname + intensity)))
  }

  /** Planning explicitly supplies all three Step frontiers, not a Skill's
    * decision to select or call an execution route.
    */
  final case class Assignment(implementation: Classification, repair: Classification, review: Classification) {
    def validateC: Consequence[Assignment] =
      if (implementation == null || repair == null || review == null ||
          implementation.kind != Kind.CODING || repair.kind != Kind.CODING || review.kind != Kind.REVIEW)
        Consequence.stateConflict("GoalPhase classification must bind implementation, repair and independent review")
      else for { _ <- implementation.validateC; _ <- repair.validateC; _ <- review.validateC } yield this
  }

  given Encoder[Classification] = Encoder.instance(value => Json.obj(
    "kind" -> Json.fromString(value.kind.toString), "complexity" -> Json.fromString(value.complexity.toString),
    "domain" -> Json.fromString(value.domain.toString), "footprint" -> Json.fromString(value.footprint.toString)))
  given Decoder[Classification] = Decoder.instance { cursor =>
    def enumeration[A](key: String, values: Array[A]): Decoder.Result[A] =
      cursor.get[String](key).flatMap(value => values.find(_.toString == value).toRight(
        io.circe.DecodingFailure("Unsupported WorkClassification " + key, cursor.history)))
    for {
      kind <- enumeration("kind", Kind.values)
      complexity <- enumeration("complexity", Complexity.values)
      domain <- enumeration("domain", Domain.values)
      footprint <- enumeration("footprint", Footprint.values)
    } yield Classification(kind, complexity, domain, footprint)
  }
  given Encoder[Assignment] = deriveEncoder
  given Decoder[Assignment] = deriveDecoder
}
