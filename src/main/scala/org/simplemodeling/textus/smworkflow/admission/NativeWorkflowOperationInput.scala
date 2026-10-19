package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.protocol.Request
import org.goldenport.record.{Record, RecordPresentable}

/** Named business values at the Operation boundary. JSON is a representation
  * of the named value, never a universal command or completion envelope.
  * @since   Oct. 12, 2026
  *  version Oct. 12, 2026
  * @version Oct. 18, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeWorkflowOperationInput {
  def valueC[A](request: Request, name: String)(using reader: ValueReader[A]): Consequence[A] =
    recordC(request, name).flatMap(reader.readC)

  def optionalValueC[A](request: Request, name: String)(using reader: ValueReader[A]): Consequence[Option[A]] =
    _parameter_c(request, name).flatMap {
      case None => Consequence.success(None)
      case Some(value) => _record_c(value, name).flatMap(reader.readC).map(Some(_))
    }

  def vectorC[A](request: Request, name: String)(using reader: ValueReader[A]): Consequence[Vector[A]] =
    _parameter_c(request, name).flatMap {
      case None => Consequence.success(Vector.empty)
      case Some(value: Json) => value.asArray.map(values => _values_c(values, name))
        .getOrElse(_invalid("Workflow parameter " + name + " requires an array"))
      case Some(value: String) => parser.parse(value).fold(
        _ => _invalid("Workflow parameter " + name + " is not business JSON"),
        json => json.asArray.map(values => _values_c(values, name))
          .getOrElse(_invalid("Workflow parameter " + name + " requires an array")))
      case Some(values: Seq[?]) => _values_c(values, name)
      case Some(values: Array[?]) => _values_c(values.toVector, name)
      case Some(_) => _invalid("Workflow parameter " + name + " requires an array")
    }

  private def _values_c[A](values: Seq[?], name: String)(using reader: ValueReader[A]): Consequence[Vector[A]] =
    values.foldLeft(Consequence.success(Vector.empty[A])) { (prior, value) =>
      for {
        accumulated <- prior
        record <- _record_c(value, name)
        decoded <- reader.readC(record)
      } yield accumulated :+ decoded
    }

  def recordC(request: Request, name: String): Consequence[Record] =
    _parameter_c(request, name).flatMap {
      case Some(value) => _record_c(value, name)
      case None => _invalid("Workflow requires named business parameter " + name)
    }

  def optionalTextC(request: Request, name: String): Consequence[Option[String]] =
    _parameter_c(request, name).flatMap {
      case None => Consequence.success(None)
      case Some(value: String) => Consequence.success(Some(value))
      case Some(_) => _invalid("Workflow parameter " + name + " must be a string")
    }

  def textC(request: Request, name: String): Consequence[String] =
    optionalTextC(request, name).flatMap {
      case Some(value) => Consequence.success(value)
      case None => _invalid("Workflow requires parameter " + name)
    }

  /** Allocation uses the incoming execution profile, never a captured host
    * context or the reusable chat's routing identity. */
  def startSessionC(request: Request, context: ExecutionContext): Consequence[String] =
    optionalTextC(request, "sessionId").map(_.getOrElse(context.idGeneration.opaqueId("sm-workflow-session")))

  private def _parameter_c(request: Request, name: String): Consequence[Option[Any]] =
    if (request == null) _invalid("Workflow Operation request is absent")
    else Consequence.success(request.toRecord.getAny(name))

  private def _record_c(value: Any, name: String): Consequence[Record] = value match {
    case record: Record => Consequence.success(record)
    case value: RecordPresentable => Consequence.success(value.toRecord())
    case value: Json => _json_record_c(value, name)
    case value: String => parser.parse(value).fold(
      _ => _invalid("Workflow named parameter " + name + " is not business JSON"),
      _json_record_c(_, name))
    case _ => _invalid("Workflow named parameter " + name + " requires its declared business record")
  }

  private def _json_record_c(value: Json, name: String): Consequence[Record] =
    value.asObject.map(fields => Consequence.success(Record.createFull(
      fields.toVector.map((key, value) => key -> _json_value(value)))))
      .getOrElse(_invalid("Workflow named parameter " + name + " requires an object"))

  // Record's existing transport vocabulary contains heterogeneous primitive
  // cells. Public business types remain the concrete generated named values.
  private def _json_value(value: Json): Any = value.fold(
    null, identity, number => number.toBigDecimal.fold[Any](number.toString)(identity), identity,
    values => values.map(_json_value).toVector,
    fields => Record.createFull(fields.toVector.map((key, value) => key -> _json_value(value))))

  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict(reason)
}
