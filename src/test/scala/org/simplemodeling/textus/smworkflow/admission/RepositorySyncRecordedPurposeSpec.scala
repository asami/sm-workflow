package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import RepositorySyncPurpose as P
import StandaloneFixExecution as S
import SplitPhaseExplicitPartitionFixture.take

/** Pure selection over native owner payloads; fixtures claim no retained or
  * executed acceptance. The native reader remains responsible for provenance.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncRecordedPurposeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val head = "a" * 40
  private val tree = "b" * 40
  private val files = Vector("docs/a.md", "docs/b.md").map(path =>
    P.File(path, "M", " ", Some("original\n"), Some(path + " changed\n"), Some(path + " changed\n"), None))
  private def task(id: String, paths: Vector[String], dependencies: Vector[String] = Vector.empty): BoundedWorkRuntime.Payload = {
    val input = S.Input("fixture-repository", id, "Document " + id, Vector("docs"), Vector.empty, Vector.empty,
      Vector(ContextReference("original-instruction-" + id, "1")), false, kind = "documentation", scalaLint = "off",
      task = Some(S.Task("direct-instruction", dependencies)))
    val initial = take(S.startC(input, "fixture-participant", Vector.empty))
    BoundedWorkRuntime.Payload(initial.copy(stage = "Assess", semanticReference = Some(ContextReference("implementation-" + id, "1")),
      candidate = Some(S.Candidate(head, tree, paths, "actual fixture candidate projection"))), "/fixture/repository", "main", head)
  }
  private def record(payload: BoundedWorkRuntime.Payload, ordinal: Int): P.SourceRecord =
    P.SourceRecord(P.Source("Task", "task-state-" + ordinal, "1"), "observed", GoalTaskNativeRun.stateCodec.encode(payload))
  private val purposeA = task("purpose-a", Vector("docs/a.md"), Vector("purpose-b"))
  private val purposeB = task("purpose-b", Vector("docs/b.md"))
  private def request: P.Request = P.Request("fixture-repository", "/fixture/repository", "main", head, tree,
    files, Vector(record(purposeA, 1), record(purposeB, 2)))

  "Recorded dirty-purpose selection" should {
    "reuse exact native Task reasons and declared ordering without manufacturing acceptance" in {
      Given("two attributable candidate projections of the same current staged snapshot in reverse dependency order")
      When("the purpose owner resolves that complete known partition")
      val result = RepositorySyncRecordedPurpose.resolve(request).get
      Then("dependency order drives the existing native content-aware owner and only grouping is supplied")
      result.groups.map(_.purpose) shouldBe Vector("Document purpose-b", "Document purpose-a")
      result.groups.map(_.identity) shouldBe Vector("recorded-task-2", "recorded-task-1")
      result.groups.last.dependencies shouldBe Vector("recorded-task-2")
      result.groups.flatMap(_.content.map(_.path)) shouldBe Vector("docs/b.md", "docs/a.md")
      result.groups.foreach(_.message should startWith("Document purpose-"))
      P.admitC(request, result).isSuccess shouldBe true
      result.excludedPaths shouldBe empty
    }

    "retain input order for independent reasons and refuse ambiguous, changed or live-intent meaning" in {
      Given("an independent known partition and incomplete or contradictory alternatives")
      val independent = request.copy(records = Vector(record(purposeA.copy(state = purposeA.state.copy(input = purposeA.state.input.copy(
        task = Some(S.Task("direct-instruction", Vector.empty))))), 1), record(purposeB, 2)))
      RepositorySyncRecordedPurpose.resolve(independent).get.groups.map(_.identity) shouldBe Vector("recorded-task-1", "recorded-task-2")
      val unknown = request.copy(files = files :+ P.File("docs/new.md", "?", "?", None, None, Some("unclassified\n"), None))
      val partial = request.copy(files = files.updated(0, files.head.copy(worktreeCode = "M", worktree = Some("later unstaged meaning\n"))))
      val overlap = request.copy(records = Vector(record(purposeA, 1), record(purposeB.copy(state = purposeB.state.copy(
        candidate = Some(purposeB.state.candidate.get.copy(paths = Vector("docs/a.md", "docs/b.md"))))), 2)))
      val cycle = request.copy(records = Vector(record(purposeA, 1), record(purposeB.copy(state = purposeB.state.copy(input = purposeB.state.input.copy(
        task = Some(S.Task("direct-instruction", Vector("purpose-a")))))), 2)))
      val intent = request.copy(records = Vector(record(purposeA.copy(state = purposeA.state.copy(stage = "CommitIntent")), 1), record(purposeB, 2)))
      When("unknown/shared/partial content, missing dependencies, changed snapshots and live effects are considered")
      val unresolved = Vector(unknown, partial, overlap, cycle, intent, request.copy(records = Vector.empty),
        request.copy(parent = "c" * 40), request.copy(indexTree = "d" * 40), request.copy(repository = "foreign"),
        request.copy(records = Vector(record(purposeA, 1))))
      Then("none becomes automatic grouping, success or permission to replay a retained effect")
      unresolved.foreach(RepositorySyncRecordedPurpose.resolve(_) shouldBe None)
    }
  }
}
