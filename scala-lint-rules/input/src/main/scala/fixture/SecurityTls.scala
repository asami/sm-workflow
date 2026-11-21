/*
rules = [NoTlsVerificationBypass]
*/
package fixture

/** Compiler-produced SemanticDB cases; fixture secrets are dummy data only.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object SecurityTls {
  final class AcceptAll extends javax.net.ssl.X509TrustManager {
    override def getAcceptedIssuers(): Array[java.security.cert.X509Certificate] = Array.empty
    override def checkClientTrusted(chain: Array[java.security.cert.X509Certificate], authType: String): Unit = () // assert: NoTlsVerificationBypass
    override def checkServerTrusted(chain: Array[java.security.cert.X509Certificate], authType: String): Unit = () // assert: NoTlsVerificationBypass
  }
  final class Verify extends javax.net.ssl.HostnameVerifier {
    override def verify(hostname: String, session: javax.net.ssl.SSLSession): Boolean = true // assert: NoTlsVerificationBypass
  }
  val sam: javax.net.ssl.HostnameVerifier = (hostname, session) => true // assert: NoTlsVerificationBypass
  final class Delegate(delegate: javax.net.ssl.X509TrustManager) extends javax.net.ssl.X509TrustManager {
    override def getAcceptedIssuers(): Array[java.security.cert.X509Certificate] = delegate.getAcceptedIssuers()
    override def checkClientTrusted(chain: Array[java.security.cert.X509Certificate], authType: String): Unit = delegate.checkClientTrusted(chain, authType)
    override def checkServerTrusted(chain: Array[java.security.cert.X509Certificate], authType: String): Unit = delegate.checkServerTrusted(chain, authType)
  }
  def alias(): javax.net.ssl.HostnameVerifier = new javax.net.ssl.HostnameVerifier {
    override def verify(hostname: String, session: javax.net.ssl.SSLSession): Boolean = { val accepted = true; accepted } // assert: NoTlsVerificationBypass
  }
  def switches(params: javax.net.ssl.SSLParameters): Unit = {
    val disabled = ""
    params.setEndpointIdentificationAlgorithm(disabled) // assert: NoTlsVerificationBypass
    params.setEndpointIdentificationAlgorithm("HTTPS")
  }
  def unresolved(params: javax.net.ssl.SSLParameters, mode: String): Unit =
    params.setEndpointIdentificationAlgorithm(mode) // assert: NoTlsVerificationBypass.UnresolvedFlow
  final class Other { def setEndpointIdentificationAlgorithm(value: String): Unit = () }
  def unrelated(value: Other): Unit = value.setEndpointIdentificationAlgorithm("")
  def intentional(params: javax.net.ssl.SSLParameters): Unit =
    // reviewed fixture exception, SLP-A23
    params.setEndpointIdentificationAlgorithm("") // scalafix:ok NoTlsVerificationBypass
}
