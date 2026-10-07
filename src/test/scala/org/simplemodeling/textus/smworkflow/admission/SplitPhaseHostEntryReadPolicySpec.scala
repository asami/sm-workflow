package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
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
final class SplitPhaseHostEntryReadPolicySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import SplitPhaseHostEntryReadPolicy.*
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "splitentry", name))
  private val source = collection("sources")
  private val proposal = collection("proposals")
  private val selection = collection("selections")
  private val values: Map[String, ConfigurationValue] = Map(
    prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
    prefix + Purpose.Source.key -> ConfigurationValue.StringValue(source.print),
    prefix + Purpose.Proposal.key -> ConfigurationValue.StringValue(proposal.print),
    SplitPhaseHostSelectionReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
    SplitPhaseHostSelectionReadPolicy.prefix + "selection-collection" -> ConfigurationValue.StringValue(selection.print))
  private def host(values: Map[String, ConfigurationValue]) =
    ResolvedConfiguration(HostConfiguration(values), ConfigurationTrace.empty)
  private val configuration = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("split-entry")),
    MigrationPolicy("v1"), RetentionPolicy("v1"), LeasePolicy("v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("split-entry")))
  private val workflow = SplitPhaseManagedStartAssembly.Declaration(configuration, collection("instances"), collection("continuations"))

  "SplitPhase managed entry read contract" should {
    "bind exact source and proposal purposes without a context-free read capability" in {
      Given("independent resolved SplitPhase declarations and no physical datastore")
      When("host composition derives the read-only entry port")
      val port = bindC(host(values), workflow).toOption.get
      Then("only the declared purpose is admitted and all context-free accesses are denied")
      admitC(host(values), Purpose.Source, "workflow", source) shouldBe a[Consequence.Success[?]]
      admitC(host(values), Purpose.Proposal, "workflow", proposal) shouldBe a[Consequence.Success[?]]
      admitC(host(values), Purpose.Source, "workflow", proposal) shouldBe a[Consequence.Failure[?]]
      admitC(host(values), Purpose.Proposal, "workflow", source) shouldBe a[Consequence.Failure[?]]
      port.loadSourceC("2", "authority") shouldBe a[Consequence.Failure[?]]
      port.loadProposalC("proposal") shouldBe a[Consequence.Failure[?]]
      port.loadSourceForCallC("2", "authority", null) shouldBe a[Consequence.Failure[?]]
      port.loadProposalForCallC("proposal", null) shouldBe a[Consequence.Failure[?]]
    }

    "deny incomplete declarations, foreign stores and aliases of every protected collection" in {
      Given("a complete exact entry and selection declaration")
      val missing = values.keys.toVector.map(key => bindC(host(values.removed(key)), workflow))
      val aliases = for {
        purpose <- Purpose.values.toVector
        reserved <- Vector(selection, workflow.instances, workflow.continuations)
      } yield bindC(host(values.updated(prefix + purpose.key, ConfigurationValue.StringValue(reserved.print))), workflow)
      When("composition receives absent, malformed, aliased or foreign store settings")
      val results = missing ++ aliases ++ Vector(
        bindC(host(values.updated(prefix + Purpose.Proposal.key, ConfigurationValue.StringValue(source.print))), workflow),
        bindC(host(values.updated(prefix + "store-name", ConfigurationValue.StringValue("other"))), workflow),
        bindC(host(values.updated(prefix + "store-name", ConfigurationValue.StringValue("Workflow"))), workflow),
        bindC(host(values.updated(prefix + Purpose.Source.key, ConfigurationValue.NumberValue(BigDecimal(1)))), workflow),
        bindC(host(values.updated(prefix + Purpose.Source.key, ConfigurationValue.StringValue(source.print + " "))), workflow),
        bindC(null, workflow), bindC(host(values), null),
        admitC(host(values), null, "workflow", source),
        admitC(host(values), Purpose.Source, "other", source))
      Then("none can create a read adapter or gain another purpose's binding")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "decode the existing source and proposal projections without generating identity or digests" in {
      Given("schema-tagged test records with independently specified source and proposal fields")
      import SplitPhaseEntryRecordFixtures.*
      When("the read-only projection codec decodes their storage payloads")
      val decodedSource = SplitPhaseEntryRecordJsonV1.decodeSourceStorageC(sourceWire)
      val decodedProposal = SplitPhaseEntryRecordJsonV1.decodeProposalStorageC(proposalWire)
      Then("the exact recorded identities, authority, revision and evidence survive")
      decodedSource shouldBe Consequence.success(sourceValue)
      decodedProposal shouldBe Consequence.success(proposalValue)
    }

    "reject unsupported encoding, schema, fields, primitive types and invalid projection evidence" in {
      Given("valid closed source and proposal JSON plus individually malformed variants")
      import SplitPhaseEntryRecordFixtures.*
      val changes = Vector[Json => Json](
        _.mapObject(_.remove("identity")),
        _.mapObject(_.add("foreign", Json.fromString("extra"))),
        _.mapObject(_.add("schemaVersion", Json.fromString("unsupported"))),
        _.mapObject(_.add("identity", Json.fromInt(1))),
        _.mapObject(_.add("authorityReference", Json.Null)),
        _.mapObject(_.add("digest", Json.fromString("not a digest"))),
        _.mapObject(_.add("identity", Json.fromString(" "))))
      When("each malformed record reaches the storage decoder")
      val sourceResults = changes.map(change => SplitPhaseEntryRecordJsonV1.decodeSourceStorageC(
        SplitPhaseEntryRecordJsonV1.sourcePrefix + change(sourceJson).noSpaces)) ++ Vector(
        SplitPhaseEntryRecordJsonV1.decodeSourceStorageC(null),
        SplitPhaseEntryRecordJsonV1.decodeSourceStorageC(sourceJson.noSpaces),
        SplitPhaseEntryRecordJsonV1.decodeSourceStorageC(proposalWire),
        SplitPhaseEntryRecordJsonV1.decodeSourceStorageC("x" * (64 * 1024 + 1)))
      val proposalResults = changes.map(change => SplitPhaseEntryRecordJsonV1.decodeProposalStorageC(
        SplitPhaseEntryRecordJsonV1.proposalPrefix + change(proposalJson).noSpaces)) :+
        SplitPhaseEntryRecordJsonV1.decodeProposalStorageC(SplitPhaseEntryRecordJsonV1.proposalPrefix +
          proposalJson.mapObject(_.add("sourceSnapshotDigest", Json.fromString("foreign"))).noSpaces)
      Then("no malformed value is coerced, defaulted or accepted as source authority")
      (sourceResults ++ proposalResults).foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }
}

/** Source/proposal fixture bytes only; no human selection or runtime authority. */
private[admission] object SplitPhaseEntryRecordFixtures {
  val sourceValue = SplitPhaseEntryAssessment.SourceSnapshot("snapshot-2", "2", "source-authority", "revision-1", "a" * 64)
  val proposalValue = SplitPhaseEntryAssessment.ProposalCandidate("proposal-2", "2", "source-authority", "a" * 64, "b" * 64)
  val sourceJson = Json.obj(
    "schemaVersion" -> Json.fromString(SplitPhaseEntryRecordJsonV1.sourceSchema),
    "identity" -> Json.fromString(sourceValue.identity),
    "sourcePhaseIdentity" -> Json.fromString(sourceValue.sourcePhaseIdentity),
    "authorityReference" -> Json.fromString(sourceValue.authorityReference),
    "revision" -> Json.fromString(sourceValue.revision), "digest" -> Json.fromString(sourceValue.digest))
  val proposalJson = Json.obj(
    "schemaVersion" -> Json.fromString(SplitPhaseEntryRecordJsonV1.proposalSchema),
    "identity" -> Json.fromString(proposalValue.identity),
    "sourcePhaseIdentity" -> Json.fromString(proposalValue.sourcePhaseIdentity),
    "authorityReference" -> Json.fromString(proposalValue.authorityReference),
    "sourceSnapshotDigest" -> Json.fromString(proposalValue.sourceSnapshotDigest),
    "digest" -> Json.fromString(proposalValue.digest))
  val sourceWire = SplitPhaseEntryRecordJsonV1.sourcePrefix + sourceJson.noSpaces
  val proposalWire = SplitPhaseEntryRecordJsonV1.proposalPrefix + proposalJson.noSpaces
}
