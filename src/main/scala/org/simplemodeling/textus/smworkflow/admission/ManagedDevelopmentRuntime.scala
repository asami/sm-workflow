package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.security.MessageDigest
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
  * @version Nov. 12, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object ManagedDevelopmentRuntime {
  final case class Policy(storeName: String, collection: String, definition: Definition,
    repositorySyncEdge: Option[String] = None, dependencyWorkEdge: Option[String] = None)
  val declaration = ComponentParameterKey.optional("sm-workflow.managed-development",
    ComponentParameterDecoder[Policy] {
      case ConfigurationValue.StringValue(text) => io.circe.jawn.JawnParser(allowDuplicateKeys = false).parse(text)
        .flatMap(_.as[Policy]).fold(e => Consequence.configurationInvalid(e.getMessage), p =>
          validateC(p.definition).flatMap(_ => if (p.storeName.nonEmpty && EntityCollectionId.parse(p.collection).isSuccess &&
            p.repositorySyncEdge != null && p.repositorySyncEdge.forall(id => p.definition.edges.exists(_.identity == id)) &&
            p.dependencyWorkEdge != null && p.dependencyWorkEdge.forall(id => p.definition.edges.exists(_.identity == id)))
            Consequence.success(p) else Consequence.configurationInvalid("Managed development requires its granted Component collection")))
      case _ => Consequence.configurationInvalid("Managed development requires a structured JSON declaration")
    })

  def runC(core: ActionCall.Core, policy: Policy, operation: String, input: Input): Consequence[Result] = for {
    roots <- rootsC(core)
    _ <- edgeC(policy.definition, input)
    result <- ManagedDevelopment.runC(operation, policy.definition, input, new NativePort(core, policy, roots))
  } yield result

  def consumerSourceC(core: ActionCall.Core, policy: Policy, edge: Edge): Consequence[Json] = for {
    roots <- rootsC(core)
    source <- new NativePort(core, policy, roots).consumerSourceC(edge)
  } yield source

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

    def publicationC(e: Edge, input: Input, source: Json): Consequence[Publication] = for {
      owner <- rootC(d.ownerWorkspace)
      report <- readC(resultPath(e, input, true))
      completion <- readC(base(d, e, input.publication) + "/completed.json")
      selected = e.requested.copy(version = version(e.requested.version, d.scope, input.publication))
      jar = absolute(owner, base(d, e, input.publication) + "/jars/" + selected.module + ".jar")
      ivy = absolute(owner, base(d, e, input.publication) + "/ivys/ivy.xml")
      _ <- if (report.hcursor.get[String]("schema").toOption.contains("sbt-cozy.development-artifact-result.v1") &&
        report.hcursor.get[String]("kind").toOption.contains("producer") &&
        report.hcursor.get[Coordinate]("coordinate").toOption.contains(selected) &&
        report.hcursor.get[String]("jar").toOption.contains(jar) && report.hcursor.get[String]("ivy").toOption.contains(ivy) &&
        completion.hcursor.get[String]("schema").toOption.contains("sbt-cozy.development-artifact-publication.v1") &&
        completion.hcursor.get[String]("scope").toOption.contains(d.scope) &&
        completion.hcursor.get[String]("selection").toOption.contains(input.selection) &&
        completion.hcursor.get[String]("publication").toOption.contains(input.publication) &&
        completion.hcursor.get[String]("organization").toOption.contains(selected.organization) &&
        completion.hcursor.get[String]("module").toOption.contains(selected.module) &&
        completion.hcursor.get[String]("version").toOption.contains(selected.version)) Consequence.unit
      else invalid("zero exit lacks matching completed publication and actual adapter report")
    } yield Publication(e, d.scope, input.publication, input.selection, selected, jar, ivy, source, report, completion)

    def consumerReportC(e: Edge, input: Input, selected: Publication): Consequence[Json] = for {
      report <- readC(resultPath(e, input, false))
      _ <- if (consumerReportValid(d, input, selected, report)) Consequence.unit
        else invalid("consumer compile lacks actual matching selected coordinates/classpath resolution")
    } yield report
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
    val mapping = selected.toVector.map(p => Json.obj("requested" -> e.requested.asJson, "selected" -> p.selected.asJson,
      "jar" -> p.jar.asJson, "ivy" -> p.ivy.asJson, "publication" -> p.identity.asJson,
      "publicationSelection" -> p.producerSelection.asJson))
    val shared = Json.obj("component" -> SmWorkflowComponent.componentId.name.asJson,
      "buildWorkspace" -> (if (producer) e.provider.workspace else e.consumer.workspace).asJson, "ownerWorkspace" -> d.ownerWorkspace.asJson,
      "scope" -> d.scope.asJson, "selection" -> input.selection.asJson)
    val adapter = shared.deepMerge(Json.obj("schema" -> "sbt-cozy.development-artifact.v1".asJson,
      "buildRoot" -> build.asJson, "ownerRoot" -> owner.asJson, "repositoryRoot" -> path(d.repository).asJson,
      "resultFile" -> result.asJson, "mappings" -> mapping.asJson))
    val declared = if (!producer) adapter else adapter.deepMerge(Json.obj("producer" -> Json.obj(
      "organization" -> e.requested.organization.asJson, "module" -> e.requested.module.asJson,
      "version" -> version(e.requested.version, d.scope, input.publication).asJson,
      "jar" -> path(base(d, e, input.publication) + "/jars/" + e.requested.module + ".jar").asJson,
      "ivy" -> path(base(d, e, input.publication) + "/ivys/ivy.xml").asJson)))
    val envelope = shared.deepMerge(Json.obj("schema" -> "cncf.scoped-sbt-input.v1".asJson,
      "kind" -> (if (producer) "producer" else "consumer").asJson, "cozyArtifact" -> declared))
    if (!producer) envelope else envelope.deepMerge(Json.obj("publication" -> input.publication.asJson,
      "repository" -> d.repository.asJson, "staging" -> d.staging.asJson))
  }

  def consumerReportValid(d: Definition, input: Input, selected: Publication, report: Json): Boolean = {
    val c = report.hcursor
    c.get[String]("schema").toOption.contains("sbt-cozy.development-artifact-result.v1") &&
      c.get[String]("kind").toOption.contains("consumer") && c.get[String]("scope").toOption.contains(d.scope) &&
      c.get[String]("selection").toOption.contains(input.selection) && c.get[Vector[Json]]("results").toOption.exists(_.exists { row =>
        val r = row.hcursor
        r.get[String]("configuration").toOption.contains("Compile") && r.get[String]("outcome").toOption.contains("success") &&
          r.get[Vector[String]]("classpath").toOption.exists(_.nonEmpty) &&
          r.get[Vector[Json]]("resolved").toOption.exists(_.exists { value =>
            value.hcursor.get[Coordinate]("requested").toOption.contains(selected.edge.requested) &&
              value.hcursor.get[Coordinate]("selected").toOption.contains(selected.selected) &&
              value.hcursor.get[Vector[String]]("jars").toOption.exists(jars =>
                jars.nonEmpty && r.get[Vector[String]]("classpath").toOption.exists(paths => jars.exists(paths.contains)))
          })
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
    def execute(): Consequence[OperationResponse] = invalid("SBT bridge is not a public Operation")
  }
}
