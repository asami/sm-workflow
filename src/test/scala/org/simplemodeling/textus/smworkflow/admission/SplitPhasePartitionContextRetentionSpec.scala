package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPartitionContextJsonV1 as J, SplitPhaseExplicitPartitionComputation as E}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhasePartitionContextFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhasePartitionContextRetentionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def prepared(value: C.Record = record) =
    take(C.prepareC(value, value.facts, instance, configuration, catalog))
  private def row(value: C.Record) = Record.data(J.storageField -> take(J.encodeC(value)))
  private final class Storage extends SplitPhasePartitionContextRetention.Port {
    var actual = Option.empty[Record]
    var creates = 0
    var created = false
    var fault = "none"
    def loadC(): Consequence[Option[Record]] = if (!created) Consequence.success(actual) else fault match {
      case "missing" => Consequence.success(None)
      case "failed" => Consequence.stateConflict("private readback interruption")
      case "divergent" => Consequence.success(Some(row(record.copy(owner = record.owner.copy(
        presentation = record.owner.presentation.copy(currentSituation = "other-observation"))))))
      case _ => Consequence.success(actual)
    }
    def createC(value: Record): Consequence[Unit] = {
      creates += 1
      if (fault == "before-create") Consequence.stateConflict("private failed Create")
      else {
        actual = Some(value)
        created = true
        if (fault == "after-create") Consequence.stateConflict("private uncertain Create response")
        else Consequence.unit
      }
    }
  }
  private def indeterminate(value: Consequence[C.Record]): Unit = value match {
    case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets.collect {
      case org.goldenport.observation.Descriptor.Facet.Reason(name) => name
    } should contain("transaction-indeterminate")
    case other => fail(s"Uncertain Create/readback published success: $other")
  }

  "Fixed-identity typed partition context retention" should {
    "create once and return only complete observed context on equal later calls" in {
      Given("a privately constructed semantic context, not issued work or storage authority")
      val storage = new Storage
      val admitted = prepared()
      When("the fixed record is retained then observed again")
      take(SplitPhasePartitionContextRetention.retainC(admitted, storage)) shouldBe record
      take(SplitPhasePartitionContextRetention.retainC(admitted, storage)) shouldBe record
      Then("no new record, reference, revision or history is manufactured")
      storage.creates shouldBe 1
      storage.actual shouldBe Some(row(record))
      admitted.record.owner.instanceRevision shouldBe instance.revision
    }

    "refuse full-data and selected-revision conflicts without overwriting the identity" in {
      Given("one observed complete record")
      val changedFacts = facts.copy(partition = facts.partition.copy(estimates = facts.partition.estimates.copy(
        items = facts.partition.estimates.items.updated(0, facts.partition.estimates.items.head.copy(duration = E.Duration.Known(261))))))
      val variants = Vector(record.copy(reference = record.reference.copy(revision = "2")),
        record.copy(owner = record.owner.copy(continuation = record.owner.continuation.copy(value = "other-continuation"))),
        record.copy(owner = record.owner.copy(presentation = record.owner.presentation.copy(currentSituation = "other-situation"))),
        record.copy(facts = changedFacts))
      When("independently constructed complete candidates reuse the same physical identity")
      Then("all conflicts fail without Create or replacement")
      variants.foreach { value =>
        val storage = new Storage
        storage.actual = Some(row(record))
        SplitPhasePartitionContextRetention.retainC(prepared(value), storage).isSuccess shouldBe false
        storage.creates shouldBe 0
        storage.actual shouldBe Some(row(record))
      }
    }

    "publish no success for uncertain Create or readback and recover committed data without retry machinery" in {
      Given("a fixed identity and private observation interruptions")
      for (fault <- Vector("before-create", "after-create", "missing", "failed", "divergent")) {
        val storage = new Storage
        storage.fault = fault
        When(s"the one Create or immediate observation is interrupted: $fault")
        indeterminate(SplitPhasePartitionContextRetention.retainC(prepared(), storage))
        storage.creates shouldBe 1
        Then("only a separately requested later observation can recover committed data")
        if (fault != "before-create") {
          storage.fault = "none"
          take(SplitPhasePartitionContextRetention.retainC(prepared(), storage)) shouldBe record
          storage.creates shouldBe 1
        } else storage.actual shouldBe None
      }
    }

    "refuse malformed existing payloads and absent dependencies without Create" in {
      Given("external retained data that cannot decode the exact complete context")
      val rows = Vector(Record.data("unrelated" -> "data"), Record.data(J.storageField -> "not-json"),
        row(record.copy(reference = record.reference.copy(identity = "foreign"))))
      When("retention observes those rows")
      Then("malformed state is not repaired or silently overwritten")
      rows.foreach { value =>
        val storage = new Storage
        storage.actual = Some(value)
        SplitPhasePartitionContextRetention.retainC(prepared(), storage).isSuccess shouldBe false
        storage.creates shouldBe 0
        storage.actual shouldBe Some(value)
      }
      SplitPhasePartitionContextRetention.retainC(null, new Storage).isSuccess shouldBe false
      SplitPhasePartitionContextRetention.retainC(prepared(), null).isSuccess shouldBe false
    }
  }
}
