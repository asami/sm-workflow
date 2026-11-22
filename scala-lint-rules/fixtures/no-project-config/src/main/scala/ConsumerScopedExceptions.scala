package fixture

import java.util.Objects.nonNull
import java.util.Objects.{nonNull as importedNonNull}

/** Compiled qualification input; methods are never executed.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object ConsumerScopedExceptions {
  // REASON: this fixture models an Option precondition guaranteed by its caller.
  @SuppressWarnings(Array("scalafix:NoOptionGet"))
  def scoped(option: Option[Int], attempted: scala.util.Try[Int]): (Int, Int) = (option.get, attempted.get)
  def outside(option: Option[Int]): Int = option.get
  def failed(attempted: scala.util.Try[Int]): Int = attempted.get
  def total(option: Option[Int]): Int = option.getOrElse(0)
  // nullable-boundary: rule=NullableBoundarySemantics target=value boundary=interop normalization=reject-null justification=java-api
  def interop(value: String | Null): Int = if (value == null) throw new IllegalArgumentException() else value.length
  // nullable-boundary: rule=NullableBoundarySemantics target=value boundary=interop normalization=reject-null justification=java-api
  def interopPredicate(value: String | Null): Int = if (java.util.Objects.nonNull(value)) value.length else throw new IllegalArgumentException()
  // nullable-boundary: rule=NullableBoundarySemantics target=value boundary=interop normalization=reject-null justification=java-api
  def importedInterop(value: String | Null): Int = if (nonNull(value)) value.length else throw new IllegalArgumentException()
  def importedDirect(value: String): Boolean = nonNull(value)
  def renamedDirect(value: String): Boolean = importedNonNull(value)
  def unrelatedSameName(value: String): Boolean = {
    def nonNull(input: String): Boolean = input.nonEmpty
    nonNull(value)
  }
  private val selectedValue: String = "value"
  private def selectedCall(): String = selectedValue
  val selectedInitializer: Boolean = this.selectedValue != null
  def selectedField: Boolean = this.selectedValue != null
  def selectedCallResult: Boolean = selectedCall() != null
  def selectedReverse: Boolean = null == this.selectedValue
  def selectedCompound: Boolean = this.selectedValue != null && selectedCall().nonEmpty
  def selectedPredicate: Boolean = java.util.Objects.nonNull(this.selectedValue)
  def selectedCallPredicate: Boolean = java.util.Objects.nonNull(selectedCall())
  def selectedNested: Boolean = {
    def inner: Boolean = selectedCall() != null
    inner
  }
  def direct(value: String): Boolean = value != null
  def directPredicate(value: String): Boolean = java.util.Objects.nonNull(value)
  def compound(value: String): Boolean = if (value != null && value.nonEmpty) true else false
  def compoundPredicate(value: String): Boolean = value.nonEmpty && java.util.Objects.nonNull(value)
  def initialized(value: String): Boolean = { val present = value != null; present }
  def internal(value: String): Boolean = if (value != null) true else false
  def internalPredicate(value: String): Boolean = if (java.util.Objects.nonNull(value)) true else false
  // nullable-boundary: rule=NullableBoundarySemantics target=value boundary=interop normalization=reject-null justification=misplaced

  def misplaced(value: String | Null): Int = if (value == null) throw new IllegalArgumentException() else value.length
  // nullable-boundary: rule=NullableBoundarySemantics target=value boundary=interop normalization=reject-null justification=old-contract
  def stale(value: String | Null): Int = 0
  // nullable-boundary: rule=NullableBoundarySemantics target=value boundary=unknown normalization=reject-null
  def malformed(value: String | Null): Int = if (value == null) throw new IllegalArgumentException() else value.length
  @SuppressWarnings(Array("scalafix:NullableBoundarySemantics"))
  def suppressionOnly(value: String): Boolean = if (value != null) true else false
  def configuredVariable(value: Int): Int = {
    var current = value
    current += 1
    current
  }
  private def _projectHelper(value: Int): Int = value
}

final class ConsumerConfiguredFinalizer {
  override def finalize(): Unit = ()
}
