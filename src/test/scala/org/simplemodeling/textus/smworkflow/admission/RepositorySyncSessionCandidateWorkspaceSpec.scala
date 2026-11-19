package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, LinkOption, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{GitOperation, SbtOperation}
import org.goldenport.cncf.validation.ValidationDiscoveryV1 as D
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, InstanceRecord, ProgressionReference}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workspace.{SessionCandidateWorkspace, SessionCandidateWorkspaceReference, SessionCandidateWorkspaceState}
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.preparation.NativeRepositoryValidationContinuationPreparation
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncManagedFetch as F
import RepositorySyncFormalFailureContinuation as R
import RepositorySyncNativeMergeValidation as C
import RepositorySyncNativeProgressStore as N
import RepositorySyncPurpose as P
import RepositorySyncPurpose.given
import RepositorySyncPurposeWorkspace as W
import SplitPhaseExplicitPartitionFixture.take

/** Real local candidates use host V7 admission or V8 initialization and native UoW effects.
  * These isolated fixtures do not qualify an installed consumer configuration.
  * @since   Nov. 12, 2026
  * @version Nov. 17, 2026
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
    val base = Phase69ConfiguredRepositoryFixture.withoutManagedCandidates(super.process_plan(root, "Equal", goal))
    val initialized = scenario == "Initialized"
    val parent = root.resolve("managed-candidates")
    val placeholder = root.resolve("candidate-alias")
    if (!initialized) {
      Files.createDirectories(parent)
      Files.createDirectories(placeholder)
    }
    val git = TypedGitHostPlanFixture.plan(Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString))
    val observations = git.hcursor.get[Vector[Json]]("gitPrograms").toOption.get.filter(row =>
      Set("root", "head", "index-tree", "status", "check-pending-index", "pending-diff")(row.hcursor.get[String]("operation").toOption.get))
      .map(row => row.mapObject(fields => fields.add("target", _alias.asJson)
        .add("identity", ("candidate-" + fields("operation").get.asString.get).asJson)
        .add("capability", ("candidate-" + fields("operation").get.asString.get).asJson)))
    val ordinary = Set("DeclarationRead", "ValidationReconstruction")(scenario)
    val sourceprograms = Vector(
      TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), operation = SbtOperation.Compile),
      TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(2),
        operation = if (ordinary) SbtOperation.Test else SbtOperation.TestOnly,
        testClasses = if (ordinary) Vector.empty else Vector(_suite))
    ).appendedAll(Option.when(ordinary)(TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable,
      Vector(3), operation = SbtOperation.FixCheck))).flatMap(_.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get)
    val candidateprograms = sourceprograms.map(row => row.mapObject { fields =>
        val identity = "candidate-" + fields("operation").get.asString.get
        fields.add("target", _alias.asJson).add("identity", identity.asJson).add("capability", identity.asJson)
      })
    val sbt = if (ordinary) sourceprograms ++ candidateprograms else candidateprograms
    val owner = SmWorkflowComponent.componentId.name
    val capabilities = (observations ++ sbt).map(_.hcursor.get[Json]("capability").toOption.get)
    val policies = if (scenario == "MissingPolicy") Vector.empty else Vector(Json.obj(
      "componentId" -> owner.asJson, "parent" -> _parent.asJson, "alias" -> _alias.asJson))
    val planned = base.mapObject(fields => fields.add("schema",
      (if (initialized) "cncf.runtime-process-execution.v8" else "cncf.runtime-process-execution.v7").asJson)
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
    if (!initialized) planned else Phase69ConfiguredRepositoryFixture.withInitializationSources(
      planned.mapObject(_.add("workspaceInitializations", Json.arr(Json.obj(
        "componentId" -> owner.asJson, "anchor" -> root.toString.asJson, "sources" -> Json.arr(),
        "parent" -> _parent.asJson, "alias" -> _alias.asJson,
        "parentPath" -> "managed-candidates".asJson, "aliasPath" -> "candidate-alias".asJson)))))
  }

  // A historical-format fixture is materialized through the same native UoW,
  // then round-tripped without rewriting any real consumer Session.
  private def _legacy_progress_c(store: N.Store, progress: P.Progress): Consequence[P.Progress] = for {
    binding <- W.preflightC(store, progress, store.declaration.purposeValidation)
    scoped <- W.allocateC(store.core, binding)
    candidate <- P.producerC(progress.candidate.get)
    group = RepositorySyncPurposeOwnership.effectiveGroup(progress, progress.plan.get.groups(progress.cursor))
    identity <- org.goldenport.cncf.processexecution.GitSelectionId.parseC(group.identity)
    paths <- group.content.foldLeft(Consequence.success(Vector.empty[org.goldenport.cncf.processexecution.GitPath])) { (prior, content) => for {
      values <- prior
      path <- org.goldenport.cncf.processexecution.GitPath.parseC(content.path)
    } yield values :+ path }
    selection = org.goldenport.cncf.processexecution.GitSelectionParameters(identity, paths, Some(group.message))
    alias <- org.goldenport.cncf.workspace.WorkspaceId.parseC(binding.slot.workspace)
    actual <- WorkflowManagedProcessExecution.executePurposeC(scoped,
      GitOperation.MaterializePurposeCandidate(identity, candidate, alias), selection)
    observed <- W.materializedC(progress, binding, actual)
  } yield progress.copy(sessionWorkspace = None, managedWorkspace = Some(observed))

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
    if (scenario == "DeclarationRead") Files.writeString(repository.resolve("project.yaml"), "project: {}\n")
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
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
    val paths = Vector("docs/purpose.md", "src/main/scala/candidatefixture/Value.scala") ++
      Option.when(scenario == "DeclarationRead")("project.yaml")
    super._setup_commands_(root, "Equal").patch(2, Vector(Vector("-C", root.resolve("repository").toString,
      "add", "--") ++ paths), 0)
  }

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

  private def _encoded_record(record: InstanceRecord): String = take(WorkflowInstanceRecordJsonV1.encodeC(record))

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
    "report each declaration read failure and resume the same prepared candidate" in {
      Given("ordinary Compile and Test bindings and a Purpose candidate that needs project declaration discovery")
      val root = _root("declaration-read")
      val repository = root.resolve("repository")
      val build = repository.resolve("build.sbt")
      val declaration = repository.resolve("project.yaml")
      val resourcekey = "textus.resource.url.file.roots"
      val parameters = _parameters(root) - RepositorySyncDocumentInputs.declaration.name -
        RepositorySyncProgramImpact.declaration.name
      var waiting = Option.empty[V.RepositoryDirectoryBatchResult]
      var prepared = Option.empty[P.Progress]
      val input = ThreeWorkflowNormalHostFixture.definition(root).repository.input

      def _refresh_properties_(result: V.RepositoryDirectoryBatchResult): List[Property] =
        List(Property("sessionId", result.sessionId, None), Property("revision", result.resultReference.revision, None))

      for ((readroot, blocked) <- Vector(root.resolve(".textus") -> Some(build), build -> Some(declaration),
          repository -> None)) {
        _with_runtime_(root, "DeclarationRead", hostParameters = parameters + (resourcekey -> readroot.toString)) {
          (owner, component, actor, prepare) =>
          def _invoke_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
            read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "RepositorySyncService", operation = operation, properties = properties), actor)))

          val result = waiting match {
            case None =>
              prepare(_.setupC(_setup_commands_(root, "DeclarationRead").size))
              Files.writeString(repository.resolve("value.txt"), "pending incorrect\n")
              val intent = Property("input", Record.data("syncMode" -> "FULL_STATE"), None)
              val issued = _invoke_[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.syncOperation, List(intent))
              val child = InstanceIdentity(issued.repositories.head.sessionId)
              var work = Option.empty[WorkflowInteraction[P.Request, Nothing]]
              prepare(_.repositoryWorkC(child, RepositorySyncPurposeContinuation.completionOperation, P.inputCodec)
                .map(value => { work = Some(value); () }))
              val request = work.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[P.Request]].request
              val proposal = P.Result("resolved", "One actual program candidate", Vector(P.Group("one-purpose", "Program value",
                "Correct program input", Vector.empty, Vector(P.Content("value.txt", Some("expected\n"))), Vector.empty,
                "Correct the one actual changed runtime input")), Vector.empty)
              val reference = ContextReference("declaration-read-purpose", "1")
              val reply = ContinuationResult(work.get.handle, request.runId, request.continuationId, request.expectedRevision,
                request.context.snapshot, TypedValue(request.resultType.get.value, proposal), reference, Vector.empty,
                ExecutionEvidence(Vector(reference), Some("declaration-read-specification")))
              _invoke_[V.RepositorySyncConflictSubmissionResult]("submitRepositoryPurposeResult",
                take(NativeWorkflowOperationInputFixture.submissionC(reply))).accepted shouldBe true
              When("the same directory Session advances to candidate preflight")
              _invoke_[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.syncOperation,
                List(Property("sessionId", issued.sessionId, None), intent))
            case Some(previous) =>
              When("setup refresh reopens the same Session with the current read permissions")
              _invoke_[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.refreshOperation, _refresh_properties_(previous))
          }
          val child = InstanceIdentity(result.repositories.head.sessionId)
          waiting.foreach { previous =>
            result.sessionId shouldBe previous.sessionId
            child.value shouldBe previous.repositories.head.sessionId
          }
          blocked match {
            case Some(path) =>
              Then("the wait reports the attempted file and its resource refusal before any workspace allocation")
              result.repositories.head.kind shouldBe "Wait"
              result.repositories.head.condition shouldBe Some("purpose-materialization-preflight")
              result.repositories.head.diagnostic.get should include(path.toString)
              result.repositories.head.diagnostic.get should include("outside configured read-only roots")
              prepare(call => for {
                saved <- call.nativeStageC(input, child, root, "purpose-1-prepared")
                allocation <- call.nativeStageC(input, child, root, "purpose-1-allocate-intent")
              } yield {
                val current = saved.get.payload.purposes.get
                prepared.foreach(prior => current shouldBe prior)
                prepared = Some(current)
                allocation shouldBe None
                ()
              })
              waiting = Some(result)
            case None =>
              Then("the same prepared candidate is checked, assessed and installed under ordinary FULL selection")
              prepare(call => for {
                saved <- call.nativeStageC(input, child, root, "purpose-1-check-1-result")
                assessment <- call.nativeStageC(input, child, root, "purpose-1-assessment")
                installed <- call.nativeStageC(input, child, root, "purpose-1-installed")
              } yield {
                saved should not be empty
                val checked = saved.get.payload.purposes.get
                checked.plan shouldBe prepared.get.plan
                checked.candidate shouldBe prepared.get.candidate
                checked.sessionWorkspace.get.session shouldBe child.value
                checked.selection.get.depth shouldBe "FULL"
                checked.receipts.drop(1).map(_.identity) shouldBe Vector("compile", "test")
                C.productSuccessful(checked.receipts(1), WorkflowSbtValidation.Kind.Compile) shouldBe true
                C.productSuccessful(checked.receipts(2), WorkflowSbtValidation.Kind.Test) shouldBe true
                assessment.flatMap(_.payload.purposes).flatMap(_.assessment).map(_.branch) shouldBe Some("admit")
                installed.flatMap(_.payload.purposes).map(_.commits) shouldBe
                  Some(Vector(prepared.get.candidate.get.commit))
                ()
              })
          }
        }
      }
    }

    "reconstruct the existing Purpose validation selection when optional fixCheck was undefined" in {
      Given("an ordinary candidate whose old managed selection includes a command the build does not define")
      val root = _root("validation-reconstruction")
      val scenario = "ValidationReconstruction"
      val repository = root.resolve("repository")
      val parameter = RepositorySyncProgramImpact.declaration.name
      val parameters = (_parameters(root) - RepositorySyncDocumentInputs.declaration.name - parameter) +
        ("textus.resource.url.file.roots" -> repository.toString)
      val checks = Vector(C.Check("compile", 1, WorkflowSbtValidation.Kind.Compile),
        C.Check("fix-check", 2, WorkflowSbtValidation.Kind.FixCheck), C.Check("test", 3))
      val oldpolicy = RepositorySyncProgramImpact.Policy(checks.map(check => RepositorySyncProgramImpact.Coverage(check,
        Vector("src"), _inputs, "Original project inputs")), checks, Vector("build.sbt", "project"), "Original FULL selection", Vector.empty)
      val intent = Property("input", Record.data("syncMode" -> "FULL_STATE"), None)
      val input = ThreeWorkflowNormalHostFixture.definition(root).repository.input
      var previous = Option.empty[V.RepositoryDirectoryBatchResult]
      var original = Option.empty[P.Progress]
      _with_runtime_(root, scenario, hostParameters = parameters + (parameter -> oldpolicy.asJson.noSpaces)) {
        (owner, component, actor, prepare) =>
        def _invoke_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "RepositorySyncService", operation = operation, properties = properties), actor)))
        prepare(_.setupC(_setup_commands_(root, scenario).size, scenario = scenario))
        Files.writeString(repository.resolve("value.txt"), "pending incorrect\n")
        val issued = _invoke_[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.syncOperation, List(intent))
        val child = InstanceIdentity(issued.repositories.head.sessionId)
        var work = Option.empty[WorkflowInteraction[P.Request, Nothing]]
        prepare(_.repositoryWorkC(child, RepositorySyncPurposeContinuation.completionOperation, P.inputCodec)
          .map(value => { work = Some(value); () }))
        val request = work.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[P.Request]].request
        val proposal = P.Result("resolved", "One actual program candidate", Vector(P.Group("one-purpose", "Program value",
          "Correct program input", Vector.empty, Vector(P.Content("value.txt", Some("expected\n"))), Vector.empty,
          "Correct the one actual changed runtime input")), Vector.empty)
        val reference = ContextReference("reconstruction-purpose", "1")
        val reply = ContinuationResult(work.get.handle, request.runId, request.continuationId, request.expectedRevision,
          request.context.snapshot, TypedValue(request.resultType.get.value, proposal), reference, Vector.empty,
          ExecutionEvidence(Vector(reference), Some("reconstruction-purpose-specification")))
        _invoke_[V.RepositorySyncConflictSubmissionResult]("submitRepositoryPurposeResult",
          take(NativeWorkflowOperationInputFixture.submissionC(reply))).accepted shouldBe true
        previous = Some(_invoke_[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.syncOperation,
          List(Property("sessionId", issued.sessionId, None), intent)))
        prepare(_.nativeStageC(input, child, root, "purpose-1-check-1-result").map { failed =>
          original = failed.flatMap(_.payload.purposes)
          original.get.receipts.last.stdout should include("Not a valid command: fixCheck")
          C.productSuccessful(original.get.receipts(1), WorkflowSbtValidation.Kind.Compile) shouldBe true
          ()
        })
      }
      When("the same child Session describes and adopts validation reconstruction under ordinary project defaults")
      val batch = previous.get
      val child = InstanceIdentity(batch.repositories.head.sessionId)
      _with_runtime_(root, scenario, hostParameters = parameters) { (owner, component, actor, prepare) =>
        def _invoke_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "RepositorySyncService", operation = operation, properties = properties), actor)))
        val properties = List(Property("sessionId", child.value, None))
        val waiting = _invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync", properties)
        waiting.condition shouldBe Some("ValidationReconstructionRequired")
        val description = _invoke_[V.RepositorySyncValidationContinuationDescriptionResult](
          "describeRepositorySyncValidationContinuation", properties)
        val target = take(RepositorySyncValidationContinuation.anyTargetC(description.target)) match {
          case RepositorySyncValidationContinuation.FormalFailureTarget(value) => value
          case _ => fail("Expected the actual formal failure reconstruction target")
        }
        target.continuationPolicy shouldBe R.reconstructionPolicy
        target.selectedChecks shouldBe checks
        target.proposedChecks shouldBe Some(Vector(checks.head, checks.last.copy(ordinal = 2)))
        target.purpose shouldBe original
        R.validTarget(target.copy(proposedChecks = Some(Vector(checks.last)))) shouldBe false
        R.validTarget(target.copy(purposeSlot = target.purposeSlot.map(slot => slot.copy(
          actionPolicy = slot.actionPolicy.copy(scalaLint = "required"))))) shouldBe false
        val answer = V.RepositorySyncValidationContinuationAnswer(R.reconstructionChoices.head,
          "Reconstruct the ordinary optional validation slot from the current project policy", target.pending.nonEmpty, target.pending.nonEmpty)
        val reference = V.WorkflowArtifactReference("reconstruction-direction", "1")
        val evidence = V.WorkflowExecutionEvidence(Vector(reference), Some(target.context.participantIdentity), None, None)
        prepare(call => NativeRepositoryValidationContinuationPreparation.prepareC(description, answer, reference, evidence,
          target.context.participantIdentity, "normal-development-host", call.core.executionContext.clock.instant())
          .flatMap(record => call.createC("host_evidence", record.recordIdentity, record.record)))
        val adoption = V.RepositorySyncValidationContinuationAdoptionInput(child.value, description.target,
          answer.choice, answer.rationale, reference, answer.priorExecutionEnded, answer.acceptOriginalUncertainty, evidence)
        _invoke_[V.RepositorySyncValidationContinuationAdoptionResult]("adoptRepositorySyncValidationContinuation",
          List(Property("input", adoption.toRecord(), None))).outcome shouldBe "adopted"
        val resumed = _invoke_[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.syncOperation,
          List(Property("sessionId", batch.sessionId, None), intent))
        Then("the same aggregate and candidate retain the old result and run only the unfinished Test")
        resumed.sessionId shouldBe batch.sessionId
        resumed.repositories.head.sessionId shouldBe child.value
        val prefix = "purpose-1-host-" + target.frontierRevision
        prepare(call => for {
          failed <- call.nativeStageC(input, child, root, "purpose-1-check-1-result")
          replay <- call.nativeStageC(input, child, root, prefix + "-check-0-intent")
          checked <- call.nativeStageC(input, child, root, prefix + "-check-1-result")
          installed <- call.nativeStageC(input, child, root, prefix + "-installed")
        } yield {
          failed.flatMap(_.payload.purposes) shouldBe original
          replay shouldBe None
          val progress = checked.flatMap(_.payload.purposes).get
          progress.candidate shouldBe original.get.candidate
          progress.receipts.take(2) shouldBe target.successfulPrefix
          C.productSuccessful(progress.receipts.last, WorkflowSbtValidation.Kind.Test) shouldBe true
          installed.flatMap(_.payload.purposes).map(_.commits) shouldBe Some(Vector(original.get.candidate.get.commit))
          ()
        })
      }
    }

    for (requirement <- Vector("check", "review")) {
      s"refuse installation when a checked frontier is reopened with a new required $requirement" in {
        Given("a real Session candidate checked successfully but paused before installation admission")
        val root = _root("checked-policy-" + requirement)
        var child = Option.empty[InstanceIdentity]
        var checked = Option.empty[N.Stage]
        var sourcehead = ""
        var sourceindex = ""
        _with_runtime_(root, "MissingPolicy", hostParameters = _parameters(root)) { (owner, component, actor, prepare) =>
          prepare(_.setupC(_setup_commands_(root, "Equal").size))
          Files.writeString(root.resolve("repository/value.txt"), "pending incorrect\n")
          def _invoke_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
            read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "RepositorySyncService", operation = operation, properties = properties), actor)))
          val issued = _invoke_[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.syncOperation,
            List(Property("input", Record.data("syncMode" -> "FULL_STATE"), None)))
          child = Some(InstanceIdentity(issued.repositories.head.sessionId))
          var work = Option.empty[WorkflowInteraction[P.Request, Nothing]]
          prepare(_.repositoryWorkC(child.get, RepositorySyncPurposeContinuation.completionOperation, P.inputCodec)
            .map(value => { work = Some(value); () }))
          val request = work.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[P.Request]].request
          val proposal = P.Result("resolved", "Checked candidate policy regression", Vector(P.Group("one-purpose", "One purpose",
            "Apply checked candidate", Vector.empty, Vector(P.Content("value.txt", Some("expected\n"))), Vector.empty,
            "One actual changed program input")), Vector.empty)
          val reference = ContextReference("checked-candidate-purpose", "1")
          val reply = ContinuationResult(work.get.handle, request.runId, request.continuationId, request.expectedRevision,
            request.context.snapshot, TypedValue(request.resultType.get.value, proposal), reference, Vector.empty,
            ExecutionEvidence(Vector(reference), Some("checked-candidate-specification")))
          _invoke_[V.RepositorySyncConflictSubmissionResult]("submitRepositoryPurposeResult",
            take(NativeWorkflowOperationInputFixture.submissionC(reply))).accepted shouldBe true
          val waiting = _invoke_[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.syncOperation,
            List(Property("sessionId", issued.sessionId, None), Property("input", Record.data("syncMode" -> "FULL_STATE"), None)))
          waiting.outcome shouldBe "AttentionRequired"
          prepare(_.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, child.get, root,
            "purpose-1-prepared").map { saved =>
            saved.get.payload.stage shouldBe "PurposePrepared"
            ()
          })
        }
        _with_runtime_(root, "Equal", hostParameters = _parameters(root)) { (_, _, _, prepare) =>
          prepare(call => _store_c(call, root, child.get).flatMap { store => for {
            current <- call.currentC(child.get)
            observed <- call.observeC()
            head <- WorkflowManagedProcessExecution.executeGitC(call.core, GitOperation.Head).flatMap(_.valueC)
            index <- WorkflowManagedProcessExecution.executeGitC(call.core, GitOperation.IndexTree).flatMap(_.valueC)
            admissions = new RepositorySyncNativeConvergence.AuthorityPort {
              def admitC(core: org.goldenport.cncf.action.ActionCall.Core, context: F.StartContext,
                target: Target, effect: RepositorySyncNativeConvergence.Effect): Consequence[Unit] =
                Consequence.stateConflict("Fixture pauses checked candidate before installation")
            }
            result = RepositorySyncPurposeRuntime.advanceC(store, current, observed, store.declaration.purposeValidation,
              Some(admissions), None, (_, _) => Consequence.stateConflict("Fixture must not finish"))
            _ = result match {
              case Consequence.Failure(conclusion) => conclusion.show should include("Fixture pauses checked candidate")
              case _ => fail("Expected real checks to reach the installation admission boundary")
            }
            saved <- store.loadC("purpose-1-assessment")
          } yield {
            checked = saved
            checked.get.payload.stage shouldBe "PurposeChecked"
            checked.get.payload.purposes.get.assessment.get.branch shouldBe "admit"
            checked.get.payload.purposes.get.receipts should have size 3
            sourcehead = head.value
            sourceindex = index.value
            ()
          } })
        }
        When("a fresh runtime advances the same checked frontier under a stricter ordinary policy")
        _with_runtime_(root, "Equal", hostParameters = _parameters(root)) { (_, _, _, prepare) =>
          prepare(call => _store_c(call, root, child.get).flatMap { store =>
            val ordinary = store.declaration.purposeValidation.get
            val additional = C.Check("new-required-compile", 3, WorkflowSbtValidation.Kind.Compile)
            val coverage = _coverage.copy(coverage = _coverage.coverage :+
              RepositorySyncProgramImpact.Coverage(additional, Vector("value.txt"), _inputs,
                "Current ordinary policy adds a required compile check"), fullChecks = _coverage.fullChecks :+ additional)
            val policy = if (requirement == "review") ordinary.copy(
              assessment = ordinary.assessment.map(_.copy(detailedReview = "required")))
              else StandardWorkflowRuntime.normalRepositoryValidation(Some(coverage))
            val selected = store.withDeclaration(store.declaration.copy(purposeValidation = Some(policy)))
            var admissions = 0
            val tripwire = new RepositorySyncNativeConvergence.AuthorityPort {
              def admitC(core: org.goldenport.cncf.action.ActionCall.Core, context: F.StartContext,
                target: Target, effect: RepositorySyncNativeConvergence.Effect): Consequence[Unit] = {
                admissions += 1
                Consequence.stateConflict("Installation admission must not be reached with stale policy")
              }
            }
            for {
              before <- call.currentC(child.get)
              _ = _encoded_record(before) shouldBe _encoded_record(checked.get.record)
              observed <- call.observeC()
              result = RepositorySyncPurposeRuntime.advanceC(selected, before, observed, Some(policy), Some(tripwire), None,
                (_, _) => Consequence.stateConflict("Stale policy must not finish"))
              _ = Then("the stricter current policy refuses the checked candidate before any installation admission")
              _ = result match {
                case Consequence.Failure(conclusion) => conclusion.show should include("differs from current ordinary validation or host policy")
                case _ => fail("A checked candidate cannot inherit an obsolete validation policy")
              }
              after <- call.currentC(child.get)
              retained <- store.loadC("purpose-1-assessment")
              intent <- store.loadC("purpose-1-commit-intent")
              installed <- store.loadC("purpose-1-installed")
              head <- WorkflowManagedProcessExecution.executeGitC(call.core, GitOperation.Head).flatMap(_.valueC)
              index <- WorkflowManagedProcessExecution.executeGitC(call.core, GitOperation.IndexTree).flatMap(_.valueC)
            } yield {
              Then("installation is refused before admission or intent and the same evidence, history and source remain")
              admissions shouldBe 0
              _encoded_record(after) shouldBe _encoded_record(before)
              retained.get.payload shouldBe checked.get.payload
              _encoded_record(retained.get.record) shouldBe _encoded_record(checked.get.record)
              intent shouldBe None
              installed shouldBe None
              head.value shouldBe sourcehead
              index.value shouldBe sourceindex
              Files.readString(root.resolve("repository/value.txt")) shouldBe "pending incorrect\n"
              ()
            }
          })
        }
      }
    }

    for ((document, initialized) <- Vector((true, false), (false, false), (false, true))) {
      s"materialize and reopen the actual candidate with ordinary validation, documentation=$document, initialized=$initialized" in {
        Given("ordinary candidate coverage and preserved source work, with existing V7 roots or absent V8 roots")
        val root = _root(if (document) "document" else if (initialized) "initialized-product" else "product")
        val scenario = if (initialized) "Initialized" else "Equal"
        val path = if (document) "docs/purpose.md" else "value.txt"
        val content = if (document) "one accepted document purpose\n" else "expected\n"
        var retained = Option.empty[N.Stage]
        var retainedresult = Option.empty[V.RepositoryDirectoryBatchResult]
        var candidateroot = ""
        val sourcecontent = if (document) content else "pending incorrect\n"
        _parameters(root).contains(P.declaration.name) shouldBe false
        if (initialized) {
          Files.notExists(root.resolve("managed-candidates"), LinkOption.NOFOLLOW_LINKS) shouldBe true
          Files.notExists(root.resolve("candidate-alias"), LinkOption.NOFOLLOW_LINKS) shouldBe true
        }
        When("the public fixture runtime initializes the selected ordinary candidate plan")
        _with_runtime_(root, scenario, hostParameters = _parameters(root)) { (owner, component, actor, prepare) =>
          Then("the declared candidate parent and dormant alias are available")
          Files.isDirectory(root.resolve("managed-candidates")) shouldBe true
          Files.isDirectory(root.resolve("candidate-alias")) shouldBe true
          Given("the ordinary repository and its actual pending purpose input")
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
            "purpose-1-workspace-observed").map(value => { retained = value; () }))
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
          val binding = progress.sessionWorkspace.get
          binding.session shouldBe child.value
          binding.source shouldBe "sm-workflow-repository"
          binding.alias shouldBe _alias
          binding.materialization should not be empty
          progress.asJson.as[P.Progress].toOption.get shouldBe progress
          N.codec.decode(N.codec.encode(retained.get.payload)).toOption.get shouldBe retained.get.payload
          progress.managedWorkspace shouldBe None
          prepare(call => _store_c(call, root, child).flatMap(store => RepositorySyncPurposeRuntime.candidateCoreC(store, progress)).flatMap { selected => for {
            actual <- WorkflowManagedProcessExecution.executeGitC(selected, GitOperation.Root, _alias).flatMap(_.valueC)
            head <- WorkflowManagedProcessExecution.executeGitC(selected, GitOperation.Head, _alias).flatMap(_.valueC)
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
        _with_runtime_(root, scenario, hostParameters = _parameters(root)) { (owner, component, actor, prepare) =>
          val stage = retained.get
          val progress = stage.payload.purposes.get
          val child = InstanceIdentity(retainedresult.get.repositories.head.sessionId)
          var actualroot = ""
          var refusals = Vector.empty[Boolean]
          var policyrefusals = Vector.empty[Boolean]
          prepare(call => _store_c(call, root, child).flatMap { store => for {
            selected <- RepositorySyncPurposeRuntime.candidateCoreC(store, progress.asJson.as[P.Progress].toOption.get)
            actual <- WorkflowManagedProcessExecution.executeGitC(selected, GitOperation.Root, _alias).flatMap(_.valueC)
            binding = progress.sessionWorkspace.get
            reference <- SessionCandidateWorkspaceReference.fromRecordC(RepositorySyncSessionWorkspace.record(binding))
          } yield {
            actualroot = actual
            val currentpolicy = StandardWorkflowRuntime.normalRepositoryValidation(Some(_coverage))
            val requiredreview = currentpolicy.copy(assessment = currentpolicy.assessment.map(_.copy(detailedReview = "required")))
            val reviewstore = store.withDeclaration(store.declaration.copy(purposeValidation = Some(requiredreview)))
            policyrefusals = Vector(RepositorySyncSessionWorkspace.requireCurrentBindingC(reviewstore, progress).isFaillure)
            if (!document) {
              val additional = C.Check("additional-required-compile", 3, WorkflowSbtValidation.Kind.Compile)
              val coverage = _coverage.copy(coverage = _coverage.coverage :+
                RepositorySyncProgramImpact.Coverage(additional, Vector("value.txt"), _inputs,
                  "Current ordinary policy additionally requires this independent compile check"),
                fullChecks = _coverage.fullChecks :+ additional)
              val changedpolicy = StandardWorkflowRuntime.normalRepositoryValidation(Some(coverage))
              val changedstore = store.withDeclaration(store.declaration.copy(purposeValidation = Some(changedpolicy)))
              policyrefusals = policyrefusals :+ RepositorySyncSessionWorkspace.requireCurrentBindingC(changedstore, progress).isFaillure
            }
            refusals = Vector(
              RepositorySyncPurposeRuntime.candidateCoreC(store, progress.copy(sessionWorkspace = Some(binding.copy(session = "foreign:session")))).isFaillure,
              RepositorySyncPurposeRuntime.candidateCoreC(store, progress.copy(sessionWorkspace = Some(binding.copy(candidate = "foreign-candidate")))).isFaillure,
              RepositorySyncPurposeRuntime.candidateCoreC(store, progress.copy(sessionWorkspace = Some(binding.copy(materialization = None)))).isFaillure,
              SessionCandidateWorkspace.reacquireC(store.core.executionContext, reference, reference.record, SessionCandidateWorkspaceState.Unknown).isFaillure,
              SessionCandidateWorkspace.reacquireC(store.core.executionContext, reference, reference.record, SessionCandidateWorkspaceState.Empty).isFaillure)
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
          progress.asJson.hcursor.downField("managedWorkspace").focus shouldBe None
        }
      }
    }

    for (occupied <- Vector(false, true)) {
      s"preserve the original setup candidate and retain a real exclusive-allocation refusal without replay, occupied=$occupied" in {
        val root = _root(if (occupied) "occupied" else "setup")
        var waiting = Option.empty[V.RepositoryDirectoryBatchResult]
        var prepared = Option.empty[N.Stage]
        var legacyprogress = Option.empty[P.Progress]
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
          result.repositories.head.diagnostic.get should include("Session candidate workspace policy")
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
          Given("the candidate host is available but an ordinary validation prerequisite is missing")
          prepare(call => _store_c(call, root, child).flatMap { store =>
            val ordinary = store.declaration.purposeValidation.get
            val unavailable = _product.copy(setupRequired = Some("actual ordinary product binding is missing"))
            val coverage = _coverage.copy(
              coverage = _coverage.coverage.map(row => if (row.check.identity == _product.identity)
                row.copy(check = unavailable) else row),
              fullChecks = _coverage.fullChecks.map(check => if (check.identity == _product.identity) unavailable else check))
            val policies = Vector(ordinary.copy(impact = None),
              ordinary.copy(setupRequired = Some("ordinary validation resource is unavailable")),
              StandardWorkflowRuntime.normalRepositoryValidation(Some(coverage)))
            policies.foldLeft(Consequence.unit) { (prior, policy) => prior.flatMap { _ => for {
              before <- call.currentC(child)
              observed <- call.observeC()
              selected = store.withDeclaration(store.declaration.copy(purposeValidation = Some(policy)))
              waiting <- RepositorySyncPurposeRuntime.advanceC(selected, before, observed, Some(policy), None, None,
                (_, _) => Consequence.stateConflict("Missing coverage cannot finish synchronization"))
              after <- call.currentC(child)
              retained <- store.loadC("purpose-1-prepared")
              allocation <- store.loadC("purpose-1-allocate-intent")
              materialization <- store.loadC("purpose-1-materialize-intent")
            } yield {
              Then("setup waits before allocation and retains the original candidate, checks, history and counters")
              waiting.condition shouldBe Some("purpose-materialization-preflight")
              _encoded_record(after) shouldBe _encoded_record(before)
              retained.get.payload shouldBe originalstage.payload
              retained.get.payload.purposes.get.plan shouldBe progress.plan
              retained.get.payload.purposes.get.receipts shouldBe progress.receipts
              retained.get.payload.purposes.get.attempt shouldBe progress.attempt
              retained.get.payload.purposes.get.slotCursor shouldBe progress.slotCursor
              allocation shouldBe None
              materialization shouldBe None
              Files.readString(root.resolve("repository/value.txt")) shouldBe "pending incorrect\n"
              ()
            } } }
          })
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
          if (!occupied) {
            Given("a genuine pre-install candidate with a separate historical managedWorkspace identity")
            prepare(call => _store_c(call, root, child).flatMap(store => _legacy_progress_c(store, progress)).map { legacy =>
              legacyprogress = Some(legacy)
              legacy.receipts shouldBe progress.receipts
              legacy.effects shouldBe progress.effects
              ()
            })
          }
          if (occupied) prepare(call => _store_c(call, root, child).flatMap { store => for {
            binding <- RepositorySyncSessionWorkspace.resolveC(store, progress, store.declaration.purposeValidation)
            source <- org.goldenport.cncf.workspace.WorkspaceId.parseC(binding.source)
            parent <- org.goldenport.cncf.workspace.WorkspaceId.parseC(binding.parent)
            alias <- org.goldenport.cncf.workspace.WorkspaceId.parseC(binding.alias)
            _ <- WorkflowManagedProcessExecution.prepareSessionCandidateC(store.core, source, parent, alias, binding.session, binding.candidate)
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
            result.outcome shouldBe "Indeterminate"
            result.repositories.head.kind shouldBe "Indeterminate"
            result.repositories.head.condition.get should include("Native continuation returned no observed business result")
            result.repositories.head.condition.get should include("retained intent is not replayed")
            var failed = Option.empty[N.Stage]
            prepare(_.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, child, root,
              "purpose-1-allocate-intent").map { saved =>
              saved.get.payload.stage shouldBe "PurposeAllocating"
              val pending = saved.get.payload.purposes.get
              pending.candidate shouldBe progress.candidate
              pending.sessionWorkspace shouldBe None
              pending.effects shouldBe progress.effects
              ()
            })
            prepare(_.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, child, root,
              "purpose-1-allocate-result").map { saved =>
              failed = saved
              saved.get.payload.stage shouldBe "PurposeUnfinished"
              val unfinished = saved.get.payload.purposes.get
              unfinished.candidate shouldBe progress.candidate
              unfinished.sessionWorkspace shouldBe None
              val rejection = unfinished.effects.last
              unfinished.effects.dropRight(1) shouldBe progress.effects
              rejection.hcursor.get[String]("allocationFailure").toOption.get.toLowerCase should include("occupied")
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
              "purpose-1-allocate-result").map { saved => saved.get.payload shouldBe failed.get.payload; () })
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
        if (!occupied) {
          When("a fresh runtime reads the genuine historical binding after normal installation")
          _with_runtime_(root, "Equal", hostParameters = _parameters(root)) { (_, _, _, prepare) =>
            val legacy = legacyprogress.get.asJson.as[P.Progress].toOption.get
            val child = InstanceIdentity(waiting.get.repositories.head.sessionId)
            prepare(call => _store_c(call, root, child).flatMap { store => for {
              selected <- RepositorySyncPurposeRuntime.candidateCoreC(store, legacy)
              actual <- WorkflowManagedProcessExecution.executeGitC(selected, GitOperation.Root, _alias).flatMap(_.valueC)
              binding = legacy.managedWorkspace.get
              reference <- SessionCandidateWorkspaceReference.fromRecordC(binding.reference)
            } yield {
              Then("the old owner reopens actual content and refuses foreign, incomplete, unknown and changed-policy bindings")
              Files.readString(Path.of(actual).resolve("value.txt")) shouldBe "expected\n"
              val currentpolicy = StandardWorkflowRuntime.normalRepositoryValidation(Some(_coverage))
              val requiredreview = currentpolicy.copy(assessment = currentpolicy.assessment.map(_.copy(detailedReview = "required")))
              val reviewstore = store.withDeclaration(store.declaration.copy(purposeValidation = Some(requiredreview)))
              W.requireCurrentBindingC(reviewstore, legacy).isFaillure shouldBe true
              val additional = C.Check("additional-required-compile", 3, WorkflowSbtValidation.Kind.Compile)
              val coverage = _coverage.copy(coverage = _coverage.coverage :+
                RepositorySyncProgramImpact.Coverage(additional, Vector("value.txt"), _inputs,
                  "Current ordinary policy adds an independent compile check"), fullChecks = _coverage.fullChecks :+ additional)
              W.requireCurrentBindingC(store.withDeclaration(store.declaration.copy(purposeValidation =
                Some(StandardWorkflowRuntime.normalRepositoryValidation(Some(coverage))))), legacy).isFaillure shouldBe true
              Vector(
                W.scopedStoreC(store, legacy.copy(managedWorkspace = Some(binding.copy(reference = binding.reference.copy(session = "foreign:session"))))).isFaillure,
                W.scopedStoreC(store, legacy.copy(managedWorkspace = Some(binding.copy(reference = binding.reference.copy(candidate = "foreign-candidate"))))).isFaillure,
                W.scopedStoreC(store, legacy.copy(managedWorkspace = Some(binding.copy(materialization = None)))).isFaillure,
                SessionCandidateWorkspace.reacquireC(store.core.executionContext, reference, binding.reference, SessionCandidateWorkspaceState.Unknown).isFaillure,
                SessionCandidateWorkspace.reacquireC(store.core.executionContext, reference, binding.reference, SessionCandidateWorkspaceState.Empty).isFaillure
              ) shouldBe Vector.fill(5)(true)
              legacy.asJson.mapObject(_.add("managedWorkspace", Json.Null)).as[P.Progress].isLeft shouldBe true
              ()
            } })
          }
        }
      }
    }
  }
}
