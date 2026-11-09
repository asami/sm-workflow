package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, Printer}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import LocalPublicationSourceApplicability as A
import GoalPhasePublishedDependencySourceAdoption as P
import GoalPhaseOrderedProgression as O
import GoalPhaseDependencySourceRevision as R
import RepositorySyncManagedGitObservation as G
import SplitPhaseExplicitPartitionFixture.take

/** All values are specification-only native-shaped data, never live authority. */
private[admission] object CommittedNoninputApplicabilitySpecificationFixture {
  val path = "docs/artifact-note.md"
  def fact(previous: LocalPublication.Result, current: LocalPublication.Source,
    root: String = "/specification/producer", branch: String = "main",
    destination: String = "file:///specification/artifact", artifact: ContextReference = ContextReference("artifact", "1")): A.Fact = {
    val program = previous.process.flatMap(WorkflowSbtCapture.decode(_).toOption).get.safeProgramIdentity
    A.Fact(A.schema, previous.request, current, root, branch,
      A.Declaration(A.declarationSchema, "producer-artifact-inputs", "1", previous.request.target.identity,
        destination, artifact, Vector(path)), Json.obj("identity" -> program.asJson,
        "target" -> previous.request.target.workspace.asJson, "operation" -> "publish-local".asJson),
      Vector(O.HistoryCommit(current.head, current.tree, Vector(previous.request.source.head)),
        O.HistoryCommit(previous.request.source.head, previous.request.source.tree, Vector.empty)),
      Vector(R.StageChange("100644", "100644", Some("c" * 40), Some("d" * 40), "M", path)))
  }
  val canonical = CanonicalDependencySourceSpecificationFixture.publication
  val previous = canonical.result.publication.copy(request = canonical.result.publication.request.copy(
    source = LocalPublication.Source("a" * 40, "b" * 40, Vector.empty),
    input = canonical.result.publication.request.input.copy(ownerSessionId = None)))
  val applicability = fact(previous, canonical.result.publication.request.source,
    CanonicalDependencySourceSpecificationFixture.binding.root, CanonicalDependencySourceSpecificationFixture.binding.branch,
    canonical.destination, canonical.artifact)
  val observation = canonical.copy(result = P.CollectionResult(previous.status, previous.reason, previous, Some(applicability)))
}

