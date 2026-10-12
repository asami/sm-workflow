package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, parser}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{ContinuationIdentity, WorkflowMechanicalTransition}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, InstanceRevision}
import SplitPhaseExplicitPartitionSemantic as S

/** One business-origin reference at the actual native revision. It stores no
  * native state copy, routing proof, validation mark or independent counter.
  * Each alternative carries only the reference its business owner consumes.
  * @since   Oct. 12, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object SplitPhaseExecutionFrontier {
  enum Value {
    case Initial(context: S.ContextReference)
    case MechanicalPlanning(checkpoint: SplitPhasePlanningCheckpointPreparation.Reference)
    case PartitionCompletion(work: ContinuationIdentity)
    case ScopeCompletion(work: ContinuationIdentity)
    case ScopeProjection(checkpoint: SplitPhaseScopeProjectionCheckpointPreparation.Reference)
    case ScopePlanning(checkpoint: SplitPhaseScopePlanningCheckpointPreparation.Reference)
    case ScopeHumanAnswer(decision: ContinuationIdentity)
    case HumanResolution(checkpoint: SplitPhaseHumanResolutionCheckpointPreparation.Reference)
    case ReasoningCompletion(work: ContinuationIdentity)
    case ReasoningHumanAnswer(decision: ContinuationIdentity)
    case SelectedPreview(receipt: WorkflowMechanicalTransition.Reference)
    case SelectedApply(receipt: WorkflowMechanicalTransition.Reference)
    case Stopped(answer: ContinuationIdentity)
  }
  import SplitPhaseExplicitPartitionRawCodecs.given
  private given Encoder[Value] = deriveEncoder[Value]
  private given Decoder[Value] = deriveDecoder[Value]
  val storageField = "splitPhaseExecutionFrontier"
  private val _prefix = "sm-workflow.split-execution-frontier.v1:"

  def key(instance: InstanceIdentity, revision: InstanceRevision): String =
    "split-frontier:" + io.circe.Json.arr(io.circe.Json.fromString(instance.value),
      io.circe.Json.fromLong(revision.value)).noSpaces

  def encode(value: Value): String = _prefix + value.asJson.noSpaces

  def decodeC(raw: String): Consequence[Value] =
    if (!raw.startsWith(_prefix)) Consequence.stateConflict("Split frontier storage format is unsupported")
    else parser.parse(raw.substring(_prefix.length)).flatMap(_.as[Value])
      .fold(error => Consequence.stateConflict(error.getMessage), Consequence.success)
}
