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
import org.simplemodeling.textus.smworkflow.host.NativeHostHumanPreparation as Preparation
import org.simplemodeling.textus.smworkflow.value as V
import NativeHostEvidence.{Purpose, Evidence}
import SplitPhaseExplicitPartitionFixture.take

/** Shared specification-owned inputs, never a genuine Human/review issuer.
  * @since   Oct. 14, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object NativeHostPreparationFixture {
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
  def wire(value: RecordPresentable): Record = record(json(value.toRecord()))
  def evidence(prepared: Preparation.Prepared): Evidence =
    take(NativeHostEvidence.decodeStorageC(prepared.record.getString(NativeHostEvidence.storageField).get))
  def evidenceKey: String = ThreeWorkflowHostEvidencePorts.key(reference.identity, reference.revision)
  val declaration = NativeHostEvidence.Declaration("workflow", DataStore.CollectionId.EntityStore(
    EntityCollectionId("test", "host", "human_evidence")), issuer)
  def observe(value: Evidence, purpose: Purpose, target: Json): Consequence[NativeHostEvidence.Observed] =
    NativeHostEvidence.observeC(value, declaration, purpose, evidenceKey, target, now)
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
  def goalWork(kind: String = "ReviewStep") = work("GoalPhaseWorkflow", NativeHostHumanEvidenceFixture.work.copy(
    sourceRevision = "7", kind = kind, candidate = Some(NativeHostHumanEvidenceFixture.snapshot),
    checksReceipt = Some("focused"), review = Some(GoalPhaseNativeReview.Context(Vector("one.scala"), "fixture diff", Vector(receipt)))),
    GoalPhaseNativeWork.inputCodec.typeIdentity, GoalPhaseNativeWork.resultCodec.typeIdentity, GoalPhaseNativeRun.completionOperation)
  def repositoryWork = work("RepositorySyncWorkflow", RepositorySyncNativeMergeReview.Request("instance", "7", "a" * 40,
    "c" * 40, "b" * 40, Vector("one.scala"), "fixture local diff", "fixture remote diff", "required", "focused",
    Vector("focused"), Vector(receipt)), RepositorySyncNativeMergeReview.inputCodec.typeIdentity,
    RepositorySyncNativeMergeReview.resultCodec.typeIdentity, RepositorySyncNativeMergeReviewContinuation.completionOperation)
}

private[admission] trait NativeHostPreparationManagedFixture extends ThreeWorkflowManagedRepositoryFixture {
  protected def withCore(body: Setup => Consequence[Unit]): Unit = {
    val parent = Files.createDirectories(Path.of("target/sm-workflow/work/human-preparation").toAbsolutePath)
    val root = Files.createTempDirectory(parent, "specification-")
    _with_runtime_(root, "UpToDate") { (_, _, _, prepare) => prepare(body) }
  }
}

/** Issued fixture questions and explicit fixture answers exercise passive preparation.
  * @since   Oct. 14, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
final class NativeHostHumanPreparationSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeHostPreparationManagedFixture {
  import NativeHostPreparationFixture.*
  private val _goal = NativeHostHumanEvidenceFixture
  private val _split = NativeHostSplitAnswerEvidenceFixture

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
        val contexts = Vector(take(NativeHostHumanContext.goalC(call.core, _goal.goalQuestion)),
          take(NativeHostHumanContext.repositoryC(call.core, repositoryquestion)),
          take(NativeHostHumanContext.splitC(call.core, _split.scopeQuestion)),
          take(NativeHostHumanContext.splitC(call.core, _split.reasoningQuestion)))
        val targets = Vector(
          take(ThreeWorkflowHostEvidencePorts.goalHumanBindingC(contexts.head.componentInstance, _goal.goalQuestion, _goal.goalAnswer, participant)),
          take(ThreeWorkflowHostEvidencePorts.repositoryHumanBindingC(RepositorySyncHumanConflict.AnswerContext(
            call.core.component.get.core.instanceId, repositoryquestion, _goal.repositoryAnswer, participant)))) ++
          Vector(_split.scopeQuestion, _split.reasoningQuestion).map(q => take(ThreeWorkflowHostEvidencePorts.splitAnswerBindingC(
            SplitPhaseHumanAnswerVerification.AnswerContext(call.core.component.get.core.instanceId, q, _split.answer(q), participant))))
        def _prepare_(context: V.WorkflowHostHumanContext): Consequence[Preparation.Prepared] = context.purpose match {
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
        // E7 supplies an actual Record with present nulls. The ordinary decoder
        // used by record()/wire() deliberately keeps its existing behavior.
        def _question_value_(value: Json): Any = value.fold[Any](null, boolean => boolean,
          number => number.toBigDecimal.get, string => string, array => array.map(_question_value_),
          fields => Record.createFull(fields.toVector.map { case (key, field) => key -> _question_value_(field) }))
        def _question_record_(value: Json): Record = _question_value_(value).asInstanceOf[Record]
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
          org.simplemodeling.textus.smworkflow.host.NativeHostHumanQuestionWire.normalize(annotated, purpose)
            .hcursor.downField("annotations").focus shouldBe Some(annotation)
          Given("the " + context.purpose + " supplied Record retains malformed canonical values even beside valid aliases")
          val protectednames = Vector("runId", "continuationId", "requiredOperation", "inputType", "resultType")
          When("the scenario constructs null-preserving question Records rather than passing them through the ordinary decoder")
          val presentinvalid = protectednames.flatMap { key =>
            val alias = PropertyNameContext.default.aliases(key)(1)
            Vector(Json.Null, Json.True, Json.fromString("")).map { invalid =>
              val supplied = raw.mapObject(_.add(alias, raw.hcursor.downField(key).focus.get).add(key, invalid))
              (key, invalid, supplied, _question_record_(supplied))
            }
          }
          Then("every canonical null is still present in the supplied Record, its JSON and the finite question normalizer")
          presentinvalid.filter(_._2.isNull).foreach { (key, _, supplied, question) =>
            withClue(context.purpose + " canonical null " + key + ": ") {
              val field = question.fields.find(_.key == key)
              field.isDefined shouldBe true
              (field.get.value.single == null) shouldBe true
              json(question).hcursor.downField(key).focus shouldBe Some(Json.Null)
              org.simplemodeling.textus.smworkflow.host.NativeHostHumanQuestionWire.normalize(supplied, purpose)
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
          val malformedquestions = presentinvalid.map { (key, invalid, _, question) =>
            (key + "=" + invalid.noSpaces + " beside valid alias", question)
          } ++ nested ++ Vector("inputType", "resultType").map(key => _malformed_(List(key), Json.fromString("foreign-type")))
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
        val context = decoded.human.get.hostContext.get
        val prepared = take(Preparation.goalC(context, goalAnswer, reference, execution, participant, issuer, now.minusSeconds(60)))
        val expected = take(ThreeWorkflowHostEvidencePorts.goalHumanBindingC(context.componentInstance,
          _goal.goalQuestion, _goal.goalAnswer, participant))
        Then("the existing v7 observation agrees on owner, slot, source, submitter, reference, worker and choice")
        context.componentInstance shouldBe call.core.component.get.core.instanceId.canonicalKey
        prepared.recordIdentity shouldBe NativeHostEvidence.recordIdentity(Purpose.GoalHuman, evidenceKey)
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
        val context = decoded.human.get.hostContext.get
        val prepared = take(Preparation.repositoryC(context, repositoryAnswer, reference, execution, participant, issuer, now.minusSeconds(60)))
        val expected = take(ThreeWorkflowHostEvidencePorts.repositoryHumanBindingC(RepositorySyncHumanConflict.AnswerContext(
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
          take(V.WorkflowHostHumanContext.createC(wire(take(NativeHostHumanContext.splitC(call.core, q))))))
        val prepared = contexts.map(c => take(Preparation.splitC(c, splitAnswer(), reference,
          execution, "executing-relay", issuer, now.minusSeconds(60))))
        val expected = Vector(_split.scopeQuestion, _split.reasoningQuestion).map(q =>
          take(ThreeWorkflowHostEvidencePorts.splitAnswerBindingC(SplitPhaseHumanAnswerVerification.AnswerContext(
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
    "preserve all native choices and discard unused rationale and resolutions" in {
      Given("actual selected fixture contexts and every declared Goal, Repository and Split answer alternative")
      withCore { call =>
        val goal = take(NativeHostHumanContext.goalC(call.core, _goal.goalQuestion))
        val repository = take(NativeHostHumanContext.repositoryC(call.core, _goal.repositoryQuestion))
        val split = take(NativeHostHumanContext.splitC(call.core, _split.scopeQuestion))
        When("public preparation consumes each selected answer while unused annotations vary")
        val goals = GoalPhaseOrderedHumanDecision.choices.map(choice => evidence(take(Preparation.goalC(goal,
          goalAnswer.copy(choice = choice, rationale = null), reference, execution, participant, issuer, now))))
        val repositories = RepositorySyncHumanConflict.choices.map(choice => evidence(take(Preparation.repositoryC(repository,
          repositoryAnswer.copy(choice = choice, resolutions = if (choice == "ProvideResolution") repositoryAnswer.resolutions else null,
            rationale = null), reference, execution, participant, issuer, now))))
        val splits = SplitPhaseHumanDirectionAnswerContract.choices.map(choice => evidence(take(Preparation.splitC(split,
          splitAnswer(choice).copy(resolution = if (choice == "ProvideResolution") splitAnswer().resolution else
            Some(V.SplitPhasePartitionContextReference(null, null))), reference, execution, participant, issuer, now))))
        val inertnulls = Vector("Defer", "Cancel").map(choice => evidence(take(Preparation.splitC(split,
          splitAnswer(choice).copy(resolution = null), reference, execution, participant, issuer, now))))
        Then("all declared choices survive and nonconsumed rationale or resolution bodies, including absent optional containers, remain inert")
        goals.map(_.binding.hcursor.downField("humanAttestation").downField("answer").get[String]("choice").toOption.get) shouldBe GoalPhaseOrderedHumanDecision.choices
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
        val context = take(NativeHostHumanContext.goalC(call.core, _goal.goalQuestion))
        val raw = json(context.question)
        val rootkeys = Vector("handle", "runId", "continuationId", "requiredOperation", "operation", "inputType", "resultType", "completionOperation", "source")
        val variants = Vector(null, context.copy(componentInstance = ""), context.copy(question = null),
          context.copy(purpose = Purpose.RepositoryHuman.identity)) ++ rootkeys.flatMap(k => Vector(
            context.copy(question = record(raw.mapObject(_.remove(k)))), context.copy(question = record(raw.mapObject(_.add(k, Json.True)))))) ++
          Vector(context.copy(question = record(raw.mapObject(_.add("inputType", Json.fromString("foreign-type"))))),
            context.copy(question = record(raw.mapObject(_.add("resultType", Json.fromString("foreign-answer"))))))
        val annotated = context.copy(question = record(raw.mapObject(_.add("expectedRevision", Json.True)
          .add("modelSnapshot", Json.False).add("presentation", Json.Null).add("grant", Json.True))))
        val repository = take(NativeHostHumanContext.repositoryC(call.core, _goal.repositoryQuestion))
        val split = take(NativeHostHumanContext.splitC(call.core, _split.scopeQuestion))
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
        val missing = missingdecisions.map(NativeHostHumanContext.goalC(call.core, _)) ++ Vector(
          NativeHostHumanContext.goalC(call.core, null),
          NativeHostHumanContext.goalC(call.core.copy(component = None), _goal.goalQuestion))
        val first = take(Preparation.goalC(context, goalAnswer, reference, execution, participant, issuer, now))
        val second = take(Preparation.goalC(annotated, goalAnswer.copy(rationale = "other unused explanation"), reference,
          execution.copy(modelIdentity = Some("unused-model")), participant, issuer, now))
        Then("every missing consumed input fails and unused annotations leave the same selected historical input")
        failed.foreach(_.isSuccess shouldBe false)
        crossed.foreach(_.isSuccess shouldBe false)
        missing.foreach(_.isSuccess shouldBe false)
        first.record.getString(NativeHostEvidence.storageField) shouldBe second.record.getString(NativeHostEvidence.storageField)
        Consequence.unit
      }
    }
  }

  "E6 Independent applicability and managed reading" should afterWord("in spec:native-host-human-preparation, example:E6, rules:R4,R5, phase:6") {
    "require the existing current-call Reader permission and exact selected binding after fixture provisioning" in {
      Given("a fixture-issued Goal question and an independently configured native Reader")
      var retained = Option.empty[(NativeHostEvidence.Reader, ActionCall.Core, Json)]
      withCore { call =>
        val context = take(NativeHostHumanContext.goalC(call.core, _goal.goalQuestion))
        val prepared = take(Preparation.goalC(context, goalAnswer, reference, execution, participant, issuer, now.minusSeconds(60)))
        val saved = evidence(prepared)
        val target = take(ThreeWorkflowHostEvidencePorts.goalHumanBindingC(context.componentInstance,
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
        val reader = take(NativeHostEvidence.bindC(call.core.component.get.subsystem.get.configuration,
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
