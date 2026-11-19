package org.simplemodeling.scalalint

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import java.io.File
import scala.collection.JavaConverters._

/** One resolver source, compiled into the build and the independent rules artifact.
  * Configuration resolution is data preparation, never a successful lint result.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object ScalaLintProfiles {
  val version = "1"
  val sourceKey = "sm-workflow.source.style"
  val historyKey = "sm-workflow.source.history.style"
  final case class Selection(source: String, history: String)
  final case class Effective(selection: Selection, profiles: Vector[String], config: Config) {
    def rules: Vector[String] = config.getStringList("rules").asScala.toVector
    def rendered: String = config.root.render(ConfigRenderOptions.defaults().setJson(false))
  }

  def selection(source: Option[String], history: Option[String]): Selection = {
    def _checked_(value: String): String = {
      require(Set("none", "goldenport")(value), "Source/history style must be exactly none or goldenport")
      value
    }
    val selected = _checked_(source.getOrElse("none"))
    Selection(selected, _checked_(history.getOrElse(selected)))
  }

  def resolve(selected: Selection, project: Option[Config], loader: ClassLoader): Effective = {
    val baseline = _resource("baseline", loader)
    val team = if (selected.source == "goldenport") _resource("goldenport", loader) else ConfigFactory.empty()
    val history = if (selected.history == "goldenport") _resource("history", loader) else ConfigFactory.empty()
    val layers = Vector(baseline, team, history) ++ project.toVector
    // Rule membership is additive; ordinary leaves use HOCON fallback semantics.
    // Includes are parsed at the project/resource owner before the merge.
    val rules = layers.flatMap(c => if (c.hasPath("rules")) c.getStringList("rules").asScala else Nil).distinct
    val merged = layers.foldLeft(ConfigFactory.empty())((prior, next) => next.withFallback(prior))
      .withValue("rules", com.typesafe.config.ConfigValueFactory.fromIterable(rules.asJava)).resolve()
    Effective(selected, Vector("baseline@" + version) ++
      (if (selected.source == "goldenport") Vector("goldenport@" + version) else Vector.empty) ++
      (if (selected.history == "goldenport") Vector("history@" + version) else Vector.empty), merged)
  }

  def project(file: File): Option[Config] =
    if (file.isFile) Some(ConfigFactory.parseFile(file)) else None

  private def _resource(name: String, loader: ClassLoader): Config = {
    val path = "scala-lint/profiles/v" + version + "/" + name + ".conf"
    require(loader.getResource(path) != null, "Unavailable shared lint profile: " + path)
    ConfigFactory.parseResources(loader, path)
  }
}
