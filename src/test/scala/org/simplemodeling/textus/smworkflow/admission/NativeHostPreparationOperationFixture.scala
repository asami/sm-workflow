package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.{Record, RecordPresentable}
import org.simplemodeling.textus.smworkflow.host.*
import org.simplemodeling.textus.smworkflow.value as V
import SplitPhaseExplicitPartitionFixture.take

/** Real normal configured runtime, current-core projections and selected local files.
  * Supplied specification facts prove no reviewer, operator or execution authority.
  * The inherited normal fixture closes each actual runtime handle.
  * @since   Oct. 15, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] trait NativeHostPreparationOperationFixture extends ThreeWorkflowManagedRepositoryFixture {
  protected val preparationService = "NativeHostPreparationService"
  protected val preparationOperations = Vector("prepareGoalStart", "prepareRepositoryStart", "prepareSplitStart",
    "prepareRepositoryEffect", "prepareAuthority", "prepareEvidence")
  protected val evidenceCollection = "entity:smworkflow.cli.host_evidence"
  protected final case class PreparedCase(label: String, input: Record, expected: Record)

  protected final class PreparationEpisode(val root: Path, val owner: Subsystem, val component: Component,
    val actor: ExecutionContext, val project: (Setup => Consequence[Unit]) => Unit) {
    def request(operation: String, input: Record): Request = Request.of(component = component.name,
      service = preparationService, operation = operation, properties = List(Property("input", input, None)))
    def invoke(operation: String, input: Record): Consequence[OperationResponse] =
      owner.executeOperationResponseInContext(request(operation, input), actor)
    def save(name: String, text: String): String = {
      val path = root.resolve("明示-" + name + ".json")
      Files.writeString(path, text, UTF_8)
      path.toUri.toASCIIString
    }
    def saveValue(name: String, value: RecordPresentable): String = save(name, take(value.toRecord().toJsonStringC))
    def saveValue(name: String, value: Record): String = save(name, take(value.toJsonStringC))
    def cases(operation: String): Vector[PreparedCase] = {
      var result = Vector.empty[PreparedCase]
      project { call => result = selectedCases(this, operation, call.core); Consequence.unit }
      result
    }
  }

  protected def withPreparation(cap: Int = 1048576)(task: PreparationEpisode => Unit): Unit = {
    val root = Files.createTempDirectory(Files.createDirectories(
      Path.of("target/sm-workflow/work/normal-preparation").toAbsolutePath), "明示-")
    _with_runtime_(root, "UpToDate", hostParameters = Map("textus.resource.read.max-bytes" -> cap.toString,
      "textus.resource.url.file.roots" -> root.toString)) {
      (owner, component, actor, project) => task(new PreparationEpisode(root, owner, component, actor, project))
    }
  }

  protected def importRows(entries: (String, Vector[Record])*): Record = Record.data("datastore" -> entries.toVector.map {
    (collection, rows) => Record.data("collection" -> collection, "records" -> rows)
  })
  protected def nativeRow(identity: String, record: Record): Record = Record.data("id" -> identity,
    NativeHostEvidence.storageField -> record.getString(NativeHostEvidence.storageField).get)

  private def selectedCases(episode: PreparationEpisode, operation: String, core: ActionCall.Core): Vector[PreparedCase] = {
    val owner = core.component.get.core.instanceId.canonicalKey
    operation match {
      case "prepareGoalStart" =>
        val input = V.GoalPhaseStartInput("phase-日本語", "sm-workflow")
        val plan = NativeHostAuthorityPreparationFixture.plan.copy(phase = input.phaseIdentity,
          repository = input.repositoryIdentity)
        val definition = take(GoalPhaseRuntimeCatalog.loadC()).definition
        val selection = ProfileStartAdmission.Selection("明示-goal", GoalPhaseNativeRun.startOperation,
          definition.workflowIdentity, definition.workflowRevision, "明示参加者", ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
          "明示-key", None, Some(TypedValue(NativeWorkflowStartInput.Kind.GoalPhase.inputType, input.asJson)))
        val selectionRow = Record.data("id" -> NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Goal(input), "明示参加者"),
          ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(selection)))
        val planRow = Record.data("id" -> selection.selectionId, GoalPhaseSelectedPlanJsonV1.storageField ->
          take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(selection.selectionId, "明示参加者", plan))))
        Vector(PreparedCase("Goal two selected rows", V.WorkflowGoalStartPreparationInput(
          episode.save("goal-input", input.asJson.noSpaces), episode.save("goal-plan", plan.asJson.noSpaces),
          selection.selectionId, "明示参加者", "ExplicitHuman", "明示-key", "entity:smworkflow.cli.goal_selection",
          "entity:smworkflow.cli.goal_plans").toRecord(),
          importRows("entity:smworkflow.cli.goal_selection" -> Vector(selectionRow), "entity:smworkflow.cli.goal_plans" -> Vector(planRow))))
      case "prepareRepositoryStart" =>
        val input = ThreeWorkflowNormalHostFixture.definition(episode.root).repository.input
        val definition = take(RepositorySyncDefinitionBinding.bindC)
        val selection = ProfileStartAdmission.Selection("明示-repository", RepositorySyncManagedFetch.startOperation,
          definition.workflowIdentity, definition.workflowRevision, "明示参加者", ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
          "明示-key", None, Some(TypedValue(NativeWorkflowStartInput.Kind.RepositorySync.inputType, input.asJson)))
        val business = NativeWorkflowStartInput.Repository(input.repositoryIdentity, input.localBranch, input.trackingRemote,
          input.trackingBranch, input.remoteEndpointIdentity, input.syncMode)
        val row = Record.data("id" -> NativeWorkflowStartBusinessKey(business, "明示参加者"),
          ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(selection)))
        Vector(PreparedCase("Repository selected row", V.WorkflowRepositoryStartPreparationInput(
          episode.save("repository-input", input.asJson.noSpaces), selection.selectionId, "明示参加者", "ExplicitHuman",
          "明示-key", "entity:smworkflow.cli.repository_selections").toRecord(),
          importRows("entity:smworkflow.cli.repository_selections" -> Vector(row))))
      case "prepareSplitStart" =>
        val f = NativeHostStartPreparationFixture
        f.starts.zipWithIndex.map { (start, index) =>
          val expected = take(f.prepareC(owner, start))
          PreparedCase("Split consent " + start.input.invocationMode + " proposal=" + start.proposal.nonEmpty,
            V.WorkflowSplitStartPreparationInput(owner, episode.saveValue(s"allocation-reference-$index", f.allocationReference(start)),
              episode.saveValue(s"allocation-$index", f.allocationRow(start)), episode.saveValue(s"source-$index", f.sourceRow(start)),
              episode.saveValue(s"start-reference-$index", f.reference), f.participant, f.issuer,
              f.now.minusSeconds(60).toString, evidenceCollection).toRecord(),
            importRows(evidenceCollection -> Vector(nativeRow(expected.recordIdentity, expected.record))))
        }
      case "prepareRepositoryEffect" =>
        val f = NativeHostRepositoryEffectPreparationFixture
        f.effects.map { effect =>
          val expected = take(f.prepareC(owner, effect))
          PreparedCase("Repository exact " + effect, V.WorkflowRepositoryEffectPreparationInput(owner,
            episode.saveValue("selection-" + effect, f.selectionRow()), episode.saveValue("target-" + effect, f.targetRow()),
            effect.toString, episode.saveValue("effect-reference-" + effect, f.reference), f.participant, f.issuer,
            f.now.minusSeconds(60).toString, evidenceCollection).toRecord(),
            importRows(evidenceCollection -> Vector(nativeRow(expected.recordIdentity, expected.record))))
        }
      case "prepareAuthority" =>
        val f = NativeHostAuthorityPreparationFixture
        f.purposes.map { purpose =>
          val output = take(f.outputC(core, purpose))
          val expected = take(NativeHostAuthorityPreparation.goalC(output.authorityContext.get, f.reference,
            f.participant, f.issuer, f.now.minusSeconds(60)))
          PreparedCase("Full closing Wait " + purpose, V.WorkflowAuthorityPreparationInput(
            episode.save("full-wait-" + purpose, f.normalCommandJson(output)), episode.saveValue("authority-reference-" + purpose, f.reference),
            f.participant, f.issuer, f.now.minusSeconds(60).toString, evidenceCollection).toRecord(),
            importRows(evidenceCollection -> Vector(nativeRow(expected.recordIdentity, expected.record))))
        }
      case "prepareEvidence" => evidenceCases(episode, core)
      case _ => throw new IllegalArgumentException("Specification selected an undeclared preparation Operation")
    }
  }

  private def evidenceCases(episode: PreparationEpisode, core: ActionCall.Core): Vector[PreparedCase] = {
    import NativeHostPreparationFixture.*
    val goalResult = V.GoalPhaseWorkResult("accept", "明示レビュー", Vector.empty, None)
    val repositoryResult = V.RepositorySyncNativeMergeReviewResult("accept", "明示レビュー", Vector.empty)
    def entry(label: String, family: String, kind: String, request: RecordPresentable, result: RecordPresentable,
      identity: String, payload: Record): PreparedCase = PreparedCase(label,
      V.WorkflowEvidencePreparationInput(family, kind,
        episode.save(label + "-request", NativeHostEvidenceToolFixture.normalCommandJson(request)),
        episode.saveValue(label + "-result", result), episode.saveValue(label + "-reference", reference),
        episode.saveValue(label + "-execution", execution), issuer, now.minusSeconds(60).toString, evidenceCollection,
        if (kind == "human") Some(participant) else None).toRecord(),
      importRows(evidenceCollection -> Vector(nativeRow(identity, payload))))
    val goals = Vector("ReviewStep", "ReviewPhase").map { kind =>
      val output = take(NativeWorkflowOperationOutput.goalWorkRequestC(goalWork(kind), core))
      val expected = take(NativeHostReviewPreparation.goalC(output.work.get.review.get.hostContext.get,
        goalResult, reference, execution, issuer, now.minusSeconds(60)))
      entry(kind, "GoalPhase", "review", output, goalResult, expected.recordIdentity, expected.record)
    }
    val repository = take(NativeWorkflowOperationOutput.repositoryReviewRequestC(repositoryWork, core))
    val review = take(NativeHostReviewPreparation.repositoryC(repository.review.get.hostContext.get,
      repositoryResult, reference, execution, issuer, now.minusSeconds(60)))
    val typedReceipt = receipt.copy(sbt = Some(WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.TestOnly,
      WorkflowSbtValidation.Outcome.Succeeded,
      Some(WorkflowSbtValidation.Selection(Vector("example.FirstSpec", "example.SecondSpec"), 2, 25L)), None)))
    val typedGoals = Vector("ReviewStep", "ReviewPhase").map { kind =>
      val input = NativeHostHumanEvidenceFixture.work.copy(sourceRevision = "7", kind = kind,
        candidate = Some(NativeHostHumanEvidenceFixture.snapshot), checksReceipt = Some("focused"),
        review = Some(GoalPhaseNativeReview.Context(Vector("one.scala"), "fixture typed diff", Vector(typedReceipt))))
      val issued = work("GoalPhaseWorkflow", input, GoalPhaseNativeWork.inputCodec.typeIdentity,
        GoalPhaseNativeWork.resultCodec.typeIdentity, GoalPhaseNativeRun.completionOperation)
      val output = take(NativeWorkflowOperationOutput.goalWorkRequestC(issued, core))
      val expected = take(NativeHostReviewPreparation.goalC(output.work.get.review.get.hostContext.get,
        goalResult, reference, execution, issuer, now.minusSeconds(60)))
      entry("Typed" + kind, "GoalPhase", "review", output, goalResult, expected.recordIdentity, expected.record)
    }
    val typedRepositoryInput = RepositorySyncNativeMergeReview.Request("instance", "7", "a" * 40,
      "c" * 40, "b" * 40, Vector("one.scala"), "fixture typed local diff", "fixture typed remote diff", "required", "focused",
      Vector("focused"), Vector(typedReceipt))
    val typedRepositoryWork = work("RepositorySyncWorkflow", typedRepositoryInput,
      RepositorySyncNativeMergeReview.inputCodec.typeIdentity, RepositorySyncNativeMergeReview.resultCodec.typeIdentity,
      RepositorySyncNativeMergeReviewContinuation.completionOperation)
    val typedRepository = take(NativeWorkflowOperationOutput.repositoryReviewRequestC(typedRepositoryWork, core))
    val typedReview = take(NativeHostReviewPreparation.repositoryC(typedRepository.review.get.hostContext.get,
      repositoryResult, reference, execution, issuer, now.minusSeconds(60)))
    val goalHuman = take(NativeWorkflowOperationOutput.goalHumanC(NativeHostHumanEvidenceFixture.goalQuestion, core))
    val own = take(NativeHostHumanPreparation.goalC(goalHuman.human.get.hostContext.get, goalAnswer,
      reference, execution, participant, issuer, now.minusSeconds(60)))
    val repositoryHuman = take(NativeWorkflowOperationOutput.repositoryHumanC(NativeHostHumanEvidenceFixture.repositoryQuestion, core))
    val human = take(NativeHostHumanPreparation.repositoryC(repositoryHuman.human.get.hostContext.get, repositoryAnswer,
      reference, execution, participant, issuer, now.minusSeconds(60)))
    goals ++ typedGoals ++ Vector(
      entry("TypedDirectRepositoryReview", "RepositorySync", "review", typedRepository, repositoryResult,
        typedReview.recordIdentity, typedReview.record),
      entry("TypedForwardedRepositoryReview", "GoalPhase", "review", NativeWorkflowOperationOutput.goalChild("outer-goal", typedRepository),
        repositoryResult, typedReview.recordIdentity, typedReview.record),
      entry("DirectRepositoryReview", "RepositorySync", "review", repository, repositoryResult, review.recordIdentity, review.record),
      entry("ForwardedRepositoryReview", "GoalPhase", "review", NativeWorkflowOperationOutput.goalChild("outer-goal", repository),
        repositoryResult, review.recordIdentity, review.record),
      entry("GoalHuman", "GoalPhase", "human", goalHuman, goalAnswer, own.recordIdentity, own.record),
      entry("DirectRepositoryHuman", "RepositorySync", "human", repositoryHuman, repositoryAnswer, human.recordIdentity, human.record),
      entry("ForwardedRepositoryHuman", "GoalPhase", "human", NativeWorkflowOperationOutput.goalChild("outer-goal", repositoryHuman),
        repositoryAnswer, human.recordIdentity, human.record))
  }
}
