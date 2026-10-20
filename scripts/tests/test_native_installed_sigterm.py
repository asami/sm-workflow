# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Read-only SBTR-06 installed SIGTERM evidence verification.

The parent invokes the literal installed CLI and signals its observed owned
runtime separately. This test never launches a Workflow, signals a process or
writes workflow storage. Missing actual response or settlement remains unknown.
"""

import argparse
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import unittest


PRIMARY = Path(__file__).resolve().parents[2]
FIXTURE = None


def pid_live(pid):
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    proc = Path("/proc") / str(pid) / "stat"
    try:
        if proc.is_file():
            return proc.read_text().rpartition(")")[2].lstrip()[:1] != "Z"
    except FileNotFoundError:
        return False
    observed = subprocess.run(["/bin/ps", "-o", "stat=", "-p", str(pid)], capture_output=True, text=True, timeout=1)
    return bool(observed.stdout.strip()) and not observed.stdout.strip().startswith("Z")


def database_documents(path):
    """Decode only actual encoded JSON text from a read-only SQLite connection."""
    documents = []
    with sqlite3.connect(path.as_uri() + "?mode=ro", uri=True) as database:
        database.execute("PRAGMA query_only = ON")
        names = [row[0] for row in database.execute("SELECT name FROM sqlite_master WHERE type='table'")]
        for name in names:
            quoted = '"' + name.replace('"', '""') + '"'
            for row in database.execute("SELECT * FROM " + quoted):
                for value in row:
                    if not isinstance(value, str):
                        continue
                    if value.startswith("cncf.workflow-json.v1:"):
                        documents.append(json.loads(value[len("cncf.workflow-json.v1:"):]))
    return documents


def mechanical(documents, session):
    return {value["reference"]["identity"]: value for value in documents
            if value.get("schemaVersion") == "cncf.workflow-mechanical-transition.v1"
            and value["reference"]["identity"].startswith(session + ":")}


def payload(value):
    return value["payload"]["value"]


def field(value, declared, wire):
    if declared in value and wire in value and value[declared] != value[wire]:
        raise AssertionError("Contradictory actual field aliases: " + declared)
    return value[declared] if declared in value else value.get(wire)


class InstalledNativeSigtermSpecification(unittest.TestCase):
    def test_actual_sigterm_retains_cancelled_receipt_history_and_counts_with_no_live_managed_process(self):
        # Given exact fresh fixture metadata and the parent's separately collected actual installed result,
        if FIXTURE is None:
            self.skipTest("requires the exact prepared installed-sigterm fixture metadata after the parent proof")
        locator = Path(FIXTURE)
        self.assertTrue(locator.is_absolute() and locator.resolve() == locator)
        metadata = json.loads(locator.read_text())
        self.assertEqual(metadata["schema"], "sm-workflow.installed-sigterm-fixture.v1")
        root = Path(metadata["root"])
        self.assertEqual(root.resolve(), root)
        self.assertEqual(root.parent, PRIMARY / "target/sm-workflow/work/validation-recovery")
        self.assertTrue(root.name.startswith("platform-installed-sigterm-"))
        self.assertEqual(locator, root / "caller/installed-sigterm-fixture.json")
        self.assertIs(metadata["pointerAuthority"], False)
        baseline = root / "caller/installed-sigterm-before.sqlite"
        self.assertEqual(metadata["baselineDatabase"], str(baseline))
        responsepath = root / "caller/installed-sigterm-output/response.json"
        stderrpath = root / "caller/installed-sigterm-output/stderr.log"
        self.assertEqual(metadata["responseReference"], str(responsepath))
        self.assertEqual(metadata["stderrReference"], str(stderrpath))
        self.assertTrue(responsepath.is_file() and stderrpath.is_file(), "actual raw output is required; absence remains unknown")
        # When only actual response, runtime metadata, recorded PIDs and read-only SQLite bytes are observed,
        response = json.loads(responsepath.read_text())
        rawstderr = stderrpath.read_text()
        events = [json.loads(line[len("[cncf-runtime-event] "):]) for line in rawstderr.splitlines()
                  if line.startswith("[cncf-runtime-event] ")]
        self.assertTrue(events, "runtime observations are required; a requested signal alone is no cancellation proof")
        for event in events:
            self.assertTrue({"state", "pid"} <= event.keys() <= {"state", "pid", "elapsedMillis"})
            self.assertIs(type(event["pid"]), int)
            self.assertGreater(event["pid"], 0)
        started = [value for value in events if value["state"] == "runtime-started"]
        self.assertEqual(len(started), 1)
        runtimepid = started[0]["pid"]
        self.assertTrue(all(value["pid"] == runtimepid for value in events))
        self.assertEqual(sum(value["state"] == "cancellation-requested" for value in events), 1)
        self.assertEqual(sum(value["state"] == "settled" for value in events), 1)
        self.assertNotIn("uncertain", [value["state"] for value in events])
        sbtpids = [int(value) for value in (root / "compile-generations.txt").read_text().splitlines()]
        self.assertEqual(len(sbtpids), 1)
        descendant = int((root / "managed-descendant.pid").read_text().strip())
        self.assertEqual(len({runtimepid, sbtpids[0], descendant}), 3)
        for pid in (runtimepid, sbtpids[0], descendant):
            self.assertFalse(pid_live(pid), "exact recorded process is still live: " + str(pid))
        before = database_documents(baseline)
        after = database_documents(root / "operation.sqlite")
        session = metadata["sessionId"]
        oldinstance = [value for value in before if value.get("schemaVersion") == "cncf.workflow-instance-record.v1" and value["identity"] == session]
        newinstance = [value for value in after if value.get("schemaVersion") == "cncf.workflow-instance-record.v1" and value["identity"] == session]
        self.assertEqual(len(oldinstance), 1)
        self.assertEqual(len(newinstance), 1)
        oldmechanical, newmechanical = mechanical(before, session), mechanical(after, session)
        self.assertTrue(oldmechanical)
        for identity, value in oldmechanical.items():
            self.assertEqual(newmechanical.get(identity), value, "original history/Plan/effect receipt changed")
        starts = [payload(value)["payload"] for value in oldmechanical.values()
                  if payload(value).get("schemaVersion") == "sm-workflow.goal-phase-native-start.v2"]
        self.assertEqual(len(starts), 1)
        self.assertEqual(starts[0]["plan"]["identity"], metadata["planIdentity"])
        self.assertEqual(starts[0]["plan"]["revision"], metadata["planRevision"])
        self.assertEqual(starts[0]["base"], metadata["base"])
        added = [value for identity, value in newmechanical.items() if identity not in oldmechanical]
        validations = [payload(value)["payload"] for value in added
                       if payload(value).get("schemaVersion") == "sm-workflow.goal-phase-native-validation.v2"]
        observed = {json.dumps(receipt, sort_keys=True): receipt for value in validations for receipt in value["receipts"]}
        # Then actual durable cancellation is incomplete at Decision, preserving baseline and zero counts with no next check.
        self.assertEqual(field(response, "sessionId", "session_id"), session)
        self.assertEqual(response["kind"], "Wait")
        progress = field(response, "repairProgress", "repair_progress")
        self.assertEqual(field(progress, "automaticCount", "automatic_count"), 0)
        self.assertEqual(field(progress, "ordinaryCount", "ordinary_count"), 0)
        self.assertEqual(progress["limit"], 2)
        self.assertEqual(newinstance[0]["history"][:len(oldinstance[0]["history"])], oldinstance[0]["history"])
        self.assertEqual(newinstance[0]["current"]["progression"], "phase.Decision")
        self.assertEqual(len(observed), 1)
        receipt = next(iter(observed.values()))
        self.assertEqual(receipt["identity"], "compile")
        self.assertEqual(receipt["termination"], "cancelled")
        self.assertIsNone(receipt.get("exitCode"))
        self.assertEqual(receipt["sbt"]["outcome"], "incomplete")
        self.assertIn("COMPILE_BEFORE_CANCEL_STDOUT", receipt["stdout"])
        self.assertIn("COMPILE_BEFORE_CANCEL_STDERR", receipt["stderr"])
        self.assertTrue(any(value["outcome"] == "failed" for value in validations))
        self.assertTrue(all(value["sourceRevision"] == metadata["sourceRevision"] for value in validations))
        self.assertFalse(any("check-intent-1" in value or "check-result-1" in value or "retry-2" in value for value in newmechanical))
        self.assertFalse(any(payload(value).get("schemaVersion") in {
            "sm-workflow.goal-phase-native-review-completion.v1", "sm-workflow.goal-phase-native-step-closure.v3"}
            for value in added))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Read-only verification after the parent's direct installed SIGTERM proof")
    parser.add_argument("--fixture", type=Path)
    FIXTURE = parser.parse_args().fixture
    unittest.main(argv=[sys.argv[0]])
