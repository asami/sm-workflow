import java.net.URLClassLoader
import sbt._
import sbt.testing.{AnnotatedFingerprint, Fingerprint, Framework, SubclassFingerprint, TestSelector}

/** SBT transport for canonical CNCF metadata. No persistent suite membership list.
 * @since   Oct. 25, 2026
 * @version Nov.  6, 2026
 */
object LabelledTests {
  def writeInventory(classpath: Seq[File], classes: File, revision: String, destination: File): Unit = {
    val loader = new URLClassLoader(classpath.map(_.toURI.toURL).toArray, ClassLoader.getPlatformClassLoader)
    try {
      val bridge = loader.loadClass("org.simplemodeling.textus.smworkflow.testing.LabelledTestSelection$")
      val instance = bridge.getField("MODULE$").get(null)
      val json = try bridge.getMethod("inventory", classOf[String], classOf[String])
        .invoke(instance, classes.getCanonicalPath, revision).asInstanceOf[String]
      catch { case error: java.lang.reflect.InvocationTargetException => throw error.getCause }
      IO.write(destination, json, java.nio.charset.StandardCharsets.UTF_8)
    } finally loader.close()
  }
  def closeLoader(loader: ClassLoader): Unit = loader match {
    case resource: AutoCloseable => resource.close()
    case resource: sbt.internal.inc.classpath.ClasspathFilter => resource.close()
    case _ => ()
  }
  final case class Request(purpose: String, patterns: Seq[String], exact: Seq[String], containing: Seq[String],
    properties: Seq[String] = Seq.empty)
  final case class Selected(definition: TestDefinition, runtimeTests: Set[String]) {
    def requiresRuntime: Boolean = runtimeTests.nonEmpty
  }

  def request(arguments: Seq[String]): Request = {
    require(arguments != null && arguments.size <= 128 && arguments.forall(value => value != null &&
      value.length <= 1024 && !value.exists(_.isControl)), "Select finite literal test arguments")
    var rest = arguments
    val purpose = if (rest.headOption.contains("--purpose")) {
      require(rest.size >= 2, "--purpose needs a canonical CNCF purpose")
      val result = rest(1)
      require(Set("SMOKE", "FOCUSED", "ADMISSION", "FULL", "HEAVY")(result), "Unknown CNCF purpose: " + result)
      rest = rest.drop(2)
      result
    } else "EXPLICIT"
    val (patterns, options) = rest.span(_ != "--")
    require(purpose != "FOCUSED" || patterns.nonEmpty,
      "FOCUSED requires actual change-specific suite selection; it is not membership")
    require(patterns.nonEmpty || purpose != "EXPLICIT", "Select a suite pattern or --purpose; empty selection is not PASS")
    var filters = options.drop(1)
    var exact = Vector.empty[String]
    var containing = Vector.empty[String]
    var properties = Vector.empty[String]
    while (filters.nonEmpty) {
      if (Set("-t", "-z")(filters.head)) {
        require(filters.size >= 2 && filters(1).nonEmpty, "ScalaTest name filters require a literal value")
        if (filters.head == "-t") exact :+= filters(1) else containing :+= filters(1)
        filters = filters.drop(2)
      } else {
        val property = filters.head
        require(property.matches("-Dcncf\\.validation\\.(mode|provider|input\\.[a-z][a-z0-9-]{0,63})=.+"),
          "Only shared CNCF validation ConfigMap properties and ScalaTest -t/-z filters are supported")
        require(property.substring(property.indexOf('=') + 1).length <= 512 &&
          !property.exists(character => character == '\"' || character == '\\' || character == ';'),
          "Validation ConfigMap properties require bounded literal values")
        val name = property.takeWhile(_ != '=')
        require(!properties.exists(_.takeWhile(_ != '=') == name), "Duplicate validation ConfigMap property")
        properties :+= property
        require(properties.size <= 18, "At most sixteen scenario inputs and the shared mode/provider pair are supported")
        filters = filters.tail
      }
    }
    Request(purpose, patterns, exact, containing, properties)
  }

  def candidates(request: Request, definitions: Seq[TestDefinition]): Seq[TestDefinition] =
    if (request.patterns.isEmpty) definitions
    else definitions.filter(definition => request.patterns.exists { pattern =>
      val regex = pattern.split("\\*", -1).map(java.util.regex.Pattern.quote).mkString(".*")
      definition.name.matches(regex)
    }).map(definition => new TestDefinition(definition.name, definition.fingerprint,
      request.patterns.contains(definition.name), definition.selectors))

  /** One SBT runner receives the exact suite-local selectors. There is no
    * conversion back to framework arguments and no union of test names.
    */
  def group(selected: Seq[Selected]): Tests.Group = {
    require(selected.nonEmpty, "Empty metadata selection is not PASS")
    require(selected.map(_.definition.name).distinct.size == selected.size, "Duplicate selected suite")
    require(selected.forall(_.definition.selectors.nonEmpty), "Selected suite has no operations")
    Tests.Group("metadata-selected", selected.map(_.definition), Tests.InProcess)
  }

