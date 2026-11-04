package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativeExternalEvidenceInput
import org.simplemodeling.textus.smworkflow.value as V
import StandaloneFixExecution as S
import StandaloneFixExecution.given
import StandaloneFixValidationRecovery as R

/** Fixture-owned external observations are not real Human consent or process receipts.
  * @since   Nov.  1, 2026
  * @version Nov.  1, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class StandaloneFixValidationRecoverySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail("Fixture setup failed: " + other)
  }
  private val _check = GoalPhaseNativeValidation.Check("installed-source", 4, WorkflowSbtValidation.Kind.TestOnly)
  private val _workspace = "sm-workflow-repository-validation-4"
  private val _suite = "org.simplemodeling.spec.RetainedQualificationSpec"
  private val _input = S.Input("fixture-repository", "qualify-existing-candidate", "Qualify the retained selected check",
    Vector("src"), Vector(_check.identity), Vector("original-business-condition"),
    Vector(ContextReference("actual-request", "1")), false)
  private val _candidate = S.Candidate("a" * 40, "b" * 40, Vector("src/Example.scala"), "Original retained diff, never duplicated into the target")
  private val _intent = _take(S.intentC(_take(S.candidateC(_take(S.startC(_input, "human", Vector(_check))), _candidate))))
    .copy(automaticCount = 2, ordinaryCount = 1)
  private val _observation = R.Observation(R.observationSchema, "RetainedExternalValidation", 12125L,
    "actual-installed-program", _check.identity, _workspace, Vector(_suite),
    R.FileReference("retained-event-log", "advance.stderr.log"), R.FileReference(_workspace, "target/streams/test/out"),
    R.FileReference(_workspace, "target/test-reports/qualification.xml"))
  private val _started = "[cncf-sbt-event] " + Json.obj("operation" -> "test-only".asJson,
    "workspaceId" -> _workspace.asJson, "programIdentity" -> _observation.programIdentity.asJson,
    "state" -> "started".asJson, "pid" -> _observation.pid.asJson).noSpaces
  private val _terminal = "[cncf-sbt-event] " + Json.obj("operation" -> "test-only".asJson,
    "workspaceId" -> _workspace.asJson, "programIdentity" -> _observation.programIdentity.asJson,
    "state" -> "terminal".asJson, "pid" -> _observation.pid.asJson, "termination" -> "Exited(0)".asJson,
    "exitCode" -> 0.asJson, "elapsedMillis" -> 434658L.asJson).noSpaces
  private val _stream = "[info] RetainedQualificationSpec:\n[info] - qualify the original source\n" +
    "[info] Total number of tests run: 1\n[info] Suites: completed 1, aborted 0\n" +
    "[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0\n[info] All tests passed.\n"
  private val _junit = s"""<testsuite name="${_suite}" tests="1" failures="0" errors="0" skipped="0"><testcase classname="${_suite}" name="qualify the original source"/></testsuite>"""
  private val _evidence = R.ObservedEvidence(_observation, _started + "\n" + _terminal + "\n", _stream, _junit)
  private val _target = _take(R.targetFor(_intent, "SmWorkflow@default", "retained-session", 5,
    "/fixture/repository", "main", _candidate.head, _evidence))
  private val _direction = R.Direction(_target, R.choices.head, "Fixture Human attributes these actual report sources to the original candidate",
    "human", ContextReference("independent-recovery-direction", "1"))

  "Explicit retained validation collection" should {
    "preserve the original intent candidate counters and conditions while only explicit purpose correction reaches NoChange" in {
      Given("an immutable historical TestOnly intent and separately attributed successful external evidence")
      val original = _intent.asJson
      val targetwire = _target.asJson
      When("the model collects the exact explicit direction and ordinary assessment subsequently runs")
      val recovered = _take(R.collectedC(_intent, _direction))
      val restored = recovered.asJson.as[S.State].fold(error => fail(error.message), identity)
      val completed = _take(S.assessC(restored))
      Then("collection retains Assess and never fabricates native receipts or accepts source repair conditions")
      recovered.stage shouldBe "Assess"
      recovered.input shouldBe _input.copy(kind = "validation")
      recovered.candidate shouldBe _intent.candidate
      recovered.receipts shouldBe empty
      recovered.generation shouldBe _intent.generation
      recovered.automaticCount shouldBe _intent.automaticCount
      recovered.ordinaryCount shouldBe _intent.ordinaryCount
      recovered.repairControl shouldBe _intent.repairControl
      recovered.semanticReference shouldBe _intent.semanticReference
      recovered.recoveredValidation shouldBe Some(_direction)
      restored shouldBe recovered
      completed.stage shouldBe "NoChange"
      completed.conditionReport shouldBe empty
      completed.message should include("business conditions remain unassessed")
      completed.recoveredValidation.get.target.evidence.observation.source shouldBe "RetainedExternalValidation"
      targetwire.hcursor.downField("candidate").downField("diff").focus shouldBe None
      _target.asJson shouldBe targetwire
      _intent.asJson shouldBe original
      R.collectedC(recovered, _direction).isSuccess shouldBe false
      S.assessC(recovered.copy(input = _input)).isSuccess shouldBe false
      S.assessC(recovered.copy(candidate = Some(_candidate.copy(tree = "c" * 40)))).isSuccess shouldBe false
      S.assessC(recovered.copy(automaticCount = 0)).isSuccess shouldBe false
    }

    "retain legacy State encoding without a recovery record and decode only closed supported target and observation JSON" in {
      Given("the old State shape and the exact versioned recovery target")
      val legacy = _intent.asJson
      val wire = _target.asJson
      val malformed = Vector(wire.mapObject(_.add("schemaVersion", "unknown".asJson)),
        wire.mapObject(_.add("schemaVersion", Json.Null)), wire.mapObject(_.add("receipts", Json.arr())),
        wire.mapObject(_.add("candidate", _candidate.asJson)), wire.mapObject(_.add("unexpected", Json.True)))
      When("the existing State decoder and opaque closed target/observation decoders inspect their inputs")
      val decoded = legacy.as[S.State]
      val rejected = malformed.map(value => R.targetC(value.noSpaces).isSuccess)
      Then("legacy input stays code and unknown versions or hybrid/native-receipt payloads are refused")
      decoded shouldBe Right(_intent)
      legacy.hcursor.downField("recoveredValidation").focus shouldBe None
      decoded.toOption.get.input.kind shouldBe "code"
      R.targetC(wire.noSpaces).toOption shouldBe Some(_target)
      rejected shouldBe Vector.fill(malformed.size)(false)
      R.observationC(_observation.asJson.noSpaces).toOption shouldBe Some(_observation)
      R.observationC(_observation.asJson.mapObject(_.add("nativeReceipt", Json.True)).noSpaces).isSuccess shouldBe false
    }

    "refuse stale cross-Session cross-owner participant and changed report targets before collection" in {
      Given("the exact originally described target and newly observed native frontier")
      val changed = Vector(_target.copy(frontierRevision = 6), _target.copy(sessionId = "another-session"),
        _target.copy(componentInstance = "SmWorkflow@other"), _target.copy(participant = "other-human"),
        _target.copy(evidence = _evidence.copy(junitReport = _junit + "\n")))
      When("fresh target equality compares each caller's direction with its authoritative observation")
      val results = changed.map(R.currentTargetC(_target, _).isSuccess)
      Then("no stale direction or changed raw report can consume the old frontier")
      results shouldBe Vector.fill(changed.size)(false)
      R.currentTargetC(_target, _target).isSuccess shouldBe true
      R.collectedC(_intent, _direction.copy(participant = "other-human")).isSuccess shouldBe false
      R.bindingC("SmWorkflow@other", _target, R.choices.head, _direction.rationale, "human", _direction.reference).isSuccess shouldBe false
    }

    "require exact independently admitted Human binding rather than an authenticated caller choice" in {
      Given("a closed fixture binding and a provider that has no independent evidence record")
      val binding = _take(R.bindingC(_target.componentInstance, _target, _direction.choice,
        _direction.rationale, _direction.participant, _direction.reference))
      var requested = Option.empty[Json]
      val missing = new R.EvidencePort {
        def admitC(core: ActionCall.Core, reference: ContextReference, target: Json): Consequence[Unit] = {
          requested = Some(target)
          Consequence.securityPermissionDenied("No independently provisioned Human record")
        }
      }
      When("collection invokes the ordinary evidence port with the exact target answer")
      val result = R.collectC(_target.componentInstance, _intent, _direction, null, missing)
      val selected = R.selectBinding(binding)
      Then("missing evidence refuses without converting the typed direction into consent")
      result.isSuccess shouldBe false
      requested shouldBe Some(binding)
      selected shouldBe Right(binding)
      R.collectC(_target.componentInstance, _intent, _direction, null, null).isSuccess shouldBe false
      R.selectBinding(binding.mapObject(_.add("extra", Json.True))).isLeft shouldBe true
      val wrongsource = binding.mapObject(_.add("standaloneValidationRecovery",
        binding.hcursor.downField("standaloneValidationRecovery").focus.get.mapObject(fields => fields.add("answer",
          fields("answer").get.mapObject(_.add("source", "AI".asJson))))))
      R.selectBinding(wrongsource).isLeft shouldBe true
    }

    "prepare the exact standalone Human recovery metadata through the ordinary passive evidence ingress" in {
      Given("the actual described target and explicit fixture Human reference worker issuer and occurrence time")
      val artifact = V.WorkflowArtifactReference(_direction.reference.identity, _direction.reference.revision)
      val description = V.StandaloneFixValidationRecoveryDescriptionResult(_target.sessionId, R.DESCRIPTION_STATUS,
        "Fixture retained successful validation", _target.asJson.noSpaces, R.choices)
      val answer = V.StandaloneFixValidationRecoveryInput(_target.sessionId, _target.asJson.noSpaces,
        _direction.choice, _direction.rationale, artifact)
      val execution = V.WorkflowExecutionEvidence(Vector(artifact), Some(_target.participant), None, None)
      val files = Map("request" -> description.toRecord(), "result" -> answer.toRecord(),
        "reference" -> artifact.toRecord(), "execution" -> execution.toRecord())
        .view.mapValues(value => _take(value.toJsonStringC)).toMap
      val options = Map("family" -> "StandaloneFix", "kind" -> R.purpose, "request" -> "request", "result" -> "result",
        "reference" -> "reference", "execution-evidence" -> "execution", "participant" -> _target.participant,
        "issuer" -> "fixture-independent-host", "occurred-at" -> "2026-11-01T00:00:00Z",
        "collection" -> "entity:test.host.validation_evidence")
      var reads = Vector.empty[String]
      def read(path: String): Consequence[String] = {
        reads = reads :+ path
        Consequence.success(files(path))
      }
      When("normal preparation serializes the supplied facts without collecting validation or reading its report files")
      val prepared = _take(NativeExternalEvidenceInput.prepareC(options, read))
      val json = io.circe.parser.parse(_take(prepared.toJsonStringC)).fold(error => fail(error.message), identity)
      val imported = json.hcursor.downField("datastore").downArray
      val row = imported.downField("records").downArray
      val payload = row.get[String](NativeExternalEvidence.storageField).fold(error => fail(error.message), identity)
      val evidence = _take(NativeExternalEvidence.decodeStorageC(payload))
      val lookup = ThreeWorkflowExternalEvidencePorts.key(artifact.identity, artifact.revision)
      Then("the ordinary storage row retains the dedicated purpose and exact independently supplied metadata and binding")
      R.DESCRIPTION_STATUS shouldBe "decision-required"
      description.status shouldBe R.DESCRIPTION_STATUS
      reads shouldBe Vector("request", "result", "reference", "execution")
      imported.get[String]("collection").toOption shouldBe Some(options("collection"))
      row.get[String]("id").toOption shouldBe Some(NativeExternalEvidence.recordIdentity(
        NativeExternalEvidence.Purpose.StandaloneValidationRecovery, lookup))
      evidence.lookupIdentity shouldBe lookup
      evidence.purpose shouldBe R.purpose
      evidence.reference shouldBe artifact.identity
      evidence.revision shouldBe artifact.revision
      evidence.issuer shouldBe options("issuer")
      evidence.participant shouldBe _target.participant
      evidence.occurredAt shouldBe options("occurred-at")
      evidence.binding shouldBe _take(R.bindingC(_target.componentInstance, _target, answer.choice,
        answer.rationale, _target.participant, _direction.reference))
      _intent.stage shouldBe "ValidationIntent"
      _intent.receipts shouldBe empty
    }

    "reject crossed families missing Human participants and mismatched description answer reference or worker during preparation" in {
      Given("the exact generated description and recovery answer supplied to the normal evidence preparation route")
      val artifact = V.WorkflowArtifactReference(_direction.reference.identity, _direction.reference.revision)
      val description = V.StandaloneFixValidationRecoveryDescriptionResult(_target.sessionId, R.DESCRIPTION_STATUS,
        "Fixture retained successful validation", _target.asJson.noSpaces, R.choices)
      val answer = V.StandaloneFixValidationRecoveryInput(_target.sessionId, _target.asJson.noSpaces,
        _direction.choice, _direction.rationale, artifact)
      val execution = V.WorkflowExecutionEvidence(Vector(artifact), Some(_target.participant), None, None)
      val files = Map("request" -> description.toRecord(), "result" -> answer.toRecord(),
        "reference" -> artifact.toRecord(), "execution" -> execution.toRecord())
        .view.mapValues(value => _take(value.toJsonStringC)).toMap
      val options = Map("family" -> "StandaloneFix", "kind" -> R.purpose, "request" -> "request", "result" -> "result",
        "reference" -> "reference", "execution-evidence" -> "execution", "participant" -> _target.participant,
        "issuer" -> "fixture-independent-host", "occurred-at" -> "2026-11-01T00:00:00Z",
        "collection" -> "entity:test.host.validation_evidence")
      val invalidoptions = Vector(options.updated("family", "GoalPhase"), options - "participant",
        options.updated("participant", "other-human"), options.updated("issuer", " fixture-independent-host"),
        options.updated("occurred-at", "not-an-instant"))
      val invalidfiles = Vector(
        files.updated("request", _take(description.copy(status = "complete").toRecord().toJsonStringC)),
        files.updated("request", _take(description.copy(choices = R.choices.reverse).toRecord().toJsonStringC)),
        files.updated("request", _take(description.copy(sessionId = "another-session").toRecord().toJsonStringC)),
        files.updated("result", _take(answer.copy(target = _target.copy(frontierRevision = 6).asJson.noSpaces).toRecord().toJsonStringC)),
        files.updated("result", _take(answer.copy(sessionId = "another-session").toRecord().toJsonStringC)),
        files.updated("reference", _take(artifact.copy(revision = "2").toRecord().toJsonStringC)),
        files.updated("execution", _take(execution.copy(workerIdentity = Some("another-human")).toRecord().toJsonStringC)),
        files.updated("execution", _take(execution.copy(workerIdentity = None).toRecord().toJsonStringC)))
      When("the closed preparation dispatcher and typed branch compare supplied metadata with the exact target")
      val rejectedoptions = invalidoptions.map(value =>
        NativeExternalEvidenceInput.prepareC(value, path => Consequence.success(files(path))).isSuccess)
      val rejectedfiles = invalidfiles.map(value =>
        NativeExternalEvidenceInput.prepareC(options, path => Consequence.success(value(path))).isSuccess)
      Then("no crossed family ambiguous Human source or changed target can produce an admissible recovery row")
      rejectedoptions shouldBe Vector.fill(invalidoptions.size)(false)
      rejectedfiles shouldBe Vector.fill(invalidfiles.size)(false)
    }

    "refuse missing nonzero conflicting or duplicated terminal events and crossed execution attribution" in {
      Given("the selected logical workspace program identity and actual started/terminal event pair")
      val invalid = Vector(_evidence.copy(terminalLog = _started),
        _evidence.copy(terminalLog = _evidence.terminalLog.replace("Exited(0)", "Exited(1)")),
        _evidence.copy(terminalLog = _evidence.terminalLog + _terminal),
        _evidence.copy(terminalLog = _evidence.terminalLog.replace("12125", "12126")),
        _evidence.copy(terminalLog = _evidence.terminalLog.replace("434658", "0")),
        _evidence.copy(observation = _observation.copy(programIdentity = "another-program")),
        _evidence.copy(observation = _observation.copy(workspace = "another-workspace")),
        _evidence.copy(observation = _observation.copy(pid = 0)),
        _evidence.copy(observation = _observation.copy(junitReport = _observation.junitReport.copy(workspace = "other-report-workspace"))))
      When("external evidence qualification matches the exact program check workspace and completed process")
      val results = invalid.map(R.evidenceValid)
      Then("exit-zero labels alone and unknown or conflicting process observations do not authorize collection")
      R.evidenceValid(_evidence) shouldBe true
      results shouldBe Vector.fill(invalid.size)(false)
    }

    "refuse zero failed skipped canceled inconsistent or foreign-suite test reports and external XML entities" in {
      Given("a passing nonempty JUnit report corroborated by the selected SBT stream")
      val invalid = Vector(_evidence.copy(junitReport = _junit.replace("tests=\"1\"", "tests=\"0\"")),
        _evidence.copy(junitReport = _junit.replace("failures=\"0\"", "failures=\"1\"")),
        _evidence.copy(junitReport = _junit.replace("skipped=\"0\"", "skipped=\"1\"")),
        _evidence.copy(junitReport = _junit.replace("<testcase", "<testcase").replace("/></testsuite>", "><failure/></testcase></testsuite>")),
        _evidence.copy(junitReport = _junit.replace(_suite, "org.other.ForeignSpec")),
        _evidence.copy(junitReport = "<!DOCTYPE testsuite SYSTEM 'https://example.invalid/entity'>" + _junit),
        _evidence.copy(sbtStream = _stream.replace("canceled 0", "canceled 1")),
        _evidence.copy(sbtStream = _stream.replace("Total number of tests run: 1", "Total number of tests run: 0")),
        _evidence.copy(sbtStream = _stream.replace("RetainedQualificationSpec:", "ForeignSpec:")))
      When("secure XML parsing and exact actual testcase and stream counts qualify each report")
      val results = invalid.map(R.evidenceValid)
      Then("unsupported external entities and every incomplete or unsuccessful report refuse")
      results shouldBe Vector.fill(invalid.size)(false)
    }

    "refuse recovery outside the narrow uncollected standalone legacy TestOnly intent" in {
      Given("the original code input and its single pending test with no semantic completion")
      val invalid = Vector(_intent.copy(stage = "Assess"), _intent.copy(pendingCheck = Some("another-check")),
        _intent.copy(input = _input.copy(kind = "validation")), _intent.copy(input = _input.copy(reviewRequired = true)),
        _intent.copy(input = _input.copy(task = Some(S.Task("direct-instruction", Vector.empty)))),
        _intent.copy(semanticReference = Some(ContextReference("already-completed-work", "1"))),
        _intent.copy(selected = Vector(_check.copy(kind = WorkflowSbtValidation.Kind.Compile))),
        _intent.copy(candidate = None))
      When("the recovery gate inspects each unsupported state without staging or process execution")
      val results = invalid.map(R.eligible)
      Then("only the exact initial legacy collection contract is eligible")
      R.eligible(_intent) shouldBe true
      results shouldBe Vector.fill(invalid.size)(false)
      ThreeWorkflowLauncherAssembly.publishedMcpOperations should contain("StandaloneFixService.describeStandaloneFixValidationRecovery")
      ThreeWorkflowLauncherAssembly.publishedMcpOperations should contain("StandaloneFixService.recoverStandaloneFixValidation")
    }
  }
}
