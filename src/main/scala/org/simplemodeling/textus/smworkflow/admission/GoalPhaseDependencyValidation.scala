package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.file.Path
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{GitBranchName, GitObjectId}
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import GoalPhaseOrderedProgression as O
import GoalPhaseDependencyScope as D
import GoalPhaseNativeValidation as V

/** References select business context, never execution permission or observations.
  * @since   Oct. 24, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseDependencyValidation {
  final case class ApprovalReference(proposal: ContextReference, decision: ContextReference)
  final case class Selection(binding: ContextReference, approvals: Vector[ApprovalReference])
  final case class Approval(step: String, proposal: D.Proposal, decision: ContextReference)
  final case class Binding(identity: String, revision: Int, step: String,
    requester: D.Component, dependency: D.Component, root: String, branch: String,
    paths: Vector[String], base: O.Snapshot, baseAdoption: String, checks: Vector[V.Check])
  final case class AcceptedPrefix(schemaVersion: String, sessionId: String, plan: ContextReference,
    step: String, closureRevision: String, candidate: O.Snapshot, paths: Vector[String])
  val acceptedPrefixSchema = "sm-workflow.dependency-accepted-prefix.v1"
  final case class Admission(binding: Binding, approvals: Vector[Approval], acceptedPrefix: Option[AcceptedPrefix] = None)
  final case class Capture(session: String, plan: ContextReference, step: String, generation: Long,
    sourceRevision: String, primaryBase: O.Snapshot, admission: Admission,
    candidate: O.Snapshot, changedPaths: Vector[String], diff: String)
  final case class CandidateOrigin(stageSourceRevision: String, observationSourceRevision: String, resultRecordRevision: String)
  final case class Execution(check: String, attempt: Int, workspace: String, operation: String)
  final case class RecoverySubject(binding: ContextReference, repository: String, workspace: String,
    base: O.Snapshot, candidate: O.Snapshot)

  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = _closed(Set("identity", "revision"), deriveDecoder)
  private given Encoder[O.Snapshot] = deriveEncoder
  private given Decoder[O.Snapshot] = _closed(Set("head", "tree"), deriveDecoder)
  given Encoder[ApprovalReference] = deriveEncoder
  given Decoder[ApprovalReference] = _closed(Set("proposal", "decision"), deriveDecoder)
  given Encoder[Selection] = deriveEncoder
  given Decoder[Selection] = _closed(Set("binding", "approvals"), deriveDecoder)
  given Encoder[Approval] = deriveEncoder
  given Decoder[Approval] = _closed(Set("step", "proposal", "decision"), deriveDecoder)
  given Encoder[Binding] = deriveEncoder
  given Decoder[Binding] = _closed(Set("identity", "revision", "step", "requester", "dependency",
    "root", "branch", "paths", "base", "baseAdoption", "checks"), deriveDecoder)
  given Encoder[AcceptedPrefix] = deriveEncoder
  given Decoder[AcceptedPrefix] = _closed(Set("schemaVersion", "sessionId", "plan", "step", "closureRevision",
    "candidate", "paths"), deriveDecoder[AcceptedPrefix]).emap(v =>
      Either.cond(prefixValid(v), v, "Invalid dependency accepted prefix v1"))
  given Encoder[Admission] = deriveEncoder[Admission].mapJson(_.mapObject(_.filter {
    case ("acceptedPrefix", value) => !value.isNull
    case _ => true
  }))
  private val _admission_decoder: Decoder[Admission] = deriveDecoder
  given Decoder[Admission] = Decoder.instance { cursor =>
    val keys = Set("binding", "approvals") ++ Option.when(cursor.downField("acceptedPrefix").focus.nonEmpty)("acceptedPrefix")
    _closed(keys, _admission_decoder)(cursor)
  }
  given Encoder[Capture] = deriveEncoder
  given Decoder[Capture] = _closed(Set("session", "plan", "step", "generation", "sourceRevision",
    "primaryBase", "admission", "candidate", "changedPaths", "diff"), deriveDecoder)
  given Encoder[CandidateOrigin] = deriveEncoder
  given Decoder[CandidateOrigin] = _closed(Set("stageSourceRevision", "observationSourceRevision", "resultRecordRevision"), deriveDecoder)
  given Encoder[Execution] = deriveEncoder
  given Decoder[Execution] = _closed(Set("check", "attempt", "workspace", "operation"), deriveDecoder)
  given Encoder[RecoverySubject] = deriveEncoder
  given Decoder[RecoverySubject] = _closed(Set("binding", "repository", "workspace", "base", "candidate"), deriveDecoder)

  private def _closed[A](keys: Set[String], decoder: Decoder[A]): Decoder[A] = Decoder.instance { cursor =>
    if (!cursor.value.asObject.exists(fields => fields.keys.toSet == keys && fields.values.forall(!_.isNull)))
      Left(DecodingFailure("Dependency validation requires exact non-null business fields", cursor.history))
    else decoder(cursor)
  }
  private def _text(value: String): Boolean = value != null && value.nonEmpty && value.length <= 4096 && !value.exists(_.isControl)
  private def _id(value: String): Boolean = value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
  private def _reference(value: ContextReference): Boolean = value != null && _text(value.identity) && _text(value.revision)
  private def _snapshot(value: O.Snapshot): Boolean = value != null &&
    GitObjectId.parseC(value.head).isSuccess && GitObjectId.parseC(value.tree).isSuccess
  private def _paths(values: Vector[String]): Boolean = values != null && values.nonEmpty && values.size <= 128 &&
    values.distinct == values && values.forall(RepositorySyncNativeConflictInput.relative)

  def selectionValid(value: Selection): Boolean = value != null && _reference(value.binding) &&
    value.approvals != null && value.approvals.nonEmpty && value.approvals.size <= 16 &&
    value.approvals.forall(a => a != null && _reference(a.proposal) && _reference(a.decision)) &&
    value.approvals.map(_.proposal).distinct.size == value.approvals.size &&
    value.approvals.map(_.decision).distinct.size == value.approvals.size
  def approvalValid(value: Approval): Boolean = value != null && _text(value.step) &&
    D.validateOptionC(Some(value.proposal)).isSuccess && _reference(value.decision)
  def bindingValid(value: Binding): Boolean = value != null && _id(value.identity) && value.revision > 0 &&
    _text(value.step) && value.requester != null && value.dependency != null && value.requester.identity != value.dependency.identity &&
    Vector(value.requester, value.dependency).forall(c => _text(c.identity) && _text(c.repository) &&
      WorkspaceId.parseC(c.workspace).isSuccess) && value.requester.repository != value.dependency.repository &&
    value.requester.workspace != value.dependency.workspace && _text(value.root) &&
    scala.util.Try(Path.of(value.root)).toOption.exists(p => p.isAbsolute && p.normalize.toString == value.root) &&
    GitBranchName.parseC(value.branch).isSuccess && _paths(value.paths) && _snapshot(value.base) && _text(value.baseAdoption) &&
    value.checks != null && value.checks.nonEmpty && value.checks.size <= 8 &&
    value.checks.forall(c => c != null && c.dependency != null && c.metadata != null &&
      c.hostIdentity != null && c.operation != null && c.dependency.isEmpty && c.metadata.isEmpty &&
      c.hostIdentity.isEmpty && c.operation.isEmpty) &&
    V.Policy(value.identity, value.revision, value.checks).validateC.isSuccess
  def prefixValid(value: AcceptedPrefix): Boolean = value != null && value.schemaVersion == acceptedPrefixSchema &&
    _text(value.sessionId) && _reference(value.plan) && _text(value.step) &&
    value.closureRevision != null && value.closureRevision.toLongOption.exists(n => n > 0 && n.toString == value.closureRevision) &&
    _snapshot(value.candidate) && _paths(value.paths)
  def admissionValid(value: Admission): Boolean = value != null && bindingValid(value.binding) &&
    value.acceptedPrefix != null && value.acceptedPrefix.forall(p => prefixValid(p) &&
      p.step != value.binding.step && p.candidate.head == value.binding.base.head &&
      (p.paths ++ value.binding.paths).distinct.size <= 128) &&
    value.approvals != null && value.approvals.nonEmpty && value.approvals.size <= 16 &&
    value.approvals.forall(a => approvalValid(a) && a.step == value.binding.step &&
      a.proposal.requester == value.binding.requester && a.proposal.dependency == value.binding.dependency) &&
    value.approvals.map(_.decision).distinct.size == value.approvals.size &&
    value.approvals.map(a => ContextReference(a.proposal.identity, a.proposal.revision)).distinct.size == value.approvals.size &&
    value.approvals.map(_.proposal.usageContract).distinct.size == 1 &&
    value.approvals.flatMap(_.proposal.paths).distinct.toSet == value.binding.paths.toSet
  def captureValid(value: Capture): Boolean = value != null && _text(value.session) && _reference(value.plan) &&
    _text(value.step) && value.generation > 0 && value.sourceRevision != null &&
    value.sourceRevision.toLongOption.exists(_ > 0) && _snapshot(value.primaryBase) && admissionValid(value.admission) &&
    value.step == value.admission.binding.step && value.admission.acceptedPrefix.forall(p =>
      p.sessionId == value.session && p.plan == value.plan && BigInt(p.closureRevision) < BigInt(value.sourceRevision)) &&
    _snapshot(value.candidate) &&
    value.candidate.head == value.admission.binding.base.head && value.changedPaths != null &&
    value.changedPaths.nonEmpty && value.changedPaths.distinct == value.changedPaths &&
    value.changedPaths.forall(value.admission.binding.paths.contains) && value.diff != null &&
    value.diff.nonEmpty && value.diff.indexOf('\u0000') < 0
  def executionValid(value: Execution): Boolean = value != null && _id(value.check) && value.attempt >= 0 &&
    value.attempt < Int.MaxValue && WorkspaceId.parseC(value.workspace).isSuccess &&
    Set("compile", "test", "test-only")(value.operation)
  def recoveryValid(value: RecoverySubject): Boolean = value != null && _reference(value.binding) &&
    _text(value.repository) && WorkspaceId.parseC(value.workspace).isSuccess && _snapshot(value.base) && _snapshot(value.candidate)

  def originValid(value: CandidateOrigin): Boolean = value != null &&
    Vector(value.stageSourceRevision, value.observationSourceRevision, value.resultRecordRevision).forall(v =>
      v != null && v.toLongOption.exists(n => n > 0 && n.toString == v)) &&
    BigInt(value.stageSourceRevision) <= BigInt(value.observationSourceRevision) &&
    BigInt(value.observationSourceRevision) < BigInt(value.resultRecordRevision)

  def capturedForC(value: Capture, session: String, plan: O.Plan, step: String,
    generation: Long, source: String, base: O.Snapshot, admission: Admission): Consequence[Unit] =
    if (captureValid(value) && value.session == session && plan != null &&
        value.plan == ContextReference(plan.identity, plan.revision) && value.step == step &&
        value.generation == generation && value.sourceRevision == source && value.primaryBase == base &&
        value.admission == admission) Consequence.unit
    else invalid("retained dependency capture crosses its Session, Plan, Step, generation, source or adopted binding")

  def admitC(selection: Selection, binding: Binding, approvals: Vector[Approval], request: GoalPhaseNativeWork.Request):
    Consequence[Admission] = {
    val selected = Option(approvals).getOrElse(Vector.empty).filter(a => approvalValid(a) &&
      Option(request).flatMap(_.stepIdentity).contains(a.step) && Option(binding).exists(b =>
        a.proposal.dependency == b.dependency && a.proposal.requester == b.requester))
    val expected = selected.map(a => ApprovalReference(ContextReference(a.proposal.identity, a.proposal.revision), a.decision))
    val admission = Admission(binding, selected)
    if (selectionValid(selection) && bindingValid(binding) && request != null &&
        Set("ImplementStep", "RepairStep")(request.kind) && request.stepIdentity.contains(binding.step) &&
        request.selectedPlan.repository == binding.requester.repository &&
        selection.binding == ContextReference(binding.identity, binding.revision.toString) &&
        selection.approvals.toSet == expected.toSet && selection.approvals.size == expected.size && admissionValid(admission))
      Consequence.success(admission)
    else invalid("selection differs from the actual assignment, binding or exact saved approval union")
  }
  def validateSelectionC(value: Option[Selection]): Consequence[Unit] =
    if (value != null && value.forall(selectionValid)) Consequence.unit else invalid("invalid selection")

  /** Structural half only. The native walker supplies both actual approvals
    * and the independently reconstructed closed-Step prefix. */
  def phaseAdmissionC(binding: Binding, approvals: Vector[Approval], plan: O.Plan,
    prefix: Option[AcceptedPrefix]): Consequence[Admission] = {
    val selected = Option(approvals).getOrElse(Vector.empty).filter(a => approvalValid(a) &&
      Option(binding).exists(b => a.step == b.step && a.proposal.requester == b.requester &&
        a.proposal.dependency == b.dependency))
    val admission = Admission(binding, selected, prefix)
    if (bindingValid(binding) && plan != null && binding.step == "phase:" + plan.phase &&
        binding.requester.repository == plan.repository && admissionValid(admission) &&
        prefix.exists(p => p.plan == ContextReference(plan.identity, plan.revision) &&
          plan.steps.exists(_.identity == p.step) && !p.paths.exists(binding.paths.contains)))
      Consequence.success(admission)
    else invalid("Release dependency requires exact actual phase approval, a disjoint closed-Step prefix and unchanged purpose")
  }
  /** Inspect the actual named input before a generated reader can discard extra fields. */
  def validatePublicResultC(record: Record): Consequence[Unit] =
    if (record == null) invalid("public completion result is absent")
    else record.toJsonStringC.flatMap(text => io.circe.parser.parse(text).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).fold(invalid, value =>
        if (value.asObject.exists(fields => !fields.contains("dependencyAdmission"))) Consequence.unit
        else invalid("public completion cannot supply an admitted dependency snapshot")))
  def instructions(binding: Option[Binding], approvals: Vector[Approval], step: Option[String], kind: String): String =
    if (!Set("ImplementStep", "RepairStep")(kind)) ""
    else binding.filter(b => bindingValid(b) && step.contains(b.step)).flatMap { selected =>
      val actual = approvals.filter(a => approvalValid(a) && a.step == selected.step &&
        a.proposal.requester == selected.requester && a.proposal.dependency == selected.dependency)
      Option.when(admissionValid(Admission(selected, actual)))(Selection(
        ContextReference(selected.identity, selected.revision.toString), actual.map(a =>
          ApprovalReference(ContextReference(a.proposal.identity, a.proposal.revision), a.decision))))
    }.map(selection => " The native owner has reconstructed these exact dependency-validation references: " +
      selection.asJson.noSpaces + ". A completed linked implementation may select them as dependencyValidation; " +
      "supply references only, never snapshots, execution success, argv or permissions. " +
      "An already adopted obligation remains required when omitted. Runtime owns actual Git/SBT capture and qualification.")
      .getOrElse("")
  def fromPublicC(value: Option[Record]): Consequence[Option[Selection]] = value match {
    case None => Consequence.success(None)
    case Some(record) if record != null => record.toJsonStringC.flatMap(text =>
      io.circe.parser.parse(text).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
        .flatMap(_.as[Selection].left.map(_.message)).fold(invalid(_), s => validateSelectionC(Some(s)).map(_ => Some(s))))
    case _ => invalid("selection must be absent or a valid object")
  }
  def publicSelection(value: Option[Selection]): Option[Record] = value.map(v => GoalPhaseNativeMetadata.record(v.asJson))
  def reviewInstructions(captures: Vector[Capture]): String =
    if (!captures.exists(_.admission.acceptedPrefix.nonEmpty)) ""
    else " A dependency capture with acceptedPrefix retains the full current HEAD/index candidate but its changedPaths and diff describe only the predecessor-tree-to-current delta. Verify its explicit native predecessor together with unchanged historical capture/check/review evidence. Assess overlapping old/current paths and their integration against the current selected checks; an earlier accepted review or PASS does not qualify the new bytes."
  def subject(value: Capture): RecoverySubject = RecoverySubject(
    ContextReference(value.admission.binding.identity, value.admission.binding.revision.toString),
    value.admission.binding.dependency.repository, value.admission.binding.dependency.workspace,
    value.admission.binding.base, value.candidate)
  def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("GoalPhase dependency validation: " + reason)
}
