package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.{GitObjectId, ProcessCapabilityId, ProcessExecutionAdmission,
  ProcessExecutionRequest, ProcessExecutionTermination, SbtExecutionAttempt, SbtExecutionOutcome}
import org.goldenport.cncf.resource.ResourceReadLimits
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{HistoryEntry, InstanceRecord, InstanceRevision, ProgressionReference}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.cncf.workspace.*
import GoalPhaseNativeRun as F
import RepositorySyncManagedGitObservation as Git
import WorkflowScalaToolProcess as Tool

/** Ordered formatter effects in the candidate inspection that owns the selected sources.
  * @since   Nov. 24, 2026
  * @version Nov. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeScalaCleanup {
  private val _config_path = ".scalafmt.conf"
  private val _lint_path = ".scalafix.conf"
  private val _cleanup_rule = "RedundantSyntax"
  private val _format_provider = Tool.capability
  private val _lint_provider = "sm-workflow-scalafix-preview"
  private val _source_limit = 1048576
  private val _tool_result = "ordered-candidate:(scala-cleanup:(?:scalafix|scalafix-imports|scalafmt):[0-9]+:apply)-result".r
  private val _history_result = "ordered-candidate:source-history-result-([0-9]+)".r
  private val _compile_result = "ordered-candidate:(scala-cleanup:semantic-compile:([1-9][0-9]*):(compile|test)):result".r

  final case class ByteChange(path: String, before: String, after: String) {
    def valid: Boolean = Option(path).exists(value => GoalPhasePurposeRepair.path(value) && value.endsWith(".scala")) &&
      Option(before).exists(_digest) && Option(after).exists(_digest) && before != after
  }
  object ByteChange {
    given Encoder[ByteChange] = deriveEncoder
    given Decoder[ByteChange] = deriveDecoder
  }

  def recordedAfter(original: InstanceRecord, current: InstanceRecord): Boolean =
    current.history.drop(original.history.size).exists(_.causation.value.startsWith("ordered-candidate:scala-cleanup:"))

  def configured(core: ActionCall.Core): Boolean = _registered(core, _format_provider) || _registered(core, _lint_provider)

  /** Only adjacent, actual managed-write results can extend the rollback proof.
    * Preview bytes, repository observations and historical receipts without
    * byte provenance never supply an attributable extra edit. */
  def rollbackChangesC(session: F.Session, core: ActionCall.Core, original: InstanceRecord,
    candidate: InstanceRecord, step: String, index: String): Consequence[Vector[ByteChange]] = for {
    _ <- if (DeterministicRepair.retainedPrefix(original, candidate) &&
        DeterministicRepair.retainedPrefix(candidate, session.current)) Consequence.unit
      else F.invalid("Scala cleanup rollback crosses native candidate ancestry")
    tools <- session.storage.bindMechanicalC(F.advanceOperation, _codec)
    histories <- session.storage.bindMechanicalC(F.advanceOperation, GoalPhaseNativeSourceHistory.codec)
    changes <- candidate.history.drop(original.history.size).foldLeft(Consequence.success(Vector.empty[ByteChange])) {
      (prior, entry) => prior.flatMap { values =>
        entry.causation.value match {
          case _tool_result(prefix) => for {
            pair <- _result_pair_c(tools, session, core, original, entry, prefix + "-intent", prefix + "-result")
            (intent, result) = pair
            digest <- result.outputHash.map(Consequence.success).getOrElse(F.invalid("Scala cleanup write lost its output digest"))
            change = ByteChange(result.path, result.inputHash, digest)
            _ <- if (result.copy(outcome = None) == intent && intent.outcome.isEmpty &&
                result.outcome.contains("updated") && result.stage == "apply" && result.step == step &&
                result.sourceRevision == original.revision.value.toString && result.index == index && change.valid)
              Consequence.unit else F.invalid("Scala cleanup write does not prove its candidate bytes")
          } yield values :+ change
          case _history_result(ordinal) => for {
            pair <- _result_pair_c(histories, session, core, original, entry,
              "source-history-intent-" + ordinal, "source-history-result-" + ordinal)
            (intent, result) = pair
            _ <- if (result.copy(outcome = None) == intent && intent.outcome.isEmpty &&
                result.step == step && result.sourceRevision == original.revision.value.toString &&
                result.outcome.nonEmpty && result.change.forall(_.valid))
              Consequence.unit else F.invalid("Source history write does not prove its candidate bytes")
          } yield values ++ result.change
          case _ => Consequence.success(values)
        }
      }
    }
  } yield changes

  private def _result_pair_c[A](adapter: WorkflowManagedMechanicalTransition[A], session: F.Session,
    core: ActionCall.Core, original: InstanceRecord, entry: HistoryEntry, intentKey: String,
    resultKey: String): Consequence[(A, A)] = {
    def _reference_(key: String) = WorkflowMechanicalTransition.Reference(original.identity.value + ":ordered-candidate:" +
      original.revision.value + ":" + key, "1")
    for {
      intent <- adapter.loadRetainedC(_reference_(intentKey))(using core.executionContext).flatMap(
        _.map(Consequence.success).getOrElse(F.invalid("Scala cleanup intent is missing")))
      observed <- adapter.loadRetainedC(_reference_(resultKey))(using core.executionContext).flatMap(
        _.map(Consequence.success).getOrElse(F.invalid("Scala cleanup result is missing")))
      predecessor <- GoalPhaseNativeHistory.prefixC(session.current, intent.receipt.next.revision)
      _ <- if (predecessor == intent.receipt.next && predecessor.causation.value == "ordered-candidate:" + intentKey &&
          observed.receipt.next.revision == entry.revision) Consequence.unit
        else F.invalid("Scala cleanup receipt does not match actual candidate history")
      _ <- GoalPhaseSourceHistoryRecovery._adjacent_c(session.current, predecessor, observed.receipt.next,
        "ordered-candidate:" + resultKey)
    } yield (intent.receipt.payload.value, observed.receipt.payload.value)
  }

  private final case class Receipt(sourceRevision: String, step: String, index: String, path: String,
    tool: String, stage: String, inputHash: String, outputHash: Option[String], outcome: Option[String],
    engine: Option[String], program: Option[String], diagnostic: Option[String])
  private given Encoder[Receipt] = deriveEncoder
  private given Decoder[Receipt] = deriveDecoder

  private val _codec = new WorkflowResultJsonV1.PayloadCodec[Receipt] {
    val typeIdentity = "GoalPhaseNativeScalaCleanupReceipt"
    def encode(value: Receipt): Json = Json.obj(
      "schemaVersion" -> "sm-workflow.goal-phase-native-scala-cleanup.v1".asJson, "payload" -> value.asJson)
    def decode(value: Json): Either[String, Receipt] = for {
      receipt <- value.hcursor.get[Receipt]("payload").left.map(_.message)
      _ <- Either.cond(encode(receipt) == value && _valid(receipt), (), "Invalid native Scala cleanup receipt")
    } yield receipt
  }

  private def _valid(receipt: Receipt): Boolean =
    receipt.sourceRevision.toLongOption.exists(_ > 0) && _text(receipt.step, 256) &&
      GitObjectId.parseC(receipt.index).isSuccess && _handwritten(receipt.path) &&
      Set("scalafmt", "scalafix", "scalafix-imports")(receipt.tool) &&
      Set("preview", "recheck", "apply")(receipt.stage) &&
      _digest(receipt.inputHash) && receipt.outputHash.forall(_digest) &&
      receipt.engine.forall(_text(_, 128)) && receipt.program.forall(_text(_, 256)) &&
      receipt.diagnostic.forall(_.length <= 4096) &&
      receipt.outcome.forall(Set("applied", "unchanged", "unavailable", "metadata-required",
        "failed", "uncertain", "updated", "conflict"))

  private def _text(value: String, limit: Int): Boolean =
    value.nonEmpty && value.length <= limit && !value.exists(_.isControl)

  private def _digest(value: String): Boolean = value.matches("[0-9a-f]{64}")

  private def _hash_bytes(value: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(value).map(byte => f"${byte & 0xff}%02x").mkString

  def hashText(value: String): String = _hash_bytes(value.getBytes(UTF_8))

  private def _handwritten(path: String): Boolean =
    GoalPhasePurposeRepair.path(path) && path.endsWith(".scala") &&
      (path.startsWith("src/main/scala/") || path.startsWith("src/test/scala/")) &&
      !path.split('/').exists(Set("generated", "src_managed", "target"))

  /** The caller passes only paths owned by its current Step and actually selected
    * for staging. Same-workspace File reads and conditional writes own the source. */
  def formatC(session: F.Session, declaration: F.Declaration, core: ActionCall.Core,
    source: InstanceRecord, current: InstanceRecord, step: String, paths: Vector[String],
    inspect: () => Consequence[Unit], history: Option[GoalPhaseNativeSourceHistory.Prepared]): Consequence[InstanceRecord] =
    _run_c(session, declaration, core, source, current, step, paths, inspect, _format_provider, workspace => {
      def _configuration_() = WorkflowScalaLintAction.formatterConfigurationC(core, declaration.target.root, workspace.value)
      _configuration_().map(_.map(config => Selection("scalafmt", _config_path, config,
        input => Tool.previewC(core, input), () => _configuration_().map(_.contains(config)),
        (path, original, replacement) => GoalPhaseNativeSourceHistory.formattedC(history, path, original, replacement))))
    })

  def cleanupC(session: F.Session, declaration: F.Declaration, core: ActionCall.Core,
    source: InstanceRecord, current: InstanceRecord, step: String, paths: Vector[String],
    inspect: () => Consequence[Unit]): Consequence[InstanceRecord] =
    _run_c(session, declaration, core, source, current, step, paths, inspect, _lint_provider, workspace => {
      def _configuration_() = WorkflowScalaLintAction.rewriteC(core, declaration.target.root, workspace.value, _cleanup_rule)
      _configuration_().map(_.map(config => Selection("scalafix", _lint_path, config.configuration,
        input => Tool.scalafixC(core, input, "RedundantSyntax.finalObject", config.scalaVersion),
        () => _configuration_().map(_.exists(value =>
          value.scalaVersion == config.scalaVersion && value.configuration == config.configuration)),
        (_, original, replacement) => Consequence.success(_redundant_final_object(original, replacement)))))
    })

  /** A semantic rule reads a single compiler snapshot for its entire selection.
    * The candidate is observed again before applying any proposal from it. */
  def organizeImportsC(session: F.Session, declaration: F.Declaration, core: ActionCall.Core,
    source: InstanceRecord, current: InstanceRecord, plan: GoalPhaseOrderedProgression.Plan, step: String,
    paths: Vector[String], inspect: () => Consequence[Git.Observed]): Consequence[InstanceRecord] = {
    val selected = paths.filter(_handwritten).distinct
    if (selected.isEmpty || !_registered(core, _lint_provider)) Consequence.success(current)
    else for {
      workspace <- WorkspaceId.parseC("sm-workflow-repository")
      configuration <- WorkflowScalaLintAction.rewriteC(core, declaration.target.root, workspace.value, "OrganizeImports")
      result <- configuration.filter(_.semanticdbRoots.nonEmpty).fold(Consequence.success(current)) { config => for {
        checks <- GoalPhaseNativeValidation.selectedChecksC(declaration, plan, Some(step))
        edits <- session.storage.bindMechanicalC(F.advanceOperation, _codec)
        builds <- session.storage.bindMechanicalC(F.advanceOperation, _compile_codec)
        index <- Git.indexTreeC(core)
        compiler = new SemanticInspection(session, declaration, core, source, step,
          FileTarget(workspace), index, config, inspect, edits, builds)
        prepared <- compiler.prepareC(current, selected, checks)
        applied <- compiler.applyC(prepared)
      } yield applied }
    } yield result
  }

  private val _named_import = raw"import (?:_root_\.)?(?:java|scala)\.(?:[a-z][A-Za-z0-9_]*\.)*([A-Z][A-Za-z0-9_]*)(?:[ \t]*)".r
  private final case class ImportRegion(prefix: Vector[String], names: Vector[String], suffix: Vector[String])

  private def _import_region(source: String): Option[ImportRegion] = {
    val lines = source.split("\\n", -1).toVector
    val first = lines.indexWhere(_.startsWith("import "))
    val last = lines.lastIndexWhere(_.startsWith("import "))
    if (first < 0 || source.contains('\r')) None
    else {
      val region = lines.slice(first, last + 1).filterNot(_.isBlank)
      val names = region.collect { case _named_import(name) => name }
      Option.when(names.size >= 2 && names.size == region.size && names.distinct == names &&
        lines.indices.filter(index => index >= first && index <= last && !lines(index).isBlank)
          .forall(DeterministicRepair.codeAtLine(lines, _)))(
        ImportRegion(lines.take(first), region.map(_.stripTrailing()).sorted, lines.drop(last + 1)))
    }
  }

  private def _redundant_final_object(original: String, replacement: String): Boolean = {
    val before = original.split("\\n", -1).toVector
    val after = replacement.split("\\n", -1).toVector
    before.size == after.size && !original.contains('\r') && original != replacement &&
      before.zip(after).zipWithIndex.forall { case ((previous, next), ordinal) =>
        if (previous == next) true
        else {
          val offset = previous.takeWhile(_ == ' ').length
          previous.matches(" *final object [A-Za-z_][A-Za-z0-9_]* \\{\\}?") &&
            next == previous.patch(offset, "", 6) && DeterministicRepair.codeAtLine(before, ordinal)
        }
      }
  }

  private final case class Selection(tool: String, configPath: String, configuration: String,
    preview: Tool.Input => Consequence[Tool.Preview], sameConfiguration: () => Consequence[Boolean],
    admits: (String, String, String) => Consequence[Boolean])

  private final case class CompileRecord(sourceRevision: String, step: String, scope: String,
    check: String, fingerprint: String, outcome: Option[String], program: Option[String],
    metadata: Map[String, String], materialStatus: Option[String], diagnostic: Option[String])
  private given Encoder[CompileRecord] = deriveEncoder
  private given Decoder[CompileRecord] = deriveDecoder
  private val _compile_codec = new WorkflowResultJsonV1.PayloadCodec[CompileRecord] {
    val typeIdentity = "GoalPhaseSemanticCleanupCompile"
    def encode(value: CompileRecord): Json = Json.obj(
      "schemaVersion" -> "sm-workflow.goal-phase-semantic-cleanup-compile.v1".asJson, "payload" -> value.asJson)
    def decode(value: Json): Either[String, CompileRecord] = for {
      record <- value.hcursor.get[CompileRecord]("payload").left.map(_.message)
      _ <- Either.cond(encode(record) == value && record.sourceRevision.toLongOption.exists(_ > 0) &&
        _text(record.step, 256) && _text(record.check, 256) && Set("compile", "test")(record.scope) &&
        _digest(record.fingerprint) && record.outcome.forall(Set("built", "failed", "unavailable", "uncertain")) &&
        record.program.forall(_text(_, 256)) && record.diagnostic.forall(_.length <= 4096) &&
        record.metadata.forall((path, digest) => _handwritten(path) && _digest(digest)) &&
        record.materialStatus.forall(Set("matched", "changed", "unavailable")) &&
        (record.outcome.contains("built") == record.materialStatus.nonEmpty) &&
        (record.materialStatus.contains("matched") || record.metadata.isEmpty),
        (), "Invalid Scala cleanup compiler receipt")
    } yield record
  }

  private final case class SemanticFile(path: WorkspaceFilePath, text: String, ordinal: Int)
  private final case class SemanticEdit(file: SemanticFile, preview: Tool.Preview,
    replacement: String, formatter: Formatter)
  private final case class SemanticBatch(record: InstanceRecord, edits: Vector[SemanticEdit], fingerprint: String)

  private final class SemanticInspection(session: F.Session, declaration: F.Declaration, core: ActionCall.Core,
    source: InstanceRecord, step: String, target: FileTarget, index: String, config: WorkflowScalaLintAction.Rewrite,
    inspect: () => Consequence[Git.Observed], edits: WorkflowManagedMechanicalTransition[Receipt],
    builds: WorkflowManagedMechanicalTransition[CompileRecord]) {

    def prepareC(current: InstanceRecord, paths: Vector[String], checks: Vector[GoalPhaseNativeValidation.Check]):
      Consequence[SemanticBatch] = for {
      fingerprint <- _fingerprint_c
      files <- paths.zipWithIndex.foldLeft(Consequence.success(Vector.empty[SemanticFile])) { case (previous, (path, ordinal)) => for {
        accumulated <- previous
        file <- WorkspaceFilePath.parseC(path)
        read <- WorkflowFileExecution.readTextC(core, target, file, FileReadPolicy(ResourceReadLimits(_source_limit)))
      } yield accumulated ++ read.text.filter(_import_region(_).nonEmpty).map(SemanticFile(file, _, ordinal)) }
      batch <- if (files.isEmpty) Consequence.success(SemanticBatch(current, Vector.empty, "")) else for {
        latest <- _fingerprint_c
        _ <- if (latest == fingerprint) Consequence.unit else F.invalid("The candidate changed while selecting semantic cleanup sources")
        prepared <- Vector("compile", "test").foldLeft(Consequence.success(SemanticBatch(current, Vector.empty, fingerprint))) {
          (prior, scope) => prior.flatMap { batch =>
            val selected = files.filter(file => file.path.value.startsWith("src/test/") == (scope == "test"))
            val kind = if (scope == "test") WorkflowSbtValidation.Kind.TestCompile else WorkflowSbtValidation.Kind.Compile
            val direct = checks.filter(check => check.kind == kind && check.operation.isEmpty && check.dependency.isEmpty &&
              !check.carLint && check.metadata.isEmpty)
            (config.semanticdbRoots.get(scope), direct) match {
              case (Some(root), Vector(check)) if selected.nonEmpty => _scope_c(batch, scope, root, check, selected)
              case _ => Consequence.success(batch)
            }
          }
        }
      } yield prepared
    } yield batch

    def applyC(batch: SemanticBatch): Consequence[InstanceRecord] =
      if (batch.edits.isEmpty) Consequence.success(batch.record)
      else for {
        latest <- _fingerprint_c
        _ <- if (latest == batch.fingerprint) Consequence.unit
          else F.invalid("The compiler candidate changed before applying the semantic cleanup proposals")
        applied <- batch.edits.foldLeft(Consequence.success(batch.record)) { (previous, edit) =>
          previous.flatMap(record => edit.formatter.applyProposalC(record, edit.file.ordinal, edit.file.path,
            edit.file.text, edit.replacement, edit.preview))
        }
      } yield applied

    private def _scope_c(batch: SemanticBatch, scope: String, root: String, check: GoalPhaseNativeValidation.Check,
      files: Vector[SemanticFile]): Consequence[SemanticBatch] = for {
      fingerprint <- _fingerprint_c
      _ <- if (fingerprint == batch.fingerprint) Consequence.unit
        else F.invalid("The semantic cleanup compiler inputs changed")
      key = hashText(Json.arr(fingerprint.asJson, root.asJson, check.identity.asJson, check.ordinal.asJson,
        check.hostIdentity.asJson, config.scalaVersion.asJson, config.configuration.asJson).noSpaces)
      proof <- _reuse_c(scope, check.identity, key, files)
      available = proof.flatMap { expected =>
        _metadata_c(root, files) match {
          case Consequence.Success(bytes) if _digests(bytes) == expected => Some(bytes)
          case _ => None
        }
      }
      material <- available.fold(_refresh_c(batch.record, fingerprint, key, scope, root, check, files))(
        bytes => Consequence.success(batch.record -> bytes))
      prepared <- files.foldLeft(Consequence.success(batch.copy(record = material._1))) { (prior, file) => prior.flatMap { active =>
        material._2.get(file.path.value).fold(Consequence.success(active)) { bytes =>
          val metadatahash = _hash_bytes(bytes.toArray)
          val settings = Selection("scalafix-imports", _lint_path, config.configuration,
            input => Tool.semanticScalafixC(core, input, config.scalaVersion, bytes).flatMap { preview =>
              if (preview.metadataHash.contains(metadatahash)) Consequence.success(preview)
              else F.invalid("Semantic cleanup preview crossed its compiler metadata")
            },
            () => WorkflowScalaLintAction.rewriteC(core, declaration.target.root, target.workspaceId.value, "OrganizeImports")
              .map(_.contains(config)),
            (_, original, replacement) => Consequence.success(_import_region(original) == _import_region(replacement)))
          val formatter = new Formatter(declaration, core, source, step, target, index, settings, () => inspect().map(_ => ()), edits)
          val input = Tool.Input(file.path.value, file.text, Map(_lint_path -> config.configuration))
          formatter.previewC(active.record, file.ordinal, "preview", input).map { (record, preview) =>
            val admissible = preview.replacement.filter(text => _import_region(text) == _import_region(file.text))
            active.copy(record = record, edits = active.edits ++ admissible.map(SemanticEdit(file, preview, _, formatter)))
          }
        }
      }}
    } yield prepared

    private def _metadata_c(root: String, files: Vector[SemanticFile]): Consequence[Map[String, Vector[Byte]]] =
      files.foldLeft(Consequence.success(Map.empty[String, Vector[Byte]])) { (prior, file) => for {
        entries <- prior
        relative <- WorkspaceFilePath.parseC(root + "/META-INF/semanticdb/" + file.path.value + ".semanticdb")
        artifact <- WorkflowFileExecution.readBytesC(core, target, relative, FileRepresentation.Identity,
          FileReadPolicy(ResourceReadLimits(_source_limit)))
      } yield entries ++ artifact.bytes.filter(_.nonEmpty).map(file.path.value -> _) }

    private def _digests(metadata: Map[String, Vector[Byte]]): Map[String, String] =
      metadata.map((path, bytes) => path -> _hash_bytes(bytes.toArray))

    private def _reuse_c(scope: String, check: String, fingerprint: String,
      files: Vector[SemanticFile]): Consequence[Option[Map[String, String]]] =
      source.history.reverseIterator.foldLeft(Consequence.success(Option.empty[Map[String, String]])) { (previous, entry) =>
        previous.flatMap { matched => (matched, entry.causation.value) match {
          case (None, _compile_result(prefix, revision, entryScope)) if entryScope == scope => for {
            number <- revision.toLongOption.map(Consequence.success).getOrElse(F.invalid("Invalid semantic compiler source revision"))
            original <- GoalPhaseNativeHistory.prefixC(session.current, InstanceRevision(number))
            pair <- _result_pair_c(builds, session, core, original, entry, prefix + ":intent", prefix + ":result")
            (initial, actual) = pair
            _ <- if (actual.copy(outcome = None, program = None, metadata = Map.empty, materialStatus = None,
                diagnostic = None) == initial &&
                initial.sourceRevision == revision && initial.outcome.isEmpty) Consequence.unit
              else F.invalid("Semantic compiler result differs from its admitted intent")
          } yield Option.when(actual.outcome.contains("built") && actual.materialStatus.contains("matched") &&
            actual.check == check && actual.step == step && actual.scope == scope && actual.fingerprint == fingerprint &&
            files.forall(file => actual.metadata.contains(file.path.value)))(
              actual.metadata.filter((path, _) => files.exists(_.path.value == path)))
          case _ => Consequence.success(matched)
        }}
      }

    private def _refresh_c(current: InstanceRecord, fingerprint: String, key: String, scope: String, root: String,
      check: GoalPhaseNativeValidation.Check, files: Vector[SemanticFile]): Consequence[(InstanceRecord, Map[String, Vector[Byte]])] = for {
      initial <- Consequence.success(CompileRecord(source.revision.value.toString, step, scope, check.identity,
        key, None, None, Map.empty, None, None))
      before <- _save_build_c(current, initial, "intent")
      attempt <- WorkflowManagedProcessExecution.repositoryValidationAttemptC(core, check.ordinal, check.kind,
        check.hostIdentity, expectedWorkspaceRoot = Some(declaration.target.root))
      terminal = attempt match {
        case SbtExecutionAttempt.Observed(result) =>
          val outcome = result.processResult.termination match {
            case ProcessExecutionTermination.TerminationUnconfirmed => "uncertain"
            case _ if result.outcome == SbtExecutionOutcome.Succeeded => "built"
            case _ => "failed"
          }
          initial.copy(outcome = Some(outcome), program = Some(result.processResult.safeProgramIdentity))
        case SbtExecutionAttempt.AdmissionRejected(_, _, reason) =>
          initial.copy(outcome = Some("unavailable"), diagnostic = Some(reason.show.take(4096)))
        case SbtExecutionAttempt.Uncertain(_, _, reason) =>
          initial.copy(outcome = Some("uncertain"), diagnostic = Some(reason.show.take(4096)))
      }
      captured = if (!terminal.outcome.contains("built")) {
        terminal -> Map.empty[String, Vector[Byte]]
      } else (for {
        latest <- _fingerprint_c
        material <- if (latest == fingerprint) _metadata_c(root, files)
          else Consequence.success(Map.empty[String, Vector[Byte]])
      } yield (latest == fingerprint) -> material) match {
        case Consequence.Success((true, material)) =>
          terminal.copy(materialStatus = Some("matched"), metadata = _digests(material)) -> material
        case Consequence.Success((false, _)) =>
          terminal.copy(materialStatus = Some("changed")) -> Map.empty[String, Vector[Byte]]
        case Consequence.Failure(reason) =>
          terminal.copy(materialStatus = Some("unavailable"), diagnostic = Some(reason.show.take(4096))) ->
            Map.empty[String, Vector[Byte]]
      }
      (record, material) = captured
      saved <- _save_build_c(before, record, "result")
      _ <- if (record.materialStatus.contains("changed")) F.invalid("The semantic compiler input changed during the selected Compile")
        else if (terminal.outcome.contains("uncertain")) F.invalid("Semantic compiler termination is unresolved; do not replay")
        else Consequence.unit
    } yield saved -> material

    private def _save_build_c(current: InstanceRecord, record: CompileRecord, label: String): Consequence[InstanceRecord] = {
      val key = "scala-cleanup:semantic-compile:" + source.revision.value + ":" + record.scope + ":" + label
      _save_effect_c(builds, declaration, core, source, current, _compile_codec.typeIdentity, key, record)
    }

    private def _fingerprint_c: Consequence[String] = for {
      observed <- inspect()
      selectedIndex <- Git.indexTreeC(core)
      _ <- if (selectedIndex == index) Consequence.unit else F.invalid("Semantic cleanup inspection index changed")
      files <- observed.dirty.sortBy(_.path).foldLeft(Consequence.success(Vector.empty[Json])) { (prior, selected) => for {
        accumulated <- prior
        path <- WorkspaceFilePath.parseC(selected.path)
        input <- WorkflowFileExecution.readBytesC(core, target, path, FileRepresentation.Identity,
          FileReadPolicy(ResourceReadLimits(_source_limit)))
        _ <- if (input.bytes.nonEmpty || selected.worktree == 'D' || selected.index == 'D') Consequence.unit
          else F.invalid("A selected semantic compiler input is missing")
      } yield accumulated :+ Json.arr(selected.path.asJson, selected.index.toString.asJson, selected.worktree.toString.asJson,
        selected.renamedFrom.asJson, input.bytes.map(bytes => _hash_bytes(bytes.toArray)).asJson) }
    } yield hashText(Json.arr(declaration.target.root.toString.asJson, observed.branch.asJson, observed.head.asJson,
      selectedIndex.asJson, files.asJson).noSpaces)
  }

  private def _run_c(session: F.Session, declaration: F.Declaration, core: ActionCall.Core,
    source: InstanceRecord, current: InstanceRecord, step: String, paths: Vector[String],
    inspect: () => Consequence[Unit], capability: String,
    select: WorkspaceId => Consequence[Option[Selection]]): Consequence[InstanceRecord] = {
    val pathsselected = paths.filter(_handwritten).distinct
    if (pathsselected.isEmpty || !_registered(core, capability)) Consequence.success(current)
    else for {
      _ <- inspect()
      workspace <- WorkspaceId.parseC("sm-workflow-repository")
      configuration <- select(workspace)
      formatted <- configuration.fold(Consequence.success(current)) { selection => for {
        adapter <- session.storage.bindMechanicalC(F.advanceOperation, _codec)
        index <- RepositorySyncManagedGitObservation.indexTreeC(core)
        call = new Formatter(declaration, core, source, step, FileTarget(workspace), index, selection, inspect, adapter)
        result <- pathsselected.zipWithIndex.foldLeft(Consequence.success(current)) { case (previous, (path, ordinal)) =>
          previous.flatMap(call.formatC(_, path, ordinal))
        }
      } yield result }
    } yield formatted
  }

  private def _registered(core: ActionCall.Core, capability: String): Boolean =
    ProcessCapabilityId.parseC(capability).toOption.exists(identity =>
      ProcessExecutionAdmission.resolveC(core.executionContext.scope, ProcessExecutionRequest(identity)).isSuccess)

  private def _save_effect_c[A](adapter: WorkflowManagedMechanicalTransition[A], declaration: F.Declaration,
    core: ActionCall.Core, source: InstanceRecord, current: InstanceRecord, identity: String,
    key: String, payload: A): Consequence[InstanceRecord] = for {
    _ <- if (current.currentProgression.contains(ProgressionReference("phase.InspectCandidate")) &&
        DeterministicRepair.retainedPrefix(source, current)) Consequence.unit
      else F.invalid("Scala tool effect crosses its admitted inspection")
    reference = WorkflowMechanicalTransition.Reference(source.identity.value + ":ordered-candidate:" +
      source.revision.value + ":" + key, "1")
    previous <- adapter.loadRetainedC(reference)(using core.executionContext)
    _ <- if (previous.isEmpty) Consequence.unit else F.invalid("Scala cleanup effect is already recorded")
    saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(declaration.configuration, reference, current,
      current.revision, F.history(current, "phase.InspectCandidate", "ordered-candidate:" + key), TypedValue(identity, payload)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield saved.receipt.next

  private final class Formatter(declaration: F.Declaration, core: ActionCall.Core,
    source: InstanceRecord, step: String, target: FileTarget, index: String, selection: Selection,
    inspect: () => Consequence[Unit],
    adapter: WorkflowManagedMechanicalTransition[Receipt]) {

    def formatC(current: InstanceRecord, path: String, ordinal: Int): Consequence[InstanceRecord] = for {
      _ <- _pinned_c
      file <- WorkspaceFilePath.parseC(path)
      read <- WorkflowFileExecution.readTextC(core, target, file, FileReadPolicy(ResourceReadLimits(_source_limit)))
      result <- read.text.fold(Consequence.success(current)) { original => for {
        input <- Consequence.success(Tool.Input(path, original, Map(selection.configPath -> selection.configuration)))
        first <- previewC(current, ordinal, "preview", input)
        formatted <- first._2.replacement.fold(Consequence.success(first._1)) { replacement => for {
          second <- previewC(first._1, ordinal, "recheck", input.copy(source = replacement))
          verified = second._2.status match {
            case Tool.PreviewStatus.Unchanged => first._2.engineVersion == second._2.engineVersion &&
              first._2.programIdentity == second._2.programIdentity
            case _ => false
          }
          preserves <- if (verified) selection.admits(path, original, replacement) else Consequence.success(false)
          result <- if (preserves) applyProposalC(second._1, ordinal, file, original, replacement, first._2)
            else Consequence.success(second._1)
        } yield result }
      } yield formatted }
    } yield result

    def previewC(current: InstanceRecord, ordinal: Int, stage: String,
      input: Tool.Input): Consequence[(InstanceRecord, Tool.Preview)] = for {
      initial <- Consequence.success(Receipt(source.revision.value.toString, step, index, input.path, selection.tool, stage,
        hashText(input.source), None, None, None, None, None))
      intended <- _save_c(current, ordinal, stage + "-intent", initial)
      actual <- selection.preview(input)
      _ <- if (actual.path == input.path) Consequence.unit
        else F.invalid("Scala formatter result crosses its admitted source path")
      outcome = actual.status match {
        case Tool.PreviewStatus.Applied => "applied"
        case Tool.PreviewStatus.Unchanged => "unchanged"
        case Tool.PreviewStatus.Unavailable => "unavailable"
        case Tool.PreviewStatus.MetadataRequired => "metadata-required"
        case Tool.PreviewStatus.FormalFailure => "failed"
        case Tool.PreviewStatus.Uncertain => "uncertain"
      }
      observed <- _save_c(intended, ordinal, stage + "-result", initial.copy(inputHash = actual.inputHash,
        outputHash = actual.replacement.map(hashText),
        outcome = Some(outcome), engine = Some(actual.engineVersion), program = actual.programIdentity,
        diagnostic = actual.diagnostic))
      _ <- actual.status match {
        case Tool.PreviewStatus.Uncertain =>
          F.invalid("Scala tool termination is unresolved. Its retained attempt cannot be replayed automatically.")
        case _ => Consequence.unit
      }
    } yield (observed, actual)

    def applyProposalC(current: InstanceRecord, ordinal: Int, file: WorkspaceFilePath,
      original: String, replacement: String, opinion: Tool.Preview): Consequence[InstanceRecord] = for {
      _ <- _pinned_c
      same <- selection.sameConfiguration()
      _ <- if (same) Consequence.unit else F.invalid("Scala tool configuration changed during preview")
      initial = Receipt(source.revision.value.toString, step, index, file.value, selection.tool, "apply", hashText(original),
        Some(hashText(replacement)), None, Some(opinion.engineVersion), opinion.programIdentity, None)
      prepared <- _save_c(current, ordinal, "apply-intent", initial)
      written <- WorkflowFileExecution.writeTextC(core, target, file, replacement, FileWriteMode.UpdateExisting(original))
      outcome <- written.outcome match {
        case FileWriteOutcome.Updated => Consequence.success("updated")
        case FileWriteOutcome.Conflict(_) => Consequence.success("conflict")
        case _ => F.invalid("Scala formatter write has no known conditional replacement outcome")
      }
      retained <- _save_c(prepared, ordinal, "apply-result", initial.copy(outcome = Some(outcome)))
      _ <- if (outcome == "updated") Consequence.unit else F.invalid("Scala formatter source changed before the conditional write")
    } yield retained

    private def _pinned_c: Consequence[Unit] = for {
      _ <- inspect()
      actual <- RepositorySyncManagedGitObservation.indexTreeC(core)
      _ <- if (actual == index) Consequence.unit else F.invalid("Scala formatter inspection index changed")
    } yield ()

    private def _save_c(current: InstanceRecord, ordinal: Int, key: String, payload: Receipt): Consequence[InstanceRecord] =
      if (_valid(payload)) _save_effect_c(adapter, declaration, core, source, current, _codec.typeIdentity,
        "scala-cleanup:" + selection.tool + ":" + ordinal + ":" + key, payload)
      else F.invalid("Malformed Scala cleanup effect")
  }
}
