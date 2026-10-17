package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.Property
import org.goldenport.record.RecordPresentable
import org.goldenport.convert.ValueReader
import org.simplemodeling.textus.smworkflow.value as V
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import RepositorySyncManagedFetch as Fetch
import RepositorySyncNativeConvergence as R
import RepositorySyncManagedGitObservation as G

/** Owning Phase calls the actually installed child Start/advance Operations.
  * Semantic work and Human questions keep the child's own completion routing.
  * Neither a pasted Terminal nor Phase review acceptance grants remote effects.
  * @since   Oct.  6, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeRepositoryClosure {
  final case class Input(instance: String, sourceRevision: String, plan: O.Plan,
    base: O.Snapshot, completed: Vector[O.ClosedStep], candidate: O.Snapshot,
    review: String, childInvocation: String)
  trait AuthorityPort {
    def isGrantedC(core: ActionCall.Core, input: Input): Consequence[Boolean]
  }
  final case class Installation(declaration: Fetch.Declaration, authority: AuthorityPort,
    validation: RepositorySyncNativeMergeValidation.Policy,
    convergence: R.AuthorityPort,
    conflicts: RepositorySyncNativeConflictInput.Policy = RepositorySyncNativeConflictInput.noSemanticPaths,
    review: Option[RepositorySyncNativeMergeReview.Installation] = None,
    humanConflict: Option[RepositorySyncHumanConflict.Installation] = None) {
    def validateC(d: F.Declaration): Consequence[Unit] = for {
      _ <- Fetch.validateDeclarationC(declaration)
      _ <- if (authority != null && convergence != null &&
          validation != null && conflicts != null && review != null && humanConflict != null &&
          declaration.configuration == d.configuration && declaration.target == d.target &&
          declaration.input.repositoryIdentity == d.input.repositoryIdentity &&
          declaration.instances == d.instances && declaration.continuations == d.continuations &&
          d.phaseValidationPolicy.nonEmpty) Consequence.unit
        else F.invalid("Phase repository closure host scope or paired storage is incomplete")
      _ <- validation.validateC
      _ <- conflicts.validateC
      _ <- review.fold(Consequence.unit)(_.validateC)
      _ <- humanConflict.fold(Consequence.unit)(_.validateC)
    } yield ()
    def bindServiceC(params: ComponentCreate, acknowledge: String => Consequence[Unit]): Consequence[spec.ServiceDefinition] =
      RepositorySyncManagedServiceAssembly.bindC(params, declaration,
        nativeProgression = true, nativeAcknowledgement = acknowledge, conflictPolicy = conflicts,
        validationPolicy = Some(validation), nativeReview = review, convergence = Some(convergence), humanConflict = humanConflict)
  }
  final case class Payload(input: Input, childSessionId: String,
    result: Option[V.RepositorySyncNativeConvergenceResult])
  private[admission] val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseRepositoryClosureRecord"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-repository-closure.v3"),
      "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("Phase repository closure is missing")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-native-repository-closure.v3", (), "Phase repository closure schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
    } yield payload
  }

  def inputC(from: InstanceRecord, controller: O.State): Consequence[Input] =
    if (controller.stage == O.Stage.PhaseClosing && controller.cursor == controller.plan.steps.size &&
        controller.completed.size == controller.plan.steps.size && controller.candidate.nonEmpty &&
        controller.reviewEvidence.nonEmpty && from.currentProgression.contains(ProgressionReference("phase.PhaseClosing")))
      Consequence.success(Input(from.identity.value, from.revision.value.toString, controller.plan, controller.base,
        controller.completed, controller.candidate.get, controller.reviewEvidence.get,
        from.identity.value + ":repository-closure:" + from.revision.value))
    else F.invalid("repository closure requires actual whole-Phase checks and independent acceptance")

  def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State, acknowledge: String => Consequence[Unit]): Consequence[V.GoalPhaseAdvanceResult] = for {
    installation <- d.repositoryClosure.map(Consequence.success).getOrElse(F.invalid("repository closure host is absent"))
    _ <- installation.validateC(d)
    input <- inputC(from, controller)
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    store = new Store(adapter, session, d, core, from, input, acknowledge)
    complete <- store.loadC("closed")
    wire <- complete match {
      case Some(value) => store.terminalC(value)
      case None => for {
        prior <- store.loadC("intent")
        prepared <- prior match {
          case Some((record, payload)) => Consequence.success(Some((record, payload, false)))
          case None => for {
            authority <- installation.authority.isGrantedC(core, input)
            saved <- authority match {
              case false => Consequence.success(Option.empty[(InstanceRecord, Payload, Boolean)])
              case true => for {
                actual <- G.observeC(core)
                _ <- if (actual.head == input.candidate.head && actual.tree == input.candidate.tree &&
                  actual.branch == d.target.localBranch && actual.dirty.isEmpty) Consequence.unit
                  else F.invalid("accepted whole-Phase candidate changed before child Start")
                entered <- controller.acceptC(O.ClosureIntentSaved(input.candidate, store.intentIdentity))
                _ <- if (entered.stage == O.Stage.PhaseClosing && domain.statemachine.GoalPhaseRunLifecycle.permits(
                  "PhaseClosing", "phase-closure-intent-saved", "PhaseClosing")) Consequence.unit
                  else F.invalid("repository closure intent is absent from CML")
                identity = core.executionContext.idGeneration.opaqueId("sm-workflow-session")
                intent <- store.saveC("intent", from, Payload(input, identity, None), closed = false)
                _ <- store.ackC("goal-repository-prepared")
              } yield Some((intent._1, intent._2, true))
            }
          } yield saved
        }
        response <- prepared match {
          case None => for {
            context <- NativeHostAuthorityContext.phaseC(core, input)
            waiting <- F.waitC(from, "Whole-Phase repository closure awaits authority",
              "Review acceptance grants no RepositorySync Start or remote effects; the Phase remains open.")
          } yield waiting.copy(authorityContext = Some(context))
          case Some((intent, payload, fresh)) => for {
            _ <- if (fresh) invokeC[V.RepositorySyncStartResult](core, Fetch.startOperation.operation,
              List(Property("input", childStartInput(installation), None),
                Property("sessionId", payload.childSessionId, None))).map(_ => ())
              else Consequence.unit
            child <- invokeC[V.RepositorySyncAdvanceResult](core, Fetch.advanceOperation.operation,
              List(Property("sessionId", payload.childSessionId, None)))
            wire <- if (child.kind != "Terminal") Consequence.success(
              NativeWorkflowOperationOutput.goalChild(from.identity.value, child))
              else for {
                result <- child.convergence.map(Consequence.success).getOrElse(
                  F.invalid("child Terminal has no repository convergence result"))
                _ <- store.ackC("goal-repository-executed")
                selected = payload.input
                entered <- controller.acceptC(O.ClosureIntentSaved(selected.candidate, store.intentIdentity))
                closed <- entered.acceptC(O.PhaseRepositoryClosed(store.intentIdentity, selected.base.head,
                  selected.candidate.head, selected.completed.map(_.commit), child.sessionId + ":" + result.tip))
                _ <- if (closed.stage == O.Stage.Closed && domain.statemachine.GoalPhaseRunLifecycle.permits(
                  "PhaseClosing", "phase-repository-closed", "Closed")) Consequence.unit
                  else F.invalid("whole-Phase closure differs from its CML/controller")
                saved <- store.saveC("closed", intent, payload.copy(result = Some(result)), closed = true)
                _ <- store.ackC("goal-repository-recorded")
                wire <- store.terminalC(saved)
              } yield wire
          } yield wire
        }
      } yield response
    }
  } yield wire

  private def childStartInput(installation: Installation): V.RepositorySyncStartInput =
    installation.declaration.input

  /** Admit only the selected native closure or its already committed completion. */
  private[admission] def completionEntryC(saved: InstanceRecord, current: InstanceRecord,
    childSessionId: String): Consequence[Option[HistoryEntry]] = for {
    _ <- WorkflowInstancePersistence.validateC(saved)
    _ <- WorkflowInstancePersistence.validateC(current)
    _ <- if (saved.lifecycle == Lifecycle.Active &&
        saved.currentProgression.contains(ProgressionReference("phase.Closed")) && saved.suspension.isEmpty &&
        Option(childSessionId).exists(_.trim.nonEmpty) && saved.identity == current.identity &&
        saved.definition.workflowIdentity == current.definition.workflowIdentity &&
        saved.definition.workflowRevision == current.definition.workflowRevision) Consequence.unit
      else F.invalid("whole-Phase completion does not own the current native closure")
    entry = F.history(saved, "phase.Closed", "ordered-phase-completed:" + childSessionId)
      .copy(lifecycle = Lifecycle.Completed, currentProgression = None)
    next <- saved.appendC(saved.revision, entry)
    admitted <- if (current.revision == saved.revision && current.lifecycle == Lifecycle.Active &&
        current.currentProgression.contains(ProgressionReference("phase.Closed")) && current.suspension.isEmpty &&
        current.history.lastOption == saved.history.lastOption) Consequence.success(Some(entry))
      else if (current.revision == next.revision && current.lifecycle == Lifecycle.Completed &&
        current.currentProgression.isEmpty && current.suspension.isEmpty &&
        current.history.lastOption.contains(entry)) Consequence.success(Option.empty[HistoryEntry])
      else F.invalid("whole-Phase completion differs from the selected committed native frontier")
  } yield admitted

  /** Invoke the actually installed public Operation with named business
    * parameters. Its after-commit result is consumed directly; no native wire,
    * copied selector, JSON parsing or additional child status read. */
  private def invokeC[A <: RecordPresentable: ValueReader](core: ActionCall.Core, name: String,
    properties: List[Property]): Consequence[A] = for {
    service <- core.component.get.core.protocol.services.services.find(_.name == "RepositorySyncService")
      .map(Consequence.success).getOrElse(F.invalid("paired child RepositorySyncService is not installed"))
    operation <- service.operations.operations.toVector.find(_.name == name).map(Consequence.success)
      .getOrElse(F.invalid("paired child Operation is not installed: " + name))
    request = core.action.request.copy(service = Some("RepositorySyncService"), operation = name,
      arguments = Nil, switches = Nil, properties = properties)
    action <- operation.createOperationRequest(request).flatMap {
      case value: CommandAction => Consequence.success(value)
      case _ => F.invalid("paired child Operation is not a canonical CommandAction")
    }
    call = action.createCall(core.copy(action = action))
    _ <- call.authorize()(using core.executionContext)
    response <- call.execute().flatMap(call.afterCommitResponseC)
    value <- response match {
      case OperationResponse.RecordResponse(record) => summon[ValueReader[A]].readC(record)
      case _ => F.invalid("paired child Operation returned no declared business record")
    }
  } yield value

  private final class Store(adapter: WorkflowManagedMechanicalTransition[Payload], session: F.Session,
    d: F.Declaration, core: ActionCall.Core, from: InstanceRecord, input: Input,
    acknowledge: String => Consequence[Unit]) {
    val intentIdentity = input.instance + ":ordered-repository-closure:" + input.sourceRevision + ":intent"
    private def reference(key: String) = WorkflowMechanicalTransition.Reference(input.instance +
      ":ordered-repository-closure:" + input.sourceRevision + ":" + key, "1")
    def loadC(key: String): Consequence[Option[(InstanceRecord, Payload)]] = adapter.loadRetainedC(reference(key))(using core.executionContext).flatMap {
      case None => Consequence.success(None)
      case Some(value) =>
        Consequence.success(Some(value.receipt.next -> value.receipt.payload.value))
    }
    def saveC(key: String, current: InstanceRecord, payload: Payload, closed: Boolean): Consequence[(InstanceRecord, Payload)] = for {
      _ <- if (current.currentProgression.contains(ProgressionReference("phase.PhaseClosing"))) Consequence.unit
        else F.invalid("repository closure has no native PhaseClosing predecessor")
      entry = if (closed) F.history(current, "phase.Closed", "ordered-repository-closed:" + payload.childSessionId)
        else F.history(current, "phase.PhaseClosing", "ordered-repository-intent:" + payload.childSessionId)
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration,
        reference(key), current, current.revision, entry, TypedValue(codec.typeIdentity, payload)),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield saved.receipt.next -> saved.receipt.payload.value
    /** Close the normal saved business result through the native instance API. */
    private def closeC(saved: (InstanceRecord, Payload)): Consequence[InstanceRecord] = for {
      _ <- if (saved._1.lifecycle == Lifecycle.Active &&
        saved._1.currentProgression.contains(ProgressionReference("phase.Closed")) &&
        saved._2.result.nonEmpty) Consequence.unit
        else F.invalid("whole-Phase completion has no actual saved repository result")
      instances <- session.storage.bindInstancesC()(using core.executionContext)
      current <- instances.loadC(saved._1.identity)(using core.executionContext).flatMap(
        _.map(Consequence.success).getOrElse(F.invalid("whole-Phase completion has no current native instance")))
      entry <- completionEntryC(saved._1, current, saved._2.childSessionId)
      completed <- entry match {
        case None => Consequence.success(current)
        case Some(value) => for {
          appended <- instances.appendC(saved._1.identity, saved._1.revision, value)(using core.executionContext)
          _ <- ackC("goal-repository-completed")
        } yield appended
      }
    } yield completed
    def terminalC(saved: (InstanceRecord, Payload)): Consequence[V.GoalPhaseAdvanceResult] = for {
      completed <- closeC(saved)
      selected = saved._2.input
    } yield NativeWorkflowOperationOutput.goalComplete(completed.identity.value, selected.plan, selected.base,
      selected.completed, selected.candidate, saved._2.result.get)
    def ackC(point: String): Consequence[Unit] = acknowledge(point) match {
      case Consequence.Success(_) => Consequence.unit
      case _ => org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "Phase repository closure acknowledgement interrupted; child owning intents determine recovery: " + point)
    }
  }
}
