import org.goldenport.cozy.{CozyProjectConfig, CozyProjectIdentityContract, CozyProjectIdentityEvidence}
import sbt._

object ProjectYamlBuild {
  def load(file: File): CozyProjectConfig =
    CozyProjectConfig.load(file)

  def requiredValue(config: CozyProjectConfig, path: String): String =
    config.value(path).getOrElse(sys.error(s"$path is required in project.yaml"))

  def admitted(config: CozyProjectConfig, scalaBinaryVersion: String): CozyProjectIdentityEvidence =
    CozyProjectIdentityContract.requireAdmitted(config, scalaBinaryVersion)

  def organization(evidence: CozyProjectIdentityEvidence, config: CozyProjectConfig): String =
    evidence.organization.getOrElse(requiredValue(config, "project.organization"))

  def moduleName(evidence: CozyProjectIdentityEvidence, config: CozyProjectConfig): String =
    evidence.moduleName.getOrElse(requiredValue(config, "project.name"))

  def version(evidence: CozyProjectIdentityEvidence, config: CozyProjectConfig): String =
    if (evidence.shape == "canonical") evidence.effectiveVersion
    else requiredValue(config, "project.component.version")

  def carBaseName(evidence: CozyProjectIdentityEvidence, moduleName: String, version: String): String =
    evidence.carBaseName.getOrElse(s"$moduleName-$version")

  def manifestMetadata(evidence: CozyProjectIdentityEvidence, config: CozyProjectConfig): Map[String, String] =
    if (evidence.shape == "canonical") evidence.manifestMetadata
    else config.mapUnder("packaging.car.manifest_metadata") ++
      Map("component" -> requiredValue(config, "project.component.name"))

  def dependencies(config: CozyProjectConfig): Seq[ModuleID] =
    _dependencies(config, "compile", None) ++
      _dependencies(config, "test", Some(Test))

  private def _dependencies(
    config: CozyProjectConfig,
    scope: String,
    configuration: Option[Configuration]
  ): Seq[ModuleID] =
    config.list(s"build.dependencies.$scope").map { coordinate =>
      val module = _module(coordinate)
      configuration.fold(module)(module % _)
    }

  private def _module(coordinate: String): ModuleID =
    coordinate.split(":", -1).toList match {
      case organization :: "" :: artifact :: version :: Nil =>
        organization %% artifact % version
      case organization :: artifact :: version :: Nil =>
        organization % artifact % version
      case _ =>
        sys.error(
          s"Invalid project.yaml dependency '$coordinate'; expected organization:artifact:version or organization::artifact:version"
        )
    }
}
