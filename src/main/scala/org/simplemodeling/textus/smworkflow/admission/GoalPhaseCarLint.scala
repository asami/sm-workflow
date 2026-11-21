/**
 * @since   Nov. 21, 2026
 * @version Nov. 21, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path
import scala.util.control.NonFatal
import io.circe.{Codec, Json}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{ProcessExecutionResult, ProcessExecutionTermination, ValidationCheckExecutionResult,
  ValidationCheckOperation, ValidationCheckPurpose, ValidationCheckTarget}

/** Decode the actual JSON emitted by the host's selected Cozy CAR lint check. */
private[smworkflow] object GoalPhaseCarLint {
  final case class Finding(level: String, category: String, code: String, message: String, path: String,
    line: Int) derives Codec.AsObject {
    def identity: String = s"$category:$code:$path:$line"
  }
  final case class Report(findings: Vector[Finding]) derives Codec.AsObject {
    def blockers: Vector[Finding] = findings.filter(_.level == "FAIL")
  }
  final case class Observation(root: String, process: ProcessExecutionResult, report: Option[Report]) {
    def passed: Boolean = report.exists(_.blockers.isEmpty)
    def repairable: Boolean = report.exists(_.blockers.nonEmpty)
  }

  private[admission] def hostProgramC(plan: Json, target: ValidationCheckTarget, root: Path): Consequence[String] = {
    val workspace = target.workspaceId.value
    val selected = for {
      workspaces <- plan.hcursor.get[Vector[Json]]("workspaces").toOption
      _ <- workspaces.filter(_.hcursor.get[String]("identity").contains(workspace)) match {
        case Vector(row) if row.hcursor.get[String]("root").contains(root.toString) => Some(())
        case _ => None
      }
      checks <- plan.hcursor.get[Vector[Json]]("validationChecks").toOption
      check <- checks.filter(row => row.hcursor.get[String]("target").contains(workspace) &&
        row.hcursor.get[String]("checkIdentity").contains(target.checkIdentity.value)) match {
        case Vector(row) if row.hcursor.get[String]("purpose").contains("lint") &&
            row.hcursor.get[Vector[String]]("fixedArguments").contains(Vector("car", "lint", ".", "--format", "json")) => Some(row)
        case _ => None
      }
      identity <- check.hcursor.get[String]("identity").toOption.filter(GoalPhaseRuntimeRepairControl.exact)
    } yield identity
    selected.map(Consequence.success).getOrElse(
      Consequence.stateConflict("CAR lint requires its fixed JSON program in the selected repository workspace"))
  }

  private[admission] def observationC(root: Path, process: ProcessExecutionResult): Consequence[Observation] = {
    val valid = root != null && root.isAbsolute && root == root.normalize &&
      process != null && process.termination != null && process.artifacts == Vector.empty &&
      process.elapsedMillis >= 0 && GoalPhaseRuntimeRepairControl.exact(process.safeProgramIdentity) &&
      Vector(process.stdout, process.stderr).forall(c =>
        c != null && c.content != null && c.byteCount == c.content.size.toLong)
    if (!valid) Consequence.stateConflict("CAR lint returned an invalid process observation")
    else process.termination match {
      case ProcessExecutionTermination.Exited(code) if code == 0 || code == 1 =>
        val stdout = WorkflowSbtCapture.text(process.stdout)
        val stderr = WorkflowSbtCapture.text(process.stderr)
        if (stdout.textStatus == "complete" && stderr.textStatus == "complete")
          reportC(root, stdout.value, code).map(report => Observation(root.toString, process, Some(report)))
        else Consequence.stateConflict("CAR lint report capture is incomplete")
      case ProcessExecutionTermination.NotStarted if Vector(process.stdout, process.stderr).exists(c =>
          c.byteCount != 0 || c.truncated) =>
        Consequence.stateConflict("Unstarted CAR lint returned contradictory output")
      case _ => Consequence.success(Observation(root.toString, process, None))
    }
  }

  def observedC(target: ValidationCheckTarget, root: Path, result: ValidationCheckExecutionResult): Consequence[Report] = {
    if (target == null || result == null || result.target != target || result.operation != ValidationCheckOperation.Run ||
        result.purpose != ValidationCheckPurpose.Lint || result.processResult == null)
      Consequence.stateConflict("CAR lint result differs from its selected native check")
    else observationC(root, result.processResult).flatMap(_.report.map(Consequence.success)
      .getOrElse(Consequence.stateConflict("CAR lint did not return a process outcome with a report")))
  }

  private[admission] def reportC(root: Path, text: String, exit: Int): Consequence[Report] = {
    val parser = io.circe.jawn.JawnParser(allowDuplicateKeys = false)
    val value = for {
      _ <- Either.cond(root != null && root.isAbsolute && root == root.normalize && text != null &&
        (exit == 0 || exit == 1), (), "CAR lint requires the selected repository and its terminal report")
      json <- parser.parse(text).left.map(_.message)
      fields <- json.asObject.filter(_.keys.toSet == Set("findings")).toRight("CAR lint report fields differ")
      raw <- fields("findings").flatMap(_.asArray).toRight("CAR lint does not contain a findings array")
      findings <- raw.foldLeft[Either[String, Vector[Finding]]](Right(Vector.empty)) { (saved, row) =>
        for {
          previous <- saved
          entry <- _finding(root, row)
        } yield previous :+ entry
      }
      report = Report(findings)
      _ <- Either.cond((exit == 0) == report.blockers.isEmpty, (), "CAR lint findings contradict its exit status")
    } yield report
    value.fold(Consequence.stateConflict(_), Consequence.success)
  }

  private def _finding(root: Path, row: Json): Either[String, Finding] = for {
    _ <- Either.cond(row.asObject.exists(_.keys.toSet == Set("level", "category", "code", "message", "path", "line")),
      (), "CAR lint finding fields differ")
    value <- row.as[Finding].left.map(_.message)
    _ <- Either.cond(Set("OK", "WARN", "FAIL")(value.level) && value.line >= 0 &&
      Vector(value.category, value.code, value.path).forall(GoalPhaseRuntimeRepairControl.exact) &&
      value.message.trim.nonEmpty, (), "Invalid CAR lint finding")
    relative <- _relative(root, value.path)
  } yield value.copy(path = relative)

  private def _relative(root: Path, original: String): Either[String, String] = try {
    val target = root.resolve(original).normalize()
    if (!target.startsWith(root)) Left("CAR lint finding is outside the selected repository")
    else Right(Option(root.relativize(target).toString).filter(_.nonEmpty).getOrElse("."))
  } catch { case NonFatal(_) => Left("Invalid CAR lint finding path") }
}
