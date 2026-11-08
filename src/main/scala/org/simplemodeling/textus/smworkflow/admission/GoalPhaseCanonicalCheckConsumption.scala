package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseDependencySourceRevision as R
import GoalPhaseNativeValidation as V
import GoalPhaseOrderedProgression as O

/** Pure compatibility for fixed later-check routes of an already selected
  * canonical source. Plain Basis values authenticate no native history.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  */
private[smworkflow] object GoalPhaseCanonicalCheckConsumption {
  final case class Basis(plan: O.Plan, adoptedStep: String, base: R.Consumption,
    previousRoot: String, capturedPolicy: String, primaryRoot: String)

  def basisC(direction: R.Direction): Consequence[Basis] = {
    if (!R.directionValid(direction) || direction.target.canonicalOrigin.isEmpty)
      GoalPhaseNativeRun.invalid("Canonical check consumption requires its actual canonical Direction")
    else direction.target.proposal.consumption match {
      case base: R.Consumption =>
        val target = direction.target
        val origin = target.canonicalOrigin.get
        val previous = origin.previousDirection.orElse(origin.originalDirection).map(_.target.binding)
          .getOrElse(target.predecessor.binding)
        Consequence.success(Basis(target.plan, target.step, base, previous.root, origin.processPolicy, origin.primaryRoot))
      case _ => GoalPhaseNativeRun.invalid("Canonical check consumption requires the captured v2 consumption")
    }
  }
  private def _check(valid: Boolean, clause: String): Either[String, Unit] =
    Either.cond(valid, (), "Canonical check consumption refuses " + clause)
  private def _result[A](value: Either[String, A]): Consequence[A] = value.fold(GoalPhaseNativeRun.invalid, Consequence.success)
  private def _json(value: String): Either[String, Json] = Option(value).toRight("Canonical check policy is absent")
    .flatMap(io.circe.parser.parse(_).left.map(_.message)).flatMap(value =>
      Either.cond(value.isObject, value, "Canonical check policy must be an object"))
  private def _array(value: Json, key: String): Either[String, Vector[Json]] =
    value.hcursor.get[Vector[Json]](key).left.map(_.message)
  private def _same_core(left: R.ConsumptionRecord, right: R.ConsumptionRecord): Boolean =
    left != null && right != null && left.primaryWorkspace == right.primaryWorkspace && left.sourceRoot == right.sourceRoot &&
      left.nonInputExclusions == right.nonInputExclusions && left.expectedModes == right.expectedModes
  private def _basis(value: Basis): Either[String, Unit] = _check(value != null && O.validatePlanC(value.plan).isSuccess &&
    R.consumptionValid(value.base) && value.plan.steps.exists(_.identity == value.adoptedStep), "captured basis")
  private def _allowed(basis: Basis): Set[String] =
    (basis.plan.steps.drop(basis.plan.steps.indexWhere(_.identity == basis.adoptedStep) + 1).flatMap(_.checks) ++
      basis.plan.phaseChecks).toSet -- basis.base.programs.map(_.check)
  private def _workspace(ordinal: Int): String = "sm-workflow-repository-validation-" + ordinal
  private def _ordinal(route: R.CheckProgram): Option[Int] = {
    val prefix = "sm-workflow-repository-validation-"
    Option(route.workspace).filter(_.startsWith(prefix)).flatMap(_.stripPrefix(prefix).toIntOption)
      .filter(n => n > 0 && _workspace(n) == route.workspace)
  }
  private def _program(policy: Json, route: R.CheckProgram): Either[String, Json] = for {
    plan <- policy.hcursor.get[Json]("processPlan").left.map(_.message)
    programs <- _array(plan, "sbtPrograms")
    selected = programs.filter(p => p.hcursor.get[String]("target").contains(route.workspace) &&
      p.hcursor.get[String]("operation").contains(route.kind))
    _ <- _check(selected.size == 1 && selected.head.hcursor.get[String]("identity").contains(route.program) &&
      programs.count(_.hcursor.get[String]("identity").contains(route.program)) == 1, "fixed program identity")
  } yield selected.head
  private def _fixed(basis: Basis, policy: Json, route: R.CheckProgram): Either[String, Json] = for {
    program <- _program(policy, route)
    plan <- policy.hcursor.get[Json]("processPlan").left.map(_.message)
    workspaces <- _array(plan, "workspaces")
    workspacesgrants <- _array(plan, "workspaceGrants")
    grants <- _array(plan, "grants")
    selected = workspaces.filter(_.hcursor.get[String]("identity").contains(route.workspace))
    capability <- program.hcursor.get[String]("capability").left.map(_.message)
    _ <- _check(selected.size == 1 && selected.head.hcursor.get[String]("root").contains(basis.primaryRoot), "primary workspace root")
    _ <- _check(workspacesgrants.count(g => g.hcursor.get[String]("componentId").contains(SmWorkflowComponent.componentId.name) &&
      g.hcursor.get[String]("workspaceId").contains(route.workspace) && g.hcursor.get[String]("access").contains("manage")) == 1 &&
      grants.exists(g => g.hcursor.get[String]("componentId").contains(SmWorkflowComponent.componentId.name) &&
        g.hcursor.get[Vector[String]]("capabilities").toOption.exists(_.contains(capability))), "original grants")
  } yield program
  private def _declared_check(basis: Basis, route: R.CheckProgram, phase: Option[V.Policy], retained: Boolean): Either[String, Unit] = for {
    policy <- _json(basis.capturedPolicy)
    steps <- policy.hcursor.get[V.Policy]("validation").left.map(_.message)
    _ <- _check(steps.validateC.isSuccess && _ordinal(route).nonEmpty, "default ordinal route")
    later = basis.plan.steps.drop(basis.plan.steps.indexWhere(_.identity == basis.adoptedStep) + 1).exists(_.checks.contains(route.check))
    phased = basis.plan.phaseChecks.contains(route.check)
    candidates = (if (later) steps.checks.filter(_.identity == route.check) else Vector.empty) ++
      (if (phased && !retained) phase.toVector.flatMap(_.checks.filter(_.identity == route.check)) else Vector.empty)
    _ <- _check((!phased || retained || phase.exists(_.validateC.isSuccess)) &&
      (if (retained && phased && !later) true else candidates.distinct match {
        case Vector(check) => check.ordinal > 0 && check.kind.wire == route.kind && _workspace(check.ordinal) == route.workspace
        case _ => false
      }), "declared later check kind and ordinal")
    _ <- _fixed(basis, policy, route).map(_ => ())
  } yield ()
  private def _extension(basis: Basis, value: R.Consumption, phase: Option[V.Policy], retained: Boolean): Either[String, Unit] = for {
    _ <- _basis(basis)
    _ <- _check(R.consumptionValid(value) && _same_core(basis.base, value) &&
      value.programs.startsWith(basis.base.programs), "unchanged base core and ordered routes")
    extras = value.programs.drop(basis.base.programs.size)
    _ <- _check(extras.nonEmpty && extras.forall(p => _allowed(basis)(p.check)), "remaining Plan check identities")
    _ <- extras.foldLeft[Either[String, Unit]](Right(()))((prior, route) => prior.flatMap(_ => _declared_check(basis, route, phase, retained)))
    _ <- _selector(basis, extras).map(_ => ())
  } yield ()
  private def _selector(basis: Basis, extras: Vector[R.CheckProgram]): Either[String, String] = for {
    _ <- _check(basis.previousRoot != null && basis.previousRoot.nonEmpty && basis.previousRoot != basis.base.sourceRoot,
      "distinct actual previous root")
    policy <- _json(basis.capturedPolicy)
    selected = basis.base.programs.filter(p => basis.plan.steps.find(_.identity == basis.adoptedStep).exists(_.checks.contains(p.check)))
    _ <- _check(selected.nonEmpty, "captured selected primary checks")
    programs <- (selected ++ extras).foldLeft[Either[String, Vector[Json]]](Right(Vector.empty)) { (prior, route) =>
      for { values <- prior; program <- _program(policy, route) } yield values :+ program
    }
    environments <- programs.foldLeft[Either[String, Vector[Map[String, String]]]](Right(Vector.empty)) { (prior, program) =>
      for { values <- prior; environment <- program.hcursor.get[Map[String, String]]("environment").left.map(_.message) }
        yield values :+ environment
    }
    keys = environments.head.keys.filter(key => key.nonEmpty && key.trim == key && !key.exists(_.isControl) &&
      environments.take(selected.size).forall(_.get(key).contains(basis.base.sourceRoot)) &&
      environments.drop(selected.size).forall(_.get(key).exists(value => value == basis.previousRoot || value == basis.base.sourceRoot))).toVector
    _ <- _check(keys.size == 1, "unique source selector")
  } yield keys.head
  private def _registration(basis: Basis, declarations: Vector[R.ConsumptionRecord], phase: Option[V.Policy]):
    Either[String, Option[R.Consumption]] = for {
    _ <- _basis(basis)
    _ <- _check(declarations != null, "host registration")
    matching = declarations.filter(_same_core(_, basis.base))
    _ <- _check(matching.distinct.size == matching.size, "duplicate registrations")
    extras = matching.filterNot(_ == basis.base)
    _ <- _check(extras.isEmpty || matching.contains(basis.base), "original base registration")
    _ <- _check(extras.size <= 1, "one current extension")
    selected <- extras.headOption match {
      case None => Right(None)
      case Some(value: R.Consumption) => _extension(basis, value, phase, retained = false).map(_ => Some(value))
      case _ => Left("Canonical check consumption refuses a legacy extension")
    }
  } yield selected
  private def _correct(policy: Json, basis: Basis, extras: Vector[R.CheckProgram], key: String): Either[String, Json] = for {
    plan <- policy.hcursor.get[Json]("processPlan").left.map(_.message)
    programs <- _array(plan, "sbtPrograms")
    captured <- _json(basis.capturedPolicy)
    definitions <- extras.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty)) { (prior, route) =>
      for { values <- prior; program <- _program(captured, route); actual <- _program(policy, route)
        _ <- _check(actual == program, "expected fixed check definition") } yield values :+ program
    }
    changed = programs.map(program => if (!definitions.contains(program)) program else program.mapObject(fields =>
      fields.add("environment", fields("environment").get.mapObject(_.add(key, Json.fromString(basis.base.sourceRoot))))))
  } yield policy.mapObject(_.add("processPlan", plan.mapObject(_.add("sbtPrograms", Json.fromValues(changed)))))

  def policyC(basis: Basis, expected: String, actual: String, declarations: Vector[R.ConsumptionRecord],
    phasePolicy: Option[V.Policy]): Consequence[Unit] = _result(for {
    extension <- _registration(basis, declarations, phasePolicy)
    _ <- extension match {
      case None => _check(actual == expected, "exact original policy")
      case Some(value) => for {
        policy <- _json(expected)
        live <- _json(actual)
        extras = value.programs.drop(basis.base.programs.size)
        key <- _selector(basis, extras)
        corrected <- _correct(policy, basis, extras, key)
        _ <- _check(live == corrected, "selector-only current policy")
      } yield ()
    }
  } yield ())

  def retainedBindingC(values: Vector[Option[R.ConsumptionRecord]]): Consequence[Option[Option[R.ConsumptionRecord]]] =
    _result(_check(values != null && values.forall(_ != null) && values.distinct.size <= 1, "consistent retained bindings")
      .map(_ => values.headOption))
  private def _covers(route: R.ConsumptionRecord, selected: Vector[V.Check]): Boolean =
    selected.filter(_.dependency.isEmpty).forall(check => route.programs.exists(p => p.check == check.identity && p.kind == check.kind.wire))
  def resolveC(base: Option[R.ConsumptionRecord], basis: Option[Basis], plan: O.Plan, selected: Vector[V.Check],
    sameStepCapture: Boolean, retained: Option[Option[R.ConsumptionRecord]], declarations: Vector[R.ConsumptionRecord],
    phasePolicy: Option[V.Policy]): Consequence[Option[R.ConsumptionRecord]] = _result[Option[R.ConsumptionRecord]](
    if (basis.exists(value => !base.contains(value.base))) Left("Canonical check basis crosses the actual original base") else retained match {
    case Some(None) => _check(base.isEmpty, "retained source-less binding crosses the actual source base").map(_ => None)
    case Some(Some(value)) => for {
      original <- base.toRight("Retained source consumption has no actual original base")
      _ <- if (value == original) Right(()) else for {
        canonical <- basis.toRight("Retained extended routing has no actual canonical capture")
        extended <- value match {
          case value: R.Consumption => Right(value)
          case _ => Left("Retained legacy routing cannot extend canonical checks")
        }
        _ <- _extension(canonical, extended, None, retained = true)
      } yield ()
      _ <- _check(_covers(value, selected) && (!sameStepCapture || value == original), "retained selected routes")
    } yield Some(value)
    case None => for {
      extension <- basis match {
        case None => Right(None)
        case Some(value) => _check(value.plan == plan, "exact current canonical Plan")
          .flatMap(_ => _registration(value, declarations, phasePolicy))
      }
      selectedroute <- base match {
        case None => Right(None)
        case Some(value) if _covers(value, selected) => Right(Some(value))
        case Some(_) => for {
          _ <- _check(!sameStepCapture, "literal same-Step captured consumption")
          value <- extension.filter(_covers(_, selected)).toRight("Primary checks lack a declared canonical check extension")
        } yield Some(value)
      }
    } yield selectedroute
  })

  def launchC(basis: Basis, retained: R.ConsumptionRecord, selected: Vector[V.Check],
    declarations: Vector[R.ConsumptionRecord], phasePolicy: Option[V.Policy], actualPolicy: String): Consequence[Unit] = _result(for {
    current <- _registration(basis, declarations, phasePolicy).flatMap(_.toRight("Current extended check registration is absent"))
    _ <- _check(_covers(retained, selected) && selected.filter(_.dependency.isEmpty).forall(check =>
      retained.programs.find(p => p.check == check.identity && p.kind == check.kind.wire).exists(current.programs.contains)),
      "currently registered selected routes")
    _ <- retained match {
      case value: R.Consumption => _extension(basis, value, None, retained = true)
      case _ => Left("Extended launch requires canonical v2 routing")
    }
    captured <- _json(basis.capturedPolicy)
    live <- _json(actualPolicy)
    key <- _selector(basis, current.programs.drop(basis.base.programs.size))
    _ <- selected.filter(_.dependency.isEmpty).foldLeft[Either[String, Unit]](Right(())) { (prior, check) => prior.flatMap { _ =>
      val route = retained.programs.find(p => p.check == check.identity && p.kind == check.kind.wire).get
      for {
        original <- _program(captured, route)
        actual <- _fixed(basis, live, route)
        expected = if (basis.base.programs.contains(route)) original else original.mapObject(fields =>
          fields.add("environment", fields("environment").get.mapObject(_.add(key, Json.fromString(basis.base.sourceRoot)))))
        _ <- _check(actual == expected, "live selected fixed program")
      } yield ()
    }}
  } yield ())
}
