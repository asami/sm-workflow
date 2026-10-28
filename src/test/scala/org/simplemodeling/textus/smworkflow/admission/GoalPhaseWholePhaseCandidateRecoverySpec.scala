package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.ConfigurationValue
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.preparation.NativeWholePhaseCandidateRecoveryPreparation as P
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseWholePhaseCandidateRecovery as W
import GoalPhaseCandidateRecovery as R
import GoalPhaseNativeCandidate as C
import GoalPhaseCandidateObservationContinuation as K
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Pure codec and independently supplied fixture evidence; no real invocation or Human proof.
  * @since   Oct. 22, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseWholePhaseCandidateRecoverySpec extends AnyWordSpec with NativeGoalSourceHistoryCliFixture {
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private val _candidate = O.Snapshot(_base.head, "c" * 40)
  private val _commit = O.Commit("d" * 40, _candidate.head, _candidate.tree)
  private val _reference = V.WorkflowArtifactReference("whole-independent-human", "1")
  private val _now = Instant.parse("2026-10-22T00:00:00Z")
  private val _execution = V.WorkflowExecutionEvidence(Vector(_reference), Some("participant"), None, None)
  private def _closed: O.State = {
    val plan = O.Plan("whole-plan", "3", "phase-6.4", "fixture-repository",
      Vector(O.Step("step-1", "Complete the owned Compile Step", Vector("value.txt"), Vector.empty, Vector("compile"))),
      Vector("test"), repairLimit = 2)
    val initial = take(O.startC(plan, _base))
    val ready = take(initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key, "Completed")))
    val observed = take(ready.acceptC(O.CandidateObserved(_candidate)))
    val checked = take(observed.acceptC(O.ChecksObserved(_candidate, Vector("compile"), "compile-receipt", true)))
    val reviewed = take(checked.acceptC(O.ReviewAdmitted(checked.next.asInstanceOf[O.Semantic].key,
      _candidate, "compile-receipt", "independent-step-review", O.ReviewOutcome.Accept)))
    val prepared = take(reviewed.acceptC(O.ClosureIntentSaved(_candidate, "closure")))
    take(prepared.acceptC(O.StepCommitObserved("closure", _commit)))
  }
  private def _target: W.Target = {
    val closed = _closed
    val plan = closed.plan
    val payload = C.Payload("10", "step-1", Vector("value.txt"), Vector("value.txt"),
      Some(_candidate), Vector("value.txt"), Some("actual fixture candidate diff"))
    val inspection = C.Payload("20", "phase:" + plan.phase, Vector.empty, Vector.empty, None, Vector.empty, None)
    W.Target("whole-session", R.controllerSnapshot(closed), "goal-phase", "1", "participant", "20", "21",
      "/private/tmp/whole-fixture-repository", "main", Vector("value.txt"), Vector.empty, None, Vector.empty,
      Vector(payload), "whole-session:ordered-candidate:20:inspection", "1", "21", inspection, Vector.empty,
      "UNKNOWN", "termination-unconfirmed", K.RepositorySnapshot("/private/tmp/whole-fixture-repository", "main",
        _commit.identity, _commit.tree, Vector.empty, _commit.tree),
      W.Policy(GoalPhaseNativeValidation.Policy("phase-test", 1, Vector(GoalPhaseNativeValidation.Check("test", 2))),
        1048576L, Json.obj("sbtPrograms" -> Json.arr(Json.obj("fixture" -> Json.fromString("opaque-host-definition"))))))
  }
  private def _description(t: W.Target): V.GoalPhaseWholePhaseCandidateRecoveryDescriptionResult =
    V.GoalPhaseWholePhaseCandidateRecoveryDescriptionResult(t.sessionId, "decision-required", W.message,
      "component", W.targetRecord(t), W.choices, W.recoveryOperation)
  private def _binding(t: W.Target, choice: String = "AuthorizeNewObservation", ended: Boolean = true,
    rationale: String = "Fresh fixture Human answer"): Json =
    take(W.bindingC("component", t, choice, rationale, "participant",
      ContextReference(_reference.identity, _reference.revision), ended))

  private def _release_target: W.Target = {
    val original = _target
    val paths = Vector(sourcePath, "src/main/scala/Another.scala")
    val plan = original.controller.plan.copy(steps = original.controller.plan.steps.map(_.copy(ownedPaths = paths)))
    val controller = original.controller.copy(plan = plan, generation = 1, repairs = 1, ordinaryRepairs = 1,
      repairDirective = Some(O.RepairDirective("Actual first failed Phase Test", Vector("Selected value differs"))))
    val inspection = original.inspection.copy(selectedPaths = paths)
    def _identity_(key: String) = original.sessionId + ":ordered-candidate:20:" + key
    val history = paths.zipWithIndex.map { (path, index) =>
      val intent = GoalPhaseNativeSourceHistory.Payload("20", inspection.step, path, "goldenport",
        "native-cli-external-goldenport-v1", "2026-10-28", true, None)
      R.HistoryPair(index, _identity_("source-history-intent-" + index), "1", (22 + index * 2).toString,
        _identity_("source-history-result-" + index), "1", (23 + index * 2).toString, intent,
        intent.copy(outcome = Some(if (index == 0) "updated" else "unchanged")))
    }
    val stages = paths.indices.map(index => R.StagePair(index, _identity_("stage-intent-" + index), "1",
      (26 + index * 2).toString, _identity_("stage-result-" + index), "1", (27 + index * 2).toString,
      inspection.copy(stagedPaths = paths.take(index)), inspection.copy(stagedPaths = paths.take(index + 1)))).toVector
    original.copy(controller = controller, candidatePaths = paths, inspection = inspection,
      frontierRevision = "29", completedCandidates = original.completedCandidates.map(_.copy(
        selectedPaths = Vector(sourcePath), stagedPaths = Vector(sourcePath), changedPaths = Vector(sourcePath))),
      stagingPrefix = stages, releasePreparation = Some(W.ReleasePreparation(
        WorkflowSourceHistoryRule.Selection("goldenport", Some(take(WorkflowSourceHistoryRule.decodeC(enabledSettings(ruleKey))))),
        history, CommitHygiene.autoFix)), repository = original.repository.copy(indexTree = "e" * 40,
        dirty = paths.map(path => K.Dirty("M", " ", path, None))))
  }

  private def _directory_target(unselected: Boolean = false, inventory: Boolean = false): W.Target = {
    val original = _release_target
    val owners = Vector("src/main/scala", "build.sbt") ++ Option.when(unselected)("src/test/scala")
    val literals = Option.when(inventory)("support/Allowed.scala").toVector
    val plan = original.controller.plan.copy(steps = original.controller.plan.steps.map(_.copy(ownedPaths = owners)),
      phaseRepairInventory = literals)
    val selected = Vector("src/main/scala", "build.sbt") ++ literals
    val inspection = original.inspection.copy(selectedPaths = selected)
    val historysize = original.releasePreparation.get.historyPrefix.size
    val source = original.sourceRevision.toLong
    def _identity_(key: String) = original.sessionId + ":ordered-candidate:" + source + ":" + key
    val stages = selected.indices.map { index =>
      val revision = source + 2 + historysize * 2 + index * 2
      R.StagePair(index, _identity_("stage-intent-" + index), "1", revision.toString,
        _identity_("stage-result-" + index), "1", (revision + 1).toString,
        inspection.copy(stagedPaths = selected.take(index)), inspection.copy(stagedPaths = selected.take(index + 1)))
    }.toVector
    original.copy(controller = original.controller.copy(plan = plan), candidatePaths = plan.phaseOwnedPaths,
      inspection = inspection, stagingPrefix = stages, frontierRevision = (source + 1 + 2 * (historysize + selected.size)).toString,
      repository = original.repository.copy(dirty = (original.repository.dirty :+ K.Dirty("M", " ", "build.sbt", None)) ++
        literals.map(path => K.Dirty("M", " ", path, None)) :+
        K.Dirty(" ", "M", "src/main/catalog/car/org/simplemodeling/textus/textus-sm-workflow.yaml", None)))
  }

  "Release recovery selected directory ownership" should {
    "round trip exact directory and build selectors while admitting their physical paths and preserving an unrelated unstaged catalog" in {
      Given("P69 directory-owner correction: two retained physical history paths, exact directory/build selectors and an unrelated unstaged catalog")
      val target = _directory_target()
      val physical = Vector(sourcePath, "src/main/scala/Another.scala", "build.sbt")
      val exact = _release_target
      val legacy = _target
      val managementpath = "docs/management.md"
      val managementplan = legacy.controller.plan.copy(steps = legacy.controller.plan.steps.map(step =>
        step.copy(ownedPaths = step.ownedPaths :+ managementpath)))
      val managementinspection = legacy.inspection.copy(selectedPaths = Vector(managementpath))
      val managementstage = R.StagePair(0, "whole-session:ordered-candidate:20:stage-intent-0", "1", "22",
        "whole-session:ordered-candidate:20:stage-result-0", "1", "23", managementinspection,
        managementinspection.copy(stagedPaths = Vector(managementpath)))
      val managed = legacy.copy(controller = legacy.controller.copy(plan = managementplan), frontierRevision = "23",
        candidatePaths = managementplan.phaseOwnedPaths, managementPaths = Vector(managementpath),
        recordingPaths = Vector(managementpath), inspection = managementinspection, stagingPrefix = Vector(managementstage),
        repository = legacy.repository.copy(indexTree = "e" * 40, dirty = Vector(K.Dirty("M", " ", managementpath, None))))
      When("the canonical target and actual physical pending paths are admitted")
      val decoded = W.decodeTarget(target.asJson)
      val pending = W.pendingPathsOwned(target, physical)
      Then("directory coverage uses slash boundaries without changing prefix identities or unstaged evidence")
      decoded shouldBe Right(target)
      take(W.targetC(W.targetRecord(target))) shouldBe target
      W.validTarget(target) shouldBe true
      W.validRepository(target, target.repository) shouldBe true
      target.inspection.selectedPaths shouldBe Vector("src/main/scala", "build.sbt")
      target.releasePreparation.get.historyPrefix shouldBe exact.releasePreparation.get.historyPrefix
      pending shouldBe true
      W.pendingPathsOwned(exact, exact.inspection.selectedPaths) shouldBe true
      W.pendingPathsOwned(legacy, Vector.empty) shouldBe true
      W.pendingPathsOwned(legacy, Vector("build.sbt")) shouldBe false
      W.decodeTarget(managed.asJson) shouldBe Right(managed)
      W.pendingPathsOwned(managed, Vector(managementpath)) shouldBe true
      W.pendingPathsOwned(managed, Vector(managementpath + "/child.md")) shouldBe false
    }

    "refuse prefix siblings unselected owners unrelated staged paths and duplicate pending paths" in {
      Given("P69 directory-owner correction: the Plan also owns a directory absent from the actual selection")
      val target = _directory_target(unselected = true)
      val cases = Vector(Vector("src/main/scala-extra/A.scala"), Vector("src/main/other/A.scala"),
        Vector("src/test/scala/Unselected.scala"),
        Vector("src/main/catalog/car/org/simplemodeling/textus/textus-sm-workflow.yaml"),
        Vector(sourcePath, sourcePath))
      When("physical pending paths are compared with both Plan ownership and the exact staged selection")
      val admitted = cases.map(W.pendingPathsOwned(target, _))
      Then("global ownership alone cannot replace actual selection and duplicate or null vectors refuse")
      W.validTarget(target) shouldBe true
      target.controller.plan.phasePathsOwned(Vector("src/test/scala/Unselected.scala")) shouldBe true
      admitted.foreach(_ shouldBe false)
      W.pendingPathsOwned(target, null) shouldBe false
    }

    "retain literal inventory ownership without extending it to descendants" in {
      Given("P69 directory-owner correction: a selected exact inventory file alongside ordinary directory owners")
      val target = _directory_target(inventory = true)
      When("the literal and a descendant are checked as pending paths")
      val exact = W.pendingPathsOwned(target, Vector("support/Allowed.scala"))
      val descendant = W.pendingPathsOwned(target, Vector("support/Allowed.scala/Child.scala"))
      Then("the valid full target accepts only the literal inventory entry")
      W.decodeTarget(target.asJson) shouldBe Right(target)
      exact shouldBe true
      descendant shouldBe false
      W.validRepository(target, target.repository.copy(dirty = Vector(
        K.Dirty("M", " ", "support/Allowed.scala/Child.scala", None)))) shouldBe false
    }

    "require both rename endpoints to remain owned and selected while keeping the unstaged catalog unchanged" in {
      Given("P69 directory-owner correction: the actual directory target and snapshots describing both rename endpoints")
      val target = _directory_target(unselected = true)
      val oldpath = "src/main/scala/Before.scala"
      val newpath = "src/main/scala/After.scala"
      val catalog = "src/main/catalog/car/org/simplemodeling/textus/textus-sm-workflow.yaml"
      val rename = target.repository.copy(dirty = Vector(K.Dirty("R", " ", newpath, Some(oldpath)), K.Dirty(" ", "M", catalog, None)))
      val crossed = Vector(
        rename.copy(dirty = Vector(K.Dirty("R", " ", newpath, Some("outside/Before.scala")))),
        rename.copy(dirty = Vector(K.Dirty("R", " ", "outside/After.scala", Some(oldpath)))),
        rename.copy(dirty = Vector(K.Dirty("R", " ", "src/main/scala-extra/After.scala", Some(oldpath)))),
        rename.copy(dirty = Vector(K.Dirty("R", " ", newpath, Some("src/main/scala-extra/Before.scala")))),
        rename.copy(dirty = Vector(K.Dirty("R", " ", catalog, Some(oldpath)))),
        rename.copy(dirty = Vector(K.Dirty("M", " ", catalog, None))))
      When("snapshot scope and no-renames physical pending endpoints are checked independently")
      val snapshot = W.validRepository(target, rename)
      val refused = crossed.map(W.validRepository(target, _))
      Then("both endpoints must stay within slash-bounded owners and every pending endpoint must also be selected")
      snapshot shouldBe true
      refused.foreach(_ shouldBe false)
      W.pendingPathsOwned(target, Vector(oldpath, newpath)) shouldBe true
      W.pendingPathsOwned(target, Vector(oldpath, "src/test/scala/OwnedButUnselected.scala")) shouldBe false
      W.pendingPathsOwned(target, Vector("outside/Before.scala", newpath)) shouldBe false
      W.pendingPathsOwned(target, Vector(oldpath, catalog)) shouldBe false
      W.validRepository(target, target.repository) shouldBe true
    }
  }

  "Release recovery exact preparation prefix" should {
    "retain the old absent-option wire and round trip actual release history before staging with unchanged Plan base and counters" in {
      Given("P69-RELEASE-UNKNOWN-01: an old whole target and a repaired all-closed target with actual updated/unchanged history and staging")
      val legacy = _target
      val release = _release_target
      val direction = W.Direction(release, ContextReference(_reference.identity, "1"), "participant", "Fresh fixture authorization", true)
      When("strict public and durable codecs read the respective canonical targets")
      val decoded = W.decodeTarget(release.asJson)
      val durable = W.codec.decode(W.codec.encode(direction))
      Then("the optional release extension is absent on the legacy wire and the complete release prefix remains exact")
      legacy.asJson.hcursor.downField("releasePreparation").focus shouldBe None
      W.decodeTarget(legacy.asJson) shouldBe Right(legacy)
      take(W.targetC(W.targetRecord(legacy))).asJson shouldBe legacy.asJson
      decoded shouldBe Right(release)
      take(W.targetC(W.targetRecord(release))) shouldBe release
      durable shouldBe Right(direction)
      release.controller.plan shouldBe _release_target.controller.plan
      release.controller.base shouldBe legacy.controller.base
      release.controller.tip shouldBe legacy.controller.tip
      release.controller.completed shouldBe legacy.controller.completed
      release.controller.repairs shouldBe 1
      release.controller.ordinaryRepairs shouldBe 1
      release.releasePreparation.get.historyPrefix.map(_.result.outcome) shouldBe Vector(Some("updated"), Some("unchanged"))
      release.stagingPrefix.head.intentRecordRevision shouldBe "26"
      release.oldObservationOutcome shouldBe "UNKNOWN"
      release.oldObservationTermination shouldBe "termination-unconfirmed"
    }

    "refuse crossed identity revision ordering rule incomplete prefixes and nonrelease or noncanonical extension fields" in {
      Given("P69-RELEASE-UNKNOWN-01: the exact release source history and stages before the missing pending-index check")
      val target = _release_target
      val preparation = target.releasePreparation.get
      val first = preparation.historyPrefix.head
      def _history_(pairs: Vector[R.HistoryPair]): W.Target = target.copy(releasePreparation = Some(preparation.copy(historyPrefix = pairs)))
      val crossed = Vector(
        _history_(Vector(first.copy(intentIdentity = "crossed"), preparation.historyPrefix.last)),
        _history_(Vector(first.copy(resultRecordRevision = "24"), preparation.historyPrefix.last)),
        _history_(Vector(first.copy(intent = first.intent.copy(sourceRevision = "19")), preparation.historyPrefix.last)),
        _history_(Vector(first.copy(result = first.result.copy(step = "step-1")), preparation.historyPrefix.last)),
        _history_(Vector(first.copy(intent = first.intent.copy(outcome = Some("updated"))), preparation.historyPrefix.last)),
        _history_(Vector(first.copy(result = first.result.copy(outcome = Some("missing"))), preparation.historyPrefix.last)),
        _history_(preparation.historyPrefix.reverse), _history_(preparation.historyPrefix.take(1)),
        _history_(preparation.historyPrefix.map(p => p.copy(intent = p.intent.copy(path = sourcePath), result = p.result.copy(path = sourcePath)))),
        target.copy(stagingPrefix = target.stagingPrefix.reverse), target.copy(stagingPrefix = target.stagingPrefix.take(1)),
        target.copy(stagingPrefix = target.stagingPrefix.map(p => p.copy(intentRecordRevision = "22"))),
        target.copy(releasePreparation = None),
        target.copy(controller = target.controller.copy(repairs = 0, ordinaryRepairs = 0, repairDirective = None)),
        target.copy(releasePreparation = Some(preparation.copy(hygieneMode = "unsupported"))),
        target.copy(releasePreparation = Some(preparation.copy(historySelection = WorkflowSourceHistoryRule.Selection("none", None)))),
        target.copy(releasePreparation = Some(preparation.copy(
          historySelection = preparation.historySelection.copy(
            rule = preparation.historySelection.rule.map(_.copy(identity = "crossed-provider"))
          )))),
        target.copy(releasePreparation = Some(preparation.copy(
          historySelection = preparation.historySelection.copy(
            rule = preparation.historySelection.rule.map(_.copy(sourceRoots = Vector("other")))
          ))))
      )
      val wire = target.asJson
      val extension = wire.hcursor.downField("releasePreparation").focus.get
      val malformed = Vector(wire.mapObject(_.add("releasePreparation", Json.Null)),
        wire.mapObject(_.add("releasePreparation", extension.mapObject(_.remove("historyPrefix")))),
        wire.mapObject(_.add("releasePreparation", extension.mapObject(_.add("laterHygiene", Json.True)))),
        _target.asJson.mapObject(_.add("historyPrefix", Json.arr())),
        _target.asJson.mapObject(_.add("releasePreparation", Json.Null)))
      When("each tampered target is decoded using the unchanged canonical rejection boundary")
      val refused = crossed.map(t => W.decodeTarget(t.asJson)) ++ malformed.map(W.decodeTarget)
      Then("no partial crossed nonrelease or unknown shape grants recovery")
      refused.foreach(_.isLeft shouldBe true)
      W.decodeTarget(wire) shouldBe Right(target)
    }
  }

  "Whole-Phase recovery E1 original all-closed empty selection" should afterWord(
    "in spec:goal-phase-whole-phase-candidate-recovery, example:E1, rules:R1,R3,R4, phase:6.4") {
    "round trip the distinct full target and strict direction while preserving controller and completed facts" in {
      Given("SM-WHOLE-CAND-01 R1: every ordered Step closed and the original whole inspection selected no management paths")
      val target = _target
      val direction = W.Direction(target, ContextReference(_reference.identity, "1"), "participant", "One fresh remaining observation", true)
      val wire = W.codec.encode(direction)
      When("the whole-specific public target and retained v1 direction are decoded")
      val decoded = take(W.targetC(W.targetRecord(target)))
      val saved = W.codec.decode(wire)
      Then("the exact Plan revision base counters original UNKNOWN and completed candidate/commit remain bound")
      W.validTarget(target) shouldBe true
      decoded shouldBe target
      saved shouldBe Right(direction)
      decoded.controller.plan.revision shouldBe "3"
      decoded.controller.base shouldBe _base
      decoded.controller.tip shouldBe O.Snapshot(_commit.identity, _commit.tree)
      decoded.controller.completed.map(_.commit) shouldBe Vector(_commit)
      decoded.controller.repairs shouldBe 0
      decoded.controller.ordinaryRepairs shouldBe 0
      decoded.inspection.selectedPaths shouldBe empty
      decoded.stagingPrefix shouldBe empty
      decoded.oldObservationOutcome shouldBe "UNKNOWN"
      decoded.oldObservationTermination shouldBe "termination-unconfirmed"
      W.codec.decode(wire.mapObject(_.add("extra", Json.True))).isLeft shouldBe true
      W.decodeTarget(target.asJson.mapObject(_.add("extra", Json.True))).isLeft shouldBe true
      W.decodeTarget(target.asJson.mapObject(_.add("extra", Json.Null))).isLeft shouldBe true
      val controllerwire = target.asJson.hcursor.downField("controller").focus.get
      val invalidcontrollers = Vector(
        controllerwire.mapObject(_.add("extra", Json.True)),
        controllerwire.mapObject(_.add("candidate", Json.Null)),
        controllerwire.mapObject(_.remove("stage")))
      invalidcontrollers.foreach(value => W.decodeTarget(target.asJson.mapObject(_.add("controller", value))).isLeft shouldBe true)
      W.codec.decode(wire.mapObject(_.add("schemaVersion", Json.fromString("selected-step-v1")))).isLeft shouldBe true
      W.codec.decode(W.codec.encode(direction.copy(priorExecutionEnded = false))).isLeft shouldBe true
      R.decodeTarget(target.asJson).isLeft shouldBe true
    }
  }

  "Whole-Phase recovery E2 prefix and snapshot refusal" should afterWord(
    "in spec:goal-phase-whole-phase-candidate-recovery, example:E2, rules:R1,R3, phase:6.4") {
    "refuse selected-Step incomplete gapped crossed and malformed full targets" in {
      Given("SM-WHOLE-CAND-01 R1: the exact pure all-closed target and its pinned repository/index")
      val target = _target
      val changed = Vector(target.copy(controller = target.controller.copy(stage = "InspectCandidate")),
        target.copy(controller = target.controller.copy(cursor = 0)),
        target.copy(controller = target.controller.copy(completed = Vector.empty)),
        target.copy(controller = target.controller.copy(checksReceipt = Some("late-check"))),
        target.copy(frontierRevision = "22"), target.copy(inspectionRecordRevision = "22"),
        target.copy(sourceRevision = "020"), target.copy(inspectionIdentity = "crossed-inspection"),
        target.copy(inspection = target.inspection.copy(step = "step-1")),
        target.copy(inspection = target.inspection.copy(selectedPaths = Vector("foreign.txt"))),
        target.copy(inspection = target.inspection.copy(candidate = Some(_candidate))),
        target.copy(completedCandidates = Vector.empty),
        target.copy(completedCandidates = target.completedCandidates.map(_.copy(step = "crossed-step"))),
        target.copy(completedCandidates = target.completedCandidates.map(_.copy(candidate = Some(_base.copy(head = "e" * 40))))),
        target.copy(repository = target.repository.copy(head = "e" * 40)),
        target.copy(repository = target.repository.copy(indexTree = "invalid-object")),
        target.copy(repository = target.repository.copy(dirty = Vector(K.Dirty("M", " ", "foreign.txt", None)))),
        target.copy(policy = target.policy.copy(maximumDiffBytes = 0)),
        target.copy(policy = target.policy.copy(processPlan = Json.arr())),
        target.copy(policy = target.policy.copy(processPlan = Json.obj())),
        target.copy(oldObservationOutcome = "PASS"))
      When("each changed prefix or snapshot is decoded under the separate whole-Phase contract")
      val refused = changed.map(t => W.decodeTarget(t.asJson))
      Then("no changed target can authorize a remaining observation")
      refused.foreach(_.isLeft shouldBe true)
      W.validTarget(target.copy(controller = null)) shouldBe false
      W.configurationJson(ConfigurationValue.NullValue) shouldBe Right(Json.Null)
      W.configurationJson(ConfigurationValue.StringValue("opaque text")) shouldBe Right(Json.fromString("opaque text"))

      Given("R1: management recording has one fully completed adjacent staging pair after the original inspection")
      val path = "docs/management.md"
      val inspection = target.inspection.copy(selectedPaths = Vector(path))
      val pair = R.StagePair(0, "whole-session:ordered-candidate:20:stage-intent-0", "1", "22",
        "whole-session:ordered-candidate:20:stage-result-0", "1", "23", inspection,
        inspection.copy(stagedPaths = Vector(path)))
      val plan = target.controller.plan.copy(steps = target.controller.plan.steps.map(s => s.copy(ownedPaths = s.ownedPaths :+ path)))
      val managed = target.copy(controller = target.controller.copy(plan = plan), frontierRevision = "23",
        candidatePaths = target.candidatePaths :+ path, managementPaths = Vector(path), recordingPaths = Vector(path),
        inspection = inspection, stagingPrefix = Vector(pair), repository = target.repository.copy(indexTree = "e" * 40,
          dirty = Vector(K.Dirty("M", " ", path, None))))
      When("the completed pair and its gapped incomplete or unsuccessful variants are decoded")
      val valid = W.decodeTarget(managed.asJson)
      val invalid = Vector(managed.copy(stagingPrefix = Vector.empty),
        managed.copy(stagingPrefix = Vector(pair.copy(resultRecordRevision = "24"))),
        managed.copy(stagingPrefix = Vector(pair.copy(result = inspection))),
        managed.copy(stagingPrefix = Vector(pair.copy(intentIdentity = "crossed-intent"))),
        managed.copy(repository = managed.repository.copy(dirty = Vector(K.Dirty("M", "M", path, None)))))
      Then("only the complete successful adjacent prefix permits the remaining observation")
      valid shouldBe Right(managed)
      val dirtywire = managed.asJson.hcursor.downField("repository").downField("dirty").focus.get.asArray.get.head
      val invaliddirty = Vector(dirtywire.mapObject(_.add("renamedFrom", Json.Null)),
        dirtywire.mapObject(_.add("extra", Json.True)), dirtywire.mapObject(_.remove("path")))
      invaliddirty.foreach(value => {
        val changed = managed.asJson.mapObject(_.add("repository", managed.asJson.hcursor.downField("repository").focus.get
          .mapObject(_.add("dirty", Json.arr(value)))))
        W.decodeTarget(changed).isLeft shouldBe true
      })
      invalid.foreach(t => W.decodeTarget(t.asJson).isLeft shouldBe true)
      W.decodeTarget(managed.asJson.mapObject(_.add("historyPrefix", Json.arr()))).isLeft shouldBe true
    }
  }

  "Whole-Phase recovery E3 independent evidence and ended provenance" should afterWord(
    "in spec:goal-phase-whole-phase-candidate-recovery, example:E3, rules:R2,R3, phase:6.4") {
    "require exact current target component participant reference policy and affirmative ended attestation" in {
      Given("SM-WHOLE-CAND-01 R2: separately supplied fixture evidence; it is no real Human or terminal process proof")
      val target = _target
      val binding = _binding(target)
      val purpose = NativeExternalEvidence.Purpose.WholePhaseCandidateRecovery
      val declaration = NativeExternalEvidence.Declaration("workflow", DataStore.CollectionId.EntityStore(
        EntityCollectionId("smworkflow", "cli", "host_evidence")), "fixture-independent-issuer")
      val evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, purpose.identity, "lookup",
        _reference.identity, _reference.revision, declaration.issuer, "participant", binding, _now.toString)
      val changedpolicy = target.copy(policy = target.policy.copy(processPlan = Json.obj("sbtPrograms" -> Json.arr("changed-host".asJson))))
      val crossed = Vector(_binding(target.copy(controller = target.controller.copy(generation = target.controller.generation + 1))),
        _binding(changedpolicy), _binding(target.copy(policy = target.policy.copy(maximumDiffBytes = target.policy.maximumDiffBytes - 1))),
        _binding(target.copy(policy = target.policy.copy(phaseValidation = target.policy.phaseValidation.copy(revision = 2)))),
        _binding(target.copy(repository = target.repository.copy(indexTree = "f" * 40))),
        binding.mapObject(_.add("componentInstance", "crossed-component".asJson)))
      When("the independent reader admits exact evidence and compares every crossed or stale binding")
      val admitted = NativeExternalEvidence.observeC(evidence, declaration, purpose, "lookup", binding, _now)
      val refused = crossed.map(NativeExternalEvidence.observeC(evidence, declaration, purpose, "lookup", _, _now))
      Then("only the exact binding admits and ended=false authorization cannot even form a binding")
      admitted.isSuccess shouldBe true
      refused.foreach(_.isSuccess shouldBe false)
      W.bindingC("component", target, "AuthorizeNewObservation", "Fresh Human", "participant",
        ContextReference(_reference.identity, "1"), false).isSuccess shouldBe false
      W.bindingC("component", target, "AuthorizeNewObservation", "Fresh Human", "another-participant",
        ContextReference(_reference.identity, "1"), true).isSuccess shouldBe false
      NativeExternalEvidence.observeC(evidence.copy(reference = "crossed"), declaration, purpose, "lookup", binding, _now).isSuccess shouldBe false
      NativeExternalEvidence.observeC(evidence, declaration, NativeExternalEvidence.Purpose.CandidateRecovery, "lookup", binding, _now).isSuccess shouldBe false
      W.selectBinding(binding.mapObject(_.add("extra", Json.True))).isLeft shouldBe true
      W.selectBinding(Json.obj()).isLeft shouldBe true
    }
  }

  "Whole-Phase recovery E6 source after normal document progress" should afterWord(
    "in spec:goal-phase-whole-phase-candidate-recovery, example:E6, rules:R1,R3, phase:6.4") {
    "admit all-closed PhaseInspection sources before and after pure native progress appends while refusing other or suspended sources" in {
      Given("CB-SM-WHOLE-CAND-02 R1: pure native history/source admission inputs, without actual document File/provider or prior-process proof")
      val controller = _closed
      val initial = take(InstanceRecord(InstanceIdentity("whole-source-admission"),
        take(GoalPhaseRuntimeCatalog.loadC()).definition, initialRevision, Lifecycle.NotStarted, None,
        CorrelationReference("whole-source-correlation"), CausationReference("whole-source-start"),
        None, None, None, Vector.empty).validateC)
      def _append_(from: InstanceRecord, stage: String, cause: String): InstanceRecord =
        take(from.appendC(from.revision, GoalPhaseNativeRun.history(from, "phase." + stage, cause)))
      val committed = _append_(initial, "PhaseInspection", "ordered-step-closure:committed")
      val intent = _append_(committed, "PhaseInspection", "ordered-document-progress-intent")
      val completed = _append_(intent, "PhaseInspection", "ordered-document-progress-result")
      val before = completed.history
      val other = _append_(completed, "PhaseValidation", "ordered-candidate:candidate")
      val incomplete = take(O.startC(controller.plan, _base))
      val suspendedentry = GoalPhaseNativeRun.history(completed, "phase.PhaseInspection", "whole-source-issued").copy(
        suspension = Some(SuspensionBoundary(ContinuationIdentity("whole-source-work"), completed.revision.next,
          ContextSnapshotReference("whole-source-context"), CompletionReference("whole-source-completion"),
          EvidenceReference("whole-source-evidence"), completed.correlation)))
      val suspended = take(completed.appendC(completed.revision, suspendedentry))
      When("source admission checks the actual pure append records and the same all-closed controller")
      val admitted = Vector(W.originalSourceC(controller, committed), W.originalSourceC(controller, completed))
      val refused = Vector(W.originalSourceC(controller, other), W.originalSourceC(incomplete, completed),
        W.originalSourceC(controller, suspended), W.originalSourceC(null, completed), W.originalSourceC(controller, null))
      val prefix = take(GoalPhaseNativeHistory.prefixC(completed, committed.revision))
      Then("document progress does not invalidate the original whole scope and exact history remains unchanged while invalid sources refuse")
      admitted.foreach(_.isSuccess shouldBe true)
      refused.foreach(_.isSuccess shouldBe false)
      prefix shouldBe committed
      prefix.history shouldBe committed.history
      take(GoalPhaseNativeHistory.prefixC(completed, intent.revision)) shouldBe intent
      completed.history shouldBe before
      completed.history.dropRight(2) shouldBe committed.history
      completed.history.map(_.causation.value) shouldBe Vector("ordered-step-closure:committed",
        "ordered-document-progress-intent", "ordered-document-progress-result")
      R.controllerSnapshot(controller) shouldBe _target.controller
      controller.completed.map(_.commit) shouldBe Vector(_commit)
      controller.cursor shouldBe controller.plan.steps.size
      suspended.suspension shouldBe suspendedentry.suspension
    }
  }

  "Whole-Phase recovery E4 passive preparation and Defer" should afterWord(
    "in spec:goal-phase-whole-phase-candidate-recovery, example:E4, rules:R2,R3, phase:6.4") {
    "serialize only exact independently supplied facts and admit Defer without an ended assertion" in {
      Given("SM-WHOLE-CAND-01 R2: a passive description and fixture-owned explicit Human answer")
      val target = _target
      val description = _description(target)
      val defer = V.GoalPhaseWholePhaseCandidateRecoveryAnswer("Defer", "Wait for real terminal evidence", false)
      val denied = V.GoalPhaseWholePhaseCandidateRecoveryAnswer("AuthorizeNewObservation", "No ended evidence", false)
      When("the passive helper receives Defer and an authorization missing the required affirmative attestation")
      val prepared = P.prepareC(description, defer, _reference, _execution, "participant", "issuer", _now)
      val refused = P.prepareC(description, denied, _reference, _execution, "participant", "issuer", _now)
      Then("Defer yields only an external record value while authorization without ended evidence refuses")
      prepared.isSuccess shouldBe true
      refused.isSuccess shouldBe false
      val row = take(prepared)
      val evidence = take(NativeExternalEvidence.decodeStorageC(row.record.getString(NativeExternalEvidence.storageField).get))
      evidence.binding shouldBe _binding(target, "Defer", false, defer.rationale)
      evidence.purpose shouldBe W.purpose
      P.prepareC(description.copy(sessionId = "crossed"), defer, _reference, _execution, "participant", "issuer", _now).isSuccess shouldBe false
      P.prepareC(description, defer, _reference, _execution.copy(workerIdentity = Some("another-worker")),
        "participant", "issuer", _now).isSuccess shouldBe false
      W.message should include("UNKNOWN")
      W.message should include("write-tree")
      W.message should include("Boolean alone")
    }
  }
}
