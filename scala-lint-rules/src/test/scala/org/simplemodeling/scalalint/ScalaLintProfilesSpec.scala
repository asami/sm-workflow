package org.simplemodeling.scalalint

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Independent profile resolution specifications.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class ScalaLintProfilesSpec extends AnyWordSpec with Matchers {
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
  }
}
