package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativePrerequisitePublicationRecoveryPreparation
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhasePrerequisitePublicationHostRecovery as H
import GoalPhasePrerequisitePublicationRecovery as B
import GoalPhaseOrderedProgression as O
import PrimaryPublicationRepairSpecificationFixture as P
import SplitPhaseExplicitPartitionFixture.take

/** All supplied process/host observations here are specification data. These
  * pure examples are not native execution or real consumer Human evidence.
  * @since   Nov.  7, 2026
  * @version Nov.  7, 2026
  */
private[admission] object PublicationHostRecoverySpecificationFixture {
  val output = "[error] java.io.IOException: No space left on device\n[error] (cozyBuildCar) packaging failure\n"
  val failed = P.result().copy(reason = "SPECIFICATION-OWNED packaging capacity failure", process = Some(P.process(output)))
  val target = H.Target(H.schema, "primary-instance", "primary-participant", "primary-session", "12",
    P.sorted(B.controller(P.controller)), P.plan, P.base, P.controller.generation, P.controller.completed,
    GoalPhaseRepairProgress.snapshot(P.controller), "12", P.candidate, ContextReference("accepted-primary-result", "1"),
    Vector(P.target), P.publicationPolicy(Vector(P.target)), P.processPolicy(P.target), failed, Vector.empty,
    Vector.empty, H.Lineage(None, Vector.empty, None, Vector.empty, Vector.empty))
  val reference = ContextReference("specification-human", "1")
  val correction = H.Correction(failed.request.input.requestIdentity, P.program,
    ContextReference("specification-host-correction", "1"), "2026-11-07T00:00:00Z",
    "SPECIFICATION-OWNED capacity restored outside source")
  val direction = H.Direction(target, H.authorize, "SPECIFICATION-OWNED authorize whole-primary successor including partial effects",
    target.participant, reference, Some(correction))
  def publicCorrection(value: H.Correction = correction): V.GoalPhasePublicationHostCorrection =
    V.GoalPhasePublicationHostCorrection(value.failedRequestIdentity, value.programIdentity,
      V.WorkflowArtifactReference(value.observationReference.identity, value.observationReference.revision), value.observedAt, value.summary)
  def input(value: H.Target = target): V.GoalPhasePrerequisitePublicationRecoveryInput =
    V.GoalPhasePrerequisitePublicationRecoveryInput(value.sessionId, GoalPhaseNativeMetadata.record(value.asJson), H.authorize,
      direction.rationale, V.WorkflowArtifactReference(reference.identity, reference.revision), Some(publicCorrection()))
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhasePrerequisitePublicationHostRecoverySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import PublicationHostRecoverySpecificationFixture as F
  private def _eligible(value: LocalPublication.Result): Boolean = H.hostFailure(value, P.target, F.target.sessionId,
    F.target.participant, P.source, F.target.processPolicy)

  "Primary publication host recovery" should {
    "E1 classify only a complete terminated packaging capacity capture at its exact fixed program" in {
      Given("docs/spec/goal-phase-prerequisite-publication-host-recovery.md R1: specification-owned packaging captures")
      val failed = F.failed
      When("the independent classification examines the complete native-shaped streams")
      val eligible = _eligible(failed)
      Then("packaging capacity qualifies and Compile, generation, Test, UNKNOWN and nonterminal captures remain separate")
      eligible shouldBe true
      Vector(P.output, P.generationOutput, "[error] (Test / test) Tests unsuccessful\nNo space left on device\n",
        "[error] (cozyBuildCar) another packaging failure\n", F.output + "[error] (Compile / compileIncremental) Compilation failed\n")
        .foreach(text => _eligible(failed.copy(process = Some(P.process(text)))) shouldBe false)
      Vector(ProcessExecutionTermination.TimedOut, ProcessExecutionTermination.Cancelled,
        ProcessExecutionTermination.NotStarted, ProcessExecutionTermination.TerminationUnconfirmed,
        ProcessExecutionTermination.Exited(0)).foreach(t => _eligible(failed.copy(process = Some(P.process(F.output, t)))) shouldBe false)
      _eligible(failed.copy(status = "uncertain")) shouldBe false
      _eligible(failed.copy(nextPreparation = Some("not-executed-preparation"))) shouldBe false
      _eligible(failed.copy(process = Some(P.process(F.output, truncated = true)))) shouldBe false
      _eligible(failed.copy(process = Some(P.process(F.output, identity = "crossed-program")))) shouldBe false
      _eligible(failed.copy(request = failed.request.copy(participant = "crossed-participant"))) shouldBe false
      _eligible(failed.copy(request = failed.request.copy(source = P.source.copy(tree = "d" * 40)))) shouldBe false
      _eligible(failed.copy(request = failed.request.copy(input = failed.request.input.copy(ownerSessionId = Some("crossed-session"))))) shouldBe false
      val invalidutf8 = WorkflowSbtCapture.decode(P.process(F.output)).toOption.get.copy(stderr =
        ProcessExecutionCapture(Vector(0xc3.toByte), 1L, false))
      _eligible(failed.copy(process = Some(WorkflowSbtCapture.encode(invalidutf8)))) shouldBe false
      GoalPhasePrerequisitePrimaryRepair.compileFailure(failed, P.target, F.target.sessionId,
        F.target.participant, P.source, F.target.processPolicy) shouldBe false
      GoalPhasePrerequisitePrimaryRepair.generationFailure(failed, P.target, F.target.sessionId,
        F.target.participant, P.source, F.target.processPolicy) shouldBe false
    }

    "E2 retain a closed complete target and every controller counter without reinterpreting old targets" in {
      Given("R2: the complete specification controller, candidate and immutable old failure")
      val target = F.target
      When("strict persistence and public Record codecs consume the same target")
      val decoded = target.asJson.as[H.Target]
      val publictarget = H.targetC(GoalPhaseNativeMetadata.record(target.asJson))
      Then("all fields round trip and crossed/unknown data refuses")
      decoded shouldBe Right(target)
      take(publictarget) shouldBe target
      target.controller shouldBe P.sorted(B.controller(P.controller))
      target.repairProgress shouldBe GoalPhaseRepairProgress.snapshot(P.controller)
      target.failed shouldBe F.failed
      target.asJson.mapObject(_.add("unknown", Json.True)).as[H.Target].isLeft shouldBe true
      H.targetValid(target.copy(controller = P.sorted(io.circe.parser.parse(target.controller).toOption.get
        .mapObject(_.remove("repairObservations"))))) shouldBe false
      H.targetValid(target.copy(frontierRevision = "11")) shouldBe false
      H.targetValid(target.copy(repairProgress = target.repairProgress.copy(automaticCount = 1))) shouldBe false
      Vector(B.schema, B.primarySchema, GoalPhasePrerequisitePrimaryRepair.targetSchema,
        GoalPhasePrerequisitePrimaryRepair.generationTargetSchema, "unknown").foreach { schema =>
        target.copy(schemaVersion = schema).asJson.as[H.Target].isLeft shouldBe true
      }
      Vector(target.copy(candidate = target.candidate.copy(candidate = Some(P.snapshot.copy(tree = "d" * 40)))) ,
        target.copy(participant = "crossed"), target.copy(candidateRecordRevision = "13"),
        target.copy(processPolicy = P.processPolicy(P.target.copy(workspace = "crossed-workspace"))),
        target.copy(publicationPolicy = P.publicationPolicy(Vector.empty)),
        target.copy(targets = Vector(P.target.copy(identity = "crossed"))))
        .foreach(value => H.targetValid(value) shouldBe false)
    }

    "E3 bind the exact Human answer and typed correction to the failed request and program" in {
      Given("R3: a separately attributable specification-owned host correction and Human direction")
      val d = F.direction
      When("the existing independent evidence purpose selects the host binding")
      val binding = take(H.bindingC(F.target.componentInstance, F.target, d.choice, d.rationale, d.participant, d.reference, d.hostCorrection))
      Then("the dedicated schema, correction and Human are exact and old purpose dispatch stays closed")
      B.selectBinding(binding) shouldBe Right(binding)
      H.directionValid(d.copy(hostCorrection = None)) shouldBe false
      H.directionValid(d.copy(hostCorrection = Some(F.correction.copy(failedRequestIdentity = "crossed")))) shouldBe false
      H.directionValid(d.copy(hostCorrection = Some(F.correction.copy(programIdentity = "crossed")))) shouldBe false
      H.directionValid(d.copy(hostCorrection = Some(F.correction.copy(observedAt = "yesterday")))) shouldBe false
      H.directionValid(d.copy(hostCorrection = Some(F.correction.copy(observationReference = d.reference)))) shouldBe true
      H.directionValid(d.copy(participant = "claimed-worker")) shouldBe false
      H.directionValid(d.copy(choice = "Defer", hostCorrection = None)) shouldBe true
      H.directionValid(d.copy(rationale = "独立した理由\n" + ("complete reason " * 12000),
        hostCorrection = Some(F.correction.copy(summary = "Capacity observation\nFull independently supplied details")))) shouldBe true
      val unencodable = new String(Array(0xd800.toChar))
      Vector("reason\u0000crossed", unencodable, "   \n").foreach { invalid =>
        H.directionValid(d.copy(rationale = invalid)) shouldBe false
        H.directionValid(d.copy(hostCorrection = Some(F.correction.copy(summary = invalid)))) shouldBe false
      }
      B.selectBinding(binding.mapObject(_.add("claimedWorker", Json.fromString("human")))).isLeft shouldBe true
      H.selectBinding(binding.mapObject(_.add("componentInstance", Json.fromString("crossed")))).isLeft shouldBe true
    }

    "E4 passively prepare only independently supplied Human and correction references" in {
      Given("R3: public input and an independent specification-owned execution attestation")
      val input = F.input()
      val reference = input.reference
      val correctionreference = input.hostCorrection.get.observationReference
      val execution = V.WorkflowExecutionEvidence(Vector(reference, correctionreference), Some(F.target.participant), None, None)
      def _prepare_(value: V.GoalPhasePrerequisitePublicationRecoveryInput = input,
        evidence: V.WorkflowExecutionEvidence = execution) = NativePrerequisitePublicationRecoveryPreparation.prepareC(
        H.description(F.target), value, reference, evidence, F.target.participant, "specification-independent-provider",
        Instant.parse("2026-11-07T00:01:00Z"))
      When("passive preparation assembles the exact existing-purpose binding")
      val prepared = _prepare_()
      Then("both references and the actual participant are required; preparation itself authenticates nobody")
      prepared.isSuccess shouldBe true
      _prepare_(evidence = execution.copy(references = Vector(reference))).isSuccess shouldBe false
      _prepare_(evidence = execution.copy(workerIdentity = Some("claimed-worker"))).isSuccess shouldBe false
      _prepare_(value = input.copy(hostCorrection = None)).isSuccess shouldBe false
      _prepare_(value = input.copy(choice = "Defer", hostCorrection = None)).isSuccess shouldBe true
      _prepare_(value = input.copy(hostCorrection = Some(F.publicCorrection(F.correction.copy(
        observedAt = "2026-11-07T00:02:00Z"))))).isSuccess shouldBe false
      _prepare_(value = input.copy(hostCorrection = Some(F.publicCorrection(F.correction.copy(
        observationReference = F.reference)))), evidence = execution.copy(references = Vector(reference))).isSuccess shouldBe true
      B.recoveryInputC(input).isSuccess shouldBe true
      val legacy = input.copy(target = B.primaryTargetRecord(PrimaryPublicationContinuationSpecificationFixture.artifactTarget),
        choice = "AcceptExistingArtifactsWithUnknownEffects")
      B.recoveryInputC(legacy).isSuccess shouldBe false
      B.recoveryInputC(legacy.copy(hostCorrection = None)).isSuccess shouldBe true
    }

    "E5 admit only the declared optional Action field while preserving exact legacy input" in {
      Given("R3: original five-field input and its separately declared optional host correction")
      val input = F.input()
      val raw = NativeCallerExecutionFixture.json(input.toRecord())
      When("the actual Action decoder sees full, omitted, null, unknown and malformed corrections")
      val accepted = B.recoveryRecordC(input.toRecord())
      Then("the optional field is local to recovery and required fields remain exact")
      accepted.isSuccess shouldBe true
      B.recoveryRecordC(input.copy(hostCorrection = None).toRecord()).isSuccess shouldBe true
      B.validateBusinessTextC(raw.mapObject(_.add("hostCorrection", Json.Null)).noSpaces).isSuccess shouldBe false
      B.recoveryRecordC(GoalPhaseNativeMetadata.record(raw.mapObject(_.add("claimedWorker", Json.True)))).isSuccess shouldBe false
      B.recoveryRecordC(GoalPhaseNativeMetadata.record(raw.mapObject(_.remove("reference")))).isSuccess shouldBe false
      B.recoveryRecordC(GoalPhaseNativeMetadata.record(raw.mapObject(_.add("hostCorrection", Json.obj(
        "failedRequestIdentity" -> F.correction.failedRequestIdentity.asJson))))).isSuccess shouldBe false
    }

    "E6 collect one finite failure successor and never turn another failure into an automatic retry" in {
      Given("R4: an explicit in-memory specification port; its supplied processes are not real native execution")
      final class Memory extends LocalPublication.Port {
        val rows = scala.collection.mutable.Map.empty[String, Json]
        var starts = 0
        var next = P.process(F.output)
        def loadC(key: String) = Consequence.success(rows.get(key))
        def createC(key: String, value: Json): Consequence[Unit] =
          if (rows.contains(key)) Consequence.stateConflict("Immutable duplicate")
          else { rows(key) = value; Consequence.unit }
        def sourceC(target: LocalPublication.Target) = Consequence.success(P.source)
        def executeC(target: LocalPublication.Target) = {
          starts += 1
          val result = WorkflowSbtCapture.decode(next).toOption.get
          Consequence.success(SbtExecutionAttempt.Observed(SbtExecutionResult(SbtTarget(take(
            org.goldenport.cncf.workspace.WorkspaceId.parseC(target.workspace))), SbtOperation.PublishLocal, result)))
        }
        def newIdentity() = "specification-preparation"
      }
      val port = new Memory
      val original = take(LocalPublication.ensurePrerequisiteC(LocalPublicationRuntime.prerequisiteInput(F.target.sessionId,
        F.target.candidate, P.target), P.target, F.target.participant, port))
      val target = F.target.copy(failed = original)
      val successor = LocalPublication.ConfirmedFailureSuccessor(ContextReference(target.sessionId, "13"), original, P.source,
        ContextReference(target.sessionId, target.candidateRecordRevision), target.processPolicy, target.publicationPolicy)
      val permit = H.Permit(target, successor)
      val oldrows = port.rows.toMap
      When("ordinary publication uses one saved Direction and then recollects the identical request")
      val failedsuccessor = take(LocalPublication.ensureConfirmedFailureSuccessorC(H.input(permit), P.target,
        target.participant, successor, port))
      val collected = take(H.collectC(permit, port))
      val repeated = take(LocalPublication.ensureConfirmedFailureSuccessorC(H.input(permit), P.target,
        target.participant, successor, port))
      Then("the new failed attempt stays failed, original rows are unchanged, and the direction has exactly one effect")
      failedsuccessor.status shouldBe "failed"
      collected shouldBe Some(failedsuccessor)
      repeated shouldBe failedsuccessor
      port.starts shouldBe 2
      oldrows.foreach((key, value) => port.rows(key) shouldBe value)
      take(H.latestC(permit, port)) shouldBe (())
      val crossed = permit.copy(successor = successor.copy(direction = ContextReference(target.sessionId, "14")))
      LocalPublication.ensureConfirmedFailureSuccessorC(H.input(crossed), P.target, target.participant,
        crossed.successor, port).isSuccess shouldBe false
      port.starts shouldBe 2
      target.controller shouldBe F.target.controller
      target.repairProgress shouldBe F.target.repairProgress
      target.candidate shouldBe F.target.candidate
    }

    "E6b collect an exact successfully published dependency prefix without executing or weakening it" in {
      Given("R2 R4: a specification-owned ordinary dependency capture and an immutable genuine-shaped publication prefix")
      import PrimaryPublicationContinuationSpecificationFixture as Q
      import DependencyValidationSpecificationFixture as D
      val candidate = Q.candidate
      val initial = take(O.startC(D.plan, D.primaryBase, Some(GoalPhaseRuntimeRepairControl.version)))
      val semantic = initial.next.asInstanceOf[O.Semantic]
      val implemented = take(initial.acceptC(O.SemanticCompleted(semantic.key, "Specification dependency source implemented")))
      val state = take(implemented.acceptC(O.CandidateObserved(candidate.candidate.get)))
      val dependency = Q.dependency
      val primary = Q.primary
      val dependencysource = LocalPublication.Source(D.capture.candidate.head, D.capture.candidate.tree, Vector.empty)
      val primarysource = Q.primarySource
      final class Memory extends LocalPublication.Port {
        val rows = scala.collection.mutable.Map.empty[String, Json]
        var starts = Vector.empty[String]
        var sources = Map(dependency.identity -> dependencysource, primary.identity -> primarysource)
        def loadC(key: String) = Consequence.success(rows.get(key))
        def createC(key: String, value: Json): Consequence[Unit] =
          if (rows.contains(key)) Consequence.stateConflict("Immutable duplicate")
          else { rows(key) = value; Consequence.unit }
        def sourceC(target: LocalPublication.Target) = Consequence.success(sources(target.identity))
        def executeC(target: LocalPublication.Target) = {
          starts = starts :+ target.identity
          val result = WorkflowSbtCapture.decode(if (target == dependency) P.process("", ProcessExecutionTermination.Exited(0))
            else P.process(F.output)).toOption.get
          Consequence.success(SbtExecutionAttempt.Observed(SbtExecutionResult(SbtTarget(take(
            org.goldenport.cncf.workspace.WorkspaceId.parseC(target.workspace))), SbtOperation.PublishLocal, result)))
        }
        def newIdentity() = "specification-preparation"
      }
      val port = new Memory
      val prefix = take(LocalPublication.ensurePrerequisiteC(LocalPublicationRuntime.prerequisiteInput("same-session",
        candidate, dependency), dependency, "participant", port))
      val failed = take(LocalPublication.ensurePrerequisiteC(LocalPublicationRuntime.prerequisiteInput("same-session",
        candidate, primary), primary, "participant", port))
      val target = H.Target(H.schema, "fixture-component", "participant", "same-session", "25", P.sorted(B.controller(state)),
        state.plan, state.base, state.generation, state.completed, GoalPhaseRepairProgress.snapshot(state), "25", candidate,
        ContextReference("specification-accepted-result", "1"), Vector(dependency, primary),
        P.publicationPolicy(Vector(dependency, primary)), P.processPolicy(primary), failed, Vector(prefix), Vector.empty,
        H.Lineage(None, Vector.empty, None, Vector.empty, Vector.empty))
      When("the read-only prefix gate recollects the same retained successes and current sources")
      val accepted = H.checkPublicationsC(target, port)
      Then("no publication runs, UNKNOWN/unnecessary cannot substitute for published, and changed source refuses")
      H.targetValid(target) shouldBe true
      accepted.isSuccess shouldBe true
      H.checkPublicationsC(target, port).isSuccess shouldBe true
      H.targetValid(target.copy(dependencyPrefix = Vector(prefix.copy(status = "uncertain")))) shouldBe false
      H.targetValid(target.copy(dependencyPrefix = Vector(prefix.copy(status = "unnecessary")))) shouldBe false
      port.sources = port.sources.updated(dependency.identity, dependencysource.copy(tree = "0" * 40))
      H.checkPublicationsC(target, port).isSuccess shouldBe false
      port.sources = port.sources.updated(dependency.identity, dependencysource).updated(primary.identity, primarysource.copy(tree = "0" * 40))
      H.checkPublicationsC(target, port).isSuccess shouldBe false
      port.starts shouldBe Vector(dependency.identity, primary.identity)
      take(LocalPublication.collectC(prefix.request.input, dependency, target.participant, port)) shouldBe Some(prefix)
    }
  }
}

