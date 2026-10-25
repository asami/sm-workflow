package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.{Consequence, Conclusion}
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.*
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseNativeValidation as V
import GoalPhaseNativeDependencyValidation as N
import GoalPhaseDependencyValidation as D
import DependencyValidationSpecificationFixture as F
import SplitPhaseExplicitPartitionFixture.take
import GoalPhaseNativeWork as S
import GoalPhaseNativeCandidate as C

/** A narrowly scoped historical producer for a fresh specification database.
  * The real issued/completed native pair already exists; only its typed legacy
  * semantic payload is selected before any candidate or check is produced.
  * All subsequent intent/result, check, review and closure records belong to
  * the ordinary native owners. This helper is never installed in the product.
  */
private[admission] object LegacyDependencyHistoryFixture {
  private val _prefix = "cncf.workflow-json.v1:"
  private def _collection(name: String): DataStore.CollectionId = DataStore.CollectionId.EntityStore(
    org.simplemodeling.model.datatype.EntityCollectionId("smworkflow", "cli", name))
  private final class StoreAccess(val core: ActionCall.Core) extends ProcedureActionCall {
    def withStoreC[A](body: DataStore => Consequence[A]): Consequence[A] = {
      given ExecutionContext = executionContext
      val space = executionContext.dataStoreSpace
      val prior = space.captureBinding()
      try bind_component_application_datastore_c("workflow").flatMap(_ =>
        space.dataStore(_collection("instances")).flatMap(body))
      finally space.restoreBinding(prior)
    }
    def execute(): Consequence[OperationResponse] =
      D.invalid("specification store accessor is not an executable Operation")
  }
  private def _store_c[A](core: ActionCall.Core)(body: DataStore => Consequence[A]): Consequence[A] =
    new StoreAccess(core).withStoreC(body)
  private def _row_c(store: DataStore, collection: String, identity: String)(using ExecutionContext) =
    store.load(_collection(collection), DataStore.StringEntryId(identity)).flatMap(_.map(Consequence.success)
      .getOrElse(D.invalid("specification canonical row is absent: " + identity)))
  private def _bytes_c(record: org.goldenport.record.Record, field: String): Consequence[String] =
    record.getString(field).filter(_.startsWith(_prefix)).map(value => Consequence.success(value.drop(_prefix.length)))
      .getOrElse(D.invalid("specification canonical field is absent: " + field))

  def legacyCompletionC(core: ActionCall.Core, continuation: String, expected: D.Admission): Consequence[String] =
    _store_c(core) { store =>
      given ExecutionContext = core.executionContext
      for {
        row <- _row_c(store, "continuations", continuation)
        issuebytes <- _bytes_c(row, "issuedWorkPayload")
        issued <- WorkflowWorkOrderJsonV1.decodeC(issuebytes, S.inputCodec)
        order = issued.current.asInstanceOf[org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation.WorkOrder[S.Request]].request
        continuationbytes <- _bytes_c(row, "continuationPayload")
        saved <- ContinuationRecordJsonV1.decodeC(continuationbytes)
        bytes <- _bytes_c(row, "completionPayload")
        completed <- WorkflowCompletionRecordJsonV1.decodeC(bytes, S.resultCodec)
        catalog <- GoalPhaseRuntimeCatalog.loadC()
        definitions <- WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition))
        root <- _row_c(store, "instances", issued.handle.instanceIdentity.value)
        rootbytes <- _bytes_c(root, "recordPayload")
        current <- WorkflowInstanceRecordJsonV1.decodeC(rootbytes, definitions)
        pending <- GoalPhaseNativeHistory.prefixC(current, InstanceRevision(order.expectedRevision.value.toLong))
        actual <- GoalPhaseNativeHistory.completionC(current, pending, completed.result.resultReference, "InspectCandidate")
        _ <- if (saved.status == ContinuationRuntimePersistence.Status.Completed &&
            saved.continuation.continuationId == order.continuationId && order.continuationId.value == continuation &&
            completed.result.handle == issued.handle && completed.result.continuationId == order.continuationId &&
            completed.instanceRevision == actual.revision && order.input.exists(_.value.kind == "ImplementStep") &&
            expected.acceptedPrefix.isEmpty && completed.result.result.value.dependencyAdmission.exists(a =>
              a.copy(acceptedPrefix = None) == expected && a.acceptedPrefix.nonEmpty)) Consequence.unit
          else D.invalid("specification legacy producer lost its actual issue/completion relationship")
        legacy = completed.copy(result = completed.result.copy(result =
          org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue(S.resultCodec.typeIdentity,
            completed.result.result.value.copy(dependencyAdmission = Some(expected)))))
        encoded <- WorkflowCompletionRecordJsonV1.encodeC(legacy, S.resultCodec)
        _ <- store.update(_collection("continuations"), DataStore.StringEntryId(continuation),
          org.goldenport.record.Record.data("completionPayload" -> (_prefix + encoded)))
      } yield encoded
    }

  /** Exact canonical bytes for all semantic issues and candidate/check/closure
    * receipts in this native prefix, selected by their actual history sources.
    */
  def snapshotC(core: ActionCall.Core, identity: String): Consequence[Vector[(String, String)]] = _store_c(core) { store =>
    given ExecutionContext = core.executionContext
    for {
      root <- _row_c(store, "instances", identity)
      bytes <- _bytes_c(root, "recordPayload")
      catalog <- GoalPhaseRuntimeCatalog.loadC()
      definitions <- WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition))
      current <- WorkflowInstanceRecordJsonV1.decodeC(bytes, definitions)
      families = Vector("ordered-candidate", "ordered-validation", "ordered-step-closure")
      references = families.flatMap { family =>
        val keys = current.history.map(_.causation.value).filter(_.startsWith(family + ":")).map(_.drop(family.length + 1)).distinct
        val sources = family match {
          case "ordered-candidate" => current.history.filter(_.causation.value == "ordered-candidate:inspection").map(_.revision.value - 1)
          case "ordered-validation" => current.history.filter(_.causation.value == "ordered-candidate:candidate").map(_.revision.value)
          case _ => current.history.filter(e => e.currentProgression.contains(ProgressionReference("phase.Closing")) &&
            e.suspension.isEmpty && current.history.exists(p => p.revision.next == e.revision && p.suspension.nonEmpty)).map(_.revision.value)
        }
        for (source <- sources; key <- keys) yield take(WorkflowMechanicalTransition.keyC(
          WorkflowMechanicalTransition.Reference(identity + ":" + family + ":" + source + ":" + key, "1")))
      }
      ids = (current.history.flatMap(_.suspension.map(_.continuationIdentity.value)) ++ references).distinct
      rows <- ids.foldLeft(Consequence.success(Vector("instance" -> bytes))) { (prior, id) => for {
        accumulated <- prior
        row <- store.load(_collection("continuations"), DataStore.StringEntryId(id))
        encoded <- row.fold(Consequence.success(Option.empty[String]))(_.toJsonStringC.map(Some(_)))
      } yield encoded.fold(accumulated)(value => accumulated :+ (id -> value)) }
    } yield rows
  }

  /** Each controlled counterexample changes one typed capture field in its
    * actual observation result; restoration uses the original canonical bytes.
    */
  def captureVariantC(core: ActionCall.Core, capture: D.Capture, variant: String): Consequence[(String, String)] =
    _store_c(core) { store =>
      given ExecutionContext = core.executionContext
      for {
        id <- WorkflowMechanicalTransition.keyC(WorkflowMechanicalTransition.Reference(capture.session +
          ":ordered-candidate:" + capture.sourceRevision + ":dependency-observation-result", "1"))
        row <- _row_c(store, "continuations", id)
        bytes <- _bytes_c(row, "mechanicalPayload")
        catalog <- GoalPhaseRuntimeCatalog.loadC()
        definitions <- WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition))
        receipt <- WorkflowMechanicalTransitionJsonV1.decodeC(bytes, definitions, C.codec)
        _ <- if (receipt.payload.value.dependency.contains(capture)) Consequence.unit
          else D.invalid("specification counterexample did not select its actual capture")
        changed = variant match {
          case "session" => capture.copy(session = "foreign-session")
          case "plan" => capture.copy(plan = capture.plan.copy(revision = "foreign"))
          case "step" => capture.copy(step = "foreign-step", admission = capture.admission.copy(
            binding = capture.admission.binding.copy(step = "foreign-step"),
            approvals = capture.admission.approvals.map(_.copy(step = "foreign-step"))))
          case "generation" => capture.copy(generation = capture.generation + 1)
          case "admission" => capture.copy(admission = capture.admission.copy(binding = capture.admission.binding.copy(revision = 2)))
          case "tree" => capture.copy(candidate = capture.candidate.copy(tree = "f" * 40))
        }
        _ <- if (D.captureValid(changed)) Consequence.unit
          else D.invalid("specification counterexample must retain a codec-valid capture shape")
        altered = receipt.copy(payload = org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue(C.codec.typeIdentity,
          receipt.payload.value.copy(dependencyAdmission = Some(changed.admission), dependency = Some(changed))))
        encoded <- WorkflowMechanicalTransitionJsonV1.encodeC(altered, C.codec)
        _ <- store.update(_collection("continuations"), DataStore.StringEntryId(id),
          org.goldenport.record.Record.data("mechanicalPayload" -> (_prefix + encoded)))
      } yield id -> bytes
    }
  def restoreCaptureC(core: ActionCall.Core, saved: (String, String)): Consequence[Unit] = _store_c(core) { store =>
    given ExecutionContext = core.executionContext
    store.update(_collection("continuations"), DataStore.StringEntryId(saved._1),
      org.goldenport.record.Record.data("mechanicalPayload" -> (_prefix + saved._2)))
  }
  def completionBoundaryVariantC(core: ActionCall.Core, continuation: String): Consequence[(String, String)] =
    _store_c(core) { store =>
      given ExecutionContext = core.executionContext
      for {
        row <- _row_c(store, "continuations", continuation)
        bytes <- _bytes_c(row, "completionPayload")
        completed <- WorkflowCompletionRecordJsonV1.decodeC(bytes, S.resultCodec)
        changed <- WorkflowCompletionRecordJsonV1.encodeC(completed.copy(instanceRevision = completed.instanceRevision.next), S.resultCodec)
        _ <- store.update(_collection("continuations"), DataStore.StringEntryId(continuation),
          org.goldenport.record.Record.data("completionPayload" -> (_prefix + changed)))
      } yield continuation -> bytes
    }
  def restoreCompletionC(core: ActionCall.Core, saved: (String, String)): Consequence[Unit] = _store_c(core) { store =>
    given ExecutionContext = core.executionContext
    store.update(_collection("continuations"), DataStore.StringEntryId(saved._1),
      org.goldenport.record.Record.data("completionPayload" -> (_prefix + saved._2)))
  }
}

