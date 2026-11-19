package org.simplemodeling.scalalint

import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigParseOptions, ConfigRenderOptions, ConfigUtil, ConfigValueFactory, ConfigValueType}
import java.io.File
import java.nio.file.{Files, LinkOption}
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
  val historyRuleKey = "sm-workflow.source.history.rule"
  val removalKey = "sm-workflow.scala-lint.remove-rules"
  val obsoleteHistoryKeys: Vector[(String, String)] = Vector(
    "sm-workflow.source-history.style" -> historyKey,
    "sm-workflow.source-history.rule" -> historyRuleKey
  )
  final case class Selection(source: String, history: String)
  final case class Effective(
    selection: Selection,
    profiles: Vector[String],
    config: Config,
    removedRules: Vector[String] = Vector.empty,
    projectOverrideKeys: Vector[String] = Vector.empty
  ) {
    def rules: Vector[String] = config.getStringList("rules").asScala.toVector
    def rendered: String = config.root.render(ConfigRenderOptions.defaults().setJson(false))
    // Config is immutable; this contains the resolved values, never a live file reference.
    def snapshot: Config = ConfigFactory.empty()
      .withValue("schemaVersion", ConfigValueFactory.fromAnyRef("sm-workflow.scala-lint-effective.v1"))
      .withValue("sourceStyle", ConfigValueFactory.fromAnyRef(selection.source))
      .withValue("historyStyle", ConfigValueFactory.fromAnyRef(selection.history))
      .withValue("profiles", ConfigValueFactory.fromIterable(profiles.asJava))
      .withValue("rules", ConfigValueFactory.fromIterable(rules.asJava))
      .withValue("removedRules", ConfigValueFactory.fromIterable(removedRules.asJava))
      .withValue("projectOverrideKeys", ConfigValueFactory.fromIterable(projectOverrideKeys.asJava))
      .withValue("configuration", config.root())
  }

  def rejectObsoleteHistoryKeys(present: String => Boolean): Unit = {
    val obsolete = obsoleteHistoryKeys.filter { case (key, _) => present(key) }
    require(obsolete.isEmpty, "Obsolete configuration key(s): " +
      obsolete.map { case (key, replacement) => key + "; use " + replacement }.mkString(", "))
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
    // Selection is public for compatibility; direct construction must also be checked.
    selection(Some(selected.source), Some(selected.history))
    project.foreach { config =>
      rejectObsoleteHistoryKeys(config.hasPathOrNull)
      Vector(sourceKey, historyKey, historyRuleKey).foreach { key =>
        require(!config.hasPathOrNull(key), "Project Scalafix configuration must not select " + key +
          "; use CNCF ExecutionContext.resolvedParameters")
      }
    }
    val baseline = _resource("baseline", loader)
    val team = if (selected.source == "goldenport") _resource("goldenport", loader) else ConfigFactory.empty()
    val history = if (selected.history == "goldenport") _resource("history", loader) else ConfigFactory.empty()
    val layers = Vector(baseline, team, history) ++ project.toVector
    // Rule membership is additive; ordinary leaves use HOCON fallback semantics.
    // Parse includes at their owning file/URL before merging; resolve substitutions afterward.
    val contexts = layers.scanLeft(ConfigFactory.empty())((prior, next) => next.withFallback(prior)).tail
    val merged = contexts.last.resolve()
    // A self-reference (including +=) must resolve inside a root that still owns
    // its original value. resolveWith(merged) substitutes a different root and
    // cannot anchor Typesafe Config's delayed self-referential merges.
    val resolvedlayers = contexts.map { context =>
      // Keep prior definitions for +=, but do not feed a final, already appended
      // value back into its own fallback. Other paths remain available for
      // cross-layer substitutions through the final resolved context.
      val fallback = _leaf_keys(context.root(), Vector.empty).foldLeft(merged) { (prior, path) =>
        prior.withoutPath(path)
      }
      context.withFallback(fallback).resolve()
    }
    val rules = layers.zip(resolvedlayers).flatMap { case (original, resolved) =>
      if (original.root().containsKey("rules")) _rule_ids(resolved, "rules")
      else Vector.empty
    }.distinct
    val resolvedproject = if (project.isDefined) resolvedlayers.takeRight(1) else Vector.empty
    val removed = project.toVector.zip(resolvedproject).flatMap { case (original, resolved) =>
      if (original.hasPathOrNull(removalKey)) _rule_ids(resolved, removalKey)
      else Vector.empty
    }.distinct
    val unknown = removed.filterNot(rules.contains)
    require(unknown.isEmpty, "Unknown rule removal(s) in " + removalKey + ": " + unknown.mkString(", "))
    val resolved = merged.withoutPath(removalKey)
      .withValue("rules", ConfigValueFactory.fromIterable(rules.filterNot(removed.contains).asJava))
    val overrides = project.toVector.zip(resolvedproject).flatMap { case (original, resolved) =>
      // Project metadata describes declarations from the project, never leaves
      // supplied only by fallback. Project substitutions may themselves expand
      // into objects, whose resolved leaves are part of that declaration.
      val ownedpaths = _leaf_keys(original.withoutPath(removalKey).root(), Vector.empty)
      val owned = ownedpaths.foldLeft(ConfigFactory.empty()) { (prior, path) =>
        resolved.withOnlyPath(path).withFallback(prior)
      }
      _leaf_keys(owned.root(), Vector.empty)
    }.sorted
    Effective(selected, Vector("baseline@" + version) ++
      (if (selected.source == "goldenport") Vector("goldenport@" + version) else Vector.empty) ++
      (if (selected.history == "goldenport") Vector("history@" + version) else Vector.empty),
      resolved, removed, overrides)
  }

  def project(file: File): Option[Config] = {
    val path = file.toPath
    if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) None
    else {
      require(Files.isRegularFile(path) && Files.isReadable(path),
        "Selected project Scalafix configuration must be a readable regular file: " + file)
      Some(ConfigFactory.parseFile(file, ConfigParseOptions.defaults().setAllowMissing(false)))
    }
  }

  private def _leaf_keys(config: ConfigObject, prefix: Vector[String]): Vector[String] =
    config.asScala.toVector.flatMap { case (key, value) =>
      val path = prefix :+ key
      value match {
        case nested: ConfigObject => _leaf_keys(nested, path)
        case _ => Vector(ConfigUtil.joinPath(path.asJava))
      }
    }

  private def _rule_ids(config: Config, key: String): Vector[String] = {
    if (!config.hasPathOrNull(key)) Vector.empty
    else config.getList(key).asScala.map { value =>
      require(value.valueType() == ConfigValueType.STRING,
        "Configuration " + key + " requires nonblank exact string rule IDs")
      val id = value.unwrapped().asInstanceOf[String]
      require(id.nonEmpty && id.trim == id, "Configuration " + key + " requires nonblank exact string rule IDs")
      id
    }.toVector
  }

  private def _resource(name: String, loader: ClassLoader): Config = {
    val path = "scala-lint/profiles/v" + version + "/" + name + ".conf"
    val resource = loader.getResource(path)
    require(resource != null, "Unavailable shared lint profile: " + path)
    ConfigFactory.parseURL(resource, ConfigParseOptions.defaults().setAllowMissing(false))
  }
}
