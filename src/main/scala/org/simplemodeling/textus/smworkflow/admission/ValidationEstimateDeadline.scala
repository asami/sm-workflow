package org.simplemodeling.textus.smworkflow.admission

import scala.concurrent.duration.*
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M}
import org.goldenport.cncf.processexecution.SbtValidationEstimate

/** Estimates describe the selected leaf inventory, never individual timers.
  * @since   Oct. 30, 2026
  * @version Oct. 30, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object ValidationEstimateDeadline {
  val defaultLeafKey = "sm-workflow.validation.default-leaf-duration-millis"
  val multiplierKey = "sm-workflow.validation.deadline-multiplier"

  final case class Settings(defaultLeaf: FiniteDuration = 1.second, multiplier: BigDecimal = BigDecimal("1.5"))
  final case class Preparation(startup: FiniteDuration, compilation: FiniteDuration,
    sharedPreparation: FiniteDuration, cleanup: FiniteDuration) {
    def durations: Vector[FiniteDuration] = Vector(startup, compilation, sharedPreparation, cleanup)
  }
  enum Classification {
    case ADMISSION, ORDINARY, HEAVY
  }
  final case class Invocation(operation: D.Operation, parameters: Map[String, Json] = Map.empty)
  final case class Leaf(reference: D.OperationReference, selector: D.ScalaTestSelector,
    provenance: D.Provenance, parameters: Map[String, Json], classification: Classification,
    expectedNanos: Long, usedDefault: Boolean)
  final case class Plan(selectionReason: String, leaves: Vector[Leaf], preparation: Preparation,
    settings: Settings, bodyNanos: Long, preparationNanos: Long, executionTimeoutMillis: Long)

  def executionEstimateC(plan: Plan): Consequence[SbtValidationEstimate] = for {
    _ <- if (plan != null && plan.leaves.nonEmpty && plan.leaves.map(_.provenance.sourceRevision).distinct.size == 1)
      Consequence.unit else invalid("One exact source revision is required for an execution estimate")
    estimate = SbtValidationEstimate(plan.selectionReason, plan.leaves.head.provenance.sourceRevision,
      plan.leaves.map(leaf => SbtValidationEstimate.Leaf(leaf.selector.suiteClassName, leaf.selector.testName,
        leaf.expectedNanos, leaf.usedDefault, Option.when(leaf.parameters.nonEmpty)(leaf.parameters))),
      SbtValidationEstimate.Preparation(durationNanos(plan.preparation.startup).longValue,
        durationNanos(plan.preparation.compilation).longValue, durationNanos(plan.preparation.sharedPreparation).longValue,
        durationNanos(plan.preparation.cleanup).longValue), plan.settings.multiplier)
    actual <- estimate.timeoutMillisC
    _ <- if (actual == plan.executionTimeoutMillis) Consequence.unit
      else invalid("Consumer and CNCF aggregate deadline differ")
  } yield estimate

  /** The call's ordinary resolved properties own configuration. No argv/env parsing. */
  def settingsC(parameters: ResolvedParameters): Consequence[Settings] = for {
    _ <- if (parameters != null) Consequence.unit else invalid("Resolved configuration is required")
    leaf <- numberC(parameters, defaultLeafKey, BigDecimal(1000))
    multiplier <- numberC(parameters, multiplierKey, BigDecimal("1.5"))
    _ <- if (leaf.isWhole && leaf.isValidLong && leaf > 0 &&
        BigInt(leaf.toLong) * 1000000 <= BigInt(Long.MaxValue) && multiplier > 0)
      Consequence.unit else invalid("Default leaf duration and multiplier must be positive representable values")
  } yield Settings(leaf.toLong.millis, multiplier)

  /** CNCF has already resolved operation overrides over class defaults. Every
    * reported parameterized instance must occur as its own actual selector.
    * Nested publication belongs in that leaf's estimate, not Preparation.
    */
  def planC(operations: Vector[D.Operation], reason: String, preparation: Preparation,
    settings: Settings = Settings()): Consequence[Plan] =
    if (operations == null) invalid("Selected leaf inventory is required")
    else planInvocationsC(operations.map(Invocation(_)), reason, preparation, settings)

  def planInvocationsC(invocations: Vector[Invocation], reason: String, preparation: Preparation,
    settings: Settings = Settings()): Consequence[Plan] = for {
    _ <- if (invocations != null && invocations.nonEmpty && invocations.forall(i => i != null &&
        validOperation(i.operation) && i.parameters != null && i.parameters.forall { case (key, value) =>
          key != null && key.trim.nonEmpty && value != null && !value.isNull }) &&
        // Inputs describe the corresponding registered body; they cannot turn
        // one SBT selector into multiple independently executable invocations.
        invocations.map(i => (i.operation.selector.suiteClassName, i.operation.selector.testName))
          .distinct.size == invocations.size &&
        reason != null && reason.trim.nonEmpty && preparation != null && settings != null &&
        positive(settings.defaultLeaf) && settings.multiplier != null && settings.multiplier > 0 &&
        preparation.durations.forall(d => d != null && durationNanos(d) >= 0)) Consequence.unit
      else invalid("A nonempty unambiguous leaf inventory, selection reason and explicit preparation estimates are required")
    leaves <- invocations.foldLeft(Consequence.success(Vector.empty[Leaf])) { (prior, invocation) => for {
      values <- prior
      operation = invocation.operation
      metadata = operation.metadata.get
      _ <- if (metadata.expectedDuration != null && metadata.expectedDuration.forall(positive)) Consequence.unit
        else invalid("Invalid resolved leaf expectedDuration")
      purposes = metadata.purposes
      _ <- if (!(purposes.contains(M.Purpose.ADMISSION) && purposes.contains(M.Purpose.HEAVY))) Consequence.unit
        else invalid("A leaf cannot be both ADMISSION and HEAVY")
      classification = if (purposes.contains(M.Purpose.HEAVY)) Classification.HEAVY
        else if (purposes.contains(M.Purpose.ADMISSION)) Classification.ADMISSION else Classification.ORDINARY
      estimate = durationNanos(metadata.expectedDuration.getOrElse(settings.defaultLeaf))
    } yield values :+ Leaf(operation.reference, operation.selector, operation.provenance, invocation.parameters,
      classification, estimate.longValue, metadata.expectedDuration.isEmpty) }
    body = leaves.foldLeft(BigInt(0))((n, leaf) => n + leaf.expectedNanos)
    overhead = preparation.durations.foldLeft(BigInt(0))((n, duration) => n + durationNanos(duration))
    millis = ((BigDecimal(body + overhead) * settings.multiplier) / BigDecimal(1000000))
      .setScale(0, BigDecimal.RoundingMode.CEILING).toBigInt
    _ <- if (body.isValidLong && overhead.isValidLong && millis.isValidLong && millis > 0) Consequence.unit
      else invalid("The estimated execution deadline exceeds the supported duration range")
  } yield Plan(reason, leaves, preparation, settings, body.longValue, overhead.longValue, millis.longValue)

  private def durationNanos(value: FiniteDuration): BigInt = BigInt(value.length) * value.unit.toNanos(1L)
  private def positive(value: FiniteDuration): Boolean = value != null &&
    durationNanos(value) > 0 && durationNanos(value).isValidLong
  private def validOperation(value: D.Operation): Boolean = value != null && value.reference != null &&
    value.selector != null && value.selector.suiteClassName != null && value.selector.testName != null &&
    value.selector.suiteClassName.nonEmpty && value.selector.testName.nonEmpty && value.provenance != null &&
    value.provenance.sourceRevision != null && value.provenance.sourceRevision.nonEmpty &&
    value.diagnostics != null && value.diagnostics.isEmpty && value.metadata != null &&
    value.metadata.exists(m => m != null && m.purposes != null)
  private def numberC(parameters: ResolvedParameters, key: String, default: BigDecimal): Consequence[BigDecimal] =
    parameters.get(key) match {
      case None => Consequence.success(default)
      case Some(parameter) => parameter.value match {
        case ConfigurationValue.NumberValue(number) if number != null => Consequence.success(number)
        case _ => invalid(key + " requires a numeric configuration value")
      }
    }
  private def invalid[A](message: String): Consequence[A] = Consequence.stateConflict("Validation estimate: " + message)
}
