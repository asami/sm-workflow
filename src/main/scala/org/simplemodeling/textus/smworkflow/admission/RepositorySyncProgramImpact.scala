package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation}
import RepositorySyncNativeProgressStore as N
import RepositorySyncNativeMergeValidation as V

/** Declared impact and consumption scopes select existing managed checks.
  * References locate native records; they never supply successful receipts or
  * review acceptance. Exact consumed Git blobs, modes and paths decide reuse.
  * @since   Oct. 28, 2026
  * @version Oct. 28, 2026
  */
private[smworkflow] object RepositorySyncProgramImpact {
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
  private def paths(value: Vector[String]): Boolean = value != null && value.nonEmpty && value.size <= 128 &&
    value.distinct == value && value.forall(RepositorySyncNativeConflictInput.relative)
  private def text(value: String): Boolean = value != null && value.trim.nonEmpty && value.length <= 4096 && !value.exists(_.isControl)
  def covers(paths: Vector[String], path: String): Boolean = paths.exists(p => path == p || path.startsWith(p + "/"))
  def validateC(p: Policy): Consequence[Unit] = if (p != null && p.coverage != null && p.coverage.nonEmpty &&
      p.coverage.size <= 16 && p.coverage.forall(c => c != null && paths(c.affectedPaths) && paths(c.inputPaths) &&
        text(c.reason) && c.affectedPaths.forall(covers(c.inputPaths, _)) && p.fullPaths != null && p.fullPaths.forall(covers(c.inputPaths, _))) && p.fullChecks != null && p.fullChecks.nonEmpty &&
      paths(p.fullPaths) && text(p.reason) && p.priorEvidence != null && p.priorEvidence.size <= 16 &&
      p.priorEvidence.distinct == p.priorEvidence && p.priorEvidence.forall(s => s != null && text(s.session) &&
        text(s.validation) && text(s.closure) && s.validation.startsWith(s.session + ":ordered-validation:") && s.validation.endsWith(":validation") &&
        s.closure.startsWith(s.session + ":ordered-step-closure:")))
    V.Policy("program-inputs", 1, "required", p.reason, p.checks).validateC
    else N.invalid("program impact requires bounded meaningful consumption/coverage, FULL rationale and native evidence locators")
  def decodeC(value: Json): Consequence[Policy] = value.as[Policy].fold(e => N.invalid(e.message), p => validateC(p).map(_ => p))

  /** FULL is selected for uncovered consumed/unknown behavior or explicit broad
    * input scope, never merely because a merge or commit exists. HEAVY has no
    * selection here: ordinary Test remains the host's existing FULL operation.
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
