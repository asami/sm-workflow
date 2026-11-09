package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.config.{ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceFilePath, WorkspaceId}
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.value as V

/** Host configuration and component-managed facts for the one publication path.
  * There is no shell, external AI execution runner or caller-selected argv here.
  * @since   Oct. 26, 2026
  *  version Oct. 28, 2026
  * @version Nov.  9, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object LocalPublicationRuntime {
  final case class CatalogOutput(targetIdentity: String, path: String, selection: String)
  final case class Policy(storeName: String, collection: DataStore.CollectionId,
    targets: Vector[LocalPublication.Target], catalogOutputs: Vector[CatalogOutput] = Vector.empty)
  private final case class Wire(storeName: String, collection: String, targets: Vector[LocalPublication.Target],
    catalogOutputs: Vector[CatalogOutput])
  val declaration = ComponentParameterKey.optional("sm-workflow.local-publication",
    ComponentParameterDecoder[Policy] { value =>
      def _json_(v: ConfigurationValue): Json = v match {
        case ConfigurationValue.StringValue(s) => Json.fromString(s)
        case ConfigurationValue.NumberValue(n) => Json.fromBigDecimal(n)
        case ConfigurationValue.BooleanValue(b) => Json.fromBoolean(b)
        case ConfigurationValue.ListValue(xs) => Json.fromValues(xs.map(_json_))
        case ConfigurationValue.ObjectValue(xs) => Json.obj(xs.toVector.map((k, v) => k -> _json_(v))*)
        case ConfigurationValue.NullValue => Json.Null
      }
      val parsed = value match {
        case ConfigurationValue.StringValue(s) => io.circe.parser.parse(s).left.map(_.message)
        case v: ConfigurationValue.ObjectValue => Right(_json_(v))
        case _ => Left("Publication configuration requires a structured object")
      }
      parsed.flatMap { raw =>
        if (!raw.asObject.exists(_.keys.forall(Set("storeName", "collection", "targets", "catalogOutputs"))))
          Left("Publication configuration contains an undeclared field")
        else {
          val normalized = if (raw.hcursor.downField("catalogOutputs").focus.isEmpty)
            raw.mapObject(_.add("catalogOutputs", Json.arr())) else raw
          normalized.hcursor.get[Vector[Json]]("catalogOutputs").left.map(_.message).flatMap { outputs =>
            if (outputs.exists(o => !o.asObject.exists(_.keys.toSet == Set("targetIdentity", "path", "selection"))))
              Left("Publication catalog output contains missing or undeclared fields")
            else normalized.hcursor.get[Vector[Json]]("targets").left.map(_.message).flatMap { targets =>
          if (targets.exists(t => !t.asObject.exists(_.keys.forall(Set("identity", "component", "workspace", "dependency", "beforeChecks", "beforeSync")))))
            Left("Publication target contains an undeclared field")
          else normalized.mapObject(_.add("targets", Json.fromValues(targets.map(t =>
            if (t.hcursor.downField("beforeChecks").focus.isEmpty) t.mapObject(_.add("beforeChecks", Json.False)) else t))))
            .as[Wire].left.map(_.message)
            }
          }
        }
      }.fold(Consequence.configurationInvalid(_), w => {
        EntityCollectionId.parse(w.collection).toOption.map(id => Policy(w.storeName, DataStore.CollectionId.EntityStore(id), w.targets, w.catalogOutputs))
          .map(p => validateC(p).map(_ => p)).getOrElse(Consequence.configurationInvalid("Publication collection is invalid"))
      })
    })

  def validateC(policy: Policy): Consequence[Unit] =
    if (policy != null && policy.storeName != null && policy.storeName.nonEmpty && policy.collection != null &&
        policy.targets != null && policy.targets.nonEmpty && policy.targets.forall(t => t != null &&
          t.identity != null && t.identity.nonEmpty && t.component != null && t.component.nonEmpty &&
          t.workspace != null && org.goldenport.cncf.workspace.WorkspaceId.parseC(t.workspace).isSuccess &&
          t.dependency != null) && policy.targets.map(_.identity).distinct.size == policy.targets.size &&
        policy.catalogOutputs != null && policy.catalogOutputs.size <= 16 && policy.catalogOutputs.forall(_ != null) &&
        policy.catalogOutputs.map(_.targetIdentity).distinct.size == policy.catalogOutputs.size &&
        policy.catalogOutputs.forall(o => o != null && o.targetIdentity != null &&
          policy.targets.exists(_.identity == o.targetIdentity) && o.path != null &&
          o.path.startsWith("src/main/catalog/car/") && o.path.endsWith(".yaml") &&
          GitPath.parseC(o.path).isSuccess && WorkspaceFilePath.parseC(o.path).isSuccess &&
          o.selection != null && GitSelectionId.parseC(o.selection).isSuccess))
      Consequence.unit else Consequence.configurationInvalid("Local publication host targets are incomplete or duplicate")

  private def _catalog_output_c(core: ActionCall.Core, target: LocalPublication.Target,
    output: CatalogOutput, indextree: String): Consequence[Boolean] = for {
    tree <- GitObjectId.parseC(indextree)
    selection <- GitSelectionId.parseC(output.selection)
    entries <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.TreeEntries(tree, selection), target.workspace).flatMap(_.valueC)
    _ <- if (entries.isEmpty || (entries.size == 1 && entries.head.path.value == output.path)) Consequence.unit
      else Consequence.stateConflict("Publication catalog selection returned crossed or extra paths")
    projected <- entries.headOption.flatMap(_.entry).filter(e => Set("100644", "100755")(e.mode)) match {
      case None => Consequence.success(false)
      case Some(entry) => for {
        blob <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.BlobContent(entry.blob), target.workspace).flatMap(_.valueC)
        workspace <- WorkspaceId.parseC(target.workspace)
        path <- WorkspaceFilePath.parseC(output.path)
        actual <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), path)
        _ <- if (actual != null && actual.target == FileTarget(workspace) && actual.path == path) Consequence.unit
          else Consequence.stateConflict("Publication catalog File result differs from its target or path")
      } yield blob.text.exists(indexed => indexed.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector == blob.bytes &&
        !indexed.contains('\u0000') && actual.text.exists(working => !working.contains('\u0000') &&
          LocalPublication.catalogTimestampOnly(target.component, indexed, working)))
    }
  } yield projected

  private[admission] def _port(core: ActionCall.Core, policy: Policy): LocalPublication.Port = new LocalPublication.Port {
    private def _bound(key: String) = ComponentRecordStorage.bindForCallC(core, policy.storeName,
      policy.collection, DataStore.StringEntryId(key))
    def loadC(key: String): Consequence[Option[Json]] = _bound(key).flatMap(_.loadC()).flatMap {
      case None => Consequence.success(None)
      case Some(row) => row.getString("localPublicationPayload").map(raw => io.circe.parser.parse(raw)
        .fold(e => Consequence.stateConflict(e.message), j => Consequence.success(Some(j))))
        .getOrElse(Consequence.stateConflict("Retained local publication has no typed payload"))
    }
    def createC(key: String, value: Json): Consequence[Unit] =
      _bound(key).flatMap(_.createC(Record.data("localPublicationPayload" -> value.noSpaces)))
    def newIdentity(): String = core.executionContext.idGeneration.opaqueId("local-publication-continuation")
    def sourceC(target: LocalPublication.Target): Consequence[LocalPublication.Source] = for {
      observed <- RepositorySyncManagedGitObservation.observeInWorkspaceC(core, target.workspace)
      tree <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.IndexTree, target.workspace).flatMap(_.valueC)
      dirty <- observed.dirty.foldLeft(Consequence.success(Vector.empty[LocalPublication.Change])) { (prior, change) => for {
        saved <- prior
        output = policy.catalogOutputs.find(o => o.targetIdentity == target.identity && o.path == change.path)
        projected <- output.filter(_ => change.index == ' ' && change.worktree == 'M' && change.renamedFrom.isEmpty)
          .fold(Consequence.success(false))(o => _catalog_output_c(core, target, o, tree.value))
      } yield if (projected) saved else saved :+ LocalPublication.Change(change.path, change.index.toString,
        change.worktree.toString, change.renamedFrom) }
      after <- RepositorySyncManagedGitObservation.observeInWorkspaceC(core, target.workspace)
      _ <- if (observed == after) Consequence.unit else Consequence.stateConflict("Publication source changed during observation")
    } yield LocalPublication.Source(observed.head, tree.value, dirty)
    def executeC(target: LocalPublication.Target): Consequence[SbtExecutionAttempt] =
      WorkflowManagedProcessExecution.publishLocalAttemptC(core, target.workspace)
  }

  def ensureC(core: ActionCall.Core, policy: Policy, input: LocalPublication.Input): Consequence[LocalPublication.Result] = for {
    _ <- validateC(policy)
    target <- policy.targets.find(_.identity == input.targetIdentity).map(Consequence.success)
      .getOrElse(Consequence.configurationInvalid("Publication target is not declared by this host"))
    participant = SecuritySubject.from(core.executionContext.security).subjectId
    result <- LocalPublication.ensureC(input, target, participant, _port(core, policy))
  } yield result

  def ensureDeliveryC(core: ActionCall.Core, policy: Policy, input: LocalPublication.Input): Consequence[LocalPublication.Result] = for {
    _ <- validateC(policy)
    target <- policy.targets.find(_.identity == input.targetIdentity).map(Consequence.success)
      .getOrElse(Consequence.configurationInvalid("Publication target is not declared by this host"))
    participant = SecuritySubject.from(core.executionContext.security).subjectId
    result <- LocalPublication.ensureDeliveryC(input, target, participant, _port(core, policy))
  } yield result

  def collectC(core: ActionCall.Core, policy: Policy, input: LocalPublication.Input): Consequence[Option[LocalPublication.Result]] = for {
    _ <- validateC(policy)
    target <- policy.targets.find(_.identity == input.targetIdentity).map(Consequence.success)
      .getOrElse(Consequence.configurationInvalid("Publication target is not declared by this host"))
    participant = SecuritySubject.from(core.executionContext.security).subjectId
    result <- LocalPublication.collectC(input, target, participant, _port(core, policy))
  } yield result

  def collectLatestC(core: ActionCall.Core, policy: Policy,
    input: LocalPublication.LatestCollectionInput): Consequence[Option[LocalPublication.Result]] = for {
    _ <- validateC(policy)
    target <- policy.targets.find(_.identity == input.targetIdentity).map(Consequence.success)
      .getOrElse(Consequence.configurationInvalid("Publication target is not declared by this host"))
    participant = SecuritySubject.from(core.executionContext.security).subjectId
    result <- LocalPublication.collectLatestC(input, target, participant, _port(core, policy))
  } yield result

  private def _prerequisite_c(core: ActionCall.Core, policy: Policy,
    input: LocalPublication.Input): Consequence[LocalPublication.Result] = for {
    _ <- validateC(policy)
    target <- policy.targets.find(_.identity == input.targetIdentity).map(Consequence.success)
      .getOrElse(Consequence.configurationInvalid("Publication target is not declared by this host"))
    participant = SecuritySubject.from(core.executionContext.security).subjectId
    result <- LocalPublication.ensurePrerequisiteC(input, target, participant, _port(core, policy))
  } yield result

  def beforeChecksC(session: GoalPhaseNativeRun.Session, d: GoalPhaseNativeRun.Declaration,
    core: ActionCall.Core, candidate: GoalPhaseNativeCandidate.Saved,
    qualifiedSuccessor: Option[LocalPublication.QualifiedSuccessor] = None,
    prerequisiteWait: Option[LocalPublication.Result] = None,
    existingArtifactAdoption: Option[GoalPhasePrerequisitePublicationUnknownRecovery.ExistingArtifactAdoption] = None,
    confirmedFailureSuccessor: Option[LocalPublication.ConfirmedFailureSuccessor] = None,
    primaryRepairPublication: Option[LocalPublication.ConfirmedFailureSuccessor] = None,
    hostRecovery: Option[GoalPhasePrerequisitePublicationHostRecovery.Permit] = None,
    sourceAlignment: Option[GoalPhaseDependencyPublicationSourceAlignment.Permit] = None):
      Consequence[Option[V.GoalPhaseAdvanceResult]] =
    d.localPublication match {
      case None if existingArtifactAdoption.nonEmpty || primaryRepairPublication.nonEmpty || hostRecovery.nonEmpty || sourceAlignment.nonEmpty =>
        Consequence.stateConflict("Artifact adoption or primary repair publication requires its publication policy")
      case None => Consequence.success(None)
      case Some(policy) =>
        val dependency = candidate.payload.dependencyAdmission.map(_.binding.identity)
          .orElse(candidate.payload.dependencySourceCapture.map(_.direction.target.binding.identity))
        val targets = policy.targets.filter(t => t.beforeChecks && t.dependency.forall(dependency.contains))
        for {
        _ <- validateC(policy)
        mismatches <- GoalPhaseDependencyPublicationSourceAlignment.mismatchesC(candidate.payload, targets,
          GoalPhaseDependencyPublicationSourceAlignment.rootC(core, _))
        _ <- sourceAlignment.fold(Consequence.unit)(permit => GoalPhaseDependencyPublicationSourceAlignment.currentC(
          permit, candidate, session.current.identity.value, session.participantIdentity, d, core))
        blocked = mismatches.filterNot(m => sourceAlignment.exists(_.skips(m.target)))
        sourcewait = Option.when(blocked.nonEmpty)(NativeWorkflowOperationOutput.goalWait(session.current.identity.value,
          GoalPhaseDependencyPublicationSourceAlignment.message + " Binding " +
            candidate.payload.dependencySourceCapture.map(c => GoalPhaseDependencySourceRevision.bindingOf(c).identity).getOrElse("") +
            " selects " + candidate.payload.dependencySourceCapture.map(c => GoalPhaseDependencySourceRevision.bindingOf(c).root).getOrElse("") +
            "; configured targets: " + blocked.map(m => m.target.identity + "@" + m.target.workspace + "=" + m.actualRoot).mkString(", "),
          "dependency-publication-source-mismatch"))
        _ <- hostRecovery.fold(Consequence.unit) { permit => for {
          _ <- if (qualifiedSuccessor.isEmpty && prerequisiteWait.isEmpty && existingArtifactAdoption.isEmpty &&
              confirmedFailureSuccessor.isEmpty && primaryRepairPublication.isEmpty &&
              candidate.payload == permit.target.candidate && candidate.record.revision.value.toString == permit.target.candidateRecordRevision &&
              session.current.identity.value == permit.target.sessionId && session.participantIdentity == permit.target.participant &&
              targets == permit.target.targets) Consequence.unit
            else Consequence.stateConflict("Host recovery permit crosses its candidate or another publication permit")
          _ <- GoalPhasePrerequisitePublicationHostRecovery.currentC(permit.target, d, core)
          _ <- GoalPhasePrerequisitePublicationHostRecovery.latestC(permit, _port(core, policy))
        } yield () }
        _ <- if (primaryRepairPublication.forall(p => confirmedFailureSuccessor.isEmpty && qualifiedSuccessor.isEmpty &&
            candidate.payload.primaryPublicationRepair.nonEmpty && targets.filter(_.dependency.isEmpty) == Vector(p.previous.request.target) &&
            p.sourceCapture == org.goldenport.cncf.workflow.ContextReference(session.current.identity.value,
              candidate.record.revision.value.toString)) &&
            existingArtifactAdoption.forall(a => targets.contains(a.previous.request.target)) &&
            !(existingArtifactAdoption.nonEmpty && qualifiedSuccessor.nonEmpty) &&
            confirmedFailureSuccessor.forall(p => existingArtifactAdoption.exists(a => targets == Vector(a.previous.request.target,
              p.previous.request.target)) && candidate.payload.dependencySourceCapture.exists(c =>
                c.schemaVersion == GoalPhaseDependencySourceRevision.primaryCaptureSchema &&
                c.direction.target.primaryFailureOrigin.exists(_.failed.request.target == p.previous.request.target)) && qualifiedSuccessor.isEmpty)) Consequence.unit
          else Consequence.stateConflict("Artifact adoption crosses its exact prerequisite target or publication choice")
        _ <- if (sourcewait.nonEmpty || sourceAlignment.isEmpty) Consequence.unit else GoalPhaseDependencyPublicationSourceAlignment.canonicalCapture(candidate.payload)
          .fold(Consequence.unit) { capture =>
            GoalPhasePrerequisitePublicationRecovery.processPolicyC(d, core).flatMap { process =>
              if (targets.filter(_.dependency.isEmpty).forall(target => GoalPhasePrerequisitePublicationRecovery.primaryProgramMatches(
                  process, target, GoalPhaseDependencySourceRevision.consumptionOf(capture)))) Consequence.unit
              else Consequence.stateConflict("Fixed primary PublishLocal environment does not select the captured canonical dependency sourceRoot")
            }
          }
        result <- targets.foldLeft(Consequence.success(sourcewait)) { (prior, target) => prior.flatMap {
          case some @ Some(_) => Consequence.success(some)
          case None if sourceAlignment.exists(_.skips(target)) => Consequence.success(Option.empty[V.GoalPhaseAdvanceResult])
          case None =>
            hostRecovery match {
            case Some(permit) if target.dependency.nonEmpty =>
              // The complete prefix was collected above. This branch has no ensure/execute call.
              Consequence.success(Option.empty[V.GoalPhaseAdvanceResult])
            case Some(permit) => for {
              _ <- GoalPhasePrerequisitePublicationHostRecovery.currentC(permit.target, d, core)
              _ <- GoalPhasePrerequisitePublicationHostRecovery.latestC(permit, _port(core, policy))
              result <- LocalPublication.ensureConfirmedFailureSuccessorC(GoalPhasePrerequisitePublicationHostRecovery.input(permit),
                target, session.participantIdentity, permit.successor, _port(core, policy))
            } yield Option.unless(result.ready)(NativeWorkflowOperationOutput.goalWait(session.current.identity.value,
              "Runtime-owned local publication: " + result.reason, "local-publication").copy(
                outcome = Some(result.status), localPublication = Some(GoalPhaseNativeMetadata.record(result.asJson))))
            case None => existingArtifactAdoption.filter(_.previous.request.target == target) match {
            case Some(adoption) =>
              val binding = GoalPhasePrerequisitePublicationUnknownRecovery.bindingOf(candidate.payload)
              for {
                _ <- if (target.dependency.contains(binding.identity) && target.workspace == binding.dependency.workspace)
                  Consequence.unit else Consequence.stateConflict("Artifact adoption differs from the captured dependency")
                _ <- adoptExistingArtifactsC(adoption, target, session.current.identity.value, session.participantIdentity,
                  GoalPhaseDependencySourceRevision.observeC(core, binding, adoption.guard.excludedFiles.map(_.path)),
                  _port(core, policy))
              } yield Option.empty[V.GoalPhaseAdvanceResult]
            case None =>
            val input = LocalPublication.Input(session.current.identity.value + ":publication:" +
              candidate.payload.sourceRevision + ":" + target.identity, target.identity,
              Some(session.current.identity.value))
            val publication = primaryRepairPublication.filter(_.previous.request.target == target) match {
              case Some(permit) => for {
                process <- GoalPhasePrerequisitePublicationRecovery.processPolicyC(d, core)
                _ <- if (process == permit.processPolicy && GoalPhasePrerequisitePublicationRecovery.publicationPolicy(policy) == permit.publicationPolicy &&
                    candidate.payload.dependencySourceCapture.forall(c => GoalPhasePrerequisitePublicationRecovery.primaryProgramMatches(
                      process, target, GoalPhaseDependencySourceRevision.consumptionOf(c)))) Consequence.unit
                  else Consequence.stateConflict("Primary repair publication changed its fixed program, destination or source consumption")
                result <- LocalPublication.ensureConfirmedFailureSuccessorC(primaryRepairInput(session.current.identity.value,
                  candidate.payload, target, permit), target, session.participantIdentity, permit, _port(core, policy))
              } yield result
              case None => confirmedFailureSuccessor.filter(_.previous.request.target == target) match {
              case Some(permit) => for {
                capture <- candidate.payload.dependencySourceCapture.map(Consequence.success).getOrElse(
                  Consequence.stateConflict("Primary permit has no exact dependency source capture"))
                process <- GoalPhasePrerequisitePublicationRecovery.processPolicyC(d, core)
                _ <- if (process == permit.processPolicy && GoalPhasePrerequisitePublicationRecovery.publicationPolicy(policy) == permit.publicationPolicy &&
                    GoalPhasePrerequisitePublicationRecovery.primaryProgramMatches(process, target,
                    GoalPhaseDependencySourceRevision.consumptionOf(capture))) Consequence.unit
                  else Consequence.stateConflict("Fixed primary PublishLocal environment does not select the captured dependency sourceRoot")
                result <- LocalPublication.ensureConfirmedFailureSuccessorC(confirmedFailureInput(session.current.identity.value,
                  candidate.payload, target, permit), target, session.participantIdentity, permit, _port(core, policy))
              } yield result
              case None => qualifiedSuccessor.filter(_.previous.target == target) match {
              case Some(permit) => LocalPublication.ensureQualifiedSuccessorC(input.copy(requestIdentity =
                input.requestIdentity + ":qualified:" + permit.direction.identity + ":" + permit.direction.revision), target,
                session.participantIdentity, permit, _port(core, policy))
              case None if prerequisiteWait.exists(_.request.target == target) =>
                retainedFailureC(core, policy, prerequisiteWait.get).map(_ => prerequisiteWait.get)
              case None => _prerequisite_c(core, policy, input)
              }
            }
            }
            publication.map { result =>
              Option.unless(result.ready)(NativeWorkflowOperationOutput.goalWait(session.current.identity.value,
                "Runtime-owned local publication: " + result.reason, "local-publication").copy(
                outcome = Some(result.status), localPublication = Some(GoalPhaseNativeMetadata.record(result.asJson))))
            }
            }
            }
        }}
        } yield result
    }

  /** Read-only gate shared with bounded specifications. An admitted Human
    * direction skips this prerequisite only; no publication fact is created.
    */
  private[admission] def adoptExistingArtifactsC(
    adoption: GoalPhasePrerequisitePublicationUnknownRecovery.ExistingArtifactAdoption,
    target: LocalPublication.Target, ownerSessionId: String, participant: String,
    currentGuard: => Consequence[GoalPhaseDependencySourceRevision.Guard], port: LocalPublication.Port): Consequence[Unit] = for {
    _ <- if (adoption != null && GoalPhaseDependencySourceRevision.referenceValid(adoption.direction) &&
        adoption.previous != null && adoption.previous.status == "uncertain" && adoption.previous.nextPreparation.isEmpty &&
        adoption.previous.request != null && adoption.previous.request.target == target && target.beforeChecks &&
        adoption.previous.request.input != null && adoption.previous.request.input.ownerSessionId.contains(ownerSessionId) &&
        adoption.previous.request.input.targetIdentity == target.identity && !adoption.previous.request.input.explicit &&
        adoption.previous.request.input.answer.isEmpty && adoption.previous.request.participant == participant &&
        GoalPhaseDependencySourceRevision.guardValid(adoption.guard) && adoption.source == LocalPublication.Source(
          adoption.guard.head, adoption.guard.indexTree, adoption.guard.dirty.map(d =>
            LocalPublication.Change(d.path, d.index, d.worktree, d.renamedFrom)))) Consequence.unit
      else Consequence.stateConflict("Artifact adoption crosses its UNKNOWN request, owner, participant, target or source")
    retained <- LocalPublication.collectC(adoption.previous.request.input, target, participant, port)
    latest <- LocalPublication.collectLatestC(LocalPublication.LatestCollectionInput(target.identity, ownerSessionId), target, participant, port)
    _ <- if (retained.contains(adoption.previous) && latest.contains(adoption.previous)) Consequence.unit
      else Consequence.stateConflict("Artifact adoption no longer selects the exact latest retained UNKNOWN")
    guard <- currentGuard
    source <- port.sourceC(target)
    _ <- if (guard == adoption.guard && source == adoption.source) Consequence.unit
      else Consequence.stateConflict("Artifact adoption source changed; a fresh exact Human target is required")
  } yield ()

  private[admission] def prerequisiteInput(session: String, candidate: GoalPhaseNativeCandidate.Payload,
    target: LocalPublication.Target): LocalPublication.Input = LocalPublication.Input(
      session + ":publication:" + candidate.sourceRevision + ":" + target.identity, target.identity, Some(session))

  private[admission] def confirmedFailureInput(session: String, candidate: GoalPhaseNativeCandidate.Payload,
    target: LocalPublication.Target, permit: LocalPublication.ConfirmedFailureSuccessor): LocalPublication.Input =
    prerequisiteInput(session, candidate, target).copy(requestIdentity = prerequisiteInput(session, candidate, target).requestIdentity +
      ":primary:" + permit.direction.identity + ":" + permit.direction.revision)

  private[admission] def primaryRepairInput(session: String, candidate: GoalPhaseNativeCandidate.Payload,
    target: LocalPublication.Target, permit: LocalPublication.ConfirmedFailureSuccessor): LocalPublication.Input =
    prerequisiteInput(session, candidate, target).copy(requestIdentity = prerequisiteInput(session, candidate, target).requestIdentity +
      ":repair:" + permit.direction.identity + ":" + permit.direction.revision)

  private[admission] def collectPrimaryRepairC(session: String, participant: String,
    candidate: GoalPhaseNativeCandidate.Payload, permit: LocalPublication.ConfirmedFailureSuccessor,
    core: ActionCall.Core, policy: Policy): Consequence[Option[LocalPublication.Result]] =
    LocalPublication.collectConfirmedFailureSuccessorC(primaryRepairInput(session, candidate, permit.previous.request.target, permit),
      permit.previous.request.target, participant, permit, _port(core, policy))

  /** Only the exact original prerequisite or its qualified primary successor is
    * admitted. Collection validates retained facts without preparing effects. */
  private[admission] def primaryPrerequisiteC(session: String, d: GoalPhaseNativeRun.Declaration,
    core: ActionCall.Core, original: GoalPhaseNativeCandidate.Payload, current: GoalPhaseNativeCandidate.Payload, target: LocalPublication.Target,
    permit: Option[LocalPublication.ConfirmedFailureSuccessor]): Consequence[LocalPublication.Result] = for {
    policy <- d.localPublication.map(Consequence.success).getOrElse(Consequence.stateConflict("Primary publication policy is absent"))
    result <- collectPrimaryPrerequisiteC(session, SecuritySubject.from(core.executionContext.security).subjectId,
      original, current, target, permit, _port(core, policy))
  } yield result

  private[admission] def collectPrimaryPrerequisiteC(session: String, participant: String,
    original: GoalPhaseNativeCandidate.Payload, current: GoalPhaseNativeCandidate.Payload,
    target: LocalPublication.Target, permit: Option[LocalPublication.ConfirmedFailureSuccessor],
    port: LocalPublication.Port): Consequence[LocalPublication.Result] = for {
    _ <- if (target.dependency.isEmpty && target.beforeChecks) Consequence.unit
      else Consequence.stateConflict("Primary continuation requires its exact primary prerequisite target")
    originalresult <- LocalPublication.collectPrerequisiteC(prerequisiteInput(session, original, target), target, participant, port)
    failed <- originalresult.filter(GoalPhasePrerequisitePublicationRecovery.confirmedFailure).map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Primary continuation has no canonical original terminated failure"))
    qualified <- permit match {
      case None => Consequence.success(Option.empty[LocalPublication.Result])
      case Some(value) => LocalPublication.collectConfirmedFailureSuccessorC(
        confirmedFailureInput(session, current, target, value), target, participant, value, port)
    }
    latest <- LocalPublication.collectLatestC(LocalPublication.LatestCollectionInput(target.identity, session), target, participant, port)
    result = qualified.getOrElse(permit.map(_.previous).getOrElse(failed))
    _ <- if (latest.contains(result)) Consequence.unit
      else Consequence.stateConflict("Latest primary publication belongs to another attempt or causal chain")
  } yield result

  /** A tightly bounded historical primary failure permits reading the old
    * artifact Direction after a host selects the corrected source program.
    * It grants no execution and does not admit a new legacy UNKNOWN answer. */
  private[admission] def primaryHistoryC(core: ActionCall.Core, policy: Policy,
    target: GoalPhasePrerequisitePublicationUnknownRecovery.Target): Consequence[Unit] = for {
    _ <- validateC(policy)
    applicable = policy.targets.filter(t => t.beforeChecks && t.dependency.forall(Some(target.admission.binding.identity).contains))
    primary <- applicable match {
      case Vector(dependency, value) if dependency == target.unknown.request.target && value.dependency.isEmpty &&
          value.workspace == target.admission.binding.requester.workspace => Consequence.success(value)
      case _ => Consequence.stateConflict("Historical primary continuation requires exactly its original ordered two targets")
    }
    failed <- LocalPublication.collectPrerequisiteC(prerequisiteInput(target.sessionId, target.candidate, primary), primary,
      target.participant, _port(core, policy))
    _ <- if (failed.exists(GoalPhasePrerequisitePublicationRecovery.confirmedFailure)) Consequence.unit
      else Consequence.stateConflict("Historical primary continuation has no genuine original terminated failure")
  } yield ()

  /** The completed v1 correction's canonical primary request remains historical
    * even when a later successor now owns the target-wide latest result. */
  private[admission] def correctedPrimaryHistoryC(core: ActionCall.Core, policy: Policy,
    target: GoalPhasePrerequisitePublicationRecovery.Target, corrected: GoalPhaseNativeCandidate.Payload): Consequence[Unit] =
    collectCorrectedPrimaryHistoryC(policy, target, corrected, _port(core, policy))

  private[admission] def collectCorrectedPrimaryHistoryC(policy: Policy,
    target: GoalPhasePrerequisitePublicationRecovery.Target, corrected: GoalPhaseNativeCandidate.Payload,
    port: LocalPublication.Port): Consequence[Unit] = for {
    _ <- validateC(policy)
    _ <- if (GoalPhasePrerequisitePublicationRecovery.targetValid(target) &&
        target.publicationPolicy == GoalPhasePrerequisitePublicationRecovery.publicationPolicy(policy) && corrected != null &&
        corrected.dependency.exists(c => c.session == target.sessionId && c.admission == target.anchoredAdmission &&
          c.plan == org.goldenport.cncf.workflow.ContextReference(target.plan.identity, target.plan.revision) &&
          c.step == target.candidate.step && c.generation == target.generation && c.primaryBase == target.base &&
          c.sourceRevision == corrected.sourceRevision) &&
        corrected.copy(sourceRevision = target.candidate.sourceRevision, dependencyAdmission = target.candidate.dependencyAdmission,
          dependency = target.candidate.dependency, dependencyOrigin = target.candidate.dependencyOrigin) == target.candidate)
      Consequence.unit else Consequence.stateConflict("Historical corrected primary crosses its original candidate, capture or publication policy")
    applicable = policy.targets.filter(t => t.beforeChecks && t.dependency.forall(Some(target.admission.binding.identity).contains))
    primary <- applicable match {
      case Vector(dependency, value) if dependency == target.publicationTarget && dependency == target.failed.request.target &&
          value.dependency.isEmpty && value.workspace == target.admission.binding.requester.workspace => Consequence.success(value)
      case _ => Consequence.stateConflict("Historical corrected primary requires its original ordered dependency and primary targets")
    }
    failed <- LocalPublication.collectPrerequisiteC(prerequisiteInput(target.sessionId, corrected, primary), primary,
      target.participant, port)
    _ <- if (failed.exists(GoalPhasePrerequisitePublicationRecovery.confirmedFailure)) Consequence.unit
      else Consequence.stateConflict("Historical corrected primary has no canonical original confirmed failure")
  } yield ()

  /** Collect only; description never prepares or starts a publication. */
  private[admission] def failedPrerequisiteC(session: String, d: GoalPhaseNativeRun.Declaration,
    core: ActionCall.Core, candidate: GoalPhaseNativeCandidate.Payload): Consequence[(Policy, LocalPublication.Result)] = for {
    policy <- d.localPublication.map(Consequence.success).getOrElse(
      Consequence.stateConflict("Prerequisite recovery requires a declared local publication policy"))
    _ <- validateC(policy)
    binding <- candidate.dependencyAdmission.map(_.binding).map(Consequence.success).getOrElse(
      Consequence.stateConflict("Prerequisite recovery requires its captured dependency binding"))
    targets = policy.targets.filter(t => t.beforeChecks && t.dependency.contains(binding.identity) &&
      t.workspace == binding.dependency.workspace)
    target <- targets match {
      case Vector(value) => Consequence.success(value)
      case _ => Consequence.stateConflict("Prerequisite recovery requires one exact declared dependency publication target")
    }
    participant = SecuritySubject.from(core.executionContext.security).subjectId
    failed <- LocalPublication.failedPrerequisiteC(prerequisiteInput(session, candidate, target), target,
      participant, _port(core, policy))
  } yield policy -> failed

  /** Historical reconstruction collects the original immutable request only.
    * Its successor may now be the latest target attempt. */
  private[admission] def retainedFailureC(core: ActionCall.Core, policy: Policy,
    failed: LocalPublication.Result): Consequence[Unit] = for {
    _ <- validateC(policy)
    _ <- if (policy.targets.contains(failed.request.target)) Consequence.unit
      else Consequence.stateConflict("Retained prerequisite publication target changed")
    participant = SecuritySubject.from(core.executionContext.security).subjectId
    actual <- LocalPublication.collectC(failed.request.input, failed.request.target, participant, _port(core, policy))
    _ <- if (actual.contains(failed)) Consequence.unit
      else Consequence.stateConflict("Retained prerequisite failure differs from its actual immutable publication")
  } yield ()
}
