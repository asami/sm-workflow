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

  "Host human interaction verification seam" should {
    import GoalPhaseHumanInteractionVerification.*
    import GoalPhaseHumanDecisionContract.*
    val asOf = java.time.Instant.parse("2026-10-01T00:00:00Z")
    def prepared(context: ExecutionContext) = {
      val core = _human_core(context)
      val expected = Binding(core.component.get.core.instanceId, HumanDecisionContractFixture.question,
        Answer("keep-open"), context.security.principal.id.value)
      (core, expected)
    }
    def receipt(binding: Binding) = Receipt("interaction-1", "fixture-human-issuer", "independent-human",
      purpose, binding, asOf.minusSeconds(86400))
    def verifier(value: Binding => Consequence[Option[Receipt]]) = new Verifier {
      val issuerIdentity = "fixture-human-issuer"
      def verifyForCallC(reference: String, expected: Binding, core: org.goldenport.cncf.action.ActionCall.Core) = value(expected)
    }
    "admit applicable Human evidence after arbitrary elapsed time without an expiry input" in {
      Given("the authenticated owning call and independent evidence for exactly its question, answer and participant")
      val (core, expected) = prepared(_resolve(SubsystemExecutionProfile.Authenticated, Map("access_token" -> "service-token")))
      val evidence = receipt(expected)
      forAll(Table("elapsedSeconds", 0L, 60L, 86400L, 31536000L)) { elapsed =>
        When("the same-scope Human evidence is admitted beyond the former local deadline")
        val admitted = admitForCallC(expected, evidence.reference, asOf.plusSeconds(elapsed), core,
          verifier(_ => Consequence.success(Some(evidence))))
        Then("time passage does not require renewal and the original attribution is preserved")
        HumanDecisionContractFixture.take(admitted).receiptForCallC(core).toOption.get shouldBe evidence
      }
    }
    "refuse authenticated User and Service calls without an independent verifier" in {
      Vector(SubjectKind.User, SubjectKind.Service).foreach { kind =>
        Given("a provider-authenticated User or Service call carrying hostile human-shaped attributes")
        val (core, expected) = prepared(_resolve(SubsystemExecutionProfile.Authenticated,
          Map("access_token" -> "service-token", "explicitHuman" -> "true", "approval" -> "true"), kind))
        When("the exact relay actor is admitted")
        val actor = GoalPhaseParticipantAdmission.admitC(expected.submittingParticipant, core.executionContext)
        Then("provider authentication establishes only actor admission")
        actor shouldBe Consequence.unit
        When("human verification has no independent verifier")
        val absent = admitForCallC(expected, "interaction-1", asOf, core, null)
        Then("actor authentication does not establish a human interaction")
        absent shouldBe a[Consequence.Failure[?]]
        When("an independent verifier observes no interaction")
        val missing = admitForCallC(expected, "interaction-1", asOf, core, verifier(_ => Consequence.success(None)))
        Then("missing evidence also withholds human admission")
        missing shouldBe a[Consequence.Failure[?]]
      }
    }
    "admit only separately verified exact fixture evidence and retain human and relay identities separately" in {
      Given("the authenticated relay call and separately verified exact human-interaction receipt")
      val (core, expected) = prepared(_resolve(SubsystemExecutionProfile.Authenticated, Map("access_token" -> "service-token")))
      val evidence = receipt(expected)
      When("the independent verifier admits the exact interaction binding")
      val admitted = HumanDecisionContractFixture.take(admitForCallC(expected, "interaction-1", asOf, core,
        verifier(_ => Consequence.success(Some(evidence)))))
      When("the admitted receipt is read with its original owning call capsule")
      val retained = HumanDecisionContractFixture.take(admitted.receiptForCallC(core))
      Then("the exact receipt preserves human and relay identities separately")
      retained shouldBe evidence
      evidence.humanParticipant should not be expected.submittingParticipant
      Given("an equal public copy of the original call capsule")
      val equalcopy = core.copy()
      Then("the copied capsule is value-equal to the original")
      equalcopy shouldBe core
      When("the copied capsule attempts to read the admitted receipt")
      val copied = admitted.receiptForCallC(equalcopy)
      Then("value equality cannot transfer the admitted call origin")
      copied shouldBe a[Consequence.Failure[?]]
    }
    "deny copied authentication, foreign component and undeclared answers before invoking the verifier" in {
      Given("an authenticated call, exact question binding and a verifier invocation counter")
      val context = _resolve(SubsystemExecutionProfile.Authenticated, Map("access_token" -> "service-token"))
      val (core, expected) = prepared(context)
      var calls = 0
      val port = verifier(binding => { calls += 1; Consequence.success(Some(receipt(binding))) })
      val copied = core.copy(executionContext = ExecutionContext.withSecurityContext(context, context.security.copy()))
      When("a call with publicly copied authentication requests human admission")
      val authentication = admitForCallC(expected, "interaction-1", asOf, copied, port)
      Then("the copied authentication origin is refused")
      authentication shouldBe a[Consequence.Failure[?]]
      When("the call has no owning component")
      val component = admitForCallC(expected, "interaction-1", asOf, core.copy(component = None), port)
      Then("the incomplete component ownership is refused")
      component shouldBe a[Consequence.Failure[?]]
      When("the answer is not one of the question's visible choices")
      val answer = admitForCallC(expected.copy(answer = Answer("unlisted")), "interaction-1", asOf, core, port)
      Then("the undeclared answer is refused")
      answer shouldBe a[Consequence.Failure[?]]
      Given("a structurally valid question belonging to a foreign component")
      val foreignQuestion = expected.question.copy(intent = expected.question.intent.copy(handle =
        expected.question.intent.handle.copy(componentIdentity = ComponentId("org.example.ForeignWorkflow"))))
      When("the foreign question is structurally validated")
      val structural = foreignQuestion.validateC
      Then("the fixture remains structurally valid")
      structural shouldBe Consequence.unit
      When("that question is submitted under the original component call")
      val foreign = admitForCallC(expected.copy(question = foreignQuestion), "interaction-1", asOf, core, port)
      Then("foreign ownership is refused despite structural validity")
      foreign shouldBe a[Consequence.Failure[?]]
      When("the question schema revision is foreign")
      val schema = admitForCallC(expected.copy(question = expected.question.copy(schemaVersion = "foreign-version")),
        "interaction-1", asOf, core, port)
      Then("the foreign schema is refused before any independent verifier is invoked")
      schema shouldBe a[Consequence.Failure[?]]
      calls shouldBe 0
    }
    "refuse a foreign current issuer purpose actor actual question target answer or interaction reference" in {
      Given("the exact call binding and receipt, with variants changing one required interaction fact")
      val (core, expected) = prepared(_resolve(SubsystemExecutionProfile.Authenticated, Map("access_token" -> "service-token")))
      val original = receipt(expected)
      val alteredQuestion = expected.question.copy(request = expected.question.request.copy(
        continuationId = org.goldenport.cncf.workflow.ContinuationIdentity("foreign-question")))
      Vector(original.copy(issuer = "foreign"), original.copy(reference = "interaction-2"),
        original.copy(humanParticipant = " "), original.copy(purposeIdentity = "command-authorization"),
        original.copy(binding = expected.copy(submittingParticipant = "foreign")),
        original.copy(binding = expected.copy(question = alteredQuestion)),
        original.copy(binding = expected.copy(answer = Answer("request-authorization"))))
        .foreach { value =>
          When("the independent verifier returns that altered receipt")
          val result = admitForCallC(expected, "interaction-1", asOf, core,
            verifier(_ => Consequence.success(Some(value))))
          Then("the mismatched interaction fact is refused")
          result shouldBe a[Consequence.Failure[?]]
        }
    }
    "consume observed visible text and diagnostics without a whole binding or second issuer witness" in {
      Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R5; Example: E14; separately supplied legacy Goal evidence for the actual question and choice")
      val (core, expected) = prepared(_resolve(SubsystemExecutionProfile.Authenticated, Map("access_token" -> "service-token")))
      val savedQuestion = expected.question.copy(choices = expected.question.choices.map(value => value.copy(label = "Original saved " + value.label)),
        request = expected.question.request.copy(context = expected.question.request.context.copy(summary = "Original saved context",
          snapshot = expected.question.request.context.snapshot.copy(workspaceRevision = Some("saved-diagnostic-workspace")))))
      val observed = receipt(expected.copy(question = savedQuestion))
      var issuerReads = 0
      var evidenceReads = 0
      val port = new Verifier {
        def issuerIdentity = {
          issuerReads += 1
          if (issuerReads == 1) observed.issuer else "unused-second-read"
        }
        def verifyForCallC(reference: String, binding: Binding, call: org.goldenport.cncf.action.ActionCall.Core) = {
          evidenceReads += 1
          Consequence.success(Some(observed))
        }
      }
      When("this separate host ingress admits its raw observation once")
      val admitted = HumanDecisionContractFixture.take(admitForCallC(expected, observed.reference, asOf, core, port))
      val original = HumanDecisionContractFixture.take(admitted.receiptForCallC(core))
      Then("saved evidence is consumed with one current issuer read, without proving unchanged text or declaration")
      original shouldBe observed
      issuerReads shouldBe 1
      evidenceReads shouldBe 1
    }
    "withhold admission for future, absent, failed or throwing observations" in {
      Given("the exact interaction receipt and a fixed admission time")
      val (core, expected) = prepared(_resolve(SubsystemExecutionProfile.Authenticated, Map("access_token" -> "service-token")))
      val original = receipt(expected)
      Vector(original.copy(occurredAt = asOf.plusSeconds(1)), original.copy(occurredAt = null), null).foreach { value =>
        When("the independent verifier reports future or incomplete interaction timing")
        val result = admitForCallC(expected, "interaction-1", asOf, core, verifier(_ => Consequence.success(Some(value))))
        Then("the invalid observation withholds human admission")
        result shouldBe a[Consequence.Failure[?]]
      }
      When("the independent verifier returns an observation failure")
      val failed = admitForCallC(expected, "interaction-1", asOf, core, verifier(_ => Consequence.stateConflict("unavailable")))
      Then("the failure withholds admission")
      failed shouldBe a[Consequence.Failure[?]]
      When("the verifier throws instead of observing the interaction")
      val thrown = admitForCallC(expected, "interaction-1", asOf, core, verifier(_ => throw new IllegalStateException("unavailable")))
      Then("the exception is contained without admission")
      thrown shouldBe a[Consequence.Failure[?]]
      When("the verifier returns no result object")
      val absent = admitForCallC(expected, "interaction-1", asOf, core, verifier(_ => null))
      Then("the incomplete observation withholds admission")
      absent shouldBe a[Consequence.Failure[?]]
    }
    "refuse an observation from an issuer no longer selected by the current host" in {
      Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R5; Example: E15; the selected host issuer is withdrawn before admission of the observed evidence")
      val (core, expected) = prepared(_resolve(SubsystemExecutionProfile.Authenticated, Map("access_token" -> "service-token")))
      val port = new Verifier {
        var issuer = "fixture-human-issuer"
        def issuerIdentity = issuer
        def verifyForCallC(reference: String, binding: Binding, call: org.goldenport.cncf.action.ActionCall.Core) = {
          issuer = "foreign"
          Consequence.success(Some(receipt(binding)))
        }
      }
      When("the ingress reads current issuer authority after observing evidence from the former issuer")
      val result = admitForCallC(expected, "interaction-1", asOf, core, port)
      Then("current issuer permission rejects the old evidence, not an unchanged-declaration proof")
      result shouldBe a[Consequence.Failure[?]]
    }
  }

  private def _human_core(context: ExecutionContext): org.goldenport.cncf.action.ActionCall.Core = {
    val owner = Subsystem(name = "human-contract-fixture",
      configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty))
    val id = org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId
    val component = new Component() {
      override val core: Component.Core = Component.Core.create(id.name, id, ComponentInstanceId.default(id), Protocol.empty)
      override def subsystem = Some(owner)
    }
    val action = new org.goldenport.cncf.action.CommandAction {
      val request = org.goldenport.protocol.Request.of(component = id.name, service = "HumanContractFixture", operation = "verify")
      def createCall(core: org.goldenport.cncf.action.ActionCall.Core) = throw new IllegalStateException("No fixture command execution")
    }
    org.goldenport.cncf.action.ActionCall.Core(action, context, Some(component), None)
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
