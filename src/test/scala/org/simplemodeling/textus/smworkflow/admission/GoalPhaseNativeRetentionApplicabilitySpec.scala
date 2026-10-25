package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseNativeCandidate as C
import GoalPhaseNativeValidation as V
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as S
import GoalPhaseNativeClosure as K
import GoalPhaseNativeRepositoryClosure as L
import RepositorySyncNativeConvergence as R
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeProgressStore as N
import SplitPhaseExplicitPartitionFixture.take

/** Native saved inputs are scoped facts, not complete host-declaration witnesses.
  * @since   Oct.  8, 2026
  * @version Oct. 25, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseNativeRetentionApplicabilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val candidate = O.Snapshot("a" * 40, "b" * 40)
  private def candidateJson = Json.obj("head" -> Json.fromString(candidate.head), "tree" -> Json.fromString(candidate.tree))
  private def payload(value: Json) = value.hcursor.downField("payload").focus.get
  private def replace(value: Json, nested: Json) = value.mapObject(_.add("payload", nested))
  private def _prefix_frontier: InstanceRecord = {
    val initial = SplitPhaseExplicitPartitionFixture.take(InstanceRecord(InstanceIdentity("prefix-retention"),
      SplitPhaseExplicitPartitionFixture.take(GoalPhaseRuntimeCatalog.loadC()).definition, initialRevision, Lifecycle.NotStarted, None,
      CorrelationReference("prefix-correlation"), CausationReference("prefix-start"),
      None, None, None, Vector.empty).validateC)
    _prefix_append(initial)
  }
  private def _prefix_append(from: InstanceRecord): InstanceRecord =
    SplitPhaseExplicitPartitionFixture.take(from.appendC(from.revision,
      GoalPhaseNativeRun.history(from, "phase.Validation", "prefix-retention-entry")))
  private def _prefix_payload(source: String): V.Payload = V.Payload(source, "S1", candidate,
    Vector(V.Check("build-tests", 1, WorkflowSbtValidation.Kind.Test)), Vector.empty, None)
  private def _prefix_receipt(outcome: WorkflowSbtValidation.Outcome, exit: Int): RepositorySyncNativeMergeValidation.Receipt =
    RepositorySyncNativeMergeValidation.Receipt("build-tests", "exited", Some(exit), "actual-shaped build output", "",
      Some(WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Test, outcome, None, None)))

  private def _attempt_key(role: String, index: Int, ordinal: Int): String =
    "check-" + role + "-" + index + (if (ordinal == 0) "" else "-retry-" + ordinal)
  private def _validation_append(from: InstanceRecord, key: String): InstanceRecord =
    take(from.appendC(from.revision, GoalPhaseNativeRun.history(from, "phase.Validation", "ordered-validation:" + key)))
  private def _attempt_payload(frontier: InstanceRecord): V.Payload = V.Payload(frontier.revision.value.toString, "S1", candidate,
    Vector(V.Check("compile", 1, WorkflowSbtValidation.Kind.Compile), V.Check("selected-test", 2), V.Check("later-test", 3)),
    Vector.empty, None)
  private def _attempt_receipt(check: V.Check, termination: ProcessExecutionTermination): RepositorySyncNativeMergeValidation.Receipt = {
    val operation = check.kind match {
      case WorkflowSbtValidation.Kind.Compile => SbtOperation.Compile
      case WorkflowSbtValidation.Kind.Test => SbtOperation.Test
      case WorkflowSbtValidation.Kind.TestOnly => SbtOperation.TestOnly
    }
    val capture = ProcessExecutionCapture(Vector.empty, 0L, false)
    val result = SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC("sm-workflow-repository-validation-" + check.ordinal))), operation,
      ProcessExecutionResult(termination, capture, capture, Vector.empty, 0L, "actual-shaped-selected-sbt"))
    take(RepositorySyncNativeMergeValidation.sbtReceiptC(check.identity, check.kind, result))
  }
  private def _attempt(previous: V.Saved, index: Int, ordinal: Int,
    receipt: Option[RepositorySyncNativeMergeValidation.Receipt]): V.Attempt = {
    val intent = V.Saved(_validation_append(previous.record, _attempt_key("intent", index, ordinal)), previous.payload)
    val result = receipt.map { actual =>
      val payload = if (actual.sbt.exists(_.outcome == WorkflowSbtValidation.Outcome.NotExecuted))
        previous.payload.copy(stopped = previous.payload.stopped :+ actual)
        else previous.payload.copy(receipts = previous.payload.receipts :+ actual)
      V.Saved(_validation_append(intent.record, _attempt_key("result", index, ordinal)), payload)
    }
    V.Attempt(Some(intent), result)
  }

  "Native candidate retained format" should {
    "read current candidate facts with compatible nested annotations and optional omissions" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R1; Example: E1; a current selected candidate payload")
      val saved = C.Payload("5", "S1", Vector("one.scala"), Vector("one.scala"),
        Some(candidate), Vector("one.scala"), Some("an actual retained diff"))
      val wire = C.codec.encode(saved)
      val nested = payload(wire)
      val annotated = nested.mapObject(_.add("candidate", candidateJson.mapObject(_.add("approve", Json.True))))
      When("the current codec decodes consumed facts with optional omissions and inert nested annotations")
      val actual = C.codec.decode(replace(wire.mapObject(_.add("diagnostics", Json.True)), annotated))
      val optional = C.codec.decode(replace(wire, nested.mapObject(_.remove("candidate").remove("diff"))))
      Then("unused values have no effect on candidate data and absent optional facts remain absent")
      actual shouldBe Right(saved)
      optional shouldBe Right(saved.copy(candidate = None, diff = None))
      nested.asObject.get.keys.toSet should not contain "hostPaths"

      Given("a retired v1 schema or missing/malformed consumed candidate fields")
      val invalid = Vector(wire.mapObject(_.add("schemaVersion", Json.fromString("sm-workflow.goal-phase-native-candidate.v1"))),
        replace(wire, nested.mapObject(_.add("selectedPaths", Json.True))),
        replace(wire, nested.mapObject(_.remove("step"))))
      When("these inputs are decoded without a compatibility reader")
      val rejected = invalid.map(C.codec.decode)
      Then("actual schema/type/required-field contradictions fail")
      rejected.foreach(_.isLeft shouldBe true)
    }
  }

  "Native validation retained format" should {
    "retain completed checks and each stopped attempt before fresh success and later continuation" in {
      Given("a native Validation append chain with completed Compile and a selected Test that is twice confirmed NotStarted")
      val frontier = _prefix_frontier
      val initial = _attempt_payload(frontier)
      val source = V.Saved(frontier, initial)
      val compile = _attempt(source, 0, 0, Some(_attempt_receipt(initial.selected(0), ProcessExecutionTermination.Exited(0))))
      val compiled = take(V.reconcileAttemptsC(source, initial, 0, Vector(compile)))
      val first = _attempt(compiled.saved, 1, 0, Some(_attempt_receipt(initial.selected(1), ProcessExecutionTermination.NotStarted)))
      val firstresult = first.result.get
      val second = _attempt(firstresult, 1, 1, Some(_attempt_receipt(initial.selected(1), ProcessExecutionTermination.NotStarted)))
      val secondresult = second.result.get
      val retry = _attempt(secondresult, 1, 2, Some(_attempt_receipt(initial.selected(1), ProcessExecutionTermination.Exited(0))))
      When("the same pure owner reconciles each exact retained attempt prefix and the subsequent selected check")
      val missing = take(V.reconcileAttemptsC(compiled.saved, initial, 1, Vector(V.Attempt(None, None))))
      val stoppedonce = take(V.reconcileAttemptsC(compiled.saved, initial, 1, Vector(first, V.Attempt(None, None))))
      val stoppedtwice = take(V.reconcileAttemptsC(compiled.saved, initial, 1, Vector(first, second)))
      val completed = take(V.reconcileAttemptsC(compiled.saved, initial, 1, Vector(first, second, retry)))
      val latermissing = take(V.reconcileAttemptsC(completed.saved, initial, 2, Vector.empty))
      val later = _attempt(completed.saved, 2, 0, Some(_attempt_receipt(initial.selected(2), ProcessExecutionTermination.Exited(0))))
      val finished = take(V.reconcileAttemptsC(completed.saved, initial, 2, Vector(later)))
      Then("each stopped result stays terminal while only a fresh suffix becomes eligible and completed receipts are reused unchanged")
      compiled.nextAttempt shouldBe None
      compiled.saved.payload.receipts.map(_.identity) shouldBe Vector("compile")
      missing shouldBe V.CheckProgress(compiled.saved, Some(0))
      stoppedonce shouldBe V.CheckProgress(firstresult, Some(1))
      stoppedtwice shouldBe V.CheckProgress(secondresult, Some(2))
      stoppedonce.saved.payload.receipts shouldBe compiled.saved.payload.receipts
      stoppedtwice.saved.payload.receipts shouldBe compiled.saved.payload.receipts
      stoppedonce.saved.payload.stopped shouldBe Vector(firstresult.payload.stopped.head)
      stoppedtwice.saved.payload.stopped shouldBe Vector(firstresult.payload.stopped.head, secondresult.payload.stopped.last)
      completed.nextAttempt shouldBe None
      completed.saved.payload.receipts shouldBe compiled.saved.payload.receipts ++ retry.result.get.payload.receipts.drop(1)
      completed.saved.payload.receipts.map(_.identity) shouldBe Vector("compile", "selected-test")
      completed.saved.payload.stopped shouldBe stoppedtwice.saved.payload.stopped
      latermissing shouldBe V.CheckProgress(completed.saved, Some(0))
      finished.nextAttempt shouldBe None
      finished.saved.payload.receipts.map(_.identity) shouldBe Vector("compile", "selected-test", "later-test")
      finished.saved.payload.stopped shouldBe stoppedtwice.saved.payload.stopped
      Vector(compiled, stoppedonce, stoppedtwice, completed, finished).foreach { progress =>
        progress.saved.payload.sourceRevision shouldBe initial.sourceRevision
        progress.saved.payload.candidate shouldBe initial.candidate
        progress.saved.payload.selected shouldBe initial.selected
        progress.saved.payload.outcome shouldBe None
        V.continuable(progress.saved) shouldBe true
        V.repairable(progress.saved.payload) shouldBe false
      }
      val stoppedview = NativeWorkflowOperationOutput.goalValidation(stoppedtwice.saved.payload)
      stoppedview.checks.map(_.identity) shouldBe Vector("compile", "selected-test")
      stoppedview.checks.last.termination shouldBe "not-started"
      stoppedview.checks.last.sbt.get.outcome shouldBe "not-executed"
      val completedview = NativeWorkflowOperationOutput.goalValidation(completed.saved.payload)
      completedview.checks.map(_.termination) shouldBe Vector("exited", "exited")
      NativeWorkflowOperationOutput.goalValidation(finished.saved.payload.copy(outcome = Some("passed"))).checks.size shouldBe 3
      firstresult.record.causation.value shouldBe "ordered-validation:check-result-1"
      secondresult.record.causation.value shouldBe "ordered-validation:check-result-1-retry-1"
      retry.result.get.record.causation.value shouldBe "ordered-validation:check-result-1-retry-2"
      finished.saved.record.history.take(firstresult.record.history.size) shouldBe firstresult.record.history
      finished.saved.record.history.take(secondresult.record.history.size) shouldBe secondresult.record.history
    }

    "leave a fresh intent after a stopped result unknown and nonreplayable" in {
      Given("a confirmed stopped first check followed by a new canonical intent with no result")
      val frontier = _prefix_frontier
      val initial = _attempt_payload(frontier)
      val source = V.Saved(frontier, initial)
      val first = _attempt(source, 0, 0, Some(_attempt_receipt(initial.selected.head, ProcessExecutionTermination.NotStarted)))
      val pending = _attempt(first.result.get, 0, 1, None)
      When("the retained owner reconciles that unresolved fresh attempt")
      val progress = take(V.reconcileAttemptsC(source, initial, 0, Vector(first, pending)))
      Then("the exact pending intent is retained without inference, replay eligibility or a continuable result")
      progress shouldBe V.CheckProgress(pending.intent.get, None)
      progress.saved.payload.receipts shouldBe empty
      progress.saved.payload.stopped shouldBe first.result.get.payload.stopped
      V.continuable(progress.saved) shouldBe false
      V.continuable(first.intent.get) shouldBe false
      V.continuable(source) shouldBe false
    }

    "leave launched failure and incomplete results terminal without nonexecution continuation" in {
      Given("canonical first-attempt results for nonzero exit, LaunchFailed and timeout")
      val frontier = _prefix_frontier
      val initial = _attempt_payload(frontier)
      val source = V.Saved(frontier, initial)
      val attempts = Vector(ProcessExecutionTermination.Exited(7), ProcessExecutionTermination.LaunchFailed,
        ProcessExecutionTermination.TimedOut).map(termination =>
          _attempt(source, 0, 0, Some(_attempt_receipt(initial.selected.head, termination))))
      When("the owner reconciles these ordinary terminal results")
      val progress = attempts.map(attempt => take(V.reconcileAttemptsC(source, initial, 0, Vector(attempt))))
      Then("each remains a completed negative receipt with no eligible preparation retry or continuable success prefix")
      progress.foreach { current =>
        current.nextAttempt shouldBe None
        current.saved.payload.receipts.size shouldBe 1
        current.saved.payload.stopped shouldBe empty
        V.continuable(current.saved) shouldBe false
      }
    }

    "reject missing crossed noncontiguous or rewritten attempt facts" in {
      Given("one stopped actual-shaped check pair and a fresh successful retry on its native append history")
      val frontier = _prefix_frontier
      val initial = _attempt_payload(frontier)
      val source = V.Saved(frontier, initial)
      val first = _attempt(source, 0, 0, Some(_attempt_receipt(initial.selected.head, ProcessExecutionTermination.NotStarted)))
      val retry = _attempt(first.result.get, 0, 1, Some(_attempt_receipt(initial.selected.head, ProcessExecutionTermination.Exited(0))))
      val actual = first.result.get
      val receipt = actual.payload.stopped.head
      val crossed = Vector(
        actual.copy(payload = actual.payload.copy(sourceRevision = "999")),
        actual.copy(payload = actual.payload.copy(step = "S2")),
        actual.copy(payload = actual.payload.copy(candidate = O.Snapshot("c" * 40, "d" * 40))),
        actual.copy(payload = actual.payload.copy(selected = initial.selected.updated(0, initial.selected.head.copy(ordinal = 4)))),
        actual.copy(payload = actual.payload.copy(stopped = Vector(receipt.copy(identity = "selected-test")))),
        actual.copy(payload = actual.payload.copy(stopped = Vector(receipt.copy(sbt = Some(receipt.sbt.get.copy(kind = WorkflowSbtValidation.Kind.Test)))))),
        actual.copy(payload = actual.payload.copy(stopped = Vector(receipt, receipt))),
        actual.copy(payload = actual.payload.copy(stopped = Vector.empty)),
        actual.copy(payload = actual.payload.copy(outcome = Some("failed"))),
        actual.copy(record = actual.record.copy(identity = InstanceIdentity("crossed-instance"))),
        actual.copy(record = _validation_append(actual.record, "check-result-0")),
        actual.copy(record = _validation_append(first.intent.get.record, "check-result-1")),
        actual.copy(record = actual.record.copy(history = actual.record.history.updated(0,
          actual.record.history.head.copy(causation = CausationReference("rewritten-history")))))
      )
      val extra = _attempt(retry.result.get, 0, 2, Some(_attempt_receipt(initial.selected.head, ProcessExecutionTermination.Exited(0))))
      val changedintent = first.intent.get.copy(payload = initial.copy(candidate = O.Snapshot("c" * 40, "d" * 40)))
      When("pure reconciliation consumes each crossed pair, omitted intent, gap, duplicate terminal or attempt after completion")
      val rejected = crossed.map(saved => V.reconcileAttemptsC(source, initial, 0, Vector(first.copy(result = Some(saved))))) ++ Vector(
        V.reconcileAttemptsC(source, initial, 0, Vector(V.Attempt(None, first.result))),
        V.reconcileAttemptsC(source, initial, 0, Vector(first.copy(intent = Some(changedintent)))),
        V.reconcileAttemptsC(source, initial, 0, Vector(V.Attempt(None, None), retry)),
        V.reconcileAttemptsC(source, initial, 0, Vector(first, V.Attempt(None, None), retry)),
        V.reconcileAttemptsC(source, initial, 0, Vector(first, first)),
        V.reconcileAttemptsC(source, initial, 0, Vector(first, retry, extra)),
        V.reconcileAttemptsC(source, initial, 0, Vector(first, retry.copy(result = Some(retry.result.get.copy(
          payload = retry.result.get.payload.copy(stopped = Vector.empty)))))),
        V.reconcileAttemptsC(source, initial, 1, Vector(first)))
      Then("structural contradictions cannot supply continuation or replace a retained terminal result")
      rejected.foreach(_.isSuccess shouldBe false)
      V.continuable(actual.copy(record = first.intent.get.record)) shouldBe false
      V.continuable(actual.copy(payload = actual.payload.copy(outcome = Some("failed")))) shouldBe false
      V.continuable(actual.copy(payload = actual.payload.copy(sourceRevision = "unknown"))) shouldBe false
      V.continuable(actual.copy(payload = actual.payload.copy(stopped = Vector.empty))) shouldBe false
    }

    "reject duplicated or replaced completed receipts and changed selection metadata" in {
      Given("a retained successful Compile prefix followed by an ordinary successful Test pair")
      val frontier = _prefix_frontier
      val initial = _attempt_payload(frontier)
      val source = V.Saved(frontier, initial)
      val compile = _attempt(source, 0, 0, Some(_attempt_receipt(initial.selected.head, ProcessExecutionTermination.Exited(0))))
      val compiled = compile.result.get
      val test = _attempt(compiled, 1, 0, Some(_attempt_receipt(initial.selected(1), ProcessExecutionTermination.Exited(0))))
      val completed = test.result.get
      val bad = Vector(
        completed.copy(payload = completed.payload.copy(receipts = Vector(compiled.payload.receipts.head, compiled.payload.receipts.head))),
        completed.copy(payload = completed.payload.copy(receipts = completed.payload.receipts.updated(0,
          completed.payload.receipts.head.copy(stdout = "rewritten diagnostic")))),
        completed.copy(payload = completed.payload.copy(selected = initial.selected.updated(1,
          initial.selected(1).copy(hostIdentity = Some("foreign-host"))))),
        completed.copy(payload = completed.payload.copy(receipts = compiled.payload.receipts)),
        completed.copy(payload = completed.payload.copy(stopped = Vector(
          _attempt_receipt(initial.selected(1), ProcessExecutionTermination.NotStarted))))
      )
      When("the selected owner reconciles each purported ordinary result against its exact prior payload")
      val rejected = bad.map(saved => V.reconcileAttemptsC(compiled, initial, 1, Vector(test.copy(result = Some(saved)))))
      Then("completed facts and metadata remain immutable and one result cannot add both stopped and completed receipts")
      rejected.foreach(_.isSuccess shouldBe false)
    }

    "retain both receipt histories across actual metadata selection and reject changed batches" in {
      Given("a stopped then successful ordinary Compile prefix and producer-discovered metadata selected at its next native successor")
      val frontier = _prefix_frontier
      val initial = _attempt_payload(frontier).copy(selected = Vector(V.Check("compile", 1, WorkflowSbtValidation.Kind.Compile)))
      val source = V.Saved(frontier, initial)
      val stopped = _attempt(source, 0, 0, Some(_attempt_receipt(initial.selected.head, ProcessExecutionTermination.NotStarted)))
      val success = _attempt(stopped.result.get, 0, 1, Some(_attempt_receipt(initial.selected.head, ProcessExecutionTermination.Exited(0))))
      val compiled = take(V.reconcileAttemptsC(source, initial, 0, Vector(stopped, success))).saved
      val discovered = GoalPhaseNativeMetadataFixture.prepared
      val selected = compiled.payload.copy(selected = initial.selected ++ discovered._1, metadata = discovered._2)
      val selection = V.Saved(_validation_append(compiled.record, "selection"), selected)
      val attempt = _attempt(selection, 1, 0, Some(_attempt_receipt(selected.selected(1), ProcessExecutionTermination.NotStarted)))
      val changed = attempt.result.get.copy(payload = attempt.result.get.payload.copy(
        metadata = discovered._2.map(_.copy(checkIdentity = "crossed-group"))))
      When("the same reconciliation semantics consume the metadata attempt and a changed-batch alternative")
      val progress = take(V.reconcileAttemptsC(selection, selected, 1, Vector(attempt)))
      val rejected = V.reconcileAttemptsC(selection, selected, 1, Vector(attempt.copy(result = Some(changed))))
      Then("selection keeps completed and stopped ordinary facts and only the exact metadata batch can continue")
      selection.record.revision.value shouldBe compiled.record.revision.value + 1
      selection.record.history.dropRight(1) shouldBe compiled.record.history
      progress.nextAttempt shouldBe Some(1)
      progress.saved.payload.receipts shouldBe compiled.payload.receipts
      progress.saved.payload.stopped.dropRight(1) shouldBe compiled.payload.stopped
      progress.saved.payload.metadata shouldBe discovered._2
      progress.saved.payload.sourceRevision shouldBe initial.sourceRevision
      progress.saved.payload.candidate shouldBe initial.candidate
      V.codec.decode(V.codec.encode(progress.saved.payload)) shouldBe Right(progress.saved.payload)
      rejected.isSuccess shouldBe false
    }

    "roundtrip stopped history while rejecting explicit null malformed or ahead-of-prefix stopped facts" in {
      Given("a current v2 native payload with two actual-shaped stopped attempts for the current Compile check")
      val frontier = _prefix_frontier
      val initial = _attempt_payload(frontier)
      val receipt = _attempt_receipt(initial.selected.head, ProcessExecutionTermination.NotStarted)
      val original = initial.copy(stopped = Vector(receipt, receipt))
      val wire = V.codec.encode(original)
      val nested = payload(wire)
      val emptywire = V.codec.encode(initial)
      val process = receipt.processObservation.get
      val invented = "invented capture".getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
      When("the existing v2 codec reads stopped history, absent optional fields and contradictory stopped shapes")
      val restored = V.codec.decode(wire)
      val absent = V.codec.decode(replace(emptywire, payload(emptywire).mapObject(_.remove("stopped"))))
      val malformed = Vector("null history" -> Json.Null, "boolean history" -> Json.True,
        "boolean receipt" -> Json.arr(Json.True), "empty receipt" -> Json.arr(Json.obj()),
        "ahead of prefix" -> Json.arr(_attempt_receipt(initial.selected(1), ProcessExecutionTermination.NotStarted).asJson),
        "exited process with NotExecuted facts" -> Json.arr(receipt.copy(processObservation = Some(
          process.copy(termination = ProcessExecutionTermination.Exited(0)))).asJson),
        "NotStarted process with captured stdout" -> Json.arr(receipt.copy(processObservation = Some(
          process.copy(stdout = ProcessExecutionCapture(invented, invented.size.toLong, false)))).asJson),
        "missing sbt facts" -> Json.arr(receipt.asJson.mapObject(_.remove("sbt"))),
        "failed facts with NotStarted process" -> Json.arr(receipt.copy(sbt = Some(
          receipt.sbt.get.copy(outcome = WorkflowSbtValidation.Outcome.Failed))).asJson))
      val rejected = malformed.map { (name, value) =>
        val malformedwire = replace(wire, nested.mapObject(_.add("stopped", value)))
        (name, malformedwire, V.codec.decode(malformedwire))
      }
      Then("old absent fields decode empty, every actual stopped fact survives and explicit contradictions fail")
      restored shouldBe Right(original)
      absent shouldBe Right(initial)
      payload(emptywire).hcursor.downField("stopped").focus shouldBe None
      rejected.zipWithIndex.foreach { case ((name, malformedwire, result), index) =>
        withClue("Malformed stopped history " + index + " (" + name + "): ") {
          malformedwire should not be wire
          result.isLeft shouldBe true
        }
      }
    }

    "roundtrip only the actual ordinary prefix result without inventing future metadata selection" in {
      Given("a current v2 typed failed Test prefix with actual source, Step and candidate but no discovered metadata")
      val original = _prefix_payload("8").copy(
        receipts = Vector(_prefix_receipt(WorkflowSbtValidation.Outcome.Failed, 1)), outcome = Some("repair-required"))
      val wire = V.codec.encode(original)
      val nested = payload(wire)
      When("the unchanged current validation codec retains that performed prefix")
      val restored = V.codec.decode(wire)
      Then("only the performed check and raw receipt survive with their exact source and candidate")
      restored shouldBe Right(original)
      restored.toOption.get.selected.map(_.identity) shouldBe Vector("build-tests")
      restored.toOption.get.receipts shouldBe original.receipts
      restored.toOption.get.sourceRevision shouldBe "8"
      restored.toOption.get.step shouldBe "S1"
      restored.toOption.get.candidate shouldBe candidate
      restored.toOption.get.metadata shouldBe empty
      nested.hcursor.downField("metadata").focus shouldBe None
      wire.hcursor.get[String]("schemaVersion").toOption shouldBe Some("sm-workflow.goal-phase-native-validation.v2")

      Given("the prefix wire with missing or malformed consumed identities, candidate, receipt kind or source fields")
      val check = nested.hcursor.downField("selected").focus.get.asArray.get.head
      val receipt = nested.hcursor.downField("receipts").focus.get.asArray.get.head
      val facts = receipt.hcursor.downField("sbt").focus.get
      def _with_check_(value: Json): Json = nested.mapObject(_.add("selected", Json.arr(value)))
      def _with_receipt_(value: Json): Json = nested.mapObject(_.add("receipts", Json.arr(value)))
      val invalid = Vector(nested.mapObject(_.remove("sourceRevision")), nested.mapObject(_.add("sourceRevision", Json.True)),
        nested.mapObject(_.remove("step")), nested.mapObject(_.add("step", Json.True)),
        nested.mapObject(_.add("candidate", Json.True)),
        nested.mapObject(_.add("candidate", candidateJson.mapObject(_.add("head", Json.fromString("wrong-head"))))),
        _with_check_(check.mapObject(_.add("identity", Json.fromString("wrong check")))),
        _with_check_(check.mapObject(_.add("kind", Json.True))),
        _with_receipt_(receipt.mapObject(_.add("identity", Json.fromString("another-check")))),
        _with_receipt_(receipt.mapObject(_.add("sbt", facts.mapObject(_.add("kind", Json.fromString("compile")))))))
      When("the current typed codec consumes each contradiction")
      val rejected = invalid.map(value => V.codec.decode(replace(wire, value)))
      Then("prefix retention does not loosen required source fields, typed kinds or exact receipt identity")
      rejected.foreach(_.isLeft shouldBe true)
    }

    "reconcile the actual contiguous prefix pair and leave an intent without result pending" in {
      Given("a Validation frontier and two genuine native append entries for one ordinary check")
      val frontier = _prefix_frontier
      val initial = _prefix_payload(frontier.revision.value.toString)
      val intent = V.Saved(_prefix_append(frontier), initial)
      val result = V.Saved(_prefix_append(intent.record), initial.copy(
        receipts = Vector(_prefix_receipt(WorkflowSbtValidation.Outcome.Succeeded, 0))))
      When("pure reconciliation consumes the saved pair and separately the pending intent")
      val complete = V.reconcilePrefixC(frontier, initial, Vector((Some(intent), Some(result))), true)
      val pending = V.reconcilePrefixC(frontier, initial, Vector((Some(intent), None)), false)
      val pendingcomplete = V.reconcilePrefixC(frontier, initial, Vector((Some(intent), None)), true)
      Then("the exact last result becomes the discovery source while an unknown result cannot complete the prefix")
      SplitPhaseExplicitPartitionFixture.take(complete) shouldBe result
      SplitPhaseExplicitPartitionFixture.take(pending) shouldBe intent
      pendingcomplete.isSuccess shouldBe false
      result.record.revision.value shouldBe frontier.revision.value + 2
      result.payload.receipts.size shouldBe 1
    }

    "refuse missing crossed or failed prefix records as the source of metadata selection" in {
      Given("an actual-shaped successful ordinary pair and malformed retained alternatives")
      val frontier = _prefix_frontier
      val initial = _prefix_payload(frontier.revision.value.toString)
      val intent = V.Saved(_prefix_append(frontier), initial)
      val result = V.Saved(_prefix_append(intent.record), initial.copy(
        receipts = Vector(_prefix_receipt(WorkflowSbtValidation.Outcome.Succeeded, 0))))
      val failed = result.copy(payload = result.payload.copy(receipts = Vector(_prefix_receipt(WorkflowSbtValidation.Outcome.Failed, 1))))
      val crossed = Vector(result.copy(payload = result.payload.copy(sourceRevision = "999")),
        result.copy(payload = result.payload.copy(step = "S2")),
        result.copy(payload = result.payload.copy(candidate = O.Snapshot("c" * 40, "d" * 40))),
        result.copy(payload = result.payload.copy(selected = Vector(V.Check("other", 1)))),
        result.copy(payload = result.payload.copy(receipts = Vector(result.payload.receipts.head.copy(identity = "other")))),
        result.copy(record = result.record.copy(identity = InstanceIdentity("foreign-prefix"))),
        result.copy(record = intent.record),
        result.copy(record = _prefix_append(result.record)),
        result.copy(record = result.record.copy(currentProgression = Some(ProgressionReference("phase.Repair")))))
      When("selection reconciliation requires the actual complete successful prefix")
      val rejected = crossed.map(saved => V.reconcilePrefixC(frontier, initial, Vector((Some(intent), Some(saved))), true)) ++
        Vector(V.reconcilePrefixC(frontier, initial, Vector((None, Some(result))), true),
          V.reconcilePrefixC(frontier, initial, Vector((None, None)), true),
          V.reconcilePrefixC(frontier, initial, Vector((Some(intent), Some(failed))), true))
      Then("no missing pair, substituted fact, record discontinuity or failed build can authorize discovery")
      rejected.foreach(_.isSuccess shouldBe false)

      Given("the same actual failed result before any metadata selection exists")
      When("reconciliation retains the performed prefix without requiring successful completion")
      val stopped = V.reconcilePrefixC(frontier, initial, Vector((Some(intent), Some(failed))), false)
      Then("the exact failed result remains available for the ordinary failure disposition without discovery")
      SplitPhaseExplicitPartitionFixture.take(stopped) shouldBe failed

      Given("a pending first intent followed by an invented later ordinary result")
      val secondcheck = V.Check("second-build", 2, WorkflowSbtValidation.Kind.Test)
      val two = initial.copy(selected = initial.selected :+ secondcheck)
      val firstintent = intent.copy(payload = two)
      val later = result.copy(payload = two.copy(receipts = result.payload.receipts))
      When("pure reconciliation sees that noncontiguous prefix")
      val noncontiguous = V.reconcilePrefixC(frontier, two,
        Vector((Some(firstintent), None), (Some(later), Some(later))), false)
      Then("an outstanding intent stops the actual prefix without inferring its result")
      noncontiguous.isSuccess shouldBe false
    }

    "read current check results without policy copies or nested key preflights" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R2; Example: E2; actual selected check bindings and result facts")
      val saved = V.Payload("8", "S1", candidate, Vector(V.Check("focused", 1, WorkflowSbtValidation.Kind.Test)),
        Vector(_prefix_receipt(WorkflowSbtValidation.Outcome.Succeeded, 0).copy(identity = "focused")), Some("passed"))
      val wire = V.codec.encode(saved)
      val nested = payload(wire)
      val check = nested.hcursor.downField("selected").focus.get.asArray.get.head
      val receipt = nested.hcursor.downField("receipts").focus.get.asArray.get.head
      val annotated = nested.mapObject(_.add("approve", Json.True)
        .add("selected", Json.arr(check.mapObject(_.add("command", Json.fromString("must-not-execute")))))
        .add("receipts", Json.arr(receipt.mapObject(_.add("authority", Json.True))))
        .add("candidate", candidateJson.mapObject(_.add("approve", Json.True))))
      When("current results decode with unused annotations and an omitted optional outcome")
      val actual = V.codec.decode(replace(wire, annotated))
      val optional = V.codec.decode(replace(wire, nested.mapObject(_.remove("outcome"))))
      Then("only typed check result facts are returned; added authority/command values are inert")
      actual shouldBe Right(saved)
      optional shouldBe Right(saved.copy(outcome = None))
      nested.asObject.get.keys.toSet should not contain "policy"

      Given("retired v1 validation data or a missing consumed source revision")
      val invalid = Vector(wire.mapObject(_.add("schemaVersion", Json.fromString("sm-workflow.goal-phase-native-validation.v1"))),
        replace(wire, nested.mapObject(_.remove("sourceRevision"))))
      When("the current-only codec consumes them")
      val rejected = invalid.map(V.codec.decode)
      Then("no policy extraction or old-format adapter is selected")
      rejected.foreach(_.isLeft shouldBe true)
    }
  }

  private def workInput = {
    val plan = O.Plan("selected-plan", "1", "PHASE-2", "repository",
      Vector(O.Step("S1", "Selected semantics", Vector("one.scala"), Vector.empty, Vector("focused"))), Vector("whole-phase"))
    S.Request("owned-instance", "11", "ReviewStep", Some("S1"), 1, "Original review meaning", plan,
      O.Snapshot("c" * 40, "d" * 40), Vector.empty, Some(candidate), Some("saved-checks"), None,
      Some(GoalPhaseNativeReview.Context(Vector("one.scala"), "original diff",
        Vector(RepositorySyncNativeMergeValidation.Receipt("focused", "exited", Some(0), "", "")))))
  }
  "Native retained review context" should {
    "roundtrip actual nonempty prior review reports through the existing assigned Request codec" in {
      Given("Spec: docs/spec/native-goal-command-task.md; a current Request carrying an original Step report with concrete findings and observations")
      val prior = GoalPhaseNativeReview.Prior("ReviewStep", Some("S1"), 1, candidate, "original-checks",
        "original-result", "8", S.Result("repair", "Original independent-review rationale", Vector("Original affected finding"),
          reviewObservations = Some(GoalPhaseFindingRecording.Observations(Vector(
            GoalPhaseFindingRecording.Observation("prior-hygiene", "HYGIENE", "Original observation", "Original supporting detail"))))))
      val original = workInput.copy(review = workInput.review.map(_.copy(priorReviews = Vector(prior))))
      When("the actual current Request codec writes and reads the complete review context")
      val wire = S.inputCodec.encode(original)
      val actual = S.inputCodec.decode(wire)
      Then("the exact prior target, references, report, findings and optional observations survive without a new result schema")
      actual shouldBe Right(original)
      val retained = actual.toOption.get.review.get.priorReviews.head
      retained shouldBe prior
      retained.result.findings shouldBe Vector("Original affected finding")
      payload(wire).hcursor.downField("review").get[Vector[Json]]("priorReviews") shouldBe Right(Vector(prior.asJson))
      wire.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-native-work-request.v2")
    }

    "default only absent prior vectors and reject explicit null or malformed retained reports" in {
      Given("Spec: docs/spec/native-goal-command-task.md; an already issued current Request with no prior review field")
      val original = workInput
      val wire = S.inputCodec.encode(original)
      val nested = payload(wire)
      val review = nested.hcursor.downField("review").focus.get
      When("the current Request codec reads absent and explicitly empty prior review vectors")
      val absent = S.inputCodec.decode(wire)
      val empty = S.inputCodec.decode(replace(wire, nested.mapObject(_.add("review",
        review.mapObject(_.add("priorReviews", Json.arr()))))))
      Then("only genuine absence defaults to empty, and an actual empty vector remains empty")
      absent shouldBe Right(original)
      empty shouldBe Right(original)
      absent.toOption.get.review.get.priorReviews shouldBe Vector.empty
      review.hcursor.downField("priorReviews").focus shouldBe None

      val prior = GoalPhaseNativeReview.Prior("ReviewStep", Some("S1"), 1, candidate, "original-checks",
        "original-result", "8", S.Result("accept", "Original accepted rationale", Vector.empty)).asJson
      val result = prior.hcursor.downField("result").focus.get
      val malformed = Vector(Json.Null, Json.True, Json.obj(), Json.arr(Json.Null), Json.arr(Json.obj()),
        Json.arr(prior.mapObject(_.remove("result"))), Json.arr(prior.mapObject(_.add("result", Json.Null))),
        Json.arr(prior.mapObject(_.add("result", Json.True))), Json.arr(prior.mapObject(_.add("result", Json.obj()))),
        Json.arr(prior.mapObject(_.add("result", result.mapObject(_.add("outcome", Json.fromString("completed")))))),
        Json.arr(prior.mapObject(_.add("result", result.mapObject(_.add("findings", Json.Null))))),
        Json.arr(prior.mapObject(_.add("result", result.mapObject(_.add("reviewObservations", Json.Null))))),
        Json.arr(prior.mapObject(_.add("generation", Json.fromLong(0)))),
        Json.arr(prior.mapObject(_.add("step", Json.Null))),
        Json.arr(prior.mapObject(_.add("candidate", Json.Null))),
        Json.arr(prior.mapObject(_.add("checksReceipt", Json.fromString("")))),
        Json.arr(prior.mapObject(_.add("resultRevision", Json.fromString("01")))), Json.arr(prior, prior))
      malformed.zipWithIndex.foreach { (value, index) =>
        Given("an explicit invalid prior vector/report case " + index)
        When("the current Request codec consumes that retained value")
        val actual = S.inputCodec.decode(replace(wire, nested.mapObject(_.add("review",
          review.mapObject(_.add("priorReviews", value))))))
        Then("nulls, invalid shapes, results, references and duplicate keys refuse without absence normalization")
        actual.isLeft shouldBe true
      }
    }

    "consume current review meaning and ignore inert policy markers" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R3; Example: E7; a current complete assigned review")
      val original = workInput
      val wire = S.inputCodec.encode(original)
      val body = payload(wire)
      val review = body.hcursor.downField("review").focus.get
      val annotated = review.mapObject(_.add("policyIdentity", Json.True)
        .add("policyRevision", Json.Null).add("grant", Json.True))
      When("current typed work decodes the review with inert former policy/permission annotations")
      val actual = S.inputCodec.decode(replace(wire, body.mapObject(_.add("review", annotated))))
      Then("only the genuine review data is consumed, without added execution permission")
      actual shouldBe Right(original)
      review.asObject.get.keys.toSet shouldBe Set("changedPaths", "diff", "checks")

      Given("a missing consumed review member or a retired v1 request schema")
      val invalid = Vector(replace(wire, body.mapObject(_.add("review", review.mapObject(_.remove("changedPaths"))))),
        wire.mapObject(_.add("schemaVersion", Json.fromString("sm-workflow.goal-phase-native-work-request.v1"))))
      When("the current request codec decodes these inputs")
      val rejected = invalid.map(S.inputCodec.decode)
      Then("actual consumed-field/schema contradictions fail without legacy normalization")
      rejected.foreach(_.isLeft shouldBe true)
    }
  }
  "Native assigned work retention" should {
    "read current optional work facts and ignore unused nested controls" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R3; Example: E5; a current typed implementation request")
      val original = workInput.copy(kind = "ImplementStep", candidate = None, checksReceipt = None, review = None)
      val wire = S.inputCodec.encode(original)
      val nested = payload(wire)
      val plan = nested.hcursor.downField("selectedPlan").focus.get
      val step = plan.hcursor.downField("steps").focus.get.asArray.get.head
      val optional = nested.mapObject(_.add("review", Json.Null).add("decisionContext", Json.Null)
        .add("selectedPlan", plan.mapObject(_.add("phaseReviewClassification", Json.Null)
          .add("steps", Json.arr(step.mapObject(_.add("workClassification", Json.Null)))))))
      val annotated = optional.mapObject(_.add("command", Json.fromString("must-not-execute"))
        .add("selectedPlan", plan.mapObject(_.add("approve", Json.True)
          .add("steps", Json.arr(step.mapObject(_.add("run", Json.True))))))
        .add("originalBase", nested.hcursor.downField("originalBase").focus.get.mapObject(_.add("authority", Json.True))))
      When("the current codec decodes optional absence and unused nested annotations")
      val decodedOptional = S.inputCodec.decode(replace(wire, optional))
      val decodedAnnotated = S.inputCodec.decode(replace(wire, annotated))
      Then("only actual business work facts are returned and unused commands grant nothing")
      decodedOptional shouldBe Right(original)
      decodedAnnotated shouldBe Right(original)

      Given("an actual semantic result and a request missing its consumed kind")
      val result = S.Result("completed", "Actual semantic result", Vector.empty)
      val resultWire = S.resultCodec.encode(result)
      When("the result codec reads current v1 with inert annotations and the request codec reads missing kind")
      val decodedResult = S.resultCodec.decode(replace(resultWire, payload(resultWire).mapObject(_.add("command", Json.True))))
      val missingKind = S.inputCodec.decode(replace(wire, nested.mapObject(_.remove("kind"))))
      Then("current result.v1 remains supported while absent actual request data fails")
      decodedResult shouldBe Right(result)
      missingKind.isLeft shouldBe true
    }
  }

  "Native Start retained format" should {
    "consume current Start facts without a command copy or unused-field refusals" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R4; Example: E9; participant, Git target and actual Plan/base facts")
      val input = workInput
      val saved = GoalPhaseNativeRun.Payload("participant", "/selected/repository", "main", input.selectedPlan, input.originalBase)
      When("the current native Start codec writes and reads these consumed facts")
      val wire = GoalPhaseNativeRun.codec.encode(saved)
      val decoded = GoalPhaseNativeRun.codec.decode(wire)
      Then("the current record needs no saved command to retain its participant, target and controller input")
      decoded shouldBe Right(saved)
      val nested = payload(wire)
      nested.asObject.get.keys.toSet should not contain "originalCommand"

      Given("the current Plan's classification is optional and diagnostics do not supply executable authority")
      val plan = nested.hcursor.downField("plan").focus.get
      val step = plan.hcursor.downField("steps").focus.get.asArray.get.head
      val unclassified = plan.mapObject(_.remove("phaseReviewClassification").add("steps",
        Json.arr(step.mapObject(_.remove("workClassification")))))
      val diagnostic = nested.mapObject(_.add("originalCommand", Json.True)
        .add("approval", Json.True).add("plan", plan.mapObject(_.add("command", Json.fromString("git"))))
        .add("base", nested.hcursor.downField("base").focus.get.mapObject(_.add("run", Json.True))))
      When("optional classification is omitted or unused diagnostic fields are present")
      val optional = GoalPhaseNativeRun.codec.decode(replace(wire, nested.mapObject(_.add("plan", unclassified))))
      val ignored = GoalPhaseNativeRun.codec.decode(replace(wire, diagnostic).mapObject(_.add("authority", Json.True)))
      Then("the same consumed business facts decode; obsolete command and diagnostic fields have no meaning")
      optional shouldBe Right(saved)
      ignored shouldBe Right(saved)

      Given("required participant, target and controller facts must have their actual consumed types")
      val invalid = Vector(nested.mapObject(_.remove("participantIdentity")),
        nested.mapObject(_.add("root", Json.True)), nested.mapObject(_.add("plan", Json.True)),
        nested.mapObject(_.remove("base")))
      When("current records cannot supply those required values, or select the removed v1 schema")
      val malformed = invalid.map(item => GoalPhaseNativeRun.codec.decode(replace(wire, item)))
      val oldSchema = GoalPhaseNativeRun.codec.decode(wire.mapObject(_.add("schemaVersion",
        Json.fromString("sm-workflow.goal-phase-native-start.v1"))))
      Then("typed consumed decoding fails and no old reader or compatibility fallback is selected")
      malformed.foreach(_.isLeft shouldBe true)
      oldSchema.isLeft shouldBe true
    }
  }

  "Native Step closure retained format" should {
    "retain current business commit facts without copied authority or policy witnesses" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R5; Example: E10; actual commit intent and result facts")
      val work = workInput
      val input = K.Input(work.instanceIdentity, work.sourceRevision, work.selectedPlan.identity,
        work.selectedPlan.revision, "S1", 1, work.originalBase, candidate, "review-result")
      val saved = K.Payload(input, 1, Some(O.Commit("e" * 40, work.originalBase.head, candidate.tree)))
      val wire = K.codec.encode(saved)
      val nested = payload(wire)
      val target = nested.hcursor.downField("input").focus.get
      val commit = nested.hcursor.downField("commit").focus.get
      val annotated = nested.mapObject(_.add("authority", Json.True).add("authorityRevision", Json.True)
        .add("input", target.mapObject(_.add("approval", Json.True)))
        .add("commit", commit.mapObject(_.add("run", Json.True))))
      When("current v3 reads typed facts, optional commit absence and inert annotations")
      val actual = K.codec.decode(replace(wire, annotated))
      val optional = K.codec.decode(replace(wire, nested.mapObject(_.remove("commit"))))
      Then("saved data contains no permission certificate and unused values do not change commit facts")
      actual shouldBe Right(saved)
      optional shouldBe Right(saved.copy(commit = None))
      nested.asObject.get.keys.toSet shouldBe Set("input", "ordinal", "commit")

      Given("retired v1/v2 rows or a malformed consumed ordinal")
      val invalid = Vector("v1", "v2").map(version => wire.mapObject(_.add("schemaVersion",
        Json.fromString("sm-workflow.goal-phase-native-step-closure." + version)))) :+
        replace(wire, nested.mapObject(_.add("ordinal", Json.True)))
      When("the current-only codec consumes these inputs")
      val rejected = invalid.map(K.codec.decode)
      Then("required types and the current storage schema own refusal, not policy or unchanged-body comparison")
      rejected.foreach(_.isLeft shouldBe true)
    }

    "roundtrip each current v3 attempt and native correction ID without inventing new review targets" in {
      Given("one original reviewed input, retained failed attempts and a later clean corrected commit")
      val work = workInput
      val input = K.Input(work.instanceIdentity, work.sourceRevision, work.selectedPlan.identity,
        work.selectedPlan.revision, "S1", 1, work.originalBase, candidate, "original-review-result")
      val failed = K.Precheck(Vector("one.scala:8: new blank line at EOF."), Vector("one.scala"), true)
      val clean = K.Precheck(Vector.empty, Vector.empty, false)
      val correction = Vector(K.Change("one.scala", "100644", "c" * 40, "d" * 40))
      val samples = Vector(K.Payload(input, 1, None, check = Some(failed)),
        K.Payload(input, 1, None, 1, Some(failed)), K.Payload(input, 1, None, 2, Some(clean), correction),
        K.Payload(input, 1, Some(O.Commit("e" * 40, input.parent.head, "f" * 40)), 2, Some(clean), correction))
      samples.zipWithIndex.foreach { (saved, index) =>
        Given("the exact saved attempt payload " + index)
        When("its current v3 codec encodes and reads the actual attempt facts")
        val wire = K.codec.encode(saved)
        val actual = K.codec.decode(wire)
        Then("attempt, check, correction/native IDs and unchanged original input survive exactly")
        actual shouldBe Right(saved)
        wire.hcursor.get[String]("schemaVersion").toOption.get shouldBe "sm-workflow.goal-phase-native-step-closure.v3"
        actual.toOption.get.input shouldBe input
        actual.toOption.get.input.candidate shouldBe candidate
        actual.toOption.get.input.reviewEvidence shouldBe Some("original-review-result")
        payload(wire).asObject.get.keys.toSet should not contain "validationTarget"
        payload(wire).asObject.get.keys.toSet should not contain "reviewTarget"
        payload(wire).asObject.get.keys.toSet should not contain "authority"
      }
    }

    "default only absent new v3 fields and reject explicit null or malformed attempt facts" in {
      Given("an existing current v3 completed receipt with none of the new optional fields")
      val work = workInput
      val input = K.Input(work.instanceIdentity, work.sourceRevision, work.selectedPlan.identity,
        work.selectedPlan.revision, "S1", 1, work.originalBase, candidate, "original-review-result")
      val original = K.Payload(input, 1, Some(O.Commit("e" * 40, input.parent.head, candidate.tree)))
      val wire = K.codec.encode(original)
      val nested = payload(wire)
      When("the unchanged legacy completed facts and explicit valid nonoptional defaults are read")
      val absent = K.codec.decode(wire)
      val defaults = K.codec.decode(replace(wire, nested.mapObject(_.add("attempt", Json.fromInt(0))
        .add("changes", Json.arr()))))
      Then("old completed facts retain their strict reviewed-tree meaning with no invented check or effect authority")
      absent shouldBe Right(original)
      defaults shouldBe Right(original)
      absent.toOption.get.check shouldBe None
      absent.toOption.get.changes shouldBe empty
      nested.asObject.get.keys.toSet shouldBe Set("input", "ordinal", "commit")
      val invalid = Vector("attempt" -> Json.Null, "attempt" -> Json.True, "attempt" -> Json.fromInt(-1),
        "check" -> Json.Null, "check" -> Json.True, "check" -> Json.obj(),
        "check" -> Json.obj("findings" -> Json.Null, "eofPaths" -> Json.arr(), "eofOnly" -> Json.False),
        "check" -> Json.obj("findings" -> Json.arr(), "eofPaths" -> Json.Null, "eofOnly" -> Json.False),
        "check" -> Json.obj("findings" -> Json.arr(), "eofPaths" -> Json.arr(), "eofOnly" -> Json.Null),
        "changes" -> Json.Null, "changes" -> Json.True, "changes" -> Json.obj(),
        "changes" -> Vector(K.Change("one.scala", "100644", "invalid", "d" * 40)).asJson,
        "changes" -> Vector.fill(2)(K.Change("one.scala", "100644", "c" * 40, "d" * 40)).asJson)
      invalid.zipWithIndex.foreach { case ((field, value), index) =>
        Given("explicit invalid new field case " + index + " for " + field)
        When("the current-only decoder consumes the malformed value rather than defaulting it")
        val result = K.codec.decode(replace(wire, nested.mapObject(_.add(field, value))))
        Then("explicit null and invalid shapes cannot masquerade as absent facts")
        result.isLeft shouldBe true
      }
    }
  }

  "Native parent-child closure retention" should {
    "consume current parent intent and child result without saved command or authority copies" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R6; Example: E13; saved parent intent and actual child result")
      val work = workInput
      val closed = O.ClosedStep("S1", O.Commit("e" * 40, work.originalBase.head, candidate.tree), "original-step-review")
      val input = L.Input(work.instanceIdentity, work.sourceRevision, work.selectedPlan,
        work.originalBase, Vector(closed), candidate, "phase-review", "owned-child-invocation")
      val child = "owned-child-instance"
      val result = org.simplemodeling.textus.smworkflow.value.RepositorySyncNativeConvergenceResult(
        "/selected/repository", "main", "origin", "main", "f" * 40, candidate.tree)
      val saved = L.Payload(input, child, None)
      val wire = L.codec.encode(saved)
      val nested = payload(wire)
      val selected = nested.hcursor.downField("input").focus.get
      val plan = selected.hcursor.downField("plan").focus.get
      val step = plan.hcursor.downField("steps").focus.get.asArray.get.head
      val oldPlan = plan.mapObject(_.remove("phaseReviewClassification").add("steps",
        Json.arr(step.mapObject(_.add("workClassification", Json.Null)))))
      When("a current row omits optional presentation fields")
      L.codec.decode(replace(wire, nested.mapObject(_.remove("result")
        .add("input", selected.mapObject(_.add("plan", oldPlan)))))) shouldBe Right(saved)
      Then("the actual parent intent is readable without command or grant copies")
      nested.hcursor.downField("childCommand").focus shouldBe None
      nested.hcursor.downField("authorityReference").focus shouldBe None
      val completed = saved.copy(result = Some(result))
      val completedWire = L.codec.encode(completed)
      L.codec.decode(completedWire) shouldBe Right(completed)
      When("unused annotations accompany a current intent or real terminal result")
      val annotated = nested.mapObject(_.add("approve", Json.True).add("childCommand", Json.True)
        .add("authority", Json.True).add("input", selected.mapObject(_.add("command", Json.True))))
      L.codec.decode(replace(wire, annotated)) shouldBe Right(saved)
      val completedBody = payload(completedWire)
      val convergence = completedBody.hcursor.downField("result").focus.get
      L.codec.decode(replace(completedWire, completedBody.mapObject(_.add("result",
        convergence.mapObject(_.add("grant", Json.True)))))) shouldBe Right(completed)
      Then("typed consumers still require their actual fields and only the current saved schema")
      Vector(nested.mapObject(_.remove("childSessionId")), nested.mapObject(_.add("childSessionId", Json.True)),
        nested.mapObject(_.remove("input"))).foreach(v => L.codec.decode(replace(wire, v)).isLeft shouldBe true)
      L.codec.decode(wire.mapObject(_.add("schemaVersion", Json.fromString("sm-workflow.goal-phase.native-repository-closure.v1")))).isLeft shouldBe true
      L.codec.decode(replace(completedWire, completedBody.mapObject(_.add("result",
        convergence.mapObject(_.add("tip", Json.True)))))).isLeft shouldBe true
    }
  }
}
