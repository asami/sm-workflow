package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import org.goldenport.Consequence

/** Opaque application lookup; never an authority or acceptance token.
  * @since Oct. 25, 2026
  * @version Oct. 25, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object DevelopmentEventCorrelation {
  val namespace = "org.simplemodeling.textus.development.v1"
  def keyC(subject: DevelopmentOperationalView.Subject): Consequence[String] =
    if (!DevelopmentOperationalView.valid(subject)) Consequence.valueInvalid("Invalid development subject")
    else {
      val tuple = Vector("sm-workflow.development-subject.v1", subject.car.group, subject.car.artifact,
        subject.car.version, subject.producerComponentId, subject.producerInstanceId, subject.workflow,
        subject.sessionId, subject.repositoryIdentity, subject.phaseIdentity)
      val bytes = Json.fromValues(tuple.map(Json.fromString)).noSpaces.getBytes(UTF_8)
      Consequence.success(MessageDigest.getInstance("SHA-256").digest(bytes).map(b => f"${b & 255}%02x").mkString)
    }
  def attributesC(subject: DevelopmentOperationalView.Subject): Consequence[Map[String, String]] =
    keyC(subject).map(key => Map("history.namespace" -> namespace, "history.key" -> key))
}
