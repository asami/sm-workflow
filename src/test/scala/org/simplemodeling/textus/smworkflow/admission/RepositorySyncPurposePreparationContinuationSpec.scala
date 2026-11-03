package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.time.Instant
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativeRepositoryPurposePreparationContinuationPreparation as Preparation
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncPurposePreparationContinuation as N
import RepositorySyncPurpose as P
import RepositorySyncManagedGitObservation as G
import SplitPhaseExplicitPartitionFixture.take
import P.given

/** Authored observations for pure contracts, not actual Human evidence or live
  * native effects. The installed fixture owns actual receipt/transition checks.
  * @since Nov. 4, 2026
  * @version Nov. 4, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncPurposePreparationContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val f = RepositorySyncPurposeRepairScopeFixture
  private def capture(text: String, truncated: Boolean = false) = {
    val bytes = text.getBytes(UTF_8).toVector
    ProcessExecutionCapture(bytes, bytes.size.toLong, truncated)
  }
  private val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(1), capture("actual stdout\n"),
    capture("actual prepare conflict\n"), Vector.empty, 3, "git")
  private def effect(result: ProcessExecutionResult, outcome: String = "Failed(1)"): Json =
    Json.obj("outcome" -> outcome.asJson, "process" -> WorkflowSbtCapture.encode(result))
  private val before = take(RepositorySyncPurposeRuntime.repairedProgressC(f.progress, f.ownershipInput,
    f.ownershipResult, RepositorySyncNativeConflictInput.Policy(f.ownershipPaths.toSet))).copy(commits = Vector.empty,
      request = f.request.copy(records = f.opaqueProgress.request.records))
  private val failed = effect(process)
  private val progress = before.copy(effects = before.effects :+ failed)
  private val prefix = RepositorySyncPurposeRuntime.attemptPrefix(progress)
  private val target = N.Target("component", "session", f.context, f.request.root, "/specification/upstream.git", "20",
    "native-repository:session:" + prefix + "-prepare-result", prefix + "-prepare-result", "20", prefix + "-prepare-intent", "19",
    P.repairProgressJson(progress).noSpaces, failed.noSpaces,
    RepositorySyncPurposeContentContinuation.canonical(f.actual.asJson).noSpaces, f.request.indexTree, f.request.parent, "", N.policy)
  private val reference = ContextReference("actual-human-direction", "1")

  "ID08 exact failed preparation target" should {
    "preserve opaque full progress and process observations through public Record conversion and reject malformed targets" in {
      Given("the first repaired Purpose with opaque names, arrays, null journal facts and complete provider streams")
      val wire = target.asJson
      val malformed = wire.asObject.get.keys.toVector.flatMap(key => Vector(wire.mapObject(_.remove(key)),
        wire.mapObject(_.add(key, Json.Null)), wire.mapObject(_.add(key, Json.True)))) :+
        wire.mapObject(_.add("priorWorkerEnded", Json.True))
      When("strict public target decoding consumes the supplied observation")
      val restored = take(N.targetC(N.targetRecord(target)))
      Then("the exact original texts and captured receipt survive and incomplete/extra fields refuse")
      restored shouldBe target
      N.decodeTarget(wire) shouldBe Right(target)
      malformed.foreach(value => N.decodeTarget(value).isLeft shouldBe true)
      N.progress(restored) shouldBe Right(progress)
      N.effect(restored) shouldBe Right(failed)
      restored.progressSnapshot should include("arbitrary_snake_case")
      restored.progressSnapshot should include("arbitrary.dotted.key")
      WorkflowSbtCapture.decode(N.effect(restored).toOption.get.hcursor.get[Json]("process").toOption.get) shouldBe Right(process)
    }
    "require exact adjacent native intent/result identities and the actual source observation" in {
      Given("an exact failure pair selected by the native frontier")
      val crossed = Vector(target.copy(sessionId = "other"), target.copy(failureRevision = "21"),
        target.copy(intentRevision = "18"), target.copy(intentKey = "other-prepare-intent"),
        target.copy(sourceHead = "f" * 40), target.copy(continuationPolicy = "other"))
      When("crossed provenance is offered as the same target")
      val refused = crossed.map(value => N.decodeTarget(value.asJson))
      Then("none is admitted and original intent progress requires exactly one appended actual effect")
      refused.foreach(_.isLeft shouldBe true)
      N.matchingFailure(before, progress, failed) shouldBe true
      N.matchingFailure(before, progress.copy(attempt = progress.attempt + 1), failed) shouldBe false
      N.matchingFailure(before, progress.copy(effects = progress.effects :+ failed), failed) shouldBe false
    }
  }

  "ID08 projected-only admitted baseline" should {
    "check every distinct original and projected path and refuse drift confined to the added baseline" in {
      Given("an admitted baseline retained only in projectedWorktree, outside the original captured request")
      val added = P.Content(f.baselinePath, Some("captured additional baseline"))
      val current = progress.plan.get.groups.head
      val extended = progress.copy(projectedWorktree = Some(Vector(progress.request.files.head).map(file =>
        P.Content(file.path, file.worktree)) :+ added), plan = progress.plan.map(plan => plan.copy(groups =
        plan.groups.updated(0, current.copy(content = current.content :+ added.copy(text = Some("current correction")))))))
      val original = progress.request.files.map(file => file.path -> file.worktree).toMap
      val unchanged = original + (added.path -> added.text)
      val drifted = original + (added.path -> Some("unexpected added-file change"))
      val reads = scala.collection.mutable.ArrayBuffer.empty[String]
      def read(values: Map[String, Option[String]])(path: String): Consequence[Option[String]] = {
        reads += path
        values.get(path).map(Consequence.success).getOrElse(Consequence.stateConflict("Unexpected test read: " + path))
      }
      When("the production predicate reads unchanged files and then only the projected-only path changes")
      val stable = take(N.rootMatchesC(extended, read(unchanged)))
      val stableReads = reads.toVector
      reads.clear()
      val changed = take(N.rootMatchesC(extended, read(drifted)))
      Then("unchanged source is eligible, the added-path drift refuses, and each path is actually read once")
      stable shouldBe true
      changed shouldBe false
      stableReads shouldBe (progress.request.files.map(_.path) :+ added.path)
      reads.toVector shouldBe stableReads
      drifted.filterNot(_._1 == added.path) shouldBe original
      extended.request shouldBe progress.request
      extended.request.files.exists(_.path == added.path) shouldBe false
    }
  }

  "ID08 preparation eligibility" should {
    "offer execution only for known nonzero preparation and leave unknown incomplete and different effects passive" in {
      Given("the unchanged original source, index and accepted first Purpose repair")
      def reason(key: String = target.failureKey, stage: String = "PurposePreparing", result: Json = failed,
        p: P.Progress = progress, actual: G.Native = f.actual, index: String = f.request.indexTree,
        matches: Boolean = true) = N.eligibility(p, key, stage, result, actual, index, matches)
      When("provider termination, effect phase or source facts differ")
      val ineligible = Vector(
        reason(result = effect(process.copy(termination = ProcessExecutionTermination.TerminationUnconfirmed), "Incomplete")),
        reason(result = effect(process.copy(stdout = capture("prefix", true)))),
        reason(result = effect(process.copy(termination = ProcessExecutionTermination.Exited(2)))),
        reason(key = prefix + "-materialize-result", stage = "PurposeMaterializing"),
        reason(key = prefix + "-commit-result", stage = "PurposeInstalling"),
        reason(p = progress.copy(commits = Vector("f" * 40))), reason(p = progress.copy(cursor = 1)),
        reason(actual = f.actual.copy(observed = f.actual.observed.copy(head = "f" * 40))),
        reason(actual = f.actual.copy(observed = f.actual.observed.copy(branch = "other"))),
        reason(index = "f" * 40), reason(matches = false))
      Then("only the original complete exited prepare is eligible and all other targets retain Defer only")
      reason() shouldBe ""
      ineligible.foreach { message =>
        message should not be empty
        N.targetChoices(target.copy(eligibilityReason = message)) shouldBe Vector("Defer")
      }
      N.targetChoices(target) shouldBe N.choices
    }
  }

  "ID08 passive Human direction" should {
    "bind the exact target participant reference and ordinary Unicode answer without an application length limit" in {
      Given("an actual supplied direction and a rationale longer than the removed arbitrary 64KiB limit")
      val rationale = "修正の理由\n" * 20000
      val answer = N.Answer(N.choices.head, rationale).asJson
      When("the answer and dedicated purpose binding are encoded")
      val binding = take(N.bindingC("component", target, N.choices.head, rationale, "participant", reference))
      Then("exact canonical data succeeds while extra null wrong participant and crossed component refuse")
      N.decodeAnswer(answer) shouldBe Right(N.Answer(N.choices.head, rationale))
      N.decodeAnswer(answer.mapObject(_.add("priorWorkerEnded", Json.True))).isLeft shouldBe true
      N.decodeAnswer(answer.mapObject(_.add("rationale", Json.Null))).isLeft shouldBe true
      N.decodeAnswer(N.Answer("AuthorizeSomethingElse", "reason").asJson).isLeft shouldBe true
      N.selectBinding(binding) shouldBe Right(binding)
      N.bindingC("other", target, N.choices.head, "reason", "participant", reference).isSuccess shouldBe false
      N.bindingC("component", target, N.choices.head, "reason", "other", reference).isSuccess shouldBe false
      val passive = target.copy(eligibilityReason = "Provider termination remains unconfirmed")
      N.bindingC("component", passive, N.choices.head, "reason", "participant", reference).isSuccess shouldBe false
      N.bindingC("component", passive, "Defer", "Keep original failure", "participant", reference).isSuccess shouldBe true
    }
    "prepare only dedicated external encoding and refuse mismatched Session or execution worker" in {
      Given("the exact description and independently supplied external facts")
      val description = V.RepositorySyncPurposePreparationContinuationDescriptionResult("session", "adoption-required", N.message,
        "component", N.targetRecord(target), N.choices, N.adoptionOperation)
      val answer = V.RepositorySyncPurposePreparationContinuationAnswer(N.choices.head, "Use corrected projection")
      val artifact = V.WorkflowArtifactReference(reference.identity, reference.revision)
      val execution = V.WorkflowExecutionEvidence(Vector(artifact), Some("participant"), None, None)
      When("the passive preparation encodes that supplied evidence without executing any workflow or effect")
      val prepared = take(Preparation.prepareC(description, answer, artifact, execution, "participant", "fixture-host",
        Instant.parse("2026-11-04T00:00:00Z")))
      Then("the dedicated record binds only the exact direction and mismatched callers cannot encode it")
      val encoded = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      encoded.purpose shouldBe N.purpose
      encoded.binding shouldBe take(N.bindingC("component", target, answer.choice, answer.rationale, "participant", reference))
      Preparation.prepareC(description.copy(sessionId = "other"), answer, artifact, execution, "participant", "fixture-host",
        Instant.parse("2026-11-04T00:00:00Z")).isSuccess shouldBe false
      Preparation.prepareC(description, answer, artifact, execution.copy(workerIdentity = Some("other")), "participant", "fixture-host",
        Instant.parse("2026-11-04T00:00:00Z")).isSuccess shouldBe false
    }
    "retain every failed fact and repair counter while allocating a distinct namespace and rejecting stale progress" in {
      Given("one exact authorization for the original failed preparation")
      When("the pure transition content is prepared for the managed CAS")
      val next = take(N.continuedProgressC(target, progress))
      Then("only its namespace changes, old keys remain distinct and identical content is stable")
      next.copy(preparationNamespace = progress.preparationNamespace) shouldBe progress
      next.effects shouldBe progress.effects
      next.request.records shouldBe progress.request.records
      (next.attempt, next.slotCursor, next.cursor, next.commits, next.ownership, next.baselineTree) shouldBe
        ((progress.attempt, progress.slotCursor, progress.cursor, progress.commits, progress.ownership, progress.baselineTree))
      N.continuedProgressC(target, progress).toOption shouldBe Some(next)
      RepositorySyncPurposeRuntime.attemptPrefix(next) shouldBe prefix + "-prepare-continuation-20"
      RepositorySyncPurposeRuntime.attemptPrefix(progress) shouldBe prefix
      next.asJson.as[P.Progress] shouldBe Right(next)
      N.continuedProgressC(target, progress.copy(attempt = progress.attempt + 1)).isSuccess shouldBe false
      N.continuedProgressC(target.copy(eligibilityReason = "Unknown"), progress).isSuccess shouldBe false
      N.Direction(target, N.choices.head, "Actual Human choice", "participant", reference).asJson.as[N.Direction].isRight shouldBe true
    }
  }
}
