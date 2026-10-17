package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.Path
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.host.NativeHostRepositoryEffectPreparation as Preparation
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import NativeHostEvidence.{Purpose, Evidence}
import RepositorySyncNativeConvergence.Effect
import SplitPhaseExplicitPartitionFixture.take

/** Reusable selected inputs are specification-owned, never genuine consent.
  * @since   Oct. 14, 2026
  * @version Oct. 14, 2026
  */
private[admission] object NativeHostRepositoryEffectPreparationFixture {
  val now = Instant.parse("2026-10-07T00:00:00Z")
  val participant = "participant"
  val issuer = "normal-development-host"
  val reference = V.WorkflowArtifactReference("specification-repository-effect", "1")
  val target = Preparation.Target("/specification/repository", "main", "origin", "main", "/specification/upstream.git")
  val business = Json.obj("repositoryIdentity" -> Json.fromString("repository"), "localBranch" -> Json.fromString("main"),
    "trackingRemote" -> Json.fromString("origin"), "trackingBranch" -> Json.fromString("main"),
    "remoteEndpointIdentity" -> Json.fromString("upstream"), "syncMode" -> Json.fromString("sync"))
  def selection: ProfileStartAdmission.Selection = {
    val definition = take(RepositorySyncDefinitionBinding.bindC)
    ProfileStartAdmission.Selection("specification-repository-selection", NativeWorkflowStartInput.Kind.RepositorySync.operation,
      definition.workflowIdentity, definition.workflowRevision, participant, ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
      "specification-repository-invocation", None, Some(TypedValue("RepositorySyncStartInput", business)))
  }
  val effects = Effect.values.toVector
  def selectionRow(value: ProfileStartAdmission.Selection = selection): Record = {
    Record.data(ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(value)))
  }
  def context(value: ProfileStartAdmission.Selection = selection): RepositorySyncManagedFetch.StartContext = {
    val input = take(NativeWorkflowStartInput.decodeC(NativeWorkflowStartInput.Kind.RepositorySync,
      value.selectedInput.get.value)).asInstanceOf[NativeWorkflowStartInput.Repository]
    RepositorySyncManagedFetch.StartContext(value.selectionId, value.idempotencyKey, value.participantIdentity,
      input.repositoryIdentity, input.localBranch, input.trackingRemote, input.trackingBranch, input.remoteEndpointIdentity, input.syncMode)
  }
  def nativeTarget(value: Preparation.Target = target): Programs.Target = {
    Programs.Target(Path.of(value.root), value.localBranch, value.remote, value.remoteBranch, value.endpoint)
  }
  def effectLookup(effect: Effect, value: ProfileStartAdmission.Selection = selection): String = {
    ThreeWorkflowHostEvidencePorts.key(value.selectionId, value.idempotencyKey, effect.toString)
  }
  def prepareC(owner: String, effect: Effect, value: ProfileStartAdmission.Selection = selection): Consequence[Preparation.Prepared] = {
    Preparation.repositoryC(owner, selectionRow(value), target, effect.toString, reference, participant, issuer, now.minusSeconds(60))
  }
  def evidence(prepared: Preparation.Prepared): Evidence = {
    take(NativeHostEvidence.decodeStorageC(prepared.record.getString(NativeHostEvidence.storageField).get))
  }
  def targetRow(value: Preparation.Target = target): Record = {
    Record.data("root" -> value.root, "localBranch" -> value.localBranch, "remote" -> value.remote,
      "remoteBranch" -> value.remoteBranch, "endpoint" -> value.endpoint)
  }
  def json(row: Record): Json = { io.circe.parser.parse(take(row.toJsonStringC)).toOption.get }
  def record(value: Json): Record = { take(new RecordDecoder().json(value.noSpaces)) }
  def changeSelection(change: Json => Json): Record = {
    val wire = selectionRow().getString(ProfileSelectionJsonV1.storageField).get
    val prefix = wire.take(wire.indexOf(':') + 1)
    val original = io.circe.parser.parse(wire.drop(prefix.length)).toOption.get
    Record.data(ProfileSelectionJsonV1.storageField -> (prefix + change(original).noSpaces))
  }
}

/** Public passive producer, current Reader and native effect authority.
  * E6 uses real public Operations in ThreeWorkflowRepositoryOperationSpec.
  * @since   Oct. 14, 2026
  * @version Oct. 14, 2026
  */
final class NativeHostRepositoryEffectPreparationSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeHostPreparationManagedFixture {
  import NativeHostRepositoryEffectPreparationFixture.*
  private val _declared = NativeHostEvidence.Declaration("workflow", DataStore.CollectionId.EntityStore(
    EntityCollectionId("test", "host", "repository_effect_evidence")), issuer)

  "E1 Exact selected repository effect" should afterWord("in spec:native-host-repository-effect-preparation, example:E1, rules:R1,R2,R3, phase:6") {
    "match the original native literal and shared port for all three exact effects without a session" in {
      Given("current specification-owned ProfileSelection business input, physical host target and actual managed owner")
      withCore { call =>
        val owner = call.core.component.get.core.instanceId.canonicalKey
        When("public preparation consumes the actual selected input and explicit metadata for each single effect")
        val prepared = effects.map(effect => effect -> take(prepareC(owner, effect)))
        val native = effects.map(effect => take(ThreeWorkflowHostEvidencePorts.repositoryEffectBindingC(call.core, context(), nativeTarget(), effect)))
        Then("the original literal scope, native projection, external lookup and metadata remain identical")
        prepared.zip(native).foreach { (entry, binding) =>
          val (effect, row) = entry
          val literal = Json.obj("componentInstance" -> Json.fromString(owner), "scope" -> Json.obj(
            "kind" -> Json.fromString("repository-effect"), "selection" -> Json.fromString("specification-repository-selection"),
            "invocation" -> Json.fromString("specification-repository-invocation"), "participant" -> Json.fromString("participant"),
            "repository" -> Json.fromString("repository"), "endpointIdentity" -> Json.fromString("upstream"), "mode" -> Json.fromString("sync"),
            "effect" -> Json.fromString(effect.toString), "target" -> Json.obj("root" -> Json.fromString("/specification/repository"),
              "localBranch" -> Json.fromString("main"), "remote" -> Json.fromString("origin"), "remoteBranch" -> Json.fromString("main"),
              "endpoint" -> Json.fromString("/specification/upstream.git"))))
          val actual = evidence(row)
          actual.binding shouldBe literal
          actual.binding shouldBe binding
          actual.lookupIdentity shouldBe effectLookup(effect)
          actual.purpose shouldBe Purpose.RepositoryEffect.identity
          actual.reference shouldBe reference.identity
          actual.revision shouldBe reference.revision
          actual.participant shouldBe participant
          actual.issuer shouldBe issuer
          actual.occurredAt shouldBe now.minusSeconds(60).toString
          row.recordIdentity shouldBe NativeHostEvidence.recordIdentity(Purpose.RepositoryEffect, effectLookup(effect))
          actual.binding.noSpaces should not include "sessionId"
          actual.binding.noSpaces should not include "workflowIdentity"
          actual.binding.hcursor.downField("scope").downField("origin").focus shouldBe None
        }
        Consequence.unit
      }
    }
  }

