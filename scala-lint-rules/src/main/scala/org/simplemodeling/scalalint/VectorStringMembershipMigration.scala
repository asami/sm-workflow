package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._
import metaconfig.Configured
import java.net.URLClassLoader
import java.nio.file.{Path, Paths}
import java.lang.reflect.Modifier
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

/** Opt-in, exact String value membership migration. No profile enables it.
  * @since   Nov. 27, 2026
  * @version Nov. 27, 2026
  * @author  ASAMI, Tomoharu
  */
final class VectorStringMembershipMigration private (
  private val _api_available: Boolean
) extends SemanticRule("VectorStringMembershipMigration") {
  def this() = this(false)

  override def withConfiguration(config: Configuration): Configured[Rule] =
    Configured.ok(new VectorStringMembershipMigration(_classpath_api(config.scalacClasspath.map(_.toNIO))))

  /** Scala 3 Core members are not exposed by the pinned SemanticDB symbol
    * table. Inspect only the compiler's configured classpath, without class
    * initialization or invocation; the successor compiler still checks the
    * Scala type arguments and equality evidence. Duplicate definitions refuse
    * migration rather than depending on classpath order. */
  private def _classpath_api(classpath: List[Path]): Boolean = try {
    val roots = classpath.map(_.toRealPath()).distinct
    val loader = new URLClassLoader(roots.map(_.toUri.toURL).toArray, null)
    try {
      val names = List("org.goldenport.util.TypeSafeCollections$",
        "org.goldenport.util.TypeSafeCollections$VectorValues", "cats.kernel.Eq$")
      val unique = names.forall(name => loader.getResources(name.replace('.', '/') + ".class").asScala.size == 1)
      if (!unique) false
      else {
        val classes = names.map(name => Class.forName(name, false, loader))
        val origins = classes.map(found => Paths.get(found.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath())
        origins.forall(roots.contains) && origins.head == origins(1) &&
          classes.forall(found => Modifier.isPublic(found.getModifiers)) &&
          _method(classes.head, "typeSafe", List("scala.collection.immutable.Vector"), names(1)) &&
          _method(classes(1), "containsEq", List("java.lang.Object", "cats.kernel.Eq", "scala.$eq$colon$eq"), "boolean") &&
          _method(classes(2), "fromUniversalEquals", Nil, "cats.kernel.Eq")
      }
    } finally loader.close()
  } catch {
    case NonFatal(_) => false
    case _: LinkageError => false
  }

  private def _method(owner: Class[_], name: String, parameters: List[String], result: String): Boolean =
    owner.getDeclaredMethods.filter(_.getName == name).toList match {
      case method :: Nil => Modifier.isPublic(method.getModifiers) && !Modifier.isStatic(method.getModifiers) &&
        method.getParameterTypes.toList.map(_.getName) == parameters && method.getReturnType.getName == result
      case _ => false
    }

  private def _atom(term: Term): Boolean = term match {
    case name: Term.Name => name.syntax.matches("[A-Za-z_][A-Za-z0-9_]*")
    case literal: Lit.String => literal.syntax.matches("\"[A-Za-z0-9 _.-]*\"")
    case _ => false
  }

  private def _render(receiver: Term, argument: Term): String =
    "_root_.org.goldenport.util.TypeSafeCollections.typeSafe[_root_.scala.Predef.String](" + receiver.syntax +
      ").containsEq[_root_.scala.Predef.String](" + argument.syntax +
      ")(using _root_.cats.Eq.fromUniversalEquals[_root_.scala.Predef.String])"

  override def fix(implicit doc: SemanticDocument): Patch = {
    val calls = doc.tree.collect {
      case call @ Term.Apply(Term.Select(receiver, Term.Name("contains")), List(argument)) => (call, receiver, argument)
    }
    Patch.fromIterable(calls.map { case (call, receiver, argument) =>
      val known = SafetySemantic.tpe(receiver).nonEmpty && SafetySemantic.tpe(argument).nonEmpty && SafetySemantic.info(call).nonEmpty
      if (!known || !_api_available)
        SafetySemantic.unresolved(call, "Vector membership migration and Core API")
      else if (calls.size != 1 || !receiver.isInstanceOf[Term.Name] || !_atom(receiver) || !_atom(argument) ||
          call.pos.startLine != call.pos.endLine || doc.input.text.length > 1048576 ||
          !SafetySemantic.vectorStringMembership(receiver, argument, call))
        Patch.lint(Diagnostic("Refused", "Only one resolved immutable Vector[String].contains(String) value comparison is supported", call.pos))
      else Patch.replaceTree(call, _render(receiver, argument))
    })
  }
}
