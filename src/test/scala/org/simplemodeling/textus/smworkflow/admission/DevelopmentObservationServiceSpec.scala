package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.record.{Field, Record}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import DevelopmentOperationalView as V

/** @since Oct. 25, 2026
  * @version Oct. 25, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class DevelopmentObservationServiceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Development observation service" should {
    "bind the generated ordinary read Operations without any native transition operation" in {
      Given("the generated service declaration")
      val service = DevelopmentObservationService.bind(null, null)
      When("the normal factory service metadata is inspected")
      service.operations.operations.toVector.map(_.name).toSet shouldBe Set("readDevelopmentState", "readDevelopmentHistory")
      Then("publication remains the normal factory's bounded generated surface")
      ThreeWorkflowLauncherAssembly.publishedMcpOperations should contain("DevelopmentObservationService.readDevelopmentState")
      ThreeWorkflowLauncherAssembly.publishedMcpOperations should contain("DevelopmentObservationService.readDevelopmentHistory")
    }
    "reject unknown nested request fields before any source access" in {
      Given("a complete subject with an injected path field and a configuration with an unapproved provider selector")
      val subject = V.Subject(V.Car("g", "a", "v"), "component", "instance", "GoalPhase", "session", "repository", "phase")
      val input = Json.obj("schemaVersion" -> Json.fromString(V.schema), "subject" -> subject.asJson.deepMerge(
        Json.obj("path" -> Json.fromString("/private/secret"))))
      When("strict request and host-configuration decoding run before a source can be bound")
      val request = V.decodeRequestC(input, false)
      val configuration = V.decodeConfigurationC(Json.obj("schemaVersion" -> Json.fromString(V.schema), "subject" -> subject.asJson,
        "provider" -> Json.fromString("unapproved")))
      Then("neither injected storage input is admitted")
      request.isSuccess shouldBe false
      configuration.isSuccess shouldBe false
      val repeated = Record.empty.copy(fields = Vector(Field("sessionId", Field.Value.Single("one")), Field("sessionId", Field.Value.Single("two"))))
      V.uniqueFields(Record.data("subject" -> repeated)) shouldBe false
    }
    "carry only bounded safe refusal fields and the source read clock" in {
      Given("the actual observation clock and an absent host association")
      val at = Instant.parse("2026-10-25T00:00:00Z")
      When("the service constructs its safe not-configured observation")
      val result = V.response("not-configured", at)
      Then("the result contains only the wire schema, finite status and actual observation time")
      result.asObject.get.keys.toSet shouldBe Set("schemaVersion", "status", "observedAt")
      result.hcursor.get[String]("observedAt").toOption shouldBe Some(at.toString)
      result.noSpaces should not include "controller"
      result.noSpaces should not include "sessionId"
    }
  }
}
