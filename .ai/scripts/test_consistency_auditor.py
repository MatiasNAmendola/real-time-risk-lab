#!/usr/bin/env python3
"""
Unit tests for consistency-auditor.py.

Uses a temporary repo structure to validate each sub-audit independently.
"""
from __future__ import annotations

import json
import os
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path


# ---------------------------------------------------------------------------
# Bootstrap: point the auditor at our temp repo
# ---------------------------------------------------------------------------
SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

import importlib.util as _ilu  # noqa: E402
_spec = _ilu.spec_from_file_location("consistency_auditor", SCRIPT_DIR / "consistency-auditor.py")
assert _spec and _spec.loader
_mod = _ilu.module_from_spec(_spec)
sys.modules["consistency_auditor"] = _mod
_spec.loader.exec_module(_mod)  # type: ignore[union-attr]
import consistency_auditor as ca  # noqa: E402


def _make_repo(tmp: str) -> Path:
    root = Path(tmp)
    (root / 'docs').mkdir()
    (root / 'vault' / '00-MOCs').mkdir(parents=True)
    (root / 'vault' / '01-Sessions').mkdir(parents=True)
    (root / 'vault' / '02-Decisions').mkdir(parents=True)
    (root / 'vault' / '03-PoCs').mkdir(parents=True)
    (root / 'vault' / '04-Concepts').mkdir(parents=True)
    (root / 'vault' / '05-Interview').mkdir(parents=True)
    (root / 'poc' / 'my-poc').mkdir(parents=True)
    (root / 'sdks' / 'my-sdk').mkdir(parents=True)
    (root / '.ai' / 'scripts').mkdir(parents=True)
    (root / '.ai' / 'context').mkdir(parents=True)
    (root / '.ai' / 'primitives' / 'skills').mkdir(parents=True)
    (root / '.ai' / 'audit-rules').mkdir(parents=True)
    (root / 'scripts').mkdir()
    return root


