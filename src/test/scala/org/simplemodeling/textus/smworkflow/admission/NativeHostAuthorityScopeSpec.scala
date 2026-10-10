package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.time.Instant
import org.goldenport.cncf.datastore.DataStore
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import NativeHostEvidence.*

/** Selected grants do not attest that full commands or Plan bodies stayed equal.
  * @since   Oct.  8, 2026
  * @version Oct.  8, 2026
  */
final class NativeHostAuthorityScopeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val now = Instant.parse("2026-10-08T00:00:00Z")
  private val declared = Declaration("workflow", DataStore.CollectionId.EntityStore(
    EntityCollectionId("test", "host", "evidence")), "independent-host")
  private val owner = "owned-component-instance"
  private def owned(value: Json) = Json.obj("componentInstance" -> Json.fromString(owner), "value" -> value)
  private def record(purpose: Purpose, binding: Json) = Evidence(schema, purpose.identity, "selected-target", "actual-grant", "1",
    declared.issuer, "participant", binding, now.minusSeconds(1).toString)
  private def read(purpose: Purpose, binding: Json) = decodeStorageC(encodeStorageC(record(purpose, binding)).toOption.get).toOption.get
  private val snapshot = Json.obj("head" -> Json.fromString("a" * 40), "tree" -> Json.fromString("b" * 40))
  private val target = Json.obj("root" -> Json.fromString("/owned/repository"), "localBranch" -> Json.fromString("main"),
    "remote" -> Json.fromString("origin"), "remoteBranch" -> Json.fromString("main"), "endpoint" -> Json.fromString("/owned/upstream.git"))
  private val command = Json.obj("schemaVersion" -> Json.fromString(RepositorySyncStartServiceOperation.wireSchema),
    "selectionIdentity" -> Json.fromString("selection"), "participantIdentity" -> Json.fromString("participant"),
    "idempotencyKey" -> Json.fromString("invocation"), "input" -> Json.obj(
      "repositoryIdentity" -> Json.fromString("repository"), "localBranch" -> Json.fromString("main"),
      "trackingRemote" -> Json.fromString("origin"), "trackingBranch" -> Json.fromString("main"),
      "remoteEndpointIdentity" -> Json.fromString("logical-upstream"), "syncMode" -> Json.fromString("FULL_STATE"),
      "repositoryPolicyVersion" -> Json.fromString("old-policy")))
  private def effect(value: Json = command, actualTarget: Json = target, name: String = "Push") = owned(Json.obj(
    "originalCommand" -> value, "target" -> actualTarget, "effect" -> Json.fromString(name)))
  private val plan = Json.obj("identity" -> Json.fromString("plan"), "revision" -> Json.fromString("1"),
    "phase" -> Json.fromString("phase"), "repository" -> Json.fromString("repository"),
    "steps" -> Json.arr(), "phaseChecks" -> Json.arr(), "repairLimit" -> Json.fromInt(3))
  private val phase = Json.obj("instance" -> Json.fromString("instance"), "sourceRevision" -> Json.fromString("7"),
    "plan" -> plan, "base" -> snapshot, "candidate" -> snapshot, "completed" -> Json.arr(Json.obj(
      "step" -> Json.fromString("step"), "review" -> Json.fromString("old rationale"), "commit" -> Json.obj(
        "identity" -> Json.fromString("c" * 40), "parent" -> Json.fromString("a" * 40), "tree" -> Json.fromString("b" * 40)))),
    "review" -> Json.fromString("actual-phase-review"), "childInvocation" -> Json.fromString("child-invocation"))
  private val step = Json.obj("instance" -> Json.fromString("instance"), "sourceRevision" -> Json.fromString("5"),
    "plan" -> Json.fromString("plan"), "planRevision" -> Json.fromString("1"), "step" -> Json.fromString("step"),
    "generation" -> Json.fromLong(1), "parent" -> snapshot, "candidate" -> snapshot, "reviewEvidence" -> Json.fromString("actual-review"))

  "Native effect authority scope" should {
    "store actual selected effect scope without a command copy or policy-version certificate" in {
      Given("Spec: docs/spec/native-host-authority-applicability.md; Rule: R1; Example: E1; an independently issued grant with logical and physical endpoint scope")
      val saved = read(Purpose.RepositoryEffect, effect())
      val scope = saved.binding.hcursor.downField("scope").focus.get
      When("the same original selection is presented with absent or unusable obsolete metadata")
      val variants = Vector(command.mapObject(_.remove("schemaVersion")), command.mapObject(_.add("sourceLine", Json.True)),
        command.mapObject(_.add("input", command.hcursor.downField("input").focus.get.mapObject(_.remove("repositoryPolicyVersion")))),
        command.mapObject(_.add("input", command.hcursor.downField("input").focus.get.mapObject(_.add("repositoryPolicyVersion", Json.True)))))
      Then("the grant has no command or policy copy and actual ingress resolves the same target without replay or renewed authority")
      scope.asObject.get.keys.toSet should not contain "originalCommand"
      scope.noSpaces should not include "repositoryPolicyVersion"
      variants.foreach(v => observeC(saved, declared, Purpose.RepositoryEffect, "selected-target", effect(v), now).isSuccess shouldBe true)
      scope.hcursor.get[String]("endpointIdentity").toOption shouldBe Some("logical-upstream")
      scope.hcursor.downField("target").get[String]("endpoint").toOption shouldBe Some("/owned/upstream.git")
    }
    "refuse a foreign participant invocation repository endpoint branch effect owner or withdrawn issuer" in {
      Given("Spec: docs/spec/native-host-authority-applicability.md; Rule: R1; Example: E2; selected real scope rather than a content seal")
      val saved = read(Purpose.RepositoryEffect, effect())
      val inputs = Vector(effect(command.mapObject(_.add("participantIdentity", Json.fromString("foreign")))),
        effect(command.mapObject(_.add("idempotencyKey", Json.fromString("foreign")))),
        effect(command.mapObject(_.add("input", command.hcursor.downField("input").focus.get.mapObject(_.add("repositoryIdentity", Json.fromString("foreign")))))),
        effect(actualTarget = target.mapObject(_.add("endpoint", Json.fromString("/foreign/upstream.git")))),
        effect(actualTarget = target.mapObject(_.add("localBranch", Json.fromString("foreign")))), effect(name = "FinalFetch"),
        effect().mapObject(_.add("componentInstance", Json.fromString("foreign"))))
      When("actual current-call targets or the trusted issuer no longer match the grant")
      val results = inputs.map(observeC(saved, declared, Purpose.RepositoryEffect, "selected-target", _, now))
      Then("no crossed grant is admitted")
      results.foreach(_.isSuccess shouldBe false)
      observeC(saved, declared.copy(issuer = "withdrawn"), Purpose.RepositoryEffect, "selected-target", effect(), now).isSuccess shouldBe false
      observeC(saved.copy(participant = "foreign"), declared, Purpose.RepositoryEffect, "selected-target", effect(), now).isSuccess shouldBe false
    }
    "select actual Phase candidate chain and child invocation without storing or certifying the complete Plan" in {
      Given("Spec: docs/spec/native-host-authority-applicability.md; Rule: R2; Example: E3; native Phase closure scope")
      val saved = read(Purpose.PhaseClosure, owned(phase))
      val selectedPlan = saved.binding.hcursor.downField("scope").downField("plan").focus.get
      val changed = phase.mapObject(_.add("plan", plan.mapObject(_.add("steps", Json.True).add("repairLimit", Json.Null)))
        .add("completed", phase.hcursor.downField("completed").focus.get.mapArray(_.map(_.mapObject(_.add("review", Json.True))))))
      When("unused planning and earlier review diagnostics change but the actual closure target does not")
      observeC(saved, declared, Purpose.PhaseClosure, "selected-target", owned(changed), now).isSuccess shouldBe true
      Then("only actual Plan references and native target facts remain; another candidate chain or child is refused")
      selectedPlan.asObject.get.keys.toSet shouldBe Set("identity", "revision", "phase", "repository")
      Vector(phase.mapObject(_.add("childInvocation", Json.fromString("foreign-child"))),
        phase.mapObject(_.add("candidate", snapshot.mapObject(_.add("tree", Json.fromString("d" * 40))))),
        phase.mapObject(_.add("completed", Json.arr()))).foreach(v =>
        observeC(saved, declared, Purpose.PhaseClosure, "selected-target", owned(v), now).isSuccess shouldBe false)
    }
    "read old authority formats without obsolete body witnesses and reject their reintroduction in current rows" in {
      Given("Spec: docs/spec/native-host-authority-applicability.md; Rule: R3; Example: E4; recognized legacy v1/v2 host authority records")
      val cases = Vector(Purpose.RepositoryEffect -> effect(), Purpose.PhaseClosure -> owned(phase), Purpose.StepClosure -> owned(step))
      When("legacy rows are read and current output is consumed")
      cases.foreach { (purpose, original) =>
        val current = read(purpose, original)
        val wire = encodeStorageC(current).toOption.get
        val json = io.circe.parser.parse(wire.substring(schema.length + 1)).toOption.get
        for (version <- Vector("v1", "v2", "v3", "v4", "v5", "v6")) {
          val oldSchema = "sm-workflow.native-host-evidence." + version
          val old = json.mapObject(_.add("schemaVersion", Json.fromString(oldSchema)).add("binding", original))
          val withExpiry = if (version == "v1") old.mapObject(_.add("validUntil", Json.True)) else old
          decodeStorageC(oldSchema + ":" + withExpiry.noSpaces).toOption.get shouldBe current
        }
        Then("actual scope and event time survive; a current copied command or complete Plan is not a supported record")
        decodeStorageC(schema + ":" + json.mapObject(_.add("binding", original)).noSpaces).isSuccess shouldBe false
        val foreign = current.binding.mapObject(_.add("scope", current.binding.hcursor.downField("scope").focus.get
          .mapObject(_.add("managementHash", Json.True))))
        decodeStorageC(schema + ":" + json.mapObject(_.add("binding", foreign)).noSpaces).isSuccess shouldBe false
        current.occurredAt shouldBe record(purpose, original).occurredAt
      }
    }
    "retain real Step candidate parent generation and review targets without accepting malformed scope" in {
      Given("Spec: docs/spec/native-host-authority-applicability.md; Rule: R2; Example: E5; actual Step closing scope")
      val saved = read(Purpose.StepClosure, owned(step))
      When("the actual target is selected or a real Step field is missing changed or mistyped")
      observeC(saved, declared, Purpose.StepClosure, "selected-target", owned(step), now).isSuccess shouldBe true
      Then("foreign candidate generation review and missing actual native targets cannot become permission")
      Vector(step.mapObject(_.add("generation", Json.fromLong(2))), step.mapObject(_.add("reviewEvidence", Json.fromString("foreign"))),
        step.mapObject(_.remove("candidate")), step.mapObject(_.add("parent", Json.True))).foreach(v =>
        observeC(saved, declared, Purpose.StepClosure, "selected-target", owned(v), now).isSuccess shouldBe false)
    }
  }
}
