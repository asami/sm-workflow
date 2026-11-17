package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import BoundedWorkRuntime.*
import BoundedWorkRuntime.given
import StandaloneFixExecution as S
import StandaloneFixExecution.given
import DelegateTaskNativeRun as N

/** Direction for an independently attested ended intent; never a process receipt. */
private[smworkflow] object DelegateTaskValidationRecovery {
  val purpose = "delegate-task-validation-recovery"
  val schema = "sm-workflow.delegate-task-validation-recovery.v1"
  final case class Target(schemaVersion: String, componentInstance: String, sessionId: String, participant: String,
    frontierRevision: Long, root: String, branch: String, baseHead: String, candidate: S.Candidate,
    check: GoalPhaseNativeValidation.Check, successfulPrefix: Vector[String], policy: Json, captureAvailability: String)
  given Encoder[Target] = deriveEncoder
  given Decoder[Target] = deriveDecoder
  final case class Proof(terminalEventReference: String, callerCompletionReference: String,
    termination: String, eventExitCode: Option[Int], callerStatus: String, callerExitCode: Int,
    historicalAssociation: String, rawCapture: String, assertionStatus: String)
  given Encoder[Proof] = deriveEncoder
  given Decoder[Proof] = deriveDecoder
  final case class Direction(frontierRevision: Long, referenceIdentity: String, referenceRevision: String,
    target: Target)
  given Encoder[Direction] = deriveEncoder
  given Decoder[Direction] = deriveDecoder
  trait EvidencePort { def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit] }
  def inputForCallC(incoming: Request, call: ActionCall.Core): Consequence[V.DelegateTaskValidationRecoveryInput] = {
    final class Reader extends ProcedureActionCall {
      val core = call
      def readC: Consequence[V.DelegateTaskValidationRecoveryInput] = for {
        source <- NativeWorkflowResourceInput.selectC[V.DelegateTaskValidationRecoveryInput](incoming)
        input <- NativeWorkflowResourceInput.readC(source, ref => read_resource_text(ref, Some(java.nio.charset.StandardCharsets.UTF_8)))
      } yield input
      def execute(): Consequence[OperationResponse] = N.invalid("Recovery input reader is not an operation")
    }
    new Reader().readC
  }
  def descriptionForCallC(incoming: Request, call: ActionCall.Core): Consequence[V.DelegateTaskValidationRecoveryDescriptionInput] = {
    final class Reader extends ProcedureActionCall {
      val core = call
      def readC: Consequence[V.DelegateTaskValidationRecoveryDescriptionInput] = for {
        source <- NativeWorkflowResourceInput.selectC[V.DelegateTaskValidationRecoveryDescriptionInput](incoming)
        input <- NativeWorkflowResourceInput.readC(source, ref => read_resource_text(ref, Some(java.nio.charset.StandardCharsets.UTF_8)))
      } yield input
      def execute(): Consequence[OperationResponse] = N.invalid("Recovery description input reader is not an operation")
    }
    new Reader().readC
  }
  private def exact(s: String): Boolean = s != null && s.nonEmpty && s.trim == s && !s.exists(_.isControl)
  private def parseC(s: String): Consequence[Json] = io.circe.parser.parse(Option(s).getOrElse(""))
    .fold(e => N.invalid(e.message), Consequence.success)
  def targetC(s: String): Consequence[Target] = for {
    json <- parseC(s)
    value <- json.as[Target].fold(e => N.invalid(e.message), Consequence.success)
    _ <- if (value.asJson == json && value.schemaVersion == schema && value.frontierRevision > 0 &&
      value.frontierRevision < Long.MaxValue && Vector(value.componentInstance, value.sessionId, value.participant,
        value.root, value.branch, value.baseHead).forall(exact) && value.candidate != null && S.validCandidate(value.candidate) &&
      value.check != null && value.check.kind == WorkflowSbtValidation.Kind.Test && value.check.ordinal > 0 &&
      value.successfulPrefix != null && value.successfulPrefix.forall(exact) && value.policy != null && value.policy.isObject &&
      Set("available", "not-retained", "absent", "unavailable-or-unreadable")(value.captureAvailability))
      Consequence.unit else N.invalid("Invalid closed validation recovery target")
  } yield value
  def proofC(s: String): Consequence[Proof] = for {
    json <- parseC(s)
    value <- json.as[Proof].fold(e => N.invalid(e.message), Consequence.success)
    _ <- if (value.asJson == json && Vector(value.terminalEventReference, value.callerCompletionReference).forall(exact) &&
      Set("exited", "output-limit-exceeded", "timed-out", "cancelled", "artifact-limit-exceeded")(value.termination) &&
      value.eventExitCode != null && (value.termination != "exited" || value.eventExitCode.nonEmpty) &&
      ((value.callerStatus == "completed" && value.callerExitCode == 0) ||
        (value.callerStatus == "failed" && value.callerExitCode != 0)) && value.historicalAssociation == "host-attested" &&
      Set("available", "unavailable")(value.rawCapture) && value.assertionStatus == "unknown") Consequence.unit
      else N.invalid("Historical recovery requires an attested confirmed terminal and completed caller; raw capture remains unknown")
  } yield value
  def bindingC(target: Target, proof: Proof, participant: String, reference: ContextReference): Consequence[Json] = for {
    _ <- targetC(target.asJson.noSpaces)
    _ <- proofC(proof.asJson.noSpaces)
    _ <- if (participant == target.participant && reference != null && exact(reference.identity) && exact(reference.revision))
      Consequence.unit else N.invalid("Recovery participant or evidence reference differs")
  } yield Json.obj("componentInstance" -> target.componentInstance.asJson,
    "delegateTaskValidationRecovery" -> Json.obj("target" -> target.asJson, "proof" -> proof.asJson,
      "participant" -> participant.asJson, "reference" -> Json.obj("identity" -> reference.identity.asJson, "revision" -> reference.revision.asJson)))
  def selectBinding(value: Json): Either[String, Json] = for {
    raw <- value.hcursor.downField("delegateTaskValidationRecovery").get[Json]("target").left.map(_.message)
    target <- targetC(raw.noSpaces).toOption.toRight("Invalid recovery target")
    p <- value.hcursor.downField("delegateTaskValidationRecovery").get[Json]("proof").left.map(_.message)
    proof <- proofC(p.noSpaces).toOption.toRight("Invalid terminal proof")
    participant <- value.hcursor.downField("delegateTaskValidationRecovery").get[String]("participant").left.map(_.message)
    id <- value.hcursor.downField("delegateTaskValidationRecovery").downField("reference").get[String]("identity").left.map(_.message)
    rev <- value.hcursor.downField("delegateTaskValidationRecovery").downField("reference").get[String]("revision").left.map(_.message)
    selected <- bindingC(target, proof, participant, ContextReference(id, rev)).toOption.toRight("Invalid recovery binding")
    _ <- Either.cond(selected == value, (), "Recovery binding is not closed")
  } yield selected

  private[admission] def policyC(h: Host, core: ActionCall.Core): Consequence[Json] = for {
    plan <- core.executionContext.resolvedParameters.get("textus.subsystem.process-execution.plan") match {
      case Some(parameter) => parameter.value match {
        case ConfigurationValue.StringValue(text) => parseC(text)
        case _ => N.invalid("Resolved process policy is not a host plan")
      }
      case None => N.invalid("Resolved process policy is absent")
    }
    receipt <- h.configuration.mechanicalReceiptLimitsC
    artifact <- WorkflowArtifactBudget.limitsC(core)
    selectedOutput <- org.goldenport.cncf.processexecution.SbtTestOutputParameters.resolveC(core.executionContext.resolvedParameters)
  } yield Json.obj("processExecution" -> plan, "mechanicalReceiptBytes" -> receipt.maxBytes.asJson,
    "artifactBytes" -> artifact.maximumBytes.asJson, "repository" -> h.repository.asJson,
    "selectedStdoutCeiling" -> selectedOutput.stdoutBytes.asJson, "selectedStderrCeiling" -> selectedOutput.stderrBytes.asJson,
    "captureBoundProvenance" -> "selected-ceilings-upper-bound-admitted-test-capture".asJson,
    "selectedChecks" -> h.checks.asJson, "effectiveProcessLimits" -> Json.Null,
    "recoveryArtifactBytes" -> WorkflowSbtCapture.maximumArtifactBytes.asJson)
  private def discoverC(p: Payload, current: InstanceRecord, core: ActionCall.Core):
      Consequence[(WorkflowSbtCapture.ExternalReference, WorkflowSbtValidation.Facts, org.goldenport.cncf.processexecution.ProcessExecutionResult)] = for {
    reservation <- p.validationCaptureReservation.map(Consequence.success).getOrElse(N.invalid("No capture reservation is retained"))
    a = reservation.association
    check <- p.state.selected.lift(p.state.receipts.size).map(Consequence.success).getOrElse(N.invalid("No pending selected check"))
    _ <- if (reservation.workspace == "sm-workflow-validation-artifacts" && a.session == current.identity.value &&
      a.intentRevision == current.revision.value && a.participant == p.state.participant &&
      a.check == check.identity && a.ordinal == check.ordinal && a.root == p.root && a.branch == p.branch &&
      a.baseHead == p.baseHead && p.state.candidate.exists(_.tree == a.candidateTree) &&
      core.component.exists(_.core.instanceId.canonicalKey == a.component)) Consequence.unit
      else N.invalid("Capture reservation crosses the actual retained intent")
    limits <- org.goldenport.cncf.resource.ResourceReadLimits.createC(reservation.artifactBytes)
    observed <- WorkflowSbtCapture.discoverC(core, reservation.workspace, reservation.path, a, limits)
    _ <- if (observed._2.kind == WorkflowSbtValidation.Kind.Test && observed._2.selection.isEmpty &&
      observed._2.estimate.isEmpty && observed._2.mode.isEmpty && observed._2.batch.isEmpty && observed._2.reason.isEmpty)
      Consequence.unit else N.invalid("Retained artifact does not contain an ordinary full-Test result")
  } yield observed
  private def availabilityC(p: Payload, current: InstanceRecord, core: ActionCall.Core): Consequence[String] =
    p.validationCaptureReservation match {
      case None => Consequence.success("not-retained")
      case Some(r) => (for {
        _ <- if (r.workspace == "sm-workflow-validation-artifacts" && r.association.session == current.identity.value &&
          r.association.intentRevision == current.revision.value) Consequence.unit else N.invalid("Foreign capture reservation")
        workspace <- org.goldenport.cncf.workspace.WorkspaceId.parseC(r.workspace)
        path <- org.goldenport.cncf.workspace.WorkspaceFilePath.parseC(r.path)
        limits <- org.goldenport.cncf.resource.ResourceReadLimits.createC(r.artifactBytes)
        read <- WorkflowFileExecution.readBytesC(core, org.goldenport.cncf.workspace.FileTarget(workspace), path,
          org.goldenport.cncf.workspace.FileRepresentation.Identity, org.goldenport.cncf.workspace.FileReadPolicy(limits))
      } yield if (read.bytes.isEmpty) "absent" else if (discoverC(p, current, core).isSuccess) "available"
        else "unavailable-or-unreadable").recoverWith(_ => Consequence.success("unavailable-or-unreadable"))
    }
  private[admission] def observeC(h: Host, current: InstanceRecord, p: Payload, core: ActionCall.Core): Consequence[Target] = for {
    _ <- if (current.lifecycle == Lifecycle.Active && current.suspension.isEmpty && p.state.stage == "ValidationIntent" &&
      p.state.input.delegation.exists(_.role == "destination") &&
      p.state.input.repositoryIdentity == h.repository && p.root == h.root && p.branch == h.branch &&
      p.state.input.ownedPaths.forall(h.paths.contains) && p.state.candidate.nonEmpty &&
      p.state.receipts.size < p.state.selected.size && p.state.pendingCheck.contains(p.state.selected(p.state.receipts.size).identity) &&
      p.state.selected(p.state.receipts.size).kind == WorkflowSbtValidation.Kind.Test &&
      p.state.receipts.zip(p.state.selected).forall((r, c) => r.identity == c.identity &&
        RepositorySyncNativeMergeValidation.productSuccessful(r, c.kind))) Consequence.unit
      else N.invalid("Recovery requires an exact destination full-Test intent with successful applicable prefix")
    _ <- GoalPhaseParticipantAdmission.admitC(p.state.participant, core.executionContext)
    _ <- N.freshC(p, core)
    policy <- policyC(h, core)
    availability <- availabilityC(p, current, core)
  } yield Target(schema, core.component.get.core.instanceId.canonicalKey, current.identity.value, p.state.participant,
    current.revision.value, p.root, p.branch, p.baseHead, p.state.candidate.get,
    p.state.selected(p.state.receipts.size), p.state.receipts.map(_.identity), policy, availability)

  def operations(h: Host, definition: DefinitionBinding, evidence: EvidencePort): Vector[spec.OperationDefinition] =
    Vector(new Operation(false, h, definition, evidence), new Operation(true, h, definition, evidence))
  private final class Operation(recover: Boolean, h: Host, definition: DefinitionBinding, evidence: EvidencePort)
      extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (recover)
      SmWorkflowComponent.DelegateTaskServiceService.RecoverDelegateTaskValidationOperation
      else SmWorkflowComponent.DelegateTaskServiceService.DescribeDelegateTaskValidationRecoveryOperation)
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
      val request = incoming
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          input <- if (recover) inputForCallC(incoming, core).map(Some(_))
            else Consequence.success(Option.empty[V.DelegateTaskValidationRecoveryInput])
          session <- input.map(x => Consequence.success(x.sessionId)).getOrElse(
            descriptionForCallC(incoming, core).map(_.sessionId))
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
          selected = core.copy(executionContext = context)
          storage <- N.storageC(h, definition, selected)
          instances <- storage.bindInstancesC()(using context)
          current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
            _.filter(_.definition == definition).map(Consequence.success).getOrElse(N.invalid("Exact delegation Session is absent")))
          payload <- N.loadPayloadC(storage, session, current.revision.value, selected)
          target <- observeC(h, current, payload, selected)
          result <- input match {
            case None => Consequence.success(output(target, "decision-required"))
            case Some(i) => for {
              expected <- targetC(i.target)
              proof <- proofC(i.proof)
              _ <- if (target == expected && i.sessionId == target.sessionId) Consequence.unit
                else N.invalid("Recovery target is stale or foreign")
              reference = ContextReference(i.reference.identity, i.reference.revision)
              binding <- bindingC(target, proof, i.participant, reference)
              _ <- evidence.admitC(selected, reference, binding)
              response <- if (target.captureAvailability == "available" && proof.rawCapture == "available") for {
                observed <- discoverC(payload, current, selected)
                _ <- if (WorkflowSbtCapture.termination(observed._3) == proof.termination &&
                  (proof.termination != "exited" || WorkflowSbtCapture.exitCode(observed._3) == proof.eventExitCode))
                  Consequence.unit else N.invalid("Host terminal proof contradicts the genuine retained process termination")
                receipt <- RepositorySyncNativeMergeValidation.compactExternalC(target.check.identity, observed._2, observed._3, observed._1)
                state <- S.observedC(payload.state, receipt)
                proposed = payload.copy(state = state, validationRecovery = None, validationCaptureReservation = None)
                _ <- N.collectedCapacityC(h, current, proposed, target.check)
                references <- N.recordCheckEvidenceC(h, selected, current.identity.value, current.revision.value, receipt)
                next = proposed.copy(state = state.copy(evidenceReferences = state.evidenceReferences ++ references))
                _ <- N.saveC(h, storage, current, next, selected)
              } yield output(target, "actual-result-collected")
              else if (Set("not-retained", "absent")(target.captureAvailability) && proof.rawCapture == "unavailable" &&
                proof.termination != "exited") for {
                direction <- Consequence.success(Direction(target.frontierRevision, reference.identity, reference.revision, target))
                next <- N.planRecoveryC(h, current, payload, direction, selected)
                _ <- N.saveC(h, storage, current, next, selected)
              } yield output(target, "authorized-awaiting-advance")
              else N.invalid("Capture availability is changed, unreadable, or contradicts the admitted proof")
            } yield response
          }
        } yield result
      }
    })
  }
  private def output(target: Target, outcome: String): OperationResponse = NativeWorkflowOperationOutput.response(
    V.DelegateTaskValidationRecoveryView(target.sessionId, outcome,
      "The original intent remains in native history; this operation ran no check. Capture availability is reported in the target.", target.asJson.noSpaces))
}
