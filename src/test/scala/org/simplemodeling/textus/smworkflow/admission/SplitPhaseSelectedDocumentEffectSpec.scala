package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import io.circe.{Json, parser}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, ProcedureActionCall}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, RuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition as M
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.jdk.CollectionConverters.*
import SplitPhaseExplicitPartitionFixture.*
import SplitPhaseExplicitPartitionRawCodecs.given
import org.simplemodeling.textus.smworkflow.runtime.SelectedPhaseDocumentProgram as Program
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningSnapshot as R,
  SplitPhaseExplicitPlanningAdvance as W, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitObjectiveSelection as O, WorkflowManagedProcessExecution as Process}

/** Actual runtime-owned JVM/file effects through canonical ProcessExec. This
  * does not claim installed public APPLY or native application completion.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
final class SplitPhaseSelectedDocumentEffectSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  /** Observation around the real runtime driver, not a substituted result or
    * access to its package-private process diagnostic seam. */
  private final class ObservedDriver extends ProcessExecutionDriver with AutoCloseable {
    private val delegate = new LocalProcessExecutionDriver()
    private val starts = new java.util.concurrent.atomic.AtomicInteger()
    private val completions = new java.util.concurrent.atomic.AtomicInteger()
    def safeIdentity: String = delegate.safeIdentity
    def startedCount: Int = starts.get()
    def completedCount: Int = completions.get()
    def startC(execution: ResolvedProcessExecution): Consequence[ProcessExecutionHandle] = {
      starts.incrementAndGet()
      delegate.startC(execution).map(observe)
    }
    override def startC(execution: ResolvedProcessExecution, area: ProcessExecutionWorkArea): Consequence[ProcessExecutionHandle] = {
      starts.incrementAndGet()
      delegate.startC(execution, area).map(observe)
    }
    private def observe(handle: ProcessExecutionHandle): ProcessExecutionHandle = new ProcessExecutionHandle {
      def awaitC: Consequence[ProcessExecutionResult] = handle.awaitC.map { result =>
        completions.incrementAndGet()
        result
      }
      def cancelC: Consequence[Unit] = handle.cancelC
    }
    def close(): Unit = delegate.close()
  }
  private final case class Fixture(root: Path, snapshot: R.Record, input: String) {
    def source: Path = root.resolve("docs/phase/phase-77.md")
    def documents = take(SplitPhaseSelectedDocuments.projectC(snapshot))
  }
  private val repository = "repo"
  private val instance = "test-selected-apply"
  private def fixture(minutes: Vector[Int] = Vector(360, 360)): Fixture = {
    val s = base(minutes)
    val input = take(E.loadC(s.initial, s.records, s.records))
    val data = W.Data(W.DataReference("document-effect-input", "1"), s.source.reference, s.source.authority,
      None, O.Records(None, None, None))
    val need = take(W.advanceC(take(W.selectC(input, data, data)))).asInstanceOf[W.AllocationRequired]
    val selected = take(W.continueMechanicalAllocatedC(take(SplitPhaseCandidateAllocationFixture.policy
      .allocateMechanicalC(need, M.Reference("document-effect", "1")))))
    val snapshot = take(R.projectC(selected)).record
    val work = Files.createDirectories(Path.of("target/sm-workflow/work/selected-document-effects").toAbsolutePath)
    val root = Files.createTempDirectory(work, "repository-")
    Files.createDirectories(root.resolve("docs/phase"))
    val original = (Vector("# Phase 77", "", "## Scope", "", "| ID | Outcome | Status |", "| --- | --- | --- |") ++
      s.inventory.orderedUnfinishedItems.map(i => s"| ${i.value} | original work | planned |") ++
      Vector("| old-completed | previous accepted work | completed |",
        "| ID | completed identity resembling a header | completed |",
        "| old---completed | completed identity containing dashes | completed |", "", "## History", "",
        "User history and unrelated notes remain here.", "", "## Source boundary", "",
        "```" + Program.sourceSchema, s.sourceBoundary.asJson.noSpaces, "```", "")).mkString("\n")
    Files.writeString(root.resolve("docs/phase/phase-77.md"), original)
    Fixture(root, snapshot, take(Program.requestC(repository, instance, "context", "1", snapshot)))
  }
  private def withRuntime[A](f: Fixture, grant: Boolean = true)(run: (ActionCall.Core, ObservedDriver, ProcessExecutionAdmission) => A): A = {
    val capability = take(ProcessCapabilityId.parseC("sm-workflow-selected-documents"))
    // Official preparation is wired into Test/compile; no SBT/resolver starts here.
    val classpathFile = Path.of("target/cncf.d/runtime-classpath.txt")
    Files.isRegularFile(classpathFile) shouldBe true
    val classpath = Files.readAllLines(classpathFile).asScala.map(_.trim).filter(_.nonEmpty).mkString(java.io.File.pathSeparator)
    val javaExecutable = Path.of(System.getProperty("java.home"), "bin/java").toString
    val fixed = Vector("-cp", classpath,
      "org.simplemodeling.textus.smworkflow.runtime.SelectedPhaseDocumentProgram", f.root.toString, repository)
    val limits = ProcessExecutionLimits(Some(10000), Some(30000), Some(1000), Some(2097152),
      Some(131072), Some(16384), Some(1), Some(16), Some(1), Some(131072), Some(4194304))
    val definition = take(ProcessProgramDefinition.fromRuntimeC(capability, "sm-workflow-selected-documents", javaExecutable,
      fixed, ProcessArgumentPolicy(fixed, Set.empty, Set(Vector("observe"), Vector("apply"))), limits, Set.empty))
    val policy = take(ProcessExecutionPolicy.createC(Vector(definition)))
    val admission = take(ProcessExecutionAdmission.createC(policy, if (grant) Vector(ProcessExecutionGrant(capability)) else Vector.empty))
    val driver = new ObservedDriver()
    GlobalContext.set(GlobalContext(WorkAreaSpace.create(RuntimeConfig.default)))
    val base = ExecutionContext.create()
    val scope = ScopeContext(ScopeKind.Runtime, "selected-document-runtime", None, base.observability,
      processexecutiondriveroption = Some(driver), processexecutionadmissionoption = Some(admission))
    // ExecutionContext.create() alone is an intentionally inert test runtime.
    // This host installs the real interpreter and its resource lifecycle; the
    // product ProcedureActionCall still executes its canonical program normally.
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val unitOfWork: UnitOfWork = new UnitOfWork(context)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(ScopeKind.Runtime, "selected-document-host", Some(scope), base.observability, None),
      unitofworksupplier = () => unitOfWork,
      unitofworkinterpreterfn = new (cats.~>[UnitOfWorkOp, Consequence]) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] = new UnitOfWorkInterpreter(unitOfWork).interpret(operation)
      },
      commitaction = uow => { take(uow.commit()); () },
      abortaction = uow => { take(uow.abort()); () },
      disposeaction = uow => { take(uow.dispose()); () },
      token = "selected-document-host")
    val action = new CommandAction {
      val request = Request(None, Some("SplitPhaseService"), "advanceSplitPhase", Nil, Nil, Nil)
      def createCall(callCore: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = callCore
        def execute(): Consequence[OperationResponse] = Consequence.stateConflict("test context only")
      }
    }
    val core = ActionCall.Core(action, context, None, None)
    try run(core, driver, admission) finally {
      try runtime.dispose() finally driver.close()
    }
  }
  private def execute(core: ActionCall.Core, f: Fixture, purpose: Process.Purpose): (ProcessExecutionResult, Json) = {
    val result = take(Process.executeC(core, purpose, f.input))
    result.termination shouldBe ProcessExecutionTermination.Exited(0)
    result.stdout.truncated shouldBe false
    val json = take(parser.parse(new String(result.stdout.content.toArray, StandardCharsets.UTF_8))
      .fold(e => Consequence.stateConflict(e.message), Consequence.success))
    json.hcursor.get[String]("schemaVersion").toOption shouldBe Some(Program.resultSchema)
    json.hcursor.get[String]("instanceIdentity").toOption shouldBe Some(instance)
    json.hcursor.get[String]("repositoryIdentity").toOption shouldBe Some(repository)
    json.hcursor.get[Vector[String]]("paths").toOption shouldBe Some(f.documents.documents.map(_.relativePath))
    result -> json
  }
  private def status(json: Json): String = json.hcursor.get[String]("status").toOption.get

  "Managed selected document process" should {
    "observe, apply and reopen real files without selecting an executable in the component" in {
      Given("a project-owned repository and a runtime-fixed JVM, classpath, repository and complete argument vectors")
      val f = fixture()
      val original = Files.readString(f.source)
      withRuntime(f) { (core, driver, _) =>
        When("canonical ProcessExec runs the logical observe purpose")
        status(execute(core, f, Process.Purpose.ObserveSelectedDocuments)._2) shouldBe "ready"
        Then("no Phase document is changed by observation")
        Files.readString(f.source) shouldBe original
        Files.exists(f.root.resolve("docs/phase/phase-77.1.md")) shouldBe false
        When("canonical ProcessExec applies the saved selected plan")
        status(execute(core, f, Process.Purpose.ApplySelectedDocuments)._2) shouldBe "applied"
        Then("actual source sections and child documents retain prior history and accepted work")
        Files.readString(f.source) should include("User history and unrelated notes remain here.")
        Files.readString(f.source) should include("| old-completed | previous accepted work | completed |")
        Files.readString(f.source) should include("| ID | completed identity resembling a header | completed |")
        Files.readString(f.source) should include("| old---completed | completed identity containing dashes | completed |")
        Files.readString(f.source) should include("## Split validation")
        f.documents.documents.tail.foreach(d => Files.readString(f.root.resolve(d.relativePath)) shouldBe d.childDocument.get)
        driver.startedCount shouldBe 2
        driver.completedCount shouldBe driver.startedCount
      }
      val timestamp = Files.getLastModifiedTime(f.source)
      Given("a physically new runtime and separately launched JVM programs after successful application")
      withRuntime(f) { (core, _, _) =>
        When("observation and application are repeated")
        status(execute(core, f, Process.Purpose.ObserveSelectedDocuments)._2) shouldBe "applied"
        status(execute(core, f, Process.Purpose.ApplySelectedDocuments)._2) shouldBe "applied"
        Then("the recorded plan is observed without another source write or duplicate section")
        Files.getLastModifiedTime(f.source) shouldBe timestamp
        Files.readString(f.source).linesIterator.count(_ == "## Split partition contract") shouldBe 1
      }
    }

    "resume a partial child installation while leaving already installed children untouched" in {
      Given("a labelled crash-position fixture: one child installed and the source still at its original application contract")
      val f = fixture(Vector(360, 360, 360))
      val child = f.documents.documents.last
      val installed = f.root.resolve(child.relativePath)
      Files.writeString(installed, child.childDocument.get)
      val timestamp = Files.getLastModifiedTime(installed)
      withRuntime(f) { (core, _, _) =>
        When("a new actual process observes and applies the same saved plan")
        status(execute(core, f, Process.Purpose.ObserveSelectedDocuments)._2) shouldBe "partial"
        status(execute(core, f, Process.Purpose.ApplySelectedDocuments)._2) shouldBe "applied"
        Then("only pending files are installed, with complete actual readback")
        Files.getLastModifiedTime(installed) shouldBe timestamp
        status(execute(core, f, Process.Purpose.ObserveSelectedDocuments)._2) shouldBe "applied"
      }
    }

    "preserve newly completed selected work before first Apply and partial-child resume" in {
      for (completedstatus <- Vector("complete", "completed", "done", "accepted"); partial <- Vector(false, true)) {
        Given(s"a saved unfinished plan whose last selected source item is now $completedstatus, with partial children=$partial")
        val f = fixture(Vector(360, 360, 360))
        val identity = f.snapshot.selection.boundary.boundary.items.last.identity.value
        val originalrow = s"| $identity | original work | planned |"
        val original = Files.readString(f.source)
        original should include(originalrow)
        Files.writeString(f.source, original.replace(originalrow, s"| $identity | original work | $completedstatus |"))
        if (partial) {
          val child = f.documents.documents.last
          Files.writeString(f.root.resolve(child.relativePath), child.childDocument.get)
        }
        val before = f.documents.documents.map { document =>
          val path = f.root.resolve(document.relativePath)
          document.relativePath -> Option.when(Files.exists(path))(Files.readAllBytes(path).toVector)
        }.toMap
        withRuntime(f) { (core, driver, _) =>
          When("actual managed JVM processes observe and attempt the same saved application")
          val results = Vector(Process.Purpose.ObserveSelectedDocuments, Process.Purpose.ApplySelectedDocuments)
            .map(purpose => execute(core, f, purpose)._2)
          Then("both refuse the source preimage and neither create, replace nor regress any selected document")
          results.foreach { json =>
            status(json) shouldBe "conflict"
            json.hcursor.get[Vector[String]]("conflictingPaths").toOption shouldBe Some(Vector("docs/phase/phase-77.md"))
          }
          f.documents.documents.foreach { document =>
            val path = f.root.resolve(document.relativePath)
            Option.when(Files.exists(path))(Files.readAllBytes(path).toVector) shouldBe before(document.relativePath)
          }
          driver.startedCount shouldBe 2
          driver.completedCount shouldBe 2
        }
      }
    }

    "refuse an occupied child before any write rather than choosing an automatic conflict resolution" in {
      Given("an independently owned child file at the selected target path")
      val f = fixture()
      val original = Files.readString(f.source)
      val child = f.root.resolve("docs/phase/phase-77.1.md")
      Files.writeString(child, "Independent existing child\n")
      withRuntime(f) { (core, _, _) =>
        When("observation and application encounter that conflict")
        val observed = execute(core, f, Process.Purpose.ObserveSelectedDocuments)._2
        val applied = execute(core, f, Process.Purpose.ApplySelectedDocuments)._2
        Then("both report the exact conflicting path and preserve source and independent child")
        Vector(observed, applied).foreach { json =>
          status(json) shouldBe "conflict"
          json.hcursor.get[Vector[String]]("conflictingPaths").toOption shouldBe Some(Vector("docs/phase/phase-77.1.md"))
        }
        Files.readString(f.source) shouldBe original
        Files.readString(child) shouldBe "Independent existing child\n"
      }
    }

    "reject changed source scope or authority as an application contradiction, not a whole-file seal" in {
      for (change <- Vector("extra-work", "different-authority")) {
        Given("the saved selected plan but a changed source application contract: " + change)
        val f = fixture()
        val original = Files.readString(f.source)
        val changed = if (change == "extra-work") original.replace("| old-completed", "| new-work | newly planned | planned |\n| old-completed")
          else original.replace("\"identity\":\"authority\"", "\"identity\":\"foreign-authority\"")
        Files.writeString(f.source, changed)
        withRuntime(f) { (core, _, _) =>
          When("the managed process attempts application")
          val result = execute(core, f, Process.Purpose.ApplySelectedDocuments)._2
          Then("no changed document is overwritten and no child is created")
          status(result) shouldBe "conflict"
          Files.readString(f.source) shouldBe changed
          Files.exists(f.root.resolve("docs/phase/phase-77.1.md")) shouldBe false
        }
      }
    }

    "refuse a missing child after the source's final installation instead of recreating deleted work" in {
      Given("an impossible crash position: final source installed, child absent")
      val f = fixture()
      val root = f.documents.documents.head
      Files.writeString(f.source, "# Phase 77\n\n" + root.sections)
      withRuntime(f) { (core, _, _) =>
        When("the saved plan is observed or applied")
        Vector(Process.Purpose.ObserveSelectedDocuments, Process.Purpose.ApplySelectedDocuments).foreach { purpose =>
          Then("contradictory final state is reported as a conflict, not normal recovery")
          status(execute(core, f, purpose)._2) shouldBe "conflict"
          Files.exists(f.root.resolve("docs/phase/phase-77.1.md")) shouldBe false
        }
      }
    }

    "enforce runtime grants and complete argument vectors before driver invocation" in {
      Given("a configured runtime program with no component-scope grant")
      val f = fixture()
      withRuntime(f, grant = false) { (core, driver, admission) =>
        When("the component submits its logical purpose")
        val denied = Process.executeC(core, Process.Purpose.ApplySelectedDocuments, f.input)
        Then("admission rejects it without a host process or document effect")
        denied.isSuccess shouldBe false
        driver.startedCount shouldBe 0
        Files.exists(f.root.resolve("docs/phase/.sm-workflow-document.lock")) shouldBe false
      }
      withRuntime(f) { (_, _, admission) =>
        Given("both words are individually familiar but their combined vector is not registered")
        When("the combined vector is submitted to runtime admission")
        val denied = admission.admitC(ProcessExecutionRequest(take(ProcessCapabilityId.parseC("sm-workflow-selected-documents")),
          Vector("observe", "apply")))
        Then("the complete-vector policy rejects it, with no shell interpretation or process fallback")
        denied.isSuccess shouldBe false
      }
    }

    "reject a crossed repository identity and symlink document through real program failures" in {
      for (crossed <- Vector(true, false)) {
        Given("a fixed runtime target but " + (if (crossed) "another repository in input data" else "a symlink child to another owned fixture file"))
        val f = fixture()
        val original = Files.readString(f.source)
        val foreign = f.root.resolve("independent.txt")
        Files.writeString(foreign, "independent\n")
        if (!crossed) Files.createSymbolicLink(f.root.resolve("docs/phase/phase-77.1.md"), foreign)
        val input = if (crossed) take(Program.requestC("other-repository", instance, "context", "1", f.snapshot)) else f.input
        withRuntime(f) { (core, driver, _) =>
          When("the canonical effect runs the actual bounded program")
          val result = take(Process.executeC(core, Process.Purpose.ApplySelectedDocuments, input))
          Then("the process exits with a structured failure and neither original nor foreign document changes")
          result.termination shouldBe ProcessExecutionTermination.Exited(2)
          Files.readString(f.source) shouldBe original
          Files.readString(foreign) shouldBe "independent\n"
          driver.startedCount shouldBe 1
          driver.completedCount shouldBe driver.startedCount
        }
      }
    }
  }
}
