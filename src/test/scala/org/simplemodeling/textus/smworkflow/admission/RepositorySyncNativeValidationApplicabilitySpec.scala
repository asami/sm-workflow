package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncStartCommand, RepositorySyncStartInput}
import RepositorySyncNativeMergeValidation as V
import RepositorySyncNativeMergeReview as R
import RepositorySyncNativeMergeReviewContinuation as W
import RepositorySyncNativeProgressStore as N
import RepositorySyncManagedFetch as Fetch
import RepositorySyncManagedGitObservation as G
import SplitPhaseExplicitPartitionFixture.take

/** Actual selected facts, not policy metadata or re-encoded body certificates.
  * @since   Oct.  8, 2026
  * @version Oct.  8, 2026
  */
final class RepositorySyncNativeValidationApplicabilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val policy = V.Policy("original-policy", 1, "required", "Original selected rationale", Vector(V.Check("focused", 1)))
  private val receipts = Vector("index-local", "index-remote", "focused").map(id => V.Receipt(id, "exited", Some(0), "", ""))
  private val state = V.State(policy.mode, policy.reason, policy.checks, "3", receipts)
  private val before = G.Observed("main", "a" * 40, "b" * 40, Vector.empty, Some("c" * 40), Some(G.Counts(1, 1)))
  private val input = R.Request("selected-instance", "7", before.head, "c" * 40, "d" * 40,
    Vector("one.scala"), "Original local diff", "Original remote diff", state.mode, state.reason,
    state.selected.map(_.identity), state.receipts)
  private def nested(value: Json) = value.hcursor.downField("payload").focus.get
  private def replace(value: Json, body: Json) = value.mapObject(_.add("payload", body))

  "Native validation facts" should {
    "write selected facts without policy copies and read their actual legacy meaning without metadata proof" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R1; Example: E1; an actual selected validation payload")
      val saved = N.Payload("MergeValidated", "/selected/repository", before,
        indexTree = Some("d" * 40), after = Some(before), mergeHead = Some("c" * 40), validation = Some(state))
      val wire = N.codec.encode(saved)
      val facts = nested(wire).hcursor.downField("validation").focus.get
      When("new output is produced and an old v1 policy stores optional or unusable metadata")
      facts.asObject.get.keys.toSet should not contain "policy"
      state.productElementNames.toSet should not contain "policy"
      Vector(None, Some(Json.Null), Some(Json.True), Some(Json.fromString("not-a-certificate"))).foreach { marker =>
        val actual = Json.obj("mode" -> Json.fromString(state.mode), "reason" -> Json.fromString(state.reason),
          "checks" -> facts.hcursor.downField("selected").focus.get)
        val oldPolicy = marker.fold(actual)(v => actual.mapObject(_.add("identity", v).add("revision", v)))
        val oldState = Json.obj("policy" -> oldPolicy, "sourceRevision" -> Json.fromString(state.sourceRevision),
          "receipts" -> facts.hcursor.downField("receipts").focus.get)
        val old = replace(wire, nested(wire).mapObject(_.add("validation", oldState)))
          .mapObject(_.add("schemaVersion", Json.fromString("sm-workflow.repository-sync-native-progress.v1")))
        N.codec.decode(old) shouldBe Right(saved)
      }
      Then("foreign executable fields and missing/malformed actual check facts still reject")
      V.stateCodec.decode(facts.mapObject(_.add("approve", Json.True))).isLeft shouldBe true
      V.stateCodec.decode(facts.mapObject(_.remove("selected"))).isLeft shouldBe true
      V.stateCodec.decode(facts.mapObject(_.add("selected", Json.arr(Json.obj("identity" -> Json.fromString("focused"),
        "ordinal" -> Json.fromInt(1), "command" -> Json.fromString("git")))))).isLeft shouldBe true
      N.codec.decode(replace(wire, nested(wire).mapObject(_.add("validation", facts.mapObject(_.add("policy", Json.Null)))))).isLeft shouldBe true
    }
    "resolve selected executable bindings without unchanged policy identity revision or complete declaration equality" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R1; Example: E2; original selected checks and current bindings")
      When("metadata changes or an extra unselected binding is installed")
      V.applies(state, policy.copy(identity = "different-metadata", revision = 8, reason = "Different projection",
        checks = policy.checks :+ V.Check("unselected", 2))) shouldBe true
      Then("a missing or incompatible selected binding or an actually different skip/required mode is refused")
      V.applies(state, policy.copy(checks = Vector.empty)) shouldBe false
      V.applies(state, policy.copy(checks = Vector(V.Check("focused", 2)))) shouldBe false
      V.applies(state, policy.copy(mode = "explicit-no-product-tests", checks = Vector.empty)) shouldBe false
    }
    "admit actual source and result coverage without a diagnostic observation witness" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R1; Example: E9; actual adjacent native frontiers")
      val initial = InstanceRecord(InstanceIdentity("validation-frontier"), take(RepositorySyncDefinitionBinding.bindC), initialRevision,
        Lifecycle.NotStarted, None, CorrelationReference("owned-correlation"), CausationReference("owned-start"), None, None, None, Vector.empty)
      def next(record: InstanceRecord, stage: String) = take(record.appendC(record.revision,
        HistoryEntry(HistorySequence(record.history.size.toLong + 1), record.revision.next, Lifecycle.Active,
          Some(ProgressionReference(stage)), record.correlation, CausationReference(stage), None, None, None)))
      val sourceRecord = next(next(next(initial, "sync.Fetched"), "sync.Integrated"), "sync.PendingMergePrepared")
      val resultRecord = next(sourceRecord, "sync.MergeValidated")
      val source = N.Stage(sourceRecord, N.Payload("PendingMergePrepared", "/selected/repository", before,
        indexTree = Some("d" * 40), after = Some(before), mergeHead = Some("c" * 40)))
      val selected = state.copy(sourceRevision = sourceRecord.revision.value.toString)
      val result = N.Stage(resultRecord, source.payload.copy(stage = "MergeValidated", validation = Some(selected),
        after = Some(before.copy(counts = Some(G.Counts(9, 9)), mergeBase = Some("e" * 40)))))
      When("selected execution facts cover the actual source and current bindings despite changed observation diagnostics")
      take(V.completedC(result, source, policy.copy(identity = "other-metadata", revision = 8), merge = true)) shouldBe ()
      Then("failed or missing actual results, foreign source and a result for another assigned check cannot become acceptance")
      Vector(selected.copy(receipts = selected.receipts.dropRight(1)),
        selected.copy(receipts = selected.receipts.updated(2, selected.receipts(2).copy(exitCode = Some(1)))),
        selected.copy(sourceRevision = "99")).foreach { contradicted =>
        V.completedC(result.copy(payload = result.payload.copy(validation = Some(contradicted))), source, policy, merge = true) shouldBe a[Consequence.Failure[?]]
      }
      val crossed = selected.copy(selected = Vector(V.Check("other-check", 2)), receipts = selected.receipts.updated(2,
        selected.receipts(2).copy(identity = "other-check")))
      V.matchesC(result.copy(payload = result.payload.copy(validation = Some(crossed))), source,
        policy.copy(checks = policy.checks :+ V.Check("other-check", 2)), 3, merge = true,
        selected = Some(policy.checks)) shouldBe a[Consequence.Failure[?]]
    }
  }

  "Native merge review facts" should {
    "discard old policy witnesses and decode declared nested fields without re-encoding a body certificate" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R2; Example: E5; original semantic review meaning")
      val wire = R.inputCodec.encode(input)
      val fields = Set("validationPolicyIdentity", "validationPolicyRevision", "reviewPolicyIdentity", "reviewPolicyRevision")
      When("new output is written or an old v1 request contains absent or unusable policy markers")
      nested(wire).asObject.get.keys.toSet.intersect(fields) shouldBe empty
      Vector(None, Some(Json.Null), Some(Json.True), Some(Json.fromString("not-a-certificate"))).foreach { marker =>
        val old = marker.fold(nested(wire))(v => fields.foldLeft(nested(wire))((body, key) => body.mapObject(_.add(key, v))))
        R.inputCodec.decode(replace(wire, old).mapObject(_.add("schemaVersion",
          Json.fromString("sm-workflow.repository-native-merge-review-request.v1")))) shouldBe Right(input)
      }
      val changed = R.Policy("different-review-metadata", 8)
      take(R.inputC(input, changed)) shouldBe ()
      Then("actual coverage/bounds, unknown nested authority and incompatible typed input still reject")
      R.inputCodec.decode(replace(wire, nested(wire).mapObject(_.add("approve", Json.True)))).isLeft shouldBe true
      R.inputCodec.decode(replace(wire, nested(wire).mapObject(_.remove("indexTree")))).isLeft shouldBe true
      R.inputC(input.copy(changedPaths = Vector("../outside")), changed) shouldBe a[Consequence.Failure[?]]
      val result = R.Result("accept", "Original reviewed meaning", Vector.empty)
      val resultWire = R.resultCodec.encode(result)
      R.resultCodec.decode(resultWire) shouldBe Right(result)
      R.resultCodec.decode(replace(resultWire, nested(resultWire).mapObject(_.add("command", Json.True)))).isLeft shouldBe true
    }
    "consume original issued meaning and presentation while resolving actual independent-review routing" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R2; Example: E6; a selected actual Required contract")
      val required = take(RepositorySyncDefinitionBinding.nativeReviewOperationC)
      val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("RepositorySyncWorkflow"),
        WorkflowDefinitionRevision("repository-sync-native-convergence-v10"), InstanceIdentity(input.instanceIdentity))
      val request = ContinuationRequest(StateMachineRunIdentity(input.instanceIdentity), org.goldenport.cncf.workflow.ContinuationIdentity("original-review"),
        StateMachineRevision("8"), required.identity, required.operation, Some(TypedValue(R.inputCodec.typeIdentity, input)), required.resultType,
        ContextBundle("Original review context", Vector.empty, Vector.empty, ContextSnapshot(handle.workflowRevision.value)),
        required.metadata.completionContract, required.metadata.evidenceContract, W.completionOperation)
      val order = WorkflowContinuation.WorkOrder(request, ExecutionRequirement(Vector(CapabilityRequirement(required.identity.capability)),
        RiskLevel("SOURCE_MERGE"), ReasoningLevel.Standard, true), MinimalPresentation("Original title", "Original situation"))
      val saved = WorkflowInteraction[R.Request, Nothing](handle, order)
      val projected = saved.copy(current = order.copy(requirement = order.requirement, presentation = MinimalPresentation("New title", "New situation"),
        request = request.copy(input = Some(TypedValue(R.inputCodec.typeIdentity, input.copy(localDiff = "New diagnostic projection"))),
          context = request.context.copy(summary = "New summary"))))
      When("current execution references and contracts select the original issue")
      take(W.selectIssuedC(projected, saved)) shouldBe saved
      Then("foreign continuation/route/revision or removal of the independent-review requirement still refuses")
      Vector(request.copy(continuationId = org.goldenport.cncf.workflow.ContinuationIdentity("foreign-slot")), request.copy(expectedRevision = StateMachineRevision("99")),
        request.copy(completionOperation = StateMachineOperationIdentity("OtherService", "complete"))).foreach { crossed =>
        W.selectIssuedC(projected, saved.copy(current = order.copy(request = crossed))) shouldBe a[Consequence.Failure[?]]
      }
      W.selectIssuedC(projected, saved.copy(current = order.copy(requirement = order.requirement.copy(reviewRequired = false)))) shouldBe a[Consequence.Failure[?]]
    }
  }

  "Native Start and downstream records" should {
    "retain one original typed Start and discard obsolete command copies from legacy fetch rows" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R3; Example: E10; original Start and actual fetch facts")
      val command = RepositorySyncStartCommand(RepositorySyncStartInput("repository", "main", "origin", "main", "endpoint", "FULL_STATE", "original-policy"),
        "original-selection", "participant", "original-invocation")
      val started = Fetch.Payload("Started", Some(command), "/selected/repository", None, None, None, None)
      val startWire = Fetch.codec(true).encode(started)
      val fetched = Fetch.Payload("Fetched", None, "/selected/repository", Some(before), Some("c" * 40),
        Some(before.copy(mergeBase = Some("a" * 40))), Some("executed"))
      val fetchWire = Fetch.codec(false).encode(fetched)
      When("new fetch output omits a copied command and v1 rows contain absent or unusable old witnesses")
      nested(fetchWire).asObject.get.keys.toSet should not contain "originalCommand"
      Fetch.codec(true).decode(startWire.mapObject(_.add("schemaVersion", Json.fromString("sm-workflow.repository-sync-managed-fetch.v1")))) shouldBe Right(started)
      Vector(None, Some(Json.Null), Some(Json.True), Some(Json.fromString("obsolete-copy"))).foreach { marker =>
        val old = marker.fold(nested(fetchWire))(v => nested(fetchWire).mapObject(_.add("originalCommand", v)))
        Fetch.codec(false).decode(replace(fetchWire, old).mapObject(_.add("schemaVersion",
          Json.fromString("sm-workflow.repository-sync-managed-fetch.v1")))) shouldBe Right(fetched)
      }
      Then("Start still needs its real command and foreign invocation/target/type does not become original authority")
      Fetch.codec(true).decode(replace(startWire, nested(startWire).mapObject(_.remove("originalCommand")))).isLeft shouldBe true
      Fetch.codec(true).decode(replace(startWire, nested(startWire).mapObject(_.add("originalCommand",
        RepositorySyncStartServiceOperation.commandJson(command).mapObject(_.add("authority", Json.True)))))).isLeft shouldBe true
      Fetch.codec(false).decode(replace(fetchWire, nested(fetchWire).mapObject(_.add("originalCommand", Json.Null)))).isLeft shouldBe true
      Fetch.sameInvocation(command, command.copy(input = command.input.copy(repositoryPolicyVersion = "another-metadata"))) shouldBe true
      Vector(command.copy(participantIdentity = "other-participant"), command.copy(selectionIdentity = "other-selection"),
        command.copy(idempotencyKey = "other-invocation"), command.copy(input = command.input.copy(remoteEndpointIdentity = "other-endpoint")))
        .foreach(v => Fetch.sameInvocation(command, v) shouldBe false)
    }
    "omit copied commands from progress producers and consume actual v1 or v2 state without their obsolete witnesses" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R3; Example: E11; selected native progress facts")
      val saved = N.Payload("Preserving", "/selected/repository", before)
      val wire = N.codec.encode(saved)
      When("v3 is written or earlier formats contain no usable command copy")
      nested(wire).asObject.get.keys.toSet should not contain "originalCommand"
      saved.productElementNames.toSet should not contain "originalCommand"
      for (version <- Vector("v1", "v2"); marker <- Vector(None, Some(Json.Null), Some(Json.True), Some(Json.fromString("obsolete-copy")))) {
        val old = marker.fold(nested(wire))(v => nested(wire).mapObject(_.add("originalCommand", v)))
        N.codec.decode(replace(wire, old).mapObject(_.add("schemaVersion",
          Json.fromString("sm-workflow.repository-sync-native-progress." + version)))) shouldBe Right(saved)
      }
      Then("new-format undeclared fields and actual missing progress facts still reject")
      N.codec.decode(replace(wire, nested(wire).mapObject(_.add("originalCommand", Json.Null)))).isLeft shouldBe true
      N.codec.decode(replace(wire, nested(wire).mapObject(_.remove("before")))).isLeft shouldBe true
    }
  }
}
