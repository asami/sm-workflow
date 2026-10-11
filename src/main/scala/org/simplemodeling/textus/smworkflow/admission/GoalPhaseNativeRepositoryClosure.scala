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
import org.simplemodeling.textus.smworkflow.value.RepositorySyncStartCommand
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import RepositorySyncManagedFetch as Fetch
import RepositorySyncNativeConvergence as R
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Owning Phase calls the actually installed child Start/advance Operations.
  * Semantic work and Human questions keep the child's own completion routing.
  * Neither a pasted Terminal nor Phase review acceptance grants remote effects.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseNativeRepositoryClosure {
  final case class Input(instance: String, sourceRevision: String, plan: O.Plan,
    base: O.Snapshot, completed: Vector[O.ClosedStep], candidate: O.Snapshot,
    review: String, childInvocation: String)
  final case class Authorization(reference: String, revision: Int, input: Input)
  trait AuthorityPort {
    def loadForCallC(core: ActionCall.Core, input: Input): Consequence[Option[Authorization]]
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
      RepositorySyncManagedServiceAssembly.bindC(params, declaration, selections,
        nativeProgression = true, nativeAcknowledgement = acknowledge, conflictPolicy = conflicts,
        validationPolicy = Some(validation), nativeReview = review, convergence = Some(convergence), humanConflict = humanConflict)
  }
  final case class Payload(input: Input, childCommand: RepositorySyncStartCommand, childIdentity: String,
    authority: String, authorityRevision: Int, result: Option[R.Result], childRevision: Option[String])
  final case class Result(plan: O.Plan, originalBase: O.Snapshot, completed: Vector[O.ClosedStep],
    reviewedCandidate: O.Snapshot, repository: R.Result, repositoryRevision: String)
  val resultCodec = new WorkflowResultJsonV1.PayloadCodec[Result] {
    val typeIdentity = "GoalPhaseAdvanceResult"
    def encode(value: Result): Json = value.asJson
    def decode(value: Json): Either[String, Result] = for {
      _ <- Either.cond(resultShape(value), (), "whole-Phase Terminal has undeclared or malformed fields")
      cursor = value.hcursor
      plan <- cursor.get[O.Plan]("plan").left.map(_.message)
      base <- cursor.get[O.Snapshot]("originalBase").left.map(_.message)
      completed <- cursor.get[Vector[O.ClosedStep]]("completed").left.map(_.message)
      candidate <- cursor.get[O.Snapshot]("reviewedCandidate").left.map(_.message)
      repositoryJson <- cursor.downField("repository").focus.toRight("missing repository result")
      repository <- R.resultCodec.decode(repositoryJson)
      revision <- cursor.get[String]("repositoryRevision").left.map(_.message)
    } yield Result(plan, base, completed, candidate, repository, revision)
  }
  private[admission] val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseAdvanceResult"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-repository-closure.v1"),
      "payload" -> Json.obj("input" -> value.input.asJson,
        "childCommand" -> RepositorySyncStartServiceOperation.commandJson(value.childCommand),
        "childIdentity" -> Json.fromString(value.childIdentity), "authority" -> Json.fromString(value.authority),
        "authorityRevision" -> Json.fromInt(value.authorityRevision), "result" -> value.result.asJson,
        "childRevision" -> value.childRevision.asJson))
    def decode(value: Json): Either[String, Payload] = for {
      _ <- Either.cond(value.asObject.exists(_.keys.toSet == Set("schemaVersion", "payload")) &&
        value.hcursor.get[String]("schemaVersion").contains("sm-workflow.goal-phase-native-repository-closure.v1"),
        (), "Phase repository closure envelope differs")
      fields <- value.hcursor.downField("payload").focus.flatMap(_.asObject)
        .filter(_.keys.forall(Set("input", "childCommand", "childIdentity", "authority", "authorityRevision", "result", "childRevision")))
        .toRight("Phase repository closure has undeclared fields")
      _ <- Either.cond(fields("input").exists(inputShape), (), "Phase repository closure contains malformed nested fields")
      cursor = Json.fromJsonObject(fields).hcursor
      input <- cursor.get[Input]("input").left.map(_.message)
      command <- fields("childCommand").flatMap(v => RepositorySyncStartServiceOperation.decodeCommandC(v.noSpaces).toOption)
        .toRight("Phase repository closure child command is incomplete")
      child <- cursor.get[String]("childIdentity").left.map(_.message)
      authority <- cursor.get[String]("authority").left.map(_.message)
      revision <- cursor.get[Int]("authorityRevision").left.map(_.message)
      result <- fields("result").filterNot(_.isNull) match {
        case None => Right(None)
        case Some(value) => R.resultCodec.decode(value).map(Some(_))
      }
      childrevision <- cursor.get[Option[String]]("childRevision").left.map(_.message)
    } yield Payload(input, command, child, authority, revision, result, childrevision)
  }

  private def inputShape(value: Json): Boolean = value.asObject.exists(fields =>
    fields.keys.forall(Set("instance", "sourceRevision", "plan", "base", "completed", "candidate", "review", "childInvocation")) &&
      fields("plan").exists(GoalPhaseNativeWork.planShape) && fields("base").exists(GoalPhaseNativeWork.snapshotShape) &&
      fields("candidate").exists(GoalPhaseNativeWork.snapshotShape) &&
      fields("completed").flatMap(_.asArray).exists(_.forall(GoalPhaseNativeWork.closedShape)))
  private def resultShape(value: Json): Boolean = value.asObject.exists(fields =>
    fields.keys.forall(Set("plan", "originalBase", "completed", "reviewedCandidate", "repository", "repositoryRevision")) &&
      fields("plan").exists(GoalPhaseNativeWork.planShape) && fields("originalBase").exists(GoalPhaseNativeWork.snapshotShape) &&
      fields("reviewedCandidate").exists(GoalPhaseNativeWork.snapshotShape) &&
      fields("completed").flatMap(_.asArray).exists(_.forall(GoalPhaseNativeWork.closedShape)))

  /** Actual owning candidate/chain and invocation scope; no complete Plan or review-body witness. */
  private[admission] def sameTarget(left: Input, right: Input): Boolean =
    left != null && right != null && left.instance == right.instance && left.sourceRevision == right.sourceRevision &&
      left.plan != null && right.plan != null && left.plan.identity == right.plan.identity && left.plan.revision == right.plan.revision &&
      left.plan.phase == right.plan.phase && left.plan.repository == right.plan.repository &&
      left.base == right.base && left.candidate == right.candidate && left.review == right.review &&
      left.childInvocation == right.childInvocation && left.completed != null && right.completed != null &&
      left.completed.map(s => s.step -> s.commit) == right.completed.map(s => s.step -> s.commit)

  private[admission] def childTargetC(command: RepositorySyncStartCommand, payload: Payload,
    participant: String, declaration: Fetch.Declaration): Consequence[Unit] = for {
    identity <- Fetch.identityC(command)
    _ <- if (Fetch.sameTarget(command.input, declaration.input) && command.participantIdentity == participant &&
        command.idempotencyKey == payload.input.childInvocation && identity.value == payload.childIdentity) Consequence.unit
      else F.invalid("saved child Start crosses its actual repository, participant or invocation")
  } yield ()

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
            authority <- installation.authority.loadForCallC(core, input)
            saved <- authority match {
              case None => Consequence.success(Option.empty[(InstanceRecord, Payload)])
              case Some(value) => for {
                _ <- if (value != null && id(value.reference) && value.revision > 0 && sameTarget(value.input, input)) Consequence.unit
                  else F.invalid("repository closure authority crosses the whole selected Phase")
                actual <- G.observeC(core)
                _ <- if (actual.head == input.candidate.head && actual.tree == input.candidate.tree &&
                  actual.branch == d.target.localBranch && actual.dirty.isEmpty) Consequence.unit
                  else F.invalid("accepted whole-Phase candidate changed before child Start")
                entered <- controller.acceptC(O.ClosureIntentSaved(input.candidate, store.intentIdentity))
                _ <- if (entered.stage == O.Stage.PhaseClosing && domain.statemachine.GoalPhaseRunLifecycle.permits(
                  "PhaseClosing", "phase-closure-intent-saved", "PhaseClosing")) Consequence.unit
                  else F.invalid("repository closure intent is absent from CML")
                command = RepositorySyncStartCommand(installation.declaration.input, installation.selectionIdentity,
                  session.command.participantIdentity, input.childInvocation)
                identity <- Fetch.identityC(command)
                intent <- store.saveC("intent", from, Payload(input, command, identity.value,
                  value.reference, value.revision, None, None), closed = false)
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
            identity <- Fetch.identityC(payload.childCommand)
            childcommand = payload.childCommand
            selectedinput = payload.input
            definition <- RepositorySyncDefinitionBinding.bindC
            childStorage <- Fetch.bindC(installation.declaration, definition, core)
            instances <- childStorage.bindInstancesC()(using core.executionContext)
            existing <- instances.loadC(identity)(using core.executionContext)
            _ <- existing match {
              case None => invokeC(core, Fetch.startOperation.operation, RepositorySyncStartServiceOperation.encodeCommand(childcommand)).map(_ => ())
              case Some(_) => Fetch.sessionC(identity.value, installation.declaration, definition, core).flatMap(s =>
                if (Fetch.sameTarget(s.command.input, childcommand.input) && s.command.selectionIdentity == childcommand.selectionIdentity &&
                    s.command.participantIdentity == childcommand.participantIdentity && s.command.idempotencyKey == childcommand.idempotencyKey)
                  Consequence.unit else F.invalid("selected child Start differs from its owning Phase invocation"))
            }
            childWire <- invokeC(core, Fetch.advanceOperation.operation, RepositorySyncManagedServiceAssembly.encodeAdvance(identity.value))
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
                child <- Fetch.sessionC(identity.value, installation.declaration, definition, core)
                record = child.started.current
                handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, record)
                _ <- if (record.lifecycle == Lifecycle.Completed && record.suspension.isEmpty &&
                    value.handle == handle && result.instanceIdentity == identity.value &&
                    result.repositoryRoot == d.target.root.toString && result.localBranch == d.target.localBranch &&
                    result.remote == d.target.remote && result.remoteBranch == d.target.remoteBranch)
                  Consequence.unit else F.invalid("child Terminal differs from its actual completed native instance")
                actual <- G.observeC(core)
                remote <- G.remoteTipC(core, d.target.remoteBranch)
                ancestry <- E.executeNativeGitC(core, E.NativeGitEffect.IsAncestor(selectedinput.candidate.head, result.tip))
                _ <- G.requiredTextC(ancestry).flatMap(text => if (text.isEmpty) Consequence.unit
                  else F.invalid("repository ancestry observation is malformed"))
                _ <- if (actual.head == result.tip && actual.tree == result.tree && actual.dirty.isEmpty &&
                  actual.trackingTip.contains(remote) && result.tip == remote && actual.counts.contains(G.Counts(0, 0)))
                  Consequence.unit else F.invalid("child convergence is not the actual current native repository")
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
        val p = value.receipt.payload.value
        if (sameTarget(p.input, input) && id(p.authority) &&
          p.authorityRevision > 0)
          childTargetC(p.childCommand, p, session.command.participantIdentity, d.repositoryClosure.get.declaration)
            .map(_ => Some(value.receipt.next -> p))
        else F.invalid("retained repository closure crosses its actual Phase/child invocation")
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
