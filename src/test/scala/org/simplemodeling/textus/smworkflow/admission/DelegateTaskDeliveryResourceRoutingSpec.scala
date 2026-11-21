package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import java.nio.file.Files
import org.goldenport.record.RecordPresentable
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Ordinary standard composition, with paired inline/authorized Resource
  * observations. No destination worker, SBT or publication is performed.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class DelegateTaskDeliveryResourceRoutingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  "The normal DelegateTask delivery frontdoor" should {
    "read the same complete observation through Resource before session composition and keep inline re-entry identical" in {
      val s = NativeCommonDelegateTaskFixture.fresh(None)
      // Select the check names already bound by this normal host, as in its
      // common-delegation qualification. This requester never runs them.
      val request = DelegateTaskRequesterRoutingFixture.input("resource-frontdoor-specification")
        .copy(checks = NonEmptyVector.of("compile", "test-only"))
      val path = s.root.resolve("requester-input.json")
      Files.writeString(path, json(request))
      val preparation = V.WorkflowDelegateTaskStartPreparationInput(path.toUri.toASCIIString,
        "resource-requester-selection", "participant", "ExplicitHuman", "resource-requester-key", "entity:smworkflow.cli.goal_selection")
      val prepared = s.callService("NativeWorkflowPreparationService", "prepareDelegateTaskStart",
        Vector("--input", json(preparation)))
      prepared.exitCode shouldBe 0
      s.selectPreparedImport(prepared)
      def view(result: F.Result): V.DelegateTaskView = NativeCommonDelegateTaskFixture.view(s, result,
        (scenario, service, operation, parameters) => scenario.callService(service, operation, parameters))
      val started = view(s.callService("DelegateTaskService", "startDelegateTask", Vector("--input", json(request),
        "--selectionId", preparation.selectionId, "--participant", "participant", "--idempotencyKey", preparation.idempotencyKey)))
      val observation = V.DelegateTaskObservationInput(started.sessionId, "dispatch-observed",
        V.WorkflowArtifactReference("SPECIFICATION-OWNED-transport-observation", "1"),
        Some("SPECIFICATION-OWNED-destination-session"), None, None, "SPECIFICATION-OWNED observed dispatch; no execution fact")
      val input = s.root.resolve("delivery-observation.json")
      Files.writeString(input, json(observation))
      def call(arguments: Vector[String]): F.Result = s.callService("DelegateTaskDeliveryService",
        "observeDelegateTaskDelivery", arguments)
      Given("the public inputResource route without an inline input or separately supplied sessionId")
      val resource = view(call(Vector("--inputResource", input.toUri.toASCIIString)))
      val inline = view(call(Vector("--input", json(observation))))
      val repeated = view(call(Vector("--inputResource", input.toUri.toASCIIString)))
      Then("both representations address the same requester and preserve its exact immutable observation")
      DelegateTaskRequesterRoutingFixture.state(resource).stage shouldBe "AwaitResult"
      DelegateTaskRequesterRoutingFixture.state(inline) shouldBe DelegateTaskRequesterRoutingFixture.state(resource)
      DelegateTaskRequesterRoutingFixture.state(repeated) shouldBe DelegateTaskRequesterRoutingFixture.state(resource)
      DelegateTaskRequesterRoutingFixture.assertNoExecution(DelegateTaskRequesterRoutingFixture.state(resource))
      Files.exists(s.root.resolve("sbt-executions.txt")) shouldBe false
      call(Vector("--input", json(observation), "--inputResource", input.toUri.toASCIIString)).exitCode should not be 0
      call(Vector("--inputResource", s.root.resolve("missing-observation.json").toUri.toASCIIString)).exitCode should not be 0
      val crossed = observation.copy(sessionId = "nonexistent-requester")
      Files.writeString(input, json(crossed))
      call(Vector("--inputResource", input.toUri.toASCIIString)).exitCode should not be 0
      val retained = view(s.callService("DelegateTaskService", "advanceDelegateTask", Vector("--sessionId", started.sessionId)))
      DelegateTaskRequesterRoutingFixture.state(retained) shouldBe DelegateTaskRequesterRoutingFixture.state(resource)
    }
  }
}
