# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Runtime caller format migration specifications; all data is test-owned.

Spec: docs/spec/runtime-caller-format-migration.md; P116-MIGRATION-01.
@since   Oct. 23, 2026
@version Oct. 23, 2026
@author  ASAMI, Tomoharu
"""

from contextlib import closing
import importlib.util
import json
from pathlib import Path
import sqlite3
import stat
import tempfile
import unittest
from unittest.mock import patch


SOURCE = Path(__file__).resolve().parents[1] / "migrate-runtime-caller-format.py"
SPEC = importlib.util.spec_from_file_location("runtime_caller_format_migration", SOURCE)
MIGRATION = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MIGRATION)


class RuntimeCallerFormatMigrationSpecification(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name).resolve()
        self.root = self.base / "cncf"
        (self.root / ".textus").mkdir(parents=True)
        self.config_path = self.root / ".textus/config.json"
        self.db = self.root / "workflow.sqlite"
        self.journal = self.root / "journal.sqlite"
        self.import_path = self.root / "human-response.json"
        self.output = self.base / "migration"
        self.definition = {"schemaVersion": "sm-workflow.three-workflow-host.v1",
                           "target": {"root": str(self.root), "localBranch": "main"},
                           "goal": {"input": {"phaseIdentity": "116"}, "repairLimit": 3},
                           "repository": {"syncMode": "FULL_STATE"}}
        self.config = {
            "sm-workflow.host.definition": json.dumps(self.definition),
            "sm-workflow.host.evidence.issuer": "original issuer",
            "sm-workflow.host.evidence.record-collection": "entity:smworkflow.local.host_evidence",
            "sm-workflow.host.evidence.store-name": "workflow",
            "textus.datastore.kind": "sqlite", "textus.datastore.path": str(self.db),
            "textus.journal.kind": "sqlite", "textus.journal.path": str(self.journal),
            "textus.import.data.file": str(self.import_path),
            "user.literal": "sm-workflow.host.definition / sm-workflow.three-workflow-host.v1",
            "cncf.sbt.test.stdout.maximum-bytes": 67108864,
            "workflow.mechanical-receipt.max-bytes": 67108864,
        }
        for family, names in (
            ("goal-phase", ("association", "authorization", "decision", "plan", "selection")),
            ("repository-sync", ("selection",)), ("split-phase", ("selection",)),
        ):
            self.config[f"sm-workflow.{family}.host-records.store-name"] = "workflow"
            for name in names:
                self.config[f"sm-workflow.{family}.host-records.{name}-collection"] = "collection:" + name
        self.rows = []
        with closing(sqlite3.connect(self.db)) as connection, connection:
            connection.execute('CREATE TABLE "host_evidence" ("id" TEXT PRIMARY KEY, "nativeHostEvidencePayload" TEXT)')
            connection.execute('CREATE TABLE instances (id TEXT PRIMARY KEY, session BLOB, repairCount INTEGER)')
            connection.execute('INSERT INTO instances VALUES (?, ?, ?)', ("existing-goal", b"\x00\xfffailed-record", 3))
            connection.execute('CREATE TABLE continuations (id TEXT, revision INTEGER, state TEXT)')
            connection.execute('INSERT INTO continuations VALUES ("same-session", 526, "unknown")')
            connection.execute('CREATE TABLE unrelated (id INTEGER PRIMARY KEY, data TEXT)')
            connection.execute('INSERT INTO unrelated VALUES (1, "retained")')
            connection.execute('CREATE INDEX unrelated_data ON unrelated(data)')
            for ordinal in range(27):
                value = {"schemaVersion": "sm-workflow.native-host-evidence.v7",
                         "purpose": "goal-phase-validation-recovery", "lookupIdentity": str(ordinal),
                         "reference": "original-" + str(ordinal), "revision": "2", "issuer": "original issuer",
                         "participant": "asami", "occurredAt": "2026-10-22T09:19:22Z",
                         "binding": {"target": {"sessionId": "same-session", "frontierRevision": "526"},
                                     "answer": {"choice": "AuthorizeNewAttempt"},
                                     "schemaVersion": "sm-workflow.native-host-evidence.v7",
                                     "literal": "sm-workflow.native-host-evidence.v7"}}
                payload = "sm-workflow.native-host-evidence.v7:" + json.dumps(value, ensure_ascii=False)
                row = ("human:" + str(ordinal), payload)
                self.rows.append(row)
                connection.execute('INSERT INTO host_evidence VALUES (?, ?)', row)
        with closing(sqlite3.connect(self.journal)) as connection, connection:
            connection.execute('CREATE TABLE events (id INTEGER PRIMARY KEY, event BLOB)')
            connection.execute('INSERT INTO events VALUES (1, ?)', (b"original journal\x00",))
        self.import_path.write_text(json.dumps({"datastore": [{
            "collection": "entity:smworkflow.local.host_evidence",
            "records": [{"id": self.rows[0][0], "nativeHostEvidencePayload": self.rows[0][1]}],
        }]}), encoding="utf-8")
        (self.root / ".textus/assembly.yaml").write_text("subsystem: cncf-development\n", encoding="utf-8")
        self.save_config()

    def save_config(self):
        self.config_path.write_text(json.dumps(self.config, indent=2) + "\n", encoding="utf-8")

    def plan(self):
        return MIGRATION.prepare_plan(str(self.root), str(self.output), 27)

    def state(self, path):
        with MIGRATION.database(path) as connection:
            return MIGRATION.snapshot(connection)

    def source_bytes(self):
        return {path: path.read_bytes() for path in (
            self.config_path, self.db, self.journal, self.import_path, self.root / ".textus/assembly.yaml")}

    def test_E1_plan_should_isolate_27_rows_and_preserve_original_bytes(self):
        # Given Spec docs/spec/runtime-caller-format-migration.md R1,R2,R3; E1: actual-style old layout.
        before = self.source_bytes()
        before_state = self.state(self.db)
        # When the owner requests a finite isolated plan.
        plan = self.plan()
        # Then only copied evidence receives the schema spelling projection.
        self.assertEqual(self.source_bytes(), before)
        self.assertEqual(self.state(self.db), before_state)
        self.assertEqual(plan["evidenceCount"], 27)
        fixture = json.loads((self.output / "fixture/.textus/config.json").read_bytes())
        self.assertEqual(fixture["textus.datastore.path"], str(self.output / "fixture/workflow.sqlite"))
        self.assertEqual(fixture["textus.journal.path"], str(self.output / "fixture/journal-0.sqlite"))
        self.assertEqual(self.state(self.journal), self.state(self.output / "fixture/journal-0.sqlite"))
        definition = json.loads(fixture["sm-workflow.runtime.definition"])
        self.assertEqual(definition, self.definition | {"schemaVersion": "sm-workflow.three-workflow-runtime.v1"})
        with closing(sqlite3.connect(self.output / "fixture/workflow.sqlite")) as connection, connection:
            rows = connection.execute('SELECT id,nativeHostEvidencePayload,nativeExternalEvidencePayload FROM host_evidence ORDER BY id').fetchall()
        self.assertEqual([(row[0], row[1]) for row in rows], sorted(self.rows))
        for _, old, new in rows:
            old_value = json.loads(old.split(":", 1)[1])
            new_value = json.loads(new.split(":", 1)[1])
            self.assertEqual(new_value, old_value | {"schemaVersion": "sm-workflow.native-external-evidence.v7"})
            self.assertEqual(new_value["binding"]["literal"], "sm-workflow.native-host-evidence.v7")
        for path in self.output.rglob("*"):
            self.assertEqual(stat.S_IMODE(path.stat().st_mode) & 0o077, 0)

    def test_E2_plan_should_preserve_authority_limits_and_retire_only_redundant_import(self):
        # Given Spec docs/spec/runtime-caller-format-migration.md R2,R4; E2: a retained normal StartupImport record.
        original = self.import_path.read_bytes()
        # When a plan proves the original record/value is already stored.
        plan = self.plan()
        live = json.loads((self.output / "proposed-config.json").read_bytes())
        # Then the pointer alone is retired; literal values, paths and authority remain unchanged.
        self.assertNotIn("textus.import.data.file", live)
        self.assertEqual(self.import_path.read_bytes(), original)
        self.assertEqual((self.output / "before-import.json").read_bytes(), original)
        self.assertEqual(plan["retiredImportRecords"], 1)
        for key, value in self.config.items():
            if key not in MIGRATION.KEYS and key != "textus.import.data.file":
                self.assertEqual(live[key], value)
        self.assertEqual(live["sm-workflow.external-evidence.issuer"], "original issuer")

    def test_E3_import_should_refuse_unprovisioned_unknown_and_duplicate_items(self):
        # Given Spec docs/spec/runtime-caller-format-migration.md R4; E3: unresolved import variants.
        examples = [
            {"datastore": [{"collection": "entity:smworkflow.local.host_evidence", "records": [
                {"id": "missing", "nativeHostEvidencePayload": self.rows[0][1]}]}]},
            {"datastore": [{"collection": "entity:unknown", "records": [{"id": "x"}]}]},
            {"entitystore": []},
            {"datastore": [{"collection": "entity:smworkflow.local.host_evidence", "records": [
                {"id": self.rows[0][0], "nativeHostEvidencePayload": "different retained value"}]}]},
            {"datastore": [{"collection": "entity:smworkflow.local.host_evidence", "records": [
                {"id": self.rows[0][0], "nativeHostEvidencePayload": self.rows[0][1]},
                {"id": self.rows[0][0], "nativeHostEvidencePayload": self.rows[0][1]}]}]},
        ]
        for value in examples:
            with self.subTest(value=value):
                self.import_path.write_text(json.dumps(value), encoding="utf-8")
                before = self.source_bytes()
                # When planning encounters any unsupported or unretained item.
                with self.assertRaises(MIGRATION.MigrationError):
                    self.plan()
                # Then it leaves original files untouched and creates no plan output.
                self.assertEqual(self.source_bytes(), before)
                self.assertFalse(self.output.exists())

    def test_E4_plan_should_refuse_malformed_or_unknown_evidence_schemas(self):
        # Given Spec docs/spec/runtime-caller-format-migration.md R2; E4: unsupported evidence bytes.
        examples = ["unknown:{}", "sm-workflow.native-host-evidence.v7:{bad-json}",
                    'sm-workflow.native-host-evidence.v7:{"schemaVersion":"unknown"}',
                    'sm-workflow.native-host-evidence.v7:{"schemaVersion":"x","schemaVersion":"x"}']
        for raw in examples:
            with self.subTest(raw=raw):
                with closing(sqlite3.connect(self.db)) as connection, connection:
                    connection.execute('UPDATE host_evidence SET nativeHostEvidencePayload=? WHERE id=?', (raw, self.rows[0][0]))
                # When planning attempts the finite schema transition.
                with self.assertRaises((MIGRATION.MigrationError, json.JSONDecodeError)):
                    self.plan()
                # Then unsupported records remain unchanged.
                self.assertFalse(self.output.exists())

    def test_E5_plan_should_refuse_key_collisions_missing_keys_and_duplicate_json(self):
        # Given Spec docs/spec/runtime-caller-format-migration.md R1,R2; E5: non-finite config spellings.
        original = self.config.copy()
        examples = [original | {"sm-workflow.runtime.definition": "{}"},
                    {key: value for key, value in original.items() if key != "sm-workflow.host.evidence.issuer"},
                    original | {"sm-workflow.host.unknown": "unhandled"}]
        for value in examples:
            self.config = value
            self.save_config()
            # When planning sees incomplete, mixed or unknown format.
            with self.assertRaises(MIGRATION.MigrationError):
                self.plan()
            # Then no migration plan is emitted.
            self.assertFalse(self.output.exists())
        self.config_path.write_text('{"same":1,"same":2}', encoding="utf-8")
        with self.assertRaisesRegex(MIGRATION.MigrationError, "duplicate JSON"):
            self.plan()

    def test_E6_plan_should_refuse_existing_projection_and_duplicate_ids(self):
        # Given Spec docs/spec/runtime-caller-format-migration.md R2; E6: a mixed projection layout.
        with closing(sqlite3.connect(self.db)) as connection, connection:
            connection.execute('ALTER TABLE host_evidence ADD COLUMN nativeExternalEvidencePayload TEXT')
        # When a new plan is requested.
        with self.assertRaisesRegex(MIGRATION.MigrationError, "mixed"):
            self.plan()
        # Then no existing projection is overwritten.
        self.assertFalse(self.output.exists())
        with closing(sqlite3.connect(self.db)) as connection, connection:
            connection.execute('DROP TABLE host_evidence')
            connection.execute('CREATE TABLE host_evidence (id TEXT, nativeHostEvidencePayload TEXT)')
            connection.executemany('INSERT INTO host_evidence VALUES (?,?)', [self.rows[0], self.rows[0]])
        with self.assertRaisesRegex(MIGRATION.MigrationError, "duplicate"):
            self.plan()

    def test_E7_apply_should_be_exact_once_and_fully_idempotent(self):
        # Given Spec docs/spec/runtime-caller-format-migration.md R3,R5; E7: a complete retained plan.
        self.plan()
        before_journal = self.journal.read_bytes()
        # When the owner explicitly applies twice.
        first = MIGRATION.apply_plan(self.output / "plan.json")
        after = self.source_bytes()
        second = MIGRATION.apply_plan(self.output / "plan.json")
        # Then no evidence, business state, failed record, counters or journal is duplicated or rewritten.
        self.assertEqual(first, second)
        self.assertEqual(self.source_bytes(), after)
        self.assertEqual(self.journal.read_bytes(), before_journal)
        self.assertEqual(self.state(self.db), self.state(self.output / "fixture/workflow.sqlite"))
        self.assertEqual(self.config_path.read_bytes(), (self.output / "proposed-config.json").read_bytes())

    def test_E8_apply_should_resume_after_database_commit_and_prepared_config_file(self):
        # Given Spec docs/spec/runtime-caller-format-migration.md R5; E8: interruption after DB commit.
        self.plan()
        with patch.object(MIGRATION.os, "replace", side_effect=OSError("test-owned interruption")):
            # When config replacement fails after its private temporary file has been prepared.
            with self.assertRaises(OSError):
                MIGRATION.apply_plan(self.output / "plan.json")
        # Then the same plan resumes exactly the committed projection and pending config replacement.
        report = MIGRATION.apply_plan(self.output / "plan.json")
        self.assertEqual(report["evidenceCount"], 27)
        self.assertEqual(self.state(self.db), self.state(self.output / "fixture/workflow.sqlite"))

    def test_E9_apply_should_refuse_business_or_journal_changes_after_plan(self):
        # Given Spec docs/spec/runtime-caller-format-migration.md R3,R5; E9: a plan followed by business progress.
        self.plan()
        with closing(sqlite3.connect(self.db)) as connection, connection:
            connection.execute('UPDATE instances SET repairCount=4')
        changed = self.source_bytes()
        # When the old plan is applied.
        with self.assertRaisesRegex(MIGRATION.MigrationError, "business"):
            MIGRATION.apply_plan(self.output / "plan.json")
        # Then the new business state and config are retained without migration effects.
        self.assertEqual(self.source_bytes(), changed)

    def test_E10_apply_should_refuse_changed_separate_journal(self):
        # Given Spec docs/spec/runtime-caller-format-migration.md R3,R5; E10: journal progress after planning.
        self.plan()
        with closing(sqlite3.connect(self.journal)) as connection, connection:
            connection.execute('INSERT INTO events VALUES (2, "new event")')
        before = self.source_bytes()
        # When applying the stale plan.
        with self.assertRaisesRegex(MIGRATION.MigrationError, "journal changed"):
            MIGRATION.apply_plan(self.output / "plan.json")
        # Then the journal and source database remain at their current values.
        self.assertEqual(self.source_bytes(), before)

    def test_E11_paths_should_refuse_symlinks_output_collisions_and_cross_root_plans(self):
        # Given Spec docs/spec/runtime-caller-format-migration.md R1,R5; E11: explicit path boundaries.
        link = self.base / "linked-root"
        link.symlink_to(self.root, target_is_directory=True)
        # When planning through a symlink, noncanonical root, or existing destination.
        with self.assertRaises(MIGRATION.MigrationError):
            MIGRATION.prepare_plan(str(link), str(self.output))
        with self.assertRaises(MIGRATION.MigrationError):
            MIGRATION.prepare_plan(str(self.root) + "/../cncf", str(self.output))
        self.plan()
        with self.assertRaises(MIGRATION.MigrationError):
            self.plan()
        # Then a relocated plan cannot choose another output/root destination.
        other = self.base / "other"
        other.mkdir()
        (other / "plan.json").write_bytes((self.output / "plan.json").read_bytes())
        with self.assertRaisesRegex(MIGRATION.MigrationError, "location"):
            MIGRATION.apply_plan(other / "plan.json")

    def test_E12_apply_should_refuse_source_symlink_swap_and_foreign_projection(self):
        # Given Spec docs/spec/runtime-caller-format-migration.md R1,R5; E12: source changed after plan.
        self.plan()
        saved = self.root / "saved.sqlite"
        self.db.rename(saved)
        self.db.symlink_to(saved)
        # When the source path is swapped to a symlink.
        with self.assertRaises(MIGRATION.MigrationError):
            MIGRATION.apply_plan(self.output / "plan.json")
        # Then no writes follow that changed path.
        self.assertEqual(self.config_path.read_bytes(), (self.output / "before-config.json").read_bytes())
        self.db.unlink()
        saved.rename(self.db)
        with closing(sqlite3.connect(self.db)) as connection, connection:
            connection.execute('ALTER TABLE host_evidence ADD COLUMN nativeExternalEvidencePayload TEXT')
            connection.execute('UPDATE host_evidence SET nativeExternalEvidencePayload="foreign" WHERE id=?', (self.rows[0][0],))
        with self.assertRaisesRegex(MIGRATION.MigrationError, "foreign projection"):
            MIGRATION.apply_plan(self.output / "plan.json")

    def test_E13_plan_should_refuse_unhandled_stores_and_wrong_explicit_count(self):
        # Given Spec docs/spec/runtime-caller-format-migration.md R1,R3; E13: additional writable Store.
        self.config["textus.other-store.path"] = str(self.db)
        self.save_config()
        # When isolation cannot redirect every explicitly configured write Store.
        with self.assertRaisesRegex(MIGRATION.MigrationError, "unhandled"):
            self.plan()
        # Then the output is absent and an explicit wrong count is also refused.
        self.assertFalse(self.output.exists())
        del self.config["textus.other-store.path"]
        self.save_config()
        with self.assertRaisesRegex(MIGRATION.MigrationError, "count"):
            MIGRATION.prepare_plan(str(self.root), str(self.output), 22)


if __name__ == "__main__":
    unittest.main()
