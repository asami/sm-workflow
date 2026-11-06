package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.time.LocalDate
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceId}
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseSourceHistoryAutomatic as A
import GoalPhaseSourceHistoryNormalization as N
import SplitPhaseExplicitPartitionFixture.take

/** Specification-owned values prove codec/matching behavior, never native
  * authority, actual transport termination or a historical File outcome.
  * @since   Nov.  6, 2026
  * @version Nov.  6, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseSourceHistoryAutomaticSpec extends AnyWordSpec with NativeGoalSourceHistoryCliFixture {
  private val _invocation = A.Invocation("spec-component", "session", "GoalPhaseService.advanceGoalPhase",
    "b32f8d9cb7534bfd9f00dab409c5424b")
  private def _target(pending: Boolean = false): N.Target = {
    val rule = take(WorkflowSourceHistoryRule.decodeC(enabledSettings(ruleKey)))
    val legacy = SourceHistoryNormalizationSpecificationFixture.target(sourcePath, enabledSettings(ruleKey),
      rule.identity, prefixSize = 2, pending = pending)
    legacy.copy(recovery = legacy.recovery.copy(intent = legacy.recovery.intent.copy(
      invocation = Some(_invocation), historyPolicy = Some(enabledSettings(ruleKey)))))
  }
  private def _observation(target: N.Target): A.CallerObservation = A.CallerObservation(A.observationSchema,
    target, _invocation, ContextReference("actual-caller-owned-terminal-result", "1"),
    "2026-11-06T00:00:00Z", "exited", Some(1))
  private def _public_target(target: N.Target): Json = {
    val projected = RuntimeContext.PropertyNameContext.default.transformRecord(N.targetRecord(target))
    io.circe.parser.parse(take(projected.toJsonStringC)).fold(error => fail(error.message), identity)
  }

  "E1 Legacy bytes and associated source-history intent" should afterWord(
    "in spec:goal-phase-source-history-recovery, example:E1, rules:SHR-AUTO-01, phase:common-repair") {
    "retain the original v1 shape and require the complete v2 invocation and rule" in {
      Given("docs/spec/goal-phase-source-history-recovery.md SHR-AUTO-01 E1: an original v1 intent with its original date")
      val legacy = _target().recovery.intent.copy(invocation = None, historyPolicy = None)
      val original = Json.obj("schemaVersion" -> "sm-workflow.goal-phase-native-source-history.v1".asJson,
        "payload" -> Json.obj("sourceRevision" -> legacy.sourceRevision.asJson, "step" -> legacy.step.asJson,
          "path" -> legacy.path.asJson, "style" -> legacy.style.asJson, "providerIdentity" -> legacy.providerIdentity.asJson,
          "updatedOn" -> legacy.updatedOn.asJson, "dateUncertain" -> legacy.dateUncertain.asJson, "outcome" -> Json.Null))
      val codec = GoalPhaseNativeSourceHistory.codec
      When("the same legacy intent and a new associated intent are encoded and decoded")
      val fresh = _target().recovery.intent
      val wire = codec.encode(fresh)
      Then("legacy bytes remain exact and the explicit v2 retains both invocation and complete rule")
      codec.encode(legacy).noSpaces shouldBe original.noSpaces
      codec.decode(original) shouldBe Right(legacy)
      wire.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-native-source-history.v2")
      codec.decode(wire) shouldBe Right(fresh)
      val result = fresh.copy(outcome = Some("updated"))
      codec.decode(codec.encode(result)) shouldBe Right(result)
      result.invocation shouldBe fresh.invocation
      result.historyPolicy shouldBe fresh.historyPolicy
      Vector("invocation", "historyPolicy").foreach { key =>
        codec.decode(wire.mapObject(fields => fields.add("payload", fields("payload").get.mapObject(_.remove(key))))).isLeft shouldBe true
        codec.decode(wire.mapObject(fields => fields.add("payload", fields("payload").get.mapObject(_.add(key, Json.Null))))).isLeft shouldBe true
      }
      codec.decode(wire.mapObject(_.add("schemaVersion", "sm-workflow.goal-phase-native-source-history.v1".asJson))).isLeft shouldBe true
      codec.decode(codec.encode(fresh.copy(historyPolicy = Some(enabledSettings(ruleKey).mapObject(_.add("excess", Json.True)))))).isLeft shouldBe true
      codec.decode(codec.encode(fresh.copy(invocation = Some(_invocation.copy(operation = "submitGoalPhaseWorkResult"))))).isLeft shouldBe true
    }
  }

  "E2 Closed finite caller transport observation" should afterWord(
    "in spec:goal-phase-source-history-recovery, example:E2, rules:SHR-AUTO-01, phase:common-repair") {
    "accept the unchanged public target projection while closing every other observation field and retaining strict durable targets" in {
      Given("docs/spec/goal-phase-source-history-recovery.md SHR-AUTO-01 E2: the actual public Record naming projection of a complete target and an otherwise canonical finite observation")
      val target = _target()
      val observation = _observation(target)
      val publictarget = _public_target(target)
      val publicbytes = publictarget.noSpaces.getBytes(UTF_8).toVector
      val wire = observation.asJson.mapObject(_.add("target", publictarget))
      When("the Resource boundary decodes the copied public target without changing the finite factual remainder")
      val decoded = A.decodeObservation(wire)
      Then("only the target is normalized into its exact internal contract and durable codecs remain strict")
      publictarget.hcursor.downField("recovery").get[String]("session_id") shouldBe Right("session")
      decoded shouldBe Right(observation)
      publictarget.noSpaces.getBytes(UTF_8).toVector shouldBe publicbytes
      A.matchObservationC(_invocation.componentInstance, target, decoded.toOption.get).isSuccess shouldBe true
      N.decodeTarget(publictarget).isLeft shouldBe true
      val selection = A.Selection(target, _invocation, observation)
      val durable = A.codec.encode(selection)
      A.codec.decode(durable) shouldBe Right(selection)
      A.codec.decode(durable.mapObject(fields => fields.add("payload", fields("payload").get
        .mapObject(_.add("target", publictarget))))).isLeft shouldBe true
      Vector(publictarget.mapObject(_.add("extra", Json.True)),
        publictarget.mapObject(_.add("frontier_revision", Json.Null)),
        publictarget.mapObject(_.add("frontierRevision", target.frontierRevision.asJson))).foreach { altered =>
        A.decodeObservation(wire.mapObject(_.add("target", altered))).isLeft shouldBe true
      }
      A.decodeObservation(wire.mapObject(_.add("observed_at", observation.observedAt.asJson))).isLeft shouldBe true
      A.decodeObservation(wire.mapObject(_.add("sourceReference", Json.Null))).isLeft shouldBe true
      val stale = wire.mapObject(_.add("target", publictarget.mapObject(_.add("frontier_cause", "stale-cause".asJson))))
      val stalefact = A.decodeObservation(stale).toOption.get
      stalefact.target.frontierCause shouldBe "stale-cause"
      A.matchObservationC(_invocation.componentInstance, target, stalefact).isSuccess shouldBe false
    }

    "reject missing explicit-null extra malformed and incompatible termination fields" in {
      Given("docs/spec/goal-phase-source-history-recovery.md SHR-AUTO-01 E2: one complete test-owned caller observation")
      val observation = _observation(_target())
      val wire = observation.asJson
      When("closed observation values are acquired without interpreting them as consent")
      val unknown = observation.copy(termination = "unknown", exitCode = None)
      val live = observation.copy(termination = "live", exitCode = None)
      Then("only canonical finite transport shapes decode and exitCode belongs only to exited")
      A.decodeObservation(wire) shouldBe Right(observation)
      A.decodeObservation(unknown.asJson) shouldBe Right(unknown)
      A.decodeObservation(live.asJson) shouldBe Right(live)
      wire.asObject.get.keys.foreach(key => A.decodeObservation(wire.mapObject(_.remove(key))).isLeft shouldBe true)
      A.decodeObservation(wire.mapObject(_.add("humanAnswer", Json.True))).isLeft shouldBe true
      A.decodeObservation(live.asJson.mapObject(_.add("exitCode", Json.Null))).isLeft shouldBe true
      Vector(observation.copy(termination = "exited", exitCode = None), observation.copy(termination = "live"),
        observation.copy(termination = "timed-out"), observation.copy(observedAt = "yesterday"),
        observation.copy(sourceReference = ContextReference("", "1")),
        observation.copy(invocation = _invocation.copy(identity = "not-an-invocation"))).foreach(value =>
          A.decodeObservation(value.asJson).isLeft shouldBe true)
    }
  }

  "E3 Exact associated frontier matching" should afterWord(
    "in spec:goal-phase-source-history-recovery, example:E3, rules:SHR-AUTO-01, phase:common-repair") {
    "admit only exited for the exact Component Session intent invocation full rule and native target" in {
      Given("docs/spec/goal-phase-source-history-recovery.md SHR-AUTO-01 E3: the complete original target with old successful v1 prefixes")
      val target = _target()
      val observation = _observation(target)
      When("terminal and crossed factual observations are matched against the reconstructed native target")
      val crossed = Vector(observation.copy(termination = "live", exitCode = None),
        observation.copy(termination = "unknown", exitCode = None),
        observation.copy(invocation = _invocation.copy(componentInstance = "other-component")),
        observation.copy(invocation = _invocation.copy(sessionId = "other-session")),
        observation.copy(invocation = _invocation.copy(operation = "GoalPhaseService.startGoalPhase")),
        observation.copy(invocation = _invocation.copy(identity = "1395912e582b44fdb47b170fe2baf326")),
        observation.copy(target = target.copy(frontierCause = "stale-cause")),
        observation.copy(target = target.copy(recovery = target.recovery.copy(intentIdentity = "crossed-intent"))))
      Then("only the exact terminal association admits selection and old v1 successful prefixes remain reusable facts")
      A.matchObservationC(_invocation.componentInstance, target, observation).isSuccess shouldBe true
      A.matchObservationC("other-component", target, observation).isSuccess shouldBe false
      crossed.foreach(value => A.matchObservationC(_invocation.componentInstance, target, value).isSuccess shouldBe false)
      target.recovery.completedPrefix.foreach(_.intent.invocation shouldBe None)
      val selected = A.Selection(target, _invocation, observation)
      A.codec.decode(A.codec.encode(selected)) shouldBe Right(selected)
      val unresolved = A.Reconciliation(selected, None, None)
      A.reconciliationCodec.decode(A.reconciliationCodec.encode(unresolved)) shouldBe Right(unresolved)
      val failed = unresolved.copy(outcome = Some("failed"), diagnostic = Some("Actual managed source is ambiguous"))
      A.reconciliationCodec.decode(A.reconciliationCodec.encode(failed)) shouldBe Right(failed)
      A.reconciliationCodec.decode(A.reconciliationCodec.encode(unresolved.copy(outcome = Some("old-success")))).isLeft shouldBe true
      A.reconciliationCodec.decode(A.reconciliationCodec.encode(failed.copy(diagnostic = None))).isLeft shouldBe true
      N.codec.decode(A.codec.encode(selected)).isLeft shouldBe true
      A.codec.decode(N.codec.encode(N.Direction(target, N.choices.head, "Explicit old API only", "participant",
        ContextReference("held-policy", "1"), true))).isLeft shouldBe true
      target.recovery.intent.outcome shouldBe None
    }

    "retain legacy UNKNOWN and its genuine-question boundary without inventing an invocation or Human answer" in {
      Given("docs/spec/goal-phase-source-history-recovery.md SHR-AUTO-01 E3: legacy pending-question target data with no invocation association")
      val target = _target(pending = true)
      val legacy = target.copy(recovery = target.recovery.copy(intent = target.recovery.intent.copy(
        invocation = None, historyPolicy = None)))
      val oldwire = legacy.asJson
      When("a caller supplies an otherwise terminal observation for unassociable legacy data")
      val refused = A.matchObservationC(_invocation.componentInstance, legacy, _observation(legacy))
      Then("the exact missing fact refuses automatic authority and leaves the pending target and UNKNOWN unchanged")
      refused.isSuccess shouldBe false
      refused.toString should include("no retained invocation association")
      A.validSelection(A.Selection(legacy, _invocation, _observation(legacy))) shouldBe false
      legacy.asJson shouldBe oldwire
      legacy.recovery.intent.outcome shouldBe None
      legacy.pendingQuestion should not be empty
      // Data alone does not create a genuine native question or exercise supersession.
    }
  }

  "E4 Read-only current postcondition" should afterWord(
    "in spec:goal-phase-source-history-recovery, example:E4, rules:SHR-AUTO-01, phase:common-repair") {
    "observe conformance or a unique correction without File write authority and retain concrete ambiguity" in {
      Given("docs/spec/goal-phase-source-history-recovery.md SHR-AUTO-01 E4: current text and the original retained date with read-only File authority")
      val target = _target()
      val rule = take(WorkflowSourceHistoryRule.decodeC(target.recovery.historyPolicy))
      val day = LocalDate.parse(target.recovery.intent.updatedOn)
      val conformant = take(WorkflowSourceHistoryRule.applyC(rule, WorkflowSourceHistoryRule.Input(sourcePath,
        changedText, day, None, None, true))).text
      val ambiguous = "/**\n * @version Oct. 20, 2026\n * @version Oct. 21, 2026\n */\n" + changedText
      Vector(changedText -> "correction-required", conformant -> "observed-current-postcondition", ambiguous -> "failed").foreach {
        (text, expected) =>
          val root = WorkflowFileRuntimeFixture.fresh()
          val file = root.resolve(sourcePath)
          Files.createDirectories(file.getParent)
          Files.writeString(file, text)
          val modified = Files.getLastModifiedTime(file)
          WorkflowFileRuntimeFixture.withCore(root, workspace = "sm-workflow-repository", access = Some("read-only"),
            mapping = enabledSettings) { core =>
            When("the current managed comment is observed before transport termination is established")
            val observation = take(A.currentObservationC(core, target))
            Then("current text is reported without claiming the old outcome or termination and bytes and mtime stay intact")
            observation should include(expected)
            observation should include("does not prove the old File outcome or invocation termination")
            if (expected == "failed") observation should include("duplicate since or latest version")
            Files.readString(file) shouldBe text
            Files.getLastModifiedTime(file) shouldBe modified
          }
      }
    }
  }
}
