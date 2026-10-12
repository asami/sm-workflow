package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.time.Instant
import org.goldenport.cncf.component.ComponentInstanceId
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import NativeHostEvidence.*
import GoalPhaseOrderedHumanDecision as Goal
import GoalPhaseNativeWork as Work
import GoalPhaseOrderedProgression as Ordered
import RepositorySyncHumanConflict as Repository
import RepositorySyncNativeConflictInput as Conflict
import SplitPhaseExplicitPartitionFixture.take

/** Passive typed/wire fixtures only; no enrolled Human or model execution. */
private[admission] object NativeHostHumanEvidenceFixture {
  val owner = ComponentInstanceId(SmWorkflowComponent.componentId, "human-spec")
  val snapshot = Ordered.Snapshot("a" * 40, "b" * 40)
  val plan = Ordered.Plan("plan", "1", "phase", "repository", Vector(
    Ordered.Step("step", "Original purpose", Vector("one.scala"), Vector.empty, Vector("focused"))), Vector("focused"))
  val work = Work.Request("instance", "3", "ImplementStep", Some("step"), 0, "Original purpose", plan,
    snapshot, Vector.empty, None, None, None)
  val goalInput = Goal.Input(work, ContextReference("requesting-result", "1"),
    Work.Result("decision-needed", "Original explanation", Vector.empty), "Original question reason")
  val conflict = Conflict.Request("instance", "3", "a" * 40, "b" * 40, Vector(
    Conflict.Conflict("conflict", "one.scala", Conflict.Blob("c" * 40, "base\n"), Conflict.Blob("d" * 40, "local\n"),
      Conflict.Blob("e" * 40, "remote\n"), worktree = "Original native conflict text\n")))
  val repositoryInput = Repository.Input(conflict, "requesting-result", "1", "Original conflict rationale")
  private def question[W](workflow: String, input: W, inputType: String, resultType: String,
    completion: StateMachineOperationIdentity): WorkflowInteraction[W, Nothing] = {
    val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity(workflow),
      WorkflowDefinitionRevision("actual-executable"), InstanceIdentity("instance"))
    val request = ContinuationRequest(StateMachineRunIdentity("instance"), ContinuationIdentity("human-slot"),
      StateMachineRevision("4"), StateMachineRequiredOperationIdentity("human-capability"),
      StateMachineOperationIdentity("HumanService", "answer"), Some(TypedValue(inputType, input)), Some(StateMachineResultTypeReference(resultType)),
      ContextBundle("Original context", Vector.empty, Vector.empty, ContextSnapshot(handle.workflowRevision.value, modelRevision = Some("actual-model"))),
      CompletionContract("completion", Vector.empty), EvidenceContract("evidence", Vector.empty), completion)
    WorkflowInteraction[W, Nothing](handle, WorkflowContinuation.Decision(request, MinimalPresentation("Original title", "Original notice")))
  }
  val goalQuestion = question("GoalPhaseWorkflow", goalInput, Goal.inputCodec.typeIdentity, Goal.answerCodec.typeIdentity, Goal.completionOperation)
  val repositoryQuestion = question("RepositorySyncWorkflow", repositoryInput, Repository.inputCodec.typeIdentity, Repository.answerCodec.typeIdentity, Repository.completionOperation)
  private def result[W, A](question: WorkflowInteraction[W, Nothing], value: A, typeIdentity: String): ContinuationResult[A] = {
    val request = question.current.asInstanceOf[WorkflowContinuation.Decision[W]].request
    ContinuationResult(question.handle, request.runId, request.continuationId, request.expectedRevision, request.context.snapshot,
      TypedValue(typeIdentity, value), ContextReference("human-answer", "1"), Vector.empty,
      ExecutionEvidence(Vector.empty, Some("actual-human")))
  }
  val goalAnswer = result(goalQuestion, Goal.Answer("ResumeAssignedWork", "Original direction explanation"), Goal.answerCodec.typeIdentity)
  val repositoryAnswer = result(repositoryQuestion, Repository.Answer("ProvideResolution",
    Vector(RepositorySyncConflictSemantic.Resolution("conflict", "Actual resolved contents\n")), "Original direction explanation"), Repository.answerCodec.typeIdentity)
  def production(purpose: Purpose): Json = if (purpose == Purpose.GoalHuman)
    take(ThreeWorkflowHostEvidencePorts.goalHumanBindingC(owner.canonicalKey, goalQuestion, goalAnswer, "actual-submitter"))
  else take(ThreeWorkflowHostEvidencePorts.repositoryHumanBindingC(Repository.AnswerContext(owner, repositoryQuestion, repositoryAnswer, "actual-submitter")))
  def at(value: Json, path: String*): Json = path.foldLeft(value)((current, key) => current.hcursor.downField(key).focus.get)
  def alter(value: Json, path: Vector[String])(f: Json => Json): Json = path.headOption match {
    case None => f(value)
    case Some(key) => value.mapObject(root => root.add(key, alter(root(key).get, path.tail)(f)))
  }
}

