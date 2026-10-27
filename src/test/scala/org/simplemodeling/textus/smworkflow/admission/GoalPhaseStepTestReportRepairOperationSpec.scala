/**
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.{Files, Path}
import java.nio.file.attribute.PosixFilePermissions
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseStepTestReportRepair as A
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeValidation as N
import GoalPhaseNativeRun as F
import SplitPhaseExplicitPartitionFixture.take

/** Real isolated managed host/SQLite and native typed process capture. The
  * executable deliberately emits fixture INVALID reports; it runs no tests
  * and provides no successful selected-test or genuine Human evidence.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseStepTestReportRepairOperationSpec extends AnyWordSpec with GoalPhaseValidationRecoveryOperationFixture {
  private val _classes = Vector("fixture.OneSpec", "fixture.TwoSpec")
  override protected def _recovery_plan = O.Plan("step-report-operation", "2", "phase-2", "sm-workflow",
    Vector(O.Step("step-1", "Correct only the fixture report", Vector("value.txt"), Vector.empty, Vector("focused"))),
    Vector("phase-test"), repairLimit = 2)
  override protected def recoveryHost(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
    val original = super.recoveryHost(root)
    original.copy(goal = original.goal.copy(stepValidation = N.Policy("step-report", 1,
      Vector(N.Check("focused", 1, WorkflowSbtValidation.Kind.TestOnly))),
      phaseValidation = N.Policy("phase-report", 1, Vector(N.Check("phase-test", 2, WorkflowSbtValidation.Kind.Test)))))
  }
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    val repository = root.resolve("repository")
    // Shared setup stages these ordinary build inputs before its initial
    // commit; the inherited recovery setup also stages CompileConsumer.
    // Preparing them does not execute SBT: only the explicit invalid-report
    // process below is selected by this fixture's native validation policy.
    if (!Files.exists(repository.resolve("build.sbt"))) TypedSbtHostPlanFixture.build(root, true)
    val file = repository.resolve("src/main/scala/CompileConsumer.scala")
    Files.createDirectories(file.getParent)
    if (!Files.exists(file)) Files.writeString(file, "object CompileConsumer // inert fixture setup\n")
    require(Vector(".gitignore", "build.sbt", "project/build.properties", "src/test/scala/ProductCheck.scala",
      "value.txt", "src/main/scala/CompileConsumer.scala").forall(path => Files.isRegularFile(repository.resolve(path))),
      "The report-restoration fixture must prepare every shared and inherited initial staging input")
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val executable = root.resolve("invalid-report-fixture.sh")
    if (!Files.exists(executable)) {
      val marker = "'" + root.resolve("report-processes.txt").toString.replace("'", "'\\''") + "'"
      val report = _classes.map(name => "[info] " + name.split('.').last + ":\n[info] Total number of tests run: 1\n" +
        "[info] Suites: completed 1, aborted 0\n[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0\n[info] All tests passed.\n").mkString
      Files.writeString(executable, "#!/bin/sh\nprintf 'fixture-process\\n' >> " + marker + "\ncat <<'FIXTURE_INVALID_REPORT'\n" +
        report + "FIXTURE_INVALID_REPORT\nexit 0\n")
      Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"))
    }
    require(goal.candidatePaths == Vector("value.txt") && goal.closure.programs.map(_.step) == Vector("step-1"),
      "The report-restoration fixture owns exactly step-1 and value.txt")
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), goalPaths = Vector("value.txt"),
      goalMessages = Vector("Goal Step step-1"), goalSelections = Vector(Vector("value.txt")))
    TypedSbtHostPlanFixture.plan(root, executable, Vector(1), Some(git), operation = SbtOperation.TestOnly, testClasses = _classes)
  }
  private def _native(record: InstanceRecord): Json = io.circe.parser.parse(take(WorkflowInstanceRecordJsonV1.encodeC(record))).toOption.get
  private def _processes(root: Path): Int = Files.readString(root.resolve("report-processes.txt")).linesIterator.size
  private def _requiredC[A](value: Option[A], fact: String): Consequence[A] = value match {
    case Some(actual) => Consequence.success(actual)
    case None => Consequence.stateConflict("Report-restoration fixture is missing " + fact)
  }
  private def _source(e: RecoveryEpisode, identity: String, original: InstanceRecord): A.Source = {
    var result = Option.empty[A.Source]
    e.prepare(call => for {
      catalog <- GoalPhaseRuntimeCatalog.loadC()
      component <- _requiredC(call.core.component, "the actual setup component")
      owner <- _requiredC(component.subsystem, "the actual setup subsystem")
      storage <- WorkflowComponentStorage.bindC(ComponentCreate(owner, ComponentOrigin.Builtin),
        SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
      instances <- storage.bindInstancesC()(using call.executionContext)
      loaded <- instances.loadC(InstanceIdentity(identity))(using call.executionContext)
      current <- _requiredC(loaded, "the current native instance " + identity)
      _ = _native(current) shouldBe _native(original)
      starts <- storage.bindMechanicalC(F.startOperation, F.codec)
      startReference = WorkflowMechanicalTransition.Reference(identity + ":ordered-start", "1")
      start <- starts.loadRetainedC(startReference)(using call.executionContext)
      started <- _requiredC(start, "the retained start receipt " + startReference)
      candidateReader <- storage.bindMechanicalC(F.advanceOperation, GoalPhaseNativeCandidate.codec)
      inspection <- _requiredC(current.history.findLast(_.causation.value == "ordered-candidate:inspection"),
        "the native ordered-candidate:inspection history entry")
      // Store._reference anchors all inspection/staging/candidate receipts to
      // the initial frontier, before its inspection entry. Later records also
      // remain in InspectCandidate and cannot identify that receipt namespace.
      frontier <- GoalPhaseNativeHistory.prefixC(current, InstanceRevision(inspection.revision.value - 1))
      _ = frontier.currentProgression shouldBe Some(ProgressionReference("phase.InspectCandidate"))
      candidateReference = WorkflowMechanicalTransition.Reference(identity + ":ordered-candidate:" + frontier.revision.value + ":candidate", "1")
      candidate <- candidateReader.loadRetainedC(candidateReference)(using call.executionContext)
      retainedCandidate <- _requiredC(candidate, "the retained candidate receipt " + candidateReference)
      savedCandidate = GoalPhaseNativeCandidate.Saved(retainedCandidate.receipt.next, retainedCandidate.receipt.payload.value)
      _ = savedCandidate.payload.sourceRevision shouldBe frontier.revision.value.toString
      candidatePrefix <- GoalPhaseNativeHistory.prefixC(current, savedCandidate.record.revision)
      _ = _native(candidatePrefix) shouldBe _native(savedCandidate.record)
      reader <- storage.bindMechanicalC(F.advanceOperation, N.codec)
      validationReference = WorkflowMechanicalTransition.Reference(identity + ":ordered-validation:" +
        savedCandidate.record.revision.value + ":validation", "1")
      validation <- reader.loadRetainedC(validationReference)(using call.executionContext)
      retainedValidation <- _requiredC(validation, "the retained terminal validation receipt " + validationReference)
      _ = retainedValidation.receipt.payload.value.sourceRevision shouldBe savedCandidate.record.revision.value.toString
      _ = _native(retainedValidation.receipt.next) shouldBe _native(current)
      initial <- O.startC(_recovery_plan, started.receipt.payload.value.base, Some(GoalPhaseRuntimeRepairControl.version))
      semantic <- initial.next match {
        case value: O.Semantic => Consequence.success(value)
        case value => Consequence.stateConflict("Report-restoration fixture start has no semantic implementation: " + value)
      }
      implemented <- initial.acceptC(O.SemanticCompleted(semantic.key, "Fixture completed"))
      observedCandidate <- _requiredC(savedCandidate.payload.candidate, "the actual observed candidate in " + candidateReference)
      inspected <- implemented.acceptC(O.CandidateObserved(observedCandidate))
      failed <- N.observedStateC(inspected, retainedValidation.receipt.payload.value,
        validationReference.identity, savedCandidate.payload.changedPaths)
      selected <- A.selectC(current, failed, Some(savedCandidate), Some(N.Saved(retainedValidation.receipt.next,
        retainedValidation.receipt.payload.value)), recoveryHost(e.root).goal.stepValidation.checks)
      actual <- _requiredC(selected, "eligibility from the exact retained candidate/validation/controller Decision")
      _ = result = Some(actual)
    } yield ())
    result.getOrElse(fail("Report-restoration fixture source callback produced no actual source"))
  }

  "Ordinary managed Step report restoration" should {
    "leave failed preparation unchanged, reopen the retained Decision and issue one counted repair without replaying the process" in {
      val root = freshRecoveryRoot("step-report")
      var identity = ""
      var source = Option.empty[A.Source]
      var issued = Option.empty[V.GoalPhaseAdvanceResult]
      var preserved = Option.empty[Json]
      val stop: String => Consequence[Unit] = point =>
        if (point == "goal-checks-admitted") Consequence.stateConflict("Fixture interruption after durable failed validation")
        else Consequence.unit
      withRecoveryEpisode(root, initialize = true, acknowledgement = Some(stop)) { e =>
        identity = e.start().sessionId
        val implementation = e.advance(identity)
        implementation.work.get.kind shouldBe "ImplementStep"
        e.submitImplementation(implementation).accepted shouldBe true
        e.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(identity)).isSuccess shouldBe false
        val decision = e.current(identity)
        decision.currentProgression shouldBe Some(ProgressionReference("phase.Decision"))
        _processes(root) shouldBe 1
        source = Some(_source(e, identity, decision))
        source.get.validation.payload.outcome shouldBe Some("failed")
        source.get.validation.payload.receipts.last.sbt.get.reason shouldBe Some("InvalidReport")
        // Reject failed program preparation before committing a disposition.
        // This is not a datastore commit/UNKNOWN fault-injection claim.
        e.prepare(call => for {
          catalog <- GoalPhaseRuntimeCatalog.loadC()
          storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
            SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
          adapter <- storage.bindMechanicalC(F.advanceOperation, A.codec)
          failed = adapter.executeC(WorkflowMechanicalTransition.Intent(_configuration_, source.get.reference,
            decision, decision.revision, F.history(decision, "phase.Repair", A.causation), TypedValue(A.codec.typeIdentity, A.payload(source.get))),
            ConsequenceT[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](
              cats.free.Free.pure(Consequence.stateConflict[Unit]("Fixture transaction preparation failed"))))(using call.executionContext)
          _ = failed.isSuccess shouldBe false
          _ = failed.toString should include("Fixture transaction preparation failed")
          retained <- adapter.loadRetainedC(source.get.reference)(using call.executionContext)
          _ = retained shouldBe None
        } yield ())
        _native(e.current(identity)) shouldBe _native(decision)
      }
      withRecoveryEpisode(root) { e =>
        // Corrected bytes may exist before the new repair is issued. They must
        // remain owned and cannot be certified as the old accepted candidate.
        Files.writeString(root.resolve("repository/value.txt"), "fixture corrected report preparation\n")
        val repair = e.advance(identity)
        withClue(s"Actual restoration ${repair.kind}: ${repair.message}. ") { repair.kind shouldBe "Request" }
        repair.work.get.kind shouldBe "RepairStep"
        repair.work.get.stepIdentity shouldBe Some("step-1")
        repair.work.get.candidate shouldBe None
        repair.work.get.checksReceipt shouldBe None
        repair.work.get.review shouldBe None
        repair.completion shouldBe None
        repair.work.get.checkFailure.get.initialPhaseRepair shouldBe None
        repair.work.get.checkFailure.get.stepTestReportRepair shouldBe Some(V.WorkflowArtifactReference(source.get.reference.identity, "1"))
        repair.work.get.checkFailure.get.validation.outcome shouldBe Some("failed")
        repair.repairProgress.get.automaticCount shouldBe 1
        repair.repairProgress.get.ordinaryCount shouldBe 1
        _processes(root) shouldBe 1
        val current = e.current(identity)
        current.history.count(_.causation.value == A.causation) shouldBe 1
        _native(take(GoalPhaseNativeHistory.prefixC(current, source.get.from.revision))) shouldBe _native(source.get.from)
        e.advance(identity).issuedWorkContext shouldBe repair.issuedWorkContext
        _native(e.current(identity)) shouldBe _native(current)
        issued = Some(repair)
        preserved = Some(_native(current))
      }
      withRecoveryEpisode(root) { e =>
        e.advance(identity).issuedWorkContext shouldBe issued.get.issuedWorkContext
        _native(e.current(identity)) shouldBe preserved.get
        e.prepare(call => for {
          catalog <- GoalPhaseRuntimeCatalog.loadC()
          storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
            SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
          adapter <- storage.bindMechanicalC(F.advanceOperation, A.codec)
          competing = WorkflowMechanicalTransition.Reference(source.get.reference.identity + ":competing", "1")
          attempted = adapter.executeC(WorkflowMechanicalTransition.Intent(_configuration_, competing,
            source.get.from, source.get.from.revision, F.history(source.get.from, "phase.Repair", A.causation),
            TypedValue(A.codec.typeIdentity, A.payload(source.get))),
            ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using call.executionContext)
          _ = attempted.isSuccess shouldBe false
          absent <- adapter.loadRetainedC(competing)(using call.executionContext)
          _ = absent shouldBe None
        } yield ())
        _native(e.current(identity)) shouldBe preserved.get
        _processes(root) shouldBe 1
        source.get.validation.payload.receipts.last.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Unverified
      }
    }
  }
}
