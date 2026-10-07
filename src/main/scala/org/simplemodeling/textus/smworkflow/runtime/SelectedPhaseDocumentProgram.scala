package org.simplemodeling.textus.smworkflow.runtime

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, LinkOption, Path, StandardCopyOption, StandardOpenOption}
import io.circe.Json
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningSnapshotJsonV1 as J,
  SplitPhasePlanningSnapshot as R, SplitPhaseSelectedDocuments as D}

/** Trusted runtime program, not a component Operation or a selectable shell.
  * Runtime binds root and repository identity in its fixed executable prefix.
  * Only the final literal observe/apply argument is admitted by capability policy.
  * Each file is atomic; the write set is NOT crash-atomic. Partial installations
  * remain inspectable and only the same selected plan's missing files are resumed.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
object SelectedPhaseDocumentProgram {
  val requestSchema = "sm-workflow.selected-documents-request.v1"
  val resultSchema = "sm-workflow.selected-documents-effect.v1"
  val sourceSchema = "sm-workflow.split-phase-source.v1"
  private val maximumBytes = 2 * 1024 * 1024

  /** No root, executable or argv comes from the input JSON. This is application
    * data, not a copied-content permission seal or a claim of native authority.
    */
  private[smworkflow] def requestC(repository: String, instance: String,
    context: String, revision: String, snapshot: R.Record): Consequence[String] =
    if (Vector(repository, instance, context, revision).exists(s => s == null || s.isEmpty || s.trim != s))
      Consequence.stateConflict("Selected document effect references are incomplete")
    else J.encodeC(snapshot).map(encoded => Json.obj(
      "schemaVersion" -> Json.fromString(requestSchema),
      "repositoryIdentity" -> Json.fromString(repository),
      "instanceIdentity" -> Json.fromString(instance),
      "context" -> Json.obj("identity" -> Json.fromString(context), "revision" -> Json.fromString(revision)),
      "snapshot" -> Json.fromString(encoded)).noSpaces)

  def main(args: Array[String]): Unit = {
    try {
      require(args.length == 3 && Set("observe", "apply").contains(args(2)), "fixed runtime arguments required")
      val bytes = System.in.readNBytes(maximumBytes + 1)
      require(bytes.length <= maximumBytes, "request limit exceeded")
      val request = io.circe.parser.parse(decode(bytes)).fold(error => throw new IllegalArgumentException(error.message), identity)
      val result = run(Path.of(args(0)), args(1), args(2), request)
      System.out.println(result.noSpaces)
    } catch {
      case scala.util.control.NonFatal(_) =>
        // Structured failure, with no host path, input or document contents leaked.
        System.err.println("selected-document-program: invalid input or unavailable filesystem operation")
        System.exit(2)
    }
  }

  private[smworkflow] def run(rootInput: Path, repository: String, action: String, request: Json): Json = {
    require(Set("observe", "apply").contains(action), "unknown action")
    val cursor = request.hcursor
    require(cursor.get[String]("schemaVersion").contains(requestSchema), "request schema differs")
    require(cursor.get[String]("repositoryIdentity").contains(repository), "fixed repository differs")
    val instance = cursor.get[String]("instanceIdentity").toOption.filter(_.nonEmpty).getOrElse(throw new IllegalArgumentException("instance missing"))
    val context = cursor.downField("context").focus.getOrElse(throw new IllegalArgumentException("context missing"))
    require(context.hcursor.get[String]("identity").exists(_.nonEmpty) &&
      context.hcursor.get[String]("revision").exists(_.nonEmpty), "context incomplete")
    val snapshot = take(J.decodeC(cursor.get[String]("snapshot").toOption.getOrElse(throw new IllegalArgumentException("snapshot missing"))))
    val selected = take(D.projectC(snapshot))
    val repositories = selected.documents.map(_.contract.hcursor.downField("validation")
      .downField("repository").get[String]("value").toOption.get).distinct
    require(repositories == Vector(repository), "selected repository differs from fixed target")
    val root = rootInput.toAbsolutePath.normalize
    val directory = root.resolve("docs/phase")
    // Walk every existing ancestor: rejecting only the final path is not enough.
    require(safeAncestors(root) && safeAncestors(directory) && Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS), "unsafe root")
    val lockPath = directory.resolve(".sm-workflow-document.lock")
    require(!Files.isSymbolicLink(lockPath) && (!Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS) ||
      Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)), "unsafe lock")
    val channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
    try {
      val lock = channel.lock()
      try {
        val paths = selected.documents.map(d => root.resolve(d.relativePath))
        val observed = paths.map(read)
        val states = selected.documents.zip(observed).map { (document, text) => classify(snapshot, document, text) }
        val invalidFinal = states.head == State.Applied && states.tail.exists(_ != State.Applied)
        val conflicts = selected.documents.zip(states).collect {
          case (d, State.Conflict) => d.relativePath
          case (d, State.Ready) if invalidFinal => d.relativePath
        }
        if (conflicts.nonEmpty) result(instance, context, repository, selected, "conflict", conflicts)
        else if (states.forall(_ == State.Applied)) result(instance, context, repository, selected, "applied", Vector.empty)
        else if (action == "observe") result(instance, context, repository, selected,
          if (states.contains(State.Applied)) "partial" else "ready", Vector.empty)
        else {
          // Preflight all files before any write. Child creation never replaces an
          // occupied file. Source is installed last, preserving unrelated sections.
          selected.documents.zip(paths).zip(observed).zip(states).reverse.foreach { case (((document, path), old), state) =>
            if (state != State.Applied) {
              val content = if (document.updateSourceSections) {
                val current = read(path)
                require(classify(snapshot, document, current) == State.Ready, "source contract changed")
                updateSource(current.get, snapshot, document)
              }
                else document.childDocument.get
              require(content.getBytes(StandardCharsets.UTF_8).length <= maximumBytes, "document limit exceeded")
              val staged = Files.createTempFile(directory, ".sm-workflow-stage-", ".tmp")
              try {
                val output = FileChannel.open(staged, StandardOpenOption.WRITE)
                try {
                  val buffer = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8))
                  while (buffer.hasRemaining) output.write(buffer)
                  output.force(true)
                } finally output.close()
                if (document.updateSourceSections) {
                  // This is a cooperative writer, not a claim of safety against
                  // arbitrary concurrent editors that ignore the shared lock.
                  require(classify(snapshot, document, read(path)) == State.Ready, "source contract changed")
                  Files.setPosixFilePermissions(staged, Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS))
                  Files.move(staged, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } else Files.createLink(path, staged)
              } finally Files.deleteIfExists(staged)
            }
          }
          require(selected.documents.zip(paths).forall((d, path) => classify(snapshot, d, read(path)) == State.Applied), "incomplete readback")
          result(instance, context, repository, selected, "applied", Vector.empty)
        }
      } finally lock.release()
    } finally channel.close()
  }

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
  private def safeAncestors(path: Path): Boolean = {
    val iterator = path.iterator()
    var current = path.getRoot
    var safe = true
    while (iterator.hasNext && safe) {
      current = current.resolve(iterator.next())
      safe = !Files.isSymbolicLink(current)
    }
    safe
  }
  private def read(path: Path): Option[String] = {
    require(!Files.isSymbolicLink(path), "symlink document")
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) None
    else {
      require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= maximumBytes, "unsafe document")
      val bytes = Files.readAllBytes(path)
      require(bytes.length <= maximumBytes, "document limit exceeded")
      Some(decode(bytes))
    }
  }
  private def decode(bytes: Array[Byte]): String = StandardCharsets.UTF_8.newDecoder()
    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    .decode(ByteBuffer.wrap(bytes)).toString
  private def unescape(value: String): String = value.replace("<br>", "\n").replace("&#96;", "`")
    .replace("&#124;", "|").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
  private def result(instance: String, context: Json, repository: String, selected: D.Projection,
    status: String, conflicts: Vector[String]): Json = Json.obj(
    "schemaVersion" -> Json.fromString(resultSchema), "instanceIdentity" -> Json.fromString(instance),
    "context" -> context, "repositoryIdentity" -> Json.fromString(repository),
    "source" -> selected.json.hcursor.downField("source").focus.get,
    "proposal" -> selected.json.hcursor.downField("proposal").focus.get,
    "status" -> Json.fromString(status), "paths" -> Json.fromValues(selected.documents.map(d => Json.fromString(d.relativePath))),
    "conflictingPaths" -> Json.fromValues(conflicts.map(Json.fromString)))
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case _ => throw new IllegalArgumentException("invalid selected data")
  }
}
