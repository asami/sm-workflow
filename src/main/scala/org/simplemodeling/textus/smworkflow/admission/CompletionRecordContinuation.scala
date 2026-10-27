/**
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Same-owner record closure over the original Completed history. The admitted
  * checkpoint retains the original accepted payload and terminal; only exact
  * clerical record deltas may use its existing selected commit owner. No semantic
  * work, publication, accepted Submit or Start is dispatched here.
  */
private[smworkflow] object CompletionRecordContinuation {
  val purpose = "completion-record-closure-continuation"
  val operation = StateMachineOperationIdentity("CompletionRecordClosureService", "advanceCompletionRecordClosure")
  // The dedicated continuation API selects declared composite execution states,
  // unlike the constituent labels retained in ordinary historical entries.
  private[admission] val phaseProgression = "PhaseClosing"
  private[admission] val taskProgression = "Running"
  final case class Source(family: String, scope: String, participant: String, root: String, branch: String,
    head: String, tree: String, owned: Vector[String], paths: Vector[String], evidence: Vector[String],
    selection: String, progression: String, accepted: Json)
  object Source { given Encoder[Source] = deriveEncoder; given Decoder[Source] = deriveDecoder }
  final case class Payload(target: Json, source: Source, plan: CompletionRecords.Plan,
    authorityIdentity: String, authorityRevision: String, stage: String,
    commit: Option[String] = None, execution: Option[Json] = None)
  object Payload { given Encoder[Payload] = deriveEncoder; given Decoder[Payload] = deriveDecoder }
  val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "CompletionRecordClosureCheckpoint"
    def encode(value: Payload) = value.asJson
    def decode(value: Json) = value.as[Payload].left.map(_.message).flatMap(p =>
      Either.cond(CompletionRecords.valid(p.plan) && p.plan.session == p.target.hcursor.get[String]("sessionId").getOrElse("") &&
        p.plan.family == p.source.family && p.plan.scope == p.source.scope && p.plan.acceptedTree == p.source.tree &&
        p.plan.files.map(_.path) == p.source.paths && Set("Prepared", "Applied", "CommitIntent", "CommitObserved", "Closed")(p.stage),
        p, "Completion record checkpoint differs from its admitted source"))
  }
  def selectBinding(value: Json): Either[String, Json] = for {
    target <- value.hcursor.get[Json]("completionRecordClosure").left.map(_.message)
    _ <- Either.cond(target.asObject.exists(_.keys.toSet == Set("target", "participant", "authorityIdentity", "authorityRevision")), (), "Incomplete closure authority binding")
    _ <- target.hcursor.get[Json]("target").left.map(_.message)
    _ <- target.hcursor.get[String]("participant").left.map(_.message).flatMap(s => Either.cond(s.trim.nonEmpty, (), "Missing participant"))
  } yield Json.obj("completionRecordClosure" -> target)
  def binding(target: Json, participant: String, reference: V.WorkflowArtifactReference): Json = Json.obj("completionRecordClosure" -> Json.obj(
    "target" -> target, "participant" -> participant.asJson, "authorityIdentity" -> reference.identity.asJson, "authorityRevision" -> reference.revision.asJson))
  private def reference(id: String, revision: String, role: String) = WorkflowMechanicalTransition.Reference(id + ":completion-records:" + revision + ":" + role, "1")
  private def empty = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())
  private def invalid[A](s: String): Consequence[A] = Consequence.stateConflict("Completion closure: " + s)

  def publicTargetC(value: org.goldenport.record.Record): Consequence[Json] = for {
    text <- value.toJsonStringC
    parsed <- io.circe.parser.parse(text).fold(e => invalid(e.message), Consequence.success)
    canonical <- GoalPhaseNativeMetadata.normalizePublicJson(parsed).fold(invalid(_), Consequence.success)
  } yield canonical

  def bindC(creation: ComponentCreate, d: GoalPhaseNativeRun.Declaration): Consequence[spec.ServiceDefinition] = for {
    evidence <- NativeExternalEvidence.bindBoundedC(creation.configuration, BoundedWorkRuntime.host(d))
  } yield NativeWorkflowDescriptionMetadata.service(SmWorkflowComponent.CompletionRecordClosureServiceService,
    spec.OperationDefinitionGroup(NonEmptyVector.of(new Operation(true, d, evidence), new Operation(false, d, evidence))))

  private def sourceC(family: String, d: GoalPhaseNativeRun.Declaration, storage: WorkflowComponentStorage,
    current: InstanceRecord, core: ActionCall.Core): Consequence[Source] = {
    if (family == "phase") for {
      starts <- storage.bindMechanicalC(GoalPhaseNativeRun.startOperation, GoalPhaseNativeRun.codec)
      start <- starts.loadRetainedC(WorkflowMechanicalTransition.Reference(current.identity.value + ":ordered-start", "1"))(using core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(invalid("Original Phase Start is absent")))
      completed <- storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, GoalPhasePostCompletionSync.codec)
      saved <- completed.loadRetainedC(GoalPhasePostCompletionSync.reference(current.identity.value))(using core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(invalid("This terminal format has no attributable local Phase completion source")))
      payload = saved.receipt.payload.value
      _ <- GoalPhaseNativeHistory.prefixC(current, saved.receipt.next.revision).flatMap(p =>
        if (p == saved.receipt.next) Consequence.unit else invalid("Completion source is not actual native history"))
      _ <- GoalPhasePostCompletionSync.completionEntryC(saved.receipt.next, current)
      source = Source(family, payload.input.plan.phase, start.receipt.payload.value.participantIdentity,
        start.receipt.payload.value.root, start.receipt.payload.value.branch, payload.input.candidate.head, payload.input.candidate.tree,
        payload.input.plan.steps.flatMap(_.ownedPaths).distinct,
        CompletionRecords.selected(d.completionRecords, family, payload.input.plan.phase),
        Vector(saved.receipt.reference.identity + "@" + saved.receipt.reference.revision, payload.input.review),
        "goal-step-" + d.closureInstallation.toVector.flatMap(_.policy.programs).find(_.step == GoalPhaseNativeReleaseClosure.programIdentity).map(_.ordinal).getOrElse(0),
        phaseProgression, GoalPhasePostCompletionSync.codec.encode(payload))
    } yield source
    else {
      val runtime = if (family == "delegate-task") DelegateTaskNativeRun else GoalTaskNativeRun
      for {
        payload <- runtime.loadPayloadC(storage, current.identity.value, current.revision.value, core)
        _ <- if (Set("Completed", "NoChange", "Returned")(payload.state.stage) && payload.state.input.task.nonEmpty &&
          payload.state.commit.nonEmpty && payload.state.candidate.exists(_.tree == payload.state.commit.get.tree)) Consequence.unit
          else invalid("Terminal has no accepted committed Task source")
        commit = payload.state.commit.get
      } yield Source(family, payload.state.input.requestIdentity, payload.state.participant, payload.root, payload.branch,
        commit.identity, commit.tree, payload.state.input.ownedPaths,
        CompletionRecords.selected(d.completionRecords, family, payload.state.input.requestIdentity),
        (payload.state.evidenceReferences ++ payload.state.reviewReference.toVector ++ payload.state.semanticReference.toVector).map(r => r.identity + "@" + r.revision).distinct,
        family, taskProgression, runtime.stateCodec.encode(payload))
    }
  }
  // Native codec JSON is opaque to public Record naming and null projection.
  // Preserve every accepted fact as stable text instead of recursively rewriting it.
  private def acceptedText(value: Json): String = {
    def ordered(json: Json): Json = json.asObject match {
      case Some(fields) => Json.obj(fields.toVector.sortBy(_._1).map { (key, nested) => key -> ordered(nested) }*)
      case None => json.asArray.fold(json)(values => Json.fromValues(values.map(ordered)))
    }
    ordered(value).noSpaces
  }
  private def target(core: ActionCall.Core, native: WorkflowTerminalClosureContinuation.Target, source: Source): Json = Json.obj(
    "componentInstance" -> core.component.get.core.instanceId.canonicalKey.asJson,
    "sessionId" -> native.instance.value.asJson, "definitionIdentity" -> native.definitionIdentity.value.asJson,
    "definitionRevision" -> native.definitionRevision.value.asJson, "correlation" -> native.correlation.value.asJson,
    "terminalSequence" -> native.terminalSequence.value.asJson, "terminalRevision" -> native.terminalRevision.value.toString.asJson,
    "terminalCausation" -> native.terminalCausation.value.asJson, "entityRevision" -> native.entityRevision.value.toString.asJson,
    "source" -> source.asJson.mapObject(_.add("accepted", acceptedText(source.accepted).asJson)))

  private final class Operation(describe: Boolean, d: GoalPhaseNativeRun.Declaration, evidence: NativeExternalEvidence.Reader) extends spec.OperationDefinition {
    private val declared = SmWorkflowComponent.CompletionRecordClosureServiceService
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe) declared.DescribeCompletionRecordClosureOperation else declared.AdvanceCompletionRecordClosureOperation)
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] = for {
      _ <- if (incoming != null && incoming.service.contains("CompletionRecordClosureService") && incoming.operation ==
        (if (describe) "describeCompletionRecordClosure" else "advanceCompletionRecordClosure")) Consequence.unit
        else invalid("The actual declared closure service/operation is required")
      input <- NativeWorkflowOperationInput.valueC[V.CompletionRecordClosureInput](incoming, "input")
      _ <- if (Set("phase", "goal-task", "delegate-task")(input.family) && input.sessionId.trim.nonEmpty &&
        (describe || (input.target.nonEmpty && input.authorityReference.nonEmpty && input.terminalRevision.nonEmpty))) Consequence.unit
        else invalid("Exact owner, original terminal target and independent authority reference are required")
    } yield new CommandAction {
      val request = incoming
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(input.sessionId))
          selected = core.copy(executionContext = context)
          definition <- if (input.family == "phase") GoalPhaseRuntimeCatalog.loadC().map(_.definition)
            else if (input.family == "goal-task") GoalTaskRuntimeCatalog.loadC() else DelegateTaskRuntimeCatalog.loadC()
          storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, org.goldenport.cncf.component.ComponentOrigin.Builtin),
            SmWorkflowComponent.componentId, d.configuration, Vector(definition), d.instances, d.continuations)
          instances <- storage.bindInstancesC()(using context)
          current <- instances.loadC(InstanceIdentity(input.sessionId))(using context)
            .flatMap(_.filter(_.definition == definition).map(Consequence.success).getOrElse(invalid("No same-owner definition/Session exists")))
          adapter <- storage.bindTerminalClosureContinuationC(operation, codec)
          response <- if (describe) for {
            native <- adapter.inspectC(current.identity)(using context)
            source <- sourceC(input.family, d, storage, current, selected)
            _ <- GoalPhaseParticipantAdmission.admitC(source.participant, context)
            _ <- if (source.paths.nonEmpty && source.paths.forall(p => StandaloneFixExecution.owned(p, source.owned))) Consequence.unit
              else invalid("No required in-scope completion record destinations are selected")
            raw = target(selected, native, source)
          } yield view(input.sessionId, source, "Description", native.terminalRevision.value.toString, raw, None)
          else for {
            supplied <- publicTargetC(input.target.get)
            retained <- adapter.loadRetainedC(reference(input.sessionId, input.terminalRevision.get, "admitted"), current.identity)(using context)
            checkpoint <- retained match {
              case Some(value) =>
                if (value.receipt.payload.value.target == supplied && value.receipt.payload.value.source.family == input.family &&
                  value.receipt.payload.value.authorityIdentity == input.authorityReference.get.identity && value.receipt.payload.value.authorityRevision == input.authorityReference.get.revision)
                  Consequence.success(value.receipt) else invalid("Redelivery crosses the original admitted closure target or authority")
              case None => for {
                native <- adapter.inspectC(current.identity)(using context)
                source <- sourceC(input.family, d, storage, current, selected)
                _ <- GoalPhaseParticipantAdmission.admitC(source.participant, context)
                actual = target(selected, native, source)
                _ <- if (actual == supplied && native.terminalRevision.value.toString == input.terminalRevision.get && source.paths.nonEmpty)
                  Consequence.unit else {
                    val keys = (actual.asObject.toVector.flatMap(_.keys) ++ supplied.asObject.toVector.flatMap(_.keys)).distinct.sorted
                    val changed = keys.filter(k => actual.hcursor.downField(k).focus != supplied.hcursor.downField(k).focus)
                    invalid("Stale or crossed terminal closure target; differing fields: " + changed.mkString(",") +
                      "; terminal revision matches: " + (native.terminalRevision.value.toString == input.terminalRevision.get) +
                      "; required paths selected: " + source.paths.nonEmpty)
                  }
                admitted <- evidence.loadC(selected, NativeExternalEvidence.Purpose.CompletionRecordClosure,
                  ThreeWorkflowExternalEvidencePorts.key(input.authorityReference.get.identity, input.authorityReference.get.revision),
                  binding(actual, source.participant, input.authorityReference.get))
                _ <- if (admitted.nonEmpty) Consequence.unit else Consequence.securityPermissionDenied("Closure continuation requires independently admitted exact same-owner authority; target data and passive preparation grant no authority")
                selection <- GitSelectionId.parseC(source.selection)
                plan <- CompletionRecords.captureC(selected, current.identity.value, source.family, source.scope, source.tree,
                  source.evidence, source.paths, source.owned, d.candidatePaths, selection, allowApplied = true)
                payload = Payload(actual, source, plan, input.authorityReference.get.identity, input.authorityReference.get.revision, "Prepared")
                completed <- adapter.executeC(WorkflowTerminalClosureContinuation.Intent(native,
                  reference(input.sessionId, input.terminalRevision.get, "admitted"), ProgressionReference(source.progression),
                  CausationReference("completion-records-continuation:" + input.terminalRevision.get), TypedValue(codec.typeIdentity, payload)))(using context)
              } yield completed.receipt
            }
            result <- continueC(d, storage, checkpoint, selected, input.terminalRevision.get)
          } yield result
        } yield NativeWorkflowOperationOutput.response(response)
      }
    }
  }

  private def view(id: String, source: Source, status: String, revision: String, facts: Json, commit: Option[String]): V.CompletionRecordClosureView =
    V.CompletionRecordClosureView(id, source.family, source.scope, status, revision, GoalPhaseNativeMetadata.record(facts), commit,
      "Original terminal, accepted work, base and verification remain retained. Publication, return and synchronization retain their original separate outcomes.")

  private def continueC(d: GoalPhaseNativeRun.Declaration, storage: WorkflowComponentStorage,
    admitted: WorkflowMechanicalTransition.Receipt[Payload], core: ActionCall.Core, revision: String): Consequence[V.CompletionRecordClosureView] = for {
    mechanical <- storage.bindMechanicalC(operation, codec)
    instances <- storage.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(admitted.next.identity)(using core.executionContext).flatMap(_.map(Consequence.success).getOrElse(invalid("Closure instance disappeared")))
    closed <- mechanical.loadRetainedC(reference(current.identity.value, revision, "closed"))(using core.executionContext)
    result <- closed match {
      case Some(value) =>
        if (value.receipt.next == current && value.receipt.payload.value.target == admitted.payload.value.target)
          Consequence.success(view(current.identity.value, admitted.payload.value.source, "Terminal", revision, codec.encode(value.receipt.payload.value), value.receipt.payload.value.commit))
        else invalid("A later native result follows this closure receipt; no successor is mutated")
      case None => for {
        attempts <- Consequence.success(current.history.count(_.causation.value.startsWith("completion-records:commit-intent-")))
        _ <- if (attempts < 256) Consequence.unit else invalid("Retained closure attempt bound is exhausted; no replay")
        lastAttempt = math.max(0, attempts - 1)
        applied <- mechanical.loadRetainedC(reference(current.identity.value, revision, "applied"))(using core.executionContext)
        intent <- mechanical.loadRetainedC(reference(current.identity.value, revision, "commit-intent-" + lastAttempt))(using core.executionContext)
        execution <- mechanical.loadRetainedC(reference(current.identity.value, revision, "execution-" + lastAttempt))(using core.executionContext)
        frontier = execution.orElse(intent).orElse(applied).map(_.receipt.next).getOrElse(admitted.next)
        _ <- if (current == frontier && current.lifecycle == Lifecycle.Active && current.suspension.isEmpty) Consequence.unit else invalid("Closure frontier changed; re-read actual results")
        source = admitted.payload.value.source
        root <- G.rootC(core)
        native <- G.inspectC(core)
        _ <- if (root == source.root && native.observed.branch == source.branch && native.mergeHead.isEmpty) Consequence.unit else invalid("Actual closure repository changed")
        nativeCommit <- G.commitC(core, native.observed.head)
        committed = nativeCommit.identity != source.head && nativeCommit.parents == Vector(source.head) && native.observed.dirty.isEmpty
        endedFailed = execution.flatMap(_.receipt.payload.value.execution).exists(raw =>
          raw.hcursor.get[String]("outcome").exists(_.startsWith("Failed")) && raw.hcursor.get[Json]("process").toOption.exists(p =>
            WorkflowSbtCapture.decode(p).toOption.exists(r => WorkflowSbtCapture.exitCode(r).exists(_ != 0))))
        response <- if (committed) for {
          changes <- CompletionRecords.deltaC(core, admitted.payload.value.plan, nativeCommit.tree)
          _ <- CompletionRecords.admitActualC(core, admitted.payload.value.plan, changes)
          saved <- saveC(d, mechanical, frontier, revision, "closed", admitted.payload.value.copy(stage = "Closed", commit = Some(nativeCommit.identity),
            execution = execution.flatMap(_.receipt.payload.value.execution)), true, core)
        } yield view(current.identity.value, source, "Terminal", revision, codec.encode(saved.payload.value), saved.payload.value.commit)
        else if (intent.nonEmpty && !endedFailed) Consequence.success(view(current.identity.value, source, "Incomplete", revision,
          codec.encode(execution.map(_.receipt.payload.value).getOrElse(intent.get.receipt.payload.value)), None))
        else for {
          _ <- if (nativeCommit.identity == source.head && nativeCommit.tree == source.tree) Consequence.unit else invalid("Actual source differs from accepted implementation commit")
          _ <- CompletionRecords.applyC(core, admitted.payload.value.plan)
          tree <- G.indexTreeC(core)
          changes <- CompletionRecords.deltaC(core, admitted.payload.value.plan, tree)
          _ <- CompletionRecords.admitActualC(core, admitted.payload.value.plan, changes)
          result <- if (changes.isEmpty) for {
            saved <- saveC(d, mechanical, frontier, revision, "closed", admitted.payload.value.copy(stage = "Closed", commit = Some(source.head)), true, core)
          } yield view(current.identity.value, source, "Terminal", revision, codec.encode(saved.payload.value), saved.payload.value.commit)
          else for {
          ready <- applied.map(_ => Consequence.success(frontier)).getOrElse(saveC(d, mechanical, frontier, revision, "applied", admitted.payload.value.copy(stage = "Applied"), false, core).map(_.next))
          selection <- GitSelectionId.parseC(source.selection)
          parent <- GitObjectId.parseC(source.head)
          checked <- E.executeGitC(core, GitOperation.CheckSelectedIndex(selection, parent)).flatMap(_.valueC)
          selected <- E.executeGitC(core, GitOperation.SelectedIndexChanges(selection, parent)).flatMap(_.valueC)
          _ <- if (checked.findingKeys.isEmpty && selected == changes) Consequence.unit else invalid("Record commit selection or hygiene differs from exact retained projection")
          pending <- saveC(d, mechanical, ready, revision, "commit-intent-" + attempts, admitted.payload.value.copy(stage = "CommitIntent"), false, core)
          actual <- E.executeGitC(core, GitOperation.CommitSelected(selection))
          saved <- saveC(d, mechanical, pending.next, revision, "execution-" + attempts, admitted.payload.value.copy(stage = "CommitObserved", execution = Some(Json.obj(
            "outcome" -> actual.outcome.toString.asJson, "process" -> WorkflowSbtCapture.encode(actual.processResult)))), false, core)
          result <- if (actual.outcome == GitExecutionOutcome.Succeeded) continueC(d, storage, admitted, core, revision)
            else Consequence.success(view(current.identity.value, source, "Incomplete", revision, codec.encode(saved.payload.value), None))
          } yield result
        } yield result
      } yield response
    }
  } yield result

  def retainedC(storage: WorkflowComponentStorage, current: InstanceRecord, core: ActionCall.Core): Consequence[Option[V.CompletionRecordClosureView]] = {
    val marker = current.history.flatMap(_.terminalClosure).reverse.find(p => p.service == operation.service && p.operation == operation.operation)
    marker match {
      case None => Consequence.success(None)
      case Some(value) => for {
        port <- storage.bindTerminalClosureContinuationC(operation, codec)
        saved <- port.loadRetainedC(WorkflowMechanicalTransition.Reference(value.referenceIdentity, value.referenceRevision), current.identity)(using core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(invalid("Native continuation provenance has no retained checkpoint")))
        result <- continueCFromRetained(storage, saved.receipt, core, value.terminalRevision.value.toString)
      } yield Some(result)
    }
  }
  private def continueCFromRetained(storage: WorkflowComponentStorage, saved: WorkflowMechanicalTransition.Receipt[Payload],
    core: ActionCall.Core, revision: String): Consequence[V.CompletionRecordClosureView] = for {
    adapter <- storage.bindMechanicalC(operation, codec)
    closed <- adapter.loadRetainedC(reference(saved.next.identity.value, revision, "closed"))(using core.executionContext)
    instances <- storage.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(saved.next.identity)(using core.executionContext).flatMap(_.map(Consequence.success).getOrElse(invalid("Closure current instance is absent")))
    _ <- closed.fold(Consequence.unit)(value => if (value.receipt.next == current) Consequence.unit else invalid("A later native result follows the closure; no old Terminal is redisplayed"))
  } yield closed.map(value => view(saved.next.identity.value, saved.payload.value.source, "Terminal", revision,
    codec.encode(value.receipt.payload.value), value.receipt.payload.value.commit)).getOrElse(
    view(saved.next.identity.value, saved.payload.value.source, "Incomplete", revision, codec.encode(saved.payload.value), None))

  private def saveC(d: GoalPhaseNativeRun.Declaration, adapter: WorkflowManagedMechanicalTransition[Payload],
    from: InstanceRecord, revision: String, role: String, payload: Payload, complete: Boolean, core: ActionCall.Core): Consequence[WorkflowMechanicalTransition.Receipt[Payload]] = {
    val entry = HistoryEntry(HistorySequence(from.history.size.toLong + 1), from.revision.next,
      if (complete) Lifecycle.Completed else Lifecycle.Active, if (complete) None else from.currentProgression,
      from.correlation, CausationReference("completion-records:" + role), from.derivedCompositeOccurrence, from.committedPredecessor, None)
    adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference(from.identity.value, revision, role), from,
      from.revision, entry, TypedValue(codec.typeIdentity, payload)), empty)(using core.executionContext).map(_.receipt)
  }
}
