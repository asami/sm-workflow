package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseCurrentSourceArtifactAdoption as A
import GoalPhasePrerequisitePrimaryRepair as P
import GoalPhasePrerequisitePublicationRecovery as B
import GoalPhasePrerequisitePublicationUnknownRecovery as W
import GoalPhaseDependencySourceRevision as R
import GoalPhaseNativeCandidate as C
import PrimaryPublicationRepairSpecificationFixture as F
import PrimaryPublicationContinuationSpecificationFixture as Q
import GoalPhaseDependencyValidation.given
import SplitPhaseExplicitPartitionFixture.take
import org.simplemodeling.textus.smworkflow.preparation.NativePrerequisitePublicationRecoveryPreparation
import org.simplemodeling.textus.smworkflow.value as V

/** Small typed business fixtures do not provision Human evidence, fabricate
  * native acceptance or qualify installed effects. E11 owns that qualification.
  * @since   Nov.  2, 2026
  * @version Nov.  7, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseCurrentSourceArtifactAdoptionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _successor = F.sourceSuccessor()
  private val _candidate = _successor._4
  private val _capture = _successor._3
  private val _previous = W.ExistingArtifactAdoption(Q.artifactFact.reference, Q.unknown, A.sourceOf(Q.postGuard), Q.postGuard)
  private val _failure = F.narrowFailure.copy(artifactAdoption = Some(_previous))
  private val _publication = F.narrowTarget.copy(frontierRevision = "62", candidateRecordRevision = "62",
    candidate = _candidate, failure = _failure, scope = P.scope(_failure))
  private val _target = A.Target(A.targetSchema, _publication, _previous, _capture.postStageGuard,
    A.sourceOf(_capture.postStageGuard), _failure.processPolicy)
  private val _reference = ContextReference("specification-current-artifact-human", "1")
  private val _direction = A.Direction(_target, A.choices.head, "SPECIFICATION-OWNED independent current artifact answer", _reference)
  // These records are pure arguments for relation predicates, never persisted
  // or admitted as workflow acceptance evidence.
  private val _repair = P.Saved(F.nativeRecord(37), F.narrowFact.copy(failure = _failure))
  private val _saved_candidate = C.Saved(F.nativeRecord(62), _candidate)
  private def _record(value: A.Target) = GoalPhaseNativeMetadata.record(value.asJson)

  "Current source artifact targets" should {
    "require a fresh artifact decision for approved inventory changes at the same committed source identity" in {
      Given("CB-P610-M3-CURRENT-ARTIFACT-GUARD-01: valid committed source fixtures with additional approved inventory but identical HEAD index and dirty status")
      import DependencySourceRevisionSpecificationFixture as S
      val (oldsource, expanded) = F.policySourceSuccessor()
      val oldcapture = oldsource.dependencySourceCapture.get
      val oldguard = oldcapture.postStageGuard
      val oldbinding = R.bindingOf(oldcapture)
      val expandedcapture = expanded.dependencySourceCapture.get
      val expandedscope = expandedcapture.scope.get
      val expandednext = expandedscope.consumptionDirection.get
      val binding = expandedscope.binding.copy(base = oldbinding.base)
      val guard = oldguard.copy(selectedFiles = expandedcapture.postStageGuard.selectedFiles,
        selectedEntries = expandedcapture.postStageGuard.selectedEntries)
      val next = expandednext.copy(target = expandednext.target.copy(binding = binding, preStageGuard = guard,
        committedBaseSuccession = None))
      val scope = expandedscope.copy(binding = binding, consumptionDirection = Some(next))
      val capture = expandedcapture.copy(preStageGuard = guard, postStageGuard = guard, scope = Some(scope))
      val previouscandidate = oldsource.copy(selectedPaths = F.narrowFailure.candidate.selectedPaths,
        stagedPaths = F.narrowFailure.candidate.stagedPaths, candidate = F.narrowFailure.candidate.candidate,
        changedPaths = F.narrowFailure.candidate.changedPaths, diff = F.narrowFailure.candidate.diff)
      val reuse = F.narrowReuse.copy(sourceRevision = previouscandidate.sourceRevision)
      val candidate = previouscandidate.copy(sourceRevision = capture.sourceRevision, candidate = F.narrowCandidate.candidate,
        diff = F.narrowCandidate.diff, dependencySourceScope = Some(scope), dependencySourceGuard = Some(guard),
        dependencySourceCapture = Some(capture), primaryPublicationRepair = Some(reuse))
      val dependency = Q.dependency.copy(workspace = oldbinding.dependency.workspace)
      val targets = Vector(dependency, Q.primary)
      val oldunknown = Q.unknown.copy(request = Q.unknown.request.copy(target = dependency, source = A.sourceOf(oldguard)))
      val previous = W.ExistingArtifactAdoption(_previous.direction, oldunknown, A.sourceOf(oldguard), oldguard)
      val input = LocalPublicationRuntime.prerequisiteInput(_failure.sessionId, previouscandidate, Q.primary)
      val failed = _failure.failed.copy(request = _failure.failed.request.copy(
        input = input.copy(requestIdentity = input.requestIdentity + ":prerequisite:0")))
      val failure = _failure.copy(candidate = previouscandidate, targets = targets,
        publicationPolicy = F.publicationPolicy(targets), failed = failed, artifactAdoption = Some(previous),
        primaryDirections = Vector.empty)
      val publication = _publication.copy(candidate = candidate, failure = failure, scope = P.scope(failure))
      val target = A.Target(A.targetSchema, publication, previous, guard, A.sourceOf(guard), failure.processPolicy)
      val before = failure.asJson
      When("the complete current-source target is checked and passed through its closed codec")
      val valid = A.targetValid(target)
      val decoded = target.asJson.as[A.Target]
      Then("the changed exact guard requires the fresh artifact choice even though the publication source identity is unchanged")
      R.modeOf(oldcapture) shouldBe "committed"
      R.captureValid(oldcapture) shouldBe true
      R.captureValid(capture) shouldBe true
      R.candidateValid(candidate) shouldBe true
      P.failureValid(failure) shouldBe true
      P.targetValid(publication) shouldBe true
      P.currentSourceSuccessorValid(previouscandidate, candidate) shouldBe true
      A.needed(P.Saved(_repair.record, _repair.fact.copy(failure = failure)),
        _saved_candidate.copy(payload = candidate), Vector.empty) shouldBe true
      target.source shouldBe previous.source
      guard.head shouldBe oldguard.head
      guard.indexTree shouldBe oldguard.indexTree
      guard.dirty shouldBe oldguard.dirty
      guard.selectedFiles should not be oldguard.selectedFiles
      binding.paths shouldBe oldbinding.paths ++ scope.approvals.flatMap(_.proposal.paths)
      valid shouldBe true
      decoded shouldBe Right(target)
      failure.asJson shouldBe before

      Given("the same authenticated fresh source direction and primary repair with no inventory guard or source change")
      val samebinding = oldbinding.copy(revision = binding.revision)
      val sameconsumption = R.Consumption(S.proposal.consumption.primaryWorkspace, S.proposal.consumption.sourceRoot,
        S.proposal.consumption.nonInputExclusions, S.proposal.consumption.expectedModes, S.proposal.consumption.programs)
      val sametarget = next.target.copy(binding = samebinding, preStageGuard = oldguard,
        proposal = S.proposal.copy(schemaVersion = R.sourceSchema,
          binding = ContextReference(samebinding.identity, samebinding.revision.toString), consumption = sameconsumption),
        sourceSuccession = next.target.sourceSuccession.map(_.copy(approvals = Vector.empty)))
      val samedirection = next.copy(target = sametarget)
      val samescope = scope.copy(binding = samebinding, approvals = Vector.empty,
        consumption = sameconsumption, coverage = S.proposal.coverage, consumptionDirection = Some(samedirection))
      val samecapture = capture.copy(preStageGuard = oldguard, postStageGuard = oldguard, scope = Some(samescope))
      val samecandidate = candidate.copy(dependencySourceScope = Some(samescope), dependencySourceGuard = Some(oldguard),
        dependencySourceCapture = Some(samecapture))
      val samepublication = publication.copy(candidate = samecandidate)
      val unchanged = target.copy(publicationTarget = samepublication, guard = oldguard, source = A.sourceOf(oldguard))
      When("the complete target is checked with both source and guard unchanged")
      val rejected = A.targetValid(unchanged)
      Then("a valid source-reference succession alone cannot create another artifact decision")
      R.captureValid(samecapture) shouldBe true
      R.candidateValid(samecandidate) shouldBe true
      P.targetValid(samepublication) shouldBe true
      P.currentSourceSuccessorValid(previouscandidate, samecandidate) shouldBe true
      unchanged.guard shouldBe previous.guard
      unchanged.source shouldBe previous.source
      rejected shouldBe false
      failure.asJson shouldBe before
    }

    "round trip a closed exact current-source target while preserving all original facts" in {
      Given("the typed current committed source successor and original primary failure with its immutable UNKNOWN adoption")
      val before = _failure.asJson
      val old = _previous.asJson
      When("the closed public target and native direction codecs round trip those exact facts")
      val target = _target.asJson.as[A.Target]
      val direction = A.codec.decode(A.codec.encode(_direction))
      val publictarget = A.targetC(_record(_target))
      Then("the current guard and source are explicit and the old failure, UNKNOWN and acceptance remain byte-identical")
      P.failureValid(_failure) shouldBe true
      P.targetValid(_publication) shouldBe true
      R.captureValid(_capture) shouldBe true
      P.currentSourceSuccessorValid(_failure.candidate, _candidate) shouldBe true
      A.targetValid(_target) shouldBe true
      target shouldBe Right(_target)
      direction shouldBe Right(_direction)
      take(publictarget) shouldBe _target
      _target.previous.previous.status shouldBe "uncertain"
      _target.previous.previous shouldBe Q.unknown
      _target.source should not be _previous.source
      _target.publicationTarget.repair shouldBe Some(_repair.reference)
      _failure.asJson shouldBe before
      _previous.asJson shouldBe old
    }

    "reject unknown fields wrong types malformed policies and incomplete direction envelopes" in {
      Given("a valid v4 target and separately encoded native direction")
      val target = _target.asJson
      val wire = A.codec.encode(_direction)
      val badtargets = Vector(target.mapObject(_.add("participant", "extra-redundant-claim".asJson)),
        target.mapObject(_.add("unknown", true.asJson)), target.mapObject(_.remove("source")),
        target.mapObject(_.add("guard", Json.arr())), target.mapObject(_.add("processPolicy", Json.obj())),
        target.mapObject(_.add("processPolicy", "not-json".asJson)),
        target.mapObject(_.add("schemaVersion", B.primarySchema.asJson)),
        target.mapObject(_.add("previous", Json.Null)))
      val badwires = Vector(wire.mapObject(_.add("extra", true.asJson)),
        wire.mapObject(_.add("schemaVersion", P.targetSchema.asJson)),
        wire.mapObject(_.remove("payload")),
        wire.mapObject(_.add("payload", _direction.asJson.mapObject(_.add("choice", "Defer".asJson)))))
      When("the closed codecs decode each malformed or extended claim")
      val targets = badtargets.map(_.as[A.Target].isLeft)
      val directions = badwires.map(A.codec.decode(_).isLeft)
      Then("every undeclared, incomplete, wrongly typed or noncanonical claim refuses")
      targets shouldBe Vector.fill(badtargets.size)(true)
      directions shouldBe Vector.fill(badwires.size)(true)
    }

    "bind the exact original owner participant UNKNOWN capture Plan base Step repair and generation" in {
      Given("a complete target whose old adoption and current capture are separate immutable evidence")
      val publication = _target.publicationTarget
      val current = publication.candidate
      val crossedpublications = Vector(publication.copy(sessionId = "other-session"),
        publication.copy(participant = "other-participant"), publication.copy(componentInstance = "other-component"),
        publication.copy(frontierRevision = "61", candidateRecordRevision = "62"),
        publication.copy(repair = Some(ContextReference("other-repair", "37"))),
        publication.copy(generation = publication.generation + 1),
        publication.copy(scope = publication.scope.copy(base = publication.scope.base.copy(tree = "0" * 40))),
        publication.copy(scope = publication.scope.copy(plan = publication.scope.plan.copy(revision = "2"))),
        publication.copy(candidate = current.copy(step = "other-step")),
        publication.copy(candidate = current.copy(dependencySourceCapture = None)),
        publication.copy(candidate = current.copy(dependencySourceCapture = Some(_capture.copy(sourceRevision = "59")))),
        publication.copy(candidate = current.copy(primaryPublicationRepair = Some(F.narrowReuse.copy(generation = 999)))))
      val old = _previous.previous
      val badold = Vector(old.copy(status = "published"), old.copy(reason = "caller-replaced-original"),
        old.copy(request = old.request.copy(participant = "other-participant")),
        old.copy(request = old.request.copy(input = old.request.input.copy(ownerSessionId = Some("other-session")))),
        old.copy(request = old.request.copy(target = old.request.target.copy(workspace = "other-workspace"))))
      val crossed = crossedpublications.map(p => _target.copy(publicationTarget = p)) ++
        badold.map(o => _target.copy(previous = _previous.copy(previous = o))) ++ Vector(
          _target.copy(guard = _previous.guard, source = _previous.source),
          _target.copy(source = _target.source.copy(tree = "0" * 40)),
          _target.copy(processPolicy = F.sorted(Json.obj("processPlan" -> Json.obj("sbtPrograms" -> Json.arr())))))
      When("each crossed claim is checked against the complete retained target")
      val rejected = crossed.map(A.targetValid)
      Then("no caller substitution supplies the original UNKNOWN or current source authority")
      rejected shouldBe Vector.fill(crossed.size)(false)
    }

    "require the publication controller generation even when a copied capture remains structurally valid" in {
      Given("CB-P611-257-02: a closed artifact target and a structurally valid capture copied into a later generation")
      val publication = _target.publicationTarget
      val before = _failure.asJson
      val previous = _previous.asJson
      val generation = publication.generation + 1
      val copied = _candidate.copy(dependencySourceCapture = Some(_capture.copy(generation = generation)))
      val mismatched = _target.copy(publicationTarget = publication.copy(candidate = copied))
      val claimed = mismatched.copy(publicationTarget = mismatched.publicationTarget.copy(generation = generation))
      When("the target checks the copied capture against its publication generation and retained controller")
      val rejected = Vector(A.targetValid(mismatched), A.targetValid(claimed))
      Then("structural capture acceptance supplies neither a matching current target nor independent artifact authority")
      C.sourceCaptureValid(copied) shouldBe true
      C.codec.decode(C.codec.encode(copied)) shouldBe Right(copied)
      P.targetValid(mismatched.publicationTarget) shouldBe true
      P.targetValid(claimed.publicationTarget) shouldBe false
      rejected shouldBe Vector(false, false)
      A.targetValid(_target) shouldBe true
      _failure.asJson shouldBe before
      _previous.asJson shouldBe previous
    }

    "require the exact current candidate capture and repair relation for applicability" in {
      Given("pure typed direction and native-shaped arguments without any provisioned acceptance")
      val later = _candidate.copy(sourceRevision = "63")
      val changed = _candidate.copy(diff = Some("another primary correction"))
      val otherrepair = _repair.copy(record = F.nativeRecord(38))
      When("applicability compares exact candidate payload, capture, candidate revision and saved repair reference")
      val matching = A.applicable(_direction, _repair, _saved_candidate)
      val crossed = Vector(A.applicable(_direction, otherrepair, _saved_candidate),
        A.applicable(_direction, _repair, _saved_candidate.copy(record = F.nativeRecord(63))),
        A.applicable(_direction, _repair, _saved_candidate.copy(payload = later)),
        A.applicable(_direction, _repair, _saved_candidate.copy(payload = changed)),
        A.applicable(_direction.copy(choice = "Defer"), _repair, _saved_candidate))
      Then("only the exact current relation applies and missing acceptance still requires a fresh decision")
      matching shouldBe true
      crossed shouldBe Vector.fill(crossed.size)(false)
      A.needed(_repair, _saved_candidate, Vector.empty) shouldBe true
      A.needed(_repair, _saved_candidate.copy(payload = F.narrowCandidate), Vector.empty) shouldBe false
    }
  }

  "Independent current artifact evidence" should {
    "dispatch only the exact closed binding and keep Defer separate from retained authority" in {
      Given("an independently identified Human answer for the exact described current target")
      val before = _failure.asJson
      When("the existing evidence selector dispatches Accept and Defer through v4")
      val accepted = take(A.bindingC(_target.componentInstance, _target, A.choices.head,
        _direction.rationale, _target.participant, _reference))
      val deferred = take(A.bindingC(_target.componentInstance, _target, "Defer",
        "SPECIFICATION-OWNED defer current decision", _target.participant, _reference))
      Then("both canonical bindings use the existing purpose while Defer can never decode as a retained direction")
      A.selectBinding(accepted) shouldBe Right(accepted)
      B.selectBinding(accepted) shouldBe Right(accepted)
      B.selectBinding(deferred) shouldBe Right(deferred)
      A.directionValid(_direction.copy(choice = "Defer")) shouldBe false
      A.directionValid(_direction.copy(reference = ContextReference(_target.sessionId, "63"))) shouldBe false
      A.codec.decode(A.codec.encode(_direction.copy(choice = "Defer"))).isLeft shouldBe true
      A.bindingC(_target.componentInstance, _target, "AuthorizePrimaryPublicationOnce", _direction.rationale,
        _target.participant, _reference).isSuccess shouldBe false
      A.bindingC(_target.componentInstance, _target, A.choices.head, "bad\u0000rationale",
        _target.participant, _reference).isSuccess shouldBe false
      A.bindingC(_target.componentInstance, _target, A.choices.head, _direction.rationale,
        "other-participant", _reference).isSuccess shouldBe false
      B.selectBinding(accepted.mapObject(_.add("extra", true.asJson))).isLeft shouldBe true
      _failure.asJson shouldBe before
    }

    "passively prepare the actual described target with independent execution evidence and complete choices" in {
      Given("the described v4 target, exact supplied independent answer and real-shaped execution evidence")
      val description = A.description(_target)
      val reference = V.WorkflowArtifactReference(_reference.identity, _reference.revision)
      val input = V.GoalPhasePrerequisitePublicationRecoveryInput(_target.sessionId, description.target,
        A.choices.head, _direction.rationale, reference, None)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(_target.participant), None, None)
      def _prepare_(described: V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult = description,
        supplied: V.GoalPhasePrerequisitePublicationRecoveryInput = input, evidence: V.WorkflowExecutionEvidence = execution) =
        NativePrerequisitePublicationRecoveryPreparation.prepareC(described, supplied, reference, evidence,
          _target.participant, "independent-specification-provider", Instant.parse("2026-11-02T00:00:00Z"))
      When("passive preparation binds the complete described source and independent answer")
      val prepared = _prepare_()
      val rejected = Vector(_prepare_(described = description.copy(choices = Vector(A.choices.head))),
        _prepare_(described = description.copy(choices = A.choices.reverse)),
        _prepare_(described = description.copy(componentInstance = "other-component")),
        _prepare_(described = description.copy(sessionId = "other-session")),
        _prepare_(described = description.copy(outcome = "observation-required")),
        _prepare_(supplied = input.copy(target = _record(_target.copy(source = _previous.source)))),
        _prepare_(evidence = execution.copy(references = Vector(V.WorkflowArtifactReference("other-source-answer", "1")))),
        _prepare_(evidence = execution.copy(workerIdentity = Some("other-participant")))).map(_.isSuccess)
      Then("the existing provider purpose is retained and stale source evidence or crossed descriptions refuse")
      prepared.isSuccess shouldBe true
      rejected shouldBe Vector.fill(rejected.size)(false)
      B.recoveryInputC(input).isSuccess shouldBe true
      B.recoveryInputC(input.copy(choice = "AuthorizePrimaryPublicationOnce")).isSuccess shouldBe false
    }
  }

  private final class PublicationMemory extends LocalPublication.Port {
    val rows = scala.collection.mutable.Map.empty[String, Json]
    var source = Q.unknown.request.source
    var writes = 0
    var starts = 0
    def loadC(key: String) = Consequence.success(rows.get(key))
    def createC(key: String, value: Json): Consequence[Unit] = if (rows.contains(key)) Consequence.stateConflict("Immutable specification duplicate")
      else { rows(key) = value; writes += 1; Consequence.unit }
    def sourceC(target: LocalPublication.Target) = Consequence.success(source)
    def executeC(target: LocalPublication.Target): Consequence[SbtExecutionAttempt] = {
      starts += 1
      val process = WorkflowSbtCapture.decode(F.process(termination = ProcessExecutionTermination.TimedOut)).toOption.get
      Consequence.success(SbtExecutionAttempt.Observed(SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC(target.workspace))),
        SbtOperation.PublishLocal, process)))
    }
    def newIdentity() = "specification-original-unknown"
  }

  "The current-source existing artifact gate" should {
    "read the exact old UNKNOWN at the new source without writing publishing or manufacturing success" in {
      Given("the real in-memory immutable publication algorithm retaining a timed-out original request and a separately selected current source")
      val memory = new PublicationMemory
      val old = take(LocalPublication.ensurePrerequisiteC(Q.unknown.request.input, Q.dependency, _target.participant, memory))
      val adoption = W.ExistingArtifactAdoption(_reference, old, _target.source, _target.guard)
      val before = memory.rows.toMap
      val writes = memory.writes
      val starts = memory.starts
      memory.source = _target.source
      When("the same read-only production gate consumes the current guard and exact original UNKNOWN twice")
      val results = Vector.fill(2)(LocalPublicationRuntime.adoptExistingArtifactsC(adoption, Q.dependency,
        _target.sessionId, _target.participant, Consequence.success(_target.guard), memory).isSuccess)
      Then("both collections match the old UNKNOWN and no new effect, request, write or success receipt is produced")
      old.status shouldBe "uncertain"
      results shouldBe Vector(true, true)
      memory.rows.toMap shouldBe before
      memory.writes shouldBe writes
      memory.starts shouldBe starts
      take(LocalPublication.collectC(old.request.input, Q.dependency, _target.participant, memory)) shouldBe Some(old)

      Given("the live source has drifted away from the accepted current capture")
      memory.source = _previous.source
      When("the otherwise exact current-source adoption is checked against that live source")
      val drifted = LocalPublicationRuntime.adoptExistingArtifactsC(adoption, Q.dependency, _target.sessionId,
        _target.participant, Consequence.success(_target.guard), memory)
      Then("live drift refuses without publishing or rewriting the old UNKNOWN")
      drifted.isSuccess shouldBe false
      memory.rows.toMap shouldBe before
      memory.writes shouldBe writes
      memory.starts shouldBe starts

      Given("a changed live guard, changed source, other owner, participant or fabricated old result")
      memory.source = _target.source
      When("each stale or crossed claim reaches the same read-only gate")
      val rejected = Vector(
        LocalPublicationRuntime.adoptExistingArtifactsC(adoption, Q.dependency, _target.sessionId, _target.participant,
          Consequence.success(_previous.guard), memory),
        LocalPublicationRuntime.adoptExistingArtifactsC(adoption.copy(source = _previous.source), Q.dependency,
          _target.sessionId, _target.participant, Consequence.success(_target.guard), memory),
        LocalPublicationRuntime.adoptExistingArtifactsC(adoption, Q.dependency, "other-session", _target.participant,
          Consequence.success(_target.guard), memory),
        LocalPublicationRuntime.adoptExistingArtifactsC(adoption, Q.dependency, _target.sessionId, "other-participant",
          Consequence.success(_target.guard), memory),
        LocalPublicationRuntime.adoptExistingArtifactsC(adoption.copy(previous = old.copy(reason = "fabricated")), Q.dependency,
          _target.sessionId, _target.participant, Consequence.success(_target.guard), memory)).map(_.isSuccess)
      Then("every crossed or stale claim refuses without changing the old UNKNOWN or invoking any process")
      rejected shouldBe Vector.fill(rejected.size)(false)
      memory.rows.toMap shouldBe before
      memory.writes shouldBe writes
      memory.starts shouldBe starts
    }
  }
}
