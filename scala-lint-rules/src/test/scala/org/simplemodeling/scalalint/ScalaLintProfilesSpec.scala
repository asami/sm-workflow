package org.simplemodeling.scalalint

import com.typesafe.config.ConfigFactory
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.net.URLClassLoader
import scala.collection.JavaConverters._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Independent profile resolution specifications.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class ScalaLintProfilesSpec extends AnyWordSpec with Matchers {
  private val loader = getClass.getClassLoader
  private val defaults = ScalaLintProfiles.selection(None, None)

  private def withDirectory(body: Path => Unit): Unit = {
    val directory = Files.createTempDirectory("slp3-profile-resolution-")
    try body(directory)
    finally {
      val paths = Files.walk(directory)
      try paths.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.delete(_))
      finally paths.close()
    }
  }

  private def write(path: Path, content: String): Unit = {
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    ()
  }

  "The reusable profile resolver" should {
    "load installed nonempty defaults with no project configuration" in {
      val effective = ScalaLintProfiles.resolve(ScalaLintProfiles.selection(None, None), None, getClass.getClassLoader)
      effective.rules.take(2) shouldBe Vector("DisableSyntax", "NoOptionGet")
      effective.rules should contain allOf ("NoTryGet", "NoEitherProjectionGet", "NoEmptySensitiveCollectionOps",
        "NoUnimplementedPlaceholder", "NoDiscardedFailureResult", "NoArrayValueEquality", "NoInferredAny",
        "NoIncompatibleEquality", "NoImplicitArrayEqualityInCollections", "NoMutableEqualityKey",
        "NoEmbeddedCredentials", "NoUnsafeSqlConstruction", "NoTlsVerificationBypass",
        "NoWeakCryptographicConfiguration", "NoUncheckedJavaDeserialization", "NoPredictableSecurityRandom",
        "NoStructuralIndentation")
      effective.rules.distinct shouldBe effective.rules
      effective.config.getBoolean("DisableSyntax.noFinalize") shouldBe true
    }
    "keep the baseline when Goldenport and project rules are added" in {
      val effective = ScalaLintProfiles.resolve(ScalaLintProfiles.selection(Some("goldenport"), Some("none")),
        Some(ConfigFactory.parseString("rules = [RedundantSyntax]")), getClass.getClassLoader)
      val baseline = ScalaLintProfiles.resolve(ScalaLintProfiles.selection(None, None), None, getClass.getClassLoader)
      effective.rules.take(baseline.rules.size) shouldBe baseline.rules
      effective.rules should contain allOf ("GoldenportPrivateMemberNaming", "GoldenportLocalHelperNaming",
        "GoldenportFlatcaseNaming", "GoldenportVisibilityNaming", "GoldenportNoDiscardedFailureResult")
      effective.rules.last shouldBe "RedundantSyntax"
      effective.rules.distinct shouldBe effective.rules
      effective.config.getBoolean("DisableSyntax.noFinalize") shouldBe true
    }
    "fail when the selected installed resource is unavailable" in {
      val missing = new ClassLoader(null) {}
      intercept[IllegalArgumentException] {
        ScalaLintProfiles.resolve(ScalaLintProfiles.selection(None, None), None, missing)
      }.getMessage should include ("Unavailable shared lint profile")
    }
    "fail on malformed project rules rather than render an empty profile" in {
      intercept[com.typesafe.config.ConfigException.WrongType] {
        ScalaLintProfiles.resolve(ScalaLintProfiles.selection(None, None),
          Some(ConfigFactory.parseString("rules = false")), getClass.getClassLoader)
      }
    }
    "select history placement independently of naming in all six combinations" in {
      val cases = Vector(
        (None, None, false),
        (Some("goldenport"), None, true),
        (Some("goldenport"), Some("none"), false),
        (Some("goldenport"), Some("goldenport"), true),
        (None, Some("goldenport"), true),
        (None, Some("none"), false)
      )
      cases.foreach { case (source, history, enabled) =>
        val effective = ScalaLintProfiles.resolve(ScalaLintProfiles.selection(source, history), None, getClass.getClassLoader)
        effective.rules.contains("GoldenportHistoryCommentPlacement") shouldBe enabled
        effective.profiles.contains("history@1") shouldBe enabled
        effective.rules.contains("GoldenportPrivateMemberNaming") shouldBe source.contains("goldenport")
      }
    }
    "never access the installed history profile for effective none" in {
      val loader = new ClassLoader(getClass.getClassLoader) {
        override def getResource(name: String): java.net.URL = {
          require(!name.endsWith("/history.conf"), "History must not be accessed")
          super.getResource(name)
        }
        override def getResources(name: String): java.util.Enumeration[java.net.URL] = {
          require(!name.endsWith("/history.conf"), "History must not be accessed")
          super.getResources(name)
        }
      }
      val effective = ScalaLintProfiles.resolve(ScalaLintProfiles.selection(Some("goldenport"), Some("none")), None, loader)
      effective.rules should not contain "GoldenportHistoryCommentPlacement"
      effective.rules should contain ("GoldenportPrivateMemberNaming")
    }
    "union rules in layer order and remove exact IDs after project additions" in {
      val baseline = ScalaLintProfiles.resolve(defaults, None, loader)
      val selected = ScalaLintProfiles.selection(Some("goldenport"), None)
      val inherited = ScalaLintProfiles.resolve(selected, None, loader)
      val effective = ScalaLintProfiles.resolve(selected, Some(ConfigFactory.parseString("""
        rules = [NoOptionGet, ProjectExtra, ProjectExtra, RemoveProjectExtra]
        sm-workflow.scala-lint.remove-rules = [NoOptionGet, GoldenportPrivateMemberNaming,
          GoldenportHistoryCommentPlacement, RemoveProjectExtra, NoOptionGet]
      """)), loader)
      effective.rules shouldBe (inherited.rules ++ Vector("ProjectExtra", "RemoveProjectExtra"))
        .filterNot(Set("NoOptionGet", "GoldenportPrivateMemberNaming", "GoldenportHistoryCommentPlacement", "RemoveProjectExtra"))
      effective.rules.take(baseline.rules.size - 1) shouldBe baseline.rules.filterNot(_ == "NoOptionGet")
      effective.removedRules shouldBe Vector("NoOptionGet", "GoldenportPrivateMemberNaming",
        "GoldenportHistoryCommentPlacement", "RemoveProjectExtra")
      effective.config.hasPathOrNull(ScalaLintProfiles.removalKey) shouldBe false
      effective.rendered should not include "remove-rules"
    }
    "reject unknown and inexact removals and malformed rule arrays" in {
      Vector(
        "sm-workflow.scala-lint.remove-rules = [MissingRule]",
        "sm-workflow.scala-lint.remove-rules = [nooptionget]",
        "sm-workflow.scala-lint.remove-rules = [\" NoOptionGet\"]",
        "sm-workflow.scala-lint.remove-rules = [\"\"]",
        "sm-workflow.scala-lint.remove-rules = [null]",
        "sm-workflow.scala-lint.remove-rules = [42]",
        "sm-workflow.scala-lint.remove-rules = null",
        "sm-workflow.scala-lint.remove-rules = false",
        "rules = [null]", "rules = [42]", "rules = [true]", "rules = [\"\"]",
        "rules = [\"   \"]", "rules = null", "rules = {}"
      ).foreach { input =>
        withClue(input) {
          intercept[Exception] {
            ScalaLintProfiles.resolve(defaults, Some(ConfigFactory.parseString(input)), loader)
          }
        }
      }
    }
    "override individual leaves and replace ordinary arrays while preserving suppression settings" in {
      val inheritedLoader = new URLClassLoader(Array(getClass.getResource("/slp3-profile-resolution/shared/")), null)
      try {
        val effective = ScalaLintProfiles.resolve(defaults, Some(ConfigFactory.parseString("""
          settings.enabled = false
          settings.labels = [project]
          lint.ignore = [NoOptionGet]
          patches.NoOptionGet = false
          settings.nullOverride = null
        """)), inheritedLoader)
        effective.config.getBoolean("settings.enabled") shouldBe false
        effective.config.getString("settings.untouched") shouldBe "baseline"
        effective.config.getStringList("settings.labels").asScala.toVector shouldBe Vector("project")
        effective.config.getStringList("lint.ignore").asScala.toVector shouldBe Vector("NoOptionGet")
        effective.config.getBoolean("patches.NoOptionGet") shouldBe false
        effective.config.getIsNull("settings.nullOverride") shouldBe true
        effective.projectOverrideKeys shouldBe Vector("lint.ignore", "patches.NoOptionGet", "settings.enabled", "settings.labels", "settings.nullOverride")
      } finally inheritedLoader.close()
    }
    "resolve includes relative to the owning shared URL and project file" in {
      val sharedLoader = new URLClassLoader(Array(getClass.getResource("/slp3-profile-resolution/shared/")), null)
      try withDirectory { directory =>
        write(directory.resolve("relative.conf"), "settings.project = included\nsettings.labels = [project]")
        write(directory.resolve(".scalafix.conf"), "include required(\"relative.conf\")\nrules = [ProjectExtra]")
        val effective = ScalaLintProfiles.resolve(defaults,
          ScalaLintProfiles.project(directory.resolve(".scalafix.conf").toFile), sharedLoader)
        effective.rules shouldBe Vector("SharedIncluded", "SharedRoot", "ProjectExtra")
        effective.config.getString("settings.untouched") shouldBe "baseline"
        effective.config.getString("settings.project") shouldBe "included"
        effective.config.getStringList("settings.labels").asScala.toVector shouldBe Vector("project")
      } finally sharedLoader.close()
    }
    "anchor project self references while retaining only project override leaves" in {
      // Given a shared include with += and a project that appends to inherited values.
      val sharedloader = new URLClassLoader(Array(getClass.getResource("/slp3-profile-resolution/shared/")), null)
      try {
        val project = ConfigFactory.parseString("""
          rules += ProjectExtra
          settings.labels = ${settings.labels} [project]
          settings.copy = ${settings.untouched}
          sm-workflow.scala-lint.remove-rules = [SharedRoot]
        """)
        // When normal HOCON resolution anchors both layers' self references.
        val effective = ScalaLintProfiles.resolve(defaults, Some(project), sharedloader)
        // Then rule union/removal, ordinary array concatenation and ownership all survive.
        effective.rules shouldBe Vector("SharedIncluded", "ProjectExtra")
        effective.removedRules shouldBe Vector("SharedRoot")
        effective.config.getStringList("settings.labels").asScala.toVector shouldBe
          Vector("shared", "inherited", "project")
        effective.config.getString("settings.copy") shouldBe "baseline"
        effective.projectOverrideKeys shouldBe Vector("rules", "settings.copy", "settings.labels")
      } finally sharedloader.close()
    }
    "keep absent rule layers out of ordered union while resolving cross layer references" in {
      // Given a team layer without rules between baseline and history.
      withDirectory { directory =>
        val profiles = Files.createDirectories(directory.resolve("scala-lint/profiles/v1"))
        write(profiles.resolve("baseline.conf"), """
          rules = [Baseline]
          catalog.rules = ${later.rules}
          settings.untouched = baseline
          settings.labels = [shared]
        """)
        write(profiles.resolve("goldenport.conf"), "settings.team = true")
        write(profiles.resolve("history.conf"), "rules = [History]")
        val sharedloader = new URLClassLoader(Array(directory.toUri.toURL), null)
        try {
          val project = ConfigFactory.parseString("""
            rules = ${catalog.rules}
            later.rules = [ProjectExtra]
            settings.copy = ${settings.untouched}
            settings.labels = [project]
          """)
          // When project references use the full context and normal leaves override.
          val effective = ScalaLintProfiles.resolve(
            ScalaLintProfiles.selection(Some("goldenport"), None), Some(project), sharedloader)
          // Then an empty rule layer cannot pull the final project rules before history.
          effective.rules shouldBe Vector("Baseline", "History", "ProjectExtra")
          effective.config.getBoolean("settings.team") shouldBe true
          effective.config.getString("settings.copy") shouldBe "baseline"
          effective.config.getStringList("settings.labels").asScala.toVector shouldBe Vector("project")
          effective.projectOverrideKeys shouldBe Vector("later.rules", "rules", "settings.copy", "settings.labels")
        } finally sharedloader.close()
      }
    }
    "fail truthfully when a cross layer reference has no definition" in {
      // Given a project declaration whose target exists in no selected layer.
      val project = ConfigFactory.parseString("settings.copy = ${slp3.undefined.target}")
      // When resolving it, then retain the normal HOCON failure instead of dropping it.
      intercept[com.typesafe.config.ConfigException.UnresolvedSubstitution] {
        ScalaLintProfiles.resolve(defaults, Some(project), loader)
      }
    }
    "distinguish absent optional project files from invalid selected files" in {
      withDirectory { directory =>
        ScalaLintProfiles.project(directory.resolve("absent.conf").toFile) shouldBe None
        intercept[IllegalArgumentException] { ScalaLintProfiles.project(directory.toFile) }
        val config = directory.resolve(".scalafix.conf")
        write(config, "rules = [")
        intercept[com.typesafe.config.ConfigException.Parse] { ScalaLintProfiles.project(config.toFile) }
        write(config, "include required(\"missing.conf\")")
        intercept[com.typesafe.config.ConfigException] { ScalaLintProfiles.project(config.toFile) }
      }
    }
    "reject a dangling selected project symlink and follow a valid file symlink" in {
      withDirectory { directory =>
        val target = directory.resolve("selected.conf")
        val selected = directory.resolve(".scalafix.conf")
        try Files.createSymbolicLink(selected, target.getFileName)
        catch {
          case _: UnsupportedOperationException => cancel("Filesystem does not support symbolic links")
        }
        val error = intercept[IllegalArgumentException] {
          ScalaLintProfiles.project(selected.toFile)
        }
        error.getMessage should include ("readable regular file")
        error.getMessage should include (selected.toString)
        write(target, "rules = [ProjectExtra]")
        val effective = ScalaLintProfiles.resolve(defaults, ScalaLintProfiles.project(selected.toFile), loader)
        effective.rules should contain ("ProjectExtra")
      }
    }
    "report obsolete keys with replacements for old-only and mixed inputs including null" in {
      ScalaLintProfiles.obsoleteHistoryKeys.foreach { case (oldKey, replacement) =>
        Vector(oldKey + " = null", oldKey + " = goldenport\n" + replacement + " = none").foreach { input =>
          val error = intercept[IllegalArgumentException] {
            ScalaLintProfiles.resolve(defaults, Some(ConfigFactory.parseString(input)), loader)
          }
          error.getMessage should include (oldKey)
          error.getMessage should include (replacement)
        }
        intercept[IllegalArgumentException] {
          ScalaLintProfiles.rejectObsoleteHistoryKeys(_ == oldKey)
        }.getMessage should include (replacement)
      }
    }
    "reject a second source or history selector in project Scalafix configuration" in {
      Vector(ScalaLintProfiles.sourceKey, ScalaLintProfiles.historyKey, ScalaLintProfiles.historyRuleKey).foreach { key =>
        val error = intercept[IllegalArgumentException] {
          ScalaLintProfiles.resolve(defaults, Some(ConfigFactory.parseString(key + " = null")), loader)
        }
        error.getMessage should include ("CNCF ExecutionContext.resolvedParameters")
      }
      Vector("", "Goldenport", "other").foreach { style =>
        intercept[IllegalArgumentException] { ScalaLintProfiles.selection(Some(style), None) }
        intercept[IllegalArgumentException] { ScalaLintProfiles.selection(None, Some(style)) }
      }
      intercept[IllegalArgumentException] {
        ScalaLintProfiles.resolve(ScalaLintProfiles.Selection("other", "none"), None, loader)
      }
    }
    "retain an immutable resolved snapshot with visible overrides and removals" in {
      withDirectory { directory =>
        val file = directory.resolve(".scalafix.conf")
        write(file, """rules = [ProjectExtra]
          sm-workflow.scala-lint.remove-rules = [NoOptionGet]
          DisableSyntax.noFinalize = false
          project.labels = [first]
          project.copy = ${project.labels}
        """)
        val issued = ScalaLintProfiles.resolve(defaults, ScalaLintProfiles.project(file.toFile), loader)
        val snapshot = issued.snapshot
        write(file, "rules = [LaterExtra]\nDisableSyntax.noFinalize = true")
        issued.snapshot shouldBe snapshot
        snapshot.getString("schemaVersion") shouldBe "sm-workflow.scala-lint-effective.v1"
        snapshot.getString("sourceStyle") shouldBe "none"
        snapshot.getString("historyStyle") shouldBe "none"
        snapshot.getStringList("profiles").asScala.toVector shouldBe Vector("baseline@1")
        snapshot.getStringList("rules").asScala.toVector shouldBe issued.rules
        snapshot.getStringList("removedRules").asScala.toVector shouldBe Vector("NoOptionGet")
        snapshot.getStringList("projectOverrideKeys").asScala.toVector shouldBe
          Vector("DisableSyntax.noFinalize", "project.copy", "project.labels", "rules")
        snapshot.getConfig("configuration") shouldBe issued.config
        snapshot.getBoolean("configuration.DisableSyntax.noFinalize") shouldBe false
        snapshot.getStringList("configuration.project.copy").asScala.toVector shouldBe Vector("first")
        snapshot.hasPathOrNull("configuration." + ScalaLintProfiles.removalKey) shouldBe false
        val later = ScalaLintProfiles.resolve(defaults, ScalaLintProfiles.project(file.toFile), loader)
        later.rules should contain ("LaterExtra")
        issued.rules should not contain "LaterExtra"
        issued.rules should not contain "NoOptionGet"
      }
    }
  }
}