  "E2 Consumed inputs and passive semantics" should afterWord("in spec:native-host-repository-effect-preparation, example:E2, rules:R1,R2,R3,R6, phase:6") {
    "refuse malformed consumed inputs while preserving existing decoder semantics and inert annotations" in {
      Given("selected specification business input with bounded missing, null, wrong-type and unsupported variants")
      withCore { call =>
        val owner = call.core.component.get.core.instanceId.canonicalKey
        val row = selectionRow()
        def _prepare_(selected: Record = row, physical: Preparation.Target = target, effect: String = "Push",
          component: String = owner, ref: V.WorkflowArtifactReference = reference, operator: String = participant,
          source: String = issuer, occurred: Instant = now) = {
          Preparation.repositoryC(component, selected, physical, effect, ref, operator, source, occurred)
        }
        val wrongbusiness = business.asObject.get.keys.toVector.flatMap(field => Vector(
          business.mapObject(_.remove(field)), business.mapObject(_.add(field, Json.True)), business.mapObject(_.add(field, Json.Null))))
        val wrongrows = Vector(null, Record.empty, Record.data("selectionPayload" -> null), Record.data("selectionPayload" -> true),
          Record.data("selectionPayload" -> "unsupported:"),
          changeSelection(_.mapObject(_.remove("startOperation"))), changeSelection(_.mapObject(_.add("startOperation", Json.Null))),
          changeSelection(_.mapObject(_.remove("selectedInput"))), changeSelection(_.mapObject(_.add("selectedInput", Json.Null))),
          changeSelection(_.mapObject(_.add("schemaVersion", Json.fromString("unsupported")))),
          changeSelection(_.mapObject(_.add("startOperation", Json.obj("service" -> Json.fromString("GoalPhaseService"),
            "operation" -> Json.fromString("startGoalPhase"))))),
          changeSelection(value => NativeHostHumanEvidenceFixture.alter(value, Vector("selectedInput"))(
            _.mapObject(_.add("typeIdentity", Json.fromString("GoalPhaseStartInput")))))) ++
          wrongbusiness.map(value => changeSelection(original => NativeHostHumanEvidenceFixture.alter(original, Vector("selectedInput"))(
            _.mapObject(_.add("value", value)))))
        val wrongtargets = Vector(null, target.copy(root = null), target.copy(root = "relative/repository"),
          target.copy(root = "/specification/../repository"), target.copy(root = "/specification/./repository"),
          target.copy(root = "\u0000"), target.copy(localBranch = ""), target.copy(remote = null),
          target.copy(remoteBranch = " main"), target.copy(endpoint = "endpoint\n"))
        val current = context()
        val physical = nativeTarget()
        val wrongcontexts = Vector(null, current.copy(selectionIdentity = null), current.copy(invocationIdentity = null),
          current.copy(participantIdentity = null), current.copy(repositoryIdentity = null),
          current.copy(remoteEndpointIdentity = null), current.copy(syncMode = null))
        val wrongnativetargets = Vector(null, physical.copy(root = null), physical.copy(localBranch = null),
          physical.copy(remote = null), physical.copy(remoteBranch = null), physical.copy(endpoint = null))
        When("the passive producer and shared projection receive malformed inputs and annotated valid selections")
        val failures = wrongrows.map(value => _prepare_(selected = value)) ++ wrongtargets.map(value => _prepare_(physical = value)) ++
          Vector(null, "", "push", "All", " Push").map(value => _prepare_(effect = value)) ++ Vector(
          _prepare_(component = null), _prepare_(component = " owner"), _prepare_(ref = null), _prepare_(ref = reference.copy(identity = "")),
          _prepare_(ref = reference.copy(revision = null)), _prepare_(operator = null), _prepare_(operator = ""),
          _prepare_(source = null), _prepare_(source = "issuer\n"), _prepare_(occurred = null))
        val scopefailures = wrongcontexts.map(value => NativeHostRepositoryEffectScope.bindingFromInputsC(owner, value, physical, Effect.Push)) ++
          wrongnativetargets.map(value => NativeHostRepositoryEffectScope.bindingFromInputsC(owner, current, value, Effect.Push)) ++ Vector(
          NativeHostRepositoryEffectScope.bindingFromInputsC(null, current, physical, Effect.Push),
          NativeHostRepositoryEffectScope.bindingFromInputsC(owner, current, physical, null),
          ThreeWorkflowHostEvidencePorts.repositoryEffectBindingC(call.core.copy(component = None), current, physical, Effect.Push))
        val annotated = _prepare_(selected = record(json(row).mapObject(_.add("grant", Json.True))))
        val origins = ProfileStartAdmission.SelectionOrigin.values.toVector.map(origin => _prepare_(selected = selectionRow(selection.copy(
          origin = origin, recommendationReference = Some("inert-recommendation")))))
        val spaced = selection.copy(selectedInput = Some(TypedValue("RepositorySyncStartInput",
          business.mapObject(_.add("syncMode", Json.fromString(" sync "))))))
        val semantics = _prepare_(selected = selectionRow(spaced), physical = target.copy(localBranch = "other-physical-branch"))
        Then("consumed failures refuse, annotations and origins grant nothing, and current business decoding adds no semantic admission")
        failures.foreach(_.isSuccess shouldBe false)
        scopefailures.foreach(_.isSuccess shouldBe false)
        evidence(take(annotated)).binding shouldBe evidence(take(_prepare_())).binding
        origins.foreach(value => evidence(take(value)).binding shouldBe evidence(take(_prepare_())).binding)
        semantics.isSuccess shouldBe true
        evidence(take(semantics)).binding.hcursor.downField("scope").get[String]("mode").toOption shouldBe Some(" sync ")
        evidence(take(semantics)).binding.hcursor.downField("scope").downField("target").get[String]("localBranch").toOption shouldBe
          Some("other-physical-branch")
        Consequence.unit
      }
    }
  }

