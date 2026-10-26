package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.workflow.GoalPhaseWorkflowStateMachineWorkflow1
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{CandidateAdmissionProducerAbi, CandidateWorkflowAbi, WorkflowInstancePersistence}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.net.URLClassLoader
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/**
 * Resolve and consume the official executable definition; no compiled-copy proof.
 * @since   Oct.  1, 2026
 * @version Oct. 26, 2026
 */
private[smworkflow] object GoalPhaseRuntimeCatalog {
  final class Catalog private[GoalPhaseRuntimeCatalog] (
    val workflow: CandidateWorkflowAbi.Definition,
    val producer: CandidateAdmissionProducerAbi.Artifact,
    val definition: WorkflowInstancePersistence.DefinitionBinding
  )

  def loadC(): Consequence[Catalog] =
    try {
      val location = Option(getClass.getProtectionDomain).flatMap(domain => Option(domain.getCodeSource))
        .map(_.getLocation)
      location match {
        case None => Consequence.configurationInvalid("GoalPhase runtime resource owner is missing")
        case Some(url) =>
          val loader = new URLClassLoader(Array(url), null)
          try loadC(loader) finally loader.close()
      }
    } catch {
      case NonFatal(_) => Consequence.configurationInvalid("GoalPhase runtime resource owner could not be read")
    }

  def loadC(loader: ClassLoader): Consequence[Catalog] =
    if (loader == null) Consequence.configurationInvalid("GoalPhase runtime resource loader is missing")
    else for {
      workflow <- readResourceC(loader, "META-INF/cozy/statemachine-workflow-abi.json")
      producer <- readResourceC(loader, "META-INF/cozy/candidate-admission-producer-abi.json")
      catalog <- parseC(workflow, producer)
    } yield catalog

  private[admission] def readResourceC(loader: ClassLoader, path: String): Consequence[String] =
    try {
      val resources = loader.getResources(path).asScala.toVector
      if (resources.size != 1)
        Consequence.configurationInvalid(s"GoalPhase requires one official runtime resource: $path")
      else {
        val input = resources.head.openStream()
        try Consequence.success(StandardCharsets.UTF_8.newDecoder()
          .decode(ByteBuffer.wrap(input.readAllBytes())).toString)
        finally input.close()
      }
    } catch {
      case NonFatal(_) => Consequence.configurationInvalid(s"GoalPhase runtime resource could not be read: $path")
    }

  def parseC(workflowJson: String, producerJson: String): Consequence[Catalog] =
    if (workflowJson == null || producerJson == null)
      Consequence.configurationInvalid("GoalPhase runtime catalog text is missing")
    else for {
      producer <- CandidateAdmissionProducerAbi.parseC(producerJson)
      compiled = GoalPhaseWorkflowStateMachineWorkflow1.workflow
      workflow <- CandidateWorkflowAbi.parseSelectedC(workflowJson, producer, "src/main/cozy/sm-workflow.cml",
        compiled.identity, compiled.version)
      binding <- WorkflowInstancePersistence.bindCandidateDefinitionC(workflow)
    } yield new Catalog(workflow, producer, binding)

}
