package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.cncf.processexecution.{ProcessExecutionCapture, ProcessExecutionResult, ProcessExecutionTermination}
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.protocol.Request
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import RepositorySyncRepairBudgetContinuation as B
import RepositorySyncNativeProgressStore as N
import RepositorySyncPurposeRepairScopeFixture as F
import SplitPhaseExplicitPartitionFixture.take

private[admission] object RepositorySyncRepairBudgetContinuationFixture {
  val cause = "native-repository:session:purpose-1-check-1-result"
  val used = Vector("session:native-repair:7")
  val target = B.Target("component", "session", F.context, F.request.root, "/specification/upstream.git",
    "10", cause, "10", cause, "10", cause, F.scope.failureReference, Some(F.failed), F.review,
    Some(F.snapshot), 1, 1, used, 1, B.policy)
}

/** Pure finite allowance contracts. These authored inputs provide no native
  * history, Human approval, check execution or actual consumer evidence.
  * @since Nov. 4, 2026
  * @version Nov. 4, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncRepairBudgetContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _used = RepositorySyncRepairBudgetContinuationFixture.used
  private val _target = RepositorySyncRepairBudgetContinuationFixture.target
  private val _reference = ContextReference("fixture-human-finite-budget", "1")
  private def _direction(target: B.Target = _target, additional: Int = 1): B.Direction =
    B.Direction(target, "ContinueRepairs", additional, "Specification-owned additional finite repair allowance",
      "participant", _reference)
  private def _binding(target: B.Target = _target, additional: Int = 1, component: String = "component",
    participant: String = "participant", reference: ContextReference = _reference) =
    B.bindingC(component, target, "ContinueRepairs", additional, "Specification-owned finite direction", participant, reference)

  "ID10 exact finite repair budget target" should {
    "retain original allowance candidate check selection and opaque Purpose progress in canonical public round trips" in {
      Given("an authored exhausted Purpose repair target with one original repair and the actual candidate-shaped failed check")
      val wire = _target.asJson
      When("the strict retained and public Record decoders consume the same target")
      val decoded = B.decodeTarget(wire)
      val restored = take(B.targetC(B.targetRecord(_target)))
      Then("all original history budget and candidate facts survive without resetting the count")
      decoded shouldBe Right(_target)
      restored shouldBe _target
      restored.purpose.get.progress.request.records shouldBe F.request.records
      restored.usedIdentities shouldBe _used
      restored.originalLimit shouldBe 1
      B.targetChoices(restored) shouldBe Vector("ContinueRepairs", "Defer")
    }
    "roundtrip complete raw exited SBT Test failure observations consistently across the target review and opaque Purpose progress" in {
      Given("an authored raw SBT Test exit-one observation with complete Unicode stdout stderr and matching typed failed facts")
      val stdout = "[error] FixtureSpec: retained expected value differed — 失敗\n"
      val stderr = "[error] Authored complete Test failure diagnostics\n"
      val outbytes = stdout.getBytes(UTF_8).toVector
      val errbytes = stderr.getBytes(UTF_8).toVector
      val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(1),
        ProcessExecutionCapture(outbytes, outbytes.size.toLong, false),
        ProcessExecutionCapture(errbytes, errbytes.size.toLong, false), Vector.empty, 17L, "sbt")
      val facts = WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Test, WorkflowSbtValidation.Outcome.Failed, None, None)
      val receipt = F.failed.copy(stdout = stdout, stderr = stderr, sbt = Some(facts), processObservation = Some(process))
      val opaquefacts = Json.obj("sbt" -> Json.obj("kind" -> Json.fromString("test"), "outcome" -> Json.fromString("failed"),
        "selection" -> Json.Null, "reason" -> Json.Null))
      val snapshot = _target.purpose.get.copy(progress = _target.purpose.get.progress.copy(
        receipts = _target.purpose.get.progress.receipts.map(value => if (value.identity == receipt.identity) receipt else value),
        effects = _target.purpose.get.progress.effects :+ opaquefacts))
      val rawtarget = _target.copy(failedCheck = Some(receipt), review = _target.review.copy(
        checks = _target.review.checks.map(value => if (value.identity == receipt.identity) receipt else value)), purpose = Some(snapshot))
      When("the strict decoder and public Record path consume the complete raw target")
      val wire = rawtarget.asJson
      val processwire = WorkflowSbtCapture.encode(process)
      val decoded = B.decodeTarget(wire)
      val restored = B.targetC(B.targetRecord(rawtarget))
      Then("both round trips preserve the raw observation exact failed facts and opaque progress without changing candidate validity")
      WorkflowSbtCapture.valid(process, facts) shouldBe true
      WorkflowSbtCapture.decode(processwire) shouldBe Right(process)
      RepositorySyncPurposeRuntime.validRepairSnapshot(snapshot) shouldBe true
      decoded shouldBe Right(rawtarget)
      withClue(restored.toString + ": ") { restored.toOption shouldBe Some(rawtarget) }
      wire.hcursor.downField("failedCheck").get[Json]("process") shouldBe Right(processwire)
      wire.hcursor.downField("failedCheck").downField("sbt").downField("selection").focus shouldBe None
      wire.hcursor.downField("failedCheck").downField("sbt").downField("reason").focus shouldBe None
      val reviewreceipt = wire.hcursor.downField("review").get[Vector[Json]]("checks").toOption.get.last
      reviewreceipt.hcursor.downField("sbt").downField("selection").focus shouldBe None
      reviewreceipt.hcursor.downField("sbt").downField("reason").focus shouldBe None
      wire.hcursor.downField("purpose").get[Json]("progress") shouldBe snapshot.asJson.hcursor.get[Json]("progress")
      val opaquerestored = take(restored).purpose.get.progress.effects.last
      opaquerestored.hcursor.downField("sbt").downField("selection").focus shouldBe Some(Json.Null)
      opaquerestored.hcursor.downField("sbt").downField("reason").focus shouldBe Some(Json.Null)
      rawtarget.review.checks.last shouldBe receipt
      rawtarget.purpose.get.progress.receipts.last shouldBe receipt
      rawtarget.purpose.get.progress.request.records shouldBe _target.purpose.get.progress.request.records

      Given("the complete raw target with explicit null or unknown fields added at either outer receipt's typed SBT facts")
      val malformed = Vector("selection" -> Json.Null, "reason" -> Json.Null, "unrecognizedFact" -> Json.True).flatMap {
        (field, value) => Vector(
          ("failedCheck.sbt." + field) -> wire.hcursor.downField("failedCheck").downField("sbt")
            .withFocus(_.mapObject(_.add(field, value))).top.get,
          ("review.checks.last.sbt." + field) -> wire.hcursor.downField("review").downField("checks")
            .withFocus(_.mapArray(values => values.updated(values.size - 1, values.last.mapObject(fields =>
              fields.add("sbt", fields("sbt").get.mapObject(_.add(field, value))))))).top.get)
      }
      When("the strict decoder reads those caller-supplied noncanonical facts without input normalization")
      val rejected = malformed.map((clue, value) => clue -> B.decodeTarget(value))
      Then("explicit null and unknown outer fact fields refuse while the original opaque null facts stay preserved")
      rejected.foreach((clue, result) => withClue(clue + ": ") { result.isLeft shouldBe true })
      snapshot.progress.effects.last shouldBe opaquefacts
    }
    "distinguish malformed finite candidate facts from a noncanonical complete target contract" in {
      Given("one contradictory used count and one complete valid target carrying an unknown extra field")
      val malformed = _target.copy(usedRepairs = 0).asJson
      val noncanonical = _target.asJson.mapObject(_.add("unrecognizedField", Json.True))
      When("the unchanged strict contract checks candidate facts and exact canonical structure separately")
      val malformedresult = B.decodeTarget(malformed)
      val noncanonicalresult = B.decodeTarget(noncanonical)
      Then("each strict refusal reports which condition failed without dropping the unknown field")
      malformedresult.swap.toOption.get should include("malformed finite candidate facts")
      noncanonicalresult.swap.toOption.get should include("noncanonical complete contract")
    }
    "refuse missing null unknown mistyped crossed failure and reset budget facts" in {
      Given("the complete exact target and structurally malformed alternatives")
      val wire = _target.asJson
      val malformed = wire.asObject.get.keys.toVector.flatMap(key =>
        (if (Set("purpose", "failedCheck")(key)) Vector.empty else Vector("missing " + key -> wire.mapObject(_.remove(key)))) ++
          Vector("null " + key -> wire.mapObject(_.add(key, Json.Null)),
            "mistyped " + key -> wire.mapObject(_.add(key, Json.True)))) :+
        ("unknown unlimited field" -> wire.mapObject(_.add("unlimited", Json.True)))
      val crossed = Vector(_target.copy(sessionId = "foreign"), _target.copy(root = "relative"),
        _target.copy(sourceRevision = "9"), _target.copy(failedCause = "native-repository:foreign:failure"),
        _target.copy(failureReference = ContextReference(F.scope.failureReference.identity, "2")),
        _target.copy(failedCheck = Some(F.failed.copy(termination = "unknown", exitCode = None))),
        _target.copy(review = F.review.copy(indexTree = "f" * 40)), _target.copy(usedRepairs = 0),
        _target.copy(usedIdentities = _used ++ _used, usedRepairs = 2),
        _target.copy(usedIdentities = Vector("session:native-repair:7:scope:9")),
        _target.copy(originalLimit = -1), _target.copy(effectiveLimit = 2), _target.copy(continuationPolicy = "foreign"))
      When("exact decoding checks every malformed field and candidate relationship")
      val rejected = malformed.map((clue, value) => clue -> B.decodeTarget(value)) ++
        crossed.zipWithIndex.map((value, index) => s"crossed target alternative $index" -> B.decodeTarget(value.asJson))
      Then("none is a canonical finite allowance target")
      rejected.foreach((clue, result) => withClue(clue + ": ") { result.isLeft shouldBe true })
      B.decodeTarget(null).isLeft shouldBe true
    }
    "require the last settled failed check even without a Purpose snapshot and preserve successful review-origin targets" in {
      Given("a failed check-origin target without optional Purpose progress and a successful review-origin target")
      val failed = _target.copy(purpose = None)
      val successful = failed.copy(failedCheck = None, review = F.review.copy(
        checks = F.review.checks.updated(F.review.checks.size - 1, F.failed.copy(exitCode = Some(0)))))
      When("the exact decoder receives a missing failed receipt or a contradictory failed receipt on a successful source")
      val missing = B.decodeTarget(failed.copy(failedCheck = None).asJson)
      val contradictory = B.decodeTarget(successful.copy(failedCheck = Some(F.failed)).asJson)
      Then("known failed checks require their exact receipt while successful review-origin candidates retain no failedCheck")
      B.decodeTarget(failed.asJson) shouldBe Right(failed)
      missing.isLeft shouldBe true
      contradictory.isLeft shouldBe true
      B.decodeTarget(successful.asJson) shouldBe Right(successful)
    }
  }
  "CB-ID10-01 review source operation ownership" should {
    "admit budget description and adoption at the existing review source gate and refuse crossed or unrelated operations" in {
      Given("the two exact RepositorySync budget operation identities and a retained review source ownership gate")
      val operations = Vector(B.describeOperation, B.adoptionOperation)
      val requests = operations.map(operation => Request.ofOperation(operation).copy(service = Some("RepositorySyncService")))
      When("the same gate used by the loaded native review source checks these public operation requests")
      val admitted = requests.map(RepositorySyncNativeMergeReviewContinuation.requireSourceOperationC)
      val crossed = requests.map(request => RepositorySyncNativeMergeReviewContinuation.requireSourceOperationC(
        request.copy(service = Some("OtherService"))))
      val unrelated = RepositorySyncNativeMergeReviewContinuation.requireSourceOperationC(
        Request.ofOperation("unrelatedOperation").copy(service = Some("RepositorySyncService")))
      Then("both budget operations may read the actual review source and no other service or unrelated operation is admitted")
      admitted.foreach(_.isSuccess shouldBe true)
      crossed.foreach(_.isSuccess shouldBe false)
      unrelated.isSuccess shouldBe false
      RepositorySyncNativeMergeReviewContinuation.requireSourceOperationC(null).isSuccess shouldBe false
    }
  }
  "ID10 independent finite Human answer" should {
    "require a positive finite Int for continuation and exactly zero for passive Defer" in {
      Given("finite continuation and passive Defer answers plus invalid numeric and extended shapes")
      val answer = B.Answer("ContinueRepairs", 1, "One further actual repair")
      val defer = B.Answer("Defer", 0, "Keep the original exhausted state")
      val wrong = Vector(answer.copy(additionalRepairs = 0).asJson, answer.copy(additionalRepairs = -1).asJson,
        answer.copy(choice = "Unlimited").asJson, answer.copy(rationale = " ").asJson,
        answer.asJson.mapObject(_.add("additionalRepairs", Json.fromLong(Int.MaxValue.toLong + 1))),
        answer.asJson.mapObject(_.add("additionalRepairs", Json.fromDoubleOrNull(1.5))),
        answer.asJson.mapObject(_.add("additionalRepairs", Json.Null)),
        answer.asJson.mapObject(_.add("allowReset", Json.True)), defer.copy(additionalRepairs = 1).asJson)
      When("the exact public answer decoder reads the supplied values")
      val continued = B.decodePublicAnswer(answer.asJson)
      val deferred = B.decodePublicAnswer(defer.asJson)
      val rejected = wrong.map(B.decodePublicAnswer)
      Then("only the positive finite continuation or zero Defer survives")
      continued shouldBe Right(answer)
      deferred shouldBe Right(defer)
      rejected.foreach(_.isLeft shouldBe true)
      B.decodePublicAnswer(Json.Null).isLeft shouldBe true
    }
    "check addition without a numerical ceiling and reject overflow instead of wrapping or granting unlimited repairs" in {
      Given("one cumulatively used repair and an exhausted allowance of one")
      When("the finite checked arithmetic consumes the largest fitting addition and the first overflow")
      val maximum = B.effectiveLimitC(_target, Int.MaxValue - 1)
      val overflow = B.effectiveLimitC(_target, Int.MaxValue)
      Then("the full Int range remains available while overflow refuses")
      take(maximum) shouldBe Int.MaxValue
      overflow.isSuccess shouldBe false
      B.effectiveLimitC(_target, 0).isSuccess shouldBe false
      B.effectiveLimitC(_target, -1).isSuccess shouldBe false
      _binding(additional = Int.MaxValue).isSuccess shouldBe false
    }
    "bind the actual component participant target answer and artifact reference and reject crossed evidence" in {
      Given("the exact described target and a supplied independent Human-shaped answer")
      When("the binding is selected and crossed components participants references or extra fields are supplied")
      val binding = take(_binding())
      val rejected = Vector(_binding(component = "foreign"), _binding(participant = "foreign"),
        _binding(reference = ContextReference("", "1")), _binding(target = _target.copy(context = F.context.copy(participantIdentity = "other"))))
      Then("only the exact binding can be offered to the separate evidence verifier")
      B.selectBinding(binding) shouldBe Right(binding)
      rejected.foreach(_.isSuccess shouldBe false)
      B.selectBinding(binding.mapObject(_.add("componentInstance", Json.fromString("foreign")))).isLeft shouldBe true
      B.selectBinding(binding.mapObject(_.add("callerApproval", Json.True))).isLeft shouldBe true
      val changed = binding.hcursor.downField("repositoryRepairBudgetContinuation").focus.get.mapObject(_.add("answer",
        binding.hcursor.downField("repositoryRepairBudgetContinuation").downField("answer").focus.get
          .mapObject(_.add("additionalRepairs", Json.fromInt(0)))))
      B.selectBinding(binding.mapObject(_.add("repositoryRepairBudgetContinuation", changed))).isLeft shouldBe true
    }
    "retain complete large rationale and persist only strict continuation directions" in {
      Given("a complete large Unicode Human-shaped rationale and an exact typed direction")
      val direction = _direction().copy(rationale = "有限の追加修正を認める根拠" * 10000)
      When("the retained direction decoder reads complete and contradictory versions")
      val decoded = direction.asJson.as[B.Direction]
      Then("the complete direction survives while Defer unknown null and extra persisted fields refuse")
      decoded shouldBe Right(direction)
      direction.copy(choice = "Defer", additionalRepairs = 0).asJson.as[B.Direction].isLeft shouldBe true
      direction.copy(participant = "foreign").asJson.as[B.Direction].isLeft shouldBe true
      direction.asJson.mapObject(_.add("unlimited", Json.True)).as[B.Direction].isLeft shouldBe true
      direction.asJson.mapObject(_.add("reference", Json.Null)).as[B.Direction].isLeft shouldBe true
    }
  }
  "ID10 cumulative finite allowance accounting" should {
    "chain two grants through the prior effective limit without resetting or refunding original used identities" in {
      Given("the original budget and an exhausted first source with one cumulative repair")
      val original = B.Budget(1, 1, Vector.empty)
      When("one finite grant is followed by a genuinely new repair and another exhausted source")
      val first = take(B.grantC(original, _direction(), _used))
      val nextused = _used :+ "session:native-repair:10"
      val nexttarget = _target.copy(frontierRevision = "20", sourceRevision = "20", failedRevision = "20",
        review = F.review.copy(validatedRevision = "20"), effectiveLimit = 2, usedIdentities = nextused, usedRepairs = 2)
      val second = take(B.grantC(first, _direction(nexttarget, 2), nextused))
      Then("original allowance and every used identity remain while effective allowance grows by exact finite additions")
      first shouldBe B.Budget(1, 2, _used)
      first.remaining shouldBe 1
      second shouldBe B.Budget(1, 4, nextused)
      second.remaining shouldBe 2
      second.originalLimit shouldBe original.originalLimit
    }
    "refuse stale previous limits refunded counts rewritten identities and repeated same-source grants" in {
      Given("an actual prior grant and the cumulative identity prefix it retained")
      val previous = B.Budget(1, 2, _used)
      val nextused = _used :+ "session:native-repair:10"
      val target = _target.copy(frontierRevision = "20", sourceRevision = "20", failedRevision = "20",
        review = F.review.copy(validatedRevision = "20"), effectiveLimit = 2, usedIdentities = nextused, usedRepairs = 2)
      val reset = target.copy(usedIdentities = Vector("session:native-repair:9", "session:native-repair:10"))
      val same = target.copy(sourceRevision = "10", failedRevision = "10", review = F.review)
      When("a caller attempts a stale original limit original reset refund identity rewrite or another grant for an already issued source")
      val rejected = Vector(B.grantC(previous, _direction(target.copy(effectiveLimit = 1)), nextused),
        B.grantC(previous, _direction(target.copy(originalLimit = 0)), nextused),
        B.grantC(previous, _direction(target.copy(usedRepairs = 1)), nextused),
        B.grantC(previous, _direction(reset), reset.usedIdentities), B.grantC(previous, _direction(same), nextused),
        B.grantC(previous, _direction(target), Vector.empty))
      Then("none creates a grant or erases cumulative repair history")
      rejected.foreach(_.isSuccess shouldBe false)
      previous shouldBe B.Budget(1, 2, _used)
      previous.copy(usedIdentities = nextused).exhausted shouldBe true
    }
    "normalize an administrative scope suffix to the original repair identity without counting it twice" in {
      Given("one original repair and its separate scope-administrative continuation identity")
      val identities = Vector("session:native-repair:7", "session:native-repair:7:scope:9", "session:native-repair:10")
      When("the same normalizer used by actual native history accounting consumes those identities")
      val normalized = identities.map(RepositorySyncRepairScopeCorrection.budgetIdentity).distinct
      Then("the administrative replacement costs no second repair but the new failed source does")
      normalized shouldBe Vector("session:native-repair:7", "session:native-repair:10")
      B.Budget(1, 2, normalized).usedRepairs shouldBe 2
    }
  }
  "ID10 backward compatible native progress provenance" should {
    "omit the optional grant on old rows and refuse explicit null or malformed new direction" in {
      Given("an old native failed payload and the same payload carrying an explicit finite direction")
      val old = N.Payload("PurposeRepairRequired", F.request.root, F.actual.observed, purposes = Some(F.progress))
      val admitted = old.copy(stage = "RepairBudgetContinuationAdmitted", repairBudget = Some(_direction()))
      val oldwire = N.codec.encode(old)
      val wire = N.codec.encode(admitted)
      When("the unchanged schema decoder reads old new null and malformed optional provenance")
      val nullpayload = oldwire.hcursor.downField("payload").focus.get.mapObject(_.add("repairBudget", Json.Null))
      val malformedpayload = wire.hcursor.downField("payload").focus.get.mapObject(_.add("repairBudget",
        _direction().asJson.mapObject(_.add("additionalRepairs", Json.fromInt(0)))))
      Then("old history stays literal new directions roundtrip and explicit null cannot masquerade as absent")
      oldwire.hcursor.downField("payload").downField("repairBudget").focus shouldBe None
      N.codec.decode(oldwire) shouldBe Right(old)
      N.codec.decode(wire) shouldBe Right(admitted)
      N.codec.decode(oldwire.mapObject(_.add("payload", nullpayload))).isLeft shouldBe true
      N.codec.decode(wire.mapObject(_.add("payload", malformedpayload))).isLeft shouldBe true
    }
  }
}
