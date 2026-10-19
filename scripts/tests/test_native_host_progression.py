# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Behavioral specifications for PHASE-6.3 / HOST / HOST-A completion.

Temporary prerequisites and subprocess responses are fixtures, not installed
native execution, genuine provenance, authority or task/Phase acceptance.
Only subprocess.run is patched; real caller routing, files and output are used.
@since   Oct. 19, 2026
@version Oct. 19, 2026
@author  ASAMI, Tomoharu
"""

from contextlib import redirect_stderr, redirect_stdout
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SOURCE = Path(__file__).resolve().parents[1] / "native-goal-command.py"
SPEC = importlib.util.spec_from_file_location("native_host_progression", SOURCE)
CALLER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CALLER)

GOAL_PAIR = ("submitGoalPhaseWorkResult", "GoalPhaseWorkResult")
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
        self.primary = self.root / "primary"
        self.host = self.root / "host"
        self.task = self.host / "target/sm-workflow/tasks/owned-task"
        self.task.mkdir(parents=True)
        for path in (
            self.primary / "bin/launcher",
            self.primary / "target/cncf.d/runtime-classpath.txt",
            self.primary / "target/cncf.d/component-descriptor.json",
            self.primary / "target/cncf.d/car-runtime-manifest.json",
            self.host / ".textus/config.json",
            self.host / ".textus/assembly.yaml",
        ):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("fixture prerequisite", encoding="utf-8")
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
        return ["--primary", str(self.primary), "--host", str(self.host),
                "--task", str(self.task), "--action", "complete", "--output", str(output),
                "--request", str(self.request), "--submission", str(self.submission)]

    def submit_response(self):
        return {"sessionId": self.submit_id, "message": "Actual fixture acknowledgement",
                "accepted": True, "outcome": "accepted"}

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
            self.assertEqual(set(kwargs), {"cwd", "stdout", "stderr", "timeout"})
            self.assertEqual(kwargs["cwd"], str(self.host))
            self.assertEqual(kwargs["timeout"], 60 if index == 0 else 240)
            if during is not None:
                during(index, observations)
            kwargs["stdout"].write(self.raw(responses[index]))
            kwargs["stderr"].write(("fixture stderr " + str(index)).encode("utf-8"))
            if fault == (index, "timeout"):
                raise subprocess.TimeoutExpired(command, kwargs["timeout"])
            if fault == (index, "transport"):
                raise OSError("fixture transport failure")
            return subprocess.CompletedProcess(command, 7 if fault == (index, "nonzero") else 0)

        with patch.object(CALLER.subprocess, "run", side_effect=invoke) as native:
            with redirect_stdout(observations), redirect_stderr(diagnostic):
                status = CALLER.main(self.args(output) if argv is None else argv)
        return status, observations, diagnostic.getvalue(), calls, native

    def expected_command(self, service, operation, pairs):
        return [str(self.primary / "bin/launcher"), "--classpath-file",
                str(self.primary / "target/cncf.d/runtime-classpath.txt"), "--main-class",
                "org.goldenport.cncf.CncfMain", "--java-opt", "-Dstdout.encoding=UTF-8", "--",
                "--force-exit", "--component-dev-dir", str(self.primary),
                "--textus.assembly.descriptor=" + str(self.host / ".textus/assembly.yaml"),
                "command", SERVICES[service] + operation, *pairs, "--json"]

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

    def test_goal_child_submit_preserves_outer_advance_identity(self):
        # Given each Repository child pair forwarded by its distinct owning Goal,
        for pair in REPOSITORY_PAIRS:
            with self.subTest(pair=pair):
                self.select("GoalPhase", pair, child=True, wire=True)
                # Shared completion action and expectations check Submit with the child ID
                # followed by Advance with the retained outer Goal ID.
                self.assert_success(self.output(), [self.submit_response(), self.advance_response()])

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

    def test_submit_transport_failure_timeout_and_nonzero_stop(self):
        # Given uncertain Submit transport, timeout or nonzero native exit with available output,
        for failure in ("transport", "timeout", "nonzero"):
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
        for failure in ("transport", "timeout", "nonzero"):
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
                # When the host asks complete to consume the route,
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
                # When the strict named-option parser admits the host action,
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
