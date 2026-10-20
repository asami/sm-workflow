# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Finite native Goal, Split or Repository host actions; admission remains native-owned.

PHASE-6 / S6-01 / S6-01D3D and S6-01D3F, finite caller rules.
@since   Oct. 14, 2026
@version Oct. 15, 2026
@version Oct. 16, 2026
@version Oct. 19, 2026
@author  ASAMI, Tomoharu
"""

from contextlib import contextmanager
import json
import os
from pathlib import Path
import re
import signal
import stat
import subprocess
import sys
import time


BASE_OPTIONS = {"executable", "host", "task", "action", "output"}
ACTION_OPTIONS = {
    "start-repository": {"input", "proposal"},
    "advance-repository": set(),
    "prepare-split-input": {
        "component-instance", "allocation-reference", "allocation", "source", "reference",
        "participant", "issuer", "occurred-at", "collection",
    },
    "start-split": {"input", "proposal"},
    "advance-split": set(),
    "submit-split-partition": {"request", "submission"},
    "submit-split-scope": {"request", "submission"},
    "submit-split-reasoning": {"request", "submission"},
    "prepare-execution": {"request", "mapping", "origin"},
    "record-execution": {"request", "execution-session", "observation", "reference"},
    "prepare-repository-input": {
        "input", "selection-id", "participant", "idempotency-key", "selection-collection",
    },
    "prepare-repository-effect": {
        "component-instance", "selection", "target", "effect", "reference",
        "participant", "issuer", "occurred-at", "collection",
    },
    "prepare-goal-input": {
        "input", "plan", "selection-id", "participant", "idempotency-key",
        "selection-collection", "plan-collection",
    },
    "prepare-review-input": {
        "request", "result", "reference", "execution-evidence", "issuer",
        "occurred-at", "collection",
    },
    "prepare-repository-review-input": {
        "request", "result", "reference", "execution-evidence", "issuer",
        "occurred-at", "collection",
    },
    "start": {"input", "proposal"},
    "advance": set(),
    "submit": {"request", "submission"},
    "complete": {"request", "submission"},
    "submit-repository-review": {"request", "submission"},
    "submit-repository-repair": {"request", "submission"},
    "submit-repository-conflict": {"request", "submission"},
}
FQN = "org.simplemodeling.textus.SmWorkflow.GoalPhaseService."
REPOSITORY_FQN = "org.simplemodeling.textus.SmWorkflow.RepositorySyncService."
SPLIT_FQN = "org.simplemodeling.textus.SmWorkflow.SplitPhaseService."
SPLIT_ROUTES = {
    "submit-split-partition": ("submitPartitionSemanticResult", "SplitPhasePartitionSemanticResult"),
    "submit-split-scope": ("submitHandoffScopeResult", "SplitPhaseScopeSemanticResult"),
    "submit-split-reasoning": ("submitReasoningResult", "SplitPhaseReasoningSemanticResult"),
}
REPOSITORY_ROUTES = {
    "submit-repository-review": ("submitNativeMergeReview", "RepositorySyncNativeMergeReviewResult"),
    "submit-repository-repair": ("submitNativeMergeRepair", "RepositorySyncNativeMergeRepairResult"),
    "submit-repository-conflict": ("submitRepositorySyncWorkResult", "RepositorySyncConflictResult"),
}
RESPONSE_LIMIT = 8 * 1024 * 1024
STDERR_LIMIT = 1024 * 1024
SESSION_FILE = "workflow-session.json"
EVENT_LINE_LIMIT = 16384
BUDGET_FIELDS = frozenset({"launchTimeoutMillis", "executionTimeoutMillis", "terminationGraceMillis"})
DISPLAY_KEYS = frozenset({
    "sessionId", "session_id", "submitSessionId", "submit_session_id", "kind", "outcome", "accepted", "message",
    "work", "step", "stepIdentity", "step_identity", "sourceRevision", "source_revision", "identity", "revision", "ordinal", "attempt",
    "repairProgress", "repair_progress", "generation", "repairCount", "repair_count", "repairLimit", "repair_limit",
    "current", "cumulative", "limit", "currentWindow", "current_window", "cumulativeRepairCount", "cumulative_repair_count",
    "automaticCount", "automatic_count", "ordinaryCount", "ordinary_count",
    "validation", "validationChecks", "validation_checks", "validationProgress", "validation_progress",
    "runtimeProgress", "runtime_progress", "checks", "checkIdentity", "check_identity", "checkOrdinal", "check_ordinal",
    "status", "exitCode", "exit_code", "termination", "elapsedMillis", "elapsed_millis", "durationMillis", "duration_millis",
    "selectedChecks", "selected_checks", "metadata", "scopeIdentity", "scope_identity", "sbt", "testSelection", "test_selection",
    "completedSuites", "completed_suites", "testsRun", "tests_run",
    "stdoutReference", "stdout_reference", "stderrReference", "stderr_reference", "outputReference", "output_reference",
})


class CallerError(Exception):
    """A finite caller contract failure; never a retry instruction."""


def require(condition, message):
    if not condition:
        raise CallerError(message)


def nonblank(value):
    return isinstance(value, str) and bool(value.strip())


def native_field(value, name):
    """Read only consumed native fields, refusing contradictory spellings."""
    wire_name = {
        "sessionId": "session_id",
        "submitOperation": "submit_operation",
        "submitSessionId": "submit_session_id",
        "resultType": "result_type",
        "issuedWorkContext": "issued_work_context",
        "nextSelection": "next_selection",
        "completionEvidence": "completion_evidence",
    }[name]
    require(name not in value or wire_name not in value or value[name] == value[wire_name],
            "contradictory native field aliases: " + name)
    return value[name] if name in value else value.get(wire_name)


def parse_options(argv):
    """Accept literal named pairs only, including no duplicate options."""
    require(len(argv) % 2 == 0, "arguments must be named value pairs")
    options = {}
    known = BASE_OPTIONS | set().union(*ACTION_OPTIONS.values())
    for key, value in zip(argv[::2], argv[1::2]):
        require(isinstance(key, str) and key.startswith("--"), "expected named argument")
        name = key[2:]
        require(name in known and name not in options, "unknown or duplicate argument")
        require(nonblank(value) and value == value.strip()
                and not any(ord(c) < 32 or ord(c) == 127 for c in value),
                "argument must be nonblank exact text")
        options[name] = value
    require(BASE_OPTIONS <= options.keys(), "missing common argument")
    action = options["action"]
    require(action in ACTION_OPTIONS, "unsupported action")
    permitted = BASE_OPTIONS | ACTION_OPTIONS[action]
    optional = {"proposal"} if action in {"start", "start-split", "start-repository"} else {"reference"} if action == "record-execution" else set()
    required = permitted - optional
    require(options.keys() <= permitted, "action-inapplicable argument")
    require(required <= options.keys(), "missing action argument")
    if action == "prepare-repository-effect":
        require(options["effect"] in {"CommitPending", "Push", "FinalFetch"},
                "unsupported Repository effect")
    preparation_collections = {
        "prepare-split-input": ("collection",),
        "prepare-repository-effect": ("collection",),
        "prepare-goal-input": ("selection-collection", "plan-collection"),
        "prepare-review-input": ("collection",),
        "prepare-repository-review-input": ("collection",),
        "prepare-repository-input": ("selection-collection",),
    }
    if action in preparation_collections:
        collections = preparation_collections[action]
        for name in collections:
            require(re.fullmatch(r"entity:[A-Za-z][A-Za-z0-9_]*\.[A-Za-z][A-Za-z0-9_]*\.[A-Za-z][A-Za-z0-9_]*",
                                 options[name]) is not None, "collection must be a strict dotted entity address")
        if action == "prepare-goal-input":
            require(options["selection-collection"] != options["plan-collection"],
                    "selected collections must be distinct")
    return options


def explicit_path(value):
    require(os.path.isabs(value) and os.path.normpath(value) == value,
            "path must be absolute and normalized")
    return Path(value)


def existing_file(path):
    require(path.is_file(), "required file is unavailable")
    return path


@contextmanager
def selected_directory(path):
    """Pin an existing write directory through symlink-free ancestors."""
    flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW
    descriptor = os.open(path.anchor, flags)
    try:
        for part in path.parts[1:]:
            child = os.open(part, flags, dir_fd=descriptor)
            os.close(descriptor)
            descriptor = child
        yield descriptor
    finally:
        os.close(descriptor)


def strict_pairs(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "JSON contains duplicate keys")
        result[key] = value
    return result


def invalid_constant(_value):
    raise CallerError("JSON contains a nonfinite number")


def json_object(text):
    value = json.loads(text, object_pairs_hook=strict_pairs, parse_constant=invalid_constant)
    require(isinstance(value, dict), "expected a JSON object")
    return value


def read_object(path):
    text = existing_file(path).read_text(encoding="utf-8")
    return text, json_object(text)


def route_record(task_fd):
    descriptor = os.open(SESSION_FILE, os.O_RDONLY | os.O_NOFOLLOW, dir_fd=task_fd)
    with os.fdopen(descriptor, "r", encoding="utf-8") as stream:
        require(stat.S_ISREG(os.fstat(stream.fileno()).st_mode), "routing must be a regular file")
        value = json_object(stream.read())
    require(set(value) == {"family", "sessionId"} and value["family"] in {"GoalPhase", "SplitPhase", "RepositorySync"}
            and nonblank(value["sessionId"]), "invalid task routing")
    return value


def routing(task_fd, family="GoalPhase"):
    value = route_record(task_fd)
    require(value["family"] == family, "invalid " + family + " task routing")
    return value["sessionId"]


def routing_absent(task_fd):
    try:
        os.stat(SESSION_FILE, dir_fd=task_fd, follow_symlinks=False)
    except FileNotFoundError:
        return
    raise CallerError("task routing is already occupied")


def new_file(directory_fd, name):
    descriptor = os.open(name, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                         0o600, dir_fd=directory_fd)
    return os.fdopen(descriptor, "wb")


def command_prefix(executable, host):
    """Use the explicitly selected installed CLI; artifact selection is its own."""
    return [str(executable), "--host", str(host), "command"]


def installed_executable(path):
    require(path.is_file() and os.access(path, os.X_OK), "installed executable is unavailable or not executable")
    return path


def prepare_goal_input(options, executable):
    """Pass opaque selected URIs and finite metadata to the normal typed owner."""
    value = {"input": existing_file(explicit_path(options["input"])).as_uri(),
             "plan": existing_file(explicit_path(options["plan"])).as_uri(),
             "selectionId": options["selection-id"], "participant": options["participant"],
             "origin": "ExplicitHuman", "idempotencyKey": options["idempotency-key"],
             "selectionCollection": options["selection-collection"],
             "planCollection": options["plan-collection"]}
    return command_prefix(executable, explicit_path(options["host"])) + [
        "org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareGoalStart",
        "--input", json.dumps(value, ensure_ascii=False), "--json"], None


def prepare_repository_input(options, executable):
    """Forward one opaque selected URI and explicit metadata to the normal owner."""
    value = {"input": existing_file(explicit_path(options["input"])).as_uri(),
             "selectionId": options["selection-id"], "participant": options["participant"],
             "origin": "ExplicitHuman", "idempotencyKey": options["idempotency-key"],
             "selectionCollection": options["selection-collection"]}
    return command_prefix(executable, explicit_path(options["host"])) + [
        "org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareRepositoryStart",
        "--input", json.dumps(value, ensure_ascii=False), "--json"], None


def prepare_split_input(options, executable):
    """Forward the nine generated fields without acquiring native-owned content."""
    value = {"componentInstance": options["component-instance"]}
    for field, option in (("allocationReference", "allocation-reference"), ("allocation", "allocation"),
                          ("source", "source"), ("reference", "reference")):
        value[field] = existing_file(explicit_path(options[option])).as_uri()
    value.update(participant=options["participant"], issuer=options["issuer"],
                 occurredAt=options["occurred-at"], collection=options["collection"])
    return command_prefix(executable, explicit_path(options["host"])) + [
        "org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareSplitStart",
        "--input", json.dumps(value, ensure_ascii=False), "--json"], None


def prepare_repository_effect(options, executable):
    """Forward opaque effect sources and explicit metadata to the normal owner."""
    value = {"componentInstance": options["component-instance"]}
    for name in ("selection", "target", "reference"):
        value[name] = existing_file(explicit_path(options[name])).as_uri()
    value.update(effect=options["effect"], participant=options["participant"],
                 issuer=options["issuer"], occurredAt=options["occurred-at"],
                 collection=options["collection"])
    return command_prefix(executable, explicit_path(options["host"])) + [
        "org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareRepositoryEffect",
        "--input", json.dumps(value, ensure_ascii=False), "--json"], None


def start(options, task_fd):
    routing_absent(task_fd)
    input_text, _input = read_object(explicit_path(options["input"]))
    pairs = ["--input", input_text]
    if "proposal" in options:
        pairs.extend(["--sessionId", options["proposal"]])
    operation = {"start": "startGoalPhase", "start-split": "startSplitPhase",
                 "start-repository": "startRepositorySync"}[options["action"]]
    return operation, pairs, None


def advance(task_fd, family="GoalPhase"):
    session_id = routing(task_fd, family)
    operation = {"GoalPhase": "advanceGoalPhase", "SplitPhase": "advanceSplitPhase",
                 "RepositorySync": "advanceRepositorySync"}[family]
    return operation, ["--sessionId", session_id], session_id


def issued_split_route(options, task_fd):
    """Admit only the action-selected semantic route on its retained Split."""
    session_id = routing(task_fd, "SplitPhase")
    _text, request = read_object(explicit_path(options["request"]))
    operation = native_field(request, "submitOperation")
    result_type = native_field(request, "resultType")
    require(request.get("kind") == "Request"
            and native_field(request, "sessionId") == session_id
            and native_field(request, "submitSessionId") == session_id
            and (operation, result_type) == SPLIT_ROUTES[options["action"]],
            "unsupported or contradictory issued Split Submit route")
    return operation, session_id


def issued_goal_route(options, task_fd):
    """Pin the selected task to the consumed fields of its issued Request."""
    session_id = routing(task_fd)
    _text, request = read_object(explicit_path(options["request"]))
    request_session_id = native_field(request, "sessionId")
    submit_operation = native_field(request, "submitOperation")
    submit_session_id = native_field(request, "submitSessionId")
    result_type = native_field(request, "resultType")
    require(request_session_id == session_id and request.get("kind") == "Request"
            and isinstance(request.get("work"), dict)
            and submit_operation == "submitGoalPhaseWorkResult"
            and submit_session_id == session_id
            and result_type == "GoalPhaseWorkResult",
            "unsupported or contradictory issued Goal Submit route")
    return submit_operation, session_id


def issued_repository_review_route(options, task_fd):
    """Admit the fixed review pair under its retained Goal or Repository owner."""
    return issued_repository_route(options, task_fd, "submit-repository-review")


def issued_repository_route(options, task_fd, action, route=None):
    """Consume one private finite pair with distinct-child or standalone IDs."""
    route = route_record(task_fd) if route is None else route
    owning_id = route["sessionId"]
    _text, request = read_object(explicit_path(options["request"]))
    request_id = native_field(request, "sessionId")
    operation = native_field(request, "submitOperation")
    child_id = native_field(request, "submitSessionId")
    result_type = native_field(request, "resultType")
    owner = ((route["family"] == "GoalPhase" and nonblank(child_id) and child_id != owning_id)
             or (route["family"] == "RepositorySync" and child_id == owning_id))
    require(request.get("kind") == "Request" and request_id == owning_id and owner
            and (operation, result_type) == REPOSITORY_ROUTES[action],
            "unsupported or contradictory issued Repository route")
    return operation, child_id


def review_input_command(options, executable, session_id, family="GoalPhase"):
    """Forward whole opaque files to the normal typed preparation Operation."""
    value = {"family": family, "kind": "review",
             "request": existing_file(explicit_path(options["request"])).as_uri(),
             "result": existing_file(explicit_path(options["result"])).as_uri(),
             "reference": existing_file(explicit_path(options["reference"])).as_uri(),
             "executionEvidence": existing_file(explicit_path(options["execution-evidence"])).as_uri(),
             "issuer": options["issuer"], "occurredAt": options["occurred-at"],
             "collection": options["collection"]}
    return command_prefix(executable, explicit_path(options["host"])) + [
        "org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareEvidence",
        "--input", json.dumps(value, ensure_ascii=False), "--json"], session_id


def issued_repository_repair_route(options, task_fd):
    """Admit the fixed repair pair under its retained Goal or Repository owner."""
    return issued_repository_route(options, task_fd, "submit-repository-repair")


def prepare_review_input(options, executable, task_fd):
    """Keep original review preparation strictly on the outer Goal route."""
    _operation, session_id = issued_goal_route(options, task_fd)
    return review_input_command(options, executable, session_id)


def issued_repository_conflict_route(options, task_fd):
    """Admit the fixed conflict pair under its retained Goal or Repository owner."""
    return issued_repository_route(options, task_fd, "submit-repository-conflict")


def submission_pairs(options, session_id, split=False):
    """Forward supplied opaque values under the existing metadata contract."""
    _text, submission = read_object(explicit_path(options["submission"]))
    required = {"result"} if split else {"result", "resultReference"}
    require(required <= submission.keys()
            and submission.keys() <= required | {"completionFacts", "executionEvidence"},
            "invalid submission call metadata")
    require(isinstance(submission["result"], dict)
            and (split or isinstance(submission["resultReference"], dict)),
            "result and resultReference must be objects")
    require("completionFacts" not in submission or isinstance(submission["completionFacts"], list),
            "completionFacts must be an array")
    require("executionEvidence" not in submission or isinstance(submission["executionEvidence"], dict),
            "executionEvidence must be an object")
    pairs = ["--sessionId", session_id]
    for name in ("result", "resultReference", "completionFacts", "executionEvidence"):
        if name in submission:
            pairs.extend(["--" + name, json.dumps(submission[name], ensure_ascii=False, allow_nan=False)])
    return pairs


def submit(options, task_fd):
    submit_operation, session_id = issued_goal_route(options, task_fd)
    return submit_operation, submission_pairs(options, session_id), session_id


def execution_command(options, executable, task_fd):
    """Forward the whole issued DTO and explicitly selected host artifact URIs."""
    route = route_record(task_fd)
    session_id = route["sessionId"]
    _text, request = read_object(explicit_path(options["request"]))
    request_id = native_field(request, "sessionId")
    operation = native_field(request, "submitOperation")
    target_id = native_field(request, "submitSessionId")
    result_type = native_field(request, "resultType")
    own = (route["family"] == "GoalPhase"
           and operation == "submitGoalPhaseWorkResult" and target_id == session_id
           and result_type == "GoalPhaseWorkResult" and isinstance(request.get("work"), dict))
    child = (route["family"] == "GoalPhase" and (operation, result_type) in REPOSITORY_ROUTES.values()
             and nonblank(target_id) and target_id != session_id)
    repository = (route["family"] == "RepositorySync" and target_id == session_id
                  and (operation, result_type) in REPOSITORY_ROUTES.values())
    split = (route["family"] == "SplitPhase" and target_id == session_id
             and (operation, result_type) in SPLIT_ROUTES.values())
    require(request.get("kind") == "Request" and request_id == session_id and (own or child or split or repository),
            "unsupported or contradictory issued execution route under selected task")
    issued = native_field(request, "issuedWorkContext")
    require(isinstance(issued, dict) and bool(issued), "execution requires the actual issuedWorkContext object")
    value = {"issuedWorkContext": issued}
    if options["action"] == "prepare-execution":
        operation = "prepareExecution"
        fields = (("mapping", "mapping"), ("origin", "origin"))
    else:
        operation = "recordExecution"
        fields = (("session", "execution-session"), ("observation", "observation"))
        if "reference" in options:
            fields += (("reference", "reference"),)
    for field, option in fields:
        value[field] = existing_file(explicit_path(options[option])).as_uri()
    return command_prefix(executable, explicit_path(options["host"])) + [
        "org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService." + operation,
        "--input", json.dumps(value, ensure_ascii=False), "--json"], session_id


def action_command(options, executable, host, task_fd):
    action = options["action"]
    if action in {"prepare-execution", "record-execution"}:
        return execution_command(options, executable, task_fd)
    if action == "prepare-repository-input":
        return prepare_repository_input(options, executable)
    if action == "prepare-repository-effect":
        return prepare_repository_effect(options, executable)
    if action == "prepare-goal-input":
        return prepare_goal_input(options, executable)
    if action == "prepare-split-input":
        return prepare_split_input(options, executable)
    if action == "prepare-review-input":
        return prepare_review_input(options, executable, task_fd)
    if action == "prepare-repository-review-input":
        route = route_record(task_fd)
        _operation, session_id = issued_repository_route(options, task_fd, "submit-repository-review", route)
        return review_input_command(options, executable, session_id, route["family"])
    service = FQN
    if action in {"start", "start-split", "start-repository"}:
        operation, pairs, session_id = start(options, task_fd)
        if action == "start-split":
            service = SPLIT_FQN
        elif action == "start-repository":
            service = REPOSITORY_FQN
    elif action == "advance":
        operation, pairs, session_id = advance(task_fd)
    elif action == "advance-split":
        operation, pairs, session_id = advance(task_fd, "SplitPhase")
        service = SPLIT_FQN
    elif action == "advance-repository":
        operation, pairs, session_id = advance(task_fd, "RepositorySync")
        service = REPOSITORY_FQN
    elif action in SPLIT_ROUTES:
        operation, session_id = issued_split_route(options, task_fd)
        pairs = submission_pairs(options, session_id, split=True)
        service = SPLIT_FQN
    elif action == "submit-repository-review":
        operation, session_id = issued_repository_review_route(options, task_fd)
        pairs = submission_pairs(options, session_id)
        service = REPOSITORY_FQN
    elif action == "submit-repository-repair":
        operation, session_id = issued_repository_repair_route(options, task_fd)
        pairs = submission_pairs(options, session_id)
        service = REPOSITORY_FQN
    elif action == "submit-repository-conflict":
        operation, session_id = issued_repository_conflict_route(options, task_fd)
        pairs = submission_pairs(options, session_id)
        service = REPOSITORY_FQN
    else:
        operation, pairs, session_id = submit(options, task_fd)
    command = command_prefix(executable, host) + [service + operation]
    return command + pairs + ["--json"], session_id


def progress(event, context, **observed):
    """Display attributable caller observations, separate from business DTOs."""
    display_observation(dict(recordType="native-command-progress", event=event,
                             **context, **observed))


def display_value(value, depth=0):
    """Bound display metadata; full native data remains in the raw response."""
    if isinstance(value, str):
        return value if len(value) <= 1024 else {"preview": value[:1024], "truncated": True}
    if value is None or isinstance(value, (bool, int, float)):
        return value
    if depth >= 4:
        return {"displayOmitted": "nested metadata; see raw response"}
    if isinstance(value, list):
        selected = [display_value(item, depth + 1) for item in value[:16]]
        return selected if len(value) <= 16 else {"items": selected, "truncated": True}
    if isinstance(value, dict):
        selected = {name: display_value(item, depth + 1) for name, item in value.items() if name in DISPLAY_KEYS}
        if "work" in selected and isinstance(value.get("work"), dict):
            selected["work"] = {name: display_value(value["work"][name], depth + 1)
                                for name in ("kind", "step", "stepIdentity", "step_identity", "sourceRevision", "source_revision") if name in value["work"]}
        return selected
    return {"displayOmitted": "unsupported metadata; see raw response"}


def observation_projection(value):
    selected = display_value(value)
    if len(json.dumps(selected, ensure_ascii=False, allow_nan=False).encode("utf-8")) > 16384:
        return {"displayOmitted": "large metadata; see raw response"}
    return selected


def exchange_context(command, host, output, outer_id, request=None):
    """Use only the actual selected invocation and optional issued DTO."""
    operation = command[command.index("command") + 1]
    target = command[command.index("--sessionId") + 1] if "--sessionId" in command else None
    context = {"operation": operation, "outerSessionId": display_value(outer_id),
               "targetSessionId": display_value(target), "host": str(host),
               "responseReference": str(output / "response.json"),
               "stderrReference": str(output / "stderr.log")}
    if request is not None:
        context["issuedObservation"] = observation_projection(request)
    return context


def response_progress(response, context):
    """Expose actual work, Step, repair counts and checks without interpretation."""
    progress("response", context, observation=observation_projection(response))


def native_call(command, host, output_fd, context):
    """Wait for one actual runtime response; only the runtime owns termination."""
    require(os.name == "posix" and hasattr(os, "setsid"),
            "native calls require an owned POSIX runtime process")
    with new_file(output_fd, "response.json") as stdout:
        with new_file(output_fd, "stderr.log") as stderr:
            descriptor = os.open("stderr.log", os.O_RDONLY | os.O_NOFOLLOW, dir_fd=output_fd)
            with os.fdopen(descriptor, "rb") as reader:
                pending = b""
                discarding = False
                process = None
                cancellation = {"requested": False, "attempted": False, "forwarded": False, "alreadyExited": False}
                cancellation_displayed = False

                def request_termination():
                    cancellation["requested"] = True
                    if process is not None and not cancellation["attempted"]:
                        cancellation["attempted"] = True
                        if process.poll() is not None:
                            cancellation["alreadyExited"] = True
                            return
                        try:
                            os.kill(process.pid, signal.SIGTERM)
                            cancellation["forwarded"] = True
                        except ProcessLookupError:
                            cancellation["alreadyExited"] = True

                def on_sigterm(_signal, _frame):
                    request_termination()

                def observe_cancellation():
                    nonlocal cancellation_displayed
                    if cancellation["attempted"] and not cancellation_displayed:
                        cancellation_displayed = True
                        progress("cancellation-requested", context, pid=process.pid, signal="SIGTERM",
                                 forwarded=cancellation["forwarded"], runtimeAlreadyExited=cancellation["alreadyExited"])

                def forward(final=False):
                    nonlocal pending, discarding

                    def emit(line):
                        if line.startswith(b"[cncf-runtime-event] ") and len(line) <= EVENT_LINE_LIMIT:
                            try:
                                raw = line.decode("utf-8")
                                event = json_object(raw[len("[cncf-runtime-event] "):])
                                if not ({"state", "pid"} <= event.keys() <= {"state", "pid", "elapsedMillis"}
                                        and event["state"] in {"runtime-started", "cancellation-requested", "settled", "uncertain"}
                                        and type(event["pid"]) is int and event["pid"] > 0
                                        and ("elapsedMillis" not in event or type(event["elapsedMillis"]) is int
                                             and 0 <= event["elapsedMillis"] <= 9223372036854775807)):
                                    return
                            except (ValueError, CallerError, UnicodeError, TypeError, RecursionError):
                                return
                            sys.stderr.write(raw + "\n")
                            sys.stderr.flush()
                            progress("runtime-event", context, rawEvent=raw)
                            return
                        if not line.startswith(b"[cncf-sbt-event] ") or len(line) > EVENT_LINE_LIMIT:
                            return
                        try:
                            raw = line.decode("utf-8")
                            event = json_object(raw[len("[cncf-sbt-event] "):])
                            required = {"operation", "workspaceId", "programIdentity", "state"}
                            optional = {"pid", "termination", "exitCode", "elapsedMillis"} | BUDGET_FIELDS
                            budgets = BUDGET_FIELDS & event.keys()
                            if not (required <= event.keys() <= required | optional
                                    and event["operation"] in {"compile", "test", "test-only"}
                                    and event["state"] in {"launching", "started", "waiting", "terminal", "uncertain"}
                                    and all(nonblank(event[name]) and len(event[name]) <= 1024
                                            for name in ("workspaceId", "programIdentity"))
                                    and all(type(event[name]) is int for name in ("pid", "exitCode", "elapsedMillis") if name in event)
                                    and ("pid" not in event or event["pid"] > 0)
                                    and ("elapsedMillis" not in event or event["elapsedMillis"] >= 0)
                                    and ("termination" not in event or nonblank(event["termination"]))
                                    and (not budgets or (budgets == BUDGET_FIELDS and event["state"] == "launching"
                                        and all(type(event[name]) is int and 0 < event[name] <= 9223372036854775807
                                                for name in BUDGET_FIELDS)))):
                                return
                        except (ValueError, CallerError, UnicodeError, TypeError, RecursionError):
                            return
                        sys.stderr.write(raw + "\n")
                        sys.stderr.flush()
                        progress("sbt-event", context, rawEvent=raw)

                    available = max(0, os.fstat(reader.fileno()).st_size - reader.tell()) if final else 65536
                    while available > 0:
                        chunk = reader.read(min(65536, available))
                        if not chunk:
                            break
                        available -= len(chunk)
                        for fragment in chunk.splitlines(keepends=True):
                            complete = fragment.endswith(b"\n")
                            if not discarding:
                                pending += fragment
                                if len(pending) > EVENT_LINE_LIMIT:
                                    pending = b""
                                    discarding = True
                            if complete:
                                line = pending[:-1] if not discarding else None
                                pending = b""
                                discarding = False
                                if line is not None:
                                    emit(line)
                        if not final:
                            break
                    if final and pending and not discarding:
                        line = pending
                        pending = b""
                        emit(line)

                started = time.monotonic()
                progress("invocation", context)
                previous = signal.signal(signal.SIGTERM, on_sigterm)
                try:
                    process = subprocess.Popen(command, cwd=str(host), stdout=stdout, stderr=stderr, start_new_session=True)
                    if cancellation["requested"]:
                        request_termination()
                    last_activity = started
                    while True:
                        try:
                            process.wait(timeout=0.1)
                        except subprocess.TimeoutExpired:
                            pass
                        observe_cancellation()
                        forward()
                        now = time.monotonic()
                        if process.returncode is not None:
                            break
                        if now - last_activity >= 5:
                            progress("running", context, elapsedSeconds=round(now - started, 1))
                            last_activity = now
                    stdout.flush()
                    stderr.flush()
                    forward(final=True)
                    progress("process-terminated", context, pid=process.pid, exitCode=process.returncode,
                             elapsedSeconds=round(time.monotonic() - started, 1))
                except BaseException as error:
                    if process is not None:
                        try:
                            request_termination()
                        except OSError as termination_error:
                            error.add_note("Runtime termination request failed: " + type(termination_error).__name__)
                        # Runtime owns cancellation, persistence and shutdown.
                        # Observation errors and Ctrl-C never introduce an outer kill timer.
                        while process.poll() is None:
                            try:
                                process.wait(timeout=0.1)
                            except (subprocess.TimeoutExpired, KeyboardInterrupt):
                                pass
                        try:
                            observe_cancellation()
                            stdout.flush()
                            stderr.flush()
                            forward(final=True)
                            progress("process-terminated", context, pid=process.pid, exitCode=process.returncode,
                                     elapsedSeconds=round(time.monotonic() - started, 1))
                        except BaseException as observation_error:
                            error.add_note("Final runtime observation unavailable: " + type(observation_error).__name__)
                    raise
                finally:
                    signal.signal(signal.SIGTERM, previous)
    require(process.returncode == 0, "native process exited unsuccessfully; retain output")
    for name, limit in (("response.json", RESPONSE_LIMIT), ("stderr.log", STDERR_LIMIT)):
        require(os.stat(name, dir_fd=output_fd, follow_symlinks=False).st_size <= limit,
                "native output exceeds its bound; retain output")
    descriptor = os.open("response.json", os.O_RDONLY | os.O_NOFOLLOW, dir_fd=output_fd)
    with os.fdopen(descriptor, "r", encoding="utf-8") as stream:
        text = stream.read(RESPONSE_LIMIT + 1)
    response = json_object(text)
    if cancellation["requested"]:
        display_observation(response)
        response_progress(response, context)
        raise CallerError("termination requested; actual result retained; no following Advance")
    return text, response


def completion_commands(options, executable, host, task_fd):
    """Select a closed issued Submit pair and retain its explicit outer Advance."""
    route = route_record(task_fd)
    _text, request = read_object(explicit_path(options["request"]))
    pair = (native_field(request, "submitOperation"), native_field(request, "resultType"))
    family = route["family"]
    if family == "GoalPhase" and pair == ("submitGoalPhaseWorkResult", "GoalPhaseWorkResult"):
        action = "submit"
    else:
        routes = SPLIT_ROUTES if family == "SplitPhase" else REPOSITORY_ROUTES
        action = next((name for name, candidate in routes.items() if candidate == pair), None)
        require(action is not None, "unsupported issued completion route")
    command, submit_id = action_command(dict(options, action=action), executable, host, task_fd)
    advance_action, service, operation = {
        "GoalPhase": ("advance", FQN, "advanceGoalPhase"),
        "SplitPhase": ("advance-split", SPLIT_FQN, "advanceSplitPhase"),
        "RepositorySync": ("advance-repository", REPOSITORY_FQN, "advanceRepositorySync"),
    }[family]
    outer_id = route["sessionId"]
    following = command_prefix(executable, host) + [service + operation, "--sessionId", outer_id, "--json"]
    return command, submit_id, following, outer_id, advance_action


def display_observation(response):
    """Flush one actual strict JSON observation before any following native call."""
    sys.stdout.write(json.dumps(response, ensure_ascii=False, allow_nan=False) + "\n")
    sys.stdout.flush()


def complete(options, executable, host, task_fd, output_fd):
    """Submit once, then explicitly Advance once only after actual acceptance."""
    command, submit_id, following, outer_id, advance_action = completion_commands(
        options, executable, host, task_fd)
    os.mkdir("advance", mode=0o700, dir_fd=output_fd)
    advance_fd = os.open("advance", os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=output_fd)
    _request_text, request = read_object(explicit_path(options["request"]))
    output = explicit_path(options["output"])
    submit_context = exchange_context(command, host, output, outer_id, request)
    advance_context = exchange_context(following, host, output / "advance", outer_id)
    try:
        _text, response = native_call(command, host, output_fd, submit_context)
        require(native_field(response, "sessionId") == submit_id and nonblank(response.get("message")),
                "Submit response ID or message mismatches the selected route")
        require(isinstance(response.get("accepted"), bool) and nonblank(response.get("outcome")),
                "Submit response lacks boolean accepted or outcome")
        display_observation(response)
        response_progress(response, submit_context)
        require(response["accepted"] is True and response["outcome"] == "accepted",
                "Submit was not acknowledged as accepted")
        require(route_record(task_fd) == {"family": {"advance": "GoalPhase", "advance-split": "SplitPhase",
                               "advance-repository": "RepositorySync"}[advance_action],
                "sessionId": outer_id}, "task routing changed after accepted Submit; retain output and stop")
        _text, response = native_call(following, host, advance_fd, advance_context)
        acknowledge(advance_action, response, outer_id, task_fd, advance_fd)
        display_observation(response)
        response_progress(response, advance_context)
    finally:
        os.close(advance_fd)


def acknowledge(action, response, session_id, task_fd, output_fd):
    if action in {"prepare-execution", "record-execution"}:
        session = response.get("session")
        selection = native_field(response, "nextSelection")
        evidence = native_field(response, "completionEvidence")
        require(nonblank(session), "execution response requires an opaque session string")
        require(selection is None or nonblank(selection), "execution selection must be an opaque string")
        require(evidence is None or isinstance(evidence, dict), "execution evidence must be structured")
        with new_file(output_fd, "execution-session.json") as stream:
            stream.write(session.encode("utf-8"))
        if selection is not None:
            with new_file(output_fd, "execution-selection.json") as stream:
                stream.write(selection.encode("utf-8"))
        if evidence is not None:
            with new_file(output_fd, "execution-evidence.json") as stream:
                stream.write(json.dumps(evidence, ensure_ascii=False, allow_nan=False).encode("utf-8"))
        return
    if action in {"prepare-goal-input", "prepare-review-input", "prepare-repository-input",
                  "prepare-repository-review-input", "prepare-split-input", "prepare-repository-effect"}:
        require(isinstance(response.get("datastore"), list), "preparation must return a datastore array")
        return
    response_session_id = native_field(response, "sessionId")
    require(nonblank(response_session_id) and nonblank(response.get("message")),
            "native response lacks sessionId or message")
    if action in {"start", "start-split", "start-repository"}:
        require(response.get("outcome") == "started", "Start was not acknowledged as started")
        payload = {"family": {"start": "GoalPhase", "start-split": "SplitPhase",
                              "start-repository": "RepositorySync"}[action],
                   "sessionId": response_session_id}
        with new_file(task_fd, SESSION_FILE) as stream:
            stream.write((json.dumps(payload, ensure_ascii=False) + "\n").encode("utf-8"))
        return
    require(response_session_id == session_id, "native response ID mismatches the selected route")
    if action in {"advance", "advance-split", "advance-repository"}:
        require(response.get("kind") in {"Request", "Human", "Wait", "Terminal"},
                "unsupported Advance kind")
    else:
        require(response.get("accepted") is True and response.get("outcome") == "accepted",
                "Submit was not acknowledged as accepted")


def execute(options):
    executable, host, task, output = (explicit_path(options[name])
                                   for name in ("executable", "host", "task", "output"))
    require(task.parent == host / "target/sm-workflow/tasks", "task must be a direct owned host task")
    require(output != task and task in output.parents, "output must be beneath the selected task")
    installed_executable(executable)
    with selected_directory(host):
        for path in (host / ".textus/config.json", host / ".textus/assembly.yaml"):
            existing_file(path)
        with selected_directory(task) as task_fd, selected_directory(output) as output_fd:
            require(not os.listdir(output_fd), "output directory must be empty")
            if options["action"] == "complete":
                complete(options, executable, host, task_fd, output_fd)
                return
            command, session_id = action_command(options, executable, host, task_fd)
            request = read_object(explicit_path(options["request"]))[1] if "request" in options else None
            context = exchange_context(command, host, output,
                                       route_record(task_fd)["sessionId"] if session_id is not None else None,
                                       request)
            text, response = native_call(command, host, output_fd, context)
            acknowledge(options["action"], response, session_id, task_fd, output_fd)
            response_progress(response, context)
            sys.stdout.write(text)
            sys.stdout.flush()


def main(argv=None):
    """Return 0 only for the selected actual acknowledgement; errors return 2."""
    try:
        execute(parse_options(list(sys.argv[1:] if argv is None else argv)))
        return 0
    except CallerError as error:
        diagnostic = str(error)
    except KeyboardInterrupt:
        print("native-goal-command: native process interrupted; business outcome remains unknown; retain output and stop", file=sys.stderr)
        return 130
    except (OSError, ValueError, RecursionError, TypeError):
        diagnostic = "invalid file, JSON or response contract; retain available output and stop"
    safe = "".join(c for c in diagnostic if c.isprintable())[:512]
    print("native-goal-command: " + safe, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
