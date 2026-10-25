package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.Request
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.*
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseDependencyValidation as D
import GoalPhaseDependencyValidation.given
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeValidation as V

/** Business fixtures never supply actual Human consent, Git observations or effect permission.
  * @since   Oct. 24, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object DependencyValidationSpecificationFixture {
  val requester = GoalPhaseDependencyScope.Component("org.simplemodeling.control", "control-center", "primary-source")
  val dependency = GoalPhaseDependencyScope.Component("org.goldenport.cncf", "goldenport-cncf", "cncf-source")
  val base = O.Snapshot("a" * 40, "b" * 40)
  val primaryBase = O.Snapshot("c" * 40, "d" * 40)
  val paths = Vector("src/main/scala/Observation.scala", "src/main/scala/UnitOfWorkProgramPlanning.scala")
  val check = V.Check("cncf-compile", 2, WorkflowSbtValidation.Kind.Compile)
  val binding = D.Binding("cncf-linked-validation", 1, "step-1", requester, dependency,
    "/specification/workspaces/cncf", "main", paths, base,
    "SPECIFICATION-OWNED adoption of existing work against this explicit Git base, not Start provenance", Vector(check))
  val approvals = paths.zipWithIndex.map { (path, index) =>
    val proposal = GoalPhaseDependencyScope.Proposal("approved-unit-" + index, "1", "IMPLEMENTATION",
      requester, dependency, ContextReference("linked-api", "1"), "SPECIFICATION-OWNED exact linked unit",
      Vector(path), Vector("Preserve existing callers"), Vector(ContextReference("step-1", "1")), Vector.empty)
    D.Approval("step-1", proposal, ContextReference("specification-answer-" + index, "1"))
  }
  val selection = D.Selection(ContextReference(binding.identity, "1"), approvals.map(a =>
    D.ApprovalReference(ContextReference(a.proposal.identity, a.proposal.revision), a.decision)))
  val admission = D.Admission(binding, approvals)
  val plan = O.Plan("selected-plan", "1", "5.1", requester.repository,
    Vector(O.Step("step-1", "Implement selected API", Vector("screen.scala"), Vector.empty, Vector("primary-test"))),
    Vector("primary-test"))
  val request = GoalPhaseNativeWork.Request("same-session", "18", "ImplementStep", Some("step-1"), 1,
    "SPECIFICATION-OWNED issued work", plan, primaryBase, Vector.empty, None, None, None)
  val capture = D.Capture("same-session", ContextReference(plan.identity, plan.revision), "step-1", 1, "20",
    primaryBase, admission, O.Snapshot(base.head, "e" * 40), paths, "diff --git a/Observation.scala b/Observation.scala\n+observed\n")
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseDependencyValidationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DependencyValidationSpecificationFixture.*

  "Public result constructor migration" should {
    "allow reference-only results and reject every raw enriched admission presence before DTO conversion" in {
      Given("a strict named public result input with ordinary reference-only dependency selection")
      val plain = Json.obj("outcome" -> "completed".asJson, "summary" -> "Specification completion".asJson,
        "findings" -> Json.arr(), "dependencyValidation" -> selection.asJson)
      val forbidden = Vector(
        plain.mapObject(_.add("dependencyAdmission", admission.asJson)),
        plain.mapObject(_.add("dependencyAdmission", Json.Null)),
        plain.mapObject(_.add("dependency_admission", admission.asJson)),
        plain.mapObject(_.add("dependency_admission", Json.Null)),
        plain.mapObject(_.add("dependencyAdmission", admission.asJson).add("dependency_admission", Json.Null)))
      def _validate_(value: Any) = {
        val request = Request.ofOperation("specificationSubmission").copy(
          properties = List(org.goldenport.protocol.Property("result", value, None)))
        NativeWorkflowOperationInput.recordC(request, "result").flatMap(D.validatePublicResultC)
      }
      When("the strict named business intake consumes Json and String results before the generated reader")
      val accepted = Vector(_validate_(plain), _validate_(plain.noSpaces))
      val rejected = forbidden.flatMap(value => Vector(_validate_(value), _validate_(value.noSpaces)))
      val absent = plain.mapObject(_.remove("dependencyValidation"))
      val absentaccepted = Vector(_validate_(absent), _validate_(absent.noSpaces))
      Then("ordinary references survive while camel case, snake case, explicit null and collisions all refuse")
      accepted.foreach(_.isSuccess shouldBe true)
      rejected.foreach(_.isSuccess shouldBe false)
      absentaccepted.foreach(_.isSuccess shouldBe true)
    }

    "preserve an actual dependency selection through the generic submission adapter" in {
      Given("a specification-issued implementation result with both independently named approval references")
      val work = NativePreparationFixture.goalWork("ImplementStep")
      val order = work.current.asInstanceOf[org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation.WorkOrder[GoalPhaseNativeWork.Request]].request
      val value = GoalPhaseNativeWork.Result("completed", "SPECIFICATION-OWNED linked source result", Vector.empty,
        dependencyValidation = Some(selection))
      val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
        order.context.snapshot, TypedValue(GoalPhaseNativeWork.resultCodec.typeIdentity, value),
        ContextReference("specification-source-result", "1"), Vector.empty, ExecutionEvidence(Vector.empty, None))
      When("the generic fixture adapter produces named business parameters for the generated result reader")
      val parameters = SplitPhaseExplicitPartitionFixture.take(NativeWorkflowOperationInputFixture.submissionC(result))
      val request = Request.ofOperation("specificationSubmission").copy(properties = parameters)
      val publicresult = SplitPhaseExplicitPartitionFixture.take(NativeWorkflowOperationInput.valueC[
        org.simplemodeling.textus.smworkflow.value.GoalPhaseWorkResult](request, "result"))
      Then("the adapter retains the selection instead of silently replacing it with legacy absence")
      D.fromPublicC(publicresult.dependencyValidation).toOption shouldBe Some(Some(selection))
      publicresult.outcome shouldBe value.outcome
      publicresult.findings shouldBe value.findings
    }
  }

  "DV-01,DV-02 exact saved dependency scope" should {
    "admit both independently saved units without widening the original Plan or base" in {
      Given("two SPECIFICATION-OWNED saved approvals for the source and classifier units")
      When("the native business boundary compares their exact union with reference-only selection")
      val admitted = D.admitC(selection, binding, approvals, request)
      Then("both references and the original primary assignment remain distinct and unchanged")
      admitted.toOption shouldBe Some(admission)
      D.admitC(selection.copy(approvals = selection.approvals.reverse), binding, approvals, request).isSuccess shouldBe true
      request.selectedPlan shouldBe plan
      request.originalBase shouldBe primaryBase
      D.publicSelection(Some(selection)).flatMap(r => D.fromPublicC(Some(r)).toOption).flatten shouldBe Some(selection)
    }

    "refuse every missing, crossed or duplicated approval selection" in {
      Given("the complete saved union, with no authority inferred from semantic text")
      val cases = Table("selection", selection.copy(approvals = Vector.empty),
        selection.copy(approvals = selection.approvals.take(1)),
        selection.copy(approvals = selection.approvals :+ selection.approvals.head),
        selection.copy(binding = ContextReference(binding.identity, "2")),
        selection.copy(approvals = selection.approvals.updated(0,
          selection.approvals.head.copy(decision = ContextReference("invented-answer", "1")))))
      When("each bounded mutation is submitted")
      forAll(cases) { value =>
        When("the selected approval mutation reaches native admission")
        val result = D.admitC(value, binding, approvals, request)
        Then("it cannot create native admission")
        result.isSuccess shouldBe false
      }
      When("the same selection is offered for a review assignment")
      val review = D.admitC(selection, binding, approvals, request.copy(kind = "ReviewStep"))
      Then("the review assignment cannot create admission")
      review.isSuccess shouldBe false
      When("the same selection is offered for another Step")
      val otherstep = D.admitC(selection, binding, approvals, request.copy(stepIdentity = Some("other-step")))
      Then("the other Step cannot create admission")
      otherstep.isSuccess shouldBe false
      When("the same selection is offered for another primary repository")
      val otherrepository = D.admitC(selection, binding, approvals, request.copy(selectedPlan = plan.copy(repository = "wrong-primary")))
      Then("the other repository cannot create admission")
      otherrepository.isSuccess shouldBe false
    }

    "refuse repository, workspace, path and historical-base substitutions" in {
      Given("a fixed binding and two actual-reference-shaped specification approvals")
      val cases = Table("binding", binding.copy(dependency = dependency.copy(repository = "wrong-repository")),
        binding.copy(dependency = dependency.copy(workspace = requester.workspace)),
        binding.copy(paths = paths.take(1)), binding.copy(paths = paths :+ "unapproved.scala"),
        binding.copy(paths = Vector("../escape.scala")), binding.copy(base = base.copy(head = "not-an-object")),
        binding.copy(baseAdoption = ""), binding.copy(root = "relative/cncf"),
        binding.copy(checks = Vector(check.copy(metadata = null))))
      When("each mutation reaches the existing admission boundary")
      forAll(cases) { value =>
        When("the substituted binding is submitted")
        val result = D.admitC(selection, value, approvals, request)
        Then("no wrong target or unsupported base is admitted")
        result.isSuccess shouldBe false
      }
    }
  }

  "DV-03,DV-07,DV-08 immutable native capture and legacy input" should {
    "retain the exact legacy admission wire and require a closed explicit prefix version" in {
      Given("an unchanged legacy approval union and a separately encoded accepted predecessor")
      val legacy = Json.obj("binding" -> binding.asJson, "approvals" -> approvals.asJson)
      val prefix = D.AcceptedPrefix(D.acceptedPrefixSchema, capture.session, capture.plan,
        "preceding-step", "19", O.Snapshot(base.head, "f" * 40), Vector("inherited.scala"))
      val extended = admission.copy(acceptedPrefix = Some(prefix))
      When("legacy and extended native admissions roundtrip through the closed codec")
      val legacywire = admission.asJson.noSpaces
      val decodedlegacy = legacy.as[D.Admission]
      val decodedextended = extended.asJson.as[D.Admission]
      Then("absence preserves its original bytes while every present extension is complete and versioned")
      legacywire shouldBe legacy.noSpaces
      decodedlegacy shouldBe Right(admission)
      decodedextended shouldBe Right(extended)
      val malformed = Vector(Json.Null, prefix.asJson.mapObject(_.remove("closureRevision")),
        prefix.asJson.mapObject(_.add("schemaVersion", "unknown".asJson)),
        prefix.asJson.mapObject(_.add("closureRevision", "019".asJson)),
        prefix.asJson.mapObject(_.add("paths", Json.arr())),
        prefix.asJson.mapObject(_.add("candidate", Json.Null)),
        prefix.asJson.mapObject(_.add("trusted", true.asJson)))
      malformed.foreach { value =>
        When("a malformed accepted prefix reaches the closed admission codec")
        val decoded = legacy.mapObject(_.add("acceptedPrefix", value)).as[D.Admission]
        Then("the incomplete or unknown extension is refused")
        decoded.isLeft shouldBe true
      }
      When("an unknown admission lineage is decoded")
      val lineage = extended.asJson.mapObject(_.add("lineage", Json.arr())).as[D.Admission]
      Then("the unknown admission field is refused")
      lineage.isLeft shouldBe true
      When("the ordinary selection is checked")
      val selected = D.selectionValid(selection)
      Then("the original selection remains valid")
      selected shouldBe true
      When("an accepted prefix is placed in the public selection")
      val enriched = selection.asJson.mapObject(_.add("acceptedPrefix", prefix.asJson)).as[D.Selection]
      Then("the selection cannot supply its own accepted-prefix evidence")
      enriched.isLeft shouldBe true
    }

    "bind incremental captures to the predecessor interpretation without granting trust to decoded anchors" in {
      Given("a well-shaped predecessor and current separately approved paths")
      val prefix = D.AcceptedPrefix(D.acceptedPrefixSchema, capture.session, capture.plan,
        "preceding-step", "19", O.Snapshot(base.head, "f" * 40), Vector("inherited.scala"))
      val extended = admission.copy(acceptedPrefix = Some(prefix))
      val incremental = capture.copy(admission = extended, changedPaths = paths.take(1))
      When("the pure capture boundary interprets that explicitly selected incremental baseline")
      val valid = D.captureValid(incremental)
      val captured = D.capturedForC(incremental, capture.session, plan, capture.step, capture.generation,
        capture.sourceRevision, primaryBase, extended)
      val incrementalbase = GoalPhaseNativeDependencyValidation.baseline(extended)
      val originalbase = GoalPhaseNativeDependencyValidation.baseline(admission)
      val decoded = GoalPhaseNativeDependencyValidation.resolvedC(extended.asJson.as[D.Admission].toOption.get, None)
      Then("the original commit base stays fixed and crossed captures or inherited-only changes refuse")
      valid shouldBe true
      captured.isSuccess shouldBe true
      incrementalbase shouldBe prefix.candidate.tree
      originalbase shouldBe base.head
      decoded.isSuccess shouldBe false
      Vector(prefix.copy(sessionId = "foreign"), prefix.copy(plan = ContextReference("other-plan", "1")),
        prefix.copy(closureRevision = capture.sourceRevision), prefix.copy(candidate = prefix.candidate.copy(head = "0" * 40)))
        .foreach { p =>
          When("a crossed predecessor is used for the incremental capture")
          val result = D.captureValid(incremental.copy(admission = extended.copy(acceptedPrefix = Some(p))))
          Then("the crossed capture is refused")
          result shouldBe false
        }
      When("the capture contains only inherited paths")
      val inherited = D.captureValid(incremental.copy(changedPaths = prefix.paths))
      Then("inherited-only changes do not qualify as current work")
      inherited shouldBe false
      When("the original independently saved approval is admitted")
      val original = D.admitC(selection, binding, approvals, request)
      Then("the original admission is retained")
      original.toOption shouldBe Some(admission)
    }

    "bind a capture to its same Session, generation, Plan and primary base" in {
      Given("a specification capture with a dependency tree different from the primary tree")
      When("the continuation owner binds it back to its original assignment")
      val original = D.capturedForC(capture, request.instanceIdentity, plan, "step-1", 1, "20", primaryBase, admission)
      Then("only that exact context is accepted; another Session or source cannot reuse it")
      original.isSuccess shouldBe true
      val cases = Table("capture", capture.copy(session = "another-session"), capture.copy(generation = 2),
        capture.copy(sourceRevision = "21"), capture.copy(primaryBase = base),
        capture.copy(plan = ContextReference(plan.identity, "2")), capture.copy(step = "step-2"),
        capture.copy(admission = admission.copy(binding = binding.copy(revision = 2))))
      forAll(cases) { value =>
        When("a crossed capture is bound to the original assignment")
        val result = D.capturedForC(value, request.instanceIdentity, plan, "step-1", 1, "20", primaryBase, admission)
        Then("the crossed assignment cannot reuse it")
        result.isSuccess shouldBe false
      }
      When("the original capture is roundtripped")
      val restored = capture.asJson.as[D.Capture]
      Then("its exact original context survives")
      restored shouldBe Right(capture)
    }

    "accept absence without inventing dependency success and reject AI-authored evidence fields" in {
      Given("legacy completion and the closed public selection wire")
      val legacy = GoalPhaseNativeWork.Result("completed", "Legacy source completion", Vector.empty)
      When("old results and malformed new selections are decoded")
      val restored = legacy.asJson.as[GoalPhaseNativeWork.Result]
      val evidence = legacy.asJson.hcursor.downField("dependencyAdmission").focus
      val absent = D.fromPublicC(None)
      Then("legacy fields stay absent and snapshots, nulls and unknown keys refuse")
      restored shouldBe Right(legacy)
      evidence shouldBe None
      absent.toOption shouldBe Some(None)
      Vector("candidate", "success", "dependencyAdmission", "argv").foreach { name =>
        When("a semantic claim is added to the closed public selection")
        val decoded = selection.asJson.mapObject(_.add(name, Json.fromString("semantic claim"))).as[D.Selection]
        Then("the evidence-shaped field is refused")
        decoded.isLeft shouldBe true
      }
      When("the selection supplies an explicit null binding")
      val nullbinding = selection.asJson.mapObject(_.add("binding", Json.Null)).as[D.Selection]
      Then("the null binding is refused")
      nullbinding.isLeft shouldBe true
      val explicitnull = legacy.asJson.mapObject(_.add("dependencyValidation", Json.Null))
      When("the legacy result supplies explicit null dependency validation")
      val nullresult = explicitnull.as[GoalPhaseNativeWork.Result]
      Then("the explicit null result field is refused")
      nullresult.isLeft shouldBe true
    }
  }
}
