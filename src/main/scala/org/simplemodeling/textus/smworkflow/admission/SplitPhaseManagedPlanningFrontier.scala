package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.ContinuationIdentity
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceCommand
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseManagedPlanningCheckpoint as D,
  SplitPhasePlanningSnapshot as R}

/** Read the actual saved successor. No planner, allocator, AI or fresh
  * publication runs here. A reader failure never selects an older frontier.
  * @since   Oct.  6, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object SplitPhaseManagedPlanningFrontier {
  final class Observed private[SplitPhaseManagedPlanningFrontier] (
    val original: D.ObservedCurrent, val snapshot: R.Record,
    val publicationPostimage: InstanceRecord, val invocationMode: String) {
    def current: InstanceRecord = original.current
  }

  def loadForAdvanceC(command: SplitPhaseAdvanceCommand,
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core): Consequence[Observed] = for {
    reads <- D.bindC(core.component.get.subsystem.get.configuration, declaration, catalog)
    original <- reads.loadCurrentForCallC(command, core)
    required <- SplitPhaseDefinitionBinding.reasoningSemanticOperationC
    reasoning = original.current.history.reverseIterator.flatMap(_.suspension)
      .find(_.completion.value == required.metadata.completionContract.identity)
    selected <- reasoning match {
      case Some(suspension) => SplitPhaseManagedReasoningCompletion.observeForCallC(
        ContinuationIdentity(suspension.continuationIdentity.value), declaration, catalog, core).flatMap {
          case Some(value) => value.downstream match {
            case Some(snapshot) => Consequence.success((snapshot, value.next,
              value.source.checkpoint.originalStart.command.input.invocationMode))
            case None => SplitPhaseManagedReasoningHumanCompletion.observeForAdvanceC(command, declaration, catalog, core)
              .flatMap { human => human.downstream match {
                case Some(snapshot) => Consequence.success((snapshot, human.next,
                  human.plan.source.source.checkpoint.originalStart.command.input.invocationMode))
                case None => _invalid("stopped Human reasoning has no planning successor")
              }}
          }
          case None => _invalid("actual reasoning result has not completed")
        }
      case None =>
        val step = original.current.history.reverseIterator.find(h =>
          h.causation.value.startsWith("human-resolution:") || h.causation.value.startsWith("scope-planning:"))
        step match {
          case Some(entry) if entry.causation.value.startsWith("human-resolution:") => for {
            reader <- SplitPhaseManagedHumanResolutionCheckpoint.bindC(core.component.get.subsystem.get.configuration,
              declaration, catalog)
            saved <- reader.loadForCallC(command, core).flatMap {
              case Some(value) => Consequence.success(value)
              case None => _invalid("actual Human successor is absent")
            }
          } yield (saved.record.downstream, saved.record.next,
            saved.source.plan.source.record.snapshot.first.originalStart.command.input.invocationMode)
          case Some(_) => for {
            reader <- SplitPhaseManagedScopePlanningCheckpoint.bindC(core.component.get.subsystem.get.configuration,
              declaration, catalog)
            saved <- reader.loadForCallC(command, core).flatMap {
              case Some(value) => Consequence.success(value)
              case None => _invalid("actual scope calculation is absent")
            }
          } yield (saved.record.downstream, saved.record.next,
            saved.scope.record.snapshot.first.originalStart.command.input.invocationMode)
          case None => reads.loadRetainedForCallC(command, core).flatMap {
            case Some(value: D.RetainedMechanical) =>
              Consequence.success((value.observed.record.snapshot, value.observed.publicationPostimage,
                value.observed.record.originalStart.command.input.invocationMode))
            case Some(value: D.RetainedPartitionCompletion) => value.observed.record.checkpoint match {
              case Some(record) => Consequence.success((record.snapshot, value.observed.record.next,
                record.originalStart.command.input.invocationMode))
              case None => _invalid("actual partition result has no planning successor")
            }
            case None => _invalid("actual planning publication is absent")
          }
        }
    }
    _ <- if (selected._1.selection.proposal.source == original.original.facts.proposal.source &&
        selected._2.identity == original.current.identity)
      Consequence.unit else _invalid("saved frontier crosses original source or native instance")
  } yield new Observed(original, selected._1, selected._2, selected._3)

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase saved planning frontier " + reason)
}
