package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import io.circe.Json
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseNativeClosure as K
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Pure native-shaped records and typed blob facts; no installed effect or authority proof.
  * @since   Oct. 19, 2026
  * @version Oct. 19, 2026
  */
final class GoalPhaseNativeClosureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private val _candidate = O.Snapshot(_base.head, "c" * 40)
  private val _paths = Vector("src/one.scala", "docs/check.md")
  private val _plan = O.Plan("closure-plan", "1", "PHASE-6.3", "sm-workflow", Vector(
    O.Step("S1", "Implement the first selected boundary", _paths, Vector.empty, Vector("test")),
    O.Step("S2", "Implement the next boundary", Vector("docs/next.md"), Vector("S1"), Vector("test"))), Vector("test"))
  private def _controller: O.State = {
    val initial = take(O.startC(_plan, _base))
    val ready = take(initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key, "implemented")))
    val seen = take(ready.acceptC(O.CandidateObserved(_candidate)))
    val checked = take(seen.acceptC(O.ChecksObserved(_candidate, Vector("test"), "checks-original", true)))
    take(checked.acceptC(O.ReviewAdmitted(checked.next.asInstanceOf[O.Semantic].key, _candidate,
      "checks-original", "review-original", O.ReviewOutcome.Accept)))
  }
  private def _frontier: InstanceRecord = {
    val initial = take(InstanceRecord(InstanceIdentity("closure-native"),
      take(GoalPhaseRuntimeCatalog.loadC()).definition, initialRevision, Lifecycle.NotStarted, None,
      CorrelationReference("closure-correlation"), CausationReference("closure-start"),
      None, None, None, Vector.empty).validateC)
    _append(initial, "review-admitted")
  }
  private def _append(from: InstanceRecord, key: String, stage: String = "Closing"): InstanceRecord =
    take(from.appendC(from.revision, GoalPhaseNativeRun.history(from, "phase." + stage, "ordered-step-closure:" + key)))
  private def _input(from: InstanceRecord): K.Input = take(K.inputC(from, _controller))
  private val _failed = K.Precheck(Vector("src/one.scala:7: new blank line at EOF."), Vector("src/one.scala"), true)
  private val _clean = K.Precheck(Vector.empty, Vector.empty, false)
  private def _attempt(from: InstanceRecord, payload: K.Payload, intent: Boolean = false): K.Attempt = {
    val suffix = if (payload.attempt == 0) "" else "-retry-" + payload.attempt
    val checked = K.Saved(_append(from, "check-result" + suffix), payload, _controller)
    K.Attempt(Some(checked), Option.when(intent)(K.Saved(_append(checked.record, "intent" + suffix), payload, _controller)))
  }
  private def _blob(text: String): GitBlobContent = GitBlobContent(text.getBytes(UTF_8).toVector, Some(text))
  private def _change(path: String = "src/one.scala", before: String = "d" * 40,
    after: String = "e" * 40, mode: String = "100644"): GitTreeChange =
    GitTreeChange(mode, mode, Some(take(GitObjectId.parseC(before))), Some(take(GitObjectId.parseC(after))),
      "M", take(GitPath.parseC(path)))
  private def _admit(changes: Vector[GitTreeChange], blobs: Map[String, GitBlobContent],
    failed: Vector[K.Precheck] = Vector(_failed)) =
    K.admitEofCorrectionC(_input(_frontier), _paths, failed, changes, blobs)
  private def _declarations(value: Json): Vector[Json] = value.hcursor.get[Vector[Json]]("gitPrograms").toOption.get
  private def _host = org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target(
    Path.of(".").toRealPath(), "main", "origin", "main", "fixture-upstream.git")

  "Native selected Step attempts" should {
    "retain failed zero and one before clean two, its intent and the normal completed shape" in {
      Given("an original reviewed Closing frontier and two actual-shaped nonclean selected checks without commit dispatch")
      val from = _frontier
      val input = _input(from)
      val first = _attempt(from, K.Payload(input, 1, None, check = Some(_failed)))
      val second = _attempt(first.check.get.record, K.Payload(input, 1, None, 1, Some(_failed)))
      val clean = _attempt(second.check.get.record, K.Payload(input, 1, None, 2, Some(_clean)), true)
      When("the Store's pure reconciliation admits each contiguous retained prefix and the pending clean intent")
      val fresh = take(K.reconcileAttemptsC(from, input, 1, Vector(K.Attempt(None, None))))
      val failedonce = take(K.reconcileAttemptsC(from, input, 1, Vector(first, K.Attempt(None, None))))
      val failedtwice = take(K.reconcileAttemptsC(from, input, 1, Vector(first, second)))
      val pending = take(K.reconcileAttemptsC(from, input, 1, Vector(first, second, clean)))
      Then("only the failed prefixes select a new attempt, preserving every original target and controller count")
      fresh shouldBe K.Progress(from, Vector.empty, Some(0), None)
      failedonce.record shouldBe first.check.get.record
      failedonce.nextAttempt shouldBe Some(1)
      failedtwice.failed shouldBe Vector(first.check.get, second.check.get)
      failedtwice.nextAttempt shouldBe Some(2)
      pending.record shouldBe clean.intent.get.record
      pending.nextAttempt shouldBe None
      pending.pending shouldBe clean.intent
      pending.failed shouldBe failedtwice.failed
      (pending.failed ++ Vector(clean.check.get, clean.intent.get)).foreach { saved =>
        saved.payload.input shouldBe input
        saved.payload.ordinal shouldBe 1
        saved.payload.commit shouldBe None
        saved.controller.generation shouldBe _controller.generation
        saved.controller.repairs shouldBe _controller.repairs
        saved.controller.ordinaryRepairs shouldBe _controller.ordinaryRepairs
        saved.controller.reviewEvidence shouldBe Some(input.reviewEvidence)
      }
      Given("that exact pending intent and a pure ordinary commit fact; actual Store completion additionally observes typed Git")
      val identity = from.identity.value + ":ordered-step-closure:" + from.revision.value + ":intent-retry-2"
      val commit = O.Commit("f" * 40, input.parent.head, input.candidate.tree)
      When("the controller closes through the same durable intent and a completed native successor is authored")
      val entered = take(_controller.acceptC(O.ClosureIntentSaved(input.candidate, identity)))
      val closed = take(entered.acceptC(O.StepCommitObserved(identity, commit)))
      val completed = K.Saved(_append(pending.record, "committed", closed.stage.toString),
        clean.intent.get.payload.copy(commit = Some(commit)), closed)
      Then("the receipt retains the original input/check and all failed native predecessors before starting S2")
      completed.payload.input shouldBe input
      completed.payload.attempt shouldBe 2
      completed.payload.check shouldBe Some(_clean)
      completed.record.history.dropRight(1) shouldBe pending.record.history
      completed.record.history.take(first.check.get.record.history.size) shouldBe first.check.get.record.history
      completed.controller.completed shouldBe Vector(O.ClosedStep("S1", commit, input.reviewEvidence))
      completed.controller.cursor shouldBe 1
      completed.controller.generation shouldBe _controller.generation + 1
      completed.controller.repairs shouldBe 0
      completed.controller.ordinaryRepairs shouldBe 0
    }

    "keep clean checks, matching intents and old original intents unknown without retry" in {
      Given("a clean check, its possible matching intent, and an old v3 intent with no new facts")
      val from = _frontier
      val input = _input(from)
      val clean = _attempt(from, K.Payload(input, 1, None, check = Some(_clean)), true)
      val old = K.Saved(_append(from, "intent"), K.Payload(input, 1, None), _controller)
      When("each unresolved chain is reconstructed without inferring no-commit from absent success")
      val checks = Vector(clean.copy(intent = None), clean, K.Attempt(None, Some(old)))
        .map(attempt => take(K.reconcileAttemptsC(from, input, 1, Vector(attempt))))
      Then("each retains its actual pending record and forbids another attempt")
      checks.foreach { progress =>
        progress.nextAttempt shouldBe None
        progress.pending should not be empty
        progress.failed shouldBe empty
      }
      checks.map(_.record) shouldBe Vector(clean.check.get.record, clean.intent.get.record, old.record)
    }

    "reject gaps, duplicates, pending successors and checkless retry or changed intent facts" in {
      Given("one retained failed check followed by either a proper pending attempt or contradictory pairs")
      val from = _frontier
      val input = _input(from)
      val first = _attempt(from, K.Payload(input, 1, None, check = Some(_failed)))
      val second = _attempt(first.check.get.record, K.Payload(input, 1, None, 1, Some(_clean)), true)
      val rewritten = second.intent.get.copy(payload = second.intent.get.payload.copy(changes = Vector(
        K.Change("src/one.scala", "100644", "d" * 40, "e" * 40))))
      val cases = Vector(Vector(K.Attempt(None, None), first), Vector(first, first),
        Vector(first, second, K.Attempt(None, None)), Vector(first, K.Attempt(None, second.intent)),
        Vector(first, second.copy(intent = Some(rewritten))), Vector(first.copy(intent = first.check)),
        Vector(K.Attempt(None, Some(K.Saved(_append(from, "intent"),
          K.Payload(input, 1, None, check = Some(_clean)), _controller)))))
      cases.zipWithIndex.foreach { (attempts, index) =>
        Given("contradictory retained attempt case " + index)
        When("the same pure owner reconciles those pairs")
        val result = K.reconcileAttemptsC(from, input, 1, attempts)
        Then("no contradiction can select a fresh effect or rewrite a prior result")
        result.isSuccess shouldBe false
      }
    }

    "reject crossed original input, native identity, history, causation, ordinal and exhausted attempt facts" in {
      Given("a canonical selected failed check and its immutable original Closing source")
      val from = _frontier
      val input = _input(from)
      val saved = _attempt(from, K.Payload(input, 1, None, check = Some(_failed))).check.get
      val crossedinputs = Vector(input.copy(instance = "foreign"), input.copy(sourceRevision = "99"),
        input.copy(plan = "foreign"), input.copy(planRevision = "2"), input.copy(step = "S2"),
        input.copy(generation = 2), input.copy(parent = input.parent.copy(tree = "d" * 40)),
        input.copy(candidate = input.candidate.copy(tree = "e" * 40)), input.copy(reviewEvidence = "other-review"))
      val crossedrecords = Vector(saved.record.copy(identity = InstanceIdentity("foreign")),
        saved.record.copy(definition = take(RepositorySyncDefinitionBinding.bindC)),
        saved.record.copy(correlation = CorrelationReference("foreign")),
        saved.record.copy(causation = CausationReference("ordered-step-closure:wrong")),
        saved.record.copy(history = saved.record.history.drop(1)),
        saved.record.copy(history = saved.record.history.updated(0,
          saved.record.history.head.copy(causation = CausationReference("rewritten-prefix")))),
        saved.record.copy(lifecycle = Lifecycle.NotStarted),
        _append(saved.record, "check-result"))
      val alternatives = crossedinputs.map(value => saved.copy(payload = saved.payload.copy(input = value))) ++
        crossedrecords.map(value => saved.copy(record = value)) ++ Vector(
          saved.copy(payload = saved.payload.copy(ordinal = 2)), saved.copy(payload = saved.payload.copy(attempt = 1)),
          saved.copy(payload = saved.payload.copy(attempt = Int.MaxValue)),
          saved.copy(payload = saved.payload.copy(check = None)),
          saved.copy(payload = saved.payload.copy(commit = Some(O.Commit("f" * 40, _base.head, _candidate.tree)))))
      alternatives.zipWithIndex.foreach { (value, index) =>
        Given("crossed or rewritten retained check case " + index)
        When("reconciliation compares the entire original payload and canonical native successor")
        val result = K.reconcileAttemptsC(from, input, 1, Vector(K.Attempt(Some(value), None)))
        Then("the invalid pair grants neither retry nor native progress")
        result.isSuccess shouldBe false
      }
    }
  }

  "Bounded EOF correction" should {
    "admit multiple regular files with only extra terminal LF removal and preserve original targets" in {
      Given("earlier actual-shaped EOF-only findings covering two owned files and exact typed old/new blobs")
      val changes = Vector(_change(), _change("docs/check.md", "1" * 40, "2" * 40, "100755"))
      val blobs = Map("d" * 40 -> _blob("source\n\n\n"), "e" * 40 -> _blob("source\n"),
        "1" * 40 -> _blob("approved document\n\n"), "2" * 40 -> _blob("approved document\n"))
      val failure = _failed.copy(eofPaths = _paths)
      val input = _input(_frontier)
      When("the byte predicate classifies these exact native differences and an unchanged selected index")
      val actual = take(_admit(changes, blobs, Vector(failure)))
      val unchanged = take(_admit(Vector.empty, Map.empty, Vector.empty))
      Then("only the canonical correction vector is returned without relabeling candidate, validation or review")
      actual shouldBe Vector(K.Change("docs/check.md", "100755", "1" * 40, "2" * 40),
        K.Change("src/one.scala", "100644", "d" * 40, "e" * 40))
      unchanged shouldBe Vector.empty
      input.candidate shouldBe _candidate
      input.reviewEvidence shouldBe "review-original"
      _controller.checksReceipt shouldBe Some("checks-original")
      _controller.plan.steps(1).ownedPaths shouldBe Vector("docs/next.md")
    }

    "reject semantic, whitespace, CR, binary, unavailable and incomplete typed blob corrections" in {
      val cases = Vector("semantic" -> (_blob("source\n\n"), _blob("changed\n")),
        "trailing space" -> (_blob("source \n\n"), _blob("source\n")),
        "CR removal" -> (_blob("source\r\n\r\n"), _blob("source\n")),
        "no extra LF" -> (_blob("source\n"), _blob("source\n")),
        "blank file" -> (_blob("\n\n"), _blob("\n")),
        "binary NUL" -> (_blob("source\u0000\n\n"), _blob("source\u0000\n")),
        "unavailable text" -> (_blob("source\n\n").copy(text = None), _blob("source\n")),
        "truncated bytes" -> (_blob("source\n\n").copy(bytes = Vector(1.toByte)), _blob("source\n")),
        "invalid UTF8" -> (GitBlobContent(Vector(0xc3.toByte, 0x28.toByte, 10.toByte, 10.toByte), Some("invalid")), _blob("source\n")))
      cases.foreach { (label, values) =>
        Given("an earlier EOF finding with " + label + " blob facts")
        When("the predicate compares complete UTF8 bytes by immutable object IDs")
        val result = _admit(Vector(_change()), Map("d" * 40 -> values._1, "e" * 40 -> values._2))
        Then("the original review is insufficient for this changed content")
        result.isSuccess shouldBe false
      }
    }

    "reject mode, identity, path, status and missing failed-EOF provenance" in {
      val original = _change()
      val changes = Vector(original.copy(status = "D", newBlob = None), original.copy(status = "R100"),
        original.copy(status = "T"), original.copy(newMode = "100755"),
        original.copy(oldMode = "120000", newMode = "120000"),
        original.copy(path = take(GitPath.parseC("foreign.scala"))), original.copy(oldBlob = original.newBlob),
        original.copy(oldBlob = Some(take(GitObjectId.parseC("9" * 40)))),
        original.copy(newBlob = Some(take(GitObjectId.parseC("8" * 40)))))
      val blobs = Map("d" * 40 -> _blob("source\n\n"), "e" * 40 -> _blob("source\n"))
      changes.zipWithIndex.foreach { (change, index) =>
        Given("a changed mode, native object identity, path or status case " + index)
        When("the typed differences are admitted against the original selected scope")
        val result = _admit(Vector(change), blobs)
        Then("no unsupported difference can reuse original result targets")
        result.isSuccess shouldBe false
      }
      Vector(Vector.empty[K.Precheck], Vector(_clean), Vector(_failed.copy(eofOnly = false)),
        Vector(_failed.copy(eofPaths = Vector("docs/check.md")))).zipWithIndex.foreach { (failed, index) =>
        Given("missing or foreign earlier EOF-only findings case " + index)
        When("otherwise valid terminal LF removal is classified")
        val result = _admit(Vector(original), blobs, failed)
        Then("absent success, an exit code or another path's finding supplies no correction authority")
        result.isSuccess shouldBe false
      }
      Given("duplicate native path differences and a missing actual blob")
      When("each is classified against the actual-shaped EOF provenance")
      val duplicate = _admit(Vector(original, original), blobs)
      val missing = _admit(Vector(original), blobs - ("e" * 40))
      Then("both incomplete change sets reject")
      duplicate.isSuccess shouldBe false
      missing.isSuccess shouldBe false
    }
  }

  "Explicit selected host declarations" should {
    "bind exactly three selected kinds per Step and fixed blob/tree reads without cross-Step paths" in {
      Given("two exact per-Step owned vectors, shared approved document and their separate messages")
      val selections = Vector(_paths, Vector("docs/check.md", "docs/next.md"))
      When("the public host fixture emits canonical typed Git JSON")
      val plan = TypedGitHostPlanFixture.plan(_host, kinds = TypedGitHostPlanFixture.observations,
        goalPaths = _paths, goalMessages = Vector("Step one", "Step two"), goalSelections = selections)
      val declarations = _declarations(plan)
      val selectedkinds = Set("commit-selected", "check-selected-index", "selected-index-changes")
      Then("each bounded declaration contains only its selected Step vector and the exact public field set")
      selections.zipWithIndex.foreach { (paths, index) =>
        val selected = declarations.filter(_.hcursor.get[String]("selectionId").toOption.contains("goal-step-" + (index + 1)))
        selected.map(_.hcursor.get[String]("operation").toOption.get).toSet shouldBe selectedkinds
        selected.size shouldBe 3
        selected.foreach { value =>
          value.hcursor.get[Vector[String]]("selectedPaths").toOption.get shouldBe paths
          value.asObject.get.keys.toSet shouldBe Set("target", "operation", "capability", "identity", "gitExecutable",
            "localBranch", "remote", "remoteBranch", "endpoint", "commitIdentity", "selectionId", "selectedPath",
            "selectedPaths", "commitMessage", "limits", "environment")
        }
      }
      declarations.filter(value => selectedkinds(value.hcursor.get[String]("operation").toOption.get)).size shouldBe 6
      declarations.map(_.hcursor.get[String]("operation").toOption.get).toSet should contain allOf ("blob-content", "tree-changes")
      TypedGitHostPlanFixture.limits.hcursor.get[Int]("argumentCount").toOption.get shouldBe 512
      declarations.filterNot(value => selectedkinds(value.hcursor.get[String]("operation").toOption.get))
        .foreach(_.asObject.get.keys.toSet should not contain "selectedPaths")
    }

    "require explicit selections and retain read checks while honoring missing identity and denied capabilities" in {
      Given("actual closure messages without explicit paths or with invalid path/count declarations")
      val invalid = Vector(Vector.empty[Vector[String]], Vector(Vector.empty[String]),
        Vector(Vector("src/one.scala", "src/one.scala")), Vector(Vector("../foreign")),
        Vector(Vector("src/one.scala"), Vector("docs/check.md")),
        Vector((1 to 257).map(index => "path-" + index).toVector))
      invalid.foreach { paths =>
        Given("an obsolete or invalid explicit selection " + paths.toString)
        When("the fixture is asked to emit a selected commit without its exact bounded vector")
        val result = scala.util.Try(TypedGitHostPlanFixture.plan(_host, goalPaths = _paths,
          goalMessages = Vector("Step one"), goalSelections = paths))
        Then("the obsolete implicit all-index commit route is unavailable")
        result.isFailure shouldBe true
      }
      Given("one explicit selected vector without a commit identity and a denied selected check capability")
      val denied = TypedGitHostPlanFixture.capability(GitOperationKind.CheckSelectedIndex, Some("goal-step-1"))
      When("the ordinary fixture constructs only the admitted selected reads")
      val plan = TypedGitHostPlanFixture.plan(_host, goalMessages = Vector("Step one"), goalSelections = Vector(_paths),
        commitIdentity = false, denied = Set(denied))
      val selected = _declarations(plan).filter(_.hcursor.get[String]("selectionId").toOption.contains("goal-step-1"))
      val capabilities = plan.hcursor.get[Vector[Json]]("grants").toOption.get.head.hcursor.get[Vector[String]]("capabilities").toOption.get
      Then("both selected checks retain the exact vector while commit and the denied grant remain absent")
      selected.map(_.hcursor.get[String]("operation").toOption.get).toSet shouldBe Set("check-selected-index", "selected-index-changes")
      selected.foreach(_.hcursor.get[Vector[String]]("selectedPaths").toOption.get shouldBe _paths)
      capabilities should not contain denied
      Given("a pure startup host with neither messages nor selections")
      When("it emits observation declarations")
      val startup = TypedGitHostPlanFixture.plan(_host, kinds = TypedGitHostPlanFixture.observations)
      Then("no implicit selected commit is installed")
      _declarations(startup).map(_.hcursor.get[String]("operation").toOption.get) should not contain "commit-selected"
    }
  }
}
