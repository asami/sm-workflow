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
    _paths, Vector("value.txt"), Some(32), N.policy)
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
        _binding(target = _target.copy(maximumFiles = Some(1))))
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
  "AUTO-FILE-RS automatic inventory and lifecycle separation" should {
    "expose structured passive preparation in the real deferred host while retaining Session-only description" in {
      Given("the generated preparation contract and normal deferred RepositorySync service")
      val generated = SmWorkflowComponent.RepositorySyncServiceService.PrepareRepositorySyncRepairScopeCorrectionOperation.specification
      val service = ThreeWorkflowLauncherAssembly.deferredDeclarations.find(_.name == "RepositorySyncService").get
      When("the deferred operation inventory is selected")
      val prepare = service.operations.operations.toVector.find(_.name == N.prepareOperation).get.specification
      val describe = service.operations.operations.toVector.find(_.name == N.describeOperation).get.specification
      Then("preparation uses input or inputResource and the legacy describe contract continues to take only sessionId")
      prepare.request shouldBe generated.request
      prepare.response shouldBe generated.response
      prepare.request.parameters.map(_.name) shouldBe List("input", "inputResource")
      describe.request.parameters.map(_.name) shouldBe List("sessionId")
      ThreeWorkflowLauncherAssembly.publishedMcpOperations should not contain ("RepositorySyncService." + N.prepareOperation)
    }
    "retain unchanged tracked additions without changing factual changed paths or converting v1 history" in {
      Given("a runtime-shaped qualified inventory containing a baseline regression outside the factual diff")
      val paths = Vector("src/test/scala/UnchangedRegressionSpec.scala")
      val scope = RepositorySyncRepairInventory.Scope(RepositorySyncRepairInventory.policy, "session", "11", "10",
        _target.failureReference, "formalTest", _target.localTip, _target.remoteTip, _target.candidateTree,
        paths, "The retained formalTest failure requires updating this baseline regression",
        Vector(RepositorySyncRepairInventory.Entry(paths.head, "100644", "f" * 40)))
      val target = _target.copy(correctionPolicy = N.automaticPolicy, inventory = Some(scope))
      When("the exact v2 target and historical v1 target are decoded")
      val current = N.decodeTarget(target.asJson)
      val legacy = N.decodeTarget(_target.asJson)
      Then("only the explicit v2 inventory adds eligibility and both factual diff and historical representation survive")
      current shouldBe Right(target)
      target.changedPaths shouldBe _paths
      target.changedPaths should not contain paths.head
      legacy shouldBe Right(_target)
      _target.asJson.hcursor.downField("inventory").focus shouldBe None
      N.decodeTarget(target.copy(correctionPolicy = N.policy).asJson).isLeft shouldBe true
      N.decodeTarget(target.copy(inventory = Some(scope.copy(additions = Vector.empty))).asJson).isLeft shouldBe true
      N.decodeTarget(target.copy(inventory = Some(scope.copy(failureIdentity = "foreign"))).asJson).isLeft shouldBe true
    }
    "require only actual lifecycle continuation and refuse a new Human file selection" in {
      Given("one automatically qualified unchanged regression and a fixture-authored lifecycle answer")
      val paths = Vector("src/test/scala/UnchangedRegressionSpec.scala")
      val scope = RepositorySyncRepairInventory.Scope(RepositorySyncRepairInventory.policy, "session", "11", "10",
        _target.failureReference, "formalTest", _target.localTip, _target.remoteTip, _target.candidateTree,
        paths, "Repair the actual retained check", Vector(RepositorySyncRepairInventory.Entry(paths.head, "100644", "f" * 40)))
      val target = _target.copy(correctionPolicy = N.automaticPolicy, inventory = Some(scope))
      def _binding_(selected: Vector[String], choice: String, ended: Boolean, accepted: Boolean) = N.bindingC("component",
        target, choice, selected, "Fixture-owned prior worker lifecycle assertion", "participant", _reference, ended, accepted)
      When("continuation admission separates the fixed automatic inventory from the prior-worker uncertainty")
      val accepted = take(_binding_(Vector.empty, N.lifecycleChoices.head, true, true))
      val refused = Vector(_binding_(paths, N.choices.head, true, true),
        _binding_(_paths.take(1), N.lifecycleChoices.head, true, true),
        _binding_(paths, N.lifecycleChoices.head, true, true),
        _binding_(Vector.empty, N.lifecycleChoices.head, false, true), _binding_(Vector.empty, N.lifecycleChoices.head, true, false))
      Then("the lifecycle-only direction binds exact prepared inventory and never invents a worker-ended answer")
      N.selectBinding(accepted) shouldBe Right(accepted)
      refused.foreach(_.isSuccess shouldBe false)
      _binding_(Vector.empty, "Defer", false, false).isSuccess shouldBe true
      val direction = N.Direction(target, paths, N.lifecycleChoices.head, "Fixture lifecycle acceptance", "participant", _reference, true, true)
      N.checkpointCodec.decode(N.checkpointCodec.encode(N.Checkpoint(direction, "UNKNOWN"))) shouldBe Right(N.Checkpoint(direction, "UNKNOWN"))
    }
  }

  "RS-COUNT-01 versioned inventory correction" should {
    "roundtrip all 251 paths in v3 with no maximumFiles field and refuse cross-version null or unknown fields" in {
      Given("a new complete 251-file candidate and its runtime-shaped qualified inventory")
      val paths = (1 to 251).map(ordinal => f"src/File$ordinal%03d.scala").toVector
      val scope = RepositorySyncRepairInventory.Scope(RepositorySyncRepairInventory.policy, "session", "11", "10",
        _target.failureReference, "formalTest", _target.localTip, _target.remoteTip, _target.candidateTree,
        paths, "Repair the complete retained failed candidate", Vector.empty)
      val target = _target.copy(changedPaths = paths, oldAssignedPaths = paths, maximumFiles = None,
        correctionPolicy = N.uncappedPolicy, inventory = Some(scope))
      val unprepared = target.copy(inventory = None)
      val wire = target.asJson
      val malformed = Vector(wire.mapObject(_.add("maximumFiles", Json.Null)),
        wire.mapObject(_.add("maximumFiles", Json.fromInt(32))), wire.mapObject(_.add("unknown", Json.True)),
        wire.mapObject(_.add("inventory", Json.Null)),
        wire.mapObject(_.add("correctionPolicy", Json.fromString(N.automaticPolicy))),
        unprepared.asJson.mapObject(_.add("inventory", Json.Null)))
      When("the strict target decoder reads prepared and unprepared v3 targets and malformed alternatives")
      val decoded = N.decodeTarget(wire)
      val readonly = N.decodeTarget(unprepared.asJson)
      val refused = malformed.map(N.decodeTarget)
      Then("both stages preserve every path without a count field and all noncanonical cross-version shapes refuse")
      decoded shouldBe Right(target)
      readonly shouldBe Right(unprepared)
      target.oldAssignedPaths.size shouldBe 251
      target.inventory.get.paths shouldBe paths
      wire.hcursor.downField("maximumFiles").focus shouldBe None
      unprepared.asJson.hcursor.downField("maximumFiles").focus shouldBe None
      unprepared.asJson.hcursor.downField("inventory").focus shouldBe None
      refused.foreach(_.isLeft shouldBe true)
    }
    "require preparation before any v3 direction and bind prepared inventory through lifecycle consent only" in {
      Given("a new unprepared target and its later complete qualified 251-file inventory")
      val paths = (1 to 251).map(ordinal => f"src/File$ordinal%03d.scala").toVector
      val target = _target.copy(changedPaths = paths, oldAssignedPaths = paths, maximumFiles = None,
        correctionPolicy = N.uncappedPolicy)
      val scope = RepositorySyncRepairInventory.Scope(RepositorySyncRepairInventory.policy, "session", "11", "10",
        target.failureReference, "formalTest", target.localTip, target.remoteTip, target.candidateTree,
        paths, "The selected failed check requires all changed files", Vector.empty)
      val prepared = target.copy(inventory = Some(scope))
      def _binding_(selected: N.Target, choice: String, selectedpaths: Vector[String] = Vector.empty,
        ended: Boolean = true, accepted: Boolean = true) = N.bindingC("component", selected, choice, selectedpaths,
        "Fixture-owned actual prior worker lifecycle answer", "participant", _reference, ended, accepted)
      val direction = N.Direction(prepared, paths, N.lifecycleChoices.head, "Fixture accepts UNKNOWN effects",
        "participant", _reference, true, true)
      val description = V.RepositorySyncRepairScopeCorrectionDescriptionResult("session", "preparation-required",
        N.preparationMessage, "component", N.targetRecord(target), Vector.empty, N.prepareOperation)
      val answer = V.RepositorySyncRepairScopeCorrectionAnswer(N.lifecycleChoices.head, Vector.empty,
        "Fixture lifecycle answer", true, true)
      val reference = V.WorkflowArtifactReference(_reference.identity, _reference.revision)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      When("direction admission observes the actual preparation stage and lifecycle assertions")
      val premature = (N.choices ++ N.lifecycleChoices).distinct.map(choice => _binding_(target, choice))
      val binding = take(_binding_(prepared, N.lifecycleChoices.head))
      val refused = Vector(_binding_(prepared, N.choices.head, paths),
        _binding_(prepared, N.lifecycleChoices.head, paths.take(1)),
        _binding_(prepared, N.lifecycleChoices.head, ended = false),
        _binding_(prepared, N.lifecycleChoices.head, accepted = false))
      val evidence = P.prepareC(description, answer, reference, execution, "participant", "fixture-issuer",
        Instant.parse("2026-11-03T00:00:00Z"))
      Then("unprepared targets allow no choices or adoption and only prepared lifecycle consent retains the full inventory")
      N.targetChoices(target) shouldBe Vector.empty
      N.targetMessage(target) shouldBe N.preparationMessage
      premature.foreach(_.isSuccess shouldBe false)
      evidence.isSuccess shouldBe false
      N.targetChoices(prepared) shouldBe N.lifecycleChoices
      N.targetMessage(prepared) shouldBe N.automaticMessage
      N.selectBinding(binding) shouldBe Right(binding)
      refused.foreach(_.isSuccess shouldBe false)
      _binding_(prepared, "Defer", ended = false, accepted = false).isSuccess shouldBe true
      val checkpoint = N.Checkpoint(direction, "UNKNOWN")
      N.checkpointCodec.decode(N.checkpointCodec.encode(checkpoint)) shouldBe Right(checkpoint)
      val prematurecheckpoint = checkpoint.copy(direction = direction.copy(target = target))
      N.checkpointCodec.decode(N.checkpointCodec.encode(prematurecheckpoint)).isLeft shouldBe true
      N.checkpointCodec.decode(N.checkpointCodec.encode(checkpoint.copy(externalEffects = "NOT_EXECUTED"))).isLeft shouldBe true
    }
    "retain the actual maximumFiles and original assignment and inventory restrictions of v1 and v2 histories" in {
      Given("historical targets whose actual signed maximumFiles is two")
      val legacy = _target.copy(maximumFiles = Some(2))
      val scope = RepositorySyncRepairInventory.Scope(RepositorySyncRepairInventory.policy, "session", "11", "10",
        legacy.failureReference, "formalTest", legacy.localTip, legacy.remoteTip, legacy.candidateTree,
        _paths.take(2), "Historical qualified file inventory", Vector.empty)
      val automatic = legacy.copy(correctionPolicy = N.automaticPolicy, inventory = Some(scope))
      val malformed = Vector(legacy.copy(maximumFiles = None), legacy.copy(maximumFiles = Some(0)),
        legacy.copy(maximumFiles = Some(129)), legacy.copy(oldAssignedPaths = _paths),
        automatic.copy(maximumFiles = None), automatic.copy(maximumFiles = Some(1)),
        automatic.copy(inventory = None), automatic.copy(inventory = Some(scope.copy(paths = _paths))))
      When("historical target direction and checkpoint decoding retains each original contract")
      val decoded = Vector(legacy, automatic).map(value => N.decodeTarget(value.asJson))
      val refused = malformed.map(value => N.decodeTarget(value.asJson))
      val direction = N.Direction(automatic, scope.paths, N.lifecycleChoices.head, "Historical lifecycle acceptance",
        "participant", _reference, true, true)
      Then("legacy maximumFiles remains present and neither old assignment nor old inventory gains the v3 no-cap meaning")
      decoded shouldBe Vector(Right(legacy), Right(automatic))
      legacy.asJson.hcursor.get[Int]("maximumFiles").toOption shouldBe Some(2)
      automatic.asJson.hcursor.get[Int]("maximumFiles").toOption shouldBe Some(2)
      refused.foreach(_.isLeft shouldBe true)
      _binding(target = legacy, paths = _paths).isSuccess shouldBe false
      N.decodeTarget(legacy.asJson.mapObject(_.add("maximumFiles", Json.Null))).isLeft shouldBe true
      N.decodeTarget(automatic.asJson.mapObject(_.remove("maximumFiles"))).isLeft shouldBe true
      val checkpoint = N.Checkpoint(direction, "UNKNOWN")
      N.checkpointCodec.decode(N.checkpointCodec.encode(checkpoint)) shouldBe Right(checkpoint)
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
