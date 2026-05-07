"""Tests for agent_bus.py — uses tempdir."""

import sys
import unittest
import tempfile
from pathlib import Path

# Ensure lib/ is on path
_HERE = Path(__file__).parent
sys.path.insert(0, str(_HERE))
sys.path.insert(0, str(_HERE.parent / "lib"))

from agent_bus import AgentBus
from handoff_schema import HandoffEnvelope


class TestAgentBus(unittest.TestCase):

    def setUp(self):
        self.tmpdir = tempfile.TemporaryDirectory()
        self.bus_path = Path(self.tmpdir.name) / "agent-bus.jsonl"
        self.bus = AgentBus(self.bus_path)

    def tearDown(self):
        self.tmpdir.cleanup()

    def _make_env(self, sender="a", recipient="b", intent="do stuff", **kwargs):
        return HandoffEnvelope.create(sender, recipient, intent, **kwargs)

    # --- send ---

    def test_send_creates_file(self):
        env = self._make_env()
        self.bus.send(env)
        self.assertTrue(self.bus_path.exists())

    def test_send_appends_jsonl(self):
        for i in range(3):
            self.bus.send(self._make_env(intent=f"task {i}"))
        lines = self.bus_path.read_text().strip().splitlines()
        self.assertEqual(len(lines), 3)

    # --- inbox ---

    def test_inbox_returns_messages_for_recipient(self):
        self.bus.send(self._make_env(recipient="agent-x"))
        self.bus.send(self._make_env(recipient="agent-y"))
        msgs = self.bus.inbox("agent-x")
        self.assertEqual(len(msgs), 1)
        self.assertEqual(msgs[0].recipient, "agent-x")

    def test_inbox_empty_when_no_messages(self):
        msgs = self.bus.inbox("nobody")
        self.assertEqual(msgs, [])

    def test_inbox_filters_acked_by_default(self):
        env = self._make_env(recipient="agent-x")
        self.bus.send(env)
        self.bus.ack(env.message_id)
        msgs = self.bus.inbox("agent-x")
        self.assertEqual(len(msgs), 0)

    def test_inbox_shows_acked_when_all_flag(self):
        env = self._make_env(recipient="agent-x")
        self.bus.send(env)
        self.bus.ack(env.message_id)
        msgs = self.bus.inbox("agent-x", only_unacked=False)
        self.assertEqual(len(msgs), 1)

    # --- ack ---

    def test_ack_appends_record(self):
        env = self._make_env()
        self.bus.send(env)
        self.bus.ack(env.message_id, status="processed")
        lines = self.bus_path.read_text().strip().splitlines()
        self.assertEqual(len(lines), 2)

    def test_ack_with_custom_status(self):
        env = self._make_env(recipient="r")
        self.bus.send(env)
        self.bus.ack(env.message_id, status="done")
        msgs = self.bus.inbox("r", only_unacked=True)
        self.assertEqual(len(msgs), 0)

    # --- all_messages ---

    def test_all_messages_returns_all_envelopes(self):
        for _ in range(5):
            self.bus.send(self._make_env())
        msgs = self.bus.all_messages()
        self.assertEqual(len(msgs), 5)

    # --- envelope fields preserved ---

    def test_envelope_roundtrip_through_bus(self):
        env = HandoffEnvelope.create(
            "orchestrator", "impl", "build feature",
            mode="reference", ref="/tmp/ctx.md", corr_id="corr-001"
        )
        self.bus.send(env)
        msgs = self.bus.inbox("impl")
        self.assertEqual(len(msgs), 1)
        m = msgs[0]
        self.assertEqual(m.message_id, env.message_id)
        self.assertEqual(m.reference_path, "/tmp/ctx.md")
        self.assertEqual(m.correlation_id, "corr-001")


if __name__ == "__main__":
    unittest.main()
