import org.goldenport.cozy.CozyPlugin.autoImport._
import org.goldenport.cozy.CozyProjectIdentityEvidence
import sbt.Keys.*

lazy val projectIdentityEvidence = settingKey[CozyProjectIdentityEvidence]("Admitted project.yaml component identity evidence")
lazy val skillSourceFiles = taskKey[Seq[(File, String)]]("Exact component-owned semantic skill sources")
lazy val skillBundle = taskKey[File]("Package the semantic skill sources as a standalone ZIP")
lazy val checkSkillBundleReproducibility = taskKey[File]("Verify skill ZIP bytes despite source timestamp and ordering changes")
lazy val prepareTestArtifacts = taskKey[Unit]("Prepare the fresh component JAR and reproducible skill distribution for tests")
lazy val runtimeTestOnly = inputKey[Unit]("Select canonical metadata, prepare selected runtime requirements and execute exact tests")
lazy val labelledFullTests = taskKey[Seq[LabelledTests.Selected]]("Canonical default FULL operations")
lazy val labelledTestSelection = inputKey[Seq[TestDefinition]]("Inspect canonical test selection without executing selected bodies")
lazy val prepareFullTests = taskKey[Unit]("Prepare prerequisites required by default FULL metadata")
lazy val labelledTestExecution = inputKey[Unit]("Internal standard SBT execution for exact metadata-selected suite operations")

lazy val root = project
  .in(file("."))
  .enablePlugins(org.goldenport.cozy.CozyPlugin)
  .settings(inConfig(Test)(Defaults.testTaskOptions(labelledTestExecution) ++
    Defaults.defaultTestTasks(labelledTestExecution) ++ Seq(
      labelledTestExecution / testFilter := (Defaults.selectedFilter _),
      labelledTestExecution := Defaults.inputTests(labelledTestExecution).evaluated
    )))
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

    // Lint only handwritten production Scala.
    Compile / scalafix / unmanagedSources := (Compile / unmanagedSources).value,
    Compile / scalafix / managedSources := Nil,
    prepareTestArtifacts := {
      (Compile / packageBin).value
      checkSkillBundleReproducibility.value
      ()
    },
    labelledFullTests := LabelledTests.select((Test / fullClasspath).value.files,
      (Test / classDirectory).value, version.value, "FULL", (Test / definedTests).value),
    Test / test / definedTests := labelledFullTests.value.map(_.definition),
    prepareFullTests := Def.taskDyn {
      if (labelledFullTests.value.exists(_.requiresRuntime)) Def.task { cozyPrepareRuntime.value; prepareTestArtifacts.value; () }
      else Def.task { prepareTestArtifacts.value; () }
    }.value,
    Test / test := (Test / test).dependsOn(prepareFullTests).value,
    labelledTestSelection := {
      val request = LabelledTests.request(sbt.complete.DefaultParsers.spaceDelimited("selection").parsed)
      val candidates = LabelledTests.candidates(request, (Test / definedTests).value)
      val selected = LabelledTests.select((Test / fullClasspath).value.files,
        (Test / classDirectory).value, version.value, request.purpose, candidates, request.exact, request.containing)
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
      val request = LabelledTests.request(sbt.complete.DefaultParsers.spaceDelimited("selection").parsed)
      val loader = (Test / testLoader).value
      try {
        val candidates = LabelledTests.candidates(request, (Test / definedTests).value)
        val selected = LabelledTests.select((Test / fullClasspath).value.files,
          (Test / classDirectory).value, version.value, request.purpose, candidates, request.exact, request.containing)
        streams.value.log.info(s"Labelled execution: purpose=${request.purpose} suites=${selected.size} operations=${selected.map(_.definition.selectors.length).sum} runtime=${selected.exists(_.requiresRuntime)}")
        // One ordinary SBT execution produces its genuine aggregate report.
        // Definitions retain their own metadata-selected TestSelector arrays.
        val execution = Def.valueStrict(LabelledTests.execute(selected, (Test / loadedTestFrameworks).value,
          loader, (Test / labelledTestExecution / testExecution).value, streams.value.log))
        val resultLogger = (Test / labelledTestExecution / testResultLogger).value
        val log = streams.value.log
        val runs = Def.task {
          resultLogger.run(log, execution.value, "Test / labelledTestExecution")
        }
        val prepared = if (selected.exists(_.requiresRuntime)) Def.taskDyn {
          cozyPrepareRuntime.value
          prepareTestArtifacts.value
          runs
        } else Def.taskDyn { prepareTestArtifacts.value; runs }
        prepared.andFinally(LabelledTests.closeLoader(loader))
      } catch {
        case error: Throwable => LabelledTests.closeLoader(loader); throw error
      }
    }.evaluated,
    runtimeTestOnly := (Test / testOnly).evaluated,
    Test / fork := false,
    Test / parallelExecution := false,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,

    skillSourceFiles := {
      val sourceRoot = baseDirectory.value / "skills"
      val names = Seq("sm-goal-phase", "sm-split-phase", "sm-repository-sync", "sm-test-fix", "sm-goal-task", "sm-review-fix")
      val skills = names.map { name =>
        val source = sourceRoot / name / "SKILL.md"
        if (!source.isFile) sys.error("Missing component-owned skill source: " + source)
        source -> ("skills/" + name + "/SKILL.md")
      }
      val contracts = Seq(
        "docs/spec/native-host-execution.md",
        "docs/spec/caller-information.md",
        "docs/spec/task-fix-delivery.md",
        "docs/spec/standalone-fix-runtime.md",
        "docs/spec/standalone-fix-acceptance.md",
        "docs/spec/goal-task-runtime.md",
        "docs/spec/standalone-review-fix-runtime.md",
        "docs/spec/goal-phase-acceptance-boundary.md",
        "docs/spec/goal-phase-purpose-repair.md",
        "docs/spec/goal-phase-release-repair-continuation.md",
        "docs/spec/goal-phase-dependency-scope.md",
        "docs/spec/goal-phase-validation-metadata.md",
        "docs/spec/native-goal-command-task.md",
        "docs/spec/runtime-caller-format-migration.md",
        "scripts/native-goal-command.py",
        "scripts/native-goal-authority.py",
        "scripts/migrate-runtime-caller-format.py"
      ).map { relativepath =>
        val source = baseDirectory.value / relativepath
        if (!source.isFile) sys.error("Missing component-owned skill contract source: " + source)
        source -> relativepath
      }
      skills ++ contracts
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
// Syntactic check only: no SemanticDB or additional compilation prerequisite.
addCommandAlias("fixCheck", "Compile / scalafix --check")
