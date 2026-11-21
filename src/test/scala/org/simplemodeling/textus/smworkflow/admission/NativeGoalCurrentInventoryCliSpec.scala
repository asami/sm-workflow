package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import java.sql.DriverManager
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import org.goldenport.record.{Record, RecordPresentable}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhasePlanRevision as R
import GoalPhaseCurrentInventoryContinuation as C
import GoalPhaseAcceptanceBoundary as A
import SplitPhaseExplicitPartitionFixture.take

/** Small isolated normal host, actual native Operations, ordinary Compile/Test
  * and Git closure. All semantic/review/closure inputs are specification-owned.
  * The same scenario uses the selected installed command without source fallback.
  * @since   Nov.  3, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
class NativeGoalCurrentInventoryCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  protected def isolated_review_inventory: Boolean = false
  protected def isolated_human_inventory: Boolean = false
  private val _source = "src/main/scala/NeededInventory.scala"
  private val _added = Vector("build.sbt", _source)
  override protected def goal_selected_paths(goal: ThreeWorkflowRuntimeParameters.Goal): Vector[Vector[String]] =
    if (goal.candidatePaths == Vector("value.txt", "docs/phase.md") &&
        goal.closure.programs.map(_.step) == Vector("step-1", "step-2"))
      Vector(goal.candidatePaths, Vector("docs/phase.md"))
    else super.goal_selected_paths(goal)
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _read(path: Path): Json = parse(Files.readString(path, UTF_8)).toOption.get
  private def _normal(scenario: F.Scenario): Unit = {
    val path = scenario.root.resolve(".textus/config.json")
    Files.writeString(path, _read(path).mapObject(_.remove("sm-workflow.runtime.definition")
      .add("textus.assembly.descriptor", Json.fromString(scenario.root.resolve(".textus/assembly.yaml").toString))).noSpaces, UTF_8)
  }
  private def _fresh(name: String, normal: Boolean = true, configured: Boolean = false): F.Scenario = {
    val scenario = F.fresh(name, sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), repairContinuation = true)
    // The actual packaged normal repository profile supports runtime selections;
    // initial setup adds only the selected ordinary Test effect and its grants.
    val path = scenario.root.resolve(".textus/config.json")
    val config = _read(path)
    val descriptor = take(GenericSubsystemDescriptor.loadComponentArchive(Path.of("src/main/car").toAbsolutePath))
    val template = parse(descriptor.config("textus.subsystem.process-execution.plan")).toOption.get
    val values = Map("runtime.root" -> scenario.root.resolve("repository").toString, "runtime.parent" -> scenario.root.toString,
      "repository.root" -> scenario.root.resolve("repository").toString, "git.branch" -> "main",
      "git.remote" -> "origin", "git.remote-branch" -> "main", "git.endpoint" -> scenario.root.resolve("upstream.git").toString,
      "git.user-name" -> "Fixture", "git.user-email" -> "fixture@example.invalid",
      "environment.path" -> "/usr/bin:/bin", "environment.java-home" -> "", "environment.ssh-auth-sock" -> "")
    def _expand_(value: Json): Json = value.mapString(text => values.foldLeft(text) { case (prior, (key, value)) =>
      prior.replace("${" + key + "}", value) }).mapArray(_.map(_expand_)).mapObject(_.mapValues(_expand_))
    def _array_(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
    val selected = _expand_(template)
    val sbt = TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable, Vector(1),
      operation = SbtOperation.Test)
    val removedcapabilities = _array_(selected, "sbtPrograms").map(_.hcursor.get[Json]("capability").toOption.get).toSet
    val grants = _array_(selected, "grants").map { grant =>
      val owner = grant.hcursor.get[String]("componentId").toOption.get
      val capabilities = _array_(grant, "capabilities").filterNot(removedcapabilities)
      val added = _array_(sbt, "grants").filter(_.hcursor.get[String]("componentId").contains(owner))
        .flatMap(_array_(_, "capabilities"))
      grant.mapObject(_.add("capabilities", Json.fromValues((capabilities ++ added).distinct)))
    }
    // Replacing product SBT declarations must retain the existing evidence
    // binding that F.fresh already created for public prepared-import storage.
    val original = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val evidence = parse(config.hcursor.get[String](CommonEvidence.declaration.name).toOption.get).toOption.get
    val evidenceworkspace = evidence.hcursor.get[String]("workspaceIdentity").toOption.get
    val evidenceworkspaces = _array_(original, "workspaces").filter(_.hcursor.get[String]("identity").contains(evidenceworkspace))
    val evidencegrants = _array_(original, "workspaceGrants").filter(row =>
      row.hcursor.get[String]("componentId").contains(SmWorkflowComponent.componentId.name) &&
        row.hcursor.get[String]("workspaceId").contains(evidenceworkspace) &&
        row.hcursor.get[String]("access").contains("manage"))
    val process = selected.mapObject(_.add("sbtPrograms", Json.fromValues(_array_(sbt, "sbtPrograms")))
      .add("workspaces", Json.fromValues((_array_(selected, "workspaces") ++ _array_(sbt, "workspaces") ++ evidenceworkspaces).distinct))
      .add("workspaceGrants", Json.fromValues((_array_(selected, "workspaceGrants") ++ _array_(sbt, "workspaceGrants") ++ evidencegrants).distinct))
      .add("grants", Json.fromValues(grants)))
    Files.writeString(path, config.mapObject(_.add("textus.subsystem.process-execution.plan",
       Json.fromString(Phase69ConfiguredRepositoryFixture.withInitializationSources(process).noSpaces)).add("textus.assembly.descriptor",
      Json.fromString(scenario.root.resolve(".textus/assembly.yaml").toString))).noSpaces, UTF_8)
    if (configured) Files.writeString(path, _read(path).mapObject(_.add(A.declaration.name,
      Json.fromString(Json.obj("schemaVersion" -> Json.fromString(A.schemaVersion), "conditions" -> Json.arr(
        Json.obj("identity" -> Json.fromString("behavior"), "scope" -> Json.fromString("all"),
          "required" -> Json.fromBoolean(true), "description" -> Json.fromString("Implement the same selected product")))).noSpaces))).noSpaces, UTF_8)
    if (normal) _normal(scenario)
    scenario
  }
  private def _start(scenario: F.Scenario): Path = {
    val task = scenario.ownTask("current-inventory")
    scenario.retain(task, scenario.start().business[V.GoalPhaseStartResult])
    task
  }
  private def _advance(scenario: F.Scenario, task: Path): V.GoalPhaseAdvanceResult =
    _advance(scenario, scenario.advance(task))

  private def _observed_resource(result: F.Result): String =
    result.business[V.GoalPhaseAdvanceResult].retainedWork.map(_.resource)
      .getOrElse(result.invocation.stdout.toUri.toASCIIString)

  /** Keep the original invocation when its log is also a public evidence input. */
  private def _advance(scenario: F.Scenario, result: F.Result): V.GoalPhaseAdvanceResult = {
    val control = result.business[V.GoalPhaseAdvanceResult]
    val work = control.retainedWork match {
      case None => control.toRecord()
      case Some(reference) =>
        val retained = scenario.readRetainedWork(reference).business[V.WorkflowRetainedWorkReadResult]
        retained.availability shouldBe "available"
        retained.reference shouldBe reference
        reference.sessionId shouldBe control.submitSessionId.getOrElse(control.sessionId)
        val detail = take(V.GoalPhaseAdvanceResult.createC(retained.response))
        detail.sessionId shouldBe control.sessionId
        detail.kind shouldBe control.kind
        detail.submitOperation shouldBe control.submitOperation
        detail.submitSessionId shouldBe control.submitSessionId
        detail.resultType shouldBe control.resultType
        detail.issuedWorkContext shouldBe control.issuedWorkContext
        detail.repairProgress shouldBe control.repairProgress
        detail.issuedWorkContext.foreach { context =>
          context.runId shouldBe reference.sessionId
          context.continuationId shouldBe reference.identity
          context.expectedRevision shouldBe reference.revision
          context.completionOperation.getString("operation") shouldBe Some(reference.completionOperation)
        }
        retained.response
    }
    val complete = control.retainedMaterials.foldLeft(work) { (prior, field) =>
      val retained = scenario.readRetainedSnapshot(field.reference).business[V.WorkflowRetainedSnapshotReadResult]
      retained.availability shouldBe "available"
      retained.reference shouldBe field.reference
      val overlay = control.controlSummary.flatMap(_.getRecord(field.fieldName)).getOrElse(Record.empty)
      val material = Record.createFull(retained.material.fields.filterNot(value => overlay.getAny(value.key).nonEmpty)
        .map(value => value.key -> value.value.single)) ++ overlay
      Record.createFull(prior.fields.filterNot(_.key == field.fieldName)
        .map(value => value.key -> value.value.single)) ++ Record.data(field.fieldName -> material)
    }
    take(V.GoalPhaseAdvanceResult.createC(complete))
  }

  private def _proposal(scenario: F.Scenario): R.Proposal = R.Proposal("needed-inventory", "1",
    "SPECIFICATION-OWNED: the retained product requires its existing build definition and one concrete new compiled source",
    scenario.plan.copy(revision = "inventory-2", steps = scenario.plan.steps.updated(0,
      scenario.plan.steps.head.copy(ownedPaths = scenario.plan.steps.head.ownedPaths ++ _added))))
  private def _result(outcome: String, proposal: Option[R.Proposal] = None): V.GoalPhaseWorkResult =
    V.GoalPhaseWorkResult(outcome, "SPECIFICATION-OWNED " + outcome, Vector.empty,
      None, None, None, R.publicProposal(proposal), None, None, None, None,
      dependencyValidation = None, diagnosticRepair = None)
  private val _findings = Vector("SPECIFICATION-OWNED: the existing build and new source are required for the selected product",
    "SPECIFICATION-OWNED: implementation and its validation remain incomplete")
  private def _configuredResult(outcome: String, proposal: Option[R.Proposal] = None): V.GoalPhaseWorkResult =
    _result(outcome, proposal).copy(findings = if (outcome == "decision-needed") _findings else Vector.empty,
      acceptance = A.publicReport(Some(A.Report(Vector(A.Observation("behavior",
        if (outcome == "decision-needed") "unsatisfied" else "satisfied",
        if (outcome == "decision-needed") "Required product behavior is not yet validated" else "The selected product behavior was observed")),
        Vector("SPECIFICATION-OWNED observations; inventory continuation does not accept this report")))))
  private def _submit(scenario: F.Scenario, work: V.GoalPhaseAdvanceResult,
    result: V.GoalPhaseWorkResult, name: String): F.Result = scenario.call("command", work.submitOperation.get,
      Vector("--sessionId", work.submitSessionId.get, "--result", _json(result), "--resultReference",
        _json(V.WorkflowArtifactReference(name, "1"))))
  private def _file(scenario: F.Scenario, name: String, value: RecordPresentable): String = {
    val path = scenario.root.resolve(name + ".json")
    Files.writeString(path, _json(value), UTF_8, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  /** Read-only retained envelopes provide provenance evidence, never fixture input. */
  private def _stored(scenario: F.Scenario): Vector[String] = {
    val connection = DriverManager.getConnection("jdbc:sqlite:" + scenario.root.resolve("workflow.sqlite").toUri.toASCIIString + "?mode=ro")
    try {
      val statement = connection.createStatement()
      try {
        val tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")
        val names = Vector.newBuilder[String]
        try { while (tables.next()) names += tables.getString(1) } finally tables.close()
        names.result().flatMap { name =>
          val rows = statement.executeQuery("SELECT * FROM \"" + name.replace("\"", "\"\"") + "\"")
          val values = Vector.newBuilder[String]
          try { while (rows.next()) (1 to rows.getMetaData.getColumnCount).foreach { column =>
            Option(rows.getString(column)).filter(_.startsWith("cncf.workflow-json.v1:")).foreach(values += _)
          } } finally rows.close()
          values.result()
        }
      } finally statement.close()
    } finally connection.close()
  }
  /** The existing native acknowledgement hook interrupts after durable intent,
    * before its result. Production state is written only by the public Operation.
    */
  private def _unobservedStage(scenario: F.Scenario, session: String): Unit = {
    val config = scenario.root.resolve(".textus/config.json")
    val assembly = scenario.root.resolve(".textus/assembly.yaml")
    val configBytes = Files.readAllBytes(config)
    val assemblyBytes = Files.readAllBytes(assembly)
    val parameters = _read(config).asObject.get.toVector.map { (key, value) => key -> value.asString.getOrElse(value.noSpaces) }.toMap
    val point = "goal-candidate-prepared:value.txt"
    var observed = false
    val acknowledgement: String => Consequence[Unit] = current =>
      if (current == point) { observed = true; Consequence.stateConflict("SPECIFICATION-OWNED interruption after durable stage intent") }
      else Consequence.unit
    try _with_runtime_(scenario.root, "Goal", Some(scenario.host), hostParameters = parameters,
      goalAcknowledgement = Some(acknowledgement), componentInstance = "default") { (owner, component, actor, _) =>
      val result = owner.executeOperationResponseInContext(Request.of(component = component.name,
        service = "GoalPhaseService", operation = "advanceGoalPhase", properties = NativeWorkflowOperationInputFixture.advance(session)), actor)
      result.isSuccess shouldBe false
      observed shouldBe true
    } finally {
      Files.write(config, configBytes)
      Files.write(assembly, assemblyBytes)
    }
  }

  if (!isolated_review_inventory && !isolated_human_inventory) "AUTO-FILE-GP ordinary native and installed current inventory" should afterWord(
    "in spec:goal-phase-current-inventory-continuation, example:E1, rules:R1,R2,R3,R4, phase:AUTO-FILE-GP-01") {
    "append an existing unchanged file and new source without Human and retain them through checks review and actual Step closure" in {
      Given("docs/spec/goal-phase-current-inventory-continuation.md R1–R3 E1: one small isolated normal host and specification-owned semantic inputs")
      val scenario = _fresh("auto-file-gp-native", configured = true)
      val task = _start(scenario)
      val original = _advance(scenario, task)
      val repository = Path.of(scenario.host.target.root)
      val build = Files.readString(repository.resolve("build.sbt"), UTF_8)
      val proposal = _proposal(scenario)
      Files.exists(repository.resolve(_source)) shouldBe false
      When("the actual issued implementation submits its concrete inventory-only proposal and ordinary Advance evaluates it")
      val inventoryResult = _configuredResult("decision-needed", Some(proposal))
      _submit(scenario, original, inventoryResult.copy(acceptance = None), "missing-required-report").exitCode should not be 0
      _advance(scenario, task) shouldBe original
      _submit(scenario, original, inventoryResult, "actual-inventory-source")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val old = _stored(scenario)
      val continued = _advance(scenario, task)
      Then("the same Session issues the current implementation under the appended inventory without a fresh Human grant")
      continued.kind shouldBe "Request"
      continued.human shouldBe None
      continued.work.get.kind shouldBe "ImplementStep"
      continued.sessionId shouldBe original.sessionId
      continued.work.get.stepIdentity shouldBe original.work.get.stepIdentity
      continued.work.get.originalBase shouldBe original.work.get.originalBase
      continued.work.get.completed shouldBe original.work.get.completed
      continued.repairProgress shouldBe original.repairProgress
      continued.work.get.selectedPlan shouldBe original.work.get.selectedPlan.copy(revision = "inventory-2",
        steps = original.work.get.selectedPlan.steps.updated(0, original.work.get.selectedPlan.steps.head.copy(
          ownedPaths = original.work.get.selectedPlan.steps.head.ownedPaths ++ _added)))
      Files.readString(repository.resolve("build.sbt"), UTF_8) shouldBe build
      _advance(scenario, task) shouldBe continued
      val retained = _stored(scenario)
      val semantic = old.filter(value => value.contains("actual-inventory-source") &&
        value.contains("sm-workflow.goal-phase-native-work-result.v1"))
      semantic should not be empty
      semantic.foreach(value => retained should contain(value))
      retained.exists(_.contains(C.decision)) shouldBe true
      retained.exists(_.contains(C.causation)) shouldBe true
      retained.exists(_.contains("actual-inventory-source")) shouldBe true
      _findings.foreach(finding => retained.exists(_.contains(finding)) shouldBe true)
      retained.exists(_.contains("Required product behavior is not yet validated")) shouldBe true
      retained.exists(_.contains(":ordered-human:")) shouldBe false

      Given("the actual appended source and product change, with no new host file registration")
      Files.createDirectories(repository.resolve(_source).getParent)
      Files.writeString(repository.resolve(_source), "object NeededInventory { val value = 42 }\n", UTF_8)
      Files.writeString(repository.resolve("value.txt"), "expected\n", UTF_8)
      When("ordinary Submit and Advance stage the actual candidate and execute the selected native Test")
      _submit(scenario, continued, _configuredResult("completed"), "inventory-implementation")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val checked = scenario.advance(task)
      val review = _advance(scenario, checked)
      Then("native independent review receives the appended Plan, concrete source diff and actual successful checks")
      review.work.get.kind shouldBe "ReviewStep"
      review.work.get.selectedPlan shouldBe continued.work.get.selectedPlan
      review.work.get.review.get.toString should include(_source)
      review.work.get.review.get.checks.head.exitCode shouldBe Some(0)
      review.work.get.review.get.checks.head.sbt.get.outcome shouldBe "succeeded"
      _advance(scenario, task) shouldBe review

      Given("separate specification-owned independent review evidence for this exact actual candidate")
      val result = _configuredResult("accept")
      val reference = V.WorkflowArtifactReference("inventory-independent-review", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-independent-reviewer"), None, None)
      val prepared = scenario.prepareHostInput("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "review",
        _observed_resource(checked), _file(scenario, "review-result", result),
        _file(scenario, "review-reference", reference), _file(scenario, "review-execution", execution),
        "normal-development-host", Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence", None).toRecord())
      scenario.selectPreparedImport(prepared)
      When("the ordinary review Submit and subsequent closure receive their separately prepared native evidence")
      scenario.call("command", review.submitOperation.get, Vector("--sessionId", review.submitSessionId.get,
        "--result", _json(result), "--resultReference", _json(reference), "--executionEvidence", _json(execution)))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val waiting = scenario.advance(task)
      _advance(scenario, waiting).authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
      val authority = scenario.prepareHostInput("prepareAuthority", V.WorkflowAuthorityPreparationInput(
        _observed_resource(waiting), _file(scenario, "step-authority", V.WorkflowArtifactReference("inventory-step-authority", "1")),
        "participant", "normal-development-host", Instant.now().minusSeconds(60).toString,
        "entity:smworkflow.cli.host_evidence").toRecord())
      scenario.selectPreparedImport(authority)
      val next = _advance(scenario, task)
      Then("a real scoped Step commit contains the new source and the next Step retains original base and repair accounting")
      next.work.get.kind shouldBe "ImplementStep"
      next.work.get.stepIdentity shouldBe Some("step-2")
      next.work.get.originalBase shouldBe original.work.get.originalBase
      next.work.get.completed.size shouldBe 1
      next.work.get.completed.head.commit.identity shouldBe scenario.candidateHead
      next.work.get.completed.head.commit.parent shouldBe original.work.get.originalBase.head
      next.repairProgress shouldBe original.repairProgress
      Files.readString(repository.resolve(_source), UTF_8) should include("value = 42")
      val repeated = _advance(scenario, task)
      // The one-time commit-hygiene note is presentation; every business field stays exact.
      repeated.copy(message = next.message) shouldBe next
      scenario.candidateHead shouldBe next.work.get.completed.head.commit.identity

      Given("the completed first Step and an accepted inventory proposal for the remaining Step reusing its revision")
      val secondPlan = next.work.get.selectedPlan
      val internalplan = _proposal(scenario).plan
      val secondProposal = R.Proposal("second-inventory", "1", "SPECIFICATION-OWNED second Step needs one source",
        internalplan.copy(steps = internalplan.steps.updated(1,
          internalplan.steps(1).copy(ownedPaths = internalplan.steps(1).ownedPaths :+ "src/SecondNeeded.scala"))))
      secondProposal.plan.revision shouldBe secondPlan.revision
      val secondResult = _configuredResult("decision-needed", Some(secondProposal))
      When("the accepted repeated revision passes normal host path expansion and coverage before native target capture")
      _submit(scenario, next, secondResult, "second-colliding-inventory")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val acceptedSecond = _stored(scenario)
      val originalSecondResult = acceptedSecond.filter(value => value.contains("second-colliding-inventory") &&
        value.contains("sm-workflow.goal-phase-native-work-result.v1"))
      originalSecondResult should not be empty
      val secondContinued = _advance(scenario, task)
      Then("the original base, accepted commit chain, duties and prior history survive subsequent reentry")
      secondContinued.kind shouldBe "Request"
      secondContinued.human shouldBe None
      secondContinued.work.get.stepIdentity shouldBe next.work.get.stepIdentity
      secondContinued.work.get.originalBase shouldBe original.work.get.originalBase
      secondContinued.work.get.completed shouldBe next.work.get.completed
      secondContinued.repairProgress shouldBe next.repairProgress
      secondContinued.work.get.selectedPlan.revision should not be secondPlan.revision
      secondContinued.work.get.selectedPlan shouldBe secondPlan.copy(revision = secondContinued.work.get.selectedPlan.revision,
        steps = secondPlan.steps.updated(1, secondPlan.steps(1).copy(
          ownedPaths = secondPlan.steps(1).ownedPaths :+ "src/SecondNeeded.scala")))
      val restored = _stored(scenario)
      originalSecondResult.foreach(value => restored should contain(value))
      restored.exists(value => value.contains("revisionCorrection") && value.contains(secondPlan.revision) &&
        value.contains(secondContinued.work.get.selectedPlan.revision)) shouldBe true
      restored.exists(_.contains("second-colliding-inventory")) shouldBe true
      restored.exists(_.contains(":ordered-human:")) shouldBe false
      _advance(scenario, task) shouldBe secondContinued
    }

    "retain a purpose concern as Human and supersede only a proven unanswered inventory question without an answer" in {
      Given("docs/spec/goal-phase-current-inventory-continuation.md R3,R4 E2: an explicit purpose revision in a small normal host")
      val concern = _fresh("auto-file-gp-concern", configured = true)
      val task = _start(concern)
      val initial = _advance(concern, task)
      val purpose = R.Proposal("actual-purpose-concern", "1", "SPECIFICATION-OWNED unresolved product purpose",
        concern.plan.copy(revision = "purpose-2", steps = concern.plan.steps.updated(0,
          concern.plan.steps.head.copy(purpose = "Implement a different product requirement"))))
      When("the authentic semantic completion changes the product purpose")
      _submit(concern, initial, _configuredResult("decision-needed", Some(purpose)), "purpose-source")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val question = _advance(concern, task)
      Then("the real judgment remains an unanswered ordinary Human question")
      question.kind shouldBe "Human"
      _advance(concern, task) shouldBe question
      _stored(concern).exists(_.contains(C.decision)) shouldBe false

      Given("an actual current implementation reporting both a concrete inventory proposal and an unresolved finding")
      val mixed = _fresh("auto-file-gp-mixed-finding")
      val mixedtask = _start(mixed)
      val mixedwork = _advance(mixed, mixedtask)
      val mixedresult = _result("decision-needed", Some(_proposal(mixed))).copy(
        findings = Vector("SPECIFICATION-OWNED unresolved requirement judgment alongside the needed files"))
      When("ordinary semantic Submit retains the complete mixed result and Advance considers automatic inventory")
      _submit(mixed, mixedwork, mixedresult, "mixed-inventory-finding-source")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val mixedquestion = _advance(mixed, mixedtask)
      Then("the genuine finding remains Human and reopening never supersedes it with an automatic inventory receipt")
      mixedquestion.kind shouldBe "Human"
      _advance(mixed, mixedtask) shouldBe mixedquestion
      val mixedstored = _stored(mixed)
      mixedstored.exists(_.contains("mixed-inventory-finding-source")) shouldBe true
      mixedstored.exists(_.contains(mixedresult.findings.head)) shouldBe true
      mixedstored.exists(_.contains(C.decision)) shouldBe false
      mixedstored.exists(_.contains(C.humanCausation)) shouldBe false

      Given("an actual fixed host with a missing literal capability which has already issued a file-only question")
      val pending = _fresh("auto-file-gp-unanswered", normal = false, configured = true)
      val pendingtask = _start(pending)
      val work = _advance(pending, pendingtask)
      _submit(pending, work, _configuredResult("decision-needed", Some(_proposal(pending))), "unanswered-inventory-source")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val unanswered = _advance(pending, pendingtask)
      unanswered.kind shouldBe "Human"
      val old = _stored(pending)
      When("normal standard composition admits invocation-local inventory under the original purpose")
      _normal(pending)
      val staleDescription = pending.call("command", GoalPhaseRemainingPlanAdoption.describeOperation, Vector("--input",
        _json(V.GoalPhaseRemainingPlanDescriptionInput(work.sessionId, R.publicProposal(Some(_proposal(pending))).get))))
        .business[V.GoalPhaseRemainingPlanDescriptionResult]
      val continued = _advance(pending, pendingtask)
      Then("native supersession retains the actual question and source result without manufacturing a Human answer")
      continued.work.get.kind shouldBe "ImplementStep"
      continued.human shouldBe None
      continued.work.get.selectedPlan.steps.head.ownedPaths shouldBe work.work.get.selectedPlan.steps.head.ownedPaths ++ _added
      continued.work.get.originalBase shouldBe work.work.get.originalBase
      continued.repairProgress shouldBe work.repairProgress
      _advance(pending, pendingtask) shouldBe continued
      val retained = _stored(pending)
      val semantic = old.filter(value => value.contains("unanswered-inventory-source") &&
        value.contains("sm-workflow.goal-phase-native-work-result.v1"))
      semantic should not be empty
      semantic.foreach(value => retained should contain(value))
      _findings.foreach(finding => retained.exists(_.contains(finding)) shouldBe true)
      retained.exists(_.contains("Required product behavior is not yet validated")) shouldBe true
      retained.exists(_.contains(C.humanCausation)) shouldBe true
      retained.exists(_.contains(C.decision)) shouldBe true
      retained.exists(_.contains("AdoptPlanRevision\",\"rationale")) shouldBe false
      When("the old public remaining-Plan target is used after the actual native question has been superseded")
      val stale = pending.call("command", GoalPhaseRemainingPlanAdoption.adoptionOperation, Vector("--input",
        _json(V.GoalPhaseRemainingPlanAdoptionInput(work.sessionId, staleDescription.target,
          R.publicProposal(Some(_proposal(pending))).get, "SPECIFICATION-OWNED deliberately stale target; no Human consent claimed",
          V.WorkflowArtifactReference("stale-inventory-target", "1")))))
      Then("the retired target cannot change the current work or manufacture an answer")
      stale.exitCode should not be 0
      _advance(pending, pendingtask) shouldBe continued
    }

    "retain progressing actual Repair observations and refuse stale or foreign work while pending work has no completion" in {
      Given("docs/spec/goal-phase-current-inventory-continuation.md R2,R3: a configured native host with an actual incomplete semantic assignment")
      val scenario = _fresh("auto-file-gp-repair", configured = true)
      val task = _start(scenario)
      val original = _advance(scenario, task)
      When("Advance has no completion observation, including when an external worker's progress is unreported")
      val pending = _advance(scenario, task)
      Then("the same pending work remains pending; no live/unknown worker outcome is inferred or adopted")
      pending shouldBe original
      _stored(scenario).exists(_.contains(C.decision)) shouldBe false

      Given("another authenticated owner using the same synthetic Session")
      val assembly = scenario.root.resolve(".textus/assembly.yaml")
      val normal = Files.readString(assembly, UTF_8)
      Files.writeString(assembly, normal.replace("id: participant", "id: foreign-participant"), UTF_8)
      When("the foreign owner calls the normal public Advance")
      val foreign = try scenario.advance(task) finally Files.writeString(assembly, normal, UTF_8)
      Then("ownership is refused and the original owner's current work remains exact")
      foreign.exitCode should not be 0
      _advance(scenario, task) shouldBe original

      Given("a specification-owned candidate with successful checks and an actual independent Review requiring Repair")
      Files.writeString(Path.of(scenario.host.target.root).resolve("value.txt"), "expected\n", UTF_8)
      _submit(scenario, original, _configuredResult("completed"), "actual-repair-initial")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val checked = scenario.advance(task)
      val review = _advance(scenario, checked)
      review.work.get.kind shouldBe "ReviewStep"
      val reviewResult = _configuredResult("decision-needed").copy(outcome = "repair",
        findings = Vector("Required product behavior is not yet validated"))
      val reviewReference = V.WorkflowArtifactReference("actual-repair-origin-review", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reviewReference), Some("specification-independent-reviewer"), None, None)
      val prepared = scenario.prepareHostInput("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "review",
        _observed_resource(checked), _file(scenario, "repair-review-result", reviewResult),
        _file(scenario, "repair-review-reference", reviewReference), _file(scenario, "repair-review-execution", execution),
        "normal-development-host", Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence", None).toRecord())
      scenario.selectPreparedImport(prepared)
      scenario.call("command", review.submitOperation.get, Vector("--sessionId", review.submitSessionId.get,
        "--result", _json(reviewResult), "--resultReference", _json(reviewReference), "--executionEvidence", _json(execution)))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val repair = _advance(scenario, task)
      repair.work.get.kind shouldBe "RepairStep"
      repair.work.get.review should not be empty
      val assigned = repair.work.get.issues.get.issues.filter(issue => issue.repository == repair.work.get.selectedPlan.repository &&
        issue.step == repair.work.get.stepIdentity.get && issue.status != "RESOLVED" && issue.status != "DECLINED")
      assigned should not be empty
      val progressing = GoalPhaseFixAssessment.Report(GoalPhaseFixAssessment.Change("STANDARD_LOGIC", Vector("selected-product"),
        false, false, "SPECIFICATION-OWNED same-purpose repair", false, false),
        GoalPhaseFixAssessment.Convergence("PROGRESSING", "HIGH", "SPECIFICATION-OWNED inventory permits the existing correction"))
      val proposal = _proposal(scenario).copy(plan = _proposal(scenario).plan.copy(revision = repair.work.get.selectedPlan.revision))
      val result = _configuredResult("decision-needed", Some(proposal)).copy(
        fixAssessment = GoalPhaseFixAssessment.publicReport(Some(progressing)),
        issueReport = Some(V.GoalPhaseIssueReport(assigned.map(issue => V.GoalPhaseIssueDisposition(issue.identity,
          "BLOCKED", "SPECIFICATION-OWNED existing issue remains unresolved while needed files are supplied", None)))))
      When("the actual Repair returns its required report, descriptive findings, retained BLOCKED issues and progressing metadata")
      _submit(scenario, repair, result, "actual-progressing-inventory")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val continued = _advance(scenario, task)
      Then("native continuation retains all original evidence and accounting without accepting or resolving the repair")
      continued.work.get.kind shouldBe "ImplementStep"
      continued.human shouldBe None
      continued.work.get.selectedPlan.revision should not be repair.work.get.selectedPlan.revision
      continued.work.get.selectedPlan.steps.head.ownedPaths shouldBe original.work.get.selectedPlan.steps.head.ownedPaths ++ _added
      continued.work.get.originalBase shouldBe original.work.get.originalBase
      continued.repairProgress shouldBe repair.repairProgress
      assigned.foreach(issue => continued.work.get.issues.get.issues.find(_.identity == issue.identity).get.status shouldBe issue.status)
      val stored = _stored(scenario)
      stored.exists(_.contains("actual-progressing-inventory")) shouldBe true
      stored.exists(_.contains("actual-repair-origin-review")) shouldBe true
      stored.exists(_.contains("revisionCorrection")) shouldBe true
      stored.exists(_.contains(progressing.convergence.rationale)) shouldBe true
      stored.exists(_.contains("PROGRESSING")) shouldBe true
      _findings.foreach(finding => stored.exists(_.contains(finding)) shouldBe true)

      _advance(scenario, task) shouldBe continued
    }

    "hold an actual unobserved stage intent without interpreting it as a completed inventory source" in {
      Given("docs/spec/goal-phase-current-inventory-continuation.md R3: configured acceptance and a native durable stage intent without its result")
      val scenario = _fresh("auto-file-gp-unknown", configured = true)
      val task = _start(scenario)
      val original = _advance(scenario, task)
      Files.writeString(Path.of(scenario.host.target.root).resolve("value.txt"), "expected\n", UTF_8)
      _submit(scenario, original, _configuredResult("completed"), "unknown-stage-implementation")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      _unobservedStage(scenario, original.sessionId)
      val before = _stored(scenario)
      before.exists(_.contains("ordered-candidate:stage-intent-0")) shouldBe true
      before.exists(_.contains("ordered-candidate:stage-result-0")) shouldBe false
      When("normal public Advance reopens an effect whose result and termination remain unknown to the runtime")
      val waiting = _advance(scenario, task)
      Then("it waits without adopting an inventory, replaying a stage or claiming that an external process ended")
      waiting.kind shouldBe "Wait"
      _advance(scenario, task) shouldBe waiting
      val after = _stored(scenario)
      after.exists(_.contains("ordered-candidate:stage-result-0")) shouldBe false
      after.exists(_.contains(C.decision)) shouldBe false
      before.foreach(value => after should contain(value))
    }
  }

  if (isolated_review_inventory || isolated_human_inventory)
    (if (isolated_human_inventory) "Human-adopted current inventory through standard CLI processes"
      else "Review-backed current inventory through ordinary CLI processes") should {
    "retain the inventory and review observation through reopening and record it once after the reviewed commit" in {
      Given("a small isolated host with an independent blocking review and one nonblocking observation")
      val scenario = _fresh(if (isolated_human_inventory) "human-inventory-cli" else "review-inventory-cli")
      val config = scenario.root.resolve(".textus/config.json")
      val policy = DevelopmentJournal.Policy("workflow", org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(
        org.simplemodeling.model.datatype.EntityCollectionId("smworkflow", "cli", "development_journal")))
      val journal = F.JournalHost(policy).values
      val current = _read(config)
      val storage = parse(current.hcursor.get[String]("textus.subsystem.storage.plan").toOption.get).toOption.get
      val extra = parse(journal("textus.subsystem.storage.plan")).toOption.get
      val records = (storage.hcursor.get[Vector[Json]]("records").toOption.get ++
        extra.hcursor.get[Vector[Json]]("records").toOption.get).distinct
      Files.writeString(config, current.mapObject(_.add(DevelopmentJournal.declaration.name,
        Json.fromString(journal(DevelopmentJournal.declaration.name))).add("textus.subsystem.storage.plan",
        Json.fromString(storage.mapObject(_.add("records", Json.fromValues(records))).noSpaces))).noSpaces, UTF_8)
      val task = _start(scenario)
      val original = _advance(scenario, task)
      val repository = Path.of(scenario.host.target.root)
      Files.writeString(repository.resolve("value.txt"), "expected\n", UTF_8)
      _submit(scenario, original, _result("completed"), "review-inventory-initial")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val checked = scenario.advance(task)
      val firstreview = _advance(scenario, checked)
      firstreview.work.get.kind shouldBe "ReviewStep"
      def _review_(work: V.GoalPhaseAdvanceResult, evidence: F.Result, result: V.GoalPhaseWorkResult, name: String): Unit = {
        val reference = V.WorkflowArtifactReference(name, "1")
        val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-independent-reviewer"), None, None)
        val prepared = scenario.prepareHostInput("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "review",
          _observed_resource(evidence), _file(scenario, name + "-result", result), _file(scenario, name + "-reference", reference),
          _file(scenario, name + "-execution", execution), "normal-development-host", Instant.now().minusSeconds(60).toString,
          "entity:smworkflow.cli.host_evidence", None).toRecord())
        scenario.selectPreparedImport(prepared)
        scenario.call("command", work.submitOperation.get, Vector("--sessionId", work.submitSessionId.get,
          "--result", _json(result), "--resultReference", _json(reference), "--executionEvidence", _json(execution)))
          .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      }
      val observation = V.GoalPhaseReviewObservation("review-inventory-note", "HYGIENE", "Clarify the selected name",
        "A nonblocking observation from the original independent review")
      val first = _result("repair").copy(findings = Vector("Correct the existing selected behavior"),
        reviewObservations = Some(V.GoalPhaseReviewObservations(Vector(observation))))
      _review_(firstreview, checked, first, "review-inventory-original-review")
      val repair = _advance(scenario, task)
      repair.work.get.kind shouldBe "RepairStep"
      repair.work.get.review should not be empty
      val assigned = repair.work.get.issues.get.issues.filter(issue => issue.status != "RESOLVED" && issue.status != "DECLINED")
      val blocked = V.GoalPhaseIssueReport(assigned.map(issue => V.GoalPhaseIssueDisposition(issue.identity,
        "BLOCKED", "The original issue awaits the required inventory", None)))
      val dependency = GoalPhaseDependencyScope.Proposal("specification-dependency-concern", "1", "REPAIR",
        GoalPhaseDependencyScope.Component("SmWorkflow", scenario.plan.repository, "repository"),
        GoalPhaseDependencyScope.Component("OtherComponent", "other-library", "repository"),
        org.goldenport.cncf.workflow.ContextReference("specification-usage", "1"),
        "SPECIFICATION-OWNED actual dependency concern requires independent Human direction",
        Vector("src/other.scala"), Vector("Preserve the selected product"),
        Vector(org.goldenport.cncf.workflow.ContextReference("step-1", "1")), Vector.empty)
      val inventory = if (isolated_human_inventory) _result("decision-needed").copy(issueReport = Some(blocked),
        dependencyProposal = GoalPhaseDependencyScope.publicProposal(Some(dependency)))
        else _result("decision-needed", Some(_proposal(scenario))).copy(issueReport = Some(blocked))
      When("the issued repair returns its genuine typed decision and later CLI invocations reopen it")
      _submit(scenario, repair, inventory, "review-inventory-repair-result").business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val beforeadoption = if (isolated_human_inventory) {
        val question = _advance(scenario, task)
        question.kind shouldBe "Human"
        _advance(scenario, task) shouldBe question
        val retained = _stored(scenario)
        retained.exists(_.contains(C.decision)) shouldBe false
        retained.exists(_.contains(C.humanCausation)) shouldBe false
        Given("the exact fixed expanded capability vector and independently prepared genuine Human remaining-Plan evidence")
        // Unused capability precedes the needed source deliberately: restoring
        // only Plan-owned paths would change the recorded staging ordinal.
        val paths = scenario.host.goal.candidatePaths ++ Vector("src/main/scala/UnusedCapability.scala") ++ _added
        val host = scenario.host.copy(goal = scenario.host.goal.copy(candidatePaths = paths))
        Files.writeString(config, _read(config).mapObject(_.add("sm-workflow.runtime.definition",
          Json.fromString(ThreeWorkflowRuntimeParameters.encode(host).noSpaces))).noSpaces, UTF_8)
        val proposal = R.publicProposal(Some(_proposal(scenario))).get
        val described = scenario.call("command", GoalPhaseRemainingPlanAdoption.describeOperation,
          Vector("--input", _json(V.GoalPhaseRemainingPlanDescriptionInput(repair.sessionId, proposal))))
        val description = described.business[V.GoalPhaseRemainingPlanDescriptionResult]
        val target = take(GoalPhaseRemainingPlanAdoption.targetC(description.target))
        target.currentInventory.get.candidatePaths shouldBe paths
        target.currentInventory.get.appendedPaths shouldBe _added
        target.base.head shouldBe repair.work.get.originalBase.head
        target.base.tree shouldBe repair.work.get.originalBase.tree
        target.completed.map(value => value.step -> value.commit.identity) shouldBe
          repair.work.get.completed.map(value => value.step -> value.commit.identity)
        val direction = V.GoalPhaseRemainingPlanAdoptionInput(repair.sessionId, description.target, proposal,
          "SPECIFICATION-OWNED actual Human supersedes the dependency concern with the unchanged product inventory",
          V.WorkflowArtifactReference("human-inventory-directive", "1"))
        val execution = V.WorkflowExecutionEvidence(Vector(direction.reference), Some("participant"), None, None)
        val prepared = scenario.prepareHostInput("prepareEvidence", V.WorkflowEvidencePreparationInput(
          "GoalPhase", "remaining-plan-adoption", described.invocation.stdout.toUri.toASCIIString,
          _file(scenario, "human-inventory-direction", direction), _file(scenario, "human-inventory-reference", direction.reference),
          _file(scenario, "human-inventory-execution", execution), "normal-development-host",
          Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence", Some("participant")).toRecord())
        scenario.selectPreparedImport(prepared)
        scenario.call("command", GoalPhaseRemainingPlanAdoption.adoptionOperation, Vector("--input", _json(direction)))
          .business[V.GoalPhaseRemainingPlanAdoptionResult].accepted shouldBe true
        _normal(scenario)
        _read(config).hcursor.downField("sm-workflow.runtime.definition").focus shouldBe None
        Given("the unchanged accepted proposal, the existing Phase note and product input with no checklist items")
        val reconstruction = V.GoalPhaseDevelopmentReconstructionInput(repair.sessionId, "docs/phase.md", "value.txt",
          proposal, "SPECIFICATION-OWNED reconstruct the unchanged accepted Plan from the fixture Phase note and " +
            "the item-free product input selected as checklist source; preserve the genuine Human inventory")
        When("standard composition reconstructs current development before another implementation is issued")
        val reconstructed = _advance(scenario, scenario.call("command", GoalPhaseDevelopmentReconstruction.operation,
          Vector("--input", _json(reconstruction))))
        Then("the native reconstruction waits in the same Session and retains the authenticated inventory history")
        reconstructed.kind shouldBe "Wait"
        reconstructed.sessionId shouldBe repair.sessionId
        reconstructed.repairProgress shouldBe repair.repairProgress
        _stored(scenario).exists(_.contains(GoalPhaseDevelopmentReconstruction.causation)) shouldBe true
        Some(retained.filter(value => value.contains("review-inventory-repair-result") &&
          value.contains("sm-workflow.goal-phase-native-work-result.v1")))
      } else None
      val continued = _advance(scenario, task)
      continued.work.get.kind shouldBe "ImplementStep"
      continued.repairProgress shouldBe repair.repairProgress
      continued.work.get.selectedPlan.steps.head.ownedPaths shouldBe repair.work.get.selectedPlan.steps.head.ownedPaths ++ _added
      continued.work.get.originalBase shouldBe repair.work.get.originalBase
      _advance(scenario, task) shouldBe continued
      beforeadoption.foreach { previous =>
        previous should not be empty
        val restored = _stored(scenario)
        previous.foreach(value => restored should contain(value))
        restored.exists(_.contains(C.decision)) shouldBe false
        restored.exists(_.contains(C.humanCausation)) shouldBe false
        restored.exists(_.contains("human-inventory-directive")) shouldBe true
        restored.exists(_.contains(GoalPhaseOrderedHumanDecision.inputCodec.typeIdentity)) shouldBe true
        GoalPhaseOrderedHumanDecision.choices.foreach { choice =>
          restored.exists(_.contains(choice + "\",\"rationale")) shouldBe false
        }
      }
      val key = DevelopmentJournalFinding.identity(org.goldenport.cncf.workflow.ContextReference(
        "review-inventory-original-review", "1"), observation.identity)
      scenario.readJournalEntry(V.JournalReadInput(key, None)).business[V.JournalReadResult].status shouldBe "absent"
      Files.createDirectories(repository.resolve(_source).getParent)
      Files.writeString(repository.resolve(_source), "object NeededInventory { val value = 42 }\n", UTF_8)
      _submit(scenario, continued, _result("completed"), "review-inventory-completed")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val rechecked = scenario.advance(task)
      val rereview = _advance(scenario, rechecked)
      rereview.work.get.kind shouldBe "ReviewStep"
      rereview.work.get.selectedPlan shouldBe continued.work.get.selectedPlan
      rereview.work.get.originalBase shouldBe repair.work.get.originalBase
      rereview.repairProgress shouldBe repair.repairProgress
      rereview.work.get.review.get.toString should include(_source)
      rereview.work.get.review.get.checks.head.exitCode shouldBe Some(0)
      rereview.work.get.review.get.checks.head.sbt.get.outcome shouldBe "succeeded"
      _advance(scenario, task) shouldBe rereview
      val reviewedissues = rereview.work.get.issues.get.issues.filter(issue => issue.status != "RESOLVED" && issue.status != "DECLINED")
      val resolved = V.GoalPhaseIssueReport(reviewedissues.map(issue => V.GoalPhaseIssueDisposition(issue.identity,
        "RESOLVED", "Independent review covers the corrected behavior", None)))
      _review_(rereview, rechecked, _result("accept").copy(issueReport = Some(resolved)), "review-inventory-final-review")
      val waiting = scenario.advance(task)
      val closure = _advance(scenario, waiting)
      closure.findingRecording.foreach(_.pending.map(_.observation.identity) shouldBe Vector(observation.identity))
      scenario.readJournalEntry(V.JournalReadInput(key, None)).business[V.JournalReadResult].status shouldBe "absent"
      val authority = scenario.prepareHostInput("prepareAuthority", V.WorkflowAuthorityPreparationInput(
        _observed_resource(waiting), _file(scenario, "review-inventory-authority", V.WorkflowArtifactReference("review-inventory-authority", "1")),
        "participant", "normal-development-host", Instant.now().minusSeconds(60).toString,
        "entity:smworkflow.cli.host_evidence").toRecord())
      scenario.selectPreparedImport(authority)
      val next = _advance(scenario, task)
      Then("the original review observation records once only after the accepted Step commit")
      next.work.get.stepIdentity shouldBe Some("step-2")
      val entry = scenario.readJournalEntry(V.JournalReadInput(key, None)).business[V.JournalReadResult].entry.get
      entry.sourceReference shouldBe V.WorkflowArtifactReference("review-inventory-original-review", "1")
      entry.finding.get.completed.last.commit.identity shouldBe next.work.get.completed.last.commit.identity
      next.work.get.completed.last.commit.identity should not be repair.work.get.originalBase.head
      next.work.get.completed.last.step shouldBe "step-1"
      next.work.get.originalBase shouldBe repair.work.get.originalBase
      val again = _advance(scenario, task)
      again.findingRecording shouldBe next.findingRecording
      scenario.readJournalEntry(V.JournalReadInput(key, None)).business[V.JournalReadResult].entry shouldBe Some(entry)
      val recorded = scenario.findJournalEntries(V.JournalFindInput(scenario.plan.repository, Some(scenario.plan.phase),
        Some(repair.sessionId), Some(DevelopmentJournalFinding.kind), Some(entry.sourceReference), None, None, Some(20)))
        .business[V.JournalFindResult]
      recorded.status shouldBe "found"
      recorded.entries.map(_.entry.identity) shouldBe Vector(key)
      recorded.hasMore shouldBe false
    }
  }
}

@org.scalatest.DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalReviewInventoryCliSpec extends NativeGoalCurrentInventoryCliSpec {
  override protected def isolated_review_inventory: Boolean = true
}

@org.scalatest.DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalHumanInventoryCliSpec extends NativeGoalCurrentInventoryCliSpec {
  override protected def isolated_human_inventory: Boolean = true
}
