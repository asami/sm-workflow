package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.convert.ValueReader
import org.goldenport.record.io.RecordDecoder
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.datastore.DataStore
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.prop.TableDrivenPropertyChecks.*
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.preparation.NativeCandidateRecoveryPreparation as P
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseCandidateRecovery as R
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeCandidate as C
import SplitPhaseExplicitPartitionFixture.take

/** Pure contract and passive preparation evidence; these inputs grant no real Human authority.
  * @since   Oct. 22, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseCandidateRecoverySpec extends AnyWordSpec with NativeGoalSourceHistoryCliFixture {
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private def _target: R.RecoveryTarget = {
    val paths = Vector(sourcePath, "docs/guide.md")
    val plan = O.Plan("candidate-plan", "2", "phase-116", "fixture-repository",
      Vector(O.Step("step-1", "Complete the owned candidate", paths, Vector.empty, Vector("test"))), Vector("test"))
    val initial = take(O.startC(plan, _base))
    val state = take(initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key, "Fixture implementation")))
    val inspection = C.Payload("50", "step-1", paths, Vector.empty, None, Vector.empty, None)
    val history = GoalPhaseNativeSourceHistory.Payload("50", "step-1", sourcePath, "goldenport",
      "native-cli-external-goldenport-v1", "2026-10-22", true, None)
    def _id_(key: String) = "fixture-session:ordered-candidate:50:" + key
    R.RecoveryTarget("fixture-session", R.controllerSnapshot(state), "goal-phase", "1", "participant", "50", "57",
      "/private/tmp/fixture-candidate-repository", "main", paths, Vector.empty,
      _id_("inspection"), "1", "51", inspection,
      WorkflowSourceHistoryRule.Selection("goldenport", Some(take(WorkflowSourceHistoryRule.decodeC(enabledSettings(ruleKey))))),
      Vector(R.HistoryPair(0, _id_("source-history-intent-0"), "1", "52", _id_("source-history-result-0"), "1", "53",
        history, history.copy(outcome = Some("updated")))),
      paths.indices.map(i => R.StagePair(i, _id_("stage-intent-" + i), "1", (54 + i * 2).toString,
        _id_("stage-result-" + i), "1", (55 + i * 2).toString, inspection.copy(stagedPaths = paths.take(i)),
        inspection.copy(stagedPaths = paths.take(i + 1)))).toVector, "UNKNOWN", "termination-unconfirmed")
  }
  private val _reference = V.WorkflowArtifactReference("fresh-independent-human", "1")
  private val _answer = V.GoalPhaseCandidateRecoveryAnswer("AuthorizeNewObservation", "Accept one new observation while old termination remains unconfirmed")
  private val _execution = V.WorkflowExecutionEvidence(Vector(_reference), Some("participant"), None, None)
  private val _now = Instant.parse("2026-10-22T00:00:00Z")
  private def _description(target: R.RecoveryTarget): V.GoalPhaseCandidateRecoveryDescriptionResult =
    V.GoalPhaseCandidateRecoveryDescriptionResult(target.sessionId, "decision-required", R.message, "component",
      R.targetRecord(target), R.choicesFor(target), R.recoveryOperation)

  private def _public_description(target: R.RecoveryTarget): V.GoalPhaseCandidateRecoveryDescriptionResult = {
    val text = NativeExternalEvidenceToolFixture.normalCommandJson(_description(target))
    take(summon[ValueReader[V.GoalPhaseCandidateRecoveryDescriptionResult]].readC(take(new RecordDecoder().json(text))))
  }

  private def _prepare_wire(target: R.RecoveryTarget, edit: Json => Json = identity) =
    NativeExternalEvidenceToolFixture.prepareNormalCommand("GoalPhase", "candidate-recovery", _description(target),
      _answer.copy(choice = R.choicesFor(target).head), "participant", _reference, _execution,
      text => {
        val wire = io.circe.parser.parse(text).fold(error => fail(error.message), identity)
        val selected = GoalPhaseNativeMetadata.normalizePublicJson(wire.hcursor.downField("target").focus.get)
          .fold(fail(_), identity)
        wire.mapObject(_.add("target", edit(selected))).noSpaces
      })

  private def _dependency_target(count: Int): R.RecoveryTarget = {
    val original = _target
    val fixture = DependencyValidationSpecificationFixture
    val requester = fixture.requester.copy(repository = original.controller.plan.repository)
    val admission = fixture.admission.copy(binding = fixture.binding.copy(requester = requester, base = fixture.primaryBase),
      approvals = fixture.approvals.map(a => a.copy(proposal = a.proposal.copy(requester = requester))))
    val inspection = original.inspection.copy(dependencyAdmission = Some(admission))
    val stages = original.stagingPrefix.map(p => p.copy(
      intent = p.intent.copy(dependencyAdmission = Some(admission)), result = p.result.copy(dependencyAdmission = Some(admission))))
    val capture = fixture.capture.copy(session = original.sessionId,
      plan = ContextReference(original.controller.plan.identity, original.controller.plan.revision),
      generation = original.controller.generation, sourceRevision = original.sourceRevision,
      primaryBase = original.controller.base, admission = admission,
      candidate = fixture.capture.candidate.copy(head = admission.binding.base.head))
    val initial = stages.last.result
    val keys = admission.binding.paths.indices.flatMap(i => Vector("dependency-stage-intent-" + i,
      "dependency-stage-result-" + i)).toVector ++ Vector("dependency-observation-intent", "dependency-observation-result")
    val prefix = keys.take(count).zipWithIndex.map { (key, index) =>
      val staged = admission.binding.paths.take(math.min((index + 1) / 2, admission.binding.paths.size))
      val payload = initial.copy(dependencyStaged = staged, dependency = Option.when(key == "dependency-observation-result")(capture))
      R.DependencyFact(key, original.sessionId + ":ordered-candidate:" + original.sourceRevision + ":" + key,
        "1", (original.frontierRevision.toLong + index + 1).toString, payload)
    }
    original.copy(inspection = inspection, stagingPrefix = stages, dependencyPrefix = prefix,
      frontierRevision = (original.frontierRevision.toLong + count).toString)
  }

  "Dependency candidate recovery and passive Human preparation" should {
    "distinguish an unknown stage from an unknown observation without inventing stage success" in {
      Given("two specification-owned approval references and every bounded contiguous preparation prefix")
      val cases = Table(("prefixLength", "choices", "subject"),
        (0, Vector("ReviseWork", "Defer"), "dependency"),
        (1, Vector("ReviseWork", "Defer"), "dependency"),
        (2, Vector("ReviseWork", "Defer"), "dependency"),
        (3, Vector("ReviseWork", "Defer"), "dependency"),
        (4, Vector("AuthorizeNewObservation", "Defer"), "dependency"),
        (5, Vector("AuthorizeNewObservation", "Defer"), "dependency"),
        (6, Vector("AuthorizeNewObservation", "Defer"), "primary"))
      When("the existing pure target and preparation owners select the remaining obligation")
      Then("only recorded staging admits a distinct observation and every old unknown stays unknown")
      forAll(cases) { (count, choices, subject) =>
        val target = _dependency_target(count)
        R.decodeTarget(target.asJson) shouldBe Right(target)
        take(R.targetC(_public_description(target).target)) shouldBe target
        take(_prepare_wire(target))
        R.choicesFor(target) shouldBe choices
        R.remainingSubject(R.preparedPayload(target)) shouldBe subject
        val answer = _answer.copy(choice = choices.head)
        P.prepareC(_description(target), answer, _reference, _execution, "participant", "test-host", _now).isSuccess shouldBe true
        P.prepareC(_description(target).copy(choices = choices.reverse), answer, _reference, _execution,
          "participant", "test-host", _now).isSuccess shouldBe false
        target.oldObservationOutcome shouldBe "UNKNOWN"
        target.oldObservationTermination shouldBe "termination-unconfirmed"
      }
    }

    "retain completed capture provenance and reject gapped crossed or widened dependency facts" in {
      Given("a completed dependency capture before the still-unobserved primary candidate")
      val target = _dependency_target(6)
      val prepared = R.preparedPayload(target)
      val origin = prepared.dependencyOrigin.get
      val last = target.dependencyPrefix.last
      val invalid = Vector(target.copy(dependencyPrefix = target.dependencyPrefix.reverse),
        target.copy(dependencyPrefix = target.dependencyPrefix.drop(1)),
        target.copy(dependencyPrefix = target.dependencyPrefix.updated(0, target.dependencyPrefix.head.copy(identity = "foreign"))),
        target.copy(dependencyPrefix = target.dependencyPrefix.updated(5, last.copy(recordRevision = "64"))),
        target.copy(dependencyPrefix = target.dependencyPrefix.updated(5, last.copy(payload = last.payload.copy(
          dependency = last.payload.dependency.map(c => c.copy(primaryBase = c.admission.binding.base)))))),
        target.copy(dependencyPrefix = target.dependencyPrefix.updated(5, last.copy(payload = last.payload.copy(
          dependency = last.payload.dependency.map(c => c.copy(sourceRevision = "51")))))))
      When("an immutable prefix is decoded rather than migrated or inferred from current Git")
      Then("the original observation namespace remains bound and every crossed alternative refuses")
      origin.stageSourceRevision shouldBe target.sourceRevision
      origin.observationSourceRevision shouldBe target.sourceRevision
      origin.resultRecordRevision shouldBe last.recordRevision
      prepared.dependency shouldBe last.payload.dependency
      prepared.dependency.get.primaryBase should not be prepared.dependency.get.admission.binding.base
      invalid.foreach(t => R.decodeTarget(t.asJson).isLeft shouldBe true)
      R.decodeTarget(target.asJson.mapObject(_.add("dependencyPrefix", Json.Null))).isLeft shouldBe true
      R.decodeTarget(target.asJson.mapObject(_.add("remainingSubject", Json.fromString("other-repository")))).isLeft shouldBe true
      val direction = R.Direction(target, ContextReference(_reference.identity, _reference.revision), "participant", _answer.rationale)
      R.codec.decode(R.codec.encode(direction)) shouldBe Right(direction)
      R.codec.decode(R.codec.encode(direction).mapObject(_.add("schemaVersion",
        Json.fromString("sm-workflow.goal-phase-candidate-recovery.v1")))).isLeft shouldBe true
      val incomplete = _dependency_target(1)
      R.bindingC("component", incomplete, "AuthorizeNewObservation", _answer.rationale, "participant",
        ContextReference(_reference.identity, _reference.revision)).isSuccess shouldBe false
      R.codec.decode(R.codec.encode(direction.copy(target = incomplete, choice = "ReviseWork"))).isRight shouldBe true
    }
  }

  "E5 partial prefix preparation" should afterWord(
    "in spec:goal-phase-closure-recovery, example:E5, rules:R8, phase:CB-P67-PC05") {
    "accept later-Step and whole-Phase preparation absence while requiring exact evidence for the completed Phase candidate" in {
      Given("docs/spec/goal-phase-closure-recovery.md R8 E5: typed events complete the first Step of a two-Step Plan with an accepted partial chain")
      val original = _target.controller.plan
      val first = original.steps.head
      val second = first.copy(identity = "step-2", dependencies = Vector(first.identity))
      val plan = original.copy(steps = Vector(first, second))
      val started = take(O.startC(plan, _base))
      val ready = take(started.acceptC(O.SemanticCompleted(started.next.asInstanceOf[O.Semantic].key, "First implementation ready")))
      val candidate = O.Snapshot(_base.head, "e" * 40)
      val inspected = take(ready.acceptC(O.CandidateObserved(candidate)))
      val checked = take(inspected.acceptC(O.ChecksObserved(candidate, first.checks, "first-checks", true)))
      val reviewed = take(checked.acceptC(O.ReviewAdmitted(checked.next.asInstanceOf[O.Semantic].key, candidate,
        "first-checks", "first-review", O.ReviewOutcome.Accept)))
      val pending = take(reviewed.acceptC(O.ClosureIntentSaved(candidate, "first-partial")))
      val chain = O.StepCommitChain(_base, O.Commit("c" * 40, _base.head, "d" * 40),
        O.Commit("f" * 40, "c" * 40, candidate.tree))
      val firstclosed = take(pending.acceptC(O.StepPartialCompletionObserved("first-partial", chain, candidate.tree, Vector.empty)))
      val laterready = take(firstclosed.acceptC(O.SemanticCompleted(firstclosed.next.asInstanceOf[O.Semantic].key, "Second implementation ready")))
      val selected = C.Payload("10", first.identity, Vector(sourcePath), Vector(sourcePath), Some(candidate),
        Vector(sourcePath), Some("first accepted diff"))
      val laterinspection = C.Payload("20", second.identity, Vector("docs/guide.md"), Vector.empty, None, Vector.empty, None)
      val latercandidate = O.Snapshot(chain.completion.identity, "1" * 40)
      val laterselected = laterinspection.copy(stagedPaths = laterinspection.selectedPaths, candidate = Some(latercandidate),
        changedPaths = laterinspection.selectedPaths, diff = Some("second accepted diff"))
      When("retained loading examines a later-Step inspection and completed candidate after the accepted partial prefix")
      val laterreceipts = Vector(laterinspection, laterselected)
      val lateradmitted = laterreceipts.map(C.retainedEvidenceC(_, laterready, Vector(selected), Vector.empty))
      Then("neither later-Step receipt requires whole-Phase evidence and both preserve absent evidence through the codec")
      laterready.cursor shouldBe 1
      lateradmitted.foreach(_.isSuccess shouldBe true)
      laterreceipts.foreach { value =>
        value.stepEvidence shouldBe None
        C.codec.decode(C.codec.encode(value)) shouldBe Right(value)
      }

      Given("typed events also complete the second Step and enter whole-Phase inspection with inspection and staging receipts still lacking a candidate")
      val laterseen = take(laterready.acceptC(O.CandidateObserved(latercandidate)))
      val laterchecked = take(laterseen.acceptC(O.ChecksObserved(latercandidate, second.checks, "second-checks", true)))
      val laterreviewed = take(laterchecked.acceptC(O.ReviewAdmitted(laterchecked.next.asInstanceOf[O.Semantic].key, latercandidate,
        "second-checks", "second-review", O.ReviewOutcome.Accept)))
      val laterpending = take(laterreviewed.acceptC(O.ClosureIntentSaved(latercandidate, "second-closure")))
      val latercommit = O.Commit("2" * 40, chain.completion.identity, latercandidate.tree)
      val closed = take(laterpending.acceptC(O.StepCommitObserved("second-closure", latercommit)))
      val phaseinspection = C.Payload("30", "phase:" + plan.phase, Vector("docs/guide.md"), Vector.empty,
        None, Vector.empty, None)
      val phasestaged = phaseinspection.copy(stagedPaths = phaseinspection.selectedPaths)
      val evidence = Vector(C.StepEvidence(first.identity, candidate, selected.changedPaths, selected.diff.get,
        chain.completion, Vector.empty, Some(chain)),
        C.StepEvidence(second.identity, latercandidate, laterselected.changedPaths, laterselected.diff.get,
          latercommit, Vector.empty))
      val phase = phasestaged.copy(candidate = Some(closed.tipSnapshot),
        changedPaths = evidence.flatMap(_.changedPaths).distinct,
        diff = Some("Phase aggregate\n" + evidence.map(_.diff).mkString("\n")), stepEvidence = Some(evidence))
      val completed = Vector(selected, laterselected)
      val preparation = Vector(phaseinspection, phasestaged)
      When("retained loading checks in-progress whole-Phase receipts and the final candidate with absent or exact Step evidence")
      val preparationadmitted = preparation.map(C.retainedEvidenceC(_, closed, completed, phaseinspection.selectedPaths))
      val missing = C.retainedEvidenceC(phase.copy(stepEvidence = None), closed, completed, phaseinspection.selectedPaths)
      val exact = C.retainedEvidenceC(phase, closed, completed, phaseinspection.selectedPaths)
      Then("inspection and staging absence remain valid while only the completed whole-Phase candidate requires the exact accepted Step evidence")
      closed.cursor shouldBe plan.steps.size
      closed.stage shouldBe O.Stage.PhaseInspection
      preparationadmitted.foreach(_.isSuccess shouldBe true)
      preparation.foreach { value =>
        value.candidate shouldBe None
        value.stepEvidence shouldBe None
        C.codec.decode(C.codec.encode(value)) shouldBe Right(value)
      }
      missing.isSuccess shouldBe false
      exact.isSuccess shouldBe true
      C.codec.decode(C.codec.encode(phase)) shouldBe Right(phase)
    }
  }

  "GoalPhase candidate recovery E9 retained Phase evidence" should afterWord("in spec:goal-phase-candidate-recovery, example:E9, rules:R1,R4, phase:CB-CC04-CAND-02") {
    "retain an exact partial chain in whole Phase StepEvidence and reject a dropped or substituted chain" in {
      Given("docs/spec/goal-phase-closure-recovery.md R8 E67: an accepted Step completed by a bound partial chain")
      val plan = _target.controller.plan
      val started = take(O.startC(plan, _base))
      val ready = take(started.acceptC(O.SemanticCompleted(started.next.asInstanceOf[O.Semantic].key, "Ready")))
      val candidate = O.Snapshot(_base.head, "e" * 40)
      val inspected = take(ready.acceptC(O.CandidateObserved(candidate)))
      val checked = take(inspected.acceptC(O.ChecksObserved(candidate, plan.steps.head.checks, "checks", true)))
      val reviewed = take(checked.acceptC(O.ReviewAdmitted(checked.next.asInstanceOf[O.Semantic].key, candidate,
        "checks", "review", O.ReviewOutcome.Accept)))
      val pending = take(reviewed.acceptC(O.ClosureIntentSaved(candidate, "partial")))
      val chain = O.StepCommitChain(_base, O.Commit("c" * 40, _base.head, "d" * 40), O.Commit("f" * 40, "c" * 40, candidate.tree))
      val closed = take(pending.acceptC(O.StepPartialCompletionObserved("partial", chain, candidate.tree, Vector.empty)))
      val selected = C.Payload("10", "step-1", Vector(sourcePath), Vector(sourcePath), Some(candidate), Vector(sourcePath), Some("accepted diff"))
      val evidence = C.StepEvidence("step-1", candidate, selected.changedPaths, selected.diff.get, chain.completion, Vector.empty, Some(chain))
      val phase = C.Payload("20", "phase:" + plan.phase, Vector.empty, Vector.empty, Some(closed.tipSnapshot),
        selected.changedPaths, Some("Phase aggregate\naccepted diff"), stepEvidence = Some(Vector(evidence)))
      When("the native candidate codec and recording-only evidence comparison reconstruct that completed Step")
      val decoded = C.codec.decode(C.codec.encode(phase))
      val admitted = C.retainedEvidenceC(phase, closed, Vector(selected), Vector.empty)
      Then("the accepted candidate diff normalization and actual native parent are kept together")
      decoded shouldBe Right(phase)
      admitted.isSuccess shouldBe true
      decoded.toOption.get.stepEvidence.get.head.partialCompletion shouldBe Some(chain)
      C.retainedEvidenceC(phase.copy(stepEvidence = None), closed, Vector(selected), Vector.empty).isSuccess shouldBe false
      C.retainedEvidenceC(phase.copy(stepEvidence = Some(Vector(evidence.copy(partialCompletion = None)))), closed, Vector(selected), Vector.empty).isSuccess shouldBe false
      val other = chain.copy(partial = chain.partial.copy(tree = "1" * 40))
      C.retainedEvidenceC(phase.copy(stepEvidence = Some(Vector(evidence.copy(partialCompletion = Some(other))))), closed, Vector(selected), Vector.empty).isSuccess shouldBe false
      val ordinary = evidence.copy(partialCompletion = None)
      ordinary.asJson.hcursor.downField("partialCompletion").focus shouldBe None
    }

    "validate exact closed Step structure without consulting current Git" in {
      Given("CB-CC04-CAND-02: pure accepted Step facts with an explicit descendant candidate and scoped Phase evidence")
      val plan = _target.controller.plan
      val initial = take(O.startC(plan, _base))
      val ready = take(initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key, "Ready")))
      val parent = O.Snapshot("c" * 40, "d" * 40)
      val proof = O.CandidateContinuation(parent, Vector(O.HistoryCommit(parent.head, parent.tree, Vector(_base.head)),
        O.HistoryCommit(_base.head, _base.tree, Vector.empty)))
      val candidate = O.Snapshot(parent.head, "e" * 40)
      val seen = take(ready.acceptC(O.CandidateContinuationObserved(candidate, proof)))
      val checked = take(seen.acceptC(O.ChecksObserved(candidate, plan.steps.head.checks, "checks", true)))
      val reviewed = take(checked.acceptC(O.ReviewAdmitted(checked.next.asInstanceOf[O.Semantic].key,
        candidate, "checks", "independent-review", O.ReviewOutcome.Accept)))
      val pending = take(reviewed.acceptC(O.ClosureIntentSaved(candidate, "closure")))
      val commit = O.Commit("f" * 40, parent.head, candidate.tree)
      val closed = take(pending.acceptC(O.StepCommitObserved("closure", commit)))
      val selected = C.Payload("10", "step-1", Vector(sourcePath), Vector(sourcePath), Some(candidate),
        Vector(sourcePath), Some("selected source diff"), Some(proof))
      val evidence = C.StepEvidence("step-1", candidate, selected.changedPaths, selected.diff.get, commit, Vector.empty)
      val phase = C.Payload("20", "phase:" + plan.phase, Vector.empty, Vector.empty, Some(closed.tipSnapshot),
        selected.changedPaths, Some("Phase aggregate\n" + evidence.diff), stepEvidence = Some(Vector(evidence)))
      def _with_(value: C.StepEvidence) = phase.copy(stepEvidence = Some(Vector(value)))
      val invalid = Vector(_with_(evidence.copy(step = "crossed-step")),
        _with_(evidence.copy(commit = commit.copy(identity = "1" * 40))),
        _with_(evidence.copy(candidate = candidate.copy(tree = "2" * 40))),
        _with_(evidence.copy(changedPaths = Vector("foreign.txt"))),
        _with_(evidence.copy(diff = "invented diff")),
        _with_(evidence.copy(eofCorrections = Vector("foreign.txt"))),
        _with_(evidence.copy(eofCorrections = Vector(sourcePath))),
        phase.copy(changedPaths = Vector("foreign.txt")),
        phase.copy(diff = Some("missing selected diff")),
        phase.copy(changedPaths = selected.changedPaths :+ "docs/management.md"))
      When("the same pure structural admission used by both retained and live loading checks the payloads")
      val valid = C.retainedEvidenceC(phase, closed, Vector(selected), Vector("docs/management.md"))
      val refused = invalid.map(C.retainedEvidenceC(_, closed, Vector(selected), Vector("docs/management.md")))
      Then("exact historical facts admit while crossed commits candidates paths diffs and correction shapes refuse")
      valid.isSuccess shouldBe true
      refused.foreach(_.isSuccess shouldBe false)
      C.retainedEvidenceC(phase, closed, Vector(selected.copy(diff = Some("crossed saved diff"))), Vector.empty).isSuccess shouldBe false
      C.retainedEvidenceC(phase, closed, Vector.empty, Vector.empty).isSuccess shouldBe false
      C.retainedEvidenceC(phase, ready, Vector(selected), Vector.empty).isSuccess shouldBe false
      val managed = phase.copy(stagedPaths = Vector("docs/management.md"),
        changedPaths = selected.changedPaths :+ "docs/management.md")
      C.retainedEvidenceC(managed, closed, Vector(selected), Vector("docs/management.md")).isSuccess shouldBe true
    }
  }

  "GoalPhase candidate recovery E1 target codec" should afterWord("in spec:goal-phase-candidate-recovery, example:E1, rules:R1,R2, phase:CC04-CAND-01") {
    "retain the entire Plan controller native prefix and old uncertainty through public naming" in {
      Given("CC04-CAND-01: a full original inspection, successful history pair and two successful staging pairs")
      val target = _target
      val record = _public_description(target).target
      val direction = R.Direction(target, ContextReference(_reference.identity, _reference.revision), "participant", _answer.rationale)
      When("the public target and dedicated durable direction roundtrip")
      val restored = R.targetC(record)
      val replayed = R.codec.decode(R.codec.encode(direction))
      Then("every bound fact survives and old observation stays UNKNOWN with termination unconfirmed")
      take(restored) shouldBe target
      replayed shouldBe Right(direction)
      target.oldObservationOutcome shouldBe "UNKNOWN"
      target.oldObservationTermination shouldBe "termination-unconfirmed"
      R.choices shouldBe Vector("AuthorizeNewObservation", "Defer")
    }
  }

  "Public recovery wire presence" should {
    "decode present optional Controller values and preserve historical internal nulls" in {
      Given("a target and Controller with present typed optional values")
      val target = _target
      val directive = O.RepairDirective("Retained finding", Vector("finding-1"))
      val controller = target.controller.copy(candidate = Some(_base), candidateParent = Some(_base),
        phaseReviewedCandidate = Some(_base), checksReceipt = Some("checks"), reviewEvidence = Some("review"),
        closureIntent = Some("closure"), decision = Some("decision"), repositoryReceipt = Some("repository"),
        decisionSource = Some("Implementation"), repairDirective = Some(directive), pendingRepair = Some(directive))
      When("the public Controller and canonical internal Controller are decoded")
      val publiccontroller = R.publicController(controller.asJson)
      val internalcontroller = target.controller.asJson.as[R.Controller]
      Then("both exact Controllers are preserved")
      publiccontroller shouldBe Right(controller)
      internalcontroller shouldBe Right(target.controller)
    }

    "reject null and malformed members before Record erases their presence" in {
      Given("a valid formatted request and the declared required and optional members")
      val target = _target
      When("real preparation receives the valid baseline and malformed member variants")
      Then("the baseline succeeds and every malformed optional member refuses")
      take(_prepare_wire(target))
      val optional = Vector("candidate", "candidateParent", "checksReceipt", "reviewEvidence", "closureIntent",
        "decision", "repositoryReceipt", "repairDirective", "decisionSource", "pendingRepair", "phaseReviewedCandidate")
      optional.foreach { name =>
        Vector(Json.Null, Json.arr()).foreach { bad =>
          _prepare_wire(target, _.mapObject(fields => fields.add("controller",
            fields("controller").get.mapObject(_.add(name, bad))))).isSuccess shouldBe false
        }
      }
      val required = Vector("plan", "stage", "cursor", "generation", "repairs", "base", "tip", "completed",
        "documentDecision", "ordinaryRepairs", "phaseRecordingPaths")
      Then("omitted required, unknown, colliding and explicit null members refuse")
      required.foreach { name =>
        _prepare_wire(target, _.mapObject(fields => fields.add("controller",
          fields("controller").get.mapObject(_.remove(name))))).isSuccess shouldBe false
      }
      _prepare_wire(target, _.mapObject(fields => fields.add("controller",
        fields("controller").get.mapObject(_.add("foreign", Json.Null))))).isSuccess shouldBe false
      _prepare_wire(target, _.mapObject(fields => fields.add("controller",
        fields("controller").get.mapObject(_.add("ordinary_repairs", Json.fromInt(0)))))).isSuccess shouldBe false
      Vector("dependencyAdmission", "dependency", "dependencyOrigin", "dependencyStaged", "candidate", "diff").foreach { name =>
        _prepare_wire(target, _.mapObject(fields => fields.add("inspection",
          fields("inspection").get.mapObject(_.add(name, Json.Null))))).isSuccess shouldBe false
      }
      _prepare_wire(target, _.mapObject(_.add("dependencyPrefix", Json.Null))).isSuccess shouldBe false
      Given("the corresponding dependency-bearing request")
      val bound = _dependency_target(6)
      When("real preparation receives the complete and omitted-admission dependency subjects")
      Then("the complete dependency baseline succeeds and its incomplete subject refuses")
      take(_prepare_wire(bound))
      _prepare_wire(bound, _.mapObject(fields => fields.add("inspection",
        fields("inspection").get.mapObject(_.remove("dependencyAdmission"))))).isSuccess shouldBe false
    }
  }
  "GoalPhase candidate recovery E2 prefix admission" should afterWord("in spec:goal-phase-candidate-recovery, example:E2, rules:R1, phase:CC04-CAND-01") {
    "refuse incomplete gapped crossed unknown or already observed target shapes" in {
      Given("CC04-CAND-01: an otherwise complete target and exact receipt payloads")
      val target = _target
      val stage = target.stagingPrefix.head
      val history = target.historyPrefix.head
      val invalid = Vector(target.copy(sourceRevision = "49"), target.copy(frontierRevision = "58"),
        target.copy(inspectionRecordRevision = "52"), target.copy(inspectionRevision = "2"),
        target.copy(stagingPrefix = target.stagingPrefix.dropRight(1)), target.copy(stagingPrefix = target.stagingPrefix.reverse),
        target.copy(stagingPrefix = target.stagingPrefix.updated(0, stage.copy(resultRecordRevision = "56"))),
        target.copy(stagingPrefix = target.stagingPrefix.updated(0, stage.copy(resultIdentity = "crossed-receipt"))),
        target.copy(stagingPrefix = target.stagingPrefix.updated(0, stage.copy(result = stage.intent))),
        target.copy(stagingPrefix = target.stagingPrefix.updated(0, stage.copy(result = stage.result.copy(diff = Some("invented"))))),
        target.copy(historyPrefix = Vector(history.copy(result = history.result.copy(outcome = None)))),
        target.copy(historyPrefix = Vector(history.copy(result = history.result.copy(outcome = Some("missing"))))),
        target.copy(historyPrefix = Vector(history.copy(result = history.result.copy(outcome = Some("excluded"))))),
        target.copy(historyPrefix = Vector(history.copy(intentIdentity = "crossed-history"))),
        target.copy(inspection = target.inspection.copy(candidate = Some(_base))),
        target.copy(inspection = target.inspection.copy(selectedPaths = Vector.empty)),
        target.copy(candidatePaths = Vector.empty), target.copy(oldObservationOutcome = "NotExecuted"),
        target.copy(oldObservationTermination = "exited"), target.copy(sourceRevision = Long.MaxValue.toString),
        target.copy(controller = target.controller.copy(stage = "PhaseInspection", cursor = 1)),
        target.copy(controller = target.controller.copy(stage = "Validation")))
      When("each target is decoded for eligibility")
      val refused = invalid.map(t => R.decodeTarget(t.asJson))
      Then("none can authorize observation or turn unknown state into confirmed execution facts")
      R.decodeTarget(target.asJson) shouldBe Right(target)
      refused.foreach(_.isLeft shouldBe true)
      R.decodeTarget(target.asJson.mapObject(_.remove("controller"))).isLeft shouldBe true
      R.decodeTarget(target.asJson.mapObject(_.remove("historySelection"))).isLeft shouldBe true
    }
  }
  "GoalPhase candidate recovery E3 independent provenance" should afterWord("in spec:goal-phase-candidate-recovery, example:E3, rules:R2,R3, phase:CC04-CAND-01") {
    "bind full controller prefix base participant and reference independently" in {
      Given("CC04-CAND-01: separately issued evidence for a complete immutable target")
      val target = _target
      val reference = ContextReference(_reference.identity, _reference.revision)
      val binding = take(R.bindingC("component", target, _answer.choice, _answer.rationale, "participant", reference))
      val declaration = NativeExternalEvidence.Declaration("workflow", DataStore.CollectionId.EntityStore(
        EntityCollectionId("smworkflow", "cli", "host_evidence")), "test-host")
      val evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, R.purpose, "lookup", reference.identity,
        reference.revision, declaration.issuer, "participant", binding, _now.toString)
      val targets = Vector(target.copy(controller = target.controller.copy(plan = target.controller.plan.copy(revision = "3"))),
        target.copy(controller = target.controller.copy(generation = target.controller.generation + 1)),
        target.copy(controller = target.controller.copy(repairs = 1, ordinaryRepairs = 1)),
        target.copy(controller = target.controller.copy(base = O.Snapshot("c" * 40, "d" * 40), tip = O.Snapshot("c" * 40, "d" * 40))),
        target.copy(participant = "another-participant"), target.copy(definitionRevision = "2"), target.copy(branch = "another-branch"),
        target.copy(historyPrefix = target.historyPrefix.map(p => p.copy(result = p.result.copy(outcome = Some("unchanged"))))))
      When("the provider compares the actual binding with crossed complete targets and a crossed answer reference")
      val accepted = NativeExternalEvidence.observeC(evidence, declaration, NativeExternalEvidence.Purpose.CandidateRecovery, "lookup", binding, _now)
      val crossed = targets.map { t =>
        val requested = take(R.bindingC("component", t, _answer.choice, _answer.rationale, t.participant, reference))
        NativeExternalEvidence.observeC(evidence, declaration, NativeExternalEvidence.Purpose.CandidateRecovery, "lookup", requested, _now)
      }
      val crossedreference = take(R.bindingC("component", target, _answer.choice, _answer.rationale, "participant", ContextReference("different-answer", "1")))
      Then("only the exact independently supplied target and actual participant/reference admit")
      accepted.isSuccess shouldBe true
      R.selectBinding(binding) shouldBe Right(binding)
      crossed.foreach(_.isSuccess shouldBe false)
      NativeExternalEvidence.observeC(evidence, declaration, NativeExternalEvidence.Purpose.CandidateRecovery, "lookup", crossedreference, _now)
        .isSuccess shouldBe false
      R.bindingC("component", target, "AuthorizeNewAttempt", _answer.rationale, "participant", reference).isSuccess shouldBe false
      R.bindingC("component", target, _answer.choice, "", "participant", reference).isSuccess shouldBe false
    }
  }
  "GoalPhase candidate recovery E4 passive preparation" should afterWord("in spec:goal-phase-candidate-recovery, example:E4, rules:R2,R3, phase:CC04-CAND-01") {
    "prepare its own Human purpose for authorization and Defer while rejecting crossed supplied facts" in {
      Given("CC04-CAND-01: actual target description, explicit answer, matching worker and real reference")
      val description = _description(_target)
      When("normal passive preparation encodes authorization and separately authenticated Defer")
      val prepared = take(P.prepareC(description, _answer, _reference, _execution, "participant", "test-host", _now))
      val deferred = take(P.prepareC(description, _answer.copy(choice = "Defer"), _reference, _execution, "participant", "test-host", _now))
      Then("the dedicated evidence purpose binds the exact answer without a workflow direction or effect")
      val actual = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      actual.purpose shouldBe R.purpose
      actual.binding.hcursor.downField("candidateRecovery").downField("answer").get[String]("choice").toOption shouldBe Some("AuthorizeNewObservation")
      val deferredevidence = take(NativeExternalEvidence.decodeStorageC(deferred.record.getString(NativeExternalEvidence.storageField).get))
      deferredevidence.binding.hcursor.downField("candidateRecovery").downField("answer").get[String]("choice").toOption shouldBe Some("Defer")
      prepared.recordIdentity should startWith(R.purpose + ":")
      Given("a crossed session, participant, worker, operation or missing answer reference")
      When("each contradictory description or supplied Human fact is prepared")
      val refused = Vector(P.prepareC(description.copy(sessionId = "foreign-session"), _answer, _reference, _execution, "participant", "test-host", _now),
        P.prepareC(description, _answer, _reference, _execution, "foreign-participant", "test-host", _now),
        P.prepareC(description, _answer, _reference, _execution.copy(workerIdentity = None), "participant", "test-host", _now),
        P.prepareC(description.copy(recoveryOperation = "recoverGoalPhaseSourceHistory"), _answer, _reference, _execution, "participant", "test-host", _now),
        P.prepareC(description, _answer, null, _execution, "participant", "test-host", _now))
      Then("passive conversion refuses every mismatch")
      refused.foreach(_.isSuccess shouldBe false)
    }
  }
}
