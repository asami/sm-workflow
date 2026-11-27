import org.goldenport.cozy.CozyPlugin.autoImport._
import org.goldenport.cozy.CozyProjectIdentityEvidence
import org.goldenport.cozy.CozyDevelopmentArtifactsPlugin.autoImport._
import org.goldenport.cozy.CozyDevelopmentArtifactOverlay.{Coordinate, ProjectReference}
import sbt.Keys.*
lazy val scalaLintInput: Project = project.in(file("scala-lint-rules/input"))
  .settings(
    scalaVersion := "3.3.8",
    semanticdbEnabled := true,
    // Qualification inputs use the project's actual published Goldenport APIs;
    // these dependencies never enter the independent rules artifact.
    libraryDependencies ++= ProjectYamlBuild.dependencies((LocalProject("root") / cozyProjectMetadata).value),
    publish / skip := true
  )

// Compile the consumer's handwritten Main and Test sources with the real Scala 3
// compiler. This project does not load the standalone fixture's nested build.
lazy val scalaLintConsumerInput: Project = project.in(file("scala-lint-rules/consumer-input"))
  .settings(
    scalaVersion := "3.3.8",
    semanticdbEnabled := true,
    publish / skip := true,
    Compile / unmanagedSourceDirectories := Seq(
      baseDirectory.value.getParentFile / "fixtures" / "no-project-config" / "src" / "main" / "scala"),
    Test / unmanagedSourceDirectories := Seq(
      baseDirectory.value.getParentFile / "fixtures" / "no-project-config" / "src" / "test" / "scala")
  )

// The host runs Scala 2.13; analyzed source remains Scala 3.3.8.
// This artifact has no dependency on the application or any CNCF runtime.
lazy val scalaLintRules: Project = project.in(file("scala-lint-rules"))
  .disablePlugins(_root_.scalafix.sbt.ScalafixPlugin)
  .enablePlugins(_root_.scalafix.sbt.ScalafixTestkitPlugin)
  .settings(
    organization := "org.simplemodeling",
    moduleName := "scala-lint-rules",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "2.13.14",
    libraryDependencies ++= Seq(
      "ch.epfl.scala" %% "scalafix-core" % "0.12.1" % Provided,
      "ch.epfl.scala" % "scalafix-interfaces" % "0.12.1" % Provided,
      "ch.epfl.scala" % "scalafix-cli_2.13.14" % "0.12.1" % Test,
      "com.typesafe" % "config" % "1.4.3",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    ),
    Compile / unmanagedSources ++= Seq("ScalaLintProfiles.scala", "ScalaLintInput.scala", "ScalaLintDelta.scala", "ScalaLintAdmission.scala", "ScalaLintSemanticdb.scala").map(
      name => baseDirectory.value.getParentFile / "project" / name),
    scalafixTestkitInputClasspath := (scalaLintInput / Compile / fullClasspath).value,
    scalafixTestkitInputSourceDirectories := (scalaLintInput / Compile / unmanagedSourceDirectories).value,
    scalafixTestkitInputScalacOptions := (scalaLintInput / Compile / scalacOptions).value,
    scalafixTestkitInputScalaVersion := (scalaLintInput / scalaVersion).value,
    scalafixTestkitOutputSourceDirectories := Nil,
    Test / resourceGenerators += Def.task {
      val props = new java.util.Properties()
      props.setProperty("sourceroot", (ThisBuild / baseDirectory).value.getAbsolutePath)
      props.setProperty("scalaVersion", (scalaLintConsumerInput / scalaVersion).value)
      props.setProperty("classpath", (scalaLintConsumerInput / Test / fullClasspath).value.files.mkString(java.io.File.pathSeparator))
      props.setProperty("mainMeta", (scalaLintConsumerInput / Compile / semanticdbTargetRoot).value.getAbsolutePath)
      props.setProperty("main", (scalaLintConsumerInput / Compile / sources).value.mkString(java.io.File.pathSeparator))
      props.setProperty("test", (scalaLintConsumerInput / Test / sources).value.mkString(java.io.File.pathSeparator))
      props.setProperty("mainScalacOptions", (scalaLintConsumerInput / Compile / scalacOptions).value.mkString("|"))
      props.setProperty("testScalacOptions", (scalaLintConsumerInput / Test / scalacOptions).value.mkString("|"))
      props.setProperty("fixture", (baseDirectory.value / "fixtures" / "no-project-config").getAbsolutePath)
      val destination = (Test / resourceManaged).value / "scala-lint-consumer.properties"
      IO.write(props, "Actual consumer compiler inputs; not a testkit invocation", destination)
      Seq(destination)
    }.taskValue
  )

lazy val projectIdentityEvidence = settingKey[CozyProjectIdentityEvidence]("Admitted project.yaml component identity evidence")
lazy val sharedScalaLintReporter = settingKey[Option[org.simplemodeling.scalalint.ScalaLintAdmission.Reporter]]("Selected native lint attribution reporter")
lazy val sharedScalaLintMain = taskKey[Unit]("Pinned check-only Scalafix over captured handwritten Compile delta")
lazy val sharedScalaLintTest = taskKey[Unit]("Pinned check-only Scalafix over captured handwritten Test delta")
lazy val sharedScalaLintCheck = taskKey[Unit]("Report all selected diagnostics and block affected declarations")
def requireOnlyLintFindings(result: Result[Unit]): Unit = result match {
  case Value(_) => ()
  case Inc(failure) =>
    val exceptions = Incomplete.allExceptions(failure)
    if (exceptions.isEmpty || !exceptions.forall {
      case error: _root_.scalafix.sbt.ScalafixFailed => error.errors.nonEmpty && error.errors.forall(_ == _root_.scalafix.interfaces.ScalafixError.LinterError)
      case _ => false
    }) throw failure
}
lazy val skillSourceFiles = taskKey[Seq[(File, String)]]("Exact component-owned semantic skill sources")
lazy val skillBundle = taskKey[File]("Package the semantic skill sources as a standalone ZIP")
lazy val checkSkillBundleReproducibility = taskKey[File]("Verify skill ZIP bytes despite source timestamp and ordering changes")
lazy val prepareTestArtifacts = taskKey[Unit]("Prepare the fresh component JAR and reproducible skill distribution for tests")
lazy val runtimeTestOnly = inputKey[Unit]("Select canonical metadata, prepare selected runtime requirements and execute exact tests")
lazy val labelledFullTests = taskKey[Seq[LabelledTests.Selected]]("Canonical default FULL operations")
lazy val testAdmission = taskKey[Unit]("Run canonical ordinary ADMISSION operations")
lazy val labelledTestSelection = inputKey[Seq[TestDefinition]]("Inspect canonical test selection without executing selected bodies")
lazy val preparePackagedTestCar = taskKey[Unit]("Build and locate the candidate CAR for selected packaged-runtime tests")
lazy val prepareScalaToolFixture = taskKey[Unit]("Resolve isolated Scala tool classpaths for selected deterministic repair tests")
lazy val scalafmtToolFixture = project.in(file("target/sm-workflow/scala-tool-build/scalafmt"))
  .settings(
    scalaVersion := "2.13.16",
    libraryDependencies += "org.scalameta" %% "scalafmt-cli" % "3.8.6",
    publish / skip := true
  )
