package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.record.{Record, RecordPresentable}
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import DelegateTaskRequesterRoutingFixture.{input, state, assertNoExecution}
import SplitPhaseExplicitPartitionFixture.take

/** One explicitly selected installed behavioral case. Every caller/destination
  * identity and transport observation here is SPECIFICATION-OWNED, not actual
  * chat delivery, producer execution, publication or consumer adoption.
  * @since   Nov.  5, 2026
  * @version Nov.  6, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeDelegateTaskRequesterCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private lazy val _executable: Path = {
    val path = Path.of(sys.props.get("smworkflow.spec.installedCommand")
      .orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND"))
      .getOrElse(fail("Select smworkflow.spec.installedCommand or SM_WORKFLOW_SPEC_INSTALLED_COMMAND")))
    require(path.isAbsolute && path.normalize == path && Files.isRegularFile(path) && Files.isExecutable(path),
      "Select the actual absolute installed executable")
    path
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _call(scenario: F.Scenario, service: String, operation: String, parameters: Vector[String]): F.Result =
    scenario.callInstalled(_executable, service, operation, parameters, installations = None,
      assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))

  "Installed requester role admission" should {
    "prepare retain and observe a foreign code request without destination execution bindings or effects" in {
      Given("an isolated SPECIFICATION-OWNED caller with no execution mapping and only its ordinary local host bindings")
      val executable = _executable
      val scenario = F.fresh("installed-delegate-requester", mappedExecution = None, bootstrapSelectedInput = false)
      sys.props.get("smworkflow.spec.componentFile").foreach { supplied =>
        val component = Path.of(supplied)
        require(component.isAbsolute && component.normalize == component && Files.isRegularFile(component),
          "The optional Component archive must be an actual absolute published file")
        val configfile = scenario.root.resolve(".textus/config.json")
        val config = parser.parse(Files.readString(configfile)).toOption.get
        Files.writeString(configfile, config.mapObject(_.add("textus.component.file", supplied.asJson)).noSpaces)
      }
      When("the isolated caller's configured execution and publication bindings are read")
      val config = parser.parse(Files.readString(scenario.root.resolve(".textus/config.json"))).toOption.get
      Then("no concrete profile, origin or local publication policy is installed")
      config.hcursor.get[String]("workflow.execution.profiles").toOption shouldBe None
      config.hcursor.get[String]("sm-workflow.execution.origin").toOption shouldBe None
      config.hcursor.get[String]("sm-workflow.local-publication").toOption shouldBe None
      When("the authorized foreign code request is selected for that caller")
      val request = input("installed-foreign-requester")
      val adopted = take(DelegateTask.inputC(request))
      Then("its destination repository differs from the caller's configured repository")
      request.repositoryIdentity should not be scenario.host.goal.input.repositoryIdentity
      Given("unrelated caller bytes and the actual pre-Start Git observations to preserve")
      val repository = Path.of(scenario.host.target.root)
      val dirtyfile = repository.resolve("unrelated-caller.txt")
      Files.writeString(dirtyfile, "SPECIFICATION-OWNED unrelated caller bytes\n")
      val head = scenario.candidateHead
      val index = scenario.candidateIndexDiff
      val status = scenario.candidateStatus
      val indexbytes = Files.readAllBytes(repository.resolve(".git/index")).toVector
      val inputfile = scenario.root.resolve("delegate-request.json")
      Files.writeString(inputfile, _json(request))
      val preparation = V.WorkflowDelegateTaskStartPreparationInput(inputfile.toUri.toASCIIString,
        "requester-selection", "participant", "ExplicitHuman", "requester-key", "entity:smworkflow.cli.goal_selection")

      When("the installed preparation Operation reads the complete selected request")
      val prepared = _call(scenario, "NativeWorkflowPreparationService", "prepareDelegateTaskStart", Vector("--input", _json(preparation)))
      Then("it returns a passive selection import successfully")
      withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
      When("that import is independently selected and the installed Start admits its exact selection")
      scenario.selectPreparedImport(prepared)
      def _start_() = _call(scenario, "DelegateTaskService", "startDelegateTask", Vector("--input", _json(request),
        "--selectionId", "requester-selection", "--participant", "participant", "--idempotencyKey", "requester-key"))
      val startedresult = _start_()
      val started = startedresult.business[V.DelegateTaskView]
      Then("the requester has its canonical Started Session")
      started.kind shouldBe "Started"
      def _advance_() = _call(scenario, "DelegateTaskService", "advanceDelegateTask",
        Vector("--sessionId", started.sessionId)).business[V.DelegateTaskView]
      def _observe_(event: String, artifact: Option[Record] = None) =
        _call(scenario, "DelegateTaskDeliveryService", "observeDelegateTaskDelivery", Vector("--input",
          _json(V.DelegateTaskObservationInput(started.sessionId, event,
            V.WorkflowArtifactReference("SPECIFICATION-OWNED-" + event, "1"),
            Some("SPECIFICATION-OWNED-destination-session"), None, artifact,
            "SPECIFICATION-OWNED observation; no real chat delivery, producer execution or activation is claimed"))))
      When("a separate installed Advance collects the requester's dispatch frontier")
      val dispatched = _advance_()
      Then("Dispatch retains the complete foreign request without execution facts")
      dispatched.kind shouldBe "Dispatch"
      state(dispatched).input shouldBe adopted
      state(dispatched).stage shouldBe "DispatchReady"
      assertNoExecution(state(dispatched))
      When("the exact requester Start is redelivered through another installed command")
      val redelivered = _start_().business[V.DelegateTaskView]
      Then("the original Session and dispatch state are retained")
      redelivered.kind shouldBe "Retained"
      redelivered.sessionId shouldBe started.sessionId
      state(redelivered) shouldBe state(dispatched)
      When("Advance recollects that retained requester")
      val redispatched = _advance_()
      Then("its dispatch state is unchanged")
      state(redispatched) shouldBe state(dispatched)
      When("the specification-owned caller records its attributable dispatch observation")
      val observedresult = _observe_("dispatch-observed")
      val observed = observedresult.business[V.DelegateTaskView]
      Then("the requester advances only to AwaitResult")
      state(observed).stage shouldBe "AwaitResult"
      When("another installed Advance collects the observed dispatch")
      val waiting = _advance_()
      Then("it returns Wait for an actual destination result")
      waiting.kind shouldBe "Wait"
      When("adoption description is requested before receiving a producer result")
      val described = _call(scenario, "DelegateTaskDeliveryService", "describeDelegateTaskAdoption",
        Vector("--sessionId", started.sessionId))
      Then("the description request is refused")
      described.exitCode should not be 0
      When("an unverified artifact attachment is offered for premature adoption")
      val refused = _observe_("adopt", Some(Record.data("sourceCommit" -> "unverified")))
      Then("that adoption is refused")
      refused.exitCode should not be 0

      When("Advance re-enters after those refused operations")
      val retained = _advance_()
      Then("the requester retains its original AwaitResult state")
      state(retained) shouldBe state(waiting)
      When("the original requester Start is redelivered while waiting")
      val waitingredelivery = _start_().business[V.DelegateTaskView]
      Then("AwaitResult retains no semantic request or producer result and every caller Git byte stays unchanged")
      state(waitingredelivery) shouldBe state(waiting)
      state(retained).input shouldBe adopted
      assertNoExecution(state(retained))
      state(retained).delivery.flatMap(_.returned) shouldBe None
      state(retained).delivery.flatMap(_.artifactSelection) shouldBe None
      Vector(started, dispatched, redelivered, observed, waiting, retained).foreach { view =>
        view.work shouldBe None
        view.issuedWorkContext shouldBe None
        view.resultType shouldBe None
      }
      scenario.candidateHead shouldBe head
      scenario.candidateIndexDiff shouldBe index
      scenario.candidateStatus shouldBe status
      Files.readAllBytes(repository.resolve(".git/index")).toVector shouldBe indexbytes
      Files.readString(dirtyfile) shouldBe "SPECIFICATION-OWNED unrelated caller bytes\n"
      Files.exists(scenario.root.resolve("sbt-executions.txt")) shouldBe false
      Files.exists(scenario.root.resolve("publication-count.txt")) shouldBe false
      val commands = scenario.invocations.filter(_.argv.head == executable.toString)
      commands.map(_.pid).distinct.size shouldBe commands.size
      commands.foreach { invocation =>
        invocation.argv(1) should startWith("org.simplemodeling.textus.SmWorkflow.")
        invocation.argv should contain ("--textus.assembly.descriptor=" + scenario.root.resolve(".textus/assembly.yaml"))
      }
      val paths = scenario.root.resolve("requester-result-paths.json")
      Files.writeString(paths, Json.obj("provenance" -> "SPECIFICATION-OWNED".asJson,
        "sessionId" -> started.sessionId.asJson, "prepared" -> prepared.invocation.stdout.toString.asJson,
        "started" -> startedresult.invocation.stdout.toString.asJson,
        "observed" -> observedresult.invocation.stdout.toString.asJson,
        "refusedAdoption" -> refused.invocation.stdout.toString.asJson,
        "invocations" -> commands.map(i => Json.obj("stdout" -> i.stdout.toString.asJson,
          "stderr" -> i.stderr.toString.asJson, "pid" -> i.pid.asJson)).asJson).spaces2)
      info("SPECIFICATION-OWNED installed requester result paths: " + paths)
    }
  }
}
