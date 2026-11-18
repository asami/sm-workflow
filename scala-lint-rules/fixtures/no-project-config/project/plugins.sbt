addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.12.1")
libraryDependencies += "com.typesafe" % "config" % "1.4.3"
// Reuse the resolver source and versioned resource owner; no copied resolver/config.
Compile / unmanagedSources += baseDirectory.value.getParentFile.getParentFile.getParentFile.getParentFile / "project" / "ScalaLintProfiles.scala"
Compile / unmanagedResourceDirectories += baseDirectory.value.getParentFile.getParentFile.getParentFile / "src" / "main" / "resources"