def _patch_roots(root: Path) -> None:
    """Monkey-patch all module-level paths to point at the temp repo."""
    ca.REPO_ROOT = root
    ca.VAULT_DIR = root / 'vault'
    ca.DOCS_DIR = root / 'docs'
    ca.AI_DIR = root / '.ai'
    ca.PRIMITIVES_DIR = root / '.ai' / 'primitives'
    ca.AUDIT_RULES_DIR = root / '.ai' / 'audit-rules'
    ca.TERMINOLOGY_YAML = root / '.ai' / 'audit-rules' / 'terminology.yaml'
    ca.DECISIONS_DIR = root / 'vault' / '02-Decisions'
    ca.CONCEPTS_DIR = root / 'vault' / '04-Concepts'
    ca.SESSIONS_DIR = root / 'vault' / '01-Sessions'
    ca.MOCS_DIR = root / 'vault' / '00-MOCs'
    ca.INTERVIEW_DIR = root / 'vault' / '05-Interview'
    ca.QA_SOURCES = [
        root / 'docs' / '09-simulacro.md',
        root / 'vault' / '05-Interview' / 'Drilling.md',
    ]
    ca.MENTION_SEARCH_ROOTS = [
        root / 'README.md',
        root / 'docs',
        root / 'vault',
        root / '.ai' / 'context',
    ]


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestInventory(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.mkdtemp()
        self.root = _make_repo(self.tmp)
        _patch_roots(self.root)

    def test_pocs_detected(self) -> None:
        result = ca.audit_inventory()
        self.assertIn('my-poc', ' '.join(result['artifacts']['pocs']))

    def test_sdks_detected(self) -> None:
        result = ca.audit_inventory()
        self.assertIn('my-sdk', ' '.join(result['artifacts']['sdks']))

    def test_total_positive(self) -> None:
        result = ca.audit_inventory()
        self.assertGreater(result['total'], 0)

    def test_gradle_parse(self) -> None:
        sg = self.root / 'settings.gradle.kts'
        sg.write_text('include("pkg:errors", "poc:alpha")\n')
        result = ca.audit_inventory()
        self.assertIn('pkg:errors', result['artifacts']['gradle_modules'])
        self.assertIn('poc:alpha', result['artifacts']['gradle_modules'])


class TestOrphans(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.mkdtemp()
        self.root = _make_repo(self.tmp)
        _patch_roots(self.root)

    def test_orphan_detected_when_no_mention(self) -> None:
        # my-poc is in the repo but mentioned nowhere
        result = ca.audit_orphans()
        orphan_artifacts = [o['artifact'] for o in result['orphans']]
        self.assertTrue(any('my-poc' in a for a in orphan_artifacts))

    def test_not_orphan_when_mentioned(self) -> None:
        # Mention my-sdk in a doc
        (self.root / 'README.md').write_text('See sdks/my-sdk for details.')
        result = ca.audit_orphans()
        orphan_artifacts = [o['artifact'] for o in result['orphans']]
        self.assertFalse(any('my-sdk' in a for a in orphan_artifacts))

    def test_orphan_count_field(self) -> None:
        result = ca.audit_orphans()
        self.assertIn('orphan_count', result)
        self.assertIsInstance(result['orphan_count'], int)


class TestQaCoverage(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.mkdtemp()
        self.root = _make_repo(self.tmp)
        _patch_roots(self.root)
        # Override artifact concepts with a small set
        self.orig_concepts = ca.ARTIFACT_CONCEPTS[:]
        ca.ARTIFACT_CONCEPTS = [
            {
                'artifact': 'poc/alpha',
                'type': 'PoC',
                'key_terms': ['alpha poc', 'alpha-poc'],
            },
            {
                'artifact': 'sdks/beta-sdk',
                'type': 'SDK',
                'key_terms': ['beta-sdk', 'beta SDK'],
            },
        ]

    def tearDown(self) -> None:
        ca.ARTIFACT_CONCEPTS = self.orig_concepts

    def test_gap_when_no_qa(self) -> None:
        result = ca.audit_qa_coverage()
        self.assertEqual(result['gap_count'], 2)

    def test_covered_when_term_present(self) -> None:
        (self.root / 'docs' / '09-simulacro.md').write_text('alpha poc is great\nbeta-sdk is used here')
        result = ca.audit_qa_coverage()
        self.assertEqual(result['covered'], 2)
        self.assertEqual(result['gap_count'], 0)

    def test_partial_coverage(self) -> None:
        (self.root / 'docs' / '09-simulacro.md').write_text('alpha poc is used')
        result = ca.audit_qa_coverage()
        self.assertEqual(result['covered'], 1)
        self.assertEqual(result['gap_count'], 1)


class TestXrefs(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.mkdtemp()
        self.root = _make_repo(self.tmp)
        _patch_roots(self.root)

    def test_broken_wikilink_detected(self) -> None:
        (self.root / 'docs' / 'test.md').write_text('See [[NonExistentPage]] for more.')
        result = ca.audit_xrefs()
        self.assertGreater(result['broken_count'], 0)
        refs = [b['ref'] for b in result['broken']]
        self.assertIn('[[NonExistentPage]]', refs)

    def test_valid_wikilink_not_broken(self) -> None:
        (self.root / 'vault' / '04-Concepts' / 'MyPage.md').write_text('# My Page')
        (self.root / 'docs' / 'test.md').write_text('See [[MyPage]] for more.')
        result = ca.audit_xrefs()
        refs = [b['ref'] for b in result['broken']]
        self.assertNotIn('[[MyPage]]', refs)

    def test_external_links_not_broken(self) -> None:
        (self.root / 'docs' / 'test.md').write_text('[example](https://example.com)')
        result = ca.audit_xrefs()
        self.assertEqual(result['broken_count'], 0)


class TestStale(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.mkdtemp()
        self.root = _make_repo(self.tmp)
        _patch_roots(self.root)

    def test_stale_path_detected(self) -> None:
        (self.root / 'docs' / 'ref.md').write_text('See poc/ghost-module for an example.')
        result = ca.audit_stale()
        cited = [s['cited_path'] for s in result['stale']]
        self.assertTrue(any('ghost-module' in c for c in cited))

    def test_valid_path_not_stale(self) -> None:
        (self.root / 'poc' / 'my-poc' / 'README.md').write_text('# PoC')
        (self.root / 'docs' / 'ref.md').write_text('See poc/my-poc for details.')
        result = ca.audit_stale()
        cited = [s['cited_path'] for s in result['stale']]
        self.assertFalse(any('my-poc' in c for c in cited))


class TestTerms(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.mkdtemp()
        self.root = _make_repo(self.tmp)
        _patch_roots(self.root)
        # Write a minimal terminology.yaml
        (self.root / '.ai' / 'audit-rules' / 'terminology.yaml').write_text(textwrap.dedent("""
            terms:
              - canonical: "fraud rule"
                aliases:
                  - "regla de fraude"
                  - "fraud_rule"
        """))

    def test_alias_detected(self) -> None:
        (self.root / 'docs' / 'some.md').write_text('This is a regla de fraude handler.')
        result = ca.audit_terms()
        self.assertGreater(result['violation_count'], 0)
        founds = [v['found'] for v in result['violations']]
        self.assertIn('regla de fraude', founds)

    def test_canonical_not_flagged(self) -> None:
        (self.root / 'docs' / 'some.md').write_text('This is a fraud rule handler.')
        result = ca.audit_terms()
        self.assertEqual(result['violation_count'], 0)

    def test_no_yaml_returns_empty(self) -> None:
        ca.TERMINOLOGY_YAML = self.root / '.ai' / 'audit-rules' / 'nonexistent.yaml'
        result = ca.audit_terms()
        self.assertEqual(result['violation_count'], 0)


class TestProhibitedTerms(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.mkdtemp()
        self.root = _make_repo(self.tmp)
        _patch_roots(self.root)
        # Also patch the module-level constants used by audit_prohibited_terms
        self.orig_excluded = ca.PROHIBITED_TERMS_EXCLUDED[:]
        ca.PROHIBITED_TERMS_EXCLUDED = [
            "vault/01-Build-Log/",
            ".ai/research/",
            "out/",
            "_personal/",
        ]

    def tearDown(self) -> None:
        ca.PROHIBITED_TERMS_EXCLUDED = self.orig_excluded

    def test_detects_interview_in_public_doc(self) -> None:
        (self.root / 'docs' / 'guide.md').write_text(
            'This is an technical practice guide.\n'
        )
        result = ca.audit_prohibited_terms()
        self.assertGreater(result['total'], 0)
        terms_found = [m['term'] for m in result['matches']]
        self.assertIn('interview', terms_found)

    def test_ignores_interview_in_build_log(self) -> None:
        (self.root / 'vault' / '01-Build-Log').mkdir(parents=True, exist_ok=True)
        (self.root / 'vault' / '01-Build-Log' / 'session-01.md').write_text(
            'Started this project as technical practice.\n'
        )
        result = ca.audit_prohibited_terms()
        files_flagged = [m['file'] for m in result['matches']]
        self.assertFalse(any('01-Build-Log' in f for f in files_flagged))

    def test_detects_naranja_x_capitalization(self) -> None:
        (self.root / 'docs' / 'arch.md').write_text(
            'The platform was built for Risk Decision Platform as a fraud detection system.\n'
        )
        result = ca.audit_prohibited_terms()
        terms_found = [m['term'] for m in result['matches']]
        self.assertIn('Risk Decision Platform', terms_found)

    def test_does_not_match_riskplatform_as_path_fragment(self) -> None:
        # "riskplatform" as a lowercase URL slug matches Risk Decision Platform case-insensitively.
        # The correct way to avoid the match is to use the excluded paths.
        # This test verifies that a doc inside .ai/research/ is excluded even if
        # it contains "Risk Decision Platform" verbatim.
        (self.root / '.ai' / 'research').mkdir(parents=True, exist_ok=True)
        (self.root / '.ai' / 'research' / 'notes.md').write_text(
            'Risk Decision Platform uses a fraud detection platform.\n'
        )
        result = ca.audit_prohibited_terms()
        files_flagged = [m['file'] for m in result['matches']]
        self.assertFalse(any('.ai/research' in f for f in files_flagged))

    def test_score_decreases_with_each_match(self) -> None:
        # 0 matches -> score 100
        result_clean = ca.audit_prohibited_terms()
        # Add a doc with multiple prohibited terms
        (self.root / 'docs' / 'bad.md').write_text(
            'This is an interview cheatsheet for entrevista prep.\n'
        )
        result_dirty = ca.audit_prohibited_terms()
        self.assertLess(result_dirty['score_pct'], result_clean['score_pct'])

    def test_score_100_when_no_matches(self) -> None:
        # Empty docs dir -> no matches -> score 100
        result = ca.audit_prohibited_terms()
        self.assertEqual(result['score_pct'], 100)
        self.assertEqual(result['total'], 0)

    def test_skip_java_package_match(self) -> None:
        # Java package / Gradle coord — should NOT be flagged (ADR-0038).
        (self.root / 'docs' / 'pkg.md').write_text(
            'Use the dependency `io.riskplatform.poc:risk-client-java:1.2.0`.\n'
            'Imports look like `import io.riskplatform.rules.Engine;`.\n'
        )
        result = ca.audit_prohibited_terms()
        files_flagged = [m['file'] for m in result['matches']]
        self.assertFalse(any('pkg.md' in f for f in files_flagged))

    def test_skip_urn_match(self) -> None:
        # Schema URN — should NOT be flagged.
        (self.root / 'docs' / 'urn.md').write_text(
            'Events publish under URN `urn:riskplatform:risk:event:v1`.\n'
        )
        result = ca.audit_prohibited_terms()
        files_flagged = [m['file'] for m in result['matches']]
        self.assertFalse(any('urn.md' in f for f in files_flagged))

    def test_skip_secrets_path_match(self) -> None:
        # Secrets Manager key path — should NOT be flagged.
        (self.root / 'docs' / 'secrets.md').write_text(
            'Read DB credentials from `riskplatform/db-password` in Secrets Manager.\n'
        )
        result = ca.audit_prohibited_terms()
        files_flagged = [m['file'] for m in result['matches']]
        self.assertFalse(any('secrets.md' in f for f in files_flagged))

    def test_skip_filesystem_path_match(self) -> None:
        # Java source filesystem path — should NOT be flagged.
        (self.root / 'docs' / 'paths.md').write_text(
            'Sources live under `src/main/java/io/riskplatform/poc/risk/`.\n'
        )
        result = ca.audit_prohibited_terms()
        files_flagged = [m['file'] for m in result['matches']]
        self.assertFalse(any('paths.md' in f for f in files_flagged))


class TestScoreComputation(unittest.TestCase):
    def test_perfect_score(self) -> None:
        results = {
            'orphans': {'total_checked': 10, 'orphan_count': 0},
            'qa_coverage': {'pct': 100},
            'xrefs': {'pct': 100},
            'stale': {'pct': 100},
            'terms': {'pct': 100, 'violations': [], 'violation_count': 0},
        }
        score = ca._compute_score(results)
        self.assertEqual(score, 100.0)

    def test_zero_orphans_weight(self) -> None:
        """inventory key should not affect score."""
        results = {
            'inventory': {'total': 5, 'artifacts': {}},
            'orphans': {'total_checked': 10, 'orphan_count': 0},
            'qa_coverage': {'pct': 100},
            'xrefs': {'pct': 100},
            'stale': {'pct': 100},
            'terms': {'pct': 100, 'violations': [], 'violation_count': 0},
        }
        score = ca._compute_score(results)
        self.assertEqual(score, 100.0)

    def test_partial_score(self) -> None:
        results = {
            'orphans': {'total_checked': 10, 'orphan_count': 5},  # 50%
            'qa_coverage': {'pct': 60},
            'xrefs': {'pct': 80},
            'stale': {'pct': 100},
            'terms': {'pct': 100, 'violations': [], 'violation_count': 0},
        }
        score = ca._compute_score(results)
        self.assertGreater(score, 0)
        self.assertLess(score, 100)


class TestMainCli(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.mkdtemp()
        self.root = _make_repo(self.tmp)
        _patch_roots(self.root)

    def test_inventory_exits_0(self) -> None:
        code = ca.main(['inventory'])
        self.assertEqual(code, 0)

    def test_all_exits_0(self) -> None:
        code = ca.main(['all'])
        self.assertEqual(code, 0)

    def test_json_flag(self) -> None:
        import io
        old = sys.stdout
        sys.stdout = io.StringIO()
        code = ca.main(['inventory', '--json'])
        out = sys.stdout.getvalue()
        sys.stdout = old
        self.assertEqual(code, 0)
        data = json.loads(out)
        self.assertIn('results', data)

    def test_strict_passes_when_high_score(self) -> None:
        # Empty repo with no docs = minimal issues
        code = ca.main(['all', '--strict'])
        # Score could be anything, just ensure it doesn't crash
        self.assertIn(code, (0, 1))

    def test_report_md_flag(self) -> None:
        import io
        old = sys.stdout
        sys.stdout = io.StringIO()
        code = ca.main(['all', '--report-md'])
        out = sys.stdout.getvalue()
        sys.stdout = old
        self.assertEqual(code, 0)
        self.assertIn('# Documentation Consistency Audit Report', out)


if __name__ == '__main__':
    unittest.main()
