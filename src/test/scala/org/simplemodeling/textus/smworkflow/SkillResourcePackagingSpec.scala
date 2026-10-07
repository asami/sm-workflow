package org.simplemodeling.textus.smworkflow

import java.nio.file.{Files, Paths}
import java.util.jar.JarFile
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SkillResourcePackagingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Component-owned skill resources" should {
    "retain exactly the three public semantic skill sources in the actual component JAR" in {
      Given("the official packageBin component artifact and the independently readable authoring sources")
      val artifact = Paths.get("target/scala-3.3.8/textus-sm-workflow_3-0.1.0-SNAPSHOT.jar")
      withClue("Run packageBin before this artifact acceptance spec: ")(Files.isRegularFile(artifact) shouldBe true)
      val prefix = "META-INF/sm-workflow/skills/"
      val names = Vector("sm-goal-phase", "sm-split-phase", "sm-repository-sync")
      val expected = names.map(name => s"$prefix$name/SKILL.md")
      val jar = new JarFile(artifact.toFile)
      try {
        When("the packaged resource namespace is enumerated and each entry is read from the real JAR")
        val entries = jar.entries().asScala.filter(entry => !entry.isDirectory && entry.getName.startsWith(prefix)).toVector
        val contents = entries.map { entry =>
          val input = jar.getInputStream(entry)
          try entry.getName -> input.readAllBytes().toVector finally input.close()
        }.toMap
        Then("there are no missing duplicate or extra skills, and every instruction byte matches the source")
        entries.map(_.getName).sorted shouldBe expected.sorted
        names.foreach { name =>
          val source = Paths.get("skills", name, "SKILL.md")
          contents(s"$prefix$name/SKILL.md") shouldBe Files.readAllBytes(source).toVector
        }
      } finally jar.close()
    }

    "retain identical instruction bytes in the standalone ZIP and component JAR" in {
      Given("the actual skillBundle ZIP, packageBin JAR and independent authoring sources")
      val archive = Paths.get("target/sm-workflow-skills-0.1.0-SNAPSHOT.zip")
      val artifact = Paths.get("target/scala-3.3.8/textus-sm-workflow_3-0.1.0-SNAPSHOT.jar")
      withClue("Run skillBundle before this distribution spec: ")(Files.isRegularFile(archive) shouldBe true)
      Files.isRegularFile(artifact) shouldBe true
      val names = Vector("sm-goal-phase", "sm-split-phase", "sm-repository-sync")
      val expected = names.map(name => s"skills/$name/SKILL.md")
      val zip = new ZipFile(archive.toFile)
      try {
        val jar = new JarFile(artifact.toFile)
        try {
          When("both real archives are opened and every standalone instruction is read")
          val entries = zip.entries().asScala.filterNot(_.isDirectory).toVector
          val contents = entries.map { entry =>
            val input = zip.getInputStream(entry)
            try entry.getName -> input.readAllBytes().toVector finally input.close()
          }.toMap
          Then("the standalone namespace contains exactly three skills with source and JAR byte equality")
          entries.map(_.getName).sorted shouldBe expected.sorted
          expected.foreach { path =>
            val sourceBytes = Files.readAllBytes(Paths.get(path)).toVector
            contents(path) shouldBe sourceBytes
            val packagedEntry = jar.getJarEntry(s"META-INF/sm-workflow/$path")
            packagedEntry should not be null
            val input = jar.getInputStream(packagedEntry)
            try input.readAllBytes().toVector shouldBe sourceBytes finally input.close()
          }
        } finally jar.close()
      } finally zip.close()
    }

    "produce identical standalone archive bytes after source timestamp and input-order changes" in {
      Given("the original skillBundle archive and the build task's separately copied and repacked source probe")
      val original = Paths.get("target/sm-workflow-skills-0.1.0-SNAPSHOT.zip")
      val probeRoot = Paths.get("target/skill-bundle-reproducibility")
      val probe = probeRoot.resolve("repacked.zip")
      Files.isRegularFile(original) shouldBe true
      withClue("Run checkSkillBundleReproducibility before this scenario: ")(Files.isRegularFile(probe) shouldBe true)
      val names = Vector("sm-goal-phase", "sm-split-phase", "sm-repository-sync")
      names.foreach { name =>
        val copy = probeRoot.resolve(s"skills/$name/SKILL.md")
        Files.isRegularFile(copy) shouldBe true
        Files.getLastModifiedTime(copy).toMillis shouldBe 946684800000L
        Files.readAllBytes(copy).toVector shouldBe Files.readAllBytes(Paths.get("skills", name, "SKILL.md")).toVector
      }
      When("the complete original and independently repacked ZIP files are read")
      val originalBytes = Files.readAllBytes(original).toVector
      val probeBytes = Files.readAllBytes(probe).toVector
      Then("archive bytes are equal without a digest comparison or source-file metadata mutation")
      probeBytes shouldBe originalBytes
    }
  }
}
