package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.cncf.workflow.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseOrderedHumanDecision as Goal
import GoalPhaseNativeWork as Work
import GoalPhaseOrderedProgression as Ordered
import RepositorySyncHumanConflict as Repository
import RepositorySyncNativeConflictInput as Conflict

/** Original Human meaning is consumed; reference resolution is not a body seal.
  * @since   Oct.  8, 2026
  * @version Oct. 24, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeHumanQuestionApplicabilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val snapshot = Ordered.Snapshot("a" * 40, "b" * 40)
  private val plan = Ordered.Plan("plan", "1", "phase", "repository", Vector(
    Ordered.Step("step", "Original assignment", Vector("one.scala"), Vector.empty, Vector("focused"))), Vector("focused"))
  private val work = Work.Request("instance", "3", "ImplementStep", Some("step"), 0, "Original purpose", plan,
    snapshot, Vector.empty, None, None, None)
  private val goalInput = Goal.Input(work, ContextReference("requesting-result", "1"),
    Work.Result("decision-needed", "Original result explanation", Vector.empty), "Original question reason")
  private val conflict = Conflict.Request("instance", "3", "a" * 40, "b" * 40, Vector(
    Conflict.Conflict("conflict", "one.scala", Conflict.Blob("c" * 40, "base\n"), Conflict.Blob("d" * 40, "local\n"),
      Conflict.Blob("e" * 40, "remote\n"), worktree = "Original conflict contents\n")))
  private val repositoryInput = Repository.Input(conflict, "requesting-result", "1", "Original conflict rationale")

  "Native Human payload consumption" should {
    "consume current ordered Decision facts with inert root and nested annotations" in {
      Given("Spec: docs/spec/native-human-question-applicability.md; Rule: current Phase5.2; Example: E1; a current ordered Decision input")
      val wire = Goal.inputCodec.encode(goalInput)
      val annotated = wire.mapObject(_.add("command", Json.True).add("requestingResult",
        wire.hcursor.downField("requestingResult").focus.get.mapObject(_.add("approval", Json.True)))
        .add("work", wire.hcursor.downField("work").focus.get.mapObject(_.add("grant", Json.True))))
      When("the owning input codec consumes only actual work, requesting result and question meaning")
      val actual = Goal.inputCodec.decode(annotated)
      Then("unused annotations neither refuse the input nor change its business meaning")
      actual shouldBe Right(goalInput)

      Given("an ordered Decision missing actual work or carrying a mistyped requesting-result identity")
      val invalid = Vector(wire.mapObject(_.remove("work")), wire.mapObject(_.add("requestingResult",
        wire.hcursor.downField("requestingResult").focus.get.mapObject(_.add("identity", Json.True)))))
      When("the same codecs consume those required business members")
      val rejected = invalid.map(Goal.inputCodec.decode)
      Then("missing or mistyped consumed values cannot supply a Decision input")
      rejected.foreach(_.isLeft shouldBe true)
    }
    "consume an ordered Human answer without an unused-field certificate" in {
      Given("Spec: docs/spec/native-human-question-applicability.md; Rule: current Phase5.2; Example: E2; actual Human direction with a rationale")
      val answer = Goal.Answer("ResumeAssignedWork", "Continue only the assigned business work")
      val wire = Goal.answerCodec.encode(answer)
      When("the answer contains an inert command annotation rather than execution authority")
      val actual = Goal.answerCodec.decode(wire.mapObject(_.add("command", Json.True)))
      Then("the actual choice and rationale are returned without granting command execution")
      actual shouldBe Right(answer)

      Given("a choice outside the business alternatives or an absent/mistyped rationale")
      val invalid = Vector(wire.mapObject(_.add("choice", Json.fromString("ExecuteCommand"))),
        wire.mapObject(_.remove("rationale")), wire.mapObject(_.add("rationale", Json.True)))
      When("the owning answer decoder consumes those actual semantic members")
      val rejected = invalid.map(Goal.answerCodec.decode)
      Then("an unknown direction or missing meaningful rationale is rejected at its consuming boundary")
      rejected.foreach(_.isLeft shouldBe true)
    }
  }

  "Repository conflict payload consumption" should {
    "consume current conflict fields without old optional-field extraction or unused-key refusal" in {
      Given("Spec: docs/spec/native-human-question-applicability.md; Rule: current Phase5.2; Example: E5; current native conflict input")
      val wire = RepositorySyncConflictSemantic.inputCodec.encode(conflict)
      val body = wire.hcursor.downField("payload").focus.get
      val item = body.hcursor.downField("conflicts").focus.get.asArray.get.head
      val annotated = wire.mapObject(_.add("command", Json.True).add("payload",
        body.mapObject(_.add("conflicts", Json.arr(item.mapObject(_.add("grant", Json.True).add("base",
          item.hcursor.downField("base").focus.get.mapObject(_.add("execute", Json.True)))))))))
      When("the current typed codec reads inert root, conflict and blob annotations")
      val actual = RepositorySyncConflictSemantic.inputCodec.decode(annotated)
      Then("the same actual conflict facts survive without new command authority")
      actual shouldBe Right(conflict)

      Given("old omitted mode/worktree fields rather than the current produced representation")
      val incomplete = Vector("mode", "worktree").map(field => wire.mapObject(_.add("payload",
        body.mapObject(_.add("conflicts", Json.arr(item.mapObject(_.remove(field))))))))
      When("the current typed codec consumes those required fields")
      val rejected = incomplete.map(RepositorySyncConflictSemantic.inputCodec.decode)
      Then("no old normalization or default-extraction reader supplies missing facts")
      rejected.foreach(_.isLeft shouldBe true)
    }
    "consume current Human and semantic answers with inert annotations and actual business meaning" in {
      Given("Spec: docs/spec/native-human-question-applicability.md; Rule: current Phase5.2; Example: E6; an actual Human conflict question")
      val wire = Repository.inputCodec.encode(repositoryInput)
      val body = wire.hcursor.downField("payload").focus.get
      val target = body.hcursor.downField("conflict").focus.get
      val item = target.hcursor.downField("conflicts").focus.get.asArray.get.head
      def withItem(value: Json): Json = body.mapObject(_.add("conflict",
        target.mapObject(_.add("conflicts", Json.arr(value)))))
      val annotated = withItem(item.mapObject(_.add("command", Json.True).add("base",
        item.hcursor.downField("base").focus.get.mapObject(_.add("grant", Json.True)))))
        .mapObject(_.add("authority", Json.True))
      When("the current Human input decoder consumes only its typed members")
      val actual = Repository.inputCodec.decode(wire.mapObject(_.add("payload", annotated)))
      Then("unused members neither reject the input nor change the issued business meaning")
      actual shouldBe Right(repositoryInput)

      Given("missing or mistyped current typed fields")
      val invalid = Vector(body.mapObject(_.remove("requestingResultIdentity")), withItem(item.mapObject(_.add("mode", Json.True))),
        body.mapObject(_.add("rationale", Json.True)))
      When("those values reach their consuming typed decoder")
      val rejected = invalid.map(value => Repository.inputCodec.decode(wire.mapObject(_.add("payload", value))))
      Then("actual required data cannot be invented by ignored annotations")
      rejected.foreach(_.isLeft shouldBe true)

      Given("a Human resolution of the actual assigned conflict IDs")
      val answer = Repository.Answer("ProvideResolution", Vector(RepositorySyncConflictSemantic.Resolution("conflict", "actual\n")), "Meaningful direction")
      val reply = Repository.answerCodec.encode(answer)
      val annotatedReply = reply.mapObject(_.add("payload", reply.hcursor.downField("payload").focus.get.mapObject(
        _.add("resolutions", Json.arr(reply.hcursor.downField("payload").downField("resolutions").focus.get.asArray.get.head
          .mapObject(_.add("path", Json.fromString("outside"))))))))
      val semantic = RepositorySyncConflictSemantic.resultCodec.encode(Repository.proposal(answer))
      When("unused path/run diagnostics accompany current Human and semantic results")
      val decodedAnswer = Repository.answerCodec.decode(annotatedReply)
      val decodedSemantic = RepositorySyncConflictSemantic.resultCodec.decode(semantic.mapObject(_.add("payload",
        semantic.hcursor.downField("payload").focus.get.mapObject(_.add("run", Json.True)))))
      Then("only conflictIdentity/text and actual disposition/rationale are consumed")
      decodedAnswer shouldBe Right(answer)
      decodedSemantic shouldBe Right(Repository.proposal(answer))
    }
  }
}
