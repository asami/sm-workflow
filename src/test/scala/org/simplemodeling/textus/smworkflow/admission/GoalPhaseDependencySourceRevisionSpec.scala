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
  * @version Oct. 27, 2026
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

  "An actual approved source inventory successor" should {
    def _fixture_(): (R.Direction, R.ScopeSuccessor, R.Capture) = {
      import DependencySourceRevisionSpecificationFixture as F
      import DependencyValidationSpecificationFixture as D
      val path = "src/main/scala/Additional.scala"
      val approval = D.approvals.head.copy(proposal = D.approvals.head.proposal.copy(
        identity = "additional-source-proposal", paths = Vector(path),
        acceptance = Vector("Qualify the additional source through the existing checks")),
        decision = ContextReference("additional-actual-decision-fixture", "1"))
      val binding = F.binding.copy(revision = F.binding.revision + 1, paths = F.binding.paths :+ path)
      val consumption = F.proposal.consumption.copy(expectedModes = F.proposal.consumption.expectedModes :+
        R.ExpectedMode(path, "100644"))
      val scope = R.ScopeSuccessor(R.scopeSchema, F.direction.directive, binding, Vector(approval), consumption,
        F.proposal.coverage :+ R.Coverage(approval.decision, approval.proposal.acceptance, binding.checks.map(_.identity)))
      val guard = F.before.copy(selectedFiles = F.before.selectedFiles :+ R.fileIdentity(path, bytes("object Additional\n")))
      val capture = F.capture.copy(schemaVersion = R.scopeCaptureSchema, generation = 3, sourceRevision = "30",
        preStageGuard = guard, postStageGuard = guard, scope = Some(scope))
      (F.direction, scope, capture)
    }

    // Specification data only: no actual image, independent Human or qualification is claimed.
    def _runtime_fixture_(): (R.Direction, R.Direction, R.ScopeSuccessor, R.Capture) = {
      val (original, prior, captured) = _fixture_()
      val identity = "b1901b58-3218-4aa1-9f9e-b57eb94e625c"
      val directory = "/specification/installations/" + identity
      val installation = R.RuntimeSelection(identity, "d56bf541-5133-4156-91e0-3a06ac0d88db",
        directory + "/runtime-installation.json",
        Vector(R.RuntimeArtifact(org.goldenport.cncf.cli.InstalledRuntimeDistribution.ROOT_COORDINATE,
          directory + "/runtime/0000.jar", "/specification/publication/cncf.jar")),
        org.goldenport.cncf.cli.InstalledRuntimeDistribution.COMPONENT_IDENTITY,
        R.RuntimeArtifact(org.goldenport.cncf.cli.InstalledRuntimeDistribution.COMPONENT_COORDINATE,
          directory + "/components/textus-sm-workflow.car", "/specification/publication/sm-workflow.car"))
      val consumption = prior.consumption.copy(runtimeImage = identity,
        manifestReference = ContextReference("cncf-installed-runtime:" + identity, "1"))
      val proposal = original.target.proposal.copy(binding = ContextReference(prior.binding.identity, prior.binding.revision.toString),
        coverage = prior.coverage, consumption = consumption)
      val target = original.target.copy(schemaVersion = R.runtimeSchema, questionIdentity = "runtime-consumption-question",
        sourceRevision = "31", pendingRevision = "32", generation = 3,
        requestingResult = ContextReference("runtime-consumption-request", "1"), proposal = proposal,
        binding = prior.binding, preStageGuard = captured.preStageGuard,
        runtime = Some(R.RuntimeSuccession(original.directive, prior.approvals, installation)))
      val next = R.Direction(target, ContextReference("runtime-independent-direction-fixture", "1"),
        original.participant, "Specification-owned bounded installed image selection")
      val scope = prior.copy(schemaVersion = R.runtimeScopeSchema, consumption = consumption, consumptionDirection = Some(next))
      (original, next, scope, captured.copy(generation = 4, sourceRevision = "38", scope = Some(scope)))
    }

    def _exclusion_fixture_(): (R.Direction, R.Direction, R.ScopeSuccessor, R.Capture) = {
      val (previous, runtime, prior, captured) = _runtime_fixture_()
      val oldpaths = Vector("docs/old-1.md", "docs/old-2.md", "docs/old-3.md", "docs/old-4.md")
      val addedpaths = Vector("docs/design/configuration.md", "docs/phase/README.md", "docs/journal/handoff.md",
        "docs/phase/startup.md", "docs/spec/startup.md")
      val oldguard = previous.target.preStageGuard.copy(excludedFiles = oldpaths.map(R.fileIdentity(_, bytes("old\n"))))
      val oldproposal = previous.target.proposal.copy(excludedPaths = oldpaths,
        consumption = previous.target.proposal.consumption.copy(nonInputExclusions = oldpaths))
      val original = previous.copy(target = previous.target.copy(proposal = oldproposal, preStageGuard = oldguard))
      val consumption = prior.consumption.copy(nonInputExclusions = oldpaths ++ addedpaths)
      val guard = runtime.target.preStageGuard.copy(excludedFiles = oldguard.excludedFiles ++
        addedpaths.map(R.fileIdentity(_, bytes("additional unrelated owner\n"))))
      val target = runtime.target.copy(schemaVersion = R.exclusionSchema, proposal = runtime.target.proposal.copy(
        excludedPaths = oldpaths ++ addedpaths, consumption = consumption), preStageGuard = guard,
        exclusionSuccession = Some(R.ExclusionSuccession(R.exclusionSuccessionSchema, original.directive, oldpaths, addedpaths)))
      val next = runtime.copy(target = target)
      val scope = prior.copy(schemaVersion = R.exclusionScopeSchema, consumption = consumption, consumptionDirection = Some(next))
      (original, next, scope, captured.copy(schemaVersion = R.exclusionCaptureSchema, direction = original,
        preStageGuard = guard, postStageGuard = guard, scope = Some(scope)))
    }

    def _committed_fixture_(): (R.Direction, R.Direction, R.ScopeSuccessor, R.Capture) = {
      val (previous, runtime, prior, captured) = _exclusion_fixture_()
      val originalmodes = previous.target.proposal.consumption.expectedModes.zipWithIndex.map((mode, index) =>
        if (index == 1) mode.copy(mode = "100755") else mode)
      val original = previous.copy(target = previous.target.copy(proposal = previous.target.proposal.copy(
        mode = "selected-changes", consumption = previous.target.proposal.consumption.copy(expectedModes = originalmodes))))
      val binding = prior.binding.copy(base = GoalPhaseOrderedProgression.Snapshot("c" * 40, "d" * 40))
      val modes = originalmodes :+ prior.consumption.expectedModes.last.copy(mode = "000000")
      val consumption = prior.consumption.copy(expectedModes = modes)
      val guard = captured.preStageGuard.copy(head = binding.base.head, committedTree = binding.base.tree,
        indexTree = binding.base.tree, selectedFiles = captured.preStageGuard.selectedFiles.dropRight(1) :+
          R.fileIdentity(binding.paths.last, None))
      val paths = Vector(binding.paths.head, runtime.target.exclusionSuccession.get.previousExclusions.head,
        runtime.target.exclusionSuccession.get.addedPaths.head, "src/main/scala/BaselineOnly.scala")
      val changes = paths.map(path => R.StageChange("100644", "100644", Some("a" * 40), Some("b" * 40), "M", path))
      val entries = modes.map(mode => R.CommittedEntry(mode.path,
        Option.when(mode.mode != "000000")(R.CommittedObject(mode.mode, "e" * 40))))
      val succession = R.CommittedBaseSuccession(R.committedBaseSuccessionSchema, original.directive,
        original.target.binding.base, binding.base, changes, paths.take(1), paths.slice(1, 2), paths.slice(2, 3),
        paths.drop(3), "committed", entries)
      val target = runtime.target.copy(schemaVersion = R.committedSchema, binding = binding,
        proposal = runtime.target.proposal.copy(mode = "committed", consumption = consumption),
        preStageGuard = guard, committedBaseSuccession = Some(succession))
      val next = runtime.copy(target = target)
      val scope = prior.copy(schemaVersion = R.committedScopeSchema, binding = binding, consumption = consumption,
        consumptionDirection = Some(next))
      (original, next, scope, captured.copy(schemaVersion = R.committedCaptureSchema, direction = original,
        preStageGuard = guard, postStageGuard = guard, stagedPaths = Vector.empty, scope = Some(scope)))
    }

    "round trip explicit committed-base v4 and preserve every legacy target scope and capture meaning" in {
      Given("an explicit committed successor plus unchanged V1 V2 and V3 fixtures")
      val (original, next, scope, capture) = _committed_fixture_()
      val (_, runtime, runtimescope, runtimecapture) = _runtime_fixture_()
      val (_, excluded, excludedscope, excludedcapture) = _exclusion_fixture_()
      val targets = Vector(original.target, runtime.target, excluded.target, next.target)
      When("the ordinary closed codecs and public Record projection read the exact complete values")
      val read = targets.map(value => value.asJson.as[R.Target])
      val public = R.targetC(R.targetRecord(next.target))
      Then("V4 is explicit and each old writer still omits committed-base succession")
      read shouldBe targets.map(Right(_))
      take(public) shouldBe next.target
      targets.dropRight(1).foreach(_.asJson.hcursor.downField("committedBaseSuccession").focus shouldBe None)
      Vector(runtimescope, excludedscope, scope).foreach(value => value.asJson.as[R.ScopeSuccessor] shouldBe Right(value))
      Vector(runtimecapture, excludedcapture, capture).foreach(value => value.asJson.as[R.Capture] shouldBe Right(value))
      R.runtimeSuccessorValid(original, next, scope.approvals) shouldBe true
      R.modeOf(original, Some(scope)) shouldBe "committed"
      R.modeOf(capture) shouldBe "committed"
      original.target.proposal.mode shouldBe "selected-changes"
      capture.direction shouldBe original
      capture.primaryBase shouldBe original.target.primaryBase
      capture.stagedPaths shouldBe empty
    }

    "refuse missing null unknown and crossed committed-base target scope capture and entry fields" in {
      Given("a complete V4 target with one explicitly absent selected entry")
      val (_, next, scope, capture) = _committed_fixture_()
      val wire = next.target.asJson
      val succession = next.target.committedBaseSuccession.get
      val absent = succession.committedEntries.last.asJson
      When("a caller removes required meaning or crosses a historical version")
      val invalidpairs = Vector("previousBase", "nextBase").flatMap { key =>
        val base = succession.asJson.hcursor.get[Json](key).toOption.get
        Vector(base.mapObject(_.add("unknown", Json.True)), base.mapObject(_.remove("head")),
          base.mapObject(_.remove("tree")), Json.Null, base.mapObject(_.add("head", Json.Null)),
          base.mapObject(_.add("tree", Json.Null)), base.mapObject(_.add("head", "invalid-oid".asJson)),
          base.mapObject(_.add("tree", "invalid-oid".asJson))).map(pair =>
          wire.mapObject(_.add("committedBaseSuccession", succession.asJson.mapObject(_.add(key, pair)))))
      }
      val invalid = Vector(wire.mapObject(_.remove("committedBaseSuccession")),
        wire.mapObject(_.add("committedBaseSuccession", Json.Null)),
        wire.mapObject(_.add("schemaVersion", R.exclusionSchema.asJson)),
        wire.mapObject(_.add("committedBaseSuccession", succession.asJson.mapObject(_.remove("sourceMode")))),
        wire.mapObject(_.add("committedBaseSuccession", succession.asJson.mapObject(_.add("unknown", Json.True))))) ++ invalidpairs
      Then("no omission or null upgrades an old schema or creates a complete selected observation")
      invalid.map(_.as[R.Target].isLeft) shouldBe Vector.fill(invalid.size)(true)
      absent.hcursor.downField("entry").focus shouldBe None
      absent.as[R.CommittedEntry] shouldBe Right(succession.committedEntries.last)
      absent.mapObject(_.add("entry", Json.Null)).as[R.CommittedEntry].isLeft shouldBe true
      absent.mapObject(_.add("unknown", Json.True)).as[R.CommittedEntry].isLeft shouldBe true
      scope.copy(schemaVersion = R.exclusionScopeSchema).asJson.as[R.ScopeSuccessor].isLeft shouldBe true
      capture.copy(schemaVersion = R.exclusionCaptureSchema).asJson.as[R.Capture].isLeft shouldBe true
      scope.asJson.mapObject(_.remove("consumptionDirection")).as[R.ScopeSuccessor].isLeft shouldBe true
    }

    "require an exact complete ordered partition without granting baseline ownership" in {
      Given("native-shaped changes in all four disjoint partitions")
      val (original, next, scope, _) = _committed_fixture_()
      val succession = next.target.committedBaseSuccession.get
      val invalid = Vector(succession.copy(baselineChangedPaths = Vector.empty),
        succession.copy(phaseChangedPaths = succession.phaseChangedPaths ++ succession.baselineChangedPaths),
        succession.copy(previousExcludedChangedPaths = succession.addedExcludedChangedPaths),
        succession.copy(changes = succession.changes.dropRight(1)),
        succession.copy(previousBase = succession.nextBase),
        succession.copy(parent = ContextReference("invented-parent", "1")))
      When("each changed path or its native parent/base classification is altered")
      val allowed = invalid.map(value => R.runtimeSuccessorValid(original,
        next.copy(target = next.target.copy(committedBaseSuccession = Some(value))), scope.approvals))
      Then("every alteration refuses and baseline paths stay outside selected ownership and exclusions")
      allowed shouldBe Vector.fill(invalid.size)(false)
      succession.baselineChangedPaths.toSet.intersect(scope.binding.paths.toSet) shouldBe empty
      succession.baselineChangedPaths.toSet.intersect(scope.consumption.nonInputExclusions.toSet) shouldBe empty
      R.runtimeSuccessorValid(next, next.copy(directive = ContextReference("second-successor", "1")), scope.approvals) shouldBe false
    }

    "retain untouched committed executable and absent entries and refuse source staging or later drift" in {
      Given("a V4 selected inventory containing changed untouched executable and absent paths")
      val (original, next, scope, capture) = _committed_fixture_()
      val succession = next.target.committedBaseSuccession.get
      val untouched = succession.committedEntries(1)
      val substituted = succession.copy(committedEntries = succession.committedEntries.updated(1,
        untouched.copy(entry = untouched.entry.map(_.copy(mode = "100644")))))
      When("selected entries, historical binding, stage effects or the effective native guard are crossed")
      val modeallowed = next.target.copy(committedBaseSuccession = Some(substituted)).asJson.as[R.Target]
      val incomplete = next.target.copy(committedBaseSuccession = Some(succession.copy(
        committedEntries = succession.committedEntries.dropRight(1)))).asJson.as[R.Target]
      val drift = capture.postStageGuard.copy(head = "f" * 40)
      Then("complete selected data and the single effective committed mode remain mandatory")
      untouched.entry.map(_.mode) shouldBe Some("100755")
      succession.phaseChangedPaths should not contain untouched.path
      succession.committedEntries.last.entry shouldBe None
      modeallowed.isLeft shouldBe true
      incomplete.isLeft shouldBe true
      capture.copy(stagedPaths = scope.binding.paths).asJson.as[R.Capture].isLeft shouldBe true
      R.captureBaseValid(original, capture.generation + 1, drift, Some(scope)) shouldBe false
      R.captureBaseValid(original, capture.generation + 1, capture.preStageGuard, Some(scope)) shouldBe true
      R.bindingOf(capture).base shouldBe succession.nextBase
      original.target.binding.base shouldBe succession.previousBase
      scope.copy(binding = original.target.binding).asJson.as[R.ScopeSuccessor].isRight shouldBe true
      R.scopeValid(original, scope.copy(binding = original.target.binding)) shouldBe false
    }

    "round trip closed non-input exclusion target scope and capture without upgrading v1 or v2 encodings" in {
      Given("four retained exclusions and five independently declared additions accompanying one runtime successor")
      val (original, next, scope, capture) = _exclusion_fixture_()
      val (_, runtime, runtimescope, runtimecapture) = _runtime_fixture_()
      val oldwire = original.target.asJson
      val runtimewire = runtime.target.asJson
      val wire = next.target.asJson
      When("the closed readers receive complete values or null missing unknown and mixed-version fields")
      val invalid = Vector(wire.mapObject(_.remove("runtime")), wire.mapObject(_.remove("exclusionSuccession")),
        wire.mapObject(_.add("exclusionSuccession", Json.Null)), wire.mapObject(_.add("runtime", Json.Null)),
        wire.mapObject(_.add("schemaVersion", R.runtimeSchema.asJson)),
        runtimewire.mapObject(_.add("exclusionSuccession", next.target.exclusionSuccession.get.asJson)),
        oldwire.mapObject(_.add("exclusionSuccession", Json.Null)),
        wire.mapObject(_.add("exclusionSuccession", next.target.exclusionSuccession.get.asJson.mapObject(_.add("unknown", Json.True)))))
      Then("v3 is explicit and old encodings retain their omitted optional field and fixed semantics")
      oldwire.hcursor.downField("exclusionSuccession").focus shouldBe None
      runtimewire.hcursor.downField("exclusionSuccession").focus shouldBe None
      oldwire.as[R.Target] shouldBe Right(original.target)
      runtimewire.as[R.Target] shouldBe Right(runtime.target)
      runtimescope.asJson.as[R.ScopeSuccessor] shouldBe Right(runtimescope)
      runtimecapture.asJson.as[R.Capture] shouldBe Right(runtimecapture)
      wire.as[R.Target] shouldBe Right(next.target)
      scope.asJson.as[R.ScopeSuccessor] shouldBe Right(scope)
      capture.asJson.as[R.Capture] shouldBe Right(capture)
      invalid.forall(_.as[R.Target].isLeft) shouldBe true
      scope.copy(schemaVersion = R.runtimeScopeSchema).asJson.as[R.ScopeSuccessor].isLeft shouldBe true
      capture.copy(schemaVersion = R.scopeCaptureSchema).asJson.as[R.Capture].isLeft shouldBe true
      scope.asJson.mapObject(_.remove("consumptionDirection")).as[R.ScopeSuccessor].isLeft shouldBe true
      original.target.asJson shouldBe oldwire
      runtime.target.asJson shouldBe runtimewire
    }

    "refuse non-input exclusion removal reorder overlap duplicate unsafe paths and a second succession" in {
      Given("a closed append-only successor anchored to the actual original Direction and native approvals")
      val (original, next, scope, _) = _exclusion_fixture_()
      val succession = next.target.exclusionSuccession.get
      def _next_(value: R.ExclusionSuccession): R.Direction = next.copy(target = next.target.copy(exclusionSuccession = Some(value)))
      When("a caller crosses the original exclusion boundary or repeats the transition")
      val invalid = Vector(_next_(succession.copy(parent = ContextReference("foreign", "1"))),
        _next_(succession.copy(previousExclusions = succession.previousExclusions.reverse)),
        _next_(succession.copy(previousExclusions = succession.previousExclusions.drop(1))),
        _next_(succession.copy(addedPaths = Vector.empty)),
        _next_(succession.copy(addedPaths = succession.addedPaths :+ succession.addedPaths.head)),
        _next_(succession.copy(addedPaths = Vector("../outside"))),
        _next_(succession.copy(addedPaths = Vector(next.target.binding.paths.head))),
        next.copy(target = next.target.copy(runtime = None)))
      val unqualified = scope.copy(consumption = scope.consumption.copy(nonInputExclusions = succession.previousExclusions))
      Then("only exact E0 plus nonempty new additions with runtime and independent consumption is valid")
      R.runtimeSuccessorValid(original, next, scope.approvals) shouldBe true
      invalid.map(R.runtimeSuccessorValid(original, _, scope.approvals)) shouldBe Vector.fill(invalid.size)(false)
      R.scopeValid(original, unqualified) shouldBe false
      R.runtimeSuccessorValid(next, next.copy(directive = ContextReference("second", "1")), scope.approvals) shouldBe false
      R.runtimeSuccessorValid(original, next, Vector.empty) shouldBe false
    }

    "project all nine non-input exclusion guards through first capture later staging and candidate recovery" in {
      Given("a v3 native scope with immutable original Direction and exact nine-file guard")
      val (original, next, scope, capture) = _exclusion_fixture_()
      val guard = capture.preStageGuard
      val changed = guard.copy(excludedFiles = guard.excludedFiles.updated(4, R.fileIdentity(guard.excludedFiles(4).path, bytes("drift\n"))))
      val absent = guard.copy(excludedFiles = guard.excludedFiles.updated(4, R.fileIdentity(guard.excludedFiles(4).path, None)))
      val staged = guard.copy(indexTree = "f" * 40, excludedEntries = Vector(R.Entry(guard.excludedFiles(4).path, "100755", "e" * 40)))
      val payload = GoalPhaseNativeCandidate.Payload(capture.sourceRevision, scope.binding.step, Vector("docs/phase.md"),
        Vector.empty, None, Vector.empty, None, dependencySourceDirection = Some(original), dependencySourceScope = Some(scope),
        dependencySourceGuard = Some(guard), dependencySourceCapture = Some(capture))
      When("first and subsequent captures compare effective qualification rather than original E0")
      val allowed = Vector(R.captureBaseValid(original, capture.generation, guard, Some(scope)),
        R.captureBaseValid(original, capture.generation, changed, Some(scope)),
        R.captureBaseValid(original, capture.generation + 1, absent, Some(scope)),
        R.captureBaseValid(original, capture.generation + 1, staged, Some(scope)),
        R.guardModesValid(original, guard.copy(excludedFiles = guard.excludedFiles.take(4)), Some(scope)))
      Then("the effective scope authenticates exact exclusions and refuses byte presence staged-mode and inventory drift")
      allowed shouldBe Vector(true, false, false, false, false)
      R.exclusionsOf(original, Some(scope)) shouldBe next.target.proposal.excludedPaths
      R.qualificationOf(original, Some(scope)) shouldBe next
      R.scopeAuthenticated(original, Some(scope), scope.approvals, Some(next)) shouldBe true
      R.scopeAuthenticated(original, Some(scope), scope.approvals) shouldBe false
      R.candidateValid(payload) shouldBe true
      GoalPhaseNativeCandidate.codec.decode(GoalPhaseNativeCandidate.codec.encode(payload)) shouldBe Right(payload)
      R.candidateValid(payload.copy(dependencySourceGuard = Some(changed))) shouldBe false
      R.stageTransitionValid(guard, changed, guard.selectedFiles.head.path, Vector.empty) shouldBe false
      R.stageTransitionValid(guard, staged, guard.selectedFiles.head.path, Vector.empty) shouldBe false
      original.target.proposal.excludedPaths should have size 4
    }

    "retain non-input exclusion source fact prefixes and UNKNOWN outcomes without replay in recovery" in {
      Given("a v3 committed capture after completed primary staging in the same source generation")
      import GoalPhaseCandidateRecovery as Recovery
      import GoalPhaseNativeCandidate as C
      import GoalPhaseOrderedProgression as O
      val (original, next, scope, capture) = _exclusion_fixture_()
      val started = take(O.startC(original.target.plan, original.target.primaryBase))
      def _completed_(state: O.State): O.State = take(state.acceptC(O.SemanticCompleted(
        state.next.asInstanceOf[O.Semantic].key, "Specification-owned implementation")))
      def _revised_(state: O.State): O.State = take(state.acceptC(O.CandidateWorkRevisionAdmitted(
        ContextReference("noninput-revision-" + state.generation, "1"), "Specification-owned revision")))
      val controller = _completed_(_revised_(_completed_(_revised_(_completed_(_revised_(_completed_(started)))))))
      val paths = original.target.plan.steps.head.ownedPaths
      val inspection = C.Payload(capture.sourceRevision, scope.binding.step, paths, Vector.empty, None, Vector.empty, None,
        dependencySourceDirection = Some(original), dependencySourceScope = Some(scope))
      def _identity_(key: String): String = original.target.sessionId + ":ordered-candidate:" + capture.sourceRevision + ":" + key
      val stage = Recovery.StagePair(0, _identity_("stage-intent-0"), "1", "40", _identity_("stage-result-0"), "1", "41",
        inspection, inspection.copy(stagedPaths = paths))
      val guarded = stage.result.copy(dependencySourceGuard = Some(capture.preStageGuard))
      val keys = Vector("dependency-source-inspection", "dependency-source-observation-intent", "dependency-source-observation-result")
      val payloads = Vector(guarded, guarded, guarded.copy(dependencySourceCapture = Some(capture)))
      val prefix = keys.zip(payloads).zipWithIndex.map { case ((key, payload), index) =>
        Recovery.DependencyFact(key, _identity_(key), "1", (42 + index).toString, payload)
      }
      val target = Recovery.RecoveryTarget(original.target.sessionId, Recovery.controllerSnapshot(controller),
        "goal-phase", "1", "participant", capture.sourceRevision, "44", "/fixture/primary", "main", paths, Vector.empty,
        _identity_("inspection"), "1", "39", inspection, WorkflowSourceHistoryRule.Selection("none", None),
        Vector.empty, Vector(stage), "UNKNOWN", "termination-unconfirmed", prefix)
      When("the existing recovery owner authenticates the retained prefix and an E0-only substituted guard")
      val allowed = (0 to prefix.size).map(n => Recovery.dependencyPrefixValid(target.copy(
        dependencyPrefix = prefix.take(n), frontierRevision = (41 + n).toString))).toVector
      val stale = guarded.copy(dependencySourceGuard = Some(capture.preStageGuard.copy(
        excludedFiles = capture.preStageGuard.excludedFiles.take(next.target.exclusionSuccession.get.previousExclusions.size))))
      Then("all actual v3 facts remain attributable and no old E0 projection or fresh observation is admitted")
      controller.generation shouldBe capture.generation
      allowed shouldBe Vector.fill(4)(true)
      Recovery.dependencyPrefixValid(target.copy(dependencyPrefix = prefix.updated(0, prefix.head.copy(payload = stale)))) shouldBe false
      Recovery.preparedPayload(target).dependencySourceCapture shouldBe Some(capture)
      Recovery.observationAllowed(target) shouldBe false
      target.oldObservationTermination shouldBe "termination-unconfirmed"
    }

    "retain the original direction while covering the approved file and historical alias" in {
      Given("fixture native approvals for the same dependency through its adopted historical workspace relation")
      val (direction, scope, capture) = _fixture_()
      When("the effective source is projected from a separately versioned scope")
      val binding = R.bindingOf(capture)
      Then("the old direction is unchanged and the new acceptance has its own exact coverage")
      R.scopeValid(direction, scope) shouldBe true
      R.captureValid(capture) shouldBe true
      binding.paths shouldBe (direction.target.binding.paths :+ "src/main/scala/Additional.scala")
      direction.target.binding.revision should be < binding.revision
      scope.coverage.take(direction.target.proposal.coverage.size) shouldBe direction.target.proposal.coverage
      R.scopeAuthenticated(direction, Some(scope), scope.approvals) shouldBe true
      R.scopeAuthenticated(direction, Some(scope), Vector.empty) shouldBe false
      R.scopeAuthenticated(direction, None, scope.approvals) shouldBe false
      R.scopeAuthenticated(direction, None, Vector.empty) shouldBe true
    }

    "refuse source replacement, unapproved files, excluded files and changed program or mode declarations" in {
      Given("a valid successor and the independent declaration it actually selects")
      val (direction, scope, _) = _fixture_()
      val wrongapproval = scope.approvals.head.copy(proposal = scope.approvals.head.proposal.copy(
        dependency = scope.approvals.head.proposal.dependency.copy(workspace = "foreign-workspace")))
      When("a structural successor tries to change a fixed source boundary")
      val invalid = Vector(
        scope.copy(binding = scope.binding.copy(root = "/foreign/source")),
        scope.copy(binding = scope.binding.copy(paths = scope.binding.paths :+ "unapproved.txt")),
        scope.copy(binding = scope.binding.copy(base = scope.binding.base.copy(head = "f" * 40))),
        scope.copy(approvals = Vector(wrongapproval)),
        scope.copy(consumption = scope.consumption.copy(programs = scope.consumption.programs.map(_.copy(program = "foreign-program")))),
        scope.copy(consumption = scope.consumption.copy(expectedModes = scope.consumption.expectedModes.updated(0,
          scope.consumption.expectedModes.head.copy(mode = "100755")))),
        scope.copy(coverage = scope.coverage.dropRight(1)))
      Then("all substitutions refuse without changing the old direction or legacy admission")
      invalid.map(R.scopeValid(direction, _)) shouldBe Vector.fill(invalid.size)(false)
    }

    "keep legacy bytes readable and require a distinct closed scope capture version" in {
      Given("the legacy direction capture and a later scope capture")
      import DependencySourceRevisionSpecificationFixture as F
      val (_, scope, capture) = _fixture_()
      val legacy = F.capture.asJson
      When("their versioned capture codecs decode original and contradictory inputs")
      val invalid = Vector(
        legacy.mapObject(_.add("scope", Json.Null)),
        capture.copy(schemaVersion = R.schema).asJson,
        capture.copy(scope = None).asJson,
        capture.asJson.mapObject(_.add("unexpected", Json.True)),
        capture.asJson.mapObject(_.add("scope", scope.asJson.mapObject(_.add("unexpected", Json.True)))))
      Then("legacy encoding has no new field and only the complete new version decodes")
      legacy.hcursor.downField("scope").focus shouldBe None
      legacy.as[R.Capture] shouldBe Right(F.capture)
      capture.asJson.as[R.Capture] shouldBe Right(capture)
      invalid.forall(_.as[R.Capture].isLeft) shouldBe true
      F.capture.asJson shouldBe legacy
    }

    "carry the scope through the native candidate envelope without upgrading an old receipt" in {
      Given("a candidate whose source selection has an authentic-shaped later scope")
      val (_, scope, capture) = _fixture_()
      val payload = GoalPhaseNativeCandidate.Payload("30", scope.binding.step, Vector("docs/phase.md"),
        Vector.empty, None, Vector.empty, None, dependencySourceDirection = Some(capture.direction),
        dependencySourceScope = Some(scope))
      val legacy = payload.copy(dependencySourceScope = None)
      When("the ordinary candidate codec emits each version and receives a crossed envelope")
      val wire = GoalPhaseNativeCandidate.codec.encode(payload)
      val oldwire = GoalPhaseNativeCandidate.codec.encode(legacy)
      Then("the new scope has an explicit version while historical receipt encoding remains unchanged")
      wire.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-native-candidate.v3")
      oldwire.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-native-candidate.v2")
      GoalPhaseNativeCandidate.codec.decode(wire) shouldBe Right(payload)
      GoalPhaseNativeCandidate.codec.decode(oldwire) shouldBe Right(legacy)
      GoalPhaseNativeCandidate.codec.decode(wire.mapObject(_.add("schemaVersion",
        Json.fromString("sm-workflow.goal-phase-native-candidate.v2")))).isLeft shouldBe true
      GoalPhaseNativeCandidate.codec.decode(wire.mapObject(_.add("unexpected", Json.True))).isLeft shouldBe true
      R.candidateValid(payload.copy(dependencySourceDirection = None)) shouldBe false
    }

    "retain the declared new-file mode and the old exclusion guard during fresh repair" in {
      Given("a later capture whose source scope includes one new regular file")
      val (direction, scope, capture) = _fixture_()
      val path = scope.binding.paths.last
      val changed = capture.preStageGuard.copy(selectedEntries = Vector(R.Entry(path, "100755", "e" * 40)))
      val exclusions = capture.preStageGuard.copy(excludedFiles = Vector.empty)
      When("freshness compares actual index modes or unrelated excluded content")
      val allowed = Vector(R.captureBaseValid(direction, 3, capture.preStageGuard, Some(scope)),
        R.captureBaseValid(direction, 3, changed, Some(scope)),
        R.captureBaseValid(direction, 3, exclusions, Some(scope)))
      Then("only the independently declared mode and preserved exclusion identity are valid")
      allowed shouldBe Vector(true, false, false)
    }

    "require an actual reconstructed consumption direction for changed runtime labels" in {
      Given("specification-shaped original and bounded successor directions over the same source")
      val (original, next, scope, capture) = _runtime_fixture_()
      val before = original.asJson
      When("structural scope validation is separated from native direction authentication")
      val valid = R.scopeValid(original, scope)
      val authenticated = R.scopeAuthenticated(original, Some(scope), scope.approvals, Some(next))
      val unsupported = R.scopeAuthenticated(original, Some(scope), scope.approvals)
      val changedlabels = scope.copy(schemaVersion = R.scopeSchema, consumptionDirection = None)
      Then("only the exact native direction can authenticate the changed image and manifest")
      valid shouldBe true
      authenticated shouldBe true
      unsupported shouldBe false
      R.scopeAuthenticated(original, None, scope.approvals, Some(next)) shouldBe false
      R.scopeValid(original, changedlabels) shouldBe false
      R.captureValid(capture) shouldBe true
      R.consumptionOf(capture) shouldBe next.target.proposal.consumption
      capture.direction.asJson shouldBe before
    }

    "refuse crossed runtime associations, source boundaries and native decisions" in {
      Given("a bounded successor with an explicit parent and exact native approvals")
      val (original, next, scope, _) = _runtime_fixture_()
      val runtime = next.target.runtime.get
      val target = next.target
      val differentindex = (if (target.predecessorGuard.indexTree.head == 'e') "f" else "e") * target.predecessorGuard.indexTree.length
      When("a successor changes the retained parent, fixed fields or actual approval coverage")
      val crossed = Vector(
        next.copy(target = target.copy(runtime = Some(runtime.copy(parent = ContextReference("other-parent", "1"))))),
        next.copy(target = target.copy(runtime = Some(runtime.copy(approvals = Vector.empty)))),
        next.copy(target = target.copy(primaryBase = target.primaryBase.copy(head = "f" * 40))),
        next.copy(target = target.copy(binding = target.binding.copy(root = "/other/source"))),
        next.copy(target = target.copy(proposal = target.proposal.copy(consumption = scope.consumption.copy(
          programs = scope.consumption.programs.map(_.copy(program = "other-program")))))),
        next.copy(target = target.copy(proposal = target.proposal.copy(consumption = scope.consumption.copy(
          manifestReference = ContextReference("cncf-installed-runtime:other-image", "1"))))),
        next.copy(target = target.copy(predecessorGuard = target.predecessorGuard.copy(indexTree = differentindex))),
        next.copy(participant = "another-participant"), next.copy(directive = original.directive))
      val allowed = crossed.map(R.runtimeSuccessorValid(original, _, scope.approvals))
      val inventedapproval = scope.approvals.head.copy(decision = ContextReference("invented-other-decision", "1"))
      Then("all crossings refuse and a matching shape cannot manufacture the real history")
      allowed shouldBe Vector.fill(crossed.size)(false)
      R.runtimeSuccessorValid(original, next, Vector(inventedapproval)) shouldBe false
      R.runtimeSuccessorValid(original, next, Vector.empty) shouldBe false
    }

    "preserve legacy target and disposition encodings while strictly versioning the successor" in {
      Given("legacy source direction and new runtime target specification values")
      val (original, next, scope, capture) = _runtime_fixture_()
      val oldwire = original.target.asJson
      val wire = next.target.asJson
      val disposition = GoalPhaseLegacyPurposeRepairContinuation.DependencySourceRevision(next)
      When("the existing Record and stored disposition owners decode both closed target versions")
      val decoded = R.targetC(R.targetRecord(next.target))
      val stored = GoalPhaseLegacyPurposeRepairContinuation.storageCodec.encode(disposition)
      val invalid = Vector(wire.mapObject(_.remove("runtime")), wire.mapObject(_.add("runtime", Json.Null)),
        wire.mapObject(_.add("schemaVersion", R.schema.asJson)),
        wire.mapObject(_.add("unexpected", Json.True)), oldwire.mapObject(_.add("runtime", Json.Null)))
      Then("old bytes omit new fields and neither missing nor contradictory new data is upgraded")
      oldwire.hcursor.downField("runtime").focus shouldBe None
      oldwire.as[R.Target] shouldBe Right(original.target)
      SplitPhaseExplicitPartitionFixture.take(decoded) shouldBe next.target
      wire.as[R.Target] shouldBe Right(next.target)
      invalid.forall(_.as[R.Target].isLeft) shouldBe true
      GoalPhaseLegacyPurposeRepairContinuation.storageCodec.decode(stored) shouldBe Right(disposition)
      scope.asJson.as[R.ScopeSuccessor] shouldBe Right(scope)
      scope.asJson.mapObject(_.add("consumptionDirection", Json.Null)).as[R.ScopeSuccessor].isLeft shouldBe true
      scope.copy(schemaVersion = R.scopeSchema).asJson.as[R.ScopeSuccessor].isLeft shouldBe true
      capture.asJson.as[R.Capture] shouldBe Right(capture)
      original.target.asJson shouldBe oldwire
    }

    "refuse unsupported installed profiles and source drift after qualified runtime selection" in {
      Given("an installed-profile specification observation and its exact selected source guard")
      val (original, next, scope, capture) = _runtime_fixture_()
      val installation = next.target.runtime.get.installation
      When("the profile, manifest association or selected source bytes differ")
      val unsupported = Vector(installation.copy(installation = "mutable-selector"),
        installation.copy(manifest = "/other/runtime-installation.json"), installation.copy(modules = Vector.empty),
        installation.copy(componentIdentity = "another-component"),
        installation.copy(modules = installation.modules.map(_.copy(path = "/foreign/module.jar"))))
      val changed = capture.preStageGuard.copy(selectedFiles = capture.preStageGuard.selectedFiles.map(_.copy(sha256 = "f" * 64)))
      val results = Vector(R.captureBaseValid(original, 4, capture.preStageGuard, Some(scope)),
        R.captureBaseValid(original, 4, changed, Some(scope)), R.captureBaseValid(original, 5, changed, Some(scope)))
      Then("only the original real-profile association and fixed qualified source bytes are eligible")
      unsupported.map(R.runtimeSelectionValid) shouldBe Vector.fill(unsupported.size)(false)
      R.runtimeMatches(installation, scope.consumption.copy(runtimeImage = "other-image")) shouldBe false
      R.runtimeMatches(installation, scope.consumption.copy(manifestReference = ContextReference("other-manifest", "1"))) shouldBe false
      results shouldBe Vector(true, false, false)
    }
  }
}
