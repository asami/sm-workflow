/**
 * @since   Oct. 26, 2026
 *  version Oct. 27, 2026
 * @version Nov.  6, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import cats.data.NonEmptyVector
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.action.{Action, ActionCall, CommandAction}
import org.goldenport.cncf.processexecution.{GitOperation, GitSelectionId}
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec as spec
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.preparation.NativeGoalTaskStartInput
import org.simplemodeling.textus.smworkflow.caller.NativeWorkflowCallerExecution
import StandaloneFixExecution as S
import StandaloneFixExecution.given

/** Typed host configuration only; these descriptors are specification data,
  * not observations of a provider or execution authority.
  */
private[admission] object BoundedWorkExecutionAdmissionFixture {
  val profile = WorkflowConcreteExecutionProfile("bounded-spec-profile", "spec-provider", "spec-model", Map.empty)
  val origin = GoalPhaseNativeExecution.Origin(WorkflowExecutorDescriptor("bounded-spec-origin", profile), WorkflowExecutionPreference())
  val classes = Vector("CodingProgrammingStandard", "DesignStandard", "ReviewStandard")
  def mappingC(supportedClasses: Vector[String] = classes): Consequence[WorkflowExecutionProfileMapping] =
    WorkflowExecutionProfileMapping.createC("bounded-spec-mapping", "1", Vector(profile),
      supportedClasses.map(name => WorkflowExecutionProfileMapping.ClassBinding(name, profile.identity)))
  private val _profile = Json.obj("identity" -> Json.fromString(profile.identity), "provider" -> Json.fromString(profile.provider),
    "model" -> Json.fromString(profile.model), "controls" -> Json.obj())
  val parameters: Map[String, String] = Map(
    WorkflowExecutionProfileParameters.declaration.name -> Json.obj(
      "schemaVersion" -> Json.fromString(WorkflowExecutionProfileParameters.schemaVersion),
      "identity" -> Json.fromString("bounded-spec-mapping"), "revision" -> Json.fromString("1"),
      "profiles" -> Json.arr(_profile), "classes" -> Json.fromValues(classes.map(name => Json.obj(
        "reasoningClass" -> Json.fromString(name), "profile" -> Json.fromString(profile.identity))))).noSpaces,
    GoalPhaseNativeExecution.declaration.name -> Json.obj("schemaVersion" -> Json.fromString(GoalPhaseNativeExecution.schemaVersion),
      "current" -> Json.obj("contextIdentity" -> Json.fromString(origin.current.contextIdentity), "profile" -> _profile),
      "preference" -> Json.obj("forceInline" -> Json.False, "isolateContext" -> Json.False)).noSpaces)
  val logicalParameters: Map[String, String] = Map(
    GoalPhaseNativeExecution.logicalOrigin.name -> "bounded-spec-caller",
    GoalPhaseDispatchPolicy.declaration.name -> GoalPhaseDispatchPolicy.revision)
  val logicalContext = GoalPhaseOperatingProfiles.Context(logicalOrigin = Some("bounded-spec-caller"),
    dispatchPolicy = Some(GoalPhaseDispatchPolicy.defaultPolicy))
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class BoundedWorkExecutionAdmissionSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  import BoundedWorkExecutionAdmissionFixture.*
  override protected def standard_runtime: Boolean = true
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    NativeCommonDelegateTaskFixture.processPlan(root, focused = false)
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail("Bounded execution specification admission failed: " + other)
  }
  private def _input(kind: String, task: Boolean): S.Input = S.Input("sm-workflow", "bounded-request", "Adopted bounded objective",
    Vector("src"), Vector("focused"), Vector("behavior"), Vector(ContextReference("adopted", "1")), false,
    kind = kind, scalaLint = "off", task = if (task) Some(S.Task("direct-instruction", Vector.empty)) else None)
  private def _host(profiles: Option[WorkflowExecutionProfileMapping], origin: Option[GoalPhaseNativeExecution.Origin]) =
    BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
      "sm-workflow", "unused-before-preflight", "main", Vector("src"), Vector.empty, profiles, origin, None, None, None)

  private def _assert_same_instance(actual: WorkflowInstancePersistence.InstanceRecord,
    expected: WorkflowInstancePersistence.InstanceRecord): Unit = {
    actual.definition.workflowIdentity shouldBe expected.definition.workflowIdentity
    actual.definition.workflowRevision shouldBe expected.definition.workflowRevision
    actual.definition.sourceCorrelation shouldBe expected.definition.sourceCorrelation
    actual.definition.producerAbiIdentity shouldBe expected.definition.producerAbiIdentity
    actual.definition.workflowAbiIdentity shouldBe expected.definition.workflowAbiIdentity
    actual.definition.bootstrapAbiIdentity shouldBe expected.definition.bootstrapAbiIdentity
    actual.definition.producerRevision shouldBe expected.definition.producerRevision
    actual.copy(definition = expected.definition) shouldBe expected
  }

  private def _assert_bounded_selections(core: ActionCall.Core): Unit = {
    val operation = new spec.OperationDefinition {
      val specification = spec.OperationDefinition.Specification(name = "selectionProbe",
        request = spec.RequestDefinition.empty, response = spec.ResponseDefinition.void)
      def createOperationRequest(incoming: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
        val request = incoming
        def createCall(call: ActionCall.Core): ActionCall = fail("Selection data probe must never execute")
      })
    }
    val service = spec.ServiceDefinition("SelectionProbe", spec.OperationDefinitionGroup(NonEmptyVector.one(operation)))
    val paths = Vector("docs/phase.md")
    Vector("goal-task", "delegate-task").foreach { identity =>
      val bound = _take(if (identity == "goal-task") RuntimeGitSelections.boundedC(service, paths, "request", "Objective")
        else RuntimeGitSelections.boundedC(service, paths, "request", "Objective", identity))
      val action = _take(bound.operations.operations.head.createOperationRequest(Request.of(component = org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name, service = "SelectionProbe",
        operation = "selectionProbe"))).asInstanceOf[Action]
      val selection = _take(GitSelectionId.parseC(identity))
      val selected = RuntimeGitSelections.selected(core.copy(action = action), GitOperation.CommitSelected(selection)).get
      selected.identity shouldBe selection
      selected.paths.map(_.value) shouldBe paths
      selected.message shouldBe Some("request: Objective")
      val other = _take(GitSelectionId.parseC(if (identity == "goal-task") "delegate-task" else "goal-task"))
      RuntimeGitSelections.selected(core.copy(action = action), GitOperation.CommitSelected(other)) shouldBe None
    }
    RuntimeGitSelections.boundedC(service, paths, "request", "Objective", " bad selection ").isSuccess shouldBe false
  }

  "Fresh bounded work execution admission" should {
    "resolve only the closed standard check IDs with their actual typed ordinal bindings" in {
      Given("standard check IDs without request-supplied classes or command arguments")
      val ids = Vector("compile", "test-only", "test", "fix-check")
      When("normal deferred composition resolves those IDs")
      val checks = _take(StandardWorkflowRuntime.standardChecksC(ids))
      Then("focused execution keeps TestOnly and each declared host ordinal")
      checks.map(_.identity) shouldBe ids
      checks.map(_.ordinal) shouldBe Vector(1, 2, 3, 4)
      checks.map(_.kind) shouldBe Vector(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Kind.TestOnly,
        WorkflowSbtValidation.Kind.Test, WorkflowSbtValidation.Kind.FixCheck)
      StandardWorkflowRuntime.standardChecksC(Vector("arbitrary-suite")).isSuccess shouldBe false
    }

    "admit logical destinations without invented models while keeping standalone semantic and validation-only routes unchanged" in {
      Given("the real logical context and standard policy, with no concrete execution pair")
      val logical = _host(None, None).copy(operatingContext = Some(logicalContext))
      Vector("code", "documentation").foreach { kind =>
        When("a new Task destination preflights all prospective implementation repair and review intent")
        GoalTaskNativeRun._preflight_execution_c(_input(kind, true), logical).isSuccess shouldBe true
        Then("missing origin or policy and contradictory concrete context still refuse")
        Vector(logicalContext.copy(logicalOrigin = None), logicalContext.copy(dispatchPolicy = None),
          logicalContext.copy(logicalOrigin = Some(" ")), logicalContext.copy(logicalOrigin = null)).foreach { context =>
          GoalTaskNativeRun._preflight_execution_c(_input(kind, true), logical.copy(operatingContext = Some(context))).isSuccess shouldBe false
        }
        StandaloneFixNativeRun._preflight_execution_c(_input(kind, false), logical).isSuccess shouldBe false
      }
      val concrete = _take(mappingC())
      GoalTaskNativeRun._preflight_execution_c(_input("code", true), _host(Some(concrete), Some(origin))
        .copy(operatingContext = Some(logicalContext.copy(executionProfiles = Some(concrete), executionOrigin = Some(origin))))).isSuccess shouldBe false
      val validation = _input("validation", false)
      StandaloneFixNativeRun._preflight_execution_c(validation, logical).isSuccess shouldBe true
      GoalPhaseNativeExecution.validatePairC(Some(concrete), None).isSuccess shouldBe false
    }

    "retain code documentation and independent review captures in the owning bounded Work codec and preserve historical omission" in {
      Given("fresh bounded semantic intent and a shared logical operating context")
      Vector("code", "documentation").foreach { kind =>
        Vector("Implement", "Repair", "Review", "DeliveryAcceptance").foreach { stage =>
          val input = _input(kind, true)
          val requirement = _take(GoalTaskNativeRun.executionRequirementC(input, stage))
          val classification = _take(GoalTaskNativeRun._classification_c(input, stage))
          When("the owning fresh capture and existing Work codec retain the assignment together")
          val (resolved, capture) = _take(GoalPhaseOperatingProfiles.captureFreshC(logicalContext, stage,
            Some(classification), requirement, None, stage == "Review"))
          val state = _take(S.startC(input, "participant", Vector(GoalPhaseNativeValidation.Check("focused", 1)))).copy(stage = stage)
          val legacy = S.Work("bounded-codec", "2", stage, state)
          val current = legacy.copy(operatingProfile = Some(capture))
          val codec = GoalTaskNativeRun.inputCodec
          Then("capture and original semantic class survive, with delegated independent Review and no provider facts")
          codec.decode(codec.encode(current)) shouldBe Right(current)
          capture.reasoningClass shouldBe requirement.reasoningClass.map(_.value)
          capture.dispatch.get.candidates shouldBe Vector("ordinary", "difficult", "hardest")
          capture.configured shouldBe None
          capture.selected shouldBe None
          resolved.disposition.get.placement shouldBe ExecutionPlacement.Delegated
          resolved.disposition.get.independence shouldBe (if (stage == "Review") ExecutionIndependence.Required else ExecutionIndependence.Optional)
          val old = codec.encode(legacy)
          old.hcursor.downField("payload").downField("operatingProfile").focus shouldBe None
          codec.decode(old) shouldBe Right(legacy)
          codec.encode(codec.decode(old).toOption.get) shouldBe old
          Vector(Json.Null, Json.obj(), Json.fromString("not-a-capture"),
            current.copy(operatingProfile = Some(capture.copy(reasoningClass = Some(" invalid")))).asJson.hcursor.get[Json]("operatingProfile").toOption.get)
            .foreach { malformed =>
              codec.decode(old.mapObject(_.add("payload", legacy.asJson.mapObject(_.add("operatingProfile", malformed))))).isLeft shouldBe true
            }
        }
      }
    }

    "start a managed logical GoalTask and recollect its immutable pending work after host settings change" in {
      Given("an actual managed SQLite host and a passively prepared documentation Task")
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/bounded-logical").toAbsolutePath), "episode-").toRealPath()
      val input = V.GoalTaskInput("sm-workflow", "logical-task", "Retain the adopted documentation meaning",
        NonEmptyVector.of("docs/phase.md"), NonEmptyVector.of("test"), NonEmptyVector.of("behavior"),
        NonEmptyVector.of(V.WorkflowArtifactReference("explicit-documentation-direction", "1")), false,
        "documentation", "off", 10, "direct-instruction", Vector.empty)
      val prepared = _take(NativeGoalTaskStartInput.prepareC(Map("input" -> "selected.json", "selection-id" -> "logical-selection",
        "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "logical-start",
        "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
      var pending = Option.empty[V.GoalTaskView]
      _with_runtime_(root, "LogicalTask", hostParameters = logicalParameters) { (owner, component, actor, prepare) =>
        io.circe.parser.parse(Files.readString(root.resolve(".textus/config.json"))).toOption.get.hcursor
          .get[String](ThreeWorkflowRuntimeParameters.definition.name).toOption shouldBe None
        prepare(call => call.setupC(_setup_commands_(root, "LogicalTask").size).flatMap { _ =>
          _assert_bounded_selections(call.core)
          val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          call.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        })
        def _invoke_(operation: String, properties: List[Property]) = read_value[V.GoalTaskView](_take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "GoalTaskService", operation = operation, properties = properties), actor)))
        When("normal Start and Advance issue the initial Implement assignment")
        val start = _invoke_("startGoalTask", List(Property("input", input, None), Property("selectionId", "logical-selection", None),
          Property("participant", "participant", None), Property("idempotencyKey", "logical-start", None)))
        val issued = _invoke_("advanceGoalTask", List(Property("sessionId", start.sessionId, None)))
        pending = Some(issued)
        Then("the actual public Request retains its capture and native requirement")
        issued.kind shouldBe "Request"
        val work = io.circe.parser.parse(_take(issued.work.get.toJsonStringC)).toOption.get
        val contextjson = io.circe.parser.parse(_take(issued.issuedWorkContext.get.context.toJsonStringC)).toOption.get
        val publicclue = "SPECIFICATION-OWNED logical GoalTask capture: actualPublicWorkJSON=" + work.noSpaces +
          "; issuedContextJSON=" + contextjson.noSpaces
        val normalizedjson = GoalPhaseNativeMetadata.normalizePublicJson(work).fold(
          reason => fail(publicclue + "; normalizationFailure=" + reason), identity)
        val decodeclue = publicclue + "; normalizedJSON=" + normalizedjson.noSpaces
        val normalized = normalizedjson.as[S.Work].fold(
          failure => fail(decodeclue + "; decodingFailure=" + failure.message), identity)
        val captureclue = decodeclue + "; existingCapture=" + normalized.operatingProfile
        normalized.kind shouldBe "Implement"
        withClue(captureclue) { normalized.operatingProfile should not be empty }
        val capture = normalized.operatingProfile.get
        withClue(captureclue + "; actualCapture=" + capture) { capture.dispatch should not be empty }
        capture.dispatch.get.candidates shouldBe Vector("ordinary", "difficult", "hardest")
        val publiccapture = GoalPhaseNativeMetadata.normalizePublicJson(contextjson).toOption.get.hcursor
          .get[GoalPhaseOperatingProfiles.Capture]("operatingProfile").toOption.get
        publiccapture shouldBe normalized.operatingProfile.get
        issued.issuedWorkContext.get.executionRequirement.getRecord("disposition").get.getString("originContextIdentity") shouldBe Some("bounded-spec-caller")
        When("the legacy caller is given this actual public logical Request and a valid specification-owned concrete mapping")
        val refused = NativeWorkflowCallerExecution.prepareC(issued.issuedWorkContext.get, _take(mappingC()),
          origin.current.copy(contextIdentity = "bounded-spec-caller"))
        Then("the retained logical-dispatch gate refuses before preparing any legacy harness session")
        refused.isSuccess shouldBe false
        refused.toString should include("Retained logical dispatch requires the versioned goal-phase-dispatch caller adapter")
      }
      val changed = logicalParameters ++ Map(GoalPhaseNativeExecution.logicalOrigin.name -> "later-caller",
        GoalPhaseDispatchPolicy.declaration.name -> Json.obj("revision" -> GoalPhaseDispatchPolicy.revision.asJson, "edges" -> Json.obj()).noSpaces)
      _with_runtime_(root, "LogicalTask", hostParameters = changed) { (owner, component, actor, _) =>
        val issued = pending.get
        When("the same managed Session is reopened under changed fresh-work settings")
        val recollected = read_value[V.GoalTaskView](_take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "GoalTaskService", operation = "advanceGoalTask", properties = List(Property("sessionId", issued.sessionId, None))), actor)))
        Then("both pending Work and actual issued context remain exactly the stored assignment")
        recollected.work shouldBe issued.work
        recollected.issuedWorkContext shouldBe issued.issuedWorkContext
        val reference = V.WorkflowArtifactReference("logical-task-result", "1")
        val result = V.GoalTaskWorkResult("completed", "Specification-owned adopted documentation result", Vector.empty,
          NonEmptyVector.of(V.GoalTaskCondition("behavior", true, "Adopted condition retained")), Vector.empty, None, None)
        def _submit_(profile: String, context: V.WorkflowIssuedWorkContext = issued.issuedWorkContext.get) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "GoalTaskService", operation = "submitGoalTaskWorkResult", properties = List(
            Property("sessionId", issued.sessionId, None), Property("issuedWorkContext", context, None), Property("result", result, None),
            Property("resultReference", reference, None), Property("executionEvidence", V.WorkflowExecutionEvidence(Vector(reference),
              Some("spec-logical-implementer"), None, Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", "spec-logical-implementer", profile))), None))), actor)
        When("actual Submit checks a crossed context and an execution outside the captured chain")
        val crossed = _submit_("ordinary", issued.issuedWorkContext.get.copy(expectedRevision = "999"))
        val outside = _submit_("outside-chain")
        Then("both the crossed revision and the outside-chain execution refuse")
        crossed.isSuccess shouldBe false
        outside.isSuccess shouldBe false
        When("the retained candidate submits with its original captured route")
        val accepted = _submit_("difficult")
        Then("a retained candidate is admitted using the original capture despite changed live settings")
        accepted.isSuccess shouldBe true
      }
    }
    "refuse first Advance without issuing work or changing native history when the admitted logical origin or policy was removed after Start" in {
      Vector(GoalPhaseNativeExecution.logicalOrigin.name, GoalPhaseDispatchPolicy.declaration.name).foreach { removed =>
        Given("a real logical Start whose first semantic WorkOrder has not been issued")
        val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/bounded-fresh-preflight").toAbsolutePath), "episode-").toRealPath()
        val input = V.GoalTaskInput("sm-workflow", "fresh-preflight-task", "Retain the adopted documentation meaning",
          NonEmptyVector.of("docs/phase.md"), NonEmptyVector.of("test"), NonEmptyVector.of("behavior"),
          NonEmptyVector.of(V.WorkflowArtifactReference("explicit-documentation-direction", "1")), false,
          "documentation", "off", 10, "direct-instruction", Vector.empty)
        val prepared = _take(NativeGoalTaskStartInput.prepareC(Map("input" -> "selected.json", "selection-id" -> "fresh-selection",
          "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "fresh-start",
          "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
        var started = Option.empty[V.GoalTaskView]
        var before = Option.empty[WorkflowInstancePersistence.InstanceRecord]
        _with_runtime_(root, "FreshTask", hostParameters = logicalParameters) { (owner, component, actor, prepare) =>
          prepare(call => call.setupC(_setup_commands_(root, "FreshTask").size).flatMap { _ =>
            val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
            call.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
          })
          started = Some(read_value[V.GoalTaskView](_take(owner.executeOperationResponseInContext(Request.of(
            component = component.name, service = "GoalTaskService", operation = "startGoalTask", properties = List(
              Property("input", input, None), Property("selectionId", "fresh-selection", None),
              Property("participant", "participant", None), Property("idempotencyKey", "fresh-start", None))), actor))))
          prepare(call => for {
            storage <- call.taskStorageC()
            instances <- storage.bindInstancesC()(using call.executionContext)
            current <- instances.loadC(WorkflowInstancePersistence.InstanceIdentity(started.get.sessionId))(using call.executionContext)
            _ = before = current
          } yield ())
          before.get.suspension shouldBe None
        }
        _with_runtime_(root, "FreshTask", hostParameters = logicalParameters - removed) { (owner, component, actor, prepare) =>
          When("the first Advance uses its actual host after " + removed + " is removed")
          val refused = owner.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalTaskService",
            operation = "advanceGoalTask", properties = List(Property("sessionId", started.get.sessionId, None))), actor)
          Then("fresh eligibility refuses and the exact native frontier and history remain unchanged with no new issued row")
          refused.isSuccess shouldBe false
          prepare(call => for {
            storage <- call.taskStorageC()
            instances <- storage.bindInstancesC()(using call.executionContext)
            current <- instances.loadC(WorkflowInstancePersistence.InstanceIdentity(started.get.sessionId))(using call.executionContext)
            issued <- storage.bindIssuedC(GoalTaskNativeRun.completionOperation, GoalTaskNativeRun.inputCodec)
            missing <- issued.loadC(ContinuationIdentity(started.get.sessionId + ":standalone:" + before.get.revision.next.value))(using call.executionContext)
            _ = current.isDefined shouldBe true
            _ = _assert_same_instance(current.get, before.get)
            _ = missing shouldBe None
          } yield ())
        }
      }
    }

    "project retained concrete Task capture through the actual public context and keep the original caller selection check" in {
      Given("a managed Task with a valid specification-owned concrete mapping and its matching genuine fixture origin")
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/bounded-concrete-context").toAbsolutePath), "episode-").toRealPath()
      val input = V.GoalTaskInput("sm-workflow", "concrete-context-task", "Retain the adopted documentation meaning",
        NonEmptyVector.of("docs/phase.md"), NonEmptyVector.of("test"), NonEmptyVector.of("behavior"),
        NonEmptyVector.of(V.WorkflowArtifactReference("explicit-documentation-direction", "1")), false,
        "documentation", "off", 10, "direct-instruction", Vector.empty)
      val prepared = _take(NativeGoalTaskStartInput.prepareC(Map("input" -> "selected.json", "selection-id" -> "concrete-selection",
        "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "concrete-start",
        "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
      _with_runtime_(root, "ConcreteTask", hostParameters = parameters) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(root, "ConcreteTask").size).flatMap { _ =>
          val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          call.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        })
        def _invoke_(operation: String, properties: List[Property]) = read_value[V.GoalTaskView](_take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "GoalTaskService", operation = operation, properties = properties), actor)))
        When("normal Start and Advance return the actual concrete Request and the existing caller prepares it")
        val started = _invoke_("startGoalTask", List(Property("input", input, None), Property("selectionId", "concrete-selection", None),
          Property("participant", "participant", None), Property("idempotencyKey", "concrete-start", None)))
        val issued = _invoke_("advanceGoalTask", List(Property("sessionId", started.sessionId, None)))
        val normalized = GoalPhaseNativeMetadata.normalizePublicJson(io.circe.parser.parse(_take(issued.work.get.toJsonStringC)).toOption.get)
          .toOption.get.as[S.Work].toOption.get
        val publiccapture = GoalPhaseNativeMetadata.normalizePublicJson(io.circe.parser.parse(_take(issued.issuedWorkContext.get.context.toJsonStringC)).toOption.get)
          .toOption.get.hcursor.get[GoalPhaseOperatingProfiles.Capture]("operatingProfile").toOption.get
        val caller = _take(NativeWorkflowCallerExecution.prepareC(issued.issuedWorkContext.get, _take(mappingC()), origin.current))
        Then("the exact retained concrete capture reaches the original selection check without an invented model")
        publiccapture shouldBe normalized.operatingProfile.get
        caller.session.primary shouldBe profile
        caller.session.mappingIdentity shouldBe publiccapture.mappingIdentity.get
        val crossedcapture = publiccapture.copy(selected = publiccapture.selected.map(_.copy(model = "contradictory-spec-model")))
        val crossedcontext = issued.issuedWorkContext.get.copy(context = issued.issuedWorkContext.get.context.removeKeys(Set("operatingProfile")) ++
          Record.data("operatingProfile" -> GoalPhaseNativeMetadata.record(crossedcapture.asJson)))
        When("the public concrete selection contradicts the retained configured mapping")
        val refused = NativeWorkflowCallerExecution.prepareC(crossedcontext, _take(mappingC()), origin.current)
        Then("the existing retained-selection check refuses the contradiction")
        refused.isSuccess shouldBe false
        refused.toString should include("caller execution session differs from the retained profile capture")
      }
    }

    "derive code, documentation and independent review intent while retaining each entry's exact logical capability" in {
      Given("the Task and standalone Required SPI and adopted code or documentation kind")
      Vector(false, true).foreach { task =>
        val runtime = if (task) GoalTaskNativeRun else StandaloneFixNativeRun
        val wire = if (task) "goal-task" else "standalone-fix"
        Vector("code", "documentation").foreach { kind =>
          Vector("Repair", "Review").concat(if (task) Vector("Implement") else Vector.empty).foreach { stage =>
            When("the native owner derives a fresh " + stage + " requirement")
            val requirement = _take(runtime.executionRequirementC(_input(kind, task), stage))
            Then("semantic classification preserves the Required SPI and review boundary")
            val operation = if (stage == "Review") "review" else if (stage == "Implement") "implementation" else "repair"
            requirement.capabilities shouldBe Vector(CapabilityRequirement(wire + "-" + operation + "-capability"))
            requirement.reasoningClass shouldBe Some(ReasoningClassReference(
              if (stage == "Review") "ReviewStandard" else if (kind == "code") "CodingProgrammingStandard" else "DesignStandard"))
            requirement.reasoning shouldBe ReasoningLevel.Standard
            requirement.risk shouldBe RiskLevel("SOURCE_IMPLEMENTATION")
            requirement.reviewRequired shouldBe (stage == "Review")
            requirement.disposition shouldBe None
          }
        }
      }
    }

    "require the adopted implementation or repair class and possible review class before a new Start" in {
      Given("a complete typed origin and mappings with different supported semantic classes")
      Vector(false, true).foreach { task =>
        val runtime = if (task) GoalTaskNativeRun else StandaloneFixNativeRun
        Vector("code", "documentation").foreach { kind =>
          val input = _input(kind, task)
          val requiredclass = if (kind == "code") "CodingProgrammingStandard" else "DesignStandard"
          val exact = _take(mappingC(Vector(requiredclass, "ReviewStandard")))
          When("new Start preflights only the classes needed by its adopted kind")
          runtime.preflightExecutionC(input, Some(exact), Some(origin)).isSuccess shouldBe true
          Then("neither a missing implementation class nor missing possible scoped review is usable")
          Vector(Vector(requiredclass), Vector("ReviewStandard")).foreach { supported =>
            runtime.preflightExecutionC(input, Some(_take(mappingC(supported))), Some(origin)).isSuccess shouldBe false
          }
        }
      }
    }

    "reject absent, partial, invalid-origin, incompatible placement and inadequate host configuration before native creation or managed effects" in {
      Given("new Task, standalone test-fix and review-origin requests with no execution core or effect ports")
      val complete = _take(mappingC())
      val bad: Vector[(Option[WorkflowExecutionProfileMapping], Option[GoalPhaseNativeExecution.Origin])] = Vector((None, None), (Some(complete), None), (None, Some(origin)),
        (Some(complete), Some(origin.copy(current = null))), (Some(complete), Some(origin.copy(preference = null))),
        (Some(complete), Some(origin.copy(current = origin.current.copy(profile = null)))),
        (Some(complete), Some(origin.copy(current = origin.current.copy(contextIdentity = "")))),
        (Some(complete), Some(origin.copy(preference = WorkflowExecutionPreference(forceInline = true)))),
        (Some(complete), Some(origin.copy(preference = WorkflowExecutionPreference(forceInline = true, isolateContext = true)))),
        (Some(_take(mappingC(Vector("ReviewStandard")))), Some(origin)),
        (Some(_take(mappingC(Vector("CodingProgrammingStandard", "DesignStandard")))), Some(origin)))
      val revieworigin = StandaloneReviewFix.Origin(ContextReference("original-review", "1"), "Adopted original review context",
        Vector(StandaloneReviewFix.Finding("finding-1", "Repair adopted finding", Vector("src/Behavior.scala"), Vector.empty)))
      val inputs = Vector(_input("code", true), _input("documentation", true), _input("code", false),
        _input("documentation", false).copy(reviewOrigin = Some(revieworigin)))
      inputs.foreach { input =>
        val runtime = if (input.task.nonEmpty) GoalTaskNativeRun else StandaloneFixNativeRun
        _take(S.inputC(input))
        bad.foreach { (profiles, from) =>
          When("Start is asked to create new work with unusable execution configuration")
          // Null ports are deliberate sentinels: touching selection, core, Git,
          // persistence or process execution before rejection would fail this spec.
          val result = runtime.startC(input, "participant", "start-key", "selected", null,
            _host(profiles, from), null, null)
          Then("configuration rejection returns before any native or managed effect boundary is reached")
          result.isSuccess shouldBe false
        }
      }
    }
  }
}
