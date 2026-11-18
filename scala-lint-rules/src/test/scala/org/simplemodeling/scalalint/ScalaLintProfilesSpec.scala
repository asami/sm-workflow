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
      effective.rules shouldBe Vector("DisableSyntax", "NoOptionGet")
      effective.config.getBoolean("DisableSyntax.noFinalize") shouldBe true
    }
    "keep the baseline when Goldenport and project rules are added" in {
      val effective = ScalaLintProfiles.resolve(ScalaLintProfiles.selection(Some("goldenport"), Some("none")),
        Some(ConfigFactory.parseString("rules = [RedundantSyntax]")), getClass.getClassLoader)
      effective.rules shouldBe Vector("DisableSyntax", "NoOptionGet", "GoldenportPrivateMemberNaming", "RedundantSyntax")
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
  }
}
