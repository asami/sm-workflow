package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.{Files, Path}
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.context.RuntimeContext.{PropertyNameContext, PropertyNameStyle}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.record.{Record, RecordPresentable}
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.preparation.NativeHumanDecisionPreparation as Preparation
import org.simplemodeling.textus.smworkflow.value as V
import NativeExternalEvidence.{Purpose, Evidence}
import SplitPhaseExplicitPartitionFixture.take

/** Shared specification-owned inputs, never a genuine Human/review issuer.
  * @since   Oct. 14, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object NativePreparationFixture {
  val now = Instant.parse("2026-10-07T00:00:00Z")
  val reference = V.WorkflowArtifactReference("human-answer", "1")
  val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("actual-human"), None, None)
  val participant = "actual-submitter"
  val issuer = "normal-development-host"
  val goalAnswer = V.GoalPhaseOrderedDecisionAnswer("ResumeAssignedWork", "Specification direction")
  val repositoryAnswer = V.RepositorySyncHumanConflictAnswer("ProvideResolution",
    Vector(V.RepositorySyncConflictResolution("conflict", "Actual resolved contents\n")), "Specification direction")
  def splitAnswer(choice: String = "ProvideResolution"): V.SplitPhaseHumanDirectionAnswer =
    V.SplitPhaseHumanDirectionAnswer(V.SplitPhaseHumanDirectionChoice(choice),
      if (choice == "ProvideResolution") Some(V.SplitPhasePartitionContextReference("actual-resolution", "1")) else None)
  def json(record: Record): Json = io.circe.parser.parse(take(record.toJsonStringC)).toOption.get
  def record(json: Json): Record = take(new RecordDecoder().json(json.noSpaces))
  // Independent Core R4 expectation: omit object nulls at every depth, never array elements.
  def expectedRecordJson(value: Json): Json = value.arrayOrObject(value,
    values => Json.fromValues(values.map(expectedRecordJson)),
    fields => Json.fromJsonObject(io.circe.JsonObject.fromIterable(fields.toVector.collect {
      case (key, field) if !field.isNull => key -> expectedRecordJson(field)
    })))
  def wire(value: RecordPresentable): Record = record(json(value.toRecord()))
  def evidence(prepared: Preparation.Prepared): Evidence =
    take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
  def evidenceKey: String = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
  val declaration = NativeExternalEvidence.Declaration("workflow", DataStore.CollectionId.EntityStore(
    EntityCollectionId("test", "host", "human_evidence")), issuer)
  def observe(value: Evidence, purpose: Purpose, target: Json): Consequence[NativeExternalEvidence.Observed] =
    NativeExternalEvidence.observeC(value, declaration, purpose, evidenceKey, target, now)
  def work[A](workflow: String, input: A, inputType: String, resultType: String,
    completion: StateMachineOperationIdentity): WorkflowInteraction[A, Nothing] = {
    val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity(workflow),
      WorkflowDefinitionRevision("specification-executable"), InstanceIdentity("instance"))
    val request = ContinuationRequest(StateMachineRunIdentity("specification-run"), ContinuationIdentity("specification-review-slot"),
      StateMachineRevision("8"), StateMachineRequiredOperationIdentity("specification-review-capability"),
      StateMachineOperationIdentity("SpecificationReview", "review"), Some(TypedValue(inputType, input)),
      Some(StateMachineResultTypeReference(resultType)), ContextBundle("Specification input", Vector.empty, Vector.empty,
        ContextSnapshot(handle.workflowRevision.value)), CompletionContract("specification-completion", Vector.empty),
      EvidenceContract("specification-evidence", Vector.empty), completion)
    WorkflowInteraction[A, Nothing](handle, WorkflowContinuation.WorkOrder(request,
      ExecutionRequirement(Vector.empty, RiskLevel("SOURCE_REVIEW"), ReasoningLevel.Standard, true),
      MinimalPresentation("Specification review", "Fixture response")))
  }
  val receipt = RepositorySyncNativeMergeValidation.Receipt("focused", "exited", Some(0), "diagnostic", "")
  def goalWork(kind: String = "ReviewStep") = work("GoalPhaseWorkflow", NativeHumanEvidenceFixture.work.copy(
    sourceRevision = "7", kind = kind, candidate = Some(NativeHumanEvidenceFixture.snapshot),
    checksReceipt = Some("focused"), review = Some(GoalPhaseNativeReview.Context(Vector("one.scala"), "fixture diff", Vector(receipt)))),
    GoalPhaseNativeWork.inputCodec.typeIdentity, GoalPhaseNativeWork.resultCodec.typeIdentity, GoalPhaseNativeRun.completionOperation)
  def repositoryWork = work("RepositorySyncWorkflow", RepositorySyncNativeMergeReview.Request("instance", "7", "a" * 40,
    "c" * 40, "b" * 40, Vector("one.scala"), "fixture local diff", "fixture remote diff", "required", "focused",
    Vector("focused"), Vector(receipt)), RepositorySyncNativeMergeReview.inputCodec.typeIdentity,
    RepositorySyncNativeMergeReview.resultCodec.typeIdentity, RepositorySyncNativeMergeReviewContinuation.completionOperation)
}

private[admission] trait NativePreparationManagedFixture extends ThreeWorkflowManagedRepositoryFixture {
  protected def withCore(body: Setup => Consequence[Unit]): Unit = {
    val parent = Files.createDirectories(Path.of("target/sm-workflow/work/human-preparation").toAbsolutePath)
    val root = Files.createTempDirectory(parent, "specification-")
    _with_runtime_(root, "UpToDate") { (_, _, _, prepare) => prepare(body) }
  }
}

/** Issued fixture questions and explicit fixture answers exercise passive preparation.
  * @since   Oct. 14, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeHumanDecisionPreparationSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativePreparationManagedFixture {
  import NativePreparationFixture.*
  private val _goal = NativeHumanEvidenceFixture
  private val _split = NativeSplitAnswerEvidenceFixture

  "Ordinary source-history recovery Human preparation" should {
    "retain the complete target across public names and refuse missing crossed malformed or legacy consent" in {
      Given("SOURCE-HISTORY-CONTINUATION-002: a fixture-issued ordinary GoalHuman with its exact first unknown intent target")
      val history = new AnyWordSpec with NativeGoalSourceHistoryCliFixture {}
      val target = GoalPhaseSourceHistoryRecovery.RecoveryTarget("instance", "plan", "1", _goal.snapshot, _goal.snapshot,
        "GoalPhaseWorkflow", "actual-executable", participant, "step", "50", "52",
        "instance:ordered-candidate:50:source-history-intent-0", "1", "52", Vector(history.sourcePath),
        Vector(history.sourcePath), Vector.empty, history.enabledSettings(history.ruleKey),
        GoalPhaseNativeCandidate.Payload("50", "step", Vector(history.sourcePath), Vector.empty, None, Vector.empty, None),
        GoalPhaseNativeSourceHistory.Payload("50", "step", history.sourcePath, "goldenport",
          "native-cli-external-goldenport-v1", "2026-10-21", true, None))
      val decision = _goal.goalQuestion.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]]
      val input = _goal.goalInput.copy(work = _goal.work.copy(selectedPlan = _goal.plan.copy(steps = _goal.plan.steps.map(
        _.copy(ownedPaths = Vector(history.sourcePath))))), result = GoalPhaseNativeWork.Result("completed", "Retained accepted work", Vector.empty),
        reason = GoalPhaseSourceHistoryRecovery.message, sourceHistoryRecovery = Some(target))
      val question = _goal.goalQuestion.copy(current = decision.copy(request = decision.request.copy(input = Some(
        TypedValue(GoalPhaseOrderedHumanDecision.inputCodec.typeIdentity, input))), presentation = MinimalPresentation(
        "Fixture recovery question", Json.obj("choices" -> Json.arr(GoalPhaseSourceHistoryRecovery.choices.map(Json.fromString)*)).noSpaces)))
      val answer = V.GoalPhaseOrderedDecisionAnswer("AuthorizeNewAttempt", "Fixture-owned independent direction")
      val submitted = _goal.goalAnswer.copy(result = TypedValue(GoalPhaseOrderedHumanDecision.answerCodec.typeIdentity,
        GoalPhaseOrderedHumanDecision.Answer(answer.choice, answer.rationale)))
      withCore { call =>
        When("ordinary output and passive preparation consume camel snake and kebab property names")
        val output = take(NativeWorkflowOperationOutput.goalHumanC(question, call.core))
        val decoded = take(V.GoalPhaseAdvanceResult.createC(wire(output)))
        val context = decoded.human.get.humanDecisionContext.get
        val contexts = Vector(context, context.copy(question = PropertyNameContext.default.transformRecord(context.question)),
          context.copy(question = PropertyNameContext(outputStyle = PropertyNameStyle.KebabCase).transformRecord(context.question)))
        val prepared = contexts.map(value => take(Preparation.goalC(value, answer, reference, execution, participant, issuer, now)))
        val binding = take(ThreeWorkflowExternalEvidencePorts.goalHumanBindingC(context.componentInstance, question, submitted, participant))
        Then("the full captured target and exact ordinary GoalHuman evidence survive every public wire style")
        take(GoalPhaseSourceHistoryRecovery.targetC(decoded.human.get.sourceHistoryRecovery.get)) shouldBe target
        prepared.foreach(value => observe(evidence(value), Purpose.GoalHuman, binding).isSuccess shouldBe true)
        prepared.map(value => evidence(value).binding).distinct.size shouldBe 1
        NativeExternalEvidence.schema shouldBe "sm-workflow.native-external-evidence.v7"

        Given("a changed or omitted recovery target, malformed presence, and a contradictory public alias")
        val raw = json(context.question)
        val path = Vector("source", "sourceHistoryRecovery")
        val changed = _goal.alter(raw, path)(_ => io.circe.Encoder[GoalPhaseSourceHistoryRecovery.RecoveryTarget].apply(
          target.copy(planRevision = "different-plan")))
        val omitted = _goal.alter(raw, Vector("source"))(_.mapObject(_.remove("sourceHistoryRecovery")))
        val malformed = Vector(Json.True, Json.obj()).map(value => _goal.alter(raw, path)(_ => value)) :+
          _goal.alter(raw, Vector("source"))(_.mapObject(_.add("source_history_recovery",
            summon[io.circe.Encoder[GoalPhaseSourceHistoryRecovery.RecoveryTarget]].apply(target))))
        When("preparation receives changed omitted malformed targets or AuthorizeNewAttempt on an ordinary legacy question")
        val changedprepared = take(Preparation.goalC(context.copy(question = record(changed)), answer,
          reference, execution, participant, issuer, now))
        val refused = (malformed :+ omitted).map(value => Preparation.goalC(context.copy(question = record(value)),
          answer, reference, execution, participant, issuer, now))
        val ordinarychoice = Preparation.goalC(context, goalAnswer, reference, execution, participant, issuer, now)
        val legacy = take(Preparation.goalC(context.copy(question = record(omitted)),
          V.GoalPhaseOrderedDecisionAnswer("Defer", "Legacy fixture direction"), reference, execution, participant, issuer, now))
        Then("changed and legacy attestation cannot bind the recovery target and malformed or unauthorized direction refuses")
        observe(evidence(changedprepared), Purpose.GoalHuman, binding).isSuccess shouldBe false
        observe(evidence(legacy), Purpose.GoalHuman, binding).isSuccess shouldBe false
        refused.foreach(_.isSuccess shouldBe false)
        ordinarychoice.isSuccess shouldBe false
        Consequence.unit
      }
    }
  }

  "Saved complete Plan revision preparation" should {
    "preserve real public nested names and bind the fixture answer to exact captured requirements checks and proposal identity" in {
      Given("a typed revision question with full saved capture and a SPECIFICATION-OWNED answer with no genuine adoption claim")
      withCore { call =>
        val captured = _goal.revisionCapture()
        val question = _goal.revisionQuestion(captured)
        val answer = goalAnswer.copy(choice = "AdoptPlanRevision", rationale = "SPECIFICATION-OWNED adoption example")
        When("normal public output crosses its generated reader, real CNCF name formatters and passive evidence preparation")
        val output = take(NativeWorkflowOperationOutput.goalHumanC(question, call.core))
        val decoded = take(V.GoalPhaseAdvanceResult.createC(wire(output)))
        val context = decoded.human.get.humanDecisionContext.get
        val contexts = Vector(context, context.copy(question = PropertyNameContext.default.transformRecord(context.question)),
          context.copy(question = PropertyNameContext(outputStyle = PropertyNameStyle.KebabCase).transformRecord(context.question)))
        val prepared = contexts.map(value => take(Preparation.goalC(value, answer, reference, execution,
          participant, issuer, now.minusSeconds(60))))
        val target = take(ThreeWorkflowExternalEvidencePorts.goalHumanBindingC(context.componentInstance,
          question, _goal.revisionAnswer(question), participant))
        Then("all public spellings retain the same complete approval target without executing or granting consent")
        decoded.human.get.choices shouldBe Vector("AdoptPlanRevision", "RejectPlanRevision", "Defer", "Cancel")
        take(GoalPhasePlanRevision.normalizeJsonC(json(decoded.human.get.planRevision.get)))
          .as[GoalPhasePlanRevision.Captured] shouldBe Right(captured)
        prepared.map(value => evidence(value).binding).distinct.size shouldBe 1
        prepared.foreach(value => observe(evidence(value), Purpose.GoalHuman, target).isSuccess shouldBe true)

        Given("the saved independent row, later different project defaults and changed proposed business targets")
        val saved = evidence(prepared.head)
        val later = GoalPhaseAcceptanceBoundary.Project(Vector(
          GoalPhaseAcceptanceBoundary.Condition("later", "step", true, "Later host default")))
        val laterselection = take(GoalPhaseAcceptanceBoundary.captureC(Some(later), captured.proposal.plan))
        val variants = Vector(_goal.revisionCapture(check = "different-check"),
          _goal.revisionCapture(requirement = "Different required behavior"),
          captured.copy(proposal = captured.proposal.copy(identity = "different-proposal")),
          captured.copy(proposal = captured.proposal.copy(revision = "2")))
        When("saved capture is decoded independently and native observation compares the changed targets with that old row")
        val retained = GoalPhaseOrderedHumanDecision.inputCodec.decode(GoalPhaseOrderedHumanDecision.inputCodec.encode(
          question.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]].request.input.get.value))
        val crossed = variants.map { value =>
          val changed = _goal.revisionQuestion(value)
          val binding = take(ThreeWorkflowExternalEvidencePorts.goalHumanBindingC(context.componentInstance,
            changed, _goal.revisionAnswer(changed), participant))
          observe(saved, Purpose.GoalHuman, binding)
        }
        Then("saved business input survives later defaults while changed checks acceptance identity or revision require their own evidence")
        retained.toOption.get.planRevision shouldBe Some(captured)
        laterselection.effectiveAcceptance should not be captured.plan.effectiveAcceptance
        observe(saved, Purpose.GoalHuman, target).isSuccess shouldBe true
        crossed.foreach(_.isSuccess shouldBe false)

        Given("present malformed revision data and equal or conflicting public-name collisions")
        val raw = json(context.question)
        val path = Vector("source", "planRevision")
        val revision = _goal.at(raw, path*)
        val collisions = Vector(revision.mapObject(fields => fields.add("plan", fields("plan").get.mapObject { plan =>
          plan.add("phase_checks", plan("phaseChecks").get)
        })), revision.mapObject(fields => fields.add("plan", fields("plan").get.mapObject { plan =>
          plan.add("phase_checks", Json.arr(Json.fromString("foreign-check")))
        })))
        val malformed = Vector(Json.True, Json.obj()) ++ collisions
        val records = malformed.map(value => GoalPhaseNativeMetadata.record(_goal.alter(raw, path)(_ => value))) :+
          GoalPhaseNativeMetadata.record(_goal.alter(raw, Vector("source"))(_.mapObject(
            _.add("plan_revision", revision))))
        When("normal preparation receives malformed nonnull structured Records")
        val rejected = records.map(value => Preparation.goalC(context.copy(question = value), answer,
          reference, execution, participant, issuer, now))
        Then("no malformed or colliding present Plan revision disappears into an ordinary question")
        rejected.foreach(_.isSuccess shouldBe false)

        Given("Core R4 omits an optional null Plan revision before Human preparation")
        val nullrevision = _goal.alter(raw, path)(_ => Json.Null)
        val omittedrevision = _goal.alter(raw, Vector("source"))(_.mapObject(_.remove("planRevision")))
        val nullrecord = GoalPhaseNativeMetadata.record(nullrevision)
        When("passive preparation consumes the surviving question and the explicitly omitted equivalent")
        val omitted = Vector(nullrecord, record(omittedrevision)).map(value => evidence(take(Preparation.goalC(
          context.copy(question = value), answer, reference, execution, participant, issuer, now))))
        Then("the canonical Record and selected binding omit revision without restoring captured approval facts")
        json(nullrecord) shouldBe expectedRecordJson(nullrevision)
        json(nullrecord).hcursor.downField("source").downField("planRevision").focus shouldBe None
        omitted.map(_.binding).distinct.size shouldBe 1
        omitted.head.binding.hcursor.downField("humanAttestation").downField("question").downField("source")
          .downField("planRevision").focus shouldBe None
        observe(omitted.head, Purpose.GoalHuman, target).isSuccess shouldBe false
        Consequence.unit
      }
    }
  }

  "R3/R4 saved Human repair progress preparation" should {
    "preserve all six runtime repair progress members across public aliases and refuse crossed or malformed policy context" in {
      Given("a specification Human question with distinct saved counts and the existing selected runtime policy")
      withCore { call =>
        val policy = GoalPhaseRuntimeRepairControl.version
        val assigned = GoalPhaseRepairProgress.Snapshot(1, 1, 2, Some(policy), Some(10), Some(10))
        val current = assigned.copy(ordinaryCount = 0)
        val question = _goal.progressQuestion(assigned, current)
        When("native output crosses the generated reader and passive preparation under canonical snake and kebab names")
        val output = take(NativeWorkflowOperationOutput.goalHumanC(question, call.core))
        val decoded = take(V.GoalPhaseAdvanceResult.createC(wire(output)))
        val context = decoded.human.get.humanDecisionContext.get
        val raw = json(context.question)
        val contexts = Vector(context, context.copy(question = PropertyNameContext.default.transformRecord(context.question)),
          context.copy(question = PropertyNameContext(outputStyle = PropertyNameStyle.KebabCase).transformRecord(context.question)))
        val prepared = contexts.map(value => take(Preparation.goalC(value, goalAnswer, reference, execution,
          participant, issuer, now.minusSeconds(60))))
        val target = take(ThreeWorkflowExternalEvidencePorts.goalHumanBindingC(context.componentInstance,
          question, _goal.goalAnswer, participant))
        Then("all spellings bind exactly the native six-member current and assignment snapshots")
        decoded.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 0, 2, Some(policy), Some(10), Some(10)))
        prepared.map(value => evidence(value).binding).distinct.size shouldBe 1
        prepared.foreach(value => observe(evidence(value), Purpose.GoalHuman, target).isSuccess shouldBe true)

        Given("changed policy context at either consumed progress position and malformed or contradictory supplied aliases")
        val sourcepaths = Vector(Vector("source", "repairProgress"), Vector("source", "assignment", "repairProgress"))
        val bindingpaths = sourcepaths.map(Vector("humanAttestation", "question") ++ _)
        val changed = Vector("runtimePolicy" -> Json.fromString("other-policy"), "selectedLimit" -> Json.fromInt(9),
          "effectiveLimit" -> Json.fromInt(9))
        val crossed = bindingpaths.flatMap(path => changed.map { case (name, value) =>
          _goal.alter(target, path)(_.mapObject(_.add(name, value)))
        })
        val invalid = Vector("runtimePolicy" -> Json.fromString("other-policy"), "selectedLimit" -> Json.fromString("ten"),
          "effectiveLimit" -> Json.fromInt(9))
        val aliases = Vector("runtime_policy" -> Json.fromString("other-policy"), "selected_limit" -> Json.fromInt(9),
          "effective_limit" -> Json.fromInt(9))
        val malformed = sourcepaths.flatMap(path => (invalid ++ aliases ++
          Vector("runtimePolicy", "selectedLimit", "effectiveLimit").map(_ -> Json.Null)).map { case (name, value) =>
          _goal.alter(raw, path)(_.mapObject(_.add(name, value)))
        })
        When("native evidence observation compares changed targets and preparation consumes malformed policy input")
        val crossedresults = crossed.map(observe(evidence(prepared.head), Purpose.GoalHuman, _))
        val refused = malformed.map(value => Preparation.goalC(context.copy(question = record(value)),
          goalAnswer, reference, execution, participant, issuer, now))
        Then("neither changed policy context nor malformed colliding or partial progress can reuse the original evidence")
        crossedresults.foreach(_.isSuccess shouldBe false)
        refused.foreach(_.isSuccess shouldBe false)
        Consequence.unit
      }
    }

    "preserve separate current and assignment snapshots through public wire and the managed Reader" in {
      Given("a specification-issued Human question with saved current 1/0/2 and prior assignment 1/1/2")
      withCore { call =>
        val question = _goal.progressQuestion()
        When("normal public output crosses its generated reader and passive declaration or wire preparation")
        val output = take(NativeWorkflowOperationOutput.goalHumanC(question, call.core))
        val decoded = take(V.GoalPhaseAdvanceResult.createC(wire(output)))
        val context = decoded.human.get.humanDecisionContext.get
        val raw = json(context.question)
        val contexts = Vector(context, context.copy(question = PropertyNameContext.default.transformRecord(context.question)),
          context.copy(question = PropertyNameContext(outputStyle = PropertyNameStyle.KebabCase).transformRecord(context.question)))
        val prepared = contexts.map(c => take(Preparation.goalC(c, goalAnswer, reference, execution,
          participant, issuer, now.minusSeconds(60))))
        val target = take(ThreeWorkflowExternalEvidencePorts.goalHumanBindingC(context.componentInstance, question, _goal.goalAnswer, participant))
        Then("the exact current and prior counts survive independently without duplicate display fields")
        decoded.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 0, 2, None, None, None))
        prepared.foreach(p => observe(evidence(p), Purpose.GoalHuman, target).isSuccess shouldBe true)
        prepared.map(p => evidence(p).binding).distinct.size shouldBe 1
        json(output.toRecord()).hcursor.downField("human").downField("repairProgress").focus shouldBe None
        json(output.toRecord()).hcursor.downField("human").downField("work").downField("repairProgress").focus shouldBe None

        Given("that prepared row provisioned under the existing managed Create permission and changed selected count contexts")
        take(call.createC("host_evidence", prepared.head.recordIdentity, prepared.head.record))
        val reader = take(NativeExternalEvidence.bindC(call.core.component.get.subsystem.get.configuration,
          GoalPhaseRuntimeParameters.Persistence(_configuration_, _collection_("instances"), _collection_("continuations"))))
        val paths = Vector(Vector("humanAttestation", "question", "source", "repairProgress"),
          Vector("humanAttestation", "question", "source", "assignment", "repairProgress"))
        val crossed = paths.map(path => _goal.alter(target, path)(_.mapObject(_.add("automaticCount", Json.fromInt(2)))))
        When("the current managed Reader checks the original row against exact and crossed progress")
        val accepted = take(reader.loadC(call.core, Purpose.GoalHuman, evidenceKey, target))
        val rejected = crossed.map(reader.loadC(call.core, Purpose.GoalHuman, evidenceKey, _))
        Then("only the exact selected snapshots reuse the independent row")
        accepted.map(_.evidence.binding) shouldBe Some(evidence(prepared.head).binding)
        rejected.foreach(_.isSuccess shouldBe false)

        Given("present malformed and contradictory nonnull progress names at either consumed source position")
        val sourcepaths = Vector(Vector("source", "repairProgress"), Vector("source", "assignment", "repairProgress"))
        val malformed = sourcepaths.flatMap(path => Vector(Json.obj(), Json.True,
          Json.obj("automatic_count" -> Json.fromInt(1), "ordinary_count" -> Json.fromInt(2), "limit" -> Json.fromInt(2)))
          .map(v => _goal.alter(raw, path)(_ => v))) ++ sourcepaths.map(path => _goal.alter(raw, path)(
            _.mapObject(_.add("automatic_count", Json.fromInt(2)))))
        When("public preparation consumes each malformed supplied Record")
        val failures = malformed.map(v => Preparation.goalC(context.copy(question = record(v)),
          goalAnswer, reference, execution, participant, issuer, now))
        Then("no malformed presence or contradictory count alias can disappear")
        failures.foreach(_.isSuccess shouldBe false)
        sourcepaths.foreach { path =>
          Given("Core R4 omits optional null progress at " + path.mkString("."))
          val supplied = _goal.alter(raw, path)(_ => Json.Null)
          val explicit = _goal.alter(raw, path.init)(_.mapObject(_.remove(path.last)))
          val questionrecord = record(supplied)
          When("passive Human preparation consumes the surviving and explicitly omitted progress shapes")
          val omitted = Vector(questionrecord, record(explicit)).map(q => evidence(take(Preparation.goalC(
            context.copy(question = q), goalAnswer, reference, execution, participant, issuer, now))))
          Then("the selected position remains absent and cannot reuse the original native progress evidence")
          json(questionrecord) shouldBe expectedRecordJson(supplied)
          _goal.at(json(questionrecord), path.init*).hcursor.downField(path.last).focus shouldBe None
          omitted.map(_.binding).distinct.size shouldBe 1
          val selectedsource = omitted.head.binding.hcursor.downField("humanAttestation").downField("question").downField("source").focus.get
          _goal.at(selectedsource, path.tail.init*).hcursor.downField(path.last).focus shouldBe None
          observe(omitted.head, Purpose.GoalHuman, target).isSuccess shouldBe false
        }
        Consequence.unit
      }
    }
  }

  "E7 Finite Human question wire names" should afterWord(
    "in spec:native-host-human-preparation, example:E7, rules:R1,R2,R3,R5,R6,R7, phase:6") {
    "preserve each native target across framework aliases and retain present-key precedence" in {
      Given("existing native Goal Repository Scope and Reasoning fixture Decisions and supplied typed answers")
      withCore { call =>
        val decision = _goal.repositoryQuestion.current.asInstanceOf[WorkflowContinuation.Decision[RepositorySyncHumanConflict.Input]]
        val repositoryinput = decision.request.input.get.value
        val repositoryquestion = _goal.repositoryQuestion.copy(current = decision.copy(request = decision.request.copy(input = Some(
          TypedValue(RepositorySyncHumanConflict.inputCodec.typeIdentity, repositoryinput.copy(conflict = repositoryinput.conflict.copy(
            conflicts = repositoryinput.conflict.conflicts.map(_.copy(path = "literal\npath.scala")))))))))
        val contexts = Vector(take(NativeHumanDecisionContext.goalC(call.core, _goal.goalQuestion)),
          take(NativeHumanDecisionContext.repositoryC(call.core, repositoryquestion)),
          take(NativeHumanDecisionContext.splitC(call.core, _split.scopeQuestion)),
          take(NativeHumanDecisionContext.splitC(call.core, _split.reasoningQuestion)))
        val targets = Vector(
          take(ThreeWorkflowExternalEvidencePorts.goalHumanBindingC(contexts.head.componentInstance, _goal.goalQuestion, _goal.goalAnswer, participant)),
          take(ThreeWorkflowExternalEvidencePorts.repositoryHumanBindingC(RepositorySyncHumanConflict.AnswerContext(
            call.core.component.get.core.instanceId, repositoryquestion, _goal.repositoryAnswer, participant)))) ++
          Vector(_split.scopeQuestion, _split.reasoningQuestion).map(q => take(ThreeWorkflowExternalEvidencePorts.splitAnswerBindingC(
            SplitPhaseHumanAnswerVerification.AnswerContext(call.core.component.get.core.instanceId, q, _split.answer(q), participant))))
        def _prepare_(context: V.WorkflowHumanDecisionContext): Consequence[Preparation.Prepared] = context.purpose match {
          case purpose if purpose == Purpose.GoalHuman.identity =>
            Preparation.goalC(context, goalAnswer, reference, execution, participant, issuer, now.minusSeconds(60))
          case purpose if purpose == Purpose.RepositoryHuman.identity =>
            Preparation.repositoryC(context, repositoryAnswer, reference, execution, participant, issuer, now.minusSeconds(60))
          case _ => Preparation.splitC(context, splitAnswer(), reference, execution, participant, issuer, now.minusSeconds(60))
        }
        def _replace_at_(value: Json, path: List[String], replacement: Json): Json = path match {
          case key :: Nil => value.mapObject(_.add(key, replacement))
          case key :: rest => value.mapObject(fields => fields.add(key, _replace_at_(fields(key).get, rest, replacement)))
          case Nil => replacement
        }
        def _question_record_(value: Json): Record = {
          val question = record(value)
          json(question) shouldBe expectedRecordJson(value)
          question
        }
        contexts.zip(targets).foreach { (context, target) =>
          val purpose = if (context.purpose == Purpose.GoalHuman.identity) Purpose.GoalHuman
            else if (context.purpose == Purpose.RepositoryHuman.identity) Purpose.RepositoryHuman else Purpose.SplitAnswer
          val raw = json(context.question)
          val canonical = take(_prepare_(context))
          val snake = PropertyNameContext.default.transformRecord(context.question)
          val kebab = PropertyNameContext(outputStyle = PropertyNameStyle.KebabCase).transformRecord(context.question)
          val annotation = Json.obj("input_type" -> Json.fromString("inert\nvalue"), "source-revision" -> Json.True,
            "values" -> Json.arr(Json.obj("continuation_id" -> Json.fromString("literal"))))
          val annotated = raw.mapObject(_.add("annotations", annotation))
          val matching = raw.mapObject(_.add("input_type", raw.hcursor.downField("inputType").focus.get))
          val differing = raw.mapObject(_.add("input_type", Json.fromString("foreign-type")))
          When("the " + context.purpose + " consumed question uses canonical snake kebab or duplicate declared names")
          val equivalent = Vector(context.question, snake, kebab, record(matching), record(differing), record(annotated))
            .map(q => take(_prepare_(context.copy(question = q))))
          Then("the unchanged native row and v7 payload agree exactly and inert annotations keep their literal values")
          equivalent.foreach { prepared =>
            prepared.recordIdentity shouldBe canonical.recordIdentity
            json(prepared.record) shouldBe json(canonical.record)
            observe(evidence(prepared), purpose, target).isSuccess shouldBe true
          }
          org.simplemodeling.textus.smworkflow.preparation.NativeHumanDecisionQuestionWire.normalize(annotated, purpose)
            .hcursor.downField("annotations").focus shouldBe Some(annotation)
          Given("the " + context.purpose + " raw JSON has malformed canonical values beside valid aliases")
          val protectednames = Vector("runId", "continuationId", "requiredOperation", "inputType", "resultType")
          When("Core canonical Record conversion omits null object attributes before preparation")
          val presentinvalid = protectednames.flatMap { key =>
            val alias = PropertyNameContext.default.aliases(key)(1)
            Vector(Json.Null, Json.True, Json.fromString("")).map { invalid =>
              val supplied = raw.mapObject(_.add(alias, raw.hcursor.downField(key).focus.get).add(key, invalid))
              (key, invalid, supplied, _question_record_(supplied))
            }
          }
          Then("Record omission allows the declared alias while direct finite JSON normalization retains canonical null")
          presentinvalid.filter(_._2.isNull).foreach { (key, _, supplied, question) =>
            withClue(context.purpose + " canonical null " + key + ": ") {
              question.fields.find(_.key == key) shouldBe None
              json(question).hcursor.downField(key).focus shouldBe None
              evidence(take(_prepare_(context.copy(question = question)))).binding shouldBe evidence(canonical).binding
              org.simplemodeling.textus.smworkflow.preparation.NativeHumanDecisionQuestionWire.normalize(supplied, purpose)
                .hcursor.downField(key).focus shouldBe Some(Json.Null)
            }
          }
          def _malformed_(path: List[String], invalid: Json): (String, Record) =
            (path.mkString(".") + "=" + invalid.noSpaces, _question_record_(_replace_at_(raw, path, invalid)))
          val nested = Vector(_malformed_(List("handle", "workflowIdentity"), Json.Null),
            _malformed_(List("handle", "instanceIdentity"), Json.True),
            _malformed_(List("source"), Json.True)) ++ (purpose match {
            case Purpose.GoalHuman => Vector(
              _malformed_(List("source", "requestingResult", "identity"), Json.Null),
              _malformed_(List("source", "assignment", "sourceRevision"), Json.True),
              _malformed_(List("source", "assignment", "selectedPlan"), Json.Null))
            case Purpose.RepositoryHuman => Vector(
              _malformed_(List("source", "requestingResult", "identity"), Json.Null),
              _malformed_(List("source", "conflict", "conflictRevision"), Json.True))
            case _ => Vector(_malformed_(List("source", "context", "identity"), Json.Null))
          })
          When("present malformed canonical fields have valid aliases or nested required fields and input/result codecs are crossed")
          val malformedquestions = presentinvalid.filterNot(_._2.isNull).map { (key, invalid, _, question) =>
            (key + "=" + invalid.noSpaces + " beside valid alias", question)
          } ++ protectednames.map(key => _malformed_(List(key), Json.Null)) ++ nested ++
            Vector("inputType", "resultType").map(key => _malformed_(List(key), Json.fromString("foreign-type")))
          val failures = malformedquestions.map { (field, question) => (field, _prepare_(context.copy(question = question))) }
          Then("strict native selection refuses every malformed consumed value without alias rescue")
          failures.foreach { (field, result) => withClue(context.purpose + " " + field + ": ") {
            result.isSuccess shouldBe false
          }}
          Given("a structurally valid supplied question with a different run identity")
          val changed = context.copy(question = record(raw.mapObject(_.add("runId", Json.fromString("different-supplied-run")))))
          When("passive preparation encodes those supplied facts and the original native target observes them")
          val changedrow = take(_prepare_(changed))
          Then("preparation preserves the supplied identity while native observation refuses the original target")
          evidence(changedrow).binding should not be evidence(canonical).binding
          observe(evidence(changedrow), purpose, target).isSuccess shouldBe false
          if (purpose == Purpose.RepositoryHuman) {
            evidence(canonical).binding.hcursor.downField("humanAttestation").downField("question").downField("source")
              .downField("conflict").downField("conflicts").downArray.get[String]("path").toOption shouldBe Some("literal\npath.scala")
            evidence(canonical).binding.hcursor.downField("humanAttestation").downField("answer").downField("resolutions")
              .downArray.get[String]("text").toOption shouldBe Some("Actual resolved contents\n")
          }
        }
        Consequence.unit
      }
    }
  }

  "E1 Goal issued context and strict wire intake" should afterWord("in spec:native-host-human-preparation, example:E1, rules:R1,R2,R3, phase:6") {
    "prepare only the supplied answer relative to the actual owning Decision" in {
      Given("a specification-issued Goal question and an explicit fixture answer, reference, worker and occurrence")
      withCore { call =>
        When("production output crosses strict generated normal JSON intake before public passive preparation")
        val output = take(NativeWorkflowOperationOutput.goalHumanC(_goal.goalQuestion, call.core))
        val decoded = take(V.GoalPhaseAdvanceResult.createC(wire(output)))
        val context = decoded.human.get.humanDecisionContext.get
        val prepared = take(Preparation.goalC(context, goalAnswer, reference, execution, participant, issuer, now.minusSeconds(60)))
        val expected = take(ThreeWorkflowExternalEvidencePorts.goalHumanBindingC(context.componentInstance,
          _goal.goalQuestion, _goal.goalAnswer, participant))
        Then("the existing v7 observation agrees on owner, slot, source, submitter, reference, worker and choice")
        context.componentInstance shouldBe call.core.component.get.core.instanceId.canonicalKey
        prepared.recordIdentity shouldBe NativeExternalEvidence.recordIdentity(Purpose.GoalHuman, evidenceKey)
        observe(evidence(prepared), Purpose.GoalHuman, expected).isSuccess shouldBe true
        context.question.getString("continuationId") shouldBe Some("human-slot")
        Consequence.unit
      }
    }
  }

  "E2 Repository literal conflict and answer" should afterWord("in spec:native-host-human-preparation, example:E2, rules:R1,R2,R3, phase:6") {
    "retain native paths and resolution contents exactly through strict nested Record intake" in {
      Given("the fixture Repository question with a literal newline path and explicit ProvideResolution text")
      withCore { call =>
        val original = _goal.repositoryQuestion.current.asInstanceOf[WorkflowContinuation.Decision[RepositorySyncHumanConflict.Input]]
        val input = original.request.input.get.value
        val changed = input.copy(conflict = input.conflict.copy(conflicts = input.conflict.conflicts.map(_.copy(path = "literal\npath.scala"))))
        val question = _goal.repositoryQuestion.copy(current = original.copy(request = original.request.copy(
          input = Some(TypedValue(RepositorySyncHumanConflict.inputCodec.typeIdentity, changed)))))
        When("the actual output is decoded and the supplied resolution is prepared")
        val output = take(NativeWorkflowOperationOutput.repositoryHumanC(question, call.core))
        val decoded = take(V.RepositorySyncAdvanceResult.createC(wire(output)))
        val context = decoded.human.get.humanDecisionContext.get
        val prepared = take(Preparation.repositoryC(context, repositoryAnswer, reference, execution, participant, issuer, now.minusSeconds(60)))
        val expected = take(ThreeWorkflowExternalEvidencePorts.repositoryHumanBindingC(RepositorySyncHumanConflict.AnswerContext(
          call.core.component.get.core.instanceId, question, _goal.repositoryAnswer, participant)))
        Then("the selected native binding preserves the literal path and newline resolution")
        observe(evidence(prepared), Purpose.RepositoryHuman, expected).isSuccess shouldBe true
        evidence(prepared).binding.hcursor.downField("humanAttestation").downField("question").downField("source")
          .downField("conflict").downField("conflicts").downArray.get[String]("path").toOption shouldBe Some("literal\npath.scala")
        evidence(prepared).binding.hcursor.downField("humanAttestation").downField("answer").downField("resolutions")
          .downArray.get[String]("text").toOption shouldBe Some("Actual resolved contents\n")
        Consequence.unit
      }
    }
  }

  "E3 Split Scope and Reasoning selected contexts" should afterWord("in spec:native-host-human-preparation, example:E3, rules:R1,R2,R3, phase:6") {
    "prepare distinct consumed question targets without using inert frontiers as public outputs" in {
      Given("scope-only passive fixtures with deliberately absent unused frontiers; Direct Operation consumers own complete public output proof")
      withCore { call =>
        When("the actual Decision projections cross the generated context reader and prepare an explicit typed answer")
        val contexts = Vector(_split.scopeQuestion, _split.reasoningQuestion).map(q =>
          take(V.WorkflowHumanDecisionContext.createC(wire(take(NativeHumanDecisionContext.splitC(call.core, q))))))
        val prepared = contexts.map(c => take(Preparation.splitC(c, splitAnswer(), reference,
          execution, "executing-relay", issuer, now.minusSeconds(60))))
        val expected = Vector(_split.scopeQuestion, _split.reasoningQuestion).map(q =>
          take(ThreeWorkflowExternalEvidencePorts.splitAnswerBindingC(SplitPhaseHumanAnswerVerification.AnswerContext(
            call.core.component.get.core.instanceId, q, _split.answer(q), "executing-relay"))))
        Then("Scope and Reasoning retain different native sources and cannot substitute for one another")
        prepared.zip(expected).foreach { (row, target) => observe(evidence(row), Purpose.SplitAnswer, target).isSuccess shouldBe true }
        observe(evidence(prepared.head), Purpose.SplitAnswer, expected.last).isSuccess shouldBe false
        contexts.map(_.question.getString("inputType")).distinct.size shouldBe 2
        Consequence.unit
      }
    }
  }

  "E4 Selected direction alternatives" should afterWord("in spec:native-host-human-preparation, example:E4, rules:R2,R3,R6, phase:6") {
    "preserve selected ordinary Goal and native Repository and Split choices and discard unused rationale and resolutions" in {
      Given("actual selected fixture contexts, selected ordinary Goal choices and every declared Repository and Split answer alternative")
      withCore { call =>
        val goal = take(NativeHumanDecisionContext.goalC(call.core, _goal.goalQuestion))
        val repository = take(NativeHumanDecisionContext.repositoryC(call.core, _goal.repositoryQuestion))
        val split = take(NativeHumanDecisionContext.splitC(call.core, _split.scopeQuestion))
        val ordinarychoices = GoalPhaseOrderedHumanDecision.choices.filterNot(_ == "AuthorizeNewAttempt")
        When("public preparation consumes each selected answer while unused annotations vary")
        val goals = ordinarychoices.map(choice => evidence(take(Preparation.goalC(goal,
          goalAnswer.copy(choice = choice, rationale = null), reference, execution, participant, issuer, now))))
        val repositories = RepositorySyncHumanConflict.choices.map(choice => evidence(take(Preparation.repositoryC(repository,
          repositoryAnswer.copy(choice = choice, resolutions = if (choice == "ProvideResolution") repositoryAnswer.resolutions else null,
            rationale = null), reference, execution, participant, issuer, now))))
        val splits = SplitPhaseHumanDirectionAnswerContract.choices.map(choice => evidence(take(Preparation.splitC(split,
          splitAnswer(choice).copy(resolution = if (choice == "ProvideResolution") splitAnswer().resolution else
            Some(V.SplitPhasePartitionContextReference(null, null))), reference, execution, participant, issuer, now))))
        val inertnulls = Vector("Defer", "Cancel").map(choice => evidence(take(Preparation.splitC(split,
          splitAnswer(choice).copy(resolution = null), reference, execution, participant, issuer, now))))
        Then("selected ordinary Goal and declared Repository and Split choices survive and nonconsumed rationale or resolution bodies, including absent optional containers, remain inert")
        goals.map(_.binding.hcursor.downField("humanAttestation").downField("answer").get[String]("choice").toOption.get) shouldBe ordinarychoices
        repositories.map(_.binding.hcursor.downField("humanAttestation").downField("answer").get[String]("choice").toOption.get) shouldBe RepositorySyncHumanConflict.choices
        splits.map(_.binding.hcursor.downField("splitAnswerAttestation").downField("answer").get[String]("choice").toOption.get) shouldBe SplitPhaseHumanDirectionAnswerContract.choices
        repositories.tail.foreach(_.binding.hcursor.downField("humanAttestation").downField("answer").downField("resolutions").focus shouldBe Some(Json.arr()))
        inertnulls.map(_.binding) shouldBe splits.tail.map(_.binding)
        splits.tail.foreach(_.binding.hcursor.downField("splitAnswerAttestation").downField("answer").downField("resolution").focus shouldBe Some(Json.Null))
        Consequence.unit
      }
    }
  }

  "E5 Missing consumed inputs and inert annotations" should afterWord("in spec:native-host-human-preparation, example:E5, rules:R1,R3,R6, phase:6") {
    "refuse missing identities and crossed codecs without manufacturing values or renewing consent" in {
      Given("an actual fixture context, missing selected-field variants and unrelated copied annotations")
      withCore { call =>
        val context = take(NativeHumanDecisionContext.goalC(call.core, _goal.goalQuestion))
        val raw = json(context.question)
        val rootkeys = Vector("handle", "runId", "continuationId", "requiredOperation", "operation", "inputType", "resultType", "completionOperation", "source")
        val variants = Vector(null, context.copy(componentInstance = ""), context.copy(question = null),
          context.copy(purpose = Purpose.RepositoryHuman.identity)) ++ rootkeys.flatMap(k => Vector(
            context.copy(question = record(raw.mapObject(_.remove(k)))), context.copy(question = record(raw.mapObject(_.add(k, Json.True)))))) ++
          Vector(context.copy(question = record(raw.mapObject(_.add("inputType", Json.fromString("foreign-type"))))),
            context.copy(question = record(raw.mapObject(_.add("resultType", Json.fromString("foreign-answer"))))))
        val annotated = context.copy(question = record(raw.mapObject(_.add("expectedRevision", Json.True)
          .add("modelSnapshot", Json.False).add("presentation", Json.Null).add("grant", Json.True))))
        val repository = take(NativeHumanDecisionContext.repositoryC(call.core, _goal.repositoryQuestion))
        val split = take(NativeHumanDecisionContext.splitC(call.core, _split.scopeQuestion))
        val crossed = Vector(
          Preparation.repositoryC(repository.copy(purpose = Purpose.GoalHuman.identity), repositoryAnswer, reference, execution, participant, issuer, now),
          Preparation.repositoryC(repository.copy(question = record(json(repository.question).mapObject(
            _.add("resultType", Json.fromString(GoalPhaseOrderedHumanDecision.answerCodec.typeIdentity))))),
            repositoryAnswer, reference, execution, participant, issuer, now),
          Preparation.splitC(split.copy(purpose = Purpose.GoalHuman.identity), splitAnswer(), reference, execution, participant, issuer, now),
          Preparation.splitC(split.copy(question = record(json(split.question).mapObject(_.add("inputType", Json.fromString("foreign-type"))))),
            splitAnswer(), reference, execution, participant, issuer, now),
          Preparation.goalC(context, goalAnswer.copy(choice = "Unknown"), reference, execution, participant, issuer, now),
          Preparation.repositoryC(repository, repositoryAnswer.copy(choice = "Unknown"), reference, execution, participant, issuer, now),
          Preparation.repositoryC(repository, repositoryAnswer.copy(resolutions = null), reference, execution, participant, issuer, now),
          Preparation.splitC(split, splitAnswer().copy(resolution = None), reference, execution, participant, issuer, now))
        val decision = _goal.goalQuestion.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]]
        val missingdecisions = Vector(_goal.goalQuestion.copy(current = null), _goal.goalQuestion.copy(handle = null),
          _goal.goalQuestion.copy(current = decision.copy(request = null)),
          _goal.goalQuestion.copy(current = decision.copy(request = decision.request.copy(input = None))),
          _goal.goalQuestion.copy(current = decision.copy(request = decision.request.copy(resultType = None))))
        When("public preparation and owning projection receive missing or mistyped consumed fields")
        val failed = variants.map(Preparation.goalC(_, goalAnswer, reference, execution, participant, issuer, now)) ++ Vector(
          Preparation.goalC(context, goalAnswer, null, execution, participant, issuer, now),
          Preparation.goalC(context, goalAnswer, reference.copy(identity = ""), execution, participant, issuer, now),
          Preparation.goalC(context, goalAnswer, reference.copy(revision = ""), execution, participant, issuer, now),
          Preparation.goalC(context, goalAnswer, reference, null, participant, issuer, now),
          Preparation.goalC(context, goalAnswer, reference, execution.copy(workerIdentity = None), participant, issuer, now),
          Preparation.goalC(context, goalAnswer, reference, execution.copy(workerIdentity = Some("")), participant, issuer, now),
          Preparation.goalC(context, goalAnswer, reference, execution, "", issuer, now),
          Preparation.goalC(context, goalAnswer, reference, execution, participant, "", now),
          Preparation.goalC(context, goalAnswer, reference, execution, participant, issuer, null))
        val missing = missingdecisions.map(NativeHumanDecisionContext.goalC(call.core, _)) ++ Vector(
          NativeHumanDecisionContext.goalC(call.core, null),
          NativeHumanDecisionContext.goalC(call.core.copy(component = None), _goal.goalQuestion))
        val first = take(Preparation.goalC(context, goalAnswer, reference, execution, participant, issuer, now))
        val second = take(Preparation.goalC(annotated, goalAnswer.copy(rationale = "other unused explanation"), reference,
          execution.copy(modelIdentity = Some("unused-model")), participant, issuer, now))
        Then("every missing consumed input fails and unused annotations leave the same selected historical input")
        failed.foreach(_.isSuccess shouldBe false)
        crossed.foreach(_.isSuccess shouldBe false)
        missing.foreach(_.isSuccess shouldBe false)
        first.record.getString(NativeExternalEvidence.storageField) shouldBe second.record.getString(NativeExternalEvidence.storageField)
        Consequence.unit
      }
    }
  }

  "Review remaining Plan direction preparation" should {
    "bind the added condition actual issue candidate checks and genuine selected worker without creating an old answer" in {
      Given("a fixture-owned read-only Review target and a new independently observed Human directive")
      val target = GoalPhaseReviewRepairAdoptionFixture.target
      val description = V.GoalPhaseRemainingPlanDescriptionResult(target.sessionId, "adoption-required",
        "Fixture-owned Review continuation", "component", GoalPhaseRemainingPlanAdoption.targetRecord(target),
        GoalPhaseRemainingPlanAdoption.adoptionOperation)
      val reference = V.WorkflowArtifactReference("fixture-new-review-direction", "1")
      val input = V.GoalPhaseRemainingPlanAdoptionInput(target.sessionId, description.target,
        GoalPhasePlanRevision.publicProposal(Some(target.captured.proposal)).get,
        "Fixture Human adopts this additional required Step condition", reference)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      def _prepare_(described: V.GoalPhaseRemainingPlanDescriptionResult, supplied: V.GoalPhaseRemainingPlanAdoptionInput,
        executed: V.WorkflowExecutionEvidence = execution) =
        org.simplemodeling.textus.smworkflow.preparation.NativeRemainingPlanPreparation.prepareC(described, supplied,
          reference, executed, "participant", issuer, now)
      When("passive preparation receives the exact description or crossed target, proposal and worker")
      val prepared = take(_prepare_(description, input))
      val saved = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      val crossedtarget = target.copy(reviewRepair = target.reviewRepair.map(_.copy(issueIdentity = "issue-4")))
      val crossed = Vector(_prepare_(description, input.copy(target = GoalPhaseRemainingPlanAdoption.targetRecord(crossedtarget))),
        _prepare_(description, input.copy(proposal = GoalPhasePlanRevision.publicProposal(Some(
          target.captured.proposal.copy(reason = "Other direction"))).get)),
        _prepare_(description, input, execution.copy(workerIdentity = Some("unrelated-worker"))))
      Then("only the exact new direction is bound and the saved evidence contains no old-question answer")
      crossed.foreach(_.isSuccess shouldBe false)
      saved.purpose shouldBe Purpose.RemainingPlanAdoption.identity
      val bound = saved.binding.hcursor.downField("remainingPlanAdoption").get[GoalPhaseRemainingPlanAdoption.Target]("target").toOption.get
      bound shouldBe target
      saved.binding.hcursor.downField("humanAttestation").focus shouldBe None
      prepared.recordIdentity shouldBe NativeExternalEvidence.recordIdentity(Purpose.RemainingPlanAdoption,
        ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision))
    }
  }

  "E6 Independent applicability and managed reading" should afterWord("in spec:native-host-human-preparation, example:E6, rules:R4,R5, phase:6") {
    "require the existing current-call Reader permission and exact selected binding after fixture provisioning" in {
      Given("a fixture-issued Goal question and an independently configured native Reader")
      var retained = Option.empty[(NativeExternalEvidence.Reader, ActionCall.Core, Json)]
      withCore { call =>
        val context = take(NativeHumanDecisionContext.goalC(call.core, _goal.goalQuestion))
        val prepared = take(Preparation.goalC(context, goalAnswer, reference, execution, participant, issuer, now.minusSeconds(60)))
        val saved = evidence(prepared)
        val target = take(ThreeWorkflowExternalEvidencePorts.goalHumanBindingC(context.componentInstance,
          _goal.goalQuestion, _goal.goalAnswer, participant))
        val alter = _goal.alter
        val q = Vector("humanAttestation", "question")
        val a = Vector("humanAttestation", "answer")
        val variants = Vector(target.mapObject(_.add("componentInstance", Json.fromString("foreign-owner"))),
          alter(target, Vector("humanAttestation"))(_.mapObject(_.add("submittingParticipant", Json.fromString("foreign-submitter")))),
          alter(target, a)(_.mapObject(_.add("worker", Json.fromString("foreign-worker")))),
          alter(target, a :+ "reference")(_.mapObject(_.add("identity", Json.fromString("foreign-answer")))),
          alter(target, a)(_.mapObject(_.add("choice", Json.fromString("Cancel")))),
          alter(target, q)(_.mapObject(_.add("continuationId", Json.fromString("foreign-slot")))),
          alter(target, q ++ Vector("source", "assignment"))(_.mapObject(_.add("sourceRevision", Json.fromString("foreign-source")))))
        val reader = take(NativeExternalEvidence.bindC(call.core.component.get.subsystem.get.configuration,
          GoalPhaseRuntimeParameters.Persistence(_configuration_, _collection_("instances"), _collection_("continuations"))))
        When("preparation returns a passive row and the existing Reader checks absence and crossed facts")
        val absent = take(reader.loadC(call.core, Purpose.GoalHuman, evidenceKey, target))
        val refused = variants.map(observe(saved, Purpose.GoalHuman, _))
        Then("preparation created no row and foreign selected facts, issuer or future occurrence fail")
        absent shouldBe None
        refused.foreach(_.isSuccess shouldBe false)
        observe(saved.copy(issuer = "foreign-issuer"), Purpose.GoalHuman, target).isSuccess shouldBe false
        observe(saved.copy(occurredAt = now.plusSeconds(1).toString), Purpose.GoalHuman, target).isSuccess shouldBe false
        Vector(Purpose.GoalReview, Purpose.StepClosure, Purpose.PhaseClosure, Purpose.RepositoryEffect).foreach(p =>
          observe(saved, p, target).isSuccess shouldBe false)
        Given("the specification explicitly provisions the returned row with its existing managed Create permission")
        take(call.createC("host_evidence", prepared.recordIdentity, prepared.record))
        When("the live managed Reader and a context-free call try to load that physical row")
        val accepted = take(reader.loadC(call.core, Purpose.GoalHuman, evidenceKey, target))
        val contextfree = reader.loadC(null, Purpose.GoalHuman, evidenceKey, target)
        Then("only the current granted managed call can read the selected Human input")
        accepted.map(_.evidence.binding) shouldBe Some(saved.binding)
        contextfree.isSuccess shouldBe false
        retained = Some((reader, call.core, target))
        Consequence.unit
      }
      When("the original managed call has ended")
      val (reader, core, target) = retained.get
      val ended = reader.loadC(core, Purpose.GoalHuman, evidenceKey, target)
      Then("retained context does not retain read permission; normal Submit and separate Advance remain in the public Operation examples")
      ended.isSuccess shouldBe false
    }
  }
}
