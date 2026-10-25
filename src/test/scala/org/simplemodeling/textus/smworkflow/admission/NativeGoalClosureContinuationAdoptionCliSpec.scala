package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import io.circe.Json
import io.circe.parser.parse
import org.scalatest.DoNotDiscover

/** Two complete installed runtime generations share each fresh isolated host.
  * The inherited scenarios obtain a real old-runtime pathspec failure through
  * ordinary Start/Submit/Test/review/closure authority, then switch only the
  * installations root for describe and ordinary Advance. Semantic responses,
  * independent reviews and authority grants are specification-owned only.
  * No native receipt is seeded or changed, and no real Session is selected.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalClosureContinuationAdoptionCliSpec extends NativeGoalClosureRecoveryCliSpec {
  private def _required(name: String): Path = {
    val path = Path.of(sys.props.getOrElse(name, throw new IllegalArgumentException("Missing " + name)))
    require(path.isAbsolute, name + " must select an absolute complete installed runtime")
    path.toRealPath()
  }
  private def _read(path: Path): Json = parse(Files.readString(path)).fold(
    error => throw new IllegalArgumentException(error.message), identity)
  private def _generation(root: Path): String = {
    val selected = _read(root.resolve("selected"))
    selected.hcursor.get[String]("kind").toOption shouldBe Some("cncf.installed-runtime-selection.v1")
    val id = selected.hcursor.get[String]("installation_id").toOption.get
    java.util.UUID.fromString(id).toString shouldBe id
    val directory = root.resolve(id)
    val manifest = _read(directory.resolve("runtime-installation.json"))
    manifest.hcursor.get[String]("kind").toOption shouldBe Some("cncf.installed-runtime.v1")
    manifest.hcursor.get[String]("installation_id").toOption shouldBe Some(id)
    val modules = manifest.hcursor.get[Vector[Json]]("modules").toOption.get
    modules should not be empty
    modules.foreach { module =>
      val relative = Path.of(module.hcursor.get[String]("path").toOption.get)
      relative.isAbsolute shouldBe false
      val file = directory.resolve(relative).normalize
      file.startsWith(directory.resolve("runtime")) shouldBe true
      Files.isSymbolicLink(file) shouldBe false
      Files.isRegularFile(file) shouldBe true
      file.getFileName.toString should endWith(".jar")
    }
    id
  }
  override protected def closure_runtime_roots: Option[(Path, Path)] = {
    val executable = _required("smworkflow.spec.installedCommand")
    val legacy = _required("smworkflow.spec.legacyRuntimeRoot")
    val current = _required("smworkflow.spec.runtimeRoot")
    require(Files.isRegularFile(executable) && Files.isExecutable(executable), "Select the actual installed command")
    require(Files.isDirectory(legacy) && Files.isDirectory(current) && legacy != current,
      "Select two distinct complete registered installation roots, never source/classes substitutes")
    _generation(legacy) should not be _generation(current)
    Some((legacy, current))
  }
}
