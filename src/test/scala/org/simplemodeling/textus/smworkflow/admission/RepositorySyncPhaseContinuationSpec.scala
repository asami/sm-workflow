/**
 * @since   Nov. 17, 2026
 * @version Nov. 17, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target
import RepositorySyncManagedFetch as F
import RepositorySyncNativeProgressStore as N
import RepositorySyncNativeMergeValidation as C
import RepositorySyncValidationContinuation as VC
import RepositorySyncPushContinuation as PC
import SplitPhaseExplicitPartitionFixture.take

/** Existing native owners issue every intent/result in fresh admitted local
  * fixtures. Explicit fixture evidence is not live Human consent. The separate
  * LocalMain suite covers actual source/local/remote cursor progression.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncPhaseContinuationSpec extends AnyWordSpec with GivenWhenThen with Phase69ConfiguredRepositoryFixture {
  private val checks = C.Policy("phase-structural-checks", 1, "explicit-no-product-tests",
    "Specification-owned clean structural candidate with no product checks", Vector.empty)
  private def root() = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase-continuation-spec").toAbsolutePath), "case-")
  private def participant(root: Path) = RepositorySyncLocalMain.Participant("repository", "sm-workflow-repository",
    root.resolve("repository").toString, "main", "origin", "main", root.resolve("upstream.git").toString)
  private def declaration(root: Path, phase: String): F.Declaration = {
    val member = participant(root)
    val plan = RepositorySyncLocalMain.Plan(member, member, linked = true)
    val hop = RepositorySyncLocalMain.Hop(plan, RepositorySyncLocalMain.Cursor(phase, None, None))
    val input = ThreeWorkflowNormalHostFixture.definition(root).repository.input.copy(remoteEndpointIdentity = member.endpoint)
    F.Declaration(_configuration_, _collection_("instances"), _collection_("continuations"), input,
      Target(root.resolve("repository"), "main", "origin", "main", member.endpoint), runtimeCleanSync = true, hop = Some(hop))
  }
  private val evidence = VC.Installation(new VC.EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: io.circe.Json) = Consequence.unit
  })
  private val pushEvidence = PC.Installation(new PC.EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: io.circe.Json) = Consequence.unit
  })

  "Phase-scoped existing continuation owners" should {
    for (phase <- Vector("source", "local", "remote")) {
      s"describe and adopt an actual resultless $phase validation intent without crossing phases" in {
        val directory = root()
        _with_runtime_(directory, "LocalAhead") { (owner, component, actor, prepare) =>
          prepare(_.setupC(_setup_commands_(directory, "LocalAhead").size))
          val session = read_value[V.RepositorySyncStartResult](take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "RepositorySyncService", operation = "startRepositorySync",
              properties = List(Property("input", Record.data("repositoryIdentity" -> ThreeWorkflowNormalHostFixture.definition(directory).repository.input.repositoryIdentity, "syncMode" -> "FULL_STATE"), None))), actor))).sessionId
          val prototype = declaration(directory, phase)
          prepare { setup =>
            for {
              initial <- RepositorySyncManagedGitObservation.observeC(setup.core)
              d = prototype.copy(hop = prototype.hop.map(h => h.copy(cursor = h.cursor.copy(
                sourceTip = Some(initial.head), localTip = Some(initial.head)))))
              core = RepositorySyncLocalMain.scopedCore(setup.core, d.hop)
              definition <- RepositorySyncDefinitionBinding.bindC
              selected <- F.sessionC(session, d, definition, core)
              fetched <- F.fetchC(selected, d, core, () => Consequence.unit)
              store <- N.bindC(selected, d, core, point => if (point == "merge-check-prepared:index-local")
                Consequence.stateConflict("SPECIFICATION-OWNED ended invocation before structural check") else Consequence.unit)
              preserved <- RepositorySyncNativePreservation.preserveC(store, fetched._1, fetched._2.after.get)
              integrated <- RepositorySyncNativeIntegration.integrateC(store, preserved)
              attempted = C.advanceIntegratedC(store, integrated, checks)
              _ = attempted.isSuccess shouldBe false
              description <- VC.describeC(session, d, definition, core, checks, _ => Consequence.unit)
              target <- VC.targetC(description.target)
              _ = target.phase shouldBe Some("local-main-" + phase)
              _ = target.successfulPrefix shouldBe empty
              _ = VC.decodeTarget(target.copy(phase = Some("local-main-crossed")).asJson).isLeft shouldBe true
              prior <- VC.sourceC(session, d, definition, core, checks, _ => Consequence.unit)
              reference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-phase-direction", "1")
              input = V.RepositorySyncValidationContinuationAdoptionInput(session, description.target, "AuthorizeNewValidation",
                "Explicit fixture terminal and uncertainty direction", reference, true, true,
                V.WorkflowExecutionEvidence(Vector(reference), Some(target.context.participantIdentity), None, None))
              adopted <- VC.adoptC(input, d, definition, core, checks, evidence, _ => Consequence.unit)
              _ = adopted.accepted shouldBe true
              now <- F.sessionC(session, d, definition, core)
              current <- N.bindC(now, d, core, _ => Consequence.unit)
              saved <- VC.selectedC(current, now.started.current)
              _ = saved.map(_.direction.target) shouldBe Some(target)
              _ = now.started.current.history.dropRight(1) shouldBe prior.intent.record.history
              oldResult <- current.loadC("validation-result-0")
              _ = oldResult shouldBe None
              _ <- VC.advanceC(current, saved.get, now.started.current, checks, None)
              untouched <- current.loadC("validation-intent-0")
              absent <- current.loadC("validation-result-0")
            } yield {
              untouched.map(_.record) shouldBe Some(prior.intent.record)
              absent shouldBe None
              ()
            }
          }
        }
      }
    }

    for (effect <- Vector(RepositorySyncNativeConvergence.Effect.Push, RepositorySyncNativeConvergence.Effect.FinalFetch)) {
      s"describe and adopt actual remote $effect failure without replaying its completed prefix" in {
        val directory = root()
        _with_runtime_(directory, "LocalAhead") { (owner, component, actor, prepare) =>
          prepare(_.setupC(_setup_commands_(directory, "LocalAhead").size))
          val input = ThreeWorkflowNormalHostFixture.definition(directory).repository.input
          val session = read_value[V.RepositorySyncStartResult](take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "RepositorySyncService", operation = "startRepositorySync",
              properties = List(Property("input", Record.data("repositoryIdentity" -> input.repositoryIdentity, "syncMode" -> input.syncMode), None))), actor))).sessionId
          val prototype = declaration(directory, "remote")
          val remote = directory.resolve("upstream.git")
          val retained = directory.resolve("temporarily-unavailable.git")
          val authority = new RepositorySyncNativeConvergence.AuthorityPort {
            def admitC(core: ActionCall.Core, context: F.StartContext, target: Target, selected: RepositorySyncNativeConvergence.Effect) = {
              if (selected == effect) Files.move(remote, retained)
              Consequence.unit
            }
          }
          try prepare { setup =>
            for {
              initial <- RepositorySyncManagedGitObservation.observeC(setup.core)
              d = prototype.copy(hop = prototype.hop.map(h => h.copy(cursor = h.cursor.copy(
                sourceTip = Some(initial.head), localTip = Some(initial.head)))))
              core = RepositorySyncLocalMain.scopedCore(setup.core, d.hop)
              definition <- RepositorySyncDefinitionBinding.bindC
              selected <- F.sessionC(session, d, definition, core)
              fetched <- F.fetchC(selected, d, core, () => Consequence.unit)
              store <- N.bindC(selected, d, core, _ => Consequence.unit)
              preserved <- RepositorySyncNativePreservation.preserveC(store, fetched._1, fetched._2.after.get)
              integrated <- RepositorySyncNativeIntegration.integrateC(store, preserved)
              checked <- C.advanceIntegratedC(store, integrated, checks)
              attempted = RepositorySyncNativeConvergence.advanceC(store, checked, authority, Some(checks))
              _ = attempted.isSuccess shouldBe false
              _ = Files.move(retained, remote)
              description <- PC.describeC(session, d, definition, core, checks, _ => Consequence.unit)
              target <- PC.targetC(description.target)
              _ = target.phase shouldBe Some("local-main-remote")
              _ = target.retainedIntent.map(_.kind) shouldBe Some(if (effect == RepositorySyncNativeConvergence.Effect.Push) "push" else "final-fetch")
              _ = PC.decodeTarget(target.copy(phase = Some("local-main-local")).asJson).isLeft shouldBe true
              before <- F.sessionC(session, d, definition, core)
              reference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-remote-direction", "1")
              adoption = V.RepositorySyncPushContinuationAdoptionInput(session, description.target, "RefreshRemoteAndContinue",
                "Explicit fixture acceptance of the ended old invocation and its literal uncertainty", reference, true, true,
                V.WorkflowExecutionEvidence(Vector(reference), Some(target.context.participantIdentity), None, None))
              accepted <- PC.adoptC(adoption, d, definition, core, checks, pushEvidence, _ => Consequence.unit)
              _ = accepted.accepted shouldBe true
              now <- F.sessionC(session, d, definition, core)
              base <- N.bindC(now, d, core, _ => Consequence.unit)
              saved <- PC.selectedC(base, now.started.current)
              pushed <- base.loadC("push-observed")
            } yield {
              saved.map(_.direction.target) shouldBe Some(target)
              now.started.current.history.dropRight(1) shouldBe before.started.current.history
              now.started.current.history.count(_.currentProgression.contains(ProgressionReference("sync.NativePushPreparing"))) shouldBe 1
              pushed.nonEmpty shouldBe (effect == RepositorySyncNativeConvergence.Effect.FinalFetch)
              ()
            }
          } finally if (Files.exists(retained)) Files.move(retained, remote)
        }
      }
    }
  }
}
