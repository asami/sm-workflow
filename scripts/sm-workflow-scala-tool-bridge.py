#!/usr/bin/env python3
"""Fixed, read-only Scalafmt preview companion for the workflow Process capability.

The host fixes the Java executable and tool classpath in the registered program
arguments. The request supplies only bounded source/configuration data.
"""
import argparse
import base64
import binascii
import json
import os
import re
from pathlib import Path, PurePosixPath
import subprocess
import sys
import tempfile

LIMIT = 1048576


def relative(value):
    if not isinstance(value, str) or not value or '\\' in value:
        raise ValueError('invalid relative path')
    path = PurePosixPath(value)
    if path.is_absolute() or any(part in ('', '.', '..') for part in value.split('/')):
        raise ValueError('invalid relative path')
    return path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--tool', choices=['scalafmt', 'scalafix'], default='scalafmt')
    parser.add_argument('--java', required=True)
    parser.add_argument('--classpath-file', required=True)
    args = parser.parse_args()
    if not os.path.isabs(args.java) or not os.path.isabs(args.classpath_file):
        raise ValueError('provider paths must be absolute')
    classpath = Path(args.classpath_file).read_text(encoding='utf-8').strip()
    if not classpath or not all(os.path.isabs(p) for p in classpath.split(os.pathsep)):
        raise ValueError('provider classpath must contain absolute paths')
    raw = sys.stdin.buffer.read(LIMIT + 1)
    if len(raw) > LIMIT:
        raise ValueError('input exceeds bound')
    request = json.loads(raw, object_pairs_hook=unique)
    if args.tool == 'scalafix':
        scalafix(request, args.java, classpath)
        return
    if (not isinstance(request, dict) or set(request) != {'schemaVersion', 'path', 'source', 'configFiles'}
            or request['schemaVersion'] != 'sm-workflow.scalafmt-preview-input.v1'):
        raise ValueError('invalid input envelope')
    path = relative(request['path'])
    if not str(path).endswith('.scala') or not isinstance(request['source'], str):
        raise ValueError('invalid source')
    configs = request['configFiles']
    if (not isinstance(configs, dict) or '.scalafmt.conf' not in configs or len(configs) > 32
            or any(not isinstance(k, str) or not isinstance(v, str) for k, v in configs.items())):
        raise ValueError('missing or excessive configuration')
    # Includes and substitutions need a separately admitted resolution contract.
    # Reject them instead of allowing the formatter to read ambient host files.
    if any(re.search(r'(?i)\binclude\b|\$\{', value) for value in configs.values() if isinstance(value, str)):
        raise ValueError('configuration includes and substitutions are unavailable')
    # Until rewrite semantics have a typed admission, reject the entire rewrite
    # configuration, including fileOverride entries.
    if any(re.search(r'(?i)\brewrite\b', value) for value in configs.values()):
        raise ValueError('formatter rewrite configuration is not admitted')
    config = configs['.scalafmt.conf']
    if config.lstrip().startswith('{'):
        parsed = json.loads(config, object_pairs_hook=unique)
        versions = [parsed.get('version')] if isinstance(parsed, dict) else []
    else:
        versions = re.findall(r'(?m)^\s*version\s*[:=]\s*[\"\']?([^\s\"\']+)', config)
    if versions != ['3.8.6']:
        raise ValueError('configuration must select the fixed Scalafmt 3.8.6 engine')
    with tempfile.TemporaryDirectory(prefix='sm-scala-preview-', dir=Path.cwd()) as temp:
        root = Path(temp)
        for name, content in configs.items():
            target = relative(name)
            if not isinstance(content, str):
                raise ValueError('invalid configuration content')
            dest = root.joinpath(*target.parts)
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(content, encoding='utf-8')
        source_file = root.joinpath(*path.parts)
        if source_file.exists():
            raise ValueError('source conflicts with configuration input')
        source_file.parent.mkdir(parents=True, exist_ok=True)
        source_file.write_text(request['source'], encoding='utf-8')
        command = [args.java, '-cp', classpath, 'org.scalafmt.cli.Cli', '--non-interactive', '--stdout',
                   '--config', '.scalafmt.conf', '--respect-project-filters', str(path)]
        result = subprocess.run(command, cwd=root,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60, check=False)
        if source_file.read_bytes() != request['source'].encode('utf-8'):
            raise ValueError('formatter modified isolated source during preview')
        if len(result.stdout) > LIMIT or len(result.stderr) > 65536:
            raise ValueError('provider output exceeds bound')
        if result.returncode == 0 and request['source'] and not result.stdout:
            raise ValueError('formatter returned empty output for nonempty source')
        output = {'schemaVersion': 'sm-workflow.scalafmt-preview-output.v1', 'status': 'completed' if result.returncode == 0 else 'failed',
                  'path': str(path), 'source': result.stdout.decode('utf-8') if result.returncode == 0 else None,
                  'diagnostic': result.stderr.decode('utf-8', errors='replace')}
        sys.stdout.write(json.dumps(output, ensure_ascii=False, separators=(',', ':')))



