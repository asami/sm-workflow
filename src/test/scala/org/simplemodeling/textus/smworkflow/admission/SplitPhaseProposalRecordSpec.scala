package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseProposalRecordSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import SplitPhaseHostProposalReadPolicy.*
  import SplitPhaseProposalRecordFixtures.*
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"record fixture failed: $other")
  }
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "splitproposal", name))
  private val bindings = Purpose.values.toVector.map(role => role -> collection(role.toString.toLowerCase))
  private val selection = collection("selections")
  private val source = collection("sources")
  private val candidate = collection("candidates")
  private val values: Map[String, ConfigurationValue] = Map(
    prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
    SplitPhaseHostSelectionReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
    SplitPhaseHostSelectionReadPolicy.prefix + "selection-collection" -> ConfigurationValue.StringValue(selection.print),
    SplitPhaseHostEntryReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
    SplitPhaseHostEntryReadPolicy.prefix + "source-collection" -> ConfigurationValue.StringValue(source.print),
    SplitPhaseHostEntryReadPolicy.prefix + "proposal-collection" -> ConfigurationValue.StringValue(candidate.print)) ++
    bindings.map((role, identity) => prefix + role.key -> ConfigurationValue.StringValue(identity.print))
  private def host(values: Map[String, ConfigurationValue]) = ResolvedConfiguration(HostConfiguration(values), ConfigurationTrace.empty)
  private val config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("proposal-records")),
    MigrationPolicy("v1"), RetentionPolicy("v1"), LeasePolicy("v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("proposal-records")))
  private val workflow = SplitPhaseManagedStartAssembly.Declaration(config, collection("instances"), collection("continuations"))

  "SplitPhase managed proposal projections" should {
    "bind all four exact purposes with no context-free datastore capability" in {
      Given("resolved host declarations for nine separate collections")
      When("the host composes its read-only proposal port")
      val port = take(bindC(host(values), workflow))
      Then("every declared purpose is admitted only to its own collection and all plain reads refuse")
      for ((role, identity) <- bindings) {
        admitC(host(values), role, "workflow", identity) shouldBe a[Consequence.Success[?]]
        bindings.filterNot(_._1 == role).foreach { (_, other) =>
          admitC(host(values), role, "workflow", other) shouldBe a[Consequence.Failure[?]]
        }
      }
      Vector(port.loadInventoryC(snapshot), port.loadDraftC(proposal),
        port.loadValidationEvidenceC(proposal), port.loadSourceBoundaryC(snapshot)).foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "deny missing, foreign and aliased host settings without selecting another provider" in {
      Given("the exact four proposal roles plus the five reserved collections")
      val missing = values.keys.toVector.map(key => bindC(host(values.removed(key)), workflow))
      val aliases = for {
        role <- Purpose.values.toVector
        reserved <- Vector(selection, source, candidate, workflow.instances, workflow.continuations)
      } yield bindC(host(values.updated(prefix + role.key, ConfigurationValue.StringValue(reserved.print))), workflow)
      When("incomplete snapshots, role aliases and noncanonical values reach composition")
      val results = missing ++ aliases ++ Vector(
        bindC(host(values.updated(prefix + Purpose.Draft.key, ConfigurationValue.StringValue(bindings.head._2.print))), workflow),
        bindC(host(values.updated(prefix + "store-name", ConfigurationValue.StringValue("other"))), workflow),
        bindC(host(values.updated(prefix + "store-name", ConfigurationValue.StringValue("Workflow"))), workflow),
        bindC(host(values.updated(prefix + Purpose.Inventory.key, ConfigurationValue.BooleanValue(true))), workflow),
        bindC(host(values.updated(prefix + Purpose.Inventory.key, ConfigurationValue.StringValue(bindings.head._2.print + " "))), workflow),
        bindC(null, workflow), bindC(host(values), null))
      Then("all requested adapters fail before any datastore access")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "roundtrip all four projections while keeping host evidence separate from semantic admission" in {
      Given("typed source inventory, draft, baseline evidence and source boundary fixtures")
      val codec = SplitPhaseProposalRecordJsonV1
      When("each projection is encoded and decoded at its explicit reference")
      val inventoryRead = codec.decodeInventoryC(snapshot, take(codec.encodeInventoryC(snapshot, inventory)))
      val draftRead = codec.decodeDraftC(proposal, take(codec.encodeDraftC(proposal, draft)))
      val validationRead = codec.decodeValidationC(proposal, take(codec.encodeValidationC(proposal, baseline)))
      val boundaryRead = codec.decodeBoundaryC(snapshot, take(codec.encodeBoundaryC(snapshot, boundary)))
      Then("the projections are lossless but stored baseline cannot supply a private Admitted result")
      inventoryRead shouldBe Consequence.success(inventory)
      draftRead shouldBe Consequence.success(draft)
      validationRead shouldBe Consequence.success(baseline.evidence)
      boundaryRead shouldBe Consequence.success(boundary)
      take(validationRead).semanticInputs shouldBe None
      SplitPhaseProposalValidation.validateC(draft, take(validationRead)) shouldBe a[Consequence.Failure[?]]
    }

    "reject extra or missing nested fields, primitive coercion, unknown enums and forged semantic inputs" in {
      Given("valid encoded draft and baseline records")
      val codec = SplitPhaseProposalRecordJsonV1
      def changed(raw: String, purpose: Purpose)(change: Json => Json): String = {
        val prefix = codec.schema(purpose) + ":"
        val root = parser.parse(raw.substring(prefix.length)).toOption.get
        prefix + root.mapObject(obj => obj.add("payload", change(obj("payload").get))).noSpaces
      }
      val draftWire = take(codec.encodeDraftC(proposal, draft))
      val validationWire = take(codec.encodeValidationC(proposal, baseline))
      val draftChanges = Vector[Json => Json](
        _.mapObject(_.remove("identity")),
        _.mapObject(_.add("extra", Json.fromString("extra"))),
        _.mapObject(obj => obj.add("children", Json.fromValues(obj("children").get.asArray.get.map(
          _.mapObject(_.add("extra", Json.fromString("extra"))))))),
        _.mapObject(obj => obj.add("children", Json.fromValues(obj("children").get.asArray.get.map(
          _.mapObject(_.add("expectedMinutes", Json.fromString("360"))))))))
      val evidenceChanges = Vector[Json => Json](
        _.mapObject(_.add("semanticInputs", Json.obj("admitted" -> Json.True))),
        _.mapObject(_.remove("semanticEdges")),
        _.mapObject(_.add("semanticEdges", Json.arr(Json.obj("afterItem" -> Json.fromString("work-1"),
          "disposition" -> Json.fromString("unknown"), "authorityReference" -> Json.fromString("source-authority"))))),
        _.mapObject(obj => obj.add("partitionPolicy", obj("partitionPolicy").get.mapObject(_.add("extra", Json.True)))),
        _.mapObject(obj => obj.add("validationOwnership", obj("validationOwnership").get.mapObject(
          _.add("timing", Json.fromString("unknown"))))))
      When("malformed payloads and foreign references reach decoding")
      val results = draftChanges.map(change => codec.decodeDraftC(proposal, changed(draftWire, Purpose.Draft)(change))) ++
        evidenceChanges.map(change => codec.decodeValidationC(proposal, changed(validationWire, Purpose.Validation)(change))) ++ Vector(
          codec.decodeDraftC("foreign", draftWire), codec.decodeDraftC(proposal, validationWire),
          codec.decodeDraftC(proposal, null), codec.decodeDraftC(proposal, "x" * (256 * 1024 + 1)))
      Then("none becomes a semantic admission, corrected estimate or trusted proposal")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }
}

/** Structurally typed but deliberately unadmitted baseline; no digest calculation. */
private[admission] object SplitPhaseProposalRecordFixtures {
  val snapshot = "snapshot-2"
  val proposal = "proposal-2"
  val inventory = SplitPhaseProposalPrecheck.SourceInventory("2", "a" * 64, Vector("work-1", "work-2"), Set("2"))
  val draft = SplitPhaseProposalPrecheck.ProposalDraft(proposal, "2", "source-authority", "a" * 64, Vector(
    SplitPhaseProposalPrecheck.Child("2", Vector("work-1"), 360, true),
    SplitPhaseProposalPrecheck.Child("2.1", Vector("work-2"), 360, true)))
  val baseline = SplitPhaseProposalRecordJsonV1.BaselineValidation(Vector.empty,
    SplitPhaseSemanticHandoff.Plan("a" * 64, "b" * 64, Vector.empty, Map("2" -> Vector.empty, "2.1" -> Vector.empty)),
    SplitPhaseValidationOwnership.Plan("a" * 64, "b" * 64, SplitPhaseValidationOwnership.Timing.FinalOnly,
      "sbt-full-suite", "sbt", "none", "2.1", Vector.empty),
    SplitPhasePartitionEnumeration.Policy("v1", 0, 64), Vector.empty)
  val boundary = SplitPhaseSourceBoundary.Contract("a" * 64, "2", "source-authority", Vector(
    SplitPhaseSourceBoundary.Item("work-1", Vector("close-1"), Vector.empty, "source-authority"),
    SplitPhaseSourceBoundary.Item("work-2", Vector("close-2"), Vector("work-1"), "source-authority")), Set.empty)
}