final class NativeHostHumanScopeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeHostHumanEvidenceFixture.*
  private val now = Instant.parse("2026-10-08T00:00:00Z")
  private val declared = Declaration("workflow", DataStore.CollectionId.EntityStore(EntityCollectionId("test", "host", "human_evidence")), "independent-host")
  private def evidence(purpose: Purpose, binding: Json) = Evidence(schema, purpose.identity, "actual-answer-lookup", "human-answer", "1",
    declared.issuer, "actual-human", binding, now.minusSeconds(1).toString)
  private def read(purpose: Purpose, binding: Json) = take(decodeStorageC(take(encodeStorageC(evidence(purpose, binding)))))
  private def observe(saved: Evidence, purpose: Purpose, binding: Json) = observeC(saved, declared, purpose, "actual-answer-lookup", binding, now)

  "Independent native Human attestation" should {
    "produce actual question direction and participant facts without complete question or answer transport copies" in {
      Given("Spec: docs/spec/native-host-human-applicability.md; Rule: R1; Example: E1; passive typed Human question and answer fixtures")
      When("both production helpers build their selected attestation")
      val cases = Vector(Purpose.GoalHuman, Purpose.RepositoryHuman).map(p => (p, production(p)))
      Then("production is current selected semantic data, not full public wire envelopes or diagnostic copies")
      cases.foreach { (purpose, produced) =>
        NativeHostHumanScope.select(purpose, produced) shouldBe Right(produced)
        read(purpose, produced).binding shouldBe produced
        produced.asObject.get.keys.toSet shouldBe Set("componentInstance", "humanAttestation")
        produced.noSpaces should not include "Original direction explanation"
        produced.noSpaces should not include "Original native conflict text"
        produced.noSpaces should not include "Original purpose"
        at(produced, "humanAttestation", "question").asObject.get.keys.toSet should not contain "context"
      }
    }
    "ignore unused question Plan context presentation and explanatory answer copies while consuming original direction" in {
      Given("Spec: docs/spec/native-host-human-applicability.md; Rule: R1; Example: E2; independently saved actual Human direction")
      Vector(Purpose.GoalHuman, Purpose.RepositoryHuman).foreach { purpose =>
        val binding = production(purpose)
        val saved = read(purpose, binding)
        val changed = alter(binding, Vector("humanAttestation", "question"))(_.mapObject(
          _.add("presentation", Json.True).add("sourceLine", Json.Null).add("generator", Json.True)
            .add("expectedRevision", Json.False).add("modelRevision", Json.True).add("commandGrant", Json.True)))
        When("only diagnostic or copied explanatory fields become different or unusable")
        val admitted = observe(saved, purpose, changed)
        Then("the original attested target/direction still resolves without renewing evidence")
        admitted.isSuccess shouldBe true
        admitted.toOption.get.evidence.occurredAt shouldBe saved.occurredAt
      }
    }
    "reject foreign owner submitter worker slot requesting result native target choice or resolution" in {
      Given("Spec: docs/spec/native-host-human-applicability.md; Rule: R2; Example: E3; actual ordered and conflict Human attestations")
      Vector(Purpose.GoalHuman, Purpose.RepositoryHuman).foreach { purpose =>
        val binding = production(purpose)
        val saved = read(purpose, binding)
        val questionPath = Vector("humanAttestation", "question")
        val answerPath = Vector("humanAttestation", "answer")
        val variants = Vector(binding.mapObject(_.add("componentInstance", Json.fromString("foreign-owner"))),
          alter(binding, Vector("humanAttestation"))(_.mapObject(_.add("submittingParticipant", Json.fromString("foreign-submitter")))),
          alter(binding, questionPath)(_.mapObject(_.add("continuationId", Json.fromString("foreign-slot")))),
          alter(binding, questionPath :+ "source" :+ "requestingResult")(_.mapObject(_.add("identity", Json.fromString("foreign-requesting-result")))),
          alter(binding, answerPath)(_.mapObject(_.add("worker", Json.fromString("foreign-human")))),
          alter(binding, answerPath)(_.mapObject(_.add("choice", Json.fromString("Cancel"))))) ++
          (if (purpose == Purpose.GoalHuman) Vector(alter(binding, questionPath ++ Vector("source", "assignment", "originalBase"))(
            _.mapObject(_.add("tree", Json.fromString("f" * 40))))) else Vector(
            alter(binding, questionPath ++ Vector("source", "conflict"))(_.mapObject(_.add("remoteTip", Json.fromString("f" * 40)))),
            alter(binding, answerPath :+ "resolutions")(_.mapArray(_.map(_.mapObject(_.add("text", Json.fromString("Other actual resolution\n"))))))))
        When("real attested actors targets or semantic directions differ")
        val outcomes = variants.map(observe(saved, purpose, _))
        Then("no foreign direction becomes Human admission or command authority")
        outcomes.foreach(_.isSuccess shouldBe false)
        observe(saved.copy(participant = "foreign-human"), purpose, binding).isSuccess shouldBe false
        observe(saved.copy(reference = "foreign-answer"), purpose, binding).isSuccess shouldBe false
        observe(saved.copy(issuer = "foreign-issuer"), purpose, binding).isSuccess shouldBe false
        observe(saved.copy(occurredAt = now.plusSeconds(1).toString), purpose, binding).isSuccess shouldBe false
      }
    }
    "keep literal native paths and resolution text as semantics without narrowing valid scope to display-safe strings" in {
      Given("Spec: docs/spec/native-host-human-applicability.md; Rule: R2; Example: E5; a native path containing a newline and a literal resolution")
      val binding = production(Purpose.RepositoryHuman)
      val literal = alter(binding, Vector("humanAttestation", "question", "source", "conflict", "conflicts"))(_.mapArray(_.map(
        _.mapObject(_.add("path", Json.fromString("literal\nname.scala"))))))
      When("the actual scoped path and answer are selected")
      val selected = NativeHostHumanScope.select(Purpose.RepositoryHuman, literal)
      Then("native path scope stays literal; no management hash or diagnostic-body copy is introduced")
      selected shouldBe Right(literal)
      val actual = read(Purpose.RepositoryHuman, literal)
      observe(actual, Purpose.RepositoryHuman, literal).isSuccess shouldBe true
      actual.binding.noSpaces should not include "worktree"
      at(actual.binding, "humanAttestation", "answer", "resolutions").asArray.get.head.hcursor.get[String]("text").toOption.get shouldBe "Actual resolved contents\n"
    }
  }
}
