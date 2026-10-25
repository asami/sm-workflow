package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.protocol.OperationResponseFormatter
import org.goldenport.convert.ValueReader
import org.goldenport.protocol.{Property, Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.{Record, RecordPresentable}
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.preparation.NativeAuthorityPreparation as Preparation
import org.simplemodeling.textus.smworkflow.value as V
import NativeExternalEvidence.{Purpose, Evidence}
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Native closing inputs and metadata are specification-owned inputs only.
  * They are not genuine independently issued operator permission.
  * @since   Oct. 14, 2026
  * @version Oct. 24, 2026
  */
private[admission] object NativeAuthorityPreparationFixture {
  val now = Instant.parse("2026-10-07T00:00:00Z")
  val reference = V.WorkflowArtifactReference("specification-authority", "1")
  val participant = "specification-operator"
  val issuer = "normal-development-host"
  val base = O.Snapshot("a" * 40, "b" * 40)
  val first = O.Commit("c" * 40, base.head, "d" * 40)
  val second = O.Commit("e" * 40, first.identity, "f" * 40)
  val completed = Vector(O.ClosedStep("step-1", first, "specification-step-review-1"),
    O.ClosedStep("step-2", second, "specification-step-review-2"))
  val plan = O.Plan("specification-plan", "1", "phase-6", "repository", Vector(
    O.Step("step-1", "First boundary", Vector("one.scala"), Vector.empty, Vector("focused")),
    O.Step("step-2", "Second boundary", Vector("two.scala"), Vector("step-1"), Vector("focused"))),
    Vector("focused"), repairLimit = 1)
  val stepInput = GoalPhaseNativeClosure.Input("specification-instance", "7", plan.identity, plan.revision,
    "step-1", 1L, base, O.Snapshot(base.head, first.tree), "specification-step-review-1")
  val phaseInput = GoalPhaseNativeRepositoryClosure.Input("specification-instance", "17", plan, base,
    completed, O.Snapshot(second.identity, second.tree), "specification-phase-review", "specification-child-invocation")
  val purposes = Vector(Purpose.StepClosure, Purpose.PhaseClosure)
  def authorityKey(purpose: Purpose): String = {
    if (purpose == Purpose.StepClosure) { ThreeWorkflowExternalEvidencePorts.key(stepInput.instance, stepInput.sourceRevision) }
    else { ThreeWorkflowExternalEvidencePorts.key(phaseInput.instance, phaseInput.sourceRevision) }
  }
  def contextC(core: ActionCall.Core, purpose: Purpose): Consequence[V.WorkflowAuthorityContext] = {
    if (purpose == Purpose.StepClosure) { NativeAuthorityContext.stepC(core, stepInput) }
    else { NativeAuthorityContext.phaseC(core, phaseInput) }
  }
  def bindingC(core: ActionCall.Core, purpose: Purpose): Consequence[Json] = {
    if (purpose == Purpose.StepClosure) { ThreeWorkflowExternalEvidencePorts.stepClosureBindingC(core, stepInput) }
    else { ThreeWorkflowExternalEvidencePorts.phaseClosureBindingC(core, phaseInput) }
  }
  def outputC(core: ActionCall.Core, purpose: Purpose): Consequence[V.GoalPhaseAdvanceResult] = {
    contextC(core, purpose).map(context => NativeWorkflowOperationOutput.goalWait("specification-instance",
      "Specification closing Wait", "Explicit host authority is absent").copy(authorityContext = Some(context)))
  }
  def json(record: Record): Json = { io.circe.parser.parse(take(record.toJsonStringC)).toOption.get }
  def record(json: Json): Record = { take(new RecordDecoder().json(json.noSpaces)) }
  def wire(value: RecordPresentable): Record = { record(json(value.toRecord())) }
  def normalCommandJson(value: RecordPresentable): String =
    OperationResponseFormatter.toResponse(Request.ofOperation("specificationAuthority").copy(properties = List(
      Property("textus.output.format", "json", None), Property("textus.output.shape", "data", None))),
      OperationResponse.RecordResponse(value.toRecord()), RunMode.Command) match {
      case Response.Json(text) => text
      case other => throw new IllegalStateException("Specification formatter returned " + other)
    }
  def evidence(prepared: Preparation.Prepared): Evidence = {
    take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
  }
}

/** Parent-selected validation owns execution; E5/E6 use actual public Goal
  * episodes in ThreeWorkflowGoalOperationSpec, not these native input fixtures.
  * @since   Oct. 14, 2026
  *  version Oct. 14, 2026
  * @version Oct. 18, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeAuthorityPreparationSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativePreparationManagedFixture {
  import NativeAuthorityPreparationFixture.*
  private val _declared = NativeExternalEvidence.Declaration("workflow", DataStore.CollectionId.EntityStore(
    EntityCollectionId("test", "host", "authority_evidence")), issuer)
  private def _prepare(context: V.WorkflowAuthorityContext): Preparation.Prepared = {
    take(Preparation.goalC(context, reference, participant, issuer, now.minusSeconds(60)))
  }
  private def _observe(value: Evidence, purpose: Purpose, binding: Json): Consequence[NativeExternalEvidence.Observed] = {
    NativeExternalEvidence.observeC(value, _declared, purpose, authorityKey(purpose), binding, now)
  }

  "E9 Phase recording closure target" should {
    "preserve the reviewed source and final management candidate through native projection and alias preparation" in {
      Given("a specification-owned Phase closure input with an accepted source and a distinct final Markdown index")
      withCore { call =>
        val source = phaseInput.candidate
        val finalinput = phaseInput.copy(candidate = source.copy(tree = "9" * 40), reviewedCandidate = Some(source))
        val legacy = take(ThreeWorkflowExternalEvidencePorts.phaseClosureBindingC(call.core, phaseInput))
        val expected = take(ThreeWorkflowExternalEvidencePorts.phaseClosureBindingC(call.core, finalinput))
        val context = take(NativeAuthorityContext.phaseC(call.core, finalinput))
        val canonical = json(context.scope)
        val reviewed = canonical.hcursor.downField("reviewedCandidate").focus.get
        When("normal generated output intake and canonical or ordinary wire aliases feed passive authority preparation")
        val output = NativeWorkflowOperationOutput.goalWait("specification-instance", "Final management target", "Separate authority")
          .copy(authorityContext = Some(context))
        val decoded = take(V.GoalPhaseAdvanceResult.createC(wire(output))).authorityContext.get
        val variants = Vector(json(decoded.scope), canonical.mapObject(_.remove("reviewedCandidate").add("reviewed_candidate", reviewed)),
          canonical.mapObject(_.add("reviewed_candidate", reviewed)))
        val prepared = variants.map(scope => evidence(_prepare(context.copy(scope = record(scope)))))
        Then("all normal spellings retain both actual targets while absent historical output remains absent")
        legacy.hcursor.downField("scope").focus.get.asObject.get.contains("reviewedCandidate") shouldBe false
        prepared.foreach { actual =>
          actual.binding shouldBe expected
          actual.binding.hcursor.downField("scope").downField("candidate").get[String]("tree").toOption shouldBe Some(finalinput.candidate.tree)
          actual.binding.hcursor.downField("scope").downField("reviewedCandidate").get[String]("tree").toOption shouldBe Some(source.tree)
          _observe(actual, Purpose.PhaseClosure, legacy).isSuccess shouldBe false
        }
        Given("contradictory optional source aliases and a different source tree")
        val conflicting = canonical.mapObject(_.add("reviewed_candidate", reviewed.mapObject(_.add("tree", Json.fromString("8" * 40)))))
        When("preparation or actual selected-grant admission consumes those crossed source targets")
        val refused = Preparation.goalC(context.copy(scope = record(conflicting)), reference, participant, issuer, now.minusSeconds(60))
        val other = take(ThreeWorkflowExternalEvidencePorts.phaseClosureBindingC(call.core,
          finalinput.copy(reviewedCandidate = Some(source.copy(tree = "8" * 40)))))
        Then("neither an alias contradiction nor another original source can reuse the final-target grant")
        refused.isSuccess shouldBe false
        _observe(prepared.head, Purpose.PhaseClosure, other).isSuccess shouldBe false
        Consequence.unit
      }
    }
  }

  "E7 Normal command closing targets" should afterWord("in spec:native-host-authority-preparation, example:E7, rules:R1,R2,R3,R6, phase:6") {
    "consume actual command json data formatting through the generated reader for both purposes" in {
      Given("owning-core Step and Phase closing projections and explicit specification metadata")
      withCore { call =>
        val outputs = purposes.map(purpose => purpose -> take(outputC(call.core, purpose)))
        val expected = purposes.map(purpose => purpose -> take(bindingC(call.core, purpose))).toMap
        When("the actual Command/json/data formatter, RecordDecoder and strict generated reader feed passive preparation")
        val prepared = outputs.map { (purpose, output) =>
          val formatted = normalCommandJson(output)
          val decoded = take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(take(new RecordDecoder().json(formatted))))
          purpose -> _prepare(decoded.authorityContext.get)
        }
        Then("canonical selected scope, binding, lookup and explicit metadata survive both normal wire purposes")
        prepared.foreach { (purpose, row) =>
          val actual = evidence(row)
          actual.binding shouldBe expected(purpose)
          actual.lookupIdentity shouldBe authorityKey(purpose)
          actual.purpose shouldBe purpose.identity
          actual.reference shouldBe reference.identity
          actual.revision shouldBe reference.revision
          actual.participant shouldBe participant
          actual.issuer shouldBe issuer
          actual.occurredAt shouldBe now.minusSeconds(60).toString
          row.recordIdentity shouldBe NativeExternalEvidence.recordIdentity(purpose, authorityKey(purpose))
          _observe(actual, purpose, expected(purpose)).isSuccess shouldBe true
        }
        Consequence.unit
      }
    }
  }

  "E8 Purpose-specific consumed aliases" should afterWord("in spec:native-host-authority-preparation, example:E8, rules:R2,R5,R6, phase:6") {
    "accept canonical wire and equal pairs while refusing conflicts missing fields and wrong consumed types" in {
      Given("canonical Step and Phase scopes with the finite consumed alias pairs and unchanged nested native targets")
      withCore { call =>
        val entries = purposes.map { purpose =>
          val context = take(contextC(call.core, purpose))
          val names = if (purpose == Purpose.StepClosure) Vector("sourceRevision" -> "source_revision",
            "planRevision" -> "plan_revision", "reviewEvidence" -> "review_evidence")
          else Vector("sourceRevision" -> "source_revision", "childInvocation" -> "child_invocation")
          (purpose, context, json(context.scope), names, take(bindingC(call.core, purpose)))
        }
        entries.foreach { (purpose, context, canonical, names, expected) =>
          names.foreach { (declaration, wireName) =>
            Given("the " + purpose.identity + " consumed pair " + declaration + "/" + wireName + " with canonical native values")
            val value = canonical.asObject.get(declaration).get
            val accepted = Vector(canonical, canonical.mapObject(_.remove(declaration).add(wireName, value)),
              canonical.mapObject(_.add(wireName, value)))
            val refused = Vector(canonical.mapObject(_.add(wireName, Json.fromString("conflicting-value"))),
              canonical.mapObject(_.remove(declaration)), canonical.mapObject(_.add(declaration, Json.True)),
              canonical.mapObject(_.remove(declaration).add(wireName, Json.True)),
              canonical.mapObject(_.add(declaration, Json.True).add(wireName, Json.True)))
            When("only this purpose's top-level consumed names are projected before the unchanged strict v7 codec")
            val rows = accepted.map(scope => _prepare(context.copy(scope = record(scope))))
            val failures = refused.map(scope => Preparation.goalC(context.copy(scope = record(scope)), reference,
              participant, issuer, now.minusSeconds(60)))
            Then("equal spellings collapse to one canonical binding and all contradictions or consumed type omissions fail")
            rows.foreach { row =>
              evidence(row).binding shouldBe expected
              evidence(row).lookupIdentity shouldBe authorityKey(purpose)
              evidence(row).binding.hcursor.downField("scope").focus.get.asObject.get.contains(wireName) shouldBe false
            }
            failures.foreach(_.isSuccess shouldBe false)
          }
        }
        Consequence.unit
      }
    }
  }

  "E1 Step closing target" should afterWord("in spec:native-host-authority-preparation, example:E1, rules:R1,R2,R3, phase:6") {
    "retain the actual native Step input through generated JSON intake and passive preparation" in {
      Given("a specification-owned native Step Closing Input and the actual owning managed core")
      withCore { call =>
        val output = take(outputC(call.core, Purpose.StepClosure))
        When("the generated full Goal output reader consumes normal JSON before explicit public preparation")
        val decoded = take(V.GoalPhaseAdvanceResult.createC(wire(output)))
        val context = decoded.authorityContext.get
        val prepared = _prepare(context)
        val expected = take(bindingC(call.core, Purpose.StepClosure))
        val actual = evidence(prepared)
        Then("current v7 scope and lookup agree with the unchanged native authority target and supplied metadata")
        context.purpose shouldBe Purpose.StepClosure.identity
        context.componentInstance shouldBe call.core.component.get.core.instanceId.canonicalKey
        actual.binding shouldBe expected
        actual.lookupIdentity shouldBe authorityKey(Purpose.StepClosure)
        actual.reference shouldBe reference.identity
        actual.revision shouldBe reference.revision
        actual.participant shouldBe participant
        actual.issuer shouldBe issuer
        actual.occurredAt shouldBe now.minusSeconds(60).toString
        prepared.recordIdentity shouldBe NativeExternalEvidence.recordIdentity(Purpose.StepClosure, authorityKey(Purpose.StepClosure))
        _observe(actual, Purpose.StepClosure, expected).isSuccess shouldBe true
        NativeWorkflowOperationOutput.goalWait("instance", "Ordinary Wait", "No Closing input").authorityContext shouldBe None
        Consequence.unit
      }
    }
  }

  "E2 Phase closing target" should afterWord("in spec:native-host-authority-preparation, example:E2, rules:R1,R2,R3, phase:6") {
    "retain the ordered chain base candidate review and child invocation with a distinct Phase purpose" in {
      Given("a specification-owned native Phase Closing Input with two ordered commits and an actual owning core")
      withCore { call =>
        val output = take(outputC(call.core, Purpose.PhaseClosure))
        When("the normal wire reader and public passive preparation consume the selected Phase context")
        val decoded = take(V.GoalPhaseAdvanceResult.createC(wire(output)))
        val prepared = _prepare(decoded.authorityContext.get)
        val actual = evidence(prepared)
        val expected = take(bindingC(call.core, Purpose.PhaseClosure))
        val scope = actual.binding.hcursor.downField("scope")
        Then("the current selected native Phase target preserves its commit order and cannot alias Step permission")
        actual.binding shouldBe expected
        scope.downField("completed").focus.get.asArray.get.map(_.hcursor.downField("commit").get[String]("identity").toOption.get) shouldBe
          completed.map(_.commit.identity)
        scope.downField("base").get[String]("head").toOption shouldBe Some(base.head)
        scope.downField("candidate").get[String]("tree").toOption shouldBe Some(second.tree)
        scope.get[String]("review").toOption shouldBe Some(phaseInput.review)
        scope.get[String]("childInvocation").toOption shouldBe Some(phaseInput.childInvocation)
        scope.downField("plan").focus.get.asObject.get.keys.toSet shouldBe Set("identity", "revision", "phase", "repository")
        actual.purpose shouldBe Purpose.PhaseClosure.identity
        actual.lookupIdentity shouldBe authorityKey(Purpose.PhaseClosure)
        _observe(actual, Purpose.PhaseClosure, expected).isSuccess shouldBe true
        _observe(actual, Purpose.StepClosure, take(bindingC(call.core, Purpose.StepClosure))).isSuccess shouldBe false
        Consequence.unit
      }
    }
  }

  "E3 Required consumed input" should afterWord("in spec:native-host-authority-preparation, example:E3, rules:R2,R3,R6, phase:6") {
    "refuse missing metadata wrong scope types and other purposes while discarding unused annotations" in {
      Given("native Step and Phase projections with explicit specification metadata and malformed consumed variants")
      withCore { call =>
        val step = take(contextC(call.core, Purpose.StepClosure))
        val phase = take(contextC(call.core, Purpose.PhaseClosure))
        val stepjson = json(step.scope)
        val phasejson = json(phase.scope)
        val wrongstepstrings = Vector("instance", "sourceRevision", "plan", "planRevision", "step", "reviewEvidence").flatMap { field =>
          Vector(stepjson.mapObject(_.remove(field)), stepjson.mapObject(_.add(field, Json.True)))
        }
        val wrongphasestrings = Vector("instance", "sourceRevision", "review", "childInvocation").flatMap { field =>
          Vector(phasejson.mapObject(_.remove(field)), phasejson.mapObject(_.add(field, Json.True)))
        }
        val wrongstep = wrongstepstrings ++ Vector(
          stepjson.mapObject(_.add("generation", Json.True)), stepjson.mapObject(_.add("generation", Json.fromDoubleOrNull(1.5))),
          stepjson.mapObject(_.remove("generation")), stepjson.mapObject(_.add("generation", Json.fromString("1"))),
          stepjson.mapObject(_.remove("candidate")), stepjson.mapObject(_.add("parent", Json.True)),
          stepjson.mapObject(_.add("parent", Json.obj("head" -> Json.True, "tree" -> Json.fromString(base.tree)))))
        val wrongphase = wrongphasestrings ++ Vector(phasejson.mapObject(_.add("completed", Json.True)), phasejson.mapObject(_.remove("completed")),
          phasejson.mapObject(_.add("completed", Json.arr(Json.obj("step" -> Json.True, "commit" -> Json.True)))),
          phasejson.mapObject(_.add("completed", Json.arr(Json.obj("step" -> Json.fromString("step-1"), "commit" -> Json.obj(
            "identity" -> Json.fromString(first.identity), "parent" -> Json.True, "tree" -> Json.fromString(first.tree)))))),
          phasejson.mapObject(_.add("base", Json.obj("head" -> Json.fromString(base.head), "tree" -> Json.True))),
          phasejson.mapObject(_.add("plan", Json.Null)))
        val unsupported = Purpose.values.toVector.filterNot(purposes.contains).map(p => step.copy(purpose = p.identity))
        val contexts = Vector(null, step.copy(componentInstance = ""), step.copy(scope = null), step.copy(purpose = null)) ++
          wrongstep.map(value => step.copy(scope = record(value))) ++ wrongphase.map(value => phase.copy(scope = record(value))) ++ unsupported
        When("passive preparation consumes malformed metadata or selected scope and then an annotated equivalent target")
        val failures = contexts.map(Preparation.goalC(_, reference, participant, issuer, now)) ++ Vector(
          Preparation.goalC(step, null, participant, issuer, now),
          Preparation.goalC(step, reference.copy(identity = ""), participant, issuer, now),
          Preparation.goalC(step, reference.copy(revision = ""), participant, issuer, now),
          Preparation.goalC(step, reference, null, issuer, now), Preparation.goalC(step, reference, "", issuer, now),
          Preparation.goalC(step, reference, participant, null, now), Preparation.goalC(step, reference, participant, "", now),
          Preparation.goalC(step, reference, participant, issuer, null))
        val annotatedstep = step.copy(scope = record(stepjson.mapObject(_.add("commandGrant", Json.True).add("diagnostic", Json.Null))))
        val annotatedphase = phase.copy(scope = record(phasejson.mapObject(_.add("grant", Json.True).add("plan",
          phasejson.hcursor.downField("plan").focus.get.mapObject(_.add("steps", Json.True).add("repairLimit", Json.Null))))))
        val originals = Vector(_prepare(step), _prepare(phase))
        val annotated = Vector(_prepare(annotatedstep), _prepare(annotatedphase))
        val missingowners = Vector(NativeAuthorityContext.stepC(call.core.copy(component = None), stepInput),
          NativeAuthorityContext.phaseC(call.core.copy(component = None), phaseInput),
          NativeAuthorityContext.stepC(call.core, null), NativeAuthorityContext.phaseC(call.core, null))
        Then("all consumed failures refuse and annotations create no authority or body certificate")
        failures.foreach(_.isSuccess shouldBe false)
        missingowners.foreach(_.isSuccess shouldBe false)
        annotated.map(_.record.getString(NativeExternalEvidence.storageField)) shouldBe originals.map(_.record.getString(NativeExternalEvidence.storageField))
        annotated.foreach(value => evidence(value).binding.noSpaces should not include "grant")
        Consequence.unit
      }
    }
  }

  "E4 Passive input and existing Reader" should afterWord("in spec:native-host-authority-preparation, example:E4, rules:R4,R5,R6, phase:6") {
    "require separate managed provisioning and refuse crossed native targets without adding metadata correlation" in {
      Given("both passive specification-owned rows and a separately configured current managed Reader")
      var retained = Option.empty[(NativeExternalEvidence.Reader, ActionCall.Core, Purpose, Json)]
      withCore { call =>
        val reader = take(NativeExternalEvidence.bindC(call.core.component.get.subsystem.get.configuration,
          GoalPhaseRuntimeParameters.Persistence(_configuration_, _collection_("instances"), _collection_("continuations"))))
        val entries = purposes.map { purpose =>
          val context = take(contextC(call.core, purpose))
          (purpose, _prepare(context), take(bindingC(call.core, purpose)))
        }
        When("the configured Reader attempts to consume rows that preparation has only returned")
        val absent = entries.map { (purpose, _, target) => take(reader.loadC(call.core, purpose, authorityKey(purpose), target)) }
        Then("no physical authority record exists and context supplies no review Human or effect permission")
        absent shouldBe Vector(None, None)
        entries.foreach { (purpose, prepared, target) =>
          Vector(Purpose.GoalReview, Purpose.GoalHuman, Purpose.RepositoryEffect).foreach { other =>
            NativeExternalEvidence.observeC(evidence(prepared), _declared, other, authorityKey(purpose), target, now).isSuccess shouldBe false
          }
        }
        Given("the specification explicitly provisions each returned input using the existing managed Create grant")
        entries.foreach { (_, prepared, _) => take(call.createC("host_evidence", prepared.recordIdentity, prepared.record)) }
        When("the live Reader compares selected target owner lookup issuer purpose and occurrence")
        val accepted = entries.map { (purpose, _, target) => take(reader.loadC(call.core, purpose, authorityKey(purpose), target)) }
        val crossed = entries.flatMap { (purpose, prepared, target) =>
          val value = evidence(prepared)
          val foreignowner = target.mapObject(_.add("componentInstance", Json.fromString("foreign-owner")))
          val foreignscope = NativeHumanEvidenceFixture.alter(target, Vector("scope", "candidate"))(
            _.mapObject(_.add("tree", Json.fromString("9" * 40))))
          Vector(reader.loadC(call.core, purpose, authorityKey(purpose), foreignowner),
            reader.loadC(call.core, purpose, authorityKey(purpose), foreignscope),
            _observe(value.copy(lookupIdentity = "foreign-lookup"), purpose, target),
            _observe(value.copy(issuer = "foreign-issuer"), purpose, target),
            _observe(value.copy(purpose = Purpose.RepositoryEffect.identity), purpose, target),
            _observe(value.copy(occurredAt = now.plusSeconds(1).toString), purpose, target))
        }
        val metadata = entries.map { (purpose, prepared, target) =>
          _observe(evidence(prepared).copy(reference = "another-explicit-reference", revision = "2", participant = "another-explicit-operator"), purpose, target)
        }
        val absentlookup = entries.map { (purpose, _, target) => take(reader.loadC(call.core, purpose, "foreign-lookup", target)) }
        val contextfree = entries.map { (purpose, _, target) => reader.loadC(null, purpose, authorityKey(purpose), target) }
        Then("only matching physical rows are admitted; valid reference and participant metadata is preserved without target correlation")
        accepted.map(_.nonEmpty) shouldBe Vector(true, true)
        crossed.foreach(_.isSuccess shouldBe false)
        metadata.foreach(_.isSuccess shouldBe true)
        absentlookup shouldBe Vector(None, None)
        contextfree.foreach(_.isSuccess shouldBe false)
        retained = Some((reader, call.core, entries.head._1, entries.head._3))
        Consequence.unit
      }
      When("the owning managed runtime has ended")
      val (reader, core, purpose, target) = retained.get
      val ended = reader.loadC(core, purpose, authorityKey(purpose), target)
      Then("retained passive facts and a former call grant no live managed read access")
      ended.isSuccess shouldBe false
    }
  }
}
