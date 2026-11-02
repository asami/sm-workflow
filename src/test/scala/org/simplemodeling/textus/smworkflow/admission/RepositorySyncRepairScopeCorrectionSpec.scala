package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.preparation.NativeRepositoryRepairScopeCorrectionPreparation as P
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncRepairScopeCorrection as N
import RepositorySyncManagedGitObservation as G
import SplitPhaseExplicitPartitionFixture.take

/** Pure supplied contracts only; these fixtures are never live Human evidence,
  * issued native work, execution observations or admission certificates.
  * @since Nov. 3, 2026
  * @version Nov. 3, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncRepairScopeCorrectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _context = RepositorySyncManagedFetch.StartContext("selection", "invocation", "participant", "repository",
    "main", "origin", "main", "owned-upstream", "FULL_STATE")
  private val _paths = Vector("docs/repair.md", "src/test/scala/ProductCheck.scala", "value.txt")
  private val _observed = G.Observed("main", "a" * 40, "d" * 40,
    _paths.map(path => G.DirtyPath('M', ' ', path, None)), Some("b" * 40), Some(G.Counts(1, 1)), Some("e" * 40))
  private val _target = N.Target("component", "session", _context, "/specification/repository", "/specification/upstream.git",
    "12", "native-repair-issued:session", "session:native-repair:11", "12", "1", "11",
    ContextReference("session:native-pending-merge", "1"), "7", ContextReference("session:native-validation-result-3", "1"), "10",
    RepositorySyncNativeMergeValidation.Receipt("formalTest", "exited", Some(1), "Actual fixture check failed", ""),
    "a" * 40, "b" * 40, "c" * 40, G.Native(_observed, Some("b" * 40)), "c" * 40,
    _paths, Vector("value.txt"), 32, N.policy)
  private val _reference = ContextReference("existing-fixture-human-direction", "2")
  private def _binding(target: N.Target = _target, paths: Vector[String] = _paths.take(2),
    participant: String = "participant", ended: Boolean = true, accepted: Boolean = true,
    reference: ContextReference = _reference) = N.bindingC("component", target, N.choices.head, paths,
      "Specification-owned explicit scope selection", participant, reference, ended, accepted)

  "RS-CHECK-REPAIR-SCOPE normal deferred registration" should {
    "publish current repair scope correction in the normal deferred RepositorySync service" in {
      Given("the normal deferred services and actual generated repair scope correction metadata")
      val service = ThreeWorkflowLauncherAssembly.deferredDeclarations.find(_.name == "RepositorySyncService").get
      val generateddescribe = SmWorkflowComponent.RepositorySyncServiceService
        .DescribeRepositorySyncRepairScopeCorrectionOperation.specification
      val generatedadopt = SmWorkflowComponent.RepositorySyncServiceService
        .AdoptRepositorySyncRepairScopeCorrectionOperation.specification
      When("the deferred RepositorySync service exposes its generated description and adoption contracts")
      val describe = service.operations.operations.toVector.find(_.name == N.describeOperation).get.specification
      val adopt = service.operations.operations.toVector.find(_.name == N.adoptionOperation).get.specification
      Then("description takes only sessionId and adoption retains its structured input or Resource contract")
      describe.request shouldBe generateddescribe.request
      describe.response shouldBe generateddescribe.response
      describe.request.parameters.map(_.name) shouldBe List("sessionId")
      adopt.request shouldBe generatedadopt.request
      adopt.response shouldBe generatedadopt.response
      adopt.request.parameters.map(_.name) shouldBe List("input", "inputResource")
      ThreeWorkflowLauncherAssembly.publishedMcpOperations should not contain ("RepositorySyncService." + N.describeOperation)
      ThreeWorkflowLauncherAssembly.publishedMcpOperations should not contain ("RepositorySyncService." + N.adoptionOperation)
    }
  }

  "RS-CHECK-REPAIR-SCOPE exact correction target" should {
    "roundtrip the complete current candidate target and reject missing null unknown and mistyped fields" in {
      Given("an explicit available unclaimed failed-check target with one narrow issued file and three permissible changed paths")
      val wire = _target.asJson
      val malformed = wire.asObject.get.keys.toVector.flatMap(key => Vector(wire.mapObject(_.remove(key)),
        wire.mapObject(_.add(key, Json.Null)), wire.mapObject(_.add(key, Json.True)))) :+
        wire.mapObject(_.add("silentPermission", Json.True))
      When("the exact target decoder consumes each supplied public contract")
      val actual = N.decodeTarget(wire)
      val refused = malformed.map(N.decodeTarget)
      Then("the complete target survives and every incomplete extended or mistyped shape refuses")
      actual shouldBe Right(_target)
      refused.foreach(_.isLeft shouldBe true)
    }
    "refuse crossed Session WorkOrder references revisions candidate scope and FULL_STATE meaning" in {
      Given("the exact current failed-check target")
      val wrong = Vector(_target.copy(sessionId = "foreign"), _target.copy(oldContinuationId = "foreign:native-repair:11"),
        _target.copy(expectedRevision = "13"), _target.copy(entityRevision = "2"), _target.copy(sourceRevision = "12"),
        _target.copy(candidateReference = ContextReference("foreign:native-pending-merge", "1")),
        _target.copy(failureReference = ContextReference("session:native-validation-result-3", "2")),
        _target.copy(candidateTree = "f" * 40), _target.copy(remoteTip = "f" * 40),
        _target.copy(context = _context.copy(syncMode = "OTHER")), _target.copy(changedPaths = _paths.reverse),
        _target.copy(oldAssignedPaths = Vector("foreign.txt")), _target.copy(changedPaths = Vector("value.txt")),
        _target.copy(failedCheck = _target.failedCheck.copy(termination = "unknown", exitCode = None)))
      When("strict decoding checks every relationship independently of live state admission")
      val refused = wrong.map(value => N.decodeTarget(value.asJson))
      Then("none can become a structurally valid correction of another issue candidate or failure")
      refused.foreach(_.isLeft shouldBe true)
    }
  }
  "RS-CHECK-REPAIR-SCOPE explicit Human direction" should {
    "bind only a bounded sorted changed-path subset to the retained participant and terminal uncertainty assertions" in {
      Given("the current target and explicit independent Human selection of the two nonconflict changed files")
      val wrongpaths = Vector(Vector.empty[String], _paths.take(2).reverse, Vector(_paths.head, _paths.head),
        Vector("foreign.txt"), Vector("../outside.txt"))
      When("the binding checks selection participant reference and explicit lifecycle assertions")
      val binding = take(_binding())
      val refused = wrongpaths.map(paths => _binding(paths = paths)) ++ Vector(_binding(participant = "other"),
        _binding(ended = false), _binding(accepted = false), _binding(reference = ContextReference("", "1")),
        _binding(target = _target.copy(maximumFiles = 1)))
      Then("only the original participant's exact finite subset can be bound while UNKNOWN remains acknowledged")
      N.selectBinding(binding) shouldBe Right(binding)
      refused.foreach(_.isSuccess shouldBe false)
      N.selectBinding(binding.mapObject(_.add("componentInstance", Json.fromString("foreign")))).isLeft shouldBe true
      val scope = binding.hcursor.downField("repositoryRepairScopeCorrection").focus.get
      val crossedanswer = scope.hcursor.downField("answer").focus.get.mapObject(_.add("participant", Json.fromString("other")))
      val crossedscope = scope.mapObject(_.add("answer", crossedanswer))
      val crossedbinding = binding.mapObject(_.add("repositoryRepairScopeCorrection", crossedscope))
      N.selectBinding(crossedbinding).isLeft shouldBe true
    }
    "preserve complete large rationale and strict answer checkpoint uncertainty while Defer remains passive" in {
      Given("a complete explicit answer whose multibyte rationale exceeds 64 KiB")
      val answer = N.Answer(N.choices.head, _paths.take(2), "判断の根拠" * 10000, true, true)
      val direction = N.Direction(_target, answer.paths, answer.choice, answer.rationale, "participant", _reference, true, true)
      val checkpoint = N.Checkpoint(direction, "UNKNOWN")
      When("the exact public answer and typed native checkpoint codecs consume that supplied direction")
      val decoded = N.decodeAnswer(answer.asJson)
      val retained = N.checkpointCodec.decode(N.checkpointCodec.encode(checkpoint))
      Then("all rationale survives and no checkpoint can relabel original UNKNOWN effects as nonexecution")
      decoded shouldBe Right(answer)
      retained shouldBe Right(checkpoint)
      N.checkpointCodec.decode(N.checkpointCodec.encode(checkpoint.copy(externalEffects = "NOT_EXECUTED"))).isLeft shouldBe true
      val defer = answer.copy(choice = "Defer", paths = Vector.empty, priorExecutionEnded = false, acceptOriginalUncertainty = false)
      N.decodeAnswer(defer.asJson) shouldBe Right(defer)
      N.decodeAnswer(answer.copy(priorExecutionEnded = false).asJson).isLeft shouldBe true
      N.decodeAnswer(answer.asJson.mapObject(_.add("permission", Json.True))).isLeft shouldBe true
      N.decodeAnswer(defer.copy(paths = _paths.take(1)).asJson).isLeft shouldBe true
    }
    "prepare only supplied description answer reference worker issuer and time through the existing evidence format" in {
      Given("a specification-authored description and independent Human-shaped fixture inputs without a live Human claim")
      val description = V.RepositorySyncRepairScopeCorrectionDescriptionResult("session", "adoption-required", N.message,
        "component", N.targetRecord(_target), N.choices, N.adoptionOperation)
      val answer = V.RepositorySyncRepairScopeCorrectionAnswer(N.choices.head, _paths.take(2), "Fixture-authored direction", true, true)
      val reference = V.WorkflowArtifactReference(_reference.identity, _reference.revision)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      val time = Instant.parse("2026-11-03T00:00:00Z")
      When("passive preparation encodes those supplied facts and rejects crossed inputs")
      val prepared = take(P.prepareC(description, answer, reference, execution, "participant", "fixture-issuer", time))
      val refused = Vector(P.prepareC(description.copy(sessionId = "foreign"), answer, reference, execution, "participant", "fixture-issuer", time),
        P.prepareC(description, answer, reference, execution.copy(workerIdentity = Some("foreign")), "participant", "fixture-issuer", time),
        P.prepareC(description, answer.copy(acceptOriginalUncertainty = false), reference, execution, "participant", "fixture-issuer", time))
      Then("the existing typed evidence record carries the exact selection and invalid supplied provenance refuses")
      prepared.recordIdentity should startWith(N.purpose + ":")
      val encoded = prepared.record.getString(NativeExternalEvidence.storageField).get
      val evidence = take(NativeExternalEvidence.decodeStorageC(encoded))
      evidence.purpose shouldBe N.purpose
      N.selectBinding(evidence.binding) shouldBe Right(evidence.binding)
      refused.foreach(_.isSuccess shouldBe false)
    }
  }
  "RS-CHECK-REPAIR-SCOPE repair accounting" should {
    "count administrative scope successors as their original repair while retaining separate candidate failures" in {
      Given("one original failed candidate and its administrative corrected continuation plus a later failed candidate")
      val original = "session:native-repair:11"
      val corrected = original + ":scope:13"
      val later = "session:native-repair:30"
      When("the repair budget normalizes only the exact native administrative suffix")
      val identities = Vector(original, corrected, later).map(N.budgetIdentity).distinct
      Then("correction does not add or reset the original attempt and a new failed candidate still counts")
      identities shouldBe Vector(original, later)
      N.budgetIdentity(original + ":scope:013") shouldBe original + ":scope:013"
      N.budgetIdentity(original + ":scope:13:other") shouldBe original + ":scope:13:other"
    }
  }
}
