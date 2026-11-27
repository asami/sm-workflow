package org.simplemodeling.textus.smworkflow.admission {

import scala.language.strictEquality
import cats.Eq

/** Comparison model for the bounded strict Scala 3 authoring target.
  * These record-shaped fixtures do not migrate the workflow's existing ID model.
  * @since   Nov. 20, 2026
  * @version Nov. 20, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object StrictComparisonExamples {
  final case class SourceId(value: String) derives CanEqual
  final case class SessionId(value: String) derives CanEqual

  final case class SourceSnapshot(id: SourceId, payload: String) derives CanEqual

  def sameValue(left: SourceSnapshot, right: SourceSnapshot)(using equality: Eq[SourceSnapshot]): Boolean =
    equality.eqv(left, right)

  def sameSource(left: SourceSnapshot, right: SourceSnapshot): Boolean = left.id == right.id

  def sameBytes(left: Array[Byte], right: Array[Byte]): Boolean = java.util.Arrays.equals(left, right)

  def sameArray(left: Array[Byte], right: Array[Byte]): Boolean = left.eq(right)

  final case class Revision[Value](value: Value)
  object Revision {
    // Comparison is available only when the stored values may already compare.
    given [Left, Right](using CanEqual[Left, Right]): CanEqual[Revision[Left], Revision[Right]] =
      CanEqual.derived
  }

  def sameRevision[Left, Right](left: Revision[Left], right: Revision[Right])
    (using CanEqual[Left, Right]): Boolean = left == right

  /** Normalize nullable Java input once at the foreign API boundary. */
  def sourceIdFromProperties(properties: java.util.Properties, key: String): Option[SourceId] =
    properties.getProperty(key) match {
      case value: String => Some(SourceId(value))
      case _ => None
    }
}

}
