package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, Printer}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseCanonicalDependencySourceSuccession as C
import GoalPhaseDependencySourceRevision as R
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** All observations and Human references here are specification-only data.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  */
private[admission] object CanonicalDependencySourceSpecificationFixture {
  import DependencyValidationSpecificationFixture as D
  import GoalPhaseDependencyValidation as V
  val oldplan = D.plan.copy(steps = D.plan.steps.head.copy(identity = "step-0") +: D.plan.steps)
  val prefixinitial = take(O.startC(oldplan, D.primaryBase, Some(GoalPhaseRuntimeRepairControl.version)))
  val prefiximplemented = take(prefixinitial.acceptC(O.SemanticCompleted(prefixinitial.next.asInstanceOf[O.Semantic].key,
    "SPECIFICATION-ONLY first accepted result")))
  val prefixcandidate = O.Snapshot(D.primaryBase.head, "9" * 40)
  val prefixinspected = take(prefiximplemented.acceptC(O.CandidateObserved(prefixcandidate)))
  val prefixchecked = take(prefixinspected.acceptC(O.ChecksObserved(prefixcandidate, Vector("primary-test"), "prefix-checks", true)))
  val prefixreviewed = take(prefixchecked.acceptC(O.ReviewAdmitted(prefixchecked.next.asInstanceOf[O.Semantic].key,
    prefixcandidate, "prefix-checks", "prefix-independent-review", O.ReviewOutcome.Accept)))
  val prefixintent = take(prefixreviewed.acceptC(O.ClosureIntentSaved(prefixcandidate, "prefix-closure")))
  val originalstate = take(prefixintent.acceptC(O.StepCommitObserved("prefix-closure",
    O.Commit("8" * 40, D.primaryBase.head, prefixcandidate.tree))))
  val prefixapprovals = D.paths.zipWithIndex.map { (path, index) =>
    val proposal = GoalPhaseDependencyScope.Proposal("prefix-unit-" + index, "1", "IMPLEMENTATION", D.requester, D.dependency,
      ContextReference("linked-api", "1"), "SPECIFICATION-ONLY accepted prefix", Vector(path),
      Vector("Preserve existing callers"), Vector(ContextReference("step-0", "1")), Vector.empty)
    V.Approval("step-0", proposal, ContextReference("prefix-answer-" + index, "1"))
  }
  val prefixadmission = V.Admission(D.binding.copy(identity = "prefix-cncf-validation", step = "step-0"), prefixapprovals)
  val prefixcapture = D.capture.copy(plan = ContextReference(oldplan.identity, oldplan.revision), step = "step-0",
    sourceRevision = "8", admission = prefixadmission)
  val acceptedprefix = V.AcceptedPrefix(V.acceptedPrefixSchema, "same-session", ContextReference(oldplan.identity, oldplan.revision),
    "step-0", "10", prefixcapture.candidate, D.paths)
  val predecessor = D.admission.copy(acceptedPrefix = Some(acceptedprefix))
  val original = {
    val previous = DependencySourceRevisionSpecificationFixture.direction
    val proposal = previous.target.proposal.copy(coverage = (D.approvals ++ prefixapprovals).map(a =>
      R.Coverage(a.decision, a.proposal.acceptance, previous.target.binding.checks.map(_.identity))))
    previous.copy(target = previous.target.copy(plan = oldplan, completed = originalstate.completed,
      generation = originalstate.generation, predecessor = predecessor,
      repairProgress = GoalPhaseRepairProgress.snapshot(originalstate),
      ancestors = Vector(R.Ancestor(prefixcapture, "10")), proposal = proposal))
  }
  val plan = original.target.plan.copy(revision = "2", steps = original.target.plan.steps.updated(1,
    original.target.plan.steps(1).copy(ownedPaths = original.target.plan.steps(1).ownedPaths :+ "added-current-step.scala")))
  val inventorydecision = take(originalstate.acceptC(O.NativeFailure("SPECIFICATION-ONLY current-Step inventory question")))
  val started = take(inventorydecision.adoptCurrentInventoryRevisionC(plan, "specification-current-inventory-human"))
  val inspected = take(take(started.acceptC(O.SemanticCompleted(started.next.asInstanceOf[O.Semantic].key,
    "SPECIFICATION-ONLY semantic result"))).acceptC(O.CandidateObserved(O.Snapshot(started.tipSnapshot.head, "6" * 40))))
  val controller = take(inspected.acceptC(O.RepairableChecksObserved(inspected.candidate.get,
    Vector("primary-test"), "specification-prior-failure", O.RepairDirective("specification repair", Vector("repair input")))))
  val binding = original.target.binding.copy(revision = 3, root = "/specification/canonical-cncf", branch = "canonical",
    base = O.Snapshot("4" * 40, "5" * 40), dependency = original.target.binding.dependency.copy(workspace = "cncf-canonical-source"))
  val consumption = R.Consumption(D.requester.workspace, binding.root, original.target.proposal.excludedPaths,
    binding.paths.map(R.ExpectedMode(_, "100644")), Vector(
      R.CheckProgram("primary-test", D.requester.workspace, "test", "specification-canonical-primary"),
      R.CheckProgram("cncf-compile", binding.dependency.workspace, "compile", "specification-canonical-dependency")))
  val source = original.target.proposal.copy(schemaVersion = R.sourceSchema,
    binding = ContextReference(binding.identity, binding.revision.toString), consumption = consumption)
  val guard = R.Guard(binding.root, binding.branch, binding.base.head, binding.base.tree, binding.base.tree,
    Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty,
    Some(R.CommittedInventory(binding.paths.map(p => R.CommittedEntry(p, Some(R.CommittedObject("100644", "7" * 40)))),
      source.excludedPaths.map(R.CommittedEntry(_, None)))))
  val publisherWorkspace = "specification-producer-primary"
  val publication = {
    val original = PublishedDependencySourceSpecificationFixture.observation
    val old = original.result.publication
    val target = old.request.target.copy(identity = "specification-primary-publication", component = binding.dependency.identity,
      workspace = publisherWorkspace, dependency = None)
    val request = old.request.copy(target = target, input = old.request.input.copy(targetIdentity = target.identity),
      source = LocalPublication.Source(binding.base.head, binding.base.tree, Vector.empty))
    val policy = Json.obj("processPlan" -> Json.obj("sbtPrograms" -> Json.arr(Json.obj(
      "target" -> publisherWorkspace.asJson, "operation" -> "publish-local".asJson,
      "identity" -> "specification-dependency-publication".asJson)), "workspaces" -> Json.arr(Json.obj(
      "identity" -> publisherWorkspace.asJson, "root" -> binding.root.asJson))))
    original.copy(result = original.result.copy(publication = old.copy(request = request)),
      publisherProcessPolicy = Printer.noSpaces.copy(sortKeys = true).print(policy))
  }
  val proposal = C.Proposal(C.proposalSchema, source, publication, ContextReference("specification-adopted-delivery", "1"))
  val primaryRoot = "/specification/workspaces/primary"
  val processPolicy = {
    val routes = consumption.programs
    Json.obj("validation" -> GoalPhaseNativeValidation.Policy("specification-primary", 1,
      Vector(GoalPhaseNativeValidation.Check("primary-test", 1, WorkflowSbtValidation.Kind.Test))).asJson,
      "processPlan" -> Json.obj("sbtPrograms" -> routes.map(p => Json.obj("identity" -> p.program.asJson,
        "target" -> p.workspace.asJson, "operation" -> p.kind.asJson, "capability" -> p.program.asJson)).asJson,
        "workspaces" -> routes.map(p => Json.obj("identity" -> p.workspace.asJson,
          "root" -> (if (p.workspace == binding.dependency.workspace) binding.root else primaryRoot).asJson)).asJson,
        "workspaceGrants" -> routes.map(p => Json.obj("componentId" -> org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name.asJson,
          "workspaceId" -> p.workspace.asJson, "access" -> "manage".asJson)).asJson,
        "grants" -> Json.arr(Json.obj("componentId" -> org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name.asJson,
          "capabilities" -> routes.map(_.program).asJson)))).noSpaces
  }
  val origin = C.Origin("specification-component", "participant", GoalPhaseCandidateRecovery.controllerSnapshot(controller),
    D.approvals ++ prefixapprovals, Some(original), None, None, proposal, primaryRoot, processPolicy,
    Vector(O.HistoryCommit(binding.base.head, binding.base.tree, Vector(original.target.binding.base.head)),
      O.HistoryCommit(original.target.binding.base.head, original.target.binding.base.tree, Vector.empty)))
  val target = original.target.copy(schemaVersion = R.canonicalSchema, questionIdentity = "", sourceRevision = "30",
    pendingRevision = "30", plan = plan, generation = controller.generation,
    repairProgress = GoalPhaseRepairProgress.snapshot(controller), proposal = source, binding = binding,
    preStageGuard = guard, canonicalOrigin = Some(origin))
  val direction = R.Direction(target, ContextReference("specification-canonical-human", "1"), "participant",
    "SPECIFICATION-ONLY independently selected canonical source", Some(C.choice))
  val capture = R.Capture(R.canonicalCaptureSchema, target.sessionId, ContextReference(plan.identity, plan.revision),
    target.step, controller.generation + 1, "34", D.primaryBase, direction, guard, Vector.empty, guard)
}

