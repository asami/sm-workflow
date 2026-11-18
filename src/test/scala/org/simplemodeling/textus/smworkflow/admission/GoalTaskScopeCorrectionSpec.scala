package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as _, *}
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.GivenWhenThen
import org.simplemodeling.textus.smworkflow.preparation.{NativeGoalTaskStartInput, NativeGoalTaskScopeCorrectionInput, NativeReviewPreparation}
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import SplitPhaseExplicitPartitionFixture.take
import StandaloneFixExecution as S
import S.given
import BoundedWorkRuntime.given

/** SPECIFICATION-OWNED selection and workspace; no actual Human event is asserted.
  * @since   Nov. 16, 2026
  * @version Nov. 18, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalTaskScopeCorrectionSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  private val added = Vector("project/SbtBridge.scala", "docs/contract.md")
  private val input = V.GoalTaskInput("sm-workflow", "scope-specification", "Preserve the same purpose",
    NonEmptyVector.one("src/test/scala"), NonEmptyVector.of("compile", "focused"), NonEmptyVector.of("behavior", "delivery"),
    NonEmptyVector.one(V.WorkflowArtifactReference("specification-source", "1")), false, "code", "off", 10,
    "direct-instruction", Vector.empty)
  private val checks = Vector(GoalPhaseNativeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile),
    GoalPhaseNativeValidation.Check("focused", 2, WorkflowSbtValidation.Kind.TestOnly))
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), kinds = TypedGitHostPlanFixture.observations :+ org.goldenport.cncf.processexecution.GitOperationKind.IndexTree)
  private def state(view: V.GoalTaskView): S.State = GoalPhaseNativeMetadata.normalizePublicJson(
    parser.parse(take(view.state.get.toJsonStringC)).toOption.get).toOption.get.as[S.State].toOption.get
  private def directory(): Path = Files.createTempDirectory(Files.createDirectories(
    Path.of("target/sm-workflow/work/goal-task-scope").toAbsolutePath), "episode-").toRealPath()

  "GoalTask pending inventory correction" should {
    "issue ordinary implementation from current main without requiring a scope correction" in {
      Given("a started specification-owned Task whose implementation has not yet been issued")
      val root = directory()
      val original = ThreeWorkflowNormalHostFixture.definition(root)
      val host = original.copy(goal = original.goal.copy(candidatePaths = input.ownedPaths.toVector,
        stepValidation = original.goal.stepValidation.copy(checks = checks)))
      _with_runtime_(root, "Equal", Some(host), BoundedWorkExecutionAdmissionFixture.parameters) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "Equal").size))
        def _value_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "GoalTaskService", operation = operation, properties = properties), actor)))
        val options = Map("input" -> "input.json", "selection-id" -> "ordinary-start", "participant" -> "participant",
          "origin" -> "ExplicitHuman", "idempotency-key" -> "ordinary-start", "selection-collection" -> "entity:smworkflow.cli.goal_selection")
        val prepared = take(NativeGoalTaskStartInput.prepareC(options, _ => input.toRecord().toJsonStringC))
        prepare { setup =>
          val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        }
        val started = _value_[V.GoalTaskView]("startGoalTask", List(Property("input", input, None),
          Property("selectionId", "ordinary-start", None), Property("participant", "participant", None),
          Property("idempotencyKey", "ordinary-start", None)))
        When("main advances and unrelated documentation appears before ordinary Advance")
        val command = Vector("/usr/bin/git", "-C", root.resolve("repository").toString,
          "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "-c", "core.hooksPath=/dev/null",
          "commit", "--allow-empty", "--no-verify", "--no-gpg-sign", "-m", "fixture independent main change")
        scala.sys.process.Process(command).! shouldBe 0
        val unrelated = root.resolve("repository/docs/unrelated.md")
        Files.createDirectories(unrelated.getParent)
        Files.writeString(unrelated, "unrelated documentation\n")
        val issued = _value_[V.GoalTaskView]("advanceGoalTask", List(Property("sessionId", started.sessionId, None)))
        Then("normal work issuance preserves the same purpose and counters without a recovery operation")
        issued.sessionId shouldBe started.sessionId
        state(issued).stage shouldBe "Implement"
        state(issued).input.ownedPaths shouldBe input.ownedPaths.toVector
        state(issued).automaticCount shouldBe 0
        state(issued).ordinaryCount shouldBe 0
        Files.readString(unrelated) shouldBe "unrelated documentation\n"
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      }
    }

    "continue a scope-corrected Repair after main advances without replacing its failed evidence or repair count" in {
      Given("a specification-owned failed check and an unclaimed Repair in the original native GoalTask")
      val root = directory()
      val original = ThreeWorkflowNormalHostFixture.definition(root)
      val host = original.copy(goal = original.goal.copy(candidatePaths = input.ownedPaths.toVector ++ added,
        stepValidation = original.goal.stepValidation.copy(checks = checks)))
      _with_runtime_(root, "Equal", Some(host), BoundedWorkExecutionAdmissionFixture.parameters) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "Equal").size))
        def _call_(operation: String, properties: List[Property]): Consequence[OperationResponse] =
          owner.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalTaskService",
            operation = operation, properties = properties), actor)
        def _value_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          read_value[A](take(_call_(operation, properties)))
        def _selection_(record: Record): Unit = prepare { setup =>
          val row = record.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        }
        val options = Map("input" -> "input.json", "selection-id" -> "source-start", "participant" -> "participant",
          "origin" -> "ExplicitHuman", "idempotency-key" -> "source-start", "selection-collection" -> "entity:smworkflow.cli.goal_selection")
        _selection_(take(NativeGoalTaskStartInput.prepareC(options, _ => input.toRecord().toJsonStringC)))
        val started = _value_[V.GoalTaskView]("startGoalTask", List(Property("input", input, None),
          Property("selectionId", "source-start", None), Property("participant", "participant", None),
          Property("idempotencyKey", "source-start", None)))
        prepare(c => for {
          storage <- c.taskStorageC()
          instances <- storage.bindInstancesC()(using c.executionContext)
          current <- instances.loadC(InstanceIdentity(started.sessionId))(using c.executionContext)
          payload <- GoalTaskNativeRun.loadPayloadC(storage, started.sessionId, current.get.revision.value, c.core)
          observed <- RepositorySyncManagedGitObservation.observeC(c.core)
          tree <- RepositorySyncManagedGitObservation.indexTreeC(c.core)
          candidate = S.Candidate(observed.head, tree, Vector.empty, "")
          checking <- S.intentC(payload.state.copy(stage = "Validate", selected = checks, candidate = Some(candidate),
            semanticReference = Some(ContextReference("specification-implementation", "1"))))
          failed = RepositorySyncNativeMergeValidation.Receipt(checks.head.identity, "exited", Some(1), "Specification failure", "",
            Some(WorkflowSbtValidation.Facts(checks.head.kind, WorkflowSbtValidation.Outcome.Failed, None, None)))
          repair <- S.observedC(checking, failed)
          nativehost = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", payload.root, payload.branch, host.goal.candidatePaths, checks, None, None, None, None, None)
          _ <- GoalTaskNativeRun.saveC(nativehost, storage, current.get, payload.copy(state = repair), c.core)
        } yield ())
        val pending = _value_[V.GoalTaskView]("advanceGoalTask", List(Property("sessionId", started.sessionId, None)))
        val before = state(pending)
        before.stage shouldBe "Repair"
        val old = pending.issuedWorkContext.get.continuationId
        val description = _value_[V.GoalTaskScopeCorrectionView]("describeGoalTaskScopeCorrection", List(Property("input",
          V.GoalTaskScopeCorrectionDescriptionInput(started.sessionId, old, added), None)))
        val target = take(GoalTaskScopeCorrection.targetC(description.target))
        val correction = V.GoalTaskScopeCorrectionInput(started.sessionId, old, description.target, "source-scope", "participant",
          "source-scope", V.WorkflowArtifactReference("specification-source-scope", "1"))
        _selection_(take(NativeGoalTaskScopeCorrectionInput.prepareC(options ++ Map("selection-id" -> correction.selectionId,
          "idempotency-key" -> correction.idempotencyKey), _ => correction.toRecord().toJsonStringC)))
        _value_[V.GoalTaskScopeCorrectionView]("correctGoalTaskScope", List(Property("input", correction, None))).outcome shouldBe "corrected-awaiting-advance"
        val partial = root.resolve("repository/src/test/scala/Partial.scala")
        Files.createDirectories(partial.getParent)
        Files.writeString(partial, "// retained partial repair\n")
        When("an independent main commit precedes ordinary Advance after the adopted scope correction")
        val command = Vector("/usr/bin/git", "-C", root.resolve("repository").toString,
          "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "-c", "core.hooksPath=/dev/null",
          "commit", "--allow-empty", "--no-verify", "--no-gpg-sign", "-m", "fixture main advancement")
        scala.sys.process.Process(command).! shouldBe 0
        val unrelated = root.resolve("repository/docs/unrelated.md")
        Files.createDirectories(unrelated.getParent)
        Files.writeString(unrelated, "unrelated documentation\n")
        val successor = _value_[V.GoalTaskView]("advanceGoalTask", List(Property("sessionId", started.sessionId, None)))
        Then("the same Repair is issued against current main while original evidence and counters remain unchanged")
        successor.sessionId shouldBe started.sessionId
        successor.issuedWorkContext.get.continuationId should not be old
        val after = state(successor)
        after.input.ownedPaths shouldBe before.input.ownedPaths ++ added
        after.copy(input = before.input) shouldBe before
        prepare(c => for {
          storage <- c.taskStorageC()
          current <- RepositorySyncManagedGitObservation.observeC(c.core)
          source <- GoalTaskNativeRun.loadPayloadC(storage, started.sessionId, successor.issuedWorkContext.get.expectedRevision.toLong - 1, c.core)
          originalpayload <- GoalTaskNativeRun.loadPayloadC(storage, started.sessionId, pending.issuedWorkContext.get.expectedRevision.toLong - 1, c.core)
          _ = originalpayload shouldBe target.business
          _ = source.baseHead shouldBe current.head
          _ = source.baseHead should not be originalpayload.baseHead
          _ = source.state shouldBe after
        } yield ())
        Files.readString(partial) shouldBe "// retained partial repair\n"
        Files.readString(unrelated) shouldBe "unrelated documentation\n"
        _value_[V.GoalTaskView]("advanceGoalTask", List(Property("sessionId", started.sessionId, None))).issuedWorkContext shouldBe successor.issuedWorkContext
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      }
    }

    "retain the exact validation direction through a managed Repair scope correction and a later checkpoint" in {
      Vector(false, true).foreach { checkpointed =>
        Given("a specification-owned legacy success and explicitly admitted missing validation in the same native Session")
        val root = directory()
        val legacyinput = input.copy(checks = NonEmptyVector.one("test"))
        val legacychecks = Vector(GoalPhaseNativeValidation.Check("test", 1, WorkflowSbtValidation.Kind.Test))
        val original = ThreeWorkflowNormalHostFixture.definition(root)
        val host = original.copy(goal = original.goal.copy(candidatePaths = legacyinput.ownedPaths.toVector ++ added,
          stepValidation = original.goal.stepValidation.copy(checks = legacychecks)))
        _with_runtime_(root, "Equal", Some(host), BoundedWorkExecutionAdmissionFixture.parameters) { (owner, component, actor, prepare) =>
          def _call_(op: String, props: List[Property]): Consequence[OperationResponse] = owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "GoalTaskService", operation = op, properties = props), actor)
          def _value_[A: org.goldenport.convert.ValueReader](op: String, props: List[Property]): A = read_value[A](take(_call_(op, props)))
          def _selection_(record: Record): Unit = prepare { setup =>
            val row = record.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
            setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
          }
          val options = Map("input" -> "input.json", "selection-id" -> "validation-start", "participant" -> "participant",
            "origin" -> "ExplicitHuman", "idempotency-key" -> "validation-start", "selection-collection" -> "entity:smworkflow.cli.goal_selection")
          prepare(_.setupC(_setup_commands_(root, "Equal").size))
          _selection_(take(NativeGoalTaskStartInput.prepareC(options, _ => legacyinput.toRecord().toJsonStringC)))
          val started = _value_[V.GoalTaskView]("startGoalTask", List(Property("input", legacyinput, None),
            Property("selectionId", "validation-start", None), Property("participant", "participant", None),
            Property("idempotencyKey", "validation-start", None)))
          prepare(c => for {
            storage <- c.taskStorageC()
            instances <- storage.bindInstancesC()(using c.executionContext)
            current <- instances.loadC(InstanceIdentity(started.sessionId))(using c.executionContext)
            payload <- GoalTaskNativeRun.loadPayloadC(storage, started.sessionId, current.get.revision.value, c.core)
            observed <- RepositorySyncManagedGitObservation.observeC(c.core)
            tree <- RepositorySyncManagedGitObservation.indexTreeC(c.core)
            receipts = legacychecks.map(check => RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(0), "", "",
              Some(WorkflowSbtValidation.Facts(check.kind, WorkflowSbtValidation.Outcome.Succeeded, None, None))))
            legacy = payload.copy(state = payload.state.copy(stage = "Assess", selected = legacychecks, receipts = receipts,
              candidate = Some(S.Candidate(observed.head, tree, Vector.empty, "")),
              semanticReference = Some(ContextReference("specification-result", "1"))))
            nativehost = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
              "sm-workflow", payload.root, payload.branch, host.goal.candidatePaths, legacychecks, None, None, None, None, None)
            _ <- GoalTaskNativeRun.saveC(nativehost, storage, current.get, legacy, c.core)
          } yield ())
          val description = _value_[V.GoalTaskValidationContinuationDescriptionResult]("describeGoalTaskValidationContinuation",
            List(Property("sessionId", started.sessionId, None)))
          val target = take(GoalTaskValidationContinuation.targetC(description.target))
          val reference = V.WorkflowArtifactReference("specification-validation-direction", "1")
          val answer = V.GoalTaskValidationContinuationInput(started.sessionId, description.target, "AuthorizeMissingChecks",
            "Specification-owned continuation", "participant", reference)
          prepare(c => for {
            prepared <- NativeReviewPreparation.taskValidationContinuationC(description, answer, reference,
              V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None), "participant", "normal-development-host",
              c.executionContext.clock.instant().minusSeconds(1))
            _ <- c.createC("host_evidence", prepared.recordIdentity, prepared.record)
          } yield ())
          _value_[V.GoalTaskValidationContinuationDescriptionResult]("adoptGoalTaskValidationContinuation",
            List(Property("input", answer, None))).outcome shouldBe "resumed-awaiting-advance"
          // Specification-owned failed facts, through the existing model and
          // native checkpoint owner; no child SBT or real Human event is claimed.
          prepare(c => for {
            storage <- c.taskStorageC()
            instances <- storage.bindInstancesC()(using c.executionContext)
            current <- instances.loadC(InstanceIdentity(started.sessionId))(using c.executionContext)
            payload <- GoalTaskNativeRun.loadPayloadC(storage, started.sessionId, current.get.revision.value, c.core)
            intent <- S.intentC(payload.state)
            check = intent.selected(intent.receipts.size)
            failed = RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(1), "Specification failure", "",
              Some(WorkflowSbtValidation.Facts(check.kind, WorkflowSbtValidation.Outcome.Failed, None, None)))
            repair <- S.observedC(intent, failed)
            nativehost = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
              "sm-workflow", payload.root, payload.branch, host.goal.candidatePaths, legacychecks, None, None, None, None, None)
            _ <- GoalTaskNativeRun.saveC(nativehost, storage, current.get, payload.copy(state = repair), c.core)
          } yield ())
          val pending = _value_[V.GoalTaskView]("advanceGoalTask", List(Property("sessionId", started.sessionId, None)))
          val before = state(pending)
          before.stage shouldBe "Repair"
          val old = pending.issuedWorkContext.get.continuationId
          val scope = _value_[V.GoalTaskScopeCorrectionView]("describeGoalTaskScopeCorrection", List(Property("input",
            V.GoalTaskScopeCorrectionDescriptionInput(started.sessionId, old, added), None)))
          val correction = V.GoalTaskScopeCorrectionInput(started.sessionId, old, scope.target, "validation-scope", "participant",
            "validation-scope", V.WorkflowArtifactReference("specification-scope-direction", "1"))
          _selection_(take(NativeGoalTaskScopeCorrectionInput.prepareC(options ++ Map("selection-id" -> correction.selectionId,
            "idempotency-key" -> correction.idempotencyKey), _ => correction.toRecord().toJsonStringC)))
          When("the managed owner admits additive ownership for that retained Repair")
          _value_[V.GoalTaskScopeCorrectionView]("correctGoalTaskScope", List(Property("input", correction, None))).outcome shouldBe "corrected-awaiting-advance"
          val originaldirection = take(GoalTaskScopeCorrection.targetC(scope.target)).business.validationContinuation.get
          prepare(c => for {
            storage <- c.taskStorageC()
            instances <- storage.bindInstancesC()(using c.executionContext)
            current <- instances.loadC(InstanceIdentity(started.sessionId))(using c.executionContext)
            payload <- GoalTaskScopeCorrection.retainedPayloadC(storage, current.get, c.core)
            amended <- GoalTaskScopeCorrection.retainedInputC(storage, current.get, c.core)
            next = payload.copy(state = payload.state.copy(input = amended))
            _ = GoalTaskValidationContinuation.retainedC(next, target.componentInstance, started.sessionId, current.get.revision.value).isSuccess shouldBe false
            _ <- GoalTaskValidationContinuation.retainedC(storage, current.get, c.core, next)
            foreigndefinition <- DelegateTaskRuntimeCatalog.loadC()
            _ = GoalTaskValidationContinuation.retainedC(storage, current.get.copy(definition = foreigndefinition), c.core, next).isSuccess shouldBe false
            _ = GoalTaskValidationContinuation.retainedC(storage, current.get, c.core,
              next.copy(state = next.state.copy(input = amended.copy(ownedPaths = amended.ownedPaths :+ "unproven.scala")))).isSuccess shouldBe false
            _ = GoalTaskValidationContinuation.retainedC(storage, current.get, c.core,
              next.copy(state = next.state.copy(input = amended.copy(objective = "Changed purpose")))).isSuccess shouldBe false
            unproven = current.get.copy(causation = CausationReference(GoalTaskScopeCorrection.resumeCause + "unproven-issue"),
              history = current.get.history.init :+ current.get.history.last.copy(causation =
                CausationReference(GoalTaskScopeCorrection.resumeCause + "unproven-issue")))
            _ = GoalTaskValidationContinuation.retainedC(storage, unproven, c.core, next).isSuccess shouldBe false
            nativehost = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
              "sm-workflow", payload.root, payload.branch, host.goal.candidatePaths, legacychecks, None, None, None, None, None)
            // Model the mechanical checkpoint saved before failed Advance025's
            // old equality guard, using the actual owner rather than a row edit.
            resumed <- if (checkpointed) GoalTaskScopeCorrection.resumeC(nativehost, storage, current.get, c.core)
              else Consequence.success(current.get)
            _ <- GoalTaskValidationContinuation.retainedC(storage, resumed, c.core, next)
          } yield ())
          Then("collection retains the original exact direction at either native frontier")
          _value_[V.GoalTaskValidationContinuationDescriptionResult]("describeGoalTaskValidationContinuation",
            List(Property("sessionId", started.sessionId, None))).target shouldBe description.target
          _value_[V.GoalTaskValidationContinuationDescriptionResult]("adoptGoalTaskValidationContinuation",
            List(Property("input", answer, None))).outcome shouldBe "already-resumed"
          When("ordinary Advance issues the amended same-purpose Repair")
          val successor = _value_[V.GoalTaskView]("advanceGoalTask", List(Property("sessionId", started.sessionId, None)))
          Then("the Session, successes, counts and previous direction stay intact")
          successor.sessionId shouldBe started.sessionId
          successor.issuedWorkContext.get.continuationId should not be old
          val after = state(successor)
          after.input.ownedPaths shouldBe before.input.ownedPaths ++ added
          after.copy(input = before.input) shouldBe before
          after.receipts.take(target.business.state.receipts.size) shouldBe target.business.state.receipts
          after.ordinaryCount shouldBe before.ordinaryCount
          after.automaticCount shouldBe before.automaticCount
          after.generation shouldBe before.generation
          prepare(c => for {
            storage <- c.taskStorageC()
            instances <- storage.bindInstancesC()(using c.executionContext)
            current <- instances.loadC(InstanceIdentity(started.sessionId))(using c.executionContext)
            payload <- GoalTaskNativeRun.loadPayloadC(storage, started.sessionId, current.get.revision.value - 1L, c.core)
            _ = payload.validationContinuation shouldBe Some(originaldirection)
            _ = payload.validationContinuation.get.target shouldBe target.asJson
          } yield ())
          _value_[V.GoalTaskView]("advanceGoalTask", List(Property("sessionId", started.sessionId, None))).issuedWorkContext shouldBe successor.issuedWorkContext
          Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        }
      }
    }

    "refuse claimed, completed or stale claim-released issues without supersession" in {
      Vector("claimed", "completed", "released").foreach { disposition =>
        val root = directory()
        val original = ThreeWorkflowNormalHostFixture.definition(root)
        val host = original.copy(goal = original.goal.copy(candidatePaths = input.ownedPaths.toVector ++ added,
          stepValidation = original.goal.stepValidation.copy(checks = checks)))
        _with_runtime_(root, "Equal", Some(host), BoundedWorkExecutionAdmissionFixture.parameters) { (owner, component, actor, prepare) =>
          prepare(_.setupC(_setup_commands_(root, "Equal").size))
          def call(op: String, props: List[Property]): Consequence[OperationResponse] = owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "GoalTaskService", operation = op, properties = props), actor)
          def value[A: org.goldenport.convert.ValueReader](op: String, props: List[Property]): A = read_value[A](take(call(op, props)))
          def provision(record: Record): Unit = prepare { setup =>
            val row = record.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
            setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
          }
          val options = Map("input" -> "input.json", "selection-id" -> "claim-start", "participant" -> "participant",
            "origin" -> "ExplicitHuman", "idempotency-key" -> "claim-start-key", "selection-collection" -> "entity:smworkflow.cli.goal_selection")
          if (disposition != "released")
            provision(take(NativeGoalTaskStartInput.prepareC(options, _ => input.toRecord().toJsonStringC)))
          val started = value[V.GoalTaskView]("startGoalTask", List(Property("input", input, None),
            Property("selectionId", "claim-start", None), Property("participant", "participant", None), Property("idempotencyKey", "claim-start-key", None)) ++
            (if (disposition == "released") List(Property("intent", V.GoalTaskStartIntent("ExplicitHuman"), None)) else Nil))
          val issued = value[V.GoalTaskView]("advanceGoalTask", List(Property("sessionId", started.sessionId, None)))
          val old = issued.issuedWorkContext.get.continuationId
          val described = value[V.GoalTaskScopeCorrectionView]("describeGoalTaskScopeCorrection", List(Property("input",
            V.GoalTaskScopeCorrectionDescriptionInput(started.sessionId, old, added), None)))
          val correction = V.GoalTaskScopeCorrectionInput(started.sessionId, old, described.target, "claim-selection", "participant",
            "claim-key", V.WorkflowArtifactReference("specification-claim-direction", "1"))
          provision(take(NativeGoalTaskScopeCorrectionInput.prepareC(options ++ Map("selection-id" -> correction.selectionId,
            "idempotency-key" -> correction.idempotencyKey), _ => correction.toRecord().toJsonStringC)))
          prepare(call => call.withTaskClaimsC { claims => for {
            claimed <- claims.claimC(ContinuationIdentity(old))
            _ <- if (disposition == "completed") claims.completeC(ContinuationIdentity(old), claimed.claimId.get).map(_ => ())
              else if (disposition == "released") claims.releaseC(ContinuationIdentity(old), claimed.claimId.get).map(_ => ())
              else Consequence.unit
          } yield () })
          call("correctGoalTaskScope", List(Property("input", correction, None))).isSuccess shouldBe false
          val refreshed = call("describeGoalTaskScopeCorrection", List(Property("input",
            V.GoalTaskScopeCorrectionDescriptionInput(started.sessionId, old, added), None)))
          refreshed.isSuccess shouldBe (disposition == "released")
          if (disposition == "released")
            read_value[V.GoalTaskScopeCorrectionView](take(refreshed)).target should not be described.target
          Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        }
      }
    }

    "retain the managed issue, admit one exact selected correction and issue a bound successor only on Advance" in {
      val root = directory()
      val original = ThreeWorkflowNormalHostFixture.definition(root)
      val host = original.copy(goal = original.goal.copy(candidatePaths = input.ownedPaths.toVector ++ added,
        stepValidation = original.goal.stepValidation.copy(checks = checks)))
      var session = ""
      var oldContext = Option.empty[V.WorkflowIssuedWorkContext]
      var correction = Option.empty[V.GoalTaskScopeCorrectionInput]
      var oldState = Option.empty[S.State]
      var base = ""
      val options = Map("input" -> "input.json", "selection-id" -> "initial-selection", "participant" -> "participant",
        "origin" -> "ExplicitHuman", "idempotency-key" -> "initial-key", "selection-collection" -> "entity:smworkflow.cli.goal_selection")
      _with_runtime_(root, "Equal", Some(host), BoundedWorkExecutionAdmissionFixture.parameters) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "Equal").size))
        def call(op: String, props: List[Property]): Consequence[OperationResponse] = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "GoalTaskService", operation = op, properties = props), actor)
        def value[A: org.goldenport.convert.ValueReader](op: String, props: List[Property]): A = read_value[A](take(call(op, props)))
        def importSelection(prepared: Record): Unit = prepare { setup =>
          val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        }
        val unrelated = root.resolve("repository/unrelated.txt")
        Files.writeString(unrelated, "SPECIFICATION-OWNED unrelated work\n")
        importSelection(take(NativeGoalTaskStartInput.prepareC(options, _ => input.toRecord().toJsonStringC)))
        val started = value[V.GoalTaskView]("startGoalTask", List(Property("input", input, None),
          Property("selectionId", "initial-selection", None), Property("participant", "participant", None), Property("idempotencyKey", "initial-key", None)))
        session = started.sessionId
        val issued = value[V.GoalTaskView]("advanceGoalTask", List(Property("sessionId", session, None)))
        oldContext = issued.issuedWorkContext
        oldState = Some(state(issued))
        val old = oldContext.get.continuationId
        val description = value[V.GoalTaskScopeCorrectionView]("describeGoalTaskScopeCorrection", List(Property("input",
          V.GoalTaskScopeCorrectionDescriptionInput(session, old, added), None)))
        val target = take(GoalTaskScopeCorrection.targetC(description.target))
        base = target.business.baseHead
        target.business.branch shouldBe "main"
        description.outcome shouldBe "decision-required"
        val selected = V.GoalTaskScopeCorrectionInput(session, old, description.target, "scope-selection", "participant",
          "scope-key", V.WorkflowArtifactReference("specification-standing-direction", "1"))
        correction = Some(selected)
        call("correctGoalTaskScope", List(Property("input", selected, None))).isSuccess shouldBe false
        Vector(Vector("../escape"), Vector("docs/contract.md", "docs/contract.md"), Vector("src/test/scala")).foreach { paths =>
          call("describeGoalTaskScopeCorrection", List(Property("input", V.GoalTaskScopeCorrectionDescriptionInput(session, old, paths), None))).isSuccess shouldBe false
        }
        call("describeGoalTaskScopeCorrection", List(Property("input", V.GoalTaskScopeCorrectionDescriptionInput("foreign-session", old, added), None))).isSuccess shouldBe false
        importSelection(take(NativeGoalTaskScopeCorrectionInput.prepareC(options ++ Map("selection-id" -> selected.selectionId,
          "idempotency-key" -> selected.idempotencyKey), _ => selected.toRecord().toJsonStringC)))
        val crossed = selected.copy(target = target.copy(additionalOwnedPaths = Vector("docs/contract.md")).asJson.noSpaces)
        val timing = selected.copy(target = target.copy(submissionConditions = Some(Vector("behavior"))).asJson.noSpaces)
        Vector(crossed, timing, selected.copy(target = "{}"), selected.copy(participant = "foreign")).foreach { bad =>
          call("correctGoalTaskScope", List(Property("input", bad, None))).isSuccess shouldBe false
        }
        value[V.GoalTaskScopeCorrectionView]("correctGoalTaskScope", List(Property("input", selected, None))).outcome shouldBe "corrected-awaiting-advance"
        value[V.GoalTaskScopeCorrectionView]("correctGoalTaskScope", List(Property("input", selected, None))).outcome shouldBe "already-corrected"
        prepare(call => call.withTaskClaimsC { claims =>
          claims.claimC(ContinuationIdentity(old)).isSuccess shouldBe false
          claims.completeC(ContinuationIdentity(old), "specification-late-old-token").isSuccess shouldBe false
          Consequence.unit
        })
        Files.readString(unrelated) shouldBe "SPECIFICATION-OWNED unrelated work\n"
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      }
      _with_runtime_(root, "Equal", Some(host), BoundedWorkExecutionAdmissionFixture.parameters) { (owner, component, actor, prepare) =>
        def call(op: String, props: List[Property]): Consequence[OperationResponse] = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "GoalTaskService", operation = op, properties = props), actor)
        def value[A: org.goldenport.convert.ValueReader](op: String, props: List[Property]): A = read_value[A](take(call(op, props)))
        value[V.GoalTaskScopeCorrectionView]("correctGoalTaskScope", List(Property("input", correction.get, None))).outcome shouldBe "already-corrected"
        val next = value[V.GoalTaskView]("advanceGoalTask", List(Property("sessionId", session, None)))
        next.sessionId shouldBe session
        next.issuedWorkContext.get.continuationId should not be oldContext.get.continuationId
        val after = state(next)
        after.input.ownedPaths shouldBe oldState.get.input.ownedPaths ++ added
        after.input.copy(ownedPaths = oldState.get.input.ownedPaths) shouldBe oldState.get.input
        after.copy(input = oldState.get.input) shouldBe oldState.get
        val description = value[V.GoalTaskScopeCorrectionView]("describeGoalTaskScopeCorrection", List(Property("input",
          V.GoalTaskScopeCorrectionDescriptionInput(session, oldContext.get.continuationId, added), None)))
        take(GoalTaskScopeCorrection.targetC(description.target)).business.baseHead shouldBe base
        value[V.GoalTaskView]("advanceGoalTask", List(Property("sessionId", session, None))).issuedWorkContext shouldBe next.issuedWorkContext
        val reference = V.WorkflowArtifactReference("specification-result", "1")
        val result = V.GoalTaskWorkResult("completed", "SPECIFICATION-OWNED result", Vector.empty,
          NonEmptyVector.of(V.GoalTaskCondition("behavior", true, "fixture"), V.GoalTaskCondition("delivery", true, "fixture")), Vector.empty, None, None)
        val evidence = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-worker"), None,
          Some(V.WorkflowExecutionObservation("INLINE", BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity,
            BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity, BoundedWorkExecutionAdmissionFixture.profile.identity)))
        val fields = List(Property("sessionId", session, None), Property("result", result, None),
          Property("resultReference", reference, None), Property("executionEvidence", evidence, None))
        call("submitGoalTaskWorkResult", fields).isSuccess shouldBe false
        call("submitGoalTaskWorkResult", fields :+ Property("issuedWorkContext", oldContext.get, None)).isSuccess shouldBe false
        call("submitGoalTaskWorkResult", fields :+ Property("issuedWorkContext", next.issuedWorkContext.get.copy(runId = "foreign"), None)).isSuccess shouldBe false
        value[V.GoalTaskView]("submitGoalTaskWorkResult", fields :+ Property("issuedWorkContext", next.issuedWorkContext.get, None)).kind shouldBe "Submitted"
        call("correctGoalTaskScope", List(Property("input", correction.get, None))).isSuccess shouldBe true
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      }
    }
  }
}