lazy val scalafixToolFixture = project.in(file("target/sm-workflow/scala-tool-build/scalafix"))
  .settings(
    scalaVersion := "2.13.14",
    libraryDependencies += "ch.epfl.scala" % "scalafix-cli_2.13.14" % "0.12.1",
    publish / skip := true
  )
lazy val prepareFullTests = taskKey[Unit]("Prepare prerequisites required by default FULL metadata")
lazy val labelledTestExecution = inputKey[Unit]("Internal standard SBT execution for exact metadata-selected suite operations")
def needsScalaToolFixture(selected: Seq[LabelledTests.Selected]): Boolean =
  selected.exists(test => test.requiresPackagedCar &&
    test.definition.name == "org.simplemodeling.textus.smworkflow.admission.DeterministicRepairSpec")
lazy val cncfSourceDirectory = SmWorkflowCncfDevelopmentBuild.resolve(file("."), sys.env.get("TEXTUS_SM_WORKFLOW_CNCF_SOURCE_ROOT"))
lazy val cncfSourceProject = cncfSourceDirectory.map(source => RootProject(source.toURI))
lazy val textusAiDevelopmentModule = "org.simplemodeling.textus" %% "textus-ai-runtime" % "0.2.3-SNAPSHOT"
lazy val selectedTextusAiDependency = settingKey[ModuleID]("Selected task-private Textus AI dependency")
lazy val phase251AllowanceFixtureJar = settingKey[Option[File]]("Verified task-private provider JAR for offline allowance fixtures")
lazy val validateCncfSource = taskKey[Unit]("Validate the selected source dependency against the declared CNCF coordinates")
lazy val validateCncfPublicationClasspath = taskKey[Unit]("Verify the normal attributed source JAR in the application publication closure")
lazy val strictGeneratedQualification = settingKey[Boolean]("Explicitly qualify fresh B04 generated strict consumers")
lazy val strictCoreQualification = settingKey[Boolean]("Explicitly qualify C04 consumers of the adopted Core collection API")
lazy val strictCoreGeneratedSourceDir = settingKey[Option[File]]("Explicit adopted Cozy C3 generated-output resource")
lazy val strictCoreGeneratedSources = taskKey[Seq[File]]("Verify and stage the adopted Cozy C3 compiler and its direct ABI")
lazy val cncfSourceSettings: Seq[Def.Setting[_]] = cncfSourceProject.toSeq.flatMap { source => Seq(
  cozyDevelopmentProjectReferences := Some(Seq(
    ProjectReference(ProjectRef(thisProjectRef.value.build, "scalaLintRules"),
      Coordinate("org.simplemodeling", "scala-lint-rules_2.13", "0.1.0-SNAPSHOT"), "scalafix", "scalafix"),
    ProjectReference((source / thisProjectRef).value,
      Coordinate("org.goldenport", "goldenport-cncf_3", "0.5.6-SNAPSHOT"),
      "compile->compile;runtime->runtime", "source")
  )),
  libraryDependencies ~= (_.filterNot(m => m.organization == "org.goldenport" && m.name == "goldenport-cncf")),
  validateCncfSource := {
    require((source / organization).value == "org.goldenport" && (source / moduleName).value == "goldenport-cncf" &&
      (source / version).value == "0.5.6-SNAPSHOT" && (source / scalaVersion).value == scalaVersion.value,
      "Selected CNCF source coordinates differ from project.yaml")
  },
  Compile / compile := ((Compile / compile) dependsOn validateCncfSource).value,
  cozyCncfRuntimeDescriptorInput := Some((source / TaskKey[File]("generateCncfRuntimeDescriptor")).value),
  cozyCncfRuntimeArtifactInput := Some((source / Compile / packageBin).value),
  Compile / dependencyClasspath := SmWorkflowCncfDevelopmentBuild.runtimeClasspath(
    (Compile / dependencyClasspath).value, (source / Compile / classDirectory).value,
    (source / Compile / packageBin).value, (source / organization).value,
    (source / moduleName).value, (source / scalaBinaryVersion).value, (source / version).value),
  validateCncfPublicationClasspath := {
    val classpath = (Compile / dependencyClasspath).value
    val archive = (source / Compile / packageBin).value.getCanonicalFile
    val classes = (source / Compile / classDirectory).value.getCanonicalFile
    val roots = classpath.filter(_.get(moduleID.key).exists(module =>
      module.organization == "org.goldenport" && module.name == "goldenport-cncf_3"))
    require(roots.size == 1 && roots.head.data.getCanonicalFile == archive &&
      roots.head.get(artifact.key).exists(value => value.extension == "jar" && value.classifier.isEmpty),
      "Publication must contain exactly the selected normally packaged CNCF root")
    require(!classpath.exists(_.data.getCanonicalFile == classes),
      "Publication cannot contain a source classes directory")
    streams.value.log.info("Source publication closure: one attributed CNCF packageBin JAR; normal transitive dependencies retained")
  }
) }

