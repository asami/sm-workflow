# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""One passive preparation for the selected retained Goal closing Wait.

PHASE-6 / S6-01 / S6-01D3J; native authority admission remains Scala-owned.
@since   Oct. 14, 2026
@version Oct. 15, 2026
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

OPTIONS = {"primary", "host", "task", "output", "request", "reference",
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


def preparation_command(options, primary, task_fd):
    """Check only selected routing; forward opaque authority files unchanged."""
    session_id = COMMAND.routing(task_fd)
    _text, request = COMMAND.read_object(COMMAND.explicit_path(options["request"]))
    COMMAND.require(COMMAND.native_field(request, "sessionId") == session_id
                    and request.get("kind") == "Wait", "request must match the selected Goal task Wait")
    value = {"request": COMMAND.existing_file(COMMAND.explicit_path(options["request"])).as_uri(),
             "reference": COMMAND.existing_file(COMMAND.explicit_path(options["reference"])).as_uri(),
             "participant": options["participant"], "issuer": options["issuer"],
             "occurredAt": options["occurred-at"], "collection": options["collection"]}
    return COMMAND.command_prefix(primary, COMMAND.explicit_path(options["host"])) + [
        "org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareAuthority",
        "--input", json.dumps(value, ensure_ascii=False), "--json"]


def execute(options):
    primary, host, task, output = (COMMAND.explicit_path(options[name])
                                   for name in ("primary", "host", "task", "output"))
    COMMAND.require(task.parent == host / "target/sm-workflow/tasks", "task must be a direct owned host task")
    COMMAND.require(output != task and task in output.parents, "output must be beneath the selected task")
    with COMMAND.selected_directory(primary), COMMAND.selected_directory(host):
        for path in (primary / "bin/launcher", host / ".textus/config.json", host / ".textus/assembly.yaml"):
            COMMAND.existing_file(path)
        for name in ("runtime-classpath.txt", "component-descriptor.json", "car-runtime-manifest.json"):
            COMMAND.existing_file(primary / "target/cncf.d" / name)
        with COMMAND.selected_directory(task) as task_fd, COMMAND.selected_directory(output) as output_fd:
            COMMAND.require(not os.listdir(output_fd), "output directory must be empty")
            command = preparation_command(options, primary, task_fd)
            text, response = COMMAND.native_call(command, host, output_fd, 60)
            COMMAND.require(isinstance(response.get("datastore"), list), "preparation must return a datastore array")
            sys.stdout.write(text)


def main(argv=None):
    """Return two on failure with retained streams and one bounded diagnostic."""
    try:
        execute(parse_options(list(sys.argv[1:] if argv is None else argv)))
        return 0
    except COMMAND.CallerError as error:
        diagnostic = str(error)
    except subprocess.TimeoutExpired:
        diagnostic = "native process timed out; retain output and stop"
    except (OSError, ValueError, RecursionError, TypeError):
        diagnostic = "invalid file, JSON or response contract; retain available output and stop"
    safe = "".join(c for c in diagnostic if c.isprintable())[:512]
    print("native-goal-authority: " + safe, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
