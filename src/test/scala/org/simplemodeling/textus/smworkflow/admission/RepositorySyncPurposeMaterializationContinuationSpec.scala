package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativeRepositoryValidationContinuationPreparation as Preparation
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncPurposeMaterializationContinuation as M
import RepositorySyncNativeProgressStore as N
import RepositorySyncPurpose as P
import RepositorySyncPurpose.given
import SplitPhaseExplicitPartitionFixture.take

/** Pure authored observations, never live Human consent or native process facts.
  * The installed specification owns actual same-Session upgrade evidence.
  * @since Nov. 4, 2026
  * @version Nov. 4, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncPurposeMaterializationContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _fixture = RepositorySyncPurposeRepairScopeFixture
  private val _bytes = "prepared actual candidate\n".getBytes(UTF_8).toVector
  private val _process = ProcessExecutionResult(ProcessExecutionTermination.Exited(0),
    ProcessExecutionCapture(_bytes, _bytes.size.toLong, false), ProcessExecutionCapture(Vector.empty, 0, false), Vector.empty, 3, "git")
  private val _effect = Json.obj("outcome" -> "Succeeded".asJson, "process" -> WorkflowSbtCapture.encode(_process))
  private val _progress = _fixture.progress.copy(request = _fixture.opaqueProgress.request,
    receipts = Vector.empty, selection = None, assessment = None, review = None, reusable = Vector.empty,
    commits = Vector.empty, effects = _fixture.opaqueProgress.effects :+ _effect)
  private val _prefix = RepositorySyncPurposeRuntime.attemptPrefix(_progress)
  private val _prepared = N.Payload("PurposePrepared", _fixture.request.root, _fixture.actual.observed, purposes = Some(_progress))
  private val _intent = _prepared.copy(stage = "PurposeMaterializing")
  private val _plan = Json.obj("workspaces" -> Vector(
    Json.obj("identity" -> "sm-workflow-repository".asJson, "root" -> _fixture.request.root.asJson),
    Json.obj("identity" -> "purpose-slot-1".asJson, "root" -> "/specification/purpose-slot-1".asJson)).asJson,
    "environment" -> Json.obj("JAVA_HOME" -> "/ordinary/JDK".asJson, "arbitrary.dotted.key" -> Json.Null,
      "snake_case" -> Json.arr("text".asJson, Json.Null)))
  private val _target = M.Target("component", "session", _fixture.context, _fixture.request.root,
    _fixture.context.remoteEndpointIdentity, "12", "native-repository:session:" + _prefix + "-materialize-intent", "",
    _prefix + "-materialize-intent", "12", _prefix + "-prepared", "11", _progress.asJson.noSpaces,
    N.codec.encode(_prepared), N.codec.encode(_intent), _fixture.actual.asJson.noSpaces, _fixture.request.indexTree,
    _plan, "purpose-slot-1", "/specification/purpose-slot-1", M.policy)
  private val _reference = ContextReference("fixture-human-direction", "1")
  private val _artifact = V.WorkflowArtifactReference(_reference.identity, _reference.revision)
  private val _execution = V.WorkflowExecutionEvidence(Vector(_artifact), Some("participant"), None, None)
  private val _input = V.RepositorySyncValidationContinuationAdoptionInput("session", M.targetRecord(_target),
    M.choices.head, "Use the corrected empty fixed destination", _artifact, true, true, _execution)
  private val _direction = M.Direction(_target, M.choices.head, _input.rationale, "participant", _reference, true, true)
  private def _metadata(example: String, rules: String) = afterWord(
    "in spec:repository-sync-formal-failure-continuation, example:" + example + ", rules:" + rules + ", phase:ID12")

  "E1 Exact resultless materialization target" should _metadata("E1", "PM-R1,PM-R6") {
    "preserve complete opaque progress native observations and host names through public conversion" in {
      Given("docs/spec/repository-sync-formal-failure-continuation.md PM-R1,PM-R6 E1: an adjacent prepared candidate and resultless intent with arbitrary journal and host keys")
      val wire = _target.asJson
      val public = M.targetRecord(_target)
      When("the complete description crosses public Record conversion and strict target restoration")
      val restored = take(M.targetC(public))
      Then("the complete candidate progress provider history and nested explicit nulls retain their original meaning")
      restored shouldBe _target
      M.decodeTarget(wire) shouldBe Right(_target)
      M.progress(restored) shouldBe Right(_progress)
      M.actual(restored) shouldBe Right(_fixture.actual)
      restored.preparedPayload shouldBe N.codec.encode(_prepared)
      restored.intentPayload shouldBe N.codec.encode(_intent)
      restored.processPlan shouldBe _plan
      restored.progressSnapshot should include("arbitrary_snake_case")
      restored.progressSnapshot should include("arbitrary.dotted.key")
      restored.actualSnapshot should include("\"mergeHead\":null")
      restored.processPlan.hcursor.downField("environment").downField("arbitrary.dotted.key").focus shouldBe Some(Json.Null)
    }
    "refuse incomplete null extra crossed and malformed opaque observations" in {
      Given("docs/spec/repository-sync-formal-failure-continuation.md PM-R1,PM-R6 E1: the complete exact target and one-field alterations")
      val wire = _target.asJson
      val malformed = wire.asObject.get.keys.toVector.flatMap(key => Vector(wire.mapObject(_.remove(key)),
        wire.mapObject(_.add(key, Json.Null)), wire.mapObject(_.add(key, Json.True)))) ++ Vector(
        wire.mapObject(_.add("unknown", Json.True)), wire.mapObject(_.add("processPlan", "{broken".asJson)),
        wire.mapObject(_.add("preparedPayload", "[]".asJson)))
      val crossed = Vector(_target.copy(sessionId = "other"), _target.copy(preparedRevision = "10"),
        _target.copy(intentRevision = "13"), _target.copy(intentKey = "other-materialize-intent"),
        _target.copy(preparedKey = "other-prepared"), _target.copy(indexTree = "f" * 40),
        _target.copy(endpoint = "other-endpoint"), _target.copy(workspaceRoot = "/other/root"),
        _target.copy(progressSnapshot = _progress.copy(attempt = 1).asJson.noSpaces))
      When("altered observations are offered as the same native materialization")
      val refused = malformed.map(M.decodeTarget) ++ crossed.map(value => M.decodeTarget(value.asJson))
      Then("each refuses without treating missing process evidence as a known failure or a nonstart")
      refused.foreach(_.isLeft shouldBe true)
      _progress.effects.last shouldBe _effect
      _intent.stage shouldBe "PurposeMaterializing"
    }
  }

  "E2 Explicit materialization policy and Human assertions" should _metadata("E2", "PM-R2") {
    "dispatch only the stated policy and require both assertions for authorization" in {
      Given("docs/spec/repository-sync-formal-failure-continuation.md PM-R2 E2: a resultless materialization and its explicit authorization answer")
      val target = RepositorySyncValidationContinuation.PurposeMaterializationTarget(_target)
      val answer = RepositorySyncValidationContinuation.Answer(M.choices.head, "明示された独立した判断\n", true, true)
      When("the existing generic continuation contract selects the target and answer")
      val decoded = RepositorySyncValidationContinuation.decodeAnyTarget(_target.asJson)
      val authorization = RepositorySyncValidationContinuation.decodeAnyAnswer(target, answer.asJson)
      Then("only this exact policy and true lifecycle assertions authorize the new materialization while Defer stays passive")
      decoded shouldBe Right(target)
      target.choices shouldBe M.choices
      authorization shouldBe Right(answer)
      Vector(answer.copy(priorExecutionEnded = false), answer.copy(acceptOriginalUncertainty = false),
        answer.copy(choice = "AuthorizeNewPreparation"), answer.copy(choice = "AuthorizeNewValidation")).foreach(value =>
        RepositorySyncValidationContinuation.decodeAnyAnswer(target, value.asJson).isLeft shouldBe true)
      M.decodeAnswer(answer.copy(choice = "Defer", priorExecutionEnded = false, acceptOriginalUncertainty = false).asJson).isRight shouldBe true
      M.decodeAnswer(answer.asJson.mapObject(_.add("priorWorkerEnded", Json.True))).isLeft shouldBe true
      M.decodeAnswer(answer.asJson.mapObject(_.remove("priorExecutionEnded"))).isLeft shouldBe true
      RepositorySyncValidationContinuation.decodeAnyTarget(_target.asJson.mapObject(_.add("continuationPolicy", "unknown".asJson))).isLeft shouldBe true
      RepositorySyncValidationContinuation.decodeAnyTarget(_target.asJson.mapObject(_.add("continuationPolicy",
        RepositorySyncValidationContinuation.policy.asJson))).isLeft shouldBe true
    }
    "bind the complete target exact artifact and original participant worker through passive preparation" in {
      Given("docs/spec/repository-sync-formal-failure-continuation.md PM-R2 E2: fixture-authored independent direction facts, never live consent")
      val description = V.RepositorySyncValidationContinuationDescriptionResult("session", "adoption-required", M.message,
        "component", M.targetRecord(_target), M.choices, RepositorySyncValidationContinuation.adoptionOperation)
      val answer = V.RepositorySyncValidationContinuationAnswer(M.choices.head, _input.rationale, true, true)
      When("normal external-evidence preparation encodes the supplied exact facts")
      val prepared = take(Preparation.prepareC(description, answer, _artifact, _execution, "participant", "fixture-host",
        Instant.parse("2026-11-04T00:00:00Z")))
      val evidence = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      Then("the existing dedicated purpose binds every fact and crossed worker or artifact membership refuses")
      evidence.purpose shouldBe NativeExternalEvidence.Purpose.RepositoryValidationContinuation.identity
      evidence.binding shouldBe take(M.bindingC("component", _target, answer.choice, answer.rationale,
        "participant", _reference, true, true))
      M.executionEvidenceValid(_input, _target) shouldBe true
      M.executionEvidenceValid(_input.copy(sessionId = "other"), _target) shouldBe false
      M.executionEvidenceValid(_input.copy(executionEvidence = _execution.copy(workerIdentity = Some("other"))), _target) shouldBe false
      M.executionEvidenceValid(_input.copy(executionEvidence = _execution.copy(references = Vector.empty)), _target) shouldBe false
      M.executionEvidenceValid(_input.copy(reference = _artifact.copy(revision = "other")), _target) shouldBe false
      M.bindingC("other", _target, answer.choice, answer.rationale, "participant", _reference, true, true).isSuccess shouldBe false
      M.bindingC("component", _target, answer.choice, answer.rationale, "other", _reference, true, true).isSuccess shouldBe false
      M.bindingC("component", _target, answer.choice, answer.rationale, "participant", _reference, false, true).isSuccess shouldBe false
      M.bindingC("component", _target, "Defer", "Retain UNKNOWN", "participant", _reference, false, false).isSuccess shouldBe true
    }
  }

  "E3 Append-only materialization provenance" should _metadata("E3", "PM-R3,PM-R4") {
    "preserve historical native v3 encoding and add only exact optional direction provenance" in {
      Given("docs/spec/repository-sync-formal-failure-continuation.md PM-R3,PM-R4 E3: historical prepared progress and one exact Human direction")
      val historical = N.codec.encode(_prepared)
      val adopted = _intent.copy(stage = "PurposePrepared", purposeMaterialization = Some(_direction))
      When("native v3 retains that unchanged progress plus the admitted direction")
      val retained = N.codec.encode(adopted)
      val restored = N.codec.decode(retained)
      Then("the historical absent field remains absent and explicit null or malformed provenance refuses")
      historical.hcursor.downField("payload").downField("purposeMaterialization").focus shouldBe None
      N.codec.decode(historical) shouldBe Right(_prepared)
      restored shouldBe Right(adopted)
      restored.toOption.get.purposes shouldBe Some(_progress)
      adopted.purposes.get shouldBe _progress
      adopted.purposes.get.request.records shouldBe _progress.request.records
      adopted.purposes.get.effects shouldBe _progress.effects
      _direction.asJson.as[M.Direction] shouldBe Right(_direction)
      _direction.copy(priorExecutionEnded = false).asJson.as[M.Direction].isLeft shouldBe true
      _direction.copy(choice = "Defer").asJson.as[M.Direction].isLeft shouldBe true
      N.codec.decode(historical.mapObject(fields => fields.add("payload", fields("payload").get.mapObject(
        _.add("purposeMaterialization", Json.Null))))).isLeft shouldBe true
      N.codec.decode(retained.mapObject(fields => fields.add("payload", fields("payload").get.mapObject(
        _.add("purposeMaterialization", Json.obj()))))).isLeft shouldBe true
    }
    "allocate fresh materialization keys while preserving all subsequent normal attempt keys and counters" in {
      Given("docs/spec/repository-sync-formal-failure-continuation.md PM-R4 E3: the original prepared progress and an admitted materialization direction")
      When("only unfinished materialization selects its new effect namespace")
      val original = M.materializationPrefix(_progress, None)
      val continued = M.materializationPrefix(_progress, Some(_direction))
      Then("old preparation and UNKNOWN intent keys remain distinct while later checks and commit retain the normal attempt prefix")
      original shouldBe _prefix
      continued shouldBe _prefix + "-materialization-continuation-12"
      continued + "-materialize-intent" should not be (original + "-materialize-intent")
      M.adoptionKey(_target) shouldBe "materialization-continuation-12-adopted"
      RepositorySyncPurposeRuntime.attemptPrefix(_progress) shouldBe _prefix
      M.progress(_target) shouldBe Right(_progress)
      (_progress.cursor, _progress.attempt, _progress.slotCursor, _progress.commits, _progress.ownership,
        _progress.projectedWorktree, _progress.preparationNamespace, _progress.hostContinuation) shouldBe
        ((0, 0, 0, Vector.empty, None, None, None, None))
      M.validProgress(_progress.copy(receipts = _fixture.progress.receipts)) shouldBe false
      M.validProgress(_progress.copy(cursor = 1)) shouldBe false
      M.validProgress(_progress.copy(commits = Vector("f" * 40))) shouldBe false
      M.validProgress(_progress.copy(candidate = None)) shouldBe false
    }
  }

  "E4 Fixed empty destination admission" should _metadata("E4", "PM-R5") {
    "select the exact disjoint absolute workspace and reject source overlap and duplicate mapping" in {
      Given("docs/spec/repository-sync-formal-failure-continuation.md PM-R5 E4: the ordinary resolved source and fixed Purpose slot registrations")
      def _workspace_plan_with_root_(value: String): Json = _plan.mapObject(fields => fields.add("workspaces", fields("workspaces").get.mapArray(values =>
        values.updated(1, values(1).mapObject(_.add("root", value.asJson))))))
      val duplicate = _plan.mapObject(fields => fields.add("workspaces", fields("workspaces").get.mapArray(values => values :+ values(1))))
      val sourcechanged = _plan.mapObject(fields => fields.add("workspaces", fields("workspaces").get.mapArray(values =>
        values.updated(0, values(0).mapObject(_.add("root", "/another/source".asJson))))))
      When("the current slot mapping is qualified before any materialization intent")
      val selected = take(M.selectWorkspaceC(_plan, _target.root, _target.workspaceId))
      val refused = Vector(_workspace_plan_with_root_(_target.root), _workspace_plan_with_root_(_target.root + "/child"), _workspace_plan_with_root_("/specification"),
        _workspace_plan_with_root_("relative-slot"), _workspace_plan_with_root_("/specification/../slot"), duplicate, sourcechanged)
        .map(plan => M.selectWorkspaceC(plan, _target.root, _target.workspaceId))
      Then("only the unique absolute disjoint mapping is selected and EMPTY limits admit no file content or descendant")
      selected shouldBe M.Host(_plan, _target.workspaceId, _target.workspaceRoot)
      refused.foreach(_.isSuccess shouldBe false)
      M.selectWorkspaceC(_plan, _target.root, "absent-slot").isSuccess shouldBe false
      M.selectWorkspaceC(_plan, _target.root, "sm-workflow-repository").isSuccess shouldBe false
      (M.emptyLimits.maxDepth, M.emptyLimits.maxVisitedDirectories, M.emptyLimits.maxEntries,
        M.emptyLimits.maxEntryBytes, M.emptyLimits.maxTotalBytes) shouldBe ((0, 1, 0, 0L, 0L))
    }
  }

  "E5 Later Purpose ownership and completed prefix" should _metadata("E5", "PM-R1,PM-R3,PM-R4") {
    "retain integrated later ownership repair counters and the actual completed-prefix index" in {
      Given("docs/spec/repository-sync-formal-failure-continuation.md PM-R1,PM-R3,PM-R4 E5: one installed Purpose followed by an accepted later repair with explicit ownership projection")
      val committed = "f" * 40
      val installedindex = "5" * 40
      val pending = _progress.copy(cursor = 1, attempt = 2, slotCursor = 3, commits = Vector(committed),
        baselineTree = Some("7" * 40), ownership = Some(_fixture.continuation),
        projectedWorktree = Some(Vector(P.Content(_fixture.laterPath, Some("corrected baseline plus future test")))),
        preparationNamespace = Some("prepare-continuation-20"), hostContinuation = Some(_fixture.hostDirection), candidate = None)
      val group = RepositorySyncPurposeOwnership.effectiveGroup(pending, pending.plan.get.groups(1))
      val candidate = _fixture.candidate.copy(parent = committed, index = installedindex, changes = group.content,
        expected = group.content.map(content => RepositorySyncPurposeOwnership.expected(pending, content.path).get),
        message = group.message, baselineTree = pending.baselineTree)
      val later = pending.copy(candidate = Some(candidate))
      val prefix = RepositorySyncPurposeRuntime.attemptPrefix(later)
      val observed = _fixture.actual.copy(observed = _fixture.actual.observed.copy(head = committed, tree = "6" * 40))
      val prepared = _prepared.copy(purposes = Some(later))
      val target = _target.copy(frontierRevision = "30", intentRevision = "30", preparedRevision = "29",
        frontierCause = "native-repository:session:" + prefix + "-materialize-intent",
        intentKey = prefix + "-materialize-intent", preparedKey = prefix + "-prepared",
        progressSnapshot = later.asJson.noSpaces, preparedPayload = N.codec.encode(prepared),
        intentPayload = N.codec.encode(prepared.copy(stage = "PurposeMaterializing")),
        actualSnapshot = observed.asJson.noSpaces, indexTree = installedindex)
      val prior = _progress.copy(candidate = Some(_fixture.candidate.copy(commit = committed, projectedIndex = installedindex)))
      val installed = prior.copy(cursor = 1, commits = Vector(committed), candidate = None)
      When("the exact materialization target qualifies that later progress and its actual preceding installed candidate")
      val decoded = M.decodeTarget(target.asJson)
      val expectedindex = take(RepositorySyncPurposePreparationContinuation.completedPrefixIndexC(later, installed, prior))
      Then("the full literal ownership projection and both old continuation meanings survive with no reset or fallback to the original index")
      decoded shouldBe Right(target)
      M.progress(target) shouldBe Right(later)
      M.validProgress(later) shouldBe true
      expectedindex shouldBe installedindex
      expectedindex should not be later.request.indexTree
      later.ownership shouldBe pending.ownership
      later.projectedWorktree shouldBe pending.projectedWorktree
      later.request shouldBe _progress.request
      later.plan shouldBe _progress.plan
      later.effects shouldBe _progress.effects
      (later.cursor, later.attempt, later.slotCursor, later.commits, later.preparationNamespace, later.hostContinuation) shouldBe
        ((1, 2, 3, Vector(committed), Some("prepare-continuation-20"), Some(_fixture.hostDirection)))
      M.materializationPrefix(later, Some(_direction.copy(target = target))) shouldBe
        prefix + "-materialization-continuation-30"
      M.validProgress(later.copy(candidate = Some(candidate.copy(parent = later.request.parent)))) shouldBe false
      RepositorySyncPurposePreparationContinuation.completedPrefixIndexC(later, installed.copy(commits = Vector("9" * 40)), prior).isSuccess shouldBe false
    }
  }
}
