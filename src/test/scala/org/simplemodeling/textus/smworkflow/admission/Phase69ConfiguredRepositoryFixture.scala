package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.Path
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import SplitPhaseExplicitPartitionFixture.take

/** Normal packaged host plan for controlled Phase 6.9 qualification.
  * No product SBT binding can accidentally turn clean synchronization into tests.
  * @since   Oct. 28, 2026
  *  version Oct. 28, 2026
  * @version Nov. 16, 2026
  */
private[admission] trait Phase69ConfiguredRepositoryFixture extends ThreeWorkflowManagedRepositoryFixture {
  override protected def standard_runtime = true
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val descriptor = take(GenericSubsystemDescriptor.loadComponentArchive(Path.of("src/main/car").toAbsolutePath))
    val template = io.circe.parser.parse(descriptor.config("textus.subsystem.process-execution.plan")).toOption.get
    val values = Map("runtime.root" -> root.resolve("repository").toString, "runtime.parent" -> root.toString,
      "repository.root" -> root.resolve("repository").toString, "git.branch" -> "main",
      "git.remote" -> "origin", "git.remote-branch" -> "main", "git.endpoint" -> root.resolve("upstream.git").toString,
      "git.user-name" -> "Fixture", "git.user-email" -> "fixture@example.invalid",
      "environment.path" -> "/usr/bin:/bin", "environment.java-home" -> "", "environment.ssh-auth-sock" -> "")
    def expand(value: Json): Json = value.mapString(text => values.foldLeft(text) { case (prior, (key, value)) =>
      prior.replace("${" + key + "}", value) }).mapArray(_.map(expand)).mapObject(_.mapValues(expand))
    // With no SBT binding an accidental product check cannot pass. The product
    // Git boundary resolves its installed default executable once per target.
    val removedcapabilities = template.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get
      .map(_.hcursor.get[Json]("capability").toOption.get).toSet
    expand(template).mapObject(fields => fields.add("sbtPrograms", Json.arr()).add("grants",
      fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(_.filterNot(removedcapabilities))))))))
  }

}

private[admission] object Phase69ConfiguredRepositoryFixture {
  /** Pure fixture data for explicit legacy slots without managed candidate authority. */
  def withoutManagedCandidates(plan: Json): Json = {
    val managedroots = plan.hcursor.get[Vector[Json]]("sessionCandidateWorkspaces").toOption.get
      .flatMap(row => Vector("parent", "alias").map(key => row.hcursor.get[String](key).toOption.get)).toSet
    val programkeys = Vector("programs", "gitPrograms", "gitRepositories", "sbtPrograms")
    val removedcapabilities = programkeys.flatMap(key => plan.hcursor.get[Vector[Json]](key).toOption.get
      .filter(row => row.hcursor.get[String]("target").toOption.exists(managedroots))
      .flatMap(_.hcursor.get[Json]("capability").toOption)).toSet
    plan.mapObject { fields =>
      val cleaned = programkeys.foldLeft(fields.remove("workspaceInitializations")) { (prior, key) =>
        prior.add(key, prior(key).get.mapArray(_.filterNot(row =>
          row.hcursor.get[String]("target").toOption.exists(managedroots))))
      }
      cleaned.add("schema", Json.fromString("cncf.runtime-process-execution.v7"))
        .add("sessionCandidateWorkspaces", Json.arr())
        .add("workspaces", cleaned("workspaces").get.mapArray(_.filterNot(row =>
          managedroots(row.hcursor.get[String]("identity").toOption.get))))
        .add("workspaceGrants", cleaned("workspaceGrants").get.mapArray(_.filterNot(row =>
          managedroots(row.hcursor.get[String]("workspaceId").toOption.get))))
        .add("grants", cleaned("grants").get.mapArray(_.map(_.mapObject(grant =>
          grant.add("capabilities", grant("capabilities").get.mapArray(_.filterNot(removedcapabilities)))))))
    }
  }

  /** Pure fixture data: initialization covers every final noninitialized workspace. */
  def withInitializationSources(plan: Json): Json = {
    val initializations = plan.hcursor.get[Vector[Json]]("workspaceInitializations").toOption.getOrElse(Vector.empty)
    val initialized = initializations.flatMap(row => Vector("parent", "alias").map(key =>
      row.hcursor.get[String](key).toOption.get)).toSet
    val sources = plan.hcursor.get[Vector[Json]]("workspaces").toOption.get
      .map(_.hcursor.get[String]("identity").toOption.get).filterNot(initialized)
    plan.mapObject(_.add("workspaceInitializations", Json.fromValues(initializations.map(row =>
      row.mapObject(_.add("sources", Json.fromValues(sources.map(Json.fromString))))))))
  }
}
