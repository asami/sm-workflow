package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.schema.{Multiplicity, ValueDomain, XString}
import org.goldenport.Consequence
import org.goldenport.value.BaseContent
import org.goldenport.protocol.spec as spec
import org.goldenport.protocol.{Argument, Property, Request}

/** Current JSON input carriers preserve absence separately from invalid input.
  * Unused Request attributes are not business input or execution authority.
  * @since   Sep. 28, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object WorkflowJsonCommandRequest {
  def inputC(request: Request): Consequence[Option[String]] =
    if (request == null) Consequence.stateConflict("Workflow input request is absent")
    else {
      val arguments = Option(request.arguments).toList.flatten.collect {
        case Argument("arg1", value, _) => value
      }
      val properties = Option(request.properties).toList.flatten.collect {
        case Property("arg1", value, _) => value
      }
      (arguments ++ properties) match {
        case Nil => Consequence.success(None)
        case List(value: String) => Consequence.success(Some(value))
        case List(_) => Consequence.stateConflict("Workflow arg1 must be a JSON string")
        case _ => Consequence.stateConflict("Workflow JSON input is ambiguous")
      }
    }

  val definition: spec.RequestDefinition = _definition(Multiplicity.One)
  val optionalDefinition: spec.RequestDefinition = _definition(Multiplicity.ZeroOne)

  private def _definition(multiplicity: Multiplicity): spec.RequestDefinition = spec.RequestDefinition(
    parameters = List(spec.ParameterDefinition(
      content = BaseContent.simple("arg1"),
      kind = spec.ParameterDefinition.Kind.Argument,
      domain = ValueDomain(XString, multiplicity)
    ))
  )
}
