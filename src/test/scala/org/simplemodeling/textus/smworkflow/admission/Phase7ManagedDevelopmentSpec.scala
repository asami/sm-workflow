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
  * @version Nov. 12, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class Phase7ManagedDevelopmentSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
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
    var execution = Execution("succeeded", Json.obj())
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
      val workspaces = array(a, "workspaces") ++ array(b, "workspaces") ++ array(compile, "workspaces") ++ array(focused, "workspaces") ++ Vector(owner, other)
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
        Vector("compile"), repairLimit = 10)
      _with_runtime_(root, mode, Some(settings), BoundedWorkExecutionAdmissionFixture.parameters ++ Map(
        "sm-workflow.managed-development" -> connectedPolicy.asJson.noSpaces, "textus.subsystem.storage.plan" -> storage.noSpaces)) { (owner, component, actor, prepare) =>
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
        prepare(call => call.goalWorkC(InstanceIdentity(a.sessionId)).map { work =>
          val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[GoalPhaseNativeWork.Request]]
          source = Some(V.WorkflowArtifactReference(order.request.continuationId.value, order.request.expectedRevision.value)); ()
        })
        def input(role: String, request: String) = {
          val c = DelegateTaskRequesterRoutingFixture.input(request).delegation
          V.DelegateTaskInput(connectedEdge.provider.repository, request, "Verify the existing provider answer and deliver its exact accepted source",
            NonEmptyVector.of("src/main/scala", "src/test/scala"), NonEmptyVector.of("compile", "test-only"), NonEmptyVector.of("behavior"),
            NonEmptyVector.of(source.get), false, "code", "off", 10, "direct-instruction", Vector.empty,
            c.copy(componentIdentity = connectedEdge.provider.identity, role = role, exclusions = Vector("remote-push"),
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
        if (mode == "CanonicalAliases") {
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
                Some(destination.sessionId), None, artifact, "SPECIFICATION-OWNED caller observation"), None)))))
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
              Some(destination.sessionId), None, Some(artifact), "SPECIFICATION-OWNED caller observation")
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
        if (mode != "Connected") {
          val retainedA = read_value[V.GoalPhaseAdvanceResult](take(invoke("GoalPhaseService", "advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(a.sessionId))))
          retainedA.issuedWorkContext.map(value => DevelopmentOperationalView.json(value.toRecord())) shouldBe
            original.issuedWorkContext.map(value => DevelopmentOperationalView.json(value.toRecord()))
        }
      }
    }
  }

  private object SyncFixture extends Phase69ConfiguredRepositoryFixture {
    override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
      val configured = super.process_plan(root, scenario, goal)
      val repository = configured.hcursor.get[Vector[Json]]("gitRepositories").toOption.get.head
        .mapObject(_.add("identity", edge.provider.repository.asJson))
      val canonical = repository.mapObject(_.add("identity", "canonical-provider".asJson)
        .add("target", "canonical-provider".asJson).add("capability", "canonical-provider".asJson))
      configured.mapObject(fields => fields.add("gitRepositories", Vector(repository, canonical).asJson)
        .add("workspaces", (fields("workspaces").get.asArray.get :+ Json.obj(
          "identity" -> "canonical-provider".asJson, "root" -> root.resolve("unrelated-canonical").toString.asJson)).asJson)
        .add("workspaceGrants", (fields("workspaceGrants").get.asArray.get :+ Json.obj(
          "componentId" -> SmWorkflowComponent.componentId.name.asJson, "workspaceId" -> "canonical-provider".asJson,
          "access" -> "manage".asJson)).asJson)
        .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
          (grant("capabilities").get.asArray.get :+ "canonical-provider".asJson).asJson))))))
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
    val programs = Vector((edge.provider.workspace, "scoped-publish"), (edge.consumer.workspace, "compile")).map { (workspace, operation) =>
      template.mapObject(_.add("target", workspace.asJson).add("operation", operation.asJson)
        .add("capability", (workspace + "-" + operation).asJson).add("identity", (workspace + "-" + operation).asJson))
    }
    val workspaces = Vector(edge.consumer.workspace -> root.resolve("consumer"), definition.ownerWorkspace -> root.resolve(".textus/sm-workflow/work.d"))
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
