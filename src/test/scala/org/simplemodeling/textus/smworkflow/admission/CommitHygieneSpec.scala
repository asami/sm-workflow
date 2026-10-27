/**
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.config.ComponentInitializationParameters
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentOrigin}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.goldenport.cncf.processexecution.*
import org.scalatest.wordspec.AnyWordSpec
import WorkflowManagedProcessExecution as E
import RepositorySyncManagedGitObservation as G
import SplitPhaseExplicitPartitionFixture.take

/** Actual managed Git and File operations in isolated specification-owned repos.
  * These are preparation observations, never genuine external work/authority.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class CommitHygieneSpec extends AnyWordSpec with ThreeWorkflowManagedRepositoryFixture {
  private val _path = "model.cml"
  private val _paths = Vector(_path, "unrelated.txt")
  override protected def goal_selected_paths(goal: ThreeWorkflowRuntimeParameters.Goal): Vector[Vector[String]] =
    Vector(Vector(_path))
  private def _episode(task: (Path, (Setup => Consequence[Unit]) => Unit) => Unit): Unit = {
    val root = Files.createTempDirectory(Files.createDirectories(
      Path.of("target/sm-workflow/work/commit-hygiene").toAbsolutePath), "episode-").toRealPath()
    val original = ThreeWorkflowNormalHostFixture.definition(root)
    val host = original.copy(goal = original.goal.copy(candidatePaths = _paths))
    _with_runtime_(root, "Goal", Some(host)) { (_, _, _, prepare) =>
      prepare(_.setupC(_setup_commands_(root, "Goal").size))
      task(root.resolve("repository"), prepare)
    }
  }
  private def _resolveMode(value: Option[String] = None): Consequence[String] = {
    val values = value.map(mode => CommitHygiene.declaration.name -> ConfigurationValue.StringValue(mode)).toMap
    val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(name = "commit-hygiene-parameters", version = None,
      configuration = ResolvedConfiguration(Configuration(values), ConfigurationTrace.empty),
      aliasResolver = AliasResolver.empty, runMode = RunMode.Command))
    val id = SmWorkflowComponent.componentId
    val descriptor = ComponentDescriptor(name = Some(id.name), componentName = Some(id.name),
      version = Some("0.1.0"), schemaVersion = Some(3), componentId = Some(id))
    var resolved = Option.empty[String]
    val factory = new SmWorkflowConfiguredComponentFactory(Vector(CommitHygiene.declaration), Vector.empty,
      (_, parameters, _) => CommitHygiene.resolveC(parameters).map { mode =>
        resolved = Some(mode)
        Vector(SmWorkflowComponent.GoalPhaseStepReviewServiceService)
      })
    factory.primaryFactory.createPrimaryC(ComponentCreate(subsystem, ComponentOrigin.Builtin, Vector(descriptor)))
      .flatMap(_ => resolved.map(Consequence.success).getOrElse(
        Consequence.configurationInvalid("Declared hygiene mode was not resolved")))
  }
  private def _stage(call: Setup, ordinal: Int): Consequence[Unit] = E.stageGoalCandidateC(call.core, ordinal).flatMap(_.valueC)
  private def _plan(call: Setup, mode: String, parent: G.Observed, repeated: Boolean = false): CommitHygiene.Record = {
    val check = take(E.checkGoalIndexC(call.core, 1, parent.head))
    take(CommitHygiene.planC(call.core, mode, check, Vector(_path), _paths, parent.tree, repeated))
  }

  "Runtime commit hygiene" should {
    "complete the actual ordinary Step closure in omitted and explicit auto-fix without another semantic assignment" in {
      new GoalPhaseNativeFindingRecordingSpec().qualifyCommitHygiene()
    }
    "resolve omitted and explicit modes through the typed configuration contract and reject invalid values" in {
      take(_resolveMode()) shouldBe "auto-fix"
      take(_resolveMode(Some("auto-fix"))) shouldBe "auto-fix"
      take(_resolveMode(Some("error"))) shouldBe "error"
      Vector("", "AUTO-FIX", "ignore", "auto-fix ").foreach(value => _resolveMode(Some(value)).isSuccess shouldBe false)
      CommitHygiene.resolveC(ComponentInitializationParameters.empty).isSuccess shouldBe false
    }
    "correct only the selected LF suffix preserve mode and unrelated staging and make one native commit" in {
      _episode { (repository, prepare) =>
        val file = repository.resolve(_path)
        val original = "component Example {\n  purpose = \"Keep bytes\"\n}\n\n\n"
        Files.writeString(file, original)
        file.toFile.setExecutable(true) shouldBe true
        val mode = Files.getPosixFilePermissions(file)
        Files.writeString(repository.resolve("unrelated.txt"), "unrelated staged bytes\n")
        prepare { call => for {
          parent <- call.observeC()
          _ <- _stage(call, 1)
          _ <- _stage(call, 2)
          _ = {
            val plan = _plan(call, take(_resolveMode()), parent)
            plan.outcome shouldBe "planned"
            plan.corrections.map(_.path) shouldBe Vector(_path)
            take(CommitHygiene.applyC(call.core, plan))
            val modified = Files.getLastModifiedTime(file)
            // Exact completed postconditions are observed, never rewritten.
            take(CommitHygiene.applyC(call.core, plan))
            Files.getLastModifiedTime(file) shouldBe modified
            Files.getPosixFilePermissions(file) shouldBe mode
            Files.readString(file) shouldBe original.dropRight(2)
            Files.readString(repository.resolve("unrelated.txt")) shouldBe "unrelated staged bytes\n"
            take(E.checkGoalIndexC(call.core, 1, parent.head)).findingKeys shouldBe empty
          }
          result <- E.commitGoalStepC(call.core, 1)
          _ <- result.valueC
          after <- call.observeC()
          commit <- G.commitC(call.core, after.head)
          _ = {
            commit.parents shouldBe Vector(parent.head)
            after.head should not be parent.head
            // Unrelated staged content remains staged outside the selected commit.
            after.dirty.map(_.path) shouldBe Vector("unrelated.txt")
            after.dirty.head.worktree shouldBe ' '
          }
        } yield () }
      }
    }
    "leave error-mode findings and clean inputs byte-for-byte untouched without a commit" in {
      _episode { (repository, prepare) =>
        val file = repository.resolve(_path)
        Files.writeString(file, "component Example {}\n\n")
        prepare { call => for {
          parent <- call.observeC()
          _ <- _stage(call, 1)
          index <- G.indexTreeC(call.core)
          _ = {
            val before = Files.getLastModifiedTime(file)
            val record = _plan(call, "error", parent)
            record.mode shouldBe "error"
            record.outcome shouldBe "diagnostic"
            record.corrections shouldBe empty
            Files.readString(file) shouldBe "component Example {}\n\n"
            Files.getLastModifiedTime(file) shouldBe before
            take(G.indexTreeC(call.core)) shouldBe index
            take(call.observeC()).head shouldBe parent.head
          }
          _ = Files.writeString(file, "component Example {}\n")
          _ <- _stage(call, 1)
          _ = {
            val before = Files.getLastModifiedTime(file)
            _plan(call, "auto-fix", parent).outcome shouldBe "clean"
            Files.getLastModifiedTime(file) shouldBe before
            take(call.observeC()).head shouldBe parent.head
          }
        } yield () }
      }
    }
    "refuse a partial staged CML suffix without changing index worktree or unrelated edits" in {
      _episode { (repository, prepare) =>
        val file = repository.resolve(_path)
        Files.writeString(file, "component Staged {}\n\n")
        prepare { call => for {
          parent <- call.observeC()
          _ <- _stage(call, 1)
          index <- G.indexTreeC(call.core)
          _ = {
            Files.writeString(file, "component Unstaged {}\n\n")
            Files.writeString(repository.resolve("unrelated.txt"), "preserve unrelated unstaged\n")
            val time = Files.getLastModifiedTime(file)
            val record = _plan(call, "auto-fix", parent)
            record.outcome shouldBe "diagnostic"
            record.diagnostic should include ("preserves partial staging")
            record.corrections shouldBe empty
            Files.readString(file) shouldBe "component Unstaged {}\n\n"
            Files.getLastModifiedTime(file) shouldBe time
            Files.readString(repository.resolve("unrelated.txt")) shouldBe "preserve unrelated unstaged\n"
            take(G.indexTreeC(call.core)) shouldBe index
            take(call.observeC()).head shouldBe parent.head
          }
        } yield () }
      }
    }
    "retain a bounded remaining-finding result and refuse changed third-value sources" in {
      _episode { (repository, prepare) =>
        val file = repository.resolve(_path)
        Files.writeString(file, "component Example {}\n\n")
        prepare { call => for {
          parent <- call.observeC()
          _ <- _stage(call, 1)
          index <- G.indexTreeC(call.core)
          _ = {
            _plan(call, "auto-fix", parent, repeated = true).outcome shouldBe "remaining-findings"
            val plan = _plan(call, "auto-fix", parent)
            Files.writeString(file, "component Concurrent {}\n")
            CommitHygiene.applyC(call.core, plan).isSuccess shouldBe false
            Files.readString(file) shouldBe "component Concurrent {}\n"
            take(G.indexTreeC(call.core)) shouldBe index
            take(call.observeC()).head shouldBe parent.head
          }
        } yield () }
      }
    }
    "exclude CRLF empty data non-CML and other whitespace corrections" in {
      CommitHygiene.corrected("body\r\n\r\n") shouldBe None
      CommitHygiene.corrected("\n\n") shouldBe None
      CommitHygiene.corrected("body\u0000\n\n") shouldBe None
      CommitHygiene.corrected("body \n") shouldBe None
      CommitHygiene.supported("expected.txt") shouldBe false
      CommitHygiene.supported("src/test/resources/expected.cml") shouldBe false
      CommitHygiene.supported("fixtures/model.cml") shouldBe false
      CommitHygiene.supported("../model.cml") shouldBe false
    }
  }
}
