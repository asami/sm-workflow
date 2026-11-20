/**
 * @since   Nov. 17, 2026
 * @version Nov. 21, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.processexecution.{GitBranchName, GitObjectId, GitOperation, GitWorktree, SbtOperation}
import org.goldenport.cncf.workflow.{WorkflowComponentStorage, WorkflowMechanicalTransition}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import SplitPhaseExplicitPartitionFixture.take
import RepositorySyncNativeMergeValidation as C
import RepositorySyncProgramImpact as P

/** Every repository and effect is specification-owned and local. Remote means
  * the isolated bare fixture, never a live GitHub endpoint or another checkout.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncLocalMainSpec extends AnyWordSpec with GivenWhenThen with Phase69ConfiguredRepositoryFixture {
  private val sourceWorkspace = "sm-workflow-repository"
  private val mainWorkspace = "canonical-main"
  private def cloneScenario(scenario: String): Boolean = scenario.startsWith("Clone")
  private def _regression(scenario: String): Boolean = scenario.startsWith("Regression")
  private val _shared_source = "src/main/java/Shared.java"
  private val _left_source = "src/main/java/Left.java"
  private val _right_source = "src/main/java/Right.java"
  private val _compile = C.Check("compile", 1, WorkflowSbtValidation.Kind.Compile)
  private val _left_test = C.Check("left-test", 2, WorkflowSbtValidation.Kind.Test)
  private val _right_test = C.Check("right-test", 3, WorkflowSbtValidation.Kind.Test)
  private val _shared_test = C.Check("shared-test", 4, WorkflowSbtValidation.Kind.Test)
  private val _full_test = C.Check("ordinary-full", 5, WorkflowSbtValidation.Kind.Test)
  private def _impact_policy: P.Policy = {
    val broad = Vector("build.sbt", "project")
    val sources = Vector(_shared_source, _left_source, _right_source)
    P.Policy(Vector(
      P.Coverage(_compile, sources, Vector("src", "value.txt") ++ broad, "Compile every contributed source"),
      P.Coverage(_left_test, Vector(_shared_source, _left_source), Vector(_shared_source, _left_source) ++ broad,
        "Left behavior consumes local and shared inputs"),
      P.Coverage(_right_test, Vector(_right_source), Vector(_right_source) ++ broad, "Right behavior consumes remote input"),
      P.Coverage(_shared_test, Vector(_shared_source), sources ++ broad, "Shared behavior also consumes either side's source")),
      Vector(_compile, _full_test), broad, "Ordinary FULL covers broad and undeclared behavior", Vector.empty)
  }

  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    super._prepare_sbt_build_(root, scenario)
    if (_regression(scenario)) {
      val build = root.resolve("repository/build.sbt")
      Files.writeString(build, Files.readString(build) + "\nGlobal / onLoad := {\n" +
        "  val original = (Global / onLoad).value\n  state => {\n    val loaded = original(state)\n" +
        "    IO.append(file(" + root.resolve("selected-launches.txt").toString.asJson.noSpaces +
        "), state.remainingCommands.map(_.commandLine).mkString(\"|\") + \"\\n\")\n    loaded\n  }\n}\n")
      val product = root.resolve("repository/src/test/scala/ProductCheck.scala")
      Files.writeString(product, Files.readString(product).replace("println(\"PRODUCT_CHECK_PASSED\")",
        "val source = java.nio.file.Files.readString(root.resolve(\"src/main/java/Shared.java\"))\n" +
        "    assert(!source.contains(\"<<<<<<<\") && source.contains(\"int value\"), \"integrated source is required\")\n" +
        "    println(\"PRODUCT_CHECK_PASSED\")"))
    }
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    Files.createDirectories(root.resolve("work"))
    if (scenario.startsWith("Batch")) Files.createDirectories(root.resolve("work-two"))
    val original = super.process_plan(root, "Equal", goal)
    def array(key: String) = original.hcursor.get[Vector[Json]](key).toOption.get
    val source = array("gitRepositories").find(_.hcursor.get[String]("target").contains(sourceWorkspace)).get
      .mapObject(_.add("localBranch", "contribution".asJson).add("identity", "source-repository".asJson))
    val main = source.mapObject(_.add("target", mainWorkspace.asJson).add("localBranch", "main".asJson)
      .add("identity", "canonical-main-family".asJson).add("capability", "canonical-main-git".asJson))
    val transferWorkspace = "canonical-source-fetch"
    val transfers = if (cloneScenario(scenario)) Vector(main.mapObject(_.add("target", transferWorkspace.asJson)
      .add("identity", "canonical-source-family".asJson).add("capability", "canonical-source-git".asJson)
      .add("remote", "source-contribution".asJson).add("remoteBranch", "contribution".asJson)
      .add("endpoint", root.resolve("work").toString.asJson))) else Vector.empty
    val second = if (scenario.startsWith("Batch")) Vector(source.mapObject(_.add("target", "second-source".asJson)
      .add("identity", "second-source-repository".asJson).add("capability", "second-source-git".asJson)
      .add("localBranch", "contribution-two".asJson))) else Vector.empty
    val extraWorkspaces = (Vector(mainWorkspace) ++ Option.when(cloneScenario(scenario))(transferWorkspace))
      .map(identity => Json.obj("identity" -> identity.asJson, "root" -> root.resolve("repository").toString.asJson)) ++
      Option.when(scenario.startsWith("Batch"))(Json.obj("identity" -> "second-source".asJson, "root" -> root.resolve("work-two").toString.asJson))
    val extraGrants = extraWorkspaces.map(row => Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
      "workspaceId" -> row.hcursor.get[String]("identity").toOption.get.asJson, "access" -> "manage".asJson))
    val configured = original.mapObject(_.add("gitRepositories", (array("gitRepositories").filterNot(
      _.hcursor.get[String]("target").contains(sourceWorkspace)) ++ Vector(source, main) ++ transfers ++ second).asJson)
      .add("workspaces", (array("workspaces").map { row =>
        if (row.hcursor.get[String]("identity").contains(sourceWorkspace)) row.mapObject(_.add("root", root.resolve("work").toString.asJson))
        else row
      } ++ extraWorkspaces).asJson)
      .add("workspaceGrants", (array("workspaceGrants") ++ extraGrants).asJson)
      .add("grants", array("grants").map { grant =>
        if (grant.hcursor.get[String]("componentId").contains(SmWorkflowComponent.componentId.name))
          grant.mapObject(fields => fields.add("capabilities", (fields("capabilities").get.asArray.get.toVector ++
            Vector("canonical-main-git".asJson) ++ Option.when(cloneScenario(scenario))("canonical-source-git".asJson) ++
              Option.when(scenario.startsWith("Batch"))("second-source-git".asJson)).asJson))
        else grant
      }.asJson))
    val reconciled = if (scenario != "BatchConflict") configured else {
      Files.createDirectories(root.resolve("separate-main"))
      val separate = main.mapObject(_.add("target", "separate-main".asJson)
        .add("identity", "separate-main-family".asJson).add("capability", "separate-main-git".asJson))
      configured.mapObject(fields => fields
        .add("gitRepositories", fields("gitRepositories").get.mapArray(_ :+ separate))
        .add("workspaces", fields("workspaces").get.mapArray(_ :+ Json.obj("identity" -> "separate-main".asJson,
          "root" -> root.resolve("separate-main").toString.asJson)))
        .add("workspaceGrants", fields("workspaceGrants").get.mapArray(_ :+ Json.obj(
          "componentId" -> SmWorkflowComponent.componentId.name.asJson, "workspaceId" -> "separate-main".asJson, "access" -> "manage".asJson)))
        .add("grants", fields("grants").get.mapArray(_.map(grant =>
          if (grant.hcursor.get[String]("componentId").contains(SmWorkflowComponent.componentId.name))
            grant.mapObject(row => row.add("capabilities", row("capabilities").get.mapArray(_ :+ "separate-main-git".asJson)))
          else grant))))
    }
    val executable = if (!_regression(scenario)) reconciled else {
      val plans = Vector(TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable,
        Vector(1), operation = SbtOperation.Compile), TypedSbtHostPlanFixture.plan(root,
        TypedSbtHostPlanFixture.normalExecutable, Vector(2, 3, 4, 5)))
      def _array_(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
      val programs = plans.flatMap(_array_(_, "sbtPrograms"))
      val added = plans.flatMap(_array_(_, "workspaces"))
      val identities = added.map(_.hcursor.get[String]("identity").toOption.get).toSet
      reconciled.mapObject(fields => fields.add("sbtPrograms", programs.asJson)
        .add("workspaces", (_array_(reconciled, "workspaces").filterNot(row =>
          identities(row.hcursor.get[String]("identity").toOption.get)) ++ added).asJson)
        .add("workspaceGrants", (_array_(reconciled, "workspaceGrants").filterNot(row =>
          identities(row.hcursor.get[String]("workspaceId").toOption.get)) ++ plans.flatMap(_array_(_, "workspaceGrants"))).asJson)
        .add("grants", fields("grants").get.mapArray(_.map(grant => grant.mapObject(g =>
          g.add("capabilities", g("capabilities").get.mapArray(_ ++ programs.map(_.hcursor.get[Json]("capability").toOption.get))))))))
    }
    Phase69ConfiguredRepositoryFixture.withInitializationSources(executable)
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
    if (_regression(scenario)) return _regression_commands(root, scenario)
    def commit(path: Path, message: String) = Vector("-C", path.toString, "-c", "user.name=Fixture",
      "-c", "user.email=fixture@example.invalid", "commit", "-m", message)
    val checkout = if (cloneScenario(scenario)) Vector(
      Vector("clone", "--no-local", root.resolve("upstream.git").toString, root.resolve("work").toString),
      Vector("-C", root.resolve("work").toString, "checkout", "-b", "contribution")) else Vector(
      Vector("-C", root.resolve("repository").toString, "worktree", "add", "-b", "contribution", root.resolve("work").toString, "main"))
    val commands = super._setup_commands_(root, "Equal") ++ checkout ++ Vector(
      Vector("-C", root.resolve("work").toString, "add", "--", "docs/work.md"), commit(root.resolve("work"), "work contribution"),
      Vector("-C", root.resolve("repository").toString, "add", "--", "docs/main.md"), commit(root.resolve("repository"), "main-only contribution"),
      Vector("clone", "--no-local", root.resolve("upstream.git").toString, root.resolve("peer").toString),
      Vector("-C", root.resolve("peer").toString, "add", "--", "docs/remote.md"), commit(root.resolve("peer"), "remote-only contribution"),
      Vector("-C", root.resolve("peer").toString, "push", root.resolve("upstream.git").toString, "HEAD:refs/heads/main"))
    if (scenario == "CloneUnrelated") commands ++ Vector(
      Vector("-C", root.resolve("repository").toString, "checkout", "--orphan", "unrelated-fixture"),
      Vector("-C", root.resolve("repository").toString, "add", "--all"), commit(root.resolve("repository"), "SPECIFICATION-OWNED unrelated root"),
      Vector("-C", root.resolve("repository").toString, "branch", "-D", "main"),
      Vector("-C", root.resolve("repository").toString, "branch", "-m", "main"))
    else if (!scenario.startsWith("Batch")) commands else commands ++ Vector(
      Vector("-C", root.resolve("repository").toString, "worktree", "add", "-b", "contribution-two", root.resolve("work-two").toString, "main^"),
      Vector("-C", root.resolve("work-two").toString, "add", "--", "docs/two.md"), commit(root.resolve("work-two"), "second contribution"))
  }
  override protected def _setup_files_(root: Path, scenario: String, index: Int): Unit = {
    if (_regression(scenario)) { _regression_files(root, scenario, index); return }
    val file = (index - (if (cloneScenario(scenario)) 1 else 0)) match {
      case 5 => Some(root.resolve("work/docs/work.md") -> "accepted work contribution\n")
      case 7 => Some(root.resolve("repository/docs/main.md") -> "preserved main-only contribution\n")
      case 10 => Some(root.resolve("peer/docs/remote.md") -> "observed remote-only contribution\n")
      case 14 if scenario.startsWith("Batch") => Some(root.resolve("work-two/docs/two.md") -> "distinct second contribution\n")
      case _ => None
    }
    file.foreach { (path, text) => Files.createDirectories(path.getParent); Files.writeString(path, text) }
  }
  private val documents = Map(RepositorySyncDocumentInputs.declaration.name -> Json.obj(
    "documentPaths" -> Vector("docs").asJson, "consumedPaths" -> Vector("build.sbt", "src", "project").asJson,
    "reason" -> "SPECIFICATION-OWNED ordinary documents; build and executable inputs remain consumed".asJson).noSpaces)

  private def _regression_commands(root: Path, scenario: String): Vector[Vector[String]] = {
    val repository = root.resolve("repository")
    val work = root.resolve("work")
    val peer = root.resolve("peer")
    val remote = root.resolve("upstream.git")
    def _commit_(directory: Path, message: String): Vector[String] = Vector("-C", directory.toString,
      "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-m", message)
    Vector(Vector("init", "--bare", "--initial-branch=main", remote.toString),
      Vector("init", "--initial-branch=main", repository.toString),
      Vector("-C", repository.toString, "add", "--", _shared_source, "docs/shared.md"),
      _commit_(repository, "base product"), Vector("-C", repository.toString, "push", remote.toString, "HEAD:refs/heads/main"),
      Vector("-C", repository.toString, "worktree", "add", "-b", "contribution", work.toString, "main"),
      Vector("-C", work.toString, "add", "--all"), _commit_(work, "local source contribution"),
      Vector("-C", repository.toString, "add", "--", "docs/shared.md"), _commit_(repository, "canonical document contribution"),
      Vector("clone", "--no-local", remote.toString, peer.toString),
      Vector("-C", peer.toString, "add", "--all"), _commit_(peer, "remote source contribution"),
      Vector("-C", peer.toString, "push", remote.toString, "HEAD:refs/heads/main"))
  }

  private def _regression_files(root: Path, scenario: String, index: Int): Unit = {
    def _write_(directory: String, path: String, content: String): Unit = {
      val file = root.resolve(directory).resolve(path)
      Files.createDirectories(file.getParent)
      Files.writeString(file, content)
    }
    if (index == 2) {
      _write_("repository", _shared_source, "public final class Shared { public static int value() { return 1; } }\n")
      _write_("repository", "docs/shared.md", "base document\n")
    }
    if (index == 6) {
      if (scenario != "RegressionDisjoint")
        _write_("work", _shared_source, "public final class Shared { public static int value() { return 2; } }\n")
      _write_("work", "docs/work.md", "retained contribution document\n")
      if (scenario == "RegressionDisjoint") _write_("work", _left_source, "public final class Left {}\n")
    }
    if (index == 8) _write_("repository", "docs/shared.md", "canonical document\n")
    if (index == 11) {
      if (scenario == "RegressionDisjoint") {
        _write_("peer", _right_source, "public final class Right {}\n")
        _write_("peer", "docs/shared.md", "remote document\n")
      } else {
        if (scenario == "RegressionSameTree") {
          _write_("peer", "docs/shared.md", "remote document\n")
          _write_("peer", "docs/work.md", "retained contribution document\n")
        } else _write_("peer", _shared_source, "public final class Shared { public static int value() { return 3; } }\n")
      }
    }
  }

  private def _stage_c(call: Setup, session: String, key: String): Consequence[RepositorySyncNativeProgressStore.Stage] = {
    for {
      definition <- RepositorySyncDefinitionBinding.bindC
      storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
        SmWorkflowComponent.componentId, _configuration_, Vector(definition), _collection_("instances"), _collection_("continuations"))
      reader <- storage.bindMechanicalC(RepositorySyncManagedFetch.advanceOperation, RepositorySyncNativeProgressStore.codec)
      value <- reader.loadRetainedC(WorkflowMechanicalTransition.Reference(session + ":native-" + key, "1"))(using call.core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("Missing actual fixture stage " + key)))
    } yield RepositorySyncNativeProgressStore.Stage(value.receipt.next, value.receipt.payload.value)
  }

  "Native canonical main integration" should {
    for (scenario <- Vector("RegressionOverlap", "RegressionDisjoint", "RegressionSameTree")) {
      s"validate both contributed input frontiers and retain independent review through $scenario aggregate convergence" in {
        Given("isolated local and remote source contributions with a real remote conflict and declared bounded checks")
        val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/local-main-spec").toAbsolutePath), scenario + "-")
        val parameters = documents ++ Map(P.declaration.name -> _impact_policy.asJson.noSpaces,
          RepositorySyncDirectoryList.declaration.name -> Vector(root.resolve("work").toString).asJson.noSpaces)
        _with_runtime_(root, scenario, hostParameters = parameters) { (owner, component, actor, prepare) =>
          prepare(_.setupC(_setup_commands_(root, scenario).size, scenario = scenario))
          def _invoke_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
            read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "RepositorySyncService", operation = operation, properties = properties), actor)))
          val input = Record.data("syncMode" -> "FULL_STATE", "mainIntegration" -> "local-first")
          When("the normal aggregate integrates the local hop before exposing the actual remote conflict")
          var batch = _invoke_[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.syncOperation,
            List(Property("input", input, None)))
          val batchid = batch.sessionId
          val session = batch.repositories.head.sessionId
          val identity = InstanceIdentity(session)
          def _collect_(): V.RepositoryDirectoryBatchResult = _invoke_[V.RepositoryDirectoryBatchResult](
            RepositorySyncDirectoryList.syncOperation, List(Property("sessionId", batchid, None), Property("input", input, None)))
          def _until_request_(): Unit = {
            var attempts = 0
            while (!batch.repositories.exists(_.kind == "Request") && batch.outcome != "Completed" && attempts < 8) {
              batch = _collect_()
              attempts += 1
            }
            withClue(s"Fixture $root aggregate member=${batch.repositories.head}: ") {
              batch.repositories.head.kind shouldBe "Request"
            }
          }
          def _launches_(): Vector[String] = if (Files.exists(root.resolve("selected-launches.txt")))
            Files.readString(root.resolve("selected-launches.txt")).linesIterator.toVector else Vector.empty
          Then("the remote conflict waits after one local compile and its directly affected registered tests")
          withClue(s"Fixture $root aggregate member=${batch.repositories.head}: ") {
            batch.repositories.head.kind shouldBe "Wait"
            batch.repositories.head.condition shouldBe Some("MergeConflicted")
          }
          var local = Option.empty[RepositorySyncNativeProgressStore.Stage]
          prepare(call => _stage_c(call, session, "local-main-local-merge-validated").map { value =>
            local = Some(value)
            value.payload.validation.get.selected shouldBe (if (scenario == "RegressionDisjoint")
              Vector(_compile, _left_test) else Vector(_compile, _left_test, _shared_test))
            C.successful(value.payload.validation.get.receipts, selected = value.payload.validation.get.selected) shouldBe true
            ()
          })
          _launches_().size shouldBe (if (scenario == "RegressionDisjoint") 2 else 3)
          Files.readString(root.resolve("sbt-executions.txt")).linesIterator.size shouldBe
            (if (scenario == "RegressionDisjoint") 1 else 2)
          When("one ordinary advance on the same child issues its captured conflict work")
          val conflictrequest = _invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync",
            NativeWorkflowOperationInputFixture.advance(session))
          Then("the child issues work for the actual conflicting path and its two versions")
          withClue(s"Fixture $root child conflict response=$conflictrequest: ") {
            conflictrequest.kind shouldBe "Request"
            conflictrequest.conflict should not be empty
          }
          var conflict = Option.empty[WorkflowInteraction[RepositorySyncNativeConflictInput.Request, Nothing]]
          prepare(_.repositoryWorkC(identity, RepositorySyncNativeConflictContinuation.completionOperation,
            RepositorySyncConflictSemantic.inputCodec).map(value => { conflict = Some(value); () }))
          val order = conflict.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].request
          val captured = order.input.get.value
          captured.conflicts.map(_.path) shouldBe Vector(if (scenario == "RegressionOverlap") _shared_source else "docs/shared.md")
          captured.conflicts.head.local.text should not be captured.conflicts.head.remote.text
          val resolution = if (scenario == "RegressionDisjoint") "canonical and remote documents integrated\n"
            else if (scenario == "RegressionSameTree") "canonical document\n"
            else "public final class Shared { public static int value() { return 4; } }\n"
          val reference = ContextReference("spec-local-first-resolution", captured.conflictRevision)
          val answer = ContinuationResult(conflict.get.handle, order.runId, order.continuationId, order.expectedRevision,
            order.context.snapshot, TypedValue(RepositorySyncConflictSemantic.resultCodec.typeIdentity,
              RepositorySyncConflictSemantic.Result("resolved", captured.conflicts.map(value =>
                RepositorySyncConflictSemantic.Resolution(value.identity, resolution)), "Integrate the isolated fixture contributions")),
            reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-conflict-worker")))
          When("the assigned conflict is completed and the normal aggregate validates the resolved remote frontier")
          _invoke_[V.RepositorySyncConflictSubmissionResult]("submitRepositorySyncWorkResult",
            take(NativeWorkflowOperationInputFixture.submissionC(answer))).accepted shouldBe true
          batch = _collect_()
          _until_request_()
          Then("the remote frontier launches only applicable product checks and retains its own structural checks")
          var remote = Option.empty[RepositorySyncNativeProgressStore.Stage]
          prepare(call => for {
            value <- _stage_c(call, session, "local-main-remote-merge-validated")
            current <- call.currentC(identity)
          } yield {
            remote = Some(value)
            val state = value.payload.validation.get
            state.selected shouldBe (if (scenario == "RegressionDisjoint")
              Vector(_compile, _left_test, _right_test, _shared_test) else Vector(_compile, _left_test, _shared_test))
            state.receipts.take(2).map(_.identity) shouldBe Vector("index-local", "index-remote")
            C.successful(state.receipts, selected = state.selected) shouldBe true
            current.history.count(_.causation.value.contains("local-main-remote-validation-result-0")) shouldBe 1
            current.history.count(_.causation.value.contains("local-main-remote-validation-result-1")) shouldBe 1
            current.history.exists(_.currentProgression.exists(_.value == "sync.NativePushPreparing")) shouldBe false
            if (scenario == "RegressionSameTree") {
              value.payload.indexTree shouldBe local.get.payload.indexTree
              state.receipts.drop(2) shouldBe local.get.payload.validation.get.receipts.drop(2)
              state.impact.get.reused.map(_.validation).distinct shouldBe
                Vector(session + ":native-local-main-local-merge-validated")
              state.impact.get.reused.map(_.closure).distinct shouldBe Vector(session + ":native-local-main-local-commit-observed")
              state.impact.get.reused.flatMap(_.review) shouldBe empty
            } else {
              state.impact.get.reused shouldBe empty
              value.payload.indexTree should not be local.get.payload.indexTree
            }
            ()
          })
          val expected = if (scenario == "RegressionSameTree") 3 else 6
          _launches_().size shouldBe expected
          Files.readString(root.resolve("sbt-executions.txt")).linesIterator.size shouldBe
            (if (scenario == "RegressionSameTree") 2 else 4)
          val launches = _launches_()
          _collect_().repositories.head.kind shouldBe "Request"
          _launches_() shouldBe launches
          var review = Option.empty[WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing]]
          prepare(_.repositoryWorkC(identity, RepositorySyncNativeMergeReviewContinuation.completionOperation,
            RepositorySyncNativeMergeReview.inputCodec).map(value => { review = Some(value); () }))
          val assigned = review.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request
          assigned.input.get.value.indexTree shouldBe remote.get.payload.indexTree.get
          assigned.input.get.value.judgmentMode shouldBe "detailed-review"
          assigned.input.get.value.remoteDiff should include(if (scenario == "RegressionDisjoint") "Left.java" else "Shared.java")
          val reviewref = ContextReference("spec-remote-independent-review", "1")
          val reviewed = ContinuationResult(review.get.handle, assigned.runId, assigned.continuationId, assigned.expectedRevision,
            assigned.context.snapshot, TypedValue(RepositorySyncNativeMergeReview.resultCodec.typeIdentity,
              RepositorySyncNativeMergeReview.Result("accept", "Independent examination of both actual remote merge diffs", Vector.empty)),
            reviewref, Vector.empty, ExecutionEvidence(Vector(reviewref), Some("specification-independent-remote-judge")))
          When("the separately admitted remote review accepts the complete resolved candidate")
          prepare(_.reviewEvidenceC(assigned.input.get.value, reviewed))
          _invoke_[V.RepositorySyncNativeMergeReviewSubmissionResult]("submitNativeMergeReview",
            take(NativeWorkflowOperationInputFixture.submissionC(reviewed))).accepted shouldBe true
          batch = _collect_()
          var attempts = 0
          while (batch.outcome != "Completed" && attempts < 8) { batch = _collect_(); attempts += 1 }
          Then("convergence pushes once after independent review without replaying the checked product programs")
          batch.outcome shouldBe "Completed"
          _launches_() shouldBe launches
          prepare(_.currentC(identity).map { current =>
            current.history.count(_.currentProgression.exists(_.value == "sync.NativePushPreparing")) shouldBe 1
            ()
          })
        }
      }
    }

    "select ordinary FULL when remote inputs are broad unknown or lack complete compile and test coverage" in {
      Given("declared bounded compile and test scopes and ordinary FULL without HEAVY")
      def _inputs_(consumed: Vector[String], unknown: Vector[String] = Vector.empty) =
        RepositorySyncDocumentInputs.Assessment(consumed ++ unknown, consumed, unknown, Vector.empty, "Fixture input classification")
      val cases = Vector(_impact_policy -> _inputs_(Vector("build.sbt")),
        _impact_policy -> _inputs_(Vector.empty, Vector("unknown/behavior.conf")),
        _impact_policy.copy(coverage = _impact_policy.coverage.filterNot(_.check == _right_test)) -> _inputs_(Vector(_right_source)),
        _impact_policy.copy(coverage = _impact_policy.coverage.filterNot(_.check == _compile)) -> _inputs_(Vector(_shared_source)))
      When("the remote hop selects coverage for each uncertain or incompletely covered frontier")
      val selections = cases.map((policy, inputs) => P.selectRemote(policy, inputs))
      Then("each selection retains configured ordinary FULL checks and an explicit reason")
      selections.foreach { selected =>
        selected.depth shouldBe "FULL"
        selected.checks shouldBe Vector(_compile, _full_test)
        selected.reason should include("FULL")
        selected.checks.exists(_.identity.contains("HEAVY")) shouldBe false
      }
    }

    "refuse a mapped main with unrelated observed history before any synchronization mutation" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/local-main-spec").toAbsolutePath), "unrelated-clone-")
      val parameters = documents + (RepositorySyncLocalMain.mappings.name -> Map(sourceWorkspace -> mainWorkspace).asJson.noSpaces)
      _with_runtime_(root, "CloneUnrelated", hostParameters = parameters) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "CloneUnrelated").size))
        var source = Option.empty[RepositorySyncManagedGitObservation.Observed]
        var main = Option.empty[RepositorySyncManagedGitObservation.Observed]
        prepare(call => for {
          a <- RepositorySyncManagedGitObservation.observeInWorkspaceC(call.core, sourceWorkspace)
          b <- RepositorySyncManagedGitObservation.observeInWorkspaceC(call.core, mainWorkspace)
        } yield { source = Some(a); main = Some(b); () })
        def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "RepositorySyncService", operation = operation, properties = properties), actor)))
        val session = invoke[V.RepositorySyncStartResult]("startRepositorySync", List(Property("input", Record.data(
          "repositoryIdentity" -> "source-repository", "syncMode" -> "FULL_STATE"), None))).sessionId
        val refused = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", List(Property("sessionId", session, None)))
        refused.condition shouldBe Some("CanonicalMainSetupRequired")
        refused.message should include("MappedCanonicalCloneHasNoObservedSharedHistory")
        prepare(call => for {
          a <- RepositorySyncManagedGitObservation.observeInWorkspaceC(call.core, sourceWorkspace)
          b <- RepositorySyncManagedGitObservation.observeInWorkspaceC(call.core, mainWorkspace)
          current <- call.currentC(InstanceIdentity(session))
        } yield {
          a shouldBe source.get
          b shouldBe main.get
          current.history.exists(_.causation.value.startsWith("managed-fetch-intent:")) shouldBe false
          ()
        })
      }
    }
    for (participant <- Vector("source", "main")) {
      s"preserve dirty $participant state under error without integration or fallback" in {
        val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/local-main-spec").toAbsolutePath), "dirty-error-")
        _with_runtime_(root, "Equal", hostParameters = documents) { (owner, component, actor, prepare) =>
          prepare(_.setupC(_setup_commands_(root, "Equal").size))
          def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
            read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "RepositorySyncService", operation = operation, properties = properties), actor)))
          val session = invoke[V.RepositorySyncStartResult]("startRepositorySync", List(Property("input", Record.data(
            "repositoryIdentity" -> "source-repository", "syncMode" -> "FULL_STATE", "uncommittedPolicy" -> "error"), None))).sessionId
          def advance() = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", List(Property("sessionId", session, None)))
          if (participant == "main") advance().condition shouldBe Some("LocalMainSourcePrepared")
          val directory = root.resolve(if (participant == "source") "work" else "repository")
          Files.writeString(directory.resolve("unrelated.txt"), "unaccepted changes remain\n")
          var before = Option.empty[RepositorySyncManagedGitObservation.Observed]
          val workspace = if (participant == "source") sourceWorkspace else mainWorkspace
          prepare(call => RepositorySyncManagedGitObservation.observeInWorkspaceC(call.core, workspace).map(value => { before = Some(value); () }))
          val refused = advance()
          refused.kind shouldBe "Error"
          refused.condition shouldBe Some("UncommittedChanges")
          advance() shouldBe refused
          prepare(call => for {
            after <- RepositorySyncManagedGitObservation.observeInWorkspaceC(call.core, workspace)
            current <- call.currentC(InstanceIdentity(session))
          } yield {
            after shouldBe before.get
            current.history.exists(_.causation.value.contains("local-main-local-integration-intent")) shouldBe false
            if (participant == "source") current.history.exists(_.causation.value.startsWith("managed-fetch-intent:")) shouldBe false
            ()
          })
          Files.readString(directory.resolve("unrelated.txt")) shouldBe "unaccepted changes remain\n"
          Files.exists(root.resolve("repository/docs/work.md")) shouldBe false
        }
      }
    }
    "release remaining batch members only for a captured cohort contribution wait" in {
      val source = RepositorySyncLocalMain.Participant("source", sourceWorkspace, "/fixture/work",
        "contribution", "origin", "main", "endpoint")
      val main = source.copy(repository = "main", workspace = mainWorkspace, root = "/fixture/main", branch = "main")
      val cohort = RepositorySyncLocalMain.Cohort("batch:repository-1", Vector(source),
        Vector("batch:repository-1"), Vector("batch:repository-1", "batch:repository-2"))
      val plan = RepositorySyncLocalMain.Plan(source, main, linked = true, cohort = Some(cohort))
      val member = RepositorySyncDirectoryList.Member(source.repository, source.workspace, source.root,
        source.branch, source.remote, source.remoteBranch, source.endpoint, cohort.routeOwner, localMain = Some(plan))
      val waiting = RepositorySyncDirectoryList.Result(source.repository, source.workspace, member.sessionId,
        "Wait", "unfinished", Some("SharedTargetAwaitingContributions"), Some("7"), None, None)
      RepositorySyncDirectoryList.contributionWait(member, waiting) shouldBe true
      Vector("Human", "Request", "Progress", "Error", "Indeterminate").foreach { kind =>
        RepositorySyncDirectoryList.contributionWait(member, waiting.copy(kind = kind)) shouldBe false
      }
      Vector(None, Some("ValidationSetupRequired"), Some("purpose-materialization-preflight"),
        Some("purpose-effect-unknown"), Some("UNKNOWN")).foreach { condition =>
        RepositorySyncDirectoryList.contributionWait(member, waiting.copy(condition = condition)) shouldBe false
      }
      RepositorySyncDirectoryList.contributionWait(member.copy(localMain = None), waiting) shouldBe false
      RepositorySyncDirectoryList.contributionWait(member.copy(localMain = Some(plan.copy(cohort = None))), waiting) shouldBe false
      RepositorySyncDirectoryList.contributionWait(member.copy(localMain = Some(plan.copy(
        cohort = Some(cohort.copy(sessions = Vector("other-session")))))), waiting) shouldBe false
      RepositorySyncDirectoryList.contributionWait(member, waiting.copy(sessionId = "other-session")) shouldBe false
    }
    "reject a separate explicitly listed main before any cohort child or effects" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/local-main-spec").toAbsolutePath), "conflicting-main-")
      val parameters = documents + (RepositorySyncDirectoryList.declaration.name -> Vector(
        root.resolve("work").toString, root.resolve("separate-main").toString).asJson.noSpaces)
      _with_runtime_(root, "BatchConflict", hostParameters = parameters) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "BatchConflict").size))
        take(_run_setup_command_(Vector("clone", "--no-local", root.resolve("upstream.git").toString, root.resolve("separate-main").toString)))
        Files.writeString(root.resolve("separate-main/distinct.txt"), "SPECIFICATION-OWNED separate main contribution\n")
        take(_run_setup_command_(Vector("-C", root.resolve("separate-main").toString, "add", "--", "distinct.txt")))
        take(_run_setup_command_(Vector("-C", root.resolve("separate-main").toString, "-c", "user.name=Fixture",
          "-c", "user.email=fixture@example.invalid", "commit", "-m", "separate explicitly listed main")))
        var original = Vector.empty[RepositorySyncManagedGitObservation.Observed]
        prepare(call => for {
          source <- RepositorySyncManagedGitObservation.observeInWorkspaceC(call.core, sourceWorkspace)
          main <- RepositorySyncManagedGitObservation.observeInWorkspaceC(call.core, mainWorkspace)
          separate <- RepositorySyncManagedGitObservation.observeInWorkspaceC(call.core, "separate-main")
        } yield { original = Vector(source, main, separate); () })
        val refused = read_value[V.RepositoryDirectoryBatchResult](take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "RepositorySyncService", operation = RepositorySyncDirectoryList.syncOperation,
            properties = List(Property("input", Record.data("syncMode" -> "FULL_STATE", "mainIntegration" -> "confirm"), None))), actor)))
        refused.outcome shouldBe "ConfigurationRequired"
        refused.repositories shouldBe empty
        refused.configuration.get.diagnostic should include("SharedTargetSetupRequired")
        prepare(call => for {
          source <- RepositorySyncManagedGitObservation.observeInWorkspaceC(call.core, sourceWorkspace)
          main <- RepositorySyncManagedGitObservation.observeInWorkspaceC(call.core, mainWorkspace)
          separate <- RepositorySyncManagedGitObservation.observeInWorkspaceC(call.core, "separate-main")
        } yield { Vector(source, main, separate) shouldBe original; () })
      }
    }
    "reconcile explicit main workspace identity as well as canonical root branch and endpoint" in {
      val source = RepositorySyncLocalMain.Participant("source", sourceWorkspace, "/fixture/work", "contribution", "origin", "main", "endpoint")
      val main = source.copy(repository = "main", workspace = mainWorkspace, root = "/fixture/main", branch = "main")
      val member = RepositorySyncDirectoryList.Member(source.repository, source.workspace, source.root, source.branch,
        source.remote, source.remoteBranch, source.endpoint, "source-session", localMain = Some(RepositorySyncLocalMain.Plan(source, main, true)))
      val listed = RepositorySyncDirectoryList.Member(main.repository, main.workspace, main.root, main.branch,
        main.remote, main.remoteBranch, main.endpoint, "main-session")
      val policies = RepositorySyncPolicies.Selection("local-first", "auto-commit")
      RepositorySyncDirectoryList.sharedTargetSetup(Vector(member, listed), policies) shouldBe None
      Vector(listed.copy(root = "/fixture/separate"), listed.copy(workspace = "crossed-workspace")).foreach { crossed =>
        RepositorySyncDirectoryList.sharedTargetSetup(Vector(member, crossed), policies).get should include("SharedTargetSetupRequired")
      }
    }
    "coordinate declared linked contributions and canonical main with one retained batch route decision" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/local-main-spec").toAbsolutePath), "batch-")
      val parameters = documents + (RepositorySyncDirectoryList.declaration.name -> Vector(
        root.resolve("work").toString, root.resolve("work-two").toString, root.resolve("repository").toString).asJson.noSpaces)
      _with_runtime_(root, "Batch", hostParameters = parameters) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "Batch").size))
        def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "RepositorySyncService", operation = operation, properties = properties), actor)))
        val input = Record.data("syncMode" -> "FULL_STATE", "mainIntegration" -> "confirm")
        val first = invoke[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.syncOperation, List(Property("input", input, None)))
        first.repositories.size shouldBe 1
        val child = first.repositories.head.sessionId
        val repository = first.repositories.head
        repository.work shouldBe empty
        val detail = read_control_value[V.WorkflowRetainedWorkReadResult](take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = WorkflowRetainedWork.serviceName,
            operation = WorkflowRetainedWork.readOperation,
            properties = List(Property("input", repository.retainedWork.get, None))), actor)))
        val human = take(V.RepositorySyncAdvanceResult.createC(detail.response))
        human.sessionId shouldBe child
        human.kind shouldBe "Human"
        human.human.get.choices shouldBe Vector("LocalFirst", "Direct", "Defer", "Cancel")
        def collect() = invoke[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.syncOperation,
          List(Property("sessionId", first.sessionId, None), Property("input", input, None)))
        collect() shouldBe first
        var actual = Option.empty[WorkflowInteraction[RepositorySyncPolicyDecision.Input, Nothing]]
        prepare(_.repositoryPolicyQuestionC(InstanceIdentity(child)).map(value => { actual = Some(value); () }))
        val request = actual.get.current.asInstanceOf[WorkflowContinuation.Decision[RepositorySyncPolicyDecision.Input]].request
        val cohort = request.input.get.value.localMain.get.cohort.get
        cohort.participants.map(_.root).toSet shouldBe Set(root.resolve("work").toString, root.resolve("work-two").toString)
        cohort.members.size shouldBe 3
        val reference = ContextReference("SPECIFICATION-OWNED-shared-route", "1")
        val answer = ContinuationResult(actual.get.handle, request.runId, request.continuationId, request.expectedRevision,
          request.context.snapshot, TypedValue(RepositorySyncPolicyDecision.answerCodec.typeIdentity,
            RepositorySyncPolicyDecision.Answer("LocalFirst", "Explicit fixture Human direction for every declared source and the shared canonical target")),
          reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("participant")))
        prepare(_.policyHumanEvidenceC(human.human.get.humanDecisionContext.get, answer))
        invoke[V.RepositorySyncHumanConflictSubmissionResult](RepositorySyncPolicyDecision.completionOperation.operation,
          take(NativeWorkflowOperationInputFixture.submissionC(answer))).accepted shouldBe true
        val advanced = collect()
        advanced.repositories.map(_.sessionId).distinct.size shouldBe 3
        advanced.repositories.count(_.condition.contains("SharedTargetAwaitingContributions")) shouldBe 1
        val completed = collect()
        completed.outcome shouldBe "Completed"
        completed.repositories.forall(_.kind == "Terminal") shouldBe true
        completed.repositories.flatMap(_.tip).distinct.size shouldBe 1
        Vector("work.md", "two.md", "main.md", "remote.md").foreach { name =>
          Files.exists(root.resolve("repository/docs").resolve(name)) shouldBe true
        }
        Files.exists(root.resolve("work/docs/two.md")) shouldBe false
        Files.exists(root.resolve("work-two/docs/work.md")) shouldBe false
        var questions = 0
        var pushes = 0
        completed.repositories.foreach { member => prepare(_.currentC(InstanceIdentity(member.sessionId)).map { current =>
          questions += current.history.count(_.causation.value.endsWith(":sync-policy:route:issued"))
          pushes += current.history.count(_.currentProgression.exists(_.value == "sync.NativePushPreparing"))
          if (cohort.sessions.contains(member.sessionId)) {
            current.history.count(_.causation.value == "repository-local-main-phase:local") shouldBe 1
            current.history.count(_.causation.value == "repository-local-main-phase:remote") shouldBe 1
          }
          ()
        }) }
        questions shouldBe 1
        pushes shouldBe 1
        collect() shouldBe completed
      }
    }
    "distinguish unavailable ambiguous and prunable linked participants without granting a mapped path" in {
      val source = RepositorySyncLocalMain.Participant("source", sourceWorkspace, "/fixture/work", "contribution", "origin", "main", "endpoint")
      def worktree(path: String, branch: String, prunable: Boolean = false) = GitWorktree(path,
        Some(take(GitObjectId.parseC("a" * 40))), Some(take(GitBranchName.parseC(branch))), false, false, false, prunable)
      val own = worktree(source.root, source.branch)
      val main = worktree("/fixture/main", "main")
      take(RepositorySyncLocalMain.selectMainC(Vector(own, main), source, None)) shouldBe (main.path -> true)
      RepositorySyncLocalMain.selectMainC(Vector(main), source, None).isSuccess shouldBe false
      RepositorySyncLocalMain.selectMainC(Vector(own), source, None).isSuccess shouldBe false
      RepositorySyncLocalMain.selectMainC(Vector(own, main, main.copy(path = "/fixture/other")), source, None).isSuccess shouldBe false
      RepositorySyncLocalMain.selectMainC(Vector(own, main.copy(prunable = true)), source, None).isSuccess shouldBe false
      val mapped = source.copy(workspace = mainWorkspace, root = "/fixture/mapped", branch = "main")
      take(RepositorySyncLocalMain.selectMainC(Vector(own), source, Some(mapped))) shouldBe (mapped.root -> false)
      RepositorySyncLocalMain.selectMainC(Vector(own, main), source, Some(mapped)).isSuccess shouldBe false
    }
    for (explicit <- Vector(false, true); separate <- Vector(false, true)) {
      s"retain both local histories before remote convergence in one Session, explicit-local-first=$explicit separate-clone=$separate" in {
        val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/local-main-spec").toAbsolutePath), "linked-")
        val scenario = if (separate) "Clone" else "Equal"
        val mapping = if (separate) Map(RepositorySyncLocalMain.mappings.name -> Map(sourceWorkspace -> mainWorkspace).asJson.noSpaces) else Map.empty[String, String]
        _with_runtime_(root, scenario, hostParameters = documents ++ mapping) { (owner, component, actor, prepare) =>
          prepare(_.setupC(_setup_commands_(root, scenario).size))
          def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
            read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "RepositorySyncService", operation = operation, properties = properties), actor)))
          val input = Record.data((Vector("repositoryIdentity" -> "source-repository", "syncMode" -> "FULL_STATE") ++
            Option.when(explicit)("mainIntegration" -> "local-first"))*)
          val session = invoke[V.RepositorySyncStartResult]("startRepositorySync", List(Property("input", input, None))).sessionId
          var sourceHead = ""
          var sourceTree = ""
          prepare(call => RepositorySyncManagedGitObservation.observeC(call.core).map { before =>
            sourceHead = before.head; sourceTree = before.tree; ()
          })
          def advance() = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", List(Property("sessionId", session, None)))
          val prepared = advance()
          prepared.kind shouldBe "Progress"
          prepared.condition shouldBe Some("LocalMainSourcePrepared")
          val integrated = advance()
          integrated.kind shouldBe "Progress"
          integrated.condition shouldBe Some("LocalMainIntegrated")
          Files.exists(root.resolve("repository/docs/work.md")) shouldBe true
          Files.exists(root.resolve("repository/docs/main.md")) shouldBe true
          Files.exists(root.resolve("repository/docs/remote.md")) shouldBe false
          if (!explicit && !separate) {
            var history = Vector.empty[String]
            prepare(_.currentC(InstanceIdentity(session)).map(value => { history = value.history.map(_.causation.value); () }))
            val unavailable = root.resolve("retained-upstream.git")
            Files.move(root.resolve("upstream.git"), unavailable)
            owner.executeOperationResponseInContext(Request.of(component = component.name, service = "RepositorySyncService",
              operation = "advanceRepositorySync", properties = List(Property("sessionId", session, None))), actor).isSuccess shouldBe false
            prepare(_.currentC(InstanceIdentity(session)).map(value => { value.history.map(_.causation.value) shouldBe history; () }))
            Files.exists(root.resolve("repository/docs/work.md")) shouldBe true
            Files.exists(root.resolve("repository/docs/remote.md")) shouldBe false
            Files.move(unavailable, root.resolve("upstream.git"))
          }
          val completed = advance()
          completed.kind shouldBe "Terminal"
          completed.sessionId shouldBe session
          Files.exists(root.resolve("repository/docs/remote.md")) shouldBe true
          Files.exists(root.resolve("work/docs/main.md")) shouldBe false
          prepare(call => for {
            after <- RepositorySyncManagedGitObservation.observeC(call.core)
            current <- call.currentC(InstanceIdentity(session))
          } yield {
            after.head shouldBe sourceHead
            after.tree shouldBe sourceTree
            current.history.count(_.causation.value == "repository-local-main-phase:local") shouldBe 1
            current.history.count(_.causation.value == "repository-local-main-phase:remote") shouldBe 1
            val pending = current.history.filter(_.currentProgression.exists(_.value == "sync.PendingMergePrepared"))
            pending.exists(_.causation.value == "native-repository:" + session + ":local-main-local-pending-merge") shouldBe true
            val remotePending = RepositorySyncLocalMain.phaseHistory(current).filter(
              _.currentProgression.exists(_.value == "sync.PendingMergePrepared"))
            remotePending.nonEmpty shouldBe true
            remotePending.forall(_.causation.value.startsWith("native-repository:" + session + ":local-main-remote-")) shouldBe true
            ()
          })
          advance() shouldBe completed
        }
      }
    }
    "keep an explicit direct route isolated from dirty canonical main" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/local-main-spec").toAbsolutePath), "direct-")
      _with_runtime_(root, "Equal", hostParameters = documents) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "Equal").size))
        Files.writeString(root.resolve("repository/unrelated-main.txt"), "another task remains untouched\n")
        var before = Option.empty[RepositorySyncManagedGitObservation.Observed]
        prepare(call => RepositorySyncManagedGitObservation.observeInWorkspaceC(call.core, mainWorkspace).map(value => { before = Some(value); () }))
        def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "RepositorySyncService", operation = operation, properties = properties), actor)))
        val session = invoke[V.RepositorySyncStartResult]("startRepositorySync", List(Property("input", Record.data(
          "repositoryIdentity" -> "source-repository", "syncMode" -> "FULL_STATE", "mainIntegration" -> "direct"), None))).sessionId
        val completed = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", List(Property("sessionId", session, None)))
        completed.kind shouldBe "Terminal"
        completed.convergence.get.repositoryRoot shouldBe root.resolve("work").toString
        Files.exists(root.resolve("work/docs/main.md")) shouldBe false
        Files.readString(root.resolve("repository/unrelated-main.txt")) shouldBe "another task remains untouched\n"
        prepare(call => for {
          after <- RepositorySyncManagedGitObservation.observeInWorkspaceC(call.core, mainWorkspace)
          current <- call.currentC(InstanceIdentity(session))
        } yield {
          after shouldBe before.get
          current.history.exists(_.causation.value.startsWith("repository-local-main-phase:")) shouldBe false
          ()
        })
      }
    }
    for (choice <- Vector("LocalFirst", "Direct", "Defer", "Cancel")) {
      s"apply actual $choice direction once and keep dirty-work authority separate in the original Session" in {
        val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/local-main-spec").toAbsolutePath), "human-")
        _with_runtime_(root, "Equal", hostParameters = documents) { (owner, component, actor, prepare) =>
          prepare(_.setupC(_setup_commands_(root, "Equal").size))
          val dirty = choice == "LocalFirst"
          if (dirty) {
            Files.writeString(root.resolve("work/docs/accepted.md"), "one intended dirty purpose\n")
            Files.writeString(root.resolve("work/excluded.txt"), "SPECIFICATION-OWNED other task\n")
          }
          def call(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(Request.of(
            component = component.name, service = "RepositorySyncService", operation = operation, properties = properties), actor)
          def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A = read_value[A](take(call(operation, properties)))
          val session = invoke[V.RepositorySyncStartResult]("startRepositorySync", List(Property("input", Record.data(
            "repositoryIdentity" -> "source-repository", "syncMode" -> "FULL_STATE", "mainIntegration" -> "confirm",
            "uncommittedPolicy" -> "confirm"), None))).sessionId
          def advance() = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", List(Property("sessionId", session, None)))
          def answer(view: V.RepositorySyncAdvanceResult, selected: String): Unit = {
            var actual = Option.empty[WorkflowInteraction[RepositorySyncPolicyDecision.Input, Nothing]]
            prepare(_.repositoryPolicyQuestionC(InstanceIdentity(session)).map(value => { actual = Some(value); () }))
            val question = actual.get.current.asInstanceOf[WorkflowContinuation.Decision[RepositorySyncPolicyDecision.Input]].request
            val reference = ContextReference("SPECIFICATION-OWNED-" + selected + "-" + question.continuationId.value, "1")
            val result = ContinuationResult(actual.get.handle, question.runId, question.continuationId, question.expectedRevision,
              question.context.snapshot, TypedValue(RepositorySyncPolicyDecision.answerCodec.typeIdentity,
                RepositorySyncPolicyDecision.Answer(selected, "Explicit specification-owned Human direction for actual captured targets")),
              reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("participant")))
            val properties = take(NativeWorkflowOperationInputFixture.submissionC(result))
            call(RepositorySyncPolicyDecision.completionOperation.operation, properties).isSuccess shouldBe false
            prepare(_.policyHumanEvidenceC(view.human.get.humanDecisionContext.get, result))
            invoke[V.RepositorySyncHumanConflictSubmissionResult](RepositorySyncPolicyDecision.completionOperation.operation, properties).accepted shouldBe true
          }
          val route = advance()
          route.kind shouldBe "Human"
          route.human.get.choices shouldBe Vector("LocalFirst", "Direct", "Defer", "Cancel")
          advance() shouldBe route
          answer(route, choice)
          val next = advance()
          if (Set("Defer", "Cancel")(choice)) {
            next.kind shouldBe "Wait"
            next.condition shouldBe Some(choice)
            advance() shouldBe next
            prepare(_.currentC(InstanceIdentity(session)).map(value => {
              value.history.exists(_.causation.value.startsWith("managed-fetch-intent:")) shouldBe false; ()
            }))
          } else if (choice == "Direct") {
            next.condition shouldBe Some("RouteSelected")
            advance().kind shouldBe "Terminal"
            Files.exists(root.resolve("work/docs/main.md")) shouldBe false
          } else {
            next.kind shouldBe "Human"
            next.human.get.choices shouldBe Vector("AutoCommitPurposes", "Defer", "Cancel")
            answer(next, "AutoCommitPurposes")
            val grouping = advance()
            grouping.resultType shouldBe Some("RepositorySyncPurposeResult")
            var work = Option.empty[WorkflowInteraction[RepositorySyncPurpose.Request, Nothing]]
            prepare(_.repositoryWorkC(InstanceIdentity(session), RepositorySyncPurposeContinuation.completionOperation,
              RepositorySyncPurpose.inputCodec).map(value => { work = Some(value); () }))
            val request = work.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncPurpose.Request]].request
            val proposal = RepositorySyncPurpose.Result("resolved", "One intended document purpose; another task remains excluded",
              Vector(RepositorySyncPurpose.Group("accepted-document", "Accepted document", "Retain accepted document intent", Vector.empty,
                Vector(RepositorySyncPurpose.Content("docs/accepted.md", Some("one intended dirty purpose\n"))), Vector.empty,
                "Only the intended document changes; declared executable inputs remain consumed")), Vector("excluded.txt"))
            val reference = ContextReference("SPECIFICATION-OWNED-purpose", "1")
            val result = ContinuationResult(work.get.handle, request.runId, request.continuationId, request.expectedRevision,
              request.context.snapshot, TypedValue(request.resultType.get.value, proposal), reference, Vector.empty,
              ExecutionEvidence(Vector(reference), Some("SPECIFICATION-OWNED-purpose-author")))
            invoke[V.RepositorySyncConflictSubmissionResult]("submitRepositoryPurposeResult",
              take(NativeWorkflowOperationInputFixture.submissionC(result))).accepted shouldBe true
            val excluded = advance()
            excluded.kind shouldBe "Wait"
            Files.readString(root.resolve("work/excluded.txt")) shouldBe "SPECIFICATION-OWNED other task\n"
            Files.exists(root.resolve("repository/docs/accepted.md")) shouldBe false
            var acceptedHead = ""
            var installed = 0
            prepare(call => for {
              observed <- RepositorySyncManagedGitObservation.observeC(call.core)
              current <- call.currentC(InstanceIdentity(session))
            } yield {
              acceptedHead = observed.head
              installed = current.history.count(_.currentProgression.exists(_.value == "sync.PurposeInstalled"))
              installed shouldBe 1
              current.history.count(_.causation.value.endsWith(":sync-policy:route:issued")) shouldBe 1
              ()
            })
            // Explicit fixture-owned resolution of the separately excluded task.
            Files.delete(root.resolve("work/excluded.txt"))
            advance().condition shouldBe Some("LocalMainSourcePrepared")
            advance().condition shouldBe Some("LocalMainIntegrated")
            advance().kind shouldBe "Terminal"
            prepare(call => for {
              observed <- RepositorySyncManagedGitObservation.observeC(call.core)
              current <- call.currentC(InstanceIdentity(session))
            } yield {
              observed.head shouldBe acceptedHead
              current.history.count(_.currentProgression.exists(_.value == "sync.PurposeInstalled")) shouldBe installed
              current.history.count(_.causation.value.endsWith(":sync-policy:route:issued")) shouldBe 1
              ()
            })
            Files.readString(root.resolve("repository/docs/accepted.md")) shouldBe "one intended dirty purpose\n"
          }
        }
      }
    }
  }
}
