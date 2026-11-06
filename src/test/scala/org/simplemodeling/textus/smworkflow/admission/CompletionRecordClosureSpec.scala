/**
 * @since   Oct. 27, 2026
 * @version Oct. 30, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.parser
import java.net.URLClassLoader
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.CandidateWorkflowAbi
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.GivenWhenThen
import SplitPhaseExplicitPartitionFixture.take

/** Exact record contracts and connected owning-operation fixtures. Fixture
  * execution is distinct from installed runtime acceptance.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class CompletionRecordClosureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val before = "# Stage facts\n\nExisting accepted work stays here.\n"
  private val facts = Vector("accepted-validation@7", "independent-review@9")
  private val projectedRecord = CompletionRecords.projection(before, "spec-session", "step", "12.1", facts)
  private val file = CompletionRecords.File("docs/stage.md", "100755", "a" * 40, before, projectedRecord, 1)
  private val plan = CompletionRecords.Plan("spec-session", "step", "12.1", "b" * 40, facts, Vector(file))
  private def blob(s: String) = GitBlobContent(s.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector, Some(s))
  private val change = GitTreeChange("100755", "100755", Some(take(GitObjectId.parseC("a" * 40))),
    Some(take(GitObjectId.parseC("c" * 40))), "M", take(GitPath.parseC(file.path)))
  private val blobs = Map("a" * 40 -> blob(before), "c" * 40 -> blob(projectedRecord))

  "Completion record preparation" should {
    "derive a distinct finite ordered read selection only from configured exact record files" in {
      val paths = Vector("docs/stage.md", "docs/phase.md")
      val selected = take(CompletionRecords.readSelectionC(paths))
      selected.identity.value shouldBe "completion-record-files"
      selected.paths.map(_.value) shouldBe paths
      selected.message shouldBe None
      Vector(Vector.empty[String], paths ++ paths, Vector("src/main"), Vector("../outside.md"),
        (1 to 129).map(n => "docs/record-" + n + ".md").toVector)
        .foreach(value => CompletionRecords.readSelectionC(value).isSuccess shouldBe false)
      CompletionRecords.readSelectionC(null).isSuccess shouldBe false
    }
    "select only a complete unchanged staging shape without treating that shape as execution proof" in {
      val snapshot = GoalPhaseOrderedProgression.Snapshot("a" * 40, "b" * 40)
      val paths = Vector("docs/stage.md", "docs/phase.md")
      val p = GoalPhaseNativeRecordingCandidate.Payload("7", snapshot, paths, paths, paths, None, Vector.empty)
      GoalPhaseNativeRecordingCandidate.capturePreparationPending(p) shouldBe true
      Vector(p.copy(stagedPaths = paths.take(1)), p.copy(stagedPaths = paths.reverse),
        p.copy(selectedPaths = paths.take(1)), p.copy(assignedPaths = paths.reverse),
        p.copy(selectedPaths = Vector.empty, stagedPaths = Vector.empty),
        p.copy(changedPaths = paths), p.copy(finalCandidate = Some(snapshot)), p.copy(records = Some(plan)))
        .foreach(value => GoalPhaseNativeRecordingCandidate.capturePreparationPending(value) shouldBe false)
      GoalPhaseNativeRecordingCandidate.resumable(p.copy(records = Some(plan))) shouldBe true
      GoalPhaseNativeRecordingCandidate.resumable(p.copy(finalCandidate = Some(snapshot))) shouldBe false
    }
    "select closure continuation states declared by the actual composite execution definitions" in {
      val phase = take(GoalPhaseRuntimeCatalog.loadC())
      val task = take(GoalTaskRuntimeCatalog.loadC())
      val delegate = take(DelegateTaskRuntimeCatalog.loadC())
      CompletionRecordContinuation.phaseProgression shouldBe "PhaseClosing"
      CompletionRecordContinuation.taskProgression shouldBe "Running"
      phase.workflow.workflow.states should contain (CompletionRecordContinuation.phaseProgression)
      val location = GoalPhaseRuntimeCatalog.getClass.getProtectionDomain.getCodeSource.getLocation
      val loader = new URLClassLoader(Array(location), null)
      try {
        val raw = take(GoalPhaseRuntimeCatalog.readResourceC(loader, "META-INF/cozy/statemachine-workflow-abi.json"))
        Vector(task, delegate).foreach { definition =>
          val selected = take(CandidateWorkflowAbi.parseSelectedC(raw, phase.producer,
            "src/main/cozy/sm-workflow.cml", definition.workflowIdentity.value, definition.workflowRevision.value))
          selected.workflow.states should contain (CompletionRecordContinuation.taskProgression)
        }
      } finally loader.close()
    }
    "commit selected Phase records through the explicit release owner before native Terminal" in {
      new GoalPhaseNativeFindingRecordingSpec().qualifyPhaseCompletionRecords(this)
    }
    "require real registered repository record selection and preserve actual native merge parents" in {
      new CompletionRecordRepositoryFixture().qualify(this)
    }
    "preserve a complete large Unicode record through normal Git capture and File closure" in {
      Given("a configured record whose accepted narrative exceeds the former application character ceiling")
      When("normal RepositorySync captures and projects that record through typed Git and File operations")
      new CompletionRecordRepositoryFixture(largeRecord = true).qualify(this)
      Then("the complete original narrative and actual accepted completion remain in the converged record")
    }
    "continue a known failed delivery publication and collect unresolved delivery without replay" in {
      new LocalPublicationSpec().qualifyDeliveryContinuation(this)
    }
    "close actual selected Step records before next-Step issuance without rerunning accepted checks" in {
      new GoalPhaseNativeFindingRecordingSpec().qualifyCompletionRecords(this)
    }
    "select only configured exact scope destinations without inferring a Phase or journal path" in {
      val policy = take(CompletionRecords.decodeC(parser.parse("""{"targets":[{"family":"step","scope":"12.1","paths":["docs/stage.md"]}]}""").toOption.get))
      CompletionRecords.selected(Some(policy), "step", "12.1") shouldBe Vector("docs/stage.md")
      CompletionRecords.selected(Some(policy), "phase", "12") shouldBe empty
      CompletionRecords.selected(None, "step", "12.1") shouldBe empty
      CompletionRecords.validateC(policy.copy(targets = policy.targets ++ policy.targets)).isSuccess shouldBe false
      CompletionRecords.validateC(policy.copy(targets = Vector(policy.targets.head.copy(paths = Vector("../outside.md"))))).isSuccess shouldBe false
    }
    "retain source acceptance and mode while admitting only the exact projected record bytes" in {
      CompletionRecords.valid(plan) shouldBe true
      val admitted = take(CompletionRecords.admitC(plan, Vector(change), blobs))
      admitted.map(_.path) shouldBe Vector(file.path)
      CompletionRecords.admitC(plan, Vector(change.copy(newMode = "100644")), blobs).isSuccess shouldBe false
      CompletionRecords.admitC(plan, Vector(change), blobs.updated("c" * 40, blob(projectedRecord + "unissued semantic change\n"))).isSuccess shouldBe false
      CompletionRecords.admitC(plan, Vector(change.copy(path = take(GitPath.parseC("docs/unrelated.md")))), blobs).isSuccess shouldBe false
      CompletionRecords.admitC(plan, Vector.empty, blobs).isSuccess shouldBe false
      plan.acceptedTree shouldBe "b" * 40
      projectedRecord should startWith (before)
      projectedRecord should not include ("final commit:")
      projectedRecord should not include ("Phase 12 completed")
    }
    "recognize exact application and refuse changed source rather than overwriting or inventing closure" in {
      CompletionRecords.fileState(file, Some(before)) shouldBe "ready"
      CompletionRecords.fileState(file, Some(projectedRecord)) shouldBe "applied"
      CompletionRecords.fileState(file, Some(before + "concurrent edit")) shouldBe "conflict"
      CompletionRecords.fileState(file, None) shouldBe "conflict"
      CompletionRecords.projection(projectedRecord, plan.session, plan.family, plan.scope, plan.evidence) shouldBe projectedRecord
      CompletionRecords.valid(plan.copy(files = Vector(file.copy(after = projectedRecord + "unissued text")))) shouldBe false
    }
  }
}
