package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseNativeCandidate as C
import GoalPhaseNativeValidation as V
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as S
import GoalPhaseNativeClosure as K
import GoalPhaseNativeRepositoryClosure as L
import RepositorySyncNativeConvergence as R
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeProgressStore as N

/** Native saved inputs are scoped facts, not complete host-declaration witnesses.
  * @since   Oct.  8, 2026
  * @version Oct. 11, 2026
  */
final class GoalPhaseNativeRetentionApplicabilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val candidate = O.Snapshot("a" * 40, "b" * 40)
  private def candidateJson = Json.obj("head" -> Json.fromString(candidate.head), "tree" -> Json.fromString(candidate.tree))
  private def payload(value: Json) = value.hcursor.downField("payload").focus.get
  private def replace(value: Json, nested: Json) = value.mapObject(_.add("payload", nested))

  "Native candidate retained format" should {
    "read current typed candidate facts without host-path witnesses or unused-field refusals" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R1; Example: E1; a current selected candidate payload")
      val saved = C.Payload("5", "S1", Vector("one.scala"), Vector("one.scala"),
        Some(candidate), Vector("one.scala"), Some("an actual retained diff"))
      val wire = C.codec.encode(saved)
      val nested = payload(wire)
      val annotated = nested.mapObject(_.add("run", Json.fromString("must-not-execute"))
        .add("candidate", candidateJson.mapObject(_.add("approve", Json.True))))
      When("the current codec decodes consumed facts with optional omissions and inert annotations")
      val actual = C.codec.decode(replace(wire.mapObject(_.add("diagnostics", Json.True)), annotated))
      val optional = C.codec.decode(replace(wire, nested.mapObject(_.remove("candidate").remove("diff"))))
      Then("unused values have no effect on candidate data and absent optional facts remain absent")
      actual shouldBe Right(saved)
      optional shouldBe Right(saved.copy(candidate = None, diff = None))
      nested.asObject.get.keys.toSet should not contain "hostPaths"

      Given("a retired v1 schema or missing/malformed consumed candidate fields")
      val invalid = Vector(wire.mapObject(_.add("schemaVersion", Json.fromString("sm-workflow.goal-phase-native-candidate.v1"))),
        replace(wire, nested.mapObject(_.add("selectedPaths", Json.True))),
        replace(wire, nested.mapObject(_.remove("step"))))
      When("these inputs are decoded without a compatibility reader")
      val rejected = invalid.map(C.codec.decode)
      Then("actual schema/type/required-field contradictions fail")
      rejected.foreach(_.isLeft shouldBe true)
    }
  }

  "Native validation retained format" should {
    "read current check results without policy copies or nested key preflights" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R2; Example: E2; actual selected check bindings and result facts")
      val saved = V.Payload("8", "S1", candidate, Vector(V.Check("focused", 1)),
        Vector(RepositorySyncNativeMergeValidation.Receipt("focused", "exited", Some(0), "", "")), Some("passed"))
      val wire = V.codec.encode(saved)
      val nested = payload(wire)
      val check = nested.hcursor.downField("selected").focus.get.asArray.get.head
      val receipt = nested.hcursor.downField("receipts").focus.get.asArray.get.head
      val annotated = nested.mapObject(_.add("approve", Json.True)
        .add("selected", Json.arr(check.mapObject(_.add("command", Json.fromString("must-not-execute")))))
        .add("receipts", Json.arr(receipt.mapObject(_.add("authority", Json.True))))
        .add("candidate", candidateJson.mapObject(_.add("approve", Json.True))))
      When("current results decode with unused annotations and an omitted optional outcome")
      val actual = V.codec.decode(replace(wire, annotated))
      val optional = V.codec.decode(replace(wire, nested.mapObject(_.remove("outcome"))))
      Then("only typed check result facts are returned; added authority/command values are inert")
      actual shouldBe Right(saved)
      optional shouldBe Right(saved.copy(outcome = None))
      nested.asObject.get.keys.toSet should not contain "policy"

      Given("retired v1 validation data or a missing consumed source revision")
      val invalid = Vector(wire.mapObject(_.add("schemaVersion", Json.fromString("sm-workflow.goal-phase-native-validation.v1"))),
        replace(wire, nested.mapObject(_.remove("sourceRevision"))))
      When("the current-only codec consumes them")
      val rejected = invalid.map(V.codec.decode)
      Then("no policy extraction or old-format adapter is selected")
      rejected.foreach(_.isLeft shouldBe true)
    }
  }

  private def workInput = {
    val plan = O.Plan("selected-plan", "1", "PHASE-2", "repository",
      Vector(O.Step("S1", "Selected semantics", Vector("one.scala"), Vector.empty, Vector("focused"))), Vector("whole-phase"))
    S.Request("owned-instance", "11", "ReviewStep", Some("S1"), 1, "Original review meaning", plan,
      O.Snapshot("c" * 40, "d" * 40), Vector.empty, Some(candidate), Some("saved-checks"), None,
      Some(GoalPhaseNativeReview.Context(Vector("one.scala"), "original diff",
        Vector(RepositorySyncNativeMergeValidation.Receipt("focused", "exited", Some(0), "", "")))))
  }
  "Native retained review context" should {
    "consume current review meaning and ignore inert policy markers" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R3; Example: E7; a current complete assigned review")
      val original = workInput
      val wire = S.inputCodec.encode(original)
      val body = payload(wire)
      val review = body.hcursor.downField("review").focus.get
      val annotated = review.mapObject(_.add("policyIdentity", Json.True)
        .add("policyRevision", Json.Null).add("grant", Json.True))
      When("current typed work decodes the review with inert former policy/permission annotations")
      val actual = S.inputCodec.decode(replace(wire, body.mapObject(_.add("review", annotated))))
      Then("only the genuine review data is consumed, without added execution permission")
      actual shouldBe Right(original)
      review.asObject.get.keys.toSet shouldBe Set("changedPaths", "diff", "checks")

      Given("a missing consumed review member or a retired v1 request schema")
      val invalid = Vector(replace(wire, body.mapObject(_.add("review", review.mapObject(_.remove("changedPaths"))))),
        wire.mapObject(_.add("schemaVersion", Json.fromString("sm-workflow.goal-phase-native-work-request.v1"))))
      When("the current request codec decodes these inputs")
      val rejected = invalid.map(S.inputCodec.decode)
      Then("actual consumed-field/schema contradictions fail without legacy normalization")
      rejected.foreach(_.isLeft shouldBe true)
    }
  }
  "Native assigned work retention" should {
    "read current optional work facts and ignore unused nested controls" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R3; Example: E5; a current typed implementation request")
      val original = workInput.copy(kind = "ImplementStep", candidate = None, checksReceipt = None, review = None)
      val wire = S.inputCodec.encode(original)
      val nested = payload(wire)
      val plan = nested.hcursor.downField("selectedPlan").focus.get
      val step = plan.hcursor.downField("steps").focus.get.asArray.get.head
      val optional = nested.mapObject(_.add("review", Json.Null).add("decisionContext", Json.Null)
        .add("selectedPlan", plan.mapObject(_.add("phaseReviewClassification", Json.Null)
          .add("steps", Json.arr(step.mapObject(_.add("workClassification", Json.Null)))))))
      val annotated = optional.mapObject(_.add("command", Json.fromString("must-not-execute"))
        .add("selectedPlan", plan.mapObject(_.add("approve", Json.True)
          .add("steps", Json.arr(step.mapObject(_.add("run", Json.True))))))
        .add("originalBase", nested.hcursor.downField("originalBase").focus.get.mapObject(_.add("authority", Json.True))))
      When("the current codec decodes optional absence and unused nested annotations")
      val decodedOptional = S.inputCodec.decode(replace(wire, optional))
      val decodedAnnotated = S.inputCodec.decode(replace(wire, annotated))
      Then("only actual business work facts are returned and unused commands grant nothing")
      decodedOptional shouldBe Right(original)
      decodedAnnotated shouldBe Right(original)

      Given("an actual semantic result and a request missing its consumed kind")
      val result = S.Result("completed", "Actual semantic result", Vector.empty)
      val resultWire = S.resultCodec.encode(result)
      When("the result codec reads current v1 with inert annotations and the request codec reads missing kind")
      val decodedResult = S.resultCodec.decode(replace(resultWire, payload(resultWire).mapObject(_.add("command", Json.True))))
      val missingKind = S.inputCodec.decode(replace(wire, nested.mapObject(_.remove("kind"))))
      Then("current result.v1 remains supported while absent actual request data fails")
      decodedResult shouldBe Right(result)
      missingKind.isLeft shouldBe true
    }
  }

  "Native Start retained format" should {
    "consume current Start facts without a command copy or unused-field refusals" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R4; Example: E9; participant, Git target and actual Plan/base facts")
      val input = workInput
      val saved = GoalPhaseNativeRun.Payload("participant", "/selected/repository", "main", input.selectedPlan, input.originalBase)
      When("the current native Start codec writes and reads these consumed facts")
      val wire = GoalPhaseNativeRun.codec.encode(saved)
      val decoded = GoalPhaseNativeRun.codec.decode(wire)
      Then("the current record needs no saved command to retain its participant, target and controller input")
      decoded shouldBe Right(saved)
      val nested = payload(wire)
      nested.asObject.get.keys.toSet should not contain "originalCommand"

      Given("the current Plan's classification is optional and diagnostics do not supply executable authority")
      val plan = nested.hcursor.downField("plan").focus.get
      val step = plan.hcursor.downField("steps").focus.get.asArray.get.head
      val unclassified = plan.mapObject(_.remove("phaseReviewClassification").add("steps",
        Json.arr(step.mapObject(_.remove("workClassification")))))
      val diagnostic = nested.mapObject(_.add("originalCommand", Json.True)
        .add("approval", Json.True).add("plan", plan.mapObject(_.add("command", Json.fromString("git"))))
        .add("base", nested.hcursor.downField("base").focus.get.mapObject(_.add("run", Json.True))))
      When("optional classification is omitted or unused diagnostic fields are present")
      val optional = GoalPhaseNativeRun.codec.decode(replace(wire, nested.mapObject(_.add("plan", unclassified))))
      val ignored = GoalPhaseNativeRun.codec.decode(replace(wire, diagnostic).mapObject(_.add("authority", Json.True)))
      Then("the same consumed business facts decode; obsolete command and diagnostic fields have no meaning")
      optional shouldBe Right(saved)
      ignored shouldBe Right(saved)

      Given("required participant, target and controller facts must have their actual consumed types")
      val invalid = Vector(nested.mapObject(_.remove("participantIdentity")),
        nested.mapObject(_.add("root", Json.True)), nested.mapObject(_.add("plan", Json.True)),
        nested.mapObject(_.remove("base")))
      When("current records cannot supply those required values, or select the removed v1 schema")
      val malformed = invalid.map(item => GoalPhaseNativeRun.codec.decode(replace(wire, item)))
      val oldSchema = GoalPhaseNativeRun.codec.decode(wire.mapObject(_.add("schemaVersion",
        Json.fromString("sm-workflow.goal-phase-native-start.v1"))))
      Then("typed consumed decoding fails and no old reader or compatibility fallback is selected")
      malformed.foreach(_.isLeft shouldBe true)
      oldSchema.isLeft shouldBe true
    }
  }

  "Native Step closure retained format" should {
    "retain current business commit facts without copied authority or policy witnesses" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R5; Example: E10; actual commit intent and result facts")
      val work = workInput
      val input = K.Input(work.instanceIdentity, work.sourceRevision, work.selectedPlan.identity,
        work.selectedPlan.revision, "S1", 1, work.originalBase, candidate, "review-result")
      val saved = K.Payload(input, 1, Some(O.Commit("e" * 40, work.originalBase.head, candidate.tree)))
      val wire = K.codec.encode(saved)
      val nested = payload(wire)
      val target = nested.hcursor.downField("input").focus.get
      val commit = nested.hcursor.downField("commit").focus.get
      val annotated = nested.mapObject(_.add("authority", Json.True).add("authorityRevision", Json.True)
        .add("input", target.mapObject(_.add("approval", Json.True)))
        .add("commit", commit.mapObject(_.add("run", Json.True))))
      When("current v3 reads typed facts, optional commit absence and inert annotations")
      val actual = K.codec.decode(replace(wire, annotated))
      val optional = K.codec.decode(replace(wire, nested.mapObject(_.remove("commit"))))
      Then("saved data contains no permission certificate and unused values do not change commit facts")
      actual shouldBe Right(saved)
      optional shouldBe Right(saved.copy(commit = None))
      nested.asObject.get.keys.toSet shouldBe Set("input", "ordinal", "commit")

      Given("retired v1/v2 rows or a malformed consumed ordinal")
      val invalid = Vector("v1", "v2").map(version => wire.mapObject(_.add("schemaVersion",
        Json.fromString("sm-workflow.goal-phase-native-step-closure." + version)))) :+
        replace(wire, nested.mapObject(_.add("ordinal", Json.True)))
      When("the current-only codec consumes these inputs")
      val rejected = invalid.map(K.codec.decode)
      Then("required types and the current storage schema own refusal, not policy or unchanged-body comparison")
      rejected.foreach(_.isLeft shouldBe true)
    }
  }

  "Native parent-child closure retention" should {
    "consume current parent intent and child result without saved command or authority copies" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R6; Example: E13; saved parent intent and actual child result")
      val work = workInput
      val closed = O.ClosedStep("S1", O.Commit("e" * 40, work.originalBase.head, candidate.tree), "original-step-review")
      val input = L.Input(work.instanceIdentity, work.sourceRevision, work.selectedPlan,
        work.originalBase, Vector(closed), candidate, "phase-review", "owned-child-invocation")
      val child = "owned-child-instance"
      val result = R.Result(child, "/selected/repository", "main", "origin", "main", "f" * 40, candidate.tree)
      val saved = L.Payload(input, child, None, None)
      val wire = L.codec.encode(saved)
      val nested = payload(wire)
      val selected = nested.hcursor.downField("input").focus.get
      val plan = selected.hcursor.downField("plan").focus.get
      val step = plan.hcursor.downField("steps").focus.get.asArray.get.head
      val oldPlan = plan.mapObject(_.remove("phaseReviewClassification").add("steps",
        Json.arr(step.mapObject(_.add("workClassification", Json.Null)))))
      When("a current row omits optional presentation fields")
      L.codec.decode(replace(wire, nested.mapObject(_.remove("result").remove("childRevision")
        .add("input", selected.mapObject(_.add("plan", oldPlan)))))) shouldBe Right(saved)
      Then("the actual parent intent is readable without command or grant copies")
      nested.hcursor.downField("childCommand").focus shouldBe None
      nested.hcursor.downField("authorityReference").focus shouldBe None
      val terminal = L.Result(work.selectedPlan, work.originalBase, Vector(closed), candidate, result, "21")
      val terminalWire = L.resultCodec.encode(terminal)
      L.resultCodec.decode(terminalWire.mapObject(_.add("plan", oldPlan))) shouldBe Right(terminal)
      When("unused annotations accompany a current intent or real terminal result")
      val annotated = nested.mapObject(_.add("approve", Json.True).add("childCommand", Json.True)
        .add("authority", Json.True).add("input", selected.mapObject(_.add("command", Json.True))))
      L.codec.decode(replace(wire, annotated)) shouldBe Right(saved)
      L.resultCodec.decode(terminalWire.mapObject(_.add("repository",
        R.resultCodec.encode(result).mapObject(_.add("grant", Json.True))))) shouldBe Right(terminal)
      Then("typed consumers still require their actual fields and only the current saved schema")
      Vector(nested.mapObject(_.remove("childIdentity")), nested.mapObject(_.add("childIdentity", Json.True)),
        nested.mapObject(_.remove("input"))).foreach(v => L.codec.decode(replace(wire, v)).isLeft shouldBe true)
      L.codec.decode(wire.mapObject(_.add("schemaVersion", Json.fromString("sm-workflow.goal-phase.native-repository-closure.v1")))).isLeft shouldBe true
      L.resultCodec.decode(terminalWire.mapObject(_.add("repository",
        R.resultCodec.encode(result).mapObject(_.add("tip", Json.True))))).isLeft shouldBe true
    }
  }
}
