package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take
import DevelopmentOperationalView as V

/** @since Oct. 25, 2026
  * @version Oct. 25, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class DevelopmentOperationalViewSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _subject = V.Subject(V.Car("org.example", "car", "1"), "component", "instance", "GoalPhase", "session", "repository", "phase")
  private val _request = Json.obj("schemaVersion" -> Json.fromString(V.schema), "subject" -> _subject.asJson)
  "Development operational wire" should {
    "accept only exact subjects and finite history bounds" in {
      Given("a complete subject without optional limit")
      take(V.decodeRequestC(_request, true)).limit shouldBe 20
      When("limits, unknown fields and schema alternatives are supplied")
      Vector(0, 101).foreach(n => V.decodeRequestC(_request.deepMerge(Json.obj("limit" -> Json.fromInt(n))), true).isSuccess shouldBe false)
      V.decodeRequestC(_request.deepMerge(Json.obj("limit" -> Json.fromDoubleOrNull(1.5))), true).isSuccess shouldBe false
      V.decodeRequestC(_request.deepMerge(Json.obj("path" -> Json.fromString("/private"))), true).isSuccess shouldBe false
      V.decodeRequestC(_request.deepMerge(Json.obj("limit" -> Json.fromInt(1))), false).isSuccess shouldBe false
      V.decodeRequestC(_request.deepMerge(Json.obj("schemaVersion" -> Json.fromString("unknown"))), true).isSuccess shouldBe false
      Then("the valid finite endpoints remain available")
      Vector(1, 100).foreach(n => take(V.decodeRequestC(_request.deepMerge(Json.obj("limit" -> Json.fromInt(n))), true)).limit shouldBe n)
    }
    "project only genuine native lifecycle and progression without inventing optional facts" in {
      Given("a validated native InstanceRecord and its actual active Decision successor")
      val initial = take(InstanceRecord(InstanceIdentity("development-state"), take(GoalPhaseRuntimeCatalog.loadC()).definition,
        initialRevision, Lifecycle.NotStarted, None, CorrelationReference("correlation"), CausationReference("cause"),
        None, None, None, Vector.empty).validateC)
      val active = take(initial.appendC(initial.revision, GoalPhaseNativeRun.history(initial, "phase.Decision", "actual-decision")))
      val before = active
      When("the restricted read-only adapter projects that retained record")
      val state = take(GoalPhaseNativeObservation.projectC(active))
      Then("only its actual lifecycle and progression are reported, with no invented work or Human facts")
      state.workflowState shouldBe "active"
      state.stage shouldBe Some("Decision")
      state.attention shouldBe "none"
      state.stepIdentity shouldBe None
      state.workKind shouldBe None
      active shouldBe before
      When("the actual native lifecycle is completed without a pending progression")
      val completed = take(active.appendC(active.revision, GoalPhaseNativeRun.history(active, "phase.Closed", "terminal").copy(
        lifecycle = Lifecycle.Completed, currentProgression = None)))
      Then("the projection reports the genuine completed lifecycle")
      take(GoalPhaseNativeObservation.projectC(completed)).workflowState shouldBe "completed"
      When("the single final retained-record recheck observes a concurrent completion or deletion")
      GoalPhaseNativeObservation.consistent(active, Some(completed), state) shouldBe GoalPhaseNativeObservation.Observation("changing")
      GoalPhaseNativeObservation.consistent(active, None, state).state shouldBe None
      Then("only the identical actual retained record permits the selected safe projection")
      GoalPhaseNativeObservation.consistent(active, Some(active), state).state shouldBe Some(state)
    }
    "allow safe owner destinations without credentials, query tokens or insecure remote origins" in {
      Given("owner-selected HTTPS and loopback destinations alongside executable, credentialled and token-bearing alternatives")
      val safe = Vector("https://development.example/phase", "http://localhost:1234/phase")
      val unsafe = Vector("javascript:alert(1)", "https://user:password@example/", "https://example/?token=secret", "http://remote.example/")
      When("the same bounded destination predicate checks each owner URL")
      val accepted = safe.map(V.safeDetailsUrl)
      val refused = unsafe.map(V.safeDetailsUrl)
      Then("ordinary HTTPS and local destinations pass while the unsafe alternatives are refused")
      accepted.foreach(_ shouldBe true)
      refused.foreach(_ shouldBe false)
    }
  }
}
