package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativeRepositoryValidationContinuationPreparation as P
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncRequiredLintContinuation as L
import RepositorySyncValidationContinuation as N
import RepositorySyncNativeMergeValidation as C
import RepositorySyncManagedGitObservation as G
import SplitPhaseExplicitPartitionFixture.take

/** These are controlled pure contract facts, not actual native process evidence.
  * The installed suite retains genuine old intent/result rows separately.
  */
private[admission] object RepositorySyncRequiredLintContinuationFixture {
  val oldChecks = Vector(C.Check("compile1", 1, WorkflowSbtValidation.Kind.Compile),
    C.Check("test2", 2, WorkflowSbtValidation.Kind.Test))
  val checks = oldChecks :+ C.Check("lint3", 3, WorkflowSbtValidation.Kind.FixCheck)
  val context = RepositorySyncManagedFetch.StartContext("selection", "invocation", "participant", "repository",
    "main", "origin", "main", "owned-upstream", "FULL_STATE")
  val observed = G.Observed("main", "a" * 40, "d" * 40,
    Vector(G.DirtyPath('M', ' ', "src/main/scala/Input.scala", None)), Some("b" * 40),
    Some(G.Counts(1, 1)), Some("c" * 40))
  val origins = (Vector("index-local", "index-remote") ++ oldChecks.map(_.identity)).zipWithIndex.map { (identity, index) =>
    L.Origin(ContextReference("session:native-repair-2-validation-intent-" + index, "1"), (7 + 2 * index).toString,
      ContextReference("session:native-repair-2-validation-result-" + index, "1"), (8 + 2 * index).toString,
      identity, "exited", Some(0), oldChecks.lift(index - 2).map(check => WorkflowSbtValidation.Facts(check.kind,
        WorkflowSbtValidation.Outcome.Succeeded, None, None)))
  }
  val target = L.Target("session", context, "component", "/specification/repository", "/specification/upstream.git",
    "15", "native-repository:session:repair-2-merge-validated", "repair-2",
    ContextReference("session:native-repair-2-pending-merge", "1"), "6", observed, observed, observed.tree,
    "b" * 40, "merge", "", ContextReference("session:native-repair-2-merge-validated", "1"), "15",
    L.Selection("required", "Pure fixture Compile and Test selection", oldChecks, "6", None, None), origins,
    C.Policy("required-lint-fixture", 1, "required", "Add the actual missing fixed lint check", checks),
    checks, None, observed, observed.tree, L.policy)
  val reference = ContextReference("fixture-required-lint-direction", "1")
  val answer = N.Answer("AuthorizeMissingRequiredLint", "Pure fixture direction retains successful checks", false, false)
  val prefix = origins.map(origin => C.Receipt(origin.identity, origin.termination, origin.exitCode, "", "", origin.sbt))
  def _binding_(selected: L.Target = target, component: String = "component", participant: String = "participant",
    ended: Boolean = false, uncertainty: Boolean = false) = L.bindingC(component, selected, answer.choice,
    answer.rationale, participant, reference, ended, uncertainty)
}

