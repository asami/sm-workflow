/**
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference

/** An explicit projection of an independently retained original-purpose grant.
  * Neither Start selection, a path prefix nor host candidate registration creates
  * this grant. It lives in the existing external authority input, not a new ledger.
  */
private[smworkflow] object GoalPhaseInventoryAuthority {
  val purpose = "goal-phase-inventory-authority"
  val operations = Vector("edit-source", "validate", "stage", "commit")
  final case class Boundary(componentInstance: String, sessionId: String, participant: String,
    repository: String, planIdentity: String, stepIdentity: String, stepPurpose: String,
    purposeReference: ContextReference, usage: String, conditions: Json,
    permittedOperations: Vector[String], excludedPaths: Vector[String])
  object Boundary {
    given Encoder[Boundary] = deriveEncoder
    given Decoder[Boundary] = deriveDecoder
  }

  private def text(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  def valid(value: Boundary): Boolean = value != null && Vector(value.componentInstance, value.sessionId,
    value.participant, value.repository, value.planIdentity, value.stepIdentity, value.stepPurpose, value.usage).forall(text) &&
    value.purposeReference != null && Vector(value.purposeReference.identity, value.purposeReference.revision).forall(text) &&
    value.conditions != null && value.conditions.isArray && value.permittedOperations != null &&
    value.permittedOperations.distinct == value.permittedOperations && operations.forall(value.permittedOperations.contains) &&
    value.permittedOperations.forall(text) && value.excludedPaths != null && value.excludedPaths.distinct == value.excludedPaths &&
    value.excludedPaths.forall(path => text(path) && RepositorySyncNativeConflictInput.relative(path) &&
      !path.exists(c => "*?[]{}".contains(c)))

  def select(value: Json): Either[String, Json] = for {
    raw <- value.hcursor.get[Json]("scope").left.map(_.message)
    boundary <- raw.as[Boundary].left.map(_.message)
    _ <- Either.cond(valid(boundary) && raw == boundary.asJson &&
      value == Json.obj("componentInstance" -> boundary.componentInstance.asJson, "scope" -> raw),
      (), "Inventory authority requires its explicit original-purpose boundary and exact owner")
  } yield value

  def coversC(value: Boundary, target: GoalPhaseRemainingPlanAdoption.Target,
    owner: String, participant: String): Consequence[Unit] = {
    if (!valid(value) || target == null) return GoalPhaseNativeRun.invalid("Explicit inventory authority is absent or invalid")
    val inventory = target.currentInventory
    val step = target.plan.steps.find(_.identity == value.stepIdentity)
    val acceptance = target.plan.effectiveAcceptance
    val covered = valid(value) && inventory.exists(i => i.componentInstance == owner && i.stepIdentity == value.stepIdentity &&
      i.appendedPaths.forall(path => !value.excludedPaths.exists(excluded =>
        path == excluded || path.startsWith(excluded + "/") || excluded.startsWith(path + "/")))) &&
      value.componentInstance == owner && value.sessionId == target.sessionId && value.participant == participant &&
      value.repository == target.plan.repository && value.planIdentity == target.plan.identity &&
      step.exists(_.purpose == value.stepPurpose) && acceptance.exists(a => a.usage.contains(value.usage) &&
        a.conditions.asJson == value.conditions)
    if (covered) Consequence.unit else GoalPhaseNativeRun.invalid(
      "Retained inventory authority does not cover this Component, purpose, conditions, operations or literal exclusions; genuine Human direction remains required")
  }
}
