/**
 * @since   Oct. 27, 2026
 * @version Nov.  5, 2026
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
import org.scalatest.GivenWhenThen
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import org.simplemodeling.textus.smworkflow.preparation.{NativeDelegateTaskStartInput, NativeDelegateTaskAdoptionPreparation, NativeReviewPreparation}
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
final class DelegateTaskWorkflowSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
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
      NonEmptyVector.of("src/test/scala", "docs/delegate.md"), NonEmptyVector.of("compile", "focused"), NonEmptyVector.of("behavior"),
      NonEmptyVector.of(V.WorkflowArtifactReference("specification-adopted-source", "1")), false, "code", "off", 10,
      "direct-instruction", Vector.empty, V.DelegateTaskContract(c.requesterIdentity, c.destinationIdentity, c.returnDestination,
        c.componentIdentity, c.role, c.validationScope, NonEmptyVector.fromVectorUnsafe(c.permittedOperations), c.exclusions,
        NonEmptyVector.of(V.WorkflowArtifactReference(c.authorizationReferences.head.identity, c.authorizationReferences.head.revision)),
        c.publicationTarget, c.artifactCoordinates, c.consumerInstructions), Vector("behavior"))
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
    Files.createDirectories(root.resolve("repository/docs"))
    Files.writeString(root.resolve("repository/docs/delegate.md"), "# Accepted delegated work\n")
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
      goalPaths = goal.candidatePaths, goalMessages = Vector("Delegate scoped result"), goalSelections = Vector(Vector(_test, "docs/delegate.md")))
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
    "retain explicit initial evaluation timing while preserving complete reports and historical absent timing" in {
      Given("one complete public request with behavior validation commit and publication obligations")
      val publicinput = _input("destination", "initial-timing").copy(
        conditions = NonEmptyVector.of("behavior", "validation", "commit", "publication"), submissionConditions = Vector.empty)
      When("the new public default is admitted without a separate timing selection")
      val deferred = take(DelegateTask.inputC(publicinput))
      val state = take(S.startC(deferred, "participant", _checks))
      val report = deferred.conditions.map(id => S.Condition(id, false, "Actual runtime checks and delivery are not yet established"))
      val result = S.Result("completed", "Bounded implementation supplied for actual later verification", Vector.empty, report,
        Vector.empty, None, None)
      val work = S.Work("initial-timing", "2", "Implement", state)
      Then("empty timing is explicit and successful implementation still reports every later condition false")
      deferred.submissionConditions shouldBe Some(Vector.empty)
      S.admitResultC(work, result).isSuccess shouldBe true
      S.admitResultC(work, result.copy(conditions = report.dropRight(1))).isSuccess shouldBe false
      S.admitResultC(work, result.copy(conditions = report.map(_.copy(satisfied = true)))).isSuccess shouldBe false
      S.admitResultC(work, result.copy(conditions = report.updated(1, report(1).copy(satisfied = true)))).isSuccess shouldBe false
      When("an ordered subset or all original IDs is explicitly declared at initial admission")
      Vector(Vector("behavior"), Vector("behavior", "validation"), deferred.conditions).foreach { ids =>
        val input = take(DelegateTask.inputC(publicinput.copy(submissionConditions = ids)))
        val selectedstate = take(S.startC(input, "participant", _checks))
        val selectedreport = report.map(c => c.copy(satisfied = ids.contains(c.identity)))
        Then("exactly those IDs are evaluated at submission and both roles retain identical timing")
        S.admitResultC(work.copy(state = selectedstate), result.copy(conditions = selectedreport)).isSuccess shouldBe true
        val requester = take(DelegateTask.inputC(publicinput.copy(submissionConditions = ids,
          delegation = publicinput.delegation.copy(role = "requester"))))
        take(DelegateTask.startRequesterC(requester, "participant")).input.submissionConditions shouldBe Some(ids)
      }
      Vector(Vector("validation", "behavior"), Vector("behavior", "behavior"), Vector("foreign")).foreach { ids =>
        DelegateTask.inputC(publicinput.copy(submissionConditions = ids)).isSuccess shouldBe false
      }
      Given("an original durable input with no timing field")
      val legacy = deferred.copy(submissionConditions = None)
      val wire = legacy.asJson
      When("the original input codec decodes and re-encodes that retained input")
      val decoded = wire.as[S.Input].toOption.get
      Then("absence stays absent and continues to require every submission condition true")
      wire.hcursor.downField("submissionConditions").focus shouldBe None
      decoded shouldBe legacy
      decoded.asJson shouldBe wire
      val legacystate = take(S.startC(legacy, "participant", _checks))
      S.admitResultC(work.copy(state = legacystate), result).isSuccess shouldBe false
      S.admitResultC(work.copy(state = legacystate), result.copy(conditions = report.map(_.copy(satisfied = true)))).isSuccess shouldBe true
      S.inputC(deferred.copy(delegation = None)).isSuccess shouldBe false
      S.inputC(deferred.copy(delegation = None, task = None)).isSuccess shouldBe false
    }

    "classify final delivery judgment with implementation capability and its actual code or documentation class" in {
      Given("a delegated destination with original conditions to evaluate after actual effects")
      Vector("code", "documentation").foreach { kind =>
        val input = take(DelegateTask.inputC(_input("destination", "delivery-class"))).copy(kind = kind,
          delegation = Some(_contract("destination").copy(validationScope = if (kind == "code") "localized" else "documentation")))
        When("the bounded owner captures DeliveryAcceptance")
        val requirement = take(DelegateTaskNativeRun.executionRequirementC(input, "DeliveryAcceptance"))
        val classification = take(DelegateTaskNativeRun._classification_c(input, "DeliveryAcceptance"))
        val (resolved, capture) = take(GoalPhaseOperatingProfiles.captureFreshC(
          BoundedWorkExecutionAdmissionFixture.logicalContext, "DeliveryAcceptance", Some(classification), requirement, None, false))
        Then("the read-only judgment uses implementation intent without claiming independent Review")
        requirement.capabilities shouldBe Vector(WorkflowProtocolV1.CapabilityRequirement("delegate-task-implementation-capability"))
        requirement.reasoningClass.map(_.value) shouldBe Some(if (kind == "code") "CodingProgrammingStandard" else "DesignStandard")
        requirement.reviewRequired shouldBe false
        resolved.disposition.get.independence shouldBe WorkflowProtocolV1.ExecutionIndependence.Optional
        capture.dispatch.get.candidates shouldBe Vector("ordinary", "difficult", "hardest")
      }
    }

    "start a logical requester without destination mappings and retain actual Implement Review and Repair admission" in {
      Given("the real managed repository fixture, an admitted logical caller and no concrete model mapping")
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/delegate-logical").toAbsolutePath), "episode-").toRealPath()
      val original = ThreeWorkflowNormalHostFixture.definition(root)
      val host = original.copy(goal = original.goal.copy(candidatePaths = Vector("src/test/scala", "docs/delegate.md"),
        stepValidation = original.goal.stepValidation.copy(checks = _checks)))
      val parameters = (_parameters -- Vector(WorkflowExecutionProfileParameters.declaration.name, GoalPhaseNativeExecution.declaration.name)) ++
        BoundedWorkExecutionAdmissionFixture.logicalParameters
      _with_runtime_(root, "DelegateLogical", Some(host), parameters) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "DelegateLogical").size))
        def _invoke_(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "DelegateTaskService", operation = operation, properties = properties), actor)
        def _advance_(id: String) = read_value[V.DelegateTaskView](take(_invoke_("advanceDelegateTask", List(Property("sessionId", id, None)))))
        def _start_(role: String): V.DelegateTaskView = {
          val input = _input(role, "logical-request", publish = false).copy(reviewRequired = true)
          val selection = "logical-" + role
          val key = "logical-start-" + role
          val prepared = take(NativeDelegateTaskStartInput.prepareC(Map("input" -> "selected.json", "selection-id" -> selection,
            "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> key,
            "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
          prepare { call =>
            val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
            call.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
          }
          read_value[V.DelegateTaskView](take(_invoke_("startDelegateTask", List(Property("input", input, None),
            Property("selectionId", selection, None), Property("participant", "participant", None), Property("idempotencyKey", key, None)))))
        }
        def _captured_(view: V.DelegateTaskView, kind: String): S.Work = {
          val publicworkjson = _json(view.work.get)
          val contextjson = _json(view.issuedWorkContext.get.context)
          val publicclue = "SPECIFICATION-OWNED logical DelegateTask capture: actualPublicWorkJSON=" + publicworkjson.noSpaces +
            "; issuedContextJSON=" + contextjson.noSpaces
          val normalizedjson = GoalPhaseNativeMetadata.normalizePublicJson(publicworkjson).fold(
            reason => fail(publicclue + "; normalizationFailure=" + reason), identity)
          val decodeclue = publicclue + "; normalizedJSON=" + normalizedjson.noSpaces
          val work = normalizedjson.as[S.Work].fold(
            failure => fail(decodeclue + "; decodingFailure=" + failure.message), identity)
          val captureclue = decodeclue + "; existingCapture=" + work.operatingProfile
          work.kind shouldBe kind
          withClue(captureclue) { work.operatingProfile should not be empty }
          val capture = work.operatingProfile.get
          withClue(captureclue + "; actualCapture=" + capture) { capture.dispatch should not be empty }
          val publiccapture = GoalPhaseNativeMetadata.normalizePublicJson(contextjson).toOption.get
            .hcursor.get[GoalPhaseOperatingProfiles.Capture]("operatingProfile").toOption.get
          publiccapture shouldBe capture
          capture.dispatch.get.candidates shouldBe Vector("ordinary", "difficult", "hardest")
          capture.configured shouldBe None
          capture.selected shouldBe None
          capture.reasoningClass shouldBe Some(if (kind == "Review") "ReviewStandard" else "CodingProgrammingStandard")
          view.issuedWorkContext.get.executionRequirement.getRecord("disposition").get.getString("placement") shouldBe Some("DELEGATED")
          if (kind == "Review") view.issuedWorkContext.get.executionRequirement.getRecord("disposition").get.getString("independence") shouldBe Some("REQUIRED")
          work
        }
        def _evidence_(worker: String, reference: V.WorkflowArtifactReference, candidate: String = "ordinary", executor: Option[String] = None) =
          V.WorkflowExecutionEvidence(Vector(reference), Some(worker), None,
            Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", executor.getOrElse(worker), candidate)))
        def _submit_(view: V.DelegateTaskView, result: V.DelegateTaskWorkResult, worker: String,
          reference: V.WorkflowArtifactReference, candidate: String = "ordinary", context: Option[V.WorkflowIssuedWorkContext] = None,
          executor: Option[String] = None) =
          _invoke_("submitDelegateTaskWorkResult", List(Property("sessionId", view.sessionId, None), Property("result", result, None),
            Property("issuedWorkContext", context.getOrElse(view.issuedWorkContext.get), None), Property("resultReference", reference, None),
            Property("executionEvidence", _evidence_(worker, reference, candidate, executor), None)))
        def _review_evidence_(view: V.DelegateTaskView, result: V.DelegateTaskWorkResult, worker: String,
          reference: V.WorkflowArtifactReference, executor: Option[String] = None): Unit = prepare { call => for {
          prepared <- NativeReviewPreparation.delegateC(view.reviewContext.get, result, reference, _evidence_(worker, reference, executor = executor),
            "normal-development-host", call.executionContext.clock.instant().minusSeconds(1))
          _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
        } yield () }
        When("the requester starts and advances with only transport responsibility")
        val requester = _start_("requester")
        val dispatch = _advance_(requester.sessionId)
        Then("dispatch needs no destination execution mapping and produces no semantic WorkOrder")
        dispatch.kind shouldBe "Dispatch"
        dispatch.issuedWorkContext shouldBe None
        When("the destination starts and advances its actual implementation assignment")
        val destination = _start_("destination")
        val implement = _advance_(destination.sessionId)
        Then("the implementation has its retained captured route")
        _captured_(implement, "Implement")
        val path = root.resolve("repository").resolve(_test)
        Files.createDirectories(path.getParent)
        Files.writeString(path, """package org.simplemodeling.textus.smworkflow.p62authored
          |import org.scalatest.wordspec.AnyWordSpec
          |import org.scalatest.matchers.should.Matchers
          |import org.scalatest.GivenWhenThen
          |final class LineWriterSpec extends AnyWordSpec with Matchers with GivenWhenThen {
          |  "LineWriter" should { "retain the text and append one terminator" in {
          |    Given("nonempty and empty text")
          |    val text = "expected"
          |    val empty = ""
          |    When("LineWriter renders each text")
          |    val rendered = LineWriter.render(text)
          |    val blank = LineWriter.render(empty)
          |    Then("each result preserves its text and appends one terminator")
          |    rendered shouldBe "expected\n"
          |    blank shouldBe "\n"
          |  }}
          |}
          |""".stripMargin)
        val completed = V.DelegateTaskWorkResult("completed", "Specification-owned bounded implementation", Vector.empty,
          NonEmptyVector.of(V.DelegateTaskCondition("behavior", true, "Adopted behavior covered")), Vector.empty, None, None)
        val implementationreference = V.WorkflowArtifactReference("logical-implementation", "1")
        When("the actual destination Submit checks its stored capture and public context")
        val outside = _submit_(implement, completed, "spec-implementer", implementationreference, "outside-chain")
        val crossedcontext = _submit_(implement, completed, "spec-implementer", implementationreference,
          context = Some(implement.issuedWorkContext.get.copy(expectedRevision = "999")))
        Then("both an outside-chain candidate and a crossed native context refuse")
        outside.isSuccess shouldBe false
        crossedcontext.isSuccess shouldBe false
        When("the retained implementation candidate submits its actual result")
        val implemented = _submit_(implement, completed, "spec-implementer", implementationreference, "difficult")
        Then("the captured implementation is admitted")
        take(implemented)
        When("the destination advances after implementation")
        val review = _advance_(destination.sessionId)
        Then("independent Review retains its own captured route")
        _captured_(review, "Review")
        val finding = completed.copy(outcome = "repair", summary = "Specification-owned scoped finding",
          findings = Vector("finding-1: explain the focused coverage"),
          conditions = NonEmptyVector.of(V.DelegateTaskCondition("behavior", false, "Scoped clarification required")))
        val reusedworker = V.WorkflowArtifactReference("logical-reused-reviewer", "1")
        _review_evidence_(review, finding, "spec-implementer", reusedworker)
        When("the retained implementer submits the Review result with matching host evidence")
        val reused = _submit_(review, finding, "spec-implementer", reusedworker)
        Then("a candidate-chain member and independent host evidence cannot replace a genuinely different review worker")
        reused.isSuccess shouldBe false
        val crossedworker = V.WorkflowArtifactReference("logical-crossed-reviewer", "1")
        Given("independent host evidence matching a fresh reviewer label with the implementer's actual executor context")
        _review_evidence_(review, finding, "spec-fresh-reviewer", crossedworker, Some("spec-implementer"))
        When("that different worker label is submitted with the retained implementer executor")
        val crossed = _submit_(review, finding, "spec-fresh-reviewer", crossedworker, executor = Some("spec-implementer"))
        Then("the contradictory worker and executor identities refuse despite matching host review evidence")
        crossed.isSuccess shouldBe false
        val reviewreference = V.WorkflowArtifactReference("logical-independent-review", "1")
        When("an honest independent reviewer submits before independent host evidence is prepared")
        val missing = _submit_(review, finding, "spec-reviewer", reviewreference)
        Then("missing host evidence still refuses")
        missing.isSuccess shouldBe false
        Given("matching host evidence for an honest distinct worker and executor identity")
        _review_evidence_(review, finding, "spec-reviewer", reviewreference)
        When("the honest reviewer submits the actual assigned result")
        val reviewed = _submit_(review, finding, "spec-reviewer", reviewreference)
        Then("the independent result is admitted")
        reviewed.isSuccess shouldBe true
        take(reviewed)
        When("the destination advances the admitted independent finding")
        val repair = _advance_(destination.sessionId)
        Then("Repair retains its own captured route")
        _captured_(repair, "Repair")
        val repairreference = V.WorkflowArtifactReference("logical-repair", "1")
        When("a delayed review context is supplied for the actual fresh Repair")
        val delayed = _submit_(repair, completed, "spec-repairer", repairreference, context = review.issuedWorkContext)
        Then("the delayed context refuses before the current Repair can complete")
        delayed.isSuccess shouldBe false
        When("the retained Repair candidate submits its actual current result")
        val repaired = _submit_(repair, completed, "spec-repairer", repairreference, "hardest")
        Then("the captured Repair is admitted")
        take(repaired)
        When("the destination advances after the current Repair")
        val rereview = _advance_(destination.sessionId)
        Then("fresh Repair and subsequent independent Review keep their own captured assignment and native context")
        _captured_(rereview, "Review")
        rereview.issuedWorkContext.get.continuationId should not be review.issuedWorkContext.get.continuationId
        val repairworker = V.WorkflowArtifactReference("logical-repairer-review", "1")
        val accept = completed.copy(outcome = "accept", summary = "Specification-owned integrated review")
        _review_evidence_(rereview, accept, "spec-repairer", repairworker)
        When("the retained repair worker attempts the subsequent Review")
        val repairreview = _submit_(rereview, accept, "spec-repairer", repairworker)
        Then("the same repair worker still cannot supply independent Review")
        repairreview.isSuccess shouldBe false
      }
    }

    "admit dispatch-only requester input without requiring a destination mapping and still refuse contradictory contexts" in {
      Given("one admitted requester contract and an explicit host with no execution mapping")
      val input = take(DelegateTask.inputC(_input("requester", "requester-preflight", publish = false)))
      val host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"), "sm-workflow",
        "unused-before-preflight", "main", Vector("src/test/scala", "docs/delegate.md"), _checks, None, None, None, None, None)
      When("only the dispatcher's admitted identities input and operating-profile consistency are checked")
      DelegateTaskNativeRun._preflight_execution_c(input, host).isSuccess shouldBe true
      Then("the same missing execution routes refuse destination work and malformed requester context still refuses")
      DelegateTaskNativeRun._preflight_execution_c(input.copy(delegation = input.delegation.map(_.copy(role = "destination"))), host).isSuccess shouldBe false
      DelegateTaskNativeRun._preflight_execution_c(input, host.copy(operatingContext = Some(
        GoalPhaseOperatingProfiles.Context(logicalOrigin = Some(" "))))).isSuccess shouldBe false
      DelegateTaskNativeRun._preflight_execution_c(input.copy(delegation = input.delegation.map(c => c.copy(destinationIdentity = c.requesterIdentity))), host).isSuccess shouldBe false
    }

    "use public native work focused checks local commit publication return and separate requester adoption without duplicate effects" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/delegate-task").toAbsolutePath), "episode-").toRealPath()
      val original = ThreeWorkflowNormalHostFixture.definition(root)
      val host = original.copy(goal = original.goal.copy(candidatePaths = Vector("src/test/scala", "docs/delegate.md"),
        stepValidation = original.goal.stepValidation.copy(checks = _checks)))
      val recordPolicy = """{"targets":[{"family":"delegate-task","scope":"single-selected-request","paths":["docs/delegate.md"]}]}"""
      var legacySession = ""
      var implementationCommit = ""
      Given("one specification-owned request with mandatory completion records and a later legacy no-change request")
      _with_runtime_(root, "Delegate", Some(host), _parameters + (CompletionRecords.declaration.name -> recordPolicy)) { (owner, component, actor, prepare) =>
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
        When("the normal destination route accepts its checks and closes the selected implementation")
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
        implementationCommit = commit.identity
        Then("the same local commit contains the exact completion record without another validation batch")
        val recordText = Files.readString(root.resolve("repository/docs/delegate.md"))
        recordText should include ("<!-- sm-workflow completion " + destination.sessionId + " delegate-task single-selected-request -->")
        recordText should include ("Evidence: validation:compile")
        recordText should not include (commit.identity)
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
        legacySession = nochange.sessionId
        _state(nochange).commit.get.identity shouldBe commit.identity
        _state(nochange).commitExecution shouldBe None
        _state(nochange).candidate.get.paths shouldBe empty
        _state(nochange).delivery.flatMap(_.publication).map(_.status) shouldBe Some("unnecessary")
        Files.readString(root.resolve("publication-count.txt")) shouldBe publications
        observe(nochange.sessionId, "return-observed", nochange.sessionId).kind shouldBe "Terminal"
      }
      Given("an older Completed destination with accepted verification and no required record at that time")
      val legacyPolicy = """{"targets":[{"family":"delegate-task","scope":"no-change-request","paths":["docs/delegate.md"]}]}"""
      val originalChecks = Files.readString(root.resolve("sbt-executions.txt"))
      val originalPublications = Files.readString(root.resolve("publication-count.txt"))
      _with_runtime_(root, "Delegate", Some(host), _parameters + (CompletionRecords.declaration.name -> legacyPolicy)) { (owner, component, actor, prepare) =>
        def call[A: org.goldenport.convert.ValueReader](name: String, input: V.CompletionRecordClosureInput): A =
          read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "CompletionRecordClosureService", operation = name, properties = List(Property("input", input, None))), actor)))
        var originalTerminal = Option.empty[org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord]
        prepare(_.delegateCurrentC(org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity(legacySession)).map(value => { originalTerminal = Some(value); () }))
        val description = call[V.CompletionRecordClosureView]("describeCompletionRecordClosure",
          V.CompletionRecordClosureInput(legacySession, "delegate-task", None, None, None))
        val describedTarget = take(CompletionRecordContinuation.publicTargetC(description.target))
        describedTarget.hcursor.downField("source").get[String]("progression") shouldBe Right("Running")
        val acceptedText = describedTarget.hcursor.downField("source").get[String]("accepted").toOption.get
        val acceptedSource = parser.parse(acceptedText).toOption.get
        acceptedSource.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.delegate-task.v1")
        acceptedSource.hcursor.downField("payload").downField("state").downField("input").get[String]("requestIdentity") shouldBe Right("no-change-request")
        acceptedSource.hcursor.downField("payload").downField("state").downField("commit").get[String]("identity") shouldBe Right(implementationCommit)
        val reference = V.WorkflowArtifactReference("specification-legacy-closure-authority", "1")
        val input = V.CompletionRecordClosureInput(legacySession, "delegate-task", Some(description.terminalRevision), Some(description.target), Some(reference))
        owner.executeOperationResponseInContext(Request.of(component = component.name, service = "CompletionRecordClosureService",
          operation = "advanceCompletionRecordClosure", properties = List(Property("input", input, None))), actor).isSuccess shouldBe false
        prepare { setup => for {
          prepared <- org.simplemodeling.textus.smworkflow.preparation.NativeCompletionRecordClosurePreparation.prepareC(description, input,
            reference, V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None), "participant", "normal-development-host",
            setup.executionContext.clock.instant().minusSeconds(1))
          _ <- setup.createC("host_evidence", prepared.recordIdentity, prepared.record)
        } yield () }
        When("the specification-owned independent host admits the exact same-owner closure target")
        val result = call[V.CompletionRecordClosureView]("advanceCompletionRecordClosure", input)
        Then("only the outstanding record is committed and the original terminal and successful work remain retained")
        result.kind shouldBe "Terminal"
        result.recordCommit should not be Some(implementationCommit)
        call[V.CompletionRecordClosureView]("advanceCompletionRecordClosure", input) shouldBe result
        prepare(_.delegateCurrentC(org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity(legacySession)).map { value =>
          value.history.take(originalTerminal.get.history.size) shouldBe originalTerminal.get.history
          value.history(originalTerminal.get.history.size).currentProgression.map(_.value) shouldBe Some("Running")
          value.identity shouldBe originalTerminal.get.identity
          // Each native load rebuilds its catalog binding; retain the exact
          // admitted definition contract rather than its JVM object identity.
          val actualdefinition = value.definition
          val originaldefinition = originalTerminal.get.definition
          (actualdefinition.workflowIdentity, actualdefinition.workflowRevision,
            actualdefinition.sourceCorrelation, actualdefinition.producerAbiIdentity,
            actualdefinition.workflowAbiIdentity, actualdefinition.bootstrapAbiIdentity,
            actualdefinition.producerRevision) shouldBe
            (originaldefinition.workflowIdentity, originaldefinition.workflowRevision,
              originaldefinition.sourceCorrelation, originaldefinition.producerAbiIdentity,
              originaldefinition.workflowAbiIdentity, originaldefinition.bootstrapAbiIdentity,
              originaldefinition.producerRevision)
          value.correlation shouldBe originalTerminal.get.correlation
          value.lifecycle shouldBe org.goldenport.cncf.workflow.WorkflowInstancePersistence.Lifecycle.Completed
          ()
        })
        val stale = input.copy(terminalRevision = Some((description.terminalRevision.toLong + 1L).toString))
        owner.executeOperationResponseInContext(Request.of(component = component.name, service = "CompletionRecordClosureService",
          operation = "advanceCompletionRecordClosure", properties = List(Property("input", stale, None))), actor).isSuccess shouldBe false
        Files.readString(root.resolve("sbt-executions.txt")) shouldBe originalChecks
        Files.readString(root.resolve("publication-count.txt")) shouldBe originalPublications
        Files.readString(root.resolve("repository/docs/delegate.md")) should include (
          "<!-- sm-workflow completion " + legacySession + " delegate-task no-change-request -->")
        val ordinary = read_value[V.DelegateTaskView](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "DelegateTaskService", operation = "advanceDelegateTask", properties = List(Property("sessionId", legacySession, None))), actor)))
        ordinary.kind shouldBe "Terminal"
        ordinary.message should include ("Completion scope delegate-task no-change-request")
      }
    }
    "keep explicit broad scope conditional review and missing-profile refusal separate from sibling contracts" in {
      val input = take(DelegateTask.inputC(_input("destination", "policy")))
      take(S.startC(input, "participant", _checks)).stage shouldBe "Implement"
      val broad = input.copy(delegation = input.delegation.map(_.copy(validationScope = "broad")))
      S.startC(broad, "participant", _checks).isSuccess shouldBe false
      val full = _checks :+ GoalPhaseNativeValidation.Check("FULL", 3, T.Kind.Test)
      When("broad impact needs FULL in addition to the admitted focused acceptance")
      val selected = take(DelegateTask.selectChecksC(broad, full))
      Then("runtime selects the installed FULL binding while retaining the adopted explicit checks")
      selected shouldBe full
      take(S.startC(broad, "participant", selected)).input shouldBe broad
      take(DelegateTask.selectChecksC(input, full)) shouldBe _checks
      DelegateTask.selectChecksC(broad, _checks).isSuccess shouldBe false
      take(S.startC(broad.copy(checks = full.map(_.identity)), "participant", full)).selected shouldBe full
      Given("the admitted destination request and its requester role carry the same requirements")
      val requester = input.copy(delegation = input.delegation.map(_.copy(role = "requester")))
      When("fresh admission has no concrete execution mapping or origin")
      val requesteradmission = DelegateTaskNativeRun.preflightExecutionC(requester, None, None)
      Then("requester metadata is admitted while destination execution still requires its mapping")
      requesteradmission.isSuccess shouldBe true
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
