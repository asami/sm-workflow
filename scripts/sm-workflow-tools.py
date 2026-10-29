#!/usr/bin/env -S uv run --no-cache --managed-python --no-python-downloads --script
# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Package and install only the shared sm-workflow skills, contracts and agents."""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import stat
import subprocess
import sys
import tomllib
import zipfile

SKILLS = ('sm-goal-phase', 'sm-repository-sync', 'sm-split-phase',
          'sm-goal-task', 'sm-test-fix', 'sm-review-fix', 'sm-delegate-task')
ROLES = ('analysis', 'design', 'implementation', 'repair', 'review')
AGENTS = tuple('sm_workflow_' + role for role in ROLES)
DOCS = ('native-host-execution', 'runtime-owned-local-publication', 'goal-phase-acceptance-boundary',
        'goal-phase-purpose-repair', 'goal-phase-precommit-step-review', 'goal-phase-release-repair-continuation',
        'goal-phase-dependency-scope', 'goal-phase-validation-metadata', 'native-goal-command-task',
        'caller-information', 'runtime-caller-format-migration',
        'goal-phase-dependency-source-revision', 'repository-sync-validation-continuation', 'repository-sync-runtime-orchestration',
        'goal-task-runtime', 'task-fix-delivery', 'standalone-fix-runtime',
        'standalone-fix-acceptance', 'standalone-review-fix-runtime',
        'delegate-task', 'native-delegate-task-caller', 'commit-completion-and-recovery')
SCRIPTS = ('native-goal-command.py', 'native-goal-authority.py', 'migrate-runtime-caller-format.py')
PAYLOAD_PATHS = (tuple('skills/' + name + '/SKILL.md' for name in SKILLS)
                 + tuple('docs/spec/' + name + '.md' for name in DOCS)
                 + tuple('scripts/' + name for name in SCRIPTS)
                 + tuple('agents/' + name + '.toml' for name in AGENTS))
INSTALLER = 'install-sm-workflow.py'


def check_runtime():
    if (sys.implementation.name != 'cpython' or sys.version_info < (3, 12)
            or sys.version_info.releaselevel != 'final'):
        raise ValueError('ENVIRONMENT_PRECONDITION_ERROR: stable CPython >=3.12 is required')
    managed = subprocess.check_output(['uv', 'python', 'dir', '--no-cache'], text=True,
                                      stderr=subprocess.PIPE).strip()
    if not Path(sys.base_prefix).resolve().is_relative_to(Path(managed).resolve()):
        raise ValueError('ENVIRONMENT_PRECONDITION_ERROR: use uv-managed CPython')


def validate_payload(payload):
    if set(payload) != set(PAYLOAD_PATHS):
        raise ValueError('bundle must contain exactly the selected skills, contracts, finite callers and agents')
    for name, body in payload.items():
        if not body or len(body) > 2 * 1024 * 1024:
            raise ValueError('empty or oversized bundle member: ' + name)
        text = body.decode('utf-8')
        if name.startswith('skills/'):
            expected = name.split('/')[1]
            front = re.match(r'\A---\n(.*?)\n---(?:\n|$)', text, re.S)
            if not front or not re.search(r'^name: ' + re.escape(expected) + r'$', front[1], re.M):
                raise ValueError('invalid skill identity/frontmatter: ' + name)
        if name.startswith('agents/'):
            agent = tomllib.loads(text)
            allowed = {'name', 'description', 'model', 'model_reasoning_effort',
                       'developer_instructions', 'features', 'skills'}
            if set(agent) - allowed:
                raise ValueError('unsupported agent settings: ' + name)
            for field in ('name', 'description', 'model', 'model_reasoning_effort', 'developer_instructions'):
                if not isinstance(agent.get(field), str) or not agent[field].strip():
                    raise ValueError('missing agent ' + field + ': ' + name)
            if agent['name'] != Path(name).stem:
                raise ValueError('agent name must match its filename: ' + name)


def load_source(source):
    source = source.resolve(strict=True)
    payload = {}
    for name in PAYLOAD_PATHS:
        path = source / name
        require_regular_path(path, source)
        payload[name] = path.read_bytes()
    validate_payload(payload)
    return payload


