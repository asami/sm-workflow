package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{ContinuationIdentity, ContextReference, WorkflowMechanicalTransition as M}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhaseScopeProjectionCheckpointPreparation as C, SplitPhaseScopeProjectionCheckpointJsonV1 as J}
import SplitPhaseExplicitPartitionFixture.take

/** Deterministic source locators and passive preparation refusal.
  * Actual owning-call capture is specified in the managed checkpoint chapter.
  *
  * @since Oct.  4, 2026
  * @version Oct. 24, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class SplitPhaseScopeProjectionCheckpointPreparationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Scope successor capture" should {
    "source-specific locators" which {
      "retain the full source tuple and distinguish every selected reference role" in {
        Given("a first checkpoint, assigned scope continuation and result with Unicode and delimiters")
        val first = K.Reference("first:語@\"root\"", "1")
        val continuation = ContinuationIdentity("scope:work")
        val result = ContextReference("assigned:result", "2")
        val expected = "scope-projection:" + Json.arr(
          Json.arr(Json.fromString(first.identity), Json.fromString(first.revision)),
          Json.fromString(continuation.value),
          Json.arr(Json.fromString(result.identity), Json.fromString(result.revision))).noSpaces
        When("the complete tuple is selected repeatedly and one reference role is changed at a time")
        val reference = take(C.referenceC(first, continuation, result))
        val repeated = take(C.referenceC(first, continuation, result))
        val variants = Vector(
          take(C.referenceC(first.copy(revision = "2"), continuation, result)),
          take(C.referenceC(first, ContinuationIdentity("other:work"), result)),
          take(C.referenceC(first, continuation, result.copy(identity = "other:result"))),
          take(C.referenceC(first, continuation, result.copy(revision = "3"))))
        val key = take(C.keyC(reference))
        val nativekey = take(M.keyC(M.Reference(reference.identity, reference.revision)))
        Then("the exact purpose and tuple remain stable without hashes or new identity allocation")
        reference shouldBe C.Reference(expected, result.revision)
        repeated shouldBe reference
        variants.distinct.size shouldBe variants.size
        variants.foreach(_ should not be reference)
        key shouldBe "scope-successor:" + nativekey
        key.getBytes(UTF_8).length should be <= 1024
      }
      "refuse incomplete source roles and retain the private legacy address helper" in {
        Given("valid short roles and an obsolete application-copy address, not a runtime selection gate")
        val first = K.Reference("first", "1")
        val work = ContinuationIdentity("scope")
        val result = ContextReference("result", "1")
        val oversized = C.Reference("a" * 1010, "1")
        When("missing or noncanonical roles and the final successor key are prepared")
        val invalid = Vector(
          C.referenceC(null, work, result), C.referenceC(first, null, result), C.referenceC(first, work, null),
          C.referenceC(first.copy(revision = " "), work, result),
          C.referenceC(first, ContinuationIdentity("bad\nwork"), result),
          C.referenceC(first, work, result.copy(identity = "")))
        val nativekey = take(M.keyC(M.Reference(oversized.identity, oversized.revision)))
        val refused = C.keyC(oversized)
        Then("source-role construction rejects incomplete inputs; the legacy-only address helper remains separate from native selection")
        invalid.foreach(_ shouldBe a[Consequence.Failure[?]])
        nativekey.getBytes(UTF_8).length should be <= 1024
        ("scope-successor:" + nativekey).getBytes(UTF_8).length should be > 1024
        refused shouldBe a[Consequence.Failure[?]]
      }
    }
    "native construction boundary" which {
      "refuse absent owning projection without manufacturing a captured native intent" in {
        Given("no owning-call completed scope projection")
        When("fresh successor capture is requested")
        val rejected = C.captureC(null)
        Then("absence is a typed conflict and passive data cannot construct Captured")
        rejected shouldBe a[Consequence.Failure[?]]
        assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseScopeProjectionCheckpointPreparation.Captured(null, null)")
      }
    }
    "full passive frame refusal" which {
      "reject absent unsupported malformed and oversized frames without constructing a native capability" in {
        Given("a selected successor purpose and untrusted full-frame inputs")
        val reference = C.Reference("scope-projection:selected", "1")
        val prefix = J.schema + ":"
        val frames = Vector(null, "sm-workflow.split-phase-scope-checkpoint.v0:{}", prefix + "{",
          prefix + "{}", prefix + "語" * (J.maximumPayloadBytes / 3 + 1))
        When("the complete frame decoder and passive encoders receive those inputs")
        val refused = frames.map(raw => J.decodeC(reference, raw))
        val unselected = J.decodeC(null, prefix + "{}")
        val absent = Vector(J.encodeC(null))
        Then("every input is a typed refusal and no execution object is reconstructed")
        refused.foreach(_ shouldBe a[Consequence.Failure[?]])
        unselected shouldBe a[Consequence.Failure[?]]
        absent.foreach(_ shouldBe a[Consequence.Failure[?]])
        J.payloadCodec.typeIdentity shouldBe C.payloadType
        J.schema should not be SplitPhaseScopeProjectionSnapshotJsonV1.schema
      }
    }
  }
}
