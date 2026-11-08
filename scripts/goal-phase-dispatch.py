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
from pathlib import Path
import sys

if (sys.implementation.name != 'cpython' or sys.version_info < (3, 12)
        or sys.version_info.releaselevel != 'final'):
    raise RuntimeError('ENVIRONMENT_PRECONDITION_ERROR: stable CPython >=3.12 is required; '
                       'use uv run --no-cache --managed-python --no-python-downloads --script')

import tomllib

SCHEMA = 'sm-workflow.caller-dispatch.v1'
REVISION = 'standard-2026-11-01'


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


def prepare(request, mapping, capabilities, root=None, repository=None, request_artifact=None):
    """Capabilities are caller-exposed facts, not guessed from this table."""
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
    if artifact is None and len(json.dumps(request, ensure_ascii=False)) > 131072:
        raise ValueError('Large issued Requests require their verified caller-retained request_artifact')
    state = dict(schema=SCHEMA, request=copy.deepcopy(request), mapping=copy.deepcopy(mapping),
                 policy=copy.deepcopy(policy), role=role, contract=contract, repository=repository,
                 requestArtifact=artifact, attempts=[], status='READY', launch=None)
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
        if state.get('requestArtifact'):
            input_message = ('Read the exact caller-retained public Request artifact: ' + state['requestArtifact'] +
                             '\nFor an explicit native detail response, response is the selected original Request. '
                             'Preserve that unchanged Request, its issued context and return contract. '
                             'Read only the scope and evidence needed for this assignment; no private workflow storage.')
        else:
            input_message = ('Actual issued Request (unchanged):\n' + json.dumps(state['request'], ensure_ascii=False))
        state['launch'] = dict(agent_type='default', fork_turns='none',
            task_name='sm_' + state['role'] + '_' + hashlib.sha256(continuation.encode('utf-8')).hexdigest()[:12],
            model=concrete['model'], reasoning_effort=concrete['effort'],
            message=state['contract'] + '\n\nAdmitted working repository: ' + state['repository'] + '\n\n' + input_message)
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


def complete(state, agent_id, semantic_result, observed=None, resources=None):
    if state['status'] != 'STARTED' or launch_identity(state['launchReturn']) != agent_id:
        raise ValueError('Only the actual launched agent can supply its return')
    if not isinstance(semantic_result, dict) or not semantic_result.get('outcome'):
        raise ValueError('The actual typed semantic payload is required')
    state = copy.deepcopy(state)
    state.update(status='RETURNED', semanticResult=copy.deepcopy(semantic_result), observed=observed, resources=resources)
    selected = state['resolved']['concrete']
    state['selectionMismatch'] = bool(observed and any(observed.get(k) is not None and observed[k] != selected[k]
                                                       for k in ('model', 'effort')))
    # A semantic decision-needed/DECLINED is a returned result, never availability.
    return state


def inline_complete(state, origin_context, semantic_result, observed=None, resources=None):
    issued = state['request'].get('issued_work_context', state['request'].get('issuedWorkContext'))
    requirement = alias(issued, 'executionRequirement', 'execution_requirement')
    origin = alias(requirement['disposition'], 'originContextIdentity', 'origin_context_identity')
    if state['status'] != 'INLINE' or origin_context != origin or not semantic_result.get('outcome'):
        raise ValueError('INLINE return requires the actual issued parent context and semantic payload')
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
    request = selected_request(request)
    if state.get('schema') != SCHEMA or state.get('request') != request:
        raise ValueError('Retained resolution belongs to a different issued Request')
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
    parser.add_argument('operation', choices=['prepare', 'launched', 'rejected', 'complete', 'inline_complete', 'execution_evidence', 'restore'])
    parser.add_argument('input', help='JSON object containing operation arguments; output is stdout only')
    args = parser.parse_args()
    values = json.loads(Path(args.input).read_text())
    print(json.dumps(globals()[args.operation](**values), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
