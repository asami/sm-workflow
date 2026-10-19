package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject}
import NativeHostEvidence.Purpose

/** Actual independent effect targets, not copies or native-state certificates.
  * @since   Oct.  8, 2026
  *  version Oct. 11, 2026
  * @version Oct. 18, 2026
  */
private[smworkflow] object NativeHostAuthorityScope {
  def supports(purpose: Purpose): Boolean = Set(Purpose.RepositoryEffect, Purpose.StepClosure, Purpose.PhaseClosure)(purpose)
  def select(purpose: Purpose, binding: Json): Either[String, Json] =
    if (!supports(purpose)) Right(binding)
    else for {
      root <- obj(binding)
      owner <- text(root, "componentInstance")
      input <- at(root, "scope")
      scope <- purpose match {
        case Purpose.RepositoryEffect => for {
          fields <- strings(input, Vector("selection", "invocation", "participant", "repository", "endpointIdentity", "mode", "effect"))
          target <- at(input, "target").flatMap(strings(_, Vector("root", "localBranch", "remote", "remoteBranch", "endpoint")))
        } yield Json.fromJsonObject(fields.add("kind", Json.fromString("repository-effect")).add("target", Json.fromJsonObject(target)))
        case Purpose.StepClosure => for {
          fields <- strings(input, Vector("instance", "sourceRevision", "plan", "planRevision", "step", "reviewEvidence"))
          generation <- input("generation").flatMap(_.asNumber).flatMap(_.toLong).toRight("Step generation must be an integer")
          parent <- at(input, "parent").flatMap(snapshot)
          candidate <- at(input, "candidate").flatMap(snapshot)
        } yield Json.fromJsonObject(fields.add("kind", Json.fromString("step-closure")).add("generation", Json.fromLong(generation))
          .add("parent", parent).add("candidate", candidate))
        case Purpose.PhaseClosure => for {
          fields <- strings(input, Vector("instance", "sourceRevision", "review", "childInvocation"))
          plan <- at(input, "plan").flatMap(strings(_, Vector("identity", "revision", "phase", "repository")))
          base <- at(input, "base").flatMap(snapshot)
          candidate <- at(input, "candidate").flatMap(snapshot)
          reviewed <- input("reviewedCandidate") match {
            case None => Right(Option.empty[Json])
            case Some(value) => obj(value).flatMap(snapshot).map(Some(_))
          }
          completed <- input("completed").flatMap(_.asArray).toRight("Completed Step targets must be an array")
          chain <- completed.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty))((prior, value) => for {
            values <- prior
            entry <- obj(value)
            step <- text(entry, "step")
            commit <- at(entry, "commit").flatMap(strings(_, Vector("identity", "parent", "tree")))
          } yield values :+ Json.obj("step" -> Json.fromString(step), "commit" -> Json.fromJsonObject(commit)))
        } yield {
          val selected = fields.add("kind", Json.fromString("phase-closure")).add("plan", Json.fromJsonObject(plan))
            .add("base", base).add("candidate", candidate).add("completed", Json.fromValues(chain))
          Json.fromJsonObject(reviewed.map(value => selected.add("reviewedCandidate", value)).getOrElse(selected))
        }
        case _ => Left("Host authority purpose is unsupported")
      }
    } yield Json.obj("componentInstance" -> Json.fromString(owner), "scope" -> scope)

  private def snapshot(input: JsonObject): Either[String, Json] =
    strings(input, Vector("head", "tree")).map(Json.fromJsonObject)
  private def strings(input: JsonObject, keys: Vector[String]): Either[String, JsonObject] =
    keys.foldLeft[Either[String, JsonObject]](Right(JsonObject.empty))((prior, key) => for {
      fields <- prior
      value <- text(input, key)
    } yield fields.add(key, Json.fromString(value)))
  private def obj(value: Json): Either[String, JsonObject] =
    Option(value).flatMap(_.asObject).toRight("Host authority scope must be an object")
  private def at(input: JsonObject, key: String): Either[String, JsonObject] =
    input(key).toRight("Host authority requires " + key).flatMap(obj)
  private def text(input: JsonObject, key: String): Either[String, String] =
    input(key).flatMap(_.asString).toRight("Host authority requires String " + key)
}
