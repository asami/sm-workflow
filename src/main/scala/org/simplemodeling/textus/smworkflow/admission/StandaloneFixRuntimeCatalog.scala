package org.simplemodeling.textus.smworkflow.admission

import java.net.URLClassLoader
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*

/** Own derived standalone definition, using the common official ABI reader.
  * @since   Oct. 26, 2026
  * @version Oct. 26, 2026
  */
private[smworkflow] object StandaloneFixRuntimeCatalog {
  def loadC(): Consequence[WorkflowInstancePersistence.DefinitionBinding] = try {
    val location = Option(getClass.getProtectionDomain).flatMap(d => Option(d.getCodeSource)).map(_.getLocation)
    location.map { url =>
      val loader = new URLClassLoader(Array(url), null)
      try for {
        raw <- GoalPhaseRuntimeCatalog.readResourceC(loader, "META-INF/cozy/statemachine-workflow-abi.json")
        producerRaw <- GoalPhaseRuntimeCatalog.readResourceC(loader, "META-INF/cozy/candidate-admission-producer-abi.json")
        producer <- CandidateAdmissionProducerAbi.parseC(producerRaw)
        compiled = domain.statemachine.workflow.StandaloneFixWorkflowStateMachineWorkflow4.workflow
        workflow <- CandidateWorkflowAbi.parseSelectedC(raw, producer, "src/main/cozy/sm-workflow.cml", compiled.identity, compiled.version)
        binding <- WorkflowInstancePersistence.bindCandidateDefinitionC(workflow)
      } yield binding
      finally loader.close()
    }.getOrElse(Consequence.configurationInvalid("Standalone runtime resource owner is missing"))
  } catch { case NonFatal(_) => Consequence.configurationInvalid("Standalone official runtime ABI could not be read") }
}
