# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Finite passive authority caller examples for PHASE-6 / S6-01 / S6-01D3J.

Temporary files and mocked subprocess streams establish no genuine task,
operator authority, provisioning, permission or acceptance. Direct execution
discovers both native Goal caller suites under this tests directory.
@since   Oct. 14, 2026
@version Oct. 15, 2026
@author  ASAMI, Tomoharu
"""

from contextlib import redirect_stderr, redirect_stdout
import importlib.util
import io
import json
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


SOURCE = Path(__file__).resolve().parents[1] / "native-goal-authority.py"
SPEC = importlib.util.spec_from_file_location("native_goal_authority", SOURCE)
CALLER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CALLER)


class NativeGoalAuthoritySpecification(unittest.TestCase):
    """Leaves bind named examples and R1–R6 to adjacent Given/When/Then."""

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.primary = self.root / "primary"
        self.host = self.root / "host"
        self.task = self.host / "target/sm-workflow/tasks/selected-task"
        self.task.mkdir(parents=True)
        self.prerequisites = (
            self.primary / "bin/launcher",
            self.primary / "target/cncf.d/runtime-classpath.txt",
            self.primary / "target/cncf.d/component-descriptor.json",
            self.primary / "target/cncf.d/car-runtime-manifest.json",
            self.host / ".textus/config.json",
            self.host / ".textus/assembly.yaml",
        )
        for path in self.prerequisites:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("specification prerequisite", encoding="utf-8")
        self.request = self.root / "closing-wait.json"
        self.reference = self.root / "operator-reference.json"
        self.reference.write_text('{ "identity": "explicit reference", "revision": "7" }\n')
        self.output_index = 0

    def retain(self, task=None, session_id="selected-goal"):
        route = (task or self.task) / "workflow-session.json"
        route.write_text(json.dumps({"family": "GoalPhase", "sessionId": session_id}), encoding="utf-8")
        return route

    def wait(self, session_id="selected-goal", spelling="session_id"):
        return {spelling: session_id, "kind": "Wait", "message": "Specification closing Wait",
                "authority_context": {"native_owned": {"opaque": True}}}

    def write_request(self, value):
        raw = value if isinstance(value, bytes) else json.dumps(value).encode("utf-8")
        self.request.write_bytes(raw)

    def output(self, task=None):
        self.output_index += 1
        output = (task or self.task) / ("call-" + str(self.output_index))
        output.mkdir()
        return output

    def args(self, output, task=None):
        return ["--primary", str(self.primary), "--host", str(self.host),
                "--task", str(task or self.task), "--output", str(output),
                "--request", str(self.request), "--reference", str(self.reference),
                "--participant", "explicit operator", "--issuer", "explicit host issuer",
                "--occurred-at", "2026-10-14T06:07:08.123Z",
                "--collection", "entity:Explicit.Host.authority_input"]

    def command(self, argv):
        def option(name):
            return argv[argv.index("--" + name) + 1]
        value = {"request": Path(option("request")).as_uri(), "reference": Path(option("reference")).as_uri(),
                 "participant": option("participant"), "issuer": option("issuer"),
                 "occurredAt": option("occurred-at"), "collection": option("collection")}
        return [str(self.primary / "bin/launcher"), "--classpath-file",
                str(self.primary / "target/cncf.d/runtime-classpath.txt"), "--main-class",
                "org.goldenport.cncf.CncfMain", "--java-opt", "-Dstdout.encoding=UTF-8", "--", "--force-exit",
                "--component-dev-dir", str(self.primary),
                "--textus.assembly.descriptor=" + str(self.host / ".textus/assembly.yaml"),
                "command", "org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareAuthority",
                "--input", json.dumps(value, ensure_ascii=False), "--json"]

    def process_output(self, response, stderr=b"", returncode=0, timeout=False):
        raw = response if isinstance(response, bytes) else (json.dumps(response) + "\n").encode("utf-8")

        def invoke(command, **kwargs):
            kwargs["stdout"].write(raw)
            kwargs["stderr"].write(stderr)
            if timeout:
                raise subprocess.TimeoutExpired(command, kwargs["timeout"])
            return subprocess.CompletedProcess(command, returncode)

        return invoke, raw

    def call(self, argv, process):
        printed, diagnostic = io.StringIO(), io.StringIO()
        with patch.object(CALLER.COMMAND.subprocess, "run", side_effect=process) as native:
            with redirect_stdout(printed), redirect_stderr(diagnostic):
                status = CALLER.main(argv)
        return status, printed.getvalue(), diagnostic.getvalue(), native

    def assert_one_call(self, native, command):
        native.assert_called_once()
        args, kwargs = native.call_args
        self.assertEqual(args, (command,))
        self.assertEqual(set(kwargs), {"cwd", "stdout", "stderr", "timeout"})
        self.assertEqual(kwargs["cwd"], str(self.host))
        self.assertEqual(kwargs["timeout"], 60)
        self.assertTrue(kwargs["stdout"].closed)
        self.assertTrue(kwargs["stderr"].closed)

    def test_a1_selected_wait_exact_argv_and_passive_output(self):
        """A1 / R1,R2,R3,R4,R6 / native-host-authority-preparation; exact passive forwarding."""
        for spelling in ("sessionId", "session_id", "equal-dual"):
            with self.subTest(spelling=spelling):
                # Given a matching selected Wait and explicit files/metadata with opaque native context.
                route = self.retain()
                request = self.wait(spelling="sessionId" if spelling == "equal-dual" else spelling)
                if spelling == "equal-dual":
                    request["session_id"] = request["sessionId"]
                self.write_request(request)
                files = (route, self.request, self.reference, *self.prerequisites)
                before = {path: path.read_bytes() for path in files}
                output = self.output()
                argv = self.args(output)
                process, raw = self.process_output({"datastore": [], "native_owned_display": {"kept": True}},
                                                   b"native warning\n")
                # When the finite caller performs its single inherited-environment normal typed preparation invocation.
                status, printed, diagnostic, native = self.call(argv, process)
                # Then exactly one concrete typed input and the actual response survive with routing/config untouched.
                self.assertEqual(status, 0)
                self.assertEqual(printed, raw.decode("utf-8"))
                self.assertEqual(diagnostic, "")
                self.assert_one_call(native, self.command(argv))
                self.assertEqual({path: path.read_bytes() for path in files}, before)
                self.assertEqual(set(path.name for path in output.iterdir()), {"response.json", "stderr.log"})
                self.assertEqual((output / "response.json").read_bytes(), raw)
                self.assertEqual((output / "stderr.log").read_bytes(), b"native warning\n")
                for name in ("response.json", "stderr.log"):
                    self.assertEqual(stat.S_IMODE((output / name).stat().st_mode), 0o600)

    def test_a1_opaque_unicode_reference_uri(self):
        """A1 / R1,R2,R3,R4,R6 / standard typed authority input with native-owned reference semantics."""
        # Given a selected matching Wait and an opaque reference at a Unicode file name.
        self.retain()
        self.write_request(self.wait())
        self.reference = self.root / "明示 承認参照.json"
        self.reference.write_bytes(b"opaque reference owned by native decoder")
        before = self.reference.read_bytes()
        output = self.output()
        argv = self.args(output)
        process, _raw = self.process_output({"datastore": []})
        # When the finite authority caller constructs its one ordinary command.
        status, _printed, diagnostic, native = self.call(argv, process)
        # Then one typed input carries exact file URIs and leaves native semantic bytes untouched.
        self.assertEqual(status, 0)
        self.assertEqual(diagnostic, "")
        self.assert_one_call(native, self.command(argv))
        command = native.call_args.args[0]
        self.assertEqual(command.count("--input"), 1)
        value = json.loads(command[command.index("--input") + 1])
        self.assertEqual(value["request"], self.request.as_uri())
        self.assertEqual(value["reference"], self.reference.as_uri())
        self.assertEqual(self.reference.read_bytes(), before)

    def test_a2_crossed_nonwait_and_contradictory_request_refusal(self):
        """A2 / R1,R4,R6 / native-host-authority-preparation; selected task identity and Wait."""
        cases = [self.wait("foreign-goal"), self.wait("foreign-goal", "sessionId"),
                 {**self.wait(), "sessionId": "foreign-goal"}, {"kind": "Wait"},
                 {**self.wait(), "session_id": None}, {**self.wait(), "session_id": True}]
        cases.extend({**self.wait(), "kind": kind} for kind in ("Request", "Human", "Terminal", "wait", None))
        for request in cases:
            with self.subTest(request=request):
                # Given a crossed, contradictory or non-Wait request and an exact retained Goal route.
                route = self.retain()
                retained = route.read_bytes()
                self.write_request(request)
                output = self.output()
                process, _raw = self.process_output({"datastore": []})
                # When only the selected routing fields are consumed before invoking native preparation.
                status, printed, diagnostic, native = self.call(self.args(output), process)
                # Then refusal precedes subprocess/output writes and preserves the route.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic)
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual(route.read_bytes(), retained)

    def test_a3_literal_flag_refusal(self):
        """A3 / R3,R4,R6 / native-host-authority-preparation; exact complete named pairs."""
        cases = [(kind, name) for kind in ("missing", "duplicate", "blank", "control")
                 for name in sorted(CALLER.OPTIONS)]
        cases.extend(("extra", name) for name in ("unknown", "action", "proposal", "result", "approval"))
        cases.extend((("unpaired", "request"), ("unnamed", "request")))
        for kind, name in cases:
            with self.subTest(case=kind, option=name):
                # Given one missing, duplicate, blank, control, unknown or action-inapplicable literal flag.
                route = self.retain()
                retained = route.read_bytes()
                self.write_request(self.wait())
                output = self.output()
                argv = self.args(output)
                if kind == "missing":
                    index = argv.index("--" + name)
                    del argv[index:index + 2]
                elif kind in {"duplicate", "extra"}:
                    argv.extend(["--" + name, "supplied-value"])
                elif kind in {"blank", "control"}:
                    argv[argv.index("--" + name) + 1] = " " if kind == "blank" else "supplied\nvalue"
                elif kind == "unnamed":
                    argv[argv.index("--" + name)] = name
                else:
                    argv.append("--" + name)
                process, _raw = self.process_output({"datastore": []})
                # When the authority caller parses the exact named pairs without defaults.
                status, printed, diagnostic, native = self.call(argv, process)
                # Then it fails before invocation or writes and leaves the task route unchanged.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic)
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual(route.read_bytes(), retained)

    def test_a4_collection_and_explicit_file_refusal(self):
        """A4 / R3,R4,R6 / native-host-authority-preparation; strict collection and explicit files."""
        cases = [("collection", value) for value in (
            "entity:smworkflow/cli/host_evidence", "entity:smworkflow.cli", "entity:smworkflow.cli.host.extra",
            "smworkflow.cli.host_evidence", "entity:1workflow.cli.host_evidence", "entity:smworkflow..host_evidence",
            "ENTITY:smworkflow.cli.host_evidence", "entity:smworkflow.cli.host-evidence")]
        for name in ("request", "reference"):
            cases.extend((name, value) for value in ("relative.json", str(self.root) + "/../absent.json",
                                                     str(self.root / "absent.json")))
        for name, value in cases:
            with self.subTest(option=name, value=value):
                # Given a malformed dotted collection or unavailable/relative/unnormalized explicit source file.
                route = self.retain()
                retained = route.read_bytes()
                self.write_request(self.wait())
                output = self.output()
                argv = self.args(output)
                argv[argv.index("--" + name) + 1] = value
                process, _raw = self.process_output({"datastore": []})
                # When the caller checks its supplied routing files and collection syntax.
                status, printed, diagnostic, native = self.call(argv, process)
                # Then native preparation is not invoked and the output and route remain untouched.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic)
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual(route.read_bytes(), retained)

    def test_a5_selected_directories_and_output_refusal(self):
        """A5 / R4,R6 / native-host-authority-preparation; selected symlink-free empty write directory."""
        for case in ("missing-output", "occupied-output", "symlink-output", "symlink-ancestor", "foreign-task",
                     "output-is-task", "foreign-output", "relative-primary", "unnormalized-host"):
            with self.subTest(case=case):
                # Given a missing/occupied/symlinked output or a path outside the exact selected host task.
                route = self.retain()
                retained = route.read_bytes()
                self.write_request(self.wait())
                output = self.output()
                argv = self.args(output)
                if case == "missing-output":
                    output.rmdir()
                elif case == "occupied-output":
                    (output / "retained.txt").write_text("preserved")
                elif case == "symlink-output":
                    output.rmdir()
                    destination = self.root / ("destination-" + str(self.output_index))
                    destination.mkdir()
                    output.symlink_to(destination, target_is_directory=True)
                elif case == "symlink-ancestor":
                    alias = self.task / ("alias-" + str(self.output_index))
                    alias.symlink_to(output, target_is_directory=True)
                    child = output / "nested"
                    child.mkdir()
                    argv[argv.index("--output") + 1] = str(alias / "nested")
                elif case == "foreign-task":
                    foreign = self.root / ("foreign-task-" + str(self.output_index))
                    foreign.mkdir()
                    argv[argv.index("--task") + 1] = str(foreign)
                elif case == "output-is-task":
                    argv[argv.index("--output") + 1] = str(self.task)
                elif case == "foreign-output":
                    argv[argv.index("--output") + 1] = str(self.root)
                elif case == "relative-primary":
                    argv[argv.index("--primary") + 1] = "primary"
                else:
                    argv[argv.index("--host") + 1] = str(self.host) + "/../host"
                process, _raw = self.process_output({"datastore": []})
                # When write-directory ownership and symlink-free prerequisites are checked.
                status, printed, diagnostic, native = self.call(argv, process)
                # Then refusal precedes the one native call and creates neither retained stream file.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic)
                native.assert_not_called()
                self.assertFalse((output / "response.json").exists())
                self.assertFalse((output / "stderr.log").exists())
                self.assertEqual(route.read_bytes(), retained)
                if case == "occupied-output":
                    self.assertEqual((output / "retained.txt").read_text(), "preserved")

    def test_a6_exact_routing_and_prerequisite_refusal(self):
        """A6 / R1,R4,R6 / native-host-authority-preparation; retained exact route and prepared launcher."""
        routes = (None, b"not-json", b"[]", b"{}", b'{"family":"SplitPhase","sessionId":"selected-goal"}',
                  b'{"family":"GoalPhase","sessionId":" "}',
                  b'{"family":"GoalPhase","sessionId":"selected-goal","session_id":"selected-goal"}',
                  b'{"family":"GoalPhase","sessionId":"selected-goal","sessionId":"other"}')
        cases = [("route", value) for value in routes] + [("prerequisite", path) for path in self.prerequisites]
        for kind, value in cases:
            with self.subTest(case=kind, value=value):
                # Given an absent/malformed/nonexact route or missing existing launcher/host prerequisite.
                route = self.retain()
                if kind == "route":
                    route.unlink()
                    if value is not None:
                        route.write_bytes(value)
                else:
                    value.unlink()
                retained = route.read_bytes() if route.exists() else None
                self.write_request(self.wait())
                output = self.output()
                process, _raw = self.process_output({"datastore": []})
                # When the passive caller rereads its selected route and existing prepared prerequisites.
                status, printed, diagnostic, native = self.call(self.args(output), process)
                # Then it refuses without invocation/writes or replacement of any selected state.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic)
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual(route.read_bytes() if route.exists() else None, retained)
                if kind == "prerequisite":
                    value.write_text("specification prerequisite", encoding="utf-8")

    def test_a7_native_failure_and_timeout_retain_streams_without_retry(self):
        """A7 / R4,R6 / native-host-authority-preparation; bounded strict response and failure retention."""
        cases = (
            ("nonzero", b'{"datastore":[]}\n', b"native diagnostic\n", 2, False),
            ("malformed", b"not-json", b"native diagnostic\n", 0, False),
            ("duplicate-key", b'{"datastore":[],"datastore":[]}', b"", 0, False),
            ("nonfinite", b'{"datastore":[],"value":NaN}', b"", 0, False),
            ("nonobject", b"[]", b"", 0, False),
            ("absent-datastore", b"{}", b"", 0, False),
            ("nonarray-datastore", b'{"datastore":{}}', b"", 0, False),
            ("oversized-response", b"x" * (CALLER.COMMAND.RESPONSE_LIMIT + 1), b"", 0, False),
            ("oversized-stderr", b'{"datastore":[]}', b"x" * (CALLER.COMMAND.STDERR_LIMIT + 1), 0, False),
            ("timeout", b"partial stdout", b"partial stderr", 0, True),
        )
        for name, response, stderr, returncode, timeout in cases:
            with self.subTest(case=name):
                # Given an unsuccessful, malformed, nonfinite, oversized or timed-out passive process.
                route = self.retain()
                self.write_request(self.wait())
                before = {path: path.read_bytes() for path in (route, self.request, self.reference, *self.prerequisites)}
                output = self.output()
                argv = self.args(output)
                process, raw = self.process_output(response, stderr, returncode, timeout)
                # When exactly one invocation produces its available actual streams and fails its response contract.
                status, printed, diagnostic, native = self.call(argv, process)
                # Then exit two retains both streams and state, prints no success JSON and never retries.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic.startswith("native-goal-authority: "))
                self.assertTrue(all(c.isprintable() for c in diagnostic.rstrip("\n")))
                self.assertLessEqual(len(diagnostic), len("native-goal-authority: ") + 513)
                self.assert_one_call(native, self.command(argv))
                self.assertEqual((output / "response.json").read_bytes(), raw)
                self.assertEqual((output / "stderr.log").read_bytes(), stderr)
                self.assertEqual({path: path.read_bytes() for path in before}, before)

    def test_a8_sequential_selected_tasks_read_their_own_ids(self):
        """A8 / R1,R4,R6 / native-host-authority-preparation; independent selected task routing."""
        # Given two separate exact host task routes and their distinct selected Wait identifiers.
        second = self.task.parent / "second-task"
        second.mkdir()
        routes = (self.retain(self.task, "first-goal"), self.retain(second, "second-goal"))
        before = {path: path.read_bytes() for path in routes}
        for task, session_id, spelling in ((self.task, "first-goal", "session_id"), (second, "second-goal", "sessionId")):
            self.write_request(self.wait(session_id, spelling))
            output = self.output(task)
            argv = self.args(output, task)
            process, raw = self.process_output({"datastore": [], "selected_fixture": session_id})
            # When each finite invocation rereads only that selected task's route and matching Wait file.
            status, printed, diagnostic, native = self.call(argv, process)
            # Then each task receives its own one-call output and both independent routes are preserved.
            self.assertEqual(status, 0)
            self.assertEqual(printed, raw.decode())
            self.assertEqual(diagnostic, "")
            self.assert_one_call(native, self.command(argv))
            self.assertEqual({path: path.read_bytes() for path in routes}, before)


if __name__ == "__main__":
    suite = unittest.TestLoader().discover(start_dir=str(Path(__file__).parent), pattern="test_native_goal_*.py")
    result = unittest.TextTestRunner().run(suite)
    sys.exit(0 if result.wasSuccessful() else 1)
