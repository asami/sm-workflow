package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.protocol.spec as spec
import org.goldenport.value.BaseContent

/** Descriptions come only from the actual generated CML declaration. This is
  * not a replacement metadata source or a capability/handler installation.
  * Runtime JSON decoding, responses and bound operation objects stay native.
  * @since   Oct.  7, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object NativeWorkflowDescriptionMetadata {
  def operation(declared: spec.OperationDefinition): spec.OperationDefinition.Specification =
    operation(declared.specification, spec.ResponseDefinition.void)

  /** The caller supplies its existing response contract. Registration and
    * Human/recovery operations already expose generated typed responses;
    * attaching descriptions must not replace them with the Start/advance void.
    */
  def operation(declared: spec.OperationDefinition.Specification,
    response: spec.ResponseDefinition): spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      content = BaseContent.simple(declared.name, declared.content.descriptiveAttributes),
      request = WorkflowJsonCommandRequest.definition,
      response = response
    )

  def service(declared: spec.ServiceDefinition,
    operations: spec.OperationDefinitionGroup): spec.ServiceDefinition =
    spec.ServiceDefinition.Instance(spec.ServiceDefinition.Specification(
      content = BaseContent.simple(declared.name, declared.specification.content.descriptiveAttributes),
      operations = operations
    ))
}
