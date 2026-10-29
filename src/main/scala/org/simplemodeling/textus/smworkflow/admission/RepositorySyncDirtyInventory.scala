package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json

/** Current typed Git status facts, without inferring purpose or acceptance.
  * A path can belong to both staged and unstaged inventories. This projection
  * neither stages work nor treats a whole-file selection as a purpose candidate.
  * @since   Oct. 28, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncDirtyInventory {
  import RepositorySyncManagedGitObservation.DirtyPath

  final case class Inventory(staged: Vector[String], unstaged: Vector[String],
    untracked: Vector[String], partial: Vector[String], renamed: Vector[(String, String)]) {
    def description: String = {
      def paths(values: Vector[String]): String = Json.fromValues(values.map(Json.fromString)).noSpaces
      val renames = Json.fromValues(renamed.map { case (from, to) =>
        Json.obj("from" -> Json.fromString(from), "to" -> Json.fromString(to)) }).noSpaces
      s"staged=${paths(staged)}; unstaged=${paths(unstaged)}; untracked=${paths(untracked)}; partial=${paths(partial)}; renamed=$renames"
    }
  }

  def fromStatus(entries: Vector[DirtyPath]): Inventory = {
    def tracked(entry: DirtyPath): Boolean = entry.index != '?' && entry.index != '!'
    def staged(entry: DirtyPath): Boolean = tracked(entry) && entry.index != ' '
    def unstaged(entry: DirtyPath): Boolean = tracked(entry) && entry.worktree != ' '
    def paths(predicate: DirtyPath => Boolean): Vector[String] = entries.filter(predicate).map(_.path).distinct.sorted
    Inventory(paths(staged), paths(unstaged), paths(_.index == '?'),
      paths(entry => staged(entry) && unstaged(entry)),
      entries.flatMap(entry => entry.renamedFrom.map(_ -> entry.path)).distinct.sorted)
  }
}
