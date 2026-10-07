package org.simplemodeling.textus.smworkflow.admission

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.util.control.NonFatal

import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseCostIsolation.{Demand, Evidence, Policy}

/** Reads cost inputs from a host-owned document bound to a source snapshot.
  * Missing evidence remains pending; malformed or stale evidence fails closed.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseCostEvidenceCatalog {
  val schemaVersion = "sm-workflow.split-phase-cost-evidence.v1"
  private val maximumDocumentBytes = 256 * 1024

  final case class Record(sourceSnapshotDigest: String, documentSha256: String,
                          policy: Policy, demandByItem: Map[String, Demand]) {
    def evidenceFor(consideredDigest: String): Evidence =
      Evidence(sourceSnapshotDigest, consideredDigest, policy, demandByItem)
  }

  def loadC(repositoryRoot: Path, snapshotIdentity: String):
      Consequence[Option[Record]] =
    SplitPhaseSourceSnapshotCatalog.loadC(repositoryRoot, snapshotIdentity).flatMap {
      case None => Consequence.stateConflict(
        "SplitPhase source snapshot catalog is missing")
      case Some(snapshot) => _loadC(repositoryRoot, snapshotIdentity, snapshot)
    }

  private def _loadC(repositoryRoot: Path, snapshotIdentity: String,
                     snapshot: SplitPhaseSourceSnapshotCatalog.Snapshot):
      Consequence[Option[Record]] = try {
    val root = repositoryRoot.toAbsolutePath.normalize
    val directory = root.resolve("docs").resolve("phase").resolve("cost-policies")
    val document = directory.resolve(s"$snapshotIdentity.json")
    if (!Files.exists(document) && !Files.isSymbolicLink(document))
      Consequence.success(None)
    else if (!_safePath(root, directory, document))
      Consequence.stateConflict("SplitPhase cost evidence path is unsafe")
    else if (Files.size(document) > maximumDocumentBytes)
      Consequence.stateConflict("SplitPhase cost evidence exceeds the size limit")
    else {
      val bytes = Files.readAllBytes(document)
      if (bytes.length > maximumDocumentBytes)
        Consequence.stateConflict("SplitPhase cost evidence exceeds the size limit")
      else {
        val decoder = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
        val raw = decoder.decode(ByteBuffer.wrap(bytes)).toString
        _parseC(raw, snapshotIdentity, snapshot, _sha(bytes)).map(Some(_))
      }
    }
  } catch {
    case NonFatal(_) => Consequence.stateConflict(
      "SplitPhase cost evidence could not be read")
  }

  private def _parseC(raw: String, expectedIdentity: String,
                      snapshot: SplitPhaseSourceSnapshotCatalog.Snapshot,
                      documentSha256: String): Consequence[Record] = {
    val decoded = for {
      json <- parse(raw).left.map(_ => "invalid SplitPhase cost evidence JSON")
      fields <- _fields(json, Set("schemaVersion", "sourceSnapshotIdentity",
        "sourceSnapshotDigest", "sourcePhaseIdentity", "sourceAuthorityReference",
        "policy", "itemDemands"))
      schema <- _string(fields, "schemaVersion")
      _ <- Either.cond(schema == schemaVersion, (),
        "unsupported SplitPhase cost evidence schema")
      identity <- _string(fields, "sourceSnapshotIdentity")
      _ <- Either.cond(identity == expectedIdentity &&
        identity == snapshot.source.identity, (),
        "SplitPhase cost evidence snapshot identity differs")
      sourceDigest <- _string(fields, "sourceSnapshotDigest")
      _ <- Either.cond(sourceDigest == snapshot.source.digest, (),
        "SplitPhase cost evidence snapshot digest differs")
      phase <- _string(fields, "sourcePhaseIdentity")
      authority <- _string(fields, "sourceAuthorityReference")
      _ <- Either.cond(phase == snapshot.source.sourcePhaseIdentity &&
        authority == snapshot.source.authorityReference, (),
        "SplitPhase cost evidence source authority differs")
      policyObject <- fields("policy").flatMap(_.asObject)
        .toRight("SplitPhase cost policy is missing")
      policy <- _policy(policyObject)
      demands <- fields("itemDemands").flatMap(_.asArray)
        .map(_.toVector).toRight("SplitPhase cost item demands are missing")
      classified <- _demands(demands)
      _ <- Either.cond(classified.map(_._1) ==
        snapshot.inventory.orderedUnfinishedItems, (),
        "SplitPhase cost item coverage or order differs")
    } yield Record(sourceDigest, documentSha256, policy, classified.toMap)
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  private def _policy(fields: JsonObject): Either[String, Policy] = for {
    _ <- Either.cond(fields.keys.toSet == Set("version", "expensiveProfile",
      "lowerCostProfile", "expensiveUnitsPerMinute", "lowerCostUnitsPerMinute",
      "materialSavingThresholdUnits"), (),
      "SplitPhase cost policy shape is incompatible")
    version <- _string(fields, "version")
    expensive <- _string(fields, "expensiveProfile")
    lower <- _string(fields, "lowerCostProfile")
    expensiveRate <- _long(fields, "expensiveUnitsPerMinute")
    lowerRate <- _long(fields, "lowerCostUnitsPerMinute")
    threshold <- _long(fields, "materialSavingThresholdUnits")
    _ <- Either.cond(expensive != lower && expensiveRate > lowerRate &&
      lowerRate >= 0 && threshold > 0, (),
      "SplitPhase cost policy rates or threshold are invalid")
  } yield Policy(version, expensive, lower, expensiveRate, lowerRate, threshold)

  private def _demands(values: Vector[Json]):
      Either[String, Vector[(String, Demand)]] = {
    val parsed = values.map { value => for {
      fields <- _fields(value, Set("identity", "demand"))
      identity <- _string(fields, "identity")
      name <- _string(fields, "demand")
      demand <- name match {
        case "ExpensiveReasoning" => Right(Demand.ExpensiveReasoning)
        case "SettledExecution" => Right(Demand.SettledExecution)
        case _ => Left("SplitPhase cost item demand is invalid")
      }
    } yield identity -> demand }
    parsed.foldLeft[Either[String, Vector[(String, Demand)]]](Right(Vector.empty)) {
      (acc, current) => for { prior <- acc; item <- current } yield prior :+ item
    }
  }

  private def _safePath(root: Path, directory: Path, document: Path): Boolean =
    Files.isDirectory(root) && !Files.isSymbolicLink(root) &&
      document.startsWith(root) &&
      !Files.isSymbolicLink(root.resolve("docs")) &&
      !Files.isSymbolicLink(root.resolve("docs").resolve("phase")) &&
      !Files.isSymbolicLink(directory) && Files.isDirectory(directory) &&
      !Files.isSymbolicLink(document) && Files.isRegularFile(document)

  private def _fields(json: Json, keys: Set[String]): Either[String, JsonObject] =
    json.asObject.filter(_.keys.toSet == keys)
      .toRight("SplitPhase cost evidence JSON has an incompatible shape")
  private def _string(fields: JsonObject, name: String): Either[String, String] =
    fields(name).flatMap(_.asString).filter(_.trim.nonEmpty)
      .toRight(s"SplitPhase cost evidence is missing $name")
  private def _long(fields: JsonObject, name: String): Either[String, Long] =
    fields(name).flatMap(_.asNumber).flatMap(_.toLong)
      .toRight(s"SplitPhase cost evidence is missing $name")
  private def _sha(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
}