/** Pure fixtures describe classified native-shaped failures, never authority. */
private[admission] object LaterSourcePublicationSpecificationFixture {
  import CanonicalDependencySourceSpecificationFixture as F
  import PrimaryPublicationRepairSpecificationFixture as P
  import GoalPhasePrerequisitePrimaryRepair as A
  import GoalPhasePrerequisitePublicationRecovery as B
  val plan = F.plan.copy(steps = F.plan.steps :+ F.plan.steps.last.copy(identity = "step-2",
    purpose = "Consume the later dependency API", checks = Vector("primary-later")))
  val initial = take(O.startC(plan, F.target.primaryBase, Some(GoalPhaseRuntimeRepairControl.version)))
  val prefiximplemented = take(initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key,
    "SPECIFICATION-ONLY first accepted result")))
  val prefixinspected = take(prefiximplemented.acceptC(O.CandidateObserved(F.prefixcandidate)))
  val prefixchecked = take(prefixinspected.acceptC(O.ChecksObserved(F.prefixcandidate, Vector("primary-test"), "prefix-checks", true)))
  val prefixreviewed = take(prefixchecked.acceptC(O.ReviewAdmitted(prefixchecked.next.asInstanceOf[O.Semantic].key,
    F.prefixcandidate, "prefix-checks", "prefix-independent-review", O.ReviewOutcome.Accept)))
  val prefixintent = take(prefixreviewed.acceptC(O.ClosureIntentSaved(F.prefixcandidate, "prefix-closure")))
  val prefixclosed = take(prefixintent.acceptC(O.StepCommitObserved("prefix-closure",
    O.Commit("8" * 40, F.target.primaryBase.head, F.prefixcandidate.tree))))
  val oldimplemented = take(prefixclosed.acceptC(O.SemanticCompleted(prefixclosed.next.asInstanceOf[O.Semantic].key,
    "SPECIFICATION-ONLY earlier implementation")))
  val oldinspected = take(oldimplemented.acceptC(O.CandidateObserved(F.inspected.candidate.get)))
  val oldcontroller = take(oldinspected.acceptC(O.RepairableChecksObserved(oldinspected.candidate.get,
    Vector("primary-test"), "specification-prior-failure", F.controller.repairDirective.get)))
  val olddirection = F.direction.copy(target = F.target.copy(plan = plan, generation = oldcontroller.generation,
    completed = oldcontroller.completed, repairProgress = GoalPhaseRepairProgress.snapshot(oldcontroller),
    canonicalOrigin = Some(F.origin.copy(controller = GoalPhaseCandidateRecovery.controllerSnapshot(oldcontroller)))))
  val oldcapture = F.capture.copy(direction = olddirection, generation = oldcontroller.generation + 1)
  val selected = take(oldcontroller.adoptCanonicalDependencySourceC(olddirection.directive))
  val implemented = take(selected.acceptC(O.SemanticCompleted(selected.next.asInstanceOf[O.Semantic].key,
    "SPECIFICATION-ONLY earlier source implementation")))
  val inspected = take(implemented.acceptC(O.CandidateObserved(O.Snapshot(selected.tipSnapshot.head, "6" * 40))))
  val checked = take(inspected.acceptC(O.ChecksObserved(inspected.candidate.get, Vector("primary-test"), "old-source-checks", true)))
  val reviewed = take(checked.acceptC(O.ReviewAdmitted(checked.next.asInstanceOf[O.Semantic].key,
    checked.candidate.get, "old-source-checks", "independent-old-source-review", O.ReviewOutcome.Accept)))
  val intent = take(reviewed.acceptC(O.ClosureIntentSaved(reviewed.candidate.get, "old-source-closure")))
  val later = take(intent.acceptC(O.StepCommitObserved("old-source-closure",
    O.Commit("1" * 40, intent.candidate.get.head, intent.candidate.get.tree))))
  val validation = take(take(later.acceptC(O.SemanticCompleted(later.next.asInstanceOf[O.Semantic].key,
    "SPECIFICATION-ONLY accepted later implementation"))).acceptC(O.CandidateObserved(O.Snapshot(later.tipSnapshot.head, "2" * 40))))
  val failedcandidate = GoalPhaseNativeCandidate.Payload("60", "step-2", Vector("screen.scala"), Vector("screen.scala"),
    validation.candidate, Vector("screen.scala"), Some("diff --git a/screen.scala b/screen.scala\n+missingApi\n"))
  val primary = P.target.copy(component = F.binding.requester.identity, workspace = F.binding.requester.workspace)
  val processPolicy = {
    val original = io.circe.parser.parse(F.processPolicy).toOption.get
    val process = original.hcursor.get[Json]("processPlan").toOption.get
    val programs = process.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get
    val laterprogram = programs.head.mapObject(_.add("identity", "specification-later-primary".asJson)
      .add("capability", "specification-later-primary".asJson).add("target", "specification-later-workspace".asJson))
    val publisher = Json.obj("identity" -> P.program.asJson, "target" -> primary.workspace.asJson,
      "operation" -> "publish-local".asJson)
    val grants = process.hcursor.get[Vector[Json]]("grants").toOption.get.map(_.mapObject(fields =>
      fields.add("capabilities", (fields("capabilities").get.as[Vector[String]].toOption.get :+
        "specification-later-primary").asJson)))
    val workspaces = process.hcursor.get[Vector[Json]]("workspaces").toOption.get :+
      Json.obj("identity" -> "specification-later-workspace".asJson, "root" -> F.primaryRoot.asJson)
    val workspacegrants = process.hcursor.get[Vector[Json]]("workspaceGrants").toOption.get :+
      Json.obj("componentId" -> org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name.asJson,
        "workspaceId" -> "specification-later-workspace".asJson, "access" -> "manage".asJson)
    val validation = original.hcursor.get[GoalPhaseNativeValidation.Policy]("validation").toOption.get
    P.sorted(original.mapObject(_.add("validation", validation.copy(checks = validation.checks :+
      GoalPhaseNativeValidation.Check("primary-later", 2, WorkflowSbtValidation.Kind.Test)).asJson)
      .add("processPlan", process.mapObject(_.add("sbtPrograms", (programs ++ Vector(laterprogram, publisher)).asJson)
        .add("grants", grants.asJson).add("workspaces", workspaces.asJson).add("workspaceGrants", workspacegrants.asJson)))))
  }
  val failed = {
    val input = LocalPublicationRuntime.prerequisiteInput("same-session", failedcandidate, primary)
    LocalPublication.Result("failed", "Specification terminated real Compile classification",
      LocalPublication.Request(input.copy(requestIdentity = input.requestIdentity + ":prerequisite:0"), primary,
        LocalPublication.Source(validation.candidate.get.head, validation.candidate.get.tree, Vector.empty),
        "participant", "SPECIFICATION-ONLY managed failure", None, Vector.empty), Some(P.process()))
  }
  val failure = A.Failure(F.origin.componentInstance, "participant", "same-session", "62", P.sorted(B.controller(validation)),
    plan, validation.base, validation.generation, validation.completed, GoalPhaseRepairProgress.snapshot(validation),
    "62", failedcandidate, ContextReference("later-implementation", "1"), Vector(primary),
    P.publicationPolicy(Vector(primary)), processPolicy, failed, None, Vector.empty, Vector.empty)
  val repair = take(A.stateC(validation, failure))
  val fact = A.Fact(A.schema, failure, A.directive(failure), P.sorted(B.controller(repair)))
  val saved = A.Saved(P.nativeRecord(63), fact)
  val historical = C.HistoricalPublicationFailure(saved.reference, fact)
  val prefix = C.AcceptedSourcePrefix(ContextReference("same-session", oldcapture.sourceRevision),
    ContextReference("same-session", "50"), oldcapture.step, olddirection.directive, None)
  val binding = F.binding.copy(revision = 4, step = "step-2", base = O.Snapshot("3" * 40, "4" * 40))
  val consumption = F.consumption.copy(programs = F.consumption.programs.updated(0,
    F.consumption.programs.head.copy(check = "primary-later", program = "specification-later-primary", workspace = "specification-later-workspace")))
  val publication = {
    val old = F.publication.result.publication
    F.publication.copy(result = F.publication.result.copy(publication = old.copy(request = old.request.copy(
      source = LocalPublication.Source(binding.base.head, binding.base.tree, Vector.empty)))))
  }
  val proposal = F.proposal.copy(source = F.source.copy(binding = ContextReference(binding.identity, "4"), consumption = consumption),
    publication = publication)
  val guard = F.guard.copy(head = binding.base.head, committedTree = binding.base.tree, indexTree = binding.base.tree)
  val origin = F.origin.copy(controller = GoalPhaseCandidateRecovery.controllerSnapshot(repair), originalDirection = Some(olddirection),
    selection = proposal, processPolicy = processPolicy, baseLineage = Vector(
      O.HistoryCommit(binding.base.head, binding.base.tree, Vector(F.binding.base.head)),
      O.HistoryCommit(F.binding.base.head, F.binding.base.tree, Vector.empty)),
    historicalPublicationFailure = Some(historical), acceptedSourcePrefix = Some(prefix))
  val target = F.target.copy(schemaVersion = R.canonicalSuccessorSchema, sourceRevision = "70", pendingRevision = "70",
    plan = plan, step = "step-2", generation = repair.generation, completed = repair.completed,
    repairProgress = GoalPhaseRepairProgress.snapshot(repair), requestingResult = failure.requestingResult,
    proposal = proposal.source, binding = binding, preStageGuard = guard, canonicalOrigin = Some(origin))
  val direction = R.Direction(target, ContextReference("later-source-human", "1"), "participant",
    "SPECIFICATION-ONLY exact later source", Some(C.choice))
  val successor = take(repair.adoptCanonicalDependencySourceC(direction.directive))
  val capture = R.Capture(R.canonicalSuccessorCaptureSchema, "same-session", ContextReference(plan.identity, plan.revision),
    "step-2", successor.generation, "74", repair.base, direction, guard, Vector.empty, guard)
  val fresh = failedcandidate.copy(sourceRevision = "74", candidate = None, changedPaths = Vector.empty, diff = None,
    stagedPaths = Vector.empty, dependencySourceDirection = Some(direction), primaryPublicationRepair = Some(saved.reuse))
  val candidate = fresh.copy(candidate = Some(O.Snapshot(validation.candidate.get.head, "5" * 40)),
    stagedPaths = fresh.selectedPaths, changedPaths = fresh.selectedPaths, diff = Some("SPECIFICATION-ONLY corrected source"),
    dependencySourceGuard = Some(guard), dependencySourceCapture = Some(capture))
  val lineageApproval = {
    val previous = origin.approvals.head.proposal
    GoalPhaseDependencyValidation.Approval(target.step, previous.copy(identity = "later-usage-scope", revision = "2",
      usageContract = previous.usageContract.copy(revision = "2"), paths = binding.paths,
      acceptance = Vector("Consume the newly approved API revision", "Preserve the original caller obligation"),
      dependentWork = Vector(ContextReference(target.step, "1"))), ContextReference("later-usage-human", "1"))
  }
  val lineageSource = proposal.source.copy(coverage = proposal.source.coverage :+
    R.Coverage(lineageApproval.decision, lineageApproval.proposal.acceptance, binding.checks.map(_.identity)))
  val lineageOrigin = origin.copy(approvals = origin.approvals :+ lineageApproval,
    selection = proposal.copy(source = lineageSource))
  val lineageTarget = target.copy(proposal = lineageSource, canonicalOrigin = Some(lineageOrigin))
  val lineageDirection = direction.copy(target = lineageTarget)
  val lineageCapture = capture.copy(direction = lineageDirection)
}

