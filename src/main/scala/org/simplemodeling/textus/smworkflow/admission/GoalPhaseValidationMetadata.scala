package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationOperationBindingV1 as B}
import org.goldenport.cncf.workspace.WorkspaceId

/** Bounded metadata selection and exact producer binding, before effect admission.
  * @since   Oct. 18, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseValidationMetadata {
  final case class Invocation(reference: D.OperationReference, parameters: Map[String, Json])
  final case class Request(
    scope: D.DiscoveryScope,
    query: D.SelectionQuery,
    supplemental: Vector[D.OperationReference],
    workspace: WorkspaceId,
    invocations: Vector[Invocation] = Vector.empty,
    executionParameters: Map[String, Json] = Map.empty
  )

  final class Selection private[GoalPhaseValidationMetadata] (
    val inventory: D.Inventory,
    val query: D.SelectionQuery,
    val supplemental: Vector[D.OperationReference],
    val operations: Vector[B.BoundOperation]
  ) {
    def requireRunnableC: Consequence[Selection] =
      if (inventory.completeness == D.Completeness.COMPLETE && operations.nonEmpty && operations.size <= 16)
        Consequence.success(this)
      else {
        val diagnostics = inventory.diagnostics ++ inventory.operations.flatMap(_.diagnostics)
        Consequence.stateConflict(
          s"Validation selection is not runnable: scope=${inventory.scope.identity}, " +
            s"revision=${inventory.scope.revision}, completeness=${inventory.completeness}, " +
            s"selected=${operations.size}, diagnostics=$diagnostics; " +
            "required batch needs COMPLETE inventory and 1..16 selected operations")
      }
  }

  def discoverC(request: Request, loader: ClassLoader): Consequence[Selection] =
    for {
      _ <- _request_c(request)
      inventory <- D.discoverC(request.scope, loader)
      selected <- selectC(request, inventory)
    } yield selected

  def selectC(request: Request, inventory: D.Inventory): Consequence[Selection] =
    for {
      _ <- _request_c(request)
      selected <- D.selectC(inventory, request.query)
      requestedScope = request.scope.copy(artifacts = request.scope.artifacts.sortBy(
        artifact => (artifact.componentIdentity, artifact.artifactIdentity)))
      actualScope = inventory.scope.copy(artifacts = inventory.scope.artifacts.sortBy(
        artifact => (artifact.componentIdentity, artifact.artifactIdentity)))
      _ <- if (actualScope == requestedScope) Consequence.unit
        else Consequence.stateConflict("Validation inventory differs from the exact requested scope identity, revision or artifacts")
      candidates = (selected.map(_.reference) ++ request.supplemental).map(Invocation(_, Map.empty)) ++ request.invocations
      bound <- candidates.foldLeft(Consequence.success(Vector.empty[B.BoundOperation])) { (result, invocation) =>
        result.flatMap { operations =>
          B.bindC(inventory, invocation.reference, request.workspace,
            request.executionParameters ++ invocation.parameters).flatMap { operation =>
            val next = if (operations.exists(bound => bound.operation.reference == operation.operation.reference &&
                bound.parameters == operation.parameters)) operations else operations :+ operation
            if (next.size <= 16) Consequence.success(next)
            else Consequence.stateConflict("Validation selected union exceeds 16 normalized operation invocations")
          }
        }
      }
    } yield new Selection(inventory, request.query, request.supplemental, bound)

  private def _request_c(request: Request): Consequence[Unit] =
    if (request == null || request.scope == null || request.query == null || request.supplemental == null ||
        request.workspace == null.asInstanceOf[WorkspaceId] || request.invocations == null ||
        !GoalPhaseNativeMetadata.parametersValid(request.executionParameters))
      Consequence.stateConflict("Validation metadata request requires structurally present bounded inputs and workspace")
    else if (request.scope.identity == null || request.scope.revision == null || request.scope.artifacts == null ||
        request.scope.artifacts.exists(artifact => artifact == null || artifact.componentIdentity == null ||
          artifact.artifactIdentity == null || artifact.artifactRevision == null || artifact.sourceRevision == null ||
          artifact.root == null || artifact.packagePrefix == null) ||
        request.supplemental.exists(reference => reference == null || reference.component == null ||
          reference.specification == null || reference.operation == null) ||
        request.invocations.exists(invocation => invocation == null ||
          !GoalPhaseNativeMetadata.referenceValid(invocation.reference) ||
          !GoalPhaseNativeMetadata.parametersValid(invocation.parameters)))
      Consequence.stateConflict("Validation metadata request contains missing scope or exact reference fields")
    else if (request.supplemental.size > 16 || request.invocations.size > 16)
      Consequence.stateConflict("Validation metadata request exceeds 16 supplemental references or explicit invocations")
    else WorkspaceId.parseC(request.workspace.value).map(_ => ())
}
