package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.Path
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import SplitPhaseExplicitPartitionFixture.take

/** Normal packaged host plan for controlled Phase 6.9 qualification.
  * No product SBT binding can accidentally turn clean synchronization into tests.
  * @since   Oct. 28, 2026
  * @version Oct. 28, 2026
  */
private[admission] trait Phase69ConfiguredRepositoryFixture extends ThreeWorkflowManagedRepositoryFixture {
  override protected def standard_runtime = true
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val descriptor = take(GenericSubsystemDescriptor.loadComponentArchive(Path.of("src/main/car").toAbsolutePath))
    val template = io.circe.parser.parse(descriptor.config("textus.subsystem.process-execution.plan")).toOption.get
    val values = Map("repository.root" -> root.resolve("repository").toString, "git.branch" -> "main",
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