def scalafix(request, java, classpath):
    semantic = isinstance(request, dict) and request.get('schemaVersion') == 'sm-workflow.scalafix-semantic-preview-input.v1'
    fields = {'schemaVersion', 'path', 'source', 'configFiles', 'rule', 'scalaVersion'} | ({'metadata'} if semantic else set())
    if (not isinstance(request, dict) or set(request) != fields
            or request['schemaVersion'] not in ('sm-workflow.scalafix-preview-input.v1', 'sm-workflow.scalafix-semantic-preview-input.v1')):
        raise ValueError('invalid Scalafix input envelope')
    path = relative(request['path'])
    rule = request['rule']
    version = request['scalaVersion']
    if (not str(path).endswith('.scala') or not isinstance(request['source'], str)
            or rule not in ('RedundantSyntax.finalObject', 'ProcedureSyntax', 'OrganizeImports')
            or version not in ('2.12.18', '3.3.8')
            or (semantic != (rule == 'OrganizeImports')) or (semantic and version != '3.3.8')):
        raise ValueError('unsupported Scalafix rule or Scala dialect')
    configs = request['configFiles']
    if not isinstance(configs, dict) or len(configs) > 32 or any(not isinstance(k, str) or not isinstance(v, str) for k, v in configs.items()):
        raise ValueError('invalid selected rule configuration')
    # Run only the attested rule and supported options. Other effective project
    # rules remain check-only diagnostics and never enter this process recipe.
    if semantic:
        formatted = configs.get('.scalafix.conf')
        if formatted is None:
            raise ValueError('effective OrganizeImports configuration is missing')
        effective = json.loads(formatted, object_pairs_hook=unique)
        selected = effective.get('OrganizeImports') if isinstance(effective, dict) else None
        if not isinstance(selected, dict) or selected.get('removeUnused') is not False:
            raise ValueError('OrganizeImports requires explicit removeUnused = false')
        config = json.dumps({'rules': ['OrganizeImports'], 'OrganizeImports': selected},
                            ensure_ascii=False, separators=(',', ':'))
    elif rule == 'RedundantSyntax.finalObject':
        config = ('rules = [RedundantSyntax]\nRedundantSyntax.finalObject = true\n'
                  'RedundantSyntax.stringInterpolator = false\n')
    else:
        config = 'rules = [ProcedureSyntax]\n'
    with tempfile.TemporaryDirectory(prefix='sm-scalafix-preview-', dir=Path.cwd()) as temp:
        root = Path(temp)
        source = root.joinpath(*path.parts)
        source.parent.mkdir(parents=True, exist_ok=True)
        original = request['source'].encode('utf-8')
        source.write_bytes(original)
        (root / '.scalafix.conf').write_text(config, encoding='utf-8')
        parser_version = '2.12.18' if rule == 'ProcedureSyntax' else version
        command = [java, '-cp', classpath, 'scalafix.cli.Cli', '--stdout', '--scala-version', parser_version,
                   '--config', '.scalafix.conf', '--files', str(path)]
        if semantic:
            try:
                metadata = base64.b64decode(request['metadata'], validate=True)
            except (ValueError, TypeError, binascii.Error):
                raise ValueError('invalid SemanticDB input')
            if len(metadata) > LIMIT:
                raise ValueError('SemanticDB input exceeds bound')
            classes = root / 'classes'
            classes.mkdir()
            if metadata:
                destination = classes / 'META-INF' / 'semanticdb' / (str(path) + '.semanticdb')
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(metadata)
            command.extend(['--sourceroot', str(root), '--semanticdb-targetroots', str(classes)])
        result = subprocess.run(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60, check=False)
        if source.read_bytes() != original:
            raise ValueError('Scalafix modified isolated source during preview')
        if len(result.stdout) > LIMIT or len(result.stderr) > 65536:
            raise ValueError('provider output exceeds bound')
        # Scalafix 0.12.1 --stdout appends exactly one framing LF.
        if result.returncode == 0 and not result.stdout.endswith(b'\n'):
            raise ValueError('Scalafix preview framing is absent')
        error = result.stderr.decode('utf-8', errors='replace')
        needs_metadata = semantic and ((result.returncode == 16 and 'error: Stale SemanticDB' in error) or
                                       (result.returncode == 8 and 'error: SemanticDB not found:' in error))
        output = {'schemaVersion': 'sm-workflow.scalafix-preview-output.v1',
                  'status': 'completed' if result.returncode == 0 else 'metadata-required' if needs_metadata else 'failed', 'path': str(path),
                  'rule': rule, 'source': result.stdout[:-1].decode('utf-8') if result.returncode == 0 else None,
                  'diagnostic': error}
        if semantic:
            output['exitCode'] = result.returncode
        sys.stdout.write(json.dumps(output, ensure_ascii=False, separators=(',', ':')))


def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('duplicate JSON key')
        result[key] = value
    return result


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.TimeoutExpired, UnicodeError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
