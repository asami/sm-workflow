package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncFormalFailureContinuation as N

/** Complete multi-megabyte normal targets use the same installed Operation and
  * evidence purpose; Resource acquisition remains with the executing CNCF call.
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryValidationResourceContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeRepositorySelectionCorrectionFixture {
  "RS-RESOURCE-01 installed complete validation adoption" should {
    "strictly refuse invalid Resource inputs then adopt the faithful large target passively and execute selected tests once" in {
      Given("a tiny product repository and actual failed Purpose under a large normally configured fixed process plan")
      val (scenario, session) = failedSelection("validation-resource-public", large = true)
      val old = current(scenario, session)
      val original = formalReceipts(scenario, session)
      val described = describe(scenario, session)
      val target = selectionTarget(described)
      target.continuationPolicy shouldBe N.selectionPolicy
      val configured = parse(Files.readString(scenario.root.resolve(".textus/config.json"))).toOption.get
      val host = parse(configured.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
      target.processPlan shouldBe host
      target.processPlan.noSpaces.getBytes(UTF_8).length should be > 4653720
      selectionTarget(describe(scenario, session)) shouldBe target
      current(scenario, session) shouldBe old
      val input = direction(scenario, session, described, N.selectionChoices.head, true, true)
      val raw = json(input)
      raw.getBytes(UTF_8).length should be > 4653720
      val uri = save(scenario.root, "complete-validation-adoption.json", raw)
      val repository = Path.of(scenario.host.target.root)
      val head = git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD")
      val tree = git(scenario.root, "-C", repository.toString, "write-tree")
      def resource(reference: String) = scenario.repositoryCall(RepositorySyncValidationContinuation.adoptionOperation,
        Vector("--inputResource", reference))
      def refused(parameters: Vector[String]): Unit = {
        val result = scenario.repositoryCall(RepositorySyncValidationContinuation.adoptionOperation, parameters)
        result.exitCode should not be 0
        current(scenario, session) shouldBe old
        formalReceipts(scenario, session) shouldBe original
        git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe head
        git(scenario.root, "-C", repository.toString, "write-tree") shouldBe tree
      }
      When("native admission receives both neither malformed duplicate null foreign stale missing or overbudget input")
      // Keep the inline argument tiny; these cases must refuse before reading it
      // as a competing complete Resource transport.
      refused(Vector("--input", "{}", "--inputResource", uri))
      refused(Vector.empty)
      refused(Vector("--input", "{"))
      val wire = parse(raw).toOption.get
      val malformed = Vector("{" , "null", "[]",
        raw.dropRight(1) + ",\"sessionId\":\"duplicate-session\"}",
        wire.mapObject(_.add("unexpectedField", Json.True)).noSpaces,
        wire.mapObject(_.add("reference", Json.Null)).noSpaces,
        json(input.copy(sessionId = session + "-foreign")),
        json(input.copy(target = N.publicRecord(target.copy(frontierRevision = "1")))),
        json(input.copy(target = N.publicRecord(target.copy(indexTree = "f" * 40)))))
      malformed.zipWithIndex.foreach { (value, index) =>
        refused(Vector("--inputResource", save(scenario.root, "invalid-validation-" + index + ".json", value)))
      }
      refused(Vector("--inputResource", scenario.root.resolve("missing-adoption.json").toUri.toASCIIString))
      val foreign = Files.createTempFile("foreign-validation-adoption-", ".json")
      Files.writeString(foreign, "{}")
      refused(Vector("--inputResource", foreign.toUri.toASCIIString))
      configure(scenario, Map("cncf.resource.read.max-bytes" -> "1048576"))
      refused(Vector("--inputResource", uri))
      configure(scenario, Map("cncf.resource.read.max-bytes" -> "67108864"))
      When("the complete faithful independently bound Resource is adopted and repeated")
      successful(resource(uri)).business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "adopted"
      val admitted = current(scenario, session)
      successful(resource(uri)).business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "already-adopted"
      Then("one direction retains every opaque plan byte with no checks or source effects")
      current(scenario, session) shouldBe admitted
      admitted.revision shouldBe old.revision.next
      formalReceipts(scenario, session) shouldBe original
      def directions(value: Json): Vector[N.Direction] = value.as[N.Direction].toOption.toVector ++
        value.asArray.toVector.flatten.flatMap(directions) ++ value.asObject.toVector.flatMap(_.values.toVector.flatMap(directions))
      val retained = stored(scenario).flatMap(value => parse(value).toOption.toVector.flatMap(directions)).filter(_.target == target)
      retained should not be empty
      retained.foreach(_.target.processPlan shouldBe host)
      git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe head
      git(scenario.root, "-C", repository.toString, "write-tree") shouldBe tree
      stored(scenario).exists(value => value.contains("originalWorkerEffects") && value.contains("UNKNOWN")) shouldBe true
      When("a separate ordinary Advance executes real unfinished checks and is called again")
      assertFreshSelectedReview(scenario, session, target, old, original)
      Then("the original complete Resource becomes stale after that new attempt")
      resource(uri).exitCode should not be 0
    }
  }
}
