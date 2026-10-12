package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, PrincipalId, ScopeContext, ScopeKind, SubjectKind}
import org.goldenport.cncf.security.{AuthenticationProvider, AuthenticationRequest, AuthenticationResult, IngressSecurityResolver}
import org.goldenport.cncf.subsystem.*
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseParticipantAdmissionSpec
    extends AnyWordSpec with Matchers with GivenWhenThen with TableDrivenPropertyChecks {
  "GoalPhase participant admission" should {
    "admit only the exact provider-authenticated principal rather than a role or alias" in {
      Given("an authenticated Service provider and hostile participant-shaped request attributes")
      val context = _resolve(SubsystemExecutionProfile.Authenticated,
        Map("access_token" -> "service-token", "principal.id" -> "foreign", "participantIdentity" -> "foreign"))
      When("the registration participant is compared with the provider-resolved principal")
      val result = GoalPhaseParticipantAdmission.admitC("provider-participant", context)
      Then("only the exact principal is admitted, without alias or whitespace normalization")
      result shouldBe Consequence.unit
      forAll(Table("claimed", "foreign", "Provider-participant", " provider-participant", "", "reviewer")) { claimed =>
        GoalPhaseParticipantAdmission.admitC(claimed, context) shouldBe a[Consequence.Failure[?]]
      }
    }

    "admit a host-configured fixed local principal without claiming provider authentication" in {
      Given("Fixed ingress with a configured local user and no authentication material")
      val context = _resolve(SubsystemExecutionProfile.Fixed, Map.empty)
      When("the exact local participant is submitted for actor admission")
      val result = GoalPhaseParticipantAdmission.admitC("local-participant", context)
      Then("the independently admitted local principal is accepted and a foreign participant is denied")
      result shouldBe Consequence.unit
      GoalPhaseParticipantAdmission.admitC("provider-participant", context) shouldBe a[Consequence.Failure[?]]
    }

    "deny controlled-test identity and public authentication-shaped attributes" in {
      Given("controlled-test ingress that claims the same ID and authenticated attributes")
      val context = _resolve(SubsystemExecutionProfile.ControlledTest,
        Map("principal.id" -> "provider-participant", "privilege" -> "user",
          "authenticated" -> "true", "cncf.security.authentication.provenance" -> "provider"))
      When("the claimed participant requests registration actor admission")
      val result = GoalPhaseParticipantAdmission.admitC("provider-participant", context)
      Then("request-level facts do not establish admitted actor origin")
      result shouldBe a[Consequence.Failure[?]]
    }

    "deny an equal security copy and incomplete calls" in {
      Given("provider-admitted ingress and an equal public copy of its security value")
      val context = _resolve(SubsystemExecutionProfile.Authenticated, Map("access_token" -> "service-token"))
      val copycontext = ExecutionContext.withSecurityContext(context, context.security.copy())
      When("actor admission is attempted with the copied or absent call context")
      val result = GoalPhaseParticipantAdmission.admitC("provider-participant", copycontext)
      Then("equal values and missing context cannot transfer the admitted origin")
      copycontext.security shouldBe context.security
      result shouldBe a[Consequence.Failure[?]]
      GoalPhaseParticipantAdmission.admitC("provider-participant", null) shouldBe a[Consequence.Failure[?]]
      GoalPhaseParticipantAdmission.admitC(null, context) shouldBe a[Consequence.Failure[?]]
    }
  }


  private def _resolve(profile: SubsystemExecutionProfile, attributes: Map[String, String],
    kind: SubjectKind = SubjectKind.Service): ExecutionContext = {
    val provider = new AuthenticationProvider {
      val name = "participant-provider"
      def authenticate(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] =
        if (request.accessToken.contains("service-token"))
          Consequence.success(Some(AuthenticationResult(PrincipalId("provider-participant"), subjectKind = kind)))
        else Consequence.success(None)
    }
    val subsystem = Subsystem(name = "participant-spec", scopeContext = Some(
      ScopeContext(ScopeKind.Subsystem, "participant-spec", None, ExecutionContext.create().observability)),
      configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty))
    val owner = subsystem
    val identity = ComponentId("org.simplemodeling.textus.smworkflow.ParticipantSpec")
    val component = new Component() {
      override val core: Component.Core = Component.Core.create(identity.name, identity,
        ComponentInstanceId.default(identity), Protocol.empty)
      override def subsystem: Option[Subsystem] = Some(owner)
      override def authenticationProviders: Vector[AuthenticationProvider] = Vector(provider)
    }.withArtifactMetadata(Component.ArtifactMetadata("spec", "ParticipantSpec", "0.0.0", component = Some("participant")))
    val configured = subsystem.withDescriptor(GenericSubsystemDescriptor(
      path = Path.of("<memory>"), subsystemName = "participant-spec",
      componentBindings = Vector(GenericSubsystemComponentBinding("participant")),
      security = Some(GenericSubsystemSecurityBinding(authentication = Some(GenericSubsystemAuthenticationBinding(
        convention = Some("enabled"), fallbackPrivilege = Some("disabled"),
        localSubject = Some(GenericSubsystemLocalSubjectBinding(id = "local-participant", roles = Vector("user"),
          capabilities = Vector("user"), attributes = Map.empty, securityLevel = Some("user"))),
        providers = Vector(GenericSubsystemAuthenticationProviderBinding(provider.name, "participant", enabled = Some(true)))))))))
    configured.add(Vector(component))
    IngressSecurityResolver.resolve(profile, configured.components.head.logic.executionContext(), attributes) match {
      case Consequence.Success(value) => value.executionContext
      case failure => fail(s"Participant ingress fixture rejected: $failure")
    }
  }
}
