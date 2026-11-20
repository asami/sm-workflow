package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import StandaloneFixExecution as S
import DelegateTaskScopeCorrection.{Checkpoint, Target}

/** Pure reconciliation fixtures are not authentication. The native reader must
  * obtain these checkpoints from actual same-owner supersession receipts.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class DelegateTaskDeliveryProjectionSpec extends AnyWordSpec with Matchers {
  private val source = ContextReference("specification-original-request", "1")
  private val correction = ContextReference("specification-genuine-correction", "1")
  private val contract = DelegateTask.Contract("requester", "destination", "requester", "org.example.Component",
    "destination", "localized", Vector("dispatch", "implement", "validate", "review", "commit", "return", "adopt"),
    Vector("remote-push"), Vector(ContextReference("specification-original-human", "1")), None, Vector.empty, "Return actual local results")
  private val input = S.Input("component", "same-request", "Retain the bounded public behavior", Vector("src/main/scala"),
    Vector("compile", "test-only"), Vector("behavior"), Vector(source), false, scalaLint = "off",
    task = Some(S.Task("direct-instruction", Vector.empty)), delegation = Some(contract))
  private val state = S.State(input, "participant", "Repair", 2, Vector.empty, None, Vector.empty, 1, 0,
    GoalPhaseRuntimeRepairControl.Context(GoalPhaseRuntimeRepairControl.version, 10))
  private val checkpoint = Checkpoint(Target("org.example.Component@specification", "same-session", "participant", 6,
    "same-original-issue", Json.obj(), Json.obj(), BoundedWorkRuntime.Payload(state,
      "/specification/repository", "main", "a" * 40), "1", Vector("src/test/scala/ExactSpec.scala")), correction)
  private def take[A](value: org.goldenport.Consequence[A]): A = value.toOption.getOrElse(fail(value.toString))

  "Derived delivery input reconciliation" should {
    "append only the genuine missing correction reference and preserve all admitted requirements" in {
      val legacy = input.copy(ownedPaths = input.ownedPaths ++ checkpoint.target.additionalOwnedPaths)
      val fixed = take(DelegateTaskDeliveryProjection.reconcileC(legacy, Vector(checkpoint)))
      fixed shouldBe legacy.copy(sourceReferences = Vector(source, correction))
      fixed.copy(sourceReferences = legacy.sourceReferences) shouldBe legacy
      take(DelegateTaskDeliveryProjection.reconcileC(fixed, Vector(checkpoint))) shouldBe fixed
      take(DelegateTaskDeliveryProjection.reconcileC(input, Vector.empty)) shouldBe input
    }
    "refuse extra or reordered ownership, altered requirements, guessed refs and an absent correction" in {
      val corrected = take(DelegateTaskDeliveryProjection.correctedC(input, checkpoint))
      Vector(corrected.copy(ownedPaths = corrected.ownedPaths.reverse),
        corrected.copy(ownedPaths = corrected.ownedPaths :+ "unadmitted.scala"),
        corrected.copy(objective = "Changed scope"),
        corrected.copy(conditions = Vector("different")),
        corrected.copy(sourceReferences = Vector(source, ContextReference("guessed-correction", "1"))),
        corrected.copy(sourceReferences = Vector(correction, source))).foreach { crossed =>
          DelegateTaskDeliveryProjection.reconcileC(crossed, Vector(checkpoint)).isSuccess shouldBe false
        }
      DelegateTaskDeliveryProjection.reconcileC(input, Vector(checkpoint)).isSuccess shouldBe false
      val changed = checkpoint.copy(target = checkpoint.target.copy(business = checkpoint.target.business.copy(
        state = state.copy(input = input.copy(checks = Vector("different"))))))
      DelegateTaskDeliveryProjection.reconcileC(corrected, Vector(changed)).isSuccess shouldBe false
    }
    "require every ordered correction when reconciling a chained inventory" in {
      val first = take(DelegateTaskDeliveryProjection.correctedC(input, checkpoint))
      val second = checkpoint.copy(target = checkpoint.target.copy(oldContinuationId = "second-issue",
        business = checkpoint.target.business.copy(state = state.copy(input = first)),
        additionalOwnedPaths = Vector("src/test/scala/OtherSpec.scala")),
        reference = ContextReference("specification-second-correction", "1"))
      val finalInput = take(DelegateTaskDeliveryProjection.correctedC(first, second))
      take(DelegateTaskDeliveryProjection.reconcileC(finalInput, Vector(checkpoint, second))) shouldBe finalInput
      DelegateTaskDeliveryProjection.reconcileC(finalInput, Vector(second, checkpoint)).isSuccess shouldBe false
      DelegateTaskDeliveryProjection.reconcileC(finalInput, Vector(checkpoint)).isSuccess shouldBe false
    }
    "identify a reused source by its native receipt while reconciling inventory" in {
      val reused = checkpoint.copy(reference = source)
      val actual = input.copy(ownedPaths = input.ownedPaths ++ reused.target.additionalOwnedPaths)
      take(DelegateTaskDeliveryProjection.reconcileC(actual, Vector(reused))) shouldBe actual
      val proof = DelegateTaskDeliveryProjection.receiptReference(reused, "7")
      proof should not be source
      proof.revision shouldBe "7"
      proof should not be DelegateTaskDeliveryProjection.receiptReference(reused.copy(
        target = reused.target.copy(oldContinuationId = "crossed-issue")), "7")
      proof should not be DelegateTaskDeliveryProjection.receiptReference(reused, "8")
      DelegateTaskDeliveryProjection.reconcileC(actual.copy(ownedPaths = actual.ownedPaths :+ "other.scala"),
        Vector(reused)).isSuccess shouldBe false
    }
  }
}
