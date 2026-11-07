package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.parse
import io.circe.syntax.*
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceFilePath, WorkspaceId}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import StandaloneFixExecution as S
import StandaloneFixExecution.given
import StandaloneFixNativeRun as N

/** Explicit collection of retained external validation, never a native process receipt.
  * @since   Nov.  1, 2026
  * @version Nov.  1, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object StandaloneFixValidationRecovery {
  val purpose = "standalone-fix-validation-recovery"
  val schema = "sm-workflow.standalone-fix-validation-recovery.v1"
  val observationSchema = "sm-workflow.retained-external-validation.v1"
  val choices = Vector("CollectSuccessfulValidation", "Defer")
  val message = "Collect only the original retained candidate's selected validation with independent Human attribution. " +
    "This neither accepts source repairs nor replays a process; a separate ordinary Advance owns assessment."

  final case class FileReference(workspace: String, path: String)
  object FileReference {
    given Encoder[FileReference] = deriveEncoder
    given Decoder[FileReference] = deriveDecoder
  }
  final case class Observation(schemaVersion: String, source: String, pid: Long, programIdentity: String,
    check: String, workspace: String, suites: Vector[String], terminalLog: FileReference,
    sbtStream: FileReference, junitReport: FileReference)
  object Observation {
    given Encoder[Observation] = deriveEncoder
    given Decoder[Observation] = deriveDecoder
  }
  final case class ObservedEvidence(observation: Observation, terminalLog: String, sbtStream: String, junitReport: String)
  object ObservedEvidence {
    given Encoder[ObservedEvidence] = deriveEncoder
    given Decoder[ObservedEvidence] = deriveDecoder
  }
  final case class CandidateReference(head: String, tree: String, paths: Vector[String])
  object CandidateReference {
    given Encoder[CandidateReference] = deriveEncoder
    given Decoder[CandidateReference] = deriveDecoder
  }
  final case class Target(schemaVersion: String, componentInstance: String, sessionId: String, participant: String,
    frontierRevision: Long, originalInput: S.Input, candidate: CandidateReference,
    check: GoalPhaseNativeValidation.Check, pendingCheck: String, stateConditions: Json,
    root: String, branch: String, baseHead: String, evidence: ObservedEvidence)
  object Target {
    given Encoder[Target] = deriveEncoder
    given Decoder[Target] = deriveDecoder
  }
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  final case class Direction(target: Target, choice: String, rationale: String, participant: String, reference: ContextReference)
  object Direction {
    given Encoder[Direction] = deriveEncoder
    given Decoder[Direction] = deriveDecoder
  }
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }

  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _candidate(value: S.Candidate): CandidateReference = CandidateReference(value.head, value.tree, value.paths)
  private def _conditions(state: S.State): Json = state.asJson.mapObject(fields =>
    fields.filter { case (key, _) => !Set("input", "candidate", "recoveredValidation", "stage", "pendingCheck", "message")(key) })
  private def _reference_valid(value: FileReference): Boolean = value != null &&
    WorkspaceId.parseC(value.workspace).isSuccess && WorkspaceFilePath.parseC(value.path).isSuccess

  def observationValid(value: Observation): Boolean = value != null && value.schemaVersion == observationSchema &&
    value.source == "RetainedExternalValidation" && value.pid > 0 && _exact(value.programIdentity) && _exact(value.check) &&
    WorkspaceId.parseC(value.workspace).isSuccess && value.suites != null &&
    WorkflowSbtValidation.Selection(value.suites, value.suites.size, 1).valid &&
    Vector(value.terminalLog, value.sbtStream, value.junitReport).forall(_reference_valid) &&
    value.sbtStream.workspace == value.workspace && value.junitReport.workspace == value.workspace

  def observationC(raw: String): Consequence[Observation] = _parse_c(raw).flatMap(json => {
    val decoded = json.as[Observation].left.map(_.message).flatMap(value =>
      Either.cond(observationValid(value) && value.asJson == json, value, "Unsupported or open retained validation observation"))
    decoded.fold(N.invalid(_), Consequence.success)
  })

  /** Verify the actual event stream; repeated or conflicting results are not collection authority. */
  private def _terminal_valid(evidence: ObservedEvidence): Boolean = {
    val observed = evidence.observation
    val parsed = evidence.terminalLog.linesIterator.filter(_.startsWith("[cncf-sbt-event] "))
      .map(line => parse(line.stripPrefix("[cncf-sbt-event] "))).toVector
    if (parsed.exists(_.isLeft)) false
    else {
      val events = parsed.flatMap(_.toOption).filter(_.hcursor.get[String]("workspaceId").contains(observed.workspace))
      val started = events.filter(_.hcursor.get[String]("state").contains("started"))
      val terminal = events.filter(_.hcursor.get[String]("state").contains("terminal"))
      events.forall { event =>
        val cursor = event.hcursor
        cursor.get[String]("operation").contains("test-only") &&
          cursor.get[String]("programIdentity").contains(observed.programIdentity) &&
          cursor.get[String]("state").exists(Set("launching", "started", "waiting", "terminal")) &&
          cursor.get[Long]("pid").toOption.forall(_ == observed.pid)
      } && started.size == 1 && terminal.size == 1 && events.indexOf(started.head) < events.indexOf(terminal.head) &&
        started.head.hcursor.get[Long]("pid").contains(observed.pid) &&
        terminal.head.hcursor.get[Long]("pid").contains(observed.pid) &&
        terminal.head.hcursor.get[String]("termination").contains("Exited(0)") &&
        terminal.head.hcursor.get[Int]("exitCode").contains(0) &&
        terminal.head.hcursor.get[Long]("elapsedMillis").exists(_ > 0)
    }
  }

  /** JDK parsing disables DTDs and all external resolution before examining test cases. */
  private def _junit_count(evidence: ObservedEvidence): Option[Long] = scala.util.Try {
    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    factory.setXIncludeAware(false)
    factory.setExpandEntityReferences(false)
    val builder = factory.newDocumentBuilder()
    builder.setEntityResolver((_, _) => throw new org.xml.sax.SAXException("External XML resolution is forbidden"))
    builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler {
      override def error(value: org.xml.sax.SAXParseException): Unit = throw value
      override def fatalError(value: org.xml.sax.SAXParseException): Unit = throw value
    })
    val document = builder.parse(new InputSource(new StringReader(evidence.junitReport)))
    val root = document.getDocumentElement
    require(Set("testsuite", "testsuites")(root.getTagName), "JUnit requires an actual suite report")
    val nodes = document.getElementsByTagName("testsuite")
    val suites = (0 until nodes.getLength).map(i => nodes.item(i).asInstanceOf[org.w3c.dom.Element]).toVector
    require(suites.map(_.getAttribute("name")).distinct.size == suites.size &&
      suites.map(_.getAttribute("name")).toSet == evidence.observation.suites.toSet, "JUnit suites differ from the declared selection")
    val counts = suites.map { suite =>
      val cases = suite.getElementsByTagName("testcase")
      val actual = (0 until cases.getLength).map(i => cases.item(i).asInstanceOf[org.w3c.dom.Element]).toVector
      val names = actual.map(c => c.getAttribute("classname") -> c.getAttribute("name"))
      val count = suite.getAttribute("tests").toLong
      require(count > 0 && count == actual.size && names.distinct.size == names.size &&
        names.forall((classname, name) => classname == suite.getAttribute("name") && _exact(name)), "JUnit lacks exact nonempty actual test cases")
      require(Vector("failures", "errors", "skipped").forall(key => suite.getAttribute(key) == "0") &&
        Vector("failure", "error", "skipped").forall(tag => suite.getElementsByTagName(tag).getLength == 0), "JUnit includes unsuccessful tests")
      count
    }
    val total = counts.sum
    if (root.getTagName == "testsuites") {
      require(root.getAttribute("tests").toLong == total &&
        Vector("failures", "errors", "skipped").forall(key => root.getAttribute(key) == "0"), "JUnit aggregate counts disagree")
    }
    total
  }.toOption

  private def _stream_valid(evidence: ObservedEvidence, count: Long): Boolean = {
    val lines = evidence.sbtStream.replaceAll("\u001b\\[[0-9;]*m", "").linesIterator
      .map(_.stripPrefix("[info] ").trim).toVector
    evidence.observation.suites.forall(suite => lines.contains(suite.split('.').last + ":")) &&
      lines.count(_ == "Total number of tests run: " + count) == 1 &&
      lines.count(_ == "Suites: completed " + evidence.observation.suites.size + ", aborted 0") == 1 &&
      lines.count(_ == "Tests: succeeded " + count + ", failed 0, canceled 0, ignored 0, pending 0") == 1 &&
      lines.count(_ == "All tests passed.") == 1
  }

  def evidenceValid(value: ObservedEvidence): Boolean = value != null && observationValid(value.observation) &&
    value.terminalLog != null && value.sbtStream != null && value.junitReport != null &&
    _terminal_valid(value) && _junit_count(value).exists(count => _stream_valid(value, count))

  def eligible(state: S.State): Boolean = state != null && S.inputC(state.input).isSuccess &&
    state.input.kind == "code" && state.input.task.isEmpty && state.input.delegation.isEmpty &&
    state.input.reviewOrigin.isEmpty && !state.input.reviewRequired && state.semanticReference.isEmpty &&
    state.stage == "ValidationIntent" && state.receipts.isEmpty && state.recoveredValidation.isEmpty &&
    state.candidate.exists(S.validCandidate) && state.selected.size == 1 &&
    state.selected.head.kind == WorkflowSbtValidation.Kind.TestOnly && state.selected.head.dependency.isEmpty &&
    state.selected.map(_.identity) == state.input.checks && state.pendingCheck.contains(state.selected.head.identity)

  def targetC(raw: String): Consequence[Target] = _parse_c(raw).flatMap(json =>
    decodeTarget(json).fold(N.invalid(_), Consequence.success))
  def decodeTarget(json: Json): Either[String, Target] = for {
    target <- json.as[Target].left.map(_.message)
    _ <- Either.cond(target.asJson == json && targetValid(target), (), "Unsupported or crossed standalone recovery target")
  } yield target

  def targetValid(target: Target): Boolean = target != null && target.schemaVersion == schema &&
    Vector(target.componentInstance, target.sessionId, target.participant, target.root, target.branch).forall(_exact) &&
    target.frontierRevision > 0 && S.inputC(target.originalInput).isSuccess && target.originalInput.kind == "code" &&
    target.originalInput.task.isEmpty && target.originalInput.delegation.isEmpty && target.originalInput.reviewOrigin.isEmpty &&
    !target.originalInput.reviewRequired && target.stateConditions != null && target.stateConditions.isObject &&
    target.candidate != null && S.validCandidate(S.Candidate(target.candidate.head, target.candidate.tree, target.candidate.paths, "")) &&
    target.baseHead == target.candidate.head && target.candidate.paths.forall(S.owned(_, target.originalInput.ownedPaths)) &&
    target.check != null && target.check.ordinal > 0 &&
    target.check.kind == WorkflowSbtValidation.Kind.TestOnly && target.check.dependency.isEmpty &&
    target.pendingCheck == target.check.identity && target.originalInput.checks == Vector(target.check.identity) &&
    evidenceValid(target.evidence) && target.evidence.observation.check == target.check.identity &&
    target.evidence.observation.workspace == "sm-workflow-repository-validation-" + target.check.ordinal

  def targetFor(state: S.State, owner: String, session: String, revision: Long,
    root: String, branch: String, baseHead: String, evidence: ObservedEvidence): Consequence[Target] =
    if (!eligible(state)) N.invalid("Recovery requires the exact uncollected legacy standalone TestOnly intent")
    else {
      val target = Target(schema, owner, session, state.participant, revision, state.input, _candidate(state.candidate.get),
        state.selected.head, state.pendingCheck.get, _conditions(state), root, branch, baseHead, evidence)
      if (targetValid(target)) Consequence.success(target) else N.invalid("Retained validation evidence does not qualify the exact pending check")
    }

  def bindingC(owner: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (!targetValid(target) || owner != target.componentInstance || participant != target.participant ||
        !choices.contains(choice) || !_exact(rationale) || reference == null || !_exact(reference.identity) || !_exact(reference.revision))
      N.invalid("Recovery requires exact owner, participant, target and independent Human direction")
    else Consequence.success(Json.obj("componentInstance" -> owner.asJson,
      "standaloneValidationRecovery" -> Json.obj("target" -> target.asJson, "answer" -> Json.obj(
        "source" -> "Human".asJson, "choice" -> choice.asJson, "rationale" -> rationale.asJson,
        "participant" -> participant.asJson, "reference" -> reference.asJson))))

  def selectBinding(value: Json): Either[String, Json] = for {
    owner <- value.hcursor.get[String]("componentInstance").left.map(_.message)
    raw <- value.hcursor.downField("standaloneValidationRecovery").get[Json]("target").left.map(_.message)
    target <- decodeTarget(raw)
    answer = value.hcursor.downField("standaloneValidationRecovery").downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    selected <- bindingC(owner, target, choice, rationale, participant, reference).toOption.toRight("Invalid standalone recovery direction")
    _ <- Either.cond(selected == value, (), "Standalone recovery evidence is not the exact closed Human binding")
  } yield selected

  private def _matches(state: S.State, target: Target): Boolean = state.participant == target.participant &&
    state.candidate.map(_candidate).contains(target.candidate) && state.selected == Vector(target.check) &&
    _conditions(state) == target.stateConditions

  def collectedC(state: S.State, direction: Direction): Consequence[S.State] =
    if (direction == null || direction.choice != choices.head || !eligible(state) ||
        !targetValid(direction.target) || state.input != direction.target.originalInput || !_matches(state, direction.target) ||
        bindingC(direction.target.componentInstance, direction.target, direction.choice, direction.rationale,
          direction.participant, direction.reference).isSuccess == false)
      N.invalid("Collection crosses the original intent, candidate, conditions or independent direction")
    else Consequence.success(state.copy(input = state.input.copy(kind = "validation"), stage = "Assess",
      pendingCheck = None, recoveredValidation = Some(direction)))

  def currentTargetC(expected: Target, actual: Target): Consequence[Unit] =
    if (targetValid(actual) && expected == actual) Consequence.unit
    else N.invalid("Recovery frontier, owner or retained evidence changed; obtain a fresh independent description")

  def collectC(owner: String, state: S.State, direction: Direction, core: ActionCall.Core,
    evidence: EvidencePort): Consequence[S.State] = for {
    binding <- bindingC(owner, direction.target, direction.choice, direction.rationale, direction.participant, direction.reference)
    _ <- if (evidence == null) Consequence.securityPermissionDenied("Standalone recovery has no independent Human evidence provider")
      else evidence.admitC(core, direction.reference, binding)
    next <- collectedC(state, direction)
  } yield next

  def assessC(state: S.State): Consequence[Unit] = state.recoveredValidation match {
    case Some(direction) if state.input.kind == "validation" && state.stage == "Assess" &&
        direction.choice == choices.head && targetValid(direction.target) &&
        state.input == direction.target.originalInput.copy(kind = "validation") && _matches(state, direction.target) &&
        state.receipts.isEmpty && state.semanticReference.isEmpty && state.pendingCheck.isEmpty &&
        bindingC(direction.target.componentInstance, direction.target, direction.choice, direction.rationale,
          direction.participant, direction.reference).isSuccess => Consequence.unit
    case _ => N.invalid("Recovered external validation cannot replace native receipts or code-repair acceptance")
  }

  private def _parse_c(raw: String): Consequence[Json] =
    Option(raw).map(value => parse(value).fold(error => N.invalid(error.message), Consequence.success))
      .getOrElse(N.invalid("Opaque recovery JSON is absent"))

  private def _read_c(core: ActionCall.Core, reference: FileReference): Consequence[String] = for {
    workspace <- WorkspaceId.parseC(reference.workspace)
    path <- WorkspaceFilePath.parseC(reference.path)
    read <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), path)
    text <- read.text.map(Consequence.success).getOrElse(N.invalid("Retained validation evidence file is absent"))
  } yield text
  private def _evidence_c(core: ActionCall.Core, observation: Observation): Consequence[ObservedEvidence] = for {
    _ <- if (observationValid(observation)) Consequence.unit else N.invalid("Unsupported retained validation observation")
    terminal <- _read_c(core, observation.terminalLog)
    stream <- _read_c(core, observation.sbtStream)
    report <- _read_c(core, observation.junitReport)
    evidence = ObservedEvidence(observation, terminal, stream, report)
    _ <- if (evidenceValid(evidence)) Consequence.unit else N.invalid("Retained terminal, stream and JUnit evidence disagree or do not establish successful nonempty execution")
  } yield evidence

  private final case class Loaded(current: InstanceRecord, payload: N.Payload, target: Target)
  private def _load_c(h: N.Host, definition: DefinitionBinding, storage: WorkflowComponentStorage,
    session: String, observation: Observation, core: ActionCall.Core): Consequence[Loaded] = for {
    instances <- storage.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(InstanceIdentity(session))(using core.executionContext).flatMap(
      _.map(Consequence.success).getOrElse(N.invalid("Recovery Session is absent")))
    _ <- current.validateC
    _ <- if (current.definition == definition && current.lifecycle == Lifecycle.Active && current.suspension.isEmpty)
      Consequence.unit else N.invalid("Recovery requires this exact open standalone native frontier")
    payload <- N.loadPayloadC(storage, session, current.revision.value, core)
    _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, core.executionContext)
    evidence <- _evidence_c(core, observation)
    target <- targetFor(payload.state, core.component.get.core.instanceId.canonicalKey, session, current.revision.value,
      payload.root, payload.branch, payload.baseHead, evidence)
  } yield Loaded(current, payload, target)

  private[admission] val DESCRIPTION_STATUS = "decision-required"

  def operations(h: N.Host, definition: DefinitionBinding, evidence: EvidencePort): Vector[spec.OperationDefinition] =
    Vector(new Operation(false, h, definition, evidence), new Operation(true, h, definition, evidence))
  private final class Operation(recover: Boolean, h: N.Host, definition: DefinitionBinding,
    evidence: EvidencePort) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (recover)
      SmWorkflowComponent.StandaloneFixServiceService.RecoverStandaloneFixValidationOperation
      else SmWorkflowComponent.StandaloneFixServiceService.DescribeStandaloneFixValidationRecoveryOperation)
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
      val request = incoming
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          input <- if (recover) NativeWorkflowOperationInput.valueC[V.StandaloneFixValidationRecoveryInput](incoming, "input")
            .map[Either[V.StandaloneFixValidationRecoveryInput, V.StandaloneFixValidationRecoveryDescriptionInput]](Left(_))
            else NativeWorkflowOperationInput.valueC[V.StandaloneFixValidationRecoveryDescriptionInput](incoming, "input")
              .map[Either[V.StandaloneFixValidationRecoveryInput, V.StandaloneFixValidationRecoveryDescriptionInput]](Right(_))
          session = input.fold(_.sessionId, _.sessionId)
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
          selected = core.copy(executionContext = context)
          storage <- N.storageC(h, definition, selected)
          expected <- input.fold(i => targetC(i.target).map(Some(_)), _ => Consequence.success(Option.empty[Target]))
          observation <- input.fold(_ => Consequence.success(expected.get.evidence.observation), i => observationC(i.observation))
          loaded <- _load_c(h, definition, storage, session, observation, selected)
          response <- input match {
            case Right(_) => Consequence.success(NativeWorkflowOperationOutput.response(
              V.StandaloneFixValidationRecoveryDescriptionResult(session, DESCRIPTION_STATUS, message,
                loaded.target.asJson.noSpaces, choices)))
            case Left(input) => for {
              _ <- currentTargetC(expected.get, loaded.target)
              result <- if (input.choice == "Defer") Consequence.success(NativeWorkflowOperationOutput.response(
                N.view(loaded.current, loaded.payload.state, "Deferred", "No validation recovery direction was stored")))
                else for {
                  reference <- Consequence.success(ContextReference(input.reference.identity, input.reference.revision))
                  direction = Direction(loaded.target, input.choice, input.rationale, loaded.target.participant, reference)
                  state <- collectC(loaded.target.componentInstance, loaded.payload.state, direction, selected, evidence)
                  saved <- N.saveC(h, storage, loaded.current, loaded.payload.copy(state = state), selected)
                } yield NativeWorkflowOperationOutput.response(N.view(saved, state, "Recovered", message))
            } yield result
          }
        } yield response
      }
    })
  }
}
