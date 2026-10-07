package org.simplemodeling.textus.smworkflow.admission

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import org.simplemodeling.textus.smworkflow.runtime.SelectedPhaseDocumentProgram

/** Fault injection in a spec-owned configured managed program, not an
  * Operation or process-driver replacement. Execute the real production main,
  * then withhold its actual stdout before the managed result can be returned.
  * @since   Oct.  7, 2026
  * @version Oct.  8, 2026
  */
object ThreeWorkflowDocumentInterruption {
  def main(args: Array[String]): Unit = {
    require(args.length == 5, "Expected fixed root, boundary, interruption flag, repository and action")
    val root = Path.of(args(0)).toAbsolutePath.normalize
    val boundary = Path.of(args(1)).toAbsolutePath.normalize
    require(root.startsWith(boundary) && root != boundary, "Unique owned document root required")
    require(Set("true", "false")(args(2)) && Set("observe", "apply")(args(4)), "Closed fault scenario required")
    val held = new ByteArrayOutputStream()
    val stdout = System.out
    val capture = new PrintStream(held, true, UTF_8)
    try {
      System.setOut(capture)
      // Real production validation, locking, writes and readback. Its failure
      // still exits the actual process nonzero; no successful receipt is made.
      SelectedPhaseDocumentProgram.main(Array(root.toString, args(3), args(4)))
    } finally {
      capture.flush()
      System.setOut(stdout)
    }
    require(held.size() <= 131072, "Actual document-program result exceeds the declared limit")
    val result = io.circe.parser.parse(held.toString(UTF_8)).fold(error => throw error, identity)
    Files.writeString(root.resolve("document-" + args(4) + "-invocations.txt"),
      ProcessHandle.current().pid().toString + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    if (args(2) == "true" && args(4) == "apply") {
      require(result.hcursor.get[String]("status").contains("applied"), "Only an actual applied postimage may be interrupted")
      Files.writeString(root.resolve("document-applied-before-result.txt"), ProcessHandle.current().pid().toString,
        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
      Thread.sleep(30000)
      throw new IllegalStateException("Owned caller must interrupt before this result returns")
    }
    stdout.print(held.toString(UTF_8))
    stdout.flush()
  }
}
