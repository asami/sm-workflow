package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import io.circe.{Json, Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation, GitPath}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.cncf.workspace.*
import scala.util.Try

/** Producer-neutral, revision-bound proposals. Admission does not apply edits.
  * @since   Nov. 22, 2026
  * @version Nov. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object DeterministicRepair {
  /** The caller must establish the candidate, receipt and owned source from
    * native retained evidence before constructing this transaction. */
  final case class Transaction(session: String, sourceRevision: String, candidate: String,
    validationReference: String, path: String, expected: String, patched: String, workspace: String)
  final case class Rollback(sourceRevision: String, failedCandidate: String, validationReference: String)
  final case class TransactionRecord(transaction: Transaction, operation: String, outcome: Option[String],
    rollback: Option[Rollback] = None)
  object Transaction {
    given Encoder[Transaction] = deriveEncoder
    given Decoder[Transaction] = deriveDecoder
  }
  object TransactionRecord {
    given Encoder[TransactionRecord] = deriveEncoder
    given Decoder[TransactionRecord] = deriveDecoder
  }
  object Rollback {
    given Encoder[Rollback] = deriveEncoder
    given Decoder[Rollback] = deriveDecoder
  }
  private val _transactionLimit = 1024 * 1024
  private def _exact(value: String): Boolean = Option(value).exists(_.nonEmpty) && value.nonEmpty && value == value.trim &&
    value.length <= 4096 && !value.exists(_.isControl)
  def validTransaction(value: Transaction): Boolean = Option(value).isDefined &&
    Vector(value.session, value.sourceRevision, value.candidate, value.validationReference).forall(_exact) &&
    GitObjectId.parseC(value.candidate).isSuccess &&
    _exact(value.workspace) && WorkspaceId.parseC(value.workspace).isSuccess &&
    value.sourceRevision.matches("[1-9][0-9]*") && BigInt(value.sourceRevision) < BigInt(Long.MaxValue) - 4 &&
    GoalPhasePurposeRepair.path(value.path) && value.path.endsWith(".scala") &&
    Option(value.expected).isDefined && Option(value.patched).isDefined && value.expected.length <= _transactionLimit &&
    value.patched.length <= _transactionLimit && value.expected != value.patched &&
    !value.expected.contains('\u0000') && !value.patched.contains('\u0000') &&
    _valid_text(value.expected) && _valid_text(value.patched)

  val transactionCodec: WorkflowResultJsonV1.PayloadCodec[TransactionRecord] = new WorkflowResultJsonV1.PayloadCodec[TransactionRecord] {
    val typeIdentity = "DeterministicRepairTransaction"
    def encode(value: TransactionRecord): Json = Json.obj(
      "schemaVersion" -> Json.fromString("sm-workflow.deterministic-repair-transaction.v1"), "payload" -> value.asJson)
    def decode(value: Json): Either[String, TransactionRecord] = for {
      wire <- Option(value).toRight("transaction record absent")
      record <- wire.hcursor.get[TransactionRecord]("payload").left.map(_.message)
      _ <- Either.cond(encode(record) == wire && validTransaction(record.transaction) &&
        Option(record.rollback).isDefined && Option(record.outcome).isDefined && record.outcome.forall(Set("updated", "conflict")) &&
        (record.operation match {
          case "apply" => record.rollback.isEmpty
          case "rollback" => record.rollback.exists(value => Option(value).isDefined &&
            Option(value.sourceRevision).isDefined && value.sourceRevision.toLongOption.exists(revision =>
              revision > record.transaction.sourceRevision.toLong && revision.toString == value.sourceRevision) &&
            _exact(value.failedCandidate) && GitObjectId.parseC(value.failedCandidate).isSuccess &&
            value.failedCandidate != record.transaction.candidate &&
            _exact(value.validationReference))
          case _ => false
        }), (), "invalid transaction record")
    } yield record
  }

  /** No inferred success: an intent without a typed result stays unknown. */
  final case class TransactionObservation(record: InstanceRecord, value: TransactionRecord)
  private def _transactionReference(source: InstanceRecord, operation: String, kind: String) =
    WorkflowMechanicalTransition.Reference(source.identity.value + ":deterministic-repair:" +
      source.revision.value + ":" + operation + ":" + kind, "1")
  private def _cause(operation: String, kind: String) = "ordered-deterministic-repair:" + operation + ":" + kind
  private[admission] val applyIntentCause = _cause("apply", "intent")
  private[admission] val rollbackIntentCause = _cause("rollback", "intent")
  private def _pure = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())

  private def _apply_result_c(current: InstanceRecord, previous: InstanceRecord, saved: InstanceRecord,
    updated: Boolean): Consequence[InstanceRecord] = for {
    actual <- GoalPhaseNativeHistory.prefixC(current, saved.revision)
    _ <- if (actual == saved && (actual.identity: InstanceIdentity) == (previous.identity: InstanceIdentity) &&
        (actual.definition: DefinitionBinding) == (previous.definition: DefinitionBinding) &&
        previous.revision.value < Long.MaxValue && (actual.revision: InstanceRevision) == (previous.revision.next: InstanceRevision) &&
        (actual.history.dropRight(1): scala.collection.immutable.Vector[org.goldenport.cncf.workflow.WorkflowInstancePersistence.HistoryEntry]) == (previous.history: scala.collection.immutable.Vector[org.goldenport.cncf.workflow.WorkflowInstancePersistence.HistoryEntry]) && (actual.causation.value: String) == (_cause("apply", "result"): String) &&
        actual.currentProgression.contains(ProgressionReference(if (updated) "phase.InspectCandidate" else "phase.Repair")) &&
        actual.suspension.isEmpty && (actual.lifecycle: Lifecycle) == (previous.lifecycle: Lifecycle)) Consequence.unit
      else GoalPhaseNativeRun.invalid("deterministic transaction result crossed its native predecessor")
  } yield actual

  def retainedTransactionC(session: GoalPhaseNativeRun.Session, core: ActionCall.Core,
    source: InstanceRecord, operation: String = "apply"): Consequence[Option[TransactionObservation]] = for {
    adapter <- session.storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, transactionCodec)
    intent <- adapter.loadRetainedC(_transactionReference(source, operation, "intent"))(using core.executionContext)
    observed <- intent match {
      case None => Consequence.success(Option.empty[TransactionObservation])
      case Some(saved) => for {
        expected <- transactionCodec.decode(transactionCodec.encode(saved.receipt.payload.value))
          .fold(GoalPhaseNativeRun.invalid, Consequence.success)
        transaction = expected.transaction
        revision = expected.rollback.fold(transaction.sourceRevision)(_.sourceRevision)
        _ <- if (expected.operation == operation && expected.outcome.isEmpty && (source.identity.value: String) == (transaction.session: String) &&
            source.revision.value.toString == revision) Consequence.unit
          else GoalPhaseNativeRun.invalid("deterministic intent crossed its binding")
        intended <- GoalPhaseSourceHistoryRecovery._adjacent_c(session.current, source, saved.receipt.next, _cause(operation, "intent"))
        result <- adapter.loadRetainedC(_transactionReference(source, operation, "result"))(using core.executionContext)
        observation <- result match {
          case None => Consequence.success(TransactionObservation(intended, expected))
          case Some(found) => for {
            value <- transactionCodec.decode(transactionCodec.encode(found.receipt.payload.value)).fold(GoalPhaseNativeRun.invalid, Consequence.success)
            _ <- if (value.copy(outcome = None) == expected && value.outcome.nonEmpty) Consequence.unit
              else GoalPhaseNativeRun.invalid("deterministic result crossed its intent")
            next <- if (operation == "apply") _apply_result_c(session.current, intended, found.receipt.next, value.outcome.contains("updated"))
              else GoalPhaseSourceHistoryRecovery._adjacent_c(session.current, intended, found.receipt.next, _cause(operation, "result"))
          } yield TransactionObservation(next, value)
        }
      } yield Some(observation)
    }
  } yield observed

  def stepContext(controller: GoalPhaseOrderedProgression.State,
    candidate: Option[GoalPhaseNativeCandidate.Saved], validation: Option[GoalPhaseNativeValidation.Saved]):
    Option[(GoalPhaseOrderedProgression.Step, GoalPhaseNativeCandidate.Saved, GoalPhaseNativeValidation.Saved)] = for {
    step <- controller.plan.steps.lift(controller.cursor)
    savedCandidate <- candidate
    savedValidation <- validation
    if !controller.releaseRepairActive && controller.completed.size == controller.cursor &&
      savedCandidate.payload.step == step.identity && savedValidation.payload.step == step.identity &&
      savedCandidate.payload.candidate.contains(savedValidation.payload.candidate)
  } yield (step, savedCandidate, savedValidation)

  def rollbackContext(controller: GoalPhaseOrderedProgression.State, from: InstanceRecord,
    candidate: Option[GoalPhaseNativeCandidate.Saved], validation: Option[GoalPhaseNativeValidation.Saved],
    repairs: Map[String, TransactionObservation], restored: Set[String]):
    Option[(String, TransactionObservation, GoalPhaseNativeCandidate.Saved, GoalPhaseNativeValidation.Saved)] = for {
    context <- stepContext(controller, candidate, validation)
    (step, savedCandidate, savedValidation) = context
    if (controller.stage == GoalPhaseOrderedProgression.Stage.Repair ||
        controller.stage == GoalPhaseOrderedProgression.Stage.Decision) && !restored.contains(step.identity)
    applied <- repairs.get(step.identity)
    if failedRevalidation(applied, from, savedCandidate, savedValidation).nonEmpty
  } yield (step.identity, applied, savedCandidate, savedValidation)

  def attemptCompilerC(session: GoalPhaseNativeRun.Session, declaration: GoalPhaseNativeRun.Declaration,
    core: ActionCall.Core, from: InstanceRecord, controller: GoalPhaseOrderedProgression.State,
    candidate: Option[GoalPhaseNativeCandidate.Saved], validation: Option[GoalPhaseNativeValidation.Saved],
    repairs: Map[String, TransactionObservation]): Consequence[Option[TransactionObservation]] =
    stepContext(controller, candidate, validation).filterNot(context => repairs.contains(context._1.identity)) match {
      case None => Consequence.success(None)
      case Some((step, savedCandidate, savedValidation)) => for {
        proposed <- prepareCompilerC(session, declaration, core, from, savedCandidate, savedValidation, step.ownedPaths.toSet)
        result <- proposed match {
          case None => Consequence.success(None)
          case Some(patch) => applyC(session, declaration, core, from, patch, step.ownedPaths.toSet).map(Some(_))
        }
      } yield result
    }

  /** A locator derived only from a complete typed failure, not file authority. */
  final case class DeterministicCompilerFinding(checkIdentity: String, reportedPath: String,
    line: Int, column: Int, echoedSource: String)

  def deterministicCompilerFinding(payload: GoalPhaseNativeValidation.Payload): Option[DeterministicCompilerFinding] = {
    val kinds = Set(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Kind.TestCompile,
      WorkflowSbtValidation.Kind.Test, WorkflowSbtValidation.Kind.TestOnly)
    if (Option(payload).isEmpty || Option(payload.outcome).isEmpty || !payload.outcome.exists(Set("repair-required", "repair-limit-reached")) ||
        !GoalPhaseNativeValidation.repairable(payload)) None
    else payload.receipts.zip(payload.selected).lastOption.flatMap { (receipt, check) =>
      val process = receipt.processObservation
      val diagnosis = IncrementalSbtFailureDiagnosis.extract(receipt, check.kind, payload.sourceRevision, payload.candidate)
      if (!kinds(check.kind) || check.carLint || receipt.group.nonEmpty || !RepositorySyncNativeMergeValidation.storedProductOutputValid(receipt) ||
          !process.exists(p => !p.stdout.truncated && !p.stderr.truncated &&
            WorkflowSbtCapture.text(p.stdout).textStatus == "complete" && WorkflowSbtCapture.text(p.stderr).textStatus == "complete") ||
          diagnosis.omittedGroups != 0 || diagnosis.groups.map(_.kind).sorted != Vector("compilation-failure", "compiler-error") ||
          diagnosis.groups.exists(group => group.occurrenceCount != 1 || group.omittedOccurrences != 0)) None
      else {
        val header = raw"\[error\] -- \[E008\] Not Found Error: (/[^\r\n:]+\.scala):(\d+):(\d+)\s*".r
        val source = raw"\[error\] (\d+) \|(.*)".r
        val caret = raw"\[error\]\s+\|\s*\^+\s*".r
        val message = raw"\[error\]\s+\|\s+value message is not a member of io\.circe\.Error\s*".r
        val task = raw"\[error\] \((?:[^\r\n()]+ / )?(Compile|Test) / (?:compile|compileIncremental)\) Compilation failed\s*".r
        val summary = raw"\[error\] Total time: \d+ s(?: \(\d+:\d{2}\))?, completed \d{4}/\d{1,2}/\d{1,2} \d{1,2}:\d{2}:\d{2}".r
        val streams = Vector(receipt.stdout, receipt.stderr).map(_.split("\\r?\\n", -1).toVector)
        val errors = streams.flatMap(_.filter(_.startsWith("[error]")))
        val (diagnostic, footer) = errors match {
          case Vector(a, b, c, d, e, f, last: String) if summary.matches(last) =>
            (Vector(a, b, c, d, e, f), Some(last))
          case rows => (rows, None)
        }
        diagnostic match {
          case Vector(header(path, ln, col), source(sourceLine, echoed), caret(), message(),
              "[error] one error found", task(configuration)) =>
            val expectedShape = ".fold(error => fail(error.message), identity)"
            val indent = echoed.takeWhile(c => c == ' ' || c == '\t').length
            val dot = echoed.indexOf(".message")
            val location = for {
              line <- ln.toIntOption.filter(_ > 0)
              column <- col.toIntOption.filter(_ > 0)
              parsedSource <- sourceLine.toIntOption.filter(_ == line)
              if parsedSource == line && echoed.drop(indent) == expectedShape && dot >= 0 && column == dot + 1
              if check.kind == WorkflowSbtValidation.Kind.Compile || configuration == "Test"
              if streams.exists(lines => lines.sliding(6).exists(_.toVector == diagnostic))
              if errors.size == 6 || footer.exists { expectedFooter =>
                streams.exists { lines =>
                  lines.sliding(7).zipWithIndex.exists {
                    case (Vector(a, b, c, d, e, f, actualFooter: String), index) =>
                      Vector(a, b, c, d, e, f) == diagnostic && expectedFooter.equals(actualFooter) &&
                        lines.drop(index + 7).forall(_.isEmpty)
                    case _ => false
                  }
                }
              }
            } yield DeterministicCompilerFinding(check.identity, path, line, column, echoed)
            location
          case _ => None
        }
      }
    }
  }

  /** Own the normal repository checkout and authenticate a compiler locator
    * against one exact staged source before considering a workspace update.
    */
  private[admission] def prepareCompilerC(session: GoalPhaseNativeRun.Session,
    declaration: GoalPhaseNativeRun.Declaration, core: ActionCall.Core, source: InstanceRecord,
    candidate: GoalPhaseNativeCandidate.Saved, validation: GoalPhaseNativeValidation.Saved,
    owned: Set[String]): Consequence[Option[Transaction]] = {
    val request = for {
      finding <- deterministicCompilerFinding(validation.payload)
      root <- Try(Path.of(session.started.receipt.payload.value.root).normalize).toOption
      reported <- Try(Path.of(finding.reportedPath)).toOption
      if root.isAbsolute && reported.isAbsolute && reported.normalize == reported && reported.startsWith(root)
      path = root.relativize(reported).toString
      if GoalPhasePurposeRepair.path(path) && path.endsWith(".scala") &&
        (path.startsWith("src/main/scala/") || path.startsWith("src/test/scala/")) && owned.contains(path)
      snapshot <- candidate.payload.candidate
      if (candidate.record.identity: InstanceIdentity) == (source.identity: InstanceIdentity) &&
        retainedPrefix(validation.record, source) &&
        validation.payload.candidate == snapshot && validation.payload.step == candidate.payload.step &&
        candidate.payload.committedWork.isEmpty && candidate.payload.continuation.isEmpty &&
        candidate.payload.dependencyAdmission.isEmpty && validation.payload.dependency.isEmpty &&
        (GoalPhaseNativeCandidate.workPaths(candidate.payload): Vector[String]).contains(path: String)
    } yield (finding, root, path, snapshot)
    request.fold(Consequence.success(Option.empty[Transaction])) { case (finding, root, path, snapshot) =>
      sourceC(declaration, core, root, path, snapshot).map(_.flatMap { case (base, workspace) =>
        val reference = validation.record.revision.value.toString + ":" + finding.checkIdentity
        val document = Document(root.resolve(path).toUri.toString, snapshot.tree, 0, base)
        _compiler_patch(finding, document, reference).toOption.map(updated =>
          Transaction(source.identity.value, source.revision.value.toString, snapshot.tree, reference,
            path, base, updated, workspace)).filter(validTransaction)
      })
    }
  }

  // One read-only Git and workspace proof shared by deterministic and ordinary repair.
  private[admission] def sourceC(declaration: GoalPhaseNativeRun.Declaration, core: ActionCall.Core,
    root: Path, path: String, snapshot: GoalPhaseOrderedProgression.Snapshot): Consequence[Option[(String, String)]] = {
      val git = WorkflowManagedProcessExecution
      val workspace = RepositorySyncDirectoryList.workspace(core, "sm-workflow-repository")
      val observed = for {
        expectedHead <- GitObjectId.parseC(snapshot.head)
        expectedTree <- GitObjectId.parseC(snapshot.tree)
        selectedPath <- GitPath.parseC(path)
        gitroot <- git.executeGitC(core, GitOperation.Root).flatMap(_.valueC)
        branch <- git.executeGitC(core, GitOperation.Branch).flatMap(_.valueC)
        head <- git.executeGitC(core, GitOperation.Head).flatMap(_.valueC)
        tree <- git.executeGitC(core, GitOperation.IndexTree).flatMap(_.valueC)
        _ <- if (gitroot == root.toString && declaration.target.root.normalize == root &&
            branch == declaration.target.localBranch && head == expectedHead && tree == expectedTree) Consequence.unit
          else GoalPhaseNativeRun.invalid("deterministic compiler candidate is no longer current")
        parent <- git.executeGitC(core, GitOperation.CommitMetadata(head)).flatMap(_.valueC)
        changes <- git.executeGitC(core, GitOperation.TreeChanges(parent.tree, tree)).flatMap(_.valueC)
        selected <- changes.filter(row => (row.path: GitPath) == selectedPath &&
          (row.newMode: String) == "100644").flatMap(_.newBlob) match {
          case Vector(blob) => Consequence.success(blob)
          case _ => GoalPhaseNativeRun.invalid("deterministic compiler source is not one staged regular file")
        }
        _ <- GoalPhaseNativeCandidate.primaryRepairPreflightC(core, declaration.candidatePaths, Vector(path))
        blob <- git.executeGitC(core, GitOperation.BlobContent(selected)).flatMap(_.valueC)
        base <- blob.text.filter(text => (text.getBytes(UTF_8).toVector: Vector[Byte]) == (blob.bytes: Vector[Byte]))
          .map(Consequence.success).getOrElse(GoalPhaseNativeRun.invalid("deterministic compiler source is not UTF-8"))
        target <- WorkspaceId.parseC(workspace)
        file <- WorkspaceFilePath.parseC(path)
        worktree <- WorkflowFileExecution.readTextC(core, FileTarget(target), file)
        _ <- if (worktree.text.contains(base)) Consequence.unit
          else GoalPhaseNativeRun.invalid("deterministic compiler source differs in the working tree")
      } yield (base, workspace)
      Consequence.success(observed.toOption)
  }

  /** Candidate-bound, read-only input for an ordinary compiler repair. */
  final case class CompilerMaterial(session: String, sourceRevision: String, candidate: String,
    validationReference: String, path: String, workspace: String, document: Document, diagnostic: Diagnostic)
  final case class CompilerLocator(checkIdentity: String, path: String, line: Int, column: Int, message: String)

  def compilerLocator(input: GoalPhaseNativeValidation.Payload): Option[CompilerLocator] = Option(input).flatMap { payload =>
    if (!payload.outcome.contains("repair-required") ||
        payload.dependency.nonEmpty || payload.dependencySource.nonEmpty || payload.sourceConsumption.nonEmpty ||
        payload.dependencyExecutions.nonEmpty || !GoalPhaseNativeValidation.repairable(payload) || payload.receipts.size > payload.selected.size ||
        !payload.receipts.zip(payload.selected).forall((receipt, check) => receipt.identity == check.identity)) None
    else {
      val failures = payload.receipts.zip(payload.selected).filterNot((receipt, check) =>
        RepositorySyncNativeMergeValidation.productSuccessful(receipt, check.kind))
      failures match {
        case Vector((receipt, check)) if Set(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Kind.TestCompile)(check.kind) &&
            !check.carLint && receipt.group.isEmpty && RepositorySyncNativeMergeValidation.storedProductOutputValid(receipt) &&
            receipt.processObservation.exists(p => !p.stdout.truncated && !p.stderr.truncated &&
              WorkflowSbtCapture.text(p.stdout).textStatus == "complete" && WorkflowSbtCapture.text(p.stderr).textStatus == "complete") =>
          val diagnosis = IncrementalSbtFailureDiagnosis.extract(receipt, check.kind, payload.sourceRevision, payload.candidate)
          val errors = diagnosis.groups.filter(_.kind == "compiler-error")
          if (diagnosis.omittedGroups != 0 || diagnosis.groups.exists(g => g.omittedOccurrences != 0) ||
              diagnosis.groups.exists(g => g.kind.startsWith("test-") || g.kind == "suite-abort") ||
              diagnosis.testExecution.status == "executed-tests-observed" ||
              diagnosis.testExecution.testsRun.exists(_ > 0) || diagnosis.testExecution.completedSuites.exists(_ > 0) ||
              errors.size != 1) None
          else for {
            error <- errors.headOption.filter(e => e.occurrenceCount == 1 && e.occurrences.size == 1)
            occurrence <- error.occurrences.headOption.filter(_.omittedCharacters == 0)
            location <- occurrence.location
            column <- location.column.filter(_ > 0)
            diagnostic = occurrence.context.split("\\n", -1).toVector
              .drop(occurrence.firstLine - occurrence.contextFirstLine).filter(_.startsWith("[error]"))
              .mkString("\n")
            if location.line > 0 && location.file.endsWith(".scala") && diagnostic.nonEmpty
          } yield CompilerLocator(check.identity, location.file, location.line, column, diagnostic)
        case _ => None
      }
    }
  }

  def compilerMaterialC(session: GoalPhaseNativeRun.Session, declaration: GoalPhaseNativeRun.Declaration,
    core: ActionCall.Core, source: InstanceRecord, candidate: GoalPhaseNativeCandidate.Saved,
    validation: GoalPhaseNativeValidation.Saved, owned: Set[String]): Consequence[Option[CompilerMaterial]] = {
    val request = for {
      finding <- compilerLocator(validation.payload)
      root <- Try(Path.of(session.started.receipt.payload.value.root).normalize).toOption
      reported <- Try(Path.of(finding.path)).toOption
      if root.isAbsolute && reported.isAbsolute && reported.normalize == reported && reported.startsWith(root)
      path = root.relativize(reported).toString
      if GoalPhasePurposeRepair.path(path) && path.endsWith(".scala") &&
        (path.startsWith("src/main/scala/") || path.startsWith("src/test/scala/")) && owned.contains(path)
      snapshot <- candidate.payload.candidate
      if (candidate.record.identity: InstanceIdentity) == (source.identity: InstanceIdentity) &&
        retainedPrefix(candidate.record, validation.record) && retainedPrefix(validation.record, source) &&
        validation.payload.candidate == snapshot && validation.payload.step == candidate.payload.step &&
        candidate.payload.committedWork.isEmpty && candidate.payload.continuation.isEmpty &&
        candidate.payload.dependencyAdmission.isEmpty && candidate.payload.dependency.isEmpty &&
        candidate.payload.dependencyOrigin.isEmpty && candidate.payload.dependencyStaged.isEmpty &&
        candidate.payload.dependencySourceDirection.isEmpty && candidate.payload.dependencySourceGuard.isEmpty &&
        candidate.payload.dependencySourceStages.isEmpty && candidate.payload.dependencySourceCapture.isEmpty &&
        candidate.payload.dependencySourceScope.isEmpty && candidate.payload.primaryPublicationRepair.isEmpty &&
        validation.payload.dependency.isEmpty && validation.payload.dependencySource.isEmpty &&
        validation.payload.sourceConsumption.isEmpty &&
        (GoalPhaseNativeCandidate.workPaths(candidate.payload): Vector[String]).contains(path: String)
    } yield (finding, root, path, snapshot)
    request.fold(Consequence.success(Option.empty[CompilerMaterial])) { case (finding, root, path, snapshot) =>
      sourceC(declaration, core, root, path, snapshot).map(_.flatMap { case (base, workspace) =>
        val reference = validation.record.revision.value.toString + ":" + finding.checkIdentity
        val document = Document(root.resolve(path).toUri.toString, snapshot.tree, 0, base)
        val point = Position(finding.line - 1, finding.column - 1)
        val diagnostic = Diagnostic("scala-compiler", document.uri, document.revision, document.version,
          "compiler-error", Range(point, point), finding.message)
        val material = CompilerMaterial(source.identity.value, source.revision.value.toString, snapshot.tree,
          reference, path, workspace, document, diagnostic)
        Option.when((base + finding.message + path + reference + document.uri).getBytes(UTF_8).length <= _transactionLimit &&
          validateSelection(document, diagnostic, Set(document.uri)).isRight)(material)
      })
    }
  }

  /** A source span is usable only when the raw Scala 3 location, echoed line
    * and continuous caret agree with the exact staged document. Leave other
    * diagnostic formats on the ordinary explicit-range path.
    */
  private[admission] def compilerDiagnosticSelection(material: CompilerMaterial): Option[(Range, String)] = {
    val point = material.diagnostic.range.start
    val header = raw"\[error\] -- .+ Error: .+\.scala:(\d+):(\d+) *".r
    val source = raw"\[error\] +(\d+) *\|(.*)".r
    val mark = raw"\[error\] +\|( *)(\^+) *".r
    material.diagnostic.message.linesIterator.take(3).toVector match {
      case Vector(header(headerLine, headerColumn), source(line, echoed), mark(indent, carets))
          if headerLine == line && line.toIntOption.contains(point.line + 1) &&
            headerColumn.toIntOption.contains(indent.length) && point.character + 1 == indent.length &&
            carets.length <= 4096 &&
            echoed.forall(c => c >= ' ' && c <= '~') =>
        // Scala 3's rendered header uses the caret's zero-based column.
        // The existing generic locator subtracts one for the diagnostic point.
        for {
          actual <- material.document.text.split("\n", -1).lift(point.line).map(_.stripSuffix("\r"))
          if actual == echoed && carets.length <= actual.length - indent.length
          start = Position(point.line, indent.length)
          range = Range(start, start.copy(character = start.character + carets.length))
          span <- _offsets(material.document.text, range).toOption
        } yield range -> material.document.text.substring(span._1, span._2)
      case _ => None
    }
  }

  /** Closed response to a bound task. Apply only one checked replacement. */
  def compilerReplyC(material: CompilerMaterial, reply: org.goldenport.record.Record): Consequence[Transaction] = for {
    text <- reply.toJsonStringC
    json <- io.circe.jawn.JawnParser(allowDuplicateKeys = false).parse(text).fold(e => GoalPhaseNativeRun.invalid(e.message), Consequence.success)
    transaction <- compilerReply(material, json).fold(GoalPhaseNativeRun.invalid, Consequence.success)
  } yield transaction

  def compilerReply(material: CompilerMaterial, reply: Json): Either[String, Transaction] = {
    def fields(value: Json, expected: Set[String]): Either[String, Unit] =
      Either.cond(value.asObject.exists(obj => obj.size == expected.size && obj.keys.forall(expected.contains)), (), "unexpected compiler reply fields")
    def position(value: Json): Either[String, Position] = for {
      _ <- fields(value, Set("line", "character"))
      line <- value.hcursor.get[Int]("line").left.map(_.message)
      character <- value.hcursor.get[Int]("character").left.map(_.message)
    } yield Position(line, character)
    val selection = compilerDiagnosticSelection(material) match {
      case Some(span) =>
        fields(reply, Set("replacement")).flatMap { _ =>
          Either.cond(material.document.revision == material.candidate, span, "compiler reply crossed its trusted binding")
        }
      case None => for {
        _ <- fields(reply, Set("candidate", "validationReference", "path", "range", "expectedSource", "replacement"))
        candidate <- reply.hcursor.get[String]("candidate").left.map(_.message)
        validation <- reply.hcursor.get[String]("validationReference").left.map(_.message)
        path <- reply.hcursor.get[String]("path").left.map(_.message)
        _ <- Either.cond(candidate == material.candidate && validation == material.validationReference && path == material.path &&
          material.document.revision == material.candidate, (), "compiler reply crossed its trusted binding")
        range <- reply.hcursor.get[Json]("range").left.map(_.message)
        _ <- fields(range, Set("start", "end"))
        start <- range.hcursor.get[Json]("start").left.map(_.message).flatMap(position)
        end <- range.hcursor.get[Json]("end").left.map(_.message).flatMap(position)
        expected <- reply.hcursor.get[String]("expectedSource").left.map(_.message)
      } yield Range(start, end) -> expected
    }
    for {
      selected <- selection
      (range, expected) = selected
      replacement <- reply.hcursor.get[String]("replacement").left.map(_.message)
      action = Action(material.diagnostic.producer, "compiler-replacement", material.diagnostic,
        Vector(Edit(material.document.uri, material.document.version, range, replacement)))
      patch <- compilerReplacement(action, material.document, Set(material.document.uri), expected)
      rendered <- render(patch, material.document)
      transaction = Transaction(material.session, material.sourceRevision, material.candidate, material.validationReference,
        material.path, material.document.text, rendered, material.workspace)
      _ <- Either.cond(validTransaction(transaction), (), "invalid compiler transaction")
    } yield transaction
  }

  private[admission] def retainedPrefix(record: InstanceRecord, source: InstanceRecord): Boolean =
    (record.identity: InstanceIdentity) == (source.identity: InstanceIdentity) &&
      (record.definition: DefinitionBinding) == (source.definition: DefinitionBinding) &&
      record.revision.value <= source.revision.value && source.history.startsWith(record.history)

  private def _compiler_patch(finding: DeterministicCompilerFinding,
    document: Document, reference: String): Either[String, String] = {
    val line = finding.line - 1
    // The compiler points at the dot. The member starts one UTF-16 unit later.
    val column = finding.column
    val range = Range(Position(line, column), Position(line, column + "message".length))
    val diagnostic = Diagnostic("scala-compiler", document.uri, document.revision, document.version, "E008", range,
      "value message is not a member of io.circe.Error")
    for {
      _ <- Either.cond(document.text.split("\\r?\\n", -1).lift(line).contains(finding.echoedSource), (),
        "compiler echo differs from staged source")
      patch <- circeErrorMessage(CirceMessageFinding(diagnostic, reference, 3, "io.circe.Error", handwritten = true),
        document, Set(document.uri))
      admitted <- compilerReplacement(patch.action, document, Set(document.uri), "message")
      revised <- render(admitted, document)
    } yield revised
  }

  def matchesCompilerSource(session: GoalPhaseNativeRun.Session, source: InstanceRecord,
    candidate: GoalPhaseNativeCandidate.Saved, validation: GoalPhaseNativeValidation.Saved,
    transaction: Transaction, owned: Set[String]): Boolean = {
    val proof = for {
      finding <- deterministicCompilerFinding(validation.payload)
      root <- Try(Path.of(session.started.receipt.payload.value.root).normalize).toOption
      uri = root.resolve(transaction.path).toUri.toString
      reference = validation.record.revision.value.toString + ":" + finding.checkIdentity
      document = Document(uri, transaction.candidate, 0, transaction.expected)
      if root.resolve(transaction.path).toString == finding.reportedPath && transaction.validationReference == reference
      rendered <- _compiler_patch(finding, document, reference).toOption
    } yield rendered
    val metalsproof = for {
      finding <- compilerLocator(validation.payload)
      root <- Try(Path.of(session.started.receipt.payload.value.root).normalize).toOption
      if root.resolve(transaction.path).toString == finding.path &&
        java.util.Objects.equals(transaction.validationReference, validation.record.revision.value.toString + ":" + finding.checkIdentity)
      document = Document(root.resolve(transaction.path).toUri.toString, transaction.candidate, 0, transaction.expected)
      point = Position(finding.line - 1, finding.column - 1)
      diagnostic = Diagnostic("scala-compiler", document.uri, document.revision, 0, "compiler-error", Range(point, point), finding.message)
      material = CompilerMaterial(transaction.session, transaction.sourceRevision, transaction.candidate,
        transaction.validationReference, transaction.path, transaction.workspace, document, diagnostic)
      rendered <- ScalaMetalsRepair.productionRendered(material)
    } yield rendered
    retainedPrefix(validation.record, source) && retainedPrefix(candidate.record, validation.record) &&
      candidate.payload.candidate.contains(validation.payload.candidate) && transaction.candidate == validation.payload.candidate.tree &&
      candidate.payload.step == validation.payload.step && owned.contains(transaction.path) &&
      (GoalPhaseNativeCandidate.workPaths(candidate.payload): Vector[String]).contains(transaction.path: String) &&
      (proof.contains(transaction.patched) || metalsproof.contains(transaction.patched))
  }

  /** Caller supplies the admitted source. An unknown intent is never replayed. */
  def applyC(session: GoalPhaseNativeRun.Session, declaration: GoalPhaseNativeRun.Declaration,
    core: ActionCall.Core, source: InstanceRecord, transaction: Transaction,
    ownedPaths: Set[String]): Consequence[TransactionObservation] = for {
    _ <- if (validTransaction(transaction) && Option(ownedPaths).isDefined && ownedPaths.contains(transaction.path) &&
      (source.identity.value: String) == (transaction.session: String) && source.revision.value.toString == transaction.sourceRevision &&
      (transaction.workspace: String) == (RepositorySyncDirectoryList.workspace(core, "sm-workflow-repository"): String)) Consequence.unit
      else GoalPhaseNativeRun.invalid("deterministic transaction is not owned")
    existing <- retainedTransactionC(session, core, source)
    result <- existing match {
      case Some(observation) if observation.value.transaction == transaction => Consequence.success(observation)
      case Some(_) => GoalPhaseNativeRun.invalid("deterministic transaction crossed existing intent")
      case None => for {
        _ <- if (session.current == source)
          Consequence.unit else GoalPhaseNativeRun.invalid("deterministic transaction source changed")
        workspace <- WorkspaceId.parseC(transaction.workspace)
        target = FileTarget(workspace)
        path <- WorkspaceFilePath.parseC(transaction.path)
        adapter <- session.storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, transactionCodec)
        initial = TransactionRecord(transaction, "apply", None)
        intended <- _saveTransactionC(adapter, declaration, core, source, source, initial, "intent")
        written <- WorkflowFileExecution.writeTextC(core, target, path,
          transaction.patched, FileWriteMode.UpdateExisting(transaction.expected))
        outcome <- written.outcome match {
          case FileWriteOutcome.Updated => Consequence.success("updated")
          case FileWriteOutcome.Conflict(_) => Consequence.success("conflict")
          case _ => GoalPhaseNativeRun.invalid("deterministic write outcome is unknown; do not replay")
        }
        recorded = initial.copy(outcome = Some(outcome))
        next <- _saveTransactionC(adapter, declaration, core, source, intended, recorded, "result")
      } yield TransactionObservation(next, recorded)
    }
  } yield result

  def failedRevalidation(applied: TransactionObservation, source: InstanceRecord,
    candidate: GoalPhaseNativeCandidate.Saved, validation: GoalPhaseNativeValidation.Saved): Option[Rollback] = for {
    snapshot <- candidate.payload.candidate
    receipt <- validation.payload.receipts.lastOption
    _ <- GitObjectId.parseC(snapshot.head).toOption
    failedTree <- GitObjectId.parseC(snapshot.tree).toOption
    oldTree <- GitObjectId.parseC(applied.value.transaction.candidate).toOption
    if retainedPrefix(validation.record, source) && retainedPrefix(applied.record, candidate.record) &&
      retainedPrefix(candidate.record, validation.record) && applied.record.revision.value < candidate.record.revision.value &&
      candidate.record.revision.value < validation.record.revision.value &&
      applied.value.operation == "apply" && applied.value.outcome.contains("updated") &&
      GoalPhaseNativeValidation.repairable(validation.payload) && validation.payload.candidate == snapshot &&
      candidate.payload.step == validation.payload.step && failedTree != oldTree
  } yield Rollback(source.revision.value.toString, snapshot.tree,
    validation.record.revision.value.toString + ":" + receipt.identity)

  /** Reverse only the observed patch, including its selected index entry.
    * A durable rollback receipt requires both effects and the original tree. */
  def rollbackC(session: GoalPhaseNativeRun.Session, declaration: GoalPhaseNativeRun.Declaration,
    core: ActionCall.Core, source: InstanceRecord, applied: TransactionObservation,
    candidate: GoalPhaseNativeCandidate.Saved, validation: GoalPhaseNativeValidation.Saved): Consequence[TransactionObservation] = {
    val git = WorkflowManagedProcessExecution
    val transaction = applied.value.transaction
    for {
      recovery <- failedRevalidation(applied, source, candidate, validation).map(Consequence.success)
        .getOrElse(GoalPhaseNativeRun.invalid("deterministic rollback lacks its actual failed revalidation"))
      existing <- retainedTransactionC(session, core, source, "rollback")
      initial = TransactionRecord(transaction, "rollback", None, Some(recovery))
      result <- existing match {
        case Some(value) if value.value.copy(outcome = None) == initial => Consequence.success(value)
        case Some(_) => GoalPhaseNativeRun.invalid("deterministic rollback crossed its existing intent")
        case None => for {
          _ <- if (session.current == source && (transaction.workspace: String) == (RepositorySyncDirectoryList.workspace(core, "sm-workflow-repository"): String)) Consequence.unit
            else GoalPhaseNativeRun.invalid("deterministic rollback workspace or frontier changed")
          workspace <- WorkspaceId.parseC(transaction.workspace)
          path <- WorkspaceFilePath.parseC(transaction.path)
          oldtree <- GitObjectId.parseC(transaction.candidate)
          newtree <- GitObjectId.parseC(recovery.failedCandidate)
          expectedHead <- GitObjectId.parseC(validation.payload.candidate.head)
          selectedPath <- GitPath.parseC(transaction.path)
          selection <- GoalPhaseNativeCandidate.primaryRepairSelectionC(declaration.candidatePaths, transaction.path)
          _ <- GoalPhaseNativeCandidate.primaryRepairPreflightC(core, declaration.candidatePaths, Vector(transaction.path))
          root <- git.executeGitC(core, GitOperation.Root).flatMap(_.valueC)
          branch <- git.executeGitC(core, GitOperation.Branch).flatMap(_.valueC)
          head <- git.executeGitC(core, GitOperation.Head).flatMap(_.valueC)
          tree <- git.executeGitC(core, GitOperation.IndexTree).flatMap(_.valueC)
          changes <- git.executeGitC(core, GitOperation.TreeChanges(oldtree, newtree)).flatMap(_.valueC)
          change <- changes match {
            case Vector(value) if root == session.started.receipt.payload.value.root && root == declaration.target.root.toString &&
                branch == declaration.target.localBranch && head == expectedHead && tree == newtree &&
                (value.path: GitPath) == selectedPath && (value.oldMode: String) == "100644" &&
                (value.newMode: String) == "100644" =>
              Consequence.success(value)
            case _ => GoalPhaseNativeRun.invalid("deterministic rollback does not have exactly the applied file in its index")
          }
          previous <- change.oldBlob.map(Consequence.success)
            .getOrElse(GoalPhaseNativeRun.invalid("deterministic rollback lost its original blob"))
          current <- change.newBlob.map(Consequence.success)
            .getOrElse(GoalPhaseNativeRun.invalid("deterministic rollback lost its patched blob"))
          before <- git.executeGitC(core, GitOperation.BlobContent(previous)).flatMap(_.valueC)
          patched <- git.executeGitC(core, GitOperation.BlobContent(current)).flatMap(_.valueC)
          _ <- if ((before.bytes: Vector[Byte]) == (transaction.expected.getBytes(UTF_8).toVector: Vector[Byte]) &&
              (patched.bytes: Vector[Byte]) == (transaction.patched.getBytes(UTF_8).toVector: Vector[Byte])) Consequence.unit
            else GoalPhaseNativeRun.invalid("deterministic rollback source is no longer exact")
          adapter <- session.storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, transactionCodec)
          intended <- _saveTransactionC(adapter, declaration, core, source, source, initial, "intent")
          written <- WorkflowFileExecution.writeTextC(core, FileTarget(workspace), path,
            transaction.expected, FileWriteMode.UpdateExisting(transaction.patched))
          outcome <- written.outcome match {
            case FileWriteOutcome.Conflict(_) => Consequence.success("conflict")
            case FileWriteOutcome.Updated => for {
              still <- git.executeGitC(core, GitOperation.IndexTree).flatMap(_.valueC)
              _ <- if (still == newtree) Consequence.unit else GoalPhaseNativeRun.invalid("deterministic rollback index changed before staging")
              _ <- git.executeSelectedGitC(core, GitOperation.StageSelectedAll(selection.identity), selection,
                transaction.workspace).flatMap(_.valueC)
              restored <- git.executeGitC(core, GitOperation.IndexTree).flatMap(_.valueC)
              parent <- git.executeGitC(core, GitOperation.Head).flatMap(_.valueC)
              _ <- if (restored == oldtree && parent == head) Consequence.unit
                else GoalPhaseNativeRun.invalid("deterministic rollback did not observe its original index and HEAD")
            } yield "updated"
            case _ => GoalPhaseNativeRun.invalid("deterministic rollback outcome is unknown; do not replay")
          }
          recorded = initial.copy(outcome = Some(outcome))
          next <- _saveTransactionC(adapter, declaration, core, source, intended, recorded, "result")
        } yield TransactionObservation(next, recorded)
      }
    } yield result
  }

  private def _saveTransactionC(adapter: WorkflowManagedMechanicalTransition[TransactionRecord],
    declaration: GoalPhaseNativeRun.Declaration, core: ActionCall.Core, source: InstanceRecord,
    previous: InstanceRecord, payload: TransactionRecord, kind: String): Consequence[InstanceRecord] =
    adapter.executeC(WorkflowMechanicalTransition.Intent(declaration.configuration,
      _transactionReference(source, payload.operation, kind), previous, previous.revision,
      GoalPhaseNativeRun.history(previous,
        if (payload.operation == "apply" && payload.outcome.contains("updated")) "phase.InspectCandidate"
        else previous.currentProgression.map(_.value).getOrElse("phase.Repair"),
        _cause(payload.operation, kind)),
      TypedValue(transactionCodec.typeIdentity, payload)), _pure)(using core.executionContext).map(_.receipt.next)
  final case class Position(line: Int, character: Int)
  final case class Range(start: Position, end: Position)
  final case class Diagnostic(producer: String, uri: String, revision: String, version: Int,
    code: String, range: Range, message: String)
  final case class Document(uri: String, revision: String, version: Int, text: String)
  final case class Edit(uri: String, version: Int, range: Range, replacement: String)
  final case class NativeProvenance(recipe: String, evidenceReference: String, scalaMajor: Option[Int] = None,
    receiverType: Option[String] = None)
  final case class Action(producer: String, identity: String, diagnostic: Diagnostic, edits: Vector[Edit],
    nativeProvenance: Option[NativeProvenance] = None)
  final case class Patch(document: Document, action: Action)
  trait Rule {
    def accepts(document: Document, diagnostic: Diagnostic, action: Action): Boolean
  }
  final case class Limits(maxEdits: Int, maxReplacementChars: Int)

  /** Render an already admitted proposal against its exact snapshot. This pure
    * operation supplies no file authority and does not install the result.
    */
  def render(patch: Patch, current: Document): Either[String, String] = for {
    _ <- Either.cond(patch.document == current, (), "snapshot changed")
    edits <- patch.action.edits.foldLeft[Either[String, Vector[(Int, Int, String)]]](Right(Vector.empty)) {
      (acc, edit) => for {
        previous <- acc
        _ <- Either.cond(edit.uri == current.uri && edit.version == current.version && _valid_text(edit.replacement), (), "invalid edit")
        offsets <- _offsets(current.text, edit.range)
      } yield previous :+ ((offsets._1, offsets._2, edit.replacement))
    }
    ordered = edits.sortBy(_._1)
    _ <- Either.cond(ordered.nonEmpty && ordered.zip(ordered.drop(1)).forall { case (a, b) => a._2 < b._1 }, (), "ambiguous edits")
  } yield ordered.reverse.foldLeft(current.text) { case (text, (start, end, replacement)) =>
    text.substring(0, start) + replacement + text.substring(end)
  }

  /** Admit one candidate-bound compiler replacement; this grants no write authority. */
  def compilerReplacement(action: Action, document: Document, ownedUris: Set[String],
    expectedSource: String): Either[String, Patch] = {
    val limit = 4096
    val rule = new Rule {
      def accepts(document: Document, diagnostic: Diagnostic, action: Action): Boolean =
        action.edits match {
          case Vector(edit) =>
            val spans = for {
              selected <- _offsets(document.text, edit.range)
              reported <- _offsets(document.text, diagnostic.range)
            } yield (selected, reported)
            spans.exists { case ((start, end), (diagnosticStart, diagnosticEnd)) =>
              val distance = if (end < diagnosticStart) diagnosticStart - end
                else if (diagnosticEnd < start) start - diagnosticEnd else 0
              end > start && end - start <= limit && expectedSource.nonEmpty &&
                expectedSource.length <= limit && document.text.substring(start, end) == expectedSource &&
                edit.replacement != expectedSource && distance <= limit
            }
          case _ => false
        }
    }
    for {
      _ <- Either.cond(document.uri.endsWith(".scala") && !document.text.contains('\u0000') &&
        _valid_text(expectedSource) && !expectedSource.contains('\u0000') &&
        action.edits.forall(edit => !edit.replacement.contains('\u0000')), (), "invalid compiler replacement")
      patch <- admit(action, document, ownedUris, Vector(rule), Limits(1, limit))
    } yield patch
  }

  /** A trusted caller projects an actual review finding into this internal form;
    * constructing it is not proof that a check was run or a file is handwritten.
    */
  final case class TerminalLfFinding(diagnostic: Diagnostic, evidenceReference: String, handwritten: Boolean)
  /** Caller-bound projection of an actual compiler receipt and receiver type. */
  final case class CirceMessageFinding(diagnostic: Diagnostic, evidenceReference: String, scalaMajor: Int,
    receiverType: String, handwritten: Boolean)

  def circeErrorMessage(finding: CirceMessageFinding, document: Document, ownedUris: Set[String]): Either[String, Patch] = {
    val diagnostic = finding.diagnostic
    for {
      _ <- Either.cond(finding.handwritten && finding.evidenceReference.nonEmpty && finding.scalaMajor == 3 &&
        finding.receiverType == "io.circe.Error" && document.uri.endsWith(".scala") &&
        diagnostic.producer == "scala-compiler" && diagnostic.code == "E008" &&
        diagnostic.message == "value message is not a member of io.circe.Error", (), "unqualified compiler finding")
      _ <- Either.cond(_circe_message_shape(document, diagnostic), (), "compiler finding does not identify the supported source shape")
      action = Action(diagnostic.producer, "circe-error-get-message", diagnostic,
        Vector(Edit(document.uri, document.version, diagnostic.range, "getMessage")),
        Some(NativeProvenance("circe-error-get-message-v1", finding.evidenceReference,
          Some(finding.scalaMajor), Some(finding.receiverType))))
      patch <- admit(action, document, ownedUris, Vector(_circe_message_rule), Limits(1, 10))
    } yield patch
  }

  private val _circe_message_rule: Rule = new Rule {
    def accepts(document: Document, diagnostic: Diagnostic, action: Action): Boolean =
      diagnostic.producer == "scala-compiler" && diagnostic.code == "E008" &&
        diagnostic.message == "value message is not a member of io.circe.Error" &&
        action.identity == "circe-error-get-message" && action.nativeProvenance.exists(p =>
          p.recipe == "circe-error-get-message-v1" && p.evidenceReference.nonEmpty &&
            p.scalaMajor.contains(3) && p.receiverType.contains("io.circe.Error")) &&
        _circe_message_shape(document, diagnostic) &&
        action.edits == Vector(Edit(document.uri, document.version, diagnostic.range, "getMessage"))
  }

  private def _circe_message_shape(document: Document, diagnostic: Diagnostic): Boolean = {
    val lines = document.text.split("\\n", -1).toVector
    val line = diagnostic.range.start.line
    val shape = ".fold(error => fail(error.message), identity)"
    if (line < 0 || line >= lines.size) false
    else {
      val text = lines(line).stripSuffix("\r")
      val indent = text.takeWhile(character => character == ' ' || character == '\t').length
      val column = indent + shape.indexOf("message")
      val observed: Range = diagnostic.range
      val expected: Range = Range(Position(line, column), Position(line, column + 7))
      text.length - indent == shape.length && text.startsWith(shape, indent) &&
        observed == expected &&
        codeAtLine(lines, line)
    }
  }

  // Reject a matching line inside comments or literals; uncertain character literals fail closed.
  private[admission] def codeAtLine(lines: Vector[String], target: Int): Boolean = {
    val prefix = lines.take(target).mkString("\n") + (if (target > 0) "\n" else "")
    var index = 0
    var block = 0
    var string = false
    var triple = false
    var linecomment = false
    var valid = true
    while (index < prefix.length && valid) {
      val current = prefix.charAt(index)
      if (linecomment) { if (current == '\n') linecomment = false; index += 1 }
      else if (block > 0) {
        if (prefix.startsWith("/*", index)) { block += 1; index += 2 }
        else if (prefix.startsWith("*/", index)) { block -= 1; index += 2 }
        else index += 1
      } else if (triple) {
        if (prefix.startsWith("\"\"\"", index)) { triple = false; index += 3 } else index += 1
      } else if (string) {
        if (current == '\\') index += 2
        else if (current == '"') { string = false; index += 1 }
        else if (current == '\n') valid = false
        else index += 1
      } else if (prefix.startsWith("//", index)) { linecomment = true; index += 2 }
      else if (prefix.startsWith("/*", index)) { block = 1; index += 2 }
      else if (prefix.startsWith("\"\"\"", index)) { triple = true; index += 3 }
      else if (current == '"') { string = true; index += 1 }
      else if (current == '\'') valid = false
      else index += 1
    }
    valid && block == 0 && !string && !triple && !linecomment
  }

  /** Propose only removal of extra EOF LF characters. No source is changed here. */
  def terminalLf(finding: TerminalLfFinding, document: Document, ownedUris: Set[String]): Either[String, Patch] = {
    val diagnostic = finding.diagnostic
    val range = _terminal_lf_range(document.text)
    for {
      _ <- Either.cond(finding.handwritten && finding.evidenceReference.nonEmpty && document.uri.endsWith(".scala") &&
        diagnostic.producer == "phase-review" && diagnostic.code == "terminal-lf", (), "unqualified terminal LF finding")
      selected <- range.toRight("source is not an extra terminal LF case")
      _ <- Either.cond(diagnostic.range == selected, (), "diagnostic range does not identify extra terminal LF")
      action = Action(diagnostic.producer, "terminal-lf", diagnostic,
        Vector(Edit(document.uri, document.version, selected, "")),
        Some(NativeProvenance("terminal-lf-v1", finding.evidenceReference)))
      patch <- admit(action, document, ownedUris, Vector(_terminal_lf_rule), Limits(1, 0))
    } yield patch
  }

  private val _terminal_lf_rule: Rule = new Rule {
    def accepts(document: Document, diagnostic: Diagnostic, action: Action): Boolean =
      diagnostic.producer == "phase-review" && diagnostic.code == "terminal-lf" &&
        action.identity == "terminal-lf" && action.nativeProvenance.exists(p =>
          p.recipe == "terminal-lf-v1" && p.evidenceReference.nonEmpty) &&
        _terminal_lf_range(document.text).exists(range => diagnostic.range == range &&
          action.edits == Vector(Edit(document.uri, document.version, range, "")))
  }

  private def _terminal_lf_range(text: String): Option[Range] = {
    val trailing = text.reverseIterator.takeWhile(_ == '\n').length
    if (trailing < 2 || trailing == text.length || text.indexOf('\u0000') >= 0) None
    else {
      val lines = text.count(_ == '\n')
      Some(Range(Position(lines - trailing + 1, 0), Position(lines, 0)))
    }
  }

  /** Authorized documents are selected by the caller, not inferred from a URI prefix. */
  def admit(action: Action, document: Document, ownedUris: Set[String], rules: Vector[Rule],
    limits: Limits): Either[String, Patch] = {
    val diagnostic = action.diagnostic
    val valid = for {
      _ <- validateSelection(document, diagnostic, ownedUris)
      _ <- Either.cond(action.producer == diagnostic.producer && rules.exists(_.accepts(document, diagnostic, action)), (), "action not attested")
      _ <- Either.cond(limits.maxEdits > 0 && limits.maxReplacementChars >= 0 && action.edits.nonEmpty &&
        action.edits.size <= limits.maxEdits, (), "edit count outside limit")
      _ <- Either.cond(action.edits.forall(edit => edit.uri == document.uri && edit.version == document.version), (), "unowned or stale edit")
      _ <- Either.cond(action.edits.forall(edit => _valid_text(edit.replacement)) && action.edits.map(_.replacement.length.toLong).sum <= limits.maxReplacementChars.toLong, (), "replacement limit exceeded")
      _ <- _offsets(document.text, diagnostic.range)
      ranges <- action.edits.foldLeft[Either[String, Vector[(Int, Int)]]](Right(Vector.empty)) { (result, edit) =>
        for { accumulated <- result; offsets <- _offsets(document.text, edit.range) } yield accumulated :+ offsets
      }
      ordered = ranges.sorted
      _ <- Either.cond(ordered.zip(ordered.drop(1)).forall { case (left, right) => left._2 < right._1 }, (), "overlapping or ambiguous edits")
    } yield Patch(document, action)
    valid
  }

  def validateSelection(document: Document, diagnostic: Diagnostic, ownedUris: Set[String]): Either[String, Unit] = for {
    _ <- Either.cond(document.uri.nonEmpty && document.revision.nonEmpty && document.version >= 0 &&
      diagnostic.producer.nonEmpty && diagnostic.code.nonEmpty && ownedUris.contains(document.uri) &&
      diagnostic.uri == document.uri && diagnostic.revision == document.revision && diagnostic.version == document.version,
      (), "unowned, malformed or stale selection")
    _ <- Either.cond(_valid_text(document.text) && _valid_text(diagnostic.message), (), "invalid document UTF-16")
    _ <- _offsets(document.text, diagnostic.range)
  } yield ()

  private def _valid_text(text: String): Boolean = {
    var index = 0
    var valid = true
    while (index < text.length && valid) {
      val character = text.charAt(index)
      if (Character.isHighSurrogate(character)) {
        valid = index + 1 < text.length && Character.isLowSurrogate(text.charAt(index + 1))
        index += 2
      } else {
        valid = !Character.isLowSurrogate(character)
        index += 1
      }
    }
    valid
  }

  private def _offsets(text: String, range: Range): Either[String, (Int, Int)] = for {
    start <- _offset(text, range.start)
    end <- _offset(text, range.end)
    _ <- Either.cond(start <= end, (), "reversed range")
  } yield (start, end)

  // JVM string indices are UTF-16 code units. Reject a boundary inside a surrogate pair.
  private def _offset(text: String, position: Position): Either[String, Int] = {
    val lines = text.split("\\n", -1).toVector
    if (position.line < 0 || position.line >= lines.size || position.character < 0) Left("invalid position")
    else {
      val line = lines(position.line).stripSuffix("\r")
      val column = position.character
      if (column > line.length || (column > 0 && column < line.length &&
        Character.isHighSurrogate(line.charAt(column - 1)) && Character.isLowSurrogate(line.charAt(column)))) Left("invalid UTF-16 position")
      else Right(lines.take(position.line).map(_.length + 1).sum + column)
    }
  }
}
