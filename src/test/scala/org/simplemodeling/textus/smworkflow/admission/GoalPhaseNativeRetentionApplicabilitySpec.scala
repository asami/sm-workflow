package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseNativeCandidate as C
import GoalPhaseNativeValidation as V
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as S
import GoalPhaseNativeClosure as K
import GoalPhaseNativeRepositoryClosure as L
import RepositorySyncManagedFetch as Fetch
import RepositorySyncNativeConvergence as R
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeProgressStore as N
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartCommand, GoalPhaseStartInput, RepositorySyncStartCommand, RepositorySyncStartInput}
import SplitPhaseExplicitPartitionFixture.take

/** Native saved inputs are scoped facts, not complete host-declaration witnesses.
  * @since   Oct.  8, 2026
  * @version Oct.  8, 2026
  */
final class GoalPhaseNativeRetentionApplicabilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val candidate = O.Snapshot("a" * 40, "b" * 40)
  private val oldValues = Vector(None, Some(Json.Null), Some(Json.True), Some(Json.fromString("obsolete-witness")))
  private def payload(value: Json) = value.hcursor.downField("payload").focus.get
  private def replace(value: Json, nested: Json) = value.mapObject(_.add("payload", nested))
  private def legacy(value: Json, schema: String, field: String, item: Option[Json]) = {
    val nested = item.fold(payload(value))(v => payload(value).mapObject(_.add(field, v)))
    replace(value, nested).mapObject(_.add("schemaVersion", Json.fromString(schema)))
  }

  "Native candidate retained format" should {
    "omit host-path witnesses and ignore obsolete v1 fields without copying their values" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R1; Example: E1; an actual selected candidate payload")
      val saved = C.Payload("5", "S1", Vector("one.scala"), Vector("one.scala"),
        Some(candidate), Vector("one.scala"), Some("an actual retained diff"))
      val wire = C.codec.encode(saved)
      When("new output is written and old v1 rows carry absent or unusable host-path witnesses")
      payload(wire).asObject.get.keys.toSet should not contain "hostPaths"
      saved.productElementNames.toSet should not contain "hostPaths"
      oldValues.foreach(item => C.codec.decode(legacy(wire, "sm-workflow.goal-phase-native-candidate.v1", "hostPaths", item)) shouldBe Right(saved))
      Then("typed facts and declared shape are read directly, not certified by re-encoding equality")
      C.codec.decode(replace(wire, payload(wire).mapObject(_.remove("candidate").remove("diff")))) shouldBe
        Right(saved.copy(candidate = None, diff = None))
      Vector(payload(wire).mapObject(_.add("run", Json.fromString("git"))),
        payload(wire).mapObject(_.add("candidate", Json.obj("head" -> Json.fromString(candidate.head),
          "tree" -> Json.fromString(candidate.tree), "approve" -> Json.True))),
        payload(wire).mapObject(_.add("selectedPaths", Json.True)),
        payload(wire).mapObject(_.remove("step"))).foreach(item => C.codec.decode(replace(wire, item)).isLeft shouldBe true)
    }
  }

  "Native validation retained format" should {
    "omit complete policy witnesses and read v1 receipts without policy equality or canonical-body proof" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R2; Example: E2; actual selected check bindings and their result facts")
      val saved = V.Payload("8", "S1", candidate, Vector(V.Check("focused", 1)),
        Vector(RepositorySyncNativeMergeValidation.Receipt("focused", "exited", Some(0), "", "")), Some("passed"))
      val wire = V.codec.encode(saved)
      When("new output is written and old v1 rows carry absent or unusable policy witnesses")
      payload(wire).asObject.get.keys.toSet should not contain "policy"
      saved.productElementNames.toSet should not contain "policy"
      oldValues.foreach(item => V.codec.decode(legacy(wire, "sm-workflow.goal-phase-native-validation.v1", "policy", item)) shouldBe Right(saved))
      Then("only actual typed fields are decoded and no obsolete policy snapshot supplies authority")
      V.codec.decode(replace(wire, payload(wire).mapObject(_.remove("outcome")))) shouldBe Right(saved.copy(outcome = None))
      val check = payload(wire).hcursor.downField("selected").focus.get.asArray.get.head
      val receipt = payload(wire).hcursor.downField("receipts").focus.get.asArray.get.head
      Vector(payload(wire).mapObject(_.add("approve", Json.True)),
        payload(wire).mapObject(_.add("selected", Json.arr(check.mapObject(_.add("command", Json.fromString("git")))))),
        payload(wire).mapObject(_.add("receipts", Json.arr(receipt.mapObject(_.add("authority", Json.True))))),
        payload(wire).mapObject(_.add("candidate", Json.obj("head" -> Json.fromString(candidate.head),
          "tree" -> Json.fromString(candidate.tree), "approve" -> Json.True))),
        payload(wire).mapObject(_.remove("sourceRevision"))).foreach(item => V.codec.decode(replace(wire, item)).isLeft shouldBe true)
    }
  }

  private def workInput = {
    val plan = O.Plan("selected-plan", "1", "PHASE-2", "repository",
      Vector(O.Step("S1", "Selected semantics", Vector("one.scala"), Vector.empty, Vector("focused"))), Vector("whole-phase"))
    S.Request("owned-instance", "11", "ReviewStep", Some("S1"), 1, "Original review meaning", plan,
      O.Snapshot("c" * 40, "d" * 40), Vector.empty, Some(candidate), Some("saved-checks"), None,
      Some(GoalPhaseNativeReview.Context(Vector("one.scala"), "original diff",
        Vector(RepositorySyncNativeMergeValidation.Receipt("focused", "exited", Some(0), "", "")))))
  }
  "Native retained review context" should {
    "omit policy witnesses and consume original v1 review meaning without its obsolete markers" in {
      Given("Spec: docs/spec/native-host-review-applicability.md; Rule: R3; Example: E7; an actual complete assigned review input")
      val original = workInput
      val wire = S.inputCodec.encode(original)
      val body = payload(wire)
      val review = body.hcursor.downField("review").focus.get
      When("new work is written and old v1 inputs contain absent null or unusable policy metadata")
      review.asObject.get.keys.toSet shouldBe Set("changedPaths", "diff", "checks")
      original.review.get.productElementNames.toSet should not contain "policyRevision"
      Vector(None, Some(Json.Null), Some(Json.True), Some(Json.fromString("obsolete-policy"))).foreach { marker =>
        val old = marker.fold(review)(v => review.mapObject(_.add("policyIdentity", v).add("policyRevision", v)))
        S.inputCodec.decode(replace(wire, body.mapObject(_.add("review", old))).mapObject(_.add("schemaVersion",
          Json.fromString("sm-workflow.goal-phase-native-work-request.v1")))) shouldBe Right(original)
      }
      Then("current work cannot restore the removed witnesses, and actual missing or foreign review data still rejects")
      S.inputCodec.decode(replace(wire, body.mapObject(_.add("review", review.mapObject(_.add("policyRevision", Json.Null)))))).isLeft shouldBe true
      S.inputCodec.decode(replace(wire, body.mapObject(_.add("review", review.mapObject(_.remove("changedPaths")))))).isLeft shouldBe true
      S.inputCodec.decode(replace(wire, body.mapObject(_.add("review", review.mapObject(_.add("grant", Json.True)))))).isLeft shouldBe true
    }
  }
  private def order(input: S.Request) = {
    val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("GoalPhaseWorkflow"),
      WorkflowDefinitionRevision("goal-phase-ordered-run-v4"), InstanceIdentity(input.instanceIdentity))
    val request = ContinuationRequest(StateMachineRunIdentity("owned-run"), org.goldenport.cncf.workflow.ContinuationIdentity("owned-assignment"),
      StateMachineRevision("12"), StateMachineRequiredOperationIdentity("goal-phase-step-review-capability"),
      StateMachineOperationIdentity("GoalPhaseStepReviewService", "reviewGoalPhaseStep"),
      Some(TypedValue(S.inputCodec.typeIdentity, input)), Some(StateMachineResultTypeReference(S.resultCodec.typeIdentity)),
      ContextBundle("Original context", Vector.empty, Vector.empty, ContextSnapshot(handle.workflowRevision.value)),
      CompletionContract("owned-completion", Vector.empty), EvidenceContract("owned-evidence", Vector.empty),
      GoalPhaseNativeRun.completionOperation)
    WorkflowInteraction[S.Request, Nothing](handle, WorkflowContinuation.WorkOrder(request,
      ExecutionRequirement(Vector(CapabilityRequirement(request.requiredOperation.capability)), RiskLevel("standard"),
        ReasoningLevel.Standard, true), MinimalPresentation("Original title", "Original situation")))
  }
  "Native assigned work retention" should {
    "read declared legacy optional forms without a full re-encoded-body certificate" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R3; Example: E5; a v1 work payload")
      val original = workInput.copy(kind = "ImplementStep", candidate = None, checksReceipt = None, review = None)
      val wire = S.inputCodec.encode(original)
      val nested = payload(wire)
      val plan = nested.hcursor.downField("selectedPlan").focus.get
      val step = plan.hcursor.downField("steps").focus.get.asArray.get.head
      val legacy = nested.mapObject(_.add("review", Json.Null).add("decisionContext", Json.Null)
        .add("selectedPlan", plan.mapObject(_.add("phaseReviewClassification", Json.Null)
          .add("steps", Json.arr(step.mapObject(_.add("workClassification", Json.Null)))))))
      When("an old permitted optional-null representation is read, rather than compared with freshly generated JSON")
      S.inputCodec.decode(replace(wire, legacy)) shouldBe Right(original)
      Then("actual nested shape/type contradictions remain failures without any unchanged-value proof")
      Vector(legacy.mapObject(_.add("command", Json.fromString("git"))),
        legacy.mapObject(_.add("selectedPlan", plan.mapObject(_.add("approve", Json.True)))),
        legacy.mapObject(_.add("selectedPlan", plan.mapObject(_.add("steps", Json.arr(step.mapObject(_.add("run", Json.True))))))),
        legacy.mapObject(_.add("originalBase", Json.obj("head" -> Json.fromString(candidate.head),
          "tree" -> Json.fromString(candidate.tree), "authority" -> Json.True))),
        legacy.mapObject(_.remove("kind"))).foreach(item => S.inputCodec.decode(replace(wire, item)).isLeft shouldBe true)
      val result = S.Result("completed", "Actual semantic result", Vector.empty)
      val resultWire = S.resultCodec.encode(result)
      S.resultCodec.decode(resultWire) shouldBe Right(result)
      S.resultCodec.decode(replace(resultWire, payload(resultWire).mapObject(_.add("command", Json.True)))).isLeft shouldBe true
    }
    "resolve original saved meaning and presentation without complete WorkOrder or selected-Plan equality" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R3; Example: E6; an original issued assignment and a later projection")
      val original = workInput
      val saved = order(original)
      val projected = original.copy(purpose = "Another projection", selectedPlan = original.selectedPlan.copy(
        steps = original.selectedPlan.steps.map(_.copy(purpose = "Another presentation"))),
        review = original.review.map(_.copy(diff = "another diagnostic projection")))
      val expected = order(projected)
      When("the actual assignment references and executable route resolve to the original saved slot")
      take(S.resolveIssuedC(expected, saved)) shouldBe original
      val work = saved.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]]
      val diagnostic = saved.copy(current = work.copy(presentation = MinimalPresentation("Different title", "Different situation"),
        request = work.request.copy(context = work.request.context.copy(summary = "Different summary",
          snapshot = work.request.context.snapshot.copy(workspaceRevision = Some("historical"), evidenceRevision = Some("not-a-proof"))))))
      take(S.resolveIssuedC(expected, diagnostic)) shouldBe original
      Then("foreign assignment/target/route or missing independent-review requirement still rejects")
      val foreignTargets = Vector(original.copy(stepIdentity = Some("S2")), original.copy(generation = 2),
        original.copy(candidate = Some(candidate.copy(tree = "e" * 40))))
      val requests = Vector(work.request.copy(runId = StateMachineRunIdentity("foreign-run")),
        work.request.copy(continuationId = org.goldenport.cncf.workflow.ContinuationIdentity("foreign-work")),
        work.request.copy(expectedRevision = StateMachineRevision("999")),
        work.request.copy(completionOperation = StateMachineOperationIdentity("OtherService", "complete"))) ++
        foreignTargets.map(value => work.request.copy(input = Some(TypedValue(S.inputCodec.typeIdentity, value))))
      requests.foreach(request => S.resolveIssuedC(expected, saved.copy(current = work.copy(request = request))) shouldBe a[Consequence.Failure[?]])
      S.resolveIssuedC(expected, saved.copy(current = work.copy(requirement = work.requirement.copy(reviewRequired = false)))) shouldBe a[Consequence.Failure[?]]
      val selected = S.Result("accept", "Actual reviewed semantics", Vector.empty)
      val ref = ContextReference("review-result", "1")
      val submitted = ContinuationResult(saved.handle, work.request.runId, work.request.continuationId,
        work.request.expectedRevision, work.request.context.snapshot, TypedValue(S.resultCodec.typeIdentity, selected),
        ref, Vector.empty, ExecutionEvidence(Vector.empty))
      S.sameResultTarget(submitted, submitted.copy(result = submitted.result.copy(value = selected.copy(summary = "Diagnostic wording")))) shouldBe true
      S.sameResultTarget(submitted, submitted.copy(resultReference = ContextReference("foreign-result", "1"))) shouldBe false
    }
  }

  "Native Start retained format" should {
    "read permitted optional Plan forms without comparing the complete saved body with new output" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R4; Example: E9; an actual retained selected Start")
      val input = workInput
      val command = GoalPhaseStartCommand(GoalPhaseStartInput(input.selectedPlan.phase, input.selectedPlan.repository),
        "selected-start", "participant", "original-invocation")
      val commandJson = io.circe.parser.parse(GoalPhaseStartServiceOperation.encodeCommand(command)).toOption.get
      val saved = GoalPhaseNativeRun.Payload(commandJson, "/selected/repository", "main", input.selectedPlan, input.originalBase)
      val wire = GoalPhaseNativeRun.codec.encode(saved)
      val nested = payload(wire)
      val plan = nested.hcursor.downField("plan").focus.get
      val step = plan.hcursor.downField("steps").focus.get.asArray.get.head
      val oldPlan = plan.mapObject(_.remove("phaseReviewClassification").add("steps",
        Json.arr(step.mapObject(_.remove("workClassification")))))
      When("optional fields omitted by an older permitted representation are decoded")
      GoalPhaseNativeRun.codec.decode(replace(wire, nested.mapObject(_.add("plan", oldPlan)))) shouldBe Right(saved)
      Then("undeclared nested execution/authority fields and malformed actual data still reject")
      Vector(nested.mapObject(_.add("approval", Json.True)),
        nested.mapObject(_.add("plan", plan.mapObject(_.add("command", Json.fromString("git"))))),
        nested.mapObject(_.add("originalCommand", commandJson.mapObject(_.add("authority", Json.True)))),
        nested.mapObject(_.add("base", Json.obj("head" -> Json.fromString(candidate.head),
          "tree" -> Json.fromString(candidate.tree), "run" -> Json.True))),
        nested.mapObject(_.add("root", Json.True))).foreach(item =>
          GoalPhaseNativeRun.codec.decode(replace(wire, item)).isLeft shouldBe true)
    }
  }

  "Native Step closure retained format" should {
    "omit complete policy witnesses and read old rows without policy or canonical-body proof" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R5; Example: E10; actual commit intent/target and result facts")
      val work = workInput
      val input = K.Input(work.instanceIdentity, work.sourceRevision, work.selectedPlan.identity,
        work.selectedPlan.revision, "S1", 1, work.originalBase, candidate, "review-result")
      val saved = K.Payload(input, 1, "original-authority", 1, Some(O.Commit("e" * 40, work.originalBase.head, candidate.tree)))
      val wire = K.codec.encode(saved)
      When("new output is written and old v1 rows contain absent or unusable policy snapshots")
      payload(wire).asObject.get.keys.toSet should not contain "policy"
      saved.productElementNames.toSet should not contain "policy"
      oldValues.foreach(item => K.codec.decode(legacy(wire, "sm-workflow.goal-phase-native-step-closure.v1", "policy", item)) shouldBe Right(saved))
      Then("declared optional result forms and actual types are decoded without re-encoding an unchanged-body certificate")
      K.codec.decode(replace(wire, payload(wire).mapObject(_.remove("commit")))) shouldBe Right(saved.copy(commit = None))
      val target = payload(wire).hcursor.downField("input").focus.get
      val commit = payload(wire).hcursor.downField("commit").focus.get
      Vector(payload(wire).mapObject(_.add("command", Json.fromString("git"))),
        payload(wire).mapObject(_.add("input", target.mapObject(_.add("approval", Json.True)))),
        payload(wire).mapObject(_.add("commit", commit.mapObject(_.add("run", Json.True)))),
        payload(wire).mapObject(_.add("ordinal", Json.True)),
        payload(wire).mapObject(_.remove("authority"))).foreach(item => K.codec.decode(replace(wire, item)).isLeft shouldBe true)
    }
  }

  "Native parent-child closure retention" should {
    "read original child commands and legacy optional forms without regenerated-body certificates" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R6; Example: E13; saved parent intent and actual child result")
      val work = workInput
      val closed = O.ClosedStep("S1", O.Commit("e" * 40, work.originalBase.head, candidate.tree), "original-step-review")
      val input = L.Input(work.instanceIdentity, work.sourceRevision, work.selectedPlan,
        work.originalBase, Vector(closed), candidate, "phase-review", "owned-child-invocation")
      val command = RepositorySyncStartCommand(RepositorySyncStartInput("repository", "main", "origin", "main",
        "selected-endpoint", "synchronize", "old-policy"), "original-child-selection", "participant", input.childInvocation)
      val child = take(Fetch.identityC(command)).value
      val result = R.Result(child, "/selected/repository", "main", "origin", "main", "f" * 40, candidate.tree)
      val saved = L.Payload(input, command, child, "original-authority", 1, None, None)
      val wire = L.codec.encode(saved)
      val nested = payload(wire)
      val selected = nested.hcursor.downField("input").focus.get
      val plan = selected.hcursor.downField("plan").focus.get
      val step = plan.hcursor.downField("steps").focus.get.asArray.get.head
      val oldPlan = plan.mapObject(_.remove("phaseReviewClassification").add("steps",
        Json.arr(step.mapObject(_.add("workClassification", Json.Null)))))
      When("an original v1 row omits optional fields, rather than matching freshly regenerated output")
      L.codec.decode(replace(wire, nested.mapObject(_.remove("result").remove("childRevision")
        .add("input", selected.mapObject(_.add("plan", oldPlan)))))) shouldBe Right(saved)
      nested.hcursor.downField("childCommand").focus shouldBe Some(RepositorySyncStartServiceOperation.commandJson(command))
      val terminal = L.Result(work.selectedPlan, work.originalBase, Vector(closed), candidate, result, "21")
      val terminalWire = L.resultCodec.encode(terminal)
      L.resultCodec.decode(terminalWire.mapObject(_.add("plan", oldPlan))) shouldBe Right(terminal)
      Then("unknown nested authority, malformed actual commands/results and missing required targets still refuse")
      Vector(nested.mapObject(_.add("approve", Json.True)),
        nested.mapObject(_.add("input", selected.mapObject(_.add("command", Json.True)))),
        nested.mapObject(_.add("childCommand", RepositorySyncStartServiceOperation.commandJson(command)
          .mapObject(_.add("authority", Json.True)))),
        nested.mapObject(_.add("result", R.resultCodec.encode(result).mapObject(_.add("grant", Json.True)))),
        nested.mapObject(_.remove("childIdentity"))).foreach(v => L.codec.decode(replace(wire, v)).isLeft shouldBe true)
      L.resultCodec.decode(terminalWire.mapObject(_.add("repository", R.resultCodec.encode(result.copy(tip = "invalid"))))).isLeft shouldBe true

      When("the current authority describes the same actual candidate/chain but changes Plan presentation and Step review wording")
      val projected = input.copy(plan = input.plan.copy(steps = input.plan.steps.map(_.copy(purpose = "changed presentation",
        checks = Vector("another projection"))), phaseChecks = Vector("another presentation")),
        completed = input.completed.map(_.copy(review = "different review wording")))
      L.sameTarget(input, projected) shouldBe true
      Fetch.sameTarget(command.input, command.input.copy(repositoryPolicyVersion = "changed-metadata")) shouldBe true
      Then("actual native chain, selected invocation, target and execution scope conflicts are not waived")
      Vector(projected.copy(instance = "foreign-instance"), projected.copy(sourceRevision = "999"),
        projected.copy(childInvocation = "another-child"), projected.copy(review = "another-review-target"),
        projected.copy(candidate = candidate.copy(tree = "c" * 40)),
        projected.copy(plan = projected.plan.copy(repository = "foreign-repository")),
        projected.copy(completed = Vector(closed.copy(commit = closed.commit.copy(identity = "f" * 40)))))
        .foreach(v => L.sameTarget(input, v) shouldBe false)
      Vector(command.input.copy(remoteEndpointIdentity = "another-endpoint"), command.input.copy(syncMode = "another-mode"),
        command.input.copy(localBranch = "another-branch")).foreach(v => Fetch.sameTarget(command.input, v) shouldBe false)
    }
  }
}