// A real authoring target, separate from deliberately rule-violating lint inputs
// and legacy mixed consumer scopes. Compile owns the B02 model and selected B04
// generated representatives; Test discovers each consumer specification once.
lazy val scalaStrictAuthoring: Project = project.in(file("scala-lint-rules/strict-authoring"))
  .dependsOn(scalaLintRules % ScalafixConfig)
  .enablePlugins(org.goldenport.cozy.CozyPlugin)
  .settings(
    cozyDevelopmentProjectReferences := Some(Seq(
      ProjectReference(ProjectRef(thisProjectRef.value.build, "scalaLintRules"),
        Coordinate("org.simplemodeling", "scala-lint-rules_2.13", "0.1.0-SNAPSHOT"), "scalafix", "scalafix")
    )),
    cozyDevelopmentModules := Seq.empty,
    scalaVersion := "3.3.8",
    scalacOptions ++= Seq(
      "-language:strictEquality", "-Yexplicit-nulls", "-Ysafe-init",
      "-Wvalue-discard", "-Wnonunit-statement", "-Wunused:imports,privates,locals",
      "-unchecked", "-deprecation", "-feature", "-Werror"),
    semanticdbEnabled := true,
    libraryDependencies ++= ProjectYamlBuild.dependencies((LocalProject("root") / cozyProjectMetadata).value)
      .filterNot(_.organization == "org.scalatest"),
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    publish / skip := true,
    strictGeneratedQualification := {
      sys.env.get("SM_WORKFLOW_STRICT_GENERATED_QUALIFICATION") match {
        case Some("true") => true
        case Some("false") => false
        case Some(_) => sys.error("SM_WORKFLOW_STRICT_GENERATED_QUALIFICATION must be true or false")
        case None => cozyProjectConfig.value.boolean("qualification.strict_generated").getOrElse(false)
      }
    },
    strictCoreQualification := {
      sys.env.get("SM_WORKFLOW_STRICT_CORE_QUALIFICATION") match {
        case Some("true") => true
        case Some("false") => false
        case Some(_) => sys.error("SM_WORKFLOW_STRICT_CORE_QUALIFICATION must be true or false")
        case None => cozyProjectConfig.value.boolean("qualification.strict_core").getOrElse(false)
      }
    },
    strictCoreGeneratedSourceDir := {
      val basedir = baseDirectory.value
      sys.env.get("SM_WORKFLOW_STRICT_CORE_GENERATED_SOURCE_DIR")
        .orElse(cozyProjectConfig.value.value("qualification.core_generated_source_dir"))
        .map { path =>
          val selected = file(path)
          if (selected.isAbsolute) selected else basedir / path
        }
    },
    strictCoreGeneratedSources := {
      val selected = strictCoreGeneratedSourceDir.value.getOrElse(sys.error(
        "C04 qualification requires SM_WORKFLOW_STRICT_CORE_GENERATED_SOURCE_DIR or qualification.core_generated_source_dir " +
          "selecting adopted Cozy dd1b826e core-c3 output"))
      require(selected.isDirectory, "C04 adopted Cozy output directory is unavailable: " + selected)
      val expected = Map(
        "CompositeStateMachineActionProgramAbi.scala" -> "d216a82c581cbba69dcb3b73fc3a2de0b2e663f9bdaea3e68653dcfd8defad19",
        "LogicalActionCompiler.scala" -> "f3daf8291539c06318829386d01c9c31aa58a5db13fcc9b2051bc320e85bec3a")
      val sources = (selected ** "*.scala").get.filter(source => expected.contains(source.getName))
      require(sources.map(_.getName).toSet == expected.keySet && sources.size == expected.size,
        "C04 requires exactly one adopted LogicalActionCompiler.scala and CompositeStateMachineActionProgramAbi.scala")
      val destinationdir = (Compile / sourceManaged).value / "core-c3"
      sources.sortBy(_.getName).map { source =>
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(IO.readBytes(source))
          .map(byte => f"${byte & 0xff}%02x").mkString
        require(digest == expected(source.getName),
          "C04 generated source differs from adopted Cozy dd1b826e core-c3 output: " + source.getName +
            "; select the admitted workflow-semantic-outcome fixture with generator JVM property " +
            "-Dcozy.generation.logical-action-membership=core-c3")
        val destination = destinationdir / source.getName
        IO.copyFile(source, destination)
        destination
      }
    },
    // The owning runtime first publishes/selects the adopted Core source through
    // normal resolution and retains the actual artifact identity. A version label
    // alone does not establish delivery. Missing APIs fail explicit qualification.
    libraryDependencies ++= {
      if (strictCoreQualification.value)
        Seq("org.goldenport" %% "goldenport-core" % "0.4.3-SNAPSHOT")
      else Nil
    },
    Compile / unmanagedSourceDirectories ++= {
      val root = baseDirectory.value / "src"
      if (strictCoreQualification.value)
        Seq(root / "core/scala") ++
          (if (strictGeneratedQualification.value) Seq(root / "generated-core/scala") else Nil)
      else Nil
    },
    cozyGeneratorBackend := "cozy",
    cozyTargetDir := (Compile / sourceManaged).value / "scala",
    cozySkipUnchangedGeneration := {
      if (strictGeneratedQualification.value) false
      else cozyProjectConfig.value.boolean("generation.skip_unchanged").getOrElse(true)
    },
    // Ordinary builds retain B2 without requiring a locally selected generator.
    // taskDyn keeps cozyGenerate outside the default task graph, even if configured.
    Compile / sourceGenerators := Seq(Def.taskDyn {
      if (strictGeneratedQualification.value) {
        require(cozyDelegateProjectDir.value.nonEmpty,
          "B04 qualification requires generation.delegate.project_dir selecting the qualified local Cozy project")
        Def.task {
          // sbt-cozy validates the selected project and owns generator execution.
          val generated = cozyGenerate.value
          val names = Set("CountryCode.scala", "RegionCode.scala")
          val selected = generated.filter(source => names.contains(source.getName))
          require(selected.map(_.getName).toSet == names && selected.size == names.size && selected.forall(_.isFile),
            "B04 requires exactly both generated library value types")
          selected
        }
      } else Def.task { Seq.empty[File] }
    }.taskValue, Def.taskDyn {
      if (strictCoreQualification.value) Def.task { strictCoreGeneratedSources.value }
      else Def.task { Seq.empty[File] }
    }.taskValue),
    // This library fixture has no CNCF ABI resources. The plugin's default
    // resource generator would otherwise invoke cozyGenerate unconditionally.
    Compile / resourceGenerators := Nil,
    Test / unmanagedSourceDirectories := Nil,
    Test / unmanagedSources := Seq(
      baseDirectory.value.getParentFile.getParentFile /
        "src/test/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowStrictComparisonSpec.scala") ++
      (if (strictGeneratedQualification.value) Seq(
        baseDirectory.value / "src/test/scala/org/simplemodeling/textus/smworkflow/admission/GeneratedStrictComparisonSpec.scala")
      else Nil) ++
      (if (strictCoreQualification.value) Seq(
        baseDirectory.value / "src/test/scala/org/simplemodeling/textus/smworkflow/admission/CoreCollectionComparisonSpec.scala",
        baseDirectory.value / "src/test/scala/org/simplemodeling/textus/smworkflow/admission/GeneratedLogicalActionCompilerSpec.scala") ++
        (if (strictGeneratedQualification.value) Seq(
          baseDirectory.value / "src/test/scala/org/simplemodeling/textus/smworkflow/admission/GeneratedCoreCollectionComparisonSpec.scala")
        else Nil)
      else Nil),
    scalafixScalaBinaryVersion := "2.13",
    scalafixConfig := (LocalProject("root") / scalafixConfig).value,
    sharedScalaLintReporter := (LocalProject("root") / sharedScalaLintReporter).value,
    Compile / scalafix / unmanagedSources := {
      val sources = (Compile / unmanagedSources).value
      sharedScalaLintReporter.value.fold(sources)(reporter => sources.filter(reporter.selected))
    },
    Test / scalafix / unmanagedSources := {
      val sources = (Test / unmanagedSources).value
      sharedScalaLintReporter.value.fold(sources)(reporter => sources.filter(reporter.selected))
    },
    Compile / scalafix / managedSources := Nil,
    Test / scalafix / managedSources := Nil,
    Compile / scalafix / aggregate := false,
    Test / scalafix / aggregate := false,
    sharedScalaLintMain := Def.taskDyn {
      val selected = sharedScalaLintReporter.value.getOrElse(sys.error("Selected native lint input is absent"))
      val selectedSources = (Compile / unmanagedSources).value.filter(selected.selected)
      if (selectedSources.isEmpty) Def.task { () }
      else Def.taskDyn {
        (Compile / compile).value
        val prepared = org.simplemodeling.scalalint.ScalaLintSemanticdb.prepare(
          (ThisBuild / baseDirectory).value.toPath, (Compile / semanticdbTargetRoot).value.toPath,
          (Compile / sources).value.map(_.toPath), selectedSources.map(_.toPath),
          (target.value / "scala-lint" / "semanticdb-input").toPath)
        val args = try prepared.map(org.simplemodeling.scalalint.ScalaLintSemanticdb.argument).getOrElse(" --check")
        catch { case error: Throwable =>
          prepared.foreach(org.simplemodeling.scalalint.ScalaLintSemanticdb.delete)
          throw error
        }
        Def.task {
          val result = (Compile / scalafix).toTask(args).result.value
          try requireOnlyLintFindings(result)
          finally prepared.foreach(org.simplemodeling.scalalint.ScalaLintSemanticdb.delete)
        }
      }
    }.value,
    sharedScalaLintTest := Def.taskDyn {
      val selected = sharedScalaLintReporter.value.getOrElse(sys.error("Selected native lint input is absent"))
      val selectedSources = (Test / unmanagedSources).value.filter(selected.selected)
      if (selectedSources.isEmpty) Def.task { () }
      else Def.taskDyn {
        (Test / compile).value
        val prepared = org.simplemodeling.scalalint.ScalaLintSemanticdb.prepare(
          (ThisBuild / baseDirectory).value.toPath, (Test / semanticdbTargetRoot).value.toPath,
          (Test / sources).value.map(_.toPath), selectedSources.map(_.toPath),
          (target.value / "scala-lint" / "semanticdb-input").toPath)
        val args = try prepared.map(org.simplemodeling.scalalint.ScalaLintSemanticdb.argument).getOrElse(" --check")
        catch { case error: Throwable =>
          prepared.foreach(org.simplemodeling.scalalint.ScalaLintSemanticdb.delete)
          throw error
        }
        Def.task {
          val result = (Test / scalafix).toTask(args).result.value
          try requireOnlyLintFindings(result)
          finally prepared.foreach(org.simplemodeling.scalalint.ScalaLintSemanticdb.delete)
        }
      }
    }.value
  )

