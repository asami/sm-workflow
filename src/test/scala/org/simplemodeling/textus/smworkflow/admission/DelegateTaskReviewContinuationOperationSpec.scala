/**
 * @since   Nov. 18, 2026
 * @version Nov. 18, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.wordspec.AnyWordSpec
import BoundedWorkRuntime.given
import DelegateTaskReviewContinuation as R

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class DelegateTaskReviewContinuationOperationSpec extends AnyWordSpec with org.scalatest.matchers.should.Matchers
    with ThreeWorkflowManagedRepositoryFixture {
  import cats.data.NonEmptyVector
  import java.nio.file.{Files, Path}
  import org.goldenport.Consequence
  import org.goldenport.cncf.processexecution.GitOperationKind
  import org.goldenport.cncf.workflow.*
  import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as _, *}
  import org.goldenport.protocol.{Property, Request}
  import org.goldenport.record.Record
  import org.simplemodeling.textus.smworkflow.preparation.NativeDelegateTaskStartInput
  import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
  import org.simplemodeling.textus.smworkflow.value as V
  import StandaloneFixExecution as S
  import S.given
  private def take[A](value: Consequence[A]): A = value.toOption.getOrElse(fail(s"Operation refused: $value"))
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
        root.resolve("upstream.git").toString), kinds = TypedGitHostPlanFixture.observations ++ Vector(
          GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff), goalPaths = Vector("docs/phase.md"))
    // The real Git observation must retain the complete larger candidate diff.
    val largeGit = git.mapObject(fields => fields.add("gitPrograms", fields("gitPrograms").get.mapArray(_.map { program =>
      program.mapObject(p => p.add("limits", p("limits").get.mapObject(_.add("stdoutBytes", 524288.asJson))))
    })))
    val base = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(largeGit), stdoutBytes = 2097152)
    val workspace = Json.obj("identity" -> "sm-workflow-validation-artifacts".asJson,
      "root" -> root.resolve("capture-root").toString.asJson)
    val grant = Json.obj("componentId" -> org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name.asJson,
      "workspaceId" -> "sm-workflow-validation-artifacts".asJson, "access" -> "manage".asJson)
    base.mapObject(fields => fields.add("workspaces", fields("workspaces").get.mapArray(_ :+ workspace))
      .add("workspaceGrants", fields("workspaceGrants").get.mapArray(_ :+ grant)))
  }
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    if (!Files.exists(root.resolve("repository/build.sbt"))) {
      TypedSbtHostPlanFixture.build(root, true)
      val product = root.resolve("repository/src/test/scala/ProductCheck.scala")
      val fixtureBytes = if (root.getFileName.toString.contains("large")) 600000 else 500000
      val source = Files.readString(product).replace("    assert(java.nio.file.Files.readString",
        "    print(\"D\" * " + fixtureBytes + "); System.out.flush()\n    assert(java.nio.file.Files.readString")
      Files.writeString(product, source)
    }
    val file = root.resolve("repository/docs/phase.md")
    Files.createDirectories(file.getParent)
    if (!Files.exists(file)) Files.writeString(file, "Specification-owned original source\n")
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] =
    super._setup_commands_(root, scenario).patch(2, Vector(Vector("-C", root.resolve("repository").toString,
      "--literal-pathspecs", "add", "--", "docs/phase.md")), 0)

  private[admission] def qualify(installed: Option[(Path, Path)], large: Boolean = false): Map[String, Int] = {
    import scala.jdk.CollectionConverters.*
    import java.util.concurrent.TimeUnit
    installed.foreach { case (executable, archive) =>
      require(executable.isAbsolute && Files.isExecutable(executable), "Installed command must exist")
      require(archive.isAbsolute && Files.isRegularFile(archive), "Candidate CAR must exist")
    }
    val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/delegate-review-managed").toAbsolutePath), (if (large) "SPEC-large-" else "SPEC-small-")).toRealPath()
    Files.createDirectories(root.resolve("capture-root/validation"))
    val contract = V.DelegateTaskContract("SPEC-requester", "SPEC-destination", "SPEC-requester", "SPEC-component", "destination",
      "documentation", NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"), Vector.empty,
      NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-authority", "1")), None, Vector.empty, "SPEC-owned direction")
    val input = V.DelegateTaskInput("sm-workflow", "SPEC-delegate-review", "SPEC-managed continuation",
      NonEmptyVector.one("docs/phase.md"), NonEmptyVector.one("test"), NonEmptyVector.of("objective", "delivery"),
      NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-original", "1")), true, "documentation", "off", 10,
      "direct-instruction", Vector.empty, contract, Vector.empty)
    val measurements = scala.collection.mutable.Map.empty[String, Int]
    def measure(label: String, value: String): Unit = {
      val size = value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
      withClue(label) { size should be <= 65536 }
      measurements(label) = size
    }
    val candidateText = "Specification-owned original source\n" + ("fixture candidate content 0123456789\n" * (if (large) 4300 else 2300))
    var session = ""
    var originalIssued = Option.empty[Json]
    var originalPublic = Option.empty[V.DelegateTaskView]
    var description = Option.empty[V.DelegateTaskReviewContinuationDescriptionResult]
    var emittedWork = Option.empty[V.WorkflowRetainedWorkReference]
    var successor = Option.empty[V.DelegateTaskView]
    val parameters = BoundedWorkExecutionAdmissionFixture.logicalParameters ++ Map(
      "textus.resource.url.file.roots" -> root.toString, "textus.resource.read.max-bytes" -> "16777216")
    def installedRecord(operation: String, argument: Vector[String],
      service: String = "DelegateTaskService"): Record = {
      val (_, archive) = installed.get
      val logs = Files.createTempDirectory(root, "SPEC-installed-")
      val stdout = logs.resolve("stdout.json")
      val stderr = logs.resolve("stderr")
      val argv = NativeWorkflowCliFixture.candidateCommand(archive) ++ Vector(org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name +
        "." + service + "." + operation, "--textus.assembly.descriptor=" + root.resolve(".textus/assembly.yaml"),
        "--textus.test.descriptor=" + root.resolve(".textus/test.yaml")) ++ argument ++ Vector("--json")
      val process = new ProcessBuilder(argv.asJava).directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      try {
        process.getOutputStream.close()
        withClue("installed operation timeout: " + stderr) { process.waitFor(300L, TimeUnit.SECONDS) shouldBe true }
        withClue("installed operation error: " + Files.readString(stderr)) { process.exitValue() shouldBe 0 }
        take(new org.goldenport.record.io.RecordDecoder().json(Files.readString(stdout)))
      } finally {
        if (process.isAlive) { process.destroy(); if (!process.waitFor(5L, TimeUnit.SECONDS)) process.destroyForcibly() }
        process.getInputStream.close(); process.getErrorStream.close()
      }
    }
    def installedCall[A: org.goldenport.convert.ValueReader](operation: String, argument: Vector[String]): A =
      take(summon[org.goldenport.convert.ValueReader[A]].readC(installedRecord(operation, argument)))
    def encoded(value: org.goldenport.record.RecordPresentable): String = take(value.toRecord().toJsonStringC)
    def installedRefuses(value: V.DelegateTaskReviewContinuationInput): Unit = {
      val (_, archive) = installed.get
      val logs = Files.createTempDirectory(root, "SPEC-installed-refusal-")
      val argv = NativeWorkflowCliFixture.candidateCommand(archive) ++ Vector(org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name +
        ".DelegateTaskService.resumeDelegateTaskReviewContinuation",
        "--textus.assembly.descriptor=" + root.resolve(".textus/assembly.yaml"),
        "--textus.test.descriptor=" + root.resolve(".textus/test.yaml"), "--input", encoded(value), "--json")
      val process = new ProcessBuilder(argv.asJava).directory(root.toFile)
        .redirectOutput(logs.resolve("stdout").toFile).redirectError(logs.resolve("stderr").toFile).start()
      try {
        process.getOutputStream.close()
        process.waitFor(300L, TimeUnit.SECONDS) shouldBe true
        process.exitValue() should not be 0
      } finally {
        if (process.isAlive) { process.destroy(); if (!process.waitFor(5L, TimeUnit.SECONDS)) process.destroyForcibly() }
        process.getInputStream.close(); process.getErrorStream.close()
      }
    }
    _with_runtime_(root, "TaskReview", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, prepare) =>
      def call(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
      val selected = take(NativeDelegateTaskStartInput.prepareC(Map("input" -> "input", "selection-id" -> "SPEC-selection",
        "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "SPEC-start",
        "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
      prepare { setup => for {
        _ <- setup.setupC(_setup_commands_(root, "TaskReview").size)
        row = selected.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        _ <- setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
      } yield () }
      val started = read_value[V.DelegateTaskView](take(call("startDelegateTask", List(Property("input", input, None),
        Property("selectionId", "SPEC-selection", None), Property("participant", "participant", None), Property("idempotencyKey", "SPEC-start", None)))))
      session = started.sessionId
      call("describeDelegateTaskReviewContinuation", List(Property("input",
        V.DelegateTaskReviewContinuationDescriptionInput(session), None))).isSuccess shouldBe false
      val issued = read_control_value[V.DelegateTaskView](take(call("advanceDelegateTask", List(Property("sessionId", session, None)))))
      call("describeDelegateTaskReviewContinuation", List(Property("input",
        V.DelegateTaskReviewContinuationDescriptionInput(session), None))).isSuccess shouldBe false
      val detail = read_value[V.WorkflowRetainedWorkReadResult](take(owner.executeOperationResponseInContext(Request.of(
        component = component.name, service = WorkflowRetainedWork.serviceName, operation = WorkflowRetainedWork.readOperation,
        properties = List(Property("input", issued.retainedWork.get, None))), actor)))
      val implement = take(V.DelegateTaskView.createC(detail.response))
      Files.writeString(root.resolve("repository/docs/phase.md"), candidateText)
      val ref = V.WorkflowArtifactReference("SPEC-original-implementation", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(ref), Some("SPEC-implementer"), None,
        Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", "SPEC-implementer", "ordinary")))
      val result = V.DelegateTaskWorkResult("completed", "SPEC implementation complete", Vector.empty,
        NonEmptyVector.of(V.DelegateTaskCondition("objective", false, "later delivery"),
          V.DelegateTaskCondition("delivery", false, "later delivery")), Vector.empty, None, None)
      take(call("submitDelegateTaskWorkResult", List(Property("sessionId", session, None),
        Property("issuedWorkContext", implement.issuedWorkContext.get, None), Property("result", result, None),
        Property("resultReference", ref, None), Property("executionEvidence", execution, None))))
      val reviewOrder = read_control_value[V.DelegateTaskView](take(call("advanceDelegateTask", List(Property("sessionId", session, None)))))
      val reviewDetail = read_value[V.WorkflowRetainedWorkReadResult](take(owner.executeOperationResponseInContext(Request.of(
        component = component.name, service = WorkflowRetainedWork.serviceName, operation = WorkflowRetainedWork.readOperation,
        properties = List(Property("input", reviewOrder.retainedWork.get, None))), actor)))
      val review = take(V.DelegateTaskView.createC(reviewDetail.response))
      originalPublic = Some(review)
      prepare { setup => for {
        definition <- DelegateTaskRuntimeCatalog.loadC()
        host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
          "sm-workflow", root.resolve("repository").toString, "main", Vector("docs/phase.md"), Vector.empty,
          None, None, None, None, None)
        context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
        selected = setup.core.copy(executionContext = context)
        store <- DelegateTaskNativeRun.storageC(host, definition, selected)
        issued <- store.bindIssuedC(DelegateTaskNativeRun.completionOperation, DelegateTaskNativeRun.inputCodec)
        interaction <- issued.loadC(ContinuationIdentity(review.issuedWorkContext.get.continuationId))(using context)
          .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC Review issue absent")))
        text <- WorkflowWorkOrderJsonV1.encodeC(interaction, DelegateTaskNativeRun.inputCodec)
        json <- io.circe.parser.parse(text).fold(e => Consequence.stateConflict(e.message), Consequence.success)
        _ = { originalIssued = Some(json) }
      } yield () }
      val reviewRef = V.WorkflowArtifactReference("SPEC-original-review", "1")
      val reviewExecution = V.WorkflowExecutionEvidence(Vector(reviewRef), Some("SPEC-reviewer"), None,
        Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", "SPEC-reviewer", "ordinary")))
      val decision = result.copy(outcome = "decision-needed", summary = "SPEC contract question",
        unresolvedQuestions = Vector("SPEC original contract question"))
      prepare { setup => for {
        admitted <- org.simplemodeling.textus.smworkflow.preparation.NativeReviewPreparation.delegateC(
          review.reviewContext.get, decision, reviewRef, reviewExecution, "normal-development-host",
          setup.executionContext.clock.instant().minusSeconds(1))
        _ <- setup.createC("host_evidence", admitted.recordIdentity, admitted.record)
      } yield () }
      take(call("submitDelegateTaskWorkResult", List(Property("sessionId", session, None),
        Property("issuedWorkContext", review.issuedWorkContext.get, None), Property("result", decision, None),
        Property("resultReference", reviewRef, None), Property("executionEvidence", reviewExecution, None))))
      call("describeDelegateTaskReviewContinuation", List(Property("input",
        V.DelegateTaskReviewContinuationDescriptionInput(session), None))).isSuccess shouldBe false
      val human = read_control_value[V.DelegateTaskView](take(call("advanceDelegateTask", List(Property("sessionId", session, None)))))
      human.kind shouldBe "Human"
      if (installed.isEmpty) {
        val query = V.DelegateTaskReviewContinuationDescriptionInput(session)
        description = Some(read_value[V.DelegateTaskReviewContinuationDescriptionResult](take(call("describeDelegateTaskReviewContinuation", List(Property("input", query, None))))))
      }
    }
    if (installed.nonEmpty) description = Some(installedCall[V.DelegateTaskReviewContinuationDescriptionResult]("describeDelegateTaskReviewContinuation",
      Vector("--input", encoded(V.DelegateTaskReviewContinuationDescriptionInput(session)))))
    val described = description.get
    described.outcome shouldBe "decision-required"
    val compactTarget = take(R.wireC(described.target))._1
    measure("description", encoded(described))
    measure("target", described.target)
    val targetJson = io.circe.parser.parse(described.target).toOption.get
    targetJson.hcursor.downField("business").succeeded shouldBe false
    targetJson.hcursor.downField("frontier").succeeded shouldBe false
    Vector("work", "result", "issued").foreach(name => targetJson.hcursor.downField("original").downField(name).succeeded shouldBe false)
    var retainedTarget: Option[R.Target] = None
    _with_runtime_(root, "TaskReview", hostParameters = parameters, componentInstance = "default") { (_, _, _, prepare) =>
      prepare { setup => for {
        definition <- DelegateTaskRuntimeCatalog.loadC()
        host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
          "sm-workflow", root.resolve("repository").toString, "main", Vector("docs/phase.md"), Vector.empty,
          None, None, None, None, None)
        context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
        selected = setup.core.copy(executionContext = context)
        store <- DelegateTaskNativeRun.storageC(host, definition, selected)
        instances <- store.bindInstancesC()(using context)
        current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC reopened Session absent")))
        payload <- DelegateTaskNativeRun.loadPayloadC(store, session, current.revision.value, selected)
        reference <- payload.state.reviewReference.map(Consequence.success)
          .getOrElse(Consequence.stateConflict("SPEC original Review reference absent"))
        original <- R.originalC(store, current, selected, reference)
        frontier <- WorkflowInstanceRecordJsonV1.encodeC(current).flatMap(s =>
          io.circe.parser.parse(s).fold(e => Consequence.stateConflict(e.message), Consequence.success))
        _ = { retainedTarget = Some(R.Target(compactTarget.componentInstance, session, compactTarget.participant,
          current.revision.value, frontier, payload, original)) }
      } yield () }
    }
    val target = retainedTarget.get
    val retainedBytes = target.asJson.noSpaces.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
    withClue("actual fixture-owned legacy target size") { retainedBytes should be >= 1708731 }
    val actualDiff = target.business.state.candidate.get.diff
    actualDiff.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be >= (if (large) 150000 else 80000)
    val actualReceipt = target.business.state.receipts.head
    actualReceipt.processObservation.nonEmpty shouldBe true
    val receiptBytes = actualReceipt.asJson.noSpaces.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
    withClue("real retained validation receipt") { receiptBytes should be >= (if (large) 750000 else 500000) }
    compactTarget.original.resultReference shouldBe target.original.resultReference
    compactTarget.original.continuationId shouldBe target.original.continuationId
    target.original.result.unresolvedQuestions shouldBe Vector("SPEC original contract question")
    target.original.work.kind shouldBe "Review"
    target.original.issued shouldBe originalIssued.get
    target.original.work.sourceRevision shouldBe target.original.sourceRevision.toString
    target.original.issued.hcursor.downField("request").get[String]("expectedRevision").toOption shouldBe
      Some((target.original.sourceRevision + 1).toString)
    target.original.issued.hcursor.downField("request").downField("context").focus shouldBe
      originalIssued.get.hcursor.downField("request").downField("context").focus
    target.original.resultReference shouldBe ContextReference("SPEC-original-review", "1")
    val stopped = target.business
    R.eligible(stopped) shouldBe true
    val invalidStates = Vector(
      "review" -> stopped.state.copy(stage = "Review"),
      "validation" -> stopped.state.copy(stage = "Validate"),
      "unknown effect" -> stopped.state.copy(stage = "CommitUnknown"),
      "terminal" -> stopped.state.copy(stage = "Completed"),
      "pending check" -> stopped.state.copy(pendingCheck = Some("test")),
      "candidate absent" -> stopped.state.copy(candidate = None),
      "failed receipt" -> stopped.state.copy(receipts = stopped.state.receipts.map(_.copy(termination = "unknown"))),
      "exhausted allowance" -> stopped.state.copy(automaticCount = stopped.state.repairControl.effectiveLimit),
      "review absent" -> stopped.state.copy(reviewReference = None))
    R.eligible(stopped.copy(state = stopped.state.copy(generation = 4, automaticCount = 3, ordinaryCount = 3))) shouldBe true
    invalidStates.foreach { case (label, other) =>
      withClue(label) { R.eligible(stopped.copy(state = other)) shouldBe false }
    }
    val reference = V.WorkflowArtifactReference("SPEC-human-direction", "1")
    val direction = V.DelegateTaskReviewContinuationInput(session, described.target, "ReevaluateReview", "SPEC-owned explicit direction", "participant", reference, Vector(V.WorkflowArtifactReference("SPEC-upstream", "1")))
    measure("resumeDirection", encoded(direction))
    _with_runtime_(root, "TaskReview", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, prepare) =>
      def call(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
      def unchanged(): Unit = {
        val again = read_value[V.DelegateTaskReviewContinuationDescriptionResult](take(call("describeDelegateTaskReviewContinuation",
          List(Property("input", V.DelegateTaskReviewContinuationDescriptionInput(session), None)))))
        again.target shouldBe described.target
      }
      // Archive the actual selected Test observation from this public Session. The
      // specimen changes only this test's in-memory target, not its native history.
      prepare { setup => for {
        context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
        selected = setup.core.copy(executionContext = context)
        receipt <- stopped.state.receipts.headOption.map(Consequence.success)
          .getOrElse(Consequence.stateConflict("SPEC actual selected receipt is absent"))
        process <- receipt.processObservation.map(Consequence.success)
          .getOrElse(Consequence.stateConflict("SPEC actual selected process is absent"))
        facts <- receipt.sbt.map(Consequence.success)
          .getOrElse(Consequence.stateConflict("SPEC actual selected facts are absent"))
        check <- stopped.state.selected.headOption.map(Consequence.success)
          .getOrElse(Consequence.stateConflict("SPEC selected check is absent"))
        candidate <- stopped.state.candidate.map(Consequence.success)
          .getOrElse(Consequence.stateConflict("SPEC selected candidate is absent"))
        association = WorkflowSbtCapture.Association(target.componentInstance, session, target.frontierRevision,
          target.participant, check.identity, check.ordinal, stopped.root, stopped.branch, stopped.baseHead, candidate.tree)
        limit = org.goldenport.cncf.resource.ResourceReadLimits(WorkflowSbtCapture.maximumArtifactBytes)
        ref <- WorkflowSbtCapture.archiveC(selected, "sm-workflow-validation-artifacts", "validation/SPEC-review.json",
          association, facts, process, limit)
        external = receipt.copy(externalCapture = Some(ref))
        externalWork = target.original.work.copy(state =
          target.original.work.state.copy(receipts = Vector(external)))
        externalIssued = target.original.issued.hcursor.downField("request").downField("input")
          .downField("value").withFocus(_ => DelegateTaskNativeRun.inputCodec.encode(externalWork)).top.get
        specimen = target.copy(business = stopped.copy(state = stopped.state.copy(receipts = Vector(external))),
          original = target.original.copy(work = externalWork, issued = externalIssued))
        wire <- R.targetC(specimen.asJson.noSpaces)
        _ = {
          wire.asJson shouldBe specimen.asJson
          wire.business.state.receipts.head.processObservation shouldBe None
          R.eligible(wire.business) shouldBe false
          R.eligible(specimen.business) shouldBe true
        }
        hydrated <- DelegateTaskNativeRun.hydrateStateC(wire.business.state, session, selected, Some(wire.business))
        _ = {
          hydrated.receipts.head.processObservation shouldBe Some(process)
          R.eligible(wire.business.copy(state = hydrated)) shouldBe true
          wire.copy(business = wire.business.copy(state = hydrated)).asJson shouldBe specimen.asJson
          val crossedRef = ref.copy(association = association.copy(session = "SPEC-foreign"))
          val crossed = wire.business.state.copy(receipts = Vector(wire.business.state.receipts.head.copy(externalCapture = Some(crossedRef))))
          DelegateTaskNativeRun.hydrateStateC(crossed, session, selected, Some(wire.business)).isSuccess shouldBe false
          val artifact = root.resolve("capture-root/validation/SPEC-review.json")
          val away = root.resolve("capture-root/validation/SPEC-review.missing")
          Files.move(artifact, away)
          try DelegateTaskNativeRun.hydrateStateC(wire.business.state, session, selected, Some(wire.business)).isSuccess shouldBe false
          finally Files.move(away, artifact)
        }
      } yield () }
      val alteredHistory = target.frontier.mapObject(fields => fields.add("history", Json.arr()))
      val mutated = Vector(
        "unadmitted" -> direction,
        "session" -> direction.copy(sessionId = "SPEC-foreign-session"),
        "participant" -> direction.copy(participant = "foreign"),
        "malformed target" -> direction.copy(target = "{}"),
        "revision" -> direction.copy(target = target.copy(frontierRevision = target.frontierRevision + 1).asJson.noSpaces),
        "owner" -> direction.copy(target = target.copy(componentInstance = "foreign").asJson.noSpaces),
        "root" -> direction.copy(target = target.copy(business = stopped.copy(root = "/foreign")).asJson.noSpaces),
        "branch" -> direction.copy(target = target.copy(business = stopped.copy(branch = "foreign")).asJson.noSpaces),
        "base" -> direction.copy(target = target.copy(business = stopped.copy(baseHead = "b" * 40)).asJson.noSpaces),
        "input" -> direction.copy(target = target.copy(business = stopped.copy(state = stopped.state.copy(
          input = stopped.state.input.copy(objective = "foreign objective")))).asJson.noSpaces),
        "history" -> direction.copy(target = target.copy(frontier = alteredHistory).asJson.noSpaces),
        "original issue" -> direction.copy(target = target.copy(original = target.original.copy(
          continuationId = "foreign-issue")).asJson.noSpaces),
        "completion" -> direction.copy(target = target.copy(original = target.original.copy(
          result = target.original.result.copy(summary = "foreign completion"))).asJson.noSpaces),
        "result reference" -> direction.copy(target = target.copy(original = target.original.copy(
          resultReference = ContextReference("foreign", "1"))).asJson.noSpaces),
        "choice" -> direction.copy(choice = "unrecognized"),
        "empty upstream" -> direction.copy(upstream = Vector.empty),
        "malformed upstream" -> direction.copy(upstream = Vector(V.WorkflowArtifactReference("", "1"))))
      val compactNegative = Vector(
        direction.copy(target = compactTarget.copy(frontierRevision = compactTarget.frontierRevision + 1).asJson.noSpaces),
        direction.copy(target = compactTarget.copy(componentInstance = "foreign").asJson.noSpaces),
        direction.copy(target = compactTarget.copy(sessionId = "foreign").asJson.noSpaces),
        direction.copy(target = compactTarget.copy(original = compactTarget.original.copy(continuationId = "foreign")).asJson.noSpaces))
      compactNegative.foreach { invalid =>
        call("resumeDelegateTaskReviewContinuation", List(Property("input", invalid, None))).isSuccess shouldBe false
        if (installed.nonEmpty) installedRefuses(invalid)
      }
      mutated.foreach { case (label, invalid) =>
        withClue(label) { call("resumeDelegateTaskReviewContinuation", List(Property("input", invalid, None))).isSuccess shouldBe false }
        unchanged()
      }
      val deferReference = V.WorkflowArtifactReference("SPEC-human-defer", "1")
      val deferred = direction.copy(choice = "Defer", reference = deferReference)
      def provision(selectedDirection: V.DelegateTaskReviewContinuationInput): Unit = {
        val selectedReference = selectedDirection.reference
        val execution = V.WorkflowExecutionEvidence(Vector(selectedReference), Some("participant"), None, None)
        def save(name: String, data: org.goldenport.record.RecordPresentable): String = {
          val path = root.resolve("SPEC-" + selectedReference.identity + "-" + name + ".json")
          Files.writeString(path, encoded(data))
          path.toUri.toASCIIString
        }
        val requestPath = save("request", described.copy(target = selectedDirection.target))
        val resultPath = save("result", selectedDirection)
        val referencePath = save("reference", selectedReference)
        val executionPath = save("execution", execution)
        val preparation = V.WorkflowEvidencePreparationInput("DelegateTask", "review-continuation", requestPath, resultPath,
          referencePath, executionPath, "normal-development-host", actor.clock.instant().minusSeconds(1).toString,
          "entity:smworkflow.cli.host_evidence", Some("participant"))
        if (selectedDirection.target == described.target) measure("evidencePreparation", encoded(preparation))
        val row = if (installed.nonEmpty) {
          val record = installedRecord("prepareEvidence", Vector("--input", encoded(preparation),
            "--preparationFormat", "legacy-full"), "NativeWorkflowPreparationService")
          record.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        } else {
          val response = take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "NativeWorkflowPreparationService", operation = "prepareEvidence", properties = List(
              Property("input", preparation, None), Property("preparationFormat", "legacy-full", None))), actor))
          val record = response match {
            case org.goldenport.protocol.operation.OperationResponse.RecordResponse(value) => value
            case org.goldenport.protocol.operation.OperationResponse.Json(value) =>
              take(new org.goldenport.record.io.RecordDecoder().json(value.noSpaces))
            case other => fail("Expected complete public preparation: " + other)
          }
          record.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        }
        if (selectedDirection.target == described.target) measure("evidenceImporter", take(row.toJsonStringC))
        prepare(setup => setup.createC("host_evidence", row.getString("id").get, row.removeKeys(Set("id"))))
      }
      if (installed.isEmpty) {
        val legacyDeferred = deferred.copy(target = target.asJson.noSpaces,
          reference = V.WorkflowArtifactReference("SPEC-human-legacy-defer", "1"))
        provision(legacyDeferred)
        read_value[V.DelegateTaskReviewContinuationDescriptionResult](take(call("resumeDelegateTaskReviewContinuation",
          List(Property("input", legacyDeferred, None))))).outcome shouldBe "deferred"
        unchanged()
      }
      provision(deferred)
      read_value[V.DelegateTaskReviewContinuationDescriptionResult](take(call("resumeDelegateTaskReviewContinuation", List(Property("input", deferred, None))))).outcome shouldBe "deferred"
      val afterDefer = read_value[V.DelegateTaskReviewContinuationDescriptionResult](take(call("describeDelegateTaskReviewContinuation",
        List(Property("input", V.DelegateTaskReviewContinuationDescriptionInput(session), None)))))
      afterDefer.target shouldBe described.target
      provision(direction)
      val issuedContext = target.original.issued.hcursor.downField("request").downField("context").focus.get
      val changedContexts = Vector(
        issuedContext.mapObject(_.add("summary", "SPEC changed context".asJson)),
        issuedContext.mapObject(_.add("requiredFacts", Json.arr(Json.obj(
          "identity" -> "SPEC-changed-fact".asJson, "revision" -> "1".asJson)))))
      val wrongRevision = target.original.issued.mapObject(fields => fields.add("request",
        fields("request").get.mapObject(_.add("expectedRevision", target.original.sourceRevision.toString.asJson))))
      val invalidRevision = direction.copy(target = target.copy(original = target.original.copy(issued = wrongRevision)).asJson.noSpaces)
      call("resumeDelegateTaskReviewContinuation", List(Property("input", invalidRevision, None))).isSuccess shouldBe false
      unchanged()
      changedContexts.foreach { context =>
        val altered = target.original.issued.mapObject(fields => fields.add("request",
          fields("request").get.mapObject(_.add("context", context))))
        val invalid = direction.copy(target = target.copy(original = target.original.copy(issued = altered)).asJson.noSpaces)
        call("resumeDelegateTaskReviewContinuation", List(Property("input", invalid, None))).isSuccess shouldBe false
        unchanged()
      }
      val sourceBeforeResume = root.resolve("repository/docs/phase.md")
      val retainedSource = Files.readString(sourceBeforeResume)
      Files.writeString(sourceBeforeResume, retainedSource + "changed before admission\n")
      try {
        call("resumeDelegateTaskReviewContinuation", List(Property("input", direction, None))).isSuccess shouldBe false
      } finally Files.writeString(sourceBeforeResume, retainedSource)
      unchanged()
      Vector(direction.copy(rationale = "different"),
        direction.copy(upstream = Vector(V.WorkflowArtifactReference("SPEC-other-upstream", "1"))),
        direction.copy(reference = V.WorkflowArtifactReference("SPEC-other-human", "1"))).foreach { invalid =>
        call("resumeDelegateTaskReviewContinuation", List(Property("input", invalid, None))).isSuccess shouldBe false
        unchanged()
      }
      if (installed.isEmpty) {
        val result = read_value[V.DelegateTaskReviewContinuationDescriptionResult](take(call("resumeDelegateTaskReviewContinuation", List(Property("input", direction, None)))))
        result.outcome shouldBe "resumed-awaiting-advance"
      }
    }
    if (installed.nonEmpty) installedCall[V.DelegateTaskReviewContinuationDescriptionResult]("resumeDelegateTaskReviewContinuation", Vector("--input", encoded(direction))).outcome shouldBe "resumed-awaiting-advance"
    var currentcandidate = stopped.state.candidate
    def verifyCheckpoint(suspended: Boolean): Unit =
      _with_runtime_(root, "TaskReview", hostParameters = parameters, componentInstance = "default") { (_, _, _, prepare) =>
        prepare { setup => for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main", Vector("docs/phase.md"), Vector.empty,
            None, None, None, None, None)
          context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
          selected = setup.core.copy(executionContext = context)
          store <- DelegateTaskNativeRun.storageC(host, definition, selected)
          instances <- store.bindInstancesC()(using context)
          current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
            _.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC reopened Session absent")))
          payload <- DelegateTaskNativeRun.loadPayloadC(store, session,
            current.revision.value - (if (suspended) 1L else 0L), selected)
          currentJson <- WorkflowInstanceRecordJsonV1.encodeC(current).flatMap(s =>
            io.circe.parser.parse(s).fold(e => Consequence.stateConflict(e.message), Consequence.success))
          _ = {
            current.suspension.nonEmpty shouldBe suspended
            current.identity.value shouldBe target.sessionId
            val oldHistory = target.frontier.hcursor.get[Vector[Json]]("history").toOption.get
            val newHistory = currentJson.hcursor.get[Vector[Json]]("history").toOption.get
            newHistory.take(oldHistory.size) shouldBe oldHistory
            newHistory.size should be > oldHistory.size
            payload.root shouldBe stopped.root
            payload.branch shouldBe stopped.branch
            payload.baseHead shouldBe stopped.baseHead
            payload.state.input shouldBe stopped.state.input
            payload.state.input.conditions shouldBe Vector("objective", "delivery")
            payload.state.selected shouldBe stopped.state.selected
            payload.state.generation shouldBe stopped.state.generation
            payload.state.automaticCount shouldBe stopped.state.automaticCount
            payload.state.ordinaryCount shouldBe stopped.state.ordinaryCount
            payload.state.repairControl shouldBe stopped.state.repairControl
            payload.state.reviewReference shouldBe Some(target.original.resultReference)
            val accepted = payload.delegateReviewContinuation.get
            accepted.target shouldBe io.circe.parser.parse(described.target).toOption.get
            accepted.choice shouldBe direction.choice
            accepted.reference shouldBe ContextReference(reference.identity, reference.revision)
            accepted.upstream shouldBe Vector(ContextReference("SPEC-upstream", "1"))
          }
        } yield () }
      }
    def verifySuccessor(complete: V.DelegateTaskView,
      readOriginal: V.WorkflowRetainedWorkReference => V.WorkflowRetainedWorkReadResult): Unit = {
      complete.issuedWorkContext.get.requiredOperation shouldBe "delegate-task-review-capability"
      val json = io.circe.parser.parse(take(complete.work.get.toJsonStringC)).toOption.get
      val work = GoalPhaseNativeMetadata.normalizePublicJson(json).toOption.get.as[S.Work].toOption.get
      work.session shouldBe session
      work.kind shouldBe "Review"
      work.state.input shouldBe stopped.state.input
      work.state.selected shouldBe stopped.state.selected
      work.state.generation shouldBe stopped.state.generation
      work.state.automaticCount shouldBe stopped.state.automaticCount
      work.state.ordinaryCount shouldBe stopped.state.ordinaryCount
      work.state.repairControl shouldBe stopped.state.repairControl
      work.state.candidate should not be stopped.state.candidate
      work.state.candidate.get.diff should include ("changed after admission")
      work.state.receipts.map(_.identity) shouldBe stopped.state.receipts.map(_.identity)
      work.state.receipts should not be stopped.state.receipts
      work.state.commit shouldBe None
      work.state.commitExecution shouldBe None
      currentcandidate = work.state.candidate
      val reevaluation = work.reviewReevaluation.getOrElse(fail("Successor omitted original Review evidence"))
      reevaluation.originalResult shouldBe target.original.result
      reevaluation.originalResult.unresolvedQuestions shouldBe Vector("SPEC original contract question")
      reevaluation.clarification.target shouldBe targetJson
      reevaluation.clarification.reference shouldBe ContextReference(reference.identity, reference.revision)
      reevaluation.clarification.choice shouldBe "ReevaluateReview"
      val originalReference = reevaluation.originalWork
      originalReference.readOperation shouldBe WorkflowRetainedWork.serviceName + "." + WorkflowRetainedWork.readOperation
      originalReference.sessionId shouldBe session
      originalReference.identity shouldBe target.original.continuationId
      originalReference.revision shouldBe (target.original.sourceRevision + 1).toString
      originalReference.resource.nonEmpty shouldBe true
      val retained = readOriginal(originalReference)
      val original = take(V.DelegateTaskView.createC(retained.response))
      def canonicalContext(value: V.WorkflowIssuedWorkContext): Json = {
        val json = io.circe.parser.parse(take(value.toRecord().toJsonStringC)).toOption.get
        GoalPhaseNativeMetadata.normalizePublicJson(json).toOption.get
      }
      val retainedContext = canonicalContext(original.issuedWorkContext.get)
      val expectedContext = canonicalContext(originalPublic.get.issuedWorkContext.get)
      val contextFields = retainedContext.asObject.get.keys.toSet ++ expectedContext.asObject.get.keys.toSet
      contextFields.foreach { field =>
        withClue(s"original issued context field $field differs") {
          retainedContext.hcursor.downField(field).focus shouldBe expectedContext.hcursor.downField(field).focus
        }
      }
      val originalWorkJson = io.circe.parser.parse(take(original.work.get.toJsonStringC)).toOption.get
      val originalWork = GoalPhaseNativeMetadata.normalizePublicJson(originalWorkJson).toOption.get.as[S.Work].toOption.get
      originalWork.asJson shouldBe target.original.work.asJson
      val issuedContextJson = originalIssued.get.hcursor.downField("request").downField("context").focus.get
      issuedContextJson shouldBe target.original.issued.hcursor.downField("request").downField("context").focus.get
      val publicContext = GoalPhaseNativeMetadata.normalizePublicJson(
        io.circe.parser.parse(take(original.issuedWorkContext.get.context.toJsonStringC)).toOption.get).toOption.get
      Vector("summary", "requiredFacts", "references").foreach { field =>
        publicContext.hcursor.downField(field).focus shouldBe issuedContextJson.hcursor.downField(field).focus
      }
      val publicSnapshot = publicContext.hcursor.downField("snapshot").focus.get
      val issuedSnapshot = issuedContextJson.hcursor.downField("snapshot").focus.get
      Vector("workflowRevision", "modelRevision", "workspaceRevision", "evidenceRevision").foreach { field =>
        publicSnapshot.hcursor.get[Option[String]](field).toOption.get shouldBe
          issuedSnapshot.hcursor.get[Option[String]](field).toOption.get
      }
    }
    verifyCheckpoint(false)
    val executionsbefore = Files.readAllLines(root.resolve("sbt-executions.txt")).size()
    val currentsource = candidateText + "changed after admission\n"
    Files.writeString(root.resolve("repository/docs/phase.md"), currentsource)
    _with_runtime_(root, "TaskReview", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, prepare) =>
      def call(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
      val repeat = read_value[V.DelegateTaskReviewContinuationDescriptionResult](take(call("resumeDelegateTaskReviewContinuation", List(Property("input", direction, None)))))
      repeat.outcome shouldBe "already-resumed"
      call("resumeDelegateTaskReviewContinuation", List(Property("input", direction.copy(rationale = "different"), None))).isSuccess shouldBe false
      if (installed.isEmpty) {
        val issued = read_control_value[V.DelegateTaskView](take(call("advanceDelegateTask", List(Property("sessionId", session, None)))))
        issued.kind shouldBe "Request"
        emittedWork = issued.retainedWork
        read_value[V.DelegateTaskReviewContinuationDescriptionResult](take(call("resumeDelegateTaskReviewContinuation",
          List(Property("input", direction, None))))).outcome shouldBe "already-resumed"
        val duplicate = read_control_value[V.DelegateTaskView](take(call("advanceDelegateTask", List(Property("sessionId", session, None)))))
        duplicate.retainedWork shouldBe issued.retainedWork
        val reference = issued.retainedWork.get
        val response = take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = WorkflowRetainedWork.serviceName, operation = WorkflowRetainedWork.readOperation,
          properties = List(Property("input", reference, None))), actor))
        val detail = read_value[V.WorkflowRetainedWorkReadResult](response)
        val complete = take(V.DelegateTaskView.createC(detail.response))
        successor = Some(complete)
        verifySuccessor(complete, originalRef => read_value[V.WorkflowRetainedWorkReadResult](take(
          owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = WorkflowRetainedWork.serviceName, operation = WorkflowRetainedWork.readOperation,
            properties = List(Property("input", originalRef, None))), actor))))
      }
    }
    if (installed.nonEmpty) {
      val issued = installedCall[V.DelegateTaskView]("advanceDelegateTask", Vector("--sessionId", session))
      issued.kind shouldBe "Request"
      emittedWork = issued.retainedWork
      val reference = issued.retainedWork.get
      val detail = take(summon[org.goldenport.convert.ValueReader[V.WorkflowRetainedWorkReadResult]].readC(
        installedRecord(WorkflowRetainedWork.readOperation, Vector("--input", encoded(reference)), WorkflowRetainedWork.serviceName)))
      val complete = take(V.DelegateTaskView.createC(detail.response))
      successor = Some(complete)
      verifySuccessor(complete, originalRef => take(summon[org.goldenport.convert.ValueReader[V.WorkflowRetainedWorkReadResult]].readC(
        installedRecord(WorkflowRetainedWork.readOperation, Vector("--input", encoded(originalRef)), WorkflowRetainedWork.serviceName))))
      installedCall[V.DelegateTaskReviewContinuationDescriptionResult]("resumeDelegateTaskReviewContinuation",
        Vector("--input", encoded(direction))).outcome shouldBe "already-resumed"
      val duplicate = installedCall[V.DelegateTaskView]("advanceDelegateTask", Vector("--sessionId", session))
      duplicate.retainedWork shouldBe issued.retainedWork
    }
    val executionsafter = Files.readAllLines(root.resolve("sbt-executions.txt")).size()
    executionsafter should be > executionsbefore
    verifyCheckpoint(true)
    _with_runtime_(root, "TaskReview", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, _) =>
      def call(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
      read_value[V.DelegateTaskReviewContinuationDescriptionResult](take(call("resumeDelegateTaskReviewContinuation",
        List(Property("input", direction, None))))).outcome shouldBe "already-resumed"
      call("resumeDelegateTaskReviewContinuation", List(Property("input", direction.copy(rationale = "changed"), None))).isSuccess shouldBe false
      val reopened = read_control_value[V.DelegateTaskView](take(call("advanceDelegateTask", List(Property("sessionId", session, None)))))
      reopened.retainedWork shouldBe emittedWork
    }
    verifyCheckpoint(true)
    val successorView = successor.get
    val successorReference = V.WorkflowArtifactReference("SPEC-successor-review", "1")
    val successorExecution = V.WorkflowExecutionEvidence(Vector(successorReference), Some("SPEC-successor-reviewer"), None,
      Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", "SPEC-successor-reviewer", "ordinary")))
    val successorResult = V.DelegateTaskWorkResult("accept", "SPEC independent successor review accepted clarified original question",
      Vector.empty, NonEmptyVector.of(V.DelegateTaskCondition("objective", false, "later delivery"),
        V.DelegateTaskCondition("delivery", false, "later delivery")), Vector.empty, None, None)
    def saveSuccessor(name: String, value: org.goldenport.record.RecordPresentable): String = {
      val path = root.resolve("SPEC-successor-" + name + ".json")
      Files.writeString(path, encoded(value))
      path.toUri.toASCIIString
    }
    val requestPath = saveSuccessor("request", successorView)
    val resultPath = saveSuccessor("result", successorResult)
    val referencePath = saveSuccessor("reference", successorReference)
    val executionPath = saveSuccessor("execution", successorExecution)
    _with_runtime_(root, "TaskReview", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, prepare) =>
      val preparation = V.WorkflowEvidencePreparationInput("DelegateTask", "review", requestPath, resultPath,
        referencePath, executionPath, "normal-development-host", actor.clock.instant().minusSeconds(1).toString,
        "entity:smworkflow.cli.host_evidence", None)
      val record = if (installed.nonEmpty) installedRecord("prepareEvidence", Vector("--input", encoded(preparation),
        "--preparationFormat", "legacy-full"), "NativeWorkflowPreparationService")
      else {
        val response = take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "NativeWorkflowPreparationService", operation = "prepareEvidence", properties = List(
            Property("input", preparation, None), Property("preparationFormat", "legacy-full", None))), actor))
        response match {
          case org.goldenport.protocol.operation.OperationResponse.RecordResponse(value) => value
          case org.goldenport.protocol.operation.OperationResponse.Json(value) =>
            take(new org.goldenport.record.io.RecordDecoder().json(value.noSpaces))
          case other => fail("Expected complete public preparation: " + other)
        }
      }
      val row = record.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
      prepare(setup => setup.createC("host_evidence", row.getString("id").get, row.removeKeys(Set("id"))))
      val submitted = if (installed.nonEmpty) installedCall[V.DelegateTaskView]("submitDelegateTaskWorkResult",
        Vector("--sessionId", session, "--issuedWorkContext", encoded(successorView.issuedWorkContext.get),
          "--result", encoded(successorResult), "--resultReference", encoded(successorReference),
          "--executionEvidence", encoded(successorExecution)))
      else read_value[V.DelegateTaskView](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
        service = "DelegateTaskService", operation = "submitDelegateTaskWorkResult", properties = List(
          Property("sessionId", session, None), Property("issuedWorkContext", successorView.issuedWorkContext.get, None),
          Property("result", successorResult, None), Property("resultReference", successorReference, None),
          Property("executionEvidence", successorExecution, None))), actor)))
      submitted.kind shouldBe "Submitted"
    }
    _with_runtime_(root, "TaskReview", hostParameters = parameters, componentInstance = "default") { (_, _, _, prepare) =>
      prepare { setup => for {
        definition <- DelegateTaskRuntimeCatalog.loadC()
        host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
          "sm-workflow", root.resolve("repository").toString, "main", Vector("docs/phase.md"), Vector.empty,
          None, None, None, None, None)
        context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
        selected = setup.core.copy(executionContext = context)
        store <- DelegateTaskNativeRun.storageC(host, definition, selected)
        completions <- store.bindCompletionC(DelegateTaskNativeRun.completionOperation, DelegateTaskNativeRun.inputCodec,
          DelegateTaskNativeRun.resultCodec, DelegateTaskNativeRun.closingType, (_, _) => Consequence.success(Vector.empty))
        retained <- completions.loadRetainedC(ContinuationIdentity(successorView.issuedWorkContext.get.continuationId))(using context)
          .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC successor completion absent")))
        _ = {
          retained.completion.result.resultReference shouldBe ContextReference(successorReference.identity, successorReference.revision)
          retained.completion.result.result.value shouldBe S.Result("accept", successorResult.summary, Vector.empty,
            Vector(S.Condition("objective", false, "later delivery"), S.Condition("delivery", false, "later delivery")),
            Vector.empty)
        }
      } yield () }
    }
    _with_runtime_(root, "TaskReview", hostParameters = parameters, componentInstance = "default") { (_, _, _, prepare) =>
      prepare { setup => for {
        definition <- DelegateTaskRuntimeCatalog.loadC()
        host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
          "sm-workflow", root.resolve("repository").toString, "main", Vector("docs/phase.md"), Vector.empty,
          None, None, None, None, None)
        context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
        selected = setup.core.copy(executionContext = context)
        store <- DelegateTaskNativeRun.storageC(host, definition, selected)
        instances <- store.bindInstancesC()(using context)
        current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC completed Session absent")))
        checkpoint <- current.causation.value.stripPrefix("standalone-completed:").takeWhile(_ != ':').toLongOption
          .map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC completion source checkpoint absent"))
        payload <- DelegateTaskNativeRun.loadPayloadC(store, session, checkpoint, selected)
        currentJson <- WorkflowInstanceRecordJsonV1.encodeC(current).flatMap(s =>
          io.circe.parser.parse(s).fold(e => Consequence.stateConflict(e.message), Consequence.success))
        _ = {
          val oldHistory = target.frontier.hcursor.get[Vector[Json]]("history").toOption.get
          currentJson.hcursor.get[Vector[Json]]("history").toOption.get.take(oldHistory.size) shouldBe oldHistory
          payload.root shouldBe stopped.root
          payload.branch shouldBe stopped.branch
          payload.baseHead shouldBe stopped.baseHead
          payload.state.input shouldBe stopped.state.input
          payload.state.candidate shouldBe currentcandidate
          payload.state.generation shouldBe stopped.state.generation
          payload.state.automaticCount shouldBe stopped.state.automaticCount
          payload.state.ordinaryCount shouldBe stopped.state.ordinaryCount
          payload.state.repairControl shouldBe stopped.state.repairControl
          payload.state.reviewReference shouldBe stopped.state.reviewReference
        }
      } yield () }
    }
    _with_runtime_(root, "TaskReview", hostParameters = parameters, componentInstance = "default") { (_, _, _, prepare) =>
      prepare { setup => for {
        definition <- DelegateTaskRuntimeCatalog.loadC()
        host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
          "sm-workflow", root.resolve("repository").toString, "main", Vector("docs/phase.md"), Vector.empty,
          None, None, None, None, None)
        context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
        selected = setup.core.copy(executionContext = context)
        store <- DelegateTaskNativeRun.storageC(host, definition, selected)
        issued <- store.bindIssuedC(DelegateTaskNativeRun.completionOperation, DelegateTaskNativeRun.inputCodec)
        interaction <- issued.loadC(ContinuationIdentity(successorView.issuedWorkContext.get.continuationId))(using context)
          .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC successor issue absent")))
        actualContext <- NativeWorkflowIssuedWorkContext.fromIssuedC(interaction,
          NativeWorkflowIssuedWorkContext.Mode.Review, DelegateTaskNativeRun.inputCodec)
        _ = {
          def normalized(value: V.WorkflowIssuedWorkContext): Json = GoalPhaseNativeMetadata.normalizePublicJson(
            io.circe.parser.parse(encoded(value)).toOption.get).toOption.get
          normalized(actualContext) shouldBe normalized(successorView.issuedWorkContext.get)
        }
      } yield () }
    }
    _with_runtime_(root, "TaskReview", hostParameters = parameters, componentInstance = "default") { (_, _, _, prepare) =>
      prepare { setup => for {
        definition <- DelegateTaskRuntimeCatalog.loadC()
        host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
          "sm-workflow", root.resolve("repository").toString, "main", Vector("docs/phase.md"), Vector.empty,
          None, None, None, None, None)
        context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
        selected = setup.core.copy(executionContext = context)
        store <- DelegateTaskNativeRun.storageC(host, definition, selected)
        instances <- store.bindInstancesC()(using context)
        current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC completed Session absent")))
        original <- R.originalC(store, current, selected, target.original.resultReference)
        _ = { original shouldBe target.original }
      } yield () }
    }
    Files.exists(root.resolve("sbt-executions.txt")) shouldBe true
    Files.readString(root.resolve("repository/docs/phase.md")) shouldBe currentsource
    Files.readAllLines(root.resolve("sbt-executions.txt")).size() shouldBe executionsafter
    val report = Json.obj("specimen" -> "fixture-owned-native-review-continuation".asJson,
      "mode" -> (if (installed.nonEmpty) "installed" else "managed").asJson,
      "growth" -> (if (large) "large" else "small").asJson,
      "legacyTargetBytes" -> retainedBytes.asJson,
      "candidateDiffBytes" -> actualDiff.getBytes(java.nio.charset.StandardCharsets.UTF_8).length.asJson,
      "receiptBytes" -> receiptBytes.asJson,
      "successorReviewSubmitted" -> true.asJson,
      "successorReviewReference" -> successorReference.identity.asJson,
      "controlBytes" -> Json.obj(measurements.toVector.sortBy(_._1).map { case (k, v) => k -> v.asJson }*))
    Files.writeString(root.resolve("review-continuation-measurements.json"), report.spaces2)
    measurements.toMap
  }
  "Managed DelegateTask Review continuation" should {
    "preserve the original Review stop across admission, separate Advance and reopen at both retained sizes" in {
      val small = qualify(None)
      val large = qualify(None, large = true)
      small.keys shouldBe large.keys
      small.foreach { case (key, size) => withClue(key) { math.abs(large(key) - size) should be <= 256 } }
    }
  }
}
