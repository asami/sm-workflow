package org.simplemodeling.textus.smworkflow.admission

/** Shared boundary classification; explicit partition computation owns enumeration.
  * @since   Sep. 27, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object SplitPhasePartitionEnumeration {
  enum EdgeDisposition {
    case CutOnly, CrossOnly, Either, Unresolved
  }
}
