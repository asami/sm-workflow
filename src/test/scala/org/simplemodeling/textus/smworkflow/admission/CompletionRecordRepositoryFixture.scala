/**
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Property, Request}
import org.scalatest.GivenWhenThen
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import SplitPhaseExplicitPartitionFixture.take

/** Selected-suite fixture using normal configured Git authority, public Operations,
  * and independent specification-owned review evidence. No live approval is supplied.
  */
private[admission] final class CompletionRecordRepositoryFixture extends ThreeWorkflowManagedRepositoryFixture {
  private val path = "docs/completion.md"
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    super._prepare_sbt_build_(root, scenario)
    Files.createDirectories(root.resolve("repository/docs"))
    Files.writeString(root.resolve("repository").resolve(path), "# Repository facts\n\nExisting narrative.\n")
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
    val commands = super._setup_commands_(root, scenario)
    commands.patch(commands.indexWhere(_.contains("commit")), Vector(Vector("-C", root.resolve("repository").toString,
      "--literal-pathspecs", "add", "--", path)), 0)
  }
  override protected def _setup_files_(root: Path, scenario: String, index: Int): Unit = {
    val inserted = super._setup_commands_(root, scenario).indexWhere(_.contains("commit"))
    if (index != inserted)
      super._setup_files_(root, scenario, if (index > inserted) index - 1 else index)
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val target = Programs.Target(root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString)
    val original = TypedGitHostPlanFixture.plan(target, goalPaths = Vector(path),
      goalMessages = Vector("Record accepted repository completion"), goalSelections = Vector(Vector(path)))
    val declared = original.hcursor.get[Vector[Json]]("gitPrograms").toOption.get.map { program =>
      if (program.hcursor.get[String]("selectionId").toOption.contains("goal-step-1")) program.mapObject(fields => fields
        .add("selectionId", Json.fromString("repository-completion-records"))
        .add("capability", Json.fromString("repository-records-" + fields("operation").get.asString.get))
        .add("identity", Json.fromString("repository-records-" + fields("operation").get.asString.get)))
      else program
    }.filterNot(program => root.getFileName.toString.startsWith("missing-") &&
      program.hcursor.get[String]("selectionId").toOption.contains("repository-completion-records"))
    val grants = original.hcursor.get[Vector[Json]]("grants").toOption.get.map(_.mapObject(_.add("capabilities",
      Json.fromValues(declared.map(_.hcursor.get[String]("capability").toOption.get).map(Json.fromString)))))
    val git = original.mapObject(_.add("gitPrograms", Json.fromValues(declared)).add("grants", Json.fromValues(grants)))
    TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(git))
  }

  def qualify(narrator: GivenWhenThen): Unit = for ((scenario, missing) <- Vector(("Equal", false), ("Diverged", false), ("Equal", true))) {
    narrator.Given("a " + scenario + " repository and explicitly configured completion destination; missing selection = " + missing)
    val directory = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/completion-repository").toAbsolutePath),
      if (missing) "missing-" else "registered-")
    val original = ThreeWorkflowNormalHostFixture.definition(directory)
    val host = original.copy(goal = original.goal.copy(candidatePaths = Vector(path)))
    val input = host.repository.input
    val selection = _repository_selection_(input, "record-repository-selection", "record-repository-start")
    val policy = CompletionRecords.Policy(Vector(CompletionRecords.Target("repository-sync", input.repositoryIdentity, Vector(path))))
    _with_runtime_(directory, scenario, host = Some(host), hostParameters = Map(CompletionRecords.declaration.name -> policy.asJson.noSpaces)) {
      (owner, component, actor, prepare) =>
      def call(operation: String, fields: List[Property]) = owner.executeOperationResponseInContext(Request.of(
        component = component.name, service = "RepositorySyncService", operation = operation, properties = fields), actor)
      prepare(setup => setup.setupC(_setup_commands_(directory, scenario).size).flatMap(_ =>
        _prepare_input_(setup, input, selection, directory, merged = true)))
      val started = read_value[V.RepositorySyncStartResult](take(call("startRepositorySync", NativeWorkflowOperationInputFixture.repository(input))))
      val id = InstanceIdentity(started.sessionId)
      def advance() = call("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(id.value))
      var parents = Vector.empty[String]
      if (scenario == "Diverged") {
        narrator.When("normal Advance validates both native parents and issues its independent review")
        read_value[V.RepositorySyncAdvanceResult](take(advance())).kind shouldBe "Request"
        var work = Option.empty[WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing]]
        prepare(_.repositoryWorkC(id, RepositorySyncNativeMergeReviewContinuation.completionOperation,
          RepositorySyncNativeMergeReview.inputCodec).map(value => { work = Some(value); () }))
        val assigned = work.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request
        parents = Vector(assigned.input.get.value.localTip, assigned.input.get.value.remoteTip)
        val reference = ContextReference("completion-repository-review", assigned.expectedRevision.value)
        val result = ContinuationResult(work.get.handle, assigned.runId, assigned.continuationId, assigned.expectedRevision,
          assigned.context.snapshot, TypedValue(RepositorySyncNativeMergeReview.resultCodec.typeIdentity,
            RepositorySyncNativeMergeReview.Result("accept", "Specification review accepts the actual validated native merge", Vector.empty)),
          reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-independent-reviewer")))
        prepare(_.reviewEvidenceC(assigned.input.get.value, result))
        read_value[V.RepositorySyncNativeMergeReviewSubmissionResult](take(call("submitNativeMergeReview",
          take(NativeWorkflowOperationInputFixture.submissionC(result))))).accepted shouldBe true
      }
      narrator.When("the owner advances through required records and separately authorized convergence")
      val actual = advance()
      if (missing) {
        narrator.Then("missing normal Git registration refuses before record mutation or Terminal")
        actual.isSuccess shouldBe false
        Files.readString(directory.resolve("repository").resolve(path)) shouldBe "# Repository facts\n\nExisting narrative.\n"
      } else {
        val completed = read_value[V.RepositorySyncAdvanceResult](take(actual))
        narrator.Then("the completion record is in the actual converged commit and native merge parents are preserved")
        completed.kind shouldBe "Terminal"
        val text = Files.readString(directory.resolve("repository").resolve(path))
        text should include("Accepted repository-sync " + input.repositoryIdentity)
        var commit = Option.empty[RepositorySyncManagedGitObservation.Commit]
        prepare(setup => RepositorySyncManagedGitObservation.commitC(setup.core, completed.convergence.get.tip).map(value => { commit = Some(value); () }))
        if (parents.nonEmpty) commit.get.parents shouldBe parents
        val checks = Files.readString(directory.resolve("sbt-executions.txt"))
        read_value[V.RepositorySyncAdvanceResult](take(advance())).kind shouldBe "Terminal"
        Files.readString(directory.resolve("sbt-executions.txt")) shouldBe checks
        Files.readString(directory.resolve("repository").resolve(path)) shouldBe text
      }
    }
  }
}
