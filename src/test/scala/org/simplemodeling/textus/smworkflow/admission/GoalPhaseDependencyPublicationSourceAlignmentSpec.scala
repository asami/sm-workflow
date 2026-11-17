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
import GoalPhaseOrderedProgression as O
import CanonicalPublicationProgramCorrectionSpecificationFixture as F
import CanonicalDependencySourceSpecificationFixture as C
import SplitPhaseExplicitPartitionFixture.take

/** Values here are semantic fixtures, never native authority or live consent.
  * The installed two-root episode owns physical routing/lifecycle evidence.
  * @since   Nov.  9, 2026
  * @version Nov. 17, 2026
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

  "P8-03 prior adoption and current consumer binding" should {
    "reuse only the same publication/source/Usage/authority across a consumer-only repair" in {
      Given("prospective target251 authority and a changed consumer tree/frontier/generation with the same dependency capture")
      val originalWire = _target.asJson
      val snapshot = F.snapshot.copy(tree = "e" * 40)
      val repair = take(F.controller.acceptC(O.RepairableChecksObserved(F.snapshot,
        F.controller.plan.steps(F.controller.cursor).checks, "SPECIFICATION-ONLY failed consumer checks",
        O.RepairDirective("SPECIFICATION-ONLY consumer repair", Vector("Repair the consumer fixture")))))
      val repairKey = repair.next match {
        case value: O.Semantic => value.key
        case value => fail(s"Expected specification repair work, got $value")
      }
      val inspected = take(repair.acceptC(O.SemanticCompleted(repairKey,
        "SPECIFICATION-ONLY repaired consumer fixture")))
      val controller = take(inspected.acceptC(O.CandidateObserved(snapshot)))
      val capture = F.capture.copy(generation = controller.generation, sourceRevision = "60")
      val candidate = F.candidate.copy(sourceRevision = "60", candidate = Some(snapshot),
        diff = Some("SPECIFICATION-ONLY repaired consumer fixture"), dependencySourceCapture = Some(capture))
      val controllerJson = B.controller(controller)
      val repaired = _target.copy(frontierRevision = "60", candidateRecordRevision = "60",
        candidate = _sorted(candidate.asJson), capture = _sorted(capture.asJson), controller = _sorted(B.controller(controller)))
      When("the existing alignment owner compares original adoption scope with current applicability")
      A.targetValid(repaired) shouldBe true
      A.reusable(_target, repaired) shouldBe true
      val previous = ContextReference(_target.sessionId, "41")
      val receipt = A.Rebinding(previous, repaired)
      A.applicabilityCodec.decode(A.applicabilityCodec.encode(receipt)) shouldBe Right(receipt)
      val savedCandidate = GoalPhaseNativeCandidate.Saved(PrimaryPublicationRepairSpecificationFixture.nativeRecord(60), candidate)
      A.candidateRebinding(_target, savedCandidate, controller, "60") shouldBe Some(repaired)
      Then("the original Human binding remains literal while changed consumer checks are not inherited")
      _target.asJson shouldBe originalWire
      repaired.publication shouldBe _target.publication
      repaired.candidate should not be _target.candidate
      controller.checksReceipt shouldBe F.controller.checksReceipt
      controller.stage shouldBe GoalPhaseOrderedProgression.Stage.Validation
      repaired.publication.get.artifact shouldBe _target.publication.get.artifact
      val publication = _target.publication.get
      val changedPublications = Vector(publication.copy(artifact = ContextReference(publication.artifact.identity, "2")),
        publication.copy(publicationOwner = "another-publisher"), publication.copy(destination = "file:///another-destination"),
        publication.copy(recollectionReference = ContextReference("another-collection", "1")),
        publication.copy(result = publication.result.copy(publication = publication.result.publication.copy(request =
          publication.result.publication.request.copy(input = publication.result.publication.request.input.copy(requestIdentity = "replacement-at-same-SNAPSHOT"))))))
      changedPublications.foreach(value => A.reusable(_target, repaired.copy(publication = Some(value))) shouldBe false)
      val changedPurpose = controller.plan.copy(steps = controller.plan.steps.updated(controller.cursor,
        controller.plan.steps(controller.cursor).copy(purpose = "A different consumer purpose")))
      val changedChecks = controller.plan.copy(revision = "current-check-selection",
        steps = controller.plan.steps.updated(controller.cursor,
          controller.plan.steps(controller.cursor).copy(checks = controller.plan.steps(controller.cursor).checks :+ "repair-regression")))
      Given("the consumer repair adds a regression fixture and the ordinary owner selects different current checks under the same admitted process authority")
      val policy = io.circe.parser.parse(repaired.processPolicy).toOption.get
      val selected = policy.hcursor.get[GoalPhaseNativeValidation.Policy]("validation").toOption.get
      val currentChecks = selected.copy(revision = selected.revision + 1, checks = selected.checks :+
        GoalPhaseNativeValidation.Check("repair-regression", selected.checks.map(_.ordinal).max + 1, WorkflowSbtValidation.Kind.Test))
      val currentPolicy = _sorted(policy.mapObject(_.add("validation", currentChecks.asJson)))
      val currentCapture = capture.copy(plan = ContextReference(changedChecks.identity, changedChecks.revision),
        planSelection = Some(ContextReference(capture.sessionId + ":ordered-development-reconstruction:59", "1")))
      val currentCandidate = candidate.copy(dependencySourceCapture = Some(currentCapture))
      val currentSelection = repaired.copy(candidateRecordRevision = repaired.candidateRecordRevision,
        processPolicy = currentPolicy, capture = _sorted(currentCapture.asJson),
        candidate = _sorted(currentCandidate.asJson),
        controller = _sorted(controllerJson.mapObject(_.add("plan", changedChecks.asJson))))
      When("the artifact owner separates original approval meaning from current verification selection")
      GoalPhaseDependencySourceRevision.captureValid(currentCapture) shouldBe true
      A.targetValid(currentSelection) shouldBe true
      A.sameProcessAuthority(repaired.processPolicy, currentPolicy) shouldBe true
      A.reusable(_target, currentSelection) shouldBe true
      Then("current check selection is retained for real new validation, without borrowing a prior check PASS or asking for another artifact answer")
      currentChecks.checks.map(_.identity) should contain("repair-regression")
      currentSelection.processPolicy should not be _target.processPolicy
      currentSelection.publication shouldBe _target.publication
      val newEffect = _sorted(policy.mapObject(_.add("processPlan", policy.hcursor.get[Json]("processPlan").toOption.get
        .mapObject(_.add("newPublicationAuthority", true.asJson)))))
      A.sameProcessAuthority(repaired.processPolicy, newEffect) shouldBe false
      A.sameProcessAuthority(repaired.processPolicy, _sorted(policy.mapObject(_.add("unknownTopLevel", true.asJson)))) shouldBe false
      Vector(repaired.copy(componentInstance = "other-component"), repaired.copy(participant = "another-participant"),
        repaired.copy(sessionId = "another-session"), repaired.copy(processPolicy = "{}"), repaired.copy(publicationPolicy = "{}"),
        repaired.copy(publication = None), repaired.copy(candidateRecordRevision = repaired.candidateRecordRevision,
          controller = _sorted(controllerJson.mapObject(_.add("plan", changedPurpose.asJson)))),
        repaired.copy(candidateRecordRevision = repaired.candidateRecordRevision,
          controller = _sorted(controllerJson.mapObject(_.add("repairs", (-1).asJson).add("ordinaryRepairs", (-1).asJson)))),
        repaired.copy(capture = _sorted(capture.copy(postStageGuard = capture.postStageGuard.copy(head = "f" * 40)).asJson)))
        .foreach(value => A.reusable(_target, value) shouldBe false)
      val wire = A.applicabilityCodec.encode(receipt)
      A.applicabilityCodec.decode(wire.mapObject(_.add("inventedHuman", true.asJson))).isLeft shouldBe true
      A.applicabilityCodec.decode(A.applicabilityCodec.encode(receipt.copy(previous = ContextReference("another-session", "41")))).isLeft shouldBe true
    }

    "keep consumer Usage and literal exclusions distinct from current check-program selection" in {
      Given("the actual-shaped typed canonical consumption contract and an independently valid current verification mapping")
      val previous = C.consumption
      val selected = previous.copy(programs = previous.programs.map(p => p.copy(check = p.check + "-repair-selection")))
      GoalPhaseDependencySourceRevision.consumptionValid(previous) shouldBe true
      GoalPhaseDependencySourceRevision.consumptionValid(selected) shouldBe true
      When("only verification selection changes under the normal source/check owner")
      A.sameConsumptionUsage(previous, selected) shouldBe true
      Then("a changed source workspace/root, excluded input or expected dependency mode still changes Usage")
      val changed = Vector(selected.copy(primaryWorkspace = "another-consumer-workspace"),
        selected.copy(sourceRoot = "/specification/another-selected-source"),
        selected.copy(nonInputExclusions = selected.nonInputExclusions :+ "literal-new-exclusion"),
        selected.copy(expectedModes = selected.expectedModes.map(_.copy(mode = "100755"))))
      changed.foreach(value => A.sameConsumptionUsage(previous, value) shouldBe false)
    }

    "recollect actual normal record order without another publication or an artifact-byte comparison" in {
      Given("a specification-owned in-memory normal publication algorithm, never a live publication or Human acceptance")
      import org.goldenport.cncf.processexecution.*
      import org.goldenport.cncf.workspace.WorkspaceId
      final class Memory extends LocalPublication.Port {
        val rows = scala.collection.mutable.Map.empty[String, Json]
        var starts = 0
        var nextTermination: ProcessExecutionTermination = ProcessExecutionTermination.Exited(0)
        var unavailable = false
        def loadC(key: String) = Consequence.success(if (unavailable && key.endsWith(":result")) None else rows.get(key))
        def createC(key: String, value: Json): Consequence[Unit] =
          if (rows.contains(key)) Consequence.stateConflict("Immutable specification duplicate")
          else { rows(key) = value; Consequence.unit }
        def sourceC(target: LocalPublication.Target) = Consequence.success(C.publication.result.publication.request.source)
        def newIdentity() = "specification-normal-publication"
        def executeC(target: LocalPublication.Target): Consequence[SbtExecutionAttempt] = {
          starts += 1
          val process = WorkflowSbtCapture.decode(PrimaryPublicationRepairSpecificationFixture.process(
            "SPECIFICATION-ONLY publication outcome", nextTermination, identity = "specification-dependency-publication")).toOption.get
          Consequence.success(SbtExecutionAttempt.Observed(SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC(target.workspace))),
            SbtOperation.PublishLocal, process)))
        }
      }
      def initial(memory: Memory): P.PublicationObservation = {
        val old = C.publication.result.publication.request
        val input = old.input.copy(requestIdentity = "specification-first-normal-publication", explicit = true)
        val published = take(LocalPublication.ensureC(input, old.target, old.participant, memory))
        C.publication.copy(result = P.CollectionResult(published.status, published.reason, published))
      }
      val memory = new Memory
      val observation = initial(memory)
      val rows = memory.rows.toMap
      val starts = memory.starts
      When("the existing collectors recheck the same adopted normal publication twice")
      take(P.currentPublicationC(observation, memory)) shouldBe ()
      take(P.currentPublicationC(observation, memory)) shouldBe ()
      Then("no ensure/publish or successful consumer-check receipt is created by applicability")
      memory.rows.toMap shouldBe rows
      memory.starts shouldBe starts
      memory.unavailable = true
      P.currentPublicationC(observation, memory).isSuccess shouldBe false
      for (termination <- Vector(ProcessExecutionTermination.Exited(0), ProcessExecutionTermination.Exited(1), ProcessExecutionTermination.TimedOut)) {
        val replaced = new Memory
        val original = initial(replaced)
        replaced.nextTermination = termination
        val old = original.result.publication.request
        take(LocalPublication.ensureC(old.input.copy(requestIdentity = "specification-new-normal-attempt", explicit = true),
          old.target, old.participant, replaced))
        P.currentPublicationC(original, replaced).isSuccess shouldBe false
      }
      val foreign = new Memory
      val original = initial(foreign)
      val old = original.result.publication.request
      take(LocalPublication.ensureC(old.input.copy(requestIdentity = "specification-foreign-owner", ownerSessionId = Some("different-owner"),
        explicit = true), old.target, old.participant, foreign))
      P.currentPublicationC(original, foreign).isSuccess shouldBe false
    }
  }

  "Ordinary recovery entrypoint dispatch" should {
    "prepare and round-trip exact v2 artifact evidence through the normal passive preparation path" in {
      Given("an explicit applicability v2 description and independently supplied artifact, recollection and answer references")
      val observation = CommittedNoninputApplicabilitySpecificationFixture.observation
      val target = _target.copy(schemaVersion = A.applicabilityTargetSchema, publication = Some(observation))
      val description = A.description(target)
      val reference = V.WorkflowArtifactReference(_reference.identity, _reference.revision)
      val input = V.GoalPhasePrerequisitePublicationRecoveryInput(target.sessionId, description.target, A.adoptionChoice,
        "SPECIFICATION-ONLY independent artifact choice", reference, None)
      val supplied = take(B.recoveryRecordC(input.toRecord()))
      val execution = V.WorkflowExecutionEvidence(Vector(reference,
        V.WorkflowArtifactReference(observation.artifact.identity, observation.artifact.revision),
        V.WorkflowArtifactReference(observation.recollectionReference.identity, observation.recollectionReference.revision)),
        Some(target.participant), None, None)
      val occurred = java.time.Instant.parse("2026-11-09T00:00:00Z")
      When("normal passive preparation encodes the selected independent evidence and the retained codec decodes it")
      val prepared = take(org.simplemodeling.textus.smworkflow.preparation.NativePrerequisitePublicationRecoveryPreparation.prepareC(
        description, supplied, reference, execution, target.participant, "specification-independent-host", occurred))
      val evidence = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      val missing = org.simplemodeling.textus.smworkflow.preparation.NativePrerequisitePublicationRecoveryPreparation.prepareC(
        description, supplied, reference, execution.copy(references = Vector(reference)), target.participant,
        "specification-independent-host", occurred)
      Then("the full outer selector retains the exact v2 answer binding while missing producer references still refuse")
      B.recoveryInputC(supplied).isSuccess shouldBe true
      evidence.purpose shouldBe B.purpose
      evidence.binding shouldBe take(A.bindingC(target.componentInstance, target, supplied.choice,
        supplied.rationale, target.participant, _reference))
      B.selectBinding(evidence.binding) shouldBe Right(evidence.binding)
      take(A.targetC(supplied.target)) shouldBe target
      missing.isSuccess shouldBe false
    }
    "admit exact v1 and applicability v2 inputs and select their own independent evidence bindings" in {
      Given("the legacy exact-source alignment and an explicit v2 alignment over the shared applicability relation")
      val applicable = _target.copy(schemaVersion = A.applicabilityTargetSchema,
        publication = Some(CommittedNoninputApplicabilitySpecificationFixture.observation))
      val targets = Vector(_target, applicable)
      val reference = V.WorkflowArtifactReference(_reference.identity, _reference.revision)
      val inputs = targets.map(target => V.GoalPhasePrerequisitePublicationRecoveryInput(target.sessionId,
        GoalPhaseNativeMetadata.record(target.asJson), A.adoptionChoice,
        "SPECIFICATION-ONLY independent artifact choice", reference, None))
      val bindings = targets.map(target => take(A.bindingC(target.componentInstance, target, A.adoptionChoice,
        "SPECIFICATION-ONLY independent artifact choice", target.participant, _reference)))
      When("the outer recovery input and evidence selectors receive the exact public records")
      val admitted = inputs.map(B.recoveryInputC)
      val selected = bindings.map(B.selectBinding)
      val converted = inputs.map(input => take(B.recoveryRecordC(input.toRecord())))
      val readmitted = converted.map(B.recoveryInputC)
      Then("both closed schema versions pass input admission without creating Human proof or a native permit")
      all(admitted.map(_.isSuccess)) shouldBe true
      selected shouldBe bindings.map(Right(_))
      all(readmitted.map(_.isSuccess)) shouldBe true
      converted.zip(targets).foreach { (input, target) =>
        take(A.targetC(input.target)) shouldBe target
        input.choice shouldBe A.adoptionChoice
        input.reference shouldBe reference
        input.hostCorrection shouldBe None
      }
      targets.foreach(target => A.choices(target) shouldBe Vector(A.adoptionChoice, "Defer"))
    }
    "refuse malformed, null, mismatched v1, unknown-version and unavailable-choice inputs at the outer dispatch" in {
      Given("one valid applicability v2 input and independent corruptions of its exact public target or choice")
      val target = _target.copy(schemaVersion = A.applicabilityTargetSchema,
        publication = Some(CommittedNoninputApplicabilitySpecificationFixture.observation))
      val input = V.GoalPhasePrerequisitePublicationRecoveryInput(target.sessionId, GoalPhaseNativeMetadata.record(target.asJson),
        A.adoptionChoice, "SPECIFICATION-ONLY independent artifact choice",
        V.WorkflowArtifactReference(_reference.identity, _reference.revision), None)
      val malformed = Vector(target.asJson.mapObject(_.add("undeclared", true.asJson)),
        target.asJson.mapObject(_.add("publication", Json.Null)),
        target.copy(schemaVersion = A.targetSchema).asJson,
        target.copy(schemaVersion = "sm-workflow.goal-phase-dependency-publication-source-alignment.v3").asJson)
      val invalid = malformed.map(value => input.copy(target = GoalPhaseNativeMetadata.record(value))) :+
        input.copy(choice = "AuthorizePrimaryPublicationOnce")
      When("the normal closed recovery input route receives each malformed target or unavailable choice")
      val refused = invalid.map(B.recoveryInputC)
      val unknown = A.isTargetSchema("sm-workflow.goal-phase-dependency-publication-source-alignment.v3")
      Then("no version fallback or new artifact permission is inferred")
      all(refused.map(_.isSuccess)) shouldBe false
      unknown shouldBe false
      A.isTargetSchema(A.targetSchema) shouldBe true
      A.isTargetSchema(A.applicabilityTargetSchema) shouldBe true
    }
    "refuse crossed owners, null facts, wrong versions and invalid choices in outer independent evidence selection" in {
      Given("one exact v2 artifact evidence binding and independent changes to its target or answer")
      val target = _target.copy(schemaVersion = A.applicabilityTargetSchema,
        publication = Some(CommittedNoninputApplicabilitySpecificationFixture.observation))
      val binding = take(A.bindingC(target.componentInstance, target, A.adoptionChoice,
        "SPECIFICATION-ONLY independent artifact choice", target.participant, _reference))
      val body = binding.hcursor.downField("prerequisitePublicationRecovery").focus.get
      val answer = body.hcursor.downField("answer").focus.get
      val alteredtargets = Vector(target.asJson.mapObject(_.add("publication", Json.Null)),
        target.copy(schemaVersion = A.targetSchema).asJson,
        target.copy(schemaVersion = "sm-workflow.goal-phase-dependency-publication-source-alignment.v3").asJson,
        target.asJson.mapObject(_.add("undeclared", true.asJson)))
      val invalid = alteredtargets.map(value => binding.mapObject(_.add("prerequisitePublicationRecovery",
        body.mapObject(_.add("target", value))))) ++ Vector(
        binding.mapObject(_.add("componentInstance", "crossed-owner".asJson)),
        binding.mapObject(_.add("prerequisitePublicationRecovery", body.mapObject(_.add("answer",
          answer.mapObject(_.add("participant", "crossed-participant".asJson)))))),
        binding.mapObject(_.add("prerequisitePublicationRecovery", body.mapObject(_.add("answer",
          answer.mapObject(_.add("choice", "AuthorizePrimaryPublicationOnce".asJson)))))))
      When("the outer evidence selector dispatches and strictly validates every supplied binding")
      val refused = invalid.map(B.selectBinding)
      Then("all malformed or crossed bindings refuse while exact v2 still selects only its own input evidence")
      all(refused.map(_.isLeft)) shouldBe true
      B.selectBinding(binding) shouldBe Right(binding)
    }
  }

  "Declared non-input artifact alignment" should {
    "select the shared relation only through target v2 and preserve independent Human artifact choice" in {
      Given("a current canonical capture and an independently recollected earlier publication with a separate complete applicability fact")
      val observation = CommittedNoninputApplicabilitySpecificationFixture.observation
      val target = _target.copy(schemaVersion = A.applicabilityTargetSchema, publication = Some(observation))
      When("the existing artifact description and Direction use the new explicit target schema")
      val direction = A.Direction(target, A.adoptionChoice, "SPECIFICATION-ONLY separate artifact judgment", _reference)
      Then("the relation matches the captured source without changing the original successful publication")
      A.observationIssues(F.capture, Some(observation)) shouldBe empty
      A.targetValid(target) shouldBe true
      A.choices(target) shouldBe Vector(A.adoptionChoice, "Defer")
      A.directionValid(direction) shouldBe true
      take(A.targetC(GoalPhaseNativeMetadata.record(target.asJson))) shouldBe target
      A.codec.decode(A.codec.encode(direction)) shouldBe Right(direction)
      A.targetValid(target.copy(schemaVersion = A.targetSchema)) shouldBe false
      A.targetValid(_target.copy(schemaVersion = A.applicabilityTargetSchema)) shouldBe false
      observation.result.publication.request.source should not be P.sourceOf(F.capture.postStageGuard)
    }
    "reject crossed applicability and explicit null in the raw publication proposal" in {
      Given("a valid collected relation and the current canonical capture")
      val observation = CommittedNoninputApplicabilitySpecificationFixture.observation
      val fact = observation.result.applicability.get
      val crossed = observation.copy(result = observation.result.copy(applicability = Some(fact.copy(root = "/crossed"))))
      val nulled = observation.asJson.mapObject(_.add("result", observation.result.asJson.mapObject(_.add("applicability", Json.Null))))
      When("the source matcher or raw proposal decoder receives crossed or null relation data")
      val issues = A.observationIssues(F.capture, Some(crossed))
      val decoded = _target.copy(schemaVersion = A.applicabilityTargetSchema, publication = Some(observation)).asJson
        .mapObject(_.add("publication", nulled)).as[A.Target]
      Then("neither can permit current artifact reuse")
      issues should not be empty
      decoded.isLeft shouldBe true
    }
  }

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
