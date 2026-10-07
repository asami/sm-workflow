import sbt._

/** Source distribution only: no installation, execution or manifest authority. */
object SkillSourceArchive {
  def packageSources(sources: Seq[(File, String)], archive: File): File = {
    IO.zip(sources.sortBy(_._2), archive, Some(0L))
    archive
  }
}
