import org.goldenport.cozy.CozyPlugin.autoImport._
import org.goldenport.cozy.CozyProjectIdentityEvidence
import sbt.Keys.*

lazy val projectIdentityEvidence = settingKey[CozyProjectIdentityEvidence]("Admitted project.yaml component identity evidence")
lazy val skillSourceFiles = taskKey[Seq[(File, String)]]("Exact component-owned semantic skill sources")
lazy val skillBundle = taskKey[File]("Package the semantic skill sources as a standalone ZIP")
lazy val checkSkillBundleReproducibility = taskKey[File]("Verify skill ZIP bytes despite source timestamp and ordering changes")

lazy val root = project
  .in(file("."))
  .enablePlugins(org.goldenport.cozy.CozyPlugin)
  .settings(
    projectIdentityEvidence := ProjectYamlBuild.admitted(cozyProjectMetadata.value, scalaBinaryVersion.value),
    organization := ProjectYamlBuild.organization(projectIdentityEvidence.value, cozyProjectMetadata.value),
    moduleName := ProjectYamlBuild.moduleName(projectIdentityEvidence.value, cozyProjectMetadata.value),
    name := moduleName.value,
    version := ProjectYamlBuild.version(projectIdentityEvidence.value, cozyProjectMetadata.value),
    scalaVersion := ProjectYamlBuild.requiredValue(cozyProjectMetadata.value, "build.scalaVersion"),
    resolvers += Resolver.defaultLocal,
    resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven",
    libraryDependencies ++= ProjectYamlBuild.dependencies(cozyProjectMetadata.value),

    // CLI/server executable specs consume the official runtime artifacts.
    // Wire preparation into both test and testOnly via their compile dependency;
    // packaged catalog specs also need the current component JAR, not a
    // stale artifact left by a previous manual packageBin invocation.
    Test / compile := ((Test / compile) dependsOn (cozyPrepareRuntime, Compile / packageBin)).value,
    Test / fork := false,
    Test / parallelExecution := false,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,

    skillSourceFiles := {
      val sourceRoot = baseDirectory.value / "skills"
      val names = Seq("sm-goal-phase", "sm-split-phase", "sm-repository-sync")
      names.map { name =>
        val source = sourceRoot / name / "SKILL.md"
        if (!source.isFile) sys.error("Missing component-owned skill source: " + source)
        source -> ("skills/" + name + "/SKILL.md")
      }
    },
    Compile / resourceGenerators += Def.task {
      val destinationRoot = (Compile / resourceManaged).value / "META-INF" / "sm-workflow"
      val copies = skillSourceFiles.value.map { case (source, relativePath) =>
        source -> (destinationRoot / relativePath)
      }
      IO.copy(copies)
      copies.map(_._2)
    }.taskValue,
    skillBundle := {
      val sources = skillSourceFiles.value
      val archive = target.value / ("sm-workflow-skills-" + version.value + ".zip")
      SkillSourceArchive.packageSources(sources, archive)
      streams.value.log.info("Standalone skill sources: " + archive)
      archive
    },
    checkSkillBundleReproducibility := {
      val original = skillBundle.value
      val probeRoot = target.value / "skill-bundle-reproducibility"
      val copies = skillSourceFiles.value.map { case (source, relativePath) =>
        val copy = probeRoot / relativePath
        IO.copyFile(source, copy)
        java.nio.file.Files.setLastModifiedTime(copy.toPath, java.nio.file.attribute.FileTime.fromMillis(946684800000L))
        copy -> relativePath
      }
      val probe = SkillSourceArchive.packageSources(copies.reverse, probeRoot / "repacked.zip")
      if (!java.util.Arrays.equals(IO.readBytes(original), IO.readBytes(probe)))
        sys.error("Skill source ZIP differs after timestamp and input-order changes")
      streams.value.log.info("Skill source ZIP reproducibility verified: " + probe)
      probe
    },

    cozyGeneratorBackend := "cozy",
    cozyTargetDir := (Compile / sourceManaged).value / "scala",
    cozyDelegateProjectDir := None,
    cozyCarName := ProjectYamlBuild.carBaseName(projectIdentityEvidence.value, moduleName.value, version.value),
    cozyManifestMetadata ++= ProjectYamlBuild.manifestMetadata(projectIdentityEvidence.value, cozyProjectMetadata.value)
  )

// The product is Scala 3, not an sbt plugin. Keep scripted's Scala 2.12 runner
// in a separate test-only project; never publish this harness or the component
// merely to execute it. One top-level scripted owns every child build here.
lazy val runtimeScripted = project.in(file("spec/runtime-scripted"))
  .enablePlugins(sbt.ScriptedPlugin)
  .settings(
    scalaVersion := "2.12.18",
    publish / skip := true,
    sbt.ScriptedPlugin.autoImport.scriptedSbt := "1.9.7",
    sbt.ScriptedPlugin.autoImport.sbtTestDirectory := (root / baseDirectory).value / "src/sbt-test",
    sbt.ScriptedPlugin.autoImport.scriptedBufferLog := false,
    sbt.ScriptedPlugin.autoImport.scriptedParallelInstances := 1,
    sbt.ScriptedPlugin.autoImport.scriptedDependencies := {
      val classpath = (root / Test / fullClasspath).value.files
      val selection = new java.util.Properties()
      selection.setProperty("classpath", classpath.mkString(java.io.File.pathSeparator))
      selection.setProperty("root", (root / baseDirectory).value.getAbsolutePath)
      selection.setProperty("launcher", sbt.ScriptedPlugin.autoImport.sbtLauncher.value.getAbsolutePath)
      selection.setProperty("owner", java.lang.ProcessHandle.current().pid().toString)
      val file = (root / target).value / "sm-workflow/work/scripted-operation-selection.properties"
      IO.createDirectory(file.getParentFile)
      val output = new java.io.FileOutputStream(file)
      try selection.store(output, "Selected inputs for this scripted invocation; not an integrity seal")
      finally output.close()
    },
    sbt.ScriptedPlugin.autoImport.scriptedLaunchOpts ++= Seq(
      "-Dsbt.server.autostart=false", "-Dsbt.supershell=false",
      "-Dsmworkflow.spec.selection=" + ((root / target).value / "sm-workflow/work/scripted-operation-selection.properties").getAbsolutePath
    )
  )
