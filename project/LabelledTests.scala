import java.net.URLClassLoader
import sbt._
import sbt.testing.TestSelector

/** SBT transport for canonical CNCF metadata. No persistent suite membership list. */
object LabelledTests {
  def closeLoader(loader: ClassLoader): Unit = loader match {
    case resource: AutoCloseable => resource.close()
    case resource: sbt.internal.inc.classpath.ClasspathFilter => resource.close()
    case _ => ()
  }
  final case class Request(purpose: String, patterns: Seq[String], exact: Seq[String], containing: Seq[String])
  final case class Selected(definition: TestDefinition, runtimeTests: Set[String]) {
    def requiresRuntime: Boolean = runtimeTests.nonEmpty
  }

  def request(arguments: Seq[String]): Request = {
    var rest = arguments
    val purpose = if (rest.headOption.contains("--purpose")) {
      require(rest.size >= 2, "--purpose needs a canonical CNCF purpose")
      val result = rest(1)
      require(Set("SMOKE", "FOCUSED", "ADMISSION", "FULL", "HEAVY")(result), "Unknown CNCF purpose: " + result)
      rest = rest.drop(2)
      result
    } else "EXPLICIT"
    val (patterns, options) = rest.span(_ != "--")
    require(patterns.nonEmpty || purpose != "EXPLICIT", "Select a suite pattern or --purpose; empty selection is not PASS")
    var filters = options.drop(1)
    var exact = Vector.empty[String]
    var containing = Vector.empty[String]
    while (filters.nonEmpty) {
      require(filters.size >= 2 && Set("-t", "-z")(filters.head),
        "Labelled execution supports ScalaTest -t and -z filters only")
      if (filters.head == "-t") exact :+= filters(1) else containing :+= filters(1)
      filters = filters.drop(2)
    }
    Request(purpose, patterns, exact, containing)
  }

  def candidates(request: Request, definitions: Seq[TestDefinition]): Seq[TestDefinition] =
    if (request.patterns.isEmpty) definitions
    else definitions.filter(definition => request.patterns.exists { pattern =>
      val regex = pattern.split("\\*", -1).map(java.util.regex.Pattern.quote).mkString(".*")
      definition.name.matches(regex)
    })

  private def _quoted(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
  def arguments(selected: Seq[Selected]): Seq[String] = selected.map { value =>
    val definition = value.definition
    val names = definition.selectors.collect { case selector: TestSelector => selector.testName }.distinct
    " " + definition.name + " -- " + names.map(name => "-t " + _quoted(name)).mkString(" ")
  }

  def select(classpath: Seq[File], classes: File, revision: String, purpose: String,
    definitions: Seq[TestDefinition], exact: Seq[String] = Seq.empty, containing: Seq[String] = Seq.empty): Seq[Selected] = {
    val loader = new URLClassLoader(classpath.map(_.toURI.toURL).toArray, ClassLoader.getPlatformClassLoader)
    try {
      val bridge = loader.loadClass("org.simplemodeling.textus.smworkflow.testing.LabelledTestSelection$")
      val instance = bridge.getField("MODULE$").get(null)
      val rows = try bridge.getMethod("select", classOf[String], classOf[String], classOf[String],
        classOf[Array[String]], classOf[Array[String]], classOf[Array[String]])
        .invoke(instance, classes.getCanonicalPath, revision, purpose, definitions.map(_.name).toArray,
          exact.toArray, containing.toArray)
        .asInstanceOf[Array[Array[String]]].toSeq
      catch { case error: java.lang.reflect.InvocationTargetException => throw error.getCause }
      val selected = rows.groupBy(_(0))
      definitions.flatMap { definition => selected.get(definition.name).map { operations =>
        Selected(new TestDefinition(definition.name, definition.fingerprint, definition.explicitlySpecified,
          operations.map(row => new TestSelector(row(1))).toArray),
          operations.filter(_(2) == "true").map(_(1)).toSet)
      }}
    } finally loader.close()
  }
}
