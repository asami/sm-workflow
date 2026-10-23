# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Behavioral specifications for PHASE-6.3 / HOST / HOST-A completion.

Temporary prerequisites and subprocess responses are fixtures, not installed
native execution, genuine provenance, authority or task/Phase acceptance.
Route examples supply native-call boundary fixtures and capture telemetry separately; transport
examples use finite owned Python children to observe live streams and runtime termination.
Real caller routing, files and output are used.
@since   Oct. 19, 2026
@version Oct. 19, 2026
@author  ASAMI, Tomoharu
"""

from contextlib import redirect_stderr, redirect_stdout
import importlib.util
import io
import json
import os
import signal
from pathlib import Path
import subprocess
import sys
import time
import tempfile
import unittest
from unittest.mock import Mock, patch


SOURCE = Path(__file__).resolve().parents[1] / "native-goal-command.py"
SPEC = importlib.util.spec_from_file_location("native_host_progression", SOURCE)
CALLER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CALLER)

GOAL_PAIR = ("submitGoalPhaseWorkResult", "GoalPhaseWorkResult")
HUMAN_PAIR = ("submitGoalPhaseOrderedDecision", "GoalPhaseOrderedDecisionAnswer")
SPLIT_PAIRS = (
    ("submitPartitionSemanticResult", "SplitPhasePartitionSemanticResult"),
    ("submitHandoffScopeResult", "SplitPhaseScopeSemanticResult"),
    ("submitReasoningResult", "SplitPhaseReasoningSemanticResult"),
)
REPOSITORY_PAIRS = (
    ("submitNativeMergeReview", "RepositorySyncNativeMergeReviewResult"),
    ("submitNativeMergeRepair", "RepositorySyncNativeMergeRepairResult"),
    ("submitRepositorySyncWorkResult", "RepositorySyncConflictResult"),
)
SERVICES = {
    "GoalPhase": "org.simplemodeling.textus.SmWorkflow.GoalPhaseService.",
    "SplitPhase": "org.simplemodeling.textus.SmWorkflow.SplitPhaseService.",
    "RepositorySync": "org.simplemodeling.textus.SmWorkflow.RepositorySyncService.",
}


class Observations(io.StringIO):
    def __init__(self):
        super().__init__()
        self.flushed = []

    def flush(self):
        self.flushed.append(self.getvalue())
        super().flush()


class NativeHostProgressionSpecification(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.executable = self.root / "installed CLI" / "sm-workflow"
        self.runtime_root = self.root / "runtime_root"
        self.task = self.runtime_root / "target/sm-workflow/tasks/owned-task"
        self.task.mkdir(parents=True)
        for path in (
            self.executable,
            self.runtime_root / ".textus/config.json",
            self.runtime_root / ".textus/assembly.yaml",
        ):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("fixture prerequisite", encoding="utf-8")
        self.executable.chmod(0o755)
        self.route = self.task / "workflow-session.json"
        self.request = self.root / "request.json"
        self.submission = self.root / "submission.json"
        self.counter = 0
        self.select()

    def save(self, path, value):
        path.write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")

    def select(self, family="GoalPhase", pair=GOAL_PAIR, child=False, wire=False):
        self.family = family
        self.outer_id = "actual-" + family
        self.submit_id = "actual-repository-child" if child else self.outer_id
        self.pair = pair
        self.save(self.route, {"family": family, "sessionId": self.outer_id})
        request = {
            "kind": "Request", "sessionId": self.outer_id,
            "submitOperation": pair[0], "submitSessionId": self.submit_id,
            "resultType": pair[1], "work": {"kind": "ImplementStep"},
            "message": "Presentation is not a route or command",
        }
        if wire:
            for name, alias in (("sessionId", "session_id"), ("submitOperation", "submit_operation"),
                                ("submitSessionId", "submit_session_id"), ("resultType", "result_type")):
                request[alias] = request.pop(name)
        self.save(self.request, request)
        supplied = {"result": {"summary": "Fixture semantic result"},
                    "completionFacts": [{"fixture": True}],
                    "executionEvidence": {"fixture": "not real execution"}}
        if family != "SplitPhase":
            supplied["resultReference"] = {"identity": "fixture", "revision": "1"}
        self.save(self.submission, supplied)

    def output(self):
        self.counter += 1
        output = self.task / ("complete-" + str(self.counter))
        output.mkdir()
        return output

    def args(self, output):
        return ["--executable", str(self.executable), "--runtime-root", str(self.runtime_root),
                "--task", str(self.task), "--action", "complete", "--output", str(output),
                "--request", str(self.request), "--submission", str(self.submission)]

    def submit_response(self):
        return {"sessionId": self.submit_id, "message": "Actual fixture acknowledgement",
                "accepted": True, "outcome": "accepted"}

    def select_human(self, wire=False):
        self.select(pair=HUMAN_PAIR, wire=wire)
        request = json.loads(self.request.read_text())
        request.update(kind="Human", human={"hostContext": {"question": {"opaque": True}}})
        self.save(self.request, request)
        self.save(self.submission, {"answer": {"choice": "AuthorizeNewAttempt", "rationale": "Fixture direction"},
                                   "resultReference": {"identity": "fixture-human", "revision": "1"},
                                   "executionEvidence": {"workerIdentity": "test-independent-human"}})
        self.provenance = self.root / "human-provenance.json"
        self.save(self.provenance, {"participant": "participant", "issuer": "normal-development-runtime_root",
                                    "occurredAt": "2026-10-21T00:00:00Z",
                                    "collection": "entity:smworkflow.cli.host_evidence"})

    def human_args(self, output):
        return self.args(output) + ["--human-provenance", str(self.provenance)]

    def advance_response(self, kind="Request"):
        return {"sessionId": self.outer_id, "message": "Actual fixture frontier", "kind": kind}

    def raw(self, value):
        return value if isinstance(value, bytes) else (json.dumps(value, indent=2) + "\n").encode("utf-8")

    def call(self, output, responses, fault=None, during=None, argv=None):
        observations, diagnostic = Observations(), io.StringIO()
        calls = []

        def invoke(command, **kwargs):
            index = len(calls)
            calls.append(command)
            self.assertEqual(set(kwargs), {"cwd", "stdout", "stderr"})
            self.assertEqual(kwargs["cwd"], str(self.runtime_root))
            if during is not None:
                during(index, observations)
            kwargs["stdout"].write(self.raw(responses[index]))
            kwargs["stderr"].write(("fixture stderr " + str(index)).encode("utf-8"))
            if fault == (index, "disconnected"):
                raise OSError("fixture runtime disconnected")
            if fault == (index, "transport"):
                raise OSError("fixture transport failure")
            return subprocess.CompletedProcess(command, 7 if fault == (index, "nonzero") else 0)

        native = Mock(side_effect=invoke)

        def fixture(command, runtime_root, descriptor, context):
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
            # Keep business DTO assertions independent of the additional,
            # separately tested progress records.
            with patch.object(CALLER, "progress") as telemetry, redirect_stdout(observations), redirect_stderr(diagnostic):
                status = CALLER.main(self.args(output) if argv is None else argv)
            native.telemetry = telemetry.call_args_list
        return status, observations, diagnostic.getvalue(), calls, native

    def expected_command(self, service, operation, pairs):
        return [str(self.executable), "--runtime-root", str(self.runtime_root), "command", SERVICES[service] + operation, *pairs, "--json"]

    def assert_success(self, output, responses, during=None):
        # Given the selected issued route, prepared output and native response fixtures,
        original_route = self.route.read_bytes()
        # When complete consumes the semantic result through the real caller,
        status, observed, diagnostic, calls, native = self.call(output, responses, during=during)
        # Then it succeeds with exact Submit/Advance routing, flushed observations and retained raw streams.
        self.assertEqual(status, 0, diagnostic)
        self.assertEqual(diagnostic, "")
        self.assertEqual(native.call_count, 2)
        supplied = json.loads(self.submission.read_text(encoding="utf-8"))
        pairs = ["--sessionId", self.submit_id]
        for name in ("result", "resultReference", "completionFacts", "executionEvidence"):
            if name in supplied:
                pairs.extend(["--" + name, json.dumps(supplied[name], ensure_ascii=False)])
        submit_family = "RepositorySync" if self.pair in REPOSITORY_PAIRS else self.family
        self.assertEqual(calls, [
            self.expected_command(submit_family, self.pair[0], pairs),
            self.expected_command(self.family, "advance" + self.family, ["--sessionId", self.outer_id]),
        ])
        lines = observed.getvalue().splitlines()
        self.assertEqual(len(lines), 2)
        self.assertEqual([json.loads(line) for line in lines], responses)
        self.assertEqual(observed.flushed, [lines[0] + "\n", observed.getvalue()])
        self.assertEqual(self.route.read_bytes(), original_route)
        for index, directory in enumerate((output, output / "advance")):
            self.assertEqual((directory / "response.json").read_bytes(), self.raw(responses[index]))
            self.assertEqual((directory / "stderr.log").read_bytes(), ("fixture stderr " + str(index)).encode())

    def test_goal_call_order_and_submit_flush_before_advance(self):
        # Given an actual-shaped Goal Request and a prepared empty output directory,
        responses = [self.submit_response(), self.advance_response()]
        output = self.output()

        # Given an observation hook that checks Submit flushing at each native call boundary,
        def during(index, observations):
            self.assertTrue((output / "advance").is_dir())
            if index == 0:
                self.assertEqual(observations.getvalue(), "")
                self.assertEqual(list((output / "advance").iterdir()), [])
            else:
                self.assertEqual([json.loads(line) for line in observations.getvalue().splitlines()], responses[:1])
                self.assertEqual(observations.flushed, [observations.getvalue()])

        # Shared completion action and expectations check one ordinary Goal Advance and both raw streams;
        # the observation hook checks that Submit is flushed before Advance.
        self.assert_success(output, responses, during)

    def test_human_complete_prepares_imports_submits_and_advances_once(self):
        # Given actual-shaped independent fixture inputs and an opaque issued Human, using either native spelling.
        for wire in (False, True):
            with self.subTest(wire=wire):
                self.select_human(wire)
                output = self.output()
                config = (self.runtime_root / ".textus/config.json").read_bytes()
                request = self.request.read_bytes()
                prepared = {"datastore": [{"fixture": "native-generated rows, not live consent"}]}
                responses = [prepared, self.submit_response(), self.advance_response()]

                def during(index, observed):
                    self.assertEqual([json.loads(line) for line in observed.getvalue().splitlines()], responses[:index])
                    if index > 0:
                        self.assertEqual((output / "preparation/response.json").read_bytes(), self.raw(prepared))

                # When the existing finite action consumes those supplied records.
                status, observed, diagnostic, calls, _native = self.call(
                    output, responses, argv=self.human_args(output), during=during)
                # Then the normal preparation, fixed binding Submit and unbound ordinary Advance retain actual outputs.
                self.assertEqual(status, 0, diagnostic)
                self.assertEqual(len(calls), 3)
                self.assertEqual(calls[0][:5], [str(self.executable), "--runtime-root", str(self.runtime_root), "command",
                    "org.simplemodeling.textus.SmWorkflow.NativeWorkflowPreparationService.prepareEvidence"])
                value = json.loads(calls[0][6])
                supplied = json.loads(self.submission.read_text())
                provenance = json.loads(self.provenance.read_text())
                self.assertEqual({name: value[name] for name in provenance}, provenance)
                self.assertEqual(value["family"], "GoalPhase")
                self.assertEqual(value["kind"], "human")
                self.assertEqual(value["request"], self.request.as_uri())
                for field, source in (("result", "answer"), ("reference", "resultReference"),
                                      ("executionEvidence", "executionEvidence")):
                    path = output / "preparation" / (source + ".json")
                    self.assertEqual(value[field], path.as_uri())
                    self.assertEqual(json.loads(path.read_text()), supplied[source])
                pairs = ["--sessionId", self.outer_id]
                for name in ("answer", "resultReference", "executionEvidence"):
                    pairs.extend(["--" + name, json.dumps(supplied[name], ensure_ascii=False)])
                expected = self.expected_command("GoalPhase", HUMAN_PAIR[0], pairs)
                expected.insert(3, "--textus.import.data.file=" + str(output / "preparation/response.json"))
                self.assertEqual(calls[1], expected)
                self.assertEqual(calls[2], self.expected_command("GoalPhase", "advanceGoalPhase", ["--sessionId", self.outer_id]))
                self.assertEqual([json.loads(line) for line in observed.getvalue().splitlines()], responses)
                self.assertEqual((self.runtime_root / ".textus/config.json").read_bytes(), config)
                self.assertEqual(self.request.read_bytes(), request)
                for index, directory in enumerate((output / "preparation", output, output / "advance")):
                    self.assertEqual((directory / "response.json").read_bytes(), self.raw(responses[index]))
                    self.assertEqual((directory / "stderr.log").read_bytes(), ("fixture stderr " + str(index)).encode())

    def test_human_missing_or_invalid_supplied_records_stop_before_effects(self):
        # Given absent provenance, nonobject/extra submission fields, and strict invalid provenance JSON.
        cases = [("missing", None), ("submission", {}), ("submission", {"answer": []}),
                 ("submission", {"answer": {}, "resultReference": {}, "executionEvidence": {}, "result": {}}),
                 ("provenance", {}), ("provenance", {"participant": " participant"}),
                 ("provenance", {"collection": "entity:invalid"}),
                 ("provenance-raw", '{"participant":"a","participant":"b"}'),
                 ("submission-raw", '{"answer":NaN}'), ("path", "relative.json")]
        for kind, value in cases:
            with self.subTest(kind=kind, value=value):
                self.select_human()
                output = self.output()
                argv = self.human_args(output)
                if kind == "missing":
                    argv = self.args(output)
                elif kind == "path":
                    argv[-1] = value
                elif kind.endswith("-raw"):
                    (self.provenance if kind.startswith("provenance") else self.submission).write_text(value)
                elif kind == "provenance":
                    current = json.loads(self.provenance.read_text()) if value else {}
                    current.update(value)
                    self.save(self.provenance, current)
                else:
                    self.save(self.submission, value)
                # When complete admits the supplied finite inputs.
                status, observed, _diagnostic, calls, _native = self.call(output, [], argv=argv)
                # Then no preparation, Submit, Advance, output write or implicit retry occurs.
                self.assertEqual(status, 2)
                self.assertEqual(observed.getvalue(), "")
                self.assertEqual(calls, [])
                self.assertEqual(list(output.iterdir()), [])

    def test_human_crossed_routes_and_semantic_provenance_stop_before_effects(self):
        # Given crossed families, IDs, pairs, colliding consumed aliases or provenance on a semantic Request.
        cases = [{"family": "SplitPhase"}, {"family": "RepositorySync"}, {"sessionId": "other"},
                 {"submitSessionId": "other"}, {"submitOperation": GOAL_PAIR[0]}, {"resultType": GOAL_PAIR[1]}]
        cases += [{name: "contradictory"} for name in ("session_id", "submit_session_id", "submit_operation", "result_type")]
        cases += [{"kind": "Request", "submitOperation": GOAL_PAIR[0], "resultType": GOAL_PAIR[1]}]
        for changes in cases:
            with self.subTest(changes=changes):
                self.select_human()
                if "family" in changes:
                    self.save(self.route, {"family": changes["family"], "sessionId": self.outer_id})
                else:
                    request = json.loads(self.request.read_text())
                    request.update(changes)
                    self.save(self.request, request)
                output = self.output()
                # When complete selects its issued route.
                status, _observed, _diagnostic, calls, _native = self.call(output, [], argv=self.human_args(output))
                # Then all effects and retries remain absent.
                self.assertEqual(status, 2)
                self.assertEqual(calls, [])
                self.assertEqual(list(output.iterdir()), [])

    def test_human_provenance_option_is_inapplicable_to_other_actions(self):
        # Given explicit provenance on an existing single-call advance action.
        self.select_human()
        output = self.output()
        argv = ["--executable", str(self.executable), "--runtime-root", str(self.runtime_root),
                "--task", str(self.task), "--action", "advance", "--output", str(output),
                "--human-provenance", str(self.provenance)]
        # When strict named-option admission selects that action.
        status, _observed, diagnostic, calls, _native = self.call(output, [], argv=argv)
        # Then provenance remains complete-only and no native effect or output is created.
        self.assertEqual(status, 2)
        self.assertIn("action-inapplicable argument", diagnostic)
        self.assertEqual(calls, [])
        self.assertEqual(list(output.iterdir()), [])

    def test_human_preparation_failure_or_submit_uncertainty_never_advances_or_retries(self):
        # Given a rejected/malformed preparation or refused/uncertain actual Human Submit.
        prepared = {"datastore": []}
        cases = [([{"outcome": "rejected"}], None), ([b"not json"], None),
                 ([prepared], (0, "nonzero")), ([prepared], (0, "transport")),
                 ([prepared, dict(self.submit_response(), accepted=False, outcome="rejected")], None),
                 ([prepared, dict(self.submit_response(), outcome="uncertain")], None),
                 ([prepared, self.submit_response()], (1, "nonzero")),
                 ([prepared, self.submit_response()], (1, "disconnected"))]
        for responses, fault in cases:
            with self.subTest(responses=responses, fault=fault):
                self.select_human()
                output = self.output()
                # When one finite completion reaches that exact failure.
                status, _observed, _diagnostic, calls, _native = self.call(
                    output, responses, fault=fault, argv=self.human_args(output))
                # Then only the attempted actual calls remain and no Advance or implicit retry follows.
                self.assertEqual(status, 2)
                self.assertEqual(len(calls), len(responses))
                self.assertEqual(list((output / "advance").iterdir()), [])

    def test_human_changed_routing_stops_after_preparation_or_accepted_submit(self):
        # Given a route changed after passive preparation or after actual accepted storage-only Submit.
        for changed in (0, 1):
            with self.subTest(changed=changed):
                self.select_human()
                output = self.output()
                responses = [{"datastore": []}, self.submit_response()][:changed + 1]

                def during(index, _observed):
                    if index == changed:
                        self.save(self.route, {"family": "GoalPhase", "sessionId": "changed-session"})

                # When the native response returns to the selected finite action.
                status, observed, _diagnostic, calls, _native = self.call(
                    output, responses, during=during, argv=self.human_args(output))
                # Then actual prior observations survive with no later Advance or retry.
                self.assertEqual(status, 2)
                self.assertEqual(len(calls), changed + 1)
                self.assertEqual([json.loads(line) for line in observed.getvalue().splitlines()], responses)
                self.assertEqual(list((output / "advance").iterdir()), [])

    def test_all_fixed_split_pairs(self):
        # Given each of the three fixed Split operation/result pairs with wire aliases,
        for pair in SPLIT_PAIRS:
            with self.subTest(pair=pair):
                self.select("SplitPhase", pair, wire=True)
                # Shared completion action and expectations check the exact Split Submit followed by
                # one outer Split Advance, using the supplied result without resultReference.
                self.assert_success(self.output(), [self.submit_response(), self.advance_response()])

    def test_all_fixed_repository_pairs(self):
        # Given each fixed standalone Repository review, repair or conflict pair,
        for pair in REPOSITORY_PAIRS:
            with self.subTest(pair=pair):
                self.select("RepositorySync", pair)
                # Shared completion action and expectations check the exact Repository Submit
                # followed by one outer Repository Advance.
                self.assert_success(self.output(), [self.submit_response(), self.advance_response()])

    def test_installed_child_transport_completes_without_development_artifacts(self):
        # Given one explicitly selected executable in a spaced path and no checkout runtime artifacts,
        output = self.output()
        calls = self.root / "installed-calls.jsonl"
        responses = [self.submit_response(), self.advance_response("Wait")]
        program = ("#!" + sys.executable + "\n"
                   "import json, pathlib, sys, os\n"
                   "calls = pathlib.Path(" + repr(str(calls)) + ")\n"
                   "prior = calls.read_text().splitlines() if calls.exists() else []\n"
                   "responses = " + repr(responses) + "\n"
                   "assert len(prior) < 2\n"
                   "assert sys.argv[1:4] == " + repr(["--runtime-root", str(self.runtime_root), "command"]) + "\n"
                   "assert sys.argv[4] == " + repr(SERVICES["GoalPhase"]) + " + ['submitGoalPhaseWorkResult', 'advanceGoalPhase'][len(prior)]\n"
                   "with calls.open('a') as stream: stream.write(json.dumps({'argv': sys.argv[1:], 'cwd': os.getcwd()}) + '\\n')\n"
                   "sys.stderr.write('installed fixture diagnostic\\n')\n"
                   "print(json.dumps(responses[len(prior)], ensure_ascii=False))\n")
        self.executable.write_text(program)
        observed, diagnostic = io.StringIO(), io.StringIO()
        originalroute = self.route.read_bytes()
        # When the real finite caller launches the selected test executable twice with no native-call mock,
        with redirect_stdout(observed), redirect_stderr(diagnostic):
            status = CALLER.main(self.args(output))
        # Then exact Submit/Advance transport, flushed actual observations and raw results are retained.
        self.assertEqual(status, 0, diagnostic.getvalue())
        actual = [json.loads(line) for line in calls.read_text().splitlines()]
        self.assertEqual(len(actual), 2)
        self.assertTrue(all(call["cwd"] == str(self.runtime_root) for call in actual))
        self.assertEqual(actual[0]["argv"][:4], ["--runtime-root", str(self.runtime_root), "command", SERVICES["GoalPhase"] + "submitGoalPhaseWorkResult"])
        self.assertEqual(actual[1]["argv"], ["--runtime-root", str(self.runtime_root), "command", SERVICES["GoalPhase"] + "advanceGoalPhase", "--sessionId", self.outer_id, "--json"])
        business = [item for item in map(json.loads, observed.getvalue().splitlines()) if "recordType" not in item]
        self.assertEqual(business, responses)
        self.assertEqual(json.loads((output / "response.json").read_text()), responses[0])
        self.assertEqual(json.loads((output / "advance/response.json").read_text()), responses[1])
        self.assertEqual(self.route.read_bytes(), originalroute)

    def test_human_transport_uses_three_owned_subprocesses_and_unchanged_preparation_bytes(self):
        # Given a selected executable fixture with three controlled real subprocess responses, not native consent.
        self.select_human()
        output = self.output()
        calls = self.root / "human-installed-calls.jsonl"
        responses = [{"datastore": [{"fixture": "native preparation output"}]},
                     self.submit_response(), self.advance_response()]
        binding = "--textus.import.data.file=" + str(output / "preparation/response.json")
        config = (self.runtime_root / ".textus/config.json").read_bytes()
        program = ("#!" + sys.executable + "\n"
                   "import json, pathlib, sys, os\n"
                   "calls = pathlib.Path(" + repr(str(calls)) + ")\n"
                   "prior = calls.read_text().splitlines() if calls.exists() else []\n"
                   "responses = " + repr(responses) + "\n"
                   "index = len(prior)\n"
                   "assert index < 3\n"
                   "argv = sys.argv[1:]\n"
                   "assert argv[:2] == " + repr(["--runtime-root", str(self.runtime_root)]) + "\n"
                   "binding = " + repr(binding) + "\n"
                   "if index == 1:\n"
                   " assert argv[2] == binding and argv[3] == 'command'\n"
                   " assert pathlib.Path(binding.split('=', 1)[1]).read_bytes() == (json.dumps(responses[0], ensure_ascii=False)+'\\n').encode()\n"
                   "else:\n"
                   " assert argv[2] == 'command' and binding not in argv\n"
                   "operations = ['org.simplemodeling.textus.SmWorkflow.NativeWorkflowPreparationService.prepareEvidence',"
                   "'" + SERVICES["GoalPhase"] + HUMAN_PAIR[0] + "','" + SERVICES["GoalPhase"] + "advanceGoalPhase']\n"
                   "assert argv[argv.index('command')+1] == operations[index]\n"
                   "with calls.open('a') as stream: stream.write(json.dumps({'argv':argv,'cwd':os.getcwd()})+'\\n')\n"
                   "sys.stderr.write('human fixture diagnostic\\n')\n"
                   "print(json.dumps(responses[index], ensure_ascii=False))\n")
        self.executable.write_text(program)
        observed, diagnostic = io.StringIO(), io.StringIO()
        # When the real caller launches its selected preparation, Submit and Advance without a native-call mock.
        with redirect_stdout(observed), redirect_stderr(diagnostic):
            status = CALLER.main(self.human_args(output))
        # Then actual subprocess transport preserves the raw preparation binding and emits each actual observation once.
        self.assertEqual(status, 0, diagnostic.getvalue())
        actual = [json.loads(line) for line in calls.read_text().splitlines()]
        self.assertEqual(len(actual), 3)
        self.assertTrue(all(call["cwd"] == str(self.runtime_root) for call in actual))
        business = [item for item in map(json.loads, observed.getvalue().splitlines()) if "recordType" not in item]
        self.assertEqual(business, responses)
        self.assertEqual(actual[1]["argv"][2], binding)
        self.assertNotIn(binding, actual[2]["argv"])
        self.assertEqual((self.runtime_root / ".textus/config.json").read_bytes(), config)
        for index, directory in enumerate((output / "preparation", output, output / "advance")):
            self.assertEqual(json.loads((directory / "response.json").read_text()), responses[index])

    def test_changed_routing_after_accepted_submit_stops_before_advance(self):
        # Given accepted Submit and a route removed, corrupted or changed during its return,
        for replacement in (None, b"invalid", {"family": "SplitPhase", "sessionId": self.outer_id},
                            {"family": "GoalPhase", "sessionId": "other-session"}):
            with self.subTest(replacement=replacement):
                self.select()
                output = self.output()
                def during(index, _observations):
                    if index == 0:
                        if replacement is None:
                            self.route.unlink()
                        elif isinstance(replacement, bytes):
                            self.route.write_bytes(replacement)
                        else:
                            self.save(self.route, replacement)
                # When complete reopens the original task route after actual acceptance,
                status, observed, _diagnostic, calls, _native = self.call(output, [self.submit_response()], during=during)
                # Then accepted output remains visible and retained without any second call or route reconstruction.
                self.assertEqual(status, 2)
                self.assertEqual(len(calls), 1)
                self.assertEqual([json.loads(line) for line in observed.getvalue().splitlines()], [self.submit_response()])
                self.assertEqual(json.loads((output / "response.json").read_text()), self.submit_response())
                self.assertEqual(list((output / "advance").iterdir()), [])

    def test_unavailable_installed_executable_never_uses_a_source_fallback(self):
        # Given a missing or nonexecutable explicit CLI and a valid retained issued route,
        for available in (True, False):
            with self.subTest(available=available):
                if available:
                    self.executable.chmod(0o644)
                else:
                    self.executable.unlink()
                output = self.output()
                # When complete checks its explicit installed entry,
                status, _observed, _diagnostic, calls, _native = self.call(output, [])
                # Then no native call, fallback or output file is produced.
                self.assertEqual(status, 2)
                self.assertEqual(calls, [])
                self.assertEqual(list(output.iterdir()), [])

    def test_goal_child_submit_preserves_outer_advance_identity(self):
        # Given each Repository child pair forwarded by its distinct owning Goal,
        for pair in REPOSITORY_PAIRS:
            with self.subTest(pair=pair):
                self.select("GoalPhase", pair, child=True, wire=True)
                # Shared completion action and expectations check Submit with the child ID
                # followed by Advance with the retained outer Goal ID.
                self.assert_success(self.output(), [self.submit_response(), self.advance_response()])

    def test_exchange_reports_actual_child_route_work_and_repair_counts(self):
        # Given an issued Goal-owned Repository child and an actual-shaped next Step,
        self.select("GoalPhase", REPOSITORY_PAIRS[0], child=True)
        request = json.loads(self.request.read_text())
        request["work"] = {"kind": "ReviewRepository", "step": "step-4", "selectedPlan": {"privateInput": "omitted"}}
        self.save(self.request, request)
        response = dict(self.advance_response(), work={"kind": "ReviewStep", "stepIdentity": "step-4"},
                        repairProgress={"automaticCount": 0, "ordinaryCount": 1, "limit": 2})
        output = self.output()
        # When complete performs its exact Submit then outer Advance,
        status, _observed, diagnostic, calls, native = self.call(output, [self.submit_response(), response])
        # Then telemetry preserves actual identities and business fields without another call.
        self.assertEqual(status, 0, diagnostic)
        self.assertEqual(len(calls), 2)
        invocations = [call for call in native.telemetry if call.args[0] == "invocation"]
        self.assertEqual(invocations[0].args[1]["outerSessionId"], self.outer_id)
        self.assertEqual(invocations[0].args[1]["targetSessionId"], self.submit_id)
        self.assertEqual(invocations[0].args[1]["issuedObservation"]["work"], {"kind": "ReviewRepository", "step": "step-4"})
        self.assertNotIn("selectedPlan", invocations[0].args[1]["issuedObservation"]["work"])
        self.assertNotIn("submitOperation", invocations[0].args[1]["issuedObservation"])
        self.assertEqual(invocations[1].args[1]["targetSessionId"], self.outer_id)
        responses = [call for call in native.telemetry if call.args[0] == "response"]
        self.assertEqual(responses[-1].kwargs["observation"], response)
        self.assertEqual(responses[-1].args[1]["responseReference"], str(output / "advance/response.json"))

    def test_live_sbt_events_are_visible_before_process_exit_with_large_raw_streams(self):
        # Given an owned child that waits for observation of its real stderr event,
        output = self.output()
        signal = output / "event-observed"
        response = {"sessionId": self.outer_id, "message": "日本語" + "x" * (9 * 1024 * 1024),
                    "kind": "Request", "work": {"kind": "ReviewStep", "step": "step-2"}}
        raw = json.dumps(response, ensure_ascii=False).encode("utf-8")
        event = '[cncf-sbt-event] {"operation":"compile","workspaceId":"fixture","programIdentity":"owned-fixture","state":"started"}'
        program = ("import json,pathlib,sys,time\n"
                   "response = " + repr(dict(response, message="")) + "\n"
                   "response['message'] = '日本語' + 'x' * (9 * 1024 * 1024)\n"
                   "sys.stdout.buffer.write(json.dumps(response, ensure_ascii=False).encode('utf-8')); sys.stdout.flush()\n"
                   "sys.stderr.write('z' * (2 * 1024 * 1024) + '\\n' + " + repr(event + "\n") + "); sys.stderr.flush()\n"
                   "deadline = time.monotonic() + 10\n"
                   "while not pathlib.Path(" + repr(str(signal)) + ").exists() and time.monotonic() < deadline: time.sleep(.01)\n"
                   "sys.exit(0 if pathlib.Path(" + repr(str(signal)) + ").exists() else 9)\n")
        observed, diagnostic = io.StringIO(), io.StringIO()
        context = {"operation": "transport-fixture", "outerSessionId": self.outer_id,
                   "responseReference": str(output / "response.json"), "stderrReference": str(output / "stderr.log")}
        original = CALLER.progress

        def display(event_name, selected, **facts):
            original(event_name, selected, **facts)
            if event_name == "sbt-event":
                signal.write_text("observed while child waits")

        # When the real finite file-backed transport runs with telemetry displayed,
        with CALLER.selected_directory(output) as descriptor:
            with patch.object(CALLER, "progress", side_effect=display), redirect_stdout(observed), redirect_stderr(diagnostic):
                text, actual = CALLER.native_call([sys.executable, "-c", program], self.runtime_root, descriptor, context)
        # Then the event is flushed before termination, raw bytes are complete and no Workflow status is invented.
        records = [json.loads(line) for line in observed.getvalue().splitlines()]
        self.assertEqual([record["event"] for record in records], ["invocation", "sbt-event", "process-terminated"])
        self.assertTrue(all(record["recordType"] == "native-command-progress" for record in records))
        self.assertEqual(records[1]["rawEvent"], event)
        self.assertEqual(records[-1]["exitCode"], 0)
        self.assertEqual(diagnostic.getvalue(), event + "\n")
        self.assertEqual(actual, response)
        self.assertEqual(text.encode("utf-8"), raw)
        self.assertEqual((output / "response.json").read_bytes(), raw)
        self.assertEqual((output / "stderr.log").read_bytes(), ("z" * (2 * 1024 * 1024) + "\n" + event + "\n").encode())

    def test_sigterm_forwards_once_to_runtime_and_collects_actual_response(self):
        # Given a real runtime-shaped child whose handler writes its actual cancellation response,
        output = self.output()
        program = ("import json,os,signal,sys,time\n"
                   "def cancel(sig,frame):\n"
                   " print('actual runtime cancellation log',file=sys.stderr,flush=True)\n"
                   " print(json.dumps({'kind':'Request','message':'actual cancelled receipt','accepted':False}),flush=True)\n"
                   " print('[cncf-runtime-event] '+json.dumps({'state':'settled','pid':os.getpid()}),file=sys.stderr,flush=True)\n"
                   " sys.exit(0)\n"
                   "signal.signal(signal.SIGTERM,cancel)\n"
                   "print('[cncf-runtime-event] '+json.dumps({'state':'runtime-started','pid':os.getpid()}),file=sys.stderr,flush=True)\n"
                   "time.sleep(5)\n")
        originalprogress = CALLER.progress
        realkill = os.kill
        previous = signal.getsignal(signal.SIGTERM)
        forwarded = []
        observed = io.StringIO()

        def record_signal(pid, selected):
            forwarded.append((pid, selected))
            return realkill(pid, selected)

        def cancel(event_name, context, **facts):
            originalprogress(event_name, context, **facts)
            if event_name == "runtime-event" and '"runtime-started"' in facts["rawEvent"]:
                realkill(os.getpid(), signal.SIGTERM)
                realkill(os.getpid(), signal.SIGTERM)

        # When the scoped caller handler receives SIGTERM twice, only its exact runtime is requested once.
        with CALLER.selected_directory(output) as descriptor:
            with patch.object(CALLER.os, "kill", side_effect=record_signal), patch.object(CALLER, "progress", side_effect=cancel):
                with redirect_stdout(observed), redirect_stderr(io.StringIO()):
                    with self.assertRaisesRegex(CALLER.CallerError, "termination requested; actual result retained"):
                        CALLER.native_call([sys.executable, "-c", program], self.runtime_root, descriptor, {})
        # Then actual runtime settlement and response survive, with no kill escalation or caller cutoff.
        displayed = [json.loads(line) for line in observed.getvalue().splitlines()]
        records = [value for value in displayed if value.get("recordType") == "native-command-progress"]
        actual = [value for value in displayed if "recordType" not in value]
        runtime = [json.loads(record["rawEvent"].split("] ", 1)[1]) for record in records if record["event"] == "runtime-event"]
        self.assertEqual(forwarded, [(runtime[0]["pid"], signal.SIGTERM)])
        self.assertEqual([value["state"] for value in runtime], ["runtime-started", "settled"])
        self.assertEqual(actual, [{"kind": "Request", "message": "actual cancelled receipt", "accepted": False}])
        self.assertEqual(sum(record["event"] == "cancellation-requested" for record in records), 1)
        self.assertEqual(records[-2]["event"], "process-terminated")
        self.assertEqual(records[-2]["exitCode"], 0)
        self.assertEqual(records[-1]["event"], "response")
        self.assertIn("actual runtime cancellation log", (output / "stderr.log").read_text())
        self.assertEqual(signal.getsignal(signal.SIGTERM), previous)

    def test_sigterm_after_actual_accepted_submit_displays_it_once_and_stops_before_advance(self):
        # Given a real Submit child returning actual acceptance after its runtime receives termination,
        output = self.output()
        raw = self.raw(self.submit_response())
        program = ("import json,os,signal,sys,time\n"
                   "def cancel(sig,frame):\n"
                   " sys.stdout.buffer.write(" + repr(raw) + "); sys.stdout.flush()\n"
                   " sys.exit(0)\n"
                   "signal.signal(signal.SIGTERM,cancel)\n"
                   "print('[cncf-runtime-event] '+json.dumps({'state':'runtime-started','pid':os.getpid()}),file=sys.stderr,flush=True)\n"
                   "time.sleep(5)\n")
        realpopen = CALLER.subprocess.Popen
        realkill = os.kill
        originalprogress = CALLER.progress
        commands, children = [], []
        observed, diagnostic = io.StringIO(), io.StringIO()
        previous = signal.getsignal(signal.SIGTERM)

        def launch(command, **kwargs):
            commands.append(command)
            child = realpopen([sys.executable, "-c", program], **kwargs)
            children.append(child)
            self.addCleanup(child.wait, timeout=6)
            return child

        def terminate(event_name, context, **facts):
            originalprogress(event_name, context, **facts)
            if event_name == "runtime-event":
                realkill(os.getpid(), signal.SIGTERM)

        # When paired completion observes SIGTERM during that one real Submit,
        with patch.object(CALLER.subprocess, "Popen", side_effect=launch):
            with patch.object(CALLER, "progress", side_effect=terminate), redirect_stdout(observed), redirect_stderr(diagnostic):
                status = CALLER.main(self.args(output))
        # Then accepted Submit remains actual and visible once, but the stop request forbids the following Advance.
        values = [json.loads(line) for line in observed.getvalue().splitlines()]
        business = [value for value in values if "recordType" not in value]
        self.assertEqual(status, 2)
        self.assertEqual(business, [self.submit_response()])
        self.assertEqual(len(commands), 1)
        self.assertIn(SERVICES["GoalPhase"] + GOAL_PAIR[0], commands[0])
        self.assertEqual(children[0].returncode, 0)
        self.assertEqual((output / "response.json").read_bytes(), raw)
        self.assertEqual(list((output / "advance").iterdir()), [])
        self.assertIn("termination requested; actual result retained; no following Advance", diagnostic.getvalue())
        self.assertEqual(signal.getsignal(signal.SIGTERM), previous)

    def test_keyboard_interrupt_reaps_only_owned_child_without_acceptance_or_advance(self):
        # Given an owned live Submit child with raw bytes and an actual CNCF event,
        output = self.output()
        raw = self.raw(self.submit_response())
        event = '[cncf-sbt-event] {"operation":"compile","workspaceId":"fixture","programIdentity":"owned-fixture","state":"started"}'
        program = ("import sys,time; sys.stdout.buffer.write(" + repr(raw) + "); sys.stdout.flush(); "
                   "print(" + repr(event) + ", file=sys.stderr, flush=True); time.sleep(10)")
        realpopen = CALLER.subprocess.Popen
        originalprogress = CALLER.progress
        children = []
        commands = []
        originalroute = self.route.read_bytes()
        observed, diagnostic = io.StringIO(), io.StringIO()

        def launch(command, **kwargs):
            commands.append(command)
            child = realpopen([sys.executable, "-c", program], **kwargs)
            children.append(child)

            def cleanup():
                if child.poll() is None:
                    child.kill()
                child.wait()

            self.addCleanup(cleanup)
            return child

        def interrupt(event_name, context, **facts):
            originalprogress(event_name, context, **facts)
            if event_name == "sbt-event":
                self.assertIsNone(children[0].poll())
                raise KeyboardInterrupt

        # When the main-thread observer is interrupted before interpreting Submit,
        with patch.object(CALLER.subprocess, "Popen", side_effect=launch):
            with patch.object(CALLER, "progress", side_effect=interrupt), redirect_stdout(observed), redirect_stderr(diagnostic):
                status = CALLER.main(self.args(output))
        # Then the direct child is already reaped, raw bytes remain and no success or Advance occurs.
        self.assertEqual(status, 130)
        self.assertEqual(len(commands), 1)
        self.assertIn(SERVICES["GoalPhase"] + GOAL_PAIR[0], commands[0])
        self.assertIsNotNone(children[0].returncode)
        self.assertNotEqual(children[0].wait(timeout=0), 0)
        records = [json.loads(line) for line in observed.getvalue().splitlines()]
        self.assertEqual([record["event"] for record in records], ["invocation", "sbt-event", "cancellation-requested", "process-terminated"])
        self.assertTrue(records[-2]["forwarded"])
        self.assertTrue(all(record["recordType"] == "native-command-progress" for record in records))
        self.assertIn("interrupted; business outcome remains unknown", diagnostic.getvalue())
        self.assertEqual((output / "response.json").read_bytes(), raw)
        self.assertEqual((output / "stderr.log").read_text(), event + "\n")
        self.assertEqual(list((output / "advance").iterdir()), [])
        self.assertEqual(self.route.read_bytes(), originalroute)

    def test_event_display_refuses_malformed_oversized_and_foreign_payloads_but_retains_raw_bytes(self):
        # Given real-shaped metadata plus malformed, oversized and payload-bearing event lines,
        output = self.output()
        valid = {"operation": "compile", "workspaceId": "fixture", "programIdentity": "fixture", "state": "terminal", "exitCode": 1}
        line = "[cncf-sbt-event] " + json.dumps(valid)
        raw = ("[cncf-sbt-event] not-json\n"
               + "[cncf-sbt-event] " + json.dumps(dict(valid, argv=["private-source"])) + "\n"
               + "[cncf-sbt-event] " + json.dumps(dict(valid, programIdentity="x" * 20000)) + "\n"
               + "[cncf-sbt-event] " + json.dumps(dict(valid, state="accepted")) + "\n"
               + line + "\n").encode()

        program = "import sys; sys.stdout.buffer.write(b'{}'); sys.stderr.buffer.write(" + repr(raw) + ")"
        observed, diagnostic = io.StringIO(), io.StringIO()
        # When the observer consumes the actual raw file sink,
        with CALLER.selected_directory(output) as descriptor:
            with redirect_stdout(observed), redirect_stderr(diagnostic):
                CALLER.native_call([sys.executable, "-c", program], self.runtime_root, descriptor, {})
        # Then only valid bounded metadata is displayed, and every original byte is retained.
        events = [json.loads(item) for item in observed.getvalue().splitlines() if json.loads(item)["event"] == "sbt-event"]
        self.assertEqual([item["rawEvent"] for item in events], [line])
        self.assertEqual(diagnostic.getvalue(), line + "\n")
        self.assertEqual((output / "stderr.log").read_bytes(), raw)
        self.assertEqual((output / "response.json").read_bytes(), b"{}")

    def test_controlled_clock_over_one_hour_keeps_waiting_with_heartbeat_and_no_signal(self):
        # Given a real finite child awaiting a heartbeat acknowledgement and a controlled parent clock,
        output = self.output()
        acknowledgement = output / "heartbeat-observed"
        event = '[cncf-sbt-event] {"operation":"compile","workspaceId":"fixture","programIdentity":"fixture","state":"waiting"}'
        program = ("import pathlib,sys,time\n"
                   "print(" + repr(event) + ",file=sys.stderr,flush=True)\n"
                   "deadline=time.monotonic()+5\n"
                   "while not pathlib.Path(" + repr(str(acknowledgement)) + ").exists():\n"
                   " if time.monotonic()>=deadline: raise RuntimeError('heartbeat unavailable')\n"
                   " time.sleep(.01)\n"
                   "print('{}',flush=True)\n")
        clock = [1000.0]
        clocksource = Mock(monotonic=lambda: clock[0], sleep=time.sleep)
        originalprogress = CALLER.progress
        observed = io.StringIO()

        def observe(event_name, context, **facts):
            originalprogress(event_name, context, **facts)
            if event_name == "sbt-event":
                clock[0] = 5001.0
            if event_name == "running":
                acknowledgement.write_text("controlled clock; no measured hour claim")

        # When the observer clock passes 4001 seconds, actual subprocess waiting remains unpatched.
        with CALLER.selected_directory(output) as descriptor:
            with patch.object(CALLER, "time", clocksource), patch.object(CALLER, "progress", side_effect=observe):
                with patch.object(CALLER.os, "kill") as signals, redirect_stdout(observed), redirect_stderr(io.StringIO()):
                    _text, actual = CALLER.native_call([sys.executable, "-c", program], self.runtime_root, descriptor, {})
                    signals.assert_not_called()
        # Then the actual later response is collected without any timer, synthetic deadline or cancellation.
        records = [json.loads(line) for line in observed.getvalue().splitlines()]
        self.assertEqual(actual, {})
        self.assertEqual(next(record for record in records if record["event"] == "running")["elapsedSeconds"], 4001.0)
        self.assertNotIn("deadline-adjusted", [record["event"] for record in records])
        self.assertNotIn("cancellation-requested", [record["event"] for record in records])
        self.assertNotIn("timeoutSeconds", records[0])
        self.assertEqual(records[-1]["exitCode"], 0)
        self.assertEqual((output / "stderr.log").read_text(), event + "\n")

    def test_runtime_events_display_only_closed_bounded_metadata_and_keep_all_raw_bytes(self):
        # Given actual runtime-shaped metadata plus foreign, malformed and oversized private payloads,
        output = self.output()
        program = ("import json,os,sys\n"
                   "valid={'state':'runtime-started','pid':os.getpid(),'elapsedMillis':0}\n"
                   "for value in [dict(valid,argv=['private']),dict(valid,pid=True),dict(valid,state='accepted'),"
                   "dict(valid,message='x'*20000),valid]:\n"
                   " print('[cncf-runtime-event] '+json.dumps(value),file=sys.stderr,flush=True)\n"
                   "print('{}',flush=True)\n")
        observed, diagnostic = io.StringIO(), io.StringIO()
        # When one actual file-backed runtime response is collected,
        with CALLER.selected_directory(output) as descriptor:
            with redirect_stdout(observed), redirect_stderr(diagnostic):
                _text, actual = CALLER.native_call([sys.executable, "-c", program], self.runtime_root, descriptor, {})
        # Then only the strict metadata is forwarded while original private lines remain solely in the raw sink.
        records = [json.loads(line) for line in observed.getvalue().splitlines()]
        runtime = [record for record in records if record["event"] == "runtime-event"]
        self.assertEqual(len(runtime), 1)
        self.assertEqual(actual, {})
        self.assertEqual(diagnostic.getvalue(), runtime[0]["rawEvent"] + "\n")
        self.assertNotIn("private", observed.getvalue())
        raw = (output / "stderr.log").read_text()
        self.assertIn("private", raw)
        self.assertEqual(len(raw.splitlines()), 5)

    def test_observation_error_requests_runtime_termination_then_awaits_actual_exit(self):
        # Given a real live child and an observer that fails after its first actual event,
        output = self.output()
        event = '[cncf-runtime-event] {"state":"runtime-started","pid":1}'
        program = "import sys,time; print(" + repr(event) + ",file=sys.stderr,flush=True); time.sleep(5)"
        originalprogress = CALLER.progress
        observations = io.StringIO()
        realpopen = CALLER.subprocess.Popen

        def fail_observer(event_name, context, **facts):
            originalprogress(event_name, context, **facts)
            if event_name == "runtime-event":
                raise ValueError("test observer failure")

        # When observation raises, the error path requests only runtime SIGTERM and waits for its exit.
        with CALLER.selected_directory(output) as descriptor:
            with patch.object(CALLER.subprocess, "Popen", wraps=realpopen) as launched:
                with patch.object(CALLER, "progress", side_effect=fail_observer), redirect_stdout(observations), redirect_stderr(io.StringIO()):
                    with self.assertRaisesRegex(ValueError, "test observer failure"):
                        CALLER.native_call([sys.executable, "-c", program], self.runtime_root, descriptor, {})
                self.assertEqual(launched.call_count, 1)
        # Then original raw bytes and error survive, with an actual exit observation and no accepted response.
        records = [json.loads(line) for line in observations.getvalue().splitlines()]
        self.assertEqual(records[-1]["event"], "process-terminated")
        self.assertEqual(records[-1]["exitCode"], -signal.SIGTERM)
        self.assertEqual(sum(record["event"] == "cancellation-requested" for record in records), 1)
        self.assertEqual((output / "stderr.log").read_text(), event + "\n")

    def test_projection_bounds_text_and_omits_plan_source_and_environment_payloads(self):
        # Given supplied counts and a native DTO with extensive scope and source payloads,
        native = {"message": "x" * 3000, "repairProgress": {"automatic_count": 0, "ordinary_count": 1, "limit": 2},
                  "work": {"kind": "ReviewStep", "step": "step-2", "sourceRevision": "14",
                           "selectedPlan": {"source": "private"}, "environment": {"secret": "private"}},
                  "stdout": "private source log", "argv": ["private input"]}
        # When telemetry projects only the admitted display metadata,
        actual = CALLER.observation_projection(native)
        # Then raw business data is unchanged while the projection is bounded and explicit about shortened text.
        self.assertEqual(actual["repairProgress"], native["repairProgress"])
        self.assertEqual(actual["work"], {"kind": "ReviewStep", "step": "step-2", "sourceRevision": "14"})
        self.assertTrue(actual["message"]["truncated"])
        self.assertEqual(len(actual["message"]["preview"]), 1024)
        self.assertNotIn("stdout", actual)
        self.assertNotIn("argv", actual)
        self.assertEqual(len(native["message"]), 3000)

    def test_projection_keeps_separate_evidence_and_journal_outcomes_with_public_references(self):
        # Given real-shaped producer outcomes and raw material that must remain outside display.
        state = {"status": "stored", "evidence": {"identity": "evidence:source", "revision": "1"},
                 "observation": {"status": "stored", "observedAt": "2026-10-20T00:00:00Z"},
                 "journal": {"status": "unavailable", "entry": None}, "text": "private captured stdout"}
        # When a final Advance response is projected for the skill.
        actual = CALLER.observation_projection({"validation": {"outcome": "passed", "evidenceRecording": [state]}})
        displayed = actual["validation"]["evidenceRecording"][0]
        # Then Test, storage and Journal facts remain distinct and the usable ID survives nesting.
        self.assertEqual(actual["validation"]["outcome"], "passed")
        self.assertEqual(displayed["status"], "stored")
        self.assertEqual(displayed["evidence"], state["evidence"])
        self.assertEqual(displayed["journal"], {"status": "unavailable", "entry": None})
        self.assertNotIn("text", displayed)

    def test_projection_preserves_formal_admission_and_actual_journal_failure_without_review_pass(self):
        # Given an actual-shaped deterministic outcome with deliberately private policy/source material.
        assessment = {"branch": "admit", "reason": "Selected checks passed; Release observation retained",
                      "triage": "not-selected", "review": "not-executed", "validationReference": "native-check:1",
                      "candidate": {"head": "private"}, "policy": {"source": "private"}}
        journal = {"status": "indeterminate", "entry": None, "sourceReference": {"identity": "native-check:1", "revision": "1"}}
        native = {"validation": {"outcome": "passed", "assessment": assessment, "assessmentJournal": journal}}
        # When ordinary bounded exchange display consumes the supplied public result.
        actual = CALLER.observation_projection(native)["validation"]
        # Then omitted AI work stays distinct from CLEAN/PASS and recording failure creates no Entry.
        self.assertEqual(actual["assessment"], {k: v for k, v in assessment.items() if k not in ("candidate", "policy")})
        self.assertEqual(actual["assessmentJournal"], journal)
        self.assertEqual(actual["outcome"], "passed")
        self.assertEqual(native["validation"]["assessment"]["policy"], {"source": "private"})

    def test_all_advance_frontiers_stop_after_two_calls(self):
        # Given accepted Submit followed by each native frontier kind,
        for kind in ("Request", "Human", "Wait", "Terminal"):
            with self.subTest(kind=kind):
                # Shared completion action and expectations check display of the supplied frontier
                # kind and completion after exactly two calls.
                self.assert_success(self.output(), [self.submit_response(), self.advance_response(kind)])

    def test_rejected_or_unaccepted_submit_is_displayed_without_advance(self):
        # Given a valid Submit observation that is rejected or lacks the accepted outcome,
        for accepted, outcome in ((False, "rejected"), (True, "uncertain"), (False, "accepted")):
            with self.subTest(accepted=accepted, outcome=outcome):
                response = dict(self.submit_response(), accepted=accepted, outcome=outcome)
                output = self.output()
                # When complete receives that observation,
                status, observed, diagnostic, calls, _native = self.call(output, [response])
                # Then it displays that actual observation, fails and leaves the Advance directory empty.
                self.assertEqual(status, 2)
                self.assertEqual(json.loads(observed.getvalue()), response)
                self.assertIn("not acknowledged as accepted", diagnostic)
                self.assertEqual(len(calls), 1)
                self.assertEqual(list((output / "advance").iterdir()), [])

    def test_wrong_id_or_invalid_submit_contract_never_advances(self):
        # Given wrong IDs, contradictory response aliases or invalid Submit field types,
        invalid = [dict(self.submit_response(), sessionId="other"),
                   dict(self.submit_response(), session_id="other"),
                   dict(self.submit_response(), accepted="true"),
                   dict(self.submit_response(), accepted=1),
                   dict(self.submit_response(), message=" "),
                   dict(self.submit_response(), outcome=None)]
        for response in invalid:
            with self.subTest(response=response):
                output = self.output()
                # When complete validates the actual Submit response,
                status, observed, _diagnostic, calls, _native = self.call(output, [response])
                # Then it fails before displaying a valid observation or making Advance, retaining raw output.
                self.assertEqual(status, 2)
                self.assertEqual(observed.getvalue(), "")
                self.assertEqual(len(calls), 1)
                self.assertEqual((output / "response.json").read_bytes(), self.raw(response))
                self.assertEqual(list((output / "advance").iterdir()), [])

    def test_submit_transport_failure_disconnect_and_nonzero_stop(self):
        # Given uncertain Submit transport, disconnect or nonzero native exit with available output,
        for failure in ("transport", "disconnected", "nonzero"):
            with self.subTest(failure=failure):
                output = self.output()
                response = self.submit_response()
                # When complete encounters that failure,
                status, observed, _diagnostic, calls, _native = self.call(output, [response], fault=(0, failure))
                # Then it retains both raw streams, reports failure and performs no Advance or retry.
                self.assertEqual(status, 2)
                self.assertEqual(observed.getvalue(), "")
                self.assertEqual(len(calls), 1)
                self.assertEqual((output / "response.json").read_bytes(), self.raw(response))
                self.assertEqual((output / "stderr.log").read_bytes(), b"fixture stderr 0")
                self.assertEqual(list((output / "advance").iterdir()), [])

    def test_malformed_submit_response_stops(self):
        # Given malformed, nonobject, duplicate-key or nonfinite Submit JSON,
        for raw in (b"not json", b"[]", b'{"accepted":true,"accepted":false}', b'{"accepted":NaN}'):
            with self.subTest(raw=raw):
                output = self.output()
                # When complete decodes the response,
                status, observed, _diagnostic, calls, _native = self.call(output, [raw])
                # Then it preserves raw bytes and stops after Submit without an accepted display or Advance.
                self.assertEqual(status, 2)
                self.assertEqual(observed.getvalue(), "")
                self.assertEqual(len(calls), 1)
                self.assertEqual((output / "response.json").read_bytes(), raw)
                self.assertEqual(list((output / "advance").iterdir()), [])

    def test_accepted_submit_then_failed_advance_preserves_first_observation(self):
        # Given an accepted Submit followed by failed or uncertain Advance transport,
        for failure in ("transport", "disconnected", "nonzero"):
            with self.subTest(failure=failure):
                output = self.output()
                responses = [self.submit_response(), self.advance_response()]
                # When the second native call fails,
                status, observed, _diagnostic, calls, _native = self.call(output, responses, fault=(1, failure))
                # Then the already flushed Submit and all available outputs remain, with failure and no retry.
                self.assertEqual(status, 2)
                self.assertEqual(json.loads(observed.getvalue()), responses[0])
                self.assertEqual(observed.flushed, [observed.getvalue()])
                self.assertEqual(len(calls), 2)
                for index, directory in enumerate((output, output / "advance")):
                    self.assertEqual((directory / "response.json").read_bytes(), self.raw(responses[index]))
                    self.assertEqual((directory / "stderr.log").read_bytes(), ("fixture stderr " + str(index)).encode())

    def test_invalid_advance_response_preserves_submit_and_stops(self):
        # Given accepted Submit followed by malformed Advance, wrong outer ID, alias conflict or unknown kind,
        invalid = [b"not json", dict(self.advance_response(), sessionId="other"),
                   dict(self.advance_response(), session_id="other"),
                   dict(self.advance_response(), kind="Unknown"), dict(self.advance_response(), message="")]
        for response in invalid:
            with self.subTest(response=response):
                output = self.output()
                accepted = self.submit_response()
                # When complete validates the second observation,
                status, observed, _diagnostic, calls, _native = self.call(output, [accepted, response])
                # Then only the valid Submit is displayed, both calls remain captured and no third call occurs.
                self.assertEqual(status, 2)
                self.assertEqual(json.loads(observed.getvalue()), accepted)
                self.assertEqual(len(calls), 2)
                self.assertEqual((output / "advance/response.json").read_bytes(), self.raw(response))

    def test_invalid_issued_route_fails_before_any_native_call(self):
        # Given unsupported/crossed issued routes, invalid work or contradictory consumed request aliases,
        cases = [("GoalPhase", GOAL_PAIR, {"kind": "Human"}),
                 ("GoalPhase", GOAL_PAIR, {"sessionId": "other"}),
                 ("GoalPhase", GOAL_PAIR, {"submitSessionId": "other"}),
                 ("GoalPhase", GOAL_PAIR, {"work": None}),
                 ("GoalPhase", GOAL_PAIR, {"submitOperation": "arbitraryCommand"}),
                 ("GoalPhase", GOAL_PAIR, {"resultType": "Wrong"}),
                 ("GoalPhase", REPOSITORY_PAIRS[0], {}),
                 ("GoalPhase", SPLIT_PAIRS[0], {}),
                 ("SplitPhase", REPOSITORY_PAIRS[0], {}),
                 ("RepositorySync", GOAL_PAIR, {}),
                 ("RepositorySync", REPOSITORY_PAIRS[0], {"submitSessionId": "other"})]
        for alias in ("session_id", "submit_operation", "submit_session_id", "result_type"):
            cases.append(("GoalPhase", GOAL_PAIR, {alias: "contradictory"}))
        for family, pair, changes in cases:
            with self.subTest(family=family, pair=pair, changes=changes):
                self.select(family, pair)
                request = json.loads(self.request.read_text(encoding="utf-8"))
                request.update(changes)
                self.save(self.request, request)
                output = self.output()
                # When the runtime_root asks complete to consume the route,
                status, observed, _diagnostic, calls, native = self.call(output, [])
                # Then it refuses before output preparation or any subprocess and leaves routing untouched.
                self.assertEqual(status, 2)
                self.assertEqual(observed.getvalue(), "")
                self.assertEqual(calls, [])
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])
                self.assertEqual(json.loads(self.route.read_text()), {"family": family, "sessionId": self.outer_id})

    def test_complete_requires_exact_request_and_submission_options(self):
        # Given missing or inapplicable complete options,
        for change in ("request", "submission", "extra"):
            with self.subTest(change=change):
                output = self.output()
                argv = self.args(output)
                if change == "extra":
                    argv.extend(["--proposal", "unadmitted"])
                else:
                    index = argv.index("--" + change)
                    del argv[index:index + 2]
                # When the strict named-option parser admits the runtime_root action,
                status, observed, _diagnostic, calls, native = self.call(output, [], argv=argv)
                # Then it fails before any native call or output write.
                self.assertEqual(status, 2)
                self.assertEqual(observed.getvalue(), "")
                self.assertEqual(calls, [])
                native.assert_not_called()
                self.assertEqual(list(output.iterdir()), [])

    def test_existing_output_is_exclusive_and_never_replayed(self):
        # Given an occupied result directory or an occupied Advance child directory,
        for occupied in ("response.json", "stderr.log", "advance"):
            with self.subTest(occupied=occupied):
                output = self.output()
                path = output / occupied
                if occupied == "advance":
                    path.mkdir()
                    path = path / "response.json"
                path.write_bytes(b"existing evidence")
                # When complete is invoked against that output,
                status, observed, _diagnostic, calls, native = self.call(output, [])
                # Then it preserves the existing bytes and refuses without Submit, Advance or replay.
                self.assertEqual(status, 2)
                self.assertEqual(observed.getvalue(), "")
                self.assertEqual(calls, [])
                native.assert_not_called()
                self.assertEqual(path.read_bytes(), b"existing evidence")


if __name__ == "__main__":
    unittest.main()
