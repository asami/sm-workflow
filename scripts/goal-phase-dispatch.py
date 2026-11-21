#!/usr/bin/env -S uv run --no-cache --managed-python --no-python-downloads --script
# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Portable caller adapter. No provider discovery, workflow commands or writes.

prepare emits exact collaboration.spawn_agent arguments. The caller invokes that
ordinary tool and records its actual return with launched/complete. dispatch is
also usable with an injected supported launch callback; it performs one launch,
never a model probe. Retain the returned state unchanged across resume.
"""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

if (sys.implementation.name != 'cpython' or sys.version_info < (3, 12)
        or sys.version_info.releaselevel != 'final'):
    raise RuntimeError('ENVIRONMENT_PRECONDITION_ERROR: stable CPython >=3.12 is required; '
                       'use uv run --no-cache --managed-python --no-python-downloads --script')

import tomllib

SCHEMA = 'sm-workflow.caller-dispatch.v1'
REVISION = 'standard-2026-11-01'
REVIEW_PACKAGE_MAX = 512 * 1024

REVIEW_RESULT_INSTRUCTIONS = (
    'Return GoalPhaseWorkResult JSON with outcome, summary and findings. Use accept only with empty findings '
    'and no relevant unresolved issues; repair requires findings; decision-needed identifies unresolved decisions. '
    'Apply the issued acceptance and instructions; report each applicable required condition in acceptance. '
    'For every relevant REVIEW issue, issueReport.dispositions must contain issueIdentity, disposition and rationale: '
    'RESOLVED, RECURRING or NOT_REVIEWED. RECURRING requires a unique 1-based findingOrdinal; other dispositions '
    'omit it. Report relevant non-REVIEW issues when needed to resolve them. Do not claim unobserved resolution. '
    'Only an issued ReviewStep purposeRepair contract permits original-purpose repair findings: repairFindingSources '
    'is {sources:[...]} with one 1-based findingOrdinal per finding; required-condition uses conditionIdentity; '
    'step-purpose uses stepIdentity, rationale and affectedPaths. Cover every required failure. '
    'reviewObservations uses {items:[{identity,category,summary,rationale}]}. '
    'Only a ReviewStep repair whose sole remaining findings are terminal-LF corrections may use '
    'completionConditions {kind:"terminal-lf",paths:[...]}, one owned path per finding. '
    'Only ReviewPhase decision-needed with findings may propose planRevision {identity,revision,reason,plan}; '
    'a review may instead request dependencyProposal for a decision, never both proposals. '
    'Omit inapplicable optional fields instead of null, and identify evidence gaps.\n'
)


def compact_issues(value):
    value = copy.deepcopy(value)
    if value is None:
        return None
    if not isinstance(value, dict) or not isinstance(value.get('issues'), list):
        raise ValueError('Malformed issue context')
    for issue in value['issues']:
        summary = issue.get('summary')
        if isinstance(summary, str) and len(summary) > 1024:
            ref = alias(issue, 'evidenceReference', 'evidence_reference')
            if not isinstance(ref, str) or not ref:
                raise ValueError('Long issue diagnostic requires its retained evidence reference')
            issue['summary'] = '[Diagnostic retained at evidenceReference]'
    return value


def check_facts(checks):
    keys = ('identity', 'termination', 'exitCode', 'sbt')
    result = []
    for check in checks:
        if not isinstance(check, dict) or not isinstance(check.get('identity'), str) or not check['identity']:
            raise ValueError('Malformed issued check')
        result.append({k: copy.deepcopy(alias(check, k, 'exit_code') if k == 'exitCode' else check.get(k))
                       for k in keys})
    return result


def review_work_resource(original, request):
    reference = (original.get('reference') if original is not request else
                 alias(request, 'retainedWork', 'retained_work'))
    resource = reference.get('resource') if isinstance(reference, dict) else None
    if not isinstance(resource, str) or not resource.startswith('sm-workflow-work:'):
        raise ValueError('Supplied review requires the retained work Resource')
    return resource


def review_package(request, files, complete, completeness_source, max_bytes=REVIEW_PACKAGE_MAX):
    """Create the exact supplied review input; completeness is a caller attestation.

    The byte limit applies to the canonical UTF-8 package, independently of any
    provider token, output, capability or cost policy. No content is truncated.
    """
    original = request
    request = selected_request(request)
    if not isinstance(request, dict) or request.get('kind') != 'Request':
        raise ValueError('An actual issued review Request is required')
    work = request.get('work')
    issued = alias(request, 'issuedWorkContext', 'issued_work_context')
    if not isinstance(work, dict) or work.get('kind') not in ('ReviewStep', 'ReviewPhase') or not isinstance(issued, dict):
        raise ValueError('Supplied package requires issued review work')
    result_type = alias(request, 'resultType', 'result_type')
    if (result_type != 'GoalPhaseWorkResult' or
            alias(issued, 'resultType', 'result_type') != result_type):
        raise ValueError('Supplied review requires the actual issued result type')
    requirement = alias(issued, 'executionRequirement', 'execution_requirement') or {}
    if not isinstance(requirement, dict):
        raise ValueError('Malformed review execution requirement')
    disposition = requirement.get('disposition', {})
    if (not isinstance(disposition, dict) or disposition.get('placement') != 'DELEGATED' or disposition.get('independence') != 'REQUIRED'
            or not alias(disposition, 'originContextIdentity', 'origin_context_identity')
            or not alias(issued, 'continuationId', 'continuation_id')):
        raise ValueError('Supplied review requires a distinct delegated independent continuation')
    candidate, review = work.get('candidate'), work.get('review')
    if (not isinstance(candidate, dict) or not all(isinstance(candidate.get(k), str) and candidate[k] for k in ('head', 'tree'))
            or not isinstance(review, dict) or not isinstance(review.get('diff'), str) or not review['diff']
            or not isinstance(review.get('checks'), list) or not review['checks']):
        raise ValueError('Issued review must contain the actual candidate, diff and checks')
    if not isinstance(max_bytes, int) or isinstance(max_bytes, bool) or not 0 < max_bytes <= REVIEW_PACKAGE_MAX:
        raise ValueError('Package limit must be between 1 and 512 KiB')
    if complete is not True or not isinstance(completeness_source, str) or not completeness_source.strip():
        return dict(status='DECLINED', reason='completeness-unconfirmed')
    if (not isinstance(files, list) or not files or any(
            not isinstance(f, dict) or set(f) != {'name', 'content'} or
            not isinstance(f['name'], str) or not f['name'].strip() or
            not isinstance(f['content'], str) for f in files)
            or len({f['name'] for f in files}) != len(files)):
        return dict(status='DECLINED', reason='missing-or-ambiguous-supplied-files')
    receipt = alias(work, 'checksReceipt', 'checks_receipt')
    if not isinstance(receipt, str) or not receipt:
        raise ValueError('Issued checks require retained receipt reference')
    plan = alias(work, 'selectedPlan', 'selected_plan') or {}
    context = issued.get('context') or {}
    contract = dict(selectedPlan=alias(work, 'selectedPlan', 'selected_plan'), stepIdentity=alias(work, 'stepIdentity', 'step_identity'),
                    effectiveAcceptance=alias(plan, 'effectiveAcceptance', 'effective_acceptance'),
                    acceptance=plan.get('acceptance'), purposeRepair=alias(work, 'purposeRepair', 'purpose_repair'),
                    issues=compact_issues(work.get('issues')), repairDirective=alias(work, 'repairDirective', 'repair_directive'),
                    releaseRepairContract=alias(work, 'releaseRepairContract', 'release_repair_contract'),
                    instructions=context.get('summary'), requiredFacts=alias(context, 'requiredFacts', 'required_facts'),
                    completion=issued.get('completion'), evidence=issued.get('evidence'))
    review_context = dict(metadata=review.get('metadata', []),
                          priorReviews=alias(review, 'priorReviews', 'prior_reviews') or [],
                          dependencyValidations=alias(review, 'dependencyValidations', 'dependency_validations') or [],
                          triageJudgment=alias(review, 'triageJudgment', 'triage_judgment'),
                          qualification=review.get('qualification'))
    packet = dict(schema='sm-workflow.supplied-review.v1',
                  workResource=review_work_resource(original, request),
                  continuationId=alias(issued, 'continuationId', 'continuation_id'),
                  resultType=result_type,
                  kind=work['kind'], purpose=work.get('purpose'),
                  candidate={k: candidate[k] for k in ('head', 'tree')},
                  contract=contract, reviewContext=review_context, checksReceipt=receipt,
                  review=dict(diff=review['diff'], checks=check_facts(review['checks']),
                              changedPaths=copy.deepcopy(alias(review, 'changedPaths', 'changed_paths'))),
                  files=copy.deepcopy(files), completenessSource=completeness_source)
    encoded = json.dumps(packet, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')
    if len(encoded) > max_bytes:
        return dict(status='DECLINED', reason='package-too-large', bytes=len(encoded), limit=max_bytes)
    return dict(status='READY', packet=packet, bytes=len(encoded), limit=max_bytes)


def assemble_review_package(request, repository, paths, excluded, complete, completeness_source,
                            max_bytes=REVIEW_PACKAGE_MAX):
    """Read explicit regular files from the issued Git candidate, never the worktree.

    Provenance and exclusions are local evidence, outside the v1 provider packet.
    Completeness remains an explicit caller judgment, not an inferred guarantee.
    """
    if complete is not True or not isinstance(completeness_source, str) or not completeness_source.strip():
        return dict(status='DECLINED', reason='completeness-unconfirmed')
    def valid_path(path):
        return (isinstance(path, str) and path and not path.startswith('/') and
                '\\' not in path and ':' not in path and not any(ord(c) < 32 or ord(c) == 127 for c in path) and
                all(part not in ('', '.', '..') for part in path.split('/')))
    if (not isinstance(paths, list) or not paths or len(paths) > 128 or not all(valid_path(p) for p in paths)
            or len(set(paths)) != len(paths) or not isinstance(excluded, list)
            or len(excluded) > 128
            or any(not isinstance(e, dict) or set(e) != {'name', 'reason'} or
                   not valid_path(e['name']) or not isinstance(e['reason'], str) or not e['reason'].strip()
                   for e in excluded) or len({e['name'] for e in excluded}) != len(excluded)
            or set(paths) & {e['name'] for e in excluded}):
        return dict(status='DECLINED', reason='invalid-resource-inventory')
    if not isinstance(max_bytes, int) or isinstance(max_bytes, bool) or not 0 < max_bytes <= REVIEW_PACKAGE_MAX:
        raise ValueError('Package limit must be between 1 and 512 KiB')
    selected = selected_request(request)
    candidate = selected.get('work', {}).get('candidate', {})
    head, tree = candidate.get('head'), candidate.get('tree')
    if not all(isinstance(x, str) and re.fullmatch(r'(?:[0-9a-f]{40}|[0-9a-f]{64})', x) for x in (head, tree)):
        return dict(status='DECLINED', reason='candidate-identity-unavailable')
    def git(*args):
        env = {k: v for k, v in os.environ.items() if not k.startswith('GIT_')}
        env.update(GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull, GIT_OPTIONAL_LOCKS='0',
                   GIT_NO_REPLACE_OBJECTS='1', GIT_NO_LAZY_FETCH='1')
        return subprocess.run(['git', '--literal-pathspecs', '-C', str(repository), *args],
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True, timeout=10, env=env).stdout
    try:
        if Path(git('rev-parse', '--show-toplevel').decode('utf-8').strip()).resolve() != Path(repository).resolve():
            return dict(status='DECLINED', reason='repository-root-mismatch')
        if git('cat-file', '-t', head).strip() != b'commit' or git('cat-file', '-t', tree).strip() != b'tree':
            return dict(status='DECLINED', reason='candidate-object-mismatch')
        files, provenance, total = [], [], 0
        for path in sorted(paths):
            entry = git('ls-tree', '-z', tree, '--', path)
            match = re.fullmatch(rb'(100644|100755) blob ([0-9a-f]{40}|[0-9a-f]{64})\t([^\x00]+)\x00', entry)
            if not match or match.group(3) != path.encode('utf-8'):
                return dict(status='DECLINED', reason='candidate-file-unavailable', path=path)
            blob = match.group(2).decode('ascii')
            size = int(git('cat-file', '-s', blob).strip())
            total += size
            if total > max_bytes:
                return dict(status='DECLINED', reason='files-too-large', limit=max_bytes)
            content = git('cat-file', 'blob', blob)
            if len(content) != size:
                return dict(status='DECLINED', reason='candidate-file-unavailable', path=path)
            files.append(dict(name=path, content=content.decode('utf-8')))
            provenance.append(dict(name=path, blob=blob, bytes=size, sha256=hashlib.sha256(content).hexdigest()))
    except (OSError, subprocess.SubprocessError, UnicodeError, ValueError):
        return dict(status='DECLINED', reason='candidate-read-failed')
    package = review_package(request, files, complete, completeness_source, max_bytes)
    if package['status'] != 'READY':
        return package
    return dict(status='READY', suppliedReview=package,
                provenance=dict(candidate=dict(head=head, tree=tree), files=provenance,
                                excluded=sorted(copy.deepcopy(excluded), key=lambda e: e['name']),
                                completenessSource=completeness_source),
                estimate=dict(packageBytes=package['bytes'], tokenEstimate=package['bytes'],
                              tokenEstimateBasis='one token per UTF-8 byte heuristic; model tokenizer unverified'))


def alias(value, camel, snake):
    if camel in value and snake in value and value[camel] != value[snake]:
        raise ValueError('Contradictory aliases: ' + camel)
    return value.get(camel, value.get(snake))


def selected_request(value):
    """Read the exact Request selected by an explicit native detail response.

    No provider call or workflow command is added to the dispatch adapter.
    """
    if not isinstance(value, dict) or 'availability' not in value or 'response' not in value:
        return value
    reference, request = value.get('reference'), value.get('response')
    if value.get('availability') != 'available' or not isinstance(reference, dict) or not isinstance(request, dict):
        raise ValueError('Selected native work detail is unavailable')
    issued = alias(request, 'issuedWorkContext', 'issued_work_context')
    if (request.get('kind') != 'Request' or not isinstance(issued, dict)
            or alias(reference, 'schemaVersion', 'schema_version') != 'sm-workflow.retained-work-reference.v1'
            or alias(request, 'submitSessionId', 'submit_session_id') != alias(reference, 'sessionId', 'session_id')
            or alias(issued, 'continuationId', 'continuation_id') != reference.get('identity')
            or alias(issued, 'expectedRevision', 'expected_revision') != reference.get('revision')):
        raise ValueError('Selected native detail crosses its issued identity, revision or Session')
    return request


def prepare(request, mapping, capabilities, root=None, repository=None, request_artifact=None, supplied_review=None):
    """Capabilities are caller-exposed facts, not guessed from this table."""
    original = request
    request = selected_request(request)
    issued = request.get('issued_work_context', request.get('issuedWorkContext'))
    if request.get('kind') != 'Request' or not isinstance(issued, dict):
        raise ValueError('An actual issued Request is required')
    work = request.get('work')
    if not isinstance(work, dict):
        raise ValueError('Compact control requires one explicit read-work/detail read before semantic dispatch')
    capture = alias(work, 'operatingProfile', 'operating_profile')
    if not capture or not capture.get('dispatch'):
        raise ValueError('No retained new dispatch policy; use the original legacy route')
    policy = capture['dispatch']
    requirement = alias(issued, 'executionRequirement', 'execution_requirement')
    disposition = requirement.get('disposition', {})
    if policy['revision'] != REVISION or mapping['revision'] != policy['revision']:
        raise ValueError('Use the exact captured versioned caller mapping')
    if disposition.get('placement') != policy['placement']:
        raise ValueError('Logical policy contradicts issued disposition')
    if policy['placement'] == 'INLINE':
        if supplied_review is not None:
            raise ValueError('Supplied review requires delegated independent execution')
        if disposition.get('independence') == 'REQUIRED' or alias(requirement, 'reviewRequired', 'review_required'):
            raise ValueError('Independent work cannot run inline')
        return dict(schema=SCHEMA, request=copy.deepcopy(request), mapping=copy.deepcopy(mapping),
                    policy=policy, status='INLINE', attempts=[], launch=None)
    if policy['placement'] != 'DELEGATED':
        raise ValueError('Deterministic work stays with its runtime owner')
    # The public Record projection spells the known role key sol-medium as
    # sol_medium. Decode only supported field aliases for traversal; retain the
    # unchanged Request/policy, including opaque values and removed edges.
    raw_edges = policy.get('edges')
    if not isinstance(raw_edges, dict):
        raise ValueError('Captured logical edges must be an object')
    edges = {}
    for name, target in raw_edges.items():
        canonical = {'sol_medium': 'sol-medium', 'solMedium': 'sol-medium'}.get(name, name)
        if canonical in edges:
            raise ValueError('Duplicate or contradictory logical role key aliases')
        edges[canonical] = target
    if any(mapping['edges'].get(a) != b for a, b in edges.items()):
        raise ValueError('Caller mapping edges contradict the captured logical policy')
    candidates, current = [], policy['role']
    while current is not None:
        if current in candidates or len(candidates) >= 16:
            raise ValueError('Fallback policy is cyclic or unbounded')
        candidates.append(current)
        current = edges.get(current)
    if candidates != policy['candidates']:
        raise ValueError('Caller mapping contradicts retained finite policy')
    # Every mapping is checked before considering availability. Invalid config
    # cannot masquerade as model absence or partially dispatch valid prefixes.
    for candidate in candidates:
        concrete = mapping['assignments'][candidate]
        if set(concrete) != {'model', 'effort'} or not all(isinstance(x, str) and x for x in concrete.values()):
            raise ValueError('Malformed concrete model/effort assignment')
    role = {'ImplementStep': 'implementation', 'RepairStep': 'repair',
            'ReviewStep': 'review', 'ReviewPhase': 'review', 'TriageStep': 'analysis',
            'Implement': 'implementation', 'Repair': 'repair', 'Review': 'review',
            'DeliveryAcceptance': 'implementation'}.get(work['kind'])
    if role is None:
        raise ValueError('Unsupported semantic role')
    if supplied_review is not None:
        if role != 'review' or not isinstance(supplied_review, dict):
            raise ValueError('Supplied package is only available to review')
        packet = supplied_review.get('packet', {})
        if not isinstance(packet, dict):
            raise ValueError('Malformed supplied review package')
        verified = review_package(original, packet.get('files'), True, packet.get('completenessSource'),
                                  supplied_review.get('limit'))
        if verified != supplied_review:
            raise ValueError('Supplied package differs from its issued review or measured bytes')
    root = Path(root) if root else Path(__file__).resolve().parents[1]
    if repository is None:
        raise ValueError('Caller must supply the admitted working repository path')
    repository = str(Path(repository).resolve())
    artifact = None
    if request_artifact is not None:
        supplied = Path(request_artifact)
        if not supplied.is_absolute() or not supplied.is_file() or supplied.is_symlink():
            raise ValueError('Use the exact caller-retained absolute public Request artifact')
        if selected_request(json.loads(supplied.read_text())) != request:
            raise ValueError('Public Request artifact differs from the unchanged issued Request')
        artifact = str(supplied)
    contract = tomllib.loads((root / 'agents' / ('sm_workflow_' + role + '.toml')).read_text())['developer_instructions']
    if supplied_review is None and artifact is None and len(json.dumps(request, ensure_ascii=False)) > 131072:
        raise ValueError('Large issued Requests require their verified caller-retained request_artifact')
    state = dict(schema=SCHEMA, request=copy.deepcopy(request), mapping=copy.deepcopy(mapping),
                 policy=copy.deepcopy(policy), role=role, contract=contract, repository=repository,
                 requestArtifact=artifact, attempts=[], status='READY', launch=None)
    if supplied_review is not None:
        state['suppliedReview'] = copy.deepcopy(supplied_review)
    return resolve(state, capabilities)


def resolve(state, capabilities):
    """Called before launch, or after a typed confirmed-unstarted rejection only."""
    state = copy.deepcopy(state)
    if state['status'] != 'READY' or state.get('launch') is not None:
        raise ValueError('Started/uncertain work cannot be resolved again')
    candidates = state['policy']['candidates']
    for candidate in candidates[len(state['attempts']):]:
        concrete = state['mapping']['assignments'][candidate]
        facts = [c for c in capabilities if c.get('model') == concrete['model'] and c.get('effort') == concrete['effort']]
        if len(facts) != 1 or facts[0].get('availability') not in ('available', 'unavailable') or not facts[0].get('source'):
            state['status'] = 'CAPABILITY_UNKNOWN'
            state['detail'] = 'Missing unambiguous caller capability evidence; no inferred absence or launch'
            return state
        fact = facts[0]
        if fact['availability'] == 'unavailable':
            state['attempts'].append(dict(candidate=candidate, concrete=concrete, status='UNAVAILABLE',
                                          executionStarted=False, reason=fact.get('reason', 'caller-exposed unavailable'), source=fact['source']))
            continue
        issued = state['request'].get('issued_work_context', state['request'].get('issuedWorkContext'))
        continuation = alias(issued, 'continuationId', 'continuation_id')
        if not continuation:
            raise ValueError('Missing native continuation identity')
        state['resolved'] = dict(candidate=candidate, concrete=concrete, source=fact['source'])
        if state.get('suppliedReview'):
            input_message = ('Review only the complete supplied package below. Do not read the repository, '
                             'other files, tools, conversation history or external sources. Do not edit or execute commands. ' +
                             REVIEW_RESULT_INSTRUCTIONS +
                             json.dumps(state['suppliedReview']['packet'], ensure_ascii=False, sort_keys=True, separators=(',', ':')))
        elif state.get('requestArtifact'):
            input_message = ('Read the exact caller-retained public Request artifact: ' + state['requestArtifact'] +
                             '\nFor an explicit native detail response, response is the selected original Request. '
                             'Preserve that unchanged Request, its issued context and return contract. '
                             'Read only the scope and evidence needed for this assignment; no private workflow storage.')
        else:
            input_message = ('Actual issued Request (unchanged):\n' + json.dumps(state['request'], ensure_ascii=False))
        instructions = state['contract'] if not state.get('suppliedReview') else (
            'Perform an independent read-only review using only the supplied package. '
            'Do not invoke tools, access repository or network, mutate anything, or submit the result. '
            'Return the typed semantic result required by the unchanged issued Request.')
        state['launch'] = dict(agent_type='default', fork_turns='none',
            task_name='sm_' + state['role'] + '_' + hashlib.sha256(continuation.encode('utf-8')).hexdigest()[:12],
            model=concrete['model'], reasoning_effort=concrete['effort'],
            message=instructions + ('\n\n' if state.get('suppliedReview') else
                                   '\n\nAdmitted working repository: ' + state['repository'] + '\n\n') + input_message)
        state['status'] = 'PREPARED'
        return state
    state['status'] = 'UNAVAILABLE'
    state['detail'] = 'Captured finite chain exhausted before execution'
    return state


def launch_identity(result):
    if not isinstance(result, dict):
        raise ValueError('The actual supported launch tool return is required')
    identities = [result[k] for k in ('task_name', 'agent_id') if k in result]
    if not identities or not all(isinstance(x, str) and x.strip() == x and x for x in identities) or len(set(identities)) != 1:
        raise ValueError('Missing or contradictory actual task_name/agent_id launch identities')
    return identities[0]


def launched(state, arguments, result):
    """Bind the actual successful start tool return. Provider identity stays unknown."""
    if state['status'] != 'PREPARED' or arguments != state['launch']:
        raise ValueError('Actual launch arguments and returned agent identity are required')
    identity = launch_identity(result)
    if state.get('suppliedReview'):
        issued = alias(state['request'], 'issuedWorkContext', 'issued_work_context')
        disposition = alias(issued, 'executionRequirement', 'execution_requirement')['disposition']
        if identity == alias(disposition, 'originContextIdentity', 'origin_context_identity'):
            raise ValueError('Independent review requires a distinct actual executor context')
    state = copy.deepcopy(state)
    state['status'] = 'STARTED'
    state['launchReturn'] = copy.deepcopy(result)
    state['executorIdentity'] = identity
    state['observed'] = None
    return state


def rejected(state, arguments, rejection, capabilities):
    """Only an explicit model/effort absence proving no execution permits the next edge."""
    if state['status'] != 'PREPARED' or arguments != state['launch']:
        raise ValueError('Rejection must bind the prepared launch')
    state = copy.deepcopy(state)
    state['rejection'] = copy.deepcopy(rejection)
    if rejection.get('kind') != 'model-effort-unavailable' or rejection.get('executionStarted') is not False or not rejection.get('source'):
        state['status'] = 'STOPPED'
        return state
    state['attempts'].append(dict(candidate=state['resolved']['candidate'], concrete=state['resolved']['concrete'],
                                  status='UNAVAILABLE', executionStarted=False, reason=rejection))
    state['launch'] = None
    state['status'] = 'READY'
    return resolve(state, capabilities)


PUBLIC_RETURN_SCHEMA = 'sm-workflow.public-worker-return.v1'
PUBLIC_RESULT_TYPES = {'GoalPhaseWorkResult', 'GoalTaskWorkResult'}


def project_result(request, value, work_resource=None):
    """Select one public worker return; the native public reader remains authoritative."""
    if not isinstance(request, dict) or request.get('kind') != 'Request' or not isinstance(value, dict):
        raise ValueError('An issued Request and public result object are required')
    def projection_field(value, camel, snake):
        if camel in value and snake in value:
            raise ValueError('Ambiguous projection field aliases: ' + camel)
        return value.get(camel, value.get(snake))

    issued = projection_field(request, 'issuedWorkContext', 'issued_work_context')
    result_type = projection_field(request, 'resultType', 'result_type')
    if result_type is not None and result_type not in PUBLIC_RESULT_TYPES:
        raise ValueError('Unsupported result type')
    if isinstance(issued, dict):
        context_type = projection_field(issued, 'resultType', 'result_type')
        if context_type is not None and context_type != result_type:
            raise ValueError('Issued context result type differs from Request')
    if 'resultResource' in value:
        if (set(value) != {'outcome', 'summary', 'resultType', 'resultResource'}
                or result_type not in PUBLIC_RESULT_TYPES or value['resultType'] != result_type
                or not all(isinstance(value.get(k), str) and value[k].strip() for k in ('outcome', 'summary', 'resultResource'))
                or not value['resultResource'].startswith('file:')):
            raise ValueError('Compact worker result requires its actual typed result Resource')
        return copy.deepcopy(value)
    envelope_keys = {'schemaVersion', 'schema_version', 'payload', 'resultType', 'result_type',
                     'issuedWorkContext', 'issued_work_context', 'workResource', 'work_resource', 'result'}
    if set(value) & envelope_keys:
        if set(value) != {'schemaVersion', 'resultType', 'workResource', 'result'}:
            raise ValueError('Unsupported or ambiguous worker return envelope')
        retained = projection_field(request, 'retainedWork', 'retained_work')
        request_resource = retained.get('resource') if isinstance(retained, dict) else None
        if work_resource is not None and request_resource is not None and work_resource != request_resource:
            raise ValueError('Selected work Resource differs from Request')
        selected_resource = work_resource if work_resource is not None else request_resource
        if (value['schemaVersion'] != PUBLIC_RETURN_SCHEMA or result_type not in PUBLIC_RESULT_TYPES
                or not isinstance(issued, dict) or not issued or context_type != result_type
                or value['resultType'] != result_type
                or not isinstance(selected_resource, str) or not selected_resource.startswith('sm-workflow-work:')
                or value['workResource'] != selected_resource):
            raise ValueError('Worker return schema, type or issued context differs')
        value = value['result']
    if (not isinstance(value, dict) or not isinstance(value.get('outcome'), str)
            or not value['outcome'].strip() or 'dependencyAdmission' in value
            or 'dependency_admission' in value or set(value) & envelope_keys):
        raise ValueError('The complete public semantic result is required')
    return copy.deepcopy(value)



def returned_result(request, value):
    """Apply the declared Goal result projection without narrowing other workflows."""
    result_type = alias(request, 'resultType', 'result_type')
    if result_type is None or result_type in PUBLIC_RESULT_TYPES:
        return project_result(request, value)
    if not isinstance(value, dict):
        raise ValueError('The actual typed semantic payload is required')
    return copy.deepcopy(value)


def complete(state, agent_id, semantic_result, observed=None, resources=None):
    if state['status'] not in ('STARTED', 'UNCERTAIN') or launch_identity(state['launchReturn']) != agent_id:
        raise ValueError('Only the actual launched agent can supply its return')
    semantic_result = returned_result(state['request'], semantic_result)
    state = copy.deepcopy(state)
    state.update(status='RETURNED', semanticResult=copy.deepcopy(semantic_result), observed=observed, resources=resources)
    selected = state['resolved']['concrete']
    state['selectionMismatch'] = bool(observed and any(observed.get(k) is not None and observed[k] != selected[k]
                                                       for k in ('model', 'effort')))
    # A semantic decision-needed/DECLINED is a returned result, never availability.
    return state


def uncertain(state, agent_id, reason):
    """Retain a lost response after a real start; it cannot select a fallback."""
    if (state.get('status') != 'STARTED' or launch_identity(state.get('launchReturn')) != agent_id
            or not isinstance(reason, str) or not reason.strip()):
        raise ValueError('Uncertainty requires the actual started agent and a reason')
    state = copy.deepcopy(state)
    state.update(status='UNCERTAIN', uncertainty=reason)
    return state


def api_prepare(request, supplied_review, work_resource, attempt_identity, package_path):
    """Prepare one explicit API operation; retaining this state is caller-owned."""
    original = request
    request = selected_request(request)
    if not isinstance(supplied_review, dict) or not isinstance(supplied_review.get('packet'), dict):
        raise ValueError('A ready supplied review package is required')
    packet = supplied_review['packet']
    if review_package(original, packet.get('files'), True, packet.get('completenessSource'),
                      supplied_review.get('limit')) != supplied_review:
        raise ValueError('Supplied package differs from issued review')
    if (work_resource != packet['workResource'] or not isinstance(attempt_identity, str)
            or not attempt_identity.strip() or attempt_identity.strip() != attempt_identity):
        raise ValueError('Actual work resource and explicit attempt identity are required')
    package_json = json.dumps(packet, ensure_ascii=False, sort_keys=True, separators=(',', ':'))
    path = Path(package_path)
    if not path.is_absolute() or path.read_bytes() != package_json.encode('utf-8'):
        raise ValueError('Retained package file must match canonical package bytes')
    issued = alias(request, 'issuedWorkContext', 'issued_work_context')
    session = alias(issued, 'runId', 'run_id')
    if not isinstance(session, str) or not session.strip():
        raise ValueError('Issued review session identity is required')
    return dict(schema='sm-workflow.api-review-attempt.v1', status='PREPARED', request=copy.deepcopy(request),
                suppliedReview=copy.deepcopy(supplied_review), operation='GoalPhaseService.runGoalPhaseApiReview',
                arguments=dict(input=dict(sessionId=session, workResource=work_resource,
                                          attemptIdentity=attempt_identity, packageResource=path.as_uri())))


def api_started(state, arguments, invocation_identity):
    if (state.get('status') != 'PREPARED' or arguments != state.get('arguments') or
            not isinstance(invocation_identity, str) or not invocation_identity.strip()):
        raise ValueError('The actual API start and prepared arguments are required')
    state = copy.deepcopy(state)
    state.update(status='STARTED', invocationIdentity=invocation_identity)
    return state


def api_complete(state, invocation_identity, response):
    if (state.get('status') not in ('STARTED', 'UNCERTAIN') or state.get('invocationIdentity') != invocation_identity
            or not isinstance(response, dict)):
        raise ValueError('Only the actual API invocation may return')
    attempt = state['arguments']['input']['attemptIdentity']
    packet = state['suppliedReview']['packet']
    if (alias(response, 'attemptIdentity', 'attempt_identity') != attempt or
            alias(response, 'continuationId', 'continuation_id') != packet['continuationId'] or
            response.get('status') not in ('COMPLETED', 'DECLINED', 'UNAVAILABLE', 'FAILED', 'BLOCKED', 'UNCERTAIN')):
        raise ValueError('API response crosses the issued attempt or continuation')
    if response['status'] == 'BLOCKED' and response.get('message') not in ('ai.input-budget', 'ai.cost-budget'):
        raise ValueError('BLOCKED requires an attributable policy reason')
    if response['status'] == 'COMPLETED':
        project_result(state['request'], response.get('result'))
    state = copy.deepcopy(state)
    state.update(status=response['status'] if response['status'] in ('UNCERTAIN', 'BLOCKED') else 'RETURNED',
                 response=copy.deepcopy(response))
    return state


def api_uncertain(state, invocation_identity, reason):
    if (state.get('status') != 'STARTED' or state.get('invocationIdentity') != invocation_identity
            or not isinstance(reason, str) or not reason.strip()):
        raise ValueError('Uncertainty requires the actual started invocation')
    state = copy.deepcopy(state)
    state.update(status='UNCERTAIN', uncertainty=reason)
    return state


def api_execution_evidence(state, references):
    """Project only actual completed invocation and caller-retained references."""
    if (state.get('status') != 'RETURNED' or state.get('response', {}).get('status') != 'COMPLETED'
            or not references or any(not isinstance(ref, dict) or set(ref) != {'identity', 'revision'} or
                                     not all(isinstance(v, str) and v for v in ref.values()) for ref in references)):
        raise ValueError('Completed API review and actual retained references are required')
    request = state['request']
    issued = alias(request, 'issuedWorkContext', 'issued_work_context')
    disposition = alias(issued, 'executionRequirement', 'execution_requirement')['disposition']
    origin = alias(disposition, 'originContextIdentity', 'origin_context_identity')
    invocation = state.get('invocationIdentity')
    capture = alias(request['work'], 'operatingProfile', 'operating_profile')
    profile = capture.get('dispatch', {}).get('role') if isinstance(capture, dict) else None
    if not all(isinstance(v, str) and v for v in (origin, invocation, profile)) or invocation == origin:
        raise ValueError('Actual distinct invocation, origin and captured profile are required')
    project_result(request, state['response'].get('result'))
    evidence = dict(references=copy.deepcopy(references), workerIdentity=invocation,
                    execution=dict(placement=disposition['placement'], originContextIdentity=origin,
                                   executorContextIdentity=invocation, profileIdentity=profile))
    model = state['response'].get('model')
    if isinstance(model, str) and model:
        evidence['modelIdentity'] = model
    return evidence


def inline_complete(state, origin_context, semantic_result, observed=None, resources=None):
    issued = state['request'].get('issued_work_context', state['request'].get('issuedWorkContext'))
    requirement = alias(issued, 'executionRequirement', 'execution_requirement')
    origin = alias(requirement['disposition'], 'originContextIdentity', 'origin_context_identity')
    if state['status'] != 'INLINE' or origin_context != origin:
        raise ValueError('INLINE return requires the actual issued parent context and semantic payload')
    semantic_result = returned_result(state['request'], semantic_result)
    state = copy.deepcopy(state)
    state.update(status='RETURNED', semanticResult=copy.deepcopy(semantic_result), observed=observed,
                 resources=resources, inlineContext=origin_context)
    return state


def execution_evidence(state, references):
    """Ordinary host evidence fields; references must name actual retained artifacts.

    This conversion grants no Submit acceptance or independent evidence authority.
    Model identity is omitted unless separately observed by the provider.
    """
    if state['status'] != 'RETURNED' or state.get('selectionMismatch') or not references or any(
            set(ref) != {'identity', 'revision'} or not all(isinstance(v, str) and v for v in ref.values())
            for ref in references):
        raise ValueError('Actual returned work and retained artifact references are required')
    issued = state['request'].get('issued_work_context', state['request'].get('issuedWorkContext'))
    requirement = alias(issued, 'executionRequirement', 'execution_requirement')
    disposition = requirement['disposition']
    worker = state.get('inlineContext') or launch_identity(state['launchReturn'])
    origin = alias(disposition, 'originContextIdentity', 'origin_context_identity')
    if not origin:
        raise ValueError('Issued origin context is required')
    evidence = dict(references=copy.deepcopy(references), workerIdentity=worker,
        execution=dict(placement=disposition['placement'], originContextIdentity=origin,
                       executorContextIdentity=worker, profileIdentity=state['policy']['role'] if state.get('inlineContext')
                       else state['resolved']['candidate']))
    if state.get('observed') and state['observed'].get('model'):
        evidence['modelIdentity'] = state['observed']['model']
    return evidence


def restore(state, request):
    original = request
    request = selected_request(request)
    if state.get('schema') != SCHEMA or state.get('request') != request:
        raise ValueError('Retained resolution belongs to a different issued Request')
    if state.get('suppliedReview'):
        supplied = state['suppliedReview']
        if not isinstance(supplied, dict) or not isinstance(supplied.get('packet'), dict):
            raise ValueError('Malformed retained supplied review package')
        packet = supplied.get('packet', {})
        if review_package(original, packet.get('files'), True, packet.get('completenessSource'), supplied.get('limit')) != supplied:
            raise ValueError('Retained supplied review package differs from its issued work')
    capture = alias(request['work'], 'operatingProfile', 'operating_profile')
    if not capture or state.get('policy') != capture.get('dispatch') or state['mapping']['revision'] != state['policy']['revision']:
        raise ValueError('Retained state contradicts its issued policy or mapping revision')
    candidates = state['policy']['candidates']
    prefix = state.get('attempts', [])
    if [a['candidate'] for a in prefix] != candidates[:len(prefix)] or any(
            a.get('status') != 'UNAVAILABLE' or a.get('executionStarted') is not False for a in prefix):
        raise ValueError('Retained fallback observations contradict the finite candidate prefix')
    if state.get('launch') is not None:
        resolved = state['resolved']
        if len(prefix) >= len(candidates) or resolved['candidate'] != candidates[len(prefix)] or \
                resolved['concrete'] != state['mapping']['assignments'][resolved['candidate']] or \
                state['launch']['model'] != resolved['concrete']['model'] or \
                state['launch']['reasoning_effort'] != resolved['concrete']['effort']:
            raise ValueError('Retained launch contradicts its concrete resolution')
    if state.get('launchReturn') is not None:
        identity = launch_identity(state['launchReturn'])
        if state.get('executorIdentity', identity) != identity:
            raise ValueError('Retained executor contradicts the actual tool return')
    return copy.deepcopy(state)  # no configuration or capability re-resolution


def dispatch(request, mapping, capabilities, launch, root=None, repository=None, request_artifact=None):
    """Use an ordinary caller-supplied tool, preserving exact arguments/return."""
    state = prepare(request, mapping, capabilities, root, repository, request_artifact)
    if state['status'] == 'PREPARED':
        arguments = copy.deepcopy(state['launch'])
        result = launch(**arguments)
        state = launched(state, arguments, result)
    return state


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=['prepare', 'review_package', 'assemble_review_package', 'launched', 'rejected', 'complete', 'uncertain', 'api_prepare', 'api_started', 'api_complete', 'api_uncertain', 'api_execution_evidence', 'inline_complete', 'execution_evidence', 'restore'])
    parser.add_argument('input', help='JSON object containing operation arguments; output is stdout only')
    args = parser.parse_args()
    values = json.loads(Path(args.input).read_text())
    print(json.dumps(globals()[args.operation](**values), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
