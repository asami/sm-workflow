package org.simplemodeling.textus.smworkflow.caller

import io.circe.Json
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.value as V

/** Select exact original public source values; this does not authenticate them.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeCallerInformationSource {
  def validLocator(value: V.WorkflowCodexSourceLocator): Boolean = value != null &&
    Vector(value.threadId, value.turnId, value.itemId).forall(s => s != null && s.nonEmpty &&
      s.trim == s && !s.contains('\u0000') && !s.contains(':'))

  def selectC(source: V.WorkflowCodexSourceLocator, expected: String, raw: String): Consequence[Json] = for {
    _ <- if (validLocator(source)) Consequence.unit else _invalid("requires an exact source locator")
    page <- io.circe.parser.parse(raw).fold(e => _invalid(e.message), Consequence.success)
    _ <- if (page.hcursor.downField("thread").get[String]("id").toOption.contains(source.threadId)) Consequence.unit
      else _invalid("public source names a different caller thread")
    turns <- page.hcursor.get[Vector[Json]]("turns").fold(e => _invalid(e.message), Consequence.success)
    selected = turns.filter(_.hcursor.get[String]("id").toOption.contains(source.turnId))
    _ <- if (selected.size == 1) Consequence.unit else _invalid("exact public source turn is missing or duplicated")
    items <- selected.head.hcursor.get[Vector[Json]]("items").fold(e => _invalid(e.message), Consequence.success)
    selecteditems = items.filter(_.hcursor.get[String]("id").toOption.contains(source.itemId))
    _ <- if (selecteditems.size == 1) Consequence.unit else _invalid("exact public source item is missing or duplicated")
    item = selecteditems.head
    _ <- if (item.hcursor.get[String]("type").toOption.contains(expected)) Consequence.unit
      else _invalid("public source item has a different information kind")
  } yield item

  private def _invalid[A](message: String): Consequence[A] = Consequence.stateConflict("Caller information " + message)
}
