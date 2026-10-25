package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.io.File
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import org.scalatest.{GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Opt-in evidence for ordinary CAR admission against one materialized runtime.
  * This diagnostic neither selects an installation nor operates on a Session.
  * @since   Oct. 22, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeInstalledCarAdmissionDiagnosticSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private final case class Cleanup(terminated: Boolean, evidence: Json)
  private final case class StackCapture(evidence: Json, failure: Option[String])

  private def _required_path(property: String): Path = {
    val path = Path.of(sys.props.getOrElse(property,
      throw new IllegalArgumentException("Missing system property " + property)))
    require(path.isAbsolute, property + " must be absolute")
    path.normalize()
  }

  private def _json(path: Path): Json = parse(Files.readString(path)).fold(
    error => throw new IllegalArgumentException(path.toString + ": " + error.message), identity)

  private def _error(cause: Throwable): String =
    cause.getClass.getName + ": " + Option(cause.getMessage).getOrElse("")

  private def _exit(process: Process): Json =
    if (process.isAlive) Json.Null else Json.fromInt(process.exitValue())

  private def _strings(values: Vector[String]): Json = Json.arr(values.map(Json.fromString)*)

  private def _pause_until(handles: Vector[ProcessHandle], deadline: Long): Unit = {
    while (handles.exists(_.isAlive) && System.nanoTime() < deadline) {
      val remaining = deadline - System.nanoTime()
      if (remaining > 0L) TimeUnit.NANOSECONDS.sleep(math.min(remaining, TimeUnit.MILLISECONDS.toNanos(25L)))
    }
  }

  /** Snapshot and terminate only this exact Process and its own descendants. */
  private def _cleanup(process: Process, includeDescendants: Boolean): Cleanup = {
    val started = Instant.now().toString
    var errors = Vector.empty[String]
    var descendants = Vector.empty[ProcessHandle]
    if (includeDescendants) {
      try {
        val stream = process.toHandle.descendants()
        try descendants = stream.iterator().asScala.toVector
        finally stream.close()
      } catch {
        case NonFatal(cause) => errors :+= _error(cause)
      }
    }
    val handles = (descendants.reverse :+ process.toHandle).distinctBy(_.pid())
    var graceful = Vector.empty[Long]
    var forced = Vector.empty[Long]
    handles.foreach { handle =>
      try {
        if (handle.isAlive) {
          graceful :+= handle.pid()
          handle.destroy()
        }
      } catch {
        case NonFatal(cause) => errors :+= _error(cause)
      }
    }
    try _pause_until(handles, System.nanoTime() + TimeUnit.SECONDS.toNanos(2L))
    catch { case NonFatal(cause) => errors :+= _error(cause) }
    handles.foreach { handle =>
      try {
        if (handle.isAlive) {
          forced :+= handle.pid()
          handle.destroyForcibly()
        }
      } catch {
        case NonFatal(cause) => errors :+= _error(cause)
      }
    }
    try _pause_until(handles, System.nanoTime() + TimeUnit.SECONDS.toNanos(10L))
    catch { case NonFatal(cause) => errors :+= _error(cause) }
    var reaped = false
    try reaped = process.waitFor(2L, TimeUnit.SECONDS)
    catch { case NonFatal(cause) => errors :+= _error(cause) }
    val alive = handles.filter(_.isAlive).map(_.pid())
    val terminated = reaped && alive.isEmpty && errors.isEmpty
    Cleanup(terminated, Json.obj(
      "started_at" -> Json.fromString(started),
      "finished_at" -> Json.fromString(Instant.now().toString),
      "descendant_pids" -> Json.arr(descendants.map(p => Json.fromLong(p.pid()))*),
      "graceful_requested_pids" -> Json.arr(graceful.map(Json.fromLong)*),
      "force_requested_pids" -> Json.arr(forced.map(Json.fromLong)*),
      "remaining_alive_pids" -> Json.arr(alive.map(Json.fromLong)*),
      "reaped" -> Json.fromBoolean(reaped),
      "terminated" -> Json.fromBoolean(terminated),
      "errors" -> _strings(errors)))
  }

  private def _stack(jstack: Path, child: Process, cwd: Path, evidence: Path, ordinal: Int): StackCapture = {
    val argv = Vector(jstack.toString, child.pid().toString)
    val stdout = evidence.resolve(s"jstack-$ordinal.stdout.log")
    val stderr = evidence.resolve(s"jstack-$ordinal.stderr.log")
    val started = Instant.now().toString
    var owned = Option.empty[Process]
    var failure = Option.empty[String]
    var timedout = false
    var exitBeforeCleanup = Json.Null
    var cleanup = Option.empty[Cleanup]
    try {
      val process = new ProcessBuilder(argv.asJava).directory(cwd.toFile)
        .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      owned = Some(process)
      process.getOutputStream.close()
      timedout = !process.waitFor(10L, TimeUnit.SECONDS)
      exitBeforeCleanup = _exit(process)
    } catch {
      case NonFatal(cause) => failure = Some(_error(cause))
    } finally {
      cleanup = owned.map(process => _cleanup(process, includeDescendants = false))
    }
    val cleanupFailed = cleanup.exists(!_.terminated)
    val outcomeFailure = failure.orElse(Option.when(cleanupFailed)("jstack cleanup did not complete"))
    StackCapture(Json.obj(
      "argv" -> _strings(argv),
      "cwd" -> Json.fromString(cwd.toString),
      "target_pid" -> Json.fromLong(child.pid()),
      "owned_pid" -> owned.fold(Json.Null)(p => Json.fromLong(p.pid())),
      "started_at" -> Json.fromString(started),
      "finished_at" -> Json.fromString(Instant.now().toString),
      "stdout" -> Json.fromString(stdout.toString),
      "stderr" -> Json.fromString(stderr.toString),
      "timed_out" -> Json.fromBoolean(timedout),
      "exit_before_cleanup" -> exitBeforeCleanup,
      "exit_after_cleanup" -> owned.fold(Json.Null)(_exit),
      "cleanup" -> cleanup.fold(Json.Null)(_.evidence),
      "failure" -> outcomeFailure.fold(Json.Null)(Json.fromString)), outcomeFailure)
  }

  "An explicitly supplied installed CAR" should {
    "retain bounded admission output and owned-process stacks before reporting its result" in {
      Given("an existing manifest and a fresh absolute evidence directory supplied through system properties")
      val producer = _required_path("user.dir").toRealPath()
      val target = producer.resolve("target").toRealPath()
      val manifest = _required_path("smworkflow.spec.admissionManifest")
      Files.isRegularFile(manifest) shouldBe true
      val directory = manifest.getParent
      val description = _json(manifest)
      val modules = description.hcursor.get[Vector[Json]]("modules").fold(
        error => throw new IllegalArgumentException(error.message), identity)
      val classpath = modules.map { module =>
        val relative = Path.of(module.hcursor.get[String]("path").fold(
          error => throw new IllegalArgumentException(error.message), identity))
        relative.isAbsolute shouldBe false
        val path = directory.resolve(relative).normalize()
        path.startsWith(directory) shouldBe true
        Files.isRegularFile(path) shouldBe true
        path
      }
      classpath should not be empty
      val componentRelative = Path.of(description.hcursor.downField("component").get[String]("path").fold(
        error => throw new IllegalArgumentException(error.message), identity))
      componentRelative.isAbsolute shouldBe false
      val component = directory.resolve(componentRelative).normalize()
      component.startsWith(directory) shouldBe true
      Files.isRegularFile(component) shouldBe true

      val suppliedEvidence = _required_path("smworkflow.spec.admissionEvidence")
      val evidence = suppliedEvidence.getParent.toRealPath().resolve(suppliedEvidence.getFileName)
      evidence.startsWith(target) shouldBe true
      evidence should not be target
      Files.exists(evidence) shouldBe false
      Files.createDirectory(evidence)
      val host = Files.createDirectory(evidence.resolve("isolated-host"))
      val textus = Files.createDirectory(host.resolve(".textus"))
      val producerConfig = producer.resolve(".textus/config.json")
      val producerAssembly = producer.resolve(".textus/assembly.yaml")
      Files.isRegularFile(producerConfig) shouldBe true
      Files.isRegularFile(producerAssembly) shouldBe true
      val config = _json(producerConfig).asObject.getOrElse(
        throw new IllegalArgumentException("Producer config must be a JSON object"))
        .remove("textus.import.data.file")
        .add("textus.datastore.path", Json.fromString(host.resolve("workflow.sqlite").toString))
      Files.writeString(textus.resolve("config.json"), Json.fromJsonObject(config).spaces2,
        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
      val assembly = Files.copy(producerAssembly, textus.resolve("assembly.yaml"))
      val javaHome = _required_path("java.home")
      val java = javaHome.resolve("bin/java")
      val jstack = javaHome.resolve("bin/jstack")
      Files.isRegularFile(java) shouldBe true
      Files.isExecutable(java) shouldBe true
      Files.isRegularFile(jstack) shouldBe true
      Files.isExecutable(jstack) shouldBe true
      val argv = Vector(java.toString, "-Dcncf.installed-runtime.manifest=" + manifest,
        "-cp", classpath.mkString(File.pathSeparator), "org.goldenport.cncf.CncfMain",
        "--force-exit", "--cncf.installed-runtime.manifest=" + manifest,
        "--no-default-components", "--component-file", component.toString,
        "--textus.assembly.descriptor=" + assembly, "command", "meta.help",
        "org.simplemodeling.textus.SmWorkflow")
      val stdout = evidence.resolve("admission.stdout.log")
      val stderr = evidence.resolve("admission.stderr.log")
      val started = Instant.now().toString
      var child = Option.empty[Process]
      var childStarted = Option.empty[String]
      var timedout = false
      var exitBeforeCleanup = Json.Null
      var stacks = Vector.empty[Json]
      var stackFailures = Vector.empty[String]
      var failure = Option.empty[String]
      var cleanup = Option.empty[Cleanup]

      When("ordinary meta.help runs with a 120 second admission budget and bounded owned-child jstack captures")
      try {
        val process = new ProcessBuilder(argv.asJava).directory(host.toFile)
          .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
        child = Some(process)
        childStarted = Some(Instant.now().toString)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120L)
        process.getOutputStream.close()
        if (!process.waitFor(15L, TimeUnit.SECONDS)) {
          val first = _stack(jstack, process, host, evidence, 1)
          stacks :+= first.evidence
          stackFailures ++= first.failure.toVector
          val remaining = deadline - System.nanoTime()
          if (remaining > 0L) process.waitFor(remaining, TimeUnit.NANOSECONDS)
          timedout = process.isAlive
          if (timedout) {
            val last = _stack(jstack, process, host, evidence, 2)
            stacks :+= last.evidence
            stackFailures ++= last.failure.toVector
          }
        }
        exitBeforeCleanup = _exit(process)
      } catch {
        case NonFatal(cause) => failure = Some(_error(cause))
      } finally {
        cleanup = child.map(process => _cleanup(process, includeDescendants = true))
        Files.writeString(evidence.resolve("diagnostic.json"), Json.obj(
          "kind" -> Json.fromString("smworkflow.installed-car-admission-diagnostic.v1"),
          "manifest" -> Json.fromString(manifest.toString),
          "component" -> Json.fromString(component.toString),
          "argv" -> _strings(argv),
          "cwd" -> Json.fromString(host.toString),
          "owned_pid" -> child.fold(Json.Null)(p => Json.fromLong(p.pid())),
          "started_at" -> Json.fromString(started),
          "child_started_at" -> childStarted.fold(Json.Null)(Json.fromString),
          "finished_at" -> Json.fromString(Instant.now().toString),
          "admission_budget_seconds" -> Json.fromInt(120),
          "stdout" -> Json.fromString(stdout.toString),
          "stderr" -> Json.fromString(stderr.toString),
          "timed_out" -> Json.fromBoolean(timedout),
          "exit_before_cleanup" -> exitBeforeCleanup,
          "exit_after_cleanup" -> child.fold(Json.Null)(_exit),
          "jstack" -> Json.arr(stacks*),
          "cleanup" -> cleanup.fold(Json.Null)(_.evidence),
          "failure" -> failure.fold(Json.Null)(Json.fromString)).spaces2,
          StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
      }

      Then("diagnostic evidence precedes assertions about actual termination, cleanup and exit status")
      withClue("Diagnostic evidence: " + evidence + " ") {
        failure shouldBe empty
        stackFailures shouldBe empty
        cleanup.map(_.terminated) shouldBe Some(true)
        timedout shouldBe false
        exitBeforeCleanup shouldBe Json.fromInt(0)
      }
    }
  }
}