/** Reads actual retained public CLI proof in a separately selected fresh JVM.
  * It never initializes the pure specification fixtures, invokes a workflow,
  * or reconstructs a process/result receipt from supplied test data. */
@org.scalatest.DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class GoalPhasePrerequisitePublicationHostRecoveryColdDecodeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cold primary publication host recovery target decoding" should {
    "decode the actual public description before fixture warmup and retain the already executed continuation proof" in {
      Given("R5: caller-selected actual public description and handoff from the retained specification-owned installed episode")
      val descriptionuri = new java.net.URI(sys.props.getOrElse("smworkflow.spec.hostRecoveryDescriptionResource",
        cancel("Select smworkflow.spec.hostRecoveryDescriptionResource as an actual public description file URI")))
      descriptionuri.getScheme shouldBe "file"
      val descriptionpath = java.nio.file.Path.of(descriptionuri)
      descriptionpath.isAbsolute shouldBe true
      val handoffpath = java.nio.file.Path.of(sys.props.getOrElse("smworkflow.spec.hostRecoveryHandoff",
        cancel("Select the absolute retained smworkflow.spec.hostRecoveryHandoff")))
      handoffpath.isAbsolute shouldBe true
      val description = io.circe.parser.parse(java.nio.file.Files.readString(descriptionpath, java.nio.charset.StandardCharsets.UTF_8))
        .fold(error => fail(error.message), identity)
      val handoff = io.circe.parser.parse(java.nio.file.Files.readString(handoffpath, java.nio.charset.StandardCharsets.UTF_8))
        .fold(error => fail(error.message), identity)
      handoff.hcursor.get[String]("schemaVersion").toOption.get shouldBe "specification.host-publication-handoff.v1"
      val publictarget = description.hcursor.downField("target").focus.getOrElse(fail("Public description omitted its actual target"))

      When("the first cold host target decode consumes that actual public Record without UNKNOWN fixture initialization")
      val target = H.targetC(GoalPhaseNativeMetadata.record(publictarget)).fold(error => fail(error.toString), identity)

      Then("the closed target retains its Session, original failed publication and successful dependency prefix")
      target.sessionId shouldBe handoff.hcursor.get[String]("sessionId").toOption.get
      H.targetC(GoalPhaseNativeMetadata.record(target.asJson)).fold(error => fail(error.toString), identity) shouldBe target
      target.asJson.as[H.Target] shouldBe Right(target)
      target.failed shouldBe handoff.hcursor.get[LocalPublication.Result]("failed").toOption.get
      target.dependencyPrefix.size shouldBe 1
      target.dependencyPrefix.head.status shouldBe "published"
      target.dependencyPrefix.head.ready shouldBe true
      val root = java.nio.file.Path.of(handoff.hcursor.get[String]("root").toOption.get)
      root.isAbsolute shouldBe true
      java.nio.file.Files.readString(root.resolve("primary-publish-task-count.txt"), java.nio.charset.StandardCharsets.UTF_8)
        .linesIterator.size shouldBe handoff.hcursor.get[Int]("primaryCount").toOption.get + 1
      java.nio.file.Files.readString(root.resolve("dependency-publish-task-count.txt"), java.nio.charset.StandardCharsets.UTF_8)
        .linesIterator.size shouldBe handoff.hcursor.get[Int]("dependencyCount").toOption.get
      java.nio.file.Files.readString(root.resolve("primary-real-check-passed.txt"), java.nio.charset.StandardCharsets.UTF_8) shouldBe
        "qualified-primary-api\n"
      java.nio.file.Files.readString(java.nio.file.Path.of(handoff.hcursor.get[String]("oldFailedOutput").toOption.get),
        java.nio.charset.StandardCharsets.UTF_8) shouldBe handoff.hcursor.get[String]("oldFailedBytes").toOption.get
      java.nio.file.Files.readString(java.nio.file.Path.of(handoff.hcursor.get[String]("oldRefusalOutput").toOption.get),
        java.nio.charset.StandardCharsets.UTF_8) shouldBe handoff.hcursor.get[String]("oldRefusalBytes").toOption.get
    }
  }
}
