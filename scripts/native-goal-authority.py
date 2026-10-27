# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""One passive preparation for the selected retained Goal closing Wait.

PHASE-6 / S6-01 / S6-01D3J; native authority admission remains Scala-owned.
@since   Oct. 14, 2026
@version Oct. 15, 2026
@version Oct. 25, 2026
@author  ASAMI, Tomoharu
"""

import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys


SOURCE = Path(__file__).parent / "native-goal-command.py"
SPEC = importlib.util.spec_from_file_location("native_goal_command_authority_utilities", SOURCE)
COMMAND = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(COMMAND)

OPTIONS = {"executable", "runtime-root", "task", "output", "request", "reference",
           "participant", "issuer", "occurred-at", "collection"}


def parse_options(argv):
    """Require every literal pair without actions, defaults or alias flags."""
    COMMAND.require(len(argv) % 2 == 0, "arguments must be named value pairs")
    options = {}
    for key, value in zip(argv[::2], argv[1::2]):
        COMMAND.require(isinstance(key, str) and key.startswith("--"), "expected named argument")
        name = key[2:]
        COMMAND.require(name in OPTIONS and name not in options, "unknown or duplicate argument")
        COMMAND.require(COMMAND.nonblank(value) and value == value.strip()
                        and not any(ord(c) < 32 or ord(c) == 127 for c in value),
                        "argument must be nonblank exact text")
        options[name] = value
    COMMAND.require(options.keys() == OPTIONS, "missing required argument")
    COMMAND.require(re.fullmatch(
        r"entity:[A-Za-z][A-Za-z0-9_]*\.[A-Za-z][A-Za-z0-9_]*\.[A-Za-z][A-Za-z0-9_]*",
        options["collection"]) is not None, "collection must be a strict dotted entity address")
    return options


def preparation_command(options, executable, task_fd):
    """Check only selected routing; forward opaque authority files unchanged."""
    session_id = COMMAND.routing(task_fd)
    _text, request = COMMAND.read_object(COMMAND.explicit_path(options["request"]))
    COMMAND.require(COMMAND.native_field(request, "sessionId") == session_id
                    and request.get("kind") == "Wait", "request must match the selected Goal task Wait")
    value = {"request": COMMAND.existing_file(COMMAND.explicit_path(options["request"])).as_uri(),
             "reference": COMMAND.existing_file(COMMAND.explicit_path(options["reference"])).as_uri(),
             "participant": options["participant"], "issuer": options["issuer"],
             "occurredAt": options["occurred-at"], "collection": options["collection"]}
    return COMMAND.command_prefix(executable, COMMAND.explicit_path(options["runtime-root"])) + [
        "org.simplemodeling.textus.SmWorkflow.NativeWorkflowPreparationService.prepareAuthority",
        "--input", json.dumps(value, ensure_ascii=False), "--json"]


def execute(options):
    executable, runtime_root, task, output = (COMMAND.explicit_path(options[name])
                                   for name in ("executable", "runtime-root", "task", "output"))
    COMMAND.require(task.parent == runtime_root / "target/sm-workflow/tasks", "task must be a direct owned runtime_root task")
    COMMAND.require(output != task and task in output.parents, "output must be beneath the selected task")
    COMMAND.installed_executable(executable)
    with COMMAND.selected_directory(runtime_root):
        with COMMAND.selected_directory(task) as task_fd, COMMAND.selected_output_directory(task_fd, task, output) as output_fd:
            command = preparation_command(options, executable, task_fd)
            context = COMMAND.exchange_context(command, runtime_root, output, COMMAND.routing(task_fd))
            text, response = COMMAND.native_call(command, runtime_root, output_fd, context)
            COMMAND.require(isinstance(response.get("datastore"), list), "preparation must return a datastore array")
            COMMAND.response_progress(response, context)
            sys.stdout.write(text)


def main(argv=None):
    """Return two on failure with retained streams and one bounded diagnostic."""
    try:
        execute(parse_options(list(sys.argv[1:] if argv is None else argv)))
        return 0
    except COMMAND.CallerError as error:
        diagnostic = str(error)
    except (OSError, ValueError, RecursionError, TypeError):
        diagnostic = "invalid file, JSON or response contract; retain available output and stop"
    safe = "".join(c for c in diagnostic if c.isprintable())[:512]
    print("native-goal-authority: " + safe, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
