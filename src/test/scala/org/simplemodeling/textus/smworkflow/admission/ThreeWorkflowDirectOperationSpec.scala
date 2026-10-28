package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import java.nio.file.{Files, Path}
import io.circe.Json
import org.goldenport.cncf.bootstrap.{BootstrapConfig, CncfHandle}
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentInstanceMetadata, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.{ComponentRecordStorage, ComponentRecordStorageDeclaration, DataStore}
import org.goldenport.cncf.event.*
import org.goldenport.cncf.security.IngressSecurityResolver
import org.goldenport.cncf.subsystem.*
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.{ContextReference, StateMachineOperationIdentity, WorkflowComponentStorage, WorkflowResultJsonV1, WorkflowWorkOrderJsonV1, WorkflowExecutionHarness}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Property, Request}
import org.goldenport.schema.Multiplicity
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.caller.NativeWorkflowCallerExecution
import org.simplemodeling.textus.smworkflow.preparation.{NativeHumanDecisionPreparation, NativeStartPreparation}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseTypedStartFixture as F

/** Direct public Operation calls on the production three-service composition.
  * An extra specification-only service prepares input and observes stored state;
  * it replaces no public Operation implementation, evidence adapter or Workflow.
  * An optional test observer runs within the actual owning result dispatch,
  * forwarding the production action and its afterCommit response unchanged.
  * Input/evidence rows are test data, not a production human interaction channel.
  * No launcher, CLI command, fabricated history or direct SQLite write is used.
  * @since   Oct.  7, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class ThreeWorkflowDirectOperationSpec extends AnyWordSpec with ThreeWorkflowDirectOperationFixture {
  // Trace: spec:native-host-start-preparation, example:E5, rules:R1,R2,R3,R4,R6, phase:6;
  // spec:native-issued-work-context, example:O-Split, rules:R1,R2,R4,R6, phase:6.
  // Keep registered names bounded; nested trace text remains in its own context.
  "Public Operations on the production three-workflow composition" should afterWord("normal Start and issued Split work") {
    "E5 Explicit Start consent provisioning" should afterWord(
      "in spec:native-host-start-preparation, examples:E5,E6, rules:R1,R2,R3,R4,R6,R7, phase:6") {
    "refuse missing consent with a passive template then create a native session after separate provisioning" in {
      Given("R7/E6: specification-owned selected allocation Source and passive template facts/requirement/presentation with no allocated execution owner and every normal row except Start consent")
      val root = Files.createTempDirectory("smwf-public-start-preparation-")
      withRuntime(root) { (owner, component, actor, prepare) =>
        def invokeC(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "SplitPhaseService", operation = operation, properties = properties), actor)
        prepare(call => prepareInputsWithFacts(call, F.facts, provisionConsent = false))
        When("the normal public Start consumes business input without a separately provisioned consent row")
        val absent = invokeC("startSplitPhase", startWire)
        Then("the actual native consent boundary refuses Start")
        absent.isSuccess shouldBe false
        Given("the specification separately creates the public producer's input through the normal managed Create grant")
        prepare { call =>
          for {
            prepared <- _prepare_start_c(call, typedStart,
              NativeStartPreparationFixture.allocationRow(typedStart), NativeStartPreparationFixture.sourceRow(typedStart))
            _ <- call.createC(collection("host_evidence"), prepared.recordIdentity, prepared.record)
          } yield ()
        }
        When("a NEW normal Start is invoked after that explicit provisioning")
        val started = _business_value[V.SplitPhaseStartResult](take(invokeC("startSplitPhase", startWire)))
        Then("R7/E6: Start constructs the execution owner and returns a real initial native execution without issuing work")
        started.outcome shouldBe "started"
        started.sessionId should not be empty
        prepare(call => call.currentC(InstanceIdentity(started.sessionId)).map { current =>
          current.identity.value shouldBe started.sessionId
          current.lifecycle shouldBe Lifecycle.Active
          current.history.size shouldBe 1
          current.suspension shouldBe empty
          ()
        })
        When("the caller separately invokes normal Advance using that returned session ID")
        val advanced = _business_value[V.SplitPhaseAdvanceResult](take(invokeC("advanceSplitPhase",
          NativeWorkflowOperationInputFixture.advance(started.sessionId))))
        Then("R7/E6: only Advance issues the actual semantic Request with its canonical instance equal to the Start session")
        advanced.kind shouldBe "Request"
        advanced.sessionId shouldBe started.sessionId
        advanced.partition should not be empty
        var assigned = Option.empty[WorkflowInteraction[SplitPhaseExplicitPartitionSemantic.Request, Nothing]]
        prepare(call => call.workC(InstanceIdentity(started.sessionId), SplitPhaseExplicitPartitionContext.completionOperation,
          SplitPhaseExplicitPartitionSemanticJson.requestCodec).map(value => { assigned = Some(value); () }))
        _assert_issued_context(advanced.issuedWorkContext, assigned.get, "design", SplitPhaseExplicitPartitionSemanticJson.requestCodec)
        assigned.get.handle.instanceIdentity.value shouldBe started.sessionId
        advanced.issuedWorkContext.get.handle.getString("instanceIdentity") shouldBe Some(started.sessionId)
      }
    }
    }
    "advance independent Split executions from the same chat without association selectors" in {
      Given("Phase5.3 P53-02–05: specification-owned public Start preparation and separate managed provisioning for one Split business input and two actual Starts in the same authenticated chat")
      val root = Files.createTempDirectory("smwf-split-executions-")
      withRuntime(root) { (owner, component, actor, prepare) =>
        def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          _business_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "SplitPhaseService", operation = operation, properties = properties), actor)))
        def _states_(ids: Vector[InstanceIdentity]): Vector[InstanceRecord] = {
          var records = Vector.empty[InstanceRecord]
          prepare(call => ids.foldLeft(Consequence.unit)((prior, identity) =>
            prior.flatMap(_ => call.currentC(identity).map(value => { records = records :+ value; () }))))
          records
        }
        prepare(prepareInputs)
        When("the same business input is started twice")
        val started = Vector.fill(2)(invoke[V.SplitPhaseStartResult]("startSplitPhase", startWire))
        val identities = started.map(value => InstanceIdentity(value.sessionId))
        Then("both executions have their own returned session ID")
        identities.distinct.size shouldBe 2
        val before = _states_(identities)
        When("Advance selects the first execution directly by its returned session ID")
        val first = invoke[V.SplitPhaseAdvanceResult]("advanceSplitPhase",
          NativeWorkflowOperationInputFixture.advance(started.head.sessionId))
        Then("only that execution issues partition work")
        first.kind shouldBe "Request"
        first.sessionId shouldBe started.head.sessionId
        first.partition should not be empty
        _states_(identities)(1).revision shouldBe before(1).revision
        When("the other execution advances and the first pending request is displayed again")
        val second = invoke[V.SplitPhaseAdvanceResult]("advanceSplitPhase",
          NativeWorkflowOperationInputFixture.advance(started(1).sessionId))
        val redisplayed = invoke[V.SplitPhaseAdvanceResult]("advanceSplitPhase",
          NativeWorkflowOperationInputFixture.advance(started.head.sessionId))
        Then("their requests stay with their own executions, without chat or context selectors")
        second.kind shouldBe "Request"
        second.sessionId shouldBe started(1).sessionId
        second.submitSessionId shouldBe Some(started(1).sessionId)
        redisplayed.sessionId shouldBe first.sessionId
        redisplayed.partition shouldBe first.partition
        Vector(first, second, redisplayed).foreach { value =>
          var assigned = Option.empty[WorkflowInteraction[SplitPhaseExplicitPartitionSemantic.Request, Nothing]]
          prepare(call => call.workC(InstanceIdentity(value.sessionId), SplitPhaseExplicitPartitionContext.completionOperation,
            SplitPhaseExplicitPartitionSemanticJson.requestCodec).map(work => { assigned = Some(work); () }))
          _assert_issued_context(value.issuedWorkContext, assigned.get, "design", SplitPhaseExplicitPartitionSemanticJson.requestCodec)
        }
        redisplayed.issuedWorkContext shouldBe first.issuedWorkContext
        first.issuedWorkContext.get.handle.getString("instanceIdentity") should not be
          second.issuedWorkContext.get.handle.getString("instanceIdentity")
      }
    }
    "start a new execution from the beginning after abandoning pending work without damaging existing files" in {
      Given("an owned directory, an existing file and one Split business input with specification-owned public Start preparation and separate managed provisioning")
      val root = Files.createTempDirectory("smwf-fresh-start-")
      val sentinel = root.resolve("existing.txt")
      Files.writeString(sentinel, "Existing user content\n")
      withRuntime(root) { (owner, component, actor, prepare) =>
        def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          _business_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "SplitPhaseService", operation = operation, properties = properties), actor)))
        prepare(prepareInputs)
        val first = invoke[V.SplitPhaseStartResult]("startSplitPhase", startWire)
        invoke[V.SplitPhaseAdvanceResult]("advanceSplitPhase",
          NativeWorkflowOperationInputFixture.advance(first.sessionId)).kind shouldBe "Request"
        When("the caller abandons pending work and starts a new execution from the same business input")
        val next = invoke[V.SplitPhaseStartResult]("startSplitPhase", startWire)
        Then("Start returns a distinct execution at its initial boundary without overwriting existing files")
        next.sessionId should not be first.sessionId
        prepare(call => call.currentC(InstanceIdentity(next.sessionId)).map { current =>
          current.lifecycle shouldBe Lifecycle.Active
          current.suspension shouldBe empty
          current.history.size shouldBe 1
          ()
        })
        Files.readString(sentinel) shouldBe "Existing user content\n"
        Files.exists(root.resolve("docs/phase/phase-77.md")) shouldBe false
      }
    }
    "admit stored Human evidence at the host boundary using current authority rather than a copied issuer receipt" in {
      Given("normal three-workflow storage and a Reader bound before an issuer withdrawal, without fabricated Workflow state")
      val root = Files.createTempDirectory("smwf-host-evidence-boundary-")
      withRuntime(root) { (_, _, _, prepare) =>
        prepare { call =>
          val configuration = call.core.component.get.subsystem.get.configuration
          val old = ResolvedConfiguration(HostConfiguration(configuration.configuration.values.updated(
            NativeExternalEvidence.prefix + "issuer", ConfigurationValue.StringValue("withdrawn-host"))), ConfigurationTrace.empty)
          val storage = GoalPhaseRuntimeParameters.Persistence(F.configuration,
            declaration.instances, declaration.continuations)
          val reader = take(NativeExternalEvidence.bindC(old, storage))
          val ports = new ThreeWorkflowExternalEvidencePorts(reader)
          val target = take(ThreeWorkflowExternalEvidencePorts.splitStartBindingC(
            SplitPhaseTypedStartHumanVerification.StartContext(call.core.component.get.core.instanceId, typedStart)))
          val now = call.executionContext.clock.instant()
          val selected = NativeExternalEvidence.Purpose.SplitStart
          val variants = Vector("current", "withdrawn", "future", "foreign-target", "absent")
          variants.foreach { variant =>
            Given(s"a separately provisioned $variant host input for this actual invocation")
            val lookup = ThreeWorkflowExternalEvidencePorts.key("boundary-" + variant, "1")
            val issued = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, selected.identity, lookup,
              "independent-start-consent", "1", "normal-development-host", "separate-human", target, now.minusSeconds(86400).toString)
            val observed = variant match {
              case "withdrawn" => issued.copy(issuer = "withdrawn-host")
              case "future" => issued.copy(occurredAt = now.plusSeconds(1).toString)
              case "foreign-target" => issued.copy(binding = target.mapObject(_.add("componentInstance", Json.fromString("another-owner"))))
              case _ => issued
            }
            if (variant != "absent") take(call.createC(collection("host_evidence"), NativeExternalEvidence.recordIdentity(selected, lookup),
              Record.data(NativeExternalEvidence.storageField -> take(NativeExternalEvidence.encodeStorageC(observed)))))
            When("the production host EvidencePort reads the physical record under the current host declaration and call clock")
            val admitted = ports.splitStart.admitC(call.core, lookup, target)
            Then("only currently authorized applicable evidence is admitted; no copied construction-time issuer restores withdrawn authority")
            admitted.isSuccess shouldBe (variant == "current")
          }
          Consequence.unit
        }
      }
    }
      "admit SplitPhase Start with expiry-free current host evidence" in {
        Given("the production three-service composition with specification-owned public Start preparation and separate managed provisioning of input and host evidence")
        val root = Files.createTempDirectory("smwf-no-internal-expiry-")
        withRuntime(root) { (owner, component, actor, prepare) =>
          prepare(call => prepareInputsWithFacts(call, F.facts))
          When("the real public Start is invoked with its current business input")
          val response = owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "SplitPhaseService", operation = "startSplitPhase",
            properties = startWire), actor)
          Then("the owning Start creates its normal native instance without a timestamp renewal requirement")
          val started = _business_value[V.SplitPhaseStartResult](take(response))
          started.outcome shouldBe "started"
          prepare(call => call.currentC(InstanceIdentity(started.sessionId)).map { current =>
            current.history.size shouldBe 1
            ()
          })
        }
      }
    "Public Split Human preparation" should afterWord("in spec:native-host-human-preparation, example:E3, rules:R1,R2,R3,R4,R5,R6, phase:6") {
    "Full issued Split Human tool conversion" should afterWord("in spec:native-host-evidence-tool, example:T4, rules:R1,R2,R3,R4, phase:6") {
    for (direction <- Vector("enriched", "resolved", "defer", "cancel", "reasoning-resolved", "reasoning-defer", "reasoning-cancel", "mapped-enriched")) {
      s"E12 session result consumption: continue actual scope through $direction and any assigned reasoning to its complete outcome" in {
        import SplitPhaseExplicitPartitionFixture.{base, enumerate, proof}
        import SplitPhaseExplicitObjectiveSelection as O
        import SplitPhaseScopeSemantic as S
        import SplitPhaseScopeSemanticJson as J
        import SplitPhaseExplicitReasoningSemantic as R
        import SplitPhaseExplicitReasoningSemanticJson as RJ
        import SplitPhaseHumanDirectionAnswerContract as H
        val mapped = direction == "mapped-enriched"
        val scopeenriched = mapped || direction == "enriched" || direction.startsWith("reasoning-")
        Given("Spec: docs/spec/split-phase-native-execution.md; Example: E6; Rules: R1,R3,R5,R6; Spec: docs/spec/session-workflow-context-resolution.md; Rule: R5; Example: E12; owned complete source/handoff data, specification-owned public Start preparation and separate managed provisioning, and the production three-service factory on managed SQLite")
        val root = Files.createTempDirectory("smwf-direct-scope-")
        val source = base(Vector(260, 180, 260))
        val boundary = proof(source, enumerate(source).candidates.find(_.cutAfterIndices == Vector(0)).get,
          "direct-scope-proposal", handoffPairs = Vector(0 -> 1))
        val objectives = O.Records(
          Some(O.CostPolicy(O.CostPolicyReference("direct-scope-cost", "1"), source.source.reference, source.source.authority,
            O.ProfileIdentity("expensive"), O.ProfileIdentity("lower"), 2, 1, 1)),
          Some(O.DemandRecord(O.DemandReference("direct-scope-demand", "1"), source.source.reference, source.source.authority,
            source.inventory.orderedUnfinishedItems.zipWithIndex.map { (id, index) =>
              O.ItemDemand(id, if (index == 0) O.Demand.Unresolved("one unknown scope-derived classification")
                else O.Demand.SettledExecution)
            })),
          Some(O.OverheadPolicy(O.OverheadPolicyReference("direct-scope-overhead", "1"), source.source.reference,
            source.source.authority, 1, 1, 0, 0, 0)))
        val facts = SplitPhaseExplicitPartitionContext.Facts(boundary.shape.records, boundary.records, source.records,
          F.facts.planning.copy(objectives = objectives))
        val start = typedStart.copy(input = F.input.copy(sourcePhaseIdentity = source.source.phase.value,
          proposalReference = Some(boundary.shape.records.proposal.reference.identity)),
          source = source.source, proposal = Some(boundary.shape.records.proposal))
        withRuntime(root, executionParameters = if (mapped) SplitExecutionFixture.parameters() else Map.empty) { (owner, component, actor, prepare) =>
          def invokeC(operation: String, properties: List[Property]) =
            owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "SplitPhaseService", operation = operation, properties = properties), actor)
          def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
            _business_value[A](take(invokeC(operation, properties)))
          prepare(call => prepareInputsWithFacts(call, facts, start))
          val started = invoke[V.SplitPhaseStartResult]("startSplitPhase",
            NativeWorkflowOperationInputFixture.start(start.input))
          val identity = InstanceIdentity(started.sessionId)
          val advance = NativeWorkflowOperationInputFixture.advance(started.sessionId)
          def current(): InstanceRecord = {
            var state = Option.empty[InstanceRecord]
            prepare(call => call.currentC(identity).map(value => { state = Some(value); () }))
            state.get
          }
          def work[A](operation: StateMachineOperationIdentity, codec: WorkflowResultJsonV1.PayloadCodec[A]):
            WorkflowInteraction[A, Nothing] = {
            var assigned = Option.empty[WorkflowInteraction[A, Nothing]]
            prepare(call => call.workC(identity, operation, codec).map(value => { assigned = Some(value); () }))
            assigned.get
          }
          def question[A](operation: StateMachineOperationIdentity, codec: WorkflowResultJsonV1.PayloadCodec[A]):
            WorkflowInteraction[A, Nothing] = {
            var assigned = Option.empty[WorkflowInteraction[A, Nothing]]
            prepare(call => call.questionC(identity, operation, codec).map(value => { assigned = Some(value); () }))
            assigned.get
          }
          def resultFor[A, B](assigned: WorkflowInteraction[A, Nothing], value: B, typeIdentity: String,
            reference: ContextReference): ContinuationResult[B] = {
            val request = assigned.current match {
              case value: WorkflowContinuation.WorkOrder[A] => value.request
              case value: WorkflowContinuation.Decision[A] => value.request
              case other => fail("Expected assigned work or decision: " + other)
            }
            ContinuationResult(assigned.handle, request.runId, request.continuationId, request.expectedRevision,
              request.context.snapshot, TypedValue(typeIdentity, value), reference, Vector.empty,
              ExecutionEvidence(Vector.empty, Some(F.participantIdentity)))
          }
          When("Advance returns only the unresolved handoff scope as a declared business request")
          val scope = invoke[V.SplitPhaseAdvanceResult]("advanceSplitPhase", advance)
          Then("the request identifies the actual Scope submit route and unresolved transfer")
          scope.kind shouldBe "Request"
          scope.submitOperation shouldBe Some("submitHandoffScopeResult")
          scope.scope.get.gaps.toVector.map(_.original.identity.value) shouldBe Vector("handoff-0")
          val scopework = work(SplitPhaseTypedScopeWorkIssue.completionOperation, J.requestCodec)
          _assert_issued_context(scope.issuedWorkContext, scopework, "design", J.requestCodec)
          Given("O-Split: the same actual pending scope assignment before semantic submission")
          When("a separate normal Advance displays that pending assignment again")
          val scopeagain = invoke[V.SplitPhaseAdvanceResult]("advanceSplitPhase", advance)
          Then("both its business view and canonical issued context remain the same")
          scopeagain.scope shouldBe scope.scope
          scopeagain.issuedWorkContext shouldBe scope.issuedWorkContext
          val scopeprepared = if (mapped) {
            Given("Split execution E6/R1,R3,R6: the first public design context and actual saved scope WorkOrder")
            When("the host prepares and retains the original canonical execution Session")
            val prepared = take(NativeWorkflowCallerExecution.prepareC(scope.issuedWorkContext.get,
              SplitExecutionFixture.mapping, SplitExecutionFixture.origin.current))
            Then("DesignStandard preserves the original independent review obligation and delegated placement")
            val requirement = scopework.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].requirement
            requirement.reasoningClass.map(_.value) shouldBe Some("DesignStandard")
            requirement.reviewRequired shouldBe true
            requirement.disposition.get.placement shouldBe ExecutionPlacement.Delegated
            requirement.disposition.get.independence shouldBe ExecutionIndependence.Required
            prepared.session.requirement shouldBe requirement
            Some(prepared)
          } else None
          val scopeinput = take(NativeWorkflowOperationOutput.workInputC(scopework))
          val evidence = SplitPhaseExplicitPartitionSemantic.EvidenceReference("direct-scope-evidence", "1")
          val judgment = S.Judgment(if (scopeenriched) S.Decision.Enriched else S.Decision.DecisionNeeded,
            "Specification interpretation of the assigned transfer",
            if (scopeenriched) scopeinput.gaps.map(gap => S.Scope(gap.original.identity,
              gap.producingItems.takeRight(1), gap.receivingItems.take(1), "original transfer ranges")) else Vector.empty,
            Vector(evidence))
          val barescopereply = resultFor(scopework, judgment, J.resultCodec.typeIdentity,
            ContextReference(scopeinput.result.identity, scopeinput.result.revision))
          val scopereply = scopeprepared.map(prepared => barescopereply.copy(evidence = SplitExecutionFixture.evidence(
            prepared, barescopereply.resultReference, "spec-scope-executor"))).getOrElse(barescopereply)
          if (mapped) {
            Given("Split execution E6/R6: the actual typed scope result and completed specification-owned harness evidence")
            val before = current()
            val observed = scopereply.evidence.execution.get
            When("Submit lacks the execution observation or crosses its origin")
            val invalids = Vector(scopereply.copy(evidence = scopereply.evidence.copy(execution = None)),
              scopereply.copy(evidence = scopereply.evidence.copy(execution = Some(observed.copy(originContextIdentity = "foreign-origin")))))
            val refused = invalids.map(value => invokeC("submitHandoffScopeResult",
              take(NativeWorkflowOperationInputFixture.submissionC(value, omitReference = true))))
            Then("both invalid completions fail without native progression or replacement of the pending issue")
            refused.foreach(_.isSuccess shouldBe false)
            current().revision shouldBe before.revision
            work(SplitPhaseTypedScopeWorkIssue.completionOperation, J.requestCodec) shouldBe scopework
          }
          Given("the requested typed scope judgment and its original completion evidence")
          When("the requested typed scope result is submitted to its actual owning completion")
          val scopeack = invoke[V.SplitPhaseScopeSubmissionResult]("submitHandoffScopeResult",
            take(NativeWorkflowOperationInputFixture.submissionC(scopereply, omitReference = true)))
          Then("Submit truthfully accepts the result without issuing the next work")
          scopeack.accepted shouldBe true
          scopeack.message should include("Accepted")
          current().suspension shouldBe empty

          Given("Split execution E6/R6: the accepted scope result and no pending next assignment")
          When("the caller separately Advances the same execution after scope acceptance")
          var response = invoke[V.SplitPhaseAdvanceResult]("advanceSplitPhase", advance)
          Then("the next response belongs to the same original execution")
          response.sessionId shouldBe started.sessionId
          if (!scopeenriched) {
            When("Advance asks Human direction for the actual DecisionNeeded scope")
            response.kind shouldBe "Human"
            response.issuedWorkContext shouldBe None
            response.scopeHuman should not be empty
            val assigned = question(SplitPhaseNativeHumanDirection.completionOperation,
              SplitPhaseNativeHumanDirection.inputCodec)
            val input = take(NativeWorkflowOperationOutput.decisionInputC(assigned))
            val answer = direction match {
              case "resolved" => H.Answer.ProvideResolution(
                SplitPhaseExplicitPartitionSemantic.ContextReference("direct-human-resolution", "1"))
              case "defer" => H.Answer.Defer
              case "cancel" => H.Answer.Cancel
            }
            val submitted = resultFor(assigned, answer, H.resultType, ContextReference("direct-human-evidence", "1"))
            val parameters = take(NativeWorkflowOperationInputFixture.submissionC(submitted))
            val before = current()
            Given("the complete public Scope Human output and the explicitly supplied fixture answer for tool example T4")
            When("the finite typed host tool converts that actual assigned output into normal importer input")
            val toolanswer = take(V.SplitPhaseHumanDirectionAnswer.createC(take(new org.goldenport.record.io.RecordDecoder().json(
              take(H.encodeC(answer)).hcursor.downField("payload").focus.get.noSpaces))))
            val toolseed = take(NativeExternalEvidenceToolFixture.prepareNormalCommand("SplitPhase", "human", response, toolanswer, F.participantIdentity,
              V.WorkflowArtifactReference(submitted.resultReference.identity, submitted.resultReference.revision),
              V.WorkflowExecutionEvidence(Vector.empty, submitted.evidence.workerIdentity, None, None)))
            val toolquestion = take(NativeSplitAnswerScope.questionBindingC(assigned))
            Then("tool export preserves the actual Scope target and supplied choice without advancing")
            NativeExternalEvidenceToolFixture.evidence(toolseed).binding.hcursor.downField("splitAnswerAttestation").downField("question").focus shouldBe
              Some(toolquestion)
            NativeExternalEvidenceToolFixture.evidence(toolseed).binding.hcursor.downField("splitAnswerAttestation").downField("answer").get[String]("choice").toOption shouldBe
              Some(toolanswer.choice.value)
            NativeExternalEvidenceToolFixture.evidence(toolseed).reference shouldBe submitted.resultReference.identity
            NativeExternalEvidenceToolFixture.evidence(toolseed).participant shouldBe submitted.evidence.workerIdentity.get
            current().revision shouldBe before.revision
            When("the Human answer lacks the required independent host consent")
            invokeC("submitSplitHumanDirection", parameters).isSuccess shouldBe false
            current().revision shouldBe before.revision
            Given("specification-owned consent for the actual assigned Human answer")
            When("public Human preparation consumes the actual Scope output context and explicit fixture answer")
            prepare(_.humanEvidenceC(response.scopeHuman.get.humanDecisionContext.get, submitted))
            Then("preparation and managed provisioning leave the pending Scope revision unchanged")
            current().revision shouldBe before.revision
            When("normal Human Submit records the selected Scope direction")
            val acknowledged = invoke[V.SplitPhaseHumanDirectionSubmissionResult]("submitSplitHumanDirection", parameters)
            Then("the Scope answer is acknowledged before the caller explicitly Advances")
            acknowledged.accepted shouldBe true
            if (direction == "resolved") {
              val request = take(S.requestC(_scope_frontier_(input).first))
              val resolution = SplitPhaseHumanScopeResolution.Record(
                SplitPhaseHumanScopeResolution.Reference("direct-human-resolution", "1"), request.context,
                request.checkpoint, input.scopeCheckpoint,
                SplitPhaseHumanScopeResolution.DecisionReference(submitted.continuationId.value),
                request.gaps.map(gap => S.Scope(gap.original.identity, gap.producingItems.takeRight(1),
                  gap.receivingItems.take(1), "Specification Human interpretation of original ranges")),
                "Complete assigned scope only",
                Vector(SplitPhaseExplicitPartitionSemantic.EvidenceReference("direct-human-interpretation", "1")))
              prepare(_.createC(collection("split_humanresolutions"), resolution.reference.identity,
                Record.data(SplitPhaseHumanScopeResolutionJsonV1.storageField ->
                  take(SplitPhaseHumanScopeResolutionJsonV1.encodeStorageC(resolution)))))
            }
            response = invoke[V.SplitPhaseAdvanceResult]("advanceSplitPhase", advance)
          }

          if (scopeenriched || direction == "resolved") {
            Given("O-Split: the current actual pending reasoning assignment after accepted scope or Human continuation")
            When("Advance requests only the unknown reasoning items")
            val reasoningresponse = invoke[V.SplitPhaseAdvanceResult]("advanceSplitPhase", advance)
            Then("analysis context retains the actual issued reasoning contracts on repeated display")
            reasoningresponse.issuedWorkContext shouldBe response.issuedWorkContext
            response.kind shouldBe "Request"
            response.submitOperation shouldBe Some("submitReasoningResult")
            response.reasoning.get.items.map(_.identity.value) shouldBe
              Vector(source.inventory.orderedUnfinishedItems.head.value)
            val assigned = work(SplitPhaseTypedReasoningWorkIssue.completionOperation, RJ.requestCodec)
            _assert_issued_context(response.issuedWorkContext, assigned, "analysis", RJ.requestCodec)
            val reasoningprepared = if (mapped) {
              Given("Split execution E6/R1,R3,R6: the first public analysis context and actual saved reasoning WorkOrder")
              When("the host prepares the new reasoning Session with its original canonical snapshot")
              val prepared = take(NativeWorkflowCallerExecution.prepareC(response.issuedWorkContext.get,
                SplitExecutionFixture.mapping, SplitExecutionFixture.origin.current))
              Then("AnalysisStandard preserves independent review and DELEGATED/REQUIRED selection")
              val requirement = assigned.current.asInstanceOf[WorkflowContinuation.WorkOrder[R.Request]].requirement
              requirement.reasoningClass.map(_.value) shouldBe Some("AnalysisStandard")
              requirement.reviewRequired shouldBe true
              requirement.disposition.get.placement shouldBe ExecutionPlacement.Delegated
              requirement.disposition.get.independence shouldBe ExecutionIndependence.Required
              prepared.session.requirement shouldBe requirement
              Some(prepared)
            } else None
            val input = take(NativeWorkflowOperationOutput.workInputC(assigned))
            val reasoning = R.Judgment(if (direction.startsWith("reasoning-")) R.Decision.DecisionNeeded
              else R.Decision.Enriched, "Classify only assigned unknown work",
              if (direction.startsWith("reasoning-")) Vector.empty else input.items.map(item =>
                R.Assignment(item.identity, R.Classification.SettledExecution, "bounded implementation")),
              Vector(SplitPhaseExplicitPartitionSemantic.EvidenceReference("direct-reasoning-evidence", "1")))
            val bareresult = resultFor(assigned, reasoning, RJ.resultCodec.typeIdentity,
              ContextReference(input.result.identity, input.result.revision))
            val result = reasoningprepared.map(prepared => bareresult.copy(evidence = SplitExecutionFixture.evidence(
              prepared, bareresult.resultReference, "spec-reasoning-executor"))).getOrElse(bareresult)
            if (mapped) {
              Given("Split execution E6/R6: an actual reasoning result with completed specification-owned harness evidence")
              val before = current()
              val observation = result.evidence.execution.get
              When("Reasoning Submit omits observation or crosses its original origin")
              val invalids = Vector(result.copy(evidence = result.evidence.copy(execution = None)),
                result.copy(evidence = result.evidence.copy(execution = Some(observation.copy(originContextIdentity = "foreign-origin")))))
              val refused = invalids.map(value => invokeC("submitReasoningResult",
                take(NativeWorkflowOperationInputFixture.submissionC(value, omitReference = true))))
              Then("native state and the actual pending reasoning issue stay unchanged")
              refused.foreach(_.isSuccess shouldBe false)
              current().revision shouldBe before.revision
              work(SplitPhaseTypedReasoningWorkIssue.completionOperation, RJ.requestCodec) shouldBe assigned
            }
            Given("the original typed reasoning judgment and its original valid completion evidence")
            When("the normal reasoning Submit consumes that result separately from Advance")
            val acknowledgement = invoke[V.SplitPhaseReasoningSubmissionResult]("submitReasoningResult",
              take(NativeWorkflowOperationInputFixture.submissionC(result, omitReference = true)))
            Then("Submit accepts without issuing new work before the separate Advance")
            acknowledgement.accepted shouldBe true
            current().suspension shouldBe empty
            Given("Split execution E6/R6: accepted reasoning and no pending assignment after Submit")
            When("the caller separately Advances after reasoning acceptance")
            response = invoke[V.SplitPhaseAdvanceResult]("advanceSplitPhase", advance)
            Then("the selected continuation still belongs to the original execution")
            response.sessionId shouldBe started.sessionId
            if (direction.startsWith("reasoning-")) {
              import SplitPhaseReasoningHumanDirection as RH
              import SplitPhaseHumanReasoningResolution as HR
              When("Advance returns the reasoning Human question with its actual business result and unknown items")
              response.kind shouldBe "Human"
              response.issuedWorkContext shouldBe None
              response.reasoningHuman.get.submittedJudgment.decision shouldBe "DecisionNeeded"
              val human = question(RH.completionOperation, RH.inputCodec)
              val reference = SplitPhaseExplicitPartitionSemantic.ContextReference("reasoning-human-resolution", "1")
              val answer = direction match {
                case "reasoning-resolved" => H.Answer.ProvideResolution(reference)
                case "reasoning-defer" => H.Answer.Defer
                case "reasoning-cancel" => H.Answer.Cancel
              }
              if (direction == "reasoning-resolved") {
                val interpretation = R.Judgment(R.Decision.Enriched, "Human interpretation of unknown items",
                  input.items.map(item => R.Assignment(item.identity, R.Classification.SettledExecution,
                    "Human bounded execution")),
                  Vector(SplitPhaseExplicitPartitionSemantic.EvidenceReference("reasoning-human-interpretation", "1")))
                // Existing consumed business coverage: incomplete, foreign or
                // repeated classifications must not become an applied plan.
                val before = current()
                val invalids = Vector(
                  "incomplete" -> interpretation.copy(assignments = Vector.empty),
                  "crossed-item" -> interpretation.copy(assignments = interpretation.assignments.map(_.copy(
                    identity = SplitPhaseExplicitProposalPrecheck.WorkItemIdentity("foreign-item")))),
                  "repeated-item" -> interpretation.copy(assignments = interpretation.assignments ++ interpretation.assignments))
                for ((episode, data) <- invalids) {
                  val invalidreference = SplitPhaseExplicitPartitionSemantic.ContextReference("reasoning-bad-" + episode, "1")
                  val record = HR.Record(invalidreference, "unused-decision-diagnostic", result.resultReference, data)
                  prepare(_.createC(collection("split_humanresolutions"), invalidreference.identity,
                    Record.data(HR.storageField -> take(HR.encodeC(record)))))
                  val invalidanswer = resultFor(human, H.Answer.ProvideResolution(invalidreference),
                    H.resultType, ContextReference("reasoning-bad-answer-" + episode, "1"))
                  prepare(_.privateNativeHumanEvidenceC(human, invalidanswer))
                  When("Human selects " + episode + " classification data for the actually requested unknown items")
                  invokeC("submitSplitHumanDirection",
                    take(NativeWorkflowOperationInputFixture.submissionC(invalidanswer))).isSuccess shouldBe false
                  Then("the owning business boundary refuses it before changing native state or creating a plan")
                  current().revision shouldBe before.revision
                }
                val record = HR.Record(reference, "unused-decision-diagnostic",
                  ContextReference("unused-reasoning-result", "1"), interpretation)
                prepare(_.createC(collection("split_humanresolutions"), reference.identity,
                  Record.data(HR.storageField -> take(HR.encodeC(record)))))
              }
              val humananswer = resultFor(human, answer, H.resultType, ContextReference("reasoning-human-answer", "1"))
              Given("the complete public Reasoning Human output and the supplied fixture answer for tool example T4")
              When("the finite typed tool converts that actual assigned output and answer to normal importer input")
              val toolanswer = take(V.SplitPhaseHumanDirectionAnswer.createC(take(new org.goldenport.record.io.RecordDecoder().json(
                take(H.encodeC(answer)).hcursor.downField("payload").focus.get.noSpaces))))
              val toolseed = take(NativeExternalEvidenceToolFixture.prepareNormalCommand("SplitPhase", "human", response, toolanswer, F.participantIdentity,
                V.WorkflowArtifactReference(humananswer.resultReference.identity, humananswer.resultReference.revision),
                V.WorkflowExecutionEvidence(Vector.empty, humananswer.evidence.workerIdentity, None, None)))
              val toolquestion = take(NativeSplitAnswerScope.questionBindingC(human))
              Then("tool export retains the actual Reasoning source and supplied choice")
              NativeExternalEvidenceToolFixture.evidence(toolseed).binding.hcursor.downField("splitAnswerAttestation").downField("question").focus shouldBe Some(toolquestion)
              NativeExternalEvidenceToolFixture.evidence(toolseed).binding.hcursor.downField("splitAnswerAttestation").downField("answer").get[String]("choice").toOption shouldBe Some(toolanswer.choice.value)
              NativeExternalEvidenceToolFixture.evidence(toolseed).reference shouldBe humananswer.resultReference.identity
              NativeExternalEvidenceToolFixture.evidence(toolseed).participant shouldBe humananswer.evidence.workerIdentity.get
              Given("an explicitly invalid duplicate Scope display alongside the actual full Reasoning Human output")
              val invalidscope = V.SplitPhaseScopeHumanQuestion("Invalid duplicate assignment", H.choices,
                response.reasoningHuman.get.sourcePhaseIdentity, response.reasoningHuman.get.sourceAuthority,
                response.reasoningHuman.get.items, response.reasoningHuman.get.candidates, scope.scope.get.gaps.toVector,
                V.SplitPhaseScopeSemanticResult("DecisionNeeded", "Invalid duplicate display", Vector.empty,
                  cats.data.NonEmptyVector.of(V.SplitPhasePartitionEvidenceReference("invalid-duplicate-fixture", "1"))),
                response.reasoningHuman.get.humanDecisionContext)
              When("the tool receives this ambiguous request rather than one selected issued Human")
              val ambiguous = NativeExternalEvidenceToolFixture.prepare("SplitPhase", "human",
                response.copy(scopeHuman = Some(invalidscope)), toolanswer, F.participantIdentity,
                V.WorkflowArtifactReference(humananswer.resultReference.identity, humananswer.resultReference.revision),
                V.WorkflowExecutionEvidence(Vector.empty, humananswer.evidence.workerIdentity, None, None))
              Then("the duplicate assignment fails without a successful seed or native progression")
              ambiguous.isSuccess shouldBe false
              Given("specification-owned evidence for that actual Human answer")
              When("public Human preparation consumes the actual Reasoning output context and explicit fixture answer")
              val beforeanswer = current()
              prepare(_.humanEvidenceC(response.reasoningHuman.get.humanDecisionContext.get, humananswer))
              Then("preparation and provisioning leave the pending Reasoning revision unchanged")
              current().revision shouldBe beforeanswer.revision
              When("normal Human Submit records the selected Reasoning direction")
              val humanack = invoke[V.SplitPhaseHumanDirectionSubmissionResult]("submitSplitHumanDirection",
                take(NativeWorkflowOperationInputFixture.submissionC(humananswer)))
              Then("the Reasoning answer is acknowledged before the explicit next Advance")
              humanack.accepted shouldBe true
              response = invoke[V.SplitPhaseAdvanceResult]("advanceSplitPhase", advance)
            }
          }
          Then("the owning Advance returns the complete Preview or the selected stopped outcome without writing documents")
          response.kind shouldBe "Terminal"
          response.issuedWorkContext shouldBe None
          response.sessionId shouldBe started.sessionId
          if (Set("defer", "cancel", "reasoning-defer", "reasoning-cancel")(direction)) {
            response.stopped.get.outcome shouldBe (if (direction.endsWith("defer")) "BLOCKED" else "CANCELLED")
            response.selectedPlan shouldBe empty
          } else {
            response.selectedPlan.get.mode shouldBe "PREVIEW"
            response.selectedPlan.get.children.flatMap(_.orderedWorkItems.map(_.identity)) shouldBe
              source.inventory.orderedUnfinishedItems.map(_.value)
          }
          current().lifecycle shouldBe Lifecycle.Completed
          current().suspension shouldBe empty
          Files.exists(root.resolve("docs/phase/phase-77.md")) shouldBe false
        }
      }
    }
    }
    }
    for (episode <- Vector("normal", "occupied child")) {
      s"apply SplitPhase selected documents through typed File direct Operations for $episode" in {
        import SplitPhaseExplicitPartitionRawCodecs.given
        import io.circe.syntax.*
        Given("a spec-owned Phase document and APPLY selection with public Start preparation and separate managed provisioning, and a normal workspace-only V3 File grant")
        val source = SplitPhaseExplicitPartitionFixture.base(Vector(360, 360))
        val start = typedStart.copy(input = F.input.copy(sourcePhaseIdentity = source.source.phase.value, invocationMode = "APPLY",
          proposalReference = Some(source.initial.shape.records.proposal.reference.identity)), source = source.source,
          proposal = Some(source.initial.shape.records.proposal))
        val facts = SplitPhaseExplicitPartitionContext.Facts(source.initial.shape.records, source.initial.records, source.records,
          SplitPhasePlanningCheckpointFixture.knownFacts.planning.copy(source = source.source.reference,
            authority = source.source.authority, objectives = SplitPhaseExplicitObjectiveSelection.Records(None, None, None)))
        val work = Files.createDirectories(Path.of("target/sm-workflow/work/direct-operation-apply").toAbsolutePath)
        // Retained as diagnostic evidence; only this spec-owned directory is used.
        val root = Files.createTempDirectory(work, "repository-")
        Files.createDirectories(root.resolve("docs/phase"))
        val phase = root.resolve("docs/phase/phase-77.md")
        val child = root.resolve("docs/phase/phase-77.1.md")
        val original = (Vector("# Phase 77", "", "## Scope", "", "| ID | Outcome | Status |", "| --- | --- | --- |") ++
          source.inventory.orderedUnfinishedItems.map(id => s"| ${id.value} | original work | planned |") ++
          Vector("| old-completed | accepted work | completed |", "", "## History", "", "Keep unrelated history.", "",
            "## Source boundary", "", "```" + WorkflowSelectedDocumentFiles.sourceSchema, source.sourceBoundary.asJson.noSpaces, "```", "")).mkString("\n")
        Files.writeString(phase, original)
        if (episode == "occupied child") Files.writeString(child, "Independent existing child\n")
        withRuntime(root, Some(_document_workspace_plan(root))) { (owner, component, actor, prepare) =>
          def invokeC(operation: String, properties: List[Property]) =
            owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "SplitPhaseService", operation = operation, properties = properties), actor)
          prepare(call => prepareInputsWithFacts(call, facts, start))
          When("the public Start creates its own APPLY execution from the selected business input")
          val started = _business_value[V.SplitPhaseStartResult](take(invokeC("startSplitPhase",
            NativeWorkflowOperationInputFixture.start(start.input))))
          val identity = InstanceIdentity(started.sessionId)
          def current(): InstanceRecord = {
            var state = Option.empty[InstanceRecord]
            prepare(call => call.currentC(identity).map(value => { state = Some(value); () }))
            state.get
          }
          Then("Start persists only its genuine first native history entry")
          current().history.size shouldBe 1
          When("Advance owns selection and actual document effects")
          val result = invokeC("advanceSplitPhase", NativeWorkflowOperationInputFixture.advance(started.sessionId))
          if (episode == "normal") {
            val response = _business_value[V.SplitPhaseAdvanceResult](take(result))
            val selected = response.selectedPlan.get
            Then("the actual applied result and document content agree with the complete selected plan")
            response.kind shouldBe "Terminal"
            response.issuedWorkContext shouldBe None
            response.sessionId shouldBe started.sessionId
            selected.mode shouldBe "APPLY"
            selected.effectStatus shouldBe Some("applied")
            selected.documents.flatMap(_.ownedItems) shouldBe source.inventory.orderedUnfinishedItems.map(_.value)
            selected.documents.tail.foreach { document =>
              Files.readString(root.resolve(document.relativePath)) shouldBe document.childDocument.get
            }
            Files.readString(phase) should include("Keep unrelated history.")
            Files.readString(phase) should include("| old-completed | accepted work | completed |")
            current().lifecycle shouldBe Lifecycle.Completed
            current().suspension shouldBe empty
          } else {
            Then("an occupied child remains a real file conflict and is not overwritten or called successful")
            result.isSuccess shouldBe false
            result.toString should include("actual documents conflict with the saved selection")
            Files.readString(phase) shouldBe original
            Files.readString(child) shouldBe "Independent existing child\n"
            current().lifecycle shouldBe Lifecycle.Active
            current().currentProgression.map(_.value) shouldBe Some(SplitPhaseSelectedDocumentEffect.applyingProgression)
          }
          Files.exists(root.resolve("docs/phase/.sm-workflow-document.lock")) shouldBe false
        }
      }
    }
    "E12 session result consumption: complete SplitPhase semantic work to Preview through direct Operations" in {
      import SplitPhaseExplicitPartitionSemantic as S
      import SplitPhaseExplicitPartitionSemanticJson as J
      Given("Spec: docs/spec/session-workflow-context-resolution.md; Rule: R5; Example: E12; specification-owned objective data, public Start preparation and separate managed provisioning, and production Operations on the same managed SQLite store")
      val root = Files.createTempDirectory("smwf-direct-preview-")
      val facts = _semantic_preview_facts()
      withRuntime(root) { (owner, component, actor, prepare) =>
        When("the installed public declarations are read and their configured business input starts an execution")
        val published = component.protocol.services.services.toVector
          .filter(service => Set("GoalPhaseService", "SplitPhaseService", "RepositorySyncService").contains(service.name))
          .flatMap(service => service.operations.operations.toVector.map(operation =>
            (s"${service.name}.${operation.name}", operation)))
        def invokeC(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "SplitPhaseService", operation = operation,
            properties = properties ++ List(Property("unusedCommandGrant", "not-authority", None),
              Property("textus.format", "yaml", None))), actor)
        def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          _business_value[A](take(invokeC(operation, properties)))
        prepare(call => prepareInputsWithFacts(call, facts))
        val started = invoke[V.SplitPhaseStartResult]("startSplitPhase", startWire)
        val identity = InstanceIdentity(started.sessionId)
        val advance = NativeWorkflowOperationInputFixture.advance(started.sessionId)
        def current(): InstanceRecord = {
          var state = Option.empty[InstanceRecord]
          prepare(call => call.currentC(identity).map(value => { state = Some(value); () }))
          state.get
        }
        Then("exactly the fifty installed Operations expose named business input and explicit execution parameters")
        val expected = Set(
          "GoalPhaseService.startGoalPhase", "GoalPhaseService.advanceGoalPhase", "GoalPhaseService.submitGoalPhaseWorkResult",
          "GoalPhaseService.submitGoalPhaseOrderedDecision",
          "GoalPhaseService.submitGoalPhaseFindingRecording",
          "GoalPhaseService.resolveGoalPhaseDiagnosticInformation",
          "GoalPhaseService.describeGoalPhaseValidationRecovery", "GoalPhaseService.recoverGoalPhaseValidation",
          "GoalPhaseService.describeGoalPhaseClosureRecovery", "GoalPhaseService.recoverGoalPhaseClosure",
          "GoalPhaseService.describeGoalPhaseSourceHistoryRecovery", "GoalPhaseService.recoverGoalPhaseSourceHistory",
          "GoalPhaseService.describeGoalPhaseSourceHistoryNormalization", "GoalPhaseService.adoptGoalPhaseSourceHistoryNormalization",
          "GoalPhaseService.describeGoalPhaseCandidateRecovery", "GoalPhaseService.recoverGoalPhaseCandidate",
          "GoalPhaseService.describeGoalPhaseWholePhaseCandidateRecovery", "GoalPhaseService.recoverGoalPhaseWholePhaseCandidate",
          "GoalPhaseService.describeGoalPhaseCandidateObservationContinuation", "GoalPhaseService.continueGoalPhaseCandidateObservation",
          "GoalPhaseService.describeGoalPhaseRemainingPlan", "GoalPhaseService.adoptGoalPhaseRemainingPlan",
          "GoalPhaseService.describeGoalPhaseDependencySourceRevision", "GoalPhaseService.adoptGoalPhaseDependencySourceRevision",
          "GoalPhaseService.describeGoalPhaseReleaseRepairContinuation", "GoalPhaseService.recoverGoalPhaseReleaseRepairContinuation",
          "GoalPhaseService.describeGoalPhaseReviewPurposeRepair", "GoalPhaseService.admitGoalPhaseReviewPurposeRepair",
          "GoalPhaseService.describeGoalPhaseClosureRecovery", "GoalPhaseService.recoverGoalPhaseClosure",
          "GoalPhaseService.describeGoalPhaseSourceHistoryRecovery", "GoalPhaseService.recoverGoalPhaseSourceHistory",
          "GoalPhaseService.describeGoalPhaseCandidateRecovery", "GoalPhaseService.recoverGoalPhaseCandidate",
          "GoalPhaseService.describeGoalPhaseWholePhaseCandidateRecovery", "GoalPhaseService.recoverGoalPhaseWholePhaseCandidate",
          "GoalPhaseService.describeGoalPhaseCandidateObservationContinuation", "GoalPhaseService.continueGoalPhaseCandidateObservation",
          "GoalPhaseService.describeGoalPhaseCompletionPolicy", "GoalPhaseService.adoptGoalPhaseCompletionPolicy",
          "GoalPhaseService.describeGoalPhaseRepairPolicy", "GoalPhaseService.adoptGoalPhaseRepairPolicy",
          "GoalPhaseService.describeGoalPhaseFailedPhaseRepair", "GoalPhaseService.adoptGoalPhaseFailedPhaseRepair",
          "GoalPhaseService.describeGoalPhaseValidationRepair", "GoalPhaseService.adoptGoalPhaseValidationRepair",
          "SplitPhaseService.startSplitPhase", "SplitPhaseService.advanceSplitPhase", "SplitPhaseService.submitPartitionSemanticResult",
          "SplitPhaseService.submitHandoffScopeResult", "SplitPhaseService.submitSplitHumanDirection", "SplitPhaseService.submitReasoningResult",
          "RepositorySyncService.startRepositorySync", "RepositorySyncService.advanceRepositorySync",
          "RepositorySyncService.describeRepositorySyncValidationContinuation", "RepositorySyncService.adoptRepositorySyncValidationContinuation",
          "RepositorySyncService.submitRepositorySyncWorkResult", "RepositorySyncService.submitRepositoryPurposeResult", "RepositorySyncService.submitNativeMergeReview",
          "RepositorySyncService.submitRepositoryConflictDecision", "RepositorySyncService.submitNativeMergeRepair")
        published should have size 51
        published.map(_._1).distinct should have size 51
        published.map(_._1).toSet shouldBe expected
        val sessiondescriptions = Set("describeGoalPhaseValidationRecovery", "describeGoalPhaseClosureRecovery",
          "describeGoalPhaseSourceHistoryRecovery", "describeGoalPhaseCandidateRecovery",
          "describeGoalPhaseWholePhaseCandidateRecovery", "describeGoalPhaseCandidateObservationContinuation",
          "describeGoalPhaseSourceHistoryNormalization", "describeRepositorySyncValidationContinuation")
        published.foreach { (identity, operation) =>
          withClue(identity) {
            operation.specification.request.parameters.head.name shouldBe
              (if (sessiondescriptions(operation.name)) "sessionId"
                else if (Vector("start", "describe", "recover", "adopt", "admit", "continue", "resolve").exists(operation.name.startsWith)) "input"
                else "sessionId")
            if (Set("describeGoalPhaseValidationRepair", "adoptGoalPhaseValidationRepair",
                "recoverGoalPhaseCandidate", "recoverGoalPhaseWholePhaseCandidate", "describeGoalPhaseReleaseRepairContinuation",
                "recoverGoalPhaseReleaseRepairContinuation").contains(operation.name)) {
              operation.specification.request.parameters.map(_.name) shouldBe Vector("input", "inputResource")
              operation.specification.request.parameters.map(_.domain.multiplicity) shouldBe
                Vector(Multiplicity.ZeroOne, Multiplicity.ZeroOne)
            } else operation.specification.request.parameters.head.domain.multiplicity shouldBe Multiplicity.One
          }
        }
        val beforeinvalid = current()
        When("Advance receives a malformed consumed sessionId rather than a valid execution ID")
        Vector(42, null).foreach { value =>
          invokeC("advanceSplitPhase", List(Property("sessionId", value, None))).isSuccess shouldBe false
        }
        Then("the input boundary refuses it without changing the execution or writing documents")
        current().revision shouldBe beforeinvalid.revision
        Files.exists(root.resolve("docs/phase/phase-77.md")) shouldBe false

        When("Advance asks only for the unknown estimate and semantic boundary")
        val requested = invoke[V.SplitPhaseAdvanceResult]("advanceSplitPhase", advance)
        Then("the assignment contains only the unknown estimate and undecided boundary")
        requested.partition.get.items.map(_.identity.value) shouldBe Vector("item-1")
        requested.partition.get.boundaries.map(_.edge.afterItem.value) shouldBe Vector("item-0")
        var assignment = Option.empty[WorkflowInteraction[S.Request, Nothing]]
        prepare(call => call.workC(identity, SplitPhaseExplicitPartitionContext.completionOperation, J.requestCodec)
          .map(value => { assignment = Some(value); () }))
        val work = assignment.get
        _assert_issued_context(requested.issuedWorkContext, work, "design", J.requestCodec)
        val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
        val input = order.input.get.value
        val evidence = S.EvidenceReference("direct-semantic-evidence", "1")
        val judgment = S.Judgment(S.Decision.Enriched, "bounded independent semantic closures",
          input.items.map(item => S.NovelEstimate(item.identity, 120, 180, "bounded work", "novel closure")),
          input.boundaries.map(boundary => S.BoundaryJudgment(boundary.edge.afterItem,
            SplitPhasePartitionEnumeration.EdgeDisposition.Either, "independent closure")), Vector(evidence))
        val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
          order.context.snapshot, TypedValue(J.resultCodec.typeIdentity, judgment),
          ContextReference(input.reply.result.identity, input.reply.result.revision), Vector.empty,
          ExecutionEvidence(Vector.empty, Some(F.participantIdentity)))
        val before = current()
        When("the assigned semantic judgment is encoded for its public Submit Operation")
        val submitted = take(NativeWorkflowOperationInputFixture.submissionC(result, omitReference = true))
        Then("the answer contains semantic business data and the execution ID, not a request/context selector")
        submitted.map(_.name).toSet shouldBe Set("sessionId", "result", "completionFacts", "executionEvidence")
        When("Submit consumes the result but does not select the next work")
        val acknowledgement = invoke[V.SplitPhaseSemanticSubmissionResult]("submitPartitionSemanticResult", submitted)
        Then("the actual committed result is acknowledged while the execution remains active without pending work")
        acknowledgement.accepted shouldBe true
        acknowledgement.message should include("Accepted")
        current().revision shouldBe before.revision.next
        current().lifecycle shouldBe Lifecycle.Active
        current().suspension shouldBe empty

        Given("the completed execution was PREVIEW while passive host allocation and recommendations now differ")
        prepare(call => call.rewriteFixtureRowC(collection("split_starts"), typedStart.reference.identity,
          SplitPhaseTypedStartJsonV1.storageField, SplitPhaseTypedStartJsonV1.allocationSchema + ":") { json =>
          json.hcursor.downField("payload").withFocus(_.mapObject { payload =>
            payload.add("input", payload("input").get.mapObject(_.add("invocationMode", Json.fromString("APPLY"))))
              .add("source", Json.Null).add("proposal", Json.False)
          }).top.get
        })
        prepare(call => call.rewriteFixtureRowC(collection("split_proposal"), facts.proposal.proposal.reference.identity,
          SplitPhaseExplicitPlanningInputJsonV1.storageField, SplitPhaseExplicitPlanningInputJsonV1.schema + ":") { json =>
          json.hcursor.downField("payload").withFocus(_.mapObject(payload => payload.add("children",
            Json.fromValues(payload("children").get.asArray.get.map(_.mapObject(
              _.add("expectedMinutes", Json.fromInt(361)))))))).top.get
        })
        prepare(call => call.rewriteFixtureRowC(collection("split_selections"),
          NativeWorkflowStartBusinessKey(typedStart.input, typedStart.participantIdentity),
          ProfileSelectionJsonV1.storageField, "sm-workflow.profile-selection-json.v1:")(
          _.mapObject(_.add("recommendationReference", Json.fromString("changed-current-recommendation")))))
        When("Advance consumes the completed business result and publishes the selected Preview")
        val preview = invoke[V.SplitPhaseAdvanceResult]("advanceSplitPhase", advance)
        Then("the original PREVIEW mode and complete work survive without rereading unused host choices or applying files")
        preview.kind shouldBe "Terminal"
        preview.issuedWorkContext shouldBe None
        preview.selectedPlan.get.mode shouldBe "PREVIEW"
        preview.selectedPlan.get.effectStatus shouldBe empty
        preview.selectedPlan.get.children.flatMap(_.orderedWorkItems.map(_.identity)) shouldBe
          facts.proposal.inventory.orderedUnfinishedItems.map(_.value)
        current().lifecycle shouldBe Lifecycle.Completed
        current().suspension shouldBe empty
        Files.exists(root.resolve("docs/phase/phase-77.md")) shouldBe false
      }
    }
    for (mapped <- Vector(false, true)) {
    s"acknowledge actual completion without rereading unused context presentation after closing selection (mapped=$mapped)" in {
      import SplitPhaseExplicitPartitionSemantic as S
      import SplitPhaseExplicitPartitionSemanticJson as J
      import SplitPhaseExplicitPartitionContextJsonV1 as C
      Given("Spec: docs/spec/split-phase-native-execution.md; Example: E5; Rules: R1,R3,R4,R6; the existing Phase5.1 unnecessary-copy policy and specification-owned public Start preparation and separate managed provisioning for one real public Start with managed partition work")
      val root = Files.createTempDirectory("smwf-completion-authoritative-response-")
      val facts = _semantic_preview_facts()
      var execution = Option.empty[InstanceIdentity]
      def identity: InstanceIdentity = execution.get
      def contextReference = S.ContextReference("split-context:" + identity.value, "1")
      def advance = NativeWorkflowOperationInputFixture.advance(identity.value)
      var pending = Option.empty[WorkflowInteraction[S.Request, Nothing]]
      var pendingstate = Option.empty[InstanceRecord]
      var completionstate = Option.empty[InstanceRecord]
      var changed = 0
      var issuedcontext = Option.empty[V.WorkflowIssuedWorkContext]
      var retained = Option.empty[WorkflowExecutionHarness.Session]
      val initialparameters = if (mapped) SplitExecutionFixture.parameters() else Map.empty[String, String]
      val changedparameters = if (mapped) SplitExecutionFixture.parameters(replacement = true) else Map.empty[String, String]
      def request(component: org.goldenport.cncf.component.Component, operation: String,
        properties: List[Property]): Request = Request.of(component = component.name,
          service = "SplitPhaseService", operation = operation, properties = properties)
      def display(call: Setup, title: String): Consequence[Unit] =
        call.rewriteFixtureRowC(collection("split_contexts"), contextReference.identity, C.storageField, C.schema + ":")(
          _.mapObject(json => json.add("payload", json("payload").get.mapObject(payload =>
            payload.add("owner", payload("owner").get.mapObject(owner => owner.add("presentation",
              owner("presentation").get.mapObject(_.add("title", Json.fromString(title))))))))))
      withRuntime(root, executionParameters = initialparameters) { (owner, component, actor, prepare) =>
        prepare(call => prepareInputsWithFacts(call, facts))
        val started = _business_value[V.SplitPhaseStartResult](take(owner.executeOperationResponseInContext(
          request(component, "startSplitPhase", startWire), actor)))
        execution = Some(InstanceIdentity(started.sessionId))
        When("normal public Advance issues the actual semantic assignment")
        val requested = _business_value[V.SplitPhaseAdvanceResult](take(owner.executeOperationResponseInContext(
          request(component, "advanceSplitPhase", advance), actor)))
        requested.kind shouldBe "Request"
        prepare(call => call.workC(identity, SplitPhaseExplicitPartitionContext.completionOperation, J.requestCodec)
          .map(value => { pending = Some(value); () }))
        prepare(call => call.currentC(identity).map(value => { pendingstate = Some(value); () }))
        Then("native work remains suspended awaiting the actual answer")
        pendingstate.get.suspension should not be empty
        _assert_issued_context(requested.issuedWorkContext, pending.get, "design", J.requestCodec)
        issuedcontext = requested.issuedWorkContext
        if (mapped) {
          Given("Split execution E5/R1,R3,R4: actual first-issued partition design and its public context")
          When("the host prepares and retains its original canonical Session")
          val prepared = take(NativeWorkflowCallerExecution.prepareC(requested.issuedWorkContext.get,
            SplitExecutionFixture.mapping, SplitExecutionFixture.origin.current))
          retained = Some(prepared.session)
          Then("the saved partition retains DesignStandard and original independent DELEGATED/REQUIRED execution")
          val requirement = pending.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].requirement
          requirement.reasoningClass.map(_.value) shouldBe Some("DesignStandard")
          requirement.reviewRequired shouldBe true
          requirement.disposition.get.placement shouldBe ExecutionPlacement.Delegated
          requirement.disposition.get.independence shouldBe ExecutionIndependence.Required
          prepared.session.requirement shouldBe requirement
        }
      }
      val work = pending.get
      val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
      val input = order.input.get.value
      val evidence = S.EvidenceReference("completion-response-semantic-evidence", "1")
      val judgment = S.Judgment(S.Decision.Enriched, "actual requested closures",
        input.items.map(item => S.NovelEstimate(item.identity, 120, 180, "bounded work", "novel closure")),
        input.boundaries.map(boundary => S.BoundaryJudgment(boundary.edge.afterItem,
          SplitPhasePartitionEnumeration.EdgeDisposition.Either, "independent closure")), Vector(evidence))
      val bareresult = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
        order.context.snapshot, TypedValue(J.resultCodec.typeIdentity, judgment),
        ContextReference(input.reply.result.identity, input.reply.result.revision), Vector.empty,
        ExecutionEvidence(Vector.empty, Some(F.participantIdentity)))
      var result = bareresult
      var reply = take(NativeWorkflowOperationInputFixture.submissionC(result))
      def changeDisplay(core: ActionCall.Core): Consequence[Unit] = {
        Given("the normal completion selected its genuine closing Plan but has not committed it")
        val call = new Setup(core, _ => Consequence.unit)
        val before = take(call.currentC(identity))
        When("the specification changes only unused stored context presentation before the owning commit")
        take(display(call, "Compatible updated display"))
        val reader = take(SplitPhasePartitionContextReadPolicy.bindC(core.component.get.subsystem.get.configuration, declaration))
        val observed = take(reader.loadForCallC(contextReference, core)).get
        Then("the display changed without changing issued work, actual scope or pending native state")
        observed.owner.presentation.title shouldBe "Compatible updated display"
        take(call.currentC(identity)).revision shouldBe before.revision
        changed += 1
        Consequence.unit
      }
      withRuntime(root, completionobservation = Some(changeDisplay), executionParameters = changedparameters) { (owner, component, actor, prepare) =>
        Given("Split execution E5/R4,R6: a reopened host with valid changed configuration omitting original DesignStandard")
        When("normal Advance redisplays pending partition work under the changed host")
        val repeated = _business_value[V.SplitPhaseAdvanceResult](take(owner.executeOperationResponseInContext(
          request(component, "advanceSplitPhase", advance), actor)))
        val repeatedagain = _business_value[V.SplitPhaseAdvanceResult](take(owner.executeOperationResponseInContext(
          request(component, "advanceSplitPhase", advance), actor)))
        var saved = Option.empty[WorkflowInteraction[S.Request, Nothing]]
        prepare(call => call.workC(identity, SplitPhaseExplicitPartitionContext.completionOperation, J.requestCodec)
          .map(value => { saved = Some(value); () }))
        Then("the original actual issue and public context survive without new allocation or native revision")
        repeated.issuedWorkContext shouldBe issuedcontext
        repeatedagain.issuedWorkContext shouldBe repeated.issuedWorkContext
        repeatedagain.partition shouldBe repeated.partition
        saved.get shouldBe pending.get
        prepare(call => call.currentC(identity).map { current =>
          current.revision shouldBe pendingstate.get.revision
          ()
        })
        if (mapped) {
          Given("the original retained Session artifact and actual redisplayed public context")
          When("the host restores that original Session without preparing against changed mapping")
          val restored = take(NativeWorkflowCallerExecution.restoreC(repeated.issuedWorkContext.get,
            take(WorkflowExecutionHarness.decodeC(take(WorkflowExecutionHarness.encodeC(retained.get))))))
          result = bareresult.copy(evidence = SplitExecutionFixture.evidence(restored, bareresult.resultReference, "spec-partition-executor"))
          reply = take(NativeWorkflowOperationInputFixture.submissionC(result))
          Then("the restored execution retains the original mapping revision and origin")
          restored.session.mappingIdentity shouldBe SplitExecutionFixture.mapping.identity
          restored.session.mappingRevision shouldBe SplitExecutionFixture.mapping.revision
          restored.session.origin shouldBe SplitExecutionFixture.origin.current
          Given("the original typed partition judgment with completed specification-owned harness evidence")
          val observed = result.evidence.execution.get
          When("Partition Submit omits observation or crosses its original origin")
          val invalids = Vector(result.copy(evidence = result.evidence.copy(execution = None)),
            result.copy(evidence = result.evidence.copy(execution = Some(observed.copy(originContextIdentity = "foreign-origin")))))
          val refused = invalids.map(value => owner.executeOperationResponseInContext(
            request(component, "submitPartitionSemanticResult", take(NativeWorkflowOperationInputFixture.submissionC(value))), actor))
          Then("both attempts fail without native progression while the fixture presentation callback has run")
          refused.foreach(_.isSuccess shouldBe false)
          changed shouldBe 2
          prepare(call => call.currentC(identity).map { current =>
            current.revision shouldBe pendingstate.get.revision
            ()
          })
        }
        Given("the original typed partition judgment and valid original completion evidence")
        When("the real managed completion commits its selected closing and returns its declared response")
        val acknowledged = _business_value[V.SplitPhaseSemanticSubmissionResult](take(owner.executeOperationResponseInContext(
          request(component, "submitPartitionSemanticResult", reply), actor)))
        prepare(call => call.currentC(identity).map(value => { completionstate = Some(value); () }))
        Then("success reflects actual completion rather than a post-success unchanged-context proof")
        acknowledged.accepted shouldBe true
        acknowledged.sessionId shouldBe identity.value
        acknowledged.message should include("Accepted")
        changed shouldBe (if (mapped) 3 else 1)
        completionstate.get.revision shouldBe pendingstate.get.revision.next
        completionstate.get.suspension shouldBe empty
      }
      withRuntime(root, executionParameters = changedparameters) { (owner, component, actor, prepare) =>
        Given("the completed business result and compatible changed stored presentation")
        When("Advance consumes that result without restoring an old presentation copy")
        val preview = _business_value[V.SplitPhaseAdvanceResult](take(owner.executeOperationResponseInContext(
          request(component, "advanceSplitPhase", advance), actor)))
        Then("normal progression publishes the complete Preview")
        preview.kind shouldBe "Terminal"
        preview.issuedWorkContext shouldBe None
        preview.selectedPlan.get.mode shouldBe "PREVIEW"
        preview.selectedPlan.get.children.flatMap(_.orderedWorkItems.map(_.identity)) shouldBe
          facts.proposal.inventory.orderedUnfinishedItems.map(_.value)
        prepare(call => call.currentC(identity).map { current =>
          current.lifecycle shouldBe Lifecycle.Completed
          ()
        })
      }
    }
    }

  }
}
