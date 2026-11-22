package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.security.MessageDigest
import scala.util.control.NonFatal
import cats.free.Free
import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.config.{ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workspace.*
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import ManagedDevelopment.*

/** Existing CNCF resources, File, ComponentRecordStorage and scoped SBT UoW.
  * Physical roots come only from the resolved CNCF workspace graph. All actual
  * acquisition and effects still cross CNCF admission, including owner grants.
  * @since   Nov. 12, 2026
  * @version Nov. 23, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object ManagedDevelopmentRuntime {
  final case class Policy(storeName: String, collection: String, definition: Definition,
    repositorySyncEdge: Option[String] = None, dependencyWorkEdge: Option[String] = None, delivery: Option[Vector[DependencyDevelopmentDelivery.Binding]] = None,
    repositorySyncInputs: Option[Vector[Input]] = None)
  final case class MachinePrograms(publication: String, consumer: String, discovery: SbtValidationDiscovery.Binding, jarWorkspace: String)
  object MachinePrograms {
    private given io.circe.Encoder[SbtValidationDiscovery.Binding] = io.circe.generic.semiauto.deriveEncoder
    private given io.circe.Decoder[SbtValidationDiscovery.Binding] = io.circe.generic.semiauto.deriveDecoder
    given io.circe.Encoder[MachinePrograms] = io.circe.generic.semiauto.deriveEncoder
    given io.circe.Decoder[MachinePrograms] = io.circe.generic.semiauto.deriveDecoder
  }
  val declaration = ComponentParameterKey.optional("sm-workflow.managed-development",
    ComponentParameterDecoder[Policy] {
      case ConfigurationValue.StringValue(text) => io.circe.jawn.JawnParser(allowDuplicateKeys = false).parse(text)
        .flatMap(_.as[Policy]).fold(e => Consequence.configurationInvalid(e.getMessage), p =>
          validateC(p.definition).flatMap(_ => if (p.storeName.nonEmpty && EntityCollectionId.parse(p.collection).isSuccess &&
            p.repositorySyncEdge != null && p.repositorySyncEdge.forall(id => p.definition.edges.exists(_.identity == id)) &&
            p.dependencyWorkEdge != null && p.dependencyWorkEdge.forall(id => p.definition.edges.exists(_.identity == id)) &&
            p.repositorySyncInputs != null && p.repositorySyncInputs.forall(inputs => inputs != null && inputs.size <= 128 &&
              inputs.forall(i => i != null && p.definition.edges.exists(_.identity == i.edgeIdentity)) &&
              inputs.map(_.edgeIdentity).distinct.size == inputs.size) &&
            DependencyDevelopmentDelivery.validateC(p.definition, p.delivery).isSuccess)
            Consequence.success(p) else Consequence.configurationInvalid("Managed development requires its granted Component collection")))
      case _ => Consequence.configurationInvalid("Managed development requires a structured JSON declaration")
    })

  def runC(core: ActionCall.Core, policy: Policy, operation: String, input: Input): Consequence[Result] = for {
    roots <- rootsC(core)
    _ <- edgeC(policy.definition, input)
    result <- ManagedDevelopment.runC(operation, policy.definition, input, new NativePort(core, policy, roots))
  } yield result

  def executionPlanC(core: ActionCall.Core): Consequence[Json] =
    core.executionContext.resolvedParameters.get("textus.subsystem.process-execution.plan").flatMap(p => p.value match {
      case ConfigurationValue.StringValue(v) => Some(v)
      case _ => None
    }).map(text => io.circe.parser.parse(text).fold(e => invalid(e.message), Consequence.success))
      .getOrElse(invalid("CNCF workspace graph is unavailable"))

  def workspaceRootC(core: ActionCall.Core, workspace: String): Consequence[String] = rootsC(core).flatMap(roots =>
    roots.get(workspace).map(Consequence.success).getOrElse(invalid("unregistered workspace: " + workspace)))

  def machineTargetC(edge: Edge, configured: Option[LocalPublicationRuntime.Policy]): Consequence[LocalPublication.Target] = for {
    jar <- edge.machineLocalJar.filter(_ => machineJarValid(edge)).map(Consequence.success)
      .getOrElse(invalid("dependency edge has no explicit machine-common local JAR binding"))
    policy <- configured.map(Consequence.success).getOrElse(invalid("machine-common local publication policy is unavailable"))
    _ <- LocalPublicationRuntime.validateC(policy)
    target <- policy.targets.find(t => t.identity == jar.publicationTarget && t.component == edge.provider.identity &&
      t.workspace == edge.provider.workspace).map(Consequence.success)
      .getOrElse(invalid("machine-common local publication target does not own the declared provider Component/workspace"))
  } yield target

  def machineProgramsC(edge: Edge, plan: Json): Consequence[MachinePrograms] = for {
    provider <- _granted_sbt_c(plan, edge.provider.workspace, "publish-local")
    consumer <- _granted_sbt_c(plan, edge.consumer.workspace, "test-compile")
    publication <- provider.hcursor.get[String]("identity").fold(e => invalid(e.message), Consequence.success)
    compilation <- consumer.hcursor.get[String]("identity").fold(e => invalid(e.message), Consequence.success)
    discovery = consumer.hcursor.downField("validationDiscovery")
    binding <- (for {
      component <- discovery.get[String]("componentIdentity")
      artifact <- discovery.get[String]("artifactIdentity")
      revision <- discovery.get[String]("artifactRevision")
      prefix <- discovery.get[String]("packagePrefix")
    } yield SbtValidationDiscovery.Binding(component, artifact, revision, prefix)).fold(
      _ => invalid("consumer test-compile has no validationDiscovery binding"), _.validateC)
    _ <- if (binding.componentIdentity == edge.consumer.identity) Consequence.unit
      else invalid("test-compile discovery crosses the declared consumer Component")
    workspaces <- plan.hcursor.get[Vector[Json]]("workspaces").fold(e => invalid(e.message), Consequence.success)
    jar <- edge.machineLocalJar.filter(_ => machineJarValid(edge)).map(Consequence.success)
      .getOrElse(invalid("machine-common JAR binding is unavailable"))
    root = Path.of(jar.consumerJar).getParent.toString
    candidates = workspaces.filter(_.hcursor.get[String]("root").contains(root))
      .flatMap(_.hcursor.get[String]("identity").toOption).filter(id => _workspace_access(plan, id, Set("read-only", "manage")))
    jarworkspace <- candidates match {
      case Vector(id) => Consequence.success(id)
      case _ => invalid("machine-common JAR directory requires one registered File workspace with read access")
    }
  } yield MachinePrograms(publication, compilation, binding, jarworkspace)

  private def _workspace_access(plan: Json, workspace: String, access: Set[String]): Boolean =
    plan.hcursor.get[Vector[Json]]("workspaceGrants").toOption.toVector.flatten.exists(grant =>
      grant.hcursor.get[String]("componentId").contains(SmWorkflowComponent.componentId.name) &&
      grant.hcursor.get[String]("workspaceId").contains(workspace) && grant.hcursor.get[String]("access").exists(access))

  private def _granted_sbt_c(plan: Json, workspace: String, operation: String): Consequence[Json] = for {
    programs <- plan.hcursor.get[Vector[Json]]("sbtPrograms").fold(_ => invalid("SBT programs are unregistered"), Consequence.success)
    selected <- programs.filter(program => program.hcursor.get[String]("target").contains(workspace) &&
      program.hcursor.get[String]("operation").contains(operation)) match {
      case Vector(value) => Consequence.success(value)
      case _ => invalid("workspace " + workspace + " does not declare one " + operation + " program")
    }
    capability <- selected.hcursor.get[String]("capability").fold(e => invalid(e.message), Consequence.success)
    _ <- if (plan.hcursor.get[Vector[Json]]("grants").toOption.toVector.flatten.exists(grant =>
      grant.hcursor.get[String]("componentId").contains(SmWorkflowComponent.componentId.name) &&
      grant.hcursor.get[Vector[String]]("capabilities").toOption.exists(_.contains(capability)))) Consequence.unit
      else invalid(operation + " capability for " + workspace + " has no Component grant")
    _ <- if (_workspace_access(plan, workspace, Set("manage"))) Consequence.unit
      else invalid(operation + " workspace " + workspace + " has no managed Component grant")
  } yield selected

  def publicationC(core: ActionCall.Core, policy: Policy, input: Input): Consequence[Option[Publication]] = for {
    roots <- rootsC(core)
    edge <- edgeC(policy.definition, input)
    raw <- new NativePort(core, policy, roots).loadC(key(policy.definition, edge, "publication", input.publication))
    publication <- raw.fold(Consequence.success(Option.empty[Publication]))(_.as[Publication]
      .fold(e => invalid(e.message), p => Consequence.success(Some(p))))
    _ <- publication.fold(Consequence.unit)(p => if (p.edge == edge && p.scope == policy.definition.scope && p.identity == input.publication)
      Consequence.unit else invalid("immutable publication crosses its scoped edge/identity"))
  } yield publication

  def consumerSourceC(core: ActionCall.Core, policy: Policy, edge: Edge): Consequence[Json] = for {
    roots <- rootsC(core)
    source <- new NativePort(core, policy, roots).consumerSourceC(edge)
  } yield source

  def machineJarObservationC(core: ActionCall.Core, edge: Edge, programs: MachinePrograms): Consequence[LocalPublication.ArtifactObservation] = for {
    jar <- edge.machineLocalJar.filter(_ => machineJarValid(edge)).map(Consequence.success)
      .getOrElse(invalid("machine-common JAR binding is unavailable"))
    path = Path.of(jar.consumerJar)
    root <- workspaceRootC(core, programs.jarWorkspace)
    _ <- if (path.getParent.toString == root) Consequence.unit else invalid("machine-common JAR crosses its retained File workspace")
    workspace <- WorkspaceId.parseC(programs.jarWorkspace)
    name <- WorkspaceFilePath.parseC(path.getFileName.toString)
    limits <- WorkflowArtifactBudget.limitsC(core)
    target = FileTarget(workspace)
    observed <- WorkflowFileExecution.readBytesC(core, target, name, FileRepresentation.Identity, FileReadPolicy(limits))
    bytes <- Option(observed).filter(r => r.target == target && r.path == name && r.representation == FileRepresentation.Identity)
      .flatMap(_.bytes).filter(_.nonEmpty).map(Consequence.success)
      .getOrElse(invalid("machine-common JAR is absent from its retained File workspace"))
    digest = MessageDigest.getInstance("SHA-256").digest(bytes.toArray).map(b => f"${b & 0xff}%02x").mkString
  } yield LocalPublication.ArtifactObservation(jar.consumerJar, digest, bytes.size)

  def machineConsumerC(core: ActionCall.Core, policy: Policy, capture: GoalPhaseDependencyValidation.ManagedCapture,
    programs: MachinePrograms, publication: LocalPublication.Result, requestIdentity: String): Consequence[Result] = for {
    roots <- rootsC(core)
    edge = capture.edge
    workspace <- WorkspaceId.parseC(edge.consumer.workspace)
    target = SbtTarget(workspace, validationDiscovery = true)
    port = new NativePort(core, policy, roots)
    input = Input(edge.identity, requestIdentity, requestIdentity, requestIdentity)
    expected <- publication.artifact.filter(a => a.valid && edge.machineLocalJar.exists(_.consumerJar == a.path))
      .map(Consequence.success).getOrElse(invalid("ordinary publication lacks the exact retained JAR observation"))
    binding = Json.obj("capture" -> capture.asJson, "programs" -> programs.asJson, "publication" -> publication.request.asJson,
      "artifact" -> expected.asJson)
    beforejar <- machineJarObservationC(core, edge, programs).map(Option(_)).recoverWith(_ => Consequence.success(None))
    stored <- if (!beforejar.contains(expected)) Consequence.success(Result("unverified",
      Json.obj("reason" -> "Machine-common JAR differs from the retained provider publication".asJson)))
    else attemptC(policy.definition, edge, input, "machine-consumer-compile", port, Some(binding)) {
      _machine_source_c(core, port, edge) match {
        case Consequence.Failure(failure) => Consequence.success(Result("admission-rejected", failure.show.asJson))
        case Consequence.Success(before) => new ExecutionCall(core).runC(target, SbtOperation.TestCompile).flatMap {
        case SbtExecutionAttempt.Observed(actual) if actual != null && actual.target == target &&
            actual.operation == SbtOperation.TestCompile && actual.processResult != null &&
            actual.processResult.safeProgramIdentity == programs.consumer =>
          val receipt = Json.obj("workspace" -> workspace.value.asJson, "operation" -> "test-compile".asJson,
            "process" -> WorkflowSbtCapture.encode(actual.processResult))
          _machine_classpath(edge, workspace, programs, actual.processResult).filter(paths =>
            actual.outcome == SbtExecutionOutcome.Succeeded && actual.discovery.exists {
              case SbtValidationDiscovery.Result.Available(build) =>
                build.workspaceId == workspace && build.binding == programs.discovery && build.fullClasspath.map(_.toString) == paths
              case _ => false
            }) match {
            case Some(paths) => (for {
              after <- _machine_source_c(core, port, edge)
              actualjar <- machineJarObservationC(core, edge, programs)
              _ <- if (actualjar == expected && before == after) Consequence.unit
                else invalid("consumer source or the machine-common JAR changed during compilation")
            } yield Result("verified", receipt.deepMerge(Json.obj(
              "fullClasspath" -> paths.asJson, "consumerSource" -> after, "artifact" -> actualjar.asJson))))
              .recoverWith(failure => Consequence.success(Result("unverified", receipt.mapObject(_.add("reason", failure.show.asJson)))))
            case None => Consequence.success(Result(actual.outcome match {
              case SbtExecutionOutcome.NotExecuted => "not-started"
              case SbtExecutionOutcome.Failed(_) => "failed"
              case SbtExecutionOutcome.Incomplete(_) => "unknown"
              case _ => "unverified"
            }, receipt))
          }
        case SbtExecutionAttempt.AdmissionRejected(selected, SbtOperation.TestCompile, reason) if selected == target =>
          Consequence.success(Result("admission-rejected", reason.show.asJson))
        case SbtExecutionAttempt.Uncertain(selected, SbtOperation.TestCompile, reason) if selected == target =>
          Consequence.success(Result("unknown", reason.show.asJson))
        case _ => Consequence.success(Result("unknown", "SBT result crosses the exact ordinary consumer program, target or operation".asJson))
        }
      }
    }
    result <- if (stored.status != "verified") Consequence.success(stored) else
      _machine_source_c(core, port, edge).map { current =>
        val receipt = stored.detail.hcursor
        val paths = for {
          raw <- receipt.get[Json]("process").toOption
          actual <- WorkflowSbtCapture.decode(raw).toOption
          resolved <- _machine_classpath(edge, workspace, programs, actual)
        } yield resolved
        if (receipt.get[String]("workspace").contains(workspace.value) && receipt.get[String]("operation").contains("test-compile") &&
          paths.exists(receipt.get[Vector[String]]("fullClasspath").contains(_)) &&
          receipt.downField("artifact").focus.contains(expected.asJson) &&
          receipt.downField("consumerSource").focus.contains(current)) stored
        else Result("unverified", stored.detail.mapObject(_.add("reason", "retained consumer verification is stale or incomplete".asJson)))
      }.recoverWith(failure => Consequence.success(Result("unverified", stored.detail.mapObject(_.add("reason", failure.show.asJson)))))
  } yield result

  private def _machine_classpath(edge: Edge, workspace: WorkspaceId, programs: MachinePrograms,
    process: ProcessExecutionResult): Option[Vector[String]] =
    if (process.safeProgramIdentity != programs.consumer) None else SbtValidationDiscovery.decode(workspace, programs.discovery, process) match {
      case SbtValidationDiscovery.Result.Available(build) if build.workspaceId == workspace =>
        val paths = build.fullClasspath.map(_.toString)
        Option.when(edge.machineLocalJar.exists(jar => paths.contains(jar.consumerJar)))(paths)
      case _ => None
    }

  private def _machine_source_c(core: ActionCall.Core, port: NativePort, edge: Edge): Consequence[Json] = for {
    index <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.IndexTree, edge.consumer.workspace).flatMap(_.valueC)
    before <- port.consumerSourceC(edge)
    repeated <- port.consumerSourceC(edge)
    after <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.IndexTree, edge.consumer.workspace).flatMap(_.valueC)
    _ <- if (before != Json.Null && before == repeated && index == after) Consequence.unit
      else invalid("consumer source changed during observation")
  } yield before.mapObject(_.add("indexTree", index.value.asJson))

  /** Read an existing admitted consumer invocation. This never creates input,
    * selects an artifact, builds a provider, or treats protection as selection. */
  def repositorySyncInputC(core: ActionCall.Core, policy: Policy, input: Input): Consequence[RepositorySyncConsumedInputs.Subject] = for {
    roots <- rootsC(core)
    edge <- edgeC(policy.definition, input)
    _ <- if (edge.localProject.isEmpty) Consequence.unit else invalid("RepositorySync localProject has no admitted scoped SBT execution adapter")
    port = new NativePort(core, policy, roots)
    selection <- ManagedDevelopment.selectionC(policy.definition, edge, input, port)
    owner <- roots.get(policy.definition.ownerWorkspace).map(Consequence.success).getOrElse(invalid("input owner workspace is unavailable"))
    build <- roots.get(edge.consumer.workspace).map(Consequence.success).getOrElse(invalid("consumer workspace is unavailable"))
    _ <- ownedInventoryC(policy.definition, selection.publication, owner)
    path = "inputs/" + edge.identity + "/consumer/" + input.requestIdentity + "/input.json"
    target <- WorkspaceId.parseC(policy.definition.ownerWorkspace).map(FileTarget.apply)
    file <- WorkspaceFilePath.parseC(path)
    actual <- WorkflowFileExecution.readTextC(core, target, file)
    text <- actual.text.map(Consequence.success).getOrElse(invalid("existing consumer input is unavailable"))
    envelope <- io.circe.jawn.JawnParser(allowDuplicateKeys = false).parse(text).fold(e => invalid(e.message), Consequence.success)
    expected = executionInput(policy.definition, edge, input, owner, build, Some(selection.publication),
      Path.of(owner).resolve("inputs/" + edge.identity + "/consumer/" + input.requestIdentity + "/result.json").toString)
    _ <- if (envelope == expected) Consequence.unit else invalid("existing consumer input differs from its admitted immutable selection/build/owner")
  } yield RepositorySyncConsumedInputs.Subject(policy.definition, input, selection.publication, path, envelope)

  private def rootsC(core: ActionCall.Core): Consequence[Map[String, String]] = for {
    text <- core.executionContext.resolvedParameters.get("textus.subsystem.process-execution.plan")
      .flatMap(p => p.value match { case ConfigurationValue.StringValue(v) => Some(v); case _ => None })
      .map(Consequence.success).getOrElse(invalid("CNCF workspace graph is unavailable"))
    json <- io.circe.parser.parse(text).fold(e => invalid(e.message), Consequence.success)
    rows <- json.hcursor.get[Vector[Json]]("workspaces").fold(e => invalid(e.message), Consequence.success)
    roots <- rows.foldLeft(Consequence.success(Map.empty[String, String])) { (prior, row) => for {
      current <- prior
      id <- row.hcursor.get[String]("identity").fold(e => invalid(e.message), Consequence.success)
      root <- row.hcursor.get[String]("root").fold(e => invalid(e.message), Consequence.success)
      _ <- if (Path.of(root).isAbsolute && !current.contains(id)) Consequence.unit else invalid("duplicate or nonabsolute CNCF workspace")
    } yield current.updated(id, root) }
  } yield roots

  private final class NativePort(core: ActionCall.Core, policy: Policy, roots: Map[String, String]) extends Port {
    val participant = SecuritySubject.from(core.executionContext.security).subjectId
    private val d = policy.definition
    private def storageC(key: String) = EntityCollectionId.parse(policy.collection).flatMap { id =>
      ComponentRecordStorage.bindForCallC(core, policy.storeName, DataStore.CollectionId.EntityStore(id), DataStore.StringEntryId(key))
    }
    def loadC(key: String): Consequence[Option[Json]] = storageC(key).flatMap(_.loadC()).flatMap {
      case None => Consequence.success(None)
      case Some(row) => row.getString("managedDevelopment").map(text => io.circe.parser.parse(text)
        .fold(e => invalid(e.message), value => Consequence.success(Some(value))))
        .getOrElse(invalid("Component record has no managed development fact"))
    }
    def createC(key: String, value: Json): Consequence[Unit] = storageC(key)
      .flatMap(_.createC(Record.data("managedDevelopment" -> value.noSpaces)))
    private def rootC(workspace: String): Consequence[String] = roots.get(workspace)
      .map(Consequence.success).getOrElse(invalid("workspace is not registered in CNCF: " + workspace))
    private def ownerC: Consequence[FileTarget] = WorkspaceId.parseC(d.ownerWorkspace).map(FileTarget.apply)
    private def pathC(path: String): Consequence[WorkspaceFilePath] = WorkspaceFilePath.parseC(path)
    private def readC(path: String): Consequence[Json] = for {
      target <- ownerC
      file <- pathC(path)
      value <- WorkflowFileExecution.readTextC(core, target, file)
      text <- value.text.map(Consequence.success).getOrElse(invalid("required adapter output is unavailable: " + path))
      json <- io.circe.jawn.JawnParser(allowDuplicateKeys = false).parse(text).fold(e => invalid(e.message), Consequence.success)
    } yield json
    private def directoryC(path: String): Consequence[Unit] = path.split('/').scanLeft("")((p, n) =>
      if (p.isEmpty) n else p + "/" + n).tail.foldLeft(Consequence.unit) { (prior, name) => for {
        _ <- prior
        target <- ownerC
        path <- pathC(name)
        actual <- WorkflowFileExecution.prepareDirectoryC(core, target, path)
        _ <- if (actual.outcome != FilePrepareDirectoryOutcome.Conflict) Consequence.unit else invalid("resource directory conflict")
      } yield () }
    private def folder(e: Edge, input: Input, producer: Boolean): String =
      "inputs/" + e.identity + "/" + (if (producer) "producer" else "consumer") + "/" + input.requestIdentity
    private def resultPath(e: Edge, input: Input, producer: Boolean): String = folder(e, input, producer) + "/result.json"
    private def absolute(root: String, relative: String): String = Path.of(root).resolve(relative).normalize.toString

    def sourceC(edge: Edge): Consequence[Json] = for {
      observed <- RepositorySyncManagedGitObservation.observeInWorkspaceC(core, edge.provider.workspace)
      target <- WorkspaceId.parseC(edge.provider.workspace).map(FileTarget.apply)
      source <- sourceContentsC(observed, name => for {
        path <- pathC(name)
        actual <- WorkflowFileExecution.readBytesC(core, target, path, FileRepresentation.Identity)
      } yield actual.bytes)
    } yield source

    override def consumerSourceC(edge: Edge): Consequence[Json] =
      if (policy.dependencyWorkEdge.contains(edge.identity)) sourceC(edge.copy(provider = edge.consumer))
      else Consequence.success(Json.Null)

    def executeC(e: Edge, input: Input, selected: Option[Publication]): Consequence[Execution] = for {
      owner <- rootC(d.ownerWorkspace)
      workspace = if (selected.isEmpty) e.provider.workspace else e.consumer.workspace
      build <- rootC(workspace)
      _ <- directoryC(folder(e, input, selected.isEmpty))
      _ <- directoryC(d.repository)
      _ <- directoryC(d.staging)
      envelope = executionInput(d, e, input, owner, build, selected, absolute(owner, resultPath(e, input, selected.isEmpty)))
      target <- ownerC
      file <- pathC(folder(e, input, selected.isEmpty) + "/input.json")
      written <- WorkflowFileExecution.writeTextC(core, target, file, envelope.noSpaces, FileWriteMode.CreateNew)
      _ <- if (written.outcome == FileWriteOutcome.Created) Consequence.unit else invalid("execution input already exists; no overwrite or replay")
      buildId <- WorkspaceId.parseC(workspace)
      ownerId <- WorkspaceId.parseC(d.ownerWorkspace)
      inputPath <- WorkspaceRelativePath.parseC(file.value)
      repository <- WorkspaceRelativePath.parseC(d.repository)
      staging <- WorkspaceRelativePath.parseC(d.staging)
      scoped = SbtScopedInput(SmWorkflowComponent.componentId, buildId, ownerId, d.scope, input.selection, inputPath,
        Option.when(selected.isEmpty)(SbtScopedPublication(input.publication, repository, staging)))
      sbtTarget = SbtTarget(buildId, scopedInput = Some(scoped))
      operation = if (selected.isEmpty) SbtOperation.ScopedPublish else SbtOperation.Compile
      actual <- new ExecutionCall(core).runC(sbtTarget, operation)
    } yield attempt(actual, sbtTarget, operation)

    override def detachC(e: Edge, input: Input): Consequence[Execution] = for {
      // A distinct invocation without scoped input actually drops the selection.
      // No running SBT/Cozy process is claimed to have reloaded.
      workspace <- WorkspaceId.parseC(e.consumer.workspace)
      target = SbtTarget(workspace)
      actual <- new ExecutionCall(core).runC(target, SbtOperation.Compile)
    } yield attempt(actual, target, SbtOperation.Compile)

    override def deleteC(relative: String): Consequence[String] = for {
      target <- ownerC
      path <- pathC(relative)
      actual <- new ExecutionCall(core).deleteC(target, path)
      _ <- if (actual.target == target && actual.path == path) Consequence.unit
        else invalid("FileDelete result crosses its exact owned target")
    } yield actual.outcome.toString

    override def inventoryC(pub: Publication): Consequence[Option[OwnedInventory]] = for {
      owner <- rootC(d.ownerWorkspace)
      inventory <- ownedInventoryC(d, pub, owner)
      // Cross-check actual completion metadata before admitting or reserving
      // cleanup; only derived logical filenames ever reach FileDelete.
      controls = pub.batch match {
        case None => Vector(base(d, pub.edge, pub.identity) + "/completed.json" -> pub.completion)
        case Some(batch) => batch.members.map(m =>
          moduleBase(d, m.requested, pub.identity) + "/completed.json" -> m.completion) ++
          batch.members.map { m =>
            val row = batch.artifacts.hcursor.get[Vector[Json]]("members").toOption.get.find(value =>
              value.hcursor.downField("module").get[String]("organization").toOption.contains(m.selected.organization) &&
                value.hcursor.downField("module").get[String]("name").toOption.contains(m.selected.module)).get
            val control = Json.obj("scope" -> d.scope.asJson, "owner" -> d.ownerWorkspace.asJson,
              "producerSelectionId" -> pub.producerSelection.asJson, "batch" -> pub.identity.asJson,
              "complete" -> true.asJson, "module" -> row.hcursor.downField("module").focus.get,
              "artifacts" -> row.hcursor.downField("artifacts").focus.get)
            moduleBase(d, m.requested, pub.identity) + "/completed-v3.json" -> control
          } ++ Vector(batchPath(d, pub.identity) + "/completed.json" -> batch.completion,
            batchPath(d, pub.identity) + "/completed-v3.json" -> batch.artifacts)
      }
      _ <- controls.foldLeft(Consequence.unit) { case (prior, (path, expected)) => for {
        _ <- prior
        actual <- readC(path)
        _ <- if (actual == expected) Consequence.unit else invalid("owned publication completion metadata changed")
      } yield () }
    } yield Some(inventory)

    def publicationC(e: Edge, input: Input, source: Json): Consequence[Publication] =
      if (e.batch.nonEmpty) batchPublicationC(e, input, source) else singlePublicationC(e, input, source)

    private def batchPublicationC(e: Edge, input: Input, source: Json): Consequence[Publication] = for {
      owner <- rootC(d.ownerWorkspace)
      report <- readC(resultPath(e, input, true))
      completion <- readC(batchPath(d, input.publication) + "/completed.json")
      artifacts <- readC(batchPath(d, input.publication) + "/completed-v3.json")
      members <- e.batch.get.modules.foldLeft(Consequence.success(Vector.empty[Member])) { (prior, module) => for {
        values <- prior
        paths = member(d, module.requested, input.publication, owner)
        control <- readC(moduleBase(d, module.requested, input.publication) + "/completed.json")
        v3control <- readC(moduleBase(d, module.requested, input.publication) + "/completed-v3.json")
        _ <- if (batchMemberValid(d, input, paths, control) &&
          v3control.hcursor.get[String]("scope").toOption.contains(d.scope) &&
          v3control.hcursor.get[String]("owner").toOption.contains(d.ownerWorkspace) &&
          v3control.hcursor.get[String]("batch").toOption.contains(input.publication) &&
          v3control.hcursor.get[String]("producerSelectionId").toOption.contains(input.selection) &&
          v3control.hcursor.get[Boolean]("complete").toOption.contains(true) &&
          artifacts.hcursor.get[Vector[Json]]("members").toOption.exists(_.exists(row =>
            row.hcursor.downField("module").focus == v3control.hcursor.downField("module").focus &&
            row.hcursor.downField("artifacts").focus == v3control.hcursor.downField("artifacts").focus)))
          Consequence.unit else invalid("incomplete or contradictory batch member/control")
      } yield values :+ paths.copy(completion = control) }
      batch = PublishedBatch(members, completion, artifacts)
      _ <- if (batchPublicationValid(d, input, batch, report)) Consequence.unit
        else invalid("zero exit lacks the exact completed batch and producer report")
      primary <- members.find(_.requested == e.requested).map(Consequence.success)
        .getOrElse(invalid("batch omits primary consumer coordinate"))
    } yield Publication(e, d.scope, input.publication, input.selection, primary.selected, primary.jar, primary.ivy,
      source, report, primary.completion, Some(batch))

    private def singlePublicationC(e: Edge, input: Input, source: Json): Consequence[Publication] = for {
      owner <- rootC(d.ownerWorkspace)
      report <- readC(resultPath(e, input, true))
      completion <- readC(base(d, e, input.publication) + "/completed.json")
      selected = e.requested.copy(version = version(e.requested.version, d.scope, input.publication))
      jar = absolute(owner, base(d, e, input.publication) + "/jars/" + selected.module + ".jar")
      ivy = absolute(owner, base(d, e, input.publication) + "/ivys/ivy.xml")
      publication = Publication(e, d.scope, input.publication, input.selection, selected, jar, ivy, source, report, completion)
      _ <- if (_single_publication_valid(publication)) Consequence.unit
        else invalid("zero exit lacks matching completed publication and actual adapter report")
    } yield publication

    def consumerReportC(e: Edge, input: Input, selected: Publication): Consequence[Json] =
      if (selected.edge.batch.isEmpty) for {
        report <- readC(resultPath(e, input, false))
        _ <- if (consumerReportValid(d, input, selected, report)) Consequence.unit
          else invalid("consumer compile lacks actual matching selected coordinates/classpath resolution")
      } yield report
      else for {
        target <- ownerC
        file <- pathC(resultPath(e, input, false))
        limits <- WorkflowArtifactBudget.limitsC(core)
        actual <- WorkflowFileExecution.readBytesC(core, target, file, FileRepresentation.Identity, FileReadPolicy(limits))
        bytes <- actual.bytes.map(Consequence.success).getOrElse(invalid("required adapter output is unavailable: " + file.value))
        text <- try Consequence.success(UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes.toArray)).toString)
          catch { case NonFatal(_) => invalid("consumer adapter report is not complete UTF-8") }
        report <- io.circe.jawn.JawnParser(allowDuplicateKeys = false).parse(text).fold(e => invalid(e.message), Consequence.success)
        // Check the complete original report before reducing persisted evidence.
        _ <- if (consumerReportValid(d, input, selected, report)) Consequence.unit
          else invalid("consumer compile lacks actual matching selected coordinates/classpath resolution")
      } yield consumerReportEvidence(report, target.workspaceId.value, file.value, bytes)
  }

  /** Persist actual consumer/generator observations once, with a content-bound
    * reference to the unchanged full report. Producer resolution rows are large
    * duplicates of the adapter's retained evidence, not status payloads.
    * The caller must validate the full report before invoking this projection.
    */
  private[admission] def consumerReportEvidence(report: Json, owner: String, path: String, bytes: Vector[Byte]): Json = {
    val compact = report.mapObject { fields =>
      fields("generation").fold(fields) { generation =>
        fields.add("generation", generation.mapObject { g =>
          g("observations").fold(g) { observations =>
            g.add("observations", observations.mapArray(_.map(_.mapObject { row =>
              row("producerResolution").fold(row) { producer =>
                val p = producer.hcursor
                row.add("producerResolution", Json.obj(
                  "producerRunReference" -> p.downField("producerRunReference").focus.getOrElse(Json.Null),
                  "reportRowCount" -> p.get[Vector[Json]]("reportRows").toOption.map(_.size).asJson))
              }
            })))
          }
        })
      }
    }
    val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes.toArray).map(b => f"${b & 0xff}%02x").mkString
    compact.mapObject(_.add("rawReport", Json.obj("workspace" -> owner.asJson, "path" -> path.asJson,
      "byteCount" -> bytes.size.asJson, "sha256" -> sha256.asJson)))
  }

  /** Convert admitted v1/v2/v3 metadata into literal owned filenames. Absolute
    * DTO paths are equality evidence only; they never choose deletion targets. */
  private[admission] def ownedInventoryC(d: Definition, pub: Publication, owner: String): Consequence[OwnedInventory] = {
    def check(ok: Boolean, reason: String): Consequence[Unit] = if (ok) Consequence.unit else invalid(reason)
    def absolute(relative: String): String = Path.of(owner).resolve(relative).normalize.toString
    def dependencies(value: Json): Consequence[Vector[Coordinate]] = for {
      values <- value.hcursor.get[Vector[Coordinate]]("dependencies").fold(e => invalid(e.message), Consequence.success)
      _ <- check(values.size <= 4096 && values.forall(c => c != null && Vector(c.organization, c.module, c.version).forall(id)),
        "invalid bounded publication dependency coordinates")
    } yield values
    val members = pub.batch.map(_.members).getOrElse(Vector(Member(pub.edge.requested, pub.selected, pub.jar, pub.ivy, pub.completion)))
    for {
      _ <- validateC(d)
      _ <- check(d.edges.contains(pub.edge) && pub.scope == d.scope && id(pub.identity) && id(pub.producerSelection) &&
        Path.of(owner).isAbsolute && members.nonEmpty && members.size <= 64 &&
        members.map(_.requested).toSet == pub.edge.batch.map(_.modules.map(_.requested).toSet).getOrElse(Set(pub.edge.requested)) &&
        members.map(_.requested).distinct.size == members.size &&
        pub.selected == pub.edge.requested.copy(version = version(pub.edge.requested.version, d.scope, pub.identity)) &&
        members.exists(m => m.requested == pub.edge.requested && m.jar == pub.jar && m.ivy == pub.ivy), "foreign owned publication")
      result <- members.foldLeft(Consequence.success(OwnedInventory(Vector.empty, Vector.empty))) { (prior, m) => for {
        before <- prior
        prefix = moduleBase(d, m.requested, pub.identity)
        coordinate = m.requested.copy(version = version(m.requested.version, d.scope, pub.identity))
        _ <- check(Vector(m.requested.organization, m.requested.module, m.requested.version).forall(id) && m.selected == coordinate &&
          m.jar == absolute(prefix + "/jars/" + coordinate.module + ".jar") && m.ivy == absolute(prefix + "/ivys/ivy.xml"),
          "publication paths/coordinates differ from the configured logical repository")
        deps <- dependencies(m.completion)
        auxiliary <- pub.batch match {
          case None => check(m.completion.hcursor.get[String]("schema").toOption.contains("sbt-cozy.development-artifact-publication.v1") &&
            m.completion.hcursor.get[String]("scope").toOption.contains(d.scope) &&
            m.completion.hcursor.get[String]("selection").toOption.contains(pub.producerSelection) &&
            m.completion.hcursor.get[String]("publication").toOption.contains(pub.identity) &&
            m.completion.hcursor.get[String]("organization").toOption.contains(coordinate.organization) &&
            m.completion.hcursor.get[String]("module").toOption.contains(coordinate.module) &&
            m.completion.hcursor.get[String]("version").toOption.contains(coordinate.version),
            "single publication completion differs from admitted identity").map(_ => Vector.empty[String])
          case Some(batch) => for {
            rows <- batch.artifacts.hcursor.get[Vector[Json]]("members").fold(e => invalid(e.message), Consequence.success)
            matching = rows.filter(row => row.hcursor.downField("module").get[String]("organization").toOption.contains(coordinate.organization) &&
              row.hcursor.downField("module").get[String]("name").toOption.contains(coordinate.module))
            row <- if (matching.size == 1) Consequence.success(matching.head) else invalid("v3 member is missing or duplicated")
            module = row.hcursor.downField("module")
            _ <- check(batchMemberValid(d, Input(pub.edge.identity, "inventory", pub.identity, pub.producerSelection), m, m.completion) &&
              batchOwnerValid(d, Input(pub.edge.identity, "inventory", pub.identity, pub.producerSelection), batch.completion) &&
              batchOwnerValid(d, Input(pub.edge.identity, "inventory", pub.identity, pub.producerSelection), batch.artifacts) &&
              rows.size == members.size && module.get[String]("selectedVersion").toOption.contains(coordinate.version) &&
              module.get[String]("baseVersion").toOption.contains(m.requested.version) &&
              module.get[String]("descriptor").toOption.contains(m.ivy) &&
              module.get[String]("control").toOption.contains(absolute(prefix + "/completed-v3.json")),
              "v3 metadata differs from admitted owner/member")
            v2rows <- batch.completion.hcursor.get[Vector[Json]]("members").fold(e => invalid(e.message), Consequence.success)
            _ <- check(v2rows.count(row => row.as[Coordinate].toOption.contains(coordinate) &&
              row.hcursor.downField("dependencies").focus == m.completion.hcursor.downField("dependencies").focus) == 1,
              "batch and member dependency evidence differ")
            artifacts <- row.hcursor.get[Vector[Json]]("artifacts").fold(e => invalid(e.message), Consequence.success)
            _ <- check(artifacts.nonEmpty && artifacts.size <= 64 && artifacts.distinct.size == artifacts.size, "invalid bounded auxiliary inventory")
            paths <- artifacts.foldLeft(Consequence.success(Vector.empty[String])) { (prior, artifact) => for {
              paths <- prior
              key = artifact.hcursor.downField("key")
              name <- key.get[String]("name").fold(e => invalid(e.message), Consequence.success)
              extension <- key.get[String]("extension").fold(e => invalid(e.message), Consequence.success)
              kind <- key.get[String]("artifactType").fold(e => invalid(e.message), Consequence.success)
              classifier <- key.get[Option[String]]("classifier").fold(e => invalid(e.message), Consequence.success)
              _ <- check(name == coordinate.module && id(extension) && id(kind) && classifier.forall(id) &&
                key.get[String]("module").toOption.contains(coordinate.organization + ":" + coordinate.module + ":" + coordinate.version),
                "foreign or nonstandard auxiliary artifact identity")
              path = prefix + "/jars/" + coordinate.module + classifier.map("-" + _).getOrElse("") + "." + extension
              _ <- check(artifact.hcursor.get[String]("publicationFile").toOption.contains(absolute(path)),
                "auxiliary filename differs from configured owned coordinate")
            } yield paths :+ path }
            _ <- check(paths.contains(prefix + "/jars/" + coordinate.module + ".jar"), "v3 inventory omits its primary JAR")
          } yield paths :+ (prefix + "/completed-v3.json")
        }
        files = Vector(prefix + "/jars/" + coordinate.module + ".jar", prefix + "/ivys/ivy.xml") ++ auxiliary ++ Vector(prefix + "/completed.json")
      } yield OwnedInventory(before.paths ++ files.distinct, (before.dependencies ++ deps).distinct) }
      markers = pub.batch.toVector.flatMap(_ => Vector(batchPath(d, pub.identity) + "/completed.json", batchPath(d, pub.identity) + "/completed-v3.json"))
      paths = result.paths ++ markers
      _ <- check(result.dependencies.size <= 4096 && paths.size <= 8192 && paths.distinct.size == paths.size && paths.forall(path =>
        path.startsWith(d.repository + "/") && WorkspaceFilePath.parseC(path).isSuccess), "owned inventory is not bounded/exact")
    } yield result.copy(paths = paths)
  }

  /** Hash only complete bytes acquired by the producer workspace's File UoW.
    * Missing paths remain explicit (deletions); failed or unsupported reads
    * propagate and cannot become a completed publication's source evidence.
    */
  private[admission] def sourceContentsC(observed: RepositorySyncManagedGitObservation.Observed,
    read: String => Consequence[Option[Vector[Byte]]]): Consequence[Json] =
    observed.dirty.sortBy(_.path).foldLeft(Consequence.success(Vector.empty[Json])) { (prior, entry) => for {
      values <- prior
      bytes <- read(entry.path)
      digest = bytes.map(value => MessageDigest.getInstance("SHA-256").digest(value.toArray)
        .map(b => f"${b & 0xff}%02x").mkString)
    } yield values :+ Json.obj("path" -> entry.path.asJson, "byteCount" -> bytes.map(_.size).asJson,
      "sha256" -> digest.asJson) }.map(contents => observed.asJson.deepMerge(Json.obj("dirtyContents" -> contents.asJson)))

  def executionInput(d: Definition, e: Edge, input: Input, owner: String, build: String,
    selected: Option[Publication], result: String): Json = {
    def path(relative: String): String = Path.of(owner).resolve(relative).normalize.toString
    val producer = selected.isEmpty
    val members = selected.toVector.flatMap(p => p.batch.map(_.members).getOrElse(
      Vector(Member(e.requested, p.selected, p.jar, p.ivy, p.completion))))
    def mapping(values: Vector[Coordinate]): Vector[Json] = selected.toVector.flatMap { p =>
      values.map { requested =>
        val m = members.find(_.requested == requested).getOrElse(
          throw new IllegalArgumentException("immutable publication omits a configured member"))
        Json.obj("requested" -> requested.asJson, "selected" -> m.selected.asJson,
          "jar" -> m.jar.asJson, "ivy" -> m.ivy.asJson, "publication" -> p.identity.asJson,
          "publicationSelection" -> p.producerSelection.asJson)
      }
    }
    val runtimeMappings = mapping(e.batch.map(_.consumerModules).getOrElse(Vector(e.requested)))
    val shared = Json.obj("component" -> SmWorkflowComponent.componentId.name.asJson,
      "buildWorkspace" -> (if (producer) e.provider.workspace else e.consumer.workspace).asJson, "ownerWorkspace" -> d.ownerWorkspace.asJson,
      "scope" -> d.scope.asJson, "selection" -> input.selection.asJson)
    val adapter = shared.deepMerge(Json.obj("schema" -> (if (e.batch.nonEmpty) "sbt-cozy.development-artifact.v2" else "sbt-cozy.development-artifact.v1").asJson,
      "buildRoot" -> build.asJson, "ownerRoot" -> owner.asJson, "repositoryRoot" -> path(d.repository).asJson,
      "resultFile" -> result.asJson, "mappings" -> runtimeMappings.asJson))
    val consumer = e.batch.flatMap(_.generator).filter(_ => !producer).fold(adapter)(g => adapter.deepMerge(Json.obj(
      "generator" -> Json.obj("cozyVersion" -> g.cozyVersion.asJson, "modelerVersion" -> g.modelerVersion.asJson,
        "batch" -> input.publication.asJson, "mappings" -> mapping(g.modules).asJson))))
    val declared = if (!producer) consumer else if (e.batch.nonEmpty) adapter.deepMerge(Json.obj(
      "producers" -> e.batch.get.modules.map { module =>
        val m = member(d, module.requested, input.publication, owner)
        m.selected.asJson.deepMerge(Json.obj("project" -> module.project.asJson, "jar" -> m.jar.asJson, "ivy" -> m.ivy.asJson))
      }.asJson)) else adapter.deepMerge(Json.obj("producer" -> Json.obj(
      "organization" -> e.requested.organization.asJson, "module" -> e.requested.module.asJson,
      "version" -> version(e.requested.version, d.scope, input.publication).asJson,
      "jar" -> path(base(d, e, input.publication) + "/jars/" + e.requested.module + ".jar").asJson,
      "ivy" -> path(base(d, e, input.publication) + "/ivys/ivy.xml").asJson)))
    val envelope = shared.deepMerge(Json.obj("schema" -> "cncf.scoped-sbt-input.v1".asJson,
      "kind" -> (if (producer) "producer" else "consumer").asJson, "cozyArtifact" -> declared))
    if (!producer) envelope else envelope.deepMerge(Json.obj("publication" -> input.publication.asJson,
      "repository" -> d.repository.asJson, "staging" -> d.staging.asJson))
  }

  def moduleBase(d: Definition, requested: Coordinate, publication: String): String =
    d.repository + "/" + requested.organization + "/" + requested.module + "/" + version(requested.version, d.scope, publication)
  def batchPath(d: Definition, publication: String): String = d.repository + "/.batches/" + d.scope + "/" + publication
  def member(d: Definition, requested: Coordinate, publication: String, owner: String): Member = {
    val root = Path.of(owner).resolve(moduleBase(d, requested, publication))
    Member(requested, requested.copy(version = version(requested.version, d.scope, publication)),
      root.resolve("jars/" + requested.module + ".jar").toString, root.resolve("ivys/ivy.xml").toString, Json.Null)
  }

  /** Verify the producer report and controls carried by the existing publication receipt. */
  def retainedPublicationValid(publication: Publication): Boolean = {
    val edge = publication.edge
    val requested = edge.requested
    def _selected_(coordinate: Coordinate): Coordinate =
      coordinate.copy(version = version(coordinate.version, publication.scope, publication.identity))
    Vector(publication.scope, publication.identity, publication.producerSelection, edge.localRepository,
      requested.organization, requested.module, requested.version).forall(id) && edge.localJar &&
      publication.selected == _selected_(requested) &&
      _artifact_repository(publication.selected, publication.jar, publication.ivy).exists { repository =>
        (edge.batch, publication.batch) match {
          case (None, None) => _single_publication_valid(publication)
          case (Some(declaration), Some(batch)) =>
            val primary = Member(requested, publication.selected, publication.jar, publication.ivy, publication.completion)
            batch.members.map(_.requested) == declaration.modules.map(_.requested) && batch.members.contains(primary) &&
              batch.members.forall(m => Vector(m.requested.organization, m.requested.module, m.requested.version).forall(id) &&
                m.selected == _selected_(m.requested) && _artifact_repository(m.selected, m.jar, m.ivy).contains(repository)) &&
              _batch_publication_valid(publication.scope, edge.localRepository, publication.identity, publication.producerSelection,
                batch, publication.report)
          case _ => false
        }
      }
  }

  private def _artifact_repository(coordinate: Coordinate, jar: String, ivy: String): Option[Path] =
    scala.util.Try {
      val jarpath = Path.of(jar)
      val suffix = Path.of(coordinate.organization, coordinate.module, coordinate.version,
        "jars", coordinate.module + ".jar")
      Option.when(jarpath.isAbsolute && jarpath.normalize == jarpath && jarpath.toString == jar &&
        jarpath.getNameCount > suffix.getNameCount && jarpath.endsWith(suffix)) {
        jarpath.getRoot.resolve(jarpath.subpath(0, jarpath.getNameCount - suffix.getNameCount))
      }.filter(root => root.resolve(suffix.getParent.getParent).resolve("ivys/ivy.xml").toString == ivy)
    }.toOption.flatten

  private def _single_publication_valid(publication: Publication): Boolean = {
    val report = publication.report.hcursor
    val completion = publication.completion.hcursor
    val selected = publication.selected
    report.get[String]("schema").toOption.contains("sbt-cozy.development-artifact-result.v1") &&
      report.get[String]("kind").toOption.contains("producer") &&
      report.get[Coordinate]("coordinate").toOption.contains(selected) &&
      report.get[String]("jar").toOption.contains(publication.jar) && report.get[String]("ivy").toOption.contains(publication.ivy) &&
      Vector("scope" -> publication.scope, "selection" -> publication.producerSelection, "publication" -> publication.identity)
        .forall { case (key, value) => report.downField(key).focus.forall(_.asString.contains(value)) } &&
      completion.get[String]("schema").toOption.contains("sbt-cozy.development-artifact-publication.v1") &&
      completion.get[String]("scope").toOption.contains(publication.scope) &&
      completion.get[String]("selection").toOption.contains(publication.producerSelection) &&
      completion.get[String]("publication").toOption.contains(publication.identity) &&
      completion.get[String]("organization").toOption.contains(selected.organization) &&
      completion.get[String]("module").toOption.contains(selected.module) &&
      completion.get[String]("version").toOption.contains(selected.version)
  }

  private def batchOwnerValid(d: Definition, input: Input, json: Json): Boolean = {
    _batch_owner_valid(d.scope, d.ownerWorkspace, input.publication, input.selection, json)
  }
  private def _batch_owner_valid(scope: String, owner: String, identity: String, selection: String, json: Json): Boolean = {
    val c = json.hcursor
    c.get[String]("scope").toOption.contains(scope) && c.get[String]("owner").toOption.contains(owner) &&
      c.get[String]("batch").toOption.contains(identity) &&
      c.get[String]("producerSelectionId").toOption.contains(selection) && c.get[Boolean]("complete").toOption.contains(true)
  }
  private[admission] def batchMemberValid(d: Definition, input: Input, member: Member, control: Json): Boolean =
    _batch_member_valid(d.scope, d.ownerWorkspace, input.publication, input.selection, member, control)

  private def _batch_member_valid(scope: String, owner: String, identity: String, selection: String, member: Member, control: Json): Boolean = {
    val c = control.hcursor
    _batch_owner_valid(scope, owner, identity, selection, control) && c.get[String]("schema").toOption.contains("sbt-cozy.development-artifact-publication.v2") &&
      c.get[String]("organization").toOption.contains(member.selected.organization) &&
      c.get[String]("module").toOption.contains(member.selected.module) && c.get[String]("version").toOption.contains(member.selected.version) &&
      c.get[String]("baseVersion").toOption.contains(member.requested.version) &&
      c.get[String]("artifact").toOption.contains(member.jar)
  }
  private[admission] def batchPublicationValid(d: Definition, input: Input, batch: PublishedBatch, report: Json): Boolean =
    _batch_publication_valid(d.scope, d.ownerWorkspace, input.publication, input.selection, batch, report)

  private def _batch_publication_valid(scope: String, owner: String, identity: String, selection: String,
    batch: PublishedBatch, report: Json): Boolean = {
    def _exact_(rows: Option[Vector[Json]], coordinate: Json => Option[Coordinate]): Boolean = rows.exists { values =>
      val actual = values.map(coordinate)
      actual.forall(_.nonEmpty) && actual.flatten.distinct.size == actual.size && actual.flatten.toSet == batch.members.map(_.selected).toSet
    }
    val r = report.hcursor
    batch.members.nonEmpty && _batch_owner_valid(scope, owner, identity, selection, batch.completion) &&
      _batch_owner_valid(scope, owner, identity, selection, batch.artifacts) &&
      batch.completion.hcursor.get[String]("schema").toOption.contains("sbt-cozy.development-artifact-batch.v2") &&
      r.get[String]("schema").toOption.contains("sbt-cozy.development-artifact-result.v2") &&
      r.get[String]("kind").toOption.contains("producer") && r.get[String]("scope").toOption.contains(scope) &&
      r.get[String]("publication").toOption.contains(identity) && r.get[String]("selection").toOption.contains(selection) &&
      _exact_(r.get[Vector[Json]]("members").toOption, _.as[Coordinate].toOption) &&
      _exact_(batch.completion.hcursor.get[Vector[Json]]("members").toOption, _.as[Coordinate].toOption) &&
      _exact_(batch.artifacts.hcursor.get[Vector[Json]]("members").toOption, value => {
        val c = value.hcursor.downField("module")
        for { org <- c.get[String]("organization").toOption; name <- c.get[String]("name").toOption
          version <- c.get[String]("selectedVersion").toOption } yield Coordinate(org, name, version)
      }) && batch.members.forall(m => _batch_member_valid(scope, owner, identity, selection, m, m.completion) &&
        r.get[Vector[Json]]("members").toOption.exists(_.exists(row => row.as[Coordinate].toOption.contains(m.selected) &&
          row.hcursor.get[String]("jar").toOption.contains(m.jar) && row.hcursor.get[String]("ivy").toOption.contains(m.ivy))))
  }

  def consumerReportValid(d: Definition, input: Input, selected: Publication, report: Json): Boolean = {
    val c = report.hcursor
    val members = selected.batch.map(_.members).getOrElse(Vector(Member(selected.edge.requested, selected.selected,
      selected.jar, selected.ivy, selected.completion)))
    val expected = selected.edge.batch.map(_.consumerModules).getOrElse(Vector(selected.edge.requested))
    val resolved = c.get[Vector[Json]]("results").toOption.exists(_.exists { row =>
      val r = row.hcursor
      val classpath = r.get[Vector[String]]("classpath").toOption.getOrElse(Vector.empty)
      val rows = r.get[Vector[Json]]("resolved").toOption.getOrElse(Vector.empty)
      r.get[String]("configuration").toOption.contains("Compile") && r.get[String]("outcome").toOption.contains("success") &&
        classpath.nonEmpty && expected.forall { requested => members.find(_.requested == requested).exists { member =>
          val matches = rows.filter(_.hcursor.get[Coordinate]("requested").toOption.contains(requested))
          matches.size == 1 && matches.head.hcursor.get[Coordinate]("selected").toOption.contains(member.selected) &&
            matches.head.hcursor.get[Vector[String]]("jars").toOption.exists(jars => jars.nonEmpty && jars.forall(classpath.contains))
        }}
    })
    c.get[String]("schema").toOption.contains("sbt-cozy.development-artifact-result.v1") &&
      c.get[String]("kind").toOption.contains("consumer") && c.get[String]("scope").toOption.contains(d.scope) &&
      c.get[String]("selection").toOption.contains(input.selection) && resolved &&
      selected.edge.batch.flatMap(_.generator).forall(_ => generationReportValid(input, selected, report))
  }

  /** The adapter reconciles child origins with its actual SBT resolution. Native
    * verification still requires that observation and generated inventory, rather
    * than mistaking a successful compiler process for selected generation.
    */
  private[admission] def generationReportValid(input: Input, selected: Publication, report: Json): Boolean = {
    val g = report.hcursor.downField("generation")
    g.get[String]("outcome").toOption.contains("generated") && g.get[String]("batch").toOption.contains(input.publication) &&
      g.get[Vector[String]]("products").toOption.exists(_.exists(_.endsWith(".scala"))) &&
      g.get[Vector[String]]("results").toOption.exists(_.nonEmpty) &&
      g.get[Vector[Json]]("observations").toOption.exists(rows => rows.nonEmpty && rows.forall { row =>
        val c = row.hcursor
        c.get[String]("outcome").toOption.contains("generated") &&
          c.downField("producerResolution").get[Vector[Json]]("reportRows").toOption.exists(_.nonEmpty) &&
          c.downField("observation").get[Vector[String]]("classpath").toOption.exists(_.nonEmpty) &&
          c.downField("observation").get[String]("cozyVersion").toOption.contains(selected.edge.batch.get.generator.get.cozyVersion) &&
          c.downField("observation").get[String]("simpleModelerVersion").toOption.contains(selected.edge.batch.get.generator.get.modelerVersion) &&
          c.downField("observation").get[String]("requiredSimpleModelerVersion").toOption.contains(selected.edge.batch.get.generator.get.modelerVersion) &&
          Vector("cozy" -> "cozy_2.12", "simpleModeler" -> "simplemodeler_2.12").forall { (name, module) =>
            selected.batch.toVector.flatMap(_.members).find(m => m.requested.organization == "org.simplemodeling" &&
              m.requested.module == module).exists { member =>
                c.downField("observation").downField(name).get[String]("codeSource").toOption.contains(member.jar) &&
                  c.downField("observation").get[Vector[String]]("classpath").toOption.exists(_.contains(member.jar))
              }
          }
      })
  }

  private def attempt(actual: SbtExecutionAttempt, target: SbtTarget, operation: SbtOperation): Execution = actual match {
    case SbtExecutionAttempt.Observed(r) if r.target == target && r.operation == operation =>
      val p = r.processResult
      val details = Json.obj("operation" -> operation.toString.asJson, "workspace" -> target.workspaceId.value.asJson,
        "termination" -> p.termination.toString.asJson, "program" -> p.safeProgramIdentity.asJson,
        "elapsedMillis" -> p.elapsedMillis.asJson,
        "stdout" -> new String(p.stdout.content.toArray, UTF_8).asJson,
        "stderr" -> new String(p.stderr.content.toArray, UTF_8).asJson)
      Execution(if (r.outcome == SbtExecutionOutcome.Succeeded) "succeeded" else "failed", details)
    case SbtExecutionAttempt.AdmissionRejected(t, op, c) if t == target && op == operation => Execution("admission-rejected", c.show.asJson)
    case SbtExecutionAttempt.Uncertain(t, op, c) if t == target && op == operation => Execution("unknown", c.show.asJson)
    case _ => Execution("unknown", "SBT result crosses its exact target or operation".asJson)
  }
  private final class ExecutionCall(val core: ActionCall.Core) extends ProcedureActionCall {
    def runC(target: SbtTarget, operation: SbtOperation): Consequence[SbtExecutionAttempt] =
      executeProgram(ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.SbtExecAttempt(target, operation))))
    def deleteC(target: FileTarget, path: WorkspaceFilePath): Consequence[FileDeleteResult] =
      executeProgram(ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.FileDelete(target, path))))
    def execute(): Consequence[OperationResponse] = invalid("SBT bridge is not a public Operation")
  }
}
