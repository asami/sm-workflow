package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject}
import NativeHostEvidence.Purpose

/** Selected effect targets, not immutable copies of a command or whole Plan.
  * Legacy extraction does not issue evidence or grant a new effect. Review and
  * Human attestations have separate semantic-target contracts, not these grants.
  * @since   Oct.  8, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object NativeHostAuthorityScope {
  def supports(purpose: Purpose): Boolean = Set(Purpose.RepositoryEffect, Purpose.StepClosure, Purpose.PhaseClosure)(purpose)

  def select(purpose: Purpose, binding: Json, legacy: Boolean): Either[String, Json] =
    if (!supports(purpose)) Right(binding)
    else for {
      root <- obj(binding)
      owner <- text(root, "componentInstance")
      current = root("scope").nonEmpty
      _ <- Either.cond(current || legacy, (), "Host authority requires selected scope, not a copied body")
      _ <- shape(root, Set("componentInstance", if (current) "scope" else "value"), strict = true)
      input <- root(if (current) "scope" else "value").toRight("Host authority scope is absent").flatMap(obj)
      scope <- purpose match {
        case Purpose.RepositoryEffect => repository(input, current)
        case Purpose.StepClosure => step(input, current)
        case Purpose.PhaseClosure => phase(input, current)
        case _ => Left("Host authority purpose is unsupported")
      }
    } yield Json.obj("componentInstance" -> Json.fromString(owner), "scope" -> scope)

  private def repository(input: JsonObject, current: Boolean): Either[String, Json] = for {
    _ <- shape(input, Set("kind", "selection", "invocation", "participant", "repository", "endpointIdentity", "mode", "target", "effect"), current)
    _ <- kind(input, "repository-effect", current)
    command <- if (current) Right(input) else input("originalCommand").toRight("Original effect selection is absent").flatMap(obj)
    selected <- if (current) Right(input) else command("input").toRight("Original effect target is absent").flatMap(obj)
    selection <- text(command, if (current) "selection" else "selectionIdentity")
    invocation <- text(command, if (current) "invocation" else "idempotencyKey")
    participant <- text(command, if (current) "participant" else "participantIdentity")
    repository <- text(selected, if (current) "repository" else "repositoryIdentity")
    endpointIdentity <- text(selected, if (current) "endpointIdentity" else "remoteEndpointIdentity")
    mode <- text(selected, if (current) "mode" else "syncMode")
    target <- input("target").toRight("Effect target is absent").flatMap(obj)
    _ <- shape(target, Set("root", "localBranch", "remote", "remoteBranch", "endpoint"), current)
    targetFields <- strings(target, Vector("root", "localBranch", "remote", "remoteBranch", "endpoint"))
    // A legacy command and its grant target must not contradict actual scope.
    // Policy metadata is deliberately not read or required here.
    _ <- if (current) Right(()) else for {
      local <- text(selected, "localBranch")
      remote <- text(selected, "trackingRemote")
      branch <- text(selected, "trackingBranch")
      _ <- Either.cond(targetFields("localBranch").contains(Json.fromString(local)) &&
        targetFields("remote").contains(Json.fromString(remote)) && targetFields("remoteBranch").contains(Json.fromString(branch)),
        (), "Original command contradicts actual effect target")
    } yield ()
    effect <- text(input, "effect")
    _ <- Either.cond(Set("CommitPending", "Push", "FinalFetch")(effect), (), "Host effect is unsupported")
  } yield Json.obj("kind" -> Json.fromString("repository-effect"), "selection" -> Json.fromString(selection),
    "invocation" -> Json.fromString(invocation), "participant" -> Json.fromString(participant),
    "repository" -> Json.fromString(repository), "endpointIdentity" -> Json.fromString(endpointIdentity),
    "mode" -> Json.fromString(mode), "target" -> Json.fromJsonObject(targetFields),
    "effect" -> Json.fromString(effect))

  private def step(input: JsonObject, current: Boolean): Either[String, Json] = for {
    _ <- shape(input, Set("kind", "instance", "sourceRevision", "plan", "planRevision", "step", "generation", "parent", "candidate", "reviewEvidence"), current)
    _ <- kind(input, "step-closure", current)
    fields <- strings(input, Vector("instance", "sourceRevision", "plan", "planRevision", "step", "reviewEvidence"))
    generation <- input("generation").flatMap(_.asNumber).flatMap(_.toLong).filter(_ >= 0).toRight("Step generation is absent or invalid")
    parent <- snapshot(input, "parent", current)
    candidate <- snapshot(input, "candidate", current)
  } yield Json.fromJsonObject(fields.add("kind", Json.fromString("step-closure")).add("generation", Json.fromLong(generation))
    .add("parent", parent).add("candidate", candidate))

  private def phase(input: JsonObject, current: Boolean): Either[String, Json] = for {
    _ <- shape(input, Set("kind", "instance", "sourceRevision", "plan", "base", "completed", "candidate", "review", "childInvocation"), current)
    _ <- kind(input, "phase-closure", current)
    fields <- strings(input, Vector("instance", "sourceRevision", "review", "childInvocation"))
    plan <- input("plan").toRight("Selected Phase Plan is absent").flatMap(obj)
    _ <- shape(plan, Set("identity", "revision", "phase", "repository"), current)
    planFields <- strings(plan, Vector("identity", "revision", "phase", "repository"))
    base <- snapshot(input, "base", current)
    candidate <- snapshot(input, "candidate", current)
    completed <- input("completed").flatMap(_.asArray).filter(_.size <= 64).toRight("Native completed chain is absent or oversized")
    chain <- completed.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty)) { (prior, value) => for {
      values <- prior
      entry <- obj(value)
      _ <- shape(entry, Set("step", "commit"), current)
      step <- text(entry, "step")
      commit <- entry("commit").toRight("Native commit target is absent").flatMap(obj)
      _ <- shape(commit, Set("identity", "parent", "tree"), current)
      facts <- oids(commit, Vector("identity", "parent", "tree"))
    } yield values :+ Json.obj("step" -> Json.fromString(step), "commit" -> Json.fromJsonObject(facts)) }
  } yield Json.fromJsonObject(fields.add("kind", Json.fromString("phase-closure")).add("plan", Json.fromJsonObject(planFields))
    .add("base", base).add("candidate", candidate).add("completed", Json.fromValues(chain)))

  private def snapshot(input: JsonObject, key: String, strict: Boolean): Either[String, Json] = for {
    value <- input(key).toRight("Native snapshot target is absent").flatMap(obj)
    _ <- shape(value, Set("head", "tree"), strict)
    fields <- oids(value, Vector("head", "tree"))
  } yield Json.fromJsonObject(fields)
  private def oids(input: JsonObject, keys: Vector[String]): Either[String, JsonObject] =
    strings(input, keys).flatMap(fields => Either.cond(keys.forall(key => fields(key).flatMap(_.asString)
      .exists(_.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})"))), fields, "Native Git target is invalid"))
  private def strings(input: JsonObject, keys: Vector[String]): Either[String, JsonObject] =
    keys.foldLeft[Either[String, JsonObject]](Right(JsonObject.empty)) { (prior, key) => for {
      fields <- prior
      value <- text(input, key)
    } yield fields.add(key, Json.fromString(value)) }
  private def obj(value: Json): Either[String, JsonObject] = Option(value).flatMap(_.asObject).toRight("Host authority scope must be an object")
  private def text(input: JsonObject, key: String): Either[String, String] = input(key).flatMap(_.asString)
    .filter(value => value.nonEmpty && value.trim == value && !value.exists(_.isControl)).toRight("Host authority requires actual " + key)
  private def shape(input: JsonObject, keys: Set[String], strict: Boolean): Either[String, Unit] =
    Either.cond(!strict || input.keys.toSet == keys, (), "Host authority scope has undeclared or missing fields")
  private def kind(input: JsonObject, expected: String, current: Boolean): Either[String, Unit] =
    Either.cond(!current || input("kind").flatMap(_.asString).contains(expected), (), "Host authority belongs to another effect purpose")
}
