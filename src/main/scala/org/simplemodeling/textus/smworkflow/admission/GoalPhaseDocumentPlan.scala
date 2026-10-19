package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject}
import org.goldenport.Consequence
import GoalPhaseOrderedProgression as O

/** Explicit document data only; source selection and execution belong to the host.
  * @since   Oct. 17, 2026
  * @version Oct. 17, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseDocumentPlan {
  final case class Binding(step: String, items: Vector[String])
  final case class Item(identity: String, acceptance: String, checked: Boolean)
  final case class Imported(plan: O.Plan, bindings: Vector[Binding], items: Vector[Item])

  private val _schema = "sm-workflow.phase-plan.v1"
  private val _selected_open = "```sm-workflow-plan"
  private val _fence_open = " {0,3}(`{3,}|~{3,})(.*)".r
  private val _item = "- \\[([ xX])\\] ([A-Za-z0-9][A-Za-z0-9_.-]{0,127}):[ \\t]*(.*)".r

  def decodeC(phaseDocument: String, checklistDocument: String): Consequence[Imported] = {
    val decoded = for {
      _ <- _require(phaseDocument != null && checklistDocument != null, "Phase or checklist document is null")
      block <- _selected_block(phaseDocument)
      json <- io.circe.parser.parse(block).left.map(_ => "selected plan block contains malformed JSON")
      envelope <- _closed_object(json, Set("schemaVersion", "plan", "bindings"), "plan envelope")
      schema <- envelope("schemaVersion").get.as[String].left.map(_ => "schemaVersion must be a string")
      _ <- _require(schema == _schema, "unsupported schemaVersion")
      plan <- envelope("plan").get.as[O.Plan].left.map(_ => "plan does not decode as an ordered Phase Plan")
      _ <- _require(O.validatePlanC(plan).isSuccess,
        "plan violates ordered Phase Step/dependency, acceptance or work classification constraints")
      bindings <- _bindings(envelope("bindings").get)
      items <- _checklist(checklistDocument)
      imported <- _bind(plan, bindings, items)
    } yield imported
    decoded.fold(reason => Consequence.configurationInvalid("GoalPhase document plan: " + reason), Consequence.success)
  }

  private def _selected_block(document: String): Either[String, String] = {
    var fence = Option.empty[(Char, Int)]
    var selected = false
    var blocks = Vector.empty[String]
    val body = Vector.newBuilder[String]
    for (line <- document.split("\\r?\\n", -1)) {
      if (selected) {
        if (line == "```") {
          blocks = blocks :+ body.result().mkString("\n")
          body.clear()
          selected = false
        } else body += line
      } else fence match {
        case Some(value) => if (_fence_close(line, value)) fence = None
        case None =>
          if (line == _selected_open) selected = true
          else fence = _opening(line)
      }
    }
    if (selected) Left("selected sm-workflow-plan block is unclosed")
    else if (blocks.isEmpty) Left("exactly one top-level sm-workflow-plan block is required; none found")
    else if (blocks.size != 1) Left("duplicate top-level sm-workflow-plan blocks")
    else Right(blocks.head)
  }

  private def _opening(line: String): Option[(Char, Int)] = line match {
    case _fence_open(marker, info) if marker.head != '`' || !info.contains('`') => Some(marker.head -> marker.length)
    case _ => None
  }

  private def _fence_close(line: String, fence: (Char, Int)): Boolean = {
    val indent = line.takeWhile(_ == ' ').length
    val rest = line.drop(indent)
    indent <= 3 && rest.takeWhile(_ == fence._1).length >= fence._2 &&
      rest.dropWhile(_ == fence._1).forall(c => c == ' ' || c == '\t')
  }

  private def _closed_object(value: Json, fields: Set[String], label: String): Either[String, JsonObject] =
    value.asObject.filter(_.keys.toSet == fields).toRight(label + " must contain exactly " + fields.toVector.sorted.mkString(", "))

  private def _bindings(value: Json): Either[String, Vector[Binding]] = for {
    rows <- value.asArray.toRight("bindings must be an array")
    bindings <- rows.foldLeft[Either[String, Vector[Binding]]](Right(Vector.empty)) { (prior, row) => for {
      result <- prior
      obj <- _closed_object(row, Set("step", "items"), "binding")
      step <- obj("step").get.as[String].left.map(_ => "binding step must be a string")
      items <- obj("items").get.as[Vector[String]].left.map(_ => "binding items must be a string array")
      _ <- _require(_identity(step) && items.nonEmpty && items.forall(_identity),
        "binding requires a stable Step ID and a nonempty list of stable item IDs")
    } yield result :+ Binding(step, items) }
  } yield bindings

  private def _checklist(document: String): Either[String, Vector[Item]] = {
    val items = Vector.newBuilder[Item]
    var current = Option.empty[Item]
    var fence = Option.empty[(Char, Int)]
    def _finish_(): Unit = {
      current.foreach(items += _)
      current = None
    }
    for (line <- document.split("\\r?\\n", -1)) {
      fence match {
        case Some(value) => if (_fence_close(line, value)) fence = None
        case None => _opening(line) match {
          case Some(value) =>
            _finish_()
            fence = Some(value)
          case None => line match {
            case _item(mark, identity, text) =>
              _finish_()
              current = Some(Item(identity, text.trim, mark != " "))
            case value if value.trim.isEmpty => ()
            case value if value.head == ' ' || value.head == '\t' =>
              current = current.map(item => item.copy(acceptance = (item.acceptance + " " + value.trim).trim))
            case _ => _finish_()
          }
        }
      }
    }
    _finish_()
    val result = items.result()
    _require(result.map(_.identity).distinct.size == result.size, "checklist contains repeated stable item IDs").map(_ => result)
  }

  private def _bind(plan: O.Plan, bindings: Vector[Binding], items: Vector[Item]): Either[String, Imported] = {
    val steps = plan.steps.map(_.identity)
    val references = bindings.flatMap(_.items)
    for {
      _ <- _require(bindings.map(_.step).distinct.size == bindings.size, "duplicate Step binding")
      _ <- _require(bindings.map(_.step).toSet == steps.toSet, "bindings must cover exactly every Plan Step")
      _ <- _require(references.distinct.size == references.size, "item ID is referenced more than once")
      _ <- _require(references.forall(id => items.exists(_.identity == id)), "referenced checklist item is missing")
      _ <- _require(items.filter(i => references.contains(i.identity)).forall(_.acceptance.nonEmpty),
        "referenced checklist acceptance is empty")
      ordered = steps.map(step => bindings.find(_.step == step).get)
      selected = ordered.flatMap(_.items).map(id => items.find(_.identity == id).get)
    } yield Imported(plan, ordered, selected)
  }

  private def _identity(value: String): Boolean = value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
  private def _require(valid: Boolean, reason: String): Either[String, Unit] = Either.cond(valid, (), reason)
}
