package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Bounded JDK security analysis; never includes source values in diagnostics.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class NoWeakCryptographicConfiguration extends SemanticRule("NoWeakCryptographicConfiguration") {
  override def fix(implicit doc: SemanticDocument): Patch = {
    val flow = new SecurityFlow
    Patch.fromIterable(flow.calls.collect {
      case call if (flow.api(call.fun, "javax/crypto/Cipher.", Set("getInstance")) ||
          flow.api(call.fun, "java/security/Signature.", Set("getInstance")) ||
          flow.api(call.fun, "javax/crypto/Mac.", Set("getInstance"))) && call.args.nonEmpty =>
        flow.string(call.args.head) match {
          case Some(value) =>
            val algorithm = value.toUpperCase(java.util.Locale.ROOT)
            val weak = algorithm == "DES" || algorithm.startsWith("DES/") || algorithm.startsWith("DESEDE") ||
              algorithm == "RC4" || algorithm == "ARCFOUR" || algorithm.contains("/ECB/") ||
              algorithm.contains("MD5") || algorithm.contains("SHA1") || algorithm.contains("SHA-1")
            if (weak) flow.diagnostic(call.args.head, "Weak JDK security algorithm or cipher mode")
            else if (flow.api(call.fun, "javax/crypto/Cipher.", Set("getInstance")) && !algorithm.contains("/")) flow.unresolved(call.args.head)
            else Patch.empty
          case _ => flow.unresolved(call.args.head)
        }
      case call if flow.api(call.fun, "javax/crypto/Cipher#", Set("init")) && call.args.size >= 3 =>
        val params = flow.resolve(call.args(2))
        params match {
          case Term.New(init) if Set("javax/crypto/spec/IvParameterSpec#", "javax/crypto/spec/GCMParameterSpec#")(init.tpe.symbol.value) =>
            val args = init.argss.flatten
            val iv = if (init.tpe.symbol.value == "javax/crypto/spec/GCMParameterSpec#") args.lift(1) else args.headOption
            val receiver = call.fun match { case Term.Select(value, _) => flow.resolve(value); case _ => call.fun }
            val algorithm = receiver match {
              case Term.Apply(fun, values) if flow.api(fun, "javax/crypto/Cipher.", Set("getInstance")) => values.headOption.flatMap(flow.string)
              case _ => None
            }
            val fresh = algorithm.exists(value => {
              val mode = value.toUpperCase(java.util.Locale.ROOT)
              mode.contains("/CBC/") || mode.contains("/GCM/") || mode.contains("/CTR/") || mode.contains("CHACHA20")
            })
            val encrypt = Set("javax/crypto/Cipher.ENCRYPT_MODE.", "javax/crypto/Cipher#ENCRYPT_MODE.")(call.args.head.symbol.value) || flow.resolve(call.args.head) == Lit.Int(1)
            if (encrypt && fresh && iv.exists(value => flow.bytesEntropy(value, call).contains(false))) Patch.empty
            else if (encrypt && fresh && iv.exists(flow.constant)) flow.diagnostic(call.args(2), "Constant encryption IV/nonce; freshness or uniqueness must be established for the selected cipher")
            else if (encrypt && (!fresh || iv.exists(value => !flow.constant(value)))) flow.unresolved(call.args(2)) else Patch.empty
          case _ => flow.unresolved(call.args(2))
        }
      case call if flow.unavailable(call.fun, Set("getInstance")) => flow.missing(call.fun)
    } ++ doc.tree.collect {
      case init: Init if flow.missingType(init, Set("IvParameterSpec", "GCMParameterSpec", "SecretKeySpec")) => flow.missing(init)
      case Term.New(init) if init.tpe.symbol.value == "javax/crypto/spec/SecretKeySpec#" && init.argss.flatten.nonEmpty =>
        flow.resolve(init.argss.flatten.head) match {
          case Term.Apply(Term.Select(receiver, name), _) if flow.api(name, "java/security/MessageDigest#", Set("digest")) =>
            flow.resolve(receiver) match {
              case Term.Apply(fun, args) if flow.api(fun, "java/security/MessageDigest.", Set("getInstance")) && args.nonEmpty =>
                flow.string(args.head) match {
                  case Some(value) if Set("MD5", "SHA-1", "SHA1")(value.toUpperCase(java.util.Locale.ROOT)) =>
                    flow.diagnostic(init, "Weak digest output used as JDK cryptographic key material")
                  case Some(_) => Patch.empty
                  case _ => flow.unresolved(init)
                }
              case _ => flow.unresolved(init)
            }
          case _ => Patch.empty
        }
    })
  }
}
