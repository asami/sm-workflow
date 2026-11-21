package org.simplemodeling.textus.smworkflow.admission {

import scala.language.strictEquality
import cats.Eq
import org.goldenport.id.Identifiable
import org.goldenport.observation.TraceId
import org.goldenport.util.TypeSafeCollections.*

/** Authored consumer of the adopted Core collection API.
  * Value membership uses the complete SourceId value. The separate observation
  * fixture explicitly selects trace identity and retains every absent identity.
  * These fixtures do not migrate existing workflow models.
  * @since   Nov. 21, 2026
  * @version Nov. 21, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object CoreCollectionComparisonExamples {
  import StrictComparisonExamples.SourceId

  final case class Observation(id: Option[TraceId], payload: String) derives CanEqual

  private val _source_equality: Eq[SourceId] = Eq.instance((left, right) => left.value == right.value)
  private val _trace_equality: Eq[TraceId] = Eq.instance((left, right) => left.value == right.value)
  private val _trace_relation: Identifiable[Observation, TraceId] = new Identifiable[Observation, TraceId] {
    def id(value: Observation): Option[TraceId] = value.id
  }

  def containsSource(values: Vector[SourceId], query: SourceId): Boolean =
    values.typeSafe.containsEq(query)(using _source_equality)

  def containsTrace(values: Vector[Observation], query: TraceId): Boolean =
    values.typeSafe.identifiableBy(_trace_relation)(using _trace_equality).containsId(query)

  def findTrace(values: Vector[Observation], query: TraceId): Option[Observation] =
    values.typeSafe.identifiableBy(_trace_relation)(using _trace_equality).findById(query)

  def distinctTraces(values: Vector[Observation]): Vector[Observation] =
    values.typeSafe.identifiableBy(_trace_relation)(using _trace_equality).distinctById
}

}
