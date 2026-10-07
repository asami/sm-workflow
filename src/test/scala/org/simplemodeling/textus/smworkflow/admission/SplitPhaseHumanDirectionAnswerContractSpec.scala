package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseHumanDirectionAnswerContract as A,
  SplitPhaseExplicitPartitionSemantic as S}

/** Closed answer interpretation; generated payload compatibility is not consent.
  *
  * @since Oct.  4, 2026
  * @version Oct.  4, 2026
  */
final class SplitPhaseHumanDirectionAnswerContractSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail("Human answer specification preparation failed: " + other)
  }
  private def _envelope(choice: String, resolution: Option[Json] = None): Json = {
    val body = Json.obj("choice" -> Json.obj("value" -> Json.fromString(choice)))
    Json.obj("schemaVersion" -> Json.fromString(A.schema),
      "payload" -> resolution.fold(body)(value => body.mapObject(_.add("resolution", value))))
  }

  "Closed SplitPhase Human answers" should {
    "passive generated payloads" which {
      "round-trip every declared alternative and retain exact resolution identity/revision" in {
        Given("all declared answers and canonical reference dimensions including Unicode and separators")
        val references = for (identity <- Vector("resolution", "計画/案", "resolution:one@two", "x" * 1024);
          revision <- Vector("1", "改訂二", "revision:3@4")) yield S.ContextReference(identity, revision)
        val answers = Vector[A.Answer](A.Answer.Defer, A.Answer.Cancel) ++ references.map(A.Answer.ProvideResolution(_))
        answers.foreach { answer =>
          When("the closed answer is encoded and independently decoded as both its contract and generated CML value")
          val json = _take(A.encodeC(answer))
          val decoded = A.payloadCodec.decode(json)
          val generated = json.hcursor.downField("payload").focus.get
            .as[org.simplemodeling.textus.smworkflow.value.SplitPhaseHumanDirectionAnswer]
          Then("the complete answer and generated choice/reference fields are preserved without any authority fields")
          decoded shouldBe Right(answer)
          generated.isRight shouldBe true
          answer match {
            case A.Answer.ProvideResolution(reference) =>
              generated.toOption.get.choice.value shouldBe "ProvideResolution"
              generated.toOption.get.resolution.map(value => S.ContextReference(value.identity, value.revision)) shouldBe Some(reference)
            case A.Answer.Defer =>
              generated.toOption.get.choice.value shouldBe "Defer"
              generated.toOption.get.resolution shouldBe None
            case A.Answer.Cancel =>
              generated.toOption.get.choice.value shouldBe "Cancel"
              generated.toOption.get.resolution shouldBe None
          }
        }
      }
      "refuse undeclared choices and resolution fields on non-resolution alternatives" in {
        Given("invalid choice and resolution combinations at the external payload boundary")
        val reference = Json.obj("identity" -> Json.fromString("resolution"), "revision" -> Json.fromString("1"))
        val payloads = Vector(_envelope("ProvideResolution"), _envelope("Defer", Some(reference)),
          _envelope("Cancel", Some(reference)), _envelope("ProvideResolution", Some(Json.Null))) ++
          Vector("", "Approve", "cancel", " Defer", "ProvideResolution ").flatMap(choice =>
            Vector(_envelope(choice), _envelope(choice, Some(reference))))
        payloads.foreach { json =>
          When("the incompatible external answer is decoded")
          val decoded = A.payloadCodec.decode(json)
          Then("no closed answer is reconstructed")
          decoded.isLeft shouldBe true
        }
      }
      "refuse additional authority metadata at every nested payload boundary" in {
        Given("a valid resolution payload and client-owned authority fields")
        val original = _take(A.encodeC(A.Answer.ProvideResolution(S.ContextReference("resolution", "1"))))
        val paths = Vector(Vector.empty[String], Vector("payload"), Vector("payload", "choice"), Vector("payload", "resolution"))
        for (path <- paths; key <- Vector("approval", "actor", "receipt", "issuer", "commandPermission", "unexpected")) {
          val cursor = path.foldLeft(original.hcursor)((before, field) => before.downField(field).success.get)
          val extended = cursor.withFocus(_.mapObject(_.add(key, Json.fromBoolean(true)))).top.get
          When("additional metadata is supplied at this envelope or nested record boundary")
          val decoded = A.payloadCodec.decode(extended)
          Then("client-supplied consent or command authority is not accepted as answer data")
          decoded.isLeft shouldBe true
        }
      }
      "refuse malformed references, invalid UTF-8 values and oversized complete payloads" in {
        Given("canonical-reference violations and a complete payload exceeding the UTF-8 byte bound")
        val invalid = Vector(null, "", " padded", "trailing ", "line\nbreak", "x" * 1025, "\uD800")
        val references = invalid.flatMap(value => Vector(S.ContextReference(value, "1"), S.ContextReference("resolution", value))) ++
          Vector(S.ContextReference("漢" * 1024, "漢" * 1024))
        references.foreach { reference =>
          When("the unsupported resolution reference is encoded")
          val encoded = A.encodeC(A.Answer.ProvideResolution(reference))
          Then("no supported answer payload or authority is produced")
          encoded.isSuccess shouldBe false
        }
        Vector(null, Json.Null, Json.obj(), Json.fromString("x" * 4097)).foreach { json =>
          When("an absent malformed or oversized external payload is decoded")
          val decoded = A.payloadCodec.decode(json)
          Then("the input does not construct an answer")
          decoded.isLeft shouldBe true
        }
        When("an absent answer or resolution value is encoded")
        val absent = Vector(A.encodeC(null), A.encodeC(A.Answer.ProvideResolution(null)))
        Then("neither absent value can enter the closed payload contract")
        absent.foreach(_.isSuccess shouldBe false)
      }
    }
    "native admission construction" which {
      "refuse missing genuine plans and free construction of verified evidence" in {
        Given("passive answer data without its actual native Human issuance plan")
        When("native preparation or current-call verification receives no owning plan")
        val prepared = SplitPhaseNativeHumanAnswer.prepareC(null, null, null)
        val admitted = SplitPhaseNativeHumanAnswer.admitForCallC(null, null, null, null, null)
        Then("passive values provide neither a postimage nor a verified Human capability")
        prepared.isSuccess shouldBe false
        admitted.isSuccess shouldBe false
        assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseNativeHumanAnswer.Verified(null, null)")
        assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseHumanAnswerVerification.Admitted(null, null)")
      }
    }
  }
}
