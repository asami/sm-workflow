package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhasePrerequisitePublicationHostRecovery as H
import GoalPhaseCanonicalPublicationProgramCorrection as K
import GoalPhasePublishedDependencySourceAdoption as P
import GoalPhaseDependencySourceRevision as R
import CanonicalPublicationProgramCorrectionSpecificationFixture as C
import PublishedDependencySourceSpecificationFixture as F
import PrimaryPublicationRepairSpecificationFixture as Process
import SplitPhaseExplicitPartitionFixture.take

/** Every process, capture, Direction and Human reference is specification-only
  * data. These pure examples authenticate no native history or real consent.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseCanonicalPublicationHistorySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private lazy val _target = C.historicalTarget
  private def _history(facts: Vector[P.ArtifactFact]): H.Target = _target.copy(publishedDependencyAdoptions = facts)
  private def _clause(value: H.Target, label: String): Unit = {
    H.targetValid(value) shouldBe false
    H.targetFailure(value) shouldBe Some(label)
  }
  private final class Memory extends LocalPublication.Port {
    val rows = scala.collection.mutable.Map.empty[String, Json]
    var starts = Vector.empty[String]
    var next = Process.process()
    var sources = (_target.dependencyPrefix.map(p => p.request.target.identity -> p.request.source) :+
      (_target.failed.request.target.identity -> _target.failed.request.source)).toMap
    def loadC(key: String) = Consequence.success(rows.get(key))
    def createC(key: String, value: Json): Consequence[Unit] =
      if (rows.contains(key)) Consequence.stateConflict("Immutable duplicate")
      else { rows(key) = value; Consequence.unit }
    def sourceC(target: LocalPublication.Target) = Consequence.success(sources(target.identity))
    def executeC(target: LocalPublication.Target) = {
      starts :+= target.identity
      Consequence.success(SbtExecutionAttempt.Observed(SbtExecutionResult(SbtTarget(take(
        org.goldenport.cncf.workspace.WorkspaceId.parseC(target.workspace))), SbtOperation.PublishLocal,
        WorkflowSbtCapture.decode(next).toOption.get)))
    }
    def newIdentity() = "specification-history-preparation-" + starts.size
  }
  private def _retained(port: Memory): H.Target = {
    port.next = Process.process("SPECIFICATION-ONLY dependency published", ProcessExecutionTermination.Exited(0),
      identity = "specification-dependency-publication")
    val dependency = _target.targets.head
    val prefix = take(LocalPublication.ensurePrerequisiteC(LocalPublicationRuntime.prerequisiteInput(_target.sessionId,
      _target.candidate, dependency), dependency, _target.participant, port))
    port.next = Process.process()
    val primary = take(LocalPublication.ensurePrerequisiteC(LocalPublicationRuntime.prerequisiteInput(_target.sessionId,
      _target.candidate, _target.targets.last), _target.targets.last, _target.participant, port))
    _target.copy(dependencyPrefix = Vector(prefix), failed = primary)
  }

  "Canonical publication with historical published facts" should {
    "E15 accept old Plan1 facts at the separate current canonical Plan2 frontier without changing old wire" in {
      Given("docs/spec/goal-phase-prerequisite-publication-host-recovery.md R7: pure old v9 source, artifact and v2 capacity facts")
      val oldwire = F.fact.asJson
      val target = _target
      When("the current capture contains the entire exact old Direction and a different current prerequisite result")
      val valid = H.targetValid(target)
      Then("v4 preserves the literal old facts while old v2 and v3 retain their narrower meanings")
      P.factValid(F.fact) shouldBe true
      R.captureValid(target.candidate.dependencySourceCapture.get) shouldBe true
      valid shouldBe true
      H.targetFailure(target) shouldBe None
      F.historical.plan.revision shouldBe "1"
      target.plan.revision shouldBe "2"
      target.candidate.sourceRevision should not be F.candidate.sourceRevision
      target.generation should not be F.host.generation
      target.dependencyPrefix.head.request.input should not be F.old.request.input
      target.dependencyPrefix.head.request.source should not be F.old.request.source
      target.publishedDependencyAdoptions.map(_.asJson) shouldBe Vector(oldwire)
      H.targetValid(target.copy(schemaVersion = H.schemaV2, canonicalProgramCorrection = None)) shouldBe false
      H.targetValid(target.copy(schemaVersion = H.schemaV3)) shouldBe false
      H.targetValid(F.host) shouldBe true
      F.fact.asJson shouldBe oldwire
    }

    "E16 round trip only the closed v4 historical field and canonical correction through public records" in {
      Given("R7: a valid v4 target with historical facts and current readiness kept distinct")
      val target = _target
      val raw = target.asJson
      When("the literal typed wire and public Record conversion are decoded")
      val decoded = raw.as[H.Target]
      val publictarget = take(H.targetC(GoalPhaseNativeMetadata.record(raw)))
      Then("unknown null aliases and schema mixtures refuse without upgrading older facts")
      decoded shouldBe Right(target)
      publictarget shouldBe target
      raw.hcursor.downField("publishedDependencyAdoptions").focus shouldBe None
      raw.hcursor.get[Vector[P.ArtifactFact]]("historicalPublishedDependencyAdoptions").toOption shouldBe Some(Vector(F.fact))
      Vector(raw.mapObject(_.remove("historicalPublishedDependencyAdoptions")),
        raw.mapObject(_.add("historicalPublishedDependencyAdoptions", Json.Null)),
        raw.mapObject(_.add("historicalPublishedDependencyAdoptions", Json.arr())),
        raw.mapObject(_.add("canonicalProgramCorrection", Json.Null)),
        raw.mapObject(_.add("unknown", Json.True)), raw.mapObject(_.add("publishedDependencyAdoptions", Vector(F.fact).asJson)),
        raw.mapObject(_.add("schemaVersion", H.schemaV3.asJson)), raw.mapObject(_.add("schemaVersion", H.schemaV2.asJson)))
        .foreach(_.as[H.Target].isLeft shouldBe true)
      val aliases = raw.mapObject(_.add("historical-published-dependency-adoptions", Vector(F.fact).asJson))
      H.targetC(GoalPhaseNativeMetadata.record(aliases)).isSuccess shouldBe false
    }

    "E17 reject crossed old facts references future order and missing exact typed ancestry with named clauses" in {
      Given("R7: the old typed Direction is linked only through the current canonical origin")
      val fact = F.fact
      val crossed = F.withFailure(F.failed.copy(reason = "SPECIFICATION-ONLY distinct old failure")).publishedDependencyAdoptions
      val actor = fact.copy(direction = fact.direction.copy(participant = "other-actor"))
      val future = fact.copy(direction = fact.direction.copy(target = fact.direction.target.copy(frontierRevision = "89")),
        record = ContextReference(_target.sessionId, "90"))
      val nextfact = fact.copy(direction = fact.direction.copy(target = fact.direction.target.copy(frontierRevision = "34")),
        record = ContextReference(_target.sessionId, "35"))
      val oldcapture = fact.direction.target.candidate.dependencySourceCapture.get
      val unsupported = fact.copy(direction = fact.direction.copy(target = fact.direction.target.copy(candidate =
        fact.direction.target.candidate.copy(dependencySourceCapture = Some(oldcapture.copy(schemaVersion = R.schema))))))
      val reference = fact.copy(direction = fact.direction.copy(target = fact.direction.target.copy(
        sourceDirection = ContextReference(_target.sessionId, "27"))))
      When("historical facts are crossed, unlinked, reordered or detached from their actual capture")
      val invalid = Vector(crossed, Vector(actor), Vector(future), Vector(reference), Vector(unsupported),
        Vector(fact, fact), Vector(nextfact, fact), Vector.empty)
      Then("each fails the historical-adoption relation rather than treating old artifact readiness as current")
      invalid.foreach(facts => _clause(_history(facts), "historical-adoption.ancestry"))
      P.factValid(future) shouldBe true
      P.factValid(nextfact) shouldBe true
      val capture = _target.candidate.dependencySourceCapture.get
      K.hasHistoricalAncestor(capture, F.direction) shouldBe true
      K.hasHistoricalAncestor(capture, crossed.head.direction.target.candidate.dependencySourceCapture.get.direction) shouldBe false
      K.hasHistoricalAncestor(F.capture, F.direction) shouldBe false
      _clause(_target.copy(dependencyPrefix = Vector(F.old)), "dependency-prefix.targets")
      _clause(_target.copy(dependencyPrefix = Vector.empty), "dependency-prefix.targets")
      val prefix = _target.dependencyPrefix.head
      _clause(_target.copy(dependencyPrefix = Vector(prefix.copy(status = "uncertain"))), "dependency-prefix.completed-result")
      _clause(_target.copy(dependencyPrefix = Vector(prefix.copy(request = prefix.request.copy(input = F.old.request.input)))),
        "dependency-prefix.current-request")
    }

    "E18 reject nonterminal success successors and extra policy changes while requiring independent evidence" in {
      Given("R3,R7: the exact current first primary failure and its separate correction observation")
      val target = _target
      val correction = target.canonicalProgramCorrection.get
      val answer = C.answer.copy(target = target, participant = target.participant,
        hostCorrection = Some(C.hostcorrection.copy(failedRequestIdentity = target.failed.request.input.requestIdentity)))
      When("a failure or policy is substituted, or the answer omits its independent host observation")
      val invalid = Vector(ProcessExecutionTermination.TerminationUnconfirmed, ProcessExecutionTermination.NotStarted,
        ProcessExecutionTermination.TimedOut, ProcessExecutionTermination.Cancelled, ProcessExecutionTermination.Exited(0))
      Then("only the complete exact first failure can bind the existing one-successor Human contract")
      invalid.foreach(termination => _clause(target.copy(failed = target.failed.copy(process = Some(Process.process(
        termination = termination)))), "primary.formal-classification"))
      _clause(target.copy(failed = target.failed.copy(status = "uncertain")), "primary.formal-classification")
      _clause(target.copy(failed = F.failed), "publication-targets")
      _clause(target.copy(failed = target.failed.copy(request = target.failed.request.copy(input =
        target.failed.request.input.copy(requestIdentity = target.failed.request.input.requestIdentity + ":host:old:1")))),
        "primary.formal-classification")
      val extra = correction.correctedProcessPolicy.replace("literal", "changed")
      _clause(target.copy(processPolicy = extra, canonicalProgramCorrection = Some(correction.copy(correctedProcessPolicy = extra))),
        "primary.formal-classification")
      H.directionValid(answer) shouldBe true
      H.directionValid(answer.copy(hostCorrection = None)) shouldBe false
      H.directionValid(answer.copy(choice = "Defer", hostCorrection = None)) shouldBe true
      val binding = take(H.bindingC(target.componentInstance, target, answer.choice, answer.rationale,
        answer.participant, answer.reference, answer.hostCorrection))
      H.selectBinding(binding) shouldBe Right(binding)
    }

    "E19 collect only the current prefix and retain exactly one primary successor with all old facts" in {
      Given("R4,R7: immutable in-memory current prerequisite results alongside literal old artifact facts")
      val port = new Memory
      val target = _retained(port)
      val rows = port.rows.toMap
      val starts = port.starts
      When("current readiness is collected and one ordinary directed primary successor is ensured twice")
      take(H.checkPublicationsC(target, port))
      val successor = LocalPublication.ConfirmedFailureSuccessor(ContextReference(target.sessionId, "45"), target.failed,
        target.failed.request.source, ContextReference(target.sessionId, target.candidateRecordRevision), target.processPolicy, target.publicationPolicy)
      val permit = H.Permit(target, successor)
      port.next = Process.process("SPECIFICATION-ONLY corrected primary", ProcessExecutionTermination.Exited(0))
      val published = take(LocalPublication.ensureConfirmedFailureSuccessorC(H.input(permit), target.targets.last,
        target.participant, successor, port))
      val repeated = take(LocalPublication.ensureConfirmedFailureSuccessorC(H.input(permit), target.targets.last,
        target.participant, successor, port))
      Then("one primary executes, dependencies never replay, and old facts policies and accounting remain literal")
      H.targetValid(target) shouldBe true
      published.status shouldBe "published"
      repeated shouldBe published
      take(H.collectC(permit, port)) shouldBe Some(published)
      port.starts shouldBe starts :+ target.targets.last.identity
      rows.foreach((key, value) => port.rows(key) shouldBe value)
      target.publishedDependencyAdoptions shouldBe Vector(F.fact)
      target.repairProgress shouldBe _target.repairProgress
      target.plan shouldBe _target.plan
      target.base shouldBe _target.base
    }

    "E20 refuse a missing immutable current result stale latest or changed current source without ensuring anything" in {
      Given("R7: current prerequisite collection and latest publication belong to the current namespace")
      val missing = new Memory
      val port = new Memory
      val target = _retained(port)
      val starts = port.starts
      When("the immutable result is missing or a later result supersedes the exact completed prefix")
      val absent = H.checkPublicationsC(target, missing)
      val dependency = target.targets.head
      port.next = Process.process("SPECIFICATION-ONLY later dependency", ProcessExecutionTermination.Exited(0),
        identity = "specification-dependency-publication")
      take(LocalPublication.ensureC(LocalPublication.Input("specification-later-request", dependency.identity,
        Some(target.sessionId), true, None), dependency, target.participant, port))
      val laterstarts = port.starts
      val stale = H.checkPublicationsC(target, port)
      val sourceport = new Memory
      val sourcetarget = _retained(sourceport)
      sourceport.sources = sourceport.sources.updated(dependency.identity, F.old.request.source)
      val changed = H.checkPublicationsC(sourcetarget, sourceport)
      Then("the exact failed read has a useful clause and no recovery read starts a process")
      absent.map(_ => "unexpected success").fold(_.toString, result => result) should include("clause=dependency-prefix.immutable-result")
      stale.map(_ => "unexpected success").fold(_.toString, result => result) should include("clause=dependency-prefix.latest-result")
      changed.map(_ => "unexpected success").fold(_.toString, result => result) should include("clause=dependency-prefix.current-source")
      missing.starts shouldBe empty
      port.starts shouldBe laterstarts
      sourceport.starts shouldBe starts
    }
  }
}