/** The runtime Git/UoW boundary is covered by the separately owned installed episode.
  * @since   Nov.  9, 2026
  * @version Nov.  9, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class LocalPublicationSourceApplicabilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CommittedNoninputApplicabilitySpecificationFixture as F
  private def _oid(value: String): GitObjectId = take(GitObjectId.parseC(value))
  private val _native_source = G.Observed(F.applicability.branch, F.applicability.current.head,
    F.applicability.current.tree, Vector.empty, None, None)
  /** Specification-only typed data. This seam has no execution or authority. */
  private class NativeObservation extends A.Observation {
    var before = _native_source
    var after = _native_source
    var rootBefore = F.applicability.root
    var rootAfter = F.applicability.root
    var indexBefore = _oid(_native_source.tree)
    var indexAfter = _oid(_native_source.tree)
    var metadata = F.applicability.lineage.map(c => c.identity ->
      GitCommitMetadata(_oid(c.identity), _oid(c.tree), c.parents.map(_oid))).toMap
    var delta = F.applicability.changes.map(c => GitTreeChange(c.oldMode, c.newMode,
      c.oldBlob.map(_oid), c.newBlob.map(_oid), c.status, take(GitPath.parseC(c.path))))
    var blobs = delta.flatMap(c => c.oldBlob.toVector ++ c.newBlob.toVector).distinct.map(id =>
      id.value -> GitBlobContent(Vector('x'.toByte), Some("x"))).toMap
    var failChanges = false
    var failBlob = false
    var calls = Vector.empty[String]
    private var _observe_calls = 0
    private var _root_calls = 0
    private var _index_calls = 0
    def observeC: Consequence[G.Observed] = {
      calls :+= "status"
      _observe_calls += 1
      Consequence.success(if (_observe_calls == 1) before else after)
    }
    def rootC: Consequence[String] = {
      calls :+= "root"
      _root_calls += 1
      Consequence.success(if (_root_calls == 1) rootBefore else rootAfter)
    }
    def indexTreeC: Consequence[GitObjectId] = {
      calls :+= "index"
      _index_calls += 1
      Consequence.success(if (_index_calls == 1) indexBefore else indexAfter)
    }
    def metadataC(commit: GitObjectId): Consequence[GitCommitMetadata] = {
      calls :+= "metadata:" + commit.value
      metadata.get(commit.value).map(Consequence.success).getOrElse(
        Consequence.stateConflict("SPECIFICATION-ONLY incomplete native metadata"))
    }
    def changesC(before: GitObjectId, after: GitObjectId): Consequence[Vector[GitTreeChange]] = {
      calls :+= "changes:" + before.value + ":" + after.value
      if (failChanges) Consequence.stateConflict("SPECIFICATION-ONLY incomplete native TreeChanges")
      else Consequence.success(delta)
    }
    def blobC(blob: GitObjectId): Consequence[GitBlobContent] = {
      calls :+= "blob:" + blob.value
      if (failBlob) Consequence.stateConflict("SPECIFICATION-ONLY incomplete native BlobContent")
      else blobs.get(blob.value).map(Consequence.success).getOrElse(
        Consequence.stateConflict("SPECIFICATION-ONLY absent native BlobContent"))
    }
  }
  private def _assess(observation: NativeObservation): Consequence[Option[A.Fact]] = A.assessObservedC(
    observation, F.previous, F.applicability.current, F.applicability.declaration, F.applicability.program,
    F.applicability.root, Some(F.applicability.branch))

  "Native applicability assessment" should {
    "return the exact fact after complete native lineage, delta, regular text blobs and repeated observations" in {
      Given("specification-only exact native commit/tree metadata, complete declared delta and clean raw workspace")
      val observation = new NativeObservation
      When("the same assessor body used by the typed Git/UoW adapter consumes those observations")
      val result = take(_assess(observation))
      Then("it returns the exact separate applicability fact after the unchanged native read sequence")
      result shouldBe Some(F.applicability)
      observation.calls shouldBe Vector("root", "status", "index",
        "metadata:" + F.applicability.current.head, "metadata:" + F.previous.request.source.head,
        "changes:" + F.previous.request.source.tree + ":" + F.applicability.current.tree,
        "blob:" + ("c" * 40), "blob:" + ("d" * 40), "status", "root", "index")
    }
    "refuse raw dirtiness even when the supplied current source is clean" in {
      Given("a clean supplied Source and raw workspace dirtiness hidden from that caller projection")
      val observation = new NativeObservation
      observation.before = _native_source.copy(dirty = Vector(G.DirtyPath(' ', 'M',
        "src/main/catalog/car/producer.yaml", None)))
      observation.after = observation.before
      When("native assessment reads actual status independently of the clean supplied source")
      val result = take(_assess(observation))
      Then("it returns no reusable fact before reading ancestry or publication inputs")
      F.applicability.current.clean shouldBe true
      result shouldBe None
      observation.calls shouldBe Vector("root", "status", "index", "status", "root", "index")
    }
    "refuse stable index, root, branch and selected native source crossings" in {
      Given("independent native workspace observations crossing one exact supplied source coordinate")
      val index = new NativeObservation
      index.indexBefore = _oid("e" * 40)
      index.indexAfter = index.indexBefore
      val root = new NativeObservation
      root.rootBefore = "/specification/crossed"
      root.rootAfter = root.rootBefore
      val branch = new NativeObservation
      branch.before = _native_source.copy(branch = "crossed")
      branch.after = branch.before
      val head = new NativeObservation
      head.before = _native_source.copy(head = "e" * 40)
      head.after = head.before
      val tree = new NativeObservation
      tree.before = _native_source.copy(tree = "e" * 40)
      tree.after = tree.before
      tree.indexBefore = _oid(tree.before.tree)
      tree.indexAfter = tree.indexBefore
      val observations = Vector(index, root, branch, head, tree)
      When("the assessor compares each stable native state with the exact selected clean source")
      val results = observations.map(o => take(_assess(o)))
      Then("each crossing is ordinary inapplicability and none reads a candidate delta")
      all(results) shouldBe None
      all(observations.map(_.calls.exists(_.startsWith("changes:")))) shouldBe false
    }
    "return no fact after exhausting a genuine unrelated native ancestry path" in {
      Given("a clean current commit whose complete native ancestry ends at an unrelated root commit")
      val observation = new NativeObservation
      val unrelated = _oid("e" * 40)
      val current = observation.metadata(F.applicability.current.head)
      observation.metadata = Map(current.commit.value -> current.copy(parents = Vector(unrelated)),
        unrelated.value -> GitCommitMetadata(unrelated, _oid("f" * 40), Vector.empty))
      When("the assessor walks all reachable native parent metadata without finding the actual publication commit")
      val result = take(_assess(observation))
      Then("nonancestry is ordinary inapplicability and no delta or blob is reused")
      result shouldBe None
      observation.calls should contain("metadata:" + unrelated.value)
      observation.calls.exists(_.startsWith("changes:")) shouldBe false
      observation.calls.takeRight(3) shouldBe Vector("status", "root", "index")
    }
    "propagate metadata identity or original/current tree contradictions as typed failures" in {
      Given("independent native metadata responses contradicting a requested commit or one selected exact tree")
      val identity = new NativeObservation
      val current = identity.metadata(F.applicability.current.head)
      identity.metadata = identity.metadata.updated(current.commit.value, current.copy(commit = _oid("e" * 40)))
      val currenttree = new NativeObservation
      currenttree.metadata = currenttree.metadata.updated(current.commit.value, current.copy(tree = _oid("e" * 40)))
      val oldtree = new NativeObservation
      val original = oldtree.metadata(F.previous.request.source.head)
      oldtree.metadata = oldtree.metadata.updated(original.commit.value, original.copy(tree = _oid("e" * 40)))
      val incomplete = new NativeObservation
      incomplete.metadata = incomplete.metadata.removed(F.previous.request.source.head)
      val observations = Vector(identity, currenttree, oldtree, incomplete)
      When("the assessor consumes those native lineage responses")
      val results = observations.map(_assess)
      Then("every contradiction or incomplete read propagates failure instead of returning a reusable fact")
      all(results.map(_.isSuccess)) shouldBe false
      all(observations.map(_.calls.exists(_.startsWith("changes:")))) shouldBe false
    }
    "refuse undeclared, input, rename, mode and special entries in the complete native delta" in {
      Given("a complete native delta with one independently crossed entry")
      val base = new NativeObservation
      val change = base.delta.head
      val changes = Vector(change.copy(path = take(GitPath.parseC("docs/undeclared.md"))),
        change.copy(path = take(GitPath.parseC("src/main/Input.scala"))),
        change.copy(status = "R100"), change.copy(newMode = "100755"),
        change.copy(newMode = "120000"))
      val observations = changes.map { changed =>
        val observation = new NativeObservation
        observation.delta = Vector(changed)
        observation
      }
      When("the assessor qualifies the complete native TreeChanges result")
      val results = observations.map(o => take(_assess(o)))
      Then("none of these complete but inapplicable deltas can reuse the publication")
      all(results) shouldBe None
      all(observations.map(_.calls.exists(_.startsWith("changes:")))) shouldBe true
      all(observations.map(_.calls.exists(_.startsWith("blob:")))) shouldBe false
    }
    "refuse NUL, binary, unavailable, non-UTF8 and inconsistent native blob contents" in {
      Given("complete lineage and a regular declared delta with one unqualified native blob")
      val contents = Vector(GitBlobContent(Vector(0.toByte), Some("\u0000")),
        GitBlobContent(Vector(0.toByte), None), GitBlobContent(Vector.empty, None),
        GitBlobContent(Vector(0xc3.toByte, 0x28.toByte), None),
        GitBlobContent(Vector('x'.toByte), Some("different")))
      val observations = contents.map { content =>
        val observation = new NativeObservation
        observation.blobs = observation.blobs.updated("c" * 40, content)
        observation
      }
      When("the assessor reads actual native BlobContent for both sides of the complete delta")
      val results = observations.map(o => take(_assess(o)))
      Then("unavailable or inconsistent text remains ordinary inapplicability")
      all(results) shouldBe None
      all(observations.map(_.calls.contains("blob:" + ("c" * 40)))) shouldBe true
      all(observations.map(_.calls.takeRight(3))) shouldBe Vector("status", "root", "index")
    }
    "propagate incomplete TreeChanges and BlobContent failures without a fallback" in {
      Given("an otherwise exact native observation whose delta or blob read reports typed incompleteness")
      val changes = new NativeObservation
      changes.failChanges = true
      val blob = new NativeObservation
      blob.failBlob = true
      When("the shared assessor reaches each failed typed observation")
      val changed = _assess(changes)
      val content = _assess(blob)
      Then("both failures propagate and no result can qualify as applicable")
      changed.isSuccess shouldBe false
      content.isSuccess shouldBe false
      changes.calls.last shouldBe "changes:" + F.previous.request.source.tree + ":" + F.applicability.current.tree
      blob.calls.last shouldBe "blob:" + ("c" * 40)
    }
    "propagate changes between initial and final raw workspace, root or index observations" in {
      Given("an initially complete qualifying native source that drifts during assessment")
      val status = new NativeObservation
      status.after = _native_source.copy(dirty = Vector(G.DirtyPath(' ', 'M', F.path, None)))
      val root = new NativeObservation
      root.rootAfter = "/specification/changed"
      val index = new NativeObservation
      index.indexAfter = _oid("e" * 40)
      val observations = Vector(status, root, index)
      When("the assessor repeats its final raw native observations after reading lineage and blobs")
      val results = observations.map(_assess)
      Then("each contradiction is a typed failure even though the earlier candidate fact qualified")
      all(results.map(_.isSuccess)) shouldBe false
      all(observations.map(_.calls.contains("blob:" + ("d" * 40)))) shouldBe true
      all(observations.map(_.calls.takeRight(3))) shouldBe Vector("status", "root", "index")
    }
  }
  "Producer artifact declaration" should {
    "accept exact non-input file declarations and reject source, build and control exclusions" in {
      Given("one versioned producer declaration of an exact documentation file")
      val declaration = F.applicability.declaration
      val forbidden = Vector("src/main/Foo.scala", "project/plugin.sbt", "build.sbt", "version.sbt", "project.yaml",
        ".cozy/config", ".textus/config", ".git/config", ".gitignore", ".jvmopts", ".sbtopts", "docs/*", "docs/")
      When("each path is proposed as the producer's non-input declaration")
      val invalid = forbidden.map(p => declaration.copy(nonInputPaths = Vector(p)))
      Then("only exact admitted regular-file paths can qualify")
      A.declarationValid(declaration) shouldBe true
      all(invalid.map(A.declarationValid)) shouldBe false
      A.declarationValid(declaration.copy(nonInputPaths = Vector(F.path, F.path))) shouldBe false
      A.declarationValid(declaration.copy(revision = "0")) shouldBe false
      A.declarationValid(declaration.copy(destination = "relative")) shouldBe false
    }
  }
  "Retained publication source relation" should {
    "preserve the actual selected program environment through the public Record boundary" in {
      Given("a selected fixed program containing literal host map keys and explicit null configuration")
      val program = F.applicability.program.mapObject(_.add("environment", Json.obj(
        "PRODUCER_SOURCE_ROOT" -> F.applicability.root.asJson, "OPTIONAL_VALUE" -> Json.Null)))
      val fact = F.applicability.copy(program = program)
      When("the retained fact crosses the public Record normalization boundary")
      val normalized = SplitPhaseExplicitPartitionFixture.take(GoalPhasePrerequisitePublicationUnknownRecovery.jsonC(
        GoalPhaseNativeMetadata.record(fact.asJson)))
      Then("the original program JSON remains literal rather than losing map keys or null values")
      normalized.as[A.Fact] shouldBe Right(fact)
      normalized.as[A.Fact].toOption.get.program shouldBe program
    }
    "bind literal original provenance with an optional Session and round-trip the separate fact" in {
      Given("an actual successful-shaped publication with no owner Session and a clean declared descendant")
      val fact = F.applicability
      When("the shared relation and strict collection codec select that original publication")
      val decoded = F.observation.result.asJson.as[P.CollectionResult]
      Then("the old request remains literal while the separate fact identifies the new source")
      A.binds(fact, F.previous, fact.current) shouldBe true
      F.previous.request.input.ownerSessionId shouldBe None
      decoded shouldBe Right(F.observation.result)
      fact.asJson.as[A.Fact] shouldBe Right(fact)
      F.observation.result.publication shouldBe F.previous
      P.observationValid(F.observation) shouldBe true
    }
    "refuse crossed participants, Session owners, targets, programs, artifacts, destinations and source guards" in {
      Given("one complete source applicability and its original publication")
      val fact = F.applicability
      val request = F.previous.request
      When("each provenance identity or current source is crossed independently")
      val crossed = Vector(request.copy(participant = "other"), request.copy(input = request.input.copy(ownerSessionId = Some("other"))),
        request.copy(target = request.target.copy(identity = "other")), request.copy(target = request.target.copy(component = "other")),
        request.copy(target = request.target.copy(workspace = "other")))
      val observations = Vector(F.observation.copy(destination = "file:///other"),
        F.observation.copy(artifact = ContextReference("other", "1")),
        F.observation.copy(publisherProcessPolicy = F.observation.publisherProcessPolicy.replace(
          "specification-dependency-publication", "other-program")))
      Then("the shared matcher refuses every crossed original or selected observation")
      all(crossed.map(r => A.binds(fact, F.previous.copy(request = r), fact.current))) shouldBe false
      all(observations.map(o => A.matches(fact, o, fact.current, fact.root, fact.branch, request.target.component))) shouldBe false
      A.matches(fact, F.observation, fact.current, "/other", fact.branch, request.target.component) shouldBe false
      A.matches(fact, F.observation, fact.current, fact.root, "other", request.target.component) shouldBe false
      A.binds(fact, F.previous, fact.current.copy(tree = "e" * 40)) shouldBe false
    }
    "refuse undeclared changes, incomplete lineage, ambiguous mode and rename or special entries" in {
      Given("a declared complete documentation modification and native ancestry path")
      val fact = F.applicability
      val change = fact.changes.head
      When("one required lineage or complete regular-file delta condition is removed")
      val invalid = Vector(fact.copy(changes = Vector(change.copy(path = "docs/undeclared.md"))),
        fact.copy(changes = Vector(change.copy(path = "unknown.file"))),
        fact.copy(changes = Vector(change, change)), fact.copy(lineage = fact.lineage.take(1)),
        fact.copy(lineage = fact.lineage.updated(0, fact.lineage.head.copy(parents = Vector.empty))),
        fact.copy(changes = Vector(change.copy(status = "R100"))),
        fact.copy(changes = Vector(change.copy(newMode = "100755"))),
        fact.copy(changes = Vector(change.copy(newMode = "120000"))),
        fact.copy(changes = Vector(change.copy(oldBlob = None))),
        fact.copy(current = fact.current.copy(dirty = Vector(LocalPublication.Change(F.path, " ", "M")))))
      Then("no malformed or partial relation can authorize artifact reuse")
      all(invalid.map(A.valid)) shouldBe false
      all(invalid.map(_.asJson.as[A.Fact].isLeft)) shouldBe true
    }
    "refuse failed, unknown, nonstarted and truncated publication evidence" in {
      Given("a complete relation bound to an actual successful-shaped original")
      val fact = F.applicability
      val process = F.previous.process.get
      When("the original terminal status or process completeness is changed")
      val invalid = Vector("failed", "uncertain", "not-started", "prepared", "unnecessary").map(s => F.previous.copy(status = s)) :+
        F.previous.copy(process = Some(process.mapObject(_.add("stdout", process.hcursor.downField("stdout").focus.get
          .mapObject(_.add("truncated", Json.True))))))
      Then("only actual complete Published provenance can be reused")
      all(invalid.map(p => A.binds(fact, p, fact.current))) shouldBe false
    }
    "preserve old result JSON and reject null applicability or applicability on Published" in {
      Given("a literal old successful result and a separate valid applicability fact")
      val old = F.previous.asJson
      When("the optional field is absent, null, or attached to an actual publication")
      val absent = old.as[LocalPublication.Result]
      val nulled = old.mapObject(_.add("applicability", Json.Null)).as[LocalPublication.Result]
      val crossed = old.mapObject(_.add("applicability", F.applicability.asJson)).as[LocalPublication.Result]
      Then("absence round-trips literally while new crossed fields fail closed")
      old.hcursor.downField("applicability").focus shouldBe None
      absent.map(_.asJson) shouldBe Right(old)
      nulled.isLeft shouldBe true
      crossed.isLeft shouldBe true
      F.observation.result.asJson.mapObject(_.add("applicability", Json.Null)).as[P.CollectionResult].isLeft shouldBe true
    }
  }
}
