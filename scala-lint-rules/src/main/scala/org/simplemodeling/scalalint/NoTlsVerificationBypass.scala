package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Bounded JDK security analysis; never includes source values in diagnostics.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class NoTlsVerificationBypass extends SemanticRule("NoTlsVerificationBypass") {
  override def fix(implicit doc: SemanticDocument): Patch = {
    val flow = new SecurityFlow
    def _inspect_(templ: Template): List[Patch] = {
      val parents = templ.inits.map(_.tpe.symbol.value)
      val trust = parents.contains("javax/net/ssl/X509TrustManager#")
      val host = parents.contains("javax/net/ssl/HostnameVerifier#")
      templ.stats.collect {
        case method: Defn.Def if trust && Set("checkServerTrusted", "checkClientTrusted")(method.name.value) &&
            method.paramss.flatten.headOption.flatMap(_.decltpe).exists {
              case Type.Apply(array, List(cert)) => array.symbol.value == "scala/Array#" && cert.symbol.value == "java/security/cert/X509Certificate#"
              case _ => false
            } && method.paramss.flatten.size == 2 =>
          flow.resolve(method.body) match {
            case Lit.Unit() | Term.Block(Nil) => flow.diagnostic(method.name, "Accept-all X509 trust check bypasses certificate verification")
            case call: Term.Apply if flow.api(call.fun, "javax/net/ssl/X509TrustManager#", Set("checkClientTrusted", "checkServerTrusted")) => Patch.empty
            case _: Term.Throw => Patch.empty
            case _ => flow.unresolved(method.name)
          }
        case method: Defn.Def if host && method.name.value == "verify" && method.paramss.flatten.size == 2 &&
            method.paramss.flatten.last.decltpe.exists(_.symbol.value == "javax/net/ssl/SSLSession#") =>
          flow.resolve(method.body) match {
            case Lit.Boolean(true) => flow.diagnostic(method.name, "Always-true JDK hostname verifier bypasses hostname verification")
            case Lit.Boolean(false) => Patch.empty
            case call: Term.Apply if flow.api(call.fun, "javax/net/ssl/HostnameVerifier#", Set("verify")) => Patch.empty
            case _: Term.Throw => Patch.empty
            case _ => flow.unresolved(method.name)
          }
      }
    }
    Patch.fromIterable(doc.tree.collect {
      case init: Init if flow.missingType(init, Set("X509TrustManager", "HostnameVerifier")) => flow.missing(init)
      case templ: Template => Patch.fromIterable(_inspect_(templ))
      case Defn.Val(_, _, Some(tpe), Term.Function(_, body)) if tpe.symbol.value == "javax/net/ssl/HostnameVerifier#" =>
        flow.resolve(body) match {
          case Lit.Boolean(true) => flow.diagnostic(body, "Always-true JDK hostname verifier bypasses hostname verification")
          case Lit.Boolean(false) | _: Term.Throw => Patch.empty
          case call: Term.Apply if flow.api(call.fun, "javax/net/ssl/HostnameVerifier#", Set("verify")) => Patch.empty
          case _ => flow.unresolved(body)
        }
      case call: Term.Apply if flow.api(call.fun, "java/lang/System.", Set("setProperty")) && call.args.size == 2 =>
        val key = flow.string(call.args.head)
        val value = flow.string(call.args(1))
        if ((key.contains("jdk.internal.httpclient.disableHostnameVerification") && value.exists(v => v.isEmpty || v.equalsIgnoreCase("true"))))
          flow.diagnostic(call, "JDK HTTP client hostname verification bypass configuration; application depends on client initialization timing")
        else if (key.contains("jdk.internal.httpclient.disableHostnameVerification") && value.isEmpty) flow.unresolved(call)
        else Patch.empty
      case call: Term.Apply if flow.api(call.fun, "javax/net/ssl/SSLParameters#", Set("setEndpointIdentificationAlgorithm")) && call.args.nonEmpty =>
        flow.resolve(call.args.head) match {
          case Lit.Null() | Lit.String("") => flow.diagnostic(call, "JDK endpoint identification is explicitly disabled")
          case _ if flow.string(call.args.head).isDefined => Patch.empty
          case _ => flow.unresolved(call)
        }
    })
  }
}
