/**
 * @since   Oct. 27, 2026
 * @version Nov.  2, 2026
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
import org.scalatest.GivenWhenThen
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import WorkflowManagedProcessExecution as E
import RepositorySyncManagedGitObservation as G
import SplitPhaseExplicitPartitionFixture.take

/** Actual managed Git and File operations in isolated specification-owned repos.
  * These are preparation observations, never genuine external work/authority.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class CommitHygieneSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  private val _path = "model.cml"
  private val _paths = Vector(_path, "unrelated.txt")
  private val _document = "docs/spec/guide.md"
  private val _staged_sibling = "docs/spec/staged.md"
  private val _unstaged_sibling = "docs/spec/unstaged.md"
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
  private def _document_episode(paths: Vector[String], selected: String = _document,
    denied: Set[String] = Set.empty, additionalSelected: Vector[String] = Vector.empty)(task: (Path, (Setup => Consequence[Unit]) => Unit) => Unit): Unit = {
    val root = Files.createTempDirectory(Files.createDirectories(
      Path.of("target/sm-workflow/work/commit-hygiene").toAbsolutePath), "document-").toRealPath()
    val repository = Files.createDirectories(root.resolve("repository"))
    Files.createDirectories(repository.resolve(selected).getParent)
    val original = ThreeWorkflowNormalHostFixture.definition(root)
    val host = original.copy(goal = original.goal.copy(candidatePaths = paths))
    val target = Programs.Target(repository, "main", "origin", "main", root.resolve("upstream.git").toString)
    val git = TypedGitHostPlanFixture.plan(target, goalPaths = paths,
      goalMessages = host.goal.closure.programs.map(p => "Goal Step " + p.step),
      goalSelections = Vector(Vector(selected) ++ additionalSelected), denied = denied)
    val process = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(git))
    _with_runtime_(root, "Goal", Some(host), Map("textus.subsystem.process-execution.plan" -> process.noSpaces)) { (_, _, _, prepare) =>
      prepare(_.setupC(_setup_commands_(root, "Goal").size))
      task(repository, prepare)
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
    "complete an actual directory-registered document Step without repeating accepted checks or review" in {
      new GoalPhaseNativeFindingRecordingSpec().qualifyDocumentCommitHygiene()
    }
    "retain the selected document partial-staging refusal at ordinary Closing" in {
      new GoalPhaseNativeFindingRecordingSpec().qualifyDocumentPartialStaging()
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
    "narrow a covering document selection and preserve every staged and unstaged sibling through one commit" in {
      Given("a selected executable Markdown guide covered by docs/spec with unrelated same-directory work")
      val paths = Vector("docs/spec", _staged_sibling)
      _document_episode(paths) { (repository, prepare) =>
        val file = repository.resolve(_document)
        val original = "# Guide\n\nKeep the accepted document bytes.\n\n\n"
        Files.writeString(file, original)
        file.toFile.setExecutable(true) shouldBe true
        val permissions = Files.getPosixFilePermissions(file)
        prepare { call => for {
          parent <- call.observeC()
          _ <- _stage(call, 1)
          _ = Files.writeString(repository.resolve(_staged_sibling), "staged sibling\n")
          _ <- _stage(call, 2)
          before <- G.indexTreeC(call.core).flatMap(GitObjectId.parseC)
          _ = {
            Files.writeString(repository.resolve(_staged_sibling), "unstaged sibling version\n")
            Files.writeString(repository.resolve(_unstaged_sibling), "untracked sibling\n")
            val check = take(E.checkGoalIndexC(call.core, 1, parent.head))
            When("ordinary hygiene preflights and stages only the literal guide below its covering ordinal")
            val plan = take(CommitHygiene.planC(call.core, "auto-fix", check, Vector(_document), paths, parent.tree))
            plan.corrections.map(_.stagingOrdinal) shouldBe Vector(1)
            take(CommitHygiene.applyC(call.core, plan))
            val after = take(G.indexTreeC(call.core).flatMap(GitObjectId.parseC))
            val delta = take(E.executeGitC(call.core, GitOperation.TreeChanges(before, after)).flatMap(_.valueC))
            Then("only the guide index entry changes and all unrelated worktree bytes and the guide mode survive")
            delta.map(_.path.value) shouldBe Vector(_document)
            delta.head.oldMode shouldBe "100755"
            delta.head.newMode shouldBe "100755"
            Files.getPosixFilePermissions(file) shouldBe permissions
            Files.readString(file) shouldBe original.dropRight(2)
            Files.readString(repository.resolve(_staged_sibling)) shouldBe "unstaged sibling version\n"
            Files.readString(repository.resolve(_unstaged_sibling)) shouldBe "untracked sibling\n"
            take(E.checkGoalIndexC(call.core, 1, parent.head)).findingKeys shouldBe empty
          }
          result <- E.commitGoalStepC(call.core, 1)
          _ <- result.valueC
          after <- call.observeC()
          commit <- G.commitC(call.core, after.head)
          _ = {
            Then("one selected commit has the original parent and the sibling index entry remains staged")
            commit.parents shouldBe Vector(parent.head)
            after.dirty.find(_.path == _staged_sibling).map(d => (d.index, d.worktree)) shouldBe Some(('A', 'M'))
            after.dirty.map(_.path).toSet shouldBe Set(_staged_sibling, _unstaged_sibling)
          }
        } yield () }
      }
    }
    "prefer an exact document registration over its covering directory" in {
      Given("the guide has both a directory registration and a later exact registration")
      val paths = Vector("docs/spec", _document)
      _document_episode(paths) { (repository, prepare) =>
        Files.writeString(repository.resolve(_document), "# Guide\n\n")
        prepare { call => for {
          parent <- call.observeC()
          _ <- _stage(call, 2)
          _ = {
            When("the EOF-only plan selects its retained staging ordinal")
            val check = take(E.checkGoalIndexC(call.core, 1, parent.head))
            val plan = take(CommitHygiene.planC(call.core, "auto-fix", check, Vector(_document), paths, parent.tree))
            Then("the exact registration wins and its ordinary narrowed application succeeds")
            plan.corrections.map(_.stagingOrdinal) shouldBe Vector(2)
            take(CommitHygiene.applyC(call.core, plan))
            Files.readString(repository.resolve(_document)) shouldBe "# Guide\n"
          }
        } yield () }
      }
    }
    "refuse denied document narrowing both during planning and before applying a retained plan" in {
      Given("the guide is staged through its exact registration but its covering staging grant is denied")
      val paths = Vector("docs/spec", _document)
      val denied = Set(TypedGitHostPlanFixture.capability(GitOperationKind.StageSelectedAll, Some("goal-path-1")))
      _document_episode(paths, denied = denied) { (repository, prepare) =>
        val file = repository.resolve(_document)
        Files.writeString(file, "# Guide\n\n")
        prepare { call => for {
          parent <- call.observeC()
          _ <- _stage(call, 2)
          before <- G.indexTreeC(call.core)
          _ = {
            val time = Files.getLastModifiedTime(file)
            val check = take(E.checkGoalIndexC(call.core, 1, parent.head))
            When("planning requires the denied covering selection and a previously admitted exact plan is crossed to it")
            CommitHygiene.planC(call.core, "auto-fix", check, Vector(_document), Vector("docs/spec"), parent.tree).isSuccess shouldBe false
            val exact = take(CommitHygiene.planC(call.core, "auto-fix", check, Vector(_document), paths, parent.tree))
            val deniedPlan = exact.copy(corrections = exact.corrections.map(_.copy(stagingOrdinal = 1)))
            CommitHygiene.applyC(call.core, deniedPlan).isSuccess shouldBe false
            Then("all bytes, timestamps, index entries and the parent remain unchanged")
            Files.readString(file) shouldBe "# Guide\n\n"
            Files.getLastModifiedTime(file) shouldBe time
            take(G.indexTreeC(call.core)) shouldBe before
            take(call.observeC()).head shouldBe parent.head
          }
        } yield () }
      }
    }
    "refuse missing coverage outside ownership and partial staged Markdown before any write" in {
      Given("a fully staged guide with an EOF-only finding")
      val paths = Vector("docs/spec")
      _document_episode(paths) { (repository, prepare) =>
        val file = repository.resolve(_document)
        Files.writeString(file, "# Staged guide\n\n")
        prepare { call => for {
          parent <- call.observeC()
          _ <- _stage(call, 1)
          before <- G.indexTreeC(call.core)
          _ = {
            val check = take(E.checkGoalIndexC(call.core, 1, parent.head))
            val time = Files.getLastModifiedTime(file)
            When("the selected path has no segment coverage or is outside the Step ownership")
            Vector((Vector(_document), Vector("docs/spe")), (Vector("docs/other.md"), paths)).foreach { (owned, staging) =>
              val result = take(CommitHygiene.planC(call.core, "auto-fix", check, owned, staging, parent.tree))
              Then("planning returns a diagnostic and keeps the file and index intact")
              result.outcome shouldBe "diagnostic"
              result.corrections shouldBe empty
              Files.getLastModifiedTime(file) shouldBe time
              take(G.indexTreeC(call.core)) shouldBe before
            }
            When("the worktree receives an unrelated edit after the guide was staged")
            Files.writeString(file, "# Unstaged guide\n\n")
            val partial = take(CommitHygiene.planC(call.core, "auto-fix", check, Vector(_document), paths, parent.tree))
            Then("partial staging refuses without replacing either version")
            partial.outcome shouldBe "diagnostic"
            partial.diagnostic should include ("preserves partial staging")
            Files.readString(file) shouldBe "# Unstaged guide\n\n"
            take(G.indexTreeC(call.core)) shouldBe before
            take(call.observeC()).head shouldBe parent.head
          }
        } yield () }
      }
    }
    "re-admit every narrowed correction before writing the first file" in {
      Given("two real staged EOF-only documents with exact grants and a denied covering grant")
      val paths = Vector("docs/spec", _document, _staged_sibling)
      val denied = Set(TypedGitHostPlanFixture.capability(GitOperationKind.StageSelectedAll, Some("goal-path-1")))
      _document_episode(paths, denied = denied, additionalSelected = Vector(_staged_sibling)) { (repository, prepare) =>
        Vector(_document, _staged_sibling).foreach(path => Files.writeString(repository.resolve(path), "# Staged document\n\n"))
        prepare { call => for {
          parent <- call.observeC()
          _ <- _stage(call, 2)
          _ <- _stage(call, 3)
          before <- G.indexTreeC(call.core)
          _ = {
            val check = take(E.checkGoalIndexC(call.core, 1, parent.head))
            val plan = take(CommitHygiene.planC(call.core, "auto-fix", check, Vector(_document, _staged_sibling), paths, parent.tree))
            plan.corrections.size shouldBe 2
            val deniedSecond = plan.copy(corrections = plan.corrections.updated(1, plan.corrections(1).copy(stagingOrdinal = 1)))
            val times = plan.corrections.map(c => c.path -> Files.getLastModifiedTime(repository.resolve(c.path))).toMap
            When("application rechecks a retained plan whose second narrowed selection is denied")
            CommitHygiene.applyC(call.core, deniedSecond).isSuccess shouldBe false
            Then("the first file is also untouched and no index entry has changed")
            plan.corrections.foreach { correction =>
              Files.readString(repository.resolve(correction.path)) shouldBe "# Staged document\n\n"
              Files.getLastModifiedTime(repository.resolve(correction.path)) shouldBe times(correction.path)
            }
            take(G.indexTreeC(call.core)) shouldBe before
            take(call.observeC()).head shouldBe parent.head
          }
        } yield () }
      }
    }
    "refuse Markdown in meaning-sensitive directories before file or index changes" in {
      Vector("test", "tests", "fixtures", "expected", "golden", "snapshots").foreach { segment =>
        Given("an EOF-only Markdown file beneath the excluded " + segment + " segment")
        val path = "docs/" + segment + "/guide.md"
        val paths = Vector("docs")
        _document_episode(paths, path) { (repository, prepare) =>
          val file = repository.resolve(path)
          Files.writeString(file, "# Intentional data\n\n")
          prepare { call => for {
            parent <- call.observeC()
            _ <- _stage(call, 1)
            before <- G.indexTreeC(call.core)
            _ = {
              val time = Files.getLastModifiedTime(file)
              val check = take(E.checkGoalIndexC(call.core, 1, parent.head))
              When("automatic hygiene considers the actual selected finding")
              val plan = take(CommitHygiene.planC(call.core, "auto-fix", check, Vector(path), paths, parent.tree))
              Then("its unsupported-path diagnostic retains the original bytes and index")
              plan.outcome shouldBe "diagnostic"
              plan.corrections shouldBe empty
              Files.readString(file) shouldBe "# Intentional data\n\n"
              Files.getLastModifiedTime(file) shouldBe time
              take(G.indexTreeC(call.core)) shouldBe before
            }
          } yield () }
        }
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
    "exclude CRLF empty data unsupported suffixes and other whitespace corrections" in {
      Given("text outside the complete LF-only nonempty suffix contract and unsupported paths")
      val textCases = Vector[(String, Option[String])](
        "body\r\n\r\n" -> None,
        "\n\n" -> None,
        "body\u0000\n\n" -> None,
        "body \n" -> None)
      val pathCases = Vector(
        "expected.txt" -> false,
        "src/test/resources/expected.cml" -> false,
        "fixtures/model.cml" -> false,
        "../model.cml" -> false,
        "README.md" -> false,
        "src/main/guide.md" -> false,
        "docs/spec/guide.md" -> true)
      When("the bounded text and path rules classify them")
      val corrected = textCases.map(value => CommitHygiene.corrected(value._1))
      val supported = pathCases.map(value => CommitHygiene.supported(value._1))
      Then("each unsupported value is retained without a correction")
      corrected shouldBe textCases.map(_._2)
      supported shouldBe pathCases.map(_._2)
    }
  }
}
