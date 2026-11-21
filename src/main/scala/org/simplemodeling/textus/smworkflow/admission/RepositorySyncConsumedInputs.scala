package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.{SbtScopedInput, SbtTarget}
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceFilePath, WorkspaceId, WorkspaceRelativePath}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.protocol.Property
import org.goldenport.protocol.Request
import org.goldenport.record.{Record, RecordPresentable}
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Existing immutable ManagedDevelopment consumer inputs, observed by the owning
  * batch. No JSON/string property can install the invocation-local binding.
  * @since   Nov. 21, 2026
  * @version Nov. 21, 2026
  */
private[smworkflow] object RepositorySyncConsumedInputs {
  final case class Subject(definition: ManagedDevelopment.Definition, input: ManagedDevelopment.Input,
    publication: ManagedDevelopment.Publication, path: String, envelope: Json)
  object Subject {
    import io.circe.generic.auto.*
    given Encoder[Subject] = io.circe.generic.semiauto.deriveEncoder
    given Decoder[Subject] = io.circe.generic.semiauto.deriveDecoder
  }
  final case class Observation(subjects: Vector[Subject], diagnostic: Option[String])
  object Observation {
    given Encoder[Observation] = deriveEncoder
    given Decoder[Observation] = deriveDecoder
  }
  final case class Change(before: Observation, after: Option[Observation]) {
    def known: Boolean = before.diagnostic.isEmpty && before.subjects.nonEmpty && after.exists(a =>
      a.diagnostic.isEmpty && a.subjects.nonEmpty && a.subjects.size == before.subjects.size &&
        a.subjects.zip(before.subjects).forall((next, prior) => next.definition == prior.definition &&
          next.input.edgeIdentity == prior.input.edgeIdentity))
    def changed: Boolean = known && !after.exists(_.subjects == before.subjects)
  }
  object Change {
    given Encoder[Change] = deriveEncoder
    given Decoder[Change] = deriveDecoder
  }
  private final case class Binding(session: String, workspace: String, change: Option[Change]) extends RecordPresentable {
    def toRecord(): Record = Record.data("session" -> session, "workspace" -> workspace)
  }
  private val key = "sm-workflow.repository-sync.consumed-inputs"
  def bind(core: ActionCall.Core, member: RepositorySyncDirectoryList.Member, change: Option[Change]): ActionCall.Core = {
    val request = core.action.request.copy(properties = core.action.request.properties.filterNot(_.name == key) :+
      Property(key, Binding(member.sessionId, member.workspace, change), None))
    RepositorySyncDirectoryList.replaceRequest(core, request)
  }
  private def binding(core: ActionCall.Core): Option[Binding] = Option(core).flatMap(c => Option(c.action))
    .flatMap(_.request.properties.find(_.name == key)).map(_.value).collect { case b: Binding => b }
  def inherit(core: ActionCall.Core, request: Request): Request = binding(core).fold(request)(b =>
    request.copy(properties = request.properties.filterNot(_.name == key) :+ Property(key, b, None)))
  def related(core: ActionCall.Core): Boolean = binding(core).nonEmpty
  def subject(core: ActionCall.Core): Option[Json] = binding(core).map(b => Json.obj(
    "session" -> b.session.asJson, "workspace" -> b.workspace.asJson, "change" -> b.change.asJson))
  def changed(core: ActionCall.Core): Boolean = binding(core).flatMap(_.change).exists(_.changed)
  def changes(core: ActionCall.Core): Vector[String] = binding(core).toVector.flatMap(_.change)
    .filter(_.changed).flatMap(_.after.toVector.flatMap(_.subjects.map(_.path))).distinct.sorted

  def observeC(core: ActionCall.Core, member: RepositorySyncDirectoryList.Member): Consequence[Observation] = {
    val observed = for {
      raw <- core.executionContext.resolvedParameters.get(ManagedDevelopmentRuntime.declaration.name).flatMap(_.value match {
        case ConfigurationValue.StringValue(text) => Some(text)
        case _ => None
      }).map(Consequence.success).getOrElse(invalid("managed-development policy is unavailable"))
      policy <- {
        import io.circe.generic.auto.*
        io.circe.parser.decode[ManagedDevelopmentRuntime.Policy](raw).fold(e => invalid(e.getMessage), Consequence.success)
      }
      // An explicitly Git-only edge contributes ordering/results, not a second
      // SBT override. It can accompany an actually selected scoped artifact.
      edges = policy.definition.edges.filter(e => e.consumer.workspace == member.workspace && e.consumer.repository == member.repositoryIdentity &&
        (e.localJar || e.localProject.nonEmpty))
      root <- ManagedDevelopmentRuntime.workspaceRootC(core, member.workspace)
      _ <- if (root == member.root) Consequence.unit else invalid("consumer input checkout differs from the captured native member")
      inputs = policy.repositorySyncInputs.getOrElse(Vector.empty).filter(i => edges.exists(_.identity == i.edgeIdentity))
      _ <- if (edges.nonEmpty && inputs.map(_.edgeIdentity).sorted == edges.map(_.identity).sorted && inputs.size == 1)
        Consequence.unit else invalid("exact existing consumer input is required for each selected edge; one admitted scoped envelope per SBT build is supported")
      values <- inputs.foldLeft(Consequence.success(Vector.empty[Subject])) { (prior, input) => for {
        values <- prior
        actual <- ManagedDevelopmentRuntime.repositorySyncInputC(core, policy, input)
      } yield values :+ actual }
    } yield Observation(values, None)
    observed.recoverWith(c => Consequence.success(Observation(Vector.empty, Some(c.show))))
  }

  /** The exact same scoped input is attached to discovery and every selected
    * check. A different validation alias cannot redirect its build workspace. */
  def targetC(core: ActionCall.Core, target: SbtTarget): Consequence[SbtTarget] = binding(core).flatMap(_.change) match {
    case None => Consequence.success(target)
    case Some(change) if change.known => for {
      selected <- change.after.get.subjects.headOption.map(Consequence.success).getOrElse(invalid("selected input is unavailable"))
      edge <- selected.definition.edges.find(_.identity == selected.input.edgeIdentity).map(Consequence.success).getOrElse(invalid("selected edge is unavailable"))
      _ <- if (target.workspaceId.value == edge.consumer.workspace && target.scopedInput.isEmpty) Consequence.unit
        else invalid("selected validation target differs from the immutable consumer input workspace")
      owner <- WorkspaceId.parseC(selected.definition.ownerWorkspace)
      path <- WorkspaceRelativePath.parseC(selected.path)
      scoped = SbtScopedInput(SmWorkflowComponent.componentId, target.workspaceId, owner, selected.definition.scope, selected.input.selection, path, None)
    } yield target.copy(scopedInput = Some(scoped))
    case _ => invalid("consumer input applicability is unknown")
  }

  def verifyC(core: ActionCall.Core): Consequence[Unit] = binding(core).flatMap(_.change) match {
    case None => Consequence.unit
    case Some(change) if change.known => change.after.get.subjects.foldLeft(Consequence.unit) { (prior, selected) => for {
      _ <- prior
      owner <- WorkspaceId.parseC(selected.definition.ownerWorkspace).map(FileTarget.apply)
      path <- WorkspaceFilePath.parseC(selected.path)
      actual <- WorkflowFileExecution.readTextC(core, owner, path)
      json <- actual.text.map(text => io.circe.parser.parse(text).fold(e => invalid(e.message), Consequence.success))
        .getOrElse(invalid("frozen consumer input disappeared"))
      _ <- if (json == selected.envelope) Consequence.unit else invalid("frozen consumer input changed during validation; no receipt reuse")
    } yield () }
    case _ => invalid("consumer input applicability is unknown")
  }

  private def invalid[A](message: String): Consequence[A] = Consequence.configurationInvalid("RepositorySync consumed input " + message)
}
