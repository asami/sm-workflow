package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, InstanceRecord}
import org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1
import org.goldenport.protocol.{Property, Request}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target
import SplitPhaseExplicitPartitionFixture.take
import RepositorySyncManagedFetch as F
import RepositorySyncNativeProgressStore as N

/** Actual managed intents and Git facts; interruption is owned by this fixture.
  * Source qualification does not attest publication, activation or live consent.
  * @since   Oct. 28, 2026
  * @version Oct. 28, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class Phase69CompletedEffectsSpec extends Phase69CompletedEffectsBehavior

/** Shared declarations register real tests on each concrete suite. */
private[smworkflow] trait Phase69CompletedEffectsBehavior extends AnyWordSpec with GivenWhenThen with org.scalatest.matchers.should.Matchers {
  private def _assert_same_instance_(actual: InstanceRecord, expected: InstanceRecord): Unit = {
    // Each managed read admits a fresh DefinitionBinding. Compare its stable
    // provenance and the complete validated persisted record, including history.
    actual.definition.sourceCorrelation shouldBe expected.definition.sourceCorrelation
    actual.definition.producerAbiIdentity shouldBe expected.definition.producerAbiIdentity
    actual.definition.workflowAbiIdentity shouldBe expected.definition.workflowAbiIdentity
    actual.definition.bootstrapAbiIdentity shouldBe expected.definition.bootstrapAbiIdentity
    actual.definition.producerRevision shouldBe expected.definition.producerRevision
    take(WorkflowInstanceRecordJsonV1.encodeC(actual)) shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(expected))
  }

  "Phase 6.9 completed effect collection" should {
    for (point <- Vector("integration-intent", "integrated")) {
      s"observe the retained fast-forward at $point without executing it again" in new Phase69ConfiguredRepositoryFixture {
        val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase69-completed-effects").toAbsolutePath), "case-")
        _with_runtime_(root, "RemoteAhead") { (owner, component, actor, prepare) =>
          prepare(_.setupC(_setup_commands_(root, "RemoteAhead").size))
          def call(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "RepositorySyncService", operation = operation,
              properties = properties), actor)
          val input = ThreeWorkflowNormalHostFixture.definition(root).repository.input.copy(
            remoteEndpointIdentity = root.resolve("upstream.git").toString)
          val session = read_value[V.RepositorySyncStartResult](take(call("startRepositorySync", List(Property("input",
            org.goldenport.record.Record.data("repositoryIdentity" -> input.repositoryIdentity, "syncMode" -> input.syncMode), None))))).sessionId
          Given("an actual public Start and an interrupted managed integration, with no manufactured receipt")
          prepare { setup =>
            val declaration = F.Declaration(_configuration_, _collection_("instances"), _collection_("continuations"), input,
              Target(root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString), runtimeCleanSync = true)
            for {
              definition <- RepositorySyncDefinitionBinding.bindC
              selected <- F.sessionC(session, declaration, definition, setup.core)
              fetched <- F.fetchC(selected, declaration, setup.core, () => Consequence.unit)
              store <- N.bindC(selected, declaration, setup.core, key =>
                if (key == point) Consequence.stateConflict("Specification-owned interruption at " + point) else Consequence.unit)
              preserved <- RepositorySyncNativePreservation.preserveC(store, fetched._1, fetched._2.after.get)
              attempted = RepositorySyncNativeIntegration.integrateC(store, preserved)
              _ <- Consequence.success { attempted.isSuccess shouldBe false; () }
            } yield ()
          }
          When("the public owner continues the same Session twice")
          val first = call("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(session))
          if (point == "integration-intent") {
            Then("unestablished execution remains UNKNOWN and no fast-forward is retried")
            first.isSuccess shouldBe false
            first.toString should include("outcome is not established")
            call("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(session)).isSuccess shouldBe false
            prepare(_.observeC().map { observed => observed.counts.get.remoteAhead shouldBe 1; () })
          } else {
            Then("the completed fast-forward is collected and native closure stays immutable")
            read_value[V.RepositorySyncAdvanceResult](take(first)).kind shouldBe "Terminal"
            var original = Option.empty[org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord]
            prepare(_.currentC(InstanceIdentity(session)).map(value => { original = Some(value); () }))
            val repeated = read_value[V.RepositorySyncAdvanceResult](take(call("advanceRepositorySync",
              NativeWorkflowOperationInputFixture.advance(session))))
            repeated.kind shouldBe "Terminal"
            prepare(_.currentC(InstanceIdentity(session)).map(value => _assert_same_instance_(value, original.get)))
          }
          prepare { setup => for {
            intent <- setup.nativeStageC(input, InstanceIdentity(session), root, "integration-intent")
            pushed <- setup.nativeStageC(input, InstanceIdentity(session), root, "push-intent")
            committed <- setup.nativeStageC(input, InstanceIdentity(session), root, "commit-intent")
          } yield { intent.get.payload.method shouldBe Some("fast-forward"); pushed shouldBe None; committed shouldBe None; () } }
          Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        }
      }
    }

    "retain the local result when the remote moves after fetch and reject push replay" in new Phase69ConfiguredRepositoryFixture {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase69-completed-effects").toAbsolutePath), "remote-")
      _with_runtime_(root, "LocalAhead") { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "LocalAhead").size))
        def call(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "RepositorySyncService", operation = operation,
            properties = properties), actor)
        val input = ThreeWorkflowNormalHostFixture.definition(root).repository.input.copy(
          remoteEndpointIdentity = root.resolve("upstream.git").toString)
        val session = read_value[V.RepositorySyncStartResult](take(call("startRepositorySync", List(Property("input",
          org.goldenport.record.Record.data("repositoryIdentity" -> input.repositoryIdentity, "syncMode" -> input.syncMode), None))))).sessionId
        var local = ""
        Given("the runtime fetched the original tip and another actor then advances the controlled remote")
        prepare { setup =>
          val declaration = F.Declaration(_configuration_, _collection_("instances"), _collection_("continuations"), input,
            Target(root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString), runtimeCleanSync = true)
          for {
            definition <- RepositorySyncDefinitionBinding.bindC
            selected <- F.sessionC(session, declaration, definition, setup.core)
            _ <- F.fetchC(selected, declaration, setup.core, () => Consequence.unit)
            observed <- setup.observeC()
          } yield { local = observed.head; () }
        }
        // This is a second fixture actor's actual change, never Workflow repair.
        val peer = root.resolve("later-peer")
        take(_run_setup_command_(Vector("clone", root.resolve("upstream.git").toString, peer.toString)))
        Files.writeString(peer.resolve("later.md"), "later remote purpose\n")
        take(_run_setup_command_(Vector("-C", peer.toString, "add", "--", "later.md")))
        take(_run_setup_command_(Vector("-C", peer.toString, "-c", "user.name=Fixture", "-c",
          "user.email=fixture@example.invalid", "commit", "-m", "test: later controlled remote purpose")))
        take(_run_setup_command_(Vector("-C", peer.toString, "push", root.resolve("upstream.git").toString, "HEAD:refs/heads/main")))
        When("the original public continuation reaches a real non-force push rejection")
        call("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(session)).isSuccess shouldBe false
        var original = Option.empty[org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord]
        prepare(_.currentC(InstanceIdentity(session)).map(value => { original = Some(value); () }))
        Then("repeated continuation observes the unresolved intent without another push or losing local work")
        call("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(session)).isSuccess shouldBe false
        prepare { setup => for {
          current <- setup.currentC(InstanceIdentity(session))
          observed <- setup.observeC()
          remote <- RepositorySyncManagedGitObservation.remoteTipC(setup.core, "main")
          pending <- setup.nativeStageC(input, InstanceIdentity(session), root, "push-intent")
          pushed <- setup.nativeStageC(input, InstanceIdentity(session), root, "push-observed")
          committed <- setup.nativeStageC(input, InstanceIdentity(session), root, "commit-intent")
        } yield {
          _assert_same_instance_(current, original.get)
          observed.head shouldBe local
          observed.dirty shouldBe empty
          remote should not be local
          pending should not be empty
          pushed shouldBe None
          committed shouldBe None
          ()
        } }
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      }
    }

  }
}
