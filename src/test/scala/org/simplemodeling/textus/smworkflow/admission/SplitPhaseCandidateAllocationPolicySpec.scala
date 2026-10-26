package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseCandidateAllocationPolicy as H,
  SplitPhaseExplicitPlanningAdvance as W, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitObjectiveSelection as O}
import SplitPhaseExplicitPartitionFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct. 24, 2026
 */
private[admission] object SplitPhaseCandidateAllocationFixture {
  val values = Map[String, ConfigurationValue](
    H.prefix + "policy-identity" -> ConfigurationValue.StringValue("candidate-policy"),
    H.prefix + "policy-revision" -> ConfigurationValue.StringValue("1"),
    H.prefix + "namespace" -> ConfigurationValue.StringValue("test-candidates"))
  def host(values: Map[String, ConfigurationValue]): ResolvedConfiguration =
    ResolvedConfiguration(HostConfiguration(values), ConfigurationTrace.empty)
  val policy = take(H.decodeC(host(values)))
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.AdmissionTestDeclaration])
final class SplitPhaseCandidateAllocationPolicySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import SplitPhaseCandidateAllocationFixture.*
  private val _result = S.ResultReference("canonical-result", "1")
  private def _need(objectives: O.Records = O.Records(None, None, None)): W.AllocationRequired = {
    val source = base(Vector(260, 180, 260))
    val input = take(E.loadC(source.initial, source.records))
    val data = W.Data(W.DataReference("original-planning", "1"), source.source.reference, source.source.authority, None, objectives)
    take(W.advanceC(take(W.selectC(input, data)))).asInstanceOf[W.AllocationRequired]
  }

  "Explicit candidate allocation policy" should {
    "allocate the complete canonical candidate set deterministically without selecting a winner" in {
      Given("a constructed missing-allocation need and an explicitly configured host policy")
      val need = _need()
      When("the host policy allocates after the canonical result reference is selected")
      val allocated = take(policy.allocateC(need, _result))
      val prefix = "sm-split:test-candidates:policy:candidate-policy@1:result:canonical-result@1"
      Then("all references are explicit, stable, distinct and cover the original ordered cut vectors")
      allocated.need shouldBe need
      allocated.projection.enumerated shouldBe need.enumerated
      allocated.data shouldBe take(policy.allocateC(need, _result)).data
      allocated.data.reference shouldBe W.DataReference(prefix + ":planning", "1")
      allocated.data.source shouldBe need.selection.data.source
      allocated.data.authority shouldBe need.selection.data.authority
      allocated.data.objectives shouldBe need.selection.data.objectives
      val allocations = allocated.data.allocations.get
      allocations.reference.identity shouldBe prefix + ":allocation"
      allocations.allocations.map(_.cutAfterIndices) shouldBe need.enumerated.candidates.map(_.cutAfterIndices)
      allocations.allocations.zipWithIndex.foreach { (value, index) =>
        value.proposal.identity shouldBe prefix + ":candidate:" + index.toString + ":proposal"
        value.ownership.identity shouldBe prefix + ":candidate:" + index.toString + ":ownership"
        value.handoffs.identity shouldBe prefix + ":candidate:" + index.toString + ":handoffs"
        value.proposal.revision shouldBe "1"
      }
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateAllocationPolicy.Allocated(null, null, null, null)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateAllocationPolicy.Policy(null, null, null)")
    }

    "escape qualified reference values injectively and change references only for changed selected policy or result" in {
      Given("reserved characters, percent signs, Unicode and spaces in canonical selected reference values")
      val need = _need()
      val selected = S.ResultReference("result:/@é %", "r:1")
      When("allocation builds qualified references rather than using unescaped concatenation or a digest")
      val allocated = take(policy.allocateC(need, selected))
      Then("the exact selected values have canonical UTF-8 percent encoding")
      allocated.data.reference.identity should include ("result:result%3A%2F%40%C3%A9%20%25@r%3A1")
      take(policy.allocateC(need, selected.copy(identity = "result%3A/@é %"))).data.reference should not be allocated.data.reference
      take(policy.allocateC(need, selected.copy(revision = "r:2"))).data.reference should not be allocated.data.reference
      val changed = take(H.decodeC(host(values.updated(H.prefix + "policy-revision", ConfigurationValue.StringValue("2")))))
      take(changed.allocateC(need, selected)).data.reference should not be allocated.data.reference
    }

    "recover the exact stored whole allocation without consulting or regenerating current policy" in {
      Given("a complete allocation dataset from the original policy")
      val need = _need()
      val allocated = take(policy.allocateC(need, _result))
      val changed = take(H.decodeC(host(values.updated(H.prefix + "namespace", ConfigurationValue.StringValue("changed-namespace")))))
      When("current host policy differs but recovery observes the original stored dataset")
      val recovered = take(H.recoverC(need, _result, allocated.data))
      Then("the original references and enumeration remain unchanged without invoking allocateC")
      recovered.data shouldBe allocated.data
      recovered.projection.enumerated shouldBe need.enumerated
      take(changed.allocateC(need, _result)).data.reference should not be recovered.data.reference
      H.recoverC(need, _result, allocated.data.copy(source = allocated.data.source.copy(revision = "foreign"))).isSuccess shouldBe false
      H.recoverC(need, _result, allocated.data.copy(authority = allocated.data.authority.copy(revision = "foreign"))).isSuccess shouldBe false
      H.recoverC(need, _result, allocated.data.copy(objectives = O.Records(None, None,
        Some(O.OverheadPolicy(O.OverheadPolicyReference("changed", "1"), allocated.data.source, allocated.data.authority, 1, 0, 0, 0, 0)))))
        .isSuccess shouldBe false
      H.recoverC(need, _result, allocated.data.copy(reference = need.selection.data.reference)).isSuccess shouldBe false
      H.recoverC(need, _result, allocated.data.copy(allocations = None)).isSuccess shouldBe false
      val incomplete = allocated.data.allocations.get.copy(allocations = allocated.data.allocations.get.allocations.dropRight(1))
      H.recoverC(need, _result, allocated.data.copy(allocations = Some(incomplete))).isSuccess shouldBe false
    }

    "reject absent malformed unbounded or unconfigured allocation before any publication" in {
      Given("allocation is neither an AI choice nor a default host convention")
      val need = _need()
      When("required policy or selected data violates the frozen bounds")
      Then("the whole allocation fails rather than trimming, renumbering or falling back")
      Vector("policy-identity", "policy-revision", "namespace").foreach { key =>
        H.decodeC(host(values.removed(H.prefix + key))).isSuccess shouldBe false
      }
      Vector("", " white ", "unsafe/namespace", "1not-canonical", "é").foreach { value =>
        H.decodeC(host(values.updated(H.prefix + "namespace", ConfigurationValue.StringValue(value)))).isSuccess shouldBe false
      }
      H.decodeC(host(values.updated(H.prefix + "policy-identity", ConfigurationValue.StringValue("x" * 129)))).isSuccess shouldBe false
      H.decodeC(null).isSuccess shouldBe false
      policy.allocateC(null, _result).isSuccess shouldBe false
      policy.allocateC(need, null).isSuccess shouldBe false
      policy.allocateC(need, _result.copy(identity = "\n")).isSuccess shouldBe false
      policy.allocateC(need, _result.copy(identity = "é" * 200)).isSuccess shouldBe false
      val source = base(Vector(260, 180, 260))
      val oversized = O.Records(None, Some(O.DemandRecord(O.DemandReference("demand", "1"), source.source.reference,
        source.source.authority, source.inventory.orderedUnfinishedItems.map(id => O.ItemDemand(id,
          O.Demand.Unresolved("あ" * SplitPhaseExplicitPlanningInputJsonV1.maximumPayloadBytes))))), None)
      policy.allocateC(_need(oversized), _result).isSuccess shouldBe false
    }
  }
}
