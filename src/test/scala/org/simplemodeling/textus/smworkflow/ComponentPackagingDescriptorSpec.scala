package org.simplemodeling.textus.smworkflow

import java.nio.file.{Files, Paths}
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentDescriptorLoader, ComponentId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class ComponentPackagingDescriptorSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def readJson(path: String): Json =
    parse(Files.readString(Paths.get(path))).fold(error => fail(error.message), identity)

  private def array(value: Json, field: String): Vector[Json] =
    value.hcursor.get[Vector[Json]](field).fold(error => fail(error.message), identity)

  private def text(value: Json, field: String): String =
    value.hcursor.get[String](field).fold(error => fail(error.message), identity)

  private def fields(element: Json): Json = Json.fromValues(array(element, "fields").map { field =>
    Json.obj(
      "name" -> Json.fromString(text(field, "name")),
      "type" -> Json.fromString(text(field, "type")),
      "multiplicity" -> Json.fromString(text(field, "multiplicity")),
      "required" -> Json.fromBoolean(field.hcursor.get[Boolean]("required").fold(error => fail(error.message), identity))
    )
  })

  "The authored packaging descriptor" should {
    "provide the canonical project identity without granting runtime authority" in {
      Given("the style-less CML project and its canonical schema-3 CAR identity descriptor")
      val source = Paths.get("src/main/car/component-descriptor.json")

      When("the framework loads the actual descriptor through its standard loader")
      val descriptors = ComponentDescriptorLoader.load(source) match {
        case Consequence.Success(value) => value
        case failure => fail(s"Packaging descriptor rejected: $failure")
      }

      Then("exactly the project Component identity and version are retained")
      descriptors.size shouldBe 1
      val descriptor = descriptors.head
      descriptor.schemaVersion shouldBe Some(3)
      descriptor.componentId shouldBe Some(ComponentId("org.simplemodeling.textus.SmWorkflow"))
      descriptor.version shouldBe Some("0.1.0-SNAPSHOT")

      And("the descriptor supplies no settings or extensions that manufacture storage or user authority")
      descriptor.config shouldBe Map.empty
      descriptor.extensions shouldBe Map.empty
      descriptor.extensionBindings.fields shouldBe empty
      descriptor.componentStyleSnapshot shouldBe None
    }
  }

  "The authored ABI manifest" should {
    "retain the complete generated CML contract without presenting it as active operation authority" in {
      Given("the current generated CML model metadata and the authored schema-v2 ABI contract")
      val metadata = readJson("target/cozy/model-metadata.json")
      text(metadata, "schema") shouldBe "cozy.cml.model-metadata.v1"
      val component = metadata.hcursor.downField("surface").downField("component").focus.getOrElse(fail("Missing CML surface"))
      val services = array(component, "services")
      val elements = array(metadata, "modelElements")
      val abi = readJson("src/main/car/abi-manifest.json")

      When("its full declarative ABI is projected using Cozy's service, operation and field contract")
      val expectedExports = Json.obj(
        "components" -> Json.arr(Json.obj("namespace" -> Json.fromString("org.simplemodeling.textus"), "id" -> Json.fromString("SmWorkflow"))),
        "services" -> Json.fromValues(services.map(s => Json.obj("name" -> Json.fromString(text(s, "name"))))),
        "operations" -> Json.fromValues(services.flatMap { service =>
          array(service, "operations").map { operation => Json.obj(
            "service" -> Json.fromString(text(service, "name")),
            "name" -> Json.fromString(text(operation, "name")),
            "kind" -> Json.fromString(text(operation, "operationType")),
            "input" -> Json.fromString(text(operation, "inputType")),
            "output" -> Json.fromString(text(operation, "outputType"))
          ) }
        }),
        "types" -> Json.fromValues(elements.filter(e => text(e, "kind") != "entity").map { element =>
          val kind = text(element, "kind").toLowerCase(java.util.Locale.ROOT)
          Json.obj("name" -> Json.fromString(text(element, "name")),
            "kind" -> Json.fromString(if (kind == "command" || kind == "query") "value" else kind),
            "fields" -> fields(element))
        }),
        "entities" -> Json.fromValues(elements.filter(e => text(e, "kind") == "entity").map { element =>
          Json.obj("name" -> Json.fromString(text(element, "name")), "fields" -> fields(element))
        })
      )

      Then("all declared services, operations, input/output types and fields match rather than an empty facade")
      text(abi, "format") shouldBe "cozy.car.abi-manifest.v2"
      abi.hcursor.downField("component").focus shouldBe readJson("src/main/car/component-descriptor.json").hcursor.downField("component").focus
      abi.hcursor.downField("abi").focus shouldBe Some(Json.obj(
        "version" -> Json.fromInt(1), "exports" -> expectedExports, "dependencies" -> Json.arr()
      ))
    }
  }
}
