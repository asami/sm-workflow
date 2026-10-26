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
import RepositorySyncValidationContinuation as N
import RepositorySyncNativeMergeValidation as C
import RepositorySyncManagedGitObservation as G
import SplitPhaseExplicitPartitionFixture.take

private[admission] object RepositorySyncValidationContinuationFixture {
  val checks = C.Policy("repository-checks", 1, "required", "Run the selected product check", Vector(C.Check("test", 1)))
  val observed = G.Observed("main", "a" * 40, "b" * 40, Vector.empty, Some("c" * 40),
    Some(G.Counts(1, 0)), Some("c" * 40))
  val prefix = Vector(C.Receipt("index-local", "exited", Some(0), "", ""))
  val context = RepositorySyncManagedFetch.StartContext("selection", "invocation", "participant", "repository",
    "main", "origin", "main", "owned-upstream", "FULL_STATE")
  val target = N.Target("session", context, "/specification/repository", "/specification/upstream.git", "9",
    "native-repository:session:validation-intent-1", "", ContextReference("session:native-integrated", "1"),
    "6", observed, observed, observed.tree, "local-ahead", "", checks,
    ContextReference("session:native-validation-intent-1", "1"), "9", 1, checks.checks.head,
    prefix, observed, observed.tree, N.policy)
  val reference = ContextReference("existing-owner-direction", "2")
  def binding(component: String = "component", selected: N.Target = target, choice: String = "AuthorizeNewValidation",
    ended: Boolean = true, uncertainty: Boolean = true, participant: String = "participant") =
    N.bindingC(component, selected, choice, "Specification-owned explicit direction", participant, reference, ended, uncertainty)
}

