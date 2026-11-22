package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.DeterministicRepair.*

/** Executable specification for bounded diagnostic proposals.
  * @since   Nov. 22, 2026
  * @version Nov. 22, 2026
  * @author  ASAMI, Tomoharu
  */
final class DeterministicRepairSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Metals proposal admission" should {
    "exchange and attest a versioned edit while ignoring command alternatives" in {
      Given("an owned snapshot and a fixture-only rule for an exact diagnostic and edit")
      val document = Document("file:///work/A.scala", "r1", 7, "abc")
      val range = Range(Position(0, 0), Position(0, 1))
      val diagnostic = Diagnostic("metals", document.uri, "r1", 7, "E1", range, "example")
      val rule = new Rule {
        def accepts(document: Document, diagnostic: Diagnostic, action: Action): Boolean =
          diagnostic.code.equals("E1") && diagnostic.message.equals("example") && document.text.equals("abc") &&
            action.producer.equals("metals") && action.edits.equals(Vector(Edit(document.uri, 7, range, "x")))
      }
      val jsonrange = Json.obj("start" -> Json.obj("line" -> Json.fromInt(0), "character" -> Json.fromInt(0)),
        "end" -> Json.obj("line" -> Json.fromInt(0), "character" -> Json.fromInt(1)))
      val edit = Json.obj("range" -> jsonrange, "newText" -> Json.fromString("x"))
      val action = Json.obj("title" -> Json.fromString("fix"), "edit" -> Json.obj("documentChanges" -> Json.arr(Json.obj(
        "textDocument" -> Json.obj("uri" -> Json.fromString(document.uri), "version" -> Json.fromInt(7)), "edits" -> Json.arr(edit)))))
      var calls = Vector.empty[String]
      var response = Json.arr(Json.obj("title" -> Json.fromString("command"), "command" -> Json.obj()), action)
      val channel = new ScalaMetalsRepair.Transport {
        def request(method: String, params: Json, timeoutMillis: Int): Either[String, Json] = {
          calls = calls :+ method
          method match {
            case "initialize" => Right(Json.obj("capabilities" -> Json.obj("codeActionProvider" -> Json.True)))
            case "textDocument/codeAction" => Right(response)
            case "shutdown" => Right(Json.Null)
            case _ => Left("unexpected")
          }
        }
        def notify(method: String, params: Json): Either[String, Unit] = { calls = calls :+ method; Right(()) }
      }
      When("the adapter negotiates and receives the proposals")
      val result = ScalaMetalsRepair.actions(ScalaMetalsRepair.Policy(), Some(channel), "file:///work", document, diagnostic, Set(document.uri), Vector(rule))
      Then("only the attested snapshot-bound patch is returned and the lifecycle is closed")
      result match {
        case ScalaMetalsRepair.Available(patch) => patch.document shouldBe document
        case other => fail(other.toString)
      }
      calls shouldBe Vector("initialize", "initialized", "textDocument/didOpen", "textDocument/codeAction", "textDocument/didClose", "shutdown", "exit")
      When("competing actions, a crossed version, or a projection limit is returned")
      response = Json.arr(action, action)
      ScalaMetalsRepair.actions(ScalaMetalsRepair.Policy(), Some(channel), "file:///work", document, diagnostic, Set(document.uri), Vector(rule)) shouldBe ScalaMetalsRepair.Unavailable("competing admissible actions")
      ScalaMetalsRepair.actions(ScalaMetalsRepair.Policy(maxActions = 1), Some(channel), "file:///work", document, diagnostic, Set(document.uri), Vector(rule)).isInstanceOf[ScalaMetalsRepair.Malformed] shouldBe true
      response = Json.arr(action.deepMerge(Json.obj("edit" -> Json.obj("documentChanges" -> Json.arr(Json.obj(
        "textDocument" -> Json.obj("uri" -> Json.fromString(document.uri), "version" -> Json.fromInt(8)), "edits" -> Json.arr(edit)))))))
      ScalaMetalsRepair.actions(ScalaMetalsRepair.Policy(), Some(channel), "file:///work", document, diagnostic, Set(document.uri), Vector(rule)) shouldBe ScalaMetalsRepair.NoAction
      Then("none produces a patch")
      When("the owned selection is stale")
      calls = Vector.empty
      ScalaMetalsRepair.actions(ScalaMetalsRepair.Policy(), Some(channel), "file:///work", document.copy(revision = "r2"), diagnostic, Set(document.uri), Vector(rule)).isInstanceOf[ScalaMetalsRepair.Malformed] shouldBe true
      Then("no transport call occurs")
      calls shouldBe empty
    }
    "reject stale, unowned, overlapping and invalid UTF-16 proposals" in {
      Given("a selected document and an attesting fixture rule")
      val document = Document("file:///work/A.scala", "r", 1, "😀")
      val range = Range(Position(0, 0), Position(0, 2))
      val diagnostic = Diagnostic("metals", document.uri, "r", 1, "E", range, "m")
      val action = Action("metals", "fixture", diagnostic, Vector(Edit(document.uri, 1, range, "x")))
      val rule = new Rule { def accepts(document: Document, diagnostic: Diagnostic, action: Action): Boolean = true }
      When("the ownership, revision or ranges are invalid")
      Then("admission fails")
      admit(action, document, Set.empty, Vector(rule), Limits(2, 10)).isLeft shouldBe true
      admit(action, document.copy(revision = "other"), Set(document.uri), Vector(rule), Limits(2, 10)).isLeft shouldBe true
      admit(action.copy(edits = action.edits ++ action.edits), document, Set(document.uri), Vector(rule), Limits(2, 10)).isLeft shouldBe true
      val split = Range(Position(0, 1), Position(0, 2))
      admit(action.copy(edits = Vector(Edit(document.uri, 1, split, "x"))), document, Set(document.uri), Vector(rule), Limits(2, 10)).isLeft shouldBe true
    }
    "report unavailable and reject malformed configuration" in {
      Given("a selected document without a transport")
      val document = Document("file:///work/A.scala", "r", 1, "a")
      val diagnostic = Diagnostic("metals", document.uri, "r", 1, "E", Range(Position(0, 0), Position(0, 1)), "m")
      When("the service is absent or disabled")
      Then("neither is an actionable result")
      ScalaMetalsRepair.actions(ScalaMetalsRepair.Policy(), None, "file:///work", document, diagnostic, Set(document.uri), Vector.empty) shouldBe ScalaMetalsRepair.Unavailable("transport unavailable")
      ScalaMetalsRepair.actions(ScalaMetalsRepair.Policy(enabled = false), None, "file:///work", document, diagnostic, Set(document.uri), Vector.empty) shouldBe ScalaMetalsRepair.Unavailable("disabled")
      WorkflowProjectDeclaration.metalsPolicyC(Json.obj()).toOption.nonEmpty shouldBe true
      WorkflowProjectDeclaration.metalsPolicyC(Json.obj("validation" -> Json.obj("metals" -> Json.Null))).toOption.nonEmpty shouldBe false
      WorkflowProjectDeclaration.metalsPolicyC(Json.obj("validation" -> Json.obj("metals" -> Json.obj("unexpected" -> Json.True)))).toOption.nonEmpty shouldBe false
    }
  }
}
