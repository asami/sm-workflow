package org.simplemodeling.textus.smworkflow

import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Owned producer JVM only; never starts SBT or mutates application sources.
 * @since   Oct.  6, 2026
 * @version Oct. 12, 2026
 */
final class GoalPhaseCmlDerivationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def execute(argv: Vector[String], log: Path): String = {
    val process = new ProcessBuilder(argv*).redirectErrorStream(true).redirectOutput(log.toFile).start()
    val completed = process.waitFor(60, TimeUnit.SECONDS)
    if (!completed) {
      process.destroyForcibly().waitFor()
      fail(s"Owned producer probe timed out; diagnostic output: $log")
    }
    val output = Files.readString(log)
    withClue(output) { process.exitValue() shouldBe 0 }
    output
  }
  "Actual GoalPhase CML producer" should {
    "derive each actual ordered frontier with only whole-Phase closure terminal" in {
      Given("the admitted Cozy producer runtime, current CML and independent combination probe")
      val cp = Files.readString(Path.of(sys.props.getOrElse("sm-workflow.cozy-runtime-classpath",
        "/Users/asami/src/dev2025/cozy/target/cozy.d/runtime-classpath.txt"))).trim
      cp should not be empty
      val parent = Files.createDirectories(Path.of("target/sm-workflow/work/goal-phase-cml-derivation"))
      val work = Files.createTempDirectory(parent, "producer-")
      val classes = Files.createDirectory(work.resolve("classes"))
      val javaExecutable = Path.of(sys.props("java.home"), "bin", "java").toString
      execute(Vector(javaExecutable, "-cp", cp, "scala.tools.nsc.Main", "-classpath", cp, "-d", classes.toString,
        "src/test/probes/GoalPhaseCmlDerivationProbe.scala"), work.resolve("compile.log"))
      When("the real producer derives every phase and retained ordered-Decision configuration")
      val output = execute(Vector(javaExecutable, "-cp", classes.toString + java.io.File.pathSeparator + cp,
        "cozy.modeler.GoalPhaseCmlDerivationProbe", "src/main/cozy/sm-workflow.cml"), work.resolve("probe.log"))
      Then("the13 configurations are unique and only whole-Phase closure derives PhaseClosed")
      output.linesIterator.exists(_ ==
        "accepted workflow=GoalPhaseWorkflow version=goal-phase-minimal-operation-v5 combinations=13 phaseClosure=1") shouldBe true
    }
  }
}
