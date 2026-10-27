# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Behavioral examples for PHASE-6 / S6-01 / S6-01D3D E1–E7, S6-01D3F E8–E14,
S6-01D3S E15–E18, S6-01D4K E19–E23, S6-01D6T3 E24, S6-01D10 E25–E29,
S6-01D14 E30–E33, S6-01D15 E34–E40 and S6-01D16 E41–E47.

Temporary files and mocked native output are specification data, never actual
task execution, authenticated selection, consent, installed work or acceptance.
Route examples supply a native-call boundary fixture and capture telemetry separately.
Native payload semantics,
permissions and provenance remain with their existing owners.
@since   Oct. 14, 2026
@version Oct. 15, 2026
@version Oct. 16, 2026
@author  ASAMI, Tomoharu
"""

from contextlib import redirect_stderr, redirect_stdout
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import stat
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch


SOURCE = Path(__file__).resolve().parents[1] / "native-goal-command.py"
SPEC = importlib.util.spec_from_file_location("native_goal_command", SOURCE)
CALLER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CALLER)


class NativeGoalCommandSpecification(unittest.TestCase):
    """Each leaf binds adjacent Given/When/Then to a frozen example and rule."""

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.executable = self.root / "installed CLI" / "sm-workflow"
        self.runtime_root = self.root / "runtime_root"
        self.task = self.runtime_root / "target/sm-workflow/tasks/selected-task"
        self.task.mkdir(parents=True)
        for path in (
            self.executable,
            self.runtime_root / ".textus/config.json",
            self.runtime_root / ".textus/assembly.yaml",
        ):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("specification prerequisite", encoding="utf-8")
        self.executable.chmod(0o755)
        self.input = self.write_json("selected-input.json", {
            "phaseIdentity": "phase-6", "repositoryIdentity": "sm-workflow",
            "nativeOwnedField": {"kept": True},
        })
        self.plan = self.write_json("selected-plan.json", {"identity": "explicit-plan"})
        self.request = self.write_json("issued-request.json", self.issued_request())
        self.submission_value = {
            "result": {"outcome": "completed", "summary": "Supplied semantic work", "findings": []},
            "resultReference": {"artifactIdentity": "supplied-artifact", "artifactRevision": "1"},
        }
        self.submission = self.write_json("supplied-submission.json", self.submission_value)
        self.review_result = self.write_json("review-result.json", {
            "outcome": "accept", "summary": "Specification review", "findings": [],
        })
        self.review_reference = self.write_json("review-reference.json", {
            "identity": "supplied-review", "revision": "1",
        })
        self.review_execution = self.write_json("review-execution.json", {
            "workerIdentity": "supplied-reviewer",
        })
        self.output_index = 0

    def write_json(self, name, value):
        path = self.root / name
        path.write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")
        return path

    def retain(self, task=None, session_id="actual-goal"):
        path = (task or self.task) / "workflow-session.json"
        path.write_text(json.dumps({"family": "GoalPhase", "sessionId": session_id}), encoding="utf-8")
        return path

    def output(self, task=None):
        self.output_index += 1
        path = (task or self.task) / ("call-" + str(self.output_index))
        path.mkdir()
        return path

    def args(self, action, output, *pairs, task=None):
        return ["--executable", str(self.executable), "--runtime-root", str(self.runtime_root),
                "--task", str(task or self.task), "--action", action,
                "--output", str(output), *pairs]

    def preparation_args(self, output):
        return self.args("prepare-goal-input", output,
                         "--input", str(self.input), "--plan", str(self.plan),
                         "--selection-id", "explicit-selection", "--participant", "asami",
                         "--idempotency-key", "explicit-key",
                         "--selection-collection", "entity:sm.Workflow.goal_selection",
                         "--plan-collection", "entity:sm.Workflow.goal_plan")

    def repository_args(self, output):
        return self.args("prepare-repository-input", output,
                         "--input", str(self.input), "--selection-id", "repository-selection",
                         "--participant", "asami", "--idempotency-key", "repository-key",
                         "--selection-collection", "entity:smworkflow.cli.repository_selections")

    def preparation_command(self, operation, value):
        return [str(self.executable), "--runtime-root", str(self.runtime_root),
                "command", "org.simplemodeling.textus.SmWorkflow.NativeWorkflowPreparationService." + operation,
                "--input", json.dumps(value, ensure_ascii=False), "--json"]

    def repository_command(self, argv):
        def option(name):
            return argv[argv.index("--" + name) + 1]
        return self.preparation_command("prepareRepositoryStart", {
            "input": Path(option("input")).as_uri(), "selectionId": option("selection-id"),
            "participant": option("participant"), "origin": "ExplicitHuman",
            "idempotencyKey": option("idempotency-key"), "selectionCollection": option("selection-collection")})

    def issued_request(self, session_id="actual-goal"):
        return {"sessionId": session_id, "kind": "Request", "message": "Issued semantic work",
                "work": {"kind": "ImplementStep", "purpose": "Supplied purpose"},
                "submitOperation": "submitGoalPhaseWorkResult", "submitSessionId": session_id,
                "resultType": "GoalPhaseWorkResult"}

    def review_request(self, wire=False):
        request = self.issued_request()
        request["work"] = {"kind": "ReviewStep", "purpose": "Supplied review target",
                           "review": {"review_context": {"native_owned": "fixture context"}}}
        if wire:
            for name, alias in (("sessionId", "session_id"), ("submitOperation", "submit_operation"),
                                ("submitSessionId", "submit_session_id"), ("resultType", "result_type")):
                request[alias] = request.pop(name)
        return request

    def review_args(self, output):
        return self.args("prepare-review-input", output,
                         "--request", str(self.request), "--result", str(self.review_result),
                         "--reference", str(self.review_reference),
                         "--execution-evidence", str(self.review_execution),
                         "--issuer", "supplied-runtime_root", "--occurred-at", "2026-10-14T06:00:00Z",
                         "--collection", "entity:smworkflow.cli.host_evidence")

    def review_command(self, argv):
        def option(name):
            return argv[argv.index("--" + name) + 1]
        return self.preparation_command("prepareEvidence", {
            "family": "GoalPhase", "kind": "review", "request": Path(option("request")).as_uri(),
            "result": Path(option("result")).as_uri(), "reference": Path(option("reference")).as_uri(),
            "executionEvidence": Path(option("execution-evidence")).as_uri(), "issuer": option("issuer"),
            "occurredAt": option("occurred-at"), "collection": option("collection")})

    def acknowledged(self, session_id="actual-goal"):
        return {"sessionId": session_id, "accepted": True, "outcome": "accepted",
                "message": "Actual native acknowledgement"}

    def process_output(self, response, stderr=b"", returncode=0, disconnected=False, during=None):
        raw = response if isinstance(response, bytes) else (json.dumps(response) + "\n").encode("utf-8")

        def invoke(command, **kwargs):
            kwargs["stdout"].write(raw)
            kwargs["stderr"].write(stderr)
            if during is not None:
                during()
            if disconnected:
                raise OSError("fixture runtime disconnected")
            return subprocess.CompletedProcess(command, returncode)

        return invoke, raw

    def call(self, argv, process):
        output, diagnostic = io.StringIO(), io.StringIO()
        native = Mock(side_effect=process)

        def fixture(command, runtime_root, descriptor, context):
            # A route-only native boundary supplies raw observations. Real
            # process ownership/interruption is covered by transport scenarios.
            CALLER.progress("invocation", context)
            with CALLER.new_file(descriptor, "response.json") as stdout:
                with CALLER.new_file(descriptor, "stderr.log") as stderr:
                    result = native(command, cwd=str(runtime_root), stdout=stdout, stderr=stderr)
            CALLER.require(result.returncode == 0, "native process exited unsuccessfully; retain output")
            with CALLER.os.fdopen(CALLER.os.open("response.json", CALLER.os.O_RDONLY | CALLER.os.O_NOFOLLOW,
                                               dir_fd=descriptor), "r", encoding="utf-8") as stream:
                text = stream.read()
            return text, CALLER.json_object(text)

        with patch.object(CALLER, "native_call", side_effect=fixture):
            # Route specifications capture the separate telemetry channel; the
            # transport specifications below exercise its actual stdout display.
            with patch.object(CALLER, "progress") as telemetry, redirect_stdout(output), redirect_stderr(diagnostic):
                status = CALLER.main(argv)
            native.telemetry = telemetry.call_args_list
        return status, output.getvalue(), diagnostic.getvalue(), native

    def command(self, operation, *pairs):
        return [str(self.executable), "--runtime-root", str(self.runtime_root),
                "command", "org.simplemodeling.textus.SmWorkflow.GoalPhaseService." + operation,
                *pairs, "--json"]

    def test_command_prefix_defers_configuration_to_native_resolver(self):
        # The helper selects cwd without manufacturing an assembly override.
        descriptor = self.runtime_root / ".textus/assembly.yaml"
        original = descriptor.read_bytes()
        self.assertEqual(CALLER.command_prefix(self.executable, self.runtime_root), [
            str(self.executable), "--runtime-root", str(self.runtime_root),
            "command"])
        self.assertEqual(descriptor.read_bytes(), original)

    def assert_one_call(self, native, command):
        native.assert_called_once()
        args, kwargs = native.call_args
        self.assertEqual(args, (command,))
        self.assertEqual(set(kwargs), {"cwd", "stdout", "stderr"})
        self.assertEqual(kwargs["cwd"], str(self.runtime_root))
        self.assertTrue(kwargs["stdout"].closed)
        self.assertTrue(kwargs["stderr"].closed)

    def test_e1_e3_e6_snake_wire_start_advance_submit_preserves_native_output(self):
        """E1/E3/E6 / R2,R3,R4,R5 / PHASE-6 S6-01D3D; native-goal-command."""
        # Given E1/R2,R3,R5 in native-goal-command: native-shaped snake_case Start specification data.
        start_output = self.output()
        response = {"session_id": "actual-goal", "message": "Started", "outcome": "started",
                    "native_owned_display": {"kept_field": True}}
        process, start_raw = self.process_output(response)
        # When the explicitly selected Start is acknowledged once.
        status, printed, diagnostic, native = self.call(
            self.args("start", start_output, "--input", str(self.input)), process)
        # Then Start has no execution cutoff, raw native naming displays unchanged and only the canonical local ID is retained.
        self.assertEqual(status, 0)
        self.assertEqual(printed, start_raw.decode())
        self.assertEqual(diagnostic, "")
        self.assert_one_call(native, self.command("startGoalPhase", "--input", self.input.read_text()))
        self.assertEqual((start_output / "response.json").read_bytes(), start_raw)
        route = self.task / "workflow-session.json"
        self.assertEqual(json.loads(route.read_text()), {"family": "GoalPhase", "sessionId": "actual-goal"})
        retained = route.read_bytes()

        # Given E6/R2,R4 in native-goal-command: that retained ID and a native-shaped issued Request.
        advance_output = self.output()
        request = {"session_id": "actual-goal", "kind": "Request", "message": "Issued semantic work",
                   "work": {"kind": "ImplementStep", "purpose": "Supplied purpose"},
                   "submit_operation": "submitGoalPhaseWorkResult", "submit_session_id": "actual-goal",
                   "result_type": "GoalPhaseWorkResult",
                   "issued_work_context": {"native_owned_field": {"kept_field": True}}}
        process, advance_raw = self.process_output(request)
        # When the selected task is explicitly advanced once.
        status, printed, diagnostic, native = self.call(self.args("advance", advance_output), process)
        # Then Advance has no execution cutoff and preserves the whole response without Submit or routing change.
        self.assertEqual(status, 0)
        self.assertEqual(printed, advance_raw.decode())
        self.assertEqual(diagnostic, "")
        self.assert_one_call(native, self.command("advanceGoalPhase", "--sessionId", "actual-goal"))
        self.assertEqual((advance_output / "response.json").read_bytes(), advance_raw)
        self.assertEqual(route.read_bytes(), retained)

        # Given E3/E6/R4,R5 in native-goal-command: the exact issued file and supplied optional semantic values.
        submit_output = self.output()
        value = dict(self.submission_value, completionFacts=[{"supplied": "日本語"}],
                     executionEvidence={"suppliedContext": {"revision": 7}})
        self.submission.write_text(json.dumps(value, ensure_ascii=False))
        process, submit_raw = self.process_output({"session_id": "actual-goal", "accepted": True,
                                                  "outcome": "accepted", "message": "Native acknowledgement",
                                                  "native_owned_display": {"kept_field": True}})
        # When Submit explicitly consumes the snake_case Request emitted by that Advance.
        status, printed, diagnostic, native = self.call(
            self.args("submit", submit_output, "--request", str(advance_output / "response.json"),
                      "--submission", str(self.submission)), process)
        # Then Submit has no execution cutoff and sends the issued route and supplied values in declared parameters once.
        self.assertEqual(status, 0)
        self.assertEqual(printed, submit_raw.decode())
        self.assertEqual(diagnostic, "")
        pairs = ["--sessionId", "actual-goal"]
        for name in ("result", "resultReference", "completionFacts", "executionEvidence"):
            pairs.extend(["--" + name, json.dumps(value[name], ensure_ascii=False)])
        self.assert_one_call(native, self.command("submitGoalPhaseWorkResult", *pairs))
        self.assertEqual((submit_output / "response.json").read_bytes(), submit_raw)
        self.assertEqual((advance_output / "response.json").read_bytes(), advance_raw)
        self.assertEqual(route.read_bytes(), retained)

    def test_e24_advance_disconnect_retains_partial_streams_and_outer_route(self):
        """E24 / R2,R3,R4,R5 / PHASE-6 S6-01D6T3; native-goal-command."""
        # Given E24: an existing outer Goal route and partial native streams before an Advance disconnect.
        route = self.retain()
        retained = route.read_bytes()
        output = self.output()
        stderr = b"native Advance still uncertain\n"
        process, raw = self.process_output(b'{"session_id":"actual-goal",', stderr, disconnected=True)
        # When one explicit Advance observes a disconnected runtime.
        status, printed, diagnostic, native = self.call(self.args("advance", output), process)
        # Then its single call fails without a caller deadline, retaining both streams and the route without success or Submit/retry.
        self.assertNotEqual(status, 0)
        self.assertEqual(printed, "")
        self.assertIn("retain available output and stop", diagnostic)
        self.assert_one_call(native, self.command("advanceGoalPhase", "--sessionId", "actual-goal"))
        self.assertEqual((output / "response.json").read_bytes(), raw)
        self.assertEqual((output / "stderr.log").read_bytes(), stderr)
        self.assertEqual({path.name for path in output.iterdir()}, {"response.json", "stderr.log"})
        self.assertEqual(route.read_bytes(), retained)

    def test_e4_e6_contradictory_consumed_native_aliases_stop(self):
        """E4/E6 / R2,R3,R4,R5 / PHASE-6 S6-01D3D; native-goal-command."""
        for action in ("start", "advance", "submit"):
            with self.subTest(acknowledgement=action):
                # Given E4/R2,R3,R5 in native-goal-command: contradictory native acknowledgement ID aliases.
                route = self.task / "workflow-session.json"
                if action != "start":
                    self.retain()
                before = route.read_bytes() if route.exists() else None
                output = self.output()
                response = {"sessionId": "actual-goal", "session_id": "other-goal", "message": "Native result",
                            "outcome": "started" if action == "start" else "accepted",
                            "kind": "Request", "accepted": True}
                process, raw = self.process_output(response, b"native diagnostic\n")
                if action == "start":
                    pairs = ["--input", str(self.input)]
                    expected = self.command("startGoalPhase", "--input", self.input.read_text())
                elif action == "advance":
                    pairs = []
                    expected = self.command("advanceGoalPhase", "--sessionId", "actual-goal")
                else:
                    pairs = ["--request", str(self.request), "--submission", str(self.submission)]
                    expected = self.command("submitGoalPhaseWorkResult", "--sessionId", "actual-goal",
                        "--result", json.dumps(self.submission_value["result"], ensure_ascii=False),
                        "--resultReference", json.dumps(self.submission_value["resultReference"], ensure_ascii=False))
                # When the selected action receives that contradictory acknowledgement once.
                status, printed, diagnostic, native = self.call(self.args(action, output, *pairs), process)
                # Then it stops without success display, ID replacement or retry, retaining both native streams.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertIn("contradictory native field aliases: sessionId", diagnostic)
                self.assert_one_call(native, expected)
                self.assertEqual((output / "response.json").read_bytes(), raw)
                self.assertEqual((output / "stderr.log").read_bytes(), b"native diagnostic\n")
                self.assertEqual(route.read_bytes() if route.exists() else None, before)

        for alias, conflicting in (("session_id", "other-outer"),
                                   ("submit_operation", "submitRepositorySyncConflictResult"),
                                   ("submit_session_id", "child-session"),
                                   ("result_type", "RepositorySyncConflictResult")):
            with self.subTest(issued_alias=alias):
                # Given E6/R2,R4 in native-goal-command: one contradictory consumed Request alias and retained routing.
                route = self.retain()
                before = route.read_bytes()
                self.request.write_text(json.dumps(dict(self.issued_request(), **{alias: conflicting})))
                output = self.output()
                process, _raw = self.process_output(self.acknowledged())
                # When Submit explicitly consumes that contradictory issued Request.
                status, printed, diagnostic, native = self.call(
                    self.args("submit", output, "--request", str(self.request), "--submission", str(self.submission)), process)
                # Then refusal precedes any native invocation or output, preserving the selected outer ID.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertIn("contradictory native field aliases:", diagnostic)
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual(route.read_bytes(), before)

    def test_e1_start_keeps_actual_id_and_refuses_occupied_routing(self):
        """E1 / R2,R3,R5 / PHASE-6 S6-01D3D."""
        # Given an owned task, a proposal, and a different successful canonical ID.
        output = self.output()
        response = {"sessionId": "actual-goal", "message": "Started", "outcome": "started"}
        process, raw = self.process_output(response, stderr=b"ordinary native warning\n")
        argv = self.args("start", output, "--input", str(self.input), "--proposal", "proposal-id")
        # When Start is called once.
        status, printed, diagnostic, native = self.call(argv, process)
        # Then the one literal call and actual full response retain only the canonical routing.
        self.assertEqual(status, 0)
        self.assertEqual(printed, raw.decode())
        self.assertEqual(diagnostic, "")
        self.assert_one_call(native, self.command("startGoalPhase", "--input", self.input.read_text(),
                                                 "--sessionId", "proposal-id"))
        route = self.task / "workflow-session.json"
        self.assertEqual(json.loads(route.read_text()), {"family": "GoalPhase", "sessionId": "actual-goal"})
        self.assertEqual((output / "stderr.log").read_bytes(), b"ordinary native warning\n")
        # Given that now-occupied routing and a fresh output, when another Start is attempted.
        retained = route.read_bytes()
        second_output = self.output()
        status, printed, _diagnostic, native = self.call(
            self.args("start", second_output, "--input", str(self.input)), process)
        # Then there is no native invocation, output or replacement.
        self.assertEqual(status, 2)
        self.assertEqual(printed, "")
        native.assert_not_called()
        self.assertEqual(route.read_bytes(), retained)
        self.assertEqual(list(second_output.iterdir()), [])

    def test_e1_start_without_proposal_passes_whole_input(self):
        """E1 / R2,R3,R5 / PHASE-6 S6-01D3D."""
        # Given explicit native-owned input fields and no proposal.
        process, _raw = self.process_output({"sessionId": "actual-goal", "message": "Started", "outcome": "started"})
        # When Start is called.
        status, _printed, _diagnostic, native = self.call(
            self.args("start", self.output(), "--input", str(self.input)), process)
        # Then the complete input is passed with no invented proposal or metadata.
        self.assertEqual(status, 0)
        self.assert_one_call(native, self.command("startGoalPhase", "--input", self.input.read_text()))

    def test_e2_independent_interleaved_advances_reread_exact_task(self):
        """E2 / R2,R3 / PHASE-6 S6-01D3D."""
        # Given two independent tasks retaining their own canonical IDs.
        other = self.task.parent / "second-task"
        other.mkdir()
        route_a = self.retain(session_id="goal-a")
        route_b = self.retain(other, "goal-b")
        for task, session_id in ((self.task, "goal-a"), (other, "goal-b"), (other, "goal-b"), (self.task, "goal-a")):
            with self.subTest(task=task.name, session_id=session_id):
                response = {"sessionId": session_id, "kind": "Request", "message": "Current request", "work": {}}
                process, raw = self.process_output(response)
                # When that explicitly selected task is advanced in interleaved order.
                status, printed, _diagnostic, native = self.call(self.args("advance", self.output(task), task=task), process)
                # Then exactly its own current file ID is used and its unchanged result displayed.
                self.assertEqual(status, 0)
                self.assertEqual(printed, raw.decode())
                self.assert_one_call(native, self.command("advanceGoalPhase", "--sessionId", session_id))
        # Given a changed specification routing file between independent calls.
        route_a.write_text(json.dumps({"family": "GoalPhase", "sessionId": "goal-a-reread"}))
        process, _raw = self.process_output({"sessionId": "goal-a-reread", "kind": "Wait", "message": "Wait"})
        # When the selected task is independently advanced again.
        status, _printed, _diagnostic, native = self.call(self.args("advance", self.output()), process)
        # Then the caller rereads that exact file and leaves the other task alone.
        self.assertEqual(status, 0)
        self.assert_one_call(native, self.command("advanceGoalPhase", "--sessionId", "goal-a-reread"))
        self.assertEqual(json.loads(route_b.read_text())["sessionId"], "goal-b")

    def test_e3_submit_exact_issued_route_without_optional_metadata(self):
        """E3 / R4,R5 / PHASE-6 S6-01D3D."""
        # Given an actual-shape issued Request, supplied result/reference and retained outer ID.
        route = self.retain()
        before = route.read_bytes()
        process, raw = self.process_output(self.acknowledged())
        # When the explicit Submit is acknowledged.
        status, printed, diagnostic, native = self.call(
            self.args("submit", self.output(), "--request", str(self.request), "--submission", str(self.submission)), process)
        # Then only the issued operation, ID and supplied JSON are submitted, with no Advance.
        self.assertEqual(status, 0)
        self.assertEqual(printed, raw.decode())
        self.assertEqual(diagnostic, "")
        self.assert_one_call(native, self.command("submitGoalPhaseWorkResult", "--sessionId", "actual-goal",
            "--result", json.dumps(self.submission_value["result"], ensure_ascii=False),
            "--resultReference", json.dumps(self.submission_value["resultReference"], ensure_ascii=False)))
        self.assertEqual(route.read_bytes(), before)

    def test_e3_submit_preserves_supplied_optional_json(self):
        """E3 / R4,R5 / PHASE-6 S6-01D3D."""
        # Given genuinely supplied-shape optional JSON as specification data.
        self.retain()
        value = dict(self.submission_value, completionFacts=[{"supplied": "日本語"}],
                     executionEvidence={"suppliedContext": {"revision": 7}})
        self.submission.write_text(json.dumps(value, ensure_ascii=False))
        process, _raw = self.process_output(self.acknowledged())
        # When Submit is called once.
        status, _printed, _diagnostic, native = self.call(
            self.args("submit", self.output(), "--request", str(self.request), "--submission", str(self.submission)), process)
        # Then every supplied object/array is passed unchanged in value, without fabricated keys.
        self.assertEqual(status, 0)
        pairs = ["--sessionId", "actual-goal"]
        for name in ("result", "resultReference", "completionFacts", "executionEvidence"):
            pairs.extend(["--" + name, json.dumps(value[name], ensure_ascii=False)])
        self.assert_one_call(native, self.command("submitGoalPhaseWorkResult", *pairs))

    def test_e4_native_failure_stops_preserves_logs_and_routing(self):
        """E4 / R2,R5 / PHASE-6 S6-01D3D."""
        cases = [
            ("nonzero", self.acknowledged(), 3, False),
            ("stderr-only", b"", 2, False),
            ("malformed", b"not-json", 0, False),
            ("nonobject", b"[]", 0, False),
            ("duplicate-json", b'{"accepted":true,"accepted":false}', 0, False),
            ("nonfinite-json", b'{"value":NaN}', 0, False),
            ("false", dict(self.acknowledged(), accepted=False), 0, False),
            ("nonboolean", dict(self.acknowledged(), accepted=1), 0, False),
            ("wrong-id", self.acknowledged("other-goal"), 0, False),
            ("wrong-outcome", dict(self.acknowledged(), outcome="rejected"), 0, False),
            ("blank-message", dict(self.acknowledged(), message="  "), 0, False),
            ("disconnected", b"partial-native-response", 0, True),
        ]
        for name, response, returncode, disconnected in cases:
            with self.subTest(case=name):
                # Given retained routing and one failing native response.
                route = self.retain()
                before = route.read_bytes()
                output = self.output()
                process, raw = self.process_output(response, b"native diagnostic\n", returncode, disconnected)
                # When the selected Submit is called once.
                status, printed, diagnostic, native = self.call(
                    self.args("submit", output, "--request", str(self.request), "--submission", str(self.submission)), process)
                # Then the call stops nonzero, retaining output/ID and no success JSON or extra call.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic.startswith("native-goal-command: "))
                self.assertLessEqual(len(diagnostic), 535)
                native.assert_called_once()
                self.assertEqual((output / "response.json").read_bytes(), raw)
                self.assertEqual((output / "stderr.log").read_bytes(), b"native diagnostic\n")
                self.assertEqual(route.read_bytes(), before)

    def test_e4_large_native_output_is_accepted_and_retained(self):
        """E4 / R2,R5 / PHASE-6 S6-01D3D."""
        for response, stderr in ((dict(self.acknowledged(), detail="x" * (9 * 1024 * 1024)), b""),
                                 (self.acknowledged(), b"x" * (1024 * 1024 + 1))):
            with self.subTest(oversized_stream="response" if not stderr else "stderr"):
                # Given retained routing and a valid stream larger than the former byte ceiling.
                route = self.retain()
                before = route.read_bytes()
                output = self.output()
                process, raw = self.process_output(response, stderr)
                # When Submit consumes the final native result.
                status, printed, _diagnostic, native = self.call(
                    self.args("submit", output, "--request", str(self.request), "--submission", str(self.submission)), process)
                # Then it accepts the acknowledgement and preserves both streams and routing.
                self.assertEqual(status, 0)
                self.assertEqual(json.loads(printed), json.loads(raw))
                native.assert_called_once()
                self.assertEqual((output / "response.json").read_bytes(), raw)
                self.assertEqual((output / "stderr.log").read_bytes(), stderr)
                self.assertEqual(route.read_bytes(), before)

    def test_e4_bad_start_acknowledgement_never_creates_routing(self):
        """E4 / R2,R3,R5 / PHASE-6 S6-01D3D."""
        for response in ({"sessionId": "actual-goal", "message": "No", "outcome": "rejected"},
                         {"sessionId": " ", "message": "Started", "outcome": "started"},
                         {"sessionId": "actual-goal", "message": "", "outcome": "started"}):
            with self.subTest(response=response):
                # Given a native Start response without its required successful contract.
                process, raw = self.process_output(response)
                output = self.output()
                # When Start is called.
                status, printed, _diagnostic, native = self.call(
                    self.args("start", output, "--input", str(self.input)), process)
                # Then one call fails, logs remain, and successful routing is absent.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                native.assert_called_once()
                self.assertFalse((self.task / "workflow-session.json").exists())
                self.assertEqual((output / "response.json").read_bytes(), raw)

    def test_e4_start_exclusive_create_preserves_newly_occupied_routing(self):
        """E4 / R2,R3,R5 / PHASE-6 S6-01D3D."""
        # Given routing becomes occupied during the single native Start.
        response = {"sessionId": "actual-goal", "message": "Started", "outcome": "started"}
        process, _raw = self.process_output(response, during=lambda: self.retain(session_id="concurrent-owner"))
        # When successful Start attempts exclusive routing creation.
        status, printed, _diagnostic, native = self.call(
            self.args("start", self.output(), "--input", str(self.input)), process)
        # Then it stops without overwriting the concurrent owner or repeating Start.
        self.assertEqual(status, 2)
        self.assertEqual(printed, "")
        native.assert_called_once()
        self.assertEqual(json.loads((self.task / "workflow-session.json").read_text())["sessionId"], "concurrent-owner")

    def test_e5_invalid_routing_refuses_before_native_call(self):
        """E5 / R3,R4 / PHASE-6 S6-01D3D."""
        for value in (None, {}, {"family": "GoalPhase", "sessionId": " "},
                      {"family": "SplitPhase", "sessionId": "actual-goal"},
                      {"family": "GoalPhase", "sessionId": "actual-goal", "permission": True}, []):
            with self.subTest(routing=value):
                # Given missing, blank, wrong-family or extra-key routing.
                route = self.task / "workflow-session.json"
                if value is not None:
                    route.write_text(json.dumps(value))
                before = route.read_bytes() if route.exists() else None
                output = self.output()
                process, _raw = self.process_output({})
                # When Advance is attempted.
                status, printed, _diagnostic, native = self.call(self.args("advance", output), process)
                # Then no native call or overwrite occurs.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual(route.read_bytes() if route.exists() else None, before)

    def test_e5_invalid_argument_pairs_refuse_before_native_call(self):
        """E5 / R3,R4 / PHASE-6 S6-01D3D."""
        self.retain()
        for extras in (("--task", str(self.task)), ("--unknown", "value"), ("--input", str(self.input)),
                       ("--proposal", ""), ("--request", str(self.request)), ("--output",),
                       ("--participant", "asami"), ("--submission", str(self.submission))):
            with self.subTest(extras=extras):
                # Given duplicate, unknown, blank, missing-pair or inapplicable options.
                output = self.output()
                process, _raw = self.process_output({})
                # When an explicit Advance attempts those arguments.
                status, printed, _diagnostic, native = self.call(self.args("advance", output, *extras), process)
                # Then validation refuses before any output or process.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
        # Given a Start missing its required input.
        process, _raw = self.process_output({})
        output = self.output()
        # When Start is attempted without input.
        status, _printed, _diagnostic, native = self.call(self.args("start", output), process)
        # Then no native process starts.
        self.assertEqual(status, 2)
        native.assert_not_called()

    def test_e5_invalid_write_destinations_refuse_without_overwrite(self):
        """E5 / R3,R4 / PHASE-6 S6-01D3D."""
        # Given outside, task-equal, redirected, occupied, relative or traversal output paths.
        self.retain()
        outside = self.root / "outside"
        outside.mkdir()
        redirected = self.task / "redirected"
        redirected.symlink_to(outside, target_is_directory=True)
        linked_ancestor = self.task / "linked-ancestor"
        linked_ancestor.symlink_to(outside, target_is_directory=True)
        (outside / "nested").mkdir()
        occupied = self.output()
        marker = occupied / "existing.txt"
        marker.write_text("preserve me")
        ordinary = self.output()
        destinations = (str(outside), str(self.task), str(redirected), str(linked_ancestor / "nested"),
                        str(occupied), "relative-output", str(ordinary) + "/../" + ordinary.name)
        for destination in destinations:
            with self.subTest(destination=destination):
                process, _raw = self.process_output({})
                # When the selected call uses that invalid write destination.
                status, printed, _diagnostic, native = self.call(self.args("advance", destination), process)
                # Then it refuses before native execution and preserves all existing files.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                native.assert_not_called()
                self.assertEqual(marker.read_text(), "preserve me")
                self.assertEqual(list(ordinary.iterdir()), [])
                self.assertEqual(list((outside / "nested").iterdir()), [])

    def test_missing_output_is_created_privately_without_changing_routing(self):
        # Given an existing selected task and no output directories beneath it.
        route = self.retain()
        before = route.read_bytes()
        output = self.task / "new-group" / "new-call"
        process, raw = self.process_output({"sessionId": "actual-goal", "kind": "Wait",
                                            "message": "Fixture frontier"})
        # When ordinary Advance is invoked without a separate mkdir operation.
        status, printed, diagnostic, native = self.call(self.args("advance", output), process)
        # Then one native call retains its exact output in private directories.
        self.assertEqual(status, 0)
        self.assertEqual(diagnostic, "")
        self.assertEqual(printed, raw.decode())
        self.assert_one_call(native, self.command("advanceGoalPhase", "--sessionId", "actual-goal"))
        self.assertEqual((output / "response.json").read_bytes(), raw)
        self.assertEqual(route.read_bytes(), before)
        for directory in (output.parent, output):
            self.assertEqual(stat.S_IMODE(directory.stat().st_mode), 0o700)
        # A second invocation cannot overwrite the retained result or launch again.
        second, _printed, _diagnostic, repeated = self.call(self.args("advance", output), process)
        self.assertEqual(second, 2)
        repeated.assert_not_called()
        self.assertEqual((output / "response.json").read_bytes(), raw)

    def test_output_creation_failure_does_not_launch_or_change_session(self):
        # Given a retained route and an output whose creation will be denied.
        route = self.retain()
        before = route.read_bytes()
        output = self.task / "cannot-create"
        process, _raw = self.process_output({})
        # When the helper cannot create its private output directory.
        with patch.object(CALLER.os, "mkdir", side_effect=PermissionError("fixture denial")):
            status, printed, diagnostic, native = self.call(self.args("advance", output), process)
        # Then it refuses before native launch and preserves the route and absent output.
        self.assertEqual(status, 2)
        self.assertEqual(printed, "")
        self.assertIn("native CLI was not launched", diagnostic)
        native.assert_not_called()
        self.assertFalse(output.exists())
        self.assertEqual(route.read_bytes(), before)

    def test_concurrent_calls_to_missing_output_launch_only_one_native_process(self):
        # Given two actual helper processes sharing one missing output; the native CLI is
        # a finite fixture, never a real Workflow or an accepted production result.
        route = self.retain()
        before = route.read_bytes()
        output = self.task / "concurrent-call"
        invocations = self.root / "fixture-invocations.txt"
        self.executable.write_text("#!" + sys.executable + "\n"
            "from pathlib import Path\n"
            "with Path(" + repr(str(invocations)) + ").open('a') as stream:\n"
            "    stream.write('invoked\\n')\n"
            "print('{\"sessionId\":\"actual-goal\",\"kind\":\"Wait\",\"message\":\"Fixture frontier\"}')\n")
        command = [sys.executable, str(SOURCE), *self.args("advance", output)]
        children = []
        try:
            # When both helpers compete for the same output and their actual processes terminate.
            for _ in range(2):
                children.append(subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE))
            results = [child.communicate(timeout=20) for child in children]
            # Then only one native fixture was launched, its response survives and the route is unchanged.
            self.assertEqual(sorted(child.returncode for child in children), [0, 2], results)
            self.assertEqual(invocations.read_text().splitlines(), ["invoked"])
            self.assertEqual(json.loads((output / "response.json").read_text()),
                             {"sessionId": "actual-goal", "kind": "Wait", "message": "Fixture frontier"})
            self.assertEqual(route.read_bytes(), before)
        finally:
            for child in children:
                if child.poll() is None:
                    child.kill()
                child.communicate()

    def test_e5_wrong_or_redirected_task_refuses_before_native_call(self):
        """E5 / R3,R4 / PHASE-6 S6-01D3D."""
        # Given a task outside the exact runtime_root child location or a symlink task.
        wrong = self.root / "unowned-task"
        wrong.mkdir()
        link = self.task.parent / "task-link"
        link.symlink_to(wrong, target_is_directory=True)
        output = self.output(wrong)
        for task in (wrong, link):
            with self.subTest(task=task):
                process, _raw = self.process_output({})
                selected_output = output if task == wrong else link / output.name
                # When Start is attempted in that selected task.
                status, printed, _diagnostic, native = self.call(
                    self.args("start", selected_output, "--input", str(self.input), task=task), process)
                # Then no native call or files are produced in the redirected task.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])

    def test_e5_unknown_or_wrong_shaped_submission_metadata_refuses(self):
        """E5 / R3,R4 / PHASE-6 S6-01D3D."""
        for value in (dict(self.submission_value, participant="invented"),
                      {"result": {}, "resultReference": {}, "completionFacts": {}},
                      {"result": {}, "resultReference": {}, "executionEvidence": []},
                      {"result": [], "resultReference": {}}, {"result": {}},
                      {"result": {}, "resultReference": None}):
            with self.subTest(submission=value):
                # Given unknown or wrong-shaped submission call metadata.
                route = self.retain()
                before = route.read_bytes()
                self.submission.write_text(json.dumps(value))
                output = self.output()
                process, _raw = self.process_output({})
                # When Submit is attempted.
                status, printed, _diagnostic, native = self.call(
                    self.args("submit", output, "--request", str(self.request), "--submission", str(self.submission)), process)
                # Then no native call, output or routing change occurs.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                native.assert_not_called()
                self.assertEqual(route.read_bytes(), before)
                self.assertEqual(list(output.iterdir()), [])

    def test_e6_human_wait_and_terminal_display_once_then_hand_back(self):
        """E6 / R2,R4 / PHASE-6 S6-01D3D."""
        for kind in ("Human", "Wait", "Terminal"):
            with self.subTest(kind=kind):
                # Given the native Advance result with independent continuation ownership.
                route = self.retain()
                before = route.read_bytes()
                response = {"sessionId": "actual-goal", "kind": kind, "message": "Actual " + kind,
                            "nativeOwnedDisplay": {"preserved": True}}
                process, raw = self.process_output(response)
                # When Advance is explicitly called.
                status, printed, _diagnostic, native = self.call(self.args("advance", self.output()), process)
                # Then the actual full result is displayed after one call, without acting on its kind.
                self.assertEqual(status, 0)
                self.assertEqual(printed, raw.decode())
                self.assert_one_call(native, self.command("advanceGoalPhase", "--sessionId", "actual-goal"))
                self.assertEqual(route.read_bytes(), before)

    def test_e6_unsupported_or_contradictory_submit_routes_make_no_call(self):
        """E6 / R2,R4 / PHASE-6 S6-01D3D."""
        replacements = ({"submitSessionId": "child-session"}, {"submitOperation": "submitRepositorySyncConflictResult"},
                        {"resultType": "RepositorySyncConflictResult"}, {"sessionId": "other-outer"},
                        {"kind": "Human"}, {"work": None}, {"submitOperation": " "}, {"submitSessionId": ""})
        for replacement in replacements:
            with self.subTest(route=replacement):
                # Given a forwarded child, other type, or contradictory issued route.
                route = self.retain()
                before = route.read_bytes()
                self.request.write_text(json.dumps(dict(self.issued_request(), **replacement)))
                output = self.output()
                process, _raw = self.process_output({})
                # When Submit is attempted.
                status, printed, _diagnostic, native = self.call(
                    self.args("submit", output, "--request", str(self.request), "--submission", str(self.submission)), process)
                # Then the caller stops without guessing a route or altering the outer ID.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                native.assert_not_called()
                self.assertEqual(route.read_bytes(), before)
                self.assertEqual(list(output.iterdir()), [])

    def test_e7_preparation_uses_exact_passive_tool_pairs_and_no_session(self):
        """E7 / R1,R2,R3,R5 / PHASE-6 S6-01D3D; prerequisite only."""
        # Given two explicit selected files, metadata and existing tool prerequisites.
        output = self.output()
        response = {"datastore": [{"collection": "entity:sm.Workflow.goal_selection", "records": [{"id": "row"}]}]}
        process, raw = self.process_output(response)
        # When prepare-goal-input is selected explicitly.
        status, printed, diagnostic, native = self.call(self.preparation_args(output), process)
        # Then one concrete typed input runs through the normal command; rows display without caller session/Workflow effects.
        expected = self.preparation_command("prepareGoalStart", {
            "input": self.input.as_uri(), "plan": self.plan.as_uri(), "selectionId": "explicit-selection",
            "participant": "asami", "origin": "ExplicitHuman", "idempotencyKey": "explicit-key",
            "selectionCollection": "entity:sm.Workflow.goal_selection", "planCollection": "entity:sm.Workflow.goal_plan"})
        self.assertEqual(status, 0)
        self.assertEqual(printed, raw.decode())
        self.assertEqual(diagnostic, "")
        self.assert_one_call(native, expected)
        command = native.call_args.args[0]
        self.assertEqual(command.count("--input"), 1)
        value = json.loads(command[command.index("--input") + 1])
        self.assertEqual(value["input"], self.input.as_uri())
        self.assertEqual(value["plan"], self.plan.as_uri())
        self.assertNotIn("nativeOwnedField", value)

        self.assertFalse((self.task / "workflow-session.json").exists())
        self.assertEqual(set(path.name for path in output.iterdir()), {"response.json", "stderr.log"})
        self.assertEqual(set(path.name for path in self.task.iterdir()), {output.name})

    def test_e7_opaque_unicode_input_and_plan_uris(self):
        """E7 / R1,R2,R3,R5 / normal typed preparation construction, without native semantic decoding."""
        # Given selected Unicode file names containing opaque non-JSON semantic bytes.
        self.input = self.root / "明示 入力.json"
        self.plan = self.root / "選択 計画.json"
        self.input.write_bytes(b"opaque input owned by native decoder")
        self.plan.write_bytes(b"opaque plan owned by native decoder")
        before = (self.input.read_bytes(), self.plan.read_bytes())
        output = self.output()
        process, _raw = self.process_output({"datastore": []})
        # When the explicit preparation action constructs its single normal command.
        status, _printed, diagnostic, native = self.call(self.preparation_args(output), process)
        # Then one typed object carries exact opaque URIs and no semantic content is re-encoded.
        self.assertEqual(status, 0)
        self.assertEqual(diagnostic, "")
        native.assert_called_once()
        command = native.call_args.args[0]
        self.assertEqual(command.count("--input"), 1)
        value = json.loads(command[command.index("--input") + 1])
        self.assertEqual(value["input"], self.input.as_uri())
        self.assertEqual(value["plan"], self.plan.as_uri())
        self.assertIn("org.simplemodeling.textus.SmWorkflow.NativeWorkflowPreparationService.prepareGoalStart", command)
        self.assertEqual((self.input.read_bytes(), self.plan.read_bytes()), before)

    def test_e7_preparation_failures_stop_preserve_output_and_existing_routing(self):
        """E7 / R2,R3,R5 / PHASE-6 S6-01D3D; prerequisite only."""
        for response, returncode in ((b"not-json", 0), ({"datastore": {}}, 0),
                                     ({}, 0), ({"datastore": []}, 2)):
            with self.subTest(response=response, returncode=returncode):
                # Given a tool error/malformed response and already-owned routing.
                route = self.retain()
                before = route.read_bytes()
                output = self.output()
                process, raw = self.process_output(response, b"tool diagnostic", returncode)
                # When the passive preparation action is selected once.
                status, printed, _diagnostic, native = self.call(self.preparation_args(output), process)
                # Then failure preserves logs and routing with no Start/import or retry.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                native.assert_called_once()
                self.assertEqual((output / "response.json").read_bytes(), raw)
                self.assertEqual((output / "stderr.log").read_bytes(), b"tool diagnostic")
                self.assertEqual(route.read_bytes(), before)

    def test_e7_preparation_invalid_pairs_and_collections_refuse_before_call(self):
        """E7 / R2,R3,R5 / PHASE-6 S6-01D3D; prerequisite only."""
        cases = ("unknown-origin", "missing-plan", "duplicate-input", "inapplicable-proposal",
                 "bad-collection", "same-collections", "absent-input")
        for case in cases:
            with self.subTest(case=case):
                # Given invalid explicit tool metadata, files or action options.
                output = self.output()
                argv = self.preparation_args(output)
                if case == "unknown-origin":
                    argv.extend(["--origin", "ExplicitHuman"])
                elif case == "missing-plan":
                    index = argv.index("--plan")
                    del argv[index:index + 2]
                elif case == "duplicate-input":
                    argv.extend(["--input", str(self.input)])
                elif case == "inapplicable-proposal":
                    argv.extend(["--proposal", "proposal"])
                elif case == "bad-collection":
                    argv[argv.index("--selection-collection") + 1] = "entity:sm/Workflow/selection"
                elif case == "same-collections":
                    argv[argv.index("--selection-collection") + 1] = "entity:sm.Workflow.goal_plan"
                else:
                    argv[argv.index("--input") + 1] = str(self.root / "absent.json")
                process, _raw = self.process_output({"datastore": []})
                # When preparation is attempted.
                status, printed, _diagnostic, native = self.call(argv, process)
                # Then no tool or business operation runs and no files are written.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])

    def test_e8_snake_review_request_uses_only_passive_tool(self):
        """E8 / R2,R3,R4 / PHASE-6 S6-01D3F; caller behavior only."""
        # Given a retained Goal route, snake-wire ReviewStep and seven explicit review pairs.
        route = self.retain()
        retained = route.read_bytes()
        self.request.write_text(json.dumps(self.review_request(wire=True)))
        request_before = self.request.read_bytes()
        output = self.output()
        argv = self.review_args(output)
        process, raw = self.process_output({"datastore": [{"collection": "entity:smworkflow.cli.host_evidence",
                                                           "records": [{"id": "fixture-review-row"}]}]})
        # When the single explicit review preparation receives a datastore-array acknowledgement.
        status, printed, diagnostic, native = self.call(argv, process)
        # Then only the normal typed preparation Operation runs, exact stdout is retained/displayed and route/request stay untouched.
        self.assertEqual(status, 0)
        self.assertEqual(printed, raw.decode())
        self.assertEqual(diagnostic, "")
        self.assert_one_call(native, self.review_command(argv))
        self.assertEqual((output / "response.json").read_bytes(), raw)
        self.assertEqual(route.read_bytes(), retained)
        self.assertEqual(self.request.read_bytes(), request_before)
        self.assertEqual(set(path.name for path in self.task.iterdir()), {route.name, output.name})

    def test_e9_review_consumed_request_aliases_and_route(self):
        """E9 / R2,R3 / PHASE-6 S6-01D3F; consumed route compatibility."""
        for spelling in ("declaration", "matching-both"):
            with self.subTest(spelling=spelling):
                # Given declaration-name Request fields, optionally with equal consumed wire aliases.
                route = self.retain()
                retained = route.read_bytes()
                request = self.review_request()
                if spelling == "matching-both":
                    request.update({name: value for name, value in self.review_request(wire=True).items()
                                    if name in {"session_id", "submit_operation", "submit_session_id", "result_type"}})
                self.request.write_text(json.dumps(request))
                argv = self.review_args(self.output())
                process, raw = self.process_output({"datastore": []})
                # When the explicit passive action consumes the matching issued Goal route.
                status, printed, diagnostic, native = self.call(argv, process)
                # Then matching aliases are accepted without changing the route or native argv.
                self.assertEqual(status, 0)
                self.assertEqual(printed, raw.decode())
                self.assertEqual(diagnostic, "")
                self.assert_one_call(native, self.review_command(argv))
                self.assertEqual(route.read_bytes(), retained)

        replacements = ({"session_id": "other"}, {"submit_operation": "other"},
                        {"submit_session_id": "other"}, {"result_type": "other"},
                        {"sessionId": "other"}, {"submitSessionId": "child"},
                        {"submitOperation": "submitRepositorySyncConflictResult"},
                        {"resultType": "RepositorySyncConflictResult"},
                        {"kind": "Human"}, {"kind": "Wait"}, {"kind": "Terminal"}, {"work": None})
        for replacement in replacements:
            with self.subTest(replacement=replacement):
                # Given a contradictory consumed alias, crossed route/type or non-Request issued output.
                route = self.retain()
                retained = route.read_bytes()
                self.request.write_text(json.dumps(dict(self.review_request(), **replacement)))
                output = self.output()
                process, _raw = self.process_output({"datastore": []})
                # When review preparation consumes that issued file.
                status, printed, diagnostic, native = self.call(self.review_args(output), process)
                # Then route admission stops before any native invocation or output write.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic)
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual(route.read_bytes(), retained)

    def test_e10_review_requires_exact_action_options(self):
        """E10 / R2,R4 / PHASE-6 S6-01D3F; no implicit metadata."""
        required = ("request", "result", "reference", "execution-evidence", "issuer", "occurred-at", "collection")
        cases = [("missing", name) for name in required] + [
            ("extra", name) for name in ("unknown", "participant", "family", "kind", "proposal", "input")
        ] + [("duplicate", "request"), ("blank", "issuer"), ("blank", "occurred-at"), ("unpaired", "issuer")]
        for case, name in cases:
            with self.subTest(case=case, option=name):
                # Given a missing, unknown, inapplicable, duplicate, blank or unpaired review option.
                route = self.retain()
                retained = route.read_bytes()
                output = self.output()
                argv = self.review_args(output)
                if case == "missing":
                    index = argv.index("--" + name)
                    del argv[index:index + 2]
                elif case in {"extra", "duplicate"}:
                    argv.extend(["--" + name, "supplied-value"])
                elif case == "blank":
                    argv[argv.index("--" + name) + 1] = " "
                else:
                    argv.append("--" + name)
                process, _raw = self.process_output({"datastore": []})
                # When the explicit review action parses those literal pairs.
                status, printed, diagnostic, native = self.call(argv, process)
                # Then no defaults, native calls or writes are introduced.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic)
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual(route.read_bytes(), retained)

    def test_e11_review_rejects_bad_collections_and_explicit_paths(self):
        """E11 / R2,R4 / PHASE-6 S6-01D3F; canonical explicit inputs."""
        cases = [("collection", value) for value in (
            "entity:smworkflow/cli/host_evidence", "entity:smworkflow.cli", "entity:smworkflow.cli.runtime_root.evidence",
            "smworkflow.cli.host_evidence", "entity:1workflow.cli.host_evidence", "entity:smworkflow..host_evidence",
        )]
        for name in ("request", "result", "reference", "execution-evidence"):
            cases.extend([(name, "relative.json"), (name, str(self.root) + "/../absent.json"),
                          (name, str(self.root / "absent.json"))])
        for name, value in cases:
            with self.subTest(option=name, value=value):
                # Given a malformed collection or relative, unnormalized or absent explicit source file.
                route = self.retain()
                retained = route.read_bytes()
                self.request.write_text(json.dumps(self.review_request()))
                output = self.output()
                argv = self.review_args(output)
                argv[argv.index("--" + name) + 1] = value
                process, _raw = self.process_output({"datastore": []})
                # When the passive action validates its selected inputs.
                status, printed, diagnostic, native = self.call(argv, process)
                # Then it refuses before native call or output and keeps the selected route.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic)
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual(route.read_bytes(), retained)

    def test_e12_review_passive_failures_retain_streams_and_stop(self):
        """E12 / R4 / PHASE-6 S6-01D3F; one call without import/progression."""
        cases = (("nonzero", {"datastore": []}, 2, False), ("disconnected", b"partial", 0, True),
                 ("malformed", b"not-json", 0, False), ("nonobject", b"[]", 0, False),
                 ("absent-datastore", {}, 0, False), ("nonarray-datastore", {"datastore": {}}, 0, False))
        for name, response, returncode, disconnected in cases:
            with self.subTest(case=name):
                # Given a failing passive process/response and unchanged routing/import configuration.
                route = self.retain()
                retained = route.read_bytes()
                config = self.runtime_root / ".textus/config.json"
                config_before = config.read_bytes()
                self.request.write_text(json.dumps(self.review_request(wire=True)))
                output = self.output()
                argv = self.review_args(output)
                process, raw = self.process_output(response, b"passive diagnostic\n", returncode, disconnected)
                # When the caller receives the actual failing streams from its one passive invocation.
                status, printed, diagnostic, native = self.call(argv, process)
                # Then failure retains both logs, route and config and stops without another action.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic.startswith("native-goal-command: "))
                self.assert_one_call(native, self.review_command(argv))
                self.assertEqual((output / "response.json").read_bytes(), raw)
                self.assertEqual((output / "stderr.log").read_bytes(), b"passive diagnostic\n")
                self.assertEqual(route.read_bytes(), retained)
                self.assertEqual(config.read_bytes(), config_before)
                self.assertEqual(set(path.name for path in self.task.iterdir()),
                                 {"workflow-session.json"} | {"call-" + str(i) for i in range(1, self.output_index + 1)})

    def test_e13_review_requires_exact_valid_task_routing(self):
        """E13 / R3,R4 / PHASE-6 S6-01D3F; exact routing before native call."""
        cases = (None, b"not-json", b"[]", b'{}', b'{"family":"SplitPhase","sessionId":"actual-goal"}',
                 b'{"family":"GoalPhase","sessionId":" "}',
                 b'{"family":"GoalPhase","sessionId":"other-goal"}',
                 b'{"family":"GoalPhase","sessionId":"actual-goal","session_id":"other-goal"}',
                 b'{"family":"GoalPhase","sessionId":"actual-goal","sessionId":"other-goal"}')
        for value in cases:
            with self.subTest(routing=value):
                # Given absent, invalid or contradictory exact task routing and an issued ReviewStep.
                route = self.task / "workflow-session.json"
                if route.exists():
                    route.unlink()
                if value is not None:
                    route.write_bytes(value)
                self.request.write_text(json.dumps(self.review_request()))
                output = self.output()
                process, _raw = self.process_output({"datastore": []})
                # When review preparation rereads that selected task's routing file.
                status, printed, diagnostic, native = self.call(self.review_args(output), process)
                # Then refusal precedes any native call/write and preserves routing exactly as supplied.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic)
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual(route.read_bytes() if route.exists() else None, value)

    def test_e14_review_forwards_files_and_metadata_without_invention(self):
        """E14 / R2,R3,R4,R6 / PHASE-6 S6-01D3F; native-owned semantic admission."""
        # Given exact supplied files with independent fixture worker/ref and explicit issuer/time/collection.
        route = self.retain()
        retained = route.read_bytes()
        self.request.write_text(json.dumps(self.review_request(wire=True)), encoding="utf-8")
        self.review_result.write_text('{ "outcome": "repair", "summary": "日本語", "findings": ["supplied"] }\n',
                                      encoding="utf-8")
        self.review_reference.write_text('{ "identity": "supplied ref", "revision": "7" }\n')
        self.review_execution.write_text('{ "workerIdentity": "distinct supplied worker" }\n')
        files = (self.request, self.review_result, self.review_reference, self.review_execution)
        before = {path: path.read_bytes() for path in files}
        config = self.runtime_root / ".textus/config.json"
        config_before = config.read_bytes()
        output = self.output()
        argv = self.review_args(output)
        for name, value in (("issuer", "explicit runtime_root issuer"), ("occurred-at", "2026-10-14T06:07:08.123Z"),
                            ("collection", "entity:Explicit.Host.review_input")):
            argv[argv.index("--" + name) + 1] = value
        process, raw = self.process_output({"datastore": [], "native_owned_display": {"kept": True}})
        # When the caller forwards the explicit files and metadata to the existing passive owner.
        status, printed, diagnostic, native = self.call(argv, process)
        # Then exact argv/file bytes/streams survive without added worker/model/time/state or config effects.
        self.assertEqual(status, 0)
        self.assertEqual(printed, raw.decode())
        self.assertEqual(diagnostic, "")
        self.assert_one_call(native, self.review_command(argv))
        self.assertEqual({path: path.read_bytes() for path in files}, before)
        self.assertEqual(route.read_bytes(), retained)
        self.assertEqual(config.read_bytes(), config_before)
        self.assertEqual((output / "response.json").read_bytes(), raw)
        self.assertEqual(set(path.name for path in self.task.iterdir()), {route.name, output.name})


    def test_e15_repository_passive_forwarding_preserves_files_and_streams(self):
        """E15 / R2,R3,R5,R7 / PHASE-6 S6-01D3S; caller forwarding only."""
        for existing_route in (False, True):
            with self.subTest(existing_route=existing_route):
                # Given a selected business file with inert claims and absent or unreadable existing routing.
                route = self.task / "workflow-session.json"
                if existing_route:
                    route.write_bytes(b"not a Goal route; passive preparation must not read it")
                route_before = route.read_bytes() if route.exists() else None
                self.input.write_text('{ "repositoryIdentity": "日本語", "permission": true, "origin": "claimed" }\n',
                                      encoding="utf-8")
                files = (self.input, self.runtime_root / ".textus/config.json", self.runtime_root / ".textus/assembly.yaml")
                before = {path: path.read_bytes() for path in files}
                output = self.output()
                argv = self.repository_args(output)
                response = b'{ "datastore": [], "selectionPayload": "native-owned spelling" }\n'
                process, raw = self.process_output(response, b"native repository warning\n")
                # When the explicitly selected Repository preparation receives one passive acknowledgement.
                status, printed, diagnostic, native = self.call(argv, process)
                # Then exact fixed argv and raw streams survive without parsing input, import or route/config effects.
                self.assertEqual(status, 0)
                self.assertEqual(printed, raw.decode())
                self.assertEqual(diagnostic, "")
                self.assert_one_call(native, self.repository_command(argv))
                self.assertEqual((output / "response.json").read_bytes(), raw)
                self.assertEqual((output / "stderr.log").read_bytes(), b"native repository warning\n")
                self.assertEqual({path: path.read_bytes() for path in files}, before)
                self.assertEqual(route.read_bytes() if route.exists() else None, route_before)
                self.assertEqual(set(path.name for path in output.iterdir()), {"response.json", "stderr.log"})
                self.assertEqual(set(path.name for path in self.task.iterdir()),
                                 ({route.name} if existing_route else set())
                                 | {"call-" + str(i) for i in range(1, self.output_index + 1)})

    def test_e16_repository_requires_exact_action_options(self):
        """E16 / R2,R3,R7 / PHASE-6 S6-01D3S; refusal before output/process."""
        required = ("input", "selection-id", "participant", "idempotency-key", "selection-collection")
        cases = [("missing", name) for name in required] + [
            ("extra", name) for name in ("unknown", "plan", "plan-collection", "request", "proposal", "origin", "permission")
        ] + [("duplicate", name) for name in required] + [("blank", name) for name in required] + [
            ("unpaired", "participant")
        ]
        for case, name in cases:
            with self.subTest(case=case, option=name):
                # Given a missing, duplicate, unknown, inapplicable, blank or unpaired Repository option.
                route = self.retain()
                files = (route, self.input, self.runtime_root / ".textus/config.json", self.runtime_root / ".textus/assembly.yaml")
                before = {path: path.read_bytes() for path in files}
                output = self.output()
                argv = self.repository_args(output)
                if case == "missing":
                    index = argv.index("--" + name)
                    del argv[index:index + 2]
                elif case in {"extra", "duplicate"}:
                    argv.extend(["--" + name, "supplied-value"])
                elif case == "blank":
                    argv[argv.index("--" + name) + 1] = " "
                else:
                    argv.append("--" + name)
                process, _raw = self.process_output({"datastore": []})
                # When the finite action parses its exact explicit pairs.
                status, printed, diagnostic, native = self.call(argv, process)
                # Then refusal precedes every subprocess/output and preserves selected files/configuration/routing.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic)
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual({path: path.read_bytes() for path in files}, before)

    def test_e17_repository_rejects_bad_collection_and_input_paths(self):
        """E17 / R2,R3,R7 / PHASE-6 S6-01D3S; explicit file/collection boundary."""
        cases = [("selection-collection", value) for value in (
            "entity:smworkflow/cli/repository_selections", "entity:smworkflow.cli",
            "entity:smworkflow.cli.repository.selections", "smworkflow.cli.repository_selections",
            "entity:1workflow.cli.repository_selections", "entity:smworkflow..repository_selections",
        )] + [("input", value) for value in (
            "relative.json", str(self.root) + "/../absent.json", str(self.root / "absent.json"),
        )]
        for name, value in cases:
            with self.subTest(option=name, value=value):
                # Given a malformed selected entity address or relative, unnormalized or missing business file.
                route = self.retain()
                files = (route, self.input, self.runtime_root / ".textus/config.json", self.runtime_root / ".textus/assembly.yaml")
                before = {path: path.read_bytes() for path in files}
                output = self.output()
                argv = self.repository_args(output)
                argv[argv.index("--" + name) + 1] = value
                process, _raw = self.process_output({"datastore": []})
                # When preparation validates the explicit file/address before invoking its native owner.
                status, printed, diagnostic, native = self.call(argv, process)
                # Then no native call or output occurs and all existing selected files remain untouched.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic)
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual({path: path.read_bytes() for path in files}, before)

    def test_e18_repository_failures_retain_streams_without_retry(self):
        """E18 / R2,R3,R5,R7 / PHASE-6 S6-01D3S; passive failure ends this call."""
        cases = (("nonzero", {"datastore": []}, 2, False), ("disconnected", b"partial", 0, True),
                 ("malformed", b"not-json", 0, False), ("nonobject", b"[]", 0, False),
                 ("duplicate", b'{"datastore":[],"datastore":[]}', 0, False),
                 ("nonfinite", b'{"datastore":[],"value":NaN}', 0, False),
                 ("missing-datastore", {}, 0, False), ("nonarray-datastore", {"datastore": {}}, 0, False))
        for name, response, returncode, disconnected in cases:
            with self.subTest(case=name):
                # Given a failing native process or strict acknowledgement and existing selected files/route/config.
                route = self.retain()
                files = (route, self.input, self.runtime_root / ".textus/config.json", self.runtime_root / ".textus/assembly.yaml")
                before = {path: path.read_bytes() for path in files}
                output = self.output()
                argv = self.repository_args(output)
                process, raw = self.process_output(response, b"repository diagnostic\n", returncode, disconnected)
                # When the one selected passive invocation returns or times out with available streams.
                status, printed, diagnostic, native = self.call(argv, process)
                # Then both raw streams remain and failure stops without retry, import or routing/config effects.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic.startswith("native-goal-command: "))
                self.assertLessEqual(len(diagnostic), 535)
                self.assert_one_call(native, self.repository_command(argv))
                self.assertEqual((output / "response.json").read_bytes(), raw)
                self.assertEqual((output / "stderr.log").read_bytes(), b"repository diagnostic\n")
                self.assertEqual({path: path.read_bytes() for path in files}, before)
                self.assertEqual(set(path.name for path in output.iterdir()), {"response.json", "stderr.log"})
                self.assertEqual(set(path.name for path in self.task.iterdir()),
                                 {route.name} | {"call-" + str(i) for i in range(1, self.output_index + 1)})


    def repository_review_request(self, spelling="camel"):
        request = {
            "sessionId": "actual-goal", "kind": "Request", "message": "Review selected merge",
            "submitOperation": "submitNativeMergeReview", "submitSessionId": "issued-child",
            "resultType": "RepositorySyncNativeMergeReviewResult",
            "repository_review": {
                "local_tip": "fixture-local", "remote_tip": "fixture-remote",
                "index_tree": "fixture-index", "changed_paths": ["docs/fixture.md"],
                "local_diff": "supplied local diff", "remote_diff": "supplied remote diff",
                "validation_mode": "required", "selected_checks": ["fixture-check"],
                "checks": [{"identity": "fixture-check", "termination": "exited", "exit_code": 0}],
                "review_context": {"purpose": "repository-sync-independent-review",
                                 "target": {"instance": "issued-child", "validated_revision": "14"},
                                 "completion": {"run_id": "issued-child",
                                                "type_identity": "RepositorySyncNativeMergeReviewResult"}},
            },
            "issued_work_context": {"thinking_mode": "review", "run_id": "issued-child",
                                    "expected_revision": "15",
                                    "result_type": "RepositorySyncNativeMergeReviewResult",
                                    "completion_operation": {"service": "RepositorySyncService",
                                                             "operation": "submitNativeMergeReview"}},
        }
        if spelling != "camel":
            for name, alias in (("sessionId", "session_id"), ("submitOperation", "submit_operation"),
                                ("submitSessionId", "submit_session_id"), ("resultType", "result_type")):
                request[alias] = request[name]
                if spelling == "snake":
                    del request[name]
        return request

    def repository_review_args(self, action, output):
        if action == "prepare-repository-review-input":
            argv = self.review_args(output)
            argv[argv.index("--action") + 1] = action
            return argv
        return self.args(action, output, "--request", str(self.request),
                         "--submission", str(self.submission))

    def repository_review_submit_command(self, value):
        pairs = ["--sessionId", "issued-child"]
        for name in ("result", "resultReference", "completionFacts", "executionEvidence"):
            if name in value:
                pairs.extend(["--" + name, json.dumps(value[name], ensure_ascii=False)])
        command = self.command("submitNativeMergeReview", *pairs)
        index = command.index("org.simplemodeling.textus.SmWorkflow.GoalPhaseService.submitNativeMergeReview")
        command[index] = "org.simplemodeling.textus.SmWorkflow.RepositorySyncService.submitNativeMergeReview"
        return command

    def test_e19_forwarded_child_preparation_preserves_raw_goal_request(self):
        """E19 / R2,R3,R5,R8 / S6-01D4K; native decoder owns child content."""
        for spelling in ("camel", "snake", "matching-both"):
            with self.subTest(spelling=spelling):
                # Given an actual-shape forwarded Goal Request without outer work and explicit review files/metadata.
                route = self.retain()
                self.request.write_text(json.dumps(self.repository_review_request(spelling)) + "\n")
                self.review_result.write_text('{ "decision": "repair", "rationale": "Supplied review", "findings": [{"identity":"fixture-finding","affectedPaths":["docs/fixture.md"],"rationale":"Supplied finding"}] }\n')
                files = (route, self.request, self.review_result, self.review_reference, self.review_execution,
                         self.runtime_root / ".textus/config.json", self.runtime_root / ".textus/assembly.yaml")
                before = {path: path.read_bytes() for path in files}
                output = self.output()
                argv = self.repository_review_args("prepare-repository-review-input", output)
                process, raw = self.process_output(b'{ "datastore": [], "native_display": "kept" }\n', b"review warning\n")
                # When the dedicated child preparation receives its one passive acknowledgement.
                status, printed, diagnostic, native = self.call(argv, process)
                # Then fixed GoalPhase review Main receives original paths and metadata, with raw streams and all inputs unchanged.
                self.assertEqual(status, 0)
                self.assertEqual(printed, raw.decode())
                self.assertEqual(diagnostic, "")
                self.assert_one_call(native, self.review_command(argv))
                self.assertEqual((output / "response.json").read_bytes(), raw)
                self.assertEqual((output / "stderr.log").read_bytes(), b"review warning\n")
                self.assertEqual({path: path.read_bytes() for path in files}, before)
                self.assertEqual(set(path.name for path in output.iterdir()), {"response.json", "stderr.log"})
                self.assertEqual(set(path.name for path in self.task.iterdir()),
                                 {route.name} | {"call-" + str(i) for i in range(1, self.output_index + 1)})

    def test_e20_child_submit_forwards_repair_and_supplied_optional_values(self):
        """E20 / R2,R3,R5,R8 / S6-01D4K; accepted Submit is no effect grant."""
        for optional in (False, True):
            with self.subTest(optional=optional):
                # Given issued child review and independently supplied repair result/reference, with optional values only if provided.
                route = self.retain()
                self.request.write_text(json.dumps(self.repository_review_request("snake")))
                value = {"result": {"decision": "repair", "rationale": "日本語 review", "findings": [
                             {"identity": "supplied-finding", "affectedPaths": ["docs/fixture.md"],
                              "rationale": "Supplied finding"}]},
                         "resultReference": {"identity": "supplied actual artifact", "revision": "14"}}
                if optional:
                    value.update(completionFacts=[{"supplied": "fact"}],
                                 executionEvidence={"workerIdentity": "supplied independent reviewer"})
                self.submission.write_text(json.dumps(value, ensure_ascii=False))
                files = (route, self.request, self.submission, self.runtime_root / ".textus/config.json",
                         self.runtime_root / ".textus/assembly.yaml")
                before = {path: path.read_bytes() for path in files}
                output = self.output()
                response = dict(self.acknowledged("issued-child"), session_id="issued-child", native_display={"kept": True})
                process, raw = self.process_output(response, b"submit warning\n")
                # When explicit child Submit returns accepted for that child.
                status, printed, diagnostic, native = self.call(
                    self.repository_review_args("submit-repository-review", output), process)
                # Then exactly RepositorySyncService/child receives supplied JSON values, and no Advance or outer-route change follows.
                self.assertEqual(status, 0)
                self.assertEqual(printed, raw.decode())
                self.assertEqual(diagnostic, "")
                self.assert_one_call(native, self.repository_review_submit_command(value))
                self.assertEqual((output / "response.json").read_bytes(), raw)
                self.assertEqual((output / "stderr.log").read_bytes(), b"submit warning\n")
                self.assertEqual({path: path.read_bytes() for path in files}, before)
                self.assertEqual(set(path.name for path in self.task.iterdir()),
                                 {route.name} | {"call-" + str(i) for i in range(1, self.output_index + 1)})

    def test_e21_child_route_and_consumed_alias_refusals_precede_output(self):
        """E21 / R2,R3,R8 / S6-01D4K; finite route only."""
        replacements = [
            {"sessionId": "other-owner"}, {"kind": "Human"}, {"kind": "Wait"}, {"kind": "Terminal"},
            {"submitSessionId": None}, {"submitSessionId": " "}, {"submitSessionId": "actual-goal"},
            {"submitSessionId": 7}, {"submitOperation": "submitRepositorySyncConflictResult"},
            {"resultType": "GoalPhaseWorkResult"},
            {"session_id": "other-owner"}, {"submit_operation": "other"},
            {"submit_session_id": "other-child"}, {"result_type": "other"},
        ]
        cases = [("replacement", value) for value in replacements] + [
            ("missing", name) for name in ("kind", "sessionId", "submitOperation", "submitSessionId", "resultType")
        ] + [("cross-family", None)]
        for action in ("prepare-repository-review-input", "submit-repository-review"):
            for case, value in cases:
                with self.subTest(action=action, case=case, value=value):
                    # Given a crossed/missing/non-Request child route or contradiction in one of the four consumed aliases.
                    route = self.retain()
                    request = self.repository_review_request()
                    if case == "replacement":
                        request.update(value)
                    elif case == "missing":
                        del request[value]
                    else:
                        route.write_text(json.dumps({"family": "RepositorySync", "sessionId": "actual-goal"}))
                    self.request.write_text(json.dumps(request))
                    before = {path: path.read_bytes() for path in (route, self.request)}
                    output = self.output()
                    process, _raw = self.process_output({})
                    # When either dedicated child action consumes that route.
                    status, printed, diagnostic, native = self.call(self.repository_review_args(action, output), process)
                    # Then refusal precedes subprocess/output and preserves owning route and issued file.
                    self.assertEqual(status, 2)
                    self.assertEqual(printed, "")
                    self.assertTrue(diagnostic)
                    native.assert_not_called()
                    self.assertEqual(list(output.iterdir()), [])
                    self.assertEqual({path: path.read_bytes() for path in before}, before)

    def test_e21_original_outer_actions_still_refuse_legitimate_child(self):
        """E21 / R3,R4,R8 / S6-01D4K; original outer-only admission retained."""
        for action in ("prepare-review-input", "submit"):
            with self.subTest(action=action):
                # Given a legitimate forwarded Repository review child under retained Goal ownership.
                route = self.retain()
                before = route.read_bytes()
                self.request.write_text(json.dumps(self.repository_review_request()))
                output = self.output()
                argv = self.review_args(output) if action == "prepare-review-input" else self.args(
                    "submit", output, "--request", str(self.request), "--submission", str(self.submission))
                process, _raw = self.process_output({})
                # When an original outer-only action is selected.
                status, printed, diagnostic, native = self.call(argv, process)
                # Then it still refuses before invocation/output and retains the Goal route.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic)
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual(route.read_bytes(), before)

    def test_e22_child_actions_require_exact_pairs(self):
        """E22 / R2,R3,R8 / S6-01D4K; no user-selected native route."""
        for action in ("prepare-repository-review-input", "submit-repository-review"):
            required = ("request", "result", "reference", "execution-evidence", "issuer", "occurred-at", "collection") if action.startswith("prepare") else ("request", "submission")
            cases = [(kind, name) for kind in ("missing", "duplicate", "blank") for name in required]
            cases += [("extra", name) for name in ("unknown", "family", "kind", "service", "operation", "sessionId", "input", "proposal", "participant")]
            cases += [("extra", "submission" if action.startswith("prepare") else "collection"), ("unpaired", "request")]
            for case, name in cases:
                with self.subTest(action=action, case=case, option=name):
                    # Given a required pair missing/duplicated/blank, or an unknown/unpaired/inapplicable route option.
                    route = self.retain()
                    self.request.write_text(json.dumps(self.repository_review_request()))
                    before = route.read_bytes()
                    output = self.output()
                    argv = self.repository_review_args(action, output)
                    if case == "missing":
                        index = argv.index("--" + name)
                        del argv[index:index + 2]
                    elif case in {"duplicate", "extra"}:
                        argv.extend(["--" + name, "supplied"])
                    elif case == "blank":
                        argv[argv.index("--" + name) + 1] = " "
                    else:
                        argv.append("--" + name)
                    process, _raw = self.process_output({})
                    # When either finite child action parses the literal pairs.
                    status, printed, diagnostic, native = self.call(argv, process)
                    # Then no defaults, process or output appear, and owning route is preserved.
                    self.assertEqual(status, 2)
                    self.assertEqual(printed, "")
                    self.assertTrue(diagnostic)
                    native.assert_not_called()
                    self.assertEqual(list(output.iterdir()), [])
                    self.assertEqual(route.read_bytes(), before)

    def test_e22_child_explicit_files_and_collection_refusals(self):
        """E22 / R2,R3,R8 / S6-01D4K; shared explicit-source contract."""
        for action in ("prepare-repository-review-input", "submit-repository-review"):
            names = ("request", "result", "reference", "execution-evidence") if action.startswith("prepare") else ("request", "submission")
            cases = [(name, value) for name in names for value in (
                "relative.json", str(self.root) + "/../absent.json", str(self.root / "absent.json"), str(self.root))]
            if action.startswith("prepare"):
                cases += [("collection", value) for value in (
                    "entity:smworkflow/cli/reviews", "entity:smworkflow.cli", "entity:smworkflow.cli.runtime_root.review",
                    "smworkflow.cli.reviews", "entity:1workflow.cli.reviews", "entity:smworkflow..reviews")]
            for name, value in cases:
                with self.subTest(action=action, option=name, value=value):
                    # Given a relative/unnormalized/missing/nonfile source or malformed explicit collection.
                    route = self.retain()
                    self.request.write_text(json.dumps(self.repository_review_request()))
                    before = route.read_bytes()
                    output = self.output()
                    argv = self.repository_review_args(action, output)
                    argv[argv.index("--" + name) + 1] = value
                    process, _raw = self.process_output({})
                    # When the child action checks its selected sources.
                    status, printed, diagnostic, native = self.call(argv, process)
                    # Then refusal precedes subprocess/output and preserves outer routing.
                    self.assertEqual(status, 2)
                    self.assertEqual(printed, "")
                    self.assertTrue(diagnostic)
                    native.assert_not_called()
                    self.assertEqual(list(output.iterdir()), [])
                    self.assertEqual(route.read_bytes(), before)

    def test_e22_child_submit_metadata_refusals(self):
        """E22 / R3,R8 / S6-01D4K; opaque result contract unchanged."""
        values = ({"result": {}}, {"resultReference": {}}, {"result": [], "resultReference": {}},
                  {"result": {}, "resultReference": None},
                  {"result": {}, "resultReference": {}, "completionFacts": {}},
                  {"result": {}, "resultReference": {}, "executionEvidence": []},
                  dict(self.submission_value, issuer="invented"), [],
                  b'not-json', b'{"result":{},"result":{},"resultReference":{}}',
                  b'{"result":{"value":NaN},"resultReference":{}}')
        for value in values:
            with self.subTest(submission=value):
                # Given missing/wrong-shaped/extra submission metadata or malformed strict JSON.
                route = self.retain()
                self.request.write_text(json.dumps(self.repository_review_request()))
                self.submission.write_bytes(value if isinstance(value, bytes) else json.dumps(value).encode())
                before = {path: path.read_bytes() for path in (route, self.submission)}
                output = self.output()
                process, _raw = self.process_output({})
                # When explicit child Submit checks its supplied metadata.
                status, printed, diagnostic, native = self.call(
                    self.repository_review_args("submit-repository-review", output), process)
                # Then no subprocess/output or route/input mutation occurs.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic)
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual({path: path.read_bytes() for path in before}, before)

    def test_e23_child_failures_retain_raw_streams_without_retry(self):
        """E23 / R2,R3,R5,R8 / S6-01D4K; one native call and child acknowledgement."""
        for action in ("prepare-repository-review-input", "submit-repository-review"):
            good = {"datastore": []} if action.startswith("prepare") else self.acknowledged("issued-child")
            cases = [("nonzero", good, 2, False), ("disconnected", b"partial response", 0, True),
                     ("malformed", b"not-json", 0, False), ("nonobject", b"[]", 0, False),
                     ("duplicate", b'{"value":1,"value":2}', 0, False),
                     ("nonfinite", b'{"value":NaN}', 0, False)]
            if action.startswith("prepare"):
                cases += [("missing-datastore", {}, 0, False), ("nonarray-datastore", {"datastore": {}}, 0, False)]
            else:
                cases += [(name, dict(good, **replacement), 0, False) for name, replacement in (
                    ("outer-id", {"sessionId": "actual-goal"}), ("other-id", {"sessionId": "other-child"}),
                    ("conflicting-id", {"session_id": "actual-goal"}), ("missing-id", {"sessionId": None}),
                    ("blank-id", {"sessionId": " "}), ("false", {"accepted": False}),
                    ("nonboolean", {"accepted": 1}), ("wrong-outcome", {"outcome": "rejected"}),
                    ("blank-message", {"message": " "}))]
            cases += [("large-invalid-json", b" " * (8 * 1024 * 1024 + 1), 0, False)]
            for name, response, returncode, disconnected in cases:
                with self.subTest(action=action, case=name):
                    # Given native failure or a strict/bounded acknowledgement violation for the selected child action.
                    route = self.retain()
                    self.request.write_text(json.dumps(self.repository_review_request("snake")))
                    files = (route, self.request, self.submission, self.runtime_root / ".textus/config.json",
                             self.runtime_root / ".textus/assembly.yaml")
                    before = {path: path.read_bytes() for path in files}
                    output = self.output()
                    argv = self.repository_review_args(action, output)
                    process, raw = self.process_output(response, b"child diagnostic\n", returncode, disconnected)
                    # When the one selected native invocation returns or times out.
                    status, printed, diagnostic, native = self.call(argv, process)
                    # Then failure retains raw streams/config/outer route, without retry, Advance or success display.
                    self.assertEqual(status, 2)
                    self.assertEqual(printed, "")
                    self.assertTrue(diagnostic.startswith("native-goal-command: "))
                    self.assertLessEqual(len(diagnostic), 535)
                    expected = self.review_command(argv) if action.startswith("prepare") else self.repository_review_submit_command(self.submission_value)
                    self.assert_one_call(native, expected)
                    self.assertEqual((output / "response.json").read_bytes(), raw)
                    self.assertEqual((output / "stderr.log").read_bytes(), b"child diagnostic\n")
                    self.assertEqual({path: path.read_bytes() for path in files}, before)
                    self.assertEqual(set(path.name for path in output.iterdir()), {"response.json", "stderr.log"})
                    self.assertEqual(set(path.name for path in self.task.iterdir()),
                                     {route.name} | {"call-" + str(i) for i in range(1, self.output_index + 1)})


    def execution_request(self, child=False, wire=True, spelling=None):
        request = self.repository_review_request("snake") if child else self.issued_request()
        if child in {"repair", "conflict"}:
            operation, result_type = (
                ("submitNativeMergeRepair", "RepositorySyncNativeMergeRepairResult") if child == "repair"
                else ("submitRepositorySyncWorkResult", "RepositorySyncConflictResult"))
            request["submit_operation"] = operation
            request["result_type"] = result_type
        if spelling is not None:
            for name, alias in (("sessionId", "session_id"), ("submitOperation", "submit_operation"),
                                ("submitSessionId", "submit_session_id"), ("resultType", "result_type")):
                value = request.get(name, request.get(alias))
                request.pop(name, None)
                request.pop(alias, None)
                if spelling != "snake":
                    request[name] = value
                if spelling != "camel":
                    request[alias] = value
        issued = {"schema_version": "cncf.workflow-protocol.v1", "run_id": "native-run",
                  "execution_requirement": {"review_required": False, "reasoning_class": "CodingProgrammingStandard",
                      "disposition": {"origin_context_identity": "original-context"}},
                  "context": {"required_facts": [{"nativeCamelControl": "unchanged"}]}}
        request.pop("issuedWorkContext", None)
        request.pop("issued_work_context", None)
        request["issued_work_context" if wire else "issuedWorkContext"] = issued
        return request, issued

    def execution_args(self, action, output, reference=False, session=None):
        pairs = ["--request", str(self.request)]
        if action == "prepare-execution":
            pairs += ["--mapping", str(self.plan), "--origin", str(self.input)]
        else:
            pairs += ["--execution-session", str(session or self.submission), "--observation", str(self.review_result)]
            if reference:
                pairs += ["--reference", str(self.review_reference)]
        return self.args(action, output, *pairs)

    def test_e25_prepare_execution_preserves_whole_wire_scope_under_goal_and_child_routes(self):
        """S6-01D10 R6/R10: normal typed input, opaque issued scope and retained output."""
        for child in (False, True, "repair", "conflict"):
            for wire in (False, True):
                for spelling in ("camel", "snake", "matching-both"):
                    with self.subTest(child=child, wire=wire, spelling=spelling):
                        # Given a current retained outer Goal ID and an actual-shape Goal/review/repair/conflict Request with agreeing consumed route aliases and with untouched nested wire controls.
                        route = self.retain()
                        request, issued = self.execution_request(child, wire, spelling)
                        request["issuedWorkContext"] = issued
                        self.request.write_text(json.dumps(request))
                        before = {path: path.read_bytes() for path in (route, self.request, self.plan, self.input)}
                        output = self.output()
                        session = '{ "schemaVersion":"cncf.workflow-execution-harness.v1", "nativeControl": {"kept_key": true} }\n'
                        selection = '{"attempt":1,"profile":{"controls":{"reasoningEffort":"bounded"}}}\n'
                        process, raw = self.process_output({"session": session, "next_selection": selection})
                        # When exactly one finite prepare-execution action receives the normal passive response.
                        status, printed, diagnostic, native = self.call(self.execution_args("prepare-execution", output), process)
                        # Then it forwards the typed whole issued object and selected URIs, retains opaque strings byte-for-byte and never submits or advances.
                        self.assertEqual((status, diagnostic), (0, ""))
                        self.assertEqual(printed, raw.decode())
                        self.assert_one_call(native, self.preparation_command("prepareExecution", {
                            "issuedWorkContext": issued, "mapping": self.plan.as_uri(), "origin": self.input.as_uri()}))
                        self.assertEqual((output / "execution-session.json").read_bytes(), session.encode())
                        self.assertEqual((output / "execution-selection.json").read_bytes(), selection.encode())
                        self.assertFalse((output / "execution-evidence.json").exists())
                        self.assertEqual({path: path.read_bytes() for path in before}, before)

    def test_e26_record_execution_uses_explicit_prior_session_and_retains_only_actual_optional_outputs(self):
        """S6-01D10 R6/R10: one explicit retained Session, no implicit selection or preparation."""
        for child in (False, True, "repair", "conflict"):
            for spelling in ("camel", "snake", "matching-both"):
                for completed in (False, True):
                    with self.subTest(completed=completed, child=child, spelling=spelling):
                        # Given the current Goal-owned Goal/review/repair/conflict route with agreeing aliases, issued object and an explicitly selected prior call's opaque Session artifact.
                        route = self.retain()
                        request, issued = self.execution_request(child, spelling=spelling)
                        self.request.write_text(json.dumps(request))
                        prior = self.output()
                        session_path = prior / "execution-session.json"
                        session_path.write_text('{ "schemaVersion": "cncf.workflow-execution-harness.v1", "kept": true }\n')
                        before = {path: path.read_bytes() for path in (route, self.request, session_path)}
                        output = self.output()
                        actual_session = '{ "nativeSession": "actual returned artifact" }\n'
                        selection = None if completed else '{"attempt":2,"profile":{"nativeControl":"kept"}}'
                        evidence = {"references": [{"identity": "actual-reference", "revision": "1"}],
                                    "worker_identity": "actual-worker", "execution": {"profile_identity": "selected"}}
                        response = {"session": actual_session, "nextSelection": selection, "next_selection": selection}
                        if completed:
                            response.update(completionEvidence=evidence, completion_evidence=evidence)
                        process, raw = self.process_output(response)
                        # When record-execution supplies its selected Session/observation and optional reference in one normal call.
                        status, printed, diagnostic, native = self.call(self.execution_args(
                            "record-execution", output, reference=completed, session=session_path), process)
                        # Then exact optional outputs are retained, pending work gains no completion and original artifacts/routing stay intact.
                        value = {"issuedWorkContext": issued, "session": session_path.as_uri(), "observation": self.review_result.as_uri()}
                        if completed:
                            value["reference"] = self.review_reference.as_uri()
                        self.assertEqual((status, diagnostic), (0, ""))
                        self.assertEqual(printed, raw.decode())
                        self.assert_one_call(native, self.preparation_command("recordExecution", value))
                        self.assertEqual((output / "execution-session.json").read_bytes(), actual_session.encode())
                        self.assertEqual((output / "execution-selection.json").exists(), not completed)
                        self.assertEqual((output / "execution-evidence.json").exists(), completed)
                        if completed:
                            self.assertEqual(json.loads((output / "execution-evidence.json").read_text()), evidence)
                        else:
                            self.assertEqual((output / "execution-selection.json").read_text(), selection)
                        self.assertEqual({path: path.read_bytes() for path in before}, before)

    def test_e27_execution_rejects_crossed_task_and_consumed_request_aliases_before_native_call(self):
        """S6-01D10 R6/R10: outer Goal routing and issued alias agreement."""
        for action in ("prepare-execution", "record-execution"):
            for change in ({"sessionId": "crossed-task"}, {"session_id": "crossed-task"}, {"kind": "Wait"},
                           {"issuedWorkContext": {}}, {"issuedWorkContext": []}, {"issuedWorkContext": None},
                           {"issued_work_context": {"conflicting": True}}, {"submit_operation": "other"}):
                with self.subTest(action=action, change=change):
                    # Given a crossed current task ID, non-Request or missing/contradictory consumed issued scope.
                    route = self.retain()
                    request, _issued = self.execution_request(wire=False)
                    request.update(change)
                    self.request.write_text(json.dumps(request))
                    before = route.read_bytes()
                    output = self.output()
                    process, _raw = self.process_output({})
                    # When either finite execution action consumes the selected Request.
                    status, printed, diagnostic, native = self.call(self.execution_args(action, output), process)
                    # Then it refuses before invocation or output, preserving the selected Goal task route.
                    self.assertEqual(status, 2)
                    self.assertEqual(printed, "")
                    self.assertTrue(diagnostic)
                    native.assert_not_called()
                    self.assertEqual(list(output.iterdir()), [])
                    self.assertEqual(route.read_bytes(), before)

    def test_e28_execution_transport_and_consumed_response_failures_promote_no_completion(self):
        """S6-01D10 R6/R10: uncertainty and contradictions stop without retries."""
        good = {"session": '{"retained":"canonical"}', "next_selection": '{"attempt":2}'}
        cases = [("nonzero", good, 2, False), ("disconnected", b"partial", 0, True),
                 ("malformed", b"not-json", 0, False), ("missing-session", {}, 0, False),
                 ("structured-session", {"session": {}}, 0, False),
                 ("selection-alias", dict(good, nextSelection='{"attempt":1}'), 0, False),
                 ("evidence-alias", dict(good, completionEvidence={}, completion_evidence={"foreign": True}), 0, False),
                 ("structured-selection", dict(good, next_selection={"attempt": 2}), 0, False),
                 ("invalid-evidence", dict(good, completion_evidence=[]), 0, False)]
        for action in ("prepare-execution", "record-execution"):
            for name, response, returncode, disconnected in cases:
                with self.subTest(action=action, case=name):
                    # Given an actual-shape current Request but a failed, uncertain or contradictory passive native response.
                    route = self.retain()
                    request, _issued = self.execution_request()
                    self.request.write_text(json.dumps(request))
                    before = route.read_bytes()
                    output = self.output()
                    process, raw = self.process_output(response, b"native diagnostic\n", returncode, disconnected)
                    # When exactly one selected execution action returns or times out.
                    status, printed, diagnostic, native = self.call(self.execution_args(action, output), process)
                    # Then only raw streams remain; no Session/selection/evidence is promoted, and no retry/Submit/Advance occurs.
                    self.assertEqual(status, 2)
                    self.assertEqual(printed, "")
                    self.assertTrue(diagnostic)
                    native.assert_called_once()
                    self.assertEqual({path.name for path in output.iterdir()}, {"response.json", "stderr.log"})
                    self.assertEqual((output / "response.json").read_bytes(), raw)
                    self.assertEqual(route.read_bytes(), before)

    def test_e29_execution_options_are_exact_and_optional_reference_is_a_selected_file(self):
        """S6-01D10 R6/R10: finite named pairs, no discovery, default session or overwrite."""
        for action in ("prepare-execution", "record-execution"):
            cases = [("extra", "family"), ("extra", "operation"), ("extra", "submission"),
                     ("duplicate", "request"), ("missing", "request")]
            cases += [("missing", "mapping" if action == "prepare-execution" else "execution-session")]
            if action == "record-execution":
                cases += [("bad-reference", value) for value in ("relative.json", str(self.root / "absent.json"), str(self.root))]
            for kind, value in cases:
                with self.subTest(action=action, case=kind, value=value):
                    # Given exact current routing and a malformed finite action option or unavailable optional reference file.
                    self.retain()
                    request, _issued = self.execution_request()
                    self.request.write_text(json.dumps(request))
                    output = self.output()
                    argv = self.execution_args(action, output)
                    if kind == "missing":
                        index = argv.index("--" + value)
                        del argv[index:index + 2]
                    elif kind == "bad-reference":
                        argv += ["--reference", value]
                    else:
                        argv += ["--" + value, "supplied"]
                    process, _raw = self.process_output({})
                    # When the selected finite action parses metadata before dispatch.
                    status, printed, diagnostic, native = self.call(argv, process)
                    # Then refusal leaves the fresh output empty with no inferred Session or native invocation.
                    self.assertEqual(status, 2)
                    self.assertEqual(printed, "")
                    self.assertTrue(diagnostic)
                    native.assert_not_called()
                    self.assertEqual(list(output.iterdir()), [])


    def conflict_request(self, spelling="camel"):
        request, _issued = self.execution_request("conflict", spelling=spelling)
        request["repository_conflict"] = {"native_owned": {"kept_field": "opaque conflict"}}
        return request

    def conflict_args(self, action, output):
        if action in {"prepare-execution", "record-execution"}:
            return self.execution_args(action, output)
        return self.args(action, output, "--request", str(self.request),
                         "--submission", str(self.submission))

    def conflict_submit_command(self, value):
        pairs = ["--sessionId", "issued-child"]
        for name in ("result", "resultReference", "completionFacts", "executionEvidence"):
            if name in value:
                pairs.extend(["--" + name, json.dumps(value[name], ensure_ascii=False)])
        command = self.command("submitRepositorySyncWorkResult", *pairs)
        index = command.index(CALLER.FQN + "submitRepositorySyncWorkResult")
        command[index] = CALLER.REPOSITORY_FQN + "submitRepositorySyncWorkResult"
        return command

    def test_e30_conflict_submit_forwards_supplied_decisions_once_to_actual_child(self):
        """E30 / R2,R3,R5,R10 / S6-01D14; supplied proposal, separate effects."""
        for spelling in ("camel", "snake", "matching-both"):
            for decision in ("resolved", "decision-needed"):
                for optional in (False, True):
                    with self.subTest(spelling=spelling, decision=decision, optional=optional):
                        # Given E30: the retained Goal's actual conflict child and supplied decision/resolutions/rationale/reference.
                        route = self.retain()
                        self.request.write_text(json.dumps(self.conflict_request(spelling)))
                        value = {"result": {"decision": decision, "resolutions": ([
                                     {"conflictIdentity": "supplied-conflict", "text": "日本語 supplied resolution"}] if decision == "resolved" else []),
                                     "rationale": "Supplied conflict rationale"},
                                 "resultReference": {"identity": "supplied conflict artifact", "revision": "17"}}
                        if optional:
                            value.update(completionFacts=[{"supplied": "conflict fact"}],
                                         executionEvidence={"workerIdentity": "supplied original executor"})
                        self.submission.write_text(json.dumps(value, ensure_ascii=False))
                        files = (route, self.request, self.submission, self.runtime_root / ".textus/config.json",
                                 self.runtime_root / ".textus/assembly.yaml")
                        before = {path: path.read_bytes() for path in files}
                        output = self.output()
                        response = dict(self.acknowledged("issued-child"), session_id="issued-child",
                                        native_display={"kept_field": True})
                        process, raw = self.process_output(response, b"conflict warning\n")
                        # When the explicit conflict Submit receives acceptance for the issued child exactly once.
                        status, printed, diagnostic, native = self.call(
                            self.conflict_args("submit-repository-conflict", output), process)
                        # Then fixed Repository FQN receives opaque supplied values; raw streams/outer route remain without Advance.
                        self.assertEqual((status, diagnostic), (0, ""))
                        self.assertEqual(printed, raw.decode())
                        self.assert_one_call(native, self.conflict_submit_command(value))
                        self.assertEqual((output / "response.json").read_bytes(), raw)
                        self.assertEqual((output / "stderr.log").read_bytes(), b"conflict warning\n")
                        self.assertEqual({path: path.read_bytes() for path in files}, before)
                        self.assertEqual({path.name for path in output.iterdir()}, {"response.json", "stderr.log"})

    def test_e31_conflict_routes_and_consumed_aliases_refuse_before_all_three_actions(self):
        """E31 / R2,R3,R4,R10 / S6-01D14; finite retained owner and child."""
        replacements = [
            {"sessionId": "other-owner"}, {"kind": "Human"}, {"kind": "Wait"}, {"kind": "Terminal"},
            {"submitOperation": "submitRepositorySyncConflictResult"},
            {"submitOperation": "submitNativeMergeReview"},
            {"resultType": "RepositorySyncNativeMergeReviewResult"}, {"resultType": "GoalPhaseWorkResult"},
            {"submitSessionId": "actual-goal"}, {"submitSessionId": " "},
            {"submitSessionId": None}, {"submitSessionId": 7},
            {"session_id": "other-owner"}, {"submit_operation": "other"},
            {"submit_session_id": "other-child"}, {"result_type": "other"},
        ]
        cases = [("replacement", value) for value in replacements]
        cases += [("missing", name) for name in ("sessionId", "kind", "submitOperation", "submitSessionId", "resultType")]
        cases += [("task", "missing"), ("task", "wrong-family"), ("task", "crossed")]
        for action in ("submit-repository-conflict", "prepare-execution", "record-execution"):
            for case, value in cases:
                with self.subTest(action=action, case=case, value=value):
                    # Given E31: missing/wrong/crossed outer task, kind, operation, type, child or contradictory consumed aliases.
                    route = self.retain()
                    request = self.conflict_request()
                    if case == "replacement":
                        request.update(value)
                    elif case == "missing":
                        del request[value]
                    elif value == "missing":
                        route.unlink()
                    else:
                        route.write_text(json.dumps({"family": "RepositorySync" if value == "wrong-family" else "GoalPhase",
                                                     "sessionId": "other-goal" if value == "crossed" else "actual-goal"}))
                    self.request.write_text(json.dumps(request))
                    before = route.read_bytes() if route.exists() else None
                    output = self.output()
                    process, _raw = self.process_output({})
                    # When any of the three finite conflict actions consumes that selected route.
                    status, printed, diagnostic, native = self.call(self.conflict_args(action, output), process)
                    # Then refusal precedes subprocess/output and preserves the exact supplied task routing.
                    self.assertEqual(status, 2)
                    self.assertEqual(printed, "")
                    self.assertTrue(diagnostic)
                    native.assert_not_called()
                    self.assertEqual(list(output.iterdir()), [])
                    self.assertEqual(route.read_bytes() if route.exists() else None, before)
        # Given E31: a legitimate conflict child under its retained Goal owner.
        route = self.retain()
        before = route.read_bytes()
        self.request.write_text(json.dumps(self.conflict_request()))
        output = self.output()
        process, _raw = self.process_output({})
        # When the original outer-only Submit receives that child request.
        status, printed, diagnostic, native = self.call(self.conflict_args("submit", output), process)
        # Then original admission still refuses before launch/output and keeps outer routing.
        self.assertEqual(status, 2)
        self.assertEqual(printed, "")
        self.assertTrue(diagnostic)
        native.assert_not_called()
        self.assertEqual(list(output.iterdir()), [])
        self.assertEqual(route.read_bytes(), before)

    def test_e32_conflict_failures_retain_raw_streams_and_stop_without_progression(self):
        """E32 / R2,R3,R5,R10 / S6-01D14; native uncertainty ends one call."""
        for action in ("submit-repository-conflict", "prepare-execution", "record-execution"):
            good = self.acknowledged("issued-child") if action.startswith("submit") else {"session": '{"original":"retained"}'}
            cases = [("nonzero", good, b"diagnostic", 2, False),
                     ("disconnected", b"partial response", b"diagnostic", 0, True),
                     ("malformed", b"not-json", b"diagnostic", 0, False),
                     ("nonobject", b"[]", b"diagnostic", 0, False),
                     ("duplicate", b'{"value":1,"value":2}', b"diagnostic", 0, False),
                     ("nonfinite", b'{"value":NaN}', b"diagnostic", 0, False),
                     ("large-invalid-json", b" " * (8 * 1024 * 1024 + 1), b"diagnostic", 0, False)]
            if action.startswith("submit"):
                cases += [(name, dict(good, **change), b"diagnostic", 0, False) for name, change in (
                    ("false", {"accepted": False}), ("nonboolean", {"accepted": 1}),
                    ("outer-id", {"sessionId": "actual-goal"}), ("wrong-child", {"sessionId": "other-child"}),
                    ("alias", {"session_id": "other-child"}), ("rejected", {"outcome": "rejected"}))]
            else:
                cases += [("missing-session", {}, b"diagnostic", 0, False)]
            for name, response, stderr, returncode, disconnected in cases:
                with self.subTest(action=action, case=name):
                    # Given E32: valid conflict scope but failed/nonzero/disconnected/strict or unaccepted/wrong-child native output.
                    route = self.retain()
                    self.request.write_text(json.dumps(self.conflict_request("snake")))
                    files = (route, self.request, self.submission, self.runtime_root / ".textus/config.json")
                    before = {path: path.read_bytes() for path in files}
                    output = self.output()
                    process, raw = self.process_output(response, stderr, returncode, disconnected)
                    # When the selected conflict action makes its single native call.
                    status, printed, diagnostic, native = self.call(self.conflict_args(action, output), process)
                    # Then failure retains both streams and routing, with no promoted artifact, second call, Submit or Advance.
                    self.assertEqual(status, 2)
                    self.assertEqual(printed, "")
                    self.assertTrue(diagnostic.startswith("native-goal-command: "))
                    self.assertLessEqual(len(diagnostic), 535)
                    native.assert_called_once()
                    if action.startswith("submit"):
                        self.assert_one_call(native, self.conflict_submit_command(self.submission_value))
                    self.assertEqual({path.name for path in output.iterdir()}, {"response.json", "stderr.log"})
                    self.assertEqual((output / "response.json").read_bytes(), raw)
                    self.assertEqual((output / "stderr.log").read_bytes(), stderr)
                    self.assertEqual({path: path.read_bytes() for path in files}, before)

    def test_e33_conflict_exact_options_metadata_and_explicit_files_refuse_before_launch(self):
        """E33 / R2,R3,R10 / S6-01D14; finite pairs and supplied artifacts only."""
        for action in ("submit-repository-conflict", "prepare-execution", "record-execution"):
            required = (("request", "submission") if action.startswith("submit") else
                        ("request", "mapping", "origin") if action == "prepare-execution" else
                        ("request", "execution-session", "observation"))
            cases = [(kind, name) for kind in ("missing", "duplicate", "blank", "unpaired") for name in required]
            cases += [("extra", name) for name in ("unknown", "family", "operation", "service", "sessionId", "proposal", "input")]
            cases += [("file", (name, value)) for name in required for value in (
                "relative.json", str(self.root) + "/../absent.json", str(self.root / "absent.json"), str(self.root))]
            if action == "record-execution":
                cases += [("file", ("reference", str(self.root / "absent-reference.json")))]
            for case, value in cases:
                with self.subTest(action=action, case=case, value=value):
                    # Given E33: a missing/duplicate/blank/unpaired/inapplicable option or unavailable/noncanonical explicit file.
                    route = self.retain()
                    self.request.write_text(json.dumps(self.conflict_request()))
                    before = route.read_bytes()
                    output = self.output()
                    argv = self.conflict_args(action, output)
                    if case == "missing":
                        index = argv.index("--" + value)
                        del argv[index:index + 2]
                    elif case == "blank":
                        argv[argv.index("--" + value) + 1] = " "
                    elif case == "unpaired":
                        argv.append("--" + value)
                    elif case == "file":
                        name, path = value
                        if "--" + name in argv:
                            argv[argv.index("--" + name) + 1] = path
                        else:
                            argv += ["--" + name, path]
                    else:
                        argv += ["--" + value, "supplied"]
                    process, _raw = self.process_output({})
                    # When the finite conflict action checks exact pairs and selected files.
                    status, printed, diagnostic, native = self.call(argv, process)
                    # Then refusal precedes launch/output and keeps the retained Goal route.
                    self.assertEqual(status, 2)
                    self.assertEqual(printed, "")
                    self.assertTrue(diagnostic)
                    native.assert_not_called()
                    self.assertEqual(list(output.iterdir()), [])
                    self.assertEqual(route.read_bytes(), before)
        values = ({"result": {}}, {"resultReference": {}}, {"result": [], "resultReference": {}},
                  {"result": {}, "resultReference": None},
                  {"result": {}, "resultReference": {}, "completionFacts": {}},
                  {"result": {}, "resultReference": {}, "executionEvidence": []},
                  dict(self.submission_value, issuer="invented"), [], b"not-json",
                  b'{"result":{},"result":{},"resultReference":{}}',
                  b'{"result":{"value":NaN},"resultReference":{}}')
        for value in values:
            with self.subTest(metadata=value):
                # Given E33: missing/wrong-shaped/extra metadata or malformed strict submission JSON for a valid conflict route.
                route = self.retain()
                self.request.write_text(json.dumps(self.conflict_request()))
                self.submission.write_bytes(value if isinstance(value, bytes) else json.dumps(value).encode())
                before = {path: path.read_bytes() for path in (route, self.submission)}
                output = self.output()
                process, _raw = self.process_output({})
                # When conflict Submit consumes the supplied submission object.
                status, printed, diagnostic, native = self.call(self.conflict_args("submit-repository-conflict", output), process)
                # Then refusal precedes launch/output and leaves metadata/routing unchanged.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic)
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual({path: path.read_bytes() for path in before}, before)


    SPLIT_CASES = (
        ("submit-split-partition", "submitPartitionSemanticResult", "SplitPhasePartitionSemanticResult"),
        ("submit-split-scope", "submitHandoffScopeResult", "SplitPhaseScopeSemanticResult"),
        ("submit-split-reasoning", "submitReasoningResult", "SplitPhaseReasoningSemanticResult"),
    )

    def retain_split(self):
        path = self.task / "workflow-session.json"
        path.write_text(json.dumps({"family": "SplitPhase", "sessionId": "actual-split"}))
        return path

    def split_preparation_args(self, output):
        return self.args("prepare-split-input", output,
                         "--component-instance", "selected-component", "--allocation-reference", str(self.input),
                         "--allocation", str(self.plan), "--source", str(self.review_result),
                         "--reference", str(self.review_reference), "--participant", "selected participant",
                         "--issuer", "selected issuer", "--occurred-at", "2026-10-16T06:00:00Z",
                         "--collection", "entity:smworkflow.cli.split_input")

    def split_request(self, operation, result_type, spelling="camel"):
        _request, issued = self.execution_request()
        request = {"sessionId": "actual-split", "kind": "Request", "submitSessionId": "actual-split",
                   "submitOperation": operation, "resultType": result_type,
                   "issuedWorkContext": issued, "native_business": {"opaque": ["日本語", 7]}}
        for name, alias in (("sessionId", "session_id"), ("submitOperation", "submit_operation"),
                            ("submitSessionId", "submit_session_id"), ("resultType", "result_type"),
                            ("issuedWorkContext", "issued_work_context")):
            if spelling != "camel":
                request[alias] = request[name]
                if spelling == "snake":
                    del request[name]
        return request, issued

    def split_args(self, action, output):
        if action == "prepare-split-input":
            return self.split_preparation_args(output)
        if action == "start-split":
            return self.args(action, output, "--input", str(self.input))
        if action == "advance-split":
            return self.args(action, output)
        if action in {"prepare-execution", "record-execution"}:
            return self.execution_args(action, output)
        return self.args(action, output, "--request", str(self.request), "--submission", str(self.submission))

    def split_command(self, operation, *pairs):
        command = self.command(operation, *pairs)
        command[command.index("org.simplemodeling.textus.SmWorkflow.GoalPhaseService." + operation)] = (
            "org.simplemodeling.textus.SmWorkflow.SplitPhaseService." + operation)
        return command

    def test_e34_split_preparation_forwards_nine_fields_without_reading_route_or_content(self):
        """E34 / R1,R2,R3,R5,R11 / S6-01D15; passive native acquisition."""
        for occupied in (False, True):
            with self.subTest(occupied=occupied):
                # Given E34: four opaque non-JSON files, nine explicit fields and absent or invalid existing routing.
                route = self.task / "workflow-session.json"
                if occupied:
                    route.write_bytes(b"unreadable route is irrelevant to passive preparation")
                before_route = route.read_bytes() if route.exists() else None
                for path in (self.input, self.plan, self.review_result, self.review_reference):
                    path.write_bytes(b"opaque native-owned selected content")
                files = (self.input, self.plan, self.review_result, self.review_reference,
                         self.runtime_root / ".textus/config.json", self.runtime_root / ".textus/assembly.yaml")
                before = {path: path.read_bytes() for path in files}
                output = self.output()
                process, raw = self.process_output(b'{ "datastore": [], "native_display": "kept" }\n', b"warning\n")
                # When prepare-split-input receives one strict datastore acknowledgement.
                status, printed, diagnostic, native = self.call(self.split_preparation_args(output), process)
                # Then exact generated metadata/URIs go once to the fixed owner, without import, Start or route/config changes.
                self.assertEqual((status, diagnostic), (0, ""))
                self.assertEqual(printed, raw.decode())
                self.assert_one_call(native, self.preparation_command("prepareSplitStart", {
                    "componentInstance": "selected-component", "allocationReference": self.input.as_uri(),
                    "allocation": self.plan.as_uri(), "source": self.review_result.as_uri(),
                    "reference": self.review_reference.as_uri(), "participant": "selected participant",
                    "issuer": "selected issuer", "occurredAt": "2026-10-16T06:00:00Z",
                    "collection": "entity:smworkflow.cli.split_input"}))
                self.assertEqual({path: path.read_bytes() for path in files}, before)
                self.assertEqual(route.read_bytes() if route.exists() else None, before_route)
                self.assertEqual((output / "response.json").read_bytes(), raw)
                self.assertEqual((output / "stderr.log").read_bytes(), b"warning\n")
                self.assertEqual({path.name for path in output.iterdir()}, {"response.json", "stderr.log"})

    def test_e35_split_start_retains_canonical_id_and_advance_observes_once(self):
        """E35 / R2,R3,R5,R11 / S6-01D15; actual standalone lifecycle."""
        for proposal in (None, "proposed-split"):
            with self.subTest(proposal=proposal):
                # Given E35: opaque object text, absent routing and a canonical native ID differing from any proposal.
                route = self.task / "workflow-session.json"
                if route.exists():
                    route.unlink()
                self.input.write_text('{ "nativeField": {"kept": "日本語"} }\n', encoding="utf-8")
                before_input = self.input.read_bytes()
                output = self.output()
                argv = self.split_args("start-split", output)
                pairs = ["--input", self.input.read_text()]
                if proposal is not None:
                    argv += ["--proposal", proposal]
                    pairs += ["--sessionId", proposal]
                process, raw = self.process_output({"session_id": "actual-split", "message": "Started", "outcome": "started"})
                # When the explicit Split Start receives its actual acknowledgement.
                status, printed, diagnostic, native = self.call(argv, process)
                # Then one fixed Split call retains only the returned Split family/ID and untouched input.
                self.assertEqual((status, diagnostic), (0, ""))
                self.assertEqual(printed, raw.decode())
                self.assert_one_call(native, self.split_command("startSplitPhase", *pairs))
                self.assertEqual(json.loads(route.read_text()), {"family": "SplitPhase", "sessionId": "actual-split"})
                self.assertEqual(self.input.read_bytes(), before_input)
                self.assertEqual((output / "response.json").read_bytes(), raw)
                # Given E35: the now-occupied canonical route and a separate empty output.
                before = route.read_bytes()
                occupied_output = self.output()
                # When another Split Start selects the same task.
                status, printed, diagnostic, native = self.call(self.split_args("start-split", occupied_output), process)
                # Then it refuses before launch/output without replacing the canonical route.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic)
                native.assert_not_called()
                self.assertEqual(list(occupied_output.iterdir()), [])
                self.assertEqual(route.read_bytes(), before)
        for kind in ("Request", "Human", "Wait", "Terminal"):
            with self.subTest(kind=kind):
                # Given E35: retained canonical Split routing and one native observation with opaque display data.
                route = self.retain_split()
                before = route.read_bytes()
                output = self.output()
                process, raw = self.process_output({"sessionId": "actual-split", "session_id": "actual-split",
                    "kind": kind, "message": "Actual " + kind, "native_display": {"kept_field": True}}, b"warning\n")
                # When the runtime_root explicitly selects advance-split once.
                status, printed, diagnostic, native = self.call(self.split_args("advance-split", output), process)
                # Then fixed Split Advance awaits the actual response and displays only the original observation.
                self.assertEqual((status, diagnostic), (0, ""))
                self.assertEqual(printed, raw.decode())
                self.assert_one_call(native, self.split_command("advanceSplitPhase", "--sessionId", "actual-split"))
                self.assertEqual(route.read_bytes(), before)
                self.assertEqual((output / "response.json").read_bytes(), raw)
                self.assertEqual((output / "stderr.log").read_bytes(), b"warning\n")
        # Given E35: routing becomes occupied during the one native Split Start.
        route.unlink()
        output = self.output()
        process, raw = self.process_output({"sessionId": "actual-split", "message": "Started", "outcome": "started"},
                                           during=lambda: self.retain(session_id="concurrent-owner"))
        # When acknowledged Start tries to create its route exclusively.
        status, printed, diagnostic, native = self.call(self.split_args("start-split", output), process)
        # Then concurrent routing survives, with raw output and no second call.
        self.assertEqual(status, 2)
        self.assertEqual(printed, "")
        self.assertTrue(diagnostic)
        native.assert_called_once()
        self.assertEqual(json.loads(route.read_text()), {"family": "GoalPhase", "sessionId": "concurrent-owner"})
        self.assertEqual((output / "response.json").read_bytes(), raw)

    def test_e36_split_three_submissions_forward_opaque_values_and_same_id_once(self):
        """E36 / R2,R3,R5,R11 / S6-01D15; native assigned result destination."""
        for action, operation, result_type in self.SPLIT_CASES:
            for spelling in ("camel", "snake", "matching-both"):
                for optional in (False, True):
                    with self.subTest(action=action, spelling=spelling, optional=optional):
                        # Given E36: the exact issued semantic pair/aliases and opaque result without caller resultReference.
                        route = self.retain_split()
                        request, _issued = self.split_request(operation, result_type, spelling)
                        self.request.write_text(json.dumps(request))
                        value = {"result": {"native_business": {"日本語": [1, {"kept": True}]}}}
                        if optional:
                            value.update(completionFacts=[{"supplied": "fact"}], executionEvidence={"supplied": {"kept": True}})
                        self.submission.write_text(json.dumps(value, ensure_ascii=False))
                        files = (route, self.request, self.submission, self.runtime_root / ".textus/config.json")
                        before = {path: path.read_bytes() for path in files}
                        output = self.output()
                        process, raw = self.process_output(dict(self.acknowledged("actual-split"), session_id="actual-split"), b"warning\n")
                        # When the action-selected Split semantic Submit receives acceptance for that same saved ID.
                        status, printed, diagnostic, native = self.call(self.split_args(action, output), process)
                        # Then one fixed FQN receives only supplied JSON in declared order, with no destination, Advance or effects.
                        pairs = ["--sessionId", "actual-split"]
                        for name in ("result", "completionFacts", "executionEvidence"):
                            if name in value:
                                pairs += ["--" + name, json.dumps(value[name], ensure_ascii=False)]
                        self.assertEqual((status, diagnostic), (0, ""))
                        self.assertEqual(printed, raw.decode())
                        self.assert_one_call(native, self.split_command(operation, *pairs))
                        self.assertNotIn("--resultReference", native.call_args.args[0])
                        self.assertEqual({path: path.read_bytes() for path in files}, before)
                        self.assertEqual((output / "response.json").read_bytes(), raw)
                        self.assertEqual((output / "stderr.log").read_bytes(), b"warning\n")
                        self.assertEqual({path.name for path in output.iterdir()}, {"response.json", "stderr.log"})

    def test_e37_split_execution_keeps_whole_context_original_session_and_opaque_artifacts(self):
        """E37 / R2,R3,R5,R11 / S6-01D15; passive mapped handoff only."""
        for _submit, operation, result_type in self.SPLIT_CASES:
            for spelling in ("camel", "snake", "matching-both"):
                for completed in (False, True):
                    with self.subTest(operation=operation, spelling=spelling, completed=completed):
                        # Given E37: an exact Split Request with whole issued context and explicitly selected first mapping/origin.
                        route = self.retain_split()
                        request, issued = self.split_request(operation, result_type, spelling)
                        self.request.write_text(json.dumps(request))
                        files = (route, self.request, self.plan, self.input)
                        before = {path: path.read_bytes() for path in files}
                        prepared = self.output()
                        session = '{ "canonicalSession": {"kept_key": "日本語"} }\n'
                        selection = '{ "attempt": 1, "native_control": {"kept": true} }\n'
                        response = {"session": session}
                        if spelling != "snake":
                            response["nextSelection"] = selection
                        if spelling != "camel":
                            response["next_selection"] = selection
                        process, raw = self.process_output(response)
                        # When prepare-execution consumes that exact Split issue once.
                        status, printed, diagnostic, native = self.call(self.execution_args("prepare-execution", prepared), process)
                        # Then the fixed passive owner receives the unchanged whole context and explicit URIs; actual opaque artifacts survive.
                        self.assertEqual((status, diagnostic), (0, ""))
                        self.assertEqual(printed, raw.decode())
                        self.assert_one_call(native, self.preparation_command("prepareExecution", {
                            "issuedWorkContext": issued, "mapping": self.plan.as_uri(), "origin": self.input.as_uri()}))
                        session_path = prepared / "execution-session.json"
                        self.assertEqual(session_path.read_bytes(), session.encode())
                        self.assertEqual((prepared / "execution-selection.json").read_bytes(), selection.encode())
                        self.assertFalse((prepared / "execution-evidence.json").exists())
                        self.assertEqual({path: path.read_bytes() for path in files}, before)
                        # Given E37: the original returned Session and later changed mapping/origin, with actual supplied observation.
                        self.plan.write_bytes(b"changed mapping must not be used for recording")
                        self.input.write_bytes(b"changed origin must not be used for recording")
                        before = {path: path.read_bytes() for path in (*files, session_path)}
                        recorded = self.output()
                        actual = '{ "returnedSession": "opaque original lineage" }\n'
                        evidence = {"worker_identity": "supplied executor", "references": [{"identity": "supplied"}]}
                        response = {"session": actual}
                        field, value = ("completionEvidence", evidence) if completed else ("nextSelection", selection)
                        alias = "completion_evidence" if completed else "next_selection"
                        if spelling != "snake":
                            response[field] = value
                        if spelling != "camel":
                            response[alias] = value
                        process, raw = self.process_output(response)
                        # When record-execution explicitly selects the original Session/observation and optional real reference.
                        status, printed, diagnostic, native = self.call(self.execution_args(
                            "record-execution", recorded, reference=completed, session=session_path), process)
                        # Then one fixed recording call retains only actual Session/selection/evidence without re-preparation or Submit.
                        value = {"issuedWorkContext": issued, "session": session_path.as_uri(),
                                 "observation": self.review_result.as_uri()}
                        if completed:
                            value["reference"] = self.review_reference.as_uri()
                        self.assertEqual((status, diagnostic), (0, ""))
                        self.assertEqual(printed, raw.decode())
                        self.assert_one_call(native, self.preparation_command("recordExecution", value))
                        self.assertEqual((recorded / "execution-session.json").read_bytes(), actual.encode())
                        self.assertEqual((recorded / "execution-selection.json").exists(), not completed)
                        self.assertEqual((recorded / "execution-evidence.json").exists(), completed)
                        if completed:
                            self.assertEqual(json.loads((recorded / "execution-evidence.json").read_text()), evidence)
                        else:
                            self.assertEqual((recorded / "execution-selection.json").read_bytes(), selection.encode())
                        self.assertEqual({path: path.read_bytes() for path in before}, before)

    def test_e38_split_and_goal_crossed_routes_refuse_before_process_or_output(self):
        """E38 / R2,R3,R4,R11 / S6-01D15; closed family and issued routes."""
        replacements = [
            {"sessionId": "other-task"}, {"submitSessionId": "child"}, {"submitSessionId": " "},
            {"submitSessionId": None}, {"submitSessionId": 7}, {"kind": "Human"}, {"kind": "Wait"}, {"kind": "Terminal"},
            {"submitOperation": "submitGoalPhaseWorkResult"}, {"resultType": "GoalPhaseWorkResult"},
            {"submitOperation": "submitNativeMergeReview", "resultType": "RepositorySyncNativeMergeReviewResult", "submitSessionId": "child"},
            {"submitOperation": "submitNativeMergeRepair", "resultType": "RepositorySyncNativeMergeRepairResult", "submitSessionId": "child"},
            {"submitOperation": "submitRepositorySyncWorkResult", "resultType": "RepositorySyncConflictResult", "submitSessionId": "child"},
            {"session_id": "other"}, {"submit_operation": "other"}, {"submit_session_id": "other"}, {"result_type": "other"},
        ]
        for submit, operation, result_type in self.SPLIT_CASES:
            changes = replacements + [{"submitOperation": other_op} for _, other_op, _ in self.SPLIT_CASES if other_op != operation]
            changes += [{"resultType": other_type} for _, _, other_type in self.SPLIT_CASES if other_type != result_type]
            cases = [("replace", change) for change in changes]
            cases += [("missing", name) for name in ("sessionId", "submitSessionId", "kind", "submitOperation", "resultType")]
            cases += [("route", value) for value in (None, {}, [], {"family": "GoalPhase", "sessionId": "actual-split"},
                {"family": "RepositorySync", "sessionId": "actual-split"}, {"family": "SplitPhase", "sessionId": "other"},
                {"family": "SplitPhase", "sessionId": " "}, {"family": "SplitPhase", "sessionId": "actual-split", "extra": True})]
            for action in (submit, "prepare-execution", "record-execution"):
                for case, change in cases:
                    with self.subTest(action=action, pair=operation, case=case, change=change):
                        # Given E38: a missing/crossed family, session, kind, child, operation/type or conflicting consumed alias.
                        route = self.retain_split()
                        request, _issued = self.split_request(operation, result_type)
                        if case == "replace":
                            request.update(change)
                        elif case == "missing":
                            del request[change]
                        elif change is None:
                            route.unlink()
                        else:
                            route.write_text(json.dumps(change))
                        self.request.write_text(json.dumps(request))
                        self.submission.write_text('{"result":{}}')
                        before = route.read_bytes() if route.exists() else None
                        output = self.output()
                        process, _raw = self.process_output({})
                        # When the selected Split semantic or execution action consumes those exact supplied files.
                        status, printed, diagnostic, native = self.call(self.split_args(action, output), process)
                        # Then admission refuses before launch/output and preserves any supplied route.
                        self.assertEqual(status, 2)
                        self.assertEqual(printed, "")
                        self.assertTrue(diagnostic)
                        native.assert_not_called()
                        self.assertEqual(list(output.iterdir()), [])
                        self.assertEqual(route.read_bytes() if route.exists() else None, before)
        for action in ("advance", "submit", "submit-repository-review", "submit-repository-repair", "submit-repository-conflict",
                       "prepare-review-input", "prepare-repository-review-input", "advance-split"):
            with self.subTest(old_boundary=action):
                # Given E38: an otherwise legitimate old Goal Request under a retained Split route, or Split Advance under Goal.
                route = self.retain() if action == "advance-split" else self.retain_split()
                self.request.write_text(json.dumps(self.issued_request()))
                self.submission.write_text(json.dumps(self.submission_value))
                before = route.read_bytes()
                output = self.output()
                if action == "prepare-review-input":
                    argv = self.review_args(output)
                elif action == "prepare-repository-review-input":
                    argv = self.repository_review_args(action, output)
                elif action == "advance":
                    argv = self.args(action, output)
                else:
                    argv = self.split_args(action, output)
                process, _raw = self.process_output({})
                # When the explicitly selected action requires its own family.
                status, printed, diagnostic, native = self.call(argv, process)
                # Then old Goal actions retain Goal-only admission and Split Advance retains Split-only admission.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic)
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual(route.read_bytes(), before)
        for action in ("prepare-execution", "record-execution"):
            for change in ({"issuedWorkContext": {}}, {"issuedWorkContext": []}, {"issuedWorkContext": None},
                           {"issued_work_context": {"contradictory": True}}):
                with self.subTest(action=action, context=change):
                    # Given E38: an exact Split route but empty, wrong-shaped or contradictory whole issued context.
                    route = self.retain_split()
                    request, _issued = self.split_request(*self.SPLIT_CASES[0][1:])
                    request.update(change)
                    self.request.write_text(json.dumps(request))
                    before = route.read_bytes()
                    output = self.output()
                    process, _raw = self.process_output({})
                    # When the selected passive execution action consumes its issued context.
                    status, printed, diagnostic, native = self.call(self.execution_args(action, output), process)
                    # Then it refuses before launch/output rather than reconstructing context or changing route.
                    self.assertEqual(status, 2)
                    self.assertEqual(printed, "")
                    self.assertTrue(diagnostic)
                    native.assert_not_called()
                    self.assertEqual(list(output.iterdir()), [])
                    self.assertEqual(route.read_bytes(), before)

    def test_e39_split_native_failure_uncertainty_and_acknowledgement_stop_with_raw_streams(self):
        """E39 / R2,R3,R5,R11 / S6-01D15; no retry or promoted completion."""
        actions = ("prepare-split-input", "start-split", "advance-split", "prepare-execution", "record-execution")
        actions += tuple(row[0] for row in self.SPLIT_CASES)
        for action in actions:
            if action == "prepare-split-input":
                good = {"datastore": []}
                invalid = ({}, {"datastore": {}})
            elif action in {"prepare-execution", "record-execution"}:
                good = {"session": '{"opaque":"session"}'}
                invalid = ({}, {"session": {}}, dict(good, nextSelection="one", next_selection="two"),
                           dict(good, completionEvidence={}, completion_evidence={"other": True}),
                           dict(good, nextSelection=[]), dict(good, completionEvidence=[]))
            else:
                good = dict(self.acknowledged("actual-split"), kind="Request")
                if action == "start-split":
                    good["outcome"] = "started"
                invalid = (dict(good, sessionId=" "), dict(good, sessionId=None), dict(good, message=" "),
                           dict(good, session_id="other"))
                if action != "advance-split":
                    invalid += (dict(good, outcome="rejected"),)
                if action != "start-split":
                    invalid += (dict(good, sessionId="other"),)
                if action == "advance-split":
                    invalid += (dict(good, kind="Unknown"),)
                elif action != "start-split":
                    invalid += (dict(good, accepted=False), dict(good, accepted=1))
            cases = [("nonzero", good, b"diagnostic", 2, False), ("disconnected", b"partial", b"diagnostic", 0, True),
                     ("malformed", b"not-json", b"diagnostic", 0, False), ("nonobject", b"[]", b"diagnostic", 0, False),
                     ("duplicate", b'{"value":1,"value":2}', b"diagnostic", 0, False),
                     ("nonfinite", b'{"value":NaN}', b"diagnostic", 0, False),
                     ("large-invalid-json", b" " * (8 * 1024 * 1024 + 1), b"diagnostic", 0, False)]
            cases += [("invalid-ack-" + str(i), value, b"diagnostic", 0, False) for i, value in enumerate(invalid)]
            for name, response, stderr, returncode, disconnected in cases:
                with self.subTest(action=action, case=name):
                    # Given E39: valid explicit Split inputs but nonzero/disconnected/strict/contradictory native acknowledgement.
                    route = self.retain_split()
                    if action == "start-split":
                        route.unlink()
                    row = next((row for row in self.SPLIT_CASES if row[0] == action), self.SPLIT_CASES[0])
                    request, _issued = self.split_request(*row[1:], spelling="snake")
                    self.request.write_text(json.dumps(request))
                    self.submission.write_text('{"result":{"opaque":true}}')
                    files = (self.input, self.plan, self.request, self.submission, self.runtime_root / ".textus/config.json")
                    before = {path: path.read_bytes() for path in files}
                    before_route = route.read_bytes() if route.exists() else None
                    output = self.output()
                    process, raw = self.process_output(response, stderr, returncode, disconnected)
                    # When the one explicitly selected native action returns or times out.
                    status, printed, diagnostic, native = self.call(self.split_args(action, output), process)
                    # Then only available raw streams remain, with no retry, routing promotion, execution artifact, Submit or Advance.
                    self.assertEqual(status, 2)
                    self.assertEqual(printed, "")
                    self.assertTrue(diagnostic.startswith("native-goal-command: "))
                    self.assertLessEqual(len(diagnostic), 535)
                    native.assert_called_once()
                    self.assertNotIn("timeout", native.call_args.kwargs)
                    self.assertEqual({path.name for path in output.iterdir()}, {"response.json", "stderr.log"})
                    self.assertEqual((output / "response.json").read_bytes(), raw)
                    self.assertEqual((output / "stderr.log").read_bytes(), stderr)
                    self.assertEqual({path: path.read_bytes() for path in files}, before)
                    self.assertEqual(route.read_bytes() if route.exists() else None, before_route)

    def test_e40_split_options_collections_files_and_submission_metadata_are_exact(self):
        """E40 / R2,R3,R11 / S6-01D15; explicit bounded caller inputs."""
        required_by_action = {
            "prepare-split-input": ("component-instance", "allocation-reference", "allocation", "source", "reference",
                                    "participant", "issuer", "occurred-at", "collection"),
            "start-split": ("input",), "advance-split": (),
            **{row[0]: ("request", "submission") for row in self.SPLIT_CASES},
        }
        for action, required in required_by_action.items():
            cases = [(kind, name, None) for kind in ("missing", "duplicate", "blank", "unpaired") for name in required]
            cases += [("extra", name, None) for name in ("unknown", "family", "service", "operation", "target", "timeout", "sessionId")]
            if action != "start-split":
                cases += [("extra", "proposal", None)]
            if action == "advance-split":
                cases += [("extra", name, None) for name in ("request", "submission", "input", "participant")]
            file_names = ("allocation-reference", "allocation", "source", "reference") if action == "prepare-split-input" else required
            cases += [("file", name, value) for name in file_names for value in (
                "relative.json", str(self.root) + "/../absent.json", str(self.root / "absent.json"), str(self.root))]
            if action == "prepare-split-input":
                cases += [("collection", "collection", value) for value in (
                    "entity:smworkflow/cli/split_input", "entity:smworkflow.cli", "entity:smworkflow.cli.split.input",
                    "smworkflow.cli.split_input", "entity:1workflow.cli.split_input", "entity:smworkflow..split_input")]
            for kind, name, value in cases:
                with self.subTest(action=action, case=kind, option=name, value=value):
                    # Given E40: missing/duplicate/blank/unpaired/inapplicable options, malformed collection or noncanonical/unavailable file.
                    route = self.retain_split()
                    if action == "start-split":
                        route.unlink()
                    row = next((row for row in self.SPLIT_CASES if row[0] == action), self.SPLIT_CASES[0])
                    request, _issued = self.split_request(*row[1:])
                    self.request.write_text(json.dumps(request))
                    self.submission.write_text('{"result":{}}')
                    before = route.read_bytes() if route.exists() else None
                    output = self.output()
                    argv = self.split_args(action, output)
                    if kind == "missing":
                        index = argv.index("--" + name)
                        del argv[index:index + 2]
                    elif kind == "blank":
                        argv[argv.index("--" + name) + 1] = " "
                    elif kind == "unpaired":
                        argv.append("--" + name)
                    elif kind in {"file", "collection"}:
                        argv[argv.index("--" + name) + 1] = value
                    else:
                        argv += ["--" + name, "supplied"]
                    process, _raw = self.process_output({})
                    # When the finite Split action checks exact pairs and explicitly selected inputs.
                    status, printed, diagnostic, native = self.call(argv, process)
                    # Then it refuses before native launch/output and keeps supplied routing unchanged.
                    self.assertEqual(status, 2)
                    self.assertEqual(printed, "")
                    self.assertTrue(diagnostic)
                    native.assert_not_called()
                    self.assertEqual(list(output.iterdir()), [])
                    self.assertEqual(route.read_bytes() if route.exists() else None, before)
        values = ({}, {"resultReference": {}}, {"result": []}, {"result": None},
                  {"result": {}, "resultReference": {}}, {"result": {}, "completionFacts": {}},
                  {"result": {}, "executionEvidence": []}, {"result": {}, "issuer": "invented"},
                  [], b"not-json", b'{"result":{},"result":{}}', b'{"result":{"value":NaN}}')
        for action, operation, result_type in self.SPLIT_CASES:
            for value in values:
                with self.subTest(action=action, metadata=value):
                    # Given E40: valid Split issue with missing/wrong-shaped/unknown/resultReference or malformed strict submission metadata.
                    route = self.retain_split()
                    request, _issued = self.split_request(operation, result_type)
                    self.request.write_text(json.dumps(request))
                    self.submission.write_bytes(value if isinstance(value, bytes) else json.dumps(value).encode())
                    before = {path: path.read_bytes() for path in (route, self.submission)}
                    output = self.output()
                    process, _raw = self.process_output({})
                    # When the selected Split semantic action reads the exact supplied submission.
                    status, printed, diagnostic, native = self.call(self.split_args(action, output), process)
                    # Then no destination or metadata is invented; refusal precedes process/output and preserves files.
                    self.assertEqual(status, 2)
                    self.assertEqual(printed, "")
                    self.assertTrue(diagnostic)
                    native.assert_not_called()
                    self.assertEqual(list(output.iterdir()), [])
                    self.assertEqual({path: path.read_bytes() for path in before}, before)


    REPOSITORY_CASES = (
        ("submit-repository-review", "submitNativeMergeReview", "RepositorySyncNativeMergeReviewResult"),
        ("submit-repository-repair", "submitNativeMergeRepair", "RepositorySyncNativeMergeRepairResult"),
        ("submit-repository-conflict", "submitRepositorySyncWorkResult", "RepositorySyncConflictResult"),
    )

    def retain_repository(self, session_id="actual-repository"):
        path = self.task / "workflow-session.json"
        path.write_text(json.dumps({"family": "RepositorySync", "sessionId": session_id}))
        return path

    def standalone_repository_request(self, operation, result_type, spelling="camel"):
        request, issued = self.split_request(operation, result_type, spelling)
        for name in ("sessionId", "session_id", "submitSessionId", "submit_session_id"):
            if name in request:
                request[name] = "actual-repository"
        return request, issued

    def standalone_repository_args(self, action, output):
        if action == "start-repository":
            return self.args(action, output, "--input", str(self.input))
        if action == "advance-repository":
            return self.args(action, output)
        if action in {"prepare-execution", "record-execution"}:
            return self.execution_args(action, output)
        return self.repository_review_args(action, output)

    def standalone_repository_command(self, operation, *pairs):
        command = self.command(operation, *pairs)
        command[command.index(CALLER.FQN + operation)] = CALLER.REPOSITORY_FQN + operation
        return command

    def test_e41_repository_start_canonical_route_and_four_explicit_advances(self):
        """E41 / R2,R3,R5,R12 / S6-01D16; closed standalone lifecycle."""
        for proposal in (None, "actual-repository", "different-proposal"):
            for spelling in ("camel", "snake", "matching-both"):
                with self.subTest(proposal=proposal, spelling=spelling):
                    # Given E41: no route, opaque object input and actual canonical native ID with agreeing aliases.
                    route = self.task / "workflow-session.json"
                    if route.exists():
                        route.unlink()
                    self.input.write_text('{ "nativeBody": {"日本語": [1, true]} }\n', encoding="utf-8")
                    before_input = self.input.read_bytes()
                    output = self.output()
                    argv = self.standalone_repository_args("start-repository", output)
                    pairs = ["--input", self.input.read_text()]
                    if proposal is not None:
                        argv += ["--proposal", proposal]
                        pairs += ["--sessionId", proposal]
                    response = {"message": "Started", "outcome": "started"}
                    if spelling != "snake":
                        response["sessionId"] = "actual-repository"
                    if spelling != "camel":
                        response["session_id"] = "actual-repository"
                    process, raw = self.process_output(response, b"warning\n")
                    # When one explicit Repository Start acknowledges that actual ID.
                    status, printed, diagnostic, native = self.call(argv, process)
                    # Then fixed native Start retains only RepositorySync/canonical ID without changing input or streams.
                    self.assertEqual((status, diagnostic), (0, ""))
                    self.assertEqual(printed, raw.decode())
                    self.assert_one_call(native, self.standalone_repository_command("startRepositorySync", *pairs))
                    self.assertEqual(json.loads(route.read_text()), {"family": "RepositorySync", "sessionId": "actual-repository"})
                    self.assertEqual(self.input.read_bytes(), before_input)
                    self.assertEqual((output / "response.json").read_bytes(), raw)
                    self.assertEqual((output / "stderr.log").read_bytes(), b"warning\n")
                    # Given E41: occupied routing and another empty output.
                    before = route.read_bytes()
                    occupied = self.output()
                    # When another Start attempts the same task.
                    status, printed, diagnostic, native = self.call(self.standalone_repository_args("start-repository", occupied), process)
                    # Then it refuses before launching or writing and preserves the actual route.
                    self.assertEqual(status, 2)
                    self.assertEqual(printed, "")
                    self.assertTrue(diagnostic)
                    native.assert_not_called()
                    self.assertEqual(list(occupied.iterdir()), [])
                    self.assertEqual(route.read_bytes(), before)
        for kind in ("Request", "Human", "Wait", "Terminal"):
            with self.subTest(kind=kind):
                # Given E41: an independently reread canonical route and one of the four actual native kinds.
                actual_id = "repository-" + kind
                route = self.retain_repository(actual_id)
                before = route.read_bytes()
                output = self.output()
                process, raw = self.process_output({"sessionId": actual_id, "session_id": actual_id,
                    "kind": kind, "message": "Actual " + kind, "outcome": "opaque native outcome"})
                # When advance-repository explicitly observes once.
                status, printed, diagnostic, native = self.call(self.standalone_repository_args("advance-repository", output), process)
                # Then the fixed Advance uses that exact ID without an execution cutoff without interpreting opaque outcome or progressing.
                self.assertEqual((status, diagnostic), (0, ""))
                self.assertEqual(printed, raw.decode())
                self.assert_one_call(native, self.standalone_repository_command("advanceRepositorySync", "--sessionId", actual_id))
                self.assertEqual(route.read_bytes(), before)
                self.assertEqual((output / "response.json").read_bytes(), raw)
        # Given E41: another owner occupies routing during successful native Start.
        route.unlink()
        output = self.output()
        process, raw = self.process_output({"sessionId": "actual-repository", "message": "Started", "outcome": "started"},
                                           during=lambda: self.retain(session_id="concurrent-owner"))
        # When Start attempts its exclusive route creation.
        status, printed, diagnostic, native = self.call(self.standalone_repository_args("start-repository", output), process)
        # Then concurrent ownership survives with raw streams and no second Start.
        self.assertEqual(status, 2)
        self.assertEqual(printed, "")
        self.assertTrue(diagnostic)
        self.assert_one_call(native, self.standalone_repository_command("startRepositorySync", "--input", self.input.read_text()))
        self.assertEqual(json.loads(route.read_text()), {"family": "GoalPhase", "sessionId": "concurrent-owner"})
        self.assertEqual((output / "response.json").read_bytes(), raw)

    def test_e42_repository_three_semantic_pairs_forward_opaque_metadata_once(self):
        """E42 / R2,R3,R5,R12 / S6-01D16; same-ID Repository Submit."""
        for action, operation, result_type in self.REPOSITORY_CASES:
            for spelling in ("camel", "snake", "matching-both"):
                for optional in (False, True):
                    with self.subTest(action=action, spelling=spelling, optional=optional):
                        # Given E42: the exact same-ID issued pair/aliases and supplied opaque result/reference and optional metadata.
                        route = self.retain_repository()
                        request, _issued = self.standalone_repository_request(operation, result_type, spelling)
                        self.request.write_text(json.dumps(request))
                        value = {"result": {"native_business": {"日本語": [7, {"kept": True}]}},
                                 "resultReference": {"identity": "supplied original artifact", "revision": "17"}}
                        if optional:
                            value.update(completionFacts=[{"supplied": "fact"}], executionEvidence={"worker": {"actual": "fixture"}})
                        self.submission.write_text(json.dumps(value, ensure_ascii=False))
                        files = (route, self.request, self.submission, self.runtime_root / ".textus/config.json", self.runtime_root / ".textus/assembly.yaml")
                        before = {path: path.read_bytes() for path in files}
                        output = self.output()
                        response = self.acknowledged("actual-repository")
                        if spelling != "camel":
                            response["session_id"] = response["sessionId"]
                            if spelling == "snake":
                                del response["sessionId"]
                        process, raw = self.process_output(response, b"warning\n")
                        # When the exact action's fixed Repository Submit acknowledges that saved ID once.
                        status, printed, diagnostic, native = self.call(self.standalone_repository_args(action, output), process)
                        # Then all supplied values reach only the fixed pair and raw output/files remain without effects or Advance.
                        pairs = ["--sessionId", "actual-repository"]
                        for name in ("result", "resultReference", "completionFacts", "executionEvidence"):
                            if name in value:
                                pairs += ["--" + name, json.dumps(value[name], ensure_ascii=False)]
                        self.assertEqual((status, diagnostic), (0, ""))
                        self.assertEqual(printed, raw.decode())
                        self.assert_one_call(native, self.standalone_repository_command(operation, *pairs))
                        self.assertEqual({path: path.read_bytes() for path in files}, before)
                        self.assertEqual((output / "response.json").read_bytes(), raw)
                        self.assertEqual((output / "stderr.log").read_bytes(), b"warning\n")
                        self.assertEqual({path.name for path in output.iterdir()}, {"response.json", "stderr.log"})

    def test_e43_repository_review_preparation_uses_retained_family_and_whole_uris(self):
        """E43 / R2,R3,R5,R12 / S6-01D16; passive family-selected evidence."""
        for family in ("GoalPhase", "RepositorySync"):
            for spelling in ("camel", "snake", "matching-both"):
                with self.subTest(family=family, spelling=spelling):
                    # Given E43: a valid distinct Goal child or same-ID Repository review and explicit opaque selected files/metadata.
                    route = self.retain() if family == "GoalPhase" else self.retain_repository()
                    request = (self.repository_review_request(spelling) if family == "GoalPhase" else
                               self.standalone_repository_request(*self.REPOSITORY_CASES[0][1:], spelling)[0])
                    self.request.write_text(json.dumps(request))
                    for path in (self.review_result, self.review_reference, self.review_execution):
                        path.write_bytes(b"opaque bytes; the native reader owns this content")
                    files = (route, self.request, self.review_result, self.review_reference, self.review_execution,
                             self.runtime_root / ".textus/config.json", self.runtime_root / ".textus/assembly.yaml")
                    before = {path: path.read_bytes() for path in files}
                    output = self.output()
                    argv = self.standalone_repository_args("prepare-repository-review-input", output)
                    for name, value in (("issuer", "explicit supplied runtime_root"), ("occurred-at", "2026-10-16T06:17:18.123Z"),
                                        ("collection", "entity:Explicit.Host.review_input")):
                        argv[argv.index("--" + name) + 1] = value
                    process, raw = self.process_output({"datastore": [], "native_display": {"kept": True}}, b"warning\n")
                    # When the dedicated review preparation receives its one passive datastore acknowledgement.
                    status, printed, diagnostic, native = self.call(argv, process)
                    # Then only retained family/fixed review and complete selected URIs/metadata reach prepareEvidence, with files untouched.
                    self.assertEqual((status, diagnostic), (0, ""))
                    self.assertEqual(printed, raw.decode())
                    self.assert_one_call(native, self.preparation_command("prepareEvidence", {
                        "family": family, "kind": "review", "request": self.request.as_uri(),
                        "result": self.review_result.as_uri(), "reference": self.review_reference.as_uri(),
                        "executionEvidence": self.review_execution.as_uri(), "issuer": "explicit supplied runtime_root",
                        "occurredAt": "2026-10-16T06:17:18.123Z", "collection": "entity:Explicit.Host.review_input"}))
                    self.assertEqual({path: path.read_bytes() for path in files}, before)
                    self.assertEqual((output / "response.json").read_bytes(), raw)
                    self.assertEqual((output / "stderr.log").read_bytes(), b"warning\n")
                    self.assertEqual({path.name for path in output.iterdir()}, {"response.json", "stderr.log"})

    def test_e44_repository_execution_keeps_whole_context_original_session_and_artifacts(self):
        """E44 / R2,R3,R5,R12 / S6-01D16; passive retained execution."""
        for _submit, operation, result_type in self.REPOSITORY_CASES:
            for spelling in ("camel", "snake", "matching-both"):
                for completed in (False, True):
                    with self.subTest(operation=operation, spelling=spelling, completed=completed):
                        # Given E44: an exact same-ID Repository issue with opaque whole context and explicit original mapping/origin.
                        route = self.retain_repository()
                        request, issued = self.standalone_repository_request(operation, result_type, spelling)
                        self.request.write_text(json.dumps(request))
                        self.plan.write_bytes(b"explicit original mapping")
                        self.input.write_bytes(b"explicit original origin")
                        files = (route, self.request, self.plan, self.input)
                        before = {path: path.read_bytes() for path in files}
                        prepared = self.output()
                        session = '{ "canonicalSession": {"kept_key": "日本語"} }\n'
                        selection = '{ "attempt": 1, "native_control": {"kept": true} }\n'
                        response = {"session": session}
                        if spelling != "snake":
                            response["nextSelection"] = selection
                        if spelling != "camel":
                            response["next_selection"] = selection
                        process, raw = self.process_output(response)
                        # When prepare-execution calls its fixed passive owner once.
                        status, printed, diagnostic, native = self.call(self.execution_args("prepare-execution", prepared), process)
                        # Then whole issued context/URIs and opaque returned Session/selection remain exact without execution or Submit.
                        self.assertEqual((status, diagnostic), (0, ""))
                        self.assertEqual(printed, raw.decode())
                        self.assert_one_call(native, self.preparation_command("prepareExecution", {
                            "issuedWorkContext": issued, "mapping": self.plan.as_uri(), "origin": self.input.as_uri()}))
                        session_path = prepared / "execution-session.json"
                        self.assertEqual(session_path.read_bytes(), session.encode())
                        self.assertEqual((prepared / "execution-selection.json").read_bytes(), selection.encode())
                        self.assertFalse((prepared / "execution-evidence.json").exists())
                        self.assertEqual((prepared / "response.json").read_bytes(), raw)
                        self.assertEqual({path: path.read_bytes() for path in files}, before)
                        # Given E44: changed later configuration and the explicit original retained Session/actual observation.
                        self.plan.write_bytes(b"replacement mapping must not reconstruct pending selection")
                        self.input.write_bytes(b"replacement origin must not reconstruct pending selection")
                        before = {path: path.read_bytes() for path in (*files, session_path, self.review_result, self.review_reference)}
                        recorded = self.output()
                        actual = '{ "returnedSession": "original opaque lineage" }\n'
                        evidence = {"worker_identity": "supplied executor", "references": [{"identity": "supplied"}]}
                        response = {"session": actual}
                        field, alias, payload = (("completionEvidence", "completion_evidence", evidence) if completed else
                                                 ("nextSelection", "next_selection", selection))
                        if spelling != "snake":
                            response[field] = payload
                        if spelling != "camel":
                            response[alias] = payload
                        process, raw = self.process_output(response)
                        # When record-execution selects the original Session and observation plus reference only when supplied.
                        status, printed, diagnostic, native = self.call(self.execution_args(
                            "record-execution", recorded, reference=completed, session=session_path), process)
                        # Then only that Session is forwarded and actual artifacts survive without consulting the replacement configuration.
                        value = {"issuedWorkContext": issued, "session": session_path.as_uri(), "observation": self.review_result.as_uri()}
                        if completed:
                            value["reference"] = self.review_reference.as_uri()
                        self.assertEqual((status, diagnostic), (0, ""))
                        self.assertEqual(printed, raw.decode())
                        self.assert_one_call(native, self.preparation_command("recordExecution", value))
                        self.assertEqual((recorded / "execution-session.json").read_bytes(), actual.encode())
                        self.assertEqual((recorded / "execution-selection.json").exists(), not completed)
                        self.assertEqual((recorded / "execution-evidence.json").exists(), completed)
                        if completed:
                            self.assertEqual(json.loads((recorded / "execution-evidence.json").read_text()), evidence)
                        else:
                            self.assertEqual((recorded / "execution-selection.json").read_bytes(), selection.encode())
                        self.assertEqual((recorded / "response.json").read_bytes(), raw)
                        self.assertEqual({path: path.read_bytes() for path in before}, before)

    def test_e45_repository_closed_family_issued_route_and_context_refusals(self):
        """E45 / R2,R3,R4,R12 / S6-01D16; refusal before output/launch."""
        replacements = [
            {"sessionId": "other"}, {"submitSessionId": "child"}, {"submitSessionId": " "},
            {"submitSessionId": None}, {"submitSessionId": 7}, {"kind": "Human"}, {"kind": "Wait"}, {"kind": "Terminal"},
            {"submitOperation": "submitGoalPhaseWorkResult"}, {"resultType": "GoalPhaseWorkResult"},
            {"submitOperation": "submitPartitionSemanticResult", "resultType": "SplitPhasePartitionSemanticResult"},
            {"session_id": "other"}, {"submit_operation": "other"}, {"submit_session_id": "other"}, {"result_type": "other"},
        ]
        for submit, operation, result_type in self.REPOSITORY_CASES:
            actions = (submit, "prepare-execution", "record-execution")
            if submit == "submit-repository-review":
                actions += ("prepare-repository-review-input",)
            changes = replacements + [{"submitOperation": op} for _, op, _ in self.REPOSITORY_CASES if op != operation]
            changes += [{"resultType": typ} for _, _, typ in self.REPOSITORY_CASES if typ != result_type]
            common = [("replace", change) for change in changes]
            common += [("missing", name) for name in ("sessionId", "submitSessionId", "kind", "submitOperation", "resultType")]
            common += [("route", value) for value in (None, {}, [], {"family": "Unknown", "sessionId": "actual-repository"},
                {"family": "GoalPhase", "sessionId": "actual-repository"}, {"family": "SplitPhase", "sessionId": "actual-repository"},
                {"family": "RepositorySync", "sessionId": "other"}, {"family": "RepositorySync", "sessionId": " "},
                {"family": "RepositorySync", "sessionId": "actual-repository", "extra": True})]
            for action in actions:
                cases = list(common)
                if action in {"prepare-execution", "record-execution"}:
                    cases += [("replace", change) for change in ({"issuedWorkContext": {}}, {"issuedWorkContext": []},
                        {"issuedWorkContext": None}, {"issued_work_context": {"contradictory": True}})]
                    cases += [("missing", "issuedWorkContext")]
                for case, change in cases:
                    with self.subTest(action=action, pair=operation, case=case, change=change):
                        # Given E45: missing/crossed family/session/child/kind/pair/context or a contradictory consumed alias.
                        route = self.retain_repository()
                        request, _issued = self.standalone_repository_request(operation, result_type)
                        if case == "replace":
                            request.update(change)
                        elif case == "missing":
                            del request[change]
                        elif change is None:
                            route.unlink()
                        else:
                            route.write_text(json.dumps(change))
                        self.request.write_text(json.dumps(request))
                        before = route.read_bytes() if route.exists() else None
                        output = self.output()
                        process, _raw = self.process_output({})
                        # When the exact finite Repository action consumes the selected route and issued file.
                        status, printed, diagnostic, native = self.call(self.standalone_repository_args(action, output), process)
                        # Then refusal precedes output/launch, with original supplied routing preserved.
                        self.assertEqual(status, 2)
                        self.assertEqual(printed, "")
                        self.assertTrue(diagnostic)
                        native.assert_not_called()
                        self.assertEqual(list(output.iterdir()), [])
                        self.assertEqual(route.read_bytes() if route.exists() else None, before)
        for action in ("advance", "advance-split", "submit", "prepare-review-input",
                       "submit-split-partition", "submit-split-scope", "submit-split-reasoning"):
            with self.subTest(old_boundary=action):
                # Given E45: a retained standalone Repository and an otherwise valid original Goal or Split request.
                route = self.retain_repository("actual-goal" if action in {"advance", "submit", "prepare-review-input"} else "actual-split")
                row = next((row for row in self.SPLIT_CASES if row[0] == action), self.SPLIT_CASES[0])
                request = self.issued_request() if action in {"advance", "submit", "prepare-review-input"} else self.split_request(*row[1:])[0]
                self.request.write_text(json.dumps(request))
                self.submission.write_text(json.dumps(self.submission_value if action == "submit" else {"result": {}}))
                before = route.read_bytes()
                output = self.output()
                argv = (self.args(action, output) if action in {"advance", "advance-split"} else
                        self.review_args(output) if action == "prepare-review-input" else self.split_args(action, output))
                process, _raw = self.process_output({})
                # When an original family-specific action is selected under Repository routing.
                status, printed, diagnostic, native = self.call(argv, process)
                # Then its original family boundary refuses before output/launch without changing routing.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic)
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual(route.read_bytes(), before)
        for family in ("GoalPhase", "SplitPhase"):
            with self.subTest(repository_advance_family=family):
                # Given E45: a valid ID retained under another closed family.
                route = self.retain() if family == "GoalPhase" else self.retain_split()
                before = route.read_bytes()
                output = self.output()
                process, _raw = self.process_output({})
                # When advance-repository selects that other family.
                status, printed, diagnostic, native = self.call(self.standalone_repository_args("advance-repository", output), process)
                # Then only its own retained Repository family can launch.
                self.assertEqual(status, 2)
                self.assertEqual(printed, "")
                self.assertTrue(diagnostic)
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual(route.read_bytes(), before)

    def test_e46_repository_failure_uncertainty_and_acknowledgement_retain_raw_streams(self):
        """E46 / R2,R3,R5,R12 / S6-01D16; no retry/progression."""
        actions = ("start-repository", "advance-repository", "prepare-repository-review-input", "prepare-execution", "record-execution")
        actions += tuple(row[0] for row in self.REPOSITORY_CASES)
        for action in actions:
            if action == "prepare-repository-review-input":
                good = {"datastore": []}
                invalid = ({}, {"datastore": {}})
            elif action in {"prepare-execution", "record-execution"}:
                good = {"session": '{"opaque":"session"}'}
                invalid = ({}, {"session": {}}, {"session": " "}, dict(good, nextSelection="one", next_selection="two"),
                           dict(good, completionEvidence={}, completion_evidence={"other": True}),
                           dict(good, nextSelection=[]), dict(good, nextSelection=" "), dict(good, completionEvidence=[]))
            else:
                good = dict(self.acknowledged("actual-repository"), kind="Request")
                if action == "start-repository":
                    good["outcome"] = "started"
                invalid = (dict(good, sessionId=" "), dict(good, sessionId=None), dict(good, message=" "),
                           dict(good, session_id="other"))
                if action != "advance-repository":
                    invalid += (dict(good, outcome="rejected"),)
                if action != "start-repository":
                    invalid += (dict(good, sessionId="other"),)
                if action == "advance-repository":
                    invalid += (dict(good, kind="Unknown"), dict(good, kind=None))
                elif action != "start-repository":
                    invalid += (dict(good, accepted=False), dict(good, accepted=1))
            cases = [("nonzero", good, b"diagnostic", 2, False), ("disconnected", b"partial", b"diagnostic", 0, True),
                     ("malformed", b"not-json", b"diagnostic", 0, False), ("nonobject", b"[]", b"diagnostic", 0, False),
                     ("duplicate", b'{"value":1,"value":2}', b"diagnostic", 0, False),
                     ("nonfinite", b'{"value":NaN}', b"diagnostic", 0, False),
                     ("large-invalid-json", b" " * (8 * 1024 * 1024 + 1), b"diagnostic", 0, False)]
            cases += [("invalid-ack-" + str(i), value, b"diagnostic", 0, False) for i, value in enumerate(invalid)]
            for name, response, stderr, returncode, disconnected in cases:
                with self.subTest(action=action, case=name):
                    # Given E46: valid Repository inputs but failed/uncertain/strict or invalid native acknowledgement.
                    route = self.retain_repository()
                    if action == "start-repository":
                        route.unlink()
                    row = next((row for row in self.REPOSITORY_CASES if row[0] == action), self.REPOSITORY_CASES[0])
                    request, _issued = self.standalone_repository_request(*row[1:], spelling="snake")
                    self.request.write_text(json.dumps(request))
                    self.submission.write_text(json.dumps(self.submission_value))
                    files = (self.input, self.plan, self.request, self.submission, self.runtime_root / ".textus/config.json")
                    before = {path: path.read_bytes() for path in files}
                    before_route = route.read_bytes() if route.exists() else None
                    output = self.output()
                    process, raw = self.process_output(response, stderr, returncode, disconnected)
                    # When the selected native action returns or disconnects after its one invocation.
                    status, printed, diagnostic, native = self.call(self.standalone_repository_args(action, output), process)
                    # Then only available raw streams survive; no retry, promoted Session, route or success progression follows.
                    self.assertEqual(status, 2)
                    self.assertEqual(printed, "")
                    self.assertTrue(diagnostic.startswith("native-goal-command: "))
                    self.assertLessEqual(len(diagnostic), 535)
                    native.assert_called_once()
                    self.assertNotIn("timeout", native.call_args.kwargs)
                    self.assertEqual({path.name for path in output.iterdir()}, {"response.json", "stderr.log"})
                    self.assertEqual((output / "response.json").read_bytes(), raw)
                    self.assertEqual((output / "stderr.log").read_bytes(), stderr)
                    self.assertEqual({path: path.read_bytes() for path in files}, before)
                    self.assertEqual(route.read_bytes() if route.exists() else None, before_route)

    def test_e47_repository_exact_options_collections_files_and_submission_metadata(self):
        """E47 / R2,R3,R12 / S6-01D16; explicit finite inputs only."""
        required_by_action = {
            "start-repository": ("input",), "advance-repository": (),
            "prepare-repository-review-input": ("request", "result", "reference", "execution-evidence", "issuer", "occurred-at", "collection"),
            "prepare-execution": ("request", "mapping", "origin"),
            "record-execution": ("request", "execution-session", "observation"),
            **{row[0]: ("request", "submission") for row in self.REPOSITORY_CASES},
        }
        for action, required in required_by_action.items():
            cases = [(kind, name, None) for kind in ("missing", "duplicate", "blank", "unpaired") for name in required]
            cases += [("extra", name, None) for name in ("unknown", "family", "service", "operation", "target", "timeout", "sessionId")]
            if action != "start-repository":
                cases += [("extra", "proposal", None)]
            else:
                cases += [("proposal", "proposal", value) for value in (" ", " proposed", "proposed\nvalue")]
                cases += [("extra", "request", None), ("extra", "submission", None)]
            if action == "advance-repository":
                cases += [("extra", name, None) for name in ("request", "submission", "input", "participant")]
            names = tuple(name for name in required if name not in {"issuer", "occurred-at", "collection"})
            if action == "record-execution":
                names += ("reference",)
            cases += [("file", name, value) for name in names for value in (
                "relative.json", str(self.root) + "/../absent.json", str(self.root / "absent.json"), str(self.root))]
            if action == "prepare-repository-review-input":
                cases += [("collection", "collection", value) for value in (
                    "entity:smworkflow/cli/reviews", "entity:smworkflow.cli", "entity:smworkflow.cli.runtime_root.review",
                    "smworkflow.cli.reviews", "entity:1workflow.cli.reviews", "entity:smworkflow..reviews")]
            for kind, name, value in cases:
                with self.subTest(action=action, case=kind, option=name, value=value):
                    # Given E47: missing/duplicate/blank/unpaired/inapplicable options, invalid collections or explicit selected paths.
                    route = self.retain_repository()
                    if action == "start-repository":
                        route.unlink()
                    row = next((row for row in self.REPOSITORY_CASES if row[0] == action), self.REPOSITORY_CASES[0])
                    request, _issued = self.standalone_repository_request(*row[1:])
                    self.request.write_text(json.dumps(request))
                    before = route.read_bytes() if route.exists() else None
                    output = self.output()
                    argv = self.standalone_repository_args(action, output)
                    if kind == "missing":
                        index = argv.index("--" + name)
                        del argv[index:index + 2]
                    elif kind == "blank":
                        argv[argv.index("--" + name) + 1] = " "
                    elif kind == "unpaired":
                        argv.append("--" + name)
                    elif kind in {"file", "collection", "proposal"}:
                        if "--" + name in argv:
                            argv[argv.index("--" + name) + 1] = value
                        else:
                            argv += ["--" + name, value]
                    else:
                        argv += ["--" + name, "supplied"]
                    process, _raw = self.process_output({})
                    # When the finite Repository action validates exact pairs and selected metadata.
                    status, printed, diagnostic, native = self.call(argv, process)
                    # Then refusal precedes output/launch and leaves any supplied route unchanged.
                    self.assertEqual(status, 2)
                    self.assertEqual(printed, "")
                    self.assertTrue(diagnostic)
                    native.assert_not_called()
                    self.assertEqual(list(output.iterdir()), [])
                    self.assertEqual(route.read_bytes() if route.exists() else None, before)
        values = ({}, {"result": {}}, {"resultReference": {}}, {"result": [], "resultReference": {}},
                  {"result": None, "resultReference": {}}, {"result": {}, "resultReference": []},
                  {"result": {}, "resultReference": None}, {"result": {}, "resultReference": {}, "completionFacts": {}},
                  {"result": {}, "resultReference": {}, "executionEvidence": []}, dict(self.submission_value, issuer="invented"),
                  [], b"not-json", b'{"result":{},"result":{},"resultReference":{}}',
                  b'{"result":{"value":NaN},"resultReference":{}}')
        for action, operation, result_type in self.REPOSITORY_CASES:
            for value in values:
                with self.subTest(action=action, metadata=value):
                    # Given E47: a valid same-ID pair but missing/wrong-shaped/extra or malformed strict submission metadata.
                    route = self.retain_repository()
                    request, _issued = self.standalone_repository_request(operation, result_type)
                    self.request.write_text(json.dumps(request))
                    self.submission.write_bytes(value if isinstance(value, bytes) else json.dumps(value).encode())
                    before = {path: path.read_bytes() for path in (route, self.request, self.submission)}
                    output = self.output()
                    process, _raw = self.process_output({})
                    # When the selected Repository Submit reads the supplied metadata.
                    status, printed, diagnostic, native = self.call(self.standalone_repository_args(action, output), process)
                    # Then required object reference/result and only declared optional types are admitted before any launch/output.
                    self.assertEqual(status, 2)
                    self.assertEqual(printed, "")
                    self.assertTrue(diagnostic)
                    native.assert_not_called()
                    self.assertEqual(list(output.iterdir()), [])
                    self.assertEqual({path: path.read_bytes() for path in before}, before)


class CallerInformationSpecification(unittest.TestCase):
    """Caller routing fixtures are observations, never workflow completion or Human consent."""

    setUp = NativeGoalCommandSpecification.setUp
    write_json = NativeGoalCommandSpecification.write_json
    issued_request = NativeGoalCommandSpecification.issued_request
    retain = NativeGoalCommandSpecification.retain
    output = NativeGoalCommandSpecification.output
    args = NativeGoalCommandSpecification.args
    call = NativeGoalCommandSpecification.call
    process_output = NativeGoalCommandSpecification.process_output

    def information(self):
        self.retain()
        request = self.issued_request()
        request["issuedWorkContext"] = {"sessionId": "actual-goal", "expectedRevision": "7",
            "continuationId": "retained-continuation", "mode": "GoalPhase"}
        self.request.write_text(json.dumps(request))
        source = {"threadId": "thread", "turnId": "turn", "itemId": "item"}
        locator = self.write_json("source-locator.json", source)
        prepared = {"issuedWorkContext": request["issuedWorkContext"], "kind": "execution-terminal",
            "source": source, "callerIdentity": "existing-caller"}
        return request, locator, prepared

    def test_prepare_returns_request_to_same_caller_without_following_operation(self):
        # Given actual retained routing and an explicitly selected exact source locator.
        request, locator, prepared = self.information()
        routing = (self.task / "workflow-session.json").read_bytes()
        output = self.output()
        invoke, raw = self.process_output(prepared)
        # When the caller asks the ordinary preparation Operation for information.
        status, printed, diagnostic, native = self.call(self.args("prepare-information", output,
            "--request", str(self.request), "--information-kind", "execution-terminal",
            "--source-locator", str(locator), "--caller-identity", "existing-caller"), invoke)
        # Then one passive call retains the exact issued context and does not Submit, recover or Advance.
        self.assertEqual((status, diagnostic), (0, ""))
        self.assertEqual(native.call_count, 1)
        command = native.call_args.args[0]
        self.assertIn("NativeWorkflowPreparationService.prepareCallerInformation", " ".join(command))
        self.assertEqual(json.loads(command[command.index("--input") + 1]), prepared)
        self.assertEqual(json.loads((output / "caller-information-request.json").read_text()), prepared)
        self.assertEqual((self.task / "workflow-session.json").read_bytes(), routing)
        self.assertEqual((output / "response.json").read_bytes(), raw)

    def test_returned_availability_remains_information_and_preserves_routing(self):
        for outcome in ("available", "partial", "unavailable"):
            with self.subTest(outcome=outcome):
                # Given a retained information request and supplied attributable observation, including absence.
                request, locator, prepared = self.information()
                retained = self.write_json("information-request.json", prepared)
                observation = self.write_json("observation.json", {"request": prepared, "outcome": outcome,
                    "observedAt": "2026-10-23T00:00:00Z", "detail": "Actual availability"})
                before = (self.task / "workflow-session.json").read_bytes()
                result = {"request": prepared, "outcome": outcome, "detail": "Actual availability"}
                invoke, raw = self.process_output(result)
                output = self.output()
                # When the caller returns observation through the finite ordinary Operation.
                status, printed, diagnostic, native = self.call(self.args("record-information", output,
                    "--request", str(self.request), "--information-request", str(retained),
                    "--observation", str(observation)), invoke)
                # Then each availability is displayed/retained after exactly one call with unchanged Session routing.
                self.assertEqual((status, diagnostic), (0, ""))
                self.assertEqual(native.call_count, 1)
                command = native.call_args.args[0]
                self.assertIn("NativeWorkflowPreparationService.recordCallerInformation", " ".join(command))
                self.assertEqual(json.loads(command[command.index("--input") + 1]), {
                    "issuedWorkContext": request["issuedWorkContext"], "request": retained.as_uri(),
                    "observation": observation.as_uri()})
                self.assertEqual(json.loads((output / "caller-information-result.json").read_text()), result)
                self.assertIn(outcome, printed)
                self.assertEqual((self.task / "workflow-session.json").read_bytes(), before)

    def test_crossed_session_or_withdrawn_provider_action_refuses_before_launch(self):
        # Given a Request from a different Session or the withdrawn provider-fetch action.
        request, locator, prepared = self.information()
        request["sessionId"] = "other-session"
        self.request.write_text(json.dumps(request))
        for action in ("prepare-information", "recover-whole-phase-from-codex"):
            output = self.output()
            invoke, raw = self.process_output(prepared)
            arguments = self.args(action, output, "--request", str(self.request))
            if action == "prepare-information":
                arguments += ["--information-kind", "execution-terminal", "--source-locator", str(locator),
                    "--caller-identity", "existing-caller"]
            # When finite action validation checks the selected current task.
            status, printed, diagnostic, native = self.call(arguments, invoke)
            # Then neither crossing nor the withdrawn action launches any CLI process.
            self.assertEqual(status, 2)
            native.assert_not_called()
            self.assertEqual(list(output.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
