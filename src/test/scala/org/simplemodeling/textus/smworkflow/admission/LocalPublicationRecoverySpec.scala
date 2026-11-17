package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take
import LocalPublication.*

/** Synthetic standalone publication recovery behavior.
  * @since   Nov. 17, 2026
  * @version Nov. 17, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class LocalPublicationRecoverySpec extends AnyWordSpec with Matchers {
  private val target = Target("producer", "org.example.Producer", "producer-workspace")
  private val source = Source("a" * 40, "b" * 40, Vector.empty)
  private final class Memory extends Port {
    val rows = scala.collection.mutable.Map.empty[String, Json]
    var current = source
    var starts = 0
    var termination: ProcessExecutionTermination = ProcessExecutionTermination.Exited(0)
    def loadC(k: String) = Consequence.success(rows.get(k))
    def createC(k: String, v: Json): Consequence[Unit] = if (rows.contains(k)) Consequence.stateConflict("duplicate")
      else { rows(k) = v; Consequence.unit }
    def sourceC(t: Target) = Consequence.success(current)
    def newIdentity() = "test-id"
    def executeC(t: Target) = {
      starts += 1
      val capture = ProcessExecutionCapture(Vector.empty, 0L, false)
      Consequence.success(SbtExecutionAttempt.Observed(SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC(t.workspace))),
        SbtOperation.PublishLocal, ProcessExecutionResult(termination, capture, capture,
          Vector.empty, 0L, "test-only"))))
    }
  }
  "Standalone publication recovery" should {
    "bind a distinct Human direction and execute at most one successor" in {
      val m = new Memory
      val original = Input("original", target.identity, explicit = true)
      val first = take(ensureC(original, target, "actor", m))
      m.rows.remove("local-publication:actor:original:result")
      val described = take(LocalPublicationRecovery.describeC("component", original, "successor", target, "actor", m))
      val answer = LocalPublicationRecovery.Answer(described, LocalPublicationRecovery.choice, "Accept unknown effects",
        "actor", ContextReference("human-answer", "1"))
      val before = m.rows.toMap
      LocalPublicationRecovery.admitC(answer, target, "actor", "component", m,
        _ => Consequence.securityPermissionDenied("no independent Human evidence")).isFaillure shouldBe true
      m.rows.toMap shouldBe before
      take(LocalPublicationRecovery.admitC(answer, target, "actor", "component", m, _ => Consequence.unit))
      m.starts shouldBe 1
      val crossed = answer.copy(target = described.copy(successorIdentity = "other"))
      LocalPublicationRecovery.admitC(crossed, target, "other", "component", m, _ => Consequence.unit).isFaillure shouldBe true
      m.current = source.copy(head = "c" * 40)
      LocalPublicationRecovery.advanceC("successor", original, target, "actor", "component", m).isFaillure shouldBe true
      m.starts shouldBe 1
      m.current = source
      val result = take(LocalPublicationRecovery.advanceC("successor", original, target, "actor", "component", m))
      result.status shouldBe "published"
      take(LocalPublicationRecovery.advanceC("successor", original, target, "actor", "component", m)) shouldBe result
      take(collectC(original, target, "actor", m)).get.status shouldBe "uncertain"
      take(collectC(result.request.input, target, "actor", m)) shouldBe Some(result)
      m.starts shouldBe 2
      first.request.input shouldBe original
    }
    "retain a failed successor without retrying or changing the original UNKNOWN" in {
      val m = new Memory
      val original = Input("failure-original", target.identity, explicit = true)
      take(ensureC(original, target, "actor", m))
      m.rows.remove("local-publication:actor:failure-original:result")
      val described = take(LocalPublicationRecovery.describeC("component", original, "failure-successor", target, "actor", m))
      val answer = LocalPublicationRecovery.Answer(described, LocalPublicationRecovery.choice, "Accept unknown effects",
        "actor", ContextReference("another-human-answer", "1"))
      take(LocalPublicationRecovery.admitC(answer, target, "actor", "component", m, _ => Consequence.unit))
      m.termination = ProcessExecutionTermination.Exited(7)
      val failed = take(LocalPublicationRecovery.advanceC("failure-successor", original, target, "actor", "component", m))
      failed.status shouldBe "failed"
      take(LocalPublicationRecovery.advanceC("failure-successor", original, target, "actor", "component", m)) shouldBe failed
      take(collectC(original, target, "actor", m)).get.status shouldBe "uncertain"
      m.starts shouldBe 2
    }
  }
}
