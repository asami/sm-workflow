package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.action.CommandAction
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.processexecution.{GitOperation, GitSelectionId, SbtOperation}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import org.simplemodeling.textus.smworkflow.preparation.{NativeDelegateTaskStartInput, NativeDelegateTaskScopeCorrectionInput}
import SplitPhaseExplicitPartitionFixture.take
import StandaloneFixExecution as S
import S.given
import DelegateTaskScopeCorrection.given

/** Actual deferred public handlers and managed local persistence. All direction
  * and selections are specification-owned, with no live Human or check result.
  * @since   Nov. 18, 2026
  * @version Nov. 17, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NormalDeferredScopeCorrectionSpec extends AnyWordSpec with GivenWhenThen with Phase69ConfiguredRepositoryFixture {
  private val added = Vector("project/SbtBridge.scala")
  private val input = V.DelegateTaskInput("sm-workflow", "deferred-scope", "Connect the existing SBT bridge",
    NonEmptyVector.one("src/test/scala"), NonEmptyVector.of("compile", "test-only"), NonEmptyVector.of("behavior", "delivery"),
    NonEmptyVector.one(V.WorkflowArtifactReference("SPECIFICATION-OWNED-request", "1")), false, "code", "required", 10,
    "direct-instruction", Vector.empty, V.DelegateTaskContract("requester", "destination", "requester", "org.example.Fixture",
      "destination", "localized", NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"),
      Vector("private"), NonEmptyVector.one(V.WorkflowArtifactReference("SPECIFICATION-OWNED-authority", "1")),
      None, Vector.empty, "Return the unchanged request and actual results"), Vector("behavior", "delivery"))

  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val base = super.process_plan(root, scenario, goal)
    // A real typed lint binding is needed for deferred assembly. This executable
    // can only fail if accidentally invoked; this specification never Advances
    // beyond implementation or supplies a validation result.
    val lint = TypedSbtHostPlanFixture.plan(root, Path.of("/usr/bin/false"), Vector(3), operation = SbtOperation.FixCheck)
    def array(plan: Json, key: String) = plan.hcursor.get[Vector[Json]](key).toOption.get
    val aliases = Set("sm-workflow-repository-validation-3")
    base.mapObject(_.add("sbtPrograms", array(lint, "sbtPrograms").asJson)
      .add("workspaces", (array(base, "workspaces").filterNot(w => w.hcursor.get[String]("identity").toOption.exists(aliases)) ++
        array(lint, "workspaces")).asJson)
      .add("workspaceGrants", (array(base, "workspaceGrants").filterNot(w => w.hcursor.get[String]("workspaceId").toOption.exists(aliases)) ++
        array(lint, "workspaceGrants")).asJson)
      .add("grants", array(base, "grants").map { grant =>
        if (grant.hcursor.get[String]("componentId").contains(SmWorkflowComponent.componentId.name))
          grant.mapObject(_.add("capabilities", (array(grant, "capabilities") ++
            array(lint, "sbtPrograms").map(_.hcursor.get[Json]("capability").toOption.get)).distinct.asJson))
        else grant
      }.asJson))
  }

  private def state(view: V.DelegateTaskView): S.State = GoalPhaseNativeMetadata.normalizePublicJson(
    parser.parse(take(view.state.get.toJsonStringC)).toOption.get).toOption.get.as[S.State].toOption.get

  "Normal deferred DelegateTask scope correction" should {
    "inspect proposed inventory, require exact selection, and preserve the native implementation frontier" in {
      Given("an isolated configured host, required lint, and a still-unsubmitted destination implementation")
      val root = Files.createTempDirectory(Path.of("target").toAbsolutePath, "normal-deferred-scope-").toRealPath()
      _with_runtime_(root, "Equal", hostParameters = BoundedWorkExecutionAdmissionFixture.parameters ++
        Map("textus.resource.url.file.roots" -> root.toString)) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "Equal").size))
        val operations = Set("describeDelegateTaskScopeCorrection", "correctDelegateTaskScope")
        component.protocol.services.services.find(_.name == "DelegateTaskService").get.operations.operations.toVector
          .map(_.name).filter(name => name == "describeDelegateTaskScopeCorrection" || name == "correctDelegateTaskScope").toSet shouldBe operations
        ThreeWorkflowLauncherAssembly.publishedMcpOperations.filter(identity =>
          identity == "DelegateTaskService.describeDelegateTaskScopeCorrection" ||
            identity == "DelegateTaskService.correctDelegateTaskScope") shouldBe operations.map("DelegateTaskService." + _)
        def request(op: String, properties: List[Property]) = Request.of(component = component.name,
          service = "DelegateTaskService", operation = op, properties = properties)
        def call(op: String, properties: List[Property]): Consequence[OperationResponse] =
          owner.executeOperationResponseInContext(request(op, properties), actor)
        def value[A: org.goldenport.convert.ValueReader](op: String, properties: List[Property]): A = read_value[A](take(call(op, properties)))
        def provision(prepared: Record): Unit = prepare { setup =>
          val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        }
        val options = Map("input" -> "input.json", "selection-id" -> "scope-start", "participant" -> "participant",
          "origin" -> "ExplicitHuman", "idempotency-key" -> "scope-start-key", "selection-collection" -> "entity:smworkflow.cli.goal_selection")
        provision(take(NativeDelegateTaskStartInput.prepareC(options, _ => input.toRecord().toJsonStringC)))
        val started = value[V.DelegateTaskView]("startDelegateTask", List(Property("input", input, None),
          Property("selectionId", "scope-start", None), Property("participant", "participant", None), Property("idempotencyKey", "scope-start-key", None)))
        def advance() = value[V.DelegateTaskView]("advanceDelegateTask", List(Property("sessionId", started.sessionId, None)))
        val issued = advance()
        val before = state(issued)
        val old = issued.issuedWorkContext.get.continuationId
        before.stage shouldBe "Implement"
        before.selected.map(_.identity) shouldBe Vector("compile", "test-only", "fix-check")
        val partial = root.resolve("repository/src/test/scala/Partial.scala")
        Files.createDirectories(partial.getParent)
        Files.writeString(partial, "// SPECIFICATION-OWNED partial source\n")
        val necessary = root.resolve("repository").resolve(added.head)
        Files.createDirectories(necessary.getParent)
        Files.writeString(necessary, "// SPECIFICATION-OWNED necessary bridge\n")
        def describe(paths: Vector[String], session: String = started.sessionId) = call("describeDelegateTaskScopeCorrection",
          List(Property("input", V.DelegateTaskScopeCorrectionDescriptionInput(session, old, paths, Vector.empty), None)))
        When("the actual deferred description inspects the necessary new literal path")
        val described = read_value[V.DelegateTaskScopeCorrectionView](take(describe(added)))
        described.outcome shouldBe "decision-required"
        val target = take(DelegateTaskScopeCorrection.targetC(described.target))
        val correction = V.DelegateTaskScopeCorrectionInput(started.sessionId, old, described.target, "scope-correction", "participant",
          "scope-correction-key", V.WorkflowArtifactReference("SPECIFICATION-OWNED-scope-direction", "1"))
        def fields(selected: V.DelegateTaskScopeCorrectionInput) = List(Property("input", selected, None))
        // Inspect the actual standard runtime's invocation-local Git data, rather
        // than rebuilding equivalent selection data in the fixture.
        def selectedPaths(incoming: Request): Vector[String] = {
          var paths = Vector.empty[String]
          prepare { setup => for {
            services <- StandardWorkflowRuntime.bindForCallC(ComponentCreate(owner, ComponentOrigin.Builtin),
              component.initializationParameters, incoming, setup.core)
            operation = services.head.operations.operations.toVector.find(_.name == incoming.operation).get
            command <- operation.createOperationRequest(incoming).map(_.asInstanceOf[CommandAction])
            id <- GitSelectionId.parseC("delegate-task")
            _ = { paths = RuntimeGitSelections.selected(setup.core.copy(action = command), GitOperation.CommitSelected(id)).get.paths.map(_.value) }
          } yield () }
          paths
        }
        Then("description widens inspection only and Git retains the originally admitted paths")
        selectedPaths(request("describeDelegateTaskScopeCorrection", List(Property("input",
          V.DelegateTaskScopeCorrectionDescriptionInput(started.sessionId, old, added, Vector.empty), None)))) shouldBe before.input.ownedPaths
        selectedPaths(request("correctDelegateTaskScope", fields(correction))) shouldBe before.input.ownedPaths
        call("correctDelegateTaskScope", fields(correction)).isSuccess shouldBe false
        Vector(Vector("../escape"), Vector("private/secret.scala"), Vector(added.head, added.head)).foreach(paths =>
          describe(paths).isSuccess shouldBe false)
        describe(added, "foreign-session").isSuccess shouldBe false
        advance().issuedWorkContext shouldBe issued.issuedWorkContext
        state(advance()) shouldBe before
        Given("an independently provisioned complete specification-owned correction selection")
        provision(take(NativeDelegateTaskScopeCorrectionInput.prepareC(options ++ Map("selection-id" -> correction.selectionId,
          "idempotency-key" -> correction.idempotencyKey), _ => correction.toRecord().toJsonStringC)))
        Vector(correction.copy(participant = "foreign"), correction.copy(target = "{}"),
          correction.copy(target = target.copy(additionalOwnedPaths = Vector("project/Other.scala")).asJson.noSpaces),
          correction.copy(target = target.copy(submissionConditions = Some(Vector("behavior"))).asJson.noSpaces),
          correction.copy(target = target.copy(frontierRevision = target.frontierRevision + 1).asJson.noSpaces)).foreach { bad =>
          call("correctDelegateTaskScope", fields(bad)).isSuccess shouldBe false
        }
        When("the public Resource form admits that exact selected correction")
        val resource = root.resolve("correction.json")
        Files.writeString(resource, take(correction.toRecord().toJsonStringC))
        value[V.DelegateTaskScopeCorrectionView]("correctDelegateTaskScope",
          List(Property("inputResource", resource.toUri.toASCIIString, None))).outcome shouldBe "corrected-awaiting-advance"
        value[V.DelegateTaskScopeCorrectionView]("correctDelegateTaskScope", fields(correction)).outcome shouldBe "already-corrected"
        val next = advance()
        val after = state(next)
        Then("only ordinary Advance issues the successor and retains all original obligations and check state")
        next.sessionId shouldBe issued.sessionId
        next.issuedWorkContext.get.continuationId should not be old
        after.input.ownedPaths shouldBe before.input.ownedPaths ++ added
        after.input.copy(ownedPaths = before.input.ownedPaths, sourceReferences = before.input.sourceReferences) shouldBe before.input
        after.copy(input = before.input) shouldBe before
        after.input.conditions shouldBe before.input.conditions
        after.input.submissionConditions shouldBe before.input.submissionConditions
        after.input.scalaLint shouldBe "required"
        after.receipts shouldBe empty
        after.commit shouldBe None
        after.generation shouldBe before.generation
        after.automaticCount shouldBe before.automaticCount
        after.ordinaryCount shouldBe before.ordinaryCount
        selectedPaths(request("advanceDelegateTask", List(Property("sessionId", started.sessionId, None)))) shouldBe after.input.ownedPaths
        // A fresh description of the successor exposes the retained native
        // frontier; its append-only prefix remains the original native history.
        val successorDescription = value[V.DelegateTaskScopeCorrectionView]("describeDelegateTaskScopeCorrection", List(Property("input",
          V.DelegateTaskScopeCorrectionDescriptionInput(started.sessionId, next.issuedWorkContext.get.continuationId,
            Vector("docs/Next.md"), Vector.empty), None)))
        val successorTarget = take(DelegateTaskScopeCorrection.targetC(successorDescription.target))
        successorTarget.frontier.hcursor.get[Vector[Json]]("history").toOption.get.take(
          target.frontier.hcursor.get[Vector[Json]]("history").toOption.get.size) shouldBe
          target.frontier.hcursor.get[Vector[Json]]("history").toOption.get
        advance().issuedWorkContext shouldBe next.issuedWorkContext
        Files.readString(partial) shouldBe "// SPECIFICATION-OWNED partial source\n"
        Files.readString(necessary) shouldBe "// SPECIFICATION-OWNED necessary bridge\n"
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      }
    }
  }
}
