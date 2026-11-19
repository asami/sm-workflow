package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Path, Paths}
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.config.{ComponentInitializationParameters, CncfConfigurationParameterCatalog}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import RepositorySyncNativeMergeValidation as V
import WorkflowSbtValidation.Kind

/** Shared initialization describes available checks and inputs, not check results.
  * Actual fetched deltas and retained native evidence still select/exempt work.
  * @since   Nov.  9, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowValidationInitialization {
  final case class Repository(program: Option[RepositorySyncProgramImpact.Policy], documents: Option[RepositorySyncDocumentInputs.Policy],
    declarationReadFailure: Option[WorkflowProjectDeclaration.ReadFailure] = None, projectProvidesFixCheck: Boolean = false)
  final case class Context(configuration: ResolvedConfiguration, parameters: ComponentInitializationParameters) {
    def repositoryC(root: Path, core: ActionCall.Core): Consequence[Repository] = for {
      program <- RepositorySyncProgramImpact.resolveC(parameters)
      documents <- RepositorySyncDocumentInputs.resolveC(parameters)
      discovery <- WorkflowProjectDeclaration.readC(root, core)
      plan <- GoalPhaseInputRecordReadPolicy.storedValue(configuration.configuration.values,
        CncfConfigurationParameterCatalog.SUBSYSTEM_PROCESS_EXECUTION_PLAN_KEY) match {
        case Some(ConfigurationValue.StringValue(raw)) => io.circe.parser.parse(raw)
          .fold(e => Consequence.configurationInvalid(e.message), Consequence.success)
        case _ => Consequence.success(Json.obj())
      }
      project = discovery.toOption.flatten
      ordinary <- if (program.nonEmpty) Consequence.success(None)
        else RepositorySyncProgramImpact.ordinaryPolicyC(parameters, root.toString, plan, project.exists(_.providesFixCheck),
          project.exists(_.sharedScalaLint))
      initialized <- assembleC(root, plan, project, program.orElse(ordinary), documents)
      bound <- initialized.program match {
        case Some(policy) if policy.checks.exists(check => check.ordinaryDiscovery || check.metadata.nonEmpty) =>
          RepositorySyncProgramImpact.bindOrdinaryC(policy, plan, root.toString)
            .map(value => initialized.copy(program = Some(value)))
        case _ => Consequence.success(initialized)
      }
    } yield bound.copy(declarationReadFailure = discovery.swap.toOption)
  }

  /** This inventory narrows existing declarations. Managed execution remains the
    * authority boundary, including workspace, capability and process limits.
    */
  private def _checks(root: Path, plan: Json, fixcheck: Boolean, sharedLint: Boolean = false): Vector[V.Check] = {
    def array(key: String) = plan.hcursor.get[Vector[Json]](key).getOrElse(Vector.empty)
    def text(value: Json, key: String) = value.hcursor.get[String](key).toOption
    val component = "org.simplemodeling.textus.SmWorkflow"
    val capabilities = array("grants").filter(text(_, "componentId").contains(component))
      .flatMap(_.hcursor.get[Vector[String]]("capabilities").getOrElse(Vector.empty)).toSet
    val workspaces = array("workspaces").filter(w => text(w, "root").exists(value =>
      Paths.get(value).normalize == root.normalize)).flatMap(text(_, "identity")).toSet
    val writable = array("workspaceGrants").filter(w => text(w, "componentId").contains(component) &&
      text(w, "access").contains("manage")).flatMap(text(_, "workspaceId")).toSet
    val prefix = "sm-workflow-repository-validation-"
    val available = array("sbtPrograms").flatMap { p => for {
      target <- text(p, "target").filter(t => workspaces(t) && writable(t) && t.startsWith(prefix))
      ordinal <- target.drop(prefix.length).toIntOption.filter(_ > 0)
      capability <- text(p, "capability").filter(capabilities)
      operation <- text(p, "operation")
      if operation != "fix-check" || !sharedLint || p.hcursor.get[Boolean]("lintInput").contains(true)
    } yield (ordinal, operation) }
    Vector("compile" -> Kind.Compile, "fix-check" -> Kind.FixCheck, "test" -> Kind.Test)
      .filter((_, kind) => kind != Kind.FixCheck || fixcheck)
      .foldLeft(Vector.empty[V.Check]) { case (selected, (name, kind)) =>
        available.filter((ordinal, operation) => operation == name && !selected.exists(_.ordinal == ordinal))
          .sortBy(_._1).headOption.fold(selected)(value => selected :+ V.Check(name, value._1, kind))
      }
  }

  def assembleC(root: Path, plan: Json, project: Option[WorkflowProjectDeclaration.Project],
    explicitProgram: Option[RepositorySyncProgramImpact.Policy], explicitDocuments: Option[RepositorySyncDocumentInputs.Policy]): Consequence[Repository] = {
    val inferred = project.flatMap { p =>
      val selected = _checks(root, plan, p.providesFixCheck, p.sharedScalaLint)
      Option.when(selected.exists(_.kind == Kind.Compile) && selected.exists(_.kind == Kind.Test)) {
        val reason = p.reason + "; no declared narrower test coverage, use actual managed FULL checks"
        RepositorySyncProgramImpact.Policy(selected.map(c => RepositorySyncProgramImpact.Coverage(c, p.inputs, p.inputs, reason)),
          selected, p.inputs, reason, Vector.empty)
      }
    }
    val program = explicitProgram.orElse(inferred)
    val documents = explicitDocuments.orElse(project.flatMap { p =>
      val consumed = (p.inputs ++ program.toVector.flatMap(policy =>
        policy.coverage.flatMap(_.inputPaths) ++ policy.fullPaths)).distinct
      // Never let a document convention exempt an explicitly consumed input.
      // If the optional default cannot fit the existing scope model, keep it
      // unknown rather than refusing light synchronization or truncating inputs.
      if (consumed.size > 128) None else Some(RepositorySyncDocumentInputs.Policy(p.documents, consumed, p.reason))
    })
    for {
      _ <- if (!project.exists(_.sharedScalaLint) || project.exists(p => p.providesFixCheck &&
        _checks(root, plan, fixcheck = true, sharedLint = true).exists(_.kind == Kind.FixCheck))) Consequence.unit
        else Consequence.configurationInvalid("Shared Scala lint project declaration requires an already trusted lintInput=true FixCheck capability for this source root; no host plan or grants were changed")
      _ <- program.fold(Consequence.unit)(RepositorySyncProgramImpact.validateC)
      _ <- documents.fold(Consequence.unit)(RepositorySyncDocumentInputs.validateC)
    } yield Repository(program, documents, projectProvidesFixCheck = project.exists(_.providesFixCheck))
  }
}
