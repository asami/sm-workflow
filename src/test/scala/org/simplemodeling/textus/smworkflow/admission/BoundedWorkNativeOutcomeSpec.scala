/**
 * @since   Oct. 27, 2026
 * @version Nov.  6, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation, SbtOperation}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import BoundedWorkRuntime.*
import StandaloneFixExecution as S
import StandaloneFixExecution.given
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E
import WorkflowSbtValidation as V
import SplitPhaseExplicitPartitionFixture.take

/** Owned SQLite/File/Git qualification. Check/result inputs and retained intent
  * interruptions are specification-owned; they are not real CLI acceptance,
  * independent reviewer evidence, or an observed process timeout.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class BoundedWorkNativeOutcomeSpec extends AnyWordSpec with Matchers with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  private val selected = Vector(GoalPhaseNativeValidation.Check("compile", 1, V.Kind.Compile),
    GoalPhaseNativeValidation.Check("focused", 2, V.Kind.TestOnly))
  private val journal = DevelopmentJournal.Policy("workflow", _collection_("development_journal"))
  private val evidence = CommonEvidence.Policy("workflow", _collection_("common_evidence"),
    "spec-host", "evidence-archive", "logs")
  private def quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
  private def root(): Path = Files.createTempDirectory("smwf-bounded-outcomes-").toRealPath()
  private def native(task: Boolean): BoundedWorkNativeRun = if (task) GoalTaskNativeRun else StandaloneFixNativeRun
  private def definitionC(task: Boolean): Consequence[DefinitionBinding] =
    if (task) GoalTaskRuntimeCatalog.loadC() else StandaloneFixRuntimeCatalog.loadC()
  private def host(directory: Path): Host = Host(_configuration_, _collection_("instances"), _collection_("continuations"),
    "fixture-repository", directory.resolve("repository").toString, "main", Vector("value.txt"), selected,
    None, None, None, Some(evidence), Some(journal))
  private def state(task: Boolean, stage: String): S.State = {
    val input = S.Input("fixture-repository", "fixture-request", "Preserve the selected value", Vector("value.txt"),
      selected.map(_.identity), Vector("behavior"), Vector(ContextReference("fixture-source", "1")),
      task, scalaLint = "off", task = Option.when(task)(S.Task("journal", Vector.empty)))
    val retained = take(S.startC(input, "participant", selected)).copy(stage = stage,
      candidate = Some(S.Candidate("a" * 40, "b" * 40, Vector("value.txt"), "Specification-owned checkpoint candidate")), semanticReference = Some(ContextReference("fixture-semantic", "1")),
      conditionReport = Vector(S.Condition("behavior", true, "Specification-owned source assessment")),
      receipts = selected.map(check => RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(0),
        "Specification-owned previous check observation", "", Some(V.Facts(check.kind, V.Outcome.Succeeded,
          Option.when(check.kind == V.Kind.TestOnly)(V.Selection(Vector("example.BehaviorSpec"), 1, 2L)), None)))))
    stage match {
      case "Completed" => take(S.committedC(retained.copy(stage = "CommitUnknown",
        reviewReference = Some(ContextReference("fixture-independent-review", "1"))), S.Commit("c" * 40, "a" * 40, "b" * 40)))
      case "Accepted" => take(S.assessC(retained.copy(stage = "Assess")))
      case "NoChange" if !task => take(S.assessC(retained.copy(stage = "Assess", semanticReference = None)))
      case "NoChange" => take(S.completedC(S.Work("fixture", "1", "Review",
        retained.copy(stage = "Review", candidate = retained.candidate.map(_.copy(paths = Vector.empty, diff = "")))),
        S.Result("accept", "Specification-owned independent review input", Vector.empty, retained.conditionReport, Vector.empty),
        ContextReference("fixture-independent-review", "1")))
      case _ => retained
    }
  }
  private def receipt(outcome: V.Outcome, exit: Int = 0): RepositorySyncNativeMergeValidation.Receipt =
    RepositorySyncNativeMergeValidation.Receipt("compile", "exited", Some(exit), "fixture native output", "",
      Some(V.Facts(V.Kind.Compile, outcome, None, None)))

  override protected def process_plan(directory: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val plan = TypedGitHostPlanFixture.plan(Programs.Target(directory.resolve("repository"), "main", "origin", "main",
      directory.resolve("upstream.git").toString), goalPaths = Vector("value.txt", "second.txt"),
      goalMessages = Vector("Fixture Task selected commit"), goalSelections = Vector(Vector("value.txt")))
    val git = plan.mapObject(fields => fields.add("gitPrograms", Json.fromValues(fields("gitPrograms").get.asArray.get.map { program =>
      val selection = if (program.hcursor.get[String]("selectionId").toOption.contains("goal-step-1"))
        program.mapObject(_.add("selectionId", Json.fromString("goal-task"))) else program
      selection.mapObject(_.add("gitExecutable", Json.fromString(directory.resolve("observed-git").toString)))
    })))
    val executable = directory.resolve("candidate-check")
    if (Files.exists(executable)) TypedSbtHostPlanFixture.plan(directory, executable, Vector(1),
      gitPlan = Some(git), operation = SbtOperation.Compile) else git
  }
  private def parameters(directory: Path): Map[String, String] = {
    Files.createDirectories(directory.resolve("archive/logs"))
    val values = NativeWorkflowCliFixture.JournalHost(journal).values
    val storage = io.circe.parser.parse(values("textus.subsystem.storage.plan")).toOption.get.mapObject(fields =>
      fields.add("records", Json.fromValues(fields("records").get.asArray.get :+ Json.obj(
        "componentId" -> Json.fromString(SmWorkflowComponent.componentId.name), "storeName" -> Json.fromString("workflow"),
        "collection" -> Json.fromString(evidence.collection.print)))))
    val plan = process_plan(directory, "Outcome", ThreeWorkflowNormalHostFixture.definition(directory).goal).mapObject(fields => fields
      .add("workspaces", Json.fromValues(fields("workspaces").get.asArray.get :+ Json.obj(
        "identity" -> Json.fromString("evidence-archive"), "root" -> Json.fromString(directory.resolve("archive").toString))))
      .add("workspaceGrants", Json.fromValues(fields("workspaceGrants").get.asArray.get :+ Json.obj(
        "componentId" -> Json.fromString(SmWorkflowComponent.componentId.name), "workspaceId" -> Json.fromString("evidence-archive"),
        "access" -> Json.fromString("manage")))))
    values ++ Map("textus.subsystem.storage.plan" -> storage.noSpaces,
      "textus.subsystem.process-execution.plan" -> plan.noSpaces)
  }
  private def prepare(directory: Path): Unit = {
    Files.createDirectories(directory.resolve("repository"))
    val wrapper = "#!/bin/sh\nfor arg in \"$@\"; do\n if [ \"$arg\" = add ]; then\n" +
      "  printf '%s\\n' add >> " + quote(directory.resolve("stage-calls").toString) + "\n fi\n if [ \"$arg\" = commit ]; then\n" +
      "  printf '%s\\n' commit >> " + quote(directory.resolve("commit-calls").toString) + "\n" +
      "  if [ -f " + quote(directory.resolve("fail-commit").toString) + " ]; then exit 1; fi\n" +
      "  if [ -f " + quote(directory.resolve("commit-then-fail").toString) + " ]; then\n" +
      "   /usr/bin/git \"$@\"\n   commit_exit=$?\n   if [ \"$commit_exit\" -eq 0 ]; then exit 1; fi\n   exit \"$commit_exit\"\n  fi\n fi\ndone\nexec /usr/bin/git \"$@\"\n"
    val executable = directory.resolve("observed-git")
    Files.writeString(executable, wrapper)
    executable.toFile.setExecutable(true) shouldBe true
    take(_run_setup_command_(Vector("init", "--initial-branch=main", directory.resolve("repository").toString)))
    Files.writeString(directory.resolve("repository/value.txt"), "baseline\n")
    Files.writeString(directory.resolve("repository/unrelated.txt"), "settled other work\n")
    take(_run_setup_command_(Vector("-C", directory.resolve("repository").toString, "add", "--", "value.txt", "unrelated.txt")))
    take(_run_setup_command_(Vector("-C", directory.resolve("repository").toString, "-c", "user.name=Fixture",
      "-c", "user.email=fixture@example.invalid", "commit", "-m", "Owned fixture baseline")))
  }
  private def candidateC(core: ActionCall.Core, head: String): Consequence[S.Candidate] = for {
    parent <- GitObjectId.parseC(head)
    paths <- E.executeGitC(core, GitOperation.PendingPaths(parent)).flatMap(_.valueC)
    diff <- E.executeGitC(core, GitOperation.PendingDiff(parent)).flatMap(_.valueC)
    tree <- G.indexTreeC(core)
  } yield S.Candidate(head, tree, paths.map(_.value), diff.text)

  private def commitCalls(directory: Path): Long = {
    val file = directory.resolve("commit-calls")
    if (Files.exists(file)) Files.readString(file).linesIterator.size.toLong else 0L
  }
  private def seedC(n: BoundedWorkNativeRun, h: Host, session: String, payload: Payload, core: ActionCall.Core):
    Consequence[(DefinitionBinding, WorkflowComponentStorage, InstanceRecord)] = for {
    definition <- definitionC(n.task)
    storage <- n.storageC(h, definition, core)
    instances <- storage.bindInstancesC()(using core.executionContext)
    initial <- InstanceRecord(InstanceIdentity(session), definition, initialRevision, Lifecycle.NotStarted, None,
      CorrelationReference("fixture"), CausationReference("fixture-start"), None, None, None, Vector.empty).validateC
    _ <- instances.createC(initial)(using core.executionContext)
    active <- instances.appendC(initial.identity, initial.revision, n.history(initial, "fixture-seed"))(using core.executionContext)
    saved <- n.saveC(h, storage, active, payload, core)
  } yield (definition, storage, saved)
  private def currentC(storage: WorkflowComponentStorage, session: String, core: ActionCall.Core): Consequence[InstanceRecord] = for {
    instances <- storage.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(InstanceIdentity(session))(using core.executionContext)
    record <- current.fold[Consequence[InstanceRecord]](Consequence.stateConflict("Expected managed native record"))(Consequence.success)
  } yield record
  private def projectionC(directory: Path, core: ActionCall.Core, reference: ContextReference, outcome: String): Consequence[Json] = for {
    retained <- CommonEvidence.loadedC(CommonEvidence.storage(core, evidence), reference.identity)
    pair <- retained.fold[Consequence[(CommonEvidence.Allocation, Option[CommonEvidence.Observation])]](
      Consequence.stateConflict("Expected managed Evidence allocation"))(Consequence.success)
    (allocation, observation) = pair
    _ = allocation.source.actionOutcome shouldBe outcome
    _ = observation.map(_.status) shouldBe Some("stored")
    bound <- ComponentRecordStorage.bindForCallC(core, journal.storeName, journal.collection,
      DataStore.StringEntryId(DevelopmentJournalEvidence.identity(reference.identity)))
    row <- bound.loadC()
    entry <- row.fold[Consequence[DevelopmentJournalEvidence.Entry]](Consequence.stateConflict("Expected Journal projection"))(
      DevelopmentJournalEvidence.storedC(_, DevelopmentJournalEvidence.identity(reference.identity)))
    _ = entry.metadata.actionOutcome shouldBe outcome
    _ = entry.observation.status shouldBe "stored"
    text = Files.readString(directory.resolve("archive").resolve(allocation.path))
    json <- io.circe.parser.parse(text).fold(error => Consequence.stateConflict(error.message), Consequence.success)
  } yield json

  "Retained Task candidate capture" should {
    "observe complete indexes and finish only remaining staging before the final repair validation once" in {
      for (mode <- Vector("unstaged", "partial", "staged", "deletion")) {
        Given("a specification-owned final accepted repair with interrupted capture and a " + mode + " index")
        val directory = root(); prepare(directory)
        val repository = directory.resolve("repository")
        Files.writeString(repository.resolve("second.txt"), "second baseline\n")
        take(_run_setup_command_(Vector("-C", repository.toString, "add", "--", "second.txt")))
        take(_run_setup_command_(Vector("-C", repository.toString, "-c", "user.name=Fixture",
          "-c", "user.email=fixture@example.invalid", "commit", "-m", "Second owned fixture path")))
        Files.writeString(repository.resolve("value.txt"), "accepted final repair\n")
        if (mode == "deletion") Files.delete(repository.resolve("second.txt"))
        else Files.writeString(repository.resolve("second.txt"), "accepted second repair\n")
        val executable = directory.resolve("candidate-check")
        Files.writeString(executable, "#!/bin/sh\nprintf '%s\\n' compile >> " +
          quote(directory.resolve("check-calls").toString) + "\nprintf '[success] Total time: 0 s\\n'\n")
        executable.toFile.setExecutable(true) shouldBe true
        _with_runtime_(directory, "Outcome", hostParameters = parameters(directory) ++
          BoundedWorkExecutionAdmissionFixture.parameters) { (_, _, _, project) =>
          project(call => for {
            base <- G.observeC(call.core)
            _ <- if (mode == "partial" || mode == "staged" || mode == "deletion")
              E.stageGoalCandidateC(call.core, 1).flatMap(_.valueC).map(_ => ()) else Consequence.unit
            _ <- if (mode == "staged" || mode == "deletion")
              E.stageGoalCandidateC(call.core, 2).flatMap(_.valueC).map(_ => ()) else Consequence.unit
            mapping <- BoundedWorkExecutionAdmissionFixture.mappingC()
            h = host(directory).copy(paths = Vector("value.txt", "second.txt"), checks = selected.take(1),
              profiles = Some(mapping), origin = Some(BoundedWorkExecutionAdmissionFixture.origin))
            original = state(true, "CandidateIntent")
            pending = original.copy(input = original.input.copy(ownedPaths = h.paths, checks = Vector("compile"),
              kind = "documentation", repairLimit = 3), selected = h.checks, candidate = None, receipts = Vector.empty,
              automaticCount = 3, ordinaryCount = 3,
              repairControl = GoalPhaseRuntimeRepairControl.Context(GoalPhaseRuntimeRepairControl.version, 3))
            payload = Payload(pending, h.root, "main", base.head, unrelated = Some(Vector.empty))
            seeded <- seedC(GoalTaskNativeRun, h, "fixture-final-capture", payload, call.core)
            stagedbefore = if (Files.exists(directory.resolve("stage-calls")))
              Files.readString(directory.resolve("stage-calls")).linesIterator.size else 0
            _ = When("ordinary Advance observes the retained capture, without another repair Submit")
            first <- GoalTaskNativeRun.advanceC("fixture-final-capture", h, seeded._1, call.core)
            current <- currentC(seeded._2, "fixture-final-capture", call.core)
            issued <- seeded._2.bindIssuedC(GoalTaskNativeRun.completionOperation, GoalTaskNativeRun.inputCodec)
            work <- issued.loadC(org.goldenport.cncf.workflow.ContinuationIdentity(current.suspension.get.continuationIdentity.value))(using call.core.executionContext)
              .flatMap {
                case Some(value) => Consequence.success(value)
                case None => Consequence.stateConflict("Expected actual retained review")
              }
            retained <- NativeWorkflowOperationOutput.workInputC(work)
            _ = Then("the same Session reaches independent Review after exactly one controlled validation process")
            _ = first.kind shouldBe "Request"
            _ = first.sessionId shouldBe "fixture-final-capture"
            _ = retained.kind shouldBe "Review"
            _ = retained.state.input shouldBe pending.input
            _ = retained.state.semanticReference shouldBe pending.semanticReference
            _ = retained.state.conditionReport shouldBe pending.conditionReport
            _ = retained.state.generation shouldBe pending.generation
            _ = retained.state.automaticCount shouldBe 3
            _ = retained.state.ordinaryCount shouldBe 3
            _ = retained.state.repairControl shouldBe pending.repairControl
            _ = retained.state.candidate.get.paths.toSet shouldBe h.paths.toSet
            _ = retained.state.receipts.map(_.identity) shouldBe Vector("compile")
            _ = current.history.take(seeded._3.history.size) shouldBe seeded._3.history
            stagedafter = Files.readString(directory.resolve("stage-calls")).linesIterator.size
            _ = (stagedafter - stagedbefore) shouldBe (if (mode == "unstaged") 2 else if (mode == "partial") 1 else 0)
            _ = Files.readString(directory.resolve("check-calls")).linesIterator.size shouldBe 1
            _ = When("another Advance collects the retained Review boundary")
            second <- GoalTaskNativeRun.advanceC("fixture-final-capture", h, seeded._1, call.core)
            _ = Then("no repair submission, staging or validation is repeated")
            _ = second.work shouldBe first.work
            _ = Files.readString(directory.resolve("check-calls")).linesIterator.size shouldBe 1
            _ = Files.readString(directory.resolve("stage-calls")).linesIterator.size shouldBe stagedafter
          } yield ())
        }
      }
    }

    "return the actual ownership choice to Human without staging another worker's index" in {
      Given("an interrupted Task capture and an extra staged path outside the adopted ownership")
      val directory = root(); prepare(directory)
      Files.writeString(directory.resolve("repository/value.txt"), "accepted Task repair\n")
      Files.writeString(directory.resolve("repository/unrelated.txt"), "another worker's selected change\n")
      take(_run_setup_command_(Vector("-C", directory.resolve("repository").toString, "add", "--", "unrelated.txt")))
      _with_runtime_(directory, "Outcome", hostParameters = parameters(directory)) { (_, _, _, project) =>
        project(call => for {
          base <- G.observeC(call.core)
          h = host(directory)
          original = state(true, "CandidateIntent")
          pending = original.copy(input = original.input.copy(repairLimit = 3), candidate = None, receipts = Vector.empty,
            automaticCount = 3, ordinaryCount = 3,
            repairControl = GoalPhaseRuntimeRepairControl.Context(GoalPhaseRuntimeRepairControl.version, 3))
          seeded <- seedC(GoalTaskNativeRun, h, "fixture-ownership-choice",
            Payload(pending, h.root, "main", base.head, unrelated = Some(Vector.empty)), call.core)
          _ = When("Advance cannot choose that extra index change as part of the original Task")
          result <- GoalTaskNativeRun.advanceC("fixture-ownership-choice", h, seeded._1, call.core)
          record <- currentC(seeded._2, "fixture-ownership-choice", call.core)
          retained <- GoalTaskNativeRun.loadPayloadC(seeded._2, "fixture-ownership-choice", record.revision.value, call.core)
          _ = Then("Human receives the actual paths and all original accepted work/counts remain")
          _ = result.kind shouldBe "Human"
          _ = result.message should include("unrelated.txt")
          _ = result.message should include("value.txt")
          _ = retained.state.semanticReference shouldBe pending.semanticReference
          _ = retained.state.automaticCount shouldBe 3
          _ = retained.state.ordinaryCount shouldBe 3
          _ = Files.exists(directory.resolve("stage-calls")) shouldBe false
          _ = Files.readString(directory.resolve("repository/unrelated.txt")) shouldBe "another worker's selected change\n"
        } yield ())
      }
    }
  }

  "Later native Advance of confirmed terminated validation" should {
    "retain the old checkpoint and successful prefix for both Task and Fix while admitting only the affected check" in {
      Given("fixture-managed Task and Fix Wait checkpoints with confirmed terminal incomplete captures")
      Vector(false, true).foreach { task =>
        val directory = root(); prepare(directory)
        Files.writeString(directory.resolve("repository/value.txt"), "selected continuation delta\n")
        _with_runtime_(directory, "Outcome", hostParameters = parameters(directory)) { (_, _, _, project) =>
          project(call => for {
            before <- G.observeC(call.core)
            _ <- E.stageGoalCandidateC(call.core, 1).flatMap(_.valueC)
            candidate <- candidateC(call.core, before.head)
            n = native(task)
            h = host(directory)
            session = "fixture-terminal-continuation-" + task
            raw = TerminatedValidationFixture.receipt(selected.last)
            waiting = state(task, "Wait").copy(candidate = Some(candidate), automaticCount = 3, ordinaryCount = 2,
              receipts = state(task, "Wait").receipts.dropRight(1) :+ raw)
            payload = Payload(waiting, h.root, "main", before.head,
              implementationWorker = Some("original-worker"), implementationWorkers = Some(Vector("original-worker", "repair-worker")))
            seeded <- seedC(n, h, session, payload, call.core)
            _ = When("the subsequent public native Advance uses an unchanged candidate but no admitted SBT target")
            result <- n.advanceC(session, h, seeded._1, call.core)
            record <- currentC(seeded._2, session, call.core)
            retained <- n.loadPayloadC(seeded._2, session, record.revision.value, call.core)
            original <- n.loadPayloadC(seeded._2, session, seeded._3.revision.value, call.core)
            _ = Then("only the affected fresh attempt is admission-blocked and its predecessor capture remains immutable")
            _ = result.kind shouldBe "Wait"
            _ = retained.state.stage shouldBe "AdmissionBlocked"
            _ = retained.state.receipts shouldBe waiting.receipts.dropRight(1)
            _ = original shouldBe payload
            _ = original.state.receipts.last shouldBe raw
            _ = retained.copy(state = waiting) shouldBe payload
            _ = retained.state.candidate shouldBe waiting.candidate
            _ = retained.state.automaticCount shouldBe 3
            _ = retained.state.ordinaryCount shouldBe 2
            _ = retained.state.repairControl shouldBe waiting.repairControl
            _ = record.revision.value should be > seeded._3.revision.value
            _ = commitCalls(directory) shouldBe 0L
          } yield ())
        }
      }
    }

    "leave changed candidates and unconfirmed executions at the same Wait without a duplicate intent" in {
      Given("an existing Wait whose original capture is either terminal or termination-unconfirmed")
      Vector(false, true).foreach { changed =>
        val directory = root(); prepare(directory)
        Files.writeString(directory.resolve("repository/value.txt"), "selected continuation delta\n")
        _with_runtime_(directory, "Outcome", hostParameters = parameters(directory)) { (_, _, _, project) =>
          project(call => for {
            before <- G.observeC(call.core)
            _ <- E.stageGoalCandidateC(call.core, 1).flatMap(_.valueC)
            candidate <- candidateC(call.core, before.head)
            h = host(directory)
            raw = if (changed) TerminatedValidationFixture.receipt(selected.last)
              else TerminatedValidationFixture.receipt(selected.last,
                org.goldenport.cncf.processexecution.ProcessExecutionTermination.TerminationUnconfirmed)
            waiting = state(true, "Wait").copy(candidate = Some(candidate),
              receipts = state(true, "Wait").receipts.dropRight(1) :+ raw)
            payload = Payload(waiting, h.root, "main", before.head)
            session = "fixture-refused-continuation-" + changed
            seeded <- seedC(GoalTaskNativeRun, h, session, payload, call.core)
            _ = if (changed) Files.writeString(directory.resolve("repository/value.txt"), "changed after capture\n") else ()
            _ = When("Advance is repeated without a valid current candidate and confirmed termination pair")
            first <- GoalTaskNativeRun.advanceC(session, h, seeded._1, call.core)
            second <- GoalTaskNativeRun.advanceC(session, h, seeded._1, call.core)
            record <- currentC(seeded._2, session, call.core)
            retained <- GoalTaskNativeRun.loadPayloadC(seeded._2, session, record.revision.value, call.core)
            _ = Then("both reads remain Wait and retain exactly the old checkpoint, counts and incomplete observation")
            _ = first.kind shouldBe "Wait"
            _ = second.kind shouldBe "Wait"
            _ = record.revision shouldBe seeded._3.revision
            _ = retained shouldBe payload
            _ = commitCalls(directory) shouldBe 0L
          } yield ())
        }
      }
    }
  }

  "Shared bounded native Evidence" should {
    "archive confirmed raw check and semantic outcomes through real managed Evidence and Journal without promoting uncertainty" in {
      val directory = root(); prepare(directory)
      _with_runtime_(directory, "Outcome", hostParameters = parameters(directory)) { (_, _, _, project) =>
        project(call => (for {
          n <- Vector(false, true)
          (outcome, code, expected) <- Vector((V.Outcome.Succeeded, 0, "passed"), (V.Outcome.Failed, 1, "failed"))
        } yield (n, outcome, code, expected)).foldLeft(Consequence.unit) { case (prior, (task, outcome, code, expected)) => prior.flatMap { _ =>
          val n = native(task); val h = host(directory); val raw = receipt(outcome, code)
          for {
            refs <- n.recordCheckEvidenceC(h, call.core, "fixture-check-" + task + "-" + code, 2L, raw)
            _ = refs.size shouldBe 1
            text <- projectionC(directory, call.core, refs.head, expected)
            _ = text shouldBe raw.asJson
            _ <- Vector(V.Outcome.NotExecuted, V.Outcome.Incomplete, V.Outcome.Unverified).foldLeft(Consequence.unit) { (p, unknown) => p.flatMap(_ =>
              n.recordCheckEvidenceC(h, call.core, "fixture-uncertain-" + task, 3L, receipt(unknown))
                .map(values => values shouldBe empty)) }
            result = S.Result("completed", "Specification-owned semantic completion", Vector.empty,
              Vector(S.Condition("behavior", true, "Source assessment; not a test receipt")), Vector.empty)
            semantic <- n.recordSemanticEvidenceC(h, call.core, "fixture-semantic-" + task, 4L, "Repair", result)
            _ = semantic.size shouldBe 1
            semanticText <- projectionC(directory, call.core, semantic.head, "passed")
            _ = semanticText shouldBe result.asJson
            crossed <- n.recordCheckEvidenceC(h, call.core, "fixture-contradictory-" + task, 5L,
              receipt(V.Outcome.Succeeded, 1))
            _ = crossed shouldBe empty
            unresolved <- n.recordSemanticEvidenceC(h, call.core, "fixture-unresolved-" + task, 5L, "Repair",
              result.copy(outcome = "decision-needed"))
            _ = unresolved shouldBe empty
          } yield ()
        }})
      }
    }

    "persist terminal references and warnings for Task and standalone and collect them on duplicate Advance" in {
      val directory = root(); prepare(directory)
      _with_runtime_(directory, "Outcome", hostParameters = parameters(directory)) { (_, _, _, project) =>
        project(call => Vector((false, "Accepted"), (false, "NoChange"), (true, "Completed"), (true, "NoChange"))
          .foldLeft(Consequence.unit) { case (prior, (task, stage)) => prior.flatMap { _ =>
            val n = native(task); val h = host(directory); val session = "fixture-terminal-" + task + "-" + stage
            for {
              seeded <- seedC(n, h, session, Payload(state(task, stage), h.root, "main", "a" * 40), call.core)
              (definition, storage, saved) = seeded
              retained <- n.loadPayloadC(storage, session, saved.revision.value, call.core)
              _ = retained.state.evidenceReferences.size shouldBe 1
              _ = retained.state.projectionWarning shouldBe None
              // A terminal checkpoint is collected, never re-executed as a new mechanical intent.
              mechanical <- storage.bindMechanicalC(n.advanceOperation, n.stateCodec)
              checkpoint <- mechanical.loadRetainedC(WorkflowMechanicalTransition.Reference(
                session + ":standalone-state:" + saved.revision.value, "1"))(using call.core.executionContext)
              committed <- checkpoint.fold[Consequence[WorkflowManagedMechanicalTransition.Retained[Payload]]](
                Consequence.stateConflict("Expected actual terminal mechanical checkpoint"))(Consequence.success)
              _ = committed.receipt.current.lifecycle shouldBe Lifecycle.Active
              _ = committed.receipt.current.suspension shouldBe None
              _ = committed.receipt.next.revision shouldBe committed.receipt.current.revision.next
              _ = committed.receipt.next.lifecycle shouldBe Lifecycle.Completed
              _ = committed.current.revision shouldBe saved.revision
              collected <- n.loadPayloadC(storage, session, committed.current.revision.value, call.core)
              _ = collected shouldBe retained // Includes counts, commit, references and warnings.
              archived <- CommonEvidence.loadedC(CommonEvidence.storage(call.core, evidence),
                retained.state.evidenceReferences.head.identity)
              observation <- archived.fold[Consequence[(CommonEvidence.Allocation, Option[CommonEvidence.Observation])]](
                Consequence.stateConflict("Expected retained terminal Evidence allocation"))(Consequence.success)
              stored <- observation._2.fold[Consequence[CommonEvidence.Observation]](
                Consequence.stateConflict("Expected terminal storage observation"))(Consequence.success)
              idempotent <- DevelopmentJournalEvidence.recordC(Some(journal), call.core, observation._1, stored)
              _ = idempotent.status shouldBe "alreadyRecorded"
              content <- projectionC(directory, call.core, retained.state.evidenceReferences.head, "passed")
              _ = content.hcursor.downField("state").get[String]("stage").toOption shouldBe Some(stage)
              first <- n.advanceC(session, h, definition, call.core)
              second <- n.advanceC(session, h, definition, call.core)
              _ = first.kind shouldBe "Terminal"
              _ = take(first.toRecord().toJsonStringC) shouldBe take(second.toRecord().toJsonStringC)
              current <- currentC(storage, session, call.core)
              _ = current.revision shouldBe saved.revision
              after <- n.loadPayloadC(storage, session, current.revision.value, call.core)
              _ = after shouldBe retained // Duplicate Advance preserves counts and any commit identity.
              // Missing projection support stays explicit for both owners, while terminal success persists.
              missingSession = session + "-no-evidence"
              missing <- seedC(n, h.copy(evidence = None), missingSession,
                Payload(state(task, stage), h.root, "main", "a" * 40), call.core)
              warned <- n.loadPayloadC(missing._2, missingSession, missing._3.revision.value, call.core)
              _ = warned.state.evidenceReferences shouldBe empty
              _ = warned.state.projectionWarning shouldBe Some("Common Evidence projection is not installed")
              unavailableSession = session + "-journal-unavailable"
              unavailable <- seedC(n, h.copy(journal = Some(journal.copy(collection = _collection_("not_granted")))),
                unavailableSession, Payload(state(task, stage), h.root, "main", "a" * 40), call.core)
              projected <- n.loadPayloadC(unavailable._2, unavailableSession, unavailable._3.revision.value, call.core)
              _ = projected.state.evidenceReferences.size shouldBe 1
              _ = projected.state.projectionWarning shouldBe Some("Common Evidence stored; Journal projection is unavailable")
            } yield ()
          }})
      }
    }
  }

  "Evidence availability and Journal recording" should {
    "retain real managed conflict and indeterminate observations without claiming stored content" in {
      for (uncertain <- Vector(false, true); task <- Vector(false, true)) {
        val directory = root(); prepare(directory)
        _with_runtime_(directory, "Outcome", hostParameters = parameters(directory)) { (_, _, _, project) =>
          if (!uncertain) {
            // Controlled real File directory collision; preserve these preexisting bytes.
            Files.delete(directory.resolve("archive/logs"))
            Files.writeString(directory.resolve("archive/logs"), "preexisting archive collision\n")
          }
          project(call => for {
            definition <- definitionC(task)
            n = native(task)
            selectedPolicy = if (uncertain) evidence.copy(workspaceIdentity = "unregistered-evidence-workspace") else evidence
            h = host(directory).copy(evidence = Some(selectedPolicy))
            session = "fixture-storage-" + uncertain + "-" + task
            expected = if (uncertain) "indeterminate" else "conflict"
            storageWarning = if (uncertain) "Common Evidence storage is indeterminate; captured content is not confirmed stored"
              else "Common Evidence storage conflict; captured content is not confirmed stored"
            seeded <- seedC(n, h, session, Payload(state(task, if (task) "Completed" else "Accepted"),
              h.root, "main", "a" * 40), call.core)
            retained <- n.loadPayloadC(seeded._2, session, seeded._3.revision.value, call.core)
            _ = retained.state.evidenceReferences.size shouldBe 1 // Real allocation, not content availability.
            reference = retained.state.evidenceReferences.head
            observed <- CommonEvidence.loadedC(CommonEvidence.storage(call.core, selectedPolicy), reference.identity)
            allocation = observed.get._1
            storage = observed.get._2.get
            _ = storage.status shouldBe expected
            _ = retained.state.projectionWarning shouldBe Some(storageWarning)
            _ = retained.state.projectionWarning.get should include("not confirmed stored")
            bound <- ComponentRecordStorage.bindForCallC(call.core, journal.storeName, journal.collection,
              DataStore.StringEntryId(DevelopmentJournalEvidence.identity(reference.identity)))
            row <- bound.loadC()
            entry <- DevelopmentJournalEvidence.storedC(row.get, DevelopmentJournalEvidence.identity(reference.identity))
            _ = entry.observation.status shouldBe expected
            idempotent <- DevelopmentJournalEvidence.recordC(Some(journal), call.core, allocation, storage)
            _ = idempotent.status shouldBe "alreadyRecorded"
            _ = n.projectionWarning(expected, Some(idempotent.status)) shouldBe retained.state.projectionWarning
            again <- n.advanceC(session, h, definition, call.core)
            _ = again.kind shouldBe "Terminal"
            current <- currentC(seeded._2, session, call.core)
            _ = current.revision shouldBe seeded._3.revision
            unavailable <- seedC(n, h.copy(journal = Some(journal.copy(collection = _collection_("not_granted")))),
              session + "-unavailable-journal", Payload(state(task, if (task) "Completed" else "Accepted"),
                h.root, "main", "a" * 40), call.core)
            warned <- n.loadPayloadC(unavailable._2, session + "-unavailable-journal", unavailable._3.revision.value, call.core)
            _ = warned.state.evidenceReferences.size shouldBe 1
            _ = warned.state.projectionWarning shouldBe Some(storageWarning + "; Journal projection is unavailable")
            _ = warned.state.projectionWarning.get should include("not confirmed stored")
            _ = warned.state.projectionWarning.get should not startWith "Common Evidence stored;"
            _ = if (!uncertain) Files.readString(directory.resolve("archive/logs")) shouldBe "preexisting archive collision\n"
          } yield ())
        }
      }
      for (n <- Vector(StandaloneFixNativeRun, GoalTaskNativeRun)) {
        n.projectionWarning("stored", Some("recorded")) shouldBe None
        n.projectionWarning("stored", Some("alreadyRecorded")) shouldBe None
        n.projectionWarning("stored", Some("unavailable")) shouldBe Some("Common Evidence stored; Journal projection is unavailable")
        for (status <- Vector("conflict", "indeterminate"); journalStatus <- Vector(Some("recorded"), Some("alreadyRecorded"), Some("unavailable"), None)) {
          val warning = n.projectionWarning(status, journalStatus)
          warning should not be empty
          warning.get should include("not confirmed stored")
          warning.get should include(status)
          warning.get should not startWith "Common Evidence stored;"
        }
      }
    }
  }

  "Native Task selected closure and reconciliation" should {
    "commit the reviewed selected delta through the real port while preserving unrelated dirty bytes" in {
      val directory = root(); prepare(directory)
      Files.writeString(directory.resolve("repository/value.txt"), "selected implementation\n")
      Files.writeString(directory.resolve("repository/unrelated.txt"), "other worker dirty bytes\n")
      _with_runtime_(directory, "Outcome", hostParameters = parameters(directory)) { (_, _, _, project) =>
        project(call => for {
          before <- G.observeC(call.core)
          _ <- E.stageGoalCandidateC(call.core, 1).flatMap(_.valueC)
          tree <- G.indexTreeC(call.core)
          candidate <- candidateC(call.core, before.head)
          _ = candidate.paths shouldBe Vector("value.txt")
          ready = state(true, "CommitReady").copy(candidate = Some(candidate), reviewReference = Some(ContextReference("fixture-independent-review", "1")))
          h = host(directory)
          payload = Payload(ready, h.root, "main", before.head, unrelated = Some(before.dirty.filterNot(_.path == "value.txt")))
          seeded <- seedC(GoalTaskNativeRun, h, "fixture-commit", payload, call.core)
          result <- GoalTaskNativeRun.advanceC("fixture-commit", h, seeded._1, call.core)
          _ = result.kind shouldBe "Terminal"
          record <- currentC(seeded._2, "fixture-commit", call.core)
          retained <- GoalTaskNativeRun.loadPayloadC(seeded._2, "fixture-commit", record.revision.value, call.core)
          observed <- G.observeC(call.core)
          actual <- G.commitC(call.core, observed.head)
          _ = actual.parents shouldBe Vector(before.head)
          _ = actual.tree shouldBe tree
          _ = retained.state.stage shouldBe "Completed"
          _ = retained.state.commit.map(_.identity) shouldBe Some(actual.identity)
          _ = retained.state.commitExecution.map(_.outcome) shouldBe Some("Succeeded")
          _ = observed.dirty shouldBe payload.unrelated.get
          _ = Files.readString(directory.resolve("repository/unrelated.txt")) shouldBe "other worker dirty bytes\n"
          _ = commitCalls(directory) shouldBe 1L
          _ <- GoalTaskNativeRun.advanceC("fixture-commit", h, seeded._1, call.core)
          _ = commitCalls(directory) shouldBe 1L
        } yield ())
      }
    }

    "retain controlled failed commit and uncertain intent without retry or loss of unrelated work" in {
      for (failure <- Vector(false, true)) {
        val directory = root(); prepare(directory)
        Files.writeString(directory.resolve("repository/value.txt"), "selected implementation\n")
        Files.writeString(directory.resolve("repository/unrelated.txt"), "other worker dirty bytes\n")
        if (failure) Files.writeString(directory.resolve("fail-commit"), "Specification-owned exit1 before commit\n")
        _with_runtime_(directory, "Outcome", hostParameters = parameters(directory)) { (_, _, _, project) =>
          project(call => for {
            before <- G.observeC(call.core)
            _ <- E.stageGoalCandidateC(call.core, 1).flatMap(_.valueC)
            h = host(directory)
            candidate <- candidateC(call.core, before.head)
            _ = candidate.paths shouldBe Vector("value.txt")
            retained = state(true, if (failure) "CommitReady" else "CommitIntent").copy(
              candidate = Some(candidate),
              reviewReference = Some(ContextReference("fixture-independent-review", "1")))
            payload = Payload(retained, h.root, "main", before.head, unrelated = Some(before.dirty.filterNot(_.path == "value.txt")))
            seeded <- seedC(GoalTaskNativeRun, h, "fixture-uncertain", payload, call.core)
            first <- GoalTaskNativeRun.advanceC("fixture-uncertain", h, seeded._1, call.core)
            _ = first.kind shouldBe "Incomplete"
            record <- currentC(seeded._2, "fixture-uncertain", call.core)
            saved <- GoalTaskNativeRun.loadPayloadC(seeded._2, "fixture-uncertain", record.revision.value, call.core)
            _ = saved.state.stage shouldBe (if (failure) "CommitFailed" else "CommitIntent")
            _ = saved.state.commit shouldBe None
            _ = saved.state.reviewReference shouldBe retained.reviewReference
            _ = saved.state.conditionReport shouldBe retained.conditionReport
            _ = saved.state.receipts shouldBe retained.receipts
            _ = saved.state.candidate shouldBe retained.candidate
            _ = saved.state.automaticCount shouldBe retained.automaticCount
            _ = saved.state.ordinaryCount shouldBe retained.ordinaryCount
            _ = commitCalls(directory) shouldBe (if (failure) 1L else 0L)
            _ <- GoalTaskNativeRun.advanceC("fixture-uncertain", h, seeded._1, call.core)
            again <- currentC(seeded._2, "fixture-uncertain", call.core)
            _ = again.revision shouldBe record.revision
            againPayload <- GoalTaskNativeRun.loadPayloadC(seeded._2, "fixture-uncertain", again.revision.value, call.core)
            _ = againPayload shouldBe saved
            _ = commitCalls(directory) shouldBe (if (failure) 1L else 0L)
            after <- G.observeC(call.core)
            _ = after.head shouldBe before.head
            _ = after.dirty.filterNot(_.path == "value.txt") shouldBe payload.unrelated.get
            _ = Files.readString(directory.resolve("repository/unrelated.txt")) shouldBe "other worker dirty bytes\n"
          } yield ())
        }
      }
    }

    "collect an actual commit after a failed process result without replaying that commit" in {
      val directory = root(); prepare(directory)
      Files.writeString(directory.resolve("repository/value.txt"), "selected implementation\n")
      Files.writeString(directory.resolve("repository/unrelated.txt"), "other worker dirty bytes\n")
      Files.writeString(directory.resolve("commit-then-fail"), "Specification-owned failure after actual Git commit\n")
      _with_runtime_(directory, "Outcome", hostParameters = parameters(directory)) { (_, _, _, project) =>
        project(call => for {
          before <- G.observeC(call.core)
          _ <- E.stageGoalCandidateC(call.core, 1).flatMap(_.valueC)
          candidate <- candidateC(call.core, before.head)
          h = host(directory)
          ready = state(true, "CommitReady").copy(candidate = Some(candidate),
            reviewReference = Some(ContextReference("fixture-independent-review", "1")))
          payload = Payload(ready, h.root, "main", before.head,
            unrelated = Some(before.dirty.filterNot(_.path == "value.txt")))
          seeded <- seedC(GoalTaskNativeRun, h, "fixture-failed-result-committed", payload, call.core)
          first <- GoalTaskNativeRun.advanceC("fixture-failed-result-committed", h, seeded._1, call.core)
          _ = first.kind shouldBe "Incomplete"
          failedRecord <- currentC(seeded._2, "fixture-failed-result-committed", call.core)
          failed <- GoalTaskNativeRun.loadPayloadC(seeded._2, "fixture-failed-result-committed", failedRecord.revision.value, call.core)
          _ = failed.state.stage shouldBe "CommitFailed"
          _ = failed.state.commit shouldBe None
          _ = failed.state.commitExecution.map(_.termination) shouldBe Some("exited")
          _ = failed.state.commitExecution.flatMap(_.exitCode) shouldBe Some(1)
          _ = commitCalls(directory) shouldBe 1L
          observed <- G.observeC(call.core)
          actual <- G.commitC(call.core, observed.head)
          _ = actual.parents shouldBe Vector(before.head)
          _ = actual.tree shouldBe candidate.tree
          collected <- GoalTaskNativeRun.advanceC("fixture-failed-result-committed", h, seeded._1, call.core)
          _ = collected.kind shouldBe "Terminal"
          completedRecord <- currentC(seeded._2, "fixture-failed-result-committed", call.core)
          completed <- GoalTaskNativeRun.loadPayloadC(seeded._2, "fixture-failed-result-committed", completedRecord.revision.value, call.core)
          _ = completed.state.stage shouldBe "Completed"
          _ = completed.state.commit.map(_.identity) shouldBe Some(actual.identity)
          _ = completed.state.commitExecution shouldBe failed.state.commitExecution
          _ = completed.state.candidate shouldBe ready.candidate
          _ = completed.state.receipts shouldBe ready.receipts
          _ = completed.state.reviewReference shouldBe ready.reviewReference
          _ = completed.state.conditionReport shouldBe ready.conditionReport
          _ = completed.state.automaticCount shouldBe ready.automaticCount
          _ = completed.state.ordinaryCount shouldBe ready.ordinaryCount
          _ = completedRecord.history.take(failedRecord.history.size) shouldBe failedRecord.history
          _ <- GoalTaskNativeRun.advanceC("fixture-failed-result-committed", h, seeded._1, call.core)
          unchanged <- currentC(seeded._2, "fixture-failed-result-committed", call.core)
          _ = unchanged.revision shouldBe completedRecord.revision
          _ = commitCalls(directory) shouldBe 1L
          after <- G.observeC(call.core)
          _ = after.head shouldBe actual.identity
          _ = after.dirty shouldBe payload.unrelated.get
          _ = Files.readString(directory.resolve("repository/unrelated.txt")) shouldBe "other worker dirty bytes\n"
        } yield ())
      }
    }

    "refuse an empty selected commit without creating an intent or executing CommitSelected" in {
      val directory = root(); prepare(directory)
      _with_runtime_(directory, "Outcome", hostParameters = parameters(directory)) { (_, _, _, project) =>
        project(call => for {
          before <- G.observeC(call.core)
          tree <- G.indexTreeC(call.core)
          h = host(directory)
          ready = state(true, "CommitReady").copy(candidate = Some(S.Candidate(before.head, tree, Vector.empty, "")),
            reviewReference = Some(ContextReference("fixture-independent-review", "1")))
          seeded <- seedC(GoalTaskNativeRun, h, "fixture-empty", Payload(ready, h.root, "main", before.head,
            unrelated = Some(Vector.empty)), call.core)
          refused = GoalTaskNativeRun.advanceC("fixture-empty", h, seeded._1, call.core)
          _ = refused.isSuccess shouldBe false
          after <- currentC(seeded._2, "fixture-empty", call.core)
          _ = after.revision shouldBe seeded._3.revision
          _ = commitCalls(directory) shouldBe 0L
          observed <- G.observeC(call.core)
          _ = observed.head shouldBe before.head
        } yield ())
      }
    }
  }
}
