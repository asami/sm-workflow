package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import java.nio.file.Path
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseDependencyScope as D
import GoalPhaseNativeWork as W

/** Configured ownership, separate from transport and from execution authority.
  * The existing bounded Workflow owns execution, commit and local publication.
  * @since   Nov. 12, 2026
  * @version Nov. 23, 2026
  */
private[smworkflow] object DependencyDevelopmentDelivery {
  val fixed = "Fixed"
  val assignedChat = "AssignedChat"
  val subtree = "DevelopmentSubtree"
  val routeChoices = Vector(subtree, assignedChat)
  final case class Binding(edge: String, mode: String, chat: Option[String] = None)
  object Binding {
    given Encoder[Binding] = deriveEncoder
    given Decoder[Binding] = deriveDecoder
  }

  final case class Route(edge: ManagedDevelopment.Edge, scope: String, mode: String, destinationIdentity: String,
    sourceRoot: String, sourceBranch: String, artifactWorkspace: String, artifactRoot: String,
    artifactRepository: String, artifactStaging: String, machinePrograms: Option[ManagedDevelopmentRuntime.MachinePrograms] = None) {
    def binding: Binding = Binding(edge.identity, mode, Option.when(mode == assignedChat)(destinationIdentity))
  }
  object Route {
    given Encoder[Route] = deriveEncoder[Route].mapJson(_.mapObject(_.filter {
      case ("machinePrograms", value) => !value.isNull
      case _ => true
    }))
    given Decoder[Route] = deriveDecoder
  }
  final case class Alternative(choice: String, label: String, available: Boolean,
    reason: Option[String], route: Option[Route])
  object Alternative {
    given Encoder[Alternative] = deriveEncoder[Alternative].mapJson(_.dropNullValues)
    given Decoder[Alternative] = deriveDecoder
  }
  final case class Offer(alternatives: Vector[Alternative], defaultChoice: Option[String]) {
    def choices: Vector[String] = alternatives.collect { case value if value.available => value.choice }
    def route(choice: String): Option[Route] = alternatives.find(_.choice == choice).flatMap(_.route)
  }
  object Offer {
    given Encoder[Offer] = deriveEncoder[Offer].mapJson(_.dropNullValues)
    given Decoder[Offer] = deriveDecoder
  }

  def humanScopeApproval(choice: String): Boolean =
    choice == "ApproveDependencyScope" || routeChoices.contains(choice)

  def routeValid(route: Route, choice: String, proposal: D.Proposal): Boolean =
    route != null && route.edge != null && routeChoices.contains(choice) && route.mode == choice &&
      route.edge.consumer == proposal.requester && route.edge.provider == proposal.dependency &&
      route.artifactWorkspace == route.edge.localRepository &&
      ManagedDevelopment.validateC(ManagedDevelopment.Definition(route.scope, route.artifactWorkspace,
        route.artifactRepository, route.artifactStaging, Vector(route.edge))).isSuccess &&
      Vector(route.destinationIdentity, route.sourceRoot, route.sourceBranch, route.artifactRoot).forall(_exact) &&
      Path.of(route.sourceRoot).isAbsolute && Path.of(route.artifactRoot).isAbsolute &&
      (if (route.edge.localJar) route.machinePrograms == None
       else ManagedDevelopment.machineJarValid(route.edge) &&
         route.artifactRoot == Path.of(route.edge.machineLocalJar.get.consumerJar).getParent.toString &&
         Option(route.machinePrograms).flatten.exists(p => p != null && _exact(p.publication) && _exact(p.consumer) &&
           _exact(p.jarWorkspace) && p.discovery != null && p.discovery.componentIdentity == route.edge.consumer.identity &&
           p.discovery.validateC.isSuccess)) &&
      (choice != subtree || route.destinationIdentity == _subtree_destination(route.edge))

  def offerValid(offer: Offer, work: W.Request, result: W.Result): Boolean =
    offer != null && offer.alternatives.map(_.choice) == routeChoices &&
      _fresh_proposal(work, result).exists(proposal => offer.alternatives.forall(option =>
        option.label == _label(option.choice) && option.available == option.route.nonEmpty &&
          (if (option.available) option.reason.isEmpty else option.reason.exists(_exact)) &&
          option.route.forall(routeValid(_, option.choice, proposal)))) &&
      offer.defaultChoice == Option.when(offer.choices.contains(subtree))(subtree)

  def requiresDirection(work: W.Request, result: W.Result,
    @scala.annotation.unused configured: Option[ManagedDevelopmentRuntime.Policy]): Boolean =
    _fresh_proposal(work, result).nonEmpty

  def offer(work: W.Request, result: W.Result, configured: Option[ManagedDevelopmentRuntime.Policy],
    core: ActionCall.Core, publication: Option[LocalPublicationRuntime.Policy] = None): Option[Offer] =
    _fresh_proposal(work, result).map(_offer(_, configured, core, publication))

  def readmittedC(saved: Route, proposal: D.Proposal, policy: ManagedDevelopmentRuntime.Policy,
    core: ActionCall.Core, publication: Option[LocalPublicationRuntime.Policy] = None): Consequence[Unit] = {
    val current = _offer(proposal, Some(policy), core, publication)
    if (current.route(saved.mode).contains(saved)) Consequence.unit
    else _invalid(current.alternatives.find(_.choice == saved.mode).flatMap(_.reason)
      .getOrElse("selected dependency route crosses its registered source, scope, artifact owner or destination"))
  }

  private def _fresh_proposal(work: W.Request, result: W.Result): Option[D.Proposal] =
    result.dependencyProposal.filter(proposal =>
      Set("ImplementStep", "RepairStep")(work.kind) && result.outcome == "decision-needed" &&
        proposal.scopeUpdate.isEmpty && proposal.requester.repository == work.selectedPlan.repository &&
        proposal.authorizationBoundary.exists(boundary =>
          Set("edit-source", "publish-local").subsetOf(boundary.permittedOperations.toSet)) &&
        !work.decisionContext.exists(context =>
          (humanScopeApproval(context.choice) || context.choice == GoalPhasePolicyDependencyContinuation.choice) &&
            context.inventoryContinuation.map(_.proposal).orElse(context.requestingResult.dependencyProposal).contains(proposal)))

  private def _endpoints(edge: ManagedDevelopment.Edge, proposal: D.Proposal): Boolean =
    edge.provider == proposal.dependency && edge.consumer == proposal.requester

  private def _label(choice: String): String =
    if (choice == subtree) "Local worktree" else "Assigned development chat"

  private def _subtree_destination(edge: ManagedDevelopment.Edge): String = "managed-worktree:" + edge.provider.workspace

  private def _offer(proposal: D.Proposal, configured: Option[ManagedDevelopmentRuntime.Policy], core: ActionCall.Core,
    publication: Option[LocalPublicationRuntime.Policy]): Offer = {
    val edges = configured.toVector.flatMap(_.definition.edges.filter(_endpoints(_, proposal)))
    val active = edges match {
      case Vector(edge) if configured.exists(_.dependencyWorkEdge.contains(edge.identity)) => Some(edge)
      case _ => None
    }
    val chat = active.flatMap(_ => configured.flatMap(binding)).filter(_.mode == assignedChat).flatMap(_.chat)
    val common = for {
      policy <- configured.map(Consequence.success)
        .getOrElse(_invalid("no managed repository is registered for the proposed provider and consumer"))
      _ <- validateC(policy.definition, policy.delivery)
      edge <- (active, edges.isEmpty) match {
        case (Some(value), _) => Consequence.success(value)
        case (_, true) => _invalid("no managed repository is registered for the proposed provider and consumer")
        case _ => _invalid("no unique active managed repository edge matches the proposed provider and consumer")
      }
      current = binding(policy)
      _ <- if (current.exists(_.mode == fixed)) _invalid("Fixed dependency does not allow development delivery")
        else Consequence.unit
      _ <- if (edge.localJar || ManagedDevelopment.machineJarValid(edge)) Consequence.unit
        else _invalid("provider has no supported local JAR publication route")
      plan <- ManagedDevelopmentRuntime.executionPlanC(core)
      member <- ManagedDevelopmentBoundedWork.selectC(Some(policy), edge.provider.repository, plan, None)
        .recoverWith(failure => _invalid("provider Git workspace: " + failure.displayMessage))
        .flatMap(_.map(Consequence.success).getOrElse(_invalid("provider has no registered managed repository/worktree")))
      artifact <- edge.machineLocalJar match {
        case None => _scoped_root_c(core, plan, policy.definition, edge, member.root)
          .map(_ -> Option.empty[ManagedDevelopmentRuntime.MachinePrograms])
        case Some(jar) => for {
          _ <- ManagedDevelopmentRuntime.machineTargetC(edge, publication)
          programs <- ManagedDevelopmentRuntime.machineProgramsC(edge, plan)
        } yield Path.of(jar.consumerJar).getParent.toString -> Some(programs)
      }
    } yield (policy.definition, edge, member, artifact)
    val alternatives = routeChoices.map { choice =>
      val selected = common.flatMap { case (definition, edge, member, (owner, programs)) =>
        val destination = if (choice == subtree) Some(_subtree_destination(edge))
          else chat
        destination.map(identity => Consequence.success(Route(edge, definition.scope, choice, identity,
          member.root, member.localBranch, definition.ownerWorkspace, owner, definition.repository, definition.staging, programs)))
          .getOrElse(_invalid("no assigned development chat is registered for this edge"))
      }
      selected.map(route => Alternative(choice, _label(choice), true, None, Some(route))).fold(failure => {
        val message = "no assigned development chat is registered for this edge"
        val reason = if (choice == assignedChat && chat.isEmpty && !failure.displayMessage.contains(message))
          failure.displayMessage + ". " + message
          else failure.displayMessage
        Alternative(choice, _label(choice), false, Some(reason), None)
      }, identity)
    }
    Offer(alternatives, Option.when(alternatives.head.available)(subtree))
  }

  private def _scoped_root_c(core: ActionCall.Core, plan: io.circe.Json, definition: ManagedDevelopment.Definition,
    edge: ManagedDevelopment.Edge, sourceroot: String): Consequence[String] = for {
    owner <- ManagedDevelopmentRuntime.workspaceRootC(core, definition.ownerWorkspace)
    _ <- if (!Path.of(owner).normalize.startsWith(Path.of(sourceroot)) &&
        !Path.of(sourceroot).startsWith(Path.of(owner).normalize)) Consequence.unit
      else _invalid("scope-private artifact workspace crosses the provider source root")
    programs <- plan.hcursor.get[Vector[io.circe.Json]]("sbtPrograms").fold(_ => _invalid("scoped publication is unregistered"), Consequence.success)
    selected = programs.filter(program => program.hcursor.get[String]("target").contains(edge.provider.workspace) &&
      program.hcursor.get[String]("operation").contains("scoped-publish"))
    _ <- if (selected.size == 1) Consequence.unit
      else _invalid("provider does not declare one scoped-publish program")
    capability <- selected.head.hcursor.get[String]("capability").fold(
      _ => _invalid("scoped publication has no capability"), Consequence.success)
    _ <- if (plan.hcursor.get[Vector[io.circe.Json]]("grants").toOption.toVector.flatten.exists(grant =>
        grant.hcursor.get[String]("componentId").contains(SmWorkflowComponent.componentId.name) &&
        grant.hcursor.get[Vector[String]]("capabilities").toOption.exists(_.contains(capability))))
      Consequence.unit else _invalid("provider scoped-publish capability has no Component grant")
    _ <- if (plan.hcursor.get[Vector[io.circe.Json]]("workspaceGrants").toOption.toVector.flatten.exists(grant =>
        grant.hcursor.get[String]("componentId").contains(SmWorkflowComponent.componentId.name) &&
          grant.hcursor.get[String]("workspaceId").contains(definition.ownerWorkspace) &&
          grant.hcursor.get[String]("access").contains("manage"))) Consequence.unit
      else _invalid("scope-private artifact workspace has no managed grant")
  } yield owner
  def validateC(definition: ManagedDevelopment.Definition, bindings: Option[Vector[Binding]]): Consequence[Unit] =
    if (bindings != null && bindings.forall(rows => rows != null && rows.forall(_ != null) && rows.map(_.edge).distinct == rows.map(_.edge) &&
      rows.forall(row => row != null && definition.edges.exists(_.identity == row.edge) &&
        Set(fixed, assignedChat, subtree)(row.mode) && row.chat != null &&
        (if (row.mode == assignedChat) row.chat.exists(_exact) else row.chat.isEmpty)))) Consequence.unit
    else _invalid("invalid dependency mode, declared edge or assigned chat")

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
        _invalid("dependency-change-required: Fixed dependency cannot execute; retain the consumer Session and await user direction")
      else if (selected.mode == assignedChat && !contract.exists(c => selected.chat.contains(c.destinationIdentity)))
        _invalid("destination differs from the configured development chat")
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

  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Dependency delivery: " + reason)
}
