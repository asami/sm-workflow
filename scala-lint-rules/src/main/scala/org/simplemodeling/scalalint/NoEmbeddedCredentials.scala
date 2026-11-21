package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Bounded JDK security analysis; never includes source values in diagnostics.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class NoEmbeddedCredentials extends SemanticRule("NoEmbeddedCredentials") {
  override def fix(implicit doc: SemanticDocument): Patch = {
    val flow = new SecurityFlow
    Patch.fromIterable(flow.calls.flatMap { call =>
      val args = call.args
      val selected = if (flow.api(call.fun, "java/sql/DriverManager.", Set("getConnection")) && args.size == 3) args.drop(2)
        else if (flow.api(call.fun, "java/security/KeyStore#", Set("load"))) args.lift(1).toList
        else if (flow.api(call.fun, "java/security/KeyStore#", Set("setKeyEntry"))) args.lift(if (args.size == 4) 2 else 1).toList
        else Nil
      selected.map { value =>
        if (flow.constant(value) && flow.resolve(value) != Lit.Null()) flow.diagnostic(value, "Embedded credential at JDK credential API; value redacted")
        else if (flow.external(value) || flow.resolve(value) == Lit.Null()) Patch.empty else flow.unresolved(value)
      }
    } ++ doc.tree.collect {
      case init: Init if flow.missingType(init, Set("SecretKeySpec", "PKCS8EncodedKeySpec", "PasswordAuthentication")) => flow.missing(init)
      case Term.New(init) if Set("javax/crypto/spec/SecretKeySpec#", "java/security/spec/PKCS8EncodedKeySpec#", "java/net/PasswordAuthentication#")(init.tpe.symbol.value) && init.argss.flatten.nonEmpty =>
        val value = if (init.tpe.symbol.value == "java/net/PasswordAuthentication#") init.argss.flatten.last else init.argss.flatten.head
        if (flow.constant(value)) flow.diagnostic(value, "Embedded credential or private key at JDK key API; value redacted") else if (flow.external(value) || flow.resolve(value) == Lit.Null()) Patch.empty else flow.unresolved(value)
      case call: Term.Apply if flow.unavailable(call.fun, Set("getConnection", "setKeyEntry")) => flow.missing(call.fun)
    })
  }
}
