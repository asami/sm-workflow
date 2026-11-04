package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncPushContinuation as P
import RepositorySyncNativeProgressStore as N
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeMergeValidation as C
import SplitPhaseExplicitPartitionFixture.take

/** Authored pure contract facts only; these are never native process or Human authority. */
private[admission] object RepositorySyncPushContinuationFixture {
  val context = RepositorySyncManagedFetch.StartContext("selection", "invocation", "participant", "repository",
    "main", "origin", "main", "owned-upstream", "FULL_STATE")
  val observed = G.Observed("main", "a" * 40, "b" * 40, Vector.empty, Some("c" * 40),
    Some(G.Counts(3, 0)), Some("c" * 40))
  val validation = C.Policy("push-fixture-checks", 1, "required", "Original fixed selected checks",
    Vector(C.Check("compile", 1, WorkflowSbtValidation.Kind.Compile), C.Check("test", 2)))
  val target = P.Target("component", "session", context, "/specification/repository", "/specification/upstream.git",
    "9", "native-repository:session:push-intent", "NativePushPreparing", "",
    Some(P.Intent(ContextReference("session:native-push-intent", "1"), "9", "push")),
    ContextReference("session:native-integration-validated", "1"), "8", observed.head, observed.tree,
    validation, validation.checks, G.Native(observed, None), "d" * 40, None, P.policy, P.finitePlan)
  val reference = ContextReference("fixture-push-direction", "2")
  val answer = P.Answer("RefreshRemoteAndContinue", "Authored fixture accepts one finite new episode", true, true)
  val direction = P.Direction(target, answer.choice, answer.rationale, context.participantIdentity,
    reference, answer.priorExecutionEnded, answer.acceptOriginalUncertainty)
  val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(1),
    ProcessExecutionCapture("retained stdout\n".getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector, 16, false),
    ProcessExecutionCapture("rejected\n".getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector, 9, false),
    Vector.empty, 23, "git-push")
  val operation = GitOperation.Push(take(GitObjectId.parseC(target.finalTip)), take(GitBranchName.parseC("main")))
  val observation = P.GitObservation(operation, GitExecutionOutcome.Failed(1), process, false)
}

