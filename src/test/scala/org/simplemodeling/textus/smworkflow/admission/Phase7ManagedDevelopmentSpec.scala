package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import cats.data.NonEmptyVector
import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.protocol.{Property, Request}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.preparation.{NativeDelegateTaskStartInput, NativeDelegateTaskAdoptionPreparation}
import ManagedDevelopment.*
import SplitPhaseExplicitPartitionFixture.take

/** Minimal real producer -> private repository -> immutable selection -> consumer
  * compilation. Fixture setup creates inputs; SBT effects use CNCF's native UoW.
  * @since   Nov. 12, 2026
  * @version Nov. 21, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class Phase7ManagedDevelopmentSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture with Phase7DependencyDevelopmentDeliveryBehaviors {
  private val edge = Edge("consumer-provider",
    GoalPhaseDependencyScope.Component("org.example.Provider", "provider-repository", TypedGitHostPlanFixture.workspace),
    GoalPhaseDependencyScope.Component("org.example.Consumer", "consumer-repository", "development-consumer"),
    "development-artifacts", true, None, Coordinate("org.example.smworkflow-p7", "provider_2.12", "0.0.1-SNAPSHOT"))
  private val definition = Definition("lane-a", "development-artifacts", "artifacts/ivy", "staging", Vector(edge))
  private val collection = ThreeWorkflowNormalHostFixture.collection("managed_development")
  private val policy = ManagedDevelopmentRuntime.Policy("workflow", collection, definition)
  private val input = Input(edge.identity, "publish-one", "publication-one", "producer-input-one")

  private class Memory extends Port {
    val rows = scala.collection.mutable.Map.empty[String, Json]
    var calls = 0
    var execution = Execution("succeeded", Json.obj("termination" -> "Exited(0)".asJson))
    var completedOutput = true
    val participant = "owner"
    val source = Json.obj("head" -> ("a" * 40).asJson, "dirty" -> Vector("actual-change.scala").asJson)
    def loadC(key: String) = Consequence.success(rows.get(key))
    def createC(key: String, value: Json): Consequence[Unit] =
      if (rows.contains(key)) invalid("duplicate native record") else { rows(key) = value; Consequence.unit }
    def sourceC(e: Edge) = Consequence.success(source)
    def executeC(e: Edge, i: Input, selected: Option[Publication]) = { calls += 1; Consequence.success(execution) }
    def publicationC(e: Edge, i: Input, origin: Json): Consequence[Publication] =
      if (!completedOutput) invalid("required completed adapter output is unavailable")
      else Consequence.success(Publication(e, definition.scope,
        i.publication, i.selection, e.requested.copy(version = version(e.requested.version, definition.scope, i.publication)),
        "/owner/artifacts/jar", "/owner/artifacts/ivy", origin, Json.obj(), Json.obj()))
    def consumerReportC(e: Edge, i: Input, p: Publication) = Consequence.success(Json.obj("actual" -> true.asJson))
  }
  private def run(operation: String, m: Memory, value: Input = input) =
    take(runC(operation, definition, value, m))

  private class OwnedMemory extends Memory {
    val deleted = scala.collection.mutable.ArrayBuffer.empty[String]
    var dependencies = Map.empty[String, Vector[Coordinate]]
    var deleteOutcome = "Deleted"
    override def inventoryC(pub: Publication) = Consequence.success(Some(OwnedInventory(Vector(
      base(definition, pub.edge, pub.identity) + "/jars/" + pub.selected.module + ".jar",
      base(definition, pub.edge, pub.identity) + "/ivys/ivy.xml",
      base(definition, pub.edge, pub.identity) + "/completed.json"), dependencies.getOrElse(pub.identity, Vector.empty))))
    override def deleteC(path: String) = { deleted += path; Consequence.success(deleteOutcome) }
    override def detachC(e: Edge, i: Input) = { calls += 1; Consequence.success(execution) }
  }

  "Minimal managed development" should {
    "resolve only the admitted dependency source and preserve its native Session root" in {
      Given("an edge selecting B's managed checkout and a separate canonical checkout")
      def repository(workspace: String, identity: String, destination: String = "main") = Json.obj(
        "target" -> workspace.asJson, "identity" -> identity.asJson, "localBranch" -> "work/b".asJson,
        "remote" -> "origin".asJson, "remoteBranch" -> destination.asJson, "endpoint" -> "explicit-b-remote".asJson)
      val plan = Json.obj("workspaces" -> Vector(
        Json.obj("identity" -> edge.provider.workspace.asJson, "root" -> "/fixture/selected-b".asJson),
        Json.obj("identity" -> "canonical-b".asJson, "root" -> "/fixture/canonical-b".asJson)).asJson,
        "gitRepositories" -> Vector(repository(edge.provider.workspace, edge.provider.repository),
          repository("canonical-b", "canonical-provider")).asJson)
      val selected = Some(policy.copy(repositorySyncEdge = Some(edge.identity)))
      def resolve(p: Json = plan, retained: Option[String] = None, route: Option[String] = Some("direct")) =
        ManagedDevelopmentRepositorySync.selectC(selected, edge.provider.repository, p, retained, route)
      When("normal RepositorySync resolves its target before Git effects")
      val member = take(resolve()).get
      Then("only the provider workspace participates, independently of JAR/project consumption")
      member.workspace shouldBe edge.provider.workspace
      member.root shouldBe "/fixture/selected-b"
      member.remoteBranch shouldBe "main"
      take(resolve(retained = Some(member.root))).get shouldBe member
      resolve(retained = Some("/fixture/canonical-b")).isSuccess shouldBe false
      resolve(route = None).isSuccess shouldBe false
      resolve(route = Some("local-first")).isSuccess shouldBe false
      val nonMain = plan.mapObject(_.add("gitRepositories", Vector(repository(edge.provider.workspace,
        edge.provider.repository, "release/next"), repository("canonical-b", "canonical-provider")).asJson))
      take(resolve(nonMain, route = None)).get.remoteBranch shouldBe "release/next"
      val missing = plan.mapObject(_.add("gitRepositories", Vector(repository("canonical-b", "canonical-provider")).asJson))
      resolve(missing).isSuccess shouldBe false
      val crossed = plan.mapObject(_.add("gitRepositories", Vector(repository(edge.provider.workspace, "foreign-repository")).asJson))
      resolve(crossed).isSuccess shouldBe false
      take(ManagedDevelopmentRepositorySync.selectC(selected, "unrelated-root", plan, None, None)) shouldBe None
      take(ManagedDevelopmentRepositorySync.selectC(Some(policy), edge.provider.repository, plan, None, None)) shouldBe None
      val independent = selected.map(p => p.copy(definition = definition.copy(edges = Vector(edge.copy(localJar = false,
        localProject = Some("explicit-project-binding"))))))
      take(ManagedDevelopmentRepositorySync.selectC(independent, edge.provider.repository, plan, None, Some("direct"))).get shouldBe member
    }

    "select the managed B bounded owner without moving a retained Session or redirecting unrelated work" in {
      val selectedEdge = edge.copy(provider = edge.provider.copy(workspace = "managed-provider"))
      val selectedPolicy = policy.copy(definition = definition.copy(edges = Vector(selectedEdge)),
        dependencyWorkEdge = Some(selectedEdge.identity))
      val plan = Json.obj("workspaces" -> Vector(
        Json.obj("identity" -> "managed-provider".asJson, "root" -> "/fixture/managed-b".asJson),
        Json.obj("identity" -> "canonical-provider".asJson, "root" -> "/fixture/canonical-b".asJson)).asJson,
        "gitRepositories" -> Vector(Json.obj("target" -> "managed-provider".asJson,
          "identity" -> selectedEdge.provider.repository.asJson, "localBranch" -> "work/b".asJson,
          "remote" -> "origin".asJson, "remoteBranch" -> "main".asJson, "endpoint" -> "configured-b".asJson)).asJson)
      val member = take(ManagedDevelopmentBoundedWork.selectC(Some(selectedPolicy), selectedEdge.provider.repository, plan, None)).get
      member.workspace shouldBe "managed-provider"
      member.root shouldBe "/fixture/managed-b"
      member.localBranch shouldBe "work/b"
      take(ManagedDevelopmentBoundedWork.selectC(Some(selectedPolicy), selectedEdge.provider.repository, plan,
        Some(member.root -> member.localBranch))).get shouldBe member
      ManagedDevelopmentBoundedWork.selectC(Some(selectedPolicy), selectedEdge.provider.repository, plan,
        Some("/fixture/canonical-b" -> "main")).isSuccess shouldBe false
      take(ManagedDevelopmentBoundedWork.selectC(Some(selectedPolicy), "unrelated-task", plan, None)) shouldBe None
      take(ManagedDevelopmentBoundedWork.selectC(Some(policy), selectedEdge.provider.repository, plan, None)) shouldBe None
      val independent = selectedPolicy.copy(definition = selectedPolicy.definition.copy(edges = Vector(selectedEdge.copy(
        localJar = false, localProject = Some("explicit-project")))))
      take(ManagedDevelopmentBoundedWork.selectC(Some(independent), selectedEdge.provider.repository, plan, None)).get shouldBe member
    }

    "require an explicit scoped delivery target and exact development coordinates instead of shared publication" in {
      val selected = policy.copy(dependencyWorkEdge = Some(edge.identity))
      val base = take(DelegateTask.inputC(DelegateTaskRequesterRoutingFixture.input("bounded-delivery")))
      val destination = base.copy(repositoryIdentity = edge.provider.repository,
        delegation = base.delegation.map(_.copy(role = "destination", componentIdentity = edge.provider.identity,
          publicationTarget = Some(edge.identity), artifactCoordinates = Vector(edge.requested.organization + ":" +
            edge.requested.module + ":" + version(edge.requested.version, definition.scope, base.requestIdentity)))))
      val input = take(ManagedDevelopmentBoundedWork.publicationInputC(selected, destination))
      input.publication shouldBe base.requestIdentity
      input.edgeIdentity shouldBe edge.identity
      ManagedDevelopmentBoundedWork.publicationInputC(selected, destination.copy(delegation = destination.delegation.map(
        _.copy(publicationTarget = Some("shared-publishLocal"))))).isSuccess shouldBe false
      ManagedDevelopmentBoundedWork.publicationInputC(selected, destination.copy(delegation = destination.delegation.map(
        _.copy(artifactCoordinates = Vector(edge.requested.organization + ":" + edge.requested.module + ":" + edge.requested.version))))).isSuccess shouldBe false
      ManagedDevelopmentBoundedWork.publicationInputC(selected, destination.copy(requestIdentity = "unbounded request identity")).isSuccess shouldBe false
    }

    "refuse canonical validation aliases that do not cover the selected B worktree" in {
      val member = RepositorySyncDirectoryList.Member(edge.provider.repository, "managed-b", "/fixture/b", "main",
        "origin", "main", "configured", "session")
      val checks = take(StandardWorkflowRuntime.standardChecksC(Vector("compile", "test-only")))
      def plan(root: String) = Json.obj("workspaces" -> checks.map(c => Json.obj(
        "identity" -> ("sm-workflow-repository-validation-" + c.ordinal).asJson, "root" -> root.asJson)).asJson,
        "sbtPrograms" -> checks.map(c => Json.obj("target" -> ("sm-workflow-repository-validation-" + c.ordinal).asJson,
          "operation" -> c.kind.wire.asJson)).asJson)
      ManagedDevelopmentBoundedWork.validationBindingsC(plan("/fixture/canonical"), member, checks).isSuccess shouldBe false
      ManagedDevelopmentBoundedWork.validationBindingsC(plan("/fixture/b"), member, checks).isSuccess shouldBe true
      ManagedDevelopmentBoundedWork.validationBindingsC(plan("/fixture/b").mapObject(_.add("sbtPrograms", Json.arr())), member, checks).isSuccess shouldBe false
    }

    "reject stable modified and untracked post-commit publication sources" in {
      val commit = StandaloneFixExecution.Commit("a" * 40, "c" * 40, "b" * 40)
      val observed = RepositorySyncManagedGitObservation.Observed("main", commit.identity, commit.tree, Vector.empty, None, None)
      val clean = take(ManagedDevelopmentRuntime.sourceContentsC(observed, _ => Consequence.success(None)))
      ManagedDevelopmentBoundedWork.acceptedPublicationSource(clean, commit) shouldBe true
      Vector(RepositorySyncManagedGitObservation.DirtyPath(' ', 'M', "src/main/scala/Provider.scala", None),
        RepositorySyncManagedGitObservation.DirtyPath('?', '?', "src/main/scala/Additional.scala", None)).foreach { dirty =>
        val stable = take(ManagedDevelopmentRuntime.sourceContentsC(observed.copy(dirty = Vector(dirty)),
          _ => Consequence.success(Some("stable post-commit edit".getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector))))
        stable.hcursor.get[String]("head").toOption shouldBe Some(commit.identity)
        ManagedDevelopmentBoundedWork.acceptedPublicationSource(stable, commit) shouldBe false
      }
      ManagedDevelopmentBoundedWork.acceptedPublicationSource(clean.mapObject(_.remove("dirtyContents")), commit) shouldBe false
      ManagedDevelopmentBoundedWork.acceptedPublicationSource(clean, commit.copy(tree = "d" * 40)) shouldBe false
    }

    "preserve the exact native adoption target through its public Record without losing null checkpoint fields" in {
      val request = take(DelegateTask.inputC(DelegateTaskRequesterRoutingFixture.input("lossless-adoption")))
      val returned = Json.obj("stage" -> "ReturnReady".asJson, "pendingCheck" -> Json.Null,
        "publication" -> Json.obj("source" -> Json.obj("trackingTip" -> Json.Null, "dirty" -> Json.arr())))
      val target = DelegateTask.AdoptionTarget("org.example.Workflow@spec", "requester-session", "1", "participant",
        request, returned, "destination-session")
      val observation = DelegateTask.Observation("adopt", ContextReference("adoption-observation", "1"),
        Some(target.destinationSessionId), None, Some(Json.obj("sourceCommit" -> "actual-commit".asJson)), "Exact fixture observation")
      def publicJson(value: Json): Json = io.circe.parser.parse(take(take(DelegateTaskNativeRun.recordC(value)).toJsonStringC)).toOption.get
      val described = publicJson(target.asJson)
      described.hcursor.get[String]("returned").toOption shouldBe Some(returned.noSpaces)
      described.as[DelegateTask.AdoptionTarget].toOption shouldBe Some(target)
      // Read the former structured shape when it is complete, without treating
      // an omitted field as equivalent to the native checkpoint's explicit null.
      target.asJson.mapObject(_.add("returned", returned)).as[DelegateTask.AdoptionTarget].toOption shouldBe Some(target)
      val binding = DelegateTask.adoptionBinding(target, observation)
      val transported = publicJson(binding)
      take(DelegateTask.selectAdoptionBinding(transported).fold(Consequence.valueInvalid(_), Consequence.success)) shouldBe binding
      val purpose = NativeExternalEvidence.Purpose.DelegateAdoption
      val lookup = ThreeWorkflowExternalEvidencePorts.key(observation.sourceReference.identity, observation.sourceReference.revision)
      val now = java.time.Instant.parse("2026-10-07T00:00:00Z")
      val evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, purpose.identity, lookup,
        observation.sourceReference.identity, observation.sourceReference.revision, "normal-development-host", target.participant,
        transported, now.minusSeconds(1).toString)
      val declaration = NativeExternalEvidence.Declaration("workflow", _collection_("host_evidence"), "normal-development-host")
      NativeExternalEvidence.observeC(evidence, declaration, purpose, lookup, binding, now).isSuccess shouldBe true
      val altered = DelegateTask.adoptionBinding(target.copy(returned = returned.mapObject(_.remove("pendingCheck"))), observation)
      NativeExternalEvidence.observeC(evidence, declaration, purpose, lookup, altered, now).isSuccess shouldBe false
      target.asJson.mapObject(_.add("returned", "malformed JSON".asJson)).as[DelegateTask.AdoptionTarget].isLeft shouldBe true
    }

    "connect native B delivery and explicit consumer integration to the exact original A Request" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase7-managed-development").toAbsolutePath), "connected-").toRealPath()
      ConnectedFixture.exercise(root)
    }

    "attach an existing provider Phase locator and wait without starting or completing a substitute destination" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase7-managed-development").toAbsolutePath), "provider-phase-").toRealPath()
      ConnectedFixture.exercise(root, "PhaseDelivery")
    }

    "exclude genuine provider Phases and bounded work in one assigned-chat FIFO in either admission order" in {
      Vector("AssignedPhaseFirst", "AssignedPhaseBoundedFirst").foreach { mode =>
        val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase7-managed-development").toAbsolutePath), "assigned-phase-").toRealPath()
        ConnectedFixture.exercise(root, mode)
      }
    }

    "admit two destination requests through native FIFO and preserve explicit waiting hold cancel and requeue" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase7-managed-development").toAbsolutePath), "assigned-").toRealPath()
      ConnectedFixture.exercise(root, "Assigned")
    }

    "resume an actual dependency Decision from retained Phase policy without a fabricated Human answer" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase7-managed-development").toAbsolutePath), "phase-policy-").toRealPath()
      ConnectedFixture.exercise(root, "PhasePolicy")
      val enabled = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase7-managed-development").toAbsolutePath), "phase-policy-enable-").toRealPath()
      ConnectedFixture.exercise(enabled, "PhasePolicyEnable")
    }

    "keep passing canonical checks from certifying B and attribute correctly bound B failure" in {
      Vector("CanonicalAliases", "FailB", "DirtyPostCommit").foreach { mode =>
        val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase7-managed-development").toAbsolutePath), "validation-").toRealPath()
        ConnectedFixture.exercise(root, mode)
      }
    }

    "capture two real pending native A WorkOrders and reject crossed callbacks without completing either A" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase7-managed-development").toAbsolutePath), "capture-").toRealPath()
      val settings = ThreeWorkflowNormalHostFixture.definition(root)
      val selection = _goal_selection_(settings.goal.input, "managed-capture-plan", "managed-capture-start")
      val plan = GoalPhaseOrderedProgression.Plan("managed-capture-plan", "1", settings.goal.input.phaseIdentity,
        settings.goal.input.repositoryIdentity, Vector(GoalPhaseOrderedProgression.Step("step-1", "Retain A while B develops",
          settings.goal.candidatePaths, Vector.empty, Vector("test"))), Vector("test"), repairLimit = 10)
      _with_runtime_(root, "Goal", Some(settings)) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(root, "Goal").size, scenario = "Goal").flatMap(_ =>
          call.createC("goal_selection", NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Goal(settings.goal.input),
            selection.participantIdentity), Record.data(ProfileSelectionJsonV1.storageField ->
              take(ProfileSelectionJsonV1.encodeStorageC(selection)))).flatMap(_ =>
            call.createC("goal_plans", selection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
              take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(selection.selectionId,
                selection.participantIdentity, plan))))))))
        def goal(operation: String, properties: List[Property]) = take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "GoalPhaseService", operation = operation, properties = properties), actor))
        val sessions = Vector.fill(2)(read_value[V.GoalPhaseStartResult](goal("startGoalPhase", NativeWorkflowOperationInputFixture.goal(settings.goal.input))).sessionId)
        val requests = sessions.map(session => read_value[V.GoalPhaseAdvanceResult](goal("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(session))))
        requests.foreach(_.kind shouldBe "Request")
        val selectedEdge = edge.copy(provider = edge.provider.copy(workspace = "managed-b"),
          consumer = edge.consumer.copy(repository = settings.goal.input.repositoryIdentity, workspace = "sm-workflow-repository"))
        val selectedPolicy = policy.copy(definition = definition.copy(edges = Vector(selectedEdge)), dependencyWorkEdge = Some(selectedEdge.identity))
        val h = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
          selectedEdge.provider.repository, root.resolve("managed-b").toString, "work/b", Vector("src/main/scala"), Vector.empty,
          None, None, None, None, None, managedDevelopment = Some(selectedPolicy))
        val captures = sessions.map { session =>
          var captured = Option.empty[GoalPhaseDependencyValidation.ManagedCapture]
          prepare { call => for {
            issued <- call.goalWorkC(InstanceIdentity(session))
            order = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[GoalPhaseNativeWork.Request]]
            base = take(DelegateTask.inputC(DelegateTaskRequesterRoutingFixture.input("managed-delivery")))
            destination = base.copy(repositoryIdentity = selectedEdge.provider.repository,
              sourceReferences = Vector(ContextReference(order.request.continuationId.value, order.request.expectedRevision.value)),
              delegation = base.delegation.map(_.copy(role = "destination", componentIdentity = selectedEdge.provider.identity,
                publicationTarget = Some(selectedEdge.identity), artifactCoordinates = Vector(selectedEdge.requested.organization + ":" +
                  selectedEdge.requested.module + ":" + version(selectedEdge.requested.version, definition.scope, base.requestIdentity)))))
            actual <- ManagedDevelopmentBoundedWork.captureC(h, destination, call.core)
            _ <- ManagedDevelopmentBoundedWork.requesterC(h, actual.get, call.core)
            _ = { captured = actual }
            _ = ManagedDevelopmentBoundedWork.requesterC(h, actual.get.copy(generation = actual.get.generation + 1), call.core).isSuccess shouldBe false
            _ = ManagedDevelopmentBoundedWork.requesterC(h, actual.get.copy(work = actual.get.work.copy(revision = "999999")), call.core).isSuccess shouldBe false
          } yield () }
          captured.get
        }
        captures.map(_.session) shouldBe sessions
        captures.map(_.work).distinct.size shouldBe 2
        captures.foreach { captured =>
          captured.plan shouldBe ContextReference(plan.identity, plan.revision)
          captured.step shouldBe "step-1"
          captured.edge shouldBe selectedEdge
        }
        sessions.zip(requests).foreach { (session, original) =>
          val retained = read_value[V.GoalPhaseAdvanceResult](goal("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(session)))
          retained.kind shouldBe "Request"
          retained.issuedWorkContext shouldBe original.issuedWorkContext
          retained.repairProgress shouldBe original.repairProgress
        }
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      }
    }

    "compose selected dependency RepositorySync through native Start and Advance without a producer build" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase7-managed-development").toAbsolutePath), "sync-").toRealPath()
      SyncFixture.exercise(root)
    }

    "order selected workspace sources from the admitted edges and reject crossed endpoints and cycles" in {
      def member(component: GoalPhaseDependencyScope.Component, session: String) = RepositorySyncDirectoryList.Member(
        component.repository, component.workspace, "/fixture/" + session, "work/" + session, "origin", "release", "configured", session)
      val a = member(edge.consumer, "a")
      val b = member(edge.provider, "b")
      val c = member(edge.provider.copy(identity = "org.example.SecondProvider", repository = "second-provider", workspace = "second-provider"), "c")
      val second = edge.copy(identity = "consumer-second", provider = edge.provider.copy(identity = "org.example.SecondProvider",
        repository = c.repositoryIdentity, workspace = c.workspace))
      val selected = policy.copy(definition = definition.copy(edges = Vector(edge, second)))
      val (ordered, dependencies) = take(ManagedDevelopmentRepositorySync.orderC(Some(selected), Vector(a, c, b)))
      ordered.map(_.sessionId) shouldBe Vector("c", "b", "a")
      dependencies shouldBe Map("a" -> Vector("b", "c"))
      take(ManagedDevelopmentRepositorySync.orderC(None, Vector(a, c, b)))._1 shouldBe Vector(a, c, b)
      take(ManagedDevelopmentRepositorySync.orderC(Some(selected), Vector(b)))._2 shouldBe Map.empty
      ManagedDevelopmentRepositorySync.orderC(Some(selected), Vector(a)).isSuccess shouldBe false
      ManagedDevelopmentRepositorySync.orderC(Some(selected), Vector(a, b.copy(repositoryIdentity = "foreign"))).isSuccess shouldBe false
      val reverse = edge.copy(identity = "reverse", provider = edge.consumer, consumer = edge.provider)
      ManagedDevelopmentRepositorySync.orderC(Some(policy.copy(definition = definition.copy(edges = Vector(edge, reverse)))), Vector(a, b)).isSuccess shouldBe false
    }

    "retain dependency waits failures and unknown applicability without blocking an unrelated selected lane" in {
      val a = RepositorySyncDirectoryList.Member("consumer", "a", "/fixture/a", "work/a", "origin", "release", "configured", "a")
      val independent = a.copy(repositoryIdentity = "independent", workspace = "other", sessionId = "other")
      val before = "b" * 40
      val provider = RepositorySyncDirectoryList.Result("provider", "b", "b", "Terminal", "converged", None, Some("8"), Some("a" * 40), Some(before))
      val state = RepositorySyncDirectoryList.Payload("participant", Vector(a, independent), Vector(provider), "AttentionRequired", None,
        dependencies = Map("a" -> Vector("b")), sourceTrees = Map("b" -> before))
      RepositorySyncDirectoryList.dependencyBlock(state, a).get should include("DependencyIntegrationApplicabilityUnknown")
      for ((kind, outcome) <- Vector("Wait" -> "unfinished", "Request" -> "unfinished", "Human" -> "unfinished", "Terminal" -> "failed", "Indeterminate" -> "indeterminate")) {
        val pending = state.copy(results = Vector(provider.copy(kind = kind, outcome = outcome)))
        RepositorySyncDirectoryList.dependencyBlock(pending, a).get should include("DependencyValidationPending")
        RepositorySyncDirectoryList.dependencyBlock(pending, independent) shouldBe None
        RepositorySyncDirectoryList.payloadCodec.decode(RepositorySyncDirectoryList.payloadCodec.encode(pending)) shouldBe Right(pending)
      }
      val moved = state.copy(results = Vector(provider.copy(tree = Some("c" * 40))))
      RepositorySyncDirectoryList.dependencyBlock(moved, a).get should include("DependencyIntegrationApplicabilityUnknown")
      RepositorySyncDirectoryList.dependencyBlock(state.copy(sourceTrees = Map.empty), a).get should include("ApplicabilityUnknown")
      val legacy = RepositorySyncDirectoryList.payloadCodec.encode(state.copy(dependencies = Map.empty, sourceTrees = Map.empty))
      legacy.asObject.get.keys.toSet should not contain "dependencies"
      RepositorySyncDirectoryList.payloadCodec.decode(legacy) shouldBe Right(state.copy(dependencies = Map.empty, sourceTrees = Map.empty))
      RepositorySyncDirectoryList.payloadCodec.decode(legacy.mapObject(_.add("dependencies", Json.Null))).isLeft shouldBe true
    }

    "admit a consumer only with known immutable consumed inputs and bind receipts to the full subject" in {
      val memory = new Memory
      run("publish", memory).status shouldBe "published"
      val publication = memory.rows(ManagedDevelopment.key(definition, edge, "publication", input.publication)).as[Publication].toOption.get
      val selected = input.copy(requestIdentity = "existing-consumer", selection = "selected-consumer")
      val path = "inputs/" + edge.identity + "/consumer/" + selected.requestIdentity + "/input.json"
      val envelope = ManagedDevelopmentRuntime.executionInput(definition, edge, selected, "/owner", "/consumer", Some(publication), "/owner/result.json")
      val subject = RepositorySyncConsumedInputs.Subject(definition, selected, publication, path, envelope)
      val before = RepositorySyncConsumedInputs.Observation(Vector(subject), None)
      val unchanged = RepositorySyncConsumedInputs.Change(before, Some(before))
      unchanged.known shouldBe true
      unchanged.changed shouldBe false
      val switched = unchanged.copy(after = Some(before.copy(subjects = Vector(subject.copy(input = selected.copy(selection = "different"))))))
      switched.known shouldBe true
      switched.changed shouldBe true
      val a = RepositorySyncDirectoryList.Member(edge.consumer.repository, edge.consumer.workspace, "/consumer", "work/a", "origin", "release", "configured", "a")
      val b = a.copy(repositoryIdentity = edge.provider.repository, workspace = edge.provider.workspace, root = "/provider", sessionId = "b")
      val result = RepositorySyncDirectoryList.Result(b.repositoryIdentity, b.workspace, b.sessionId, "Terminal", "converged", None, Some("8"), Some("a" * 40), Some("b" * 40))
      val state = RepositorySyncDirectoryList.Payload("participant", Vector(b, a), Vector(result), "Ready", None,
        dependencies = Map(a.sessionId -> Vector(b.sessionId)), consumerInputs = Map(a.sessionId -> unchanged))
      RepositorySyncDirectoryList.dependencyBlock(state, a) shouldBe None
      RepositorySyncDirectoryList.dependencyBlock(state.copy(consumerInputs = Map(a.sessionId -> switched)), a) shouldBe None
      RepositorySyncDirectoryList.dependencyBlock(state.copy(results = Vector(result.copy(outcome = "failed"))), a).get should include("DependencyValidationPending")
      RepositorySyncDirectoryList.dependencyBlock(state.copy(consumerInputs = Map(a.sessionId -> unchanged.copy(after = None))), a).get should include("ApplicabilityUnknown")
      RepositorySyncDirectoryList.payloadCodec.decode(RepositorySyncDirectoryList.payloadCodec.encode(state)) shouldBe Right(state)
      val policy = RepositorySyncNativeMergeValidation.Policy("related", 1, "explicit-no-product-tests", "known unchanged consumption", Vector.empty,
        dependencyInputs = Some(envelope))
      val receipt = RepositorySyncNativeMergeValidation.State(policy.mode, policy.reason, Vector.empty, "8", Vector.empty, dependencyInputs = Some(envelope))
      RepositorySyncNativeMergeValidation.applies(receipt, policy) shouldBe true
      RepositorySyncNativeMergeValidation.applies(receipt, policy.copy(dependencyInputs = Some(Json.obj("different" -> true.asJson)))) shouldBe false
      RepositorySyncNativeMergeValidation.applies(receipt, policy.copy(dependencyInputs = None)) shouldBe false
    }

    for (scenario <- Vector("Equal", "ChangedDependency", "Conflict")) {
      s"retain the real registered workspace $scenario outcome without claiming driver acceptance" in {
        val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase7-managed-development").toAbsolutePath), "workspace-").toRealPath()
        WorkspaceSyncFixture.exercise(root, scenario)
      }
    }

    for (failedProvider <- Vector(false, true)) {
      s"collect overlapping native provider checks and ${if (failedProvider) "block" else "validate"} the scoped consumer" in {
        val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase7-managed-development").toAbsolutePath),
          "native-workspace-products-").toRealPath()
        WorkspaceProductFixture.exercise(root, failedProvider)
      }
    }

    "admit only the actual nonmerge structural prefix and exact scoped attempt across all outcomes" in {
      import org.goldenport.cncf.processexecution.*
      import org.goldenport.cncf.workspace.{WorkspaceId, WorkspaceRelativePath}
      val fixture = RepositorySyncMergeCheckContinuationFixture
      val check = fixture.checks.head
      val workspace = take(WorkspaceId.parseC(check.workspaceIdentity))
      val target = SbtTarget(workspace, scopedInput = Some(SbtScopedInput(SmWorkflowComponent.componentId, workspace,
        take(WorkspaceId.parseC("development-artifacts")), "qualified-scope", "qualified-selection",
        take(WorkspaceRelativePath.parseC("inputs/qualified/input.json")), None)))
      val state = fixture.state.copy(receipts = fixture.prefix.take(1))
      val prepared = fixture.intent.copy(stage = "IntegrationValidationChecking", mergeHead = None,
        validation = Some(state), method = Some("fast-forward"))
      val empty = ProcessExecutionCapture(Vector.empty, 0L, false)
      val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(7), empty, empty, Vector.empty, 1L, "authored-native-shape")
      val observed = SbtExecutionAttempt.Observed(SbtExecutionResult(target, SbtOperation.Compile, process))
      val recorded = take(RepositorySyncNativeMergeValidation.productAttemptPayloadC(prepared, check, observed, target))
      recorded.validation.get.receipts.take(1) shouldBe state.receipts
      recorded.validation.get.receipts.last.exitCode shouldBe Some(7)
      recorded.validation.get.receipts.last.processObservation shouldBe Some(process)
      val conclusion = Consequence.stateConflict[Unit]("authored typed preparation rejection") match {
        case Consequence.Failure(value) => value
        case _ => fail("Expected a diagnostic conclusion")
      }
      val rejection = SbtExecutionAttempt.AdmissionRejected(target, SbtOperation.Compile, conclusion)
      val refused = take(RepositorySyncNativeMergeValidation.productAttemptPayloadC(prepared, check, rejection, target))
      refused.stage shouldBe "ValidationPreparationRejected"
      refused.validation shouldBe prepared.validation
      refused.validationPreparation.get.diagnostic shouldBe conclusion.show
      RepositorySyncNativeMergeValidation.mayRunCheck(refused, 1, merge = false) shouldBe false
      val uncertain = SbtExecutionAttempt.Uncertain(target, SbtOperation.Compile, conclusion)
      val crossed = target.copy(scopedInput = target.scopedInput.map(_.copy(selection = "crossed-selection")))
      Vector(SbtExecutionAttempt.Observed(SbtExecutionResult(crossed, SbtOperation.Compile, process)),
        SbtExecutionAttempt.AdmissionRejected(crossed, SbtOperation.Compile, conclusion),
        SbtExecutionAttempt.Uncertain(crossed, SbtOperation.Compile, conclusion), uncertain).foreach { actual =>
        RepositorySyncNativeMergeValidation.productAttemptPayloadC(prepared, check, actual, target).isSuccess shouldBe false
      }
      RepositorySyncNativeMergeValidation.productAttemptPayloadC(prepared.copy(validation = Some(state.copy(
        receipts = fixture.prefix))), check, observed, target).isSuccess shouldBe false
      RepositorySyncNativeMergeValidation.productAttemptPayloadC(prepared.copy(validation = Some(state.copy(
        receipts = state.receipts.map(_.copy(exitCode = Some(1)))))), check, observed, target).isSuccess shouldBe false
      prepared.validation.get.receipts shouldBe fixture.prefix.take(1)
    }

    "join every confirmed native child after submission uncertainty and retain later mixed receipts" in {
      import org.goldenport.cncf.job.{JobId, JobResult}
      import org.goldenport.protocol.operation.OperationResponse
      val members = Vector("unconfirmed", "first", "second", "third").map(id =>
        RepositorySyncDirectoryList.Member(id, id, "/fixture/" + id, "main", "origin", "main", "configured", id))
      val success = JobResult.Success(OperationResponse.Scalar("actual independent success"))
      val failure = JobResult.Failure(org.goldenport.Conclusion.simple("actual independent failure"))
      val ids = Vector("first", "second", "third").map(id => id -> JobId.generate()).toMap
      val attempts = members.map(member => member.sessionId -> RepositorySyncDirectoryList.Attempt(
        ids.get(member.sessionId).map(_.value), true, None)).toMap
      val existing = RepositorySyncDirectoryList.Result("earlier", "earlier", "earlier", "Terminal", "converged",
        None, Some("8"), Some("a" * 40), Some("b" * 40))
      for (stage <- Vector("AttentionRequired", "Indeterminate")) {
        Given("three confirmed child IDs, one unconfirmed submission and an earlier retained result")
        val payload = RepositorySyncDirectoryList.Payload("participant", members, Vector(existing), stage, None, attempts = attempts)
        val waited = scala.collection.mutable.ArrayBuffer.empty[String]
        When("the first exact wait is unconfirmed but later independent Jobs actually terminate")
        val joined = take(RepositorySyncDirectoryList.joinConfirmedC(payload, job => {
          waited += job.value
          if (job.value == ids("first").value) Consequence.stateConflict("wait interrupted before a result was observed")
          else if (job.value == ids("second").value) Consequence.success(success)
          else if (job.value == ids("third").value) Consequence.success(failure)
          else fail("Unconfirmed submission must never be replayed or awaited: " + job.value)
        }))
        Then("both later receipts survive and every confirmed Job has had its own foreground wait")
        waited.toVector shouldBe Vector("first", "second", "third").map(ids(_).value)
        joined.results shouldBe Map("second" -> success, "third" -> failure)
        joined.diagnostics.size shouldBe 1
        joined.diagnostics.head should include(ids("first").value)
        RepositorySyncDirectoryList.payloadCodec.decode(RepositorySyncDirectoryList.payloadCodec.encode(payload)) shouldBe Right(payload)
        payload.results shouldBe Vector(existing)
      }
    }

    "retain source origin, exact publication and immutable edge selection without implicitly building a provider" in {
      Given("an explicit independent source, JAR and project binding")
      val m = new Memory
      run("publish", m).status shouldBe "published"
      run("publish", m).status shouldBe "published"
      m.calls shouldBe 1
      val consumer = input.copy(requestIdentity = "consumer-one", selection = "consumer-selection-one")
      When("the consumer selects the exact completed result")
      run("select", m, consumer).status shouldBe "selected-unverified"
      m.calls shouldBe 1
      run("status", m, consumer).status shouldBe "selected-unverified"
      run("compile", m, consumer).status shouldBe "verified"
      run("compile", m, consumer).status shouldBe "verified"
      m.calls shouldBe 2
      run("status", m, consumer).status shouldBe "verified"
      val retained = m.rows(ManagedDevelopment.key(definition, edge, "publication", input.publication)).as[Publication].toOption.get
      retained.source shouldBe m.source
      retained.selected.version should not be edge.requested.version
      runC("select", definition, consumer.copy(publication = "missing"), m).isSuccess shouldBe false
      runC("compile", definition, consumer.copy(selection = "foreign"), m).isSuccess shouldBe false
      m.calls shouldBe 2
      run("publish", m, input.copy(requestIdentity = "publish-two", publication = "publication-two")).status shouldBe "published"
      runC("select", definition, consumer.copy(publication = "publication-two"), m).isSuccess shouldBe false
      m.calls shouldBe 3
    }

    "preserve failed and unknown effects without automatic publication replay or selectable partial output" in {
      val failed = new Memory
      failed.execution = Execution("failed", Json.obj("exit" -> 1.asJson))
      run("publish", failed).status shouldBe "failed"
      run("publish", failed).status shouldBe "failed"
      failed.calls shouldBe 1
      run("status", failed).status shouldBe "failed"
      runC("select", definition, input, failed).isSuccess shouldBe false
      val incomplete = new Memory
      incomplete.completedOutput = false
      run("publish", incomplete).status shouldBe "unverified"
      run("publish", incomplete).status shouldBe "unverified"
      incomplete.calls shouldBe 1
      runC("select", definition, input, incomplete).isSuccess shouldBe false
      val interrupted = new Memory
      interrupted.rows(ManagedDevelopment.key(definition, edge, "publish-intent", input.requestIdentity)) =
        Json.obj("input" -> input.asJson, "participant" -> interrupted.participant.asJson)
      run("publish", interrupted).status shouldBe "unknown"
      interrupted.calls shouldBe 0
      runC("publish", definition, input.copy(publication = "crossed"), interrupted).isSuccess shouldBe false
      ManagedDevelopment.validateC(definition.copy(edges = Vector(edge.copy(localRepository = "foreign")))).isSuccess shouldBe false
      version("1.0", "a-b", "c") should not be version("1.0", "a", "b-c")
    }

    "retain explicit update rollback and detach outcomes without changing an in-flight immutable input" in {
      val m: Memory = new Memory {
        override def detachC(e: Edge, i: Input) = { calls += 1; Consequence.success(execution) }
      }
      run("publish", m)
      val a = input.copy(requestIdentity = "verify-a", selection = "selected-a")
      run("select", m, a)
      val before = m.rows(ManagedDevelopment.key(definition, edge, "selection", a.selection))
      run("publish", m, input.copy(requestIdentity = "publish-b", publication = "publication-b"))
      val b = a.copy(requestIdentity = "verify-b", publication = "publication-b", selection = "selected-b")
      run("select", m, b)
      run("verify", m, b).status shouldBe "verified"
      run("verify", m, a).status shouldBe "verified"
      m.rows(ManagedDevelopment.key(definition, edge, "selection", a.selection)) shouldBe before
      val detached = a.copy(requestIdentity = "detach-a")
      run("detach", m, detached).status shouldBe "detached"
      val callsAfterDetach: Int = m.calls
      run("detach", m, detached).status shouldBe "detached"
      m.calls shouldBe callsAfterDetach
      run("status", m, detached).status shouldBe "detached"
      runC("detach", definition, detached.copy(selection = "crossed"), m).isSuccess shouldBe false
      run("prune", m, a).status shouldBe "prune-unavailable"
      m.rows(ManagedDevelopment.key(definition, edge, "selection", a.selection)) shouldBe before
      m.calls shouldBe callsAfterDetach
    }

    "prune only a newly admitted unprotected inventory and preserve immutable history across reconnection" in {
      val m = new OwnedMemory
      run("publish", m).status shouldBe "published"
      val selected = input.copy(requestIdentity = "select-before-prune", selection = "old-selection")
      run("select", m, selected)
      run("prune", m, input.copy(requestIdentity = "active-prune")).status shouldBe "prune-unavailable"
      m.deleted shouldBe empty
      run("detach", m, selected.copy(requestIdentity = "detach-before-prune")).status shouldBe "detached"
      val retained = m.rows(ManagedDevelopment.key(definition, edge, "publication", input.publication))
      val pruned = input.copy(requestIdentity = "actual-prune")
      val result = run("prune", m, pruned)
      result.status shouldBe "pruned"
      m.deleted.size shouldBe 3
      m.deleted.forall(_.startsWith(base(definition, edge, input.publication) + "/")) shouldBe true
      m.rows(ManagedDevelopment.key(definition, edge, "publication", input.publication)) shouldBe retained
      run("prune", m, pruned) shouldBe result
      m.deleted.size shouldBe 3
      val reconnected = new OwnedMemory { override val rows = m.rows }
      run("status", reconnected, pruned).status shouldBe "pruned"
      runC("verify", definition, selected.copy(requestIdentity = "new-use"), reconnected).isSuccess shouldBe false
      runC("select", definition, selected.copy(requestIdentity = "new-select", selection = "new-selection"), reconnected).isSuccess shouldBe false
      runC("publish", definition, input.copy(requestIdentity = "replacement"), reconnected).isSuccess shouldBe false
      reconnected.deleted shouldBe empty
      reconnected.calls shouldBe 0
    }

    "protect retained selections and transitive publication closure until explicit unpin and dependent pruning" in {
      val m = new OwnedMemory
      run("publish", m)
      val b = input.copy(requestIdentity = "publish-b", publication = "b", selection = "selection-b")
      m.dependencies += "b" -> Vector(edge.requested.copy(version = version(edge.requested.version, definition.scope, input.publication)))
      run("publish", m, b)
      val c = input.copy(requestIdentity = "publish-c", publication = "c", selection = "selection-c")
      m.dependencies += "c" -> Vector(edge.requested.copy(version = version(edge.requested.version, definition.scope, "b")))
      run("publish", m, c)
      val pinned = c.copy(requestIdentity = "pin-c", retain = Some(true))
      run("select", m, pinned)
      run("detach", m, pinned.copy(requestIdentity = "detach-c"))
      Vector(input, b, c).zipWithIndex.foreach { (value, index) =>
        run("prune", m, value.copy(requestIdentity = "protected-" + index)).status shouldBe "prune-unavailable"
      }
      m.deleted shouldBe empty
      val unpinned = c.copy(requestIdentity = "unpin-c", retain = Some(false))
      run("select", m, unpinned)
      run("detach", m, unpinned.copy(requestIdentity = "detach-unpinned"))
      Vector(c, b, input).zipWithIndex.foreach { (value, index) =>
        run("prune", m, value.copy(requestIdentity = "prune-dependency-" + index)).status shouldBe "pruned"
      }
      m.deleted.size shouldBe 9
    }

    "refuse foreign and dangling development dependencies without admitting a selectable publication" in {
      val m = new OwnedMemory
      m.dependencies += input.publication -> Vector(edge.requested.copy(version = version(edge.requested.version, "foreign", "missing")))
      run("publish", m).status shouldBe "unverified"
      runC("select", definition, input, m).isSuccess shouldBe false
      run("prune", m, input.copy(requestIdentity = "foreign-prune")).status shouldBe "prune-unavailable"
      m.deleted shouldBe empty
    }

    "retain in-flight and uncertain use independently of a newer active selection" in {
      val m = new OwnedMemory {
        override def executeC(e: Edge, i: Input, selected: Option[Publication]): Consequence[Execution] = {
          if (i.requestIdentity == "in-flight-a") {
            val b = input.copy(requestIdentity = "switch-during-a", publication = "b", selection = "selection-b")
            run("select", this, b)
            run("verify", this, b.copy(requestIdentity = "verify-during-a")).status shouldBe "verified"
            run("prune", this, input.copy(requestIdentity = "prune-during-a")).status shouldBe "prune-unavailable"
            deleted shouldBe empty
            Consequence.success(Execution("unknown", Json.obj("termination" -> "TerminationUnconfirmed".asJson)))
          } else super.executeC(e, i, selected)
        }
      }
      run("publish", m)
      run("publish", m, input.copy(requestIdentity = "publish-b", publication = "b"))
      val a = input.copy(requestIdentity = "in-flight-a", selection = "selection-a")
      run("select", m, a)
      run("verify", m, a).status shouldBe "unknown"
      val calls = m.calls
      run("verify", m, a).status shouldBe "unknown"
      m.calls shouldBe calls
      val reconnected = new OwnedMemory { override val rows = m.rows }
      run("prune", reconnected, input.copy(requestIdentity = "prune-after-unknown")).status shouldBe "prune-unavailable"
      reconnected.deleted shouldBe empty
    }

    "preserve active protection after a conflicting selection and after replay of an older successful select" in {
      val m = new OwnedMemory
      run("publish", m)
      run("publish", m, input.copy(requestIdentity = "publish-b", publication = "b"))
      val a = input.copy(requestIdentity = "select-a", selection = "same-selection")
      run("select", m, a)
      runC("select", definition, a.copy(requestIdentity = "conflicting-select", publication = "b"), m).isSuccess shouldBe false
      run("prune", m, input.copy(requestIdentity = "still-active-a")).status shouldBe "prune-unavailable"
      val b = input.copy(requestIdentity = "select-b", publication = "b", selection = "selection-b")
      run("select", m, b)
      run("select", m, a).status shouldBe "selected-unverified"
      run("prune", m, input.copy(requestIdentity = "inactive-a")).status shouldBe "pruned"
      run("prune", m, b.copy(requestIdentity = "still-active-b")).status shouldBe "prune-unavailable"
    }

    "clear only the expected active selection when detach completes after a concurrent switch" in {
      val m = new OwnedMemory {
        override def detachC(e: Edge, i: Input): Consequence[Execution] = {
          run("select", this, input.copy(requestIdentity = "switch-while-detaching", publication = "b", selection = "selection-b"))
          super.detachC(e, i)
        }
      }
      run("publish", m)
      run("publish", m, input.copy(requestIdentity = "publish-b", publication = "b"))
      val a = input.copy(requestIdentity = "select-a", selection = "selection-a")
      run("select", m, a)
      run("detach", m, a.copy(requestIdentity = "detach-a")).status shouldBe "detached"
      run("prune", m, input.copy(requestIdentity = "prune-b", publication = "b")).status shouldBe "prune-unavailable"
      run("prune", m, input.copy(requestIdentity = "prune-a")).status shouldBe "pruned"
    }

    "retain per-file partial and unknown deletion without replay or resurrection" in {
      Vector("Unknown", "Conflict", "Failed", "Absent").foreach { outcome =>
        val m = new OwnedMemory {
          override def deleteC(path: String): Consequence[String] = {
            deleted += path
            Consequence.success(if (deleted.size == 1) "Deleted" else outcome)
          }
        }
        run("publish", m)
        val selected = input.copy(requestIdentity = "select-old", selection = "selection-old")
        run("select", m, selected)
        run("detach", m, selected.copy(requestIdentity = "detach-old"))
        val request = input.copy(requestIdentity = "partial-prune")
        val result = run("prune", m, request)
        result.status shouldBe (if (outcome == "Unknown") "unknown" else if (outcome == "Absent") "pruned" else "prune-incomplete")
        val count = m.deleted.size
        run("prune", m, request) shouldBe result
        m.deleted.size shouldBe count
        val reconnected = new OwnedMemory { override val rows = m.rows }
        run("prune", reconnected, request.copy(requestIdentity = "another-prune")).status shouldBe "prune-unavailable"
        runC("verify", definition, selected.copy(requestIdentity = "new-use"), reconnected).isSuccess shouldBe false
        reconnected.deleted shouldBe empty
      }
    }

    "fail closed on a competing protection revision before admitting effects and keep legacy publications unprunable" in {
      val legacy = new Memory
      run("publish", legacy)
      run("prune", legacy, input.copy(requestIdentity = "legacy-prune")).status shouldBe "prune-unavailable"
      var contend = false
      val m = new OwnedMemory {
        override def createC(key: String, value: Json): Consequence[Unit] =
          if (contend && key.startsWith("managed-development-protection:")) {
            contend = false
            rows(key) = value
            invalid("competing writer owns this revision; result is not our receipt")
          } else super.createC(key, value)
      }
      run("publish", m)
      contend = true
      runC("select", definition, input.copy(requestIdentity = "contended-select"), m).isSuccess shouldBe false
      m.deleted shouldBe empty
      run("prune", m, input.copy(requestIdentity = "contended-prune")).status shouldBe "prune-unavailable"
      m.deleted shouldBe empty
    }

    "preserve a pending Unknown when deletion succeeds but its progress observation is interrupted" in {
      val m = new OwnedMemory {
        var cut = false
        override def deleteC(path: String): Consequence[String] = {
          deleted += path
          cut = true
          Consequence.success("Deleted")
        }
        override def createC(key: String, value: Json): Consequence[Unit] =
          if (cut && key.startsWith("managed-development-protection:")) invalid("interrupted progress retention")
          else super.createC(key, value)
      }
      run("publish", m)
      val request = input.copy(requestIdentity = "interrupted-prune")
      run("prune", m, request).status shouldBe "unknown"
      m.deleted.size shouldBe 1
      val reconnected = new OwnedMemory { override val rows = m.rows }
      val status = run("status", reconnected, request)
      status.status shouldBe "unknown"
      status.detail.noSpaces should include ("Unknown")
      run("prune", reconnected, request).status shouldBe "unknown"
      run("prune", reconnected, request.copy(requestIdentity = "restart-prune")).status shouldBe "prune-unavailable"
      runC("select", definition, input.copy(requestIdentity = "select-interrupted"), reconnected).isSuccess shouldBe false
      reconnected.deleted shouldBe empty
    }

    "read a long contiguous protection history without a lifetime cap or linear recursive traversal" in {
      Vector(1000000000000L, Long.MaxValue).foreach { frontier =>
        var protectionReads = 0
        val m = new Memory {
          override def loadC(key: String): Consequence[Option[Json]] =
            if (key.startsWith("managed-development-protection:")) {
              protectionReads += 1
              val revision = key.substring(key.lastIndexOf(':') + 1).toLong
              Consequence.success(Option.when(revision <= frontier)(Protection(revision, definition.ownerWorkspace,
                definition.scope, definition.repository, Vector.empty, Map.empty, Map.empty, Map.empty).asJson))
            } else super.loadC(key)
        }
        val result = run("status", m)
        result.status shouldBe "not-found"
        result.detail.hcursor.downField("protection").get[Long]("revision").toOption shouldBe Some(frontier)
        protectionReads should be <= 128
        m.calls shouldBe 0
        if (frontier == Long.MaxValue) {
          runC("publish", definition, input, m).isSuccess shouldBe false
          m.calls shouldBe 0
        }
      }
    }

    "retry a no-effect state transition only after observing a different valid immutable winner" in {
      var compete = false
      val m = new OwnedMemory {
        override def createC(key: String, value: Json): Consequence[Unit] =
          if (compete && key.startsWith("managed-development-protection:")) {
            compete = false
            val proposed = value.as[Protection].toOption.get
            // A different no-op successor won the exact next key. This is
            // not an ambiguous readback of our own desired transition.
            rows(key) = proposed.copy(active = Map.empty).asJson
            invalid("another process won the next revision")
          } else super.createC(key, value)
      }
      run("publish", m)
      val calls = m.calls
      compete = true
      run("select", m, input.copy(requestIdentity = "contended-but-safe-select")).status shouldBe "selected-unverified"
      m.calls shouldBe calls
      run("prune", m, input.copy(requestIdentity = "protected-after-retry")).status shouldBe "prune-unavailable"
      m.deleted shouldBe empty
    }

    "derive logical owned inventory and reject absolute DTO redirection before cleanup" in {
      val m = new Memory
      run("publish", m)
      val original = m.rows(ManagedDevelopment.key(definition, edge, "publication", input.publication)).as[Publication].toOption.get
      val prefix = base(definition, edge, input.publication)
      val owner = "/owner/managed-artifacts"
      val marker = Json.obj("schema" -> "sbt-cozy.development-artifact-publication.v1".asJson,
        "scope" -> definition.scope.asJson, "selection" -> input.selection.asJson, "publication" -> input.publication.asJson,
        "organization" -> original.selected.organization.asJson, "module" -> original.selected.module.asJson,
        "version" -> original.selected.version.asJson, "dependencies" -> Vector.empty[Coordinate].asJson)
      val publication = original.copy(jar = owner + "/" + prefix + "/jars/" + edge.requested.module + ".jar",
        ivy = owner + "/" + prefix + "/ivys/ivy.xml", completion = marker)
      val inventory = take(ManagedDevelopmentRuntime.ownedInventoryC(definition, publication, owner))
      inventory.paths.size shouldBe 3
      inventory.paths.forall(_.startsWith(definition.repository + "/")) shouldBe true
      ManagedDevelopmentRuntime.ownedInventoryC(definition, publication.copy(jar = "/home/user/.ivy2/local/victim.jar"), owner).isSuccess shouldBe false
      ManagedDevelopmentRuntime.ownedInventoryC(definition, publication.copy(ivy = owner + "/../victim/ivy.xml"), owner).isSuccess shouldBe false
      ManagedDevelopmentRuntime.ownedInventoryC(definition.copy(ownerWorkspace = "foreign-owner"), publication, owner).isSuccess shouldBe false
      ManagedDevelopmentRuntime.ownedInventoryC(definition, publication.copy(completion = marker.mapObject(_.remove("dependencies"))), owner).isSuccess shouldBe false
    }

    "share only an explicitly named matching producer publication between independent consumers" in {
      val second = edge.copy(identity = "consumer-two", consumer = edge.consumer.copy(workspace = "consumer-two-workspace"))
      val d = definition.copy(edges = Vector(edge, second))
      val m = new Memory
      take(runC("publish", d, input, m)).status shouldBe "published"
      val selected = input.copy(edgeIdentity = second.identity, requestIdentity = "second", selection = "second-selection",
        publicationEdge = Some(edge.identity))
      take(runC("select", d, selected, m)).status shouldBe "selected-unverified"
      take(runC("verify", d, selected, m)).status shouldBe "verified"
      runC("select", d, selected.copy(selection = "unbound", publicationEdge = None), m).isSuccess shouldBe false
      runC("verify", d, selected.copy(publicationEdge = None), m).isSuccess shouldBe false
      val foreign = second.copy(provider = second.provider.copy(workspace = "foreign-provider"))
      runC("select", d.copy(edges = Vector(edge, foreign)), selected.copy(selection = "foreign"), m).isSuccess shouldBe false
    }

    "separate declared generator and runtime mappings and reject incomplete or conflicting batch bindings" in {
      val cozy = Coordinate("org.simplemodeling", "cozy_2.12", "0.3.3-SNAPSHOT")
      val modeler = Coordinate("org.simplemodeling", "simplemodeler_2.12", "1.1.26-SNAPSHOT")
      val batch = Batch(Vector(ProjectModule("provider", edge.requested), ProjectModule("cozy", cozy),
        ProjectModule("modeler", modeler)), Vector(edge.requested), Some(Generator(cozy.version, modeler.version, Vector(cozy, modeler))))
      val e = edge.copy(batch = Some(batch))
      val d = definition.copy(edges = Vector(e))
      ManagedDevelopment.validateC(d).isSuccess shouldBe true
      ManagedDevelopment.validateC(d.copy(edges = Vector(e.copy(batch = Some(batch.copy(modules = batch.modules :+ batch.modules.head)))))).isSuccess shouldBe false
      val conflicting = batch.copy(generator = batch.generator.map(_.copy(cozyVersion = "foreign")))
      ManagedDevelopment.validateC(d.copy(edges = Vector(e.copy(batch = Some(conflicting))))).isSuccess shouldBe false
      val members = batch.modules.map(m => ManagedDevelopmentRuntime.member(d, m.requested, input.publication, "/owner"))
      val main = members.head
      val pub = Publication(e, d.scope, input.publication, input.selection, main.selected, main.jar, main.ivy, Json.obj(),
        Json.obj(), Json.obj(), Some(PublishedBatch(members, Json.obj(), Json.obj())))
      val envelope = ManagedDevelopmentRuntime.executionInput(d, e, input, "/owner", "/consumer", Some(pub), "/owner/result.json")
      val adapter = envelope.hcursor.downField("cozyArtifact")
      adapter.get[String]("schema").toOption shouldBe Some("sbt-cozy.development-artifact.v2")
      adapter.get[Vector[Json]]("mappings").toOption.get.map(_.hcursor.get[Coordinate]("requested").toOption.get) shouldBe Vector(edge.requested)
      adapter.downField("generator").get[Vector[Json]]("mappings").toOption.get.map(_.hcursor.get[Coordinate]("requested").toOption.get) shouldBe Vector(cozy, modeler)
      val m = new Memory
      m.rows(ManagedDevelopment.key(d, e, "publication", input.publication)) = pub.copy(batch = pub.batch.map(b => b.copy(members = b.members.dropRight(1)))).asJson
      runC("select", d, input, m).isSuccess shouldBe false
      ManagedDevelopmentRuntime.generationReportValid(input, pub, Json.obj()) shouldBe false
      val exitOnly = Json.obj("generation" -> Json.obj("outcome" -> "generated".asJson, "batch" -> input.publication.asJson,
        "products" -> Vector("/consumer/Output.scala").asJson, "results" -> Vector("/consumer/result.json").asJson))
      ManagedDevelopmentRuntime.generationReportValid(input, pub, exitOnly) shouldBe false
    }

    "reject changed modified or untracked contents even when Git status and paths stay identical" in {
      val dirty = Vector(
        RepositorySyncManagedGitObservation.DirtyPath(' ', 'M', "already-modified.scala", None),
        RepositorySyncManagedGitObservation.DirtyPath('?', '?', "untracked.scala", None))
      val observed = RepositorySyncManagedGitObservation.Observed("main", "a" * 40, "b" * 40, dirty, None, None)
      dirty.foreach { changed =>
        Given("unchanged Git HEAD, tree and modified/untracked path metadata")
        var contents = dirty.map(entry => entry.path -> "before".getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector).toMap
        val m = new Memory {
          override def sourceC(e: Edge): Consequence[Json] = ManagedDevelopmentRuntime.sourceContentsC(observed,
            name => Consequence.success(contents.get(name)))
          override def executeC(e: Edge, i: Input, selected: Option[Publication]): Consequence[Execution] = {
            contents = contents.updated(changed.path, "after!".getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector)
            super.executeC(e, i, selected)
          }
        }
        val before = take(m.sourceC(edge))
        When("the successful producer execution changes an already dirty file's contents")
        run("publish", m).status shouldBe "unverified"
        val after = take(m.sourceC(edge))
        before.hcursor.downField("dirty").focus shouldBe after.hcursor.downField("dirty").focus
        before should not be after
        Then("the changed output is retained as unverified and cannot be selected or replayed")
        m.rows.contains(ManagedDevelopment.key(definition, edge, "publication", input.publication)) shouldBe false
        runC("select", definition, input, m).isSuccess shouldBe false
        run("status", m).status shouldBe "unverified"
        run("publish", m).status shouldBe "unverified"
        m.calls shouldBe 1
      }
    }

    "publish a real Scala library privately and compile a never-published original dependency through the public Operations" in {
      Given("an enabled published sbt-cozy plugin and two explicitly registered build workspaces")
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase7-managed-development").toAbsolutePath), "native-").toRealPath()
      prepareBuild(root.resolve("repository"), producer = true)
      prepareBuild(root.resolve("consumer"), producer = false)
      Files.createDirectories(root.resolve(".textus/sm-workflow/work.d"))
      val shared = Path.of(System.getProperty("user.home"), ".ivy2/local", edge.requested.organization, edge.requested.module)
      val baselineExists = Files.exists(shared)
      val baseline = tree(shared)
      val storage = ThreeWorkflowNormalHostFixture.storagePlan.mapObject(fields => fields.add("records",
        Json.fromValues(fields("records").get.asArray.get :+ Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
          "storeName" -> "workflow".asJson, "collection" -> collection.asJson))))
      _with_runtime_(root, "DevelopmentArtifacts", hostParameters = Map("sm-workflow.managed-development" -> policy.asJson.noSpaces,
        "textus.subsystem.storage.plan" -> storage.noSpaces)) { (subsystem, component, context, setup) =>
        setup(call => call.setupC(_setup_commands_(root, "Goal").size, scenario = "Goal"))
        def call(operation: String, value: Input): V.ManagedDevelopmentResult = read_control_value[V.ManagedDevelopmentResult](
          take(subsystem.executeOperationResponseInContext(Request.of(component = component.name,
            service = "ManagedDevelopmentService", operation = operation,
            properties = List(Property("input", GoalPhaseNativeMetadata.record(value.asJson), None))), context)))
        When("the actual producer publishes without shared publishLocal")
        val publication = call("publishDevelopmentArtifact", input)
        withClue(publication.toRecord().toString) { publication.status shouldBe "published" }
        val selected = input.copy(requestIdentity = "consumer-one", selection = "consumer-selection-one")
        call("selectDevelopmentArtifact", selected).status shouldBe "selected-unverified"
        Then("the original dependency need never have existed in a baseline repository")
        Files.exists(shared) shouldBe baselineExists
        val compiled = call("compileDevelopmentConsumer", selected)
        withClue(compiled.toRecord().toString) { compiled.status shouldBe "verified" }
        // Observe the exact already-written native envelope and selected record,
        // rather than using active/pinned protection as a build-input selector.
        var observedInput = Option.empty[RepositorySyncConsumedInputs.Subject]
        setup(c => ManagedDevelopmentRuntime.repositorySyncInputC(c.core, policy, selected).map { actual =>
          observedInput = Some(actual)
        })
        observedInput.get.input shouldBe selected
        observedInput.get.publication.identity shouldBe input.publication
        observedInput.get.envelope.hcursor.get[String]("kind").toOption shouldBe Some("consumer")
        observedInput.get.envelope.hcursor.get[String]("buildWorkspace").toOption shouldBe Some(edge.consumer.workspace)
        setup { c =>
          import org.goldenport.cncf.processexecution.{SbtScenarioSelection, SbtTarget, SbtValidationMode}
          import org.goldenport.cncf.workspace.WorkspaceId
          val member = RepositorySyncDirectoryList.Member(edge.consumer.repository, edge.consumer.workspace,
            root.resolve("consumer").toString, "main", "origin", "main", "configured", "selected-consumer-session")
          val observation = RepositorySyncConsumedInputs.Observation(Vector(observedInput.get), None)
          val change = RepositorySyncConsumedInputs.Change(observation, Some(observation))
          val bound = RepositorySyncConsumedInputs.bind(c.core, member, Some(change))
          val workspace = take(WorkspaceId.parseC(edge.consumer.workspace))
          val discovery = take(RepositorySyncConsumedInputs.targetC(bound, SbtTarget(workspace, validationDiscovery = true)))
          val cases = Vector(SbtValidationMode.Case(SbtScenarioSelection("example.SelectedSpec", "exact discovered case"), "develop"))
          val testing = take(RepositorySyncConsumedInputs.targetC(bound, SbtTarget(workspace, validationBatch = cases)))
          discovery.scopedInput shouldBe testing.scopedInput
          discovery.validationDiscovery shouldBe true
          testing.validationBatch shouldBe cases
          discovery.scopedInput.get.buildWorkspace shouldBe workspace
          discovery.scopedInput.get.ownerWorkspace.value shouldBe definition.ownerWorkspace
          discovery.scopedInput.get.selection shouldBe selected.selection
          discovery.scopedInput.get.input.value shouldBe observedInput.get.path
          discovery.scopedInput.get.publication shouldBe None
          RepositorySyncConsumedInputs.targetC(bound, SbtTarget(take(WorkspaceId.parseC("foreign-consumer")))).isSuccess shouldBe false
          RepositorySyncConsumedInputs.targetC(bound, testing).isSuccess shouldBe false
          val unknown = RepositorySyncConsumedInputs.bind(c.core, member, Some(change.copy(after = None)))
          RepositorySyncConsumedInputs.targetC(unknown, SbtTarget(workspace, validationDiscovery = true)).isSuccess shouldBe false
          Consequence.unit
        }
        setup { c =>
          ManagedDevelopmentRuntime.repositorySyncInputC(c.core, policy,
            selected.copy(requestIdentity = "never-written-input")).isSuccess shouldBe false
          Consequence.unit
        }
        call("readDevelopmentArtifactStatus", selected).status shouldBe "verified"
        val output = root.resolve("consumer/target/scala-2.12/classes/example/Consumer$.class")
        Files.isRegularFile(output) shouldBe true
        val source = root.resolve("consumer/src/main/scala/example/Consumer.scala")
        Files.writeString(source, "package example\nobject Consumer { val incompatible: Int = Provider.missingApi }\n")
        val incompatible = call("compileDevelopmentConsumer", selected.copy(requestIdentity = "consumer-incompatible"))
        incompatible.status shouldBe "failed"
        call("readDevelopmentArtifactStatus", selected.copy(requestIdentity = "consumer-incompatible")).status shouldBe "failed"
        tree(shared) shouldBe baseline
        val archive = root.resolve(".textus/sm-workflow/work.d").resolve(base(definition, edge, input.publication))
          .resolve("jars/" + edge.requested.module + ".jar")
        Files.isRegularFile(archive) shouldBe true
        val marker = archive.getParent.getParent.resolve("completed.json")
        Files.isRegularFile(marker) shouldBe true
        call("publishDevelopmentArtifact", input).status shouldBe "published"
      }
      tree(shared) shouldBe baseline
    }

    "connect the adopted batch and selected generator through native Operations in two consumers" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase7-managed-development").toAbsolutePath), "generation-").toRealPath()
      val local = Path.of(System.getProperty("user.home"), ".ivy2/local")
      val roots = Vector(
        ("cozy", Coordinate("org.simplemodeling", "cozy_2.12", "0.3.3-SNAPSHOT"), "cozy", "2.12.20"),
        ("modeler", Coordinate("org.simplemodeling", "simplemodeler_2.12", "1.1.26-SNAPSHOT"), "simplemodeler", "2.12.20"),
        ("cncf", Coordinate("org.goldenport", "goldenport-cncf_3", "0.5.6-SNAPSHOT"), "goldenport-cncf", "3.3.8"))
      val batch = Batch(roots.map((project, c, _, _) => ProjectModule(project, c)), Vector(roots.last._2),
        Some(Generator(roots.head._2.version, roots(1)._2.version, roots.take(2).map(_._2))))
      val primary = edge.copy(requested = roots.last._2, batch = Some(batch))
      val second = primary.copy(identity = "consumer-two-provider", consumer = primary.consumer.copy(
        repository = "consumer-two-repository", workspace = "development-consumer-two"))
      val declared = definition.copy(edges = Vector(primary, second))
      def quoted(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
      def write(base: Path, name: String, text: String): Unit = {
        val file = base.resolve(name)
        Files.createDirectories(file.getParent)
        Files.writeString(file, text)
      }
      def bootstrap(base: Path): Unit = {
        write(base, "project/build.properties", "sbt.version=1.11.7\n")
        write(base, "project/plugins.sbt",
          "resolvers += \"SimpleModeling.org\" at \"https://www.simplemodeling.org/repository/maven\"\n" +
          "resolvers += Resolver.defaultLocal\naddSbtPlugin(\"org.goldenport\" % \"sbt-cozy\" % \"0.1.18-SNAPSHOT\")\n")
        write(base, ".gitignore", "target/\nproject/target/\nproject/project/\n")
      }
      val producer = root.resolve("repository")
      bootstrap(producer)
      write(producer, "src/test/scala/ProductCheck.scala", "object ProductCheck\n")
      write(producer, "value.txt", "fixture setup input\n")
      val shared = roots.map((_, c, _, _) => local.resolve(c.organization).resolve(c.module).resolve(c.version))
      val before = shared.map(tree)
      // Repackage actual owner-delivered JARs and Ivy, never authored generated Scala.
      val projects = roots.map { (project, c, name, scala) =>
        val base = local.resolve(c.organization).resolve(c.module).resolve(c.version)
        val jar = base.resolve("jars/" + c.module + ".jar")
        val ivy = base.resolve("ivys/ivy.xml")
        Files.isRegularFile(jar) shouldBe true
        Files.isRegularFile(ivy) shouldBe true
        val sources = quoted(base.resolve("srcs/" + c.module + "-sources.jar").toString)
        val pom = quoted(base.resolve("poms/" + c.module + ".pom").toString)
        val incompleteSelection = quoted("\"selection\":\"batch-incomplete\"")
        val packaged = if (project == "cncf") s"""if (sys.props.get("sm.artifact.selection").exists(path => IO.read(file(path)).contains($incompleteSelection))) declared.filterNot(_._1.extension == "pom") else declared"""
          else "declared"
        val conflictSelection = quoted("\"selection\":\"batch-conflict\"")
        val conflictXml = s"""<ivy-module version="2.0"><info organisation="${c.organization}" module="${c.module}" revision="${c.version}"/><dependencies><dependency org="org.simplemodeling" name="simplemodeler_2.12" rev="${roots(1)._2.version}-conflicting"/></dependencies></ivy-module>"""
        val descriptor = if (project == "cozy") s"""if (sys.props.get("sm.artifact.selection").exists(path => IO.read(file(path)).contains($conflictSelection))) { val conflict = target.value / "p7-conflicting-ivy.xml"; IO.write(conflict, ${quoted(conflictXml)}); conflict } else file(${quoted(ivy.toString)})"""
          else s"file(${quoted(ivy.toString)})"
        s"""lazy val $project = project.in(file("$project")).settings(organization := "${c.organization}", name := "$name", version := "${c.version}", scalaVersion := "$scala", Compile / packageBin := file(${quoted(jar.toString)}), makeIvyXml := { $descriptor }, packagedArtifacts := { val declared = Map(Artifact("${c.module}") -> file(${quoted(jar.toString)}), Artifact("${c.module}").withType("src").withClassifier(Some("sources")) -> file($sources), Artifact("${c.module}").withType("pom").withExtension("pom") -> file($pom)); $packaged })"""
      }.mkString("\n")
      write(producer, "build.sbt", "import org.goldenport.cozy.CozyDevelopmentArtifactsPlugin\n" + projects +
        "\nlazy val root = project.in(file(\".\")).enablePlugins(CozyDevelopmentArtifactsPlugin)\n")
      Vector(root.resolve("consumer"), root.resolve("consumer-two")).foreach { consumer =>
        bootstrap(consumer)
        write(consumer, "build.sbt", """import org.goldenport.cozy.{CozyPlugin, CozyDevelopmentArtifactsPlugin}
import CozyPlugin.autoImport._
import CozyDevelopmentArtifactsPlugin.autoImport._

lazy val p7OldRuntimeCompile = taskKey[Unit]("Compile the selected generator's actual output against pinned old CNCF")
lazy val p7OldRuntimeEvidence = taskKey[Unit]("Retain the isolated old compiler's resolved runtime and source inputs")

lazy val root = project.in(file(".")).enablePlugins(CozyPlugin, CozyDevelopmentArtifactsPlugin).settings(
  scalaVersion := "3.3.8",
  resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven",
  cozyGenerationTarget := "cncf",
  cozyDelegateCoursierVersion := Some("0.3.3-SNAPSHOT"),
  cozyDevelopmentModules := Seq("org.goldenport" %% "goldenport-cncf" % "0.5.6-SNAPSHOT"),
  libraryDependencies += cozyDevelopmentDependency("org.goldenport" %% "goldenport-cncf" % "0.5.6-SNAPSHOT").value,
  p7OldRuntimeCompile := Def.taskDyn {
    val oldRuntime = sys.props.get("sm.artifact.selection").exists(path =>
      IO.read(file(path)).contains("\"selection\":\"compiler-old-cncf\""))
    if (oldRuntime) Def.task { (LocalProject("oldCompiler") / Compile / compile).value; () }
    else Def.task { () }
  }.value,
  Compile / compile := ((Compile / compile) dependsOn p7OldRuntimeCompile).value
)

// This independent compiler fixture spans the overlay guard boundary: the
// selected root still generates with its real compatible development runtime.
// No root products, project dependency, selected runtime or generated-source
// replacement is supplied to the compiler pinned to the ordinary old runtime.
lazy val oldCompiler = project.in(file("old-compiler"))
  .disablePlugins(CozyPlugin, CozyDevelopmentArtifactsPlugin).settings(
    scalaVersion := "3.3.8",
    resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven",
    libraryDependencies += "org.goldenport" %% "goldenport-cncf" % "0.5.2",
    dependencyOverrides += "org.goldenport" %% "goldenport-cncf" % "0.5.2",
    Compile / sources := (LocalRootProject / Compile / managedSources).value.filter(source =>
      Set("SampleLogicalUiDefinitions.scala", "SampleLogicalUiMetadataProvider.scala").contains(source.getName)),
    p7OldRuntimeEvidence := {
      val generatedSources = (Compile / sources).value
      require(generatedSources.map(_.getName).toSet == Set("SampleLogicalUiDefinitions.scala", "SampleLogicalUiMetadataProvider.scala"),
        "the old compiler requires the real selected Logical UI output")
      val modules = update.value.configurations.flatMap(_.modules).filter(module =>
        module.module.organization == "org.goldenport" && module.module.name == "goldenport-cncf_3")
      require(modules.nonEmpty && modules.forall(_.module.revision == "0.5.2"), "old CNCF must actually resolve")
      val jars = modules.flatMap(_.artifacts.collect { case (artifact, jar) if artifact.extension == "jar" => jar }).distinct
      require(jars.size == 1, "one exact old CNCF binary is required")
      val classpath = (Compile / dependencyClasspath).value.files
      val rootClasses = (LocalRootProject / Compile / classDirectory).value.getCanonicalFile
      require(classpath.contains(jars.head), "the resolved old binary must reach the compiler")
      require(!classpath.exists(path => path.getAbsolutePath.contains(".textus/sm-workflow/work.d") ||
        path.getCanonicalFile == rootClasses), "the old compiler must be isolated")
      val archive = new java.util.jar.JarFile(jars.head)
      try {
        require(archive.getEntry("org/goldenport/cncf/display/GeneratedLogicalUiAbi.class") == null)
        require(archive.getEntry("org/goldenport/cncf/display/GeneratedLogicalUiMetadataProvider.class") == null)
      } finally archive.close()
      val evidence = Vector("runtime=org.goldenport:goldenport-cncf_3:0.5.2", "jar=" + jars.head.getCanonicalPath) ++
        classpath.map(path => "classpath=" + path.getCanonicalPath) ++ generatedSources.map(source => "source=" + source.getCanonicalPath)
      IO.write(target.value / "p7-old-runtime-evidence.txt", evidence.mkString("\n"))
      streams.value.log.info("P7 pinned compiler runtime org.goldenport:goldenport-cncf_3:0.5.2; generated Logical UI ABI absent")
    },
    Compile / compile := ((Compile / compile) dependsOn p7OldRuntimeEvidence).value
  )
""")
        write(consumer, "project.yaml", """project:
  namespace: com.example
  id: Sample
  kind: car
  component:
    version: 0.0.1-SNAPSHOT
build:
  scalaVersion: 3.3.8
  cozyVersion: 0.3.3-SNAPSHOT
  dependencies:
    compile:
      - org.goldenport::goldenport-cncf:0.5.6-SNAPSHOT
packaging:
  kind: car
  car:
    runtime:
      cncf:
        minimum: 0.5.6-SNAPSHOT
        excluded: []
        tested:
          - 0.5.6-SNAPSHOT
""")
        // Reduced from Cozy baed5f17's logicalui/ess2-01.cml, with an explicit
        // owner, package and provenance for this self-contained consumer fixture.
        write(consumer, "src/main/cozy/sample.cml", """# COMPONENT

## Sample

### PACKAGE

com.example

# LOGICAL-UI
component = "Sample"
producerId = "cozy.logical-ui-generated.v1"
sourceId = "phase7-generated-api"
sourceRevision = "phase7-generated-api-1"

## LIST
screenId = "collecting-resources"
definitionRevision = "ess-definition-1"
viewBindingId = "ess.reference"
title = "Collecting Resources"
detailTarget = "capture-detail"

### ITEM-FIELDS

#### FIELD
id = "title"
label = "Title"
role = "title"
bindingId = "ess.title"
allowedKinds = ["text"]

## DETAIL
screenId = "capture-detail"
definitionRevision = "ess-definition-1"
viewBindingId = "ess.reference"
title = "Capture Detail"

### SECTIONS

#### SECTION
id = "main"

##### FIELDS

###### FIELD
id = "title"
label = "Title"
role = "title"
bindingId = "ess.title"
allowedKinds = ["text"]
""")
        write(consumer, "src/main/car/abi-manifest.json", """{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"com.example","id":"Sample","version":"0.0.1-SNAPSHOT"},"abi":{"version":1,"exports":{"components":[{"namespace":"com.example","id":"Sample"}],"operations":[],"entities":[]},"dependencies":[]}}""")
      }
      Files.createDirectories(root.resolve(".textus/sm-workflow/work.d"))
      val storage = ThreeWorkflowNormalHostFixture.storagePlan.mapObject(fields => fields.add("records",
        Json.fromValues(fields("records").get.asArray.get :+ Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
          "storeName" -> "workflow".asJson, "collection" -> collection.asJson))))
      _with_runtime_(root, "DevelopmentArtifacts", hostParameters = Map("sm-workflow.managed-development" ->
        policy.copy(definition = declared).asJson.noSpaces, "textus.subsystem.storage.plan" -> storage.noSpaces)) { (subsystem, component, context, setup) =>
        setup(call => call.setupC(_setup_commands_(root, "Goal").size, scenario = "Goal"))
        def call(operation: String, value: Input): V.ManagedDevelopmentResult = read_control_value[V.ManagedDevelopmentResult](
          take(subsystem.executeOperationResponseInContext(Request.of(component = component.name,
            service = "ManagedDevelopmentService", operation = operation,
            properties = List(Property("input", GoalPhaseNativeMetadata.record(value.asJson), None))), context)))
        Given("two complete real Cozy/Modeler/CNCF batches in one native private repository")
        Vector("a", "b", "prune-candidate").foreach { name =>
          val result = call("publishDevelopmentArtifact", input.copy(requestIdentity = "publish-" + name, publication = name))
          withClue(result.toRecord().toString) { result.status shouldBe "published" }
        }
        val partial = input.copy(requestIdentity = "publish-partial-batch", publication = "partial-batch", selection = "batch-incomplete")
        val incomplete = call("publishDevelopmentArtifact", partial)
        withClue(incomplete.toRecord().toString) { incomplete.status shouldBe "failed" }
        take(incomplete.detail.toJsonStringC) should include ("Ivy descriptor publications differ from packaged artifacts")
        val owned = root.resolve(".textus/sm-workflow/work.d")
        tree(owned.resolve(declared.staging).resolve(declared.scope).resolve(partial.publication)).nonEmpty shouldBe true
        Files.exists(owned.resolve(ManagedDevelopmentRuntime.batchPath(declared, partial.publication)).resolve("completed.json")) shouldBe false
        roots.foreach { (_, coordinate, _, _) =>
          Files.exists(owned.resolve(ManagedDevelopmentRuntime.moduleBase(declared, coordinate, partial.publication)).resolve("completed.json")) shouldBe false
        }
        subsystem.executeOperationResponseInContext(Request.of(component = component.name,
          service = "ManagedDevelopmentService", operation = "selectDevelopmentArtifact",
          properties = List(Property("input", GoalPhaseNativeMetadata.record(partial.copy(requestIdentity = "select-partial-batch").asJson), None))), context).isSuccess shouldBe false
        val conflicting = input.copy(requestIdentity = "publish-conflicting-batch", publication = "conflicting-batch", selection = "batch-conflict")
        val conflict = call("publishDevelopmentArtifact", conflicting)
        withClue(conflict.toRecord().toString) { conflict.status shouldBe "failed" }
        take(conflict.detail.toJsonStringC) should include ("batch dependency disagrees with declared producer version")
        Files.exists(owned.resolve(ManagedDevelopmentRuntime.batchPath(declared, conflicting.publication)).resolve("completed.json")) shouldBe false
        subsystem.executeOperationResponseInContext(Request.of(component = component.name,
          service = "ManagedDevelopmentService", operation = "selectDevelopmentArtifact",
          properties = List(Property("input", GoalPhaseNativeMetadata.record(conflicting.copy(requestIdentity = "select-conflicting-batch").asJson), None))), context).isSuccess shouldBe false
        When("fresh invocations update and roll back A to B to A using unchanged build definitions")
        Vector("a", "b", "a").zipWithIndex.foreach { (name, index) =>
          val selected = input.copy(requestIdentity = "verify-" + index, publication = name, selection = "selection-" + name)
          call("selectDevelopmentArtifact", selected).status shouldBe "selected-unverified"
          val result = call("verifyDevelopmentArtifact", selected)
          withClue(result.toRecord().toString) { result.status shouldBe "verified" }
          val details = take(result.detail.toJsonStringC)
          details should include (version(roots.head._2.version, declared.scope, name))
          details should include ("producerResolution")
          details should include ("codeSource")
          val resolution = io.circe.parser.parse(details).toOption.get.hcursor.downField("data").downField("resolution").focus.get
          val reference = resolution.hcursor.downField("rawReport")
          reference.get[String]("workspace").toOption shouldBe Some(declared.ownerWorkspace)
          val reportPath = "inputs/" + selected.edgeIdentity + "/consumer/" + selected.requestIdentity + "/result.json"
          reference.get[String]("path").toOption shouldBe Some(reportPath)
          val originalBytes = Files.readAllBytes(owned.resolve(reportPath))
          reference.get[Int]("byteCount").toOption shouldBe Some(originalBytes.length)
          reference.get[String]("sha256").toOption shouldBe Some(java.security.MessageDigest.getInstance("SHA-256")
            .digest(originalBytes).map(b => f"${b & 0xff}%02x").mkString)
          val original = io.circe.jawn.JawnParser(allowDuplicateKeys = false)
            .parse(new String(originalBytes, java.nio.charset.StandardCharsets.UTF_8)).toOption.get
          resolution.hcursor.downField("results").focus shouldBe original.hcursor.downField("results").focus
          val rawGeneration = original.hcursor.downField("generation")
          val compactGeneration = resolution.hcursor.downField("generation")
          Vector("outcome", "batch", "results", "products").foreach { field =>
            compactGeneration.downField(field).focus shouldBe rawGeneration.downField(field).focus
          }
          val rawObservations = rawGeneration.get[Vector[Json]]("observations").toOption.get
          val compactObservations = compactGeneration.get[Vector[Json]]("observations").toOption.get
          compactObservations.size shouldBe rawObservations.size
          compactObservations.zip(rawObservations).foreach { (compact, raw) =>
            compact.mapObject(_.remove("producerResolution")) shouldBe raw.mapObject(_.remove("producerResolution"))
            val rawProducer = raw.hcursor.downField("producerResolution")
            val compactProducer = compact.hcursor.downField("producerResolution")
            compactProducer.downField("producerRunReference").focus shouldBe rawProducer.downField("producerRunReference").focus
            compactProducer.get[Int]("reportRowCount").toOption shouldBe Some(rawProducer.get[Vector[Json]]("reportRows").toOption.get.size)
            compactProducer.downField("reportRows").focus shouldBe None
          }
          resolution.noSpaces.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be < originalBytes.length
          val status = call("readDevelopmentArtifactStatus", selected)
          status.status shouldBe "verified"
          io.circe.parser.parse(take(status.detail.toJsonStringC)).toOption.get.hcursor
            .downField("data").downField("resolution").focus shouldBe Some(resolution)
          val generated = tree(root.resolve("consumer/target"))
          generated.keys.exists(_.endsWith("/SampleLogicalUiDefinitions.scala")) shouldBe true
          generated.keys.exists(_.endsWith("/SampleLogicalUiDefinitions$.class")) shouldBe true
          details should include (version(roots.last._2.version, declared.scope, name))
        }
        val firstTree = tree(root.resolve("consumer/target"))
        val secondInput = input.copy(edgeIdentity = second.identity, requestIdentity = "second-verify", publication = "b",
          selection = "second-b", publicationEdge = Some(primary.identity))
        call("selectDevelopmentArtifact", secondInput).status shouldBe "selected-unverified"
        val compiled = call("verifyDevelopmentArtifact", secondInput)
        withClue(compiled.toRecord().toString) { compiled.status shouldBe "verified" }
        tree(root.resolve("consumer/target")) shouldBe firstTree
        val secondDetails = take(compiled.detail.toJsonStringC)
        secondDetails should include (version(roots.head._2.version, declared.scope, "b"))
        secondDetails should include (version(roots.last._2.version, declared.scope, "b"))
        val logicalUiNames = Set("SampleLogicalUiDefinitions.scala", "SampleLogicalUiMetadataProvider.scala")
        val managedLogicalUi = root.resolve("consumer-two/target/scala-3.3.8/src_managed/main/scala/com/example/logicalui")
          .toAbsolutePath.normalize()
        val reportedProducts = io.circe.parser.parse(secondDetails).toOption.get.hcursor
          .downField("data").downField("resolution").downField("generation")
          .get[Vector[String]]("products").toOption.get
        val generatedPaths = reportedProducts.map(Path.of(_)).filter(path => logicalUiNames(path.getFileName.toString))
        generatedPaths.size shouldBe 2
        generatedPaths.foreach { path =>
          path.isAbsolute shouldBe true
          path.normalize() shouldBe path
          path.getParent shouldBe managedLogicalUi
          Files.isRegularFile(path) shouldBe true
        }
        generatedPaths.toSet shouldBe logicalUiNames.map(managedLogicalUi.resolve(_))
        def generatedBytes = generatedPaths.map(path => path -> Files.readAllBytes(path).toVector).toMap
        val generatedBefore = generatedBytes
        Then("the exact selected generator output fails against an actually resolved old CNCF runtime")
        val oldInput = secondInput.copy(requestIdentity = "compiler-negative", selection = "compiler-old-cncf")
        call("selectDevelopmentArtifact", oldInput).status shouldBe "selected-unverified"
        val rejected = call("verifyDevelopmentArtifact", oldInput)
        withClue(rejected.toRecord().toString) { rejected.status shouldBe "failed" }
        val diagnostics = take(rejected.detail.toJsonStringC)
        val compilerData = io.circe.parser.parse(diagnostics).toOption.get.hcursor.downField("data")
        compilerData.get[String]("termination").toOption shouldBe Some("Exited(1)")
        val compilerStdout = compilerData.get[String]("stdout").toOption.get
        compilerStdout should include ("P7 pinned compiler runtime org.goldenport:goldenport-cncf_3:0.5.2")
        val diagnosticLines = compilerStdout.linesIterator.toVector
        val diagnosticStarts = diagnosticLines.indices.filter(index => diagnosticLines(index).startsWith("[error] -- ")).toVector
        val diagnosticBlocks = diagnosticStarts.zip(diagnosticStarts.drop(1) :+ diagnosticLines.size)
          .map { (start, end) => diagnosticLines.slice(start, end) }
        val missingAbiDiagnostics = Set(
          "value display is not a member of org.goldenport.cncf",
          "value GeneratedLogicalUiAbi is not a member of org.goldenport.cncf.display",
          "type GeneratedLogicalUiAbi is not a member of org.goldenport.cncf.display")
        val generatedImport = "import org.goldenport.cncf.display.GeneratedLogicalUiAbi"
        generatedPaths.foreach { path =>
          val header = ("\\[error\\] -- \\[E008\\] Not Found Error: " + java.util.regex.Pattern.quote(path.toString) +
            ":([0-9]+):[0-9]+\\s*").r
          val sourceLines = Files.readAllLines(path)
          withClue(s"missing generated ABI diagnostic for $path in $compilerStdout") {
            diagnosticBlocks.exists { block =>
              block.head match {
                case header(lineNumber) =>
                  val line = lineNumber.toInt
                  line > 0 && line <= sourceLines.size() && sourceLines.get(line - 1).trim == generatedImport &&
                    block.exists(_.matches("\\[error\\]\\s+" + line + " \\|\\s*" +
                      java.util.regex.Pattern.quote(generatedImport) + "\\s*")) &&
                    block.exists(value => missingAbiDiagnostics.contains(value.stripPrefix("[error]").trim.stripPrefix("|").trim))
                case _ => false
              }
            } shouldBe true
          }
        }
        val oldEvidence = Files.readString(root.resolve("consumer-two/old-compiler/target/p7-old-runtime-evidence.txt"))
        oldEvidence should include ("runtime=org.goldenport:goldenport-cncf_3:0.5.2")
        oldEvidence.linesIterator.filter(_.startsWith("source=")).map(_.stripPrefix("source=")).toSet shouldBe
          generatedPaths.map(_.toString).toSet
        generatedBytes shouldBe generatedBefore
        tree(root.resolve("consumer/target")) shouldBe firstTree
        call("readDevelopmentArtifactStatus", oldInput).status shouldBe "failed"
        val restored = call("verifyDevelopmentArtifact", secondInput.copy(requestIdentity = "compatible-after-old"))
        withClue(restored.toRecord().toString) { restored.status shouldBe "verified" }
        take(restored.detail.toJsonStringC) should include (version(roots.last._2.version, declared.scope, "b"))
        generatedBytes shouldBe generatedBefore
        val secondTree = tree(root.resolve("consumer-two/target"))
        val detached = call("detachDevelopmentArtifact", input.copy(requestIdentity = "detach-a", publication = "a", selection = "selection-a"))
        withClue(detached.toRecord().toString) { detached.status shouldBe "detached" }
        tree(root.resolve("consumer-two/target")) shouldBe secondTree
        call("pruneDevelopmentArtifact", secondInput.copy(requestIdentity = "protected-b-prune")).status shouldBe "prune-unavailable"
        val repository = root.resolve(".textus/sm-workflow/work.d").resolve(declared.repository)
        val repositoryBeforePrune = tree(repository)
        val candidate = input.copy(requestIdentity = "native-unprotected-prune", publication = "prune-candidate", selection = "unused-candidate")
        val pruned = call("pruneDevelopmentArtifact", candidate)
        withClue(pruned.toRecord().toString) { pruned.status shouldBe "pruned" }
        val pruneDetails = take(pruned.detail.toJsonStringC)
        pruneDetails should include ("Deleted")
        pruneDetails should include ("completed-v3.json")
        pruneDetails should include ("sources.jar")
        pruneDetails should include (".pom")
        val removed = repositoryBeforePrune.keySet -- tree(repository).keySet
        removed.nonEmpty shouldBe true
        removed.forall(name => name.contains(version(roots.head._2.version, declared.scope, candidate.publication)) ||
          name.contains(version(roots(1)._2.version, declared.scope, candidate.publication)) ||
          name.contains(version(roots.last._2.version, declared.scope, candidate.publication)) ||
          name.startsWith(".batches/" + declared.scope + "/" + candidate.publication + "/")) shouldBe true
        removed.exists(_.endsWith(".claim")) shouldBe false
        tree(repository) shouldBe repositoryBeforePrune.filterNot { (name, _) => removed.contains(name) }
        call("pruneDevelopmentArtifact", candidate).status shouldBe "pruned"
        call("readDevelopmentArtifactStatus", candidate).status shouldBe "pruned"
        subsystem.executeOperationResponseInContext(Request.of(component = component.name,
          service = "ManagedDevelopmentService", operation = "selectDevelopmentArtifact",
          properties = List(Property("input", GoalPhaseNativeMetadata.record(candidate.copy(requestIdentity = "select-pruned").asJson), None))), context).isSuccess shouldBe false
        tree(root.resolve("consumer-two/target")) shouldBe secondTree
        shared.map(tree) shouldBe before
      }
    }

    "keep two actual source and artifact lanes independent across a failed consumer and runtime reconnection" in {
      Given("two explicitly registered source/consumer pairs and separate scoped artifact repositories")
      val parent = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase7-managed-development").toAbsolutePath), "lanes-").toRealPath()
      val laneA = parent.resolve("lane-a")
      val laneB = parent.resolve("lane-b")
      val definitionB = definition.copy(scope = "lane-b")
      Vector(laneA, laneB).foreach { root =>
        prepareBuild(root.resolve("repository"), producer = true)
        prepareBuild(root.resolve("consumer"), producer = false)
        Files.createDirectories(root.resolve(".textus/sm-workflow/work.d"))
      }
      Files.writeString(laneB.resolve("repository/src/main/scala/example/Provider.scala"),
        "package example\nobject Provider { def answer: Int = 84 }\n")
      val consumer = input.copy(requestIdentity = "consumer-one", selection = "consumer-selection-one")
      val failedInput = consumer.copy(requestIdentity = "consumer-incompatible")
      val shared = Path.of(System.getProperty("user.home"), ".ivy2/local", edge.requested.organization, edge.requested.module)
      val baseline = tree(shared)
      val storage = ThreeWorkflowNormalHostFixture.storagePlan.mapObject(fields => fields.add("records",
        Json.fromValues(fields("records").get.asArray.get :+ Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
          "storeName" -> "workflow".asJson, "collection" -> collection.asJson))))
      // Reopening A uses its existing native records, without re-Start or
      // re-running fixture Git setup. This is not automatic Goal resumption.
      def withLane(root: Path, d: Definition, initialize: Boolean)
        (task: ((String, Input) => V.ManagedDevelopmentResult) => Unit): Unit =
        _with_runtime_(root, "DevelopmentArtifacts", hostParameters = Map(
          "sm-workflow.managed-development" -> policy.copy(definition = d).asJson.noSpaces,
          "textus.subsystem.storage.plan" -> storage.noSpaces)) { (subsystem, component, context, setup) =>
          if (initialize) setup(call => call.setupC(_setup_commands_(root, "Goal").size, scenario = "Goal"))
          def call(operation: String, value: Input): V.ManagedDevelopmentResult = read_control_value[V.ManagedDevelopmentResult](
            take(subsystem.executeOperationResponseInContext(Request.of(component = component.name,
              service = "ManagedDevelopmentService", operation = operation,
              properties = List(Property("input", GoalPhaseNativeMetadata.record(value.asJson), None))), context)))
          task(call)
        }
      def jar(root: Path, d: Definition): Path = root.resolve(".textus/sm-workflow/work.d")
        .resolve(base(d, edge, input.publication)).resolve("jars/" + edge.requested.module + ".jar")

      When("lane A retains a failed consumer after its completed scoped publication")
      withLane(laneA, definition, initialize = true) { call =>
        call("publishDevelopmentArtifact", input).status shouldBe "published"
        call("selectDevelopmentArtifact", consumer).status shouldBe "selected-unverified"
        Files.writeString(laneA.resolve("consumer/src/main/scala/example/Consumer.scala"),
          "package example\nobject Consumer { val answer: Int = Provider.missingApi }\n")
        call("compileDevelopmentConsumer", failedInput).status shouldBe "failed"
      }
      val originalA = tree(jar(laneA, definition).getParent.getParent)
      Then("the unrelated lane publishes and compiles the same ordinary coordinates independently")
      withLane(laneB, definitionB, initialize = true) { call =>
        call("publishDevelopmentArtifact", input).status shouldBe "published"
        // A's selection has the same public identity, but does not exist in B.
        call("readDevelopmentArtifactStatus", consumer).status shouldBe "published"
        call("selectDevelopmentArtifact", consumer).status shouldBe "selected-unverified"
        val result = call("compileDevelopmentConsumer", consumer)
        withClue(result.toRecord().toString) { result.status shouldBe "verified" }
        val details = take(result.detail.toJsonStringC)
        details should include(version(edge.requested.version, definitionB.scope, input.publication))
        details should not include (jar(laneA, definition).toString)
        Files.isRegularFile(laneB.resolve("consumer/target/scala-2.12/classes/example/Consumer$.class")) shouldBe true
      }
      val originalB = tree(jar(laneB, definitionB).getParent.getParent)
      Files.readAllBytes(jar(laneA, definition)).toVector should not be Files.readAllBytes(jar(laneB, definitionB)).toVector
      tree(jar(laneA, definition).getParent.getParent) shouldBe originalA
      withLane(laneA, definition, initialize = false) { call =>
        call("readDevelopmentArtifactStatus", failedInput).status shouldBe "failed"
        Files.writeString(laneA.resolve("consumer/src/main/scala/example/Consumer.scala"),
          "package example\nobject Consumer { val answer: Int = Provider.answer }\n")
        val result = call("compileDevelopmentConsumer", consumer)
        withClue(result.toRecord().toString) { result.status shouldBe "verified" }
        val details = take(result.detail.toJsonStringC)
        details should include(version(edge.requested.version, definition.scope, input.publication))
        details should not include (jar(laneB, definitionB).toString)
        call("readDevelopmentArtifactStatus", failedInput).status shouldBe "failed"
      }
      tree(jar(laneB, definitionB).getParent.getParent) shouldBe originalB
      tree(shared) shouldBe baseline
    }
  }

  private object ConnectedFixture extends ThreeWorkflowManagedRepositoryFixture {
    override protected def standard_runtime = true
    private val connectedEdge = edge.copy(provider = edge.provider.copy(workspace = "managed-b"),
      consumer = edge.consumer.copy(repository = "sm-workflow", workspace = "sm-workflow-repository"))
    private val connectedDefinition = definition.copy(scope = "connected", edges = Vector(connectedEdge))
    private val connectedPolicy = policy.copy(definition = connectedDefinition, dependencyWorkEdge = Some(connectedEdge.identity))
    private val suite = "example.ProviderSpec"
    private def array(value: Json, name: String): Vector[Json] = value.hcursor.get[Vector[Json]](name).toOption.get
    private def remap(value: Json, workspace: String, prefix: String): Json = value.mapString { text =>
      if (text == TypedGitHostPlanFixture.workspace) workspace
      else if (text.startsWith("sm-workflow-git-")) prefix + text else text
    }.mapArray(_.map(v => remap(v, workspace, prefix))).mapObject(_.mapValues(v => remap(v, workspace, prefix)))

    override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
      val producer = root.getFileName.toString == "b"
      if (!Files.exists(root.resolve("repository/build.sbt"))) {
        prepareBuild(root.resolve("repository"), producer)
        Files.createDirectories(root.resolve("repository/src/test/scala"))
        Files.writeString(root.resolve("repository/src/test/scala/ProductCheck.scala"), "object ProductCheck\n")
        Files.writeString(root.resolve("repository/value.txt"), "fixture baseline\n")
        if (producer) {
          Files.writeString(root.resolve("repository/build.sbt"), Files.readString(root.resolve("repository/build.sbt")) +
            "libraryDependencies += \"org.scalatest\" %% \"scalatest\" % \"3.2.19\" % Test\n")
          Files.createDirectories(root.resolve("repository/src/test/scala/example"))
          Files.writeString(root.resolve("repository/src/test/scala/example/ProviderSpec.scala"),
            "package example\nimport org.scalatest.funsuite.AnyFunSuite\nfinal class ProviderSpec extends AnyFunSuite { test(\"accepted answer\") { assert(Provider.answer == 42) } }\n")
          if (scenario == "FailB") Files.writeString(root.resolve("repository/src/main/scala/example/Provider.scala"),
            "package example\nobject Provider { val answer: Int = \"selected B must fail\" }\n")
        }
      }
    }
    override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
      val original = super._setup_commands_(root, "Goal")
      original.patch(original.indexWhere(_.contains("commit")), Vector(Vector("-C", root.resolve("repository").toString,
        "add", "--", "src", "project/plugins.sbt")), 0)
    }
    override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
      val a = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString),
        goalPaths = goal.candidatePaths)
      val bRoot = root.resolve("b/repository")
      val originalB = remap(TypedGitHostPlanFixture.plan(Programs.Target(bRoot, "main", "origin", "main", root.resolve("b/upstream.git").toString),
        goalPaths = Vector("src/main/scala", "src/test/scala"), goalMessages = Vector("Managed B accepted source"),
        goalSelections = Vector(Vector("src/main/scala", "src/test/scala"))), "managed-b", "b-")
      val b = originalB.mapObject(_.add("gitPrograms", array(originalB, "gitPrograms")
        .map(p => if (p.hcursor.get[String]("selectionId").contains("goal-step-1")) p.mapObject(_.add("selectionId", "delegate-task".asJson)) else p).asJson))
      val validationRoot = if (scenario == "CanonicalAliases") root.resolve("canonical") else root.resolve("b")
      val compile = TypedSbtHostPlanFixture.plan(validationRoot, TypedSbtHostPlanFixture.normalExecutable, Vector(1), operation = SbtOperation.Compile)
      val focused = TypedSbtHostPlanFixture.plan(validationRoot, TypedSbtHostPlanFixture.normalExecutable, Vector(2),
        operation = SbtOperation.TestOnly, testClasses = Vector(suite))
      val template = array(compile, "sbtPrograms").head
      val artifacts = Vector("managed-b" -> "scoped-publish", "sm-workflow-repository" -> "compile").map { (workspace, operation) =>
        template.mapObject(_.add("target", workspace.asJson).add("operation", operation.asJson)
          .add("capability", (workspace + "-" + operation).asJson).add("identity", (workspace + "-" + operation).asJson))
      }
      val owner = Json.obj("identity" -> connectedDefinition.ownerWorkspace.asJson,
        "root" -> root.resolve(".textus/sm-workflow/work.d").toString.asJson)
      val other = Json.obj("identity" -> "other-consumer".asJson, "root" -> root.resolve("other/repository").toString.asJson)
      val capture = Json.obj("identity" -> "sm-workflow-validation-artifacts".asJson,
        "root" -> root.resolve("validation-captures").toString.asJson)
      val workspaces = array(a, "workspaces") ++ array(b, "workspaces") ++ array(compile, "workspaces") ++
        array(focused, "workspaces") ++ Vector(owner, other, capture)
      // V5 repository declarations own the ordinary Git family. Keep only the
      // explicitly selected programs; declaring both families for one target
      // would overlap the native repository expansion.
      val git = (array(a, "gitPrograms") ++ array(b, "gitPrograms"))
        .filter(_.hcursor.get[String]("selectionId").toOption.nonEmpty)
      val sbt = array(compile, "sbtPrograms") ++ array(focused, "sbtPrograms") ++ artifacts
      def repository(workspace: String, identity: String, endpoint: Path) = Json.obj("target" -> workspace.asJson,
        "capability" -> (workspace + "-git").asJson, "identity" -> identity.asJson,
        "gitExecutable" -> "/usr/bin/git".asJson, "localBranch" -> "main".asJson, "remote" -> "origin".asJson,
        "remoteBranch" -> "main".asJson, "endpoint" -> endpoint.toString.asJson,
        "commitIdentity" -> Json.obj("name" -> "Workflow Fixture".asJson, "email" -> "workflow@example.invalid".asJson),
        "limits" -> TypedGitHostPlanFixture.limits, "environment" -> Json.obj("PATH" -> "/usr/bin:/bin".asJson))
      val repositories = Vector(repository("sm-workflow-repository", "sm-workflow", root.resolve("upstream.git")),
        repository("managed-b", connectedEdge.provider.repository, root.resolve("b/upstream.git")))
      a.mapObject(_.add("schema", "cncf.runtime-process-execution.v5".asJson)
        .add("workspaces", workspaces.asJson).add("gitPrograms", git.asJson).add("sbtPrograms", sbt.asJson)
        .add("gitRepositories", repositories.asJson)
        .add("workspaceGrants", workspaces.map(w => Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
          "workspaceId" -> w.hcursor.get[String]("identity").toOption.get.asJson, "access" -> "manage".asJson)).asJson)
        .add("grants", Json.arr(Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
          "capabilities" -> (git ++ sbt ++ repositories).map(_.hcursor.get[String]("capability").toOption.get).distinct
            .filterNot(capability => scenario == "DirtyPostCommit" && capability == "managed-b-scoped-publish").asJson))))
    }

    def exercise(root: Path, mode: String = "Connected"): Unit = {
      Files.createDirectories(root.resolve("b/repository"))
      Files.createDirectories(root.resolve("other/repository"))
      Files.createDirectories(root.resolve(".textus/sm-workflow/work.d"))
      Files.createDirectories(root.resolve("validation-captures/validation"))
      if (mode == "CanonicalAliases") TypedSbtHostPlanFixture.build(root.resolve("canonical"), true)
      val storage = ThreeWorkflowNormalHostFixture.storagePlan.mapObject(fields => fields.add("records",
        (fields("records").get.asArray.get :+ Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
          "storeName" -> "workflow".asJson, "collection" -> collection.asJson)).asJson))
      val settings = ThreeWorkflowNormalHostFixture.definition(root)
      val selection = _goal_selection_(settings.goal.input, "connected-plan", "connected-start")
      val coding = GoalPhaseWorkClassification.Classification(GoalPhaseWorkClassification.Kind.CODING,
        GoalPhaseWorkClassification.Complexity.STANDARD, GoalPhaseWorkClassification.Domain.PROGRAMMING, GoalPhaseWorkClassification.Footprint.SMALL)
      val plan = GoalPhaseOrderedProgression.Plan("connected-plan", "1", settings.goal.input.phaseIdentity, "sm-workflow",
        Vector(GoalPhaseOrderedProgression.Step("step-1", "Document the consumer's verified dependency", Vector("docs/phase.md"), Vector.empty, Vector("compile"),
          workClassification = Some(GoalPhaseWorkClassification.Assignment(coding, coding, coding.copy(kind = GoalPhaseWorkClassification.Kind.REVIEW))))),
        Vector("compile"), repairLimit = 10, allowDependencyRepair = Option.when(mode == "PhasePolicy")(true))
      val selectedPolicy = if (mode == "Assigned" || mode.startsWith("AssignedPhase")) connectedPolicy.copy(delivery = Some(Vector(
        DependencyDevelopmentDelivery.Binding(connectedEdge.identity, DependencyDevelopmentDelivery.assignedChat,
          Some(DelegateTaskRequesterRoutingFixture.input("owner").delegation.destinationIdentity))))) else connectedPolicy
      // This provider Plan explicitly defers synchronization until scoped publication.
      val completionParameters = if (mode == "PhaseDelivery" || mode.startsWith("AssignedPhase")) Map(
        ThreeWorkflowRuntimeParameters.completionPolicy.name -> GoalPhasePostCompletionSync.localPolicy)
      else Map.empty[String, String]
      var reconnect = Option.empty[(V.DelegateTaskInput, String)]
      _with_runtime_(root, mode, Some(settings), BoundedWorkExecutionAdmissionFixture.parameters ++ Map(
        "sm-workflow.managed-development" -> selectedPolicy.asJson.noSpaces, "textus.subsystem.storage.plan" -> storage.noSpaces) ++ completionParameters) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(root, mode).size, scenario = mode).flatMap(_ =>
          call.setupC(_setup_commands_(root.resolve("b"), mode).size, Some(root.resolve("b")), mode)).flatMap(_ =>
          call.setupC(_setup_commands_(root.resolve("other"), mode).size, Some(root.resolve("other")), mode)).flatMap(_ =>
          call.createC("goal_selection", NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Goal(settings.goal.input), "participant"),
            Record.data(ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(selection))))).flatMap(_ =>
          call.createC("goal_plans", selection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
            take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(selection.selectionId, "participant", plan)))))))
        def invoke(service: String, operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = service, operation = operation, properties = properties), actor)
        def delegate(operation: String, properties: List[Property]) = read_value[V.DelegateTaskView](take(invoke("DelegateTaskService", operation, properties)))
        def advance(session: String) = delegate("advanceDelegateTask", List(Property("sessionId", session, None)))
        val a = read_value[V.GoalPhaseStartResult](take(invoke("GoalPhaseService", "startGoalPhase", NativeWorkflowOperationInputFixture.goal(settings.goal.input))))
        val original = read_value[V.GoalPhaseAdvanceResult](take(invoke("GoalPhaseService", "advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(a.sessionId))))
        var source = Option.empty[V.WorkflowArtifactReference]
        var actualGoal = Option.empty[GoalPhaseNativeWork.Request]
        prepare(call => call.goalWorkC(InstanceIdentity(a.sessionId)).map { work =>
          val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[GoalPhaseNativeWork.Request]]
          source = Some(V.WorkflowArtifactReference(order.request.continuationId.value, order.request.expectedRevision.value))
          actualGoal = order.request.input.map(_.value); ()
        })
        def input(role: String, request: String) = {
          val c = DelegateTaskRequesterRoutingFixture.input(request).delegation
          V.DelegateTaskInput(connectedEdge.provider.repository, request, "Verify the existing provider answer and deliver its exact accepted source",
            NonEmptyVector.of("src/main/scala", "src/test/scala"), NonEmptyVector.of("compile", "test-only"), NonEmptyVector.of("behavior"),
            NonEmptyVector.of(source.get), false, "code", "off", 10, "direct-instruction", Vector.empty,
            c.copy(componentIdentity = connectedEdge.provider.identity, role = role, exclusions = Vector("remote-push"),
              permittedOperations = if (mode == "Assigned" || mode.startsWith("AssignedPhase")) NonEmptyVector.fromVectorUnsafe(c.permittedOperations.toVector ++
                Vector("queue-hold", "queue-cancel", "queue-resume")) else if (mode == "Connected")
                NonEmptyVector.fromVectorUnsafe(c.permittedOperations.toVector :+ "repository-sync") else c.permittedOperations,
              synchronization = Option.when(mode == "Connected")(V.DelegateDeliverySynchronization(connectedEdge.provider.repository,
                "main", "origin", "main", root.resolve("b/upstream.git").toString, "FULL_STATE")),
              publicationTarget = Some(connectedEdge.identity), artifactCoordinates = Vector(connectedEdge.requested.organization + ":" +
                connectedEdge.requested.module + ":" + version(connectedEdge.requested.version, connectedDefinition.scope, request))), Vector("behavior"))
        }
        def start(value: V.DelegateTaskInput, suffix: String): Consequence[org.goldenport.protocol.operation.OperationResponse] = {
          val selected = "connected-" + suffix
          val prepared = take(NativeDelegateTaskStartInput.prepareC(Map("input" -> "selected.json", "selection-id" -> selected,
            "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> selected,
            "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => value.toRecord().toJsonStringC))
          prepare { call =>
            val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
            call.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
          }
          invoke("DelegateTaskService", "startDelegateTask", List(Property("input", value, None), Property("selectionId", selected, None),
            Property("participant", "participant", None), Property("idempotencyKey", selected, None)))
        }
        if (mode.startsWith("PhasePolicy")) {
          var activeWork = original
          if (mode == "PhasePolicyEnable") {
            actualGoal.get.selectedPlan.allowDependencyRepair shouldBe None
            actualGoal.get.selectedPlan.allowSharedCliUpdate shouldBe None
            val revised = plan.copy(revision = "2", allowDependencyRepair = Some(true))
            val proposed = GoalPhasePlanRevision.Proposal("phase-policy-enable", "1",
              "SPECIFICATION-OWNED direction to enable necessary external repair for this unchanged Phase purpose", revised)
            val result = V.GoalPhaseWorkResult("decision-needed", "SPECIFICATION-OWNED Phase permission direction",
              Vector.empty, None, None, None, GoalPhasePlanRevision.publicProposal(Some(proposed)), None, None, None, None,
              dependencyValidation = None, diagnosticRepair = None)
            val reference = V.WorkflowArtifactReference("phase-policy-enable-proposal", "1")
            read_value[V.GoalPhaseWorkSubmissionResult](take(invoke("GoalPhaseService", "submitGoalPhaseWorkResult", List(
              Property("sessionId", a.sessionId, None), Property("result", result, None), Property("issuedWorkContext", original.issuedWorkContext.get, None),
              Property("resultReference", reference, None), Property("executionEvidence", V.WorkflowExecutionEvidence(Vector(reference),
                Some("specification-enable-proposer"), None, Some(V.WorkflowExecutionObservation("INLINE",
                  BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity, BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity,
                  BoundedWorkExecutionAdmissionFixture.profile.identity))), None))))).accepted shouldBe true
            val question = read_value[V.GoalPhaseAdvanceResult](take(invoke("GoalPhaseService", "advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(a.sessionId))))
            question.kind shouldBe "Human"
            question.human.get.choices shouldBe Vector("AdoptPlanRevision", "RejectPlanRevision", "Defer", "Cancel")
            val answer = V.GoalPhaseOrderedDecisionAnswer("AdoptPlanRevision", "SPECIFICATION-OWNED independent Human direction; no real user consent claimed")
            val answerReference = V.WorkflowArtifactReference("phase-policy-human-enable", "1")
            val evidence = V.WorkflowExecutionEvidence(Vector(answerReference), Some("participant"), None, None)
            val properties = List(Property("sessionId", a.sessionId, None), Property("answer", answer, None),
              Property("resultReference", answerReference, None), Property("executionEvidence", evidence, None))
            invoke("GoalPhaseService", "submitGoalPhaseOrderedDecision", properties).isSuccess shouldBe false
            val stillPending = read_value[V.GoalPhaseAdvanceResult](take(invoke("GoalPhaseService", "advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(a.sessionId))))
            stillPending.human.get.work.selectedPlan.allowDependencyRepair shouldBe None
            prepare { call =>
              for {
                prepared <- org.simplemodeling.textus.smworkflow.preparation.NativeHumanDecisionPreparation.goalC(
                  question.human.get.humanDecisionContext.get, answer, answerReference, evidence, "participant", "normal-development-host",
                  call.executionContext.clock.instant().minusSeconds(60))
                _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
              } yield ()
            }
            read_value[V.GoalPhaseOrderedDecisionSubmissionResult](take(invoke("GoalPhaseService", "submitGoalPhaseOrderedDecision", properties))).accepted shouldBe true
            activeWork = read_value[V.GoalPhaseAdvanceResult](take(invoke("GoalPhaseService", "advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(a.sessionId))))
            activeWork.kind shouldBe "Request"
            prepare(call => call.goalWorkC(InstanceIdentity(a.sessionId)).map { work =>
              val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[GoalPhaseNativeWork.Request]]
              actualGoal = order.request.input.map(_.value)
              source = Some(V.WorkflowArtifactReference(order.request.continuationId.value, order.request.expectedRevision.value)); ()
            })
          }
          val actual = actualGoal.get
          actual.selectedPlan.allowDependencyRepair shouldBe Some(true)
          actual.selectedPlan.allowSharedCliUpdate shouldBe None
          val proposal = GoalPhaseDependencyScope.Proposal("external-local-repair", "1", "IMPLEMENTATION",
            connectedEdge.consumer, connectedEdge.provider.copy(identity = "org.example.External"),
            ContextReference("selected-usage", "1"), "Necessary provider correction for this Phase purpose",
            Vector("src/External.scala"), Vector("Preserve the required consumer API"), Vector(ContextReference(source.get.identity, source.get.revision)), Vector.empty,
            Some(GoalPhaseDependencyScope.AuthorizationBoundary(GoalPhaseDependencyScope.authorizationSchema,
              ContextReference(actual.selectedPlan.identity, actual.selectedPlan.revision),
              Vector("edit-source", "validate", "independent-review", "publish-local"), Vector.empty)))
          val result = V.GoalPhaseWorkResult("decision-needed", "SPECIFICATION-OWNED necessary external dependency proposal",
            Vector.empty, None, None, None, None, None, Some(GoalPhaseNativeMetadata.record(proposal.asJson)), None, None,
            dependencyValidation = None, diagnosticRepair = None)
          val reference = V.WorkflowArtifactReference("policy-semantic", "1")
          read_value[V.GoalPhaseWorkSubmissionResult](take(invoke("GoalPhaseService", "submitGoalPhaseWorkResult", List(
            Property("sessionId", a.sessionId, None), Property("result", result, None),
            Property("issuedWorkContext", activeWork.issuedWorkContext.get, None), Property("resultReference", reference, None),
            Property("executionEvidence", V.WorkflowExecutionEvidence(Vector(reference), Some("specification-policy-implementer"), None,
              Some(V.WorkflowExecutionObservation("INLINE", BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity,
                BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity, BoundedWorkExecutionAdmissionFixture.profile.identity))), None))))).accepted shouldBe true
          val admitted = read_value[V.GoalPhaseAdvanceResult](take(invoke("GoalPhaseService", "advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(a.sessionId))))
          val resumed = if (admitted.kind == "Request") admitted else read_value[V.GoalPhaseAdvanceResult](take(invoke(
            "GoalPhaseService", "advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(a.sessionId))))
          resumed.kind shouldBe "Request"
          resumed.sessionId shouldBe a.sessionId
          resumed.work.get.decisionContext.get.choice shouldBe GoalPhasePolicyDependencyContinuation.choice
          resumed.work.get.decisionContext.get.decisionReference.get.identity should include(":phase-policy-dependency:")
          resumed.work.get.selectedPlan.allowDependencyRepair shouldBe Some(true)
          resumed.work.get.selectedPlan.allowSharedCliUpdate shouldBe None
          resumed.work.get.originalBase shouldBe original.work.get.originalBase
          resumed.work.get.completed shouldBe original.work.get.completed
          resumed.repairProgress shouldBe original.repairProgress
          val reconnected = read_value[V.GoalPhaseAdvanceResult](take(invoke("GoalPhaseService", "advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(a.sessionId))))
          reconnected.issuedWorkContext.map(c => DevelopmentOperationalView.json(c.toRecord())) shouldBe
            resumed.issuedWorkContext.map(c => DevelopmentOperationalView.json(c.toRecord()))
        } else if (mode == "PhaseDelivery" || mode.startsWith("AssignedPhase")) {
          Given("an existing provider Phase and bounded requests share the configured assigned development chat")
          val boundedFirst = if (mode == "AssignedPhaseBoundedFirst") {
            val bounded = read_value[V.DelegateTaskView](take(start(input("destination", "phase-bounded-first"), "phase-bounded-first")))
            val work = advance(bounded.sessionId)
            work.kind shouldBe "Request"
            Some(bounded -> work)
          } else None
          val providerInput = V.GoalPhaseStartInput("provider-phase", connectedEdge.provider.repository)
          val providerPlan = plan.copy(identity = "existing-provider-phase", phase = providerInput.phaseIdentity,
            repository = providerInput.repositoryIdentity, steps = plan.steps.map(_.copy(identity = "provider-step",
              purpose = "Develop the provider through its existing Phase owner", ownedPaths = Vector("src/main/scala", "src/test/scala"),
              checks = Vector("compile", "test-only"))), deliverySynchronization = Some("after-scoped-publication"))
          val providerSelection = _goal_selection_(providerInput, providerPlan.identity, "provider-phase-start")
          prepare(call => call.createC("goal_selection", NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Goal(providerInput), "participant"),
            Record.data(ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(providerSelection))))
            .flatMap(_ => call.createC("goal_plans", providerSelection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
              take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(providerSelection.selectionId, "participant", providerPlan)))))))
          val provider = read_value[V.GoalPhaseStartResult](take(invoke("GoalPhaseService", "startGoalPhase", NativeWorkflowOperationInputFixture.goal(providerInput))))
          var providerSource = ""
          var originalProvider = ""
          prepare(call => call.goalCurrentC(InstanceIdentity(provider.sessionId)).map { current =>
            providerSource = current.revision.value.toString
            originalProvider = take(WorkflowInstanceRecordJsonV1.encodeC(current)); ()
          })
          val pending = read_value[V.GoalPhaseAdvanceResult](take(invoke("GoalPhaseService", "advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(provider.sessionId))))
          pending.kind shouldBe (if (boundedFirst.nonEmpty) "Wait" else "Request")
          if (boundedFirst.nonEmpty) prepare(call => call.goalCurrentC(InstanceIdentity(provider.sessionId)).map { current =>
            take(WorkflowInstanceRecordJsonV1.encodeC(current)) shouldBe originalProvider
            current.suspension shouldBe None; ()
          })
          if (mode.startsWith("AssignedPhase")) {
            When("a second genuine Phase and another bounded request are admitted to the same chat")
            val secondInput = providerInput.copy(phaseIdentity = "second-provider-phase")
            val secondPlan = providerPlan.copy(identity = "second-provider-plan", phase = secondInput.phaseIdentity)
            val secondSelection = _goal_selection_(secondInput, secondPlan.identity, "second-provider-start")
            prepare(call => call.createC("goal_selection", NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Goal(secondInput), "participant"),
              Record.data(ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(secondSelection))))
              .flatMap(_ => call.createC("goal_plans", secondSelection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
                take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(secondSelection.selectionId, "participant", secondPlan)))))))
            val second = read_value[V.GoalPhaseStartResult](take(invoke("GoalPhaseService", "startGoalPhase", NativeWorkflowOperationInputFixture.goal(secondInput))))
            val bounded = read_value[V.DelegateTaskView](take(start(input("destination", "phase-bounded-later"), "phase-bounded-later")))
            val secondWait = read_value[V.GoalPhaseAdvanceResult](take(invoke("GoalPhaseService", "advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(second.sessionId))))
            secondWait.kind shouldBe "Wait"
            advance(bounded.sessionId).kind shouldBe "Wait"
            Then("only the original FIFO owner can expose work and waiting does not alter either provider lifecycle")
            var secondRecord = ""
            prepare(call => call.goalCurrentC(InstanceIdentity(second.sessionId)).map { r =>
              secondRecord = take(WorkflowInstanceRecordJsonV1.encodeC(r)); r.suspension shouldBe None; ()
            })
            read_value[V.GoalPhaseAdvanceResult](take(invoke("GoalPhaseService", "advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(second.sessionId)))).kind shouldBe "Wait"
            prepare(call => call.goalCurrentC(InstanceIdentity(second.sessionId)).map { r =>
              take(WorkflowInstanceRecordJsonV1.encodeC(r)) shouldBe secondRecord; ()
            })
            boundedFirst.foreach { (first, work) =>
              val reference = V.WorkflowArtifactReference("phase-fifo-pause", "1")
              val result = V.DelegateTaskWorkResult("decision-needed", "SPECIFICATION-OWNED bounded direction is needed",
                Vector.empty, NonEmptyVector.of(V.DelegateTaskCondition("behavior", false, "Await explicit direction")),
                Vector("Choose provider behavior"), None, None)
              delegate("submitDelegateTaskWorkResult", List(Property("sessionId", first.sessionId, None), Property("result", result, None),
                Property("issuedWorkContext", work.issuedWorkContext.get, None), Property("resultReference", reference, None),
                Property("executionEvidence", V.WorkflowExecutionEvidence(Vector(reference), Some("specification-phase-fifo-implementer"), None,
                  Some(V.WorkflowExecutionObservation("INLINE", BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity,
                    BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity, BoundedWorkExecutionAdmissionFixture.profile.identity))), None)))
              advance(first.sessionId).kind shouldBe "Human"
              val hold = V.DelegateTaskObservationInput(first.sessionId, "queue-hold", input("destination", "phase-bounded-first").delegation.authorizationReferences.head,
                Some(first.sessionId), None, None, "SPECIFICATION-OWNED release by explicit original bounded control grant", Some("phase-fifo-hold"))
              take(invoke("DelegateTaskDeliveryService", "observeDelegateTaskDelivery", List(Property("input", hold, None))))
              read_value[V.GoalPhaseAdvanceResult](take(invoke("GoalPhaseService", "advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(provider.sessionId)))).kind shouldBe "Request"
              read_value[V.GoalPhaseAdvanceResult](take(invoke("GoalPhaseService", "advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(second.sessionId)))).kind shouldBe "Wait"
            }
          }
          val locator = V.DelegateProviderPhaseLocator(provider.sessionId, providerSource, "phase-publication", "phase-producer-selection")
          val requested = input("requester", "phase-delivery")
          val selectedRequest = requested.copy(delegation = requested.delegation.copy(providerPhase = Some(locator),
            artifactCoordinates = Vector(connectedEdge.requested.organization + ":" + connectedEdge.requested.module + ":" +
              version(connectedEdge.requested.version, connectedDefinition.scope, locator.publication))))
          val requester = read_value[V.DelegateTaskView](take(start(selectedRequest, "phase-delivery-requester")))
          val observation = V.DelegateTaskObservationInput(requester.sessionId, "dispatch-observed",
            V.WorkflowArtifactReference("actual-provider-phase-dispatch", "1"), Some(provider.sessionId), None, None,
            "SPECIFICATION-OWNED actual existing provider Phase locator; no substitute bounded Start", None)
          take(invoke("DelegateTaskDeliveryService", "observeDelegateTaskDelivery", List(Property("input", observation, None))))
          val waiting = advance(requester.sessionId)
          waiting.kind shouldBe "Wait"
          val retained = DelegateTaskRequesterRoutingFixture.state(waiting)
          retained.delivery.flatMap(_.phaseDelivery) shouldBe None
          retained.delivery.flatMap(_.returned) shouldBe None
          retained.automaticCount shouldBe 0
          val again = read_value[V.GoalPhaseAdvanceResult](take(invoke("GoalPhaseService", "advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(provider.sessionId))))
          if (boundedFirst.isEmpty) again.issuedWorkContext.map(c => DevelopmentOperationalView.json(c.toRecord())) shouldBe pending.issuedWorkContext.map(c => DevelopmentOperationalView.json(c.toRecord()))
          else again.kind shouldBe "Request"
          val publish = ManagedDevelopment.Input(connectedEdge.identity, "phase-publication", locator.publication, locator.producerSelection,
            providerPhase = Some(ProviderPhaseDelivery.Locator(locator.sessionId, locator.startRevision, locator.publication, locator.producerSelection)))
          invoke("ManagedDevelopmentService", "publishDevelopmentArtifact", List(Property("input", GoalPhaseNativeMetadata.record(publish.asJson), None))).isSuccess shouldBe false
          Files.exists(root.resolve(".textus/sm-workflow/work.d/artifacts")) shouldBe false
          if (mode.startsWith("AssignedPhase")) {
            val bounded = read_value[V.DelegateTaskView](take(start(input("destination", "after-failed-phase-publication"), "after-failed-phase-publication")))
            advance(bounded.sessionId).kind shouldBe "Wait"
            val unchanged = read_value[V.GoalPhaseAdvanceResult](take(invoke("GoalPhaseService", "advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(provider.sessionId))))
            unchanged.issuedWorkContext.map(c => DevelopmentOperationalView.json(c.toRecord())) shouldBe again.issuedWorkContext.map(c => DevelopmentOperationalView.json(c.toRecord()))
          }
        } else if (mode == "Assigned") {
          val firstInput = input("destination", "fifo-first")
          val secondInput = input("destination", "fifo-second")
          val first = read_value[V.DelegateTaskView](take(start(firstInput, "fifo-first")))
          val second = read_value[V.DelegateTaskView](take(start(secondInput, "fifo-second")))
          val work = advance(first.sessionId)
          work.kind shouldBe "Request"
          advance(second.sessionId).kind shouldBe "Wait"
          advance(first.sessionId).issuedWorkContext.map(c => DevelopmentOperationalView.json(c.toRecord())) shouldBe
            work.issuedWorkContext.map(c => DevelopmentOperationalView.json(c.toRecord()))
          def pause(session: String, pending: V.DelegateTaskView): Unit = {
            val reference = V.WorkflowArtifactReference("decision-" + session, "1")
            val result = V.DelegateTaskWorkResult("decision-needed", "SPECIFICATION-OWNED semantic uncertainty",
              Vector.empty, NonEmptyVector.of(V.DelegateTaskCondition("behavior", false, "Await bounded user direction")),
              Vector("Choose the required provider behavior"), None, None)
            delegate("submitDelegateTaskWorkResult", List(Property("sessionId", session, None), Property("result", result, None),
              Property("issuedWorkContext", pending.issuedWorkContext.get, None), Property("resultReference", reference, None),
              Property("executionEvidence", V.WorkflowExecutionEvidence(Vector(reference), Some("specification-fifo-implementer-" + session), None,
                Some(V.WorkflowExecutionObservation("INLINE", BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity,
                  BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity, BoundedWorkExecutionAdmissionFixture.profile.identity))), None)))
            advance(session).kind shouldBe "Human"
          }
          def command(session: String, event: String, identity: String) = V.DelegateTaskObservationInput(session, event,
            firstInput.delegation.authorizationReferences.head, Some(session), None, None,
            "SPECIFICATION-OWNED actual caller control under the original explicit grant", Some(identity))
          def control(value: V.DelegateTaskObservationInput): V.DelegateTaskView = read_value[V.DelegateTaskView](
            take(invoke("DelegateTaskDeliveryService", "observeDelegateTaskDelivery", List(Property("input", value, None)))))
          def queue(value: V.DelegateTaskView): AssignedDevelopmentChatQueue.State =
            DevelopmentOperationalView.json(value.state.get).hcursor.get[AssignedDevelopmentChatQueue.State]("assignedChatQueue").toOption.get
          pause(first.sessionId, work)
          advance(second.sessionId).kind shouldBe "Wait"
          Given("the first active request is held by command H1 under its original reusable authorization")
          val h1 = command(first.sessionId, "queue-hold", "fifo-hold-1")
          control(h1)
          val secondWork = advance(second.sessionId)
          secondWork.kind shouldBe "Request"
          pause(second.sessionId, secondWork)
          control(command(second.sessionId, "queue-cancel", "fifo-cancel-second"))
          val r1 = command(first.sessionId, "queue-resume", "fifo-resume-1")
          val resumed = queue(control(r1))
          When("the original H1 is redelivered after R1")
          queue(control(h1)) shouldBe resumed
          queue(control(r1)) shouldBe resumed
          Then("the retained command outcomes preserve the later direction and a new command can still use the same grant")
          val h2 = h1.copy(commandIdentity = Some("fifo-hold-2"))
          val heldAgain = queue(control(h2))
          heldAgain.entries.find(_.session == first.sessionId).get.status shouldBe "held"
          queue(control(r1)) shouldBe heldAgain
          invoke("DelegateTaskDeliveryService", "observeDelegateTaskDelivery", List(Property("input", h1.copy(event = "queue-cancel"), None))).isSuccess shouldBe false
          invoke("DelegateTaskDeliveryService", "observeDelegateTaskDelivery", List(Property("input", h1.copy(commandIdentity = None), None))).isSuccess shouldBe false
          control(r1.copy(commandIdentity = Some("fifo-resume-2")))
          reconnect = Some(firstInput -> first.sessionId)
          advance(first.sessionId).kind shouldBe "Human"
          advance(second.sessionId).kind shouldBe "Wait"
          DelegateTaskRequesterRoutingFixture.state(advance(first.sessionId)).automaticCount shouldBe 0
        } else if (mode == "CanonicalAliases") {
          prepare(call => WorkflowManagedProcessExecution.repositoryValidationAttemptC(call.core, 1, WorkflowSbtValidation.Kind.Compile).map { attempt =>
            attempt match {
              case org.goldenport.cncf.processexecution.SbtExecutionAttempt.Observed(result) => result.outcome shouldBe org.goldenport.cncf.processexecution.SbtExecutionOutcome.Succeeded
              case other => fail("Canonical compile must actually pass: " + other)
            }; ()
          })
          start(input("destination", "wrong-aliases"), "wrong-aliases").isSuccess shouldBe false
        } else {
          prepare { call =>
            val h = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
              connectedEdge.provider.repository, root.resolve("b/repository").toString, "main", Vector("src/main/scala", "src/test/scala"),
              Vector.empty, None, None, None, None, None, managedDevelopment = Some(connectedPolicy))
            val crossedEdge = connectedEdge.copy(consumer = connectedEdge.consumer.copy(workspace = "other-consumer"))
            val crossed = h.copy(managedDevelopment = Some(connectedPolicy.copy(definition = connectedDefinition.copy(edges = Vector(crossedEdge)))))
            val actual = take(DelegateTask.inputC(input("destination", "connected-delivery")))
            for {
              captured <- ManagedDevelopmentBoundedWork.captureC(h, actual, call.core)
              _ = captured.get.session shouldBe a.sessionId
              _ = ManagedDevelopmentBoundedWork.captureC(crossed, actual, call.core).isSuccess shouldBe false
              _ = ManagedDevelopmentBoundedWork.requesterC(h, captured.get.copy(edge = crossedEdge), call.core).isSuccess shouldBe false
            } yield ()
          }
          val requester = read_value[V.DelegateTaskView](take(start(input("requester", "connected-delivery"), "requester")))
          val destination = read_value[V.DelegateTaskView](take(start(input("destination", "connected-delivery"), "destination")))
          def observe(session: String, event: String, artifact: Option[Record] = None): V.DelegateTaskView =
            read_value[V.DelegateTaskView](take(invoke("DelegateTaskDeliveryService", "observeDelegateTaskDelivery", List(Property("input",
              V.DelegateTaskObservationInput(session, event, V.WorkflowArtifactReference("connected-" + event + "-" + session, "1"),
                Some(destination.sessionId), None, artifact, "SPECIFICATION-OWNED caller observation", None), None)))))
          observe(requester.sessionId, "dispatch-observed")
          val work = advance(destination.sessionId)
          work.kind shouldBe "Request"
          val semantic = V.DelegateTaskWorkResult("completed", "SPECIFICATION-OWNED existing answer retained for actual native checks",
            Vector.empty, NonEmptyVector.of(V.DelegateTaskCondition("behavior", true, "The assigned existing answer is retained")), Vector.empty, None, None)
          delegate("submitDelegateTaskWorkResult", List(Property("sessionId", destination.sessionId, None), Property("result", semantic, None),
            Property("issuedWorkContext", work.issuedWorkContext.get, None), Property("resultReference", V.WorkflowArtifactReference("connected-semantic", "1"), None),
            Property("executionEvidence", V.WorkflowExecutionEvidence(Vector(V.WorkflowArtifactReference("connected-semantic", "1")), Some("specification-implementer"), None,
              Some(V.WorkflowExecutionObservation("INLINE", BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity,
                BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity, BoundedWorkExecutionAdmissionFixture.profile.identity))), None)))
          val delivered = advance(destination.sessionId)
          val bState = DelegateTaskRequesterRoutingFixture.state(delivered)
          if (mode == "Connected") {
            bState.delivery.flatMap(_.managedPublication) should not be empty
            bState.delivery.flatMap(_.synchronization).map(_.state) shouldBe Some("deferred")
            bState.delivery.flatMap(_.synchronization).flatMap(_.childSessionId) shouldBe None
          }
          if (mode == "FailB") {
            bState.receipts.exists(r => r.identity == "compile" && r.exitCode.contains(1)) shouldBe true
            bState.commit shouldBe None
            bState.delivery.flatMap(_.managedPublication) shouldBe None
            advance(requester.sessionId).kind shouldBe "Wait"
            Files.exists(root.resolve(".textus/sm-workflow/work.d/artifacts")) shouldBe false
          } else if (mode == "DirtyPostCommit") {
            delivered.kind shouldBe "Wait"
            bState.stage shouldBe "PublicationWait"
            bState.commit should not be empty
            bState.receipts.forall(_.exitCode.contains(0)) shouldBe true
            val sourcePath = root.resolve("b/repository/src/main/scala/example/Provider.scala")
            val originalSource = Files.readString(sourcePath)
            val extraPath = sourcePath.resolveSibling("Unaccepted.scala")
            Vector(sourcePath -> "package example\nobject Provider { def answer: Int = 99 }\n",
              extraPath -> "package example\nobject Unaccepted\n").foreach { (path, text) =>
              Files.writeString(path, text)
              invoke("DelegateTaskService", "advanceDelegateTask", List(Property("sessionId", destination.sessionId, None))).isSuccess shouldBe false
              Files.readString(path) shouldBe text
              if (path == sourcePath) Files.writeString(sourcePath, originalSource) else Files.delete(extraPath)
            }
            val operationInput = Input(connectedEdge.identity, "connected-delivery", "connected-delivery", "connected-delivery")
            invoke("ManagedDevelopmentService", "selectDevelopmentArtifact", List(Property("input", GoalPhaseNativeMetadata.record(operationInput.asJson), None))).isSuccess shouldBe false
            advance(requester.sessionId).kind shouldBe "Wait"
          } else {
            delivered.kind shouldBe "Return"
            val publication = bState.delivery.flatMap(_.managedPublication).get
            ManagedDevelopmentBoundedWork.acceptedPublicationSource(publication.source, bState.commit.get) shouldBe true
            val received = advance(requester.sessionId)
            DelegateTaskRequesterRoutingFixture.state(received).stage shouldBe "ResultReceived"
            val operationInput = Input(connectedEdge.identity, "connected-delivery", "connected-delivery", "connected-delivery")
            val selectionIdentity: String = ManagedDevelopment.key(connectedDefinition, connectedEdge, "selection", operationInput.selection)
            val artifact = Record.data("sourceCommit" -> bState.commit.get.identity, "coordinates" -> input("requester", "connected-delivery").delegation.artifactCoordinates,
              "selectionReference" -> Record.data("identity" -> selectionIdentity, "revision" -> "1"),
              "consumerCheckReferences" -> Vector.empty[Record])
            val adoption = V.DelegateTaskObservationInput(requester.sessionId, "adopt", V.WorkflowArtifactReference("connected-adopt-" + requester.sessionId, "1"),
              Some(destination.sessionId), None, Some(artifact), "SPECIFICATION-OWNED caller observation", None)
            val context = read_value[V.DelegateTaskAdoptionContext](take(invoke("DelegateTaskDeliveryService", "describeDelegateTaskAdoption",
              List(Property("sessionId", requester.sessionId, None)))))
            // Keep preparation failures attributable to their actual boundary.
            // Do not normalize or replace the public target/selection to make
            // independently admitted adoption evidence appear valid.
            def assertRecordJson(value: Any, path: String): Unit = value match {
              case record: Record =>
                withClue("Connected adoption Record keys at " + path + ": ") {
                  record.fields.map(_.key).distinct.size shouldBe record.fields.size
                }
                record.fields.foreach(field => assertRecordJson(field.value.single, path + "." + field.key))
                withClue("Connected adoption Record serialization at " + path + ": ") {
                  record.toJsonStringC.isSuccess shouldBe true
                }
              case values: Seq[?] => values.zipWithIndex.foreach { (member, index) =>
                assertRecordJson(member, path + "[" + index + "]")
              }
              case other =>
                val actualType = Option(other).map(_.getClass.getName).getOrElse("null")
                withClue("Connected adoption leaf at " + path + " (" + actualType + "): ") {
                  Record.data("value" -> other).toJsonStringC.isSuccess shouldBe true
                }
            }
            assertRecordJson(context.target, "context.target")
            assertRecordJson(artifact, "artifactSelection")
            prepare(call => for {
              prepared <- NativeDelegateTaskAdoptionPreparation.prepareC(context, adoption, adoption.sourceReference,
                V.WorkflowExecutionEvidence(Vector(adoption.sourceReference), Some("participant"), None, None), "normal-development-host", call.executionContext.clock.instant().minusSeconds(1))
                .recoverWith(error => Consequence.stateConflict("Connected native adoption preparation failed before host-evidence write: " + error.print))
              _ = assertRecordJson(prepared.record, "prepared.hostEvidence")
              _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
                .recoverWith(error => Consequence.stateConflict("Connected native adoption host-evidence write failed: " + error.print))
            } yield ())
            val before = tree(root.resolve(".textus/sm-workflow/work.d"))
            val adopted = observe(requester.sessionId, "adopt", Some(artifact))
            tree(root.resolve(".textus/sm-workflow/work.d")) shouldBe before
            adopted.kind shouldBe "Wait"
            def requesterRecord(): InstanceRecord = {
              var actual = Option.empty[InstanceRecord]
              prepare(call => call.delegateCurrentC(InstanceIdentity(requester.sessionId)).map { current => actual = Some(current); () })
              actual.get
            }
            // Reopening admits a fresh DefinitionBinding object. Compare the
            // complete persisted record, including its definition reference and
            // history, rather than that object's reference identity.
            def persisted(record: InstanceRecord): String = take(WorkflowInstanceRecordJsonV1.encodeC(record))
            def publicJson(record: Option[Record]): Option[Json] = record.map(DevelopmentOperationalView.json)
            def contextJson(context: Option[V.WorkflowIssuedWorkContext]): Option[Json] = publicJson(context.map(_.toRecord()))
            DelegateTaskRequesterRoutingFixture.state(adopted).stage shouldBe "Adopted"
            val adoptedRecord = requesterRecord()
            adoptedRecord.lifecycle shouldBe Lifecycle.Active
            val waiting = advance(requester.sessionId)
            waiting.kind shouldBe "Wait"
            val waitingState = DelegateTaskRequesterRoutingFixture.state(waiting)
            waitingState.delivery.flatMap(_.managedConsumer).map(_.status) shouldBe Some("published")
            waitingState.delivery.flatMap(_.managedContinuation).map(_.session) shouldBe Some(a.sessionId)
            val waitingRecord = requesterRecord()
            waitingRecord.lifecycle shouldBe Lifecycle.Active
            waitingRecord.revision shouldBe adoptedRecord.revision.next
            waitingRecord.history.dropRight(1) shouldBe adoptedRecord.history
            // Public state is read back through its actual retained snapshot.
            // Reobserving unchanged integration must not append another entry.
            val waitingAgain = advance(requester.sessionId)
            publicJson(waitingAgain.state) shouldBe publicJson(waiting.state)
            persisted(requesterRecord()) shouldBe persisted(waitingRecord)
            def artifactCall(operation: String) = read_control_value[V.ManagedDevelopmentResult](take(invoke("ManagedDevelopmentService", operation,
              List(Property("input", GoalPhaseNativeMetadata.record(operationInput.asJson), None)))))
            artifactCall("selectDevelopmentArtifact").status shouldBe "selected-unverified"
            advance(requester.sessionId).kind shouldBe "Wait"
            artifactCall("compileDevelopmentConsumer").status shouldBe "verified"
            Files.writeString(root.resolve("repository/value.txt"), "source changed after consumer verification\n")
            invoke("DelegateTaskService", "advanceDelegateTask", List(Property("sessionId", requester.sessionId, None))).isSuccess shouldBe false
            Files.writeString(root.resolve("repository/value.txt"), "fixture baseline\n")
            val resumed = advance(requester.sessionId)
            resumed.kind shouldBe "Request"
            val resumedRecord = requesterRecord()
            resumedRecord.lifecycle shouldBe Lifecycle.Active
            // A's retained Request has A's work/context, not B's bounded state.
            // Reobserve the identical admitted adoption to qualify its passive
            // redisplay. Read an exact public snapshot only when the response
            // supplies a retained reference instead of inline state.
            def requesterState() = {
              val control = read_control_value[V.DelegateTaskView](take(invoke("DelegateTaskDeliveryService", "observeDelegateTaskDelivery",
                List(Property("input", adoption, None)))))
              control.kind shouldBe "Wait"
              control.sessionId shouldBe requester.sessionId
              val state = control.state.getOrElse {
                val reference = control.retainedMaterials.find(_.fieldName == "state").get.reference
                reference.sessionId shouldBe requester.sessionId
                reference.owner shouldBe "DelegateTaskWorkflow"
                val detail = read_control_value[V.WorkflowRetainedSnapshotReadResult](take(invoke(WorkflowRetainedWork.serviceName,
                  WorkflowRetainedSnapshot.readOperation, List(Property("input", reference, None)))))
                detail.availability shouldBe "available"
                detail.reference shouldBe reference
                detail.material
              }
              persisted(requesterRecord()) shouldBe persisted(resumedRecord)
              DelegateTaskRequesterRoutingFixture.state(control.copy(state = Some(state)))
            }
            val resumedState = requesterState()
            resumedState.stage shouldBe "Adopted"
            resumedState.delivery.flatMap(_.managedConsumer).map(_.status) shouldBe Some("verified")
            resumedState.delivery.flatMap(_.managedContinuation).map(_.session) shouldBe Some(a.sessionId)
            resumed.submitSessionId shouldBe Some(a.sessionId)
            resumed.submitOperation shouldBe original.submitOperation
            resumed.resultType shouldBe Some("GoalPhaseWorkResult")
            contextJson(resumed.issuedWorkContext) shouldBe contextJson(original.issuedWorkContext)
            publicJson(resumed.work) shouldBe publicJson(original.work.map(_.toRecord()))
            val resumedAgain = advance(requester.sessionId)
            contextJson(resumedAgain.issuedWorkContext) shouldBe contextJson(resumed.issuedWorkContext)
            resumedAgain.submitSessionId shouldBe resumed.submitSessionId
            resumedAgain.submitOperation shouldBe resumed.submitOperation
            resumedAgain.resultType shouldBe resumed.resultType
            publicJson(resumedAgain.work) shouldBe publicJson(resumed.work)
            persisted(requesterRecord()) shouldBe persisted(resumedRecord)
            requesterState() shouldBe resumedState
            val deferred = read_value[V.DelegateTaskView](take(start(input("requester", "deferred-delivery"), "deferred")))
            observe(deferred.sessionId, "dispatch-observed")
            observe(deferred.sessionId, "defer")
            advance(deferred.sessionId).kind shouldBe "Wait"
            DelegateTaskRequesterRoutingFixture.state(advance(deferred.sessionId)).stage shouldBe "AwaitResult"
            Files.createDirectories(root.resolve("repository/docs"))
            Files.writeString(root.resolve("repository/docs/phase.md"), "# Verified dependency\nThe explicit scoped provider compiles in this consumer.\n")
            // This is the genuine fixture's original A semantic result. No A acceptance,
            // validation receipt or new Start is synthesized from B's delivery.
            val aResult = V.GoalPhaseWorkResult("completed", "SPECIFICATION-OWNED original consumer documentation authored after real selected compilation",
              Vector.empty, None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
            val completed = read_value[V.GoalPhaseWorkSubmissionResult](take(invoke("GoalPhaseService", resumed.submitOperation.get,
              List(Property("sessionId", resumed.submitSessionId.get, None), Property("result", aResult, None),
                Property("issuedWorkContext", resumed.issuedWorkContext.get, None), Property("resultReference", V.WorkflowArtifactReference("connected-a-semantic", "1"), None),
                Property("executionEvidence", V.WorkflowExecutionEvidence(Vector(V.WorkflowArtifactReference("connected-a-semantic", "1")), Some("specification-a-implementer"), None,
                  Some(V.WorkflowExecutionObservation("INLINE", BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity,
                    BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity, BoundedWorkExecutionAdmissionFixture.profile.identity))), None)))))
            completed.accepted shouldBe true
            invoke("DelegateTaskService", "advanceDelegateTask", List(Property("sessionId", requester.sessionId, None))).isSuccess shouldBe false
          }
        }
        if (mode != "Connected" && !mode.startsWith("PhasePolicy")) {
          val retainedA = read_value[V.GoalPhaseAdvanceResult](take(invoke("GoalPhaseService", "advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(a.sessionId))))
          retainedA.issuedWorkContext.map(value => DevelopmentOperationalView.json(value.toRecord())) shouldBe
            original.issuedWorkContext.map(value => DevelopmentOperationalView.json(value.toRecord()))
        }
      }
      if (mode == "Assigned") {
        val (originalInput, session) = reconnect.get
        Vector(DependencyDevelopmentDelivery.Binding(connectedEdge.identity, DependencyDevelopmentDelivery.fixed),
          DependencyDevelopmentDelivery.Binding(connectedEdge.identity, DependencyDevelopmentDelivery.assignedChat, Some("new-development-chat"))).foreach { binding =>
          val changedPolicy = connectedPolicy.copy(delivery = Some(Vector(binding)))
          _with_runtime_(root, mode, Some(settings), BoundedWorkExecutionAdmissionFixture.parameters ++ Map(
            "sm-workflow.managed-development" -> changedPolicy.asJson.noSpaces, "textus.subsystem.storage.plan" -> storage.noSpaces)) { (owner, component, actor, prepare) =>
            def invoke(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
              Request.of(component = component.name, service = "DelegateTaskService", operation = operation, properties = properties), actor)
            def current(): String = {
              var value = ""
              prepare(call => call.delegateCurrentC(InstanceIdentity(session)).map(r => { value = take(WorkflowInstanceRecordJsonV1.encodeC(r)); () }))
              value
            }
            val before = current()
            Given("a retained AssignedChat request while today's configuration selects " + binding.mode + binding.chat.getOrElse(""))
            val properties = List(Property("input", originalInput, None), Property("sessionId", session, None),
              Property("selectionId", "connected-fifo-first", None), Property("participant", "participant", None),
              Property("idempotencyKey", "connected-fifo-first", None))
            When("the original complete Start is redelivered with its actual native Session")
            val retained = read_value[V.DelegateTaskView](take(invoke("startDelegateTask", properties)))
            retained.sessionId shouldBe session
            current() shouldBe before
            Then("the original queue/mode/frontier and accepted history remain, while changed bodies and keys refuse")
            invoke("startDelegateTask", properties.map(p => if (p.name == "input") Property("input", originalInput.copy(objective = "changed request"), None) else p)).isSuccess shouldBe false
            invoke("startDelegateTask", properties.map(p => if (p.name == "idempotencyKey") Property("idempotencyKey", "changed-key", None) else p)).isSuccess shouldBe false
            val observed = read_value[V.DelegateTaskView](take(invoke("advanceDelegateTask", List(Property("sessionId", session, None)))))
            observed.kind shouldBe "Human"
            observed.message should not include "dependency-change-required"
            DelegateTaskRequesterRoutingFixture.state(observed).automaticCount shouldBe 0
            val newRequest = "new-" + binding.mode
            val newInput = originalInput.copy(requestIdentity = newRequest,
              delegation = originalInput.delegation.copy(destinationIdentity = binding.chat.getOrElse(originalInput.delegation.destinationIdentity),
                artifactCoordinates = Vector(connectedEdge.requested.organization + ":" + connectedEdge.requested.module + ":" +
                  version(connectedEdge.requested.version, connectedDefinition.scope, newRequest))))
            val selected = "changed-mode-new-" + binding.mode
            val prepared = take(NativeDelegateTaskStartInput.prepareC(Map("input" -> "new.json", "selection-id" -> selected,
              "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> selected,
              "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => newInput.toRecord().toJsonStringC))
            prepare { call =>
              val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
              call.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
            }
            val newStart = invoke("startDelegateTask", List(Property("input", newInput, None),
              Property("selectionId", selected, None), Property("participant", "participant", None), Property("idempotencyKey", selected, None)))
            if (binding.mode == DependencyDevelopmentDelivery.fixed) newStart.isSuccess shouldBe false
            else {
              val fresh = read_value[V.DelegateTaskView](take(newStart))
              read_value[V.DelegateTaskView](take(invoke("advanceDelegateTask", List(Property("sessionId", fresh.sessionId, None))))).kind shouldBe "Request"
              // A different configured chat remains independent of the retained active request.
              fresh.sessionId should not be session
            }
          }
        }
      }
    }
  }

  /** Three actual Git source worktrees, two explicit provider edges, local bare
    * remotes and no SBT/publication binding. The changed-input arm deliberately
    * retains the missing consumer-validation subject instead of accepting it. */
  private object WorkspaceSyncFixture extends Phase69ConfiguredRepositoryFixture {
    private def source(root: Path, ordinal: Int) = root.resolve("source-" + ordinal)
    private def workspace(ordinal: Int) = "p7-workspace-" + ordinal
    private def identity(ordinal: Int) = "p7-repository-" + ordinal
    private def component(ordinal: Int) = GoalPhaseDependencyScope.Component("org.example.Workspace" + ordinal,
      identity(ordinal), workspace(ordinal))
    private val edges = Vector(1, 2).map(ordinal => edge.copy(identity = "workspace-edge-" + ordinal,
      provider = component(ordinal), consumer = component(0)))
    private val managed = policy.copy(definition = definition.copy(edges = edges))

    override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
      val template = Phase69ConfiguredRepositoryFixture.withoutManagedCandidates(super.process_plan(root, scenario, goal))
      val original = template.hcursor.get[Vector[Json]]("gitRepositories").toOption.get.head
      val repositories = Vector(0, 1, 2).map(ordinal => original.mapObject(_.add("target", workspace(ordinal).asJson)
        .add("identity", identity(ordinal).asJson).add("capability", identity(ordinal).asJson)
        .add("localBranch", ("work/p7-" + ordinal).asJson).add("remoteBranch", "release".asJson)
        .add("endpoint", source(root, ordinal).resolve("upstream.git").toString.asJson)))
      template.mapObject(_.add("gitRepositories", repositories.asJson)
        .add("workspaces", Vector(0, 1, 2).map(ordinal => Json.obj("identity" -> workspace(ordinal).asJson,
          "root" -> source(root, ordinal).resolve("repository").toString.asJson)).asJson)
        .add("workspaceGrants", Vector(0, 1, 2).map(ordinal => Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
          "workspaceId" -> workspace(ordinal).asJson, "access" -> "manage".asJson)).asJson)
        .add("grants", Json.arr(Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
          "capabilities" -> Vector(0, 1, 2).map(ordinal => identity(ordinal)).asJson))))
    }

    def exercise(root: Path, scenario: String): Unit = {
      val directories = Vector(0, 1, 2).map(ordinal => source(root, ordinal).resolve("repository").toString)
      directories.foreach(path => Files.createDirectories(Path.of(path)))
      val parameters = Map(RepositorySyncDirectoryList.declaration.name -> directories.asJson.noSpaces,
        ManagedDevelopmentRuntime.declaration.name -> managed.asJson.noSpaces,
        // This fixture's text file has no build consumer. Declare that host
        // fact explicitly: a docs/ path alone does not establish applicability.
        RepositorySyncDocumentInputs.declaration.name -> Json.obj(
          "documentPaths" -> Vector("docs/change.md").asJson,
          "consumedPaths" -> Vector("src", "build.sbt", "project").asJson,
          "reason" -> "The controlled Git-only fixture never consumes docs/change.md as a build input; immutable consumer selections remain unavailable".asJson).noSpaces)
      var result = Json.Null
      var session = ""
      var revision = ""
      def json(value: org.goldenport.protocol.operation.OperationResponse): Json = value match {
        case org.goldenport.protocol.operation.OperationResponse.RecordResponse(record) => DevelopmentOperationalView.json(record)
        case other => fail("Expected native workspace batch: " + other)
      }
      _with_runtime_(root, "Equal", hostParameters = parameters) { (owner, component, actor, prepare) =>
        prepare { _ => Vector(0, 1, 2).foldLeft(Consequence.unit) { (prior, ordinal) =>
          val directory = source(root, ordinal)
          val selected = if (ordinal == 1 && scenario == "Conflict") "Conflict" else "Equal"
          val commands = if (ordinal == 1 && scenario == "ChangedDependency") {
            _setup_commands_(directory, "Equal") ++ Vector(
              Vector("clone", "--no-local", directory.resolve("upstream.git").toString, directory.resolve("peer").toString),
              Vector("-C", directory.resolve("peer").toString, "add", "--", "docs/change.md"),
              Vector("-C", directory.resolve("peer").toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-m", "non-consumed remote documentation"),
              Vector("-C", directory.resolve("peer").toString, "push", directory.resolve("upstream.git").toString, "HEAD:refs/heads/main"))
          } else _setup_commands_(directory, selected)
          commands.zipWithIndex.foldLeft(prior) { case (previous, (command, index)) => previous.flatMap { _ =>
            _setup_files_(directory, selected, index)
            if (ordinal == 1 && scenario == "ChangedDependency" && index == 5) {
              Files.createDirectories(directory.resolve("peer/docs"))
              Files.writeString(directory.resolve("peer/docs/change.md"), "Unconsumed documentation does not prove changed dependency input.\n")
            }
            _run_setup_command_(command.map {
              case "--initial-branch=main" => if (command.contains("--bare")) "--initial-branch=release" else "--initial-branch=work/p7-" + ordinal
              case argument => argument.replace("refs/heads/main", "refs/heads/release")
            })
          }}
        }}
        def invoke(operation: String, properties: List[Property]) = json(take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "RepositorySyncService", operation = operation, properties = properties), actor)))
        Given("one registered consumer and two selected provider sources, with the consumer listed first")
        result = invoke(RepositorySyncDirectoryList.syncOperation, List(Property("input", Record.data("syncMode" -> "FULL_STATE"), None)))
        session = result.hcursor.get[String]("sessionId").toOption.get
        revision = result.hcursor.downField("resultReference").get[String]("revision").toOption.get
        val rows = result.hcursor.get[Vector[Json]]("repositories").toOption.get
        Then("native result identities retain provider order, actual conflicts and the independent provider result")
        withClue("Actual native workspace batch: " + result.spaces2 + "\n") {
          rows.take(2).map(_.hcursor.get[String]("repositoryIdentity").toOption.get) shouldBe Vector(identity(1), identity(2))
          rows(1).hcursor.get[String]("kind").toOption shouldBe Some("Terminal")
          rows(1).hcursor.get[String]("outcome").toOption shouldBe Some("converged")
          result.hcursor.get[String]("outcome").toOption shouldBe Some("AttentionRequired")
          result.hcursor.get[Vector[String]]("notStarted").toOption shouldBe Some(Vector(identity(0)))
          val diagnostic = result.hcursor.downField("configuration").get[String]("diagnostic").toOption.get
          diagnostic should include(if (scenario == "Conflict") "DependencyValidationPending" else "DependencyIntegrationApplicabilityUnknown")
          if (scenario == "Conflict") rows.head.hcursor.get[String]("kind").toOption should not be Some("Terminal")
          else {
            rows.head.hcursor.get[String]("kind").toOption shouldBe Some("Terminal")
            rows.head.hcursor.get[String]("outcome").toOption shouldBe Some("converged")
          }
        }
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        Files.exists(root.resolve(".textus/sm-workflow/work.d/artifacts")) shouldBe false
        invoke(RepositorySyncDirectoryList.syncOperation, List(Property("sessionId", session, None),
          Property("input", Record.data("syncMode" -> "FULL_STATE"), None))) shouldBe result
      }
      _with_runtime_(root, "Equal", hostParameters = parameters) { (owner, component, actor, _) =>
        val retained = json(take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "RepositorySyncService", operation = RepositorySyncDirectoryList.readOperation,
          properties = List(Property("sessionId", session, None), Property("revision", revision, None))), actor)))
        retained shouldBe result
      }
    }
  }

  /** Actual DirectoryList composition, existing shared engine and normal SBT.
    * The two source generators coordinate only inside this private fixture;
    * their bounded rendezvous fails when the required checks do not overlap.
    */
  private object WorkspaceProductFixture extends Phase69ConfiguredRepositoryFixture {
    import org.goldenport.cncf.processexecution.*
    import org.goldenport.cncf.workspace.WorkspaceId
    import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M}
    import GoalPhaseNativeMetadata.given
    private def source(root: Path, n: Int) = root.resolve("source-" + n)
    private def workspace(n: Int) = "p7-product-workspace-" + n
    private def identity(n: Int) = "p7-product-repository-" + n
    private def component(n: Int) = GoalPhaseDependencyScope.Component("org.example.Product" + n, identity(n), workspace(n))
    private val selectedEdge = edge.copy(identity = "product-provider", provider = component(1), consumer = component(0),
      requested = edge.requested.copy(module = "provider_3"))
    private val orderingEdge = selectedEdge.copy(identity = "independent-provider", provider = component(2), localJar = false)
    private val selectedDefinition = definition.copy(scope = "native-products", edges = Vector(selectedEdge, orderingEdge))
    private val publicationInput = input.copy(edgeIdentity = selectedEdge.identity)
    private val consumerInput = publicationInput.copy(requestIdentity = "native-product-consumer", selection = "native-product-selection")
    private val selectedPolicy = policy.copy(definition = selectedDefinition, repositorySyncInputs = Some(Vector(consumerInput)))
    private val productCompile = RepositorySyncNativeMergeValidation.Check("product-compile", 1, WorkflowSbtValidation.Kind.Compile)
    private val focusedSource = org.simplemodeling.textus.smworkflow.admission.phase7workspacefixture.Phase7WorkspaceConsumerFixtureSource
    private def compiledProducts(root: Path) = source(root, 0).resolve("repository/target/native-consumer-test-classes")
    override protected def realtime_scheduler = true
    private def array(value: Json, key: String) = value.hcursor.get[Vector[Json]](key).toOption.get

    override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
      val base = Phase69ConfiguredRepositoryFixture.withoutManagedCandidates(super.process_plan(root, scenario, goal))
      val repository = array(base, "gitRepositories").head
      val repositories = Vector(0, 1, 2).map(n => repository.mapObject(_.add("target", workspace(n).asJson)
        .add("identity", identity(n).asJson).add("capability", (workspace(n) + "-git").asJson)
        .add("endpoint", source(root, n).resolve("upstream.git").toString.asJson)))
      val template = array(TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1),
        executionTimeoutMillis = 240000L, operation = SbtOperation.Compile), "sbtPrograms").head
      val sbt = Vector(0, 1, 2).map(n => template.mapObject(_.add("target", workspace(n).asJson)
        .add("identity", (workspace(n) + "-compile").asJson).add("capability", (workspace(n) + "-compile").asJson))) ++
        Vector(template.mapObject(_.add("target", workspace(1).asJson).add("operation", "scoped-publish".asJson)
          .add("identity", "product-publish".asJson).add("capability", "product-publish".asJson)),
          template.mapObject(_.add("target", workspace(0).asJson).add("operation", "test-compile".asJson)
            .add("identity", "product-test-compile".asJson).add("capability", "product-test-compile".asJson)),
          template.mapObject(_.add("target", workspace(0).asJson).add("operation", "test-only".asJson)
            .add("identity", "product-focused".asJson).add("capability", "product-focused".asJson)
            .add("testClasses", Vector(focusedSource.suiteName).asJson)
            .add("validationMode", Json.obj("modes" -> Vector("develop").asJson,
              "provider" -> "fixture".asJson, "livePrerequisites" -> Json.arr()))))
      val workspaces = Vector(0, 1, 2).map(n => Json.obj("identity" -> workspace(n).asJson,
        "root" -> source(root, n).resolve("repository").toString.asJson)) :+
        Json.obj("identity" -> selectedDefinition.ownerWorkspace.asJson,
          "root" -> root.resolve(".textus/sm-workflow/work.d").toString.asJson)
      base.mapObject(_.add("gitRepositories", repositories.asJson).add("sbtPrograms", sbt.asJson)
        .add("workspaces", workspaces.asJson).add("workspaceGrants", workspaces.map(w => Json.obj(
          "componentId" -> SmWorkflowComponent.componentId.name.asJson,
          "workspaceId" -> w.hcursor.get[String]("identity").toOption.get.asJson, "access" -> "manage".asJson)).asJson)
        .add("grants", Json.arr(Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
          "capabilities" -> (repositories ++ sbt).map(_.hcursor.get[String]("capability").toOption.get).asJson))))
    }

    private def prepareSources(root: Path): Unit = {
      Vector(0, 1, 2).foreach { n =>
        val directory = source(root, n)
        prepareBuild(directory.resolve("repository"), producer = n != 0)
        val build = directory.resolve("repository/build.sbt")
        Files.writeString(build, Files.readString(build).replace("2.12.18", "3.3.8"))
        if (n == 2) {
          // This independent provider has ordinary Compile authority only.
          Files.writeString(build, "scalaVersion := \"3.3.8\"\n")
        }
        val coordination = root.resolve("qualification")
        val generator = """
          |Compile / sourceGenerators += Def.task {
          |  val coordination = file(__COORDINATION__)
          |  val ordinal = __ORDINAL__
          |  val generated = (Compile / sourceManaged).value / "OverlapQualification.scala"
          |  if ((coordination / "enabled").isFile) {
          |    IO.write(coordination / (ordinal + ".started"), java.time.Instant.now.toString)
          |    if (ordinal != 0) {
          |      val deadline = System.nanoTime + java.util.concurrent.TimeUnit.SECONDS.toNanos(90)
          |      while (!(coordination / "1.started").isFile || !(coordination / "2.started").isFile) {
          |        require(System.nanoTime < deadline, "independent required Compile checks did not overlap")
          |        Thread.sleep(10L)
          |      }
          |    } else {
          |      require((coordination / "1.finished").isFile && (coordination / "2.finished").isFile,
          |        "consumer check began before both provider checks")
          |    }
          |    val invalid = ordinal == 2 && (coordination / "fail-provider").isFile
          |    IO.write(generated, "object OverlapQualification { val answer: Int = " +
          |      (if (invalid) "\"actual compile failure\"" else "42") + " }\n")
          |    IO.write(coordination / (ordinal + ".finished"), java.time.Instant.now.toString)
          |  } else IO.write(generated, "object OverlapQualification { val answer: Int = 42 }\n")
          |  Seq(generated)
          |}.taskValue
          |""".stripMargin.replace("__COORDINATION__", coordination.toString.asJson.noSpaces)
          .replace("__ORDINAL__", n.toString)
        Files.writeString(build, Files.readString(build) + generator)
        if (n != 0) {
          Files.createDirectories(directory.resolve("repository/src/main/java"))
          Files.writeString(directory.resolve("repository/src/main/java/ProviderInput.java"),
            "public final class ProviderInput { public static int answer() { return 1; } }\n")
        }
        if (n == 0) {
          // Reuse the normal ScalaTest classpath/mode build, but compile this
          // unique suite from source in the consumer rather than loading a
          // same-named parent class through discovery's parent-first loader.
          TypedSbtHostPlanFixture.buildMetadata(root.resolve("metadata-build"), focusedSource.getClass)
          val metadataBuild = Files.readString(root.resolve("metadata-build/repository/build.sbt"))
            .replace(focusedSource.getClass.getName.asJson.noSpaces, focusedSource.suiteName.asJson.noSpaces)
          Files.writeString(build, Files.readString(build) + "\n" + metadataBuild +
            "\nTest / classDirectory := baseDirectory.value / \"target\" / \"native-consumer-test-classes\"\n")
          val testSource = directory.resolve("repository/src/test/scala/Phase7NativeScopedConsumerSpec.scala")
          Files.createDirectories(testSource.getParent)
          Files.writeString(testSource, focusedSource.source)
        }
        val commands = _setup_commands_(directory, "Equal")
        commands.zipWithIndex.foreach { (command, index) =>
          if (index == 2) take(_run_setup_command_(Vector("-C", directory.resolve("repository").toString, "add", "--", ".")))
          take(_run_setup_command_(command))
        }
        // A real remote-only source successor forces nonmerge product checks.
        take(_run_setup_command_(Vector("clone", "--no-local", directory.resolve("upstream.git").toString, directory.resolve("peer").toString)))
        val changed = if (n == 0) "src/main/scala/example/Consumer.scala" else "src/main/java/ProviderInput.java"
        Files.writeString(directory.resolve("peer").resolve(changed), if (n == 0)
          "package example\nobject Consumer { val answer: Int = Provider.answer + 1 }\n"
          else "public final class ProviderInput { public static int answer() { return 2; } }\n")
        Vector(Vector("add", "--", changed), Vector("-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-m", "changed consumed source"),
          Vector("push", directory.resolve("upstream.git").toString, "HEAD:refs/heads/main")).foreach(args =>
          take(_run_setup_command_(Vector("-C", directory.resolve("peer").toString) ++ args)))
      }
    }

    def exercise(root: Path, failedProvider: Boolean): Unit = {
      import org.goldenport.cncf.job.{InMemoryJobEngine, JobResult}
      import org.goldenport.protocol.operation.OperationResponse
      prepareSources(root)
      Files.createDirectories(root.resolve(".textus/sm-workflow/work.d"))
      val storage = ThreeWorkflowNormalHostFixture.storagePlan.mapObject(fields => fields.add("records",
        (fields("records").get.asArray.get :+ Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
          "storeName" -> "workflow".asJson, "collection" -> collection.asJson)).asJson))
      val parameters = Map(RepositorySyncDirectoryList.declaration.name -> Vector(0, 1, 2)
          .map(n => source(root, n).resolve("repository").toString).asJson.noSpaces,
        ManagedDevelopmentRuntime.declaration.name -> selectedPolicy.asJson.noSpaces,
        "textus.subsystem.storage.plan" -> storage.noSpaces,
        "textus.job.scheduler.worker-count" -> "3",
        RepositorySyncDocumentInputs.declaration.name -> Json.obj("documentPaths" -> Vector("docs").asJson,
          "consumedPaths" -> Vector("src", "build.sbt", "project").asJson,
          "reason" -> "Native compiled sources and the exact selected consumer envelope are consumed inputs".asJson).noSpaces)
      var tree = ""
      _with_runtime_(root, "Equal", hostParameters = parameters) { (owner, component, actor, prepare) =>
        def artifact(operation: String, input: Input) = read_control_value[V.ManagedDevelopmentResult](take(
          owner.executeOperationResponseInContext(Request.of(component = component.name, service = "ManagedDevelopmentService",
            operation = operation, properties = List(Property("input", GoalPhaseNativeMetadata.record(input.asJson), None))), actor)))
        artifact("publishDevelopmentArtifact", publicationInput).status shouldBe "published"
        artifact("selectDevelopmentArtifact", consumerInput).status shouldBe "selected-unverified"
        artifact("compileDevelopmentConsumer", consumerInput).status shouldBe "verified"
        // Compile only the child metadata inputs under the existing retained
        // consumer envelope, then read the exact future fast-forward tree.
        // Aggregate product checks still execute and collect their own results.
        prepare { call =>
          val member = RepositorySyncDirectoryList.Member(identity(0), workspace(0), source(root, 0).resolve("repository").toString,
            "main", "origin", "main", source(root, 0).resolve("upstream.git").toString, "native-product-test-products")
          for {
            observed <- RepositorySyncConsumedInputs.observeC(call.core, member)
            _ = observed.diagnostic shouldBe None
            _ = observed.subjects.size shouldBe 1
            bound = RepositorySyncConsumedInputs.bind(call.core, member,
              Some(RepositorySyncConsumedInputs.Change(observed, Some(observed))))
            attempt <- WorkflowManagedProcessExecution.repositoryValidationAttemptC(bound, 1,
              WorkflowSbtValidation.Kind.TestCompile, Some(workspace(0)))
            _ = attempt match {
              case SbtExecutionAttempt.Observed(result) =>
                result.outcome shouldBe SbtExecutionOutcome.Succeeded
                result.target.scopedInput.nonEmpty shouldBe true
              case other => fail("Consumer metadata TestCompile must actually pass under its retained input: " + other)
            }
            _ <- WorkflowManagedProcessExecution.executeGitC(call.core, GitOperation.Fetch, workspace(0))
            tip <- WorkflowManagedProcessExecution.executeGitC(call.core, GitOperation.RemoteTip, workspace(0)).flatMap(_.valueC)
            metadata <- WorkflowManagedProcessExecution.executeGitC(call.core, GitOperation.CommitMetadata(tip.tip), workspace(0)).flatMap(_.valueC)
          } yield { tree = metadata.tree.value }
        }
      }
      // The actual TestCompile products are inside this candidate workspace;
      // the unique suite is absent from discovery's parent loader.
      val compiled = compiledProducts(root).toRealPath()
      Files.isRegularFile(compiled.resolve(focusedSource.suiteName.replace('.', '/') + ".class")) shouldBe true
      intercept[ClassNotFoundException] {
        Class.forName(focusedSource.suiteName, false, classOf[D.Inventory].getClassLoader)
      }
      val scope = D.DiscoveryScope("native-consumer-metadata", "1", Vector(D.ArtifactScope("org.example.Product0",
        "native-consumer-metadata", "1", tree, compiled, focusedSource.packageName)))
      val profile = GoalPhaseNativeMetadata.Profile(scope, D.SelectionQuery(purposes = Set(M.Purpose.FOCUSED),
        explicitSuites = Set(focusedSource.suiteName)), Vector.empty)
      val focused = RepositorySyncNativeMergeValidation.Check("consumer-focused", 2, WorkflowSbtValidation.Kind.TestOnly,
        workspace = Some(workspace(0)), metadata = Some(profile))
      val paths = Vector("src", "build.sbt", "project")
      val impact = RepositorySyncProgramImpact.Policy(Vector(
        RepositorySyncProgramImpact.Coverage(productCompile, Vector("src"), paths, "Compile every changed native source"),
        RepositorySyncProgramImpact.Coverage(focused, Vector("src/main/scala/example/Consumer.scala"), paths,
          "Execute the actual focused metadata selector under the selected consumer envelope")),
        Vector(productCompile, focused), Vector("build.sbt", "project"), "Changed build input requires all registered product checks", Vector.empty)
      val qualification = Files.createDirectories(root.resolve("qualification"))
      Files.writeString(qualification.resolve("enabled"), "bounded native aggregate qualification\n")
      if (failedProvider) Files.writeString(qualification.resolve("fail-provider"), "genuine invalid generated Scala source\n")
      var finalResult = Json.Null
      var session = ""
      var consumerSession = Option.empty[String]
      _with_runtime_(root, "Equal", hostParameters = parameters.updated(RepositorySyncProgramImpact.declaration.name, impact.asJson.noSpaces)) {
        (owner, component, actor, prepare) =>
        val engine = component.jobEngine.asInstanceOf[InMemoryJobEngine]
        engine.schedulerConfig.workerCount shouldBe 3
        engine.schedulerConfig.autoStartWorkers shouldBe true
        def invoke(extra: List[Property] = Nil) = read_control_value[V.RepositoryDirectoryBatchResult](take(
          owner.executeOperationResponseInContext(Request.of(component = component.name, service = "RepositorySyncService",
            operation = RepositorySyncDirectoryList.syncOperation,
            properties = extra :+ Property("input", Record.data("syncMode" -> "FULL_STATE"), None)), actor)))
        val result = invoke()
        finalResult = DevelopmentOperationalView.json(result.toRecord())
        session = result.sessionId
        val rows = result.repositories
        withClue(finalResult.spaces2) {
          rows.take(2).map(_.repositoryIdentity) shouldBe Vector(identity(1), identity(2))
          rows.head.kind shouldBe "Terminal"
          rows.head.outcome shouldBe "converged"
          if (failedProvider) {
            rows(1).kind shouldBe "Wait"
            rows(1).condition shouldBe Some("IntegrationValidationChecking")
            result.notStarted shouldBe Vector(identity(0))
            result.outcome shouldBe "AttentionRequired"
            Files.exists(qualification.resolve("0.started")) shouldBe false
          } else {
            rows.map(_.kind) shouldBe Vector.fill(3)("Terminal")
            rows.map(_.outcome) shouldBe Vector.fill(3)("converged")
            result.notStarted shouldBe empty
            result.outcome shouldBe "Completed"
            Files.isRegularFile(qualification.resolve("0.started")) shouldBe true
            consumerSession = Some(rows.last.sessionId)
          }
        }
        val starts = Vector(1, 2).map(n => java.time.Instant.parse(Files.readString(qualification.resolve(n + ".started"))))
        val ends = Vector(1, 2).map(n => java.time.Instant.parse(Files.readString(qualification.resolve(n + ".finished"))))
        starts.forall(start => ends.forall(end => start.isBefore(end))) shouldBe true
        val jobs = engine.listJobs(100, persistentOnly = false).flatMap { model => engine.getResult(model.jobId) match {
          case Some(JobResult.Success(OperationResponse.RecordResponse(record))) =>
            summon[org.goldenport.convert.ValueReader[V.RepositorySyncAdvanceResult]].readC(record).toOption
              .filter(v => rows.exists(_.sessionId == v.sessionId)).map(v => model.jobId.value -> v)
          case _ => None
        }}
        jobs.map(_._1).distinct.size shouldBe rows.size
        val models = jobs.map((id, value) => value.sessionId -> engine.query(take(org.goldenport.cncf.job.JobId.parse(id))).get).toMap
        if (!failedProvider) {
          val consumer = models(rows.last.sessionId)
          rows.take(2).foreach(row => consumer.createdAt.isBefore(models(row.sessionId).updatedAt) shouldBe false)
        }
        rows.foreach { row =>
          val actual = jobs.filter(_._2.sessionId == row.sessionId)
          actual.size shouldBe 1
          actual.head._2.kind shouldBe row.kind
          actual.head._2.outcome.getOrElse("unfinished") shouldBe row.outcome
        }
        // Retain exact native IDs, observed intervals and separate business
        // records as qualification output, never as a synthesized PASS receipt.
        Files.writeString(root.resolve("qualification-evidence.json"), Json.obj(
          "aggregate" -> finalResult, "jobs" -> jobs.map((id, value) => Json.obj("jobId" -> id.asJson,
            "result" -> DevelopmentOperationalView.json(value.toRecord()),
            "createdAt" -> models(value.sessionId).createdAt.toString.asJson,
            "terminalAt" -> models(value.sessionId).updatedAt.toString.asJson)).asJson,
          "requiredCheckIntervals" -> Vector(1, 2).map(n => Json.obj("repository" -> identity(n).asJson,
            "started" -> Files.readString(qualification.resolve(n + ".started")).asJson,
            "finished" -> Files.readString(qualification.resolve(n + ".finished")).asJson)).asJson).spaces2)
        val before = Vector(0, 1, 2).map(n => qualification.resolve(n + ".started"))
          .filter(Files.exists(_)).map(p => p.toString -> Files.readString(p)).toMap
        DevelopmentOperationalView.json(invoke(List(Property("sessionId", session, None))).toRecord()) shouldBe finalResult
        before.foreach((path, bytes) => Files.readString(Path.of(path)) shouldBe bytes)
        // Native child receipts prove the nonmerge Compile and scoped focused
        // result reached persistence through the production loop.
        rows.foreach { row => prepare { call => for {
          stage <- call.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input,
            InstanceIdentity(row.sessionId), source(root, if (row.repositoryIdentity == identity(0)) 0 else if (row.repositoryIdentity == identity(1)) 1 else 2),
            if (row.kind == "Terminal") "integration-validated" else "validation-result-1")
        } yield {
          val state = stage.get.payload.validation.get
          state.receipts.head.identity shouldBe "index-local"
          state.receipts.count(_.identity.startsWith("index-")) shouldBe 1
          if (row.repositoryIdentity == identity(0)) {
            state.receipts.drop(1).map(_.identity) shouldBe state.selected.map(_.identity)
            state.selected.last.invocation.nonEmpty shouldBe true
            state.receipts.last.sbt.get.mode.get.verified shouldBe true
            state.dependencyInputs.nonEmpty shouldBe true
          }
        } } }
      }
      consumerSession.foreach { child =>
        _with_runtime_(root, "Equal", hostParameters = parameters.updated(RepositorySyncProgramImpact.declaration.name, impact.asJson.noSpaces)) {
          (owner, component, actor, _) =>
          val result = read_control_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
              properties = List(Property("sessionId", child, None))), actor)))
          result.sessionId shouldBe child
          result.kind shouldBe "Terminal"
          result.outcome shouldBe Some("converged")
        }
      }
    }
  }

  private object SyncFixture extends Phase69ConfiguredRepositoryFixture {
    override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
      val configured = super.process_plan(root, scenario, goal)
      val repositories = configured.hcursor.get[Vector[Json]]("gitRepositories").toOption.get
      val repository = repositories.head
        .mapObject(_.add("identity", edge.provider.repository.asJson))
      val canonical = repository.mapObject(_.add("identity", "canonical-provider".asJson)
        .add("target", "canonical-provider".asJson).add("capability", "canonical-provider".asJson))
      // Keep the packaged candidate repository paired with its retained grant.
      val complete = configured.mapObject(fields => fields.add("gitRepositories", (Vector(repository) ++ repositories.tail :+ canonical).asJson)
        .add("workspaces", (fields("workspaces").get.asArray.get :+ Json.obj(
          "identity" -> "canonical-provider".asJson, "root" -> root.resolve("unrelated-canonical").toString.asJson)).asJson)
        .add("workspaceGrants", (fields("workspaceGrants").get.asArray.get :+ Json.obj(
          "componentId" -> SmWorkflowComponent.componentId.name.asJson, "workspaceId" -> "canonical-provider".asJson,
          "access" -> "manage".asJson)).asJson)
        .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
          (grant("capabilities").get.asArray.get :+ "canonical-provider".asJson).asJson))))))
      Phase69ConfiguredRepositoryFixture.withInitializationSources(complete)
    }

    def exercise(root: Path): Unit = {
      val canonical = Files.createDirectories(root.resolve("unrelated-canonical"))
      Files.writeString(canonical.resolve("unrelated.txt"), "Unrelated canonical source is not a Git input.\n")
      val before = tree(canonical)
      val parameters = Map("sm-workflow.managed-development" -> policy.copy(repositorySyncEdge = Some(edge.identity)).asJson.noSpaces,
        ManagedDevelopmentRepositorySync.mainIntegration.name -> "direct")
      var session = ""
      def json(response: org.goldenport.protocol.operation.OperationResponse): Json = response match {
        case org.goldenport.protocol.operation.OperationResponse.RecordResponse(record) => DevelopmentOperationalView.json(record)
        case other => fail("Expected native RepositorySync response: " + other)
      }
      _with_runtime_(root, "Equal", hostParameters = parameters) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(root, "Equal").size, scenario = "Equal"))
        Given("a managed provider and an unrelated canonical checkout with no usable Git history")
        val started = json(take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "RepositorySyncService", operation = "startRepositorySync",
          properties = List(Property("input", org.goldenport.record.Record.data(
            "repositoryIdentity" -> edge.provider.repository, "syncMode" -> "FULL_STATE"), None))), actor)))
        session = started.hcursor.get[String]("sessionId").toOption.get
        When("native Advance converges the selected equal dependency")
        val result = json(take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "RepositorySyncService", operation = "advanceRepositorySync",
          properties = List(Property("sessionId", session, None))), actor)))
        result.hcursor.get[String]("kind").toOption shouldBe Some("Terminal")
        result.hcursor.get[String]("outcome").toOption shouldBe Some("converged")
        Then("the native runtime needs no producer SBT binding and leaves the canonical source untouched")
        tree(canonical) shouldBe before
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        Files.exists(root.resolve(".textus/sm-workflow/work.d/artifacts")) shouldBe false
      }
      _with_runtime_(root, "Equal", hostParameters = parameters) { (owner, component, actor, _) =>
        val result = json(take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "RepositorySyncService", operation = "advanceRepositorySync",
          properties = List(Property("sessionId", session, None))), actor)))
        result.hcursor.get[String]("kind").toOption shouldBe Some("Terminal")
        result.hcursor.get[String]("outcome").toOption shouldBe Some("converged")
        tree(canonical) shouldBe before
      }
    }
  }

  private def tree(path: Path): Map[String, Vector[Byte]] = if (!Files.exists(path)) Map.empty else {
    import scala.jdk.CollectionConverters.*
    val stream = Files.walk(path)
    try stream.iterator().asScala.filter(Files.isRegularFile(_)).map(p => path.relativize(p).toString -> Files.readAllBytes(p).toVector).toMap
    finally stream.close()
  }
  private def prepareBuild(root: Path, producer: Boolean): Unit = {
    Files.createDirectories(root.resolve("project"))
    Files.createDirectories(root.resolve("src/main/scala/example"))
    Files.writeString(root.resolve("project/build.properties"), "sbt.version=1.9.7\n")
    Files.writeString(root.resolve("project/plugins.sbt"),
      "resolvers += \"SimpleModeling.org\" at \"https://www.simplemodeling.org/repository/maven\"\n" +
      "resolvers += Resolver.defaultLocal\naddSbtPlugin(\"org.goldenport\" % \"sbt-cozy\" % \"0.1.18-SNAPSHOT\")\n")
    Files.writeString(root.resolve(".gitignore"), "target/\nproject/target/\nproject/project/\n")
    if (producer) {
      Files.createDirectories(root.resolve("src/test/scala"))
      Files.writeString(root.resolve("src/test/scala/ProductCheck.scala"), "object ProductCheck\n")
      Files.writeString(root.resolve("value.txt"), "fixture setup input\n")
    }
    Files.writeString(root.resolve("build.sbt"),
      "enablePlugins(org.goldenport.cozy.CozyDevelopmentArtifactsPlugin)\n" +
      "scalaVersion := \"2.12.18\"\norganization := \"" + edge.requested.organization + "\"\n" +
      "name := \"" + (if (producer) "provider" else "consumer") + "\"\nversion := \"0.0.1-SNAPSHOT\"\n" +
      (if (producer) "" else "libraryDependencies += cozyDevelopmentDependency(\"" + edge.requested.organization + "\" %% \"provider\" % \"0.0.1-SNAPSHOT\").value\n" +
        "cozyDevelopmentModules += (\"" + edge.requested.organization + "\" %% \"provider\" % \"0.0.1-SNAPSHOT\")\n"))
    Files.writeString(root.resolve("src/main/scala/example/" + (if (producer) "Provider" else "Consumer") + ".scala"),
      if (producer) "package example\nobject Provider { def answer: Int = 42 }\n"
      else "package example\nobject Consumer { val answer: Int = Provider.answer }\n")
  }

  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    // Artifact Operations opt into their build workspaces; Goal capture keeps its registered Goal plan.
    if (scenario != "DevelopmentArtifacts") return super.process_plan(root, scenario, goal)
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString))
    val template = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1),
      operation = SbtOperation.Compile, executionTimeoutMillis = 240000L, stdoutBytes = 1048576, stderrBytes = 131072)
      .hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.head
    val extraConsumer = Option.when(Files.isDirectory(root.resolve("consumer-two")))(
      "development-consumer-two" -> root.resolve("consumer-two")).toVector
    val programs = (Vector((edge.provider.workspace, "scoped-publish"), (edge.consumer.workspace, "compile")) ++
      extraConsumer.map((workspace, _) => workspace -> "compile")).map { (workspace, operation) =>
      template.mapObject(_.add("target", workspace.asJson).add("operation", operation.asJson)
        .add("capability", (workspace + "-" + operation).asJson).add("identity", (workspace + "-" + operation).asJson))
    }
    val workspaces = Vector(edge.consumer.workspace -> root.resolve("consumer"), definition.ownerWorkspace -> root.resolve(".textus/sm-workflow/work.d")) ++ extraConsumer
    def array(name: String): Vector[Json] = git.hcursor.get[Vector[Json]](name).toOption.get
    val capabilities = array("gitPrograms").map(_.hcursor.get[String]("capability").toOption.get) ++
      programs.map(_.hcursor.get[String]("capability").toOption.get)
    git.mapObject(_.add("sbtPrograms", programs.asJson)
      .add("workspaces", (array("workspaces") ++ workspaces.map { (id, path) => Json.obj("identity" -> id.asJson, "root" -> path.toString.asJson) }).asJson)
      .add("workspaceGrants", (array("workspaceGrants") ++ workspaces.map { (id, _) => Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
        "workspaceId" -> id.asJson, "access" -> "manage".asJson) }).asJson)
      .add("grants", Json.arr(Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson, "capabilities" -> capabilities.asJson))))
  }
}
