package org.simplemodeling.textus.smworkflow

import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Actual producer JVM remains separate from the Scala3 application classloader.
  * No SBT process or dependency publication is started by this specification.
  *
  * @since Oct.  4, 2026
  * @version Oct. 24, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class SplitPhaseCmlDerivationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _execute(argv: Vector[String], log: Path): String = {
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

  "Actual SplitPhase CML producer" should {
    "derive every entry and Human configuration without reopening preview or application" in {
      Given("the existing admitted Cozy Scala2.12 producer runtime, current CML and relocated producer probe")
      val classpathfile = Path.of(sys.props.getOrElse("sm-workflow.cozy-runtime-classpath",
        "/Users/asami/src/dev2025/cozy/target/cozy.d/runtime-classpath.txt"))
      val classpath = Files.readString(classpathfile).trim
      classpath should not be empty
      val root = Files.createDirectories(Path.of("target", "sm-workflow", "work", "cml-derivation-spec"))
      // Retain unique diagnostic outputs; project clean owns removal after child JVM exit.
      val work = Files.createTempDirectory(root, "producer-")
      val classes = Files.createDirectory(work.resolve("classes"))
      val javaexecutable = Path.of(sys.props("java.home"), "bin", "java").toString
      _execute(Vector(javaexecutable, "-cp", classpath, "scala.tools.nsc.Main", "-classpath", classpath,
        "-d", classes.toString, "src/test/probes/SplitPhaseCmlDerivationProbe.scala"), work.resolve("compile.log"))
      When("the actual producer normalizes the source and checks every reachable entry/Human pair")
      val output = _execute(Vector(javaexecutable, "-cp", classes.toString + java.io.File.pathSeparator + classpath,
        "cozy.modeler.SplitPhaseCmlDerivationProbe", "src/main/cozy/sm-workflow.cml"), work.resolve("probe.log"))
      Then("all150 normalized derivations are unique, including retained scope Human states, application intent and stopped/terminal dominance")
      output.linesIterator.exists(_ ==
        "accepted workflow=SplitPhaseWorkflow version=split-phase-business-operation-v14 pairs=150 terminalDominance=true") shouldBe true
    }
  }
}
