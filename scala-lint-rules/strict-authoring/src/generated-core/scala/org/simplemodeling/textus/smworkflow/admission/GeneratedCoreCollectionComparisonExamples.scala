package org.simplemodeling.textus.smworkflow.admission {

import scala.language.strictEquality
import domain.value.{CountryCode, RegionCode}
import org.goldenport.util.TypeSafeCollections.*

/** Authored API consumer of actual Cozy-generated value types and their Eq.
  * The call sites in this file are authored, not generator output. Genuine
  * generated API calls are separately adopted from Cozy's logical-action compiler.
  * @since   Nov. 21, 2026
  * @version Nov. 21, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GeneratedCoreCollectionComparisonExamples {
  def containsCountry(values: Vector[CountryCode], query: CountryCode): Boolean =
    values.typeSafe.containsEq(query)

  def containsRegion(values: Vector[RegionCode], query: RegionCode): Boolean =
    values.typeSafe.containsEq(query)
}

}
