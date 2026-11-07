package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json, Printer}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import GoalPhaseDependencySourceRevision as R
import GoalPhaseNativeCandidate as C

/** One source selector value derived from an authenticated canonical capture.
  * Policy and Direction references express causality, not tamper-proof evidence.
  * Native traversal and independent Human admission remain their own owners.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseCanonicalPublicationProgramCorrection {
  final case class Correction(oldProcessPolicy: String, correctedProcessPolicy: String,
    programIdentity: String, sourceEnvironmentKey: String, previousRoot: String, sourceRoot: String,
    sourceCapture: ContextReference, sourceDirectionDirective: ContextReference, previousDirectionDirective: ContextReference,
    previousBinding: ContextReference, binding: ContextReference)
  object Correction {
    given Encoder[Correction] = deriveEncoder
    private val _decoder: Decoder[Correction] = deriveDecoder
    given Decoder[Correction] = Decoder.instance(c => _decoder(c).flatMap(v =>
      if (valid(v) && v.asJson == c.value) Right(v)
      else Left(DecodingFailure("Canonical publisher correction is incomplete, crossed or noncanonical", c.history))))
  }
  private def _exact(value: String): Boolean = value != null && value.trim == value && value.nonEmpty &&
    !value.exists(_.isControl)
  private def _root(value: String): Boolean = _exact(value) && scala.util.Try(java.nio.file.Path.of(value)).toOption
    .exists(path => path.isAbsolute && path.normalize.toString == value)
  private def _sorted(value: Json): String = Printer.noSpaces.copy(sortKeys = true).print(value)
  private def _policy(value: String): Option[Json] = Option(value).flatMap(io.circe.parser.parse(_).toOption)
    .filter(v => v.isObject && _sorted(v) == value)
  private def _programs(policy: Json): Option[Vector[Json]] =
    policy.hcursor.downField("processPlan").get[Vector[Json]]("sbtPrograms").toOption
  private def _environment(program: Json): Option[Map[String, String]] =
    program.hcursor.get[Map[String, String]]("environment").toOption
  private def _replace(policy: Json, program: Json, key: String, root: String): Json =
    policy.mapObject(fields => fields.add("processPlan", fields("processPlan").get.mapObject(plan =>
      plan.add("sbtPrograms", Json.fromValues(plan("sbtPrograms").get.asArray.get.map { p =>
        if (p == program) p.mapObject(fields => fields.add("environment",
          fields("environment").get.mapObject(_.add(key, root.asJson)))) else p
      })))))

  def valid(value: Correction): Boolean = value != null && Vector(value.programIdentity,
    value.sourceEnvironmentKey, value.previousRoot, value.sourceRoot).forall(_exact) &&
    _root(value.previousRoot) && _root(value.sourceRoot) && value.previousRoot != value.sourceRoot &&
    Vector(value.sourceCapture, value.sourceDirectionDirective,
      value.previousDirectionDirective, value.previousBinding, value.binding).forall(R.referenceValid) &&
    (for {
      old <- _policy(value.oldProcessPolicy)
      corrected <- _policy(value.correctedProcessPolicy)
      programs <- _programs(old)
      program <- programs.filter(_.hcursor.get[String]("identity").contains(value.programIdentity)) match {
        case Vector(v) => Some(v)
        case _ => None
      }
      environment <- _environment(program)
    } yield program.hcursor.get[String]("operation").contains("publish-local") &&
      environment.get(value.sourceEnvironmentKey).contains(value.previousRoot) &&
      _replace(old, program, value.sourceEnvironmentKey, value.sourceRoot) == corrected).contains(true)

  /** Only the declared selected primary check routes participate. Historical
    * programs intentionally retained by the host do not select the new root.
    */
  def derive(candidate: C.Payload, target: LocalPublication.Target): Option[Correction] = for {
    capture <- Option(candidate).flatMap(_.dependencySourceCapture)
      .filter(c => c.schemaVersion == R.canonicalCaptureSchema && R.captureValid(c))
    direction = capture.direction
    origin <- direction.target.canonicalOrigin
    previous <- origin.previousDirection.orElse(origin.originalDirection).filter(R.directionValid)
    oldbinding = previous.target.binding
    binding = direction.target.binding
    consumption = direction.target.proposal.consumption
    _ <- Option.when(target != null && target.beforeChecks && target.dependency.isEmpty &&
      target.workspace == consumption.primaryWorkspace && consumption.sourceRoot == binding.root &&
      oldbinding.root != binding.root && previous.target.sessionId == capture.sessionId &&
      oldbinding.dependency.identity == binding.dependency.identity &&
      oldbinding.dependency.repository == binding.dependency.repository)(())
    old <- _policy(origin.processPolicy)
    programs <- _programs(old)
    publisher <- programs.filter(p => p.hcursor.get[String]("target").contains(target.workspace) &&
      p.hcursor.get[String]("operation").contains("publish-local")) match {
        case Vector(v) => Some(v)
        case _ => None
      }
    identity <- publisher.hcursor.get[String]("identity").toOption.filter(_exact)
    environment <- _environment(publisher)
    selected = consumption.programs.filter(p =>
      direction.target.plan.steps.find(_.identity == direction.target.step).exists(_.checks.contains(p.check)))
    checks <- Option.when(selected.nonEmpty)(selected).flatMap(_.foldLeft(Option(Vector.empty[Json])) { (prior, route) =>
      for {
        values <- prior
        program <- programs.filter(p => p.hcursor.get[String]("identity").contains(route.program) &&
          p.hcursor.get[String]("target").contains(route.workspace) &&
          p.hcursor.get[String]("operation").contains(route.kind)) match {
            case Vector(v) => Some(v)
            case _ => None
          }
      } yield values :+ program
    })
    key <- environment.toVector.collect { case (key, value) if _exact(key) && value == oldbinding.root &&
      checks.forall(p => _environment(p).exists(_.get(key).contains(binding.root))) => key } match {
        case Vector(v) => Some(v)
        case _ => None
      }
    correction = Correction(origin.processPolicy, _sorted(_replace(old, publisher, key, binding.root)), identity,
      key, oldbinding.root, binding.root, ContextReference(capture.sessionId, capture.sourceRevision),
      direction.directive, previous.directive, ContextReference(oldbinding.identity, oldbinding.revision.toString),
      ContextReference(binding.identity, binding.revision.toString))
    _ <- Option.when(valid(correction))(())
  } yield correction

  def matches(value: Correction, candidate: C.Payload, target: LocalPublication.Target): Boolean =
    valid(value) && derive(candidate, target).contains(value)

  /** No output-text classifier supplies source or retry authority. This must be
    * the base prerequisite's first retained formal failure, never a successor.
    */
  def failure(value: LocalPublication.Result, target: LocalPublication.Target, session: String,
    participant: String, candidate: C.Payload, correction: Correction): Boolean =
    matches(correction, candidate, target) && value != null && value.request != null && value.request.input != null &&
      value.request.input == firstInput(session, candidate, target) &&
      value.request.target == target && value.request.participant == participant && value.request.source != null &&
      candidate.candidate.exists(s => s.head == value.request.source.head && s.tree == value.request.source.tree) &&
      value.status == "failed" && value.nextPreparation != null && value.nextPreparation.isEmpty && value.process != null &&
      value.process.exists(raw => WorkflowSbtCapture.decode(raw).toOption.exists { process =>
        WorkflowSbtCapture.exitCode(process).exists(_ != 0) && process.safeProgramIdentity == correction.programIdentity &&
          !process.stdout.truncated && !process.stderr.truncated &&
          WorkflowSbtCapture.text(process.stdout).textStatus == "complete" &&
          WorkflowSbtCapture.text(process.stderr).textStatus == "complete"
      })

  def firstInput(session: String, candidate: C.Payload, target: LocalPublication.Target): LocalPublication.Input = {
    val input = LocalPublicationRuntime.prerequisiteInput(session, candidate, target)
    input.copy(requestIdentity = input.requestIdentity + ":prerequisite:0")
  }
}
