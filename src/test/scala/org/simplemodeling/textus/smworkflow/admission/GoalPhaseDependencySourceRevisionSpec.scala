package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.processexecution.{GitObjectId, GitPath, GitTreeChange}
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseDependencySourceRevision as R
import SplitPhaseExplicitPartitionFixture.take

private[admission] object DependencySourceRevisionSpecificationFixture {
  import DependencyValidationSpecificationFixture as F
  val binding = F.binding.copy(revision = 2, root = "/specification/workspaces/current-cncf",
    dependency = F.dependency.copy(workspace = "cncf-current-source"),
    base = GoalPhaseOrderedProgression.Snapshot("1" * 40, "2" * 40))
  val before = R.Guard(binding.root, binding.branch, binding.base.head, binding.base.tree, binding.base.tree,
    Vector.empty, binding.paths.map(p => R.fileIdentity(p, Some("expected\n".getBytes.toVector))),
    Vector(R.fileIdentity("docs/unrelated.md", None)), Vector.empty, Vector.empty)
  val proposal = R.Proposal(R.schema, ContextReference(binding.identity, "2"), "committed", Vector("docs/unrelated.md"),
    F.approvals.map(a => R.Coverage(a.decision, a.proposal.acceptance, binding.checks.map(_.identity))),
    R.Consumption(F.requester.workspace, binding.root, "fixture-image", ContextReference("fixture-manifest", "1"),
      Vector("docs/unrelated.md"), binding.paths.map(R.ExpectedMode(_, "100644")),
      Vector(R.CheckProgram("primary-test", "primary-validation", "test", "fixture-primary-check"))))
  val old = before.copy(root = F.binding.root, head = F.binding.base.head, committedTree = F.binding.base.tree,
    indexTree = F.capture.candidate.tree, excludedFiles = Vector.empty)
  val target = R.Target(R.schema, "same-session", "fixture-source-question", "18", "19", F.plan,
    F.binding.step, 1, F.primaryBase, Vector.empty,
    GoalPhaseRepairProgress.Snapshot(0, 0, F.plan.repairLimit), ContextReference("fixture-request", "1"),
    F.admission, Vector.empty, proposal, binding, old, before)
  val direction = R.Direction(target, ContextReference("fixture-independent-direction", "1"), "participant", "Fixture migration")
  val capture = R.Capture(R.schema, "same-session", ContextReference(F.plan.identity, F.plan.revision), binding.step,
    2, "25", F.primaryBase, direction, before, Vector.empty, before)
}

