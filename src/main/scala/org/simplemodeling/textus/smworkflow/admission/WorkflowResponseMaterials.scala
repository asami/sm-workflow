package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceRecord, HistoryEntry}
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V

/** Exact field projections of native retained state, selected without executing
  * any Workflow continuation. Small current business observations remain inline.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  */
private[smworkflow] object WorkflowResponseMaterials {
  def referencesC(record: Record, call: ActionCall.Core): Consequence[Vector[V.WorkflowRetainedFieldReference]] = {
    val service = Option(call.action).flatMap(_.request.service)
    val owner = service.collect {
      case "GoalPhaseService" => "GoalPhaseWorkflow"
      case "GoalTaskService" => "GoalTaskWorkflow"
      case "DelegateTaskService" => "DelegateTaskWorkflow"
      case "StandaloneFixService" => "StandaloneFixWorkflow"
      case "SplitPhaseService" => "SplitPhaseWorkflow"
    }
    val selected = Vector("state", "completion", "validation", "selectedPlan").filter(name => record.getRecord(name).nonEmpty)
    if (owner.isEmpty || selected.isEmpty) Consequence.success(Vector.empty)
    else for {
      identity <- record.getString("sessionId").map(Consequence.success).getOrElse(invalid("response has no owning Session"))
      session <- WorkflowRetainedSnapshot.sessionC(owner.get, identity, call)
      (_, current, core) = session
      references <- selected.foldLeft(Consequence.success(Vector.empty[V.WorkflowRetainedFieldReference])) { (prior, field) => for {
        values <- prior
        reference <- selectC(field, record.getRecord(field).get, current, core)
        material <- WorkflowRetainedSnapshot.readC(reference, core)
        expected = DevelopmentOperationalView.json(record.getRecord(field).get)
        actual <- GoalPhaseNativeMetadata.normalizePublicJson(material).fold(invalid, Consequence.success)
        supplied <- GoalPhaseNativeMetadata.normalizePublicJson(expected).fold(invalid, Consequence.success)
        // These fields are actual separately observed current summaries, rather
        // than the immutable source projection. Keep them inline explicitly.
        overlay = field match {
          case "validation" => Set("evidenceRecording", "assessmentJournal")
          case "completion" => Set("synchronization", "repository")
          case "selectedPlan" => Set("documentEffect")
          case _ => Set.empty[String]
        }
        _ <- if (actual.mapObject(_.filter { case (name, _) => !overlay(name) }) == supplied.mapObject(_.filter { case (name, _) => !overlay(name) }))
          Consequence.unit else invalid("selected retained " + field + " differs from its actual public projection")
      } yield values :+ V.WorkflowRetainedFieldReference(field, WorkflowRetainedSnapshot.publicReference(reference)) }
    } yield references
  }

  private def selectC(field: String, value: Record, current: InstanceRecord, core: ActionCall.Core):
      Consequence[WorkflowRetainedSnapshot.Reference] = {
    val identity = current.identity.value
    val owner = current.definition.workflowIdentity.value
    def entry(predicate: HistoryEntry => Boolean): Consequence[HistoryEntry] =
      current.history.reverse.find(predicate).map(Consequence.success).getOrElse(invalid("selected " + field + " has no native history source"))
    def reference(saved: HistoryEntry, key: String, kind: String): WorkflowRetainedSnapshot.Reference =
      WorkflowRetainedSnapshot.Reference(WorkflowRetainedSnapshot.schema, core.component.get.core.instanceId.canonicalKey,
        owner, current.definition.workflowRevision.value, identity, key, "1", saved.revision.value.toString, kind, "json")
    (owner, field) match {
      case ("GoalTaskWorkflow" | "DelegateTaskWorkflow" | "StandaloneFixWorkflow", "state") =>
        entry(_.causation.value.startsWith("standalone-state:")).map(saved =>
          reference(saved, identity + ":standalone-state:" + saved.revision.value, "bounded-state"))
      case ("GoalPhaseWorkflow", "validation") => for {
        source <- value.getString("sourceRevision").filter(_.matches("[1-9][0-9]*")).map(Consequence.success)
          .getOrElse(invalid("validation has no exact native source revision"))
        saved <- entry(e => e.revision.value > source.toLong && e.causation.value.startsWith("ordered-validation:"))
      } yield reference(saved, identity + ":ordered-validation:" + source + ":" + saved.causation.value.stripPrefix("ordered-validation:"), "goal-validation")
      case ("GoalPhaseWorkflow", "completion") =>
        current.history.find(_.causation.value == "ordered-local-completion:" + identity) match {
          case Some(saved) => Consequence.success(reference(saved, GoalPhasePostCompletionSync.reference(identity).identity, "goal-local-completion"))
          case None => for {
            intent <- entry(_.causation.value.startsWith("ordered-repository-intent:"))
            saved <- entry(_.causation.value.startsWith("ordered-repository-closed:"))
            source <- current.history.find(_.revision.value == intent.revision.value - 1).map(Consequence.success)
              .getOrElse(invalid("repository completion has no original adjacent source"))
          } yield reference(saved, identity + ":ordered-repository-closure:" + source.revision.value + ":closed", "goal-repository-completion")
        }
      case ("SplitPhaseWorkflow", "selectedPlan") => for {
        p <- GoalPhaseRuntimeParameters.decodeC(core.component.get.initializationParameters)
        declaration = SplitPhaseManagedStartAssembly.Declaration(p.configuration, p.instances, p.continuations)
        frontier <- SplitPhaseExecutionFrontierDataStorePort.bindForCallC(core, declaration)
        actual <- frontier.loadForCallC(current.identity, current.revision, core)
        selected <- actual match {
          case Some(SplitPhaseExecutionFrontier.Value.SelectedPreview(native)) =>
            entry(_.causation.value == "selected-preview-prepared:" + identity).map(saved =>
              reference(saved, native.identity, "split-selected-preview").copy(revision = native.revision))
          case Some(SplitPhaseExecutionFrontier.Value.SelectedApply(native)) =>
            entry(_.causation.value == "selected-application-intent:" + identity).map(saved =>
              reference(saved, native.identity, "split-selected-apply").copy(revision = native.revision))
          case _ => invalid("selected Split plan has no retained native frontier reference")
        }
      } yield selected
      case _ => invalid("public field has no supported native retained owner")
    }
  }
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Workflow response material " + reason)
}
