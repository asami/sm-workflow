package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.syntax.*
import org.goldenport.Consequence
import GoalPhaseDocumentSource as D
import GoalPhaseOrderedProgression as O

/** Business projection of closed Steps; Markdown never supplies execution facts.
  * @since   Oct. 17, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseDocumentProgress {
  final case class Policy(source: D.Source) {
    def paths: Vector[String] = Vector(source.phasePath, source.checklistPath)
    def validateC(plan: O.Plan, fixedCandidatePaths: Vector[String]): Consequence[Unit] = for {
      _ <- D.validateC(source)
      _ <- O.validatePlanC(plan)
      _ <- if (fixedCandidatePaths != null && paths.forall(fixedCandidatePaths.contains) &&
          plan.steps.forall(step => paths.forall(step.ownedPaths.contains))) Consequence.unit
        else _invalid("enabled progress requires both literal source paths in every Step and fixed host candidate paths")
    } yield ()
  }
  final case class Step(step: String, items: Vector[String], commit: O.Commit, review: Option[String],
    admission: Option[GoalPhaseStepAssessment.Admission] = None)
  object Step {
    def apply(step: String, items: Vector[String], commit: O.Commit, review: String): Step =
      new Step(step, items, commit, Some(review))
  }
  final case class Projection(phase: String, steps: Vector[Step])
  final case class Merged(enabled: Boolean, replacement: Option[String])

  private val _schema = "sm-workflow.phase-progress.v1"
  private val _opening = "```sm-workflow-progress"
  private val _fence = " {0,3}(`{3,}|~{3,})(.*)".r
  private final case class Block(start: Int, end: Int, newline: String)

  object Projection {
    given Encoder[Projection] = Encoder.instance(value => Json.obj(
      "schemaVersion" -> Json.fromString(_schema), "phase" -> Json.fromString(value.phase),
      "steps" -> Json.fromValues(value.steps.map(row => Json.obj(
        "step" -> Json.fromString(row.step), "items" -> row.items.asJson,
        "commit" -> Json.obj("identity" -> Json.fromString(row.commit.identity),
          "parent" -> Json.fromString(row.commit.parent), "tree" -> Json.fromString(row.commit.tree)),
        "review" -> row.review.asJson).mapObject(fields => row.admission.fold(fields)(a => fields.add("admission", a.asJson)))))))
    given Decoder[Projection] = Decoder.instance(cursor =>
      _decode(cursor.value).left.map(reason => DecodingFailure(reason, cursor.history)))
  }

  def projectionC(controller: O.State, documents: D.Bound): Consequence[Projection] = for {
    _ <- D.instructionsC(Some(documents), None)
    _ <- if (controller != null && controller.completed.map(_.step) ==
        controller.plan.steps.take(controller.completed.size).map(_.identity)) Consequence.unit
      else _invalid("completed Steps differ from the ordered controller")
    rows <- controller.completed.foldLeft(Consequence.success(Vector.empty[Step])) { (prior, closed) => for {
      result <- prior
      binding <- documents.bindings.filter(_.step == closed.step) match {
        case Vector(value) => Consequence.success(value)
        case _ => _invalid("completed Step has no unique protected item binding")
      }
    } yield result :+ Step(closed.step, binding.items.sorted, closed.commit, closed.review, closed.admission) }
    result = Projection(controller.plan.phase, rows)
    _ <- _decode(result.asJson).fold(_invalid(_), _ => Consequence.unit)
  } yield result

  def policyC(plan: O.Plan, documents: D.Bound, fixedCandidatePaths: Vector[String]): Consequence[Policy] = for {
    _ <- D.instructionsC(Some(documents), None)
    _ <- Policy(documents.source).validateC(plan, fixedCandidatePaths)
  } yield Policy(documents.source)

  def mergeC(currentText: String, phase: String, previous: Option[Projection],
    desired: Projection): Consequence[Merged] = {
    val merged = for {
      _ <- _require(currentText != null && _text(phase) && previous != null && desired != null,
        "merge requires current text, phase and actual projections")
      _ <- _decode(desired.asJson)
      _ <- _require(desired.phase == phase && previous.forall(_.phase == phase), "projection phase differs")
      block <- _block(currentText)
      result <- block match {
        case None if previous.isEmpty => Right(Merged(false, None))
        case None => Left("owned progress overlap: previously written block is missing")
        case Some(value) => for {
          json <- io.circe.parser.parse(currentText.substring(value.start, value.end))
            .left.map(_ => "owned progress overlap: malformed JSON")
          current <- _decode(json)
          _ <- _require(current.phase == phase, "owned progress overlap: contradictory phase")
          _ <- previous match {
            case Some(actual) => _require(current == actual, "owned progress overlap: fields differ from actual prior progress")
            case None => _require(current.steps.isEmpty, "initial progress cannot supply execution facts")
          }
        } yield if (current == desired) Merged(true, None) else {
          val body = desired.asJson.spaces2.replace("\n", value.newline) + value.newline
          Merged(true, Some(currentText.substring(0, value.start) + body + currentText.substring(value.end)))
        }
      }
    } yield result
    merged.fold(_invalid(_), Consequence.success)
  }

  private def _decode(value: Json): Either[String, Projection] = for {
    obj <- _object(value, Set("schemaVersion", "phase", "steps"))
    schema <- obj("schemaVersion").get.as[String].left.map(_ => "invalid progress schema")
    _ <- _require(schema == _schema, "unsupported progress schema")
    phase <- obj("phase").get.as[String].left.map(_ => "invalid progress phase")
    _ <- _require(_text(phase), "empty progress phase")
    rows <- obj("steps").get.asArray.toRight("progress Steps must be an array")
    steps <- rows.foldLeft[Either[String, Vector[Step]]](Right(Vector.empty)) { (prior, row) => for {
      result <- prior
      fields <- _object(row, if (row.hcursor.downField("admission").succeeded)
        Set("step", "items", "commit", "review", "admission") else Set("step", "items", "commit", "review"))
      step <- fields("step").get.as[String].left.map(_ => "invalid Step identity")
      items <- fields("items").get.as[Vector[String]].left.map(_ => "invalid item identities")
      commit <- _object(fields("commit").get, Set("identity", "parent", "tree"))
      identity <- commit("identity").get.as[String].left.map(_ => "invalid commit identity")
      parent <- commit("parent").get.as[String].left.map(_ => "invalid commit parent")
      tree <- commit("tree").get.as[String].left.map(_ => "invalid commit tree")
      review <- fields("review").get.as[Option[String]].left.map(_ => "invalid review identity")
      admission <- row.hcursor.get[Option[GoalPhaseStepAssessment.Admission]]("admission").left.map(_ => "invalid Step admission")
      _ <- _require(_id(step) && items.nonEmpty && items.forall(_id) && items.distinct == items &&
        Vector(identity, parent, tree).forall(_oid) && O.validStepAcceptance(O.ClosedStep(step,
          O.Commit(identity, parent, tree), review, admission)), "invalid progress Step business fields")
    } yield result :+ Step(step, items, O.Commit(identity, parent, tree), review, admission) }
    _ <- _require(steps.map(_.step).distinct.size == steps.size &&
      steps.flatMap(_.items).distinct.size == steps.flatMap(_.items).size, "duplicate progress identities")
  } yield Projection(phase, steps)

  private def _block(text: String): Either[String, Option[Block]] = {
    var fence = Option.empty[(Char, Int)]
    var selected = Option.empty[(Int, String)]
    var blocks = Vector.empty[Block]
    val lines = "[^\n]*(?:\n|$)".r.findAllMatchIn(text).filter(_.start < text.length)
    for (matched <- lines) {
      val raw = matched.matched
      val line = raw.stripSuffix("\n").stripSuffix("\r")
      selected match {
        case Some((start, newline)) if line == "```" =>
          blocks = blocks :+ Block(start, matched.start, newline)
          selected = None
        case Some(_) => ()
        case None => fence match {
          case Some((marker, length)) =>
            val indent = line.takeWhile(_ == ' ').length
            val rest = line.drop(indent)
            if (indent <= 3 && rest.takeWhile(_ == marker).length >= length &&
                rest.dropWhile(_ == marker).forall(c => c == ' ' || c == '\t')) fence = None
          case None if line == _opening =>
            selected = Some(matched.end -> (if (raw.endsWith("\r\n")) "\r\n" else "\n"))
          case None => line match {
            case _fence(marker, info) if marker.head != '`' || !info.contains('`') => fence = Some(marker.head -> marker.length)
            case _ => ()
          }
        }
      }
    }
    if (selected.nonEmpty) Left("owned progress overlap: unclosed progress block")
    else if (blocks.size > 1) Left("owned progress overlap: duplicate progress blocks")
    else Right(blocks.headOption)
  }

  private def _object(value: Json, fields: Set[String]): Either[String, JsonObject] =
    value.asObject.filter(_.keys.toSet == fields).toRight("owned progress overlap: invalid object fields")
  private def _require(value: Boolean, reason: String): Either[String, Unit] = Either.cond(value, (), reason)
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.exists(_.isControl)
  private def _id(value: String): Boolean = value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
  private def _oid(value: String): Boolean = value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("GoalPhase document progress: " + reason)
}
