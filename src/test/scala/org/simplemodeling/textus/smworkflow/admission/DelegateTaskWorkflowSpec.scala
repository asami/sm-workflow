/**
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.workflow.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import org.simplemodeling.textus.smworkflow.preparation.{NativeDelegateTaskStartInput, NativeDelegateTaskAdoptionPreparation}
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import StandaloneFixExecution as S
import StandaloneFixExecution.given
import WorkflowSbtValidation as T
import SplitPhaseExplicitPartitionFixture.take

/** Independent normal operations with actual managed checks/commit/publication.
  * Transport, semantic worker and artifact-selection observations are explicitly
  * specification-owned, never genuine chat delivery or installed activation.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class DelegateTaskWorkflowSpec extends AnyWordSpec with ThreeWorkflowManagedRepositoryFixture {
  private val _checks = Vector(GoalPhaseNativeValidation.Check("compile", 1, T.Kind.Compile),
    GoalPhaseNativeValidation.Check("focused", 2, T.Kind.TestOnly))
  private val _suite = "org.simplemodeling.textus.smworkflow.p62authored.LineWriterSpec"
  private val _test = "src/test/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriterSpec.scala"
  private val _component = "org.example.DelegateFixture"
  private val _publication = "delegate-fixture"
  private def _contract(role: String, publish: Boolean = true) = DelegateTask.Contract("requester-existing-chat", "destination-existing-chat",
    "requester-existing-chat", _component, role, "localized", Vector("dispatch", "implement", "validate", "commit", "review", "return", "adopt", "publishLocal"),
    Vector("remote-push", "operational-repository"), Vector(ContextReference("specification-explicit-user-direction", "1")),
    Option.when(publish)(_publication), Vector("org.example:p68-delegate-fixture_3:0.0.1-SNAPSHOT"), "Use the actual publication/source selection; activation is a separate observation")
  private def _input(role: String, request: String, publish: Boolean = true): V.DelegateTaskInput = {
    val c = _contract(role, publish)
    V.DelegateTaskInput("sm-workflow", request, "Author focused behavioral coverage for the existing LineWriter",
      NonEmptyVector.of("src/test/scala"), NonEmptyVector.of("compile", "focused"), NonEmptyVector.of("behavior"),
      NonEmptyVector.of(V.WorkflowArtifactReference("specification-adopted-source", "1")), false, "code", "off", 10,
      "direct-instruction", Vector.empty, V.DelegateTaskContract(c.requesterIdentity, c.destinationIdentity, c.returnDestination,
        c.componentIdentity, c.role, c.validationScope, NonEmptyVector.fromVectorUnsafe(c.permittedOperations), c.exclusions,
        NonEmptyVector.of(V.WorkflowArtifactReference(c.authorizationReferences.head.identity, c.authorizationReferences.head.revision)),
        c.publicationTarget, c.artifactCoordinates, c.consumerInstructions))
  }
  private def _state(v: V.DelegateTaskView): S.State = {
    val raw = take(v.state.get.toJsonStringC)
    val normalized = GoalPhaseNativeMetadata.normalizePublicJson(parser.parse(raw).toOption.get).toOption.get
    normalized.as[S.State].toOption.get
  }
  private def _json(record: Record): Json = parser.parse(take(record.toJsonStringC)).toOption.get
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = if (!Files.exists(root.resolve("repository/build.sbt"))) {
    TypedSbtHostPlanFixture.buildAuthoring(root)
    Files.createDirectories(root.resolve("repository/src/test/scala"))
    Files.writeString(root.resolve("repository/src/test/scala/ProductCheck.scala"), "object ProductCheck {}\n")
    Files.writeString(root.resolve("repository/value.txt"), "preserved baseline\n")
    Files.writeString(root.resolve("repository/build.sbt"), Files.readString(root.resolve("repository/build.sbt")) +
      "\norganization := \"org.example\"\nname := \"p68-delegate-fixture\"\nversion := \"0.0.1-SNAPSHOT\"\n" +
      "publishLocal := { val result = publishLocal.value; IO.append(file(\"../publication-count.txt\"), \"published\\n\"); result }\n")
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
    val original = super._setup_commands_(root, "Goal")
    original.patch(original.indexWhere(_.contains("commit")), Vector(Vector("-C", root.resolve("repository").toString,
      "add", "--", "src/main", "docs")), 0)
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    def array(j: Json, k: String) = j.hcursor.get[Vector[Json]](k).toOption.get
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString),
      goalPaths = goal.candidatePaths, goalMessages = Vector("Delegate scoped result"), goalSelections = Vector(Vector("src/test/scala")))
      .mapObject(fields => fields.add("gitPrograms", Json.fromValues(fields("gitPrograms").get.asArray.get.map { program =>
        program.hcursor.get[String]("selectionId").toOption match {
          case Some("goal-step-1") => program.mapObject(_.add("selectionId", "delegate-task".asJson))
          case _ => program
        }
      })))
    val compile = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(git), operation = SbtOperation.Compile)
    val focused = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(2), operation = SbtOperation.TestOnly, testClasses = Vector(_suite))
    val publication = array(TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), operation = SbtOperation.PublishLocal), "sbtPrograms").head
      .mapObject(_.add("target", TypedGitHostPlanFixture.workspace.asJson).add("identity", "delegate-publication".asJson).add("capability", "delegate-publication".asJson))
    val programs = array(compile, "sbtPrograms") ++ array(focused, "sbtPrograms") :+ publication
    val caps = (array(compile, "gitPrograms") ++ programs).map(_.hcursor.get[Json]("capability").toOption.get)
    compile.mapObject(_.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues(array(compile, "workspaces") ++ array(focused, "workspaces")))
      .add("workspaceGrants", Json.fromValues(array(compile, "workspaceGrants") ++ array(focused, "workspaceGrants")))
      .add("grants", Json.arr(Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson, "capabilities" -> Json.fromValues(caps)))))
  }
  private def _parameters: Map[String, String] = {
    val collection = ThreeWorkflowNormalHostFixture.collection("local_publication")
    val storage = ThreeWorkflowNormalHostFixture.storagePlan.mapObject(fields => fields.add("records",
      Json.fromValues(fields("records").get.asArray.get :+ Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
        "storeName" -> "workflow".asJson, "collection" -> collection.asJson))))
    BoundedWorkExecutionAdmissionFixture.parameters ++ Map("textus.subsystem.storage.plan" -> storage.noSpaces,
      "sm-workflow.local-publication" -> Json.obj("storeName" -> "workflow".asJson, "collection" -> collection.asJson,
        "targets" -> Json.arr(Json.obj("identity" -> _publication.asJson, "component" -> _component.asJson,
          "workspace" -> TypedGitHostPlanFixture.workspace.asJson))).noSpaces)
  }

  "Independent delegated delivery" should {
    "use public native work focused checks local commit publication return and separate requester adoption without duplicate effects" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/delegate-task").toAbsolutePath), "episode-").toRealPath()
      val original = ThreeWorkflowNormalHostFixture.definition(root)
      val host = original.copy(goal = original.goal.copy(candidatePaths = Vector("src/test/scala"),
        stepValidation = original.goal.stepValidation.copy(checks = _checks)))
      _with_runtime_(root, "Delegate", Some(host), _parameters) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "Delegate").size))
        def invoke(operation: String, properties: List[Property], delivery: Boolean = false): V.DelegateTaskView =
          read_value[V.DelegateTaskView](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = if (delivery) "DelegateTaskDeliveryService" else "DelegateTaskService", operation = operation, properties = properties), actor)))
        def start(input: V.DelegateTaskInput, suffix: String): V.DelegateTaskView = {
          val selection = "delegate-selection-" + suffix
          val key = "delegate-key-" + suffix
          val prepared = take(NativeDelegateTaskStartInput.prepareC(Map("input" -> "selected.json", "selection-id" -> selection,
            "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> key,
            "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
          prepare { call =>
            val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
            call.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
          }
          invoke("startDelegateTask", List(Property("input", input, None), Property("selectionId", selection, None),
            Property("participant", "participant", None), Property("idempotencyKey", key, None)))
        }
        def advance(id: String) = invoke("advanceDelegateTask", List(Property("sessionId", id, None)))
        def observe(id: String, event: String, destination: String, result: Option[Record] = None, artifact: Option[Record] = None) =
          invoke("observeDelegateTaskDelivery", List(Property("input", V.DelegateTaskObservationInput(id, event,
            V.WorkflowArtifactReference("specification-transport-" + event, "1"), Some(destination), result, artifact,
            "SPECIFICATION-OWNED caller observation; no real chat or activation is claimed"), None)), delivery = true)
        def implement(started: V.DelegateTaskView, changed: Boolean): V.DelegateTaskView = {
          val work = advance(started.sessionId)
          work.kind shouldBe "Request"
          work.resultType shouldBe Some("DelegateTaskWorkResult")
          advance(started.sessionId).issuedWorkContext shouldBe work.issuedWorkContext
          if (changed) {
            val path = root.resolve("repository").resolve(_test)
            Files.createDirectories(path.getParent)
            Files.writeString(path, """package org.simplemodeling.textus.smworkflow.p62authored
              |import org.scalatest.wordspec.AnyWordSpec
              |import org.scalatest.matchers.should.Matchers
              |final class LineWriterSpec extends AnyWordSpec with Matchers {
              |  "LineWriter" should { "retain text and append one LF" in {
              |    LineWriter.render("expected") shouldBe "expected\\n"
              |    LineWriter.render("日本語") shouldBe "日本語\\n"
              |    LineWriter.render("") shouldBe "\\n"
              |  }}
              |}
              |""".stripMargin.replace("\\\\n", "\\n"))
          }
          val result = V.DelegateTaskWorkResult("completed", "SPECIFICATION-OWNED focused behavior authored or applicable existing source retained",
            Vector.empty, NonEmptyVector.of(V.DelegateTaskCondition("behavior", true, "Explicit assigned condition")), Vector.empty, None, None)
          invoke("submitDelegateTaskWorkResult", List(Property("sessionId", started.sessionId, None), Property("result", result, None),
            Property("issuedWorkContext", work.issuedWorkContext.get, None), Property("resultReference", V.WorkflowArtifactReference("semantic-" + started.sessionId, "1"), None),
            Property("executionEvidence", V.WorkflowExecutionEvidence(Vector(V.WorkflowArtifactReference("semantic-" + started.sessionId, "1")),
              Some("specification-implementer"), None,
              Some(V.WorkflowExecutionObservation("INLINE", BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity,
                BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity, BoundedWorkExecutionAdmissionFixture.profile.identity))), None)))
          advance(started.sessionId)
        }
        val request = "single-selected-request"
        val requester = start(_input("requester", request), "requester")
        advance(requester.sessionId).kind shouldBe "Dispatch"
        val destination = start(_input("destination", request), "destination")
        requester.sessionId should not be destination.sessionId
        val dispatched = observe(requester.sessionId, "dispatch-observed", destination.sessionId)
        _state(dispatched).stage shouldBe "AwaitResult"
        val returned = implement(destination, changed = true)
        returned.kind shouldBe "Return"
        val state = _state(returned)
        val redelivered = invoke("startDelegateTask", List(Property("input", _input("destination", request), None),
          Property("selectionId", "delegate-selection-destination", None), Property("participant", "participant", None),
          Property("idempotencyKey", "delegate-key-destination", None)))
        redelivered.sessionId shouldBe destination.sessionId
        redelivered.kind shouldBe "Retained"
        _state(redelivered) shouldBe state
        state.stage shouldBe "ReturnReady"
        state.receipts.map(_.identity) shouldBe Vector("compile", "focused")
        state.receipts.last.sbt.flatMap(_.selection).exists(s => s.testsRun > 0 && s.completedSuites > 0) shouldBe true
        state.commit should not be empty
        state.reviewReference shouldBe None
        state.automaticCount shouldBe 0
        state.delivery.flatMap(_.publication).map(_.status) shouldBe Some("published")
        val commit = state.commit.get
        val publications = Files.readString(root.resolve("publication-count.txt"))
        val executions = Files.readString(root.resolve("sbt-executions.txt"))
        _state(advance(destination.sessionId)) shouldBe state
        Files.readString(root.resolve("publication-count.txt")) shouldBe publications
        Files.readString(root.resolve("sbt-executions.txt")) shouldBe executions
        val received = observe(requester.sessionId, "result-received", destination.sessionId, returned.state)
        _state(received).stage shouldBe "ResultReceived"
        val returnedAck = observe(destination.sessionId, "return-observed", destination.sessionId)
        returnedAck.kind shouldBe "Terminal"
        val selection = Record.data("sourceCommit" -> commit.identity, "coordinates" -> _contract("requester").artifactCoordinates,
          "selectionReference" -> Record.data("identity" -> "specification-consumer-selection", "revision" -> "1"),
          "consumerCheckReferences" -> Vector.empty[Record])
        val adoptionInput = V.DelegateTaskObservationInput(requester.sessionId, "adopt",
          V.WorkflowArtifactReference("specification-transport-adopt", "1"), Some(destination.sessionId), None, Some(selection),
          "SPECIFICATION-OWNED caller observation; no real chat or activation is claimed")
        // A label or caller attachment alone cannot cross the host observation gate.
        owner.executeOperationResponseInContext(Request.of(component = component.name, service = "DelegateTaskDeliveryService",
          operation = "observeDelegateTaskDelivery", properties = List(Property("input", adoptionInput, None))), actor).isSuccess shouldBe false
        val adoptionContext = read_value[V.DelegateTaskAdoptionContext](take(owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "DelegateTaskDeliveryService", operation = "describeDelegateTaskAdoption",
          properties = List(Property("sessionId", requester.sessionId, None))), actor)))
        prepare { call => for {
          prepared <- NativeDelegateTaskAdoptionPreparation.prepareC(adoptionContext, adoptionInput, adoptionInput.sourceReference,
            V.WorkflowExecutionEvidence(Vector(adoptionInput.sourceReference), Some("participant"), None, None), "normal-development-host",
            call.executionContext.clock.instant().minusSeconds(1))
          _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
        } yield () }
        val adopted = observe(requester.sessionId, "adopt", destination.sessionId, artifact = Some(selection))
        _state(adopted).stage shouldBe "Adopted"
        _state(adopted).delivery.flatMap(_.adoptionScope) shouldBe Some("result-and-selection-attachment-only")
        _state(adopted).delivery.flatMap(_.artifactSelection) shouldBe Some(_json(selection))
        observe(requester.sessionId, "adopt", destination.sessionId, artifact = Some(selection)).state shouldBe adopted.state
        _state(adopted).delivery.get.returned shouldBe Some(_json(returned.state.get))
        // A fresh legitimate no-change request executes its actual required checks,
        // retains the existing commit and reuses the applicable publication.
        val nochange = implement(start(_input("destination", "no-change-request"), "no-change"), changed = false)
        _state(nochange).commit.get.identity shouldBe commit.identity
        _state(nochange).commitExecution shouldBe None
        _state(nochange).candidate.get.paths shouldBe empty
        _state(nochange).delivery.flatMap(_.publication).map(_.status) shouldBe Some("unnecessary")
        Files.readString(root.resolve("publication-count.txt")) shouldBe publications
      }
    }
    "keep explicit broad scope conditional review and missing-profile refusal separate from sibling contracts" in {
      val input = take(DelegateTask.inputC(_input("destination", "policy")))
      take(S.startC(input, "participant", _checks)).stage shouldBe "Implement"
      val broad = input.copy(delegation = input.delegation.map(_.copy(validationScope = "broad")))
      S.startC(broad, "participant", _checks).isSuccess shouldBe false
      val full = _checks :+ GoalPhaseNativeValidation.Check("FULL", 3, T.Kind.Test)
      take(S.startC(broad.copy(checks = full.map(_.identity)), "participant", full)).selected shouldBe full
      DelegateTaskNativeRun.preflightExecutionC(input, None, None).isSuccess shouldBe false
      val mapping = take(BoundedWorkExecutionAdmissionFixture.mappingC())
      take(DelegateTaskNativeRun.preflightExecutionC(input, Some(mapping), Some(BoundedWorkExecutionAdmissionFixture.origin)))
      Vector("Implement", "Repair", "Review").foreach { kind =>
        val requirement = take(DelegateTaskNativeRun.executionRequirementC(input, kind))
        requirement.reasoningClass should not be empty
        requirement.capabilities.head.identity should startWith ("delegate-task-")
      }
      S.inputC(input.copy(delegation = input.delegation.map(_.copy(permittedOperations = Vector("implement"))))).isSuccess shouldBe false
      val restricted = input.copy(delegation = input.delegation.map(_.copy(exclusions = Vector("src/test/scala/private"))))
      S.permitted("src/test/scala/private/Expected.scala", restricted) shouldBe false
      S.permitted("src/test/scala/Behavior.scala", restricted) shouldBe true
      // Existing GoalTask still selects its mandatory review policy and standalone
      // still has no task/commit contract; neither is invoked by delegation.
      GoalTaskNativeRun.completionOperation.operation shouldBe "submitGoalTaskWorkResult"
      StandaloneFixNativeRun.completionOperation.operation shouldBe "submitStandaloneFixWorkResult"
    }
    "retain distinct dispatch and adoption frontiers and reject crossed or edited transport observations" in {
      val input = take(DelegateTask.inputC(_input("requester", "observations")))
      val state = take(S.startC(input, "participant", _checks)).copy(stage = "DispatchReady")
      val dispatch = DelegateTask.Observation("dispatch-observed", ContextReference("actual-specification-source", "1"), Some("destination-session"), None, None, "Caller observed selected delivery")
      val sent = take(DelegateTask.observeC(state, dispatch))
      take(DelegateTask.observeC(sent, dispatch)) shouldBe sent
      DelegateTask.observeC(sent, dispatch.copy(destinationSessionId = Some("other"))).isSuccess shouldBe false
      DelegateTask.observeC(state, dispatch.copy(event = "adopt", artifactSelection = Some(Json.obj("activation" -> "unverified".asJson)))).isSuccess shouldBe false
      DelegateTask.observeC(sent, dispatch.copy(event = "result-received", result = Some(state.asJson))).isSuccess shouldBe false
      sent.automaticCount shouldBe state.automaticCount
      sent.ordinaryCount shouldBe state.ordinaryCount
    }
  }
}
