package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.simplemodeling.model.datatype.EntityCollectionId

/** Passive finite input decoding; resource acquisition belongs to the current call.
  * @since   Oct. 15, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativePreparationInput {
  def optionsC(options: Map[String, String], required: Set[String],
    readText: String => Consequence[String], optional: Set[String] = Set.empty): Consequence[Map[String, String]] =
    if (options == null || readText == null || !required.subsetOf(options.keySet) ||
        !options.keySet.subsetOf(required ++ optional) || options.exists((key, value) => !exact(key) || !exact(value)))
      invalid("requires exactly the selected finite fields and exact nonempty values")
    else Consequence.success(options)

  def exact(value: String): Boolean =
    value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)

  def collectionC(value: String): Consequence[Unit] =
    if (value == null || !value.startsWith("entity:")) invalid("collection must be a canonical entity address")
    else value.drop("entity:".length).split("\\.", -1).toVector match {
      case Vector(major, minor, name) if Vector(major, minor, name).forall(_.matches("[A-Za-z][A-Za-z0-9_]*")) =>
        Consequence(EntityCollectionId(major, minor, name)).map(_ => ())
      case _ => invalid("collection must be entity:major.minor.name with valid labels")
    }

  def instantC(value: String): Consequence[Instant] =
    try Consequence.success(Instant.parse(value))
    catch { case NonFatal(_) => invalid("occurred-at must be an ISO Instant") }

  def readC(reference: String, readText: String => Consequence[String]): Consequence[Record] =
    readText(reference).flatMap(new RecordDecoder().json)

  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Preparation input " + reason)
}
