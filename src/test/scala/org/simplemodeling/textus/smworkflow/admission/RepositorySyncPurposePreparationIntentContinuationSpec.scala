package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativeRepositoryPurposePreparationContinuationPreparation as Preparation
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncPurposePreparationIntentContinuation as I
import RepositorySyncPurposePreparationContinuation as Previous
import RepositorySyncPurpose as P
import RepositorySyncNativeProgressStore as N
import SplitPhaseExplicitPartitionFixture.take
import P.given

/** Pure authored contracts only. These observations and directions are not
  * retained native work or actual Human lifecycle evidence.
  * @since Nov. 6, 2026
  * @version Nov. 6, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncPurposePreparationIntentContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _f = RepositorySyncPurposeRepairScopeFixture
  private val _progress = _f.opaqueProgress.copy(cursor = 1, slotCursor = 1, attempt = 0,
    baselineTree = None, candidate = None, receipts = Vector.empty, assessment = None, selection = None,
    review = None, reusable = Vector.empty, commits = Vector("f" * 40), hostContinuation = None,
    ownership = None, projectedWorktree = None, preparationNamespace = None)
  private val _actual = _f.actual.copy(observed = _f.actual.observed.copy(head = _progress.commits.last))
  private val _index = "5" * 40
  private val _prefix = RepositorySyncPurposeRuntime.attemptPrefix(_progress)
  private val _target = I.Target("component", "session", _f.context, _progress.request.root,
    _f.context.remoteEndpointIdentity, "20", "native-repository:session:" + _prefix + "-prepare-intent",
    _prefix + "-prepare-intent", "20", P.repairProgressJson(_progress).noSpaces,
    RepositorySyncPurposeContentContinuation.canonical(_actual.asJson).noSpaces, _index, _actual.observed.head,
    "", I.policy, "UNKNOWN")
  private val _reference = ContextReference("fixture-independent-human", "1")

  "RS-PREP-02 exact intent target" should {
    "roundtrip the complete public target while preserving opaque progress text and refusing partial shapes" in {
      Given("an initial later Purpose attempt with one completed commit and opaque journal and effect facts")
      val wire = _target.asJson
      val malformed = wire.asObject.get.keys.toVector.flatMap(key => Vector(wire.mapObject(_.remove(key)),
        wire.mapObject(_.add(key, Json.Null)), wire.mapObject(_.add(key, Json.True)))) ++ Vector(
        wire.mapObject(_.add("retainedRejection", Json.Null)), wire.mapObject(_.add("failureRevision", "21".asJson)),
        wire.mapObject(_.add("process", Json.obj())), wire.mapObject(_.add("priorWorkerEnded", Json.True)))
      When("strict internal and public Record codecs consume the described observation")
      val restored = take(I.targetC(I.targetRecord(_target)))
      Then("the exact strings survive and missing null invented receipt or extended contracts refuse")
      restored shouldBe _target
      I.decodeTarget(wire) shouldBe Right(_target)
      I.progress(restored) shouldBe Right(_progress)
      restored.progressSnapshot should include("arbitrary_snake_case")
      restored.progressSnapshot should include("arbitrary.dotted.key")
      restored.progressSnapshot should include("optionalEffect")
      malformed.foreach(value => I.decodeTarget(value).isLeft shouldBe true)
      Previous.validatePublicTarget(wire) shouldBe Right(())
      wire.hcursor.downField("failureRevision").focus shouldBe None
      wire.hcursor.downField("retainedRejection").focus shouldBe None
    }
    "reject foreign provenance stale revisions and policy substitutions without decoding an older contract" in {
      Given("one exact preparation intent target without a result revision")
      val wrong = Vector(_target.copy(sessionId = "foreign"), _target.copy(frontierRevision = "21"),
        _target.copy(intentRevision = "19"), _target.copy(intentKey = "foreign-prepare-intent"),
        _target.copy(frontierCause = "native-repository:other:" + _target.intentKey),
        _target.copy(sourceHead = "a" * 40), _target.copy(originalResultStatus = "FAILED"),
        _target.copy(continuationPolicy = Previous.policy), _target.copy(continuationPolicy = I.policy + "-future"))
      When("each crossed target is presented through explicit policy selection")
      val refused = wrong.map(value => Previous.validatePublicTarget(value.asJson))
      Then("none can fall through to an older or partial decoder")
      refused.foreach(_.isLeft shouldBe true)
      I.decodeTarget(_target.asJson.mapObject(_.add("progressSnapshot", Json.obj()))).isLeft shouldBe true
      I.decodeTarget(_target.copy(progressSnapshot = _target.progressSnapshot + " ").asJson).isLeft shouldBe true
    }
  }

  "RS-PREP-02 initial current-group continuation" should {
    "preserve the complete later-Purpose progress and counters while changing only its namespace" in {
      Given("cursor one attempt zero with no repair baseline and the exact completed-prefix index")
      val before = _progress.copy(cursor = 0, slotCursor = 0, commits = Vector.empty,
        candidate = Some(_f.candidate.copy(commit = _progress.commits.last, projectedIndex = _index)))
      val installed = before.copy(cursor = 1, slotCursor = 1, candidate = None, commits = _progress.commits)
      When("the completed prefix is authenticated and one exact new namespace is prepared")
      val expectedindex = take(Previous.completedPrefixIndexC(_progress, installed, before))
      val reason = I.eligibility(_progress, _actual, _index, true, expectedindex)
      val next = take(I.continuedProgressC(_target, _progress))
      Then("the ordinary runtime accepts the new syntax without resetting any prefix effect or accepted plan")
      reason shouldBe ""
      next.preparationNamespace shouldBe Some("prepare-intent-continuation-20")
      next.copy(preparationNamespace = _progress.preparationNamespace) shouldBe _progress
      (next.cursor, next.attempt, next.slotCursor, next.baselineTree, next.commits) shouldBe
        ((1, 0, 1, None, _progress.commits))
      next.request shouldBe _progress.request
      next.plan shouldBe _progress.plan
      next.effects shouldBe _progress.effects
      RepositorySyncPurposeOwnership.validProgress(next) shouldBe true
      RepositorySyncPurposeRuntime.attemptPrefix(next) shouldBe _prefix + "-prepare-intent-continuation-20"
      RepositorySyncPurposeOwnership.validProgress(next.copy(preparationNamespace = Some("arbitrary"))) shouldBe false
      RepositorySyncPurposeOwnership.validProgress(next.copy(preparationNamespace = Some("prepare-intent-continuation-0"))) shouldBe false
      domain.statemachine.RepositorySyncBoundaryLifecycle.permits("PurposePreparing", "purpose-preparation-continuation", "PurposeGrouped") shouldBe true
      domain.statemachine.RepositorySyncBoundaryLifecycle.permits("PurposeUnfinished", "purpose-preparation-continuation", "PurposeGrouped") shouldBe true
    }
    "refuse changed source candidates unmerged state and malformed completed commit prefixes" in {
      Given("the exact unchanged source and accepted current group")
      val wrong = Vector(_progress.copy(cursor = -1), _progress.copy(cursor = 2),
        _progress.copy(commits = Vector.empty), _progress.copy(commits = Vector("bad")),
        _progress.copy(commits = _progress.commits :+ "a" * 40), _progress.copy(candidate = Some(_f.candidate)),
        _progress.copy(attempt = 1, baselineTree = None), _progress.copy(plan = None))
      When("progress or its actual source relationship differs")
      val messages = wrong.map(p => I.eligibility(p, _actual, _index, true, _index)) ++ Vector(
        I.eligibility(_progress, _actual.copy(mergeHead = Some("a" * 40)), _index, true, _index),
        I.eligibility(_progress, _actual.copy(observed = _actual.observed.copy(head = "a" * 40)), _index, true, _index),
        I.eligibility(_progress, _actual.copy(observed = _actual.observed.copy(branch = "foreign")), _index, true, _index),
        I.eligibility(_progress, _actual, "6" * 40, true, _index), I.eligibility(_progress, _actual, _index, false, _index))
      Then("every crossed observation is ineligible and stale progress cannot change namespace")
      messages.foreach(_ should not be empty)
      I.continuedProgressC(_target, _progress.copy(slotCursor = 2)).isSuccess shouldBe false
      I.continuedProgressC(_target.copy(eligibilityReason = "Source drifted"), _progress).isSuccess shouldBe false
    }
  }

  "RS-PREP-02 independent Human evidence" should {
    "bind exact lifecycle and UNKNOWN acceptance to the supplied choice target participant and reference" in {
      Given("an explicit affirmative attestation with an ordinary Unicode multiline rationale")
      val rationale = "独立した fixture Human が prior invocation の終了を確認した。\nUNKNOWN effects をこの target に限り受け入れる。"
      val answer = I.Answer(I.choices.head, rationale)
      When("the existing purpose selector consumes the new canonical binding")
      val binding = take(I.bindingC("component", _target, answer.choice, answer.rationale, "participant", _reference))
      val answercursor = binding.hcursor.downField("repositoryPurposePreparationContinuation").downField("answer")
      Then("both supplied-choice assertions are sealed and their omission or mutation refuses")
      I.decodePublicAnswer(answer.asJson) shouldBe Right(answer)
      Previous.selectBinding(binding) shouldBe Right(binding)
      answercursor.get[Boolean]("priorInvocationIndependentlySettledOrEnded").toOption shouldBe Some(true)
      answercursor.get[Boolean]("acceptOriginalUnknownEffects").toOption shouldBe Some(true)
      val omitted = answercursor.withFocus(_.mapObject(_.remove("priorInvocationIndependentlySettledOrEnded"))).top.get
      val altered = answercursor.withFocus(_.mapObject(_.add("acceptOriginalUnknownEffects", Json.False))).top.get
      Vector(omitted, altered).foreach(value => Previous.selectBinding(value).isLeft shouldBe true)
      I.bindingC("foreign", _target, answer.choice, rationale, "participant", _reference).isSuccess shouldBe false
      I.bindingC("component", _target, answer.choice, rationale, "other", _reference).isSuccess shouldBe false
      I.decodeAnswer(Previous.Answer(Previous.choices.head, rationale).asJson).isLeft shouldBe true
      val passive = _target.copy(eligibilityReason = "Source changed")
      I.bindingC("component", passive, answer.choice, rationale, "participant", _reference).isSuccess shouldBe false
      I.bindingC("component", passive, "Defer", rationale, "participant", _reference).isSuccess shouldBe true
    }
    "passively encode actual supplied issuer worker and attestation without gaining adoption authority" in {
      Given("the exact description and independently supplied external facts")
      val description = V.RepositorySyncPurposePreparationContinuationDescriptionResult("session", "adoption-required", I.message,
        "component", I.targetRecord(_target), I.choices, Previous.adoptionOperation)
      val answer = V.RepositorySyncPurposePreparationContinuationAnswer(I.choices.head, "Fixture Human attests independent settlement and accepts UNKNOWN effects")
      val artifact = V.WorkflowArtifactReference(_reference.identity, _reference.revision)
      val execution = V.WorkflowExecutionEvidence(Vector(artifact), Some("participant"), None, None)
      val occurred = Instant.parse("2026-11-06T00:00:00Z")
      When("preparation encodes only those supplied facts for the unchanged existing evidence purpose")
      val prepared = take(Preparation.prepareC(description, answer, artifact, execution, "participant", "fixture-host", occurred))
      val encoded = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      Then("the dedicated host evidence preserves the exact typed target and foreign facts refuse")
      encoded.purpose shouldBe Previous.purpose
      encoded.binding shouldBe take(I.bindingC("component", _target, answer.choice, answer.rationale, "participant", _reference))
      Previous.selectBinding(encoded.binding) shouldBe Right(encoded.binding)
      Preparation.prepareC(description.copy(sessionId = "foreign"), answer, artifact, execution,
        "participant", "fixture-host", occurred).isSuccess shouldBe false
      Preparation.prepareC(description, answer, artifact, execution.copy(workerIdentity = Some("foreign")),
        "participant", "fixture-host", occurred).isSuccess shouldBe false
      Preparation.prepareC(description.copy(choices = Previous.choices), answer, artifact, execution,
        "participant", "fixture-host", occurred).isSuccess shouldBe false
    }
  }

  "RS-PREP-02 retained rejection and payload compatibility" should {
    "encode the full boundary diagnostic without inventing process execution or retroactively populating UNKNOWN" in {
      Given("a genuine supplied preparation-boundary Conclusion diagnostic and its adjacent immutable intent")
      val diagnostic = "Actual boundary refusal\n原因: staged addition differs from current worktree\nNo process receipt was returned."
      val rejection = I.admissionRejection(diagnostic)
      val rejected = _progress.copy(effects = _progress.effects :+ rejection)
      val target = _target.copy(frontierRevision = "21", frontierCause = "native-repository:session:" + I.resultKey(_target),
        originalResultStatus = "PREPARATION_REJECTED", retainedRejection = Some(rejection.noSpaces),
        progressSnapshot = P.repairProgressJson(rejected).noSpaces)
      When("the new exact rejection contract is encoded separately from an absent result")
      val restored = take(I.targetC(I.targetRecord(target)))
      Then("only the actual diagnostic is appended and no exit code process receipt or fake failure revision appears")
      restored shouldBe target
      I.isAdmissionRejection(rejection) shouldBe true
      rejection.hcursor.get[String]("conclusion").toOption shouldBe Some(diagnostic)
      rejection.asObject.get.keys.toSet shouldBe Set("kind", "conclusion")
      Previous.matchingFailure(_progress, rejected, rejection) shouldBe true
      I.decodeTarget(_target.asJson) shouldBe Right(_target)
      _target.originalResultStatus shouldBe "UNKNOWN"
      _progress.effects should not contain rejection
      I.decodeTarget(target.copy(retainedRejection = Some("{}")).asJson).isLeft shouldBe true
      I.decodeTarget(_target.copy(retainedRejection = Some(rejection.noSpaces)).asJson).isLeft shouldBe true
      I.decodeTarget(target.copy(originalResultStatus = "UNKNOWN").asJson).isLeft shouldBe true
    }
    "retain old v3 payloads and v1 process-failure contracts while requiring exact new optional provenance" in {
      Given("an old payload without new provenance, one real process-shaped fixture failure and one new direction")
      val oldpayload = N.Payload("PurposePreparing", _progress.request.root, _actual.observed, purposes = Some(_progress))
      val direction = I.Direction(_target, I.choices.head, "Fixture Human independently settled and accepted UNKNOWN", "participant", _reference)
      val bytes = "actual failure diagnostic\n".getBytes(UTF_8).toVector
      val capture = ProcessExecutionCapture(bytes, bytes.size.toLong, false)
      val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(1), capture, capture, Vector.empty, 1, "git")
      val effect = Json.obj("outcome" -> "Failed(1)".asJson, "process" -> WorkflowSbtCapture.encode(process))
      val before = _progress.copy(attempt = 1, baselineTree = Some("7" * 40))
      val failed = before.copy(effects = before.effects :+ effect)
      val prefix = RepositorySyncPurposeRuntime.attemptPrefix(failed)
      val previous = Previous.Target("component", "session", _f.context, _progress.request.root, _f.context.remoteEndpointIdentity,
        "31", "native-repository:session:" + prefix + "-prepare-result", prefix + "-prepare-result", "31",
        prefix + "-prepare-intent", "30", P.repairProgressJson(failed).noSpaces, effect.noSpaces,
        _target.actualSnapshot, _index, _actual.observed.head, "", Previous.policy)
      When("old and new payloads and explicit policy bindings are decoded")
      val oldwire = N.codec.encode(oldpayload)
      val newpayload = oldpayload.copy(purposePreparationIntent = Some(direction))
      val newwire = N.codec.encode(newpayload)
      val explicitnull = oldwire.hcursor.downField("payload").withFocus(_.mapObject(_.add("purposePreparationIntent", Json.Null))).top.get
      val oldbinding = take(Previous.bindingC("component", previous, Previous.choices.head, "Known failed process", "participant", _reference))
      Then("missing provenance remains readable, explicit null refuses, and both contracts keep their own exact semantics")
      N.codec.decode(oldwire) shouldBe Right(oldpayload)
      oldwire.hcursor.downField("payload").downField("purposePreparationIntent").focus shouldBe None
      N.codec.decode(newwire) shouldBe Right(newpayload)
      N.codec.decode(explicitnull).isLeft shouldBe true
      I.Direction(_target, direction.choice, direction.rationale, direction.participant, _reference).asJson.as[I.Direction] shouldBe Right(direction)
      Previous.decodeTarget(previous.asJson) shouldBe Right(previous)
      Previous.selectBinding(oldbinding) shouldBe Right(oldbinding)
      Previous.validatePublicTarget(previous.asJson) shouldBe Right(())
      Previous.eligibility(failed, previous.failureKey, "PurposePreparing", effect, _actual, _index, true, Some(_index)) shouldBe ""
      Previous.eligibility(_progress, I.resultKey(_target), "PurposePreparing", I.admissionRejection("Actual refusal"),
        _actual, _index, true, Some(_index)) should not be empty
    }
  }
}
