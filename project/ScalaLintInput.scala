package org.simplemodeling.scalalint

import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions, ConfigRenderOptions, ConfigSyntax, ConfigValueType}
import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.util.Base64
import scala.collection.JavaConverters._

/** Trusted fixed-build input. No profile resolution or engine lives here.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object ScalaLintInput {
  val property = "cncf.sbt.lint.input"
  val contextKey = "sm-workflow.scala-lint.context"
  final case class Binding(identity: String, revision: String, config: Config) {
    def json: String = config.root.render(ConfigRenderOptions.concise().setJson(true))
    def diffBase: String = {
      val value = config.getString(contextKey + ".diffBase")
      require(value.matches("[0-9a-f]{40}|[0-9a-f]{64}"), "Lint admission requires its exact Git base")
      value
    }
  }

  def decode(encoded: String): Binding = {
    require(encoded != null && encoded.nonEmpty && encoded.length <= 90000 &&
      encoded.matches("[A-Za-z0-9_-]+"), "Malformed selected lint envelope")
    val bytes = Base64.getUrlDecoder.decode(encoded)
    require(Base64.getUrlEncoder.withoutPadding().encodeToString(bytes) == encoded,
      "Noncanonical selected lint envelope")
    val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString
    val envelope = ConfigFactory.parseString(text, ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON))
    require(envelope.root.keySet.asScala.toSet == Set("identity", "revision", "config"),
      "Selected lint envelope requires exact identity, revision and config")
    require(Vector("identity", "revision").forall(key => envelope.getValue(key).valueType == ConfigValueType.STRING),
      "Selected lint identity/revision must be exact strings")
    val binding = Binding(envelope.getString("identity"), envelope.getString("revision"), envelope.getConfig("config"))
    require(binding.identity.matches("[A-Za-z0-9_.-]{1,128}") &&
      binding.revision.matches("[A-Za-z0-9_.-]{1,128}"), "Invalid selected lint identity/revision")
    require(binding.json.getBytes(StandardCharsets.UTF_8).length <= 65536,
      "Selected lint configuration exceeds the typed input bound")
    require(binding.config.isResolved, "Selected lint configuration must already be resolved")
    require(binding.config.hasPath("rules"), "Selected lint configuration has no rules")
    require(binding.config.getList("rules").asScala.forall(value => value.valueType == ConfigValueType.STRING &&
      value.unwrapped.toString.nonEmpty && value.unwrapped.toString.trim == value.unwrapped.toString),
      "Selected lint rules must be exact nonblank strings")
    binding.diffBase
    ScalaLintDelta.read(binding.config)
    binding
  }

  /** Presence, including an empty property, must never select ambient config. */
  def selected(properties: Map[String, String]): Option[Binding] = properties.get(property).map(decode)
}
