package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPartitionContextJsonV1 as J}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhasePartitionContextFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct. 24, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class SplitPhasePartitionContextRetentionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private final class Storage extends SplitPhasePartitionContextRetention.Port {
    var actual = Option.empty[Record]
    var creates = 0
    def createC(value: Record): Consequence[Unit] = {
      creates += 1
      actual = Some(value)
      Consequence.unit
    }
  }

  "Normal typed partition context retention" should {
    "create the prepared semantic or planning context once without a readback" in {
      Given("independently constructed semantic and planning contexts with their own Create ports")
      val semantic = take(C.prepareC(record, facts, instance, configuration, catalog))
      val planning = take(C.preparePlanningC(record, facts, instance, configuration, catalog))
      val semanticStorage = new Storage
      val planningStorage = new Storage

      When("each prepared context is saved through its normal single Create")
      val semanticResult = take(SplitPhasePartitionContextRetention.retainC(semantic, semanticStorage))
      val planningResult = take(SplitPhasePartitionContextRetention.retainPlanningC(planning, planningStorage))

      Then("the prepared record is returned and its complete inert data is written once")
      semanticResult shouldBe semantic.record
      planningResult shouldBe planning.record
      Vector(semanticStorage, planningStorage).foreach { storage =>
        storage.creates shouldBe 1
        val raw = storage.actual.get.getString(J.storageField).get
        take(J.decodeC(record.reference, raw)) shouldBe record
      }
    }
  }
}
