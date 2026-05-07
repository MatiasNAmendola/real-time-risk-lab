#!/usr/bin/env python3
"""
agent_bus.py — Inter-agent message bus with schema versioning and ack.
Stdlib only. Append-only JSONL. Uses fcntl.flock for concurrent safety.

Usage:
    bus = AgentBus(Path(".ai/logs/agent-bus.jsonl"))
    env = HandoffEnvelope.create("orchestrator", "impl-agent", "implement X", body="...")
    bus.send(env)
    messages = bus.inbox("impl-agent")
    bus.ack(messages[0].message_id)
"""

import sys
import os
import json
import fcntl
import argparse
from pathlib import Path
from datetime import datetime, timezone

# Allow import from sibling lib/ directory when invoked as a script
_SCRIPT_DIR = Path(__file__).parent
_LIB_DIR = _SCRIPT_DIR.parent / "lib"
if str(_LIB_DIR) not in sys.path:
    sys.path.insert(0, str(_LIB_DIR))

from handoff_schema import HandoffEnvelope, SCHEMA_VERSION  # noqa: E402


DEFAULT_BUS_PATH = Path(__file__).parent.parent / "logs" / "agent-bus.jsonl"


class AgentBus:
    """Append-only JSONL message bus for inter-agent communication."""

    def __init__(self, log_path: Path = DEFAULT_BUS_PATH) -> None:
        self.log_path = log_path
        self.log_path.parent.mkdir(parents=True, exist_ok=True)

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def send(self, envelope: HandoffEnvelope) -> None:
        """Append a HandoffEnvelope to the bus log."""
        row = envelope.to_jsonl_row()
        row["_bus_status"] = "pending"
        self._append(row)

    def inbox(self, recipient: str, only_unacked: bool = True) -> list[HandoffEnvelope]:
        """Return messages for `recipient`. Filter by pending status if only_unacked."""
        rows = self._read_all()
        # Build ack map: message_id -> latest status
        ack_map: dict[str, str] = {}
        for row in rows:
            if row.get("_record_type") == "ack":
                ack_map[row["message_id"]] = row["status"]

        result = []
        for row in rows:
            if row.get("_record_type") == "ack":
                continue
            if row.get("recipient") != recipient:
                continue
            msg_id = row.get("message_id", "")
            status = ack_map.get(msg_id, "pending")
            if only_unacked and status != "pending":
                continue
            try:
                result.append(HandoffEnvelope.from_jsonl_row(row))
            except (KeyError, ValueError):
                continue
        return result

    def ack(self, message_id: str, status: str = "seen") -> None:
        """Acknowledge a message by appending an ack record."""
        ack_row = {
            "_record_type": "ack",
            "message_id": message_id,
            "status": status,
            "acked_at": datetime.now(timezone.utc).isoformat(),
        }
        self._append(ack_row)

    def all_messages(self) -> list[HandoffEnvelope]:
        """Return all envelopes (regardless of ack status)."""
        rows = self._read_all()
        result = []
        for row in rows:
            if row.get("_record_type") == "ack":
                continue
            try:
                result.append(HandoffEnvelope.from_jsonl_row(row))
            except (KeyError, ValueError):
                continue
        return result

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _append(self, row: dict) -> None:
        with open(self.log_path, "a", encoding="utf-8") as fh:
            fcntl.flock(fh, fcntl.LOCK_EX)
            try:
                fh.write(json.dumps(row) + "\n")
            finally:
                fcntl.flock(fh, fcntl.LOCK_UN)

    def _read_all(self) -> list[dict]:
        if not self.log_path.exists():
            return []
        rows = []
        with open(self.log_path, "r", encoding="utf-8") as fh:
            fcntl.flock(fh, fcntl.LOCK_SH)
            try:
                for line in fh:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        rows.append(json.loads(line))
                    except json.JSONDecodeError:
                        continue
            finally:
                fcntl.flock(fh, fcntl.LOCK_UN)
        return rows


# ------------------------------------------------------------------
# CLI
# ------------------------------------------------------------------

def _cli():
    parser = argparse.ArgumentParser(description="Agent bus CLI")
    sub = parser.add_subparsers(dest="cmd", required=True)

    # send
    p_send = sub.add_parser("send", help="Send a message")
    p_send.add_argument("--from", dest="sender", required=True)
    p_send.add_argument("--to", dest="recipient", required=True)
    p_send.add_argument("--intent", required=True)
    p_send.add_argument("--body", default=None)
    p_send.add_argument("--mode", default="summary")
    p_send.add_argument("--bus", default=str(DEFAULT_BUS_PATH))

    # inbox
    p_inbox = sub.add_parser("inbox", help="List unacked messages")
    p_inbox.add_argument("--recipient", required=True)
    p_inbox.add_argument("--all", dest="all_msgs", action="store_true")
    p_inbox.add_argument("--bus", default=str(DEFAULT_BUS_PATH))

    # ack
    p_ack = sub.add_parser("ack", help="Acknowledge a message")
    p_ack.add_argument("message_id")
    p_ack.add_argument("--status", default="seen")
    p_ack.add_argument("--bus", default=str(DEFAULT_BUS_PATH))

    args = parser.parse_args()
    bus = AgentBus(Path(args.bus))

    if args.cmd == "send":
        env = HandoffEnvelope.create(
            args.sender, args.recipient, args.intent,
            mode=args.mode, body=args.body,
        )
        bus.send(env)
        print(f"sent {env.message_id}")

    elif args.cmd == "inbox":
        msgs = bus.inbox(args.recipient, only_unacked=not args.all_msgs)
        for m in msgs:
            print(f"{m.message_id[:8]}  {m.sender} -> {m.recipient}  [{m.context_mode}]  {m.intent[:60]}")

    elif args.cmd == "ack":
        bus.ack(args.message_id, status=args.status)
        print(f"acked {args.message_id}")


if __name__ == "__main__":
    _cli()
