package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.workspace.*
import org.goldenport.protocol.operation.OperationResponse

/** Logical File intents interpreted only by the owning call's current UoW.
  * CNCF owns workspace authority, complete acquisition and per-file installation.
  * @since   Oct. 15, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowFileExecution {
  def readTextC(core: ActionCall.Core, target: FileTarget,
    path: WorkspaceFilePath): Consequence[FileReadTextResult] =
    _owning_c(core).flatMap(_.readC(target, path))

  def writeTextC(core: ActionCall.Core, target: FileTarget, path: WorkspaceFilePath,
    text: String, mode: FileWriteMode): Consequence[FileWriteTextResult] =
    _owning_c(core).flatMap(_.writeC(target, path, text, mode))

  private def _owning_c(core: ActionCall.Core): Consequence[Execution] =
    if (core == null || core.executionContext == null)
      Consequence.stateConflict("Workflow File requires its actual owning call")
    else Consequence.success(new Execution(core))

  private final class Execution(val core: ActionCall.Core) extends ProcedureActionCall {
    def readC(target: FileTarget, path: WorkspaceFilePath): Consequence[FileReadTextResult] =
      executeProgram(file_read_text(target, path))
    def writeC(target: FileTarget, path: WorkspaceFilePath, text: String,
      mode: FileWriteMode): Consequence[FileWriteTextResult] =
      executeProgram(file_write_text(target, path, text, mode))
    def execute(): Consequence[OperationResponse] =
      Consequence.stateConflict("Workflow File bridge is not a public Operation")
  }
}
