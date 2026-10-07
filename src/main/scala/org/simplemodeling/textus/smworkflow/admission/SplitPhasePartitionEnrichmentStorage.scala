package org.simplemodeling.textus.smworkflow.admission

import cats.syntax.all.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.{ComponentDataStore, ComponentRecordStorage, DataStore}
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedPartitionCompletion as P,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPlanningAdvance as W, SplitPhasePartitionEnrichmentPublication as U,
  SplitPhasePartitionEnrichmentJsonV1 as J, SplitPhaseExplicitPlanningInputJsonV1 as F}

/** Purpose metadata only at assembly. Capabilities are freshly bound inside the
  * current native call; no provider, pool, lease or bootstrap context is retained.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhasePartitionEnrichmentStorage {
  val prefix = "sm-workflow.split-phase.partition-enrichment."
  final class Port private[SplitPhasePartitionEnrichmentStorage] (
    val storeName: String, val collection: DataStore.CollectionId,
    val estimatesCollection: DataStore.CollectionId, val handoffsCollection: DataStore.CollectionId,
    val planningCollection: DataStore.CollectionId, val proposalCollection: DataStore.CollectionId,
    val ownershipCollection: DataStore.CollectionId) {

    def stageForCallC(plan: P.Plan, core: ActionCall.Core): Consequence[ExecUowM[Unit]] =
      U.projectC(plan).flatMap {
        case None => Consequence.success(ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[org.goldenport.cncf.unitofwork.UnitOfWorkOp, X], Unit](()))
        case Some(projected) => for {
          publication <- J.encodeC(projected.record)
          estimate <- projected.estimates.fold(Consequence.success(Vector.empty[(DataStore.CollectionId, String, Record)])) { value =>
            F.encodeEstimatesC(value).map(raw => Vector((estimatesCollection, value.reference.identity, Record.data(F.storageField -> raw))))
          }
          handoff <- projected.handoffs.fold(Consequence.success(Vector.empty[(DataStore.CollectionId, String, Record)])) { value =>
            F.encodeHandoffsC(value).map(raw => Vector((handoffsCollection, value.reference.identity, Record.data(F.storageField -> raw))))
          }
          planning <- projected.planning.fold(Consequence.success(Vector.empty[(DataStore.CollectionId, String, Record)])) { value =>
            F.encodePlanningC(value).map(raw => Vector((planningCollection, value.reference.identity, Record.data(F.storageField -> raw))))
          }
          candidates <- projected.candidates.foldLeft(Consequence.success(Vector.empty[(DataStore.CollectionId, String, Record)])) {
            (previous, candidate) => for {
              accumulated <- previous
              proposal <- F.encodeProposalC(candidate.proposal)
              ownership <- F.encodeOwnershipC(candidate.ownership)
              handoffs <- F.encodeHandoffsC(candidate.handoffs)
            } yield accumulated ++ Vector(
              (proposalCollection, candidate.proposal.reference.identity, Record.data(F.storageField -> proposal)),
              (ownershipCollection, candidate.ownership.reference.identity, Record.data(F.storageField -> ownership)),
              (handoffsCollection, candidate.handoffs.reference.identity, Record.data(F.storageField -> handoffs)))
          }
          programs <- (estimate ++ handoff ++ planning ++ candidates :+ (collection, projected.record.result.identity, Record.data(J.storageField -> publication)))
            .foldLeft(Consequence.success(Vector.empty[ExecUowM[Unit]])) { case (previous, (target, identity, row)) => for {
              accumulated <- previous
              storage <- ComponentRecordStorage.bindForCallC(core, storeName, target, DataStore.StringEntryId(identity))
              staged <- storage.stageCreateC(row)
            } yield accumulated :+ staged }
        } yield programs.foldLeft(ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[org.goldenport.cncf.unitofwork.UnitOfWorkOp, X], Unit](()))((before, staged) =>
          before.flatMap(_ => staged))
      }

    def loadPlanningForCallC(reference: W.DataReference, core: ActionCall.Core): Consequence[W.Data] =
      if (reference == null || !_exact(reference.identity, reference.revision))
        Consequence.stateConflict("SplitPhase planning observation requires its exact selected reference")
      else _required_c(planningCollection, reference.identity, F.storageField,
        raw => F.decodePlanningC(reference, raw), core)

    def loadForCallC(reference: S.ResultReference, core: ActionCall.Core): Consequence[Option[U.Record]] =
      if (reference == null || !_exact(reference.identity, reference.revision))
        Consequence.stateConflict("SplitPhase enrichment observation requires the exact retained result reference")
      else _load_c(collection, reference.identity, J.storageField, core).flatMap {
        case None => Consequence.success(None)
        case Some(raw) => J.decodeC(reference, raw).map(Some(_))
      }

    def verifyForCallC(plan: P.Plan, current: InstanceRecord, publication: Option[U.Record],
      planning: Option[W.Data], core: ActionCall.Core): Consequence[U.Recovered] = U.projectC(plan).flatMap {
      case None => U.verifyC(plan, current, publication, None, None, planning, Vector.empty)
      case Some(expected) => for {
        estimates <- expected.record.estimates.fold(Consequence.success(Option.empty[E.Estimates])) { reference =>
          _load_c(estimatesCollection, reference.identity, F.storageField, core).flatMap {
            case None => Consequence.success(None)
            case Some(raw) => F.decodeEstimatesC(reference, raw).map(Some(_))
          }
        }
        handoffs <- expected.record.handoffs.fold(Consequence.success(Option.empty[B.HandoffPlan])) { reference =>
          _load_c(handoffsCollection, reference.identity, F.storageField, core).flatMap {
            case None => Consequence.success(None)
            case Some(raw) => F.decodeHandoffsC(reference, raw).map(Some(_))
          }
        }
        candidates <- expected.candidates.foldLeft(Consequence.success(Vector.empty[U.CandidateDocuments])) { (previous, candidate) => for {
          accumulated <- previous
          proposal <- _required_c(proposalCollection, candidate.proposal.reference.identity, F.storageField,
            raw => F.decodeProposalC(candidate.proposal.reference, raw), core)
          ownership <- _required_c(ownershipCollection, candidate.ownership.reference.identity, F.storageField,
            raw => F.decodeOwnershipC(candidate.ownership.reference, raw), core)
          handoffs <- _required_c(handoffsCollection, candidate.handoffs.reference.identity, F.storageField,
            raw => F.decodeHandoffsC(candidate.handoffs.reference, raw), core)
        } yield accumulated :+ U.CandidateDocuments(proposal, ownership, handoffs) }
        recovered <- U.verifyC(plan, current, publication, estimates, handoffs, planning, candidates)
      } yield recovered
    }

    private def _required_c[A](target: DataStore.CollectionId, identity: String, field: String,
      decode: String => Consequence[A], core: ActionCall.Core): Consequence[A] =
      _load_c(target, identity, field, core).flatMap {
        case Some(raw) => decode(raw)
        case None => Consequence.stateConflict("SplitPhase complete planning or candidate row is absent")
      }

    private def _load_c(target: DataStore.CollectionId, identity: String, field: String,
      core: ActionCall.Core): Consequence[Option[String]] = for {
      storage <- ComponentRecordStorage.bindForCallC(core, storeName, target, DataStore.StringEntryId(identity))
      loaded <- storage.loadC()
      value <- loaded match {
        case None => Consequence.success(None)
        case Some(row) => row.getString(field).map(raw => Consequence.success(Some(raw)))
          .getOrElse(Consequence.stateConflict("SplitPhase enrichment row payload is absent or has the wrong type"))
      }
    } yield value
  }

  def bindC(host: ResolvedConfiguration, workflow: SplitPhaseManagedStartAssembly.Declaration): Consequence[Port] =
    if (host == null || workflow == null || workflow.configuration == null || workflow.instances == null || workflow.continuations == null)
      Consequence.configurationInvalid("SplitPhase enrichment requires explicit host and Workflow metadata")
    else for {
      _ <- workflow.configuration.validateC
      store <- _string_c(host, "store-name")
      _ <- if (ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, store).normalizedName == store)
        Consequence.unit else Consequence.configurationInvalid("SplitPhase enrichment store is not canonical")
      raw <- _string_c(host, "record-collection")
      identity <- EntityCollectionId.parse(raw)
      _ <- if (identity.print == raw) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase enrichment collection is not canonical")
      inputs <- SplitPhasePlanningInputReadPolicy.decodeC(host)
      start <- SplitPhaseTypedStartReadPolicy.decodeC(host)
      context <- SplitPhasePartitionContextReadPolicy.decodeC(host)
      selection <- SplitPhaseHostSelectionReadPolicy.decodeC(host)
      target = DataStore.CollectionId.EntityStore(identity)
      targets = F.Purpose.values.toVector.map(inputs.collection) ++ Vector(start.collection, context.collection,
        selection.collection, workflow.instances, workflow.continuations, target)
      _ <- if (Vector(store, inputs.storeName, start.storeName, context.storeName, selection.storeName)
          .forall(_ == workflow.configuration.workflowStore.name) && targets.distinct.size == 15) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase enrichment requires fifteen distinct same-store purposes")
    } yield new Port(store, target, inputs.collection(F.Purpose.Estimates), inputs.collection(F.Purpose.Handoffs),
      inputs.collection(F.Purpose.Planning), inputs.collection(F.Purpose.Proposal), inputs.collection(F.Purpose.Ownership))

  private def _string_c(host: ResolvedConfiguration, key: String): Consequence[String] =
    GoalPhaseHostRecordReadPolicy.storedValue(host.configuration.values, prefix + key) match {
      case Some(ConfigurationValue.StringValue(value)) if _name(value) => Consequence.success(value)
      case _ => Consequence.configurationInvalid("SplitPhase enrichment requires exact host configuration")
    }
  private def _exact(identity: String, revision: String): Boolean = _name(identity) && _name(revision)
  private def _name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
