package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.file.Path
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseDependencySourceRevision as R
import DependencySourceRevisionSpecificationFixture as F
import DependencyValidationSpecificationFixture as D

/** Pure versioned consumption and succession specifications; fixtures are not consent.
  * @since   Oct. 31, 2026
  * @version Oct. 31, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class DependencySourceConsumptionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _consumption = R.Consumption(F.proposal.consumption.primaryWorkspace, F.binding.root,
    F.proposal.excludedPaths, F.proposal.consumption.expectedModes, F.proposal.consumption.programs)
  private val _initial = F.direction.copy(target = F.target.copy(schemaVersion = R.sourceSchema,
    proposal = F.proposal.copy(schemaVersion = R.sourceSchema, consumption = _consumption)))
  private val _path = "src/main/scala/Additional.scala"
  private val _approval = D.approvals.head.copy(proposal = D.approvals.head.proposal.copy(
    identity = "additional-source-consumption", paths = Vector(_path),
    acceptance = Vector("Qualify the additional source through existing checks")),
    decision = ContextReference("additional-source-approval", "1"))

  private def _successor(original: R.Direction, previous: Option[R.Direction] = None): (R.Direction, R.ScopeSuccessor) = {
    val actual = previous.getOrElse(original)
    val paths = original.target.binding.paths :+ _path
    val binding = original.target.binding.copy(revision = actual.target.binding.revision + 1, paths = paths)
    val consumption = _consumption.copy(expectedModes = _consumption.expectedModes :+ R.ExpectedMode(_path, "100644"))
    val coverage = original.target.proposal.coverage :+
      R.Coverage(_approval.decision, _approval.proposal.acceptance, binding.checks.map(_.identity))
    val proposal = original.target.proposal.copy(schemaVersion = R.sourceSchema,
      binding = ContextReference(binding.identity, binding.revision.toString), consumption = consumption, coverage = coverage)
    val guard = original.target.preStageGuard.copy(selectedFiles = original.target.preStageGuard.selectedFiles :+
      R.fileIdentity(_path, Some("object Additional\n".getBytes.toVector)))
    val target = original.target.copy(schemaVersion = R.sourceSchema, questionIdentity = "source-only-question",
      sourceRevision = (BigInt(actual.target.pendingRevision) + 2).toString,
      pendingRevision = (BigInt(actual.target.pendingRevision) + 3).toString,
      generation = actual.target.generation + 2, requestingResult = ContextReference("source-only-request", "1"),
      binding = binding, proposal = proposal, preStageGuard = guard, runtime = None,
      sourceSuccession = Some(R.SourceSuccession(original.directive, Vector(_approval), actual.directive)))
    val next = R.Direction(target, ContextReference("source-only-direction-" + target.generation, "1"),
      original.participant, "Explicit source-only succession")
    val scope = R.ScopeSuccessor(R.sourceScopeSchema, original.directive, binding, Vector(_approval), consumption,
      coverage, Some(next))
    (next, scope)
  }

  private def _runtime(original: R.Direction): R.Direction = {
    val (next, scope) = _successor(original)
    val identity = "b1901b58-3218-4aa1-9f9e-b57eb94e625c"
    val directory = "/specification/installations/" + identity
    val installation = R.RuntimeSelection(identity, "d56bf541-5133-4156-91e0-3a06ac0d88db",
      directory + "/runtime-installation.json",
      Vector(R.RuntimeArtifact(org.goldenport.cncf.cli.InstalledRuntimeDistribution.ROOT_COORDINATE,
        directory + "/runtime/0000.jar", "/specification/publication/cncf.jar")),
      org.goldenport.cncf.cli.InstalledRuntimeDistribution.COMPONENT_IDENTITY,
      R.RuntimeArtifact(org.goldenport.cncf.cli.InstalledRuntimeDistribution.COMPONENT_COORDINATE,
        directory + "/components/textus-sm-workflow.car", "/specification/publication/sm-workflow.car"))
    val consumption = R.LegacyConsumption(_consumption.primaryWorkspace, _consumption.sourceRoot, identity,
      ContextReference("cncf-installed-runtime:" + identity, "1"), _consumption.nonInputExclusions,
      scope.consumption.expectedModes, _consumption.programs)
    next.copy(directive = ContextReference("retained-runtime-direction", "1"), target = next.target.copy(
      schemaVersion = R.runtimeSchema, proposal = next.target.proposal.copy(schemaVersion = R.schema, consumption = consumption),
      runtime = Some(R.RuntimeSuccession(original.directive, Vector(_approval), installation)), sourceSuccession = None))
  }

  private def _same_scope_successor(original: R.Direction): (R.Direction, R.ScopeSuccessor) = {
    val (extended, _) = _successor(original)
    val binding = original.target.binding.copy(revision = original.target.binding.revision + 1)
    val proposal = original.target.proposal.copy(schemaVersion = R.sourceSchema,
      binding = ContextReference(binding.identity, binding.revision.toString), consumption = _consumption)
    val next = extended.copy(target = extended.target.copy(binding = binding, proposal = proposal,
      preStageGuard = original.target.preStageGuard,
      sourceSuccession = Some(R.SourceSuccession(original.directive, Vector.empty, original.directive))))
    val scope = R.ScopeSuccessor(R.sourceScopeSchema, original.directive, binding, Vector.empty,
      _consumption, original.target.proposal.coverage, Some(next))
    (next, scope)
  }

  private def _source_declaration(next: R.Direction): GoalPhaseNativeRun.Declaration =
    GoalPhaseNativeRun.Declaration(null, null, null, null, null,
      dependencySourceRevisions = Vector(next.target.binding),
      dependencySourceConsumptions = Vector(next.target.proposal.consumption))

  "Versioned source consumption" should {
    "encode exactly five active fields and its explicit schema without image authority" in {
      Given("an active source-only consumption")
      val expected = Json.obj("schemaVersion" -> R.consumptionSchema.asJson,
        "primaryWorkspace" -> _consumption.primaryWorkspace.asJson, "sourceRoot" -> _consumption.sourceRoot.asJson,
        "nonInputExclusions" -> _consumption.nonInputExclusions.asJson, "expectedModes" -> _consumption.expectedModes.asJson,
        "programs" -> _consumption.programs.asJson)
      When("the active and sum-type codecs encode and decode it")
      val wire = _consumption.asJson
      val record: R.ConsumptionRecord = _consumption
      Then("the closed object contains only the declared business inputs and schema marker")
      wire shouldBe expected
      wire.as[R.Consumption] shouldBe Right(_consumption)
      record.asJson shouldBe expected
      wire.as[R.ConsumptionRecord] shouldBe Right(record)
    }

    "round trip the exact historical seven-field shape and values without rewriting it" in {
      Given("the original image-based consumption record")
      val legacy = F.proposal.consumption
      val expected = Json.obj("primaryWorkspace" -> legacy.primaryWorkspace.asJson,
        "sourceRoot" -> legacy.sourceRoot.asJson, "runtimeImage" -> "fixture-image".asJson,
        "manifestReference" -> ContextReference("fixture-manifest", "1").asJson,
        "nonInputExclusions" -> legacy.nonInputExclusions.asJson, "expectedModes" -> legacy.expectedModes.asJson,
        "programs" -> legacy.programs.asJson)
      When("a retained legacy record is read and encoded again")
      val restored = expected.as[R.ConsumptionRecord]
      Then("the legacy variant preserves every field and never acquires a current schema")
      restored shouldBe Right(legacy)
      restored.toOption.get.asJson.noSpaces shouldBe expected.noSpaces
      legacy.asJson shouldBe expected
      expected.as[R.Consumption].isLeft shouldBe true
    }

    "reject unknown null absent hybrid and open active schemas and malformed historical constraints" in {
      Given("otherwise valid active and legacy source consumptions")
      val active = _consumption.asJson
      val legacy = F.proposal.consumption.asJson
      val invalid = Vector(active.mapObject(_.add("schemaVersion", "future".asJson)),
        active.mapObject(_.add("schemaVersion", Json.Null)), active.mapObject(_.remove("schemaVersion")),
        active.mapObject(_.add("runtimeImage", "image".asJson)),
        active.mapObject(_.add("manifestReference", ContextReference("manifest", "1").asJson)),
        active.mapObject(_.add("extra", Json.True)), legacy.mapObject(_.add("schemaVersion", R.consumptionSchema.asJson)),
        legacy.mapObject(_.add("schemaVersion", Json.Null)), legacy.mapObject(_.remove("runtimeImage")),
        legacy.mapObject(_.add("runtimeImage", "".asJson)), legacy.mapObject(_.add("manifestReference", Json.Null)))
      When("the discriminating closed decoder receives each unsupported payload")
      val results = invalid.map(_.as[R.ConsumptionRecord].isLeft)
      Then("no omitted marker or mixed contract silently becomes active consumption")
      results shouldBe Vector.fill(invalid.size)(true)
      legacy.as[R.LegacyConsumption].isRight shouldBe true
    }

    "decode active and explicit historical declarations through ordinary host configuration" in {
      Given("a pure normal-host definition containing both declared variants")
      val host = ThreeWorkflowNormalHostFixture.definition(Path.of("/specification/primary"))
      val declaration = host.copy(goal = host.goal.copy(dependencySourceRevisions = Vector(F.binding),
        dependencySourceConsumptions = Vector(_consumption, F.proposal.consumption)))
      When("the existing public definition codec decodes its encoded declaration")
      val restored = ThreeWorkflowRuntimeParameters.decodeDefinitionC(ThreeWorkflowRuntimeParameters.encode(declaration))
      Then("the host retains both exact variants for active selection and historical reconstruction")
      SplitPhaseExplicitPartitionFixture.take(restored) shouldBe declaration
    }
  }

  "Source-only v5 qualification" should {
    "update the same active scope using inherited approval coverage without a new scope decision" in {
      Given("an active source whose existing admission already covers every selected path")
      val before = _initial.asJson
      val (next, scope) = _same_scope_successor(_initial)
      val declaration = _source_declaration(next)
      When("normal scope selection receives the exact current binding and no additional scope approval")
      val selected = R.scopeC(_initial, D.approvals, declaration, Some(next))
      Then("the explicit v5 direction selects a scope with empty additions and unchanged inherited obligations")
      R.sourceSuccessorValid(_initial, next, D.approvals) shouldBe true
      R.sourcePreviousValid(_initial, next.target, None) shouldBe true
      SplitPhaseExplicitPartitionFixture.take(selected) shouldBe Some(scope)
      R.scopeValid(_initial, scope) shouldBe true
      R.scopeAuthenticated(_initial, Some(scope), D.approvals, Some(next)) shouldBe true
      R.consumptionPreflightC(_initial, Some(scope)).isSuccess shouldBe true
      next.asJson.as[R.Direction] shouldBe Right(next)
      scope.asJson.as[R.ScopeSuccessor] shouldBe Right(scope)
      next.target.sourceSuccession.get.asJson.as[R.SourceSuccession] shouldBe Right(next.target.sourceSuccession.get)
      next.target.binding.paths shouldBe _initial.target.binding.paths
      next.target.proposal.coverage shouldBe _initial.target.proposal.coverage
      next.target.predecessor.approvals shouldBe _initial.target.predecessor.approvals
      next.target.repairProgress shouldBe _initial.target.repairProgress
      _initial.asJson shouldBe before
    }

    "migrate a legacy initial source under its existing approvals with only the latest active declaration" in {
      Given("an immutable historical source and a host selecting only its new active revision")
      val original = F.direction
      val before = original.asJson
      val (next, scope) = _same_scope_successor(original)
      val host = ThreeWorkflowNormalHostFixture.definition(Path.of("/specification/primary"))
      val definition = host.copy(goal = host.goal.copy(dependencySourceRevisions = Vector(next.target.binding),
        dependencySourceConsumptions = Vector(next.target.proposal.consumption)))
      When("current configuration selects active consumption while retained records keep their historical codecs")
      val restored = SplitPhaseExplicitPartitionFixture.take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
        ThreeWorkflowRuntimeParameters.encode(definition)))
      val selected = R.scopeC(original, D.approvals, _source_declaration(next), Some(next))
      Then("migration preserves old approvals and bytes without requiring image fields in the current host list")
      restored shouldBe definition
      restored.goal.dependencySourceConsumptions shouldBe Vector(_consumption)
      restored.goal.dependencySourceRevisions should not contain original.target.binding
      restored.goal.dependencySourceConsumptions should not contain original.target.proposal.consumption
      original.asJson.as[R.Direction] shouldBe Right(original)
      R.sourceSuccessorValid(original, next, D.approvals) shouldBe true
      R.sourcePreviousValid(original, next.target, None) shouldBe true
      SplitPhaseExplicitPartitionFixture.take(selected) shouldBe Some(scope)
      R.scopeAuthenticated(original, Some(scope), D.approvals, Some(next)) shouldBe true
      R.consumptionPreflightC(original, None).isSuccess shouldBe false
      R.consumptionPreflightC(original, Some(scope)).isSuccess shouldBe true
      next.asJson.as[R.Direction] shouldBe Right(next)
      scope.asJson.as[R.ScopeSuccessor] shouldBe Right(scope)
      next.target.predecessor.approvals shouldBe original.target.predecessor.approvals
      original.asJson shouldBe before
    }

    "refuse missing or crossed inherited coverage even when no additional scope approval is needed" in {
      Given("a same-scope active successor with the complete inherited approval union")
      val (next, scope) = _same_scope_successor(_initial)
      val missing = next.target.proposal.coverage.dropRight(1)
      val crossed = next.target.proposal.coverage.updated(0, next.target.proposal.coverage.head.copy(
        approval = ContextReference("unrelated-inherited-approval", "1")))
      When("an empty-additional-approval direction drops or substitutes inherited coverage")
      val invalid = Vector(missing, crossed).map(coverage => next.copy(target = next.target.copy(
        proposal = next.target.proposal.copy(coverage = coverage))))
      Then("neither the target nor its scope can erase or manufacture the existing acceptance obligations")
      invalid.map(R.sourceSuccessorValid(_initial, _, D.approvals)) shouldBe Vector(false, false)
      invalid.map(_.asJson.as[R.Direction].isLeft) shouldBe Vector(true, true)
      invalid.map(direction => R.scopeValid(_initial, scope.copy(coverage = direction.target.proposal.coverage,
        consumptionDirection = Some(direction)))) shouldBe Vector(false, false)
      R.sourceSuccessorValid(_initial, next, D.approvals) shouldBe true
    }

    "retain nonempty approval requirements for legacy runtime and standalone ordinary scopes" in {
      Given("a valid historical runtime successor and an active same-scope source successor")
      val original = F.direction
      val runtime = _runtime(original)
      val (next, scope) = _same_scope_successor(_initial)
      val ordinary = scope.copy(consumptionDirection = None)
      val legacyordinary = scope.copy(schemaVersion = R.scopeSchema, predecessor = original.directive,
        consumption = original.target.proposal.consumption, consumptionDirection = None)
      val emptyruntime = runtime.copy(target = runtime.target.copy(runtime = runtime.target.runtime.map(
        _.copy(approvals = Vector.empty))))
      When("empty additional vectors are presented outside an explicit v5 source direction")
      val selected = R.scopeC(_initial, D.approvals, _source_declaration(next))
      Then("ordinary no-change selection stays absent and historical runtime approval rules stay strict")
      SplitPhaseExplicitPartitionFixture.take(selected) shouldBe None
      R.scopeValid(_initial, ordinary) shouldBe false
      R.scopeValid(original, legacyordinary) shouldBe false
      ordinary.asJson.as[R.ScopeSuccessor].isLeft shouldBe true
      legacyordinary.asJson.as[R.ScopeSuccessor].isLeft shouldBe true
      R.runtimeSuccessorValid(original, emptyruntime, Vector.empty) shouldBe false
      emptyruntime.asJson.as[R.Direction].isLeft shouldBe true
      R.runtimeSuccessorValid(original, runtime, Vector(_approval)) shouldBe true
    }

    "round trip an initial target and capture and admit effective active execution" in {
      Given("an initial active direction over the existing immutable source guards")
      val capture = F.capture.copy(schemaVersion = R.sourceCaptureSchema, direction = _initial)
      When("target capture and execution eligibility use that initial direction")
      val eligible = R.consumptionPreflightC(_initial, None)
      Then("initial records use v5 even without a scope and retain the original repair snapshot")
      _initial.target.asJson.as[R.Target] shouldBe Right(_initial.target)
      capture.asJson.as[R.Capture] shouldBe Right(capture)
      eligible.isSuccess shouldBe true
      _initial.target.repairProgress shouldBe F.target.repairProgress
      _initial.target.asJson.hcursor.downField("sourceSuccession").focus shouldBe None
      _initial.target.copy(schemaVersion = R.schema).asJson.as[R.Target].isLeft shouldBe true
      F.target.copy(proposal = _initial.target.proposal).asJson.as[R.Target].isLeft shouldBe true
      capture.copy(schemaVersion = R.schema).asJson.as[R.Capture].isLeft shouldBe true
    }

    "admit source-only succession without a runtime image change and preserve all original facts" in {
      Given("an active source and a genuine-shaped later scope approval")
      val originalwire = _initial.asJson
      val (next, scope) = _successor(_initial)
      val capture = F.capture.copy(schemaVersion = R.sourceCaptureSchema, direction = _initial,
        generation = next.target.generation + 1, sourceRevision = (BigInt(next.target.pendingRevision) + 2).toString,
        preStageGuard = next.target.preStageGuard, postStageGuard = next.target.preStageGuard, scope = Some(scope))
      When("normal successor scope capture and effective-consumption validators inspect it")
      val accepted = R.sourceSuccessorValid(_initial, next, Vector(_approval)) &&
        R.sourcePreviousValid(_initial, next.target, None)
      Then("v5 source-only records are eligible without altering original snapshots or legacy history")
      accepted shouldBe true
      next.target.runtime shouldBe None
      R.scopeValid(_initial, scope) shouldBe true
      R.scopeAuthenticated(_initial, Some(scope), Vector(_approval), Some(next)) shouldBe true
      R.scopeAuthenticated(_initial, Some(scope), Vector(_approval)) shouldBe false
      next.asJson.as[R.Direction] shouldBe Right(next)
      scope.asJson.as[R.ScopeSuccessor] shouldBe Right(scope)
      capture.asJson.as[R.Capture] shouldBe Right(capture)
      R.consumptionPreflightC(_initial, Some(scope)).isSuccess shouldBe true
      next.target.repairProgress shouldBe _initial.target.repairProgress
      _initial.asJson shouldBe originalwire
      next.target.asJson.mapObject(_.add("sourceSuccession", Json.Null)).as[R.Target].isLeft shouldBe true
      next.target.copy(schemaVersion = R.runtimeSchema).asJson.as[R.Target].isLeft shouldBe true
    }

    "append explicit active consumption after legacy runtime history and authenticate its actual predecessor" in {
      Given("a retained valid runtime successor whose image constraints remain historical")
      val original = F.direction
      val runtime = _runtime(original)
      val before = runtime.asJson
      val (next, scope) = _successor(original, Some(runtime))
      When("source migration names the actual current direction and fresh effective consumption")
      val accepted = R.sourceSuccessorValid(original, next, Vector(_approval)) &&
        R.sourcePreviousValid(original, next.target, Some(runtime))
      Then("legacy decoding remains strict while only the explicitly appended v5 consumption can execute")
      R.runtimeSuccessorValid(original, runtime, Vector(_approval)) shouldBe true
      runtime.asJson.as[R.Direction] shouldBe Right(runtime)
      accepted shouldBe true
      R.scopeValid(original, scope) shouldBe true
      R.sourcePreviousValid(original, next.target, None) shouldBe false
      R.consumptionPreflightC(original, None).isSuccess shouldBe false
      val oldscope = scope.copy(schemaVersion = R.runtimeScopeSchema, binding = runtime.target.binding,
        consumption = runtime.target.proposal.consumption, coverage = runtime.target.proposal.coverage,
        consumptionDirection = Some(runtime))
      R.consumptionPreflightC(original, Some(oldscope)).isSuccess shouldBe false
      R.consumptionPreflightC(original, Some(scope)).isSuccess shouldBe true
      runtime.asJson shouldBe before
      next.target.repairProgress shouldBe original.target.repairProgress
    }

    "bind each further source succession to its actual new frontier binding and accumulated obligations" in {
      Given("an existing active successor and the same retained original source")
      val (previous, _) = _successor(_initial)
      val (next, scope) = _successor(_initial, Some(previous))
      val marker = next.target.sourceSuccession.get
      val crossed = Vector(next.target.copy(sourceSuccession = Some(marker.copy(previousDirection = _initial.directive))),
        next.target.copy(generation = previous.target.generation),
        next.target.copy(sourceRevision = previous.target.pendingRevision),
        next.target.copy(binding = next.target.binding.copy(revision = previous.target.binding.revision)),
        next.target.copy(proposal = next.target.proposal.copy(coverage = _initial.target.proposal.coverage)))
      When("the actual traversed previous direction is checked separately from the retained original")
      val results = crossed.map(R.sourcePreviousValid(_initial, _, Some(previous)))
      Then("only a fresh append over the exact current reference preserves prior obligations")
      R.sourceSuccessorValid(_initial, next, Vector(_approval)) shouldBe true
      R.sourcePreviousValid(_initial, next.target, Some(previous)) shouldBe true
      R.scopeValid(_initial, scope) shouldBe true
      results shouldBe Vector.fill(crossed.size)(false)
    }

    "refuse changed parents approvals participants modes roots programs exclusions and revisions" in {
      Given("an otherwise valid active source successor")
      val (next, scope) = _successor(_initial)
      val target = next.target
      val marker = target.sourceSuccession.get
      val consumption = scope.consumption match {
        case active: R.Consumption => active
        case _ => fail("Expected the active fixture")
      }
      val changed = Vector(
        next.copy(target = target.copy(sourceSuccession = Some(marker.copy(parent = ContextReference("wrong-parent", "1"))))),
        next.copy(target = target.copy(sourceSuccession = Some(marker.copy(approvals = Vector.empty)))),
        next.copy(participant = "another-participant"), next.copy(directive = _initial.directive),
        next.copy(target = target.copy(generation = _initial.target.generation)),
        next.copy(target = target.copy(binding = target.binding.copy(root = "/other/source"))),
        next.copy(target = target.copy(proposal = target.proposal.copy(consumption = consumption.copy(
          expectedModes = consumption.expectedModes.updated(0, consumption.expectedModes.head.copy(mode = "100755")))))),
        next.copy(target = target.copy(proposal = target.proposal.copy(consumption = consumption.copy(
          programs = consumption.programs.map(_.copy(program = "other-program")))))),
        next.copy(target = target.copy(proposal = target.proposal.copy(excludedPaths = Vector.empty,
          consumption = consumption.copy(nonInputExclusions = Vector.empty)))))
      When("structural successor validation applies the original contract and actual approvals")
      val results = changed.map(R.sourceSuccessorValid(_initial, _, Vector(_approval)))
      Then("all substitutions refuse without narrowing inherited scope or dropping acceptance")
      results shouldBe Vector.fill(changed.size)(false)
      R.sourceSuccessorValid(_initial, next, Vector.empty) shouldBe false
      R.sourceSuccessorValid(_initial, next, Vector(_approval.copy(decision = ContextReference("invented", "1")))) shouldBe false
    }
  }
}
