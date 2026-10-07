package org.simplemodeling.textus.smworkflow.runtime

import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*

/** Trusted host adapter for a fixed SBT check. CNCF owns the outer process,
  * output limits, timeout and descendant termination. This entry point only
  * gives the launcher its actual repository working directory and forwards
  * its exit status/output. It has no Workflow, store, permission or retry logic.
  * No command, launcher or repository is taken from an AI/public result.
  * @since   Oct.  7, 2026
  * @version Oct.  8, 2026
  */
object SbtValidationProgram {
  private[smworkflow] def definitionC(ordinal: Int, root: Path, java: String,
    launcher: Path, classpath: String, commands: Vector[String],
    limits: ProcessExecutionLimits, environment: Map[String, String] = Map.empty): Consequence[ProcessProgramDefinition] = for {
    _ <- if (ordinal > 0 && ordinal <= 16 && absolute(root) && absolute(launcher) &&
        text(java) && absolute(Path.of(java)) && text(classpath) && validCommands(commands) && environment != null)
      Consequence.unit else Consequence.stateConflict("SBT validation requires a fixed host target, launcher and command vector")
    capability <- ProcessCapabilityId.parseC("sm-workflow-repository-validation-" + ordinal)
    fixed = Vector("-cp", classpath, "org.simplemodeling.textus.smworkflow.runtime.SbtValidationProgram",
      root.toString, java, launcher.toString) ++ commands
    definition <- ProcessProgramDefinition.fromRuntimeC(capability, capability.print, java, fixed,
      ProcessArgumentPolicy(fixed, permittedArgumentVectors = Set(Vector.empty)), limits, Set.empty,
      environment = environment)
  } yield definition

  def main(args: Array[String]): Unit = {
    val code = try {
      require(args.length >= 4 && args.length <= 19, "fixed host arguments required")
      val root = Path.of(args(0)); val java = Path.of(args(1)); val launcher = Path.of(args(2))
      val commands = args.drop(3).toVector
      require(absolute(root) && Files.isDirectory(root) && absolute(java) && Files.isExecutable(java) &&
        absolute(launcher) && Files.isRegularFile(launcher) && validCommands(commands), "fixed SBT launch target unavailable")
      // Do not change user.dir while retaining a different OS working directory:
      // forked product tests and other build subprocesses need the real cwd too.
      val command = Vector(java.toString, "-Dsbt.supershell=false", "-Dsbt.server.autostart=false",
        "-Dsbt.log.noformat=true", "-jar", launcher.toString) ++ commands
      val child = new ProcessBuilder(command*).directory(root.toFile).inheritIO().start()
      try child.waitFor()
      catch { case interrupted: InterruptedException =>
        child.destroy()
        Thread.currentThread().interrupt()
        throw interrupted
      }
    } catch { case scala.util.control.NonFatal(_) =>
      System.err.println("sbt-validation: fixed host launch failed")
      2
    }
    System.exit(code)
  }

  private def absolute(path: Path): Boolean = path != null && path.isAbsolute && path.normalize == path
  private def text(value: String): Boolean = value != null && value.trim.nonEmpty &&
    !value.contains('\u0000') && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 131072
  private def validCommands(values: Vector[String]): Boolean = values != null && values.nonEmpty &&
    values.size <= 16 && values.forall(text) && !values.exists(_.startsWith("-"))
}
