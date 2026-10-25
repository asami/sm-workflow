package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import RepositorySyncFileEffect as F
import SplitPhaseExplicitPartitionFixture.take

/** Actual typed File composition and semantic assigned-text policy.
  * Filesystem algorithms, encoding and root defenses remain producer contracts.
  * @since   Oct. 15, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncFileEffectSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _workspace = "sm-workflow-repository"
  private val paths = Set("first.txt", "second.txt")
  private val policy = RepositorySyncNativeConflictInput.Policy(paths, maximumBlobBytes = 1024)
  private def fixture(): Path = {
    val root = WorkflowFileRuntimeFixture.fresh()
    Files.writeString(root.resolve("first.txt"), "first unresolved\n", UTF_8)
    Files.writeString(root.resolve("second.txt"), "second unresolved\n", UTF_8)
    root
  }
  private def request = F.Request("actual-instance", "actual-conflict-revision", Vector(
    F.File("first.txt", Some("first unresolved\n"), "first resolved\n"),
    F.File("second.txt", Some("second unresolved\n"), "second resolved\n")))
  private def _contents(root: Path): Vector[String] = request.files.map(f => Files.readString(root.resolve(f.path), UTF_8))

  "Assigned conflict text through the normal typed File UoW" should {
    "capture observe and apply exact Unicode and literal filenames without Git metadata or a read lock" in {
      Given("an actual workspace with literal LF tab backslash colon and Unicode filenames and texts")
      val root = WorkflowFileRuntimeFixture.fresh()
      val names = Vector("line\nfeed.txt", "tab\tname.txt", "literal\\colon:日本.txt")
      val originals = names.indices.map(i => s"original $i 日本🚀\n\t\\:\n").toVector
      names.zip(originals).foreach((name, text) => Files.writeString(root.resolve(name), text, UTF_8))
      val assigned = F.Request("instance", "revision", names.map(name => F.File(name, None, "更新 é🚀\n")))
      val selected = RepositorySyncNativeConflictInput.Policy(names.toSet, maximumBlobBytes = 1024)
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace, access = Some("read-only")) { core =>
        When("the actual ReadOnly call captures and observes the assigned complete text")
        val captured = take(F.captureC(core, selected, assigned))
        val observed = take(F.observeC(core, selected, assigned.copy(files = captured.files)))
        Then("the original ordered instance revision path and text facts survive without metadata creation or mutation")
        captured.instance shouldBe assigned.instance
        captured.revision shouldBe assigned.revision
        captured.status shouldBe "captured"
        captured.files.map(_.path) shouldBe names
        captured.files.map(_.expected) shouldBe originals.map(Some.apply)
        observed.status shouldBe "ready"
        names.map(name => Files.readString(root.resolve(name), UTF_8)) shouldBe originals
        Files.exists(root.resolve(".git")) shouldBe false
      }
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace) { core =>
        val captured = take(F.captureC(core, selected, assigned))
        val selectedrequest = assigned.copy(files = captured.files)
        When("the actual Manage call applies each assigned expected-current replacement once")
        val applied = take(F.applyC(core, selected, selectedrequest))
        val observed = take(F.observeC(core, selected, selectedrequest))
        Then("actual typed results and independent readback retain all literal text and ordered coverage")
        applied.status shouldBe "applied"
        applied.files shouldBe selectedrequest.files
        observed.status shouldBe "applied"
        names.map(name => Files.readString(root.resolve(name), UTF_8)) shouldBe Vector.fill(names.size)("更新 é🚀\n")
        Files.exists(root.resolve(".git")) shouldBe false
      }
    }

    "preflight every assigned path before changing an earlier ready file" in {
      Given("an earlier ready file and a later file edited by its user after the saved proposal")
      val root = fixture()
      Files.writeString(root.resolve("second.txt"), "user resolution\n", UTF_8)
      val before = _contents(root)
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace) { core =>
        When("the actual UoW preflights and attempts the two-file edit")
        val observed = take(F.observeC(core, policy, request))
        val applied = take(F.applyC(core, policy, request))
        Then("both report conflict and the complete write set remains untouched")
        observed.status shouldBe "conflict"
        applied.status shouldBe "conflict"
        _contents(root) shouldBe before
      }
    }

    "include unchanged files but refuse replay of partially or fully applied changed replacements" in {
      Given("an assigned unchanged first file and one ready changed replacement")
      val root = fixture()
      val unchanged = request.copy(files = request.files.updated(0, request.files.head.copy(replacement = request.files.head.expected.get)))
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace) { core =>
        When("the actual UoW applies the changed replacement with unchanged coverage")
        val applied = take(F.applyC(core, policy, unchanged))
        Then("the actual result includes the original unchanged file without overwriting it")
        applied.status shouldBe "applied"
        applied.files shouldBe unchanged.files
        Files.readString(root.resolve("first.txt"), UTF_8) shouldBe "first unresolved\n"
        Files.readString(root.resolve("second.txt"), UTF_8) shouldBe "second resolved\n"
      }
      for (fully <- Vector(false, true)) {
        Given("the original changed intent with " + (if (fully) "all" else "only one") + " changed replacement already installed")
        val partial = fixture()
        Files.writeString(partial.resolve("first.txt"), request.files.head.replacement, UTF_8)
        if (fully) Files.writeString(partial.resolve("second.txt"), request.files.last.replacement, UTF_8)
        val before = _contents(partial)
        WorkflowFileRuntimeFixture.withCore(partial, workspace = _workspace) { core =>
          When("the owning call observes and attempts that original saved application again")
          val observed = take(F.observeC(core, policy, request))
          val refused = take(F.applyC(core, policy, request))
          Then("observation describes actual state while application refuses replay and preserves both files")
          observed.status shouldBe (if (fully) "applied" else "ready")
          refused.status shouldBe "conflict"
          _contents(partial) shouldBe before
        }
      }
    }

    "distinguish absent text from a present empty capture and never create a missing conflict file" in {
      Given("one present empty file and an absent second assigned leaf")
      val root = fixture()
      Files.writeString(root.resolve("first.txt"), "", UTF_8)
      Files.delete(root.resolve("second.txt"))
      val empty = request.copy(files = Vector(request.files.head.copy(expected = None)))
      val both = request.copy(files = request.files.map(_.copy(expected = None)))
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace) { core =>
        When("actual capture observes the empty file and attempts the assignment containing the absent file")
        val captured = take(F.captureC(core, policy, empty))
        val absent = F.captureC(core, policy, both)
        val applied = take(F.applyC(core, policy, request.copy(files = request.files.updated(0,
          request.files.head.copy(expected = Some(""))))))
        Then("only the empty file supplies Some text and missing capture/application cannot become a new file")
        captured.files.head.expected shouldBe Some("")
        absent.isFaillure shouldBe true
        applied.status shouldBe "conflict"
        Files.readString(root.resolve("first.txt"), UTF_8) shouldBe ""
        Files.exists(root.resolve("second.txt")) shouldBe false
      }
    }

    "enforce exact assigned paths references and semantic finite non-NUL text restrictions" in {
      Given("ordinary selected files and a finite actual semantic policy")
      val root = fixture()
      val before = _contents(root)
      val invalid = Vector(
        request.copy(instance = " instance"), request.copy(revision = ""), request.copy(files = Vector.empty),
        request.copy(files = Vector(request.files.head, request.files.head)),
        request.copy(files = Vector(request.files.head.copy(path = "unknown.txt"))),
        request.copy(files = Vector(request.files.head.copy(path = ".git/config"))),
        request.copy(files = Vector(request.files.head.copy(expected = None))),
        request.copy(files = Vector(request.files.head.copy(replacement = "NUL\u0000text"))),
        request.copy(files = Vector(request.files.head.copy(replacement = "é" * 513))),
        request.copy(files = Vector(request.files.head.copy(replacement = null))))
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace) { core =>
        When("the owning call receives malformed assigned intents or an invalid selected policy")
        val refused = invalid.map(F.applyC(core, policy, _).isFaillure)
        val invalidpolicy = F.applyC(core, policy.copy(maximumBlobBytes = 0), request)
        Then("semantic admission fails before any write and preserves both actual originals")
        refused shouldBe Vector.fill(invalid.size)(true)
        invalidpolicy.isFaillure shouldBe true
        _contents(root) shouldBe before
      }
      for (actual <- Vector("binary\u0000text", "é" * 513)) {
        Given("a complete CNCF-readable file that exceeds the assigned business text contract")
        Files.writeString(root.resolve("first.txt"), actual, UTF_8)
        WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace) { core =>
          When("actual capture returns complete text to the narrower semantic policy")
          val refused = F.captureC(core, policy, request.copy(files = request.files.map(_.copy(expected = None))))
          Then("the semantic consumer refuses that content without truncation or mutation")
          refused.isFaillure shouldBe true
          Files.readString(root.resolve("first.txt"), UTF_8) shouldBe actual
          Files.readString(root.resolve("second.txt"), UTF_8) shouldBe before.last
        }
      }
    }

    "deny replacement under actual ReadOnly authority after allowing ordinary observation" in {
      Given("the same assigned intent under a normal ReadOnly workspace grant")
      val root = fixture()
      val before = _contents(root)
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace, access = Some("read-only")) { core =>
        When("the actual owning UoW observes and then attempts replacement")
        val observed = take(F.observeC(core, policy, request))
        val refused = F.applyC(core, policy, request)
        Then("read remains available while the current coarse grant denies every write")
        observed.status shouldBe "ready"
        refused.isFaillure shouldBe true
        _contents(root) shouldBe before
        Files.exists(root.resolve(".git")) shouldBe false
      }
    }
  }
}
