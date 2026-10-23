package org.simplemodeling.textus.smworkflow.admission

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeReleaseClosure as C

/** Retained Final receipt boundaries, separate from actual CLI effect evidence.
  * @since   Oct. 24, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class Phase65GoalPhaseFixSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def oid(c: Char): String = c.toString * 40
  private val parent = O.Snapshot(oid('a'), oid('b'))
  private val candidate = O.Snapshot(parent.head, oid('c'))
  private val plan = O.Plan("selected", "1", "6.5", "repository",
    Vector(O.Step("one", "Write input and its contract", Vector("value.txt", "docs"), Vector.empty, Vector("focused"))),
    Vector("focused"), 1)
  private val input = GoalPhaseNativeRepositoryClosure.Input("session", "17", plan, parent,
    Vector.empty, candidate, "scoped-review", "phase-closure-17")
  private val intent = C.Payload(input, parent, 2)
  private val execution = C.Execution("succeeded", "Exited(0)", Some(0), "", false, Vector("docs/usage.md"))
  private val commit = O.Commit(oid('d'), parent.head, candidate.tree)

  "P65-Q2 retained Final commit" should {
    "retain distinct intent actual execution and committed facts" in {
      Given("an exact accepted release target and its fixed program")
      val values = Vector(intent, intent.copy(execution = Some(execution)),
        intent.copy(execution = Some(execution), commit = Some(commit)))
      When("each actual lifecycle receipt is encoded and read independently")
      val read = values.map(value => C.codec.decode(C.codec.encode(value)))
      Then("no receipt silently supplies a missing execution or commit")
      read shouldBe values.map(Right(_))
      intent.execution shouldBe None
      intent.commit shouldBe None
    }

    "reject false success foreign scope and commits without an actual successful result" in {
      Given("malformed or crossed result facts instead of the selected Final result")
      val invalid = Vector(intent.copy(ordinal = 0),
        intent.copy(execution = Some(execution.copy(outcome = "unknown"))),
        intent.copy(execution = Some(execution.copy(exitCode = Some(1)))),
        intent.copy(execution = Some(execution.copy(selectedPaths = Vector("outside.txt")))),
        intent.copy(commit = Some(commit)),
        intent.copy(execution = Some(execution), commit = Some(commit.copy(parent = oid('e')))),
        intent.copy(execution = Some(execution), commit = Some(commit.copy(tree = oid('f')))))
      When("those receipts are read through the normal retained codec")
      val results = invalid.map(value => C.codec.decode(C.codec.encode(value)))
      Then("none can advance the accepted release target")
      results.foreach(_.isLeft shouldBe true)
    }
  }
}
