package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import BoundedWorkRuntime.given
import StandaloneFixExecution as S
import StandaloneFixExecution.given
import GoalTaskValidationContinuation as C

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalTaskValidationContinuationSpec extends AnyWordSpec with GivenWhenThen with Matchers {
  private val input = S.Input("fixture", "legacy", "Preserve the original task", Vector("src"),
    Vector("fix-check", "test"), Vector("done"), Vector(ContextReference("source", "1")), true,
    task = Some(S.Task("direct-instruction", Vector.empty)))
  private val checks = Vector(GoalPhaseNativeValidation.Check("fix-check", 1, WorkflowSbtValidation.Kind.FixCheck),
    GoalPhaseNativeValidation.Check("test", 2, WorkflowSbtValidation.Kind.Test))
  private def receipt(check: GoalPhaseNativeValidation.Check) = RepositorySyncNativeMergeValidation.Receipt(
    check.identity, "exited", Some(0), "", "", Some(WorkflowSbtValidation.Facts(check.kind,
      WorkflowSbtValidation.Outcome.Succeeded, None, None)))
  private val initial = S.startC(input, "participant", checks).toOption.get.copy(stage = "Assess",
    candidate = Some(S.Candidate("a" * 40, "b" * 40, Vector("src/Example.scala"), "diff")),
    semanticReference = Some(ContextReference("issued-result", "1")), receipts = checks.map(receipt))
  private val payload = BoundedWorkRuntime.Payload(initial, "/fixture", "main", "a" * 40)
  private val target = C.Target("component", "session", "participant", 8, Json.obj("frontier" -> true.asJson), payload, C.missing(payload))

  "The common selected-validation target" should {
    "refresh a zero-test candidate without accepting its old check or changing the Task contract" in {
      Given("a GoalTask with a captured zero-test selected check and the original successful prefix")
      val original = SelectedValidationFixture.state
      val state = original.copy(receipts = original.receipts.dropRight(1) :+ SelectedValidationFixture.zeroReceipt())
      val payload = BoundedWorkRuntime.Payload(state, "/fixture", "main", state.candidate.get.head)
      val r = BoundedSelectedValidationContinuation
      val target = r.Target(r.schema, "component", "session", "participant", "GoalTask", 8,
        payload.baseHead, state.candidate.get.tree, "focused", state.input.ownedPaths, Vector.empty, true)
      val direction = C.Direction(target.asJson, r.refresh, "Correct the selected suite classification", "participant",
        ContextReference("specification-zero-tests-direction", "1"))
      When("an independently admitted direction selects the current owned candidate")
      val adopted = r.adoptedC(payload, target, direction).toOption.get
      Then("the old observation and contract remain while native Advance owns fresh candidate observation")
      adopted.state.stage shouldBe "SelectedValidationCandidateReady"
      adopted.state.receipts shouldBe state.receipts
      adopted.state.selected shouldBe state.selected
      adopted.state.input shouldBe state.input
      adopted.baseHead shouldBe payload.baseHead
      adopted.state.repairControl shouldBe state.repairControl
      adopted.state.ordinaryCount shouldBe state.ordinaryCount
      adopted.state.automaticCount shouldBe state.automaticCount
      adopted.validationContinuation shouldBe Some(direction)
      RepositorySyncNativeMergeValidation.productSuccessful(adopted.state.receipts.last, WorkflowSbtValidation.Kind.TestOnly) shouldBe false
    }

    "retain old receipts and distinguish unchanged and source-changing continuation" in {
      Given("a GoalTask Wait and independently selected fixture direction")
      val state = SelectedValidationFixture.state
      val old = BoundedWorkRuntime.Payload(state, "/fixture", "main", state.candidate.get.head)
      val r = BoundedSelectedValidationContinuation
      val target = r.Target(r.schema, "component", "session", "participant", "GoalTask", 8,
        old.baseHead, state.candidate.get.tree, "focused", state.input.ownedPaths, Vector.empty, false)
      val reference = ContextReference("specification-direction", "1")
      val direction = C.Direction(target.asJson, r.retry, "Correct selected automatic tests", "participant", reference)
      When("the same candidate is admitted and the original typed binding is read")
      val next = r.adoptedC(old, target, direction).toOption.get
      Then("only the affected active receipt is removed, with the old state left intact")
      next.state.receipts shouldBe old.state.receipts.dropRight(1)
      next.state.semanticReference shouldBe old.state.semanticReference
      next.state.input shouldBe old.state.input
      next.state.selected shouldBe old.state.selected
      next.state.repairControl shouldBe old.state.repairControl
      C.retainedC(next, "component", "session", 9).isSuccess shouldBe true
      val binding = r.bindingC(target, r.retry, "fixture", "participant", reference).toOption.get
      C.selectBinding(binding).toOption shouldBe Some(binding)
      val source = target.copy(additionalOwnedPaths = Vector("docs/contract.md"), refreshCandidate = true)
      val changed = r.adoptedC(old, source, direction.copy(target = source.asJson, choice = r.refresh)).toOption.get
      changed.state.stage shouldBe "SelectedValidationCandidateReady"
      changed.state.receipts shouldBe old.state.receipts
      changed.state.input.ownedPaths shouldBe state.input.ownedPaths :+ "docs/contract.md"
      changed.state.candidate shouldBe old.state.candidate
      changed.state.ordinaryCount shouldBe old.state.ordinaryCount
      changed.state.automaticCount shouldBe old.state.automaticCount
    }
  }

  "A legacy GoalTask validation continuation" should {
    "retain its successful prefix and derive only the missing typed checks" in {
      Given("a completed FixCheck and full Test")
      C.eligible(payload) shouldBe true
      When("the supplemental selection is derived")
      val extra = C.missing(payload)
      Then("the full Test remains Test and Compile and TestOnly are appended")
      extra.map(_.kind) shouldBe Vector(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Kind.TestOnly)
      extra.map(_.ordinal) shouldBe Vector(3, 4)
      val direction = C.Direction(target.asJson, "AuthorizeMissingChecks", "Human direction", "participant", ContextReference("human", "1"))
      val retained = payload.copy(state = initial.copy(stage = "Validate", selected = checks ++ extra), validationContinuation = Some(direction))
      C.retainedC(retained, "component", "session", 9).isSuccess shouldBe true
      retained.state.receipts shouldBe initial.receipts
      retained.state.input shouldBe initial.input
    }
    "reject crossed and ineligible frontiers" in {
      Given("the original completed batch")
      C.eligible(payload.copy(state = initial.copy(pendingCheck = Some("test")))) shouldBe false
      C.eligible(payload.copy(state = initial.copy(receipts = initial.receipts.dropRight(1)))) shouldBe false
      C.eligible(payload.copy(state = initial.copy(selected = checks.updated(1, checks.head)))) shouldBe false
      When("a retained direction is crossed")
      val direction = C.Direction(target.asJson, "AuthorizeMissingChecks", "Human direction", "participant", ContextReference("human", "1"))
      val retained = payload.copy(state = initial.copy(stage = "Validate", selected = checks ++ C.missing(payload)), validationContinuation = Some(direction))
      Then("foreign owner and stale revision refuse")
      C.retainedC(retained, "foreign", "session", 9).isSuccess shouldBe false
      C.retainedC(retained, "component", "session", 8).isSuccess shouldBe false
      C.retainedC(retained.copy(state = retained.state.copy(receipts = Vector.empty)), "component", "session", 9).isSuccess shouldBe false
    }
    "derive missing checks after a successful historical batch beyond sixteen entries" in {
      Given("seventeen distinct successful historical Test checks")
      val many = (1 to 17).toVector.map(i => GoalPhaseNativeValidation.Check("test-" + i, i, WorkflowSbtValidation.Kind.Test))
      val legacy = payload.copy(state = initial.copy(selected = many, receipts = many.map(receipt)))
      When("the continuation derives its missing checks")
      val extra = C.missing(legacy)
      Then("it preserves the historical batch and uses consecutive positive ordinals")
      C.eligible(legacy) shouldBe true
      extra.map(_.ordinal) shouldBe Vector(18, 19, 20)
      extra.map(_.kind) shouldBe Vector(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Kind.TestOnly, WorkflowSbtValidation.Kind.FixCheck)
    }
    "retain original worker lineage through a real modeled Repair completion" in {
      Given("a specification-owned original worker and an admitted supplemental direction")
      val original = payload.copy(implementationWorker = Some("implementer"), implementationWorkers = Some(Vector("implementer")))
      val originalTarget = C.Target("component", "session", "participant", 8, Json.obj("frontier" -> true.asJson), original, C.missing(original))
      val direction = C.Direction(originalTarget.asJson, "AuthorizeMissingChecks", "Human direction", "participant", ContextReference("human", "1"))
      val adopted = original.copy(state = original.state.copy(stage = "Validate", selected = original.state.selected ++ originalTarget.supplemental),
        validationContinuation = Some(direction))
      C.retainedC(adopted, "component", "session", 9).isSuccess shouldBe true
      When("the supplemental Compile fails and the normal model completes one bounded Repair")
      val intent = S.intentC(adopted.state).toOption.get
      val check = intent.selected(intent.receipts.size)
      val failed = RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(1), "fixture failed", "",
        Some(WorkflowSbtValidation.Facts(check.kind, WorkflowSbtValidation.Outcome.Failed, None, None)))
      val repair = S.observedC(intent, failed).toOption.get
      repair.stage shouldBe "Repair"
      val result = S.Result("completed", "Specification repair completed", Vector.empty,
        repair.input.conditions.map(id => S.Condition(id, true, "Fixture repair supplied")), Vector.empty)
      val next = S.completedC(S.Work("session", "10", "Repair", repair), result, ContextReference("repair-result", "1")).toOption.get
      // Native completion owner records the actual repair worker with distinct append semantics.
      val progressed = adopted.copy(state = next, implementationWorker = Some("repairer"),
        implementationWorkers = Some(Vector("implementer", "repairer")))
      Then("the original direction remains valid without claiming a successful check")
      C.retainedC(progressed, "component", "session", 11).isSuccess shouldBe true
      progressed.state.generation shouldBe original.state.generation + 1
      progressed.state.automaticCount shouldBe original.state.automaticCount + 1
      C.retainedC(progressed.copy(implementationWorkers = Some(Vector("repairer"))), "component", "session", 11).isSuccess shouldBe false
      C.retainedC(progressed.copy(implementationWorkers = Some(Vector("other", "repairer"))), "component", "session", 11).isSuccess shouldBe false
      C.retainedC(progressed.copy(implementationWorker = Some("foreign")), "component", "session", 11).isSuccess shouldBe false
      C.retainedC(progressed.copy(state = next.copy(repairControl = next.repairControl.copy(selectedLimit = 1))), "component", "session", 11).isSuccess shouldBe false
    }
    "preserve old payload wire absence and bind Defer without a checkpoint" in {
      Given("an old payload")
      payload.asJson.hcursor.downField("validationContinuation").focus shouldBe None
      When("a Human chooses Defer")
      val binding = C.bindingC("component", target, "Defer", "not now", "participant", ContextReference("human", "1")).toOption.get
      Then("the exact binding remains independently verifiable")
      C.selectBinding(binding).isRight shouldBe true
      payload.validationContinuation shouldBe None
    }
  }
}
