package org.simplemodeling.textus.smworkflow

/** Scala 2.12 probe input for the actual Cozy producer; compiled only by the owning test.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  */
private[smworkflow] object GoalPhaseOrderedTriageProducerProbe {
  val source: String = """package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model}

/** Exercise the actual Scala2.12 producer for the ordered phase and Decision roles.
  * @since   Oct.  6, 2026
  * @version Oct. 24, 2026
  */
object GoalPhaseOrderedTriageDerivationProbe {
  def main(args: Array[String]): Unit = {
    require(args.length == 1, "Expected one actual CML source")
    // Given the current application source and its actual producer model.
    val model = Model.parse(KaleidoxConfig.default,
      new String(Files.readAllBytes(Paths.get(args(0))), StandardCharsets.UTF_8))
    require(model.errors.isEmpty, model.errors.mkString(" | "))
    val selected = CompositeStateMachineCml.workflowDefinitions(model).filter(_.identity == "GoalPhaseWorkflow")
    require(selected.size == 1, "Expected one GoalPhase Workflow")
    val workflow = selected.head
    require(workflow.version == "goal-phase-business-operation-v7", workflow.version)
    val phases = Vector("Idle", "Implementation", "InspectCandidate", "Validation", "Triage", "Review", "Repair", "Closing",
      "PhaseInspection", "PhaseValidation", "PhaseReview", "PhaseClosing", "Decision", "Closed")
    val named = Map("Idle" -> "Pending", "Implementation" -> "OrderedImplementation",
      "InspectCandidate" -> "OrderedInspectCandidate", "Validation" -> "OrderedValidation",
      "Triage" -> "OrderedTriage", "Review" -> "OrderedReview", "Repair" -> "OrderedRepair", "Closing" -> "OrderedClosing",
      "PhaseInspection" -> "PhaseInspection", "PhaseValidation" -> "PhaseValidation",
      "PhaseReview" -> "PhaseReview", "PhaseClosing" -> "PhaseClosing",
      "Decision" -> "PhaseDecision", "Closed" -> "PhaseClosed")
    // When each actual phase configuration is derived from normalized producer output.
    val actual = phases.map { phase =>
      val configuration = Map("phase" -> phase, "orderedDecision" -> "Retained", "findingRecords" -> "Retained")
      val states = workflow.compositeStateMachine.derivations
        .filter(_.configuration.bindings.map(b => b.role -> b.state).toMap == configuration).map(_.state)
      (phase, states)
    }
    // Then every real frontier is unique and only whole-Phase closure is terminal.
    require(workflow.compositeStateMachine.derivations.size == 14, "Expected the 14 actual phase configurations")
    actual.foreach { case (phase, states) =>
      require(states == Vector(named(phase)), s"$phase: $states != ${named(phase)}")
    }
    require(actual.count(_._2 == Vector("PhaseClosed")) == 1, "Only whole-Phase closure derives PhaseClosed")
    require(workflow.compositeStateMachine.initialConfiguration.get.bindings.map(b => b.role -> b.state).toMap ==
      Map("phase" -> "Idle", "orderedDecision" -> "Retained", "findingRecords" -> "Retained"), "Initial roles differ")
    require(workflow.requiredOperations.size == 7, "Expected seven actual Required Operations")
    val triage = workflow.requiredOperations.find(_.capability == "goal-phase-step-triage-capability").get
    require(triage.action.identity == "TriageOrderedStep", "Triage action differs")
    require(triage.action.operation.service == "GoalPhaseStepTriageService", "Triage service differs")
    require(triage.action.operation.name == "triageGoalPhaseStep", "Triage operation differs")
    require(triage.action.operation.inputType == Some("GoalPhaseWorkRequest"), "Triage input differs")
    require(triage.action.operation.outputType == Some("GoalPhaseWorkResult"), "Triage result differs")
    val recording = workflow.requiredOperations.find(_.capability == "goal-phase-finding-recording-capability").get
    require(recording.action.identity == "RecordGoalFindings", "Recording action differs")
    require(recording.action.operation.service == "GoalPhaseFindingRecordingService", "Recording service differs")
    require(recording.action.operation.name == "recordGoalFindings", "Recording operation differs")
    require(recording.action.operation.inputType == Some("GoalPhaseFindingRecordingRequest"), "Recording input differs")
    require(recording.action.operation.outputType == Some("GoalPhaseFindingRecordingResult"), "Recording result differs")
    require(workflow.providedOperations.map(_.operation.name).toSet == Set("startGoalPhase", "advanceGoalPhase",
      "submitGoalPhaseWorkResult", "submitGoalPhaseOrderedDecision", "submitGoalPhaseFindingRecording",
      "describeGoalPhaseValidationRecovery", "recoverGoalPhaseValidation",
      "describeGoalPhaseRemainingPlan", "adoptGoalPhaseRemainingPlan",
      "describeGoalPhaseReviewPurposeRepair", "admitGoalPhaseReviewPurposeRepair",
      "describeGoalPhaseClosureRecovery", "recoverGoalPhaseClosure",
      "describeGoalPhaseSourceHistoryRecovery", "recoverGoalPhaseSourceHistory",
      "describeGoalPhaseCandidateRecovery", "recoverGoalPhaseCandidate",
      "describeGoalPhaseWholePhaseCandidateRecovery", "recoverGoalPhaseWholePhaseCandidate",
      "describeGoalPhaseCandidateObservationContinuation", "continueGoalPhaseCandidateObservation",
      "describeGoalPhaseCompletionPolicy", "adoptGoalPhaseCompletionPolicy",
      "describeGoalPhaseFailedPhaseRepair", "adoptGoalPhaseFailedPhaseRepair"), "Provided Operations differ")
    println("accepted workflow=GoalPhaseWorkflow version=goal-phase-business-operation-v7 combinations=14 phaseClosure=1")
  }
}
"""
}
