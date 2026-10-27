# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""AI-ACTIVITY-DISPLAY-01: presentation observes work without admitting it.

Given/When/Then examples exercise the real API and finite helper transport.
Fixture observations are not actual execution, authorization or acceptance.
"""

from contextlib import redirect_stdout
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


SOURCE = Path(__file__).resolve().parents[1] / "native-goal-command.py"
SPEC = importlib.util.spec_from_file_location("native_ai_activity", SOURCE)
CALLER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CALLER)


class NativeAIActivitySpecification(unittest.TestCase):
    def context(self, **changes):
        return dict({"actor": "subagent", "action": "start", "purpose": "Review issued scope",
                     "role": "sm_workflow_review", "sessionId": "actual-session", "stepId": "step-1",
                     "runtimeControl": "parent", "model": {"value": "selected-model", "provenance": "configured"},
                     "reasoningEffort": {"value": "high", "provenance": "requested"}}, **changes)

    def test_dispatch_retains_real_spawn_id_for_paired_result(self):
        # Given an ordinary real-shape spawn response, without a start claim.
        spawn_response = {"agent_id": "019abc-real-agent"}
        context = self.context()
        # When the caller passes only the actual returned identity, then its paired result.
        dispatch = CALLER.ai_activity_record("dispatch", context, {"agentId": spawn_response["agent_id"]})
        result = CALLER.ai_activity_record("result", dispatch["activityContext"], {
            "agentId": spawn_response["agent_id"], "summary": "No blockers observed"})
        # Then dispatch is not start/acceptance, while the result is attributed.
        self.assertEqual(dispatch["activityContext"]["agentId"], spawn_response["agent_id"])
        self.assertEqual(dispatch["started"], "unknown")
        self.assertEqual(result["pairing"], "matched")
        self.assertIn("🤖 ◀ subagent 結果受領", result["presentation"]["commentary"])
        for field in ("purpose=", "role=", "agentId=", "Session=", "Step=", "model=", "reasoningEffort="):
            self.assertIn(field, result["presentation"]["commentary"])
        self.assertEqual(context, self.context())

    def test_resume_empty_ack_retains_identity_without_start_or_acceptance(self):
        # Given an existing selected agent and a resume acknowledgement with no metadata.
        context = self.context(action="resume", agentId="existing-agent")
        # When the caller reports that acknowledgement.
        record = CALLER.ai_activity_record("dispatch", context, {})
        # Then reuse remains explicit and cannot establish start or acceptance.
        self.assertEqual(record["activityContext"]["agentId"], "existing-agent")
        self.assertEqual(record["started"], "unknown")
        self.assertEqual(record["accepted"], "unknown")
        self.assertIn("action=resume", record["presentation"]["commentary"])
        self.assertIn("呼出し", record["presentation"]["commentary"])

    def test_exact_underscore_identity_roundtrips_and_oversized_ids_never_collide(self):
        # Given an actual-shape identity containing Markdown characters and two long IDs.
        agent_id = "/root/sm_skill_compatibility_review"
        # When dispatch context is retained and reused with the original actual identity.
        dispatch = CALLER.ai_activity_record("dispatch", self.context(), {"agentId": agent_id})
        result = CALLER.ai_activity_record("result", dispatch["activityContext"], {"agentId": agent_id})
        oversized = CALLER.ai_activity_record("result", self.context(agentId="a" * 512 + "first"),
                                             {"agentId": "a" * 512 + "second"})
        control = CALLER.ai_activity_record("dispatch", self.context(), {"agentId": "agent\nforged"})
        # Then exact structured identity pairs, while unsafe identities cannot truncate into a match.
        self.assertEqual(dispatch["activityContext"]["agentId"], agent_id)
        self.assertEqual(result["pairing"], "matched")
        self.assertNotIn(agent_id, result["presentation"]["commentary"])
        self.assertEqual(oversized["pairing"], "unknown")
        self.assertEqual(oversized["activityContext"]["agentId"], "unknown")
        self.assertEqual(control["activityContext"]["agentId"], "unknown")

    def test_explicit_working_and_started_are_separate_observations(self):
        # Given an actual progress event, distinct from selection or an empty ack.
        context = self.context(agentId="existing-agent")
        # When explicit work and start are observed.
        record = CALLER.ai_activity_record("working", context, {"agentId": "existing-agent", "started": True})
        # Then the working marker and start value reflect just those observations.
        self.assertIn("🤖 ✨ AI作業中", record["presentation"]["commentary"])
        self.assertIs(record["started"], True)
        self.assertEqual(record["outcome"], "unknown")

    def test_observed_model_supersedes_display_without_rewriting_selection(self):
        # Given configured/requested metadata and actual execution metadata.
        context = self.context(agentId="agent")
        observation = {"agentId": "agent", "model": {"value": "actual-model", "provenance": "observed"},
                       "reasoningEffort": {"value": "medium", "provenance": "observed"}}
        # When the actual result is presented.
        record = CALLER.ai_activity_record("result", context, observation)
        # Then actual execution is displayed and original selection remains separate.
        self.assertEqual(record["model"], observation["model"])
        self.assertEqual(record["reasoningEffort"], observation["reasoningEffort"])
        self.assertEqual(record["activityContext"]["model"], context["model"])
        self.assertEqual(record["activityContext"]["reasoningEffort"], context["reasoningEffort"])
        self.assertIn(r"actual\-model (observed)", record["presentation"]["commentary"])

    def test_unobserved_inherited_model_and_invalid_observation_remain_unknown(self):
        # Given only inherited/invalid execution settings and a role name.
        context = self.context(model={"value": "parent-model", "provenance": "inherited"}, reasoningEffort=None)
        # When a result claims configured metadata in the observation slot.
        record = CALLER.ai_activity_record("result", context, {
            "model": {"value": "claimed-model", "provenance": "configured"}})
        # Then neither role nor parent identity is used to infer actual settings.
        self.assertEqual(record["model"], {"value": "unknown", "provenance": "unknown"})
        self.assertEqual(record["reasoningEffort"]["value"], "unknown")
        self.assertNotIn("parent-model", json.dumps(record))
        self.assertNotIn("claimed-model", json.dumps(record))

    def test_missing_and_conflicting_result_identity_are_unknown(self):
        # Given an original identity, and missing, different or contradictory actual IDs.
        context = self.context(agentId="original", chatTitle="Original chat")
        observations = [{}, {"agentId": "different"}, {"agentId": "original", "chatTitle": "Other chat"}]
        for observation in observations:
            with self.subTest(observation=observation):
                # When the result is presented.
                record = CALLER.ai_activity_record("result", context, observation)
                # Then unknown attribution is explicit and cannot choose a successful pair.
                self.assertEqual(record["pairing"], "unknown")
                self.assertIn("状態不明", record["presentation"]["commentary"])
                if observation:
                    self.assertTrue(record["identityConflict"])
                    self.assertEqual(record["activityContext"]["agentId"], "unknown")

    def test_result_receipt_never_infers_success_or_native_acceptance(self):
        # Given an attributable summary with no structured outcome or acceptance.
        context = self.context(agentId="agent")
        # When the result is received, and separately when explicit fields are supplied.
        record = CALLER.ai_activity_record("result", context, {"agentId": "agent", "summary": "done"})
        explicit = CALLER.ai_activity_record("result", context, {
            "agentId": "agent", "outcome": "rejected", "accepted": False})
        # Then missing is unknown and explicit rejection remains rejection.
        self.assertEqual(record["outcome"], "unknown")
        self.assertEqual(record["accepted"], "unknown")
        self.assertIn("outcome=unknown; accepted=unknown", record["presentation"]["commentary"])
        self.assertEqual(explicit["outcome"], "rejected")
        self.assertIs(explicit["accepted"], False)

    def test_parent_working_and_waiting_are_distinct(self):
        # Given actual parent work followed by an actual wait condition.
        context = self.context(actor="parent")
        # When each observed boundary is presented.
        working = CALLER.ai_activity_record("working", context)
        waiting = CALLER.ai_activity_record("waiting", context, {"reason": "Waiting for issued review"})
        # Then a wait is not described as active parent work.
        self.assertIn("🤖 🛠️ 親AI作業中", working["presentation"]["commentary"])
        self.assertIn("🤖 ⏳ 親AI待機中", waiting["presentation"]["commentary"])
        self.assertNotIn("親AI作業中", waiting["presentation"]["commentary"])
        self.assertIn("Waiting for issued review", waiting["presentation"]["commentary"])

    def test_chat_title_dispatch_work_wait_and_actual_result(self):
        # Given the actual destination title, not an opaque ID masquerading as a title.
        context = self.context(actor="chat", chatTitle="Architecture discussion", agentId="opaque-chat-id")
        expected = {"dispatch": "💬 📨 別チャットへ依頼中", "working": "💬 ✨ 別チャット作業中",
                    "waiting": "💬 ⏳ 別チャット待機中", "result": "💬 ◀ 別チャット結果受領",
                    "failed": "💬 🔴 別チャット依頼失敗", "unknown": "💬 🟡 別チャット状態不明"}
        for event, marker in expected.items():
            # When each actual observation is presented.
            record = CALLER.ai_activity_record(event, context, {"chatTitle": "Architecture discussion"})
            # Then the named chat has the observed boundary and never substitutes its ID as title.
            self.assertIn(marker, record["presentation"]["commentary"])
            self.assertIn("chat=Architecture discussion", record["presentation"]["commentary"])
            self.assertNotIn("opaque-chat-id", record["presentation"]["commentary"])

    def test_chat_title_conflict_is_unknown(self):
        # Given a different actual title on a dispatch observation.
        context = self.context(actor="chat", chatTitle="Original")
        # When contradictory metadata is supplied.
        record = CALLER.ai_activity_record("dispatch", context, {"chatTitle": "Different"})
        # Then neither title wins the pairing.
        self.assertEqual(record["pairing"], "unknown")
        self.assertEqual(record["activityContext"]["chatTitle"], "unknown")
        self.assertIn("別チャット状態不明", record["presentation"]["commentary"])

    def test_child_result_preserves_ongoing_runtime_control(self):
        # Given a child result inside an ongoing runtime-owned interval.
        context = self.context(agentId="agent", runtimeControl="sm-workflow")
        # When the attributable result arrives.
        record = CALLER.ai_activity_record("result", context, {"agentId": "agent"})
        # Then the gear interval stays open and no parent handback is asserted.
        self.assertIn("⚙️ 🔵 │ sm-workflow 実行中", record["presentation"]["commentary"])
        self.assertIn("制御：sm-workflow", record["presentation"]["commentary"])
        self.assertNotIn("制御：親チャット", record["presentation"]["commentary"])
        self.assertNotIn("制御返却", record["presentation"]["commentary"])

    def test_unknown_input_keys_never_copy_secret_payloads(self):
        # Given extra tool/argv/env/result fields containing secret fixture values.
        context = self.context(argv=["SECRET_ARGV"], env={"TOKEN": "SECRET_ENV"}, tool="SECRET_TOOL")
        # When only whitelisted observations are presented.
        record = CALLER.ai_activity_record("dispatch", context, {"agentId": "agent", "result": "SECRET_RESULT"})
        # Then no raw unknown payload is copied into record or commentary.
        encoded = json.dumps(record)
        self.assertNotIn("SECRET", encoded)
        self.assertNotIn("argv", record["activityContext"])
        self.assertNotIn("result", record["observation"])

    def test_selected_metadata_preserves_structured_spelling_and_escapes_commentary(self):
        # Given actual selected metadata with underscores and a colon-bearing model name.
        context = self.context(
            role="cncf_fix_worker_luna_xhigh", sessionId="session_id:one", stepId="step_id:two",
            reference="source:work_ref", model={"value": "gpt-oss:20b", "provenance": "configured"})
        # When the existing dispatch observation is presented.
        record = CALLER.ai_activity_record("dispatch", context, {"agentId": "/root/actual_agent"})
        # Then structured metadata stays reusable and commentary displays the same spelling safely.
        for name in ("role", "sessionId", "stepId", "reference", "model"):
            self.assertEqual(record["activityContext"][name], context[name])
        self.assertEqual(record["model"], context["model"])
        commentary = record["presentation"]["commentary"]
        for expected in (
            r"role=cncf\_fix\_worker\_luna\_xhigh", r"model=gpt\-oss\:20b (configured)",
            r"Session=session\_id\:one", r"Step=step\_id\:two", r"reference=source\:work\_ref",
            r"agentId=\/root\/actual\_agent",
        ):
            self.assertIn(expected, commentary)
        self.assertNotIn("＿", commentary)
        self.assertNotIn("：20b", commentary)

    def test_scalar_sanitation_bounds_structured_data_and_escapes_presentation(self):
        # Given supplied Markdown, line separators, bidi/control codes and long text.
        malicious = "\n# [link](https://example.invalid)<img src=x>\x00\u202e\u2028**bold**" + "x" * 900
        context = self.context(purpose=malicious, role=["invalid"], sessionId=None)
        # When it is rendered.
        record = CALLER.ai_activity_record("dispatch", context)
        # Then structured text is bounded/control-free and only commentary escapes its syntax.
        value = record["activityContext"]["purpose"]
        self.assertLessEqual(len(value), 512)
        for token in ("\n", "\x00", "\u202e", "\u2028"):
            self.assertNotIn(token, value)
        self.assertTrue(value.startswith("# [link](https://example.invalid)<img src=x>**bold**"))
        commentary = record["presentation"]["commentary"]
        self.assertIn(
            r"purpose=\# \[link\]\(https\:\/\/example\.invalid\)\<img src\=x\>\*\*bold\*\*",
            commentary,
        )
        self.assertNotIn("[link](https://example.invalid)", commentary)
        self.assertNotIn("<img src=x>", commentary)
        self.assertEqual(record["activityContext"]["role"], "unknown")
        self.assertEqual(record["activityContext"]["sessionId"], "unknown")
        self.assertEqual(len(record["presentation"]["commentary"].splitlines()), 2)
        json.dumps(record, allow_nan=False)

    def test_malformed_input_renderer_and_sink_failures_are_nonblocking(self):
        # Given malformed metadata and a renderer/sink failure.
        # When each presentation-only failure occurs.
        malformed = CALLER.ai_activity_record([], ["bad"], {"accepted": "true", "started": 1})
        with patch.object(CALLER, "ai_activity_commentary", side_effect=RuntimeError("secret")):
            renderer = CALLER.ai_activity_record("working")
        with patch.object(CALLER, "display_observation", side_effect=OSError("secret")) as sink:
            delivery = CALLER.observe_ai_activity("working", self.context())
        # Then failure is bounded, never retried and never claims visible delivery.
        self.assertEqual(malformed["event"], "unknown")
        self.assertEqual(malformed["started"], "unknown")
        self.assertEqual(malformed["accepted"], "unknown")
        self.assertEqual(renderer["event"], "unknown")
        self.assertNotIn("secret", json.dumps(renderer))
        self.assertEqual(delivery["delivery"], "failed")
        self.assertEqual(sink.call_count, 1)

    def test_finite_helper_has_no_business_task_or_runtime_mutations(self):
        # Given a local display file and business entry points that must not be called.
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory).resolve() / "observation.json"
            path.write_text(json.dumps({"event": "dispatch", "context": self.context(),
                                        "observation": {"agentId": "agent"}}), encoding="utf-8")
            output = io.StringIO()
            # When the finite entry runs without business/task arguments.
            with patch.object(CALLER, "execute") as execute, patch.object(CALLER, "parse_options") as parse:
                with redirect_stdout(output):
                    exit_code = CALLER.main(["ai-activity", "--observation", str(path)])
            # Then it emits one record and creates no task/runtime side effect.
            self.assertEqual(exit_code, 0)
            self.assertEqual(json.loads(output.getvalue())["kind"], "native-ai-activity")
            execute.assert_not_called()
            parse.assert_not_called()
            self.assertEqual(list(Path(directory).resolve().iterdir()), [path])

    def test_real_helper_subprocess_needs_no_cli_and_mutates_no_task(self):
        # Given only an absolute observation file; no installed CLI or runtime.
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory).resolve() / "observation.json"
            path.write_text(json.dumps({"event": "waiting", "context": {"actor": "parent"},
                                        "observation": {"reason": "Child result pending"}}), encoding="utf-8")
            # When the real helper entry is invoked by the current managed interpreter.
            result = subprocess.run([sys.executable, str(SOURCE), "ai-activity", "--observation", str(path)],
                                    cwd=directory, capture_output=True, text=True, check=False)
            # Then it completes locally and leaves only the input file.
            self.assertEqual(result.returncode, 0, result.stderr)
            record = json.loads(result.stdout)
            self.assertIn("親AI待機中", record["presentation"]["commentary"])
            self.assertEqual(record["delivery"], "attempted")
            self.assertEqual(list(Path(directory).resolve().iterdir()), [path])

    def test_finite_helper_refuses_missing_bad_symlink_and_oversized_input(self):
        # Given malformed paths, JSON and nonregular/symlink/oversized files.
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            bad = root / "bad.json"
            bad.write_text("{bad", encoding="utf-8")
            oversized = root / "large.json"
            oversized.write_bytes(b" " * 65537)
            link = root / "link.json"
            link.symlink_to(bad)
            arguments = [[], ["--observation", "relative.json"], ["--observation", str(root / "missing")],
                         ["--observation", str(bad)], ["--observation", str(oversized)],
                         ["--observation", str(link)], ["--observation", str(root)],
                         ["--observation", str(bad), "--extra", "value"]]
            for argv in arguments:
                with self.subTest(argv=argv):
                    output = io.StringIO()
                    # When an invalid finite display request is received.
                    with redirect_stdout(output):
                        exit_code = CALLER.main(["ai-activity", *argv])
                    # Then display failure stays a safe unknown, not a business failure.
                    self.assertEqual(exit_code, 0)
                    self.assertEqual(json.loads(output.getvalue())["event"], "unknown")

    def test_real_helper_broken_display_pipe_exits_zero(self):
        # Given a pipe with no reader before the display-only helper starts.
        reader, writer = os.pipe()
        os.close(reader)
        try:
            # When a safe unknown record cannot be delivered or flushed at shutdown.
            result = subprocess.run([sys.executable, str(SOURCE), "ai-activity"],
                                    stdout=writer, stderr=subprocess.PIPE, text=True, check=False)
        finally:
            os.close(writer)
        # Then sink failure cannot become a business/process failure or leak a traceback.
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stderr, "")


if __name__ == "__main__":
    unittest.main()
