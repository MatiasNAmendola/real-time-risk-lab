"""Tests for handoff_schema.py."""

import unittest
import json
from handoff_schema import HandoffEnvelope, SCHEMA_VERSION


class TestHandoffSchema(unittest.TestCase):

    def test_create_basic(self):
        env = HandoffEnvelope.create("orchestrator", "impl-agent", "implement feature X")
        self.assertEqual(env.sender, "orchestrator")
        self.assertEqual(env.recipient, "impl-agent")
        self.assertEqual(env.context_mode, "summary")
        self.assertEqual(env.schema_version, SCHEMA_VERSION)
        self.assertIsNotNone(env.message_id)
        self.assertIsNotNone(env.created_at)

    def test_create_with_all_fields(self):
        env = HandoffEnvelope.create(
            "agent-a", "agent-b", "review code",
            mode="reference",
            ref="/tmp/context.md",
            corr_id="abc-123",
        )
        self.assertEqual(env.context_mode, "reference")
        self.assertEqual(env.reference_path, "/tmp/context.md")
        self.assertEqual(env.correlation_id, "abc-123")

    def test_roundtrip_jsonl(self):
        env = HandoffEnvelope.create("a", "b", "do stuff", mode="full", body="body text")
        row = env.to_jsonl_row()
        restored = HandoffEnvelope.from_jsonl_row(row)
        self.assertEqual(env.message_id, restored.message_id)
        self.assertEqual(env.body, restored.body)
        self.assertEqual(env.intent, restored.intent)

    def test_roundtrip_json(self):
        env = HandoffEnvelope.create("x", "y", "intent", mode="none")
        text = env.to_json()
        restored = HandoffEnvelope.from_json(text)
        self.assertEqual(env.message_id, restored.message_id)

    def test_jsonl_row_is_serializable(self):
        env = HandoffEnvelope.create("a", "b", "c")
        # Should not raise
        json.dumps(env.to_jsonl_row())

    def test_invalid_schema_version(self):
        with self.assertRaises(ValueError):
            HandoffEnvelope(
                message_id="x", schema_version="bad/v99",
                sender="a", recipient="b", intent="c",
                context_mode="summary",
            )

    def test_invalid_context_mode(self):
        with self.assertRaises(ValueError):
            HandoffEnvelope(
                message_id="x", schema_version=SCHEMA_VERSION,
                sender="a", recipient="b", intent="c",
                context_mode="partial",  # type: ignore
            )

    def test_unique_message_ids(self):
        ids = {HandoffEnvelope.create("a", "b", "c").message_id for _ in range(50)}
        self.assertEqual(len(ids), 50)

    def test_context_modes_all_valid(self):
        for mode in ("full", "summary", "reference", "none"):
            env = HandoffEnvelope.create("a", "b", "c", mode=mode)  # type: ignore
            self.assertEqual(env.context_mode, mode)

    def test_body_none_by_default(self):
        env = HandoffEnvelope.create("a", "b", "intent")
        self.assertIsNone(env.body)

    def test_from_jsonl_row_preserves_created_at(self):
        env = HandoffEnvelope.create("a", "b", "c")
        row = env.to_jsonl_row()
        restored = HandoffEnvelope.from_jsonl_row(row)
        self.assertEqual(env.created_at, restored.created_at)


if __name__ == "__main__":
    unittest.main()
