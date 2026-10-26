package org.simplemodeling.textus.smworkflow.preparation

import io.circe.Json
import org.goldenport.cncf.context.RuntimeContext.PropertyNameContext
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.NativeExternalEvidence.Purpose
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseFindingRecording, GoalPhasePlanRevision, GoalPhaseNativeMetadata}

/** Finite wire names for supplied Human questions; native scopes own projection.
  * @since   Oct. 15, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeHumanDecisionQuestionWire {
  private enum Node {
    case Question, Handle, GoalSource, GoalAssignment, GoalResult, Progress, RepositorySource, RepositoryConflict, SplitSource, SplitWork
  }

  def normalize(question: Json, purpose: Purpose): Json = _node(question, Node.Question, purpose)

  def inputType(question: Record): Option[String] = Option(question).flatMap { record =>
    PropertyNameContext.default.aliases("inputType").find(record.keySet.contains)
      .filter(key => record.getAny(key).exists(_.isInstanceOf[String])).flatMap(record.getString)
  }

  private def _node(value: Json, node: Node, purpose: Purpose): Json = value.mapObject { original =>
    val keys = node match {
      case Node.Question => Vector("runId", "continuationId", "requiredOperation", "inputType", "resultType",
        "handle", "operation", "completionOperation", "source")
      case Node.Handle => Vector("componentIdentity", "workflowIdentity", "workflowRevision", "instanceIdentity", "protocolVersion")
      case Node.GoalSource => Vector("requestingResult", "assignment", "repairProgress", "planRevision", "sourceHistoryRecovery", "result")
      case Node.GoalResult => Vector("reviewObservations", "dependencyProposal")
      case Node.GoalAssignment => Vector("instanceIdentity", "sourceRevision", "kind", "stepIdentity", "generation",
        "selectedPlan", "originalBase", "candidate", "checksReceipt", "repairProgress")
      case Node.Progress => Vector("automaticCount", "ordinaryCount", "limit", "runtimePolicy", "selectedLimit", "effectiveLimit")
      case Node.RepositorySource => Vector("requestingResult", "conflict")
      case Node.RepositoryConflict => Vector("instanceIdentity", "conflictRevision", "localTip", "remoteTip", "conflicts")
      case Node.SplitSource => Vector("kind", "context", "checkpoint", "scopeCheckpoint", "result", "work")
      case Node.SplitWork => Vector("continuationId", "source", "authority", "demand", "items")
    }
    val named = keys.foldLeft(original) { (fields, name) =>
      val supplied = PropertyNameContext.default.aliases(name).flatMap(original.apply).map { value =>
        if (name == "repairProgress") _node(value, Node.Progress, purpose)
        else if (name == "sourceHistoryRecovery") GoalPhaseNativeMetadata.normalizePublicJson(value).getOrElse(Json.Null)
        else if (name == "planRevision" || name == "dependencyProposal") GoalPhasePlanRevision.normalizeJsonC(value).toOption.getOrElse(Json.Null)
        else if (name == "reviewObservations") GoalPhaseFindingRecording.normalizeObservations(value).getOrElse(Json.Null)
        else value
      }
      val retained = if (node == Node.Progress || node == Node.GoalResult) PropertyNameContext.default.aliases(name)
        .filterNot(_ == name).foldLeft(fields)((selected, alias) => selected.remove(alias)) else fields
      if (((name == "planRevision" || name == "reviewObservations" || name == "dependencyProposal" || name == "sourceHistoryRecovery") && supplied.size > 1) ||
          ((node == Node.Progress || name == "repairProgress") && supplied.distinct.size > 1)) retained.add(name, Json.Null)
      else supplied.headOption.fold(retained)(selected => retained.add(name, selected))
    }
    val children = node match {
      case Node.Question => Vector("handle" -> Node.Handle, "source" -> (purpose match {
        case Purpose.GoalHuman => Node.GoalSource
        case Purpose.RepositoryHuman => Node.RepositorySource
        case _ => Node.SplitSource
      }))
      case Node.GoalSource => Vector("assignment" -> Node.GoalAssignment, "repairProgress" -> Node.Progress, "result" -> Node.GoalResult)
      case Node.GoalAssignment => Vector("repairProgress" -> Node.Progress)
      case Node.RepositorySource => Vector("conflict" -> Node.RepositoryConflict)
      case Node.SplitSource => Vector("work" -> Node.SplitWork)
      case _ => Vector.empty
    }
    children.foldLeft(named) { case (fields, (key, child)) =>
      fields(key).fold(fields)(selected => fields.add(key, _node(selected, child, purpose)))
    }
  }
}
