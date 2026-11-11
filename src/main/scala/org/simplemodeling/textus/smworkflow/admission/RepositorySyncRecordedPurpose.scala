package org.simplemodeling.textus.smworkflow.admission

import RepositorySyncPurpose as P

/** Reuse attributable native Task intent only for its exact captured candidate.
  * A broad owned directory, old successful check or matching filename alone
  * cannot classify new work. This supplies grouping, never check/review acceptance.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  */
private[smworkflow] object RepositorySyncRecordedPurpose {
  private final case class Task(identity: String, objective: String, dependencies: Vector[String], paths: Vector[String])

  def resolve(request: P.Request): Option[P.Result] = {
    val tasks = request.records.zipWithIndex.flatMap { (record, ordinal) =>
      if (record.source.kind != "Task" || record.status != "observed" ||
          !record.source.repositoryIdentity.forall(_ == request.repository)) None
      else GoalTaskNativeRun.stateCodec.decode(record.payload).toOption.flatMap { payload =>
        val state = payload.state
        state.candidate.filter(candidate => payload.root == request.root && payload.branch == request.branch &&
          state.input.repositoryIdentity == request.repository && state.input.task.nonEmpty &&
          state.semanticReference.nonEmpty && state.commit.isEmpty &&
          Set("Validate", "Assess", "Review", "CommitReady")(state.stage) &&
          candidate.head == request.parent && candidate.tree == request.indexTree && candidate.paths.nonEmpty &&
          candidate.paths.forall(path => StandaloneFixExecution.owned(path, state.input.ownedPaths)))
          .map(candidate => (ordinal, Task(state.input.requestIdentity, state.input.objective,
            state.input.task.get.dependencies, candidate.paths)))
      }
    }
    // Preserve source order when no dependency constrains it. All dependency
    // identities must name another actual selected Task; no caller scheduling.
    val unique = tasks.map(_._2.identity).distinct.size == tasks.size
    val files = request.files
    val exact = files.nonEmpty && files.forall(file => file.unexamined.isEmpty && file.worktree == file.indexed &&
      file.worktreeCode == " " && file.indexCode != " " && file.indexCode != "?" &&
      tasks.count(_._2.paths.contains(file.path)) == 1) &&
      tasks.forall { (_, task) => task.paths.forall(path => files.exists(_.path == path)) &&
        task.dependencies.forall(id => id != task.identity && tasks.exists(_._2.identity == id)) }
    if (!unique || !exact || tasks.isEmpty || tasks.size > 16) None
    else {
      def ordered(remaining: Vector[(Int, Task)], selected: Vector[(Int, Task)]): Option[Vector[(Int, Task)]] =
        if (remaining.isEmpty) Some(selected)
        else remaining.find { (_, task) => task.dependencies.forall(id => selected.exists(_._2.identity == id)) }
          .flatMap(next => ordered(remaining.filterNot(_._1 == next._1), selected :+ next))
      ordered(tasks, Vector.empty).flatMap { selected =>
        def identity(id: String): String = "recorded-task-" + (tasks.find(_._2.identity == id).get._1 + 1)
        val groups = selected.map { (_, task) => P.Group(identity(task.identity), task.objective,
          task.objective.trim, task.dependencies.map(identity),
          task.paths.map(path => P.Content(path, files.find(_.path == path).get.indexed)), Vector.empty,
          "Reuse the native Task intent for its exact captured HEAD, index tree and selected paths") }
        val result = P.Result("resolved", "Recorded native Task purposes cover the exact current staged candidate", groups, Vector.empty)
        Option.when(P.admitC(request, result).isSuccess)(result)
      }
    }
  }
}