/** Canonical source contracts use specification-only observations and authority.
  * @since   Nov.  8, 2026
  * @version Nov.  9, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseCanonicalDependencySourceSuccessionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CanonicalDependencySourceSpecificationFixture as F
  "LSR-01 Later source and genuine publication failure" should {
    import LaterSourcePublicationSpecificationFixture as L
    import GoalPhasePrerequisitePrimaryRepair as A
    "retain complete independently approved Usage revisions within one identity across the accepted source prefix" in {
      Given("a specification-only accepted Usage revision1 and a distinct current-Step approval of revision2 with its own full acceptance")
      val oldcoverage = L.olddirection.target.proposal.coverage
      val oldapprovals = L.origin.approvals
      When("the exact later target direction and capture cross public and strict owner codecs")
      val publictarget = R.targetC(GoalPhaseNativeMetadata.record(L.lineageTarget.asJson))
      val directionwire = io.circe.parser.parse(take(GoalPhaseNativeMetadata.record(L.lineageDirection.asJson).toJsonStringC)).toOption.get
      val publicdirection = R.decodePublicDirection(directionwire)
      val capturewire = io.circe.parser.parse(take(GoalPhaseNativeMetadata.record(L.lineageCapture.asJson).toJsonStringC)).toOption.get
      val publiccapture = for {
        wire <- GoalPhaseNativeMetadata.normalizePublicJson(capturewire)
        direction <- R.decodePublicDirection(wire.hcursor.downField("direction").focus.get)
        capture <- wire.mapObject(_.add("direction", direction.asJson)).as[R.Capture].left.map(_.message)
      } yield capture
      val opaque = L.lineageApproval.copy(proposal = L.lineageApproval.proposal.copy(
        usageContract = ContextReference("linked-api", "approved-next")))
      val opaquevalid = R.targetValid(L.lineageTarget.copy(canonicalOrigin = Some(L.lineageOrigin.copy(
        approvals = L.origin.approvals :+ opaque))))
      Then("both literal refs and complete per-decision obligations survive without changing the cause prefix or repair reservation")
      R.targetValid(L.lineageTarget) shouldBe true
      take(publictarget) shouldBe L.lineageTarget
      publicdirection shouldBe Right(L.lineageDirection)
      publiccapture shouldBe Right(L.lineageCapture)
      L.lineageTarget.asJson.as[R.Target] shouldBe Right(L.lineageTarget)
      L.lineageDirection.asJson.as[R.Direction] shouldBe Right(L.lineageDirection)
      L.lineageCapture.asJson.as[R.Capture] shouldBe Right(L.lineageCapture)
      L.lineageOrigin.approvals.init shouldBe oldapprovals
      oldapprovals.map(_.proposal.usageContract).distinct shouldBe Vector(ContextReference("linked-api", "1"))
      L.lineageOrigin.approvals.last.proposal.usageContract shouldBe ContextReference("linked-api", "2")
      L.lineageSource.coverage.startsWith(oldcoverage) shouldBe true
      L.lineageSource.coverage.last shouldBe R.Coverage(L.lineageApproval.decision,
        L.lineageApproval.proposal.acceptance, L.binding.checks.map(_.identity))
      L.lineageOrigin.originalDirection shouldBe Some(L.olddirection)
      L.lineageOrigin.historicalPublicationFailure shouldBe Some(L.historical)
      L.lineageTarget.repairProgress shouldBe L.target.repairProgress
      L.lineageTarget.repairProgress.ordinaryCount shouldBe 1
      L.lineageTarget.repairProgress.limit shouldBe 10
      opaquevalid shouldBe true
    }
    "refuse foreign boundaries and incomplete or rewritten approval acceptance while retaining literal inherited references" in {
      Given("the exact same-identity revision lineage and the complete ordered old and new coverage")
      val approval = L.lineageApproval
      val scope = approval.proposal
      def _target_(approvals: Vector[GoalPhaseDependencyValidation.Approval], coverage: Vector[R.Coverage]): R.Target = {
        val source = L.lineageSource.copy(coverage = coverage)
        L.lineageTarget.copy(proposal = source, canonicalOrigin = Some(L.lineageOrigin.copy(approvals = approvals,
          selection = L.lineageOrigin.selection.copy(source = source))))
      }
      val foreign = Vector(scope.copy(usageContract = scope.usageContract.copy(identity = "foreign-usage")),
        scope.copy(requester = scope.requester.copy(identity = "foreign-requester")),
        scope.copy(dependency = scope.dependency.copy(identity = "foreign-component")),
        scope.copy(dependency = scope.dependency.copy(repository = "foreign-repository")))
      val coverage = L.lineageSource.coverage
      val oldentry = coverage.head
      val newentry = coverage.last
      When("one boundary, native approval, original acceptance or inherited reference is removed or crossed")
      val targets = foreign.map(value => _target_(L.origin.approvals :+ approval.copy(proposal = value), coverage)) ++ Vector(
        _target_(L.origin.approvals, coverage),
        _target_(L.lineageOrigin.approvals, coverage.tail),
        _target_(L.lineageOrigin.approvals, coverage.init),
        _target_(L.lineageOrigin.approvals, coverage.updated(0, oldentry.copy(acceptance = Vector("Replaced old obligation")))),
        _target_(L.lineageOrigin.approvals, coverage.updated(0, oldentry.copy(acceptance = oldentry.acceptance.dropRight(1)))),
        _target_(L.lineageOrigin.approvals, coverage.updated(coverage.size - 1, newentry.copy(acceptance = Vector("Replaced new obligation")))),
        _target_(L.lineageOrigin.approvals, coverage.updated(coverage.size - 1, newentry.copy(acceptance = newentry.acceptance.dropRight(1)))),
        _target_(L.lineageOrigin.approvals.updated(0, L.origin.approvals.head.copy(proposal =
          L.origin.approvals.head.proposal.copy(usageContract = ContextReference("linked-api", "2")))), coverage),
        L.lineageTarget.copy(canonicalOrigin = Some(L.lineageOrigin.copy(originalDirection = Some(
          L.olddirection.copy(directive = ContextReference("replacement-original-human", "1")))))))
      Then("the structural codecs refuse every crossed target without claiming native Human authentication")
      targets.foreach { target =>
        R.targetValid(target) shouldBe false
        target.asJson.as[R.Target].isLeft shouldBe true
      }
      R.targetValid(L.target) shouldBe true
      F.target.asJson.as[R.Target] shouldBe Right(F.target)
      F.capture.asJson.as[R.Capture] shouldBe Right(F.capture)
    }
    "resolve current same-core routes without reinterpreting retained predecessor registrations as future extensions" in {
      Given("the actual old and current fixture captures with one source core and different fixed current-Step routes")
      val old = R.consumptionOf(L.oldcapture)
      val current = R.consumptionOf(L.capture)
      val declarations: Vector[R.ConsumptionRecord] = Vector(old, current)
      val basis = take(GoalPhaseCanonicalCheckConsumption.basisC(L.direction))
      val selected = Vector(GoalPhaseNativeValidation.Check("primary-later", 2, WorkflowSbtValidation.Kind.Test))
      When("the source owner projects only proven literal ancestors before the unchanged canonical policy and check resolver")
      val view = take(C.currentRegistrationsC(L.direction, declarations))
      val resolved = GoalPhaseCanonicalCheckConsumption.resolveC(Some(current), Some(basis), L.plan, selected,
        sameStepCapture = true, retained = None, declarations = view, phasePolicy = None)
      val policy = GoalPhaseCanonicalCheckConsumption.policyC(basis, L.origin.processPolicy, L.origin.processPolicy, view, None)
      val unprojected = GoalPhaseCanonicalCheckConsumption.resolveC(Some(current), Some(basis), L.plan, selected,
        sameStepCapture = true, retained = None, declarations = declarations, phasePolicy = None)
      Then("the current route is selected while the original declaration vector and accepted source remain literal")
      view shouldBe Vector(current)
      take(resolved) shouldBe Some(current)
      policy.isSuccess shouldBe true
      unprojected.isSuccess shouldBe false
      declarations shouldBe Vector(old, current)
      R.consumptionOf(L.oldcapture) shouldBe old
      L.direction.target.canonicalOrigin.get.originalDirection shouldBe Some(L.olddirection)
    }
    "preserve unrelated ambiguity and reject duplicate declarations or crossed claimed source lineage" in {
      Given("the same-core successor, an unrelated route, and exact predecessor declarations retained as history")
      val old = R.consumptionOf(L.oldcapture)
      val current = L.consumption
      val declarations: Vector[R.ConsumptionRecord] = Vector(old, current)
      val unrelated = current.copy(programs = current.programs.updated(0, current.programs.head.copy(program = "unrelated-fixed-route")))
      val basis = take(GoalPhaseCanonicalCheckConsumption.basisC(L.direction))
      val selected = Vector(GoalPhaseNativeValidation.Check("primary-later", 2, WorkflowSbtValidation.Kind.Test))
      val crossed = L.direction.copy(target = L.target.copy(canonicalOrigin = Some(L.origin.copy(originalDirection = Some(
        L.olddirection.copy(target = L.olddirection.target.copy(sessionId = "foreign-session")))))))
      When("projection receives unrelated same-core declarations, hidden duplicate ancestors, crossed lineage or a legacy direction")
      val view = take(C.currentRegistrationsC(L.direction, declarations :+ unrelated))
      val refused = GoalPhaseCanonicalCheckConsumption.resolveC(Some(current), Some(basis), L.plan, selected,
        sameStepCapture = true, retained = None, declarations = view, phasePolicy = None)
      val legacy = C.currentRegistrationsC(L.olddirection, declarations)
      val legacybasis = take(GoalPhaseCanonicalCheckConsumption.basisC(L.olddirection))
      Then("only exact historical records are filtered; unrelated ambiguity and the old resolver contract still refuse")
      view shouldBe Vector(current, unrelated)
      refused.isSuccess shouldBe false
      GoalPhaseCanonicalCheckConsumption.policyC(basis, L.origin.processPolicy, L.origin.processPolicy, view, None).isSuccess shouldBe false
      C.currentRegistrationsC(L.direction, declarations :+ old).isSuccess shouldBe false
      C.currentRegistrationsC(L.direction, declarations :+ current).isSuccess shouldBe false
      C.currentRegistrationsC(L.direction, declarations :+ null).isSuccess shouldBe false
      C.currentRegistrationsC(crossed, declarations).isSuccess shouldBe false
      take(legacy) shouldBe declarations
      GoalPhaseCanonicalCheckConsumption.policyC(legacybasis, L.olddirection.target.canonicalOrigin.get.processPolicy,
        L.olddirection.target.canonicalOrigin.get.processPolicy, take(legacy), None).isSuccess shouldBe false
    }
    "round trip the separate publication cause and accepted earlier Step while preserving legacy bytes" in {
      Given("a classified pre-check Compile failure after a pure accepted earlier source Step, with no old later-Step source capture")
      val legacy = F.target.asJson
      When("the v11 target and v12 capture cross the public Record and strict owner codecs")
      val publictarget = R.targetC(GoalPhaseNativeMetadata.record(L.target.asJson))
      val context = C.publicationContext(L.historical)
      Then("the literal original and accepted prefix remain separate from current source and repair accounting")
      A.factValid(L.fact) shouldBe true
      R.directionValid(L.olddirection) shouldBe true
      R.targetValid(L.target) shouldBe true
      take(publictarget) shouldBe L.target
      L.target.asJson.as[R.Target] shouldBe Right(L.target)
      L.capture.asJson.as[R.Capture] shouldBe Right(L.capture)
      context.publication shouldBe L.failed
      context.testsExecuted shouldBe false
      L.failedcandidate.dependencySourceCapture shouldBe None
      L.target.canonicalOrigin.get.originalDirection shouldBe Some(L.olddirection)
      L.target.completed shouldBe L.repair.completed
      GoalPhaseRepairProgress.snapshot(L.successor) shouldBe GoalPhaseRepairProgress.snapshot(L.repair)
      legacy.hcursor.downField("canonicalOrigin").downField("historicalPublicationFailure").succeeded shouldBe false
      legacy.hcursor.downField("canonicalOrigin").downField("acceptedSourcePrefix").succeeded shouldBe false
      legacy.as[R.Target] shouldBe Right(F.target)
      F.capture.asJson.as[R.Capture] shouldBe Right(F.capture)
    }
    "reject crossed missing null stale and mixed cause or version fields" in {
      Given("the exact later-Step publication target and immutable native-shaped reservation")
      val origins = Vector(L.origin.copy(acceptedSourcePrefix = None),
        L.origin.copy(acceptedSourcePrefix = Some(L.prefix.copy(closure = ContextReference("foreign", "50")))),
        L.origin.copy(acceptedSourcePrefix = Some(L.prefix.copy(closure = ContextReference("same-session", "71")))),
        L.origin.copy(historicalPublicationFailure = Some(L.historical.copy(repair = ContextReference("same-session", "64")))),
        L.origin.copy(historicalPublicationFailure = Some(L.historical.copy(repair = ContextReference("foreign", "63")))),
        L.origin.copy(controller = L.origin.controller.copy(ordinaryRepairs = L.origin.controller.ordinaryRepairs + 1)))
      When("one exact relation, version discriminator, source binding or required field is crossed")
      val targets = origins.map(o => L.target.copy(canonicalOrigin = Some(o))) ++ Vector(
        L.target.copy(schemaVersion = R.canonicalSchema),
        L.target.copy(binding = L.binding.copy(step = L.olddirection.target.step)),
        L.target.copy(plan = L.plan.copy(identity = "foreign-plan")))
      val malformed = Vector(L.historical.asJson.mapObject(_.remove("fact")),
        L.historical.asJson.mapObject(_.add("fact", Json.Null)),
        L.historical.asJson.mapObject(_.add("authority", Json.True)))
      Then("none decodes into a usable direction or fabricated check failure")
      targets.foreach(value => R.targetValid(value) shouldBe false)
      malformed.foreach(_.as[C.HistoricalPublicationFailure].isLeft shouldBe true)
      L.target.asJson.mapObject(_.add("canonicalOrigin", L.origin.asJson.mapObject(
        _.add("historicalPublicationFailure", Json.Null)))).as[R.Target].isLeft shouldBe true
      L.capture.copy(schemaVersion = R.canonicalCaptureSchema).asJson.as[R.Capture].isLeft shouldBe true
      F.capture.copy(schemaVersion = R.canonicalSuccessorCaptureSchema).asJson.as[R.Capture].isLeft shouldBe true
    }
    "select only the effective authenticated structural source sequence for Step and Phase execution" in {
      Given("old accepted source and a later source with different current-Step fixed routes; tuples carry no native authority")
      val accepted = Vector(L.oldcapture -> Some("50"))
      val complete = accepted :+ (L.capture -> Some("80"))
      When("the structural selector proves each consumption change against the exact prior source and closure")
      val step = GoalPhaseNativeValidation.sourceConsumptionSequenceC(accepted :+ (L.capture -> None))
      val phase = GoalPhaseNativeValidation.sourceConsumptionSequenceC(complete)
      val legacy = GoalPhaseNativeValidation.sourceConsumptionSequenceC(accepted)
      val unowned = GoalPhaseNativeValidation.admittedSourceConsumptionC(Some(L.capture), None)
      Then("current execution uses the new routes while original captures and single-base behavior remain literal")
      take(step) shouldBe Some(L.consumption)
      take(phase) shouldBe Some(L.consumption)
      take(legacy) shouldBe Some(R.consumptionOf(L.oldcapture))
      take(unowned) shouldBe Some(L.consumption)
      Vector(accepted :+ (L.capture.copy(direction = F.direction) -> None),
        Vector(L.oldcapture -> Some("51"), L.capture -> None),
        Vector(L.oldcapture -> None, L.capture -> None),
        Vector(L.capture -> Some("80"), L.oldcapture -> None)).foreach(entries =>
        GoalPhaseNativeValidation.sourceConsumptionSequenceC(entries).isSuccess shouldBe false)
    }
  }
  "E1 Canonical committed source and current Plan lineage" must afterWord(
    "in spec:goal-phase-dependency-source-revision, example:E1, rules:R10,R11,R12, phase:6.11") {
    "round trip distinct closed proposal target direction and capture without management file identities" in {
      Given("docs/spec/goal-phase-dependency-source-revision.md R10–R12 E1; specification-only Plan1 Direction and current Plan2 one-path append")
      When("the canonical proposal and native committed representation are encoded")
      val proposalvalid = C.proposalValid(F.proposal)
      val targetvalid = R.targetValid(F.target)
      val proposal = F.proposal.asJson.as[C.Proposal]
      val publicrecord = GoalPhaseNativeMetadata.record(F.proposal.asJson)
      val canonicalpublic = C.isProposal(publicrecord)
      val publicproposal = C.proposalC(publicrecord)
      val target = F.target.asJson.as[R.Target]
      val direction = F.direction.asJson.as[R.Direction]
      val capture = F.capture.asJson.as[R.Capture]
      val originalwire = F.target.canonicalOrigin.get.originalDirection.get.asJson
      val predecessorwire = F.original.asJson
      val guardwire = F.target.preStageGuard.asJson.noSpaces
      val stagedpaths = R.stagingPaths(F.direction, None, F.guard)
      Then("the original lineage stays literal and only the canonical guard uses native entries")
      proposalvalid shouldBe true
      targetvalid shouldBe true
      proposal shouldBe Right(F.proposal)
      canonicalpublic shouldBe true
      publicproposal.toOption shouldBe Some(F.proposal)
      target shouldBe Right(F.target)
      direction shouldBe Right(F.direction)
      capture shouldBe Right(F.capture)
      originalwire shouldBe predecessorwire
      F.target.completed shouldBe F.originalstate.completed
      F.target.completed.map(_.step) shouldBe Vector("step-0")
      F.target.ancestors.map(_.capture) shouldBe Vector(F.prefixcapture)
      guardwire should not include "sha256"
      F.target.preStageGuard.selectedFiles shouldBe empty
      F.target.binding.root should not be F.original.target.binding.root
      F.target.binding.branch should not be F.original.target.binding.branch
      F.target.binding.base should not be F.original.target.binding.base
      stagedpaths shouldBe empty
      F.publication.result.publication.request.target.dependency shouldBe None
      F.publication.result.publication.request.participant shouldBe "external-publisher"
    }
  }
  "E2 Canonical contracts fail closed" must afterWord(
    "in spec:goal-phase-dependency-source-revision, example:E2, rules:R10,R11,R12, phase:6.11") {
    "reject null unknown mixed versions and crossed source lineage modes approvals or programs" in {
      Given("docs/spec/goal-phase-dependency-source-revision.md R10–R12 E2; a complete specification-only target")
      When("one required canonical source fact or contract is crossed")
      val targets = Vector(F.target.copy(schemaVersion = R.sourceSchema), F.target.copy(canonicalOrigin = None),
        F.target.copy(plan = F.original.target.plan), F.target.copy(binding = F.binding.copy(root = "/crossed")),
        F.target.copy(binding = F.binding.copy(dependency = F.binding.dependency.copy(identity = "crossed"))),
        F.target.copy(binding = F.binding.copy(dependency = F.binding.dependency.copy(repository = "crossed"))),
        F.target.copy(binding = F.binding.copy(requester = F.binding.requester.copy(workspace = "crossed"))),
        F.target.copy(binding = F.binding.copy(base = F.binding.base.copy(head = "9" * 40))),
        F.target.copy(preStageGuard = F.guard.copy(dirty = Vector(R.Dirty("?", "?", "foreign.scala", None)))),
        F.target.copy(preStageGuard = F.guard.copy(indexTree = "9" * 40)),
        F.target.copy(preStageGuard = F.guard.copy(committedInventory = Some(F.guard.committedInventory.get.copy(
          selected = F.guard.committedInventory.get.selected.updated(0, F.guard.committedInventory.get.selected.head.copy(
            entry = Some(R.CommittedObject("100755", "7" * 40)))))))),
        F.target.copy(binding = F.binding.copy(paths = F.binding.paths :+ "unapproved.scala")),
        F.target.copy(canonicalOrigin = Some(F.origin.copy(approvals = Vector.empty))),
        F.target.copy(canonicalOrigin = Some(F.origin.copy(processPolicy = "{}"))),
        F.target.copy(canonicalOrigin = Some(F.origin.copy(controller = F.origin.controller.copy(
          phaseRecordingPaths = Vector("active-recording.md"))))),
        F.target.copy(canonicalOrigin = Some(F.origin.copy(controller = F.origin.controller.copy(
          candidateParent = Some(F.target.primaryBase))))),
        F.target.copy(proposal = F.source.copy(consumption = F.consumption.copy(programs =
          F.consumption.programs.updated(0, F.consumption.programs.head.copy(program = "arbitrary"))))))
      Then("none can become a canonical Direction")
      targets.foreach(t => withClue(t.toString)(R.targetValid(t) shouldBe false))
      Vector(F.target.asJson.mapObject(_.add("canonicalOrigin", Json.Null)),
        F.target.asJson.mapObject(_.add("authority", Json.True)),
        F.target.asJson.mapObject(_.add("schemaVersion", R.publishedSchema.asJson))).foreach(_.as[R.Target].isLeft shouldBe true)
      F.guard.asJson.mapObject(_.add("committedInventory", Json.Null)).as[R.Guard].isLeft shouldBe true
      F.guard.asJson.mapObject(_.add("digest", Json.fromString("invented"))).as[R.Guard].isLeft shouldBe true
      R.captureValid(F.capture.copy(stagedPaths = F.binding.paths)) shouldBe false
      R.captureValid(F.capture.copy(schemaVersion = R.sourceCaptureSchema)) shouldBe false
    }
  }
  "E3 Pending worker lifecycle and retained repair budget" must afterWord(
    "in spec:goal-phase-dependency-source-revision, example:E3, rules:R10,R13, phase:6.11") {
    "require explicit ended-worker and unknown-effects assertions while preserving the reserved repair" in {
      Given("docs/spec/goal-phase-dependency-source-revision.md R10,R13 E3; an actual-shaped unclaimed Repair request, not proof of nonexecution")
      val request = DependencyValidationSpecificationFixture.request.copy(kind = "RepairStep", selectedPlan = F.plan,
        generation = F.controller.generation, completed = F.controller.completed, repairDirective = F.controller.repairDirective,
        repairProgress = Some(GoalPhaseRepairProgress.snapshot(F.controller)))
      val pending = C.Pending(ContextReference("specification-original-repair", "30"), "1", request)
      val proposal = F.proposal.copy(workerLifecycle = Some(C.WorkerLifecycle(true, true)))
      val target = F.target.copy(canonicalOrigin = Some(F.origin.copy(pending = Some(pending), selection = proposal)))
      When("the trusted exact Human Direction authorizes source succession")
      val next = take(F.controller.adoptCanonicalDependencySourceC(F.direction.directive))
      Then("one generation changes, counters and reservations stay literal and ordinary work remains due")
      R.targetValid(target) shouldBe true
      C.choicesFor(target) shouldBe Vector(C.pendingChoice, "Defer")
      pending.asJson.as[C.Pending] shouldBe Right(pending)
      pending.asJson.mapObject(_.add("status", "CLAIMED".asJson)).as[C.Pending].isLeft shouldBe true
      pending.asJson.mapObject(_.add("claimId", "live-worker".asJson)).as[C.Pending].isLeft shouldBe true
      C.proposalValid(proposal.copy(workerLifecycle = Some(C.WorkerLifecycle(false, true)))) shouldBe false
      C.proposalValid(proposal.copy(workerLifecycle = Some(C.WorkerLifecycle(true, false)))) shouldBe false
      R.targetValid(target.copy(canonicalOrigin = Some(target.canonicalOrigin.get.copy(selection = F.proposal)))) shouldBe false
      next.generation shouldBe F.controller.generation + 1
      next.stage shouldBe F.controller.stage
      GoalPhaseRepairProgress.snapshot(next) shouldBe GoalPhaseRepairProgress.snapshot(F.controller)
      next.pendingRepair shouldBe F.controller.pendingRepair
      next.repairObservations shouldBe F.controller.repairObservations
      next.repairDirective shouldBe F.controller.repairDirective
      next.plan shouldBe F.controller.plan
      next.completed shouldBe F.controller.completed
      next.next.asInstanceOf[O.Semantic].key.kind shouldBe O.WorkKind.RepairStep
      F.inspected.adoptCanonicalDependencySourceC(F.direction.directive).isSuccess shouldBe false
    }
  }
  "E4 Exact historical failed-check guidance" must afterWord(
    "in spec:goal-phase-dependency-source-revision, example:E4, rules:R12,R13, phase:6.11") {
    "retain admitted terminal failure references and reject incomplete crossed or active projections" in {
      Given("docs/spec/goal-phase-dependency-source-revision.md R12,R13 E4; specification-only terminal previous check failure")
      val check = GoalPhaseNativeValidation.Check("primary-test", 1, WorkflowSbtValidation.Kind.Test)
      val receipt = RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(1),
        "SPECIFICATION-ONLY actual-shaped failure", "", Some(WorkflowSbtValidation.Facts(
          WorkflowSbtValidation.Kind.Test, WorkflowSbtValidation.Outcome.Failed, None, None)))
      val validation = GoalPhaseNativeValidation.Payload("20", F.target.step, F.inspected.candidate.get,
        Vector(check), Vector(receipt), Some("repair-required"))
      val context = take(GoalPhaseNativeWork.checkFailureContextC(Vector("screen.scala"), "specification-only diff", validation))
      val failure = C.HistoricalFailure(ContextReference(F.target.sessionId, "24"), ContextReference(F.target.sessionId, "28"), context)
      val target = F.target.copy(canonicalOrigin = Some(F.origin.copy(historicalFailure = Some(failure))))
      val crossed = Vector(failure.copy(validation = failure.validation.copy(identity = "crossed-session")),
        failure.copy(validation = failure.validation.copy(revision = "24")),
        failure.copy(context = context.copy(validation = validation.copy(outcome = None))),
        failure.copy(context = context.copy(validation = validation.copy(outcome = Some("passed")))))
      val future = target.copy(canonicalOrigin = Some(target.canonicalOrigin.get.copy(historicalFailure =
        Some(failure.copy(validation = failure.validation.copy(revision = "31"))))))
      val active = target.asJson.hcursor.downField("canonicalOrigin").focus.get.mapObject(_.add("activeCheck", Json.True))
      When("the closed canonical origin records only this prior native terminal context")
      val failurevalid = C.failureValid(failure)
      val decoded = failure.asJson.as[C.HistoricalFailure]
      val targetvalid = R.targetValid(target)
      val crossedvalid = crossed.map(C.failureValid)
      val futurevalid = R.targetValid(future)
      val activeorigin = active.as[C.Origin]
      Then("the failure is retained as guidance without qualifying the new candidate")
      failurevalid shouldBe true
      decoded shouldBe Right(failure)
      targetvalid shouldBe true
      crossedvalid.foreach(_ shouldBe false)
      futurevalid shouldBe false
      activeorigin.isLeft shouldBe true
    }
  }
  "E5 Independent canonical evidence references" must afterWord(
    "in spec:goal-phase-dependency-source-revision, example:E5, rules:R10,R12, phase:6.11") {
    "require direction recollection artifact and adopted delivery at the real evidence time" in {
      Given("docs/spec/goal-phase-dependency-source-revision.md R10,R12 E5; exact specification-only description and Human answer")
      import org.simplemodeling.textus.smworkflow.value as V
      val reference = V.WorkflowArtifactReference(F.direction.directive.identity, F.direction.directive.revision)
      val description = V.GoalPhaseDependencySourceRevisionDescriptionResult(F.target.sessionId, "direction-required", R.message,
        F.origin.componentInstance, R.targetRecord(F.target), R.choicesFor(F.target), R.adoptionOperation)
      val input = V.GoalPhaseDependencySourceRevisionAdoptionInput(F.target.sessionId, description.target, C.choice, F.direction.rationale, reference)
      val references = Vector(reference, V.WorkflowArtifactReference(F.publication.recollectionReference.identity, F.publication.recollectionReference.revision),
        V.WorkflowArtifactReference(F.publication.artifact.identity, F.publication.artifact.revision),
        V.WorkflowArtifactReference(F.proposal.adoptedDelivery.identity, F.proposal.adoptedDelivery.revision))
      def _prepare_(values: Vector[V.WorkflowArtifactReference], at: String) =
        org.simplemodeling.textus.smworkflow.preparation.NativeDependencySourceRevisionPreparation.prepareC(description, input, reference,
          V.WorkflowExecutionEvidence(values, Some("participant"), None, None), "participant", "specification-only-host", java.time.Instant.parse(at))
      When("the passive evidence preparation receives the exact complete references")
      val prepared = _prepare_(references, "2026-11-08T00:00:00Z")
      val missing = references.indices.map(i => _prepare_(references.patch(i, Nil, 1), "2026-11-08T00:00:00Z"))
      val premature = _prepare_(references, "2026-11-06T00:00:00Z")
      Then("each required reference and the publication observation time remain independently checked")
      prepared.isSuccess shouldBe true
      missing.foreach(_.isSuccess shouldBe false)
      premature.isSuccess shouldBe false
    }
  }
  "E6 Accepted prefix preservation in the native pure continuation" must afterWord(
    "in spec:goal-phase-dependency-source-revision, example:E6, rules:R13, phase:6.11") {
    "keep completed commits reviews original base and current repair accounting literal" in {
      Given("docs/spec/goal-phase-dependency-source-revision.md R13 E6; a pure two-Step native controller with a genuinely closed first Step")
      val plan = F.original.target.plan.copy(steps = F.original.target.plan.steps :+
        F.original.target.plan.steps.head.copy(identity = "step-2"))
      val initial = take(O.startC(plan, F.target.primaryBase))
      val implemented = take(initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key, "specification-only first result")))
      val candidate = O.Snapshot(initial.base.head, "8" * 40)
      val inspected = take(implemented.acceptC(O.CandidateObserved(candidate)))
      val checked = take(inspected.acceptC(O.ChecksObserved(candidate, Vector("primary-test"), "specification-first-checks", true)))
      val reviewed = take(checked.acceptC(O.ReviewAdmitted(checked.next.asInstanceOf[O.Semantic].key, candidate,
        "specification-first-checks", "specification-first-review", O.ReviewOutcome.Accept)))
      val intent = take(reviewed.acceptC(O.ClosureIntentSaved(candidate, "specification-first-closure")))
      val current = take(intent.acceptC(O.StepCommitObserved("specification-first-closure", O.Commit("9" * 40, candidate.head, candidate.tree))))
      When("the native owner admits one canonical source generation for the next semantic stage")
      val next = take(current.adoptCanonicalDependencySourceC(F.direction.directive))
      Then("accepted prefix and real repair accounting remain untouched")
      next.completed shouldBe current.completed
      next.completed.map(_.step) shouldBe Vector("step-0")
      next.base shouldBe initial.base
      next.plan shouldBe current.plan
      next.generation shouldBe current.generation + 1
      GoalPhaseRepairProgress.snapshot(next) shouldBe GoalPhaseRepairProgress.snapshot(current)
    }
  }
  "E7 Committed inventory structural validity without application count limits" must afterWord(
    "in spec:goal-phase-dependency-source-revision, example:E7, rules:R11, phase:6.11") {
    "accept selected and excluded inventories above128 while rejecting duplicate crossed and unsafe paths" in {
      Given("docs/spec/goal-phase-dependency-source-revision.md R11 E7; specification-only native-shaped entries establish no Git observation or authority")
      val selected = Vector.tabulate(129)(index => R.CommittedEntry("src/main/scala/Unit" + index + ".scala",
        Some(R.CommittedObject(if (index % 2 == 0) "100644" else "100755", "7" * 40))))
      val excluded = Vector.tabulate(130)(index => R.CommittedEntry("docs/noninput/Unit" + index + ".md", None))
      val inventory = R.CommittedInventory(selected, excluded)
      val guard = F.guard.copy(committedInventory = Some(inventory))
      val invalid = Vector(inventory.copy(selected = selected :+ selected.head),
        inventory.copy(excluded = excluded :+ excluded.head),
        inventory.copy(excluded = excluded :+ selected.head),
        inventory.copy(selected = selected.updated(0, selected.head.copy(path = "../outside.scala"))),
        inventory.copy(excluded = excluded.updated(0, excluded.head.copy(path = "/outside.md"))))
      val malformed = Vector(inventory.asJson.mapObject(_.add("selected", Json.Null)),
        inventory.asJson.mapObject(_.add("excluded", Json.Null)),
        inventory.asJson.mapObject(_.remove("selected")), inventory.asJson.mapObject(_.remove("excluded")),
        inventory.asJson.mapObject(_.add("authority", Json.True)), inventory.copy(selected = Vector.empty).asJson,
        inventory.copy(selected = selected.updated(0, selected.head.copy(entry = Some(R.CommittedObject("120000", "7" * 40))))).asJson,
        inventory.copy(selected = selected.updated(0, selected.head.copy(entry = Some(R.CommittedObject("100644", "invalid-blob"))))).asJson)
      When("the complete inventories pass through the canonical structural validator and real inventory and Guard wire codecs")
      val canonicalvalid = C.guardValid(guard)
      val guardvalid = R.guardValid(guard)
      val decodedinventory = inventory.asJson.as[R.CommittedInventory]
      val decodedguard = guard.asJson.as[R.Guard]
      val invalidshapes = invalid.map(value => C.guardValid(guard.copy(committedInventory = Some(value))))
      val invalidinventories = invalid.map(_.asJson.as[R.CommittedInventory])
      val invalidguards = invalid.map(value => guard.copy(committedInventory = Some(value)).asJson.as[R.Guard])
      val malformedinventories = malformed.map(_.as[R.CommittedInventory])
      Then("inventory counts add no application refusal while uniqueness literal path safety and exact wire fields remain mandatory")
      canonicalvalid shouldBe true
      guardvalid shouldBe true
      decodedinventory shouldBe Right(inventory)
      decodedguard shouldBe Right(guard)
      invalidshapes.foreach(_ shouldBe false)
      invalidinventories.foreach(_.isLeft shouldBe true)
      invalidguards.foreach(_.isLeft shouldBe true)
      malformedinventories.foreach(_.isLeft shouldBe true)
    }
  }
}
