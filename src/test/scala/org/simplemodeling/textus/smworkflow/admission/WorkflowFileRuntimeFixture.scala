package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.bootstrap.BootstrapConfig
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentInstanceMetadata, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.security.IngressSecurityResolver
import org.goldenport.cncf.subsystem.SubsystemExecutionProfile
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import SplitPhaseExplicitPartitionFixture.take

/** Public V3 configuration and ordinary host/Operation UoW, with no File process.
  * Each fixture owns its normal handle and closes its runtime resources.
  * @since   Oct. 15, 2026
  * @version Oct. 20, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object WorkflowFileRuntimeFixture {
  val documentsWorkspace = "sm-workflow-documents"
  def fresh(): Path = Files.createTempDirectory(Files.createDirectories(
    Path.of("target/sm-workflow/work/typed-file").toAbsolutePath), "case-").toRealPath()

  def plan(root: Path, workspace: String = documentsWorkspace, access: Option[String] = Some("manage")): Json =
    Json.obj("schema" -> Json.fromString("cncf.runtime-process-execution.v3"),
      "programs" -> Json.arr(), "grants" -> Json.arr(), "sbtPrograms" -> Json.arr(), "gitPrograms" -> Json.arr(),
      "workspaces" -> Json.arr(Json.obj("identity" -> Json.fromString(workspace),
        "root" -> Json.fromString(root.toRealPath().toString))),
      "workspaceGrants" -> Json.fromValues(access.toVector.map(value => Json.obj(
        "componentId" -> Json.fromString(SmWorkflowComponent.componentId.name),
        "workspaceId" -> Json.fromString(workspace), "access" -> Json.fromString(value)))))

  def withCore[A](root: Path, workspace: String = documentsWorkspace, access: Option[String] = Some("manage"),
    cap: Int = 1048576, mapping: Map[String, Json] = Map(
      WorkflowSelectedDocumentFiles.repositoryKey -> Json.fromString("repo"),
      WorkflowSelectedDocumentFiles.workspaceKey -> Json.fromString(documentsWorkspace)))
    (task: ActionCall.Core => A): A = {
    val directory = Files.createDirectories(root.resolve(".textus"))
    val values = mapping ++ Map(
      "textus.subsystem.process-execution.plan" -> Json.fromString(plan(root, workspace, access).noSpaces),
      "textus.resource.read.max-bytes" -> Json.fromString(cap.toString),
      "textus.subsystem.user-mode" -> Json.fromString("standalone"))
    Files.writeString(directory.resolve("config.json"), Json.obj(values.toVector*).noSpaces, StandardCharsets.UTF_8)
    Files.writeString(directory.resolve("assembly.yaml"),
      """subsystem: typed-file-spec
        |components: []
        |security:
        |  authentication:
        |    convention: enabled
        |    fallback_privilege: disabled
        |    local_subject:
        |      id: participant
        |      roles: [user]
        |      capabilities: [user]
        |      security_level: user
        |config:
        |  textus.subsystem.user-mode: standalone
        |""".stripMargin, StandardCharsets.UTF_8)
    Files.writeString(directory.resolve("test.yaml"),
      """kind: test-descriptor
        |execution:
        |  profile: controlled
        |  key: typed-file-spec
        |  time:
        |    mode: manual
        |    start-at: '2026-10-15T00:00:00Z'
        |  random:
        |    mode: seeded
        |    seed: typed-file-spec
        |  ids:
        |    mode: deterministic
        |  scheduler:
        |    mode: manual
        |  ordering:
        |    mode: deterministic
        |""".stripMargin, StandardCharsets.UTF_8)
    val runtime = new CncfRuntime()
    val handle = try take(runtime.initializeHandle(BootstrapConfig(cwd = root, args = Array(
      s"--textus.assembly.descriptor=${directory.resolve("assembly.yaml")}",
      s"--textus.test.descriptor=${directory.resolve("test.yaml")}"))))
      catch { case error: Throwable => runtime.closeEmbedding(); throw error }
    try {
      val owner = RuntimeBindingAdmissionFixture.admit(handle.subsystem)
      var result = Option.empty[A]
      val probe = new spec.OperationDefinition {
        val specification = spec.OperationDefinition.Specification(name = "fileProbe",
          request = spec.RequestDefinition.empty, response = spec.ResponseDefinition.void)
        def createOperationRequest(requestInput: Request): Consequence[OperationRequest] =
          Consequence.success(new CommandAction {
            val request = requestInput
            override def commandExecutionMode = CommandExecutionMode.Sync
            def createCall(callCore: ActionCall.Core): ActionCall = new ProcedureActionCall {
              val core = callCore
              def execute(): Consequence[OperationResponse] = {
                result = Some(task(core))
                Consequence.success(OperationResponse.Scalar("typed-file-spec"))
              }
            }
          })
      }
      val factory = new SmWorkflowConfiguredComponentFactory(Vector.empty, Vector.empty,
        (_, _, _) => Consequence.success(Vector(spec.ServiceDefinition("SpecificationFiles",
          spec.OperationDefinitionGroup(NonEmptyVector.of(probe)))))).primaryFactory
      val id = SmWorkflowComponent.componentId
      val component = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin,
        Vector(ComponentDescriptor(name = Some(id.name), componentName = Some(id.name),
          version = Some("0.1.0-SNAPSHOT"), schemaVersion = Some(3), componentId = Some(id))))
        .withInstanceMetadata(ComponentInstanceMetadata(id.name, "typed-file-spec", componentId = Some(id)))))
      owner.add(component)
      val authenticated = take(IngressSecurityResolver.resolve(SubsystemExecutionProfile.Fixed,
        component.logic.executionContext(), Map.empty)).executionContext
      val actor: ExecutionContext = ExecutionContext.withFrameworkCallTreeEnabled(authenticated, true)
      take(owner.executeOperationResponseInContext(Request.of(component = component.name,
        service = "SpecificationFiles", operation = "fileProbe"), actor))
      result.get
    } finally handle.close()
  }
}
