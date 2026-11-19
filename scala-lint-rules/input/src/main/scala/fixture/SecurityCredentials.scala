/*
rules = [NoEmbeddedCredentials]
*/
package fixture

/** Compiler-produced SemanticDB cases; fixture secrets are dummy data only.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object SecurityCredentials {
  def embedded(): java.sql.Connection = {
    val dummy = "fixture-only-not-a-real-password"
    java.sql.DriverManager.getConnection("jdbc:fixture", "fixture", dummy) // assert: NoEmbeddedCredentials
  }
  def key(): javax.crypto.spec.SecretKeySpec = {
    val bytes = "fixture-only-key".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    new javax.crypto.spec.SecretKeySpec(bytes, "AES") // assert: NoEmbeddedCredentials
  }
  def privateKey(): java.security.spec.PKCS8EncodedKeySpec = {
    val dummy = Array[Byte](1, 2, 3)
    new java.security.spec.PKCS8EncodedKeySpec(dummy) // assert: NoEmbeddedCredentials
  }
  def external(): java.sql.Connection =
    java.sql.DriverManager.getConnection("jdbc:fixture", "fixture", System.getenv("FIXTURE_CREDENTIAL"))
  def unresolved(value: String): java.sql.Connection =
    java.sql.DriverManager.getConnection("jdbc:fixture", "fixture", value) // assert: NoEmbeddedCredentials.UnresolvedFlow
  final class Other { def getConnection(a: String, b: String, c: String): Unit = () }
  def unrelated(value: Other): Unit = value.getConnection("a", "b", "ordinary text")
  def intentional(): java.sql.Connection =
    // fixture exception, SLP-A21
    java.sql.DriverManager.getConnection("jdbc:fixture", "fixture", "dummy") // scalafix:ok NoEmbeddedCredentials
}
