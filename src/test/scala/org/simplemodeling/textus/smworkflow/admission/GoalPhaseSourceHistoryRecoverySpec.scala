package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseSourceHistoryRecovery as R
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Immutable specification inputs are not production Human consent.
  * @since   Oct. 21, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseSourceHistoryRecoverySpec extends AnyWordSpec with NativeGoalSourceHistoryCliFixture {
  private val _snapshot = O.Snapshot("a" * 40, "b" * 40)
  private def _target: R.RecoveryTarget = R.RecoveryTarget("session", "plan", "2", _snapshot, _snapshot,
    "goal-phase", "1", "participant", "step-1", "50", "52",
    "session:ordered-candidate:50:source-history-intent-0", "1", "52", Vector(sourcePath), Vector(sourcePath),
    Vector.empty, enabledSettings(ruleKey), GoalPhaseNativeCandidate.Payload("50", "step-1", Vector(sourcePath),
      Vector.empty, None, Vector.empty, None), GoalPhaseNativeSourceHistory.Payload("50", "step-1", sourcePath,
      "goldenport", "native-cli-external-goldenport-v1", "2026-10-21", true, None))

  "Source history recovery target and direction" should {
    "require a complete explicit later-intent prefix while preserving the original index-zero wire" in {
      Given("P116-SOURCE-HISTORY-RECOVERY-002: two normally encoded successful prefix pairs before the unresolved third intent")
      val legacy = _target
      val paths = Vector("src/main/scala/First.scala", "src/main/scala/Second.scala", sourcePath)
      val prefix = paths.take(2).zipWithIndex.map { (path, index) =>
        val intent = legacy.intent.copy(path = path)
        R.CompletedPrefix(index, "session:ordered-candidate:50:source-history-intent-" + index, "1",
          (52 + index * 2).toString, "session:ordered-candidate:50:source-history-result-" + index, "1",
          (53 + index * 2).toString, intent, intent.copy(outcome = Some(if (index == 0) "updated" else "unchanged")))
      }
      val target = legacy.copy(candidatePaths = paths, ownedPaths = paths,
        inspection = legacy.inspection.copy(selectedPaths = paths), intentIndex = 2, completedPrefix = prefix,
        intentIdentity = "session:ordered-candidate:50:source-history-intent-2", intentRecordRevision = "56", frontierRevision = "56")
      val wire = target.asJson
      When("the later target traverses public Record naming and ordinary Human input and binding selection")
      val restored = R.targetC(RuntimeContext.PropertyNameContext.default.transformRecord(R.targetRecord(target)))
      val question = NativeHumanEvidenceFixture.goalInput.copy(sourceHistoryRecovery = Some(target))
      val binding = take(R.bindingC("component", target, "AuthorizeNewAttempt", "Accept the remaining unknown effect",
        target.participant, ContextReference("later-human", "1")))
      Then("all prefix references revisions payloads and indices survive and legacy index-zero bytes remain unchanged")
      take(restored) shouldBe target
      GoalPhaseOrderedHumanDecision.inputCodec.decode(GoalPhaseOrderedHumanDecision.inputCodec.encode(question)) shouldBe Right(question)
      R.selectBinding(binding) shouldBe Right(binding)
      wire.hcursor.get[String]("prefixContract").toOption shouldBe Some("sm-workflow.goal-phase-source-history-recovery-prefix.v2")
      legacy.asJson.hcursor.downField("prefixContract").focus shouldBe None
      legacy.asJson.hcursor.downField("intentIndex").focus shouldBe None
      legacy.asJson.hcursor.downField("completedPrefix").focus shouldBe None

      Given("independent evidence binding the complete later prefix and a different structurally valid prefix outcome")
      val declared = NativeExternalEvidence.Declaration("workflow", org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(
        org.simplemodeling.model.datatype.EntityCollectionId("smworkflow", "cli", "host_evidence")), "test-host")
      val now = Instant.parse("2026-10-21T00:00:00Z")
      val evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, R.purpose, "lookup", "later-human", "1",
        declared.issuer, target.participant, binding, now.toString)
      val changed = target.copy(completedPrefix = prefix.updated(0,
        prefix.head.copy(result = prefix.head.result.copy(outcome = Some("unchanged")))))
      val changedbinding = take(R.bindingC("component", changed, "AuthorizeNewAttempt", "Accept the remaining unknown effect",
        target.participant, ContextReference("later-human", "1")))
      When("the evidence port observes the original exact binding and the crossed complete prefix binding")
      val accepted = NativeExternalEvidence.observeC(evidence, declared, NativeExternalEvidence.Purpose.SourceHistoryRecovery,
        "lookup", binding, now)
      val crossedprefix = NativeExternalEvidence.observeC(evidence, declared, NativeExternalEvidence.Purpose.SourceHistoryRecovery,
        "lookup", changedbinding, now)
      Then("the prefix remains part of Human authority even when both target shapes are independently valid")
      accepted.isSuccess shouldBe true
      R.decodeTarget(changed.asJson) shouldBe Right(changed)
      crossedprefix.isSuccess shouldBe false

      Given("missing fields, crossed pairs, unsupported results, duplicate paths and unsafe revision arithmetic")
      val invalid = Vector(target.copy(intentIndex = 1), target.copy(completedPrefix = prefix.reverse),
        target.copy(completedPrefix = prefix.drop(1)), target.copy(completedPrefix = prefix.updated(0, prefix.head.copy(index = 1))),
        target.copy(completedPrefix = prefix.updated(0, prefix.head.copy(resultRevision = "2"))),
        target.copy(completedPrefix = prefix.updated(0, prefix.head.copy(resultRecordRevision = "54"))),
        target.copy(completedPrefix = prefix.updated(0, prefix.head.copy(intentIdentity = prefix(1).intentIdentity))),
        target.copy(completedPrefix = prefix.updated(0, prefix.head.copy(result = prefix.head.result.copy(outcome = Some("missing"))))),
        target.copy(completedPrefix = prefix.updated(0, prefix.head.copy(result = prefix.head.result.copy(outcome = Some("excluded"))))),
        target.copy(completedPrefix = prefix.updated(0, prefix.head.copy(result = prefix.head.result.copy(updatedOn = "2026-10-20")))),
        target.copy(completedPrefix = prefix.updated(0, prefix.head.copy(intent = prefix.head.intent.copy(path = sourcePath)))),
        target.copy(sourceRevision = Long.MaxValue.toString), target.copy(intentIndex = Int.MaxValue))
      val incomplete = Vector("prefixContract", "intentIndex", "completedPrefix").map(name => wire.mapObject(_.remove(name))) ++
        Vector(wire.mapObject(_.add("completedPrefix", Json.Null)), wire.mapObject(_.add("prefixContract", Json.fromString("v1"))),
          wire.mapObject(_.add("intentIndex", Json.fromInt(0))))
      When("each invalid target and a crossed prefix Human binding are decoded")
      val refused = invalid.map(value => R.decodeTarget(value.asJson)) ++ incomplete.map(R.decodeTarget)
      val crossed = binding.mapObject(_.add("sourceHistoryRecovery", binding.hcursor.downField("sourceHistoryRecovery").focus.get
        .mapObject(_.add("target", invalid.head.asJson))))
      Then("none can become a legacy permission or a later-intent authorization")
      refused.foreach(_.isLeft shouldBe true)
      R.selectBinding(crossed).isLeft shouldBe true
    }

    "retain the optional exact target in ordinary Human input while reading historical inputs without it" in {
      Given("SOURCE-HISTORY-CONTINUATION-002: historical ordered Human input and an optional complete recovery target")
      val legacy = NativeHumanEvidenceFixture.goalInput
      val recovery = legacy.copy(result = legacy.result.copy(outcome = "completed"), reason = R.message,
        sourceHistoryRecovery = Some(_target))
      When("the existing ordered Human codec writes and reads old and recovery question inputs")
      val oldwire = GoalPhaseOrderedHumanDecision.inputCodec.encode(legacy)
      val newwire = GoalPhaseOrderedHumanDecision.inputCodec.encode(recovery)
      val restored = GoalPhaseOrderedHumanDecision.inputCodec.decode(newwire)
      Then("historical absence stays absent and the complete optional target survives without a schema migration")
      oldwire.hcursor.downField("sourceHistoryRecovery").focus shouldBe None
      GoalPhaseOrderedHumanDecision.inputCodec.decode(oldwire) shouldBe Right(legacy)
      restored shouldBe Right(recovery)
      newwire.hcursor.downField("sourceHistoryRecovery").focus shouldBe Some(_target.asJson)

      Given("a present malformed or incomplete recovery target in the saved question")
      val malformed = Vector(Json.Null, Json.True, Json.obj(), _target.asJson.mapObject(_.remove("intentRecordRevision")))
      When("each malformed target is decoded through the same ordered Human input codec")
      val rejected = malformed.map(value => GoalPhaseOrderedHumanDecision.inputCodec.decode(
        newwire.mapObject(_.add("sourceHistoryRecovery", value))))
      Then("present malformed recovery authority cannot silently become a legacy question")
      rejected.foreach(_.isLeft shouldBe true)
    }

    "roundtrip public Record naming and preserve the unchanged legacy native intent codec" in {
      Given("docs/spec/goal-phase-source-history-recovery.md R1: the exact first unresolved native intent")
      val target = _target
      val old = GoalPhaseNativeSourceHistory.codec.encode(target.intent)
      val rendered = RuntimeContext.PropertyNameContext.default.transformRecord(R.targetRecord(target))
      When("the public target and the original intent are decoded")
      val restored = R.targetC(rendered)
      val intent = GoalPhaseNativeSourceHistory.codec.decode(old)
      Then("all binding fields and original intent bytes retain their meaning")
      take(restored) shouldBe target
      intent shouldBe Right(target.intent)
      intent.map(GoalPhaseNativeSourceHistory.codec.encode) shouldBe Right(old)
      val json = io.circe.parser.parse(take(rendered.toJsonStringC)).toOption.get
      R.targetC(GoalPhaseNativeMetadata.record(json.mapObject(_.add("sourceRevision", Json.fromString("50")))))
        .isSuccess shouldBe false
    }

    "refuse gaps crossed targets staging results and malformed provenance" in {
      Given("R1,R2: a complete selected-Step source target and contradictory fields")
      val target = _target
      val invalid = Vector(target.copy(sessionId = "another-session"), target.copy(sourceRevision = "49"),
        target.copy(frontierRevision = "53"), target.copy(intentRevision = "2"),
        target.copy(intentRecordRevision = "53"), target.copy(step = "step-2"),
        target.copy(inspection = target.inspection.copy(stagedPaths = Vector(sourcePath))),
        target.copy(inspection = target.inspection.copy(candidate = Some(_snapshot))),
        target.copy(intent = target.intent.copy(outcome = Some("updated"))),
        target.copy(intent = target.intent.copy(path = "foreign.scala")),
        target.copy(intent = target.intent.copy(updatedOn = "impossible-date")),
        target.copy(intent = target.intent.copy(providerIdentity = "another-provider")),
        target.copy(historyPolicy = Json.Null), target.copy(candidatePaths = Vector.empty))
      When("each malformed or foreign target is decoded before evidence admission")
      val refused = invalid.map(value => R.decodeTarget(value.asJson))
      Then("none can identify an eligible retained first intent")
      R.decodeTarget(target.asJson) shouldBe Right(target)
      refused.foreach(_.isLeft shouldBe true)
    }

    "bind independent Human evidence to its own purpose participant reference issuer and complete target" in {
      Given("R3: a supplied Human answer and independently provisioned test-owned evidence")
      val target = _target
      val reference = ContextReference("source-history-human", "1")
      val binding = take(R.bindingC("component", target, "AuthorizeNewAttempt", "Accept possible repeated normalization",
        "participant", reference))
      val declared = NativeExternalEvidence.Declaration("workflow", org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(
        org.simplemodeling.model.datatype.EntityCollectionId("smworkflow", "cli", "host_evidence")), "test-host")
      val now = Instant.parse("2026-10-21T00:00:00Z")
      val evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, R.purpose, "lookup", reference.identity,
        reference.revision, declared.issuer, "participant", binding, now.toString)
      When("the exact evidence and crossed independent facts are observed")
      val accepted = NativeExternalEvidence.observeC(evidence, declared, NativeExternalEvidence.Purpose.SourceHistoryRecovery,
        "lookup", binding, now)
      val crossed = Vector(evidence.copy(purpose = GoalPhaseValidationRecovery.purpose), evidence.copy(participant = "other"),
        evidence.copy(reference = "other-answer"), evidence.copy(revision = "2"), evidence.copy(issuer = "other-host"),
        evidence.copy(binding = take(R.bindingC("component", target.copy(planRevision = "3"), "AuthorizeNewAttempt",
          "Accept possible repeated normalization", "participant", reference))))
      Then("only its own exact direction is admitted and no execution receipt is created")
      accepted.isSuccess shouldBe true
      crossed.foreach(value => NativeExternalEvidence.observeC(value, declared, NativeExternalEvidence.Purpose.SourceHistoryRecovery,
        "lookup", binding, now).isSuccess shouldBe false)
      val direction = R.Direction(target, reference, "participant", "Accept possible repeated normalization")
      R.codec.decode(R.codec.encode(direction)) shouldBe Right(direction)
      R.codec.decode(R.codec.encode(direction.copy(participant = "other"))).isLeft shouldBe true
      binding.hcursor.downField("validationRecovery").focus shouldBe None
      R.selectBinding(binding.mapObject(_.add("aiClaimsConsent", Json.True))) shouldBe Right(binding)
    }

    "prepare passive standard import data only for matching supplied Human facts" in {
      Given("R3: the actual described target and separately supplied answer/reference/worker")
      val target = _target
      val description = V.GoalPhaseSourceHistoryRecoveryDescriptionResult(target.sessionId, "decision-required", R.message,
        "component", R.targetRecord(target), R.choices, R.recoveryOperation)
      val reference = V.WorkflowArtifactReference("human", "1")
      val answer = V.GoalPhaseSourceHistoryRecoveryAnswer("Defer", "Keep the unknown effect retained")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      When("the passive preparation is called with matching and crossed workers")
      val prepared = org.simplemodeling.textus.smworkflow.preparation.NativeSourceHistoryRecoveryPreparation.prepareC(
        description, answer, reference, execution, "participant", "test-host", Instant.parse("2026-10-21T00:00:00Z"))
      val crossed = org.simplemodeling.textus.smworkflow.preparation.NativeSourceHistoryRecoveryPreparation.prepareC(
        description, answer, reference, execution, "other", "test-host", Instant.parse("2026-10-21T00:00:00Z"))
      Then("only inert own-purpose import data is returned; mismatched Human provenance refuses")
      take(prepared).recordIdentity should startWith(R.purpose + ":")
      crossed.isSuccess shouldBe false
    }
  }
}
