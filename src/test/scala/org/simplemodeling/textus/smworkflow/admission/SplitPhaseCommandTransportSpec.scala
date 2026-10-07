package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.protocol.spec as spec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.*

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseCommandTransportSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"transport binding failed: $other")
  }

  private final class BindingProbe {
    var calls = 0
    private def forbidden[A]: Consequence[A] = {
      calls += 1
      Consequence.stateConflict("transport must not call an execution port")
    }
    private val selection = new ProfileStartAdmission.SelectionPort {
      def loadC(identity: String) = forbidden[Option[ProfileStartAdmission.Selection]]
    }
    private val entry = new SplitPhaseEntryAssessment.Port {
      def loadSourceC(phase: String, authority: String) = forbidden[Option[SplitPhaseEntryAssessment.SourceSnapshot]]
      def loadProposalC(identity: String) = forbidden[Option[SplitPhaseEntryAssessment.ProposalCandidate]]
    }
    private val proposals = new SplitPhaseProposalAssessment.Port {
      def loadInventoryC(identity: String) = forbidden[Option[SplitPhaseProposalPrecheck.SourceInventory]]
      def loadDraftC(identity: String) = forbidden[Option[SplitPhaseProposalPrecheck.ProposalDraft]]
      def loadValidationEvidenceC(identity: String) = forbidden[Option[SplitPhaseProposalValidation.Evidence]]
      def loadSourceBoundaryC(identity: String) = forbidden[Option[SplitPhaseSourceBoundary.Contract]]
    }
    private val previews = new SplitPhasePreviewExecution.CatalogPort {
      def loadC(command: SplitPhaseStartCommand) = forbidden[SplitPhasePreviewExecution.Catalog]
    }
    private val instances = new WorkflowInstancePersistence {
      def create(configuration: Configuration, record: InstanceRecord) = forbidden[InstanceRecord]
      def load(configuration: Configuration, identity: InstanceIdentity) = forbidden[Option[InstanceRecord]]
      def append(configuration: Configuration, identity: InstanceIdentity,
        revision: InstanceRevision, entry: HistoryEntry) = forbidden[InstanceRecord]
    }
    private val definition = take(SplitPhaseRuntimeCatalog.loadC()).definition
    private val configuration = Configuration(
      NamedWorkflowStore("transport-probe", WorkflowStoreIdentity("transport-probe-store")),
      MigrationPolicy("probe-v1"), RetentionPolicy("probe-v1"), LeasePolicy("probe-v1"),
      DeliveryConfiguration.SameStore(WorkflowStoreIdentity("transport-probe-store")))

    val operations: Vector[spec.OperationDefinition] = Vector(
      take(SplitPhaseStartServiceOperation.bindC(selection, entry, definition, configuration, instances)),
      take(SplitPhaseProposalAssessmentServiceOperation.bindC(selection, entry, proposals, definition, configuration, instances)),
      take(SplitPhasePreviewServiceOperation.bindC(selection, entry, proposals, previews, definition, configuration, instances)),
      take(SplitPhaseApplyServiceOperation.bindC(Path.of("."), selection, entry, proposals, previews,
        definition, configuration, instances)))
  }

  private val start = SplitPhaseStartCommand(SplitPhaseStartInput("2", "PREVIEW", "source-authority",
    None, None, None), "selection", "participant", "start-key")
  private val commands = Map(
    "startSplitPhase" -> SplitPhaseStartServiceOperation.encodeCommand(start),
    "assessSplitProposal" -> SplitPhaseProposalAssessmentServiceOperation.encodeCommand(
      SplitPhaseProposalAssessmentCommand(start, "1", "assessment-key")),
    "previewSplitPhase" -> SplitPhasePreviewServiceOperation.encodeCommand(
      SplitPhasePreviewCommand(start, "2", "preview-key")),
    "applySplitPhase" -> SplitPhaseApplyServiceOperation.encodeCommand(
      SplitPhaseApplyCommand(start.copy(input = start.input.copy(invocationMode = "APPLY")), "2", "apply-key")))

  private def cli(name: String, wire: String): Request = Request.of(component = "sm-workflow",
    service = "SplitPhaseService", operation = name, arguments = List(Argument("arg1", wire)),
    switches = Nil, properties = Nil)
  private def mcp(name: String, wire: String): Request = Request.of(component = "sm-workflow",
    service = "SplitPhaseService", operation = name, arguments = Nil, switches = Nil,
    properties = List(Property("arg1", wire, None), Property("textus.format", "json", None)))

  "SplitPhase canonical command carriers" should {
    "reach every existing typed decoder through both CLI and MCP without executing host ports" in {
      Given("all four bound Operations with official runtime definitions and counting refusal-only ports")
      val probe = new BindingProbe
      probe.operations.map(_.specification.name).toSet shouldBe commands.keySet
      When("the same versioned command is submitted as a CLI argument or MCP property")
      val results = probe.operations.flatMap { operation =>
        val name = operation.specification.name
        Vector(operation.createOperationRequest(cli(name, commands(name))),
          operation.createOperationRequest(mcp(name, commands(name))))
      }
      Then("all commands are decoded, but no selection, persistence, catalog or document effect runs")
      results.size shouldBe 8
      results.foreach(_ shouldBe a[Consequence.Success[?]])
      probe.calls shouldBe 0
    }

    "reject malformed JSON through either carrier before any domain or persistence work" in {
      Given("all four bound Operations and an incompatible empty JSON command")
      val probe = new BindingProbe
      When("each typed decoder receives that JSON through CLI and MCP")
      val results = probe.operations.flatMap { operation =>
        val name = operation.specification.name
        Vector(operation.createOperationRequest(cli(name, "{}")),
          operation.createOperationRequest(mcp(name, "{}")))
      }
      Then("both transports reject all four commands without calling an execution port")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      probe.calls shouldBe 0
    }

    "reject ambiguous duplicate extra non-string and foreign-service carriers without choosing a winner" in {
      Given("the four Operations and valid commands surrounded by incompatible carrier metadata")
      val probe = new BindingProbe
      When("each Operation checks incompatible transport requests")
      val results = probe.operations.flatMap { operation =>
        val name = operation.specification.name
        val wire = commands(name)
        val argument = cli(name, wire)
        val property = mcp(name, wire)
        val requests = Vector(
          argument.copy(properties = property.properties),
          property.copy(properties = property.properties :+ Property("arg1", wire, None)),
          argument.copy(arguments = argument.arguments :+ Argument("arg1", wire)),
          property.copy(properties = property.properties :+ Property("origin", "ExplicitHuman", None)),
          property.copy(properties = List(Property("arg1", 1, None))),
          property.copy(properties = List(Property("arg1", wire, None), Property("textus.format", "text", None))),
          argument.copy(service = Some("GoalPhaseService")),
          property.copy(operation = "startGoalPhase"))
        requests.map(operation.createOperationRequest)
      }
      Then("none yields an executable request or consults authority or storage")
      results.size shouldBe 32
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      probe.calls shouldBe 0
    }
  }
}
