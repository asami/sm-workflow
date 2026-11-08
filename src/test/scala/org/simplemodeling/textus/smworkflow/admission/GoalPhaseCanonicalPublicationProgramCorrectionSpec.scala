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
import GoalPhaseCanonicalPublicationProgramCorrection as K
import GoalPhasePrerequisitePublicationHostRecovery as H
import GoalPhaseDependencySourceRevision as R
import GoalPhaseOrderedProgression as O
import PrimaryPublicationRepairSpecificationFixture as P
import CanonicalDependencySourceSpecificationFixture as C
import PublicationHostRecoverySpecificationFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** All Directions, observations, captures and Human answers below are supplied
  * specification-only data, not native history or actual execution evidence.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object CanonicalPublicationProgramCorrectionSpecificationFixture {
  val key = "SPECIFICATION_SELECTED_SOURCE"
  val primary = P.target.copy(workspace = C.consumption.primaryWorkspace)
  val oldroot = C.original.target.binding.root
  val oldpolicy = {
    val policy = io.circe.parser.parse(C.processPolicy).toOption.get
    val plan = policy.hcursor.get[Json]("processPlan").toOption.get
    val checks = plan.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.map { program =>
      if (program.hcursor.get[String]("target").contains(C.consumption.primaryWorkspace))
        program.mapObject(_.add("environment", Json.obj(key -> C.binding.root.asJson))) else program
    }
    val publisher = Json.obj("identity" -> P.program.asJson, "target" -> primary.workspace.asJson,
      "operation" -> "publish-local".asJson, "executable" -> "/specification/bin/sbt".asJson,
      "capability" -> "specification-publish".asJson,
      "limits" -> Json.obj("executionTimeoutMillis" -> 60000.asJson),
      "environment" -> Json.obj(key -> oldroot.asJson, "PRESERVED" -> "literal".asJson))
    P.sorted(policy.mapObject(_.add("processPlan", plan.mapObject(_.add("sbtPrograms", (checks :+ publisher).asJson)))))
  }
  val direction = C.direction.copy(target = C.target.copy(canonicalOrigin = Some(C.origin.copy(processPolicy = oldpolicy))))
  val capture = C.capture.copy(direction = direction)
  val selected = take(C.controller.adoptCanonicalDependencySourceC(direction.directive))
  val completed = take(selected.acceptC(O.SemanticCompleted(selected.next.asInstanceOf[O.Semantic].key,
    "SPECIFICATION-ONLY current canonical implementation")))
  val snapshot = O.Snapshot(completed.tipSnapshot.head, "6" * 40)
  val controller = take(completed.acceptC(O.CandidateObserved(snapshot)))
  val path = controller.plan.steps(controller.cursor).ownedPaths.head
  val candidate = P.candidate.copy(sourceRevision = capture.sourceRevision, step = capture.step,
    changedPaths = Vector(path), candidate = Some(snapshot), dependencySourceDirection = Some(direction),
    dependencySourceGuard = Some(capture.preStageGuard), dependencySourceCapture = Some(capture))
  val source = P.source.copy(head = snapshot.head, tree = snapshot.tree)
  val correction = K.derive(candidate, primary).get
  val failed = P.result(candidate, primary, source).copy(request = P.result(candidate, primary, source).request.copy(
    input = K.firstInput(capture.sessionId, candidate, primary), participant = direction.participant))
  val target = F.target.copy(schemaVersion = H.schemaV3, componentInstance = C.origin.componentInstance,
    participant = direction.participant, sessionId = capture.sessionId, frontierRevision = "40",
    controller = P.sorted(GoalPhasePrerequisitePublicationRecovery.controller(controller)), plan = controller.plan,
    base = controller.base, generation = controller.generation, completed = controller.completed,
    repairProgress = GoalPhaseRepairProgress.snapshot(controller), candidateRecordRevision = "40", candidate = candidate,
    targets = Vector(primary), publicationPolicy = P.publicationPolicy(Vector(primary)),
    processPolicy = correction.correctedProcessPolicy, failed = failed, canonicalProgramCorrection = Some(correction))
  val hostcorrection = F.correction.copy(failedRequestIdentity = failed.request.input.requestIdentity)
  val answer = F.direction.copy(target = target, participant = target.participant, hostCorrection = Some(hostcorrection))

  /** Pure factory only: old Plan1 published facts followed by a separately
    * selected canonical Plan2 capture. No native history is asserted here. */
  def historicalTarget: H.Target = {
    val published = PublishedDependencySourceSpecificationFixture
    val repaired = take(published.adoptedController.acceptC(O.RepairableChecksObserved(
      published.adoptedController.candidate.get, Vector("primary-test"), "specification-old-check-failure",
      O.RepairDirective("SPECIFICATION-ONLY actual-shaped check failure", Vector("repair current input")))))
    val decision = take(repaired.acceptC(O.NativeFailure("SPECIFICATION-ONLY current inventory amendment")))
    val plan = repaired.plan.copy(revision = "2", steps = repaired.plan.steps.updated(0,
      repaired.plan.steps.head.copy(ownedPaths = repaired.plan.steps.head.ownedPaths :+ "current-history.scala")))
    val amended = take(decision.adoptCurrentInventoryRevisionC(plan, "specification-inventory-human"))
    val binding = published.target.binding.copy(revision = published.target.binding.revision + 1,
      root = C.binding.root, branch = C.binding.branch, base = C.binding.base,
      dependency = published.target.binding.dependency.copy(workspace = C.binding.dependency.workspace))
    val consumption = C.consumption.copy(nonInputExclusions = published.proposal.excludedPaths,
      expectedModes = binding.paths.map(R.ExpectedMode(_, "100644")))
    val source = published.proposal.copy(mode = "committed", binding = ContextReference(binding.identity, binding.revision.toString),
      consumption = consumption)
    val proposal = C.proposal.copy(source = source)
    val guard = C.guard.copy(committedInventory = Some(R.CommittedInventory(
      binding.paths.map(p => R.CommittedEntry(p, Some(R.CommittedObject("100644", "7" * 40)))),
      source.excludedPaths.map(R.CommittedEntry(_, None)))))
    val policy = io.circe.parser.parse(oldpolicy).toOption.get
    val processplan = policy.hcursor.get[Json]("processPlan").toOption.get
    val programs = processplan.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.map(program =>
      if (program.hcursor.get[String]("identity").contains(P.program)) program.mapObject(fields =>
        fields.add("environment", fields("environment").get.mapObject(_.add(key, published.target.binding.root.asJson)))) else program)
    val oldprocess = P.sorted(policy.mapObject(_.add("processPlan", processplan.mapObject(_.add("sbtPrograms", programs.asJson)))))
    val origin = C.origin.copy(componentInstance = published.historical.componentInstance,
      controller = GoalPhaseCandidateRecovery.controllerSnapshot(amended), approvals = published.target.predecessor.approvals,
      originalDirection = Some(published.direction), previousDirection = None, selection = proposal, processPolicy = oldprocess,
      baseLineage = Vector(O.HistoryCommit(binding.base.head, binding.base.tree, Vector(published.target.binding.base.head)),
        O.HistoryCommit(published.target.binding.base.head, published.target.binding.base.tree, Vector.empty)))
    val sourcetarget = published.target.copy(schemaVersion = R.canonicalSchema, sourceRevision = "40", pendingRevision = "40",
      plan = plan, generation = amended.generation, repairProgress = GoalPhaseRepairProgress.snapshot(amended),
      proposal = source, binding = binding, preStageGuard = guard, publishedOrigin = None, canonicalOrigin = Some(origin))
    val direction = C.direction.copy(target = sourcetarget)
    val selected = take(amended.adoptCanonicalDependencySourceC(direction.directive))
    val implemented = take(selected.acceptC(O.SemanticCompleted(selected.next.asInstanceOf[O.Semantic].key,
      "SPECIFICATION-ONLY canonical history repair")))
    val snapshot = O.Snapshot(implemented.tipSnapshot.head, "6" * 40)
    val controller = take(implemented.acceptC(O.CandidateObserved(snapshot)))
    val capture = C.capture.copy(plan = ContextReference(plan.identity, plan.revision), step = sourcetarget.step,
      generation = selected.generation, sourceRevision = "41", direction = direction, preStageGuard = guard, postStageGuard = guard)
    val candidate = published.candidate.copy(sourceRevision = capture.sourceRevision, candidate = Some(snapshot),
      changedPaths = Vector(plan.steps.head.ownedPaths.head), dependencySourceDirection = Some(direction),
      dependencySourceGuard = Some(capture.preStageGuard), dependencySourceStages = Vector.empty,
      dependencySourceCapture = Some(capture))
    val correction = K.derive(candidate, primary).get
    val failed = P.result(candidate, primary, P.source.copy(head = snapshot.head, tree = snapshot.tree))
    val dependency = published.old.request.target.copy(workspace = C.binding.dependency.workspace)
    val prefix = published.old.copy(request = published.old.request.copy(target = dependency,
      input = K.firstInput(capture.sessionId, candidate, dependency),
      source = LocalPublication.Source(C.binding.base.head, C.binding.base.tree, Vector.empty)))
    published.host.copy(schemaVersion = H.schemaV4, frontierRevision = "44", candidateRecordRevision = "43",
      controller = P.sorted(GoalPhasePrerequisitePublicationRecovery.controller(controller)), plan = plan,
      generation = controller.generation, repairProgress = GoalPhaseRepairProgress.snapshot(controller), candidate = candidate,
      targets = Vector(dependency, primary), publicationPolicy = P.publicationPolicy(Vector(dependency, primary)),
      processPolicy = correction.correctedProcessPolicy, failed = failed.copy(request = failed.request.copy(
        input = K.firstInput(capture.sessionId, candidate, primary), participant = direction.participant)),
      dependencyPrefix = Vector(prefix), canonicalProgramCorrection = Some(correction))
  }
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseCanonicalPublicationProgramCorrectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "LSR-01 Versioned canonical publisher consumers" should {
    "derive the same exact publisher correction from a v12 later-Step capture" in {
      Given("a later-Step v11 source direction with genuine-shaped publication provenance and an earlier accepted source")
      import LaterSourcePublicationSpecificationFixture as L
      val root = "/specification/later-canonical-source"
      val binding = L.binding.copy(root = root)
      val consumption = L.consumption.copy(sourceRoot = root)
      val publication = L.publication.copy(publisherProcessPolicy = P.sorted(Json.obj("processPlan" -> Json.obj(
        "sbtPrograms" -> Json.arr(Json.obj("target" -> C.publisherWorkspace.asJson,
          "operation" -> "publish-local".asJson, "identity" -> "specification-dependency-publication".asJson)),
        "workspaces" -> Json.arr(Json.obj("identity" -> C.publisherWorkspace.asJson, "root" -> root.asJson))))))
      val policy = io.circe.parser.parse(L.processPolicy).toOption.get
      val plan = policy.hcursor.get[Json]("processPlan").toOption.get
      val programs = plan.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.map { program =>
        if (program.hcursor.get[String]("identity").contains("specification-later-primary"))
          program.mapObject(_.add("environment", Json.obj("SELECTED_SOURCE" -> root.asJson)))
        else if (program.hcursor.get[String]("identity").contains(P.program))
          program.mapObject(_.add("environment", Json.obj("SELECTED_SOURCE" -> L.binding.root.asJson)))
        else program
      }
      val workspaces = plan.hcursor.get[Vector[Json]]("workspaces").toOption.get.map { workspace =>
        if (workspace.hcursor.get[String]("identity").contains(binding.dependency.workspace))
          workspace.mapObject(_.add("root", root.asJson)) else workspace
      }
      val process = P.sorted(policy.mapObject(_.add("processPlan", plan.mapObject(
        _.add("sbtPrograms", programs.asJson).add("workspaces", workspaces.asJson)))))
      val proposal = L.proposal.copy(source = L.proposal.source.copy(consumption = consumption), publication = publication)
      val origin = L.origin.copy(selection = proposal, processPolicy = process)
      val target = L.target.copy(binding = binding, proposal = proposal.source, preStageGuard = L.guard.copy(root = root),
        canonicalOrigin = Some(origin))
      val direction = L.direction.copy(target = target)
      val capture = L.capture.copy(direction = direction, preStageGuard = target.preStageGuard, postStageGuard = target.preStageGuard)
      val candidate = L.candidate.copy(dependencySourceDirection = Some(direction), dependencySourceGuard = Some(target.preStageGuard),
        dependencySourceCapture = Some(capture))
      When("the publisher correction consumes the new canonical capture selector")
      val correction = K.derive(candidate, L.primary)
      Then("only the exact old-root publisher selector changes and crossed capture versions still refuse")
      R.captureValid(capture) shouldBe true
      correction should not be empty
      correction.get.previousRoot shouldBe L.binding.root
      correction.get.sourceRoot shouldBe root
      K.matches(correction.get, candidate, L.primary) shouldBe true
      K.derive(candidate.copy(dependencySourceCapture = Some(capture.copy(schemaVersion = R.canonicalCaptureSchema))), L.primary) shouldBe None
      K.matches(correction.get.copy(sourceCapture = ContextReference("foreign", capture.sourceRevision)), candidate, L.primary) shouldBe false
    }
  }
  import CanonicalPublicationProgramCorrectionSpecificationFixture as S
  private def _derive(candidate: GoalPhaseNativeCandidate.Payload = S.candidate): Option[K.Correction] = K.derive(candidate, S.primary)
  private def _policy(candidate: GoalPhaseNativeCandidate.Payload, policy: Json): GoalPhaseNativeCandidate.Payload = {
    val capture = candidate.dependencySourceCapture.get
    val direction = capture.direction
    val transformed = direction.copy(target = direction.target.copy(
      canonicalOrigin = Some(direction.target.canonicalOrigin.get.copy(processPolicy = P.sorted(policy)))))
    candidate.copy(dependencySourceDirection = Some(transformed),
      dependencySourceCapture = Some(capture.copy(direction = transformed)))
  }
  private def _failure(value: LocalPublication.Result): Boolean = K.failure(value, S.primary, S.target.sessionId,
    S.target.participant, S.candidate, S.correction)

  "Canonical primary publisher source correction" should {
    "E8 derive exactly one publisher value from the actual captured previous Direction and selected checks" in {
      Given("docs/spec/goal-phase-prerequisite-publication-host-recovery.md R6: specification-only canonical capture with genuine-shaped old Direction lineage")
      val old = io.circe.parser.parse(S.oldpolicy).toOption.get
      When("only the declared selected primary check routes supply source selectors")
      val correction = _derive()
      Then("the corrected policy differs only at the one publisher source selector value")
      R.captureValid(S.capture) shouldBe true
      correction shouldBe Some(S.correction)
      S.correction.previousRoot shouldBe S.oldroot
      S.correction.sourceRoot shouldBe C.binding.root
      S.correction.sourceEnvironmentKey shouldBe S.key
      S.correction.oldProcessPolicy shouldBe S.oldpolicy
      val expected = old.mapObject(fields => fields.add("processPlan", fields("processPlan").get.mapObject(plan =>
        plan.add("sbtPrograms", Json.fromValues(plan("sbtPrograms").get.asArray.get.map { program =>
          if (program.hcursor.get[String]("identity").contains(P.program)) program.mapObject(fields =>
            fields.add("environment", fields("environment").get.mapObject(_.add(S.key, C.binding.root.asJson)))) else program
        })))))
      S.correction.correctedProcessPolicy shouldBe P.sorted(expected)
      S.correction.previousDirectionDirective shouldBe C.original.directive
      S.correction.sourceCapture shouldBe ContextReference(S.capture.sessionId, S.capture.sourceRevision)
      S.correction.binding shouldBe ContextReference(C.binding.identity, C.binding.revision.toString)
      val origin = S.direction.target.canonicalOrigin.get
      val missing = S.capture.copy(direction = S.direction.copy(target = S.direction.target.copy(canonicalOrigin =
        Some(origin.copy(originalDirection = None, previousDirection = None)))))
      _derive(S.candidate.copy(dependencySourceCapture = Some(missing))) shouldBe None
      _derive(S.candidate.copy(dependencySourceCapture = None)) shouldBe None
    }

    "E9 reject ambiguous missing blank crossed and unrelated source selectors without policy overrides" in {
      Given("R6: a single declared source selector in each selected primary check and the publisher")
      val old = io.circe.parser.parse(S.oldpolicy).toOption.get
      val plan = old.hcursor.get[Json]("processPlan").toOption.get
      val programs = plan.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get
      def _changed_(edit: Json => Json): GoalPhaseNativeCandidate.Payload = _policy(S.candidate,
        old.mapObject(_.add("processPlan", plan.mapObject(_.add("sbtPrograms", programs.map(edit).asJson)))))
      When("a missing or second selector destroys the unique old-to-current mapping")
      val missing = _changed_(program => if (program.hcursor.get[String]("identity").contains(P.program))
        program.mapObject(_.add("environment", Json.obj("PRESERVED" -> "literal".asJson))) else program)
      val ambiguous = _changed_(program => program.hcursor.get[Json]("environment").toOption.fold(program)(environment =>
        program.mapObject(_.add("environment", environment.mapObject(_.add("SECOND_SELECTOR",
          (if (program.hcursor.get[String]("identity").contains(P.program)) S.oldroot else C.binding.root).asJson))))))
      Then("neither arbitrary environment changes nor another command can be authorized by this correction")
      _derive(missing) shouldBe None
      _derive(ambiguous) shouldBe None
      val publisher = programs.last
      Vector("", " ", "/unrelated/root").foreach { root =>
        _derive(_changed_(p => if (p == publisher) p.mapObject(_.add("environment", Json.obj(S.key -> root.asJson))) else p)) shouldBe None
      }
      val corrected = io.circe.parser.parse(S.correction.correctedProcessPolicy).toOption.get
      val correctedplan = corrected.hcursor.get[Json]("processPlan").toOption.get
      val correctedprograms = correctedplan.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get
      Vector("executable" -> "/other/sbt".asJson, "limits" -> Json.obj("executionTimeoutMillis" -> 60001.asJson),
        "target" -> "other-workspace".asJson, "identity" -> "other-program".asJson,
        "environment" -> Json.obj(S.key -> C.binding.root.asJson, "PRESERVED" -> "changed".asJson)).foreach { (key, value) =>
        val changed = corrected.mapObject(_.add("processPlan", correctedplan.mapObject(_.add("sbtPrograms",
          correctedprograms.updated(correctedprograms.size - 1, correctedprograms.last.mapObject(_.add(key, value))).asJson))))
        K.valid(S.correction.copy(correctedProcessPolicy = P.sorted(changed))) shouldBe false
      }
      Vector("validation" -> Json.obj("checks" -> Json.arr()), "maximumDiffBytes" -> 1.asJson).foreach { (key, value) =>
        K.valid(S.correction.copy(correctedProcessPolicy = P.sorted(corrected.mapObject(_.add(key, value))))) shouldBe false
      }
      Vector("grants", "workspaceGrants", "workspaces").foreach { key =>
        val changed = corrected.mapObject(_.add("processPlan", correctedplan.mapObject(_.add(key, Json.arr()))))
        K.valid(S.correction.copy(correctedProcessPolicy = P.sorted(changed))) shouldBe false
      }
      val changedcheck = correctedprograms.head.mapObject(_.add("environment", Json.obj(S.key -> S.oldroot.asJson)))
      val changedchecks = corrected.mapObject(_.add("processPlan", correctedplan.mapObject(_.add("sbtPrograms",
        correctedprograms.updated(0, changedcheck).asJson))))
      K.valid(S.correction.copy(correctedProcessPolicy = P.sorted(changedchecks))) shouldBe false
      K.valid(S.correction.copy(sourceEnvironmentKey = "PRESERVED")) shouldBe false
      K.matches(S.correction.copy(previousBinding = ContextReference("crossed", "1")), S.candidate, S.primary) shouldBe false
      K.matches(S.correction.copy(sourceDirectionDirective = ContextReference("crossed", "1")), S.candidate, S.primary) shouldBe false
    }

    "E10 preserve strict v3 public and durable wire while leaving v1 and v2 encodings literal" in {
      Given("R6: the closed v3 target and the unchanged legacy v1 specification target")
      val target = S.target
      val legacy = F.target.asJson
      When("the exact target is encoded and projected through the public Record conversion")
      val decoded = target.asJson.as[H.Target]
      val publictarget = H.targetC(GoalPhaseNativeMetadata.record(target.asJson))
      Then("the correction belongs exclusively to v3 and every actual outer identity stays exact")
      decoded shouldBe Right(target)
      take(publictarget) shouldBe target
      F.target.asJson shouldBe legacy
      legacy.hcursor.downField("canonicalProgramCorrection").focus shouldBe None
      Vector(target.asJson.mapObject(_.add("canonicalProgramCorrection", Json.Null)),
        target.asJson.mapObject(_.remove("canonicalProgramCorrection")),
        target.asJson.mapObject(_.add("publishedDependencyAdoptions", Json.arr())),
        target.asJson.mapObject(_.add("unknown", Json.True)),
        target.asJson.mapObject(_.add("schemaVersion", H.schema.asJson)),
        target.asJson.mapObject(_.add("schemaVersion", H.schemaV2.asJson))).foreach(_.as[H.Target].isLeft shouldBe true)
      Vector(target.copy(processPolicy = S.oldpolicy), target.copy(sessionId = "crossed"),
        target.copy(candidateRecordRevision = "41"), target.copy(targets = Vector(S.primary.copy(workspace = "crossed"))),
        target.copy(canonicalProgramCorrection = Some(S.correction.copy(sourceRoot = "/crossed"))))
        .foreach(value => H.targetValid(value) shouldBe false)
      val record = S.correction.asJson
      record.mapObject(_.add("unknown", Json.True)).as[K.Correction].isLeft shouldBe true
      record.mapObject(_.add("sourceEnvironmentKey", Json.Null)).as[K.Correction].isLeft shouldBe true
    }

    "E11 qualify only the first complete actual nonzero fixed primary prerequisite result" in {
      Given("R6: specification-only native-shaped first prerequisite and complete streams")
      val failed = S.failed
      When("terminal classification is bound to the exact candidate Session participant program and source")
      val qualified = _failure(failed)
      Then("UNKNOWN nonterminal successful malformed and successor outcomes never grant this branch")
      qualified shouldBe true
      Vector(ProcessExecutionTermination.TimedOut, ProcessExecutionTermination.Cancelled, ProcessExecutionTermination.NotStarted,
        ProcessExecutionTermination.TerminationUnconfirmed, ProcessExecutionTermination.Exited(0))
        .foreach(t => _failure(failed.copy(process = Some(P.process(termination = t)))) shouldBe false)
      _failure(failed.copy(status = "uncertain")) shouldBe false
      _failure(failed.copy(nextPreparation = Some("pending"))) shouldBe false
      _failure(failed.copy(process = Some(P.process(truncated = true)))) shouldBe false
      _failure(failed.copy(process = Some(P.process(identity = "crossed")))) shouldBe false
      _failure(failed.copy(request = failed.request.copy(participant = "crossed"))) shouldBe false
      _failure(failed.copy(request = failed.request.copy(source = S.source.copy(tree = "9" * 40)))) shouldBe false
      Vector(":prerequisite:1", ":host:direction:1", ":primary:direction:1").foreach(suffix =>
        _failure(failed.copy(request = failed.request.copy(input = failed.request.input.copy(
          requestIdentity = LocalPublicationRuntime.prerequisiteInput(S.target.sessionId, S.candidate, S.primary).requestIdentity + suffix)))) shouldBe false)
      val invalidutf8 = WorkflowSbtCapture.decode(failed.process.get).toOption.get.copy(stderr = ProcessExecutionCapture(Vector(0xc3.toByte), 1L, false))
      _failure(failed.copy(process = Some(WorkflowSbtCapture.encode(invalidutf8)))) shouldBe false
      _failure(failed.copy(process = None)) shouldBe false
    }

    "E12 bind the whole old-new relation to independent Human evidence and keep Defer passive" in {
      Given("R3,R6: independent specification-only correction observation and Human answer")
      val answer = S.answer
      When("the existing evidence binding contains the complete v3 target and host observation")
      val binding = take(H.bindingC(S.target.componentInstance, S.target, answer.choice, answer.rationale,
        answer.participant, answer.reference, answer.hostCorrection))
      Then("source choice and correction observation alone confer no publication authority")
      H.selectBinding(binding) shouldBe Right(binding)
      binding.hcursor.downField("prerequisitePublicationRecovery").get[H.Target]("target").toOption shouldBe Some(S.target)
      H.directionValid(answer.copy(hostCorrection = None)) shouldBe false
      H.directionValid(answer.copy(choice = "AdoptCanonicalDependencySourceRevision")) shouldBe false
      H.directionValid(answer.copy(choice = "Defer", hostCorrection = None)) shouldBe true
      H.directionValid(answer.copy(hostCorrection = Some(S.hostcorrection.copy(programIdentity = "crossed")))) shouldBe false
      H.selectBinding(binding.mapObject(_.add("authority", Json.True))).isLeft shouldBe true
    }

    "E13 use the existing finite successor owner and collect without replay or accounting changes" in {
      Given("R4,R6: an in-memory specification port with an actual-shaped retained failed prerequisite")
      final class Memory extends LocalPublication.Port {
        val rows = scala.collection.mutable.Map.empty[String, Json]
        var starts = 0
        var next = P.process()
        def loadC(key: String) = Consequence.success(rows.get(key))
        def createC(key: String, value: Json): Consequence[Unit] = if (rows.contains(key))
          Consequence.stateConflict("Immutable duplicate") else { rows(key) = value; Consequence.unit }
        def sourceC(target: LocalPublication.Target) = Consequence.success(S.source)
        def executeC(target: LocalPublication.Target) = {
          starts += 1
          Consequence.success(SbtExecutionAttempt.Observed(SbtExecutionResult(SbtTarget(take(
            org.goldenport.cncf.workspace.WorkspaceId.parseC(target.workspace))), SbtOperation.PublishLocal,
            WorkflowSbtCapture.decode(next).toOption.get)))
        }
        def newIdentity() = "specification-canonical-preparation"
      }
      val port = new Memory
      val failed = take(LocalPublication.ensurePrerequisiteC(LocalPublicationRuntime.prerequisiteInput(S.target.sessionId,
        S.candidate, S.primary), S.primary, S.target.participant, port))
      val target = S.target.copy(failed = failed)
      val successor = LocalPublication.ConfirmedFailureSuccessor(ContextReference(target.sessionId, "41"), failed, S.source,
        ContextReference(target.sessionId, target.candidateRecordRevision), target.processPolicy, target.publicationPolicy)
      val permit = H.Permit(target, successor)
      val oldrows = port.rows.toMap
      port.next = P.process("SPECIFICATION-ONLY successful corrected publication", ProcessExecutionTermination.Exited(0))
      When("ordinary publication ensures the directed successor once and later calls only recollect it")
      val published = take(LocalPublication.ensureConfirmedFailureSuccessorC(H.input(permit), S.primary,
        target.participant, successor, port))
      val collected = take(H.collectC(permit, port))
      val repeated = take(LocalPublication.ensureConfirmedFailureSuccessorC(H.input(permit), S.primary,
        target.participant, successor, port))
      Then("the same owner retains the original failure and only one fresh effect with unchanged Plan and counters")
      published.status shouldBe "published"
      collected shouldBe Some(published)
      repeated shouldBe published
      port.starts shouldBe 2
      oldrows.foreach((key, value) => port.rows(key) shouldBe value)
      _failure(published.copy(status = "failed", process = Some(P.process()))) shouldBe false
      target.plan shouldBe S.target.plan
      target.base shouldBe S.target.base
      target.completed shouldBe S.target.completed
      target.repairProgress shouldBe S.target.repairProgress
      target.lineage shouldBe S.target.lineage
    }
  }
}
