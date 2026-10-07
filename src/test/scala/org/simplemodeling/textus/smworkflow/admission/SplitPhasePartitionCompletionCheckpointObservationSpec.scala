package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePartitionCompletionCheckpointPreparation as A,
  SplitPhasePartitionCompletionCheckpointObservation as O, SplitPhasePlanningCheckpointObservation as R,
  SplitPhasePlanningCheckpointPreparation as K, SplitPhaseExplicitPartitionSemantic as S}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseAICompletionCheckpointFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhasePartitionCompletionCheckpointObservationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _original(saved: A.Record) = R.Original(saved.context, saved.start, saved.context.facts)
  private def _checkpoint(saved: A.Record) = saved.checkpoint.map(k => R.Data(K.index(k), k, _original(saved),
    k.snapshot.selection, R.builtDocuments(k.snapshot)))
  "Read-only complete AI-completion relations" should {
    "preserve original capture at completion and after legitimate later native history" in {
      Given("inert captured data and independently supplied raw facts, not native Retained witnesses")
      val values = Vector(capture().record, capture(S.Decision.DecisionNeeded).record)
      When("complete raw lineage is compared at revision three and with a later actual append")
      val observed = values.map { v =>
        val later = take(v.next.appendC(v.next.revision, v.next.history.last.copy(sequence = HistorySequence(4),
          revision = InstanceRevision(4), causation = CausationReference("later-native"))))
        take(O.verifyC(v, _original(v), _checkpoint(v), v.header, v.issued, v.completion, later))
      }
      Then("the comparison returns Unit only and does not substitute the earlier completion for current state")
      observed shouldBe Vector((), ())
      values.foreach(v => take(O.verifyC(v, _original(v), _checkpoint(v), v.header, v.issued, v.completion, v.next)) shouldBe ())
    }
    "refuse complete independent same-reference drift missing documents and altered original history" in {
      Given("one complete capture with Built candidate rows and native raw lineage")
      val v = capture().record
      val data = _checkpoint(v).get
      val changed = _original(v).copy(facts = v.context.facts.copy(planning = data.effective.planning))
      val history = v.next.copy(history = v.next.history.updated(0, v.next.history.head.copy(causation = CausationReference("foreign"))))
      When("one required complete row result or prefix differs")
      val results = Vector(
        O.verifyC(v, changed, Some(data), v.header, v.issued, v.completion, v.next),
        O.verifyC(v, _original(v), Some(data.copy(documents = Vector.empty)), v.header, v.issued, v.completion, v.next),
        O.verifyC(v, _original(v), None, v.header, v.issued, v.completion, v.next),
        O.verifyC(v, _original(v), Some(data), None, v.issued, v.completion, v.next),
        O.verifyC(v, _original(v), Some(data), v.header, v.issued, v.completion.copy(closingResult = None), v.next),
        O.verifyC(v, _original(v), Some(data), v.header, v.issued, v.completion, history))
      Then("raw reference equality cannot replace the original complete observation")
      results.foreach(_.isSuccess shouldBe false)
    }
  }
}
