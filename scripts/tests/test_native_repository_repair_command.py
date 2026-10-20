# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""P1 / R6 finite repair caller examples; mocked subprocess is no native proof.
@since   Oct. 15, 2026
@version Oct. 15, 2026
"""

from contextlib import redirect_stderr, redirect_stdout
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

SOURCE = Path(__file__).resolve().parents[1] / "native-goal-command.py"
SPEC = importlib.util.spec_from_file_location("native_repository_repair_caller", SOURCE)
CALLER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CALLER)


class NativeRepositoryRepairCommandSpecification(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.executable = self.root / "installed CLI" / "sm-workflow"
        self.host = self.root / "host"
        self.task = self.host / "target/sm-workflow/tasks/owned"
        self.task.mkdir(parents=True)
        for path in (self.executable, self.host / ".textus/config.json", self.host / ".textus/assembly.yaml"):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("Specification prerequisite", encoding="utf-8")
        self.executable.chmod(0o755)
        self.route = self.task / "workflow-session.json"
        self.route.write_text(json.dumps({"family": "GoalPhase", "sessionId": "outer"}))
        self.request = self.root / "request.json"
        self.submission = self.root / "submission.json"
        self.value = {"result": {"decision": "repaired", "rationale": "Supplied repair",
                                  "replacements": [{"path": "shared.txt", "text": "日本語\n"}]},
                      "resultReference": {"identity": "supplied-artifact", "revision": "1"},
                      "completionFacts": [{"supplied": "fact"}],
                      "executionEvidence": {"workerIdentity": "supplied-worker"}}
        self.submission.write_text(json.dumps(self.value, ensure_ascii=False))
        self.counter = 0

    def request_value(self):
        return {"sessionId": "outer", "kind": "Request", "submitSessionId": "child",
                "submitOperation": "submitNativeMergeRepair", "resultType": "RepositorySyncNativeMergeRepairResult",
                "repositoryRepair": {"inert": "native-owned business input"}, "argv": ["ignored"]}

    def args(self, action="submit-repository-repair"):
        self.counter += 1
        self.output = self.task / ("call-" + str(self.counter))
        self.output.mkdir()
        return ["--executable", str(self.executable), "--host", str(self.host), "--task", str(self.task),
                "--action", action, "--output", str(self.output), "--request", str(self.request),
                "--submission", str(self.submission)]

    def call(self, args, response=None, code=0, timeout=False):
        raw = json.dumps(response or {"sessionId": "child", "accepted": True,
                                      "outcome": "accepted", "message": "Native accepted"},
                         ensure_ascii=False).encode("utf-8")
        def process(command, **kwargs):
            kwargs["stdout"].write(raw)
            kwargs["stderr"].write(b"native diagnostic\n")
            if timeout:
                raise OSError("fixture runtime disconnected")
            return subprocess.CompletedProcess(command, code)
        out, err = io.StringIO(), io.StringIO()
        native = Mock(side_effect=process)

        def fixture(command, host, descriptor, context):
            # A route-only native boundary supplies raw observations. Real
            # process ownership/interruption is covered by transport scenarios.
            CALLER.progress("invocation", context)
            with CALLER.new_file(descriptor, "response.json") as stdout:
                with CALLER.new_file(descriptor, "stderr.log") as stderr:
                    result = native(command, cwd=str(host), stdout=stdout, stderr=stderr)
            CALLER.require(result.returncode == 0, "native process exited unsuccessfully; retain output")
            for name, limit in (("response.json", CALLER.RESPONSE_LIMIT), ("stderr.log", CALLER.STDERR_LIMIT)):
                CALLER.require(CALLER.os.stat(name, dir_fd=descriptor, follow_symlinks=False).st_size <= limit,
                               "native output exceeds its bound; retain output")
            with CALLER.os.fdopen(CALLER.os.open("response.json", CALLER.os.O_RDONLY | CALLER.os.O_NOFOLLOW,
                                               dir_fd=descriptor), "r", encoding="utf-8") as stream:
                text = stream.read(CALLER.RESPONSE_LIMIT + 1)
            return text, CALLER.json_object(text)


        with patch.object(CALLER, "native_call", side_effect=fixture), patch.object(CALLER, "progress"):
            with redirect_stdout(out), redirect_stderr(err):
                status = CALLER.main(args)
        return status, out.getvalue(), err.getvalue(), native, raw

    def test_p1_complete_child_submit_preserves_opaque_values_and_outer_route(self):
        # Given P1/R6 an issued repair child with opaque Unicode rationale and replacement text.
        self.value["result"]["rationale"] = "修復の理由 — 😀"
        self.value["result"]["replacements"][0]["text"] = "修復 — 😀\n"
        self.submission.write_text(json.dumps(self.value, ensure_ascii=False), encoding="utf-8")
        self.request.write_text(json.dumps(self.request_value()))
        before = self.route.read_bytes()
        # When the dedicated action performs its single native Submit and receives a Unicode acknowledgement.
        response = {"sessionId": "child", "accepted": True, "outcome": "accepted",
                    "message": "修復を受理 — 😀"}
        status, out, err, native, raw = self.call(self.args(), response=response)
        # Then UTF8 output and opaque named values survive on the child FQN while the outer route remains.
        self.assertEqual((status, out, err), (0, raw.decode("utf-8"), ""))
        self.assertEqual(json.loads(out)["message"], response["message"])
        self.assertEqual((self.output / "response.json").read_bytes(), raw)
        native.assert_called_once()
        argv, kwargs = native.call_args
        command = argv[0]
        self.assertEqual(command[:4], [str(self.executable), "--host", str(self.host), "command"])
        self.assertIn("org.simplemodeling.textus.SmWorkflow.RepositorySyncService.submitNativeMergeRepair", command)
        self.assertEqual(command[command.index("--sessionId") + 1], "child")
        for name, value in self.value.items():
            self.assertEqual(json.loads(command[command.index("--" + name) + 1]), value)
        self.assertEqual(command[-1], "--json")
        self.assertEqual(kwargs["cwd"], str(self.host))
        self.assertEqual(self.route.read_bytes(), before)

    def test_p1_aliases_and_inert_annotations_keep_the_same_child(self):
        for spelling in ("snake", "matching-both"):
            with self.subTest(spelling=spelling):
                # Given P1/R6 normal aliases and inert root/nested annotations.
                value = self.request_value()
                for camel, snake in (("sessionId", "session_id"), ("submitSessionId", "submit_session_id"),
                                     ("submitOperation", "submit_operation"), ("resultType", "result_type")):
                    value[snake] = value[camel]
                    if spelling == "snake":
                        del value[camel]
                value["inertDiagnostic"] = {"permission": "ignored"}
                self.request.write_text(json.dumps(value))
                # When the finite caller consumes only its route fields.
                status, _out, _err, native, _raw = self.call(self.args())
                # Then aliases do not become another route or permission.
                self.assertEqual(status, 0)
                native.assert_called_once()

    def test_p1_crossed_missing_and_wrong_routes_refuse_before_launch(self):
        changes = [{"sessionId": "foreign"}, {"submitSessionId": "outer"}, {"submitSessionId": " "},
                   {"submitSessionId": 3}, {"kind": "Wait"}, {"kind": "Human"}, {"kind": "Terminal"},
                   {"submitOperation": "submitNativeMergeReview"}, {"resultType": "GoalPhaseWorkResult"},
                   {"session_id": "foreign"}, {"submit_session_id": "foreign"},
                   {"submit_operation": "other"}, {"result_type": "other"}]
        cases = [dict(self.request_value(), **change) for change in changes]
        cases += [{k: v for k, v in self.request_value().items() if k != missing}
                  for missing in ("sessionId", "submitSessionId", "submitOperation", "resultType", "kind")]
        for value in cases:
            with self.subTest(value=value):
                # Given P1/R6 a crossed, missing or contradictory consumed route.
                self.request.write_text(json.dumps(value))
                before = self.route.read_bytes()
                # When repair Submit consumes it.
                status, out, err, native, _raw = self.call(self.args())
                # Then refusal occurs before output or process allocation.
                self.assertEqual(status, 2)
                self.assertEqual(out, "")
                self.assertTrue(err)
                native.assert_not_called()
                self.assertEqual(list(self.output.iterdir()), [])
                self.assertEqual(self.route.read_bytes(), before)

    def test_p1_exact_action_pairs_and_crossed_original_actions_refuse(self):
        for action in ("submit", "submit-repository-review", "unknown-action"):
            # Given P1/R6 a repair assignment with another selected action.
            self.request.write_text(json.dumps(self.request_value()))
            # When that original or unknown action consumes the repair route.
            status, _out, _err, native, _raw = self.call(self.args(action))
            # Then no native route is guessed.
            self.assertEqual(status, 2)
            native.assert_not_called()
        for change in ("missing", "duplicate", "extra", "unpaired"):
            # Given P1/R6 a missing/duplicate/inapplicable/unpaired explicit argument.
            args = self.args()
            if change == "missing":
                del args[-2:]
            elif change == "duplicate":
                args += ["--request", str(self.request)]
            elif change == "extra":
                args += ["--input", str(self.request)]
            else:
                args += ["--request"]
            # When the finite parser consumes those named pairs.
            status, _out, _err, native, _raw = self.call(args)
            # Then there is no default route or native call.
            self.assertEqual(status, 2)
            native.assert_not_called()

    def test_p1_acknowledgement_requires_the_actual_child(self):
        responses = [{"sessionId": session, "accepted": accepted, "outcome": outcome, "message": message}
                     for session, accepted, outcome, message in (("outer", True, "accepted", "ok"),
                         ("foreign", True, "accepted", "ok"), ("child", False, "accepted", "ok"),
                         ("child", True, "rejected", "ok"), ("child", True, "accepted", " "))]
        for response in responses:
            # Given P1/R6 a legitimate issued child and a mismatched native acknowledgement.
            self.request.write_text(json.dumps(self.request_value()))
            before = self.route.read_bytes()
            # When the single native call returns that acknowledgement.
            status, out, _err, native, raw = self.call(self.args(), response=response)
            # Then it stops, retains output and leaves the outer route unchanged.
            self.assertEqual(status, 2)
            self.assertEqual(out, "")
            native.assert_called_once()
            self.assertEqual((self.output / "response.json").read_bytes(), raw)
            self.assertEqual(self.route.read_bytes(), before)

    def test_p1_native_failure_and_disconnection_never_make_an_extra_call(self):
        for code, timeout in ((2, False), (0, True)):
            # Given P1/R6 an actual-shape assignment and a native failure/disconnection.
            self.request.write_text(json.dumps(self.request_value()))
            before = self.route.read_bytes()
            # When the one selected process fails.
            status, out, err, native, raw = self.call(self.args(), code=code, timeout=timeout)
            # Then no retry or Advance follows and original streams/routing survive.
            self.assertEqual(status, 2)
            self.assertEqual(out, "")
            self.assertTrue(err)
            native.assert_called_once()
            self.assertEqual((self.output / "response.json").read_bytes(), raw)
            self.assertEqual((self.output / "stderr.log").read_bytes(), b"native diagnostic\n")
            self.assertEqual(self.route.read_bytes(), before)


if __name__ == "__main__":
    unittest.main()
