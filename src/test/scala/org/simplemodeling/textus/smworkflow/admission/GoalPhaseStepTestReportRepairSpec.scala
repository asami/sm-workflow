/**
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseStepTestReportRepair as A
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeValidation as V
import SplitPhaseExplicitPartitionFixture.take

/** Specification-owned native shapes, not process execution or live authority. */
private[admission] object GoalPhaseStepTestReportRepairFixture {
  val base = O.Snapshot("a" * 40, "b" * 40)
  val snapshot = base.copy(tree = "c" * 40)
  val checks = Vector(V.Check("compile", 1, WorkflowSbtValidation.Kind.Compile),
    V.Check("focused", 2, WorkflowSbtValidation.Kind.TestOnly))
  val plan = O.Plan("step-report-plan", "2", "6.7", "fixture-repository",
    Vector(O.Step("T4", "Correct the selected report", Vector("src", "build.sbt"), Vector.empty, checks.map(_.identity))),
    Vector("phase-test"), repairLimit = 10)
  def append(from: InstanceRecord, stage: String, cause: String): InstanceRecord =
    take(from.appendC(from.revision, GoalPhaseNativeRun.history(from, "phase." + stage, cause)))
  private def key(state: O.State) = state.next.asInstanceOf[O.Semantic].key
  def receipt(check: V.Check, unverified: Boolean): RepositorySyncNativeMergeValidation.Receipt = {
    val bytes = (if (unverified) "Fixture malformed multi-run report" else "Fixture compile observation").getBytes(UTF_8).toVector
    val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(0),
      ProcessExecutionCapture(bytes, bytes.size.toLong, false), ProcessExecutionCapture(Vector.empty, 0L, false),
      Vector.empty, 1L, "fixture-step-report")
    RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(0), new String(bytes.toArray, UTF_8), "",
      Some(WorkflowSbtValidation.Facts(check.kind,
        if (unverified) WorkflowSbtValidation.Outcome.Unverified else WorkflowSbtValidation.Outcome.Succeeded,
        None, Option.when(unverified)("InvalidReport"))), Some(process))
  }
  final case class Fixture(from: InstanceRecord, controller: O.State,
    candidate: GoalPhaseNativeCandidate.Saved, validation: V.Saved) {
    def selectC = A.selectC(from, controller, Some(candidate), Some(validation), checks)
  }
  def fixture(repairs: Int = 3, limit: Int = 10, policy: Option[String] = Some(GoalPhaseRuntimeRepairControl.version)): Fixture = {
    var state = take(O.startC(plan.copy(repairLimit = limit), base, policy))
    state = take(state.acceptC(O.SemanticCompleted(key(state), "Fixture initial implementation")))
    (0 until repairs).foreach { n =>
      state = take(state.acceptC(O.CandidateObserved(snapshot)))
      state = take(state.acceptC(O.RepairableChecksObserved(snapshot, checks.map(_.identity), "prior-failure-" + n,
        O.RepairDirective("Fixture ordinary correction", Vector("Retained fixture defect")), comparableFindings = false)))
      state = take(state.acceptC(O.SemanticCompleted(key(state), "Fixture correction completed")))
    }
    state = take(state.acceptC(O.CandidateObserved(snapshot)))
    val initial = take(InstanceRecord(InstanceIdentity("fixture-step-report"), take(GoalPhaseRuntimeCatalog.loadC()).definition,
      initialRevision, Lifecycle.NotStarted, None, CorrelationReference("fixture"), CausationReference("fixture"),
      None, None, None, Vector.empty).validateC)
    val inspection = append(initial, "InspectCandidate", "fixture-implementation")
    val candidateRecord = append(inspection, "Validation", "ordered-candidate:candidate")
    val candidate = GoalPhaseNativeCandidate.Saved(candidateRecord, GoalPhaseNativeCandidate.Payload(
      inspection.revision.value.toString, "T4", Vector("src/Behavior.scala"), Vector("src/Behavior.scala"),
      Some(snapshot), Vector("src/Behavior.scala"), Some("Fixture original full diff")))
    val record = append(candidateRecord, "Decision", "ordered-validation:validation")
    val value = V.Payload(candidateRecord.revision.value.toString, "T4", snapshot, checks,
      checks.zipWithIndex.map((check, index) => receipt(check, index == checks.size - 1)), Some("failed"))
    val reference = record.identity.value + ":ordered-validation:" + value.sourceRevision + ":validation"
    Fixture(record, take(V.observedStateC(state, value, reference)), candidate, V.Saved(record, value))
  }
  def frozenDependencyFixture(): Fixture = {
    val f = fixture()
    val original = DependencyValidationSpecificationFixture
    val requester = original.requester.copy(repository = f.controller.plan.repository)
    val binding = original.binding.copy(step = "T4", requester = requester, checks = Vector(
      V.Check("dependency-compile", 3, WorkflowSbtValidation.Kind.Compile),
      V.Check("dependency-focused", 4, WorkflowSbtValidation.Kind.TestOnly)))
    val approvals = original.approvals.map(approval => approval.copy(step = "T4",
      proposal = approval.proposal.copy(requester = requester)))
    val admission = GoalPhaseDependencyValidation.Admission(binding, approvals)
    val capture = original.capture.copy(session = f.from.identity.value,
      plan = ContextReference(f.controller.plan.identity, f.controller.plan.revision), step = "T4",
      generation = f.controller.generation, sourceRevision = f.candidate.payload.sourceRevision,
      primaryBase = f.controller.base, admission = admission)
    val combined = take(GoalPhaseNativeDependencyValidation.checksC(checks, Some(capture)))
    f.copy(candidate = f.candidate.copy(payload = f.candidate.payload.copy(dependencyAdmission = Some(admission),
      dependencyStaged = binding.paths, dependency = Some(capture))),
      validation = f.validation.copy(payload = f.validation.payload.copy(selected = combined, dependency = Some(capture))))
  }
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseStepTestReportRepairSpec extends AnyWordSpec with Matchers {
  import GoalPhaseStepTestReportRepairFixture.*
  "Terminated Step test-report repair" should {
    "retain an unchanged frozen dependency only when the primary report precedes every dependency execution" in {
      val f = frozenDependencyFixture()
      val validation = f.validation.payload
      A.terminalFailureValid(validation) shouldBe false
      V.repairable(validation) shouldBe false
      A.frozenDependencyFailureValid(validation) shouldBe true
      val source = take(f.selectC).get
      val payload = A.payload(source)
      A.codec.decode(A.codec.encode(payload)) shouldBe Right(payload)
      payload.frozenDependencyBranch.get.contract shouldBe "primary-invalid-report-before-frozen-dependency.v1"
      payload.frozenDependencyBranch.get.selected shouldBe validation.selected
      A.codec.decode(A.codec.encode(payload.copy(frozenDependencyBranch = None))).isLeft shouldBe true
      val changedBranch = payload.frozenDependencyBranch.map(_.copy(captureSourceRevision = "9999"))
      A.codec.decode(A.codec.encode(payload.copy(frozenDependencyBranch = changedBranch))).isLeft shouldBe true
      A.codec.decode(A.codec.encode(payload.copy(originalCandidate = GoalPhaseNativeCandidate.codec.encode(
        fixture().candidate.payload)))).isLeft shouldBe true
      payload.originalCandidate shouldBe GoalPhaseNativeCandidate.codec.encode(f.candidate.payload)
      payload.originalValidation shouldBe V.codec.encode(validation)
      val repair = take(A.admittedStateC(source, payload))
      repair.repairs shouldBe 4
      repair.ordinaryRepairs shouldBe 4
      repair.plan shouldBe f.controller.plan
      repair.base shouldBe f.controller.base
      repair.completed shouldBe f.controller.completed
      validation.receipts.map(_.identity) shouldBe checks.map(_.identity)
      validation.selected.drop(checks.size).map(_.dependency) shouldBe Vector.fill(2)(Some(validation.dependency.get.admission.binding.identity))
      validation.dependencyExecutions shouldBe empty
      val provenance = Some(ContextReference(source.reference.identity, "1"))
      GoalPhaseNativeWork.checkFailureContextC(f.candidate.payload.changedPaths, f.candidate.payload.diff.get,
        validation).isSuccess shouldBe false
      GoalPhaseNativeWork.checkFailureContextC(f.candidate.payload.changedPaths, f.candidate.payload.diff.get,
        validation, stepTestReportRepair = provenance).isSuccess shouldBe true
    }

    "refuse dependency attempts and crossed frozen source metadata without broadening the default failure route" in {
      val f = frozenDependencyFixture()
      val value = f.validation.payload
      val dependent = value.selected.drop(checks.size)
      val execution = GoalPhaseDependencyValidation.Execution(dependent.head.identity, 0,
        value.dependency.get.admission.binding.dependency.workspace, "compile")
      val altered = Vector(
        value.copy(receipts = value.receipts :+ receipt(dependent.head, false), dependencyExecutions = Vector(execution)),
        value.copy(dependencyExecutions = Vector(execution)),
        value.copy(stopped = Vector(receipt(dependent.head, false))),
        value.copy(admissionFailures = Vector(V.AdmissionFailure(dependent.head.identity, 0, "Fixture denial"))),
        value.copy(uncertainties = Vector(V.Uncertainty(dependent.head.identity, 0, "Fixture UNKNOWN"))),
        value.copy(newAttempts = Vector(V.NewAttempt(dependent.head.identity, 1, "1", "fixture-evidence", "1", "participant", "Fixture new attempt"))),
        value.copy(sourceConsumption = Some(DependencySourceRevisionSpecificationFixture.proposal.consumption)),
        value.copy(selected = value.selected.dropRight(1)), value.copy(selected = value.selected.reverse))
      altered.foreach { invalid =>
        A.terminalFailureValid(invalid) shouldBe false
        A.frozenDependencyFailureValid(invalid) shouldBe false
        f.copy(validation = f.validation.copy(payload = invalid)).selectC.toOption.forall(_.isEmpty) shouldBe true
      }
      val capture = value.dependency.get
      val changedCaptures = Vector(capture.copy(session = "foreign"), capture.copy(plan = ContextReference("foreign", "2")),
        capture.copy(step = "foreign"), capture.copy(primaryBase = base.copy(tree = "d" * 40)),
        capture.copy(generation = f.controller.generation + 1), capture.copy(sourceRevision = "9999"), capture.copy(sourceRevision = "01"))
      changedCaptures.foreach { changed =>
        val crossed = f.copy(candidate = f.candidate.copy(payload = f.candidate.payload.copy(dependency = Some(changed))),
          validation = f.validation.copy(payload = value.copy(dependency = Some(changed))))
        crossed.selectC.toOption.forall(_.isEmpty) shouldBe true
      }
      f.copy(validation = f.validation.copy(payload = value.copy(dependency = Some(capture.copy(generation = capture.generation - 1)))))
        .selectC.isSuccess shouldBe false
    }

    "retain the failed unverified source and reserve exactly one ordinary same-Step batch" in {
      val f = fixture()
      val source = take(f.selectC).get
      val payload = A.payload(source)
      val state = take(A.admittedStateC(source, payload))
      val again = take(A.admittedStateC(source, payload))
      f.validation.payload.outcome shouldBe Some("failed")
      f.validation.payload.receipts.last.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Unverified
      V.repairable(f.validation.payload) shouldBe false
      state.stage shouldBe O.Stage.Repair
      state.repairs shouldBe 4
      state.ordinaryRepairs shouldBe 4
      again.repairs shouldBe state.repairs
      state.generation shouldBe f.controller.generation + 1
      state.plan shouldBe f.controller.plan
      state.base shouldBe base
      state.completed shouldBe f.controller.completed
      state.candidate shouldBe None
      state.checksReceipt shouldBe None
      state.next.asInstanceOf[O.Semantic].key.kind shouldBe O.WorkKind.RepairStep
      state.next.asInstanceOf[O.Semantic].key.step shouldBe Some("T4")
      state.continueStepTestReportRepairC(payload.validationReference.identity, payload.directive).isSuccess shouldBe false
      A.codec.decode(A.codec.encode(payload)) shouldBe Right(payload)
      payload.frozenDependencyBranch shouldBe None
      A.codec.encode(payload).hcursor.downField("payload").downField("frozenDependencyBranch").focus shouldBe None
      payload.originalValidation shouldBe V.codec.encode(f.validation.payload)
      payload.originalCandidate shouldBe GoalPhaseNativeCandidate.codec.encode(f.candidate.payload)
      payload.nativeSource shouldBe io.circe.parser.parse(take(WorkflowInstanceRecordJsonV1.encodeC(f.from))).toOption.get
      val next = append(f.from, "Repair", A.causation)
      A.successorC(f.from, next).isSuccess shouldBe true
      A.successorC(f.from, append(next, "Repair", "extra")).isSuccess shouldBe false
    }

    "refuse unknown live incomplete truncated cancelled nonzero and other unverified observations" in {
      val f = fixture()
      val value = f.validation.payload
      val last = value.receipts.last
      val raw = last.processObservation.get
      val variants = Vector(
        value.copy(outcome = None), value.copy(outcome = Some("passed")),
        value.copy(uncertainties = Vector(V.Uncertainty("focused", 0, "Fixture unknown"))),
        value.copy(admissionFailures = Vector(V.AdmissionFailure("focused", 0, "Fixture denial"))),
        value.copy(stopped = Vector(last)),
        value.copy(receipts = value.receipts.dropRight(1)),
        value.copy(receipts = value.receipts.updated(1, last.copy(processObservation = None))),
        value.copy(receipts = value.receipts.updated(1, last.copy(exitCode = Some(1),
          processObservation = Some(raw.copy(termination = ProcessExecutionTermination.Exited(1)))))),
        value.copy(receipts = value.receipts.updated(1, last.copy(termination = "cancelled", exitCode = None,
          processObservation = Some(raw.copy(termination = ProcessExecutionTermination.Cancelled))))),
        value.copy(receipts = value.receipts.updated(1, last.copy(processObservation = Some(raw.copy(
          stdout = raw.stdout.copy(truncated = true)))))),
        value.copy(receipts = value.receipts.updated(1, last.copy(sbt = last.sbt.map(_.copy(reason = Some("SuiteMismatch")))))))
      variants.foreach { altered =>
        A.terminalFailureValid(altered) shouldBe false
        f.copy(validation = f.validation.copy(payload = altered)).selectC.toOption.forall(_.isEmpty) shouldBe true
      }
    }

    "refuse crossed native history scope metadata and exhausted or absent runtime policy" in {
      val f = fixture()
      val crossed = f.copy(candidate = f.candidate.copy(payload = f.candidate.payload.copy(candidate = Some(base))))
      val foreign = f.copy(validation = f.validation.copy(record = f.from.copy(identity = InstanceIdentity("foreign"))))
      val later = f.copy(from = append(f.from, "Decision", "unrelated-later-entry"))
      Vector(crossed, foreign, later).foreach(_.selectC.isSuccess shouldBe false)
      A.selectC(f.from, f.controller, Some(f.candidate), Some(f.validation), checks.reverse).isSuccess shouldBe false
      take(fixture(repairs = 0, policy = None).selectC) shouldBe None
      take(fixture(repairs = 3, limit = 3).selectC) shouldBe None
    }

    "bind closed receipt fields and distinguish this provenance from ordinary failed checks" in {
      val f = fixture()
      val source = take(f.selectC).get
      val value = A.payload(source)
      val wire = A.codec.encode(value)
      A.codec.decode(wire.mapObject(_.add("unexpected", true.asJson))).isLeft shouldBe true
      val unknown = wire.mapObject(_.add("payload", wire.hcursor.downField("payload").focus.get.mapObject(_.add("unexpected", true.asJson))))
      A.codec.decode(unknown).isLeft shouldBe true
      A.admittedStateC(source, value.copy(automaticCount = 0)).isSuccess shouldBe false
      A.admittedStateC(source, value.copy(nativeSource = Json.obj("foreign" -> true.asJson))).isSuccess shouldBe false
      A.admittedStateC(source, value.copy(step = "foreign")).isSuccess shouldBe false
      val reference = ContextReference(source.reference.identity, "1")
      GoalPhaseNativeWork.checkFailureContextC(f.candidate.payload.changedPaths, f.candidate.payload.diff.get,
        f.validation.payload).isSuccess shouldBe false
      GoalPhaseNativeWork.checkFailureContextC(f.candidate.payload.changedPaths, f.candidate.payload.diff.get,
        f.validation.payload, stepTestReportRepair = Some(reference)).isSuccess shouldBe true
      GoalPhaseNativeWork.checkFailureContextC(f.candidate.payload.changedPaths, f.candidate.payload.diff.get,
        f.validation.payload, initialPhaseRepair = Some(reference)).isSuccess shouldBe false
    }
  }
}
