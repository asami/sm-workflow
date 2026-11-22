package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.Path
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.SbtValidationDiscovery
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import DependencyDevelopmentDelivery as Delivery
import GoalPhaseDependencyScope as Scope
import ManagedDevelopment as Managed

/** Dependency JAR use and the delivery route are independently captured.
  * @since   Nov. 23, 2026
  * @version Nov. 23, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class MachineLocalJarContractSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _provider = Scope.Component("org.example.Provider", "provider-repository", "provider-worktree")
  private val _consumer = Scope.Component("org.example.Consumer", "consumer-repository", "consumer-workspace")
  private val _coordinate = Managed.Coordinate("org.example.provider", "provider_3", "1.2.3-SNAPSHOT")
  private val _scoped = Managed.Edge("consumer-provider", _provider, _consumer, "development-artifacts",
    localJar = true, localProject = Some("provider-project"), requested = _coordinate)
  private val _ivy_jar = "/fixture/.ivy2/local/org.example.provider/provider_3/1.2.3-SNAPSHOT/jars/provider_3.jar"
  private val _maven_jar = "/fixture/.m2/repository/org/example/provider/provider_3/1.2.3-SNAPSHOT/provider_3-1.2.3-SNAPSHOT.jar"
  private val _machine = _scoped.copy(localJar = false, localProject = None,
    machineLocalJar = Some(Managed.MachineLocalJar("provider-publish-local", _ivy_jar)))
  private val _machine_programs = ManagedDevelopmentRuntime.MachinePrograms("publish-provider", "test-compile-consumer",
    SbtValidationDiscovery.Binding(_consumer.identity, "org.example:consumer_3", "1.0.0-SNAPSHOT", "org.example.consumer"), "machine-local-jar")
  private val _input = Managed.Input(_scoped.identity, "dependency-request", "publication", "consumer-selection")
  private val _proposal = Scope.Proposal("provider-change", "1", "IMPLEMENTATION", _consumer, _provider,
    ContextReference("provider-usage", "1"), "Update the provider contract", Vector("src/main/scala/Provider.scala"),
    Vector("Keep the public API compatible"), Vector(ContextReference("consumer-step", "1")), Vector.empty)
  private val _subtree_destination = "managed-worktree:" + _provider.workspace
  private val _assigned_chat = Delivery.Binding(_scoped.identity, Delivery.assignedChat, Some("registered-provider-chat"))

  private def _definition(edge: Managed.Edge): Managed.Definition =
    Managed.Definition("consumer-lane", edge.localRepository, "artifacts/ivy", "artifacts/staging", Vector(edge))

  private def _route(edge: Managed.Edge, mode: String, destination: String): Delivery.Route = {
    val definition = _definition(edge)
    val artifactroot = edge.machineLocalJar.map(binding => Path.of(binding.consumerJar).getParent.toString)
      .getOrElse("/fixture/private-artifacts")
    Delivery.Route(edge, definition.scope, mode, destination, "/fixture/provider-worktree", "work/provider",
      definition.ownerWorkspace, artifactroot, definition.repository, definition.staging,
      machinePrograms = edge.machineLocalJar.map(_ => _machine_programs))
  }

  "Managed dependency JAR use" should {
    "read a legacy scoped selection without adding machine-local fields or dropping its project annotation" in {
      Given("the legacy wire shape of a scoped edge with a project annotation")
      val legacy = Json.obj(
        "identity" -> _scoped.identity.asJson,
        "provider" -> _provider.asJson,
        "consumer" -> _consumer.asJson,
        "localRepository" -> _scoped.localRepository.asJson,
        "localJar" -> true.asJson,
        "localProject" -> "provider-project".asJson,
        "requested" -> _coordinate.asJson)

      When("the existing scoped operation receives the decoded edge")
      val edge = legacy.as[Managed.Edge].toOption.get
      val selected = Managed.edgeC(_definition(edge), _input)

      Then("the same edge is admitted and written in the same legacy shape")
      selected shouldBe Consequence.success(_scoped)
      edge.machineLocalJar shouldBe None
      edge.asJson shouldBe legacy
    }

    "require an explicit machine-local declaration to use ordinary coordinates without entering scoped publication" in {
      Given("an Ivy local location, a Maven local location unsupported by publishLocal, and an undeclared binding")
      val machine = _machine.machineLocalJar.get
      val ivy = _machine
      val maven = _machine.copy(machineLocalJar = Some(machine.copy(consumerJar = _maven_jar)))
      val absent = _machine.copy(machineLocalJar = None)

      When("the declared edges are checked for delivery and for scoped publication")
      val routes = Vector(ivy, maven, absent).map(_route(_, Delivery.subtree, _subtree_destination))

      Then("only the layout produced by ordinary publishLocal is offered for delivery")
      Managed.validateC(_definition(ivy)).isSuccess shouldBe true
      Managed.validateC(_definition(maven)).isSuccess shouldBe false
      Vector(ivy, maven, absent).foreach(e => Managed.edgeC(_definition(e), _input).isSuccess shouldBe false)
      routes.map(Delivery.routeValid(_, Delivery.subtree, _proposal)) shouldBe Vector(true, false, false)
    }

    "reject malformed ordinary JAR bindings and conflicting consumption modes" in {
      Given("a machine-local edge with either an invalid target, a foreign or non-normalized path, or a conflicting mode")
      val machine = _machine.machineLocalJar.get
      val batch = Managed.Batch(Vector(Managed.ProjectModule("provider", _coordinate)), Vector(_coordinate), None)
      val malformed = Vector(
        "missing publication target" -> _machine.copy(machineLocalJar = Some(machine.copy(publicationTarget = ""))),
        "relative classpath entry" -> _machine.copy(machineLocalJar = Some(machine.copy(consumerJar = _ivy_jar.drop(1)))),
        "foreign module" -> _machine.copy(machineLocalJar = Some(machine.copy(consumerJar = _ivy_jar.replace("provider_3", "other_3")))),
        "foreign version" -> _machine.copy(machineLocalJar = Some(machine.copy(consumerJar = _ivy_jar.replace("1.2.3", "2.0.0")))),
        "parent traversal" -> _machine.copy(machineLocalJar = Some(machine.copy(
          consumerJar = "/fixture/elsewhere/.." + _ivy_jar.stripPrefix("/fixture")))),
        "scoped JAR" -> _machine.copy(localJar = true),
        "source project" -> _machine.copy(localProject = _scoped.localProject),
        "private batch" -> _machine.copy(batch = Some(batch)))

      When("each edge is presented as the managed definition and as the selected worktree route")
      val results = malformed.map { case (reason, edge) =>
        val route = _route(edge, Delivery.subtree, _subtree_destination)
        (reason, Managed.validateC(_definition(edge)), Delivery.routeValid(route, Delivery.subtree, _proposal))
      }

      Then("each incompatible declaration is rejected at both boundaries")
      results.foreach { case (reason, definition, route) =>
        withClue(reason) {
          definition.isSuccess shouldBe false
          route shouldBe false
        }
      }
    }
  }

  "Dependency delivery routes" should {
    "retain either JAR use with the captured worktree or registered chat and its exact source and scope" in {
      Given("the same provider and consumer with a scoped assignment and an explicit machine-local assignment for each owner")
      val routes = Vector(
        _route(_scoped, Delivery.subtree, _subtree_destination),
        _route(_scoped, Delivery.assignedChat, _assigned_chat.chat.get),
        _route(_machine, Delivery.subtree, _subtree_destination),
        _route(_machine, Delivery.assignedChat, _assigned_chat.chat.get))
      val crossed = _proposal.copy(dependency = _provider.copy(workspace = "different-provider-worktree"))

      When("the machine-local selection is available and each retained route is read from its own wire value")
      val wire = routes.map(_.asJson)
      val retained = wire.map(_.as[Delivery.Route].toOption.get)

      Then("each assignment keeps its selected JAR, coordinates, scope, source root and delivery owner")
      retained shouldBe routes
      retained.map(_.edge.machineLocalJar) shouldBe Vector(None, None, _machine.machineLocalJar, _machine.machineLocalJar)
      retained.map(_.machinePrograms) shouldBe Vector(None, None, Some(_machine_programs), Some(_machine_programs))
      wire.take(2).foreach(_.hcursor.downField("machinePrograms").succeeded shouldBe false)
      retained.filter(_.mode == Delivery.assignedChat).map(_.binding) shouldBe Vector(_assigned_chat, _assigned_chat)
      retained.foreach { route =>
        Delivery.routeValid(route, route.mode, _proposal) shouldBe true
        Delivery.routeValid(route, route.mode, crossed) shouldBe false
      }
    }

    "require captured publication and consumer programs and the expected JAR directory for machine-local delivery" in {
      Given("a machine-local worktree route with its publication, consumer compile and discovery bindings")
      val route = _route(_machine, Delivery.subtree, _subtree_destination)

      When("each route is checked with its complete binding, missing programs or a crossed artifact root")
      val foreign = _machine_programs.copy(discovery = _machine_programs.discovery.copy(componentIdentity = _provider.identity))
      val candidates = Vector(route, route.copy(machinePrograms = None), route.copy(artifactRoot = "/fixture/foreign-artifacts"),
        route.copy(machinePrograms = Some(foreign)), route.copy(machinePrograms = Some(_machine_programs.copy(jarWorkspace = ""))))
      val admitted = candidates.map(Delivery.routeValid(_, Delivery.subtree, _proposal))

      Then("only the route that retains both programs and the exact JAR directory is admitted")
      admitted shouldBe Vector(true, false, false, false, false)
    }
  }
}
