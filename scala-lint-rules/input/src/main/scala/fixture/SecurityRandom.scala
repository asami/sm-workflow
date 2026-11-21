/*
rules = [NoPredictableSecurityRandom]
*/
package fixture

/** Compiler-produced SemanticDB cases; fixture secrets are dummy data only.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object SecurityRandom {
  def predictable(): javax.crypto.spec.SecretKeySpec = {
    val bytes = new Array[Byte](16)
    val random = new java.util.Random(1L)
    random.nextBytes(bytes)
    new javax.crypto.spec.SecretKeySpec(bytes, "AES") // assert: NoPredictableSecurityRandom
  }
  def seeded(): java.security.KeyPair = {
    val seed = Array[Byte](1, 2, 3, 4)
    val random = new java.security.SecureRandom(seed)
    val generator = java.security.KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048, random) // assert: NoPredictableSecurityRandom
    generator.generateKeyPair()
  }
  def seededBeforeFirstUse(): java.security.KeyPair = {
    val random = new java.security.SecureRandom()
    random.setSeed(1L)
    val generator = java.security.KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048, random) // assert: NoPredictableSecurityRandom
    generator.generateKeyPair()
  }
  def timestamp(): javax.crypto.spec.IvParameterSpec = {
    val bytes = System.currentTimeMillis().toString.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    new javax.crypto.spec.IvParameterSpec(bytes) // assert: NoPredictableSecurityRandom
  }
  def fresh(): javax.crypto.spec.SecretKeySpec = {
    val bytes = new Array[Byte](16)
    val random = new java.security.SecureRandom()
    random.nextBytes(bytes)
    new javax.crypto.spec.SecretKeySpec(bytes, "AES")
  }
  def conditionalBytes(flag: Boolean): javax.crypto.spec.IvParameterSpec = {
    val bytes = new Array[Byte](16)
    val random = new java.security.SecureRandom()
    if (flag) random.nextBytes(bytes)
    new javax.crypto.spec.IvParameterSpec(bytes) // assert: NoPredictableSecurityRandom.UnresolvedFlow
  }
  def conditionalConstantBytes(flag: Boolean): javax.crypto.spec.IvParameterSpec = {
    val bytes = Array[Byte](0, 0, 0, 0)
    val random = new java.security.SecureRandom()
    if (flag) { random.nextBytes(bytes) }
    new javax.crypto.spec.IvParameterSpec(bytes) // assert: NoPredictableSecurityRandom.UnresolvedFlow
  }
  def deferredBytes(): javax.crypto.spec.SecretKeySpec = {
    val bytes = new Array[Byte](16)
    val random = new java.security.SecureRandom()
    val fill = () => random.nextBytes(bytes)
    new javax.crypto.spec.SecretKeySpec(bytes, "AES") // assert: NoPredictableSecurityRandom.UnresolvedFlow
  }
  def lazyBytes(): javax.crypto.spec.SecretKeySpec = {
    val bytes = new Array[Byte](16)
    val random = new java.security.SecureRandom()
    lazy val fill = random.nextBytes(bytes)
    new javax.crypto.spec.SecretKeySpec(bytes, "AES") // assert: NoPredictableSecurityRandom.UnresolvedFlow
  }
  def conditionalSeed(flag: Boolean): java.security.KeyPair = {
    val random = new java.security.SecureRandom()
    if (flag) random.setSeed(1L)
    val generator = java.security.KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048, random) // assert: NoPredictableSecurityRandom.UnresolvedFlow
    generator.generateKeyPair()
  }
  def deferredSeed(): java.security.KeyPair = {
    val random = new java.security.SecureRandom()
    def seed(): Unit = random.setSeed(1L)
    val generator = java.security.KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048, random) // assert: NoPredictableSecurityRandom.UnresolvedFlow
    generator.generateKeyPair()
  }
  def conditionalPriorUse(flag: Boolean): java.security.KeyPair = {
    val random = new java.security.SecureRandom()
    if (flag) random.nextInt()
    random.setSeed(1L)
    val generator = java.security.KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048, random) // assert: NoPredictableSecurityRandom.UnresolvedFlow
    generator.generateKeyPair()
  }
  def deferredPriorUse(): java.security.KeyPair = {
    val random = new java.security.SecureRandom()
    val use = () => random.nextInt()
    random.setSeed(1L)
    val generator = java.security.KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048, random) // assert: NoPredictableSecurityRandom.UnresolvedFlow
    generator.generateKeyPair()
  }
  def usedBeforeSeed(): java.security.KeyPair = {
    val random = new java.security.SecureRandom()
    random.nextInt()
    random.setSeed(1L)
    val generator = java.security.KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048, random)
    generator.generateKeyPair()
  }
  def unresolved(bytes: Array[Byte]): javax.crypto.spec.SecretKeySpec =
    new javax.crypto.spec.SecretKeySpec(bytes, "AES") // assert: NoPredictableSecurityRandom.UnresolvedFlow
  def simulation(): Int = new java.util.Random(1L).nextInt(10)
  final class Other { def nextBytes(value: Array[Byte]): Unit = () }
  def unrelated(value: Other): Unit = value.nextBytes(Array[Byte](1))
  def intentional(): javax.crypto.spec.SecretKeySpec = {
    val bytes = new Array[Byte](16)
    new java.util.Random(1L).nextBytes(bytes)
    // deterministic reviewed fixture, SLP-A26
    new javax.crypto.spec.SecretKeySpec(bytes, "AES") // scalafix:ok NoPredictableSecurityRandom
  }
}
