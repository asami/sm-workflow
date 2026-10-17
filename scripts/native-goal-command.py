# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""One explicit native Goal, Split or Repository action; admission remains native-owned.

PHASE-6 / S6-01 / S6-01D3D and S6-01D3F, finite caller rules.
@since   Oct. 14, 2026
@version Oct. 15, 2026
@version Oct. 16, 2026
@author  ASAMI, Tomoharu
"""

from contextlib import contextmanager
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys


BASE_OPTIONS = {"primary", "host", "task", "action", "output"}
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
    preparation_collections = {
        "prepare-split-input": ("collection",),
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


def launcher_prefix(primary):
    return [str(primary / "bin/launcher"), "--classpath-file",
            str(primary / "target/cncf.d/runtime-classpath.txt"),
            "--main-class", "org.goldenport.cncf.CncfMain", "--java-opt", "-Dstdout.encoding=UTF-8", "--"]


def command_prefix(primary, host):
    """The ordinary configured CNCF launcher shared by every finite action."""
    return launcher_prefix(primary) + ["--force-exit", "--component-dev-dir", str(primary),
            "--textus.assembly.descriptor=" + str(host / ".textus/assembly.yaml"), "command"]


def prepare_goal_input(options, primary):
    """Pass opaque selected URIs and finite metadata to the normal typed owner."""
    value = {"input": existing_file(explicit_path(options["input"])).as_uri(),
             "plan": existing_file(explicit_path(options["plan"])).as_uri(),
             "selectionId": options["selection-id"], "participant": options["participant"],
             "origin": "ExplicitHuman", "idempotencyKey": options["idempotency-key"],
             "selectionCollection": options["selection-collection"],
             "planCollection": options["plan-collection"]}
    return command_prefix(primary, explicit_path(options["host"])) + [
        "org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareGoalStart",
        "--input", json.dumps(value, ensure_ascii=False), "--json"], None


def prepare_repository_input(options, primary):
    """Forward one opaque selected URI and explicit metadata to the normal owner."""
    value = {"input": existing_file(explicit_path(options["input"])).as_uri(),
             "selectionId": options["selection-id"], "participant": options["participant"],
             "origin": "ExplicitHuman", "idempotencyKey": options["idempotency-key"],
             "selectionCollection": options["selection-collection"]}
    return command_prefix(primary, explicit_path(options["host"])) + [
        "org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareRepositoryStart",
        "--input", json.dumps(value, ensure_ascii=False), "--json"], None


def prepare_split_input(options, primary):
    """Forward the nine generated fields without acquiring native-owned content."""
    value = {"componentInstance": options["component-instance"]}
    for field, option in (("allocationReference", "allocation-reference"), ("allocation", "allocation"),
                          ("source", "source"), ("reference", "reference")):
        value[field] = existing_file(explicit_path(options[option])).as_uri()
    value.update(participant=options["participant"], issuer=options["issuer"],
                 occurredAt=options["occurred-at"], collection=options["collection"])
    return command_prefix(primary, explicit_path(options["host"])) + [
        "org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareSplitStart",
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


def review_input_command(options, primary, session_id, family="GoalPhase"):
    """Forward whole opaque files to the normal typed preparation Operation."""
    value = {"family": family, "kind": "review",
             "request": existing_file(explicit_path(options["request"])).as_uri(),
             "result": existing_file(explicit_path(options["result"])).as_uri(),
             "reference": existing_file(explicit_path(options["reference"])).as_uri(),
             "executionEvidence": existing_file(explicit_path(options["execution-evidence"])).as_uri(),
             "issuer": options["issuer"], "occurredAt": options["occurred-at"],
             "collection": options["collection"]}
    return command_prefix(primary, explicit_path(options["host"])) + [
        "org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareEvidence",
        "--input", json.dumps(value, ensure_ascii=False), "--json"], session_id


def issued_repository_repair_route(options, task_fd):
    """Admit the fixed repair pair under its retained Goal or Repository owner."""
    return issued_repository_route(options, task_fd, "submit-repository-repair")


def prepare_review_input(options, primary, task_fd):
    """Keep original review preparation strictly on the outer Goal route."""
    _operation, session_id = issued_goal_route(options, task_fd)
    return review_input_command(options, primary, session_id)


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


def execution_command(options, primary, task_fd):
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
    return command_prefix(primary, explicit_path(options["host"])) + [
        "org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService." + operation,
        "--input", json.dumps(value, ensure_ascii=False), "--json"], session_id


def action_command(options, primary, host, task_fd):
    action = options["action"]
    if action in {"prepare-execution", "record-execution"}:
        return execution_command(options, primary, task_fd)
    if action == "prepare-repository-input":
        return prepare_repository_input(options, primary)
    if action == "prepare-goal-input":
        return prepare_goal_input(options, primary)
    if action == "prepare-split-input":
        return prepare_split_input(options, primary)
    if action == "prepare-review-input":
        return prepare_review_input(options, primary, task_fd)
    if action == "prepare-repository-review-input":
        route = route_record(task_fd)
        _operation, session_id = issued_repository_route(options, task_fd, "submit-repository-review", route)
        return review_input_command(options, primary, session_id, route["family"])
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
    command = command_prefix(primary, host) + [service + operation]
    return command + pairs + ["--json"], session_id


def native_call(command, host, output_fd, timeout):
    """One inherited-environment argv call; subprocess.run reaps its timeout."""
    with new_file(output_fd, "response.json") as stdout:
        with new_file(output_fd, "stderr.log") as stderr:
            result = subprocess.run(command, cwd=str(host), stdout=stdout, stderr=stderr, timeout=timeout)
    require(result.returncode == 0, "native process exited unsuccessfully; retain output")
    for name, limit in (("response.json", RESPONSE_LIMIT), ("stderr.log", STDERR_LIMIT)):
        require(os.stat(name, dir_fd=output_fd, follow_symlinks=False).st_size <= limit,
                "native output exceeds its bound; retain output")
    descriptor = os.open("response.json", os.O_RDONLY | os.O_NOFOLLOW, dir_fd=output_fd)
    with os.fdopen(descriptor, "r", encoding="utf-8") as stream:
        text = stream.read(RESPONSE_LIMIT + 1)
    return text, json_object(text)


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
                  "prepare-repository-review-input", "prepare-split-input"}:
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
    primary, host, task, output = (explicit_path(options[name])
                                   for name in ("primary", "host", "task", "output"))
    require(task.parent == host / "target/sm-workflow/tasks", "task must be a direct owned host task")
    require(output != task and task in output.parents, "output must be beneath the selected task")
    with selected_directory(primary), selected_directory(host):
        for path in (primary / "bin/launcher", host / ".textus/config.json", host / ".textus/assembly.yaml"):
            existing_file(path)
        for name in ("runtime-classpath.txt", "component-descriptor.json", "car-runtime-manifest.json"):
            existing_file(primary / "target/cncf.d" / name)
        with selected_directory(task) as task_fd, selected_directory(output) as output_fd:
            require(not os.listdir(output_fd), "output directory must be empty")
            command, session_id = action_command(options, primary, host, task_fd)
            text, response = native_call(command, host, output_fd,
                                         240 if options["action"] in {"advance", "advance-split", "advance-repository"} else 60)
            acknowledge(options["action"], response, session_id, task_fd, output_fd)
            sys.stdout.write(text)


def main(argv=None):
    """Return 0 only for the selected actual acknowledgement; errors return 2."""
    try:
        execute(parse_options(list(sys.argv[1:] if argv is None else argv)))
        return 0
    except CallerError as error:
        diagnostic = str(error)
    except subprocess.TimeoutExpired:
        diagnostic = "native process timed out; retain output and stop"
    except (OSError, ValueError, RecursionError, TypeError):
        diagnostic = "invalid file, JSON or response contract; retain available output and stop"
    safe = "".join(c for c in diagnostic if c.isprintable())[:512]
    print("native-goal-command: " + safe, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
