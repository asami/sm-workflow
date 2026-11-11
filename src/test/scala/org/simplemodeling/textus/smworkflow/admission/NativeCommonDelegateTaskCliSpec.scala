package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import org.goldenport.record.RecordPresentable
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take
import StandaloneFixExecution as S
import S.given

/** Tiny build and ordinary host declarations only. The fixed focused suite is
  * host configuration, never a request-supplied class or command argument.
  * @since   Nov.  5, 2026
  * @version Nov. 11, 2026
  */
private[admission] object NativeCommonDelegateTaskFixture {
  val suite = "org.simplemodeling.textus.smworkflow.p62authored.LineWriterSpec"
  val testPath = "src/test/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriterSpec.scala"
  val component = "org.example.CommonDelegateFixture"
  val publication = "common-delegate-fixture"

  def processPlan(root: Path, focused: Boolean = true): Json = {
    val descriptor = take(GenericSubsystemDescriptor.loadComponentArchive(Path.of("src/main/car").toAbsolutePath))
    val template = parser.parse(descriptor.config("textus.subsystem.process-execution.plan")).toOption.get
    val values = Map("repository.root" -> root.resolve("repository").toString, "git.branch" -> "main",
      "git.remote" -> "origin", "git.remote-branch" -> "main", "git.endpoint" -> root.resolve("upstream.git").toString,
      "git.user-name" -> "Fixture", "git.user-email" -> "fixture@example.invalid",
      "executable.sbt" -> TypedSbtHostPlanFixture.normalExecutable.toString,
      "environment.path" -> sys.env.getOrElse("PATH", "/usr/bin:/bin"),
      "environment.java-home" -> System.getProperty("java.home"), "environment.ssh-auth-sock" -> "")
    def _expand_(value: Json): Json = value.mapString(text => values.foldLeft(text) { case (prior, (key, replacement)) =>
      prior.replace("${" + key + "}", replacement) }).mapArray(_.map(_expand_)).mapObject(_.mapValues(_expand_))
    val base = _expand_(template)
    if (!focused) base else {
      val program = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(2),
        operation = SbtOperation.TestOnly, testClasses = Vector(suite))
        .hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.head
      base.mapObject(fields => fields.add("sbtPrograms", fields("sbtPrograms").get.mapArray(_ :+ program))
        .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
          grant("capabilities").get.mapArray(_ :+ program.hcursor.get[Json]("capability").toOption.get)))))))
    }
  }
}

/** Explicit installed qualification. Human selections, transport observations
  * and semantic judgments are SPECIFICATION-OWNED. Actual checks, commit and
  * tiny artifact publication belong to the normal destination runtime.
  */
