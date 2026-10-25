package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.Files
import java.time.{Instant, LocalDate}
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceId}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.preparation.NativeSourceHistoryNormalizationPreparation
import GoalPhaseSourceHistoryNormalization as N
import GoalPhaseSourceHistoryRecovery as R
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Immutable binding and actual managed File specifications. Supplied policy
  * references and lifecycle assertions are test-owned facts, never live consent.
  * @since   Oct. 26, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseSourceHistoryNormalizationSpec extends AnyWordSpec with NativeGoalSourceHistoryCliFixture {
  private val snapshot = O.Snapshot("a" * 40, "b" * 40)
  private val day = LocalDate.of(2026, 10, 21)
  private val artifact = ContextReference("already-held-policy-direction", "1")
  private val source = WorkflowSourceHistory.Source(sourcePath, day, None, None, true)
  private def rule = take(WorkflowSourceHistoryRule.decodeC(enabledSettings(ruleKey)))
  private def target(prefixSize: Int = 2, pending: Boolean = true): N.Target = {
    val paths = (0 until prefixSize).map(index => "src/main/scala/Prefix" + index + ".scala").toVector :+ sourcePath
    def payload(path: String) = GoalPhaseNativeSourceHistory.Payload("50", "step-1", path, "goldenport",
      rule.identity, day.toString, true, None)
    val prefix = paths.take(prefixSize).zipWithIndex.map { (path, index) => R.CompletedPrefix(index,
      "session:ordered-candidate:50:source-history-intent-" + index, "1", (52 + index * 2).toString,
      "session:ordered-candidate:50:source-history-result-" + index, "1", (53 + index * 2).toString,
      payload(path), payload(path).copy(outcome = Some("updated"))) }
    val frontier = (52 + prefixSize * 2).toString
    val recovery = R.RecoveryTarget("session", "plan", "2", snapshot, snapshot, "goal-phase", "1", "participant",
      "step-1", "50", frontier, "session:ordered-candidate:50:source-history-intent-" + prefixSize, "1", frontier,
      paths, paths, Vector.empty, enabledSettings(ruleKey), GoalPhaseNativeCandidate.Payload("50", "step-1", paths,
        Vector.empty, None, Vector.empty, None), payload(sourcePath), prefixSize, prefix)
    val question = "session:ordered-human:" + frontier
    N.Target(recovery, (frontier.toLong + (if (pending) 1 else 0)).toString,
      if (pending) question + ":issued" else "ordered-candidate:source-history-intent-" + prefixSize, N.policy,
      if (pending) Some(N.Question(question, (frontier.toLong + 1).toString)) else None)
  }
  private def withSource(text: String, access: Option[String] = Some("manage"))(
    task: (java.nio.file.Path, org.goldenport.cncf.action.ActionCall.Core, FileTarget) => Unit): Unit = {
    val root = WorkflowFileRuntimeFixture.fresh()
    val file = root.resolve(sourcePath)
    Files.createDirectories(file.getParent)
    Files.writeString(file, text)
    WorkflowFileRuntimeFixture.withCore(root, workspace = "sm-workflow-repository", access = access,
      mapping = enabledSettings) { core => task(file, core, FileTarget(take(WorkspaceId.parseC("sm-workflow-repository")))) }
  }

  "Same-authority normalization through managed File" should {
    "apply the original rule and date idempotently while preserving body and unrelated comments" in {
      Given("SHR-02: a source with unrelated comment text and its original intent date")
      val original = "// Retain this unrelated comment.\n" + changedText
      val expected = take(WorkflowSourceHistoryRule.applyC(rule, WorkflowSourceHistoryRule.Input(sourcePath,
        original, day, None, None, true))).text
      withSource(original) { (file, core, workspace) =>
        When("one bounded managed normalization and then its current-postcondition observation run")
        val first = take(GoldenportSourceHistoryDriver.normalizeC(core, workspace, rule, source))
        val modified = Files.getLastModifiedTime(file)
        val second = take(GoldenportSourceHistoryDriver.normalizeC(core, workspace, rule, source))
        Then("only the first writes, N(N(text)) equals N(text), and the second preserves mtime")
        first.outcome shouldBe "updated-current"
        second shouldBe GoldenportSourceHistoryDriver.Normalization("observed-current-postcondition", None)
        Files.readString(file) shouldBe expected
        Files.readString(file) should include("// Retain this unrelated comment.")
        Files.readString(file) should endWith(changedText)
        Files.getLastModifiedTime(file) shouldBe modified
      }
    }

    "observe already normalized current content with read-only authority and no File replacement" in {
      Given("SHR-03: current comment already meets the old intent despite its missing receipt")
      val current = take(WorkflowSourceHistoryRule.applyC(rule, WorkflowSourceHistoryRule.Input(sourcePath,
        changedText, day, None, None, true))).text
      withSource(current, Some("read-only")) { (file, core, workspace) =>
        val before = Files.getLastModifiedTime(file)
        When("the existing rule and original effective date are reconciled")
        val observed = take(GoldenportSourceHistoryDriver.normalizeC(core, workspace, rule, source))
        Then("actual current postcondition succeeds without write authority or an assertion about the old execution")
        observed.outcome shouldBe "observed-current-postcondition"
        Files.readString(file) shouldBe current
        Files.getLastModifiedTime(file) shouldBe before
      }
    }

    "return known prewrite failure for ambiguous comments and unavailable acquisition" in {
      Given("SHR-03: a duplicate managed latest field whose authority cannot be guessed")
      val ambiguous = "/**\n * @version Oct. 20, 2026\n * @version Oct. 21, 2026\n */\n" + changedText
      withSource(ambiguous) { (file, core, workspace) =>
        val before = Files.getLastModifiedTime(file)
        When("the bounded normalizer reads the ambiguous input")
        val actual = take(GoldenportSourceHistoryDriver.normalizeC(core, workspace, rule, source))
        Then("a concrete failed observation is available before any write and all bytes remain")
        actual.outcome shouldBe "failed"
        actual.diagnostic.get should include("duplicate since or latest version")
        Files.readString(file) shouldBe ambiguous
        Files.getLastModifiedTime(file) shouldBe before
      }
      withSource(changedText, None) { (file, core, workspace) =>
        When("File acquisition is denied before replacement")
        val actual = take(GoldenportSourceHistoryDriver.normalizeC(core, workspace, rule, source))
        Then("the failure carries its actual diagnostic and never becomes an unknown write")
        actual.outcome shouldBe "failed"
        actual.diagnostic should not be empty
        Files.readString(file) shouldBe changedText
      }
    }

    "refuse changed full rules or altered postconditions before fresh effects" in {
      Given("the exact adopted original rule/date and a complete current postcondition")
      val selected = target(0, pending = false)
      val current = take(WorkflowSourceHistoryRule.applyC(rule, WorkflowSourceHistoryRule.Input(sourcePath,
        changedText, day, None, None, true))).text
      withSource(current) { (file, core, _) =>
        val before = Files.getLastModifiedTime(file)
        val changed = selected.copy(recovery = selected.recovery.copy(historyPolicy = selected.recovery.historyPolicy
          .mapObject(_.add("removeHistoryAtSince", Json.False))))
        When("candidate reuse checks the changed full rule against the retained original rule")
        val crossed = N.verifyAffectedC(core, changed)
        val retained = N.verifyAffectedC(core, selected)
        Then("the crossed full rule refuses and the unchanged postcondition verifies without writes")
        crossed.isSuccess shouldBe false
        retained.isSuccess shouldBe true
        Files.getLastModifiedTime(file) shouldBe before
        Given("an ordinary source edit changes the affected current postcondition")
        Files.writeString(file, changedText)
        When("candidate reuse checks the original target again")
        val altered = N.verifyAffectedC(core, selected)
        Then("changed bytes cannot be excluded from fresh normalization under invented success")
        altered.isSuccess shouldBe false
        Files.readString(file) shouldBe changedText
      }
    }
  }

  "Strict normalization policy adoption binding" should {
    "roundtrip real public omission of absent legacy nested options without admitting explicit nulls or unknown fields" in {
      Given("a complete recovery with eight successful intents and legacy nulls only for known absent options")
      val selected = target(8)
      val legacy = selected.recovery.asJson
      val expected = legacy.mapObject { fields =>
        fields.add("intent", fields("intent").get.mapObject(_.remove("outcome")))
          .add("inspection", fields("inspection").get.mapObject(_.remove("candidate").remove("diff")))
          .add("completedPrefix", fields("completedPrefix").get.mapArray(_.map(_.mapObject(entry =>
            entry.add("intent", entry("intent").get.mapObject(_.remove("outcome")))))))
      }
      When("the target crosses the actual Record JSON renderer and public property-name transformation")
      val canonical = selected.asJson
      val rendered = take(N.targetRecord(selected).toJsonStringC)
      val publicrecord = RuntimeContext.PropertyNameContext.default.transformRecord(N.targetRecord(selected))
      val transported = N.targetC(publicrecord)
      Then("the new wrapper uses the real optional-absence shape while retaining the complete original recovery")
      canonical.hcursor.get[Json]("recovery") shouldBe Right(expected)
      legacy.hcursor.downField("intent").downField("outcome").focus shouldBe Some(Json.Null)
      io.circe.parser.parse(rendered) shouldBe Right(canonical)
      take(transported) shouldBe selected
      N.decodeTarget(canonical) shouldBe Right(selected)

      Given("the same public target with explicit absent-option nulls, unknown nested fields or a colliding public name")
      val malformed = Vector(
        expected.mapObject(fields => fields.add("intent", fields("intent").get.mapObject(_.add("outcome", Json.Null)))),
        expected.mapObject(fields => fields.add("inspection", fields("inspection").get.mapObject(_.add("candidate", Json.Null)))),
        expected.mapObject(fields => fields.add("inspection", fields("inspection").get.mapObject(_.add("diff", Json.Null)))),
        expected.mapObject(fields => fields.add("completedPrefix", fields("completedPrefix").get.mapArray(_.map(
          _.mapObject(entry => entry.add("intent", entry("intent").get.mapObject(_.add("outcome", Json.Null)))))))),
        expected.mapObject(fields => fields.add("intent", fields("intent").get.mapObject(_.add("unexpected", Json.True)))),
        expected.mapObject(fields => fields.add("historyPolicy", fields("historyPolicy").get.mapObject(_.add("unexpected", Json.Null)))))
      When("strict canonical admission decodes each supplied shape without normalizing its nulls or extra keys")
      val refused = malformed.map(recovery => N.decodeTarget(canonical.mapObject(_.add("recovery", recovery))))
      val collision = N.decodePublicTarget(canonical.mapObject(fields => fields.add("recovery",
        expected.mapObject(_.add("intent_index", Json.fromInt(selected.recovery.intentIndex))))))
      val opaque = selected.copy(recovery = selected.recovery.copy(historyPolicy = selected.recovery.historyPolicy
        .mapObject(_.add("unexpected", Json.Null)))).asJson
      Then("explicit nulls unknown fields and aliases refuse and opaque rule JSON is never silently scrubbed")
      refused.foreach(_.isLeft shouldBe true)
      collision.isLeft shouldBe true
      opaque.hcursor.downField("recovery").downField("historyPolicy").downField("unexpected").focus shouldBe Some(Json.Null)
    }

    "bind the immediately preceding reconciliation and reject malformed or crossed resume provenance" in {
      Given("the exact original pending target and a truthful failed reconciliation frontier")
      val original = target(8)
      val reference = N.reference(original)
      val prior = N.ResumeFrom(reference.identity, reference.revision, reference.identity + ":reconciliation-intent", "1",
        Some(reference.identity + ":reconciliation-result"), Some("1"), Some("failed"))
      val selected = original.copy(frontierRevision = (original.frontierRevision.toLong + 3).toString,
        frontierCause = "ordered-source-history-normalization-reconciliation:result", pendingQuestion = None, resumeFrom = Some(prior))
      val wire = selected.asJson
      val unresolved = selected.copy(frontierRevision = (selected.frontierRevision.toLong - 1).toString,
        frontierCause = "ordered-source-history-normalization-reconciliation:intent",
        resumeFrom = Some(prior.copy(resultIdentity = None, resultRevision = None, outcome = None)))
      When("canonical and public codecs transport both failed and resultless resume targets")
      val decoded = N.decodeTarget(wire)
      val publictarget = N.targetC(RuntimeContext.PropertyNameContext.default.transformRecord(N.targetRecord(selected)))
      val binding = N.bindingC("component", selected, N.choices.head, "Continue the retained policy after actual termination",
        "participant", artifact, true)
      Then("the latest invocation is explicit and the original full policy date prefix and unknown remain exact")
      decoded shouldBe Right(selected)
      take(publictarget) shouldBe selected
      N.decodeTarget(unresolved.asJson) shouldBe Right(unresolved)
      selected.recovery shouldBe original.recovery
      selected.pendingQuestion shouldBe None
      N.reference(selected).identity shouldBe reference.identity + ":resume:" + selected.frontierRevision
      take(binding).hcursor.downField("sourceHistoryNormalization").get[Json]("target") shouldBe Right(wire)
      N.selectBinding(take(binding)) shouldBe Right(take(binding))
      N.bindingC("component", unresolved, N.choices.head, "No terminal fact", "participant", artifact, false).isSuccess shouldBe false

      Given("malformed null extra crossed stale or successful provenance supplied as a resume target")
      val malformed = Vector(
        wire.mapObject(_.add("resumeFrom", Json.Null)),
        wire.mapObject(_.add("resumeFrom", prior.asJson.mapObject(_.add("extra", Json.True)))),
        wire.mapObject(_.add("resumeFrom", prior.asJson.mapObject(_.add("outcome", Json.Null)))),
        selected.copy(resumeFrom = Some(prior.copy(directionIdentity = "other-direction"))).asJson,
        selected.copy(resumeFrom = Some(prior.copy(directionRevision = "2"))).asJson,
        selected.copy(resumeFrom = Some(prior.copy(intentIdentity = reference.identity + ":reconciliation-result"))).asJson,
        selected.copy(resumeFrom = Some(prior.copy(intentRevision = "2"))).asJson,
        selected.copy(resumeFrom = Some(prior.copy(resultIdentity = Some("crossed-result")))).asJson,
        selected.copy(resumeFrom = Some(prior.copy(resultRevision = None))).asJson,
        selected.copy(resumeFrom = Some(prior.copy(outcome = None))).asJson,
        selected.copy(resumeFrom = Some(prior.copy(outcome = Some("updated-current")))).asJson,
        selected.copy(frontierRevision = (selected.frontierRevision.toLong - 2).toString).asJson,
        selected.copy(pendingQuestion = original.pendingQuestion).asJson,
        unresolved.copy(resumeFrom = Some(prior.copy(outcome = None))).asJson)
      When("strict decoding sees fields that cannot express the actual adjacent non-success relationship")
      val rejected = malformed.map(N.decodeTarget)
      val collision = N.decodePublicTarget(wire.mapObject(_.add("resume_from", prior.asJson)))
      Then("each malformed combination and public-name collision refuses")
      rejected.foreach(_.isLeft shouldBe true)
      collision.isLeft shouldBe true
    }

    "roundtrip complete pending targets with eight successful original intents and reject incomplete or crossed fields" in {
      Given("SHR-05: the later-intent8 legacy unanswered Human shape and complete immutable prefix")
      val selected = target(8)
      val wire = selected.asJson
      When("public property naming and exact canonical codecs transport the target")
      val transported = take(N.targetC(RuntimeContext.PropertyNameContext.default.transformRecord(N.targetRecord(selected))))
      Then("the public transport preserves the complete target")
      transported shouldBe selected
      Then("all eight prefix payloads remain exact and missing extra null crossed or colliding fields refuse")
      selected.recovery.completedPrefix.size shouldBe 8
      N.decodeTarget(wire) shouldBe Right(selected)
      val malformed = Vector(wire.mapObject(_.remove("frontierCause")), wire.mapObject(_.add("policy", Json.Null)),
        wire.mapObject(_.add("unrecognized", Json.True)), wire.mapObject(_.add("pendingQuestion", Json.Null)),
        wire.mapObject(_.add("frontierRevision", Json.fromString("71"))),
        wire.mapObject(_.add("recovery", selected.recovery.asJson.mapObject(_.add("unexpected", Json.True)))))
      malformed.foreach(value => N.decodeTarget(value).isLeft shouldBe true)
      N.decodePublicTarget(wire.mapObject(_.add("frontier_revision", Json.fromString(selected.frontierRevision)))).isLeft shouldBe true
      N.decodeTarget(selected.copy(pendingQuestion = Some(N.Question("other-question", selected.frontierRevision))).asJson).isLeft shouldBe true
      val absent = target(0, pending = false)
      absent.asJson.hcursor.downField("pendingQuestion").focus shouldBe None
      N.decodeTarget(absent.asJson) shouldBe Right(absent)
    }

    "admit only dedicated independent policy direction with actual prior-ended attestation" in {
      Given("SHR-03,SHR-05: already held policy direction and independent test host terminal evidence")
      val selected = target()
      val binding = take(N.bindingC("component", selected, N.choices.head, "Adopt the held policy", "participant", artifact, true))
      val now = Instant.parse("2026-10-26T00:00:00Z")
      val declared = NativeExternalEvidence.Declaration("workflow", org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(
        org.simplemodeling.model.datatype.EntityCollectionId("smworkflow", "cli", "host_evidence")), "test-host")
      val evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, N.purpose, "lookup", artifact.identity,
        artifact.revision, declared.issuer, "participant", binding, now.toString)
      When("the exact policy binding and foreign legacy purpose or prior-ended=false assertions are presented")
      val observed = NativeExternalEvidence.observeC(evidence, declared, NativeExternalEvidence.Purpose.SourceHistoryNormalization,
        "lookup", binding, now).isSuccess
      Then("the exact independent policy evidence is admitted")
      observed shouldBe true
      When("adoption asserts no actual prior-ended evidence")
      val unterminated = N.bindingC("component", selected, N.choices.head, "No terminal evidence", "participant", artifact, false).isSuccess
      Then("the false terminal assertion refuses adoption")
      unterminated shouldBe false
      When("the existing direction explicitly defers without terminal evidence")
      val deferred = N.bindingC("component", selected, "Defer", "Retain current state", "participant", artifact, false).isSuccess
      Then("the inert Defer direction is admitted")
      deferred shouldBe true
      Then("legacy recovery, crossed participant/reference/issuer/time and annotated bindings provide no fallback")
      Vector(evidence.copy(purpose = R.purpose), evidence.copy(participant = "other"), evidence.copy(reference = "other"),
        evidence.copy(issuer = "other-host"), evidence.copy(occurredAt = now.plusSeconds(1).toString)).foreach(value =>
        NativeExternalEvidence.observeC(value, declared, NativeExternalEvidence.Purpose.SourceHistoryNormalization,
          "lookup", binding, now).isSuccess shouldBe false)
      N.selectBinding(binding) shouldBe Right(binding)
      N.selectBinding(binding.mapObject(_.add("aiClaimsTermination", Json.True))).isLeft shouldBe true
      R.selectBinding(binding).isLeft shouldBe true
    }

    "passively encode actual description answer and prior-ended assertion without changing the old unknown receipt" in {
      Given("an actual typed normalization description and already held test-owned policy reference")
      val selected = target()
      val old = GoalPhaseNativeSourceHistory.codec.encode(selected.recovery.intent)
      val description = V.GoalPhaseSourceHistoryNormalizationDescriptionResult("session", "adoption-required", N.message,
        "component", N.targetRecord(selected), N.choices, N.adoptionOperation)
      val answer = V.GoalPhaseSourceHistoryNormalizationAnswer(N.choices.head, "Use the already held policy", true)
      val reference = V.WorkflowArtifactReference(artifact.identity, artifact.revision)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      val now = Instant.parse("2026-10-26T00:00:00Z")
      When("passive preparation binds the actual supplied facts")
      val prepared = take(NativeSourceHistoryNormalizationPreparation.prepareC(description, answer, reference,
        execution, "participant", "test-host", now))
      val stored = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      Then("only dedicated inert import data is returned and no old Human answer or original result is fabricated")
      prepared.recordIdentity should startWith(N.purpose + ":")
      stored.binding.hcursor.downField("sourceHistoryNormalization").downField("answer").get[Boolean]("priorExecutionEnded") shouldBe Right(true)
      GoalPhaseNativeSourceHistory.codec.encode(selected.recovery.intent) shouldBe old
      selected.recovery.intent.outcome shouldBe None
      NativeSourceHistoryNormalizationPreparation.prepareC(description, answer.copy(priorExecutionEnded = false), reference,
        execution, "participant", "test-host", now).isSuccess shouldBe false
      NativeSourceHistoryNormalizationPreparation.prepareC(description.copy(choices = N.choices.reverse), answer, reference,
        execution, "participant", "test-host", now).isSuccess shouldBe false
      NativeSourceHistoryNormalizationPreparation.prepareC(description, answer, reference,
        execution, "other", "test-host", now).isSuccess shouldBe false
    }
  }
}
