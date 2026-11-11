package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.syntax.*
import java.net.URLClassLoader
import java.nio.file.Path
import scala.util.control.NonFatal
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationOperationBindingV1 as B}
import org.goldenport.cncf.workspace.WorkspaceId
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation, ProcessExecutionResult, ProcessExecutionTermination,
  SbtValidationDiscovery as SD, SbtValidationMode as SM, SbtExecutionAttempt}
import GoalPhaseNativeMetadata.given
import WorkflowSbtValidation.given
import RepositorySyncNativeProgressStore as N
import RepositorySyncNativeMergeValidation as V

/** Declared impact and consumption scopes select existing managed checks.
  * References locate native records; they never supply successful receipts or
  * review acceptance. Exact consumed Git blobs, modes and paths decide reuse.
  * @since   Oct. 28, 2026
  * @version Nov.  8, 2026
  */
private[smworkflow] object RepositorySyncProgramImpact {
  final case class BuildRecord(workspace: String, binding: Option[SD.Binding], process: ProcessExecutionResult) {
    def available: Option[SD.Build] = binding.flatMap { value =>
      WorkspaceId.parseC(workspace).toOption.flatMap(id => SD.decode(id, value, process) match {
        case SD.Result.Available(build) => Some(build)
        case _ => None
      })
    }
  }
  final case class BatchRecord(cases: Vector[SM.Case], facts: WorkflowSbtValidation.Facts, process: ProcessExecutionResult) {
    def valid: Boolean = cases != null && cases.nonEmpty && facts != null && facts.batch.exists(_.map(_.selected) == cases) &&
      WorkflowSbtCapture.valid(process, facts)
  }
  final case class Group(sourceTree: String, workspace: String, build: Option[BuildRecord] = None,
    inventory: Option[D.Inventory] = None, cases: Vector[SM.Case] = Vector.empty,
    planned: Vector[Vector[SM.Case]] = Vector.empty, batches: Vector[BatchRecord] = Vector.empty,
    pending: Option[String] = None, refusal: Option[String] = None) {
    def valid: Boolean = sourceTree != null && GitObjectId.parseC(sourceTree).isSuccess &&
      WorkspaceId.parseC(workspace).isSuccess && build != null && inventory != null && cases != null &&
      planned != null && batches != null && pending != null && refusal != null &&
      build.forall(value => value != null && value.workspace == workspace && value.process != null) &&
      (inventory.isEmpty || (build.exists(_.available.nonEmpty) && inventory.exists(value =>
        value.scope.artifacts.forall(_.sourceRevision == sourceTree)) && cases.nonEmpty && planned.flatten == cases)) &&
      batches.size <= planned.size && batches.zip(planned).forall((actual, expected) =>
        actual != null && actual.valid && actual.cases == expected)
    def successful: Boolean = valid && refusal.isEmpty && pending.isEmpty && build.exists(_.available.nonEmpty) &&
      inventory.exists(_.completeness == D.Completeness.COMPLETE) && cases.nonEmpty &&
      batches.size == planned.size && batches.forall(_.facts.outcome == WorkflowSbtValidation.Outcome.Succeeded)
    def knownFailure: Boolean = build.exists(_.process.termination match {
      case ProcessExecutionTermination.Exited(code) => code != 0
      case _ => false
    }) || batches.exists(_.facts.outcome == WorkflowSbtValidation.Outcome.Failed)
  }
  private given Encoder[ProcessExecutionResult] = Encoder.instance(WorkflowSbtCapture.encode)
  private given Decoder[ProcessExecutionResult] = Decoder.instance(cursor =>
    WorkflowSbtCapture.decode(cursor.value).left.map(DecodingFailure(_, cursor.history)))
  private given Encoder[SD.Binding] = deriveEncoder
  private given Decoder[SD.Binding] = deriveDecoder
  private given Encoder[BuildRecord] = deriveEncoder
  private given Decoder[BuildRecord] = deriveDecoder
  private given Encoder[BatchRecord] = deriveEncoder
  private given Decoder[BatchRecord] = deriveDecoder
  object Group {
    given Encoder[Group] = deriveEncoder
    given Decoder[Group] = deriveDecoder
  }

  def groupReceipt(identity: String, group: Group): V.Receipt = V.Receipt(identity,
    if (group.successful) "group-completed" else if (group.knownFailure) "group-failed" else "group-incomplete",
    None, "", "", group = Some(group))

  /** Discovery is causally downstream of an actual managed build between native candidate observations. */
  def selectBuiltC(check: V.Check, sourceTree: String, build: BuildRecord): Consequence[(D.Inventory, Vector[SM.Case])] = for {
    observed <- build.available.map(Consequence.success).getOrElse(N.invalid("Ordinary build discovery is unavailable"))
    _ <- if (observed.products.size == 1) Consequence.unit else N.invalid("Ordinary discovery currently requires one product root for one artifact identity")
    scope = D.DiscoveryScope(observed.binding.artifactIdentity, observed.binding.artifactRevision,
      Vector(D.ArtifactScope(observed.binding.componentIdentity, observed.binding.artifactIdentity,
        observed.binding.artifactRevision, sourceTree, observed.products.head, observed.binding.packagePrefix)))
    query = check.metadata.map(_.query).getOrElse(D.SelectionQuery(standardFramework = true))
    inventory <- try {
      val loader = new URLClassLoader(observed.fullClasspath.map(_.toUri.toURL).toArray, classOf[D.Inventory].getClassLoader)
      try D.discoverC(scope, loader, query.explicitSuites) finally loader.close()
    } catch { case NonFatal(error) => N.invalid("Ordinary compiled test discovery is unavailable: " + error.getClass.getSimpleName) }
    _ <- if (inventory.completeness == D.Completeness.COMPLETE) Consequence.unit else N.invalid("Ordinary compiled test discovery is incomplete")
    selected <- D.selectC(inventory, query)
    references = (selected.map(_.reference) ++ check.metadata.toVector.flatMap(_.supplemental)).distinct
    workspace <- WorkspaceId.parseC(build.workspace)
    cases <- references.foldLeft(Consequence.success(Vector.empty[SM.Case])) { (prior, reference) => for {
      values <- prior
      variants <- B.bindVariantsC(inventory, reference, workspace)
    } yield values ++ variants.map(bound => SM.Case(bound.intent.target.scenario.get,
      bound.intent.target.validationMode.get, bound.intent.target.validationProvider)) }
    _ <- if (cases.nonEmpty && cases.distinct == cases) Consequence.unit else N.invalid("Ordinary FULL has no complete distinct runnable variants")
  } yield (inventory, cases)

  private def _group_payload(stage: N.Stage, identity: String, group: Group): N.Payload = {
    val state = stage.payload.validation.get
    stage.payload.copy(validation = Some(state.copy(groups = state.groups.updated(identity, group))))
  }

  /** One legacy logical check retains every native build/batch occurrence. An intent without result is never replayed. */
  def executeOrdinaryC(store: N.Store, candidate: N.Stage, prepared: N.Stage, check: V.Check,
    index: Int, namespace: String): Consequence[N.Stage] = {
    val prefix = "ordinary-" + index
    def _key_(suffix: String): String = N.candidateKey(namespace, prefix + "-" + suffix)
    def _group_(stage: N.Stage): Group = stage.payload.validation.get.groups.getOrElse(check.identity,
      Group(candidate.payload.indexTree.get, check.workspaceIdentity))
    def _load_result_(suffix: String, from: N.Stage): Consequence[Option[N.Stage]] = for {
      intent <- store.loadC(_key_(suffix + "-intent"))
      result <- store.loadC(_key_(suffix + "-result"))
      _ <- result.fold(Consequence.unit) { saved =>
        if (intent.exists(start => start.record.revision == from.record.revision.next &&
            start.record.history.dropRight(1) == from.record.history && saved.record.revision == start.record.revision.next &&
            saved.record.history.dropRight(1) == start.record.history) &&
            _group_(saved).sourceTree == candidate.payload.indexTree.get && _group_(saved).workspace == check.workspaceIdentity)
          Consequence.unit else N.invalid("Ordinary retained occurrence crosses its actual native predecessor or candidate")
      }
      _ <- if (intent.nonEmpty && result.isEmpty) N.uncertain("Ordinary " + suffix + " has a retained intent without result; UNKNOWN is not replayed") else Consequence.unit
    } yield result
    for {
      _ <- V.preconditionsC(store, candidate)
      built <- _load_result_("build", prepared).flatMap {
        case Some(value) => Consequence.success(value)
        case None => for {
          intent <- store.saveC(_key_("build-intent"), "native-check-prepared", prepared.record,
            _group_payload(prepared, check.identity, _group_(prepared).copy(pending = Some("build"))))
          actual <- WorkflowManagedProcessExecution.discoveryBuildAttemptC(store.core, check.workspaceIdentity)
          group <- actual match {
            case SbtExecutionAttempt.Observed(result) =>
              val binding = result.discovery.collect { case SD.Result.Available(value) => value.binding }
              val unavailable = if (result.processResult.termination == ProcessExecutionTermination.Exited(0))
                result.discovery.collect { case SD.Result.Unavailable(reason) => reason } else None
              Consequence.success(_group_(intent).copy(build = Some(BuildRecord(check.workspaceIdentity, binding, result.processResult)),
                pending = None, refusal = unavailable))
            case SbtExecutionAttempt.AdmissionRejected(_, _, conclusion) =>
              Consequence.success(_group_(intent).copy(pending = None, refusal = Some(conclusion.show)))
            case SbtExecutionAttempt.Uncertain(_, _, conclusion) => N.uncertain("Ordinary build remains UNKNOWN: " + conclusion.show)
          }
          recorded <- store.saveC(_key_("build-result"), "native-check-recorded", intent.record, _group_payload(intent, check.identity, group))
          _ <- store.acknowledgeC("ordinary-build-recorded:" + check.identity)
        } yield recorded
      }
      _ <- V.preconditionsC(store, candidate)
      selected <- if (_group_(built).refusal.nonEmpty || !_group_(built).build.exists(_.available.nonEmpty)) Consequence.success(built)
        else store.loadC(_key_("selection-refused")).flatMap {
          case Some(value) if value.record.revision == built.record.revision.next && value.record.history.dropRight(1) == built.record.history =>
            Consequence.success(value)
          case Some(_) => N.invalid("Ordinary retained setup refusal crosses its actual build occurrence")
          case None => store.loadC(_key_("selection")).flatMap {
          case Some(value) if value.record.revision == built.record.revision.next && value.record.history.dropRight(1) == built.record.history &&
              _group_(value).build == _group_(built).build => Consequence.success(value)
          case Some(_) => N.invalid("Ordinary retained selection crosses its actual build occurrence")
          case None => for {
            selection <- selectBuiltC(check, candidate.payload.indexTree.get, _group_(built).build.get)
            workspace <- WorkspaceId.parseC(check.workspaceIdentity)
            planned <- SM.groupCasesC(store.core.executionContext, workspace, selection._2)
            group = _group_(built).copy(inventory = Some(selection._1), cases = selection._2, planned = planned)
            saved <- store.saveC(_key_("selection"), "native-check-recorded", built.record, _group_payload(built, check.identity, group))
          } yield saved
        }
        }.recoverWith { conclusion =>
          store.saveC(_key_("selection-refused"), "native-check-recorded", built.record,
            _group_payload(built, check.identity, _group_(built).copy(refusal = Some(conclusion.show))))
        }
      checked <- _group_(selected).planned.zipWithIndex.foldLeft(Consequence.success(selected)) { case (prior, (cases, ordinal)) => prior.flatMap { current =>
        val group = _group_(current)
        if (group.refusal.nonEmpty || group.knownFailure || group.batches.exists(_.facts.outcome != WorkflowSbtValidation.Outcome.Succeeded)) Consequence.success(current)
        else _load_result_("batch-" + ordinal, current).flatMap {
          case Some(value) => Consequence.success(value)
          case None => for {
            _ <- V.preconditionsC(store, candidate)
            intent <- store.saveC(_key_("batch-" + ordinal + "-intent"), "native-check-prepared", current.record,
              _group_payload(current, check.identity, group.copy(pending = Some("batch-" + ordinal))))
            actual <- WorkflowManagedProcessExecution.validationBatchAttemptC(store.core, check.workspaceIdentity, cases)
            updated <- actual match {
              case SbtExecutionAttempt.Observed(result) => WorkflowSbtValidation.factsC(WorkflowSbtValidation.Kind.TestOnly, result)
                .map(facts => _group_(intent).copy(batches = group.batches :+ BatchRecord(cases, facts, result.processResult), pending = None))
              case SbtExecutionAttempt.AdmissionRejected(_, _, conclusion) =>
                Consequence.success(_group_(intent).copy(pending = None, refusal = Some(conclusion.show)))
              case SbtExecutionAttempt.Uncertain(_, _, conclusion) => N.uncertain("Ordinary validation batch remains UNKNOWN: " + conclusion.show)
            }
            saved <- store.saveC(_key_("batch-" + ordinal + "-result"), "native-check-recorded", intent.record,
              _group_payload(intent, check.identity, updated))
            _ <- store.acknowledgeC("ordinary-batch-recorded:" + check.identity + ":" + ordinal)
            _ <- V.preconditionsC(store, candidate)
          } yield saved
        }
      }}
      group = _group_(checked)
      _ <- if (group.valid) Consequence.unit else N.invalid("Ordinary group lost its exact build/selection/batch coverage")
      state = checked.payload.validation.get
      completed <- if (group.refusal.nonEmpty) Consequence.success(checked) else for {
        saved <- store.saveC(N.candidateKey(namespace, "validation-result-" + index), "native-check-recorded", checked.record,
          checked.payload.copy(validation = Some(state.copy(receipts = state.receipts :+ groupReceipt(check.identity, group),
            groups = state.groups - check.identity))))
        _ <- store.acknowledgeC("merge-check-recorded:" + check.identity)
      } yield saved
    } yield completed
  }

  final case class Coverage(check: V.Check, affectedPaths: Vector[String], inputPaths: Vector[String], reason: String)
  final case class Prior(session: String, validation: String, closure: String)
  final case class Policy(coverage: Vector[Coverage], fullChecks: Vector[V.Check], fullPaths: Vector[String],
    reason: String, priorEvidence: Vector[Prior]) {
    def checks: Vector[V.Check] = (coverage.map(_.check) ++ fullChecks).distinct
  }
  final case class Reuse(check: String, validation: String, closure: String, review: Option[String])
  final case class Selection(depth: String, reason: String, checks: Vector[V.Check], reused: Vector[Reuse])
  final case class Evidence(tree: String, validation: String, closure: String,
    checks: Vector[V.Check], receipts: Vector[V.Receipt], review: Option[String])
  trait EvidencePort { def loadC(source: Prior, store: N.Store): Consequence[Evidence] }
  given Encoder[Coverage] = deriveEncoder
  given Decoder[Coverage] = deriveDecoder
  given Encoder[Prior] = deriveEncoder
  given Decoder[Prior] = deriveDecoder
  given Encoder[Policy] = deriveEncoder
  given Decoder[Policy] = deriveDecoder
  given Encoder[Reuse] = deriveEncoder
  given Decoder[Reuse] = deriveDecoder
  given Encoder[Selection] = deriveEncoder
  given Decoder[Selection] = deriveDecoder

  val declaration: ComponentParameterKey[Policy] = ComponentParameterKey.optional(
    "sm-workflow.repository-sync.program-inputs", ComponentParameterDecoder {
      case ConfigurationValue.StringValue(raw) => io.circe.parser.parse(raw).fold(
        _ => N.invalid("program impact configuration is not JSON"), decodeC)
      case value: ConfigurationValue.ObjectValue => decodeC(json(value))
      case _ => N.invalid("program impact configuration requires an object")
    })
  def resolveC(parameters: ComponentInitializationParameters): Consequence[Option[Policy]] = parameters.resolve(declaration).map(_.value)
  /** Select only already declared typed programs on this repository's exact
    * ordinary workspace. Resolving data installs neither programs nor grants.
    * A Test program alone never establishes FULL coverage; the supplied policy
    * must already declare its coverage and FULL selection semantics.
    */
  def bindOrdinaryC(p: Policy, plan: Json, root: String): Consequence[Policy] = for {
    _ <- validateC(p)
    workspaces <- plan.hcursor.get[Vector[Json]]("workspaces").fold(e => N.invalid(e.message), Consequence.success)
    programs <- plan.hcursor.get[Vector[Json]]("sbtPrograms").fold(e => N.invalid(e.message), Consequence.success)
    roots = workspaces.filter(_.hcursor.get[String]("root").contains(root))
      .flatMap(_.hcursor.get[String]("identity").toOption).toSet
    bindings <- p.checks.foldLeft(Consequence.success(Map.empty[String, V.Check])) { (prior, check) => for {
      values <- prior
      candidates = programs.filter(program => program.hcursor.get[String]("operation").contains(check.kind.wire) &&
        program.hcursor.get[String]("target").toOption.exists(roots.contains))
        .flatMap(_.hcursor.get[String]("target").toOption).distinct
      // Preserve an existing exact legacy alias when it is genuinely bound to
      // this root. Otherwise require a unique ordinary destination, never pick
      // another repository's same ordinal or silently resolve ambiguity.
      exact = candidates.filter(_ == check.workspaceIdentity)
      selected = if (exact.nonEmpty) exact else if (check.workspace.nonEmpty) Vector.empty else candidates
      resolved = if (selected.size != 1) check.copy(setupRequired = Some(
        "ValidationSetupRequired: check " + check.identity + " (" + check.kind.wire +
          ") requires one declared ordinary SBT workspace for " + root + "; candidates=" + selected.mkString(",")))
        else if (selected.head == "sm-workflow-repository-validation-" + check.ordinal)
          check.copy(workspace = None, setupRequired = None)
        else check.copy(workspace = Some(selected.head), setupRequired = None)
      grouped = if (resolved.metadata.exists(_.query.standardFramework)) resolved.copy(ordinaryDiscovery = true) else resolved
    } yield values.updated(check.identity, grouped) }
    result = p.copy(coverage = p.coverage.map(c => c.copy(check = bindings(c.check.identity))),
      fullChecks = p.fullChecks.map(c => bindings(c.identity)))
    _ <- validateC(result)
  } yield result

  /** Reuse the existing ordinary runtime declaration, not another sync-only test catalogue. */
  def ordinaryPolicyC(parameters: ComponentInitializationParameters, root: String, plan: Json = Json.Null): Consequence[Option[Policy]] = {
    val workspaces = plan.hcursor.get[Vector[Json]]("workspaces").toOption.getOrElse(Vector.empty)
      .filter(_.hcursor.get[String]("root").contains(root)).flatMap(_.hcursor.get[String]("identity").toOption).toSet
    val programs = plan.hcursor.get[Vector[Json]]("sbtPrograms").toOption.getOrElse(Vector.empty)
    val ordinary = programs.filter(program => program.hcursor.get[String]("operation").contains("test-only") &&
      program.hcursor.get[Boolean]("discoveredTests").contains(true) &&
      program.hcursor.get[String]("target").toOption.exists(workspaces))
    if (ordinary.nonEmpty) {
      if (ordinary.size != 1) N.invalid("ValidationSetupRequired: ordinary discovered TestOnly destination is ambiguous")
      else {
        val workspace = ordinary.head.hcursor.get[String]("target").toOption.get
        val full = V.Check("ordinary-project-full", 1, WorkflowSbtValidation.Kind.TestOnly,
          workspace = Some(workspace), ordinaryDiscovery = true)
        val lint = programs.filter(program => program.hcursor.get[String]("target").contains(workspace) &&
          program.hcursor.get[String]("operation").contains("fix-check"))
        val checks = Vector(full) ++ Option.when(lint.size == 1)(V.Check("scala-lint", 2, WorkflowSbtValidation.Kind.FixCheck,
          workspace = Some(workspace))).toVector
        val policy = Policy(Vector.empty, checks, Vector.empty,
          "FULL: ordinary managed TestCompile products/classpath select all applicable non-Slow discovered tests and required modes", Vector.empty)
        validateC(policy).map(_ => Some(policy))
      }
    } else parameters.resolve(ThreeWorkflowRuntimeParameters.definition).flatMap { selected =>
      selected.value match {
        case Some(definition) if definition.target.root == root =>
          val checks = definition.repository.validation.checks
          if (!checks.exists(_.metadata.exists(_.query.standardFramework))) Consequence.success(None)
          else if (checks.exists(_.kind == WorkflowSbtValidation.Kind.Test))
            N.invalid("ValidationSetupRequired: ordinary FULL requires exact standard framework selection; raw Test cannot establish that coverage")
          else {
            val policy = Policy(Vector.empty, checks, Vector.empty,
              "Ordinary project FULL uses its declared complete framework discovery with Slow excluded", Vector.empty)
            validateC(policy).map(_ => Some(policy))
          }
        case _ => Consequence.success(None)
      }
    }
  }

  def operationC(check: V.Check): Consequence[B.BoundOperation] = for {
    invocation <- check.invocation.map(Consequence.success).getOrElse(N.invalid("Exact ordinary metadata invocation is absent"))
    profile <- check.metadata.map(Consequence.success).getOrElse(N.invalid("Ordinary metadata source profile is absent"))
    _ <- if (invocation.inventory.scope == profile.scope.copy(artifacts = profile.scope.artifacts.sortBy(a =>
        (a.componentIdentity, a.artifactIdentity))) && invocation.inventory.completeness == D.Completeness.COMPLETE)
      Consequence.unit else N.invalid("Ordinary invocation crosses its exact complete source/artifact scope")
    selected <- D.selectC(invocation.inventory, profile.query)
    _ <- if ((selected.map(_.reference) ++ profile.supplemental).contains(invocation.reference)) Consequence.unit
      else N.invalid("Ordinary invocation is outside its exact standard/focused selection")
    workspace <- WorkspaceId.parseC(check.workspaceIdentity)
    variants <- B.bindVariantsC(invocation.inventory, invocation.reference, workspace)
    bound <- variants.find(_.intent.target.validationMode.contains(invocation.mode)).map(Consequence.success)
      .getOrElse(N.invalid("Ordinary invocation changes a required mode variant"))
  } yield bound

  def belongs(check: V.Check, requested: Vector[V.Check]): Boolean = check.invocation match {
    case None => requested.contains(check)
    case Some(invocation) => requested.exists(original => original.identity == invocation.hostIdentity &&
      original == check.copy(identity = original.identity, invocation = None)) && operationC(check).isSuccess
  }

  /** A retained selection is re-bound purely; only a new candidate opens its own artifact loader. */
  def prepareOrdinaryC(requested: Vector[V.Check], pending: N.Stage, store: N.Store,
    retained: Option[N.Stage]): Consequence[Vector[V.Check]] = retained.flatMap(_.payload.validation) match {
    case Some(state) if state.sourceRevision == pending.record.revision.value.toString &&
        state.selected.forall(belongs(_, requested)) => Consequence.success(state.selected)
    case Some(_) => N.invalid("Retained ordinary selection crosses its candidate or registered project checks")
    case None => requested.foldLeft(Consequence.success(Vector.empty[V.Check])) { (prior, check) => for {
      values <- prior
      expanded <- check.metadata match {
        case None => Consequence.success(Vector(check))
        case Some(profile) => for {
          _ <- if (profile.source.isEmpty && profile.scope.artifacts.forall(artifact =>
              pending.payload.indexTree.contains(artifact.sourceRevision) &&
                artifact.root.startsWith(Path.of(store.original.repositoryRoot)))) Consequence.unit
            else N.invalid("ValidationSetupRequired: ordinary discovery needs this candidate's exact compiled source/artifact identity")
          workspace <- WorkspaceId.parseC(check.workspaceIdentity)
          selection <- try {
            val loader = new URLClassLoader(profile.scope.artifacts.map(_.root.toUri.toURL).toArray, classOf[D.Inventory].getClassLoader)
            try GoalPhaseValidationMetadata.discoverC(GoalPhaseValidationMetadata.Request(profile.scope, profile.query,
              profile.supplemental, workspace), loader).flatMap(_.requireRunnableC)
            finally loader.close()
          } catch { case NonFatal(error) => N.invalid("Ordinary project discovery is unavailable: " + error.getClass.getSimpleName) }
          expanded = selection.operations.zipWithIndex.map { (bound, index) =>
            check.copy(identity = check.identity + "-operation-" + (index + 1),
              invocation = Some(V.Invocation(check.identity, selection.inventory, bound.operation.reference,
                bound.intent.target.validationMode.get)))
          }
        } yield expanded
      }
      result = values ++ expanded
      _ <- if (result.size <= 16) Consequence.unit else N.invalid("Ordinary selected variants exceed the existing 16-check bound")
    } yield result }
  }

  private def paths(value: Vector[String]): Boolean = value != null && value.nonEmpty && value.size <= 128 &&
    value.distinct == value && value.forall(RepositorySyncNativeConflictInput.relative)
  private def text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.exists(_.isControl)
  def covers(paths: Vector[String], path: String): Boolean = paths.exists(p => path == p || path.startsWith(p + "/"))
  def validateC(p: Policy): Consequence[Unit] = if (p != null && p.coverage != null && p.fullChecks != null && (p.coverage.nonEmpty || p.fullChecks.exists(check => check.ordinaryDiscovery || check.metadata.exists(_.query.standardFramework))) &&
      p.coverage.size <= 16 && p.coverage.forall(c => c != null && paths(c.affectedPaths) && paths(c.inputPaths) &&
        text(c.reason) && c.affectedPaths.forall(covers(c.inputPaths, _)) && p.fullPaths != null && p.fullPaths.forall(covers(c.inputPaths, _))) && p.fullChecks != null && p.fullChecks.nonEmpty &&
      p.fullPaths != null && (p.fullPaths.isEmpty || paths(p.fullPaths)) && text(p.reason) && p.priorEvidence != null && p.priorEvidence.size <= 16 &&
      p.priorEvidence.distinct == p.priorEvidence && p.priorEvidence.forall(s => s != null && text(s.session) &&
        text(s.validation) && text(s.closure) && s.validation.startsWith(s.session + ":ordered-validation:") && s.validation.endsWith(":validation") &&
        s.closure.startsWith(s.session + ":ordered-step-closure:")))
    V.Policy("program-inputs", 1, "required", p.reason, p.checks).validateC
    else N.invalid("program impact requires bounded meaningful consumption/coverage, FULL rationale and native evidence locators")
  def decodeC(value: Json): Consequence[Policy] = value.as[Policy].fold(e => N.invalid(e.message), p => validateC(p).map(_ => p))

  /** FULL is selected for uncovered consumed/unknown behavior or explicit broad
    * input scope, never merely because a merge or commit exists. HEAVY has no
    * selection here. Legacy Test policies retain their original scope; new
    * ordinary FULL groups use actual build discovery and standard framework facts.
    */
  def select(p: Policy, inputs: RepositorySyncDocumentInputs.Assessment): Selection = {
    val affected = (inputs.consumedPaths ++ inputs.unknownPaths).distinct.sorted
    val broad = affected.filter(covers(p.fullPaths, _))
    val uncovered = affected.filterNot(path => p.coverage.exists(c => covers(c.affectedPaths, path)))
    val full = broad.nonEmpty || uncovered.nonEmpty
    val selected = if (full) p.fullChecks else p.coverage.filter(c => affected.exists(covers(c.affectedPaths, _))).map(_.check).distinct
    val reason = if (full) "FULL: changed broad inputs=" + broad.mkString(",") + "; uncovered inputs=" +
      uncovered.mkString(",") + "; " + p.reason
      else "FOCUSED: changed bounded inputs=" + affected.mkString(",") + "; " + p.coverage
        .filter(c => selected.contains(c.check)).map(_.reason).mkString("; ")
    Selection(if (full) "FULL" else "FOCUSED", reason, selected, Vector.empty)
  }

  def prepareC(store: N.Store, pending: N.Stage, inputs: RepositorySyncDocumentInputs.Assessment,
    p: Policy): Consequence[(Selection, Map[String, V.Receipt])] = for {
    _ <- validateC(p)
    selection = select(p, inputs)
    _ <- if (selection.checks.nonEmpty) Consequence.unit else N.invalid("changed program inputs have no selected coverage")
    result <- reuseForTreeC(store, pending.payload.indexTree.get, p, selection)
    _ <- V.preconditionsC(store, pending)
  } yield result

  /** Reuse against the actual prepared purpose tree, not the excluded source worktree.
    * The owning native purpose route separately binds its candidate/source facts.
    */
  def reuseForTreeC(store: N.Store, candidateTree: String, p: Policy,
    selection: Selection): Consequence[(Selection, Map[String, V.Receipt])] = for {
    _ <- validateC(p)
    prior <- p.priorEvidence.foldLeft(Consequence.success(Vector.empty[Evidence])) { (before, source) => for {
      values <- before
      port <- store.declaration.programEvidence.map(Consequence.success).getOrElse(N.invalid("native prior evidence reader is absent"))
      observed <- port.loadC(source, store)
    } yield values :+ observed }
    result <- prior.foldLeft(Consequence.success((selection, Map.empty[String, V.Receipt]))) { (before, evidence) => for {
      current <- before
      oldTree <- GitObjectId.parseC(evidence.tree)
      newTree <- GitObjectId.parseC(candidateTree)
      delta <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.TreeChanges(oldTree, newTree)).flatMap(_.valueC)
      reusable = selection.checks.filterNot(c => current._2.contains(c.identity)).filter { check =>
        p.coverage.find(_.check == check).exists(coverage => !delta.exists(c => covers(coverage.inputPaths, c.path.value))) &&
          evidence.checks.contains(check) && evidence.receipts.exists(r => r.identity == check.identity && V.productSuccessful(r, check.kind))
      }
      receipts = reusable.map(c => c.identity -> evidence.receipts.find(_.identity == c.identity).get).toMap
      refs = reusable.map(c => Reuse(c.identity, evidence.validation, evidence.closure, evidence.review))
    } yield (current._1.copy(reused = current._1.reused ++ refs), current._2 ++ receipts) }
  } yield result

  /** Composition receives the existing managed native storage. These are reads
    * of actual Start, validation and committed Step closure, never caller claims.
    */
  def evidencePort(storage: WorkflowComponentStorage): EvidencePort = new EvidencePort {
    def loadC(source: Prior, store: N.Store): Consequence[Evidence] = for {
      starts <- storage.bindMechanicalC(GoalPhaseNativeRun.startOperation, GoalPhaseNativeRun.codec)
      start <- starts.loadRetainedC(GoalPhaseNativeRun.reference(source.session))(using store.core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(N.invalid("prior native GoalPhase Start is absent")))
      _ <- if (start.receipt.payload.value.root == store.original.repositoryRoot &&
          start.receipt.payload.value.branch == store.context.localBranch &&
          start.receipt.payload.value.participantIdentity == store.context.participantIdentity)
        Consequence.unit else N.invalid("prior native evidence crosses repository/branch/principal")
      validations <- storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, GoalPhaseNativeValidation.codec)
      validation <- validations.loadRetainedC(WorkflowMechanicalTransition.Reference(source.validation, "1"))(using store.core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(N.invalid("prior native validation is absent")))
      closures <- storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, GoalPhaseNativeClosure.codec)
      closure <- closures.loadRetainedC(WorkflowMechanicalTransition.Reference(source.closure, "1"))(using store.core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(N.invalid("prior committed native Step closure is absent")))
      instances <- storage.bindInstancesC()(using store.core.executionContext)
      identity = org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity(source.session)
      current <- instances.loadC(identity)(using store.core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(N.invalid("prior native instance is absent")))
      validationPrefix <- GoalPhaseNativeHistory.prefixC(current, validation.receipt.next.revision)
      closurePrefix <- GoalPhaseNativeHistory.prefixC(current, closure.receipt.next.revision)
      _ <- if (validationPrefix == validation.receipt.next && closurePrefix == closure.receipt.next) Consequence.unit
        else N.invalid("prior evidence crosses its actual native instance history")
      v = validation.receipt.payload.value
      c = closure.receipt.payload.value
      _ <- if (validation.receipt.next.identity.value == source.session && closure.receipt.next.identity.value == source.session &&
          validation.receipt.next.revision.value < closure.receipt.next.revision.value &&
          c.input.instance == source.session && c.input.step == v.step && c.input.candidate == v.candidate &&
          c.commit.exists(_.tree == v.candidate.tree) && v.outcome.contains("passed") &&
          v.receipts.size == v.selected.size && GoalPhaseNativeValidation.successful(v.receipts, v.selected) &&
          (c.input.reviewEvidence.nonEmpty || c.input.admission.exists(_.assessment.branch == "admit")))
        Consequence.unit else N.invalid("prior evidence is not applicable successful validation with actual accepted Step closure")
    } yield Evidence(v.candidate.tree, source.validation, source.closure,
      v.selected.filter(c => c.metadata.isEmpty && c.hostIdentity.isEmpty && c.operation.isEmpty && c.dependency.isEmpty)
        .map(c => V.Check(c.identity, c.ordinal, c.kind)), v.receipts, c.input.reviewEvidence)
  }

  /** Validation reuse alone never establishes review. The exact complete tree
    * and the actual accepted native Step closure must retain reviewer evidence.
    */
  def reusableReviewC(store: N.Store, tree: String, policy: Policy): Consequence[Option[org.goldenport.cncf.workflow.ContextReference]] =
    policy.priorEvidence.foldLeft(Consequence.success(Option.empty[org.goldenport.cncf.workflow.ContextReference])) { (prior, source) =>
      prior.flatMap {
        case value @ Some(_) => Consequence.success(value)
        case None => store.declaration.programEvidence.map(_.loadC(source, store)).getOrElse(N.invalid("prior purpose review has no native evidence reader"))
          .map(value => if (value.tree == tree && value.review.nonEmpty)
            Some(org.goldenport.cncf.workflow.ContextReference(value.closure, "1")) else None)
      }
    }

  private def json(value: ConfigurationValue): Json = value match {
    case ConfigurationValue.StringValue(v) => Json.fromString(v)
    case ConfigurationValue.NumberValue(v) => Json.fromBigDecimal(v)
    case ConfigurationValue.BooleanValue(v) => Json.fromBoolean(v)
    case ConfigurationValue.ListValue(v) => Json.fromValues(v.map(json))
    case ConfigurationValue.ObjectValue(v) => Json.obj(v.toVector.map((k, v) => k -> json(v))*)
    case ConfigurationValue.NullValue => Json.Null
  }
}