/** Controlled attempts exercise the ordinary native check program, not a replacement executor.
  * They establish no actual Git/SBT execution or production Human authority.
  * @since   Oct. 24, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseNativeDependencyValidationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _check = F.check.copy(dependency = Some(F.binding.identity))
  private def _source(check: V.Check = _check, capture: D.Capture = F.capture): V.Saved = {
    val initial = take(InstanceRecord(InstanceIdentity("same-session"), take(GoalPhaseRuntimeCatalog.loadC()).definition,
      initialRevision, Lifecycle.NotStarted, None, CorrelationReference("specification"),
      CausationReference("specification-start"), None, None, None, Vector.empty).validateC)
    val record = _append(initial, "specification-validation-source")
    V.Saved(record, V.Payload(record.revision.value.toString, "step-1", F.primaryBase,
      Vector(check), Vector.empty, None, dependency = Some(capture)))
  }
  private def _append(record: InstanceRecord, cause: String): InstanceRecord =
    take(record.appendC(record.revision, GoalPhaseNativeRun.history(record, "phase.Validation", cause)))
  private def _conclusion: Conclusion = Consequence.stateConflict[Unit]("SPECIFICATION-OWNED unavailable execution") match {
    case Consequence.Failure(value) => value
    case _ => fail("Expected a structured failure")
  }
  private def _target(workspace: String = F.dependency.workspace): SbtTarget = SbtTarget(take(WorkspaceId.parseC(workspace)))
  private def _observed(termination: ProcessExecutionTermination = ProcessExecutionTermination.Exited(0),
    workspace: String = F.dependency.workspace, operation: SbtOperation = SbtOperation.Compile): SbtExecutionAttempt = {
    val empty = ProcessExecutionCapture(Vector.empty, 0L, false)
    SbtExecutionAttempt.Observed(SbtExecutionResult(_target(workspace), operation,
      ProcessExecutionResult(termination, empty, empty, Vector.empty, 1L, "controlled-dependency-attempt")))
  }
  private final class Episode(initial: V.Saved, outcomes: Vector[SbtExecutionAttempt],
    retained: Vector[V.Attempt] = Vector.empty, fresh: Int => Consequence[Unit] = _ => Consequence.unit,
    inheritedTip: Boolean = false) {
    private var _attempts = retained
    private var _dispatches = 0
    private var _preconditions = 0
    def attempts: Vector[V.Attempt] = _attempts
    def dispatches: Int = _dispatches
    def preconditions: Int = _preconditions
    def runC(): Consequence[V.Saved] = V.runCheckC(initial.payload.selected.head, 0, inheritedTip)(
      () => V.reconcileAttemptsC(initial, initial.payload, 0, _attempts),
      () => { _preconditions += 1; fresh(_preconditions) },
      _save_c, _ => Consequence.unit,
      _ => {
        val selected = outcomes.lift(_dispatches).getOrElse(fail("Unexpected extra dependency dispatch"))
        _dispatches += 1
        Consequence.success(selected)
      }, _ => Consequence.unit)
    private def _save_c(key: String, record: InstanceRecord, payload: V.Payload): Consequence[V.Saved] = {
      if (V.codec.decode(V.codec.encode(payload)).isLeft) Consequence.stateConflict("Invalid controlled native payload")
      else {
        val ordinal = if (key.contains("-retry-")) key.split("-retry-").last.toInt else 0
        val saved = V.Saved(_append(record, "ordered-validation:" + key), payload)
        if (_attempts.size == ordinal) _attempts :+= V.Attempt(None, None)
        val previous = _attempts(ordinal)
        _attempts = _attempts.updated(ordinal, if (key.startsWith("check-intent-")) previous.copy(intent = Some(saved))
          else previous.copy(result = Some(saved)))
        Consequence.success(saved)
      }
    }
  }

  "DV-05 typed dependency attempt attribution" should {
    "refuse decoded legacy and prefixed admissions when no native owner chain is supplied" in {
      Given("a codec-valid legacy admission and a separately codec-valid caller-authored prefix")
      import D.given
      import io.circe.syntax.*
      val prefix = D.AcceptedPrefix(D.acceptedPrefixSchema, F.capture.session, F.capture.plan,
        "preceding-step", "19", F.capture.candidate, F.capture.changedPaths)
      val values = Vector(F.admission, F.admission.copy(acceptedPrefix = Some(prefix)))
        .map(_.asJson.as[D.Admission].toOption.get)
      When("dependency resolution is requested without the native reconstruction owner")
      val results = values.map(N.resolvedC(_, None))
      Then("decoding either shape supplies no ephemeral admission authentication")
      results.foreach(_.isSuccess shouldBe false)
    }

    "guard a primary-only Step or Phase check before and after execution while preserving an actual result" in {
      Given("primary-only validation with a previously qualified dependency tip supplied by the native Store")
      val check = _check.copy(identity = "primary-compile", dependency = None)
      val source = _source(check)
      val initial = source.copy(payload = source.payload.copy(dependency = None))
      val before = new Episode(initial, Vector.empty, fresh = _ => D.invalid("dependency changed before dispatch"), inheritedTip = true)
      val after = new Episode(initial, Vector(_observed(workspace = F.requester.workspace)), fresh = n =>
        if (n == 1) Consequence.unit else D.invalid("dependency changed after execution"), inheritedTip = true)
      When("the inherited-tip precondition detects drift before a primary process")
      val beforeresult = before.runC()
      Then("drift before dispatch prevents the primary process from starting")
      beforeresult.isSuccess shouldBe false
      before.dispatches shouldBe 0
      When("the inherited-tip postcondition detects drift after a successful primary process")
      val afterresult = after.runC()
      Then("the actual result is retained without adopting stale qualification")
      afterresult.isSuccess shouldBe false
      after.dispatches shouldBe 1
      after.preconditions shouldBe 2
      after.attempts.head.result.get.payload.receipts.head.exitCode shouldBe Some(0)
      after.attempts.head.result.get.payload.outcome shouldBe None
      after.attempts.head.result.get.payload.dependency shouldBe None
      after.attempts.head.result.get.payload.dependencyExecutions shouldBe empty
    }

    "retain the exact selected target, operation and candidate alongside actual observed results" in {
      Given("a controlled dependency Compile result and a separately identified primary candidate")
      val source = _source()
      When("the ordinary native check program records the attempt")
      val episode = new Episode(source, Vector(_observed()))
      val saved = take(episode.runC())
      Then("its before/after preconditions run and the receipt cannot become a primary result")
      episode.dispatches shouldBe 1
      episode.preconditions shouldBe 2
      saved.payload.dependency shouldBe source.payload.dependency
      saved.payload.candidate shouldBe F.primaryBase
      saved.payload.dependencyExecutions shouldBe Vector(D.Execution(_check.identity, 0, F.dependency.workspace, "compile"))
      saved.payload.receipts.map(_.identity) shouldBe Vector(_check.identity)
      V.successful(saved.payload.receipts, saved.payload.selected) shouldBe true
      V.codec.decode(V.codec.encode(saved.payload)) shouldBe Right(saved.payload)
      N.publicView(saved.payload) should not be empty
    }

    "reject wrong target or operation for observed, rejected and uncertain attempts" in {
      Given("a fixed dependency check that cannot borrow another repository's outcome")
      val cases = Table("attempt", _observed(workspace = "primary-source"), _observed(operation = SbtOperation.Test),
        SbtExecutionAttempt.AdmissionRejected(_target("primary-source"), SbtOperation.Compile, _conclusion),
        SbtExecutionAttempt.Uncertain(_target(), SbtOperation.Test, _conclusion))
      When("each crossed attempt reaches the actual native result constructor")
      forAll(cases) { attempt =>
        When("the crossed attempt is projected for the selected check")
        val result = V.attemptPayloadC(_source().payload, _check, 0, attempt)
        Then("no receipt, retry or success can be manufactured")
        result.isSuccess shouldBe false
      }
      When("workspace resolution is requested with another dependency binding")
      val workspace = N.workspaceC(_check.copy(dependency = Some("another-binding")), Some(F.capture))
      Then("the crossed binding is refused")
      workspace.isSuccess shouldBe false
      When("a primary-only check is offered as the captured dependency selection")
      val checks = N.checksC(Vector(_check.copy(dependency = None)), Some(F.capture))
      Then("the mismatched selection is refused")
      checks.isSuccess shouldBe false
    }

    "retain missing permission without a process receipt and leave an unexecuted suite unverified" in {
      Given("a selected dependency with an actual admission rejection-shaped controlled observation")
      When("the result owner separates admission from actual execution")
      val rejected = take(V.attemptPayloadC(_source().payload, _check, 0,
        SbtExecutionAttempt.AdmissionRejected(_target(), SbtOperation.Compile, _conclusion)))
      Then("no process or dependency success is inferred")
      rejected.receipts shouldBe empty
      rejected.dependencyExecutions shouldBe empty
      rejected.admissionFailures.map(_.checkIdentity) shouldBe Vector(_check.identity)
      val test = _check.copy(kind = WorkflowSbtValidation.Kind.TestOnly)
      val capture = F.capture.copy(admission = F.admission.copy(binding = F.binding.copy(checks = Vector(test.copy(dependency = None)))))
      val source = _source(test, capture)
      When("an actually observed TestOnly attempt has no verified suite selection")
      val unverified = take(V.attemptPayloadC(source.payload, test, 0, _observed(operation = SbtOperation.TestOnly)))
      Then("the process remains unverified and cannot supply dependency success")
      unverified.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Unverified
      V.successful(unverified.receipts, unverified.selected) shouldBe false
    }
  }

  "DV-04,DV-07 retained failure and uncertainty" should {
    "preserve failure, retain an unknown result and never automatically repeat it" in {
      Given("a controlled nonzero exit and a separate uncertain attempt in the same immutable assignment")
      val failed = take(V.attemptPayloadC(_source().payload, _check, 0, _observed(ProcessExecutionTermination.Exited(1))))
      V.successful(failed.receipts, failed.selected) shouldBe false
      val source = _source()
      val first = new Episode(source, Vector(SbtExecutionAttempt.Uncertain(_target(), SbtOperation.Compile, _conclusion)))
      val uncertain = take(first.runC())
      When("a later ordinary check invocation reconciles that unchanged native prefix")
      val resumed = new Episode(source, Vector.empty, first.attempts)
      val retained = take(resumed.runC())
      Then("the original unknown attempt survives with no extra dispatch or implicit recovery consent")
      first.dispatches shouldBe 1
      resumed.dispatches shouldBe 0
      retained shouldBe uncertain
      retained.payload.uncertainties.size shouldBe 1
      retained.payload.newAttempts shouldBe empty
      retained.record.identity shouldBe source.record.identity
    }

    "save the observed result before refusing changed dependency bytes" in {
      Given("controlled freshness succeeds before execution and fails at the postcheck boundary")
      val episode = new Episode(_source(), Vector(_observed()), fresh = count =>
        if (count == 1) Consequence.unit else Consequence.stateConflict("SPECIFICATION-OWNED dependency drift"))
      When("the same native program reaches its postcheck observation")
      val result = episode.runC()
      Then("continuation refuses while retaining the real attempt and its receipt without success adoption")
      result.isSuccess shouldBe false
      episode.dispatches shouldBe 1
      episode.attempts.head.result.get.payload.receipts.size shouldBe 1
      episode.attempts.head.result.get.payload.outcome shouldBe None
    }

    "append one explicitly directed controlled recovery without replacing the old unknown attempt" in {
      Given("an actual native unknown-prefix shape and SPECIFICATION-OWNED recovery direction, not genuine Human consent")
      val source = _source()
      val first = new Episode(source, Vector(SbtExecutionAttempt.Uncertain(_target(), SbtOperation.Compile, _conclusion)))
      val unknown = take(first.runC())
      val old = first.attempts.head
      val direction = V.NewAttempt(_check.identity, 0, old.intent.get.record.revision.value.toString,
        "specification-explicit-recovery", "1", "participant",
        "SPECIFICATION-OWNED new attempt, accepting potentially repeated effects of the old unknown attempt")
      val recovered = V.Saved(_append(unknown.record, "ordered-validation:check-recovery-0"),
        unknown.payload.copy(newAttempts = unknown.payload.newAttempts :+ direction))
      val attempts = Vector(old.copy(recovery = Some(recovered)))
      When("the existing native attempt reconciler observes that exact controlled recovery prefix")
      val next = new Episode(source, Vector(_observed()), attempts)
      val result = take(next.runC())
      Then("one new ordinal runs in the same Session and all previous unknown facts and direction remain retained")
      next.dispatches shouldBe 1
      next.attempts.size shouldBe 2
      next.attempts.head shouldBe attempts.head
      result.record.identity shouldBe source.record.identity
      result.payload.uncertainties shouldBe unknown.payload.uncertainties
      result.payload.newAttempts shouldBe Vector(direction)
      result.payload.dependencyExecutions shouldBe Vector(D.Execution(_check.identity, 1, F.dependency.workspace, "compile"))
      result.payload.dependency shouldBe source.payload.dependency
    }

    "keep legacy native validation independent of the new optional subject" in {
      Given("an old primary check selection with no dependency obligation")
      val legacy = _source().payload.copy(selected = Vector(_check.copy(dependency = None)), dependency = None)
      When("the ordinary codec restores the omitted extension")
      val restored = V.codec.decode(V.codec.encode(legacy))
      val view = N.publicView(legacy)
      val checks = N.checksC(legacy.selected, None)
      Then("absence remains absence and cannot produce a dependency projection")
      restored shouldBe Right(legacy)
      view shouldBe None
      checks.toOption shouldBe Some(legacy.selected)
    }
  }
}
