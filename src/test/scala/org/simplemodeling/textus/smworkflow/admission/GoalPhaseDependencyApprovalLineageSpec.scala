/**
 * @since   Nov.  6, 2026
 * @version Nov.  8, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseDependencyApprovalLineage as L
import GoalPhaseDependencyScope as D
import GoalPhaseDependencyValidation as V
import GoalPhaseNativeWork as W
import SplitPhaseExplicitPartitionFixture.take

/** All answers and identities here are specification-owned fixtures. */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseDependencyApprovalLineageSpec extends AnyWordSpec with Matchers {
  private val fixture = DependencyValidationSpecificationFixture
  private val purpose = ContextReference("fixture-purpose", "1")
  private val boundary = D.AuthorizationBoundary(D.authorizationSchema, purpose,
    Vector("edit-source", "validate"), Vector.empty)
  private val parent = fixture.approvals.head.copy(proposal = fixture.approvals.head.proposal.copy(
    authorizationBoundary = Some(boundary)))
  private def context(a: V.Approval): W.DecisionContext = W.DecisionContext(
    W.Result("decision-needed", "Fixture question", Vector.empty, dependencyProposal = Some(a.proposal)),
    "ApproveDependencyScope", "Fixture answer", Some(a.decision))
  private val first = context(parent)
  private val firstKey = L.Key("session", ContextReference("plan", "1"), parent.step, "human-1", 10)
  private val nextKey = firstKey.copy(continuation = "human-2", revision = 20)
  private def child(ref: ContextReference = parent.decision): V.Approval = parent.copy(
    proposal = parent.proposal.copy(revision = "2", paths = parent.proposal.paths :+ "src/New.scala",
      authorizationBoundary = Some(boundary.copy(permittedOperations = Vector("edit-source"))),
      scopeUpdate = Some(D.ScopeUpdate(parent.decision, "Fixture adoption retains the necessary files"))), decision = ref)
  private def initial = take(L.appendC(L.State(), firstKey, parent, first, None))
  private def append(state: L.State, a: V.Approval, k: L.Key = nextKey,
    direction: Option[W.DecisionContext] = Some(first)) = L.appendC(state, k, a, context(a), direction)

  "Native dependency approval lineage" should {
    "retain both fixture completions and project only a proved successor for either aliased or distinct evidence" in {
      Vector(parent.decision, ContextReference("other-fixture-answer", "1")).foreach { ref =>
        val before = initial
        val successor = child(ref)
        val after = take(append(before, successor))
        before.approvals shouldBe Vector(parent)
        after.approvals shouldBe Vector(successor)
        after.entries.map(_.original) shouldBe Vector(parent, successor)
        after.entries.map(_.key) shouldBe Vector(firstKey, nextKey)
        after.entries.head.context shouldBe first
      }
    }
    "prove legacy boundary adoption from the prior proposal identity without rewriting that proposal" in {
      val legacy = parent.copy(proposal = parent.proposal.copy(authorizationBoundary = None))
      val legacyContext = context(legacy)
      val before = take(L.appendC(L.State(), firstKey, legacy, legacyContext, None))
      val adoptionBoundary = boundary.copy(purpose = ContextReference(legacy.proposal.identity, legacy.proposal.revision))
      val adoption = legacy.copy(proposal = legacy.proposal.copy(revision = "2",
        paths = legacy.proposal.paths :+ "src/New.scala", authorizationBoundary = Some(adoptionBoundary),
        scopeUpdate = Some(D.ScopeUpdate(legacy.decision, "Fixture explicit legacy boundary adoption"))))
      val after = take(L.appendC(before, nextKey, adoption, context(adoption), Some(legacyContext)))
      after.approvals shouldBe Vector(adoption)
      after.entries.head.original shouldBe legacy
      after.entries.head.context shouldBe legacyContext
      after.entries.head.original.proposal.authorizationBoundary shouldBe None
      val unrelated = adoption.copy(proposal = adoption.proposal.copy(authorizationBoundary = Some(
        adoptionBoundary.copy(purpose = ContextReference("unrelated", "1")))))
      L.appendC(before, nextKey, unrelated, context(unrelated), Some(legacyContext)).isSuccess shouldBe false
      val independent = unrelated.copy(decision = ContextReference("independent-fixture-answer", "1"))
      val separate = take(L.appendC(before, nextKey, independent, context(independent), Some(legacyContext)))
      separate.approvals shouldBe Vector(legacy, independent)
    }
    "reject mixed sessions, nonmonotonic history and null native metadata" in {
      val ordinary = parent.copy(proposal = parent.proposal.copy(identity = "other-fixture"))
      L.appendC(initial, nextKey.copy(session = "other-session"), ordinary, context(ordinary), None).isSuccess shouldBe false
      L.appendC(initial, nextKey.copy(revision = 9), ordinary, context(ordinary), None).isSuccess shouldBe false
      L.appendC(initial, nextKey.copy(revision = firstKey.revision), ordinary, context(ordinary), None).isSuccess shouldBe false
      L.appendC(L.State(Vector(null)), nextKey, ordinary, context(ordinary), None).isSuccess shouldBe false
      val malformed = L.Entry(null, parent, first, parent, first, active = true)
      L.appendC(L.State(Vector(malformed)), nextKey, ordinary, context(ordinary), None).isSuccess shouldBe false
      L.appendC(initial, null, ordinary, context(ordinary), None).isSuccess shouldBe false
      L.appendC(initial, nextKey, ordinary, null, None).isSuccess shouldBe false
    }
    "retain the earlier admission while admitting only the successor at the later prefix" in {
      val before = initial
      val oldBinding = fixture.binding.copy(paths = parent.proposal.paths)
      val oldSelection = V.Selection(ContextReference(oldBinding.identity, "1"), Vector(
        V.ApprovalReference(ContextReference(parent.proposal.identity, parent.proposal.revision), parent.decision)))
      val historical = take(V.admitC(oldSelection, oldBinding, before.approvals, fixture.request))
      val successor = child()
      val after = take(append(before, successor))
      val currentBinding = oldBinding.copy(revision = 2, paths = successor.proposal.paths)
      val currentSelection = V.Selection(ContextReference(currentBinding.identity, "2"), Vector(
        V.ApprovalReference(ContextReference(successor.proposal.identity, successor.proposal.revision), successor.decision)))
      val current = take(V.admitC(currentSelection, currentBinding, after.approvals, fixture.request))
      historical.approvals shouldBe Vector(parent)
      historical.binding shouldBe oldBinding
      current.approvals shouldBe Vector(successor)
      V.admitC(oldSelection, oldBinding, after.approvals, fixture.request).isSuccess shouldBe false
    }
    "refuse crossed or unsupported ancestry and duplicate native identities" in {
      val successor = child()
      val crossed = Vector(
        nextKey.copy(session = "foreign"), nextKey.copy(plan = ContextReference("plan", "2")),
        nextKey.copy(step = "other"), nextKey.copy(revision = 9), firstKey,
        nextKey.copy(continuation = firstKey.continuation))
      crossed.foreach(k => append(initial, successor, k).isSuccess shouldBe false)
      append(initial, successor, direction = None).isSuccess shouldBe false
      append(initial, successor, direction = Some(first.copy(choice = "RejectDependencyScope"))).isSuccess shouldBe false
      val p = successor.proposal
      Vector(
        p.copy(requester = p.requester.copy(workspace = "foreign")),
        p.copy(dependency = p.dependency.copy(repository = "foreign")),
        p.copy(usageContract = ContextReference("foreign", "1")),
        p.copy(acceptance = Vector("foreign")),
        p.copy(dependentWork = Vector(ContextReference("foreign", "1"))),
        p.copy(paths = Vector("src/New.scala")),
        p.copy(authorizationBoundary = Some(boundary.copy(purpose = ContextReference("foreign", "1"))))
      ).foreach(proposal => append(initial, successor.copy(proposal = proposal)).isSuccess shouldBe false)
      val noParent = successor.copy(proposal = p.copy(scopeUpdate = Some(D.ScopeUpdate(
        ContextReference("missing", "1"), "Fixture missing parent"))))
      append(initial, noParent).isSuccess shouldBe false
      val duplicate = take(L.appendC(initial, nextKey, parent, first, None))
      append(duplicate, successor, nextKey.copy(continuation = "human-3", revision = 30), Some(first)).isSuccess shouldBe false
    }
    "retain a separately answered different purpose independently and refuse an aliased crossed purpose" in {
      val different = child(ContextReference("different-fixture-answer", "1"))
      val crossed = different.copy(proposal = different.proposal.copy(authorizationBoundary = Some(
        boundary.copy(purpose = ContextReference("other-fixture-purpose", "1")))))
      val state = take(append(initial, crossed))
      state.approvals shouldBe Vector(parent, crossed)
      state.entries.forall(_.active) shouldBe true
      append(initial, crossed.copy(decision = parent.decision)).isSuccess shouldBe false
    }
    "retain unlinked duplicate evidence so ordinary admission uniqueness still refuses it" in {
      val other = parent.copy(proposal = parent.proposal.copy(identity = "other-fixture"))
      val state = take(L.appendC(initial, nextKey, other, context(other), None))
      state.approvals shouldBe Vector(parent, other)
      V.admissionValid(V.Admission(fixture.binding.copy(paths = parent.proposal.paths), state.approvals)) shouldBe false
    }
  }
}
