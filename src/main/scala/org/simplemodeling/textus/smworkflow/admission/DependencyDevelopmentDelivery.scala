package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence

/** Configured ownership, separate from transport and from execution authority.
  * The existing bounded Workflow owns execution, commit and scoped publication.
  * @since   Nov. 12, 2026
  * @version Nov. 18, 2026
  */
private[smworkflow] object DependencyDevelopmentDelivery {
  val fixed = "Fixed"
  val assignedChat = "AssignedChat"
  val subtree = "DevelopmentSubtree"
  final case class Binding(edge: String, mode: String, chat: Option[String] = None)
  object Binding {
    given Encoder[Binding] = deriveEncoder
    given Decoder[Binding] = deriveDecoder
  }
  def validateC(definition: ManagedDevelopment.Definition, bindings: Option[Vector[Binding]]): Consequence[Unit] =
    if (bindings != null && bindings.forall(rows => rows != null && rows.forall(_ != null) && rows.map(_.edge).distinct == rows.map(_.edge) &&
      rows.forall(row => row != null && definition.edges.exists(_.identity == row.edge) &&
        Set(fixed, assignedChat, subtree)(row.mode) && row.chat != null &&
        (if (row.mode == assignedChat) row.chat.exists(exact) else row.chat.isEmpty)))) Consequence.unit
    else invalid("invalid dependency mode, declared edge or assigned chat")

  /** Historical graph declarations retain their existing subtree route. Explicit
    * Fixed is never converted to a managed/repair mode by Phase permission.
    */
  def binding(policy: ManagedDevelopmentRuntime.Policy): Option[Binding] =
    policy.dependencyWorkEdge.flatMap(edge => policy.delivery.toVector.flatten.find(_.edge == edge))

  def admitC(policy: ManagedDevelopmentRuntime.Policy, input: StandaloneFixExecution.Input): Consequence[Unit] = for {
    _ <- validateC(policy.definition, policy.delivery)
    _ <- binding(policy).fold(Consequence.unit) { selected =>
      val contract = input.delegation
      if (selected.mode == fixed && contract.exists(_.role == "destination"))
        invalid("dependency-change-required: Fixed dependency cannot execute; retain the consumer Session and await user direction")
      else if (selected.mode == assignedChat && !contract.exists(c => selected.chat.contains(c.destinationIdentity)))
        invalid("destination differs from the configured development chat")
      else Consequence.unit
    }
  } yield ()

  def fixedState(policy: Option[ManagedDevelopmentRuntime.Policy], state: StandaloneFixExecution.State): StandaloneFixExecution.State =
    if (policy.flatMap(binding).exists(_.mode == fixed) && DelegateTask.requester(state.input))
      state.copy(stage = "DependencyChangeRequired", message =
        "dependency-change-required: " + state.input.delegation.get.componentIdentity +
          " is Fixed; " + state.input.objective + ". Affected consumer work waits for user direction; no dispatch or dependency effects are admitted")
    else state

  def queued(policy: Option[ManagedDevelopmentRuntime.Policy], input: StandaloneFixExecution.Input): Boolean =
    !DelegateTask.requester(input) && input.delegation.nonEmpty && policy.flatMap(binding).exists(_.mode == assignedChat)

  private def exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Dependency delivery: " + reason)
}
