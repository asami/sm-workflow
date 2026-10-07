package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.schema.{Multiplicity, ValueDomain, XString}
import org.goldenport.value.BaseContent
import org.goldenport.protocol.spec as spec
import org.goldenport.protocol.{Argument, Property, Request}

/** Both canonical transports carry the same versioned JSON command.
  * CLI uses a positional argument; MCP uses the schema's named property.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object WorkflowJsonCommandRequest {
  def commandArguments(request: Request): List[Argument] = {
    if (request == null || request.arguments == null || request.properties == null ||
        request.switches == null || request.switches.nonEmpty ||
        request.arguments.exists(_ == null) || request.properties.exists(_ == null)) return Nil
    val format = request.properties.filter(_.name == "textus.format")
    if (format.nonEmpty && (format.size != 1 || format.head.value != "json")) return Nil
    val commands = request.properties.filterNot(_.name == "textus.format")
    (request.arguments, commands) match {
      case (List(argument @ Argument("arg1", value: String, _)), Nil) if value != null => List(argument)
      case (Nil, List(Property("arg1", value: String, _))) if value != null => List(Argument("arg1", value))
      case _ => Nil
    }
  }

  val definition: spec.RequestDefinition = spec.RequestDefinition(
    parameters = List(spec.ParameterDefinition(
      content = BaseContent.simple("arg1"),
      kind = spec.ParameterDefinition.Kind.Argument,
      domain = ValueDomain(XString, Multiplicity.One)
    ))
  )
}
