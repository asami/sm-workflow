package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.validation.annotation.ValidationSpecification
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration
import RepositorySyncNativeMergeValidation as C
import RepositorySyncPostReviewValidation as P
import RepositorySyncProgramImpact as I
import SplitPhaseExplicitPartitionFixture.take
import WorkflowSbtValidation.Kind

/** Supplemental validation of tracked lint inputs after review.
  * @since   Nov. 20, 2026
  * @version Nov. 26, 2026
  */
@ValidationSpecification(declaration = classOf[LocalTestDeclaration])
final class RepositorySyncPostReviewValidationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _binding = P.Binding("cozy-lint-config-ignore", ".gitignore", Vector(".scalafix.conf"), "lint3")
  private val _old_checks = Vector(C.Check("compile1", 1, Kind.Compile), C.Check("test2", 2, Kind.Test))
  private val _fix_check = C.Check("lint3", 3, Kind.FixCheck)
  private val _uncovered = _binding.ignoreFile +: _binding.lintInputs
  private val _lint_coverage = I.Coverage(_fix_check, _binding.lintInputs, _binding.lintInputs,
    "FixCheck consumes the tracked Scalafix configuration")
  private val _impact = I.Policy(Vector(_lint_coverage), _old_checks :+ _fix_check, Vector.empty,
    "Keep the retained product checks and declare the lint input", Vector.empty, Some(_binding))
  private val _policy = C.Policy("repository-validation", 1, "required", _impact.reason,
    _old_checks :+ _fix_check, impact = Some(_impact))
  private val _plan = P.Plan(_binding, _fix_check)
  private val _exception = P.Ignore(".scalafix.conf", "included-by-exception", Some(".gitignore"),
    Some(6), Some("!/.scalafix.conf"))
  private val _git_receipt = P.GitReceipt("exited", Some(0), Vector(_exception))

  "Post-review validation" should {
    "check selection" which {
      "selects only the appended ordinary FixCheck for the declared changed inputs" in {
        Given("retained Compile and Test checks and an added FixCheck that consumes the tracked lint configuration")
        val policy = _policy

        When("the declared ignore file and tracked lint input both need coverage")
        val selected = take(P.selectC(policy, _old_checks, _uncovered))

        Then("the plan names only the additional FixCheck with its original identity and ordinal")
        selected shouldBe Some(_plan)
      }

      "requires both affected and consumed input coverage from the added check" in {
        Given("policies with absent or partial lint coverage")
        val candidates = Vector(
          Vector.empty,
          Vector(_lint_coverage.copy(affectedPaths = Vector(_binding.ignoreFile), inputPaths = _uncovered)),
          Vector(_lint_coverage.copy(inputPaths = Vector(_binding.ignoreFile)))
        ).map(coverage => _policy.copy(impact = Some(_impact.copy(coverage = coverage))))

        When("the supplemental check is selected under each policy")
        val selected = candidates.map(policy => take(P.selectC(policy, _old_checks, _uncovered)))

        Then("each selection is refused")
        selected shouldBe Vector(None, None, None)
      }

      "preserves the retained check identities, kinds, ordinals, and order" in {
        Given("policies that change a retained check or reorder the retained pair")
        val compile = _old_checks.head
        val test = _old_checks.last
        val orders = Vector(
          Vector(compile.copy(identity = "replacement-compile"), test, _fix_check),
          Vector(compile.copy(kind = Kind.Test), test, _fix_check),
          Vector(compile.copy(ordinal = 4), test, _fix_check),
          Vector(test, compile, _fix_check),
          Vector(compile, test.copy(testScope = Some("admission")), _fix_check)
        )

        When("the modified check sequences are compared with the retained sequence")
        val selected = orders.map(checks => take(P.selectC(_policy.copy(checks = checks), _old_checks, _uncovered)))

        Then("every changed sequence is refused")
        selected shouldBe Vector.fill(orders.size)(None)
      }

      "refuses an unexpected uncovered input or an invalid fixed selection" in {
        Given("a selection with an extra uncovered path, a malformed selection ID, or a different check identity")
        val malformed = _policy.copy(impact = Some(_impact.copy(postReviewValidation =
          Some(_binding.copy(ignoreSelection = "cozy lint config ignore")))))
        val mismatched = _policy.copy(checks = _old_checks :+ _fix_check.copy(identity = "other-lint"))

        When("the configured supplemental plan is selected")
        val extra = take(P.selectC(_policy, _old_checks, _uncovered :+ "unexpected.conf"))
        val invalid = take(P.selectC(malformed, _old_checks, _uncovered))
        val wrongcheck = take(P.selectC(mismatched, _old_checks, _uncovered))

        Then("none of these selections yields a plan")
        Vector(extra, invalid, wrongcheck) shouldBe Vector(None, None, None)
      }
    }

    "Git ignore binding" which {
      "requires one declaration for the exact workspace, operation, selection ID, and paths" in {
        Given("a typed check-ignore declaration for the repository workspace and tracked lint configuration")
        val workspace = "sm-workflow-repository"
        val row = Json.obj(
          "target" -> Json.fromString(workspace),
          "operation" -> Json.fromString("check-ignore"),
          "selectionId" -> Json.fromString(_binding.ignoreSelection),
          "selectedPaths" -> _binding.lintInputs.asJson
        )
        def _process_(rows: Json*): Json = Json.obj("gitPrograms" -> Json.arr(rows*))
        val crossed = row.mapObject(_.add("selectedPaths", Vector(_binding.ignoreFile).asJson))
        val invalid = Vector(
          "absent" -> _process_(),
          "wrong workspace" -> _process_(row.mapObject(_.add("target", Json.fromString("another-workspace")))),
          "wrong operation" -> _process_(row.mapObject(_.add("operation", Json.fromString("status")))),
          "wrong selection ID" -> _process_(row.mapObject(_.add("selectionId", Json.fromString("another-selection")))),
          "crossed path" -> _process_(crossed),
          "extra path" -> _process_(row.mapObject(_.add("selectedPaths", _uncovered.asJson))),
          "duplicate path" -> _process_(row.mapObject(_.add("selectedPaths", (_binding.lintInputs ++ _binding.lintInputs).asJson))),
          "duplicate declaration" -> _process_(row, row),
          "conflicting declaration" -> _process_(row, crossed)
        )

        When("the process declarations are matched against the configured binding")
        val matched = P.gitBindingMatches(_process_(row), workspace, _binding)
        val unexpectedlymatched = invalid.collect {
          case (label, process) if P.gitBindingMatches(process, workspace, _binding) => label
        }

        Then("the exact declaration matches and each missing, crossed, or ambiguous declaration fails")
        matched shouldBe true
        unexpectedlymatched shouldBe empty
      }

      "preserves the declared order of selected paths" in {
        Given("a selection with two tracked lint inputs")
        val workspace = "sm-workflow-repository"
        val binding = _binding.copy(lintInputs = Vector(".scalafix.conf", "lint/scalafix.conf"))
        def _process_(paths: Vector[String]): Json = Json.obj("gitPrograms" -> Json.arr(Json.obj(
          "target" -> Json.fromString(workspace),
          "operation" -> Json.fromString("check-ignore"),
          "selectionId" -> Json.fromString(binding.ignoreSelection),
          "selectedPaths" -> paths.asJson
        )))

        When("the declaration lists the same inputs in the original or reversed order")
        val ordered = P.gitBindingMatches(_process_(binding.lintInputs), workspace, binding)
        val reversed = P.gitBindingMatches(_process_(binding.lintInputs.reverse), workspace, binding)

        Then("the original declaration matches and the reordering fails")
        ordered shouldBe true
        reversed shouldBe false
      }
    }

    "Git ignore receipts" which {
      "require a completed inclusion-by-exception row for the exact tracked lint path" in {
        Given("a terminal receipt for the declared ignore file's negation of the tracked Scalafix configuration")
        val valid = _git_receipt
        val invalid = Vector(
          _exception.copy(disposition = "excluded", line = Some(5), pattern = Some("/.scalafix.conf")),
          _exception.copy(disposition = "no-rule", source = None, line = None, pattern = None),
          _exception.copy(source = Some(".git/info/exclude")),
          _exception.copy(line = None),
          _exception.copy(line = Some(0)),
          _exception.copy(pattern = None),
          _exception.copy(pattern = Some("/.scalafix.conf")),
          _exception.copy(pattern = Some("!")),
          _exception.copy(path = ".gitignore"),
          _exception.copy(path = "*.conf")
        ).map(observation => valid.copy(observations = Vector(observation)))

        When("the receipts are checked against the exact supplemental plan")
        val accepted = valid.accepted(_plan)
        val rejected = invalid.filter(_.accepted(_plan))

        Then("only the complete observation of the declared leaf and negation is accepted")
        accepted shouldBe true
        rejected shouldBe empty
      }

      "refuse unconfirmed, partial, and duplicate observations" in {
        Given("receipts that lack confirmed completion or an exact observation vector")
        val receipts = Vector(
          _git_receipt.copy(termination = "termination-unconfirmed", exitCode = None),
          _git_receipt.copy(exitCode = None),
          _git_receipt.copy(exitCode = Some(1)),
          _git_receipt.copy(exitCode = Some(2)),
          _git_receipt.copy(observations = Vector.empty),
          _git_receipt.copy(observations = Vector(_exception, _exception))
        )

        When("the receipts are checked against the supplemental plan")
        val accepted = receipts.filter(_.accepted(_plan))

        Then("none satisfies the declared observation")
        accepted shouldBe empty
      }
    }

    "program-input configuration" which {
      "preserves old JSON that omits the supplemental binding and round-trips the new field" in {
        Given("a program-input configuration written before the optional binding was introduced")
        val oldwire = Json.obj(
          "coverage" -> Vector(_lint_coverage).asJson,
          "fullChecks" -> (_old_checks :+ _fix_check).asJson,
          "fullPaths" -> Json.arr(),
          "reason" -> Json.fromString(_impact.reason),
          "priorEvidence" -> Json.arr()
        )

        When("the policy is decoded and encoded with and without a supplemental binding")
        val legacy = take(I.decodeC(oldwire))
        val legacywire = legacy.asJson
        val updatedwire = legacy.copy(postReviewValidation = Some(_binding)).asJson
        val updated = take(I.decodeC(updatedwire))

        Then("the old wire form stays intact and the new wire form retains the exact binding")
        legacy.postReviewValidation shouldBe None
        legacywire shouldBe oldwire
        updatedwire.hcursor.get[P.Binding]("postReviewValidation") shouldBe Right(_binding)
        updated shouldBe _impact
      }

      "allows the bound supplemental FixCheck to cover only its lint configuration" in {
        Given("retained Compile and Test checks that cover the full source scope and an added lint-only coverage row")
        val fullpaths = Vector("src")
        val oldcoverage = _old_checks.map(check => I.Coverage(check, fullpaths, fullpaths, "Consumes the full source scope"))
        val policy = _impact.copy(coverage = oldcoverage :+ _lint_coverage, fullPaths = fullpaths)

        When("program-input configurations with and without the supplemental binding are decoded")
        val bound = I.decodeC(policy.asJson)
        val unbound = I.decodeC(policy.copy(postReviewValidation = None).asJson)

        Then("the supplemental binding admits the narrow row while the unbound policy requires full source coverage")
        take(bound) shouldBe policy
        unbound.isSuccess shouldBe false
      }
    }
  }
}
