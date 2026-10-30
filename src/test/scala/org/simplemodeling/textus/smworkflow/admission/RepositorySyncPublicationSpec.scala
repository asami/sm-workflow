package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.Path
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import RepositorySyncManagedGitObservation.DirtyPath
import SplitPhaseExplicitPartitionFixture.take

/** Publication is not a commit. Only sync may adopt its ordinary catalog
  * outputs before pushing the final clean tree.
  * @since   Oct. 30, 2026
  * @version Oct. 30, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncPublicationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val catalog = "src/main/catalog/car/org/example/component.yaml"

  "RepositorySync final publication" should {
    "admit modified and new publication catalogs while preserving every other kind of dirty work" in {
      take(RepositorySyncPublication.outputPathsC(Vector(DirtyPath(' ', 'M', catalog, None)))) shouldBe Vector(catalog)
      take(RepositorySyncPublication.outputPathsC(Vector(DirtyPath('?', '?', catalog, None)))) shouldBe Vector(catalog)
      for (entry <- Vector(DirtyPath('M', ' ', catalog, None), DirtyPath(' ', 'M', "src/main/scala/Other.scala", None),
          DirtyPath(' ', 'M', "docs/ongoing.md", None), DirtyPath(' ', 'D', catalog, None),
          DirtyPath(' ', 'M', catalog, Some("old.yaml")), DirtyPath(' ', 'M', "src/main/catalog/car/../Other.yaml", None))) {
        withClue(entry) { RepositorySyncPublication.outputPathsC(Vector(entry)).isSuccess shouldBe false }
      }
      RepositorySyncPublication.outputPathsC(Vector(DirtyPath(' ', 'M', catalog, None),
        DirtyPath(' ', 'M', "README.md", None))).isSuccess shouldBe false
    }

    "restore old publication targets without silently enabling publication during sync" in {
      val target = Json.obj("identity" -> Json.fromString("producer"), "component" -> Json.fromString("component"),
        "workspace" -> Json.fromString("sm-workflow-repository"), "beforeChecks" -> Json.False)
      target.as[LocalPublication.Target].toOption.get.beforeSync shouldBe false
      target.mapObject(_.add("beforeSync", Json.True)).as[LocalPublication.Target].toOption.get.beforeSync shouldBe true
      target.mapObject(_.add("beforeSync", Json.Null)).as[LocalPublication.Target].isLeft shouldBe true
    }

    "ship an ordinary default publication target using CNCF's existing PATH-based sbt resolution" in {
      val descriptor = take(GenericSubsystemDescriptor.loadComponentArchive(Path.of("src/main/car").toAbsolutePath))
      val publication = io.circe.parser.parse(descriptor.config("sm-workflow.local-publication")).toOption.get
      val plan = io.circe.parser.parse(descriptor.config("textus.subsystem.process-execution.plan")).toOption.get
      val target = publication.hcursor.get[Vector[Json]]("targets").toOption.get.head
      target.hcursor.get[String]("identity").toOption shouldBe Some("repository")
      target.hcursor.get[String]("workspace").toOption shouldBe Some("sm-workflow-repository")
      val programs = plan.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get
        .filter(_.hcursor.get[String]("operation").toOption.contains("publish-local"))
      programs.size shouldBe 1
      programs.head.hcursor.get[String]("sbtExecutable").toOption shouldBe Some("${executable.sbt}")
      programs.head.hcursor.get[String]("target").toOption shouldBe Some("sm-workflow-repository")
    }

    "declare publication and metadata commit before native push and final convergence" in {
      import domain.statemachine.RepositorySyncBoundaryLifecycle.permits
      permits("IntegrationValidated", "native-publication-prepared", "PublicationPreparing") shouldBe true
      permits("PublicationPreparing", "native-publication-observed", "PublicationObserved") shouldBe true
      permits("PublicationObserved", "native-publication-stage-prepared", "PublicationStaging") shouldBe true
      permits("PublicationStaging", "native-publication-staged", "PublicationStaged") shouldBe true
      permits("PublicationStaged", "native-publication-commit-prepared", "PublicationCommitPreparing") shouldBe true
      permits("PublicationCommitPreparing", "native-publication-commit-observed", "PublicationCommitObserved") shouldBe true
      permits("PublicationCommitObserved", "native-push-prepared", "NativePushPreparing") shouldBe true
      permits("PublicationPreparing", "native-convergence-observed", "Converged") shouldBe false
      permits("PublicationStaging", "native-push-prepared", "NativePushPreparing") shouldBe false
    }
  }
}
