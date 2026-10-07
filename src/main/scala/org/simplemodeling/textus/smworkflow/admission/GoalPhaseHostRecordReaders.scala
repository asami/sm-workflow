package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration

/**
 * Pure reader composition. No pool, record installation or human grant.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object GoalPhaseHostRecordReaders {
  final case class Ports(selection: ProfileSelectionDataStorePort,
    invocation: GoalPhaseSubmissionRegistration.DataStorePort, authority: GoalPhaseClosureAuthority.Ports)

  def bindC(host: ResolvedConfiguration, workflow: GoalPhaseManagedStartAssembly.Declaration): Consequence[Ports] =
    if (workflow == null || workflow.configuration == null || workflow.instances == null || workflow.continuations == null)
      Consequence.configurationInvalid("GoalPhase host readers require their Workflow declaration")
    else for {
      _ <- workflow.configuration.validateC
      records <- GoalPhaseHostRecordReadPolicy.decodeC(host)
      _ <- if (records.storeName == workflow.configuration.workflowStore.name &&
          !records.collections.exists { (_, collection) =>
            collection == workflow.instances || collection == workflow.continuations
          }) Consequence.unit
        else Consequence.configurationInvalid("GoalPhase host records must use the Workflow store and separate collections")
      selection <- requiredC(records, GoalPhaseHostRecordReadPolicy.Purpose.Selection)
      association <- requiredC(records, GoalPhaseHostRecordReadPolicy.Purpose.Association)
      authorization <- requiredC(records, GoalPhaseHostRecordReadPolicy.Purpose.Authorization)
      decision <- requiredC(records, GoalPhaseHostRecordReadPolicy.Purpose.Decision)
    } yield Ports(new ProfileSelectionDataStorePort(records.storeName, selection),
      new GoalPhaseSubmissionRegistration.DataStorePort(records.storeName, association),
      GoalPhaseClosureAuthority.Ports(new GoalPhaseClosureAuthorizationRecord.DataStorePort(records.storeName, authorization),
        new GoalPhaseClosureDecisionRecord.DataStorePort(records.storeName, decision)))

  private def requiredC(records: GoalPhaseHostRecordReadPolicy.Declaration, purpose: GoalPhaseHostRecordReadPolicy.Purpose) =
    records.collections.find(_._1 == purpose) match {
      case Some((_, collection)) => Consequence.success(collection)
      case None => Consequence.configurationInvalid("GoalPhase host record purpose is absent")
    }
}
