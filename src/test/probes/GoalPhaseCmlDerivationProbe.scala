package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model}

/** Exercise the real Scala2.12 producer rather than reconstructing CML rules. */
object GoalPhaseCmlDerivationProbe {
  def main(args: Array[String]): Unit = {
    require(args.length == 1, "Expected one actual CML source")
    // Given the current application source and its actual producer model.
    val model = Model.parse(KaleidoxConfig.default,
      new String(Files.readAllBytes(Paths.get(args(0))), StandardCharsets.UTF_8))
    require(model.errors.isEmpty, model.errors.mkString(" | "))
    val selected = CompositeStateMachineCml.workflowDefinitions(model).filter(_.identity == "GoalPhaseWorkflow")
    require(selected.size == 1, "Expected one GoalPhase Workflow")
    val workflow = selected.head
    require(workflow.version == "goal-phase-ordered-run-v4", workflow.version)
    val phases = Vector("Idle", "Implementation", "InspectCandidate", "Validation", "Review", "Repair", "Closing",
      "PhaseInspection", "PhaseValidation", "PhaseReview", "PhaseClosing", "Decision", "Closed")
    val named = Map("Implementation" -> "OrderedImplementation", "InspectCandidate" -> "OrderedInspectCandidate",
      "Validation" -> "OrderedValidation", "Review" -> "OrderedReview", "Repair" -> "OrderedRepair",
      "Closing" -> "OrderedClosing", "PhaseInspection" -> "PhaseInspection", "PhaseValidation" -> "PhaseValidation",
      "PhaseReview" -> "PhaseReview", "PhaseClosing" -> "PhaseClosing", "Decision" -> "PhaseDecision", "Closed" -> "PhaseClosed")
    // When all reachable combinations are derived from normalized producer output.
    val actual = for (step <- Vector("Awaiting", "Closed"); human <- Vector("Idle", "Awaiting", "Answered"); phase <- phases) yield {
      val configuration = Map("step" -> step, "human" -> human, "phase" -> phase,
        "orderedDecision" -> "Retained")
      val states = workflow.compositeStateMachine.derivations
        .filter(_.configuration.bindings.map(b => b.role -> b.state).toMap == configuration).map(_.state)
      val expected = if (phase == "Idle") {
        if (step == "Closed") "Closed"
        else Map("Idle" -> "Pending", "Awaiting" -> "HumanPending", "Answered" -> "HumanAnswered")(human)
      } else if (step == "Awaiting" && human == "Idle") named(phase) else "PhaseDecision"
      (step, human, phase, states, expected)
    }
    // Then legacy closure never substitutes for Phase closure, and mixed axes stay nonterminal.
    require(actual.size == 78, "Expected all78 reachable combinations")
    actual.foreach { case (step, human, phase, states, expected) =>
      require(states == Vector(expected), s"$step/$human/$phase: $states != $expected")
    }
    require(actual.count(_._4 == Vector("PhaseClosed")) == 1, "Only the supported whole-Phase closure derives PhaseClosed")
    println("accepted workflow=GoalPhaseWorkflow version=goal-phase-ordered-run-v4 combinations=78 mixedDecision=60 phaseClosure=1")
  }
}