  /** Public sbt Tests/test-interface APIs only. One runner per matching
    * framework sees the entire selected group; its genuine done() summary is
    * retained verbatim for the standard TestResultLogger.
    */
  def execute(selected: Seq[Selected], frameworks: Map[TestFramework, Framework], loader: ClassLoader,
    execution: Tests.Execution, log: sbt.internal.util.ManagedLogger,
    properties: Seq[String] = Seq.empty, fork: Boolean = false): Task[Tests.Output] = {
    require(!fork, "LabelledTests exact-selector adapter does not support Test / fork=true; use the ordinary SBT fork execution owner")
    // These are framework ConfigMap arguments, never JVM properties or process environment.
    val definitions = group(selected).tests.toVector
    def matches(a: Fingerprint, b: Fingerprint): Boolean = (a, b) match {
      case (x: SubclassFingerprint, y: SubclassFingerprint) =>
        x.isModule == y.isModule && x.superclassName == y.superclassName
      case (x: AnnotatedFingerprint, y: AnnotatedFingerprint) =>
        x.isModule == y.isModule && x.annotationName == y.annotationName
      case _ => false
    }
    val matching = frameworks.filter { case (_, framework) =>
      definitions.exists(test => framework.fingerprints().exists(matches(test.fingerprint, _)))
    }
    require(definitions.forall(test => matching.values.exists(_.fingerprints().exists(matches(test.fingerprint, _)))),
      "A selected suite has no loaded test framework")
    val configured = if (properties.isEmpty) execution else execution.copy(options = execution.options ++
      matching.keys.toVector.map(identity => Tests.Argument(identity, properties: _*)))
    val runners = matching.map { case (identity, framework) =>
      val arguments = configured.options.collect {
        case argument: Tests.Argument if argument.framework.forall(_ == identity) => argument.args
      }.flatten
      identity -> framework.runner(arguments.toArray, Array.empty[String], loader)
    }
    // This public Vector overload owns option filtering/setup/cleanup internally.
    // Its ProcessedOptions overload is unusable outside sbt's package boundary.
    Tests(matching, loader, runners, definitions, configured, log).map { output =>
      output.copy(summaries = runners.map { case (identity, runner) =>
        Tests.Summary(matching(identity).name(), runner.done())
      }.toVector)
    }
  }

  def select(classpath: Seq[File], classes: File, revision: String, purpose: String,
    definitions: Seq[TestDefinition], exact: Seq[String] = Seq.empty, containing: Seq[String] = Seq.empty,
    properties: Seq[String] = Seq.empty): Seq[Selected] = {
    val loader = new URLClassLoader(classpath.map(_.toURI.toURL).toArray, ClassLoader.getPlatformClassLoader)
    try {
      val bridge = loader.loadClass("org.simplemodeling.textus.smworkflow.testing.LabelledTestSelection$")
      val instance = bridge.getField("MODULE$").get(null)
      val rows = try {
        try bridge.getMethod("select", classOf[String], classOf[String], classOf[String],
          classOf[Array[String]], classOf[Array[String]], classOf[Array[String]], classOf[Array[String]], classOf[Array[String]])
          .invoke(instance, classes.getCanonicalPath, revision, purpose, definitions.map(_.name).toArray,
            exact.toArray, containing.toArray, definitions.filter(_.explicitlySpecified).map(_.name).toArray, properties.toArray)
          .asInstanceOf[Array[Array[String]]].toSeq
        catch {
          case _: NoSuchMethodException =>
            require(properties.isEmpty, "Installed test-selection bridge does not support managed ConfigMap arguments")
            bridge.getMethod("select", classOf[String], classOf[String], classOf[String],
              classOf[Array[String]], classOf[Array[String]], classOf[Array[String]], classOf[Array[String]])
              .invoke(instance, classes.getCanonicalPath, revision, purpose, definitions.map(_.name).toArray,
                exact.toArray, containing.toArray, definitions.filter(_.explicitlySpecified).map(_.name).toArray)
              .asInstanceOf[Array[Array[String]]].toSeq
        }
      } catch { case error: java.lang.reflect.InvocationTargetException => throw error.getCause }
      val selected = rows.groupBy(_(0))
      definitions.flatMap { definition => selected.get(definition.name).map { operations =>
        Selected(new TestDefinition(definition.name, definition.fingerprint,
          definition.explicitlySpecified,
          operations.map(row => new TestSelector(row(1))).toArray),
          operations.filter(_(2) == "true").map(_(1)).toSet)
      }}
    } finally loader.close()
  }
}
