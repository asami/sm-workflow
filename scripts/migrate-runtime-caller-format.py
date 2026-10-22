# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Finite offline caller-format installation; never a workflow operation.

P116-MIGRATION-01. See docs/spec/runtime-caller-format-migration.md.
@since   Oct. 23, 2026
@version Oct. 23, 2026
@author  ASAMI, Tomoharu
"""

import argparse
from contextlib import closing, contextmanager
import json
import os
from pathlib import Path
import sqlite3
import stat
import sys


SCHEMA = "sm-workflow.runtime-caller-format-migration.v1"
OLD_SCHEMA = "sm-workflow.native-host-evidence.v7"
NEW_SCHEMA = "sm-workflow.native-external-evidence.v7"
OLD_FIELD = "nativeHostEvidencePayload"
NEW_FIELD = "nativeExternalEvidencePayload"
OLD_DEFINITION = "sm-workflow.three-workflow-host.v1"
NEW_DEFINITION = "sm-workflow.three-workflow-runtime.v1"
CONFIG_LIMIT = 8 * 1024 * 1024
DATABASE_LIMIT = 512 * 1024 * 1024
ROW_LIMIT = 1000000
KEYS = {"sm-workflow.host.definition": "sm-workflow.runtime.definition"}
KEYS.update({"sm-workflow.host.evidence." + name: "sm-workflow.external-evidence." + name
             for name in ("issuer", "record-collection", "store-name")})
for family, names in (
    ("goal-phase", ("association-collection", "authorization-collection", "decision-collection",
                    "plan-collection", "selection-collection", "store-name")),
    ("repository-sync", ("selection-collection", "store-name")),
    ("split-phase", ("selection-collection", "store-name")),
):
    KEYS.update({f"sm-workflow.{family}.host-records.{name}":
                 f"sm-workflow.{family}.input-records.{name}" for name in names})


class MigrationError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise MigrationError(message)


def object_pairs(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "duplicate JSON key: " + key)
        result[key] = value
    return result


def decode(raw):
    value = json.loads(raw, object_pairs_hook=object_pairs,
                       parse_constant=lambda value: reject_constant(value))
    require(isinstance(value, dict), "JSON must be a mapping")
    return value


def reject_constant(value):
    raise MigrationError("non-finite JSON number: " + value)


def encode(value):
    return (json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n").encode("utf-8")


def schema_property(raw, old, new):
    """Change the root schema token only, preserving all other JSON bytes."""
    require(decode(raw).get("schemaVersion") == old, "unknown inner schema")
    decoder = json.JSONDecoder()
    position = raw.index("{") + 1
    while True:
        while raw[position].isspace():
            position += 1
        key, position = decoder.raw_decode(raw, position)
        while raw[position].isspace():
            position += 1
        require(raw[position] == ":", "invalid schema property separator")
        position += 1
        while raw[position].isspace():
            position += 1
        value_start = position
        _, position = decoder.raw_decode(raw, position)
        if key == "schemaVersion":
            return raw[:value_start] + json.dumps(new) + raw[position:]
        while raw[position].isspace():
            position += 1
        require(raw[position] == ",", "schema property is absent")
        position += 1


def checked_path(raw, *, exists=True, directory=False):
    require(isinstance(raw, (str, Path)), "path must be explicit")
    path = Path(raw)
    require(path.is_absolute() and str(path) == str(raw), "path must be absolute and canonical")
    require(path.resolve(strict=exists) == path, "symlink or noncanonical path: " + str(path))
    for part in (path, *path.parents):
        require(not part.is_symlink(), "symlink path: " + str(part))
    if exists:
        require(path.is_dir() if directory else path.is_file(), "unexpected path kind: " + str(path))
    else:
        require(not path.exists(), "output collision: " + str(path))
        checked_path(path.parent, directory=True)
    return path


def identity(path):
    info = os.stat(checked_path(path), follow_symlinks=False)
    return [info.st_dev, info.st_ino]


def read_bytes(path, limit=CONFIG_LIMIT):
    path = checked_path(path)
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(descriptor, "rb") as stream:
        require(stat.S_ISREG(os.fstat(stream.fileno()).st_mode), "not a regular file")
        data = stream.read(limit + 1)
        require(len(data) <= limit, "file exceeds migration limit: " + str(path))
        require(identity(path) == [os.fstat(stream.fileno()).st_dev, os.fstat(stream.fileno()).st_ino],
                "file replaced while reading: " + str(path))
        return data


def new_file(path, data):
    checked_path(path, exists=False)
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "wb") as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())


def new_directory(path):
    checked_path(path, exists=False)
    path.mkdir(mode=0o700)


@contextmanager
def database(path, *, writable=False):
    path = checked_path(path)
    require(path.stat().st_size <= DATABASE_LIMIT, "database exceeds migration limit")
    original_identity = identity(path)
    # The held descriptor and repeated path/inode checks reject replacement or
    # symlink swaps around SQLite opening and before transaction completion.
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    connection = None
    try:
        connection = sqlite3.connect(path.as_uri() + ("?mode=rw" if writable else "?mode=ro"),
                                     uri=True, timeout=5)
        require(identity(path) == original_identity, "database path replaced while opening")
        connection.execute("PRAGMA trusted_schema=OFF")
        if not writable:
            connection.execute("PRAGMA query_only=ON")
        yield connection
        require(identity(path) == original_identity, "database path replaced during migration")
    finally:
        if connection is not None:
            connection.close()
        os.close(descriptor)


def quote(name):
    return '"' + name.replace('"', '""') + '"'


def snapshot(connection):
    require(connection.execute("PRAGMA integrity_check").fetchall() == [("ok",)],
            "SQLite integrity_check failed")
    schema = connection.execute(
        "SELECT type,name,tbl_name,sql FROM sqlite_schema ORDER BY type,name").fetchall()
    tables = {}
    for kind, name, _, sql in schema:
        if kind != "table":
            continue
        require(sql is None or "CREATE VIRTUAL TABLE" not in sql.upper(), "virtual tables unsupported")
        columns = connection.execute("PRAGMA table_xinfo(" + quote(name) + ")").fetchall()
        rows = connection.execute("SELECT * FROM " + quote(name)).fetchmany(ROW_LIMIT + 1)
        require(len(rows) <= ROW_LIMIT, "table exceeds migration row limit")
        # SQLite values retain their types and bytes; no JSON/digest comparison.
        tables[name] = (columns, sorted(rows, key=repr))
    return schema, tables


def evidence(connection):
    columns = connection.execute('PRAGMA table_info("host_evidence")').fetchall()
    names = [column[1] for column in columns]
    require("id" in names and OLD_FIELD in names, "exact old host_evidence layout required")
    require(NEW_FIELD not in names, "already-converted or mixed evidence projection; plan refused")
    rows = connection.execute('SELECT "id", "nativeHostEvidencePayload" FROM "host_evidence"').fetchall()
    require(len(rows) <= ROW_LIMIT, "evidence exceeds migration row limit")
    seen, projections = set(), []
    for row_id, raw in rows:
        require(isinstance(row_id, str) and row_id and row_id not in seen, "duplicate or invalid evidence id")
        seen.add(row_id)
        require(isinstance(raw, str) and raw.startswith(OLD_SCHEMA + ":"), "unknown evidence storage schema")
        body = raw[len(OLD_SCHEMA) + 1:]
        value = decode(body)
        require(value.get("schemaVersion") == OLD_SCHEMA, "unknown inner evidence schema")
        require(all(isinstance(value.get(key), str) and value[key] for key in (
            "purpose", "lookupIdentity", "reference", "revision", "issuer", "participant", "occurredAt")),
            "incomplete evidence string fields")
        require(isinstance(value.get("binding"), dict), "evidence binding must be a mapping")
        # A binding may itself contain a schemaVersion property. It is opaque
        # user evidence and must not participate in this root-token change.
        rewritten = schema_property(body, OLD_SCHEMA, NEW_SCHEMA)
        converted = NEW_SCHEMA + ":" + rewritten
        projected = decode(rewritten)
        projected["schemaVersion"] = OLD_SCHEMA
        require(projected == value, "projection changed evidence semantics")
        projections.append((row_id, raw, converted))
    return sorted(projections)


def project(connection, projections):
    # These are the only live write statements admitted by this tool.
    connection.execute('ALTER TABLE "host_evidence" ADD COLUMN "nativeExternalEvidencePayload" TEXT')
    for row_id, original, converted in projections:
        cursor = connection.execute(
            'UPDATE "host_evidence" SET "nativeExternalEvidencePayload"=? '
            'WHERE "id"=? AND "nativeHostEvidencePayload"=? AND "nativeExternalEvidencePayload" IS NULL',
            (converted, row_id, original))
        require(cursor.rowcount == 1, "evidence guard failed")


def backup(source, destination):
    new_file(destination, b"")
    with database(source) as original, database(destination, writable=True) as copy:
        original.backup(copy)
        require(snapshot(original) == snapshot(copy), "backup differs from source")


def transform_config(config):
    require(all(key in config for key in KEYS), "complete old 14-key configuration required")
    require(not any(key in config for key in KEYS.values()), "old/new configuration collision")
    require(all(isinstance(config[key], str) and config[key] for key in KEYS), "old configuration value must be a string")
    old_families = ("sm-workflow.host.", "sm-workflow.goal-phase.host-records.",
                    "sm-workflow.repository-sync.host-records.", "sm-workflow.split-phase.host-records.")
    require(not any(key.startswith(old_families) and key not in KEYS for key in config),
            "unknown legacy configuration key")
    definition = decode(config["sm-workflow.host.definition"])
    require(definition.get("schemaVersion") == OLD_DEFINITION, "unknown runtime definition schema")
    proposed = {KEYS.get(key, key): value for key, value in config.items()}
    proposed["sm-workflow.runtime.definition"] = schema_property(
        config["sm-workflow.host.definition"], OLD_DEFINITION, NEW_DEFINITION)
    require(config.get("textus.datastore.kind") == "sqlite", "explicit SQLite datastore required")
    checked_path(config.get("textus.datastore.path"))
    require(config["sm-workflow.host.evidence.record-collection"] in (
        "entity:smworkflow.local.host_evidence",
        "smworkflow-local-entity_collection-ec1_10_smworkflow_5_local_13_host_evidence-0-stable"),
        "finite migration requires the host_evidence collection")
    return proposed


def journal_paths(config):
    journals, unhandled = {}, []
    for key, value in config.items():
        lower = key.lower()
        storage = any(word in lower for word in ("store", "journal", "storage", "sqlite"))
        if lower.endswith((".path", ".url", ".directory", ".root", ".file")) and storage and key != "textus.datastore.path":
            if "journal" in lower:
                journals[key] = checked_path(value)
            else:
                unhandled.append(key)
        if storage and lower.endswith(".kind") and key != "textus.datastore.kind":
            require(value == "sqlite", "unhandled configured Store: " + key)
    require(not unhandled, "unhandled configured write Stores: " + ", ".join(unhandled))
    require(not config.get("textus.import.entity.file"), "unresolved entity StartupImport prerequisite")
    return journals


def retire_import(config, connection, raw):
    if "textus.import.data.file" not in config:
        require(raw is None, "unexpected StartupImport backup")
        return config.copy(), 0
    require(raw is not None, "unresolved StartupImport prerequisite: source unavailable")
    value = decode(raw)
    require(set(value) == {"datastore"} and isinstance(value["datastore"], list) and value["datastore"],
            "unresolved StartupImport prerequisite: unsupported container")
    seen, count = set(), 0
    for section in value["datastore"]:
        require(isinstance(section, dict) and set(section) == {"collection", "records"},
                "unresolved StartupImport prerequisite: unsupported section")
        require(section["collection"] == "entity:smworkflow.local.host_evidence",
                "unresolved StartupImport prerequisite: unsupported collection")
        require(isinstance(section["records"], list) and section["records"], "empty StartupImport records")
        for record in section["records"]:
            require(isinstance(record, dict) and set(record) == {"id", OLD_FIELD},
                    "unresolved StartupImport prerequisite: unsupported record")
            require(isinstance(record["id"], str) and record["id"] not in seen, "duplicate import record id")
            seen.add(record["id"])
            retained = connection.execute(
                'SELECT "id", "nativeHostEvidencePayload" FROM "host_evidence" WHERE "id"=?',
                (record["id"],)).fetchall()
            require(retained == [(record["id"], record[OLD_FIELD])],
                    "unresolved StartupImport prerequisite: record is not already retained exactly")
            count += 1
    result = config.copy()
    del result["textus.import.data.file"]
    return result, count


def original_files(paths):
    result = {}
    for path in paths:
        # Account for WAL and rollback sidecars without modifying/checkpointing them.
        for item in (path, *(Path(str(path) + suffix) for suffix in ("-wal", "-shm", "-journal"))):
            if item.exists() or item.is_symlink():
                result[str(item)] = (identity(item), read_bytes(item, DATABASE_LIMIT))
    return result


def verify_files_unchanged(files):
    for name, (expected_identity, expected_bytes) in files.items():
        require(identity(name) == expected_identity and read_bytes(name, DATABASE_LIMIT) == expected_bytes,
                "original file changed; make a new plan: " + name)


def prepare_plan(runtime_root, output, expected_evidence_count=None):
    root = checked_path(runtime_root, directory=True)
    output = checked_path(output, exists=False)
    require(not output.is_relative_to(root), "plan output must be outside runtime root")
    config_path = checked_path(root / ".textus/config.json")
    assembly = checked_path(root / ".textus/assembly.yaml")
    raw_config = read_bytes(config_path)
    config = decode(raw_config)
    proposed = transform_config(config)
    source = checked_path(config["textus.datastore.path"])
    journals = journal_paths(config)
    require(all(path != source for path in journals.values()), "journal must be a separate SQLite file")
    import_path = checked_path(config["textus.import.data.file"]) if "textus.import.data.file" in config else None
    raw_import = read_bytes(import_path) if import_path else None
    originals = original_files([config_path, assembly, source, *journals.values(), *([import_path] if import_path else [])])
    with database(source) as connection:
        projections = evidence(connection)
        require(expected_evidence_count is None or len(projections) == expected_evidence_count,
                "evidence count does not match explicit expectation")
        proposed, retired_count = retire_import(proposed, connection, raw_import)
    # No output is created until finite prerequisites have been established.
    new_directory(output)
    new_directory(output / "fixture")
    new_directory(output / "fixture/.textus")
    new_file(output / "before-config.json", raw_config)
    new_file(output / "before-assembly.yaml", read_bytes(assembly))
    new_file(output / "proposed-config.json", encode(proposed))
    if raw_import is not None:
        new_file(output / "before-import.json", raw_import)
    backup(source, output / "before.sqlite")
    backup(output / "before.sqlite", output / "fixture/workflow.sqlite")
    with database(output / "fixture/workflow.sqlite", writable=True) as connection:
        with connection:
            project(connection, projections)
        fixture_state = snapshot(connection)
    fixture_config = proposed.copy()
    fixture_config["textus.datastore.path"] = str(output / "fixture/workflow.sqlite")
    journal_inventory = []
    for ordinal, (key, path) in enumerate(sorted(journals.items())):
        before = output / f"before-journal-{ordinal}.sqlite"
        copied = output / f"fixture/journal-{ordinal}.sqlite"
        backup(path, before)
        backup(before, copied)
        copied.chmod(0o400)
        before.chmod(0o400)
        fixture_config[key] = str(copied)
        journal_inventory.append({"key": key, "source": str(path), "identity": identity(path), "ordinal": ordinal})
    new_file(output / "fixture/.textus/config.json", encode(fixture_config))
    new_file(output / "fixture/.textus/assembly.yaml", read_bytes(assembly))
    with database(output / "before.sqlite") as before, closing(sqlite3.connect(":memory:")) as expected, expected:
        before.backup(expected)
        project(expected, projections)
        require(snapshot(expected) == fixture_state, "fixture verification failed")
    verify_files_unchanged(originals)
    with database(source) as current, database(output / "before.sqlite") as before:
        require(snapshot(current) == snapshot(before), "source changed during plan; make a new plan")
    plan = {"schema": SCHEMA, "runtimeRoot": str(root), "output": str(output),
            "datastore": str(source), "sourceIdentity": identity(source),
            "configIdentity": identity(config_path), "assemblyIdentity": identity(assembly),
            "evidenceCount": len(projections), "expectedEvidenceCount": expected_evidence_count,
            "journals": journal_inventory, "importPath": str(import_path) if import_path else None,
            "importIdentity": identity(import_path) if import_path else None, "retiredImportRecords": retired_count,
            "keyMapping": KEYS, "projection": {"table": "host_evidence", "original": OLD_FIELD, "added": NEW_FIELD,
                "ids": [row[0] for row in projections]},
            "limits": {"configBytes": CONFIG_LIMIT, "databaseBytes": DATABASE_LIMIT, "tableRows": ROW_LIMIT},
            "verification": {"integrity": "ok", "originalCells": "unchanged", "sourceBytes": "unchanged"}}
    new_file(output / "plan.json", encode(plan))
    new_file(output / "plan-report.json", encode(plan["verification"] | {"evidenceCount": len(projections)}))
    (output / "before.sqlite").chmod(0o400)
    return plan


def verify_identity(path, expected):
    require(identity(path) == expected, "source path identity changed; make a new plan: " + str(path))


def apply_plan(plan_path):
    plan_path = checked_path(plan_path)
    require(plan_path.name == "plan.json", "select the generated plan.json")
    output = checked_path(plan_path.parent, directory=True)
    plan = decode(read_bytes(plan_path))
    require(plan.get("schema") == SCHEMA and plan.get("output") == str(output), "invalid plan location/schema")
    root = checked_path(plan.get("runtimeRoot"), directory=True)
    require(not output.is_relative_to(root), "plan output must be outside runtime root")
    config_path = checked_path(root / ".textus/config.json")
    assembly = checked_path(root / ".textus/assembly.yaml")
    before_raw = read_bytes(output / "before-config.json")
    config = decode(before_raw)
    proposed = transform_config(config)
    source = checked_path(config["textus.datastore.path"])
    require(plan.get("datastore") == str(source), "plan datastore differs from original config")
    verify_identity(source, plan.get("sourceIdentity"))
    verify_identity(assembly, plan.get("assemblyIdentity"))
    require(read_bytes(assembly) == read_bytes(output / "before-assembly.yaml"), "assembly changed; make a new plan")
    import_path = checked_path(config["textus.import.data.file"]) if "textus.import.data.file" in config else None
    require(plan.get("importPath") == (str(import_path) if import_path else None), "plan import path mismatch")
    raw_import = read_bytes(output / "before-import.json") if import_path else None
    if import_path:
        verify_identity(import_path, plan.get("importIdentity"))
        require(read_bytes(import_path) == raw_import, "StartupImport source changed; make a new plan")
    with database(output / "before.sqlite") as before, closing(sqlite3.connect(":memory:")) as expected, expected:
        projections = evidence(before)
        proposed, retired_count = retire_import(proposed, before, raw_import)
        require(plan.get("evidenceCount") == len(projections) and plan.get("retiredImportRecords") == retired_count,
                "plan count differs from retained snapshot")
        require(plan.get("expectedEvidenceCount") in (None, len(projections)), "plan expected count mismatch")
        require(plan.get("keyMapping") == KEYS and plan.get("projection") == {
            "table": "host_evidence", "original": OLD_FIELD, "added": NEW_FIELD,
            "ids": [row[0] for row in projections]}, "plan finite mapping differs")
        proposed_raw = encode(proposed)
        require(read_bytes(output / "proposed-config.json") == proposed_raw, "proposed configuration altered")
        current_raw = read_bytes(config_path)
        require(current_raw in (before_raw, proposed_raw), "configuration changed; make a new plan")
        if current_raw == before_raw:
            verify_identity(config_path, plan.get("configIdentity"))
        journals = journal_paths(config)
        require(plan.get("journals") == [
            {"key": key, "source": str(path), "identity": identity(path), "ordinal": ordinal}
            for ordinal, (key, path) in enumerate(sorted(journals.items()))], "journal paths or identities changed")
        journal_originals = original_files(journals.values())
        for ordinal, (_, path) in enumerate(sorted(journals.items())):
            with database(path) as current, database(output / f"before-journal-{ordinal}.sqlite") as saved:
                require(snapshot(current) == snapshot(saved), "journal changed; make a new plan")
        before_state = snapshot(before)
        before.backup(expected)
        project(expected, projections)
        expected_state = snapshot(expected)
        # Hold the ordinary SQLite transaction from the first complete cell
        # comparison until commit; no custom lock or process discovery.
        with database(source, writable=True) as current:
            try:
                current.execute("BEGIN IMMEDIATE")
                state = snapshot(current)
                require(state in (before_state, expected_state),
                        "business rows/schema or foreign projection changed; make a new plan")
                if state == before_state:
                    project(current, projections)
                require(snapshot(current) == expected_state, "post-migration cell verification failed")
                verify_identity(source, plan["sourceIdentity"])
                require(read_bytes(config_path) == current_raw, "configuration changed during apply")
                verify_files_unchanged(journal_originals)
                current.commit()
            except BaseException:
                current.rollback()
                raise
        # Database commit and config replacement are separate durable effects.
        # A crash here is recovered by the same plan's exact expected state.
        if current_raw != proposed_raw:
            replacement = config_path.parent / ".runtime-caller-format-config.tmp"
            if replacement.exists() or replacement.is_symlink():
                require(read_bytes(replacement) == proposed_raw, "foreign configuration replacement file")
                require(stat.S_IMODE(replacement.stat().st_mode) == 0o600, "replacement file mode is not private")
            else:
                new_file(replacement, proposed_raw)
            verify_identity(source, plan["sourceIdentity"])
            require(read_bytes(config_path) == before_raw, "configuration changed before replacement")
            verify_identity(config_path, plan["configIdentity"])
            os.replace(replacement, config_path)
            directory = os.open(config_path.parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
            try:
                os.fsync(directory)
            finally:
                os.close(directory)
        require(read_bytes(config_path) == proposed_raw, "configuration replacement verification failed")
        with database(source) as current:
            require(snapshot(current) == expected_state, "database changed after commit; retain plan and inspect")
        verify_files_unchanged(journal_originals)
    report = {"schema": SCHEMA, "evidenceCount": len(projections), "originalCells": "unchanged",
              "journals": "unchanged", "configuration": "migrated", "workflowOperations": 0}
    # The durable report is created once; repeated apply verifies it verbatim.
    report_path = output / "apply-report.json"
    if report_path.exists() or report_path.is_symlink():
        require(read_bytes(report_path) == encode(report), "existing apply report differs")
    else:
        new_file(report_path, encode(report))
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    plan = commands.add_parser("plan")
    plan.add_argument("--runtime-root", required=True)
    plan.add_argument("--output", required=True)
    plan.add_argument("--expected-evidence-count", type=int)
    apply = commands.add_parser("apply")
    apply.add_argument("--plan", required=True)
    args = parser.parse_args(argv)
    try:
        if args.command == "plan":
            require(args.expected_evidence_count is None or args.expected_evidence_count >= 0, "negative expected count")
            result = prepare_plan(args.runtime_root, args.output, args.expected_evidence_count)
        else:
            result = apply_plan(args.plan)
        print(json.dumps(result, ensure_ascii=False))
        return 0
    except (MigrationError, OSError, sqlite3.Error, UnicodeError, json.JSONDecodeError) as error:
        print("Migration prerequisite/refusal: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
