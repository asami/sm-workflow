package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model}

/** Exercise the actual Scala2.12 producer for the ordered phase and Decision roles.
  * @since   Oct.  6, 2026
  * @version Oct. 12, 2026
  */
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
    require(workflow.version == "goal-phase-minimal-operation-v5", workflow.version)
    val phases = Vector("Idle", "Implementation", "InspectCandidate", "Validation", "Review", "Repair", "Closing",
      "PhaseInspection", "PhaseValidation", "PhaseReview", "PhaseClosing", "Decision", "Closed")
    val named = Map("Idle" -> "Pending", "Implementation" -> "OrderedImplementation",
      "InspectCandidate" -> "OrderedInspectCandidate", "Validation" -> "OrderedValidation",
      "Review" -> "OrderedReview", "Repair" -> "OrderedRepair", "Closing" -> "OrderedClosing",
      "PhaseInspection" -> "PhaseInspection", "PhaseValidation" -> "PhaseValidation",
      "PhaseReview" -> "PhaseReview", "PhaseClosing" -> "PhaseClosing",
      "Decision" -> "PhaseDecision", "Closed" -> "PhaseClosed")
    // When each actual phase configuration is derived from normalized producer output.
    val actual = phases.map { phase =>
      val configuration = Map("phase" -> phase, "orderedDecision" -> "Retained")
      val states = workflow.compositeStateMachine.derivations
        .filter(_.configuration.bindings.map(b => b.role -> b.state).toMap == configuration).map(_.state)
      (phase, states)
    }
    // Then every real frontier is unique and only whole-Phase closure is terminal.
    require(workflow.compositeStateMachine.derivations.size == 13, "Expected the13 actual phase configurations")
    actual.foreach { case (phase, states) =>
      require(states == Vector(named(phase)), s"$phase: $states != ${named(phase)}")
    }
    require(actual.count(_._2 == Vector("PhaseClosed")) == 1, "Only whole-Phase closure derives PhaseClosed")
    println("accepted workflow=GoalPhaseWorkflow version=goal-phase-minimal-operation-v5 combinations=13 phaseClosure=1")
  }
}
