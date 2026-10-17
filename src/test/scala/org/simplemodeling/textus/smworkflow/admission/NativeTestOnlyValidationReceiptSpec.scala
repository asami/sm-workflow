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
import SplitPhaseExplicitPartitionFixture.take

/** Current-schema compatibility and actual selected facts, without runtime effects.
  * @since   Oct. 15, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
final class NativeTestOnlyValidationReceiptSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _check = V.Check("focused", 1, Kind.TestOnly)
  private val _goal_check = G.Check("focused", 1, Kind.TestOnly)
  private val _snapshot = O.Snapshot("a" * 40, "b" * 40)
  private def _receipt(tests: Long = 3L, classes: Vector[String] = Vector("example.SelectedSpec"),
    selection: Boolean = true): V.Receipt = {
    def capture(text: String) = {
      val bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
      ProcessExecutionCapture(bytes, bytes.size.toLong, false)
    }
    val result = SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC("sm-workflow-repository-validation-1"))),
      SbtOperation.TestOnly, ProcessExecutionResult(ProcessExecutionTermination.Exited(0), capture("actual output\n"),
        capture("diagnostic\n"), Vector.empty, 1L, "selected-sbt"),
      if (selection) Some(SbtTestSelectionResult.Verified(classes, classes.size, tests)) else None)
    take(V.sbtReceiptC("focused", Kind.TestOnly, result))
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
  private def _host = {
    val original = ThreeWorkflowNormalHostFixture.definition(Path.of("/selected/repository"))
    original.copy(goal = original.goal.copy(stepValidation = original.goal.stepValidation.copy(checks = Vector(_goal_check)),
      phaseValidation = original.goal.phaseValidation.copy(checks = Vector(_goal_check))),
      repository = original.repository.copy(validation = original.repository.validation.copy(checks = Vector(_check))))
  }
  private def _fields(value: Json)(change: (String, Json) => Option[Json]): Json = value.arrayOrObject(value,
    items => Json.fromValues(items.map(item => _fields(item)(change))),
    fields => Json.obj(fields.toVector.flatMap { case (key, item) => change(key, _fields(item)(change)).map(key -> _) }*))
  private def _legacy(value: Json): Json = _fields(value)((key, item) => if (key == "kind" || key == "sbt") None else Some(item))
  private def _invalid_kind(value: Json, kind: Json): Json = _fields(value)((key, item) =>
    Some(if (key == "kind") kind else item))
  private def _invalid_sbt(value: Json, sbt: Json): Json = _fields(value)((key, item) =>
    Some(if (key == "sbt") sbt else item))

  "Current typed validation codecs" should {
    "roundtrip TestOnly facts through all five nested persistence and review boundaries and the actual host definition" in {
      Given("actual typed TestOnly receipts and selected Check kind in current Goal, Repository and host payloads")
      When("each existing current-schema codec encodes and decodes its own nested facts")
      val goal = G.codec.decode(G.codec.encode(_goal))
      val state = V.stateCodec.decode(V.stateCodec.encode(_state))
      val progress = N.codec.decode(N.codec.encode(_progress))
      val work = W.inputCodec.decode(W.inputCodec.encode(_work))
      val review = R.inputCodec.decode(R.inputCodec.encode(_review))
      val host = take(ThreeWorkflowHostParameters.decodeDefinitionC(ThreeWorkflowHostParameters.encode(_host)))
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
      val workwire = _fields(W.inputCodec.encode(_work))((key, item) => if (key == "sbt") None else Some(item))
      val reviewwire = _legacy(R.inputCodec.encode(_review))
      When("the current-only codecs reopen the old absence shapes as data")
      val goal = G.codec.decode(goalwire).toOption.get
      val state = V.stateCodec.decode(statewire).toOption.get
      val progress = N.codec.decode(progresswire).toOption.get
      val work = W.inputCodec.decode(workwire).toOption.get
      val review = R.inputCodec.decode(reviewwire).toOption.get
      val host = take(ThreeWorkflowHostParameters.decodeDefinitionC(_legacy(ThreeWorkflowHostParameters.encode(_host))))
      Then("only historical Test meaning is assigned; no receipt gains selected-suite success or a new execution")
      goal shouldBe _goal.copy(selected = Vector(_goal_check.copy(kind = Kind.Test)), receipts = Vector(_actual.copy(sbt = None)))
      state shouldBe _state.copy(selected = Vector(_check.copy(kind = Kind.Test)), receipts = Vector(_actual.copy(sbt = None)))
      progress.validation shouldBe Some(state)
      work.review.get.checks shouldBe Vector(_actual.copy(sbt = None))
      review.checks shouldBe Vector(_actual.copy(sbt = None))
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
      val invalid = Vector(Json.Null, Json.True, Json.fromInt(1), Json.fromString("compile"))
      When("the explicit Check companion codecs are consumed by Goal, Repository and actual host codecs")
      val rejected = invalid.map { kind => Vector(
        G.codec.decode(_invalid_kind(G.codec.encode(_goal), kind)).isLeft,
        V.stateCodec.decode(_invalid_kind(V.stateCodec.encode(_state), kind)).isLeft,
        N.codec.decode(_invalid_kind(N.codec.encode(_progress), kind)).isLeft,
        !ThreeWorkflowHostParameters.decodeDefinitionC(_invalid_kind(ThreeWorkflowHostParameters.encode(_host), kind)).isSuccess) }
      Then("every malformed present kind fails instead of obtaining the absent-field Test default")
      rejected.flatten.forall(identity) shouldBe true
      G.Policy("selected", 1, Vector(_goal_check.copy(kind = null))).validateC.isSuccess shouldBe false
      V.Policy("selected", 1, "required", "Selected", Vector(_check.copy(kind = null))).validateC.isSuccess shouldBe false
    }

    "reject malformed present sbt through every nested receipt codec and retain inert annotations" in {
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
      Then("malformed consumed data fails and inert fields change no typed business fact")
      rejected.flatten.forall(identity) shouldBe true
      G.codec.decode(_fields(G.codec.encode(_goal))(annotation)) shouldBe Right(_goal)
      V.stateCodec.decode(_fields(V.stateCodec.encode(_state))(annotation)) shouldBe Right(_state)
      N.codec.decode(_fields(N.codec.encode(_progress))(annotation)) shouldBe Right(_progress)
      W.inputCodec.decode(_fields(W.inputCodec.encode(_work))(annotation)) shouldBe Right(_work)
      R.inputCodec.decode(_fields(R.inputCodec.encode(_review))(annotation)) shouldBe Right(_review)
      take(ThreeWorkflowHostParameters.decodeDefinitionC(_fields(ThreeWorkflowHostParameters.encode(_host))(annotation))) shouldBe _host
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
      V.successful(Vector(structural, _actual.copy(sbt = None)), merge = false, selected = Vector(_check)) shouldBe false
      R.inputCodec.decode(R.inputCodec.encode(_review.copy(checks = Vector(negative)))).toOption.get.checks.head shouldBe negative
      negative.exitCode shouldBe Some(0)
      negative.sbt.get.outcome shouldBe Outcome.Unverified
    }
  }

  "Independent review targets" should {
    "retain selected SBT facts through canonical storage and refuse crossed actual Goal and Repository observations" in {
      Given("explicit specification evidence bindings for Goal and Repository with actual typed TestOnly receipts")
      import NativeHostEvidence.{Declaration, Evidence, Purpose}
      val now = Instant.parse("2026-10-07T00:00:00Z")
      val declared = Declaration("workflow", DataStore.CollectionId.EntityStore(
        EntityCollectionId("test", "host", "review_evidence")), "specification-issuer")
      val reference = Json.obj("identity" -> Json.fromString("specification-result"), "revision" -> Json.fromString("1"))
      val lookup = ThreeWorkflowHostEvidencePorts.key("specification-result", "1")
      val completion = Json.obj("handle" -> Json.obj(
        "componentIdentity" -> Json.fromString("specification-component"),
        "workflowIdentity" -> Json.fromString("specification-workflow"),
        "workflowRevision" -> Json.fromString("1"), "instanceIdentity" -> Json.fromString("selected-instance"),
        "protocolVersion" -> Json.fromString("1")), "runId" -> Json.fromString("specification-run"),
        "continuationId" -> Json.fromString("specification-review"), "typeIdentity" -> Json.fromString("specification-result-type"))
      val variants = Vector(_receipt(tests = 4L), _receipt(classes = Vector("example.OtherSpec")),
        _receipt(selection = false), _actual.copy(sbt = Some(Facts(Kind.Test, Outcome.Succeeded, None, None))))
      Vector(Purpose.GoalReview, Purpose.RepositoryReview).foreach { purpose =>
        val checksKey = if (purpose == Purpose.GoalReview) "testChecks" else "checks"
        def target(receipt: V.Receipt): Json = if (purpose == Purpose.GoalReview)
          ThreeWorkflowHostEvidencePorts.goalReviewTarget(_work.copy(review = _work.review.map(_.copy(checks = Vector(receipt)))))
        else ThreeWorkflowHostEvidencePorts.repositoryReviewTarget(_review.copy(checks = Vector(receipt)))
        def binding(selected: Json): Json = ThreeWorkflowHostEvidencePorts.reviewBinding("specification-owner",
          if (purpose == Purpose.GoalReview) "goal-review" else "repository-review", selected, completion,
          reference, Json.fromString("specification-reviewer"), if (purpose == Purpose.GoalReview)
            ThreeWorkflowHostEvidencePorts.goalReviewVerdict(W.Result("accept", "diagnostic", Vector.empty))
          else ThreeWorkflowHostEvidencePorts.repositoryReviewVerdict(R.Result("accept", "diagnostic", Vector.empty)))
        val actual = binding(target(_actual))
        val evidence = Evidence(NativeHostEvidence.schema, purpose.identity, lookup, "specification-result", "1",
          declared.issuer, "specification-reviewer", actual, now.toString)
        When("the real v7 encoder and decoder select the target and observation compares its actual binding for " + purpose)
        val raw = take(NativeHostEvidence.encodeStorageC(evidence))
        val decoded = take(NativeHostEvidence.decodeStorageC(raw))
        val stored = io.circe.parser.parse(raw.substring(NativeHostEvidence.schema.length + 1)).toOption.get
          .hcursor.downField("binding").downField("attestation").downField("target")
          .downField(checksKey).downArray.downField("sbt")
        Then("explicit kind outcome classes and counts survive storage and the matching actual binding is admitted")
        stored.get[String]("kind") shouldBe Right("test-only")
        stored.get[String]("outcome") shouldBe Right("succeeded")
        stored.downField("selection").get[Vector[String]]("testClasses") shouldBe Right(Vector("example.SelectedSpec"))
        stored.downField("selection").get[Int]("completedSuites") shouldBe Right(1)
        stored.downField("selection").get[Long]("testsRun") shouldBe Right(3L)
        NativeHostEvidence.observeC(decoded, declared, purpose, lookup, actual, now).isSuccess shouldBe true
        variants.foreach { receipt =>
          Given("the same issued review with a valid changed class count outcome or kind for " + purpose)
          When("the original stored evidence is observed against those changed selected facts")
          val crossed = NativeHostEvidence.observeC(decoded, declared, purpose, lookup, binding(target(receipt)), now)
          Then("that changed selected meaning cannot reuse the original evidence")
          crossed.isSuccess shouldBe false
        }
        Given("the historical target without sbt and a typed target annotated with unused logs")
        val historical = binding(target(_actual.copy(sbt = None)))
        When("historical storage is reopened and typed evidence is compared with stripped or annotated targets")
        val legacy = take(NativeHostEvidence.decodeStorageC(take(NativeHostEvidence.encodeStorageC(evidence.copy(binding = historical)))))
        val annotated = binding(target(_actual).mapObject(_.add(checksKey, Json.arr(
          target(_actual).hcursor.downField(checksKey).downArray.focus.get.mapObject(
            _.add("stdout", Json.fromString("inert log")).add("grant", Json.True))))))
        Then("absence retains its exact historical shape and cannot stand for typed facts while annotations remain inert")
        NativeHostEvidence.observeC(decoded, declared, purpose, lookup, historical, now).isSuccess shouldBe false
        NativeHostEvidence.observeC(legacy, declared, purpose, lookup, actual, now).isSuccess shouldBe false
        NativeHostEvidence.observeC(decoded, declared, purpose, lookup, annotated, now).isSuccess shouldBe true
        legacy.binding shouldBe historical
        if (purpose == Purpose.GoalReview)
          legacy.binding.hcursor.downField("attestation").downField("target").downField("testChecks").focus shouldBe None
        else legacy.binding.hcursor.downField("attestation").downField("target").downField("checks").downArray.focus.get
          .asObject.get.keys.toSet shouldBe Set("identity", "termination", "exitCode")
        Vector(Json.Null, Json.True, Json.obj(), _actual.sbt.get.asJson.mapObject(_.add("kind", Json.fromString("unknown"))))
          .foreach { malformed =>
            Given("present malformed sbt in a selected " + purpose + " review check")
            val invalid = binding(target(_actual).mapObject(_.add(checksKey, Json.arr(
              target(_actual).hcursor.downField(checksKey).downArray.focus.get.mapObject(_.add("sbt", malformed))))))
            When("the real storage encoder or decoder consumes that malformed selected fact")
            val invalidRaw = io.circe.parser.parse(raw.substring(NativeHostEvidence.schema.length + 1)).toOption.get
              .mapObject(_.add("binding", invalid))
            Then("both storage directions reject it instead of treating presence as historical absence")
            NativeHostEvidence.encodeStorageC(evidence.copy(binding = invalid)).isSuccess shouldBe false
            NativeHostEvidence.decodeStorageC(NativeHostEvidence.schema + ":" + invalidRaw.noSpaces).isSuccess shouldBe false
          }
      }
    }

    "bind actual TestOnly class count and outcome while preserving historical no-facts target shape" in {
      Given("the same assigned review with actual TestOnly variants and historical raw receipts")
      val variants = Vector(_actual, _receipt(tests = 4L), _receipt(classes = Vector("example.OtherSpec")), _receipt(selection = false))
      val oldwork = _work.copy(review = _work.review.map(_.copy(checks = Vector(_actual.copy(sbt = None)))))
      val oldreview = _review.copy(checks = Vector(_actual.copy(sbt = None)))
      When("host evidence projects the semantic target of each actual assigned review")
      val goals = variants.map(receipt => ThreeWorkflowHostEvidencePorts.goalReviewTarget(
        _work.copy(review = _work.review.map(_.copy(checks = Vector(receipt))))))
      val repositories = variants.map(receipt => ThreeWorkflowHostEvidencePorts.repositoryReviewTarget(_review.copy(checks = Vector(receipt))))
      val oldgoal = ThreeWorkflowHostEvidencePorts.goalReviewTarget(oldwork)
      val oldrepository = ThreeWorkflowHostEvidencePorts.repositoryReviewTarget(oldreview)
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
      ThreeWorkflowHostEvidencePorts.repositoryReviewTarget(_review.copy(checks = Vector(_actual.copy(stdout = "changed log", stderr = "changed error")))) shouldBe repositories.head
    }
  }
}