def package_bundle(source, output):
    payload = load_source(source)
    payload[INSTALLER] = Path(__file__).read_bytes()
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.is_symlink():
        raise ValueError('bundle output must not be a symlink')
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, body in sorted(payload.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (stat.S_IFREG | 0o644) << 16
            archive.writestr(info, body)


def load_bundle(bundle):
    with zipfile.ZipFile(bundle) as archive:
        names = archive.namelist()
        files = set(PAYLOAD_PATHS) | {INSTALLER}
        directories = {str(parent) + '/' for name in files
                       for parent in Path(name).parents if parent != Path('.')}
        if (len(names) != len(set(names)) or not files <= set(names)
                or set(names) - files - directories):
            raise ValueError('unexpected, missing or duplicate bundle member')
        for info in archive.infolist():
            mode = info.external_attr >> 16
            if stat.S_ISLNK(mode) or info.file_size > 2 * 1024 * 1024:
                raise ValueError('symlink or oversized bundle member: ' + info.filename)
        payload = {name: archive.read(name) for name in PAYLOAD_PATHS}
    validate_payload(payload)
    return payload


def require_regular_path(path, root):
    """Check the selected tree's real filesystem boundary before any write."""
    if root.exists() and not root.is_dir():
        raise ValueError('expected destination directory: ' + str(root))
    for item in (path, *path.parents):
        if item == root:
            break
        if item.is_symlink():
            raise ValueError('unexpected symlink: ' + str(item))
        if item != path and item.exists() and not item.is_dir():
            raise ValueError('expected parent directory: ' + str(item))
    if path.exists() and not path.is_file():
        raise ValueError('expected regular file: ' + str(path))


def installation_plan(payload, shared, home, replace=False):
    shared, home = shared.resolve(), home.resolve()
    plan = []
    for name in PAYLOAD_PATHS:
        destinations = [shared / name]
        if name.startswith('agents/'):
            destinations.append(home / '.codex' / name)
        for path in destinations:
            root = shared if path.is_relative_to(shared) else home
            require_regular_path(path, root)
            if path.exists() and path.read_bytes() == payload[name]:
                continue
            if path.exists() and not replace:
                raise ValueError('existing content differs; reconcile or explicitly select --replace: ' + str(path))
            plan.append(('copy', path, payload[name]))
    for name in SKILLS:
        path = home / '.agents/skills' / name
        target = shared / 'skills' / name
        duplicate = home / '.codex/skills' / name
        if duplicate.exists() or duplicate.is_symlink():
            raise ValueError('duplicate skill discovery path: ' + str(duplicate))
        for parent in path.parents:
            if parent == home:
                break
            if parent.is_symlink():
                raise ValueError('unexpected discovery parent symlink: ' + str(parent))
            if parent.exists() and not parent.is_dir():
                raise ValueError('expected discovery parent directory: ' + str(parent))
        if path.is_symlink() and path.resolve() == target:
            continue
        if path.exists() or path.is_symlink():
            raise ValueError('preserve and reconcile occupied skill discovery path: ' + str(path))
        plan.append(('link', path, target))
    return plan


def install(payload, shared, home, write=False, replace=False):
    plan = installation_plan(payload, shared, home, replace)
    for action, path, value in plan:
        print(action.upper() + ' ' + str(path))
    if write:
        for action, path, value in plan:
            path.parent.mkdir(parents=True, exist_ok=True)
            if action == 'link':
                path.symlink_to(value, target_is_directory=True)
            elif action == 'mode':
                path.chmod(value)
            else:
                path.write_bytes(value)
                path.chmod(0o644)
    print(f'changes={len(plan)} write={str(write).lower()}')
    return len(plan)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    actions = parser.add_subparsers(dest='action', required=True)
    package = actions.add_parser('package')
    package.add_argument('--source', required=True, type=Path, help='Selected common/codex tree')
    package.add_argument('--output', required=True, type=Path)
    install_parser = actions.add_parser('install')
    install_parser.add_argument('--bundle', required=True, type=Path)
    install_parser.add_argument('--shared-root', required=True, type=Path, help='Shared common/codex destination')
    install_parser.add_argument('--home', type=Path, default=Path.home())
    install_parser.add_argument('--write', action='store_true')
    install_parser.add_argument('--replace', action='store_true', help='Explicitly replace differing selected payload files')
    status = actions.add_parser('status')
    status.add_argument('--source', required=True, type=Path)
    status.add_argument('--home', type=Path, default=Path.home())
    args = parser.parse_args()
    try:
        check_runtime()
        if args.action == 'package':
            package_bundle(args.source, args.output)
            print('bundle=' + str(args.output.resolve()) + ' members=' + str(len(PAYLOAD_PATHS) + 1))
            return 0
        if args.action == 'install':
            install(load_bundle(args.bundle), args.shared_root, args.home, args.write, args.replace)
            return 0
        return int(bool(install(load_source(args.source), args.source, args.home)))
    except (OSError, ValueError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        print('sm-workflow-tools: ' + str(error), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
