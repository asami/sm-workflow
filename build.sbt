import org.goldenport.cozy.CozyPlugin.autoImport._
import org.goldenport.cozy.CozyProjectIdentityEvidence
import sbt.Keys.*

lazy val projectIdentityEvidence = settingKey[CozyProjectIdentityEvidence]("Admitted project.yaml component identity evidence")
lazy val skillSourceFiles = taskKey[Seq[(File, String)]]("Exact component-owned semantic skill sources")
lazy val skillBundle = taskKey[File]("Package the semantic skill sources as a standalone ZIP")
lazy val checkSkillBundleReproducibility = taskKey[File]("Verify skill ZIP bytes despite source timestamp and ordering changes")
lazy val runtimeTestOnly = inputKey[Unit]("Prepare official runtime artifacts and run the selected executable specifications")

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

    // Explicit runtimeTestOnly and fixed native testOnly selections prepare the
    // official runtime and current CAR. Ordinary test keeps its declared scope.
    runtimeTestOnly := Def.inputTaskDyn {
      val selection = (sbt.complete.DefaultParsers.Space ~> sbt.complete.DefaultParsers.any.*).parsed.mkString
      cozyPrepareRuntime.value
      (Compile / packageBin).value
      (Test / testOnly).toTask(" " + selection)
    }.evaluated,
    // Lint only handwritten production Scala. Test and managed/generated code
    // retain their compiler/tests; project/build code is outside this profile.
    Compile / scalafix / unmanagedSources := (Compile / unmanagedSources).value,
    Compile / scalafix / managedSources := Nil,
    // Fixed native TestOnly selections run the real CLI against the ordinary
    // freshly resolved runtime and current CAR; no caller-built classpath.
    Test / testOnly := (Test / testOnly).dependsOn(cozyPrepareRuntime, Compile / packageBin).evaluated,
    // Explicit testOnly selection admits these artifact-dependent @DoNotDiscover suites.
    Test / testOnly / definedTests := (Test / definedTests).value.map { definition =>
      if (Set("org.simplemodeling.textus.smworkflow.admission.Phase65ConditionalLintSpec",
        "org.simplemodeling.textus.smworkflow.admission.Phase65GoalPhaseFixCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.Phase65GoalPhaseAssessmentCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeCallerInformationCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.ThreeWorkflowSbtOperationSpec",
        "org.simplemodeling.textus.smworkflow.admission.GoalPhaseNativeCandidateCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalCheckRepairCliSpec",
      "org.simplemodeling.textus.smworkflow.admission.NativeGoalLargeValidationCliSpec",
      "org.simplemodeling.textus.smworkflow.admission.NativeGoalTestOutputPolicyCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalMechanicalReceiptCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalStartupRecoveryCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalRuntimeStabilityCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeInstalledCarAdmissionDiagnosticSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalKnownFailedPhaseValidationCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalFailedPhaseRepairAdoptionCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalPostCompletionSyncCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalReviewRepairAdoptionCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalClosureContinuationAdoptionCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalWholePhaseCandidateRecoveryCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalProjectBuildCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalRepairContinuationLiveSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalMetadataCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalPlanRevisionCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalDependencyScopeCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalDependencyScopeLiveSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalAcceptanceBoundaryCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalPlanRevisionLiveSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalValidationAuthoringLiveSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalCmlValidationAuthoringLiveSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalSliceValidationPlanLiveSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalTypedInputsLiveSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalFindingRecordingLiveSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalSourceHistoryCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalProviderSourceHistoryCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalScopedQualityClosureCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeGoalProviderRepairCliSpec",
        "org.simplemodeling.textus.smworkflow.admission.NativeRepositoryProviderConflictCliSpec")(definition.name))
        new TestDefinition(definition.name, definition.fingerprint, true, definition.selectors)
      else definition
    },
    Test / fork := false,
    Test / parallelExecution := false,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,

    skillSourceFiles := {
      val sourceRoot = baseDirectory.value / "skills"
      val names = Seq("sm-goal-phase", "sm-split-phase", "sm-repository-sync")
      val skills = names.map { name =>
        val source = sourceRoot / name / "SKILL.md"
        if (!source.isFile) sys.error("Missing component-owned skill source: " + source)
        source -> ("skills/" + name + "/SKILL.md")
      }
      val contracts = Seq(
        "docs/spec/native-host-execution.md",
        "docs/spec/caller-information.md",
        "docs/spec/goal-phase-acceptance-boundary.md",
        "docs/spec/goal-phase-purpose-repair.md",
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
