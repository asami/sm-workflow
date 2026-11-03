package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import RepositorySyncReviewInputContinuation as R
import RepositorySyncValidationContinuation as N
import RepositorySyncNativeMergeValidation as C
import RepositorySyncNativeMergeReview as M
import RepositorySyncDocumentInputs as D
import RepositorySyncProgramImpact as P
import RepositorySyncNativeProgressStore as S
import RepositorySyncManagedGitObservation as G
import SplitPhaseExplicitPartitionFixture.take

/** Pure specification facts only: none of these values proves native effects,
  * genuine successful processes, independent live judgment or stored history.
  * The installed two-stage specification supplies actual lifecycle evidence.
  */
private[admission] object RepositorySyncReviewInputContinuationFixture {
  val checks = Vector(C.Check("compile1", 1, WorkflowSbtValidation.Kind.Compile),
    C.Check("test2", 2, WorkflowSbtValidation.Kind.Test), C.Check("lint3", 3, WorkflowSbtValidation.Kind.FixCheck))
  val paths = Vector("README.md", "docs/guide.md", "scripts/probe.py", "skills/probe/SKILL.md", "src/Input.scala", "value.txt")
  val scopes = Vector("scripts", "skills", "src", "value.txt")
  val original = D.Assessment(paths, Vector("src/Input.scala", "value.txt"),
    Vector("README.md", "scripts/probe.py", "skills/probe/SKILL.md"), Vector.empty, "Pure original host mapping")
  val proposed = D.Assessment(paths, Vector("scripts/probe.py", "skills/probe/SKILL.md", "src/Input.scala", "value.txt"),
    Vector.empty, Vector.empty, "Pure corrected host mapping")
  val mapping = D.Policy(Vector("README.md", "docs"), scopes, "Explicit pure fixture documentation and consumption")
  val impact = P.Policy(checks.map(check => P.Coverage(check, scopes, scopes, "Pure existing consumed input coverage")),
    checks, Vector("src", "value.txt"), "Actual-shaped source delta requires existing full checks", Vector.empty)
  val validation = C.Policy("review-input-fixture", 1, "required", "Pure retained ordered selection", checks, impact = Some(impact))
  val context = RepositorySyncManagedFetch.StartContext("selection", "invocation", "participant", "repository",
    "main", "origin", "main", "owned-upstream", "FULL_STATE")
  val observed = G.Observed("main", "a" * 40, "d" * 40,
    Vector(G.DirtyPath('M', ' ', "src/Input.scala", None)), Some("b" * 40), Some(G.Counts(1, 1)), Some("c" * 40))
  val reference = ContextReference("fixture-independent-classification-direction", "1")
  val answer = N.Answer("AdoptInputClassificationAndContinueReview", "Pure fixture classification direction", false, false)
  val target = R.Target("session", context, "component", "/specification/repository", "/specification/upstream.git",
    "20", "fixture-original-review-result", "",
    R.Candidate(R.NativeReference(ContextReference("session:native-pending-merge", "1"), "6"), observed, observed,
      observed.tree, "b" * 40, "merge", ""),
    R.NativeReference(ContextReference("session:native-merge-validated", "1"), "17"),
    R.Review("session:native-review:17", ContextReference("fixture-original-review-result", "1"), "20", "decision-needed"),
    original, Some(mapping), proposed, validation, checks, observed, observed.tree, Vector.empty, R.policy)
  val receipts = (Vector("index-local", "index-remote") ++ checks.map(_.identity)).zipWithIndex.map { (identity, index) =>
    C.Receipt(identity, "exited", Some(0), "", "", checks.lift(index - 2).map(check =>
      WorkflowSbtValidation.Facts(check.kind, WorkflowSbtValidation.Outcome.Succeeded, None, None)))
  }
  val request = M.Request("session", "17", observed.head, "b" * 40, observed.tree, paths,
    "complete local candidate diff\n", "complete remote candidate diff\n", "required", "Pure selected checks", checks.map(_.identity), receipts,
    uncertainties = original.unknownPaths.map("Unexamined unknown input: " + _))
  def binding(selected: R.Target = target, component: String = "component", participant: String = "participant",
    direction: String = answer.choice, ended: Boolean = false, accepted: Boolean = false,
    ref: ContextReference = reference) = R.bindingC(component, selected, direction, answer.rationale, participant, ref, ended, accepted)
}

