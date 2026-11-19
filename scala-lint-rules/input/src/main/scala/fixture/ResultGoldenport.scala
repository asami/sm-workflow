/*
rules = [GoldenportNoDiscardedFailureResult]
*/
package fixture

import org.goldenport.Consequence
import org.goldenport.cncf.Program
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import cats.free.Free

/** Intentional compiler-qualified positive and negative rule examples.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
object ResultGoldenport {
  def result: Consequence[Int] = Consequence.success(1)
  def program: Program[UnitOfWorkOp, Int] = Free.pure[UnitOfWorkOp, Int](1)
  def discarded: Unit = {
    result // assert: GoldenportNoDiscardedFailureResult
    program // assert: GoldenportNoDiscardedFailureResult
    ()
  }
  def callback(values: List[Int]): Unit = values.foreach { _ =>
    result // assert: GoldenportNoDiscardedFailureResult
  }
  def collectedResults(values: List[Int]): List[Consequence[Int]] = values.map { _ => result }
  def collectedPrograms(values: List[Int]): List[Program[UnitOfWorkOp, Int]] = values.map { _ => program }
  val stored: Consequence[Int] = result
  def propagated: Program[UnitOfWorkOp, Int] = program
  def composed: Program[UnitOfWorkOp, Int] = program.map(_ + 1)
  def unrelatedFree: Free[Option, Int] = Free.pure[Option, Int](1)
  def ordinaryFree: Unit = { unrelatedFree; () }
}