lazy val root: Project = project
  .in(file("."))
  .aggregate(scalaLintRules, scalaStrictAuthoring)
  .dependsOn(scalaLintRules % ScalafixConfig)
  .dependsOn(cncfSourceProject.toSeq.map(source => source % "compile->compile;runtime->runtime"): _*)
  .enablePlugins(org.goldenport.cozy.CozyPlugin)
  .settings(inConfig(Test)(Defaults.testTaskOptions(labelledTestExecution) ++
    Defaults.defaultTestTasks(labelledTestExecution) ++ Seq(
      labelledTestExecution / testFilter := (Defaults.selectedFilter _),
      labelledTestExecution := Defaults.inputTests(labelledTestExecution).evaluated
    )))
  .settings(
    phase251AllowanceFixtureJar := {
      val path = sys.env.get("SM_WORKFLOW_SPEC_P251_ALLOWANCE_JAR")
      val expected = sys.env.get("SM_WORKFLOW_SPEC_P251_ALLOWANCE_SHA256")
      (path, expected) match {
        case (None, None) => None
        case (Some(p), Some(hash)) if p.nonEmpty && hash.matches("[0-9a-fA-F]{64}") =>
          val jar = file(p)
          require(jar.isFile && jar.canRead, "P251 allowance fixture JAR must be a readable file")
          val digest = java.security.MessageDigest.getInstance("SHA-256")
          val input = new java.io.FileInputStream(jar)
          try {
            val buffer = new Array[Byte](8192)
            var count = input.read(buffer)
            while (count != -1) {
              digest.update(buffer, 0, count)
              count = input.read(buffer)
            }
          } finally input.close()
          val actual = digest.digest().map(b => f"${b & 0xff}%02x").mkString
          require(actual.equalsIgnoreCase(hash), "P251 allowance fixture JAR SHA-256 mismatch")
          Some(jar)
        case _ => sys.error("Set both P251 allowance fixture JAR and SHA-256, with a valid SHA-256")
      }
    },
    Test / unmanagedJars ++= phase251AllowanceFixtureJar.value.toSeq.map(Attributed.blank),
    cozyDevelopmentProjectReferences := Some(Seq(
      ProjectReference(ProjectRef(thisProjectRef.value.build, "scalaLintRules"),
        Coordinate("org.simplemodeling", "scala-lint-rules_2.13", "0.1.0-SNAPSHOT"), "scalafix", "scalafix")
    )),
    cozyDevelopmentModules := Seq(textusAiDevelopmentModule),
    selectedTextusAiDependency := cozyDevelopmentDependency(textusAiDevelopmentModule).value,
    // The task-private selection is supplied by the runtime; ordinary builds
    // must not require the producer's unpublished base version.
    libraryDependencies ++= {
      if (sys.props.contains("sm.artifact.selection")) {
        val selected = selectedTextusAiDependency.value
        // The scoped producer publishes packageBin only, even if its Ivy
        // descriptor also advertises source and documentation artifacts.
        val binary = selected.artifacts(Artifact(selected.name, "jar", "jar"))
        Seq(if (cncfSourceProject.nonEmpty) binary.exclude("org.goldenport", "goldenport-cncf_3") else binary)
      } else Nil
    },
    projectIdentityEvidence := ProjectYamlBuild.admitted(cozyProjectMetadata.value, scalaBinaryVersion.value),
    organization := ProjectYamlBuild.organization(projectIdentityEvidence.value, cozyProjectMetadata.value),
    moduleName := ProjectYamlBuild.moduleName(projectIdentityEvidence.value, cozyProjectMetadata.value),
    name := moduleName.value,
    version := ProjectYamlBuild.version(projectIdentityEvidence.value, cozyProjectMetadata.value),
    scalaVersion := ProjectYamlBuild.requiredValue(cozyProjectMetadata.value, "build.scalaVersion"),
    resolvers += Resolver.defaultLocal,
    resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven",
    libraryDependencies ++= ProjectYamlBuild.dependencies(cozyProjectMetadata.value),
    libraryDependencies += "com.typesafe" % "config" % "1.4.3",
    // Share configuration data/code, without adding the rule engine to the CAR.
    Compile / unmanagedSources ++= Seq("ScalaLintProfiles.scala", "ScalaLintInput.scala", "ScalaLintDelta.scala").map(
      name => baseDirectory.value / "project" / name),
    Compile / resourceGenerators += Def.task {
      val source = baseDirectory.value / "scala-lint-rules" / "src" / "main" / "resources" / "scala-lint"
      val destination = (Compile / resourceManaged).value / "scala-lint"
      IO.copyDirectory(source, destination)
      (destination ** "*").get.filter(_.isFile)
    }.taskValue,

    scalafixScalaBinaryVersion := "2.13",
    semanticdbEnabled := true,
    sharedScalaLintReporter := org.simplemodeling.scalalint.ScalaLintInput.selected(sys.props.toMap).map(input =>
      new org.simplemodeling.scalalint.ScalaLintAdmission.Reporter(baseDirectory.value.toPath, input, text => System.out.println(text))),
    // sbt-scalafix 0.12.1 reads its callback at ThisBuild, including both scopes.
    ThisBuild / scalafixCallback := (LocalRootProject / sharedScalaLintReporter).value
      .getOrElse((ThisBuild / scalafixCallback).value),
    sharedScalaLintMain := Def.taskDyn {
      val selected = sharedScalaLintReporter.value.getOrElse(sys.error("Selected native lint input is absent"))
      val selectedSources = (Compile / unmanagedSources).value.filter(selected.selected)
      if (selectedSources.isEmpty) Def.task { () }
      else Def.taskDyn {
        (Compile / compile).value
        val prepared = org.simplemodeling.scalalint.ScalaLintSemanticdb.prepare(
          (ThisBuild / baseDirectory).value.toPath, (Compile / semanticdbTargetRoot).value.toPath,
          (Compile / sources).value.map(_.toPath), selectedSources.map(_.toPath),
          (target.value / "scala-lint" / "semanticdb-input").toPath)
        val args = try prepared.map(org.simplemodeling.scalalint.ScalaLintSemanticdb.argument).getOrElse(" --check")
        catch { case error: Throwable =>
          prepared.foreach(org.simplemodeling.scalalint.ScalaLintSemanticdb.delete)
          throw error
        }
        Def.task {
          val result = (Compile / scalafix).toTask(args).result.value
          try requireOnlyLintFindings(result)
          finally prepared.foreach(org.simplemodeling.scalalint.ScalaLintSemanticdb.delete)
        }
      }
    }.value,
    sharedScalaLintTest := Def.taskDyn {
      val selected = sharedScalaLintReporter.value.getOrElse(sys.error("Selected native lint input is absent"))
      val selectedSources = (Test / unmanagedSources).value.filter(selected.selected)
      if (selectedSources.isEmpty) Def.task { () }
      else Def.taskDyn {
        (Test / compile).value
        val prepared = org.simplemodeling.scalalint.ScalaLintSemanticdb.prepare(
          (ThisBuild / baseDirectory).value.toPath, (Test / semanticdbTargetRoot).value.toPath,
          (Test / sources).value.map(_.toPath), selectedSources.map(_.toPath),
          (target.value / "scala-lint" / "semanticdb-input").toPath)
        val args = try prepared.map(org.simplemodeling.scalalint.ScalaLintSemanticdb.argument).getOrElse(" --check")
        catch { case error: Throwable =>
          prepared.foreach(org.simplemodeling.scalalint.ScalaLintSemanticdb.delete)
          throw error
        }
        Def.task {
          val result = (Test / scalafix).toTask(args).result.value
          try requireOnlyLintFindings(result)
          finally prepared.foreach(org.simplemodeling.scalalint.ScalaLintSemanticdb.delete)
        }
      }
    }.value,
    sharedScalaLintCheck := {
      (scalaStrictAuthoring / Test / compile).value
      sharedScalaLintMain.value
      sharedScalaLintTest.value
      (scalaStrictAuthoring / sharedScalaLintMain).value
      (scalaStrictAuthoring / sharedScalaLintTest).value
      sharedScalaLintReporter.value.getOrElse(sys.error("Selected native lint input is absent")).finish()
    },
    // A present typed input is authoritative. Legacy fixed tasks keep their
    // original project configuration when the host did not select this input.
    scalafixConfig := org.simplemodeling.scalalint.ScalaLintInput.selected(sys.props.toMap).map { selected =>
      val destination = target.value / "scala-lint" / "effective-input.conf"
      IO.write(destination, selected.json, java.nio.charset.StandardCharsets.UTF_8)
      destination
    }.orElse(Some(baseDirectory.value / ".scalafix.conf")),
    // The local rule module uses ScalafixConfig, never the CAR runtime classpath.
    // Lint handwritten Compile and Test; generated sources stay with their owner.
    Compile / scalafix / unmanagedSources := {
      val sources = (Compile / unmanagedSources).value
      sharedScalaLintReporter.value.fold(sources)(reporter => sources.filter(reporter.selected))
    },
    Compile / scalafix / managedSources := Nil,
    Compile / scalafix / aggregate := false,
    Test / scalafix / unmanagedSources := {
      val sources = (Test / unmanagedSources).value
      sharedScalaLintReporter.value.fold(sources)(reporter => sources.filter(reporter.selected))
    },
    Test / scalafix / managedSources := Nil,
    Test / scalafix / aggregate := false,
    Test / resourceGenerators += Def.task {
      val archive = (scalaLintRules / Compile / packageBin).value.getCanonicalFile
      val properties = new java.util.Properties()
      properties.setProperty("ruleArtifact", archive.toURI.toASCIIString)
      val destination = (Test / resourceManaged).value / "phase81-scala-lint.properties"
      IO.write(properties, "Actual normally packaged independent rule artifact for connected native fixtures", destination)
      Seq(destination)
    }.taskValue,
    prepareTestArtifacts := {
      (Compile / packageBin).value
      checkSkillBundleReproducibility.value
      ()
    },
    // This one suite belongs exclusively to scalaStrictAuthoring/Test. Root's
    // canonical metadata inventory and testOnly selection retain their scope.
    Test / unmanagedSources := {
      val strictspec = baseDirectory.value /
        "src/test/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowStrictComparisonSpec.scala"
      val fixtureSources = Set(
        baseDirectory.value / "src/test/scala/org/simplemodeling/textus/smworkflow/admission/Phase251OfflineAllowanceIntegrationSpec.scala",
        baseDirectory.value / "src/test/scala/org/simplemodeling/textus/ai/runtime/Phase251OfflineAllowanceFixture.scala",
        baseDirectory.value / "src/test/scala/org/simplemodeling/textus/ai/runtime/Phase253TwoGroupAllowanceFixture.scala",
        baseDirectory.value / "src/test/scala/org/simplemodeling/textus/smworkflow/admission/Phase253TwoGroupAllowanceIntegrationSpec.scala"
      )
      (Test / unmanagedSources).value.filterNot(source =>
        source == strictspec || (phase251AllowanceFixtureJar.value.isEmpty && fixtureSources(source)))
    },
    Test / compile := {
      val compiled = (Test / compile).value
      val classes = (Test / classDirectory).value
      LabelledTests.writeInventory(classes +: (Test / dependencyClasspath).value.files,
        classes, version.value, target.value / "sm-workflow" / "validation-inventory.json")
      IO.writeLines(target.value / "sm-workflow" / "validation-classpath.txt",
        (classes +: (Test / dependencyClasspath).value.files).map(_.getCanonicalPath))
      compiled
    },
    labelledFullTests := LabelledTests.select((Test / fullClasspath).value.files,
      (Test / classDirectory).value, version.value, "FULL", (Test / definedTests).value),
    Test / test / definedTests := labelledFullTests.value.map(_.definition),
    preparePackagedTestCar := {
      val car = cozyBuildCar.value
      IO.write(target.value / "sm-workflow" / "packaged-test-car.txt", car.getCanonicalPath, java.nio.charset.StandardCharsets.UTF_8)
    },
    prepareScalaToolFixture := {
      val format = (scalafmtToolFixture / Compile / dependencyClasspath).value.files
      val fixes = (scalafixToolFixture / Compile / dependencyClasspath).value.files
      val directory = target.value / "sm-workflow" / "scala-tool-fixture"
      IO.write(directory / "scalafmt-classpath.txt",
        format.map(_.getCanonicalPath).mkString(java.io.File.pathSeparator), java.nio.charset.StandardCharsets.UTF_8)
      IO.write(directory / "scalafix-classpath.txt",
        fixes.map(_.getCanonicalPath).mkString(java.io.File.pathSeparator), java.nio.charset.StandardCharsets.UTF_8)
    },
    prepareFullTests := Def.taskDyn {
      val selected = labelledFullTests.value
      if (needsScalaToolFixture(selected)) Def.task {
        cozyPrepareRuntime.value
        prepareTestArtifacts.value
        preparePackagedTestCar.value
        prepareScalaToolFixture.value
      } else if (selected.exists(_.requiresPackagedCar)) Def.task { cozyPrepareRuntime.value; prepareTestArtifacts.value; preparePackagedTestCar.value; () }
      else if (selected.exists(_.requiresRuntime)) Def.task { cozyPrepareRuntime.value; prepareTestArtifacts.value; () }
      else Def.task { prepareTestArtifacts.value; () }
    }.value,
    Test / test := (Test / test).dependsOn(prepareFullTests).value,
    testAdmission := Def.taskDyn {
      val loader = (Test / testLoader).value
      val log = streams.value.log
      try {
        val selected = try LabelledTests.select((Test / fullClasspath).value.files,
          (Test / classDirectory).value, version.value, "ADMISSION", (Test / definedTests).value)
        catch {
          case scala.util.control.NonFatal(error) =>
            log.error("CNCF-ADMISSION-V1 unavailable")
            throw error
        }
        if (selected.isEmpty) {
          Def.task { log.info("CNCF-ADMISSION-V1 empty") }.andFinally(LabelledTests.closeLoader(loader))
        } else {
          val rows = selected.flatMap(s => s.definition.selectors.collect {
            case t: sbt.testing.TestSelector => s.definition.name -> t.testName()
          })
          require(rows.nonEmpty && rows.distinct.size == rows.size, "Empty or duplicate admission selection")
          def token(value: String): String = java.util.Base64.getUrlEncoder.withoutPadding()
            .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
          log.info("CNCF-ADMISSION-V1 selected " + rows.map { case (suite, name) => token(suite) + ":" + token(name) }.mkString(","))
          val frameworks = (Test / loadedTestFrameworks).value
          val config = (Test / labelledTestExecution / testExecution).value
          val forkTests = (Test / fork).value
          val resultLogger = (Test / labelledTestExecution / testResultLogger).value
          // The producer verifies one genuine ScalaTest summary per selected suite.
          val runs = selected.map { suite =>
            val execution = Def.valueStrict(LabelledTests.execute(Seq(suite), frameworks, loader, config, log,
              properties = Seq("-C", "org.simplemodeling.textus.smworkflow.testing.AdmissionSuccessReporter"), fork = forkTests))
            Def.task { resultLogger.run(log, execution.value, "Test / testAdmission") }
          }
          val done = Def.task { log.info("CNCF-ADMISSION-V1 completed") }
          val run = Def.sequential(runs :+ done)
          val prepared = if (needsScalaToolFixture(selected)) Def.taskDyn {
            cozyPrepareRuntime.value; prepareTestArtifacts.value; preparePackagedTestCar.value; prepareScalaToolFixture.value; run
          } else if (selected.exists(_.requiresPackagedCar)) Def.taskDyn {
            cozyPrepareRuntime.value; prepareTestArtifacts.value; preparePackagedTestCar.value; run
          } else if (selected.exists(_.requiresRuntime)) Def.taskDyn {
            cozyPrepareRuntime.value; prepareTestArtifacts.value; run
          } else Def.taskDyn { prepareTestArtifacts.value; run }
          prepared.andFinally(LabelledTests.closeLoader(loader))
        }
      } catch {
        case error: Throwable => LabelledTests.closeLoader(loader); throw error
      }
    }.value,
    labelledTestSelection := {
      val request = LabelledTests.request(sbt.complete.DefaultParsers.spaceDelimited("selection").parsed)
      val candidates = LabelledTests.candidates(request, (Test / definedTests).value)
      val selected = LabelledTests.select((Test / fullClasspath).value.files,
        (Test / classDirectory).value, version.value, request.purpose, candidates, request.exact, request.containing, request.properties)
      val operations = selected.map(_.definition.selectors.length).sum
      streams.value.log.info(s"Labelled selection: purpose=${request.purpose} suites=${selected.size} operations=$operations runtime=${selected.exists(_.requiresRuntime)}")
      selected.foreach(value => streams.value.log.info("Selected: " + value.definition.name))
      selected.map(_.definition)
    },
    // Use SBT's standard implementation at a separate internal key: a dynamic
    // self-reference to the overwritten testOnly key is undefined at runtime.
    // Native typed TestOnly receives the same selection/preparation.
    // runtimeTestOnly is only a descriptive alias.
    Test / testOnly := Def.inputTaskDyn {
      val arguments = sbt.complete.DefaultParsers.spaceDelimited("selection").parsed
      val request = LabelledTests.request(arguments)
      val rootTests = (Test / definedTests).value
      val childTests = (scalaStrictAuthoring / Test / definedTests).value.map(_.name).toSet
      val rootNames = rootTests.map(_.name).toSet
      // Only the ordinary aggregate command can rely on the child to report its
      // real results. Aliases, filters, patterns and unknown names still fail
      // through the root's canonical selection path.
      val directCommand = state.value.currentCommand.exists(_.commandLine.trim.split("\\s+").toSeq ==
        (Seq("testOnly") ++ arguments))
      val childOnly = (Test / testOnly / aggregate).value && directCommand &&
        request.purpose == "EXPLICIT" && arguments.nonEmpty &&
        arguments.forall(_.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")) &&
        arguments.forall(name => childTests(name) && !rootNames(name))
      if (childOnly) Def.task { () }
      else {
        val loader = (Test / testLoader).value
        try {
          val candidates = LabelledTests.candidates(request, rootTests)
          val selected = LabelledTests.select((Test / fullClasspath).value.files,
            (Test / classDirectory).value, version.value, request.purpose, candidates, request.exact, request.containing, request.properties)
          streams.value.log.info(s"Labelled execution: purpose=${request.purpose} suites=${selected.size} operations=${selected.map(_.definition.selectors.length).sum} runtime=${selected.exists(_.requiresRuntime)}")
          // One ordinary SBT execution produces its genuine aggregate report.
          // Definitions retain their own metadata-selected TestSelector arrays.
          val execution = Def.valueStrict(LabelledTests.execute(selected, (Test / loadedTestFrameworks).value,
            loader, (Test / labelledTestExecution / testExecution).value, streams.value.log,
            properties = request.properties, fork = (Test / fork).value))
          val resultLogger = (Test / labelledTestExecution / testResultLogger).value
          val log = streams.value.log
          val runs = Def.task {
            resultLogger.run(log, execution.value, "Test / labelledTestExecution")
          }
          val prepared = if (needsScalaToolFixture(selected)) Def.taskDyn {
            cozyPrepareRuntime.value
            prepareTestArtifacts.value
            preparePackagedTestCar.value
            prepareScalaToolFixture.value
            runs
          } else if (selected.exists(_.requiresPackagedCar)) Def.taskDyn {
            cozyPrepareRuntime.value
            prepareTestArtifacts.value
            preparePackagedTestCar.value
            runs
          } else if (selected.exists(_.requiresRuntime)) Def.taskDyn {
            cozyPrepareRuntime.value
            prepareTestArtifacts.value
            runs
          } else Def.taskDyn { prepareTestArtifacts.value; runs }
          prepared.andFinally(LabelledTests.closeLoader(loader))
        } catch {
          case error: Throwable => LabelledTests.closeLoader(loader); throw error
        }
      }
    }.evaluated,
    runtimeTestOnly := (Test / testOnly).evaluated,
    Test / fork := false,
    Test / parallelExecution := false,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,

    skillSourceFiles := {
      val sourceRoot = baseDirectory.value / "skills"
      val names = Seq("sm-goal-phase", "sm-split-phase", "sm-repository-sync", "sm-test-fix", "sm-goal-task", "sm-review-fix", "sm-delegate-task")
      val skills = names.map { name =>
        val source = sourceRoot / name / "SKILL.md"
        if (!source.isFile) sys.error("Missing component-owned skill source: " + source)
        source -> ("skills/" + name + "/SKILL.md")
      }
      val contracts = Seq(
        "docs/spec/native-host-execution.md",
        "docs/spec/semantic-result-submission.md",
        "docs/spec/phase8-compact-continuation.md",
        "docs/spec/development-journal-semantic-entries.md",
        "docs/spec/runtime-owned-local-publication.md",
        "docs/spec/caller-information.md",
        "docs/spec/task-fix-delivery.md",
        "docs/spec/standalone-fix-runtime.md",
        "docs/spec/standalone-fix-acceptance.md",
        "docs/spec/goal-task-runtime.md",
        "docs/spec/delegate-task.md",
        "docs/spec/native-delegate-task-caller.md",
        "docs/spec/commit-completion-and-recovery.md",
        "docs/spec/standalone-review-fix-runtime.md",
        "docs/spec/goal-phase-acceptance-boundary.md",
        "docs/spec/goal-phase-purpose-repair.md",
        "docs/spec/goal-phase-precommit-step-review.md",
        "docs/spec/goal-phase-release-repair-continuation.md",
        "docs/spec/goal-phase-dependency-scope.md",
        "docs/spec/goal-phase-dependency-source-revision.md",
        "docs/spec/repository-sync-validation-continuation.md",
        "docs/spec/repository-sync-runtime-orchestration.md",
        "docs/spec/repository-sync-directory-list.md",
        "docs/spec/workflow-response-artifact-references.md",
        "docs/spec/goal-phase-validation-metadata.md",
        "docs/spec/native-goal-command-task.md",
        "docs/spec/runtime-caller-format-migration.md",
        "scripts/native-goal-command.py",
        "scripts/native-goal-authority.py",
        "scripts/migrate-runtime-caller-format.py",
        "scripts/goal-phase-dispatch.py",
        "skills/sm-goal-phase/dispatch-mappings.json",
        "skills/sm-goal-phase/cost-role-mapping.json"
      ).map { relativepath =>
        val source = baseDirectory.value / relativepath
        if (!source.isFile) sys.error("Missing component-owned skill contract source: " + source)
        source -> relativepath
      }
      val agents = Seq("analysis", "design", "implementation", "repair", "review", "chat_waiter").map { role =>
        val relativePath = "agents/sm_workflow_" + role + ".toml"
        val source = baseDirectory.value / relativePath
        if (!source.isFile) sys.error("Missing component-owned agent source: " + source)
        source -> relativePath
      }
      val installer = baseDirectory.value / "scripts" / "sm-workflow-tools.py"
      if (!installer.isFile) sys.error("Missing component-owned skill installer: " + installer)
      val metalsBridge = baseDirectory.value / "scripts" / "sm-workflow-metals-bridge.py"
      if (!metalsBridge.isFile) sys.error("Missing bounded Metals companion: " + metalsBridge)
      skills ++ contracts ++ agents ++ Seq(installer -> "install-sm-workflow.py", metalsBridge -> "sm-workflow-metals-bridge.py")
    },
    Compile / resourceGenerators += Def.task {
      val destinationRoot = (Compile / resourceManaged).value / "META-INF" / "sm-workflow"
      val copies = skillSourceFiles.value.map { case (source, relativePath) =>
        source -> (destinationRoot / relativePath)
      }
      // This private namespace contains only the declared source distribution.
      // Remove stale payloads and interpreter caches before rebuilding it.
      IO.delete(destinationRoot)
      IO.copy(copies)
      copies.map(_._2)
    }.taskValue,
    Compile / resourceGenerators += Def.task {
      val source = baseDirectory.value / "scripts" / "sm-workflow-scala-tool-bridge.py"
      val destination = (Compile / resourceManaged).value / "META-INF" / "sm-workflow-runtime" / source.getName
      IO.copyFile(source, destination)
      Seq(destination)
    }.taskValue,
    Compile / packageBin / mappings := {
      val prefix = "META-INF/sm-workflow/"
      val declared = skillSourceFiles.value.map(_._2).toSet
      // Copying managed resources does not remove old files from classDirectory.
      // Only declared distribution entries belong in this private JAR namespace.
      (Compile / packageBin / mappings).value.filter { case (_, path) =>
        !path.startsWith(prefix) || declared(path.stripPrefix(prefix))
      }
    },
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
  .settings(cncfSourceSettings)
// Semantic rules use the normal compiler/SemanticDB prerequisite in each scope.
addCommandAlias("fixCheck", if (org.simplemodeling.scalalint.ScalaLintInput.selected(sys.props.toMap).nonEmpty)
  "sharedScalaLintCheck" else ";scalaStrictAuthoring / Test / compile;Compile / scalafix --check;Test / scalafix --check;scalaStrictAuthoring / Compile / scalafix --check;scalaStrictAuthoring / Test / scalafix --check")
