import java.io.File
import sbt._
import sbt.Keys._

/** Explicit source selection restores the publisher itself before its first
  * runtime-owned publication. Normal builds retain declared artifact resolution.
  */
object SmWorkflowCncfDevelopmentBuild {
  /** A project dependency's classes are a compilation input, while the normal
    * application publisher requires attributed regular JARs. Retain the
    * resolved transitive dependencies and expose the selected project's own
    * packageBin result with the same verified cross-published identity.
    */
  def runtimeClasspath(classpath: Seq[Attributed[File]], classes: File, archive: File,
    organization: String, moduleName: String, binaryVersion: String, version: String): Seq[Attributed[File]] = {
    val artifactName = moduleName + "_" + binaryVersion
    require(organization == "org.goldenport" && artifactName == "goldenport-cncf_3" && version == "0.5.3-SNAPSHOT",
      "Selected source runtime identity differs from the declared CNCF dependency")
    require(archive.isFile && archive.getName == artifactName + "-" + version + ".jar",
      "Selected CNCF packageBin must be the ordinary cross-published JAR")
    val projectFiles = Set(classes.getCanonicalFile, archive.getCanonicalFile)
    val retained = classpath.filterNot(entry => projectFiles(entry.data.getCanonicalFile))
    require(!retained.exists(_.get(moduleID.key).exists(module =>
      module.organization == organization && Set(moduleName, artifactName)(module.name))),
      "Selected source and resolved CNCF artifacts must not coexist")
    retained :+ Attributed.blank(archive.getCanonicalFile)
      .put(moduleID.key, ModuleID(organization, artifactName, version))
      .put(artifact.key, Artifact(artifactName))
  }

  def resolve(root: File, selection: Option[String]): Option[File] = selection.map { raw =>
    val path = new File(raw)
    require(path.isAbsolute && raw.trim.nonEmpty, "CNCF source selection requires an absolute checkout")
    val source = path.getCanonicalFile
    val consumer = root.getCanonicalFile
    require(!source.toPath.startsWith(consumer.toPath) && !consumer.toPath.startsWith(source.toPath),
      "CNCF source and consumer checkouts must be disjoint")
    require(new File(source, "build.sbt").isFile && new File(source,
      "src/main/scala/org/goldenport/cncf/processexecution/SbtExecution.scala").isFile,
      "Selected checkout must provide the CNCF typed SBT implementation")
    source
  }
}
