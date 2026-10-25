package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.file.Paths
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.simplemodeling.textus.smworkflow.caller.NativeCallerInformationSource
import org.simplemodeling.textus.smworkflow.value as V

/** Original public caller item qualification. Historical causal association and
  * unresolved effects require an actual independent Human adoption.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseValidationPriorInvocation {
  final case class CallerSourcePolicy(schemaVersion: String, threadId: String,
    installedExecutable: String, runtimeRoot: String, taskRoot: String)
  object CallerSourcePolicy {
    given Encoder[CallerSourcePolicy] = deriveEncoder
    private val _raw: Decoder[CallerSourcePolicy] = deriveDecoder
    given Decoder[CallerSourcePolicy] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (policyValid(value) && value.asJson == cursor.value) Right(value)
      else Left(DecodingFailure("Caller source policy requires its exact valid closed configuration", cursor.history))))
  }
  final case class PriorInvocationSelector(schemaVersion: String, sourceLocator: V.WorkflowCodexSourceLocator,
    sourceResource: String, outputName: String)
  object PriorInvocationSelector {
    given Encoder[PriorInvocationSelector] = deriveEncoder
    private val _raw: Decoder[PriorInvocationSelector] = deriveDecoder
    given Decoder[PriorInvocationSelector] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (selectorValid(value) && value.asJson == cursor.value) Right(value)
      else Left(DecodingFailure("Prior invocation selector requires its exact closed fields", cursor.history))))
  }
  final case class PriorInvocationSnapshot(schemaVersion: String, sourceLocator: V.WorkflowCodexSourceLocator,
    sourceReference: ContextReference, sourceResource: String, sourceObservedAt: String,
    selectedItemJson: String, policy: CallerSourcePolicy, outputName: String, proofScope: String)
  object PriorInvocationSnapshot {
    given Encoder[PriorInvocationSnapshot] = deriveEncoder
    private val _raw: Decoder[PriorInvocationSnapshot] = deriveDecoder
    given Decoder[PriorInvocationSnapshot] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (snapshotValid(value) && value.asJson == cursor.value) Right(value)
      else Left(DecodingFailure("Prior invocation snapshot requires its exact original item, locator, clock and limitations", cursor.history))))
  }

  def policyValid(value: CallerSourcePolicy): Boolean = value != null &&
    value.schemaVersion == "sm-workflow.validation-repair-caller-source-policy.v1" &&
    NativeCallerInformationSource.validLocator(V.WorkflowCodexSourceLocator(value.threadId, "turn", "item")) &&
    Vector(value.installedExecutable, value.runtimeRoot, value.taskRoot).forall(_path) &&
    scala.util.Try {
      val tasks = Paths.get(value.runtimeRoot).resolve("target/sm-workflow/tasks")
      val task = Paths.get(value.taskRoot)
      task != tasks && task.startsWith(tasks)
    }.getOrElse(false)

  def selectorValid(value: PriorInvocationSelector): Boolean = value != null &&
    value.schemaVersion == "sm-workflow.validation-prior-invocation-selector.v1" &&
    NativeCallerInformationSource.validLocator(value.sourceLocator) && _output(value.outputName) &&
    NativeWorkflowResourceInput.referenceC(value.sourceResource).isSuccess

  def snapshotValid(value: PriorInvocationSnapshot): Boolean = value != null &&
    value.schemaVersion == "sm-workflow.validation-prior-invocation-snapshot.v1" &&
    value.proofScope == "caller-invocation-terminated-only" && policyValid(value.policy) &&
    selectorValid(PriorInvocationSelector("sm-workflow.validation-prior-invocation-selector.v1",
      value.sourceLocator, value.sourceResource, value.outputName)) && value.sourceLocator.threadId == value.policy.threadId &&
    value.sourceReference == _reference(value.sourceLocator) && scala.util.Try(Instant.parse(value.sourceObservedAt)).isSuccess &&
    io.circe.parser.parse(value.selectedItemJson).toOption.exists(item => item.noSpaces == value.selectedItemJson && _item_shape(item))

  def command(policy: CallerSourcePolicy, sessionId: String, outputName: String): String =
    "/bin/zsh -lc \"exec '" + policy.installedExecutable + "' --runtime-root '" + policy.runtimeRoot +
      "' command org.simplemodeling.textus.SmWorkflow.GoalPhaseService.advanceGoalPhase --sessionId " + sessionId +
      " --json > '" + policy.taskRoot + "/" + outputName + "/response.json' 2> '" +
      policy.taskRoot + "/" + outputName + "/stderr.log'\""

  def observeC(selector: PriorInvocationSelector, policy: CallerSourcePolicy, sessionId: String,
    now: Instant, readText: String => Consequence[String]): Consequence[PriorInvocationSnapshot] = for {
    item <- _read_c(selector, policy, sessionId, readText)
    _ <- if (now != null) Consequence.unit else _invalid("requires the actual observation clock")
  } yield PriorInvocationSnapshot("sm-workflow.validation-prior-invocation-snapshot.v1", selector.sourceLocator,
    _reference(selector.sourceLocator), selector.sourceResource, now.toString, item.noSpaces, policy,
    selector.outputName, "caller-invocation-terminated-only")

  def revalidateC(snapshot: PriorInvocationSnapshot, policy: CallerSourcePolicy, sessionId: String,
    readText: String => Consequence[String]): Consequence[Unit] = for {
    _ <- if (snapshotValid(snapshot) && snapshot.policy == policy) Consequence.unit
      else _invalid("original source snapshot or resolved comparison policy changed")
    item <- _read_c(PriorInvocationSelector("sm-workflow.validation-prior-invocation-selector.v1",
      snapshot.sourceLocator, snapshot.sourceResource, snapshot.outputName), policy, sessionId, readText)
    _ <- if (io.circe.parser.parse(snapshot.selectedItemJson).toOption.contains(item)) Consequence.unit
      else _invalid("original public source item changed")
  } yield ()

  private def _read_c(selector: PriorInvocationSelector, policy: CallerSourcePolicy, sessionid: String,
    readtext: String => Consequence[String]): Consequence[Json] = for {
    _ <- if (selectorValid(selector) && policyValid(policy) && selector.sourceLocator.threadId == policy.threadId &&
        sessionid != null && sessionid.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,255}") && readtext != null) Consequence.unit
      else _invalid("requires the configured original caller and supported exact invocation rendering")
    raw <- readtext(selector.sourceResource)
    item <- NativeCallerInformationSource.selectC(selector.sourceLocator, "commandExecution", raw)
    _ <- if (_item_shape(item) && item.hcursor.get[String]("command").toOption.contains(command(policy, sessionid, selector.outputName)) &&
        item.hcursor.get[String]("cwd").toOption.contains(policy.runtimeRoot)) Consequence.unit
      else _invalid("original caller item is partial, nonterminal or crosses the exact configured invocation")
  } yield item

  private def _item_shape(value: Json): Boolean = value != null && value.asObject.exists(fields =>
    fields.keys.toSet == Set("type", "id", "command", "cwd", "status", "exitCode", "durationMs") &&
      fields.values.forall(!_.isNull)) && value.hcursor.get[String]("type").toOption.contains("commandExecution") &&
    Vector("id", "command", "cwd").forall(key => value.hcursor.get[String](key).toOption.exists(s => s.nonEmpty && !s.contains('\u0000'))) &&
    value.hcursor.downField("durationMs").focus.flatMap(_.asNumber).flatMap(_.toLong).exists(_ >= 0) && {
      val exit = value.hcursor.downField("exitCode").focus.flatMap(_.asNumber).flatMap(_.toInt)
      value.hcursor.get[String]("status").toOption.exists {
        case "completed" => exit.contains(0)
        case "failed" => exit.exists(_ != 0)
        case _ => false
      }
    }
  private def _reference(value: V.WorkflowCodexSourceLocator): ContextReference =
    ContextReference("codex:" + value.threadId + ":" + value.turnId + ":" + value.itemId, "1")
  private def _output(value: String): Boolean = value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}") &&
    value != "." && value != ".."
  private def _path(value: String): Boolean = value != null && value.nonEmpty &&
    !value.exists(c => c.isControl || "'\"\\$`".contains(c)) && scala.util.Try {
      val path = Paths.get(value)
      path.isAbsolute && path.normalize == path
    }.getOrElse(false)
  private def _invalid[A](message: String): Consequence[A] = Consequence.stateConflict("Validation prior invocation " + message)
}
