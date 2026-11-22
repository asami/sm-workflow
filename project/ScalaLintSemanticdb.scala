package org.simplemodeling.scalalint

import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.meta.internal.semanticdb._
import scala.util.control.NonFatal

/** Copies only compiler-proven sibling definitions into a disposable Scalafix input. */
object ScalaLintSemanticdb {
  private final case class Input(source: Path, sourceHash: String, path: Path, bytes: Array[Byte], metadataHash: String, documents: TextDocuments, document: TextDocument)

  private def digest(name: String, bytes: Array[Byte]): String =
    MessageDigest.getInstance(name).digest(bytes).map(b => f"${b & 0xff}%02x").mkString

  private def global(symbol: String): Boolean =
    symbol.nonEmpty && !symbol.startsWith("local") && !symbol.contains(";") &&
      !symbol.exists(c => Character.isISOControl(c) || Character.isWhitespace(c))

  private def simple(tpe: Type): Boolean = tpe match {
    case ref: TypeRef =>
      val validSymbol = global(ref.symbol) && ref.symbol.endsWith("#") && !ref.symbol.contains("[") && !ref.symbol.contains("(")
      // Only admit applied constructors whose arity is known here. Unknown
      // constructors cannot establish full application without class records.
      val appliedArity = Map("scala/Option#" -> 1, "scala/collection/immutable/Vector#" -> 1)
      validSymbol && ref.prefix == Type.Empty &&
        (if (ref.typeArguments.isEmpty) !appliedArity.contains(ref.symbol)
         else appliedArity.get(ref.symbol).contains(ref.typeArguments.size) && ref.typeArguments.forall(simple))
    case _ => false
  }

  private def safe(info: SymbolInformation): Boolean = {
    val eligible = info.kind == SymbolInformation.Kind.FIELD || info.kind == SymbolInformation.Kind.METHOD
    eligible && global(info.symbol) && (info.signature match {
      case value: ValueSignature => simple(value.tpe)
      case method: MethodSignature => method.typeParameters.forall(s => s.symlinks.isEmpty && s.hardlinks.isEmpty) &&
        method.parameterLists.forall(s => s.symlinks.isEmpty && s.hardlinks.isEmpty) && simple(method.returnType)
      case _ => false
    })
  }

  /** None means ordinary Scalafix must run with its unmodified metadata. */
  def prepare(sourceRoot: Path, metadataRoot: Path, sources: Seq[Path], selected: Seq[Path], target: Path): Option[Path] = {
    try {
      val root = sourceRoot.toRealPath()
      val meta = metadataRoot.toRealPath()
      val declared = sources.filter(_.toString.endsWith(".scala")).map(_.toRealPath()).distinct
      val wanted = selected.map(_.toRealPath()).toSet
      if (declared.size > 100000 || !wanted.subsetOf(declared.toSet)) return None
      val inputs = declared.map { source =>
        if (!source.startsWith(root)) return None
        val relative = root.relativize(source).toString.replace(java.io.File.separatorChar, '/')
        if (relative.isEmpty || relative.split('/').exists(p => p == ".." || p.isEmpty)) return None
        val path = meta.resolve("META-INF/semanticdb").resolve(relative + ".semanticdb").normalize()
        if (!path.startsWith(meta) || !Files.isRegularFile(path) || !path.toRealPath().startsWith(meta)) return None
        val bytes = Files.readAllBytes(path)
        val docs = TextDocuments.parseFrom(bytes)
        if (docs.documents.size != 1) return None
        val doc = docs.documents.head
        val content = Files.readAllBytes(source)
        if (doc.uri != relative || !doc.md5.equalsIgnoreCase(digest("MD5", content))) return None
        source -> Input(source, digest("SHA-256", content), path, bytes, digest("SHA-256", bytes), docs, doc)
      }.toMap
      val definitions = inputs.values.toVector.flatMap { input =>
        val declared = input.document.occurrences.filter(_.role == SymbolOccurrence.Role.DEFINITION).map(_.symbol).toSet
        input.document.symbols.filter(info => declared(info.symbol))
      }.groupBy(_.symbol)
      val unique = definitions.collect { case (symbol, Seq(info)) if safe(info) => symbol -> info }
      val changes = inputs.toVector.filter { case (source, _) => wanted(source) }.flatMap { case (_, input) =>
        val existing = input.document.symbols.map(_.symbol).toSet
        val additions = input.document.occurrences.iterator.map(_.symbol).filter(global).toSet
          .diff(existing).toVector.sorted.flatMap(unique.get)
        if (additions.isEmpty) None else Some(input -> input.document.withSymbols(input.document.symbols ++ additions))
      }
      if (changes.isEmpty) None
      else {
        if (!inputs.values.forall(i => digest("SHA-256", Files.readAllBytes(i.source)) == i.sourceHash &&
            digest("SHA-256", Files.readAllBytes(i.path)) == i.metadataHash)) return None
        Files.createDirectories(target)
        val out = Files.createTempDirectory(target, "semanticdb-")
        try {
          // The target root must contain all selected documents, including those without additions.
          val rewritten = changes.map { case (i, d) => i.path -> d }.toMap
          inputs.foreach { case (source, input) =>
            if (wanted(source)) {
              val destination = out.resolve(meta.relativize(input.path)).normalize()
              if (!destination.startsWith(out)) throw new IllegalArgumentException("unsafe metadata path")
              Files.createDirectories(destination.getParent)
              val bytes = rewritten.get(input.path).map(d => input.documents.withDocuments(Seq(d)).toByteArray).getOrElse(input.bytes)
              Files.write(destination, bytes)
            }
          }
          Some(out)
        } catch {
          case NonFatal(error) => delete(out); throw error
        }
      }
    } catch { case NonFatal(_) => None }
  }

  def delete(root: Path): Unit = {
    if (Files.exists(root)) {
      val paths = Files.walk(root)
      try {
        import scala.collection.JavaConverters._
        paths.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists(_))
      } finally paths.close()
    }
  }

  def argument(root: Path): String = {
    val path = root.toAbsolutePath.toString
    require(!path.exists(c => Character.isISOControl(c) || c == '"' || c == '\\'), "unsafe Scalafix target path")
    " --check --no-cache \"--semanticdb-targetroots=" + path + "\""
  }
}
