/**
 * @since   Nov. 17, 2026
 * @version Nov. 18, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.validation.ValidationDiscoveryV1 as D
import org.goldenport.cncf.validation.ValidationMetadataV1 as M
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1
import org.goldenport.protocol.Property
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseQualification as Q
import SplitPhaseExplicitPartitionFixture.take

class Phase8QualificationConnectionDeclaration extends org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration {
  override def declaration: org.goldenport.cncf.validation.ValidationMetadataV1.Declaration =
    super.declaration.copy(features = super.declaration.features +
      org.goldenport.cncf.validation.ValidationMetadataV1.FeatureReference("component:sm-workflow", "phase8-qualification-connection"))
}

/** Normal native Operations own candidate capture, typed checks and review.
  * All source, Human and reviewer facts are specification-owned fixture facts.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[Phase8QualificationConnectionDeclaration])
final class GoalPhaseQualificationOperationSpec extends AnyWordSpec with GivenWhenThen with GoalPhaseValidationRecoveryOperationFixture
    with org.simplemodeling.textus.smworkflow.testing.DevelopFixtureValidation {
  private val suffix = GoalPhaseNativeValidation.Check("qualification-product", 2, WorkflowSbtValidation.Kind.TestOnly)
  private val inventory = Q.Policy("finite-qualification-fixture", "1", Vector(Q.Scope("step-1", Vector(suffix))))
  private var _triage_policy = false
  private var _phase_failure = false
  private var _managed = false
  private var _managed_setup = "none"
  private var _managed_host_revision = false
  private val _managed_suite = "org.simplemodeling.textus.smworkflow.p805built.ManagedQualificationProductSpec"
  private val _managed_source = "src/test/scala/org/simplemodeling/textus/smworkflow/p805built/ManagedQualificationProductSpec.scala"
  private val _reconstruction_phase = "docs/phase/phase-2.md"
  private val _reconstruction_checklist = "docs/phase/phase-2-checklist.md"
  private val _build = GoalPhaseNativeValidation.Check("qualification-build", 2, WorkflowSbtValidation.Kind.TestCompile)
  private def _managed_inventory(root: Path): Q.Policy = {
    val products = root.resolve("repository/target/p805-test-classes")
    val artifact = D.ArtifactScope("component:p805-built", "p805-built-tests", "fixture-1", "fixture-source-1",
      if (_managed_setup == "wrong-root") root.resolve("repository/target/foreign-classes") else products,
      "org.simplemodeling.textus.smworkflow.p805built")
    val query = if (_managed_setup == "empty-selection") D.SelectionQuery(features = Set(M.FeatureReference("absent", "absent")))
      else D.SelectionQuery(standardFramework = true, explicitSuites = Set(_managed_suite))
    val profile = GoalPhaseNativeMetadata.Profile(D.DiscoveryScope("p805-built", "1", Vector(artifact)), query,
      Vector.empty, buildCheckIdentity = Some(_build.identity))
    inventory.copy(scopes = Vector(Q.Scope("step-1", Vector(_build,
      GoalPhaseNativeValidation.Check("qualification-managed", 3, WorkflowSbtValidation.Kind.TestOnly, Some(profile))))))
  }
  override protected def _recovery_plan = {
    val plan = super._recovery_plan.copy(repairLimit = 10)
    if (!_triage_policy) plan else plan.copy(steps = plan.steps.map(_.copy(actionPolicy = Some(
      GoalPhaseStepAssessment.Policy("qualification-triage-boundary", "documentation", "conditional", "off", "off",
        Vector.empty, semanticTriage = "conditional")))))
  }
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    super._prepare_sbt_build_(root, scenario)
    val buildfile = root.resolve("repository/build.sbt")
    val compilebuild = Files.readString(buildfile)
    TypedSbtHostPlanFixture.buildMetadata(root, classOf[qualificationfixture.QualificationProductSpec])
    val metadatabuild = Files.readString(buildfile)
    Files.writeString(buildfile, compilebuild + "\n" + metadatabuild)
    if (_managed) {
      val source = root.resolve("repository").resolve(_managed_source)
      Files.createDirectories(source.getParent)
      Files.writeString(source, """package org.simplemodeling.textus.smworkflow.p805built
        |final class ManagedQualificationDeclaration extends org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration
        |@org.goldenport.cncf.validation.annotation.ValidationSpecification(
        |  declaration = classOf[ManagedQualificationDeclaration])
        |final class ManagedQualificationProductSpec extends org.scalatest.wordspec.AnyWordSpec
        |    with org.scalatest.GivenWhenThen with org.scalatest.matchers.should.Matchers
        |    with org.simplemodeling.textus.smworkflow.testing.DevelopFixtureValidation {
        |  "The separately compiled product" should {
        |    "retain its real semantic input" in {
        |      Given("the selected semantic input in the isolated product repository")
        |      val path = java.nio.file.Path.of("value.txt")
        |      When("the separately compiled product reads its real input")
        |      val content = java.nio.file.Files.readString(path)
        |      Then("the selected semantic change including its final newline is retained")
        |      content shouldBe "selected semantic change\n"
        |    }
        |  }
        |}
        |""".stripMargin)
      val build = "\nTest / classDirectory := baseDirectory.value / \"target\" / \"p805-test-classes\"\n" +
        "Test / definedTests := Seq(new TestDefinition(" + Json.fromString(_managed_suite).noSpaces +
        ", new sbt.testing.SubclassFingerprint { def superclassName(): String = \"org.scalatest.Suite\"; " +
        "def isModule(): Boolean = false; def requireNoArgConstructor(): Boolean = true }, true, Array(new sbt.testing.SuiteSelector)))\n" +
        (if (_managed_setup == "missing-exports") "Test / exportedProducts := Seq.empty\n" else "")
      Files.writeString(root.resolve("repository/build.sbt"), build, StandardOpenOption.APPEND)
      if (_managed_setup == "wrong-root") {
        val phase = root.resolve("repository").resolve(_reconstruction_phase)
        val checklist = root.resolve("repository").resolve(_reconstruction_checklist)
        Files.createDirectories(phase.getParent)
        if (!Files.exists(phase)) Files.writeString(phase, "# Current tiny Phase\nRetain the original development Plan and checks.\n")
        if (!Files.exists(checklist)) Files.writeString(checklist, "- [ ] current: Preserve the original checks and qualification\n")
      }
    }
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] =
    super._setup_commands_(root, scenario).map { command =>
      if (_managed && command.contains("add") && command.contains("src/main/scala/CompileConsumer.scala"))
        command ++ Vector(_managed_source) ++
          (if (_managed_setup == "wrong-root") Vector(_reconstruction_phase, _reconstruction_checklist) else Vector.empty)
      else command
    }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val original = super.process_plan(root, scenario, goal)
    val testplan = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(if (_managed) 3 else 2),
      operation = SbtOperation.TestOnly, testClasses = Vector(if (_managed) _managed_suite else classOf[qualificationfixture.QualificationProductSpec].getName))
      .mapObject(fields => fields.add("sbtPrograms", Json.fromValues(fields("sbtPrograms").get.asArray.get.map { program =>
        val selected = if (_managed) program.mapObject(_.add("validationMode", Json.obj(
          "modes" -> Json.arr(Json.fromString("develop")), "provider" -> Json.fromString("fixture"),
          "livePrerequisites" -> Json.arr()))) else program
        selected.mapObject(p => p.add("environment", p("environment").get.mapObject { environment =>
          val actual = if (_phase_failure) environment.add("SM_WORKFLOW_QUALIFICATION_EXPECTED", Json.fromString("Required Phase-qualified value"))
            else environment
          if (_managed && _managed_host_revision) actual.add("P805_FIXTURE_HOST_REVISION", Json.fromString("2")) else actual
        }))
      })))
    def array(value: Json, key: String) = value.hcursor.get[Vector[Json]](key).toOption.get
    val buildplan = if (!_managed) None else {
      val base = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(2), operation = SbtOperation.Compile)
      val binding = Json.obj("componentIdentity" -> Json.fromString("component:p805-built"),
        "artifactIdentity" -> Json.fromString("p805-built-tests"), "artifactRevision" -> Json.fromString("fixture-1"),
        "packagePrefix" -> Json.fromString("org.simplemodeling.textus.smworkflow.p805built"))
      Some(base.mapObject(_.add("sbtPrograms", Json.fromValues(array(base, "sbtPrograms").map(program =>
        program.mapObject(_.add("operation", Json.fromString("test-compile")).add("validationDiscovery", binding)))))))
    }
    val programs = array(original, "sbtPrograms") ++ buildplan.toVector.flatMap(array(_, "sbtPrograms")) ++ array(testplan, "sbtPrograms")
    val grants = array(original, "grants").map(_.mapObject(fields => fields.add("capabilities",
      Json.fromValues((array(original, "gitPrograms") ++ programs).map(_.hcursor.downField("capability").focus.get)))))
    original.mapObject(_.add("sbtPrograms", Json.fromValues(programs)).add("grants", Json.fromValues(grants))
      .add("workspaces", Json.fromValues(array(original, "workspaces") ++ buildplan.toVector.flatMap(array(_, "workspaces")) ++ array(testplan, "workspaces")))
      .add("workspaceGrants", Json.fromValues(array(original, "workspaceGrants") ++ buildplan.toVector.flatMap(array(_, "workspaceGrants")) ++ array(testplan, "workspaceGrants"))))
  }
  private def executions(root: Path): Vector[String] = {
    val path = root.resolve("sbt-executions.txt")
    if (Files.exists(path)) Files.readString(path).linesIterator.toVector else Vector.empty
  }
  private def describe(e: RecoveryEpisode, id: String): V.GoalPhaseQualificationDescriptionResult =
    e.invoke[V.GoalPhaseQualificationDescriptionResult](Q.describeOperation,
      List(Property("input", V.GoalPhaseQualificationDescriptionInput(id).toRecord(), None)))
  private def select(e: RecoveryEpisode, input: V.GoalPhaseQualificationSelectionInput) =
    e.call(Q.selectOperation, List(Property("input", input.toRecord(), None)))
  private def _same_state(actual: InstanceRecord, expected: InstanceRecord): Unit =
    io.circe.parser.parse(take(WorkflowInstanceRecordJsonV1.encodeC(actual))).toOption.get shouldBe
      io.circe.parser.parse(take(WorkflowInstanceRecordJsonV1.encodeC(expected))).toOption.get

  "Same-Session finite qualification" should {
    "discover a newly compiled product through actual managed fullClasspath and retain review without replay after reopen" in {
      _managed = true
      try {
        Given("an isolated normal Operation episode with a unique product absent from the parent test loader")
        val root = freshRecoveryRoot("qualification-managed-classpath")
        val policy = _managed_inventory(root)
        var id = ""
        var source = 0L
        withRecoveryEpisode(root, initialize = true, qualificationPolicy = Some(policy)) { e =>
          id = e.start().sessionId
          e.submitImplementation(e.advance(id)).accepted shouldBe true
          e.advance(id).kind shouldBe "Wait"
          val description = describe(e, id)
          read_value[V.GoalPhaseQualificationSelectionResult](take(select(e,
            V.GoalPhaseQualificationSelectionInput(id, description.target, policy.scopes.head.checks.map(_.identity))))).accepted shouldBe true
          source = e.current(id).revision.value
          When("ordinary Advance owns the real build export discovery and selected TestOnly")
          val review = e.advance(id)
          Then("the resulting native review retains truthful managed prefix and complete product inventory")
          withClue(review.message) { review.work.map(_.kind) shouldBe Some("ReviewStep") }
          review.work.get.kind shouldBe "ReviewStep"
          val actual = e.retained(id, source.toString, "validation").get.payload
          actual.outcome shouldBe Some("passed")
          actual.receipts.map(_.identity).take(2) shouldBe Vector("compile", _build.identity)
          actual.receipts(1).processObservation should not be empty
          actual.metadata.head.inventory.completeness shouldBe D.Completeness.COMPLETE
          actual.metadata.head.inventory.operations.map(_.selector.suiteClassName).distinct shouldBe Vector(_managed_suite)
          val exported = take(GoalPhaseNativeMetadata.buildsC(Vector(_build, policy.scopes.head.checks.last), actual.receipts)).values.head
          exported.products should contain(root.resolve("repository/target/p805-test-classes"))
          exported.fullClasspath.size should be > exported.products.size
          GoalPhaseNativeValidation.codec.decode(GoalPhaseNativeValidation.codec.encode(actual)) shouldBe Right(actual)
          val crossed = actual.copy(receipts = actual.receipts.updated(1, actual.receipts(1).copy(identity = "foreign-build")))
          GoalPhaseNativeValidation.codec.decode(GoalPhaseNativeValidation.codec.encode(crossed)).isLeft shouldBe true
          val missing = actual.copy(receipts = actual.receipts.updated(1, actual.receipts(1).copy(processObservation = None)))
          GoalPhaseNativeValidation.codec.decode(GoalPhaseNativeValidation.codec.encode(missing)).isLeft shouldBe true
          val changedBatch = actual.metadata.head.copy(profile = actual.metadata.head.profile.copy(buildCheckIdentity = Some("compile")))
          GoalPhaseNativeValidation.codec.decode(GoalPhaseNativeValidation.codec.encode(actual.copy(metadata = Vector(changedBatch)))).isLeft shouldBe true
          val commandlines = Files.readString(root.resolve("sbt-command-lines.txt"))
          commandlines should include("export Test / fullClasspath")
          commandlines should include("export Test / exportedProducts")
          executions(root).size shouldBe 3
        }
        withRecoveryEpisode(root, qualificationPolicy = Some(policy)) { e =>
          val before = e.current(id)
          val count = executions(root)
          e.advance(id).work.get.kind shouldBe "ReviewStep"
          executions(root) shouldBe count
          _same_state(e.current(id), before)
        }
      } finally _managed = false
    }

    "retain the actual successful prefix and return setup Wait without replay or repair charge after preparation refusal" in {
      for (failure <- Vector("missing-exports", "empty-selection", "wrong-root")) {
        _managed = true
        _managed_setup = failure
        try {
          Given("an isolated qualified candidate with a real managed build and a known preparation refusal")
          val root = freshRecoveryRoot("qualification-setup-" + failure)
          val policy = _managed_inventory(root)
          var id = ""
          var retained = Option.empty[org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord]
          var generation = 0L
          var freshtarget = Option.empty[Json]
          var prefixsource = ""
          var originalprefix = Option.empty[GoalPhaseNativeValidation.Payload]
          withRecoveryEpisode(root, initialize = true, qualificationPolicy = Some(policy)) { e =>
            id = e.start().sessionId
            e.submitImplementation(e.advance(id)).accepted shouldBe true
            e.advance(id).kind shouldBe "Wait"
            val description = describe(e, id)
            read_value[V.GoalPhaseQualificationSelectionResult](take(select(e,
              V.GoalPhaseQualificationSelectionInput(id, description.target, policy.scopes.head.checks.map(_.identity))))).accepted shouldBe true
            val selection = e.current(id)
            When("normal Advance reaches known metadata preparation refusal")
            val waiting = e.advance(id)
            Then("the existing Wait reports setup while preserving the selected prefix and repair allowance")
            waiting.kind shouldBe "Wait"
            waiting.message should include("ValidationSetupRequired")
            waiting.repairProgress.get.automaticCount shouldBe 0
            waiting.repairProgress.get.limit shouldBe 10
            e.retained(id, selection.revision.value.toString, "validation") shouldBe None
            e.retained(id, selection.revision.value.toString, "selection") shouldBe None
            val prefix = e.retained(id, selection.revision.value.toString, "check-result-1").get.payload
            prefixsource = selection.revision.value.toString
            originalprefix = Some(prefix)
            prefix.receipts.head.exitCode shouldBe Some(0)
            prefix.receipts.last.exitCode shouldBe Some(0)
            prefix.receipts.last.sbt.get.outcome shouldBe (if (failure == "missing-exports")
              WorkflowSbtValidation.Outcome.Unverified else WorkflowSbtValidation.Outcome.Succeeded)
            prefix.outcome shouldBe None
            prefix.candidate shouldBe prefix.qualification.get.target.candidate
            generation = prefix.qualification.get.target.generation
            e.current(id).history.take(selection.history.size) shouldBe selection.history
            retained = Some(e.current(id))
            executions(root).size shouldBe 2
            e.advance(id).message should include("ValidationSetupRequired")
            _same_state(e.current(id), retained.get)
            executions(root).size shouldBe 2
          }
          withRecoveryEpisode(root, qualificationPolicy = Some(policy)) { e =>
            e.advance(id).message should include("ValidationSetupRequired")
            _same_state(e.current(id), retained.get)
            executions(root).size shouldBe 2
            if (failure == "wrong-root") {
              Given("the same retained complete successful preparation prefix and a fresh semantic source change")
              val before = e.current(id)
              Files.writeString(root.resolve("repository/value.txt"), "fresh semantic change after setup refusal\n")
              val proposal = Json.obj("identity" -> Json.fromString("qualification-setup-current-development"),
                "revision" -> Json.fromString("1"),
                "reason" -> Json.fromString("Retain the same raw Plan while reconciling the changed current source"),
                "plan" -> _recovery_plan.asJson)
              val input = V.GoalPhaseDevelopmentReconstructionInput(id, _reconstruction_phase, _reconstruction_checklist,
                GoalPhaseNativeMetadata.record(proposal), "SPECIFICATION-OWNED same Session fresh source after metadata setup refusal")
              When("the public reconstruction operation reconciles the actual preparation prefix")
              val reconstructed = e.invoke[V.GoalPhaseAdvanceResult](GoalPhaseDevelopmentReconstruction.operation,
                List(Property("input", input.toRecord(), None)))
              Then("it retains the Session history and original repair allowance without replaying any check")
              reconstructed.kind shouldBe "Wait"
              reconstructed.sessionId shouldBe id
              reconstructed.repairProgress.get.automaticCount shouldBe 0
              reconstructed.repairProgress.get.limit shouldBe 10
              e.current(id).history.take(before.history.size) shouldBe before.history
              executions(root).size shouldBe 2
              When("a separate ordinary Advance captures the fresh current candidate")
              val waiting = e.advance(id)
              Then("fresh qualification selection is required at the next generation with the same original accounting")
              waiting.kind shouldBe "Wait"
              waiting.message should include("qualification selection required")
              waiting.sessionId shouldBe id
              waiting.repairProgress.get.automaticCount shouldBe 0
              waiting.repairProgress.get.limit shouldBe 10
              executions(root).size shouldBe 2
              val description = describe(e, id)
              val publictarget = io.circe.parser.parse(take(description.target.toJsonStringC)).toOption.get
              val target = GoalPhaseNativeMetadata.normalizePublicJson(publictarget).toOption.get
              freshtarget = Some(target)
              target.hcursor.get[Long]("generation") shouldBe Right(generation + 1)
              target.hcursor.get[String]("sessionId") shouldBe Right(id)
              e.current(id).identity.value shouldBe id
              e.current(id).history.take(before.history.size) shouldBe before.history
            }
          }
          if (failure == "wrong-root") {
            val previoushost = _managed_host_revision
            _managed_host_revision = true
            try {
              Given("the reconstructed same Session and a newly installed qualification policy and inert host revision")
              withRecoveryEpisode(root, qualificationPolicy = Some(policy.copy(revision = "2"))) { e =>
                When("ordinary Advance and description read the historical selection before exposing the fresh unselected target")
                val waiting = e.advance(id)
                val description = describe(e, id)
                val publictarget = io.circe.parser.parse(take(description.target.toJsonStringC)).toOption.get
                val target = GoalPhaseNativeMetadata.normalizePublicJson(publictarget).toOption.get
                Then("only the fresh target binds the new policy and host while the successful original prefix stays immutable")
                waiting.kind shouldBe "Wait"
                waiting.message should include("qualification selection required")
                waiting.sessionId shouldBe id
                waiting.repairProgress.get.automaticCount shouldBe 0
                waiting.repairProgress.get.limit shouldBe 10
                target.hcursor.get[String]("sessionId") shouldBe Right(id)
                target.hcursor.get[Long]("generation") shouldBe Right(generation + 1)
                target.hcursor.get[String]("policyRevision") shouldBe Right("2")
                val host = target.hcursor.get[String]("hostPlan").toOption.get
                host should not be freshtarget.get.hcursor.get[String]("hostPlan").toOption.get
                host should include("P805_FIXTURE_HOST_REVISION")
                e.current(id).identity.value shouldBe id
                e.current(id).history.take(retained.get.history.size) shouldBe retained.get.history
                val prefix = e.retained(id, prefixsource, "check-result-1").get.payload
                GoalPhaseNativeValidation.codec.encode(prefix) shouldBe GoalPhaseNativeValidation.codec.encode(originalprefix.get)
                executions(root).size shouldBe 2
              }
            } finally _managed_host_revision = previoushost
          }
        } finally { _managed = false; _managed_setup = "none" }
      }
    }

    "issue genuine initial Phase repair after a qualified suffix failure while preserving the closed Step and history" in {
      _phase_failure = true
      try {
        val root = freshRecoveryRoot("qualification-phase-failure")
        val phaseInventory = inventory.copy(scopes = Vector(Q.Scope("phase:" + _recovery_plan.phase, Vector(suffix))))
        withRecoveryEpisode(root, initialize = true, qualificationPolicy = Some(phaseInventory)) { e =>
          val id = e.start().sessionId
          e.submitImplementation(e.advance(id)).accepted shouldBe true
          val stepReview = e.advance(id)
          stepReview.work.get.kind shouldBe "ReviewStep"
          stepReview.work.get.review.get.checks.head.exitCode shouldBe Some(0)
          e.acceptStep(stepReview)
          val waiting = e.advance(id)
          waiting.kind shouldBe "Wait"
          waiting.message should include("qualification selection required")
          val before = e.current(id)
          val count = executions(root)
          count.size shouldBe 1
          val description = describe(e, id)
          read_value[V.GoalPhaseQualificationSelectionResult](take(select(e,
            V.GoalPhaseQualificationSelectionInput(id, description.target, Vector(suffix.identity))))).accepted shouldBe true
          val validationSource = e.current(id).revision.value
          val repair = e.advance(id)
          repair.work.get.kind shouldBe "RepairStep"
          repair.work.get.stepIdentity shouldBe None
          repair.work.get.completed.map(_.step) shouldBe Vector("step-1")
          repair.work.get.checkFailure.get.initialPhaseRepair should not be empty
          repair.work.get.checkFailure.get.validation.qualification should not be empty
          repair.repairProgress.get.automaticCount shouldBe 1
          repair.repairProgress.get.ordinaryCount shouldBe 1
          repair.repairProgress.get.limit shouldBe 10
          val native = e.retained(id, validationSource.toString, "validation").get.payload
          native.outcome shouldBe Some("failed")
          native.receipts.head.exitCode shouldBe Some(0)
          native.receipts.last.exitCode.exists(_ != 0) shouldBe true
          native.qualification.get.target.required.map(_.identity) shouldBe Vector("compile")
          native.qualification.get.target.supplemental shouldBe Vector(suffix)
          e.current(id).history.take(before.history.size) shouldBe before.history
          val completed = repair.work.get.completed
          val afterChecks = executions(root)
          e.advance(id).work.get.completed shouldBe completed
          executions(root) shouldBe afterChecks
        }
      } finally _phase_failure = false
    }

    "refuse a policy permitting triage before selection or any check intent without charging repair" in {
      _triage_policy = true
      try {
        val root = freshRecoveryRoot("qualification-triage-refusal")
        withRecoveryEpisode(root, initialize = true, qualificationPolicy = Some(inventory)) { e =>
          val id = e.start().sessionId
          e.submitImplementation(e.advance(id)).accepted shouldBe true
          val waiting = e.advance(id)
          waiting.kind shouldBe "Wait"
          val before = e.current(id)
          val refused = e.call(Q.describeOperation,
            List(Property("input", V.GoalPhaseQualificationDescriptionInput(id).toRecord(), None)))
          refused.isSuccess shouldBe false
          refused.toString should include("semantic triage")
          _same_state(e.current(id), before)
          executions(root) shouldBe Vector.empty
          waiting.repairProgress.get.automaticCount shouldBe 0
          waiting.repairProgress.get.limit shouldBe 10
          e.advance(id).kind shouldBe "Wait"
          _same_state(e.current(id), before)
          executions(root) shouldBe Vector.empty
        }
      } finally _triage_policy = false
    }

    "stop before checks preserve the original prefix and carry the actual suffix through fresh review after reopen" in {
      val root = freshRecoveryRoot("qualification-success")
      var identity = ""
      var selected = Option.empty[V.GoalPhaseQualificationSelectionInput]
      var selectionRevision = 0L
      withRecoveryEpisode(root, initialize = true, qualificationPolicy = Some(inventory)) { e =>
        Given("an issued implementation under a finite independently configured suffix with original limit ten")
        identity = e.start().sessionId
        val implementation = e.advance(identity)
        e.submitImplementation(implementation).accepted shouldBe true
        When("ordinary Advance captures the real candidate")
        val waiting = e.advance(identity)
        Then("it returns before every check intent instead of advancing to review")
        waiting.kind shouldBe "Wait"
        waiting.message should include("qualification selection required")
        executions(root) shouldBe Vector.empty
        val before = e.current(identity)
        val description = describe(e, identity)
        description.selectOperation shouldBe Q.selectOperation
        _same_state(e.current(identity), before)
        val input = V.GoalPhaseQualificationSelectionInput(identity, description.target, Vector(suffix.identity))
        val target = io.circe.parser.parse(take(description.target.toJsonStringC)).toOption.get
        val wrong = GoalPhaseNativeMetadata.record(target.mapObject(_.add("generation", Json.fromLong(999))))
        Vector(input.copy(sessionId = "foreign-session"), input.copy(target = wrong),
          input.copy(target = GoalPhaseNativeMetadata.record(target.mapObject(_.remove("hostPlan")))),
          input.copy(target = GoalPhaseNativeMetadata.record(target.mapObject(_.add("hostPlan", Json.Null)))),
          input.copy(checkIdentities = Vector("undeclared")), input.copy(checkIdentities = Vector(suffix.identity, suffix.identity)))
          .foreach(value => select(e, value).isSuccess shouldBe false)
        _same_state(e.current(identity), before)
        executions(root) shouldBe Vector.empty
        val inputFile = root.resolve("qualification-selection.json")
        Files.writeString(inputFile, take(input.toRecord().toJsonStringC))
        e.call(Q.selectOperation, List(Property("input", input.toRecord(), None),
          Property("inputResource", inputFile.toUri.toASCIIString, None))).isSuccess shouldBe false
        _same_state(e.current(identity), before)
        val selectedResult = e.call(Q.selectOperation,
          List(Property("inputResource", inputFile.toUri.toASCIIString, None)))
        read_value[V.GoalPhaseQualificationSelectionResult](take(selectedResult)).accepted shouldBe true
        selected = Some(input)
        selectionRevision = e.current(identity).revision.value
        select(e, input).isSuccess shouldBe false
        e.current(identity).revision.value shouldBe selectionRevision
      }
      for (policy <- Vector(None, Some(inventory.copy(revision = "replaced"))))
        withRecoveryEpisode(root, qualificationPolicy = policy) { e =>
          Given("the selected suffix's independent inventory is missing or replaced after restart")
          e.call("advanceGoalPhase", List(Property("sessionId", identity, None))).isSuccess shouldBe false
          executions(root) shouldBe Vector.empty
          e.current(identity).revision.value shouldBe selectionRevision
        }
      withRecoveryEpisode(root, admission = "missing-program", qualificationPolicy = Some(inventory)) { e =>
        Given("the actual resolved host plan changed after selection")
        e.call("advanceGoalPhase", List(Property("sessionId", identity, None))).isSuccess shouldBe false
        executions(root) shouldBe Vector.empty
        e.current(identity).revision.value shouldBe selectionRevision
      }
      withRecoveryEpisode(root, qualificationPolicy = Some(inventory)) { e =>
        When("the actual same Session reopens and ordinary Advance executes the combined checks")
        val review = e.advance(identity)
        review.work.get.kind shouldBe "ReviewStep"
        val checks = review.work.get.review.get.checks
        review.work.get.review.get.qualification should not be empty
        checks.map(_.identity) shouldBe Vector("compile", suffix.identity)
        checks.forall(_.exitCode.contains(0)) shouldBe true
        val native = e.retained(identity, selectionRevision.toString, "validation").get.payload
        native.qualification.get.target.required.map(_.identity) shouldBe Vector("compile")
        native.qualification.get.target.supplemental shouldBe Vector(suffix)
        native.selected.map(_.identity) shouldBe Vector("compile", suffix.identity)
        val pids = executions(root)
        pids.size shouldBe 2
        e.advance(identity).work.get.kind shouldBe "ReviewStep"
        executions(root) shouldBe pids
        select(e, selected.get).isSuccess shouldBe false
        review.work.get.selectedPlan.steps.head.checks shouldBe Vector("compile")
        review.repairProgress.get.limit shouldBe 10
      }
    }

    "retain a real failing suffix and use the original same-Session repair allowance" in {
      val root = freshRecoveryRoot("qualification-failure")
      withRecoveryEpisode(root, initialize = true, qualificationPolicy = Some(inventory)) { e =>
        val id = e.start().sessionId
        e.submitImplementation(e.advance(id)).accepted shouldBe true
        Files.writeString(root.resolve("repository/value.txt"), "A real product mismatch\n")
        e.advance(id).kind shouldBe "Wait"
        executions(root) shouldBe Vector.empty
        val description = describe(e, id)
        read_value[V.GoalPhaseQualificationSelectionResult](take(select(e,
          V.GoalPhaseQualificationSelectionInput(id, description.target, Vector(suffix.identity))))).accepted shouldBe true
        val revision = e.current(id).revision.value
        val repair = e.advance(id)
        repair.work.get.kind shouldBe "RepairStep"
        repair.sessionId shouldBe id
        repair.repairProgress.get.limit shouldBe 10
        repair.repairProgress.get.automaticCount shouldBe 1
        val actual = e.retained(id, revision.toString, "validation").get.payload
        actual.receipts.head.exitCode shouldBe Some(0)
        actual.receipts.last.exitCode.exists(_ != 0) shouldBe true
        actual.qualification.get.target.supplemental shouldBe Vector(suffix)
      }
    }
  }
}
