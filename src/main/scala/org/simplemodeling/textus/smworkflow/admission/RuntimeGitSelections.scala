package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.{GitOperation, GitPath, GitSelectionId, GitSelectionParameters}
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec as spec
import org.goldenport.record.{Record, RecordPresentable}

/** Invocation-local execution data derived from the actual native run.
  * This is neither a host grant nor a verification receipt. The ordinary CNCF
  * Git boundary still admits the workspace, actor, capability and arguments.
  * Explicit legacy compositions do not install this data and keep their fixed
  * programs. Nothing is written to a file or cached across calls.
  * @since Oct. 27, 2026
  * @version Oct. 27, 2026
  */
private[smworkflow] object RuntimeGitSelections {
  private val key = "sm-workflow.runtime-git-data"
  private final case class Data(selections: Map[String, GitSelectionParameters]) extends RecordPresentable {
    def toRecord(): Record = Record.data("selections" -> selections.toVector.sortBy(_._1).map { (id, data) =>
      Record.data("identity" -> id, "paths" -> data.paths.map(_.value), "message" -> data.message.getOrElse(""))
    })
  }

  def goalC(service: spec.ServiceDefinition, plan: GoalPhaseOrderedProgression.Plan,
    candidatePaths: Vector[String] = Vector.empty): Consequence[spec.ServiceDefinition] = {
    val owned = plan.phaseOwnedPaths
    val registered = if (candidatePaths.isEmpty) owned else candidatePaths
    val stages = registered.zipWithIndex.map((path, index) => ("goal-path-" + (index + 1), Vector(path), Option.empty[String]))
    val steps = plan.steps.zipWithIndex.map((step, index) => ("goal-step-" + (index + 1), step.ownedPaths,
      Some(plan.phase + ": " + step.purpose.take(200))))
    val release = ("goal-step-" + (plan.steps.size + 1), owned, Some("Release " + plan.phase))
    bindC(service, stages ++ steps :+ release)
  }

  def repositoryC(service: spec.ServiceDefinition, paths: Set[String]): Consequence[spec.ServiceDefinition] =
    bindC(service, paths.toVector.sorted.zipWithIndex.map((path, index) =>
      ("conflict-" + (index + 1), Vector(path), Option.empty[String])))

  def boundedC(service: spec.ServiceDefinition, paths: Vector[String], requestIdentity: String,
    objective: String): Consequence[spec.ServiceDefinition] =
    bindC(service, paths.zipWithIndex.map((path, index) =>
      ("goal-path-" + (index + 1), Vector(path), Option.empty[String])) :+
      ("goal-task", paths, Some(requestIdentity + ": " + objective.take(200))))

  private def bindC(service: spec.ServiceDefinition, rows: Vector[(String, Vector[String], Option[String])]): Consequence[spec.ServiceDefinition] =
    rows.foldLeft(Consequence.success(Map.empty[String, GitSelectionParameters])) { case (prior, (name, paths, message)) => for {
      accumulated <- prior
      identity <- GitSelectionId.parseC(name)
      selected <- paths.foldLeft(Consequence.success(Vector.empty[GitPath])) { (prior, path) => for {
        accumulated <- prior
        parsed <- GitPath.parseC(path)
      } yield accumulated :+ parsed }
    } yield accumulated.updated(name, GitSelectionParameters(identity, selected, message)) }.map { values =>
      val data = Data(values)
      NativeWorkflowDescriptionMetadata.service(service, spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(
        service.operations.operations.toVector.map { operation => new spec.OperationDefinition {
          val specification = operation.specification
          def createOperationRequest(request: Request): Consequence[OperationRequest] =
            operation.createOperationRequest(request.copy(properties = request.properties.filterNot(_.name == key) :+
              Property(key, data, None)))
        }})))
    }

  def selected(core: ActionCall.Core, operation: GitOperation[?]): Option[GitSelectionParameters] = {
    val identity = operation match {
      case GitOperation.StageSelected(id) => Some(id)
      case GitOperation.StageSelectedAll(id) => Some(id)
      case GitOperation.CommitSelected(id) => Some(id)
      case GitOperation.CheckSelectedIndex(id, _) => Some(id)
      case GitOperation.SelectedIndexChanges(id, _) => Some(id)
      case _ => None
    }
    val data = core.action.request.properties.collectFirst { case property if property.name == key => property.value }
      .collect { case value: Data => value }
    for { id <- identity; current <- data; value <- current.selections.get(id.value) } yield value
  }
}
