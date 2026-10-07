package org.simplemodeling.textus.smworkflow.runtime

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.{CodingErrorAction, StandardCharsets as CS}
import java.nio.file.{Files, LinkOption as L, Path, StandardCopyOption as M, StandardOpenOption as O}
import io.circe.{Json, parser}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.simplemodeling.textus.smworkflow.admission.RepositorySyncNativeConflictInput as I

/** Host-owned cooperative file writer. This starts no subprocess, owns no DB,
  * and receives no executable/root/grant from semantic or public input.
  * Per-file atomic replacement is not multi-file or Git/DB atomicity.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
object RepositoryConflictFileProgram {
  val capability = "sm-workflow-repository-conflict-files"
  val schema = "sm-workflow.repository-conflict-files.v1"
  final case class File(path: String, expected: Option[String], replacement: String)
  final case class Request(instance: String, revision: String, files: Vector[File])
  final case class Result(instance: String, revision: String, status: String, files: Vector[File])
  private val maxRequest = 16 * 1024 * 1024

  private[smworkflow] def definitionsC(root: Path, paths: Set[String], bytes: Int,
    executable: String, classpath: String, limits: ProcessExecutionLimits): Consequence[Vector[ProcessProgramDefinition]] = for {
    _ <- if (root != null && root.isAbsolute && root.normalize == root && paths != null &&
        paths.nonEmpty && paths.size <= 128 && paths.forall(I.relative) && bytes > 0 && bytes <= 262144 &&
        classpath != null && classpath.nonEmpty) Consequence.unit
      else Consequence.stateConflict("Repository conflict file runtime target/policy is incomplete")
    id <- ProcessCapabilityId.parseC(capability)
    fixed = Vector("-cp", classpath, "org.simplemodeling.textus.smworkflow.runtime.RepositoryConflictFileProgram",
      root.toString, Json.fromValues(paths.toVector.sorted.map(Json.fromString)).noSpaces, bytes.toString)
    definition <- ProcessProgramDefinition.fromRuntimeC(id, capability, executable, fixed,
      ProcessArgumentPolicy(fixed, permittedArgumentVectors = Set(Vector("capture"), Vector("observe"), Vector("apply"))),
      limits, Set.empty)
  } yield Vector(definition)

  private[smworkflow] def encode(request: Request): String =
    Json.obj("schemaVersion" -> Json.fromString(schema), "request" -> request.asJson).noSpaces
  private[smworkflow] def decodeRequest(json: Json): Either[String, Request] = for {
    _ <- Either.cond(json.asObject.exists(_.keys.toSet == Set("schemaVersion", "request")) &&
      json.hcursor.get[String]("schemaVersion").contains(schema), (), "file intent envelope differs")
    request <- json.hcursor.get[Request]("request").left.map(_.message)
    _ <- Either.cond(parser.parse(encode(request)).contains(json), (), "file intent shape differs")
  } yield request
  private[smworkflow] def decodeResult(json: Json): Either[String, Result] = for {
    _ <- Either.cond(json.asObject.exists(_.keys.toSet == Set("schemaVersion", "result")) &&
      json.hcursor.get[String]("schemaVersion").contains(schema), (), "file observation envelope differs")
    result <- json.hcursor.get[Result]("result").left.map(_.message)
    _ <- Either.cond(resultJson(result) == json, (), "file observation shape differs")
  } yield result
  private def resultJson(result: Result): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "result" -> result.asJson)

  def main(args: Array[String]): Unit = try {
    require(args.length == 4, "fixed arguments required")
    val paths = parser.parse(args(1)).flatMap(_.as[Vector[String]]).toOption.get.toSet
    val bytes = System.in.readNBytes(maxRequest + 1)
    require(bytes.length <= maxRequest, "request budget")
    val request = decodeRequest(parser.parse(text(bytes)).toOption.get).toOption.get
    val result = run(Path.of(args(0)), paths, args(2).toInt, args(3), request)
    System.out.println(resultJson(result).noSpaces)
  } catch { case scala.util.control.NonFatal(_) =>
    System.err.println("repository-conflict-files: invalid input or unavailable filesystem operation")
    System.exit(2)
  }

  private[smworkflow] def run(root: Path, allowed: Set[String], limit: Int, action: String, request: Request): Result = {
    require(root != null && root.isAbsolute && root.normalize == root && safe(root) &&
      Files.isDirectory(root.resolve(".git"), L.NOFOLLOW_LINKS) && safe(root.resolve(".git")), "fixed ordinary Git root required")
    require(Set("capture", "observe", "apply")(action) && allowed.nonEmpty && allowed.size <= 128 &&
      allowed.forall(I.relative) && limit > 0 && limit <= 262144, "fixed policy required")
    require(request != null && request.instance != null && request.instance.nonEmpty &&
      request.revision != null && request.revision.nonEmpty && request.files != null && request.files.nonEmpty &&
      request.files.size <= 128 && request.files.forall(f => f != null && allowed(f.path) &&
        valid(f.replacement, limit) && f.expected != null && f.expected.forall(valid(_, limit))) &&
      request.files.map(_.path).distinct.size == request.files.size &&
      request.files.forall(f => f.expected.isEmpty == (action == "capture")), "assigned file intent required")
    val lockPath = root.resolve(".git/sm-workflow-conflict-files.lock")
    require(safe(lockPath) && (!Files.exists(lockPath, L.NOFOLLOW_LINKS) || Files.isRegularFile(lockPath, L.NOFOLLOW_LINKS)), "unsafe lock")
    val channel = FileChannel.open(lockPath, O.CREATE, O.WRITE, L.NOFOLLOW_LINKS)
    try {
      val lock = channel.lock()
      try {
        val current = request.files.map(f => read(root.resolve(f.path), limit))
        if (action == "capture") Result(request.instance, request.revision, "captured",
          request.files.zip(current).map((f, value) => f.copy(expected = Some(value))))
        else {
          val states = request.files.zip(current).map((f, value) =>
            if (value == f.replacement) "applied" else if (f.expected.contains(value)) "ready" else "conflict")
          if (states.contains("conflict")) Result(request.instance, request.revision, "conflict", request.files)
          else if (states.forall(_ == "applied")) Result(request.instance, request.revision, "applied", request.files)
          else if (action == "observe") Result(request.instance, request.revision,
            if (states.contains("applied")) "partial" else "ready", request.files)
          else {
            request.files.zip(states).foreach { (file, state) => if (state != "applied") {
              val path = root.resolve(file.path)
              require(file.expected.contains(read(path, limit)), "overwrite precondition differs")
              val staged = Files.createTempFile(path.getParent, ".sm-workflow-conflict-", ".tmp")
              try {
                val output = FileChannel.open(staged, O.WRITE)
                try {
                  val buffer = ByteBuffer.wrap(file.replacement.getBytes(CS.UTF_8))
                  while (buffer.hasRemaining) output.write(buffer)
                  output.force(true)
                } finally output.close()
                require(safe(path) && file.expected.contains(read(path, limit)), "overwrite precondition differs")
                Files.setPosixFilePermissions(staged, Files.getPosixFilePermissions(path, L.NOFOLLOW_LINKS))
                Files.move(staged, path, M.ATOMIC_MOVE, M.REPLACE_EXISTING)
              } finally Files.deleteIfExists(staged)
            }}
            require(request.files.forall(f => read(root.resolve(f.path), limit) == f.replacement), "application readback differs")
            Result(request.instance, request.revision, "applied", request.files)
          }
        }
      } finally lock.release()
    } finally channel.close()
  }
  private def valid(value: String, limit: Int): Boolean = value != null && !value.contains('\u0000') && value.getBytes(CS.UTF_8).length <= limit
  private def safe(path: Path): Boolean = {
    val parts = path.iterator()
    var current = path.getRoot
    var result = true
    while (parts.hasNext && result) { current = current.resolve(parts.next()); result = !Files.isSymbolicLink(current) }
    result
  }
  private def read(path: Path, limit: Int): String = {
    require(safe(path) && Files.isRegularFile(path, L.NOFOLLOW_LINKS) && Files.size(path) <= limit, "unsafe text file")
    val channel = FileChannel.open(path, O.READ, L.NOFOLLOW_LINKS)
    val buffer = ByteBuffer.allocate(limit + 1)
    try { while (buffer.hasRemaining && channel.read(buffer) >= 0) (); buffer.flip() }
    finally channel.close()
    require(buffer.remaining() <= limit, "text budget")
    val value = CS.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT).decode(buffer).toString
    require(valid(value, limit), "binary text")
    value
  }
  private def text(bytes: Array[Byte]): String = CS.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString
}