/** Fresh pure source-selection fixtures do not claim actual Human evidence.
  * @since   Oct. 26, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseDependencySourceRevisionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val selected = "src/main/scala/Component.scala"
  private val excluded = "docs/notes/unrelated.md"
  private def bytes(value: String) = Some(value.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector)
  private val source = R.fileIdentity(selected, bytes("object Component\n"))
  private val unrelated = R.fileIdentity(excluded, bytes("Unrelated owner work\n"))
  private val before = R.Guard("/fixture/canonical", "main", "a" * 40, "b" * 40, "c" * 40,
    Vector(R.Dirty(" ", "M", selected, None), R.Dirty("A", " ", excluded, None)),
    Vector(source), Vector(unrelated), Vector.empty, Vector(R.Entry(excluded, "100644", "d" * 40)))
  private val entry = R.Entry(selected, "100644", "e" * 40)
  private val staged = before.copy(indexTree = "f" * 40,
    dirty = Vector(R.Dirty("M", " ", selected, None), before.dirty.last), selectedEntries = Vector(entry))
  private val change = GitTreeChange("100644", "100644", Some(take(GitObjectId.parseC("1" * 40))),
    Some(take(GitObjectId.parseC(entry.blob))), "M", take(GitPath.parseC(selected)))
  private val proposal = R.Proposal(R.schema, ContextReference("cncf-source", "2"), "selected-changes",
    Vector(excluded), Vector(R.Coverage(ContextReference("actual-saved-approval", "1"),
      Vector("Preserve inherited API behavior"), Vector("cncf-compile"))),
    R.Consumption("primary-validation", before.root, "fixture-image", ContextReference("fixture-manifest", "1"),
      Vector(excluded), Vector(R.ExpectedMode(selected, "100644")),
      Vector(R.CheckProgram("primary-test", "primary-validation", "test", "fixture-primary-check"))))

  "Dependency source byte guards" should {
    "distinguish same-status unstaged bytes and absence from an empty regular file" in {
      Given("a changed source with the same Git status before and after another edit")
      val changed = R.fileIdentity(selected, bytes("object Replaced\n"))
      When("the actual managed reader's byte vectors become identity metadata")
      source should not equal changed
      R.fileIdentity(selected, None) should not equal R.fileIdentity(selected, bytes(""))
      Then("the identities retain actual presence and bytes without normalizing text")
      R.fileValid(source) shouldBe true
      R.fileValid(R.fileIdentity(selected, None)) shouldBe true
      R.fileIdentity(selected, bytes("x\r\n")) should not equal R.fileIdentity(selected, bytes("x\n"))
    }

    "accept an attributable selected staging transition while preserving unrelated staged work" in {
      Given("one selected source and an unrelated staged document in the same index")
      When("native TreeChanges reports exactly the retained selected stage effect")
      val allowed = R.stageTransitionValid(before, staged, selected, Vector(change))
      Then("only the selected entry changes; the excluded document bytes and index entry remain")
      allowed shouldBe true
      staged.excludedFiles shouldBe before.excludedFiles
      staged.excludedEntries shouldBe before.excludedEntries
    }

    "refuse a same-status selected byte change during staging" in {
      Given("a correctly shaped stage result plus an independent source edit")
      val drift = staged.copy(selectedFiles = Vector(R.fileIdentity(selected, bytes("changed again\n"))))
      When("the post-stage guard is checked against adopted bytes")
      val allowed = R.stageTransitionValid(before, drift, selected, Vector(change))
      Then("a retained stage result cannot authorize the independent edit")
      allowed shouldBe false
    }

    "refuse excluded byte or index-entry changes without adding them to source ownership" in {
      Given("the same selected stage operation and excluded document inventory")
      val changedBytes = staged.copy(excludedFiles = Vector(R.fileIdentity(excluded, bytes("other edit\n"))))
      val changedIndex = staged.copy(excludedEntries = Vector(R.Entry(excluded, "100644", "9" * 40)))
      When("either excluded content or its staged blob has changed")
      val actual = Vector(changedBytes, changedIndex).map(R.stageTransitionValid(before, _, selected, Vector(change)))
      Then("both transitions refuse even though the selected stage result is otherwise valid")
      actual shouldBe Vector(false, false)
    }

    "refuse a changed index without its matching native tree change" in {
      Given("post-stage index movement and an absent native result")
      When("no actual selected tree change is supplied")
      val changed = R.stageTransitionValid(before, staged, selected, Vector.empty)
      val unchanged = R.stageTransitionValid(before, before, selected, Vector.empty)
      Then("the operation cannot treat an unknown effect as an allowed transition")
      changed shouldBe false
      unchanged shouldBe true
    }

    "refuse unrelated or contradictory selected tree changes" in {
      Given("a real selected source transition and an unrelated tree-change path")
      val other = change.copy(path = take(GitPath.parseC(excluded)))
      When("the retained result differs from the selected path or actual entry")
      val unrelated = R.stageTransitionValid(before, staged, selected, Vector(other))
      val substituted = R.stageTransitionValid(before, staged.copy(selectedEntries = Vector(entry.copy(blob = "8" * 40))),
        selected, Vector(change))
      Then("neither extra scope nor a substituted selected blob is allowed")
      unrelated shouldBe false
      substituted shouldBe false
    }
  }

  "Versioned source selection input" should {
    "read public targets and Git changes when Record projection omits absent optional fields" in {
      val fixture = DependencySourceRevisionSpecificationFixture
      val dirty = R.Dirty(" ", "M", fixture.binding.paths.head, None)
      val target = fixture.target.copy(proposal = fixture.proposal.copy(mode = "selected-changes"),
        preStageGuard = fixture.before.copy(dirty = Vector(dirty)))
      Given("a valid selected-source target with a non-renamed file and an added Git blob")
      R.targetValid(target) shouldBe true
      val added = R.StageChange("000000", "100644", None, Some("8" * 40), "A", dirty.path)
      def public(value: Json): Json = {
        val raw = take(GoalPhaseNativeMetadata.record(value).toJsonStringC)
        val parsed = io.circe.parser.parse(raw).fold(error => fail(error.message), identity)
        GoalPhaseNativeMetadata.normalizePublicJson(parsed).fold(reason => fail(reason), identity)
      }
      When("the ordinary Record projection omits None fields and the exact domain readers consume it")
      val publicTarget = R.targetRecord(target)
      val publicChange = public(added.asJson)
      Then("the target and change retain their meaning without admitting unknown or missing required fields")
      take(R.targetC(publicTarget)) shouldBe target
      publicChange.hcursor.downField("oldBlob").focus shouldBe None
      publicChange.as[R.StageChange] shouldBe Right(added)
      publicChange.mapObject(_.add("unexpected", Json.fromString("extra"))).as[R.StageChange].isLeft shouldBe true
      publicChange.mapObject(_.remove("path")).as[R.StageChange].isLeft shouldBe true
    }

    "reject a same-byte executable-mode substitution through an otherwise attributable stage result" in {
      Given("a declared non-executable source mode and an actual native 100644 to 100755 transition")
      val fixture = DependencySourceRevisionSpecificationFixture
      val path = fixture.binding.paths.head
      val after = fixture.before.copy(indexTree = "9" * 40,
        dirty = Vector(R.Dirty("M", " ", path, None)), selectedEntries = Vector(R.Entry(path, "100755", "8" * 40)))
      val fact = R.StageFact(path, fixture.before, after,
        Vector(R.StageChange("100644", "100755", Some("7" * 40), Some("8" * 40), "M", path)))
      When("the direction compares the actual new mode with the independent expected mode")
      val attributable = R.stageFactValid(fact)
      val allowed = R.stageModeValid(fixture.direction, fact)
      Then("attribution alone cannot authorize a different executable mode")
      attributable shouldBe true
      allowed shouldBe false
    }

    "reject an already-staged executable substitution when a later repair stages no changes" in {
      Given("a later selected-source repair whose index already contains an undeclared executable mode")
      val fixture = DependencySourceRevisionSpecificationFixture
      val path = fixture.binding.paths.head
      val direction = fixture.direction.copy(target = fixture.target.copy(
        proposal = fixture.proposal.copy(mode = "selected-changes")))
      val guard = fixture.before.copy(indexTree = "9" * 40,
        dirty = Vector(R.Dirty("M", " ", path, None)),
        selectedEntries = Vector(R.Entry(path, "100755", "8" * 40)))
      val fact = R.StageFact(path, guard, guard, Vector.empty)
      val capture = fixture.capture.copy(direction = direction, generation = 3,
        preStageGuard = guard, postStageGuard = guard, stagedPaths = fixture.binding.paths)
      When("the later-generation capture, unchanged stage result and retained capture are validated")
      val attributable = R.stageFactValid(fact)
      val baseAllowed = R.captureBaseValid(direction, 3, guard)
      val stageAllowed = R.stageModeValid(direction, fact)
      val retained = capture.asJson.as[R.Capture]
      Then("a no-op staging result cannot authorize an already-staged executable mode")
      attributable shouldBe true
      baseAllowed shouldBe false
      stageAllowed shouldBe false
      retained.isLeft shouldBe true
    }

    "bind actual primary target and program receipts to the declared source-consumption route" in {
      Given("a fixture source capture and fixed primary check program")
      import org.goldenport.cncf.processexecution.*
      import org.goldenport.cncf.workspace.WorkspaceId
      val fixture = DependencySourceRevisionSpecificationFixture
      val check = GoalPhaseNativeValidation.Check("primary-test", 1)
      val selected = take(GoalPhaseNativeDependencyValidation.checksC(Vector(check), None, Some(fixture.capture)))
      val payload = GoalPhaseNativeValidation.Payload("26", fixture.binding.step, fixture.target.primaryBase,
        selected, Vector.empty, None, dependencySource = Some(fixture.capture), sourceConsumption = Some(fixture.proposal.consumption))
      val output = ProcessExecutionCapture(Vector.empty, 0L, false)
      def observed(workspace: String, program: String): SbtExecutionAttempt = SbtExecutionAttempt.Observed(
        SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC(workspace))), SbtOperation.Test,
          ProcessExecutionResult(ProcessExecutionTermination.Exited(0), output, output, Vector.empty, 1L, program)))
      When("the existing validation owner receives the declared route, an old workspace, or a substituted program")
      val accepted = GoalPhaseNativeValidation.attemptPayloadC(payload, check, 0, observed("primary-validation", "fixture-primary-check"))
      val crossed = Vector(GoalPhaseNativeValidation.attemptPayloadC(payload, check, 0, observed("old-primary", "fixture-primary-check")),
        GoalPhaseNativeValidation.attemptPayloadC(payload, check, 0, observed("primary-validation", "old-primary-program")))
      Then("only the matching actual receipt is retained and no primary execution is relabelled as a dependency check")
      accepted.isSuccess shouldBe true
      take(accepted).dependencyExecutions shouldBe empty
      crossed.map(_.isSuccess) shouldBe Vector(false, false)
    }

    "route distinct dependency checks through their exact declared workspaces and programs" in {
      Given("a revised source with separate compile and test programs and unchanged inherited acceptance")
      import org.goldenport.cncf.processexecution.*
      import org.goldenport.cncf.workspace.WorkspaceId
      import GoalPhaseNativeValidation as V
      val fixture = DependencySourceRevisionSpecificationFixture
      val compile = fixture.binding.checks.head
      val test = V.Check("cncf-contracts", 3, WorkflowSbtValidation.Kind.Test)
      val binding = fixture.binding.copy(checks = Vector(compile, test))
      val programs = Vector(R.CheckProgram(compile.identity, "cncf-compile-route", "compile", "cncf-compile-program"),
        R.CheckProgram(test.identity, "cncf-test-route", "test", "cncf-test-program"))
      val proposal = fixture.proposal.copy(consumption = fixture.proposal.consumption.copy(
        programs = fixture.proposal.consumption.programs ++ programs),
        coverage = fixture.proposal.coverage.map(_.copy(checks = binding.checks.map(_.identity))))
      val target = fixture.target.copy(binding = binding, proposal = proposal)
      val capture = fixture.capture.copy(direction = fixture.direction.copy(target = target))
      val primary = V.Check("primary-test", 1)
      val checks = take(GoalPhaseNativeDependencyValidation.checksC(Vector(primary), None, Some(capture)))
      val payload = V.Payload("26", binding.step, fixture.target.primaryBase, checks, Vector.empty, None,
        dependencySource = Some(capture), sourceConsumption = Some(proposal.consumption))
      val empty = ProcessExecutionCapture(Vector.empty, 0L, false)
      def actual(workspace: String, kind: SbtOperation, program: String,
        termination: ProcessExecutionTermination = ProcessExecutionTermination.Exited(0)) = SbtExecutionAttempt.Observed(
        SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC(workspace))), kind,
          ProcessExecutionResult(termination, empty, empty, Vector.empty,
            if (termination == ProcessExecutionTermination.NotStarted) 0L else 1L, program)))
      import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
      val emptyRecord = take(InstanceRecord(InstanceIdentity(fixture.target.sessionId),
        take(GoalPhaseRuntimeCatalog.loadC()).definition, initialRevision, Lifecycle.NotStarted, None,
        CorrelationReference("source-route-unit"), CausationReference("source-route-start"),
        None, None, None, Vector.empty).validateC)
      def append(from: InstanceRecord, cause: String): InstanceRecord =
        take(from.appendC(from.revision, GoalPhaseNativeRun.history(from, "phase.Validation", cause)))
      val record = (1 to 26).foldLeft(emptyRecord)((previous, index) => append(previous, "fixture-validation-" + index))
      When("normal validation consumes the exact target and program for each check")
      val first = take(V.attemptPayloadC(payload, checks.head, 0,
        actual("primary-validation", SbtOperation.Test, "fixture-primary-check")))
      val second = take(V.attemptPayloadC(first, checks(1), 0,
        actual("cncf-compile-route", SbtOperation.Compile, "cncf-compile-program")))
      val completed = take(V.attemptPayloadC(second, checks(2), 0,
        actual("cncf-test-route", SbtOperation.Test, "cncf-test-program")))
      val wrongTarget = V.attemptPayloadC(first, checks(1), 0,
        actual(binding.dependency.workspace, SbtOperation.Compile, "cncf-compile-program"))
      val wrongProgram = V.attemptPayloadC(first, checks(1), 0,
        actual("cncf-compile-route", SbtOperation.Compile, "old-program"))
      val decoded = completed.asJson.as[V.Payload]
      val prior = V.Saved(record, first)
      val intent = V.Saved(append(record, "ordered-validation:check-intent-1"), first)
      val result = V.Saved(append(intent.record, "ordered-validation:check-result-1"), second)
      val reconciled = V.reconcileAttemptsC(prior, payload, 1, Vector(V.Attempt(Some(intent), Some(result))))
      val stopped = take(V.attemptPayloadC(first, checks(1), 0,
        actual("cncf-compile-route", SbtOperation.Compile, "cncf-compile-program", ProcessExecutionTermination.NotStarted)))
      val stoppedResult = result.copy(payload = stopped)
      val stoppedProgress = V.reconcileAttemptsC(prior, payload, 1,
        Vector(V.Attempt(Some(intent), Some(stoppedResult))))
      val partial = target.copy(proposal = proposal.copy(consumption = proposal.consumption.copy(
        programs = proposal.consumption.programs.filterNot(_.check == test.identity))))
      val partialAllowed = R.targetValid(partial)
      Then("both actual routes survive retained validation and crossed or incomplete mappings refuse")
      completed.dependencyExecutions.map(_.workspace) shouldBe Vector("cncf-compile-route", "cncf-test-route")
      wrongTarget.isSuccess shouldBe false
      wrongProgram.isSuccess shouldBe false
      decoded shouldBe Right(completed)
      take(reconciled).saved.payload shouldBe second
      take(reconciled).nextAttempt shouldBe None
      take(stoppedProgress).saved.payload shouldBe stopped
      take(stoppedProgress).nextAttempt shouldBe Some(1)
      partialAllowed shouldBe false
    }

    "refuse caller-selected exclusions and executable modes outside the independent host declaration" in {
      Given("a valid target with independently declared non-input documents and regular source modes")
      val fixture = DependencySourceRevisionSpecificationFixture
      val foreign = fixture.target.copy(proposal = fixture.proposal.copy(excludedPaths = Vector("build.sbt")))
      val substituted = fixture.target.copy(preStageGuard = fixture.before.copy(selectedEntries =
        Vector(R.Entry(fixture.binding.paths.head, "100755", "8" * 40))))
      When("the target decoder checks either a caller-added exclusion or a conflicting staged mode")
      val outcomes = Vector(foreign, substituted).map(_.asJson.as[R.Target].isLeft)
      Then("neither independent policy nor an executable mode is inferred from caller filenames")
      outcomes shouldBe Vector(true, true)
    }

    "authenticate revised-source recovery facts without importing their capture into a new generation" in {
      Given("an isolated completed primary stage followed by an actual-shaped committed source prefix")
      import GoalPhaseCandidateRecovery as Recovery
      import GoalPhaseNativeCandidate as C
      import GoalPhaseOrderedProgression as O
      val fixture = DependencySourceRevisionSpecificationFixture
      val started = take(O.startC(fixture.target.plan, fixture.target.primaryBase))
      val first = take(started.acceptC(O.SemanticCompleted(started.next.asInstanceOf[O.Semantic].key,
        "Specification implementation")))
      val revised = take(first.acceptC(O.CandidateWorkRevisionAdmitted(ContextReference("fixture-revision", "1"),
        "Specification-owned same-Step revision")))
      val inspected = take(revised.acceptC(O.SemanticCompleted(revised.next.asInstanceOf[O.Semantic].key,
        "Specification revised implementation")))
      val paths = fixture.target.plan.steps.head.ownedPaths
      val inspection = C.Payload("25", fixture.binding.step, paths, Vector.empty, None, Vector.empty, None,
        dependencySourceDirection = Some(fixture.direction))
      def id(key: String) = fixture.target.sessionId + ":ordered-candidate:25:" + key
      val stage = Recovery.StagePair(0, id("stage-intent-0"), "1", "27", id("stage-result-0"), "1", "28",
        inspection, inspection.copy(stagedPaths = paths))
      val guarded = stage.result.copy(dependencySourceGuard = Some(fixture.before))
      val payloads = Vector(guarded, guarded, guarded.copy(dependencySourceCapture = Some(fixture.capture)))
      val keys = Vector("dependency-source-inspection", "dependency-source-observation-intent", "dependency-source-observation-result")
      val prefix = keys.zip(payloads).zipWithIndex.map { case ((key, payload), index) =>
        Recovery.DependencyFact(key, id(key), "1", (29 + index).toString, payload)
      }
      val target = Recovery.RecoveryTarget(fixture.target.sessionId, Recovery.controllerSnapshot(inspected),
        "goal-phase", "1", "participant", "25", "31", "/fixture/primary", "main", paths, Vector.empty,
        id("inspection"), "1", "26", inspection, WorkflowSourceHistoryRule.Selection("none", None),
        Vector.empty, Vector(stage), "UNKNOWN", "termination-unconfirmed", prefix)
      When("the original recovery owner authenticates all prefix lengths and a crossed original fact")
      val outcomes = (0 to prefix.size).map(n => Recovery.dependencyPrefixValid(target.copy(
        dependencyPrefix = prefix.take(n), frontierRevision = (28 + n).toString))).toVector
      val crossed = Recovery.dependencyPrefixValid(target.copy(dependencyPrefix = prefix.updated(0,
        prefix.head.copy(identity = "foreign-source-fact"))))
      val observation = Recovery.observationAllowed(target)
      Then("recovery retains every old fact and uncertainty and requires explicit ordinary ReviseWork")
      outcomes shouldBe Vector.fill(4)(true)
      crossed shouldBe false
      observation shouldBe false
      Recovery.preparedPayload(target).dependencySourceCapture shouldBe Some(fixture.capture)
      target.oldObservationTermination shouldBe "termination-unconfirmed"
    }

    "prepare only independently attributed direction against the exact public description" in {
      Given("a specification-owned description, explicit answer and external participant")
      import org.simplemodeling.textus.smworkflow.value as V
      import org.simplemodeling.textus.smworkflow.preparation.NativeDependencySourceRevisionPreparation
      val fixture = DependencySourceRevisionSpecificationFixture
      val reference = V.WorkflowArtifactReference("specification-direction", "1")
      val description = V.GoalPhaseDependencySourceRevisionDescriptionResult(fixture.target.sessionId,
        "direction-required", R.message, "specification-component", R.targetRecord(fixture.target), R.choices, R.adoptionOperation)
      val answer = V.GoalPhaseDependencySourceRevisionAdoptionInput(fixture.target.sessionId, description.target,
        "AdoptDependencySourceRevision", "Specification direction", reference)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      def prepare(input: V.GoalPhaseDependencySourceRevisionAdoptionInput, participant: String) =
        NativeDependencySourceRevisionPreparation.prepareC(description, input, reference, execution, participant,
          "specification-host", java.time.Instant.parse("2026-10-26T00:00:00Z"))
      When("the passive converter receives actual matching direction or a crossed input")
      val valid = prepare(answer, "participant").isSuccess
      val crossed = Vector(prepare(answer.copy(sessionId = "foreign-session"), "participant"),
        prepare(answer.copy(target = R.targetRecord(fixture.target.copy(sourceRevision = "17"))), "participant"),
        prepare(answer.copy(choice = "Defer"), "participant"), prepare(answer, "other-participant")).map(_.isSuccess)
      Then("only the matching independent answer creates evidence; conversion performs no adoption")
      valid shouldBe true
      crossed shouldBe Vector.fill(4)(false)
    }

    "qualify committed empty-diff content only as a new capture, never legacy accepted evidence" in {
      Given("a fixture migration between different roots and original commit bases")
      val fixture = DependencySourceRevisionSpecificationFixture
      When("the strict direction and committed capture codecs retain their own source identity")
      val restored = fixture.capture.asJson.as[R.Capture]
      Then("the empty-diff committed mode preserves the predecessor without fabricating a V1 capture")
      R.targetValid(fixture.target) shouldBe true
      restored shouldBe Right(fixture.capture)
      fixture.capture.stagedPaths shouldBe empty
      fixture.capture.primaryBase shouldBe DependencyValidationSpecificationFixture.primaryBase
      GoalPhaseDependencySourceRevision.binding(None, Some(fixture.capture)) shouldBe Some(fixture.binding)
      GoalPhaseDependencySourceRevision.binding(Some(DependencyValidationSpecificationFixture.capture), Some(fixture.capture)) shouldBe None
    }

    "reject crossed source captures, unknown disposition versions and caller-authored legacy replacement" in {
      Given("a complete specification direction with the original saved approval union")
      val fixture = DependencySourceRevisionSpecificationFixture
      val malformed = Vector(fixture.capture.copy(sessionId = "another-session"),
        fixture.capture.copy(plan = ContextReference("foreign-plan", "1")),
        fixture.capture.copy(generation = fixture.target.generation),
        fixture.capture.copy(sourceRevision = fixture.target.pendingRevision),
        fixture.capture.copy(primaryBase = fixture.binding.base),
        fixture.capture.copy(postStageGuard = fixture.before.copy(selectedFiles = Vector.empty)))
      When("each unsupported capture is decoded")
      val decoded = malformed.map(_.asJson.as[R.Capture].isLeft)
      val stored = GoalPhaseLegacyPurposeRepairContinuation.DependencySourceRevision(fixture.direction)
      val wire = GoalPhaseLegacyPurposeRepairContinuation.storageCodec.encode(stored)
      val restored = GoalPhaseLegacyPurposeRepairContinuation.storageCodec.decode(wire)
      val future = GoalPhaseLegacyPurposeRepairContinuation.storageCodec.decode(wire.mapObject(_.add("schemaVersion", "future".asJson)))
      val legacy = DependencyValidationSpecificationFixture.admission
      import GoalPhaseDependencyValidation.given
      val forged = legacy.asJson.mapObject(_.add("sourceDirection", fixture.direction.asJson)).as[GoalPhaseDependencyValidation.Admission]
      Then("none can inherit earlier checks or accepted status")
      decoded.forall(identity) shouldBe true
      restored shouldBe Right(stored)
      future.isLeft shouldBe true
      forged.isLeft shouldBe true
    }

    "allow ordinary later repair bytes within the existing boundary while fixing exclusions and source identity" in {
      Given("a native adopted source and a subsequent ordinary repair generation")
      val fixture = DependencySourceRevisionSpecificationFixture
      val changed = fixture.before.copy(selectedFiles = fixture.before.selectedFiles.map(f => f.copy(sha256 = "9" * 64)))
      When("the first adopted generation or later native repair captures those bytes")
      val actual = Vector(R.captureBaseValid(fixture.direction, 2, changed),
        R.captureBaseValid(fixture.direction, 3, changed),
        R.captureBaseValid(fixture.direction, 3, changed.copy(excludedFiles = Vector.empty)),
        R.captureBaseValid(fixture.direction, 3, changed.copy(head = "3" * 40)))
      Then("the first capture refuses drift and later repair never changes its exclusions or base")
      actual shouldBe Vector(false, true, false, false)
    }

    "round trip the exact source mode without creating acceptance evidence" in {
      Given("a proposed source and inherited verification coverage")
      When("the closed business codec reads the proposal")
      val decoded = proposal.asJson.as[R.Proposal]
      Then("only the reference and content selection survive, with no acceptance state")
      decoded shouldBe Right(proposal)
      proposal.copy(mode = "committed").asJson.as[R.Proposal] shouldBe Right(proposal.copy(mode = "committed"))
    }

    "reject unknown versions, modes, fields and malformed exclusion paths" in {
      Given("an otherwise valid source proposal")
      val malformed = Vector(proposal.asJson.mapObject(_.add("accepted", Json.True)),
        proposal.copy(schemaVersion = "future-schema").asJson, proposal.copy(mode = "automatic").asJson,
        proposal.copy(excludedPaths = Vector("../outside")).asJson,
        proposal.copy(excludedPaths = Vector(excluded, excluded)).asJson)
      When("business input attempts to widen or obscure the selected source")
      val actual = malformed.map(_.as[R.Proposal].isLeft)
      Then("every malformed case refuses before authority or effects")
      actual.forall(identity) shouldBe true
    }

    "reject malformed native guard entries and overlapping selected exclusions" in {
      Given("a retained guard representing one selected source and one excluded document")
      When("an input claims an unsupported mode or overlapping ownership")
      val modes = before.copy(selectedEntries = Vector(entry.copy(mode = "120000"))).asJson.as[R.Guard]
      val overlap = before.copy(excludedFiles = Vector(source)).asJson.as[R.Guard]
      Then("the strict guard codec refuses the contradictory content selection")
      modes.isLeft shouldBe true
      overlap.isLeft shouldBe true
    }
  }
}
