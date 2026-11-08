package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseCanonicalCheckConsumption as Q
import GoalPhaseDependencySourceRevision as R
import GoalPhaseNativeValidation as V
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Pure supplied policy data is not a native source proof or Human authority.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  */
private[admission] object CanonicalCheckConsumptionSpecificationFixture {
  val key = "SPECIFICATION_SOURCE_SELECTOR"
  val previousRoot = "/specification/previous-source"
  val sourceRoot = "/specification/canonical-source"
  val primaryRoot = "/specification/primary"
  val checks = Vector(V.Check("b1", 1), V.Check("b2", 2),
    V.Check("b3", 41, WorkflowSbtValidation.Kind.TestOnly), V.Check("b4", 107, WorkflowSbtValidation.Kind.Compile))
  val phaseCheck = V.Check("phase", 211)
  val stepPolicy = V.Policy("specification-steps", 1, checks)
  val phasePolicy = V.Policy("specification-phase", 1, Vector(phaseCheck))
  val plan = O.Plan("canonical-checks", "2", "canonical-checks-phase", "specification-repository",
    checks.map(check => O.Step(check.identity, "Complete " + check.identity, Vector("src/" + check.identity + ".scala"),
      Vector.empty, Vector(check.identity))), Vector(phaseCheck.identity))
  def route(check: V.Check): R.CheckProgram = {
    val workspace = "sm-workflow-repository-validation-" + check.ordinal
    R.CheckProgram(check.identity, workspace, check.kind.wire, "specification-program-" + check.ordinal)
  }
  val dependency = R.CheckProgram("dependency-compile", "specification-dependency", "compile", "specification-dependency-compile")
  val base = R.Consumption("specification-primary", sourceRoot, Vector("docs/excluded.md"),
    Vector(R.ExpectedMode("src/input.scala", "100644")), Vector(route(checks(1)), dependency))
  val later = base.copy(programs = base.programs :+ route(checks(2)))
  val effective = later.copy(programs = later.programs ++ Vector(route(checks(3)), route(phaseCheck)))
  val publisher = Json.obj("identity" -> "specification-publisher".asJson, "target" -> base.primaryWorkspace.asJson,
    "operation" -> "publish-local".asJson, "environment" -> Json.obj(key -> previousRoot.asJson))
  val policy = {
    val routes = checks.map(route) :+ route(phaseCheck)
    Json.obj("maximumDiffBytes" -> 1048576.asJson, "validation" -> stepPolicy.asJson,
      "processPlan" -> Json.obj("sbtPrograms" -> (routes.map(program => Json.obj("identity" -> program.program.asJson,
        "target" -> program.workspace.asJson, "operation" -> program.kind.asJson, "executable" -> "/specification/sbt".asJson,
        "capability" -> program.program.asJson, "testClasses" -> Vector("specification.FixedSpec").asJson,
        "limits" -> Json.obj("executionTimeoutMillis" -> 60000.asJson),
        "environment" -> Json.obj(key -> (if (program.check == "b2") sourceRoot else previousRoot).asJson,
          "UNCHANGED" -> "literal".asJson))) :+ publisher).asJson,
        "gitPrograms" -> Json.arr(Json.obj("identity" -> "specification-unchanged-git".asJson)),
        "workspaces" -> routes.map(program => Json.obj("identity" -> program.workspace.asJson, "root" -> primaryRoot.asJson)).asJson,
        "workspaceGrants" -> routes.map(program => Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
          "workspaceId" -> program.workspace.asJson, "access" -> "manage".asJson)).asJson,
        "grants" -> Json.arr(Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
          "capabilities" -> routes.map(_.program).asJson))))
  }
  def text(value: Json): String = io.circe.Printer.noSpaces.copy(sortKeys = true).print(value)
  val basis = Q.Basis(plan, "b2", base, previousRoot, text(policy), primaryRoot)
  def programs(value: Json)(change: Json => Json): Json = value.mapObject(fields => fields.add("processPlan",
    fields("processPlan").get.mapObject(plan => plan.add("sbtPrograms", Json.fromValues(
      plan("sbtPrograms").get.asArray.get.map(change))))))
  def corrected(value: Json = policy, routes: R.Consumption = effective): Json = programs(value) { program =>
    if (routes.programs.drop(base.programs.size).exists(route => program.hcursor.get[String]("identity").contains(route.program)))
      program.mapObject(fields => fields.add("environment", fields("environment").get.mapObject(_.add(key, sourceRoot.asJson))))
    else program
  }
  def resolve(selected: Vector[V.Check], declarations: Vector[R.ConsumptionRecord] = Vector(base, effective),
    retained: Option[Option[R.ConsumptionRecord]] = None, sameStep: Boolean = false, currentPlan: O.Plan = plan) =
    Q.resolveC(Some(base), Some(basis), currentPlan, selected, sameStep, retained, declarations, Some(phasePolicy))
}

