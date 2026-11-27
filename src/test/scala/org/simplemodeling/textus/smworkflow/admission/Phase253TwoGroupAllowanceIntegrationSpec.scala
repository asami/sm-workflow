/**
 * @since   Nov. 27, 2026
 * @version Nov. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import io.circe.Json
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.spi.{ResolvedSpiMember, SpiMemberMetadata}
import org.goldenport.cncf.spi.ai.runner.{AiRunner, AiRunnerSocketSet}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.ai.runtime.Phase253TwoGroupAllowanceFixture as Fixture
import org.simplemodeling.textus.smworkflow.value as V

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.PackagedRuntimeTestDeclaration])
final class Phase253TwoGroupAllowanceIntegrationSpec extends AnyWordSpec with Matchers {
  private def response(body: Json): String = {
    val model = body.hcursor.get[String]("model").toOption.getOrElse(fail("missing selected model"))
    val prompt = body.hcursor.downField("messages").downN(0).get[String]("content").toOption.getOrElse(fail("missing prompt"))
    val content = if (prompt.startsWith("Propose a single small Scala compiler correction")) {
      val packetLine = prompt.linesIterator.drop(1).nextOption().getOrElse(fail("missing diagnostic packet"))
      val packet = io.circe.parser.parse(packetLine).toOption.getOrElse(fail("invalid diagnostic packet")).hcursor
      if (packet.get[String]("selectedSource").toOption.nonEmpty) Json.obj("replacement" -> Json.fromString("42"))
      else Json.obj("candidate" -> Json.fromString(packet.get[String]("candidate").toOption.get),
        "validationReference" -> Json.fromString(packet.get[String]("validationReference").toOption.get),
        "path" -> Json.fromString(packet.get[String]("path").toOption.get),
        "range" -> Json.obj("start" -> Json.obj("line" -> Json.fromInt(0), "character" -> Json.fromInt(28)),
          "end" -> Json.obj("line" -> Json.fromInt(0), "character" -> Json.fromInt(33))),
        "expectedSource" -> Json.fromString("\"bad\""), "replacement" -> Json.fromString("42"))
    } else Json.obj("outcome" -> Json.fromString("accept"), "summary" -> Json.fromString("offline review"), "findings" -> Json.arr())
    Json.obj("id" -> Json.fromString("fixture-response"), "model" -> Json.fromString(model),
      "usage" -> Json.obj("prompt_tokens" -> Json.fromInt(1), "completion_tokens" -> Json.fromInt(1), "total_tokens" -> Json.fromInt(2)),
      "choices" -> Json.arr(Json.obj("finish_reason" -> Json.fromString("stop"),
        "message" -> Json.obj("content" -> Json.fromString(content.noSpaces))))).noSpaces
  }
  private def compilerTask: GoalPhaseApiReview.BoundedRecordTask = {
    import DeterministicRepair.*
    val text = "object A { val value: Int = \"bad\" }\n"
    val uri = "file:///fixture/A.scala"
    val document = Document(uri, "a" * 40, 0, text)
    val point = Position(0, 28)
    val material = CompilerMaterial("session", "12", document.revision, "11:compile", "src/main/scala/A.scala",
      "sm-workflow-repository", document, Diagnostic("scala-compiler", uri, document.revision, 0,
        "compiler-error", Range(point, point), "type mismatch"))
    GoalPhaseApiReview.compilerTask(material, "compiler-attempt", "compiler-continuation", "11:compile", 65536)
      .getOrElse(fail("compiler task unavailable"))
  }
  "The actual producer's two-group allowance through ordinary consumer routes" should {
    "load the verified producer and select each group or refuse without paid fallback" in {
      val expected = Path.of(sys.env("SM_WORKFLOW_SPEC_P251_ALLOWANCE_JAR")).toRealPath()
      Path.of(Fixture.producerSource.toURI).toRealPath() shouldBe expected
      for (kind <- Vector("review", "compiler"); (first, second, selected) <- Vector(
        (1000000L, 1000000L, Some(("gpt-5.6-terra", "openai-daily-group-2"))),
        (1000000L, 1L, Some(("gpt-5.6-sol", "openai-daily-group-1"))),
        (1L, 1L, None))) {
        val db = SqlDataStore.sqlite(Files.createTempDirectory("p253-two-groups-").resolve("ledger.db").toString)
        val base = ExecutionContext.test()
        val transport = new Fixture.Transport
        transport.reply = response
        val ctx = transport.context(base)
        ctx.dataStoreSpace.useDataStore(db)
        try {
          given ExecutionContext = ctx
          val runner = Fixture.runner(Fixture.groups(first, second))
          val socket = new AiRunnerSocketSet {}
          val id = ComponentId("org.example.Phase253Fixture")
          socket.installSpiMembers(Vector(ResolvedSpiMember[AiRunner](runner,
            SpiMemberMetadata("ai-runner", id.name, ComponentInstanceId.default(id),
              capabilities = Set("textus-ai.bounded-task.v1"), isDefault = true))))
          if (kind == "review") {
            val input = V.GoalPhaseApiReviewInput("session", "work:exact", "review-attempt", "file:///package", Some(true))
            val result = GoalPhaseApiReview.reviewWithSocketC(Some(socket), true, input, "review-continuation", "package")
              .toOption.getOrElse(fail("missing review result"))
            result.status shouldBe (if (selected.nonEmpty) "COMPLETED" else "DECLINED")
            result.model shouldBe selected.map(_._1)
            selected.foreach { case (_, group) => result.metadata.flatMap(_.getString("ai.usage.model_group")) shouldBe Some(group) }
          } else {
            GoalPhaseApiReview.boundedRecord(runner, compilerTask) match {
              case GoalPhaseApiReview.BoundedRecordResult.Completed(result) =>
                selected should not be empty
                result.model shouldBe selected.map(_._1)
                result.metadata.get("ai.usage.model_group") shouldBe selected.map(_._2)
                result.record.getString("replacement") shouldBe Some("42")
              case GoalPhaseApiReview.BoundedRecordResult.Declined => selected shouldBe None
              case other => fail("unexpected compiler result: " + other)
            }
          }
          transport.calls.get() shouldBe (if (selected.nonEmpty) 1 else 0)
        } finally db.closeC()
      }
    }
  }
}
