package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{ProcessExecutionCapture, ProcessExecutionResult, ProcessExecutionTermination, GitObjectId, GitPath, GitTreeChange, SbtTarget, SbtTestScope, SbtOperation, SbtExecutionResult}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import RepositorySyncNativeMergeValidation as V
import RepositorySyncNativeMergeReview as R
import RepositorySyncNativeProgressStore as N
import RepositorySyncManagedFetch as Fetch
import RepositorySyncManagedGitObservation as G
import SplitPhaseExplicitPartitionFixture.take

/** Actual selected facts, not policy metadata or re-encoded body certificates.
  * @since   Oct.  8, 2026
  *  version Oct. 24, 2026
  * @version Nov. 26, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncNativeValidationApplicabilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val policy = V.Policy("original-policy", 1, "required", "Original selected rationale", Vector(V.Check("focused", 1)))
  private val receipts = Vector("index-local", "index-remote", "focused").map(id => V.Receipt(id, "exited", Some(0), "", ""))
  private val state = V.State(policy.mode, policy.reason, policy.checks, "3", receipts)
  private val before = G.Observed("main", "a" * 40, "b" * 40, Vector.empty, Some("c" * 40), Some(G.Counts(1, 1)))
  private val input = R.Request("selected-instance", "7", before.head, "c" * 40, "d" * 40,
    Vector("one.scala"), "Original local diff", "Original remote diff", state.mode, state.reason,
    state.selected.map(_.identity), state.receipts)
  private def nested(value: Json) = value.hcursor.downField("payload").focus.get
  private def replace(value: Json, body: Json) = value.mapObject(_.add("payload", body))

  private val _merge_policy = {
    val compile = V.Check("compile", 1, WorkflowSbtValidation.Kind.Compile)
    val test = V.Check("source-test", 2)
    val full = V.Check("full-test", 3)
    RepositorySyncProgramImpact.Policy(Vector(compile, test).map(check => RepositorySyncProgramImpact.Coverage(
      check, Vector("src"), Vector("src"), "Declared source coverage")), Vector(full), Vector("build.sbt"),
      "Full validates broader integrated logic", Vector.empty)
  }
  private def _source_change(path: String, before: String, after: String): GitTreeChange =
    GitTreeChange("100644", "100644", Some(take(GitObjectId.parseC(before * 40))),
      Some(take(GitObjectId.parseC(after * 40))), "M", take(GitPath.parseC(path)))
  private def _source_inputs(paths: Vector[String]) = RepositorySyncDocumentInputs.Assessment(paths, paths,
    Vector.empty, Vector.empty, "Known source inputs")

  "Shared native merge scope" should {
    "select Admission for separate files carried from either parent in either merge stage" in {
      Given("complete base-to-parent and base-to-candidate source changes with declared coverage")
      val first = _source_change("src/First.scala", "1", "2")
      val second = _source_change("src/Second.scala", "3", "4")
      val inputs = _source_inputs(Vector(first.path.value, second.path.value))
      When("the same native selector consumes local/remote parents in either order")
      val selections = Vector((Vector(first), Vector(second)), (Vector(second), Vector(first))).map { (left, right) =>
        take(RepositorySyncProgramImpact.mergeSelectionC(_merge_policy, inputs, left, right, Vector(first, second),
          _ => fail("Exact complete file carry-through needs no comment claim")))
      }
      Then("every selected Test carries Admission while Compile and lint retain their duties")
      selections.foreach { selected =>
        selected.depth shouldBe "ADMISSION"
        selected.checks.map(_.effectiveTestScope) shouldBe Vector(SbtTestScope.Full, SbtTestScope.Admission)
        selected.checks.map(_.identity) shouldBe Vector("compile", "source-test")
        take(V.productTargetC(selected.checks.last)).testScope shouldBe SbtTestScope.Admission
      }
    }

    "require Full for clean within-file logic composition and edited resolution logic" in {
      Given("two parents change different methods in one source file without a textual conflict")
      val left = _source_change("src/Input.scala", "1", "2")
      val right = _source_change("src/Input.scala", "1", "3")
      val candidate = _source_change("src/Input.scala", "1", "4")
      val texts = Map("2" * 40 -> "object Input { val a = 2; val b = 1 }\n",
        "3" * 40 -> "object Input { val a = 1; val b = 3 }\n",
        "4" * 40 -> "object Input { val a = 2; val b = 3 }\n")
      When("the final index contains both contributions or a new edited expression")
      val selected = Vector(texts, texts.updated("4" * 40, "object Input { val a = 4; val b = 3 }\n")).map { blobs =>
        take(RepositorySyncProgramImpact.mergeSelectionC(_merge_policy, _source_inputs(Vector(left.path.value)),
          Vector(left), Vector(right), Vector(candidate), id => Consequence.success(blobs.get(id.value))))
      }
      Then("Git's clean merge and conflict status cannot reduce the required Full scope")
      selected.foreach { value =>
        value.depth shouldBe "FULL"
        value.checks shouldBe _merge_policy.fullChecks
        value.checks.forall(_.effectiveTestScope == SbtTestScope.Full) shouldBe true
      }
    }

    "accept comment text resolution while preserving Scala strings, layout and directive semantics" in {
      Given("a comment-only conflict with code and indentation preserved")
      val left = _source_change("src/Input.scala", "1", "2")
      val right = _source_change("src/Input.scala", "1", "3")
      val candidate = _source_change("src/Input.scala", "1", "4")
      val text = "object Input:\n  val text = \"https://host/*literal*/\" // local comment\n  val a = 1\n"
      val resolved = text.replace("local comment", "combined comment")
      val blobs = Map("2" * 40 -> text, "3" * 40 -> text.replace("local comment", "remote comment"), "4" * 40 -> resolved)
      When("typed blobs establish ordinary-comment equivalence with a parent")
      val selected = take(RepositorySyncProgramImpact.mergeSelectionC(_merge_policy, _source_inputs(Vector(left.path.value)),
        Vector(left), Vector(right), Vector(candidate), id => Consequence.success(blobs.get(id.value))))
      Then("Admission is selected without erasing literal comment markers or indentation")
      selected.depth shouldBe "ADMISSION"
      RepositorySyncProgramImpact.ordinaryScalaCommentsEquivalent(text, resolved) shouldBe true
      Vector(text.replace("  val a", "val a"), text.replace("val a = 1", "val a = 2"),
        text.replace("host/*literal*/", "other/*literal*/"), text + "//> using dep example\n",
        text.replace("// local comment", "// scalafix:off"), text.replace("val a = 1", "val a = s\"value\""))
        .foreach(other => RepositorySyncProgramImpact.ordinaryScalaCommentsEquivalent(text, other) shouldBe false)
      RepositorySyncProgramImpact.ordinaryScalaCommentsEquivalent("val x = 1 // added\n", "val x = 1\n") shouldBe true
      RepositorySyncProgramImpact.ordinaryScalaCommentsEquivalent("val x = 1 /* added */\n", "val x = 1\n") shouldBe true
      RepositorySyncProgramImpact.ordinaryScalaCommentsEquivalent("val x = a/* separator */b\n", "val x = ab\n") shouldBe false
      RepositorySyncProgramImpact.ordinaryScalaCommentsEquivalent("val x = a/* one */b\n", "val x = ab\n") shouldBe false
      RepositorySyncProgramImpact.ordinaryScalaCommentsEquivalent("val x = 1 /* one /* nested */ */\n",
        "val x = 1 /* other /* nested */ */\n") shouldBe true
      RepositorySyncProgramImpact.ordinaryScalaCommentsEquivalent("val x = \"\"\"// one\"\"\"\n",
        "val x = \"\"\"// two\"\"\"\n") shouldBe false
    }

    "require Full when a multiline literal changes along with a comment" in {
      val left = _source_change("src/Input.scala", "1", "2")
      val right = _source_change("src/Input.scala", "1", "3")
      val candidate = _source_change("src/Input.scala", "1", "4")
      val original = "object Input {\n  val x = \"\"\"first  \nsecond\"\"\" // local\n}\n"
      val other = original.replace("// local", "// remote")
      val changed = original.replace("first  \n", "first \n").replace("// local", "// resolved")
      val blobs = Map("2" * 40 -> original, "3" * 40 -> other, "4" * 40 -> changed)
      val selected = take(RepositorySyncProgramImpact.mergeSelectionC(_merge_policy, _source_inputs(Vector(left.path.value)),
        Vector(left), Vector(right), Vector(candidate), id => Consequence.success(blobs.get(id.value))))
      selected.depth shouldBe "FULL"
      RepositorySyncProgramImpact.ordinaryScalaCommentsEquivalent(original, other) shouldBe true
      RepositorySyncProgramImpact.ordinaryScalaCommentsEquivalent(original, changed) shouldBe false
    }

    "resume only the exact legacy focused selection for the original candidate" in {
      val inputs = _source_inputs(Vector("src/Input.scala"))
      val legacy = RepositorySyncProgramImpact.select(_merge_policy, inputs)
      val host = V.Policy("original-policy", 1, "required", "Original selected rationale",
        _merge_policy.checks, impact = Some(_merge_policy))
      val issued = V.State(host.mode, legacy.reason, legacy.checks, "3", Vector.empty,
        Some(inputs), Some(legacy))
      V.retainedLegacySelection(issued, host, inputs, "3", false) shouldBe true
      val reused = RepositorySyncProgramImpact.Reuse("source-test", "validation", "closure", None)
      V.retainedLegacySelection(issued.copy(impact = Some(legacy.copy(reused = Vector(reused)))),
        host, inputs, "3", false) shouldBe true
      val shared = V.Check("shared-test", 4)
      val hopPolicy = _merge_policy.copy(coverage = _merge_policy.coverage :+
        RepositorySyncProgramImpact.Coverage(shared, Vector("src/Other.scala"), Vector("src/Input.scala"), "Shared consumer"))
      val hopHost = host.copy(checks = hopPolicy.checks, impact = Some(hopPolicy))
      val local = RepositorySyncProgramImpact.select(hopPolicy, inputs)
      val localIntent = issued.copy(selected = local.checks, impact = Some(local))
      V.retainedLegacySelection(localIntent, hopHost, inputs, "3", false) shouldBe true
      V.retainedLegacySelection(localIntent, hopHost, inputs, "3", true) shouldBe false
      V.retainedLegacySelection(issued, host, inputs, "4", false) shouldBe false
      V.retainedLegacySelection(issued.copy(selected = _merge_policy.fullChecks), host, inputs, "3", false) shouldBe false
      val admission = legacy.checks.map(check => if (check.kind == WorkflowSbtValidation.Kind.Test)
        check.copy(testScope = Some("admission")) else check)
      V.retainedLegacySelection(issued.copy(selected = admission), host, inputs, "3", false) shouldBe false
      V.retainedLegacySelection(issued.copy(impact = Some(legacy.copy(checks = _merge_policy.fullChecks))),
        host, inputs, "3", false) shouldBe false
    }

    "retain an issued legacy Full selection and its receipts when source carry-through now selects Admission" in {
      val first = _source_change("src/First.scala", "1", "2")
      val second = _source_change("src/Second.scala", "3", "4")
      val inputs = _source_inputs(Vector(first.path.value, second.path.value))
      val oldPolicy = _merge_policy.copy(fullPaths = Vector("src"),
        fullChecks = Vector(_merge_policy.coverage.last.check, _merge_policy.fullChecks.head),
        ordinaryMergeSources = true)
      val host = V.Policy("original-policy", 1, "required", "Original selected rationale",
        oldPolicy.checks, impact = Some(oldPolicy))
      val old = RepositorySyncProgramImpact.select(oldPolicy, inputs)
      val current = take(RepositorySyncProgramImpact.mergeSelectionC(oldPolicy, inputs, Vector(first), Vector(second),
        Vector(first, second), _ => fail("Exact file carry-through needs no blob")))
      old.depth shouldBe "FULL"
      current.depth shouldBe "ADMISSION"
      val fullReceipts = Vector("index-local", "index-remote") ++ old.checks.map(_.identity)
      val issued = V.State(host.mode, old.reason, old.checks, "3",
        fullReceipts.map(id => V.Receipt(id, "exited", Some(0), "", "")), Some(inputs), Some(old))
      V.retainedLegacySelection(issued, host, inputs, "3", false) shouldBe true
      V.successful(issued.receipts, selected = issued.selected) shouldBe true
      V.retainedLegacySelection(issued, host, inputs, "4", false) shouldBe false
      V.retainedLegacySelection(issued.copy(selected = issued.selected.reverse), host, inputs, "3", false) shouldBe false
      V.retainedLegacySelection(issued.copy(selected = current.checks), host, inputs, "3", false) shouldBe false
      V.retainedLegacySelection(issued.copy(impact = Some(old.copy(checks = current.checks))),
        host, inputs, "3", false) shouldBe false
      V.retainedLegacySelection(issued, host, _source_inputs(Vector("src/Other.scala")), "3", false) shouldBe false
      V.successful(issued.receipts, selected = current.checks) shouldBe false
    }

    "preserve arbitrary ordinary line-comment text without weakening code or string identity" in {
      Given("ordinary alphanumeric comment contents on an unchanged Scala code line")
      val property = org.scalacheck.Prop.forAll(org.scalacheck.Gen.alphaNumStr) { comment =>
        val original = "object Input { val text = \"//literal\" } // old\n"
        val candidate = "object Input { val text = \"//literal\" } // " + comment + "\n"
        RepositorySyncProgramImpact.ordinaryScalaCommentsEquivalent(original, candidate) &&
          !RepositorySyncProgramImpact.ordinaryScalaCommentsEquivalent(original, candidate.replace("//literal", "//changed"))
      }
      When("the lexical equivalence property is checked across generated comment contents")
      val checked = org.scalacheck.Test.check(org.scalacheck.Test.Parameters.default.withMinSuccessfulTests(50), property)
      Then("only ordinary comment text is ignored")
      checked.passed shouldBe true
    }

    "keep documents structural and select the strongest scope for consumed or uncertain input" in {
      Given("a declared non-consumed document and source carry-through")
      val change = _source_change("src/Input.scala", "1", "2")
      val docs = RepositorySyncDocumentInputs.Assessment(Vector("docs/guide.md"), Vector.empty, Vector.empty, Vector.empty, "Non-consumed document")
      When("the same selector sees document-only, build-consumed document, broad, missing or unknown inputs")
      def _select_(inputs: RepositorySyncDocumentInputs.Assessment, policy: RepositorySyncProgramImpact.Policy = _merge_policy) =
        take(RepositorySyncProgramImpact.mergeSelectionC(policy, inputs, Vector(change), Vector.empty, Vector(change),
          _ => Consequence.success(None)))
      val source = _source_inputs(Vector(change.path.value))
      val consumed = source.copy(changedPaths = source.changedPaths :+ "docs/schema.md", consumedPaths = source.consumedPaths :+ "docs/schema.md")
      Then("only non-consumed documentation has no product check; every stronger requirement wins")
      _select_(docs).checks shouldBe empty
      _select_(docs).depth shouldBe "DOCUMENT"
      Vector(_select_(consumed), _select_(source.copy(unknownPaths = Vector("unknown/input"))),
        _select_(source.copy(binaryPaths = source.changedPaths)), _select_(source, _merge_policy.copy(fullPaths = Vector("src"))))
        .foreach(value => value.depth shouldBe "FULL")
      val combined = _source_change("src/Input.scala", "1", "3")
      take(RepositorySyncProgramImpact.mergeSelectionC(_merge_policy, source, Vector(change), Vector.empty, Vector(combined),
        _ => Consequence.success(None))).depth shouldBe "FULL"
    }
  }

  "Inferred standard source coverage" should {
    "specialize ordinary source merges while preserving explicit Full and nonmerge selection" in {
      Given("the standard inferred catch-all Full policy and an explicit broad policy with identical paths")
      val source = _source_change("src/Input.scala", "1", "2")
      val inputs = _source_inputs(Vector(source.path.value))
      val explicit = _merge_policy.copy(fullPaths = Vector("src"))
      val inferred = explicit.copy(ordinaryMergeSources = true)
      When("the candidate carries its complete source file through one parent")
      def _selected_(policy: RepositorySyncProgramImpact.Policy) = take(RepositorySyncProgramImpact.mergeSelectionC(policy,
        inputs, Vector(source), Vector.empty, Vector(source), _ => fail("No comment observation is needed")))
      Then("only inferred merge defaults can narrow source scope; explicit and nonmerge Full remain Full")
      _selected_(inferred).depth shouldBe "ADMISSION"
      _selected_(explicit).depth shouldBe "FULL"
      RepositorySyncProgramImpact.select(inferred, inputs).depth shouldBe "FULL"
      inferred.asJson.as[RepositorySyncProgramImpact.Policy] shouldBe Right(inferred)
      _merge_policy.asJson.hcursor.downField("ordinaryMergeSources").focus shouldBe None
      _merge_policy.asJson.as[RepositorySyncProgramImpact.Policy] shouldBe Right(_merge_policy)
    }
  }

  "Repository Test scope receipts" should {
    "decode historical Full and reject Admission on unrelated TestOnly duties" in {
      Given("a legacy Test check and a newly selected typed Admission check")
      val old = Json.obj("identity" -> "source-test".asJson, "ordinal" -> 2.asJson)
      val admission = V.Check("source-test", 2, testScope = Some("admission"))
      When("selected checks are persisted and the actual typed target is resolved")
      old.as[V.Check].map(_.effectiveTestScope) shouldBe Right(SbtTestScope.Full)
      admission.asJson.as[V.Check] shouldBe Right(admission)
      take(V.productTargetC(admission)).testScope shouldBe SbtTestScope.Admission
      Then("scope is never silently transferred to TestOnly or satisfied by an old Full receipt")
      admission.copy(kind = WorkflowSbtValidation.Kind.TestOnly).asJson.as[V.Check].isLeft shouldBe true
      V.productSuccessful(V.Receipt("source-test", "exited", Some(0), "", ""), admission) shouldBe false
      V.applies(state.copy(selected = Vector(admission)), policy.copy(checks = Vector(admission.copy(testScope = None)))) shouldBe false
    }

    "bind exact report cases and reject cross-scope acceptance or reuse" in {
      Given("retained typed Admission facts with an actual-shaped finite ScalaTest report")
      def _token_(value: String) = java.util.Base64.getUrlEncoder.withoutPadding().encodeToString(value.getBytes("UTF-8"))
      val suite = "example.SelectedSpec"
      val test = "selected case"
      val row = _token_(suite) + ":" + _token_(test)
      val text = Vector("CNCF-ADMISSION-V1 selected " + row, "CNCF-ADMISSION-V1 succeeded " + row,
        "[info] Total number of tests run: 1", "[info] Suites: completed 1, aborted 0",
        "[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0", "[info] All tests passed.",
        "CNCF-ADMISSION-V1 completed").mkString("\n")
      def _capture_(value: String) = { val bytes = value.getBytes("UTF-8").toVector; ProcessExecutionCapture(bytes, bytes.size.toLong, false) }
      val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(0), _capture_(text), _capture_(""), Vector.empty, 7L, "typed-test")
      val check = V.Check("source-test", 2, testScope = Some("admission"))
      val actual = SbtExecutionResult(take(V.productTargetC(check)), SbtOperation.Test, process)
      When("the normal typed result is retained and assessed against current selected checks")
      val receipt = take(V.sbtReceiptC(check.identity, check.kind, actual))
      Then("scope and exact selected cases survive, and neither Full nor mismatched cases can pass")
      V.productSuccessful(receipt, check) shouldBe true
      V.productSuccessful(receipt, check.copy(testScope = None)) shouldBe false
      V.productSuccessful(receipt.copy(sbt = receipt.sbt.map(_.copy(admissionCases = Some(Vector(
        WorkflowSbtValidation.AdmissionCase(suite, "another case")))))), check) shouldBe false
      val prepared = N.Payload("ValidationChecking", "/selected/repository", before,
        validation = Some(V.State("required", "Selected Admission", Vector(check), "3", receipts.take(2))))
      V.productAttemptPayloadC(prepared, check, org.goldenport.cncf.processexecution.SbtExecutionAttempt.Observed(
        actual.copy(target = actual.target.copy(testScope = SbtTestScope.Full)))).isSuccess shouldBe false
      receipt.asJson.as[V.Receipt].map(value => V.productSuccessful(value, check)) shouldBe Right(true)
      V.successful(receipts.take(2) :+ receipt, selected = Vector(check)) shouldBe true
      V.successful(receipts.take(2) :+ receipt, selected = Vector(check.copy(testScope = None))) shouldBe false
    }
  }

  "Complete semantic content" should {
    "retain large program coverage and FULL selection reasons through the actual typed policy and selection codecs" in {
      Given("declared consumed-input coverage with full explanations exceeding 64 KiB")
      val rationale = "選択根拠を保持する" * 10000
      val focused = V.Check("focused", 1)
      val full = V.Check("full", 2)
      val impact = RepositorySyncProgramImpact.Policy(
        Vector(RepositorySyncProgramImpact.Coverage(focused, Vector("src"), Vector("src", "build.sbt"), rationale)),
        Vector(full), Vector("build.sbt"), rationale, Vector.empty)
      val wire = io.circe.Encoder[RepositorySyncProgramImpact.Policy].apply(impact)
      val source = RepositorySyncDocumentInputs.Assessment(Vector("src/Example.scala"), Vector("src/Example.scala"),
        Vector.empty, Vector.empty, "Selected consumed source")
      When("normal policy decoding and program impact selection consume focused and broad changes")
      val restored = take(RepositorySyncProgramImpact.decodeC(wire))
      val selected = RepositorySyncProgramImpact.select(restored, source)
      val broad = RepositorySyncProgramImpact.select(restored, source.copy(consumedPaths = Vector("build.sbt")))
      Then("both selected reasons retain their complete explanation and exact check choices")
      restored shouldBe impact
      selected.reason shouldBe "FOCUSED: changed bounded inputs=src/Example.scala; " + rationale
      selected.checks shouldBe Vector(focused)
      broad.reason shouldBe "FULL: changed broad inputs=build.sbt; uncovered inputs=build.sbt; " + rationale
      broad.checks shouldBe Vector(full)
      Vector(selected, broad).foreach { value =>
        io.circe.Encoder[RepositorySyncProgramImpact.Selection].apply(value).as[RepositorySyncProgramImpact.Selection] shouldBe Right(value)
      }
      RepositorySyncProgramImpact.validateC(impact.copy(reason = rationale + "\n")).isSuccess shouldBe false
      RepositorySyncProgramImpact.validateC(impact.copy(coverage = impact.coverage.map(_.copy(inputPaths = Vector("../foreign"))))).isSuccess shouldBe false
    }

    "retain large purpose content and messages without weakening exact inventory coverage" in {
      Given("a complete selected Unicode file and a purpose proposal covering that actual inventory")
      val text = "完全な作業内容\n" * 20000
      val explanation = "判断理由" * 20000
      val request = RepositorySyncPurpose.Request("repository", "/selected/repository", "main", "a" * 40, "b" * 40,
        Vector(RepositorySyncPurpose.File("one.scala", "M", " ", Some(text), Some(text), Some(text), None)), Vector.empty)
      val group = RepositorySyncPurpose.Group("retained-purpose", explanation, explanation, Vector.empty,
        Vector(RepositorySyncPurpose.Content("one.scala", Some(text))), Vector.empty, explanation)
      val result = RepositorySyncPurpose.Result("resolved", explanation, Vector(group), Vector.empty)
      When("normal purpose admission and its public typed codec consume the full proposal")
      take(RepositorySyncPurpose.admitC(request, result)) shouldBe ()
      val restored = RepositorySyncPurpose.resultCodec.decode(RepositorySyncPurpose.resultCodec.encode(result))
      Then("the exact content, commit message and rationale remain available, and foreign or malformed content still fails")
      restored shouldBe Right(result)
      text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 131072
      Vector(group.copy(content = Vector(RepositorySyncPurpose.Content("foreign.scala", Some(text)))),
        group.copy(content = Vector(RepositorySyncPurpose.Content("one.scala", Some("\u0000")))),
        group.copy(content = Vector(RepositorySyncPurpose.Content("one.scala", Some(0xd800.toChar.toString))))).foreach { invalid =>
        RepositorySyncPurpose.admitC(request, result.copy(groups = Vector(invalid))).isSuccess shouldBe false
      }
    }

    "retain full review and selected lint reasons while preserving finding coverage and required choices" in {
      Given("an actual selected review request and a full multibyte explanation exceeding the obsolete semantic ceilings")
      val text = "判断を保持する" * 20000
      val result = R.Result("repair", text, Vector(R.Finding("finding-1", input.changedPaths, text)))
      val reviewpolicy = R.Policy("review-policy", 1)
      val lintpolicy = RepositorySyncMergeAssessment.Policy("conditional", "not-applicable", "not-applicable", text, Vector.empty)
      When("ordinary review admission and merge policy validation consume those semantic values")
      take(R.admitC(input, result, reviewpolicy)) shouldBe ()
      take(RepositorySyncMergeAssessment.validateC(lintpolicy)) shouldBe ()
      take(V.Policy("full-reason", 1, "required", text, policy.checks).validateC) shouldBe ()
      take(RepositorySyncDocumentInputs.validateC(RepositorySyncDocumentInputs.Policy(Vector("docs"), Vector.empty, text))) shouldBe ()
      Then("exact review payloads roundtrip and unsupported findings, missing reasons or invalid choices remain refused")
      R.resultCodec.decode(R.resultCodec.encode(result)) shouldBe Right(result)
      R.admitC(input, result.copy(findings = Vector(R.Finding("finding-1", Vector("foreign.scala"), text))), reviewpolicy).isSuccess shouldBe false
      R.admitC(input, result.copy(rationale = "\u0000"), reviewpolicy).isSuccess shouldBe false
      RepositorySyncMergeAssessment.validateC(lintpolicy.copy(lintReason = " ")).isSuccess shouldBe false
      RepositorySyncMergeAssessment.validateC(lintpolicy.copy(scalaLint = "implicit")).isSuccess shouldBe false
      RepositorySyncDocumentInputs.validateC(RepositorySyncDocumentInputs.Policy(Vector("../foreign"), Vector.empty, text)).isSuccess shouldBe false
    }
  }

  "Native validation facts" should {
    "refuse replacement of a selected Test binding by TestOnly while preserving old Test receipts" in {
      Given("a saved current-schema Test selection with an absent kind and five-field product receipt")
      val restored = V.stateCodec.decode(V.stateCodec.encode(state)).toOption.get
      val replacement = policy.copy(checks = Vector(V.Check("focused", 1, WorkflowSbtValidation.Kind.TestOnly)))
      When("current policy resolution consumes identity ordinal and kind against the retained selection")
      val compatible = V.applies(restored, policy.copy(identity = "other-metadata", revision = 9,
        checks = policy.checks :+ V.Check("unselected", 2, WorkflowSbtValidation.Kind.TestOnly)))
      val replaced = V.applies(restored, replacement)
      Then("metadata and unused bindings are inert, but a changed selected kind cannot promote or replay the old Test")
      compatible shouldBe true
      replaced shouldBe false
      restored shouldBe state
      V.successful(restored.receipts, selected = restored.selected) shouldBe true
      V.successful(restored.receipts, selected = replacement.checks) shouldBe false
      restored.receipts.last.sbt shouldBe None
      V.productSuccessful(restored.receipts.last, WorkflowSbtValidation.Kind.TestOnly) shouldBe false
    }

    "consume current selected facts and ignore unused annotations without policy extraction" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R1; Example: E1; an actual selected validation payload")
      val saved = N.Payload("MergeValidated", "/selected/repository", before,
        indexTree = Some("d" * 40), after = Some(before), mergeHead = Some("c" * 40), validation = Some(state))
      val wire = N.codec.encode(saved)
      val facts = nested(wire).hcursor.downField("validation").focus.get
      When("current output is produced and independently constructed diagnostic fields are present")
      val decorated = facts.mapObject(_.add("approve", Json.True).add("policy", Json.False)
        .add("selected", facts.hcursor.downField("selected").focus.get.mapArray(_.map(_.mapObject(_.add("command", Json.True)))))
        .add("receipts", facts.hcursor.downField("receipts").focus.get.mapArray(_.map(_.mapObject(_.add("authority", Json.Null))))))
      val decoded = V.stateCodec.decode(decorated)
      Then("actual facts decode without a policy copy or annotation becoming authority")
      decoded shouldBe Right(state)
      N.codec.decode(wire) shouldBe Right(saved)
      facts.asObject.get.keys.toSet should not contain "policy"
      state.productElementNames.toSet should not contain "policy"

      Given("the same current check state with required facts absent or mistyped")
      When("the typed consumer decodes required check fields")
      val wrongCheck = facts.mapObject(_.add("selected", Json.arr(Json.obj("identity" -> Json.fromString("focused"),
        "ordinal" -> Json.True))))
      Then("missing required collections and wrong ordinal types fail without a shape verifier")
      V.stateCodec.decode(facts.mapObject(_.remove("selected"))).isLeft shouldBe true
      V.stateCodec.decode(wrongCheck).isLeft shouldBe true
    }
    "resolve selected executable bindings without unchanged policy identity revision or complete declaration equality" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R1; Example: E2; original selected checks and current bindings")
      When("metadata changes or an extra unselected binding is installed")
      V.applies(state, policy.copy(identity = "different-metadata", revision = 8, reason = "Different projection",
        checks = policy.checks :+ V.Check("unselected", 2))) shouldBe true
      Then("a missing or incompatible selected binding or an actually different skip/required mode is refused")
      V.applies(state, policy.copy(checks = Vector.empty)) shouldBe false
      V.applies(state, policy.copy(checks = Vector(V.Check("focused", 2)))) shouldBe false
      V.applies(state, policy.copy(mode = "explicit-no-product-tests", checks = Vector.empty)) shouldBe false
    }
    "admit actual source and result coverage without a diagnostic observation witness" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R1; Example: E9; actual adjacent native frontiers")
      val initial = InstanceRecord(InstanceIdentity("validation-frontier"), take(RepositorySyncDefinitionBinding.bindC), initialRevision,
        Lifecycle.NotStarted, None, CorrelationReference("owned-correlation"), CausationReference("owned-start"), None, None, None, Vector.empty)
      def next(record: InstanceRecord, stage: String) = take(record.appendC(record.revision,
        HistoryEntry(HistorySequence(record.history.size.toLong + 1), record.revision.next, Lifecycle.Active,
          Some(ProgressionReference(stage)), record.correlation, CausationReference(stage), None, None, None)))
      val sourceRecord = next(next(next(initial, "sync.Fetched"), "sync.Integrated"), "sync.PendingMergePrepared")
      val resultRecord = next(sourceRecord, "sync.MergeValidated")
      val source = N.Stage(sourceRecord, N.Payload("PendingMergePrepared", "/selected/repository", before,
        indexTree = Some("d" * 40), after = Some(before), mergeHead = Some("c" * 40)))
      val selected = state.copy(sourceRevision = sourceRecord.revision.value.toString)
      val result = N.Stage(resultRecord, source.payload.copy(stage = "MergeValidated", validation = Some(selected),
        after = Some(before.copy(counts = Some(G.Counts(9, 9)), mergeBase = Some("e" * 40)))))
      When("selected execution facts cover the actual source and current bindings despite changed observation diagnostics")
      take(V.completedC(result, source, policy.copy(identity = "other-metadata", revision = 8), merge = true)) shouldBe ()
      Then("failed or missing actual results cannot become acceptance; source copies are not acceptance certificates")
      Vector(selected.copy(receipts = selected.receipts.dropRight(1)),
        selected.copy(receipts = selected.receipts.updated(2, selected.receipts(2).copy(exitCode = Some(1))))).foreach { contradicted =>
        V.completedC(result.copy(payload = result.payload.copy(validation = Some(contradicted))), source, policy, merge = true) shouldBe a[Consequence.Failure[?]]
      }
      Given("Spec: docs/spec/repository-sync-native-merge-repair.md; Rule: R6; Example: C1; existing E9 retained checks belong to another actual candidate source")
      val crossed = result.copy(payload = result.payload.copy(validation = Some(selected.copy(sourceRevision = "99"))))
      When("completedC consumes the retained checks against the actual selected source")
      val completed = V.completedC(crossed, source, policy, merge = true)
      Then("checks from another candidate source are refused")
      completed shouldBe a[Consequence.Failure[?]]
    }
  }

  "Historical structural Git Receipt compatibility" should {
    "roundtrip and reuse the exact old State and five-field Receipt schema without execution" in {
      Given("R1: an ordinary pre-migration validation State with its original structural and product receipts")
      val old = Json.obj("mode" -> Json.fromString("required"), "reason" -> Json.fromString(policy.reason),
        "selected" -> Json.arr(Json.obj("identity" -> Json.fromString("focused"), "ordinal" -> Json.fromInt(1))),
        "sourceRevision" -> Json.fromString("3"), "receipts" -> Json.fromValues(receipts.map(receipt => Json.obj(
          "identity" -> Json.fromString(receipt.identity), "termination" -> Json.fromString(receipt.termination),
          "exitCode" -> Json.fromInt(receipt.exitCode.get), "stdout" -> Json.fromString(receipt.stdout),
          "stderr" -> Json.fromString(receipt.stderr)))))
      When("the existing codec reads those historical facts and the current acceptance policy reuses them")
      val restored = V.stateCodec.decode(old).toOption.get
      val accepted = V.successful(restored.receipts, merge = true)
      Then("the exact original schema and selected facts survive without a process request or policy version")
      restored shouldBe state
      V.stateCodec.encode(restored) shouldBe old
      accepted shouldBe true
      V.successful(V.stateCodec.decode(V.stateCodec.encode(restored)).toOption.get.receipts, merge = true) shouldBe true
      old.hcursor.downField("receipts").focus.get.asArray.get.foreach(receipt =>
        receipt.asObject.get.keys.toSet shouldBe Set("identity", "termination", "exitCode", "stdout", "stderr"))
    }

    "preserve clean single and inherited findings while rejecting shared opaque findings" in {
      Given("R2: clean actual-exit receipts and complete historical exit-two structural findings")
      val clean = V.Receipt("index-local", "exited", Some(0), "", "")
      val local = clean.copy(exitCode = Some(2), stdout = "one.scala:7: trailing whitespace\r\n+ context\n")
      val remote = clean.copy(identity = "index-remote", exitCode = Some(2), stdout = "other.scala:8: whitespace\n")
      val product = V.Receipt("focused", "exited", Some(0), "Actual product output", "")
      When("the unchanged application policy compares CNCF-decoded parent finding sets")
      val cleanmerge = V.successful(Vector(clean, clean.copy(identity = "index-remote"), product))
      val single = V.successful(Vector(local))
      val nonmerge = V.successful(Vector(local, product), merge = false)
      val inherited = V.successful(Vector(local, clean.copy(identity = "index-remote"), product))
      val independent = V.successful(Vector(local, remote, product))
      val shared = V.successful(Vector(local, remote.copy(stdout = local.stdout), product))
      Then("a single structural sample retains its old acceptance and only shared parent findings block a merge")
      cleanmerge shouldBe true
      single shouldBe true
      nonmerge shouldBe true
      inherited shouldBe true
      independent shouldBe true
      shared shouldBe false
      V.successful(Vector(clean, clean.copy(identity = "index-remote"), product.copy(exitCode = Some(7)))) shouldBe false
    }

    "refuse unsupported malformed or missing structural receipts and admit complete large findings" in {
      Given("R3: the historical schema can contain failed indeterminate incomplete or contradictory stored values")
      val clean = V.Receipt("index-local", "exited", Some(0), "", "")
      val invalid = Vector(clean.copy(termination = "indeterminate", exitCode = None),
        clean.copy(exitCode = None), clean.copy(exitCode = null), clean.copy(stdout = null), clean.copy(stderr = null),
        clean.copy(stdout = "unexpected"), clean.copy(stderr = "unexpected"), clean.copy(stdout = "\u0000"),
        clean.copy(stdout = 0xd800.toChar.toString), clean.copy(exitCode = Some(2), stdout = "+ source only\n"),
        clean.copy(exitCode = Some(2)), clean.copy(exitCode = Some(2), stdout = "finding", stderr = "warning"),
        clean.copy(stderr = "x" * 65537),
        clean.copy(exitCode = Some(1)), clean.copy(exitCode = Some(7)), clean.copy(exitCode = Some(128)))
      When("retained admission checks completion exit ordinary UTF-8 and NUL before CNCF's pure codec")
      val accepted = invalid.map(receipt => V.successful(Vector(receipt)))
      Then("none becomes a usable structural fact and no new execution is requested")
      accepted shouldBe Vector.fill(invalid.size)(false)
      V.successful(Vector(null.asInstanceOf[V.Receipt])) shouldBe false
      V.successful(Vector(clean.copy(exitCode = Some(2), stdout = "x" * 65536))) shouldBe true
      V.successful(Vector(clean.copy(exitCode = Some(2), stdout = "é" * 40000))) shouldBe true
    }

    "retain admitted actual negative captures and leave malformed captures uncertain" in {
      Given("R4: complete raw results from an ordinary failed or actually timed-out selected check")
      def _capture_(text: String): ProcessExecutionCapture = {
        val bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
        ProcessExecutionCapture(bytes, bytes.size.toLong, false)
      }
      val failed = ProcessExecutionResult(ProcessExecutionTermination.Exited(7), _capture_("actual failure\n"),
        _capture_("diagnostic\n"), Vector.empty, 1L, "selected-check")
      val unknown = failed.copy(termination = ProcessExecutionTermination.TimedOut)
      When("the existing receipt admission stores complete strict actual text")
      val failedreceipt = take(V.receiptC("index-local", failed))
      val unknownreceipt = take(V.receiptC("index-local", unknown))
      val malformed = Vector(failed.copy(stdout = failed.stdout.copy(truncated = true)),
        failed.copy(stdout = failed.stdout.copy(byteCount = 0L)),
        failed.copy(stdout = _capture_("\u0000")),
        failed.copy(stdout = ProcessExecutionCapture(Vector(0xff.toByte), 1L, false)))
      Then("failure and actual timeout retain their real termination and captures while refused capture supplies no fabricated result for replay")
      failedreceipt shouldBe V.Receipt("index-local", "exited", Some(7), "actual failure\n", "diagnostic\n")
      unknownreceipt shouldBe V.Receipt("index-local", "timed-out", None, "actual failure\n", "diagnostic\n")
      V.successful(Vector(failedreceipt)) shouldBe false
      V.successful(Vector(unknownreceipt)) shouldBe false
      malformed.foreach(result => V.receiptC("index-local", result).isSuccess shouldBe false)
      val large = failed.copy(stdout = _capture_("界" * 40000), stderr = _capture_("診断" * 40000))
      val retained = take(V.receiptC("index-local", large))
      retained.termination shouldBe "exited"
      retained.exitCode shouldBe Some(7)
      retained.stdout shouldBe "界" * 40000
      retained.stderr shouldBe "診断" * 40000
      V.productSuccessful(retained) shouldBe false
      V.Receipt.mergeRepairDecoder.decodeJson(V.Receipt.mergeRepairEncoder(retained)) shouldBe Right(retained)
    }
  }

  "Native merge review facts" should {
    "consume only current typed review facts and ignore inert policy or command annotations" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: current Phase5.2; Example: E5; current review input and selected check facts")
      val wire = R.inputCodec.encode(input)
      val check = nested(wire).hcursor.downField("checks").focus.get.asArray.get.head
      val annotated = nested(wire).mapObject(_.add("reviewPolicyIdentity", Json.True).add("approve", Json.True)
        .add("checks", Json.fromValues(input.checks.indices.map { index =>
          nested(wire).hcursor.downField("checks").focus.get.asArray.get(index).mapObject(_.add("command", Json.True))
        })))
      When("current request.v2 decodes unused root and nested annotations")
      val actual = R.inputCodec.decode(replace(wire.mapObject(_.add("authority", Json.True)), annotated))
      Then("the same assigned business input is returned, without policy extraction or command authority")
      actual shouldBe Right(input)

      Given("an unsupported old schema or actual missing/mistyped consumed data")
      val invalid = Vector(wire.mapObject(_.add("schemaVersion",
        Json.fromString("sm-workflow.repository-native-merge-review-request.v1"))),
        replace(wire, nested(wire).mapObject(_.remove("indexTree"))),
        replace(wire, nested(wire).mapObject(_.add("checks", Json.arr(check.mapObject(_.add("identity", Json.True)))))))
      When("the current-only typed decoder consumes those records")
      val rejected = invalid.map(R.inputCodec.decode)
      Then("required business types and the current schema own refusal, not exact key sets")
      rejected.foreach(_.isLeft shouldBe true)

      Given("an actual semantic review result with an inert command diagnostic")
      val result = R.Result("accept", "Actual reviewed meaning", Vector.empty)
      val resultWire = R.resultCodec.encode(result)
      When("the current result.v1 codec consumes that semantic result")
      val decodedResult = R.resultCodec.decode(replace(resultWire, nested(resultWire).mapObject(_.add("command", Json.True))))
      Then("diagnostics are not a reason to reject the actual result")
      decodedResult shouldBe Right(result)
    }
    "interpret new semantic findings at their actual assigned business boundary" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: current Phase5.2; Example: E6; assigned changed paths and a genuine review disposition")
      val policy = R.Policy("review-installation", 1)
      val finding = R.Finding("finding", Vector("one.scala"), "Repair the assigned source")
      When("a repair result names an assigned path and contains meaningful findings")
      val actual = R.admitC(input, R.Result("repair", "Repair is needed", Vector(finding)), policy)
      Then("the result can select its semantic disposition without recertifying the whole input")
      take(actual) shouldBe ()

      Given("new findings outside the actual assignment or contradictory accept/repair dispositions")
      val invalid = Vector(R.Result("repair", "Repair is needed", Vector(finding.copy(affectedPaths = Vector("outside.scala")))),
        R.Result("accept", "Accepted", Vector(finding)), R.Result("repair", "Repair is needed", Vector.empty))
      When("the owning semantic consumer interprets those actual results")
      val rejected = invalid.map(R.admitC(input, _, policy))
      Then("foreign findings or contradictory result meaning cannot select semantic acceptance")
      rejected.foreach(_.isSuccess shouldBe false)
    }
  }

  "Native Start and downstream records" should {
    "store consumed Start context and actual fetch data without command copies" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R3; Example: E10; original Start and actual fetch facts")
      val context = Fetch.StartContext("original-selection", "original-invocation", "participant", "repository",
        "main", "origin", "main", "endpoint", "FULL_STATE")
      val started = Fetch.Payload("Started", Some(context), "/selected/repository", None, None, None, None)
      val startWire = Fetch.codec(true).encode(started)
      val fetched = Fetch.Payload("Fetched", None, "/selected/repository", Some(before), Some("c" * 40),
        Some(before.copy(mergeBase = Some("a" * 40))), Some("executed"))
      val fetchWire = Fetch.codec(false).encode(fetched)
      When("current Start and fetched output are written and read")
      val decodedStart = Fetch.codec(true).decode(startWire)
      val decodedFetch = Fetch.codec(false).decode(fetchWire)
      Then("only actual context/observations are retained, without commands or policy versions")
      decodedStart shouldBe Right(started)
      decodedFetch shouldBe Right(fetched)
      nested(startWire).asObject.get.keys.toSet should not contain "originalCommand"
      nested(fetchWire).asObject.get.keys.toSet should not contain "originalCommand"
      nested(startWire).hcursor.downField("context").focus.get.asObject.get.keys.toSet should not contain "repositoryPolicyVersion"

      Given("current rows annotated with unused command/authority fields and an invalid consumed participant type")
      val actualContext = nested(startWire).hcursor.downField("context").focus.get
      val annotated = nested(startWire).mapObject(_.add("originalCommand", Json.True)
        .add("context", actualContext.mapObject(_.add("authority", Json.Null))))
      When("the current typed codec reads those records")
      val accepted = Fetch.codec(true).decode(replace(startWire, annotated))
      val malformed = Fetch.codec(true).decode(replace(startWire, nested(startWire).mapObject(_.add("context",
        actualContext.mapObject(_.add("participantIdentity", Json.True))))))
      Then("unused fields are inert, but a wrong consumed type or old representation is unsupported")
      accepted shouldBe Right(started)
      malformed.isLeft shouldBe true
      Vector("v1", "v2").foreach(version => Fetch.codec(true).decode(startWire.mapObject(_.add("schemaVersion",
        Json.fromString("sm-workflow.repository-sync-managed-fetch." + version)))).isLeft shouldBe true)
    }
    "consume current progress and ignore unused fields without old-format readers" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R3; Example: E11; selected native progress facts")
      val saved = N.Payload("Preserving", "/selected/repository", before)
      val wire = N.codec.encode(saved)
      When("current v3 progress is written with inert annotations")
      val annotated = replace(wire, nested(wire).mapObject(_.add("originalCommand", Json.True).add("materialization", Json.False)))
      val decoded = N.codec.decode(annotated)
      Then("actual progress is read without retaining those old fields")
      decoded shouldBe Right(saved)
      nested(wire).asObject.get.keys.toSet should not contain "originalCommand"
      saved.productElementNames.toSet should not contain "originalCommand"

      Given("missing consumed progress data and unsupported old schemas")
      When("those records reach the typed current reader")
      val missing = N.codec.decode(replace(wire, nested(wire).mapObject(_.remove("before"))))
      Then("required typed observations fail and no old reader is selected")
      missing.isLeft shouldBe true
      Vector("v1", "v2").foreach(version => N.codec.decode(wire.mapObject(_.add("schemaVersion",
        Json.fromString("sm-workflow.repository-sync-native-progress." + version)))).isLeft shouldBe true)
    }
  }
}
