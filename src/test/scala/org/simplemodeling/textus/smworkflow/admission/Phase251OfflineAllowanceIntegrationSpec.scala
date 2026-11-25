/**
 * @since   Nov. 25, 2026
 * @version Nov. 24, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import io.circe.Json
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.convert.ValueReader
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import SplitPhaseExplicitPartitionFixture.take
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.spi.{ResolvedSpiMember, SpiMemberMetadata}
import org.goldenport.cncf.spi.ai.runner.{AiRunner, AiRunnerSocketSet}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.ai.runtime.Phase251OfflineAllowanceFixture as Fixture
import org.simplemodeling.textus.smworkflow.value as V

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.PackagedRuntimeTestDeclaration])
final class Phase251OfflineAllowanceIntegrationSpec extends AnyWordSpec with Matchers with ThreeWorkflowManagedRepositoryFixture {
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    super.process_plan(root, scenario, goal).mapObject(fields => fields.add("sbtPrograms",
      fields("sbtPrograms").getOrElse(fail("sbtPrograms absent")).mapArray(_.map(_.mapObject(
        _.add("operation", Json.fromString("compile")))))))

  private def compilerResponse(body: Json, mode: String): String = {
    val prompt = body.hcursor.downField("messages").downN(0).get[String]("content").toOption
      .getOrElse(fail("Fixture request has no prompt"))
    val original = prompt.takeWhile(_ != '\n')
    val task = prompt.substring(original.length + 1).takeWhile(_ != '\n')
    val packet = io.circe.parser.parse(task).toOption.getOrElse(fail("Fixture packet absent")).hcursor
    def value(name: String) = packet.get[String](name).toOption.getOrElse(fail("Missing " + name))
    val output = packet.get[String]("selectedSource").toOption match {
      case Some(_) => Json.obj("replacement" -> Json.fromString("42"))
      case None =>
        val source = value("source")
        val (line, row) = source.linesIterator.zipWithIndex.find(_._1.contains("\"bad\""))
          .getOrElse(fail("Fixture source absent"))
        val start = line.indexOf("\"bad\"")
        def position(column: Int) = Json.obj("line" -> Json.fromInt(row), "character" -> Json.fromInt(column))
        Json.obj("candidate" -> Json.fromString(value("candidate")),
          "validationReference" -> Json.fromString(value("validationReference")), "path" -> Json.fromString(value("path")),
          "range" -> Json.obj("start" -> position(start), "end" -> position(start + 5)),
          "expectedSource" -> Json.fromString("\"bad\""), "replacement" -> Json.fromString("42"))
    }
    Json.obj("id" -> Json.fromString("response-repair-" + mode), "model" -> Json.fromString(Fixture.config.model),
      "usage" -> Json.obj("prompt_tokens" -> Json.fromInt(1), "completion_tokens" -> Json.fromInt(1),
        "total_tokens" -> Json.fromInt(2)), "choices" -> Json.arr(Json.obj("finish_reason" -> Json.fromString("stop"),
        "message" -> Json.obj("content" -> Json.fromString(output.noSpaces))))).noSpaces
  }

  "The ordinary review route with the actual offline allowance" should {
    "reject an allocation claimed for a different model" in {
      val allocation = Fixture.allocation(10000000, credit = false, dailyIneligible = false)
      Fixture.acceptsModelClaim(allocation, Fixture.config.model) shouldBe true
      Fixture.acceptsModelClaim(allocation, "other-model") shouldBe false
    }
    "enforce model and promotional-credit boundaries through the review route" in {
      for ((mode, allocation, completed) <- Vector(
        ("wrong-model", Fixture.allocation(10000000, false, false, claimedModel = "other-model"), false),
        ("expired-credit", Fixture.allocation(1, true, false, expiredCredit = true), false),
        ("verified-credit", Fixture.allocation(1, true, false), true))) {
        val db = SqlDataStore.sqlite(Files.createTempDirectory("p251-review-" + mode).resolve("ledger.db").toString)
        val ctx = ExecutionContext.test()
        ctx.dataStoreSpace.useDataStore(db)
        try {
          val post = new Fixture.Transport
          val responses = new java.util.concurrent.ConcurrentLinkedQueue[org.goldenport.cncf.spi.ai.runner.AiRecordResponse]()
          val content = Json.obj("outcome" -> Json.fromString("accept"), "summary" -> Json.fromString("fixture review"),
            "findings" -> Json.arr()).noSpaces
          val body = Json.obj("id" -> Json.fromString("review-credit"), "model" -> Json.fromString(Fixture.config.model),
            "usage" -> Json.obj("prompt_tokens" -> Json.fromInt(1), "completion_tokens" -> Json.fromInt(1),
              "total_tokens" -> Json.fromInt(2)), "choices" -> Json.arr(Json.obj("finish_reason" -> Json.fromString("stop"),
              "message" -> Json.obj("content" -> Json.fromString(content))))).noSpaces
          if (completed) post.replies.add(Right((200, Some("req-review-credit"), body)))
          val socket = new AiRunnerSocketSet {}
          val id = ComponentId("org.example.Phase251PolicyFixture")
          socket.installSpiMembers(Vector(ResolvedSpiMember[AiRunner](Fixture.runner(ctx, allocation, post, responses),
            SpiMemberMetadata("ai-runner", id.name, ComponentInstanceId.default(id),
              capabilities = Set("textus-ai.bounded-task.v1"), isDefault = true))))
          val input = V.GoalPhaseApiReviewInput("session", "work:exact", mode, "file:///package", Some(true))
          val result = GoalPhaseApiReview.reviewWithSocketC(Some(socket), true, input, "continuation", "package")(using ctx)
            .toOption.getOrElse(fail("Expected review result"))
          result.status shouldBe (if (completed) "COMPLETED" else "DECLINED")
          post.calls.get() shouldBe (if (completed) 1 else 0)
          if (completed) {
            result.result.map(_.outcome) shouldBe Some("accept")
            Option(responses.peek()).flatMap(_.metadata.get("ai.usage.reservation_basis")) shouldBe
              Some("verified-promotional-credit-reservation")
          }
        } finally db.closeC()
      }
    }
    "refuse an insufficient daily allowance before the synthetic transport is called" in {
      val db = SqlDataStore.sqlite(Files.createTempDirectory("p251-review-").resolve("ledger.db").toString)
      val ctx = ExecutionContext.test()
      ctx.dataStoreSpace.useDataStore(db)
      try {
        val post = new Fixture.Transport
        val runner = Fixture.runner(ctx, Fixture.allocation(1, credit = false, dailyIneligible = false), post)
        val socket = new AiRunnerSocketSet {}
        val id = ComponentId("org.example.Phase251OfflineFixture")
        socket.installSpiMembers(Vector(ResolvedSpiMember[AiRunner](runner,
          SpiMemberMetadata("ai-runner", id.name, ComponentInstanceId.default(id),
            capabilities = Set("textus-ai.bounded-task.v1"), isDefault = true))))
        val input = V.GoalPhaseApiReviewInput("session", "work:exact", "attempt", "file:///package", Some(true))
        val result = GoalPhaseApiReview.reviewWithSocketC(Some(socket), true, input, "continuation", "package")(using ctx)
          .toOption.getOrElse(fail("Expected review transport result"))
        result.status shouldBe "DECLINED"
        post.calls.get() shouldBe 0
      } finally db.closeC()
    }
  }
  "An admitted ordinary review" should {
    "return its typed result and actual synthetic receipt" in {
      val db = SqlDataStore.sqlite(Files.createTempDirectory("p251-review-success-").resolve("ledger.db").toString)
      val ctx = ExecutionContext.test()
      ctx.dataStoreSpace.useDataStore(db)
      try {
        val post = new Fixture.Transport
        val content = Json.obj("outcome" -> Json.fromString("accept"), "summary" -> Json.fromString("fixture review"),
          "findings" -> Json.arr()).noSpaces
        val body = Json.obj("id" -> Json.fromString("response-review-1"), "model" -> Json.fromString(Fixture.config.model),
          "usage" -> Json.obj("prompt_tokens" -> Json.fromInt(1), "completion_tokens" -> Json.fromInt(1),
            "total_tokens" -> Json.fromInt(2)),
          "choices" -> Json.arr(Json.obj("finish_reason" -> Json.fromString("stop"),
            "message" -> Json.obj("content" -> Json.fromString(content))))).noSpaces
        post.replies.add(Right((200, Some("req-review-1"), body)))
        val runner = Fixture.runner(ctx, Fixture.allocation(10000000, credit = false, dailyIneligible = false), post)
        val socket = new AiRunnerSocketSet {}
        val id = ComponentId("org.example.Phase251SuccessFixture")
        socket.installSpiMembers(Vector(ResolvedSpiMember[AiRunner](runner,
          SpiMemberMetadata("ai-runner", id.name, ComponentInstanceId.default(id),
            capabilities = Set("textus-ai.bounded-task.v1"), isDefault = true))))
        val input = V.GoalPhaseApiReviewInput("session", "work:exact", "success-attempt", "file:///package", Some(true))
        val result = GoalPhaseApiReview.reviewWithSocketC(Some(socket), true, input, "continuation", "package")(using ctx)
          .toOption.getOrElse(fail("Expected review result"))
        result.status shouldBe "COMPLETED"
        result.provider shouldBe Some("openai")
        result.model shouldBe Some(Fixture.config.model)
        result.result.map(_.outcome) shouldBe Some("accept")
        result.metadata.flatMap(_.getString("ai.usage.total_tokens")) shouldBe Some("2")
        post.calls.get() shouldBe 1
      } finally db.closeC()
    }
  }
  "Concurrent offline review requests" should {
    "retain an uncertain reservation across store reopen" in {
      def socket(runner: AiRunner): AiRunnerSocketSet = {
        val result = new AiRunnerSocketSet {}
        val id = ComponentId("org.example.Phase251ConcurrentFixture")
        result.installSpiMembers(Vector(ResolvedSpiMember[AiRunner](runner,
          SpiMemberMetadata("ai-runner", id.name, ComponentInstanceId.default(id),
            capabilities = Set("textus-ai.bounded-task.v1"), isDefault = true))))
        result
      }
      def review(s: AiRunnerSocketSet, ctx: ExecutionContext, attempt: String): String = {
        val input = V.GoalPhaseApiReviewInput("session", "work:exact", attempt, "file:///package", Some(true))
        GoalPhaseApiReview.reviewWithSocketC(Some(s), true, input, "continuation", "package")(using ctx)
          .toOption.getOrElse(fail("Expected review result")).status
      }
      val root = Files.createTempDirectory("p251-concurrent-")
      val probeDb = SqlDataStore.sqlite(root.resolve("probe.db").toString)
      val probeCtx = ExecutionContext.test()
      probeCtx.dataStoreSpace.useDataStore(probeDb)
      val probe = new Fixture.Transport
      try {
        val s = socket(Fixture.runner(probeCtx, Fixture.allocation(10000000, false, false), probe))
        review(s, probeCtx, "attempt-0") shouldBe "UNCERTAIN"
        probe.calls.get() shouldBe 1
      } finally probeDb.closeC()
      val bound = probe.bodies.peek().noSpaces.getBytes(java.nio.charset.StandardCharsets.UTF_8).length.toLong + 132L
      val gross = (bound * 100L + 89L) / 90L
      val allocation = Fixture.allocation(gross, false, false)
      val path = root.resolve("ledger.db")
      val firstDb = SqlDataStore.sqlite(path.toString)
      val ctx = ExecutionContext.test()
      ctx.dataStoreSpace.useDataStore(firstDb)
      val post = new Fixture.Transport
      try {
        val s = socket(Fixture.runner(ctx, allocation, post))
        val gate = new java.util.concurrent.CountDownLatch(1)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
          val tasks = Vector("attempt-1", "attempt-2").map { id =>
            pool.submit(new java.util.concurrent.Callable[String] {
              def call(): String = { gate.await(); review(s, ctx, id) }
            })
          }
          gate.countDown()
          val statuses = tasks.map(_.get(30, java.util.concurrent.TimeUnit.SECONDS))
          statuses.count(_ == "UNCERTAIN") shouldBe 1
          statuses.count(_ == "DECLINED") shouldBe 1
          post.calls.get() shouldBe 1
        } finally pool.shutdownNow()
      } finally firstDb.closeC()
      val reopenedDb = SqlDataStore.sqlite(path.toString)
      val reopened = ExecutionContext.test()
      reopened.dataStoreSpace.useDataStore(reopenedDb)
      try {
        val after = new Fixture.Transport
        val s = socket(Fixture.runner(reopened, allocation, after))
        review(s, reopened, "attempt-3") shouldBe "DECLINED"
        after.calls.get() shouldBe 0
      } finally reopenedDb.closeC()
    }
  }
  "The ordinary bounded compiler repair route" should {
    "apply real allowance decisions before the synthetic transport" in {
      for ((mode, daily, credit, ineligible, verified, expectedCalls) <- Vector(
        ("cross-route", 1222223L, false, false, true, 1),
        ("completed", 10000000L, false, false, true, 1),
        ("daily", 10000000L, false, false, true, 1),
        ("insufficient", 1L, false, false, true, 0),
        ("unknown", 10000000L, false, false, false, 0),
        ("no-credit", 1L, false, false, true, 0),
        ("credit-verified", 1L, true, false, true, 1),
        ("credit-expired", 1L, true, false, true, 0),
        ("wrong-model", 10000000L, false, false, true, 0))) {
      val parent = Files.createDirectories(Path.of("target/sm-workflow/work/p251-offline").toAbsolutePath)
      val root = Files.createTempDirectory(parent, "repair-" + mode + "-").toRealPath()
      val ledgerPath = root.resolve("allowance.db")
      val ledger = SqlDataStore.sqlite(ledgerPath.toString)
      val allocation = Fixture.allocation(daily, credit, ineligible, if (mode == "cross-route") 600000L else 100L,
        expiredCredit = mode == "credit-expired", claimedModel = if (mode == "wrong-model") "other-model" else Fixture.config.model)
      val ctx = ExecutionContext.test()
      ctx.dataStoreSpace.useDataStore(ledger)
      try {
        val input = V.GoalPhaseStartInput("p251-offline-repair", "sm-workflow")
        val path = "src/main/scala/RepairExample.scala"
        val check = GoalPhaseNativeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile)
        val normal = ThreeWorkflowNormalHostFixture.definition(root)
        val host = normal.copy(goal = normal.goal.copy(input = input, candidatePaths = Vector(path),
          stepValidation = normal.goal.stepValidation.copy(checks = Vector(check)),
          phaseValidation = normal.goal.phaseValidation.copy(checks = Vector(check))))
        val plan = GoalPhaseOrderedProgression.Plan("p251-offline", "1", input.phaseIdentity,
          input.repositoryIdentity, Vector(GoalPhaseOrderedProgression.Step("step-1", "Compile fixture",
            Vector(path), Vector.empty, Vector(check.identity))), Vector(check.identity), repairLimit = 4)
        val post = new Fixture.Transport
        if (Set("completed", "credit-verified")(mode)) post.replyForBody = Some(body => compilerResponse(body, mode))
        else post.replies.add(Left("synthetic possible dispatch"))
        val entered = new java.util.concurrent.CountDownLatch(1)
        val release = new java.util.concurrent.CountDownLatch(1)
        if (mode == "cross-route") post.onPost = () => {
          entered.countDown()
          if (!release.await(30, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture release timed out")
        }
        val responses = new java.util.concurrent.ConcurrentLinkedQueue[org.goldenport.cncf.spi.ai.runner.AiRecordResponse]()
        val runner = Fixture.runner(ctx, allocation, post, responses, sizingVerified = verified)
        _with_runtime_(root, "Goal", Some(host)) { (owner, component, actor, prepare) =>
          val socket = component.port.entries.collectFirst { case value: AiRunnerSocketSet => value }.getOrElse {
            val created = new AiRunnerSocketSet {}
            component.withPort(Component.Port.input(created).orElse(component.port))
            created
          }
          val id = ComponentId("org.example.Phase251RepairFixture")
          socket.installSpiMembers(Vector(ResolvedSpiMember[AiRunner](runner,
            SpiMemberMetadata("ai-runner", id.name, ComponentInstanceId.default(id),
              capabilities = Set("textus-ai.bounded-task.v1"), isDefault = true))))
          prepare(call => for {
            _ <- call.setupC(_setup_commands_(root, "Goal").size)
            selection = _goal_selection_(input, "p251-selection", "p251-start")
            _ <- call.createC("goal_selection", NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Goal(input),
              selection.participantIdentity), Record.data(ProfileSelectionJsonV1.storageField ->
              take(ProfileSelectionJsonV1.encodeStorageC(selection))))
            _ <- call.createC("goal_plans", selection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
              take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(selection.selectionId,
                selection.participantIdentity, plan)))))
          } yield ())
          def invoke[A: ValueReader](operation: String, properties: List[Property]): A =
            read_value[A](take(owner.executeOperationResponseInContext(
              Request.of(component = component.name, service = "GoalPhaseService", operation = operation, properties = properties), actor)))
          val started = invoke[V.GoalPhaseStartResult]("startGoalPhase", NativeWorkflowOperationInputFixture.goal(input))
          def advance() = invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(started.sessionId))
          advance().work.map(_.kind) shouldBe Some("ImplementStep")
          val source = root.resolve("repository").resolve(path)
          Files.createDirectories(source.getParent)
          val original = "object RepairExample { val value: Int = \"bad\" }\n"
          Files.writeString(source, original)
          val result = V.GoalPhaseWorkResult("completed", "Fixture failing source", Vector.empty,
            None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
          val reference = V.WorkflowArtifactReference("p251-fixture-implementation", "1")
          invoke[V.GoalPhaseWorkSubmissionResult]("submitGoalPhaseWorkResult", List(Property("sessionId", started.sessionId, None),
            Property("result", result.toRecord(), None), Property("resultReference", reference.toRecord(), None))).accepted shouldBe true
          if (mode == "cross-route") {
            val pool = java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
              val running = pool.submit(new java.util.concurrent.Callable[V.GoalPhaseAdvanceResult] {
                def call(): V.GoalPhaseAdvanceResult = advance()
              })
              try {
                entered.await(120, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
                val reviewInput = V.GoalPhaseApiReviewInput("review-session", "review:exact", "competing-review",
                  "file:///package", Some(true))
                val review = GoalPhaseApiReview.reviewWithSocketC(Some(socket), true, reviewInput, "review-continuation", "package")(using ctx)
                  .toOption.getOrElse(fail("Expected competing review result"))
                review.status shouldBe "DECLINED"
                post.calls.get() shouldBe 1
              } finally release.countDown()
              running.get(30, java.util.concurrent.TimeUnit.SECONDS)
            } finally pool.shutdownNow()
          } else advance()
          val again = advance()
          again.sessionId shouldBe started.sessionId
          post.calls.get() shouldBe expectedCalls
          if (Set("completed", "credit-verified")(mode)) {
            again.work.map(_.kind) shouldBe Some("ReviewStep")
            Files.readString(source).linesIterator.toVector.lastOption shouldBe Some(original.trim.replace("\"bad\"", "42"))
            val expectedBasis = if (credit) "verified-promotional-credit-reservation" else "verified-daily-reservation"
            Option(responses.peek()).flatMap(_.metadata.get("ai.usage.reservation_basis")) shouldBe Some(expectedBasis)
          } else {
            Files.readString(source).linesIterator.toVector.lastOption shouldBe Some(original.trim)
            if (Set("credit-expired", "wrong-model")(mode)) again.work.map(_.kind) shouldBe Some("RepairStep")
          }
          var causes = Vector.empty[String]
          prepare(call => call.goalCurrentC(InstanceIdentity(started.sessionId)).map(current =>
            { causes = current.history.map(_.causation.value); () }))
          causes.count(_ == "ordered-bounded-compiler:intent") shouldBe 1
          causes.count(_ == "ordered-bounded-compiler:result") shouldBe 1
        }
      } finally ledger.closeC()
      if (mode == "cross-route") {
        val reopenedDb = SqlDataStore.sqlite(ledgerPath.toString)
        val reopened = ExecutionContext.test()
        reopened.dataStoreSpace.useDataStore(reopenedDb)
        try {
          val laterPost = new Fixture.Transport
          val laterRunner = Fixture.runner(reopened, allocation, laterPost)
          val socket = new AiRunnerSocketSet {}
          val id = ComponentId("org.example.Phase251ReopenedFixture")
          socket.installSpiMembers(Vector(ResolvedSpiMember[AiRunner](laterRunner,
            SpiMemberMetadata("ai-runner", id.name, ComponentInstanceId.default(id),
              capabilities = Set("textus-ai.bounded-task.v1"), isDefault = true))))
          val input = V.GoalPhaseApiReviewInput("later-session", "later:exact", "later-review", "file:///package", Some(true))
          val review = GoalPhaseApiReview.reviewWithSocketC(Some(socket), true, input, "later-continuation", "package")(using reopened)
            .toOption.getOrElse(fail("Expected review after store reopen"))
          review.status shouldBe "DECLINED"
          laterPost.calls.get() shouldBe 0
        } finally reopenedDb.closeC()
      }
      }
    }
  }

}