/** Public codecs and native prefix contracts; installed CLI coverage owns actual effects. */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncPushContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import RepositorySyncPushContinuationFixture.*

  "SYNC-20261104-11 exact public push continuation" should {
    "roundtrip the complete target answer direction and independent binding without a rationale size cap" in {
      Given("an authored original resultless push source and a complete explicit Human answer")
      val rationale = "継続を判断した理由\n次の段落もそのまま残す。\n" * 10000
      val large = answer.copy(rationale = rationale)
      val selected = direction.copy(rationale = rationale)
      When("canonical public contracts and the exact independent evidence binding are decoded")
      val bound = take(P.bindingC(target.componentInstance, target, large.choice, large.rationale,
        context.participantIdentity, reference, true, true))
      Then("all facts and the full rationale remain intact; the old result is still absent")
      P.decodeTarget(target.asJson) shouldBe Right(target)
      P.decodePublicTarget(target.asJson) shouldBe Right(target)
      P.targetC(P.targetRecord(target)).toOption shouldBe Some(target)
      P.decodePublicAnswer(large.asJson) shouldBe Right(large)
      selected.asJson.as[P.Direction] shouldBe Right(selected)
      P.selectBinding(bound) shouldBe Right(bound)
      target.nativeObservation shouldBe None
      target.asJson.hcursor.downField("nativeObservation").focus shouldBe None
      target.asJson.hcursor.downField("purposeSnapshot").focus shouldBe None
      rationale.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 65536
      P.decodeAnswer(large.copy(rationale = "   \n\t ").asJson).isLeft shouldBe true
      P.decodeAnswer(large.copy(rationale = "理由\u0000を変更").asJson).isLeft shouldBe true
      P.decodeAnswer(large.copy(rationale = "理由\ud800").asJson).isLeft shouldBe true
    }

    "refuse missing null unknown crossed and expanded target facts" in {
      Given("the complete finite plan bound to one Component Session principal and native source")
      val wire = target.asJson
      val malformed = Vector(wire.mapObject(_.remove("context")), wire.mapObject(_.add("retainedIntent", Json.Null)),
        wire.mapObject(_.add("nativeObservation", Json.Null)), wire.mapObject(_.add("unexpected", true.asJson)),
        target.copy(continuationPolicy = "unknown").asJson,
        target.copy(finitePlan = target.finitePlan :+ "retry-until-success").asJson,
        target.copy(committedRevision = "99").asJson,
        target.copy(actual = G.Native(observed.copy(head = "e" * 40), None)).asJson,
        target.copy(actual = G.Native(observed, Some("e" * 40))).asJson,
        target.copy(retainedIntent = target.retainedIntent.map(_.copy(reference = ContextReference("other:native-push-intent", "1")))).asJson)
      When("any target input discards or contradicts a bound fact")
      val decoded = malformed.map(P.decodeTarget)
      Then("every changed contract is refused and the original complete target remains decodable")
      decoded.foreach(_.isLeft shouldBe true)
      P.decodeTarget(wire) shouldBe Right(target)
      P.bindingC("other-component", target, answer.choice, answer.rationale,
        context.participantIdentity, reference, true, true).isSuccess shouldBe false
      P.bindingC(target.componentInstance, target, answer.choice, answer.rationale,
        "other-participant", reference, true, true).isSuccess shouldBe false
    }

    "require explicit ended and UNKNOWN assertions while retaining passive Defer as an answer only" in {
      Given("an authored UNKNOWN source and a Defer answer that supplies no execution authority")
      val deferred = answer.copy(choice = "Defer", priorExecutionEnded = false, acceptOriginalUncertainty = false)
      When("answers omit terminal or uncertainty acceptance, or attempt to persist Defer as direction")
      val missingended = P.decodeAnswer(answer.copy(priorExecutionEnded = false).asJson)
      val missinguncertainty = P.decodeAnswer(answer.copy(acceptOriginalUncertainty = false).asJson)
      Then("continuation refuses either omission and a saved direction cannot be Defer")
      missingended.isLeft shouldBe true
      missinguncertainty.isLeft shouldBe true
      P.decodeAnswer(deferred.asJson) shouldBe Right(deferred)
      direction.copy(choice = "Defer").asJson.as[P.Direction].isLeft shouldBe true
      P.decodeAnswer(answer.asJson.mapObject(_.add("extra", true.asJson))).isLeft shouldBe true
      P.decodeAnswer(answer.asJson.mapObject(_.add("priorExecutionEnded", "true".asJson))).isLeft shouldBe true
    }

    "bind exact stale and contradictory answers without permitting source or finite plan substitution" in {
      Given("an exact independently bound target and its immutable answer")
      val bound = take(P.bindingC("component", target, answer.choice, answer.rationale, "participant", reference, true, true))
      When("the evidence envelope crosses Component or supplies unknown answer fields")
      val crossed = bound.mapObject(_.add("componentInstance", "other".asJson))
      val extra = bound.mapObject(fields => fields.add("repositoryPushContinuation",
        fields("repositoryPushContinuation").get.mapObject(section => section.add("answer",
          section("answer").get.mapObject(_.add("retry", true.asJson))))))
      Then("exact evidence selection refuses crossed and expanded bindings; separately described targets stay distinct")
      P.selectBinding(crossed).isLeft shouldBe true
      P.selectBinding(extra).isLeft shouldBe true
      P.currentTargetC(target, target).isSuccess shouldBe true
      P.currentTargetC(target, target.copy(advertisedRemoteTip = "e" * 40)).isSuccess shouldBe false
      P.currentTargetC(target, target.copy(componentInstance = "other")).isSuccess shouldBe false
      P.currentTargetC(target, target.copy(context = context.copy(participantIdentity = "other"))).isSuccess shouldBe false
      target.copy(advertisedRemoteTip = "e" * 40) should not be target
      direction.copy(rationale = "Different later answer") should not be direction
      P.namespace(target) shouldBe "push-continuation-9"
      P.adoptionKey(target) shouldBe "push-continuation-9-adopted"
    }

    "require complete exact direction references in typed adoption execution evidence" in {
      Given("a complete authored typed adoption input whose execution evidence references its exact direction")
      val artifact = V.WorkflowArtifactReference(reference.identity, reference.revision)
      val execution = V.WorkflowExecutionEvidence(Vector(artifact), Some(context.participantIdentity), None, None)
      val input = V.RepositorySyncPushContinuationAdoptionInput(target.sessionId, P.targetRecord(target),
        answer.choice, answer.rationale, artifact, answer.priorExecutionEnded, answer.acceptOriginalUncertainty, execution)
      val malformed = Vector(Vector.empty, Vector(artifact.copy(identity = "unrelated-direction")),
        Vector(artifact.copy(revision = "other-revision")), Vector(artifact, artifact.copy(revision = ""))).map(references =>
        input.copy(executionEvidence = execution.copy(references = references)))
      When("typed decoding or direct adoption receives empty crossed or malformed evidence references")
      val decoded = malformed.map(value => P.adoptionInputC(value.toRecord()))
      val direct = malformed.map(value => P.adoptC(value, null, null, null, validation, null,
        _ => org.goldenport.Consequence.unit))
      val complete = P.adoptionInputC(input.toRecord())
      Then("the complete input remains intact and every invalid reference set refuses before native adoption or duplicate handling")
      complete.toOption shouldBe Some(input)
      decoded.foreach(_.isSuccess shouldBe false)
      direct.foreach(_.isSuccess shouldBe false)
    }

    "reject duplicate input fields before typed adoption can discard them" in {
      Given("an input Record that repeats a field name before any Session or effect lookup")
      val duplicate = org.goldenport.record.Record.create(Seq("sessionId" -> "session", "sessionId" -> "other"))
      When("the exact typed adoption decoder observes the duplicate Record")
      val decoded = P.adoptionInputC(duplicate)
      Then("the malformed request is refused without treating a last value as authority")
      decoded.isSuccess shouldBe false
      P.decodePublicTarget(target.asJson.mapObject(_.add("frontier_revision", "10".asJson))).isLeft shouldBe true
    }
  }

  "SYNC-20261104-11 actual typed Git capture persistence" should {
    "roundtrip literal failed process bytes and distinguish failure incomplete invalid and missing results" in {
      Given("authored typed push failure facts with literal stdout stderr and terminal exit status")
      val incomplete = observation.copy(outcome = GitExecutionOutcome.Incomplete(ProcessExecutionTermination.TerminationUnconfirmed),
        process = process.copy(termination = ProcessExecutionTermination.TerminationUnconfirmed))
      val invalid = observation.copy(outcome = GitExecutionOutcome.InvalidOutput("actual typed decoder rejected output"),
        process = process.copy(termination = ProcessExecutionTermination.Exited(0)))
      val artifact = ProcessExecutionArtifact(take(ProcessArtifactName.parseC("retained-report")), ProcessArtifactKind.File, 1000000L)
      val metadata = observation.copy(process = process.copy(artifacts = Vector(artifact)))
      When("the closed observation codecs retain each actual kind without creating SBT facts")
      val values = Vector(observation, incomplete, invalid, metadata)
      val wires = values.map(_.asJson)
      Then("literal process captures and kinds roundtrip; no resultless source is certified as a success")
      wires.zip(values).foreach { (wire, expected) =>
        wire.as[P.GitObservation] shouldBe Right(expected)
        wire.hcursor.downField("sbt").focus shouldBe None
        P.successful(expected) shouldBe false
      }
      P.failed(observation) shouldBe true
      P.failed(incomplete) shouldBe false
      P.failed(invalid) shouldBe false
      metadata.asJson.as[P.GitObservation].toOption.get.process.artifacts shouldBe Vector(artifact)
      observation.copy(outcome = GitExecutionOutcome.Failed(2)).asJson.as[P.GitObservation].isLeft shouldBe true
      observation.asJson.mapObject(_.add("process", Json.Null)).as[P.GitObservation].isLeft shouldBe true
    }

    "read historical absent payload fields and refuse explicit null or malformed provenance" in {
      Given("an old native progress payload without any push continuation or push observation")
      val original = N.Payload("NativePushPreparing", target.root, observed, indexTree = Some(target.tree), finalTip = Some(target.finalTip))
      val old = N.codec.encode(original)
      val actual = original.copy(stage = "NativePushFailed", pushObservation = Some(observation))
      When("old and actual typed-result rows are restored and null provenance is supplied")
      val nulls = Vector("pushContinuation", "pushObservation").map(name => old.mapObject(fields => fields.add("payload",
        fields("payload").get.mapObject(_.add(name, Json.Null)))))
      Then("absence remains absence and actual captures survive; null cannot erase provenance")
      N.codec.decode(old) shouldBe Right(original)
      N.codec.decode(N.codec.encode(actual)) shouldBe Right(actual)
      old.hcursor.downField("payload").downField("pushObservation").focus shouldBe None
      nulls.foreach(value => N.codec.decode(value).isLeft shouldBe true)
    }
  }

  "SYNC-20261104-11 historical source lineage" should {
    "reconstruct the exact old native prefix while refusing absent or malformed source revisions" in {
      Given("a native history with an old push frontier followed by a separate later mechanical record")
      val initial = InstanceRecord(InstanceIdentity("session"), take(RepositorySyncDefinitionBinding.bindC), initialRevision,
        Lifecycle.NotStarted, None, CorrelationReference("correlation"), CausationReference("start"), None, None, None, Vector.empty)
      def _next_(record: InstanceRecord, stage: String, cause: String): InstanceRecord = take(record.appendC(record.revision,
        HistoryEntry(HistorySequence(record.history.size.toLong + 1), record.revision.next, Lifecycle.Active,
          Some(ProgressionReference("sync." + stage)), record.correlation, CausationReference(cause), None, None, None)))
      val old = _next_(initial, "NativePushPreparing", "native-repository:session:push-intent")
      val later = _next_(old, "PushContinuationAdmitted", "native-repository:session:push-continuation-1-adopted")
      When("a later reader selects the original source revision instead of the mutable current head")
      val selected = P.prefixC(later, old.revision.value.toString)
      Then("the exact old source and history are preserved and missing or noncanonical revisions refuse")
      selected.toOption shouldBe Some(old)
      P.prefixC(later, "999").isSuccess shouldBe false
      P.prefixC(later, "01").isSuccess shouldBe false
      old.history should not be later.history
    }
  }
}
