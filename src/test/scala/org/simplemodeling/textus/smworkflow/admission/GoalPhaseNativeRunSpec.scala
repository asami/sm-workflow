package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.*
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.subsystem.*
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Request, Response}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartCommand, GoalPhaseStartInput, RepositorySyncStartCommand, RepositorySyncStartInput}
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeRun as F
import GoalPhaseNativeWork as S
import GoalPhaseNativeServiceAssembly as A
import SplitPhaseExplicitPartitionFixture.take

/** Actual managed Git observation/SQLite/CLI/paired native work. Explicit
  * fixture input installation is not normal deployment or independent reviewer proof.
  * @since   Oct.  6, 2026
  *  version Oct.  8, 2026
  * @version Oct.  9, 2026
  */
final class GoalPhaseNativeRunSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "goalordered", name))
  private val config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("workflow-store")),
    MigrationPolicy("test-v1"), RetentionPolicy("test-v1"), LeasePolicy("test-v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("workflow-store")))
  private val limits = ProcessExecutionLimits(Some(10000), Some(30000), Some(1000), Some(2097152),
    Some(131072), Some(16384), Some(3), Some(192), Some(1), Some(131072), Some(4194304))
  private final case class Episode(root: Path) {
    val target = Programs.Target(root.resolve("repository"), "main", "origin", "main", root.resolve("remote.git").toString)
    val input = GoalPhaseStartInput("PHASE-2", "owned-repository")
    val command = GoalPhaseStartCommand(input, "selected-complete-phase", "participant", "ordered-start")
    val plan = O.Plan("complete-plan", "1", input.phaseIdentity, input.repositoryIdentity,
      Vector(O.Step("S1", "Implement the first connected boundary", Vector("one.scala"), Vector.empty, Vector("focused")),
        O.Step("S2", "Complete the next boundary", Vector("two.scala"), Vector("S1"), Vector("focused-next"))),
      Vector("whole-phase"))
    val declaration = F.Declaration(config, collection("instances"), collection("continuations"), input, target)
    def identity = take(F.identityC(command)).value
    def selection = ProfileStartAdmission.Selection(command.selectionIdentity, F.startOperation,
      WorkflowDefinitionIdentity("GoalPhaseWorkflow"), WorkflowDefinitionRevision("goal-phase-ordered-run-v4"),
      command.participantIdentity, ProfileStartAdmission.SelectionOrigin.ExplicitHuman, command.idempotencyKey,
      None, Some(GoalPhaseStartCommandAdapter.selectedInput(input)))
  }
  private def episode(): Episode = Episode(Files.createTempDirectory(Files.createDirectories(
    Path.of("target/sm-workflow/work/native-goal-phase").toAbsolutePath.normalize), "episode-"))
  private final class Driver extends ProcessExecutionDriver with AutoCloseable {
    private val delegate = new LocalProcessExecutionDriver()
    private var counts = Map.empty[String, Int]
    private var argumentCounts = Map.empty[(String, Vector[String]), Int]
    def count(id: String) = counts.getOrElse(id, 0)
    def count(id: String, arguments: Vector[String]) = argumentCounts.getOrElse((id, arguments), 0)
    def safeIdentity = delegate.safeIdentity
    private def note(e: ResolvedProcessExecution): Unit = {
      val id = e.definition.safeProgramIdentity
      counts = counts.updated(id, count(id) + 1)
      val arguments = e.request.arguments
      argumentCounts = argumentCounts.updated((id, arguments), count(id, arguments) + 1)
    }
    def startC(e: ResolvedProcessExecution): Consequence[ProcessExecutionHandle] = { note(e); delegate.startC(e) }
    override def startC(e: ResolvedProcessExecution, area: ProcessExecutionWorkArea): Consequence[ProcessExecutionHandle] = {
      note(e); delegate.startC(e, area)
    }
    def close(): Unit = delegate.close()
  }
  private final class Host(e: Episode, actor: String = "participant", selected: Option[O.Plan] = None,
    missingPlan: Boolean = false, candidate: Boolean = false,
    acknowledgement: String => Consequence[Unit] = _ => Consequence.unit,
    checkMode: String = "", reviewMode: String = "", closureMode: String = "", phaseMode: String = "",
    repositoryMode: String = "", repositoryScenario: String = "", repositoryHuman: Boolean = false,
    normalBinding: Boolean = false, extraCandidatePaths: Vector[String] = Vector.empty,
    repositoryAuthorityPresentation: Boolean = false,
    repositoryInstallationSelection: String = "selected-phase-repository") extends AutoCloseable {
    val driver = new Driver
    var planReads = 0
    private val peer = e.root.resolve("peer")
    private val conflictPaths = if (repositoryScenario == "conflict") Set("one.scala") else Set.empty[String]
    private val remotePath = if (repositoryScenario == "conflict") "one.scala" else "remote.scala"
    private val initialAdd = if (conflictPaths.isEmpty) Vector.empty else
      Vector(Vector("-C", e.target.root.toString, "add", "--", "one.scala"))
    private val setupArgv = Vector(Vector("init", "--initial-branch=main", e.target.root.toString)) ++ initialAdd ++ Vector(
      Vector("-C", e.target.root.toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
        "commit", "--allow-empty", "-m", "initial owned fixture")) ++
      (if (repositoryMode.isEmpty) Vector.empty else Vector(
        Vector("init", "--bare", "--initial-branch=main", e.target.endpoint),
        Vector("-C", e.target.root.toString, "remote", "add", "origin", e.target.endpoint),
        Vector("-C", e.target.root.toString, "push", "--no-verify", "--", e.target.endpoint, "main:refs/heads/main"))) ++
      (if (repositoryScenario.isEmpty) Vector.empty else Vector(
        Vector("clone", "--no-local", "--", e.target.endpoint, peer.toString),
        Vector("-C", peer.toString, "add", "--", remotePath),
        Vector("-C", peer.toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
          "commit", "-m", "owned remote semantic change"),
        Vector("-C", peer.toString, "push", "--no-verify", "--", e.target.endpoint, "HEAD:refs/heads/main")))
    private val paths = if (candidate) (e.plan.steps.flatMap(_.ownedPaths) ++ extraCandidatePaths).distinct else Vector.empty
    private val validation = if (checkMode.isEmpty) None else Some(GoalPhaseNativeValidation.Policy("owned-checks",
      if (checkMode == "changed-policy") 2 else 1,
      Vector(GoalPhaseNativeValidation.Check(if (checkMode == "missing") "unselected-check" else "focused",
        if (checkMode == "changed-binding") 16 else 1)) ++
        (if (closureMode.nonEmpty) Vector(GoalPhaseNativeValidation.Check("focused-next", 2)) else Vector.empty) ++
        (if (checkMode == "unselected-checks") Vector(GoalPhaseNativeValidation.Check("unused-check", 16)) else Vector.empty)))
    private val phaseValidation = if (phaseMode.isEmpty) None else Some(GoalPhaseNativeValidation.Policy("owned-phase-checks",
      if (phaseMode == "changed-policy") 2 else 1,
      Vector(GoalPhaseNativeValidation.Check(if (phaseMode == "missing") "focused" else "whole-phase",
        if (phaseMode == "changed-binding") 16 else 3))))
    var independentReviewAdmissions = 0
    // Explicit host-owned test ingress, not a production proof of independence.
    private var admittedReviews = Map.empty[ContextReference, (String, String)]
    def registerReview(w: WorkflowInteraction[S.Request, Nothing]): Unit = {
      val input = w.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
      admittedReviews = admittedReviews.updated(ContextReference("owned-review-result", input.sourceRevision),
        (input.instanceIdentity, input.sourceRevision))
    }
    private val review = if (reviewMode.isEmpty) None else Some(GoalPhaseNativeReview.Installation(
      GoalPhaseNativeReview.Policy("owned-independent-review", if (reviewMode == "changed-policy") 2 else 1),
      new GoalPhaseNativeReview.EvidencePort {
        def admitC(core: ActionCall.Core, input: S.Request, submitted: ContinuationResult[S.Result]) = {
          independentReviewAdmissions += 1
          if (reviewMode != "deny" && core != null &&
              admittedReviews.get(submitted.resultReference).contains((input.instanceIdentity, input.sourceRevision)) &&
              submitted.evidence.workerIdentity.contains("independent-reviewer")) Consequence.unit
          else F.invalid("actual independent reviewer admission is absent in this test host")
        }
      }))
    private var closureAuthorities = Map.empty[(String, String), GoalPhaseNativeClosure.Authorization]
    def registerClosure(w: WorkflowInteraction[S.Request, Nothing], saved: Response): Unit = {
      val input = w.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
      val revision = take(WorkflowWaitJsonV1.decodeC(saved.print)).current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value
      val parent = input.completed.lastOption.map(s => O.Snapshot(s.commit.identity, s.commit.tree)).getOrElse(input.originalBase)
      val selected = GoalPhaseNativeClosure.Input(input.instanceIdentity, revision, input.selectedPlan.identity,
        input.selectedPlan.revision, input.stepIdentity.get, input.generation, parent, input.candidate.get, "owned-review-result")
      closureAuthorities = closureAuthorities.updated((input.instanceIdentity, revision),
        GoalPhaseNativeClosure.Authorization("owned-step-commit-authority", 1, selected))
    }
    private val closure = if (closureMode.isEmpty) None else Some(GoalPhaseNativeClosure.Installation(
      GoalPhaseNativeClosure.Policy("owned-step-commits", if (closureMode == "changed-policy") 2 else 1,
        e.plan.steps.zipWithIndex.map((s, i) => GoalPhaseNativeClosure.StepProgram(s.identity,
          if (closureMode == "changed-binding") i + 2 else i + 1)) ++
          (if (closureMode == "unselected-program") Vector(GoalPhaseNativeClosure.StepProgram("unused-Step", 64)) else Vector.empty)),
      new GoalPhaseNativeClosure.AuthorityPort {
        def loadForCallC(core: ActionCall.Core, input: GoalPhaseNativeClosure.Input) =
          Consequence.success(if (closureMode == "deny") None else closureAuthorities.get((input.instance, input.sourceRevision))
            .map(a => if (closureMode == "crossed") a.copy(input = a.input.copy(step = "different-Step")) else a))
      }))
    private val repositoryDeclaration = RepositorySyncManagedFetch.Declaration(config, e.declaration.instances,
      e.declaration.continuations, RepositorySyncStartInput(e.input.repositoryIdentity, "main", "origin", "main",
        "owned-phase-remote", "FULL_STATE", "policy-v1"), e.target)
    private var repositoryAuthorities = Map.empty[(String, String), GoalPhaseNativeRepositoryClosure.Authorization]
    private var repositorySelection = Option.empty[ProfileStartAdmission.Selection]
    private var childReviewAdmissions = Map.empty[ContextReference, (String, String)]
    private var humanAdmissions = Map.empty[ContextReference, Json]
    var independentHumanAdmissions = 0
    def registerChildHuman(question: WorkflowInteraction[RepositorySyncHumanConflict.Input, Nothing],
      answer: ContinuationResult[RepositorySyncHumanConflict.Answer]): Unit = {
      val attestation = take(ThreeWorkflowHostEvidencePorts.repositoryHumanBindingC(RepositorySyncHumanConflict.AnswerContext(
        component.core.instanceId, question, answer, e.command.participantIdentity)))
      humanAdmissions = humanAdmissions.updated(answer.resultReference, attestation)
    }
    private val childHuman = if (!repositoryHuman) None else Some(RepositorySyncHumanConflict.Installation(
      new RepositorySyncHumanConflict.EvidencePort {
        def admitC(core: ActionCall.Core, reference: ContextReference, target: Json) = {
          independentHumanAdmissions += 1
          val lookup = ThreeWorkflowHostEvidencePorts.key(reference.identity, reference.revision)
          val observed = humanAdmissions.get(reference).map { actual =>
              val now = core.executionContext.clock.instant()
              NativeHostEvidence.Evidence(NativeHostEvidence.schema, RepositorySyncHumanConflict.purpose, lookup,
                reference.identity, reference.revision, "owned-independent-human-channel", "owned-human", actual, now.minusSeconds(1).toString)
          }
          NativeHostEvidenceFixtureAdmission.admitC(core, "owned-independent-human-channel", NativeHostEvidence.Purpose.RepositoryHuman,
            lookup, target, observed)
        }
      }))
    def registerChildReview(w: WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing]): Unit = {
      val input = w.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request.input.get.value
      childReviewAdmissions = childReviewAdmissions.updated(ContextReference("owned-child-review", input.validatedRevision),
        (input.instanceIdentity, input.validatedRevision))
    }
    private val childReview = if (repositoryScenario.isEmpty) None else Some(RepositorySyncNativeMergeReview.Installation(
      RepositorySyncNativeMergeReview.Policy("owned-child-independent-review", 1),
      new RepositorySyncNativeMergeReview.EvidencePort {
        def admitC(core: ActionCall.Core, input: RepositorySyncNativeMergeReview.Request,
          submitted: ContinuationResult[RepositorySyncNativeMergeReview.Result]) =
          if (core != null && core.action.request.operation == RepositorySyncNativeMergeReviewContinuation.completionOperation.operation &&
              childReviewAdmissions.get(submitted.resultReference).contains((input.instanceIdentity, input.validatedRevision)) &&
              submitted.evidence.workerIdentity.contains("independent-child-reviewer")) Consequence.unit
          else F.invalid("child reviewer admission is absent for this actual native candidate")
      }))
    def registerRepository(w: WorkflowInteraction[S.Request, Nothing], accepted: Response): Unit = {
      val input = w.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
      val revision = take(WorkflowWaitJsonV1.decodeC(accepted.print)).current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value
      val key = input.instanceIdentity + ":repository-closure:" + revision
      val selected = GoalPhaseNativeRepositoryClosure.Input(input.instanceIdentity, revision, input.selectedPlan,
        input.originalBase, input.completed, input.candidate.get, "owned-review-result", key)
      repositoryAuthorities = repositoryAuthorities.updated((input.instanceIdentity, revision),
        GoalPhaseNativeRepositoryClosure.Authorization("owned-repository-authority", 1,
          if (repositoryMode == "crossed") selected.copy(review = "different-review")
          else if (repositoryAuthorityPresentation) selected.copy(plan = selected.plan.copy(
            steps = selected.plan.steps.map(_.copy(purpose = "Authority presentation of the same target"))),
            completed = selected.completed.map(_.copy(review = "Authority presentation of the same commit")))
          else selected))
      repositorySelection = Some(ProfileStartAdmission.Selection("selected-phase-repository", RepositorySyncManagedFetch.startOperation,
        WorkflowDefinitionIdentity("RepositorySyncWorkflow"), WorkflowDefinitionRevision("repository-sync-native-convergence-v10"),
        e.command.participantIdentity, ProfileStartAdmission.SelectionOrigin.ExplicitHuman, key, None,
        Some(TypedValue("RepositorySyncStartInput", take(io.circe.parser.parse(RepositorySyncStartServiceOperation.encodeCommand(
          RepositorySyncStartCommand(repositoryDeclaration.input, "selected-phase-repository", e.command.participantIdentity, key)))
          .fold(_ => F.invalid("owned child command JSON"), Consequence.success)).hcursor.downField("input").focus.get))))
    }
    private val repository = if (repositoryMode.isEmpty) None else Some(GoalPhaseNativeRepositoryClosure.Installation(
      repositoryDeclaration, repositoryInstallationSelection, new ProfileStartAdmission.CallScopedSelectionPort {
        def loadForCallC(id: String, core: ActionCall.Core) = Consequence.success(repositorySelection.filter(_.selectionId == id))
      }, new GoalPhaseNativeRepositoryClosure.AuthorityPort {
        def loadForCallC(core: ActionCall.Core, input: GoalPhaseNativeRepositoryClosure.Input) =
          Consequence.success(if (repositoryMode == "deny") None else repositoryAuthorities.get((input.instance, input.sourceRevision)))
      }, RepositorySyncNativeMergeValidation.Policy("owned-repository-checks", 1, "required", "Validate actual final integration",
        Vector(RepositorySyncNativeMergeValidation.Check("repository-check", 4))),
      new RepositorySyncNativeConvergence.AuthorityPort {
        def admitC(core: ActionCall.Core, originalCommand: io.circe.Json, target: Programs.Target,
          effect: RepositorySyncNativeConvergence.Effect): Consequence[Unit] =
          if (repositoryMode != "denied-effect" && target == e.target) Consequence.unit
          else F.invalid("selected fixture remote effect is not authorized")
      }, conflicts = RepositorySyncNativeConflictInput.Policy(conflictPaths), review = childReview, humanConflict = childHuman))
    private val declaration = e.declaration.copy(candidatePaths = paths, validationPolicy = validation,
      reviewInstallation = review, closureInstallation = closure, phaseValidationPolicy = phaseValidation, repositoryClosure = repository)
    private val checkPrograms = ((validation.toVector ++ phaseValidation.toVector).flatMap(_.checks) ++
      (if (repository.nonEmpty) Vector(GoalPhaseNativeValidation.Check("repository-check", 4)) else Vector.empty)).map { check =>
      val capability = take(ProcessCapabilityId.parseC("sm-workflow-repository-validation-" + check.ordinal))
      val args = Vector("--no-optional-locks", "--no-replace-objects", "-C", e.target.root.toString,
        "-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false", "-c", "credential.helper=") ++
        (if (checkMode == "fail" || (check.ordinal == 3 && phaseMode == "fail") || (check.ordinal == 4 && repositoryMode == "failed-check"))
          Vector("rev-parse", "--verify", "refs/heads/absent-owned-test-reference")
          else Vector("diff", "--cached", "--no-ext-diff", "--no-textconv", "--check", "--"))
      take(ProcessProgramDefinition.fromRuntimeC(capability, capability.print, "/usr/bin/git", args,
        ProcessArgumentPolicy(args, permittedArgumentVectors = Set(Vector.empty)), limits, Set.empty,
        environment = Map("PATH" -> "/usr/bin:/bin", "GIT_CONFIG_GLOBAL" -> "/dev/null", "GIT_CONFIG_NOSYSTEM" -> "1")))
    }
    private val conflictPrograms = if (conflictPaths.isEmpty) Vector.empty else {
      val classpath = Files.readAllLines(Path.of("target/cncf.d/runtime-classpath.txt")).asScala
        .map(_.trim).filter(_.nonEmpty).mkString(java.io.File.pathSeparator)
      take(org.simplemodeling.textus.smworkflow.runtime.RepositoryConflictFileProgram.definitionsC(e.target.root,
        conflictPaths, 65536, Path.of(System.getProperty("java.home"), "bin/java").toString, classpath, limits))
    }
    private val programs = take(Programs.definitionsC(e.target, "/usr/bin/git", limits, Map("PATH" -> "/usr/bin:/bin"),
      conflictPaths = conflictPaths,
      goalPaths = paths,
      commitIdentity = if (closureMode.nonEmpty) Some(Programs.CommitIdentity("Fixture", "fixture@example.invalid")) else None,
      goalCommitMessages = if (closureMode.nonEmpty) e.plan.steps.map(s => "Owned fixture Step " + s.identity) else Vector.empty)) ++ checkPrograms ++ conflictPrograms ++
      setupArgv.zipWithIndex.map { (args, index) =>
        val capability = take(ProcessCapabilityId.parseC("ordered-goal-setup-" + index))
        take(ProcessProgramDefinition.fromRuntimeC(capability, capability.print, "/usr/bin/git", args,
          ProcessArgumentPolicy(args, permittedArgumentVectors = Set(Vector.empty)), limits, Set.empty,
          environment = Map("PATH" -> "/usr/bin:/bin", "GIT_CONFIG_GLOBAL" -> "/dev/null", "GIT_CONFIG_NOSYSTEM" -> "1")))
      }
    private val admission = take(ProcessExecutionAdmission.createC(take(ProcessExecutionPolicy.createC(programs)),
      programs.map(p => ProcessExecutionGrant(p.capability))))
    GlobalContext.set(GlobalContext(WorkAreaSpace.create(RuntimeConfig.default)))
    private val ctx = ExecutionContext.create()
    private val configured = ResolvedConfiguration(HostConfiguration(Map[String, ConfigurationValue](
      "textus.subsystem.user-mode" -> ConfigurationValue.StringValue("standalone"),
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(e.root.resolve("workflow.sqlite").toString),
      GoalPhaseHostRecordReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
      GoalPhaseHostRecordReadPolicy.prefix + "selection-collection" -> ConfigurationValue.StringValue(collection("selections").print),
      GoalPhaseHostRecordReadPolicy.prefix + "association-collection" -> ConfigurationValue.StringValue(collection("associations").print),
      GoalPhaseHostRecordReadPolicy.prefix + "authorization-collection" -> ConfigurationValue.StringValue(collection("authorizations").print),
      GoalPhaseHostRecordReadPolicy.prefix + "decision-collection" -> ConfigurationValue.StringValue(collection("decisions").print),
      GoalPhaseNativeInputReadPolicy.planKey -> ConfigurationValue.StringValue(collection("plans").print))), ConfigurationTrace.empty)
    private val policy = take(EventPersistencePolicy.createC("ordered-goal-fixture", Vector(
      EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
    private val storage = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"), collection("events"),
      collection("indexes"), Vector(EventStorePersistenceDeclaration.WorkflowGrant(SmWorkflowComponent.componentId,
        "workflow", e.declaration.instances, e.declaration.continuations))))
    private val bootstrap = take(SubsystemEventStoreBootstrap.createC(policy, storage,
      ScopeContext(ScopeKind.Subsystem, "ordered-goal-host", None, ctx.observability,
        processexecutiondriveroption = Some(driver), processexecutionadmissionoption = Some(admission)), configured,
      None, org.goldenport.cncf.path.AliasResolver.empty))
    val owner = RuntimeBindingAdmissionFixture.admit(bootstrap.subsystem)
    private val selections = new ProfileSelectionDataStorePort("workflow", collection("selections"))
    private val planReader = new GoalPhaseSelectedPlanDataStorePort("workflow", collection("plans"))
    // A counter around the real reader observes whether Start reselects data.
    // It neither chooses a Plan nor supplies fixture data to production code.
    private val plans = new F.PlanPort {
      def loadForCallC(id: String, core: ActionCall.Core) = {
        planReads += 1
        planReader.loadForCallC(id, core)
      }
    }
    private val setup = new spec.OperationDefinition {
      val specification = spec.OperationDefinition.Specification(name = "fixturePrepare",
        request = WorkflowJsonCommandRequest.definition, response = spec.ResponseDefinition.void)
      def createOperationRequest(r: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
        val request = r
        override def commandExecutionMode = CommandExecutionMode.Sync
        def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
          val core = c
          def execute(): Consequence[OperationResponse] = setupArgv.indices.foldLeft(Consequence.unit) { (prior, index) =>
            prior.flatMap { _ =>
              if (setupArgv(index) == Vector("-C", e.target.root.toString, "add", "--", "one.scala"))
                Files.writeString(e.target.root.resolve("one.scala"), "object OriginalShared\n")
              if (setupArgv(index) == Vector("-C", peer.toString, "add", "--", remotePath))
                Files.writeString(peer.resolve(remotePath), "object RemoteChange\n")
              executeProgram(process_exec(ProcessExecutionRequest(take(ProcessCapabilityId.parseC("ordered-goal-setup-" + index)))))
                .flatMap(r => if (r.termination == ProcessExecutionTermination.Exited(0)) Consequence.unit else F.invalid("owned fixture setup failed"))
            }
          }.flatMap { _ =>
            // Only fixture preparation installs selected host inputs. Neither
            // the reader nor a physical-reopen Host has an installer side effect.
            given ExecutionContext = executionContext
            val space = executionContext.dataStoreSpace
            val prior = space.captureBinding()
            try for {
              _ <- bind_component_application_datastore_c("workflow")
              selectionStore <- space.dataStore(collection("selections"))
              foundSelection <- selectionStore.load(collection("selections"), DataStore.StringEntryId(e.command.selectionIdentity))
              _ <- if (foundSelection.nonEmpty) Consequence.unit else ProfileSelectionJsonV1.encodeStorageC(e.selection).flatMap(raw =>
                selectionStore.create(collection("selections"), DataStore.StringEntryId(e.command.selectionIdentity),
                  Record.data(ProfileSelectionJsonV1.storageField -> raw)))
              planStore <- space.dataStore(collection("plans"))
              foundPlan <- planStore.load(collection("plans"), DataStore.StringEntryId(e.command.selectionIdentity))
              _ <- if (foundPlan.nonEmpty || missingPlan) Consequence.unit else GoalPhaseSelectedPlanJsonV1.encodeStorageC(
                F.SelectedPlan(e.command.selectionIdentity, e.command.participantIdentity, e.input, selected.getOrElse(e.plan))).flatMap(raw =>
                  planStore.create(collection("plans"), DataStore.StringEntryId(e.command.selectionIdentity),
                    Record.data(GoalPhaseSelectedPlanJsonV1.storageField -> raw)))
            } yield OperationResponse.Scalar("fixture-prepared")
            finally space.restoreBinding(prior)
          }
        }
      })
    }
    private val factory = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty, (creation, _, _) =>
      (if (normalBinding) A.bindHostC(creation, declaration, acknowledgement)
        else A.bindC(creation, declaration, selections, plans, acknowledgement)).map(s => spec.ServiceDefinition("GoalPhaseService",
        spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(s.operations.operations.toVector :+ setup)))),
      additionalBindings = if (repositoryMode == "missing-install") Vector.empty else repository.toVector.map(r =>
        (creation, _, _) => r.bindServiceC(creation, acknowledgement))).primaryFactory
    private val component = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
    owner.add(component)
    owner.withDescriptor(GenericSubsystemDescriptor(path = Path.of("<ordered-goal-spec>"), subsystemName = "ordered-goal",
      componentBindings = Vector(GenericSubsystemComponentBinding(component.name)), security = Some(GenericSubsystemSecurityBinding(
        authentication = Some(GenericSubsystemAuthenticationBinding(convention = Some("enabled"), fallbackPrivilege = Some("disabled"),
          localSubject = Some(GenericSubsystemLocalSubjectBinding(id = actor, roles = Vector("user"), capabilities = Vector("user"),
            attributes = Map.empty, securityLevel = Some("user")))))))))
    def invoke(name: String, wire: String): Consequence[Response] = new CncfRuntime().executeCommandResponse(owner,
      Array(s"${component.name}/GoalPhaseService/$name", "--json", wire))
    def prepare(): Unit = { take(invoke("fixturePrepare", "{}")); () }
    def start() = invoke("startGoalPhase", GoalPhaseStartServiceOperation.encodeCommand(e.command))
    def advance() = invoke("advanceGoalPhase", A.encodeAdvance(e.identity))
    def complete(result: ContinuationResult[S.Result]) = invoke("submitGoalPhaseWorkResult", take(WorkflowResultJsonV1.encodeC(result, S.resultCodec)))
    def invokeChild(name: String, wire: String): Consequence[Response] = new CncfRuntime().executeCommandResponse(owner,
      Array(s"${component.name}/RepositorySyncService/$name", "--json", wire))
    def completeChildReview(result: ContinuationResult[RepositorySyncNativeMergeReview.Result]) =
      invokeChild(RepositorySyncNativeMergeReviewContinuation.completionOperation.operation,
        take(WorkflowResultJsonV1.encodeC(result, RepositorySyncNativeMergeReview.resultCodec)))
    def completeChildConflict(result: ContinuationResult[RepositorySyncConflictSemantic.Result]) =
      invokeChild(RepositorySyncNativeConflictContinuation.completionOperation.operation,
        take(WorkflowResultJsonV1.encodeC(result, RepositorySyncConflictSemantic.resultCodec)))
    def completeChildHuman(result: ContinuationResult[RepositorySyncHumanConflict.Answer]) =
      invokeChild(RepositorySyncHumanConflict.completionOperation.operation,
        take(WorkflowResultJsonV1.encodeC(result, RepositorySyncHumanConflict.answerCodec)))
    def close(): Unit = try take(bootstrap.shutdownC()) finally driver.close()
  }
  private def work(response: Response) = take(WorkflowWorkOrderJsonV1.decodeC(response.print, S.inputCodec))
  private def result(w: WorkflowInteraction[S.Request, Nothing], outcome: String = "completed") = {
    val order = w.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
    val input = order.input.get.value
    val ref = if (input.kind == "RepairStep") ContextReference("owned-repair-result", input.sourceRevision)
      else ContextReference("owned-implementation-result", if (input.generation == 1) "1" else input.sourceRevision)
    ContinuationResult(w.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
      TypedValue(S.resultCodec.typeIdentity, S.Result(outcome, "Only the assigned Step semantics\nwith explicit remaining work", Vector.empty)),
      ref, Vector.empty, ExecutionEvidence(Vector(ref), Some("participant")))
  }
  private def reviewResult(w: WorkflowInteraction[S.Request, Nothing], outcome: String = "accept") = {
    val order = w.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
    val ref = ContextReference("owned-review-result", order.input.get.value.sourceRevision)
    ContinuationResult(w.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
      TypedValue(S.resultCodec.typeIdentity, S.Result(outcome, "Independent review of only the assigned candidate",
        if (outcome == "repair") Vector("Repair the assigned first-Step boundary") else Vector.empty)),
      ref, Vector.empty, ExecutionEvidence(Vector(ref), Some("independent-reviewer")))
  }
  /** Actual owned Git commits, not injected ClosedStep or Phase-check receipts. */
  private def closeSteps(h: Host, e: Episode, count: Int): Consequence[Response] = {
    h.prepare(); take(h.start())
    var assigned = work(take(h.advance()))
    var next: Consequence[Response] = null
    for (index <- 0 until count) {
      val input = assigned.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
      val step = input.selectedPlan.steps(index)
      input.kind shouldBe "ImplementStep"
      input.stepIdentity shouldBe Some(step.identity)
      Files.writeString(e.target.root.resolve(step.ownedPaths.head), "object Owned" + step.identity + "\n")
      take(h.complete(result(assigned)))
      val review = work(take(h.advance())); h.registerReview(review)
      val accepted = take(h.complete(reviewResult(review))); h.registerClosure(review, accepted)
      next = h.advance()
      if (index + 1 < count) assigned = work(take(next))
    }
    next
  }
  private def childReviewWork(response: Response) = take(WorkflowWorkOrderJsonV1.decodeC(response.print,
    RepositorySyncNativeMergeReview.inputCodec))
  private def childConflictWork(response: Response) = take(WorkflowWorkOrderJsonV1.decodeC(response.print,
    RepositorySyncConflictSemantic.inputCodec))
  private def childHumanQuestion(response: Response) = take(WorkflowDecisionJsonV1.decodeC(response.print,
    RepositorySyncHumanConflict.inputCodec))
  private def childHumanAnswer(question: WorkflowInteraction[RepositorySyncHumanConflict.Input, Nothing],
    choice: String = "ProvideResolution"): ContinuationResult[RepositorySyncHumanConflict.Answer] = {
    val order = question.current.asInstanceOf[WorkflowContinuation.Decision[RepositorySyncHumanConflict.Input]].request
    val input = order.input.get.value
    ContinuationResult(question.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
      TypedValue(RepositorySyncHumanConflict.answerCodec.typeIdentity, RepositorySyncHumanConflict.Answer(choice,
        if (choice == "ProvideResolution") input.conflict.conflicts.map(c => RepositorySyncConflictSemantic.Resolution(c.identity,
          "object OwnedS1\nobject RemoteChange\n")) else Vector.empty, "actual enrolled Human fixture direction")),
      ContextReference("owned-human-answer", order.expectedRevision.value), Vector.empty,
      ExecutionEvidence(Vector(ContextReference("owned-human-answer", order.expectedRevision.value)), Some("owned-human")))
  }
  private def childReviewResult(w: WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing],
    decision: String = "accept"): ContinuationResult[RepositorySyncNativeMergeReview.Result] = {
    val order = w.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request
    val input = order.input.get.value
    ContinuationResult(w.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
      TypedValue(RepositorySyncNativeMergeReview.resultCodec.typeIdentity,
        RepositorySyncNativeMergeReview.Result(decision, "independent child integration disposition", Vector.empty)),
      ContextReference("owned-child-review", input.validatedRevision), Vector.empty,
      ExecutionEvidence(Vector(ContextReference("owned-child-review", input.validatedRevision)), Some("independent-child-reviewer")))
  }
  private def childConflictResult(w: WorkflowInteraction[RepositorySyncNativeConflictInput.Request, Nothing],
    decision: String = "resolved"): ContinuationResult[RepositorySyncConflictSemantic.Result] = {
    val order = w.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].request
    val input = order.input.get.value
    ContinuationResult(w.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
      TypedValue(RepositorySyncConflictSemantic.resultCodec.typeIdentity, RepositorySyncConflictSemantic.Result(decision,
        if (decision == "resolved") input.conflicts.map(c => RepositorySyncConflictSemantic.Resolution(c.identity,
          "object OwnedS1\nobject RemoteChange\n")) else Vector.empty, "only the assigned native child conflict")),
      ContextReference("owned-child-conflict", input.conflictRevision), Vector.empty,
      ExecutionEvidence(Vector(ContextReference("owned-child-conflict", input.conflictRevision)), Some("participant")))
  }
  "GoalPhase native ordered entry and paired work" should {
    "retain classified native frontiers through normal Start advance completion and physical reopen" in {
      Given("Spec: docs/spec/goal-phase-work-classification.md; Rule: R5; Example: E6; a classified selected Plan and the existing normal host binder")
      import GoalPhaseWorkClassification.*
      val e = episode()
      val implementation = Classification(Kind.CODING, Complexity.TRIVIAL, Domain.PROGRAMMING, Footprint.SMALL)
      val repair = implementation.copy(complexity = Complexity.COMPLEX)
      val independent = Classification(Kind.REVIEW, Complexity.STANDARD, Domain.ENGINEERING, Footprint.SMALL)
      val selected = e.plan.copy(repairLimit = 1,
        steps = e.plan.steps.map(_.copy(workClassification = Some(Assignment(implementation, repair, independent)))),
        phaseReviewClassification = Some(independent.copy(footprint = Footprint.LARGE)))
      def order(w: WorkflowInteraction[S.Request, Nothing]) =
        w.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]]
      def requirement(w: WorkflowInteraction[S.Request, Nothing], kind: String, level: ReasoningLevel, name: String): Unit = {
        val actual = order(w)
        actual.request.input.get.value.kind shouldBe kind
        actual.request.input.get.value.selectedPlan shouldBe selected
        actual.requirement.reasoning shouldBe level
        actual.requirement.reasoningClass shouldBe Some(ReasoningClassReference(name))
        actual.requirement.reviewRequired shouldBe kind.startsWith("Review")
        // Semantic issue is not an inferred executor/profile observation.
        actual.requirement.disposition shouldBe None
      }
      var issued: WorkflowInteraction[S.Request, Nothing] = null
      val first = new Host(e, selected = Some(selected), candidate = true, checkMode = "pass",
        reviewMode = "allow", closureMode = "allow", phaseMode = "pass", normalBinding = true)
      try {
        first.prepare()
        When("public Start and owning advance read the real saved classification and issue implementation")
        take(first.start()); issued = work(take(first.advance()))
        Then("the native issued requirement contains CodingProgrammingRoutine without granting placement or skipping checks")
        requirement(issued, "ImplementStep", ReasoningLevel.Routine, "CodingProgrammingRoutine")
        first.driver.count("sm-workflow-repository-validation-1") shouldBe 0
        first.driver.count("sm-workflow-git-goal-commit-step-1") shouldBe 0
        first.driver.count("sm-workflow-git-goal-commit-step-2") shouldBe 0
      } finally first.close()
      Given("a physically reopened normal host with no installer or current Plan and the original classified issue")
      val later = new Host(e, missingPlan = true, candidate = true, checkMode = "pass",
        reviewMode = "allow", closureMode = "allow", phaseMode = "pass", normalBinding = true)
      try {
        When("advance reads the retained issue and completion advances only through actual staging and checks")
        val retained = work(take(later.advance()))
        retained shouldBe issued
        Files.writeString(e.target.root.resolve("one.scala"), "object ClassifiedInitial\n")
        take(later.complete(result(retained)))
        val review = work(take(later.advance()))
        Then("the next native requirement is ReviewStandard and actual validation has run once")
        requirement(review, "ReviewStep", ReasoningLevel.Standard, "ReviewStandard")
        later.driver.count("sm-workflow-repository-validation-1") shouldBe 1
        When("independently enrolled fixture review requests the assigned repair")
        later.registerReview(review); take(later.complete(reviewResult(review, "repair")))
        val assignedRepair = work(take(later.advance()))
        Then("repair requests its distinct CodingProgrammingDeep class and retains the selected one-round allowance")
        requirement(assignedRepair, "RepairStep", ReasoningLevel.Deep, "CodingProgrammingDeep")
        order(assignedRepair).request.input.get.value.selectedPlan.repairLimit shouldBe 1
        When("repair completes and fresh checks plus independently enrolled review authorize only Step closure")
        Files.writeString(e.target.root.resolve("one.scala"), "object ClassifiedRepaired\n")
        take(later.complete(result(assignedRepair)))
        val freshReview = work(take(later.advance()))
        requirement(freshReview, "ReviewStep", ReasoningLevel.Standard, "ReviewStandard")
        later.registerReview(freshReview)
        val accepted = take(later.complete(reviewResult(freshReview)))
        later.registerClosure(freshReview, accepted)
        val second = work(take(later.advance()))
        Then("only S1 closes and the same classified Plan supplies S2 implementation")
        requirement(second, "ImplementStep", ReasoningLevel.Routine, "CodingProgrammingRoutine")
        order(second).request.input.get.value.stepIdentity shouldBe Some("S2")
        order(second).request.input.get.value.completed.map(_.step) shouldBe Vector("S1")
        When("S2 obtains its own actual checks independent fixture review and authorized native commit")
        Files.writeString(e.target.root.resolve("two.scala"), "object ClassifiedSecond\n")
        take(later.complete(result(second)))
        val secondReview = work(take(later.advance()))
        requirement(secondReview, "ReviewStep", ReasoningLevel.Standard, "ReviewStandard")
        later.registerReview(secondReview)
        val secondAccepted = take(later.complete(reviewResult(secondReview)))
        later.registerClosure(secondReview, secondAccepted)
        val whole = work(take(later.advance()))
        Then("whole-Phase review uses its retained ReviewCritical class and remains pending rather than Terminal")
        requirement(whole, "ReviewPhase", ReasoningLevel.Critical, "ReviewCritical")
        order(whole).request.input.get.value.completed.map(_.step) shouldBe Vector("S1", "S2")
        later.driver.count("sm-workflow-repository-validation-1") shouldBe 2
        later.driver.count("sm-workflow-repository-validation-2") shouldBe 1
        later.driver.count("sm-workflow-repository-validation-3") shouldBe 1
        later.driver.count("sm-workflow-git-goal-commit-step-1") shouldBe 1
        later.driver.count("sm-workflow-git-goal-commit-step-2") shouldBe 1
        later.driver.count("sm-workflow-git-push") shouldBe 0
        later.planReads shouldBe 0
      } finally later.close()
    }

    "bind normal persistent inputs and retain the actual selected Plan after physical reopen" in {
      Given("an owned native Git repository with selected input rows installed only during private fixture preparation")
      val e = episode(); val first = new Host(e, normalBinding = true)
      var issued: WorkflowInteraction[S.Request, Nothing] = null
      try {
        first.prepare()
        When("the normal host binder serves public Start and owning advance")
        take(first.start())
        issued = work(take(first.advance()))
        Then("the actual saved selection and complete Plan determine the first assigned work")
        issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value.selectedPlan shouldBe e.plan
      } finally first.close()
      Given("a new normal host over the same SQLite database with no installer invocation")
      val later = new Host(e, missingPlan = true, normalBinding = true)
      try {
        When("duplicate public Start observes the retained native history")
        val duplicate = take(WorkflowWaitJsonV1.decodeC(take(later.start()).print))
        Then("Start retains the original instance without recapturing its Git base")
        duplicate.handle.instanceIdentity shouldBe issued.handle.instanceIdentity
        later.driver.count("sm-workflow-git-head") shouldBe 0
        later.driver.count("sm-workflow-git-root") shouldBe 0
        When("owning advance admits the current native root and branch and reads the retained work")
        val retained = work(take(later.advance()))
        Then("the original issued work survives, with only the existing root/branch admission and no new Git base capture")
        retained shouldBe issued
        later.driver.count("sm-workflow-git-head") shouldBe 0
        later.driver.count("sm-workflow-git-root") shouldBe 1
        later.driver.count("sm-workflow-git-branch") shouldBe 1
      } finally later.close()
    }

    "save the whole selected plan and issue only its first assigned Step without command work in the result" in {
      Given("an owned clean native Git repository and genuinely selected fixture two-Step plan")
      val e = episode(); val h = new Host(e)
      try {
        h.prepare()
        When("Start and owning advance execute through the actual CLI service")
        val start = take(WorkflowWaitJsonV1.decodeC(take(h.start()).print))
        val issued = work(take(h.advance()))
        val input = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
        Then("the native issue carries all Steps and only S1 is assigned")
        input.selectedPlan shouldBe e.plan
        input.stepIdentity shouldBe Some("S1")
        input.originalBase.head should fullyMatch regex "[0-9a-f]{40}"
        val initialWire = S.inputCodec.encode(input)
        initialWire.hcursor.downField("payload").downField("review").succeeded shouldBe false
        S.inputCodec.decode(initialWire) shouldBe Right(input)
        val undeclared = initialWire.mapObject(_.add("payload",
          initialWire.hcursor.downField("payload").focus.get.mapObject(_.add("command", io.circe.Json.fromString("git")))))
        S.inputCodec.decode(undeclared).isLeft shouldBe true
        issued.handle shouldBe start.handle
        work(take(h.advance())) shouldBe issued
        h.planReads shouldBe 1
        When("the assigned semantic result is returned through its paired completion")
        val saved = take(WorkflowWaitJsonV1.decodeC(take(h.complete(result(issued))).print))
        Then("completion is nonterminal and does not run staging, checks, commit or push")
        saved.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value shouldBe "4"
        take(WorkflowWaitJsonV1.decodeC(take(h.advance()).print)) shouldBe saved
        h.driver.count("sm-workflow-git-stage-full-state") shouldBe 0
        h.driver.count("sm-workflow-git-commit-pending") shouldBe 0
        h.driver.count("sm-workflow-git-push") shouldBe 0
        h.driver.count("sm-workflow-repository-validation-1") shouldBe 0
        h.driver.count("sm-workflow-git-head") shouldBe 1
      } finally h.close()
    }
    "reopen pending and completed native work without reselecting a plan or recapturing the initial base" in {
      Given("a saved ordered Start and actual pending implementation issue")
      val e = episode(); val first = new Host(e)
      var issued: WorkflowInteraction[S.Request, Nothing] = null
      try { first.prepare(); take(first.start()); issued = work(take(first.advance())) } finally first.close()
      Given("a physically reopened SQLite host retaining the original selected Plan row without an installer")
      val later = new Host(e, selected = Some(e.plan.copy(revision = "2")))
      try {
        When("owning advance reads its saved actual pending work and completes it")
        val retained = work(take(later.advance()))
        val saved = take(later.complete(result(retained)))
        Then("the original complete plan and base are retained, with no new selection or initial Git capture")
        retained shouldBe issued
        later.planReads shouldBe 0
        later.driver.count("sm-workflow-git-head") shouldBe 0
        take(WorkflowWaitJsonV1.decodeC(saved.print)).current shouldBe a[WorkflowContinuation.Wait]
      } finally later.close()
      Given("a second physical host reopen after the actual native completion")
      val completed = new Host(e, missingPlan = true)
      try {
        When("owning advance observes the actual retained completion")
        val saved = take(WorkflowWaitJsonV1.decodeC(take(completed.advance()).print))
        Then("it observes revision4 without issuing new work or relabeling a Phase Terminal")
        saved.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value shouldBe "4"
        completed.planReads shouldBe 0
        completed.driver.count("sm-workflow-git-head") shouldBe 0
      } finally completed.close()
    }
    "refuse crossed results and review acceptance while retaining the original pending implementation" in {
      Given("an actual native issued implementation")
      val e = episode(); val h = new Host(e)
      try {
        h.prepare(); take(h.start()); val issued = work(take(h.advance())); val r = result(issued)
        When("submitted results substitute continuation revision or claim review acceptance")
        val crossed = h.complete(r.copy(expectedRevision = StateMachineRevision("99")))
        val review = h.complete(result(issued, "accept"))
        Then("both fail without consuming the original issue or changing its frontier")
        crossed.isSuccess shouldBe false
        review.isSuccess shouldBe false
        work(take(h.advance())) shouldBe issued
        When("the actual assigned completion then returns its result")
        val saved = h.complete(r)
        Then("ordinary completion remains available and does not close a Step or Phase")
        saved.isSuccess shouldBe true
        h.driver.count("sm-workflow-git-commit-pending") shouldBe 0
      } finally h.close()
    }
    "refuse missing or wrong-Phase plans and foreign actors before initial Git inspection" in {
      Vector("missing", "crossed-plan", "foreign-actor").foreach { mode =>
        Given("an owned native repository with a " + mode + " host selection")
        val e = episode(); val h = new Host(e, actor = if (mode == "foreign-actor") "foreign" else "participant",
          selected = if (mode == "crossed-plan") Some(e.plan.copy(phase = "PHASE-3")) else None, missingPlan = mode == "missing")
        try {
          h.prepare()
          When("ordered Start attempts its ordinary native admission")
          val failed = h.start()
          Then("it refuses without inspecting or changing selected Git material")
          failed.isSuccess shouldBe false
          h.driver.count("sm-workflow-git-head") shouldBe 0
          h.driver.count("sm-workflow-git-stage-full-state") shouldBe 0
        } finally h.close()
      }
    }
    "inspect and stage only assigned first-Step paths through owning advance and retain the native candidate on reopen" in {
      Given("a native first-Step issue and host-installed fixed staging programs for the saved complete plan")
      val e = episode(); val first = new Host(e, candidate = true)
      var inspected: WorkflowInteraction[Nothing, Nothing] = null
      try {
        first.prepare(); take(first.start()); val issued = work(take(first.advance()))
        Files.writeString(e.target.root.resolve("one.scala"), "object FirstBoundary\n")
        When("semantic completion saves its result and owning advance performs native inspection")
        take(first.complete(result(issued)))
        first.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 0
        inspected = take(WorkflowWaitJsonV1.decodeC(take(first.advance()).print))
        Then("the fixed first path is staged once and native tree/diff are observed without checks, commit, push or another AI issue")
        inspected.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value shouldBe "8"
        first.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 1
        first.driver.count("sm-workflow-git-stage-goal-path-2") shouldBe 0
        first.driver.count("sm-workflow-git-stage-full-state") shouldBe 0
        first.driver.count("sm-workflow-git-index-tree") shouldBe 1
        first.driver.count("sm-workflow-git-pending-diff") shouldBe 1
        first.driver.count("sm-workflow-git-pending-paths") shouldBe 1
        first.driver.count("sm-workflow-git-commit-pending") shouldBe 0
        first.driver.count("sm-workflow-git-push") shouldBe 0
        first.driver.count("sm-workflow-repository-validation-1") shouldBe 0
        take(WorkflowWaitJsonV1.decodeC(take(first.advance()).print)) shouldBe inspected
        first.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 1
        first.driver.count("sm-workflow-git-pending-diff") shouldBe 1
      } finally first.close()
      Given("a physically reopened SQLite host without a fresh plan selection")
      val later = new Host(e, missingPlan = true, candidate = true)
      try {
        When("owning advance observes the saved actual candidate")
        val saved = take(WorkflowWaitJsonV1.decodeC(take(later.advance()).print))
        Then("the same native Validation frontier is retained without recapturing diffs or replaying staging")
        saved shouldBe inspected
        later.planReads shouldBe 0
        later.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 0
        later.driver.count("sm-workflow-git-pending-diff") shouldBe 0
        later.driver.count("sm-workflow-git-index-tree") shouldBe 0
      } finally later.close()
    }
    "refuse next-Step or unowned changes before any native candidate staging" in {
      Vector("two.scala", "unowned.scala").foreach { path =>
        Given("an admitted first-Step implementation and an actual change outside its selected ownership: " + path)
        val e = episode(); val h = new Host(e, candidate = true)
        try {
          h.prepare(); take(h.start()); val issued = work(take(h.advance()))
          Files.writeString(e.target.root.resolve(path), "object OutOfFirstStep\n")
          take(h.complete(result(issued)))
          When("owning advance inspects the actual selected repository")
          val refused = h.advance()
          Then("out-of-Step work is refused without broad staging, candidate admission or repository closure")
          refused.isSuccess shouldBe false
          h.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 0
          h.driver.count("sm-workflow-git-stage-goal-path-2") shouldBe 0
          h.driver.count("sm-workflow-git-stage-full-state") shouldBe 0
          h.driver.count("sm-workflow-git-index-tree") shouldBe 0
          Files.readString(e.target.root.resolve(path)) shouldBe "object OutOfFirstStep\n"
        } finally h.close()
      }
    }
    "retain each native staging interruption without replay and recover recorded candidate preparation by observation" in {
      Vector("goal-candidate-prepared:one.scala", "goal-candidate-executed:one.scala",
        "goal-candidate-recorded:one.scala", "goal-candidate-observed").foreach { boundary =>
        Given("an owned first-Step change and a host acknowledgement interruption at " + boundary)
        val e = episode(); val first = new Host(e, candidate = true, acknowledgement = stage =>
          if (stage == boundary) F.invalid("fixture interrupted actual acknowledgement") else Consequence.unit)
        try {
          first.prepare(); take(first.start()); val issued = work(take(first.advance()))
          Files.writeString(e.target.root.resolve("one.scala"), "object InterruptedBoundary\n")
          take(first.complete(result(issued)))
          When("owning advance reaches the actual selected interruption boundary")
          val failed = first.advance()
          Then("the interrupted call is not reported as accepted candidate or Phase closure")
          failed.isSuccess shouldBe false
          first.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe
            (if (boundary == "goal-candidate-prepared:one.scala") 0 else 1)
        } finally first.close()
        Given("the actual shared SQLite store reopened after that interruption")
        val later = new Host(e, candidate = true, missingPlan = true)
        try {
          When("owning advance consumes the retained native intent or recorded result")
          val saved = take(WorkflowWaitJsonV1.decodeC(take(later.advance()).print))
          Then("no stage command is replayed; only a recorded result can lead to observed Validation")
          val revision = saved.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value
          revision shouldBe (if (Set("goal-candidate-recorded:one.scala", "goal-candidate-observed")(boundary)) "8" else "6")
          later.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 0
          later.driver.count("sm-workflow-git-stage-goal-path-2") shouldBe 0
          later.planReads shouldBe 0
        } finally later.close()
      }
    }
    "run the saved Plan's required native check once and retain its actual Review frontier across reopen" in {
      Given("an assigned first-Step candidate with an explicit fixed host check binding")
      val e = episode(); val first = new Host(e, candidate = true, checkMode = "pass")
      var validated: WorkflowInteraction[Nothing, Nothing] = null
      try {
        first.prepare(); take(first.start()); val issued = work(take(first.advance()))
        Files.writeString(e.target.root.resolve("one.scala"), "object ValidatedBoundary\n")
        take(first.complete(result(issued)))
        When("owning advance absorbs candidate staging, observation and the selected managed check")
        validated = take(WorkflowWaitJsonV1.decodeC(take(first.advance()).print))
        Then("the actual passed check reaches Review but neither review acceptance nor Step/Phase closure")
        validated.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value shouldBe "11"
        first.driver.count("sm-workflow-repository-validation-1") shouldBe 1
        first.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 1
        first.driver.count("sm-workflow-git-commit-pending") shouldBe 0
        first.driver.count("sm-workflow-git-push") shouldBe 0
        take(WorkflowWaitJsonV1.decodeC(take(first.advance()).print)) shouldBe validated
        first.driver.count("sm-workflow-repository-validation-1") shouldBe 1
      } finally first.close()
      Given("the physical shared SQLite host reopened without reselecting the plan")
      val later = new Host(e, candidate = true, checkMode = "pass", missingPlan = true)
      try {
        When("advance loads the actual completed native validation")
        val retained = take(WorkflowWaitJsonV1.decodeC(take(later.advance()).print))
        Then("the same Review frontier remains with no check or staging replay")
        retained shouldBe validated
        later.planReads shouldBe 0
        later.driver.count("sm-workflow-repository-validation-1") shouldBe 0
        later.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 0
      } finally later.close()
    }
    "retain an actual failed native check at Decision without retry or review acceptance" in {
      Given("a fixed host check whose actual owned Git process fails")
      val e = episode(); val first = new Host(e, candidate = true, checkMode = "fail")
      var failed: WorkflowInteraction[Nothing, Nothing] = null
      try {
        first.prepare(); take(first.start()); val issued = work(take(first.advance()))
        Files.writeString(e.target.root.resolve("one.scala"), "object FailedValidationBoundary\n")
        take(first.complete(result(issued)))
        When("owning advance runs that actual check")
        failed = take(WorkflowWaitJsonV1.decodeC(take(first.advance()).print))
        Then("the failed process has a saved nonterminal Decision, not a successful candidate review")
        failed.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value shouldBe "11"
        failed.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include("failed")
        first.driver.count("sm-workflow-repository-validation-1") shouldBe 1
        take(WorkflowWaitJsonV1.decodeC(take(first.advance()).print)) shouldBe failed
        first.driver.count("sm-workflow-repository-validation-1") shouldBe 1
      } finally first.close()
      Given("a reopened host whose check implementation can now pass but whose original result was a failure")
      val later = new Host(e, candidate = true, checkMode = "pass", missingPlan = true)
      try {
        When("advance observes the actual retained failed validation")
        val retained = take(WorkflowWaitJsonV1.decodeC(take(later.advance()).print))
        Then("it does not automatically repeat the command or erase that outcome")
        retained shouldBe failed
        later.driver.count("sm-workflow-repository-validation-1") shouldBe 0
        later.driver.count("sm-workflow-git-commit-pending") shouldBe 0
      } finally later.close()
    }
    "retain check interruption intent or recorded result without another external check on reopen" in {
      Vector("goal-check-prepared:focused", "goal-check-executed:focused", "goal-check-recorded:focused",
        "goal-checks-admitted").foreach { boundary =>
        Given("an actual selected candidate and host acknowledgement interruption at " + boundary)
        val e = episode(); val first = new Host(e, candidate = true, checkMode = "pass", acknowledgement = stage =>
          if (stage == boundary) F.invalid("fixture interrupted check acknowledgement") else Consequence.unit)
        try {
          first.prepare(); take(first.start()); val issued = work(take(first.advance()))
          Files.writeString(e.target.root.resolve("one.scala"), "object InterruptedCheckBoundary\n")
          take(first.complete(result(issued)))
          When("advance reaches the actual check boundary")
          val interrupted = first.advance()
          Then("the interrupted invocation does not pretend successful native validation")
          interrupted.isSuccess shouldBe false
          first.driver.count("sm-workflow-repository-validation-1") shouldBe
            (if (boundary == "goal-check-prepared:focused") 0 else 1)
        } finally first.close()
        Given("the actual store physically reopened after that interruption")
        val later = new Host(e, candidate = true, checkMode = "pass", missingPlan = true)
        try {
          When("advance loads its retained native check intent or result")
          val retained = take(WorkflowWaitJsonV1.decodeC(take(later.advance()).print))
          Then("unknown results remain nonterminal; recorded results alone can establish Review with no check replay")
          retained.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value shouldBe
            (if (Set("goal-check-recorded:focused", "goal-checks-admitted")(boundary)) "11" else "9")
          later.driver.count("sm-workflow-repository-validation-1") shouldBe 0
          later.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 0
          later.planReads shouldBe 0
        } finally later.close()
      }
    }
    "refuse missing or incompatible selected check bindings and actual changed native candidates without check execution" in {
      Given("a fixed host binding which does not provide the saved Plan's required check")
      val missingEpisode = episode(); val missing = new Host(missingEpisode, candidate = true, checkMode = "missing")
      try {
        missing.prepare(); take(missing.start()); val issued = work(take(missing.advance()))
        Files.writeString(missingEpisode.target.root.resolve("one.scala"), "object MissingCheck\n")
        take(missing.complete(result(issued)))
        When("advance resolves the actual selected check names")
        val refused = missing.advance()
        Then("missing binding refuses instead of silently skipping product checks")
        refused.isSuccess shouldBe false
        missing.driver.count("sm-workflow-repository-validation-1") shouldBe 0
      } finally missing.close()
      Given("a candidate already saved at Validation without an installed check policy")
      val e = episode(); val first = new Host(e, candidate = true)
      try {
        first.prepare(); take(first.start()); val issued = work(take(first.advance()))
        Files.writeString(e.target.root.resolve("one.scala"), "object OriginalCandidate\n")
        take(first.complete(result(issued))); take(first.advance())
      } finally first.close()
      Files.writeString(e.target.root.resolve("one.scala"), "object UnstagedAfterCandidate\n")
      val changed = new Host(e, candidate = true, checkMode = "pass")
      try {
        When("a configured host attempts validation after an actual intervening worktree edit")
        val refused = changed.advance()
        Then("the changed native candidate cannot run a check under the earlier candidate receipt")
        refused.isSuccess shouldBe false
        changed.driver.count("sm-workflow-repository-validation-1") shouldBe 0
      } finally changed.close()
      Given("another actual completed validation and a subsequently absent or incompatible selected check binding")
      val validatedEpisode = episode(); val original = new Host(validatedEpisode, candidate = true, checkMode = "pass")
      try {
        original.prepare(); take(original.start()); val issued = work(take(original.advance()))
        Files.writeString(validatedEpisode.target.root.resolve("one.scala"), "object PolicyBoundCandidate\n")
        take(original.complete(result(issued))); take(original.advance())
      } finally original.close()
      Vector("changed-binding", "").foreach { mode =>
        val altered = new Host(validatedEpisode, candidate = true, checkMode = mode, missingPlan = true)
        try {
          When("advance loads that retained outcome with a missing or incompatibly resolved required check")
          val refused = altered.advance()
          Then("an incompatible or missing selected check cannot consume the prior outcome or replay checks")
          refused.isSuccess shouldBe false
          altered.driver.count("sm-workflow-repository-validation-1") shouldBe 0
        } finally altered.close()
      }
    }
    "resume retained candidate and validation without whole host-path or policy declaration certificates" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R2; Example: E3; an actual passed selected check in SQLite")
      val e = episode()
      val first = new Host(e, candidate = true, checkMode = "pass")
      val retained = try {
        first.prepare(); take(first.start()); val issued = work(take(first.advance()))
        Files.writeString(e.target.root.resolve("one.scala"), "object ScopedRetainedCandidate\n")
        take(first.complete(result(issued)))
        take(first.advance()).print
      } finally first.close()
      When("the store reopens with different policy metadata or an extra unselected binding and host path")
      for (mode <- Vector("changed-policy", "unselected-checks")) {
        val later = new Host(e, candidate = true, checkMode = mode, missingPlan = true,
          extraCandidatePaths = Vector("unused.scala"))
        try {
          val response = take(later.advance())
          Then("the saved outcome survives without re-running its check or certifying the whole host declaration")
          response.print shouldBe retained
          later.driver.count("sm-workflow-repository-validation-1") shouldBe 0
          later.driver.count("sm-workflow-repository-validation-16") shouldBe 0
          later.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 0
          later.planReads shouldBe 0
        } finally later.close()
      }
    }
    "issue an independent review from actual native checks and retain paired work/result across physical host reopen" in {
      Given("a complete selected plan and a host-installed independent review ingress")
      val e = episode(); val first = new Host(e, candidate = true, checkMode = "pass", reviewMode = "allow")
      val selected = try {
        first.prepare(); take(first.start()); val implementation = work(take(first.advance()))
        Files.writeString(e.target.root.resolve("one.scala"), "object IndependentlyReviewed\n")
        take(first.complete(result(implementation)))
        When("advance absorbs native staging and checks before issuing only the next semantic review")
        val review = work(take(first.advance())); val duplicate = work(take(first.advance()))
        val input = review.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
        Then("review includes actual diff, paths, selected check receipts and the complete original plan/base")
        input.kind shouldBe "ReviewStep"
        input.stepIdentity shouldBe Some("S1")
        input.sourceRevision shouldBe "11"
        input.selectedPlan shouldBe e.plan
        input.review.get.changedPaths shouldBe Vector("one.scala")
        input.review.get.diff should include("IndependentlyReviewed")
        input.review.get.checks.map(_.identity) shouldBe Vector("focused")
        input.review.get.checks.head.exitCode shouldBe Some(0)
        duplicate shouldBe review
        first.driver.count("sm-workflow-repository-validation-1") shouldBe 1
        first.independentReviewAdmissions shouldBe 0
        review
      } finally first.close()
      Given("the physically reopened SQLite host has no current Plan selection")
      val later = new Host(e, candidate = true, checkMode = "pass", reviewMode = "allow", missingPlan = true)
      try {
        When("the same pending review is observed and the host separately registers its actual test reviewer")
        val retained = work(take(later.advance())); later.registerReview(retained)
        val saved = take(WorkflowWaitJsonV1.decodeC(take(later.complete(reviewResult(retained))).print))
        val duplicate = take(WorkflowWaitJsonV1.decodeC(take(later.advance()).print))
        Then("canonical paired completion reaches Closing but never runs commit or completes Step/Phase")
        retained shouldBe selected
        saved.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value shouldBe "13"
        saved.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include("phase.Closing")
        saved.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include(
          "Selected native checks and independent semantic review are retained")
        saved.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include(
          "Step commit and whole-Phase closure remain unfinished")
        duplicate shouldBe saved
        later.driver.count("sm-workflow-repository-validation-1") shouldBe 0
        later.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 0
        later.driver.count("sm-workflow-git-commit") shouldBe 0
        later.planReads shouldBe 0
        later.independentReviewAdmissions shouldBe 1
      } finally later.close()
    }
    "complete an original retained review after policy metadata changes without regenerating its meaning or granting closure" in {
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R3; Example: E8; an actual issued independent review in SQLite")
      val e = episode()
      val first = new Host(e, candidate = true, checkMode = "pass", reviewMode = "allow")
      val pending = try {
        first.prepare(); take(first.start()); val implementation = work(take(first.advance()))
        Files.writeString(e.target.root.resolve("one.scala"), "object OriginalReviewMeaning\n")
        take(first.complete(result(implementation)))
        work(take(first.advance()))
      } finally first.close()
      When("the physical host reopens with different review policy metadata and independently admits the original assigned Result")
      val later = new Host(e, missingPlan = true, candidate = true, checkMode = "pass", reviewMode = "changed-policy")
      try {
        val retained = work(take(later.advance()))
        later.registerReview(retained)
        val completed = take(WorkflowWaitJsonV1.decodeC(take(later.complete(reviewResult(retained))).print))
        Then("the original review meaning reaches Closing once, without re-running checks or granting any commit")
        retained shouldBe pending
        retained.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value.review.get.diff shouldBe
          pending.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value.review.get.diff
        completed.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include("phase.Closing")
        later.complete(reviewResult(retained)).isSuccess shouldBe false
        later.independentReviewAdmissions shouldBe 1
        later.driver.count("sm-workflow-repository-validation-1") shouldBe 0
        later.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 0
        later.driver.count("sm-workflow-git-commit") shouldBe 0
        later.planReads shouldBe 0
      } finally later.close()
    }
    "admit only an independent disposition and issue assigned repair or retain a bounded decision" in {
      Vector(("repair", 1, "Repair"), ("repair", 0, "Decision"), ("decision-needed", 1, "Decision")).foreach {
        (outcome, allowance, target) =>
          Given("a host-selected bounded plan repair policy and actual passed native checks")
          val e = episode(); val h = new Host(e, candidate = true, checkMode = "pass", reviewMode = "allow",
            selected = Some(e.plan.copy(repairLimit = allowance)))
          try {
            h.prepare(); take(h.start()); val implementation = work(take(h.advance()))
            Files.writeString(e.target.root.resolve("one.scala"), "object ReviewDisposition\n")
            take(h.complete(result(implementation))); val review = work(take(h.advance())); h.registerReview(review)
            When("the independently admitted reviewer returns its assigned disposition and findings")
            val saved = take(WorkflowWaitJsonV1.decodeC(take(h.complete(reviewResult(review, outcome))).print))
            val advanced = take(h.advance())
            Then("the actual controller and CML successor agree without command replay, budget refund or closure")
            saved.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include("phase." + target)
            saved.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include(
              if (target == "Repair") "Repair execution, fresh candidate checks and fresh review remain unfinished"
              else "The assigned result requires a decision")
            if (target == "Repair") {
              val repair = work(advanced)
              val order = repair.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]]
              val input = order.request.input.get.value
              input.kind shouldBe "RepairStep"
              input.generation shouldBe 2
              input.sourceRevision shouldBe "13"
              input.selectedPlan.repairLimit shouldBe allowance
              input.repairDirective.get.findings shouldBe Vector("Repair the assigned first-Step boundary")
              input.candidate shouldBe None
              input.checksReceipt shouldBe None
              input.review shouldBe review.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value.review
              order.request.operation shouldBe StateMachineOperationIdentity("GoalPhaseRepairWorkService", "repairGoalPhaseStep")
              order.requirement.reviewRequired shouldBe false
              work(take(h.advance())) shouldBe repair
            } else take(WorkflowWaitJsonV1.decodeC(advanced.print)) shouldBe saved
            h.driver.count("sm-workflow-repository-validation-1") shouldBe 1
            h.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 1
            h.driver.count("sm-workflow-git-commit") shouldBe 0
          } finally h.close()
      }
    }
    "reconstruct consumed repair rounds across physical reopen and run fresh checks and reviews until the selected limit" in {
      Vector(1, 2).foreach { allowance =>
        Given("a selected complete plan with a bounded repair allowance and one actual independent repair disposition")
        val e = episode(); val selected = e.plan.copy(repairLimit = allowance)
        val first = new Host(e, selected = Some(selected), candidate = true, checkMode = "pass", reviewMode = "allow")
        var initialReview: S.Request = null
        val pending = try {
          first.prepare(); take(first.start()); val implementation = work(take(first.advance()))
          Files.writeString(e.target.root.resolve("one.scala"), "object BeforeRepair\n")
          take(first.complete(result(implementation))); val review = work(take(first.advance())); first.registerReview(review)
          initialReview = review.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
          take(first.complete(reviewResult(review, "repair")))
          work(take(first.advance()))
        } finally first.close()
        Given("a physically reopened SQLite host with no current plan reader")
        val later = new Host(e, missingPlan = true, candidate = true, checkMode = "pass", reviewMode = "allow")
        try {
          var repair = pending
          var previousReview = initialReview
          for (round <- 1 to allowance) {
            When("the exact assigned repair is completed and advance selects this round's actual candidate and checks")
            work(take(later.advance())) shouldBe repair
            val input = repair.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
            input.selectedPlan shouldBe selected
            input.generation shouldBe round + 1
            input.originalBase shouldBe initialReview.originalBase
            input.completed shouldBe Vector.empty
            Files.writeString(e.target.root.resolve("one.scala"), s"object RepairedRound$round\n")
            val beforeChecks = later.driver.count("sm-workflow-repository-validation-1")
            val beforeStage = later.driver.count("sm-workflow-git-stage-goal-path-1")
            take(later.complete(result(repair)))
            later.driver.count("sm-workflow-repository-validation-1") shouldBe beforeChecks
            later.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe beforeStage
            val review = work(take(later.advance()))
            val reviewed = review.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
            Then("the generation and receipt are fresh, with no reuse of earlier successful checks or review")
            reviewed.kind shouldBe "ReviewStep"
            reviewed.generation shouldBe round + 1
            reviewed.selectedPlan shouldBe selected
            reviewed.checksReceipt should not be previousReview.checksReceipt
            reviewed.sourceRevision should not be previousReview.sourceRevision
            reviewed.candidate should not be previousReview.candidate
            reviewed.review.get.diff should include(s"RepairedRound$round")
            reviewed.repairDirective shouldBe input.repairDirective
            later.driver.count("sm-workflow-repository-validation-1") shouldBe round
            later.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe round
            work(take(later.advance())) shouldBe review
            later.registerReview(review)
            val saved = take(WorkflowWaitJsonV1.decodeC(take(later.complete(reviewResult(review, "repair"))).print))
            if (round < allowance) repair = work(take(later.advance()))
            else {
              saved.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include("phase.Decision")
              take(WorkflowWaitJsonV1.decodeC(take(later.advance()).print)) shouldBe saved
            }
            previousReview = reviewed
          }
          Then("the saved allowance is exhausted without closure, plan reselection or command replay")
          later.planReads shouldBe 0
          later.driver.count("sm-workflow-git-commit") shouldBe 0
          later.driver.count("sm-workflow-git-push") shouldBe 0
          later.independentReviewAdmissions shouldBe allowance
        } finally later.close()
        Given("another physical reopen with a different unselected allowance")
        val finalHost = new Host(e, selected = Some(e.plan.copy(repairLimit = 64)), candidate = true, checkMode = "pass", reviewMode = "allow")
        try {
          When("advance reconstructs the actual completed rounds")
          val retained = take(WorkflowWaitJsonV1.decodeC(take(finalHost.advance()).print))
          Then("Decision remains nonterminal and the original exhausted budget is not replenished")
          retained.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include("phase.Decision")
          finalHost.planReads shouldBe 0
          finalHost.driver.count("sm-workflow-repository-validation-1") shouldBe 0
          finalHost.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 0
        } finally finalHost.close()
      }
    }
    "accept only the fresh repaired review for Closing without claiming Step or Phase completion" in {
      Given("a retained independent repair request within the selected allowance")
      val e = episode(); val h = new Host(e, selected = Some(e.plan.copy(repairLimit = 1)),
        candidate = true, checkMode = "pass", reviewMode = "allow")
      try {
        h.prepare(); take(h.start()); val implementation = work(take(h.advance()))
        Files.writeString(e.target.root.resolve("one.scala"), "object ToRepair\n")
        take(h.complete(result(implementation))); val prior = work(take(h.advance())); h.registerReview(prior)
        take(h.complete(reviewResult(prior, "repair"))); val repair = work(take(h.advance()))
        Files.writeString(e.target.root.resolve("one.scala"), "object NowRepaired\n")
        take(h.complete(result(repair))); val review = work(take(h.advance())); h.registerReview(review)
        When("the earlier accepted envelope is crossed with this fresh review and then the actual assigned review is accepted")
        h.complete(reviewResult(prior)).isSuccess shouldBe false
        val saved = take(WorkflowWaitJsonV1.decodeC(take(h.complete(reviewResult(review))).print))
        Then("only the current paired completion reaches nonterminal Closing and no commands are replayed")
        saved.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include("phase.Closing")
        take(WorkflowWaitJsonV1.decodeC(take(h.advance()).print)) shouldBe saved
        h.driver.count("sm-workflow-repository-validation-1") shouldBe 2
        h.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 2
        h.driver.count("sm-workflow-git-commit") shouldBe 0
      } finally h.close()
    }
    "refuse crossed, review-shaped or ownership-escaping repair results before paired completion" in {
      Vector(false, true).foreach { outside =>
        Given("an actual independent repair issue and no authority to modify the next Step")
        val e = episode(); val h = new Host(e, selected = Some(e.plan.copy(repairLimit = 1)),
          candidate = true, checkMode = "pass", reviewMode = "allow")
        try {
          h.prepare(); take(h.start()); val implementation = work(take(h.advance()))
          Files.writeString(e.target.root.resolve("one.scala"), "object AssignedRepair\n")
          take(h.complete(result(implementation))); val review = work(take(h.advance())); h.registerReview(review)
          take(h.complete(reviewResult(review, "repair"))); val repair = work(take(h.advance())); val reply = result(repair)
          if (outside) Files.writeString(e.target.root.resolve("two.scala"), "object OutsideCurrentStep\n")
          When("a crossed revision, invented review outcome or next-Step modification is submitted")
          h.complete(reply.copy(expectedRevision = StateMachineRevision("999"))).isSuccess shouldBe false
          h.complete(result(repair, "accept")).isSuccess shouldBe false
          if (outside) h.complete(reply).isSuccess shouldBe false
          Then("the actual repair work remains pending and no fresh checks or closure are run")
          work(take(h.advance())) shouldBe repair
          h.driver.count("sm-workflow-repository-validation-1") shouldBe 1
          h.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 1
          h.driver.count("sm-workflow-git-commit") shouldBe 0
        } finally h.close()
      }
    }
    "retain an explicit repair decision through the CML edge without running candidate commands" in {
      Given("a bounded assigned repair that cannot complete semantically")
      val e = episode(); val h = new Host(e, selected = Some(e.plan.copy(repairLimit = 1)),
        candidate = true, checkMode = "pass", reviewMode = "allow")
      try {
        h.prepare(); take(h.start()); val implementation = work(take(h.advance()))
        Files.writeString(e.target.root.resolve("one.scala"), "object NeedsDecision\n")
        take(h.complete(result(implementation))); val review = work(take(h.advance())); h.registerReview(review)
        take(h.complete(reviewResult(review, "repair"))); val repair = work(take(h.advance()))
        When("the assigned repair returns an explicit decision-needed result")
        val saved = take(WorkflowWaitJsonV1.decodeC(take(h.complete(result(repair, "decision-needed"))).print))
        Then("Decision is saved and neither the old checks nor a new command cycle can close the Step")
        saved.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include("phase.Decision")
        take(WorkflowWaitJsonV1.decodeC(take(h.advance()).print)) shouldBe saved
        h.driver.count("sm-workflow-repository-validation-1") shouldBe 1
        h.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 1
        h.driver.count("sm-workflow-git-commit") shouldBe 0
      } finally h.close()
    }
    "retain an interrupted repaired-candidate staging intent across reopen without replay or stale review" in {
      Given("an admitted repair result followed by an interrupted second-round external staging acknowledgement")
      val e = episode(); var repaired = false
      val h = new Host(e, selected = Some(e.plan.copy(repairLimit = 1)), candidate = true, checkMode = "pass", reviewMode = "allow",
        acknowledgement = stage => if (repaired && stage == "goal-candidate-executed:one.scala")
          F.invalid("owned repaired staging interruption") else Consequence.unit)
      try {
        h.prepare(); take(h.start()); val implementation = work(take(h.advance()))
        Files.writeString(e.target.root.resolve("one.scala"), "object BeforeInterruption\n")
        take(h.complete(result(implementation))); val review = work(take(h.advance())); h.registerReview(review)
        take(h.complete(reviewResult(review, "repair"))); val repair = work(take(h.advance()))
        Files.writeString(e.target.root.resolve("one.scala"), "object InterruptedRepair\n")
        take(h.complete(result(repair))); repaired = true
        When("advance executes staging but cannot record its acknowledged result")
        h.advance().isSuccess shouldBe false
        Then("one actual second-round effect has occurred without a reusable completion")
        h.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 2
        h.driver.count("sm-workflow-repository-validation-1") shouldBe 1
      } finally h.close()
      Given("a physically reopened host with the durable repaired-round intent")
      val later = new Host(e, missingPlan = true, candidate = true, checkMode = "pass", reviewMode = "allow")
      try {
        When("advance is observed twice")
        val saved = take(WorkflowWaitJsonV1.decodeC(take(later.advance()).print))
        val duplicate = take(WorkflowWaitJsonV1.decodeC(take(later.advance()).print))
        Then("the repaired staging remains nonterminal and is not replayed or replaced with the earlier review")
        duplicate shouldBe saved
        saved.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include("not automatically replayed")
        later.driver.count("sm-workflow-git-stage-goal-path-1") shouldBe 0
        later.driver.count("sm-workflow-repository-validation-1") shouldBe 0
        later.planReads shouldBe 0
      } finally later.close()
    }
    "refuse unenrolled or crossed review results and an actually changed candidate before paired completion" in {
      Given("an actual issued review without independently admitted test reviewer evidence")
      val e = episode(); val h = new Host(e, candidate = true, checkMode = "pass", reviewMode = "allow")
      val pending = try {
        h.prepare(); take(h.start()); val implementation = work(take(h.advance()))
        Files.writeString(e.target.root.resolve("one.scala"), "object ReviewAdmission\n")
        take(h.complete(result(implementation))); val review = work(take(h.advance())); val reply = reviewResult(review)
        When("an AI asserts independence or substitutes its result revision")
        val unenrolled = h.complete(reply)
        h.registerReview(review)
        val crossed = h.complete(reply.copy(expectedRevision = StateMachineRevision("999")))
        val selfReview = h.complete(reply.copy(evidence = reply.evidence.copy(workerIdentity = Some("participant"))))
        Then("none of those replies can become native acceptance")
        unenrolled.isSuccess shouldBe false
        crossed.isSuccess shouldBe false
        selfReview.isSuccess shouldBe false
        review
      } finally h.close()
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R3; Example: E7; a physically reopened host with changed metadata or removed independent review installation")
      Vector("changed-policy", "").foreach { mode =>
        val changed = new Host(e, candidate = true, checkMode = "pass", reviewMode = mode, missingPlan = true)
        try {
          When("advance observes the earlier issued review")
          val observed = changed.advance()
          Then("metadata alone cannot replace original issued meaning; missing installation still refuses")
          if (mode == "changed-policy") work(take(observed)) shouldBe pending
          else observed.isSuccess shouldBe false
          changed.driver.count("sm-workflow-repository-validation-1") shouldBe 0
        } finally changed.close()
      }
      Given("an actual unstaged modification after review was issued")
      Files.writeString(e.target.root.resolve("one.scala"), "object ChangedAfterReview\n")
      val changed = new Host(e, candidate = true, checkMode = "pass", reviewMode = "allow", missingPlan = true)
      try {
        changed.registerReview(pending)
        When("the earlier independent semantic answer is submitted against the changed candidate")
        val refused = changed.complete(reviewResult(pending))
        Then("live candidate admission refuses before consulting independent result evidence or committing")
        refused.isSuccess shouldBe false
        changed.independentReviewAdmissions shouldBe 0
        changed.driver.count("sm-workflow-git-commit") shouldBe 0
      } finally changed.close()
    }
    "keep an actually failed check out of independent review issue even when review is installed" in {
      Given("an actual failing fixed check and an installed independent review ingress")
      val e = episode(); val h = new Host(e, candidate = true, checkMode = "fail", reviewMode = "allow")
      try {
        h.prepare(); take(h.start()); val implementation = work(take(h.advance()))
        Files.writeString(e.target.root.resolve("one.scala"), "object FailedBeforeReview\n")
        take(h.complete(result(implementation)))
        When("advance receives the native failed check outcome")
        val saved = take(WorkflowWaitJsonV1.decodeC(take(h.advance()).print))
        val duplicate = take(WorkflowWaitJsonV1.decodeC(take(h.advance()).print))
        Then("the Decision stays nonterminal and no semantic review can be issued or accepted")
        saved.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value shouldBe "11"
        duplicate shouldBe saved
        h.independentReviewAdmissions shouldBe 0
        h.driver.count("sm-workflow-repository-validation-1") shouldBe 1
        h.driver.count("sm-workflow-git-commit") shouldBe 0
      } finally h.close()
    }
    "commit the reviewed authorized first Step and complete the next Step's own repair/check/review round after reopen" in {
      Given("a complete two-Step plan, fixed host commit programs and separately registered closure authority")
      val e = episode(); val selected = e.plan.copy(repairLimit = 1)
      val first = new Host(e, selected = Some(selected), candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "allow")
      var originalBase: O.Snapshot = null
      val pending = try {
        first.prepare(); take(first.start()); val implementation = work(take(first.advance()))
        Files.writeString(e.target.root.resolve("one.scala"), "object FirstClosedStep\n")
        take(first.complete(result(implementation))); val review = work(take(first.advance())); first.registerReview(review)
        val reviewed = take(first.complete(reviewResult(review))); first.registerClosure(review, reviewed)
        When("owning advance commits the actual reviewed tree and selects only the next Step semantics")
        val next = work(take(first.advance()))
        val input = next.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
        originalBase = input.originalBase
        Then("the actual single-parent commit closes S1 while the whole original plan and base are retained")
        input.kind shouldBe "ImplementStep"
        input.stepIdentity shouldBe Some("S2")
        input.generation shouldBe 2
        input.selectedPlan shouldBe selected
        input.completed.map(_.step) shouldBe Vector("S1")
        input.completed.head.commit.parent shouldBe originalBase.head
        input.sourceRevision shouldBe "15"
        input.completed.head.commit.identity should not be originalBase.head
        input.candidate shouldBe None
        work(take(first.advance())) shouldBe next
        first.driver.count("sm-workflow-git-goal-commit-step-1") shouldBe 1
        first.driver.count("sm-workflow-git-goal-commit-step-2") shouldBe 0
        next
      } finally first.close()
      Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R5; Example: E12; a physically reopened host with an extra unselected commit binding, no current Plan reader and the retained S2 issue")
      val later = new Host(e, missingPlan = true, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "unselected-program")
      val phaseInspection = try {
        work(take(later.advance())) shouldBe pending
        Files.writeString(e.target.root.resolve("two.scala"), "object SecondStepBeforeRepair\n")
        take(later.complete(result(pending))); val review = work(take(later.advance())); later.registerReview(review)
        val input = review.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
        When("S2 is inspected and reviewed against the actual S1 commit rather than the original HEAD")
        Then("the selected checks and diff belong only to S2 and the completed S1 history remains explicit")
        input.stepIdentity shouldBe Some("S2")
        input.completed shouldBe pending.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value.completed
        input.candidate.get.head shouldBe input.completed.head.commit.identity
        input.review.get.changedPaths shouldBe Vector("two.scala")
        input.review.get.diff should not include "FirstClosedStep"
        input.review.get.checks.map(_.identity) shouldBe Vector("focused-next")
        take(later.complete(reviewResult(review, "repair"))); val repair = work(take(later.advance()))
        val repairInput = repair.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
        repairInput.generation shouldBe 3
        repairInput.completed shouldBe input.completed
        Files.writeString(e.target.root.resolve("two.scala"), "object SecondStepRepaired\n")
        take(later.complete(result(repair))); val repairedReview = work(take(later.advance())); later.registerReview(repairedReview)
        val accepted = take(later.complete(reviewResult(repairedReview))); later.registerClosure(repairedReview, accepted)
        When("the independently accepted and separately authorized S2 is committed")
        val saved = take(WorkflowWaitJsonV1.decodeC(take(later.advance()).print))
        Then("all Steps are committed but the whole Phase is not declared closed")
        saved.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include("Whole-Phase inspection")
        saved.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include("Phase is not closed")
        take(WorkflowWaitJsonV1.decodeC(take(later.advance()).print)) shouldBe saved
        later.driver.count("sm-workflow-git-goal-commit-step-1") shouldBe 0
        later.driver.count("sm-workflow-git-goal-commit-step-2") shouldBe 1
        later.driver.count("sm-workflow-repository-validation-1") shouldBe 0
        later.driver.count("sm-workflow-repository-validation-2") shouldBe 2
        later.driver.count("sm-workflow-git-push") shouldBe 0
        later.planReads shouldBe 0
        saved
      } finally later.close()
      Given("another reopened host whose authority reader no longer grants new commits")
      val last = new Host(e, missingPlan = true, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "deny")
      try {
        When("owning advance reconstructs both actual Step commits and consumed review/repair history")
        val observed = take(WorkflowWaitJsonV1.decodeC(take(last.advance()).print))
        Then("the saved PhaseInspection remains nonterminal with no repeated commit or check")
        observed shouldBe phaseInspection
        last.driver.count("sm-workflow-git-goal-commit-step-1") shouldBe 0
        last.driver.count("sm-workflow-git-goal-commit-step-2") shouldBe 0
        last.driver.count("sm-workflow-repository-validation-2") shouldBe 0
        last.planReads shouldBe 0
      } finally last.close()
    }
    "require actual current Step authority separately from review acceptance before committing" in {
      Given("an accepted native review and host-installed commit program without a recorded authorization")
      val e = episode(); val h = new Host(e, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "allow")
      try {
        h.prepare(); take(h.start()); val implementation = work(take(h.advance()))
        Files.writeString(e.target.root.resolve("one.scala"), "object RequiresCommitAuthority\n")
        take(h.complete(result(implementation))); val review = work(take(h.advance())); h.registerReview(review)
        val accepted = take(h.complete(reviewResult(review)))
        When("advance is called with review acceptance but no host-recorded commit authority")
        val waiting = take(WorkflowWaitJsonV1.decodeC(take(h.advance()).print))
        Then("Closing remains open and neither review acceptance nor a process capability invents approval")
        waiting.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value shouldBe "13"
        waiting.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include("authority is unavailable")
        h.driver.count("sm-workflow-git-goal-commit-step-1") shouldBe 0
        Given("the host separately records the exact current candidate/Plan/review authorization")
        h.registerClosure(review, accepted)
        When("owning advance is called again")
        val next = work(take(h.advance()))
        Then("only the admitted first Step is committed and next semantic work becomes available")
        next.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value.stepIdentity shouldBe Some("S2")
        h.driver.count("sm-workflow-git-goal-commit-step-1") shouldBe 1
      } finally h.close()
    }
    "refuse crossed authority or a changed accepted candidate before saving a Step commit intent" in {
      Vector("crossed", "changed-candidate", "outside-path").foreach { mode =>
        Given("an independently accepted candidate and a host-recorded closure authorization")
        val e = episode(); val h = new Host(e, candidate = true, checkMode = "pass", reviewMode = "allow",
          closureMode = if (mode == "crossed") "crossed" else "allow")
        try {
          h.prepare(); take(h.start()); val implementation = work(take(h.advance()))
          Files.writeString(e.target.root.resolve("one.scala"), "object AcceptedCandidate\n")
          take(h.complete(result(implementation))); val review = work(take(h.advance())); h.registerReview(review)
          val accepted = take(h.complete(reviewResult(review))); h.registerClosure(review, accepted)
          if (mode == "changed-candidate") Files.writeString(e.target.root.resolve("one.scala"), "object ChangedAfterAcceptance\n")
          if (mode == "outside-path") Files.writeString(e.target.root.resolve("two.scala"), "object OutsideCommittedStep\n")
          When("advance observes a crossed authorization or actual changed repository")
          val refused = h.advance()
          Then("no Step commit command or next Step issue is authorized")
          refused.isSuccess shouldBe false
          h.driver.count("sm-workflow-git-goal-commit-step-1") shouldBe 0
          h.driver.count("sm-workflow-git-stage-goal-path-2") shouldBe 0
        } finally h.close()
      }
    }
    "inspect and validate the whole original Phase before issuing and admitting its distinct independent review" in {
      Given("two actual owned Step commits and a separate fixed whole-Phase check installation")
      val e = episode()
      val first = new Host(e, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "allow", phaseMode = "pass")
      var phaseReview: WorkflowInteraction[S.Request, Nothing] = null
      try {
        When("owning advance commits both Steps and reaches the whole-Phase frontier")
        phaseReview = work(take(closeSteps(first, e, 2)))
        val order = phaseReview.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
        val input = order.input.get.value
        Then("only ReviewWholePhase is issued with the complete original Plan/base/chain and whole-Phase evidence")
        input.kind shouldBe "ReviewPhase"
        input.stepIdentity shouldBe None
        input.selectedPlan shouldBe e.plan
        input.completed.map(_.step) shouldBe Vector("S1", "S2")
        input.candidate.get.head shouldBe input.completed.last.commit.identity
        input.originalBase.head shouldBe input.completed.head.commit.parent
        input.review.get.changedPaths.toSet shouldBe Set("one.scala", "two.scala")
        input.review.get.diff should include("OwnedS1")
        input.review.get.diff should include("OwnedS2")
        input.review.get.checks.map(_.identity) shouldBe Vector("whole-phase")
        order.requiredOperation shouldBe take(S.requiredC("ReviewPhase")).identity
        order.operation shouldBe StateMachineOperationIdentity("GoalPhaseReviewService", "reviewGoalPhase")
        first.driver.count("sm-workflow-repository-validation-3") shouldBe 1
        first.driver.count("sm-workflow-git-goal-commit-step-1") shouldBe 1
        first.driver.count("sm-workflow-git-goal-commit-step-2") shouldBe 1
        work(take(first.advance())) shouldBe phaseReview
      } finally first.close()
      Given("a physically reopened host without a current Plan reader or new Step commit authority")
      val later = new Host(e, missingPlan = true, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "deny", phaseMode = "pass")
      try {
        When("the exact Phase review is observed and an unenrolled reviewer attempts completion")
        work(take(later.advance())) shouldBe phaseReview
        later.complete(reviewResult(phaseReview)).isSuccess shouldBe false
        later.registerReview(phaseReview)
        When("the independently admitted assigned result is completed and owning advance resumes")
        val accepted = take(WorkflowWaitJsonV1.decodeC(take(later.complete(reviewResult(phaseReview))).print))
        Then("PhaseClosing remains nonterminal until an actual authorized repository closure")
        accepted.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include("phase.PhaseClosing")
        take(WorkflowWaitJsonV1.decodeC(take(later.advance()).print)) shouldBe accepted
        later.planReads shouldBe 0
        later.driver.count("sm-workflow-repository-validation-3") shouldBe 0
        later.driver.count("sm-workflow-git-goal-commit-step-1") shouldBe 0
        later.driver.count("sm-workflow-git-goal-commit-step-2") shouldBe 0
      } finally later.close()
    }
    "never substitute last-Step validation for failed or unbound whole-Phase checks" in {
      for (mode <- Vector("fail", "missing")) {
        Given("an actual closed Step but a failing or missing required whole-Phase check binding")
        val e = episode()
        val h = new Host(e, selected = Some(e.plan.copy(steps = e.plan.steps.take(1))), candidate = true,
          checkMode = "pass", reviewMode = "allow", closureMode = "allow", phaseMode = mode)
        try {
          When("owning advance reaches the Phase validation frontier")
          val advanced = closeSteps(h, e, 1)
          Then("last-Step success never issues a whole-Phase review or manufactures acceptance")
          if (mode == "fail") {
            val wait = take(WorkflowWaitJsonV1.decodeC(take(advanced).print))
            wait.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include("failed")
            take(WorkflowWaitJsonV1.decodeC(take(h.advance()).print)) shouldBe wait
          } else {
            advanced.isSuccess shouldBe false
            h.advance().isSuccess shouldBe false
          }
          h.driver.count("sm-workflow-repository-validation-1") shouldBe 1
          h.driver.count("sm-workflow-repository-validation-3") shouldBe (if (mode == "fail") 1 else 0)
          h.driver.count("sm-workflow-git-goal-commit-step-1") shouldBe 1
          h.independentReviewAdmissions shouldBe 1
        } finally h.close()
      }
    }
    "keep whole-Phase repair and Human dispositions at Decision without reopening closed Steps or refunding allowance" in {
      for (outcome <- Vector("repair", "decision-needed")) {
        Given("a genuinely issued Phase review with one retained actual Step commit and an available Plan repair allowance")
        val e = episode()
        val h = new Host(e, selected = Some(e.plan.copy(steps = e.plan.steps.take(1), repairLimit = 64)), candidate = true,
          checkMode = "pass", reviewMode = "allow", closureMode = "allow", phaseMode = "pass")
        try {
          val review = work(take(closeSteps(h, e, 1))); h.registerReview(review)
          val input = review.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
          input.completed.size shouldBe 1
          When("the actual independent Phase review requires repair or a Human decision")
          val saved = take(WorkflowWaitJsonV1.decodeC(take(h.complete(reviewResult(review, outcome))).print))
          Then("advance observes Decision rather than changing the selected Plan or repairing an already closed Step")
          saved.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include("phase.Decision")
          take(WorkflowWaitJsonV1.decodeC(take(h.advance()).print)) shouldBe saved
          h.driver.count("sm-workflow-git-goal-commit-step-1") shouldBe 1
          h.driver.count("sm-workflow-repository-validation-3") shouldBe 1
        } finally h.close()
      }
    }
    "retain interrupted whole-Phase checks without policy certificates and refuse incompatible checks or actual candidate changes" in {
      for (point <- Vector("prepared", "executed", "recorded")) {
        Given("actual Step closure and an interruption at the selected whole-Phase check boundary")
        val e = episode()
        val h = new Host(e, selected = Some(e.plan.copy(steps = e.plan.steps.take(1))), candidate = true,
          checkMode = "pass", reviewMode = "allow", closureMode = "allow", phaseMode = "pass",
          acknowledgement = stage => if (stage == "goal-check-" + point + ":whole-phase") F.invalid("owned interrupted Phase check") else Consequence.unit)
        try {
          When("owning advance reaches that actual effect boundary")
          closeSteps(h, e, 1).isSuccess shouldBe false
          Then("the actual Phase check count distinguishes prepared from executed effects")
          h.driver.count("sm-workflow-repository-validation-3") shouldBe (if (point == "prepared") 0 else 1)
        } finally h.close()
        Given("physical pool reopen with no new Step authority and only retained Phase evidence")
        val later = new Host(e, missingPlan = true, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "deny", phaseMode = "pass")
        try {
          When("owning advance observes the frontier twice")
          val first = take(later.advance()); val second = take(later.advance())
          Then("only a recorded check allows Phase review; unknown effects remain waiting and no check is replayed")
          if (point == "recorded") work(second) shouldBe work(first)
          else take(WorkflowWaitJsonV1.decodeC(second.print)) shouldBe take(WorkflowWaitJsonV1.decodeC(first.print))
          later.driver.count("sm-workflow-repository-validation-3") shouldBe 0
          later.driver.count("sm-workflow-git-goal-commit-step-1") shouldBe 0
        } finally later.close()
      }
      for (mode <- Vector("changed-policy", "changed-binding", "removed-policy", "changed-candidate")) {
        Given("an actual pending whole-Phase review after separately validated native Step commits")
        val e = episode()
        val first = new Host(e, selected = Some(e.plan.copy(steps = e.plan.steps.take(1))), candidate = true,
          checkMode = "pass", reviewMode = "allow", closureMode = "allow", phaseMode = "pass")
        var pending: WorkflowInteraction[S.Request, Nothing] = null
        try pending = work(take(closeSteps(first, e, 1))) finally first.close()
        val later = new Host(e, missingPlan = true, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "deny",
          phaseMode = if (mode == "removed-policy") "" else if (Set("changed-policy", "changed-binding")(mode)) mode else "pass")
        try {
          When("the selected binding, policy metadata or actual reviewed native candidate differs")
          if (mode == "changed-candidate") {
            Files.writeString(e.target.root.resolve("one.scala"), "object ChangedAfterPhaseReview\n")
            later.registerReview(pending)
            later.complete(reviewResult(pending)).isSuccess shouldBe false
            later.independentReviewAdmissions shouldBe 0
          } else if (mode == "changed-policy") {
            // R2/E4: current binding remains compatible; revision alone is not a restart gate.
            work(take(later.advance())) shouldBe pending
          } else later.advance().isSuccess shouldBe false
          Then("compatible retained work survives; incompatibility refuses, without check replay or new Phase acceptance")
          later.driver.count("sm-workflow-repository-validation-3") shouldBe 0
          later.driver.count("sm-workflow-repository-validation-16") shouldBe 0
          later.driver.count("sm-workflow-git-goal-commit-step-1") shouldBe 0
        } finally later.close()
      }
    }
    "close the whole Phase only after its installed child RepositorySync actually converges" in {
      Given("two actual accepted Step commits, Phase checks/review and a separately authorized owned local remote")
      val e = episode()
      val h = new Host(e, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "allow",
        phaseMode = "pass", repositoryMode = "allow")
      var wire = ""
      try {
        val review = work(take(closeSteps(h, e, 2))); h.registerReview(review)
        val accepted = take(h.complete(reviewResult(review))); h.registerRepository(review, accepted)
        When("owning advance invokes the actual installed child Start and advance Operations")
        wire = take(h.advance()).print
        val terminal = take(WorkflowTerminalJsonV1.decodeC(wire, GoalPhaseNativeRepositoryClosure.resultCodec))
        val closed = terminal.current.asInstanceOf[WorkflowContinuation.Terminal[GoalPhaseNativeRepositoryClosure.Result]].result.value
        Then("Goal Terminal retains the original full plan/base/Steps and a genuine completed child result")
        terminal.handle.instanceIdentity.value shouldBe e.identity
        closed.plan shouldBe e.plan
        closed.completed.map(_.step) shouldBe Vector("S1", "S2")
        closed.repository.tip shouldBe closed.completed.last.commit.identity
        closed.repository.tree shouldBe closed.reviewedCandidate.tree
        closed.repository.instanceIdentity should startWith("repository-sync:")
        h.driver.count("sm-workflow-git-push") shouldBe 1
        h.driver.count("sm-workflow-repository-validation-4") shouldBe 1
        h.driver.count("sm-workflow-git-is-ancestor") shouldBe 1
        take(h.advance()).print shouldBe wire
        h.driver.count("sm-workflow-git-push") shouldBe 1
      } finally h.close()
      Given("physical pool reopen without current Plan, review enrollment or new closure authority")
      val later = new Host(e, missingPlan = true, candidate = true, checkMode = "pass", reviewMode = "allow",
        closureMode = "deny", phaseMode = "pass", repositoryMode = "deny")
      try {
        When("owning advance reads the genuinely recorded whole-Phase result")
        take(later.advance()).print shouldBe wire
        Then("it returns that same Terminal without rerunning child effects or selecting another plan")
        later.planReads shouldBe 0
        later.driver.count("sm-workflow-git-push") shouldBe 0
        later.driver.count("sm-workflow-git-fetch") shouldBe 0
        later.driver.count("sm-workflow-repository-validation-4") shouldBe 0
      } finally later.close()
    }
    "route diverged and conflicted child work through its own completions before closing the owning Phase" in {
      for (scenario <- Vector("diverged", "conflict")) {
        Given("two actual accepted Steps and a real divergent owned remote, with paired child semantic Operations")
        val e = episode()
        val first = new Host(e, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "allow",
          phaseMode = "pass", repositoryMode = "allow", repositoryScenario = scenario)
        var phaseReview: WorkflowInteraction[S.Request, Nothing] = null
        var accepted: Response = null
        var issued: WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing] = null
        try {
          phaseReview = work(take(closeSteps(first, e, 2))); first.registerReview(phaseReview)
          accepted = take(first.complete(reviewResult(phaseReview))); first.registerRepository(phaseReview, accepted)
          When("owning Goal advance reaches actual pending child integration")
          val initial = take(first.advance())
          if (scenario == "conflict") {
            val conflict = childConflictWork(initial)
            val order = conflict.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].request
            Then("the child conflict retains its own handle, source input and exact completion route")
            conflict.handle.instanceIdentity.value should startWith("repository-sync:")
            order.completionOperation shouldBe RepositorySyncNativeConflictContinuation.completionOperation
            order.input.get.value.conflicts.map(_.path) shouldBe Vector("one.scala")
            childConflictWork(take(first.advance())) shouldBe conflict
            first.driver.count("sm-workflow-git-pending-merge") shouldBe 1
            When("AI returns only its assigned conflict proposal to the child completion Operation")
            take(first.completeChildConflict(childConflictResult(conflict)))
            Then("proposal admission does not commit or push the merge")
            first.driver.count("sm-workflow-git-commit-pending") shouldBe 0
            first.driver.count("sm-workflow-git-push") shouldBe 0
            When("owning Goal advance materializes and checks the actual child candidate")
            issued = childReviewWork(take(first.advance()))
          } else issued = childReviewWork(initial)
          val order = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request
          Then("the assigned child review contains the actual integrated diff/checks, not another Goal review")
          order.completionOperation shouldBe RepositorySyncNativeMergeReviewContinuation.completionOperation
          issued.handle.instanceIdentity.value should startWith("repository-sync:")
          issued.handle.instanceIdentity.value should not be e.identity
          order.input.get.value.localTip should not be order.input.get.value.remoteTip
          order.input.get.value.checks.map(_.identity) shouldBe Vector("index-local", "index-remote", "repository-check")
          childReviewWork(take(first.advance())) shouldBe issued
          first.driver.count("sm-workflow-repository-validation-4") shouldBe 1
          first.driver.count("sm-workflow-git-commit-pending") shouldBe 0
          first.driver.count("sm-workflow-git-push") shouldBe 0
        } finally first.close()
        Given("physical pool reopen retaining the actual child work without a Plan or new Step authority")
        val later = new Host(e, missingPlan = true, candidate = true, checkMode = "pass", reviewMode = "allow",
          closureMode = "deny", phaseMode = "pass", repositoryMode = "allow", repositoryScenario = scenario)
        try {
          later.registerRepository(phaseReview, accepted)
          When("owning advance loads the same child issue and its independently registered semantic completion is submitted")
          childReviewWork(take(later.advance())) shouldBe issued
          later.registerChildReview(issued)
          take(later.completeChildReview(childReviewResult(issued)))
          Then("semantic acceptance alone still commits and pushes nothing")
          later.driver.count("sm-workflow-git-commit-pending") shouldBe 0
          later.driver.count("sm-workflow-git-push") shouldBe 0
          When("owning Goal advance executes authorized child convergence")
          val wire = take(later.advance()).print
          val terminal = take(WorkflowTerminalJsonV1.decodeC(wire, GoalPhaseNativeRepositoryClosure.resultCodec))
          val closed = terminal.current.asInstanceOf[WorkflowContinuation.Terminal[GoalPhaseNativeRepositoryClosure.Result]].result.value
          Then("only real child convergence closes the original complete Phase and preserves its ordered ancestor")
          terminal.handle.instanceIdentity.value shouldBe e.identity
          closed.plan shouldBe e.plan
          closed.completed.map(_.step) shouldBe Vector("S1", "S2")
          closed.repository.tip should not be closed.completed.last.commit.identity
          closed.reviewedCandidate.head shouldBe closed.completed.last.commit.identity
          closed.repository.instanceIdentity shouldBe issued.handle.instanceIdentity.value
          later.planReads shouldBe 0
          later.driver.count("sm-workflow-git-pending-merge") shouldBe 0
          later.driver.count("sm-workflow-repository-validation-4") shouldBe 0
          later.driver.count("sm-workflow-git-commit-pending") shouldBe 1
          later.driver.count("sm-workflow-git-push") shouldBe 1
          later.driver.count("sm-workflow-git-is-ancestor") shouldBe 1
          take(later.advance()).print shouldBe wire
          later.driver.count("sm-workflow-git-commit-pending") shouldBe 1
          later.driver.count("sm-workflow-git-push") shouldBe 1
        } finally later.close()
      }
    }
    "refuse child review without independent evidence or through the parent completion route" in {
      Given("an actual pending child merge review returned by the owning complete Phase")
      val e = episode()
      val h = new Host(e, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "allow",
        phaseMode = "pass", repositoryMode = "allow", repositoryScenario = "diverged")
      try {
        val review = work(take(closeSteps(h, e, 2))); h.registerReview(review)
        val accepted = take(h.complete(reviewResult(review))); h.registerRepository(review, accepted)
        val child = childReviewWork(take(h.advance()))
        val answer = childReviewResult(child)
        When("an answer self-claims review evidence or is posted to the parent's semantic completion")
        val unregistered = h.completeChildReview(answer)
        val wrongRoute = h.invoke("submitGoalPhaseWorkResult", take(WorkflowResultJsonV1.encodeC(answer,
          RepositorySyncNativeMergeReview.resultCodec)))
        Then("both fail without consuming the child or closing the Phase")
        unregistered.isSuccess shouldBe false
        wrongRoute.isSuccess shouldBe false
        childReviewWork(take(h.advance())) shouldBe child
        h.driver.count("sm-workflow-git-commit-pending") shouldBe 0
        h.driver.count("sm-workflow-git-push") shouldBe 0
        Given("the independently enrolled child review with a Human-required disposition")
        h.registerChildReview(child)
        When("the child completion returns decision-needed rather than acceptance")
        take(h.completeChildReview(childReviewResult(child, "decision-needed")))
        val first = take(h.advance()).print
        Then("owning Goal advance returns the actual child WAIT, not consent or a Goal Terminal")
        take(WorkflowWaitJsonV1.decodeC(first)).handle.instanceIdentity.value shouldBe child.handle.instanceIdentity.value
        take(h.advance()).print shouldBe first
        h.driver.count("sm-workflow-git-commit-pending") shouldBe 0
        h.driver.count("sm-workflow-git-push") shouldBe 0
      } finally h.close()
    }
    "retain a Human-required child conflict without automatic semantic choice or parent closure" in {
      Given("a real owned native conflict reached from an accepted whole Phase")
      val e = episode()
      val h = new Host(e, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "allow",
        phaseMode = "pass", repositoryMode = "allow", repositoryScenario = "conflict")
      try {
        val review = work(take(closeSteps(h, e, 2))); h.registerReview(review)
        val accepted = take(h.complete(reviewResult(review))); h.registerRepository(review, accepted)
        val child = childConflictWork(take(h.advance()))
        When("the assigned child semantic completion cannot decide and requests Human intervention")
        take(h.completeChildConflict(childConflictResult(child, "decision-needed")))
        val first = take(h.advance()).print
        Then("the original child WAIT persists and no materialization, check, commit, push or Goal Terminal is fabricated")
        take(WorkflowWaitJsonV1.decodeC(first)).handle.instanceIdentity.value shouldBe child.handle.instanceIdentity.value
        take(h.advance()).print shouldBe first
        h.driver.count("sm-workflow-repository-conflict-files") shouldBe 1
        h.driver.count("sm-workflow-repository-validation-4") shouldBe 0
        h.driver.count("sm-workflow-git-commit-pending") shouldBe 0
        h.driver.count("sm-workflow-git-push") shouldBe 0
      } finally h.close()
    }
    "resume a genuine child conflict Decision only after independently admitted Human resolution" in {
      Given("an accepted whole Phase whose actual child conflict has a paired independent Human channel")
      val e = episode()
      val first = new Host(e, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "allow",
        phaseMode = "pass", repositoryMode = "allow", repositoryScenario = "conflict", repositoryHuman = true)
      var phaseReview: WorkflowInteraction[S.Request, Nothing] = null
      var accepted: Response = null
      var question: WorkflowInteraction[RepositorySyncHumanConflict.Input, Nothing] = null
      var semantic: ContinuationResult[RepositorySyncConflictSemantic.Result] = null
      try {
        phaseReview = work(take(closeSteps(first, e, 2))); first.registerReview(phaseReview)
        accepted = take(first.complete(reviewResult(phaseReview))); first.registerRepository(phaseReview, accepted)
        val conflict = childConflictWork(take(first.advance())); semantic = childConflictResult(conflict, "decision-needed")
        When("the assigned AI reports DecisionNeeded and owning advance publishes an actual child Decision")
        take(first.completeChildConflict(semantic)); question = childHumanQuestion(take(first.advance()))
        Then("the question retains the original native conflict and requesting result, with its own Human completion route")
        val order = question.current.asInstanceOf[WorkflowContinuation.Decision[RepositorySyncHumanConflict.Input]].request
        order.completionOperation shouldBe RepositorySyncHumanConflict.completionOperation
        order.input.get.value.conflict shouldBe conflict.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].request.input.get.value
        order.input.get.value.requestingResultIdentity shouldBe semantic.resultReference.identity
        childHumanQuestion(take(first.advance())) shouldBe question
        first.driver.count("sm-workflow-repository-conflict-files") shouldBe 1
        first.driver.count("sm-workflow-git-commit-pending") shouldBe 0
        first.driver.count("sm-workflow-git-push") shouldBe 0
      } finally first.close()
      Given("physical pool reopen retaining the genuine child Decision, without Plan or new Step authority")
      val later = new Host(e, missingPlan = true, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "deny",
        phaseMode = "pass", repositoryMode = "allow", repositoryScenario = "conflict", repositoryHuman = true)
      try {
        later.registerRepository(phaseReview, accepted)
        childHumanQuestion(take(later.advance())) shouldBe question
        val answer = childHumanAnswer(question)
        When("a self-claimed Human answer or unassigned conflict ID is submitted")
        later.completeChildHuman(answer).isSuccess shouldBe false
        later.registerChildHuman(question, answer)
        val wrong = answer.copy(result = answer.result.copy(value = answer.result.value.copy(
          resolutions = Vector(RepositorySyncConflictSemantic.Resolution("unassigned", "outside")))))
        later.completeChildHuman(wrong).isSuccess shouldBe false
        Then("the issue is not consumed and no file or Git command runs")
        childHumanQuestion(take(later.advance())) shouldBe question
        later.driver.count("sm-workflow-repository-conflict-files") shouldBe 0
        When("the actual independently enrolled complete Human answer is saved")
        take(later.completeChildHuman(answer))
        take(later.completeChildHuman(answer))
        Then("completion and historical duplicate observation execute no materialization, commit or push")
        later.driver.count("sm-workflow-repository-conflict-files") shouldBe 0
        later.driver.count("sm-workflow-git-commit-pending") shouldBe 0
        later.driver.count("sm-workflow-git-push") shouldBe 0
        When("owning advance materializes/checks the Human proposal and the assigned independent review is accepted")
        val review = childReviewWork(take(later.advance())); later.registerChildReview(review)
        take(later.completeChildReview(childReviewResult(review)))
        val wire = take(later.advance()).print
        val closed = take(WorkflowTerminalJsonV1.decodeC(wire, GoalPhaseNativeRepositoryClosure.resultCodec))
          .current.asInstanceOf[WorkflowContinuation.Terminal[GoalPhaseNativeRepositoryClosure.Result]].result.value
        Then("only actual authorized child convergence closes the original whole Phase")
        closed.plan shouldBe e.plan
        closed.completed.map(_.step) shouldBe Vector("S1", "S2")
        later.planReads shouldBe 0
        later.driver.count("sm-workflow-git-pending-merge") shouldBe 0
        later.driver.count("sm-workflow-repository-validation-4") shouldBe 1
        later.driver.count("sm-workflow-git-commit-pending") shouldBe 1
        later.driver.count("sm-workflow-git-push") shouldBe 1
        take(later.advance()).print shouldBe wire
      } finally later.close()
    }
    "retain independently admitted Human Defer and Cancel without resolving or aborting the child repository" in {
      for (choice <- Vector("Defer", "Cancel")) {
        Given("an actual child conflict Decision with independently enrolled fixture Human direction")
        val e = episode()
        val h = new Host(e, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "allow",
          phaseMode = "pass", repositoryMode = "allow", repositoryScenario = "conflict", repositoryHuman = true)
        try {
          val phase = work(take(closeSteps(h, e, 2))); h.registerReview(phase)
          val accepted = take(h.complete(reviewResult(phase))); h.registerRepository(phase, accepted)
          val conflict = childConflictWork(take(h.advance()))
          take(h.completeChildConflict(childConflictResult(conflict, "decision-needed")))
          val question = childHumanQuestion(take(h.advance()))
          val answer = childHumanAnswer(question, choice); h.registerChildHuman(question, answer)
          When("Human defers or cancels this Decision path and owning advance is repeated")
          take(h.completeChildHuman(answer))
          val first = take(h.advance()).print
          Then("the retained child WAIT is stable without automatic resolution, abort, new question or Goal closure")
          take(WorkflowWaitJsonV1.decodeC(first)).handle.instanceIdentity shouldBe question.handle.instanceIdentity
          take(h.advance()).print shouldBe first
          h.driver.count("sm-workflow-repository-conflict-files") shouldBe 1
          h.driver.count("sm-workflow-repository-validation-4") shouldBe 0
          h.driver.count("sm-workflow-git-commit-pending") shouldBe 0
          h.driver.count("sm-workflow-git-push") shouldBe 0
        } finally h.close()
      }
    }
    "recover an acknowledged-lost native Human issue without issuing another question or replaying conflict capture" in {
      Given("an actual accepted whole Phase and interruption after its child Human question is committed")
      val e = episode()
      var issueAcknowledgements = 0
      val interrupted: String => Consequence[Unit] = stage =>
        if (stage == "human-conflict-issued") {
          issueAcknowledgements += 1
          F.invalid("owned interruption after genuine Human issue")
        } else Consequence.unit
      val first = new Host(e, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "allow",
        phaseMode = "pass", repositoryMode = "allow", repositoryScenario = "conflict", repositoryHuman = true,
        acknowledgement = interrupted)
      var phase: WorkflowInteraction[S.Request, Nothing] = null
      var accepted: Response = null
      var question: WorkflowInteraction[RepositorySyncHumanConflict.Input, Nothing] = null
      try {
        phase = work(take(closeSteps(first, e, 2))); first.registerReview(phase)
        accepted = take(first.complete(reviewResult(phase))); first.registerRepository(phase, accepted)
        val conflict = childConflictWork(take(first.advance()))
        take(first.completeChildConflict(childConflictResult(conflict, "decision-needed")))
        When("owning advance commits the genuine question but its acknowledgement fails")
        first.advance().isSuccess shouldBe false
        Then("the response is not accepted and no materialization, check or convergence is fabricated")
        issueAcknowledgements shouldBe 1
        first.independentHumanAdmissions shouldBe 0
        first.driver.count("sm-workflow-repository-conflict-files") shouldBe 1
        first.driver.count("sm-workflow-repository-validation-4") shouldBe 0
        first.driver.count("sm-workflow-git-commit-pending") shouldBe 0
        first.driver.count("sm-workflow-git-push") shouldBe 0
        When("the same owning advance observes the committed native question")
        question = childHumanQuestion(take(first.advance()))
        Then("no second publication acknowledgement or Human grant is needed")
        issueAcknowledgements shouldBe 1
        first.independentHumanAdmissions shouldBe 0
      } finally first.close()
      Given("a physically reopened pool with no Plan, Step authority or Human evidence enrollment")
      val later = new Host(e, missingPlan = true, candidate = true, checkMode = "pass", reviewMode = "allow",
        closureMode = "deny", phaseMode = "pass", repositoryMode = "allow", repositoryScenario = "conflict",
        repositoryHuman = true, acknowledgement = interrupted)
      try {
        later.registerRepository(phase, accepted)
        When("owning advance reads the actual retained question twice")
        childHumanQuestion(take(later.advance())) shouldBe question
        childHumanQuestion(take(later.advance())) shouldBe question
        Then("the child handle and revision remain exact without publication, consent or external replay")
        issueAcknowledgements shouldBe 1
        later.independentHumanAdmissions shouldBe 0
        later.planReads shouldBe 0
        later.driver.count("sm-workflow-git-pending-merge") shouldBe 0
        later.driver.count("sm-workflow-repository-conflict-files") shouldBe 0
        later.driver.count("sm-workflow-repository-validation-4") shouldBe 0
        later.driver.count("sm-workflow-git-commit-pending") shouldBe 0
        later.driver.count("sm-workflow-git-push") shouldBe 0
      } finally later.close()
    }
    "observe committed Human answers after lost acknowledgement without regranting consent or replacing the saved answer" in {
      for (choice <- Vector("ProvideResolution", "Defer", "Cancel")) {
        Given("the actual independently enrolled child Human " + choice + " answer and a post-commit interruption")
        val e = episode()
        var completedAcknowledgements = 0
        val interrupted: String => Consequence[Unit] = stage =>
          if (stage == "human-conflict-completed") {
            completedAcknowledgements += 1
            F.invalid("owned interruption after native Human completion")
          } else Consequence.unit
        val first = new Host(e, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "allow",
          phaseMode = "pass", repositoryMode = "allow", repositoryScenario = "conflict", repositoryHuman = true,
          acknowledgement = interrupted)
        var phase: WorkflowInteraction[S.Request, Nothing] = null
        var accepted: Response = null
        var question: WorkflowInteraction[RepositorySyncHumanConflict.Input, Nothing] = null
        var answer: ContinuationResult[RepositorySyncHumanConflict.Answer] = null
        try {
          phase = work(take(closeSteps(first, e, 2))); first.registerReview(phase)
          accepted = take(first.complete(reviewResult(phase))); first.registerRepository(phase, accepted)
          val conflict = childConflictWork(take(first.advance()))
          take(first.completeChildConflict(childConflictResult(conflict, "decision-needed")))
          question = childHumanQuestion(take(first.advance())); answer = childHumanAnswer(question, choice)
          first.registerChildHuman(question, answer)
          When("the actual completion Operation saves the answer but its post-commit response fails")
          val interruptedcompletion = first.completeChildHuman(answer)
          Then("only one independent consent admission and completion acknowledgement occurred, with no file or Git effect")
          interruptedcompletion.isSuccess shouldBe false
          first.independentHumanAdmissions shouldBe 1
          completedAcknowledgements shouldBe 1
          first.driver.count("sm-workflow-repository-conflict-files") shouldBe 1
          first.driver.count("sm-workflow-repository-validation-4") shouldBe 0
          first.driver.count("sm-workflow-git-commit-pending") shouldBe 0
          first.driver.count("sm-workflow-git-push") shouldBe 0
        } finally first.close()
        Given("a physical pool reopen with an empty independent Human channel and unchanged actual Phase authority")
        val later = new Host(e, missingPlan = true, candidate = true, checkMode = "pass", reviewMode = "allow",
          closureMode = "deny", phaseMode = "pass", repositoryMode = "allow", repositoryScenario = "conflict",
          repositoryHuman = true, acknowledgement = interrupted)
        try {
          later.registerRepository(phase, accepted)
          When("the retained answer is observed through its declared completion Operation with original or changed rationale diagnostics")
          val historical = take(later.completeChildHuman(answer)).print
          val repeated = take(later.completeChildHuman(answer)).print
          val changed = answer.copy(result = answer.result.copy(value = answer.result.value.copy(
            rationale = answer.result.value.rationale + " replacement")))
          val diagnostic = take(later.completeChildHuman(changed)).print
          Then("historical observation needs no new Human grant or acknowledgement and cannot replace the original answer")
          repeated shouldBe historical
          diagnostic shouldBe historical
          later.independentHumanAdmissions shouldBe 0
          completedAcknowledgements shouldBe 1
          val retained = take(WorkflowWaitJsonV1.decodeC(historical))
          retained.handle.instanceIdentity shouldBe question.handle.instanceIdentity
          retained.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value.toLong shouldBe
            answer.expectedRevision.value.toLong + 1
          later.driver.count("sm-workflow-repository-conflict-files") shouldBe 0
          later.driver.count("sm-workflow-repository-validation-4") shouldBe 0
          later.driver.count("sm-workflow-git-commit-pending") shouldBe 0
          later.driver.count("sm-workflow-git-push") shouldBe 0
          When("the retry instead selects a different semantic Human direction")
          val otherchoice = if (choice == "Cancel") "Defer" else "Cancel"
          val replacement = answer.copy(result = answer.result.copy(value = answer.result.value.copy(
            choice = otherchoice, resolutions = Vector.empty)))
          val rejected = later.completeChildHuman(replacement)
          Then("the changed direction is refused without a new consent grant, acknowledgement or repository effect")
          rejected.isSuccess shouldBe false
          later.independentHumanAdmissions shouldBe 0
          completedAcknowledgements shouldBe 1
          later.driver.count("sm-workflow-repository-conflict-files") shouldBe 0
          later.driver.count("sm-workflow-git-push") shouldBe 0
          When("owning advance resumes only the saved Human direction")
          if (choice == "ProvideResolution") {
            val review = childReviewWork(take(later.advance())); later.registerChildReview(review)
            take(later.completeChildReview(childReviewResult(review)))
            val wire = take(later.advance()).print
            val closed = take(WorkflowTerminalJsonV1.decodeC(wire, GoalPhaseNativeRepositoryClosure.resultCodec))
              .current.asInstanceOf[WorkflowContinuation.Terminal[GoalPhaseNativeRepositoryClosure.Result]].result.value
            Then("only later authorized native child convergence closes the entire original Phase, once")
            closed.plan shouldBe e.plan
            closed.completed.map(_.step) shouldBe Vector("S1", "S2")
            take(later.advance()).print shouldBe wire
            later.driver.count("sm-workflow-repository-conflict-files", Vector("capture")) shouldBe 0
            later.driver.count("sm-workflow-repository-conflict-files", Vector("observe")) shouldBe 2
            later.driver.count("sm-workflow-repository-conflict-files", Vector("apply")) shouldBe 1
            later.driver.count("sm-workflow-repository-validation-4") shouldBe 1
            later.driver.count("sm-workflow-git-commit-pending") shouldBe 1
            later.driver.count("sm-workflow-git-push") shouldBe 1
          } else {
            val wire = take(later.advance()).print
            Then("Defer or Cancel remains the same stable child WAIT without resolution, abort or Phase closure")
            wire shouldBe historical
            take(later.advance()).print shouldBe wire
            later.driver.count("sm-workflow-repository-conflict-files") shouldBe 0
            later.driver.count("sm-workflow-repository-validation-4") shouldBe 0
            later.driver.count("sm-workflow-git-commit-pending") shouldBe 0
            later.driver.count("sm-workflow-git-push") shouldBe 0
          }
          later.independentHumanAdmissions shouldBe 0
          completedAcknowledgements shouldBe 1
          later.planReads shouldBe 0
          later.driver.count("sm-workflow-git-pending-merge") shouldBe 0
        } finally later.close()
      }
    }
    "refuse missing or crossed Phase authority and missing child installation before remote effects" in {
      for (mode <- Vector("deny", "crossed", "missing-install", "denied-effect")) {
        Given("an actually accepted whole Phase but absent, crossed or effect-ineligible child authority/installation")
        val e = episode()
        val h = new Host(e, selected = Some(e.plan.copy(steps = e.plan.steps.take(1))), candidate = true,
          checkMode = "pass", reviewMode = "allow", closureMode = "allow", phaseMode = "pass", repositoryMode = mode)
        try {
          val review = work(take(closeSteps(h, e, 1))); h.registerReview(review)
          val accepted = take(h.complete(reviewResult(review))); h.registerRepository(review, accepted)
          When("owning advance requests actual authorized repository closure")
          val advanced = h.advance()
          Then("no missing grant is inferred from Phase review and no push or Goal Terminal is produced")
          if (mode == "deny") take(WorkflowWaitJsonV1.decodeC(take(advanced).print)).handle.instanceIdentity.value shouldBe e.identity
          else advanced.isSuccess shouldBe false
          h.driver.count("sm-workflow-git-push") shouldBe 0
        } finally h.close()
      }
    }
    "return a genuine nonterminal child wait rather than closing the Phase after failed repository checks" in {
      Given("an accepted Phase with a real installed child and a failing fixed child check")
      val e = episode()
      val h = new Host(e, selected = Some(e.plan.copy(steps = e.plan.steps.take(1))), candidate = true,
        checkMode = "pass", reviewMode = "allow", closureMode = "allow", phaseMode = "pass", repositoryMode = "failed-check")
      try {
        val review = work(take(closeSteps(h, e, 1))); h.registerReview(review)
        val accepted = take(h.complete(reviewResult(review))); h.registerRepository(review, accepted)
        When("actual child validation fails and owning Phase advances twice")
        val first = take(h.advance()).print
        val second = take(h.advance()).print
        Then("the child's own WAIT/identity is returned without replay, push or Phase completion")
        val wait = take(WorkflowWaitJsonV1.decodeC(first))
        wait.handle.instanceIdentity.value should startWith("repository-sync:")
        second shouldBe first
        h.driver.count("sm-workflow-repository-validation-4") shouldBe 1
        h.driver.count("sm-workflow-git-push") shouldBe 0
      } finally h.close()
    }
    "recover recorded child convergence across interrupted Phase acknowledgements without repeating Git effects" in {
      for (point <- Vector("prepared", "executed", "recorded", "completed")) {
        Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R6; Example: E14; an actual accepted Phase with different authority presentation and an owning child-closure interruption")
        val e = episode()
        val h = new Host(e, selected = Some(e.plan.copy(steps = e.plan.steps.take(1))), candidate = true,
          checkMode = "pass", reviewMode = "allow", closureMode = "allow", phaseMode = "pass", repositoryMode = "allow",
          repositoryAuthorityPresentation = true,
          acknowledgement = stage => if (stage == "goal-repository-" + point) F.invalid("owned Phase closure interruption") else Consequence.unit)
        var review: WorkflowInteraction[S.Request, Nothing] = null
        var accepted: Response = null
        try {
          review = work(take(closeSteps(h, e, 1))); h.registerReview(review)
          accepted = take(h.complete(reviewResult(review))); h.registerRepository(review, accepted)
          When("advance reaches that real prepared/executed/recorded/completed boundary")
          h.advance().isSuccess shouldBe false
          h.driver.count("sm-workflow-git-push") shouldBe (if (point == "prepared") 0 else 1)
        } finally h.close()
        Given("physical pool reopen retains the original child selection while the current installation projects another new-invocation selection")
        val later = new Host(e, missingPlan = true, candidate = true, checkMode = "pass", reviewMode = "allow",
          closureMode = "deny", phaseMode = "pass", repositoryMode = "allow",
          repositoryAuthorityPresentation = true, repositoryInstallationSelection = "new-projection-not-the-saved-child")
        try {
          later.registerRepository(review, accepted)
          When("owning advance resumes from canonical parent and child records")
          val first = take(later.advance()).print
          Then("a genuine Goal Terminal is admitted without repeating any already executed push")
          val terminal = take(WorkflowTerminalJsonV1.decodeC(first, GoalPhaseNativeRepositoryClosure.resultCodec))
          terminal.handle.instanceIdentity.value shouldBe e.identity
          terminal.current.asInstanceOf[WorkflowContinuation.Terminal[GoalPhaseNativeRepositoryClosure.Result]]
            .result.value.plan shouldBe e.plan.copy(steps = e.plan.steps.take(1))
          take(later.advance()).print shouldBe first
          later.driver.count("sm-workflow-git-push") shouldBe (if (point == "prepared") 1 else 0)
          later.driver.count("sm-workflow-repository-validation-4") shouldBe (if (point == "prepared") 1 else 0)
          later.planReads shouldBe 0
        } finally later.close()
      }
    }
    "observe interrupted prepared or executed Step commits without replay or fabricated next-Step acceptance" in {
      Vector("prepared", "executed", "recorded").foreach { point =>
        Given("a current accepted and authorized Step with an interruption at a selected commit acknowledgement")
        val e = episode(); val h = new Host(e, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "allow",
          acknowledgement = stage => if (stage == s"goal-step-commit-$point:S1") F.invalid("owned commit interruption") else Consequence.unit)
        try {
          h.prepare(); take(h.start()); val implementation = work(take(h.advance()))
          Files.writeString(e.target.root.resolve("one.scala"), "object InterruptedClosure\n")
          take(h.complete(result(implementation))); val review = work(take(h.advance())); h.registerReview(review)
          val accepted = take(h.complete(reviewResult(review))); h.registerClosure(review, accepted)
          When("advance reaches the selected prepared/executed/recorded acknowledgement")
          h.advance().isSuccess shouldBe false
          Then("the actual command count reflects the interruption boundary")
          h.driver.count("sm-workflow-git-goal-commit-step-1") shouldBe (if (point == "prepared") 0 else 1)
        } finally h.close()
        Given("Spec: docs/spec/goal-phase-native-retention-applicability.md; Rule: R5; Example: E11; a physically reopened host with changed closure policy metadata and no new closure authority")
        val later = new Host(e, missingPlan = true, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "changed-policy")
        try {
          When("the owning frontier is observed twice")
          val first = take(later.advance()); val second = take(later.advance())
          Then("a recorded commit admits only the next Step; an unrecorded effect remains open without automatic replay")
          if (point == "recorded") {
            val assigned = work(first)
            work(second) shouldBe assigned
            assigned.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value.completed.map(_.step) shouldBe Vector("S1")
          } else {
            val wait = take(WorkflowWaitJsonV1.decodeC(first.print))
            take(WorkflowWaitJsonV1.decodeC(second.print)) shouldBe wait
            wait.current.asInstanceOf[WorkflowContinuation.Wait].presentation.currentSituation should include("No automatic replay")
          }
          later.driver.count("sm-workflow-git-goal-commit-step-1") shouldBe 0
          later.driver.count("sm-workflow-repository-validation-1") shouldBe 0
        } finally later.close()
        Given("the current selected commit program actually resolves to another binding")
        val incompatible = new Host(e, missingPlan = true, candidate = true, checkMode = "pass", reviewMode = "allow", closureMode = "changed-binding")
        try {
          When("the saved commit slot is resolved under that incompatible program")
          val refused = incompatible.advance()
          Then("real executable incompatibility refuses without replaying commit or checks")
          refused.isSuccess shouldBe false
          incompatible.driver.count("sm-workflow-git-goal-commit-step-1") shouldBe 0
          incompatible.driver.count("sm-workflow-repository-validation-1") shouldBe 0
        } finally incompatible.close()
      }
    }
  }
}
