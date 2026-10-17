package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.workspace.*
import org.goldenport.configuration.ConfigurationValue
import SplitPhasePlanningSnapshot as R
import SplitPhaseSelectedDocuments as D
import SplitPhaseSelectedDocumentEffect.{Observation, Status}

/** Saved selected business edits, using actual typed File facts in the current UoW.
  * Each file installation is independent; retained partial effects are never replayed.
  * @since   Oct. 15, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowSelectedDocumentFiles {
  val sourceSchema = "sm-workflow.split-phase-source.v1"
  val resultSchema = "sm-workflow.selected-documents-effect.v1"
  val repositoryKey = "sm-workflow.split-phase.documents.repository"
  val workspaceKey = "sm-workflow.split-phase.documents.workspace"
  final case class Request(repository: String, instance: String, context: String,
    revision: String, snapshot: R.Record)

  def validateC(request: Request): Consequence[D.Projection] =
    if (request == null || Vector(request.repository, request.instance, request.context, request.revision)
        .exists(s => s == null || s.isEmpty || s.trim != s)) invalid("references are incomplete")
    else for {
      selected <- D.projectC(request.snapshot)
      repositories = selected.documents.map(_.contract.hcursor.downField("validation")
        .downField("repository").get[String]("value").toOption).distinct
      _ <- if (repositories == Vector(Some(request.repository))) Consequence.unit
        else invalid("selected repository differs from its business association")
    } yield selected

  def observeC(core: ActionCall.Core, request: Request): Consequence[Observation] = for {
    selected <- validateC(request)
    target <- targetC(core, request.repository)
    observed <- readC(core, target, selected)
    states = selected.documents.zip(observed).map((d, text) => classify(request.snapshot, d, text))
  } yield observation(request, selected, states, applying = false)

  def applyC(core: ActionCall.Core, request: Request): Consequence[Observation] = for {
    selected <- validateC(request)
    target <- targetC(core, request.repository)
    observed <- readC(core, target, selected)
    states = selected.documents.zip(observed).map((d, text) => classify(request.snapshot, d, text))
    result <- if (!states.forall(_ == State.Ready))
      Consequence.success(observation(request, selected, states, applying = true))
    else {
      // Compute the complete intended write set before the first installation.
      val writes = selected.documents.zip(observed).map { (document, text) =>
        val content = if (document.updateSourceSections) updateSource(text.get, request.snapshot, document)
          else document.childDocument.get
        val mode = if (document.updateSourceSections) FileWriteMode.UpdateExisting(text.get) else FileWriteMode.CreateNew
        (document.relativePath, content, mode)
      }.reverse
      writes.foldLeft(Consequence.success(Option.empty[String])) { case (prior, (name, content, mode)) =>
        prior.flatMap {
          case conflict @ Some(_) => Consequence.success(conflict)
          case None => WorkspaceFilePath.parseC(name).flatMap { path =>
            WorkflowFileExecution.writeTextC(core, target, path, content, mode) match {
              case Consequence.Success(value) => (mode, value.outcome) match {
                case (FileWriteMode.CreateNew, FileWriteOutcome.Created) => Consequence.success(None)
                case (FileWriteMode.UpdateExisting(_), FileWriteOutcome.Updated) => Consequence.success(None)
                case (_, FileWriteOutcome.Conflict(_)) => Consequence.success(Some(name))
                case _ => uncertain("typed installation outcome differs from the selected edit")
              }
              case _ => uncertain("write was attempted without an established result; no replay")
            }
          }
        }
      }.map {
        case Some(path) => result(request, selected, Status.Conflict, Vector(path))
        case None => result(request, selected, Status.Applied, Vector.empty)
      }
    }
  } yield result

  private def targetC(core: ActionCall.Core, repository: String): Consequence[FileTarget] = for {
    configuration <- Option(core).flatMap(_.component).flatMap(_.subsystem).map(_.configuration)
      .filter(_ != null).map(Consequence.success).getOrElse(invalid("owning Subsystem configuration is absent"))
    configuredRepository <- exactC(configuration.configuration.values, repositoryKey)
    _ <- if (repository == configuredRepository) Consequence.unit else invalid("repository is not configured")
    workspace <- exactC(configuration.configuration.values, workspaceKey)
    identity <- WorkspaceId.parseC(workspace)
  } yield FileTarget(identity)

  private def exactC(values: Map[String, ConfigurationValue], key: String): Consequence[String] =
    GoalPhaseHostRecordReadPolicy.storedValue(values, key) match {
      case Some(ConfigurationValue.StringValue(value)) if value != null && value.nonEmpty && value.trim == value =>
        Consequence.success(value)
      case _ => invalid("requires an explicit exact repository/workspace association")
    }

  private def readC(core: ActionCall.Core, target: FileTarget, selected: D.Projection): Consequence[Vector[Option[String]]] =
    selected.documents.foldLeft(Consequence.success(Vector.empty[Option[String]])) { (prior, document) => for {
      values <- prior
      path <- WorkspaceFilePath.parseC(document.relativePath)
      value <- WorkflowFileExecution.readTextC(core, target, path)
    } yield values :+ value.text }

  private def observation(request: Request, selected: D.Projection, states: Vector[State], applying: Boolean): Observation = {
    val inconsistent = states.head == State.Applied && states.tail.exists(_ != State.Applied)
    val conflicts = selected.documents.zip(states).collect {
      case (document, State.Conflict) => document.relativePath
      case (document, State.Applied) if applying => document.relativePath
      case (document, state) if inconsistent && state != State.Applied => document.relativePath
    }.distinct
    val status = if (conflicts.nonEmpty || inconsistent) Status.Conflict
      else if (states.forall(_ == State.Applied)) Status.Applied
      else if (states.contains(State.Applied)) Status.Partial else Status.Ready
    result(request, selected, status, conflicts)
  }

  private def result(request: Request, selected: D.Projection, status: Status, conflicts: Vector[String]): Observation =
    Observation(status, Json.obj("schemaVersion" -> Json.fromString(resultSchema),
      "instanceIdentity" -> Json.fromString(request.instance),
      "context" -> Json.obj("identity" -> Json.fromString(request.context), "revision" -> Json.fromString(request.revision)),
      "repositoryIdentity" -> Json.fromString(request.repository),
      "source" -> selected.json.hcursor.downField("source").focus.get,
      "proposal" -> selected.json.hcursor.downField("proposal").focus.get,
      "status" -> Json.fromString(status.toString.toLowerCase(java.util.Locale.ROOT)),
      "paths" -> Json.fromValues(selected.documents.map(d => Json.fromString(d.relativePath))),
      "conflictingPaths" -> Json.fromValues(conflicts.map(Json.fromString))))

  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Selected document File " + reason)
  private def uncertain[A](reason: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate("Selected document File " + reason)

  private enum State { case Ready, Applied, Conflict }
  private def classify(snapshot: R.Record, document: D.Document, text: Option[String]): State = text match {
    case None => if (document.updateSourceSections) State.Conflict else State.Ready
    case Some(value) =>
      val selected = block(value, D.childSchema)
      val allItems = snapshot.selection.boundary.boundary.items.map(_.identity.value).toSet
      val rows = scopeRows(value)
      val owned = document.ownedItems.map(_.value)
      val expectedrows = scopeRows(document.sections).getOrElse(Vector.empty)
      val selectedRows = rows.exists(r => r.filter(row => allItems(row.identity)) == expectedrows &&
        expectedrows.map(_.identity) == owned &&
        owned.forall(id => r.count(_.identity == id) == 1) &&
        r.filterNot(row => allItems(row.identity)).forall(_.completed))
      if (selected.contains(document.contract) && selectedRows) State.Applied
      else if (!document.updateSourceSections || value.split("\n", -1).contains("```" + D.childSchema)) State.Conflict
      else {
        import org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPartitionRawCodecs.given
        import io.circe.syntax.*
        val boundary = snapshot.selection.boundary.boundary
        val current = scopeRows(value)
        if (block(value, sourceSchema).contains(boundary.asJson) &&
          current.exists(rows => rows.filter(row => allItems(row.identity)).map(_.identity) == boundary.items.map(_.identity.value) &&
            rows.filter(row => allItems(row.identity)).forall(row => !row.completed) &&
            rows.filterNot(row => allItems(row.identity)).forall(_.completed)) &&
          boundary.items.forall(i => current.exists(_.count(_.identity == i.identity.value) == 1))) State.Ready
        else State.Conflict
      }
  }

  private final case class ScopeRow(identity: String, criterion: String, status: String) {
    def completed: Boolean = Set("complete", "completed", "done", "accepted").contains(status.toLowerCase(java.util.Locale.ROOT))
  }
  private def scopeRows(text: String): Option[Vector[ScopeRow]] = {
    val lines = text.split("\n", -1).toVector
    val start = lines.indexOf("## Scope")
    if (start < 0 || lines.count(_ == "## Scope") != 1) None
    else {
      val end = lines.indexWhere(_.startsWith("## "), start + 1) match { case -1 => lines.size; case n => n }
      val rows = lines.slice(start + 1, end).filter(_.startsWith("|"))
      if (rows.size < 2 || !rows.head.matches("\\|\\s*ID\\s*\\|.*") || !rows(1).contains("---")) None
      else {
        val cells = rows.drop(2).map(_.split("\\|", -1).toVector)
        if (cells.exists(_.size != 5)) None
        else Some(cells.map(c => ScopeRow(unescape(c(1).trim), unescape(c(2).trim), c(3).trim)))
      }
    }
  }

  private def updateSource(original: String, snapshot: R.Record, document: D.Document): String = {
    val lines = original.split("\n", -1).toVector
    val start = lines.indexOf("## Scope")
    val end = lines.indexWhere(_.startsWith("## "), start + 1) match { case -1 => lines.size; case n => n }
    val unfinished = snapshot.selection.boundary.boundary.items.map(_.identity.value).toSet
    // The source contract has already admitted the table shape. Exclude its
    // two header rows by position, never by a completed item's identity text.
    val keptRows = lines.slice(start + 1, end).filter(_.startsWith("|")).drop(2).filter(line =>
      line.split("\\|", -1).lift(1).map(s => unescape(s.trim)).exists(id => !unfinished.contains(id)))
    val generated = document.sections.split("\n", -1).toVector
    val nextSection = generated.indexWhere(_.startsWith("## "), 1)
    val sections = (generated.take(nextSection).filterNot(_.isEmpty) ++ keptRows ++ Vector("") ++ generated.drop(nextSection)).mkString("\n")
    // Source's original boundary is historical application data; keep it with
    // unrelated headings and history rather than copying or sealing its bytes.
    (lines.take(start) ++ Vector(sections) ++ lines.drop(end)).mkString("\n").stripSuffix("\n") + "\n"
  }

  private def block(text: String, schema: String): Option[Json] = {
    val lines = text.split("\n", -1).toVector
    val opening = "```" + schema
    val starts = lines.zipWithIndex.collect { case (line, i) if line == opening => i }
    starts match {
      case Vector(start) =>
        val end = lines.indexOf("```", start + 1)
        if (end > start) io.circe.parser.parse(lines.slice(start + 1, end).mkString("\n")).toOption else None
      case _ => None
    }
  }
  private def unescape(value: String): String = value.replace("<br>", "\n").replace("&#96;", "`")
    .replace("&#124;", "|").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}
