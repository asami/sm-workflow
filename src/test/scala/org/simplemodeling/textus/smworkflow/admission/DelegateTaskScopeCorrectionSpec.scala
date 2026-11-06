package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Json, parser}
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.{NativeDelegateTaskStartInput, NativeDelegateTaskScopeCorrectionInput}
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import SplitPhaseExplicitPartitionFixture.take
import StandaloneFixExecution as S
import S.given

/** Public native operations and real managed persistence, without launching SBT.
  * Authority is specification-owned; this does not claim an actual Human event.
  * @since Oct. 30, 2026
  * @version Nov.  7, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class DelegateTaskScopeCorrectionSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  private val _added = Vector("project/SbtBridge.scala", "docs/contract.md")
  private val _contract = V.DelegateTaskContract("requester", "destination", "requester", "org.example.Component",
    "destination", "localized", NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"),
    Vector("remote-push"), NonEmptyVector.one(V.WorkflowArtifactReference("specification-human", "1")),
    None, Vector.empty, "Retain source and native results")
  private val _input = V.DelegateTaskInput("sm-workflow", "scope-correction-specification", "Correct the same SBT connection",
    NonEmptyVector.one("src/test/scala"), NonEmptyVector.of("compile", "focused"), NonEmptyVector.one("behavior"),
    NonEmptyVector.one(V.WorkflowArtifactReference("specification-request", "1")), false, "code", "off", 10,
    "direct-instruction", Vector.empty, _contract, Vector("behavior"))
  private val _checks = Vector(GoalPhaseNativeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile),
    GoalPhaseNativeValidation.Check("focused", 2, WorkflowSbtValidation.Kind.TestOnly))
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), kinds = TypedGitHostPlanFixture.observations :+ org.goldenport.cncf.processexecution.GitOperationKind.IndexTree)

  private def _state(view: V.DelegateTaskView): S.State = GoalPhaseNativeMetadata.normalizePublicJson(
    parser.parse(take(view.state.get.toJsonStringC)).toOption.get).toOption.get.as[S.State].toOption.get

  "Same-Session Task scope correction" should {
    "add same-purpose repair inventory while retaining failed checks and the existing repair allowance" in {
      Given("a SPECIFICATION-OWNED destination with retained failed checks and an unclaimed Repair checkpoint")
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/scope-correction").toAbsolutePath), "repair-").toRealPath()
      val original = ThreeWorkflowNormalHostFixture.definition(root)
      val host = original.copy(goal = original.goal.copy(candidatePaths = _input.ownedPaths.toVector ++ _added,
        stepValidation = original.goal.stepValidation.copy(checks = _checks)))
      _with_runtime_(root, "Equal", Some(host), BoundedWorkExecutionAdmissionFixture.parameters ++
        Map("textus.resource.url.file.roots" -> root.toString)) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "Equal").size))
        def _call_(operation: String, properties: List[Property]): Consequence[OperationResponse] =
          owner.executeOperationResponseInContext(Request.of(component = component.name, service = "DelegateTaskService",
            operation = operation, properties = properties), actor)
        def _value_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          read_value[A](take(_call_(operation, properties)))
        def _import_selection_(prepared: Record): Unit = prepare { setup =>
          val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        }
        val options = Map("input" -> "input.json", "selection-id" -> "repair-initial", "participant" -> "participant",
          "origin" -> "ExplicitHuman", "idempotency-key" -> "repair-initial-key", "selection-collection" -> "entity:smworkflow.cli.goal_selection")
        _import_selection_(take(NativeDelegateTaskStartInput.prepareC(options, _ => _input.toRecord().toJsonStringC)))
        val started = _value_[V.DelegateTaskView]("startDelegateTask", List(Property("input", _input, None),
          Property("selectionId", "repair-initial", None), Property("participant", "participant", None), Property("idempotencyKey", "repair-initial-key", None)))
        // This is fixture preparation through the native checkpoint owner, not
        // an observation of real SBT or a mutation of any consumer Session.
        prepare { call => for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          h = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main", host.goal.candidatePaths, _checks,
            None, None, None, None, None)
          store <- DelegateTaskNativeRun.storageC(h, definition, call.core)
          instances <- store.bindInstancesC()(using call.core.executionContext)
          current <- instances.loadC(org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity(started.sessionId))(using call.core.executionContext)
            .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("Fixture Start is absent")))
          payload <- DelegateTaskNativeRun.loadPayloadC(store, started.sessionId, current.revision.value, call.core)
          observed <- call.observeC()
          failed = org.simplemodeling.textus.smworkflow.admission.RepositorySyncNativeMergeValidation.Receipt(
            "focused", "exited", Some(1), "SPECIFICATION-OWNED retained check failure", "")
          repair = payload.state.copy(stage = "Repair", generation = 3, automaticCount = 2, ordinaryCount = 1,
            candidate = Some(S.Candidate(observed.head, observed.tree, Vector("src/test/scala/Repair.scala"), "fixture candidate")),
            receipts = Vector(failed), semanticReference = Some(ContextReference("retained-implementation", "1")),
            findings = Vector("check:focused:failed"), repairControl = payload.state.repairControl.observe(
              "fixture-repair", "retained-failure", Vector("check:focused:failed")))
          _ <- DelegateTaskNativeRun.saveC(h, store, current, payload.copy(state = repair), call.core)
        } yield () }
        val partial = root.resolve("repository/src/test/scala/Repair.scala")
        Files.createDirectories(partial.getParent)
        Files.writeString(partial, "// retained same-purpose repair in progress\n")
        def _advance_() = _value_[V.DelegateTaskView]("advanceDelegateTask", List(Property("sessionId", started.sessionId, None)))
        val old = _advance_()
        val before = _state(old)
        val continuation = old.issuedWorkContext.get.continuationId
        When("the same pending Repair is described with necessary additional literal paths")
        val description = _value_[V.DelegateTaskScopeCorrectionView]("describeDelegateTaskScopeCorrection",
          List(Property("input", V.DelegateTaskScopeCorrectionDescriptionInput(started.sessionId, continuation, _added, Vector.empty), None)))
        Then("the public description is available while the repair and all its facts remain pending")
        old.work.get.getString("kind") shouldBe Some("Repair")
        description.outcome shouldBe "decision-required"
        _advance_().issuedWorkContext shouldBe old.issuedWorkContext
        When("a condition timing change is proposed through the Repair inventory route")
        val timing = _call_("describeDelegateTaskScopeCorrection", List(Property("input",
          V.DelegateTaskScopeCorrectionDescriptionInput(started.sessionId, continuation, _added, Vector("behavior")), None)))
        Then("inventory permission does not expand to changing the initial condition timing")
        timing.isSuccess shouldBe false
        Given("the exact correction selected independently by the fixture Human")
        val correction = V.DelegateTaskScopeCorrectionInput(started.sessionId, continuation, description.target,
          "repair-inventory", "participant", "repair-inventory-key", V.WorkflowArtifactReference("fixture-repair-inventory-human", "1"))
        _import_selection_(take(NativeDelegateTaskScopeCorrectionInput.prepareC(options ++ Map(
          "selection-id" -> correction.selectionId, "idempotency-key" -> correction.idempotencyKey),
          _ => correction.toRecord().toJsonStringC)))
        val correctionfile = root.resolve("repair-inventory.json")
        Files.writeString(correctionfile, take(correction.toRecord().toJsonStringC))
        When("the correction is applied and normal Advance issues the successor Repair")
        val corrected = _value_[V.DelegateTaskScopeCorrectionView]("correctDelegateTaskScope",
          List(Property("inputResource", correctionfile.toUri.toASCIIString, None)))
        val next = _advance_()
        val after = _state(next)
        Then("the same Session preserves its original repair state and adds only the selected inventory and provenance")
        corrected.outcome shouldBe "corrected-awaiting-advance"
        next.sessionId shouldBe old.sessionId
        next.work.get.getString("kind") shouldBe Some("Repair")
        next.issuedWorkContext.get.continuationId should not be continuation
        after.input.ownedPaths shouldBe before.input.ownedPaths ++ _added
        after.input.conditions shouldBe before.input.conditions
        after.input.submissionConditions shouldBe before.input.submissionConditions
        after.input.sourceReferences should contain(ContextReference(correction.reference.identity, correction.reference.revision))
        after.copy(input = before.input) shouldBe before
        after.automaticCount shouldBe 2
        after.ordinaryCount shouldBe 1
        after.repairControl.selectedLimit shouldBe 10
        Files.readString(partial) shouldBe "// retained same-purpose repair in progress\n"
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      }
    }

    "retain immutable issued work and partial source, admit the exact correction, and continue without executing checks" in {
      Given("an isolated native destination with an unclaimed initial implementation.")
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/scope-correction").toAbsolutePath), "episode-").toRealPath()
      val original = ThreeWorkflowNormalHostFixture.definition(root)
      val host = original.copy(goal = original.goal.copy(candidatePaths = _input.ownedPaths.toVector ++ _added,
        stepValidation = original.goal.stepValidation.copy(checks = _checks)))
      _with_runtime_(root, "Equal", Some(host), BoundedWorkExecutionAdmissionFixture.parameters) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "Equal").size))
        def _call_(operation: String, properties: List[Property]): Consequence[OperationResponse] =
          owner.executeOperationResponseInContext(Request.of(component = component.name, service = "DelegateTaskService",
            operation = operation, properties = properties), actor)
        def _value_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          read_value[A](take(_call_(operation, properties)))
        def _import_selection_(prepared: Record): Unit = prepare { setup =>
          val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        }
        val options = Map("input" -> "input.json", "selection-id" -> "initial-selection", "participant" -> "participant",
          "origin" -> "ExplicitHuman", "idempotency-key" -> "initial-key", "selection-collection" -> "entity:smworkflow.cli.goal_selection")
        _import_selection_(take(NativeDelegateTaskStartInput.prepareC(options, _ => _input.toRecord().toJsonStringC)))
        val started = _value_[V.DelegateTaskView]("startDelegateTask", List(Property("input", _input, None),
          Property("selectionId", "initial-selection", None), Property("participant", "participant", None), Property("idempotencyKey", "initial-key", None)))
        def _advance_() = _value_[V.DelegateTaskView]("advanceDelegateTask", List(Property("sessionId", started.sessionId, None)))
        val old = _advance_()
        val before = _state(old)
        val continuation = old.issuedWorkContext.get.continuationId
        val partial = root.resolve("repository/src/test/scala/Partial.scala")
        Files.createDirectories(partial.getParent)
        Files.writeString(partial, "// retained partial implementation\n")
        When("the public operation describes this exact still-pending issue.")
        val description = _value_[V.DelegateTaskScopeCorrectionView]("describeDelegateTaskScopeCorrection",
          List(Property("input", V.DelegateTaskScopeCorrectionDescriptionInput(started.sessionId, continuation,
            _added, Vector.empty), None)))
        Then("description alone grants no correction or execution authority.")
        description.outcome shouldBe "decision-required"
        _advance_().issuedWorkContext shouldBe old.issuedWorkContext
        val correction = V.DelegateTaskScopeCorrectionInput(started.sessionId, continuation, description.target,
          "correction-selection", "participant", "correction-key", V.WorkflowArtifactReference("specification-correction-human", "1"))
        When("correction is requested without an independently selected Human input")
        val unselected = _call_("correctDelegateTaskScope", List(Property("input", correction, None)))
        Then("description alone cannot retire the pending issue")
        unselected.isSuccess shouldBe false
        Given("the independently prepared SPECIFICATION-OWNED selection for this exact correction")
        val correctionoptions = options ++ Map("selection-id" -> correction.selectionId, "idempotency-key" -> correction.idempotencyKey)
        _import_selection_(take(NativeDelegateTaskScopeCorrectionInput.prepareC(correctionoptions, _ => correction.toRecord().toJsonStringC)))
        val target = take(DelegateTaskScopeCorrection.targetC(description.target))
        val different = correction.copy(target = {
          import io.circe.syntax.*
          target.copy(additionalOwnedPaths = Vector("docs/contract.md")).asJson.noSpaces
        })
        When("different additional paths attempt to reuse the selected correction")
        val crossedpaths = _call_("correctDelegateTaskScope", List(Property("input", different, None)))
        Then("the crossed path selection is refused")
        crossedpaths.isSuccess shouldBe false
        When("the independently prepared SPECIFICATION-OWNED Human selection is admitted.")
        val corrected = _value_[V.DelegateTaskScopeCorrectionView]("correctDelegateTaskScope", List(Property("input", correction, None)))
        Then("supersession is append-only and ordinary Advance issues the same-Session successor.")
        corrected.outcome shouldBe "corrected-awaiting-advance"
        When("the exact correction is repeated and ordinary Advance issues its successor")
        val repeated = _value_[V.DelegateTaskScopeCorrectionView]("correctDelegateTaskScope", List(Property("input", correction, None)))
        val next = _advance_()
        Then("repetition is idempotent and the new issue retains the same Session and original state")
        repeated.outcome shouldBe "already-corrected"
        next.sessionId shouldBe old.sessionId
        next.issuedWorkContext.get.continuationId should not be continuation
        val after = _state(next)
        after.input.ownedPaths shouldBe before.input.ownedPaths ++ _added
        after.copy(input = before.input) shouldBe before
        after.input.copy(ownedPaths = before.input.ownedPaths, sourceReferences = before.input.sourceReferences) shouldBe before.input
        after.input.sourceReferences should contain (ContextReference(correction.reference.identity, correction.reference.revision))
        Files.readString(partial) shouldBe "// retained partial implementation\n"
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        When("Advance recollects the successor and the old exact correction is repeated again")
        val recollected = _advance_()
        val repeatedafter = _value_[V.DelegateTaskScopeCorrectionView]("correctDelegateTaskScope", List(Property("input", correction, None)))
        Then("the successor binding and committed correction remain unchanged")
        recollected.issuedWorkContext shouldBe next.issuedWorkContext
        repeatedafter.outcome shouldBe "already-corrected"
        Given("a caller without the independently admitted participant context")
        val untrusted = org.goldenport.cncf.context.ExecutionContext.withSecurityContext(actor, actor.security.copy())
        When("that caller describes the retained correction")
        val untrustedresult = owner.executeOperationResponseInContext(Request.of(component = component.name, service = "DelegateTaskService",
          operation = "describeDelegateTaskScopeCorrection", properties = List(Property("input",
            V.DelegateTaskScopeCorrectionDescriptionInput(started.sessionId, continuation,
              _added, Vector.empty), None))), untrusted)
        Then("the untrusted description is refused")
        untrustedresult.isSuccess shouldBe false
        Given("a SPECIFICATION-OWNED result for the original adopted behavior condition")
        val result = V.DelegateTaskWorkResult("completed", "Stale issue", Vector.empty,
          NonEmptyVector.one(V.DelegateTaskCondition("behavior", true, "specification")), Vector.empty, None, None)
        val properties = List(Property("sessionId", started.sessionId, None),
          Property("result", result, None),
          Property("resultReference", V.WorkflowArtifactReference("specification-result", "1"), None),
          Property("executionEvidence", V.WorkflowExecutionEvidence(Vector(V.WorkflowArtifactReference("specification-result", "1")),
            Some("specification-implementer"), None, Some(V.WorkflowExecutionObservation("INLINE",
              BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity,
              BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity,
              BoundedWorkExecutionAdmissionFixture.profile.identity))), None))
        When("the retired worker or an unbound worker attempts to complete the successor")
        val stale = _call_("submitDelegateTaskWorkResult", properties :+ Property("issuedWorkContext", old.issuedWorkContext.get, None))
        val unbound = _call_("submitDelegateTaskWorkResult", properties)
        Then("both stale and missing issued bindings are refused")
        stale.isSuccess shouldBe false
        unbound.isSuccess shouldBe false
        When("the result uses the actual successor issued binding")
        val submitted = _call_("submitDelegateTaskWorkResult", properties :+ Property("issuedWorkContext", next.issuedWorkContext.get, None))
        Then("only that current submission is retained without executing SBT")
        submitted.isSuccess shouldBe true
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      }
    }

    "admit evaluation timing through the same public Session without replacing its conditions or running SBT" in {
      Given("the original two obligations and an unsubmitted SPECIFICATION-OWNED destination issue.")
      val input = _input.copy(conditions = NonEmptyVector.of("behavior", "delivery"), submissionConditions = Vector("behavior", "delivery"))
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/scope-correction").toAbsolutePath), "episode-").toRealPath()
      val original = ThreeWorkflowNormalHostFixture.definition(root)
      val host = original.copy(goal = original.goal.copy(candidatePaths = input.ownedPaths.toVector ++ _added,
        stepValidation = original.goal.stepValidation.copy(checks = _checks)))
      _with_runtime_(root, "Equal", Some(host), BoundedWorkExecutionAdmissionFixture.parameters) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "Equal").size))
        def _call_(operation: String, properties: List[Property]): Consequence[OperationResponse] =
          owner.executeOperationResponseInContext(Request.of(component = component.name, service = "DelegateTaskService",
            operation = operation, properties = properties), actor)
        def _value_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          read_value[A](take(_call_(operation, properties)))
        def _import_selection_(prepared: Record): Unit = prepare { setup =>
          val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        }
        val options = Map("input" -> "input.json", "selection-id" -> "initial-selection", "participant" -> "participant",
          "origin" -> "ExplicitHuman", "idempotency-key" -> "initial-key", "selection-collection" -> "entity:smworkflow.cli.goal_selection")
        _import_selection_(take(NativeDelegateTaskStartInput.prepareC(options, _ => input.toRecord().toJsonStringC)))
        val started = _value_[V.DelegateTaskView]("startDelegateTask", List(Property("input", input, None),
          Property("selectionId", "initial-selection", None), Property("participant", "participant", None), Property("idempotencyKey", "initial-key", None)))
        def _advance_() = _value_[V.DelegateTaskView]("advanceDelegateTask", List(Property("sessionId", started.sessionId, None)))
        val old = _advance_()
        val before = _state(old)
        val continuation = old.issuedWorkContext.get.continuationId
        When("the public description selects the original behavior ID for submission.")
        val description = _value_[V.DelegateTaskScopeCorrectionView]("describeDelegateTaskScopeCorrection",
          List(Property("input", V.DelegateTaskScopeCorrectionDescriptionInput(started.sessionId, continuation,
            Vector.empty, Vector("behavior")), None)))
        Given("the exact described timing correction awaiting independently selected current-call authority")
        val correction = V.DelegateTaskScopeCorrectionInput(started.sessionId, continuation, description.target,
          "timing-selection", "participant", "timing-key", V.WorkflowArtifactReference("specification-timing-human", "1"))
        When("the timing correction is requested before its Human selection is admitted")
        val unselected = _call_("correctDelegateTaskScope", List(Property("input", correction, None)))
        Then("passive description cannot authorize a timing change")
        unselected.isSuccess shouldBe false
        Given("the independently prepared SPECIFICATION-OWNED Human timing selection")
        _import_selection_(take(NativeDelegateTaskScopeCorrectionInput.prepareC(options ++ Map(
          "selection-id" -> correction.selectionId, "idempotency-key" -> correction.idempotencyKey),
          _ => correction.toRecord().toJsonStringC)))
        val target = take(DelegateTaskScopeCorrection.targetC(description.target))
        import io.circe.syntax.*
        val crossed = correction.copy(target = target.copy(submissionConditions = Some(Vector("delivery"))).asJson.noSpaces)
        When("a different timing subset is offered against the exact selection.")
        val refused = _call_("correctDelegateTaskScope", List(Property("input", crossed, None)))
        Then("only the independently selected exact subset can supersede the issue.")
        refused.isSuccess shouldBe false
        When("the exact selected correction is applied and ordinary Advance recollects it.")
        val corrected = _value_[V.DelegateTaskScopeCorrectionView]("correctDelegateTaskScope", List(Property("input", correction, None)))
        Then("the original issue is retired without changing the Session or consuming repair counts.")
        corrected.outcome shouldBe "corrected-awaiting-advance"
        When("the exact timing correction is repeated and ordinary Advance issues the successor")
        val repeated = _value_[V.DelegateTaskScopeCorrectionView]("correctDelegateTaskScope", List(Property("input", correction, None)))
        val next = _advance_()
        Then("repetition is idempotent and every condition identity and prior state is retained")
        repeated.outcome shouldBe "already-corrected"
        next.sessionId shouldBe old.sessionId
        next.issuedWorkContext.get.continuationId should not be continuation
        val after = _state(next)
        after.input.conditions shouldBe before.input.conditions
        after.input.submissionConditions shouldBe Some(Vector("behavior"))
        after.input.copy(submissionConditions = before.input.submissionConditions) shouldBe before.input
        after.copy(input = before.input, evidenceReferences = before.evidenceReferences) shouldBe before
        after.evidenceReferences should contain (ContextReference(correction.reference.identity, correction.reference.revision))
        after.conditionReport shouldBe before.conditionReport
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        When("Advance and both exact correction operations recollect the retained successor")
        val recollected = _advance_()
        val describedagain = _value_[V.DelegateTaskScopeCorrectionView]("describeDelegateTaskScopeCorrection",
          List(Property("input", V.DelegateTaskScopeCorrectionDescriptionInput(started.sessionId, continuation,
            Vector.empty, Vector("behavior")), None)))
        val correctedagain = _value_[V.DelegateTaskScopeCorrectionView]("correctDelegateTaskScope",
          List(Property("input", correction, None)))
        Then("the exact issue and correction remain unchanged")
        recollected.issuedWorkContext shouldBe next.issuedWorkContext
        describedagain.outcome shouldBe "already-corrected"
        correctedagain.outcome shouldBe "already-corrected"
        When("a fresh timing description is requested against the exact current successor")
        val replacement = _call_("describeDelegateTaskScopeCorrection", List(Property("input",
          V.DelegateTaskScopeCorrectionDescriptionInput(started.sessionId, next.issuedWorkContext.get.continuationId,
            Vector.empty, Vector("delivery")), None)))
        Then("the changed subset is described without granting authority or changing its current issue")
        replacement.isSuccess shouldBe true
        val replacementdescription = read_value[V.DelegateTaskScopeCorrectionView](take(replacement))
        val replacementtarget = take(DelegateTaskScopeCorrection.targetC(replacementdescription.target))
        replacementtarget.business.state.input shouldBe after.input
        replacementtarget.submissionConditions shouldBe Some(Vector("delivery"))
        val unselectedreplacement = correction.copy(oldContinuationId = next.issuedWorkContext.get.continuationId,
          target = replacementdescription.target, selectionId = "unselected-replacement", idempotencyKey = "unselected-replacement-key")
        When("the fresh changed timing is submitted without its own exact current Human selection")
        val unauthorized = _call_("correctDelegateTaskScope", List(Property("input", unselectedreplacement, None)))
        Then("the current pending issue remains unchanged")
        unauthorized.isSuccess shouldBe false
        _advance_().issuedWorkContext shouldBe next.issuedWorkContext
      }
    }

    "retain later obligations without certifying them at implementation submission or review" in {
      Given("the original adopted destination contract.")
      val original = take(DelegateTask.inputC(_input)).copy(conditions = Vector("behavior", "delivery"), submissionConditions = None)
      When("only the original behavior obligation is selected for submission.")
      val planned = take(DelegateTaskScopeCorrection.correctedInputC(original, Vector.empty, Some(Vector("behavior"))))
      Then("every original obligation remains adopted.")
      planned.copy(submissionConditions = None) shouldBe original
      Given("the selected destination and a report that leaves the later delivery obligation false")
      val before = take(S.startC(planned, "destination", _checks))
      val pending = Vector(S.Condition("behavior", true, "Implementation submitted for normal qualification"),
        S.Condition("delivery", false, "Await actual validation, publication and old-engineering application"))
      val completed = S.Result("completed", "Implementation only", Vector.empty, pending, Vector.empty)
      When("the initial implementation submits only the selected stage-local satisfaction")
      val submitted = take(S.completedC(S.Work("same-session", "1", "Implement", before), completed,
        ContextReference("implementation-result", "1")))
      Then("implementation is retained for qualification without completing later delivery or consuming repair counts")
      submitted.stage shouldBe "Inspect"
      submitted.conditionReport.last.satisfied shouldBe false
      submitted.generation shouldBe before.generation
      submitted.ordinaryCount shouldBe before.ordinaryCount
      val premature = completed.copy(conditions = pending.map(_.copy(satisfied = true)))
      When("implementation prematurely claims every condition satisfied")
      val prematureimplementation = S.admitResultC(S.Work("same-session", "1", "Implement", before), premature)
      Then("premature implementation acceptance is refused")
      prematureimplementation.isSuccess shouldBe false
      Given("the same pending conditions at the independent review stage")
      val review = submitted.copy(stage = "Review")
      When("review evaluates a truthful stage-local report and a premature all-true report")
      val acceptedreview = S.admitResultC(S.Work("same-session", "2", "Review", review), completed.copy(outcome = "accept"))
      val prematurereview = S.admitResultC(S.Work("same-session", "2", "Review", review), premature.copy(outcome = "accept"))
      Then("only the stage-local review report is admitted")
      acceptedreview.isSuccess shouldBe true
      prematurereview.isSuccess shouldBe false
      Given("the SPECIFICATION-OWNED late-delivery semantic stage")
      val delivered = submitted.copy(stage = "DeliveryAcceptance", commit = Some(S.Commit("commit", "parent", "tree")),
        delivery = Some(DelegateTask.Delivery()))
      val work = S.Work("same-session", "3", "DeliveryAcceptance", delivered)
      When("late acceptance lacks either satisfied final conditions or a retained commit")
      val incomplete = S.admitResultC(work, completed.copy(outcome = "accept"))
      val uncommitted = S.admitResultC(work.copy(state = delivered.copy(commit = None)), premature.copy(outcome = "accept"))
      Then("both incomplete final claims are refused")
      incomplete.isSuccess shouldBe false
      uncommitted.isSuccess shouldBe false
      When("even an all-true final report has no retained successful checks or matching committed candidate")
      val unevidenced = S.admitResultC(work, premature.copy(outcome = "accept"))
      Then("truthful stage-local submission cannot fabricate completed delivery")
      unevidenced.isSuccess shouldBe false
      When("a fresh current business input selects changed ordered timing including empty and full subsets")
      val changed = Vector(
        DelegateTaskScopeCorrection.correctedInputC(original, Vector.empty, Some(Vector.empty)),
        DelegateTaskScopeCorrection.correctedInputC(original, Vector.empty, Some(original.conditions)),
        DelegateTaskScopeCorrection.correctedInputC(planned, Vector.empty, Some(Vector("delivery"))))
      Then("changed timing is model-valid without changing the original request conditions or authority")
      changed.foreach { result =>
        val current = take(result)
        current.copy(submissionConditions = original.submissionConditions) shouldBe original
      }
      When("unknown IDs, duplicate or reversed IDs, or the same unchanged timing without added paths are proposed")
      val invalidcorrections = Vector(
        DelegateTaskScopeCorrection.correctedInputC(original, Vector.empty, Some(Vector("not-adopted"))),
        DelegateTaskScopeCorrection.correctedInputC(planned, Vector.empty, planned.submissionConditions),
        DelegateTaskScopeCorrection.correctedInputC(planned, Vector.empty, None))
      val invalidinputs = Vector(Vector("behavior", "behavior"), Vector("delivery", "behavior"), Vector("foreign")).map { ids =>
        S.inputC(original.copy(submissionConditions = Some(ids)))
      }
      Then("every invalid or no-op timing proposal is refused")
      invalidcorrections.foreach(result => result.isSuccess shouldBe false)
      invalidinputs.foreach(result => result.isSuccess shouldBe false)
      import io.circe.syntax.*
      When("an original input without an explicit plan is encoded using its historical wire representation")
      val historical = original.asJson
      Then("the optional timing field is omitted and the historical input decodes unchanged")
      historical.hcursor.downField("submissionConditions").succeeded shouldBe false
      historical.as[S.Input].toOption.get shouldBe original
    }

    "retain ordinary all-true requester and validation-only admission when no destination timing plan is adopted" in {
      Given("an unchanged ordinary destination and its original requester.")
      val original = take(DelegateTask.inputC(_input)).copy(conditions = Vector("behavior", "delivery"), submissionConditions = None)
      val initial = take(S.startC(original, "participant", _checks))
      val report = original.conditions.map(id => S.Condition(id, true, "SPECIFICATION-OWNED original obligation"))
      val result = S.Result("completed", "SPECIFICATION-OWNED ordinary completion", Vector.empty, report, Vector.empty)
      val requester = original.copy(delegation = original.delegation.map(_.copy(role = "requester")))
      val validation = S.Input("sm-workflow", "validation-only", "Qualify selected checks", Vector("src"),
        Vector("focused"), Vector("behavior"), original.sourceReferences, false, kind = "validation", scalaLint = "off")
      When("an ordinary completion is admitted without an explicit timing plan.")
      val admitted = S.admitResultC(S.Work("ordinary", "1", "Implement", initial), result)
      val incomplete = S.admitResultC(S.Work("ordinary", "1", "Implement", initial),
        result.copy(conditions = report.updated(1, report(1).copy(satisfied = false))))
      Then("all-true remains mandatory and the requester remains passive.")
      admitted.isSuccess shouldBe true
      incomplete.isSuccess shouldBe false
      When("the original requester starts with its complete passive destination contract")
      val passive = take(DelegateTask.startRequesterC(requester, "participant"))
      Then("the requester remains at DispatchReady without executable or semantic work")
      passive.stage shouldBe "DispatchReady"
      passive.selected shouldBe Vector.empty
      passive.semanticReference shouldBe None
      When("a requester attempts to adopt destination-only condition timing")
      val requestertiming = DelegateTaskScopeCorrection.correctedInputC(requester, Vector.empty, Some(Vector("behavior")))
      Then("the timing correction is refused for the requester")
      requestertiming.isSuccess shouldBe false
      When("validation-only work attempts to claim semantic completion or timing.")
      val qualified = take(S.startC(validation, "participant", Vector(_checks.last)))
      val semantic = S.admitResultC(S.Work("validation", "1", "Repair", qualified.copy(stage = "Repair")), result)
      val timing = S.inputC(validation.copy(submissionConditions = Some(Vector("behavior"))))
      Then("qualification cannot become implementation, repair, review or delegation.")
      semantic.isSuccess shouldBe false
      timing.isSuccess shouldBe false
    }

    "finish late acceptance only with every final obligation and the retained source checks review commit and publication" in {
      Given("SPECIFICATION-OWNED typed observations, not actual consumer or SBT facts.")
      import WorkflowSbtValidation as T
      import RepositorySyncNativeMergeValidation.Receipt
      import io.circe.syntax.*
      val original = take(DelegateTask.inputC(_input)).copy(conditions = Vector("behavior", "delivery"), reviewRequired = true, submissionConditions = None,
        delegation = Some(take(DelegateTask.inputC(_input)).delegation.get.copy(publicationTarget = Some("fixture-publication"),
          permittedOperations = _contract.permittedOperations.toVector :+ "publishLocal", artifactCoordinates = Vector("org.example:fixture:0.0.1-SNAPSHOT"))))
      val planned = take(DelegateTaskScopeCorrection.correctedInputC(original, Vector.empty, Some(Vector("behavior"))))
      val initial = take(S.startC(planned, "participant", _checks))
      val pending = Vector(S.Condition("behavior", true, "SPECIFICATION-OWNED submitted behavior"),
        S.Condition("delivery", false, "Await retained qualification and actual delivery evidence"))
      val implementation = S.Result("completed", "SPECIFICATION-OWNED implementation", Vector.empty, pending, Vector.empty)
      val submitted = take(S.completedC(S.Work("late-gate", "1", "Implement", initial), implementation,
        ContextReference("SPECIFICATION-OWNED-implementation", "1")))
      val candidate = S.Candidate("a" * 40, "b" * 40, Vector("src/test/scala/BehaviorSpec.scala"), "SPECIFICATION-OWNED selected diff")
      When("the ordinary selected check batch and independent review complete.")
      val inspected = take(S.candidateC(submitted, candidate))
      val observed = _checks.foldLeft(inspected) { (current, check) =>
        val facts = T.Facts(check.kind, T.Outcome.Succeeded,
          Option.when(check.kind == T.Kind.TestOnly)(T.Selection(Vector("example.BehaviorSpec"), 1, 1L)), None)
        take(S.observedC(take(S.intentC(current)), Receipt(check.identity, "exited", Some(0), "", "", Some(facts))))
      }
      val reviewed = take(S.completedC(S.Work("late-gate", "2", "Review", take(S.assessC(observed))),
        implementation.copy(outcome = "accept"), ContextReference("SPECIFICATION-OWNED-independent-review", "1")))
      Then("later obligations remain false until the actual retained closure is available.")
      reviewed.stage shouldBe "CommitReady"
      reviewed.conditionReport shouldBe pending
      Given("a retained SPECIFICATION-OWNED selected commit and ready publication for the same source")
      val committed = take(S.committedC(reviewed.copy(stage = "CommitIntent"),
        S.Commit("c" * 40, candidate.head, candidate.tree)))
      val target = LocalPublication.Target("fixture-publication", _contract.componentIdentity, "sm-workflow-repository")
      val request = LocalPublication.Request(LocalPublication.Input("fixture-publication-request", target.identity), target,
        LocalPublication.Source(committed.commit.get.identity, candidate.tree, Vector.empty), "participant",
        "SPECIFICATION-OWNED retained publication", None, Vector.empty)
      val publication = LocalPublication.Result("published", "SPECIFICATION-OWNED typed publication outcome", request)
      val delivered = committed.copy(stage = "DeliveryAcceptance", delivery = Some(DelegateTask.Delivery(
        destinationSessionId = Some("late-gate"), publication = Some(publication))))
      val work = S.Work("late-gate", "3", "DeliveryAcceptance", delivered)
      val finalresult = implementation.copy(outcome = "accept", conditions = pending.map(_.copy(satisfied = true)))
      When("incomplete, crossed or fabricated final claims are submitted.")
      val invalid = Vector(finalresult.copy(conditions = pending), finalresult.copy(conditions = finalresult.conditions.take(1)),
        finalresult.copy(conditions = finalresult.conditions.updated(1, S.Condition("fabricated", true, "Not adopted"))),
        finalresult.copy(outcome = "repair", findings = Vector("May not reopen source repair")))
      val invalidadmissions = invalid.map(result => S.admitResultC(work, result))
      val invalidclosures = Vector(
        S.admitResultC(work.copy(state = delivered.copy(commit = None)), finalresult),
        S.admitResultC(work.copy(state = delivered.copy(delivery = Some(DelegateTask.Delivery()))), finalresult),
        S.admitResultC(work.copy(state = delivered.copy(delivery = delivered.delivery.map(_.copy(
          publication = Some(publication.copy(status = "uncertain")))))), finalresult),
        S.admitResultC(work.copy(state = delivered.copy(delivery = delivered.delivery.map(_.copy(
          publication = Some(publication.copy(request = request.copy(source = request.source.copy(head = "d" * 40)))))))), finalresult))
      Then("none can close or replace the retained delivery obligations.")
      invalidadmissions.foreach(result => result.isSuccess shouldBe false)
      invalidclosures.foreach(result => result.isSuccess shouldBe false)
      When("every final obligation is accepted against the original retained delivery.")
      val accepted = take(S.completedC(work, finalresult, ContextReference("SPECIFICATION-OWNED-final-evidence", "1")))
      val expected = original.copy(delegation = original.delegation.map(_.copy(role = "requester")))
      val returned = take(DelegateTask.returnedC(expected, accepted.asJson))
      Then("only the final report and evidence append change; return still checks source and qualification.")
      returned.stage shouldBe "ReturnReady"
      accepted.copy(stage = delivered.stage, conditionReport = delivered.conditionReport,
        evidenceReferences = delivered.evidenceReferences, message = delivered.message) shouldBe delivered
      accepted.priorReview shouldBe delivered.priorReview
      accepted.priorReviewCandidate shouldBe delivered.priorReviewCandidate
      accepted.receipts shouldBe delivered.receipts
      accepted.automaticCount shouldBe initial.automaticCount
      accepted.ordinaryCount shouldBe initial.ordinaryCount
      accepted.generation shouldBe initial.generation
      S.deliveryPending(accepted) shouldBe false
      When("return is attempted without retained checks or required review or against a different purpose")
      val refusedreturns = Vector(
        DelegateTask.returnedC(expected, accepted.copy(receipts = Vector.empty).asJson),
        DelegateTask.returnedC(expected, accepted.copy(reviewReference = None).asJson),
        DelegateTask.returnedC(expected.copy(objective = "Crossed purpose"), accepted.asJson))
      Then("each incomplete or crossed return remains refused")
      refusedreturns.foreach(result => result.isSuccess shouldBe false)
    }

    "refuse replacement paths, exclusions and unrelated purpose changes in the correction payload" in {
      Given("the original literal ownership and exclusion contract.")
      val original = take(DelegateTask.inputC(_input))
      val excluded = original.copy(delegation = original.delegation.map(_.copy(exclusions = Vector("project"))))
      When("an empty correction, replacement, escaped path or excluded path is proposed.")
      val attempts = Vector(
        DelegateTaskScopeCorrection.correctedInputC(original, Vector.empty),
        DelegateTaskScopeCorrection.correctedInputC(original, Vector("src/test/scala")),
        DelegateTaskScopeCorrection.correctedInputC(original, Vector("../outside")),
        DelegateTaskScopeCorrection.correctedInputC(excluded, Vector("project/SbtBridge.scala")))
      Then("every proposal is refused without widening the original purpose or authority.")
      attempts.foreach(attempt => attempt.isSuccess shouldBe false)
    }
  }
}
