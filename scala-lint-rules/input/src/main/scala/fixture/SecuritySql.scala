/*
rules = [NoUnsafeSqlConstruction]
*/
package fixture

/** Compiler-produced SemanticDB cases; fixture secrets are dummy data only.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object SecuritySql {
  def dynamic(connection: java.sql.Connection, input: String): java.sql.PreparedStatement = {
    val query = "select * from records where label = '" + input + "'"
    connection.prepareStatement(query) // assert: NoUnsafeSqlConstruction
  }
  def interpolated(statement: java.sql.Statement, input: String): Boolean =
    statement.execute(s"select * from records where label = '$input'") // assert: NoUnsafeSqlConstruction
  def bound(connection: java.sql.Connection, input: String): java.sql.PreparedStatement = {
    val statement = connection.prepareStatement("select * from records where label = ?")
    statement.setString(1, input)
    statement
  }
  def structure(connection: java.sql.Connection, archived: Boolean): java.sql.PreparedStatement = {
    val query = if (archived) "select id from archive" else "select id from records"
    connection.prepareStatement(query)
  }
  def unresolved(connection: java.sql.Connection, query: String): java.sql.PreparedStatement =
    connection.prepareStatement(query) // assert: NoUnsafeSqlConstruction.UnresolvedFlow
  final class Other { def execute(query: String): Unit = () }
  def unrelated(value: Other, data: String): Unit = value.execute("ordinary " + data)
  def intentional(statement: java.sql.Statement, data: String): Boolean =
    // reviewed fixture exception, SLP-A22
    statement.execute("select " + data) // scalafix:ok NoUnsafeSqlConstruction
}
