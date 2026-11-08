package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.resource.ResourceReference
import org.goldenport.convert.ValueReader
import org.goldenport.protocol.Request

/** Shared transport only. The executing call owns Resource authorization,
  * finite streaming acquisition and UTF-8 decoding; business admission follows.
  * @since   Oct. 24, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeWorkflowResourceInput {
  sealed trait Source[+A]
  final case class Inline[A](value: A) extends Source[A]
  final case class Resource(reference: ResourceReference.Url) extends Source[Nothing]

  def referenceC(value: String): Consequence[ResourceReference.Url] =
    if (value == null || value.isEmpty || value.trim != value) _invalid("requires an absolute local file URI")
    else ResourceReference.parseC(value).flatMap {
      case reference: ResourceReference.Url if reference.scheme == "sm-workflow-work" =>
        WorkflowRetainedWork.decodeResourceC(value).map(_ => reference)
      case reference: ResourceReference.Url if reference.scheme == "sm-workflow-snapshot" =>
        WorkflowRetainedSnapshot.decodeResourceC(value).map(_ => reference)
      case reference: ResourceReference.Url if reference.scheme == "file" &&
          reference.uri.getRawAuthority == null && reference.uri.getRawQuery == null &&
          reference.uri.getRawFragment == null && Option(reference.uri.getPath).exists(_.startsWith("/")) =>
        Consequence.success(reference)
      case _ => _invalid("requires an absolute local file URI without authority, query or fragment")
    }

  def selectC[A: ValueReader](request: Request): Consequence[Source[A]] =
    selectC[A](request, "input", "inputResource")

  def selectC[A: ValueReader](request: Request, inlineName: String, resourceName: String): Consequence[Source[A]] = {
    val record = Option(request).map(_.toRecord)
    (record.flatMap(_.getAny(inlineName)), record.flatMap(_.getAny(resourceName))) match {
      case (Some(_), None) => NativeWorkflowOperationInput.valueC[A](request, inlineName).map(Inline(_))
      case (None, Some(value: String)) => referenceC(value).map(Resource(_))
      case _ => _invalid("requires exactly one of " + inlineName + " or " + resourceName)
    }
  }
  def readC[A: ValueReader](source: Source[A], readText: ResourceReference => Consequence[String]): Consequence[A] =
    readC[A](source, readText, "input")

  def readC[A: ValueReader](source: Source[A], readText: ResourceReference => Consequence[String], valueName: String): Consequence[A] =
    source match {
      case Inline(value: org.goldenport.record.Record) =>
        if (!DevelopmentOperationalView.uniqueFields(value)) _invalid("inline artifact contains duplicate fields")
        else value.toJsonStringC.flatMap { text =>
          if (!text.contains("retainedSnapshot")) summon[ValueReader[A]].readC(value)
          else WorkflowRetainedSnapshot.expandTextC(text, readText)
            .flatMap(NativeWorkflowOperationInput.decodeValueC[A](_, valueName))
        }
      case Inline(value) => Consequence.success(value)
      case Resource(reference) => readText(reference).flatMap(NativeWorkflowOperationInput.decodeValueC[A](_, valueName))
    }
  private def _invalid[A](message: String): Consequence[A] = Consequence.stateConflict("Workflow Resource input " + message)
}
