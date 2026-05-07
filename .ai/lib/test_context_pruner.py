"""Tests for context_pruner.py."""

import unittest
from context_pruner import prune_duplicates, stub_old_tool_results, estimate_savings


def make_result(content: str) -> dict:
    return {"kind": "tool_result", "content": content}

def make_event(kind: str) -> dict:
    return {"kind": kind, "content": "some payload"}


class TestPruneDuplicates(unittest.TestCase):

    def test_no_duplicates_unchanged(self):
        events = [make_result("abc"), make_result("def"), make_result("ghi")]
        result = prune_duplicates(events)
        self.assertEqual(len(result), 3)
        self.assertEqual(sum(1 for e in result if e["kind"] == "tool_result"), 3)

    def test_duplicate_becomes_stub(self):
        events = [make_result("same"), make_result("other"), make_result("same")]
        result = prune_duplicates(events)
        stubs = [e for e in result if e["kind"] == "tool_result_stub"]
        self.assertEqual(len(stubs), 1)

    def test_stub_references_first_occurrence(self):
        events = [make_result("x"), make_result("x")]
        result = prune_duplicates(events)
        stub = result[1]
        self.assertEqual(stub["kind"], "tool_result_stub")
        self.assertEqual(stub["ref"], 0)

    def test_non_tool_results_pass_through(self):
        events = [make_event("user_message"), make_result("data"), make_event("assistant")]
        result = prune_duplicates(events)
        self.assertEqual(result[0]["kind"], "user_message")
        self.assertEqual(result[2]["kind"], "assistant")

    def test_three_identical_two_stubs(self):
        events = [make_result("dup")] * 3
        result = prune_duplicates(events)
        stubs = [e for e in result if e["kind"] == "tool_result_stub"]
        self.assertEqual(len(stubs), 2)

    def test_digest_in_stub(self):
        events = [make_result("hello"), make_result("hello")]
        result = prune_duplicates(events)
        stub = result[1]
        self.assertIn("digest", stub)
        self.assertEqual(len(stub["digest"]), 8)


class TestStubOldToolResults(unittest.TestCase):

    def test_keeps_last_n(self):
        events = [make_result(f"output-{i}") for i in range(10)]
        result = stub_old_tool_results(events, keep_last_n=5)
        kept = [e for e in result if e["kind"] == "tool_result"]
        stubbed = [e for e in result if e["kind"] == "tool_result_stub"]
        self.assertEqual(len(kept), 5)
        self.assertEqual(len(stubbed), 5)

    def test_under_threshold_unchanged(self):
        events = [make_result(f"x{i}") for i in range(3)]
        result = stub_old_tool_results(events, keep_last_n=5)
        self.assertEqual(len(result), 3)
        self.assertTrue(all(e["kind"] == "tool_result" for e in result))

    def test_non_tool_events_not_stubbed(self):
        events = [make_event("msg"), make_result("data"), make_event("reply")]
        result = stub_old_tool_results(events, keep_last_n=5)
        self.assertEqual(result[0]["kind"], "msg")
        self.assertEqual(result[2]["kind"], "reply")


class TestEstimateSavings(unittest.TestCase):

    def test_savings_reported(self):
        original = [make_result("x" * 100), make_result("y" * 100)]
        pruned = [make_result("x" * 100), {"kind": "tool_result_stub", "digest": "abc"}]
        stats = estimate_savings(original, pruned)
        self.assertEqual(stats["original_chars"], 200)
        self.assertEqual(stats["pruned_chars"], 100)
        self.assertEqual(stats["stubs_introduced"], 1)


if __name__ == "__main__":
    unittest.main()
