package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Bounded JDK security analysis; never includes source values in diagnostics.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class NoPredictableSecurityRandom extends SemanticRule("NoPredictableSecurityRandom") {
  override def fix(implicit doc: SemanticDocument): Patch = {
    val flow = new SecurityFlow
    def _predictable_(tree: Tree): Boolean = {
      val value = flow.resolve(tree)
      value match {
        case Term.New(init) if init.tpe.symbol.value == "java/security/SecureRandom#" => init.argss.flatten.exists(flow.constant)
        case call: Term.Apply if flow.api(call.fun, "java/lang/System.", Set("currentTimeMillis", "nanoTime")) => true
        case Term.Apply(Term.Select(receiver, name), _) if Set("nextLong", "nextInt", "nextDouble", "nextFloat", "nextBytes")(name.value) =>
          flow.created(receiver, "java/util/Random#") || flow.created(receiver, "scala/util/Random#") ||
            name.symbol.value.startsWith("java/util/concurrent/ThreadLocalRandom#") || name.symbol.value.startsWith("scala/util/Random.")
        case _ => value.children.exists(_predictable_)
      }
    }
    def _entropy_(tree: Tree, sink: Tree): Option[Boolean] =
      if (_predictable_(tree)) Some(true) else flow.bytesEntropy(tree, sink)
    Patch.fromIterable(doc.tree.collect {
      case init: Init if flow.missingType(init, Set("SecretKeySpec", "IvParameterSpec", "GCMParameterSpec", "SecureRandom")) => flow.missing(init)
      case Term.New(init) if Set("javax/crypto/spec/SecretKeySpec#", "javax/crypto/spec/IvParameterSpec#", "javax/crypto/spec/GCMParameterSpec#")(init.tpe.symbol.value) =>
        val args = init.argss.flatten
        val value = if (init.tpe.symbol.value == "javax/crypto/spec/GCMParameterSpec#") args.lift(1) else args.headOption
        value.map { arg =>
          _entropy_(arg, init) match {
            case Some(true) => flow.diagnostic(arg, "Predictable random or timestamp data reaches a JDK key/IV/nonce API")
            case Some(false) => Patch.empty
            case None if flow.constant(arg) && !flow.hasEntropyWrite(arg, init) => Patch.empty
            case _ => flow.unresolved(arg)
          }
        }.getOrElse(Patch.empty)
      case call: Term.Apply if flow.api(call.fun, "javax/crypto/KeyGenerator#", Set("init")) || flow.api(call.fun, "java/security/KeyPairGenerator#", Set("initialize")) =>
        val random = if (call.args.size >= 2) call.args.lastOption else call.args.find(arg => flow.created(arg, "java/security/SecureRandom#"))
        random.map { arg =>
          flow.secureEntropy(arg, call) match {
            case Some(true) => flow.diagnostic(arg, "Fixed-seeded SecureRandom reaches JDK key generation")
            case Some(false) => Patch.empty
            case None => flow.unresolved(arg)
          }
        }.getOrElse(Patch.empty)
      case call: Term.Apply if flow.unavailable(call.fun, Set("nextBytes", "setSeed")) => flow.missing(call.fun)
    })
  }
}