  "E3 Current native applicability" should afterWord("in spec:native-host-repository-effect-preparation, example:E3, rules:R2,R3,R4,R6, phase:6") {
    "refuse every crossed target and preserve native metadata participant equality and distinct purposes" in {
      Given("a public prepared specification row and the existing native observer for its actual target")
      withCore { call =>
        val owner = call.core.component.get.core.instanceId.canonicalKey
        val prepared = take(prepareC(owner, Effect.Push))
        val value = evidence(prepared)
        val binding = value.binding
        def _observe_(entry: Evidence = value, target: Json = binding) =
          NativeHostEvidence.observeC(entry, _declared, Purpose.RepositoryEffect, effectLookup(Effect.Push), target, now)
        val fields = Vector("selection", "invocation", "participant", "repository", "endpointIdentity", "mode", "effect")
        val physical = Vector("root", "localBranch", "remote", "remoteBranch", "endpoint")
        val targets = Vector(binding.mapObject(_.add("componentInstance", Json.fromString("foreign-owner")))) ++
          fields.map(field => NativeHostHumanEvidenceFixture.alter(binding, Vector("scope"))(_.mapObject(_.add(field, Json.fromString("foreign"))))) ++
          physical.map(field => NativeHostHumanEvidenceFixture.alter(binding, Vector("scope", "target"))(_.mapObject(_.add(field, Json.fromString("foreign")))))
        When("the observer compares each target field, lookup, issuer, purpose, occurrence and explicit operator correlation")
        val crossed = targets.map(target => _observe_(target = target)) ++ Vector(_observe_(value.copy(lookupIdentity = "foreign")),
          _observe_(value.copy(issuer = "foreign")), _observe_(value.copy(occurredAt = now.plusSeconds(1).toString))) ++
          Vector(Purpose.RepositoryReview, Purpose.SplitStart, Purpose.StepClosure, Purpose.PhaseClosure).map(p => _observe_(value.copy(purpose = p.identity)))
        val mismatched = take(Preparation.repositoryC(owner, selectionRow(), target, "Push", reference, "other-operator", issuer, now))
        val mismatchedobservation = _observe_(evidence(mismatched))
        val accepted = _observe_()
        val metadata = _observe_(value.copy(reference = "another-explicit-reference", revision = "2"))
        Then("only exact current effect authority is accepted and preparing a different operator cannot authorize it")
        crossed.foreach(_.isSuccess shouldBe false)
        mismatchedobservation.isSuccess shouldBe false
        mismatchedobservation.toString should include("another selected participant")
        accepted.isSuccess shouldBe true
        accepted.toOption.get.evidence.participant shouldBe participant
        metadata.isSuccess shouldBe true
        metadata.toOption.get.evidence.reference shouldBe "another-explicit-reference"
        metadata.toOption.get.evidence.revision shouldBe "2"
        Consequence.unit
      }
    }
  }

  "E4 Separate managed provisioning" should afterWord("in spec:native-host-repository-effect-preparation, example:E4, rules:R1,R4,R6, phase:6") {
    "remain absent until managed Create and retain configured read grants current core and exact target checks" in {
      Given("a passive specification row and the actual configured managed Reader")
      var retained = Option.empty[(NativeHostEvidence.Reader, ActionCall.Core, Json)]
      withCore { call =>
        val host = call.core.component.get.subsystem.get.configuration
        val persistence = GoalPhaseRuntimeParameters.Persistence(_configuration_, _collection_("instances"), _collection_("continuations"))
        val reader = take(NativeHostEvidence.bindC(host, persistence))
        val prepared = take(prepareC(call.core.component.get.core.instanceId.canonicalKey, Effect.Push))
        val binding = evidence(prepared).binding
        When("the live Reader looks for a row that preparation has only returned")
        val absent = take(reader.loadC(call.core, Purpose.RepositoryEffect, effectLookup(Effect.Push), binding))
        Then("there is no stored permission or implicit read grant")
        absent shouldBe None
        Given("the specification separately provisions that row with the existing managed Create grant")
        take(call.createC("host_evidence", prepared.recordIdentity, prepared.record))
        val foreignhost = ResolvedConfiguration(HostConfiguration(host.configuration.values.updated(
          NativeHostEvidence.prefix + "record-collection", ConfigurationValue.StringValue(
            EntityCollectionId("smworkflow", "cli", "foreign_evidence").print))), ConfigurationTrace.empty)
        val foreignreader = take(NativeHostEvidence.bindC(foreignhost, persistence))
        When("the current Reader attempts exact, foreign, ungranted and component-free reads")
        val accepted = take(reader.loadC(call.core, Purpose.RepositoryEffect, effectLookup(Effect.Push), binding))
        val denied = foreignreader.loadC(call.core, Purpose.RepositoryEffect, effectLookup(Effect.Push), binding)
        val crossed = reader.loadC(call.core, Purpose.RepositoryEffect, effectLookup(Effect.Push),
          binding.mapObject(_.add("componentInstance", Json.fromString("foreign-owner"))))
        val foreignlookup = take(reader.loadC(call.core, Purpose.RepositoryEffect, "foreign", binding))
        val contextfree = reader.loadC(call.core.copy(component = None), Purpose.RepositoryEffect, effectLookup(Effect.Push), binding)
        Then("only the exact matching current granted read is admitted")
        accepted.nonEmpty shouldBe true
        denied.isSuccess shouldBe false
        crossed.isSuccess shouldBe false
        foreignlookup shouldBe None
        contextfree.isSuccess shouldBe false
        retained = Some((reader, call.core, binding))
        Consequence.unit
      }
      When("the owning managed runtime has ended")
      val (reader, core, binding) = retained.get
      val ended = reader.loadC(core, Purpose.RepositoryEffect, effectLookup(Effect.Push), binding)
      Then("retained inputs and the former core do not keep managed read access alive")
      ended.isSuccess shouldBe false
    }
  }

