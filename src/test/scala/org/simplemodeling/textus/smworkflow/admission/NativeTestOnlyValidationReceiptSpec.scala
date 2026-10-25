package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.Path
import java.time.Instant
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import WorkflowSbtValidation.*
import RepositorySyncNativeMergeValidation as V
import RepositorySyncNativeProgressStore as N
import GoalPhaseNativeValidation as G
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as W
import RepositorySyncNativeMergeReview as R
import RepositorySyncNativeMergeRepair as M
import SplitPhaseExplicitPartitionFixture.take

/** Current-schema compatibility and actual selected facts, without runtime effects.
  * @since   Oct. 15, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeTestOnlyValidationReceiptSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _check = V.Check("focused", 1, Kind.TestOnly)
  private val _goal_check = G.Check("focused", 1, Kind.TestOnly)
  private val _snapshot = O.Snapshot("a" * 40, "b" * 40)
  private def _receipt(tests: Long = 3L, classes: Vector[String] = Vector("example.SelectedSpec"),
    selection: Boolean = true, kind: Kind = Kind.TestOnly): V.Receipt = {
    def capture(text: String) = {
      val bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
      ProcessExecutionCapture(bytes, bytes.size.toLong, false)
    }
    val operation = kind match {
      case Kind.Compile => SbtOperation.Compile
      case Kind.Test => SbtOperation.Test
      case Kind.TestOnly => SbtOperation.TestOnly
    }
    val result = SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC("sm-workflow-repository-validation-1"))),
      operation, ProcessExecutionResult(ProcessExecutionTermination.Exited(0), capture("actual output\n"),
        capture("diagnostic\n"), Vector.empty, 1L, "selected-sbt"),
      if (selection && kind == Kind.TestOnly) Some(SbtTestSelectionResult.Verified(classes, classes.size, tests)) else None)
    take(V.sbtReceiptC("focused", kind, result))
  }
  private val _actual = _receipt()
  private val _state = V.State("required", "Selected product check", Vector(_check), "8", Vector(_actual))
  private val _goal = G.Payload("8", "S1", _snapshot, Vector(_goal_check), Vector(_actual), Some("passed"))
  private val _observed = RepositorySyncManagedGitObservation.Observed("main", _snapshot.head, _snapshot.tree,
    Vector.empty, Some("c" * 40), Some(RepositorySyncManagedGitObservation.Counts(1, 1)))
  private val _progress = N.Payload("MergeValidated", "/selected/repository", _observed,
    indexTree = Some(_snapshot.tree), after = Some(_observed), mergeHead = Some("c" * 40), validation = Some(_state))
  private val _plan = O.Plan("selected-plan", "1", "PHASE-6", "repository",
    Vector(O.Step("S1", "Selected work", Vector("one.scala"), Vector.empty, Vector("focused"))), Vector("focused"))
  private val _work = W.Request("selected-instance", "9", "ReviewStep", Some("S1"), 1L, "Review selected work",
    _plan, _snapshot, Vector.empty, Some(_snapshot), Some("actual-checks"), None,
    Some(GoalPhaseNativeReview.Context(Vector("one.scala"), "actual diff", Vector(_actual))))
  private val _review = R.Request("selected-instance", "9", _snapshot.head, "c" * 40, _snapshot.tree,
    Vector("one.scala"), "actual local diff", "actual remote diff", "required", "Selected product check",
    Vector("focused"), Vector(_actual))
  private def _merge_repair(receipt: V.Receipt): M.Request = M.Request(_review.copy(checks = Vector(receipt)),
    Vector(R.Finding("selected-finding", Vector("one.scala"), "Repair the selected changed source")),
    Vector(M.File("one.scala", "selected source")))
  private def _merge_repair_wire(request: M.Request, receipt: Json): Json =
    M.inputCodec.encode(request).hcursor.downField("payload").downField("review").downField("checks")
      .downArray.withFocus(_ => receipt).top.get
  private def _host = {
    val original = ThreeWorkflowNormalHostFixture.definition(Path.of("/selected/repository"))
    original.copy(goal = original.goal.copy(stepValidation = original.goal.stepValidation.copy(checks = Vector(_goal_check)),
      phaseValidation = original.goal.phaseValidation.copy(checks = Vector(_goal_check))),
      repository = original.repository.copy(validation = original.repository.validation.copy(checks = Vector(_check))))
  }
  private def _fields(value: Json)(change: (String, Json) => Option[Json]): Json = value.arrayOrObject(value,
    items => Json.fromValues(items.map(item => _fields(item)(change))),
    fields => Json.obj(fields.toVector.flatMap { case (key, item) => change(key, _fields(item)(change)).map(key -> _) }*))
  private def _historical_receipts(value: Json): Json =
    if (value.hcursor.get[String]("schemaVersion").toOption.contains(WorkflowSbtCapture.schema))
      value.as[V.Receipt].toOption.get.copy(processObservation = None).asJson
    else value.arrayOrObject(value, items => Json.fromValues(items.map(_historical_receipts)),
      fields => Json.fromFields(fields.toVector.map((key, nested) => key -> _historical_receipts(nested))))
  private def _legacy(value: Json): Json = _fields(_historical_receipts(value))((key, item) => if (key == "kind" || key == "sbt") None else Some(item))
  private def _invalid_kind(value: Json, kind: Json): Json = _fields(value)((key, item) =>
    Some(if (key == "kind") kind else item))
  private def _invalid_sbt(value: Json, sbt: Json): Json = _fields(value)((key, item) =>
    Some(if (key == "sbt") sbt else item))
  private def _capture(text: String): ProcessExecutionCapture = {
    val bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
    ProcessExecutionCapture(bytes, bytes.size.toLong, false)
  }
  private def _result(exit: Int, stdout: ProcessExecutionCapture, stderr: ProcessExecutionCapture): SbtExecutionResult =
    SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC("sm-workflow-repository-validation-1"))), SbtOperation.Test,
      ProcessExecutionResult(ProcessExecutionTermination.Exited(exit), stdout, stderr, Vector.empty, 1L, "selected-sbt"), None)

  "Merge-repair enclosing receipt compatibility" should {
    "write the historical six-field receipt with null facts and roundtrip the actual repair request" in {
      Given("a specification-owned merge-repair request with a nested historical receipt and no SBT facts")
      val receipt = _actual.copy(sbt = None, processObservation = None)
      val request = _merge_repair(receipt)
      When("the actual merge-repair input codec writes and reopens its nested review checks")
      val wire = M.inputCodec.encode(request)
      val nested = wire.hcursor.downField("payload").downField("review").downField("checks").downArray.focus.get
      val reopened = M.inputCodec.decode(wire)
      Then("the exact old six fields include sbt:null and the complete request remains unchanged")
      nested shouldBe Json.obj("identity" -> receipt.identity.asJson, "termination" -> receipt.termination.asJson,
        "exitCode" -> receipt.exitCode.asJson, "stdout" -> receipt.stdout.asJson,
        "stderr" -> receipt.stderr.asJson, "sbt" -> Json.Null)
      reopened shouldBe Right(request)
    }

    "read historical absent or null optional facts and retain the old tolerance of unrelated fields" in {
      Given("the actual repair request and historical receipt shapes with absent or null optional members")
      val receipt = _actual.copy(exitCode = None, sbt = None, processObservation = None)
      val request = _merge_repair(receipt)
      val legacy = Json.obj("identity" -> receipt.identity.asJson, "termination" -> receipt.termination.asJson,
        "exitCode" -> Json.Null, "stdout" -> receipt.stdout.asJson, "stderr" -> receipt.stderr.asJson, "sbt" -> Json.Null)
      val variants = Vector(legacy, legacy.mapObject(_.remove("sbt")),
        legacy.mapObject(_.remove("sbt").remove("exitCode").add("inertDiagnostic", Json.True)))
      When("the merge-repair input codec reads these nested legacy checks")
      val reopened = variants.map(value => M.inputCodec.decode(_merge_repair_wire(request, value)))
      Then("null and absence keep None and unrelated legacy fields do not change the request")
      reopened shouldBe Vector.fill(variants.size)(Right(request))
    }

    "roundtrip historical typed facts through the actual nested review and repair request" in {
      Given("a specification-owned repair request whose historical receipt retains selected TestOnly facts")
      val receipt = _actual.copy(processObservation = None)
      val request = _merge_repair(receipt)
      When("the actual merge-repair input codec encodes and decodes the request")
      val wire = M.inputCodec.encode(request)
      val nested = wire.hcursor.downField("payload").downField("review").downField("checks").downArray.focus.get
      val reopened = M.inputCodec.decode(wire)
      Then("the exact old six-field shape and typed facts survive without a raw observation")
      nested shouldBe Json.obj("identity" -> receipt.identity.asJson, "termination" -> receipt.termination.asJson,
        "exitCode" -> receipt.exitCode.asJson, "stdout" -> receipt.stdout.asJson,
        "stderr" -> receipt.stderr.asJson, "sbt" -> receipt.sbt.asJson)
      reopened shouldBe Right(request)
    }

    "roundtrip the canonical tagged raw observation through the actual repair request" in {
      Given("a specification-owned repair request with a typed receipt containing shaped CNCF raw captures")
      val request = _merge_repair(_actual)
      When("the actual merge-repair input codec writes and reopens the nested raw receipt")
      val wire = M.inputCodec.encode(request)
      val nested = wire.hcursor.downField("payload").downField("review").downField("checks").downArray.focus.get
      val reopened = M.inputCodec.decode(wire)
      Then("canonical tagged bytes and the complete ProcessExecutionResult survive exactly without executing a process")
      nested shouldBe _actual.asJson
      nested.hcursor.get[String]("schemaVersion") shouldBe Right(WorkflowSbtCapture.schema)
      reopened shouldBe Right(request)
      reopened.toOption.get.review.checks.head.processObservation shouldBe _actual.processObservation
    }

    "refuse malformed unknown and present-null tags without falling back to the valid legacy fields" in {
      Given("a valid historical nested repair receipt with a present tag and malformed new raw observations")
      val receipt = _actual.copy(processObservation = None)
      val request = _merge_repair(receipt)
      val legacy = receipt.asJson
      val tagged = Vector(Json.Null, Json.True, Json.fromString("unknown-receipt-schema"),
        Json.fromString(WorkflowSbtCapture.schema)).map(tag => legacy.mapObject(_.add("schemaVersion", tag)))
      val malformed = Vector(_actual.asJson.mapObject(_.add("process", Json.Null)),
        _actual.asJson.mapObject(_.add("unrecognized", Json.True)))
      When("the actual merge-repair input codec consumes each tagged nested check")
      val rejected = (tagged ++ malformed).map(value => M.inputCodec.decode(_merge_repair_wire(request, value)).isLeft)
      Then("every present malformed or unknown tag fails instead of reopening through the legacy reader")
      rejected shouldBe Vector.fill(tagged.size + malformed.size)(true)
    }
  }

  "Complete typed SBT output" should {
    "retain large shaped successful and nonzero facts through Goal Repository and review codecs without another text ceiling" in {
      Given("shaped managed Test captures with multibyte stdout, near-two-MiB stdout, and large stderr; these perform no execution")
      val outputs = Vector(("日本語" * 8000, "diagnostic\n"), ("日" * 699000, "diagnostic\n"),
        ("actual output\n", "error " * 12000))
      val goalcheck = _goal_check.copy(kind = Kind.Test)
      val repositorycheck = _check.copy(kind = Kind.Test)
      val structural = V.Receipt("index-local", "exited", Some(0), "", "")
      for ((stdout, stderr) <- outputs; exit <- Vector(0, 7)) {
        When("the complete typed result with actual-shaped exit " + exit + " is admitted and each current codec reopens it")
        val receipt = take(V.sbtReceiptC("focused", Kind.Test, _result(exit, _capture(stdout), _capture(stderr))))
        val goal = _goal.copy(selected = Vector(goalcheck), receipts = Vector(receipt),
          outcome = Some(if (exit == 0) "passed" else "failed"))
        val state = _state.copy(selected = Vector(repositorycheck), receipts = Vector(receipt))
        val progress = _progress.copy(validation = Some(state))
        val work = _work.copy(review = _work.review.map(_.copy(checks = Vector(receipt))))
        val review = _review.copy(checks = Vector(receipt))
        val reopened = G.codec.decode(G.codec.encode(goal))
        Then("exact complete output and exit survive while only zero succeeds and nonzero remains a typed failed repair source")
        receipt.stdout shouldBe stdout
        receipt.stderr shouldBe stderr
        receipt.exitCode shouldBe Some(exit)
        receipt.sbt shouldBe Some(Facts(Kind.Test, if (exit == 0) Outcome.Succeeded else Outcome.Failed, None, None))
        reopened shouldBe Right(goal)
        V.stateCodec.decode(V.stateCodec.encode(state)) shouldBe Right(state)
        N.codec.decode(N.codec.encode(progress)) shouldBe Right(progress)
        W.inputCodec.decode(W.inputCodec.encode(work)) shouldBe Right(work)
        R.inputCodec.decode(R.inputCodec.encode(review)) shouldBe Right(review)
        V.productSuccessful(receipt) shouldBe (exit == 0)
        G.successful(reopened.toOption.get.receipts, Vector(goalcheck)) shouldBe (exit == 0)
        V.successful(Vector(structural, receipt), merge = false, selected = Vector(repositorycheck)) shouldBe (exit == 0)
        G.repairable(reopened.toOption.get) shouldBe (exit != 0)
      }
    }

    "refuse incomplete mismatched negative-count invalid UTF-8 and NUL captures before retaining typed output" in {
      Given("complete actual-shaped typed Test results and independently malformed stdout or stderr captures")
      val ordinary = _capture("actual output\n")
      val malformed = Vector(ordinary.copy(truncated = true), ordinary.copy(byteCount = ordinary.byteCount + 1),
        ordinary.copy(byteCount = -1L), ProcessExecutionCapture(Vector(0xc3.toByte, 0x28.toByte), 2L, false), _capture("bad\u0000text"))
      When("both zero and nonzero typed results try to admit each malformed stream")
      val rejected = for (exit <- Vector(0, 7); capture <- malformed; result <- Vector(
        _result(exit, capture, ordinary), _result(exit, ordinary, capture)))
        yield V.sbtReceiptC("focused", Kind.Test, result).isSuccess
      Then("no malformed capture is promoted to a passed or failed receipt and selected identity and kind remain mandatory")
      rejected shouldBe Vector.fill(rejected.size)(false)
      V.sbtReceiptC("invalid identity", Kind.Test, _result(0, ordinary, ordinary)).isSuccess shouldBe false
      V.sbtReceiptC("focused", Kind.Compile, _result(0, ordinary, ordinary)).isSuccess shouldBe false

      Given("stored typed output with a NUL or an unpaired UTF-16 surrogate")
      val invalidtext = Vector("bad\u0000text", "bad\ud800text")
      When("the product gate and Goal payload decoder inspect either stored stream")
      val receipts = invalidtext.flatMap(text => Vector(_receipt(kind = Kind.Test).copy(stdout = text, processObservation = None),
        _receipt(kind = Kind.Test).copy(stderr = text, processObservation = None)))
      Then("typed presence cannot bypass strict ordinary text validation after persistence")
      receipts.foreach { receipt =>
        V.productSuccessful(receipt) shouldBe false
        G.codec.decode(G.codec.encode(_goal.copy(selected = Vector(_goal_check.copy(kind = Kind.Test)),
          receipts = Vector(receipt)))).isLeft shouldBe true
      }
    }

    "keep the exact 64-KiB structural and historical output bound and the Goal diagnostic bound" in {
      Given("historical Test receipts, structural captures and recovery diagnostics at and above 65536 UTF-8 bytes")
      val boundary = "x" * 65536
      val over = boundary + "x"
      val legacy = _receipt(kind = Kind.Test).copy(sbt = None, stdout = boundary, processObservation = None)
      val goal = _goal.copy(selected = Vector(_goal_check.copy(kind = Kind.Test)), receipts = Vector(legacy))
      When("the existing structural converter, product gate and Goal decoder consume those texts")
      val structural = V.receiptC("index-local", _result(0, _capture(boundary), _capture("")).processResult)
      Then("the historical and structural boundary remains inclusive with no typed promotion or diagnostic expansion")
      structural.isSuccess shouldBe true
      V.receiptC("index-local", _result(0, _capture(over), _capture("")).processResult).isSuccess shouldBe false
      V.receiptC("index-local", _result(0, _capture(""), _capture(over)).processResult).isSuccess shouldBe false
      V.productSuccessful(legacy) shouldBe true
      G.codec.decode(G.codec.encode(goal)) shouldBe Right(goal)
      Vector(legacy.copy(stdout = over), legacy.copy(stderr = over), legacy.copy(stdout = "日" * 21846)).foreach { receipt =>
        V.productSuccessful(receipt) shouldBe false
        G.codec.decode(G.codec.encode(goal.copy(receipts = Vector(receipt)))).isLeft shouldBe true
      }
      val diagnostic = _goal.copy(receipts = Vector.empty, outcome = None,
        uncertainties = Vector(G.Uncertainty("focused", 0, boundary)))
      G.codec.decode(G.codec.encode(diagnostic)) shouldBe Right(diagnostic)
      G.codec.decode(G.codec.encode(diagnostic.copy(uncertainties = Vector(G.Uncertainty("focused", 0, over))))).isLeft shouldBe true
    }
  }

  "Current typed validation codecs" should {
    "roundtrip selected Compile and actual Compile facts through every current persistence review and host boundary" in {
      Given("a selected Compile check and actual typed Compile receipt in the existing Goal Repository and host fixtures")
      val compilecheck = _check.copy(kind = Kind.Compile)
      val compilegoalcheck = _goal_check.copy(kind = Kind.Compile)
      val receipt = _receipt(kind = Kind.Compile)
      val compilestate = _state.copy(selected = Vector(compilecheck), receipts = Vector(receipt))
      val compilegoal = _goal.copy(selected = Vector(compilegoalcheck), receipts = Vector(receipt))
      val compileprogress = _progress.copy(validation = Some(compilestate))
      val compilework = _work.copy(review = _work.review.map(_.copy(checks = Vector(receipt))))
      val compilereview = _review.copy(checks = Vector(receipt))
      val originalhost = _host
      val compilehost = originalhost.copy(goal = originalhost.goal.copy(
        stepValidation = originalhost.goal.stepValidation.copy(checks = Vector(compilegoalcheck)),
        phaseValidation = originalhost.goal.phaseValidation.copy(checks = Vector(compilegoalcheck))),
        repository = originalhost.repository.copy(validation = originalhost.repository.validation.copy(checks = Vector(compilecheck))))
      val structural = V.Receipt("index-local", "exited", Some(0), "", "")
      When("the existing codecs encode and reopen each Compile selection and receipt")
      val goal = G.codec.decode(G.codec.encode(compilegoal))
      val state = V.stateCodec.decode(V.stateCodec.encode(compilestate))
      val progress = N.codec.decode(N.codec.encode(compileprogress))
      val work = W.inputCodec.decode(W.inputCodec.encode(compilework))
      val review = R.inputCodec.decode(R.inputCodec.encode(compilereview))
      val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(ThreeWorkflowRuntimeParameters.encode(compilehost)))
      Then("explicit Compile selections raw output and facts survive without gaining selected-test metadata")
      goal shouldBe Right(compilegoal)
      state shouldBe Right(compilestate)
      progress shouldBe Right(compileprogress)
      work shouldBe Right(compilework)
      review shouldBe Right(compilereview)
      host shouldBe compilehost
      compilecheck.asJson.hcursor.get[String]("kind") shouldBe Right("compile")
      compilegoalcheck.asJson.hcursor.get[String]("kind") shouldBe Right("compile")
      receipt.sbt shouldBe Some(Facts(Kind.Compile, Outcome.Succeeded, None, None))
      state.toOption.get.receipts.head.stdout shouldBe "actual output\n"
      state.toOption.get.receipts.head.stderr shouldBe "diagnostic\n"
      G.successful(goal.toOption.get.receipts, goal.toOption.get.selected) shouldBe true
      V.successful(Vector(structural, state.toOption.get.receipts.head), merge = false,
        selected = state.toOption.get.selected) shouldBe true

      Given("the same Compile selections with a historical raw Test receipt lacking typed facts")
      val legacy = _receipt(kind = Kind.Test).copy(sbt = None, processObservation = None)
      When("both workflow gates compare those legacy facts with the selected Compile checks")
      val goallegacy = G.successful(Vector(legacy), Vector(compilegoalcheck))
      val repositorylegacy = V.successful(Vector(structural, legacy), merge = false, selected = Vector(compilecheck))
      Then("historical absence can satisfy only Test and cannot satisfy either Compile workflow")
      V.productSuccessful(legacy, Kind.Test) shouldBe true
      V.productSuccessful(legacy, Kind.Compile) shouldBe false
      goallegacy shouldBe false
      repositorylegacy shouldBe false
    }

    "roundtrip TestOnly facts through all five nested persistence and review boundaries and the actual host definition" in {
      Given("actual typed TestOnly receipts and selected Check kind in current Goal, Repository and host payloads")
      When("each existing current-schema codec encodes and decodes its own nested facts")
      val goal = G.codec.decode(G.codec.encode(_goal))
      val state = V.stateCodec.decode(V.stateCodec.encode(_state))
      val progress = N.codec.decode(N.codec.encode(_progress))
      val work = W.inputCodec.decode(W.inputCodec.encode(_work))
      val review = R.inputCodec.decode(R.inputCodec.encode(_review))
      val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(ThreeWorkflowRuntimeParameters.encode(_host)))
      Then("kind, classes, count, actual raw output and original source and selected binding survive unchanged")
      goal shouldBe Right(_goal)
      state shouldBe Right(_state)
      progress shouldBe Right(_progress)
      work shouldBe Right(_work)
      review shouldBe Right(_review)
      host shouldBe _host
      state.toOption.get.selected shouldBe Vector(_check)
      goal.toOption.get.sourceRevision shouldBe "8"
      goal.toOption.get.selected shouldBe Vector(_goal_check)
      work.toOption.get.review.get.checks shouldBe Vector(_actual)
      review.toOption.get.checks shouldBe Vector(_actual)
    }

    "read absent kind and sbt as historical Test facts through every current nested boundary without promotion" in {
      Given("current records with pre-extension Check and five-field Receipt shapes")
      val goalwire = _legacy(G.codec.encode(_goal))
      val statewire = _legacy(V.stateCodec.encode(_state))
      val progresswire = _legacy(N.codec.encode(_progress))
      // Work kind describes assigned semantics and is not a Check kind.
      val workwire = _fields(_historical_receipts(W.inputCodec.encode(_work)))((key, item) => if (key == "sbt") None else Some(item))
      val reviewwire = _legacy(R.inputCodec.encode(_review))
      When("the current-only codecs reopen the old absence shapes as data")
      val goal = G.codec.decode(goalwire).toOption.get
      val state = V.stateCodec.decode(statewire).toOption.get
      val progress = N.codec.decode(progresswire).toOption.get
      val work = W.inputCodec.decode(workwire).toOption.get
      val review = R.inputCodec.decode(reviewwire).toOption.get
      val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(_legacy(ThreeWorkflowRuntimeParameters.encode(_host))))
      Then("only historical Test meaning is assigned; no receipt gains selected-suite success or a new execution")
      goal shouldBe _goal.copy(selected = Vector(_goal_check.copy(kind = Kind.Test)), receipts = Vector(_actual.copy(sbt = None, processObservation = None)))
      state shouldBe _state.copy(selected = Vector(_check.copy(kind = Kind.Test)), receipts = Vector(_actual.copy(sbt = None, processObservation = None)))
      progress.validation shouldBe Some(state)
      work.review.get.checks shouldBe Vector(_actual.copy(sbt = None, processObservation = None))
      review.checks shouldBe Vector(_actual.copy(sbt = None, processObservation = None))
      host.goal.stepValidation.checks.head.kind shouldBe Kind.Test
      host.repository.validation.checks.head.kind shouldBe Kind.Test
      V.productSuccessful(state.receipts.head, Kind.Test) shouldBe true
      V.productSuccessful(state.receipts.head, Kind.TestOnly) shouldBe false
      G.successful(goal.receipts, _goal.selected) shouldBe false
      G.codec.encode(goal) shouldBe goalwire
      V.stateCodec.encode(state) shouldBe statewire
    }

    "reject present null Boolean number and unknown Check kinds at every selected host and payload boundary" in {
      Given("current records whose explicit Check kind is present but malformed")
      val invalid = Vector(Json.Null, Json.True, Json.fromInt(1), Json.fromString("unsupported-operation"))
      When("the explicit Check companion codecs are consumed by Goal, Repository and actual host codecs")
      val rejected = invalid.map { kind => Vector(
        G.codec.decode(_invalid_kind(G.codec.encode(_goal), kind)).isLeft,
        V.stateCodec.decode(_invalid_kind(V.stateCodec.encode(_state), kind)).isLeft,
        N.codec.decode(_invalid_kind(N.codec.encode(_progress), kind)).isLeft,
        !ThreeWorkflowRuntimeParameters.decodeDefinitionC(_invalid_kind(ThreeWorkflowRuntimeParameters.encode(_host), kind)).isSuccess) }
      Then("every malformed present kind fails instead of obtaining the absent-field Test default")
      rejected.flatten.forall(identity) shouldBe true
      G.Policy("selected", 1, Vector(_goal_check.copy(kind = null))).validateC.isSuccess shouldBe false
      V.Policy("selected", 1, "required", "Selected", Vector(_check.copy(kind = null))).validateC.isSuccess shouldBe false
    }

    "reject malformed SBT facts and extra new raw-receipt fields through every nested receipt codec" in {
      Given("typed current receipts with either malformed sbt or unused diagnostic annotations")
      val invalid = Vector(Json.Null, Json.True, Json.obj(), _actual.sbt.get.asJson.mapObject(_.add("outcome", Json.fromString("invented"))))
      val annotation: (String, Json) => Option[Json] = (key, item) => Some(item.mapObject(_.add("command", Json.True).add("authority", Json.Null)))
      When("the five nested consumers decode the actual receipt field and ignore unrelated diagnostics")
      val rejected = invalid.map { sbt => Vector(
        G.codec.decode(_invalid_sbt(G.codec.encode(_goal), sbt)).isLeft,
        V.stateCodec.decode(_invalid_sbt(V.stateCodec.encode(_state), sbt)).isLeft,
        N.codec.decode(_invalid_sbt(N.codec.encode(_progress), sbt)).isLeft,
        W.inputCodec.decode(_invalid_sbt(W.inputCodec.encode(_work), sbt)).isLeft,
        R.inputCodec.decode(_invalid_sbt(R.inputCodec.encode(_review), sbt)).isLeft) }
      Then("malformed consumed data and unknown closed raw fields fail without legacy fallback")
      rejected.flatten.forall(identity) shouldBe true
      G.codec.decode(_fields(G.codec.encode(_goal))(annotation)).isLeft shouldBe true
      V.stateCodec.decode(_fields(V.stateCodec.encode(_state))(annotation)).isLeft shouldBe true
      N.codec.decode(_fields(N.codec.encode(_progress))(annotation)).isLeft shouldBe true
      W.inputCodec.decode(_fields(W.inputCodec.encode(_work))(annotation)).isLeft shouldBe true
      R.inputCodec.decode(_fields(R.inputCodec.encode(_review))(annotation)).isLeft shouldBe true
      take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(_fields(ThreeWorkflowRuntimeParameters.encode(_host))(annotation))) shouldBe _host
      G.codec.decode(G.codec.encode(_goal).mapObject(_.add("schemaVersion", Json.fromString("sm-workflow.goal-phase-native-validation.v1")))).isLeft shouldBe true
      N.codec.decode(N.codec.encode(_progress).mapObject(_.add("schemaVersion", Json.fromString("sm-workflow.repository-sync-native-progress.v2")))).isLeft shouldBe true
    }
  }

  "Selected product success" should {
    "consume exact ordered identity and requested kind and retain raw-zero typed negatives" in {
      Given("selected TestOnly checks, an actual positive receipt and a raw-zero unverified receipt")
      val structural = V.Receipt("index-local", "exited", Some(0), "", "")
      val negative = _receipt(selection = false)
      When("the Goal and Repository pure gates consume the selected receipt prefix")
      val positive = V.successful(Vector(structural, _actual), merge = false, selected = Vector(_check))
      val failed = V.successful(Vector(structural, negative), merge = false, selected = Vector(_check))
      Then("only exact positive selected facts pass; order, identity, kind and historical absence cannot be recast")
      positive shouldBe true
      failed shouldBe false
      G.successful(Vector(_actual), Vector(_goal_check)) shouldBe true
      G.successful(Vector(negative), Vector(_goal_check)) shouldBe false
      G.successful(Vector(_actual), Vector(_goal_check.copy(identity = "different"))) shouldBe false
      G.successful(Vector(_actual), Vector(_goal_check.copy(kind = Kind.Test))) shouldBe false
      G.successful(Vector(_actual, _actual), Vector(_goal_check)) shouldBe false
      V.successful(Vector(structural, _actual.copy(identity = "different")), merge = false, selected = Vector(_check)) shouldBe false
      V.successful(Vector(structural, _actual.copy(sbt = None, processObservation = None)), merge = false, selected = Vector(_check)) shouldBe false
      R.inputCodec.decode(R.inputCodec.encode(_review.copy(checks = Vector(negative)))).toOption.get.checks.head shouldBe negative
      negative.exitCode shouldBe Some(0)
      negative.sbt.get.outcome shouldBe Outcome.Unverified
    }
  }

  "Independent review targets" should {
    "retain selected SBT facts through canonical storage and refuse crossed actual Goal and Repository observations" in {
      Given("explicit specification evidence bindings for Goal and Repository with actual typed TestOnly receipts")
      import NativeExternalEvidence.{Declaration, Evidence, Purpose}
      val now = Instant.parse("2026-10-07T00:00:00Z")
      val declared = Declaration("workflow", DataStore.CollectionId.EntityStore(
        EntityCollectionId("test", "host", "review_evidence")), "specification-issuer")
      val reference = Json.obj("identity" -> Json.fromString("specification-result"), "revision" -> Json.fromString("1"))
      val lookup = ThreeWorkflowExternalEvidencePorts.key("specification-result", "1")
      val completion = Json.obj("handle" -> Json.obj(
        "componentIdentity" -> Json.fromString("specification-component"),
        "workflowIdentity" -> Json.fromString("specification-workflow"),
        "workflowRevision" -> Json.fromString("1"), "instanceIdentity" -> Json.fromString("selected-instance"),
        "protocolVersion" -> Json.fromString("1")), "runId" -> Json.fromString("specification-run"),
        "continuationId" -> Json.fromString("specification-review"), "typeIdentity" -> Json.fromString("specification-result-type"))
      val variants = Vector(_receipt(tests = 4L), _receipt(classes = Vector("example.OtherSpec")),
        _receipt(selection = false), _actual.copy(sbt = Some(Facts(Kind.Test, Outcome.Succeeded, None, None))))
      Vector(Purpose.GoalReview, Purpose.RepositoryReview).foreach { purpose =>
        val checkskey = if (purpose == Purpose.GoalReview) "testChecks" else "checks"
        def _target_(receipt: V.Receipt): Json = if (purpose == Purpose.GoalReview)
          ThreeWorkflowExternalEvidencePorts.goalReviewTarget(_work.copy(review = _work.review.map(_.copy(checks = Vector(receipt)))))
        else ThreeWorkflowExternalEvidencePorts.repositoryReviewTarget(_review.copy(checks = Vector(receipt)))
        def _binding_(selected: Json): Json = ThreeWorkflowExternalEvidencePorts.reviewBinding("specification-owner",
          if (purpose == Purpose.GoalReview) "goal-review" else "repository-review", selected, completion,
          reference, Json.fromString("specification-reviewer"), if (purpose == Purpose.GoalReview)
            ThreeWorkflowExternalEvidencePorts.goalReviewVerdict(W.Result("accept", "diagnostic", Vector.empty))
          else ThreeWorkflowExternalEvidencePorts.repositoryReviewVerdict(R.Result("accept", "diagnostic", Vector.empty)))
        val actual = _binding_(_target_(_actual))
        val evidence = Evidence(NativeExternalEvidence.schema, purpose.identity, lookup, "specification-result", "1",
          declared.issuer, "specification-reviewer", actual, now.toString)
        When("the real v7 encoder and decoder select the target and observation compares its actual binding for " + purpose)
        val raw = take(NativeExternalEvidence.encodeStorageC(evidence))
        val decoded = take(NativeExternalEvidence.decodeStorageC(raw))
        val stored = io.circe.parser.parse(raw.substring(NativeExternalEvidence.schema.length + 1)).toOption.get
          .hcursor.downField("binding").downField("attestation").downField("target")
          .downField(checkskey).downArray.downField("sbt")
        Then("explicit kind outcome classes and counts survive storage and the matching actual binding is admitted")
        stored.get[String]("kind") shouldBe Right("test-only")
        stored.get[String]("outcome") shouldBe Right("succeeded")
        stored.downField("selection").get[Vector[String]]("testClasses") shouldBe Right(Vector("example.SelectedSpec"))
        stored.downField("selection").get[Int]("completedSuites") shouldBe Right(1)
        stored.downField("selection").get[Long]("testsRun") shouldBe Right(3L)
        NativeExternalEvidence.observeC(decoded, declared, purpose, lookup, actual, now).isSuccess shouldBe true
        variants.foreach { receipt =>
          Given("the same issued review with a valid changed class count outcome or kind for " + purpose)
          When("the original stored evidence is observed against those changed selected facts")
          val crossed = NativeExternalEvidence.observeC(decoded, declared, purpose, lookup, _binding_(_target_(receipt)), now)
          Then("that changed selected meaning cannot reuse the original evidence")
          crossed.isSuccess shouldBe false
        }
        Given("the historical target without sbt and a typed target annotated with unused logs")
        val historical = _binding_(_target_(_actual.copy(sbt = None, processObservation = None)))
        When("historical storage is reopened and typed evidence is compared with stripped or annotated targets")
        val legacy = take(NativeExternalEvidence.decodeStorageC(take(NativeExternalEvidence.encodeStorageC(evidence.copy(binding = historical)))))
        val annotated = _binding_(_target_(_actual).mapObject(_.add(checkskey, Json.arr(
          _target_(_actual).hcursor.downField(checkskey).downArray.focus.get.mapObject(
            _.add("stdout", Json.fromString("inert log")).add("grant", Json.True))))))
        Then("absence retains its exact historical shape and cannot stand for typed facts while annotations remain inert")
        NativeExternalEvidence.observeC(decoded, declared, purpose, lookup, historical, now).isSuccess shouldBe false
        NativeExternalEvidence.observeC(legacy, declared, purpose, lookup, actual, now).isSuccess shouldBe false
        NativeExternalEvidence.observeC(decoded, declared, purpose, lookup, annotated, now).isSuccess shouldBe true
        legacy.binding shouldBe historical
        if (purpose == Purpose.GoalReview)
          legacy.binding.hcursor.downField("attestation").downField("target").downField("testChecks").focus shouldBe None
        else legacy.binding.hcursor.downField("attestation").downField("target").downField("checks").downArray.focus.get
          .asObject.get.keys.toSet shouldBe Set("identity", "termination", "exitCode")
        Vector(Json.Null, Json.True, Json.obj(), _actual.sbt.get.asJson.mapObject(_.add("kind", Json.fromString("unknown"))))
          .foreach { malformed =>
            Given("present malformed sbt in a selected " + purpose + " review check")
            val invalid = _binding_(_target_(_actual).mapObject(_.add(checkskey, Json.arr(
              _target_(_actual).hcursor.downField(checkskey).downArray.focus.get.mapObject(_.add("sbt", malformed))))))
            When("the real storage encoder or decoder consumes that malformed selected fact")
            val invalidraw = io.circe.parser.parse(raw.substring(NativeExternalEvidence.schema.length + 1)).toOption.get
              .mapObject(_.add("binding", invalid))
            Then("both storage directions reject it instead of treating presence as historical absence")
            NativeExternalEvidence.encodeStorageC(evidence.copy(binding = invalid)).isSuccess shouldBe false
            NativeExternalEvidence.decodeStorageC(NativeExternalEvidence.schema + ":" + invalidraw.noSpaces).isSuccess shouldBe false
          }
      }
    }

    "bind actual TestOnly class count and outcome while preserving historical no-facts target shape" in {
      Given("the same assigned review with actual TestOnly variants and historical raw receipts")
      val variants = Vector(_actual, _receipt(tests = 4L), _receipt(classes = Vector("example.OtherSpec")), _receipt(selection = false))
      val oldwork = _work.copy(review = _work.review.map(_.copy(checks = Vector(_actual.copy(sbt = None, processObservation = None)))))
      val oldreview = _review.copy(checks = Vector(_actual.copy(sbt = None, processObservation = None)))
      When("host evidence projects the semantic target of each actual assigned review")
      val goals = variants.map(receipt => ThreeWorkflowExternalEvidencePorts.goalReviewTarget(
        _work.copy(review = _work.review.map(_.copy(checks = Vector(receipt))))))
      val repositories = variants.map(receipt => ThreeWorkflowExternalEvidencePorts.repositoryReviewTarget(_review.copy(checks = Vector(receipt))))
      val oldgoal = ThreeWorkflowExternalEvidencePorts.goalReviewTarget(oldwork)
      val oldrepository = ThreeWorkflowExternalEvidencePorts.repositoryReviewTarget(oldreview)
      Then("actual selected facts distinguish targets without copying raw logs or changing old target fields")
      goals.distinct.size shouldBe variants.size
      repositories.distinct.size shouldBe variants.size
      goals.foreach { target =>
        target.hcursor.downField("testChecks").focus.get.asArray.get.head.hcursor.downField("sbt").focus.nonEmpty shouldBe true
        target.noSpaces should not include "actual output"
        target.noSpaces should not include "diagnostic"
      }
      oldgoal.asObject.get.keys.toSet shouldBe Set("instance", "sourceRevision", "workKind", "step", "generation", "plan", "base", "candidate", "checksReceipt", "changedPaths")
      oldrepository.hcursor.downField("checks").focus.get.asArray.get.head.asObject.get.keys.toSet shouldBe Set("identity", "termination", "exitCode")
      ThreeWorkflowExternalEvidencePorts.repositoryReviewTarget(_review.copy(checks = Vector(_actual.copy(stdout = "changed log", stderr = "changed error")))) shouldBe repositories.head
    }
  }
}
