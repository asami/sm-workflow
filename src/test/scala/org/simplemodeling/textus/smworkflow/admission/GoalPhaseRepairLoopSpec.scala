package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.component.{Component, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.configuration.{Configuration as GenericConfiguration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.GivenWhenThen
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartCommand, GoalPhaseStartInput, RepairStepResult, ReviewStepResult, StepCloseRequest, StepReviewEvidence}

/**
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseRepairLoopSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"repair loop rejected: $failure")
  }
  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString.stripSuffix("\n") finally source.close()
  }
  private def digest(content: Vector[Byte]): String = MessageDigest.getInstance("SHA-256")
    .digest(content.toArray).map(byte => f"${byte & 0xff}%02x").mkString
  private val now = Instant.parse("2026-09-28T00:00:00Z")
  private val sidecar = take(CandidateAdmissionProducerAbi.parseC(
    resource("/workflow/goal-phase-candidate-admission-producer-abi.json")))
  private val binding = take(WorkflowInstancePersistence.bindCandidateDefinitionC(take(
    CandidateWorkflowAbi.parseC(resource("/workflow/goal-phase-workflow-abi.json"), sidecar,
      "src/main/cozy/sm-workflow.cml"))))

  private final class Fixture(viaCli: Boolean = false, failIntent: Boolean = false,
    fromPublicStart: Boolean = false, withAuthority: Boolean = false)
      extends GoalPhaseApplicationFixture(viaCli, failIntent, fromPublicStart, withAuthority)
  "GoalPhase integrated review and closure authority" should {
    "look up closure authority in the current completion call without treating accepted review as permission" in {
      for (cli <- Vector(false, true); granted <- Vector(false, true)) {
        Given("a Start-selected Workflow and call-scoped closure authority ports")
        val f = new GoalPhaseApplicationFixture(viaCli = cli, fromPublicStart = true,
          withAuthority = true, callScopedAuthority = true)
        try {
          val started = take(f.start())
          f.associateClose(started.handle)
          f.autoGrant = granted // test-only host evidence, not an installer or product grant
          val work = f.firstReview()
          val wire = f.reviewWire(f.reviewResult(work, "accept"))
          When("accepted review completes through the registered Service or CLI Operation")
          val response = take(f.invoke("submitStepReview", wire))
          Then("both authority reads use the completion call's component and context")
          f.authorityCores.size shouldBe 2
          (f.authorityCores.head eq f.authorityCores.last) shouldBe true
          f.authorityCores.head.component.get.componentId shouldBe SmWorkflowComponent.componentId
          f.authorityCores.head.action.request.operation shouldBe "submitStepReview"
          f.authorityPorts.authorizations.loadC(f.captured.get) shouldBe a[Consequence.Failure[?]]
          if (granted) {
            Then("only separately supplied exact authority allows one deterministic closure")
            take(WorkflowTerminalJsonV1.decodeC(response, GoalPhaseStepCloseServiceOperation.terminalCodec)).handle shouldBe started.handle
            f.commits.size shouldBe 1
            f.decisionCores shouldBe empty
          } else {
            Then("review acceptance alone returns a Decision and leaves the Step open")
            take(WorkflowDecisionJsonV1.decodeC(response, GoalPhaseClosureAuthority.inputCodec)).handle shouldBe started.handle
            f.commits shouldBe empty
            f.stored.lifecycle shouldBe Lifecycle.Active
            f.decisionCores.size shouldBe 1
            (f.decisionCores.head eq f.authorityCores.head) shouldBe true
          }
          When("the same review Result is replayed")
          val replay = f.invoke("submitStepReview", wire)
          Then("canonical completion rejects replay without additional authority reads or commits")
          replay shouldBe a[Consequence.Failure[?]]
          f.authorityCores.size shouldBe 2
          f.commits.size shouldBe (if (granted) 1 else 0)
        } finally f.subsystem.shutdown()
      }
    }

    "consume acceptance once and recover the same stop-only Decision through Service and CLI" in {
      for (cli <- Vector(false, true)) {
        val f = new Fixture(cli, fromPublicStart = true, withAuthority = true)
        try {
          val started = take(f.start())
          f.associateClose(started.handle)
          val work = f.firstReview()
          f.authorityCalls shouldBe 0
          val result = f.reviewResult(work, "accept")
          val resultWire = f.reviewWire(result)
          val wire = take(f.invoke("submitStepReview", resultWire))
          val stopped = take(WorkflowDecisionJsonV1.decodeC(wire, GoalPhaseClosureAuthority.inputCodec))
          stopped.handle shouldBe started.handle
          f.saved.issued shouldBe work
          f.saved.submitted shouldBe result
          f.saved.reviewed shouldBe f.stored
          f.captured.get.input.candidate.reviewEvidence shouldBe result.result.value.reviewEvidence
          f.stored.revision shouldBe InstanceRevision(3L)
          f.stored.lifecycle shouldBe Lifecycle.Active
          f.stored.suspension shouldBe empty
          val before = f.stored
          take(f.project(started.handle)) shouldBe wire
          take(f.project(started.handle)) shouldBe wire
          f.invoke("submitStepReview", resultWire) shouldBe a[Consequence.Failure[?]]
          f.invoke("recordStepCloseAuthority", wire) shouldBe a[Consequence.Failure[?]]
          f.stored shouldBe before
          f.creates shouldBe 1
          f.appends shouldBe 3
          f.closeInvocations shouldBe 1
          f.commits shouldBe empty
        } finally f.subsystem.shutdown()
      }
    }

    "commit accepted review only with already recorded exact authority" in {
      for (cli <- Vector(false, true)) {
        val f = new Fixture(cli, fromPublicStart = true, withAuthority = true)
        try {
          val started = take(f.start())
          f.associateClose(started.handle)
          f.autoGrant = true
          val work = f.firstReview()
          val wire = f.reviewWire(f.reviewResult(work, "accept"))
          val terminal = take(WorkflowTerminalJsonV1.decodeC(take(f.invoke("submitStepReview", wire)),
            GoalPhaseStepCloseServiceOperation.terminalCodec))
          terminal.handle shouldBe started.handle
          f.stored.lifecycle shouldBe Lifecycle.Completed
          f.stored.revision shouldBe InstanceRevision(4L)
          take(f.receiptStore.loadC(f.stored.identity, InstanceRevision(3L))).get.issued shouldBe work
          f.commits.size shouldBe 1
          f.commits.head.reviewEvidenceIdentity shouldBe "review-2"
          f.decisionCalls shouldBe 0
          f.closeInvocations shouldBe 1
          f.invoke("submitStepReview", wire) shouldBe a[Consequence.Failure[?]]
          f.project(started.handle) shouldBe a[Consequence.Failure[?]]
          f.commits.size shouldBe 1
        } finally f.subsystem.shutdown()
      }
    }

    "gate changed and unchanged repaired candidates after a new full review on the Start instance" in {
      for (cli <- Vector(false, true); changed <- Vector(false, true); granted <- Vector(false, true)) {
        val f = new Fixture(cli, fromPublicStart = true, withAuthority = true)
        try {
          val started = take(f.start())
          f.associateClose(started.handle)
          val startHistory = f.stored.history
          val repair = f.repairWork()
          f.authorityCalls shouldBe 0
          if (changed) f.program = "authority repaired program\n".getBytes(StandardCharsets.UTF_8).toVector
          val reReview = f.work(take(f.invoke("submitStepRepair", f.repairWire(f.repaired(repair)))))
          f.authorityCalls shouldBe 0
          f.autoGrant = granted
          val result = f.reviewResult(reReview, "accept")
          val resultWire = f.reviewWire(result)
          val wire = take(f.invoke("submitStepReview", resultWire))
          f.captured.get.expectedRevision shouldBe StateMachineRevision("7")
          f.captured.get.input.candidate.programDigest shouldBe digest(f.program)
          f.captured.get.input.candidate.reviewEvidence.map(_.evidenceIdentity) shouldBe Vector("review-2", "review-6")
          if (granted) {
            take(WorkflowTerminalJsonV1.decodeC(wire, GoalPhaseStepCloseServiceOperation.terminalCodec)).handle shouldBe started.handle
            f.stored.revision shouldBe InstanceRevision(8L)
            f.commits.size shouldBe 1
            f.commits.head.reviewEvidenceIdentity shouldBe "review-6"
            f.commits.head.programDigest shouldBe digest(f.program)
          } else {
            take(WorkflowDecisionJsonV1.decodeC(wire, GoalPhaseClosureAuthority.inputCodec)).handle shouldBe started.handle
            f.stored.revision shouldBe InstanceRevision(7L)
            f.saved.submitted shouldBe result
            take(f.project(started.handle)) shouldBe wire
            f.commits shouldBe empty
          }
          f.stored.history.take(1) shouldBe startHistory
          f.closeInvocations shouldBe 1
          f.creates shouldBe 1
          f.invoke("submitStepReview", resultWire) shouldBe a[Consequence.Failure[?]]
        } finally f.subsystem.shutdown()
      }
    }

    "gate proactive full review too without issuing or retaining a WorkOrder" in {
      for (cli <- Vector(false, true); granted <- Vector(false, true)) {
        val f = new Fixture(cli, fromPublicStart = true, withAuthority = true)
        try {
          val started = take(f.start())
          f.associateClose(started.handle)
          val evidence = StepReviewEvidence("proactive-full-review", f.originalCandidate,
            "2026-09-27T23:59:00Z", "2026-09-28T01:00:00Z", "full-step", "reviewed", "fixture-reviewer")
          f.autoGrant = granted
          val wire = take(f.invoke("requestStepClose", GoalPhaseStepCloseJson.encode(
            f.request.copy(reviewEvidence = Vector(evidence)))))
          take(f.receiptStore.loadC(f.stored.identity, InstanceRevision(1L))) shouldBe empty
          f.retained shouldBe empty
          f.stored.history.flatMap(_.suspension) shouldBe empty
          if (granted) {
            take(WorkflowTerminalJsonV1.decodeC(wire, GoalPhaseStepCloseServiceOperation.terminalCodec))
            f.stored.lifecycle shouldBe Lifecycle.Completed
            f.commits.size shouldBe 1
          } else {
            take(WorkflowDecisionJsonV1.decodeC(wire, GoalPhaseClosureAuthority.inputCodec))
            f.stored.revision shouldBe InstanceRevision(1L)
            f.commits shouldBe empty
          }
          f.closeInvocations shouldBe 1
        } finally f.subsystem.shutdown()
      }
    }

    "reject incomplete integrated ports before creating any Start instance" in {
      val f = new Fixture(fromPublicStart = true, withAuthority = true)
      try {
        val invalid = Vector(f.ports.copy(closureAuthority = null), f.ports.copy(closureAuthority = Some(null)),
          f.ports.copy(closureAuthority = Some(f.closurePorts.copy(receipts = null))),
          f.ports.copy(closureAuthority = Some(f.closurePorts.copy(authority = null))),
          f.ports.copy(closureAuthority = Some(f.closurePorts.copy(authority = f.authorityPorts.copy(authorizations = null)))))
        invalid.foreach(ports => GoalPhaseFixtureServiceAssembly.bindC(ports) shouldBe a[Consequence.Failure[?]])
        Option(f.stored) shouldBe empty
        f.creates shouldBe 0
      } finally f.subsystem.shutdown()
    }

    "publish no Decision or commit when accepted-review retention fails or returns a different record" in {
      for (wrong <- Vector(false, true)) {
        val f = new Fixture(withAuthority = true)
        try {
          val work = f.firstReview()
          val wire = f.reviewWire(f.reviewResult(work, "accept"))
          f.failReceipt = !wrong
          f.wrongReceipt = wrong
          f.invoke("submitStepReview", wire) shouldBe a[Consequence.Failure[?]]
          f.stored.revision shouldBe InstanceRevision(3L)
          f.stored.lifecycle shouldBe Lifecycle.Active
          f.stored.suspension shouldBe empty
          f.authorityCalls shouldBe 0
          f.commits shouldBe empty
          f.invoke("submitStepReview", wire) shouldBe a[Consequence.Failure[?]]
          f.project(work.handle) shouldBe a[Consequence.Failure[?]]
        } finally f.subsystem.shutdown()
      }
    }

    "recover a failed Decision display from the retained proof without resubmitting Result" in {
      val f = new Fixture(withAuthority = true)
      try {
        val work = f.firstReview()
        val wire = f.reviewWire(f.reviewResult(work, "accept"))
        f.failDecision = true
        f.invoke("submitStepReview", wire) shouldBe a[Consequence.Failure[?]]
        val saved = f.saved
        val current = f.stored
        f.failDecision = false
        val projected = take(f.project(work.handle))
        take(f.project(work.handle)) shouldBe projected
        f.saved shouldBe saved
        f.invoke("submitStepReview", wire) shouldBe a[Consequence.Failure[?]]
        f.stored shouldBe current
        f.commits shouldBe empty
        f.closeInvocations shouldBe 1
      } finally f.subsystem.shutdown()
    }

    "fail closed on retained proof, instance, candidate or authority drift and never commit from projection" in {
      val f = new Fixture(withAuthority = true)
      try {
        val work = f.firstReview()
        take(f.invoke("submitStepReview", f.reviewWire(f.reviewResult(work, "accept"))))
        val saved = f.saved
        val current = f.stored
        val originalProgram = f.program
        val intent = f.captured.get
        val forged = Vector(saved.copy(reviewed = saved.reviewed.copy(correlation = CorrelationReference("foreign"))),
          saved.copy(submitted = saved.submitted.copy(resultReference = ContextReference("forged", "revision"))),
          saved.copy(submitted = saved.submitted.copy(result = saved.submitted.result.copy(value =
            saved.submitted.result.value.copy(reviewEvidence = saved.submitted.result.value.reviewEvidence.map(
              _.copy(provenance = "changed-reviewer")))))))
        forged.foreach { record =>
          f.receiptOverride = Some(record)
          f.project(work.handle) shouldBe a[Consequence.Failure[?]]
        }
        f.receiptOverride = None
        f.project(work.handle, now.plusSeconds(7200)) shouldBe a[Consequence.Failure[?]]
        f.program = "changed after acceptance".getBytes(StandardCharsets.UTF_8).toVector
        f.project(work.handle) shouldBe a[Consequence.Failure[?]]
        f.program = originalProgram
        f.stored = current.copy(causation = CausationReference("changed-instance"))
        f.project(work.handle) shouldBe a[Consequence.Failure[?]]
        f.stored = current
        f.authorization = Some(f.grant(intent).copy(expectedRevision = StateMachineRevision("2")))
        f.project(work.handle) shouldBe a[Consequence.Failure[?]]
        f.authorization = Some(f.grant(intent))
        f.project(work.handle) shouldBe a[Consequence.Failure[?]]
        f.stored shouldBe current
        f.commits shouldBe empty
      } finally f.subsystem.shutdown()
    }

    "reject receipt drift during authority lookup before either Decision or commit" in {
      for (granted <- Vector(false, true)) {
        val f = new Fixture(withAuthority = true)
        try {
          val work = f.firstReview()
          f.autoGrant = granted
          f.onAuthority = _ => f.receiptOverride = Some(f.saved.copy(
            submitted = f.saved.submitted.copy(resultReference = ContextReference("mutated", "result"))))
          f.invoke("submitStepReview", f.reviewWire(f.reviewResult(work, "accept"))) shouldBe a[Consequence.Failure[?]]
          f.stored.revision shouldBe InstanceRevision(3L)
          f.commits shouldBe empty
        } finally f.subsystem.shutdown()
      }
    }

    "retain exact receipts idempotently and forbid replacing a consumed review proof" in {
      val f = new Fixture(withAuthority = true)
      try {
        val work = f.firstReview()
        take(f.invoke("submitStepReview", f.reviewWire(f.reviewResult(work, "accept"))))
        val saved = f.saved
        take(f.receiptStore.putIfAbsentC(saved)) shouldBe saved
        f.receiptStore.putIfAbsentC(saved.copy(submitted = saved.submitted.copy(
          resultReference = ContextReference("replacement", "result")))) shouldBe a[Consequence.Failure[?]]
        f.receiptStore.putIfAbsentC(null) shouldBe a[Consequence.Failure[?]]
        take(f.receiptStore.loadC(saved.reviewed.identity, saved.reviewed.revision)) shouldBe Some(saved)
        f.receiptStore.loadC(null, saved.reviewed.revision) shouldBe a[Consequence.Failure[?]]
        f.commits shouldBe empty
      } finally f.subsystem.shutdown()
    }
  }

  "GoalPhase public Start-to-close composition" should {
    "reject incomplete host ports and incompatible generated workflow or Required SPI bindings" in {
      val f = new Fixture(fromPublicStart = true)
      try {
        GoalPhaseFixtureServiceAssembly.bindC(null) shouldBe a[Consequence.Failure[?]]
        val invalid = Vector(f.ports.copy(selection = null), f.ports.copy(invocation = null),
          f.ports.copy(instances = null), f.ports.copy(commit = null),
          f.ports.copy(definition = take(SplitPhaseDefinitionBinding.bindC)),
          f.ports.copy(review = f.review.copy(identity = StateMachineRequiredOperationIdentity("foreign-capability"))))
        invalid.foreach { ports =>
          GoalPhaseFixtureServiceAssembly.bindC(ports) shouldBe a[Consequence.Failure[?]]
        }
        f.creates shouldBe 0
        Option(f.stored) shouldBe empty
      } finally f.subsystem.shutdown()
    }

    "complete the Start-created instance through review Result via Service and CLI" in {
      for (viaCli <- Vector(false, true)) {
        val f = new Fixture(viaCli, fromPublicStart = true)
        try {
          Option(f.stored) shouldBe empty
          val started = take(f.start())
          val startHistory = f.stored.history
          take(f.start()) shouldBe started
          f.creates shouldBe 1
          f.appends shouldBe 1
          f.associateClose(started.handle)
          val work = f.firstReview()
          work.handle shouldBe started.handle
          f.order(work).expectedRevision shouldBe StateMachineRevision("2")
          f.order(work).completionOperation shouldBe StateMachineOperationIdentity("GoalPhaseService", "submitStepReview")
          f.stored.history.take(1) shouldBe startHistory
          f.stored.derivedCompositeOccurrence shouldBe empty
          f.stored.committedPredecessor shouldBe empty
          val accepted = f.reviewWire(f.reviewResult(work, "accept"))
          val terminal = take(WorkflowTerminalJsonV1.decodeC(take(f.invoke("submitStepReview", accepted)),
            GoalPhaseStepCloseServiceOperation.terminalCodec))
          terminal.handle shouldBe started.handle
          terminal.current shouldBe WorkflowContinuation.Terminal(TypedValue("StepCloseResult", "f" * 40),
            MinimalPresentation("Step Closed", "Committed reviewed Step"))
          f.stored.lifecycle shouldBe Lifecycle.Completed
          f.stored.history.map(_.revision.value) shouldBe Vector(1L, 2L, 3L, 4L)
          f.stored.history.take(1) shouldBe startHistory
          f.stored.history.map(_.correlation).distinct shouldBe Vector(startHistory.head.correlation)
          f.creates shouldBe 1
          f.commits.size shouldBe 1
          f.closeInvocations shouldBe 1
          val completed = f.stored
          f.invoke("submitStepReview", accepted) shouldBe a[Consequence.Failure[?]]
          f.start() shouldBe a[Consequence.Failure[?]]
          f.stored shouldBe completed
          f.creates shouldBe 1
          f.commits.size shouldBe 1
        } finally f.subsystem.shutdown()
      }
    }

    "keep Start provenance through repair and current-candidate re-review without another close request" in {
      for (viaCli <- Vector(false, true); changed <- Vector(false, true)) {
        val f = new Fixture(viaCli, fromPublicStart = true)
        try {
          val started = take(f.start())
          val startHistory = f.stored.history
          f.associateClose(started.handle)
          val repair = f.repairWork()
          repair.handle shouldBe started.handle
          if (changed) f.program = "public Start repaired program\n".getBytes(StandardCharsets.UTF_8).toVector
          val reReview = f.work(take(f.invoke("submitStepRepair", f.repairWire(f.repaired(repair)))))
          reReview.handle shouldBe started.handle
          f.order(reReview).input.get.value.programDigest shouldBe digest(f.program)
          val terminal = take(WorkflowTerminalJsonV1.decodeC(take(f.invoke("submitStepReview",
            f.reviewWire(f.reviewResult(reReview, "accept")))), GoalPhaseStepCloseServiceOperation.terminalCodec))
          terminal.handle shouldBe started.handle
          f.stored.lifecycle shouldBe Lifecycle.Completed
          f.stored.history.map(_.revision.value) shouldBe (1L to 8L).toVector
          f.stored.history.take(1) shouldBe startHistory
          f.creates shouldBe 1
          f.closeInvocations shouldBe 1
          f.commits.size shouldBe 1
          f.commits.head.programDigest shouldBe digest(f.program)
        } finally f.subsystem.shutdown()
      }
    }

    "reuse proactive full review from the public Start instance without a semantic WorkOrder" in {
      for (viaCli <- Vector(false, true)) {
        val f = new Fixture(viaCli, fromPublicStart = true)
        try {
          val started = take(f.start())
          f.associateClose(started.handle)
          val evidence = StepReviewEvidence("proactive-full-review", f.originalCandidate,
            "2026-09-27T23:59:00Z", "2026-09-28T01:00:00Z", "full-step", "full candidate reviewed", "fixture-reviewer")
          val request = f.request.copy(reviewEvidence = Vector(evidence))
          val terminal = take(WorkflowTerminalJsonV1.decodeC(take(f.invoke("requestStepClose",
            GoalPhaseStepCloseJson.encode(request))), GoalPhaseStepCloseServiceOperation.terminalCodec))
          terminal.handle shouldBe started.handle
          f.stored.lifecycle shouldBe Lifecycle.Completed
          f.stored.history.map(_.revision.value) shouldBe Vector(1L, 2L)
          f.stored.history.flatMap(_.suspension) shouldBe empty
          f.retained shouldBe empty
          f.creates shouldBe 1
          f.commits.size shouldBe 1
          f.commits.head.reviewEvidenceIdentity shouldBe evidence.evidenceIdentity
          f.closeInvocations shouldBe 1
        } finally f.subsystem.shutdown()
      }
    }

    "require human Start selection and a host-associated submission instead of manufacturing an instance" in {
      for (viaCli <- Vector(false, true)) {
        val f = new Fixture(viaCli, fromPublicStart = true)
        try {
          f.invoke("requestStepClose", GoalPhaseStepCloseJson.encode(f.request)) shouldBe a[Consequence.Failure[?]]
          val selection = f.selected.get
          f.selected = Some(selection.copy(origin = ProfileStartAdmission.SelectionOrigin.Workflow))
          f.start() shouldBe a[Consequence.Failure[?]]
          f.creates shouldBe 0
          Option(f.stored) shouldBe empty
          f.selected = Some(selection)
          val started = take(f.start())
          val before = f.stored
          f.invoke("requestStepClose", GoalPhaseStepCloseJson.encode(f.request)) shouldBe a[Consequence.Failure[?]]
          f.hostCloseInstance = Some(InstanceIdentity("foreign-instance"))
          f.invoke("requestStepClose", GoalPhaseStepCloseJson.encode(f.request)) shouldBe a[Consequence.Failure[?]]
          f.associateClose(started.handle)
          f.invoke("requestStepClose", GoalPhaseStepCloseJson.encode(f.request.copy(submissionIdentity = "foreign-submission"))) shouldBe
            a[Consequence.Failure[?]]
          f.start(f.startCommand.copy(input = f.startCommand.input.copy(phaseIdentity = "77.2"))) shouldBe a[Consequence.Failure[?]]
          f.stored shouldBe before
          f.commits shouldBe empty
          f.creates shouldBe 1
          f.firstReview().handle shouldBe started.handle
        } finally f.subsystem.shutdown()
      }
    }

    "reject foreign review Handles and worker-selected states without consuming the Start-bound claim" in {
      for (viaCli <- Vector(false, true)) {
        val f = new Fixture(viaCli, fromPublicStart = true)
        try {
          val started = take(f.start())
          f.associateClose(started.handle)
          val work = f.firstReview()
          val submitted = f.reviewResult(work, "accept")
          val before = f.stored
          val claim = take(f.runtime.recoverClaimC(submitted.continuationId))
          val invalid = Vector(submitted.copy(handle = submitted.handle.copy(instanceIdentity = InstanceIdentity("foreign-instance"))),
            submitted.copy(expectedRevision = StateMachineRevision("1")),
            submitted.copy(result = submitted.result.copy(value = submitted.result.value.copy(decision = "Closed"))))
          invalid.foreach { value =>
            f.invoke("submitStepReview", f.reviewWire(value)) shouldBe a[Consequence.Failure[?]]
            f.stored shouldBe before
            take(f.runtime.recoverClaimC(submitted.continuationId)) shouldBe claim
          }
          val selectedState = f.reviewWire(submitted).replace("\"decision\":", "\"nextState\":\"Closed\",\"decision\":")
          f.invoke("submitStepReview", selectedState) shouldBe a[Consequence.Failure[?]]
          f.stored shouldBe before
          take(f.runtime.recoverClaimC(submitted.continuationId)) shouldBe claim
          take(f.invoke("submitStepReview", f.reviewWire(submitted)))
          f.stored.lifecycle shouldBe Lifecycle.Completed
          f.creates shouldBe 1
          f.commits.size shouldBe 1
        } finally f.subsystem.shutdown()
      }
    }

    "recover the same initial Start and publish no review if suspension persistence fails" in {
      for (viaCli <- Vector(false, true)) {
        val f = new Fixture(viaCli, fromPublicStart = true)
        try {
          f.failAppendRevision = Some(InstanceRevision(1L))
          f.start() shouldBe a[Consequence.Failure[?]]
          f.stored.lifecycle shouldBe Lifecycle.NotStarted
          f.stored.history shouldBe empty
          f.creates shouldBe 1
          f.failAppendRevision = None
          val started = take(f.start())
          f.associateClose(started.handle)
          f.failAppendRevision = Some(InstanceRevision(2L))
          f.invoke("requestStepClose", GoalPhaseStepCloseJson.encode(f.request)) shouldBe a[Consequence.Failure[?]]
          f.stored.revision shouldBe InstanceRevision(1L)
          f.stored.suspension shouldBe empty
          val continuation = org.goldenport.cncf.workflow.ContinuationIdentity(
            s"${started.handle.instanceIdentity.value}:2:${f.request.submissionIdentity}:review")
          take(f.issued.loadC(continuation)) shouldBe empty
          f.runtime.claimC(continuation) shouldBe a[Consequence.Failure[?]]
          f.commits shouldBe empty
          f.creates shouldBe 1
        } finally f.subsystem.shutdown()
      }
    }

    "never project Completed if terminal persistence fails after the exact fixture commit" in {
      for (viaCli <- Vector(false, true)) {
        val f = new Fixture(viaCli, fromPublicStart = true)
        try {
          val started = take(f.start())
          f.associateClose(started.handle)
          val work = f.firstReview()
          f.failAppendRevision = Some(InstanceRevision(4L))
          val submitted = f.reviewWire(f.reviewResult(work, "accept"))
          f.invoke("submitStepReview", submitted) shouldBe a[Consequence.Failure[?]]
          f.stored.lifecycle shouldBe Lifecycle.Active
          f.stored.revision shouldBe InstanceRevision(3L)
          f.stored.suspension shouldBe empty
          f.commits.size shouldBe 1
          f.invoke("submitStepReview", submitted) shouldBe a[Consequence.Failure[?]]
          f.creates shouldBe 1
          f.commits.size shouldBe 1
        } finally f.subsystem.shutdown()
      }
    }

    "reject artifact drift on the public Start instance before deterministic commit" in {
      val f = new Fixture(fromPublicStart = true)
      try {
        val started = take(f.start())
        f.associateClose(started.handle)
        val work = f.firstReview()
        f.program = "unreviewed Start-bound drift\n".getBytes(StandardCharsets.UTF_8).toVector
        f.invoke("submitStepReview", f.reviewWire(f.reviewResult(work, "accept"))) shouldBe a[Consequence.Failure[?]]
        f.stored.lifecycle shouldBe Lifecycle.Active
        f.stored.revision shouldBe InstanceRevision(3L)
        f.stored.history.head.correlation.value shouldBe f.startCommand.selectionIdentity
        f.commits shouldBe empty
        f.creates shouldBe 1
      } finally f.subsystem.shutdown()
    }
  }

  "GoalPhase bounded repair loop" should {
    "complete changed and unchanged candidates through Service and CLI with one close request" in {
      for (viaCli <- Vector(false, true); changed <- Vector(false, true)) {
        val f = new Fixture(viaCli)
        try {
          val repair = f.repairWork()
          f.stored.revision shouldBe InstanceRevision(4L)
          f.order(repair).operation shouldBe StateMachineOperationIdentity("StepRepairService", "repairStep")
          f.order(repair).completionOperation shouldBe StateMachineOperationIdentity("GoalPhaseService", "submitStepRepair")
          f.commits shouldBe empty
          val submitted = f.repaired(repair)
          val before = f.stored
          val claim = take(f.runtime.recoverClaimC(submitted.continuationId))
          val selectorWire = f.repairWire(submitted).replace("\"outcome\":", "\"nextState\":\"Closed\",\"outcome\":")
          f.invoke("submitStepRepair", selectorWire) shouldBe a[Consequence.Failure[?]]
          f.stored shouldBe before
          take(f.runtime.recoverClaimC(submitted.continuationId)) shouldBe claim
          val invalid = Vector(submitted.copy(expectedRevision = StateMachineRevision("999")),
            submitted.copy(contextSnapshot = submitted.contextSnapshot.copy(modelRevision = Some("stale-model"))),
            submitted.copy(result = submitted.result.copy(value = submitted.result.value.copy(outcome = "failed"))),
            submitted.copy(result = submitted.result.copy(value = submitted.result.value.copy(evidenceIdentity = "other"))))
          invalid.foreach { value =>
            f.invoke("submitStepRepair", f.repairWire(value)) shouldBe a[Consequence.Failure[?]]
            f.stored shouldBe before
            take(f.runtime.recoverClaimC(submitted.continuationId)) shouldBe claim
          }
          if (changed) f.program = "repaired program\n".getBytes(StandardCharsets.UTF_8).toVector
          val wire = f.repairWire(submitted)
          val reReview = f.work(take(f.invoke("submitStepRepair", wire)))
          f.stored.revision shouldBe InstanceRevision(6L)
          reReview.handle shouldBe repair.handle
          val current = f.order(reReview).input.get.value
          current.submissionIdentity shouldBe f.request.submissionIdentity
          current.reviewEvidence.map(_.evidenceIdentity) shouldBe Vector("review-2")
          current.programDigest shouldBe digest(f.program)
          f.order(reReview).operation shouldBe StateMachineOperationIdentity("StepReviewService", "reviewStep")
          f.order(repair).input.get.value shouldBe f.request
          f.invoke("submitStepRepair", wire) shouldBe a[Consequence.Failure[?]]
          f.stored.revision shouldBe InstanceRevision(6L)
          if (changed) {
            val beforeReview = f.stored
            f.invoke("submitStepReview", f.reviewWire(f.reviewResult(reReview, "accept", Some(f.originalCandidate)))) shouldBe
              a[Consequence.Failure[?]]
            f.stored shouldBe beforeReview
          }
          val accept = f.reviewWire(f.reviewResult(reReview, "accept"))
          val terminal = take(f.invoke("submitStepReview", accept))
          take(WorkflowTerminalJsonV1.decodeC(terminal, GoalPhaseStepCloseServiceOperation.terminalCodec))
            .current shouldBe a[WorkflowContinuation.Terminal[?]]
          f.stored.lifecycle shouldBe Lifecycle.Completed
          f.stored.revision shouldBe InstanceRevision(8L)
          f.commits.size shouldBe 1
          f.commits.head.programDigest shouldBe digest(f.program)
          f.closeInvocations shouldBe 1
          f.invoke("submitStepReview", accept) shouldBe a[Consequence.Failure[?]]
          f.commits.size shouldBe 1
        } finally f.subsystem.shutdown()
      }
    }

    "return nonterminal WAIT when the one-repair budget is exhausted" in {
      val f = new Fixture()
      try {
        val repair = f.repairWork()
        val reReview = f.work(take(f.invoke("submitStepRepair", f.repairWire(f.repaired(repair)))))
        val response = take(f.invoke("submitStepReview", f.reviewWire(f.reviewResult(reReview, "repair"))))
        take(WorkflowWaitJsonV1.decodeC(response)).current shouldBe a[WorkflowContinuation.Wait]
        f.stored.lifecycle shouldBe Lifecycle.Active
        f.stored.revision shouldBe InstanceRevision(7L)
        f.stored.suspension shouldBe empty
        f.commits shouldBe empty
        f.closeInvocations shouldBe 1
      } finally f.subsystem.shutdown()
    }

    "issue no repair WorkOrder when intent persistence fails after review completion" in {
      val f = new Fixture(failIntent = true)
      try {
        val review = f.firstReview()
        f.invoke("submitStepReview", f.reviewWire(f.reviewResult(review, "repair"))) shouldBe a[Consequence.Failure[?]]
        f.stored.revision shouldBe InstanceRevision(3L)
        f.stored.suspension shouldBe empty
        f.runtime.claimC(org.goldenport.cncf.workflow.ContinuationIdentity("goal-loop:4:close-loop:repair")) shouldBe
          a[Consequence.Failure[?]]
        f.commits shouldBe empty
      } finally f.subsystem.shutdown()
    }

    "issue no re-review and no commit when the repaired snapshot is unavailable" in {
      val f = new Fixture()
      try {
        val repair = f.repairWork()
        f.snapshotMissing = true
        val wire = f.repairWire(f.repaired(repair))
        f.invoke("submitStepRepair", wire) shouldBe a[Consequence.Failure[?]]
        f.stored.revision shouldBe InstanceRevision(5L)
        f.stored.suspension shouldBe empty
        f.invoke("submitStepRepair", wire) shouldBe a[Consequence.Failure[?]]
        f.commits shouldBe empty
      } finally f.subsystem.shutdown()
    }

    "reject artifact drift after re-review issuance without claiming Step Closed" in {
      val f = new Fixture()
      try {
        val repair = f.repairWork()
        val reReview = f.work(take(f.invoke("submitStepRepair", f.repairWire(f.repaired(repair)))))
        f.program = "unreviewed drift".getBytes(StandardCharsets.UTF_8).toVector
        f.invoke("submitStepReview", f.reviewWire(f.reviewResult(reReview, "accept"))) shouldBe a[Consequence.Failure[?]]
        f.stored.lifecycle shouldBe Lifecycle.Active
        f.stored.revision shouldBe InstanceRevision(7L)
        f.commits shouldBe empty
      } finally f.subsystem.shutdown()
    }
  }
}
