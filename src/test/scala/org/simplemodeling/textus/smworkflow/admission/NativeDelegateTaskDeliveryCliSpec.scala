package org.simplemodeling.textus.smworkflow.admission

import io.circe.syntax.*
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import org.goldenport.cncf.processexecution.{ProcessExecutionCapture, ProcessExecutionResult, ProcessExecutionTermination}
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.RecordPresentable
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take
import StandaloneFixExecution as S
import S.given
import WorkflowSbtValidation as T
import RepositorySyncNativeMergeValidation as R
import scala.jdk.CollectionConverters.*

/** Public source-launcher transport with specification-owned return data.
  * @since Nov. 7, 2026
  * @version Nov. 7, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeDelegateTaskDeliveryCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)

  "Public delegate delivery Resource intake" should {
    "retain a complete large typed producer return under the ordinary finite Resource policy" in {
      Given("an isolated source-launcher host and specification-owned requester selection")
      val scenario = F.fresh("delegate-large-return", bootstrapSelectedInput = false,
        sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), sbtDiagnosticBytes = 1500000)
      val root = scenario.root
      val repo = Path.of(sys.props("user.dir")).toAbsolutePath
      var sequence = 0
      def call(service: String, operation: String, args: Vector[String]): F.Result = {
        val argv = Vector(repo.resolve("bin/launcher").toString, "--classpath-file",
          repo.resolve("target/cncf.d/runtime-classpath.txt").toString, "--main-class",
          "org.goldenport.cncf.CncfMain", "--", "--force-exit", "--component-dev-dir", repo.toString,
          s"--textus.assembly.descriptor=${root.resolve(".textus/assembly.yaml")}", "command",
          s"org.simplemodeling.textus.SmWorkflow.$service.$operation") ++ args ++ Vector("--json")
        val out = root.resolve(s"delivery-$sequence.json")
        val err = root.resolve(s"delivery-$sequence.err")
        sequence += 1
        val process = new ProcessBuilder(argv.asJava).directory(root.toFile)
          .redirectOutput(out.toFile).redirectError(err.toFile).start()
        process.getOutputStream.close()
        val invocation = F.Invocation(argv, process.pid(), out, err)
        try {
          withClue(invocation.logs) { process.waitFor(240, TimeUnit.SECONDS) shouldBe true }
          F.Result(process.exitValue(), invocation)
        } finally if (process.isAlive) process.destroyForcibly()
      }
      val base = DelegateTaskRequesterRoutingFixture.input("large-resource-fixture")
      val request = base.copy(reviewRequired = false, scalaLint = "off",
        delegation = base.delegation.copy(publicationTarget = None, artifactCoordinates = Vector.empty,
          permittedOperations = cats.data.NonEmptyVector.fromVectorUnsafe(base.delegation.permittedOperations.toVector.filterNot(_ == "publishLocal"))))
      val file = root.resolve("request.json")
      Files.writeString(file, json(request))
      val prepare = V.WorkflowDelegateTaskStartPreparationInput(file.toUri.toASCIIString,
        "selection", "participant", "ExplicitHuman", "key", "entity:smworkflow.cli.goal_selection")
      val prepared = call("NativeWorkflowPreparationService", "prepareDelegateTaskStart", Vector("--input", json(prepare)))
      withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
      val preparedStdout = prepared.invocation.stdout
      require(preparedStdout.isAbsolute && preparedStdout.normalize == preparedStdout &&
        preparedStdout.startsWith(root) && Files.isRegularFile(preparedStdout),
        "Prepared stdout must be the actual host-owned file")
      val config = root.resolve(".textus/config.json")
      val current = io.circe.parser.parse(Files.readString(config))
        .fold(error => throw new AssertionError(error.message), identity)
      require(current.isObject, "Host configuration must be an object")
      Files.writeString(config, current.mapObject(_.add("textus.import.data.file",
        io.circe.Json.fromString(preparedStdout.toString))).noSpaces)
      val started = call("DelegateTaskService", "startDelegateTask", Vector("--input", json(request),
        "--selectionId", "selection", "--participant", "participant", "--idempotencyKey", "key"))
        .business[V.DelegateTaskView]
      call("DelegateTaskService", "advanceDelegateTask", Vector("--sessionId", started.sessionId)).business[V.DelegateTaskView]
      val destination = "SPECIFICATION-OWNED-destination-session"
      def observation(event: String, result: Option[org.goldenport.record.Record]) = V.DelegateTaskObservationInput(
        started.sessionId, event, V.WorkflowArtifactReference("SPECIFICATION-OWNED-" + event, "1"), Some(destination),
        result, None, "SPECIFICATION-OWNED transport observation")
      call("DelegateTaskDeliveryService", "observeDelegateTaskDelivery", Vector("--input", json(observation("dispatch-observed", None))))
        .business[V.DelegateTaskView]
      val requester = take(DelegateTask.inputC(request))
      val producerInput = requester.copy(delegation = requester.delegation.map(_.copy(role = "destination")),
        ownedPaths = requester.ownedPaths :+ "src/extra.scala", sourceReferences = requester.sourceReferences :+ ContextReference("specification-correction", "1"))
      val checks = Vector(GoalPhaseNativeValidation.Check(requester.checks(0), 1, T.Kind.Compile),
        GoalPhaseNativeValidation.Check(requester.checks(1), 2, T.Kind.TestOnly))
      val bytes = Vector.fill(1600000)('x'.toByte)
      val out = ProcessExecutionCapture(bytes, bytes.size.toLong, false)
      val empty = ProcessExecutionCapture(Vector.empty, 0L, false)
      val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(0), out, empty, Vector.empty, 1L, "specification-check")
      val receipts = checks.map { check =>
        val facts = T.Facts(check.kind, T.Outcome.Succeeded,
          Option.when(check.kind == T.Kind.TestOnly)(T.Selection(Vector("example.FixtureSpec"), 1, 1L)), None)
        R.Receipt(check.identity, "exited", Some(0), "", "", Some(facts), Some(process))
      }
      val head = "a" * 40
      val tree = "b" * 40
      val producer = take(S.startC(producerInput, "participant", checks)).copy(stage = "ReturnReady",
        candidate = Some(S.Candidate(head, tree, Vector.empty, "")), receipts = receipts,
        conditionReport = producerInput.conditions.map(id => S.Condition(id, true, "SPECIFICATION-OWNED acceptance")),
        commit = Some(S.Commit(head, "c" * 40, tree)), delivery = Some(DelegateTask.Delivery(destinationSessionId = Some(destination))))
      val input = observation("result-received", Some(GoalPhaseNativeMetadata.record(producer.asJson)))
      val resource = root.resolve("large-observation.json")
      val text = json(input)
      Files.writeString(resource, text)
      text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 2000000
      When("the normal public operation reads the full observation DTO through its authorized Resource")
      val result = call("DelegateTaskDeliveryService", "observeDelegateTaskDelivery",
        Vector("--inputResource", resource.toUri.toASCIIString)).business[V.DelegateTaskView]
      val received = DelegateTaskRequesterRoutingFixture.state(result)
      Then("the requester retains the complete captured bytes and corrected inventory without claiming adoption")
      received.stage shouldBe "ResultReceived"
      val retained = take(DelegateTask.returnedC(requester, received.delivery.get.returned.get))
      retained.input shouldBe producerInput
      retained.receipts.map(_.processObservation.get.stdout.content) shouldBe Vector(bytes, bytes)
      retained.receipts.map(_.processObservation.get.stdout.byteCount) shouldBe Vector(bytes.size.toLong, bytes.size.toLong)
    }
  }
}