/** Pure contracts plus independent normal managed evidence ingress.
  * Native history/effect cases are qualified by the separate public CLI suite.
  * @since Oct. 26, 2026
  * @version Oct. 26, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncValidationContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativePreparationManagedFixture {
  import RepositorySyncValidationContinuationFixture.*

  "RS-VC1 strict persisted and public contracts" should {
    "roundtrip historical v3 payloads exactly while omitting the absent new direction" in {
      Given("a native v3 Integrated record before validation continuation existed")
      val old = Json.obj("schemaVersion" -> RepositorySyncNativeProgressStore.schema.asJson, "payload" -> Json.obj(
        "stage" -> "Integrated".asJson, "root" -> target.root.asJson, "before" -> observed.asJson,
        "indexTree" -> Json.Null, "checkpoint" -> Json.Null, "after" -> Some(observed).asJson,
        "method" -> "local-ahead".asJson, "mergeHead" -> Json.Null, "validation" -> Json.Null, "finalTip" -> Json.Null))
      When("the extended codec reads and writes the old payload")
      val restored = RepositorySyncNativeProgressStore.codec.decode(old).toOption.get
      Then("the absent continuation remains absent and the complete old wire representation is unchanged")
      restored.continuation shouldBe None
      RepositorySyncNativeProgressStore.codec.encode(restored) shouldBe old
    }

    "refuse absent null extra and malformed meaning-bearing target and answer fields" in {
      Given("a complete exact resultless-intent target and explicit typed answer")
      val wire = target.asJson
      val answer = Json.obj("choice" -> "AuthorizeNewValidation".asJson, "rationale" -> "Explicit fixture direction".asJson,
        "priorExecutionEnded" -> true.asJson, "acceptOriginalUncertainty" -> true.asJson)
      When("each required target or answer field is absent null or extended")
      val malformed = wire.asObject.get.keys.toVector.flatMap(key => Vector(wire.mapObject(_.remove(key)),
        wire.mapObject(_.add(key, Json.Null)))) :+ wire.mapObject(_.add("unobservedAuthority", Json.True))
      val wronganswers = answer.asObject.get.keys.toVector.flatMap(key => Vector(answer.mapObject(_.remove(key)),
        answer.mapObject(_.add(key, Json.Null)))) :+ answer.mapObject(_.add("silentRetry", Json.True))
      Then("strict decode preserves all supplied meaning and rejects every malformed shape")
      N.decodeTarget(wire) shouldBe Right(target)
      malformed.foreach(N.decodeTarget(_).isLeft shouldBe true)
      wronganswers.foreach(N.decodeAnswer(_).isLeft shouldBe true)
      N.decodeAnswer(answer).isRight shouldBe true
      Vector(target.copy(frontierRevision = "09"), target.copy(intentRevision = "10"),
        target.copy(sessionId = "foreign"), target.copy(check = target.check.copy(ordinal = 2)),
        target.copy(candidateTree = "malformed"), target.copy(checkIndex = 0),
        target.copy(actual = observed.copy(dirty = Vector(G.DirtyPath('U', 'U', "value.txt", None)))))
        .foreach(value => N.decodeTarget(value.asJson).isLeft shouldBe true)
    }

    "bind complete policy check kind source prefix participant and explicit uncertainty acceptance" in {
      Given("a successful structural receipt and a missing selected SBT result")
      val actual = take(binding())
      When("the target policy check kind or participant and terminal assertions are crossed")
      val refusals = Vector(binding(ended = false), binding(uncertainty = false), binding(participant = "other"),
        binding(selected = target.copy(successfulPrefix = prefix.map(_.copy(identity = "index-remote")))) )
      Then("no direction can reuse an unattributed prefix or infer original invocation termination")
      refusals.foreach(_.isSuccess shouldBe false)
      N.selectBinding(actual) shouldBe Right(actual)
      val nested = actual.hcursor.downField("repositoryValidationContinuation").focus.get
      Vector(actual.mapObject(_.add("componentInstance", Json.fromString("other"))),
        actual.mapObject(_.add("repositoryValidationContinuation", nested.mapObject(_.add("target",
          target.copy(validationPolicy = checks.copy(checks = Vector(checks.checks.head.copy(
            kind = WorkflowSbtValidation.Kind.TestOnly))), check = checks.checks.head.copy(kind = WorkflowSbtValidation.Kind.TestOnly)).asJson)))))
        .foreach(_ should not be actual)
      N.validPrefix(prefix :+ C.Receipt("test", "exited", Some(1), "", ""), checks) shouldBe false
    }

    "require explicit changed FULL_STATE direction for mixed staging and keep old checks historical" in {
      Given("the same HEAD and tracking with one staged and one unstaged selected file")
      val dirty = observed.copy(dirty = Vector(G.DirtyPath('M', ' ', "staged.txt", None), G.DirtyPath(' ', 'M', "worktree.txt", None)))
      val changed = target.copy(actual = dirty, indexTree = "d" * 40)
      When("each bounded public choice is evaluated against that current candidate scope")
      val unchanged = binding(selected = changed)
      val fullstate = binding(selected = changed, choice = "AdoptCurrentFullState")
      val defer = binding(selected = changed, choice = "Defer", ended = false, uncertainty = false)
      Then("mixed staging has only explicit FULL_STATE authority and Defer retains a passive answer")
      unchanged.isSuccess shouldBe false
      fullstate.isSuccess shouldBe true
      defer.isSuccess shouldBe true
      binding(selected = changed.copy(context = context.copy(syncMode = "OTHER")), choice = "AdoptCurrentFullState").isSuccess shouldBe false
      changed.successfulPrefix shouldBe prefix
      N.namespace(changed) shouldBe "validation-continuation-9"
      RepositorySyncNativeProgressStore.candidateKey(N.namespace(changed), "integrated") shouldBe "validation-continuation-9-integrated"
    }

    "refuse concurrently staged unrelated paths and rename endpoints before checkpoint creation" in {
      Given("explicit adopted mixed staging whose status codes normalize under normal full-state staging")
      val before = observed.copy(dirty = Vector(G.DirtyPath('M', 'M', "selected.txt", None),
        G.DirtyPath(' ', 'M', "second.txt", None)))
      val staged = before.copy(dirty = Vector(G.DirtyPath('M', ' ', "selected.txt", None),
        G.DirtyPath('M', ' ', "second.txt", None)))
      When("the post-stage gate compares complete path/rename scope, clean worktree and unchanged HEAD/tracking")
      val admitted = RepositorySyncNativePreservation.adoptedStagedC(before, staged)
      val foreign = staged.copy(dirty = staged.dirty :+ G.DirtyPath('A', ' ', "unrelated.txt", None))
      val renamed = staged.copy(dirty = staged.dirty.updated(0, G.DirtyPath('R', ' ', "selected.txt", Some("outside.txt"))))
      val dirty = staged.copy(dirty = staged.dirty.updated(0, staged.dirty.head.copy(worktree = 'M')))
      Then("normal status normalization passes but a newly staged foreign path, rename, HEAD or tracking drift cannot enter a checkpoint")
      admitted.isSuccess shouldBe true
      Vector(foreign, renamed, dirty, staged.copy(head = "e" * 40), staged.copy(trackingTip = Some("f" * 40)))
        .foreach(value => RepositorySyncNativePreservation.adoptedStagedC(before, value).isSuccess shouldBe false)
    }

    "return the first immutable index tree after an ordered stable post-stage scope observation" in {
      Given("an adopted path whose normal staging has left a fully staged clean worktree")
      val before = observed.copy(dirty = Vector(G.DirtyPath('M', 'M', "selected.txt", None)))
      val staged = before.copy(dirty = Vector(G.DirtyPath('M', ' ', "selected.txt", None)))
      val firsttree = "d" * 40
      var reads = Vector.empty[String]
      var treecount = 0
      When("the capture program reads the tree before the scope observation and confirms the same tree afterwards")
      val captured = RepositorySyncNativePreservation.capturedAdoptedIndexC(before,
        () => { treecount += 1; reads = reads :+ ("tree-" + treecount); Consequence.success(firsttree) },
        () => { reads = reads :+ "observed"; Consequence.success(staged) })
      Then("only the first captured immutable tree is returned after both ordered checks")
      take(captured) shouldBe firsttree
      reads shouldBe Vector("tree-1", "observed", "tree-2")
    }

    "refuse an index mutation between the valid scope observation and confirming tree read" in {
      Given("a successful fully staged path observation with different immutable trees before and after it")
      val before = observed.copy(dirty = Vector(G.DirtyPath('M', 'M', "selected.txt", None)))
      val staged = before.copy(dirty = Vector(G.DirtyPath('M', ' ', "selected.txt", None)))
      val firsttree = "d" * 40
      val latertree = "e" * 40
      var reads = Vector.empty[String]
      var treecount = 0
      var downstream = Option.empty[String]
      When("the capture program observes index drift across its post-stage gate")
      val captured = RepositorySyncNativePreservation.capturedAdoptedIndexC(before,
        () => { treecount += 1; reads = reads :+ ("tree-" + treecount)
          Consequence.success(if (treecount == 1) firsttree else latertree) },
        () => { reads = reads :+ "observed"; Consequence.success(staged) })
        .map { tree => downstream = Some(tree); tree }
      Then("drift refuses without returning the unchecked later tree or reaching the downstream continuation")
      captured.isSuccess shouldBe false
      downstream shouldBe None
      reads shouldBe Vector("tree-1", "observed", "tree-2")
    }

    "stop captured index admission at a refused post-stage path scope" in {
      Given("a captured tree followed by a fully staged observation containing a foreign path")
      val before = observed.copy(dirty = Vector(G.DirtyPath('M', 'M', "selected.txt", None)))
      val foreign = before.copy(dirty = Vector(G.DirtyPath('M', ' ', "selected.txt", None),
        G.DirtyPath('A', ' ', "unrelated.txt", None)))
      var reads = Vector.empty[String]
      var downstream = Option.empty[String]
      When("the capture program applies the existing exact path and rename gate")
      val captured = RepositorySyncNativePreservation.capturedAdoptedIndexC(before,
        () => { reads = reads :+ "tree"; Consequence.success("d" * 40) },
        () => { reads = reads :+ "observed"; Consequence.success(foreign) })
        .map { tree => downstream = Some(tree); tree }
      Then("scope refusal prevents a confirming read and any downstream continuation")
      captured.isSuccess shouldBe false
      downstream shouldBe None
      reads shouldBe Vector("tree", "observed")
    }
  }

  "RS-VC1 independent evidence" should {
    "remain passive until normal host provisioning and refuse wrong purpose participant target and lifecycle assertions" in {
      Given("a specification-owned terminal attestation, never a producer observation of a real invocation")
      withCore { call =>
        val component = call.core.component.get.core.instanceId.canonicalKey
        val description = V.RepositorySyncValidationContinuationDescriptionResult(target.sessionId, "adoption-required",
          N.message, component, N.targetRecord(target), N.choices, N.adoptionOperation)
        val answer = V.RepositorySyncValidationContinuationAnswer("AuthorizeNewValidation", "Specification-owned explicit direction", true, true)
        val ref = V.WorkflowArtifactReference(reference.identity, reference.revision)
        val execution = V.WorkflowExecutionEvidence(Vector(ref), Some("participant"), None, None)
        val prepared = take(P.prepareC(description, answer, ref, execution, "participant", "normal-development-host",
          call.core.executionContext.clock.instant().minusSeconds(60)))
        val reader = take(NativeExternalEvidence.bindC(call.core.component.get.subsystem.get.configuration,
          GoalPhaseRuntimeParameters.Persistence(_configuration_, _collection_("instances"), _collection_("continuations"))))
        val binding = take(RepositorySyncValidationContinuationFixture.binding(component))
        val purpose = NativeExternalEvidence.Purpose.RepositoryValidationContinuation
        val lookup = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
        When("the prepared row has not yet been independently provisioned")
        Then("the independent reader finds no evidence from passive preparation")
        take(reader.loadC(call.core, purpose, lookup, binding)) shouldBe None
        When("the host independently provisions the prepared evidence row")
        take(call.createC("host_evidence", prepared.recordIdentity, prepared.record))
        Then("normal managed admission consumes only that exact dedicated purpose and full binding")
        take(reader.loadC(call.core, purpose, lookup, binding)).nonEmpty shouldBe true
        take(reader.loadC(call.core, NativeExternalEvidence.Purpose.SourceHistoryNormalization, lookup, binding)) shouldBe None
        val crossed = take(RepositorySyncValidationContinuationFixture.binding(component, target.copy(frontierRevision = "10",
          intentRevision = "10")))
        reader.loadC(call.core, purpose, lookup, crossed).isSuccess shouldBe false
        P.prepareC(description, answer.copy(priorExecutionEnded = false), ref, execution, "participant",
          "normal-development-host", java.time.Instant.now()).isSuccess shouldBe false
        P.prepareC(description, answer.copy(acceptOriginalUncertainty = false), ref, execution, "participant",
          "normal-development-host", java.time.Instant.now()).isSuccess shouldBe false
        P.prepareC(description, answer, ref, execution.copy(workerIdentity = Some("other")), "participant",
          "normal-development-host", java.time.Instant.now()).isSuccess shouldBe false
        Consequence.unit
      }
    }
  }
}
