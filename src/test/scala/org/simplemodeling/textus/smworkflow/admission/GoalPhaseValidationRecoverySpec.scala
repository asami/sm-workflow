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
import SplitPhaseExplicitPartitionFixture.take

/** Immutable data/chain specifications; these facts are test inputs, not host
  * observations or an independent production Human attestation.
  * @since   Oct. 20, 2026
  * @version Oct. 20, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseValidationRecoverySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val snapshot = O.Snapshot("a" * 40, "b" * 40)
  private val check = V.Check("compile", 1, WorkflowSbtValidation.Kind.Compile)
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
      val declared = NativeHostEvidence.Declaration("workflow", org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(
        org.simplemodeling.model.datatype.EntityCollectionId("smworkflow", "cli", "host_evidence")), "test-host")
      val now = Instant.parse("2026-10-20T00:00:00Z")
      val evidence = NativeHostEvidence.Evidence(NativeHostEvidence.schema, R.purpose,
        "lookup", reference.identity, reference.revision, declared.issuer, "participant", binding, now.toString)
      When("the exact direction and crossed variants are independently observed")
      val accepted = NativeHostEvidence.observeC(evidence, declared, NativeHostEvidence.Purpose.ValidationRecovery, "lookup", binding, now)
      val crossed = Vector(evidence.copy(participant = "another-participant"), evidence.copy(reference = "another-answer"),
        evidence.copy(revision = "2"), evidence.copy(binding = take(R.bindingC("component-instance", target.copy(planRevision = "2"),
          "AuthorizeNewAttempt", "Accept possible repeat effects", "participant", reference))))
      Then("only the exact participant/reference/complete target binding is admitted")
      withClue(accepted.toString + " ") { accepted.isSuccess shouldBe true }
      crossed.foreach(value => NativeHostEvidence.observeC(value, declared, NativeHostEvidence.Purpose.ValidationRecovery,
        "lookup", binding, now).isSuccess shouldBe false)
      R.decodeTarget(target.asJson.mapObject(_.add("attempt", Json.fromString("0")))).isLeft shouldBe true
      R.selectBinding(binding.mapObject(_.add("ignoredAuthority", Json.True))) shouldBe Right(binding)
    }
  }
}
