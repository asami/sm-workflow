package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.{NativeDelegateTaskStartInput,
  NativeDelegateTaskRequestReconstructionPreparation}
import org.simplemodeling.textus.smworkflow.value as V
import BoundedWorkRuntime.*
import BoundedWorkRuntime.given
import StandaloneFixExecution as S
import StandaloneFixExecution.given
import DelegateTaskRequestReconstruction as R
import SplitPhaseExplicitPartitionFixture.take

/** All Human and transport facts here are SPECIFICATION-OWNED; no actual
  * consumer dispatch or destination program is executed by this specification.
  * @since   Nov. 19, 2026
  * @version Nov. 27, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class DelegateTaskRequestReconstructionSpec extends AnyWordSpec with GivenWhenThen
    with Phase69ConfiguredRepositoryFixture {
  private def _request: V.DelegateTaskInput = DelegateTaskRequesterRoutingFixture.input("request-reconstruction")
    .copy(checks = NonEmptyVector.of("compile", "fix-check", "test-only"))
  private def _payload: Payload = Payload(take(DelegateTask.startRequesterC(take(DelegateTask.inputC(_request)),
    "participant")), "/SPECIFICATION-OWNED/requester", "main", "retained-base")
  private def _target(payload: Payload): R.Target = R.Target("SPECIFICATION-OWNED-component", "same-session", "2",
    payload.state.participant, payload.state.input.requestIdentity,
    R.Policy(payload.state.input.checks, payload.state.input.scalaLint))
  private val _human = ContextReference("SPECIFICATION-OWNED-human-direction", "1")
  private def _observation: R.Observation = R.Observation(_request.delegation.destinationIdentity,
    "delivered", "rejected-before-start", "ended", None,
    Vector(ContextReference("SPECIFICATION-OWNED-notification", "1"),
      ContextReference("SPECIFICATION-OWNED-intake-refusal", "1"), ContextReference("SPECIFICATION-OWNED-ended-handler", "1")))
  private def _direction(payload: Payload): R.Direction = R.Direction(_target(payload),
    R.Policy(Vector("compile", "test-only"), "off"), "AdoptDerivedValidationPolicy",
    "SPECIFICATION-OWNED Human corrects derived policy and covers each actual intake source", "participant", _human, _observation)
  private def _checks = take(StandardWorkflowRuntime.standardChecksC(Vector("compile", "fix-check", "test-only")))
  private def _answer(target: String, session: String): V.DelegateTaskRequestReconstructionInput =
    V.DelegateTaskRequestReconstructionInput(session, target, NonEmptyVector.of("compile", "test-only"), "off",
      "AdoptDerivedValidationPolicy", "SPECIFICATION-OWNED actual Human correction covering transport sources", "participant",
      V.WorkflowArtifactReference(_human.identity, _human.revision), V.DelegateTaskIntakeObservation(
        _observation.destinationIdentity, "delivered", "rejected-before-start", "ended", None,
        NonEmptyVector.fromVectorUnsafe(_observation.sourceReferences.map(r => V.WorkflowArtifactReference(r.identity, r.revision))), None))

  private val _suite = "org.simplemodeling.textus.knowledgeworkbench.MaterialResearchPreparationSpec"
  private val _focused_check = "test-only." + _suite
  private val _intake_reference = ContextReference("SPECIFICATION-OWNED-intake-observation", "1")
  private val _intake_observer = "SPECIFICATION-OWNED-destination-observer"
  private def _broad_request: V.DelegateTaskInput = _request.copy(
    checks = NonEmptyVector.of("compile", "test"), scalaLint = "off", reviewRequired = true,
    ownedPaths = NonEmptyVector.of("src/test/scala/" + _suite.replace('.', '/') + ".scala",
      "src/test/scala/org/simplemodeling/textus/knowledgeworkbench/MaterialWorkFixture.scala"),
    delegation = _request.delegation.copy(validationScope = "broad"))

  private def _start_requester(request: V.DelegateTaskInput, identity: String,
    invoke: (String, List[Property]) => Consequence[OperationResponse], prepare: (Setup => Consequence[Unit]) => Unit): V.DelegateTaskView = {
    val selection = identity + "-selection"
    val key = identity + "-key"
    val prepared = take(NativeDelegateTaskStartInput.prepareC(Map("input" -> "selected.json",
      "selection-id" -> selection, "participant" -> "participant", "origin" -> "ExplicitHuman",
      "idempotency-key" -> key, "selection-collection" -> "entity:smworkflow.cli.goal_selection"),
      _ => request.toRecord().toJsonStringC))
    val row = prepared.getVector("datastore").get.head.asInstanceOf[org.goldenport.record.Record]
      .getVector("records").get.head.asInstanceOf[org.goldenport.record.Record]
    prepare(_.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id"))))
    val startresult = invoke("startDelegateTask", List(Property("input", request, None),
      Property("selectionId", selection, None), Property("participant", "participant", None),
      Property("idempotencyKey", key, None)))
    startresult match {
      case Consequence.Failure(conclusion) => fail("Native requester Start: " + conclusion.show)
      case success => read_value[V.DelegateTaskView](take(success))
    }
  }

  "Requester reconstruction policy" should {
    "preserve business data and arbitrary valid native counters while selecting only corrected derived policy" in {
      Given("unstarted requesters carrying their real generations, counters, limits, bases and business contracts")
      val property = Prop.forAll(Gen.posNum[Long], Gen.chooseNum(0, _request.repairLimit)) { (generation, count) =>
        val original = _payload.copy(state = _payload.state.copy(generation = generation,
          automaticCount = count, ordinaryCount = count))
        val direction = _direction(original)
        val next = take(R.selectedPayloadC(original, direction, _human))
        next.state.input == original.state.input.copy(checks = direction.policy.checks, scalaLint = "off") &&
          next.copy(state = original.state, requestReconstruction = None) == original &&
          next.state.copy(input = original.state.input) == original.state &&
          next.requestReconstruction.contains(R.Checkpoint(direction))
      }
      When("each exact independently admitted Human policy is selected through shared management reconstruction")
      val checked = Test.check(Test.Parameters.default, property)
      Then("only checks and Scala lint change; every other retained input, result, counter and source fact survives")
      checked.passed shouldBe true
    }

    "keep mandatory lint mandatory and retain Compile and TestOnly for code delivery" in {
      Given("an original lint-off requester, a Human direction selecting required lint and another removing Compile")
      val original = _payload.copy(state = _payload.state.copy(input = _payload.state.input.copy(scalaLint = "off")))
      val required = _direction(original).copy(policy = R.Policy(Vector("compile", "test-only"), "required"))
      val weakened = _direction(original).copy(policy = R.Policy(Vector("test-only"), "off"))
      When("the native family applies its existing logical check-kind policy")
      val accepted = take(R.selectedPayloadC(original, required, _human))
      val missinglint = DelegateTask.selectChecksC(accepted.state.input,
        _checks.filterNot(_.kind == WorkflowSbtValidation.Kind.FixCheck))
      val missingcompile = R.selectedPayloadC(original, weakened, _human)
      Then("required lint is preserved and neither absent mandatory lint nor missing code validation is accepted")
      accepted.state.input.scalaLint shouldBe "required"
      take(DelegateTask.selectChecksC(accepted.state.input, _checks)).map(_.kind) should contain(WorkflowSbtValidation.Kind.FixCheck)
      missinglint.isSuccess shouldBe false
      missingcompile.isSuccess shouldBe false
    }

    "refuse started unknown live and crossed owner facts without inventing nonexecution" in {
      Given("an actual rejected-before-Start observation and material incompatible alternatives")
      val original = _payload
      val direction = _direction(original)
      val observations = Vector(_observation.copy(destinationSessionId = Some("actual-destination-session")),
        _observation.copy(intakeStatus = "started"), _observation.copy(intakeStatus = "accepted"),
        _observation.copy(notificationStatus = "unknown"), _observation.copy(handlerStatus = "live"),
        _observation.copy(handlerStatus = "unknown"), _observation.copy(intakeStatus = "unknown"),
        _observation.copy(destinationIdentity = "another-destination"), _observation.copy(sourceReferences = Vector.empty))
      When("the shared gate and family immutable target checks interpret these facts")
      val refused = observations.map(o => R.selectedPayloadC(original, direction.copy(observation = o), _human))
      val destination = original.state.input.copy(delegation = original.state.input.delegation.map(_.copy(role = "destination")))
      val crossed = Vector(original.copy(state = original.state.copy(input = destination)),
        original.copy(state = original.state.copy(stage = "AwaitResult")))
      Then("every crossed or uncertain route refuses without a policy checkpoint")
      refused.foreach(_.isSuccess shouldBe false)
      crossed.foreach(p => R.selectedPayloadC(p, direction, _human).isSuccess shouldBe false)
      R.selectedPayloadC(original, direction, _human.copy(identity = "another-human")).isSuccess shouldBe false
      R.selectedPayloadC(original, direction.copy(target = direction.target.copy(requestIdentity = "other-request")),
        _human).isSuccess shouldBe false
    }

    "read historical payloads with an absent checkpoint and dispatch exactly the effective request with its role exchanged" in {
      Given("the original durable payload before requester reconstruction existed")
      val original = _payload
      val oldwire = DelegateTaskNativeRun.stateCodec.encode(original)
      When("the current codec reads that unchanged old wire and the corrected policy becomes destination input")
      val decoded = DelegateTaskNativeRun.stateCodec.decode(oldwire)
      val corrected = take(R.selectedPayloadC(original, _direction(original), _human))
      val destination = take(DelegateTask.destinationInputC(corrected.state.input))
      Then("old payloads retain absence and destination input changes only the requester role")
      decoded shouldBe Right(original)
      oldwire.hcursor.downField("payload").downField("requestReconstruction").focus shouldBe None
      DelegateTaskNativeRun.stateCodec.decode(DelegateTaskNativeRun.stateCodec.encode(corrected)) shouldBe Right(corrected)
      take(DelegateTask.inputC(destination)) shouldBe corrected.state.input.copy(
        delegation = corrected.state.input.delegation.map(_.copy(role = "destination")))
      R.targetC(_target(original).asJson.mapObject(_.add("objective", "changed business".asJson)).noSpaces).isSuccess shouldBe false
    }

    "limit an authority-preserving clarification to the existing full test and its exact owned Scala suite" in {
      Given("a broad code request whose existing Compile and Full Test authorize one conventional spec")
      val input = take(DelegateTask.inputC(_broad_request))
      val original = _payload.copy(state = take(DelegateTask.startRequesterC(input, "participant"))
        .copy(generation = 4, automaticCount = 1, ordinaryCount = 2))
      val observation = _observation.copy(observerIdentity = Some(_intake_observer))
      val direction = R.Direction(_target(original), R.Policy(Vector("compile", _focused_check, "test"), "off"),
        R.clarifyFocusedTest, "Observed pre-Start rejection and ended handler", "participant", _intake_reference, observation)
      val substituted = direction.copy(policy = direction.policy.copy(checks = Vector("compile", "test-only.OtherSpec", "test")))
      val removedfull = direction.copy(policy = direction.policy.copy(checks = Vector("compile", _focused_check)))
      val omittedcompile = direction.copy(policy = direction.policy.copy(checks = Vector(_focused_check, "test")))
      val changedlint = direction.copy(policy = direction.policy.copy(scalaLint = "required"))
      val uncertain = Vector(observation.copy(intakeStatus = "unknown"), observation.copy(intakeStatus = "started"),
        observation.copy(handlerStatus = "live"), observation.copy(destinationSessionId = Some("actual-session")),
        observation.copy(observerIdentity = None))
      When("the existing authority is tested against actual and incompatible observation or policy alternatives")
      val selected = take(R.selectedPayloadC(original, direction, _intake_reference))
      val refused = Vector(substituted, removedfull, omittedcompile, changedlint) ++ uncertain.map(o => direction.copy(observation = o))
      Then("the correction preserves the request and native counters, and cannot replace a check or infer nonexecution")
      selected.state.input shouldBe input.copy(checks = direction.policy.checks)
      selected.copy(state = selected.state.copy(input = input), requestReconstruction = None) shouldBe original
      refused.foreach(value => R.selectedPayloadC(original, value, _intake_reference).isSuccess shouldBe false)
      val crossed = _broad_request.copy(checks = NonEmptyVector.of("compile", "test-only.OtherSpec", "test"))
      DelegateTask.inputC(crossed).isSuccess shouldBe false
    }

    "refuse dotted source path segments that would select a different Scala package" in {
      Given("an owned spec path with a literal dot in its directory or filename")
      val input = take(DelegateTask.inputC(_broad_request))
      val paths = Vector("src/test/scala/org.simplemodeling/textus/knowledgeworkbench/MaterialResearchPreparationSpec.scala",
        "src/test/scala/org/simplemodeling/textus/knowledgeworkbench.MaterialResearchPreparationSpec.scala")
      When("each path would flatten to the same fully qualified name as the conventional owned suite")
      val selections = paths.map(path => DelegateTask.approvedFocusedChecks(input.copy(ownedPaths = Vector(path))))
      val requested = paths.map(path => DelegateTask.inputC(_broad_request.copy(ownedPaths = NonEmptyVector.one(path),
        checks = NonEmptyVector.of("compile", _focused_check, "test"))))
      Then("neither a clarification nor a scoped destination selection may use the aliased suite")
      selections shouldBe Vector(None, None)
      requested.foreach(_.isSuccess shouldBe false)
    }

    "require the fixed focused suite at the destination's actual selection ordinal" in {
      Given("the same scoped check and declared destination TestOnly program")
      val check = take(StandardWorkflowRuntime.standardChecksC(Vector("compile", _focused_check, "test"),
        scopedTestOnly = true))(1)
      val workspace = "sm-workflow-repository-validation-2"
      val root = "/SPECIFICATION-OWNED/destination"
      val program = Json.obj("target" -> workspace.asJson, "operation" -> "test-only".asJson,
        "testClasses" -> Vector(_suite).asJson)
      def _plan_(binding: Json) = Json.obj(
        "workspaces" -> Vector(Json.obj("identity" -> workspace.asJson, "root" -> root.asJson)).asJson,
        "sbtPrograms" -> Vector(binding).asJson)
      When("validation uses the correct, crossed, broadened and dynamically discovered selections")
      val exact = DelegateTask.focusedTestProgramC(_plan_(program), root, check)
      val unrelated = DelegateTask.focusedTestProgramC(_plan_(program), root + "-other", check)
      val changed = Vector(program.mapObject(_.add("testClasses", Vector("example.OtherSpec").asJson)),
        program.mapObject(_.add("testClasses", Vector(_suite, "example.OtherSpec").asJson)),
        program.mapObject(_.add("discoveredTests", Json.True)))
      Then("only the originally scoped suite is admitted at the actual binding")
      exact.isSuccess shouldBe true
      unrelated.isSuccess shouldBe false
      changed.foreach(binding => DelegateTask.focusedTestProgramC(_plan_(binding), root, check).isSuccess shouldBe false)
    }
  }

  "Native requester reconstruction" should {
    "add omitted logical TestOnly through genuine direction without destination bindings and retain same-Session dispatch" in {
      Given("a specification-owned native requester requiring Compile and lint but omitting TestOnly, with no destination programs")
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/request-reconstruction")
        .toAbsolutePath), "native-").toRealPath()
      _with_runtime_(root, "UpToDate", hostParameters = Map("textus.resource.url.file.roots" -> root.toString)) {
        (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "UpToDate").size))
        val request = _request.copy(checks = NonEmptyVector.of("compile", "fix-check"))
        def _invoke_(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "DelegateTaskService", operation = operation, properties = properties), actor)
        val started = _start_requester(request, "request-reconstruction", _invoke_, prepare)
        val session = started.sessionId
        val dispatched = read_value[V.DelegateTaskView](take(_invoke_("advanceDelegateTask", List(Property("sessionId", session, None)))))
        val before = DelegateTaskRequesterRoutingFixture.state(dispatched)
        val description = read_value[V.DelegateTaskRequestReconstructionDescriptionResult](take(_invoke_(R.describeOperation,
          List(Property("sessionId", session, None)))))
        val answer = _answer(description.target, session)
        val direction = take(R.directionC(answer))
        val answerfile = root.resolve("correction.json")
        Files.writeString(answerfile, take(answer.toRecord().toJsonStringC))
        def _reconstruct_() = _invoke_(R.reconstructOperation, List(Property("inputResource", answerfile.toUri.toASCIIString, None)))
        When("a caller first presents correction strings without independently provisioned Human evidence")
        val absent = _reconstruct_()
        Then("missing Human evidence refuses and no destination input or successful effect is invented")
        absent.isSuccess shouldBe false
        When("a correction tries to carry a changed business objective or new authority alongside the policy")
        val rawanswer = parser.parse(take(answer.toRecord().toJsonStringC)).toOption.get
        val crossedfields = Vector("objective", "authorizationReferences").map { field =>
          Files.writeString(answerfile, rawanswer.mapObject(_.add(field, "unadmitted replacement".asJson)).noSpaces)
          _reconstruct_().isSuccess
        }
        Then("the exact public correction boundary refuses both added business and authority fields")
        crossedfields shouldBe Vector(false, false)
        Files.writeString(answerfile, take(answer.toRecord().toJsonStringC))
        Given("an independently observed Human direction choosing Defer at the same target")
        val defer = answer.copy(choice = "Defer", reference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-defer", "1"))
        prepare { call =>
          val evidence = take(NativeDelegateTaskRequestReconstructionPreparation.prepareC(description, defer, defer.reference,
            V.WorkflowExecutionEvidence(Vector(defer.reference), Some("participant"), None, None), "participant", "normal-development-host",
            call.executionContext.clock.instant().minusSeconds(1)))
          call.createC("host_evidence", evidence.recordIdentity, evidence.record)
        }
        When("that actual direction is submitted through the typed input form")
        val deferred = read_value[V.DelegateTaskRequestReconstructionDescriptionResult](take(_invoke_(R.reconstructOperation,
          List(Property("input", defer, None)))))
        val afterdefer = read_value[V.DelegateTaskRequestReconstructionDescriptionResult](take(_invoke_(R.describeOperation,
          List(Property("sessionId", session, None)))))
        Then("Defer preserves the same native target and original effective policy")
        deferred.outcome shouldBe "deferred"
        afterdefer.target shouldBe description.target
        When("the independent fixture host prepares and provisions actual Human direction covering each intake source")
        prepare { call =>
          val evidence = take(NativeDelegateTaskRequestReconstructionPreparation.prepareC(description, answer, answer.reference,
            V.WorkflowExecutionEvidence(Vector(answer.reference), Some("participant"), None, None), "participant", "normal-development-host",
            call.executionContext.clock.instant().minusSeconds(1)))
          call.createC("host_evidence", evidence.recordIdentity, evidence.record)
        }
        var original = Option.empty[org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord]
        prepare(_.delegateCurrentC(InstanceIdentity(session)).map(value => { original = Some(value); () }))
        val reconstructed = read_value[V.DelegateTaskRequestReconstructionDescriptionResult](take(_reconstruct_()))
        Then("one corrected native checkpoint awaits normal Advance in the original Session")
        reconstructed.outcome shouldBe "reconstructed-awaiting-advance"
        reconstructed.sessionId shouldBe session
        prepare { call => call.delegateCurrentC(InstanceIdentity(session)).map { current =>
          current.revision shouldBe original.get.revision.next
          current.history.dropRight(1) shouldBe original.get.history
        } }
        When("ordinary Advance returns Dispatch and the exact adopted direction is recollected after revision advanced")
        val dispatch = read_value[V.DelegateTaskView](take(_invoke_("advanceDelegateTask", List(Property("sessionId", session, None)))))
        val repeated = read_value[V.DelegateTaskRequestReconstructionDescriptionResult](take(_reconstruct_()))
        Then("the destination input comes from corrected canonical request and recollection appends nothing")
        val effective = DelegateTaskRequesterRoutingFixture.state(dispatch)
        dispatch.kind shouldBe "Dispatch"
        effective.input shouldBe before.input.copy(checks = direction.policy.checks, scalaLint = "off")
        effective.copy(input = before.input) shouldBe before
        take(DelegateTask.inputC(dispatch.dispatchInput.get)) shouldBe effective.input.copy(
          delegation = effective.input.delegation.map(_.copy(role = "destination")))
        repeated.outcome shouldBe "already-reconstructed"
        When("the same Human reference is offered with a conflicting material direction")
        Files.writeString(answerfile, take(answer.copy(rationale = "A conflicting direction with the same reference").toRecord().toJsonStringC))
        val conflicting = _reconstruct_()
        Then("the stored original direction refuses replacement and keeps its checkpoint")
        conflicting.isSuccess shouldBe false
        prepare { call => call.delegateCurrentC(InstanceIdentity(session)).map { current =>
          current.revision shouldBe original.get.revision.next
          current.history.dropRight(1) shouldBe original.get.history
        } }
      }
    }

    "record an independently observed pre-Start refusal and clarify focused TestOnly without a second Human direction" in {
      Given("the already authorized broad Compile and Test request and its actual owned behavioral spec")
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/request-reconstruction")
        .toAbsolutePath), "focused-").toRealPath()
      _with_runtime_(root, "UpToDate") { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "UpToDate").size))
        def _invoke_(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "DelegateTaskService", operation = operation, properties = properties), actor)
        val session = _start_requester(_broad_request, "focused-clarification", _invoke_, prepare).sessionId
        val selection = List(Property("sessionId", session, None))
        val dispatched = read_value[V.DelegateTaskView](take(_invoke_("advanceDelegateTask", selection)))
        val before = DelegateTaskRequesterRoutingFixture.state(dispatched)
        val description = read_value[V.DelegateTaskRequestReconstructionDescriptionResult](take(_invoke_(R.describeOperation, selection)))
        val reference = V.WorkflowArtifactReference(_intake_reference.identity, _intake_reference.revision)
        val correction = _answer(description.target, session)
        val answer = correction.copy(choice = R.clarifyFocusedTest,
          checks = NonEmptyVector.of("compile", _focused_check, "test"),
          rationale = "Observed pre-Start refusal and ended destination handler", reference = reference,
          observation = correction.observation.copy(observerIdentity = Some(_intake_observer)))
        var original = Option.empty[org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord]
        prepare(_.delegateCurrentC(InstanceIdentity(session)).map { record => original = Some(record) })
        def _reconstruct_() = _invoke_(R.reconstructOperation, List(Property("input", answer, None)))
        When("the authenticated requester supplies an observation before the independent host has recorded it")
        val absent = _reconstruct_()
        Then("the observation cannot authenticate itself")
        description.outcome shouldBe "clarification-available"
        description.choices should contain(R.clarifyFocusedTest)
        absent.isSuccess shouldBe false
        Given("the independent host records the exact transport observation as evidence")
        prepare { call =>
          val binding = take(R.bindingC(take(R.directionC(answer))))
          val evidencebody = binding.hcursor.downField("delegateTaskRequestReconstruction").downField("answer")
          evidencebody.get[String]("source") shouldBe Right("IntakeObservation")
          evidencebody.get[String]("participant") shouldBe Right("participant")
          evidencebody.downField("observation").get[String]("observerIdentity") shouldBe Right(_intake_observer)
          val observed = call.executionContext.clock.instant().minusSeconds(1)
          val crossed = NativeDelegateTaskRequestReconstructionPreparation.prepareC(description, answer, reference,
            V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None), "participant", "normal-development-host", observed)
          crossed.isSuccess shouldBe false
          val evidence = take(NativeDelegateTaskRequestReconstructionPreparation.prepareC(description, answer, reference,
            V.WorkflowExecutionEvidence(Vector(reference), Some(_intake_observer), None, None), "participant", "normal-development-host", observed))
          call.createC("host_evidence", evidence.recordIdentity, evidence.record)
        }
        When("the requester records the clarification, advances, and submits the same observation again")
        val corrected = read_value[V.DelegateTaskRequestReconstructionDescriptionResult](take(_reconstruct_()))
        val dispatch = read_value[V.DelegateTaskView](take(_invoke_("advanceDelegateTask", selection)))
        val repeated = read_value[V.DelegateTaskRequestReconstructionDescriptionResult](take(_reconstruct_()))
        Then("ordinary Advance dispatches the exact corrected input in the same Session with one new history entry")
        corrected.outcome shouldBe "reconstructed-awaiting-advance"
        dispatch.sessionId shouldBe session
        dispatch.kind shouldBe "Dispatch"
        val expected = before.input.copy(checks = answer.checks.toVector)
        DelegateTaskRequesterRoutingFixture.state(dispatch) shouldBe before.copy(input = expected)
        take(DelegateTask.inputC(dispatch.dispatchInput.get)) shouldBe expected.copy(
          delegation = expected.delegation.map(_.copy(role = "destination")))
        repeated.outcome shouldBe "already-reconstructed"
        prepare { call => call.delegateCurrentC(InstanceIdentity(session)).map { current =>
          current.revision shouldBe original.get.revision.next
          current.history.dropRight(1) shouldBe original.get.history
        } }
      }
    }
  }
}
