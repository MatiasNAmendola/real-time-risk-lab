"""
handoff_schema.py — Schema for inter-agent messages with context_mode.
Stdlib only. Principle: Inter-agent communication is a protocol, not a free-for-all.
"""

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Literal, Optional
import uuid
import json

ContextMode = Literal["full", "summary", "reference", "none"]
SCHEMA_VERSION = "naranja/handoff-envelope/v1"


@dataclass
class HandoffEnvelope:
    message_id: str
    schema_version: str
    sender: str
    recipient: str
    intent: str                          # what the receiver should do
    context_mode: ContextMode            # how the body should be interpreted
    body: Optional[str] = None           # full or summary text
    reference_path: Optional[str] = None # path if mode=reference
    correlation_id: Optional[str] = None
    created_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())

    def __post_init__(self):
        if self.schema_version != SCHEMA_VERSION:
            raise ValueError(f"unsupported schema_version: {self.schema_version}")
        valid_modes: tuple = ("full", "summary", "reference", "none")
        if self.context_mode not in valid_modes:
            raise ValueError(f"invalid context_mode: {self.context_mode!r}")

    @classmethod
    def create(
        cls,
        sender: str,
        recipient: str,
        intent: str,
        *,
        mode: ContextMode = "summary",
        body: Optional[str] = None,
        ref: Optional[str] = None,
        corr_id: Optional[str] = None,
    ) -> "HandoffEnvelope":
        return cls(
            message_id=str(uuid.uuid4()),
            schema_version=SCHEMA_VERSION,
            sender=sender,
            recipient=recipient,
            intent=intent,
            context_mode=mode,
            body=body,
            reference_path=ref,
            correlation_id=corr_id,
        )

    def to_jsonl_row(self) -> dict:
        return {
            "message_id": self.message_id,
            "schema_version": self.schema_version,
            "sender": self.sender,
            "recipient": self.recipient,
            "intent": self.intent,
            "context_mode": self.context_mode,
            "body": self.body,
            "reference_path": self.reference_path,
            "correlation_id": self.correlation_id,
            "created_at": self.created_at,
        }

    @classmethod
    def from_jsonl_row(cls, row: dict) -> "HandoffEnvelope":
        return cls(
            message_id=row["message_id"],
            schema_version=row["schema_version"],
            sender=row["sender"],
            recipient=row["recipient"],
            intent=row["intent"],
            context_mode=row["context_mode"],
            body=row.get("body"),
            reference_path=row.get("reference_path"),
            correlation_id=row.get("correlation_id"),
            created_at=row.get("created_at", datetime.now(timezone.utc).isoformat()),
        )

    def to_json(self) -> str:
        return json.dumps(self.to_jsonl_row())

    @classmethod
    def from_json(cls, text: str) -> "HandoffEnvelope":
        return cls.from_jsonl_row(json.loads(text))
