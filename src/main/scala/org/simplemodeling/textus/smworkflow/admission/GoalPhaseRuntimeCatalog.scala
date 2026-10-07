package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.workflow.GoalPhaseWorkflowStateMachineWorkflow1
import domain.statemachine.candidateadmission.{CandidateAdmissionProducerAbi as CompiledProducerAbi, GoalPhaseWorkflowCandidateAdmissionProducer1}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{CandidateAdmissionProducerAbi, CandidateWorkflowAbi, WorkflowInstancePersistence}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.net.URLClassLoader
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/**
 * Immutable compiled-definition admission, with no storage or authority lookup.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
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

  private def readResourceC(loader: ClassLoader, path: String): Consequence[String] =
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
      _ <- admitCompiledProducerC(producer)
      compiled = GoalPhaseWorkflowStateMachineWorkflow1.workflow
      workflow <- CandidateWorkflowAbi.parseSelectedC(workflowJson, producer, "src/main/cozy/sm-workflow.cml",
        compiled.identity, compiled.version)
      _ <- admitCompiledC(workflow)
      binding <- WorkflowInstancePersistence.bindCandidateDefinitionC(workflow)
    } yield new Catalog(workflow, producer, binding)

  private def admitCompiledProducerC(received: CandidateAdmissionProducerAbi.Artifact): Consequence[Unit] = {
    import CandidateAdmissionProducerAbi.*
    val compiled = GoalPhaseWorkflowCandidateAdmissionProducer1.producer
    def source(value: CompiledProducerAbi.SourceIdentity): SourceLocation =
      SourceLocation(value.line.getOrElse(-1))
    def reference(value: CompiledProducerAbi.SourceReference): SourceReference =
      SourceReference(value.value, source(value.source))
    def operation(value: CompiledProducerAbi.Operation): Operation =
      Operation(ServiceIdentity(value.service), OperationIdentity(value.name),
        value.inputType.map(TypeIdentity.apply), value.resultType.map(TypeIdentity.apply))
    val model = Model(ModelIdentity(compiled.model.compositeStateMachineIdentity),
      compiled.model.compositeStateMachineName, source(compiled.model.compositeStateMachineSource),
      compiled.model.workflow.map(value => Workflow(WorkflowIdentity(value.identity), WorkflowVersion(value.version),
        source(value.rootSource), source(value.definitionSource))))
    val generator = Generator(compiled.generator.schemaVersion, compiled.generator.generatorIdentity)
    val judgments = compiled.judgments.map(value => Judgment(JudgmentActionIdentity(value.actionIdentity),
      operation(value.operation), value.inputBinding.map(InputBinding.apply),
      reference(value.goal), reference(value.context), reference(value.candidate),
      value.alternatives.map(reference), value.criteria.map(reference), reference(value.expectedResult),
      reference(value.rationale), reference(value.evidence), reference(value.evidenceScope),
      reference(value.evidenceFreshness), reference(value.evidenceProvenance),
      source(value.actionSource), source(value.semanticSource)))
    val admissions = compiled.admissions.map(value => Admission(AdmissionActionIdentity(value.actionIdentity),
      JudgmentActionIdentity(value.candidateJudgmentActionIdentity), operation(value.operation),
      value.inputBinding.map(InputBinding.apply), value.effectClass, value.transactionRequirement,
      source(value.actionSource), source(value.semanticSource), source(value.candidateActionSource)))
    val relations = compiled.judgmentAdmissions.map(value => JudgmentAdmission(
      JudgmentActionIdentity(value.judgmentActionIdentity), AdmissionActionIdentity(value.admissionActionIdentity),
      source(value.judgmentSource), source(value.admissionSource), source(value.candidateActionSource)))
    val required = compiled.requiredSpi.map(value => RequiredSpi(RequiredSpiIdentity(value.capability),
      JudgmentActionIdentity(value.actionIdentity), operation(value.operation),
      source(value.capabilitySource), source(value.actionSource)))
    val expected = ProducerModel(model, generator, judgments, admissions, relations, required)
    val matches = received.models.filter(_.model.workflow.exists(value =>
      expected.model.workflow.exists(key => key.identity == value.identity && key.version == value.version)))
    if (compiled.schemaVersion == received.schemaVersion && received.generator == generator &&
        matches == Vector(expected)) Consequence.unit
    else Consequence.configurationInvalid("GoalPhase runtime producer policy differs from compiled definition")
  }

  private def admitCompiledC(received: CandidateWorkflowAbi.Definition): Consequence[Unit] = {
    val compiled = GoalPhaseWorkflowStateMachineWorkflow1.workflow
    def operation(service: String, name: String, input: Option[String], result: Option[String]) =
      CandidateWorkflowAbi.Operation(service, name, input, result)
    val actions = compiled.actions.map(action => CandidateWorkflowAbi.Action(action.identity, action.kind,
      operation(action.operation.service, action.operation.name, action.operation.inputType, action.operation.resultType),
      action.inputBinding, action.source.line.getOrElse(-1)))
    val required = compiled.requiredOperations.map(spi => CandidateWorkflowAbi.RequiredSpi(spi.identity.capability,
      spi.actionIdentity, operation(spi.operation.service, spi.operation.operation,
        spi.inputType.map(_.value), spi.resultType.map(_.value)), spi.capabilitySource.line.getOrElse(-1)))
    val expected = CandidateWorkflowAbi.Workflow(compiled.identity, compiled.version,
      compiled.source.root.line.getOrElse(-1), compiled.source.definition.line.getOrElse(-1),
      compiled.states.map(_.name), actions, required)
    if (compiled.schemaVersion == CandidateWorkflowAbi.schemaVersion && received.workflow == expected)
      Consequence.unit
    else Consequence.configurationInvalid("GoalPhase runtime catalog differs from compiled Workflow")
  }

  def admitBindingC(catalog: Catalog, declaration: GoalPhaseManagedStartAssembly.Declaration,
    producer: CandidateAdmissionProducerAbi.Artifact
  ): Consequence[Unit] =
    if (catalog == null || declaration == null || declaration.definition == null ||
        producer != catalog.producer)
      Consequence.configurationInvalid("GoalPhase Factory differs from its admitted runtime catalog")
    else declaration.definition.validateC.flatMap { actual =>
      val expected = catalog.definition
      if (actual.workflowIdentity == expected.workflowIdentity && actual.workflowRevision == expected.workflowRevision &&
          actual.sourceCorrelation == expected.sourceCorrelation && actual.producerAbiIdentity == expected.producerAbiIdentity &&
          actual.workflowAbiIdentity == expected.workflowAbiIdentity && actual.bootstrapAbiIdentity == expected.bootstrapAbiIdentity &&
          actual.producerRevision == expected.producerRevision && actual.fixtureSha256 == expected.fixtureSha256)
        Consequence.unit
      else Consequence.configurationInvalid("GoalPhase Factory definition differs from admitted runtime bytes")
    }
}
