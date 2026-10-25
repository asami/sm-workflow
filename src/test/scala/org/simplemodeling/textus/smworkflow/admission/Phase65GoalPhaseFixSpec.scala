package org.simplemodeling.textus.smworkflow.admission

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeReleaseClosure as C
import GoalPhaseNativeValidation as V
import SplitPhaseExplicitPartitionFixture.take

/** Retained Final receipt boundaries, separate from actual CLI effect evidence.
  * @since   Oct. 24, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class Phase65GoalPhaseFixSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _oid(c: Char): String = c.toString * 40
  private val _parent = O.Snapshot(_oid('a'), _oid('b'))
  private val _candidate = O.Snapshot(_parent.head, _oid('c'))
  private val _plan = O.Plan("selected", "1", "6.5", "repository",
    Vector(O.Step("one", "Write input and its contract", Vector("value.txt", "docs"), Vector.empty, Vector("focused"))),
    Vector("focused"), 1)
  private val _input = GoalPhaseNativeRepositoryClosure.Input("session", "17", _plan, _parent,
    Vector.empty, _candidate, "scoped-review", "phase-closure-17")
  private val _intent = C.Payload(_input, _parent, 2)
  private val _execution = C.Execution("succeeded", "Exited(0)", Some(0), "", false, Vector("docs/usage.md"))
  private val _commit = O.Commit(_oid('d'), _parent.head, _candidate.tree)
  private def _validation_append(from: InstanceRecord, stage: String, cause: String): InstanceRecord =
    take(from.appendC(from.revision, GoalPhaseNativeRun.history(from, "phase." + stage, cause)))
  private def _failure_chain(stage: String): (InstanceRecord, V.Payload, Vector[(Option[V.Saved], Option[V.Saved])]) = {
    val initial = take(InstanceRecord(InstanceIdentity("release-failure-outcome"),
      take(GoalPhaseRuntimeCatalog.loadC()).definition, initialRevision, Lifecycle.NotStarted, None,
      CorrelationReference("release-failure-correlation"), CausationReference("specification-start"),
      None, None, None, Vector.empty).validateC)
    val frontier = _validation_append(initial, stage, "specification-validation-entry")
    val selected = Vector(V.Check("compile", 1, WorkflowSbtValidation.Kind.Compile), V.Check("test", 2))
    val payload = V.Payload(frontier.revision.value.toString, "phase:6.5", _candidate, selected, Vector.empty, None)
    val passed = RepositorySyncNativeMergeValidation.Receipt("compile", "exited", Some(0), "Specification-owned compiled output", "",
      Some(WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Outcome.Succeeded, None, None)))
    val failed = RepositorySyncNativeMergeValidation.Receipt("test", "exited", Some(7), "Specification-owned failed check output", "",
      Some(WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Test, WorkflowSbtValidation.Outcome.Failed, None, None)))
    val compileintent = V.Saved(_validation_append(frontier, stage, "ordered-validation:check-intent-0"), payload)
    val compileresult = V.Saved(_validation_append(compileintent.record, stage, "ordered-validation:check-result-0"),
      payload.copy(receipts = Vector(passed)))
    val testintent = V.Saved(_validation_append(compileresult.record, stage, "ordered-validation:check-intent-1"), compileresult.payload)
    val testresult = V.Saved(_validation_append(testintent.record, stage, "ordered-validation:check-result-1"),
      compileresult.payload.copy(receipts = Vector(passed, failed)))
    (frontier, payload, Vector((Some(compileintent), Some(compileresult)), (Some(testintent), Some(testresult))))
  }

  "P65-Q2 retained Final commit" should {
    "classify only a reconciled completed failed Phase check for missing outcome finalization" in {
      Given("specification-owned contiguous PhaseValidation intent and result pairs with a successful prefix and typed exited failure")
      val (frontier, payload, records) = _failure_chain("PhaseValidation")
      val (stepfrontier, steppayload, steprecords) = _failure_chain("Validation")
      When("the existing pure owner reconciles the actual-shaped completed and intent-only prefixes")
      val completed = take(V.reconcilePrefixC(frontier, payload, records, complete = false))
      val intentonly = take(V.reconcilePrefixC(frontier, payload, records.updated(1, (records(1)._1, None)), complete = false))
      val step = take(V.reconcilePrefixC(stepfrontier, steppayload, steprecords, complete = false))
      Then("only the completed Phase failure can settle its missing outcome and execution continuation stays forbidden")
      V.completedFailurePending(completed) shouldBe true
      V.continuable(completed) shouldBe false
      V.completedFailurePending(intentonly) shouldBe false
      V.completedFailurePending(step) shouldBe false

      Given("a final result whose instance identity crosses the actual preceding intent and result chain")
      val crossedresult = completed.copy(record = completed.record.copy(identity = InstanceIdentity("crossed-instance")))
      val crossedrecords = records.updated(1, (records(1)._1, Some(crossedresult)))
      When("the existing chain owner reconciles the crossed final result before mechanical classification")
      val crossed = V.reconcilePrefixC(frontier, payload, crossedrecords, complete = false)
      Then("the actual instance mismatch is refused by chain admission")
      crossed.isSuccess shouldBe false

      Given("altered outcome causation source suspension and non-failure terminal facts")
      val failed = completed.payload.receipts.last
      val success = failed.copy(exitCode = Some(0), sbt = Some(failed.sbt.get.copy(outcome = WorkflowSbtValidation.Outcome.Succeeded)))
      val timeout = failed.copy(termination = "timed-out", exitCode = None,
        sbt = Some(failed.sbt.get.copy(outcome = WorkflowSbtValidation.Outcome.Incomplete)))
      val notstarted = failed.copy(termination = "not-started", exitCode = None, stdout = "", stderr = "",
        sbt = Some(failed.sbt.get.copy(outcome = WorkflowSbtValidation.Outcome.NotExecuted)))
      val boundary = SuspensionBoundary(ContinuationIdentity("specification-pending"), completed.record.revision.next,
        ContextSnapshotReference("specification-context"), CompletionReference("specification-completion"),
        EvidenceReference("specification-evidence"), completed.record.correlation)
      val suspended = take(completed.record.appendC(completed.record.revision,
        GoalPhaseNativeRun.history(completed.record, "phase.PhaseValidation", "ordered-validation:check-result-1")
          .copy(suspension = Some(boundary))))
      val invalid = Vector[V.Saved](null, completed.copy(record = null), completed.copy(payload = null),
        completed.copy(record = records(1)._1.get.record),
        completed.copy(payload = completed.payload.copy(outcome = Some("failed"))),
        completed.copy(payload = completed.payload.copy(sourceRevision = "999")),
        completed.copy(payload = completed.payload.copy(sourceRevision = "0" + completed.payload.sourceRevision)),
        completed.copy(payload = completed.payload.copy(sourceRevision = initialRevision.value.toString)),
        completed.copy(record = suspended),
        completed.copy(payload = completed.payload.copy(receipts = completed.payload.receipts.dropRight(1) :+ success)),
        completed.copy(payload = completed.payload.copy(receipts = completed.payload.receipts.dropRight(1) :+ timeout)),
        completed.copy(payload = completed.payload.copy(receipts = completed.payload.receipts.dropRight(1) :+ timeout.copy(termination = "indeterminate"))),
        completed.copy(payload = completed.payload.copy(receipts = completed.payload.receipts.dropRight(1) :+ timeout.copy(termination = "launch-failed"))),
        completed.copy(payload = completed.payload.copy(receipts = completed.payload.receipts.dropRight(1), stopped = Vector(notstarted))))
      When("the mechanical classification sees each inadmissible settlement candidate")
      val refused = invalid.map(V.completedFailurePending)
      Then("no unknown incomplete not-started suspended crossed or already-finalized receipt gains outcome or dispatch eligibility")
      refused.zipWithIndex.foreach { (value, index) =>
        withClue(s"mechanical classification negative index=$index: ") { value shouldBe false }
      }
    }

    "permit fresh Release check repair while retaining the initial Phase failure Decision" in {
      Given("the generated PhaseValidation lifecycle and its distinct fresh Release repair event")
      When("the owning lifecycle selects repair, initial failure and an unrelated destination")
      val repair = domain.statemachine.GoalPhaseRunLifecycle.permits("PhaseValidation", "phase-checks-repair-required", "Repair")
      val initial = domain.statemachine.GoalPhaseRunLifecycle.permits("PhaseValidation", "phase-checks-failed", "Decision")
      val unrelated = domain.statemachine.GoalPhaseRunLifecycle.permits("PhaseValidation", "phase-checks-repair-required", "Closed")
      Then("only the exact repair and unchanged initial failure edges are permitted")
      repair shouldBe true
      initial shouldBe true
      unrelated shouldBe false
    }

    "retain distinct intent actual execution and committed facts" in {
      Given("an exact accepted release target and its fixed program")
      val values = Vector(_intent, _intent.copy(execution = Some(_execution)),
        _intent.copy(execution = Some(_execution), commit = Some(_commit)))
      When("each actual lifecycle receipt is encoded and read independently")
      val read = values.map(value => C.codec.decode(C.codec.encode(value)))
      Then("no receipt silently supplies a missing execution or commit")
      read shouldBe values.map(Right(_))
      _intent.execution shouldBe None
      _intent.commit shouldBe None
    }

    "reject false success foreign scope and commits without an actual successful result" in {
      Given("malformed or crossed result facts instead of the selected Final result")
      val invalid = Vector(_intent.copy(ordinal = 0),
        _intent.copy(execution = Some(_execution.copy(outcome = "unknown"))),
        _intent.copy(execution = Some(_execution.copy(exitCode = Some(1)))),
        _intent.copy(execution = Some(_execution.copy(selectedPaths = Vector("outside.txt")))),
        _intent.copy(commit = Some(_commit)),
        _intent.copy(execution = Some(_execution), commit = Some(_commit.copy(parent = _oid('e')))),
        _intent.copy(execution = Some(_execution), commit = Some(_commit.copy(tree = _oid('f')))))
      When("those receipts are read through the normal retained codec")
      val results = invalid.map(value => C.codec.decode(C.codec.encode(value)))
      Then("none can advance the accepted release target")
      results.foreach(_.isLeft shouldBe true)
    }
  }
}
