package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.{Files, Path}
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Public host JSON and selected product-test input only. Ordinary CNCF
  * bootstrap binds the plan; this helper never executes a tool or a driver.
  * @since   Oct. 15, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object TypedSbtHostPlanFixture {
  def normalExecutable: Path = {
    val path = Path.of(sys.props.getOrElse("smworkflow.spec.sbt",
      throw new IllegalArgumentException("Missing normal smworkflow.spec.sbt executable")))
    require(path.isAbsolute, "The normal SBT executable must be absolute")
    val actual = path.toRealPath()
    require(Files.isRegularFile(actual) && Files.isExecutable(actual),
      "The normal SBT executable must be a regular executable file")
    actual
  }

  def plan(root: Path, executable: Path, ordinals: Vector[Int], gitPlan: Option[Json] = None,
    executionTimeoutMillis: Long = 180000L): Json = {
    require(ordinals.nonEmpty && ordinals.forall(value => value >= 1 && value <= 16) &&
      ordinals.distinct == ordinals, "Only distinct selected SBT ordinals 1..16 are admitted")
    require(executionTimeoutMillis > 0L, "The finite SBT execution timeout must be positive")
    val owner = Json.fromString(SmWorkflowComponent.componentId.name)
    val targets = ordinals.map(ordinal => "sm-workflow-repository-validation-" + ordinal)
    val jdkhome = System.getProperty("java.home")
    val environment = Map("JAVA_HOME" -> jdkhome, "HOME" -> System.getProperty("user.home"),
      "PATH" -> (Path.of(jdkhome, "bin").toString + java.io.File.pathSeparator + sys.env.getOrElse("PATH", "/usr/bin:/bin")))
    val limits = Json.obj(Vector("launchTimeoutMillis" -> 10000L, "executionTimeoutMillis" -> executionTimeoutMillis,
      "terminationGraceMillis" -> 1000L, "stdinBytes" -> 2097152L, "stdoutBytes" -> 131072L,
      "stderrBytes" -> 16384L, "argumentCount" -> 16L, "argumentBytes" -> 131072L,
      "artifactCount" -> 1L, "artifactBytes" -> 131072L, "workAreaBytes" -> 4194304L)
      .map { case (name, value) => name -> Json.fromLong(value) }*)
    val sbtplan = Json.obj("schema" -> Json.fromString("cncf.runtime-process-execution.v2"), "programs" -> Json.arr(),
      "grants" -> Json.arr(Json.obj("componentId" -> owner,
        "capabilities" -> Json.fromValues(targets.map(Json.fromString)))),
      "workspaces" -> Json.fromValues(targets.map(target => Json.obj("identity" -> Json.fromString(target),
        "root" -> Json.fromString(root.resolve("repository").toString)))),
      "workspaceGrants" -> Json.fromValues(targets.map(target => Json.obj(
        "componentId" -> owner, "workspaceId" -> Json.fromString(target), "access" -> Json.fromString("manage")))),
      "sbtPrograms" -> Json.fromValues(targets.map(target => Json.obj("target" -> Json.fromString(target),
        "operation" -> Json.fromString("test"), "capability" -> Json.fromString(target), "identity" -> Json.fromString(target),
        "sbtExecutable" -> Json.fromString(executable.toString), "limits" -> limits,
        "environment" -> Json.obj(environment.toVector.map { case (name, value) => name -> Json.fromString(value) }*)))))
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

  /** ProductCheck is deliberate test-owned product input, never an executor. */
  def build(root: Path, successful: Boolean): Unit = {
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
      |    assert(java.nio.file.Files.readString(root.resolve("value.txt")) == "expected\n", "actual product input differs")
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
