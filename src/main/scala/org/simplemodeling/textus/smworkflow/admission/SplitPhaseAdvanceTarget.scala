package org.simplemodeling.textus.smworkflow.admission

import org.simplemodeling.textus.smworkflow.value.SplitPhasePartitionContextReference

/** Internal target resolved from the current invocation and assigned context.
  * It is not a public command DTO or a second transport decoding route.
  * @since   Oct. 12, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] final case class SplitPhaseAdvanceTarget(
  instanceIdentity: String,
  context: SplitPhasePartitionContextReference
)
