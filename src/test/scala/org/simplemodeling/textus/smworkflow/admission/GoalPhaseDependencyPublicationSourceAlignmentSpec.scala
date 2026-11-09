package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, Printer}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseDependencyPublicationSourceAlignment as A
import GoalPhasePrerequisitePublicationRecovery as B
import GoalPhasePublishedDependencySourceAdoption as P
import CanonicalPublicationProgramCorrectionSpecificationFixture as F
import CanonicalDependencySourceSpecificationFixture as C
import SplitPhaseExplicitPartitionFixture.take

/** Values here are semantic fixtures, never native authority or live consent.
  * The installed two-root episode owns physical routing/lifecycle evidence.
  * @since   Nov.  9, 2026
  * @version Nov.  9, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseDependencyPublicationSourceAlignmentSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _sorted(value: Json): String = Printer.noSpaces.copy(sortKeys = true).print(value)
  private val _dependency = LocalPublication.Target("old-consumer-publisher", C.binding.dependency.identity,
    "old-consumer-workspace", Some(C.binding.identity), beforeChecks = true)
  private val _target = A.Target(A.targetSchema, C.origin.componentInstance, F.direction.participant, F.capture.sessionId,
    "40", _sorted(B.controller(F.controller)), "40", _sorted(F.candidate.asJson), _sorted(F.capture.asJson),
    Vector(A.TargetRoot(_dependency, C.original.target.binding.root)), F.correction.correctedProcessPolicy,
    PrimaryPublicationRepairSpecificationFixture.publicationPolicy(Vector(F.primary, _dependency)),
    Some(C.publication), Vector.empty)
  private val _reference = ContextReference("specification-independent-artifact-human", "1")
  private def _root(workspace: String): Consequence[String] = Consequence.success(
    if (workspace == _dependency.workspace) C.original.target.binding.root else C.binding.root)

  "Canonical dependency publisher preflight" should {
    "scan the old dependency after a primary-first target without invoking a publisher" in {
      Given("a canonical source capture and a primary-first list whose later dependency still points at the old root")
      var observed = Vector.empty[String]
      When("the read-only all-target root scan runs")
      val mismatches = take(A.mismatchesC(F.candidate, Vector(F.primary, _dependency), workspace => {
        observed = observed :+ workspace
        _root(workspace)
      }))
      Then("only the actual obsolete dependency target is returned; the primary target is never executed or resolved")
      mismatches shouldBe _target.targets
      observed shouldBe Vector(C.binding.dependency.workspace, _dependency.workspace)
    }
    "allow a different workspace alias resolving to the same physical root" in {
      Given("the same canonical capture and an independently named dependency workspace")
      When("typed Git root results resolve both workspace names to the selected root")
      val mismatches = take(A.mismatchesC(F.candidate, Vector(_dependency), _ => Consequence.success(C.binding.root)))
      Then("the legacy publisher is still applicable and no artifact choice is inferred")
      mismatches shouldBe empty
    }
    "retain legacy candidate behavior and reject a crossed canonical workspace" in {
      Given("an original candidate without a canonical capture and a current canonical candidate")
      When("the legacy scan or the crossed current workspace is presented")
      val legacy = take(A.mismatchesC(F.candidate.copy(dependencySourceCapture = None), Vector(_dependency),
        _ => fail("Legacy candidate must not resolve a canonical workspace")))
      val crossed = A.mismatchesC(F.candidate, Vector(_dependency), _ => Consequence.success("/specification/crossed"))
      Then("legacy routing stays unchanged while the crossed canonical root refuses before any publication")
      legacy shouldBe empty
      crossed.isSuccess shouldBe false
    }
  }
  "Separately qualified producer publication" should {
    "accept a genuinely successful-shaped collect from a differently named producer owner and workspace" in {
      Given("a normal successful collect whose producer Component, actual source and declared root match the current canonical guard")
      val observation = C.publication
      When("qualification keeps producer and consumer names independent")
      val issues = A.observationIssues(F.capture, Some(observation))
      Then("its exact provenance is retained without reattributing the old consumer publication")
      observation.result.publication.request.target.workspace should not be _dependency.workspace
      observation.publicationOwner should not be _target.sessionId
      issues shouldBe empty
      A.targetValid(_target) shouldBe true
      A.choices(_target) shouldBe Vector(A.adoptionChoice, "Defer")
    }
    "keep an old-root Published result truthful while refusing it for the current source" in {
      Given("a complete Exited-zero collect with the old repository in its own publisher policy")
      val old = C.publication.copy(publisherProcessPolicy = C.publication.publisherProcessPolicy.replace(
        C.binding.root, C.original.target.binding.root))
      When("the old successful publication is qualified against the new source")
      val issues = A.observationIssues(F.capture, Some(old))
      Then("success remains success and the root mismatch prevents adoption")
      P.observationValid(old) shouldBe true
      old.result.publication.status shouldBe "published"
      issues should contain("The actual publisher workspace must resolve to the selected canonical dependency root")
    }
    "refuse a wrong Component, crossed program, failed publication or stale source" in {
      Given("independent changes to one concrete part of the producer observation")
      val original = C.publication
      val result = original.result.publication
      val component = original.copy(result = original.result.copy(publication = result.copy(request = result.request.copy(
        target = result.request.target.copy(component = "crossed.Component")))))
      val program = original.copy(publisherProcessPolicy = original.publisherProcessPolicy.replace(
        "specification-dependency-publication", "crossed-program"))
      val failed = original.copy(result = original.result.copy(publication = result.copy(status = "failed")))
      val stale = original.copy(result = original.result.copy(publication = result.copy(request = result.request.copy(
        source = result.request.source.copy(head = "f" * 40)))))
      When("each changed observation is qualified separately")
      val issues = Vector(component, program, failed, stale).map(o => A.observationIssues(F.capture, Some(o)))
      Then("none can consume the selected canonical source artifact")
      all(issues.map(_.nonEmpty)) shouldBe true
    }
    "offer only passive Defer without a supplied current observation" in {
      Given("the actual mismatch and no producer observation")
      val target = _target.copy(publication = None, applicabilityIssues = A.observationIssues(F.capture, None))
      When("the existing recovery description is made")
      val description = A.description(target)
      Then("it supplies the concrete target and actionable producer evidence requirements without inventing an artifact")
      A.targetValid(target) shouldBe true
      description.choices shouldBe Vector("Defer")
      description.message should include("collectLocalPublication")
      A.bindingC(target.componentInstance, target, A.adoptionChoice, "SPECIFICATION-ONLY", target.participant,
        _reference).isSuccess shouldBe false
    }
    "retain complete unsuccessful proposals for Defer without weakening the successful observation contract" in {
      Given("a complete normal collect-shaped proposal with a failed result and truthful failed status")
      val original = C.publication
      val failed = original.copy(result = original.result.copy(status = "failed",
        publication = original.result.publication.copy(status = "failed")))
      val target = _target.copy(publication = Some(failed), applicabilityIssues = A.observationIssues(F.capture, Some(failed)))
      When("the closed alignment description decodes the supplied proposal and exact target")
      val decoded = take(A.observationC(GoalPhaseNativeMetadata.record(failed.asJson)))
      val described = take(A.targetC(GoalPhaseNativeMetadata.record(target.asJson)))
      Then("failure is preserved with only Defer while the original successful-observation decoder still refuses it")
      decoded shouldBe failed
      described shouldBe target
      A.choices(described) shouldBe Vector("Defer")
      failed.asJson.as[P.PublicationObservation].isLeft shouldBe true
      A.directionValid(A.Direction(target, A.adoptionChoice, "SPECIFICATION-ONLY", _reference)) shouldBe false
    }
  }
  "Exact independent artifact Direction" should {
    "retain the full opaque native source and controller and select the existing Human purpose binding" in {
      Given("the complete current target and a separate specification-only Human reference")
      val direction = A.Direction(_target, A.adoptionChoice, "SPECIFICATION-ONLY artifact decision", _reference)
      When("the public target and retained Direction round-trip through their strict codecs")
      val decoded = take(A.targetC(GoalPhaseNativeMetadata.record(_target.asJson)))
      val binding = take(A.bindingC(_target.componentInstance, _target, direction.choice, direction.rationale,
        _target.participant, _reference))
      Then("the whole controller, candidate, capture and producer remain literal; a binding alone grants no native permit")
      decoded shouldBe _target
      decoded.controller shouldBe _sorted(B.controller(F.controller))
      decoded.candidate shouldBe _sorted(F.candidate.asJson)
      A.codec.decode(A.codec.encode(direction)) shouldBe Right(direction)
      B.selectBinding(binding) shouldBe Right(binding)
      B.selectBinding(binding.mapObject(_.add("owner", "crossed".asJson))).isLeft shouldBe true
    }
    "refuse null, unknown, crossed controller/source and self-referential Human answers" in {
      Given("one valid target and its independent artifact answer")
      val direction = A.Direction(_target, A.adoptionChoice, "SPECIFICATION-ONLY", _reference)
      When("a caller adds fields, removes native provenance or uses the future native record as Human proof")
      val extra = _target.asJson.mapObject(_.add("undeclared", true.asJson))
      val absent = _target.asJson.mapObject(_.add("publication", Json.Null))
      val crossed = _target.copy(capture = _sorted(F.capture.copy(sessionId = "crossed").asJson))
      val self = direction.copy(reference = ContextReference(_target.sessionId, "41"))
      Then("strict public decoding and positive Direction admission refuse without creating a permit")
      extra.as[A.Target].isLeft shouldBe true
      absent.as[A.Target].isLeft shouldBe true
      A.targetValid(crossed) shouldBe false
      A.directionValid(self) shouldBe false
      A.directionValid(direction.copy(choice = "AuthorizePrimaryPublicationOnce")) shouldBe false
    }
  }
}
