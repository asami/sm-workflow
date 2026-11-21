resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven"
resolvers += Resolver.defaultLocal

val sbtCozyVersion = sys.props.getOrElse("sbt.cozy.version", sys.env.getOrElse("SBT_COZY_VERSION", "0.1.18-SNAPSHOT"))
addSbtPlugin("org.goldenport" % "sbt-cozy" % sbtCozyVersion)

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.12.1")

// The same profile resolver is compiled into the independent rules artifact.
libraryDependencies += "com.typesafe" % "config" % "1.4.3"
// The pinned engine's ScalaMeta parser supplies declaration attribution only.
libraryDependencies += "org.scalameta" %% "scalameta" % "4.9.3"
Compile / unmanagedResourceDirectories += baseDirectory.value.getParentFile / "scala-lint-rules" / "src" / "main" / "resources"
