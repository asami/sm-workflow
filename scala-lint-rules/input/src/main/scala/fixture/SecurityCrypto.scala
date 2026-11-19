/*
rules = [NoWeakCryptographicConfiguration]
*/
package fixture

/** Compiler-produced SemanticDB cases; fixture secrets are dummy data only.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object SecurityCrypto {
  def weak(): javax.crypto.Cipher = {
    val algorithm = "DES/CBC/PKCS5Padding"
    javax.crypto.Cipher.getInstance(algorithm) // assert: NoWeakCryptographicConfiguration
  }
  def signature(): java.security.Signature =
    java.security.Signature.getInstance("SHA1withRSA") // assert: NoWeakCryptographicConfiguration
  def nonce(key: java.security.Key): Unit = {
    val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
    val nonce = Array[Byte](0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, new javax.crypto.spec.GCMParameterSpec(128, nonce)) // assert: NoWeakCryptographicConfiguration
  }
  def digestKey(data: Array[Byte]): javax.crypto.spec.SecretKeySpec = {
    val digest = java.security.MessageDigest.getInstance("MD5")
    val bytes = digest.digest(data)
    new javax.crypto.spec.SecretKeySpec(bytes, "AES") // assert: NoWeakCryptographicConfiguration
  }
  def freshNonce(key: java.security.Key): Unit = {
    val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
    val bytes = new Array[Byte](12)
    new java.security.SecureRandom().nextBytes(bytes)
    cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, new javax.crypto.spec.GCMParameterSpec(128, bytes))
  }
  def conditionalNonce(key: java.security.Key, flag: Boolean): Unit = {
    val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
    val bytes = new Array[Byte](12)
    val random = new java.security.SecureRandom()
    if (flag) random.nextBytes(bytes)
    cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, new javax.crypto.spec.GCMParameterSpec(128, bytes)) // assert: NoWeakCryptographicConfiguration.UnresolvedFlow
  }
  def deferredNonce(key: java.security.Key): Unit = {
    val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
    val bytes = new Array[Byte](12)
    val random = new java.security.SecureRandom()
    val fill = () => random.nextBytes(bytes)
    cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, new javax.crypto.spec.GCMParameterSpec(128, bytes)) // assert: NoWeakCryptographicConfiguration.UnresolvedFlow
  }
  def appropriate(): javax.crypto.Cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
  def checksum(data: Array[Byte]): Array[Byte] = java.security.MessageDigest.getInstance("MD5").digest(data)
  def unresolved(algorithm: String): javax.crypto.Cipher =
    javax.crypto.Cipher.getInstance(algorithm) // assert: NoWeakCryptographicConfiguration.UnresolvedFlow
  final class Other { def getInstance(value: String): String = value }
  def unrelated(value: Other): String = value.getInstance("DES")
  def intentional(): javax.crypto.Cipher =
    // reviewed fixture exception, SLP-A24
    javax.crypto.Cipher.getInstance("DES") // scalafix:ok NoWeakCryptographicConfiguration
}
