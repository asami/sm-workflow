package org.simplemodeling.textus.smworkflow.admission

import scala.concurrent.duration.*
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.*
import io.circe.Json

/** Normal workflow preparation and execution; no process or SBT runner fallback.
  * @since   Oct. 30, 2026
  * @version Oct. 30, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object EstimatedValidationExecution {
  val workspacesKey = "sm-workflow.validation.estimated-check-workspaces"
  val startupKey = "sm-workflow.validation.startup-duration-millis"
  val sharedKey = "sm-workflow.validation.shared-preparation-duration-millis"
  val cleanupKey = "sm-workflow.validation.cleanup-duration-millis"
  final case class Prepared(estimate: SbtValidationEstimate, compilation: SbtExecutionResult)
  final case class Compilation(ordinal: Int, sourceTree: String, observation: Json)

  def compilationC(value: Compilation, ordinal: Int, sourceTree: String): Consequence[SbtExecutionResult] = for {
    _ <- if (value != null && value.observation != null && value.ordinal == ordinal && value.sourceTree == sourceTree)
      Consequence.unit else invalid("Retained preparation differs from the current candidate and check")
    process <- WorkflowSbtCapture.decode(value.observation).fold(invalid(_), Consequence.success)
    _ <- if (process.termination == ProcessExecutionTermination.Exited(0) &&
        WorkflowSbtCapture.valid(process, WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.TestCompile,
          WorkflowSbtValidation.Outcome.Succeeded, None, None))) Consequence.unit
      else invalid("Retained preparation has no successful terminal TestCompile observation")
    workspace <- WorkspaceId.parseC("sm-workflow-repository-validation-" + ordinal)
  } yield SbtExecutionResult(SbtTarget(workspace), SbtOperation.TestCompile, process)

  def selectedC(core: ActionCall.Core, ordinal: Int, kind: WorkflowSbtValidation.Kind): Consequence[Boolean] = {
    val workspace = "sm-workflow-repository-validation-" + ordinal
    core.executionContext.resolvedParameters.get(workspacesKey) match {
      case None => Consequence.success(false)
      case Some(parameter) => parameter.value match {
        case ConfigurationValue.ListValue(values) if values != null && values.forall {
          case ConfigurationValue.StringValue(value) => value != null && WorkspaceId.parseC(value).isSuccess
          case _ => false
        } =>
          val selected = values.collect { case ConfigurationValue.StringValue(value) => value }
          if (selected.distinct.size != selected.size) invalid("Estimated workspace configuration contains duplicates")
          else if (selected.contains(workspace) && kind != WorkflowSbtValidation.Kind.TestOnly)
            invalid("Estimated leaf execution requires a fixed TestOnly selection")
          else Consequence.success(selected.contains(workspace))
        case _ => invalid("Estimated workspaces require a list of typed logical workspace names")
      }
    }
  }

  /** The preparation receipt is returned before the test body is started, so
    * the owning workflow can retain it without manufacturing a check success.
    */
  def estimateC(core: ActionCall.Core, ordinal: Int, sourceRevision: String, reason: String,
    compilation: Compilation): Consequence[Prepared] = for {
        result <- compilationC(compilation, ordinal, sourceRevision)
        workspace <- WorkspaceId.parseC("sm-workflow-repository-validation-" + ordinal)
        classes <- SbtValidationEstimate.selectedTestClassesC(core.executionContext, SbtTarget(workspace))
        path <- WorkspaceFilePath.parseC("target/sm-workflow/validation-inventory.json")
        captured <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), path)
        raw <- captured.text.map(Consequence.success).getOrElse(invalid("TestCompile produced no canonical leaf inventory"))
        operations <- CompiledValidationInventory.selectedC(raw, sourceRevision, classes, None)
        settings <- ValidationEstimateDeadline.settingsC(core.executionContext.resolvedParameters)
        startup <- durationC(core, startupKey, 30000L)
        shared <- durationC(core, sharedKey, 0L)
        cleanup <- durationC(core, cleanupKey, 5000L)
        // Count observed compilation preparation once in the aggregate, then
        // subtract the already consumed duration from the body's remaining
        // allowance. No successful preparation is executed again to measure it.
        compilationDuration = result.processResult.elapsedMillis.millis
        plan <- ValidationEstimateDeadline.planC(operations, reason,
          ValidationEstimateDeadline.Preparation(startup, compilationDuration, shared, cleanup), settings)
        aggregate <- ValidationEstimateDeadline.executionEstimateC(plan)
        estimate = aggregate.copy(completedPreparationNanos = compilationDuration.toNanos)
        _ <- estimate.timeoutMillisC
  } yield Prepared(estimate, result)

  private def durationC(core: ActionCall.Core, key: String, default: Long): Consequence[FiniteDuration] =
    core.executionContext.resolvedParameters.get(key) match {
      case None => Consequence.success(default.millis)
      case Some(parameter) => parameter.value match {
        case ConfigurationValue.NumberValue(value) if value != null && value.isWhole && value.isValidLong &&
            value >= 0 && BigInt(value.toLong) * 1000000 <= BigInt(Long.MaxValue) => Consequence.success(value.toLong.millis)
        case _ => invalid(key + " requires nonnegative integral milliseconds")
      }
    }
  private def invalid[A](message: String): Consequence[A] = Consequence.configurationInvalid(message)
}
