package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.workflow.SplitPhaseWorkflowStateMachineWorkflow2
import java.net.URLClassLoader
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{CandidateAdmissionProducerAbi, CandidateWorkflowAbi, WorkflowInstancePersistence}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/**
 * Official packaged-definition admission only; no fixture or execution-store fallback.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseRuntimeCatalog {
  final class Catalog private[SplitPhaseRuntimeCatalog] (
    val workflow: CandidateWorkflowAbi.Definition,
    val producer: CandidateAdmissionProducerAbi.Artifact,
    val definition: WorkflowInstancePersistence.DefinitionBinding
  )

  def loadC(): Consequence[Catalog] =
    try {
      Option(getClass.getProtectionDomain).flatMap(domain => Option(domain.getCodeSource))
        .map(_.getLocation) match {
        case None => Consequence.configurationInvalid("SplitPhase runtime resource owner is missing")
        case Some(location) =>
          val loader = new URLClassLoader(Array(location), null)
          try loadC(loader) finally loader.close()
      }
    } catch {
      case NonFatal(_) => Consequence.configurationInvalid("SplitPhase runtime resource owner could not be read")
    }

  def loadC(loader: ClassLoader): Consequence[Catalog] =
    if (loader == null) Consequence.configurationInvalid("SplitPhase runtime resource loader is missing")
    else for {
      workflow <- readResourceC(loader, "META-INF/cozy/statemachine-workflow-abi.json")
      producer <- readResourceC(loader, "META-INF/cozy/candidate-admission-producer-abi.json")
      catalog <- parseC(workflow, producer)
    } yield catalog

  private def readResourceC(loader: ClassLoader, path: String): Consequence[String] =
    try {
      val resources = loader.getResources(path).asScala.toVector
      if (resources.size != 1)
        Consequence.configurationInvalid(s"SplitPhase requires one official runtime resource: $path")
      else {
        val input = resources.head.openStream()
        try Consequence.success(StandardCharsets.UTF_8.newDecoder()
          .decode(ByteBuffer.wrap(input.readAllBytes())).toString)
        finally input.close()
      }
    } catch {
      case NonFatal(_) => Consequence.configurationInvalid(s"SplitPhase runtime resource could not be read: $path")
    }

  def parseC(workflowJson: String, producerJson: String): Consequence[Catalog] =
    if (workflowJson == null || producerJson == null)
      Consequence.configurationInvalid("SplitPhase runtime catalog text is missing")
    else for {
      producer <- CandidateAdmissionProducerAbi.parseC(producerJson)
      compiled <- SplitPhaseDefinitionBinding.candidateC
      _ <- admitProducerC(producer, compiled.sidecar)
      descriptor = SplitPhaseWorkflowStateMachineWorkflow2.workflow
      workflow <- CandidateWorkflowAbi.parseSelectedC(workflowJson, producer, "src/main/cozy/sm-workflow.cml",
        descriptor.identity, descriptor.version)
      _ <- if (CandidateWorkflowAbi.compatibleWorkflow(workflow.workflow, compiled.workflow)) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase runtime catalog differs from compiled Workflow")
      binding <- WorkflowInstancePersistence.bindCandidateDefinitionC(workflow)
    } yield new Catalog(workflow, producer, binding)

  private def admitProducerC(received: CandidateAdmissionProducerAbi.Artifact,
    compiled: CandidateAdmissionProducerAbi.Artifact
  ): Consequence[Unit] = {
    val matches = received.models.filter(_.model.workflow.exists(value =>
      value.identity.value == "SplitPhaseWorkflow" && value.version.value == "split-phase-selected-application-v12"))
    if (matches.size == 1 && compiled.models.size == 1 &&
        CandidateAdmissionProducerAbi.compatiblePolicy(matches.head, compiled.models.head)) Consequence.unit
    else Consequence.configurationInvalid("SplitPhase runtime producer policy differs from compiled definition")
  }
}
