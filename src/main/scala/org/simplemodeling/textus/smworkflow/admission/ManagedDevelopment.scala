package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence

/** A dependency edge selects source, JAR and project independently. Publications
  * and selections are immutable Component records, never a work.d JSON ledger.
  * @since   Nov. 12, 2026
  * @version Nov. 21, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object ManagedDevelopment {
  final case class Coordinate(organization: String, module: String, version: String)
  object Coordinate {
    given io.circe.Encoder[Coordinate] = io.circe.generic.semiauto.deriveEncoder
    given io.circe.Decoder[Coordinate] = io.circe.generic.semiauto.deriveDecoder
  }
  final case class ProjectModule(project: String, requested: Coordinate)
  object ProjectModule {
    given io.circe.Encoder[ProjectModule] = io.circe.generic.semiauto.deriveEncoder
    given io.circe.Decoder[ProjectModule] = io.circe.generic.semiauto.deriveDecoder
  }
  final case class Generator(cozyVersion: String, modelerVersion: String, modules: Vector[Coordinate])
  object Generator {
    given io.circe.Encoder[Generator] = io.circe.generic.semiauto.deriveEncoder
    given io.circe.Decoder[Generator] = io.circe.generic.semiauto.deriveDecoder
  }
  final case class Batch(modules: Vector[ProjectModule], consumerModules: Vector[Coordinate], generator: Option[Generator])
  object Batch {
    given io.circe.Encoder[Batch] = io.circe.generic.semiauto.deriveEncoder
    given io.circe.Decoder[Batch] = io.circe.generic.semiauto.deriveDecoder
  }
  final case class Member(requested: Coordinate, selected: Coordinate, jar: String, ivy: String, completion: Json)
  final case class PublishedBatch(members: Vector[Member], completion: Json, artifacts: Json)
  final case class Edge(identity: String, provider: GoalPhaseDependencyScope.Component, consumer: GoalPhaseDependencyScope.Component,
    localRepository: String, localJar: Boolean, localProject: Option[String], requested: Coordinate, batch: Option[Batch] = None)
  object Edge {
    given io.circe.Encoder[Edge] = io.circe.generic.semiauto.deriveEncoder[Edge].mapJson(_.mapObject(_.filter {
      case ("batch", value) => !value.isNull
      case _ => true
    }))
    given io.circe.Decoder[Edge] = io.circe.generic.semiauto.deriveDecoder
  }
  final case class Definition(scope: String, ownerWorkspace: String, repository: String,
    staging: String, edges: Vector[Edge])
  final case class Input(edgeIdentity: String, requestIdentity: String, publication: String, selection: String,
    providerPhase: Option[ProviderPhaseDelivery.Locator] = None,
    synchronization: Option[DevelopmentDeliverySynchronization.Selection] = None,
    publicationEdge: Option[String] = None,
    retain: Option[Boolean] = None)
  object Input {
    given io.circe.Encoder[Input] = io.circe.generic.semiauto.deriveEncoder[Input].mapJson(_.mapObject(_.filter {
      case ("providerPhase" | "synchronization" | "publicationEdge" | "retain", value) => !value.isNull
      case _ => true
    }))
    given io.circe.Decoder[Input] = io.circe.generic.semiauto.deriveDecoder
  }
  final case class Publication(edge: Edge, scope: String, identity: String, producerSelection: String,
    selected: Coordinate, jar: String, ivy: String, source: Json, report: Json, completion: Json, batch: Option[PublishedBatch] = None)
  object Publication {
    given io.circe.Encoder[Publication] = io.circe.generic.semiauto.deriveEncoder[Publication].mapJson(_.mapObject(_.filter {
      case ("batch", value) => !value.isNull
      case _ => true
    }))
    given io.circe.Decoder[Publication] = io.circe.generic.semiauto.deriveDecoder
  }
  final case class Selection(identity: String, publication: Publication)
  final case class Result(status: String, detail: Json)
  final case class Execution(status: String, detail: Json)

  /** Only native admission of a new publication supplies this bounded inventory.
    * Historical publications are never upgraded from their DTO paths alone. */
  final case class OwnedInventory(paths: Vector[String], dependencies: Vector[Coordinate])
  final case class PublicationIdentity(edge: String, publication: String)
  final case class ProtectedSelection(selection: String, publication: PublicationIdentity)
  final case class ProtectedUse(publication: PublicationIdentity, closure: Vector[PublicationIdentity], unknownClosure: Boolean)
  final case class DeleteObservation(path: String, outcome: String)
  final case class PublicationProtection(identity: PublicationIdentity, coordinates: Vector[Coordinate],
    state: String, inventory: Option[OwnedInventory], effects: Vector[DeleteObservation],
    pruneRequest: Option[String] = None)
  final case class Protection(revision: Long, owner: String, scope: String, repository: String,
    publications: Vector[PublicationProtection], active: Map[String, ProtectedSelection],
    retained: Map[String, ProtectedSelection], uses: Map[String, ProtectedUse])

  trait Port {
    def loadC(key: String): Consequence[Option[Json]]
    def createC(key: String, value: Json): Consequence[Unit]
    def sourceC(edge: Edge): Consequence[Json]
    def consumerSourceC(edge: Edge): Consequence[Json] = sourceC(edge)
    def executeC(edge: Edge, input: Input, selected: Option[Publication]): Consequence[Execution]
    def publicationC(edge: Edge, input: Input, source: Json): Consequence[Publication]
    def consumerReportC(edge: Edge, input: Input, selected: Publication): Consequence[Json]
    def detachC(edge: Edge, input: Input): Consequence[Execution] = invalid("ordinary consumer execution is unavailable")
    def inventoryC(publication: Publication): Consequence[Option[OwnedInventory]] = Consequence.success(None)
    def deleteC(path: String): Consequence[String] = invalid("owned FileDelete is unavailable")
    def participant: String
  }

  def id(value: String): Boolean = value != null && value != "." && value != ".." && value.matches("[A-Za-z0-9_.-]{1,128}")
  private def coordinate(value: Coordinate): Boolean = value != null &&
    Vector(value.organization, value.module, value.version).forall(id)
  def validateC(d: Definition): Consequence[Unit] =
    if (d != null && id(d.scope) && workspace(d.ownerWorkspace) && relative(d.repository) && relative(d.staging) &&
      d.repository != d.staging && !d.repository.startsWith(d.staging + "/") && !d.staging.startsWith(d.repository + "/") &&
      d.edges != null && d.edges.nonEmpty && d.edges.map(_.identity).distinct.size == d.edges.size &&
      d.edges.forall(e => e != null && id(e.identity) && component(e.provider) && component(e.consumer) &&
        e.provider.workspace != e.consumer.workspace && e.localRepository == d.ownerWorkspace &&
        e.localProject != null && e.localProject.forall(workspace) && coordinate(e.requested) && validBatch(e))) Consequence.unit
    else invalid("explicit scope, independent dependency bindings or resource membership is invalid")


  private def uniqueModules(values: Vector[Coordinate]): Boolean = values != null && values.forall(coordinate) &&
    values.map(c => c.organization -> c.module).distinct.size == values.size
  private def validBatch(e: Edge): Boolean = e.batch != null && e.batch.forall { b =>
    b != null && b.modules != null && b.modules.nonEmpty && b.modules.size <= 64 &&
      b.modules.forall(m => m != null && id(m.project)) && b.modules.map(_.project).distinct.size == b.modules.size &&
      uniqueModules(b.modules.map(_.requested)) && uniqueModules(b.consumerModules) && b.consumerModules.contains(e.requested) &&
      b.consumerModules.forall(b.modules.map(_.requested).contains) && b.generator != null && b.generator.forall { g =>
        g != null && id(g.cozyVersion) && id(g.modelerVersion) && uniqueModules(g.modules) &&
          g.modules.contains(Coordinate("org.simplemodeling", "cozy_2.12", g.cozyVersion)) &&
          g.modules.contains(Coordinate("org.simplemodeling", "simplemodeler_2.12", g.modelerVersion)) &&
          g.modules.forall(b.modules.map(_.requested).contains)
      }
  }

  def edgeC(d: Definition, input: Input): Consequence[Edge] = for {
    _ <- validateC(d)
    _ <- if (input != null && Vector(input.edgeIdentity, input.requestIdentity, input.publication, input.selection).forall(id) &&
      input.retain != null && input.publicationEdge != null && input.publicationEdge.forall(id) && input.providerPhase != null && input.providerPhase.forall(ProviderPhaseDelivery.valid) && input.synchronization != null &&
      input.synchronization.forall(DevelopmentDeliverySynchronization.valid))
      Consequence.unit else invalid("invalid explicit operation identities")
    edge <- d.edges.find(_.identity == input.edgeIdentity).map(Consequence.success)
      .getOrElse(invalid("dependency edge is not declared"))
    _ <- if (edge.localJar) Consequence.unit else invalid("dependency edge has no local JAR binding")
  } yield edge

  def version(original: String, scope: String, publication: String): String =
    original + "-smdev-" + scope.length + "-" + scope + "-" + publication.length + "-" + publication
  def base(d: Definition, e: Edge, publication: String): String = d.repository + "/" +
    e.requested.organization + "/" + e.requested.module + "/" + version(e.requested.version, d.scope, publication)
  def key(d: Definition, e: Edge, kind: String, identity: String): String =
    "managed-development:" + d.scope + ":" + e.identity + ":" + kind + ":" + identity
  private def component(value: GoalPhaseDependencyScope.Component): Boolean = value != null &&
    id(value.identity) && id(value.repository) && workspace(value.workspace)
  private def workspace(value: String): Boolean = org.goldenport.cncf.workspace.WorkspaceId.parseC(value).isSuccess
  private def relative(value: String): Boolean = org.goldenport.cncf.workspace.WorkspaceRelativePath.parseC(value).isSuccess
  private def decodeC[A: io.circe.Decoder](value: Json): Consequence[A] =
    value.as[A].fold(e => invalid(e.message), Consequence.success)
  private def samePublisher(a: Edge, b: Edge): Boolean = a.provider == b.provider &&
    a.localRepository == b.localRepository && a.requested == b.requested && a.batch == b.batch
  private def publicationEdgeC(d: Definition, e: Edge, input: Input): Consequence[Edge] =
    d.edges.find(_.identity == input.publicationEdge.getOrElse(e.identity)).filter(samePublisher(_, e))
      .map(Consequence.success).getOrElse(invalid("publication edge is not an explicitly declared matching producer/batch binding"))

  private[admission] def selectionC(d: Definition, e: Edge, input: Input, p: Port): Consequence[Selection] = for {
    row <- p.loadC(key(d, e, "selection", input.selection))
    s <- row.map(decodeC[Selection]).getOrElse(invalid("selection is unavailable; ordinary fallback is forbidden"))
    _ <- if (s.identity == input.selection && samePublisher(s.publication.edge, e) && s.publication.edge.identity == input.publicationEdge.getOrElse(e.identity) && s.publication.scope == d.scope &&
      s.publication.identity == input.publication && publicationValid(d, e, s.publication)) Consequence.unit else invalid("selection crosses its dependency edge or publication")
  } yield s

  private def publicationValid(d: Definition, e: Edge, p: Publication): Boolean =
    p.batch != null && p.selected == e.requested.copy(version = version(e.requested.version, d.scope, p.identity)) &&
      (e.batch match {
        case None => p.batch.isEmpty
        case Some(expected) => p.batch.exists { actual =>
          actual.members != null && actual.members.forall(_ != null) && actual.members.map(_.requested).toSet == expected.modules.map(_.requested).toSet &&
            actual.members.size == expected.modules.size && actual.members.forall(m => m != null &&
              m.selected == m.requested.copy(version = version(m.requested.version, d.scope, p.identity)) &&
              m.jar != null && m.ivy != null && m.jar.nonEmpty && m.ivy.nonEmpty) &&
            actual.members.exists(m => m.requested == e.requested && m.selected == p.selected && m.jar == p.jar && m.ivy == p.ivy)
        }
      })

  private def identity(p: Publication): PublicationIdentity = PublicationIdentity(p.edge.identity, p.identity)
  private def selectedCoordinates(d: Definition, e: Edge, publication: String): Vector[Coordinate] =
    e.batch.map(_.modules.map(_.requested)).getOrElse(Vector(e.requested)).map(c =>
      c.copy(version = version(c.version, d.scope, publication)))
  private def protectionKey(d: Definition, revision: Long): String =
    "managed-development-protection:" + d.ownerWorkspace + ":" + d.scope + ":" + revision
  private def emptyProtection(d: Definition): Protection =
    Protection(0, d.ownerWorkspace, d.scope, d.repository, Vector.empty, Map.empty, Map.empty, Map.empty)
  private def validRef(value: PublicationIdentity): Boolean = value != null && id(value.edge) && id(value.publication)
  private def validProtectedSelection(value: ProtectedSelection): Boolean =
    value != null && id(value.selection) && validRef(value.publication)
  private def validProtection(d: Definition, state: Protection): Boolean =
    state != null && state.owner == d.ownerWorkspace && state.scope == d.scope && state.repository == d.repository &&
      state.publications != null && state.active != null && state.retained != null && state.uses != null &&
      state.publications.forall(v => v != null && validRef(v.identity) && v.coordinates != null &&
        v.coordinates.nonEmpty && v.coordinates.forall(coordinate) && v.inventory != null &&
        v.inventory.forall(i => i != null && i.paths != null && i.paths.nonEmpty && i.paths.size <= 8192 &&
          i.paths.distinct.size == i.paths.size && i.paths.forall(path => relative(path) && path.startsWith(d.repository + "/")) &&
          i.dependencies != null && i.dependencies.size <= 4096 && i.dependencies.forall(coordinate)) &&
        v.effects != null && v.effects.forall(effect => effect != null && relative(effect.path) &&
          Set("Deleted", "Absent", "Conflict", "Failed", "Unknown").contains(effect.outcome)) &&
        Set("publishing", "published", "legacy", "pruning", "prune-incomplete", "pruned").contains(v.state) &&
        v.pruneRequest != null && v.pruneRequest.forall(id)) &&
      state.publications.map(_.identity).distinct.size == state.publications.size &&
      state.publications.flatMap(_.coordinates).distinct.size == state.publications.flatMap(_.coordinates).size &&
      state.active.values.forall(validProtectedSelection) && state.retained.values.forall(validProtectedSelection) &&
      state.uses.values.forall(v => v != null && validRef(v.publication) && v.closure != null && v.closure.forall(validRef))

  /** Unique next-key Create keeps the sequence contiguous. Probe its frontier
    * in logarithmic reads with constant stack usage. A concurrent append only
    * makes the returned candidate stale; the subsequent Create arbitrates it. */
  private def protectionC(d: Definition, p: Port): Consequence[Protection] = {
    def probeC(revision: Long): Consequence[Option[Protection]] =
      p.loadC(protectionKey(d, revision)).flatMap {
        case None => Consequence.success(None)
        case Some(raw) => decodeC[Protection](raw).flatMap { state =>
          if (state != null && state.revision == revision && validProtection(d, state))
            Consequence.success(Some(state))
          else invalid("protection history is malformed or belongs to another owner/repository")
        }
      }
    var current = emptyProtection(d)
    var lower = 0L
    var upper = 1L
    var expanding = true
    while (expanding) {
      probeC(upper) match {
        case Consequence.Success(Some(state)) =>
          current = state
          lower = upper
          if (upper == Long.MaxValue) return Consequence.success(current)
          upper = if (upper > Long.MaxValue / 2) Long.MaxValue else upper * 2
        case Consequence.Success(None) => expanding = false
        case failed => return failed.map(_.getOrElse(current))
      }
    }
    while (upper - lower > 1) {
      val middle = lower + (upper - lower) / 2
      probeC(middle) match {
        case Consequence.Success(Some(state)) => current = state; lower = middle
        case Consequence.Success(None) => upper = middle
        case failed => return failed.map(_.getOrElse(current))
      }
    }
    Consequence.success(current)
  }
  private def changeProtectionC(d: Definition, p: Port)(change: Protection => Consequence[Protection]): Consequence[Protection] = {
    def appendC(remaining: Int): Consequence[Protection] = for {
      before <- protectionC(d, p)
      _ <- if (before.revision < Long.MaxValue) Consequence.unit else invalid("protection revision number is exhausted")
      proposed <- change(before)
      after = proposed.copy(revision = before.revision + 1)
      _ <- if (validProtection(d, after)) Consequence.unit else invalid("proposed protection revision is malformed; no effects admitted")
      key = protectionKey(d, after.revision)
      result <- {
        val created = p.createC(key, after.asJson)
        created match {
          case Consequence.Success(_) => p.loadC(key).flatMap { observed =>
            if (observed.contains(after.asJson)) Consequence.success(after)
            else invalid("protection Create has no exact readback; no effects admitted")
          }
          case _ => p.loadC(key).flatMap {
            case Some(raw) if raw != after.asJson && remaining > 0 => decodeC[Protection](raw).flatMap { winner =>
              if (winner != null && winner.revision == after.revision && validProtection(d, winner)) appendC(remaining - 1)
              else invalid("contending protection revision is malformed; no effects admitted")
            }
            // Equal bytes can be our own uncertain write. Absence proves no
            // winner. Neither observation permits re-running this transition.
            case _ => created.map(_ => after)
          }
        }
      }
    } yield result
    appendC(3)
  }

  private def availableC(state: Protection, ref: PublicationIdentity): Consequence[Unit] =
    state.publications.find(_.identity == ref) match {
      case None => Consequence.unit // Legacy use remains supported, never destructive admission.
      case Some(value) if Set("published", "legacy").contains(value.state) => Consequence.unit
      case _ => invalid("publication is incomplete, pruning, pruned or uncertain; use is unavailable")
    }
  private def closureC(state: Protection, roots: Vector[PublicationIdentity]): Consequence[Vector[PublicationIdentity]] = {
    def visit(todo: Vector[PublicationIdentity], seen: Vector[PublicationIdentity]): Consequence[Vector[PublicationIdentity]] =
      todo.headOption match {
        case None => Consequence.success(seen)
        case Some(ref) if seen.contains(ref) => visit(todo.tail, seen)
        case Some(ref) => for {
          value <- state.publications.find(_.identity == ref).filter(_.state == "published")
            .map(Consequence.success).getOrElse(invalid("unknown or unavailable protected dependency closure"))
          inventory <- value.inventory.map(Consequence.success).getOrElse(invalid("legacy dependency closure is unknown"))
          dependencies <- inventory.dependencies.filter(_.version.contains("-smdev-")).foldLeft(
            Consequence.success(Vector.empty[PublicationIdentity])) { (prior, coordinate) => for {
              found <- prior
              owners = state.publications.filter(_.coordinates.contains(coordinate))
              owner <- if (owners.size == 1) Consequence.success(owners.head.identity)
                else invalid("foreign or dangling development dependency; cleanup/use is unavailable")
            } yield found :+ owner }
          result <- visit(todo.tail ++ dependencies, seen :+ ref)
        } yield result
      }
    visit(roots, Vector.empty)
  }
  private def selectionKey(e: Edge, selection: String): String = e.identity + ":" + selection
  private def useKey(e: Edge, input: Input, kind: String): String = e.identity + ":" + kind + ":" + input.requestIdentity
  private def protectUseC(d: Definition, e: Edge, input: Input, pub: Publication, kind: String, p: Port): Consequence[Unit] =
    changeProtectionC(d, p) { state => for {
      _ <- availableC(state, identity(pub))
      known = state.publications.find(_.identity == identity(pub)).exists(_.inventory.nonEmpty)
      use <- if (known) closureC(state, Vector(identity(pub))).map(values => ProtectedUse(identity(pub), values, false))
        else Consequence.success(ProtectedUse(identity(pub), Vector(identity(pub)), true))
      key = useKey(e, input, kind)
      _ <- if (!state.uses.contains(key)) Consequence.unit else invalid("execution already owns an in-flight or uncertain use")
    } yield state.copy(uses = state.uses.updated(key, use)) }.map(_ => ())
  private def releaseUseC(d: Definition, e: Edge, input: Input, kind: String, actual: Execution, p: Port): Consequence[Unit] = {
    val termination = actual.detail.hcursor.get[String]("termination").toOption
    val stopped = actual.status == "admission-rejected" || termination.exists(t =>
      t.startsWith("Exited(") || Set("NotStarted", "LaunchFailed").contains(t))
    if (!stopped) Consequence.unit
    else changeProtectionC(d, p)(state => Consequence.success(state.copy(uses = state.uses - useKey(e, input, kind)))).map(_ => ())
  }

  private def selectProtectionC(d: Definition, e: Edge, input: Input, pub: Publication, p: Port): Consequence[Unit] =
    changeProtectionC(d, p) { state => for {
      _ <- availableC(state, identity(pub))
      _ <- if (state.publications.find(_.identity == identity(pub)).exists(_.inventory.nonEmpty))
        closureC(state, Vector(identity(pub))).map(_ => ()) else Consequence.unit
      selected = ProtectedSelection(input.selection, identity(pub))
      retained = input.retain match {
        case Some(true) => state.retained.updated(selectionKey(e, input.selection), selected)
        case Some(false) => state.retained - selectionKey(e, input.selection)
        case None => state.retained
      }
    } yield state.copy(active = state.active.updated(e.identity, selected), retained = retained) }.map(_ => ())

  private def reservePublicationC(d: Definition, e: Edge, input: Input, p: Port): Consequence[Unit] =
    changeProtectionC(d, p) { state =>
      val ref = PublicationIdentity(e.identity, input.publication)
      val coordinates = selectedCoordinates(d, e, input.publication)
      if (state.publications.exists(value => value.identity == ref || value.coordinates.exists(coordinates.contains)))
        invalid("publication coordinates are already reserved; replacement is forbidden")
      else Consequence.success(state.copy(publications = state.publications :+
        PublicationProtection(ref, coordinates, "publishing", None, Vector.empty),
        uses = state.uses.updated(useKey(e, input, "publish"), ProtectedUse(ref, Vector(ref), true))))
    }.map(_ => ())
  private def admitPublicationC(d: Definition, pub: Publication, inventory: Option[OwnedInventory], p: Port): Consequence[Unit] =
    changeProtectionC(d, p) { state => for {
      reserved <- state.publications.find(_.identity == identity(pub)).filter(_.state == "publishing")
        .map(Consequence.success).getOrElse(invalid("publication lacks its original protection reservation"))
      value = reserved.copy(state = if (inventory.nonEmpty) "published" else "legacy", inventory = inventory)
      after = state.copy(publications = state.publications.map(v => if (v.identity == value.identity) value else v))
      _ <- if (inventory.nonEmpty) closureC(after, Vector(value.identity)).map(_ => ()) else Consequence.unit
    } yield after }.map(_ => ())

  private def pruneC(d: Definition, e: Edge, input: Input, p: Port): Consequence[Result] =
    attemptC(d, e, input, "prune", p) {
      (for {
        sourceEdge <- publicationEdgeC(d, e, input)
        raw <- p.loadC(key(d, sourceEdge, "publication", input.publication))
        pub <- raw.map(decodeC[Publication]).getOrElse(invalid("prune requires an admitted immutable publication"))
        _ <- if (pub.edge == sourceEdge && pub.scope == d.scope && pub.identity == input.publication && publicationValid(d, e, pub)) Consequence.unit
          else invalid("prune publication crosses its configured owner/binding")
        live <- p.inventoryC(pub)
        state <- changeProtectionC(d, p) { state => for {
          target <- state.publications.find(_.identity == identity(pub)).filter(v => v.state == "published" && v.inventory.nonEmpty)
            .map(Consequence.success).getOrElse(invalid("publication has no new safe inventory, or is already unavailable"))
          _ <- if (live == target.inventory && live.exists(i => i.paths.nonEmpty && i.paths.distinct.size == i.paths.size))
            Consequence.unit else invalid("owned publication metadata/inventory changed; no deletion admitted")
          _ <- if (!state.uses.values.exists(_.unknownClosure)) Consequence.unit else invalid("an uncertain/legacy use has unknown dependency closure")
          protectedRefs <- closureC(state, (state.active.values ++ state.retained.values).map(_.publication).toVector ++
            state.uses.values.toVector.flatMap(_.closure))
          _ <- if (!protectedRefs.contains(target.identity)) Consequence.unit else invalid("publication is active, retained, in-use or in their dependency closure")
          // Even an unselected admitted dependent must remain usable. Unknown
          // publication metadata cannot silently become permission to delete.
          _ <- if (!state.publications.exists(v => v.identity != target.identity && v.state == "published" &&
            v.inventory.exists(_.dependencies.exists(target.coordinates.contains)))) Consequence.unit
            else invalid("publication is still required by another admitted publication")
          reserved = target.copy(state = "pruning", pruneRequest = Some(input.requestIdentity))
        } yield state.copy(publications = state.publications.map(v => if (v.identity == target.identity) reserved else v)) }
        target = state.publications.find(_.identity == identity(pub)).get
        result <- deleteFilesC(d, target, input, p)
      } yield result).recoverWith { error =>
        protectionC(d, p).map { state =>
          val target = state.publications.find(_.identity == PublicationIdentity(input.publicationEdge.getOrElse(e.identity), input.publication))
          val pending = target.exists(v => Set("pruning", "prune-incomplete").contains(v.state) && v.pruneRequest.contains(input.requestIdentity))
          Result(if (pending) "unknown" else "prune-unavailable", Json.obj(
            "reason" -> error.show.asJson, "protection" -> target.asJson))
        }.recoverWith(_ => Consequence.success(Result("unknown", Json.obj("reason" -> error.show.asJson))))
      }
    }

  private def deleteFilesC(d: Definition, target: PublicationProtection, input: Input, p: Port): Consequence[Result] = {
    def result(status: String, effects: Vector[DeleteObservation]): Result = Result(status, Json.obj(
      "publication" -> target.identity.asJson, "candidates" -> target.inventory.get.paths.asJson, "effects" -> effects.asJson))
    def step(paths: Vector[String], effects: Vector[DeleteObservation]): Consequence[Result] = paths.headOption match {
      case None => changeProtectionC(d, p)(state => Consequence.success(state.copy(publications = state.publications.map(v =>
        if (v.identity == target.identity) v.copy(state = "pruned", effects = effects) else v))))
        .map(_ => result("pruned", effects))
      case Some(path) => for {
        // Before each irreversible occurrence, a durable pending marker makes
        // interruption unavailable across restart. This occurrence is never replayed.
        _ <- changeProtectionC(d, p) { state =>
          val current = state.publications.find(_.identity == target.identity)
          if (!current.exists(v => v.state == "pruning" && v.pruneRequest.contains(input.requestIdentity) && v.effects == effects))
            invalid("prune progress changed; no effect admitted")
          else Consequence.success(state.copy(publications = state.publications.map(v => if (v.identity == target.identity)
            v.copy(effects = effects :+ DeleteObservation(path, "Unknown")) else v)))
        }
        outcome <- p.deleteC(path).recoverWith(_ => Consequence.success("Unknown"))
        observed = if (Set("Deleted", "Absent", "Conflict", "Failed").contains(outcome)) outcome else "Unknown"
        after = effects :+ DeleteObservation(path, observed)
        _ <- changeProtectionC(d, p)(state => Consequence.success(state.copy(publications = state.publications.map(v =>
          if (v.identity == target.identity) v.copy(effects = after, state = if (Set("Deleted", "Absent").contains(observed)) "pruning" else "prune-incomplete") else v))))
        next <- if (Set("Deleted", "Absent").contains(observed)) step(paths.tail, after)
          else Consequence.success(result(if (observed == "Unknown") "unknown" else "prune-incomplete", after))
      } yield next
    }
    step(target.inventory.get.paths, Vector.empty)
  }

  def runC(operation: String, d: Definition, input: Input, p: Port): Consequence[Result] = for {
    e <- edgeC(d, input)
    _ <- publicationEdgeC(d, e, input)
    _ <- if (operation != "publish" || input.publicationEdge.forall(_ == e.identity)) Consequence.unit
      else invalid("publication cannot redirect its producer edge")
    result <- operation match {
      case "publish" => publishC(d, e, input, p)
      case "select" => attemptC(d, e, input, "select", p) { for {
        sourceEdge <- publicationEdgeC(d, e, input)
        raw <- p.loadC(key(d, sourceEdge, "publication", input.publication))
        pub <- raw.map(decodeC[Publication]).getOrElse(invalid("only completed publications can be selected"))
        _ <- if (pub.edge == sourceEdge && pub.scope == d.scope && pub.identity == input.publication && publicationValid(d, e, pub))
          Consequence.unit else invalid("publication crosses its declared binding")
        s = Selection(input.selection, pub)
        before <- protectionC(d, p)
        _ <- availableC(before, identity(pub))
        // Freeze the identity before activation. Competing different values
        // must not supersede the active protection of a successful selection.
        _ <- immutableC(p, key(d, e, "selection", input.selection), s.asJson)
        _ <- selectProtectionC(d, e, input, pub, p)
      } yield Result("selected-unverified", s.asJson) }
      case "compile" | "verify" => for {
        selection <- selectionC(d, e, input, p)
        result <- attemptC(d, e, input, "compile", p) {
          protectUseC(d, e, input, selection.publication, "compile", p).flatMap { _ =>
          p.consumerSourceC(e).flatMap { before => p.executeC(e, input, Some(selection.publication)).flatMap { actual =>
            releaseUseC(d, e, input, "compile", actual, p).flatMap { _ =>
            if (actual.status != "succeeded") Consequence.success(Result(actual.status, actual.detail))
            else p.consumerSourceC(e).flatMap { after =>
              if (before != after) Consequence.success(Result("unverified", Json.obj(
                "execution" -> actual.detail, "reason" -> "consumer source changed during compilation".asJson)))
              else p.consumerReportC(e, input, selection.publication).transform(
              report => Consequence.success(Result("verified",
                Json.obj("selection" -> selection.asJson, "execution" -> actual.detail, "resolution" -> report,
                  "consumerSource" -> after))),
              error => Consequence.success(Result("unverified", Json.obj("execution" -> actual.detail, "reason" -> error.show.asJson))))
            }
          }}}}
        }
      } yield result
      case "detach" => for {
        selection <- selectionC(d, e, input, p)
        result <- attemptC(d, e, input, "detach", p) {
          for {
            _ <- protectUseC(d, e, input, selection.publication, "detach", p)
            actual <- p.detachC(e, input)
            _ <- releaseUseC(d, e, input, "detach", actual, p)
            _ <- if (actual.status == "succeeded") changeProtectionC(d, p) { state =>
              val expected = ProtectedSelection(input.selection, identity(selection.publication))
              Consequence.success(if (state.active.get(e.identity).contains(expected)) state.copy(active = state.active - e.identity) else state)
            }.map(_ => ()) else Consequence.unit
          } yield Result(
            if (actual.status == "succeeded") "detached" else actual.status,
            Json.obj("previousSelection" -> selection.asJson, "execution" -> actual.detail,
              "refresh" -> "fresh ordinary SBT invocation; no existing JVM was changed".asJson))
        }
      } yield result
      case "prune" => pruneC(d, e, input, p)
      case "status" => for {
        selection <- p.loadC(key(d, e, "selection", input.selection))
        sourceEdge <- publicationEdgeC(d, e, input)
        publication <- p.loadC(key(d, sourceEdge, "publication", input.publication))
        protection <- protectionC(d, p)
        lifetime = protection.publications.find(_.identity == PublicationIdentity(sourceEdge.identity, input.publication))
        pruned <- p.loadC(key(d, e, "prune-result", input.requestIdentity))
        pruneIntent <- p.loadC(key(d, e, "prune-intent", input.requestIdentity))
        detached <- p.loadC(key(d, e, "detach-result", input.requestIdentity))
        detachIntent <- p.loadC(key(d, e, "detach-intent", input.requestIdentity))
        compiled <- p.loadC(key(d, e, "compile-result", input.requestIdentity))
        selected <- p.loadC(key(d, e, "select-result", input.requestIdentity))
        selectIntent <- p.loadC(key(d, e, "select-intent", input.requestIdentity))
        published <- p.loadC(key(d, e, "publish-result", input.requestIdentity))
        compileIntent <- p.loadC(key(d, e, "compile-intent", input.requestIdentity))
        publishIntent <- p.loadC(key(d, e, "publish-intent", input.requestIdentity))
        intent = if (pruned.nonEmpty || pruneIntent.nonEmpty) pruneIntent else if (detached.nonEmpty || detachIntent.nonEmpty) detachIntent else if (compiled.nonEmpty || compileIntent.nonEmpty) compileIntent else if (selected.nonEmpty || selectIntent.nonEmpty) selectIntent else publishIntent
        _ <- if (intent.forall(_ == intentJson(input, p))) Consequence.unit
          else invalid("status request crosses its retained participant or immutable input")
        result <- pruned.orElse(detached).orElse(compiled).orElse(selected).orElse(published).map(decodeC[Result]).getOrElse(Consequence.success(Result(
          if (intent.nonEmpty) "unknown" else if (lifetime.exists(v => Set("pruning", "prune-incomplete", "pruned").contains(v.state))) lifetime.get.state
          else if (selection.nonEmpty) "selected-unverified"
          else if (publication.nonEmpty) "published" else "not-found",
          Json.obj("publication" -> publication.asJson, "selection" -> selection.asJson))))
      } yield result.copy(detail = result.detail.deepMerge(Json.obj("protection" -> Json.obj(
        "publication" -> lifetime.asJson, "active" -> protection.active.asJson, "retained" -> protection.retained.asJson,
        "uses" -> protection.uses.asJson, "revision" -> protection.revision.asJson))))
      case _ => invalid("operation is not implemented")
    }
  } yield result

  private def publishC(d: Definition, e: Edge, input: Input, p: Port): Consequence[Result] =
    attemptC(d, e, input, "publish", p) {
      for {
        previous <- p.loadC(key(d, e, "publication", input.publication))
        _ <- if (previous.isEmpty) Consequence.unit else invalid("immutable publication already exists; replacement is forbidden")
        _ <- reservePublicationC(d, e, input, p)
        source <- p.sourceC(e)
        actual <- p.executeC(e, input, None)
        _ <- releaseUseC(d, e, input, "publish", actual, p)
        result <- if (actual.status != "succeeded") Consequence.success(Result(actual.status, actual.detail))
          else for {
            after <- p.sourceC(e)
            // Native source evidence includes dirty-file bytes, not only Git path/status metadata.
            result <- if (source != after) Consequence.success(Result("unverified", Json.obj(
              "execution" -> actual.detail, "reason" -> "producer source changed during publication; artifact remains unselected".asJson)))
              else p.publicationC(e, input, source).transform(
                pub => (for {
                  inventory <- p.inventoryC(pub)
                  _ <- admitPublicationC(d, pub, inventory, p)
                  _ <- immutableC(p, key(d, e, "publication", input.publication), pub.asJson)
                } yield Result("published", Json.obj("publication" -> pub.asJson, "execution" -> actual.detail)))
                  .recoverWith(error => Consequence.success(Result("unverified", Json.obj("execution" -> actual.detail, "reason" -> error.show.asJson)))),
                error => Consequence.success(Result("unverified", Json.obj("execution" -> actual.detail, "reason" -> error.show.asJson))))
          } yield result
      } yield result
    }

  /** An interrupted effect is observable UNKNOWN, never an automatic rerun. */
  private def attemptC(d: Definition, e: Edge, input: Input, kind: String, p: Port)
    (execute: => Consequence[Result]): Consequence[Result] = for {
    previous <- p.loadC(key(d, e, kind + "-result", input.requestIdentity))
    result <- previous match {
      case Some(raw) => for {
        intent <- p.loadC(key(d, e, kind + "-intent", input.requestIdentity))
        _ <- if (intent.contains(intentJson(input, p))) Consequence.unit else invalid("request redelivery crosses its participant or immutable input")
        result <- decodeC[Result](raw)
      } yield result
      case None => for {
        intent <- p.loadC(key(d, e, kind + "-intent", input.requestIdentity))
        result <- intent match {
          case Some(raw) => if (raw == intentJson(input, p)) Consequence.success(Result("unknown", raw))
            else invalid("request identity is already owned by another input or participant")
          case None => for {
            _ <- p.createC(key(d, e, kind + "-intent", input.requestIdentity), intentJson(input, p))
            result <- execute
            _ <- p.createC(key(d, e, kind + "-result", input.requestIdentity), result.asJson)
          } yield result
        }
      } yield result
    }
  } yield result
  private def intentJson(input: Input, p: Port): Json =
    Json.obj("input" -> input.asJson, "participant" -> p.participant.asJson)
  private def immutableC(p: Port, key: String, value: Json): Consequence[Unit] = p.loadC(key).flatMap {
    case Some(old) if old == value => Consequence.unit
    case Some(_) => invalid("immutable publication or selection already has another value")
    case None => p.createC(key, value)
  }
  def invalid[A](detail: String): Consequence[A] = Consequence.stateConflict("Managed development: " + detail)
}
