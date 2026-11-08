package org.simplemodeling.textus.smworkflow.admission

import scala.concurrent.duration.*
import io.circe.Json
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M}
import org.goldenport.cncf.validation.annotation.ValidationSpecification
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration

/** Selected leaf estimates and once-only overhead, without starting processes.
  * @since   Oct. 30, 2026
  *  version Oct. 30, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
@ValidationSpecification(declaration = classOf[LocalTestDeclaration])
final class ValidationEstimateDeadlineSpec extends AnyWordSpec with Matchers {
  private val E = ValidationEstimateDeadline
  private val overhead = E.Preparation(2.seconds, 3.seconds, 4.seconds, 1.second)
  private def leaf(name: String, declaration: M.Declaration = M.Declaration(),
    overrideDeclaration: Option[M.Declaration] = None): D.Operation =
    D.Operation(D.OperationReference("fixture", "Example", name), D.Provenance("tests", "1", "source-1", "1"),
      D.ScalaTestSelector("fixture.ExampleSpec", name), M.resolveC(Some(declaration), overrideDeclaration).toOption, Vector.empty)

  "Selected validation estimation" should {
    "count each leaf and preparation once under one aggregate deadline" in {
      val operations = Vector("first", "second", "third").map(leaf(_))
      val plan = E.planC(operations, "changed selection behavior", overhead).toOption.get
      plan.leaves.size shouldBe 3
      plan.leaves.forall(_.usedDefault) shouldBe true
      plan.bodyNanos shouldBe 3.seconds.toNanos
      plan.preparationNanos shouldBe 10.seconds.toNanos
      plan.executionTimeoutMillis shouldBe 19500L
      E.executionEstimateC(plan).toOption.get.timeoutMillisC.toOption shouldBe Some(19500L)
    }

    "apply the resolved class default to each leaf and preserve the operation override" in {
      val classDefault = M.Declaration(expectedDuration = Some(2.seconds))
      val operationOverride = M.Declaration(expectedDuration = Some(5.seconds))
      val operations = Vector(leaf("one", classDefault), leaf("two", classDefault),
        leaf("three", classDefault, Some(operationOverride)))
      val plan = E.planC(operations, "duration inheritance", overhead).toOption.get
      plan.leaves.map(_.expectedNanos) shouldBe Vector(2.seconds.toNanos, 2.seconds.toNanos, 5.seconds.toNanos)
      plan.bodyNanos shouldBe 9.seconds.toNanos
      plan.executionTimeoutMillis shouldBe 28500L
    }

    "count separately selected parameter variants and retain their exact inputs" in {
      val fixture = classOf[registeredinputfixture.RegisteredInputFixtureSpec]
      val root = java.nio.file.Path.of(fixture.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
      val scope = D.DiscoveryScope("registered-inputs", "1", Vector(D.ArtifactScope("component:fixture", "tests", "1",
        "source-1", root, "org.simplemodeling.textus.smworkflow.admission.registeredinputfixture")))
      val inventory = D.discoverC(scope, fixture.getClassLoader, Set(fixture.getName)).toOption.get
      val operations = inventory.operations.filter(_.selector.suiteClassName == fixture.getName).sortBy(_.selector.testName)
      operations.size shouldBe 2
      val invocations = operations.map(operation => E.Invocation(operation, Map("count" -> Json.fromInt(
        registeredinputfixture.RegisteredInputFixtureCases.countFor(operation.selector.testName).get))))
      val plan = E.planInvocationsC(invocations, "two actual parameterized invocations", overhead).toOption.get
      plan.bodyNanos shouldBe 2.seconds.toNanos
      plan.leaves.map(_.parameters("count")) shouldBe Vector(Json.fromInt(1), Json.fromInt(2))
      val execution = E.executionEstimateC(plan).toOption.get
      execution.leaves.map(_.registeredInputs.get("count")) shouldBe Vector(Json.fromInt(1), Json.fromInt(2))
      execution.timeoutMillisC.toOption shouldBe Some(plan.executionTimeoutMillis)
      E.planInvocationsC(Vector(invocations.head, invocations.head), "duplicate", overhead).isSuccess shouldBe false
      E.planInvocationsC(Vector(invocations.head, invocations.head.copy(parameters = invocations.last.parameters)),
        "one registration cannot execute two inputs", overhead).isSuccess shouldBe false
    }

    "preserve ordinary and HEAVY classifications in the same focused selection" in {
      val operations = Vector(leaf("ordinary"), leaf("heavy", M.Declaration(
        purposes = Some(Set(M.Purpose.HEAVY)), expectedDuration = Some(12.minutes))))
      val plan = E.planC(operations, "publication boundary changed", overhead).toOption.get
      plan.leaves.map(_.classification) shouldBe Vector(E.Classification.ORDINARY, E.Classification.HEAVY)
      plan.bodyNanos shouldBe (12.minutes + 1.second).toNanos
    }

    "reject empty, missing or diagnosed inventory rather than shorten the plan" in {
      E.planC(Vector.empty, "empty", overhead).isSuccess shouldBe false
      E.planC(Vector(leaf("missing").copy(metadata = None)), "missing", overhead).isSuccess shouldBe false
      val diagnosed = leaf("diagnosed").copy(diagnostics = Vector(D.Diagnostic("loading", "fixture", "unavailable")))
      E.planC(Vector(diagnosed), "incomplete", overhead).isSuccess shouldBe false
    }

    "round a fractional millisecond up and reject overflowing aggregate estimates" in {
      val none = E.Preparation(Duration.Zero, Duration.Zero, Duration.Zero, Duration.Zero)
      val small = E.planC(Vector(leaf("short", M.Declaration(expectedDuration = Some(1.nano)))), "rounding", none).toOption.get
      small.executionTimeoutMillis shouldBe 1L
      E.planC(Vector(leaf("one"), leaf("two")), "overflow", none,
        E.Settings(Long.MaxValue.nanos, BigDecimal("1.5"))).isSuccess shouldBe false
    }

    "read defaults and explicit numeric settings from resolved configuration" in {
      def parameters(entries: (String, ConfigurationValue)*): ResolvedParameters =
        ResolvedParameters.fromResolvedConfiguration(ResolvedConfiguration(Configuration(entries.toMap), ConfigurationTrace.empty))
      E.settingsC(parameters()).toOption shouldBe Some(E.Settings())
      val configured = E.settingsC(parameters(E.defaultLeafKey -> ConfigurationValue.NumberValue(BigDecimal(250)),
        E.multiplierKey -> ConfigurationValue.NumberValue(BigDecimal(2)))).toOption.get
      E.planC(Vector(leaf("one")), "configured", overhead, configured).toOption.get.executionTimeoutMillis shouldBe 20500L
      E.settingsC(parameters(E.defaultLeafKey -> ConfigurationValue.StringValue("250"))).isSuccess shouldBe false
      E.settingsC(parameters(E.multiplierKey -> ConfigurationValue.NumberValue(BigDecimal(0)))).isSuccess shouldBe false
    }
  }
}
