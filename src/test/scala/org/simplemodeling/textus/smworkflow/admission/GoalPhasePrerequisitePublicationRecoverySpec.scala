package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, Printer}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseDependencyValidation.given
import GoalPhaseDependencyValidation as D
import GoalPhasePrerequisitePublicationRecovery as R
import GoalPhaseDependencySourceRevision as S
import GoalPhasePrerequisitePublicationUnknownRecovery as U
import DependencyValidationSpecificationFixture as F
import SplitPhaseExplicitPartitionFixture.take
import org.simplemodeling.textus.smworkflow.value as V

/** Complete semantic data, never an actual Human provider or native receipt. */
private[admission] object PrimaryPublicationContinuationSpecificationFixture {
  import GoalPhaseOrderedProgression as O
  private def _sorted(value: Json): String = Printer.noSpaces.copy(sortKeys = true).print(value)
  val primary = LocalPublication.Target("primary-delivery", F.requester.identity, F.requester.workspace, beforeChecks = true)
  val dependency = LocalPublication.Target("dependency-delivery", F.dependency.identity, F.dependency.workspace,
    Some(F.binding.identity), beforeChecks = true)
  val candidate = GoalPhaseNativeCandidate.Payload("20", F.binding.step, Vector("screen.scala"), Vector("screen.scala"),
    Some(O.Snapshot(F.primaryBase.head, "f" * 40)), Vector("screen.scala"), Some("Preserved primary implementation"),
    dependencyAdmission = Some(F.admission), dependencyStaged = F.paths, dependency = Some(F.capture))
  val guard = DependencySourceRevisionSpecificationFixture.old.copy(excludedFiles = Vector.empty)
  val primarySource = LocalPublication.Source(candidate.candidate.get.head, candidate.candidate.get.tree, Vector.empty)
  val controller = Json.obj("stage" -> "Validation".asJson, "plan" -> F.plan.asJson, "base" -> F.primaryBase.asJson,
    "generation" -> 1L.asJson, "completed" -> Json.arr(), "cursor" -> 0.asJson)
  val consumption = S.Consumption(F.requester.workspace, F.binding.root, Vector.empty,
    F.paths.map(S.ExpectedMode(_, "100644")), Vector(S.CheckProgram("primary-test", "primary-validation", "test", "fixture-primary-check")))
  val processPolicy = _sorted(Json.obj("processPlan" -> Json.obj("sbtPrograms" -> Json.arr(Json.obj(
    "target" -> primary.workspace.asJson, "operation" -> "publish-local".asJson,
    "environment" -> Json.obj("EXPLICIT_TEST_DEPENDENCY_ROOT" -> consumption.sourceRoot.asJson))))))
  private def _request(target: LocalPublication.Target, source: LocalPublication.Source): LocalPublication.Request = {
    val input = LocalPublicationRuntime.prerequisiteInput("same-session", candidate, target)
    LocalPublication.Request(input.copy(requestIdentity = input.requestIdentity + ":prerequisite:0"), target, source,
      "participant", "Specification actual prerequisite", None, Vector.empty)
  }
  val unknown = LocalPublication.Result("uncertain", "Original retained UNKNOWN", _request(dependency,
    LocalPublication.Source(guard.head, guard.indexTree, Vector.empty)))
  private val _capture = ProcessExecutionCapture(Vector.empty, 0L, false)
  val failed = LocalPublication.Result("failed", "Actual typed nonzero primary", _request(primary, primarySource),
    Some(WorkflowSbtCapture.encode(ProcessExecutionResult(ProcessExecutionTermination.Exited(7), _capture, _capture,
      Vector.empty, 1L, "specification-primary-failure"))))
  val unknownTarget = U.Target(U.schema, "fixture-component", "participant", "same-session", "25", _sorted(controller),
    F.plan, F.primaryBase, 1L, Vector.empty, GoalPhaseRepairProgress.Snapshot(0, 0, F.plan.repairLimit), "25", candidate,
    F.admission, Vector.empty, unknown, "{\"publication\":1}", processPolicy, guard, Some(guard), None,
    ContextReference("accepted-original-primary-result", "1"), None)
  val originalDirection = U.Direction(unknownTarget, ContextReference("original-artifact-human", "1"),
    "SPECIFICATION-OWNED accept original dependency artifacts", Some("AcceptExistingArtifactsWithUnknownEffects"))
  val sourceRequired = R.PrimaryTarget(R.primarySchema, R.PrimaryPhase.SourceRequired, "fixture-component", "participant", "same-session", "26",
    _sorted(controller), F.plan, F.primaryBase, 1L, Vector.empty, unknownTarget.repairProgress, "25", candidate,
    originalDirection, ContextReference("same-session", "26"), unknownTarget.requestingResult, Vector(dependency, primary),
    unknownTarget.publicationPolicy, processPolicy, failed, primarySource, guard, None, Vector.empty, Vector.empty)
  val corrected = guard.copy(dirty = Vector(S.Dirty("A", "M", F.paths.head, None)),
    selectedFiles = guard.selectedFiles.updated(0, S.fileIdentity(F.paths.head, Some("corrected source\n".getBytes.toVector))),
    selectedEntries = F.paths.map(path => S.Entry(path, "100644", "1" * 40)))
  val proposal = S.Proposal(S.sourceSchema, ContextReference(F.binding.identity, "2"), "selected-changes", Vector.empty,
    F.approvals.map(a => S.Coverage(a.decision, a.proposal.acceptance, F.binding.checks.map(_.identity))), consumption)
  val sourceTarget = S.Target(S.primarySchema, "same-session", "", "26", "26", F.plan, F.binding.step, 1L,
    F.primaryBase, Vector.empty, sourceRequired.repairProgress, sourceRequired.requestingResult, F.admission, Vector.empty,
    proposal, F.binding.copy(revision = 2), Some(corrected), corrected, primaryFailureOrigin = Some(sourceRequired.copy(dependencyGuard = corrected)))
  val sourceDirection = S.Direction(sourceTarget, ContextReference("combined-source-human", "1"), "participant",
    "SPECIFICATION-OWNED concrete source/artifact/one-primary plan", Some(S.combinedPrimaryChoice))
  val postGuard = corrected.copy(indexTree = "9" * 40, dirty = corrected.dirty.map(_.copy(worktree = " ")),
    selectedEntries = corrected.selectedEntries.updated(0, corrected.selectedEntries.head.copy(blob = "2" * 40)))
  val stage = S.StageFact(F.paths.head, corrected, postGuard, Vector(S.StageChange("100644", "100644", Some("1" * 40),
    Some("2" * 40), "M", F.paths.head)))
  val capture = S.Capture(S.primaryCaptureSchema, "same-session", ContextReference(F.plan.identity, F.plan.revision),
    F.binding.step, 2L, "27", F.primaryBase, sourceDirection, postGuard, Vector(F.paths.head), corrected)
  val currentCandidate = candidate.copy(sourceRevision = "27", dependencyAdmission = None, dependencyStaged = Vector.empty,
    dependency = None, dependencySourceDirection = Some(sourceDirection), dependencySourceGuard = Some(corrected),
    dependencySourceStages = Vector(stage), dependencySourceCapture = Some(capture))
  val artifactTarget = sourceRequired.copy(phase = R.PrimaryPhase.ArtifactDecision, frontierRevision = "33", generation = 2L,
    controller = _sorted(controller.mapObject(_.add("generation", 2L.asJson))), candidateRecordRevision = "33", candidate = currentCandidate,
    dependencyGuard = postGuard, sourceCapture = Some(capture))
  val artifactFact = R.PrimaryFact(artifactTarget, ContextReference("same-session", "34"), "Derived artifact operation",
    "AcceptExistingArtifactsWithUnknownEffects", Some(sourceDirection.directive))
  val primaryTarget = artifactTarget.copy(phase = R.PrimaryPhase.PrimaryDecision, frontierRevision = "34", priorDirections = Vector(artifactFact))
  val primaryFact = R.PrimaryFact(primaryTarget, ContextReference("same-session", "35"), "Derived primary operation",
    "AuthorizePrimaryPublicationOnce", Some(sourceDirection.directive))
}

