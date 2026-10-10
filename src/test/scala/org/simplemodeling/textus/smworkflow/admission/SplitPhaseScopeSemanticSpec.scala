package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseScopeSemantic as S, SplitPhaseScopeSemanticJson as J,
  SplitPhaseExplicitPartitionSemantic as P, SplitPhaseExplicitBoundaryValidation as B,
  SplitPhasePlanningSnapshot as R}
import SplitPhaseExplicitPartitionFixture.*

/**
 * @since   Oct.  4, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseScopeSemanticSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _checkpoint = {
    val source = base(Vector.fill(4)(180))
    val candidate = enumerate(source).candidates.find(_.cutAfterIndices == Vector(1)).get
    val boundary = proof(source, candidate, "scope-template", handoffPairs = Vector(0 -> 1))
    SplitPhasePlanningCheckpointFixture.forBoundary(source, boundary).record
  }
  private def _judgment(request: S.Request) = S.Judgment(
    S.Decision.Enriched, "transfer-closure-analysis", request.gaps.map(gap => S.Scope(gap.original.identity,
      gap.producingItems.takeRight(1), gap.receivingItems.take(1), "only these items produce and consume the original evidence")),
    Vector(P.EvidenceReference("scope-evidence", "1")))
  private def _reference(request: S.Request) = ContextReference(request.result.identity, request.result.revision)
  private def _ordered_subsets(items: Vector[SplitPhaseExplicitProposalPrecheck.WorkItemIdentity]) =
    (1 until (1 << items.size)).map(mask => items.zipWithIndex.collect {
      case (item, index) if (mask & (1 << index)) != 0 => item
    }).toVector

  "Purpose-specific handoff scope work" should {
    "request only distinct semantic gaps while leaving the complete candidate frontier untouched" in {
      Given("a retained checkpoint containing Built candidates and unresolved original transfer scopes")
      val saved = _checkpoint
      val original = saved.snapshot
      When("Workflow selects its separate scope task without reconstructing executable candidates")
      val request = take(S.requestC(saved))
      Then("only the actual unresolved transfers are selected and source closure plus every candidate remain")
      request.gaps.nonEmpty shouldBe true
      request.gaps.map(_.original.identity).distinct shouldBe request.gaps.map(_.original.identity)
      request.source shouldBe saved.snapshot.selection.proposal.source.reference
      request.checkpoint shouldBe saved.reference
      request.items.map(_.identity).toSet shouldBe request.gaps.flatMap(gap => gap.producingItems ++ gap.receivingItems).toSet
      request.gaps.map(_.original).foreach(handoff => saved.snapshot.selection.boundary.handoffs.handoffs should contain (handoff))
      saved.snapshot shouldBe original
      val frontier = saved.snapshot.projection.get.asInstanceOf[R.ProjectionView.Requirements]
      frontier.candidates.exists(_.isInstanceOf[R.CandidateProjectionView.BuiltView]) shouldBe true
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseManagedScopeSemantic.Source(null, null, null)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseScopeSemantic.Admitted(null, null)")
    }
    "admit exact semantic ranges without allowing ownership routing or partial DecisionNeeded enrichment" in {
      Given("the assigned scope request and host-owned result reference")
      val request = take(S.requestC(_checkpoint))
      val enriched = _judgment(request)
      When("the complete unknown-only judgment is returned")
      val actual = take(S.admitC(request, _reference(request), enriched))
      Then("the original task and semantic result are preserved separately from mechanical routing")
      actual.request shouldBe request
      actual.judgment shouldBe enriched
      val first = enriched.scopes.head
      val foreign = SplitPhaseExplicitProposalPrecheck.WorkItemIdentity("foreign")
      val invalid = Vector(enriched.copy(scopes = Vector.empty),
        enriched.copy(scopes = enriched.scopes.updated(0, first.copy(producingItems = Vector.empty))),
        enriched.copy(scopes = enriched.scopes.updated(0, first.copy(receivingItems = Vector(foreign)))),
        enriched.copy(scopes = enriched.scopes.updated(0, first.copy(producingItems = request.gaps.head.producingItems.reverse))),
        enriched.copy(scopes = enriched.scopes ++ enriched.scopes), enriched.copy(evidence = Vector.empty),
        enriched.copy(decision = S.Decision.DecisionNeeded))
      invalid.foreach(value => S.admitC(request, _reference(request), value).isSuccess shouldBe false)
      S.admitC(request, _reference(request).copy(identity = "worker-result"), enriched).isSuccess shouldBe false
      take(S.admitC(request, _reference(request), enriched.copy(decision = S.Decision.DecisionNeeded, scopes = Vector.empty)))
        .judgment.scopes shouldBe Vector.empty
    }
    "use actual generated scope declarations and typed purpose payloads without legacy aliases" in {
      Given("the current CML scope action request result and completion contracts")
      val required = take(SplitPhaseDefinitionBinding.scopeSemanticOperationC)
      val request = take(S.requestC(_checkpoint))
      val result = _judgment(request)
      When("application payloads are decoded as actual generated value classes")
      val requestjson = J.requestCodec.encode(request)
      val resultjson = J.resultCodec.encode(result)
      val generatedrequest = requestjson.hcursor.downField("payload").focus.get
        .as[org.simplemodeling.textus.smworkflow.value.SplitPhaseScopeSemanticRequest].toOption.get
      val generatedresult = resultjson.hcursor.downField("payload").focus.get
        .as[org.simplemodeling.textus.smworkflow.value.SplitPhaseScopeSemanticResult].toOption.get
      Then("the distinct SPI and generated fields match and unknown routing claims remain unused data")
      required.actionIdentity shouldBe "AssessHandoffScopes"
      required.inputType.map(_.value) shouldBe Some(J.requestCodec.typeIdentity)
      required.resultType.map(_.value) shouldBe Some(J.resultCodec.typeIdentity)
      generatedrequest.asJson shouldBe requestjson.hcursor.downField("payload").focus.get
      generatedresult.asJson shouldBe resultjson.hcursor.downField("payload").focus.get
      J.requestCodec.decode(requestjson).toOption shouldBe Some(request)
      J.resultCodec.decode(resultjson).toOption shouldBe Some(result)
      val extra = resultjson.hcursor.downField("payload").withFocus(_.mapObject(_.add("nextOperation", Json.fromString("apply")))).top.get
      J.resultCodec.decode(extra) shouldBe Right(result)
      J.requestCodec.decode(SplitPhaseExplicitPartitionSemanticJson.requestCodec.encode(null)).isLeft shouldBe true
    }

    "decode declared scope data without rerendering or granting semantic completion" in {
      Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R9; Example: E22; passive scope data and a semantically incomplete judgment")
      val request = take(S.requestC(_checkpoint))
      val judgment = _judgment(request)
      val incomplete = judgment.copy(scopes = Vector.empty, rationale = "")
      val requestwire = io.circe.parser.parse(J.requestCodec.encode(request).spaces2).toOption.get
      val resultwire = io.circe.parser.parse(J.resultCodec.encode(incomplete).spaces2).toOption.get

      When("ordinary declared decoding reads compatible presentation and incomplete semantic data")
      val decodedrequest = J.requestCodec.decode(requestwire)
      val decodedresult = J.resultCodec.decode(resultwire)
      val admission = S.admitC(request, _reference(request), incomplete)

      Then("typed data remains passive and only the actual semantic boundary refuses the incomplete task")
      decodedrequest shouldBe Right(request)
      decodedresult shouldBe Right(incomplete)
      admission.isSuccess shouldBe false
    }

    "reject missing consumed scope fields and wrong types at the input boundary" in {
      Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R9; Example: E23; all nested declared Scope request and result objects")
      val request = take(S.requestC(_checkpoint))
      val requestwire = J.requestCodec.encode(request)
      val resultwire = J.resultCodec.encode(_judgment(request))
      def _mutations_(value: Json): Vector[Json] = value.asObject match {
        case Some(fields) =>
          fields.keys.toVector.map(field => value.mapObject(_.remove(field))) ++
            fields.toVector.flatMap { (field, child) =>
              _mutations_(child).map(changed => value.mapObject(_.add(field, changed)))
            }
        case None => value.asArray.map(_.zipWithIndex.flatMap { (child, index) =>
          _mutations_(child).map(changed => value.mapArray(_.updated(index, changed)))
        }.toVector).getOrElse(Vector.empty)
      }
      val badrequests = _mutations_(requestwire) :+ requestwire.hcursor.downField("payload").downField("source")
        .downField("revision").withFocus(_ => Json.fromInt(1)).top.get
      val badresults = _mutations_(resultwire) ++ Vector(
        resultwire.hcursor.downField("payload").downField("decision").withFocus(_ => Json.fromString("enriched")).top.get,
        resultwire.hcursor.downField("schemaVersion").withFocus(_ => Json.fromString("foreign")).top.get,
        resultwire.hcursor.downField("payload").downField("evidence").withFocus(_ => Json.Null).top.get)

      When("the request and result readers consume each independently malformed nested input")
      val rejectedrequests = badrequests.map(J.requestCodec.decode)
      val rejectedresults = badresults.map(J.resultCodec.decode)

      Then("contradictory fields or types fail directly without another serializer or admission pass")
      rejectedrequests.nonEmpty shouldBe true
      rejectedresults.nonEmpty shouldBe true
      rejectedrequests.foreach(_.isLeft shouldBe true)
      rejectedresults.foreach(_.isLeft shouldBe true)
    }

    "E12 admit the assigned scope answer without context or checkpoint echoes" in {
      Given("Spec: docs/spec/session-workflow-context-resolution.md; Rule: R5; Example: E12; actual requested scope gaps and passive legacy result diagnostics")
      val request = take(S.requestC(_checkpoint))
      val judgment = _judgment(request)
      val wire = J.resultCodec.encode(judgment)
      val legacy = wire.hcursor.downField("payload").withFocus(_.mapObject(_.add("context", Json.fromString("obsolete"))
        .add("checkpoint", Json.Null).add("commandGrant", Json.True))).top.get

      When("the typed reader consumes the answer and scope admission applies it to the actual selected request")
      val decoded = J.resultCodec.decode(legacy).toOption.get
      val admitted = take(S.admitC(request, _reference(request), decoded))

      Then("legacy control copies are neither required nor compared and only the actual semantic ranges are admitted")
      wire.hcursor.downField("payload").focus.get.asObject.get.keys.toSet shouldBe
        Set("decision", "rationale", "scopes", "evidence")
      decoded shouldBe judgment
      admitted.request shouldBe request
      admitted.judgment shouldBe judgment
    }
  }

  "Shared scope interpretation bounds" should {
    "validate every nonempty ordered range combination without admitting a Human reference as the AI result" in {
      Given("the complete original scope request and all ordered producer/receiver subsets of its bounded fixture")
      val request = take(S.requestC(_checkpoint))
      val combinations = request.gaps.foldLeft(Vector(Vector.empty[S.Scope])) { (prior, gap) =>
        for {
          scopes <- prior
          producing <- _ordered_subsets(gap.producingItems)
          receiving <- _ordered_subsets(gap.receivingItems)
        } yield scopes :+ S.Scope(gap.original.identity, producing, receiving, "explicit complete interpretation")
      }
      val humanreference = ContextReference("human-resolution-data", "1")
      When("the shared range boundary and the distinct AI-result admission each interpret the same scope vectors")
      val actual = combinations.map { scopes =>
        val judgment = _judgment(request).copy(scopes = scopes)
        (S.inspectScopesC(request, scopes), S.admitC(request, _reference(request), judgment),
          S.admitC(request, humanreference, judgment))
      }
      Then("all bounded interpretations are valid data, while the Human reference supplies no AI completion authority")
      combinations.nonEmpty shouldBe true
      actual.foreach { (bounds, ai, human) =>
        bounds.isSuccess shouldBe true
        ai.isSuccess shouldBe true
        human.isSuccess shouldBe false
      }
    }

    "refuse partial foreign repeated reordered or absent ranges at the shared boundary" in {
      Given("one complete original request and a valid interpretation within its source ranges")
      val request = take(S.requestC(_checkpoint))
      val scopes = _judgment(request).scopes
      val first = scopes.head
      val foreign = SplitPhaseExplicitProposalPrecheck.WorkItemIdentity("foreign")
      val invalid = Vector(null, Vector.empty[S.Scope], scopes ++ scopes,
        scopes.updated(0, null),
        scopes.updated(0, first.copy(handoff = B.HandoffIdentity("foreign"))),
        scopes.updated(0, first.copy(producingItems = Vector.empty)),
        scopes.updated(0, first.copy(receivingItems = Vector(foreign))),
        scopes.updated(0, first.copy(producingItems = request.gaps.head.producingItems.reverse)),
        scopes.updated(0, first.copy(receivingItems = first.receivingItems ++ first.receivingItems)),
        scopes.updated(0, first.copy(rationale = " ")))
      When("the shared interpretation boundary consumes incomplete or contradictory scope data")
      val rejected = invalid.map(value => S.inspectScopesC(request, value))
      val missingrequest = S.inspectScopesC(null, scopes)
      Then("no invalid interpretation or missing request is admitted")
      rejected.foreach(_.isSuccess shouldBe false)
      missingrequest.isSuccess shouldBe false
    }
  }
}
