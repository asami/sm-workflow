package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, Printer}
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.{NativeDependencySourceRevisionPreparation, NativePrerequisitePublicationRecoveryPreparation}
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhasePublishedDependencySourceAdoption as P
import GoalPhasePrerequisitePublicationHostRecovery as H
import GoalPhasePrerequisitePublicationRecovery as B
import GoalPhaseDependencySourceRevision as R
import GoalPhaseDependencyValidation.given
import GoalPhaseOrderedProgression as O
import DependencyValidationSpecificationFixture as D
import PrimaryPublicationContinuationSpecificationFixture as U
import PrimaryPublicationRepairSpecificationFixture as Process
import SplitPhaseExplicitPartitionFixture.take

/** Every process, file and Human reference below is specification-owned data.
  * These pure model examples establish no actual execution or consumer consent.
  * @since   Nov.  7, 2026
  * @version Nov.  7, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object PublishedDependencySourceSpecificationFixture {
  def sorted(value: Json): String = Printer.noSpaces.copy(sortKeys = true).print(value)
  val processPolicy = sorted(Json.obj("processPlan" -> Json.obj("sbtPrograms" -> Json.arr(
    Json.obj("target" -> U.primary.workspace.asJson, "operation" -> "publish-local".asJson,
      "identity" -> Process.program.asJson, "environment" -> Json.obj("TEXTUS_SOURCE_ROOT" -> D.binding.root.asJson)),
    Json.obj("target" -> U.dependency.workspace.asJson, "operation" -> "publish-local".asJson,
      "identity" -> "specification-dependency-publication".asJson)),
    "workspaces" -> Json.arr(Json.obj("identity" -> D.binding.dependency.workspace.asJson, "root" -> D.binding.root.asJson)))))
  val initial = take(O.startC(D.plan, D.primaryBase, Some(GoalPhaseRuntimeRepairControl.version)))
  val controller = take(take(initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key,
    "SPECIFICATION-OWNED original implementation"))).acceptC(O.CandidateObserved(U.candidate.candidate.get)))
  val oldSource = LocalPublication.Source(D.capture.candidate.head, D.capture.candidate.tree, Vector.empty)
  val old = U.unknown.copy(status = "published", reason = "SPECIFICATION-OWNED original success",
    request = U.unknown.request.copy(source = oldSource), process = Some(Process.process("[success] published\n",
      ProcessExecutionTermination.Exited(0), identity = "specification-dependency-publication")))
  val failed = U.failed.copy(process = Some(Process.process(PublicationHostRecoverySpecificationFixture.output)))
  val historical = H.Target(H.schema, "fixture-component", "participant", "same-session", "25",
    sorted(B.controller(controller)), D.plan, D.primaryBase, 1L, Vector.empty, GoalPhaseRepairProgress.snapshot(controller),
    "25", U.candidate, ContextReference("accepted-original-primary-result", "1"), Vector(U.dependency, U.primary),
    Process.publicationPolicy(Vector(U.dependency, U.primary)), processPolicy, failed, Vector(old), Vector.empty,
    H.Lineage(None, Vector.empty, None, Vector.empty, Vector.empty))
  val guard = U.corrected
  val external = old.copy(reason = "SPECIFICATION-OWNED externally published repaired input", request = old.request.copy(
    input = old.request.input.copy(requestIdentity = "external-owner:publication:repaired", ownerSessionId = Some("external-owner"), explicit = true),
    source = P.sourceOf(guard), participant = "external-publisher"))
  val observation = P.PublicationObservation(P.CollectionResult(external.status, external.reason, external), P.collectOperation,
    ContextReference("specification-normal-collect", "1"), "specification-external-store-owner", "2026-11-07T00:00:00Z",
    processPolicy, "file:///specification/local-publication", ContextReference("specification-published-artifact", "1"))
  val origin = P.SourceOrigin(historical, observation, U.dependency, D.admission, Vector.empty, Vector.empty)
  val proposal = U.proposal
  val target = R.Target(R.publishedSchema, historical.sessionId, "", "25", "25", D.plan, D.binding.step, 1L,
    D.primaryBase, Vector.empty, historical.repairProgress, historical.requestingResult, D.admission, Vector.empty,
    proposal, D.binding.copy(revision = 2), None, guard, publishedOrigin = Some(origin))
  val direction = R.Direction(target, ContextReference("specification-source-human", "1"), "participant",
    "SPECIFICATION-OWNED exact repaired source selection", Some(P.sourceChoice))
  val capture = U.capture.copy(schemaVersion = R.publishedCaptureSchema, direction = direction, sourceRevision = "26")
  val candidate = U.currentCandidate.copy(sourceRevision = "26", dependencySourceDirection = Some(direction), dependencySourceCapture = Some(capture))
  val artifact = P.ArtifactTarget(P.artifactSchema, historical.componentInstance, historical.participant, historical.sessionId,
    "33", sorted(B.controller(take(controller.adoptPrerequisiteSourceRevisionC(direction.directive)))), "33", candidate,
    ContextReference("same-session", "26"), origin, observation, capture.postStageGuard, processPolicy, historical.publicationPolicy)
  val artifactDirection = P.ArtifactDirection(artifact, P.artifactChoice, "SPECIFICATION-OWNED adopt exact recollection",
    "participant", ContextReference("specification-artifact-human", "1"))
  val fact = P.ArtifactFact(artifactDirection, ContextReference("same-session", "34"))
  val host = historical.copy(schemaVersion = H.schemaV2, frontierRevision = "34", controller = artifact.controller,
    generation = 2L, candidateRecordRevision = "33", candidate = candidate, publishedDependencyAdoptions = Vector(fact))
  def withObservation(value: P.PublicationObservation): R.Target = target.copy(publishedOrigin = Some(origin.copy(publication = value)))
  def withFailure(value: LocalPublication.Result): H.Target = {
    val previous = historical.copy(failed = value)
    val sourceorigin = origin.copy(historical = previous)
    val sourcetarget = target.copy(publishedOrigin = Some(sourceorigin))
    val sourcedirection = direction.copy(target = sourcetarget)
    val sourcecapture = capture.copy(direction = sourcedirection)
    val sourcecandidate = candidate.copy(dependencySourceDirection = Some(sourcedirection), dependencySourceCapture = Some(sourcecapture))
    val artifacttarget = artifact.copy(candidate = sourcecandidate, origin = sourceorigin)
    val artifactdirection = artifactDirection.copy(target = artifacttarget)
    val artifactfact = fact.copy(direction = artifactdirection)
    host.copy(failed = value, candidate = sourcecandidate, publishedDependencyAdoptions = Vector(artifactfact))
  }
}

/** Closed new authority contracts retain the old v1 and UNKNOWN meanings.
  * @since   Nov.  7, 2026
  * @version Nov.  7, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhasePublishedDependencySourceAdoptionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import PublishedDependencySourceSpecificationFixture as F
  "Published dependency adoption" should {
    "retain source, artifact and host contracts separately" which {
      "round trip the complete closed source proposal, capture, artifact and v2 host target" in {
        Given("R1–R4: specification-owned original success, packaging failure and external recollection")
        val proposal = P.SourceProposal(P.proposalSchema, F.proposal, F.observation)
        When("the new closed codecs interpret the exact data")
        val source = F.target.asJson.as[R.Target]
        Then("old source/result identities remain literal and no UNKNOWN is created")
        proposal.asJson.as[P.SourceProposal] shouldBe Right(proposal)
        source shouldBe Right(F.target)
        F.capture.asJson.as[R.Capture] shouldBe Right(F.capture)
        F.artifact.asJson.as[P.ArtifactTarget] shouldBe Right(F.artifact)
        F.host.asJson.as[H.Target] shouldBe Right(F.host)
        F.host.dependencyPrefix shouldBe Vector(F.old)
        F.host.failed shouldBe F.failed
        F.host.lineage.unknown shouldBe None
        F.observation.result.publication.request.participant shouldBe "external-publisher"
        F.target.predecessorGuard shouldBe None
        F.historical.asJson.hcursor.downField("publishedDependencyAdoptions").focus shouldBe None
      }
      "refuse missing null unknown and mixed-version fields" in {
        Given("a closed complete new target and its exact public field set")
        val raw = F.target.asJson
        When("the schema is incomplete, mixed or carries an undeclared authority claim")
        val invalid = Vector(raw.mapObject(_.remove("publishedOrigin")), raw.mapObject(_.add("publishedOrigin", Json.Null)),
          raw.mapObject(_.add("authority", Json.True)), raw.mapObject(_.add("schemaVersion", R.sourceSchema.asJson)))
        Then("none is an admitted source revision")
        invalid.foreach(_.as[R.Target].isLeft shouldBe true)
        F.artifact.asJson.mapObject(_.add("publication", Json.Null)).as[P.ArtifactTarget].isLeft shouldBe true
        F.host.asJson.mapObject(_.remove("publishedDependencyAdoptions")).as[H.Target].isLeft shouldBe true
        F.host.copy(schemaVersion = H.schema).asJson.as[H.Target].isLeft shouldBe true
        H.targetValid(F.historical) shouldBe true
      }
    }
    "bind historical capture and current source without fabricating file history" which {
      "require the real legacy snapshot when no historical Guard exists" in {
        Given("R1: a legacy dependency Capture with no file hashes")
        val old = F.historical.candidate
        When("the historical capture or successful publication snapshot is crossed")
        val crossedsource = F.oldSource.copy(tree = "0" * 40)
        val crossedrequest = F.old.request.copy(source = crossedsource)
        val crossedpublication = F.old.copy(request = crossedrequest)
        val crossedhistory = F.historical.copy(dependencyPrefix = Vector(crossedpublication))
        val origins = Vector(F.origin.copy(historical = F.historical.copy(candidate = old.copy(dependency = None))),
          F.origin.copy(historical = crossedhistory))
        Then("the absence of a Guard cannot weaken old capture/source equality")
        origins.foreach(P.originValid(_) shouldBe false)
        R.targetValid(F.target.copy(schemaVersion = R.sourceSchema, publishedOrigin = None)) shouldBe false
      }
      "account for the actual index-only transition without rewriting the publisher source" in {
        Given("R2: a published pre-stage input and typed staging/capture specification data")
        When("the retained capture connects pre-stage input to its actual post-stage guard")
        val accepted = R.captureValid(F.capture)
        Then("the original publication source stays pre-stage and current freshness uses post-stage")
        accepted shouldBe true
        F.capture.preStageGuard shouldBe F.guard
        P.sourceOf(F.capture.preStageGuard) shouldBe F.external.request.source
        P.sourceOf(F.capture.postStageGuard) should not be F.external.request.source
        val stagedrequest = F.external.request.copy(source = P.sourceOf(F.capture.postStageGuard))
        val stagedpublication = F.external.copy(request = stagedrequest)
        val stagedresult = F.observation.result.copy(publication = stagedpublication)
        val stagedobservation = F.observation.copy(result = stagedresult)
        val crossedartifact = F.artifact.copy(publication = stagedobservation)
        P.artifactTargetValid(crossedartifact) shouldBe false
      }
      "let the exact new source Human select one declared concrete added regression path" in {
        Given("R2: the higher binding explicitly includes an additional source regression file")
        val path = "src/test/scala/AbiRegressionSpec.scala"
        val binding = F.target.binding.copy(paths = F.target.binding.paths :+ path)
        val guard = F.guard.copy(dirty = F.guard.dirty :+ R.Dirty("?", "?", path, None),
          selectedFiles = F.guard.selectedFiles :+ R.fileIdentity(path, Some("SPECIFICATION-OWNED regression\n".getBytes.toVector)))
        val consumption = F.proposal.consumption.asInstanceOf[R.Consumption].copy(expectedModes =
          F.proposal.consumption.expectedModes :+ R.ExpectedMode(path, "100644"))
        val observed = F.observation.copy(result = F.observation.result.copy(publication = F.external.copy(
          request = F.external.request.copy(source = P.sourceOf(guard)))))
        val selected = F.target.copy(binding = binding, preStageGuard = guard,
          proposal = F.proposal.copy(consumption = consumption), publishedOrigin = Some(F.origin.copy(publication = observed, addedPaths = Vector(path))))
        When("the new exact source selection is interpreted")
        val valid = R.targetValid(selected)
        Then("the added path is explicit while original approvals and coverage remain literal")
        valid shouldBe true
        selected.predecessor shouldBe D.admission
        R.targetValid(selected.copy(publishedOrigin = Some(selected.publishedOrigin.get.copy(addedPaths = Vector.empty)))) shouldBe false
        R.targetValid(selected.copy(binding = binding.copy(root = "/crossed-root"))) shouldBe false
      }
    }
    "retain later independently directed host failures" which {
      "describe only the exact current-candidate successor while preserving original publication history" in {
        Given("CB-P611-PUBLISHED-HOST-SUCCESSOR: specification-owned port/process observations, not native execution or Human consent")
        final class Memory extends LocalPublication.Port {
          val rows = scala.collection.mutable.Map.empty[String, Json]
          var starts = 0
          def loadC(key: String) = Consequence.success(rows.get(key))
          def createC(key: String, value: Json): Consequence[Unit] =
            if (rows.contains(key)) Consequence.stateConflict("Specification immutable duplicate")
            else { rows(key) = value; Consequence.unit }
          def sourceC(target: LocalPublication.Target) = Consequence.success(U.primarySource)
          def executeC(target: LocalPublication.Target) = {
            starts += 1
            val process = WorkflowSbtCapture.decode(Process.process(PublicationHostRecoverySpecificationFixture.output)).toOption.get
            val workspace = take(org.goldenport.cncf.workspace.WorkspaceId.parseC(target.workspace))
            Consequence.success(SbtExecutionAttempt.Observed(SbtExecutionResult(SbtTarget(workspace), SbtOperation.PublishLocal, process)))
          }
          def newIdentity() = "specification-host-failure-observation"
        }
        val port = new Memory
        val original = take(LocalPublication.ensurePrerequisiteC(LocalPublicationRuntime.prerequisiteInput(F.host.sessionId,
          F.historical.candidate, U.primary), U.primary, F.host.participant, port))
        val first = F.withFailure(original)
        val correction = PublicationHostRecoverySpecificationFixture.correction.copy(
          failedRequestIdentity = original.request.input.requestIdentity, programIdentity = Process.program)
        val firstdirection = H.Direction(first, H.authorize, "SPECIFICATION-OWNED first exact host successor",
          first.participant, ContextReference("specification-first-host-human", "1"), Some(correction))
        val saved = H.Saved(Process.nativeRecord(35L), firstdirection)
        val priorrows = port.rows.toMap
        When("the specification port retains a qualified failed successor at the exact existing host input")
        val failed = take(LocalPublication.ensureConfirmedFailureSuccessorC(H.input(saved.permit), U.primary,
          first.participant, saved.permit.successor, port))
        val next = first.copy(frontierRevision = saved.reference.revision, failed = failed, priorDirections = Vector(saved.reference))
        val proof = H.publishedFailureC(next, Vector(saved), port)
        val nextcorrection = correction.copy(failedRequestIdentity = failed.request.input.requestIdentity)
        val nextdirection = H.Direction(next, H.authorize, "SPECIFICATION-OWNED fresh independent host correction",
          next.participant, ContextReference("specification-next-host-human", "1"), Some(nextcorrection))
        Then("a fresh target/direction is possible without altering the original failure, successful prefix or source capture")
        H.targetValid(first) shouldBe true
        H.directionValid(firstdirection) shouldBe true
        failed.request.input shouldBe H.input(saved.permit)
        failed.request.input.requestIdentity should startWith (next.sessionId + ":publication:" + next.candidate.sourceRevision + ":")
        H.targetValid(next) shouldBe true
        next.asJson.as[H.Target] shouldBe Right(next)
        proof.isSuccess shouldBe true
        H.directionValid(nextdirection) shouldBe true
        next.publishedDependencyAdoptions.head.direction.target.origin.historical.failed shouldBe original
        next.dependencyPrefix shouldBe first.dependencyPrefix
        next.candidate shouldBe first.candidate
        next.repairProgress shouldBe first.repairProgress
        priorrows.foreach((key, value) => port.rows(key) shouldBe value)
        And("later failures require an actual matching saved direction and retained result, not a supplied reference alone")
        H.publishedFailureC(next, Vector.empty, port).isSuccess shouldBe false
        val stalerecord = saved.copy(record = Process.nativeRecord(36L))
        H.publishedFailureC(next, Vector(stalerecord), port).isSuccess shouldBe false
        val stalecontroller = first.copy(controller = F.historical.controller)
        val staledirection = saved.copy(direction = firstdirection.copy(target = stalecontroller))
        H.publishedFailureC(next, Vector(staledirection), port).isSuccess shouldBe false
        val missing = new Memory
        H.publishedFailureC(next, Vector(saved), missing).isSuccess shouldBe false
        missing.starts shouldBe 0
        And("crossed successor input, direction revision, primary source, target and policies remain refused")
        val crossedinput = failed.request.input.copy(requestIdentity = failed.request.input.requestIdentity + ":unrelated")
        val crossedrequest = failed.request.copy(input = crossedinput)
        val crossedidentity = next.copy(failed = failed.copy(request = crossedrequest))
        val crossedrevision = next.copy(priorDirections = Vector(saved.reference.copy(revision = "34")))
        val crossedsource = failed.request.source.copy(dirty = Vector(LocalPublication.Change("screen.scala", "M", " ")))
        val crossedsourcefailure = failed.copy(request = failed.request.copy(source = crossedsource))
        val crossedtarget = failed.copy(request = failed.request.copy(target = U.primary.copy(workspace = "another-primary")))
        val invalid = Vector(crossedidentity, crossedrevision, next.copy(failed = crossedsourcefailure),
          next.copy(failed = crossedtarget), next.copy(processPolicy = "{}"), next.copy(publicationPolicy = "{}"))
        invalid.foreach(H.targetValid(_) shouldBe false)
        When("the normal qualified collector reads the same retained failure repeatedly")
        val collected = take(H.collectC(saved.permit, port))
        val repeated = take(H.collectC(saved.permit, port))
        Then("both reads retain the failure and perform no additional specification-port execution")
        collected shouldBe Some(failed)
        repeated shouldBe collected
        port.starts shouldBe 2
      }
    }
    "retain artifact rationale as business text" which {
      "preserve multiline and surrounding whitespace in the complete independent binding" in {
        Given("CB-P611-PUBLISHED-RATIONALE: a valid concrete artifact target and a complete multiline Human explanation")
        val rationale = "  採用の理由\nThe exact repaired publication is selected.\n\tRetain this whitespace.  "
        val direction = F.artifactDirection.copy(rationale = rationale)
        When("the artifact direction and binding interpret the original business text")
        val binding = take(P.bindingC(F.artifact.componentInstance, F.artifact, P.artifactChoice, rationale,
          F.artifact.participant, direction.reference))
        Then("the rationale remains exact and is accepted through both closed owners")
        P.directionValid(direction) shouldBe true
        direction.asJson.as[P.ArtifactDirection] shouldBe Right(direction)
        binding.hcursor.downField("prerequisitePublicationRecovery").downField("answer").get[String]("rationale") shouldBe Right(rationale)
        P.selectBinding(binding) shouldBe Right(binding)
      }
      "refuse absent blank NUL and unencodable rationale without changing identifier rules" in {
        Given("the same artifact target and invalid business-text examples")
        val unencodable = new String(Array(0xd800.toChar))
        val invalid = Vector(null, "   \n\t", "reason\u0000crossed", unencodable)
        When("artifact direction and independent binding apply the established business-text rule")
        val directions = invalid.map(rationale => P.directionValid(F.artifactDirection.copy(rationale = rationale)))
        val bindings = invalid.map(rationale => P.bindingC(F.artifact.componentInstance, F.artifact, P.artifactChoice,
          rationale, F.artifact.participant, F.artifactDirection.reference).isSuccess)
        Then("all invalid rationale is refused and identifiers still require exact identifier text")
        directions shouldBe Vector.fill(invalid.size)(false)
        bindings shouldBe Vector.fill(invalid.size)(false)
        P.directionValid(F.artifactDirection.copy(participant = " participant ")) shouldBe false
      }
    }
    "reject incomplete and crossed evidence" which {
      "admit only complete Exited zero at the selected normal publication program" in {
        Given("R1: specification-owned complete process captures")
        val invalid = Vector(ProcessExecutionTermination.TimedOut, ProcessExecutionTermination.TerminationUnconfirmed,
          ProcessExecutionTermination.NotStarted, ProcessExecutionTermination.Exited(1))
        When("a recollection is running, uncertain, failed, truncated or from another program")
        val results = invalid.map(t => F.external.copy(process = Some(Process.process("published", t,
          identity = "specification-dependency-publication")))) ++ Vector(F.external.copy(status = "uncertain"),
          F.external.copy(process = Some(Process.process("published", ProcessExecutionTermination.Exited(0), truncated = true,
            identity = "specification-dependency-publication"))), F.external.copy(process = Some(Process.process("published", ProcessExecutionTermination.Exited(0)))))
        Then("none grants a source selection")
        results.foreach(r => P.observationValid(F.observation.copy(result = P.CollectionResult(r.status, r.reason, r))) shouldBe false)
        P.observationValid(F.observation.copy(collectOperation = "ensureLocalPublication")) shouldBe false
        P.observationValid(F.observation.copy(destination = "relative/path")) shouldBe false
        P.originValid(F.origin.copy(selectedTarget = U.primary)) shouldBe false
        val primaryrequest = F.external.request.copy(target = U.primary)
        val primarypublication = F.external.copy(request = primaryrequest)
        val primaryresult = F.observation.result.copy(publication = primarypublication)
        val primaryobservation = F.observation.copy(result = primaryresult)
        val crossedorigin = F.origin.copy(publication = primaryobservation)
        P.originValid(crossedorigin) shouldBe false
      }
      "bind artifact adoption to the exact frontier, provenance and independent references" in {
        Given("R3: the artifact target and an independently named specification Human")
        val described = P.artifactDescription(F.artifact)
        val reference = V.WorkflowArtifactReference(F.artifactDirection.reference.identity, F.artifactDirection.reference.revision)
        val input = V.GoalPhasePrerequisitePublicationRecoveryInput(F.artifact.sessionId, described.target,
          P.artifactChoice, F.artifactDirection.rationale, reference, None)
        val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
        When("passive evidence preparation receives no actual recollection/artifact references")
        val prepared = NativePrerequisitePublicationRecoveryPreparation.prepareC(described, input, reference, execution,
          "participant", "specification-issuer", Instant.parse("2026-11-07T00:00:01Z"))
        Then("caller JSON is refused and no new fact can reuse the old frontier")
        prepared.isSuccess shouldBe false
        P.artifactTargetValid(F.artifact.copy(participant = "crossed-participant")) shouldBe false
        P.artifactTargetValid(F.artifact.copy(candidateRecordRevision = "34")) shouldBe false
        P.factValid(F.fact.copy(record = F.fact.record.copy(revision = "35"))) shouldBe false
        H.targetValid(F.host.copy(publishedDependencyAdoptions = Vector(F.fact, F.fact))) shouldBe false
        P.selectBinding(take(P.bindingC(F.artifact.componentInstance, F.artifact, P.artifactChoice,
          F.artifactDirection.rationale, F.artifact.participant, F.artifactDirection.reference))).isRight shouldBe true
      }
      "require the source Human to bind the complete publisher provenance and its actual references" in {
        Given("R1–R2: the concrete source target and separately supplied specification Human")
        val reference = V.WorkflowArtifactReference(F.direction.directive.identity, F.direction.directive.revision)
        val described = V.GoalPhaseDependencySourceRevisionDescriptionResult(F.target.sessionId, "direction-required", P.message,
          F.historical.componentInstance, R.targetRecord(F.target), P.sourceChoices, R.adoptionOperation)
        val input = V.GoalPhaseDependencySourceRevisionAdoptionInput(F.target.sessionId, described.target, P.sourceChoice,
          F.direction.rationale, reference)
        val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
        When("passive preparation sees a claimed result without its recollection and artifact references")
        val refused = NativeDependencySourceRevisionPreparation.prepareC(described, input, reference, execution,
          "participant", "specification-issuer", Instant.parse("2026-11-07T00:00:01Z"))
        val binding = take(R.bindingC(F.historical.componentInstance, F.target, P.sourceChoice, F.direction.rationale,
          "participant", F.direction.directive))
        val crossed = F.withObservation(F.observation.copy(destination = "file:///another-publication-destination"))
        val crossedbinding = take(R.bindingC(F.historical.componentInstance, crossed, P.sourceChoice, F.direction.rationale,
          "participant", F.direction.directive))
        Then("source preparation refuses and a different destination cannot reuse the exact admitted whole binding")
        refused.isSuccess shouldBe false
        crossedbinding should not be binding
        R.selectBinding(binding) shouldBe Right(binding)
        val future = F.withObservation(F.observation.copy(observedAt = "2026-11-08T00:00:00Z"))
        val futuredescription = described.copy(target = R.targetRecord(future))
        val complete = execution.copy(references = Vector(reference,
          V.WorkflowArtifactReference(F.observation.recollectionReference.identity, F.observation.recollectionReference.revision),
          V.WorkflowArtifactReference(F.observation.artifact.identity, F.observation.artifact.revision)))
        NativeDependencySourceRevisionPreparation.prepareC(futuredescription, input.copy(target = futuredescription.target), reference,
          complete, "participant", "specification-issuer", Instant.parse("2026-11-07T00:00:01Z")).isSuccess shouldBe false
      }
    }
  }
}
