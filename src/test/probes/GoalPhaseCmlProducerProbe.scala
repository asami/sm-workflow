package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.security.MessageDigest
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model}

/** Producer-only compatibility probe. Run explicitly with the current Cozy
  * Scala 2.12 compiled classpath; this is not part of sm-workflow's Scala 3
  * application test sources and does not replace CAR generation or CNCF tests.
  */
object GoalPhaseCmlProducerProbe {
  def main(args: Array[String]): Unit = {
    require(args.length == 1, "Expected exactly one CML source path")
    val source = new String(Files.readAllBytes(Paths.get(args(0))), StandardCharsets.UTF_8)
    val model = Model.parse(KaleidoxConfig.default, source)
    require(model.errors.isEmpty, model.errors.mkString(" | "))

    val workflows = CompositeStateMachineCml.workflowDefinitions(model)
    val selected = workflows.filter(_.identity == "GoalPhaseWorkflow")
    require(selected.size == 1, s"Expected one GoalPhase Workflow, found ${selected.size}")
    val workflow = selected.head
    require(workflow.identity == "GoalPhaseWorkflow", workflow.identity)
    val actions = workflow.compositeStateMachine.actions.map(_.identity)
    require(actions.contains("ReviewStep") && actions.contains("AdmitStepClose"), actions.mkString(","))
    require(actions.contains("RepairStep"), "Expected the ordinary RepairStep action")
    require(workflow.requiredOperations.size == 2, "Expected review and repair required operations")
    require(workflow.providedOperations.size == 4,
      "Expected start, close, review completion and repair completion provided operations")

    val workflowJson = StateMachineWorkflowAbiGenerator.canonicalJson(selected)
    val candidateJson = CandidateAdmissionProducerAbiGenerator.canonicalJson(Vector.empty, selected)
    val workflowRealm = StateMachineWorkflowAbiGenerator.generate(selected)
    val candidateRealm = CandidateAdmissionProducerAbiGenerator.generate(Vector.empty, selected)
    require(workflowJson.contains("GoalPhaseWorkflow"), "Workflow ABI omits GoalPhaseWorkflow")
    require(candidateJson.contains("ReviewStep") && candidateJson.contains("AdmitStepClose"),
      "Candidate-Admission ABI omits the Judgment/Admission pair")
    require(workflowRealm.get("target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/GoalPhaseWorkflowStateMachineWorkflow1.scala").nonEmpty,
      "Workflow generator omitted GoalPhase descriptor")
    require(candidateRealm.get("target/scala-3.3.8/src_managed/main/scala/domain/statemachine/candidateadmission/GoalPhaseWorkflowCandidateAdmissionProducer1.scala").nonEmpty,
      "Candidate-Admission generator omitted GoalPhase descriptor")

    println(s"accepted workflow=${workflow.identity} actions=${actions.mkString(",")} required=${workflow.requiredOperations.size} provided=${workflow.providedOperations.size}")
    println(s"workflow_abi=${StateMachineWorkflowAbiGenerator.schemaVersion} candidate_abi=${CandidateAdmissionProducerAbiGenerator.schemaVersion}")
    println(s"workflow_json_sha256=${sha256(workflowJson)} candidate_json_sha256=${sha256(candidateJson)}")
    // Emit the actual producer's canonical bytes, not an application reconstruction.
    println("workflow_json_base64=" + java.util.Base64.getEncoder.encodeToString(workflowJson.getBytes(StandardCharsets.UTF_8)))
    println("candidate_json_base64=" + java.util.Base64.getEncoder.encodeToString(candidateJson.getBytes(StandardCharsets.UTF_8)))
  }

  private def sha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
