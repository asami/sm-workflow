package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseStartCommand, SplitPhaseStartInput}
import SplitPhaseExplicitStartPreparation.{Record, Reference}

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseStartRecordRetentionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"retention fixture rejected: $failure")
  }
  private val definition = take(SplitPhaseRuntimeCatalog.loadC()).definition
  private val config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("registry")),
    MigrationPolicy("v1"), RetentionPolicy("v1"), LeasePolicy("v1"), DeliveryConfiguration.SameStore(WorkflowStoreIdentity("registry")))
  private val command = SplitPhaseStartCommand(SplitPhaseStartInput("77", "PREVIEW", "authority", Some("snapshot"), None, None),
    "selection", "participant", "key")
  private val selection = ProfileStartAdmission.Selection("selection", StateMachineOperationIdentity("SplitPhaseService", "startSplitPhase"),
    definition.workflowIdentity, definition.workflowRevision, "participant", ProfileStartAdmission.SelectionOrigin.ExplicitHuman, "key", None)
  private val original = Record(Reference("selection", "1"), InstanceIdentity("instance"), definition.workflowIdentity,
    definition.workflowRevision, config, command, selection, SplitPhaseEntryAssessment.Assessment(
      SplitPhaseEntryAssessment.SourceSnapshot("snapshot", "77", "authority", "1", "a" * 64), None))
  private def row(value: Record) = org.goldenport.record.Record.data(
    SplitPhaseExplicitStartRecordJsonV1.storageField -> take(SplitPhaseExplicitStartRecordJsonV1.encodeC(value)))
  private final class Storage extends SplitPhaseStartRecordRetention.Port {
    var actual = Option.empty[org.goldenport.record.Record]
    var creates = 0
    var failCreate = false
    var maskAfterCreate = false
    var divergentAfterCreate = false
    var created = false
    def loadC() = if (created && maskAfterCreate) Consequence.success(None)
      else if (created && divergentAfterCreate) Consequence.success(Some(row(original.copy(instanceIdentity = InstanceIdentity("foreign")))))
      else Consequence.success(actual)
    def createC(value: org.goldenport.record.Record) = {
      creates += 1
      if (failCreate) Consequence.stateConflict("test-only uncertain Create")
      else { actual = Some(value); created = true; Consequence.unit }
    }
  }

  "SplitPhase fixed invocation record retention" should {
    "create once and return only exact observed data on equal retry" in {
      Given("an empty invocation store and the exact immutable Start record")
      val storage = new Storage
      When("the record is retained for the first time")
      val initial = take(SplitPhaseStartRecordRetention.retainC(original, storage))
      Then("the observed record matches the original allocation")
      initial shouldBe original
      When("the identical record is retained again")
      val repeated = take(SplitPhaseStartRecordRetention.retainC(original, storage))
      Then("the retry returns the same row without another Create")
      repeated shouldBe original
      storage.creates shouldBe 1
      storage.actual shouldBe Some(row(original))
    }
    "reject every full-record conflict without overwriting an existing invocation" in {
      Vector(original.copy(instanceIdentity = InstanceIdentity("foreign")),
        original.copy(reference = original.reference.copy(revision = "2")),
        original.copy(command = command.copy(idempotencyKey = "foreign")),
        original.copy(selection = selection.copy(recommendationReference = Some("foreign"))),
        original.copy(assessment = original.assessment.copy(source = original.assessment.source.copy(revision = "2"))))
        .foreach { changed =>
          Given("an existing invocation row and a conflict in one of its full-record fields")
          val storage = new Storage
          storage.actual = Some(row(original))
          When("the conflicting invocation is retained")
          val result = SplitPhaseStartRecordRetention.retainC(changed, storage)
          Then("retention fails without Create or replacement of the existing row")
          result shouldBe a[Consequence.Failure[?]]
          storage.creates shouldBe 0
          storage.actual shouldBe Some(row(original))
        }
    }
    "publish no success after an uncertain Create or unavailable divergent readback" in {
      Vector("create", "missing", "divergent").foreach { mode =>
        Given("a store with uncertain Create acknowledgement or missing or divergent committed readback")
        val storage = new Storage
        storage.failCreate = mode == "create"
        storage.maskAfterCreate = mode == "missing"
        storage.divergentAfterCreate = mode == "divergent"
        When("the original invocation record is retained through that fault")
        val result = SplitPhaseStartRecordRetention.retainC(original, storage)
        Then("no success is published and the uncertainty remains transaction-indeterminate")
        result match {
          case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets.collect {
            case org.goldenport.observation.Descriptor.Facet.Reason(name) => name
          } should contain("transaction-indeterminate")
          case other => fail(s"Uncertain registration published success: $other")
        }
        storage.creates shouldBe 1
        if (mode != "create") {
          storage.maskAfterCreate = false
          storage.divergentAfterCreate = false
          When("the committed readback becomes ordinary and the same record is retried")
          val recovered = take(SplitPhaseStartRecordRetention.retainC(original, storage))
          Then("the original row is recovered without another Create")
          recovered shouldBe original
          storage.creates shouldBe 1
        }
      }
    }
    "refuse malformed existing data and absent dependencies without Create" in {
      Given("an existing row without the Start payload")
      val storage = new Storage
      storage.actual = Some(org.goldenport.record.Record.data("unrelated" -> "data"))
      When("retention observes that malformed row")
      val malformed = SplitPhaseStartRecordRetention.retainC(original, storage)
      Then("the row is refused without Create")
      malformed shouldBe a[Consequence.Failure[?]]
      storage.creates shouldBe 0
      When("the retained record itself is absent")
      val missingrecord = SplitPhaseStartRecordRetention.retainC(null, storage)
      Then("the missing record is refused")
      missingrecord shouldBe a[Consequence.Failure[?]]
      When("the retention port is absent")
      val missingport = SplitPhaseStartRecordRetention.retainC(original, null)
      Then("the missing port is refused")
      missingport shouldBe a[Consequence.Failure[?]]
      When("managed registration has no owning call or dependencies")
      val registration = SplitPhaseManagedStartRecordRegistration.registerForCallC(original, null, null, null, null)
      Then("context-free registration is refused")
      registration shouldBe a[Consequence.Failure[?]]
    }
  }
}
