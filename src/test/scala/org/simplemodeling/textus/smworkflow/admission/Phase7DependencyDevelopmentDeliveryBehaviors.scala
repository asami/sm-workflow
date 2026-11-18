package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import StandaloneFixExecution as S
import S.given
import AssignedDevelopmentChatQueue as Q
import DependencyDevelopmentDelivery as D
import GoalPhaseOrderedProgression as O
import GoalPhaseDevelopmentPermissions as P
import SplitPhaseExplicitPartitionFixture.take

/** Executable business specifications; fixture observations are not delivery
  * acceptance, native Human consent or an external-chat dispatch receipt.
  * @since   Nov. 12, 2026
  * @version Nov. 18, 2026
  */
private[admission] trait Phase7DependencyDevelopmentDeliveryBehaviors {
  self: AnyWordSpec with Matchers with GivenWhenThen =>
  private val input = take(DelegateTask.inputC(DelegateTaskRequesterRoutingFixture.input("first")))
  private def destination(name: String) = input.copy(requestIdentity = name,
    delegation = input.delegation.map(_.copy(role = "destination", destinationIdentity = "development-chat")))
  private val edge = ManagedDevelopment.Edge("dependency", GoalPhaseDependencyScope.Component("org.example.Provider", "provider", "provider-code"),
    GoalPhaseDependencyScope.Component("org.example.Consumer", "consumer", "consumer-code"), "artifact-owner", true, None,
    ManagedDevelopment.Coordinate("org.example", "provider_3", "1-SNAPSHOT"))
  private val definition = ManagedDevelopment.Definition("delivery", "artifact-owner", "artifacts/ivy", "staging", Vector(edge))
  private def policy(mode: String, chat: Option[String] = None) = ManagedDevelopmentRuntime.Policy("workflow", "smworkflow.delivery",
    definition, dependencyWorkEdge = Some(edge.identity), delivery = Some(Vector(D.Binding(edge.identity, mode, chat))))
  private val plan = NativeHumanEvidenceFixture.plan
  private def proposal(selected: O.Plan, operations: Vector[String] = Vector("edit-source", "validate", "independent-review", "publish-local")) =
    GoalPhaseDependencyScope.Proposal("necessary-dependency", "1", "IMPLEMENTATION", edge.consumer, edge.provider,
      ContextReference("selected-usage", "1"), "Necessary provider change for the selected Phase purpose", Vector("src/Provider.scala"),
      Vector("Preserve existing API and satisfy selected consumer"), Vector(ContextReference("consumer-work", "1")), Vector.empty,
      Some(GoalPhaseDependencyScope.AuthorizationBoundary(GoalPhaseDependencyScope.authorizationSchema,
        ContextReference(selected.identity, selected.revision), operations, Vector.empty)))

  "Assigned development chat ownership" should {
    "keep transport distinct from FIFO admission and retain waiting work until explicit hold and recorded requeue" in {
      Given("two admitted requests for one configured owner and another independent owner")
      val first = destination("first")
      val second = destination("second")
      val empty = Q.State("development-chat", Vector.empty, Vector.empty, Vector.empty)
      val one = take(Q.enqueueC(empty, "session-first", first, "native-first", 3))
      val two = take(Q.enqueueC(one, "session-second", second, "native-second", 3))
      When("the first request claims its queue place and waits for a decision")
      val active = take(Q.transitionC(two, "session-first", "claim", "native-first", 4))
      Q.transitionC(active, "session-second", "claim", "native-second", 4).isSuccess shouldBe false
      take(Q.enqueueC(active, "session-first", first, "native-reconnect", 9)) shouldBe active
      Q.enqueueC(active, "session-first", first.copy(objective = "different"), "native-reconnect", 9).isSuccess shouldBe false
      Q.enqueueC(active, "other-session", first, "native-redelivery", 9).isSuccess shouldBe false
      val other = destination("other").copy(delegation = first.delegation.map(_.copy(destinationIdentity = "other-chat")))
      val independent = take(Q.enqueueC(Q.State("other-chat", Vector.empty, Vector.empty, Vector.empty), "other-session", other, "other-native", 3))
      take(Q.transitionC(independent, "other-session", "claim", "other-native", 4)).entries.head.status shouldBe "active"
      Then("only an explicit hold releases the first place and resume appends it behind the second")
      val held = take(Q.transitionC(active, "session-first", "hold", "original-control-grant", 5))
      val resumed = take(Q.transitionC(held, "session-first", "resume", "original-control-grant", 6))
      resumed.order shouldBe Vector("session-second", "session-first")
      val secondActive = take(Q.transitionC(resumed, "session-second", "claim", "native-second", 7))
      val delivered = take(Q.transitionC(secondActive, "session-second", "deliver", "accepted-publication", 8))
      take(Q.transitionC(delivered, "session-first", "claim", "native-first", 10)).entries.count(_.status == "active") shouldBe 1
      delivered.events.map(_.action) shouldBe Vector("enqueue", "enqueue", "claim", "hold", "resume", "claim", "deliver")
    }

    "retain each queue command independently of its reusable grant and ignore replay after later direction" in {
      Given("two queued requests and an original authorization grant usable by distinct control commands")
      val first = destination("first")
      val one = take(Q.enqueueC(Q.State("development-chat", Vector.empty, Vector.empty, Vector.empty), "one", first, "native", 3))
      val two = take(Q.enqueueC(one, "two", destination("second"), "native-second", 3))
      val active = take(Q.transitionC(two, "one", "claim", "native", 4))
      def command(event: String, identity: String) = DelegateTask.Observation(event, ContextReference("original-grant", "1"),
        Some("one"), None, None, "SPECIFICATION-OWNED explicit queue direction", Some(identity))
      val hold = command("queue-hold", "hold-1")
      val resume = command("queue-resume", "resume-1")
      When("hold H1 is followed by resume R1 and then H1 is redelivered")
      val held = take(Q.controlC(active, "one", hold, 5))
      val resumed = take(Q.controlC(held, "one", resume, 5))
      Then("both original outcomes remain retained and neither replay changes current order or history")
      resumed.order shouldBe Vector("two", "one")
      take(Q.controlC(resumed, "one", hold, 5)) shouldBe resumed
      take(Q.controlC(resumed, "one", resume, 5)) shouldBe resumed
      resumed.controls.get.map(c => c.identity -> c.status) shouldBe Vector("hold-1" -> "held", "resume-1" -> "queued")
      val newHold = take(Q.controlC(resumed, "one", hold.copy(commandIdentity = Some("hold-2")), 5))
      newHold.entries.head.status shouldBe "held"
      newHold.controls.get.size shouldBe 3
      take(Q.controlC(newHold, "one", resume, 5)) shouldBe newHold
      Q.controlC(resumed, "one", hold.copy(event = "queue-cancel"), 5).isSuccess shouldBe false
      Q.controlC(resumed, "one", hold.copy(sourceReference = ContextReference("changed-grant", "1")), 5).isSuccess shouldBe false
      Q.controlC(resumed, "two", hold.copy(destinationSessionId = Some("two")), 5).isSuccess shouldBe false
      Q.controlC(resumed, "one", hold.copy(commandIdentity = None), 5).isSuccess shouldBe false
      // Historical queues/observations remain readable without made-up commands.
      active.asJson.mapObject(_.remove("controls")).as[Q.State].toOption.get.controls shouldBe None
      hold.asJson.mapObject(_.remove("commandIdentity")).as[DelegateTask.Observation].toOption.get.commandIdentity shouldBe None
    }

    "place real provider Starts and bounded requests in the same FIFO without a bounded Phase surrogate" in {
      Given("a native provider Start captured under the assigned-chat policy")
      val start = GoalPhaseNativeRun.Payload("participant", "/fixture/provider", "main", plan, NativeHumanEvidenceFixture.snapshot,
        dependencyDelivery = Some(D.Binding(edge.identity, D.assignedChat, Some("development-chat"))))
      val empty = Q.State("development-chat", Vector.empty, Vector.empty, Vector.empty)
      val phaseFirst = take(Q.enqueuePhaseC(empty, "phase-one", "3", start))
      val mixed = take(Q.enqueueC(phaseFirst, "bounded", destination("bounded"), "native-bounded", 3))
      val active = take(Q.transitionC(mixed, "phase-one", "claim", "phase-one", 3))
      Q.transitionC(active, "bounded", "claim", "bounded", 4).isSuccess shouldBe false
      val twoPhases = take(Q.enqueuePhaseC(active, "phase-two", "3", start))
      Q.transitionC(twoPhases, "phase-two", "claim", "phase-two", 3).isSuccess shouldBe false
      When("the real provider's accepted publication releases its place")
      val delivered = take(Q.transitionC(twoPhases, "phase-one", "deliver", "actual-publication", 40))
      Then("the bounded predecessor goes next and the second Phase stays behind it")
      delivered.order shouldBe Vector("bounded", "phase-two")
      take(Q.transitionC(delivered, "bounded", "claim", "bounded", 4)).entries.count(_.status == "active") shouldBe 1
      take(Q.enqueuePhaseC(twoPhases, "phase-one", "3", start)) shouldBe twoPhases
      Q.enqueuePhaseC(twoPhases, "phase-one", "4", start).isSuccess shouldBe false
      Q.enqueuePhaseC(twoPhases, "phase-one", "3", start.copy(base = start.base.copy(head = "different"))).isSuccess shouldBe false
    }

    "retain cancellation and refuse prerequisites queued behind their own unfinished parent" in {
      val first = destination("first")
      val queued = take(Q.enqueueC(Q.State("development-chat", Vector.empty, Vector.empty, Vector.empty), "one", first, "native", 3))
      val active = take(Q.transitionC(queued, "one", "claim", "native", 4))
      val prerequisite = destination("prerequisite").copy(delegation = first.delegation.map(_.copy(prerequisiteFor = Some("first"))))
      Q.enqueueC(active, "prerequisite", prerequisite, "native-prerequisite", 3).isSuccess shouldBe false
      val cancelled = take(Q.transitionC(active, "one", "cancel", "original-cancel-grant", 5))
      cancelled.entries.head.status shouldBe "cancelled"
      cancelled.order shouldBe empty
      take(Q.enqueueC(cancelled, "one", first, "reconnect", 9)) shouldBe cancelled
      Q.transitionC(cancelled, "one", "resume", "grant", 10).isSuccess shouldBe false
    }

    "project Fixed as an affected-consumer wait while refusing execution and mismatched assigned owners" in {
      val requester = take(DelegateTask.startRequesterC(input, "participant"))
      val fixed = policy(D.fixed)
      val waiting = D.fixedState(Some(fixed), requester)
      waiting.stage shouldBe "DependencyChangeRequired"
      waiting.input shouldBe input
      waiting.automaticCount shouldBe requester.automaticCount
      waiting.receipts shouldBe requester.receipts
      waiting.delivery shouldBe requester.delivery
      D.admitC(fixed, destination("first")).isSuccess shouldBe false
      D.admitC(policy(D.assignedChat, Some("development-chat")), destination("first")).isSuccess shouldBe true
      D.admitC(policy(D.assignedChat, Some("foreign-chat")), destination("first")).isSuccess shouldBe false
      D.admitC(policy(D.subtree), destination("first")).isSuccess shouldBe true
      D.validateC(definition, Some(Vector(D.Binding(edge.identity, D.fixed, Some("chat"))))).isSuccess shouldBe false
    }
  }

  "Independent Phase development permissions" should {
    "default historical omissions to false and keep all four combinations independent for managed and external producers" in {
      val historical = plan.asJson.mapObject(_.remove("allowDependencyRepair").remove("allowSharedCliUpdate"))
      val decoded = historical.as[O.Plan].toOption.get
      decoded.allowDependencyRepair shouldBe None
      decoded.allowSharedCliUpdate shouldBe None
      for (repair <- Vector(false, true); cli <- Vector(false, true)) {
        val selected = plan.copy(allowDependencyRepair = Some(repair), allowSharedCliUpdate = Some(cli))
        val jar = proposal(selected)
        val install = proposal(selected, Vector("edit-source", "install-runtime"))
        def assess(p: GoalPhaseDependencyScope.Proposal, managed: Boolean) = take(P.assessC(selected, p,
          P.capture(selected, p, Option.when(managed)(policy(D.subtree))))).status
        assess(jar, managed = true) shouldBe "admitted"
        assess(jar, managed = false) shouldBe (if (repair) "admitted" else "human-dependency-repair")
        assess(install, managed = true) shouldBe (if (cli) "admitted" else "human-shared-cli-update")
        assess(install, managed = false) shouldBe (if (!cli) "human-shared-cli-update" else if (!repair) "human-dependency-repair" else "admitted")
        assess(proposal(selected, Vector("install-runtime")), managed = false) shouldBe
          (if (cli) "admitted" else "human-shared-cli-update")
        selected.asJson.as[O.Plan] shouldBe Right(selected)
        val public = NativeWorkflowOperationOutput.goalWork(NativeHumanEvidenceFixture.work.copy(selectedPlan = selected))
        public.selectedPlan.allowDependencyRepair shouldBe Some(repair)
        public.selectedPlan.allowSharedCliUpdate shouldBe Some(cli)
      }
      historical.mapObject(_.add("allowDependencyRepair", Json.Null)).as[O.Plan].isLeft shouldBe true
      historical.mapObject(_.add("allowSharedCliUpdate", Json.fromString("true"))).as[O.Plan].isLeft shouldBe true
    }

    "preserve explicit Fixed and purpose restrictions despite enabled policy and refuse remote effects" in {
      val selected = plan.copy(allowDependencyRepair = Some(true), allowSharedCliUpdate = Some(true))
      val jar = proposal(selected)
      take(P.assessC(selected, jar, P.capture(selected, jar, Some(policy(D.fixed))))).status shouldBe "dependency-change-required"
      val remote = proposal(selected, Vector("edit-source", "publish-remote", "push"))
      take(P.assessC(selected, remote, P.capture(selected, remote, None))).status shouldBe "decision-needed"
      val crossed = jar.copy(authorizationBoundary = jar.authorizationBoundary.map(_.copy(purpose = ContextReference("unrelated-purpose", "1"))))
      take(P.assessC(selected, crossed, P.capture(selected, crossed, None))).status shouldBe "decision-needed"
      val excluded = jar.copy(authorizationBoundary = jar.authorizationBoundary.map(_.copy(excludedPaths = jar.paths)))
      GoalPhaseDependencyScope.validateProposalC(excluded).isSuccess shouldBe false
      val managed = P.capture(selected, jar, Some(policy(D.subtree)))
      P.assessC(plan, proposal(plan), managed).isSuccess shouldBe false
    }
  }
  "Existing provider Phase delivery composition" should {
    "retain typed original Phase locators and independent sync selection without manufacturing a bounded result" in {
      val phase = ProviderPhaseDelivery.Locator("actual-provider-phase", "3", "phase-publication", "provider-selection")
      val sync = DevelopmentDeliverySynchronization.Selection("provider", "work/provider", "origin", "work/provider", "registered-provider", "FULL_STATE")
      val requested = input.copy(delegation = input.delegation.map(_.copy(providerPhase = Some(phase), synchronization = Some(sync),
        permittedOperations = input.delegation.get.permittedOperations :+ "repository-sync")))
      DelegateTask.valid(requested.delegation.get) shouldBe true
      requested.asJson.as[S.Input] shouldBe Right(requested)
      requested.asJson.hcursor.downField("delegation").downField("providerPhase").get[String]("sessionId") shouldBe Right(phase.sessionId)
      ProviderPhaseDelivery.valid(phase.copy(startRevision = "0")) shouldBe false
      val original = take(DelegateTask.startRequesterC(requested, "participant"))
      original.stage shouldBe "DispatchReady"
      original.receipts shouldBe empty
      original.commit shouldBe None
      original.delivery.flatMap(_.returned) shouldBe None
      val crossed = DelegateTask.Observation("dispatch-observed", ContextReference("actual-transport", "1"),
        Some("another-phase"), None, None, "SPECIFICATION-OWNED actual transport projection")
      DelegateTask.observeC(original, crossed).isSuccess shouldBe false
      val observed = take(DelegateTask.observeC(original, crossed.copy(destinationSessionId = Some(phase.sessionId))))
      observed.stage shouldBe "AwaitResult"
      observed.delivery.flatMap(_.phaseDelivery) shouldBe None
      val prior = GoalPhasePostCompletionSync.Synchronization("unknown", "SPECIFICATION-OWNED already reserved native child", Some("actual-child"), Some("advanceRepositorySync"))
      // Existing child wins before any storage, source, transport or execution
      // access. This is a specification value, not actual Phase acceptance.
      take(DevelopmentDeliverySynchronization.ensureC(null, null, null, null, Some(prior))) shouldBe prior
    }

    "keep post-publication synchronization an explicit original Phase choice across ordinary permission revisions" in {
      val selected = plan.copy(deliverySynchronization = Some("after-scoped-publication"))
      selected.asJson.as[O.Plan] shouldBe Right(selected)
      val state = take(O.startC(selected, NativeHumanEvidenceFixture.snapshot))
      val work = state.next.asInstanceOf[O.Semantic]
      val paused = take(state.acceptC(O.SemanticCompleted(work.key, "SPECIFICATION-OWNED semantic completion")))
      paused.plan.deliverySynchronization shouldBe Some("after-scoped-publication")
      val invalid = selected.asJson.mapObject(_.add("deliverySynchronization", Json.fromString("automatic-retry")))
      invalid.as[O.Plan].isLeft shouldBe true
      val legacy = plan.asJson
      legacy.hcursor.downField("deliverySynchronization").focus shouldBe None
    }
  }

}
