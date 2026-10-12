package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.runtime.RepositoryConflictFileProgram as P

/**
 * @since   Oct.  6, 2026
 * @version Oct. 12, 2026
 */
final class RepositorySyncConflictFileProgramSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def fixture(): Path = {
    val owned = Files.createDirectories(Path.of("target/sm-workflow/work/conflict-file-program").toAbsolutePath.normalize)
    val root = Files.createTempDirectory(owned, "repository-")
    Files.createDirectory(root.resolve(".git"))
    Files.writeString(root.resolve("first.txt"), "first unresolved\n")
    Files.writeString(root.resolve("second.txt"), "second unresolved\n")
    root
  }
  private def request = P.Request("actual-instance", "actual-conflict-revision", Vector(
    P.File("first.txt", Some("first unresolved\n"), "first resolved\n"),
    P.File("second.txt", Some("second unresolved\n"), "second resolved\n")))
  private val paths = Set("first.txt", "second.txt")

  "Runtime-fixed conflict file program" should {
    "observe and apply two selected ordinary file replacements once" in {
      Given("two selected ordinary files with their original text")
      val root = fixture()
      val originalMode = Files.getPosixFilePermissions(root.resolve("second.txt"))
      When("the exact selected overwrite intent is observed and applied")
      val observed = P.run(root, paths, 1024, "observe", request)
      val result = P.run(root, paths, 1024, "apply", request)
      Then("both original texts are replaced and file modes are retained")
      observed.status shouldBe "ready"
      result.status shouldBe "applied"
      Files.readString(root.resolve("first.txt")) shouldBe "first resolved\n"
      Files.readString(root.resolve("second.txt")) shouldBe "second resolved\n"
      Files.getPosixFilePermissions(root.resolve("second.txt")) shouldBe originalMode
    }
    "preflight all paths and preserve a conflicting later file without modifying an earlier ready one" in {
      Given("a ready first path and an intervening user edit at the second path")
      val root = fixture()
      Files.writeString(root.resolve("second.txt"), "other user's resolution\n")
      When("application encounters contradictory overwrite data")
      val result = P.run(root, paths, 1024, "apply", request)
      Then("neither file is overwritten or reclassified as accepted")
      result.status shouldBe "conflict"
      Files.readString(root.resolve("first.txt")) shouldBe "first unresolved\n"
      Files.readString(root.resolve("second.txt")) shouldBe "other user's resolution\n"
    }
    "reject unconfigured paths, symlink ancestors and oversized or binary text before writes" in {
      Given("a fixed path policy and real owned filesystem targets")
      val root = fixture()
      val outside = Files.createTempDirectory(root.getParent, "outside-")
      Files.writeString(outside.resolve("file.txt"), "outside unchanged\n")
      Files.createSymbolicLink(root.resolve("link"), outside)
      When("an unknown path, linked ancestor or invalid text is submitted")
      intercept[IllegalArgumentException](P.run(root, paths, 1024, "apply",
        request.copy(files = Vector(P.File("unknown.txt", Some("x"), "y")))))
      intercept[IllegalArgumentException](P.run(root, Set("link/file.txt"), 1024, "apply",
        request.copy(files = Vector(P.File("link/file.txt", Some("outside unchanged\n"), "bad")))))
      intercept[IllegalArgumentException](P.run(root, paths, 2, "apply", request))
      intercept[IllegalArgumentException](P.run(root, paths, 1024, "apply",
        request.copy(files = Vector(P.File("first.txt", Some("first unresolved\n"), "binary\u0000text")))))
      Then("ordinary and outside source contents remain untouched")
      Files.readString(root.resolve("first.txt")) shouldBe "first unresolved\n"
      Files.readString(outside.resolve("file.txt")) shouldBe "outside unchanged\n"
    }
  }
}
