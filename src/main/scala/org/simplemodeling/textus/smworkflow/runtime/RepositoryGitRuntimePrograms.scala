package org.simplemodeling.textus.smworkflow.runtime

import java.nio.file.Path

/** Business target retained by existing Workflow declarations and persistence.
  * Execution configuration belongs to the host's CNCF typed Git plan.
  * @since   Oct.  6, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositoryGitRuntimePrograms {
  final case class Target(root: Path, localBranch: String, remote: String,
    remoteBranch: String, endpoint: String)
}
