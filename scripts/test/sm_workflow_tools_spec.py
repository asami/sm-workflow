#!/usr/bin/env -S uv run --no-cache --managed-python --no-python-downloads --script
# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Exercise sm-only distribution and installation in disposable homes."""
from contextlib import redirect_stdout
import importlib.util
import io
import json
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import tomllib
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('sm_tools', ROOT / 'scripts/sm-workflow-tools.py')
tools = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tools)


class SmWorkflowInstallationSpec(unittest.TestCase):
    def setUp(self):
        fixtures = ROOT / 'target/sm-workflow-tools/work/tests'
        fixtures.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=fixtures)
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.shared = self.root / 'workstation/common/codex'
        self.home = self.root / 'home'
        self.archive = self.root / 'sm-workflow-tools.zip'
        tools.package_bundle(ROOT, self.archive)
        self.payload = tools.load_bundle(self.archive)

    def install(self, write=True, replace=False):
        with redirect_stdout(io.StringIO()):
            return tools.install(self.payload, self.shared, self.home, write, replace)

    def test_bundle_round_trip_and_repeatable_packaging(self):
        # Given an archive of the current skill and agent sources.
        second = self.root / 'second.zip'
        # When the same sources are packaged again.
        tools.package_bundle(ROOT, second)
        # Then the payload and portable installer are reproducible and complete.
        self.assertEqual(self.archive.read_bytes(), second.read_bytes())
        with zipfile.ZipFile(self.archive) as archive:
            self.assertEqual(len(archive.namelist()), 36)
            self.assertIn(tools.INSTALLER, archive.namelist())
            for member in archive.infolist():
                expected = 0o644
                self.assertEqual((member.external_attr >> 16) & 0o777, expected)
        for relative, body in self.payload.items():
            self.assertEqual(body, (ROOT / relative).read_bytes())

    def test_product_zip_structural_directories_are_accepted(self):
        # Given a normal SBT-style source archive with explicit directory entries.
        with zipfile.ZipFile(self.archive, 'a') as archive:
            directories = {str(parent) + '/' for name in archive.namelist()
                           for parent in Path(name).parents if parent != Path('.')}
            for name in sorted(directories):
                archive.writestr(name, b'')
        # When the transported installer loads and installs that product archive.
        self.payload = tools.load_bundle(self.archive)
        # Then structural entries create no extra payload or installation targets.
        self.assertEqual(self.install(), 47)
        self.assertEqual(self.install(), 0)

    def test_unexpected_or_duplicate_archive_directories_are_rejected(self):
        for name in ('unrelated/', '../skills/', 'skills/'):
            with self.subTest(directory=name):
                candidate = self.root / 'invalid-directory.zip'
                tools.package_bundle(ROOT, candidate)
                with zipfile.ZipFile(candidate, 'a') as archive:
                    archive.writestr(name, b'')
                    if name == 'skills/':
                        archive.writestr(name, b'')
                with self.assertRaises(ValueError):
                    tools.load_bundle(candidate)

    def test_fresh_install_is_repeatable_and_preserves_other_roles_and_config(self):
        # Given an existing CNCF installation and configuration.
        other = self.home / '.codex/agents/cncf_command_runner.toml'
        other.parent.mkdir(parents=True)
        other.write_bytes(b'existing unrelated definition')
        config = self.home / '.codex/config.toml'
        config.write_bytes(b'existing config and credentials are not install targets')
        # When the sm-only bundle is installed and then installed again.
        first = self.install()
        second = self.install()
        # Then only the selected files and links are added, once.
        self.assertEqual(first, 47)
        self.assertEqual(second, 0)
        self.assertEqual(other.read_bytes(), b'existing unrelated definition')
        self.assertEqual(config.read_bytes(), b'existing config and credentials are not install targets')
        for agent in tools.AGENTS:
            path = self.home / '.codex/agents' / (agent + '.toml')
            self.assertFalse(path.is_symlink())
            self.assertEqual(tomllib.loads(path.read_text())['name'], agent)
            self.assertEqual(path.stat().st_mode & 0o777, 0o644)
        for skill in tools.SKILLS:
            link = self.home / '.agents/skills' / skill
            self.assertTrue(link.is_symlink())
            contract = link / '../../docs/spec/native-host-execution.md'
            self.assertTrue(contract.resolve().is_file())
        for caller in tools.SCRIPTS:
            path = self.shared / 'scripts' / caller
            self.assertFalse(path.is_symlink())
            self.assertEqual(path.read_bytes(), self.payload['scripts/' + caller])
            self.assertEqual(path.stat().st_mode & 0o777,
                             0o644)

    def test_dry_run_creates_no_shared_or_home_files(self):
        # Given an empty destination home and shared skill root.
        # When installation is inspected without write permission.
        # Then proposed changes are reported without creating either root.
        self.assertGreater(self.install(write=False), 0)
        self.assertFalse(self.shared.exists())
        self.assertFalse(self.home.exists())

    def test_installed_skills_resolve_their_declared_contracts(self):
        # Given the complete current bundle installed into a fresh transported home.
        self.install()
        # When a caller reads each skill through its ordinary discovery link.
        for name in tools.SKILLS:
            skill = self.home / '.agents/skills' / name / 'SKILL.md'
            for link in re.findall(r'\]\(([^)]+)\)', skill.read_text()):
                if link.startswith('../../docs/spec/'):
                    with self.subTest(skill=name, contract=link):
                        # Then every direct shared contract resolves within the deployed tree.
                        destination = (skill.parent / link.split('#')[0]).resolve()
                        self.assertTrue(destination.is_relative_to(self.shared))
                        self.assertTrue(destination.is_file(), str(destination))

    def test_deployed_caller_preserves_assessment_and_journal_outcomes(self):
        # Given an installed caller and an actual-shaped result without an AI review.
        self.install()
        spec = importlib.util.spec_from_file_location('deployed_caller', self.shared / 'scripts/native-goal-command.py')
        caller = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(caller)
        assessment = {'branch': 'admit', 'reason': 'Selected checks passed',
                      'triage': 'not-selected', 'review': 'not-executed',
                      'validationReference': 'native-check:1', 'policy': {'source': 'private'}}
        journal = {'status': 'indeterminate', 'entry': None}
        # When the ordinary display projection consumes the supplied native validation.
        displayed = caller.observation_projection({'validation': {
            'outcome': 'passed', 'assessment': assessment, 'assessmentJournal': journal}})['validation']
        # Then omitted review is not promoted to PASS, and Journal failure remains visible.
        self.assertEqual(displayed['assessment'], {key: value for key, value in assessment.items() if key != 'policy'})
        self.assertEqual(displayed['assessmentJournal'], journal)
        self.assertEqual(displayed['outcome'], 'passed')

    def task_fixture(self, response_kind='Submitted'):
        """Transport fixture only; no native Session, authority or acceptance."""
        self.install()
        runtime = self.root / 'ordinary-target'
        task = runtime / 'target/sm-workflow/tasks/retained-task'
        task.mkdir(parents=True)
        route = {'family': 'GoalTask', 'sessionId': 'fixture-task'}
        (task / 'workflow-session.json').write_text(json.dumps(route))
        issued = {'continuationIdentity': 'old-issued-id', 'expectedRevision': '7',
                  'context': {'opaque_field': ['unchanged', 13]}}
        request = self.root / 'request.json'
        request.write_text(json.dumps(dict(route, kind='Request', work={'kind': 'Review'},
            submitOperation='submitGoalTaskWorkResult', submitSessionId='fixture-task',
            resultType='GoalTaskWorkResult', issued_work_context=issued)))
        submission = self.root / 'submission.json'
        submission.write_text(json.dumps({'result': {'summary': 'fixture only'},
                                         'resultReference': {'identity': 'fixture', 'revision': '1'}}))
        executable = self.root / 'sm-workflow'
        calls = self.root / 'calls.jsonl'
        executable.write_text(f'''#!{sys.executable}
import json, sys
from pathlib import Path
with Path({str(calls)!r}).open('a') as stream:
    stream.write(json.dumps(sys.argv[1:]) + '\\n')
operation = sys.argv[sys.argv.index('command') + 1].split('.')[-1]
response = {{'sessionId': 'fixture-task', 'message': 'Transport fixture only'}}
if operation == 'submitGoalTaskWorkResult':
    response['kind'] = {response_kind!r}
elif operation == 'advanceGoalTask':
    response['kind'] = 'Incomplete'
elif operation == 'prepareEvidence':
    response['datastore'] = []
else:
    sys.exit(9)
print(json.dumps(response))
''')
        executable.chmod(0o755)
        output = task / 'exchange'
        args = [sys.executable, str(self.shared / 'scripts/native-goal-command.py'),
                '--executable', str(executable), '--runtime-root', str(runtime),
                '--task', str(task), '--output', str(output), '--action', 'complete',
                '--request', str(request), '--submission', str(submission)]
        return args, calls, request, issued, output

    def test_transported_task_caller_forwards_original_context_and_advances_once(self):
        # Given an installed helper, a retained Task and no fixed .textus files.
        args, calls, request, issued, output = self.task_fixture()
        before = request.read_bytes()
        # When the fixture CLI returns Task Submitted followed by Incomplete.
        result = subprocess.run(args, capture_output=True, text=True)
        # Then the exact original context is forwarded and both raw results survive.
        self.assertEqual(result.returncode, 0, result.stderr)
        invoked = [json.loads(line) for line in calls.read_text().splitlines()]
        self.assertEqual(len(invoked), 2)
        self.assertTrue(invoked[0][3].endswith('GoalTaskService.submitGoalTaskWorkResult'))
        self.assertTrue(invoked[1][3].endswith('GoalTaskService.advanceGoalTask'))
        self.assertEqual(json.loads(invoked[0][invoked[0].index('--issuedWorkContext') + 1]), issued)
        self.assertFalse(any('assembly.descriptor' in item for call in invoked for item in call))
        self.assertEqual(request.read_bytes(), before)
        self.assertEqual(json.loads((output / 'response.json').read_text())['kind'], 'Submitted')
        self.assertEqual(json.loads((output / 'advance/response.json').read_text())['kind'], 'Incomplete')

    def test_task_without_original_context_stops_before_cli_launch(self):
        args, calls, request, issued, output = self.task_fixture()
        value = json.loads(request.read_text())
        del value['issued_work_context']
        request.write_text(json.dumps(value))
        result = subprocess.run(args, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('issuedWorkContext', result.stderr)
        self.assertFalse(calls.exists())

    def test_task_unacknowledged_submit_retains_response_without_advance(self):
        args, calls, request, issued, output = self.task_fixture('Incomplete')
        result = subprocess.run(args, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(len(calls.read_text().splitlines()), 1)
        self.assertEqual(json.loads((output / 'response.json').read_text())['kind'], 'Incomplete')

    def test_task_review_preparation_keeps_task_family(self):
        args, calls, request, issued, output = self.task_fixture()
        args[args.index('complete')] = 'prepare-review-input'
        del args[args.index('--submission'):]
        for name in ('result', 'reference', 'execution-evidence'):
            path = self.root / (name + '.json')
            path.write_text('{}')
            args.extend(['--' + name, str(path)])
        args.extend(['--issuer', 'fixture', '--occurred-at', '2026-10-27T00:00:00Z',
                     '--collection', 'entity:fixture.native.evidence'])
        result = subprocess.run(args, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        call = json.loads(calls.read_text())
        self.assertEqual(json.loads(call[call.index('--input') + 1])['family'], 'GoalTask')

    def test_bundled_installer_runs_outside_the_authoring_checkout(self):
        # Given a transported ZIP, its installer needs no imports from the workstation checkout.
        installer = self.root / tools.INSTALLER
        with zipfile.ZipFile(self.archive) as archive:
            installer.write_bytes(archive.read(tools.INSTALLER))
        command = [sys.executable, str(installer), 'install', '--bundle', str(self.archive),
                   '--shared-root', str(self.shared), '--home', str(self.home)]
        # When the portable installer is inspected from an unrelated directory.
        dry = subprocess.run(command, cwd=self.root, capture_output=True, text=True)
        # Then inspection succeeds without creating the home.
        self.assertEqual(dry.returncode, 0, dry.stderr)
        self.assertFalse(self.home.exists())
        # When the same bundle is installed with explicit write permission.
        applied = subprocess.run(command + ['--write'], cwd=self.root, capture_output=True, text=True)
        # Then installation succeeds and status reports no remaining changes.
        self.assertEqual(applied.returncode, 0, applied.stderr)
        status = subprocess.run([sys.executable, str(installer), 'status', '--source', str(self.shared),
                                 '--home', str(self.home)], cwd=self.root, capture_output=True, text=True)
        self.assertEqual(status.returncode, 0, status.stderr)
        self.assertIn('changes=0', status.stdout)

    def test_differing_agent_is_preserved_before_any_write(self):
        # Given a locally edited destination agent.
        path = self.home / '.codex/agents/sm_workflow_review.toml'
        path.parent.mkdir(parents=True)
        path.write_bytes(b'local edit')
        # When replacement has not been authorized.
        # Then installation stops before writing any shared file.
        with self.assertRaisesRegex(ValueError, 'existing content differs'):
            self.install()
        self.assertEqual(path.read_bytes(), b'local edit')
        self.assertFalse(self.shared.exists())

    def test_explicit_replacement_updates_only_selected_files(self):
        # Given an installed bundle and an older selected agent definition.
        self.install()
        path = self.home / '.codex/agents/sm_workflow_review.toml'
        path.write_bytes(b'selected older definition')
        # When replacement of selected files is explicitly requested.
        # Then only the differing selected agent is replaced.
        self.assertEqual(self.install(replace=True), 1)
        self.assertEqual(path.read_bytes(), self.payload['agents/sm_workflow_review.toml'])

    def test_shared_local_edits_are_not_overwritten(self):
        # Given newer local edits to a shared contract.
        path = self.shared / 'docs/spec/native-host-execution.md'
        path.parent.mkdir(parents=True)
        path.write_bytes(b'newer workstation edit')
        # When installation is requested without replacement authority.
        # Then the local contract survives and the home remains untouched.
        with self.assertRaisesRegex(ValueError, 'existing content differs'):
            self.install()
        self.assertEqual(path.read_bytes(), b'newer workstation edit')
        self.assertFalse(self.home.exists())

    def test_occupied_skill_directory_stops_whole_installation(self):
        # Given a real directory occupying a selected skill discovery link.
        path = self.home / '.agents/skills/sm-goal-phase'
        path.mkdir(parents=True)
        (path / 'keep.txt').write_text('preserve')
        # When replacement of selected file content is allowed.
        # Then installation still preserves the directory and stops before writes.
        with self.assertRaisesRegex(ValueError, 'occupied skill'):
            self.install(replace=True)
        self.assertEqual((path / 'keep.txt').read_text(), 'preserve')
        self.assertFalse(self.shared.exists())

    def test_duplicate_discovery_is_rejected_even_with_correct_standard_link(self):
        # Given a correct installation with a second legacy discovery directory.
        self.install()
        duplicate = self.home / '.codex/skills/sm-goal-phase'
        duplicate.mkdir(parents=True)
        # When that home is inspected for installation again.
        # Then duplicate discovery is rejected rather than silently accepted.
        with self.assertRaisesRegex(ValueError, 'duplicate skill'):
            self.install()

    def test_symlinked_agent_and_ancestor_cannot_redirect_writes(self):
        # Given an agent directory symlink pointing outside the selected home.
        outside = self.root / 'outside'
        outside.mkdir()
        agents = self.home / '.codex/agents'
        agents.parent.mkdir(parents=True)
        agents.symlink_to(outside, target_is_directory=True)
        # When installation with replacement is requested.
        # Then the redirected target stays empty and no shared files are written.
        with self.assertRaisesRegex(ValueError, 'unexpected symlink'):
            self.install(replace=True)
        self.assertEqual(list(outside.iterdir()), [])
        self.assertFalse(self.shared.exists())

    def test_nondirectory_ancestor_is_rejected_before_any_write(self):
        # Given a regular file occupying a discovery ancestor directory.
        self.home.mkdir()
        (self.home / '.agents').write_text('preserve file')
        # When installation is requested.
        # Then the obstruction is reported before any shared files are written.
        with self.assertRaisesRegex(ValueError, 'parent directory'):
            self.install()
        self.assertFalse(self.shared.exists())

    def test_invalid_agent_identity_is_rejected(self):
        # Given a selected sm agent payload declaring another role's name.
        broken = dict(self.payload)
        broken['agents/sm_workflow_review.toml'] = b'name = "cncf_review"'
        # When the payload is validated.
        # Then the mismatched identity is rejected.
        with self.assertRaises(ValueError):
            tools.validate_payload(broken)

    def test_missing_traversal_duplicate_and_symlink_bundle_members_are_rejected(self):
        # Given archives with missing, escaping, duplicate or symlink members.
        cases = ('missing', 'traversal', 'duplicate', 'symlink')
        with zipfile.ZipFile(self.archive) as archive:
            original = {name: archive.read(name) for name in archive.namelist()}
        for case in cases:
            with self.subTest(case=case):
                path = self.root / (case + '.zip')
                with zipfile.ZipFile(path, 'w') as archive:
                    for name, body in original.items():
                        if case == 'missing' and name == tools.INSTALLER:
                            continue
                        if case == 'symlink' and name == tools.INSTALLER:
                            info = zipfile.ZipInfo(name)
                            info.external_attr = (stat.S_IFLNK | 0o777) << 16
                            archive.writestr(info, 'outside')
                        else:
                            archive.writestr(name, body)
                    if case == 'traversal':
                        archive.writestr('../outside', 'unapproved')
                    if case == 'duplicate':
                        archive.writestr(tools.INSTALLER, 'duplicate')
                # When each malformed archive is loaded.
                # Then loading rejects it before installation can begin.
                with self.assertRaises(ValueError):
                    tools.load_bundle(path)


if __name__ == '__main__':
    raise SystemExit(unittest.main())
