package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence

/** The narrow document write boundary. An implementation must compare every
  * expected digest and install every desired document as one cooperative
  * operation: a returned rejection or failure may not leave a subset of the
  * write set installed. This is not a crash-atomic multi-file transaction;
  * callers must not claim durability across process termination or power loss.
  * No public APPLY Operation is supplied here.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseDocumentCas {
  trait Port extends SplitPhaseDocumentConflict.ReadPort {
    def compareAndSwapC(changes: Vector[SplitPhaseDocumentProjection.Change]):
        Consequence[Port.Result]
  }

  object Port {
    sealed trait Result
    case object Applied extends Result
    case object Rejected extends Result
  }

  sealed trait Outcome
  object Outcome {
    case object Applied extends Outcome
    case object AlreadyApplied extends Outcome
    final case class Conflict(paths: Vector[String]) extends Outcome
  }

  def executeC(plan: SplitPhaseDocumentProjection.Plan,
               port: Port): Consequence[Outcome] =
    if (port == null)
      Consequence.stateConflict("SplitPhase document CAS port is missing")
    else SplitPhaseDocumentStaticValidation.validateC(plan).flatMap(_ =>
      SplitPhaseDocumentConflict.classifyC(plan, port)).flatMap {
      case SplitPhaseDocumentConflict.Outcome.AlreadyApplied =>
        Consequence.success(Outcome.AlreadyApplied)
      case SplitPhaseDocumentConflict.Outcome.Conflict(paths) =>
        Consequence.success(Outcome.Conflict(paths))
      case _: SplitPhaseDocumentConflict.Outcome.Ready =>
        port.compareAndSwapC(plan.changes).flatMap {
          case Port.Applied =>
            SplitPhaseDocumentConflict.classifyC(plan, port).flatMap {
              case SplitPhaseDocumentConflict.Outcome.AlreadyApplied =>
                Consequence.success(Outcome.Applied)
              case _ => Consequence.stateConflict(
                "SplitPhase document CAS did not install the complete write set")
            }
          case Port.Rejected =>
            SplitPhaseDocumentConflict.classifyC(plan, port).flatMap {
              case SplitPhaseDocumentConflict.Outcome.AlreadyApplied =>
                Consequence.success(Outcome.AlreadyApplied)
              case SplitPhaseDocumentConflict.Outcome.Conflict(paths) =>
                Consequence.success(Outcome.Conflict(paths))
              case _ => Consequence.stateConflict(
                "SplitPhase document CAS rejected without observable revision drift")
            }
          case _ => Consequence.stateConflict("SplitPhase document CAS result is invalid")
        }
    }
}