/** Strict v2 append-only selection and independent evidence ingress, with v1
  * decoding preserved. Fixture facts never authenticate real native provenance.
  * @since Nov. 3, 2026
  * @version Nov. 3, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncRequiredLintContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import RepositorySyncRequiredLintContinuationFixture.*

  "DEV-RS-RETAINED-REQUIRED-LINT explicit versioned contracts" should {
    "dispatch recognized targets explicitly while preserving the historical v1 decoder and boolean meaning" in {
      Given("a pure v2 retained successful selection and a distinct historical resultless v1 target")
      val old = RepositorySyncValidationContinuationFixture.target
      val oldanswer = N.Answer("AuthorizeNewValidation", "Fixture terminal uncertainty direction", true, true)
      When("the public facade selects each branch from its explicit continuationPolicy")
      val current = N.decodeAnyTarget(target.asJson).toOption.get
      val previous = N.decodeAnyTarget(old.asJson).toOption.get
      Then("each typed branch accepts only its own answers without any v1 intent placeholder or fallback")
      current shouldBe N.RequiredLintTarget(target)
      previous shouldBe N.OriginalTarget(old)
      N.decodeTarget(old.asJson) shouldBe Right(old)
      N.decodeTarget(target.asJson).isLeft shouldBe true
      N.decodeAnyAnswer(current, answer.asJson) shouldBe Right(answer)
      N.decodeAnyAnswer(previous, oldanswer.asJson) shouldBe Right(oldanswer)
      N.decodeAnyAnswer(current, oldanswer.asJson).isLeft shouldBe true
      N.decodeAnyAnswer(previous, answer.asJson).isLeft shouldBe true
      N.decodeAnyAnswer(previous, oldanswer.copy(priorExecutionEnded = false).asJson).isLeft shouldBe true
      N.decodeAnyAnswer(previous, oldanswer.copy(acceptOriginalUncertainty = false).asJson).isLeft shouldBe true
      N.anyTargetC(L.targetRecord(target)).toOption shouldBe Some(current)
      N.decodePublicAnyTarget(target.asJson) shouldBe Right(current)
      val fields = target.asJson.asObject.get.keys.toSet
      fields should not contain "intentReference"
      fields should not contain "successfulPrefix"
      Vector("unknown-policy", N.policy).foreach { policy =>
        N.decodeAnyTarget(target.asJson.mapObject(_.add("continuationPolicy", policy.asJson))).isLeft shouldBe true
      }
    }

    "refuse missing null extra and malformed target answer and persisted direction fields" in {
      Given("an exact complete v2 target and false false answer with specification-owned facts")
      val wire = target.asJson
      val direction = L.Direction(target, answer.choice, answer.rationale, context.participantIdentity, reference, false, false)
      When("required meaning-bearing fields or canonical values are absent changed or extended")
      val malformed = wire.asObject.get.keys.toVector.flatMap(key => Vector(wire.mapObject(_.remove(key)),
        wire.mapObject(_.add(key, Json.Null)))) :+ wire.mapObject(_.add("silentRetry", Json.True))
      val answerwire = answer.asJson
      val badanswers = answerwire.asObject.get.keys.toVector.flatMap(key => Vector(answerwire.mapObject(_.remove(key)),
        answerwire.mapObject(_.add(key, Json.Null)))) :+ answerwire.mapObject(_.add("acceptOldSuccessAsUnknown", Json.True))
      Then("strict codecs refuse malformed contracts and only the explicit adopting direction persists")
      L.decodeTarget(wire) shouldBe Right(target)
      malformed.foreach(value => L.decodeTarget(value).isLeft shouldBe true)
      badanswers.foreach(value => L.decodeAnswer(value).isLeft shouldBe true)
      direction.asJson.as[L.Direction] shouldBe Right(direction)
      direction.copy(choice = "Defer").asJson.as[L.Direction].isLeft shouldBe true
      Vector(answer.copy(priorExecutionEnded = true), answer.copy(acceptOriginalUncertainty = true),
        answer.copy(choice = "AuthorizeNewValidation"), answer.copy(rationale = answer.rationale + "\n"))
        .foreach(value => L.decodeAnswer(value.asJson).isLeft shouldBe true)
      L.decodeAnswer(answer.copy(choice = "Defer").asJson).isRight shouldBe true
      Vector(target.copy(frontierRevision = "015"), target.copy(retainedRevision = "14"),
        target.copy(sessionId = "crossed"), target.copy(frontierCause = "native-repository:other:merge-validated"),
        target.copy(namespace = "validation-continuation-2"), target.copy(componentInstance = ""))
        .foreach(value => L.decodeTarget(value.asJson).isLeft shouldBe true)
    }
  }

  "DEV-RS-RETAINED-REQUIRED-LINT candidate and successful prefix" should {
    "retain historical unresolved conflict observation while requiring resolved candidate and current observations" in {
      Given("a retained historical unresolved conflict and the unchanged resolved candidate and current observations")
      val historicalbefore = target.candidateBefore.copy(dirty = target.candidateBefore.dirty :+
        G.DirtyPath('U', 'U', "value.txt", None))
      val selected = target.copy(candidateBefore = historicalbefore)
      val invalidbefore = historicalbefore.copy(dirty = target.candidateBefore.dirty :+
        G.DirtyPath('Z', 'Z', "value.txt", None))
      When("the v2 contract validates and roundtrips the target and examines unresolved current or malformed historical observations")
      val admitted = L.validTarget(selected)
      val restored = L.decodeTarget(selected.asJson)
      val refused = Vector(selected.copy(candidateAfter = historicalbefore), selected.copy(actual = historicalbefore),
        selected.copy(candidateAfter = historicalbefore, actual = historicalbefore),
        selected.copy(candidateBefore = invalidbefore)).map(L.validTarget)
      Then("only the exact historical Git conflict is retained while resolved and current observations remain strict")
      admitted shouldBe true
      restored shouldBe Right(selected)
      restored.map(_.asJson) shouldBe Right(selected.asJson)
      refused.foreach(_ shouldBe false)
    }

    "allow exactly one appended fixed FixCheck without deleting reordering rebinding or adding other product checks" in {
      Given("retained Compile and Test identities ordinals kinds and their actual-shaped origin locators")
      val extracompile = C.Check("extraCompile", 3, WorkflowSbtValidation.Kind.Compile)
      val extratest = C.Check("extraTest", 3, WorkflowSbtValidation.Kind.Test)
      val bad = Vector(checks.tail, checks.reverse, Vector(checks(1), checks(0), checks(2)),
        checks.updated(0, checks.head.copy(identity = "otherCompile")), checks.updated(0, checks.head.copy(ordinal = 4)),
        checks.updated(0, checks.head.copy(kind = WorkflowSbtValidation.Kind.Test)),
        oldChecks :+ extracompile, oldChecks :+ extratest,
        checks :+ C.Check("extraLint", 4, WorkflowSbtValidation.Kind.FixCheck))
      When("the current selected checks differ from the retained ordered prefix or append more than the missing lint")
      val admitted = L.validTarget(target)
      val refused = bad.map(selected => L.validTarget(target.copy(expandedChecks = selected,
        validationPolicy = target.validationPolicy.copy(checks = selected))))
      val withoutlint = L.validTarget(target.copy(validationPolicy = target.validationPolicy.copy(checks = oldChecks)))
      val prefixresults = Vector(prefix, prefix.dropRight(1), prefix.reverse,
        prefix.updated(3, prefix(3).copy(exitCode = Some(1)))).map(L.validPrefix(_, oldChecks))
      Then("only the exact append-only selection passes and old successful facts remain unchanged")
      admitted shouldBe true
      refused.foreach(_ shouldBe false)
      withoutlint shouldBe false
      target.retainedSelection.selected shouldBe oldChecks
      prefixresults shouldBe Vector(true, false, false, false)
    }

    "refuse crossed original candidate receipt lineage failed facts and current candidate drift" in {
      Given("a same-candidate target whose origin reference pairs name native intent and result records")
      val first = origins.head
      val last = origins.last
      val failed = last.sbt.get.copy(outcome = WorkflowSbtValidation.Outcome.Failed)
      val badorigins = Vector(origins.reverse, origins.dropRight(1),
        origins.updated(0, first.copy(intentReference = first.intentReference.copy(identity = "foreign-intent"))),
        origins.updated(0, first.copy(resultReference = first.resultReference.copy(identity = "foreign-result"))),
        origins.updated(0, first.copy(resultRevision = first.intentRevision)),
        origins.updated(3, last.copy(identity = "otherTest")),
        origins.updated(3, last.copy(termination = "timed-out")),
        origins.updated(3, last.copy(exitCode = Some(1))), origins.updated(3, last.copy(sbt = Some(failed))))
      When("source selection candidate tree receipt attribution or actual HEAD index and tracking do not match")
      val badtargets = Vector(target.copy(candidateReference = target.candidateReference.copy(identity = "foreign-candidate")),
        target.copy(retainedReference = target.retainedReference.copy(identity = "foreign-validation")),
        target.copy(candidateRevision = "5"), target.copy(candidateTree = "e" * 40),
        target.copy(mergeHead = "e" * 40), target.copy(indexTree = "e" * 40),
        target.copy(actual = observed.copy(head = "e" * 40)),
        target.copy(actual = observed.copy(trackingTip = Some("e" * 40))),
        target.copy(actual = observed.copy(dirty = observed.dirty :+ G.DirtyPath(' ', 'M', "foreign.scala", None))))
      Then("a declaration of success supplies no authority to cross the retained native candidate or receipt provenance")
      badorigins.foreach(value => L.decodeTarget(target.copy(receiptOrigins = value).asJson).isLeft shouldBe true)
      badtargets.foreach(value => L.decodeTarget(value.asJson).isLeft shouldBe true)
      L.namespace(target) shouldBe "required-lint-15"
      RepositorySyncNativeProgressStore.candidateKey(L.namespace(target), "pending-merge") shouldBe "required-lint-15-pending-merge"
    }

    "preserve historical native v3 payloads without adding absent v2 direction" in {
      Given("an old Integrated payload without any continuation direction")
      val old = Json.obj("schemaVersion" -> RepositorySyncNativeProgressStore.schema.asJson, "payload" -> Json.obj(
        "stage" -> "Integrated".asJson, "root" -> target.root.asJson, "before" -> observed.asJson,
        "indexTree" -> Json.Null, "checkpoint" -> Json.Null, "after" -> Some(observed).asJson,
        "method" -> "local-ahead".asJson, "mergeHead" -> Json.Null, "validation" -> Json.Null, "finalTip" -> Json.Null))
      When("the native progress codec reads historical bytes")
      val restored = RepositorySyncNativeProgressStore.codec.decode(old).toOption.get
      Then("neither v1 nor v2 direction appears and the old payload shape is unchanged")
      restored.continuation shouldBe None
      restored.requiredLint shouldBe None
      RepositorySyncNativeProgressStore.codec.encode(restored) shouldBe old
    }
  }

  "DEV-RS-RETAINED-REQUIRED-LINT independent host evidence" should {
    "bind exact component participant target answer and not-applicable flags without implicitly provisioning evidence" in new NativePreparationManagedFixture {
      Given("a specification-owned v2 direction and an ordinary independent managed evidence reader")
      withCore { call =>
        val component = call.core.component.get.core.instanceId.canonicalKey
        val selected = target.copy(componentInstance = component)
        val description = V.RepositorySyncValidationContinuationDescriptionResult(selected.sessionId, "adoption-required",
          L.message, component, L.targetRecord(selected), L.choices, N.adoptionOperation)
        val publicanswer = V.RepositorySyncValidationContinuationAnswer(answer.choice, answer.rationale, false, false)
        val ref = V.WorkflowArtifactReference(reference.identity, reference.revision)
        val execution = V.WorkflowExecutionEvidence(Vector(ref), Some("participant"), None, None)
        val reader = take(NativeExternalEvidence.bindC(call.core.component.get.subsystem.get.configuration,
          GoalPhaseRuntimeParameters.Persistence(_configuration_, _collection_("instances"), _collection_("continuations"))))
        val bound = take(_binding_(selected, component))
        val purpose = NativeExternalEvidence.Purpose.RepositoryValidationContinuation
        val lookup = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
        When("passive preparation has encoded the answer but the host has not provisioned it")
        val prepared = take(P.prepareC(description, publicanswer, ref, execution, "participant", "normal-development-host",
          call.core.executionContext.clock.instant().minusSeconds(60)))
        val absent = take(reader.loadC(call.core, purpose, lookup, bound))
        val refusals = Vector(_binding_(selected, "other-component"), _binding_(selected, component, "other-participant"),
          _binding_(selected, component, ended = true), _binding_(selected, component, uncertainty = true))
        val selectedbinding = N.selectBinding(bound)
        val dispatchedbinding = N.bindingAnyC(component, N.RequiredLintTarget(selected), answer.choice, answer.rationale,
          "participant", reference, false, false).toOption
        Then("there is no adopting evidence and crossed component participant or v1 boolean assertions refuse")
        absent shouldBe None
        refusals.foreach(_.isSuccess shouldBe false)
        selectedbinding shouldBe Right(bound)
        dispatchedbinding shouldBe Some(bound)
        When("the independent specification host provisions its own exact purpose-bound row")
        take(call.createC("host_evidence", prepared.recordIdentity, prepared.record))
        val available = take(reader.loadC(call.core, purpose, lookup, bound))
        val wrongpurpose = take(reader.loadC(call.core, NativeExternalEvidence.Purpose.SourceHistoryNormalization, lookup, bound))
        val crossed = take(_binding_(selected.copy(frontierRevision = "16", retainedRevision = "16"), component))
        val crossedlookup = reader.loadC(call.core, purpose, lookup, crossed)
        val preparationrefusals = Vector(
          P.prepareC(description, publicanswer.copy(priorExecutionEnded = true), ref, execution, "participant",
            "normal-development-host", java.time.Instant.now()),
          P.prepareC(description, publicanswer.copy(acceptOriginalUncertainty = true), ref, execution, "participant",
            "normal-development-host", java.time.Instant.now()),
          P.prepareC(description, publicanswer, ref, execution.copy(workerIdentity = Some("other")), "participant",
            "normal-development-host", java.time.Instant.now()),
          P.prepareC(description.copy(choices = N.choices), publicanswer, ref, execution, "participant",
            "normal-development-host", java.time.Instant.now()))
        Then("only the exact dedicated binding is available and changed target purpose worker or false false meaning cannot reuse it")
        available.nonEmpty shouldBe true
        wrongpurpose shouldBe None
        crossedlookup.isSuccess shouldBe false
        preparationrefusals.foreach(_.isSuccess shouldBe false)
        Consequence.unit
      }
    }
  }
}
