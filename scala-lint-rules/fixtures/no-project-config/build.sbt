import org.simplemodeling.scalalint.ScalaLintProfiles

scalaVersion := "3.3.8"
semanticdbEnabled := true
scalafixDependencies += "org.simplemodeling" %% "scala-lint-rules" % "0.1.0-SNAPSHOT"
scalafixConfig := {
  val effective = ScalaLintProfiles.resolve(ScalaLintProfiles.selection(Some("goldenport"), Some("none")),
    ScalaLintProfiles.project(baseDirectory.value / ".scalafix.conf"), ScalaLintProfiles.getClass.getClassLoader)
  val destination = target.value / "scala-lint" / "effective.conf"
  IO.write(destination, effective.rendered)
  Some(destination)
}
Compile / scalafix / unmanagedSources := (Compile / unmanagedSources).value
Compile / scalafix / managedSources := Nil
Test / scalafix / unmanagedSources := (Test / unmanagedSources).value
Test / scalafix / managedSources := Nil
addCommandAlias("fixCheck", ";Compile / scalafix --check;Test / scalafix --check")
