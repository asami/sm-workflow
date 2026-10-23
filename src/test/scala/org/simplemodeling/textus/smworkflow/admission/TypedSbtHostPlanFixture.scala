package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import org.goldenport.cncf.processexecution.SbtOperation
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.metafixture.SelectedMetadataFixtureSpec

/** Public host JSON and selected product-test input only. Ordinary CNCF
  * bootstrap binds the plan; this helper never executes a tool or a driver.
  * @since   Oct. 15, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object TypedSbtHostPlanFixture {
  def normalExecutable: Path = {
    val path = Path.of(sys.props.get("smworkflow.spec.sbt")
      .orElse(sys.env.get("SM_WORKFLOW_SPEC_SBT"))
      .getOrElse(throw new IllegalArgumentException("Missing normal smworkflow.spec.sbt or SM_WORKFLOW_SPEC_SBT executable")))
    require(path.isAbsolute, "The normal SBT executable must be absolute")
    val actual = path.toRealPath()
    require(Files.isRegularFile(actual) && Files.isExecutable(actual),
      "The normal SBT executable must be a regular executable file")
    actual
  }

  def plan(root: Path, executable: Path, ordinals: Vector[Int], gitPlan: Option[Json] = None,
    executionTimeoutMillis: Long = 180000L, operation: SbtOperation = SbtOperation.Test,
    testClasses: Vector[String] = Vector.empty, stdoutBytes: Int = 131072, stderrBytes: Int = 16384): Json = {
    require(stdoutBytes >= 1 && stdoutBytes <= 2097152 && stderrBytes >= 1 && stderrBytes <= 131072,
      "Select finite native stdout and stderr capture limits")
    require(testClasses != null && (operation match {
      case SbtOperation.TestOnly => testClasses.nonEmpty && testClasses.size <= 16 &&
        testClasses.forall(x => x != null && x.length <= 256 &&
          x.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")) &&
        testClasses.distinct == testClasses &&
        testClasses.map(_.split('.').last).distinct.size == testClasses.size
      case SbtOperation.Compile | SbtOperation.Test => testClasses.isEmpty
    }), "Only bounded exact TestOnly classes or class-free Compile/Test are admitted")
    val operationwire = operation match {
      case SbtOperation.Compile => "compile"
      case SbtOperation.Test => "test"
      case SbtOperation.TestOnly => "test-only"
    }
    require(ordinals.nonEmpty && ordinals.forall(value => value >= 1 && value <= 16) &&
      ordinals.distinct == ordinals, "Only distinct selected SBT ordinals 1..16 are admitted")
    require(executionTimeoutMillis > 0L, "The finite SBT execution timeout must be positive")
    val owner = Json.fromString(SmWorkflowComponent.componentId.name)
    val targets = ordinals.map(ordinal => "sm-workflow-repository-validation-" + ordinal)
    val jdkhome = System.getProperty("java.home")
    val environment = Map("JAVA_HOME" -> jdkhome, "HOME" -> System.getProperty("user.home"),
      "PATH" -> (Path.of(jdkhome, "bin").toString + java.io.File.pathSeparator + sys.env.getOrElse("PATH", "/usr/bin:/bin")))
    val limits = Json.obj(Vector("launchTimeoutMillis" -> 10000L, "executionTimeoutMillis" -> executionTimeoutMillis,
      "terminationGraceMillis" -> 1000L, "stdinBytes" -> 2097152L, "stdoutBytes" -> stdoutBytes.toLong,
      "stderrBytes" -> stderrBytes.toLong, "argumentCount" -> 16L, "argumentBytes" -> 131072L,
      "artifactCount" -> 1L, "artifactBytes" -> 131072L, "workAreaBytes" -> 4194304L)
      .map { case (name, value) => name -> Json.fromLong(value) }*)
    val sbtplan = Json.obj("schema" -> Json.fromString("cncf.runtime-process-execution.v2"), "programs" -> Json.arr(),
      "grants" -> Json.arr(Json.obj("componentId" -> owner,
        "capabilities" -> Json.fromValues(targets.map(Json.fromString)))),
      "workspaces" -> Json.fromValues(targets.map(target => Json.obj("identity" -> Json.fromString(target),
        "root" -> Json.fromString(root.resolve("repository").toString)))),
      "workspaceGrants" -> Json.fromValues(targets.map(target => Json.obj(
        "componentId" -> owner, "workspaceId" -> Json.fromString(target), "access" -> Json.fromString("manage")))),
      "sbtPrograms" -> Json.fromValues(targets.map { target =>
        val program = Json.obj("target" -> Json.fromString(target),
        "operation" -> Json.fromString(operationwire), "capability" -> Json.fromString(target), "identity" -> Json.fromString(target),
        "sbtExecutable" -> Json.fromString(executable.toString), "limits" -> limits,
        "environment" -> Json.obj(environment.toVector.map { case (name, value) => name -> Json.fromString(value) }*))
        if (operation == SbtOperation.TestOnly) program.mapObject(_.add("testClasses",
          Json.fromValues(testClasses.map(Json.fromString)))) else program
      }))
    gitPlan.fold(sbtplan) { git =>
      def _array_(plan: Json, name: String): Vector[Json] = plan.hcursor.downField(name).focus.get.asArray.get.toVector
      require(_array_(git, "programs").isEmpty, "Combined typed Git/SBT plans admit no generic programs")
      val capabilities = (_array_(git, "gitPrograms") ++ _array_(sbtplan, "sbtPrograms"))
        .map(_.hcursor.downField("capability").focus.get)
      sbtplan.mapObject(_.add("schema", Json.fromString("cncf.runtime-process-execution.v3"))
        .add("gitPrograms", git.hcursor.downField("gitPrograms").focus.get)
        .add("workspaces", Json.fromValues(_array_(git, "workspaces") ++ _array_(sbtplan, "workspaces")))
        .add("workspaceGrants", Json.fromValues(_array_(git, "workspaceGrants") ++ _array_(sbtplan, "workspaceGrants")))
        .add("grants", Json.arr(Json.obj("componentId" -> owner, "capabilities" -> Json.fromValues(capabilities)))))
    }
  }

  /** Enroll the actual compiled fixture in an ordinary ScalaTest sbt build.
    * Setup writes inputs only; the owning native UoW later executes testOnly.
    */
  def buildMetadata(root: Path, fixture: Class[?] = classOf[SelectedMetadataFixtureSpec]): Unit = {
    val primary = Path.of("").toAbsolutePath.normalize
    val classpathfile = primary.resolve("target/cncf.d/runtime-classpath.txt")
    require(Files.isRegularFile(classpathfile), "Prepare the official primary runtime classpath first")
    val fixtureroot = Path.of(fixture.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
    val entries = Files.readAllLines(classpathfile).asScala.toVector.map { value =>
      require(value.nonEmpty && !value.exists(_.isControl), "Runtime classpath contains an invalid entry")
      val path = Path.of(value)
      require(path.isAbsolute && path.normalize == path && Files.exists(path),
        "Runtime classpath entries must exist and be absolute normalized paths")
      path
    }
    val roots = (fixtureroot +: entries).distinct
    require(roots.forall(path => path.isAbsolute && path.normalize == path && Files.exists(path)),
      "Actual fixture and runtime roots must exist")
    def literal(value: String): String = Json.fromString(value).noSpaces
    val repository = Files.createDirectories(root.resolve("repository"))
    Files.createDirectories(repository.resolve("project"))
    Files.writeString(repository.resolve("project/build.properties"), "sbt.version=1.9.7\n")
    Files.writeString(repository.resolve(".gitignore"), "target/\nproject/target/\nproject/project/\n.bsp/\n")
    val build = """scalaVersion := "3.3.8"
      |Test / unmanagedClasspath ++= Seq(__ROOTS__).map(path => Attributed.blank(file(path)))
      |Test / testFrameworks := Seq(new TestFramework("org.scalatest.tools.Framework"))
      |Test / definedTests := Seq(new TestDefinition(__SUITE__, new sbt.testing.SubclassFingerprint {
      |  def superclassName(): String = "org.scalatest.Suite"
      |  def isModule(): Boolean = false
      |  def requireNoArgConstructor(): Boolean = true
      |}, true, Array(new sbt.testing.SuiteSelector)))
      |Global / onLoad := {
      |  val original = (Global / onLoad).value
      |  state => {
      |    val loaded = original(state)
      |    IO.append(file(__PIDS__), java.lang.ProcessHandle.current().pid().toString + "\n")
      |    IO.append(file(__COMMANDS__), state.remainingCommands.map(_.commandLine).mkString("", "\n", "\n"))
      |    loaded
      |  }
      |}
      |""".stripMargin.replace("__ROOTS__", roots.map(p => literal(p.toString)).mkString(", "))
      .replace("__SUITE__", literal(fixture.getName))
      .replace("__PIDS__", literal(root.resolve("sbt-executions.txt").toString))
      .replace("__COMMANDS__", literal(root.resolve("sbt-command-lines.txt").toString))
    Files.writeString(repository.resolve("build.sbt"), build)
  }

  /** Two finite typed SBT targets share the existing typed Git boundary. */
  def authoringPlan(root: Path, executable: Path, git: Json, compileFirst: Boolean = false): Json = {
    val ordinary = plan(root, executable, Vector(1), Some(git),
      operation = if (compileFirst) SbtOperation.Compile else SbtOperation.Test)
    val selected = plan(root, executable, Vector(2), operation = SbtOperation.TestOnly,
      testClasses = Vector("org.simplemodeling.textus.smworkflow.p62authored.LineWriterSpec"))
    def array(value: Json, name: String): Vector[Json] = value.hcursor.get[Vector[Json]](name).toOption.get
    val programs = array(ordinary, "sbtPrograms") ++ array(selected, "sbtPrograms")
    val capabilities = (array(ordinary, "gitPrograms") ++ programs).map(_.hcursor.downField("capability").focus.get)
    ordinary.mapObject(_.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues(array(ordinary, "workspaces") ++ array(selected, "workspaces")))
      .add("workspaceGrants", Json.fromValues(array(ordinary, "workspaceGrants") ++ array(selected, "workspaceGrants")))
      .add("grants", Json.arr(Json.obj("componentId" -> Json.fromString(SmWorkflowComponent.componentId.name),
        "capabilities" -> Json.fromValues(capabilities)))))
  }

  /** Inputs only. The external semantic worker must author the absent test source. */
  def buildAuthoring(root: Path, cml: Boolean = false): Unit = {
    val primary = Path.of("").toAbsolutePath.normalize
    val classpathfile = primary.resolve("target/cncf.d/runtime-classpath.txt")
    require(Files.isRegularFile(classpathfile), "Prepare the official primary runtime classpath first")
    val fixtureroot = Path.of(classOf[SelectedMetadataFixtureSpec].getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
    val roots = Files.readAllLines(classpathfile).asScala.toVector.map { value =>
      require(value.nonEmpty && !value.exists(_.isControl), "Runtime classpath contains an invalid entry")
      val path = Path.of(value)
      require(path.isAbsolute && path.normalize == path && Files.exists(path),
        "Runtime classpath entries must exist and be absolute normalized paths")
      path
    }.filterNot(_.toRealPath() == fixtureroot).distinct
    require(roots.nonEmpty, "Authoring requires the actual primary runtime entries")
    def literal(value: String): String = Json.fromString(value).noSpaces
    val repository = Files.createDirectories(root.resolve("repository"))
    val library = repository.resolve("src/main/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriter.scala")
    require(!Files.exists(repository.resolve("src/test/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriterSpec.scala")),
      "The semantic test source must remain absent before external authoring")
    Files.createDirectories(library.getParent)
    Files.createDirectories(repository.resolve("project"))
    Files.createDirectories(repository.resolve("docs"))
    Files.writeString(repository.resolve("project/build.properties"), "sbt.version=1.9.7\n")
    Files.writeString(repository.resolve(".gitignore"), "target/\nproject/target/\nproject/project/\n.bsp/\n")
    Files.writeString(library, "package org.simplemodeling.textus.smworkflow.p62authored\n" +
      "object LineWriter { def render(value: String): String = value + \"\\n\" }\n")
    val build = """scalaVersion := "3.3.8"
      |Compile / unmanagedClasspath ++= Seq(__ROOTS__).map(path => Attributed.blank(file(path)))
      |Test / unmanagedClasspath ++= Seq(__ROOTS__).map(path => Attributed.blank(file(path)))
      |Test / testFrameworks := Seq(new TestFramework("org.scalatest.tools.Framework"))
      |Test / definedTests := Seq(new TestDefinition("org.simplemodeling.textus.smworkflow.p62authored.LineWriterSpec", new sbt.testing.SubclassFingerprint {
      |  def superclassName(): String = "org.scalatest.Suite"
      |  def isModule(): Boolean = false
      |  def requireNoArgConstructor(): Boolean = true
      |}, true, Array(new sbt.testing.SuiteSelector)))
      |Global / onLoad := {
      |  val original = (Global / onLoad).value
      |  state => {
      |    val loaded = original(state)
      |    IO.append(file(__PIDS__), java.lang.ProcessHandle.current().pid().toString + "\n")
      |    IO.append(file(__COMMANDS__), state.remainingCommands.map(_.commandLine).mkString("", "\n", "\n"))
      |    loaded
      |  }
      |}
      |""".stripMargin.replace("__ROOTS__", roots.map(p => literal(p.toString)).mkString(", "))
      .replace("__PIDS__", literal(root.resolve("sbt-executions.txt").toString))
      .replace("__COMMANDS__", literal(root.resolve("sbt-command-lines.txt").toString))
    val generated = if (cml) "Test / unmanagedSourceDirectories += baseDirectory.value / \"target\" / \"scala-3.3.8\" / \"src_managed\" / \"test\" / \"scala\"\n" else ""
    Files.writeString(repository.resolve("build.sbt"), build + generated)
    val requirements = """# LineWriter validation requirements
      |
      |Author three real AnyWordSpec behavior tests with GivenWhenThen and should matchers in
      |src/test/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriterSpec.scala.
      |LineWriter.render must map "expected" to "expected\n", "日本語" to "日本語\n",
      |and "" to "\n". Use the exact registrations:
      |- LineWriter should terminate an ASCII value with one LF
      |- LineWriter should retain Unicode text before one LF
      |- LineWriter should represent empty text with one LF
      |
      |Author Java-runtime CNCF validation annotations and typed ValidationDeclarationProvider
      |classes in that same package/file: specification line-writer@1, operations ascii,
      |unicode and empty. Declare ascii as SMOKE with feature(component:line-writer,ascii),
      |unicode as FOCUSED with feature(component:line-writer,unicode), expectedDuration 500ms,
      |durationClass NORMAL and executionRequirement capability line-writer-tests, risk local,
      |reasoning STANDARD, reviewRequired false; empty as SMOKE with
      |feature(component:line-writer,empty). Retain both specification and operation origins.
      |Append a bounded validation design rationale here. Do not author official test commands.
      |Step 2 records the observed exact metadata selection for the authored artifact here.
      |""".stripMargin
    val cmlrequirements = """# LineWriter CML validation requirements
      |
      |Author three real AnyWordSpec behavior tests with GivenWhenThen and should matchers in
      |src/test/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriterSpec.scala.
      |LineWriter.render must map "expected" to "expected\n", "日本語" to "日本語\n",
      |and "" to "\n". Use the exact registrations:
      |- LineWriter should terminate an ASCII value with one LF
      |- LineWriter should retain Unicode text before one LF
      |- LineWriter should represent empty text with one LF
      |
      |Author docs/validation.cml for ordinary Cozy modeler-scala-value generation of four
      |public no-argument ValidationDeclarationProvider classes in
      |org.simplemodeling.textus.smworkflow.p62authored: SpecificationDeclaration,
      |AsciiDeclaration, UnicodeDeclaration and EmptyDeclaration. The specification is
      |line-writer@1 with declaration {} so both specification and operation origins remain.
      |The operations are ascii, unicode and empty. Declare ascii as SMOKE with
      |feature(component:line-writer,ascii), unicode as FOCUSED with
      |feature(component:line-writer,unicode), expectedDuration 500ms, durationClass NORMAL,
      |executionRequirement capability line-writer-tests, risk local, reasoning STANDARD,
      |reviewRequired false; empty as SMOKE with feature(component:line-writer,empty).
      |Author the matching Java-runtime CNCF annotation anchors in the test source, referring
      |to these generated providers. The ordinary generated Test sources and AI-authored
      |tests/anchors must compile together in this same test artifact. Append the bounded
      |validation design rationale here. Do not hand-author generated providers or official
      |test commands. The fixture supplies requirements only, never CML, tests or anchors.
      |""".stripMargin
    Files.writeString(repository.resolve("docs/validation.md"), if (cml) cmlrequirements else requirements)
  }

  /** ProductCheck is deliberate test-owned product input, never an executor. */
  def build(root: Path, successful: Boolean, diagnosticBytes: Int = 0): Unit = {
    require(diagnosticBytes >= 0 && diagnosticBytes <= 1500000,
      "Select finite diagnostic padding from 0 to 1500000 bytes")
    val diagnostic = if (diagnosticBytes == 0) ""
      else if (diagnosticBytes <= 90000) "    print(\"D\" * " + diagnosticBytes + "); System.out.flush()\n"
      else {
        require(diagnosticBytes == 1500000, "Large diagnostics select exactly 1500000 UTF-8 bytes")
        "    print(" + Json.fromString("D\"\\あ").noSpaces + " * 250000); System.out.flush()\n"
      }
    val repository = Files.createDirectories(root.resolve("repository"))
    Files.createDirectories(repository.resolve("project"))
    Files.createDirectories(repository.resolve("src/test/scala"))
    Files.writeString(repository.resolve("project/build.properties"), "sbt.version=1.9.7\n")
    Files.writeString(repository.resolve(".gitignore"), "target/\nproject/target/\nproject/project/\n.bsp/\n")
    Files.writeString(repository.resolve("value.txt"), if (successful) "expected\n" else "incorrect\n")
    Files.writeString(repository.resolve("build.sbt"), """scalaVersion := "2.12.18"
      |Test / test := (Test / runMain).toTask(" ProductCheck").value
      |""".stripMargin)
    Files.writeString(repository.resolve("src/test/scala/ProductCheck.scala"), """object ProductCheck {
      |  def main(args: Array[String]): Unit = {
      |    val root = java.nio.file.Paths.get("").toAbsolutePath.normalize
      |    val marker = root.getParent.resolve("sbt-executions.txt")
      |    java.nio.file.Files.write(marker, (java.lang.ProcessHandle.current().pid().toString + "\n").getBytes("UTF-8"),
      |      java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
      |    println("PRODUCT_CHECK_ENTERED " + root)
      |__DIAGNOSTIC__    assert(java.nio.file.Files.readString(root.resolve("value.txt")) == "expected\n", "actual product input differs")
      |    println("PRODUCT_CHECK_PASSED")
      |  }
      |}
      |""".stripMargin.replace("__DIAGNOSTIC__", diagnostic))
  }

  /** Normal Compile consumes an ordinary managed source generator, with no main. */
  def buildCompile(root: Path, successful: Boolean): Unit = {
    val repository = Files.createDirectories(root.resolve("repository"))
    Files.createDirectories(repository.resolve("project"))
    Files.createDirectories(repository.resolve("src/main/scala"))
    Files.writeString(repository.resolve("project/build.properties"), "sbt.version=1.9.7\n")
    Files.writeString(repository.resolve("build.sbt"), """scalaVersion := "2.12.18"
      |Compile / sourceManaged := baseDirectory.value / "target" / "selected-generated"
      |Compile / sourceGenerators += Def.task {
      |  val root = baseDirectory.value
      |  val generated = (Compile / sourceManaged).value / "CompileGenerated.scala"
      |  IO.write(generated, "object CompileGenerated { val value: Int = __VALUE__ }\n")
      |  IO.append(root.getParentFile / "compile-generations.txt", java.lang.ProcessHandle.current().pid().toString + "\n")
      |  streams.value.log.info("COMPILE_GENERATOR_ENTERED " + root.getCanonicalPath)
      |  Seq(generated)
      |}.taskValue
      |""".stripMargin.replace("__VALUE__", if (successful) "42" else "\\\"incorrect\\\""))
    Files.writeString(repository.resolve("src/main/scala/CompileConsumer.scala"),
      "object CompileConsumer { val value: Int = CompileGenerated.value }\n")
  }

  /** ProductCheck consumes actual compiled semantic code; history is runtime-owned. */
  def buildSourceHistory(root: Path): Unit = {
    build(root, true)
    val repository = root.resolve("repository")
    Files.createDirectories(repository.resolve("src/main/scala"))
    Files.writeString(repository.resolve("src/main/scala/HistoryExample.scala"),
      "object HistoryExample { val value: Int = 0 }\n")
    Files.writeString(repository.resolve("src/test/scala/ProductCheck.scala"), """object ProductCheck {
      |  def main(args: Array[String]): Unit = {
      |    val root = java.nio.file.Paths.get("").toAbsolutePath.normalize
      |    val marker = root.getParent.resolve("sbt-executions.txt")
      |    java.nio.file.Files.write(marker, (java.lang.ProcessHandle.current().pid().toString + "\n").getBytes("UTF-8"),
      |      java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
      |    println("PRODUCT_CHECK_ENTERED " + root)
      |    assert(HistoryExample.value == 42, "actual compiled source value differs")
      |    println("PRODUCT_CHECK_PASSED")
      |  }
      |}
      |""".stripMargin)
  }

  /** Closed repair examples select behavior only from the actual shared text.
    * All build inputs are prepared before the fixture's initial commit.
    */
  def buildRepair(root: Path, mode: String): Unit = {
    require(Set("connected", "failed", "indeterminate", "intent-only")(mode),
      "Only the selected repair check modes are admitted")
    build(root, true)
    Files.writeString(root.resolve("repository/src/test/scala/ProductCheck.scala"), """object ProductCheck {
      |  def main(args: Array[String]): Unit = {
      |    val root = java.nio.file.Paths.get("").toAbsolutePath.normalize
      |    val marker = root.getParent.resolve("sbt-executions.txt")
      |    java.nio.file.Files.write(marker, (java.lang.ProcessHandle.current().pid().toString + "\n").getBytes("UTF-8"),
      |      java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
      |    println("PRODUCT_CHECK_ENTERED " + root)
      |    assert(java.nio.file.Files.readString(root.resolve("value.txt")) == "expected\n", "actual product input differs")
      |    val shared = java.nio.file.Files.readString(root.resolve("shared.txt"))
      |    assert(shared == "resolved shared line\n" || shared == "repaired shared line\n", "actual shared input differs")
      |    val mode = "__MODE__"
      |    if (shared == "repaired shared line\n" && mode != "connected") {
      |      java.nio.file.Files.writeString(root.getParent.resolve("repaired-check-entered.txt"),
      |        java.lang.ProcessHandle.current().pid().toString,
      |        java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE)
      |      mode match {
      |        case "failed" => assert(false, "actual repaired product input fails")
      |        case "indeterminate" => Thread.sleep(180000L)
      |        case "intent-only" =>
      |          System.out.print("__NUL__")
      |          System.out.flush()
      |      }
      |    } else println("PRODUCT_CHECK_PASSED")
      |  }
      |}
      |""".stripMargin.replace("__MODE__", mode).replace("__NUL__", "\\" + "u0000"))
  }
}
