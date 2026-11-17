/**
 * @since   Nov. 17, 2026
 * @version Nov. 17, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Files
import org.goldenport.convert.ValueReader
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.RecordPresentable
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import SplitPhaseExplicitPartitionFixture.take

/** Actual managed public Operations; inputs remain explicitly specification-owned.
  * No Workflow transition, Git command or SBT process is requested by this episode.
  * The embedded host uses owned temporary files and SQLite; the fixture's process setup callback is unused.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class DevelopmentJournalSemanticOperationSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  "Managed semantic Journal Operations" should {
    "record link correct change state and read selected retained revisions through their generated ABI" in {
      Given("a normal isolated managed host with its declared Journal purpose and collection")
      val root = Files.createTempDirectory("journal-semantic-operation-").toRealPath()
      val policy = DevelopmentJournal.Policy("workflow", _collection_("development_journal"))
      _with_runtime_(root, "JournalSemantic", hostParameters = NativeWorkflowCliFixture.JournalHost(policy).values) {
        (owner, component, actor, _) =>
          def call[A: ValueReader](operation: String, value: RecordPresentable): A = read_value[A](take(
            owner.executeOperationResponseInContext(Request.of(component = component.name, service = "JournalService",
              operation = operation, properties = List(Property("input", value.toRecord(), None))), actor)))
          val original = V.JournalSemanticContent("Observed setup failure", "SPECIFICATION-OWNED: setup failed before business entry",
            None, Vector(V.JournalSemanticLink("Fix", V.WorkflowArtifactReference("specification:setup-change", "1")),
              V.JournalSemanticLink("Validation", V.WorkflowArtifactReference("specification:next-start", "1"))))
          When("an attributed Decision and linked Issue use ordinary recording Operations")
          val decisionInput = V.JournalSemanticRecordInput("Decision", "repo", "11.1", None, "spec-session",
            V.WorkflowArtifactReference("specification:decision", "1"), original)
          val decision = call[V.JournalRecordingState]("recordJournalEntry", decisionInput)
          val issueInput = decisionInput.copy(kind = "Issue", sourceReference = V.WorkflowArtifactReference("specification:issue", "1"),
            content = original.copy(state = Some("open"), links = Vector(V.JournalSemanticLink("Decision",
              V.WorkflowArtifactReference(decision.entry.get.identity, decision.entry.get.revision)))))
          val issue = call[V.JournalRecordingState]("recordJournalEntry", issueInput)
          Then("the actual created entries return references and source reuse does not duplicate them")
          decision.status shouldBe "recorded"
          issue.status shouldBe "recorded"
          call[V.JournalRecordingState]("recordJournalEntry", issueInput).status shouldBe "alreadyRecorded"
          val handoff = call[V.JournalRecordingState]("recordJournalEntry", decisionInput.copy(kind = "Handoff",
            sourceReference = V.WorkflowArtifactReference("specification:handoff", "1"), content = original.copy(
              links = Vector(V.JournalSemanticLink("Issue", V.WorkflowArtifactReference(issue.entry.get.identity, "1"))))))
          handoff.status shouldBe "recorded"
          call[V.JournalSemanticReadResult]("readJournalSemanticEntry", V.JournalSemanticReadInput(handoff.entry.get.identity, None))
            .entry.get.content.links.head.reference.identity shouldBe issue.entry.get.identity
          val id = issue.entry.get.identity
          When("a correction and state update consume the exact expected revision")
          val correction = V.JournalSemanticChangeInput(id, "repo", "1", V.WorkflowArtifactReference("specification:correction", "1"),
            "Clarify the observed setup failure", issueInput.content.copy(summary = "Clarified setup failure"))
          val corrected = call[V.JournalRecordingState]("correctJournalEntry", correction)
          withClue(s"Managed correction returned sanitized status ${corrected.status}: ") {
            corrected.status shouldBe "updated"
          }
          call[V.JournalRecordingState]("correctJournalEntry", correction).status shouldBe "conflict"
          val updated = call[V.JournalRecordingState]("updateJournalIssueState", V.JournalIssueStateInput(id, "repo", "2",
            V.WorkflowArtifactReference("specification:resolution", "1"), "Selected setup correction checked in specification", "resolved"))
          Then("selected current and historical reads preserve original content and the previous revision")
          updated.entry.get.revision shouldBe "3"
          val current = call[V.JournalSemanticReadResult]("readJournalSemanticEntry", V.JournalSemanticReadInput(id, None)).entry.get
          current.content.summary shouldBe "Clarified setup failure"
          current.content.state shouldBe Some("resolved")
          current.previousRevision shouldBe Some("2")
          val old = call[V.JournalSemanticReadResult]("readJournalSemanticEntry", V.JournalSemanticReadInput(id, Some("1"))).entry.get
          old.content shouldBe issueInput.content
          old.sourceReference shouldBe issueInput.sourceReference
          When("a bounded related-ID and state query and the common kind query use the same store")
          val scope = V.JournalFindInput("repo", Some("11.1"), Some("spec-session"), Some("Issue"), None, None, None, Some(1))
          val found = call[V.JournalFindResult]("findJournalSemanticEntries", V.JournalSemanticFindInput(scope,
            Some("resolved"), Some(decision.entry.get.identity)))
          Then("only the matching current Entry is returned and invalid scope/state inputs make no success claim")
          found.status shouldBe "found"
          found.entries.map(_.entry) shouldBe Vector(updated.entry.get)
          call[V.JournalFindResult]("findJournalEntries", scope).entries.map(_.entry) shouldBe Vector(updated.entry.get)
          call[V.JournalRecordingState]("correctJournalEntry", correction.copy(repositoryIdentity = "other", expectedRevision = "3")).status shouldBe "invalid"
          call[V.JournalRecordingState]("updateJournalIssueState", V.JournalIssueStateInput(decision.entry.get.identity, "repo", "1",
            V.WorkflowArtifactReference("specification:bad-state", "1"), "Invalid kind", "closed")).status shouldBe "invalid"
          call[V.JournalFindResult]("findJournalSemanticEntries", V.JournalSemanticFindInput(scope, Some("accepted"), None)).status shouldBe "invalid"
      }
    }
    "return disabled for a valid semantic find and invalid for malformed filters without Journal configuration" in {
      Given("a normal managed host with Journal configuration absent")
      val root = Files.createTempDirectory("journal-semantic-disabled-").toRealPath()
      _with_runtime_(root, "JournalSemanticDisabled") { (owner, component, actor, _) =>
        def find(input: V.JournalSemanticFindInput): V.JournalFindResult = read_value[V.JournalFindResult](take(
          owner.executeOperationResponseInContext(Request.of(component = component.name, service = "JournalService",
            operation = "findJournalSemanticEntries", properties = List(Property("input", input.toRecord(), None))), actor)))
        val scope = V.JournalFindInput("repo", None, None, Some("Issue"), None, None, None, Some(1))
        When("the generated public Operation receives valid and invalid semantic filters")
        val disabled = find(V.JournalSemanticFindInput(scope, Some("open"), None))
        val invalid = find(V.JournalSemanticFindInput(scope, Some("accepted"), None))
        Then("it returns typed disabled or invalid with no entries or hasMore claim")
        disabled.status shouldBe "disabled"
        invalid.status shouldBe "invalid"
        Vector(disabled, invalid).foreach { value =>
          value.entries shouldBe empty
          value.hasMore shouldBe false
        }
      }
    }
    "report unavailable when an explicit dedicated SQLite purpose lacks its required path" in {
      Given("a granted Journal purpose with an explicitly malformed dedicated SQLite binding and a valid basic store")
      val root = Files.createTempDirectory("journal-semantic-unavailable-").toRealPath()
      val policy = DevelopmentJournal.Policy("unbound-journal", _collection_("development_journal"))
      val selector = org.goldenport.cncf.datastore.ComponentDataStore.Request.forComponent(
        org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId, policy.storeName).canonicalComponentSelector.get
      // A missing dedicated binding legitimately uses the configured basic store. An explicit
      // SQLite binding without a path fails resolution and must never fall through to that store.
      val parameters = NativeWorkflowCliFixture.JournalHost(policy).values +
        (s"textus.component.$selector.datastores.${policy.storeName}.kind" -> "sqlite")
      _with_runtime_(root, "JournalSemanticUnavailable", hostParameters = parameters) {
        (owner, component, actor, _) =>
          val id = DevelopmentJournalSemantic.identity("Issue", DevelopmentJournalSemantic.Source("specification:missing", "1"))
          val input = V.JournalIssueStateInput(id, "repo", "1", V.WorkflowArtifactReference("specification:attempt", "1"), "Purpose unavailable", "closed")
          When("the actual managed Operation attempts the bounded state update")
          val output = read_value[V.JournalRecordingState](take(owner.executeOperationResponseInContext(Request.of(
            component = component.name, service = "JournalService", operation = "updateJournalIssueState",
            properties = List(Property("input", input.toRecord(), None))), actor)))
          Then("it returns no mutation or Entry claim from another store")
          output.status shouldBe "unavailable"
          output.entry shouldBe None
          val recordInput = V.JournalSemanticRecordInput("Issue", "repo", "11.1", None, "spec-session",
            V.WorkflowArtifactReference("specification:refused-create", "1"),
            V.JournalSemanticContent("Refused purpose", "Specification-owned binding failure", Some("open"), Vector.empty))
          val recorded = read_value[V.JournalRecordingState](take(owner.executeOperationResponseInContext(Request.of(
            component = component.name, service = "JournalService", operation = "recordJournalEntry",
            properties = List(Property("input", recordInput.toRecord(), None))), actor)))
          recorded.status shouldBe "unavailable"
          recorded.entry shouldBe None
          val readInput = V.JournalSemanticReadInput(id, None)
          val selected = read_value[V.JournalSemanticReadResult](take(owner.executeOperationResponseInContext(Request.of(
            component = component.name, service = "JournalService", operation = "readJournalSemanticEntry",
            properties = List(Property("input", readInput.toRecord(), None))), actor)))
          selected.status shouldBe "unavailable"
          selected.entry shouldBe None
          When("the same unavailable managed purpose receives a valid semantic find")
          val scope = V.JournalFindInput("repo", None, None, Some("Issue"), None, None, None, Some(1))
          val findInput = V.JournalSemanticFindInput(scope, Some("open"), None)
          val found = read_value[V.JournalFindResult](take(owner.executeOperationResponseInContext(Request.of(
            component = component.name, service = "JournalService", operation = "findJournalSemanticEntries",
            properties = List(Property("input", findInput.toRecord(), None))), actor)))
          Then("the public result is sanitized unavailable and does not query the configured basic store")
          found.status shouldBe "unavailable"
          found.entries shouldBe empty
          found.hasMore shouldBe false
          found.message should not include root.toString
          found.message should not include "unbound-journal"
      }
    }
  }
}
