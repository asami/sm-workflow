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
import org.goldenport.protocol.Argument
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
  * @version Oct. 11, 2026
  */
private[smworkflow] object GoalPhaseNativeRepositoryClosure {
  final case class Input(instance: String, sourceRevision: String, plan: O.Plan,
    base: O.Snapshot, completed: Vector[O.ClosedStep], candidate: O.Snapshot,
    review: String, childInvocation: String)
  trait AuthorityPort {
    def isGrantedC(core: ActionCall.Core, input: Input): Consequence[Boolean]
  }
  final case class Installation(declaration: Fetch.Declaration, selectionIdentity: String,
    selections: ProfileStartAdmission.CallScopedSelectionPort, authority: AuthorityPort,
    validation: RepositorySyncNativeMergeValidation.Policy,
    convergence: R.AuthorityPort,
    conflicts: RepositorySyncNativeConflictInput.Policy = RepositorySyncNativeConflictInput.noSemanticPaths,
    review: Option[RepositorySyncNativeMergeReview.Installation] = None,
    humanConflict: Option[RepositorySyncHumanConflict.Installation] = None) {
    def validateC(d: F.Declaration): Consequence[Unit] = for {
      _ <- Fetch.validateDeclarationC(declaration)
      _ <- if (id(selectionIdentity) && selections != null && authority != null && convergence != null &&
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
  final case class Payload(input: Input, childIdentity: String,
    result: Option[R.Result], childRevision: Option[String])
  final case class Result(plan: O.Plan, originalBase: O.Snapshot, completed: Vector[O.ClosedStep],
    reviewedCandidate: O.Snapshot, repository: R.Result, repositoryRevision: String)
  val resultCodec = new WorkflowResultJsonV1.PayloadCodec[Result] {
    val typeIdentity = "GoalPhaseAdvanceResult"
    def encode(value: Result): Json = value.asJson
    def decode(value: Json): Either[String, Result] =
      Option(value).toRight("whole-Phase result is missing").flatMap(_.as[Result].left.map(_.message))
  }
  private[admission] val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseAdvanceResult"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-repository-closure.v2"),
      "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("Phase repository closure is missing")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-native-repository-closure.v2", (), "Phase repository closure schema differs")
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
    from: InstanceRecord, controller: O.State, acknowledge: String => Consequence[Unit]): Consequence[String] = for {
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
          case Some(value) => Consequence.success(Some(value))
          case None => for {
            authority <- installation.authority.isGrantedC(core, input)
            saved <- authority match {
              case false => Consequence.success(Option.empty[(InstanceRecord, Payload)])
              case true => for {
                actual <- G.observeC(core)
                _ <- if (actual.head == input.candidate.head && actual.tree == input.candidate.tree &&
                  actual.branch == d.target.localBranch && actual.dirty.isEmpty) Consequence.unit
                  else F.invalid("accepted whole-Phase candidate changed before child Start")
                entered <- controller.acceptC(O.ClosureIntentSaved(input.candidate, store.intentIdentity))
                _ <- if (entered.stage == O.Stage.PhaseClosing && domain.statemachine.GoalPhaseRunLifecycle.permits(
                  "PhaseClosing", "phase-closure-intent-saved", "PhaseClosing")) Consequence.unit
                  else F.invalid("repository closure intent is absent from CML")
                selection <- installation.selections.loadForCallC(installation.selectionIdentity, core).flatMap {
                  case Some(value) => Consequence.success(value)
                  case None => F.invalid("child repository host selection is missing")
                }
                identity <- Fetch.identityC(selection.selectionId, selection.idempotencyKey)
                intent <- store.saveC("intent", from, Payload(input, identity.value, None, None), closed = false)
                _ <- store.ackC("goal-repository-prepared")
              } yield Some(intent)
            }
          } yield saved
        }
        response <- prepared match {
          case None => F.waitC(from, "Whole-Phase repository closure awaits authority",
            "Review acceptance grants no RepositorySync Start or remote effects; the Phase remains open.")
          case Some((intent, payload)) => for {
            // Exact installed Operations, current caller/participant and same Subsystem.
            definition <- RepositorySyncDefinitionBinding.bindC
            identity = InstanceIdentity(payload.childIdentity)
            selectedinput = payload.input
            childStorage <- Fetch.bindC(installation.declaration, definition, core)
            instances <- childStorage.bindInstancesC()(using core.executionContext)
            existing <- instances.loadC(identity)(using core.executionContext)
            _ <- existing match {
              case None => invokeC(core, Fetch.startOperation.operation, childStartInput(installation)).map(_ => ())
              case Some(_) => Consequence.unit
            }
            childWire <- invokeC(core, Fetch.advanceOperation.operation,
              Json.obj("instanceIdentity" -> Json.fromString(identity.value)).noSpaces)
            terminal <- if (io.circe.parser.parse(childWire).toOption.exists(_.hcursor.get[String]("kind").contains("TERMINAL")))
              WorkflowTerminalJsonV1.decodeC(childWire, R.resultCodec).map(Some(_))
              else Consequence.success(None)
            wire <- terminal match {
              case None => Consequence.success(childWire)
              case Some(value) => for {
                result <- value.current match {
                  case t: WorkflowContinuation.Terminal[?] => Consequence.success(t.result.value.asInstanceOf[R.Result])
                  case _ => F.invalid("child has no actual typed Terminal")
                }
                record <- instances.loadC(identity)(using core.executionContext).flatMap {
                  case Some(value) => Consequence.success(value)
                  case None => F.invalid("completed child repository instance is missing")
                }
                _ <- store.ackC("goal-repository-executed")
                entered <- controller.acceptC(O.ClosureIntentSaved(selectedinput.candidate, store.intentIdentity))
                closed <- entered.acceptC(O.PhaseRepositoryClosed(store.intentIdentity, selectedinput.base.head,
                  selectedinput.candidate.head, selectedinput.completed.map(_.commit), identity.value + ":" + record.revision.value))
                _ <- if (closed.stage == O.Stage.Closed && domain.statemachine.GoalPhaseRunLifecycle.permits(
                  "PhaseClosing", "phase-repository-closed", "Closed")) Consequence.unit
                  else F.invalid("whole-Phase closure differs from its CML/controller")
                saved <- store.saveC("closed", intent, payload.copy(result = Some(result),
                  childRevision = Some(record.revision.value.toString)), closed = true)
                _ <- store.ackC("goal-repository-recorded")
                wire <- store.terminalC(saved)
              } yield wire
            }
          } yield wire
        }
      } yield response
    }
  } yield wire

  private def childStartInput(installation: Installation): String = {
    val input = installation.declaration.input
    Json.obj("input" -> Json.obj(
      "repositoryIdentity" -> Json.fromString(input.repositoryIdentity),
      "localBranch" -> Json.fromString(input.localBranch),
      "trackingRemote" -> Json.fromString(input.trackingRemote),
      "trackingBranch" -> Json.fromString(input.trackingBranch),
      "remoteEndpointIdentity" -> Json.fromString(input.remoteEndpointIdentity),
      "syncMode" -> Json.fromString(input.syncMode)),
      "selectionIdentity" -> Json.fromString(installation.selectionIdentity)).noSpaces
  }

  private def invokeC(core: ActionCall.Core, name: String, wire: String): Consequence[String] = for {
    service <- core.component.get.core.protocol.services.services.find(_.name == "RepositorySyncService")
      .map(Consequence.success).getOrElse(F.invalid("paired child RepositorySyncService is not installed"))
    operation <- service.operations.operations.toVector.find(_.name == name).map(Consequence.success)
      .getOrElse(F.invalid("paired child Operation is not installed: " + name))
    request = core.action.request.copy(service = Some("RepositorySyncService"), operation = name,
      arguments = List(Argument("arg1", wire)), switches = Nil, properties = Nil)
    action <- operation.createOperationRequest(request).flatMap {
      case value: CommandAction => Consequence.success(value)
      case _ => F.invalid("paired child Operation is not a canonical CommandAction")
    }
    call = action.createCall(core.copy(action = action))
    _ <- call.authorize()(using core.executionContext)
    response <- call.execute().flatMap(call.afterCommitResponseC)
    text <- response match {
      case value: OperationResponse.Scalar[?] => value.value match {
        case text: String => Consequence.success(text)
        case _ => F.invalid("paired child Operation returned no Workflow JSON")
      }
      case _ => F.invalid("paired child Operation returned no scalar Workflow result")
    }
  } yield text

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
      entry = if (closed) F.history(current, "phase.Closed", "ordered-repository-closed:" + payload.childIdentity)
        else F.history(current, "phase.PhaseClosing", "ordered-repository-intent:" + payload.childIdentity)
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration,
        reference(key), current, current.revision, entry, TypedValue(codec.typeIdentity, payload)),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield saved.receipt.next -> saved.receipt.payload.value
    /** Close the normal saved business result through the native instance API. */
    private def closeC(saved: (InstanceRecord, Payload)): Consequence[InstanceRecord] = for {
      _ <- if (saved._1.lifecycle == Lifecycle.Active &&
        saved._1.currentProgression.contains(ProgressionReference("phase.Closed")) &&
        saved._2.result.nonEmpty && saved._2.childRevision.nonEmpty) Consequence.unit
        else F.invalid("whole-Phase completion has no actual saved repository result")
      entry = F.history(saved._1, "phase.Closed", "ordered-phase-completed:" + saved._2.childIdentity)
        .copy(lifecycle = Lifecycle.Completed, currentProgression = None)
      instances <- session.storage.bindInstancesC()(using core.executionContext)
      completed <- instances.appendC(saved._1.identity, saved._1.revision, entry)(using core.executionContext)
      _ <- ackC("goal-repository-completed")
    } yield completed
    def terminalC(saved: (InstanceRecord, Payload)): Consequence[String] = for {
      completed <- closeC(saved)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, completed)
      selected = saved._2.input
      result = Result(selected.plan, selected.base, selected.completed, selected.candidate, saved._2.result.get, saved._2.childRevision.get)
      terminal <- WorkflowProtocolV1.projectTerminalC(handle, completed, TypedValue(resultCodec.typeIdentity, result),
        MinimalPresentation("Whole selected Phase closed", "All selected Steps, Phase checks, independent review and actual RepositorySync convergence are retained."))
      wire <- WorkflowTerminalJsonV1.encodeC(terminal, resultCodec)
    } yield wire
    def ackC(point: String): Consequence[Unit] = acknowledge(point) match {
      case Consequence.Success(_) => Consequence.unit
      case _ => org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "Phase repository closure acknowledgement interrupted; child owning intents determine recovery: " + point)
    }
  }
  private def id(value: String) = value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
}
