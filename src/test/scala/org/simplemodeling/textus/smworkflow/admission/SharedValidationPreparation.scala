package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}

/** Test arrangement only. Normal publication owns image preparation; every
  * scenario retains its own writable host, selection, repository and outputs.
  * This object never starts a CLI/SBT process or manufactures a runtime receipt.
  */
private[admission] object SharedValidationPreparation {
  final case class Baseline(installedImage: Path, inputs: Vector[Path])
  final case class Scenario(baseline: Baseline, root: Path, repository: Path,
    configuration: Path, stores: Path, selection: Path, publication: Path)

  /** The suite explicitly owns this single preparation lifetime. No global cache. */
  final class Run private[SharedValidationPreparation](val baseline: Baseline, parent: Path) {
    def scenario(name: String): Scenario = {
      require(name.matches("[A-Za-z0-9][A-Za-z0-9-]{0,63}"), "Use a bounded scenario name")
      val root = Files.createTempDirectory(parent, name + "-")
      def directory(name: String): Path = Files.createDirectory(root.resolve(name))
      Scenario(baseline, root, directory("repository"), directory("configuration"),
        directory("stores"), directory("selection"), directory("publication"))
    }
  }

  def prepare(parent: Path)(normalPreparation: => Baseline): Run = {
    require(parent != null && parent.isAbsolute && parent.normalize == parent && Files.isDirectory(parent),
      "Select an existing suite-owned preparation directory")
    val baseline = normalPreparation
    require(baseline != null && baseline.installedImage != null && baseline.installedImage.isAbsolute &&
      baseline.installedImage.normalize == baseline.installedImage && Files.isDirectory(baseline.installedImage) &&
      baseline.inputs != null && baseline.inputs.distinct.size == baseline.inputs.size && baseline.inputs.forall(p =>
        p != null && p.isAbsolute && p.normalize == p && Files.isRegularFile(p)),
      "Normal preparation must return an actual installed image and immutable input files")
    new Run(baseline, parent)
  }
}
