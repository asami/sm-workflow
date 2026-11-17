package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.GivenWhenThen
import RepositorySyncPurpose as P
import RepositorySyncPurpose.given
import RepositorySyncSessionWorkspaceReceipt as R
import SplitPhaseExplicitPartitionFixture.take

/**
  * @since   Nov. 17, 2026
  * @version Nov. 17, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncSessionWorkspaceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val progress = RepositorySyncPurposeRepairScopeFixture.progress
  private val candidate = progress.candidate.get
  private val source = take(WorkspaceId.parseC("sm-workflow-repository"))
  private val alias = take(WorkspaceId.parseC("purpose-slot-1"))
  private val id = take(GitSelectionId.parseC("purpose-test"))
  private val paths = candidate.changes.map(row => take(GitPath.parseC(row.path)))
  private val selection = GitSelectionParameters(id, paths, Some(candidate.message))
  private val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(0),
    ProcessExecutionCapture(Vector.empty, 0, false), ProcessExecutionCapture(Vector.empty, 0, false), Vector.empty, 2, "git")
  private val producer = take(P.producerC(candidate))
  private val materialization = GitExecutionResult(GitTarget(source, Some(selection)),
    GitOperation.MaterializePurposeCandidate(id, producer, alias), GitExecutionOutcome.Succeeded, Some(()), process, paths)
  private val root = GitExecutionResult(GitTarget(alias), GitOperation.Root, GitExecutionOutcome.Succeeded,
    Some("/fixture/session/candidate"), process, Vector.empty)
  private val head = GitExecutionResult(GitTarget(alias), GitOperation.Head, GitExecutionOutcome.Succeeded,
    Some(producer.commit), process, Vector.empty)
  private val slot = P.Slot(alias.value, Vector.empty,
    GoalPhaseStepAssessment.Policy("fixture", "code", "required", "off", "off", Vector.empty))

  "Session candidate retention" should {
    "round trip actual typed materialization and observations and reject crossed fields" in {
      val material = take(R.materializationC(candidate, materialization))
      val observedRoot = take(R.rootC(root))
      val observedHead = take(R.headC(head))
      val retained = R.Materialized(candidate, material, observedRoot, observedHead)
      retained.asJson.as[R.Materialized] shouldBe Right(retained)
      take(R.restoreC(retained, alias.value)) shouldBe ((materialization, root, head))
      R.restoreC(retained, "other-workspace").isSuccess shouldBe false
      R.restoreC(retained.copy(head = observedHead.copy(operation = "root")), alias.value).isSuccess shouldBe false
      R.materializationC(candidate, materialization.copy(outcome = GitExecutionOutcome.Failed(1))).isSuccess shouldBe false
      R.rootC(root.copy(value = None)).isSuccess shouldBe false
    }
    "retain the session slot and leave historic configured-slot progress readable" in {
      val host = P.SessionWorkspace("org.simplemodeling.textus.SmWorkflow", source.value, "purpose-parent", alias.value,
        "session", "candidate", slot)
      val session = progress.copy(sessionWorkspace = Some(host))
      session.asJson.as[P.Progress] shouldBe Right(session)
      P.slot(Some(P.Policy(Vector(slot.copy(workspace = "legacy-slot")), Vector.empty)), "repository", session) shouldBe Some(slot)
      val old = progress.copy(sessionWorkspace = None)
      old.asJson.hcursor.downField("sessionWorkspace").focus shouldBe None
      old.asJson.as[P.Progress] shouldBe Right(old)
      old.asJson.mapObject(_.add("sessionWorkspace", Json.Null)).as[P.Progress].isLeft shouldBe true
    }
    "retain an unfinished ordinary build without inventing a product result" in {
      val pending = RepositorySyncProgramImpact.Group(candidate.tree, alias.value, pending = Some("build"))
      val retained = progress.copy(ordinaryGroups = Map("ordinary-project-full" -> pending))
      retained.asJson.as[P.Progress] shouldBe Right(retained)
      retained.ordinaryGroups("ordinary-project-full").successful shouldBe false
      retained.receipts shouldBe progress.receipts
      retained.asJson.mapObject(_.add("ordinaryGroups", Json.Null)).as[P.Progress].isLeft shouldBe true
    }
    "preserve the historical managed binding without allocating or changing its evidence" in {
      Given("a historical managed candidate with an unobserved materialization and retained attempt evidence")
      val reference = org.goldenport.cncf.workspace.SessionCandidateWorkspaceRecord(
        "org.simplemodeling.textus.SmWorkflow", source.value, "purpose-parent", alias.value,
        "historical-session", "historical-candidate")
      val binding = RepositorySyncPurposeWorkspace.Binding(reference, slot)
      val retained = progress.copy(managedWorkspace = Some(binding))
      // This is the actual former wire shape: neither new field existed.
      val historical = retained.asJson.mapObject(_.remove("ordinaryGroups").remove("sessionWorkspace"))
      When("the stored historical shape is read by the integrated owner")
      val reopened = historical.as[P.Progress].toOption.get
      Then("the same binding and evidence survive, while a null or mixed binding is refused")
      reopened shouldBe retained
      reopened.effects shouldBe progress.effects
      reopened.receipts shouldBe progress.receipts
      reopened.attempt shouldBe progress.attempt
      reopened.managedWorkspace.get.materialization shouldBe None
      P.slot(None, "repository", reopened) shouldBe Some(slot)
      P.selectedSlot(None, "repository", reopened) shouldBe Some(slot)
      historical.mapObject(_.add("managedWorkspace", Json.Null)).as[P.Progress].isLeft shouldBe true
      val session = P.SessionWorkspace(reference.component, source.value, reference.parent, alias.value,
        reference.session, reference.candidate, slot)
      retained.copy(sessionWorkspace = Some(session)).asJson.as[P.Progress].isLeft shouldBe true
    }
  }
}
