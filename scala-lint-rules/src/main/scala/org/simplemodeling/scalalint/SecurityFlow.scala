package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._
import scalafix.lint.LintSeverity

/** Bounded JDK security analysis; never includes source values in diagnostics.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[scalalint] final class SecurityFlow(implicit doc: SemanticDocument) {
  private val _values = doc.tree.collect {
    case Defn.Val(_, List(pat: Pat.Var), _, rhs) if !pat.name.symbol.isNone => pat.name.symbol -> rhs
  }.toMap

  def resolve(tree: Tree, seen: Set[Symbol] = Set.empty): Tree = tree match {
    case name: Term.Name if !seen(name.symbol) && _values.contains(name.symbol) =>
      resolve(_values(name.symbol), seen + name.symbol)
    case Term.Ascribe(value, _) => resolve(value, seen)
    case Term.Block(stats) if stats.nonEmpty && stats.dropRight(1).forall(_.isInstanceOf[Defn.Val]) => resolve(stats.last, seen)
    case other => other
  }

  def api(tree: Tree, owner: String, methods: Set[String]): Boolean = tree match {
    case name: Term.Name => methods.exists { method =>
      name.symbol.value.startsWith(owner + method + "(") ||
        (owner.endsWith(".") && name.symbol.value.startsWith(owner.dropRight(1) + "#" + method + "("))
    }
    case Term.Select(_, name) => api(name, owner, methods)
    case Term.ApplyType(fun, _) => api(fun, owner, methods)
    case _ => false
  }

  def unavailable(tree: Tree, methods: Set[String]): Boolean = tree match {
    case Term.Select(_, name) => methods(name.value) && name.symbol.isNone
    case name: Term.Name => methods(name.value) && name.symbol.isNone
    case _ => false
  }

  def string(tree: Tree): Option[String] = resolve(tree) match {
    case Lit.String(value) => Some(value)
    case Term.ApplyInfix(left, Term.Name("+"), _, List(right)) =>
      for (a <- string(left); b <- string(right)) yield a + b
    case Term.If(_, left, right) => for (a <- string(left); b <- string(right) if a == b) yield a
    case _ => None
  }

  def constant(tree: Tree): Boolean = resolve(tree) match {
    case _: Lit => true
    case Term.Select(value, Term.Name("toCharArray")) => string(value).isDefined
    case Term.Apply(Term.Select(value, name), args)
        if Set("getBytes", "toCharArray")(name.value) => string(value).isDefined
    case Term.Apply(fun, args) if fun.symbol.value.startsWith("scala/Array.") => args.forall(constant)
    case Term.ApplyType(fun, _) if fun.symbol.value.startsWith("scala/Array.") => true
    case Term.Apply(Term.ApplyType(fun, _), args) if fun.symbol.value.startsWith("scala/Array.") => args.forall(constant)
    case Term.ApplyInfix(left, Term.Name("+"), _, List(right)) => constant(left) && constant(right)
    case Term.Interpolate(Term.Name("s"), _, args) => args.forall(constant)
    case Term.If(_, left, right) => constant(left) && constant(right)
    case _ => false
  }

  def external(tree: Tree): Boolean = resolve(tree) match {
    case call: Term.Apply if api(call.fun, "java/lang/System.", Set("getenv", "getProperty")) => true
    case Term.Select(value, Term.Name("toCharArray")) => external(value)
    case Term.Apply(Term.Select(value, name), _) if Set("getBytes", "toCharArray")(name.value) => external(value)
    case _ => false
  }

  def identity(tree: Tree, seen: Set[Symbol] = Set.empty): Symbol = tree match {
    case name: Term.Name if !seen(name.symbol) => _values.get(name.symbol) match {
      case Some(alias: Term.Name) => identity(alias, seen + name.symbol)
      case _ => name.symbol
    }
    case _ => tree.symbol
  }

  def created(tree: Tree, owner: String): Boolean = resolve(tree) match {
    case Term.New(init) => init.tpe.symbol.value == owner
    case _ => false
  }

  private def _scope(tree: Tree): Option[Tree] = tree.parent match {
    case Some(block: Term.Block) => Some(block)
    case Some(method: Defn.Def) => Some(method)
    case Some(templ: Template) => Some(templ)
    case Some(parent) => _scope(parent)
    case None => None
  }

  // Only an eager statement (or an eager val initializer) in the sink's
  // immediate scope establishes state. Do not traverse branches, callbacks,
  // lazy initializers, local definitions, or possibly by-name arguments.
  private def _unconditional_before(effect: Tree, sink: Tree): Boolean = {
    def _eager_(tree: Tree, scope: Tree): Boolean = tree.parent match {
      case Some(parent) if parent eq scope => true
      case Some(value: Defn.Val) if (value.rhs eq tree) && !value.mods.exists(_.isInstanceOf[Mod.Lazy]) => _eager_(value, scope)
      case Some(parent: Term.Ascribe) => _eager_(parent, scope)
      case _ => false
    }
    effect.pos.end < sink.pos.start && _scope(sink).exists(scope => _eager_(effect, scope))
  }

  def secureEntropy(tree: Tree, sink: Tree): Option[Boolean] = resolve(tree) match {
    case Term.New(init) if init.tpe.symbol.value == "java/security/SecureRandom#" && _scope(init) == _scope(sink) =>
      if (init.argss.flatten.nonEmpty) {
        if (init.argss.flatten.forall(constant)) Some(true) else None
      } else {
        val seeds = calls.filter { call =>
          api(call.fun, "java/security/SecureRandom#", Set("setSeed")) && call.pos.end < sink.pos.start &&
            (call.fun match { case Term.Select(receiver, _) => !identity(tree).isNone && identity(receiver) == identity(tree); case _ => false })
        }.sortBy(_.pos.start)
        val uses = calls.filter { call =>
          call.pos.end < seeds.headOption.map(_.pos.start).getOrElse(-1) &&
            (api(call.fun, "java/security/SecureRandom#", Set("nextBytes", "nextInt", "nextLong", "generateSeed")) ||
              api(call.fun, "java/util/Random#", Set("nextInt", "nextLong"))) &&
            (call.fun match { case Term.Select(receiver, _) => !identity(tree).isNone && identity(receiver) == identity(tree); case _ => false })
        }
        if (seeds.exists(seed => !_unconditional_before(seed, sink)) ||
            uses.exists(use => !seeds.headOption.exists(seed => _unconditional_before(use, seed)))) None
        else if (seeds.isEmpty || uses.nonEmpty) Some(false)
        else if (seeds.size == 1 && seeds.head.args.forall(constant)) Some(true) else None
      }
    case _ => None
  }

  private def _byte_writes(tree: Tree, sink: Tree): List[Term.Apply] = calls.collect {
    case call @ Term.Apply(Term.Select(_, name), List(arg))
        if name.value == "nextBytes" && !identity(tree).isNone && identity(arg) == identity(tree) &&
          call.pos.end < sink.pos.start => call
  }

  def hasEntropyWrite(tree: Tree, sink: Tree): Boolean = _byte_writes(tree, sink).nonEmpty

  def bytesEntropy(tree: Tree, sink: Tree): Option[Boolean] = {
    val writes = _byte_writes(tree, sink).collect {
      case call @ Term.Apply(Term.Select(receiver, name), List(arg))
          if _unconditional_before(call, sink) => resolve(receiver) match {
          case Term.New(init) if init.tpe.symbol.value == "java/security/SecureRandom#" =>
            secureEntropy(receiver, call)
          case _ if created(receiver, "java/util/Random#") || created(receiver, "scala/util/Random#") ||
              name.symbol.value.startsWith("scala/util/Random.") ||
              name.symbol.value.startsWith("java/util/concurrent/ThreadLocalRandom#") => Some(true)
          case _ => None
        }
      case _ => None
    }
    if (writes.size == 1) writes.head else None
  }

  def missingType(init: Init, names: Set[String]): Boolean = init.tpe.symbol.isNone &&
    init.tpe.collect { case name: Type.Name if names(name.value) => name }.nonEmpty

  def diagnostic(tree: Tree, message: String): Patch =
    Patch.lint(Diagnostic("", message, tree.pos))

  def unresolved(tree: Tree): Patch = Patch.lint(Diagnostic("UnresolvedFlow",
    "Security-relevant flow is unresolved by bounded local analysis; only unconditional local effects establish state; review is required; no safety or vulnerability conclusion", tree.pos, severity = LintSeverity.Warning))

  def missing(tree: Tree): Patch = Patch.lint(Diagnostic("AnalysisUnavailable",
    "Required security API symbol analysis is unavailable", tree.pos))

  def calls: List[Term.Apply] = doc.tree.collect { case call: Term.Apply => call }
}
