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
import sys
import tempfile
import unittest
from unittest import mock

# Loading the real generated installer must leave its packaged resource tree intact.
sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('dispatch_adapter', ROOT / 'scripts/goal-phase-dispatch.py')
A = importlib.util.module_from_spec(spec)
spec.loader.exec_module(A)


class DispatchSpec(unittest.TestCase):
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
        self.request = {'kind': 'Request', 'work': {'kind': 'ReviewStep', 'operating_profile': {
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
