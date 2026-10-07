import scala.sys.process.Process

TaskKey[Unit]("checkOperations") := {
  val keys = Seq("classpath", "root", "launcher", "owner")
  val selection = new java.util.Properties()
  val input = new java.io.FileInputStream(sys.props.getOrElse("smworkflow.spec.selection",
    sys.error("Missing scripted-owned selection file")))
  try selection.load(input) finally input.close()
  val selected = keys.map(key => key -> Option(selection.getProperty(key)).getOrElse(
    sys.error("Missing scripted-owned selection: " + key))).toMap
  val javaExecutable = file(sys.props("java.home")) / "bin/java"
  val argv = Seq(javaExecutable.getAbsolutePath,
    "-Dsmworkflow.spec.launcher=" + selected("launcher"),
    "-Dsmworkflow.spec.owner=" + selected("owner"),
    "-cp", selected("classpath"), "org.scalatest.tools.Runner", "-s",
    "org.simplemodeling.textus.smworkflow.admission.ThreeWorkflowSbtOperationSpec", "-o")
  val exit = Process(argv, file(selected("root"))).!
  if (exit != 0) sys.error("Direct Operation SBT specifications failed: " + exit)
}
