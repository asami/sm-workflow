package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseOrderedHumanDecision as Goal
import GoalPhaseNativeWork as Work
import GoalPhaseOrderedProgression as Ordered
import RepositorySyncHumanConflict as Repository
import RepositorySyncNativeConflictInput as Conflict
import SplitPhaseExplicitPartitionFixture.take

/** Original Human meaning is consumed; reference resolution is not a body seal.
  * @since   Oct.  8, 2026
  * @version Oct.  8, 2026
  */
final class NativeHumanQuestionApplicabilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val snapshot = Ordered.Snapshot("a" * 40, "b" * 40)
  private val plan = Ordered.Plan("plan", "1", "phase", "repository", Vector(
    Ordered.Step("step", "Original assignment", Vector("one.scala"), Vector.empty, Vector("focused"))), Vector("focused"))
  private val work = Work.Request("instance", "3", "ImplementStep", Some("step"), 0, "Original purpose", plan,
    snapshot, Vector.empty, None, None, None)
  private val goalInput = Goal.Input(work, ContextReference("requesting-result", "1"),
    Work.Result("decision-needed", "Original result explanation", Vector.empty), "Original question reason")
  private val conflict = Conflict.Request("instance", "3", "a" * 40, "b" * 40, Vector(
    Conflict.Conflict("conflict", "one.scala", Conflict.Blob("c" * 40, "base\n"), Conflict.Blob("d" * 40, "local\n"),
      Conflict.Blob("e" * 40, "remote\n"), worktree = "Original conflict contents\n")))
  private val repositoryInput = Repository.Input(conflict, "requesting-result", "1", "Original conflict rationale")

  private def question[W](workflow: String, input: W, inputType: String, resultType: String,
    completion: StateMachineOperationIdentity): WorkflowInteraction[W, Nothing] = {
    val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity(workflow),
      WorkflowDefinitionRevision("actual-executable"), InstanceIdentity("instance"))
    val request = ContinuationRequest(StateMachineRunIdentity("instance"), ContinuationIdentity("human-slot"),
      StateMachineRevision("4"), StateMachineRequiredOperationIdentity("human-capability"),
      StateMachineOperationIdentity("HumanService", "answer"), Some(TypedValue(inputType, input)),
      Some(StateMachineResultTypeReference(resultType)), ContextBundle("Original context", Vector.empty, Vector.empty,
        ContextSnapshot(handle.workflowRevision.value, modelRevision = Some("actual-model"))),
      CompletionContract("completion", Vector.empty), EvidenceContract("human-evidence", Vector.empty), completion)
    WorkflowInteraction[W, Nothing](handle, WorkflowContinuation.Decision(request, MinimalPresentation("Original title", "Original notice")))
  }
  private def decision[W](value: WorkflowInteraction[W, Nothing]) = value.current.asInstanceOf[WorkflowContinuation.Decision[W]]
  private def withInput[W](value: WorkflowInteraction[W, Nothing], input: W) = {
    val d = decision(value)
    value.copy(current = d.copy(request = d.request.copy(input = d.request.input.map(_.copy(value = input)))))
  }

  "Native Human question recovery" should {
    "consume the original ordered question without projected purpose Plan reason context or presentation equality" in {
      Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R1; Example: E1; a saved Goal question and its actual assignment")
      val saved = question("GoalPhaseWorkflow", goalInput, Goal.inputCodec.typeIdentity, Goal.answerCodec.typeIdentity, Goal.completionOperation)
      val d = decision(saved)
      val projected = withInput(saved, goalInput.copy(work = work.copy(purpose = "New explanation", selectedPlan = plan.copy(
        steps = plan.steps.map(_.copy(purpose = "New projected purpose")))), reason = "New reason",
        result = goalInput.result.copy(summary = "New projected summary")))
      val changed = projected.copy(current = decision(projected).copy(presentation = MinimalPresentation("New title", "New notice"),
        request = decision(projected).request.copy(context = d.request.context.copy(summary = "New context",
          snapshot = d.request.context.snapshot.copy(workspaceRevision = Some("New diagnostic workspace"), evidenceRevision = Some("New diagnostic evidence"))))))
      When("the executable target still resolves but freshly projected question bodies differ")
      val selected = take(Goal.selectIssuedC(changed, saved))
      Then("the original question supplies its meaning and presentation without being reissued")
      selected shouldBe saved
      decision(selected).request.input.get.value.reason shouldBe "Original question reason"
    }
    "refuse real ordered requesting-result assignment native-target route slot or model conflicts" in {
      Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R1; Example: E2; an actual ordered question target")
      val saved = question("GoalPhaseWorkflow", goalInput, Goal.inputCodec.typeIdentity, Goal.answerCodec.typeIdentity, Goal.completionOperation)
      val d = decision(saved)
      val crossed = Vector(withInput(saved, goalInput.copy(requestingResult = ContextReference("foreign-result", "1"))),
        withInput(saved, goalInput.copy(work = work.copy(stepIdentity = Some("other-step")))),
        withInput(saved, goalInput.copy(work = work.copy(originalBase = snapshot.copy(tree = "f" * 40)))),
        saved.copy(current = d.copy(request = d.request.copy(continuationId = ContinuationIdentity("other-slot")))),
        saved.copy(current = d.copy(request = d.request.copy(expectedRevision = StateMachineRevision("99")))),
        saved.copy(current = d.copy(request = d.request.copy(operation = StateMachineOperationIdentity("OtherService", "answer")))),
        saved.copy(current = d.copy(request = d.request.copy(context = d.request.context.copy(snapshot =
          d.request.context.snapshot.copy(modelRevision = Some("other-model")))))))
      When("actual selected work or executable scope is foreign")
      val results = crossed.map(Goal.selectIssuedC(saved, _))
      Then("no question is selected and no recovery grants completion or new effects")
      results.foreach(_.isSuccess shouldBe false)
    }
    "consume original repository conflict meaning instead of comparing full reconstructed contents and rationale" in {
      Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R1; Example: E3; an issued question for one native conflict")
      val saved = question("RepositorySyncWorkflow", repositoryInput, Repository.inputCodec.typeIdentity,
        Repository.answerCodec.typeIdentity, Repository.completionOperation)
      val projected = withInput(saved, repositoryInput.copy(rationale = "New explanation", conflict = conflict.copy(
        conflicts = conflict.conflicts.map(_.copy(worktree = "New diagnostic projection")))))
      When("original native tips revision and requesting result select the saved question")
      val selected = take(Repository.selectIssuedC(projected, saved))
      Then("materialization receives original conflict input, not its reconstructed replacement")
      selected shouldBe saved
      decision(selected).request.input.get.value.conflict.conflicts.head.worktree shouldBe "Original conflict contents\n"
    }
    "refuse foreign repository conflict frontier tips requesting result or completion route" in {
      Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R1; Example: E4; an actual native conflict question")
      val saved = question("RepositorySyncWorkflow", repositoryInput, Repository.inputCodec.typeIdentity,
        Repository.answerCodec.typeIdentity, Repository.completionOperation)
      val d = decision(saved)
      val crossed = Vector(withInput(saved, repositoryInput.copy(conflict = conflict.copy(conflictRevision = "99"))),
        withInput(saved, repositoryInput.copy(conflict = conflict.copy(remoteTip = "f" * 40))),
        withInput(saved, repositoryInput.copy(requestingResultRevision = "99")),
        saved.copy(current = d.copy(request = d.request.copy(completionOperation = StateMachineOperationIdentity("OtherService", "complete")))),
        saved.copy(current = d.copy(request = d.request.copy(input = Some(TypedValue("ForeignInput", repositoryInput))))))
      When("actual resolution target or executable contract differs")
      val results = crossed.map(Repository.selectIssuedC(saved, _))
      Then("no whole-body witness is needed to reject the real incompatibility")
      results.foreach(_.isSuccess shouldBe false)
    }
  }

  "Repository conflict payload admission" should {
    "read original v1 optional defaults directly rather than require today's full re-encoded body" in {
      Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R3; Example: E5; original native conflict and Human input formats")
      val original = conflict.copy(conflicts = conflict.conflicts.map(_.copy(mode = "100644", worktree = "")))
      val wire = RepositorySyncConflictSemantic.inputCodec.encode(original)
      def oldRequest(value: Json) = value.mapObject(root => root.add("conflicts", root("conflicts").get.mapArray(
        _.map(_.mapObject(_.remove("mode").remove("worktree"))))))
      val legacy = wire.mapObject(root => root.add("payload", oldRequest(root("payload").get)))
      val human = repositoryInput.copy(conflict = original)
      val humanWire = Repository.inputCodec.encode(human)
      val oldHuman = humanWire.mapObject(root => root.add("payload", root("payload").get.mapObject(fields =>
        fields.add("conflict", oldRequest(fields("conflict").get)))))
      When("saved optional mode/worktree defaults are absent rather than copied from a fresh encoding")
      val decoded = RepositorySyncConflictSemantic.inputCodec.decode(legacy)
      val decodedHuman = Repository.inputCodec.decode(oldHuman)
      Then("original declared defaults read without storage rewrite or renewed authority")
      decoded shouldBe Right(original)
      decodedHuman shouldBe Right(human)
      val current = Repository.inputCodec.encode(human)
      Repository.inputCodec.decode(current) shouldBe Right(human)
    }
    "reject undeclared nested execution fields missing actual fields and wrong types without re-encoding proof" in {
      Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R3; Example: E6; actual closed Human and conflict payloads")
      val wire = Repository.inputCodec.encode(repositoryInput)
      val body = wire.hcursor.downField("payload").focus.get
      val target = body.hcursor.downField("conflict").focus.get
      val item = target.hcursor.downField("conflicts").focus.get.asArray.get.head
      def withConflictItem(value: Json): Json = body.mapObject(_.add("conflict",
        target.mapObject(_.add("conflicts", Json.arr(value)))))
      val foreignBlob = item.hcursor.downField("base").focus.get.mapObject(_.add("grant", Json.True))
      val malformed = Vector(body.mapObject(_.add("authority", Json.True)), body.mapObject(_.remove("requestingResultIdentity")),
        withConflictItem(item.mapObject(_.add("command", Json.True))),
        withConflictItem(item.mapObject(_.add("mode", Json.True))), withConflictItem(item.mapObject(_.add("base", foreignBlob))))
      When("input adds a hidden command/grant or contradicts a required typed field")
      val decoded = malformed.map(v => Repository.inputCodec.decode(wire.mapObject(_.add("payload", v))))
      Then("declared structure and typed admission reject it, not a canonical-body comparison")
      decoded.foreach(_.isLeft shouldBe true)
      val answer = Repository.Answer("ProvideResolution", Vector(RepositorySyncConflictSemantic.Resolution("conflict", "actual\n")), "Meaningful direction")
      val reply = Repository.answerCodec.encode(answer)
      Repository.answerCodec.decode(reply) shouldBe Right(answer)
      val altered = reply.mapObject(root => root.add("payload", root("payload").get.mapObject(fields => fields.add("resolutions",
        fields("resolutions").get.mapArray(_.map(_.mapObject(_.add("path", Json.fromString("outside")))))))))
      Repository.answerCodec.decode(altered).isLeft shouldBe true
      val semantic = RepositorySyncConflictSemantic.resultCodec.encode(Repository.proposal(answer))
      RepositorySyncConflictSemantic.resultCodec.decode(semantic).isRight shouldBe true
      RepositorySyncConflictSemantic.resultCodec.decode(semantic.mapObject(root => root.add("payload",
        root("payload").get.mapObject(_.add("run", Json.True))))).isLeft shouldBe true
    }
  }
}
