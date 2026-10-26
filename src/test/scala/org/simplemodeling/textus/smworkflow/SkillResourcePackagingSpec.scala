package org.simplemodeling.textus.smworkflow

import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.jar.JarFile
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  2, 2026
 * @version Oct. 25, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class SkillResourcePackagingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _skill_paths = Vector("sm-goal-phase", "sm-split-phase", "sm-repository-sync")
    .map(name => s"skills/$name/SKILL.md")
  private val _contract_paths = Vector(
    "docs/spec/native-host-execution.md",
    "docs/spec/runtime-owned-local-publication.md",
    "docs/spec/caller-information.md",
    "docs/spec/goal-phase-acceptance-boundary.md",
    "docs/spec/goal-phase-release-repair-continuation.md",
    "docs/spec/goal-phase-dependency-scope.md",
    "docs/spec/goal-phase-validation-metadata.md"
  )
  private val _caller_paths = Vector(
    "docs/spec/native-goal-command-task.md",
    "docs/spec/runtime-caller-format-migration.md",
    "scripts/native-goal-command.py",
    "scripts/native-goal-authority.py",
    "scripts/migrate-runtime-caller-format.py"
  )
  private val _supporting_paths = Vector("docs/spec/goal-phase-purpose-repair.md")
  private val _distribution_paths = _skill_paths ++ _contract_paths ++ _caller_paths ++ _supporting_paths

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

    "retain identical bytes for every declared distribution file in the standalone ZIP and component JAR" in {
      Given("the actual skillBundle ZIP, packageBin JAR and independent authoring sources")
      val archive = Paths.get("target/sm-workflow-skills-0.1.0-SNAPSHOT.zip")
      val artifact = Paths.get("target/scala-3.3.8/textus-sm-workflow_3-0.1.0-SNAPSHOT.jar")
      withClue("Run skillBundle before this distribution spec: ")(Files.isRegularFile(archive) shouldBe true)
      Files.isRegularFile(artifact) shouldBe true
      val zip = new ZipFile(archive.toFile)
      try {
        val jar = new JarFile(artifact.toFile)
        try {
          When("both real archives are opened and all standalone skills and supporting contracts are read")
          val entries = zip.entries().asScala.filterNot(_.isDirectory).toVector
          val contents = entries.map { entry =>
            val input = zip.getInputStream(entry)
            try entry.getName -> input.readAllBytes().toVector finally input.close()
          }.toMap
          val packagedpaths = jar.entries().asScala
            .filter(entry => !entry.isDirectory && entry.getName.startsWith("META-INF/sm-workflow/"))
            .map(_.getName.stripPrefix("META-INF/sm-workflow/")).toVector
          Then("both distribution namespaces contain exactly the declared files with authoring and JAR byte equality")
          entries.map(_.getName).sorted shouldBe _distribution_paths.sorted
          packagedpaths.sorted shouldBe _distribution_paths.sorted
          _distribution_paths.foreach { path =>
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

    "resolve actual direct contract links from installed skill instructions within the standalone ZIP" in {
      Given("the actual standalone ZIP and its installed relative skill paths")
      val archive = Paths.get("target/sm-workflow-skills-0.1.0-SNAPSHOT.zip")
      withClue("Run skillBundle before this link specification: ")(Files.isRegularFile(archive) shouldBe true)
      val markdownlink = "\\[[^\\]]*\\]\\(([^)]+)\\)".r
      val zip = new ZipFile(archive.toFile)
      try {
        When("direct docs/spec Markdown links are read from each packaged skill and resolved after fragment removal")
        val packagedpaths = zip.entries().asScala.filterNot(_.isDirectory).map(_.getName).toSet
        val resolvedbyskill = _skill_paths.map { path =>
          val entry = zip.getEntry(path)
          val input = zip.getInputStream(entry)
          val instructions = try new String(input.readAllBytes(), UTF_8) finally input.close()
          val references = markdownlink.findAllMatchIn(instructions).map(_.group(1))
            .map(_.takeWhile(_ != '#')).filter(_.startsWith("../../docs/spec/")).toVector
          path -> references.map(reference => Paths.get(path).getParent.resolve(reference).normalize().toString)
        }.toMap
        Then("every skill has actual contract links and every direct or supporting contract target is packaged")
        resolvedbyskill.values.foreach { targets =>
          targets should not be empty
          targets.foreach { target => packagedpaths should contain(target) }
        }
        resolvedbyskill.values.flatten.toSet shouldBe _contract_paths.toSet
        val acceptance = zip.getInputStream(zip.getEntry("docs/spec/goal-phase-acceptance-boundary.md"))
        val acceptancebody = try new String(acceptance.readAllBytes(), UTF_8) finally acceptance.close()
        val supporting = markdownlink.findAllMatchIn(acceptancebody).map(_.group(1)).map(_.takeWhile(_ != '#'))
          .filter(_ == "goal-phase-purpose-repair.md").map(reference => Paths.get("docs/spec").resolve(reference).toString).toSet
        supporting shouldBe _supporting_paths.toSet
        supporting.foreach(packagedpaths should contain(_))
      } finally zip.close()
    }

    "produce identical standalone archive bytes after source timestamp and input-order changes" in {
      Given("the original skillBundle archive and the build task's separately copied and repacked source probe")
      val original = Paths.get("target/sm-workflow-skills-0.1.0-SNAPSHOT.zip")
      val probeRoot = Paths.get("target/skill-bundle-reproducibility")
      val probe = probeRoot.resolve("repacked.zip")
      Files.isRegularFile(original) shouldBe true
      withClue("Run checkSkillBundleReproducibility before this scenario: ")(Files.isRegularFile(probe) shouldBe true)
      When("the complete ZIP bytes and all fifteen copied sources, timestamps and original authoring bytes are read")
      val originalBytes = Files.readAllBytes(original).toVector
      val probeBytes = Files.readAllBytes(probe).toVector
      val copies = _distribution_paths.map { path =>
        val copy = probeRoot.resolve(path)
        (path, Files.isRegularFile(copy), Files.getLastModifiedTime(copy).toMillis,
          Files.readAllBytes(copy).toVector, Files.readAllBytes(Paths.get(path)).toVector)
      }
      val zip = new ZipFile(original.toFile)
      try {
        val originalsources = _distribution_paths.map { path =>
          val input = zip.getInputStream(zip.getEntry(path))
          try path -> input.readAllBytes().toVector finally input.close()
        }.toMap
        Then("all fifteen copies have the changed timestamp, authoring bytes remain original, and full ZIP bytes are equal")
        copies.foreach { case (path, regular, timestamp, copiedbytes, authoringbytes) =>
          regular shouldBe true
          timestamp shouldBe 946684800000L
          copiedbytes shouldBe originalsources(path)
          authoringbytes shouldBe originalsources(path)
        }
        probeBytes shouldBe originalBytes
      } finally zip.close()
    }
  }
}
