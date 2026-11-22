# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Deterministic adapter contracts; tool returns below are specification-owned."""
import copy
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import tomllib
import unittest
from unittest import mock

# Loading the real generated installer must leave its packaged resource tree intact.
sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('dispatch_adapter', ROOT / 'scripts/goal-phase-dispatch.py')
A = importlib.util.module_from_spec(spec)
spec.loader.exec_module(A)


class DispatchSpec(unittest.TestCase):
    def test_explicit_native_detail_selects_original_request_and_refuses_crossed_revision(self):
        # Given a retained owner response and the unchanged original issued context.
        request = {'kind': 'Request', 'submit_session_id': 'session', 'work': {'kind': 'ImplementStep'},
                   'issued_work_context': {'continuation_id': 'issued-work', 'expected_revision': '7'}}
        detail = {'availability': 'available', 'reference': {
            'schema_version': 'sm-workflow.retained-work-reference.v1', 'session_id': 'session',
            'identity': 'issued-work', 'revision': '7'}, 'response': copy.deepcopy(request)}
        original = copy.deepcopy(detail)
        # When dispatch selects the explicit public detail response.
        self.assertEqual(A.selected_request(detail), request)
        # Then no complete historical envelope is rebuilt or modified.
        self.assertEqual(detail, original)
        detail['reference']['revision'] = '8'
        with self.assertRaisesRegex(ValueError, 'crosses its issued'):
            A.selected_request(detail)

    def test_compact_control_requires_explicit_detail_without_launch_or_lookup(self):
        # Given an actual compact control context with its native reference.
        compact = {'kind': 'Request', 'issued_work_context': {'continuation_id': 'native-work'},
                   'retained_work': {'resource': 'sm-workflow-work:actual-owner'}}
        # When passive dispatch has no selected semantic body yet.
        # Then it requests the explicit owner read without inferring work or a fallback.
        with self.assertRaisesRegex(ValueError, 'explicit read-work/detail read'):
            A.prepare(compact, {}, [])

    def test_unsupported_python_fails_before_role_loading_or_launch(self):
        # Given a system interpreter below the shared caller's declared minimum.
        for version in ((3, 9, 6), (3, 11, 9)):
            unsupported = importlib.util.spec_from_file_location('unsupported_dispatch',
                                                               ROOT / 'scripts/goal-phase-dispatch.py')
            module = importlib.util.module_from_spec(unsupported)
            # When the module is loaded, no role, capability or agent is touched.
            with mock.patch.object(sys, 'version_info', version):
                # Then the environment error identifies the supported entry route.
                with self.assertRaisesRegex(RuntimeError,
                                            'ENVIRONMENT_PRECONDITION_ERROR: stable CPython >=3.12'):
                    unsupported.loader.exec_module(module)

    def setUp(self):
        self.mapping = json.loads((ROOT / 'skills/sm-goal-phase/dispatch-mappings.json').read_text())
        self.request = {'kind': 'Request', 'retained_work': {'resource': 'sm-workflow-work:actual'},
            'work': {'kind': 'ReviewStep', 'operating_profile': {
            'dispatch': {'revision': A.REVISION, 'role': 'mechanical',
                         'candidates': ['mechanical', 'small', 'sol-medium', 'difficult', 'hardest'], 'placement': 'DELEGATED',
                         'edges': copy.deepcopy(self.mapping['edges'])}}},
            'issued_work_context': {'continuation_id': 'fixture:work:1', 'expected_revision': '2',
                'execution_requirement': {'disposition': {'placement': 'DELEGATED', 'independence': 'REQUIRED', 'origin_context_identity': 'fixture-parent'},
                                          'review_required': True}}}
        self.facts = [dict(model=c['model'], effort=c['effort'], availability='available', source='fixture-capability')
                      for c in self.mapping['assignments'].values()]

    def prepare(self, facts=None):
        return A.prepare(self.request, self.mapping, self.facts if facts is None else facts, ROOT, ROOT)

    def test_supplied_review_binds_actual_work_and_uses_fresh_restricted_context(self):
        request = copy.deepcopy(self.request)
        request['result_type'] = 'GoalPhaseWorkResult'
        request['issued_work_context']['result_type'] = 'GoalPhaseWorkResult'
        request['work']['checks_receipt'] = 'fixture:validation:1'
        request['work']['candidate'] = {'head': 'actual-head', 'tree': 'actual-tree'}
        request['work']['review'] = {'diff': 'diff --git a/a b/a\n+変更', 'checks': [{'identity': 'test'}]}
        request['work']['prior_history'] = 'must not be sent'
        files = [{'name': 'specification.md', 'content': 'Required behavior'}]
        package = A.review_package(request, files, True, 'caller-selected complete input')
        self.assertEqual(package['status'], 'READY')
        encoded = json.dumps(package['packet'], ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')
        self.assertEqual(package['bytes'], len(encoded))
        self.assertEqual(A.review_package(request, files, True, 'caller-selected complete input', len(encoded)),
                         dict(package, limit=len(encoded)))
        state = A.prepare(request, self.mapping, self.facts, ROOT, ROOT, supplied_review=package)
        self.assertEqual(state['request'], request)
        self.assertEqual(state['launch']['fork_turns'], 'none')
        self.assertNotIn('Admitted working repository:', state['launch']['message'])
        self.assertIn('Do not read the repository', state['launch']['message'])
        self.assertIn('Required behavior', state['launch']['message'])
        self.assertNotIn('must not be sent', state['launch']['message'])
        self.assertEqual(A.restore(state, request), state)
        with self.assertRaisesRegex(ValueError, 'distinct actual executor'):
            A.launched(state, state['launch'], {'agent_id': 'fixture-parent'})
        started = A.launched(state, state['launch'], {'agent_id': 'fresh-reviewer'})
        uncertain = A.uncertain(started, 'fresh-reviewer', 'response lost after start')
        self.assertEqual(A.restore(uncertain, request)['status'], 'UNCERTAIN')
        with self.assertRaises(ValueError):
            A.resolve(uncertain, self.facts)
        recovered = A.complete(uncertain, 'fresh-reviewer', {'outcome': 'decision-needed', 'summary': 'Recovered', 'findings': []})
        self.assertEqual(recovered['status'], 'RETURNED')
        returned = A.complete(started, 'fresh-reviewer', {'outcome': 'decision-needed', 'summary': 'Needs evidence', 'findings': []})
        evidence = A.execution_evidence(returned, [{'identity': 'actual-result', 'revision': '1'}])
        self.assertEqual(evidence['execution']['executorContextIdentity'], 'fresh-reviewer')
        altered = copy.deepcopy(state)
        altered['suppliedReview']['packet']['files'][0]['content'] = 'changed'
        with self.assertRaisesRegex(ValueError, 'differs'):
            A.restore(altered, request)
        crossed = copy.deepcopy(request)
        crossed['work']['candidate']['tree'] = 'another-tree'
        with self.assertRaisesRegex(ValueError, 'differs'):
            A.prepare(crossed, self.mapping, self.facts, ROOT, ROOT, supplied_review=package)

    def test_candidate_file_assembly_is_ordered_bounded_and_keeps_provenance_local(self):
        # Given an issued review and Git's actual candidate object responses.
        request = copy.deepcopy(self.request)
        request['result_type'] = request['issued_work_context']['result_type'] = 'GoalPhaseWorkResult'
        head, tree, a, b = '1' * 40, '2' * 40, '3' * 40, '4' * 40
        request['work'].update(candidate={'head': head, 'tree': tree}, checks_receipt='receipt',
                               review={'diff': 'diff', 'checks': [{'identity': 'test'}]})
        def git(argv, **kwargs):
            self.assertEqual(kwargs['env']['GIT_NO_LAZY_FETCH'], '1')
            self.assertEqual(kwargs['env']['GIT_NO_REPLACE_OBJECTS'], '1')
            args = argv[4:]
            if args[0] == 'rev-parse':
                output = (str(ROOT) + '\n').encode()
            elif args[0] == 'ls-tree':
                name = args[-1]
                blob = {'a.txt': a, 'b.txt': b}[name]
                output = ('100644 blob ' + blob + '\t' + name + '\0').encode()
            elif args[1] == '-t':
                output = b'commit\n' if args[-1] == head else b'tree\n'
            elif args[1] == '-s':
                output = b'1\n'
            else:
                output = {a: b'A', b: b'B'}[args[-1]]
            return mock.Mock(stdout=output)
        exclusions = [{'name': 'omitted.txt', 'reason': 'outside review scope'}]
        # When the caller selects files in either order from the candidate.
        with mock.patch.object(A.subprocess, 'run', side_effect=git):
            first = A.assemble_review_package(request, ROOT, ['b.txt', 'a.txt'], exclusions, True, 'scope reviewed')
            second = A.assemble_review_package(request, ROOT, ['a.txt', 'b.txt'], exclusions, True, 'scope reviewed')
            oversized = A.assemble_review_package(request, ROOT, ['a.txt', 'b.txt'], exclusions, True, 'scope reviewed', 1)
        # Then the separate staged tree is accepted, the v1 packet is stable,
        # and provenance remains outside the packet.
        self.assertEqual(first, second)
        self.assertEqual(first['status'], 'READY')
        self.assertEqual([f['name'] for f in first['suppliedReview']['packet']['files']], ['a.txt', 'b.txt'])
        self.assertEqual(first['provenance']['files'][0]['blob'], a)
        self.assertNotIn('provenance', first['suppliedReview']['packet'])
        self.assertEqual(oversized['reason'], 'files-too-large')
        self.assertEqual(A.assemble_review_package(request, ROOT, ['a.txt'], [], False, 'scope reviewed')['reason'],
                         'completeness-unconfirmed')
        self.assertEqual(A.assemble_review_package(request, ROOT, ['../a.txt'], [], True, 'scope reviewed')['reason'],
                         'invalid-resource-inventory')

    def test_supplied_review_compacts_receipts_and_retains_contract(self):
        request = copy.deepcopy(self.request)
        del request['retained_work']
        request['submit_session_id'] = 'fixture-run'
        request['result_type'] = request['issued_work_context']['result_type'] = 'GoalPhaseWorkResult'
        request['issued_work_context']['context'] = {'summary': 'Evaluate all required conditions'}
        request['work'].update(candidate={'head': 'head', 'tree': 'tree'}, checks_receipt='run:validation:1',
            step_identity='step', selected_plan={'effective_acceptance': {'conditions': ['required']}},
            purpose_repair={'identity': 'actual-contract'},
            issues={'issues': [{'identity': 'issue-1', 'source': 'REVIEW', 'status': 'OPEN', 'step': 'step', 'summary': 'x' * 2000, 'evidence_reference': 'run:old:1'}]},
            review={'diff': 'complete diff', 'checks': [{'identity': 'test', 'termination': 'exited',
                'exit_code': 0, 'sbt': {'kind': 'test', 'outcome': 'succeeded'}, 'stdout': 'x' * 600000}]})
        detail = {'availability': 'available', 'reference': {
            'schema_version': 'sm-workflow.retained-work-reference.v1', 'session_id': 'fixture-run',
            'identity': 'fixture:work:1', 'revision': '2', 'resource': 'sm-workflow-work:retained-owner'},
            'response': request}
        files = [{'name': 'source', 'content': 'complete'}]
        supplied = A.review_package(detail, files, True, 'attested')
        packet = supplied['packet']
        self.assertEqual(packet['workResource'], 'sm-workflow-work:retained-owner')
        self.assertEqual(packet['checksReceipt'], 'run:validation:1')
        self.assertEqual(packet['review']['checks'][0]['exitCode'], 0)
        self.assertNotIn('stdout', packet['review']['checks'][0])
        self.assertEqual(packet['contract']['issues']['issues'][0]['evidence_reference'], 'run:old:1')
        self.assertEqual(packet['contract']['purposeRepair']['identity'], 'actual-contract')
        self.assertEqual(packet['contract']['selectedPlan']['effective_acceptance']['conditions'], ['required'])
        self.assertEqual(packet['contract']['issues']['issues'][0]['status'], 'OPEN')
        self.assertIn('RECURRING requires a unique 1-based findingOrdinal', A.REVIEW_RESULT_INSTRUCTIONS)
        self.assertIn('issueReport.deferredIssues', A.REVIEW_RESULT_INSTRUCTIONS)
        self.assertEqual(packet['contract']['instructions'], 'Evaluate all required conditions')
        state = A.prepare(detail, self.mapping, self.facts, ROOT, ROOT, supplied_review=supplied)
        self.assertEqual(A.restore(state, detail), state)
        detail['reference']['resource'] = 'sm-workflow-work:crossed-owner'
        with self.assertRaisesRegex(ValueError, 'differs'):
            A.restore(state, detail)
        del request['work']['issues']['issues'][0]['evidence_reference']
        with self.assertRaisesRegex(ValueError, 'retained evidence'):
            A.review_package(detail, files, True, 'attested')

    def test_supplied_review_declines_incomplete_or_oversized_without_truncation(self):
        request = copy.deepcopy(self.request)
        request['result_type'] = 'GoalPhaseWorkResult'
        request['issued_work_context']['result_type'] = 'GoalPhaseWorkResult'
        request['work']['checks_receipt'] = 'fixture:validation:1'
        request['work']['candidate'] = {'head': 'head', 'tree': 'tree'}
        request['work']['review'] = {'diff': 'complete diff', 'checks': [{'identity': 'check'}]}
        files = [{'name': 'spec', 'content': 'x' * 65536}, {'name': 'source', 'content': '追加'}]
        packet = A.review_package(request, files, True, 'attested')
        self.assertEqual(packet['packet']['files'], files)
        self.assertGreater(packet['bytes'], 65536)
        self.assertEqual(A.review_package(request, files, False, 'attested')['reason'], 'completeness-unconfirmed')
        self.assertEqual(A.review_package(request, [], True, 'attested')['reason'], 'missing-or-ambiguous-supplied-files')
        self.assertEqual(A.review_package(request, files, True, 'attested', packet['bytes'] - 1)['reason'], 'package-too-large')
        with self.assertRaisesRegex(ValueError, '512 KiB'):
            A.review_package(request, files, True, 'attested', A.REVIEW_PACKAGE_MAX + 1)
        malformed = copy.deepcopy(request)
        malformed['issued_work_context']['execution_requirement'] = 'invalid'
        with self.assertRaisesRegex(ValueError, 'Malformed'):
            A.review_package(malformed, files, True, 'attested')
        self.assertEqual(A.prepare(request, self.mapping, self.facts, ROOT, ROOT)['status'], 'PREPARED')
        request['work']['kind'] = 'ImplementStep'
        with self.assertRaisesRegex(ValueError, 'issued review'):
            A.review_package(request, files, True, 'attested')

    def test_supplied_review_reproduces_ordered_multifile_package_and_provenance(self):
        request = copy.deepcopy(self.request)
        request['result_type'] = request['issued_work_context']['result_type'] = 'GoalPhaseWorkResult'
        request['work']['checks_receipt'] = 'fixture:validation:1'
        request['work']['candidate'] = {'head': 'candidate-head', 'tree': 'candidate-tree'}
        request['work']['review'] = {'diff': '+変更', 'checks': [{'identity': 'test'}]}
        files = [{'name': 'spec', 'content': '要件'}, {'name': 'source', 'content': 'code'}]

        first = A.review_package(request, files, True, 'caller-selected complete input')
        repeated = A.review_package(copy.deepcopy(request), copy.deepcopy(files), True,
                                    'caller-selected complete input')
        self.assertEqual(first, repeated)
        self.assertEqual(first['packet']['files'], files)
        self.assertEqual(first['packet']['candidate'], request['work']['candidate'])
        self.assertEqual(first['packet']['checksReceipt'], 'fixture:validation:1')
        self.assertEqual(first['packet']['workResource'], 'sm-workflow-work:actual')
        encoded = json.dumps(first['packet'], ensure_ascii=False, sort_keys=True,
                             separators=(',', ':')).encode('utf-8')
        self.assertEqual(first['bytes'], len(encoded))
        self.assertEqual(A.review_package(request, files, True, 'caller-selected complete input',
                                          len(encoded) - 1)['reason'], 'package-too-large')
        self.assertEqual(first['packet']['files'][0]['content'], '要件')

    def test_api_attempt_retains_identity_and_never_restarts_uncertain_execution(self):
        request = copy.deepcopy(self.request)
        request['result_type'] = 'GoalPhaseWorkResult'
        request['issued_work_context'].update(result_type='GoalPhaseWorkResult', run_id='actual-session')
        request['work']['checks_receipt'] = 'fixture:validation:1'
        request['work']['candidate'] = {'head': 'head', 'tree': 'tree'}
        request['work']['review'] = {'diff': 'diff', 'checks': [{'identity': 'check'}]}
        package = A.review_package(request, [{'name': 'spec', 'content': 'required'}], True, 'complete')
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'package.json'
            canonical = json.dumps(package['packet'], ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')
            path.write_bytes(canonical)
            state = A.api_prepare(request, package, 'sm-workflow-work:actual', 'attempt-1', str(path))
            self.assertEqual(state['arguments']['input']['packageResource'], path.as_uri())
            self.assertNotIn('packageJson', state['arguments']['input'])
            with self.assertRaisesRegex(ValueError, 'Actual work resource'):
                A.api_prepare(request, package, 'sm-workflow-work:crossed', 'attempt-1', str(path))
            path.write_bytes(b'{}')
            with self.assertRaisesRegex(ValueError, 'canonical'):
                A.api_prepare(request, package, 'sm-workflow-work:actual', 'attempt-1', str(path))
        started = A.api_started(state, state['arguments'], 'invocation-1')
        uncertain = A.api_uncertain(started, 'invocation-1', 'lost response')
        with self.assertRaises(ValueError):
            A.api_started(uncertain, state['arguments'], 'invocation-2')
        with self.assertRaises(ValueError):
            A.api_complete(uncertain, 'invocation-2', {})
        blocked_response = {'status': 'BLOCKED', 'attemptIdentity': 'attempt-1',
                            'continuationId': 'fixture:work:1', 'message': 'ai.input-budget'}
        blocked = A.api_complete(uncertain, 'invocation-1', blocked_response)
        self.assertEqual(blocked['status'], 'BLOCKED')
        self.assertEqual(blocked['response'], blocked_response)
        with self.assertRaises(ValueError):
            A.api_started(blocked, state['arguments'], 'invocation-2')
        with self.assertRaises(ValueError):
            A.api_complete(blocked, 'invocation-1', blocked_response)
        with self.assertRaises(ValueError):
            A.api_execution_evidence(blocked, [{'identity': 'receipt', 'revision': '1'}])
        opaque_block = dict(blocked_response, message='provider failed')
        with self.assertRaises(ValueError):
            A.api_complete(uncertain, 'invocation-1', opaque_block)
        for other_status in ('DECLINED', 'UNAVAILABLE', 'FAILED', 'UNCERTAIN'):
            other = A.api_complete(uncertain, 'invocation-1', dict(blocked_response, status=other_status))
            self.assertEqual(other['status'], 'UNCERTAIN' if other_status == 'UNCERTAIN' else 'RETURNED')
            self.assertEqual(other['response']['status'], other_status)
        crossed_block = copy.deepcopy(blocked_response)
        crossed_block['continuationId'] = 'another-continuation'
        with self.assertRaises(ValueError):
            A.api_complete(uncertain, 'invocation-1', crossed_block)
        response = {'status': 'COMPLETED', 'attemptIdentity': 'attempt-1',
                    'continuationId': 'fixture:work:1',
                    'result': {'outcome': 'decision-needed', 'summary': 'reviewed', 'findings': []}}
        returned = A.api_complete(uncertain, 'invocation-1', response)
        self.assertEqual(returned['status'], 'RETURNED')
        evidence = A.api_execution_evidence(returned, [{'identity': 'retained-result', 'revision': '1'}])
        self.assertEqual(evidence['execution']['executorContextIdentity'], 'invocation-1')
        self.assertNotIn('modelIdentity', evidence)
        crossed = copy.deepcopy(response)
        crossed['attemptIdentity'] = 'another-attempt'
        with self.assertRaises(ValueError):
            A.api_complete(uncertain, 'invocation-1', crossed)
        same_context = copy.deepcopy(returned)
        same_context['invocationIdentity'] = 'fixture-parent'
        with self.assertRaises(ValueError):
            A.api_execution_evidence(same_context, [{'identity': 'retained-result', 'revision': '1'}])

    def test_public_worker_result_projection_binds_format_type_and_context(self):
        request = copy.deepcopy(self.request)
        request['result_type'] = 'GoalPhaseWorkResult'
        request['retained_work'] = {'resource': 'sm-workflow-work:original'}
        request['issued_work_context']['result_type'] = 'GoalPhaseWorkResult'
        state = A.prepare(request, self.mapping, self.facts, ROOT, ROOT)
        state = A.launched(state, state['launch'], {'task_name': 'fixture-worker'})
        public = {'outcome': 'completed', 'summary': 'full result', 'findings': [],
                  'dependencyValidation': {'binding': {'identity': 'source', 'revision': '1'},
                                           'approvals': [{'decision': {'identity': 'approval', 'revision': '2'}}]}}
        envelope = {'schemaVersion': A.PUBLIC_RETURN_SCHEMA, 'resultType': 'GoalPhaseWorkResult',
                    'workResource': request['retained_work']['resource'], 'result': public}
        compact = {'outcome': 'completed', 'summary': 'full result', 'resultType': 'GoalPhaseWorkResult',
                   'resultResource': 'file:///fixture/result.json'}
        self.assertEqual(A.complete(state, 'fixture-worker', compact)['semanticResult'], compact)
        returned = A.complete(state, 'fixture-worker', envelope)
        self.assertEqual(returned['semanticResult'], public)
        self.assertEqual(A.complete(state, 'fixture-worker', public)['semanticResult'], public)
        public['findings'].append('changed after return')
        self.assertEqual(returned['semanticResult']['findings'], [])
        invalid = []
        for key, replacement in [('schemaVersion', 'sm-workflow.goal-phase-native-work-result.v1'),
                                 ('resultType', 'GoalTaskWorkResult'), ('workResource', 'sm-workflow-work:other'), ('result', None)]:
            bad = copy.deepcopy(envelope); bad[key] = replacement; invalid.append(bad)
        invalid.extend([{**envelope, 'issuedWorkContext': request['issued_work_context']},
                        {k: v for k, v in {**envelope, 'issuedWorkContext': request['issued_work_context']}.items() if k != 'workResource'},
                        {**envelope, 'payload': public}, {**envelope, 'result_type': 'GoalPhaseWorkResult'},
                        {'payload': public}, {'outcome': 'completed', 'dependencyAdmission': None}])
        for bad in invalid:
            with self.subTest(value=bad), self.assertRaises(ValueError):
                A.complete(state, 'fixture-worker', bad)
        other = copy.deepcopy(state); other['request']['issued_work_context']['result_type'] = 'GoalTaskWorkResult'
        with self.assertRaises(ValueError):
            A.complete(other, 'fixture-worker', envelope)
        legacy = copy.deepcopy(self.request)
        with self.assertRaises(ValueError):
            A.project_result(legacy, envelope)
        # Both equal and unequal alias pairs are ambiguous at this projection boundary.
        for field in ('request_type', 'request_context', 'context_type'):
            for equal in (True, False):
                with self.subTest(field=field, equal=equal):
                    crossed = copy.deepcopy(request)
                    if field == 'request_type':
                        crossed['resultType'] = 'GoalPhaseWorkResult' if equal else 'GoalTaskWorkResult'
                    elif field == 'request_context':
                        crossed['issuedWorkContext'] = copy.deepcopy(crossed['issued_work_context']) if equal else {}
                    else:
                        crossed['issued_work_context']['resultType'] = 'GoalPhaseWorkResult' if equal else 'GoalTaskWorkResult'
                    with self.assertRaisesRegex(ValueError, 'Ambiguous projection field aliases'):
                        A.project_result(crossed, public)
                    with self.assertRaisesRegex(ValueError, 'Ambiguous projection field aliases'):
                        A.project_result(crossed, envelope)
        # Other pre-existing callers of alias retain their historical equal-alias behavior.
        self.assertEqual(A.alias({'resultType': 'same', 'result_type': 'same'}, 'resultType', 'result_type'), 'same')

    def test_bounded_roles_preserve_the_actual_request_context_and_observation(self):
        # Given the three bounded semantic names and the existing versioned policy.
        for kind, role in (('Implement', 'implementation'), ('Repair', 'repair'), ('Review', 'review'),
                           ('DeliveryAcceptance', 'implementation')):
            with self.subTest(kind=kind):
                request = copy.deepcopy(self.request)
                request['work']['kind'] = kind
                original = copy.deepcopy(request)
                # When the finite adapter binds a real tool return and semantic result.
                state = A.prepare(request, self.mapping, self.facts, ROOT, ROOT)
                self.assertEqual(state['role'], role)
                self.assertEqual(state['contract'], tomllib.loads((ROOT / 'agents' / ('sm_workflow_' + role + '.toml'))
                                 .read_text())['developer_instructions'])
                worker = '/root/bounded-' + role
                state = A.launched(state, state['launch'], {'task_name': worker})
                result = {'outcome': 'accept' if kind in ('Review', 'DeliveryAcceptance') else 'completed', 'summary': 'Specification-owned return'}
                state = A.complete(state, worker, result)
                evidence = A.execution_evidence(state, [dict(identity='bounded-result', revision='1')])
                # Then public context, actual worker and logical candidate remain exact.
                self.assertEqual(state['request'], original)
                self.assertEqual(request, original)
                self.assertEqual(A.restore(state, original), state)
                self.assertEqual(evidence['workerIdentity'], worker)
                self.assertEqual(evidence['execution']['originContextIdentity'], 'fixture-parent')
                self.assertEqual(evidence['execution']['executorContextIdentity'], worker)
                self.assertEqual(evidence['execution']['profileIdentity'], 'mechanical')
                self.assertNotIn('modelIdentity', evidence)
        for unsupported in ('Inspect', 'Dispatch', 'Publish', 'implement'):
            request = copy.deepcopy(self.request)
            request['work']['kind'] = unsupported
            with self.assertRaisesRegex(ValueError, 'Unsupported semantic role'):
                A.prepare(request, self.mapping, self.facts, ROOT, ROOT)

    def test_all_edges_and_exhaustion_preserve_exact_effort(self):
        # Given each configured route and progressively unavailable prefixes.
        for role in self.mapping['assignments']:
            path, current = [], role
            while current:
                path.append(current)
                current = self.mapping['edges'].get(current)
            request = copy.deepcopy(self.request)
            request['work']['operating_profile']['dispatch'].update(role=role, candidates=path)
            for unavailable in range(len(path) + 1):
                facts = copy.deepcopy(self.facts)
                for candidate in path[:unavailable]:
                    concrete = self.mapping['assignments'][candidate]
                    for fact in facts:
                        if all(fact[k] == concrete[k] for k in ('model', 'effort')):
                            fact['availability'] = 'unavailable'
                state = A.prepare(request, self.mapping, facts, ROOT, ROOT)
                self.assertEqual(len(state['attempts']), unavailable)
                if unavailable == len(path):
                    self.assertEqual(state['status'], 'UNAVAILABLE')
                else:
                    concrete = self.mapping['assignments'][path[unavailable]]
                    self.assertEqual(state['launch']['model'], concrete['model'])
                    self.assertEqual(state['launch']['reasoning_effort'], concrete['effort'])

    def test_captured_subset_is_executed_without_restoring_removed_edges(self):
        request = copy.deepcopy(self.request)
        request['work']['operating_profile']['dispatch'].update(
            edges={'mechanical': 'small'}, candidates=['mechanical', 'small'])
        facts = copy.deepcopy(self.facts)
        facts[0]['availability'] = 'unavailable'
        state = A.prepare(request, self.mapping, facts, ROOT, ROOT)
        self.assertEqual(state['resolved']['candidate'], 'small')
        self.assertEqual(A.launched(state, state['launch'], {'task_name': '/root/subset'})['executorIdentity'], '/root/subset')
        for fact in facts:
            if fact['model'] == 'gpt-6.1-terra' and fact['effort'] == 'medium':
                fact['availability'] = 'unavailable'
        exhausted = A.prepare(request, self.mapping, facts, ROOT, ROOT)
        self.assertEqual(exhausted['status'], 'UNAVAILABLE')
        self.assertEqual([a['candidate'] for a in exhausted['attempts']], ['mechanical', 'small'])
        request['work']['operating_profile']['dispatch']['edges'] = {'mechanical': 'hardest'}
        with self.assertRaises(ValueError):
            A.prepare(request, self.mapping, facts, ROOT, ROOT)

    def test_public_role_key_aliases_preserve_request_subset_and_reject_collisions(self):
        for key in ('sol_medium', 'solMedium'):
            request = copy.deepcopy(self.request)
            policy = request['work']['operating_profile']['dispatch']
            policy.update(role='sol-medium', candidates=['sol-medium', 'difficult'],
                          edges={key: 'difficult'})
            original = copy.deepcopy(request)
            facts = copy.deepcopy(self.facts)
            for fact in facts:
                if fact['model'] == 'gpt-6.1-sol' and fact['effort'] == 'medium':
                    fact['availability'] = 'unavailable'
            state = A.prepare(request, self.mapping, facts, ROOT, ROOT)
            self.assertEqual(state['resolved']['candidate'], 'difficult')
            self.assertEqual(state['policy'], policy)
            self.assertEqual(state['request'], original)
            self.assertEqual(request, original)

            self.assertEqual(A.restore(state, request), state)
            for target in ('difficult', 'hardest'):
                policy['edges']['sol-medium'] = target
                with self.assertRaisesRegex(ValueError, 'logical role key aliases'):
                    A.prepare(request, self.mapping, facts, ROOT, ROOT)

    def test_real_tool_task_name_is_retained_and_crossed_aliases_are_refused(self):
        prepared = self.prepare()
        returned = {'task_name': '/root/actual-supported-shape'}
        state = A.launched(prepared, prepared['launch'], returned)
        self.assertEqual(state['launchReturn'], returned)
        state = A.complete(state, returned['task_name'], {'outcome': 'accept', 'summary': 'Fixture', 'findings': []})
        evidence = A.execution_evidence(state, [dict(identity='fixture-result', revision='1')])
        self.assertEqual(evidence['workerIdentity'], returned['task_name'])
        self.assertNotIn('modelIdentity', evidence)
        with self.assertRaises(ValueError):
            A.launched(prepared, prepared['launch'], dict(returned, agent_id='/root/other'))

    def test_assignment_names_distinguish_continuations_and_remain_stable_on_resume(self):
        # Given repeated repairs/reviews of the same role in one caller context.
        first = self.prepare()
        next_request = copy.deepcopy(self.request)
        next_request['issued_work_context']['continuation_id'] = 'fixture:work:2'
        second = A.prepare(next_request, self.mapping, self.facts, ROOT, ROOT)
        self.assertRegex(first['launch']['task_name'], r'^sm_review_[0-9a-f]{12}$')
        self.assertNotEqual(first['launch']['task_name'], second['launch']['task_name'])
        self.assertEqual(self.prepare()['launch'], first['launch'])
        self.assertEqual(A.restore(json.loads(json.dumps(first)), self.request), first)
        # A caller name collision is not model unavailability or a successful start.
        stopped = A.rejected(first, first['launch'], dict(
            kind='task-name-conflict', executionStarted=False, source='fixture-tool-rejection'), self.facts)
        self.assertEqual(stopped['status'], 'STOPPED')
        self.assertEqual(stopped['attempts'], [])
        # Existing saved launches and the actual tool-returned identity stay exact.
        legacy = copy.deepcopy(first)
        legacy['launch']['task_name'] = 'sm_review'
        self.assertEqual(A.restore(legacy, self.request), legacy)
        returned = {'task_name': '/root/' + first['launch']['task_name']}
        started = A.launched(first, first['launch'], returned)
        self.assertEqual(A.restore(started, self.request)['launchReturn'], returned)
        self.assertEqual(started['executorIdentity'], returned['task_name'])

    def test_large_public_request_uses_verified_artifact_without_copying_logs_into_launch(self):
        request = copy.deepcopy(self.request)
        request['work']['diagnostic_history'] = ['large fixture evidence ' * 20000]
        with self.assertRaises(ValueError):
            A.prepare(request, self.mapping, self.facts, ROOT, ROOT)
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / 'public-request.json'
            artifact.write_text(json.dumps(request))
            state = A.prepare(request, self.mapping, self.facts, ROOT, ROOT, str(artifact))
            self.assertEqual(state['request'], request)
            self.assertIn(str(artifact), state['launch']['message'])
            self.assertNotIn('large fixture evidence', state['launch']['message'])
            self.assertLess(len(state['launch']['message']), 32768)
            self.assertEqual(A.restore(state, request), state)
            artifact.write_text(json.dumps(self.request))
            with self.assertRaises(ValueError):
                A.prepare(request, self.mapping, self.facts, ROOT, ROOT, str(artifact))

    def test_actual_generated_payload_installs_adapter_and_exact_mapping(self):
        # Given the current build's real generated component resources, supplied
        # by the owning selected Scala suite rather than a fabricated inventory.
        resource_root = Path(os.environ['SM_WORKFLOW_GENERATED_RESOURCES'])
        spec = importlib.util.spec_from_file_location('generated_installer', resource_root / 'install-sm-workflow.py')
        installer = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(installer)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / 'product.zip'
            installer.package_bundle(resource_root, archive)
            payload = installer.load_bundle(archive)
            installed = root / 'shared'
            installer.install(payload, installed, root / 'home', write=True)
            self.assertEqual((installed / 'scripts/goal-phase-dispatch.py').read_bytes(),
                             (resource_root / 'scripts/goal-phase-dispatch.py').read_bytes())
            mapped = json.loads((installed / 'skills/sm-goal-phase/dispatch-mappings.json').read_text())
            adapter_spec = importlib.util.spec_from_file_location('installed_adapter', installed / 'scripts/goal-phase-dispatch.py')
            adapter = importlib.util.module_from_spec(adapter_spec)
            adapter_spec.loader.exec_module(adapter)
            request = copy.deepcopy(self.request)
            request['work'] = json.loads(os.environ['SM_WORKFLOW_PUBLIC_DISPATCH_WORK'])
            self.assertIn('sol_medium', request['work']['operating_profile']['dispatch']['edges'])
            original = copy.deepcopy(request)
            state = adapter.prepare(request, mapped, self.facts, installed, ROOT)
            self.assertIn('read-only', state['launch']['message'])
            self.assertEqual(state['request'], original)
            self.assertEqual(request, original)

            # The installed resource also assembles the issued candidate, rather
            # than reading the possibly dirty source worktree.
            head = subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', 'HEAD'], text=True).strip()
            tree = subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', 'HEAD^{tree}'], text=True).strip()
            path = 'scripts/goal-phase-dispatch.py'
            candidate_content = subprocess.check_output(['git', '-C', str(ROOT), 'show', head + ':' + path]).decode('utf-8')
            request['result_type'] = request['issued_work_context']['result_type'] = 'GoalPhaseWorkResult'
            request['work'].update(candidate={'head': head, 'tree': tree}, checks_receipt='fixture:receipt',
                                   review={'diff': 'fixture diff', 'checks': [{'identity': 'test'}]})
            first = adapter.assemble_review_package(request, ROOT, [path], [], True, 'fixture scope')
            repeated = adapter.assemble_review_package(request, ROOT, [path], [], True, 'fixture scope')
            self.assertEqual(first, repeated)
            self.assertEqual(first['status'], 'READY')
            self.assertEqual(first['suppliedReview']['packet']['files'], [{'name': path, 'content': candidate_content}])
            self.assertEqual(first['provenance']['candidate'], {'head': head, 'tree': tree})
            self.assertEqual(adapter.assemble_review_package(request, ROOT, ['missing-fixture'], [], True,
                                                              'fixture scope')['reason'], 'candidate-file-unavailable')

    def test_launch_callback_preserves_role_contract_arguments_return_and_resume(self):
        # Given exposed absence of Luna/Terra, then an available Sol high.
        facts = copy.deepcopy(self.facts)
        for fact in facts:
            if fact['model'] != 'gpt-6.1-sol' or fact['effort'] != 'high':
                fact['availability'] = 'unavailable'
        calls = []
        def launch(**args):
            calls.append(args)
            return {'agent_id': 'fixture-agent', 'status': 'running'}
        state = A.dispatch(self.request, self.mapping, facts, launch, ROOT, ROOT)
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0]['model'], 'gpt-6.1-sol')
        self.assertEqual(calls[0]['reasoning_effort'], 'high')
        self.assertEqual(calls[0]['fork_turns'], 'none')
        self.assertIn('read-only', calls[0]['message'])
        self.assertEqual(state['observed'], None)
        state = A.complete(state, 'fixture-agent', {'outcome': 'decision-needed', 'summary': 'Fixture', 'findings': []})
        self.assertEqual(state['status'], 'RETURNED')
        evidence = A.execution_evidence(state, [dict(identity='fixture-result', revision='1')])
        self.assertEqual(evidence['execution']['executorContextIdentity'], 'fixture-agent')
        self.assertEqual(evidence['execution']['profileIdentity'], 'difficult')
        self.assertNotIn('modelIdentity', evidence)
        self.assertEqual(A.restore(json.loads(json.dumps(state)), self.request), state)
        with self.assertRaises(ValueError):
            A.resolve(state, self.facts)
        with self.assertRaises(ValueError):
            A.complete(state, 'other', {'outcome': 'completed'})

    def test_nonavailability_failures_and_unknown_capability_never_fallback(self):
        state = self.prepare()
        for kind in ('permission', 'policy', 'invalid-config', 'timeout', 'transport', 'declined', 'execution-failed'):
            result = A.rejected(state, state['launch'], dict(kind=kind, executionStarted=False, source='fixture'), self.facts)
            self.assertEqual(result['status'], 'STOPPED')
            self.assertEqual(result['attempts'], [])
        result = A.rejected(state, state['launch'], dict(kind='model-effort-unavailable', executionStarted=True, source='fixture'), self.facts)
        self.assertEqual(result['status'], 'STOPPED')
        self.assertEqual(self.prepare([])['status'], 'CAPABILITY_UNKNOWN')
        rejection = dict(kind='model-effort-unavailable', executionStarted=False, source='fixture-typed-rejection')
        result = A.rejected(state, state['launch'], rejection, self.facts)
        self.assertEqual(result['resolved']['candidate'], 'small')
        self.assertEqual(len(result['attempts']), 1)
        with self.assertRaises(ValueError):
            A.launched(state, dict(state['launch'], reasoning_effort='low'), {'agent_id': 'fixture'})

    def test_no_retrospective_or_crossed_capture_and_no_inline_independent_review(self):
        request = copy.deepcopy(self.request)
        del request['work']['operating_profile']
        with self.assertRaises(ValueError):
            A.prepare(request, self.mapping, self.facts, ROOT, ROOT)
        request = copy.deepcopy(self.request)
        request['work']['operating_profile']['dispatch'].update(placement='INLINE', role='parent', candidates=['parent'])
        request['issued_work_context']['execution_requirement']['disposition']['placement'] = 'INLINE'
        with self.assertRaises(ValueError):
            A.prepare(request, self.mapping, self.facts, ROOT, ROOT)
        request['issued_work_context']['execution_requirement']['review_required'] = False
        request['issued_work_context']['execution_requirement']['disposition']['independence'] = 'OPTIONAL'
        self.assertEqual(A.prepare(request, self.mapping, [], ROOT, ROOT)['status'], 'INLINE')
        state = self.prepare()
        crossed = copy.deepcopy(self.request)
        crossed['issued_work_context']['expected_revision'] = '3'
        with self.assertRaises(ValueError):
            A.restore(state, crossed)
        malformed = copy.deepcopy(self.mapping)
        malformed['assignments']['hardest']['effort'] = None
        with self.assertRaises(ValueError):
            A.prepare(self.request, malformed, self.facts, ROOT, ROOT)


if __name__ == '__main__':
    unittest.main()
