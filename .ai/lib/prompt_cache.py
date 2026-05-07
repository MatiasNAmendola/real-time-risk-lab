"""
prompt_cache.py — Helper for Anthropic prompt cache breakpoints.
Stdlib only. Principle: Caching is explicit.

Usage:
    builder = PromptCacheBuilder()
    builder.add_stable(system_prompt)   # gets cache_control marker
    builder.add_stable(rules_text)      # second stable block
    builder.add_volatile(user_message)  # no cache marker
    blocks = builder.build()
    # Pass blocks as the 'system' or 'messages[].content' list to the Anthropic API.
    # Stable blocks are served from server-side cache on repeat calls (~75% cheaper).
"""

from dataclasses import dataclass, field
from typing import Union


@dataclass
class CacheBreakpoint:
    """A text block marked for server-side caching."""
    content: str
    cache_control: dict = field(default_factory=lambda: {"type": "ephemeral"})

    def to_api_block(self) -> dict:
        return {
            "type": "text",
            "text": self.content,
            "cache_control": self.cache_control,
        }


class PromptCacheBuilder:
    """Builds a list of message blocks with cache_control markers for Anthropic API.

    Marks stable prefixes (system prompt, rules, preamble) for server-side caching.
    The Anthropic API caches up to 4 breakpoints per request.

    Example (Python SDK):
        import anthropic
        client = anthropic.Anthropic()
        builder = PromptCacheBuilder()
        builder.add_stable(SYSTEM_PROMPT)
        builder.add_stable(RULES_TEXT)
        builder.add_volatile(user_turn)
        response = client.messages.create(
            model="claude-sonnet-4-6",
            system=builder.build_system_blocks(),
            messages=[{"role": "user", "content": builder.build_user_blocks()}],
        )
    """

    MAX_BREAKPOINTS = 4

    def __init__(self) -> None:
        self.blocks: list[Union[CacheBreakpoint, dict]] = []
        self._stable_count = 0

    def add_stable(self, content: str) -> "PromptCacheBuilder":
        """Add a stable block that should be cached (system prompt, rules, etc.)."""
        if self._stable_count >= self.MAX_BREAKPOINTS:
            # Downgrade to volatile — max breakpoints reached
            self.blocks.append({"type": "text", "text": content})
        else:
            self.blocks.append(CacheBreakpoint(content=content))
            self._stable_count += 1
        return self

    def add_volatile(self, content: str) -> "PromptCacheBuilder":
        """Add a volatile block (user prompt, dynamic context) — not cached."""
        self.blocks.append({"type": "text", "text": content})
        return self

    def build(self) -> list[dict]:
        """Returns the final list of API-ready content blocks."""
        result = []
        for block in self.blocks:
            if isinstance(block, CacheBreakpoint):
                result.append(block.to_api_block())
            else:
                result.append(block)
        return result

    def build_system_blocks(self) -> list[dict]:
        """Alias for build() — use for the 'system' parameter."""
        return self.build()

    def build_user_blocks(self) -> list[dict]:
        """Returns only volatile (non-cached) blocks — use for user message content."""
        return [
            b if isinstance(b, dict) else b.to_api_block()
            for b in self.blocks
            if not isinstance(b, CacheBreakpoint)
        ]

    def stable_char_count(self) -> int:
        """Total chars in stable (cached) blocks — useful for budget tracking."""
        return sum(
            len(b.content) for b in self.blocks if isinstance(b, CacheBreakpoint)
        )

    def volatile_char_count(self) -> int:
        """Total chars in volatile blocks."""
        return sum(
            len(b.get("text", "")) for b in self.blocks if isinstance(b, dict)
        )
