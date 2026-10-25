package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.protocol.OperationResponseFormatter
import org.goldenport.protocol.{Property, Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.RecordPresentable
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.{NativeExternalEvidenceInput as Main, NativeHumanDecisionPreparation, NativeReviewPreparation}
import org.simplemodeling.textus.smworkflow.value as V
import NativeExternalEvidence.Purpose
import SplitPhaseExplicitPartitionFixture.take

/** Typed finite tool input fixture; files contain only specification facts.
  * @since   Oct. 14, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object NativeExternalEvidenceToolFixture {
  import NativePreparationFixture.*
  val collection = "entity:smworkflow.cli.host_evidence"
  def normalCommandJson(value: RecordPresentable): String =
    OperationResponseFormatter.toResponse(Request.ofOperation("specificationReview").copy(properties = List(
      Property("textus.output.format", "json", None), Property("textus.output.shape", "data", None))),
      OperationResponse.RecordResponse(value.toRecord()), RunMode.Command) match {
      case Response.Json(text) => text
      case other => throw new IllegalStateException("Specification formatter returned " + other)
    }
  def arguments(family: String, kind: String, participantIdentity: String = participant): Map[String, String] =
    Map("family" -> family, "kind" -> kind, "request" -> "request.json", "result" -> "result.json",
      "reference" -> "reference.json", "execution-evidence" -> "execution.json", "issuer" -> issuer,
      "occurred-at" -> now.minusSeconds(60).toString, "collection" -> collection) ++
      (if (Set("human", "candidate-recovery", "candidate-observation-continuation")(kind))
        Map("participant" -> participantIdentity) else Map.empty[String, String])
  def files(request: RecordPresentable, answer: RecordPresentable, resultReference: V.WorkflowArtifactReference = reference,
    executionEvidence: V.WorkflowExecutionEvidence = execution): Map[String, String] = Map(
    "request.json" -> take(request.toRecord().toJsonStringC), "result.json" -> take(answer.toRecord().toJsonStringC),
    "reference.json" -> take(resultReference.toRecord().toJsonStringC), "execution.json" -> take(executionEvidence.toRecord().toJsonStringC))
  def prepareRecord(family: String, kind: String, request: RecordPresentable, answer: RecordPresentable,
    participantIdentity: String = participant, resultReference: V.WorkflowArtifactReference = reference,
    executionEvidence: V.WorkflowExecutionEvidence = execution): Consequence[Record] = {
    val selected = files(request, answer, resultReference, executionEvidence)
    Main.prepareC(arguments(family, kind, participantIdentity), path => selected.get(path)
      .map(Consequence.success).getOrElse(Consequence.stateConflict("Specification file absent")))
  }
  def prepare(family: String, kind: String, request: RecordPresentable, answer: RecordPresentable,
    participantIdentity: String = participant, resultReference: V.WorkflowArtifactReference = reference,
    executionEvidence: V.WorkflowExecutionEvidence = execution): Consequence[String] =
    prepareRecord(family, kind, request, answer, participantIdentity, resultReference, executionEvidence).flatMap(_.toJsonStringC)
  def prepareNormalCommand(family: String, kind: String, request: RecordPresentable, answer: RecordPresentable,
    participantIdentity: String = participant, resultReference: V.WorkflowArtifactReference = reference,
    executionEvidence: V.WorkflowExecutionEvidence = execution,
    transformRequest: String => String = identity): Consequence[String] = {
    val selected = files(request, answer, resultReference, executionEvidence)
      .updated("request.json", transformRequest(normalCommandJson(request)))
    Main.prepareC(arguments(family, kind, participantIdentity), path => selected.get(path)
      .map(Consequence.success).getOrElse(Consequence.stateConflict("Specification file absent"))).flatMap(_.toJsonStringC)
  }
  def evidence(value: Record): NativeExternalEvidence.Evidence = evidence(take(value.toJsonStringC))
  def row(value: Record): Json = row(take(value.toJsonStringC))
  def evidence(jsonText: String): NativeExternalEvidence.Evidence = {
    val payload = io.circe.parser.parse(jsonText).toOption.get.hcursor.downField("datastore").downArray
      .downField("records").downArray.get[String](NativeExternalEvidence.storageField).toOption.get
    take(NativeExternalEvidence.decodeStorageC(payload))
  }
  def row(jsonText: String): Json = io.circe.parser.parse(jsonText).toOption.get.hcursor
    .downField("datastore").downArray.downField("records").downArray.focus.get
}

/** Passive input specifications execute only during parent-selected validation.
  * Fixture success is not genuine independent review, Human consent or task completion.
  * @since   Oct. 14, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeExternalEvidenceInputSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativePreparationManagedFixture {
  import NativePreparationFixture.*
  private val _tool = NativeExternalEvidenceToolFixture
  private val _goal_result = V.GoalPhaseWorkResult("accept", "Fixture review", Vector.empty, None, None, None, None, None, None, None, None, dependencyValidation = None)
  private val _repository_result = V.RepositorySyncNativeMergeReviewResult("accept", "Fixture merge review", Vector.empty)

  "T1 Own Goal review input" should afterWord("in spec:native-host-evidence-tool, example:T1, rules:R1,R2,R3,R4, phase:6") {
    "export selected ReviewStep and ReviewPhase rows identical to public passive preparation" in {
      Given("specification-issued Goal ReviewStep and ReviewPhase outputs with independently supplied fixture review DTOs")
      withCore { call =>
        val outputs = Vector("ReviewStep", "ReviewPhase").map(kind =>
          take(NativeWorkflowOperationOutput.goalWorkRequestC(goalWork(kind), call.core)))
        When("the finite typed tool reads each actual output and all explicit host metadata")
        val seeds = outputs.map(o => take(_tool.prepareRecord("GoalPhase", "review", o, _goal_result)))
        val rows = outputs.map(o => take(NativeReviewPreparation.goalC(o.work.get.review.get.reviewContext.get,
          _goal_result, reference, execution, issuer, now.minusSeconds(60))))
        Then("each single standard importer row preserves the prepared identity and existing encoded payload")
        seeds.zip(rows).foreach { (seed, prepared) =>
          _tool.row(seed).hcursor.get[String]("id").toOption shouldBe Some(prepared.recordIdentity)
          _tool.row(seed).hcursor.get[String](NativeExternalEvidence.storageField).toOption shouldBe prepared.record.getString(NativeExternalEvidence.storageField)
          _tool.evidence(seed).purpose shouldBe Purpose.GoalReview.identity
          io.circe.parser.parse(take(seed.toJsonStringC)).toOption.get.hcursor.downField("datastore").downArray.get[String]("collection").toOption shouldBe Some(_tool.collection)
        }
        Consequence.unit
      }
    }
  }

  "T2 Own and forwarded Repository review input" should afterWord("in spec:native-host-evidence-tool, example:T2, rules:R1,R2,R3,R4, phase:6") {
    "retain the actual child review context when Goal forwards a repository Request" in {
      Given("a specification-issued Repository WorkOrder and its unchanged public child forwarding into Goal")
      withCore { call =>
        val repository = take(NativeWorkflowOperationOutput.repositoryReviewRequestC(repositoryWork, call.core))
        val goal = NativeWorkflowOperationOutput.goalChild("outer-goal-session", repository)
        When("the tool consumes the actual direct and forwarded typed review Request")
        val direct = take(_tool.prepareRecord("RepositorySync", "review", repository, _repository_result))
        val child = take(_tool.prepareRecord("GoalPhase", "review", goal, _repository_result))
        val prepared = take(NativeReviewPreparation.repositoryC(repository.review.get.reviewContext.get,
          _repository_result, reference, execution, issuer, now.minusSeconds(60)))
        Then("the row keeps the child completion and payload rather than replacing IDs from the outer session")
        _tool.row(child) shouldBe _tool.row(direct)
        _tool.row(child).hcursor.get[String](NativeExternalEvidence.storageField).toOption shouldBe prepared.record.getString(NativeExternalEvidence.storageField)
        _tool.evidence(child).purpose shouldBe Purpose.RepositoryReview.identity
        _tool.evidence(child).binding.hcursor.downField("attestation").downField("result").get[String]("continuationId").toOption shouldBe
          Some(repository.review.get.reviewContext.get.completion.continuationId)
        Consequence.unit
      }
    }
  }

  "Typed purpose source public input" should {
    "retain the exact boxed sources and reject raw explicit null or unknown wrapper fields before generated decoding" in {
      Given("a marked actual Step WorkOrder and a generated public result with one typed original-purpose source")
      withCore { call =>
        val original = goalWork("ReviewStep")
        val order = original.current.asInstanceOf[org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation.WorkOrder[GoalPhaseNativeWork.Request]]
        val oldinput = order.request.input.get.value
        val input = oldinput.copy(purposeRepair = GoalPhasePurposeRepair.capture("ReviewStep", oldinput.stepIdentity, oldinput.selectedPlan))
        val issued = original.copy(current = order.copy(request = order.request.copy(input = Some(order.request.input.get.copy(value = input)))))
        val output = take(NativeWorkflowOperationOutput.goalWorkRequestC(issued, call.core))
        val source: GoalPhasePurposeRepair.Source = GoalPhasePurposeRepair.StepPurpose(1, input.stepIdentity.get,
          "The original purpose has an actual defect", Vector("one.scala"))
        val value = _goal_result.copy(outcome = "repair", summary = "Repair original purpose", findings = Vector("Missing original case"),
          repairFindingSources = GoalPhasePurposeRepair.publicSources(Some(Vector(source))))
        val files = _tool.files(output, value)
        val raw = io.circe.parser.parse(files("result.json")).toOption.get
        val malformed = Vector(Json.Null, Json.obj("sources" -> Json.arr(source.asJson), "unknown" -> Json.fromBoolean(true)),
          Json.obj("sources" -> Json.Null), Json.obj("sources" -> Json.arr(source.asJson.mapObject(_.add("conditionIdentity", Json.fromString("irrelevant"))))))
        When("the normal finite public adapter consumes the exact public payload and each malformed raw variant")
        val accepted = _tool.prepareRecord("GoalPhase", "review", output, value)
        val invalidresults = malformed.map(wrapper => raw.mapObject(_.add("repairFindingSources", wrapper))) ++ Vector(
          raw.mapObject(_.remove("repairFindingSources").add("repair_finding_sources", Json.Null)),
          raw.mapObject(_.add("repair_finding_sources", Json.Null)))
        val rejected = invalidresults.map { invalid =>
          val changed = files.updated("result.json", invalid.noSpaces)
          Main.prepareC(_tool.arguments("GoalPhase", "review"), path => Consequence.success(changed(path)))
        }
        Then("the evidence contains the native exact array while no null or unobserved field disappears into success")
        _tool.evidence(take(accepted)).binding.hcursor.downField("attestation").downField("result").downField("verdict")
          .get[Json]("repairFindingSources") shouldBe Right(Json.arr(source.asJson))
        rejected.zipWithIndex.foreach { (result, index) =>
          withClue("Malformed public source wrapper variant " + index + ": ") { result.isSuccess shouldBe false }
        }
        Consequence.unit
      }
    }
  }

  "T3 Explicit own and forwarded Human answers" should afterWord("in spec:native-host-evidence-tool, example:T3, rules:R1,R2,R3,R4,R5, phase:6") {
    "export Goal and Repository Human rows only with a supplied participant and actual answer" in {
      Given("actual production projections of fixture Goal and Repository Decisions and an unchanged forwarded Repository Human")
      withCore { call =>
        val goal = take(NativeWorkflowOperationOutput.goalHumanC(NativeHumanEvidenceFixture.goalQuestion, call.core))
        val repository = take(NativeWorkflowOperationOutput.repositoryHumanC(NativeHumanEvidenceFixture.repositoryQuestion, call.core))
        val child = NativeWorkflowOperationOutput.goalChild("outer-goal-session", repository)
        When("the typed adapter reads those Human outputs with explicit participant, answer and actual metadata")
        val own = take(_tool.prepareRecord("GoalPhase", "human", goal, goalAnswer))
        val direct = take(_tool.prepareRecord("RepositorySync", "human", repository, repositoryAnswer))
        val forwarded = take(_tool.prepareRecord("GoalPhase", "human", child, repositoryAnswer))
        val prepared = take(NativeHumanDecisionPreparation.goalC(goal.human.get.humanDecisionContext.get, goalAnswer,
          reference, execution, participant, issuer, now.minusSeconds(60)))
        val selectedfiles = _tool.files(goal, goalAnswer)
        val absentparticipant = Main.prepareC(_tool.arguments("GoalPhase", "human") - "participant", path =>
          Consequence.success(selectedfiles(path)))
        Then("only the supplied choices enter purpose-specific current-v7 rows; no participant is inferred or review effect issued")
        _tool.row(own).hcursor.get[String](NativeExternalEvidence.storageField).toOption shouldBe prepared.record.getString(NativeExternalEvidence.storageField)
        _tool.evidence(own).purpose shouldBe Purpose.GoalHuman.identity
        _tool.evidence(direct).purpose shouldBe Purpose.RepositoryHuman.identity
        _tool.row(forwarded) shouldBe _tool.row(direct)
        absentparticipant.isSuccess shouldBe false
        Consequence.unit
      }
    }
  }

  "T4 Split assigned Human selection" should afterWord("in spec:native-host-evidence-tool, example:T4, rules:R1,R2,R3,R4, phase:6") {
    "refuse Split review and unassigned outputs while managed Direct consumers exercise both full public Human outputs" in {
      Given("Split has Human preparation but no review kind, and an ordinary unassigned Wait output")
      val wait = V.SplitPhaseAdvanceResult("fixture-session", "Wait", "No assigned input", None, None, None,
        Some("waiting"), None, None, None, None, None, None, None, None, None, None)
      When("the tool receives unsupported review or a Human request without an assigned context")
      val review = _tool.prepareRecord("SplitPhase", "review", wait, splitAnswer())
      val absent = _tool.prepareRecord("SplitPhase", "human", wait, splitAnswer())
      Then("no successful seed is exported; the Direct suite supplies actual full Scope and Reasoning T4 cases for all declared choices")
      review.isSuccess shouldBe false
      absent.isSuccess shouldBe false
    }
  }

  "T5 Invalid explicit tool inputs" should afterWord("in spec:native-host-evidence-tool, example:T5, rules:R1,R2,R3,R4,R5,R6, phase:6") {
    "fail closed for malformed arguments files metadata and absent ambiguous or crossed assignments" in {
      Given("a valid fixture Goal Human output and explicit typed file map with bounded invalid variants")
      withCore { call =>
        val goal = take(NativeWorkflowOperationOutput.goalHumanC(NativeHumanEvidenceFixture.goalQuestion, call.core))
        val repository = take(NativeWorkflowOperationOutput.repositoryHumanC(NativeHumanEvidenceFixture.repositoryQuestion, call.core))
        val selectedfiles = _tool.files(goal, goalAnswer)
        val args = _tool.arguments("GoalPhase", "human")
        def _replace_(key: String, value: String): Map[String, String] = args.updated(key, value)
        def _read_(files: Map[String, String])(path: String): Consequence[String] = files.get(path)
          .map(Consequence.success).getOrElse(Consequence.stateConflict("Specification file absent"))
        val argumentvariants = Vector(args.updated("unknown", "value"), args - "family", args - "participant",
          _replace_("participant", ""), _replace_("issuer", ""), _replace_("occurred-at", "invalid-time"),
          _replace_("collection", "smworkflow.cli.host_evidence"), _replace_("collection", "entity:"),
          _replace_("family", "Unknown"), _replace_("kind", "Unknown"),
          _tool.arguments("GoalPhase", "review").updated("participant", participant))
        val missingworker = execution.copy(workerIdentity = None)
        val filevariants = Vector(selectedfiles - "request.json", selectedfiles.updated("request.json", "not-json"),
          selectedfiles.updated("reference.json", "{}"), selectedfiles.updated("execution.json", take(missingworker.toRecord().toJsonStringC)),
          selectedfiles.updated("reference.json", take(reference.copy(identity = "").toRecord().toJsonStringC)),
          selectedfiles.updated("result.json", "{\"choice\":true,\"rationale\":\"unused\"}"))
        val contexts = goal.human.get.humanDecisionContext.get
        val crossed = goal.copy(human = Some(goal.human.get.copy(humanDecisionContext = Some(contexts.copy(purpose = Purpose.RepositoryHuman.identity)))))
        val requests = Vector(goal.copy(kind = "Wait"), goal.copy(kind = "Terminal"), goal.copy(human = None),
          goal.copy(human = Some(goal.human.get.copy(humanDecisionContext = None))), crossed,
          goal.copy(resultType = Some("RepositorySyncHumanConflictAnswer")), goal.copy(repositoryHuman = repository.human))
        When("the finite adapter receives every explicit invalid argument, file or selected output variant")
        val failures = argumentvariants.map(Main.prepareC(_, _read_(selectedfiles))) ++ filevariants.map(files => Main.prepareC(args, _read_(files))) ++
          requests.map(_tool.prepareRecord("GoalPhase", "human", _, goalAnswer))
        Then("each returns failure without a successful bootstrap row or guessed answer")
        failures.foreach(_.isSuccess shouldBe false)
        Consequence.unit
      }
    }
  }

  "T8 Normal command own and forwarded Human inputs" should afterWord(
    "in spec:native-host-evidence-tool, example:T8, rules:R1,R2,R3,R4,R5,R6, phase:6") {
    "retain Goal direct Repository and forwarded Human rows and refuse invalid assigned inputs" in {
      Given("actual production projections of fixture Decisions and explicit Human answers and metadata")
      withCore { call =>
        val goal = take(NativeWorkflowOperationOutput.goalHumanC(NativeHumanEvidenceFixture.goalQuestion, call.core))
        val repository = take(NativeWorkflowOperationOutput.repositoryHumanC(NativeHumanEvidenceFixture.repositoryQuestion, call.core))
        val child = NativeWorkflowOperationOutput.goalChild("outer-goal-session", repository)
        val cases: Vector[(String, RecordPresentable, RecordPresentable)] = Vector(
          ("GoalPhase", goal, goalAnswer), ("RepositorySync", repository, repositoryAnswer), ("GoalPhase", child, repositoryAnswer))
        When("the finite tool reads actual normal formatter output through unchanged family readers")
        val seeds = cases.map { (family, output, answer) =>
          take(_tool.prepareNormalCommand(family, "human", output, answer))
        }
        val canonical = cases.map { (family, output, answer) => take(_tool.prepare(family, "human", output, answer)) }
        val context = goal.human.get.humanDecisionContext.get
        val badquestions = Vector("inputType", "resultType").map(key => context.copy(question = record(
          json(context.question).mapObject(_.add(key, Json.fromString("foreign-type"))))))
        val invalid = Vector(goal.copy(kind = "Wait"), goal.copy(human = None),
          goal.copy(human = Some(goal.human.get.copy(humanDecisionContext = None))),
          goal.copy(resultType = Some("RepositorySyncHumanConflictAnswer")),
          goal.copy(repositoryHuman = repository.human),
          goal.copy(human = Some(goal.human.get.copy(humanDecisionContext = Some(context.copy(componentInstance = ""))))),
          goal.copy(human = Some(goal.human.get.copy(humanDecisionContext = Some(context.copy(purpose = Purpose.RepositoryHuman.identity)))))) ++
          badquestions.map(c => goal.copy(human = Some(goal.human.get.copy(humanDecisionContext = Some(c)))))
        val failures = invalid.map(_tool.prepareNormalCommand("GoalPhase", "human", _, goalAnswer))
        val selected = _tool.files(goal, goalAnswer).updated("request.json", _tool.normalCommandJson(goal))
        val missingparticipant = Main.prepareC(_tool.arguments("GoalPhase", "human") - "participant",
          path => Consequence.success(selected(path)))
        Then("normal wire rows equal canonical native payloads and crossed owner type or ambiguous and missing contexts fail")
        seeds.zip(canonical).foreach { (actual, expected) => _tool.row(actual) shouldBe _tool.row(expected) }
        _tool.row(seeds(1)) shouldBe _tool.row(seeds(2))
        seeds.map(_tool.evidence(_).purpose) shouldBe Vector(Purpose.GoalHuman.identity,
          Purpose.RepositoryHuman.identity, Purpose.RepositoryHuman.identity)
        failures.foreach(_.isSuccess shouldBe false)
        missingparticipant.isSuccess shouldBe false
        Consequence.unit
      }
    }
  }

  "T7 Normal command own and forwarded review inputs" should afterWord(
    "in spec:native-host-evidence-tool, example:T7, rules:R1,R2,R3,R4,R5,R6, phase:6"
  ) {
    "export identical configured rows for Goal Step Phase direct Repository and forwarded child reviews" in {
      withCore { call =>
        val goals = Vector("ReviewStep", "ReviewPhase").map(kind => {
          val output = take(NativeWorkflowOperationOutput.goalWorkRequestC(goalWork(kind), call.core))
          (kind, "GoalPhase", output, _goal_result, output.work.get.review.get.reviewContext.get, Purpose.GoalReview)
        })
        val repository = take(NativeWorkflowOperationOutput.repositoryReviewRequestC(repositoryWork, call.core))
        val child = NativeWorkflowOperationOutput.goalChild("outer-goal-session", repository)
        val cases: Vector[(String, String, RecordPresentable, RecordPresentable, V.WorkflowReviewContext, Purpose)] =
          goals ++ Vector(
            ("direct Repository", "RepositorySync", repository, _repository_result, repository.review.get.reviewContext.get, Purpose.RepositoryReview),
            ("Goal-forwarded Repository", "GoalPhase", child, _repository_result, repository.review.get.reviewContext.get, Purpose.RepositoryReview))
        cases.foreach { (label, family, output, answer, context, purpose) =>
          Given("Spec: docs/spec/native-host-evidence-tool.md; Rules: R1,R2,R3,R4,R5,R6; Example: T7; specification-issued " + label + " and explicit fixture metadata")
          val selected = _tool.files(output, answer).updated("request.json", _tool.normalCommandJson(output))
          When("the finite tool reads the normal command request through its strict family reader and passive review preparer")
          val seed = take(Main.prepareC(_tool.arguments(family, "review"), path => selected.get(path)
            .map(Consequence.success).getOrElse(Consequence.stateConflict("Specification file absent"))))
          val canonical = take(_tool.prepareRecord(family, "review", output, answer))
          val prepared = if (purpose == Purpose.GoalReview)
            take(NativeReviewPreparation.goalC(context, _goal_result, reference, execution, issuer, now.minusSeconds(60)))
          else take(NativeReviewPreparation.repositoryC(context, _repository_result, reference, execution, issuer, now.minusSeconds(60)))
          val evidence = _tool.evidence(seed)
          val completion = evidence.binding.hcursor.downField("attestation").downField("result")
          Then("the " + label + " row retains the selected native payload owner and child completion in the configured collection")
          _tool.row(seed) shouldBe _tool.row(canonical)
          _tool.row(seed).hcursor.get[String](NativeExternalEvidence.storageField).toOption shouldBe prepared.record.getString(NativeExternalEvidence.storageField)
          _tool.row(seed).hcursor.get[String]("id").toOption shouldBe Some(prepared.recordIdentity)
          evidence.purpose shouldBe purpose.identity
          evidence.binding.hcursor.get[String]("componentInstance").toOption shouldBe Some(context.componentInstance)
          completion.get[String]("runId").toOption shouldBe Some(context.completion.runId)
          completion.get[String]("continuationId").toOption shouldBe Some(context.completion.continuationId)
          completion.get[String]("typeIdentity").toOption shouldBe Some(context.completion.typeIdentity)
          io.circe.parser.parse(take(seed.toJsonStringC)).toOption.get.hcursor.downField("datastore").downArray.get[String]("collection").toOption shouldBe Some(_tool.collection)
        }
        Consequence.unit
      }
    }
  }
}
