package org.simplemodeling.textus.smworkflow.admission

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhasePartialCommitClosure as P
import GoalPhaseClosureRecovery as R
import GoalPhaseNativeClosure as K
import GoalPhaseOrderedProgression as O
import RepositorySyncManagedGitObservation as G
import SplitPhaseExplicitPartitionFixture.take

/** Native-shaped pure proof data, never real Session authority.
  * @since   Oct. 26, 2026
  * @version Oct. 27, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhasePartialCommitClosureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _parent = O.Snapshot("a" * 40, "b" * 40)
  private val _partial = O.Commit("c" * 40, _parent.head, "d" * 40)
  private val _tree = "e" * 40
  private def _modify(path: String, mode: String = "100644") =
    P.Change(path, mode, mode, Some("1" * 40), Some("2" * 40))
  private val _changes = Vector(_modify("docs/one.md"), _modify("docs/two.md"), _modify("src/code.scala"))
  private val _proof = P.Proof(_parent, _partial, _tree, Vector.empty, _changes,
    Vector(_changes.last), _changes.take(2), Vector("docs"))

  "E6 canonical partial recovery preparation" should afterWord(
    "in spec:goal-phase-closure-recovery, example:E6, rules:R7,R8, phase:CB-P67-PC06") {
    "prepare the exact applicable partial-completion choices while preserving Human evidence and target binding requirements" in {
      Given("docs/spec/goal-phase-closure-recovery.md R7 R8 E6: a native-shaped known-success partial target and its canonical public description")
      val input = K.Input("fixture", "1", "plan", "1", "S1", 4, _parent, O.Snapshot(_parent.head, _tree), Some("review"))
      val original = R.Execution(input, 1, 0, "commit-selected", "sm-workflow-repository", "succeeded",
        Some("Exited(0)"), Some(0), "", Vector("src/code.scala"))
      val observed = R.Observation("/fixture/repository", "main", _partial.identity, _partial.tree, _tree, None,
        _changes.take(2).map(c => G.DirtyPath('M', ' ', c.path, None)), Vector("docs"), true,
        _changes.map(_.path), G.Commit(_partial.identity, _partial.tree, Vector(_parent.head)))
      val target = R.RecoveryTarget("fixture", "definition", "1", "participant", "plan", "1", "S1", 4,
        "1", "3", R.reference(input, "intent", 0).identity, "1", "2", 1, 0, _parent, input.candidate,
        Some("review"), Vector("docs", "src"), observed, Some(original), partialCompletion = Some(_proof))
      val choices = R.choicesFor(target)
      val description = org.simplemodeling.textus.smworkflow.value.GoalPhaseClosureRecoveryDescriptionResult(
        target.sessionId, "decision-required", R.message, "component", R.targetRecord(target), choices, R.recoveryOperation)
      val answer = org.simplemodeling.textus.smworkflow.value.GoalPhaseClosureRecoveryAnswer(
        "CompletePartialCommit", "Fixture trusted host retained the original terminal execution", true)
      val reference = org.simplemodeling.textus.smworkflow.value.WorkflowArtifactReference("partial-human-preparation", "1")
      val execution = org.simplemodeling.textus.smworkflow.value.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      val now = java.time.Instant.parse("2026-10-26T00:00:00Z")
      def _prepare_(described: org.simplemodeling.textus.smworkflow.value.GoalPhaseClosureRecoveryDescriptionResult = description,
        answered: org.simplemodeling.textus.smworkflow.value.GoalPhaseClosureRecoveryAnswer = answer,
        actor: String = "participant", provenance: org.simplemodeling.textus.smworkflow.value.WorkflowExecutionEvidence = execution) =
        org.simplemodeling.textus.smworkflow.preparation.NativeClosureRecoveryPreparation.prepareC(
          described, answered, reference, provenance, actor, "trusted-fixture-host", now)
      When("the actual passive preparation owner consumes canonical completion and Defer answers and its normal storage reader decodes the binding")
      val prepared = take(_prepare_())
      val evidence = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      val deferred = _prepare_(answered = answer.copy(choice = "Defer", priorExecutionEnded = false))
      val bound = evidence.binding.hcursor.downField("closureRecovery")
      Then("only applicable choices are advertised and the exact target choice participant and reference survive passive storage")
      choices shouldBe Vector("CompletePartialCommit", "Defer")
      evidence.purpose shouldBe R.purpose
      evidence.participant shouldBe "participant"
      R.decodeTarget(bound.downField("target").focus.get) shouldBe Right(target)
      bound.downField("answer").get[String]("choice") shouldBe Right("CompletePartialCommit")
      bound.downField("answer").get[String]("participant") shouldBe Right("participant")
      bound.downField("answer").downField("reference").get[String]("identity") shouldBe Right(reference.identity)
      bound.downField("answer").downField("reference").get[String]("revision") shouldBe Right(reference.revision)
      bound.downField("answer").get[Boolean]("priorExecutionEnded") shouldBe Right(true)
      deferred.isSuccess shouldBe true

      Given("noncanonical description choices, invalid descriptions or targets, and crossed or missing Human provenance")
      val badchoices = Vector(R.choices, choices.dropRight(1), choices.reverse, choices :+ choices.head, choices :+ "InjectedChoice")
      val baddescriptions = badchoices.map(value => description.copy(choices = value)) ++ Vector(null,
        description.copy(outcome = "completed"), description.copy(recoveryOperation = "foreign-operation"),
        description.copy(target = null), description.copy(target = R.targetRecord(target.copy(sessionId = " "))),
        description.copy(sessionId = "foreign-session"))
      When("the same preparation owner consumes each noncanonical description and inapplicable or unbound answer")
      val refused = baddescriptions.map(value => _prepare_(described = value)) ++ Vector(
        _prepare_(answered = answer.copy(choice = "AuthorizeNewAttempt")),
        _prepare_(answered = answer.copy(choice = "ReconcileCommit")),
        _prepare_(answered = answer.copy(priorExecutionEnded = false)),
        _prepare_(answered = null),
        _prepare_(actor = "foreign-participant", provenance = execution.copy(workerIdentity = Some("foreign-participant"))),
        _prepare_(provenance = execution.copy(workerIdentity = Some("foreign-worker"))),
        _prepare_(provenance = execution.copy(references = Vector.empty)),
        org.simplemodeling.textus.smworkflow.preparation.NativeClosureRecoveryPreparation.prepareC(
          description, answer, null, execution, "participant", "trusted-fixture-host", now))
      Then("global widened missing reordered duplicate or injected choices and every crossed or absent binding requirement refuse")
      refused.foreach(_.isSuccess shouldBe false)
    }
  }

  "E4 partial-chain public Recording eligibility" should afterWord(
    "in spec:goal-phase-closure-recovery, example:E4, rules:R8, phase:P67-T1-CB01") {
    "round trip first and later partial prefixes and ordinary absence while refusing crossed predecessor snapshots" in {
      Given("docs/spec/goal-phase-closure-recovery.md R8 E4: a valid existing Plan and assigned recording items for first and later completed partial Steps")
      val recording = GoalPhaseFindingRecording
      val destination = "docs/journal/findings.md"
      val initial = NativeHumanEvidenceFixture.plan.steps.head.copy(ownedPaths = Vector("one.scala", destination))
      val second = initial.copy(identity = "second", dependencies = Vector(initial.identity))
      val plan = NativeHumanEvidenceFixture.plan.copy(steps = Vector(initial, second))
      val chain = O.StepCommitChain(_parent, _partial, O.Commit("f" * 40, _partial.identity, _tree))
      val first = O.ClosedStep(initial.identity, chain.completion, Some("accepted-review"), partialCompletion = Some(chain))
      val nextparent = O.Snapshot(chain.completion.identity, chain.completion.tree)
      val laterchain = O.StepCommitChain(nextparent, O.Commit("5" * 40, nextparent.head, "6" * 40),
        O.Commit("7" * 40, "5" * 40, "8" * 40))
      val later = O.ClosedStep(second.identity, laterchain.completion, Some("later-review"), partialCompletion = Some(laterchain))
      val reference = org.goldenport.cncf.workflow.ContextReference("independent-review", "7")
      val observation = recording.Observation("chain-note", "HYGIENE", "Retain the accepted chain",
        "A nonblocking finding preserves the actual native commit parents")
      val item = recording.Item(reference, "ReviewStep", Some(first.step), observation, destination)
      val request = recording.Request("instance", "18", "recording-batch", plan, _parent, Vector(first),
        Some(first.step), Vector(item), None, None)
      val laterrequest = request.copy(completed = Vector(first, later), stepIdentity = Some(later.step),
        items = Vector(item.copy(stepIdentity = Some(later.step))))
      val phaserequest = laterrequest.copy(stepIdentity = None,
        items = Vector(item.copy(sourceKind = "ReviewPhase", stepIdentity = None)))
      val ordinary = O.ClosedStep(initial.identity, O.Commit("9" * 40, _parent.head, "1" * 40), Some("ordinary-review"))
      val ordinaryrequest = request.copy(completed = Vector(ordinary))
      val ordinarylater = O.ClosedStep(second.identity, O.Commit("2" * 40, nextparent.head, "3" * 40), Some("ordinary-later-review"))
      val ordinarylaterrequest = laterrequest.copy(completed = Vector(first, ordinarylater))
      val result = recording.Result("completed", "All assigned findings recorded", Vector(
        recording.ItemResult(reference, observation.identity, "recorded", Vector(
          recording.SavedSection(destination, "Finding chain-note")), "Saved the exact assigned observation")))
      val requests = Vector(request, laterrequest, phaserequest, ordinaryrequest, ordinarylaterrequest)
      When("the actual public Recording request and result codecs retain their finite assignments and validation checks the completed prefix")
      val retained = requests.map(value => recording.inputCodec.decode(recording.inputCodec.encode(value)))
      val retainedresult = recording.resultCodec.decode(recording.resultCodec.encode(result))
      Then("first and later chains keep actual D.parent=C, ordinary chain absence stays absent, and the exact result remains admissible")
      retained shouldBe requests.map(Right(_))
      retainedresult shouldBe Right(result)
      requests.foreach { value =>
        recording.validateRequestC(value).isSuccess shouldBe true
        recording.admitResultC(value, result).isSuccess shouldBe true
      }
      request.completed.head.commit.parent shouldBe chain.partial.identity
      laterrequest.completed.last.commit.parent shouldBe laterchain.partial.identity
      recording.inputCodec.encode(ordinaryrequest).hcursor.downField("payload").downField("completed").downN(0)
        .downField("partialCompletion").focus shouldBe None

      Given("crossed first or later predecessor heads and trees with internally valid partial chains, and incorrect ordinary immediate parents")
      val crossedhead = laterchain.copy(originalParent = nextparent.copy(head = "4" * 40),
        partial = laterchain.partial.copy(parent = "4" * 40))
      val crossedtree = laterchain.copy(originalParent = nextparent.copy(tree = "4" * 40))
      val invalid = Vector(request.copy(originalBase = _parent.copy(head = "4" * 40)),
        request.copy(originalBase = _parent.copy(tree = "4" * 40)),
        laterrequest.copy(completed = Vector(first, later.copy(partialCompletion = Some(crossedhead)))),
        laterrequest.copy(completed = Vector(first, later.copy(partialCompletion = Some(crossedtree)))),
        ordinaryrequest.copy(completed = Vector(ordinary.copy(commit = ordinary.commit.copy(parent = "4" * 40)))),
        ordinarylaterrequest.copy(completed = Vector(first, ordinarylater.copy(commit = ordinarylater.commit.copy(parent = _parent.head)))))
      When("Recording validates and decodes each crossed completed-prefix assignment")
      val refused = invalid.map(recording.validateRequestC)
      val refusedwire = invalid.map(value => recording.inputCodec.decode(recording.inputCodec.encode(value)))
      Then("neither a crossed predecessor head or tree nor a skipped ordinary predecessor can authorize recording")
      O.validStepCommitChain(crossedhead) shouldBe true
      O.validStepCommitChain(crossedtree) shouldBe true
      refused.foreach(_.isSuccess shouldBe false)
      refusedwire.foreach(_.isLeft shouldBe true)
      invalid.foreach(value => recording.admitResultC(value, result).isSuccess shouldBe false)
    }
  }

  "E3 partial-chain automatic journal eligibility" should afterWord(
    "in spec:goal-phase-closure-recovery, example:E3, rules:R8, phase:P67-T1-CB01") {
    "retain exact ordered predecessors through automatic context and entry storage while preserving ordinary compatibility" in {
      Given("docs/spec/goal-phase-closure-recovery.md R8 E3: a first completed partial chain and a later chain using the previous actual native snapshot")
      val chain = O.StepCommitChain(_parent, _partial, O.Commit("f" * 40, _partial.identity, _tree))
      val first = O.ClosedStep("S1", chain.completion, Some("accepted-review"), partialCompletion = Some(chain))
      val context = DevelopmentJournalFinding.Context("repository", "phase", "fixture-session", "18", "plan", "1",
        "Implementation", Some("S1"), _parent, Vector(first), None, None, None)
      val nextparent = O.Snapshot(chain.completion.identity, chain.completion.tree)
      val laterchain = O.StepCommitChain(nextparent, O.Commit("5" * 40, nextparent.head, "6" * 40),
        O.Commit("7" * 40, "5" * 40, "8" * 40))
      val later = O.ClosedStep("S2", laterchain.completion, Some("later-review"), partialCompletion = Some(laterchain))
      val latercontext = context.copy(eligibilityStage = "PhaseInspection", stepIdentity = Some("S2"), completed = Vector(first, later))
      val reference = org.goldenport.cncf.workflow.ContextReference("actual-fixture-review", "1")
      val observation = GoalPhaseFindingRecording.Observation("chain-note", "HYGIENE", "Retain the accepted chain",
        "A separate nonblocking observation with the original actual native parents")
      val pending = GoalPhaseNativeFindingRecording.Pending(reference, "ReviewStep", Some("S1"), observation)
      When("automatic recording validates its exact context and serializes the immutable finding through the normal storage row")
      val admitted = DevelopmentJournalFinding.validateContextC(context)
      val lateradmitted = DevelopmentJournalFinding.validateContextC(latercontext)
      val entry = take(DevelopmentJournalFinding.entryC(context, pending, java.time.Instant.parse("2026-10-26T00:00:00Z")))
      val stored = take(DevelopmentJournalFinding.storedC(take(DevelopmentJournalFinding.rowC(entry)), entry.identity))
      val automatic = GoalPhaseNativeJournalFinding.Payload(context.sourceRevision, context,
        GoalPhaseNativeJournalFinding.Policy("workflow", org.simplemodeling.model.datatype.EntityCollectionId("spec", "journal", "entries").print),
        Vector(pending), None)
      val retained = GoalPhaseNativeJournalFinding.codec.decode(GoalPhaseNativeJournalFinding.codec.encode(automatic))
      Then("eligibility storage and public journal output retain P/C/D and the actual D parent C for first and later Steps")
      admitted.isSuccess shouldBe true
      lateradmitted.isSuccess shouldBe true
      stored shouldBe entry
      stored.context.completed.head.partialCompletion shouldBe Some(chain)
      stored.context.completed.head.commit.parent shouldBe chain.partial.identity
      retained shouldBe Right(automatic)
      DevelopmentJournalFinding.publicEntry(stored).finding.get.completed.head.partialCompletion shouldBe
        NativeWorkflowOperationOutput.goalClosedStep(first).partialCompletion

      Given("an ordinary completed prefix and crossed first or later predecessor head/tree facts with otherwise valid chains")
      val ordinary = O.ClosedStep("S1", O.Commit("9" * 40, _parent.head, "1" * 40), Some("ordinary-review"))
      val ordinarycontext = context.copy(completed = Vector(ordinary))
      val crossedhead = laterchain.copy(originalParent = nextparent.copy(head = "2" * 40),
        partial = laterchain.partial.copy(parent = "2" * 40))
      val crossedtree = laterchain.copy(originalParent = nextparent.copy(tree = "2" * 40))
      val invalid = Vector(context.copy(originalBase = _parent.copy(head = "2" * 40)),
        context.copy(originalBase = _parent.copy(tree = "2" * 40)),
        latercontext.copy(completed = Vector(first, later.copy(partialCompletion = Some(crossedhead)))),
        latercontext.copy(completed = Vector(first, later.copy(partialCompletion = Some(crossedtree)))),
        ordinarycontext.copy(completed = Vector(ordinary.copy(commit = ordinary.commit.copy(parent = "2" * 40)))))
      When("the ordered context checks compare each chain anchor or ordinary direct parent with its real predecessor")
      val compatible = DevelopmentJournalFinding.validateContextC(ordinarycontext)
      val refused = invalid.map(DevelopmentJournalFinding.validateContextC)
      Then("ordinary absence remains valid and neither a crossed predecessor head nor a crossed predecessor tree can enter journal storage")
      compatible.isSuccess shouldBe true
      val ordinaryentry = take(DevelopmentJournalFinding.entryC(ordinarycontext, pending, java.time.Instant.parse("2026-10-26T00:00:00Z")))
      take(DevelopmentJournalFinding.storedC(take(DevelopmentJournalFinding.rowC(ordinaryentry)), ordinaryentry.identity)) shouldBe ordinaryentry
      refused.foreach(_.isSuccess shouldBe false)
      invalid.foreach(value => DevelopmentJournalFinding.entryC(value,
        pending.copy(stepIdentity = value.stepIdentity), java.time.Instant.parse("2026-10-26T00:00:00Z")).isSuccess shouldBe false)
    }
  }

  "E1 exact partial commit containment" should afterWord(
    "in spec:goal-phase-closure-recovery, example:E1, rules:R7,R8, phase:P67-T1-CB01") {
    "retain native old and new blobs modes absences and both rename names" in {
      Given("docs/spec/goal-phase-closure-recovery.md R7 E1: a contained partial and exact nonempty accepted remainder")
      val deletion = P.Change("docs/old.md", "100755", "000000", Some("3" * 40), None)
      val addition = P.Change("docs/new.md", "000000", "100755", None, Some("3" * 40))
      val renamed = _proof.copy(candidateChanges = Vector(addition, deletion, _changes.last), remainder = Vector(addition, deletion))
      When("the native path by path proof and actual exact or directory selection are checked")
      val ordinary = P.validProof(_proof)
      val rename = P.validProof(renamed)
      Then("the retained proof covers deletions additions executable modes and both sides of a no-renames delta")
      ordinary shouldBe true
      rename shouldBe true
      P.coverage(Vector("docs/old.md", "docs/new.md"), Vector("docs")) shouldBe true
      P.coverage(Vector("docs/old.md", "docs/new.md"), Vector("docs/new.md")) shouldBe false
      P.validProof(renamed.copy(remainder = Vector(addition))) shouldBe false
      P.validProof(renamed.copy(remainder = Vector(addition.copy(newMode = "100644"), deletion))) shouldBe false
    }

    "reject foreign committed content wrong modes blobs parents trees and incomplete selection" in {
      Given("docs/spec/goal-phase-closure-recovery.md R7 E1: one frozen exact native proof")
      val invalid = Vector(
        _proof.copy(committedChanges = Vector(_modify("foreign.txt"))),
        _proof.copy(committedChanges = Vector(_changes.last.copy(newBlob = Some("4" * 40)))),
        _proof.copy(committedChanges = Vector(_changes.last.copy(newMode = "100755"))),
        _proof.copy(partial = _partial.copy(parent = "f" * 40)),
        _proof.copy(expectedTree = _partial.tree), _proof.copy(remainder = Vector.empty),
        _proof.copy(selectedPaths = Vector("docs/one.md")),
        _proof.copy(candidateChanges = _changes :+ _modify("docs/one.md")))
      When("each substituted native fact is checked against the frozen containment relation")
      val actual = invalid.map(P.validProof)
      Then("no extra commit content mode blob alias or missing physical path is accepted")
      actual.foreach(_ shouldBe false)
    }
  }

  "E2 known successful original execution" should afterWord(
    "in spec:goal-phase-closure-recovery, example:E2, rules:R7,R8, phase:P67-T1-CB01") {
    "admit only actual succeeded Exited zero with the exact current partial native frontier" in {
      Given("docs/spec/goal-phase-closure-recovery.md R7 E2: a known successful original execution and exact remainder")
      val input = K.Input("fixture", "1", "plan", "1", "S1", 4, _parent, O.Snapshot(_parent.head, _tree), Some("review"))
      val execution = R.Execution(input, 1, 0, "commit-selected", "sm-workflow-repository", "succeeded",
        Some("Exited(0)"), Some(0), "", Vector("src/code.scala"))
      val observed = R.Observation("/fixture/repository", "main", _partial.identity, _partial.tree, _tree, None,
        _changes.take(2).map(c => G.DirtyPath('M', ' ', c.path, None)), Vector("docs"), true,
        _changes.map(_.path), G.Commit(_partial.identity, _partial.tree, Vector(_parent.head)))
      val target = R.RecoveryTarget("fixture", "definition", "1", "participant", "plan", "1", "S1", 4,
        "1", "3", R.reference(input, "intent", 0).identity, "1", "2", 1, 0, _parent, input.candidate,
        Some("review"), Vector("docs", "src"), observed, Some(execution), partialCompletion = Some(_proof))
      val invalid = Vector(target.copy(execution = None), target.copy(execution = Some(execution.copy(outcome = "incomplete", termination = None, exitCode = None))),
        target.copy(execution = Some(execution.copy(outcome = "failed", termination = Some("Exited(1)"), exitCode = Some(1)))),
        target.copy(observation = observed.copy(commit = observed.commit.copy(parents = Vector(_parent.head, "f" * 40)))),
        target.copy(observation = observed.copy(head = "f" * 40)), target.copy(observation = observed.copy(indexTree = "f" * 40)),
        target.copy(observation = observed.copy(mergeHead = Some("f" * 40))),
        target.copy(candidate = input.candidate.copy(tree = "f" * 40)),
        target.copy(observation = observed.copy(dirty = Vector(G.DirtyPath('R', ' ', "docs/new.md", Some("foreign.txt"))))))
      When("the complete target is admitted for the distinct completion choice")
      val admitted = R.choiceC(target, "CompletePartialCommit")
      val refused = invalid.map(R.choiceC(_, "CompletePartialCommit"))
      Then("only exact terminal success and unchanged original P/C/T native facts permit a completion direction")
      admitted.isSuccess shouldBe true
      refused.foreach(_.isSuccess shouldBe false)
      R.choiceC(target, "AuthorizeNewAttempt").isSuccess shouldBe false
      R.choiceC(target, "ReconcileCommit").isSuccess shouldBe false
    }
  }
}
