package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{GitOperation, SbtOperation}
import org.goldenport.cncf.validation.ValidationDiscoveryV1 as D
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, ProgressionReference}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workspace.{SessionCandidateWorkspace, SessionCandidateWorkspaceReference, SessionCandidateWorkspaceState}
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncManagedFetch as F
import RepositorySyncNativeMergeValidation as C
import RepositorySyncNativeProgressStore as N
import RepositorySyncPurpose as P
import RepositorySyncPurpose.given
import RepositorySyncPurposeWorkspace as W
import SplitPhaseExplicitPartitionFixture.take

/** Real local candidates use host v7 resource admission and native UoW effects.
  * These isolated fixtures do not qualify an installed consumer configuration.
  * @since   Nov. 12, 2026
  * @version Nov. 12, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class RepositorySyncSessionCandidateWorkspaceSpec extends AnyWordSpec with GivenWhenThen
    with Phase69ConfiguredRepositoryFixture {
  private val _alias = "purpose-candidate"
  private val _parent = "purpose-parent"
  private val _compile = C.Check("ordinary-compile", 1, WorkflowSbtValidation.Kind.Compile)
  private val _product = C.Check("ordinary-product", 2, WorkflowSbtValidation.Kind.TestOnly)
  private val _checks = Vector(_compile, _product)
  private val _suite = "candidatefixture.ProductCheckSpec"
  private val _inputs = Vector("value.txt", "src", "build.sbt", "project")
  private val _coverage = RepositorySyncProgramImpact.Policy(_checks.map(check =>
    RepositorySyncProgramImpact.Coverage(check, Vector("value.txt"), _inputs,
      "Ordinary Compile and selected ScalaTest consume the actual candidate inputs")), _checks,
    Vector("build.sbt", "project"), "Actual declared fixture product coverage", Vector.empty)

  private def _parameters(root: Path): Map[String, String] = Map(
    RepositorySyncDirectoryList.declaration.name -> Vector(root.resolve("repository").toString).asJson.noSpaces,
    RepositorySyncDocumentInputs.declaration.name -> RepositorySyncDocumentInputs.Policy(Vector("docs"), _inputs,
      "Documents are not consumed; declared program inputs take precedence").asJson.noSpaces,
    RepositorySyncProgramImpact.declaration.name -> _coverage.asJson.noSpaces)

  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val base = super.process_plan(root, "Equal", goal)
    val parent = Files.createDirectories(root.resolve("managed-candidates"))
    val placeholder = Files.createDirectories(root.resolve("candidate-alias"))
    val git = TypedGitHostPlanFixture.plan(Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString))
    val observations = git.hcursor.get[Vector[Json]]("gitPrograms").toOption.get.filter(row =>
      Set("root", "head", "index-tree", "status", "check-pending-index", "pending-diff")(row.hcursor.get[String]("operation").toOption.get))
      .map(row => row.mapObject(fields => fields.add("target", _alias.asJson)
        .add("identity", ("candidate-" + fields("operation").get.asString.get).asJson)
        .add("capability", ("candidate-" + fields("operation").get.asString.get).asJson)))
    val sbt = Vector(
      TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), operation = SbtOperation.Compile),
      TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(2), operation = SbtOperation.TestOnly,
        testClasses = Vector(_suite))).flatMap(_.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get)
      .map(row => row.mapObject { fields =>
        val identity = "candidate-" + fields("operation").get.asString.get
        fields.add("target", _alias.asJson).add("identity", identity.asJson).add("capability", identity.asJson)
      })
    val owner = SmWorkflowComponent.componentId.name
    val capabilities = (observations ++ sbt).map(_.hcursor.get[Json]("capability").toOption.get)
    val policies = if (scenario == "MissingPolicy") Vector.empty else Vector(Json.obj(
      "componentId" -> owner.asJson, "parent" -> _parent.asJson, "alias" -> _alias.asJson))
    base.mapObject(fields => fields.add("schema", "cncf.runtime-process-execution.v7".asJson)
      .add("sessionCandidateWorkspaces", Json.fromValues(policies)).add("validationChecks", Json.arr())
      .add("sbtPrograms", Json.fromValues(sbt))
      .add("gitPrograms", fields("gitPrograms").get.mapArray(_ ++ observations))
      .add("workspaces", fields("workspaces").get.mapArray(_ ++ Vector(
        Json.obj("identity" -> _parent.asJson, "root" -> parent.toString.asJson),
        Json.obj("identity" -> _alias.asJson, "root" -> placeholder.toString.asJson))))
      .add("workspaceGrants", fields("workspaceGrants").get.mapArray(_ ++ Vector(_parent, _alias).map(identity =>
        Json.obj("componentId" -> owner.asJson, "workspaceId" -> identity.asJson, "access" -> "manage".asJson))))
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant =>
        grant.add("capabilities", grant("capabilities").get.mapArray(_ ++ capabilities)))))))
  }

  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    val repository = root.resolve("repository")
    Files.createDirectories(repository.resolve("project"))
    Files.createDirectories(repository.resolve("src/main/scala/candidatefixture"))
    Files.createDirectories(repository.resolve("src/test/scala"))
    Files.createDirectories(repository.resolve("docs"))
    Files.writeString(repository.resolve("project/build.properties"), "sbt.version=1.9.7\n")
    Files.writeString(repository.resolve(".gitignore"), "target/\nproject/target/\nproject/project/\n.bsp/\n")
    Files.writeString(repository.resolve("build.sbt"), """scalaVersion := "2.12.18"
      |libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
      |""".stripMargin)
    Files.writeString(repository.resolve("src/main/scala/candidatefixture/Value.scala"),
      "package candidatefixture\nobject Value { val expected = \"expected\\n\" }\n")
    Files.writeString(repository.resolve("src/test/scala/ProductCheck.scala"), """package candidatefixture
      |import java.nio.file.{Files, Paths}
      |import org.scalatest.GivenWhenThen
      |import org.scalatest.matchers.should.Matchers
      |import org.scalatest.wordspec.AnyWordSpec
      |final class ProductCheckSpec extends AnyWordSpec with Matchers with GivenWhenThen {
      |  "The actual candidate" should {
      |    "contain the accepted value in its own working directory" in {
      |      Given("the materialized candidate working directory")
      |      val root = Paths.get("").toAbsolutePath.normalize
      |      println("PRODUCT_CHECK_ENTERED " + root)
      |      When("the actual candidate runtime input is read")
      |      val observed = Files.readString(root.resolve("value.txt"))
      |      Then("it contains the accepted expected value")
      |      observed shouldBe Value.expected
      |      println("PRODUCT_CHECK_PASSED")
      |    }
      |  }
      |}
      |""".stripMargin)
    Files.writeString(repository.resolve("value.txt"), "incorrect\n")
    Files.writeString(repository.resolve("docs/purpose.md"), "original document\n")
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] =
    super._setup_commands_(root, "Equal").patch(2, Vector(Vector("-C", root.resolve("repository").toString,
      "add", "--", "docs/purpose.md", "src/main/scala/candidatefixture/Value.scala")), 0)

  private def _store_c(call: Setup, root: Path, identity: InstanceIdentity): Consequence[N.Store] = {
    val definition = take(RepositorySyncDefinitionBinding.bindC)
    val declaration = F.Declaration(_configuration_, _collection_("instances"), _collection_("continuations"),
      ThreeWorkflowNormalHostFixture.definition(root).repository.input,
      Target(root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString),
      documentInputs = Some(io.circe.parser.decode[RepositorySyncDocumentInputs.Policy](
        _parameters(root)(RepositorySyncDocumentInputs.declaration.name)).toOption.get),
      purposeValidation = Some(StandardWorkflowRuntime.normalRepositoryValidation(Some(_coverage))))
    for {
      session <- F.sessionC(identity.value, declaration, definition, call.core)
      store <- N.bindC(session, declaration, call.core, _ => Consequence.unit)
    } yield store
  }
  private def _root(name: String): Path = Files.createTempDirectory(
    Files.createDirectories(Path.of("target/session-candidate-workspace").toAbsolutePath), name + "-")

  // Unsupported selection inputs only; no discovery or execution result is asserted.
  private def _unsupported_checks(root: Path, candidate: P.Candidate): Vector[C.Check] = {
    val scope = D.DiscoveryScope("candidate-inputs", "1", Vector(D.ArtifactScope("candidatefixture", "candidatefixture-tests",
      "1", candidate.tree, root.resolve("repository/target/scala-2.12/test-classes"), "candidatefixture")))
    val reference = D.OperationReference("candidatefixture", "product", "accepted-value")
    val operation = D.Operation(reference, D.Provenance("candidate-inputs", "1", candidate.tree, "1"),
      D.ScalaTestSelector(_suite, "The actual candidate should contain the accepted value in its own working directory"),
      None, Vector.empty, Some(D.FrameworkFacts(Set.empty, false)))
    val inventory = D.Inventory(scope, D.Completeness.COMPLETE, Vector(operation), Vector.empty)
    val profile = GoalPhaseNativeMetadata.Profile(scope, D.SelectionQuery(), Vector.empty)
    Vector(_product.copy(ordinaryDiscovery = true), _product.copy(metadata = Some(profile)),
      _product.copy(invocation = Some(C.Invocation(_product.identity, inventory, reference, "develop"))))
  }

  "Session-owned Purpose candidate workspace" should {
    for (document <- Vector(true, false)) {
      s"materialize and reopen the actual candidate with ordinary validation, documentation=$document" in {
        val root = _root(if (document) "document" else "product")
        val path = if (document) "docs/purpose.md" else "value.txt"
        val content = if (document) "one accepted document purpose\n" else "expected\n"
        var retained = Option.empty[N.Stage]
        var retainedresult = Option.empty[V.RepositoryDirectoryBatchResult]
        var candidateroot = ""
        Given("a normal v7 candidate parent/alias and ordinary coverage, with distinct original source work preserved for the product case")
        val sourcecontent = if (document) content else "pending incorrect\n"
        _parameters(root).contains(P.declaration.name) shouldBe false
        _with_runtime_(root, "Equal", hostParameters = _parameters(root)) { (owner, component, actor, prepare) =>
          prepare(_.setupC(_setup_commands_(root, "Equal").size))
          Files.writeString(root.resolve("repository").resolve(path), sourcecontent)
          def _invoke_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
            read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "RepositorySyncService", operation = operation, properties = properties), actor)))
          val issued = _invoke_[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.syncOperation,
            List(Property("input", Record.data("syncMode" -> "FULL_STATE"), None)))
          val child = InstanceIdentity(issued.repositories.head.sessionId)
          var work = Option.empty[WorkflowInteraction[P.Request, Nothing]]
          prepare(_.repositoryWorkC(child, RepositorySyncPurposeContinuation.completionOperation, P.inputCodec)
            .map(value => { work = Some(value); () }))
          val request = work.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[P.Request]].request
          if (!document) request.input.get.value.files.find(_.path == path).get.worktree shouldBe Some(sourcecontent)
          val proposal = P.Result("resolved", "One original candidate purpose", Vector(P.Group("one-purpose", "One purpose",
            "Apply one purpose", Vector.empty, Vector(P.Content(path, Some(content))), Vector.empty,
            "The actual changed file has one independent meaning")), Vector.empty)
          val reference = ContextReference("managed-purpose-author", "1")
          val reply = ContinuationResult(work.get.handle, request.runId, request.continuationId, request.expectedRevision,
            request.context.snapshot, TypedValue(request.resultType.get.value, proposal), reference, Vector.empty,
            ExecutionEvidence(Vector(reference), Some("managed-purpose-specification")))
          When("the original grouping is accepted and normal aggregate continuation owns candidate allocation and effects")
          _invoke_[V.RepositorySyncConflictSubmissionResult]("submitRepositoryPurposeResult",
            take(NativeWorkflowOperationInputFixture.submissionC(reply))).accepted shouldBe true
          val result = _invoke_[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.syncOperation, List(
            Property("sessionId", issued.sessionId, None), Property("input", Record.data("syncMode" -> "FULL_STATE"), None)))
          retainedresult = Some(result)
          prepare(_.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, child, root,
            "purpose-1-materialized").map(value => { retained = value; () }))
          Then("the same child installs the candidate in a real Git worktree while preserving its original source work")
          withClue(s"Actual managed batch: $result\n") {
            if (document) {
              result.outcome shouldBe "Completed"
              result.repositories.head.kind shouldBe "Terminal"
              result.repositories.head.outcome shouldBe "converged"
            } else {
              result.outcome shouldBe "AttentionRequired"
              result.repositories.head.kind shouldBe "Wait"
              result.repositories.head.condition shouldBe Some("purpose-unfinished")
              result.repositories.head.diagnostic.get should include("value.txt")
            }
            result.repositories.head.sessionId shouldBe child.value
          }
          retained should not be empty
          val progress = retained.get.payload.purposes.get
          val binding = progress.managedWorkspace.get
          binding.reference.session shouldBe child.value
          binding.reference.source shouldBe "sm-workflow-repository"
          binding.reference.alias shouldBe _alias
          binding.materialization should not be empty
          progress.asJson.as[P.Progress].toOption.get shouldBe progress
          N.codec.decode(N.codec.encode(retained.get.payload)).toOption.get shouldBe retained.get.payload
          progress.asJson.hcursor.downField("managedWorkspace").downField("reference").keys.get.toSet shouldBe
            Set("component", "source", "parent", "alias", "session", "candidate")
          prepare(call => _store_c(call, root, child).flatMap(store => W.scopedStoreC(store, progress)).flatMap { selected => for {
            actual <- WorkflowManagedProcessExecution.executeGitC(selected.core, GitOperation.Root, _alias).flatMap(_.valueC)
            head <- WorkflowManagedProcessExecution.executeGitC(selected.core, GitOperation.Head, _alias).flatMap(_.valueC)
          } yield {
            candidateroot = actual
            head.value shouldBe progress.candidate.get.commit
            Files.isRegularFile(Path.of(actual).resolve(".git")) shouldBe true
            Files.readString(Path.of(actual).resolve(path)) shouldBe content
            actual should not be root.resolve("repository").toString
            actual should not be root.resolve("candidate-alias").toString
            ()
          } })
          Files.readString(root.resolve("repository/.git/refs/heads/main")).trim shouldBe progress.candidate.get.commit
          Files.readString(root.resolve("repository").resolve(path)) shouldBe sourcecontent
          prepare(_.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, child, root,
            "purpose-1-installed").map { saved =>
            saved should not be empty
            saved.get.record.currentProgression shouldBe Some(ProgressionReference("sync.PurposeInstalled"))
            val installed = saved.get.payload.purposes.get
            installed.cursor shouldBe 1
            installed.plan shouldBe progress.plan
            installed.commits shouldBe Vector(progress.candidate.get.commit)
            ()
          })
          prepare(_.currentC(child).map { current =>
            Vector("PurposeGrouped", "PurposePreparing", "PurposePrepared", "PurposeInstalled").foreach { stage =>
              current.history.count(_.currentProgression.contains(ProgressionReference("sync." + stage))) shouldBe 1
            }
            ()
          })
          prepare(_.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, child, root,
            "purpose-1-assessment").map { saved =>
            val checked = saved.get.payload.purposes.get
            checked.receipts.head.identity shouldBe "index-local"
            checked.receipts.head.exitCode shouldBe Some(0)
            if (document) checked.receipts.drop(1) shouldBe empty
            else {
              checked.selection.get.checks.map(_.identity) shouldBe _checks.map(_.identity)
              checked.receipts.drop(1) should have size 2
              C.productSuccessful(checked.receipts(1), _compile.kind) shouldBe true
              checked.receipts(1).sbt.get.kind shouldBe WorkflowSbtValidation.Kind.Compile
              val receipt = checked.receipts.last
              C.productSuccessful(receipt, _product.kind) shouldBe true
              val facts = receipt.sbt.get
              facts.kind shouldBe WorkflowSbtValidation.Kind.TestOnly
              facts.selection.get.testClasses shouldBe Vector(_suite)
              facts.selection.get.completedSuites shouldBe 1
              facts.selection.get.testsRun should be > 0L
              receipt.stdout should include("PRODUCT_CHECK_ENTERED " + candidateroot)
              receipt.stdout should include("PRODUCT_CHECK_PASSED")
            }
            ()
          })
          if (document) prepare(_.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, child, root,
            "purpose-1-check-0-intent").map { saved => saved shouldBe None; () })
        }
        When("the same isolated runtime reopens and rebinds only the known materialized candidate")
        _with_runtime_(root, "Equal", hostParameters = _parameters(root)) { (owner, component, actor, prepare) =>
          val stage = retained.get
          val progress = stage.payload.purposes.get
          val binding = progress.managedWorkspace.get
          val child = InstanceIdentity(retainedresult.get.repositories.head.sessionId)
          var actualroot = ""
          var refusals = Vector.empty[Boolean]
          var policyrefusals = Vector.empty[Boolean]
          prepare(call => _store_c(call, root, child).flatMap { store => for {
            selected <- W.scopedStoreC(store, progress.asJson.as[P.Progress].toOption.get)
            actual <- WorkflowManagedProcessExecution.executeGitC(selected.core, GitOperation.Root, _alias).flatMap(_.valueC)
            reference <- SessionCandidateWorkspaceReference.fromRecordC(binding.reference)
          } yield {
            actualroot = actual
            val currentpolicy = StandardWorkflowRuntime.normalRepositoryValidation(Some(_coverage))
            val requiredreview = currentpolicy.copy(assessment = currentpolicy.assessment.map(_.copy(detailedReview = "required")))
            val reviewstore = store.withDeclaration(store.declaration.copy(purposeValidation = Some(requiredreview)))
            policyrefusals = Vector(W.requireCurrentBindingC(reviewstore, progress).isFaillure)
            if (!document) {
              val additional = C.Check("additional-required-compile", 3, WorkflowSbtValidation.Kind.Compile)
              val coverage = _coverage.copy(coverage = _coverage.coverage :+
                RepositorySyncProgramImpact.Coverage(additional, Vector("value.txt"), _inputs,
                  "Current ordinary policy additionally requires this independent compile check"),
                fullChecks = _coverage.fullChecks :+ additional)
              val changedpolicy = StandardWorkflowRuntime.normalRepositoryValidation(Some(coverage))
              val changedstore = store.withDeclaration(store.declaration.copy(purposeValidation = Some(changedpolicy)))
              policyrefusals = policyrefusals :+ W.requireCurrentBindingC(changedstore, progress).isFaillure
            }
            refusals = Vector(
              W.scopedStoreC(store, progress.copy(managedWorkspace = Some(binding.copy(reference = binding.reference.copy(session = "foreign:session"))))).isFaillure,
              W.scopedStoreC(store, progress.copy(managedWorkspace = Some(binding.copy(reference = binding.reference.copy(candidate = "foreign-candidate"))))).isFaillure,
              W.scopedStoreC(store, progress.copy(managedWorkspace = Some(binding.copy(materialization = None)))).isFaillure,
              SessionCandidateWorkspace.reacquireC(store.core.executionContext, reference, binding.reference, SessionCandidateWorkspaceState.Unknown).isFaillure,
              SessionCandidateWorkspace.reacquireC(store.core.executionContext, reference, binding.reference, SessionCandidateWorkspaceState.Empty).isFaillure)
            ()
          } })
          val result = read_value[V.RepositoryDirectoryBatchResult](take(owner.executeOperationResponseInContext(Request.of(
            component = component.name, service = "RepositorySyncService", operation = RepositorySyncDirectoryList.syncOperation,
            properties = List(Property("sessionId", retainedresult.get.sessionId, None),
              Property("input", Record.data("syncMode" -> "FULL_STATE"), None))), actor)))
          Then("the installed candidate and prior batch outcome recollect unchanged and refused selections gain no effects")
          actualroot shouldBe candidateroot
          policyrefusals shouldBe Vector.fill(if (document) 1 else 2)(true)
          refusals shouldBe Vector.fill(5)(true)
          result shouldBe retainedresult.get
          Files.readString(root.resolve("repository/.git/refs/heads/main")).trim shouldBe progress.candidate.get.commit
          Files.readString(Path.of(actualroot).resolve(path)) shouldBe content
          Files.readString(root.resolve("repository").resolve(path)) shouldBe sourcecontent
          progress.asJson.mapObject(_.add("managedWorkspace", Json.Null)).as[P.Progress].isLeft shouldBe true
          progress.copy(managedWorkspace = None).asJson.hcursor.downField("managedWorkspace").focus shouldBe None
        }
      }
    }

    for (occupied <- Vector(false, true)) {
      s"preserve the original setup candidate and retain a real exclusive-allocation refusal without replay, occupied=$occupied" in {
        val root = _root(if (occupied) "occupied" else "setup")
        var waiting = Option.empty[V.RepositoryDirectoryBatchResult]
        var prepared = Option.empty[N.Stage]
        var sourcehead = ""
        Given("the actual fixture lacks only the v7 logical candidate policy and retains one genuinely prepared program candidate")
        _with_runtime_(root, "MissingPolicy", hostParameters = _parameters(root)) { (owner, component, actor, prepare) =>
          prepare(_.setupC(_setup_commands_(root, "Equal").size))
          Files.writeString(root.resolve("repository/value.txt"), "pending incorrect\n")
          sourcehead = Files.readString(root.resolve("repository/.git/refs/heads/main")).trim
          def _invoke_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
            read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "RepositorySyncService", operation = operation, properties = properties), actor)))
          val issued = _invoke_[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.syncOperation,
            List(Property("input", Record.data("syncMode" -> "FULL_STATE"), None)))
          val child = InstanceIdentity(issued.repositories.head.sessionId)
          var work = Option.empty[WorkflowInteraction[P.Request, Nothing]]
          prepare(_.repositoryWorkC(child, RepositorySyncPurposeContinuation.completionOperation, P.inputCodec)
            .map(value => { work = Some(value); () }))
          val request = work.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[P.Request]].request
          val proposal = P.Result("resolved", "One actual program candidate", Vector(P.Group("one-purpose", "Program value", "Correct program input",
            Vector.empty, Vector(P.Content("value.txt", Some("expected\n"))), Vector.empty,
            "Correct the one actual changed runtime input")), Vector.empty)
          val reference = ContextReference("managed-setup-author", "1")
          val reply = ContinuationResult(work.get.handle, request.runId, request.continuationId, request.expectedRevision,
            request.context.snapshot, TypedValue(request.resultType.get.value, proposal), reference, Vector.empty,
            ExecutionEvidence(Vector(reference), Some("managed-setup-specification")))
          When("normal continuation reaches the missing logical policy before a workspace allocation intent")
          _invoke_[V.RepositorySyncConflictSubmissionResult]("submitRepositoryPurposeResult",
            take(NativeWorkflowOperationInputFixture.submissionC(reply))).accepted shouldBe true
          val result = _invoke_[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.syncOperation, List(
            Property("sessionId", issued.sessionId, None), Property("input", Record.data("syncMode" -> "FULL_STATE"), None)))
          waiting = Some(result)
          Then("the concrete setup wait retains the one prepared candidate with no workspace intent or result")
          result.repositories.head.condition shouldBe Some("purpose-materialization-preflight")
          result.repositories.head.diagnostic.get should include("v7 sessionCandidateWorkspaces")
          prepare(_.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, child, root,
            "purpose-1-prepared").map { saved =>
            prepared = saved
            saved.get.payload.purposes.get.managedWorkspace shouldBe None
            ()
          })
          prepare(_.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, child, root,
            "purpose-1-materialize-intent").map { saved => saved shouldBe None; () })
          Files.readString(root.resolve("repository/value.txt")) shouldBe "pending incorrect\n"
        }
        When("the host policy is corrected, unsupported selections are refused and the exact aggregate revision resumes")
        _with_runtime_(root, "Equal", hostParameters = _parameters(root)) { (owner, component, actor, prepare) =>
          val original = waiting.get
          val originalstage = prepared.get
          val progress = originalstage.payload.purposes.get
          val child = InstanceIdentity(original.repositories.head.sessionId)
          var selectiondiagnostics = Vector.empty[String]
          prepare(call => _store_c(call, root, child).map { store =>
            selectiondiagnostics = _unsupported_checks(root, progress.candidate.get).map { unsupported =>
              val coverage = _coverage.copy(
                coverage = _coverage.coverage.map(row => if (row.check.identity == _product.identity) row.copy(check = unsupported) else row),
                fullChecks = _coverage.fullChecks.map(check => if (check.identity == _product.identity) unsupported else check))
              val policy = StandardWorkflowRuntime.normalRepositoryValidation(Some(coverage))
              val selectedstore = store.withDeclaration(store.declaration.copy(purposeValidation = Some(policy)))
              W.preflightC(selectedstore, progress, selectedstore.declaration.purposeValidation) match {
                case Consequence.Failure(conclusion) => conclusion.show
                case Consequence.Success(_) => "Unexpected unsupported selection success"
              }
            }
            ()
          })
          prepare(_.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, child, root,
            "purpose-1-materialize-intent").map { saved => saved shouldBe None; () })
          prepare(_.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, child, root,
            "purpose-1-materialize-result").map { saved => saved shouldBe None; () })
          if (occupied) prepare(call => _store_c(call, root, child).flatMap { store => for {
            binding <- W.preflightC(store, progress, store.declaration.purposeValidation)
            _ <- W.allocateC(store.core, binding)
          } yield () }) // CNCF fixture arrangement only; no native materialization claim or storage write.
          def _invoke_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
            read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "RepositorySyncService", operation = operation, properties = properties), actor)))
          val result = _invoke_[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.refreshOperation,
            List(Property("sessionId", original.sessionId, None), Property("revision", original.resultReference.revision, None)))
          Then("supported policy installs the candidate with ongoing source work preserved, while an actual post-intent refusal stays unresolved")
          selectiondiagnostics should have size 3
          selectiondiagnostics.foreach { diagnostic =>
            diagnostic should include("ValidationSetupRequired")
            diagnostic should include("statically selected ordinary typed check")
          }
          result.repositories.head.sessionId shouldBe child.value
          if (!occupied) {
            result.outcome shouldBe "AttentionRequired"
            result.repositories.head.kind shouldBe "Wait"
            result.repositories.head.condition shouldBe Some("purpose-unfinished")
            result.repositories.head.diagnostic.get should include("value.txt")
            Files.readString(root.resolve("repository/.git/refs/heads/main")).trim shouldBe progress.candidate.get.commit
            Files.readString(root.resolve("repository/value.txt")) shouldBe "pending incorrect\n"
            prepare(_.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, child, root,
              "purpose-1-installed").map { saved =>
              saved should not be empty
              saved.get.record.currentProgression shouldBe Some(ProgressionReference("sync.PurposeInstalled"))
              saved.get.payload.purposes.get.cursor shouldBe 1
              saved.get.payload.purposes.get.commits shouldBe Vector(progress.candidate.get.commit)
              ()
            })
          } else {
            result.outcome shouldBe "AttentionRequired"
            result.repositories.head.kind shouldBe "Wait"
            result.repositories.head.condition shouldBe Some("purpose-effect-unfinished")
            var failed = Option.empty[N.Stage]
            prepare(_.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, child, root,
              "purpose-1-materialize-intent").map { saved =>
              saved.get.payload.stage shouldBe "PurposeMaterializing"
              val pending = saved.get.payload.purposes.get
              pending.candidate shouldBe progress.candidate
              pending.managedWorkspace.get.materialization shouldBe None
              pending.effects shouldBe progress.effects
              ()
            })
            prepare(_.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, child, root,
              "purpose-1-materialize-result").map { saved =>
              failed = saved
              saved.get.payload.stage shouldBe "PurposeUnfinished"
              val unfinished = saved.get.payload.purposes.get
              unfinished.candidate shouldBe progress.candidate
              unfinished.managedWorkspace.get.materialization shouldBe None
              val rejection = unfinished.effects.last
              unfinished.effects.dropRight(1) shouldBe progress.effects
              rejection.hcursor.get[String]("kind").toOption shouldBe Some("managed-materialization-rejection")
              rejection.hcursor.get[String]("diagnostic").toOption.get.toLowerCase should include("occupied")
              rejection.hcursor.downField("process").focus shouldBe None
              rejection.hcursor.downField("outcome").focus shouldBe None
              ()
            })
            val passive = _invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync", List(Property("sessionId", child.value, None)))
            passive.kind shouldBe "Wait"
            val recollected = _invoke_[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.syncOperation, List(
              Property("sessionId", result.sessionId, None), Property("input", Record.data("syncMode" -> "FULL_STATE"), None)))
            recollected shouldBe result
            prepare(_.currentC(child).map { current =>
              current.revision shouldBe failed.get.record.revision
              current.history shouldBe failed.get.record.history
              ()
            })
            prepare(_.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, child, root,
              "purpose-1-materialize-result").map { saved => saved.get.payload shouldBe failed.get.payload; () })
            Vector("purpose-1-materialized", "purpose-1-check-0-intent", "purpose-1-installed").foreach { key =>
              prepare(_.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, child, root,
                key).map { saved => saved shouldBe None; () })
            }
            Files.readString(root.resolve("repository/.git/refs/heads/main")).trim shouldBe sourcehead
            Files.readString(root.resolve("repository/value.txt")) shouldBe "pending incorrect\n"
          }
          prepare(_.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, child, root,
            "purpose-1-prepared").map { saved =>
            saved.get.payload shouldBe originalstage.payload
            saved.get.record.identity shouldBe originalstage.record.identity
            saved.get.record.revision shouldBe originalstage.record.revision
            saved.get.record.history shouldBe originalstage.record.history
            ()
          })
          prepare(_.currentC(child).map { current =>
            Vector("PurposeGrouped", "PurposePreparing", "PurposePrepared").foreach { stage =>
              current.history.count(_.currentProgression.contains(ProgressionReference("sync." + stage))) shouldBe 1
            }
            current.history.count(_.currentProgression.contains(ProgressionReference("sync.PurposeInstalled"))) shouldBe (if (occupied) 0 else 1)
            ()
          })
          _invoke_[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.readOperation,
            List(Property("sessionId", original.sessionId, None), Property("revision", original.resultReference.revision, None))) shouldBe original
        }
      }
    }
  }
}
