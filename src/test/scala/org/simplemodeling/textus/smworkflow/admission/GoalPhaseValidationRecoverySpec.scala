package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.{Consequence, Conclusion}
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseNativeValidation as V
import GoalPhaseValidationRecovery as R
import GoalPhaseOrderedProgression as O
import GoalPhaseDependencyValidation.given
import SplitPhaseExplicitPartitionFixture.take

/** Immutable data/chain and production check-program specifications; these facts are test inputs, not host
  * observations or an independent production Human attestation.
  * @since   Oct. 20, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseValidationRecoverySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val snapshot = O.Snapshot("a" * 40, "b" * 40)
  private val check = V.Check("compile", 1, WorkflowSbtValidation.Kind.Compile)

  "DV-07 dependency validation recovery subject" should {
    "retain the dependency alongside the original primary target without granting a new attempt" in {
      Given("a SPECIFICATION-OWNED unknown attempt for the same Session, candidate and check")
      val capture = DependencyValidationSpecificationFixture.capture
      val subject = GoalPhaseDependencyValidation.subject(capture)
      val original = R.RecoveryTarget("same-session", "selected-plan", "1", capture.primaryBase,
        capture.primaryBase, "20", "25", "step-1", "cncf-compile", 2, 0, "24", dependency = Some(subject))
      When("the ordinary recovery codec and independent Human binding select that exact subject")
      val restored = R.decodeTarget(original.asJson)
      val binding = take(R.bindingC("org.simplemodeling.SmWorkflow", original, "AuthorizeNewAttempt",
        "SPECIFICATION-OWNED explicit new attempt, accepting potentially repeated effects", "participant",
        ContextReference("specification-human-answer", "1")))
      Then("repository, workspace, base and candidate remain in the actual authorization payload")
      restored shouldBe Right(original)
      binding.hcursor.downField("validationRecovery").get[R.RecoveryTarget]("target").toOption shouldBe Some(original)
      R.selectBinding(binding) shouldBe Right(binding)
      val crossed = original.copy(dependency = Some(subject.copy(workspace = "another-source")))
      take(R.bindingC("org.simplemodeling.SmWorkflow", crossed, "AuthorizeNewAttempt",
        "SPECIFICATION-OWNED distinct subject", "participant", ContextReference("specification-human-answer", "1")))
        .hcursor.downField("validationRecovery").get[R.RecoveryTarget]("target").toOption should not be Some(original)
      R.decodeTarget(original.asJson.mapObject(_.add("dependency", Json.Null))).isLeft shouldBe true
      R.decodeTarget(original.asJson.mapObject(_.add("dependency",
        subject.asJson.mapObject(_.remove("candidate"))))).isLeft shouldBe true
      R.decodeTarget(original.copy(dependency = None).asJson).toOption.get.dependency shouldBe None
      // Decoding and binding perform no native recovery, effect dispatch or history mutation.
    }
  }
  private def source: V.Saved = {
    val empty = take(InstanceRecord(InstanceIdentity("validation-recovery-unit"),
      take(GoalPhaseRuntimeCatalog.loadC()).definition, initialRevision, Lifecycle.NotStarted, None,
      CorrelationReference("unit-correlation"), CausationReference("unit-start"), None, None, None, Vector.empty).validateC)
    val record = append(empty, "unit-validation-source")
    V.Saved(record, V.Payload(record.revision.value.toString, "step-1", snapshot, Vector(check), Vector.empty, None))
  }
  private def append(from: InstanceRecord, cause: String): InstanceRecord =
    take(from.appendC(from.revision, GoalPhaseNativeRun.history(from, "phase.Validation", cause)))
  private def intent(previous: V.Saved, ordinal: Int = 0): V.Saved = V.Saved(
    append(previous.record, "ordered-validation:check-intent-0" + (if (ordinal == 0) "" else "-retry-" + ordinal)), previous.payload)
  private def result(prepared: V.Saved, payload: V.Payload): V.Saved =
    V.Saved(append(prepared.record, "ordered-validation:check-result-0"), payload)
  private def recovery(prepared: V.Saved, previous: V.Saved): V.Saved = V.Saved(
    append(previous.record, "ordered-validation:check-recovery-0"), previous.payload.copy(newAttempts = previous.payload.newAttempts :+
      V.NewAttempt(check.identity, 0, prepared.record.revision.value.toString, "actual-unit-human-answer", "1", "participant",
        "I explicitly authorize a new attempt, accepting possible repeated effects")))
  private def conclusion(message: String): Conclusion = Consequence.stateConflict[Unit](message) match {
    case Consequence.Failure(value) => value
    case _ => fail("Expected a diagnostic conclusion")
  }

  /** Controlled Store/UoW effects for the exact program used by native Advance.
    * These observations do not claim a real production startup failure.
    */
  private final class CheckEpisode(initial: V.Saved, outcomes: Vector[SbtExecutionAttempt],
    retained: Vector[V.Attempt] = Vector.empty,
    precondition: Int => Consequence[Unit] = _ => Consequence.unit) {
    private var _attempts = retained
    private var _dispatches = Vector.empty[V.Check]
    private var _events = Vector.empty[String]
    private var _preconditions = 0
    def attempts: Vector[V.Attempt] = _attempts
    def dispatches: Vector[V.Check] = _dispatches
    def events: Vector[String] = _events
    def runC(): Consequence[V.Saved] = V.runCheckC(check, 0)(
      () => {
        _events :+= "reconcile"
        V.reconcileAttemptsC(initial, initial.payload, 0, _attempts)
      },
      () => {
        _events :+= "preconditions"
        _preconditions += 1
        precondition(_preconditions)
      }, _save_c, point => { _events :+= "ack:" + point; Consequence.unit }, _run_c,
      _ => { _events :+= "evidence"; Consequence.unit })
    private def _save_c(key: String, from: InstanceRecord, payload: V.Payload): Consequence[V.Saved] =
      if (V.codec.decode(V.codec.encode(payload)).isLeft) Consequence.stateConflict("Controlled Store refuses invalid native payload")
      else {
      val ordinal = if (key.contains("-retry-")) key.split("-retry-").last.toInt else 0
      val saved = V.Saved(append(from, "ordered-validation:" + key), payload)
      if (_attempts.size == ordinal) _attempts :+= V.Attempt(None, None)
      val previous = _attempts(ordinal)
      _attempts = _attempts.updated(ordinal, if (key.startsWith("check-intent-")) previous.copy(intent = Some(saved))
        else previous.copy(result = Some(saved)))
      _events :+= key
      Consequence.success(saved)
    }
    private def _run_c(selected: V.Check): Consequence[SbtExecutionAttempt] = {
      val ordinal = _dispatches.size
      _events :+= "run:" + selected.identity
      _dispatches :+= selected
      Consequence.success(outcomes.lift(ordinal).getOrElse(fail("Unexpected extra selected check dispatch")))
    }
  }
  private def _observed(termination: ProcessExecutionTermination, stdout: String = "", stderr: String = ""):
    SbtExecutionAttempt = {
    def _capture_(text: String): ProcessExecutionCapture = {
      val bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
      ProcessExecutionCapture(bytes, bytes.size.toLong, false)
    }
    SbtExecutionAttempt.Observed(SbtExecutionResult(
      SbtTarget(take(WorkspaceId.parseC("sm-workflow-repository-validation-1"))), SbtOperation.Compile,
      ProcessExecutionResult(termination, _capture_(stdout), _capture_(stderr), Vector.empty, 1L, "controlled-check-attempt")))
  }

  "E7 Bounded runtime NotStarted recovery" should afterWord(
    "in spec:goal-phase-validation-recovery, example:E7, rules:R1,R2,R7, phase:execution-recovery") {
    "retain the stopped receipt before reconciling and dispatching the same check once more" in {
      Given("docs/spec/goal-phase-validation-recovery.md R1,R2,R7 E7: controlled NotStarted then successful owning-UoW results")
      val initial = source
      val episode = new CheckEpisode(initial, Vector(_observed(ProcessExecutionTermination.NotStarted),
        _observed(ProcessExecutionTermination.Exited(0))))
      When("the exact native single-check program drives the admitted selection")
      val saved = take(episode.runC())
      Then("both immutable attempts and the original selection survive in the prescribed effect order")
      episode.dispatches shouldBe Vector(check, check)
      episode.attempts.size shouldBe 2
      val stopped = episode.attempts.head.result.get
      val retry = episode.attempts(1).intent.get
      retry.payload shouldBe stopped.payload
      retry.record.history.dropRight(1) shouldBe stopped.record.history
      retry.record.causation.value shouldBe "ordered-validation:check-intent-0-retry-1"
      saved.payload.stopped shouldBe stopped.payload.stopped
      saved.payload.stopped.head.termination shouldBe "not-started"
      saved.payload.stopped.head.exitCode shouldBe None
      saved.payload.receipts.map(_.identity) shouldBe Vector(check.identity)
      saved.payload.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
      saved.payload.copy(receipts = Vector.empty, stopped = Vector.empty) shouldBe initial.payload
      episode.events shouldBe Vector("reconcile", "preconditions", "check-intent-0", "ack:goal-check-prepared:compile",
        "run:compile", "ack:goal-check-executed:compile", "check-result-0", "ack:goal-check-recorded:compile", "evidence",
        "reconcile", "preconditions", "check-intent-0-retry-1", "ack:goal-check-prepared:compile", "run:compile",
        "ack:goal-check-executed:compile", "check-result-0-retry-1", "ack:goal-check-recorded:compile", "evidence")
    }

    "stop after a second NotStarted and preserve a later ordinary invocation's fresh bound" in {
      Given("docs/spec/goal-phase-validation-recovery.md R7 E7: three controlled NotStarted outcomes")
      val initial = source
      val episode = new CheckEpisode(initial, Vector.fill(3)(_observed(ProcessExecutionTermination.NotStarted)))
      When("one native check invocation completes its bounded startup recovery")
      val stopped = take(episode.runC())
      Then("two terminal attempts are retained, with no third dispatch or invented product receipt")
      episode.dispatches shouldBe Vector(check, check)
      episode.attempts.size shouldBe 2
      stopped.payload.stopped.size shouldBe 2
      stopped.payload.receipts shouldBe empty
      V.continuable(stopped) shouldBe true

      Given("that retained prefix and a later ordinary invocation under the same selected check")
      val resumed = new CheckEpisode(initial, Vector(_observed(ProcessExecutionTermination.NotStarted),
        _observed(ProcessExecutionTermination.Exited(0))), episode.attempts)
      When("the later invocation reconciles its actual retained ordinals")
      val completed = take(resumed.runC())
      Then("new ordinals follow the original terminal prefix with only one additional dispatch per invocation")
      resumed.dispatches shouldBe Vector(check, check)
      resumed.attempts.take(2) shouldBe episode.attempts
      resumed.attempts(2).intent.get.record.causation.value shouldBe "ordered-validation:check-intent-0-retry-2"
      resumed.attempts(3).intent.get.record.causation.value shouldBe "ordered-validation:check-intent-0-retry-3"
      completed.payload.stopped.take(2) shouldBe stopped.payload.stopped
      completed.payload.receipts.head.exitCode shouldBe Some(0)
    }

    "recheck current preconditions before preparing any extra intent" in {
      Given("docs/spec/goal-phase-validation-recovery.md R7 E7: NotStarted followed by unavailable current preparation")
      val initial = source
      val episode = new CheckEpisode(initial, Vector(_observed(ProcessExecutionTermination.NotStarted)),
        precondition = count => if (count == 1) Consequence.unit else Consequence.stateConflict("Current preparation refused"))
      When("the bounded program attempts to re-admit the same check")
      val refused = episode.runC()
      Then("the actual stopped result stays terminal and no second intent or execution is created")
      refused.isSuccess shouldBe false
      episode.dispatches shouldBe Vector(check)
      episode.attempts.size shouldBe 1
      episode.attempts.head.result.get.payload.stopped.size shouldBe 1
      episode.events.takeRight(3) shouldBe Vector("evidence", "reconcile", "preconditions")
    }

    "skip dispatch for successful and unresolved retained prefixes" in {
      Given("docs/spec/goal-phase-validation-recovery.md R7 E7: complete success and unresolved intent prefixes")
      val initial = source
      val prepared = intent(initial)
      val passed = result(prepared, take(V.attemptPayloadC(initial.payload, check, 0,
        _observed(ProcessExecutionTermination.Exited(0)))))
      val prefixes = Vector(Vector(V.Attempt(Some(prepared), Some(passed))), Vector(V.Attempt(Some(prepared), None)))
      prefixes.foreach { retained =>
        val episode = new CheckEpisode(initial, Vector.empty, retained)
        When("the exact native program reconciles the retained prefix")
        val saved = take(episode.runC())
        Then("neither preconditions, intent creation nor UoW dispatch is repeated")
        episode.dispatches shouldBe empty
        episode.events shouldBe Vector("reconcile")
        episode.attempts shouldBe retained
        saved shouldBe retained.last.result.getOrElse(prepared)
      }
    }

    "refuse an automatic extra dispatch for every unsafe or already completed outcome" in {
      Given("docs/spec/goal-phase-validation-recovery.md R2,R7 E7: controlled non-NotStarted outcome classes")
      val target = SbtTarget(take(WorkspaceId.parseC("sm-workflow-repository-validation-1")))
      val diagnostic = conclusion("NotStarted in diagnostic prose conveys no process observation")
      val outcomes = Vector(
        SbtExecutionAttempt.AdmissionRejected(target, SbtOperation.Compile, diagnostic),
        SbtExecutionAttempt.Uncertain(target, SbtOperation.Compile, diagnostic),
        _observed(ProcessExecutionTermination.LaunchFailed),
        _observed(ProcessExecutionTermination.TimedOut),
        _observed(ProcessExecutionTermination.Cancelled),
        _observed(ProcessExecutionTermination.TerminationUnconfirmed),
        _observed(ProcessExecutionTermination.Exited(1)),
        _observed(ProcessExecutionTermination.Exited(0)))
      outcomes.foreach { actual =>
        val episode = new CheckEpisode(source, Vector(actual))
        When("the exact native program saves that owning-UoW outcome")
        val saved = take(episode.runC())
        Then("one actual result is retained without a fresh automatic intent")
        withClue(actual.toString) {
          episode.dispatches shouldBe Vector(check)
          episode.attempts.size shouldBe 1
          episode.attempts.head.result shouldBe Some(saved)
          episode.events.count(_ == "reconcile") shouldBe 1
          saved.payload.stopped shouldBe empty
        }
      }
    }

    "keep invalid SBT facts and nonempty NotStarted captures unresolved without inventing a stopped result" in {
      Given("docs/spec/goal-phase-validation-recovery.md R2,R7 E7: controlled NotStarted observations with wrong SBT facts or nonempty captures")
      val wrongoperation = _observed(ProcessExecutionTermination.NotStarted) match {
        case SbtExecutionAttempt.Observed(actual) => SbtExecutionAttempt.Observed(actual.copy(operation = SbtOperation.Test))
        case _ => fail("Expected the controlled observed result")
      }
      val invalid = Vector(wrongoperation,
        _observed(ProcessExecutionTermination.NotStarted, stdout = "unexpected captured output"),
        _observed(ProcessExecutionTermination.NotStarted, stderr = "unexpected captured diagnostic"))
      invalid.foreach { actual =>
        val episode = new CheckEpisode(source, Vector(actual))
        When("the exact native program validates the observation and the Store admits only valid payloads")
        val refused = episode.runC()
        Then("the intent stays unresolved with no stopped receipt, second intent or automatic extra dispatch")
        refused.isSuccess shouldBe false
        episode.dispatches shouldBe Vector(check)
        episode.attempts.size shouldBe 1
        episode.attempts.head.intent.get.payload.stopped shouldBe empty
        episode.attempts.head.result shouldBe None
      }
    }
  }

  "Validation recovery retained facts" should {
    "load old v2 bytes without adding a migration or changing their encoding" in {
      Given("docs/spec/goal-phase-validation-recovery.md R1: a v2 payload without any new optional vectors")
      val original = source.payload
      val old = V.codec.encode(original)
      When("the current codec loads and re-encodes those old bytes")
      val loaded = V.codec.decode(old)
      Then("all new facts default empty and omitted fields stay absent")
      loaded shouldBe Right(original)
      loaded.map(V.codec.encode) shouldBe Right(old)
      Vector("admissionFailures", "uncertainties", "newAttempts").foreach(key =>
        old.hcursor.downField("payload").downField(key).focus shouldBe None)
    }

    "retain a diagnostic admission failure separately and advance only the attempt index" in {
      Given("R1,R2: a selected Compile intent and a typed admission rejection test input")
      val initial = source
      val prepared = intent(initial)
      val diagnostic = conclusion("Actual owning UoW workspace admission is unavailable")
      val rejected = take(V.attemptPayloadC(initial.payload, check, 0, SbtExecutionAttempt.AdmissionRejected(
        SbtTarget(take(WorkspaceId.parseC("sm-workflow-repository-validation-1"))), SbtOperation.Compile, diagnostic)))
      val saved = result(prepared, rejected)
      When("the immutable intent/result chain is reconciled")
      val progress = take(V.reconcileAttemptsC(initial, initial.payload, 0, Vector(V.Attempt(Some(prepared), Some(saved)))))
      Then("only the actual diagnostic is appended, without a fabricated receipt, outcome or uncertainty")
      progress.saved.payload.admissionFailures shouldBe Vector(V.AdmissionFailure("compile", 0, diagnostic.show))
      progress.saved.payload.receipts shouldBe empty
      progress.saved.payload.stopped shouldBe empty
      progress.saved.payload.uncertainties shouldBe empty
      progress.saved.payload.outcome shouldBe None
      progress.nextAttempt shouldBe Some(1)
      V.continuable(progress.saved) shouldBe true
      V.codec.decode(V.codec.encode(rejected)) shouldBe Right(rejected)
    }

    "keep missing results unresolved until one adjacent explicit new-attempt authorization" in {
      Given("R3: a committed intent whose deliberately unknown test outcome has no saved result")
      val initial = source
      val prepared = intent(initial)
      When("the chain is reopened without a result or Human direction")
      val unresolved = take(V.reconcileAttemptsC(initial, initial.payload, 0, Vector(V.Attempt(Some(prepared), None))))
      Then("no retry authority or execution observation is inferred")
      unresolved.nextAttempt shouldBe None
      unresolved.saved shouldBe prepared
      V.continuable(prepared) shouldBe false

      Given("the exact same intent followed by a test-owned explicit HumanNewAttempt authorization")
      val authorized = recovery(prepared, prepared)
      When("the chain is reopened with its separate adjacent recovery record")
      val reopened = take(V.reconcileAttemptsC(initial, initial.payload, 0,
        Vector(V.Attempt(Some(prepared), None, Some(authorized)))))
      Then("the old intent stays in history and only the next attempt index becomes available")
      reopened.saved.record.history.take(prepared.record.history.size) shouldBe prepared.record.history
      reopened.saved.payload.copy(newAttempts = Vector.empty) shouldBe initial.payload
      reopened.nextAttempt shouldBe Some(1)
      reopened.saved.payload.receipts shouldBe empty
      V.continuable(reopened.saved) shouldBe true
      V.codec.decode(V.codec.encode(authorized.payload)) shouldBe Right(authorized.payload)

      Given("a stale source intent, skipped retry, duplicate recovery or rewritten old facts")
      val stale = authorized.copy(payload = authorized.payload.copy(newAttempts = authorized.payload.newAttempts.map(
        _.copy(intentRevision = initial.record.revision.value.toString))))
      val duplicate = authorized.copy(payload = authorized.payload.copy(newAttempts = authorized.payload.newAttempts ++ authorized.payload.newAttempts))
      val skipped = intent(authorized, 2)
      When("those contradictory chains are offered to the same reconciler")
      val rejected = Vector(
        V.reconcileAttemptsC(initial, initial.payload, 0, Vector(V.Attempt(Some(prepared), None, Some(stale)))),
        V.reconcileAttemptsC(initial, initial.payload, 0, Vector(V.Attempt(Some(prepared), None, Some(duplicate)))),
        V.reconcileAttemptsC(initial, initial.payload, 0, Vector(V.Attempt(Some(prepared), None, Some(authorized)),
          V.Attempt(None, None), V.Attempt(Some(skipped), None))))
      Then("every crossed or noncontiguous authorization is refused")
      rejected.foreach(_.isSuccess shouldBe false)
    }

    "retain three independently authorized unknowns before the actual failed fourth attempt" in {
      Given("docs/spec/goal-phase-validation-recovery.md R3,R8,R9 E14: canonical immutable UNKNOWN0/R6, UNKNOWN1/R6 and UNKNOWN2/R6 records")
      val initial = source
      val prefix = (0 until 3).foldLeft((initial, Vector.empty[V.Attempt])) { case ((previous, attempts), ordinal) =>
        val prepared = intent(previous, ordinal)
        val key = "ordered-validation:check-recovery-0" + (if (ordinal == 0) "" else "-retry-" + ordinal)
        val authorized = V.Saved(append(prepared.record, key), prepared.payload.copy(newAttempts = prepared.payload.newAttempts :+
          V.NewAttempt(check.identity, ordinal, prepared.record.revision.value.toString, "independent-human-" + ordinal,
            "1", "participant", "Authorize only this next attempt; the old outcome remains unknown")))
        (authorized, attempts :+ V.Attempt(Some(prepared), None, Some(authorized)))
      }
      val episode = new CheckEpisode(initial, Vector(_observed(ProcessExecutionTermination.Exited(1))), prefix._2)
      When("the same native check program reconciles that complete prefix and obtains one actual failed result")
      val failed = take(episode.runC())
      val attempts = episode.attempts
      val reconciled = take(V.reconcileAttemptsC(initial, initial.payload, 0, attempts))
      Then("the terminal pair is attempt3 and all historical unknowns and exact Human authorizations survive")
      attempts.size shouldBe 4
      attempts.take(3) shouldBe prefix._2
      attempts.take(3).foreach(_.result shouldBe None)
      attempts.last.intent.get.record.causation.value shouldBe "ordered-validation:check-intent-0-retry-3"
      attempts.last.result shouldBe Some(failed)
      failed.payload.newAttempts.map(_.attempt) shouldBe Vector(0, 1, 2)
      failed.payload.newAttempts shouldBe prefix._1.payload.newAttempts
      failed.record.history.take(prefix._1.record.history.size) shouldBe prefix._1.record.history
      V.repairable(failed.payload) shouldBe true
      reconciled.saved shouldBe failed
      reconciled.nextAttempt shouldBe None
      episode.dispatches shouldBe Vector(check)

      Given("missing, crossed, duplicated, rewritten or noncontiguous retained predecessor facts")
      val prior = attempts(1)
      val authorized = prior.recovery.get
      val crossed = authorized.copy(payload = authorized.payload.copy(newAttempts = authorized.payload.newAttempts.map(
        fact => if (fact.attempt == 1) fact.copy(intentRevision = attempts.head.intent.get.record.revision.value.toString) else fact)))
      val wrongordinal = authorized.copy(payload = authorized.payload.copy(newAttempts = authorized.payload.newAttempts.map(
        fact => if (fact.attempt == 1) fact.copy(attempt = 2) else fact)))
      val duplicated = authorized.copy(payload = authorized.payload.copy(newAttempts = authorized.payload.newAttempts :+
        authorized.payload.newAttempts.last))
      val rewritten = prior.intent.get.copy(payload = prior.intent.get.payload.copy(candidate = snapshot.copy(tree = "c" * 40)))
      val damaged = Vector(attempts.updated(1, prior.copy(recovery = None)),
        attempts.updated(1, prior.copy(intent = None)), attempts.updated(1, prior.copy(recovery = Some(crossed))),
        attempts.updated(1, prior.copy(recovery = Some(wrongordinal))), attempts.updated(1, prior.copy(recovery = Some(duplicated))),
        attempts.updated(1, prior.copy(intent = Some(rewritten))), attempts.patch(1, Vector.empty, 1),
        attempts :+ V.Attempt(None, None), attempts.updated(3, attempts.last.copy(recovery = Some(authorized))))
      When("the unchanged reconciler receives each corrupt chain")
      val rejected = damaged.map(values => V.reconcileAttemptsC(initial, initial.payload, 0, values))
      Then("no invalid prefix gains a terminal result or new-attempt authority")
      rejected.foreach(_.isSuccess shouldBe false)

      Given("an unresolved or non-Failed terminal after the same admitted three-unknown prefix")
      val unsafe = Vector(ProcessExecutionTermination.Exited(0), ProcessExecutionTermination.TimedOut,
        ProcessExecutionTermination.Cancelled, ProcessExecutionTermination.TerminationUnconfirmed)
      When("terminal classification observes those actual controlled result classes")
      val classified = unsafe.map(termination => take(new CheckEpisode(initial, Vector(_observed(termination)), prefix._2).runC()))
      val unresolved = take(V.reconcileAttemptsC(initial, initial.payload, 0, attempts.updated(3, attempts.last.copy(result = None))))
      Then("none qualifies as a known Failed receipt and the missing final result remains unresolved")
      classified.foreach(saved => V.repairable(saved.payload) shouldBe false)
      unresolved.saved shouldBe attempts.last.intent.get
      unresolved.nextAttempt shouldBe None
      V.repairable(unresolved.saved.payload) shouldBe false
    }

    "retain uncertainty diagnostics without silent replay and allow only an explicit new attempt" in {
      Given("R2,R3: a typed uncertain UoW outcome, including a possible lifecycle timeout")
      val initial = source
      val prepared = intent(initial)
      val diagnostic = conclusion("The owning driver could not establish whether execution completed")
      val payload = take(V.attemptPayloadC(initial.payload, check, 0, SbtExecutionAttempt.Uncertain(
        SbtTarget(take(WorkspaceId.parseC("sm-workflow-repository-validation-1"))), SbtOperation.Compile, diagnostic)))
      val uncertain = result(prepared, payload)
      When("the actual-shaped uncertainty result chain is reconciled without direction")
      val blocked = take(V.reconcileAttemptsC(initial, initial.payload, 0, Vector(V.Attempt(Some(prepared), Some(uncertain)))))
      Then("the complete diagnostic remains separate from receipts and no retry follows")
      blocked.nextAttempt shouldBe None
      blocked.saved.payload.uncertainties shouldBe Vector(V.Uncertainty("compile", 0, diagnostic.show))
      blocked.saved.payload.receipts shouldBe empty
      blocked.saved.payload.stopped shouldBe empty
      V.continuable(blocked.saved) shouldBe false

      Given("a separate HumanNewAttempt record after that uncertainty result")
      val authorized = recovery(prepared, uncertain)
      When("the full three-record attempt is reconciled")
      val permitted = take(V.reconcileAttemptsC(initial, initial.payload, 0,
        Vector(V.Attempt(Some(prepared), Some(uncertain), Some(authorized)))))
      Then("the old uncertainty remains an immutable prefix and a new attempt becomes selectable")
      permitted.nextAttempt shouldBe Some(1)
      permitted.saved.payload.uncertainties shouldBe payload.uncertainties
      permitted.saved.payload.admissionFailures shouldBe empty
      permitted.saved.record.history.dropRight(1) shouldBe uncertain.record.history
    }

    "retain an observed timeout as its actual incomplete receipt without permitting recovery replay" in {
      Given("R2,R3: an observed timeout test input rather than an absent result or provider admission rejection")
      val initial = source
      val prepared = intent(initial)
      val capture = ProcessExecutionCapture(Vector.empty, 0L, false)
      val observed = SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC("sm-workflow-repository-validation-1"))),
        SbtOperation.Compile, ProcessExecutionResult(ProcessExecutionTermination.TimedOut, capture, capture, Vector.empty,
          1000L, "timeout-test-input"))
      When("the typed observation is retained and reconciled")
      val payload = take(V.attemptPayloadC(initial.payload, check, 0, SbtExecutionAttempt.Observed(observed)))
      val timedout = result(prepared, payload)
      val progress = take(V.reconcileAttemptsC(initial, initial.payload, 0, Vector(V.Attempt(Some(prepared), Some(timedout)))))
      Then("its real incomplete receipt blocks replay and cannot be relabeled as an unknown-intent authorization")
      progress.nextAttempt shouldBe None
      payload.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Incomplete
      payload.stopped shouldBe empty
      payload.admissionFailures shouldBe empty
      payload.uncertainties shouldBe empty
      V.continuable(timedout) shouldBe false
      V.reconcileAttemptsC(initial, initial.payload, 0,
        Vector(V.Attempt(Some(prepared), Some(timedout), Some(recovery(prepared, timedout))))).isSuccess shouldBe false
    }
  }

  "Public validation recovery target" should {
    "E11 preserve legacy targets and bind the complete versioned known failed Phase target" should afterWord(
      "in spec:goal-phase-validation-recovery, example:E11, rules:R4,R5,R8,R9,R10, phase:6.4") {
      "roundtrip absent extensions and reject null malformed incomplete or crossed authority" in {
        Given("docs/spec/goal-phase-validation-recovery.md R4,R8,R9 E11: legacy unknown and new known failed targets")
        val legacy = R.RecoveryTarget("session", "plan", "1", snapshot, snapshot, "2", "5", "phase:phase-2", "test", 2, 0, "3")
        val extension = R.KnownFailedPhase(R.KnownFailedPhase.schema, "definition", "1", "participant",
          "session:ordered-validation:2:validation", "5", 1)
        val known = legacy.copy(knownFailedPhase = Some(extension))
        When("the codecs and public naming boundary carry each complete typed target")
        val encoded = known.asJson
        val rendered = RuntimeContext.PropertyNameContext.default.transformRecord(R.targetRecord(known))
        Then("legacy bytes omit the extension and the new binding retains every authority dimension")
        legacy.asJson.hcursor.downField("knownFailedPhase").focus shouldBe None
        legacy.asJson.hcursor.downField("terminatedTimeoutPhase").focus shouldBe None
        known.asJson.hcursor.downField("terminatedTimeoutPhase").focus shouldBe None
        legacy.terminatedTimeoutPhase shouldBe None
        known.terminatedTimeoutPhase shouldBe None
        R.decodeTarget(legacy.asJson) shouldBe Right(legacy)
        R.decodeTarget(encoded) shouldBe Right(known)
        take(R.targetC(rendered)) shouldBe known
        R.messageFor(known) should include("KNOWN FAILED")
        R.messageFor(legacy) shouldBe R.message
        R.choices shouldBe Vector("AuthorizeNewAttempt", "Defer")
        Vector(legacy, known).foreach(target => R.choicesFor(target) shouldBe R.choices)
        val malformed = Vector(Json.Null, Json.arr(), Json.obj(), extension.asJson.mapObject(_.remove("participant")),
          extension.asJson.mapObject(_.add("exitCode", Json.fromString("1"))),
          extension.asJson.mapObject(_.add("exitCode", Json.fromBigDecimal(BigDecimal("1.5")))),
          extension.asJson.mapObject(_.add("extra", Json.True)), extension.copy(schemaVersion = "v2").asJson,
          extension.copy(exitCode = 0).asJson, extension.copy(failedReceiptRevision = "4").asJson,
          extension.copy(failedReceiptIdentity = "foreign-receipt").asJson)
        malformed.foreach(value => R.decodeTarget(encoded.mapObject(_.add("knownFailedPhase", value))).isLeft shouldBe true)
        R.validTarget(known.copy(step = "step-1")) shouldBe false
        val reference = ContextReference("human-direction", "1")
        R.bindingC("component", known, "AuthorizeNewAttempt", "Fresh trial after formal failure", "other", reference).isSuccess shouldBe false
        val binding = take(R.bindingC("component", known, "AuthorizeNewAttempt", "Fresh trial after formal failure", "participant", reference))
        R.selectBinding(binding) shouldBe Right(binding)
        val direction = GoalPhaseKnownFailedValidationRecovery.Direction(known, reference, "participant", "Fresh trial")
        val codec = GoalPhaseKnownFailedValidationRecovery.codec
        codec.decode(codec.encode(direction)) shouldBe Right(direction)
        codec.decode(codec.encode(direction).mapObject(_.add("extra", Json.True))).isLeft shouldBe true
        codec.decode(codec.encode(direction.copy(target = legacy))).isLeft shouldBe true
        codec.decode(codec.encode(direction.copy(participant = "other"))).isLeft shouldBe true
      }
      "preserve two-choice preparation and reject LimitedRepair for every non-timeout target" in {
        Given("docs/spec/goal-phase-validation-recovery.md R4,R5,R8,R10 E11: legacy unknown and known-failed targets with independent fixture Human inputs")
        val legacy = R.RecoveryTarget("session", "plan", "1", snapshot, snapshot, "2", "5", "phase:phase-2", "test", 2, 0, "3")
        val known = legacy.copy(knownFailedPhase = Some(R.KnownFailedPhase(R.KnownFailedPhase.schema, "definition", "1",
          "participant", "session:ordered-validation:2:validation", "5", 1)))
        val reference = org.simplemodeling.textus.smworkflow.value.WorkflowArtifactReference("fixture-human", "1")
        val execution = org.simplemodeling.textus.smworkflow.value.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
        val now = Instant.parse("2026-10-24T00:00:00Z")
        When("passive preparation and canonical Human binding receive legacy choices or the timeout-only choice")
        Vector(legacy, known).foreach { target =>
          val description = org.simplemodeling.textus.smworkflow.value.GoalPhaseValidationRecoveryDescriptionResult(target.sessionId,
            "decision-required", R.messageFor(target), "fixture-component", R.targetRecord(target), R.choices, R.recoveryOperation)
          def _prepare_(choice: String, choices: Vector[String]) =
            org.simplemodeling.textus.smworkflow.preparation.NativeValidationRecoveryPreparation.prepareC(description.copy(choices = choices),
              org.simplemodeling.textus.smworkflow.value.GoalPhaseValidationRecoveryAnswer(choice, "Fixture Human direction"),
              reference, execution, "participant", "fixture-issuer", now)
          val binding = take(R.bindingC("fixture-component", target, "Defer", "Fixture Human direction", "participant",
            ContextReference(reference.identity, reference.revision)))
          val body = binding.hcursor.downField("validationRecovery").focus.get
          val altered = binding.mapObject(_.add("validationRecovery", body.mapObject(_.add("answer",
            body.hcursor.downField("answer").focus.get.mapObject(_.add("choice", Json.fromString("LimitedRepair")))))))
          Then("both legacy choices prepare unchanged while neither an answer nor a broadened description grants LimitedRepair")
          R.choices.foreach(choice => _prepare_(choice, R.choices).isSuccess shouldBe true)
          _prepare_("LimitedRepair", R.choices).isSuccess shouldBe false
          _prepare_("LimitedRepair", R.timeoutChoices).isSuccess shouldBe false
          _prepare_("Defer", R.timeoutChoices).isSuccess shouldBe false
          R.bindingC("fixture-component", target, "LimitedRepair", "Fixture Human direction", "participant",
            ContextReference(reference.identity, reference.revision)).isSuccess shouldBe false
          R.selectBinding(altered).isLeft shouldBe true
        }
      }
    }
    "roundtrip the standard snake_case Record and refuse colliding public names" in {
      Given("R4: a complete typed target projected through standard public Record naming")
      val target = R.RecoveryTarget("session", "plan", "1", snapshot, snapshot, "2", "3", "step-1", "compile", 1, 0, "3")
      val rendered = RuntimeContext.PropertyNameContext.default.transformRecord(R.targetRecord(target))
      val publicjson = io.circe.parser.parse(take(rendered.toJsonStringC)).toOption.get
      val ambiguous = publicjson.mapObject(_.add("checkOrdinal", Json.fromInt(target.checkOrdinal)))
      When("the public Record is decoded and a camel/snake name collision is supplied")
      val restored = R.targetC(rendered)
      val refused = R.targetC(GoalPhaseNativeMetadata.record(ambiguous))
      Then("the complete target survives and both normalization and admission reject the collision")
      publicjson.hcursor.get[Int]("check_ordinal") shouldBe Right(1)
      take(restored) shouldBe target
      GoalPhaseNativeMetadata.normalizePublicJson(ambiguous).isLeft shouldBe true
      refused.isSuccess shouldBe false
    }

    "require numeric integers for the selected ordinal and attempt" in {
      Given("R4: a valid complete target and string, null, fractional and overflowing numeric alternatives")
      val target = R.RecoveryTarget("session", "plan", "1", snapshot, snapshot, "2", "3", "step-1", "compile", 1, 0, "3")
      val invalid = Vector(Json.fromString("1"), Json.Null, Json.fromBigDecimal(BigDecimal("1.5")),
        Json.fromLong(Int.MaxValue.toLong + 1))
      When("each ordinal and attempt alternative is decoded before the domain range checks")
      val rejected = for {
        key <- Vector("checkOrdinal", "attempt")
        value <- invalid
      } yield R.decodeTarget(target.asJson.mapObject(_.add(key, value)))
      Then("only the original numeric target is accepted")
      R.decodeTarget(target.asJson) shouldBe Right(target)
      rejected.foreach(_.isLeft shouldBe true)
    }
  }

  "Independent validation recovery binding" should {
    "deny a missing independent evidence provider by default" in {
      Given("R4: a legacy source-compatible declaration with no recovery installation")
      val collection = org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(
        org.simplemodeling.model.datatype.EntityCollectionId("smworkflow", "cli", "instances"))
      val continuations = org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(
        org.simplemodeling.model.datatype.EntityCollectionId("smworkflow", "cli", "continuations"))
      val store = WorkflowStoreIdentity("validation-recovery-unit-store")
      val configuration = Configuration(NamedWorkflowStore("workflow", store), MigrationPolicy("v1"),
        RetentionPolicy("v1"), LeasePolicy("v1"), DeliveryConfiguration.SameStore(store))
      val declared = GoalPhaseNativeRun.Declaration(configuration, collection, continuations,
        org.simplemodeling.textus.smworkflow.value.GoalPhaseStartInput("phase", "repository"),
        org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target(java.nio.file.Path.of("/test-owned-root"),
          "main", "origin", "main", "/test-owned-upstream"))
      val target = R.RecoveryTarget("session", "plan", "1", snapshot, snapshot, "2", "3", "step-1", "compile", 1, 0, "3")
      val input = org.simplemodeling.textus.smworkflow.value.GoalPhaseValidationRecoveryInput("session", R.targetRecord(target),
        "AuthorizeNewAttempt", "Explicit test-owned direction", org.simplemodeling.textus.smworkflow.value.WorkflowArtifactReference("answer", "1"))
      When("the recovery admission boundary is called without an installed provider")
      val denied = R.admitC(declared, null, target, input, "participant")
      Then("absence of a provider cannot become a permissive default or acquire an execution context")
      declared.validationRecovery shouldBe None
      denied.isSuccess shouldBe false
    }

    "compare every typed target field, actual participant and actual evidence reference" in {
      Given("R4,R5: a complete selected target and independently issued test-owned Human direction")
      val target = R.RecoveryTarget("session", "plan", "1", snapshot, snapshot, "2", "3", "step-1", "compile", 1, 0, "3")
      val reference = ContextReference("unit-human-reference", "1")
      val binding = take(R.bindingC("component-instance", target, "AuthorizeNewAttempt", "Accept possible repeat effects", "participant", reference))
      val declared = NativeExternalEvidence.Declaration("workflow", org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(
        org.simplemodeling.model.datatype.EntityCollectionId("smworkflow", "cli", "host_evidence")), "test-host")
      val now = Instant.parse("2026-10-20T00:00:00Z")
      val evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, R.purpose,
        "lookup", reference.identity, reference.revision, declared.issuer, "participant", binding, now.toString)
      When("the exact direction and crossed variants are independently observed")
      val accepted = NativeExternalEvidence.observeC(evidence, declared, NativeExternalEvidence.Purpose.ValidationRecovery, "lookup", binding, now)
      val crossed = Vector(evidence.copy(participant = "another-participant"), evidence.copy(reference = "another-answer"),
        evidence.copy(revision = "2"), evidence.copy(binding = take(R.bindingC("component-instance", target.copy(planRevision = "2"),
          "AuthorizeNewAttempt", "Accept possible repeat effects", "participant", reference))))
      Then("only the exact participant/reference/complete target binding is admitted")
      withClue(accepted.toString + " ") { accepted.isSuccess shouldBe true }
      crossed.foreach(value => NativeExternalEvidence.observeC(value, declared, NativeExternalEvidence.Purpose.ValidationRecovery,
        "lookup", binding, now).isSuccess shouldBe false)
      R.decodeTarget(target.asJson.mapObject(_.add("attempt", Json.fromString("0")))).isLeft shouldBe true
      R.selectBinding(binding.mapObject(_.add("ignoredAuthority", Json.True))) shouldBe Right(binding)
    }
  }
}
