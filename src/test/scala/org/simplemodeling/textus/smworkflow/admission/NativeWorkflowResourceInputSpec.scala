package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.*
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import SplitPhaseExplicitPartitionFixture.take

/** Shared transport and actual Resource acquisition regressions without business authority.
  * @since Oct. 24, 2026
  * @version Oct. 27, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeWorkflowResourceInputSpec extends AnyWordSpec with Matchers with GivenWhenThen with NativePreparationOperationFixture {
  private def _request(values: (String, Any)*): Request = Request.of(
    component = "org.simplemodeling.textus.SmWorkflow", service = "GoalPhaseService",
    operation = "describeGoalPhaseValidationRepair", arguments = values.toList.map((n, v) => Argument(n, v)),
    switches = Nil, properties = Nil)
  private val _input = V.GoalPhaseAdvanceInput("fixture-session")
  private def _candidate_business_cases(valid: Json): Vector[(String, String)] = {
    val fields = valid.asObject.get
    def _key_(canonical: String): String = fields.keys.find(key =>
      RuntimeContext.PropertyNameContext.default.normalizeInputName(key) == canonical)
      .getOrElse(fail("Generated candidate input is missing " + canonical))
    val sessionkey = _key_("sessionId")
    val targetkey = _key_("target")
    val referencekey = _key_("reference")
    val alias = if (sessionkey == "sessionId") "session_id" else "sessionId"
    val altered = Vector(
      "top-level null" -> Json.Null,
      "top-level array" -> Json.arr(),
      "top-level scalar" -> Json.fromString("candidate"),
      "empty object" -> Json.obj(),
      "unknown business key" -> valid.mapObject(_.add("unexpected", Json.True)),
      "missing Session" -> valid.mapObject(_.remove(sessionkey)),
      "Session alias collision" -> valid.mapObject(_.add(alias, Json.fromString("foreign-session"))),
      "unknown reference key" -> valid.mapObject(_.add(referencekey,
        fields(referencekey).get.mapObject(_.add("unexpected", Json.True)))),
      "empty reference" -> valid.mapObject(_.add(referencekey, Json.obj())),
      "nested target null" -> valid.mapObject(_.add(targetkey,
        fields(targetkey).get.mapObject(_.add("fixture", Json.Null))))) ++
      Vector("sessionId", "target", "choice", "rationale", "reference").map { canonical =>
        (canonical + " null") -> valid.mapObject(_.add(_key_(canonical), Json.Null))
      } ++ Vector("identity", "revision").map { key =>
        ("reference " + key + " null") -> valid.mapObject(_.add(referencekey,
          fields(referencekey).get.mapObject(_.add(key, Json.Null))))
      }
    Vector("malformed JSON" -> "{") ++ altered.map((label, value) => label -> value.noSpaces)
  }

  /** Valid public target data only; no native Session or evidence is created. */
  private def _absent_candidate_input(root: Path): V.GoalPhaseCandidateRecoveryInput = {
    import GoalPhaseCandidateRecovery as R
    import GoalPhaseOrderedProgression as O
    val session = "absent-candidate-resource-session"
    val paths = Vector("value.txt")
    val plan = O.Plan("resource-acquisition-plan", "1", "resource-phase", "fixture-repository",
      Vector(O.Step("step-1", "Retain the selected value", paths, Vector.empty, Vector("test"))), Vector("test"))
    val initial = take(O.startC(plan, O.Snapshot("a" * 40, "b" * 40)))
    val state = take(initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key, "Fixture implementation")))
    val inspection = GoalPhaseNativeCandidate.Payload("50", "step-1", paths, Vector.empty, None, Vector.empty, None)
    def _identity_(key: String) = session + ":ordered-candidate:50:" + key
    val target = R.RecoveryTarget(session, R.controllerSnapshot(state), "goal-phase", "1", "participant", "50", "53",
      root.toString, "main", paths, Vector.empty, _identity_("inspection"), "1", "51", inspection,
      WorkflowSourceHistoryRule.Selection("none", None), Vector.empty,
      Vector(R.StagePair(0, _identity_("stage-intent-0"), "1", "52", _identity_("stage-result-0"), "1", "53",
        inspection, inspection.copy(stagedPaths = paths))), "UNKNOWN", "termination-unconfirmed")
    V.GoalPhaseCandidateRecoveryInput(session, R.targetRecord(target), "Defer", "Fixture acquisition control",
      V.WorkflowArtifactReference("fixture-human", "1"))
  }

  "Candidate recovery Resource transport" should {
    "bind candidate recovery without acquiring a Resource and enforce exclusive transport" in {
      Given("the actual candidate recovery Operation and an absent local file without a recovery port")
      val operation = GoalPhaseCandidateRecovery.operations(null).last
      def _request_(properties: Property*) = Request.of(component = "org.simplemodeling.textus.SmWorkflow",
        service = "GoalPhaseService", operation = GoalPhaseCandidateRecovery.recoveryOperation,
        properties = properties.toList)
      val resource = Property("inputResource", "file:///private/tmp/absent-candidate-recovery.json", None)
      When("binding selects that Resource without executing the owning ProcedureActionCall")
      val selected = operation.createOperationRequest(_request_(resource))
      val refused = Vector(_request_(), _request_(Property("input", "{}", None), resource),
        _request_(Property("input", "null", None), resource), _request_(Property("inputResource", "https://example.test/a", None)))
        .map(operation.createOperationRequest)
      Then("binding succeeds without file or port access and missing ambiguous or remote selectors refuse")
      selected.isSuccess shouldBe true
      refused.foreach(_.isSuccess shouldBe false)
    }
    "preserve the complete candidate DTO through either exclusive transport with one Resource read" in {
      Given("the generated candidate DTO with its entire target and one exclusive local Resource")
      val input = V.GoalPhaseCandidateRecoveryInput("fixture-session", Record.data("fixture" -> "retained"),
        "ReviseWork", "Specification-owned semantic revision", V.WorkflowArtifactReference("fixture-human", "1"))
      val text = take(input.toRecord().toJsonStringC)
      val inline = take(NativeWorkflowResourceInput.selectC[Record](_request("input" -> text)))
      val resource = take(NativeWorkflowResourceInput.selectC[Record](
        _request("inputResource" -> "file:///private/tmp/candidate.json")))
      var reads = 0
      When("the owning reader resolves the complete DTO through each transport")
      val a = take(NativeWorkflowResourceInput.readC(inline, _ => fail("Inline must not acquire a Resource"))
        .flatMap(GoalPhaseCandidateRecovery.recoveryInputC))
      val b = take(NativeWorkflowResourceInput.readC(resource, _ => { reads += 1; Consequence.success(text) })
        .flatMap(GoalPhaseCandidateRecovery.recoveryInputC))
      Then("both transports preserve all fields and only Resource acquires text once")
      a shouldBe input
      b shouldBe input
      reads shouldBe 1
    }
    "reject each changed malformed null unknown or aliased candidate business value through both transports" in {
      Given("a generated complete DTO and explicit mutations of its actual serialized object keys")
      val input = V.GoalPhaseCandidateRecoveryInput("fixture-session", Record.data("fixture" -> "retained"),
        "ReviseWork", "Specification-owned semantic revision", V.WorkflowArtifactReference("fixture-human", "1"))
      val text = take(input.toRecord().toJsonStringC)
      val valid = parse(text).fold(error => fail(error.message), identity)
      val invalidbusiness = Table(("case", "businessJson"), _candidate_business_cases(valid)*)
      val resource = take(NativeWorkflowResourceInput.selectC[Record](_request("inputResource" -> "file:///private/tmp/candidate.json")))
      forAll(invalidbusiness) { (label, mutated) =>
        Given(label + " differs from the valid generated wire value")
        mutated should not be text
        parse(mutated).toOption.foreach(_ should not be valid)
        When("shared transport decoding and the owning business-key admission process each exclusive source")
        val inline = NativeWorkflowResourceInput.selectC[Record](_request("input" -> mutated)).flatMap(source =>
          NativeWorkflowResourceInput.readC(source, _ => fail("Inline must not fall back to Resource")))
          .flatMap(GoalPhaseCandidateRecovery.recoveryInputC)
        var reads = 0
        val acquired = NativeWorkflowResourceInput.readC(resource, _ => { reads += 1; Consequence.success(mutated) })
          .flatMap(GoalPhaseCandidateRecovery.recoveryInputC)
        Then("both sources refuse this mutation and Resource has exactly one read without fallback")
        inline.isSuccess shouldBe false
        acquired.isSuccess shouldBe false
        reads shouldBe 1
      }
    }
    "refuse physical Resource and business failures before native recovery while a valid acquisition reaches missing Start" in {
      withPreparation(cap = 16384) { episode =>
        Given("the actual recovery Operation, current Resource actor, finite roots and no native Session")
        val input = _absent_candidate_input(episode.root)
        take(GoalPhaseCandidateRecovery.targetC(input.target)).sessionId shouldBe input.sessionId
        val text = take(input.toRecord().toJsonStringC)
        val valid = parse(text).fold(error => fail(error.message), identity)
        def _invoke_(uri: String) = episode.owner.executeOperationResponseInContext(Request.of(
          component = episode.component.name, service = "GoalPhaseService", operation = GoalPhaseCandidateRecovery.recoveryOperation,
          properties = List(Property("inputResource", uri, None))), episode.actor)
        def _failure_(result: Consequence[org.goldenport.protocol.operation.OperationResponse]): String = result match {
          case Consequence.Failure(conclusion) => conclusion.toString
          case _ => fail("An absent native Session must never receive successful recovery")
        }
        val control = episode.save("candidate-valid-control", text)
        val missing = episode.root.resolve("absent-candidate.json")
        Files.exists(missing) shouldBe false
        val outside = Files.createTempFile(episode.root.getParent, "outside-candidate-", ".json")
        Files.writeString(outside, text, UTF_8)
        val malformed = episode.root.resolve("malformed-utf8.json")
        Files.write(malformed, Array(0xc3.toByte, 0x28.toByte))
        val oversized = episode.saveValue("candidate-over-cap", input.copy(rationale = "x" * 16384))
        Files.size(Path.of(java.net.URI.create(oversized))) should be > 16384L
        val refusals = Table(("case", "resourceUri"), (Vector(
          "missing in-root file" -> missing.toUri.toASCIIString,
          "existing outside-root file" -> outside.toUri.toASCIIString,
          "malformed UTF-8" -> malformed.toUri.toASCIIString,
          "finite byte-cap overflow" -> oversized) ++
          _candidate_business_cases(valid).zipWithIndex.map { case ((label, value), index) =>
            label -> episode.save("candidate-invalid-" + index, value)
          })*)
        try {
          When("the normal owning call acquires a valid complete DTO")
          val acquired = _failure_(_invoke_(control))
          Then("native admission is reached and refuses specifically because its ordered Start is absent")
          acquired should include ("owning advance has no actual native ordered Start")
          acquired should not include ("native ordered entry requires its actual owning call")
          forAll(refusals) { (label, uri) =>
            When("the normal owning call acquires " + label)
            val refused = _failure_(_invoke_(uri))
            Then("it refuses before the native recovery admission reached by the positive control")
            refused should not include ("owning advance has no actual native ordered Start")
            refused should not be acquired
          }
        } finally Files.deleteIfExists(outside)
      }
    }
  }
  "Shared current Resource input" should {
    "permit both actual validation repair owning calls while refusing a Session without native Start" in {
      withPreparation() { e =>
        Given("the normal generated Operations, valid business keys and an absent fixture Session without business authority")
        val session = "absent-validation-repair-session"
        val proposal = Record.data("fixture" -> "nonempty-proposal")
        val target = Record.data("fixture" -> "nonempty-target")
        val description = V.GoalPhaseValidationRepairDescriptionInput(session, proposal, None)
        val adoption = V.GoalPhaseValidationRepairAdoptionInput(session, target, proposal, "Defer",
          "Specification-only absent Session refusal", V.WorkflowArtifactReference("fixture-reference", "1"))
        val inputs = Vector(GoalPhaseValidationRepairAdoption.describeOperation -> description.toRecord(),
          GoalPhaseValidationRepairAdoption.adoptionOperation -> adoption.toRecord())
        When("both complete DTOs enter their generated public handlers through normal inline transport")
        val refused = inputs.map { (operation, input) =>
          operation -> e.owner.executeOperationResponseInContext(Request.of(component = e.component.name,
            service = "GoalPhaseService", operation = operation,
            properties = List(Property("input", take(input.toJsonStringC), None))), e.actor)
        }
        Then("owning call binding permits each Operation and retained Session admission refuses the missing native Start")
        refused.foreach { (operation, result) =>
          withClue(operation) {
            result match {
              case Consequence.Failure(conclusion) =>
                conclusion.toString should include ("owning advance has no actual native ordered Start")
                conclusion.toString should not include ("native ordered entry requires its actual owning call")
              case _ => fail("An absent fixture Session must not receive business admission")
            }
          }
        }
      }
    }
    "enforce XOR exact business keys and alias collisions in both actual new public handlers" in {
      withPreparation() { e =>
        Given("the normal generated service and no supplied Session effect authority")
        val absent = Vector.empty[Property]
        val ambiguous = Vector(Property("input", "{}", None), Property("inputResource", "file:///tmp/absent.json", None))
        val business = Vector("{}", "null", "[]", "{\"sessionId\":\"fixture\",\"proposal\":{},\"extra\":true}",
          "{\"sessionId\":\"fixture\",\"session-id\":\"other\",\"proposal\":{}}", "{\"sessionId\":null,\"proposal\":{}}")
        When("each actual public handler receives an absent ambiguous malformed or extended business input")
        val refused = Vector("describeGoalPhaseValidationRepair", "adoptGoalPhaseValidationRepair").flatMap { operation =>
          (Vector(absent, ambiguous) ++ business.map(text => Vector(Property("input", text, None)))).map { properties =>
            e.owner.executeOperationResponseInContext(Request.of(component = e.component.name, service = "GoalPhaseService",
              operation = operation, properties = properties.toList), e.actor)
          }
        }
        Then("every call refuses before any native adoption or Resource fallback")
        refused.foreach(_.isSuccess shouldBe false)
      }
    }
    "decode the same complete generated business value through either exclusive transport" in {
      Given("one exact generated DTO and an escaped local file reference")
      val text = take(_input.toRecord().toJsonStringC)
      val inline = take(NativeWorkflowResourceInput.selectC[V.GoalPhaseAdvanceInput](_request("input" -> text)))
      val resource = take(NativeWorkflowResourceInput.selectC[V.GoalPhaseAdvanceInput](_request("inputResource" -> "file:///private/tmp/input%20value.json")))
      var reads = 0
      When("the owning execution context resolves the selected source")
      val a = take(NativeWorkflowResourceInput.readC(inline, _ => fail("inline must not acquire a Resource")))
      val b = take(NativeWorkflowResourceInput.readC(resource, _ => { reads += 1; Consequence.success(text) }))
      Then("there is exactly one Resource read and both retain the identical business value")
      a shouldBe _input
      b shouldBe _input
      reads shouldBe 1
    }
    "refuse missing conflicting nonlocal and malformed inputs before business admission" in {
      Given("absent ambiguous or unsafe transport selectors")
      val requests = Vector(_request(), _request("input" -> "{}", "inputResource" -> "file:///private/tmp/a")) ++
        Vector[Any](null, 4, "", "relative.json", "file:relative", "file://host/tmp/a", "https://example.test/a",
          "file:///tmp/a?q=1", "file:///tmp/a#part", " file:///tmp/a").map(x => _request("inputResource" -> x))
      When("the shared selector runs")
      val results = requests.map(NativeWorkflowResourceInput.selectC[V.GoalPhaseAdvanceInput])
      Then("every malformed selector refuses")
      results.foreach(_.isSuccess shouldBe false)
      val resource = take(NativeWorkflowResourceInput.selectC[V.GoalPhaseAdvanceInput](_request("inputResource" -> "file:///tmp/a")))
      Vector("{", "null", "[]", "{}", "{\"sessionId\":null}").foreach { text =>
        NativeWorkflowResourceInput.readC(resource, _ => Consequence.success(text)).isSuccess shouldBe false
      }
      NativeWorkflowResourceInput.readC(resource, _ => Consequence.resourceInvalid("normal denied/oversized/invalid UTF-8 Resource")).isSuccess shouldBe false
    }
  }
}
