package org.simplemodeling.textus.smworkflow.admission

import io.circe.syntax.*
import java.nio.file.{Files, Path}
import java.util.jar.JarFile
import java.util.zip.ZipFile
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import StandaloneFixExecution as S
import StandaloneFixExecution.given
import WorkflowSbtValidation as T
import RepositorySyncNativeMergeValidation as B

/** Bounded public-operation specifications. Check, worker, chat and source facts
  * are fixture-owned; these cases prove no installed/cross-chat acceptance.
  * @since   Oct. 27, 2026
  * @version Oct. 27, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class ThreeWorkflowTaskFixDeliverySpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativePreparationOperationFixture {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail("Delivery fixture failed: " + other)
  }
  private def business[A: ValueReader](value: Consequence[OperationResponse]): A = take(value) match {
    case OperationResponse.Json(json) => take(summon[ValueReader[A]].readC(take(
      new org.goldenport.record.io.RecordDecoder().json(json.noSpaces))))
    case other => fail("Expected generated public output: " + other)
  }
  private val selected = Vector(GoalPhaseNativeValidation.Check("compile", 1, T.Kind.Compile),
    GoalPhaseNativeValidation.Check("focused", 2, T.Kind.TestOnly))
  private val input = S.Input("fixture-repository", "selected-delivery", "Implement the selected behavior",
    Vector("src"), selected.map(_.identity), Vector("behavior"), Vector(ContextReference("request", "1")), false,
    scalaLint = "off")
  private val candidate = S.Candidate("a" * 40, "b" * 40, Vector("src/Behavior.scala"), "fixture delta")
  private val conditions = Vector(S.Condition("behavior", true, "Fixture selected behavior addressed"))
  private val references = Vector(ContextReference("fixture-retained-check-batch", "1"))
  private def receipt(check: GoalPhaseNativeValidation.Check) = B.Receipt(check.identity, "exited", Some(0), "", "",
    Some(T.Facts(check.kind, T.Outcome.Succeeded,
      if (check.kind == T.Kind.TestOnly) Some(T.Selection(Vector("fixture.BehaviorSpec"), 1, 2)) else None, None)))
  private def validated(task: Boolean = false): S.State = {
    val adopted = input.copy(task = Option.when(task)(S.Task("direct-instruction", Vector.empty)))
    val started = take(S.startC(adopted, "destination-participant", selected))
    val implemented = if (task) take(S.completedC(S.Work("destination-session", "1", "Implement", started),
      S.Result("completed", "Fixture implementation", Vector.empty, conditions, Vector.empty), ContextReference("author", "1")))
      else started.copy(semanticReference = Some(ContextReference("repair", "1")), conditionReport = conditions,
        automaticCount = 2, ordinaryCount = 1)
    selected.foldLeft(take(S.candidateC(implemented, candidate)))((state, check) =>
      take(S.observedC(take(S.intentC(state)), receipt(check)))).copy(evidenceReferences = references)
  }
  private def committed = {
    val reviewed = take(S.completedC(S.Work("destination-session", "9", "Review", validated(true)),
      S.Result("accept", "Fixture independent review result", Vector.empty, conditions, Vector.empty), ContextReference("review", "1")))
    take(S.committedC(reviewed.copy(stage = "CommitIntent"), S.Commit("c" * 40, candidate.head, candidate.tree)))
  }
  private def response(state: S.State, kind: String = "Terminal") = V.StandaloneFixView("destination-session", kind,
    state.message, Some(take(new org.goldenport.record.io.RecordDecoder().json(state.asJson.noSpaces))),
    None, None, None, None, None, Vector.empty, None)
  private def preparation(e: PreparationEpisode, state: S.State, route: String = "sm-test-fix", kind: String = "Terminal") =
    V.WorkflowTaskFixDeliveryPreparationInput(route, input.requestIdentity, "requester-chat", "destination-chat",
      "destination-participant", e.saveValue("destination-result-" + java.util.UUID.randomUUID().toString, response(state, kind)), V.WorkflowArtifactReference("actual-shaped-source", "1"))
  private def prepare(e: PreparationEpisode, input: V.WorkflowTaskFixDeliveryPreparationInput) =
    business[V.WorkflowTaskFixDelivery](e.invoke("prepareTaskFixDelivery", input.toRecord()))
  private def adoption(e: PreparationEpisode, packet: V.WorkflowTaskFixDelivery, disposition: String = "adopt") =
    V.WorkflowTaskFixAdoptionPreparationInput(e.saveValue("delivery-" + java.util.UUID.randomUUID().toString, packet), packet.requestIdentity,
      packet.requesterIdentity, packet.destinationIdentity, disposition, "Requester assesses this exact returned scope")
  // Unchanged public response from p67-task-delivery-acceptance/host/
  // destination-task-standalone6-20261027/advance-closure-response.json.
  // Archived observations exercise serialization; this test performs no work or authentication.
  private def observedTaskResponse: io.circe.Json = {
    io.circe.parser.parse(TaskFixDeliveryPublicWireFixture.completedTask).fold(error => fail(error.getMessage), identity)
  }
  private def record(json: io.circe.Json) = take(new org.goldenport.record.io.RecordDecoder().json(json.noSpaces))
  private def observedPreparation(e: PreparationEpisode, json: io.circe.Json) =
    V.WorkflowTaskFixDeliveryPreparationInput("sm-goal-task", "p67-real-existing-chat-task-20261027",
      "requester-chat", "destination-chat", "asami",
      e.save("observed-task-" + java.util.UUID.randomUUID().toString, json.noSpaces),
      V.WorkflowArtifactReference("archived-public-response-fixture", "1"))

  "Task/FIX existing-chat attachments" should {
    "prepare and adopt the ordinary snake-case completed Task response without rewriting its observation" in {
      withPreparation() { e =>
        val wire = observedTaskResponse
        val original = record(wire)
        val packet = prepare(e, observedPreparation(e, wire))
        packet.disposition shouldBe "completed-committed"
        packet.sessionId shouldBe "4698ba5ab263a9b42ec517ae479820ff"
        packet.observation shouldBe original
        packet.provenance should include("authentication")
        val returned = business[V.WorkflowTaskFixAdoption](e.invoke("prepareTaskFixAdoption", adoption(e, packet).toRecord()))
        returned.delivery shouldBe packet
        returned.delivery.observation shouldBe original
        // Compare the complete retained public JSON, including fields not used to derive the label.
        val retained = io.circe.parser.parse(take(returned.delivery.observation.toJsonStringC)).fold(
          error => fail(error.getMessage), identity)
        retained shouldBe wire
        val state = retained.hcursor.downField("state")
        state.get[String]("projection_warning").toOption shouldBe Some("Common Evidence archive returned no allocation")
        state.get[Int]("generation").toOption shouldBe Some(1)
        state.get[Int]("automatic_count").toOption shouldBe Some(0)
        state.get[Int]("ordinary_count").toOption shouldBe Some(0)
        state.downField("prior_review").get[String]("outcome").toOption shouldBe Some("accept")
        state.downField("prior_review_candidate").focus shouldBe state.downField("candidate").focus
        state.downField("commit").get[String]("identity").toOption shouldBe Some("07e623c2bc0a5f5c9c2d893a4ad5bc92ee031811")
        state.downField("receipts").downArray.right.downField("sbt").downField("selection")
          .get[Int]("tests_run").toOption shouldBe Some(5)
      }
    }

    "refuse mixed public aliases and contradictory delivery labels in both observed Task preparation paths" in {
      withPreparation() { e =>
        val wire = observedTaskResponse
        val packet = prepare(e, observedPreparation(e, wire))
        // Even equal aliases are ambiguous and must be refused before the State decoder selects one.
        Vector("p67-delivery-example", "foreign-repository").foreach { repository =>
          val collision = wire.hcursor.downField("state").downField("input").withFocus(
            _.mapObject(_.add("repositoryIdentity", io.circe.Json.fromString(repository)))).top.get
          e.invoke("prepareTaskFixDelivery", observedPreparation(e, collision).toRecord()).isSuccess shouldBe false
          e.invoke("prepareTaskFixAdoption", adoption(e, packet.copy(observation = record(collision))).toRecord())
            .isSuccess shouldBe false
        }
        e.invoke("prepareTaskFixAdoption", adoption(e, packet.copy(disposition = "accepted-uncommitted")).toRecord())
          .isSuccess shouldBe false
        val contradictory = wire.hcursor.downField("state").downField("stage")
          .withFocus(_ => io.circe.Json.fromString("Accepted")).top.get
        e.invoke("prepareTaskFixDelivery", observedPreparation(e, contradictory).toRecord()).isSuccess shouldBe false
        e.invoke("prepareTaskFixAdoption", adoption(e, packet.copy(observation = record(contradictory))).toRecord())
          .isSuccess shouldBe false
      }
    }

    "retain matching skill and contract bytes in the actual source JAR and ZIP without asserting deployment" in {
      val jar = new JarFile("target/scala-3.3.8/textus-sm-workflow_3-0.1.0-SNAPSHOT.jar")
      try {
        val zip = new ZipFile("target/sm-workflow-skills-0.1.0-SNAPSHOT.zip")
        try {
          val paths = Vector("sm-test-fix", "sm-goal-task", "sm-review-fix").map(n => s"skills/$n/SKILL.md") ++
            Vector("task-fix-delivery", "standalone-fix-runtime", "goal-task-runtime", "standalone-review-fix-runtime",
              "standalone-fix-acceptance").map(n => s"docs/spec/$n.md")
          paths.foreach { path =>
            val source = Files.readAllBytes(Path.of(path)).toVector
            val packaged = jar.getInputStream(jar.getJarEntry("META-INF/sm-workflow/" + path))
            try packaged.readAllBytes().toVector shouldBe source finally packaged.close()
            val bundled = zip.getInputStream(zip.getEntry(path))
            try bundled.readAllBytes().toVector shouldBe source finally bundled.close()
          }
        } finally zip.close()
      } finally jar.close()
    }

    "retain actual-shaped selected checks, sources and counters and distinguish committed Task from uncommitted FIX" in {
      withPreparation() { e =>
        val standalone = validated()
        val fix = prepare(e, preparation(e, standalone))
        fix.disposition shouldBe "accepted-uncommitted"
        fix.provenance should include("authentication")
        val returned = business[V.WorkflowTaskFixAdoption](e.invoke("prepareTaskFixAdoption", adoption(e, fix).toRecord()))
        returned.delivery shouldBe fix
        returned.effect should include("attachment-only")
        val state = take(io.circe.parser.parse(take(returned.delivery.observation.toJsonStringC)).fold(
          err => Consequence.stateConflict(err.getMessage), json => Consequence.success(json)))
        state.hcursor.downField("state").get[Int]("automaticCount").toOption shouldBe Some(2)
        val task = prepare(e, preparation(e, committed, "sm-goal-task"))
        task.disposition shouldBe "completed-committed"
        task.observation shouldBe response(committed).toRecord()
        task.toRecord().keySet should not contain "datastore"
        standalone.commit shouldBe None
      }
    }

    "preserve original review finding dispositions in the same standalone delivery route" in {
      withPreparation() { e =>
        val finding = StandaloneReviewFix.Finding("original-1", "Original selected defect", candidate.paths, Vector.empty)
        val origin = StandaloneReviewFix.Origin(ContextReference("original-review", "3"), "Retained original meaning", Vector(finding))
        val state = validated().copy(input = input.copy(reviewOrigin = Some(origin)), reviewDispositions = Some(Vector(
          StandaloneReviewFix.Disposition("original-1", "fixed", "mechanical", "Fixture verified selected regression", candidate.paths, Vector("focused")))))
        val packet = prepare(e, preparation(e, state, "sm-review-fix"))
        packet.disposition shouldBe "accepted-uncommitted"
        packet.observation shouldBe response(state).toRecord()
        e.invoke("prepareTaskFixDelivery", preparation(e, state, "sm-test-fix").toRecord()).isSuccess shouldBe false
      }
    }

    "keep failed and UNKNOWN closure and observed work/wait incomplete rather than allowing successful adoption" in {
      withPreparation() { e =>
        Vector(("CommitUnknown", "Wait", "observed-wait"), ("CommitFailed", "Incomplete", "closure-failed"),
          ("Implement", "Request", "observed-work"), ("ValidationIntent", "Wait", "observed-wait")).foreach { (stage, kind, label) =>
          val state = validated(true).copy(stage = stage)
          val packet = prepare(e, preparation(e, state, "sm-goal-task", kind))
          packet.disposition shouldBe label
          e.invoke("prepareTaskFixAdoption", adoption(e, packet).toRecord()).isSuccess shouldBe false
          business[V.WorkflowTaskFixAdoption](e.invoke("prepareTaskFixAdoption", adoption(e, packet, "defer").toRecord())).disposition shouldBe "defer"
        }
      }
    }

    "refuse crossed owners/request/participant, altered closure labels and unsupported success without executing missing work" in {
      withPreparation() { e =>
        val selectedInput = preparation(e, validated())
        Vector(selectedInput.copy(requesterIdentity = "destination-chat"), selectedInput.copy(requestIdentity = "foreign"),
          selectedInput.copy(destinationParticipant = "foreign"), selectedInput.copy(route = "sm-goal-task")).foreach { invalid =>
          e.invoke("prepareTaskFixDelivery", invalid.toRecord()).isSuccess shouldBe false
        }
        e.invoke("prepareTaskFixDelivery", preparation(e, validated().copy(receipts = Vector.empty)).toRecord()).isSuccess shouldBe false
        e.invoke("prepareTaskFixDelivery", preparation(e, committed.copy(commit = None), "sm-goal-task").toRecord()).isSuccess shouldBe false
        val packet = prepare(e, selectedInput)
        val changed = adoption(e, packet.copy(disposition = "completed-committed"))
        e.invoke("prepareTaskFixAdoption", changed.toRecord()).isSuccess shouldBe false
        e.invoke("prepareTaskFixAdoption", adoption(e, packet).copy(requesterIdentity = "another-requester").toRecord()).isSuccess shouldBe false
      }
    }

    "return no-change without creating a commit or adding a check/review executor" in {
      withPreparation() { e =>
        val started = take(S.startC(input, "destination-participant", selected))
        val noChange = selected.foldLeft(take(S.candidateC(started, candidate.copy(paths = Vector.empty, diff = ""))))((state, check) =>
          take(S.observedC(take(S.intentC(state)), receipt(check))))
        noChange.stage shouldBe "NoChange"
        val packet = prepare(e, preparation(e, noChange))
        packet.disposition shouldBe "no-change"
        business[V.WorkflowTaskFixAdoption](e.invoke("prepareTaskFixAdoption", adoption(e, packet).toRecord())).delivery shouldBe packet
        noChange.reviewReference shouldBe None
        noChange.commit shouldBe None
      }
    }
  }
}