/** Pure bounded contract evidence; supplied _process observations and byte
  * identities are specification data. Installed qualification is separate.
  * @since   Nov.  1, 2026
  *  version Nov.  2, 2026
  * @version Nov.  5, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhasePrerequisitePublicationRecoverySpec extends AnyWordSpec with Matchers with GivenWhenThen with NativePreparationOperationFixture {
  private val _target = LocalPublication.Target("cncf-delivery", "org.goldenport.cncf", F.dependency.workspace,
    Some(F.binding.identity), beforeChecks = true)
  private val _candidate = GoalPhaseNativeCandidate.Payload("20", F.binding.step, Vector("screen.scala"),
    Vector("screen.scala"), Some(GoalPhaseOrderedProgression.Snapshot(F.primaryBase.head, "f" * 40)),
    Vector("screen.scala"), Some("diff --git a/screen.scala b/screen.scala\n+implemented\n"),
    dependencyAdmission = Some(F.admission), dependencyStaged = F.paths, dependency = Some(F.capture))
  private val _source = LocalPublication.Source(F.binding.base.head, F.capture.candidate.tree,
    F.paths.map(LocalPublication.Change(_, "A", " ")))
  private val _input = LocalPublicationRuntime.prerequisiteInput("same-session", _candidate, _target)
  private val _request = LocalPublication.Request(_input.copy(requestIdentity = _input.requestIdentity + ":prerequisite:0"),
    _target, _source, "actor", "Actual required publication", None, Vector.empty)
  private def _process(termination: ProcessExecutionTermination): ProcessExecutionResult = {
    val capture = ProcessExecutionCapture(Vector.empty, 0L, false)
    ProcessExecutionResult(termination, capture, capture, Vector.empty, 1L, "fixture-publication")
  }
  private val _failed = LocalPublication.Result("failed", "Actual typed failure", _request,
    Some(WorkflowSbtCapture.encode(_process(ProcessExecutionTermination.Exited(7)))))
  private val _guard = S.Guard(F.binding.root, F.binding.branch, F.binding.base.head, F.binding.base.tree,
    F.capture.candidate.tree, Vector(S.Dirty("A", "M", F.paths.head, None)),
    F.paths.map(path => S.fileIdentity(path, Some("corrected\n".getBytes.toVector))), Vector.empty,
    F.paths.map(path => S.Entry(path, "100644", "1" * 40)), Vector.empty)
  private val _controller = Json.obj("stage" -> "Validation".asJson, "plan" -> F.plan.asJson,
    "base" -> F.primaryBase.asJson, "generation" -> 1L.asJson, "cursor" -> 0.asJson,
    "completed" -> Json.arr())
  private val _selected = R.Target(R.schema, "fixture-component", "participant", "same-session", "25", _controller,
    F.plan, F.primaryBase, 1L, Vector.empty, GoalPhaseRepairProgress.Snapshot(0, 0, F.plan.repairLimit), "25",
    _candidate, F.admission, F.admission, Vector.empty, _target, "{\"policy\":1}", _failed, _guard, "{\"plan\":1}")
  private val _reference = ContextReference("independent-prerequisite-answer", "1")
  private def _retained_unknown_origin: U.Target = {
    val guard = _guard.copy(dirty = Vector(S.Dirty("A", " ", F.paths.head, None)))
    U.Target(U.schema, "fixture-component", "participant", "same-session", "25",
      Printer.noSpaces.copy(sortKeys = true).print(_controller), F.plan, F.primaryBase, 1L, Vector.empty,
      _selected.repairProgress, "25", _candidate, F.admission, Vector.empty,
      LocalPublication.Result("uncertain", "Retained writer intent", _request.copy(participant = "participant")),
      _selected.publicationPolicy, _selected.processPolicy, guard, Some(guard),
      Some(ContextReference("same-session", "24")), ContextReference("actual-accepted-semantic-result", "1"), None)
  }
  private def _prepare_observation(description: String, observation: U.Observation,
    result: Option[Json] = None): Consequence[org.goldenport.record.Record] = {
    val reference = V.WorkflowArtifactReference(observation.reference.identity, observation.reference.revision)
    val records = Map("fixture:description" -> description, "fixture:observation" -> result.getOrElse(observation.asJson).noSpaces,
      "fixture:reference" -> take(reference.toRecord().toJsonStringC), "fixture:execution" -> take(
        V.WorkflowExecutionEvidence(Vector(reference), Some(observation.scope.participant), None, None).toRecord().toJsonStringC))
    val options = Map("family" -> "GoalPhase", "kind" -> "goal-phase-prerequisite-publication-observation",
      "request" -> "fixture:description", "result" -> "fixture:observation", "reference" -> "fixture:reference",
      "execution-evidence" -> "fixture:execution", "participant" -> observation.scope.participant,
      "issuer" -> "independent-host", "occurred-at" -> "2026-11-01T00:00:00Z", "collection" -> "entity:smworkflow.cli.host_evidence")
    org.simplemodeling.textus.smworkflow.preparation.NativeExternalEvidenceInput.prepareC(options,
      key => Consequence.success(records(key)))
  }

  "session-only-description" should {
    "reach native Start admission with a declared bare Session business parameter" in {
      Given("a normal preparation host with no actual native ordered Start for the selected Session")
      withPreparation() { episode =>
        import org.goldenport.protocol.{Property, Request}
        val request = Request.of(component = episode.component.name, service = "GoalPhaseService",
          operation = R.describeOperation, properties = List(Property("sessionId", "description-absent-session", None)))
        When("the normal owning Operation executes the Session-only description")
        val failure = episode.owner.executeOperationResponseInContext(request, episode.actor) match {
          case Consequence.Failure(conclusion) => conclusion.toString
          case _ => fail("A description without an actual native ordered Start must refuse")
        }
        Then("business admission refuses the absent Start without treating the Session as an authentication credential")
        failure should include ("no actual native ordered Start")
        failure should not include ("403")
        failure should not include ("requires a service authentication provider")
      }
    }

    "refuse the old input-only shape and missing or nontext Session parameters" in {
      Given("the same normal owning host and no Session or native store data")
      withPreparation() { episode =>
        import org.goldenport.protocol.{Property, Request}
        val oldinput = take(V.GoalPhasePrerequisitePublicationRecoveryDescriptionInput("description-absent-session")
          .toRecord().toJsonStringC)
        val cases = Vector(
          List(Property("input", oldinput, None)),
          List.empty[Property],
          List(Property("sessionId", 42, None)))
        When("each undeclared input-only, missing or nontext request reaches its normal owning Operation")
        val failures = cases.map { properties =>
          episode.owner.executeOperationResponseInContext(Request.of(component = episode.component.name,
            service = "GoalPhaseService", operation = R.describeOperation, properties = properties), episode.actor) match {
            case Consequence.Failure(conclusion) => conclusion.toString
            case _ => fail("Only a declared text sessionId can enter Session business admission")
          }
        }
        Then("every invalid shape refuses before native Start admission and none acts as a compatibility alias")
        failures.foreach { failure =>
          failure should not include ("no actual native ordered Start")
          failure should not include ("403")
          failure should not include ("requires a service authentication provider")
        }
      }
    }
  }

  "Primary failure continuation" should afterWord(
    "in spec:goal-phase-prerequisite-publication-recovery, example:E1,E2,E3,E4,E5, rules:R1,R2,R3, phase:M3-PRIMARY-CONTINUATION-409") {
    "E1 retain original UNKNOWN and the primary failure in a distinct exact two-target schema" in {
      Given("docs/spec/goal-phase-prerequisite-publication-recovery.md R1 E1: accepted original artifacts followed by a terminated primary failure")
      import PrimaryPublicationContinuationSpecificationFixture as P
      When("the complete SourceRequired target passes its public Record and closed schema")
      val decoded = take(R.primaryTargetC(R.primaryTargetRecord(P.sourceRequired)))
      Then("original candidate, Direction and genuine failed process remain distinct and exact")
      R.primaryTargetValid(P.sourceRequired) shouldBe true
      decoded shouldBe P.sourceRequired
      R.primaryChoices(decoded) shouldBe Vector("Defer")
      decoded.originalDirection.target.unknown shouldBe P.unknown
      decoded.failed shouldBe P.failed
      decoded.candidateRecordRevision should not be decoded.frontierRevision
      Vector(P.sourceRequired.copy(targets = Vector(P.primary, P.dependency)),
        P.sourceRequired.copy(targets = P.sourceRequired.targets :+ P.primary),
        P.sourceRequired.copy(originalDirectionRecord = ContextReference("same-session", "27")),
        P.sourceRequired.copy(failed = P.failed.copy(status = "uncertain"))).foreach(R.primaryTargetValid(_) shouldBe false)
      P.sourceRequired.asJson.mapObject(_.add("invented", Json.True)).as[R.PrimaryTarget].isLeft shouldBe true
      P.sourceRequired.asJson.mapObject(_.add("sourceCapture", Json.Null)).as[R.PrimaryTarget].isLeft shouldBe true
      P.sourceRequired.asJson.mapObject(_.add("schemaVersion", R.schema.asJson)).as[R.PrimaryTarget].isLeft shouldBe true
    }

    "E2 bind same-root corrected source bytes while preserving the original accepted guard" in {
      Given("docs/spec/goal-phase-prerequisite-publication-recovery.md R1 E2: an admitted dependency path corrected after the original primary failure")
      import PrimaryPublicationContinuationSpecificationFixture as P
      val fresh = P.sourceRequired.copy(dependencyGuard = P.corrected)
      When("the fresh primary origin and its selected v7 source proposal are interpreted")
      val decoded = take(S.targetC(S.targetRecord(P.sourceTarget)))
      Then("current correction is eligible without rewriting old artifact authority or the primary delta")
      R.primaryTargetValid(fresh) shouldBe true
      S.targetValid(decoded) shouldBe true
      fresh.originalDirection.target.guard shouldBe P.guard
      fresh.dependencyGuard should not be P.guard
      decoded.primaryFailureOrigin shouldBe Some(fresh)
      S.targetValid(decoded.copy(primaryFailureOrigin = Some(P.sourceRequired))) shouldBe false
      R.primaryTargetValid(fresh.copy(candidate = fresh.candidate.copy(diff = Some("crossed primary work")))) shouldBe false
      R.primaryProgramMatches(P.processPolicy, P.primary, P.consumption) shouldBe true
      R.primaryProgramMatches(P.processPolicy.replace(P.consumption.sourceRoot, P.consumption.sourceRoot + "-other"), P.primary, P.consumption) shouldBe false
      R.primaryProgramMatches("{\"processPlan\":{\"sbtPrograms\":[]}}", P.primary, P.consumption) shouldBe false
    }

    "E3 retain separate ordered facts under one combined authority and expose uncovered source-only decisions" in {
      Given("docs/spec/goal-phase-prerequisite-publication-recovery.md R2 E3: captured combined source authority and its distinct artifact and primary operation facts")
      import PrimaryPublicationContinuationSpecificationFixture as P
      When("each fact is encoded under the new mechanical version")
      val artifact = R.primaryCodec.decode(R.primaryCodec.encode(P.artifactFact))
      val primary = R.primaryCodec.decode(R.primaryCodec.encode(P.primaryFact))
      Then("both operation identities differ while inherited authority remains the same actual source directive")
      artifact shouldBe Right(P.artifactFact)
      primary shouldBe Right(P.primaryFact)
      P.artifactFact.reference should not be P.primaryFact.reference
      P.artifactFact.inheritedFrom shouldBe P.primaryFact.inheritedFrom
      R.primaryChoices(P.artifactTarget) shouldBe Vector("AcceptExistingArtifactsWithUnknownEffects", "Defer")
      R.primaryChoices(P.primaryTarget) shouldBe Vector("AuthorizePrimaryPublicationOnce", "Defer")
      R.primaryFactValid(P.primaryFact.copy(inheritedFrom = Some(ContextReference("unrelated-human", "1")))) shouldBe false
      val sourceonly = P.sourceDirection.copy(choice = Some("AdoptDependencySourceRevisionWithUnknownEffects"))
      val capture = P.capture.copy(direction = sourceonly)
      val candidate = P.currentCandidate.copy(dependencySourceDirection = Some(sourceonly), dependencySourceCapture = Some(capture))
      val uncovered = P.artifactTarget.copy(candidate = candidate, sourceCapture = Some(capture))
      R.primaryTargetValid(uncovered) shouldBe true
      R.primaryFactValid(P.artifactFact.copy(target = uncovered)) shouldBe false
      R.primaryFactValid(P.artifactFact.copy(target = uncovered, inheritedFrom = None,
        reference = ContextReference("fresh-artifact-human", "1"))) shouldBe true
      val changedprogram = P.artifactTarget.copy(processPolicy = P.processPolicy.replace("EXPLICIT_TEST_DEPENDENCY_ROOT", "DIFFERENT_EXPLICIT_ROOT"))
      R.primaryTargetValid(changedprogram) shouldBe true
      R.primaryChoices(changedprogram) shouldBe Vector("AcceptExistingArtifactsWithUnknownEffects", "Defer")
      R.primaryFactValid(P.artifactFact.copy(target = changedprogram)) shouldBe false
      R.primaryFactValid(P.artifactFact.copy(target = changedprogram, inheritedFrom = None)) shouldBe true
      val pending = P.primaryTarget.copy(frontierRevision = "35", priorDirections = Vector(P.artifactFact, P.primaryFact))
      R.primaryChoices(pending) shouldBe Vector("Defer")
      val unknown = pending.copy(phase = R.PrimaryPhase.PrimaryUncertain, failed = P.failed.copy(status = "uncertain", process = None))
      R.primaryTargetValid(unknown) shouldBe true
      R.primaryChoices(unknown) shouldBe Vector("Defer")
      R.primaryTargetValid(unknown.copy(failed = P.failed.copy(status = "published"))) shouldBe false
    }

    "E3 require a fresh individual decision for post-grant policy drift while retaining an unchanged grant" in {
      Given("CB-M3-01: both operation facts are retained before any primary successor reservation")
      import PrimaryPublicationContinuationSpecificationFixture as P
      val pending = P.primaryTarget.copy(frontierRevision = "35", priorDirections = Vector(P.artifactFact, P.primaryFact))
      val processdrift = pending.copy(processPolicy = P.processPolicy.replace("EXPLICIT_TEST_DEPENDENCY_ROOT", "CHANGED_EXPLICIT_ROOT"))
      val publicationdrift = pending.copy(publicationPolicy = "{\"publication\":2}")
      When("a live target binds a materially changed process or publication policy at the same admitted source")
      val fresh = R.PrimaryFact(processdrift, ContextReference("fresh-program-human", "1"),
        "SPECIFICATION-OWNED changed current primary program", "AuthorizePrimaryPublicationOnce")
      val renewed = processdrift.copy(frontierRevision = "36", priorDirections = processdrift.priorDirections :+ fresh)
      Then("historical authority remains intact, changed operations require an individual answer, and applicable grants cannot duplicate")
      Vector(processdrift, publicationdrift).foreach { target =>
        R.primaryTargetValid(target) shouldBe true
        take(R.primaryTargetC(R.primaryTargetRecord(target))) shouldBe target
        R.primaryChoices(target) shouldBe Vector("AuthorizePrimaryPublicationOnce", "Defer")
        target.priorDirections.last.target.processPolicy shouldBe P.processPolicy
        target.priorDirections.last.target.publicationPolicy shouldBe P.sourceRequired.publicationPolicy
      }
      R.primaryFactValid(fresh) shouldBe true
      R.primaryFactValid(fresh.copy(inheritedFrom = Some(P.sourceDirection.directive))) shouldBe false
      R.primaryChoices(pending) shouldBe Vector("Defer")
      R.primaryTargetValid(renewed) shouldBe true
      R.primaryChoices(renewed) shouldBe Vector("Defer")
      val newfailure = processdrift.copy(failed = P.failed.copy(reason = "A different actual terminal failure"))
      R.primaryChoices(newfailure) shouldBe Vector("AuthorizePrimaryPublicationOnce", "Defer")
      val newfact = fresh.copy(target = newfailure, reference = ContextReference("fresh-failure-human", "1"))
      R.primaryFactValid(newfact) shouldBe true
      R.primaryFactValid(newfact.copy(inheritedFrom = Some(P.sourceDirection.directive))) shouldBe false
      R.primaryChoices(processdrift.copy(phase = R.PrimaryPhase.PrimaryUncertain)) shouldBe Vector("Defer")
    }

    "E3 traverse historical catalog-output drift only after canonical primary proof and require a fresh live-policy decision" in {
      Given("CB-M3-01b: immutable original UNKNOWN history, exact storage and two targets, and a changed valid catalog-output policy")
      import PrimaryPublicationContinuationSpecificationFixture as P
      val collection = org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(
        org.simplemodeling.model.datatype.EntityCollectionId("smworkflow", "spec", "publication"))
      val historicalpolicy = LocalPublicationRuntime.Policy("fixture-publication-store", collection, Vector(P.dependency, P.primary))
      val output = LocalPublicationRuntime.CatalogOutput(P.primary.identity,
        "src/main/catalog/car/org/example/primary.yaml", "publication-catalog")
      val currentpolicy = historicalpolicy.copy(catalogOutputs = Vector(output))
      val historical = R.publicationPolicy(historicalpolicy)
      val current = R.publicationPolicy(currentpolicy)
      val original = P.unknownTarget.copy(publicationPolicy = historical)
      val changedprocess = P.processPolicy.replace("EXPLICIT_TEST_DEPENDENCY_ROOT", "CHANGED_EXPLICIT_ROOT")
      var calls = Vector.empty[String]
      def _traverse_(target: U.Target, policy: LocalPublicationRuntime.Policy, process: String,
        primary: Boolean, proof: Consequence[Unit] = Consequence.unit,
        unknown: Consequence[Unit] = Consequence.unit): Consequence[Unit] = U.historicalPoliciesC(target, policy, process, primary,
          { calls = calls :+ "canonical-primary"; proof }, { calls = calls :+ "retained-unknown"; unknown })
      When("the production historical policy gate traverses unchanged history and each kind of primary-only drift")
      take(_traverse_(original, historicalpolicy, P.processPolicy, false))
      val unchangedcalls = calls
      val driftcalls = Vector((historicalpolicy, changedprocess), (currentpolicy, P.processPolicy), (currentpolicy, changedprocess)).map {
        (policy, process) =>
          calls = Vector.empty
          val accepted = _traverse_(original, policy, process, true)
          (accepted.isSuccess, calls)
      }
      Then("unchanged legacy traversal needs only the old UNKNOWN, while process or publication drift proves the canonical primary once before UNKNOWN")
      unchangedcalls shouldBe Vector("retained-unknown")
      driftcalls.foreach(_ shouldBe (true, Vector("canonical-primary", "retained-unknown")))
      U.historicalPublicationPolicyCompatible(historical, historicalpolicy, false) shouldBe true
      U.historicalPublicationPolicyCompatible(historical, currentpolicy, true) shouldBe true
      original.publicationPolicy shouldBe historical
      When("the required canonical primary proof or retained UNKNOWN proof refuses")
      calls = Vector.empty
      val failedprimary = _traverse_(original, currentpolicy, P.processPolicy, true,
        proof = Consequence.stateConflict("SPECIFICATION canonical primary failure is absent"))
      val primarycalls = calls
      calls = Vector.empty
      val failedunknown = _traverse_(original, currentpolicy, P.processPolicy, true,
        unknown = Consequence.stateConflict("SPECIFICATION retained UNKNOWN differs"))
      val unknowncalls = calls
      Then("neither refusal reaches a current decision and primary failure short-circuits UNKNOWN collection")
      failedprimary.isSuccess shouldBe false
      primarycalls shouldBe Vector("canonical-primary")
      failedunknown.isSuccess shouldBe false
      unknowncalls shouldBe Vector("canonical-primary", "retained-unknown")
      When("ordinary UNKNOWN history, changed storage or complete targets, or malformed policies attempt the same traversal")
      val othercollection = org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(
        org.simplemodeling.model.datatype.EntityCollectionId("smworkflow", "spec", "other_publication"))
      val crossed = Vector(currentpolicy.copy(storeName = "another-store"), currentpolicy.copy(collection = othercollection),
        currentpolicy.copy(targets = currentpolicy.targets.reverse),
        currentpolicy.copy(targets = currentpolicy.targets.updated(1, P.primary.copy(beforeSync = true))),
        currentpolicy.copy(catalogOutputs = Vector(output.copy(path = "outside-catalog.yaml"))))
      val json = io.circe.parser.parse(historical).toOption.get
      def _text_(value: Json): String = Printer.noSpaces.copy(sortKeys = true).print(value)
      val malformed = Vector("{", "null", _text_(json.mapObject(_.add("unexpected", Json.True))),
        _text_(json.mapObject(_.remove("collection"))), _text_(json.mapObject(_.add("catalogOutputs", Json.Null))),
        _text_(json.mapObject(_.add("catalogOutputs", Json.arr(output.asJson.mapObject(_.add("unexpected", Json.True)))))),
        _text_(json.mapObject(_.add("catalogOutputs", Json.arr(output.copy(targetIdentity = "undeclared").asJson)))))
      calls = Vector.empty
      val nonprimary = Vector(_traverse_(original, currentpolicy, P.processPolicy, false),
        _traverse_(original, historicalpolicy, changedprocess, false))
      val crossedresults = crossed.map(policy => _traverse_(original, policy, P.processPolicy, true))
      val malformedresults = malformed.map(text => _traverse_(original.copy(publicationPolicy = text), currentpolicy, P.processPolicy, true))
      Then("all refuse before invoking either proof, without relaxing legacy history or migrating retained results")
      (nonprimary ++ crossedresults ++ malformedresults).foreach(_.isSuccess shouldBe false)
      calls shouldBe empty
      When("successful historical proof reaches the current full-policy primary decision with the retained old facts")
      take(_traverse_(original, currentpolicy, P.processPolicy, true))
      val originaldirection = P.originalDirection.copy(target = original)
      val sourceorigin = P.sourceRequired.copy(originalDirection = originaldirection, publicationPolicy = historical,
        dependencyGuard = P.corrected)
      val sourcedirection = P.sourceDirection.copy(target = P.sourceTarget.copy(primaryFailureOrigin = Some(sourceorigin)))
      val capture = P.capture.copy(direction = sourcedirection)
      val candidate = P.currentCandidate.copy(dependencySourceDirection = Some(sourcedirection), dependencySourceCapture = Some(capture))
      val artifacttarget = P.artifactTarget.copy(originalDirection = originaldirection, publicationPolicy = historical,
        candidate = candidate, sourceCapture = Some(capture))
      val artifactfact = P.artifactFact.copy(target = artifacttarget)
      val primarytarget = P.primaryTarget.copy(originalDirection = originaldirection, publicationPolicy = historical,
        candidate = candidate, sourceCapture = Some(capture), priorDirections = Vector(artifactfact))
      val primaryfact = P.primaryFact.copy(target = primarytarget)
      val live = primarytarget.copy(frontierRevision = "35", publicationPolicy = current, priorDirections = Vector(artifactfact, primaryfact))
      val fresh = R.PrimaryFact(live, ContextReference("fresh-catalog-policy-human", "1"),
        "SPECIFICATION-OWNED current catalog-output policy", "AuthorizePrimaryPublicationOnce")
      Then("only fresh individual authority covers current policy and all historical publication policy bytes remain unchanged")
      R.primaryTargetValid(live) shouldBe true
      R.primaryChoices(live) shouldBe Vector("AuthorizePrimaryPublicationOnce", "Defer")
      R.primaryFactValid(fresh) shouldBe true
      R.primaryFactValid(fresh.copy(inheritedFrom = Some(sourcedirection.directive))) shouldBe false
      live.originalDirection.target.publicationPolicy shouldBe historical
      live.priorDirections.foreach(_.target.publicationPolicy shouldBe historical)
      live.publicationPolicy shouldBe current
    }

    "E4 passively prepare only the exact v2 phase and complete choices under the existing purpose" in {
      Given("docs/spec/goal-phase-prerequisite-publication-recovery.md R2 E4: an independently supplied source-bound artifact answer")
      import PrimaryPublicationContinuationSpecificationFixture as P
      val target = P.artifactTarget
      val reference = V.WorkflowArtifactReference("fresh-artifact-human", "1")
      val input = V.GoalPhasePrerequisitePublicationRecoveryInput(target.sessionId, R.primaryTargetRecord(target),
        "AcceptExistingArtifactsWithUnknownEffects", "SPECIFICATION-OWNED exact artifact answer", reference)
      val description = V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult(target.sessionId, "direction-required",
        R.primaryMessage(target), target.componentInstance, R.primaryTargetRecord(target), R.primaryChoices(target), R.recoveryOperation)
      def _prepare_(value: V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult) =
        org.simplemodeling.textus.smworkflow.preparation.NativePrerequisitePublicationRecoveryPreparation.prepareC(value, input, reference,
          V.WorkflowExecutionEvidence(Vector(reference), Some(target.participant), None, None), target.participant,
          "independent-fixture-provider", java.time.Instant.parse("2026-11-01T00:00:00Z"))
      When("passive preparation binds the described phase, participant and whole ordered choice set")
      val prepared = _prepare_(description)
      Then("the existing evidence purpose is retained and incomplete or crossed descriptions refuse")
      prepared.isSuccess shouldBe true
      _prepare_(description.copy(choices = Vector("AcceptExistingArtifactsWithUnknownEffects"))).isSuccess shouldBe false
      _prepare_(description.copy(componentInstance = "foreign-component")).isSuccess shouldBe false
      _prepare_(description.copy(outcome = "observation-required")).isSuccess shouldBe false
      val binding = take(R.primaryBindingC(target.componentInstance, target, input.choice, input.rationale,
        target.participant, ContextReference(reference.identity, reference.revision)))
      R.selectBinding(binding) shouldBe Right(binding)
      R.selectBinding(binding.mapObject(_.add("inventedAuthority", Json.True))).isLeft shouldBe true
    }

    "E5 acquire large complete input through the three existing executing public handlers" in {
      withPreparation(cap = 1048576) { episode =>
        Given("docs/spec/goal-phase-prerequisite-publication-recovery.md R3 E5: complete inputs above 270 KiB and an absent native Session")
        import PrimaryPublicationContinuationSpecificationFixture as P
        import org.goldenport.protocol.{Property, Request}
        val huge = "complete target policy data " * 15000
        val target = P.sourceRequired.copy(processPolicy = Printer.noSpaces.copy(sortKeys = true).print(Json.obj("complete" -> huge.asJson)))
        R.primaryTargetValid(target) shouldBe true
        val largeproposal = P.proposal.copy(binding = ContextReference("explicit-large-binding-" + ("x" * 280000), "2"))
        val inputs = Vector(
          R.recoveryOperation -> V.GoalPhasePrerequisitePublicationRecoveryInput(P.sourceRequired.sessionId,
            R.primaryTargetRecord(target), "Defer", "SPECIFICATION-OWNED transport only", V.WorkflowArtifactReference("fixture", "1")).toRecord(),
          S.describeOperation -> V.GoalPhaseDependencySourceRevisionDescriptionInput(P.sourceRequired.sessionId,
            GoalPhaseNativeMetadata.record(largeproposal.asJson)).toRecord(),
          S.adoptionOperation -> V.GoalPhaseDependencySourceRevisionAdoptionInput(P.sourceRequired.sessionId,
            S.targetRecord(P.sourceTarget), S.combinedPrimaryChoice, huge, V.WorkflowArtifactReference("fixture", "1")).toRecord())
        def _failure_(operation: String, properties: List[Property]): String =
          episode.owner.executeOperationResponseInContext(Request.of(component = episode.component.name, service = "GoalPhaseService",
            operation = operation, properties = properties), episode.actor) match {
            case Consequence.Failure(conclusion) => conclusion.toString
            case _ => fail("Transport specification owns no native Session or recovery authority")
          }
        When("inline and authorized Resource inputs enter their normal owning call")
        inputs.zipWithIndex.foreach { case ((operation, record), index) =>
          val text = take(record.toJsonStringC)
          val uri = episode.save("primary-complete-" + index, text)
          val inlinefailure = _failure_(operation, List(Property("input", text, None)))
          val resource = _failure_(operation, List(Property("inputResource", uri, None)))
          Then("the same complete business record reaches native Start admission without an argv-size cap or fallback")
          inlinefailure should include ("no actual native ordered Start")
          resource should include ("no actual native ordered Start")
          if (operation != S.describeOperation) {
            val json = io.circe.parser.parse(text).toOption.get
            val targetjson = json.hcursor.downField("target").focus.get
            val nullfield = if (operation == R.recoveryOperation) "sourceCapture" else "waitOrigin"
            val nullinput = json.mapObject(_.add("target", targetjson.mapObject(_.add(nullfield, Json.Null)))).noSpaces
            val nullinline = _failure_(operation, List(Property("input", nullinput, None)))
            val nullresource = _failure_(operation, List(Property("inputResource", episode.save("primary-target-null-" + index, nullinput), None)))
            Then("an explicit null in an optional nested target field refuses before native Start admission in either transport")
            nullinline should include ("explicit JSON null")
            nullresource should include ("explicit JSON null")
            nullinline should not include ("no actual native ordered Start")
            nullresource should not include ("no actual native ordered Start")
          }
          val invalid = Vector("{", text.dropRight(1), "{\"sessionId\":\"same-session\",\"sessionId\":\"other\"}",
            "{\"sessionId\":null}", "{\"sessionId\":\"same-session\",\"unexpected\":true}")
          invalid.zipWithIndex.foreach { (raw, number) =>
            _failure_(operation, List(Property("inputResource", episode.save("primary-invalid-" + index + "-" + number, raw), None))) should not include ("no actual native ordered Start")
          }
          _failure_(operation, List(Property("input", text, None), Property("inputResource", uri, None))) should not include ("no actual native ordered Start")
          _failure_(operation, List(Property("inputResource", episode.root.getParent.resolve("denied-primary.json").toUri.toASCIIString, None))) should not include ("no actual native ordered Start")
          text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 270000
        }
        R.validateBusinessTextC("{\"a\":{\"x\":1,\"x\":2}}").isSuccess shouldBe false
        R.validateBusinessTextC("{\"a\":1,\"\\u0061\":2}").isSuccess shouldBe false
        R.validateBusinessTextC("{\"target\":{\"nested\":[{},[],null]}}").isSuccess shouldBe false
        R.validateBusinessTextC("{\"emptyObject\":{},\"emptyArray\":[],\"text\":\"null\"}").isSuccess shouldBe true
      }
    }
  }

  "Prerequisite publication recovery target" should {
    "retain a complete canonical resolved process policy while requiring explicit Human acceptance of unknown effects" in {
      Given("RP-UNKNOWN323-01: pure specification data for a blocked UNKNOWN target and a complete large resolved policy")
      val aliases = (1 to 128).toVector.map(index => f"fixture-policy-status-$index%03d")
      val status = Json.obj("target" -> F.dependency.workspace.asJson, "operation" -> GitOperationKind.Status.wireName.asJson,
        "identity" -> "fixture-status".asJson, "capability" -> "fixture-status".asJson,
        "gitExecutable" -> "/usr/bin/git".asJson, "localBranch" -> F.binding.branch.asJson,
        "remote" -> "origin".asJson, "remoteBranch" -> "main".asJson,
        "endpoint" -> "https://example.invalid/fixture-policy.git".asJson, "commitIdentity" -> Json.Null,
        "selectionId" -> Json.Null, "selectedPath" -> Json.Null, "commitMessage" -> Json.Null,
        "limits" -> TypedGitHostPlanFixture.limits, "environment" -> Json.obj("PATH" -> "/usr/bin:/bin".asJson))
      val owner = org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name
      val process = Json.obj("schema" -> "cncf.runtime-process-execution.v3".asJson, "programs" -> Json.arr(),
        "sbtPrograms" -> Json.arr(), "gitPrograms" -> aliases.map(alias => status.mapObject(
          _.add("target", alias.asJson).add("identity", alias.asJson).add("capability", alias.asJson))).asJson,
        "grants" -> Json.arr(Json.obj("componentId" -> owner.asJson, "capabilities" -> aliases.asJson)),
        "workspaces" -> aliases.map(alias => Json.obj("identity" -> alias.asJson, "root" -> F.binding.root.asJson)).asJson,
        "workspaceGrants" -> aliases.map(alias => Json.obj("componentId" -> owner.asJson,
          "workspaceId" -> alias.asJson, "access" -> "read-only".asJson)).asJson)
      val checks = GoalPhaseNativeValidation.Policy("fixture-policy-checks", 1,
        Vector(GoalPhaseNativeValidation.Check("fixture-test", 1)))
      val resolved = Json.obj("processPlan" -> process, "maximumDiffBytes" -> GitDiffParameters.defaultBytes.asJson,
        "validation" -> checks.asJson)
      val policy = Printer.noSpaces.copy(sortKeys = true).print(resolved)
      val target = _retained_unknown_origin.copy(processPolicy = policy)
      val reference = V.WorkflowArtifactReference("fixture-large-policy-human", "1")
      val input = V.GoalPhasePrerequisitePublicationRecoveryInput(target.sessionId, U.targetRecord(target), "AcceptExistingArtifactsWithUnknownEffects",
        "SPECIFICATION-OWNED independent answer", reference)
      val description = V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult(target.sessionId, "direction-required",
        U.messageFor(target), target.componentInstance, U.targetRecord(target), U.choicesFor(target), R.recoveryOperation)
      When("the complete policy passes strict codecs, generated public Records and passive evidence preparation")
      val converted = take(summon[ValueReader[V.GoalPhasePrerequisitePublicationRecoveryInput]].readC(input.toRecord()))
      val described = take(summon[ValueReader[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]].readC(description.toRecord()))
      val lifecycle = U.WriterLifecycle("cncf-writer-exited", ContextReference("fixture-policy-writer", "1"),
        ContextReference("fixture-policy-terminal", "1"), "publication-writer-lifecycle-complete",
        Printer.noSpaces.copy(sortKeys = true).print(WorkflowSbtCapture.encode(_process(ProcessExecutionTermination.Exited(0)))))
      val observation = U.Observation(U.observationSchema, ContextReference("fixture-policy-observation", "1"),
        U.scopeOf(target), lifecycle, None)
      Then("all policy bytes and the same frontier survive, while a legacy qualified Direction still requires qualification")
      policy.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be >= 77521
      checks.validateC.isSuccess shouldBe true
      U.targetValid(target) shouldBe true
      target.asJson.as[U.Target] shouldBe Right(target)
      take(U.targetC(converted.target)) shouldBe target
      take(U.targetC(described.target)) shouldBe target
      take(U.targetC(converted.target)).processPolicy shouldBe policy
      target.frontierRevision shouldBe _retained_unknown_origin.frontierRevision
      target.observation shouldBe None
      U.qualified(target) shouldBe false
      U.directionValid(U.Direction(target, _reference, input.rationale)) shouldBe false
      _prepare_observation(take(description.toRecord().toJsonStringC), observation).isSuccess shouldBe true
      U.qualified(target.copy(observation = Some(observation))) shouldBe false
      org.simplemodeling.textus.smworkflow.preparation.NativePrerequisitePublicationRecoveryPreparation.prepareC(
        description, converted, reference, V.WorkflowExecutionEvidence(Vector(reference), Some(target.participant), None, None),
        target.participant, "independent-host", java.time.Instant.parse("2026-11-01T00:00:00Z")).isSuccess shouldBe true
      Vector(null, "", "{}", "[]", "null", "1", "\"policy\"", "{", "{\"z\":1,\"a\":2}", " {\"a\":1}").foreach { invalid =>
        U.targetValid(target.copy(processPolicy = invalid)) shouldBe false
        target.copy(processPolicy = invalid).asJson.as[U.Target].isLeft shouldBe true
      }
      val binding = take(U.bindingC(target.componentInstance, target, input.choice, input.rationale, target.participant, _reference))
      U.selectBinding(binding) shouldBe Right(binding)
      val altered = target.copy(processPolicy = Printer.noSpaces.copy(sortKeys = true).print(
        resolved.mapObject(_.add("maximumDiffBytes", (GitDiffParameters.defaultBytes + 1L).asJson))))
      U.targetValid(altered) shouldBe true
      take(U.bindingC(altered.componentInstance, altered, input.choice, input.rationale, altered.participant, _reference)) should not be binding
      val largerationale = "x" * 65537
      val largebinding = take(U.bindingC(target.componentInstance, target, input.choice, largerationale, target.participant, _reference))
      U.selectBinding(largebinding) shouldBe Right(largebinding)
      largebinding.hcursor.downField("prerequisitePublicationUnknownRecovery").downField("answer").get[String]("rationale") shouldBe Right(largerationale)
      U.directionValid(U.Direction(target, _reference, largerationale)) shouldBe false
    }

    "construct the Wait source target from retained origin before observing a declared newer committed base" in {
      Given("CB-UNKNOWN323-02-BASE: a retained native UNKNOWN origin and an explicitly declared newer committed source")
      val origin = _retained_unknown_origin
      val selected = F.binding.copy(revision = 2, base = GoalPhaseOrderedProgression.Snapshot("1" * 40, "2" * 40))
      val fresh = origin.guard.copy(head = selected.base.head, committedTree = selected.base.tree,
        indexTree = selected.base.tree, dirty = Vector.empty, selectedEntries = Vector.empty)
      val consumption = S.Consumption(F.requester.workspace, selected.root, Vector.empty,
        selected.paths.map(S.ExpectedMode(_, "100644")),
        Vector(S.CheckProgram("primary-test", "primary-validation", "test", "fixture-primary-check")))
      val proposal = S.Proposal(S.sourceSchema, ContextReference(selected.identity, "2"), "committed", Vector.empty,
        F.approvals.map(a => S.Coverage(a.decision, a.proposal.acceptance, selected.checks.map(_.identity))), consumption)
      val change = S.StageChange("100644", "100644", Some("3" * 40), Some("4" * 40), "M", selected.paths.head)
      val succession = S.CommittedBaseSuccession(S.committedBaseSuccessionSchema, origin.requestingResult,
        F.binding.base, selected.base, Vector(change), Vector(selected.paths.head), Vector.empty, Vector.empty, Vector.empty,
        "committed", selected.paths.map(path => S.CommittedEntry(path, Some(S.CommittedObject("100644", "4" * 40)))))
      var observations = Vector.empty[(D.Binding, Vector[String])]
      var inventories = Vector.empty[(D.Binding, Vector[String], ContextReference, GoalPhaseOrderedProgression.Snapshot)]
      def _observe_(binding: D.Binding, exclusions: Vector[String]): Consequence[S.Guard] = {
        observations :+= binding -> exclusions
        if (binding == selected && exclusions.isEmpty) Consequence.success(fresh)
        else Consequence.stateConflict("Fixture actual HEAD/tree differs from the old binding base")
      }
      def _inventory_(binding: D.Binding, exclusions: Vector[String], parent: ContextReference,
        previous: GoalPhaseOrderedProgression.Snapshot): Consequence[S.CommittedBaseSuccession] = {
        inventories :+= ((binding, exclusions, parent, previous))
        if (binding == selected && exclusions.isEmpty && parent == origin.requestingResult && previous == F.binding.base)
          Consequence.success(succession) else Consequence.stateConflict("Fixture succession inventory crosses selected native source")
      }
      _observe_(F.binding, Vector.empty).isSuccess shouldBe false
      observations = Vector.empty
      When("the same constructor used by production resolves its retained origin and observes the selected new declaration")
      val result = take(U._source_selection_target_c(origin, proposal, selected, _observe_, _inventory_))
      Then("only the new binding reaches physical observation and the complete committed inventory preserves original provenance")
      observations shouldBe Vector(selected -> Vector.empty)
      inventories shouldBe Vector((selected, Vector.empty, origin.requestingResult, F.binding.base))
      result.predecessorGuard shouldBe Some(origin.guard)
      result.preStageGuard shouldBe fresh
      result.waitOrigin shouldBe Some(origin)
      result.committedBaseSuccession shouldBe Some(succession)
      result.completed shouldBe origin.completed
      result.repairProgress shouldBe origin.repairProgress
      result.requestingResult shouldBe origin.requestingResult
      result.proposal.coverage shouldBe proposal.coverage
      Given("the actual complete v6 source-description DTO and an independent lifecycle observation of its retained origin")
      val description = V.GoalPhaseDependencySourceRevisionDescriptionResult(result.sessionId, "direction-required", S.message,
        origin.componentInstance, S.targetRecord(result), S.choicesFor(result), S.adoptionOperation)
      val descriptiontext = take(description.toRecord().toJsonStringC)
      val descriptionjson = io.circe.parser.parse(descriptiontext).toOption.get
      val lifecycle = U.WriterLifecycle("cncf-writer-exited", ContextReference("owned-writer-invocation", "1"),
        ContextReference("owned-writer-source", "1"), "publication-writer-lifecycle-complete",
        Printer.noSpaces.copy(sortKeys = true).print(WorkflowSbtCapture.encode(_process(ProcessExecutionTermination.Exited(0)))))
      val observation = U.Observation(U.observationSchema, ContextReference("independent-retained-writer-observation", "1"),
        U.scopeOf(origin), lifecycle, None)
      When("normal passive preparation selects that source description by its closed v6 target schema")
      val prepared = _prepare_observation(descriptiontext, observation)
      Then("the existing evidence kind accepts the exact retained origin without synthesizing a prerequisite description")
      prepared.isSuccess shouldBe true
      When("description provenance, target schema, finite fields or the observation's origin are changed")
      val changeddescriptions = Vector(descriptionjson.mapObject(_.add("unexpected", Json.True)),
        descriptionjson.mapObject(_.add("target", Json.Null)),
        descriptionjson.mapObject(_.add("target", result.asJson.mapObject(_.add("waitOrigin", Json.Null)))),
        descriptionjson.mapObject(_.add("target", result.asJson.mapObject(_.add("schemaVersion", S.sourceSchema.asJson)))),
        descriptionjson.mapObject(_.add("target", result.asJson.mapObject(_.add("unexpected", Json.True)))))
      val crosseddescriptions = Vector(description.copy(sessionId = "foreign-session"),
        description.copy(componentInstance = "foreign-component"), description.copy(adoptionOperation = R.recoveryOperation))
      val crossedobservation = observation.copy(scope = observation.scope.copy(frontierRevision = "26"))
      Then("all malformed, mixed or crossed inputs refuse instead of falling back to another description decoder")
      changeddescriptions.foreach(value => _prepare_observation(value.noSpaces, observation).isSuccess shouldBe false)
      crosseddescriptions.foreach(value => _prepare_observation(take(value.toRecord().toJsonStringC), observation).isSuccess shouldBe false)
      _prepare_observation(descriptiontext, observation, Some(crossedobservation.asJson)).isSuccess shouldBe false
      _prepare_observation(descriptiontext, observation, Some(observation.asJson.mapObject(_.add("qualification", Json.Null)))).isSuccess shouldBe false
      When("the selected base or its complete finite inventory is absent or incomplete")
      val missing = U._source_selection_target_c(origin, proposal, selected, _observe_,
        (_, _, _, _) => Consequence.stateConflict("Fixture required inventory absent"))
      val incomplete = U._source_selection_target_c(origin, proposal, selected, _observe_,
        (_, _, _, _) => Consequence.success(succession.copy(committedEntries = succession.committedEntries.dropRight(1))))
      val absentorigin = U._source_selection_target_c(origin.copy(retainedGuard = None), proposal, selected, _observe_, _inventory_)
      Then("construction refuses without substituting current bytes for the old guard")
      missing.isSuccess shouldBe false
      incomplete.isSuccess shouldBe false
      absentorigin.isSuccess shouldBe false
    }

    "construct an approved added-path selection without applying the old binding path restriction first" in {
      Given("CB-UNKNOWN323-02-SCOPE: an authentic retained extra scope approval and a newly present selected source file")
      val extra = "src/main/scala/QualifiedSuccessor.scala"
      val approval = F.approvals.head.copy(proposal = F.approvals.head.proposal.copy(identity = "retained-extra-scope",
        paths = Vector(extra)), decision = ContextReference("retained-extra-scope-answer", "1"))
      val origin = _retained_unknown_origin.copy(scopeApprovals = Vector(approval))
      val selected = F.binding.copy(revision = 2, paths = F.paths :+ extra)
      val fresh = origin.guard.copy(dirty = origin.guard.dirty :+ S.Dirty("?", "?", extra, None),
        selectedFiles = origin.guard.selectedFiles :+ S.fileIdentity(extra, Some("class QualifiedSuccessor\n".getBytes.toVector)))
      val consumption = S.Consumption(F.requester.workspace, selected.root, Vector.empty,
        selected.paths.map(S.ExpectedMode(_, "100644")),
        Vector(S.CheckProgram("primary-test", "primary-validation", "test", "fixture-primary-check")))
      val coverage = (F.approvals :+ approval).map(a => S.Coverage(a.decision, a.proposal.acceptance, selected.checks.map(_.identity)))
      val proposal = S.Proposal(S.sourceSchema, ContextReference(selected.identity, "2"), "selected-changes", Vector.empty, coverage, consumption)
      var observed = Vector.empty[D.Binding]
      var inventories = 0
      def _observe_(binding: D.Binding, exclusions: Vector[String]): Consequence[S.Guard] = {
        observed :+= binding
        if (binding == selected && exclusions.isEmpty && fresh.dirty.forall(d => binding.paths.contains(d.path))) Consequence.success(fresh)
        else Consequence.stateConflict("Fixture actual dirty source contains a path outside the old selection")
      }
      def _inventory_(binding: D.Binding, exclusions: Vector[String], parent: ContextReference,
        previous: GoalPhaseOrderedProgression.Snapshot): Consequence[S.CommittedBaseSuccession] = {
        inventories += 1
        Consequence.stateConflict("An unchanged base must not request committed inventory")
      }
      _observe_(F.binding, Vector.empty).isSuccess shouldBe false
      observed = Vector.empty
      When("production's source-target constructor observes that complete selected declaration")
      val result = take(U._source_selection_target_c(origin, proposal, selected, _observe_, _inventory_))
      Then("the retained old guard remains exact while the fresh outer selection includes only the retained-approved addition")
      observed shouldBe Vector(selected)
      inventories shouldBe 0
      result.predecessorGuard shouldBe Some(origin.guard)
      result.preStageGuard shouldBe fresh
      result.waitOrigin.get.scopeApprovals shouldBe Vector(approval)
      result.proposal.coverage shouldBe coverage
      result.binding.paths shouldBe F.paths :+ extra
      Given("an optional exact qualification of the old retained origin with its original path inventory")
      val oldconsumption = consumption.copy(expectedModes = F.paths.map(S.ExpectedMode(_, "100644")))
      val oldobservation = U.Observation(U.observationSchema, ContextReference("independent-old-source-observation", "1"),
        U.scopeOf(origin), U.WriterLifecycle("cncf-writer-exited", ContextReference("owned-old-writer", "1"),
          ContextReference("owned-old-writer-result", "1"), "publication-writer-lifecycle-complete",
          Printer.noSpaces.copy(sortKeys = true).print(WorkflowSbtCapture.encode(_process(ProcessExecutionTermination.Exited(0))))),
        Some(U.Qualification(ContextReference("independent-old-source-qualification", "1"), ContextReference("old-source-proof", "1"),
          oldconsumption, LocalPublication.Source(origin.guard.head, origin.guard.indexTree,
            origin.guard.dirty.map(d => LocalPublication.Change(d.path, d.index, d.worktree, d.renamedFrom))))))
      val qualifiedorigin = origin.copy(observation = Some(oldobservation))
      When("source construction keeps that exact observation while selecting the separately declared larger inventory")
      val qualifiedtarget = take(U._source_selection_target_c(qualifiedorigin, proposal, selected, _observe_, _inventory_))
      Then("old qualification is neither stripped nor rewritten to qualify the new selection")
      U.qualified(qualifiedorigin) shouldBe true
      qualifiedtarget.waitOrigin.get.observation shouldBe Some(oldobservation)
      qualifiedtarget.proposal.consumption shouldBe consumption
      oldobservation.qualification.get.consumption should not be consumption
      When("the same new file lacks its retained approval, coverage or complete selected inventory")
      val unapproved = U._source_selection_target_c(origin.copy(scopeApprovals = Vector.empty), proposal, selected, _observe_, _inventory_)
      val uncovered = U._source_selection_target_c(origin, proposal.copy(coverage = coverage.dropRight(1)), selected, _observe_, _inventory_)
      val incomplete = U._source_selection_target_c(origin, proposal, selected,
        (_, _) => Consequence.success(fresh.copy(selectedFiles = fresh.selectedFiles.dropRight(1))), _inventory_)
      Then("each construction refuses and none requests a committed-base inventory")
      unapproved.isSuccess shouldBe false
      uncovered.isSuccess shouldBe false
      incomplete.isSuccess shouldBe false
      inventories shouldBe 0
    }

    "keep UNKNOWN lifecycle and source qualification separate from Human authorization with strict public records" in {
      Given("M3-UNKNOWN323-CODEC: an actual-shaped UNKNOWN request, retained source and blocked native target")
      val guard = _guard.copy(dirty = Vector(S.Dirty("A", " ", F.paths.head, None)))
      val unknown = LocalPublication.Result("uncertain", "Retained intent has no result", _request.copy(participant = "participant"))
      val target = U.Target(U.schema, "fixture-component", "participant", "same-session", "25",
        Printer.noSpaces.copy(sortKeys = true).print(_controller), F.plan, F.primaryBase, 1L, Vector.empty,
        _selected.repairProgress, "25", _candidate, F.admission, Vector.empty, unknown,
        _selected.publicationPolicy, _selected.processPolicy, guard, Some(guard),
        Some(ContextReference("same-session", "24")), ContextReference("actual-accepted-semantic-result", "1"), None)
      val modes = F.paths.map(S.ExpectedMode(_, "100644"))
      val consumption = S.Consumption(F.binding.requester.workspace, F.binding.root, Vector.empty, modes,
        F.binding.checks.map(c => S.CheckProgram(c.identity, F.binding.dependency.workspace, c.kind.wire, "fixture-" + c.identity)))
      val lifecycle = U.WriterLifecycle("cncf-writer-exited", ContextReference("actual-writer-invocation", "1"),
        ContextReference("actual-writer-terminal-source", "1"), "publication-writer-lifecycle-complete",
        Printer.noSpaces.copy(sortKeys = true).print(WorkflowSbtCapture.encode(_process(ProcessExecutionTermination.Exited(0)))))
      val scope = U.scopeOf(target)
      val observation = U.Observation(U.observationSchema, ContextReference("independent-lifecycle-observation", "1"), scope, lifecycle, None)
      val qualified = target.copy(observation = Some(observation.copy(qualification = Some(U.Qualification(
        ContextReference("actual-source-qualification", "1"), ContextReference("actual-qualification-source", "1"), consumption,
        LocalPublication.Source(guard.head, guard.indexTree, guard.dirty.map(d => LocalPublication.Change(d.path, d.index, d.worktree, d.renamedFrom))))))))
      When("the public target and independent observation are converted through Core Record and generated input")
      val publictarget = U.targetRecord(qualified)
      val reference = V.WorkflowArtifactReference("independent-unknown-successor-human", "1")
      val rationale = "独立した判断理由" * 12000
      val input = V.GoalPhasePrerequisitePublicationRecoveryInput(target.sessionId, publictarget, U.choices.head,
        rationale, reference)
      val converted = take(summon[ValueReader[V.GoalPhasePrerequisitePublicationRecoveryInput]].readC(input.toRecord()))
      Then("only exact qualified data can form a legacy Direction; neither its type nor Human prose supplies missing lifecycle facts")
      U.targetValid(target) shouldBe true
      U.qualified(target) shouldBe false
      U.qualified(target.copy(observation = Some(observation))) shouldBe false
      U.directionValid(U.Direction(target, _reference, "Human says to continue")) shouldBe false
      U.directionValid(U.Direction(qualified, _reference, rationale)) shouldBe true
      val legacy = U.Direction(qualified, _reference, rationale)
      val legacyjson = Json.obj("target" -> qualified.asJson, "reference" -> _reference.asJson, "rationale" -> rationale.asJson)
      legacy.asJson shouldBe legacyjson
      legacyjson.as[U.Direction] shouldBe Right(legacy)
      U.codec.decode(Json.obj("schemaVersion" -> U.schema.asJson, "payload" -> legacyjson)) shouldBe Right(legacy)
      rationale.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 65536
      converted.rationale shouldBe rationale
      take(U.targetC(converted.target)) shouldBe qualified
      qualified.asJson.as[U.Target] shouldBe Right(qualified)
      qualified.asJson.mapObject(_.add("retry", Json.True)).as[U.Target].isLeft shouldBe true
      qualified.asJson.mapObject(_.add("observation", Json.Null)).as[U.Target].isLeft shouldBe true
      qualified.asJson.mapObject(_.add("schemaVersion", R.schema.asJson)).as[U.Target].isLeft shouldBe true
      R.decodeTarget(qualified.asJson).isLeft shouldBe true
      U.targetValid(qualified.copy(participant = "foreign")) shouldBe false
      U.targetValid(qualified.copy(sessionId = "foreign")) shouldBe false
      U.targetValid(qualified.copy(frontierRevision = "26")) shouldBe false
      U.targetValid(qualified.copy(guard = guard.copy(indexTree = "9" * 40))) shouldBe false
      Vector(ProcessExecutionTermination.NotStarted, ProcessExecutionTermination.TimedOut,
        ProcessExecutionTermination.Cancelled, ProcessExecutionTermination.TerminationUnconfirmed).foreach { termination =>
        U.lifecycleValid(lifecycle.copy(process = Printer.noSpaces.copy(sortKeys = true).print(
          WorkflowSbtCapture.encode(_process(termination))))) shouldBe false
      }
      U.lifecycleValid(lifecycle.copy(scope = "caller-only")) shouldBe false
      U.lifecycleValid(lifecycle.copy(kind = "owned-pre-execution-sentinel")) shouldBe false
      val binding = take(U.bindingC(target.componentInstance, qualified, U.choices.head, input.rationale,
        target.participant, ContextReference(reference.identity, reference.revision)))
      R.selectBinding(binding) shouldBe Right(binding)
      Vector("", " ", "bad\u0000reason", " padded ").foreach { invalid =>
        U.bindingC(target.componentInstance, qualified, U.choices.head, invalid, target.participant, _reference).isSuccess shouldBe false
      }
      U.bindingC(target.componentInstance, qualified, U.choices.head, rationale, "foreign", _reference).isSuccess shouldBe false
      U.bindingC(target.componentInstance, qualified, U.choices.head, rationale, target.participant,
        ContextReference("bad\u0000reference", "1")).isSuccess shouldBe false
      U.bindingC(target.componentInstance, qualified, R.choices.head, input.rationale, target.participant, _reference).isSuccess shouldBe false
      val description = V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult(target.sessionId, "direction-required", U.message,
        target.componentInstance, publictarget, U.choices, R.recoveryOperation)
      val prepared = take(org.simplemodeling.textus.smworkflow.preparation.NativePrerequisitePublicationRecoveryPreparation.prepareC(
        description, converted, reference, V.WorkflowExecutionEvidence(Vector(reference), Some(target.participant), None, None),
        target.participant, "independent-host", java.time.Instant.parse("2026-11-01T00:00:00Z")))
      prepared.recordIdentity should include(R.purpose)
      val preparedhuman = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      preparedhuman.binding shouldBe binding
      preparedhuman.binding.hcursor.downField("prerequisitePublicationUnknownRecovery").downField("answer")
        .get[String]("rationale") shouldBe Right(rationale)
      org.simplemodeling.textus.smworkflow.preparation.NativePrerequisitePublicationRecoveryPreparation.prepareC(
        description.copy(target = U.targetRecord(qualified.copy(processPolicy = "{\"plan\":2}"))), converted, reference,
        V.WorkflowExecutionEvidence(Vector(reference), Some(target.participant), None, None), target.participant,
        "independent-host", java.time.Instant.parse("2026-11-01T00:00:00Z")).isSuccess shouldBe false
      When("the normal passive preparation input acquires one exact independently supplied lifecycle observation")
      val observedreference = V.WorkflowArtifactReference(observation.reference.identity, observation.reference.revision)
      val records = Map("fixture:description" -> take(description.toRecord().toJsonStringC),
        "fixture:observation" -> observation.asJson.noSpaces,
        "fixture:reference" -> take(observedreference.toRecord().toJsonStringC),
        "fixture:execution" -> take(V.WorkflowExecutionEvidence(Vector(observedreference), Some(target.participant), None, None)
          .toRecord().toJsonStringC))
      val options = Map("family" -> "GoalPhase", "kind" -> "goal-phase-prerequisite-publication-observation",
        "request" -> "fixture:description", "result" -> "fixture:observation", "reference" -> "fixture:reference",
        "execution-evidence" -> "fixture:execution", "participant" -> target.participant, "issuer" -> "independent-host",
        "occurred-at" -> "2026-11-01T00:00:00Z", "collection" -> "entity:smworkflow.cli.host_evidence")
      var reads = Vector.empty[String]
      val preparedobservation = org.simplemodeling.textus.smworkflow.preparation.NativeExternalEvidenceInput.prepareC(options,
        key => { reads :+= key; Consequence.success(records(key)) })
      Then("preparation creates ordinary StartupImport data once and crossed, null or undeclared observation fields refuse")
      preparedobservation.isSuccess shouldBe true
      reads.sorted shouldBe records.keys.toVector.sorted
      val altered = Vector(observation.asJson.mapObject(_.add("retry", Json.True)),
        observation.asJson.mapObject(_.add("qualification", Json.Null)),
        observation.copy(scope = scope.copy(frontierRevision = "26")).asJson,
        observation.copy(scope = scope.copy(participant = "foreign")).asJson,
        observation.copy(reference = ContextReference("foreign-reference", "1")).asJson,
        observation.copy(lifecycle = lifecycle.copy(scope = "caller-only")).asJson)
      altered.foreach { raw =>
        org.simplemodeling.textus.smworkflow.preparation.NativeExternalEvidenceInput.prepareC(options,
          key => Consequence.success(if (key == "fixture:observation") raw.noSpaces else records(key))).isSuccess shouldBe false
      }
      val row = take(org.simplemodeling.textus.smworkflow.preparation.NativePrerequisitePublicationRecoveryPreparation.prepareObservationC(
        observation, "independent-host", java.time.Instant.parse("2026-11-01T00:00:00Z")))
      val fact = take(NativeExternalEvidence.decodeStorageC(row.record.getString(NativeExternalEvidence.storageField).get))
      fact.purpose shouldBe NativeExternalEvidence.Purpose.PrerequisitePublicationObservation.identity
      U.selectObservation(fact.binding) shouldBe Right(U.observationBinding(observation))
      val installation = R.Installation(new R.EvidencePort {
        def admitC(core: org.goldenport.cncf.action.ActionCall.Core, reference: ContextReference, binding: Json) = Consequence.unit
      })
      installation.observation shouldBe None
    }

    "round trip explicit uncertainty choices without manufacturing historical qualification or legacy permission" in {
      Given("M3-HISTORICAL-GAP-01-CODEC: a retained UNKNOWN with no writer observation or qualification")
      val target = _retained_unknown_origin
      val choices = Vector("AcceptExistingArtifactsWithUnknownEffects", "AuthorizeNewPublicationWithUnknownEffects")
      val record = _facts().initial.direction.record
      When("each independently admitted explicit choice is retained in the existing Direction envelope")
      val directions = choices.map(choice => U.Direction(target, _reference, "SPECIFICATION-OWNED unknown-effects decision", Some(choice)))
      val decoded = directions.map(direction => U.codec.decode(U.codec.encode(direction)))
      Then("each choice remains explicit and the original request, unknown status and missing observations remain unchanged")
      U.choicesFor(target) shouldBe choices :+ "Defer"
      decoded shouldBe directions.map(Right(_))
      directions.foreach { direction =>
        U.directionValid(direction) shouldBe true
        U.qualified(direction.target) shouldBe false
        direction.target.observation shouldBe None
        direction.target.unknown shouldBe target.unknown
        direction.asJson.hcursor.get[String]("choice") shouldBe Right(direction.choice.get)
        val saved = U.Saved(record, direction)
        U.permit(saved).nonEmpty shouldBe direction.choice.contains("AuthorizeNewPublicationWithUnknownEffects")
        U.existingArtifactAdoption(saved).nonEmpty shouldBe direction.choice.contains("AcceptExistingArtifactsWithUnknownEffects")
      }
      U.messageFor(target) should include(target.unknown.request.input.requestIdentity)
      U.messageFor(target) should include(target.unknown.reason)
      U.messageFor(target) should include("duplicate, concurrent or partial")
      U.messageFor(target) should include("remain unknown")
      U.bindingC(target.componentInstance, target, "AuthorizeQualifiedSuccessorPublication", "Continue", target.participant, _reference)
        .isSuccess shouldBe false
      val legacy = U.Direction(target, _reference, "Legacy cannot infer qualification")
      U.codec.decode(U.codec.encode(legacy)).isLeft shouldBe true
      Vector(None, Some("Defer"), Some("AuthorizeQualifiedSuccessorPublication"), Some("Retry"), Some("")).foreach { choice =>
        U.directionValid(directions.head.copy(choice = choice)) shouldBe false
      }
      Vector(directions.head.asJson.mapObject(_.add("choice", Json.Null)),
        directions.head.asJson.mapObject(_.remove("choice")),
        directions.head.asJson.mapObject(_.add("permission", Json.True))).foreach(_.as[U.Direction].isLeft shouldBe true)
      U.bindingC(target.componentInstance, target, choices.head, "Continue", "foreign-participant", _reference).isSuccess shouldBe false
      U.bindingC(target.componentInstance, target.copy(sessionId = "foreign-session"), choices.head, "Continue", target.participant,
        _reference).isSuccess shouldBe false

      Given("the exact description and an independently supplied Defer or explicit decision")
      val reference = V.WorkflowArtifactReference(_reference.identity, _reference.revision)
      val description = V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult(target.sessionId, "direction-required",
        U.messageFor(target), target.componentInstance, U.targetRecord(target), U.choicesFor(target), R.recoveryOperation)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(target.participant), None, None)
      def _prepare_(choice: String, selected: V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult = description,
        evidence: V.WorkflowExecutionEvidence = execution) =
        org.simplemodeling.textus.smworkflow.preparation.NativePrerequisitePublicationRecoveryPreparation.prepareC(selected,
          V.GoalPhasePrerequisitePublicationRecoveryInput(target.sessionId, U.targetRecord(target), choice, "Independent decision", reference),
          reference, evidence, target.participant, "independent-host", java.time.Instant.parse("2026-11-01T00:00:00Z"))
      When("passive preparation binds the exact choice and the independent participant")
      val prepared = (choices :+ "Defer").map(choice => choice -> take(_prepare_(choice)))
      Then("preparation retains distinct choices, and absent independent identity or changed target/choices refuses")
      prepared.foreach { case (choice, value) =>
        val evidence = take(NativeExternalEvidence.decodeStorageC(value.record.getString(NativeExternalEvidence.storageField).get))
        evidence.binding.hcursor.downField("prerequisitePublicationUnknownRecovery").downField("answer")
          .get[String]("choice") shouldBe Right(choice)
      }
      _prepare_(choices.head, evidence = execution.copy(workerIdentity = None)).isSuccess shouldBe false
      _prepare_(choices.head, selected = description.copy(choices = U.choices)).isSuccess shouldBe false
      _prepare_(choices.head, selected = description.copy(target = U.targetRecord(target.copy(processPolicy = "{\"plan\":2}"))))
        .isSuccess shouldBe false
      _prepare_("AuthorizeQualifiedSuccessorPublication").isSuccess shouldBe false
      U.directionValid(U.Direction(target, _reference, "Deferred", Some("Defer"))) shouldBe false
    }

    "distinguish Wait source uncertainty decisions from observed legacy directions without weakening source coverage" in {
      Given("M3-HISTORICAL-GAP-01-SOURCE: the exact retained Wait origin and a declared successor source with complete coverage")
      val origin = _retained_unknown_origin
      val selected = F.binding.copy(revision = 2)
      val consumption = S.Consumption(F.requester.workspace, selected.root, Vector.empty,
        selected.paths.map(S.ExpectedMode(_, "100644")),
        Vector(S.CheckProgram("primary-test", "primary-validation", "test", "fixture-primary-check")))
      val proposal = S.Proposal(S.sourceSchema, ContextReference(selected.identity, "2"), "selected-changes", Vector.empty,
        F.approvals.map(a => S.Coverage(a.decision, a.proposal.acceptance, selected.checks.map(_.identity))), consumption)
      val target = take(U._source_selection_target_c(origin, proposal, selected, (_, _) => Consequence.success(origin.guard),
        (_, _, _, _) => Consequence.stateConflict("Unchanged committed base requires no inventory")))
      val choice = "AdoptDependencySourceRevisionWithUnknownEffects"
      val direction = S.Direction(target, _reference, origin.participant, "Independent source-only decision", Some(choice))
      When("a source-only uncertainty direction is encoded through the existing Wait envelope")
      val decoded = U.sourceCodec.decode(U.sourceCodec.encode(direction))
      Then("explicit uncertainty and old absent-choice records remain readable, while new observed-choice admission still requires lifecycle proof")
      S.choicesFor(target) shouldBe Vector(choice, "Defer")
      decoded shouldBe Right(direction)
      S.directionValid(direction.copy(choice = None)) shouldBe true
      S.directionValid(direction) shouldBe true
      S.bindingC(origin.componentInstance, target, "AdoptDependencySourceRevision", direction.rationale,
        origin.participant, _reference).isSuccess shouldBe false
      direction.target.waitOrigin shouldBe Some(origin)
      direction.target.repairProgress shouldBe origin.repairProgress
      S.directionValid(direction.copy(choice = Some("AdoptDependencySourceRevision"))) shouldBe false
      S.directionValid(direction.copy(participant = "foreign")) shouldBe false
      direction.asJson.mapObject(_.add("choice", Json.Null)).as[S.Direction].isLeft shouldBe true
      direction.asJson.mapObject(_.add("unexpected", Json.True)).as[S.Direction].isLeft shouldBe true
      direction.asJson.mapObject(_.remove("choice")).as[S.Direction] shouldBe Right(direction.copy(choice = None))
      val reference = V.WorkflowArtifactReference(_reference.identity, _reference.revision)
      val description = V.GoalPhaseDependencySourceRevisionDescriptionResult(target.sessionId, "direction-required", S.message,
        origin.componentInstance, S.targetRecord(target), S.choicesFor(target), S.adoptionOperation)
      val input = V.GoalPhaseDependencySourceRevisionAdoptionInput(target.sessionId, S.targetRecord(target), choice,
        direction.rationale, reference)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(origin.participant), None, None)
      When("passive source preparation binds exactly that described source and choice")
      val prepared = take(org.simplemodeling.textus.smworkflow.preparation.NativeDependencySourceRevisionPreparation.prepareC(
        description, input, reference, execution, origin.participant, "independent-host", java.time.Instant.parse("2026-11-01T00:00:00Z")))
      Then("the stored independent decision is exact, and unavailable observed choice or changed description refuses")
      val evidence = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      evidence.binding shouldBe take(S.bindingC(origin.componentInstance, target, choice, direction.rationale, origin.participant, _reference))
      org.simplemodeling.textus.smworkflow.preparation.NativeDependencySourceRevisionPreparation.prepareC(
        description, input.copy(choice = "AdoptDependencySourceRevision"), reference, execution, origin.participant,
        "independent-host", java.time.Instant.parse("2026-11-01T00:00:00Z")).isSuccess shouldBe false
      org.simplemodeling.textus.smworkflow.preparation.NativeDependencySourceRevisionPreparation.prepareC(
        description.copy(choices = S.choices), input, reference, execution, origin.participant,
        "independent-host", java.time.Instant.parse("2026-11-01T00:00:00Z")).isSuccess shouldBe false

      Given("an authentic independently observed old writer lifecycle with no source qualification")
      val lifecycle = U.WriterLifecycle("cncf-writer-exited", ContextReference("source-writer", "1"),
        ContextReference("source-writer-terminal", "1"), "publication-writer-lifecycle-complete",
        Printer.noSpaces.copy(sortKeys = true).print(WorkflowSbtCapture.encode(_process(ProcessExecutionTermination.Exited(0)))))
      val observation = U.Observation(U.observationSchema, ContextReference("source-writer-observation", "1"), U.scopeOf(origin), lifecycle, None)
      val observed = target.copy(waitOrigin = Some(origin.copy(observation = Some(observation))))
      val legacy = S.Direction(observed, _reference, origin.participant, "Observed source decision")
      val legacyjson = Json.obj("target" -> observed.asJson, "directive" -> _reference.asJson,
        "participant" -> origin.participant.asJson, "rationale" -> legacy.rationale.asJson)
      When("the legacy absent-choice source Direction is read and re-encoded")
      val legacydecoded = legacyjson.as[S.Direction]
      Then("historical bytes remain canonical and both independent source choices are available")
      legacydecoded shouldBe Right(legacy)
      legacy.asJson shouldBe legacyjson
      U.sourceCodec.decode(Json.obj("schemaVersion" -> U.schema.asJson, "payload" -> legacyjson)) shouldBe Right(legacy)
      S.choicesFor(observed) shouldBe Vector("AdoptDependencySourceRevision", choice, "Defer")
      U.qualified(observed.waitOrigin.get) shouldBe false
      S.targetValid(target.copy(proposal = proposal.copy(coverage = Vector.empty))) shouldBe false
      S.directionValid(direction.copy(target = target.copy(waitOrigin = None))) shouldBe false
    }

    "round trip a closed exact target and independent answer without accepting extra fields or changed provenance" in {
      Given("M3R-01: an exact ordinary candidate, original failure, bounded correction and specification Human answer")
      val binding = take(R.bindingC(_selected.componentInstance, _selected, R.choices.head,
        "SPECIFICATION-OWNED correction", "participant", _reference))
      When("public target and canonical Human bindings are decoded")
      val decoded = R.decodeTarget(_selected.asJson)
      Then("the exact target and independent binding round trip, while crossed provenance refuses")
      R.targetValid(_selected) shouldBe true
      decoded shouldBe Right(_selected)
      take(R.targetC(R.targetRecord(_selected))) shouldBe _selected
      R.selectBinding(binding) shouldBe Right(binding)
      R.decodeTarget(_selected.asJson.mapObject(_.add("retry", Json.True))).isLeft shouldBe true
      R.decodeTarget(_selected.asJson.mapObject(_.add("controller", "{ \"stage\": \"Validation\" }".asJson))).isLeft shouldBe true
      R.decodeTarget(_selected.asJson.mapObject(_.add("failed", _selected.asJson.hcursor.get[Json]("failed").toOption.get
        .mapObject(_.add("process", _failed.process.get))))).isLeft shouldBe true
      R.selectBinding(binding.mapObject(_.add("retry", Json.True))).isLeft shouldBe true
      R.bindingC("foreign", _selected, R.choices.head, "Correction", "participant", _reference).isSuccess shouldBe false
      R.bindingC(_selected.componentInstance, _selected, R.choices.head, "Correction", "foreign", _reference).isSuccess shouldBe false
      R.bindingC(_selected.componentInstance, _selected, "Retry", "Correction", "participant", _reference).isSuccess shouldBe false
      R.bindingC(_selected.componentInstance, _selected, "Defer", "Correction", "participant", _reference).isSuccess shouldBe true
      R.targetValid(_selected.copy(candidate = _candidate.copy(diff = Some(""), changedPaths = Vector.empty))) shouldBe true
    }

    "preserve the actual public Record publictarget through generated input conversion and passive preparation" in {
      Given("M3R-08: the public description and an independently supplied fixture Human answer")
      val publictarget = R.targetRecord(_selected)
      val answerreference = V.WorkflowArtifactReference("fixture-public-record-answer", "1")
      val description = V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult(_selected.sessionId,
        "direction-required", R.message, _selected.componentInstance, publictarget, R.choices, R.recoveryOperation)
      val rationale = "独立した判断理由" * 12000
      val answer = V.GoalPhasePrerequisitePublicationRecoveryInput(_selected.sessionId, publictarget, R.choices.head,
        rationale, answerreference)
      When("generated ValueReaders and normal evidence preparation convert the public Records")
      val supplied = take(summon[ValueReader[V.GoalPhasePrerequisitePublicationRecoveryInput]].readC(answer.toRecord()))
      val described = take(summon[ValueReader[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]].readC(description.toRecord()))
      val prepared = take(org.simplemodeling.textus.smworkflow.preparation.NativePrerequisitePublicationRecoveryPreparation.prepareC(
        described, supplied, answerreference, V.WorkflowExecutionEvidence(Vector(answerreference), Some("participant"), None, None),
        "participant", "fixture-independent-host", java.time.Instant.parse("2026-11-01T01:02:03Z")))
      Then("the exact publictarget remains lossless and its prepared row binds the same independent answer")
      take(R.targetC(supplied.target)) shouldBe _selected
      take(R.targetC(described.target)) shouldBe _selected
      rationale.length should be > 65536
      supplied.rationale shouldBe rationale
      val evidence = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      evidence.purpose shouldBe R.purpose
      R.selectBinding(evidence.binding) shouldBe Right(evidence.binding)
      evidence.binding shouldBe take(R.bindingC(_selected.componentInstance, _selected, R.choices.head, rationale,
        "participant", ContextReference(answerreference.identity, answerreference.revision)))
      evidence.binding.hcursor.downField("prerequisitePublicationRecovery").downField("answer")
        .get[String]("rationale") shouldBe Right(rationale)
    }

    "refuse crossed Session, owner, Plan, base, source, index and path contracts" in {
      Given("M3R-02: the full old candidate and same-binding correction target")
      val crossed = Vector(
        _selected.copy(sessionId = "another-session"), _selected.copy(frontierRevision = "26"),
        _selected.copy(plan = F.plan.copy(identity = "another-plan")),
        _selected.copy(base = F.primaryBase.copy(head = "9" * 40)),
        _selected.copy(generation = 2), _selected.copy(candidate = _candidate.copy(sourceRevision = "25")),
        _selected.copy(publicationTarget = _target.copy(workspace = "foreign-workspace")),
        _selected.copy(failed = _failed.copy(request = _request.copy(input = _request.input.copy(ownerSessionId = Some("foreign"))))),
        _selected.copy(preGuard = _guard.copy(root = "/foreign/root")),
        _selected.copy(preGuard = _guard.copy(branch = "foreign")),
        _selected.copy(preGuard = _guard.copy(indexTree = "9" * 40)),
        _selected.copy(preGuard = _guard.copy(dirty = _guard.dirty :+ S.Dirty(" ", "M", "outside.scala", None))),
        _selected.copy(preGuard = _guard.copy(dirty = Vector.empty)),
        _selected.copy(processPolicy = "{\"z\":1,\"a\":2}"), _selected.copy(publicationPolicy = "{}"))
      When("those supplied targets are decoded without any effect")
      val accepted = crossed.map(value => R.decodeTarget(value.asJson).isRight)
      Then("every cross-boundary substitution refuses")
      accepted shouldBe Vector.fill(crossed.size)(false)
      When("a different canonical execution policy is bound to an independent answer")
      val policydrift = _selected.copy(processPolicy = "{\"plan\":2}")
      val changed = take(R.bindingC(_selected.componentInstance, policydrift, R.choices.head,
        "Correction", "participant", _reference))
      Then("the changed policy cannot reuse the original binding")
      changed should not be take(R.bindingC(_selected.componentInstance, _selected, R.choices.head,
        "Correction", "participant", _reference))
    }

    "classify only actual terminated nonzero process outcomes as eligible failure" in {
      Given("M3R-03: typed publication observations including UNKNOWN and nontermination")
      val outcomes = Vector(ProcessExecutionTermination.Exited(0), ProcessExecutionTermination.NotStarted,
        ProcessExecutionTermination.LaunchFailed, ProcessExecutionTermination.TimedOut,
        ProcessExecutionTermination.Cancelled, ProcessExecutionTermination.TerminationUnconfirmed,
        ProcessExecutionTermination.ArtifactLimitExceeded)
      When("the recovery contract reads the saved actual process termination")
      val rejected = outcomes.map(outcome => _failed.copy(process = Some(WorkflowSbtCapture.encode(_process(outcome))))) ++
        Vector(_failed.copy(status = "uncertain"), _failed.copy(status = "admission-rejected"), _failed.copy(process = None))
      Then("status prose or missing evidence never turns into permission")
      rejected.foreach(value => R.confirmedFailure(value) shouldBe false)
      R.confirmedFailure(_failed) shouldBe true
    }

    "reject any original native check, metadata selection or validation intent" in {
      Given("M3R-04: all finite native intent namespaces before the first check")
      val keys = Vector("selection", "validation", "check-intent-0", "check-result-0", "check-recovery-0", "check-intent-15")
      When("the passive first-check gate sees an existing fact")
      val rejected = keys.map(key => GoalPhaseNativeValidation.requireUnstartedPrerequisiteC(name => Consequence.success(name == key)).isSuccess)
      Then("every started namespace refuses and only an entirely unstarted frontier is eligible")
      rejected shouldBe Vector.fill(keys.size)(false)
      GoalPhaseNativeValidation.requireUnstartedPrerequisiteC(_ => Consequence.success(false)).isSuccess shouldBe true
    }

    "retain original primary facts and reject incomplete or forged correction stage chains" in {
      Given("M3R-05: a separately directed correction with its old completed stage history")
      val direction = R.Direction(_selected, _reference, "SPECIFICATION-OWNED correction")
      val payload = R.Payload(direction, Vector.empty, None)
      When("the persisted recovery codec is read before its new effects")
      val decoded = R.codec.decode(R.codec.encode(payload))
      Then("the original direction round trips and a supplied capture cannot bypass its actual correction stages")
      decoded shouldBe Right(payload)
      R.payloadValid(payload.copy(candidate = Some(_candidate))) shouldBe false
      R.codec.decode(R.codec.encode(payload).mapObject(_.add("permission", Json.True))).isLeft shouldBe true
      R.correctionPaths(_selected) shouldBe Vector(F.paths.head)
      _selected.candidate shouldBe _candidate
      _selected.candidate.stagedPaths shouldBe _candidate.stagedPaths
      _selected.candidate.dependencyStaged shouldBe F.paths
    }

    "authenticate only the exact stage prefix and recovered dependency while retaining the primary payload" in {
      Given("M3R-09: one attributable correction StageFact and a newly observed dependency capture")
      val after = _guard.copy(indexTree = "9" * 40, dirty = Vector(S.Dirty("A", " ", F.paths.head, None)),
        selectedEntries = _guard.selectedEntries.updated(0, S.Entry(F.paths.head, "100644", "2" * 40)))
      val fact = S.StageFact(F.paths.head, _guard, after, Vector(S.StageChange("100644", "100644", Some("1" * 40),
        Some("2" * 40), "M", F.paths.head)))
      val capture = F.capture.copy(sourceRevision = "26", candidate = F.capture.candidate.copy(tree = after.indexTree),
        diff = "diff --git a/Observation.scala b/Observation.scala\n+corrected\n")
      val recovered = _candidate.copy(sourceRevision = "26", dependency = Some(capture), dependencyOrigin = None)
      val payload = R.Payload(R.Direction(_selected, _reference, "SPECIFICATION-OWNED correction"), Vector(fact), Some(recovered))
      When("the mechanical result codec authenticates the exact direction and guarded stage prefix")
      val decoded = R.codec.decode(R.codec.encode(payload))
      Then("the exact result round trips and changes to primary facts, history or scope refuse")
      R.payloadValid(payload) shouldBe true
      decoded shouldBe Right(payload)
      R.payloadValid(payload.copy(candidate = Some(recovered.copy(diff = Some("invented primary diff"))))).shouldBe(false)
      R.payloadValid(payload.copy(candidate = Some(recovered.copy(stagedPaths = Vector.empty)))).shouldBe(false)
      R.payloadValid(payload.copy(stages = Vector(fact.copy(path = F.paths.last)))).shouldBe(false)
      R.payloadValid(payload.copy(candidate = Some(recovered.copy(dependencyOrigin =
        Some(D.CandidateOrigin("20", "26", "30")))))).shouldBe(false)
    }
  }

  private final case class Facts(initial: R.Progress, rows: Map[String, R.Saved], capture: D.Capture) {
    def reconstructC(records: Map[String, R.Saved]): Consequence[R.Progress] = {
      val actual = rows("observation-result").record
      R.reconstructC(initial, key => Consequence.success(records.get(key)), (before, saved, key) => for {
        prefix <- GoalPhaseNativeHistory.prefixC(actual, saved.record.revision)
        _ <- if (prefix == saved.record && saved.record.identity == before.identity &&
            saved.record.definition == before.definition && saved.record.revision == before.revision.next &&
            saved.record.history.dropRight(1) == before.history && saved.record.currentProgression == before.currentProgression &&
            saved.record.lifecycle == Lifecycle.Active && saved.record.suspension.isEmpty &&
            saved.record.causation.value == R.causationPrefix + _selected.frontierRevision + ":" + key) Consequence.unit
          else Consequence.stateConflict("Specification fact crossed its native predecessor")
      } yield (), supplied => if (supplied == capture) Consequence.unit
        else Consequence.stateConflict("Specification fact crossed its observed capture"))
    }
  }
  private def _facts(target: R.Target = _selected): Facts = {
    val empty = take(InstanceRecord(InstanceIdentity(_selected.sessionId), take(GoalPhaseRuntimeCatalog.loadC()).definition,
      initialRevision, Lifecycle.NotStarted, None, CorrelationReference("prerequisite-unit"),
      CausationReference("prerequisite-start"), None, None, None, Vector.empty).validateC)
    def _append_(from: InstanceRecord, key: String): InstanceRecord = take(from.appendC(from.revision,
      GoalPhaseNativeRun.history(from, "phase.Validation", R.causationPrefix + _selected.frontierRevision + ":" + key)))
    val original = (1 to 25).foldLeft(empty)((previous, index) => _append_(previous, "original-" + index))
    val payload = R.Payload(R.Direction(target, _reference, "SPECIFICATION-OWNED correction"), Vector.empty, None)
    val direction = R.Saved(_append_(original, "direction"), payload)
    val intent = R.Saved(_append_(direction.record, "stage-0-intent"), payload)
    val after = _guard.copy(indexTree = "9" * 40, dirty = Vector(S.Dirty("A", " ", F.paths.head, None)),
      selectedEntries = _guard.selectedEntries.updated(0, S.Entry(F.paths.head, "100644", "2" * 40)))
    val fact = S.StageFact(F.paths.head, _guard, after, Vector(S.StageChange("100644", "100644", Some("1" * 40),
      Some("2" * 40), "M", F.paths.head)))
    val staged = R.Saved(_append_(intent.record, "stage-0-result"), payload.copy(stages = Vector(fact)))
    val observing = R.Saved(_append_(staged.record, "observation-intent"), staged.payload)
    val capture = F.capture.copy(sourceRevision = direction.record.revision.value.toString,
      candidate = F.capture.candidate.copy(tree = after.indexTree),
      diff = "diff --git a/Observation.scala b/Observation.scala\n+corrected\n")
    val candidate = _candidate.copy(sourceRevision = capture.sourceRevision, dependency = Some(capture), dependencyOrigin = None)
    val completed = R.Saved(_append_(observing.record, "observation-result"), staged.payload.copy(candidate = Some(candidate)))
    // No runtime capability is installed in this fact-fold fixture. Unknown dispatch must stop before using one.
    val source = R.Source(GoalPhaseNativeRun.Session(null, null, "participant", completed.record), original,
      take(GoalPhaseOrderedProgression.startC(F.plan, F.primaryBase)), GoalPhaseNativeCandidate.Saved(original, _candidate),
      null, None, Vector.empty)
    Facts(R.Progress(source, direction, direction, complete = false, unknown = false),
      Map("stage-0-intent" -> intent, "stage-0-result" -> staged,
        "observation-intent" -> observing, "observation-result" -> completed), capture)
  }

  "Retained prerequisite correction facts" should {
    "read completed corrected history across process drift only with canonical corrected-candidate primary evidence" in {
      Given("F1: a fully reconstructed v1 correction followed by its actual canonical primary failure and a later successful target attempt")
      val primary = LocalPublication.Target("primary-product", "org.example.PrimaryProduct", F.requester.workspace, beforeChecks = true)
      val collection = org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(
        org.simplemodeling.model.datatype.EntityCollectionId("smworkflow", "spec", "publication"))
      val policy = LocalPublicationRuntime.Policy("fixture-publication-store", collection, Vector(_target, primary))
      val target = _selected.copy(publicationPolicy = R.publicationPolicy(policy))
      val facts = _facts(target)
      val progress = take(facts.reconstructC(facts.rows))
      val corrected = progress.candidate.get.payload
      val memory = new Memory
      memory.current = LocalPublication.Source(corrected.candidate.get.head, corrected.candidate.get.tree, Vector.empty)
      val input = LocalPublicationRuntime.prerequisiteInput(target.sessionId, corrected, primary)
      val failed = take(LocalPublication.ensurePrerequisiteC(input, primary, target.participant, memory))
      memory.result = _process(ProcessExecutionTermination.Exited(0))
      val later = take(LocalPublication.ensureC(LocalPublication.Input("later-successful-primary", primary.identity,
        Some(target.sessionId), explicit = true), primary, target.participant, memory))
      val rows = memory.rows.toMap
      val starts = memory.starts
      var supplied = Vector.empty[GoalPhaseNativeCandidate.Payload]
      def _proof_(candidate: GoalPhaseNativeCandidate.Payload): Consequence[Unit] = {
        supplied = supplied :+ candidate
        LocalPublicationRuntime.collectCorrectedPrimaryHistoryC(policy, target, candidate, memory)
      }
      val changed = "{\"currentSourceProgram\":2}"
      When("historical reading checks changed process grants after reconstructing every ordered stage and observation")
      val admitted = R.historicalProcessC(progress, changed, _proof_)
      Then("only the reconstructed corrected candidate selects the old canonical failure, independently of target-wide latest success")
      admitted.isSuccess shouldBe true
      supplied shouldBe Vector(corrected)
      supplied.head.sourceRevision should not be target.candidate.sourceRevision
      failed.status shouldBe "failed"
      later.status shouldBe "published"
      take(LocalPublication.collectLatestC(LocalPublication.LatestCollectionInput(primary.identity, target.sessionId),
        primary, target.participant, memory)) shouldBe Some(later)
      memory.rows.toMap shouldBe rows
      memory.starts shouldBe starts
      When("matching policy reads incomplete history, while changed policy lacks completed effects or genuine canonical evidence")
      val incomplete = take(facts.reconstructC(Map.empty))
      val unknown = take(facts.reconstructC(Map("stage-0-intent" -> facts.rows("stage-0-intent"))))
      supplied = Vector.empty
      val matching = Vector(incomplete, unknown, progress).map(value => R.historicalProcessC(value, target.processPolicy, _proof_))
      val refused = Vector(incomplete, unknown, progress.copy(unknown = true),
        progress.copy(saved = progress.saved.copy(payload = progress.saved.payload.copy(candidate = None))))
        .map(value => R.historicalProcessC(value, changed, _proof_))
      val missingproof = R.historicalProcessC(progress, changed,
        _ => Consequence.stateConflict("SPECIFICATION canonical primary failure is absent"))
      Then("matching historical policy is unchanged and incomplete, UNKNOWN or proofless drift never dispatches evidence or effects")
      matching.foreach(_.isSuccess shouldBe true)
      refused.foreach(_.isSuccess shouldBe false)
      missingproof.isSuccess shouldBe false
      supplied shouldBe empty
      When("crossed source revisions, primary payload, storage, target order or participant try to substitute for the canonical proof")
      val crossed = Vector(
        LocalPublicationRuntime.collectCorrectedPrimaryHistoryC(policy, target,
          corrected.copy(sourceRevision = target.candidate.sourceRevision), memory),
        LocalPublicationRuntime.collectCorrectedPrimaryHistoryC(policy, target, corrected.copy(diff = Some("crossed primary delta")), memory),
        LocalPublicationRuntime.collectCorrectedPrimaryHistoryC(policy.copy(storeName = "another-store"), target, corrected, memory),
        LocalPublicationRuntime.collectCorrectedPrimaryHistoryC(policy.copy(targets = policy.targets.reverse), target, corrected, memory),
        LocalPublicationRuntime.collectCorrectedPrimaryHistoryC(policy, target.copy(participant = "foreign-participant"), corrected, memory),
        LocalPublicationRuntime.collectCorrectedPrimaryHistoryC(policy, target, corrected, new Memory))
      Then("the original storage, ordered targets, corrected capture and exact canonical owner remain mandatory")
      crossed.foreach(_.isSuccess shouldBe false)
      memory.rows.toMap shouldBe rows
      memory.starts shouldBe starts
    }

    "retain the historical dependency guard only for uncaptured primary source selection while observing current source everywhere else" in {
      Given("F2: an authenticated SourceRequired guard and a logical workspace now rebound to a separately declared successor root")
      import PrimaryPublicationContinuationSpecificationFixture as P
      var oldobservations = 0
      var capturedobservations = Vector.empty[S.Capture]
      def _old_(): Consequence[S.Guard] = {
        oldobservations += 1
        Consequence.stateConflict("SPECIFICATION old physical root is no longer selected by this workspace")
      }
      def _captured_(capture: S.Capture): Consequence[S.Guard] = {
        capturedobservations = capturedobservations :+ capture
        Consequence.stateConflict("SPECIFICATION current captured source freshness failed")
      }
      When("the source-selection constructor obtains its no-capture dependency origin")
      val selected = R.primaryDependencyGuardC(P.guard, None, true, _old_(), _captured_)
      Then("it returns only the retained authenticated guard without observing the old physical binding")
      take(selected) shouldBe P.guard
      oldobservations shouldBe 0
      capturedobservations shouldBe empty
      R.primaryTargetValid(P.sourceRequired.copy(dependencyGuard = take(selected))) shouldBe true
      When("ordinary primary description or a captured source uses the same observation boundary")
      val ordinary = R.primaryDependencyGuardC(P.guard, None, false, _old_(), _captured_)
      val captured = R.primaryDependencyGuardC(P.guard, Some(P.capture), true, _old_(), _captured_)
      val current = R.primaryDependencyGuardC(P.guard, Some(P.capture), false, _old_(),
        capture => Consequence.success(capture.postStageGuard))
      Then("ordinary old-source failure still refuses and captured source selection cannot evade current freshness")
      ordinary.isSuccess shouldBe false
      captured.isSuccess shouldBe false
      take(current) shouldBe P.postGuard
      oldobservations shouldBe 1
      capturedobservations shouldBe Vector(P.capture)
      R.primaryProgramMatches(P.processPolicy.replace(P.consumption.sourceRoot, P.consumption.sourceRoot + "-crossed"),
        P.primary, P.consumption) shouldBe false
      S.targetValid(P.sourceTarget.copy(preStageGuard = P.guard.copy(root = P.guard.root + "-crossed"))) shouldBe false
    }

    "pause interrupted staging and observation without dispatching or accepting future results" in {
      Given("M3R-10: actual appendC native direction, stage and observation records with retained effects interrupted")
      val facts = _facts()
      val records = Vector(Map("stage-0-intent" -> facts.rows("stage-0-intent")), facts.rows - "observation-result")
      When("the authenticated fold reconstructs each missing result and ordinary Advance is repeated")
      val progress = records.map(rows => take(facts.reconstructC(rows)))
      var acknowledgements = 0
      val dispatched = progress.map(value => (1 to 2).map(_ => R.advanceC(value, null, null,
        _ => { acknowledgements += 1; Consequence.unit }).isSuccess))
      Then("each frontier remains UNKNOWN and no stage, observation, publication or acknowledgement can start")
      progress.foreach { value => value.unknown shouldBe true; value.complete shouldBe false }
      dispatched.foreach(_ shouldBe Vector(false, false))
      acknowledgements shouldBe 0

      When("future results, skipped stages, foreign predecessors or rewritten directions are supplied")
      val stageintent = facts.rows("stage-0-intent")
      val invalid = Vector(
        facts.rows - "stage-0-intent",
        facts.rows - "stage-0-result",
        facts.rows - "observation-intent",
        facts.rows.updated("stage-0-intent", stageintent.copy(record = stageintent.record.copy(identity = InstanceIdentity("foreign")))),
        facts.rows.updated("stage-0-result", facts.rows("stage-0-result").copy(record = stageintent.record)),
        facts.rows.updated("stage-0-intent", stageintent.copy(payload = stageintent.payload.copy(direction =
          stageintent.payload.direction.copy(reference = ContextReference("foreign-answer", "1"))))))
      val refused = invalid.map(rows => facts.reconstructC(rows).isSuccess)
      Then("none of those native chains can supply a recovery result")
      refused shouldBe Vector.fill(invalid.size)(false)
    }

    "retain the exact original direction and candidate only after every adjacent effect result" in {
      Given("M3R-11: the complete native appendC correction chain and its exact observed dependency capture")
      val facts = _facts()
      When("the authenticated retained fold reaches its final observation result")
      val progress = take(facts.reconstructC(facts.rows))
      val stale = facts.copy(initial = facts.initial.copy(direction = facts.initial.direction.copy(record = facts.initial.source.from)))
      Then("the exact direction, primary payload and capture survive and a stale direction refuses")
      progress.complete shouldBe true
      progress.unknown shouldBe false
      progress.direction shouldBe facts.initial.direction
      progress.saved shouldBe facts.rows("observation-result")
      progress.candidate.get.payload.dependency shouldBe Some(facts.capture)
      progress.candidate.get.payload.copy(sourceRevision = _candidate.sourceRevision,
        dependency = _candidate.dependency) shouldBe _candidate
      stale.reconstructC(facts.rows).isSuccess shouldBe false
    }
  }

  private class Memory extends LocalPublication.Port {
    val rows = scala.collection.mutable.Map.empty[String, Json]
    var current = _source
    var starts = 0
    var result = _process(ProcessExecutionTermination.Exited(7))
    def loadC(key: String) = Consequence.success(rows.get(key))
    def createC(key: String, value: Json) = if (rows.contains(key)) Consequence.stateConflict("Immutable duplicate")
      else { rows(key) = value; Consequence.unit }
    def sourceC(target: LocalPublication.Target) = Consequence.success(current)
    def executeC(target: LocalPublication.Target) = {
      starts += 1
      Consequence.success(SbtExecutionAttempt.Observed(SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC(target.workspace))),
        SbtOperation.PublishLocal, result)))
    }
    def newIdentity() = "fixture-preparation"
  }

  "Explicit existing-artifact adoption" should {
    "skip only the exact latest UNKNOWN prerequisite at unchanged source without publishing or retaining a fake success" in {
      Given("M3-HISTORICAL-GAP-01-ARTIFACTS: a real in-memory UNKNOWN attempt and an explicit independent artifact decision")
      val memory = new Memory
      memory.result = _process(ProcessExecutionTermination.TimedOut)
      val old = take(LocalPublication.ensurePrerequisiteC(_input, _target, "actor", memory))
      val target = _retained_unknown_origin.copy(participant = "actor", unknown = old)
      val direction = U.Direction(target, _reference, "SPECIFICATION-OWNED accept existing artifacts with unknown effects",
        Some("AcceptExistingArtifactsWithUnknownEffects"))
      val saved = U.Saved(_facts().initial.direction.record, direction)
      val adoption = U.existingArtifactAdoption(saved).get
      memory.current = adoption.source
      val retained = memory.rows.toMap
      When("the same read-only gate used before native checks is called repeatedly")
      val results = (1 to 2).map(_ => LocalPublicationRuntime.adoptExistingArtifactsC(adoption, _target, target.sessionId,
        target.participant, Consequence.success(target.guard), memory).isSuccess)
      Then("the gate permits normal checks and creates no publication request, process, result or success receipt")
      results shouldBe Vector(true, true)
      U.permit(saved) shouldBe None
      U.qualified(target) shouldBe false
      memory.starts shouldBe 1
      memory.rows.toMap shouldBe retained
      take(LocalPublication.collectC(old.request.input, _target, "actor", memory)) shouldBe Some(old)
      old.status shouldBe "uncertain"
      adoption.previous shouldBe old

      When("the selected source bytes, guard, owner, participant, target or original result differs")
      val changedguard = target.guard.copy(selectedFiles = target.guard.selectedFiles.updated(0,
        S.fileIdentity(F.paths.head, Some("changed after Human decision\n".getBytes.toVector))))
      val changedbytes = LocalPublicationRuntime.adoptExistingArtifactsC(adoption, _target, target.sessionId,
        target.participant, Consequence.success(changedguard), memory)
      memory.current = adoption.source.copy(tree = "9" * 40)
      val changedsource = LocalPublicationRuntime.adoptExistingArtifactsC(adoption, _target, target.sessionId,
        target.participant, Consequence.success(target.guard), memory)
      memory.current = adoption.source
      val wrongowner = LocalPublicationRuntime.adoptExistingArtifactsC(adoption, _target, "foreign-session",
        target.participant, Consequence.success(target.guard), memory)
      val wrongparticipant = LocalPublicationRuntime.adoptExistingArtifactsC(adoption, _target, target.sessionId,
        "foreign-participant", Consequence.success(target.guard), memory)
      val wrongtarget = LocalPublicationRuntime.adoptExistingArtifactsC(adoption, _target.copy(workspace = "foreign-workspace"),
        target.sessionId, target.participant, Consequence.success(target.guard), memory)
      val wrongresult = LocalPublicationRuntime.adoptExistingArtifactsC(adoption.copy(previous = old.copy(reason = "invented")),
        _target, target.sessionId, target.participant, Consequence.success(target.guard), memory)
      val fakesuccess = LocalPublicationRuntime.adoptExistingArtifactsC(adoption.copy(previous = old.copy(status = "published")),
        _target, target.sessionId, target.participant, Consequence.success(target.guard), memory)
      Then("every crossed binding refuses and no additional publication starts or facts are appended")
      Vector(changedbytes, changedsource, wrongowner, wrongparticipant, wrongtarget, wrongresult, fakesuccess)
        .foreach(_.isSuccess shouldBe false)
      memory.starts shouldBe 1
      memory.rows.toMap shouldBe retained

      Given("a separate explicit Human direction authorizing a new publication despite unknown historical effects")
      memory.result = _process(ProcessExecutionTermination.Exited(0))
      val publicationdirection = U.Direction(target, ContextReference("later-independent-publication-human", "1"),
        "SPECIFICATION-OWNED authorize a new publication with unknown effects", Some("AuthorizeNewPublicationWithUnknownEffects"))
      val permit = U.permit(saved.copy(direction = publicationdirection)).get
      val laterinput = LocalPublication.Input("later-artifact-target-attempt", _target.identity, Some(target.sessionId))
      val later = take(LocalPublication.ensureQualifiedSuccessorC(laterinput, _target, target.participant, permit, memory))
      val latest = memory.rows.toMap
      When("that new publication is recollected and the old artifact direction tries to skip over it")
      val recollected = (1 to 2).map(_ => take(LocalPublication.ensureQualifiedSuccessorC(
        laterinput, _target, target.participant, permit, memory)))
      val stale = LocalPublicationRuntime.adoptExistingArtifactsC(adoption, _target, target.sessionId, target.participant,
        Consequence.success(target.guard), memory)
      Then("the new publication executes once, the stale adoption refuses, and the original UNKNOWN stays exact")
      later.ready shouldBe true
      recollected.foreach(_ shouldBe later)
      publicationdirection.target.observation shouldBe None
      U.qualified(publicationdirection.target) shouldBe false
      stale.isSuccess shouldBe false
      take(LocalPublication.collectC(old.request.input, _target, target.participant, memory)) shouldBe Some(old)
      memory.starts shouldBe 2
      memory.rows.toMap shouldBe latest
    }
  }

  "Passive failed prerequisite collection" should {
    "retain a failed or UNKNOWN approved successor without retrying its effect" in {
      Vector(ProcessExecutionTermination.Exited(9), ProcessExecutionTermination.TimedOut).foreach { termination =>
        Given("M3R-12: the original failure and one independently approved candidate source revision")
        val memory = new Memory
        val old = take(LocalPublication.ensurePrerequisiteC(_input, _target, "actor", memory))
        val original = memory.rows.toMap
        memory.current = _source.copy(tree = "9" * 40, dirty = Vector(LocalPublication.Change(F.paths.head, "A", " ")))
        memory.result = _process(termination)
        val successorinput = LocalPublicationRuntime.prerequisiteInput("same-session", _candidate.copy(sourceRevision = "26"), _target)
        When("ordinary prerequisite publication retains that successor's actual failed or uncertain result")
        val successor = take(LocalPublication.ensurePrerequisiteC(successorinput, _target, "actor", memory))
        val retained = memory.rows.toMap
        val repeated = (1 to 2).map(_ => take(LocalPublication.ensurePrerequisiteC(successorinput, _target, "actor", memory)))
        Then("the same request and process stay selected, the old failure is immutable and starts remain two")
        successor.status shouldBe (if (termination == ProcessExecutionTermination.TimedOut) "uncertain" else "failed")
        successor.request.input.requestIdentity should not be old.request.input.requestIdentity
        repeated.foreach(_ shouldBe successor)
        memory.starts shouldBe 2
        memory.rows.toMap shouldBe retained
        original.foreach { case (key, value) => memory.rows(key) shouldBe value }
      }
    }

    "preserve normal failed retention despite corrected bytes and reject a later target attempt" in {
      Given("M3R-06: one actual specification-owned failed prerequisite and an in-scope worktree correction")
      val memory = new Memory
      val old = take(LocalPublication.ensurePrerequisiteC(_input, _target, "actor", memory))
      val rows = memory.rows.toMap
      memory.current = _source.copy(dirty = Vector(LocalPublication.Change(F.paths.head, "A", "M")))
      When("ordinary ensure and passive recovery description inspect the same original request")
      val retained = take(LocalPublication.ensurePrerequisiteC(_input, _target, "actor", memory))
      val collected = take(LocalPublication.failedPrerequisiteC(_input, _target, "actor", memory))
      Then("the immutable failure stays selected and passive collection creates no effect or store record")
      retained shouldBe old
      collected shouldBe old
      memory.rows.toMap shouldBe rows
      memory.starts shouldBe 1
      memory.result = _process(ProcessExecutionTermination.Exited(0))
      When("another ordinary publication becomes the target's latest attempt")
      val later = take(LocalPublication.ensureC(LocalPublication.Input("different-target-attempt", _target.identity, Some("same-session")),
        _target, "actor", memory))
      Then("the historical failure cannot authorize recovery for that later target")
      later.ready shouldBe true
      LocalPublication.failedPrerequisiteC(_input, _target, "actor", memory).isSuccess shouldBe false
      memory.starts shouldBe 2
    }

    "refuse missing failure, UNKNOWN and not-started prerequisites without preparing a new attempt" in {
      Given("M3R-07: absent or non-failed native publication evidence")
      val missing = new Memory
      When("passive recovery looks for an absent request")
      val absent = LocalPublication.failedPrerequisiteC(_input, _target, "actor", missing)
      Then("it refuses without preparing any request")
      absent.isSuccess shouldBe false
      missing.rows shouldBe empty
      Vector(ProcessExecutionTermination.TimedOut, ProcessExecutionTermination.NotStarted,
        ProcessExecutionTermination.Exited(0)).foreach { termination =>
        val memory = new Memory
        memory.result = _process(termination)
        take(LocalPublication.ensurePrerequisiteC(_input, _target, "actor", memory))
        val before = memory.rows.toMap
        When("passive recovery checks the exact outcome")
        val refused = LocalPublication.failedPrerequisiteC(_input, _target, "actor", memory)
        Then("no alternative request or execution is created")
        refused.isSuccess shouldBe false
        memory.rows.toMap shouldBe before
        memory.starts shouldBe 1
      }
    }
  }

  "Primary repair v3 public recovery" should {
    "prepare the complete new-candidate scope through the existing independent evidence purpose" in {
      Given("a repaired primary candidate under genuinely narrowed legacy publication authority")
      import PrimaryPublicationRepairSpecificationFixture as P
      import GoalPhasePrerequisitePrimaryRepair as A
      val state = P.nextValidation(P.narrowRepair, P.narrowCandidate.candidate.get)
      val failure = P.narrowFailure
      val target = A.Target(A.targetSchema, failure.componentInstance, failure.participant, failure.sessionId, "42",
        P.sorted(R.controller(state)), state.generation, "42", P.narrowCandidate, failure, Some(P.narrowReuse.repair),
        A.scope(failure), "new-primary-publication")
      val description = A.description(target)
      val reference = V.WorkflowArtifactReference("independent-primary-scope-answer", "1")
      val input = V.GoalPhasePrerequisitePublicationRecoveryInput(failure.sessionId, description.target,
        "AuthorizePrimaryPublicationAndSameScopeRepairs", "SPECIFICATION-OWNED same Component Step programs and destination", reference)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(failure.participant), None, None)
      When("normal passive preparation validates the complete answer and closed v3 target")
      val prepared = org.simplemodeling.textus.smworkflow.preparation.NativePrerequisitePublicationRecoveryPreparation.prepareC(
        description, input, reference, execution, failure.participant, "independent-host", java.time.Instant.parse("2026-11-02T00:00:00Z"))
      Then("the existing purpose accepts the shape without provisioning authority or changing old v2 choices")
      prepared.isSuccess shouldBe true
      R.recoveryInputC(input).isSuccess shouldBe true
      R.recoveryInputC(input.copy(choice = "arbitrary-publication")).isSuccess shouldBe false
      take(A.targetC(description.target)).candidate shouldBe P.narrowCandidate
      PrimaryPublicationContinuationSpecificationFixture.sourceRequired.schemaVersion shouldBe R.primarySchema
      R.primaryChoices(PrimaryPublicationContinuationSpecificationFixture.sourceRequired) shouldBe Vector("Defer")
    }
  }
}
