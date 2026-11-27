package org.simplemodeling.textus.smworkflow.admission

import com.typesafe.config.ConfigFactory
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.SbtLintInput
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationResult
import scala.jdk.CollectionConverters.*
import scala.util.Try
import DeterministicRepair.*
import GoalPhaseNativeWork as W

/** Candidate-bound use of an already executed, read-only Scalafix check.
  * @since   Nov. 24, 2026
  * @version Nov. 27, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object ScalaLintRepair {
  final case class Attestation(checkIdentity: String, inputIdentity: String, inputRevision: String,
    diffBase: String, recipe: String)
  object Attestation {
    given Encoder[Attestation] = deriveEncoder
    given Decoder[Attestation] = deriveDecoder
  }
  def requestPurpose(purpose: String, restored: Boolean): String =
    purpose + (if (restored)
      " The deterministic edit was reverted after its failed revalidation. The failure capture describes the checked candidate before rollback."
    else "")

  private val _recipe = "scalafix-redundant-final-object-v1"
  private val _membership_recipe = "scalafix-vector-string-membership-v1"

  /** Narrow Scala 3 migration diagnostic whose fix is consumable by the
    * pinned Scala 2 parser. The provider must propose these exact bytes. */
  def procedureRendered(material: CompilerMaterial): Option[String] = {
    val diagnostic = material.diagnostic
    val lines = material.document.text.split("\\n", -1).toVector
    val message = "Procedure syntax no longer supported; `: Unit =` should be inserted here"
    for {
      (span, token) <- DeterministicRepair.compilerDiagnosticSelection(material)
      point = span.start
      line <- lines.lift(point.line)
      if diagnostic.message.linesIterator.exists(_.trim.endsWith(message)) &&
        token == "{" && !material.document.text.contains('\r') &&
        point.character > 0 && point.character < line.length && line.charAt(point.character) == '{' &&
        line.take(point.character).matches(" +def [A-Za-z_][A-Za-z0-9_]* ") &&
        DeterministicRepair.codeAtLine(lines, point.line)
      before = point.character - 1
      replacement = line.take(before) + ": Unit =" + line.drop(before)
    } yield lines.updated(point.line, replacement).mkString("\n")
  }

  /** Called only with the actual Observed SBT target; no current-config reread. */
  def attest(checkIdentity: String, input: SbtLintInput): Option[Attestation] = Try {
    val config = ConfigFactory.parseString(input.configJson, com.typesafe.config.ConfigParseOptions.defaults().setSyntax(com.typesafe.config.ConfigSyntax.JSON))
    val rules = config.getStringList("rules").asScala.toVector
    require(java.util.Objects.equals(input.identity, "sm-workflow-shared-scala-lint") && java.util.Objects.equals(input.revision, "1") &&
      input.configJson.getBytes(UTF_8).length <= 65536 && config.isResolved)
    val recipe = if (rules == Vector("VectorStringMembershipMigration")) _membership_recipe else {
      require(rules.count(value => java.util.Objects.equals(value, "RedundantSyntax")) == 1 &&
        config.getBoolean("RedundantSyntax.finalObject") && !config.getBoolean("RedundantSyntax.stringInterpolator"))
      _recipe
    }
    val base = config.getString("sm-workflow.scala-lint.context.diffBase")
    val proof = Attestation(checkIdentity, input.identity, input.revision, base, recipe)
    require(valid(proof))
    proof
  }.toOption

  def valid(value: Attestation): Boolean = Option(value).exists(proof => Set(_recipe, _membership_recipe)(proof.recipe)) &&
    value.inputIdentity == "sm-workflow-shared-scala-lint" && value.inputRevision == "1" &&
    Option(value.checkIdentity).exists(_.matches("[A-Za-z0-9_.-]{1,128}")) &&
    Option(value.diffBase).exists(_.matches("[0-9a-f]{40}|[0-9a-f]{64}"))

  def appendValid(before: Vector[Attestation], after: Vector[Attestation], check: String): Boolean =
    after == before || (after.size == before.size + 1 && after.dropRight(1) == before &&
      after.lastOption.exists(value => valid(value) && value.checkIdentity == check) &&
      !before.exists(_.checkIdentity == check))

  final case class Finding(checkIdentity: String, reportedPath: String, diff: Vector[String], recipe: String = _recipe)

  /** Only complete, ordinary failed FixCheck captures can locate a source. */
  def finding(payload: GoalPhaseNativeValidation.Payload): Option[Finding] = {
    val eligible = GoalPhaseNativeValidation.Payload.validLintRepairs(payload) &&
      payload.outcome.contains("repair-required") && GoalPhaseNativeValidation.repairable(payload) &&
      payload.dependency.isEmpty && payload.dependencySource.isEmpty && payload.sourceConsumption.isEmpty &&
      payload.uncertainties.isEmpty && payload.admissionFailures.isEmpty &&
      payload.receipts.zip(payload.selected).forall((receipt, check) => receipt.identity == check.identity)
    if (!eligible) None else payload.receipts.zip(payload.selected).lastOption.flatMap { (receipt, check) =>
      val proof = payload.lintRepairs.filter(_.checkIdentity == check.identity)
      val captures = receipt.processObservation
      val complete = captures.exists(p => !p.stdout.truncated && !p.stderr.truncated &&
        WorkflowSbtCapture.text(p.stdout).textStatus == "complete" && WorkflowSbtCapture.text(p.stderr).textStatus == "complete")
      if (check.kind != WorkflowSbtValidation.Kind.FixCheck || check.carLint || check.dependency.nonEmpty ||
          receipt.group.nonEmpty || !complete || receipt.termination != "exited" || !receipt.exitCode.exists(_ != 0) ||
          !RepositorySyncNativeMergeValidation.storedProductOutputValid(receipt) || proof.size != 1 || !proof.forall(value => valid(value) && value.diffBase == payload.candidate.head) ||
          payload.receipts.zip(payload.selected).dropRight(1).exists((r, c) =>
            !RepositorySyncNativeMergeValidation.productSuccessful(r, c.kind))) None
      else {
        // sbt may prefix CLI output; normalize only that literal rendering layer.
        val rawrows = Vector(receipt.stdout, receipt.stderr).flatMap(_.split("\\r?\\n", -1).toVector)
        val rows = rawrows.map(_.stripPrefix("[error] "))
        val starts = rows.zipWithIndex.collect { case (row, index) if row.startsWith("--- ") => index }
        starts match {
          case Vector(index) if rows.lift(index + 1).contains("+++ <expected fix>") =>
            val diff = rows.drop(index + 2).takeWhile(row => row.startsWith("@@ ") ||
              row.startsWith(" ") || row.startsWith("-") || row.startsWith("+"))
            val errors = rawrows.zipWithIndex.filter((row, _) => row.startsWith("[error]"))
            val acceptederrors = errors.forall { (row, ordinal) =>
              if (ordinal >= index && ordinal < index + 2 + diff.size) true
              else row.stripPrefix("[error] ") match {
                case value => value.matches("(?:\\([^\\r\\n]*scalafix\\) )?(?:scalafix\\.sbt\\.ScalafixFailed: )?(?:TestError|\\[TestError\\])") ||
                  value.matches("Total time: [^\\r\\n]+")
              }
            }
            if (diff.nonEmpty && diff.size <= 12 && diff.forall(_.length <= 4096) && acceptederrors &&
              rows.count(_.startsWith("+++ ")) == 1 && rows.count(_.startsWith("@@ ")) == 1)
              proof.headOption.map(item => Finding(check.identity, rows(index).stripPrefix("--- "), diff, item.recipe))
            else None
          case _ => None
        }
      }
    }
  }

  private def _membership_line(line: String): Option[String] = {
    val expression = """(?<![A-Za-z0-9_.$])([A-Za-z_][A-Za-z0-9_]*)\.contains\(([A-Za-z_][A-Za-z0-9_]*|"[A-Za-z0-9 _.-]*")\)""".r
    expression.findAllMatchIn(line).toVector match {
      case Vector(found) =>
        val replacement = "_root_.org.goldenport.util.TypeSafeCollections.typeSafe[_root_.scala.Predef.String](" + found.group(1) +
          ").containsEq[_root_.scala.Predef.String](" + found.group(2) +
          ")(using _root_.cats.Eq.fromUniversalEquals[_root_.scala.Predef.String])"
        Some(line.take(found.start) + replacement + line.drop(found.end))
      case _ => None
    }
  }

  /** Derive one exact edit from an authenticated, bounded one-hunk tool diff. */
  def rendered(finding: Finding, document: Document): Option[String] = {
    val header = raw"@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@".r
    val source = document.text.split("\n", -1).toVector
    val body = finding.diff.drop(1)
    val oldlines = body.collect { case row if row.startsWith(" ") || row.startsWith("-") => row.drop(1) }
    val newlines = body.collect { case row if row.startsWith(" ") || row.startsWith("+") => row.drop(1) }
    finding.diff.headOption.flatMap {
      case header(oldstart, oldcount, newstart, newcount) => for {
        start <- oldstart.toIntOption.filter(_ > 0).map(_ - 1)
        count <- Option(oldcount).fold(Option(1))(_.toIntOption)
        nextcount <- Option(newcount).fold(Option(1))(_.toIntOption)
        if oldstart == newstart && count == oldlines.size && nextcount == newlines.size && count == nextcount &&
          count > 0 && count <= 7 && source.slice(start, start + count) == oldlines && document.text.endsWith("\n") &&
          !document.text.contains('\r') && body.forall(row => row.headOption.exists(Set(' ', '-', '+')))
        removed <- body.filter(_.startsWith("-")) match { case Vector(value) => Some(value.drop(1)); case _ => None }
        added <- body.filter(_.startsWith("+")) match { case Vector(value) => Some(value.drop(1)); case _ => None }
        replacement <- if (finding.recipe == _recipe && removed.matches(" *final object [A-Za-z_][A-Za-z0-9_]* \\{\\}?")) {
          val indent = removed.takeWhile(_ == ' ').length
          Some(removed.take(indent) + removed.drop(indent + 6))
        } else if (finding.recipe == _membership_recipe) _membership_line(removed) else None
        if java.util.Objects.equals(added, replacement)
        indent = removed.takeWhile(_ == ' ').length
        offset <- oldlines.zipWithIndex.collect { case (row, index) if row == removed && newlines.lift(index).contains(added) => index } match {
          case Vector(value) => Some(value); case _ => None
        }
        line = start + offset
        if DeterministicRepair.codeAtLine(source, line) &&
          source.take(line).mkString("\n").length <= 1048576 &&
          oldlines.updated(offset, added) == newlines
        // The CLI uses exactly three lines of context on either side.
        if start == math.max(0, line - 3) && start + count == math.min(source.size - 1, line + 4)
        range = if (finding.recipe == _recipe) Range(Position(line, indent), Position(line, indent + 6))
          else Range(Position(line, 0), Position(line, removed.length))
        inserted = if (finding.recipe == _recipe) "" else added
        diagnostic = Diagnostic("scalafix", document.uri, document.revision, document.version,
          finding.recipe, range, finding.recipe)
        action = Action("scalafix", finding.recipe, diagnostic,
          Vector(Edit(document.uri, document.version, diagnostic.range, inserted)))
        patch <- admit(action, document, Set(document.uri), Vector(new Rule {
          def accepts(document: Document, diagnostic: Diagnostic, action: Action): Boolean =
            action.identity == finding.recipe && action.edits == Vector(Edit(document.uri, document.version, diagnostic.range, inserted))
        }), Limits(1, 4096)).toOption
        text <- render(patch, document).toOption
      } yield text
      case _ => None
    }
  }

  private def _ordinary(candidate: GoalPhaseNativeCandidate.Saved, validation: GoalPhaseNativeValidation.Saved,
    source: InstanceRecord): Boolean =
    DeterministicRepair.retainedPrefix(candidate.record, validation.record) &&
      DeterministicRepair.retainedPrefix(validation.record, source) &&
      candidate.payload.candidate.contains(validation.payload.candidate) && candidate.payload.step == validation.payload.step &&
      candidate.payload.committedWork.isEmpty && candidate.payload.continuation.isEmpty &&
      candidate.payload.dependencyAdmission.isEmpty && candidate.payload.dependency.isEmpty &&
      candidate.payload.dependencyOrigin.isEmpty && candidate.payload.dependencyStaged.isEmpty &&
      candidate.payload.dependencySourceDirection.isEmpty && candidate.payload.dependencySourceGuard.isEmpty &&
      candidate.payload.dependencySourceStages.isEmpty && candidate.payload.dependencySourceCapture.isEmpty &&
      candidate.payload.dependencySourceScope.isEmpty && candidate.payload.primaryPublicationRepair.isEmpty &&
      validation.payload.dependency.isEmpty && validation.payload.dependencySource.isEmpty && validation.payload.sourceConsumption.isEmpty

  private def _handwritten(path: String, owned: Set[String], candidate: GoalPhaseNativeCandidate.Saved): Boolean =
    GoalPhasePurposeRepair.path(path) && path.endsWith(".scala") && owned.contains(path) &&
      (path.startsWith("src/main/scala/") || path.startsWith("src/test/scala/")) &&
      !path.split('/').exists(part => Set("generated", "src_managed", "target")(part)) &&
      (GoalPhaseNativeCandidate.workPaths(candidate.payload): Vector[String]).contains(path)

  def prepareC(session: GoalPhaseNativeRun.Session, declaration: GoalPhaseNativeRun.Declaration, core: ActionCall.Core,
    source: InstanceRecord, candidate: GoalPhaseNativeCandidate.Saved, validation: GoalPhaseNativeValidation.Saved,
    owned: Set[String]): Consequence[Option[Transaction]] = {
    val selected = for {
      located <- finding(validation.payload)
      root <- Try(Path.of(session.started.receipt.payload.value.root)).toOption
      reported <- Try(Path.of(located.reportedPath)).toOption
      if root.isAbsolute && reported.isAbsolute && reported.normalize == reported && reported.startsWith(root) &&
        _ordinary(candidate, validation, source)
      path = root.relativize(reported).toString
      if _handwritten(path, owned, candidate)
    } yield (located, root, path)
    selected.fold(Consequence.success(Option.empty[Transaction])) { (located, root, path) =>
      DeterministicRepair.sourceC(declaration, core, root, path, validation.payload.candidate).map(_.flatMap { (text, workspace) =>
        val reference = validation.record.revision.value.toString + ":" + located.checkIdentity
        rendered(located, Document(root.resolve(path).toUri.toString, validation.payload.candidate.tree, 0, text)).map(patched =>
          Transaction(source.identity.value, source.revision.value.toString, validation.payload.candidate.tree,
            reference, path, text, patched, workspace)).filter(validTransaction)
      })
    }
  }

  def matches(session: GoalPhaseNativeRun.Session, source: InstanceRecord, candidate: GoalPhaseNativeCandidate.Saved,
    validation: GoalPhaseNativeValidation.Saved, transaction: Transaction, owned: Set[String]): Boolean = {
    val proof = for {
      located <- finding(validation.payload)
      root <- Try(Path.of(session.started.receipt.payload.value.root)).toOption
      if _ordinary(candidate, validation, source) && _handwritten(transaction.path, owned, candidate) &&
        root.resolve(transaction.path).toString == located.reportedPath && transaction.candidate == validation.payload.candidate.tree &&
        java.util.Objects.equals(transaction.validationReference, validation.record.revision.value.toString + ":" + located.checkIdentity)
      text <- rendered(located, Document(root.resolve(transaction.path).toUri.toString, transaction.candidate, 0, transaction.expected))
    } yield text
    proof.contains(transaction.patched)
  }

  def attemptC(session: GoalPhaseNativeRun.Session, declaration: GoalPhaseNativeRun.Declaration, core: ActionCall.Core,
    source: InstanceRecord, controller: GoalPhaseOrderedProgression.State,
    candidate: Option[GoalPhaseNativeCandidate.Saved], validation: Option[GoalPhaseNativeValidation.Saved],
    repairs: Map[String, TransactionObservation], lastResult: Option[(W.Request, ContinuationResult[W.Result])],
    issues: Option[GoalPhaseFixIssues.State]): Consequence[Option[TransactionObservation]] =
    stepContext(controller, candidate, validation).filterNot(context => repairs.contains(context._1.identity))
      .fold(Consequence.success(Option.empty[TransactionObservation])) { (step, savedcandidate, savedvalidation) => for {
        lint <- prepareC(session, declaration, core, source, savedcandidate, savedvalidation, step.ownedPaths.toSet)
        proposal <- lint.fold(terminalC(session, declaration, core, source, controller, savedcandidate,
          savedvalidation, lastResult, issues))(value => Consequence.success(Some(value)))
        result <- proposal.fold(Consequence.success(Option.empty[TransactionObservation]))(value =>
          applyC(session, declaration, core, source, value, step.ownedPaths.toSet).map(Some(_)))
      } yield result }

  /** The original admitted review must have only one terminal-LF finding. */
  def terminalPath(request: W.Request, result: W.Result, candidate: GoalPhaseNativeCandidate.Saved,
    validation: GoalPhaseNativeValidation.Saved): Option[String] = for {
    conditions <- result.completionConditions
    path <- conditions.paths match { case Vector(value) => Some(value); case _ => None }
    step <- request.stepIdentity.flatMap(id => request.selectedPlan.steps.find(_.identity == id))
    if request.kind == "ReviewStep" && result.outcome == "repair" && result.findings.size == 1 &&
      conditions.kind == "terminal-lf" &&
      GoalPhaseNativeReviewCompletion.admitResultC(request.kind, result, Some(step.ownedPaths)).isSuccess &&
      GoalPhaseAcceptanceBoundary.admitRequestC(request, result).isSuccess &&
      _handwritten(path, step.ownedPaths.toSet, candidate) &&
      request.candidate == candidate.payload.candidate && request.candidate.contains(validation.payload.candidate) &&
      request.checksReceipt.contains(validation.record.identity.value + ":ordered-validation:" + validation.payload.sourceRevision + ":validation") &&
      request.selectedPlan.steps.exists(s => s.identity == validation.payload.step && s.identity == candidate.payload.step) &&
      validation.payload.outcome.contains("passed") && validation.payload.receipts.size == validation.payload.selected.size &&
      GoalPhaseNativeValidation.successful(validation.payload.receipts, validation.payload.selected)
  } yield path

  private def _terminal_proof(source: InstanceRecord, controller: GoalPhaseOrderedProgression.State,
    candidate: GoalPhaseNativeCandidate.Saved, validation: GoalPhaseNativeValidation.Saved,
    lastresult: Option[(W.Request, ContinuationResult[W.Result])], issues: Option[GoalPhaseFixIssues.State]): Option[(String, String)] = for {
    (request, result) <- lastresult
    path <- terminalPath(request, result.result.value, candidate, validation)
    if _ordinary(candidate, validation, source) && request.selectedPlan == controller.plan &&
      request.generation + 1 == controller.generation && controller.stage == GoalPhaseOrderedProgression.Stage.Repair &&
      controller.repairDirective.contains(GoalPhaseOrderedProgression.RepairDirective(result.result.value.summary, result.result.value.findings)) &&
      java.util.Objects.equals(source.causation.value, result.resultReference.identity) && java.util.Objects.equals(request.instanceIdentity, source.identity.value) &&
      java.util.Objects.equals(result.result.typeIdentity, W.resultCodec.typeIdentity) && result.resultReference.identity.nonEmpty && result.resultReference.revision.nonEmpty
    open = issues.toVector.flatMap(_.issues).filter(_.status == "OPEN")
    if open.size == 1 && open.forall(issue => java.util.Objects.equals(issue.evidenceReference, result.resultReference.identity) &&
      issue.step == validation.payload.step && issue.source == "REVIEW" && result.result.value.findings.headOption.contains(issue.summary))
  } yield path -> (result.resultReference.identity + "@" + result.resultReference.revision)

  private def _terminal_render(document: Document, reference: String): Option[String] = {
    val trailing = document.text.reverseIterator.takeWhile(_ == '\n').length
    val lines = document.text.count(_ == '\n')
    val range = Range(Position(lines - trailing + 1, 0), Position(lines, 0))
    val diagnostic = Diagnostic("phase-review", document.uri, document.revision, document.version, "terminal-lf", range, "terminal-lf")
    terminalLf(TerminalLfFinding(diagnostic, reference, handwritten = true), document, Set(document.uri)).toOption
      .flatMap(patch => render(patch, document).toOption)
  }

  def terminalC(session: GoalPhaseNativeRun.Session, declaration: GoalPhaseNativeRun.Declaration, core: ActionCall.Core,
    source: InstanceRecord, controller: GoalPhaseOrderedProgression.State, candidate: GoalPhaseNativeCandidate.Saved,
    validation: GoalPhaseNativeValidation.Saved, lastResult: Option[(W.Request, ContinuationResult[W.Result])],
    issues: Option[GoalPhaseFixIssues.State]): Consequence[Option[Transaction]] =
    _terminal_proof(source, controller, candidate, validation, lastResult, issues).fold(Consequence.success(Option.empty[Transaction])) {
      (path, reference) =>
        val root = Path.of(session.started.receipt.payload.value.root)
        DeterministicRepair.sourceC(declaration, core, root, path, validation.payload.candidate).map(_.flatMap { (text, workspace) =>
          _terminal_render(Document(root.resolve(path).toUri.toString, validation.payload.candidate.tree, 0, text), reference).map(patched =>
            Transaction(source.identity.value, source.revision.value.toString, validation.payload.candidate.tree,
              reference, path, text, patched, workspace)).filter(validTransaction)
        })
    }

  def matchesTerminal(source: InstanceRecord, controller: GoalPhaseOrderedProgression.State,
    candidate: GoalPhaseNativeCandidate.Saved, validation: GoalPhaseNativeValidation.Saved, transaction: Transaction,
    lastResult: Option[(W.Request, ContinuationResult[W.Result])], issues: Option[GoalPhaseFixIssues.State]): Boolean =
    _terminal_proof(source, controller, candidate, validation, lastResult, issues).exists { (path, reference) =>
      path == transaction.path && transaction.validationReference == reference && transaction.candidate == validation.payload.candidate.tree &&
        _terminal_render(Document("file:///" + path, transaction.candidate, 0, transaction.expected), reference).contains(transaction.patched)
    }
}
