package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.time.Instant
import org.goldenport.cncf.datastore.DataStore
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import NativeExternalEvidence.*

/** Selected grants do not attest that full commands or Plan bodies stayed equal.
  * @since   Oct.  8, 2026
  * @version Oct. 24, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeAuthorityScopeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val now = Instant.parse("2026-10-08T00:00:00Z")
  private val declared = Declaration("workflow", DataStore.CollectionId.EntityStore(
    EntityCollectionId("test", "host", "evidence")), "independent-host")
  private val owner = "owned-component-instance"
  private def owned(value: Json) = Json.obj("componentInstance" -> Json.fromString(owner), "scope" -> value)
  private def record(purpose: Purpose, binding: Json) = Evidence(schema, purpose.identity, "selected-target", "actual-grant", "1",
    declared.issuer, "participant", binding, now.minusSeconds(1).toString)
  private def read(purpose: Purpose, binding: Json) = decodeStorageC(encodeStorageC(record(purpose, binding)).toOption.get).toOption.get
  private val snapshot = Json.obj("head" -> Json.fromString("a" * 40), "tree" -> Json.fromString("b" * 40))
  private val target = Json.obj("root" -> Json.fromString("/owned/repository"), "localBranch" -> Json.fromString("main"),
    "remote" -> Json.fromString("origin"), "remoteBranch" -> Json.fromString("main"), "endpoint" -> Json.fromString("/owned/upstream.git"))
  private val effectScope = Json.obj("selection" -> Json.fromString("selection"), "participant" -> Json.fromString("participant"),
    "invocation" -> Json.fromString("invocation"), "repository" -> Json.fromString("repository"),
    "endpointIdentity" -> Json.fromString("logical-upstream"), "mode" -> Json.fromString("FULL_STATE"))
  private def effect(value: Json = effectScope, actualTarget: Json = target, name: String = "Push") =
    owned(value.mapObject(_.add("kind", Json.fromString("repository-effect")).add("target", actualTarget).add("effect", Json.fromString(name))))
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
      val variants = Vector(effectScope.mapObject(_.add("sourceLine", Json.True)),
        effectScope.mapObject(_.add("generator", Json.False).add("repositoryPolicyVersion", Json.True)))
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
      val inputs = Vector(effect(effectScope.mapObject(_.add("participant", Json.fromString("foreign")))),
        effect(effectScope.mapObject(_.add("invocation", Json.fromString("foreign")))),
        effect(effectScope.mapObject(_.add("repository", Json.fromString("foreign")))),
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
    "preserve legacy Phase output and bind the optional original reviewed snapshot as an actual target" in {
      Given("a legacy Phase closure grant and a separate final management candidate with an original reviewed source")
      val legacy = read(Purpose.PhaseClosure, owned(phase))
      val reviewed = snapshot.mapObject(_.add("tree", Json.fromString("e" * 40)))
      val current = phase.mapObject(_.add("reviewedCandidate", reviewed))
      val saved = read(Purpose.PhaseClosure, owned(current))
      When("canonical normalization selects either absent or present reviewed source and consumes the same target")
      val legacyselected = NativeAuthorityScope.select(Purpose.PhaseClosure, owned(phase)).toOption.get
      val selected = saved.binding.hcursor.downField("scope")
      val equivalent = current.mapObject(_.add("reviewedCandidate", reviewed.mapObject(_.add("unused", Json.True))))
      Then("absence retains the historical output and equal head/tree accepts without copying unrelated snapshot fields")
      legacy.binding shouldBe legacyselected
      legacy.binding.hcursor.downField("scope").focus.get.asObject.get.contains("reviewedCandidate") shouldBe false
      legacy.binding.hcursor.downField("scope").focus.get.asObject.get.keys.toSet shouldBe
        Set("kind", "instance", "sourceRevision", "review", "childInvocation", "plan", "base", "candidate", "completed")
      observeC(legacy, declared, Purpose.PhaseClosure, "selected-target", owned(phase), now).isSuccess shouldBe true
      selected.downField("reviewedCandidate").focus shouldBe Some(reviewed)
      observeC(saved, declared, Purpose.PhaseClosure, "selected-target", owned(equivalent), now).isSuccess shouldBe true
    }
    "refuse crossed absent and malformed optional Phase reviewed targets in both comparison directions" in {
      Given("a grant bound to an original reviewed source separately from its final management candidate")
      val current = phase.mapObject(_.add("reviewedCandidate", snapshot))
      val saved = read(Purpose.PhaseClosure, owned(current))
      val legacy = read(Purpose.PhaseClosure, owned(phase))
      val crossed = Vector(phase,
        current.mapObject(_.add("reviewedCandidate", snapshot.mapObject(_.add("head", Json.fromString("f" * 40))))),
        current.mapObject(_.add("reviewedCandidate", snapshot.mapObject(_.add("tree", Json.fromString("f" * 40))))))
      val malformed = Vector(Json.Null, Json.True, Json.fromString("snapshot"), Json.obj(),
        Json.obj("head" -> Json.fromString("a" * 40)), Json.obj("tree" -> Json.fromString("b" * 40)))
        .map(value => current.mapObject(_.add("reviewedCandidate", value)))
      When("actual admission compares another source or tries to select a null scalar or incomplete optional snapshot")
      val compared = crossed.map(value => observeC(saved, declared, Purpose.PhaseClosure, "selected-target", owned(value), now))
      val reversed = observeC(legacy, declared, Purpose.PhaseClosure, "selected-target", owned(current), now)
      val selected = malformed.map(value => NativeAuthorityScope.select(Purpose.PhaseClosure, owned(value)))
      Then("different head/tree, presence changes and malformed present snapshots cannot reuse authority")
      compared.foreach(_.isSuccess shouldBe false)
      reversed.isSuccess shouldBe false
      selected.foreach(_.isLeft shouldBe true)
      malformed.foreach(value => observeC(saved, declared, Purpose.PhaseClosure, "selected-target", owned(value), now).isSuccess shouldBe false)
    }
  }
}