/** Strict v3 classification continuation, preserved historical codecs and real
  * production eligibility predicates. Native process evidence is separate.
  * @since Nov. 3, 2026
  * @version Nov. 3, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncReviewInputContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import RepositorySyncReviewInputContinuationFixture.*

  "RRI-02 explicit versioned continuation contracts" should {
    "select v3 explicitly while preserving the original v1 and required-lint v2 branches and their boolean meanings" in {
      Given("distinct pure v1 resultless intent v2 missing lint and v3 retained decision-needed targets")
      val old = RepositorySyncValidationContinuationFixture.target
      val lint = RepositorySyncRequiredLintContinuationFixture.target
      val oldanswer = N.Answer("AuthorizeNewValidation", "Original terminal uncertainty direction", true, true)
      val lintanswer = RepositorySyncRequiredLintContinuationFixture.answer
      When("the public facade dispatches each target by its explicit continuation policy")
      val current = N.decodeAnyTarget(target.asJson).toOption.get
      val previous = N.decodeAnyTarget(old.asJson).toOption.get
      val required = N.decodeAnyTarget(lint.asJson).toOption.get
      Then("v3 has its own typed branch and cannot silently fall back to either historical contract")
      current shouldBe N.ReviewInputsTarget(target)
      previous shouldBe N.OriginalTarget(old)
      required shouldBe N.RequiredLintTarget(lint)
      N.decodeAnyAnswer(current, answer.asJson) shouldBe Right(answer)
      N.decodeAnyAnswer(previous, oldanswer.asJson) shouldBe Right(oldanswer)
      N.decodeAnyAnswer(required, lintanswer.asJson) shouldBe Right(lintanswer)
      N.decodeAnyAnswer(current, oldanswer.asJson).isLeft shouldBe true
      N.decodeAnyAnswer(current, lintanswer.asJson).isLeft shouldBe true
      N.decodeAnyAnswer(previous, answer.asJson).isLeft shouldBe true
      N.decodeAnyAnswer(required, answer.asJson).isLeft shouldBe true
      N.decodeTarget(old.asJson) shouldBe Right(old)
      RepositorySyncRequiredLintContinuation.decodeTarget(lint.asJson) shouldBe Right(lint)
      Vector(N.policy, RepositorySyncRequiredLintContinuation.policy, "unknown-policy").foreach { discriminator =>
        N.decodeAnyTarget(target.asJson.mapObject(_.add("continuationPolicy", discriminator.asJson))).isLeft shouldBe true
      }
    }

    "normalize real public Record snake case keys without accepting conflicting duplicate fields" in {
      Given("a canonical pure v3 target in the generated description DTO and a generated false false answer DTO")
      val canonicaltarget = parse(take(R.targetRecord(target).toJsonStringC)).toOption.get
      val description = org.simplemodeling.textus.smworkflow.value.RepositorySyncValidationContinuationDescriptionResult(
        target.sessionId, "adoption-required", R.message, target.componentInstance, R.targetRecord(target), R.choices, N.adoptionOperation)
      val answerdto = org.simplemodeling.textus.smworkflow.value.RepositorySyncValidationContinuationAnswer(
        answer.choice, answer.rationale, answer.priorExecutionEnded, answer.acceptOriginalUncertainty)
      When("the normal runtime response context renders the DTO Records and public codecs normalize their snake case transport")
      val rendered = org.goldenport.cncf.context.RuntimeContext.Context.default.transformRecord(description.toRecord())
      val publictarget = parse(take(rendered.toJsonStringC)).toOption.get.hcursor.get[Json]("target").toOption.get
      val publicanswer = parse(take(org.goldenport.cncf.context.RuntimeContext.Context.default
        .transformRecord(answerdto.toRecord()).toJsonStringC)).toOption.get
      val restored = R.decodePublicTarget(publictarget)
      val restoredanswer = R.decodePublicAnswer(publicanswer)
      Then("the same exact contract returns through the public facade and preserves false false lifecycle flags")
      canonicaltarget.hcursor.get[String]("continuationPolicy") shouldBe Right(R.policy)
      R.decodeTarget(canonicaltarget) shouldBe Right(target)
      publictarget.hcursor.get[String]("continuation_policy") shouldBe Right(R.policy)
      publicanswer.hcursor.get[Boolean]("prior_execution_ended") shouldBe Right(false)
      publicanswer.hcursor.get[Boolean]("accept_original_uncertainty") shouldBe Right(false)
      restored shouldBe Right(target)
      restoredanswer shouldBe Right(answer)
      N.anyTargetC(R.targetRecord(target)).toOption shouldBe Some(N.ReviewInputsTarget(target))
      R.decodeTarget(publictarget).isLeft shouldBe true
      R.decodePublicTarget(publictarget.mapObject(_.add("continuationPolicy", "foreign".asJson))).isLeft shouldBe true
      R.decodePublicAnswer(publicanswer.mapObject(_.add("priorExecutionEnded", Json.True))).isLeft shouldBe true
    }

    "refuse every missing null extra malformed or foreign target answer and persisted direction field" in {
      Given("one canonical v3 target false false answer and adopting direction containing pure native-shaped references")
      val wire = target.asJson
      val answerwire = answer.asJson
      val direction = R.Direction(target, answer.choice, answer.rationale, context.participantIdentity, reference, false, false)
      val directionwire = direction.asJson
      def _corrupt_(value: Json): Vector[Json] = value.asObject.get.keys.toVector.flatMap(key =>
        Vector(value.mapObject(_.remove(key)), value.mapObject(_.add(key, Json.Null)))) :+
        value.mapObject(_.add("unobservedAuthority", Json.True))
      When("required top-level or nested native identity fields are missing null extended or crossed")
      val badtargets = _corrupt_(wire) ++ Vector("candidate", "validated", "review", "context", "originalInputs", "proposedInputs")
        .flatMap(key => _corrupt_(wire.hcursor.get[Json](key).toOption.get).map(value => wire.mapObject(_.add(key, value))))
      val invalididentities = Vector(target.copy(sessionId = "foreign-session"), target.copy(componentInstance = ""),
        target.copy(frontierRevision = "020"), target.copy(frontierRevision = "19"), target.copy(frontierCause = "foreign-result"),
        target.copy(review = target.review.copy(continuationId = "foreign-review")),
        target.copy(review = target.review.copy(result = ContextReference("foreign-result", "1"))),
        target.copy(review = target.review.copy(decision = "accept")),
        target.copy(validated = target.validated.copy(reference = ContextReference("foreign-source", "1"))),
        target.copy(candidate = target.candidate.copy(pending = target.candidate.pending.copy(nativeRevision = "21"))),
        target.copy(indexTree = "e" * 40))
      Then("strict decoders reject all malformed meanings and persisted Defer or lifecycle assertions")
      R.decodeTarget(wire) shouldBe Right(target)
      R.decodeAnswer(answerwire) shouldBe Right(answer)
      directionwire.as[R.Direction] shouldBe Right(direction)
      badtargets.foreach(value => R.decodeTarget(value).isLeft shouldBe true)
      invalididentities.foreach(value => R.decodeTarget(value.asJson).isLeft shouldBe true)
      _corrupt_(answerwire).foreach(value => R.decodeAnswer(value).isLeft shouldBe true)
      _corrupt_(directionwire).foreach(value => value.as[R.Direction].isLeft shouldBe true)
      Vector(answer.copy(choice = "AuthorizeNewValidation"), answer.copy(priorExecutionEnded = true),
        answer.copy(acceptOriginalUncertainty = true), answer.copy(rationale = answer.rationale + "\n"))
        .foreach(value => R.decodeAnswer(value.asJson).isLeft shouldBe true)
      direction.copy(choice = "Defer").asJson.as[R.Direction].isLeft shouldBe true
      direction.copy(participant = "foreign").asJson.as[R.Direction].isLeft shouldBe true
      direction.copy(priorExecutionEnded = true).asJson.as[R.Direction].isLeft shouldBe true
      direction.copy(acceptOriginalUncertainty = true).asJson.as[R.Direction].isLeft shouldBe true
    }

    "bind only the installed component actual participant exact choice and reference with false false flags" in {
      Given("one eligible pure target and its independent direction reference")
      When("binding is requested for the exact or crossed component participant choice reference and lifecycle claims")
      val exact = take(binding())
      val bad = Vector(binding(component = "foreign"), binding(participant = "foreign"), binding(direction = "accept"),
        binding(ended = true), binding(accepted = true), binding(ref = ContextReference("", "1")))
      Then("only the explicit binding passes and the public selector retains its exact independent answer")
      N.selectBinding(exact) shouldBe Right(exact)
      bad.foreach(_.isSuccess shouldBe false)
      exact.hcursor.downField("repositoryValidationContinuation").downField("answer").get[ContextReference]("reference") shouldBe Right(reference)
      val crossed = exact.mapObject(_.add("componentInstance", "foreign".asJson))
      N.selectBinding(crossed).isLeft shouldBe true
      val changedreference = exact.mapObject(fields => fields.add("repositoryValidationContinuation",
        fields("repositoryValidationContinuation").get.mapObject(inner => inner.add("answer", inner("answer").get
          .mapObject(_.add("reference", ContextReference("changed-independent-reference", "2").asJson))))))
      N.selectBinding(changedreference).toOption should not be Some(exact)
      R.decodeAnswer(answer.copy(choice = "Defer").asJson).isRight shouldBe true
    }
  }

  "RRI-02 actual production input qualification" should {
    "refuse CB-RRI-01 current mode check effect order and consumed coverage drift while allowing unrelated metadata" in {
      Given("pure retained validation state and an adopted target with the original selected check objects and consumed scopes")
      val retained = C.State("required", "Pure original retained selection", checks, target.candidate.pending.nativeRevision,
        receipts, Some(original), Some(P.select(impact, original)))
      def _changed_checks_(selected: Vector[C.Check]): C.Policy = validation.copy(checks = selected,
        impact = Some(impact.copy(fullChecks = selected, coverage = impact.coverage.zip(selected).map {
          (coverage, check) => coverage.copy(check = check)
        })))
      val kind = checks.updated(0, checks.head.copy(kind = WorkflowSbtValidation.Kind.Test))
      val ordinal = checks.updated(0, checks.head.copy(ordinal = 4))
      val consumedcoverage = validation.copy(impact = Some(impact.copy(coverage = impact.coverage.map(coverage =>
        coverage.copy(inputPaths = coverage.inputPaths :+ "another-consumed-input")))))
      val affectedcoverage = validation.copy(impact = Some(impact.copy(coverage = impact.coverage.map(coverage =>
        coverage.copy(affectedPaths = coverage.affectedPaths :+ "another-consumed-input",
          inputPaths = coverage.inputPaths :+ "another-consumed-input")))))
      val metadata = validation.copy(identity = "current-metadata", revision = 2, reason = "Updated explanation only",
        impact = Some(impact.copy(reason = "Updated impact explanation only", coverage = impact.coverage.map(coverage =>
          coverage.copy(reason = "Updated coverage explanation only")))))
      When("the actual production current-applicability helper compares current effects and scopes against retained validation")
      val exact = R.currentApplicabilityIssues(retained, target, validation, proposed)
      val refusals = Vector(validation.copy(mode = "explicit-no-product-tests", checks = Vector.empty),
        _changed_checks_(kind), _changed_checks_(ordinal), _changed_checks_(checks.reverse),
        consumedcoverage, affectedcoverage).map(current => R.currentApplicabilityIssues(retained, target, current, proposed))
      val retaineddrift = R.currentApplicabilityIssues(retained.copy(selected = checks.reverse), target, validation, proposed)
      val metadataonly = R.currentApplicabilityIssues(retained, target, metadata, proposed.copy(reason = "Updated classification explanation only"))
      Then("every effect or consumed-scope drift exposes issues without new receipts while inert metadata leaves applicability intact")
      exact shouldBe empty
      refusals.foreach(_ should not be empty)
      retaineddrift should not be empty
      metadataonly shouldBe empty
      retained.receipts shouldBe receipts
      retained.selected shouldBe checks
    }

    "classify an old unknown README as documentation only outside every selected consumed input scope" in {
      Given("old unknown README script and skill paths and unchanged selected program coverage")
      When("the production eligibility function compares exact original and proposed assessments")
      val qualified = R.eligibilityIssues(original, proposed, validation, checks)
      val coveredreadme = impact.copy(coverage = impact.coverage.map(value => value.copy(inputPaths = value.inputPaths :+ "README.md")))
      val refused = R.eligibilityIssues(original, proposed, validation.copy(impact = Some(coveredreadme)), checks)
      Then("README documentation passes only when selected inputPaths do not consume it")
      qualified shouldBe empty
      refused.exists(_.contains("README.md")) shouldBe true
      P.select(impact, proposed).checks shouldBe checks
      target.originalInputs shouldBe original
      target.proposedInputs shouldBe proposed
    }

    "retain script and skill consumption regardless of md suffix and refuse all previously known demotions" in {
      Given("old unknown scripts and SKILL.md under real selected program input scopes plus already known consumed and document paths")
      val demoted = proposed.copy(consumedPaths = proposed.consumedPaths.filterNot(path =>
        path.startsWith("scripts/") || path.startsWith("skills/")))
      val oldconsumed = proposed.copy(consumedPaths = proposed.consumedPaths.filterNot(_ == "src/Input.scala"))
      val olddocument = proposed.copy(consumedPaths = (proposed.consumedPaths :+ "docs/guide.md").sorted)
      When("the production predicate compares each attempted mapping with actual selected coverage")
      val scriptissues = R.eligibilityIssues(original, demoted, validation, checks)
      val sourceissues = R.eligibilityIssues(original, oldconsumed, validation, checks)
      val documentissues = R.eligibilityIssues(original, olddocument, validation, checks)
      Then("script and skill demotion expose exact issues and no known input changes meaning")
      Vector("scripts/probe.py", "skills/probe/SKILL.md").foreach(path => scriptissues.exists(_.contains(path)) shouldBe true)
      sourceissues.exists(_.contains("src/Input.scala")) shouldBe true
      documentissues.exists(_.contains("docs/guide.md")) shouldBe true
      R.eligibilityIssues(original, proposed, validation, checks) shouldBe empty
    }

    "expose changed binary unavailable unknown missing coverage and selection drift as ineligible without new success" in {
      Given("one eligible pure old unknown classification and complete original-shaped successful receipts")
      val missingcoverage = impact.copy(coverage = impact.coverage.map(value => value.copy(
        affectedPaths = Vector("src", "value.txt"), inputPaths = Vector("src", "value.txt"))))
      val cases = Vector(
        R.eligibilityIssues(original, proposed.copy(changedPaths = proposed.changedPaths :+ "z-new.txt"), validation, checks),
        R.eligibilityIssues(original, proposed.copy(binaryPaths = Vector("scripts/probe.py")), validation, checks),
        R.eligibilityIssues(original.copy(binaryPaths = Vector("scripts/probe.py")), proposed.copy(binaryPaths = Vector("scripts/probe.py")), validation, checks),
        R.eligibilityIssues(original, proposed.copy(unknownPaths = Vector("README.md")), validation, checks),
        R.eligibilityIssues(original, proposed, validation.copy(impact = Some(missingcoverage)), checks),
        R.eligibilityIssues(original, proposed, validation.copy(impact = None), checks),
        R.eligibilityIssues(original, proposed, validation.copy(checks = checks.dropRight(1)), checks),
        R.eligibilityIssues(original, proposed, validation, checks.reverse))
      When("those exact issue vectors are exposed in passive ineligible targets")
      val ineligible = cases.map(issues => target.copy(applicabilityIssues = issues))
      Then("only Defer remains available while adoption fails and the original receipt objects are retained")
      cases.foreach(_ should not be empty)
      cases(1).mkString should include("binary")
      cases(3).mkString should include("Unknown or unavailable")
      cases(4).mkString should include("coverage")
      ineligible.foreach { selected =>
        R.availableChoices(selected) shouldBe Vector("Defer")
        binding(selected = selected).isSuccess shouldBe false
        binding(selected = selected, direction = "Defer").isSuccess shouldBe true
      }
      request.checks shouldBe receipts
      request.checks.zip(receipts).foreach { (retained, originalreceipt) => (retained eq originalreceipt) shouldBe true }
      request.selectedChecks shouldBe checks.map(_.identity)
      target.asJson.noSpaces should not include "stdout"
      target.asJson.noSpaces should not include "stderr"
      target.asJson.noSpaces should not include "complete local candidate diff"
    }
  }

  "RRI-02 historical progress and independent review compatibility" should {
    "omit absent reviewInputs from old progress canonical wire without manufacturing an adoption or successful check" in {
      Given("the exact old v3 Integrated wire predating retained review input direction")
      val old = Json.obj("schemaVersion" -> S.schema.asJson, "payload" -> Json.obj(
        "stage" -> "Integrated".asJson, "root" -> target.root.asJson, "before" -> observed.asJson,
        "indexTree" -> Json.Null, "checkpoint" -> Json.Null, "after" -> Some(observed).asJson,
        "method" -> "local-ahead".asJson, "mergeHead" -> Json.Null, "validation" -> Json.Null, "finalTip" -> Json.Null))
      When("the current progress codec reads and writes the old historical payload")
      val restored = S.codec.decode(old).toOption.get
      Then("the complete old bytes remain canonical and no direction validation or semantic acceptance appears")
      S.codec.encode(restored) shouldBe old
      restored.reviewInputs shouldBe None
      restored.validation shouldBe None
      S.codec.encode(restored).hcursor.downField("payload").downField("reviewInputs").focus shouldBe None
    }

    "retain original request result codecs and refuse blind accept while any Unexamined input remains" in {
      Given("an original detailed review with complete candidate diffs successful-shaped checks and unresolved unknown inputs")
      val oldrequest = M.inputCodec.encode(request)
      val decision = M.Result("decision-needed", "Original partial observations accept no path; classification and semantic judgment remain unresolved", Vector.empty)
      val oldresult = Json.obj("schemaVersion" -> "sm-workflow.repository-native-merge-review-result.v1".asJson,
        "payload" -> Json.obj("decision" -> decision.decision.asJson, "rationale" -> decision.rationale.asJson, "findings" -> Json.arr()))
      val accept = M.Result("accept", "A blind acceptance cannot erase unknown input", Vector.empty)
      val policy = M.Policy("independent-fixture-review", 1)
      When("the unchanged old codecs roundtrip the issue and result and production admission examines blind acceptance")
      val decodedrequest = M.inputCodec.decode(oldrequest)
      val decodedresult = M.resultCodec.decode(oldresult)
      val blind = M.admitC(request, accept, policy)
      Then("decision-needed remains decision-needed and successful checks create no old accepted semantic coverage")
      decodedrequest shouldBe Right(request)
      decodedrequest.map(M.inputCodec.encode) shouldBe Right(oldrequest)
      decodedresult shouldBe Right(decision)
      M.resultCodec.encode(decision) shouldBe oldresult
      oldrequest.hcursor.downField("payload").downField("followUp").focus shouldBe None
      oldrequest.hcursor.downField("payload").downField("judgmentMode").focus shouldBe None
      blind.isSuccess shouldBe false
      M.admitC(request, decision, policy).isSuccess shouldBe true
      request.changedPaths shouldBe paths
      request.localDiff shouldBe "complete local candidate diff\n"
      request.remoteDiff shouldBe "complete remote candidate diff\n"
      request.checks shouldBe receipts
    }
  }
}
