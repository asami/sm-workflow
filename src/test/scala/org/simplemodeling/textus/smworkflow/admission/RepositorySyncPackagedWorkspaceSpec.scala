package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, LinkOption, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.bootstrap.BootstrapConfig
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, CncfConfigurationParameterDefinition}
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import org.goldenport.configuration.{ConfigurationParameter, ConfigurationValue}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import SplitPhaseExplicitPartitionFixture.take

/** The real packaged defaults enter public CNCF initialization.
  * No Session, Git operation or SBT process is started by this spec.
  * Installed consumer continuation remains a separate acceptance obligation.
  * @since   Nov. 12, 2026
  *  version Nov. 12, 2026
  * @version Nov. 16, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class RepositorySyncPackagedWorkspaceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _key = "textus.subsystem.process-execution.plan"
  private val _owner = SmWorkflowComponent.componentId.name
  private val _source = "sm-workflow-repository"
  private val _parent = "sm-workflow-session-candidates"
  private val _alias = "sm-workflow-session-candidate"

  private def _rows(plan: Json, key: String): Vector[Json] =
    plan.hcursor.get[Vector[Json]](key).toOption.get

  private def _text(row: Json, key: String): String =
    row.hcursor.get[String](key).toOption.get

  private def _packaged_plan: Json = {
    val path = Path.of("src/main/car/assembly-descriptor.yaml").toAbsolutePath.normalize()
    val source = take(GenericSubsystemDescriptor.loadAssemblyDescriptorC(path))
    val descriptor = take(GenericSubsystemDescriptor.applyAssemblyOverrideC(
      GenericSubsystemDescriptor(path, "sm-workflow"), source))
    parse(descriptor.config(_key)).toOption.get
  }

  /** Only documented environment placeholders are substituted. Managed roots
    * stay absent until ordinary public runtime initialization.
    */
  private def _test_plan(root: Path): Json = {
    val repository = Files.createDirectories(root.resolve("repository"))
    val replacements = Vector(
      "${runtime.root}" -> repository.toString,
      "${runtime.parent}" -> root.toString,
      "${repository.root}" -> repository.toString,
      "${executable.sbt}" -> TypedSbtHostPlanFixture.normalExecutable.toString,
      "${environment.path}" -> sys.env.getOrElse("PATH", "/usr/bin:/bin"),
      "${environment.java-home}" -> System.getProperty("java.home"),
      "${environment.ssh-auth-sock}" -> root.resolve("unused-ssh-agent.sock").toString,
      "${git.branch}" -> "main",
      "${git.remote}" -> "origin",
      "${git.remote-branch}" -> "main",
      "${git.endpoint}" -> root.resolve("unused-upstream.git").toString,
      "${git.user-name}" -> "Packaged Workspace Specification",
      "${git.user-email}" -> "packaged-workspace@example.invalid")
    def _replace_(value: Json): Json = value.fold(Json.Null, Json.fromBoolean, Json.fromJsonNumber,
      text => Json.fromString(replacements.foldLeft(text) { case (prior, (key, replacement)) =>
        prior.replace(key, replacement)
      }), values => Json.fromValues(values.map(_replace_)),
      fields => Json.fromJsonObject(fields.mapValues(_replace_)))
    _replace_(_packaged_plan)
  }

  /** The public registered codec round-trips pure declaration data. */
  private def _declaration_c(plan: Json): Consequence[Json] =
    CncfConfigurationParameterCatalog.closed.definitions.find(_.canonicalSpelling == _key).get match {
      case typed: CncfConfigurationParameterDefinition.Typed[?] => _round_trip_c(typed.parameter, plan)
    }

  private def _round_trip_c[A](parameter: ConfigurationParameter[A], plan: Json): Consequence[Json] = for {
    decoded <- parameter.codec.decode(ConfigurationValue.StringValue(plan.noSpaces))
    encoded <- parameter.codec.encode(decoded)
    result <- encoded match {
      case ConfigurationValue.StringValue(value) =>
        parse(value).fold(error => Consequence.configurationInvalid(error.message), Consequence.success)
      case _ => Consequence.configurationInvalid("Process plan codec must encode its JSON string")
    }
  } yield result

  private def _managed_roots_absent(root: Path): Unit =
    Files.notExists(root.resolve(".sm-workflow"), LinkOption.NOFOLLOW_LINKS) shouldBe true

  private def _initialize_c(root: Path, plan: Json): Consequence[Json] = {
    val configuration = Files.createDirectories(root.resolve(".textus"))
    Files.createDirectories(root.resolve(".cncf"))
    Files.writeString(configuration.resolve("config.json"), Json.obj(_key -> plan.noSpaces.asJson).noSpaces)
    val descriptor = root.resolve("test.yaml")
    Files.writeString(descriptor,
      """kind: test-descriptor
        |execution:
        |  profile: controlled
        |  key: packaged-workspace
        |  time:
        |    mode: manual
        |    start-at: '2026-11-16T00:00:00Z'
        |  random:
        |    mode: seeded
        |    seed: packaged-workspace
        |  ids:
        |    mode: deterministic
        |  scheduler:
        |    mode: manual
        |  ordering:
        |    mode: deterministic
        |""".stripMargin)
    val home = Files.createDirectories(root.resolve("home"))
    val arguments = Array(s"--textus.test.descriptor=$descriptor", s"--textus.test.home.path=$home",
      "--textus.test.home.inherit.runtime=false")
    val runtime = new CncfRuntime()
    try runtime.initializeHandle(BootstrapConfig(cwd = root, args = arguments)).flatMap { handle =>
      try _declaration_c(plan)
      finally handle.close()
    } finally runtime.closeEmbedding()
  }

  "Packaged RepositorySync workspace admission" should {
    "initialize absent sibling roots and reuse them with the unique owner policy and all three Manage grants" in {
      Given("the real assembly YAML with only isolated test environment placeholder values")
      val root = Files.createTempDirectory("sm-workflow-packaged-workspace-").toRealPath()
      val plan = _test_plan(root)
      _managed_roots_absent(root)
      When("the registered CNCF process-plan codec decodes and encodes its V8 declaration")
      val declaration = take(_declaration_c(plan))
      Then("the roundtrip retains the declaration without creating managed roots")
      _rows(declaration, "workspaces") shouldBe _rows(plan, "workspaces")
      _rows(declaration, "workspaceInitializations") shouldBe _rows(plan, "workspaceInitializations")
      _managed_roots_absent(root)
      When("public runtime initialization selects the same trusted expanded plan")
      val admitted = take(_initialize_c(root, plan))
      Then("one owner policy binds distinct source, parent and alias workspaces outside the source checkout")
      _text(admitted, "schema") shouldBe "cncf.runtime-process-execution.v8"
      _rows(admitted, "validationChecks") shouldBe empty
      _rows(admitted, "sessionCandidateWorkspaces") shouldBe Vector(Json.obj(
        "componentId" -> _owner.asJson, "parent" -> _parent.asJson, "alias" -> _alias.asJson))
      val roots = Vector(_source, _parent, _alias).map { identity =>
        val matches = _rows(admitted, "workspaces").filter(row => _text(row, "identity") == identity)
        matches should have size 1
        Path.of(_text(matches.head, "root")).normalize()
      }
      roots shouldBe Vector(root.resolve("repository"), root.resolve(".sm-workflow/session-candidates"),
        root.resolve(".sm-workflow/candidate-alias"))
      roots.foreach(path => Files.isDirectory(path) shouldBe true)
      for (left <- roots; right <- roots if left != right) left.startsWith(right) shouldBe false
      Vector(_source, _parent, _alias).foreach { identity =>
        _rows(admitted, "workspaceGrants").filter(row =>
          _text(row, "componentId") == _owner && _text(row, "workspaceId") == identity &&
            _text(row, "access") == "manage") should have size 1
      }
      val sources = _rows(admitted, "workspaces").map(row => _text(row, "identity"))
        .filterNot(Set(_parent, _alias))
      sources should have size 17
      _rows(admitted, "workspaceInitializations") shouldBe Vector(Json.obj(
        "componentId" -> _owner.asJson, "anchor" -> root.toString.asJson,
        "sources" -> sources.asJson, "parent" -> _parent.asJson, "alias" -> _alias.asJson,
        "parentPath" -> ".sm-workflow/session-candidates".asJson,
        "aliasPath" -> ".sm-workflow/candidate-alias".asJson))
      _rows(admitted, "workspaces").filter(row => sources.contains(_text(row, "identity")))
        .foreach(row => Path.of(_text(row, "root")) shouldBe root.resolve("repository"))
      Given("an existing marker in the initialized parent")
      val marker = roots(1).resolve("retained-marker")
      Files.writeString(marker, "retained parent content")
      When("a fresh owned runtime initializes the same selected plan again")
      val reused = take(_initialize_c(root, plan))
      Then("the declaration and existing roots are reused without replacing parent content")
      reused shouldBe admitted
      roots.foreach(path => Files.isDirectory(path) shouldBe true)
      Files.readString(marker) shouldBe "retained parent content"
    }

    "admit alias typed Git and one SBT declaration per ordinary operation with matching owner capabilities" in {
      Given("the actual packaged source declarations and their candidate alias declarations")
      val root = Files.createTempDirectory("sm-workflow-packaged-programs-").toRealPath()
      val plan = _test_plan(root)
      _managed_roots_absent(root)
      When("public runtime initialization admits the actual packaged Git and typed SBT declarations")
      val admitted = take(_initialize_c(root, plan))
      Then("the alias keeps the source destination, executable, environment and finite limits with its own grants")
      _rows(admitted, "programs") shouldBe empty
      _rows(admitted, "gitPrograms") shouldBe empty
      val repositories = _rows(admitted, "gitRepositories")
      val source = repositories.filter(row => _text(row, "target") == _source)
      val alias = repositories.filter(row => _text(row, "target") == _alias)
      source should have size 1
      alias should have size 1
      alias.head shouldBe source.head.mapObject(_.add("target", _alias.asJson)
        .add("identity", "sm-workflow-purpose-git".asJson).add("capability", "sm-workflow-purpose-git".asJson))
      val programs = _rows(admitted, "sbtPrograms")
      val ordinary = programs.filter(row => _text(row, "target") != _alias)
      val candidates = programs.filter(row => _text(row, "target") == _alias)
      ordinary should have size 49
      candidates.map(row => _text(row, "operation")).sorted shouldBe
        Vector("compile", "fix-check", "publish-local", "test")
      candidates.foreach { candidate =>
        val operation = _text(candidate, "operation")
        val original = ordinary.find(row => _text(row, "operation") == operation).get
        val identity = "sm-workflow-purpose-" + operation
        candidate shouldBe original.mapObject(_.add("target", _alias.asJson)
          .add("identity", identity.asJson).add("capability", identity.asJson))
      }
      val grants = _rows(admitted, "grants").filter(row => _text(row, "componentId") == _owner)
      grants should have size 1
      val capabilities = grants.head.hcursor.get[Vector[String]]("capabilities").toOption.get
      capabilities.distinct shouldBe capabilities
      (alias ++ candidates).foreach(row => capabilities should contain(_text(row, "capability")))
      ordinary.foreach(row => capabilities should contain(_text(row, "capability")))
      capabilities should contain(_text(source.head, "capability"))
    }

    for (scenario <- Vector("missing parent Manage", "crossed policy owner")) {
      s"refuse $scenario through public runtime initialization before creating managed roots" in {
        Given(s"the real packaged plan with $scenario and absent managed roots")
        val root = Files.createTempDirectory("sm-workflow-packaged-refusal-").toRealPath()
        val plan = _test_plan(root)
        val altered = if (scenario == "missing parent Manage")
          plan.mapObject(_.add("workspaceGrants", Json.fromValues(
            _rows(plan, "workspaceGrants").filterNot(row => _text(row, "workspaceId") == _parent))))
        else plan.mapObject(_.add("sessionCandidateWorkspaces", Json.fromValues(
          _rows(plan, "sessionCandidateWorkspaces").map(_.mapObject(
            _.add("componentId", "org.simplemodeling.textus.OtherComponent".asJson))))))
        _managed_roots_absent(root)
        When("public runtime initialization selects the altered trusted plan")
        val result = _initialize_c(root, altered)
        Then("initialization fails and neither managed root nor its shared prefix is created")
        result.isFaillure shouldBe true
        _managed_roots_absent(root)
        Files.notExists(root.resolve(".sm-workflow/session-candidates"), LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.notExists(root.resolve(".sm-workflow/candidate-alias"), LinkOption.NOFOLLOW_LINKS) shouldBe true
        }
      }
  }
}