  "E5 Native effect admission" should afterWord("in spec:native-host-repository-effect-preparation, example:E5, rules:R2,R3,R4,R6, phase:6") {
    "require independently provisioned exact authority for each effect without executing Git" in {
      Given("the actual current convergence AuthorityPort with no prepared rows in managed storage")
      withCore { call =>
        val reader = take(NativeHostEvidence.bindC(call.core.component.get.subsystem.get.configuration,
          GoalPhaseRuntimeParameters.Persistence(_configuration_, _collection_("instances"), _collection_("continuations"))))
        val authority = new ThreeWorkflowHostEvidencePorts(reader).convergence
        val owner = call.core.component.get.core.instanceId.canonicalKey
        When("native admission asks for each absent effect")
        val absent = effects.map(effect => authority.admitC(call.core, context(), nativeTarget(), effect))
        Then("all three independently required effect permissions are refused")
        absent.foreach(_.isSuccess shouldBe false)
        effects.foreach { effect =>
          Given("one public prepared specification effect row and a separate managed Create")
          val prepared = take(prepareC(owner, effect))
          take(call.createC("host_evidence", prepared.recordIdentity, prepared.record))
          When("native admission checks that effect and a crossed physical target")
          val exact = authority.admitC(call.core, context(), nativeTarget(), effect)
          val crossed = authority.admitC(call.core, context(), nativeTarget().copy(remoteBranch = "foreign"), effect)
          val notprovisioned = effects.filterNot(e => effects.indexOf(e) <= effects.indexOf(effect))
            .map(e => authority.admitC(call.core, context(), nativeTarget(), e))
          Then("only independently provisioned matching effects are admitted and no Git action is performed by admission")
          exact.isSuccess shouldBe true
          crossed.isSuccess shouldBe false
          notprovisioned.foreach(_.isSuccess shouldBe false)
        }
        Given("a separately addressed current selection and public prepared metadata belonging to another operator")
        val other = selection.copy(selectionId = "specification-mismatched-effect")
        val mismatched = take(Preparation.repositoryC(owner, selectionRow(other), target, "Push", reference, "other-operator", issuer, now))
        take(call.createC("host_evidence", mismatched.recordIdentity, mismatched.record))
        When("native effect admission consumes that managed row")
        val rejected = authority.admitC(call.core, context(other), nativeTarget(), Effect.Push)
        Then("the existing selected-participant equality still refuses the prepared input")
        rejected.isSuccess shouldBe false
        rejected.toString should include("another selected participant")
        Given("a public Push row separately provisioned at a FinalFetch address for another specification selection")
        val crossedselection = selection.copy(selectionId = "specification-crossed-effect")
        val push = take(prepareC(owner, Effect.Push, crossedselection))
        take(call.createC("host_evidence", NativeHostEvidence.recordIdentity(Purpose.RepositoryEffect,
          effectLookup(Effect.FinalFetch, crossedselection)), push.record))
        When("native FinalFetch admission reads that crossed-effect input")
        val crossedeffect = authority.admitC(call.core, context(crossedselection), nativeTarget(), Effect.FinalFetch)
        Then("separate provisioning cannot turn a different effect payload into the selected effect grant")
        crossedeffect.isSuccess shouldBe false
        Consequence.unit
      }
    }
  }
}
