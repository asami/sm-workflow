/**
 * @since   Nov. 21, 2026
 * @version Nov. 21, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{ProcessExecutionCapture, ProcessExecutionResult, ProcessExecutionTermination}
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseCompilePackageSpec extends AnyWordSpec with Matchers {
  import GoalPhaseCompilePackage as P
  import GoalPhaseNativeValidation as N
  import GoalPhaseOrderedProgression.Snapshot
  import IncrementalSbtDiagnosisInformation.Selection
  import RepositorySyncNativeMergeValidation.Receipt
  import WorkflowSbtValidation.{Facts, Kind, Outcome}

  private val _root = Path.of("/fixture/repository")
  private val _candidate = Snapshot("a" * 40, "b" * 40)
  private val _reader = "src/main/scala/fixture/Reader.scala"
  private val _interface = "src/main/scala/fixture/Interface.scala"
  private val _out = "[error] -- [E007] Type Mismatch Error: /fixture/repository/" + _reader +
    ":9:2\n[error] Found: String\n[error] Required: Int\n"
  private val _err = "[error] (Compile / compileIncremental) Compilation failed\n"
  private val _package = Selection("package", P.kind, "Reader and its contract are complete for this failure",
    Some("compile"), None, None, None)
  private def file(path: String, id: String) =
    Selection(id, P.sourceKind, "Complete affected declaration", None, None, None, Some(path))
  private val _selections = Vector(file(_interface, "interface"), _package, file(_reader, "reader"))

  private def capture(s: String, truncated: Boolean = false) = {
    val bytes = s.getBytes(UTF_8).toVector
    ProcessExecutionCapture(bytes, bytes.size.toLong, truncated)
  }

  private def material(truncated: Boolean = false) = {
    val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(1), capture(_out, truncated),
      capture(_err), Vector.empty, 7L, "compile-fixture")
    val report = Receipt("compile", "exited", Some(1), _out, _err,
      Some(Facts(Kind.Compile, Outcome.Failed, None, None)), Some(process))
    val validation = N.Payload("8", "S1", _candidate, Vector(N.Check("compile", 1, Kind.Compile)),
      Vector(report), Some("repair-required"))
    P.Material("session", "work", "9", "Repair selected compile failure", "validated-candidate", validation,
      Vector("src"), Json.obj("step" -> Json.fromString("S1")))
  }

  private def sources(first: String, second: String) =
    Vector(P.Source(_reader, "c" * 40, first), P.Source(_interface, "d" * 40, second))

  "Compile package preparation" should {
    "include complete candidate blobs, both captures and the adopted work contract in a deterministic byte bound" in {
      val files = sources("package fixture\nobject Reader\n", "package fixture\ntrait Interface\n")
      def read(tree: String, paths: Vector[String]) = {
        tree shouldBe _candidate.tree
        paths shouldBe Vector(_interface, _reader)
        Consequence.success(files)
      }
      val ready = P.prepareSelectionC(Some(material()), _root, _selections, 4096, read).toOption.get
      ready.map(_.selection) shouldBe _selections
      val item = ready.find(_.selection == _package).get
      val packet = item.text.get
      val decoded = io.circe.parser.parse(packet).toOption.get.hcursor
      decoded.downField("diagnostics").get[String]("stdout") shouldBe Right(_out)
      decoded.downField("diagnostics").get[String]("stderr") shouldBe Right(_err)
      decoded.downField("contract").get[String]("step") shouldBe Right("S1")
      val supplied = decoded.get[Vector[Json]]("files").toOption.get
      supplied.flatMap(_.hcursor.get[String]("content").toOption) shouldBe files.sortBy(_.path).map(_.text)
      val bytes = packet.getBytes(UTF_8).length
      bytes should be < 4096
      P.prepareSelectionC(Some(material()), _root, _selections, bytes, read).toOption.get shouldBe ready
      val larger = P.prepareSelectionC(Some(material()), _root, _selections, bytes - 1, read).toOption.get(1)
      larger.coverage shouldBe "agent-required"
      larger.text shouldBe None
      larger.hygiene shouldBe empty
    }

    "keep incomplete and crossed inputs on the existing editor route" in {
      val files = sources("final class Reader", "trait Interface")
      var reads = 0
      val reader = (_: String, _: Vector[String]) => { reads += 1; Consequence.success(files) }
      val missingAnchor = P.prepareSelectionC(Some(material()), _root, _selections.take(2), 4096, reader).toOption.get
      missingAnchor.map(_.coverage).distinct shouldBe Vector("agent-required")
      val truncated = P.prepareSelectionC(Some(material(truncated = true)), _root, _selections, 4096, reader).toOption.get
      truncated.flatMap(_.text) shouldBe empty
      reads shouldBe 0
      val missing = P.prepareSelectionC(Some(material()), _root, _selections, 4096,
        (_, _) => Consequence.success(files.take(1))).toOption.get(1)
      missing.coverage shouldBe "agent-required"
      missing.text shouldBe None
    }

    "report a single oversized handwritten source independently of aggregate package excess" in {
      val files = sources("語" * 350, "trait Interface")
      val item = P.prepareSelectionC(Some(material()), _root, _selections, 1024,
        (_, _) => Consequence.success(files)).toOption.get(1)
      item.coverage shouldBe "agent-required"
      item.hygiene shouldBe Vector(P.Hygiene(_reader, 1050, "c" * 40, 1024))
      P._hygiene(files.head.copy(path = "target/generated-sources/Reader.scala"), 1024) shouldBe None
      P._hygiene(files.head.copy(path = "src/data/dictionary.json"), 1024) shouldBe None
      P._hygiene(files.head.copy(text = "// Automatically generated\n" + files.head.text), 1024) shouldBe None
      val combined = P.prepareSelectionC(Some(material()), _root, _selections, 1024,
        (_, _) => Consequence.success(sources("a" * 800, "b" * 800))).toOption.get(1)
      combined.coverage shouldBe "agent-required"
      combined.hygiene shouldBe empty
    }
  }

  "The source-size Journal" should {
    "deduplicate the same repository, path and blob across Sessions without mutating the first observation" in {
      import DevelopmentJournalSemantic as J
      val fact = P.Hygiene(_reader, 554000, "c" * 40, 524288)
      val now = Instant.parse("2026-11-21T12:00:00Z")
      def entry(session: String, observation: P.Hygiene) =
        J.sourceSizeEntryC("repo", "9", Some("S1"), session, J.Source(session + ":validation", "1"), observation, now).toOption.get
      var row = Option.empty[Record]
      var creates = 0
      val store = new DevelopmentJournal.Storage {
        def loadC() = Consequence.success(row)
        def createC(value: Record) = { row = Some(value); creates += 1; Consequence.unit }
      }
      val original = entry("original", fact)
      val later = entry("later", fact.copy(threshold = 500000))
      original.identity shouldBe later.identity
      J.appendC(store, original).toOption.get.status shouldBe "recorded"
      J.appendC(store, later).toOption.get.status shouldBe "alreadyRecorded"
      J.storedC(row.get, original.identity).toOption.get shouldBe original
      creates shouldBe 1
      entry("later", fact.copy(blob = "d" * 40)).identity should not be original.identity
    }
  }
}
