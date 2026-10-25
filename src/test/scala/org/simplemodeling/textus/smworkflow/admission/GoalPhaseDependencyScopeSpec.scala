package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.context.RuntimeContext.{PropertyNameContext, PropertyNameStyle}
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseDependencyScope.*

/** Specification-owned reports prove input admission, never edits, consent or native success.
  * @since   Oct. 19, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseDependencyScopeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[T](value: Consequence[T]): T = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"Dependency scope input failed: $failure")
  }
  private val _requester = Component("org.simplemodeling.workflow", "sm-workflow", "workflow-code")
  private val _dependency = Component("org.simplemodeling.contracts", "sm-workflow", "workflow-code")
  private val _paths = Vector("src/main/scala/contracts/UsageContract.scala", "src/test/scala/contracts/UsageContractSpec.scala")
  private val _proposed = Proposal("dependency-change:P62-DECISION", "revision-1", "PLANNING", _requester, _dependency,
    ContextReference("usage-contract:dependency-api", "revision-4"),
    "The selected workflow needs a dependency API extension.\nRequest only the declared contract and its specification.",
    _paths, Vector("Retain existing callers", "Expose the selected contract\nwith its failure behavior"),
    Vector(ContextReference("PHASE-6.2:P62-DECISION", "1")), Vector.empty)
  private val _external = _proposed.copy(dependency = _dependency.copy(repository = "shared-contracts"))
  private val _escaped = _proposed.copy(discovery = "REVIEW", reason =
    "Specification-owned review report: an unapproved dependency edit escaped the assigned Component.\nHuman disposition is pending.",
    discoveredEdits = Vector(_paths.head))
  private def _field(value: Json, name: String, nested: Json): Json = value.mapObject(_.add(name, nested))

  "Logical Component and repository targets" should afterWord("in spec:goal-phase-dependency-scope, rules:R1,R2,R5") {
    "retain same-repository and external selections through owning codecs and actual public Record naming" in {
      Given("two distinct logical Components, a Usage Contract and exact work selections, with no configured workspace roots")
      val proposals = Vector(_proposed, _external)
      val contexts = Vector(PropertyNameContext.default,
        PropertyNameContext(outputStyle = PropertyNameStyle.KebabCase))
      When("the strict codecs and the public Record projection consume both repository relationships")
      val decoded = proposals.map(value => value.asJson.as[Proposal])
      val records = proposals.map(value => publicProposal(Some(value)).get)
      val restored = records.map(record => (Vector(record) ++ contexts.map(_.transformRecord(record)))
        .map(value => _take(fromPublicProposalC(Some(value)))))
      Then("every exact identifier, workspace, Usage Contract, path, acceptance and dependent work reference survives")
      decoded shouldBe proposals.map(Right(_))
      restored.zip(proposals).foreach { (values, expected) => values.foreach(_ shouldBe Some(expected)) }
      sameRepository(_proposed) shouldBe true
      sameRepository(_external) shouldBe false
      _external.requester.workspace shouldBe _external.dependency.workspace
      _proposed.requester.asJson.as[Component] shouldBe Right(_requester)
      _proposed.asJson.hcursor.get[Vector[String]]("discoveredEdits") shouldBe Right(Vector.empty[String])
      _take(validateOptionC(None)) shouldBe ()
      _take(fromPublicProposalC(None)) shouldBe None
      publicProposal(None) shouldBe None
    }
  }

  "Proposal source admission" should afterWord("in spec:goal-phase-dependency-scope, rules:R3") {
    "admit only matching decision-needed sources and leave absent proposals to the existing result owner" in {
      Given("planning, implementation, repair and review reports owned by their existing semantic kinds")
      val matching = Vector("ImplementStep" -> _proposed, "ImplementStep" -> _proposed.copy(discovery = "IMPLEMENTATION"),
        "RepairStep" -> _proposed.copy(discovery = "REPAIR"), "ReviewStep" -> _escaped, "ReviewPhase" -> _escaped)
      val crossed = Vector("ImplementStep" -> _proposed.copy(discovery = "REPAIR"), "RepairStep" -> _proposed,
        "ReviewStep" -> _proposed.copy(discovery = "IMPLEMENTATION"), "ReviewPhase" -> _proposed,
        "RecordGoalFindings" -> _escaped, null.asInstanceOf[String] -> _proposed)
      When("the pure input boundary checks source, outcome and the optional proposal itself")
      val admitted = matching.map { (kind, value) => admitResultC(kind, "decision-needed", Some(value)) }
      val wrongsource = crossed.map { (kind, value) => admitResultC(kind, "decision-needed", Some(value)) }
      val wrongoutcome = matching.flatMap { (kind, value) =>
        Vector("completed", "accept", "repair-required", "DECISION-NEEDED", null).map(outcome =>
          admitResultC(kind, outcome, Some(value)))
      }
      val absent = Vector("ImplementStep" -> "completed", "ReviewStep" -> "accept",
        "RepairStep" -> "repair-required", "existing-other-kind" -> "existing-outcome", (null, null))
        .map { (kind, outcome) => admitResultC(kind, outcome, None) }
      val malformed = Vector(null, Some(null), Some(_proposed.copy(paths = Vector.empty)))
        .map(value => admitResultC("ImplementStep", "decision-needed", value))
      Then("only the matching present proposals pass, and absence adds no new result restriction")
      admitted.foreach(_ shouldBe Consequence.unit)
      (wrongsource ++ wrongoutcome ++ malformed).foreach(_.isSuccess shouldBe false)
      absent.foreach(_ shouldBe Consequence.unit)
    }
  }

  "Disclosed review edits" should afterWord("in spec:goal-phase-dependency-scope, rules:R2,R4,R5") {
    "retain the exact report and reject changed discovery or an undisclosed expansion of requested paths" in {
      Given("a specification-owned independent-review report of one unapproved edit inside two requested literal files")
      val expanded = _escaped.copy(discoveredEdits = Vector("src/main/scala/contracts/Other.scala"))
      val prefix = _escaped.copy(paths = Vector("src/main"))
      When("the report roundtrips and admission checks its source and exact requested membership")
      val decoded = _escaped.asJson.as[Proposal]
      val restored = _take(fromPublicProposalC(publicProposal(Some(_escaped))))
      val rejected = Vector(_escaped.copy(discovery = "PLANNING"), _escaped.copy(discovery = "IMPLEMENTATION"),
        _escaped.copy(discovery = "REPAIR"), expanded, prefix).map(validateProposalC)
      Then("the disclosed paths and reason survive as a report without approval, reversion or acceptance")
      decoded shouldBe Right(_escaped)
      restored shouldBe Some(_escaped)
      restored.get.discoveredEdits shouldBe Vector(_paths.head)
      restored.get.reason shouldBe _escaped.reason
      rejected.foreach(_.isSuccess shouldBe false)
    }
  }

  "Finite proposal values" should afterWord("in spec:goal-phase-dependency-scope, rules:R1,R2,R4") {
    "refuse null nested data, duplicate selections and invalid logical identities or paths" in {
      Given("a valid logical proposal and representative null, duplicate, target and path violations")
      val nulls = Vector(null, _proposed.copy(requester = null), _proposed.copy(dependency = _dependency.copy(workspace = null)),
        _proposed.copy(usageContract = null), _proposed.copy(paths = null), _proposed.copy(paths = Vector(null)),
        _proposed.copy(acceptance = null), _proposed.copy(acceptance = Vector(null)), _proposed.copy(dependentWork = null),
        _proposed.copy(dependentWork = Vector(null)), _proposed.copy(discoveredEdits = null),
        _escaped.copy(discoveredEdits = Vector(null)), _proposed.copy(usageContract = ContextReference(null, "1")))
      val duplicates = Vector(_proposed.copy(paths = _paths :+ _paths.head),
        _proposed.copy(acceptance = _proposed.acceptance :+ _proposed.acceptance.head),
        _proposed.copy(dependentWork = _proposed.dependentWork ++ _proposed.dependentWork),
        _escaped.copy(discoveredEdits = Vector(_paths.head, _paths.head)))
      val targets = Vector(_proposed.copy(identity = " padded"), _proposed.copy(revision = "rev/1"),
        _proposed.copy(requester = _requester.copy(identity = "workflow\\component")),
        _proposed.copy(dependency = _dependency.copy(repository = "repo\nname")),
        _proposed.copy(dependency = _dependency.copy(repository = "two repos")),
        _proposed.copy(dependency = _dependency.copy(workspace = "/physical/root")),
        _proposed.copy(dependency = _dependency.copy(workspace = "UpperCase")),
        _proposed.copy(dependency = _dependency.copy(identity = _requester.identity)), _proposed.copy(discovery = "review"))
      val badpaths = Vector("/absolute/file", "../outside.scala", "src/../outside.scala", "src//file", "./file",
        "https://example.test/file", "file:relative", "src\\file", "src/line\nbreak").map(path => _proposed.copy(paths = Vector(path)))
      When("typed admission consumes each causally distinct malformed input")
      val refused = (nulls ++ duplicates ++ targets ++ badpaths).map(validateProposalC)
      val badoptions = Vector(null, Some(null)).map(validateOptionC)
      Then("no malformed input becomes an absent proposal or an authorized target")
      (refused ++ badoptions).foreach(_.isSuccess shouldBe false)
    }

    "apply UTF-8 and collection bounds while preserving literal multiline descriptions and reference text" in {
      Given("logical identifiers and descriptions at their byte limits, and finite exact collections")
      val bounded = _proposed.copy(identity = "x" * 1024, revision = "依" * 341,
        reason = "r" * 16384, acceptance = Vector("a" * 16384),
        usageContract = ContextReference(" Usage Contract with exact spacing ", "依" * 341))
      val maximum = _escaped.copy(paths = Vector.tabulate(128)(i => s"src/selected-$i.scala"),
        acceptance = Vector.tabulate(32)(i => s"Selected acceptance $i"),
        dependentWork = Vector.tabulate(32)(i => ContextReference(s"dependent-work:$i", "1")),
        discoveredEdits = Vector.tabulate(128)(i => s"src/selected-$i.scala"))
      val invalid = Vector(bounded.copy(identity = "依" * 342), bounded.copy(reason = "あ" * 5462),
        _proposed.copy(reason = " \n\t"), _proposed.copy(reason = "report\u0000text"), _proposed.copy(acceptance = Vector(" ")),
        _proposed.copy(usageContract = ContextReference("x" * 1025, "1")),
        _proposed.copy(usageContract = ContextReference("contract", "rev\n1")),
        _proposed.copy(paths = Vector.empty), _proposed.copy(acceptance = Vector.empty), _proposed.copy(dependentWork = Vector.empty),
        maximum.copy(paths = maximum.paths :+ "src/extra.scala"),
        maximum.copy(acceptance = maximum.acceptance :+ "Extra acceptance"),
        maximum.copy(dependentWork = maximum.dependentWork :+ ContextReference("extra", "1")),
        maximum.copy(discoveredEdits = maximum.discoveredEdits :+ "src/extra.scala"))
      When("typed validation and owning codecs consume bounded and excessive selections")
      val admitted = Vector(bounded, maximum).map(value => validateProposalC(value))
      val roundtrips = Vector(bounded, maximum).map(value => value.asJson.as[Proposal])
      val refused = invalid.map(validateProposalC)
      Then("valid exact content survives while byte overflow, blank or NUL text and collection violations fail")
      admitted.foreach(_ shouldBe Consequence.unit)
      roundtrips shouldBe Vector(Right(bounded), Right(maximum))
      refused.foreach(_.isSuccess shouldBe false)
    }
  }

  "Strict proposal JSON and public names" should afterWord("in spec:goal-phase-dependency-scope, rules:R5") {
    "refuse unknown, null, absent and wrong owning shapes including nested Components and references" in {
      Given("the exact owning JSON for a valid proposal with an explicitly empty discovered-edits vector")
      val wire = _proposed.asJson
      val component = _requester.asJson
      val reference = wire.hcursor.downField("usageContract").focus.get
      val malformed = Vector(wire.mapObject(_.add("approval", Json.True)), wire.mapObject(_.remove("discoveredEdits")),
        _field(wire, "paths", Json.Null), _field(wire, "paths", Json.fromString(_paths.head)),
        _field(wire, "discovery", Json.fromString("UNKNOWN")), _field(wire, "requester", Json.Null),
        _field(wire, "requester", component.mapObject(_.add("root", Json.fromString("/tmp/root")))),
        _field(wire, "requester", component.mapObject(_.remove("workspace"))),
        _field(wire, "dependency", _field(_dependency.asJson, "identity", Json.Null)),
        _field(wire, "usageContract", reference.mapObject(_.remove("revision"))),
        _field(wire, "usageContract", reference.mapObject(_.add("permission", Json.True))),
        _field(wire, "dependentWork", Json.arr(_field(reference, "identity", Json.Null))),
        _field(wire, "dependentWork", Json.arr(reference, reference)), Json.Null, Json.arr(), Json.True)
      When("the owning Proposal and Component decoders consume those malformed shapes")
      val refused = malformed.map(_.as[Proposal])
      val badcomponents = Vector(Json.Null, component.mapObject(_.remove("identity")),
        component.mapObject(_.add("unknown", Json.True))).map(_.as[Component])
      Then("no optional field, nested substitute or unknown authority field bypasses validation")
      (refused ++ badcomponents).foreach { result =>
        result.isLeft shouldBe true
        result.swap.toOption.get.message should startWith("GoalPhase dependency scope")
      }
    }

    "refuse colliding normalized public names and malformed Record presence" in {
      Given("equal public aliases of an owning name and a nested Usage Contract reference name")
      val wire = _proposed.asJson
      val collision = _field(wire, "usage_contract", wire.hcursor.downField("usageContract").focus.get)
      val nestedcollision = _field(wire, "usageContract", wire.hcursor.downField("usageContract").focus.get
        .mapObject(fields => fields.add("Identity", fields("identity").get)))
      val invalidrecords: Vector[Option[Record]] = Vector(null, Some(null),
        Some(GoalPhaseNativeMetadata.record(Json.obj("identity" -> Json.True))))
      When("existing CNCF normalization processes the public Records before the strict owning decoder")
      val refused = Vector(collision, nestedcollision).map(value => fromPublicProposalC(Some(GoalPhaseNativeMetadata.record(value)))) ++
        invalidrecords.map(fromPublicProposalC)
      Then("even equal aliases fail and malformed presence cannot be treated as absence")
      refused.foreach(_.isSuccess shouldBe false)
      refused.foreach(_.toString should include("GoalPhase dependency scope"))
    }
  }
}
