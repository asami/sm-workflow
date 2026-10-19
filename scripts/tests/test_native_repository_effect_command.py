# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""PHASE-6.3 / SYNC finite passive Repository effect caller specifications.

Temporary files and mocked subprocess streams are specification fixtures, never
actual consent, Human provenance, imported authority or installed execution.
Only the subprocess boundary is mocked; native decoding and admission keep
their existing owners.
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
SPEC = importlib.util.spec_from_file_location("native_repository_effect_caller", SOURCE)
CALLER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CALLER)


class NativeRepositoryEffectCommandSpecification(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.primary = self.root / "primary"
        self.host = self.root / "host"
        self.task = self.host / "target/sm-workflow/tasks/owned"
        self.task.mkdir(parents=True)
        self.prerequisites = (
            self.primary / "bin/launcher", self.primary / "target/cncf.d/runtime-classpath.txt",
            self.primary / "target/cncf.d/component-descriptor.json",
            self.primary / "target/cncf.d/car-runtime-manifest.json",
            self.host / ".textus/config.json", self.host / ".textus/assembly.yaml",
        )
        for path in self.prerequisites:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("Specification prerequisite", encoding="utf-8")
        self.files = {}
        for name in ("selection", "target", "reference"):
            path = self.root / (name + " 選択 %23.data")
            path.write_bytes(b"opaque, deliberately not caller JSON: \xff\x00" + name.encode())
            self.files[name] = path
        self.metadata = {"component-instance": "smworkflow.選択", "participant": "participant — 😀",
                         "issuer": "supplied issuer", "occurred-at": "2026-10-19T01:23:45Z",
                         "collection": "entity:smworkflow.cli.effect_authority"}
        self.route = self.task / "workflow-session.json"
        self.counter = 0

    def args(self, effect="CommitPending"):
        self.counter += 1
        self.output = self.task / ("call-" + str(self.counter))
        self.output.mkdir()
        values = {"primary": str(self.primary), "host": str(self.host), "task": str(self.task),
                  "action": "prepare-repository-effect", "output": str(self.output),
                  **self.metadata, **{name: str(path) for name, path in self.files.items()},
                  "effect": effect}
        return [part for name, value in values.items() for part in ("--" + name, value)]

    def snapshot(self):
        return {path: path.read_bytes() for path in (*self.prerequisites, *self.files.values())}

    def call(self, args, response, returncode=0, timeout=False):
        raw = response if isinstance(response, bytes) else (json.dumps(response, ensure_ascii=False) + "\n").encode("utf-8")
        stderr = b"actual subprocess diagnostic\n"

        def process(command, **kwargs):
            kwargs["stdout"].write(raw)
            kwargs["stderr"].write(stderr)
            if timeout:
                raise subprocess.TimeoutExpired(command, kwargs["timeout"])
            return subprocess.CompletedProcess(command, returncode)

        out, err = io.StringIO(), io.StringIO()
        with patch.object(CALLER.subprocess, "run", side_effect=process) as native:
            with redirect_stdout(out), redirect_stderr(err):
                status = CALLER.main(args)
        return status, out.getvalue(), err.getvalue(), native, raw, stderr

    def test_sync_effect_each_exact_effect_forwards_opaque_sources_without_routing(self):
        for effect in ("CommitPending", "Push", "FinalFetch"):
            for route_present in (False, True):
                with self.subTest(effect=effect, route_present=route_present):
                    # Given SYNC-E1: three opaque files, explicit metadata and absent or unreadable-as-JSON routing.
                    if route_present:
                        self.route.write_bytes(b"not a routing JSON object")
                    elif self.route.exists():
                        self.route.unlink()
                    before_route = self.route.read_bytes() if route_present else None
                    before = self.snapshot()
                    args = self.args(effect)
                    response = {"datastore": [{"native_owned": "passive — 😀"}]}
                    # When the selected finite action receives one native passive preparation response.
                    status, out, err, native, raw, stderr = self.call(args, response)
                    # Then only the fixed ordinary API receives every literal field and URI, with no routing or effects.
                    self.assertEqual((status, out, err), (0, raw.decode("utf-8"), ""))
                    native.assert_called_once()
                    positional, kwargs = native.call_args
                    command = positional[0]
                    expected_value = {
                        "componentInstance": self.metadata["component-instance"],
                        **{name: path.as_uri() for name, path in self.files.items()},
                        "effect": effect, "participant": self.metadata["participant"],
                        "issuer": self.metadata["issuer"], "occurredAt": self.metadata["occurred-at"],
                        "collection": self.metadata["collection"],
                    }
                    self.assertEqual(json.loads(command[-2]), expected_value)
                    self.assertEqual(command, [
                        str(self.primary / "bin/launcher"), "--classpath-file",
                        str(self.primary / "target/cncf.d/runtime-classpath.txt"), "--main-class",
                        "org.goldenport.cncf.CncfMain", "--java-opt", "-Dstdout.encoding=UTF-8", "--",
                        "--force-exit", "--component-dev-dir", str(self.primary),
                        "--textus.assembly.descriptor=" + str(self.host / ".textus/assembly.yaml"),
                        "command", "org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareRepositoryEffect",
                        "--input", command[-2], "--json",
                    ])
                    self.assertEqual(set(kwargs), {"cwd", "stdout", "stderr", "timeout"})
                    self.assertEqual((kwargs["cwd"], kwargs["timeout"]), (str(self.host), 60))
                    self.assertTrue(kwargs["stdout"].closed and kwargs["stderr"].closed)
                    self.assertEqual({path.name for path in self.output.iterdir()}, {"response.json", "stderr.log"})
                    self.assertEqual((self.output / "response.json").read_bytes(), raw)
                    self.assertEqual((self.output / "stderr.log").read_bytes(), stderr)
                    self.assertEqual(self.snapshot(), before)
                    self.assertEqual(self.route.read_bytes() if self.route.exists() else None, before_route)

    def test_sync_effect_exact_required_pairs_effects_and_collections_refuse_before_native(self):
        required = ("primary", "host", "task", "action", "output", "component-instance", "selection",
                    "target", "effect", "reference", "participant", "issuer", "occurred-at", "collection")
        cases = [(kind, name, None) for kind in ("missing", "duplicate", "blank", "unpaired") for name in required]
        cases += [("extra", name, "supplied") for name in
                  ("input", "proposal", "request", "submission", "origin", "selection-id", "allocation",
                   "family", "operation", "service", "timeout", "sessionId")]
        cases += [("replace", "effect", value) for value in
                  ("commitpending", "push", "finalfetch", "Fetch", "Commit", "Unknown", "Push,FinalFetch")]
        cases += [("replace", "collection", value) for value in
                  ("entity:smworkflow/cli/effects", "entity:smworkflow.cli", "entity:smworkflow.cli.host.effects",
                   "smworkflow.cli.effects", "entity:1workflow.cli.effects", "entity:smworkflow..effects")]
        for kind, name, value in cases:
            with self.subTest(kind=kind, name=name, value=value):
                # Given SYNC-E2: a missing, duplicate, blank, unpaired, inapplicable or invalid exact named pair.
                args = self.args()
                before = self.snapshot()
                if kind == "missing":
                    index = args.index("--" + name)
                    del args[index:index + 2]
                elif kind == "blank":
                    args[args.index("--" + name) + 1] = " "
                elif kind == "replace":
                    args[args.index("--" + name) + 1] = value
                elif kind == "unpaired":
                    args.append("--" + name)
                else:
                    args += ["--" + name, value or "supplied"]
                # When the finite parser consumes that supplied invocation.
                status, out, err, native, _raw, _stderr = self.call(args, {"datastore": []})
                # Then refusal precedes native invocation and output allocation, with no inferred route or defaults.
                self.assertEqual((status, out), (2, ""))
                self.assertTrue(err.startswith("native-goal-command: "))
                native.assert_not_called()
                self.assertEqual(list(self.output.iterdir()), [])
                self.assertFalse(self.route.exists())
                self.assertEqual(self.snapshot(), before)

    def test_sync_effect_each_source_requires_an_existing_absolute_normalized_regular_file(self):
        for name in ("selection", "target", "reference"):
            for value in ("relative.data", str(self.root) + "/../missing.data",
                          str(self.root / "missing.data"), str(self.root)):
                with self.subTest(name=name, value=value):
                    # Given SYNC-E3: one selected source is relative, unnormalized, missing or a directory.
                    args = self.args()
                    args[args.index("--" + name) + 1] = value
                    before = self.snapshot()
                    # When preparation checks the explicitly selected files without decoding their contents.
                    status, out, err, native, _raw, _stderr = self.call(args, {"datastore": []})
                    # Then no native API, output, selection reconstruction or route is created.
                    self.assertEqual((status, out), (2, ""))
                    self.assertTrue(err)
                    native.assert_not_called()
                    self.assertEqual(list(self.output.iterdir()), [])
                    self.assertFalse(self.route.exists())
                    self.assertEqual(self.snapshot(), before)

    def test_sync_effect_native_failure_and_non_datastore_response_retain_actual_streams(self):
        cases = [("nonzero", {"datastore": []}, 2, False),
                 ("timeout", b"partial actual stdout", 0, True),
                 ("malformed", b"not-json", 0, False), ("nonobject", b"[]", 0, False),
                 ("duplicate", b'{"datastore":[],"datastore":[]}', 0, False),
                 ("nonfinite", b'{"datastore":[],"value":NaN}', 0, False)]
        cases += [("non-datastore", value, 0, False) for value in
                  ({}, {"datastore": {}}, {"datastore": None}, {"datastore": "[]"}, {"sessionId": "unrelated"})]
        for effect in ("CommitPending", "Push", "FinalFetch"):
            for name, response, code, timeout in cases:
                with self.subTest(effect=effect, case=name, response=response):
                    # Given SYNC-E4: valid opaque sources but a failed, uncertain or invalid actual preparation response.
                    args = self.args(effect)
                    before = self.snapshot()
                    # When the sole selected native invocation returns or times out with its actual streams.
                    status, out, err, native, raw, stderr = self.call(args, response, code, timeout)
                    # Then available streams survive without retry, unrelated operation, Session promotion or routing.
                    self.assertEqual((status, out), (2, ""))
                    self.assertTrue(err.startswith("native-goal-command: "))
                    native.assert_called_once()
                    self.assertEqual(native.call_args.args[0][-4],
                                     "org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareRepositoryEffect")
                    self.assertEqual({path.name for path in self.output.iterdir()}, {"response.json", "stderr.log"})
                    self.assertEqual((self.output / "response.json").read_bytes(), raw)
                    self.assertEqual((self.output / "stderr.log").read_bytes(), stderr)
                    self.assertFalse(self.route.exists())
                    self.assertEqual(self.snapshot(), before)


if __name__ == "__main__":
    unittest.main()
