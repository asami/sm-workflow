package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativeValidationRecoveryPreparation
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseValidationRecovery as R
import GoalPhaseInterruptedValidationRepair as I
import GoalPhaseTerminatedTimeoutValidationRecovery as T
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Closed wire and preparation inputs owned by this specification. Actual
  * producer termination, current source proof and effects belong to E15.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseTerminatedTimeoutValidationRecoverySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _reference = ContextReference("new-timeout-human", "1")
  private def _target(size: Int = 40): R.RecoveryTarget = {
    val base = O.Snapshot("a" * size, "b" * size)
    val candidate = O.Snapshot("c" * size, "d" * size)
    val source = I.Source(I.kind, "timeout-session", "GoalPhaseWorkflow", "1", "participant", "plan", "1",
      base, candidate, "10", "13", "phase:phase-2", "test", 2, 0,
      I.Receipt("timeout-session:ordered-validation:10:check-intent-0", "11"),
      I.Receipt("timeout-session:ordered-validation:10:check-result-0", "12"),
      I.Receipt("timeout-session:ordered-validation:10:validation", "13"), "timed-out", "incomplete")
    R.RecoveryTarget(source.sessionId, source.planIdentity, source.planRevision, base, candidate,
      source.sourceRevision, source.decisionRevision, source.step, source.checkIdentity, source.checkOrdinal,
      source.attempt, source.intent.revision, terminatedTimeoutPhase = Some(source))
  }
  private def _direction(target: R.RecoveryTarget) =
    T.Direction(target, _reference, "participant", "New same-candidate attempt under the extended normal host deadline")

  "E15 confirmed timeout same-candidate validation" should afterWord(
    "in spec:goal-phase-validation-recovery, example:E15, rules:R4,R5,R10, phase:6.5") {
    "round trip the separate complete timeout extension and Direction for both existing Git object lengths" in {
      Given("docs/spec/goal-phase-validation-recovery.md R4,R10 E15: specification-owned complete 40- and 64-character timeout targets without known-failed or dependency authority")
      val targets = Vector(_target(), _target(64))
      When("canonical target and separate Direction codecs carry each exact typed source")
      val restored = targets.map(target => R.decodeTarget(target.asJson))
      val directions = targets.map(target => T.codec.decode(T.codec.encode(_direction(target))))
      val rendered = RuntimeContext.PropertyNameContext.default.transformRecord(R.targetRecord(targets.head))
      Then("the shared source remains explicit TimedOut/incomplete and the original variants are not manufactured")
      restored shouldBe targets.map(Right(_))
      directions shouldBe targets.map(target => Right(_direction(target)))
      take(R.targetC(rendered)) shouldBe targets.head
      targets.foreach { target =>
        target.knownFailedPhase shouldBe None
        target.dependency shouldBe None
        target.asJson.hcursor.downField("knownFailedPhase").focus shouldBe None
        T.codec.encode(_direction(target)).hcursor.get[String]("schemaVersion").toOption shouldBe Some(T.schema)
        R.messageFor(target) should include("confirmed terminated timeout")
        R.messageFor(target) should include("no source repair or corrective Step")
        R.choicesFor(target) shouldBe Vector("AuthorizeNewAttempt", "LimitedRepair", "Defer")
        R.messageFor(target) should include("AuthorizeNewAttempt (deadline extension), LimitedRepair, or Defer")
        R.messageFor(target) should include("normal host deadline must actually be adjusted")
        R.messageFor(target) should include("does not change its configured value")
        R.messageFor(target) should include("fix known errors")
        R.messageFor(target) should include("actual start/end observations")
        R.messageFor(target) should include("diagnostic stack/process evidence")
        R.messageFor(target) should include("Timeout alone is not proof of a hang")
        R.messageFor(target) should include("last printed test may already have completed")
        R.messageFor(target) should include("test identity unknown or evidence missing")
        R.messageFor(target) should include("normal slow execution or an external wait")
        R.messageFor(target) should include("CNCF owns execution and result interpretation")
        R.messageFor(target) should include("deeper investigation without retry")
        GoalPhaseKnownFailedValidationRecovery.codec.decode(T.codec.encode(_direction(target))).isLeft shouldBe true
      }
    }

    "refuse incomplete unknown null crossed ambiguous noninteger and malformed timeout targets" in {
      Given("R4,R10 E15: a canonical timeout target and damaged extension or outer records")
      val target = _target()
      val source = target.terminatedTimeoutPhase.get
      val wire = target.asJson
      val extension = wire.hcursor.downField("terminatedTimeoutPhase").focus.get
      val known = R.KnownFailedPhase(R.KnownFailedPhase.schema, source.workflowIdentity, source.workflowRevision,
        source.participant, source.validation.identity, source.validation.revision, 1)
      val ambiguous = target.copy(knownFailedPhase = Some(known))
      val dependency = GoalPhaseDependencyValidation.subject(DependencyValidationSpecificationFixture.capture)
      val crossed = Vector(target.copy(sessionId = "other"), target.copy(planIdentity = "other"), target.copy(planRevision = "2"),
        target.copy(base = target.candidate), target.copy(candidate = target.base), target.copy(sourceRevision = "9"),
        target.copy(frontierRevision = "14"), target.copy(step = "phase:other"), target.copy(checkIdentity = "other"),
        target.copy(checkOrdinal = 3), target.copy(attempt = 1), target.copy(intentRevision = "12"), ambiguous,
        target.copy(dependency = Some(dependency)))
      val damagedsources = Vector(source.copy(kind = "unknown"), source.copy(termination = "termination-unconfirmed"),
        source.copy(termination = "cancelled"), source.copy(outcome = "failed"), source.copy(sourceRevision = "010"),
        source.copy(result = source.result.copy(identity = "foreign-result")),
        source.copy(result = source.result.copy(revision = "13")), source.copy(validation = source.validation.copy(revision = "14"))) ++
        Vector("a" * 39, "a" * 63, "a" * 65, "g" * 64, "A" * 40).map(value => source.copy(candidate = source.candidate.copy(head = value)))
      val malformed = Vector(wire.mapObject(_.add("unknown", Json.True)), wire.mapObject(_.remove("intentRevision")),
        wire.mapObject(_.add("terminatedTimeoutPhase", Json.Null)), wire.mapObject(_.add("terminatedTimeoutPhase", Json.obj())),
        wire.mapObject(_.add("terminatedTimeoutPhase", extension.mapObject(_.add("unknown", Json.True)))),
        wire.mapObject(_.add("terminatedTimeoutPhase", extension.mapObject(_.remove("result")))),
        wire.mapObject(_.add("terminatedTimeoutPhase", extension.mapObject(_.add("validation", Json.Null)))),
        wire.mapObject(_.add("checkOrdinal", Json.fromBigDecimal(BigDecimal("2.5")))),
        wire.mapObject(_.add("terminatedTimeoutPhase", extension.mapObject(_.add("checkOrdinal", Json.fromBigDecimal(BigDecimal("2.5")))))))
      val publicwire = io.circe.parser.parse(take(RuntimeContext.PropertyNameContext.default.transformRecord(
        R.targetRecord(target)).toJsonStringC)).toOption.get
      When("strict decoding and standard Record normalization receive each damaged contract")
      val refused = (crossed ++ damagedsources.map(value => target.copy(terminatedTimeoutPhase = Some(value))))
        .map(value => R.decodeTarget(value.asJson)) ++ malformed.map(R.decodeTarget)
      val collision = R.targetC(GoalPhaseNativeMetadata.record(publicwire.mapObject(_.add("checkOrdinal", Json.fromInt(2)))))
      Then("none becomes timeout authority and legacy absence remains an ordinary non-timeout representation")
      refused.foreach(_.isLeft shouldBe true)
      collision.isSuccess shouldBe false
      val absent = take(R.targetC(R.targetRecord(target.copy(terminatedTimeoutPhase = None))))
      absent.terminatedTimeoutPhase shouldBe None
      T.codec.decode(T.codec.encode(_direction(absent))).isLeft shouldBe true
    }

    "require the exact separate Direction participant reference schema and complete envelope" in {
      Given("R10 E15: a canonical timeout Direction with actual specification-owned Human reference")
      val direction = _direction(_target())
      val wire = T.codec.encode(direction)
      val payload = wire.hcursor.downField("payload").focus.get
      val malformed = Vector(wire.mapObject(_.add("unknown", Json.True)), wire.mapObject(_.remove("schemaVersion")),
        wire.mapObject(_.add("schemaVersion", Json.fromString("sm-workflow.goal-phase-known-failed-validation-recovery.v1"))),
        wire.mapObject(_.add("payload", payload.mapObject(_.add("reference", Json.Null)))),
        wire.mapObject(_.add("payload", payload.mapObject(_.add("unknown", Json.True)))),
        T.codec.encode(direction.copy(participant = "other")), T.codec.encode(direction.copy(reference = ContextReference("", "1"))))
      When("the separate strict Direction codec receives crossed fields and envelopes")
      val refused = malformed.map(T.codec.decode)
      Then("only complete timeout directions survive without entering the known-failed namespace")
      T.codec.decode(wire) shouldBe Right(direction)
      refused.foreach(_.isLeft shouldBe true)
      T.codec.typeIdentity shouldBe "GoalPhaseTerminatedTimeoutValidationRecoveryDirection"
    }

    "prepare LimitedRepair from the actual timeout description with exact Human evidence and reject altered binding scope" in {
      Given("docs/spec/goal-phase-validation-recovery.md R4,R5,R10 E15: a supplied timeout description and independently observed fixture-owned Human answer")
      val target = _target()
      val description = V.GoalPhaseValidationRecoveryDescriptionResult(target.sessionId, "decision-required", R.messageFor(target),
        "fixture-component", R.targetRecord(target), R.choicesFor(target), R.recoveryOperation)
      val answer = V.GoalPhaseValidationRecoveryAnswer("LimitedRepair", "Investigate the suspected hang and known errors before proposing repair")
      val reference = V.WorkflowArtifactReference(_reference.identity, _reference.revision)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      val now = Instant.parse("2026-10-24T00:00:00Z")
      When("the common passive preparation binds the exact timeout description and actual Human inputs")
      val prepared = take(NativeValidationRecoveryPreparation.prepareC(description, answer, reference, execution,
        "participant", "fixture-independent-issuer", now))
      val binding = take(R.bindingC("fixture-component", target, answer.choice, answer.rationale, "participant", _reference))
      val evidence = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      Then("the existing purpose carries exact timeout authority without process or Workflow effects")
      evidence.purpose shouldBe R.purpose
      evidence.binding shouldBe binding
      R.selectBinding(binding) shouldBe Right(binding)
      NativeValidationRecoveryPreparation.prepareC(description.copy(choices = R.choices), answer, reference, execution,
        "participant", "fixture-independent-issuer", now).isSuccess shouldBe false
      NativeValidationRecoveryPreparation.prepareC(description.copy(choices = R.timeoutChoices :+ "Retry"), answer,
        reference, execution, "participant", "fixture-independent-issuer", now).isSuccess shouldBe false
      NativeValidationRecoveryPreparation.prepareC(description.copy(choices = R.timeoutChoices.reverse), answer,
        reference, execution, "participant", "fixture-independent-issuer", now).isSuccess shouldBe false
      R.timeoutChoices.foreach { choice =>
        val selected = answer.copy(choice = choice)
        NativeValidationRecoveryPreparation.prepareC(description, selected, reference, execution,
          "participant", "fixture-independent-issuer", now).isSuccess shouldBe true
      }
      R.bindingC("fixture-component", target, answer.choice, answer.rationale, "other", _reference).isSuccess shouldBe false
      NativeValidationRecoveryPreparation.prepareC(description, answer, reference, execution, "other", "fixture-independent-issuer", now)
        .isSuccess shouldBe false
      val body = binding.hcursor.downField("validationRecovery").focus.get
      val altered = Vector(binding.mapObject(_.add("unknown", Json.True)),
        binding.mapObject(_.add("validationRecovery", body.mapObject(_.add("unknown", Json.True)))),
        binding.mapObject(_.add("validationRecovery", body.mapObject(_.add("answer",
          body.hcursor.downField("answer").focus.get.mapObject(_.add("unknown", Json.True)))))),
        binding.mapObject(_.add("validationRecovery", body.mapObject(_.add("target", target.copy(frontierRevision = "14").asJson)))))
      altered.foreach(value => R.selectBinding(value).isLeft shouldBe true)
      val declaration = NativeExternalEvidence.Declaration("workflow", org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(
        org.simplemodeling.model.datatype.EntityCollectionId("fixture", "host", "evidence")), "fixture-independent-issuer")
      val oldbinding = take(R.bindingC("fixture-component", target, answer.choice, answer.rationale, "participant",
        ContextReference("old-human", "1")))
      val crossed = Vector(evidence.copy(binding = oldbinding), evidence.copy(participant = "other"), evidence.copy(issuer = "other"),
        evidence.copy(revision = "2"), evidence.copy(occurredAt = "2026-10-25T00:00:00Z"))
      NativeExternalEvidence.observeC(evidence, declaration, NativeExternalEvidence.Purpose.ValidationRecovery,
        evidence.lookupIdentity, binding, now).isSuccess shouldBe true
      crossed.foreach(value => NativeExternalEvidence.observeC(value, declaration, NativeExternalEvidence.Purpose.ValidationRecovery,
        evidence.lookupIdentity, binding, now).isSuccess shouldBe false)
    }
  }
}
