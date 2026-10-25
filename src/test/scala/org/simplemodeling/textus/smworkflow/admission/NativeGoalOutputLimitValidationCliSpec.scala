package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V

/** Actual installed native output-limit admission, separate from historical
  * qualification and from the real consumer Session.
  * @since Oct. 24, 2026
  * @version Oct. 25, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalOutputLimitValidationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeValidationRepairInstalledFixture.*
  "Installed actual output-limit validation" should afterWord("in spec:goal-phase-validation-repair-adoption, example:E1, rules:R1,R2,R6") {
    "retain the incomplete bytes across reopen and adopt one corrective Plan through ordinary local closure" in {
      val executable = path("smworkflow.spec.installedCommand")
      val installations = path("smworkflow.spec.runtimeRoot")
      val finishadopted = sys.props.get("smworkflow.validationRepair.stage").contains("finish-adopted")
      val (state, observation, business) = if (finishadopted || sys.props.contains("smworkflow.validationRepair.nativeExistingRoot")) {
        Given(if (finishadopted) "the retained incomplete invocation and actual adopted Direction with Step3 issued but uncompleted after the failed guard test"
          else "the retained isolated Session with its already executed whole Test, actual output and original caller policy")
        When("the normal codecs read that existing incomplete observation without replaying setup or Advance")
        existingNative(executable, finishAdopted = finishadopted)
      } else {
        Given("the selected installed candidate and an isolated normally accepted original Plan")
        val fresh = setup(executable, installations, "specification-fixture-caller", large = false)
        val root = Path.of(fresh.root)
        val cli = new Cli(root, executable, installations)
        executions(root).size shouldBe 2
        When("ordinary Advance commits the last accepted Step and executes the actual output-limited whole Test")
        val observed = cli.advance(fresh.sessionId)
        (fresh, observed.stdout, observed.business[V.GoalPhaseAdvanceResult])
      }
      val root = Path.of(state.root)
      Then("the incomplete Decision retains actual raw bytes and no complete successful result is invented")
      val wire = Files.readString(observation)
      wire should include ("incomplete")
      wire should include ("output-limit-exceeded")
      wire should include ("process_observation")
      wire should include ("truncated")
      wire should not include ("transaction-indeterminate")
      business.sessionId shouldBe state.sessionId
      executions(root).size shouldBe 3
      if (finishadopted) {
        When("a normal read confirms unchanged pending Step3 and only the remaining adopted correction proceeds")
        finishAdopted(state, executable, installations)
      } else {
        When("an independent fixture Human adopts the native source-specific correction and a separate Advance issues it")
        qualifyNative(state, executable, installations, business)
      }
      Then("the same Session closes after fresh checks and reviews with the original incomplete receipt unchanged")
      Files.isRegularFile(Path.of(state.taskRoot).resolve("native-output-limited-receipts.json")) shouldBe true
      Files.isRegularFile(Path.of(state.taskRoot).resolve("after-closure-native-snapshot.json")) shouldBe true
      executions(root).size shouldBe 5
    }
  }
}