/** These specifications select no real process and create no native records.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseCanonicalCheckConsumptionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CanonicalCheckConsumptionSpecificationFixture as F
  private def _policy(actual: Json, declarations: Vector[R.ConsumptionRecord] = Vector(F.base, F.effective),
    basis: Q.Basis = F.basis, expected: Json = F.policy) = Q.policyC(basis, F.text(expected), F.text(actual), declarations, Some(F.phasePolicy))

  "Effective canonical check consumption" should {
    "accept one explicit later-Step and Phase extension using captured fixed routes and unbounded positive ordinals" in {
      Given("docs/spec/goal-phase-dependency-source-revision.md: supplied original b2, later b3/b4 and independently declared Phase check")
      val current = F.corrected()
      When("one normal registration retains the exact base and appends only remaining fixed check routes")
      val routing = F.resolve(Vector(F.checks(2), F.checks(3), F.phaseCheck))
      val compatibility = _policy(current)
      Then("the effective entry routes exactly the selected checks without changing the captured base or its codec")
      take(routing) shouldBe Some(F.effective)
      compatibility.isSuccess shouldBe true
      F.effective.programs.take(F.base.programs.size) shouldBe F.base.programs
      F.route(F.phaseCheck).workspace shouldBe "sm-workflow-repository-validation-211"
      val original: R.ConsumptionRecord = F.base
      original.asJson.as[R.ConsumptionRecord] shouldBe Right(original)
      take(Q.basisC(CanonicalDependencySourceSpecificationFixture.direction)).base shouldBe
        CanonicalDependencySourceSpecificationFixture.consumption
    }

    "preserve exact legacy policy and same-Step source-consumption payloads when the base already covers selected checks" in {
      Given("the literal original entry and the unchanged native validation v2 payload encoding")
      val payload = V.Payload("10", "b2", O.Snapshot("a" * 40, "b" * 40), Vector(F.checks(1)), Vector.empty, None,
        sourceConsumption = Some(F.base))
      val wire = V.codec.encode(payload)
      When("the base covers b2 and no effective registration is needed")
      val route = F.resolve(Vector(F.checks(1)), Vector(F.base), sameStep = true)
      Then("the original routing and codec remain literal and an empty registration permits only exact policy equality")
      take(route) shouldBe Some(F.base)
      V.codec.decode(wire) shouldBe Right(payload)
      V.codec.encode(payload) shouldBe wire
      _policy(F.policy, Vector.empty).isSuccess shouldBe true
      _policy(F.corrected(), Vector.empty).isSuccess shouldBe false
      F.resolve(Vector(F.checks(2)), sameStep = true).isSuccess shouldBe false
    }

    "preserve an independently admitted publisher correction while changing only the added check selector values" in {
      Given("an expected policy whose publisher selector has separately been corrected by its existing v4 authority owner")
      val expected = F.programs(F.policy)(program => if (program == F.publisher)
        program.mapObject(_.add("environment", Json.obj(F.key -> F.sourceRoot.asJson))) else program)
      val current = F.corrected(expected)
      When("the later-check relation receives that already selected expected publisher policy")
      val accepted = _policy(current, expected = expected)
      Then("it preserves that expected publisher and cannot grant the same publisher change against the old expectation")
      accepted.isSuccess shouldBe true
      _policy(current).isSuccess shouldBe false
      val actualpublisher = current.hcursor.downField("processPlan").get[Vector[Json]]("sbtPrograms").toOption.get
        .find(_.hcursor.get[String]("identity").contains("specification-publisher")).get
      actualpublisher.hcursor.downField("environment").get[String](F.key) shouldBe Right(F.sourceRoot)
      current.hcursor.get[Int]("maximumDiffBytes") shouldBe F.policy.hcursor.get[Int]("maximumDiffBytes")
    }

    "reject changed source inventory modes exclusions base order unknown earlier checks and crossed fixed routes" in {
      Given("the exact base core, original ordered programs and remaining Plan check identities")
      val invalid = Vector(F.effective.copy(sourceRoot = "/specification/other"),
        F.effective.copy(primaryWorkspace = "other-primary"),
        F.effective.copy(expectedModes = Vector(R.ExpectedMode("src/input.scala", "100755"))),
        F.effective.copy(expectedModes = Vector(R.ExpectedMode("src/other.scala", "100644"))),
        F.effective.copy(nonInputExclusions = Vector.empty),
        F.effective.copy(programs = F.base.programs.reverse ++ F.effective.programs.drop(F.base.programs.size)),
        F.base.copy(programs = F.base.programs :+ F.route(F.checks.head)),
        F.base.copy(programs = F.base.programs :+ F.route(F.checks(2)).copy(check = "undeclared")),
        F.base.copy(programs = F.base.programs :+ F.route(F.checks(2)).copy(kind = "test")),
        F.base.copy(programs = F.base.programs :+ F.route(F.checks(2)).copy(program = "crossed-program")),
        F.base.copy(programs = F.base.programs :+ F.route(F.checks(2)).copy(workspace = "sm-workflow-repository-validation-42")))
      When("normal declarations attempt to replace the base or introduce an unselected route")
      val results = invalid.map(value => F.resolve(Vector(F.checks(2)), Vector(F.base, value)))
      Then("every route selection refuses without source or Plan mutation")
      results.foreach(_.isSuccess shouldBe false)
      F.resolve(Vector(F.checks(2)), currentPlan = F.plan.copy(revision = "3")).isSuccess shouldBe false
      F.resolve(Vector(F.checks(2)), Vector(F.base)).isSuccess shouldBe false
      _policy(F.corrected(), Vector(F.base, F.effective, F.effective)).isSuccess shouldBe false
      _policy(F.corrected(), Vector(F.base, F.later, F.effective)).isSuccess shouldBe false
    }

    "reject policy changes outside the exact added selector values and ambiguous or missing selectors and definitions" in {
      Given("captured fixed commands, tests, limits, grants, workspace roots and one unique source selector")
      val current = F.corrected()
      val route = F.route(F.checks(2))
      val changedprograms = Vector("executable" -> "/different/sbt".asJson,
        "testClasses" -> Vector("other.Test").asJson, "limits" -> Json.obj("executionTimeoutMillis" -> 60001.asJson),
        "environment" -> Json.obj(F.key -> F.sourceRoot.asJson, "UNCHANGED" -> "changed".asJson)).map { (key, value) =>
        F.programs(current)(program => if (program.hcursor.get[String]("identity").contains(route.program))
          program.mapObject(_.add(key, value)) else program)
      }
      val changedplan = Vector("grants", "workspaceGrants", "workspaces", "gitPrograms").map { key =>
        current.mapObject(fields => fields.add("processPlan", fields("processPlan").get.mapObject(_.add(key, Json.arr()))))
      }
      val missing = F.programs(F.policy)(program => if (program.hcursor.get[String]("identity").contains(route.program))
        program.mapObject(_.add("environment", Json.obj("UNCHANGED" -> "literal".asJson))) else program)
      val ambiguous = F.programs(F.policy)(program => program.hcursor.get[Json]("environment").toOption.fold(program)(environment =>
        program.mapObject(_.add("environment", environment.mapObject(_.add("SECOND_SELECTOR",
          (if (program.hcursor.get[String]("identity").contains(F.route(F.checks(1)).program)) F.sourceRoot else F.previousRoot).asJson))))))
      val wrongroot = F.programs(F.policy)(program => if (program.hcursor.get[String]("identity").contains(route.program))
        program.mapObject(_.add("environment", Json.obj(F.key -> "/unrelated/source".asJson))) else program)
      val duplicate = F.policy.mapObject(fields => fields.add("processPlan", fields("processPlan").get.mapObject(plan =>
        plan.add("sbtPrograms", Json.fromValues(plan("sbtPrograms").get.asArray.get :+
          plan("sbtPrograms").get.asArray.get.find(_.hcursor.get[String]("identity").contains(route.program)).get)))))
      When("the live envelope changes or the captured program mapping is missing or ambiguous")
      val policies = changedprograms ++ changedplan :+ current.mapObject(_.add("maximumDiffBytes", 1.asJson))
      Then("only the exact environment value substitution is compatible")
      policies.foreach(value => _policy(value).isSuccess shouldBe false)
      Vector(missing, ambiguous, wrongroot, duplicate).foreach(value =>
        _policy(current, basis = F.basis.copy(capturedPolicy = F.text(value)), expected = value).isSuccess shouldBe false)
      _policy(current, basis = F.basis.copy(previousRoot = F.sourceRoot)).isSuccess shouldBe false
      Q.policyC(F.basis, F.text(F.policy), F.text(current), Vector(F.base, F.effective), None).isSuccess shouldBe false
    }

    "restore an older native effective route literally after a newer current registration and distinguish absent retained records" in {
      Given("supplied old b3 routing and a newer host entry that also covers b4 and Phase; provenance remains the native loader's job")
      val retained = take(Q.retainedBindingC(Vector(Some(F.later), Some(F.later))))
      val sourceless = take(Q.retainedBindingC(Vector(None)))
      When("routing is reconstructed from an actual retained field before current configuration selection")
      val old = F.resolve(Vector(F.checks(2)), retained = retained)
      val crossednone = F.resolve(Vector(F.checks(2)), retained = sourceless)
      val none = Q.resolveC(None, None, F.plan, Vector(F.checks(2)), false, Some(None), Vector.empty, None)
      Then("old fields remain old, no retained record differs from retained None, and contradictions cannot choose a fallback")
      take(old) shouldBe Some(F.later)
      crossednone.isSuccess shouldBe false
      take(none) shouldBe None
      take(Q.retainedBindingC(Vector.empty)) shouldBe None
      sourceless shouldBe Some(None)
      Q.retainedBindingC(Vector(None, Some(F.later))).isSuccess shouldBe false
      Q.retainedBindingC(Vector(Some(F.base), Some(F.later))).isSuccess shouldBe false
      Q.resolveC(None, None, F.plan, Vector(F.checks(2)), false, retained, Vector.empty, None).isSuccess shouldBe false
      F.resolve(Vector(F.checks(3)), retained = retained).isSuccess shouldBe false
    }

    "require the needed current registration and corrected live fixed route before a new launch from retained routing" in {
      Given("the immutable older b3 route and one larger currently admitted effective entry")
      val selected = Vector(F.checks(2))
      val live = F.text(F.corrected())
      When("a pending b3 launch rechecks normal registration and its actual selected program")
      val allowed = Q.launchC(F.basis, F.later, selected, Vector(F.base, F.effective), Some(F.phasePolicy), live)
      val removed = Q.launchC(F.basis, F.later, selected, Vector(F.base), Some(F.phasePolicy), live)
      val reset = Q.launchC(F.basis, F.later, selected, Vector(F.base, F.effective), Some(F.phasePolicy), F.text(F.policy))
      Then("unfinished work may launch only under its still-admitted corrected route, while completed historical reconstruction remains passive")
      allowed.isSuccess shouldBe true
      removed.isSuccess shouldBe false
      reset.isSuccess shouldBe false
      take(F.resolve(selected, declarations = Vector.empty, retained = Some(Some(F.later)))) shouldBe Some(F.later)
      val wrongphase = F.phasePolicy.copy(checks = Vector(F.phaseCheck.copy(ordinal = 212)))
      Q.launchC(F.basis, F.effective, Vector(F.phaseCheck), Vector(F.base, F.effective), Some(wrongphase), live).isSuccess shouldBe false
    }
  }
}