private[admission] trait NativeCommonDelegateTaskQualification extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  protected def commonDelegateExecutable: Path
  protected def commonDelegateInstallations: Option[Path] = None
  protected def commonDelegateComponentFile: Option[Path] = sys.props.get("smworkflow.spec.componentFile")
    .orElse(sys.env.get("SM_WORKFLOW_SPEC_COMPONENT_FILE")).map(Path.of(_))
  protected def commonDelegateCall(scenario: F.Scenario, service: String, operation: String, arguments: Vector[String]): F.Result =
    scenario.callInstalled(commonDelegateExecutable, service, operation, arguments, installations = commonDelegateInstallations,
      assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _state(view: V.DelegateTaskView): S.State = GoalPhaseNativeMetadata.normalizePublicJson(
    parser.parse(take(view.state.get.toJsonStringC)).toOption.get).toOption.get.as[S.State].toOption.get
  private def _work(view: V.DelegateTaskView): S.Work = GoalPhaseNativeMetadata.normalizePublicJson(
    parser.parse(take(view.work.get.toJsonStringC)).toOption.get).toOption.get.as[S.Work].toOption.get
  private def _call(scenario: F.Scenario, service: String, operation: String, arguments: Vector[String]): F.Result =
    commonDelegateCall(scenario, service, operation, arguments)

  /** Each selected detail is another actual installed process. Keep ordinary
    * control available separately for output and route assertions below. */
  private def _view(scenario: F.Scenario, result: F.Result): V.DelegateTaskView = {
    val control = result.business[V.DelegateTaskView]
    val work = control.retainedWork.map { reference =>
      _call(scenario, WorkflowRetainedWork.serviceName, WorkflowRetainedWork.readOperation,
        Vector("--input", _json(reference))).business[V.WorkflowRetainedWorkReadResult].response
    }.getOrElse(control.toRecord())
    val complete = control.retainedMaterials.foldLeft(work) { (prior, field) =>
      val detail = _call(scenario, WorkflowRetainedWork.serviceName, WorkflowRetainedSnapshot.readOperation,
        Vector("--input", _json(field.reference))).business[V.WorkflowRetainedSnapshotReadResult]
      val overlay = control.controlSummary.flatMap(_.getRecord(field.fieldName)).getOrElse(org.goldenport.record.Record.empty)
      val material = org.goldenport.record.Record.createFull(detail.material.fields.filterNot(value => overlay.getAny(value.key).nonEmpty)
        .map(value => value.key -> value.value.single)) ++ overlay
      org.goldenport.record.Record.createFull(prior.fields.filterNot(_.key == field.fieldName)
        .map(value => value.key -> value.value.single)) ++ org.goldenport.record.Record.data(field.fieldName -> material)
    }
    take(V.DelegateTaskView.createC(complete))
  }

  private def _fresh(): F.Scenario = {
    val scenario = F.fresh("installed-common-delegate", bootstrapSelectedInput = false,
      sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable))
    val root = scenario.root
    Files.createDirectories(root.resolve("isolated-home/.textus"))
    val repository = Path.of(scenario.host.target.root)
    TypedSbtHostPlanFixture.buildAuthoring(root)
    Files.writeString(repository.resolve("build.sbt"),
      "\norganization := \"org.example.sm-workflow-common-delegate-fixture\"\n" +
        "name := \"common-delegate-fixture\"\nversion := \"0.0.1-SNAPSHOT\"\n" +
        "publishLocal := { val result = publishLocal.value; IO.append(file(\"../publication-count.txt\"), \"published\\n\"); result }\n",
      StandardOpenOption.APPEND)
    // Fixed fixture baseline only, before any business request. Runtime owns the
    // later selected commit; these setup calls do not create native receipts.
    take(_run_setup_command_(Vector("-C", repository.toString, "add", "--", ".gitignore", "build.sbt",
      "project/build.properties", "src/main", "docs")))
    take(_run_setup_command_(Vector("-C", repository.toString, "-c", "user.name=Fixture",
      "-c", "user.email=fixture@example.invalid", "commit", "-m", "Configure tiny common delegation fixture")))
    val configfile = root.resolve(".textus/config.json")
    val config = parser.parse(Files.readString(configfile)).toOption.get
    val collection = ThreeWorkflowNormalHostFixture.collection("local_publication")
    val storage = ThreeWorkflowNormalHostFixture.storagePlan.mapObject(fields => fields.add("records",
      fields("records").get.mapArray(_ :+ Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
        "storeName" -> "workflow".asJson, "collection" -> collection.asJson))))
    val policy = Json.obj("storeName" -> "workflow".asJson, "collection" -> collection.asJson,
      "targets" -> Json.arr(Json.obj("identity" -> NativeCommonDelegateTaskFixture.publication.asJson,
        "component" -> NativeCommonDelegateTaskFixture.component.asJson,
        "workspace" -> TypedGitHostPlanFixture.workspace.asJson)))
    // Both host plans below replace F.fresh's plans, including its importer
    // collection/workspace grants. Rebuild that declaration against these
    // selected plans before the first public preparation call.
    val selected = config.mapObject(fields => fields.remove(ThreeWorkflowRuntimeParameters.definition.name)
      .remove(CommonEvidence.declaration.name)
      .add("textus.subsystem.process-execution.plan", NativeCommonDelegateTaskFixture.processPlan(root).noSpaces.asJson)
      .add("textus.subsystem.storage.plan", storage.noSpaces.asJson)
      .add(LocalPublicationRuntime.declaration.name, policy.noSpaces.asJson))
    val logical = BoundedWorkExecutionAdmissionFixture.logicalParameters.foldLeft(selected) { case (prior, (key, value)) =>
      prior.mapObject(_.add(key, value.asJson))
    }
    val installed = commonDelegateComponentFile.fold(logical) { component =>
      require(component.isAbsolute && component.normalize == component && Files.isRegularFile(component),
        "Select an actual absolute Component archive")
      logical.mapObject(_.add("textus.component.file", component.toString.asJson))
    }
    Files.writeString(configfile, installed.noSpaces)
    scenario.enablePreparedImportRetention()
    scenario
  }

  protected def qualifyCommonDelegate(outcome: String): Unit = {
      Given("one tiny isolated normal host with no runtime.definition and SPECIFICATION-OWNED request authority")
      val scenario = _fresh()
      val root = scenario.root
      val config = parser.parse(Files.readString(root.resolve(".textus/config.json"))).toOption.get
      config.hcursor.get[String](ThreeWorkflowRuntimeParameters.definition.name).toOption shouldBe None
      val conditions = NonEmptyVector.of("behavior", "validation", "commit", "publication")
      val request = V.DelegateTaskInput(scenario.host.goal.input.repositoryIdentity, "installed-common-request",
        "SPECIFICATION-OWNED author tiny LineWriter behavior and return the genuinely published source",
        NonEmptyVector.one(NativeCommonDelegateTaskFixture.testPath), NonEmptyVector.of("compile", "test-only"), conditions,
        NonEmptyVector.one(V.WorkflowArtifactReference("SPECIFICATION-OWNED-original-request", "1")), false,
        "code", "off", 10, "direct-instruction", Vector.empty,
        V.DelegateTaskContract("SPECIFICATION-OWNED-requester", "SPECIFICATION-OWNED-destination",
          "SPECIFICATION-OWNED-requester", NativeCommonDelegateTaskFixture.component, "requester", "localized",
          NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt", "publishLocal"),
          Vector("remote-push"), NonEmptyVector.one(V.WorkflowArtifactReference("SPECIFICATION-OWNED-Human", "1")),
          Some(NativeCommonDelegateTaskFixture.publication),
          Vector("org.example.sm-workflow-common-delegate-fixture:common-delegate-fixture_3:0.0.1-SNAPSHOT"),
          "SPECIFICATION-OWNED artifact return; consumer selection and activation remain separate"), Vector.empty)
      def _start_(input: V.DelegateTaskInput): V.DelegateTaskView = {
        val role = input.delegation.role
        val file = root.resolve(role + "-input.json")
        Files.writeString(file, _json(input))
        val preparation = V.WorkflowDelegateTaskStartPreparationInput(file.toUri.toASCIIString,
          role + "-selection", "participant", "ExplicitHuman", role + "-key", "entity:smworkflow.cli.goal_selection")
        val prepared = _call(scenario, "NativeWorkflowPreparationService", "prepareDelegateTaskStart", Vector("--input", _json(preparation)))
        scenario.selectPreparedImport(prepared)
        _view(scenario, _call(scenario, "DelegateTaskService", "startDelegateTask", Vector("--input", _json(input),
          "--selectionId", role + "-selection", "--participant", "participant", "--idempotencyKey", role + "-key")))
      }
      def _advance_(id: String) = _view(scenario, _call(scenario, "DelegateTaskService", "advanceDelegateTask",
        Vector("--sessionId", id)))
      def _observe_(id: String, event: String, destination: String, result: Option[org.goldenport.record.Record] = None) =
        _view(scenario, _call(scenario, "DelegateTaskDeliveryService", "observeDelegateTaskDelivery", Vector("--input", _json(
          V.DelegateTaskObservationInput(id, event, V.WorkflowArtifactReference("SPECIFICATION-OWNED-" + event, "1"),
            Some(destination), result, None, "SPECIFICATION-OWNED transport observation; no consumer effect is certified")))))
      def _submit_(view: V.DelegateTaskView, result: V.DelegateTaskWorkResult, name: String): F.Result = {
        val reference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-" + name, "1")
        val worker = "SPECIFICATION-OWNED-" + name
        val evidence = V.WorkflowExecutionEvidence(Vector(reference), Some(worker), None,
          Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", worker, "ordinary")))
        _call(scenario, "DelegateTaskService", "submitDelegateTaskWorkResult", Vector("--sessionId", view.sessionId,
          "--issuedWorkContext", _json(view.issuedWorkContext.get), "--result", _json(result),
          "--resultReference", _json(reference), "--executionEvidence", _json(evidence)))
      }
      When("requester and destination select the same complete request and default timing through public preparation and Start")
      val requester = _start_(request)
      val dispatch = _advance_(requester.sessionId)
      val destination = _start_(request.copy(delegation = request.delegation.copy(role = "destination")))
      val implement = _advance_(destination.sessionId)
      val dispatched = _observe_(requester.sessionId, "dispatch-observed", destination.sessionId)
      Then("requester transport preserves explicit empty timing and destination work does not wait for later effects")
      dispatch.kind shouldBe "Dispatch"
      _state(dispatched).stage shouldBe "AwaitResult"
      _state(dispatched).input.submissionConditions shouldBe Some(Vector.empty)
      _state(implement).input.submissionConditions shouldBe Some(Vector.empty)
      _work(implement).kind shouldBe "Implement"
      _work(implement).operatingProfile.get.dispatch.get.candidates shouldBe Vector("ordinary", "difficult", "hardest")
      _advance_(destination.sessionId).issuedWorkContext shouldBe implement.issuedWorkContext
      Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      Files.exists(root.resolve("publication-count.txt")) shouldBe false
      val beforehead = scenario.candidateHead
      val path = Path.of(scenario.host.target.root).resolve(NativeCommonDelegateTaskFixture.testPath)
      Files.createDirectories(path.getParent)
      Files.writeString(path, """package org.simplemodeling.textus.smworkflow.p62authored
        |import org.scalatest.wordspec.AnyWordSpec
        |import org.scalatest.matchers.should.Matchers
        |import org.scalatest.GivenWhenThen
        |final class LineWriterSpec extends AnyWordSpec with Matchers with GivenWhenThen {
        |  "LineWriter" should { "preserve each selected text before one terminator" in {
        |    Given("ASCII Unicode and empty text")
        |    val inputs = Vector("expected", "日本語", "")
        |    When("each input is rendered")
        |    val rendered = inputs.map(LineWriter.render)
        |    Then("the original text is followed by exactly one LF")
        |    rendered shouldBe inputs.map(_ + "\n")
        |  }}
        |}
        |""".stripMargin)
      val deferredreport = conditions.toVector.map(id => V.DelegateTaskCondition(id, false,
        "Actual runtime validation local commit and publication have not yet established this obligation"))
      val completed = V.DelegateTaskWorkResult("completed", "SPECIFICATION-OWNED tiny behavior supplied for genuine later verification",
        Vector.empty, NonEmptyVector.fromVectorUnsafe(deferredreport), Vector.empty, None, None)
      When("the destination submits a complete honest report with later effects false")
      val submitted = _submit_(implement, completed, "implementer")
      Then("implementation is accepted before those checks commit or publication execute")
      withClue(submitted.invocation.logs) { submitted.exitCode shouldBe 0 }
      Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      scenario.candidateHead shouldBe beforehead
      var current = _advance_(destination.sessionId)
      var remaining = 12
      while (current.work.isEmpty && remaining > 0 && current.kind != "Return") {
        current = _advance_(destination.sessionId)
        remaining -= 1
      }
      Then("ordinary destination owners execute fixed Compile TestOnly local commit and actual tiny publication before final judgment")
      withClue(current.message) { current.work should not be empty }
      val acceptedwork = _work(current)
      acceptedwork.kind shouldBe "DeliveryAcceptance"
      acceptedwork.state.conditionReport.map(_.satisfied) shouldBe Vector.fill(4)(false)
      acceptedwork.state.receipts.map(_.identity) shouldBe Vector("compile", "test-only")
      acceptedwork.state.receipts.foreach(_.exitCode shouldBe Some(0))
      acceptedwork.state.commit.get.identity shouldBe scenario.candidateHead
      scenario.candidateHead should not be beforehead
      acceptedwork.state.delivery.get.publication.get.ready shouldBe true
      acceptedwork.state.delivery.get.publication.get.request.source.head shouldBe scenario.candidateHead
      acceptedwork.state.reviewReference shouldBe None // Conditional detailed review was not required for this bounded request.
      val checks = Files.readString(root.resolve("sbt-executions.txt"))
      val publications = Files.readString(root.resolve("publication-count.txt"))
      publications.linesIterator.toVector shouldBe Vector("published")
      val commands = Files.readString(root.resolve("sbt-command-lines.txt"))
      commands should include("testOnly " + NativeCommonDelegateTaskFixture.suite)
      val finalreport = deferredreport.map(c => c.copy(satisfied = true,
        detail = "SPECIFICATION-OWNED judgment binds the retained actual selected checks source commit and publication"))
      val finalresult = completed.copy(outcome = "accept", conditions = NonEmptyVector.fromVectorUnsafe(finalreport))
      When("final acceptance omits an original ID or falsely leaves an obligation unresolved")
      val missing = _submit_(current, finalresult.copy(conditions = NonEmptyVector.fromVectorUnsafe(finalreport.dropRight(1))), "final-missing")
      val unfinished = _submit_(current, finalresult.copy(conditions = NonEmptyVector.fromVectorUnsafe(
        finalreport.updated(3, deferredreport(3)))), "final-unfinished")
      Then("both refuse without replaying the retained effects")
      missing.exitCode should not be 0
      unfinished.exitCode should not be 0
      _advance_(destination.sessionId).issuedWorkContext shouldBe current.issuedWorkContext
      if (outcome == "decision-needed") {
        When("DeliveryAcceptance honestly retains unresolved activation obligations after actual commit and publication")
        val decision = finalresult.copy(outcome = "decision-needed", conditions = NonEmptyVector.fromVectorUnsafe(deferredreport),
          unresolvedQuestions = Vector("SPECIFICATION-OWNED activation remains unresolved"))
        val submitteddecision = _submit_(current, decision, "delivery-decision")
        withClue(submitteddecision.invocation.logs) { submitteddecision.exitCode shouldBe 0 }
        val submittedcontrol = submitteddecision.business[V.DelegateTaskView]
        submittedcontrol.kind shouldBe "Submitted"
        submittedcontrol.state shouldBe empty
        submittedcontrol.retainedMaterials.map(_.fieldName) should contain("state")
        val humanresult = _call(scenario, "DelegateTaskService", "advanceDelegateTask", Vector("--sessionId", destination.sessionId))
        val humancontrol = humanresult.business[V.DelegateTaskView]
        Then("ordinary Human control uses its exact state owner without a fabricated completion operation or ReturnReady")
        humancontrol.kind shouldBe "Human"
        humancontrol.state shouldBe empty
        humancontrol.retainedWork shouldBe empty
        humancontrol.issuedWorkContext shouldBe empty
        humancontrol.submitOperation shouldBe empty
        humancontrol.retainedMaterials.map(_.fieldName) should contain("state")
        val human = _state(_view(scenario, humanresult))
        human.stage shouldBe "Human"
        human.receipts shouldBe acceptedwork.state.receipts
        human.commit shouldBe acceptedwork.state.commit
        human.delivery shouldBe acceptedwork.state.delivery
        human.automaticCount shouldBe acceptedwork.state.automaticCount
        human.ordinaryCount shouldBe acceptedwork.state.ordinaryCount
        human.conditionReport.forall(_.satisfied) shouldBe false
        _state(_advance_(destination.sessionId)) shouldBe human
        Files.readString(root.resolve("sbt-executions.txt")) shouldBe checks
        Files.readString(root.resolve("publication-count.txt")) shouldBe publications
        scenario.candidateHead shouldBe acceptedwork.state.commit.get.identity
        scenario.candidateStatus shouldBe ""
      } else {
      When("the exact DeliveryAcceptance result evaluates every obligation against those retained actual effects")
      val submittedacceptance = _submit_(current, finalresult, "delivery-acceptance")
      withClue(submittedacceptance.invocation.logs) { submittedacceptance.exitCode shouldBe 0 }
      val returned = _advance_(destination.sessionId)
      val returnedstate = _state(returned)
      val received = _observe_(requester.sessionId, "result-received", destination.sessionId, returned.state)
      val observed = _observe_(destination.sessionId, "return-observed", destination.sessionId)
      Then("actual result transport retains the same request timing and source with no duplicate checks commit or publication")
      returned.kind shouldBe "Return"
      returnedstate.conditionReport.forall(_.satisfied) shouldBe true
      _state(received).stage shouldBe "ResultReceived"
      _state(observed).stage shouldBe "Returned"
      _state(received).input.submissionConditions shouldBe Some(Vector.empty)
      _advance_(requester.sessionId).state shouldBe received.state
      _advance_(destination.sessionId).state shouldBe observed.state
      _state(_observe_(requester.sessionId, "result-received", destination.sessionId, returned.state)) shouldBe _state(received)
      Files.readString(root.resolve("sbt-executions.txt")) shouldBe checks
      Files.readString(root.resolve("publication-count.txt")) shouldBe publications
      scenario.candidateHead shouldBe acceptedwork.state.commit.get.identity
      scenario.candidateStatus shouldBe ""
      }
      info("SPECIFICATION-OWNED installed common delegation results retained under " + root)
  }
}

@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeCommonDelegateTaskCliSpec extends AnyWordSpec with NativeCommonDelegateTaskQualification {
  override protected lazy val commonDelegateExecutable: Path = {
    val path = Path.of(sys.props.get("smworkflow.spec.installedCommand")
      .orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND"))
      .getOrElse(fail("Select smworkflow.spec.installedCommand or SM_WORKFLOW_SPEC_INSTALLED_COMMAND")))
    require(path.isAbsolute && path.normalize == path && Files.isRegularFile(path) && Files.isExecutable(path),
      "Select the actual absolute installed executable")
    path
  }
  "Installed common delegation" should {
    Vector("accept", "decision-needed").foreach { outcome =>
      s"defer initial obligations then retain actual focused checks commit publication and $outcome through normal owners" in {
        qualifyCommonDelegate(outcome)
      }
    }
  }
}
