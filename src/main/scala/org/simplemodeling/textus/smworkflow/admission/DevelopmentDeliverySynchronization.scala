package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.protocol.Property
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhasePostCompletionSync.Synchronization

/** One declared existing-owner synchronization after required local publication.
  * The artifact owner's immutable native intent/result reserve one child. A
  * reconnect observes the intent/result and never replays Start or Advance.
  * Neither local delivery nor its queue waits for later child resolution.
  * @since   Nov. 12, 2026
  * @version Nov. 18, 2026
  */
private[smworkflow] object DevelopmentDeliverySynchronization {
  final case class Selection(repositoryIdentity: String, localBranch: String, trackingRemote: String,
    trackingBranch: String, remoteEndpointIdentity: String, syncMode: String)
  object Selection {
    given Encoder[Selection] = deriveEncoder
    given Decoder[Selection] = deriveDecoder
  }
  final case class Intent(input: ManagedDevelopment.Input, participant: String, sourceHead: String,
    sourceTree: String, child: String, selection: Selection)
  object Intent {
    given Encoder[Intent] = deriveEncoder
    given Decoder[Intent] = deriveDecoder
  }
  def valid(s: Selection): Boolean = s != null && Vector(s.repositoryIdentity, s.localBranch, s.trackingRemote,
    s.trackingBranch, s.remoteEndpointIdentity, s.syncMode).forall(v => v != null && v.nonEmpty && v.trim == v && !v.exists(_.isControl))
  def input(s: Selection, mainRoute: Option[String] = None): V.RepositorySyncStartInput =
    V.RepositorySyncStartInput(s.repositoryIdentity, s.localBranch, s.trackingRemote, s.trackingBranch,
      s.remoteEndpointIdentity, s.syncMode, mainIntegration = mainRoute, uncommittedPolicy = None)

  def ensureC(core: ActionCall.Core, policy: ManagedDevelopmentRuntime.Policy, request: ManagedDevelopment.Input,
    publication: ManagedDevelopment.Publication, prior: Option[Synchronization] = None,
    phase: Option[ProviderPhaseDelivery.Accepted] = None, mainRoute: Option[String] = None): Consequence[Synchronization] = {
    // An actual earlier Phase hook always wins; no duplicate lifecycle/attempt.
    if (prior.exists(_.childSessionId.nonEmpty)) Consequence.success(prior.get)
    else request.synchronization match {
      case None => Consequence.success(prior.getOrElse(Synchronization("not-selected", "No delivery synchronization was selected")))
      case Some(selected) => for {
        _ <- if (valid(selected) && selected.repositoryIdentity == publication.edge.provider.repository)
          Consequence.unit else ManagedDevelopment.invalid("synchronization target differs from the published provider")
        _ <- if (phase.forall(_.declaredSynchronization.contains("after-scoped-publication"))) Consequence.unit
          else ManagedDevelopment.invalid("provider Phase did not originally declare post-scoped-publication synchronization; existing sync choice cannot be rewritten")
        intentStore <- storageC(core, policy, publication, "delivery-sync-intent")
        resultStore <- storageC(core, policy, publication, "delivery-sync-result")
        saved <- loadC(resultStore)
        retained <- saved match {
          case Some(value) => for {
            _ <- if (value.hcursor.get[ManagedDevelopment.Input]("input").contains(request)) Consequence.unit
              else ManagedDevelopment.invalid("saved synchronization result belongs to another exact delivery/selection")
            sync <- value.hcursor.get[Synchronization]("synchronization").fold(e => ManagedDevelopment.invalid(e.message), Consequence.success)
          } yield sync
          case None => for {
            intent <- loadC(intentStore)
            result <- intent match {
              case Some(raw) => for {
                actual <- raw.as[Intent].fold(e => ManagedDevelopment.invalid(e.message), Consequence.success)
                _ <- if (actual.input == request && actual.selection == selected && actual.sourceHead == source(publication, "head") &&
                  actual.sourceTree == source(publication, "tree")) Consequence.unit
                  else ManagedDevelopment.invalid("retained synchronization intent belongs to another delivery")
              } yield Synchronization("unknown", "Retained synchronization intent is not replayed; observe the child through its existing owner", Some(actual.child), Some("advanceRepositorySync"))
              case None => for {
                result <- (for {
                plan <- ManagedDevelopmentRuntime.executionPlanC(core)
                route = mainRoute.orElse(core.executionContext.resolvedParameters.get("sm-workflow.repository-sync.main-integration").flatMap(p => p.value match {
                  case org.goldenport.configuration.ConfigurationValue.StringValue(v) => Some(v)
                  case _ => None
                }))
                member <- ManagedDevelopmentRepositorySync.selectC(Some(policy), selected.repositoryIdentity, plan, phase.map(_.root), route)
                  .flatMap(_.map(Consequence.success).getOrElse(ManagedDevelopment.invalid("no declared managed dependency synchronization edge")))
                _ <- if (member.workspace == publication.edge.provider.workspace && member.localBranch == selected.localBranch &&
                  member.remote == selected.trackingRemote && member.remoteBranch == selected.trackingBranch && member.endpoint == selected.remoteEndpointIdentity)
                  Consequence.unit else ManagedDevelopment.invalid("synchronization selection crosses its exact registered provider/remote/branch")
                observed <- RepositorySyncManagedGitObservation.observeInWorkspaceC(core, publication.edge.provider.workspace)
                safe = observed.head == source(publication, "head") && observed.tree == source(publication, "tree") &&
                  observed.branch == selected.localBranch && observed.dirty.isEmpty
                available = core.component.get.core.protocol.services.services.exists(_.name == "RepositorySyncService")
                result <- if (!safe || !available) Consequence.success(Synchronization("deferred",
                  if (!safe) "Current provider source differs from the accepted published source; no synchronization was attempted"
                  else "Existing RepositorySync owner is unavailable; no fallback or new effect grant"))
                else for {
                  child <- Consequence.success(core.executionContext.idGeneration.opaqueId("sm-workflow-session"))
                  who = org.goldenport.cncf.security.SecuritySubject.from(core.executionContext.security).subjectId
                  intent = Intent(request, who, observed.head, observed.tree, child, selected)
                  _ <- intentStore.createC(Record.data("managedDevelopment" -> intent.asJson.noSpaces))
                  // The existing public owner's ordinary admission supplies any
                  // actual execution/push permission, never this locator.
                  attempt <- runC(core, selected, child, route).recoverWith(error => Consequence.success(Synchronization("unknown",
                    "Reserved existing-owner Start/Advance did not establish completion: " + error.toString, Some(child), Some("advanceRepositorySync"))))
                } yield attempt
                } yield result).recoverWith(error => Consequence.success(Synchronization("deferred",
                  "No synchronization Start was admitted by the source/registered-owner selection: " + error.toString)))
                _ <- resultStore.createC(Record.data("managedDevelopment" -> Json.obj("input" -> request.asJson, "synchronization" -> result.asJson).noSpaces))
              } yield result
            }
          } yield result
        }
      } yield retained
    }
  }
  def readC(core: ActionCall.Core, policy: ManagedDevelopmentRuntime.Policy, publication: ManagedDevelopment.Publication):
    Consequence[Option[Synchronization]] = for {
    store <- storageC(core, policy, publication, "delivery-sync-result")
    value <- loadC(store)
    result <- value.fold(Consequence.success(Option.empty[Synchronization]))(raw => raw.hcursor.get[Synchronization]("synchronization")
      .fold(e => ManagedDevelopment.invalid(e.message), sync => Consequence.success(Some(sync))))
  } yield result

  private def runC(core: ActionCall.Core, selected: Selection, child: String,
    mainRoute: Option[String]): Consequence[Synchronization] = for {
    _ <- GoalPhaseNativeRepositoryClosure.invokeC[V.RepositorySyncStartResult](core, "startRepositorySync",
      List(Property("input", input(selected, mainRoute), None), Property("sessionId", child, None)))
    actual <- GoalPhaseNativeRepositoryClosure.invokeC[V.RepositorySyncAdvanceResult](core, "advanceRepositorySync",
      List(Property("sessionId", child, None)))
    status = if (actual.kind == "Terminal" && actual.outcome.contains("converged")) "succeeded"
      else if (actual.kind == "Human") "human" else if (actual.kind == "Request") "request"
      else if (actual.outcome.exists(_.contains("failed"))) "failed" else "unknown"
  } yield Synchronization(status, "Actual single RepositorySync continuation returned " + actual.kind +
    "; local publication remains usable and further work belongs to that child", Some(child),
    Option.unless(status == "succeeded")("advanceRepositorySync"))

  private def source(p: ManagedDevelopment.Publication, field: String): String = p.source.hcursor.get[String](field).toOption.getOrElse("")
  private def storageC(core: ActionCall.Core, policy: ManagedDevelopmentRuntime.Policy, p: ManagedDevelopment.Publication,
    kind: String): Consequence[ComponentRecordStorage] = EntityCollectionId.parse(policy.collection).flatMap(id =>
      ComponentRecordStorage.bindForCallC(core, policy.storeName, DataStore.CollectionId.EntityStore(id), DataStore.StringEntryId(
        ManagedDevelopment.key(policy.definition, p.edge, kind, p.identity))))
  private def loadC(storage: ComponentRecordStorage): Consequence[Option[Json]] = storage.loadC().flatMap {
    case None => Consequence.success(None)
    case Some(row) => row.getString("managedDevelopment").map(text => io.circe.parser.parse(text)
      .fold(e => ManagedDevelopment.invalid(e.message), value => Consequence.success(Some(value))))
      .getOrElse(ManagedDevelopment.invalid("delivery synchronization record is malformed"))
  }
}
