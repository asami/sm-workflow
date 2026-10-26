package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseDependencyInventoryContinuation as I
import GoalPhaseDependencyScope as D
import GoalPhaseDependencyValidation as V
import GoalPhaseDependencyValidation.given
import GoalPhaseNativeWork as W
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Specification-owned native-shaped data exercises proof, never genuine Human
  * consent, installed execution, persistence effects or independent acceptance.
  * @since   Oct. 26, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseDependencyInventoryContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _fixture = DependencyValidationSpecificationFixture
  private val _boundary = D.AuthorizationBoundary(D.authorizationSchema, ContextReference("purpose:linked-contract", "1"),
    Vector("edit-source", "validate"), Vector("src/Forbidden.scala"))
  private val _proposal = _fixture.approvals.head.proposal.copy(authorizationBoundary = Some(_boundary))
  private val _approval = _fixture.approvals.head.copy(proposal = _proposal)
  private val _updated = _proposal.copy(identity = "current-inventory", revision = "2", reason = "Same selected contract needs a renamed implementation",
    paths = Vector("src/main/scala/Renamed.scala", "src/test/scala/RenamedSpec.scala"),
    scopeUpdate = Some(D.ScopeUpdate(_approval.decision, "These exact files implement and verify the unchanged acceptance")))
  private val _original = W.Result("decision-needed", "SPECIFICATION-OWNED original grant question", Vector.empty,
    dependencyProposal = Some(_proposal))
  private val _context = W.DecisionContext(_original, "ApproveDependencyScope", "SPECIFICATION-OWNED original answer", Some(_approval.decision))
  private def _append(from: InstanceRecord, stage: String, cause: String): InstanceRecord =
    take(from.appendC(from.revision, GoalPhaseNativeRun.history(from, "phase." + stage, cause)))
  private final case class Native(from: InstanceRecord, controller: O.State, input: W.Request,
    result: ContinuationResult[W.Result]) {
    def selectC = I.selectC(from, controller, Some(input -> result), Vector(_approval), Vector(_context))
  }
  private def _native(repair: Boolean = false): Native = {
    val initial = take(InstanceRecord(InstanceIdentity("specification-inventory-session"),
      take(GoalPhaseRuntimeCatalog.loadC()).definition, initialRevision, Lifecycle.NotStarted, None,
      CorrelationReference("specification-inventory-correlation"), CausationReference("specification-start"),
      None, None, None, Vector.empty).validateC)
    val started = take(O.startC(_fixture.plan, _fixture.primaryBase))
    val controller = if (!repair) started else {
      val implemented = take(started.acceptC(O.SemanticCompleted(started.next.asInstanceOf[O.Semantic].key, "Implemented fixture")))
      val candidate = O.Snapshot(_fixture.primaryBase.head, "e" * 40)
      val observed = take(implemented.acceptC(O.CandidateObserved(candidate)))
      take(observed.acceptC(O.RepairableChecksObserved(candidate, _fixture.plan.steps.head.checks, "specification-failed-check",
        O.RepairDirective("Correct selected failed check", Vector("Correct selected fixture source")))))
    }
    val stage = if (repair) "Repair" else "Implementation"
    val worksource = _append(initial, stage, "specification-actual-shaped-assignment")
    val checkfailure = if (!repair) None else {
      val check = GoalPhaseNativeValidation.Check("primary-test", 1, WorkflowSbtValidation.Kind.Test)
      val receipt = RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(1),
        "SPECIFICATION-OWNED selected primary test failure", "",
        Some(WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Test, WorkflowSbtValidation.Outcome.Failed, None, None)))
      val validation = GoalPhaseNativeValidation.Payload(worksource.revision.value.toString, "step-1",
        O.Snapshot(_fixture.primaryBase.head, "e" * 40), Vector(check), Vector(receipt), Some("repair-required"))
      Some(take(W.checkFailureContextC(_fixture.plan.steps.head.ownedPaths,
        "SPECIFICATION-OWNED complete selected primary source diff\n-old fixture source\n+corrected fixture source\n", validation)))
    }
    val input = _fixture.request.copy(instanceIdentity = worksource.identity.value,
      sourceRevision = worksource.revision.value.toString, generation = controller.generation,
      kind = if (repair) "RepairStep" else "ImplementStep", repairDirective = controller.repairDirective,
      decisionContext = Some(_context), checkFailure = checkfailure)
    val identity = org.goldenport.cncf.workflow.ContinuationIdentity("specification-inventory-slot")
    val boundary = SuspensionBoundary(WorkflowInstancePersistence.ContinuationIdentity(identity.value), worksource.revision.next,
      ContextSnapshotReference(worksource.definition.workflowRevision.value), CompletionReference("goal-phase-ordered-implementation-completion-v1"),
      EvidenceReference("goal-phase-ordered-implementation-evidence-v1"), worksource.correlation)
    val issuedentry = GoalPhaseNativeRun.history(worksource, "phase." + stage, "specification-issued").copy(suspension = Some(boundary))
    val pending = take(worksource.appendC(worksource.revision, issuedentry))
    val reference = ContextReference("specification-inventory-result", "1")
    val report = W.Result("decision-needed", "SPECIFICATION-OWNED inventory update", Vector.empty,
      dependencyProposal = Some(if (repair) _updated.copy(discovery = "REPAIR") else _updated))
    val from = _append(pending, "Decision", reference.identity)
    val result = ContinuationResult(WorkflowHandle(SmWorkflowComponent.componentId, from.definition.workflowIdentity,
      from.definition.workflowRevision, from.identity), StateMachineRunIdentity(from.identity.value), identity,
      StateMachineRevision(pending.revision.value.toString), ContextSnapshot(from.definition.workflowRevision.value),
      TypedValue(W.resultCodec.typeIdentity, report), reference, Vector.empty,
      ExecutionEvidence(Vector(reference), Some("specification-owned-with-no-authority")))
    Native(from, take(controller.acceptC(O.NativeFailure(report.summary))), input, result)
  }

  "PA-INVENTORY-CARRY-01 stable purpose comparison" should {
    "carry new and renamed inventory under its actual answer while sending each changed authority field to Human" in {
      Given("a typed specification grant and necessary renamed implementation and test inventory")
      val changed = Vector(
        _updated.copy(requester = _updated.requester.copy(identity = "different-requester")),
        _updated.copy(dependency = _updated.dependency.copy(workspace = "other-source")),
        _updated.copy(usageContract = ContextReference("other-contract", "1")),
        _updated.copy(acceptance = Vector("Different acceptance")),
        _updated.copy(dependentWork = Vector(ContextReference("other-work", "1"))),
        _updated.copy(authorizationBoundary = Some(_boundary.copy(purpose = ContextReference("other-purpose", "1")))),
        _updated.copy(authorizationBoundary = Some(_boundary.copy(permittedOperations = _boundary.permittedOperations :+ "push"))),
        _updated.copy(authorizationBoundary = Some(_boundary.copy(excludedPaths = Vector.empty))),
        _updated.copy(authorizationBoundary = Some(_boundary.copy(exactPaths = Some(_updated.paths)))))
      When("the pure comparison checks complete stable contracts and the legacy absence case")
      val same = take(I.assessC(_approval.step, _updated, Vector(_approval)))
      val different = changed.map(value => take(I.assessC(_approval.step, value, Vector(_approval))))
      val legacy = take(I.assessC(_approval.step, _updated, Vector(_approval.copy(proposal = _proposal.copy(authorizationBoundary = None)))))
      Then("only inventory metadata inherits the actual answer and every material mismatch names its fields")
      same shouldBe I.WithinBoundary(_approval)
      different.foreach {
        case I.NeedsHuman(reason) =>
          reason should include("Inventory boundary mismatch:")
          reason should include("requestingResult.dependencyProposal.reason")
          reason should include("scopeUpdate.rationale")
        case other => fail("Expected a precise Human boundary mismatch: " + other)
      }
      legacy.asInstanceOf[I.NeedsHuman].reason should include("legacyAuthorizationBoundary")
      Vector(Vector.empty[V.Approval], Vector(_approval, _approval), Vector(_approval.copy(step = "crossed-step")),
        Vector(_approval.copy(decision = ContextReference("foreign-answer", "1"))))
        .foreach(values => I.assessC(_approval.step, _updated, values).isSuccess shouldBe false)
    }

    "refuse stale assignment links, rejection and missing direction while matching the exact effective inventory receipt" in {
      Given("one current issued Request and a separately retained original answer")
      val request = _fixture.request.copy(decisionContext = Some(_context))
      val receipt = I.InventoryContext(ContextReference("session:ordered-dependency-inventory:20", "1"), _approval.decision, _updated)
      val current = _context.copy(inventoryContinuation = Some(receipt))
      val projected = _approval.copy(proposal = _updated)
      val next = _updated.copy(revision = "3", paths = Vector("src/Next.scala"))
      When("native assignment admission compares the actual issued context and reconstructed current projection")
      val accepted = I.assessAssignmentC(request, _updated, Vector(_approval), Vector(_context))
      val continued = I.assessAssignmentC(request.copy(decisionContext = Some(current)), next, Vector(projected), Vector(current))
      val rejected = Vector(request.copy(decisionContext = None),
        request.copy(decisionContext = Some(_context.copy(choice = "RejectDependencyScope"))),
        request.copy(decisionContext = Some(_context.copy(decisionReference = Some(ContextReference("older-answer", "1"))))))
        .map(input => I.assessAssignmentC(input, _updated, Vector(_approval), Vector(_context))) ++ Vector(
          I.assessAssignmentC(request.copy(decisionContext = Some(current)), next, Vector(projected), Vector(_context)),
          I.assessAssignmentC(request.copy(decisionContext = Some(current)), next, Vector(_approval), Vector(current)))
      Then("only the source-bound original or exact authenticated current receipt can carry the grant")
      accepted.isSuccess shouldBe true
      continued.isSuccess shouldBe true
      rejected.foreach(_.isSuccess shouldBe false)
    }
  }

  "Native inventory proof and codec" should {
    "E67 retain a partial-chain accepted prefix in the existing inventory continuation receipt" in {
      Given("docs/spec/goal-phase-closure-recovery.md R8 E67: an exact inventory direction and immutable accepted P/C/D prefix")
      val source = take(_native().selectC).get
      val original = I.payload(source)
      val partial = O.Commit("9" * 40, original.base.head, "d" * 40)
      val completion = O.Commit("e" * 40, partial.identity, "f" * 40)
      val chain = O.StepCommitChain(original.base, partial, completion)
      val step = O.ClosedStep("prior-step", completion, Some("accepted-review"), partialCompletion = Some(chain))
      val payload = original.copy(completed = Vector(step))
      When("the existing immutable receipt is encoded and decoded without a live observation")
      val decoded = I.codec.decode(I.codec.encode(payload))
      Then("native source generation frontier approval and consumed counts remain exact with the completed chain")
      decoded shouldBe Right(payload)
      decoded.toOption.get.completed.head.partialCompletion shouldBe Some(chain)
      decoded.toOption.get.completed.head.commit.parent shouldBe partial.identity
      decoded.toOption.get.sourceRevision shouldBe original.sourceRevision
      decoded.toOption.get.generation shouldBe original.generation
      decoded.toOption.get.repairProgress shouldBe original.repairProgress
      val forged = payload.copy(completed = Vector(step.copy(partialCompletion = Some(chain.copy(completion = partial)))))
      I.codec.decode(I.codec.encode(forged)).isLeft shouldBe true
    }

    "bind a complete source and one adjacent successor while preserving Plan, base, prefix and counters" in {
      Given("specification-owned complete adjacent work issuance and decision-needed completion")
      val native = _native()
      val source = take(native.selectC).get
      val payload = I.payload(source)
      When("the pure native admission checks its exact payload and canonical successor")
      val state = take(I.admittedStateC(source, payload))
      val next = _append(source.from, state.stage.toString, I.causation)
      val successor = I.successorC(source.from, next, state.stage)
      Then("the original grant and immutable context remain, and ordinary resumption changes no repair count")
      I.codec.decode(I.codec.encode(payload)) shouldBe Right(payload)
      state.stage shouldBe O.Stage.Implementation
      state.plan shouldBe native.controller.plan
      state.base shouldBe native.controller.base
      state.completed shouldBe native.controller.completed
      GoalPhaseRepairProgress.snapshot(state) shouldBe GoalPhaseRepairProgress.snapshot(native.controller)
      state.generation shouldBe native.controller.generation + 1
      payload.approval shouldBe _approval
      payload.originalResult shouldBe _original
      payload.proposed shouldBe _updated
      successor.isSuccess shouldBe true
      I.successorC(source.from, _append(next, state.stage.toString, I.causation), state.stage).isSuccess shouldBe false
      I.successorC(source.from, next.copy(identity = InstanceIdentity("crossed-session")), state.stage).isSuccess shouldBe false
      I.admittedStateC(source, payload.copy(step = "crossed-step")).isSuccess shouldBe false
      I.admittedStateC(source, payload.copy(sourceRevision = "1")).isSuccess shouldBe false
      I.admittedStateC(source, payload.copy(approval = _approval.copy(decision = ContextReference("forged-answer", "1")))).isSuccess shouldBe false
    }

    "reject crossed native sources, wrong kinds, unknown fields, nulls and changed public context" in {
      Given("one exact source and its owning closed receipt and Request context codecs")
      val native = _native()
      val source = take(native.selectC).get
      val wire = I.codec.encode(I.payload(source))
      val payload = wire.hcursor.downField("payload").focus.get
      val invalidwire = Vector(wire.mapObject(_.add("unknown", Json.True)),
        wire.mapObject(_.add("payload", Json.Null)),
        wire.mapObject(_.add("payload", payload.mapObject(_.add("unknown", Json.True)))),
        wire.mapObject(_.add("payload", payload.mapObject(_.add("approval", Json.Null)))))
      val inventory = I.InventoryContext(ContextReference(source.reference.identity, "1"), _approval.decision, _updated)
      val context = _context.copy(inventoryContinuation = Some(inventory))
      val request = native.input.copy(decisionContext = Some(context))
      val requestwire = W.inputCodec.encode(request)
      val badcontext = requestwire.mapObject(_.add("payload", requestwire.hcursor.downField("payload").focus.get.mapObject(fields =>
        fields.add("decisionContext", fields("decisionContext").get.mapObject(_.add("unknown", Json.True))))))
      When("proof consumes crossed source requests and codecs consume malformed retained payloads")
      val wrong = Vector(native.input.copy(instanceIdentity = "foreign-session"), native.input.copy(stepIdentity = Some("crossed-step")),
        native.input.copy(kind = "ReviewStep"), native.input.copy(sourceRevision = native.from.revision.value.toString))
        .map(input => I.selectC(native.from, native.controller, Some(input -> native.result), Vector(_approval), Vector(_context)))
      Then("no crossed provenance or unknown/null context is accepted as an inventory receipt")
      wrong.foreach(_.isSuccess shouldBe false)
      invalidwire.foreach(value => I.codec.decode(value).isLeft shouldBe true)
      W.inputCodec.decode(requestwire) shouldBe Right(request)
      W.inputCodec.decode(badcontext).isLeft shouldBe true
      inventory.asJson.as[I.InventoryContext] shouldBe Right(inventory)
      inventory.asJson.mapObject(_.add("approval", Json.Null)).as[I.InventoryContext].isLeft shouldBe true
      inventory.asJson.mapObject(_.add("unknown", Json.True)).as[I.InventoryContext].isLeft shouldBe true
      I.selectC(native.from, native.controller, Some(native.input -> native.result.copy(
        result = TypedValue(W.resultCodec.typeIdentity, native.result.result.value.copy(dependencyProposal = Some(_proposal))))),
        Vector(_approval), Vector(_context)).toOption shouldBe Some(None)
    }

    "resume the same Repair Step without reserving another batch or changing its original directive" in {
      Given("a specification-owned Step repair already reserved after its selected failed check")
      val native = _native(repair = true)
      val source = take(native.selectC).get
      When("the inventory receipt admits ordinary resumption for this current repair assignment")
      val state = take(I.admittedStateC(source, I.payload(source)))
      Then("Repair remains the same Step, retained original grant and unchanged active and cumulative repair evidence")
      state.stage shouldBe O.Stage.Repair
      state.cursor shouldBe native.controller.cursor
      state.repairDirective shouldBe native.controller.repairDirective
      state.repairs shouldBe native.controller.repairs
      state.ordinaryRepairs shouldBe native.controller.ordinaryRepairs
      state.plan shouldBe native.controller.plan
      state.completed shouldBe native.controller.completed
      I.successorC(source.from, _append(source.from, "Repair", I.causation), O.Stage.Repair).isSuccess shouldBe true
    }
  }

  "Current dependency validation projection" should {
    "consume the new exact registered Binding and preserve the prior Admission bytes" in {
      Given("an immutable prior Admission and a runtime-projected current inventory under the same actual answer")
      val oldbinding = _fixture.binding.copy(paths = _proposal.paths)
      val old = V.Admission(oldbinding, Vector(_approval))
      val oldwire = old.asJson
      val projected = _approval.copy(proposal = _updated)
      val currentbinding = oldbinding.copy(revision = 2, paths = _updated.paths)
      val context = _context.copy(inventoryContinuation = Some(I.InventoryContext(
        ContextReference("session:ordered-dependency-inventory:20", "1"), _approval.decision, _updated)))
      val request = _fixture.request.copy(decisionContext = Some(context))
      val selection = V.Selection(ContextReference(currentbinding.identity, "2"), Vector(V.ApprovalReference(
        ContextReference(_updated.identity, _updated.revision), _approval.decision)))
      When("unchanged dependency validation consumes the exact current binding and projected grant")
      val configured = take(I.bindingC(request, Some(currentbinding), Some(old))).get
      val current = take(V.admitC(selection, configured, Vector(projected), request))
      val stale = V.admitC(selection, oldbinding, Vector(projected), _fixture.request)
      Then("current paths are exact, the original answer remains, and the old Admission stays unchanged")
      current.binding.paths shouldBe _updated.paths
      current.approvals shouldBe Vector(projected)
      current.approvals.head.decision shouldBe old.approvals.head.decision
      old shouldBe V.Admission(oldbinding, Vector(_approval))
      old.asJson shouldBe oldwire
      stale.isSuccess shouldBe false
      I.bindingC(request, None, Some(old)).isSuccess shouldBe false
      Vector(currentbinding.copy(identity = "different-binding"), currentbinding.copy(root = "/other/checkout"),
        currentbinding.copy(branch = "different-branch"), currentbinding.copy(base = O.Snapshot("e" * 40, "f" * 40)),
        currentbinding.copy(baseAdoption = "A different source adoption"), currentbinding.copy(step = "different-step"),
        currentbinding.copy(checks = currentbinding.checks.map(_.copy(identity = "different-check"))))
        .foreach(binding => I.bindingC(request, Some(binding), Some(old)).isSuccess shouldBe false)
    }
  }
}
