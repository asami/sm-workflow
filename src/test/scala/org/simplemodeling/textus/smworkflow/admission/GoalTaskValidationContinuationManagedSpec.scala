package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{GitOperationKind, SbtOperation}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.{NativeGoalTaskStartInput, NativeReviewPreparation}
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import BoundedWorkRuntime.given
import StandaloneFixExecution as S
import StandaloneFixExecution.given
import GoalTaskValidationContinuation as C

/** Specification-owned receipt facts exercise native public admission only;
  * they do not represent installed execution or a consumer's consent.
  * @since   Nov.  7, 2026
  * @version Nov.  8, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
class GoalTaskValidationContinuationManagedSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  private def take[A](x: Consequence[A]): A = x.toOption.getOrElse(fail(s"Operation refused: $x"))
  private val suite = "org.simplemodeling.textus.smworkflow.p62authored.LineWriterSpec"
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), kinds = TypedGitHostPlanFixture.observations ++ Vector(
        GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff),
      goalPaths = Vector("docs/phase.md"))
    val plans = Vector((1, SbtOperation.FixCheck), (2, SbtOperation.Test), (3, SbtOperation.Compile),
      (4, SbtOperation.TestOnly)).map { (ordinal, operation) =>
      TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(ordinal),
        if (ordinal == 1) Some(git) else None, operation = operation,
        testClasses = if (operation == SbtOperation.TestOnly) Vector(suite) else Vector.empty)
    }
    def array(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
    val programs = plans.flatMap(array(_, "sbtPrograms"))
    val capabilities = (array(plans.head, "gitPrograms") ++ programs).map(_.hcursor.get[Json]("capability").toOption.get)
    plans.head.mapObject(_.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues(plans.flatMap(array(_, "workspaces"))))
      .add("workspaceGrants", Json.fromValues(plans.flatMap(array(_, "workspaceGrants"))))
      .add("grants", Json.arr(Json.obj("componentId" -> "org.simplemodeling.textus.SmWorkflow".asJson,
        "capabilities" -> Json.fromValues(capabilities.distinct)))))
  }
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    super._prepare_sbt_build_(root, scenario)
    TypedSbtHostPlanFixture.buildAuthoring(root, discoverAllTests = true)
    val repository = root.resolve("repository")
    Files.writeString(repository.resolve("project/plugins.sbt"), "addSbtPlugin(\"ch.epfl.scala\" % \"sbt-scalafix\" % \"0.12.1\")\n")
    Files.writeString(repository.resolve(".scalafix.conf"), "rules = [DisableSyntax]\nDisableSyntax.noFinalize = true\n")
    Files.writeString(repository.resolve("build.sbt"),
      "\nThisBuild / semanticdbEnabled := true\naddCommandAlias(\"fixCheck\", \"Compile / scalafix --check\")\n", StandardOpenOption.APPEND)
    val test = root.resolve("repository/src/test/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriterSpec.scala")
    Files.createDirectories(test.getParent)
    Files.writeString(test, """package org.simplemodeling.textus.smworkflow.p62authored
      |import org.scalatest.wordspec.AnyWordSpec
      |final class LineWriterSpec extends AnyWordSpec {
      |  "LineWriter" should { "append LF" in { assert(LineWriter.render("fixture") == "fixture\n") } }
      |}
      |""".stripMargin)
    val path = root.resolve("repository/docs/phase.md")
    Files.createDirectories(path.getParent)
    Files.writeString(path, "Specification source\n")
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] =
    super._setup_commands_(root, scenario).patch(2, Vector(Vector("-C", root.resolve("repository").toString,
      "--literal-pathspecs", "add", "--", ".gitignore", ".scalafix.conf", "build.sbt", "project", "src", "docs")), 0)

  "Changed-source continuation coverage" should {
    "retain the old candidate and Compile in history while requiring coverage of newly captured production source" in {
      Given("a specification-owned unverified check and an admitted source-changing target")
      val state = SelectedValidationFixture.state
      val payload = BoundedWorkRuntime.Payload(state, "/fixture", "main", state.candidate.get.head)
      val r = BoundedSelectedValidationContinuation
      val target = r.Target(r.schema, "component", "session", "participant", "GoalTask", 8,
        payload.baseHead, state.candidate.get.tree, "focused", state.input.ownedPaths, Vector("docs/contract.md"), true)
      val direction = C.Direction(target.asJson, r.refresh, "Correct common continuation", "participant", ContextReference("fixture-direction", "1"))
      val adopted = take(r.adoptedC(payload, target, direction))
      When("normal candidate selection observes changed production inputs")
      val candidate = state.candidate.get.copy(tree = "d" * 40, paths = Vector("src/Value.scala", "docs/contract.md"), diff = "changed fixture source")
      val current = take(S.candidateC(adopted.state.copy(stage = "Inspect"), candidate))
      Then("old successful Compile is not reused for different source and counters are unchanged")
      payload.state.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
      payload.state.candidate shouldBe state.candidate
      current.receipts shouldBe empty
      current.selected shouldBe state.selected
      current.stage shouldBe "Validate"
      current.ordinaryCount shouldBe state.ordinaryCount
      current.automaticCount shouldBe state.automaticCount
    }
  }

  "Native GoalTask validation continuation" should {
    "append a direction without an SBT effect and retain the original checkpoint" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/task-validation-managed").toAbsolutePath), "episode-").toRealPath()
      Given("a test-owned legacy GoalTask and test-owned successful receipt facts")
      _with_runtime_(root, "TaskValidation", hostParameters = BoundedWorkExecutionAdmissionFixture.parameters) {
        (owner, component, actor, project) =>
        def call(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "GoalTaskService", operation = name, properties = properties), actor)
        def value[A: org.goldenport.convert.ValueReader](name: String, properties: List[Property]): A = read_value[A](take(call(name, properties)))
        val input = V.GoalTaskInput("sm-workflow", "fixture-validation", "Qualify specification source",
          NonEmptyVector.one("docs/phase.md"), NonEmptyVector.one("test"), NonEmptyVector.one("objective"),
          NonEmptyVector.one(V.WorkflowArtifactReference("specification-source", "1")), true,
          "code", "required", 10, "direct-instruction", Vector.empty)
        val prepared = take(NativeGoalTaskStartInput.prepareC(Map("input" -> "input", "selection-id" -> "selection",
          "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "start",
          "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
        project(c => c.setupC(_setup_commands_(root, "TaskValidation").size).flatMap { _ =>
          val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          c.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        })
        val started = value[V.GoalTaskView]("startGoalTask", List(Property("input", input, None),
          Property("selectionId", "selection", None), Property("participant", "participant", None), Property("idempotencyKey", "start", None)))
        project(c => for {
          storage <- c.taskStorageC()
          instances <- storage.bindInstancesC()(using c.executionContext)
          current <- instances.loadC(InstanceIdentity(started.sessionId))(using c.executionContext)
          payload <- GoalTaskNativeRun.loadPayloadC(storage, started.sessionId, current.get.revision.value, c.core)
          observed <- RepositorySyncManagedGitObservation.observeC(c.core)
          tree <- RepositorySyncManagedGitObservation.indexTreeC(c.core)
          checks = Vector(GoalPhaseNativeValidation.Check("fix-check", 1, WorkflowSbtValidation.Kind.FixCheck),
            GoalPhaseNativeValidation.Check("test", 2, WorkflowSbtValidation.Kind.Test))
          receipts = checks.map { check =>
            val wire = WorkflowSbtValidation.Facts(check.kind, WorkflowSbtValidation.Outcome.Succeeded, None, None)
              .asJson.mapObject(_.add("estimate", Json.Null))
            RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(0), "", "",
              Some(wire.as[WorkflowSbtValidation.Facts].toOption.get))
          }
          candidate = S.Candidate(observed.head, tree, Vector.empty, "")
          legacy = payload.copy(state = payload.state.copy(input = payload.state.input.copy(checks = Vector("fix-check", "test")),
            stage = "Assess", selected = checks, receipts = receipts,
            candidate = Some(candidate), semanticReference = Some(ContextReference("specification-result", "1")),
            conditionReport = Vector(S.Condition("objective", true, "Specification-owned work completed"))))
          host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", payload.root, payload.branch, payload.state.input.ownedPaths, checks, None, None, None, None, None)
          _ <- GoalTaskNativeRun.saveC(host, storage, current.get, legacy, c.core)
        } yield ())
        val described = value[V.GoalTaskValidationContinuationDescriptionResult]("describeGoalTaskValidationContinuation",
          List(Property("sessionId", started.sessionId, None)))
        val target = take(C.targetC(described.target))
        target.supplemental.map(_.ordinal) shouldBe Vector(3, 4)
        val reference = V.WorkflowArtifactReference("specification-human", "1")
        val answer = V.GoalTaskValidationContinuationInput(started.sessionId, described.target, "AuthorizeMissingChecks",
          "Specification direction", "participant", reference)
        project(c => for {
          prepared <- NativeReviewPreparation.taskValidationContinuationC(described, answer, reference,
            V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None), "participant", "normal-development-host",
            c.executionContext.clock.instant().minusSeconds(1))
          _ <- c.createC("host_evidence", prepared.recordIdentity, prepared.record)
        } yield ())
        val deferReference = V.WorkflowArtifactReference("specification-defer", "1")
        val deferred = answer.copy(choice = "Defer", reference = deferReference)
        project(c => for {
          prepared <- NativeReviewPreparation.taskValidationContinuationC(described, deferred, deferReference,
            V.WorkflowExecutionEvidence(Vector(deferReference), Some("participant"), None, None), "participant", "normal-development-host",
            c.executionContext.clock.instant().minusSeconds(1))
          _ <- c.createC("host_evidence", prepared.recordIdentity, prepared.record)
        } yield ())
        value[V.GoalTaskValidationContinuationDescriptionResult]("adoptGoalTaskValidationContinuation", List(Property("input", deferred, None))).outcome shouldBe "deferred"
        value[V.GoalTaskValidationContinuationDescriptionResult]("describeGoalTaskValidationContinuation",
          List(Property("sessionId", started.sessionId, None))).target shouldBe described.target
        When("the exact independent direction is adopted")
        val adopted = value[V.GoalTaskValidationContinuationDescriptionResult]("adoptGoalTaskValidationContinuation", List(Property("input", answer, None)))
        Then("the native checkpoint retains the successful prefix and does not run a check")
        adopted.outcome shouldBe "resumed-awaiting-advance"
        value[V.GoalTaskValidationContinuationDescriptionResult]("adoptGoalTaskValidationContinuation", List(Property("input", answer, None))).outcome shouldBe "already-resumed"
        project(c => for {
          storage <- c.taskStorageC()
          instances <- storage.bindInstancesC()(using c.executionContext)
          current <- instances.loadC(InstanceIdentity(started.sessionId))(using c.executionContext)
          payload <- GoalTaskNativeRun.loadPayloadC(storage, started.sessionId, current.get.revision.value, c.core)
          _ = payload.state.receipts shouldBe target.business.state.receipts
          _ = payload.state.selected shouldBe target.business.state.selected ++ target.supplemental
          _ = payload.state.input shouldBe target.business.state.input
        } yield ())
        When("ordinary public Advance executes the two supplemental checks")
        val review = value[V.GoalTaskView]("advanceGoalTask", List(Property("sessionId", started.sessionId, None)))
        Then("strict assessment reaches independent Review with the original prefix unchanged")
        var reviewRevision = 0L
        var reviewedState: Option[S.State] = None
        project(c => for {
          storage <- c.taskStorageC()
          instances <- storage.bindInstancesC()(using c.executionContext)
          current <- instances.loadC(InstanceIdentity(started.sessionId))(using c.executionContext)
          // Review is suspended on issued work; its source checkpoint precedes that suspension.
          sourceRevision = current.get.revision.value - (if (current.get.suspension.nonEmpty) 1 else 0)
          _ = { reviewRevision = sourceRevision }
          payload <- GoalTaskNativeRun.loadPayloadC(storage, started.sessionId, sourceRevision, c.core)
          state = payload.state
          _ = { reviewedState = Some(state) }
          original = target.business
          _ = state.stage shouldBe "Review"
          _ = state.input shouldBe original.state.input
          _ = state.candidate shouldBe original.state.candidate
          _ = state.receipts.take(2) shouldBe original.state.receipts
          _ = state.receipts.take(2).foreach(_.sbt.get.asJson.hcursor.downField("estimate").focus shouldBe Some(Json.Null))
          _ = state.selected shouldBe original.state.selected ++ target.supplemental
          _ = state.receipts.drop(2).map(_.sbt.get.kind) shouldBe Vector(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Kind.TestOnly)
          _ = state.receipts.drop(2).foreach(_.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded)
          _ = state.receipts.last.sbt.get.selection.get.testClasses shouldBe Vector(suite)
          _ = state.receipts.last.sbt.get.selection.get.valid shouldBe true
          _ = state.receipts.drop(2).foreach(_.processObservation.nonEmpty shouldBe true)
          _ = state.automaticCount shouldBe original.state.automaticCount
          _ = state.ordinaryCount shouldBe original.state.ordinaryCount
          _ = state.generation shouldBe original.state.generation
          _ = state.semanticReference shouldBe original.state.semanticReference
          _ = state.input.sourceReferences shouldBe original.state.input.sourceReferences
          _ = payload.implementationWorker shouldBe original.implementationWorker
          _ = payload.implementationWorkers shouldBe original.implementationWorkers
        } yield ())
        val executions = Files.readString(root.resolve("sbt-executions.txt")).linesIterator.toVector
        executions.size shouldBe 2
        value[V.GoalTaskView]("advanceGoalTask", List(Property("sessionId", started.sessionId, None)))
        Files.readString(root.resolve("sbt-executions.txt")).linesIterator.toVector shouldBe executions

        def evidence(reference: V.WorkflowArtifactReference, worker: String, independent: Boolean) =
          V.WorkflowExecutionEvidence(Vector(reference), Some(worker), None, Some(V.WorkflowExecutionObservation(
            if (independent) "DELEGATED" else "INLINE", BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity,
            if (independent) worker else BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity,
            BoundedWorkExecutionAdmissionFixture.profile.identity)))
        def submit(view: V.GoalTaskView, result: V.GoalTaskWorkResult, reference: V.WorkflowArtifactReference,
          execution: V.WorkflowExecutionEvidence): V.GoalTaskView = value[V.GoalTaskView]("submitGoalTaskWorkResult",
          List(Property("sessionId", started.sessionId, None), Property("issuedWorkContext", view.issuedWorkContext.get, None),
            Property("result", result, None), Property("resultReference", reference, None), Property("executionEvidence", execution, None)))
        def prepareReview(view: V.GoalTaskView, result: V.GoalTaskWorkResult, reference: V.WorkflowArtifactReference,
          execution: V.WorkflowExecutionEvidence): Unit = project(c => for {
          prepared <- NativeReviewPreparation.taskC(view.reviewContext.get, result, reference, execution,
            "normal-development-host", c.executionContext.clock.instant().minusSeconds(1))
          _ <- c.createC("host_evidence", prepared.recordIdentity, prepared.record)
        } yield ())
        val finding = V.GoalTaskWorkResult("repair", "Specification review found a missing clarification",
          Vector("Explain the selected validation behavior"),
          NonEmptyVector.one(V.GoalTaskCondition("objective", false, "Clarification required")), Vector.empty, None, None)
        val reviewReference = V.WorkflowArtifactReference("specification-independent-review", "1")
        val reviewExecution = evidence(reviewReference, "spec-reviewer", true)
        When("normal public review preparation and Submit use the actual issued context")
        val publicTarget = GoalPhaseNativeMetadata.normalizePublicJson(io.circe.parser.parse(
          take(review.reviewContext.get.target.toJsonStringC)).toOption.get).toOption.get
        val publicPrefix = publicTarget.hcursor.downField("state").get[Vector[Json]]("receipts").toOption.get.take(2)
        publicPrefix.foreach(_.hcursor.downField("sbt").downField("estimate").focus shouldBe None)
        prepareReview(review, finding, reviewReference, reviewExecution)
        submit(review, finding, reviewReference, reviewExecution).kind shouldBe "Submitted"
        val repair = value[V.GoalTaskView]("advanceGoalTask", List(Property("sessionId", started.sessionId, None)))
        repair.kind shouldBe "Request"
        val repairWork = GoalPhaseNativeMetadata.normalizePublicJson(io.circe.parser.parse(take(repair.work.get.toJsonStringC)).toOption.get)
          .toOption.get.as[S.Work].toOption.get
        repairWork.kind shouldBe "Repair"
        repairWork.state.receipts shouldBe reviewedState.get.receipts
        repairWork.state.automaticCount shouldBe 0
        repairWork.state.ordinaryCount shouldBe 0
        Files.writeString(root.resolve("repository/docs/phase.md"), "Specification source\nSelected validation behavior is clarified.\n")
        val repairReference = V.WorkflowArtifactReference("specification-repair", "1")
        val completed = V.GoalTaskWorkResult("completed", "Clarified selected validation behavior", Vector.empty,
          NonEmptyVector.one(V.GoalTaskCondition("objective", true, "Clarification authored")), Vector.empty, None, None)
        submit(repair, completed, repairReference,
          evidence(repairReference, BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity, false)).kind shouldBe "Submitted"
        When("ordinary Advance captures the changed candidate and executes fresh selected validation")
        val rereview = value[V.GoalTaskView]("advanceGoalTask", List(Property("sessionId", started.sessionId, None)))
        val rereviewWork = GoalPhaseNativeMetadata.normalizePublicJson(io.circe.parser.parse(take(rereview.work.get.toJsonStringC)).toOption.get)
          .toOption.get.as[S.Work].toOption.get
        rereviewWork.kind shouldBe "Review"
        rereviewWork.state.generation shouldBe reviewedState.get.generation + 1
        rereviewWork.state.automaticCount shouldBe 1
        rereviewWork.state.ordinaryCount shouldBe 1
        rereviewWork.state.receipts.size shouldBe 4
        rereviewWork.state.receipts.foreach(_.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded)
        rereviewWork.state.receipts.foreach(_.processObservation.nonEmpty shouldBe true)
        val accept = completed.copy(outcome = "accept", summary = "Independent review accepts the corrected candidate")
        val acceptReference = V.WorkflowArtifactReference("specification-independent-accept", "1")
        val acceptExecution = evidence(acceptReference, "spec-second-reviewer", true)
        prepareReview(rereview, accept, acceptReference, acceptExecution)
        submit(rereview, accept, acceptReference, acceptExecution).kind shouldBe "Submitted"
        // Advance consumes the accepted review. The fixture does not provision a
        // GoalTask commit selection; a later commit admission is outside this test.
        val postAccept = call("advanceGoalTask", List(Property("sessionId", started.sessionId, None)))
        postAccept.isSuccess shouldBe false
        postAccept.toString should include("git.execution.program-definition")
        Then("the original review checkpoint and explicit-null prefix remain intact")
        project(c => for {
          storage <- c.taskStorageC()
          instances <- storage.bindInstancesC()(using c.executionContext)
          current <- instances.loadC(InstanceIdentity(started.sessionId))(using c.executionContext)
          accepted <- GoalTaskNativeRun.loadPayloadC(storage, started.sessionId, current.get.revision.value, c.core)
          original <- GoalTaskNativeRun.loadPayloadC(storage, started.sessionId, reviewRevision, c.core)
          _ = accepted.state.priorReview.map(_.outcome) shouldBe Some("accept")
          _ = accepted.state.reviewReference.map(_.identity) shouldBe Some(acceptReference.identity)
          _ = accepted.state.stage shouldBe "CommitReady"
          _ = accepted.state.commitExecution shouldBe None
          _ = accepted.state.automaticCount shouldBe 1
          _ = accepted.state.ordinaryCount shouldBe 1
          _ = original.state shouldBe reviewedState.get
          _ = original.state.receipts.take(2).foreach(_.sbt.get.asJson.hcursor.downField("estimate").focus shouldBe Some(Json.Null))
        } yield ())
      }
    }
  }
}
