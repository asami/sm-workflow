package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseSourceBoundaryDocumentReader.{DocumentReference, loadC}

/**
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseSourceBoundaryDocumentReaderSpec extends AnyWordSpec with Matchers {
  private val reference = DocumentReference("snapshot-1", "a" * 64, "77",
    "source-authority-1", Path.of("src/test/resources/source-boundary-reader-fixture"),
    "c25b570819610acae6f27df9d4f456a0af164ab368242641b7845febdbfb5455")

  "SplitPhase source boundary document reader" should {
    "read an exact versioned block from the frozen Phase document" in {
      loadC(reference) match {
        case Consequence.Success(Some(contract)) =>
          contract.sourceSnapshotDigest shouldBe "a" * 64
          contract.sourcePhaseIdentity shouldBe "77"
          contract.items.map(_.identity) shouldBe Vector("work-1", "work-2", "work-3")
          contract.items(2).prerequisiteItems shouldBe Vector("work-2")
          contract.authorizedHandoffReferences shouldBe
            Set("authority-edge-work-1", "authority-edge-work-2")
        case other => fail(s"Expected source boundary contract: $other")
      }
    }

    "reject a stale document digest or another source identity" in {
      loadC(reference.copy(phaseDocumentSha256 = "b" * 64)) shouldBe
        a[Consequence.Failure[?]]
      loadC(reference.copy(sourcePhaseIdentity = "78")) shouldBe
        a[Consequence.Failure[?]]
      loadC(reference.copy(sourceAuthorityReference = "another-authority")) shouldBe
        a[Consequence.Failure[?]]
    }

    "leave a prose-only Phase without a contract pending" in {
      loadC(reference.copy(sourcePhaseIdentity = "78",
        phaseDocumentSha256 =
          "982c2261b918573efa5b514a952d47c11335971b9d303607d86c10a503d278e9")) shouldBe
        Consequence.success(None)
    }

    "reject a malformed declared contract instead of treating it as absent" in {
      loadC(reference.copy(sourcePhaseIdentity = "79",
        phaseDocumentSha256 =
          "a6197bfd06a38ad5590360821a8bf2ff8e11e09424374a12574a57f6706399f1")) shouldBe
        a[Consequence.Failure[?]]
    }
  }
}
