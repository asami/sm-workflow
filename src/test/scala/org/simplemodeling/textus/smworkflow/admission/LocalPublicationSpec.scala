package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take
import LocalPublication.*

/** Pure decision and continuation contracts. Supplied process facts are test
  * data; actual CLI/UoW execution is qualified separately.
  * @since   Oct. 26, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class LocalPublicationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _target = Target("producer", "org.example.Producer", "producer-workspace")
  private val _clean = Source("a" * 40, "b" * 40, Vector.empty)
  private def _observed(termination: ProcessExecutionTermination, workspace: String = _target.workspace): SbtExecutionAttempt = {
    val capture = ProcessExecutionCapture(Vector.empty, 0L, false)
    SbtExecutionAttempt.Observed(SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC(workspace))), SbtOperation.PublishLocal,
      ProcessExecutionResult(termination, capture, capture, Vector.empty, 0L, "test-owned-publication")))
  }
  private class Memory extends Port {
    val rows = scala.collection.mutable.Map.empty[String, Json]
    var source = _clean
    var starts = 0
    var ids = 0
    var next: SbtExecutionAttempt = _observed(ProcessExecutionTermination.Exited(0))
    def loadC(key: String) = Consequence.success(rows.get(key))
    def createC(key: String, value: Json): Consequence[Unit] = {
      if (rows.contains(key)) Consequence.stateConflict("Immutable duplicate")
      else { rows(key) = value; Consequence.unit }
    }
    def sourceC(t: Target) = Consequence.success(source)
    def executeC(t: Target) = { starts += 1; Consequence.success(next) }
    def newIdentity() = { ids += 1; "runtime-issued-" + ids }
  }
  private def _ensure(m: Memory, identity: String = "request", explicit: Boolean = false,
    answer: Option[Answer] = None): Result = take(LocalPublication.ensureC(
    Input(identity, _target.identity, Some("same-retained-session"), explicit, answer), _target, "actor", m))

  "Runtime-owned publication" should {
    "observe a new source change after a ready successor skipped without publishing" in {
      Given("a prerequisite published A, then a documentation-only successor skipped")
      val m = new Memory
      val input = Input("changing-source-prerequisite", _target.identity)
      take(ensurePrerequisiteC(input, _target, "actor", m)).status shouldBe "published"
      m.source = _clean.copy(dirty = Vector(Change("docs/guide.md", " ", "M")))
      take(ensurePrerequisiteC(input, _target, "actor", m)).status shouldBe "unnecessary"
      When("source code changes without another target publication")
      m.source = _clean.copy(dirty = Vector(Change("src/main/scala/Input.scala", " ", "M")))
      take(ensurePrerequisiteC(input, _target, "actor", m)).status shouldBe "published"
      Then("the obsolete no-effect successor cannot hide the newly necessary build")
      m.starts shouldBe 2
    }

    "reuse a prerequisite's accepted negative semantic answer while its publication baseline stays current" in {
      Given("an A publication and a prerequisite's actual bounded question")
      val m = new Memory
      _ensure(m, "external-a")
      m.source = _clean.copy(tree = "c" * 40, dirty = Vector(Change("rules/example.conf", "M", " ")))
      val input = Input("semantic-prerequisite", _target.identity)
      val question = take(ensurePrerequisiteC(input, _target, "actor", m))
      val answer = Answer(question.request.question.get, false, "This staged file does not affect the build")
      val resolved = take(ensurePrerequisiteC(input.copy(answer = Some(answer)), _target, "actor", m))
      When("the same prerequisite resumes with the same source and actual publication baseline")
      take(ensurePrerequisiteC(input, _target, "actor", m)) shouldBe resolved
      Then("its accepted answer is retained without another question or publication")
      resolved.status shouldBe "unnecessary"
      m.starts shouldBe 1
      m.ids shouldBe 1
    }

    "preserve a prerequisite's own dirty success and terminal failure instead of selecting another attempt" in {
      Given("a dirty producer and each actual terminal outcome")
      val attempts = Vector(_observed(ProcessExecutionTermination.Exited(0)),
        _observed(ProcessExecutionTermination.Exited(9)),
        SbtExecutionAttempt.AdmissionRejected(SbtTarget(take(WorkspaceId.parseC(_target.workspace))),
          SbtOperation.PublishLocal, Consequence.stateConflict("fixture admission rejection").conclusion))
      attempts.foreach { attempt =>
        val m = new Memory
        m.source = _clean.copy(dirty = Vector(Change("src/main/scala/Input.scala", " ", "M")))
        m.next = attempt
        val input = Input("same-prerequisite", _target.identity)
        val first = take(ensurePrerequisiteC(input, _target, "actor", m))
        When("the same prerequisite resumes before its check")
        take(ensurePrerequisiteC(input, _target, "actor", m)) shouldBe first
        Then("the original result remains terminal with exactly one publication attempt")
        m.starts shouldBe 1
      }
    }

    "reassess a prerequisite that initially skipped without an actual publication attempt of its own" in {
      Given("another request published A and a prerequisite reused that applicable success")
      val m = new Memory
      _ensure(m, "other-a")
      val input = Input("skipped-prerequisite", _target.identity)
      take(ensurePrerequisiteC(input, _target, "actor", m)).status shouldBe "unnecessary"
      m.source = _clean.copy(head = "c" * 40, tree = "d" * 40)
      _ensure(m, "other-b")
      When("the skipped prerequisite resumes on the original A source")
      m.source = _clean
      take(ensurePrerequisiteC(input, _target, "actor", m)).status shouldBe "published"
      Then("the superseded skip is retained but cannot leave B installed")
      m.starts shouldBe 3
    }

    "reassess a resumed prerequisite against the latest target without replaying old successful requests" in {
      Given("a prerequisite that published A and was interrupted before its check")
      val m = new Memory
      val input = Input("session-candidate-prerequisite", _target.identity)
      val first = take(ensurePrerequisiteC(input, _target, "actor", m))
      take(ensurePrerequisiteC(input, _target, "actor", m)) shouldBe first
      m.starts shouldBe 1
      When("another actual publication changes the target to B and the original A prerequisite resumes")
      m.source = _clean.copy(head = "c" * 40, tree = "d" * 40)
      _ensure(m, "different-b")
      m.source = _clean
      take(ensurePrerequisiteC(input, _target, "actor", m)).status shouldBe "published"
      Then("only the newly necessary successor executes and the original success remains unchanged")
      m.starts shouldBe 3
      take(LocalPublication.collectC(first.request.input, _target, "actor", m)).get shouldBe first
    }

    "retain a negative build-relevance answer without using its superseded publication baseline" in {
      Given("a bounded unknown-path question based on successful A")
      val m = new Memory
      _ensure(m, "clean-a")
      val questioned = _clean.copy(tree = "c" * 40, dirty = Vector(Change("rules/example.conf", "M", " ")))
      m.source = questioned
      val question = _ensure(m, "pending-question")
      question.status shouldBe "semantic-required"
      m.source = _clean.copy(head = "d" * 40, tree = "e" * 40)
      _ensure(m, "clean-b")
      When("the exact questioned A source returns and the answer says its staged paths do not affect the build")
      m.source = questioned
      val answer = Answer(question.request.question.get, false, "The staged rules are documentation-only inputs")
      _ensure(m, "pending-question", answer = Some(answer)).status shouldBe "published"
      Then("the semantic answer cannot leave B installed instead of the required A source")
      m.starts shouldBe 3
    }

    "require publication when an intervening successful build superseded an older clean source" in {
      Given("a clean A publication followed by a dirty publication or clean B publication")
      Vector(_clean.copy(dirty = Vector(Change("src/main/scala/Input.scala", " ", "M"))),
        _clean.copy(head = "c" * 40, tree = "d" * 40)).foreach { intervening =>
        val m = new Memory
        _ensure(m, "original-a").status shouldBe "published"
        m.source = intervening
        _ensure(m, "intervening").status shouldBe "published"
        When("the working tree returns to clean A with a fresh request")
        m.source = _clean
        _ensure(m, "returned-a").status shouldBe "published"
        Then("the old A receipt remains collectible but cannot describe the latest local artifact")
        m.starts shouldBe 3
        _ensure(m, "original-a").status shouldBe "published"
        _ensure(m, "same-current-a").status shouldBe "unnecessary"
        m.starts shouldBe 3
      }
    }

    "retain uncertainty across fresh request identities when the latest target effect has no terminal result" in {
      Given("a successful A publication followed by an interrupted dirty publication")
      val m = new Memory
      _ensure(m, "original-a")
      m.source = _clean.copy(dirty = Vector(Change("src/main/scala/Input.scala", " ", "M")))
      val changed = _ensure(m, "interrupted")
      val key = m.rows.collectFirst { case (key, value) if key.endsWith(":result") &&
        value.hcursor.get[String]("status").toOption.contains("published") &&
        value.hcursor.downField("request").get[Input]("input").toOption.contains(changed.request.input) => key }.get
      m.rows.remove(key)
      When("a fresh request returns to the old clean source")
      m.source = _clean
      _ensure(m, "fresh-key").status shouldBe "uncertain"
      Then("neither historical success nor a new identity permits another effect")
      m.starts shouldBe 2
    }

    "publish missing evidence once and recollect completed success without executing again" in {
      Given("a selected producer with no retained publication evidence")
      val m = new Memory
      When("ensure executes, its response is lost, and the same request is collected and ensured")
      val first = _ensure(m)
      val collected = take(LocalPublication.collectC(first.request.input, _target, "actor", m)).get
      val repeated = _ensure(m)
      Then("the actual terminal result is reused and the publication count remains one")
      first.status shouldBe "published"
      collected shouldBe first
      repeated shouldBe first
      m.starts shouldBe 1
    }

    "skip the same native source and documentation-only changes using actual retained success" in {
      Given("a successful clean publication created by the same runtime path")
      val m = new Memory
      _ensure(m)
      When("a new prerequisite observes the same native source and then only documentation changes")
      _ensure(m, "same-source").status shouldBe "unnecessary"
      m.source = _clean.copy(dirty = Vector(Change("docs/guide.md", " ", "M")))
      val docs = _ensure(m, "docs-only")
      Then("neither requires another publication")
      docs.status shouldBe "unnecessary"
      m.starts shouldBe 1
    }

    "keep explicit requests and actual source changes on the same execution path" in {
      Given("successful evidence for the clean source")
      val m = new Memory
      _ensure(m)
      When("an explicit publication is requested and later a source path is renamed into documentation")
      _ensure(m, "explicit", explicit = true).status shouldBe "published"
      m.source = _clean.copy(dirty = Vector(Change("docs/old.scala", "R", " ", Some("src/main/scala/Old.scala"))))
      val changed = _ensure(m, "source-rename")
      Then("both use the same typed execution and the rename is not mistaken for a documentation-only edit")
      changed.status shouldBe "published"
      m.starts shouldBe 3
    }

    "delegate only unresolved staged build relevance and bind the answer to the issued source" in {
      Given("successful evidence followed by a staged unclassified build input")
      val m = new Memory
      _ensure(m)
      m.source = _clean.copy(tree = "c" * 40, dirty = Vector(Change("rules/example.conf", "M", " ")))
      When("the runtime prepares a bounded semantic question")
      val question = _ensure(m, "semantic")
      Then("there is no process or generic publication approval question")
      question.status shouldBe "semantic-required"
      question.request.paths shouldBe Vector("rules/example.conf")
      m.starts shouldBe 1
      When("the actual answer identifies this path as a build input")
      _ensure(m, "semantic", answer = Some(Answer(question.request.question.get, true, "This rule is loaded during compile")))
        .status shouldBe "published"
      Then("the runtime executes exactly once after that answer")
      m.starts shouldBe 2
    }

    "reject a stale semantic answer and preserve the original question" in {
      Given("an issued staged-source question")
      val m = new Memory
      _ensure(m)
      m.source = _clean.copy(dirty = Vector(Change("rules.conf", "M", " ")))
      val question = _ensure(m, "semantic")
      m.source = m.source.copy(tree = "d" * 40)
      When("an answer arrives after the actual source changed")
      val result = LocalPublication.ensureC(Input("semantic", _target.identity, Some("same-retained-session"),
        answer = Some(Answer(question.request.question.get, true, "Relevant"))), _target, "actor", m)
      Then("no publication occurs and the original immutable question remains")
      result.isFaillure shouldBe true
      m.starts shouldBe 1
      m.rows.values.count(_.hcursor.get[String]("status").toOption.contains("published")) should be >= 1
    }

    "continue only confirmed nonstart preparation while retaining each actual attempt" in {
      Given("an actual typed NotStarted result")
      val m = new Memory
      m.next = _observed(ProcessExecutionTermination.NotStarted)
      val first = _ensure(m)
      When("a read collects it and the next ordinary ensure follows its runtime-owned preparation")
      take(LocalPublication.collectC(first.request.input, _target, "actor", m)).get shouldBe first
      m.starts shouldBe 1
      val second = _ensure(m)
      second.status shouldBe "not-started"
      m.next = _observed(ProcessExecutionTermination.Exited(0))
      val third = _ensure(m)
      Then("two nonstarts and one success remain retained without permission renewal or product repair")
      third.status shouldBe "published"
      m.starts shouldBe 3
      m.rows.values.count(_.hcursor.get[String]("status").toOption.contains("not-started")) shouldBe 2
      take(LocalPublication.collectC(first.request.input, _target, "actor", m)).get shouldBe third
      m.starts shouldBe 3
    }

    "retain actual failure admission rejection and uncertainty without automatic replay" in {
      Given("actual-shaped failed, rejected and uncertain outcomes")
      val conclusion = Consequence.serviceUnavailable("test-owned admission failure").conclusion
      val cases = Vector(_observed(ProcessExecutionTermination.Exited(1)) -> "failed",
        _observed(ProcessExecutionTermination.TimedOut) -> "uncertain",
        SbtExecutionAttempt.AdmissionRejected(SbtTarget(take(WorkspaceId.parseC(_target.workspace))), SbtOperation.PublishLocal, conclusion) -> "admission-rejected",
        SbtExecutionAttempt.Uncertain(SbtTarget(take(WorkspaceId.parseC(_target.workspace))), SbtOperation.PublishLocal, conclusion) -> "uncertain")
      When("each request is ensured twice")
      cases.foreach { (attempt, status) =>
        val m = new Memory
        m.next = attempt
        _ensure(m).status shouldBe status
        _ensure(m).status shouldBe status
        Then("only one actual attempt exists")
        m.starts shouldBe 1
      }
    }

    "never replay an intent whose terminal outcome is missing" in {
      Given("an immutable intent without a terminal result after a response interruption")
      val m = new Memory
      val result = _ensure(m)
      m.rows.remove("local-publication:actor:request:result")
      When("the same publication request is ensured")
      val recollected = _ensure(m)
      Then("uncertainty is returned without another process")
      recollected.status shouldBe "uncertain"
      m.starts shouldBe 1
    }

    "reject a mismatched workspace result and caller-supplied execution or destination fields" in {
      Given("a process result for a different target and an input with arbitrary argv")
      val m = new Memory
      m.next = _observed(ProcessExecutionTermination.Exited(0), "other-workspace")
      When("the runtime accepts results and decodes the closed input")
      LocalPublication.ensureC(Input("bad", _target.identity), _target, "actor", m).isFaillure shouldBe true
      LocalPublication.decodeInputC(Json.obj("requestIdentity" -> Json.fromString("bad"),
        "targetIdentity" -> Json.fromString(_target.identity), "argv" -> Json.arr(Json.fromString("publish"))))
        .isFaillure shouldBe true
      Then("no successful publication is recorded")
      m.rows.values.exists(_.hcursor.get[String]("status").toOption.contains("published")) shouldBe false
    }

    "collect an absent request without process execution or source observation" in {
      Given("a collector with no retained request")
      val m = new Memory { override def sourceC(t: Target) = fail("Collection must not inspect source") }
      When("collection is called")
      take(LocalPublication.collectC(Input("absent", _target.identity), _target, "actor", m)) shouldBe None
      Then("no publication starts")
      m.starts shouldBe 0
    }
  }
}
