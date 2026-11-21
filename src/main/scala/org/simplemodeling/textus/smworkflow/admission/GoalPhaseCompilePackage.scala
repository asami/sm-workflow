/**
 * @since   Nov. 21, 2026
 * @version Nov. 21, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Codec, Json}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation, GitPath, GitSelectionId, GitSelectionParameters}
import scala.util.Try

/** Supply a bounded compile failure with exact source from its validated tree. */
private[smworkflow] object GoalPhaseCompilePackage {
  import GoalPhaseNativeValidation as N
  import IncrementalSbtDiagnosisInformation.{Item, Selection}

  val kind = "compile-package"
  val sourceKind = "candidate-source"
  private val _schema = "sm-workflow.supplied-compile-failure.v1"
  final case class Source(path: String, blob: String, text: String)
  final case class Hygiene(path: String, bytes: Int, blob: String, threshold: Int) derives Codec.AsObject
  final case class Material(session: String, work: String, revision: String, purpose: String,
    receipt: String, validation: N.Payload, ownedPaths: Vector[String], contract: Json)

  def selected(selection: Selection): Boolean = selection.kind == kind || selection.kind == sourceKind

  def prepareC(core: ActionCall.Core, root: Path, work: GoalPhaseNativeWork.Request, workId: String, revision: String,
    selections: Vector[Selection]): Consequence[Vector[Item]] =
    for {
      limit <- GoalPhaseApiReview.packageLimitC(core)
      selected = for {
        step <- work.selectedPlan.steps.find(s => work.stepIdentity.contains(s.identity))
        failure <- work.checkFailure.filter(f => f.validation.step == step.identity &&
          f.initialPhaseRepair.isEmpty && f.stepTestReportRepair.isEmpty)
        directive <- work.repairDirective
      } yield Material(work.instanceIdentity, workId, revision, work.purpose,
        validationSource(work.instanceIdentity, failure.validation).identity, failure.validation, step.ownedPaths,
        Json.obj("plan" -> work.selectedPlan.identity.asJson, "planRevision" -> work.selectedPlan.revision.asJson,
          "step" -> step.identity.asJson, "acceptance" -> work.selectedPlan.acceptance.asJson,
          "effectiveAcceptance" -> work.selectedPlan.effectiveAcceptance.asJson,
          "reason" -> directive.reason.asJson, "findings" -> directive.findings.asJson))
      items <- prepareSelectionC(selected, root, selections, limit, (tree, paths) => _read_sources_c(core, tree, paths))
    } yield items

  def validationSource(session: String, payload: N.Payload): DevelopmentJournalSemantic.Source =
    DevelopmentJournalSemantic.Source(session + ":ordered-validation:" + payload.sourceRevision + ":validation", "1")

  private[admission] def prepareSelectionC(material: Option[Material], root: Path,
    selections: Vector[Selection], limit: Int, readC: (String, Vector[String]) => Consequence[Vector[Source]]):
    Consequence[Vector[Item]] = {
    val packageSelection = selections.filter(_.kind == kind)
    val sources = selections.filter(_.kind == sourceKind)
    val paths = sources.flatMap(_.path)
    val shaped = packageSelection match {
      case Vector(value) => value.check.nonEmpty && value.path.isEmpty && value.stream.isEmpty &&
        value.occurrenceLine.isEmpty && sources.nonEmpty && sources.size + 1 == selections.size &&
        paths.size == sources.size && paths.distinct.size == paths.size && sources.forall(s =>
          s.check.isEmpty && s.stream.isEmpty && s.occurrenceLine.isEmpty)
      case _ => false
    }
    if (!shaped) Consequence.success(selections.map(s =>
      _decline(s, "Select one compile-package and each complete candidate-source in the same request")))
    else material match {
      case None => Consequence.success(selections.map(s => _decline(s, "No current Step compile repair contract")))
      case Some(value) => _prepare_c(value, root, packageSelection.head, sources, limit, readC).map { prepared =>
        val byIdentity = prepared.map(item => item.selection.identity -> item).toMap
        selections.map(s => byIdentity(s.identity))
      }
    }
  }

  private def _prepare_c(material: Material, root: Path, request: Selection, sources: Vector[Selection], limit: Int,
    readC: (String, Vector[String]) => Consequence[Vector[Source]]): Consequence[Vector[Item]] = {
    val paths = sources.map(_.path.get)
    def decline(reason: String) =
      (request +: sources).map(s => _decline(s, reason))
    val eligible = for {
      (check, receipt) <- material.validation.selected.zip(material.validation.receipts).lastOption
      if material.validation.outcome.contains("repair-required") && N.repairable(material.validation)
      if receipt.identity == check.identity && request.check.contains(check.identity) &&
        check.dependency.isEmpty && material.validation.dependency.isEmpty &&
        material.validation.dependencySource.isEmpty && material.validation.sourceConsumption.isEmpty && !check.carLint
      if Set(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Kind.TestCompile)(check.kind)
      process <- receipt.processObservation
      stdout = WorkflowSbtCapture.text(process.stdout)
      stderr = WorkflowSbtCapture.text(process.stderr)
      if stdout.textStatus == "complete" && stderr.textStatus == "complete"
      diagnosis = IncrementalSbtFailureDiagnosis.extract(receipt, check.kind, material.validation.sourceRevision,
        material.validation.candidate)
      locations <- _locations(root, diagnosis)
      if locations.forall(paths.contains)
      if paths.forall(p => GitPath.parseC(p).isSuccess &&
        material.ownedPaths.exists(owner => p == owner || p.startsWith(owner + "/")))
    } yield (check, stdout.value, stderr.value)
    eligible match {
      case None => Consequence.success(decline("Complete owned source and retained compiler diagnostics are unavailable"))
      case Some((check, stdout, stderr)) =>
        // An unavailable managed read is an ordinary reason to use the editor.
        val tree = material.validation.candidate.tree
        readC(tree, paths.sorted).map { observed =>
          val files = observed.sortBy(_.path)
          val hygiene = files.flatMap(_hygiene(_, limit))
          val complete = files.map(_.path) == paths.sorted
          def packet = Json.obj("schema" -> _schema.asJson, "session" -> material.session.asJson,
            "continuation" -> material.work.asJson, "revision" -> material.revision.asJson,
            "purpose" -> material.purpose.asJson, "validation" -> material.receipt.asJson,
            "sourceRevision" -> material.validation.sourceRevision.asJson,
            "candidate" -> Json.obj("head" -> material.validation.candidate.head.asJson, "tree" -> tree.asJson),
            "check" -> check.identity.asJson, "kind" -> check.wireKind.asJson, "ownedPaths" -> material.ownedPaths.asJson,
            "contract" -> material.contract, "completenessSource" -> request.reason.asJson,
            "diagnostics" -> Json.obj("stdout" -> stdout.asJson, "stderr" -> stderr.asJson),
            "files" -> Json.fromValues(files.map(s => Json.obj(
              "path" -> s.path.asJson, "blob" -> s.blob.asJson, "content" -> s.text.asJson)))).noSpaces
          val minimumBytes = files.map(_.text.getBytes(UTF_8).length.toLong).sum +
            stdout.getBytes(UTF_8).length + stderr.getBytes(UTF_8).length
          val item = if (!complete) _decline(request, "A selected candidate source is unavailable")
            else if (minimumBytes > limit) _decline(request, s"Complete package exceeds $limit bytes before JSON framing")
            else {
              val encoded = packet
              val bytes = encoded.getBytes(UTF_8).length
              if (bytes > limit) _decline(request, s"Complete package requires $bytes bytes and limit is $limit")
              else Item(request, "available", "complete-candidate-package", "none", "tree:" + tree,
                Some(encoded), s"Eligible supplied compile input, $bytes bytes")
            }
          val result = Map(request.identity -> item.copy(hygiene = hygiene)) ++ sources.map { s =>
            val source = files.find(f => s.path.contains(f.path))
            s.identity -> source.fold(_decline(s, "Candidate source unavailable"))(f =>
              Item(s, "available", "whole-candidate-file", "none", "blob:" + f.blob, None,
                "Complete candidate source selected for the package"))
          }
          result
        }.map(values => (request +: sources).map(s => values(s.identity)))
          .recoverWith(_ => Consequence.success(decline("Managed candidate-tree source read unavailable")))
    }
  }

  private def _locations(root: Path, view: IncrementalSbtFailureDiagnosis.View): Option[Vector[String]] = {
    val groups = view.groups.filter(_.kind == "compiler-error")
    val raw = groups.flatMap(_.occurrences.map(_.location))
    if (view.omittedGroups != 0 || groups.isEmpty || groups.exists(_.omittedOccurrences > 0) ||
        raw.exists(_.isEmpty)) None
    else {
      val paths = raw.flatten.map(location => Try {
        val absolute = root.resolve(location.file).normalize()
        Option.when(absolute.startsWith(root) && absolute != root)(root.relativize(absolute).toString)
      }.toOption.flatten)
      Option.when(paths.forall(_.nonEmpty))(paths.flatten.distinct)
    }
  }

  private def _read_sources_c(core: ActionCall.Core, tree: String, paths: Vector[String]): Consequence[Vector[Source]] = for {
    objectId <- GitObjectId.parseC(tree)
    identity <- GitSelectionId.parseC("compile-package-files")
    parsed <- paths.foldLeft(Consequence.success(Vector.empty[GitPath])) { (prior, path) => for {
      values <- prior
      p <- GitPath.parseC(path)
    } yield values :+ p }
    selection = GitSelectionParameters(identity, parsed)
    rows <- WorkflowManagedProcessExecution.executePurposeC(core, GitOperation.TreeEntries(objectId, identity), selection).flatMap(_.valueC)
    sources = rows.flatMap(row => row.entry.filter(e => Set("100644", "100755")(e.mode)).flatMap { e =>
      val observed = WorkflowManagedProcessExecution.executeGitC(core, GitOperation.BlobContent(e.blob)).flatMap(_.valueC)
      observed.toOption.flatMap(blob => blob.text.filter(t => !t.contains('\u0000') &&
        t.getBytes(UTF_8).toVector == blob.bytes).map(t => Source(row.path.value, e.blob.value, t)))
    })
  } yield sources

  private[admission] def _hygiene(source: Source, threshold: Int): Option[Hygiene] = {
    val bytes = source.text.getBytes(UTF_8).length
    val parts = source.path.split("/").toVector
    val filename = parts.lastOption.getOrElse("")
    val repositorySource = filename.endsWith(".scala") || filename.endsWith(".java")
    val ordinary = !parts.exists(Set("target", "src_managed", "generated", "generated-sources", "source_managed"))
    val generatorHeader = source.text.linesIterator.take(8).exists(line => {
      val text = line.toLowerCase(java.util.Locale.ROOT)
      text.contains("generated by") || text.contains("automatically generated") || text.contains("do not edit")
    })
    Option.when(bytes > threshold && repositorySource && ordinary && !generatorHeader)(
      Hygiene(source.path, bytes, source.blob, threshold))
  }

  private def _decline(s: Selection, reason: String): Item =
    Item(s, "unavailable", "agent-required", "unverified", "", None, reason)
}
